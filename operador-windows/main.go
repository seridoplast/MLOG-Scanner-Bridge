package main

import (
	"crypto/rand"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

//go:embed web/*
var webFiles embed.FS

const centralVersion = "3.6.16"

type Note struct {
	ID            string `json:"id"`
	Order         string `json:"order"`
	Text          string `json:"text"`
	Status        string `json:"status"`
	CreatedAt     string `json:"createdAt"`
	UpdatedAt     string `json:"updatedAt"`
	CompletedAt   string `json:"completedAt,omitempty"`
	CompletedBy   string `json:"completedBy,omitempty"`
	CompletedByID string `json:"completedById,omitempty"`
}
type Broadcast struct {
	ID          string            `json:"id"`
	Text        string            `json:"text"`
	CreatedAt   string            `json:"createdAt"`
	Status      string            `json:"status"`
	Targets     []string          `json:"targets"`
	Delivered   map[string]string `json:"delivered"`          // servidor ofereceu na resposta HTTP
	Received    map[string]string `json:"received,omitempty"` // app confirmou processamento
	Seen        map[string]string `json:"seen"`               // operador tocou Entendido
	TargetNames map[string]string `json:"targetNames,omitempty"`
}
type DeviceObservation struct {
	ID         string `json:"id"`
	Order      string `json:"order"`
	Text       string `json:"text"`
	Status     string `json:"status"`
	RecordedAt string `json:"recordedAt"`
}
type FleetAction struct {
	ID          string `json:"id"`
	DeviceID    string `json:"deviceId"`
	DeviceName  string `json:"deviceName"`
	Kind        string `json:"kind"`
	Status      string `json:"status"`
	CreatedAt   string `json:"createdAt"`
	DeliveredAt string `json:"deliveredAt,omitempty"`
	CompletedAt string `json:"completedAt,omitempty"`
	Result      string `json:"result,omitempty"`
}
type Device struct {
	ID               string              `json:"id"`
	Name             string              `json:"name"`
	IP               string              `json:"ip"`
	LastSeen         string              `json:"lastSeen"`
	LastOrder        string              `json:"lastOrder,omitempty"`
	Online           bool                `json:"online"`
	NamePending      bool                `json:"namePending,omitempty"`
	BridgeVersion    string              `json:"bridgeVersion,omitempty"`
	AndroidVersion   string              `json:"androidVersion,omitempty"`
	Model            string              `json:"model,omitempty"`
	Shizuku          bool                `json:"shizuku"`
	Accessibility    bool                `json:"accessibility"`
	MlogForeground   bool                `json:"mlogForeground"`
	RecentActivity   bool                `json:"recentActivity"`
	Battery          int                 `json:"battery"`
	OrdersDetected   int                 `json:"ordersDetected"`
	ConfirmedOrders  int                 `json:"confirmedOrders"`
	OrderHistory     []string            `json:"orderHistory,omitempty"`
	LastActivityAt   string              `json:"lastActivityAt,omitempty"`
	LastObservations []DeviceObservation `json:"lastObservations,omitempty"`
}
type DB struct {
	Notes      []Note            `json:"notes"`
	Token      string            `json:"token"`
	Broadcasts []Broadcast       `json:"broadcasts"`
	Devices    map[string]Device `json:"devices,omitempty"`
	Actions    []FleetAction     `json:"actions,omitempty"`
}
type Server struct {
	sync.Mutex
	db             DB
	devices        map[string]Device
	path           string
	port           int
	lastDiscovery  string
	discoveryCount uint64
}

func uid() string {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return hex.EncodeToString(b)
}
func newServer(path string, port int) (*Server, error) {
	s := &Server{path: path, port: port, devices: make(map[string]Device)}
	if _, err := os.Stat(path); errors.Is(err, os.ErrNotExist) {
		if _, backErr := os.Stat(path + ".bak"); backErr == nil {
			_ = os.Rename(path+".bak", path)
		}
	}
	if b, e := os.ReadFile(path); e == nil {
		if e = json.Unmarshal(b, &s.db); e != nil {
			return nil, fmt.Errorf("banco invalido: %w", e)
		}
	} else if !errors.Is(e, os.ErrNotExist) {
		return nil, e
	}
	if s.db.Notes == nil {
		s.db.Notes = []Note{}
	}
	if s.db.Broadcasts == nil {
		s.db.Broadcasts = []Broadcast{}
	}
	if s.db.Devices == nil {
		s.db.Devices = make(map[string]Device)
	}
	if s.db.Actions == nil {
		s.db.Actions = []FleetAction{}
	}
	for id, d := range s.db.Devices {
		d.Online = false
		s.devices[id] = d
	}
	if s.db.Token == "" {
		s.db.Token = uid() + uid()
		if err := s.save(); err != nil {
			return nil, err
		}
	}
	return s, nil
}
func (s *Server) save() error {
	if e := os.MkdirAll(filepath.Dir(s.path), 0700); e != nil {
		return e
	}
	data, e := json.MarshalIndent(s.db, "", "  ")
	if e != nil {
		return e
	}
	tmp := s.path + ".tmp"
	if e = os.WriteFile(tmp, data, 0600); e != nil {
		return e
	}
	// Windows refuses to replace an existing file via os.Rename.
	if runtime.GOOS == "windows" {
		backup := s.path + ".bak"
		_ = os.Remove(backup)
		if _, err := os.Stat(s.path); err == nil {
			if err := os.Rename(s.path, backup); err != nil {
				return err
			}
		}
		if err := os.Rename(tmp, s.path); err != nil {
			_ = os.Rename(backup, s.path)
			return err
		}
		_ = os.Remove(backup)
		return nil
	}
	return os.Rename(tmp, s.path)
}
func reply(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}
func fail(w http.ResponseWriter, code int, msg string) {
	reply(w, code, map[string]string{"error": msg})
}
func (s *Server) local(r *http.Request) bool {
	host, _, e := net.SplitHostPort(r.RemoteAddr)
	return e == nil && net.ParseIP(host) != nil && net.ParseIP(host).IsLoopback()
}
func (s *Server) admin(w http.ResponseWriter, r *http.Request) bool {
	if !s.local(r) {
		fail(w, 403, "painel disponivel somente neste computador")
		return false
	}
	origin := r.Header.Get("Origin")
	if origin != "" && origin != fmt.Sprintf("http://127.0.0.1:%d", s.port) && origin != fmt.Sprintf("http://localhost:%d", s.port) {
		fail(w, 403, "origem recusada")
		return false
	}
	return true
}
func (s *Server) authorized(w http.ResponseWriter, r *http.Request) bool {
	token := r.Header.Get("X-Bridge-Token")
	if len(token) != len(s.db.Token) || subtle.ConstantTimeCompare([]byte(token), []byte(s.db.Token)) != 1 {
		fail(w, 401, "chave do coletor invalida")
		return false
	}
	return true
}
func readJSON(w http.ResponseWriter, r *http.Request, v interface{}) bool {
	if r.Header.Get("Content-Type") != "application/json" {
		fail(w, 415, "use application/json")
		return false
	}
	dec := json.NewDecoder(io.LimitReader(r.Body, 8192))
	dec.DisallowUnknownFields()
	if err := dec.Decode(v); err != nil {
		fail(w, 400, "JSON invalido")
		return false
	}
	var more interface{}
	if dec.Decode(&more) != io.EOF {
		fail(w, 400, "JSON extra")
		return false
	}
	return true
}
func normalizeOrder(s string) string { return strings.TrimSpace(s) }
func validOrder(s string) bool {
	if s == "" || len(s) > 72 {
		return false
	}
	for _, r := range s {
		if !(r >= '0' && r <= '9' || r >= 'A' && r <= 'Z' || r >= 'a' && r <= 'z' || r == '-' || r == '/' || r == '_') {
			return false
		}
	}
	return true
}
func validText(s string) bool { return strings.TrimSpace(s) != "" && len([]rune(s)) <= 1500 }
func (s *Server) handle(w http.ResponseWriter, r *http.Request) {
	switch r.URL.Path {
	case "/assets/seridoplast.svg":
		if !s.admin(w, r) {
			return
		}
		blob, e := webFiles.ReadFile("web/seridoplast-logo.svg")
		if e != nil {
			http.Error(w, "logo indisponivel", 500)
			return
		}
		w.Header().Set("Content-Type", "image/svg+xml")
		w.Header().Set("Cache-Control", "public,max-age=86400")
		_, _ = w.Write(blob)
	case "/":
		if !s.admin(w, r) {
			return
		}
		f, e := webFiles.ReadFile("web/index.html")
		if e != nil {
			http.Error(w, e.Error(), 500)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		_, _ = w.Write(f)
	case "/api/admin/state":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "GET" {
			fail(w, 405, "metodo")
			return
		}
		s.Lock()
		defer s.Unlock()
		copied := append([]Note{}, s.db.Notes...)
		sort.Slice(copied, func(i, j int) bool { return copied[i].CreatedAt > copied[j].CreatedAt })
		devices := s.allDevicesLocked()
		broadcasts := append([]Broadcast{}, s.db.Broadcasts...)
		sort.Slice(broadcasts, func(i, j int) bool { return broadcasts[i].CreatedAt > broadcasts[j].CreatedAt })
		w.Header().Set("X-Seridoplast-Central-Version", centralVersion)
		reply(w, 200, map[string]interface{}{"notes": copied, "serverVersion": centralVersion, "token": s.db.Token, "addresses": localIPs(), "port": s.port, "devices": devices, "broadcasts": broadcasts, "lastDiscovery": s.lastDiscovery, "discoveryCount": s.discoveryCount, "actions": s.actionsForDisplayLocked()})
	case "/api/admin/device/command":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			Kind     string `json:"kind"`
			Target   string `json:"target"`
			DeviceID string `json:"deviceId"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if in.Kind != "check_stable_update" && in.Kind != "shizuku_assist" {
			fail(w, 400, "comando não permitido")
			return
		}
		if in.Target != "all_online" && in.Target != "single" {
			fail(w, 400, "destino inválido")
			return
		}
		if in.Target == "single" && !validDeviceID(in.DeviceID) {
			fail(w, 400, "coletor inválido")
			return
		}
		s.Lock()
		defer s.Unlock()
		selected := []Device{}
		for _, d := range s.onlineDevicesLocked() {
			if in.Target == "all_online" || in.DeviceID == d.ID {
				selected = append(selected, d)
			}
		}
		if len(selected) == 0 {
			fail(w, 409, "nenhum coletor online atende ao filtro")
			return
		}
		now := time.Now()
		count := 0
		skipped := 0
		for _, d := range selected {
			if in.Kind == "shizuku_assist" && d.Shizuku {
				skipped++
				continue
			}
			throttled := false
			for _, a := range s.db.Actions {
				if a.DeviceID != d.ID || a.Kind != in.Kind {
					continue
				}
				at, e := time.Parse(time.RFC3339, a.CreatedAt)
				if e == nil && now.Sub(at) < 3*time.Minute {
					throttled = true
					break
				}
			}
			if throttled {
				skipped++
				continue
			}
			s.db.Actions = append(s.db.Actions, FleetAction{ID: uid(), DeviceID: d.ID, DeviceName: d.Name, Kind: in.Kind, Status: "pending", CreatedAt: now.Format(time.RFC3339)})
			count++
		}
		if len(s.db.Actions) > 400 {
			s.db.Actions = s.db.Actions[len(s.db.Actions)-400:]
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]interface{}{"ok": true, "queued": count, "skipped": skipped, "selected": len(selected)})
	case "/api/admin/device/rename":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			ID   string `json:"id"`
			Name string `json:"name"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		in.Name = strings.TrimSpace(in.Name)
		if !validDeviceID(in.ID) || !validDeviceName(in.Name) {
			fail(w, 400, "Nome deve ter entre 1 e 60 caracteres.")
			return
		}
		s.Lock()
		defer s.Unlock()
		d, ok := s.devices[in.ID]
		if !ok {
			fail(w, 404, "Coletor ainda não cadastrado.")
			return
		}
		d.Name = in.Name
		d.NamePending = true
		s.devices[in.ID] = d
		s.db.Devices[in.ID] = d
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/admin/save":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var input struct {
			ID    string `json:"id"`
			Order string `json:"order"`
			Text  string `json:"text"`
		}
		if !readJSON(w, r, &input) {
			return
		}
		input.Order = normalizeOrder(input.Order)
		input.Text = strings.TrimSpace(input.Text)
		if !validOrder(input.Order) || !validText(input.Text) {
			fail(w, 400, "numero do pedido invalido ou observacao vazia/grande (max. 1500 caracteres)")
			return
		}
		s.Lock()
		defer s.Unlock()
		now := time.Now().Format(time.RFC3339)
		if input.ID != "" {
			found := false
			for i := range s.db.Notes {
				if s.db.Notes[i].ID == input.ID && s.db.Notes[i].Status == "pending" {
					s.db.Notes[i].Order = input.Order
					s.db.Notes[i].Text = input.Text
					s.db.Notes[i].UpdatedAt = now
					found = true
					break
				}
			}
			if !found {
				fail(w, 404, "observacao nao encontrada ou ja concluida")
				return
			}
		}
		if input.ID == "" {
			s.db.Notes = append(s.db.Notes, Note{ID: uid(), Order: input.Order, Text: input.Text, Status: "pending", CreatedAt: now, UpdatedAt: now})
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/admin/status":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			ID     string `json:"id"`
			Status string `json:"status"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if in.Status != "pending" && in.Status != "done" {
			fail(w, 400, "status invalido")
			return
		}
		s.Lock()
		defer s.Unlock()
		found := false
		for i := range s.db.Notes {
			n := &s.db.Notes[i]
			if n.ID != in.ID {
				continue
			}
			found = true
			n.Status = in.Status
			n.UpdatedAt = time.Now().Format(time.RFC3339)
			if in.Status == "done" {
				n.CompletedAt = n.UpdatedAt
				n.CompletedBy = "painel"
			} else {
				n.CompletedAt = ""
				n.CompletedBy = ""
				n.CompletedByID = ""
			}
			break
		}
		if !found {
			fail(w, 404, "observacao nao encontrada")
			return
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/admin/delete":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			ID string `json:"id"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		s.Lock()
		defer s.Unlock()
		found := false
		for i, n := range s.db.Notes {
			if n.ID == in.ID {
				found = true
				s.db.Notes = append(s.db.Notes[:i], s.db.Notes[i+1:]...)
				s.removeObservationSnapshotLocked(n.ID)
				break
			}
		}
		if !found {
			fail(w, 404, "observacao nao encontrada")
			return
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/admin/broadcast":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			Text   string `json:"text"`
			ID     string `json:"id"`
			Action string `json:"action"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		s.Lock()
		defer s.Unlock()
		if in.Action == "delete" {
			found := false
			for i, notice := range s.db.Broadcasts {
				if notice.ID == in.ID {
					s.db.Broadcasts = append(s.db.Broadcasts[:i], s.db.Broadcasts[i+1:]...)
					found = true
					break
				}
			}
			if !found {
				fail(w, 404, "aviso nao encontrado")
				return
			}
			if err := s.save(); err != nil {
				fail(w, 500, err.Error())
				return
			}
			reply(w, 200, map[string]bool{"ok": true})
			return
		}
		if in.Action == "close" {
			found := false
			for i := range s.db.Broadcasts {
				if s.db.Broadcasts[i].ID == in.ID && s.db.Broadcasts[i].Status == "active" {
					s.db.Broadcasts[i].Status = "closed"
					found = true
					break
				}
			}
			if !found {
				fail(w, 404, "aviso não encontrado")
				return
			}
			if err := s.save(); err != nil {
				fail(w, 500, err.Error())
				return
			}
			reply(w, 200, map[string]bool{"ok": true})
			return
		}
		if in.Action != "send" || !validText(in.Text) || utf8.RuneCountInString(in.Text) > 600 {
			fail(w, 400, "aviso deve ter entre 1 e 600 caracteres")
			return
		}
		connected := s.onlineDevicesLocked()
		if len(connected) == 0 {
			fail(w, 409, "nenhum coletor conectado; mantenha-os ligados na mesma rede")
			return
		}
		targets := make([]string, 0, len(connected))
		targetNames := make(map[string]string, len(connected))
		for _, d := range connected {
			targets = append(targets, d.ID)
			targetNames[d.ID] = d.Name
		}
		s.db.Broadcasts = append(s.db.Broadcasts, Broadcast{
			ID: uid(), Text: strings.TrimSpace(in.Text), CreatedAt: time.Now().Format(time.RFC3339), Status: "active", Targets: targets, Delivered: map[string]string{}, Received: map[string]string{}, Seen: map[string]string{}, TargetNames: targetNames,
		})
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]interface{}{"ok": true, "recipients": len(targets)})
	case "/api/device/heartbeat":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		var in struct {
			ID                string `json:"id"`
			Name              string `json:"name"`
			Order             string `json:"order"`
			BridgeVersion     string `json:"bridgeVersion"`
			AndroidVersion    string `json:"androidVersion"`
			Model             string `json:"model"`
			Shizuku           bool   `json:"shizuku"`
			Accessibility     bool   `json:"accessibility"`
			MlogForeground    bool   `json:"mlogForeground"`
			RecentActivity    bool   `json:"recentActivity"`
			LastActivityAgeMs int64  `json:"lastActivityAgeMs"`
			Battery           int    `json:"battery"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if !validDeviceID(in.ID) || strings.TrimSpace(in.Name) == "" || utf8.RuneCountInString(in.Name) > 80 || strings.ContainsAny(in.Name, "\r\n\x00") || len(in.Order) > 72 ||
			len(in.BridgeVersion) > 24 || len(in.Model) > 80 || len(in.AndroidVersion) > 24 || in.Battery < -1 || in.Battery > 100 {
			fail(w, 400, "coletor invalido")
			return
		}
		peer, _, _ := net.SplitHostPort(r.RemoteAddr)
		s.Lock()
		defer s.Unlock()
		now := time.Now().Format(time.RFC3339)
		d, known := s.devices[in.ID]
		if !known {
			d = Device{ID: in.ID, Name: in.Name, Battery: -1}
		}
		changed := !known || (in.Order != "" && in.Order != d.LastOrder)
		if !d.NamePending {
			d.Name = strings.TrimSpace(in.Name)
		}
		d.IP = peer
		d.LastSeen = now
		// Preserve last known order when no order is currently visible.
		if in.Order != "" {
			d.LastOrder = in.Order
		}
		d.Online = true
		d.BridgeVersion = in.BridgeVersion
		d.AndroidVersion = in.AndroidVersion
		d.Model = in.Model
		d.Shizuku = in.Shizuku
		d.Accessibility = in.Accessibility
		d.MlogForeground = in.MlogForeground
		d.RecentActivity = in.RecentActivity
		// Duration is based on elapsedRealtime() on Android, not an unreliable device wall clock.
		// Older clients send no age: fall back to the last heartbeat reporting activity.
		if in.LastActivityAgeMs > 0 && in.LastActivityAgeMs <= 7*24*60*60*1000 {
			eventAt := time.Now().Add(-time.Duration(in.LastActivityAgeMs) * time.Millisecond)
			previous, e := time.Parse(time.RFC3339, d.LastActivityAt)
			if e != nil || eventAt.After(previous.Add(60*time.Second)) {
				d.LastActivityAt = eventAt.Format(time.RFC3339)
			}
		} else if in.RecentActivity {
			previous, e := time.Parse(time.RFC3339, d.LastActivityAt)
			if e != nil || time.Since(previous) > 60*time.Second {
				d.LastActivityAt = now
			}
		}
		d.Battery = in.Battery
		if in.Order != "" && validOrder(in.Order) && !contains(d.OrderHistory, in.Order) {
			d.OrderHistory = append(d.OrderHistory, in.Order)
			// Keep only a bounded local sample; this is NOT the completed-order count.
			if len(d.OrderHistory) > 5000 {
				d.OrderHistory = d.OrderHistory[len(d.OrderHistory)-5000:]
			}
			d.OrdersDetected++
		}
		if in.Order != "" {
			s.captureObservationsLocked(&d, in.Order)
		}
		d.ConfirmedOrders = 0
		confirmed := map[string]bool{}
		for _, n := range s.db.Notes {
			if n.Status == "done" && n.CompletedByID == in.ID {
				confirmed[n.Order] = true
			}
		}
		d.ConfirmedOrders = len(confirmed)
		s.devices[in.ID] = d
		// Save profiles periodically rather than writing the DB every 15 seconds.
		persisted := s.db.Devices[in.ID]
		if changed || d.NamePending != persisted.NamePending || d.Name != persisted.Name ||
			(!d.LastSeenIsRecent(persisted.LastSeen, 2*time.Minute)) ||
			(d.LastActivityAt != persisted.LastActivityAt) ||
			len(d.LastObservations) != len(persisted.LastObservations) {
			s.db.Devices[in.ID] = d
			if err := s.save(); err != nil {
				fail(w, 500, err.Error())
				return
			}
		}
		alerts := []Broadcast{}
		deliveredNew := false
		for i := range s.db.Broadcasts {
			b := &s.db.Broadcasts[i]
			if b.Status != "active" || !contains(b.Targets, in.ID) {
				continue
			}
			if _, seen := b.Seen[in.ID]; seen {
				continue
			}
			if b.Delivered == nil {
				b.Delivered = map[string]string{}
			}
			// Delivered is operational status only, persisted on next DB mutation.
			if _, ok := b.Delivered[in.ID]; !ok {
				b.Delivered[in.ID] = now
				deliveredNew = true
			}
			alerts = append(alerts, *b)
		}
		if deliveredNew {
			if err := s.save(); err != nil {
				fail(w, 500, err.Error())
				return
			}
		}
		commands := []FleetAction{}
		for i := range s.db.Actions {
			a := &s.db.Actions[i]
			if a.DeviceID != in.ID || a.Status == "acknowledged" {
				continue
			}
			created, err := time.Parse(time.RFC3339, a.CreatedAt)
			if err != nil || time.Since(created) > 15*time.Minute {
				continue
			}
			if a.DeliveredAt == "" {
				a.DeliveredAt = now
				a.Status = "delivered"
			}
			commands = append(commands, *a)
		}
		if len(commands) > 0 {
			_ = s.save()
		}
		reply(w, 200, map[string]interface{}{"ok": true, "broadcasts": alerts, "commands": commands,
			"preferredName": func() string {
				if d.NamePending {
					return d.Name
				}
				return ""
			}()})
		if d.NamePending {
			d.NamePending = false
			s.devices[in.ID] = d
			s.db.Devices[in.ID] = d
			_ = s.save()
		}
	case "/api/device/command-result":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		var in struct {
			ID        string `json:"id"`
			CommandID string `json:"commandId"`
			Result    string `json:"result"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if !validDeviceID(in.ID) || !validDeviceID(in.CommandID) || len(in.Result) > 200 {
			fail(w, 400, "resultado inválido")
			return
		}
		s.Lock()
		defer s.Unlock()
		found := false
		for i := range s.db.Actions {
			a := &s.db.Actions[i]
			if a.ID == in.CommandID && a.DeviceID == in.ID {
				if a.Status != "acknowledged" {
					a.Status = "acknowledged"
					a.CompletedAt = time.Now().Format(time.RFC3339)
					a.Result = in.Result
				}
				found = true
				break
			}
		}
		if !found {
			fail(w, 404, "solicitação não encontrada para este coletor")
			return
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/device/received":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		var in struct {
			ID           string   `json:"id"`
			BroadcastIDs []string `json:"broadcastIds"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if !validDeviceID(in.ID) || len(in.BroadcastIDs) > 60 {
			fail(w, 400, "identificadores invalidos")
			return
		}
		s.Lock()
		defer s.Unlock()
		found := 0
		now := time.Now().Format(time.RFC3339)
		for _, id := range in.BroadcastIDs {
			if !validDeviceID(id) {
				fail(w, 400, "aviso invalido")
				return
			}
			for i := range s.db.Broadcasts {
				b := &s.db.Broadcasts[i]
				if b.ID != id || !contains(b.Targets, in.ID) {
					continue
				}
				if b.Received == nil {
					b.Received = map[string]string{}
				}
				if _, ok := b.Received[in.ID]; !ok {
					b.Received[in.ID] = now
				}
				found++
				break
			}
		}
		if found > 0 {
			if err := s.save(); err != nil {
				fail(w, 500, err.Error())
				return
			}
		}
		reply(w, 200, map[string]interface{}{"ok": true, "confirmed": found})
	case "/api/device/seen":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		var in struct {
			ID          string `json:"id"`
			BroadcastID string `json:"broadcastId"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if !validDeviceID(in.ID) || len(in.BroadcastID) != 32 {
			fail(w, 400, "identificadores invalidos")
			return
		}
		s.Lock()
		defer s.Unlock()
		found := false
		for i := range s.db.Broadcasts {
			b := &s.db.Broadcasts[i]
			if b.ID == in.BroadcastID && contains(b.Targets, in.ID) {
				if b.Seen == nil {
					b.Seen = map[string]string{}
				}
				b.Seen[in.ID] = time.Now().Format(time.RFC3339)
				found = true
				break
			}
		}
		if !found {
			fail(w, 404, "aviso não direcionado a este coletor")
			return
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/ping":
		if r.Method != "GET" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/notes":
		if r.Method != "GET" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		order := normalizeOrder(r.URL.Query().Get("order"))
		if !validOrder(order) {
			fail(w, 400, "pedido invalido")
			return
		}
		s.Lock()
		defer s.Unlock()
		list := []Note{}
		for _, n := range s.db.Notes {
			if n.Order == order && n.Status == "pending" {
				list = append(list, n)
			}
		}
		sort.Slice(list, func(i, j int) bool { return list[i].CreatedAt < list[j].CreatedAt })
		reply(w, 200, map[string]interface{}{"notes": list, "order": order})
	case "/api/complete":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		var in struct {
			Order    string   `json:"order"`
			IDs      []string `json:"ids"`
			Device   string   `json:"device"`
			DeviceID string   `json:"deviceId"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		in.Order = normalizeOrder(in.Order)
		if !validOrder(in.Order) || len(in.IDs) == 0 || len(in.IDs) > 100 || len(in.Device) > 80 ||
			(in.DeviceID != "" && !validDeviceID(in.DeviceID)) {
			fail(w, 400, "dados invalidos")
			return
		}
		s.Lock()
		defer s.Unlock()
		now := time.Now().Format(time.RFC3339)
		changed := 0
		for i := range s.db.Notes {
			n := &s.db.Notes[i]
			if n.Order != in.Order || n.Status != "pending" {
				continue
			}
			for _, id := range in.IDs {
				if n.ID == id {
					n.Status = "done"
					n.CompletedAt = now
					n.CompletedBy = in.Device
					n.CompletedByID = in.DeviceID
					n.UpdatedAt = now
					changed++
					break
				}
			}
		}
		if changed > 0 {
			if e := s.save(); e != nil {
				fail(w, 500, e.Error())
				return
			}
		}
		reply(w, 200, map[string]interface{}{"ok": true, "completed": changed})
	default:
		fail(w, 404, "nao encontrado")
	}
}

// Historical observation snapshot belongs to a device, not just the active order.
// It remains available when the collector goes offline. No barcode history is stored.
func (s *Server) captureObservationsLocked(d *Device, order string) {
	for _, n := range s.db.Notes {
		if n.Order != order {
			continue
		}
		found := false
		for i := range d.LastObservations {
			if d.LastObservations[i].ID == n.ID {
				d.LastObservations[i].Text = n.Text
				d.LastObservations[i].Status = n.Status
				found = true
				break
			}
		}
		if !found {
			d.LastObservations = append(d.LastObservations, DeviceObservation{ID: n.ID, Order: n.Order, Text: n.Text, Status: n.Status, RecordedAt: time.Now().Format(time.RFC3339)})
		}
	}
	if len(d.LastObservations) > 12 {
		d.LastObservations = d.LastObservations[len(d.LastObservations)-12:]
	}
}

// Avoid displaying deleted private/internal notes in offline device history.
func (s *Server) removeObservationSnapshotLocked(noteID string) {
	for id, d := range s.devices {
		out := d.LastObservations[:0]
		for _, item := range d.LastObservations {
			if item.ID != noteID {
				out = append(out, item)
			}
		}
		d.LastObservations = out
		s.devices[id] = d
		s.db.Devices[id] = d
	}
}
func (s *Server) actionsForDisplayLocked() []FleetAction {
	out := make([]FleetAction, 0, len(s.db.Actions))
	for _, a := range s.db.Actions {
		v := a
		at, e := time.Parse(time.RFC3339, v.CreatedAt)
		if e == nil && time.Since(at) > 15*time.Minute && v.Status != "acknowledged" {
			v.Status = "expired"
		}
		out = append(out, v)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt > out[j].CreatedAt })
	if len(out) > 35 {
		out = out[:35]
	}
	return out
}
func validDeviceID(id string) bool { return len(id) == 32 && isHex(id) }
func isHex(s string) bool {
	for _, r := range s {
		if !strings.ContainsRune("0123456789abcdef", r) {
			return false
		}
	}
	return true
}
func contains(a []string, v string) bool {
	for _, x := range a {
		if x == v {
			return true
		}
	}
	return false
}
func validDeviceName(name string) bool {
	return strings.TrimSpace(name) != "" && utf8.RuneCountInString(name) <= 60 &&
		!strings.ContainsAny(name, "\r\n\x00")
}
func (d Device) LastSeenIsRecent(previous string, threshold time.Duration) bool {
	if previous == "" {
		return false
	}
	t, err := time.Parse(time.RFC3339, previous)
	return err == nil && time.Since(t) < threshold
}
func (s *Server) allDevicesLocked() []Device {
	out := make([]Device, 0, len(s.devices))
	cutoff := time.Now().Add(-75 * time.Second)
	for _, d := range s.devices {
		t, err := time.Parse(time.RFC3339, d.LastSeen)
		d.Online = err == nil && t.After(cutoff)
		out = append(out, d)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Online != out[j].Online {
			return out[i].Online
		}
		return out[i].Name < out[j].Name
	})
	return out
}
func (s *Server) onlineDevicesLocked() []Device {
	devices := []Device{}
	cutoff := time.Now().Add(-75 * time.Second)
	for _, d := range s.devices {
		t, e := time.Parse(time.RFC3339, d.LastSeen)
		if e == nil && t.After(cutoff) {
			devices = append(devices, d)
		}
	}
	sort.Slice(devices, func(i, j int) bool { return devices[i].Name < devices[j].Name })
	return devices
}

// Descoberta LAN de confiança: sem cadastro manual, somente em sub-redes locais.
// A resposta inclui a chave para permitir provisionamento de todos os coletores.
// Não use em Wi-Fi público, rede de convidados ou porta exposta na internet.
func sameLocalSubnet(peer net.IP) bool {
	if peer == nil || peer.To4() == nil || !peer.IsPrivate() {
		return false
	}
	ifaces, err := net.Interfaces()
	if err != nil {
		return false
	}
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := iface.Addrs()
		for _, a := range addrs {
			if n, ok := a.(*net.IPNet); ok && n.IP.To4() != nil && n.Contains(peer) {
				return true
			}
		}
	}
	return false
}
func (s *Server) serveDiscovery() {
	conn, e := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4zero, Port: 8766})
	if e != nil {
		log.Printf("Descoberta LAN indisponivel (UDP 8766): %v", e)
		return
	}
	defer conn.Close()
	buf := make([]byte, 256)
	for {
		_ = conn.SetReadDeadline(time.Now().Add(5 * time.Second))
		n, addr, e := conn.ReadFromUDP(buf)
		if e != nil {
			continue
		}
		if string(buf[:n]) != "SERIDOPLAST_NOTES_DISCOVER_V1" || !sameLocalSubnet(addr.IP) {
			continue
		}
		s.Lock()
		s.lastDiscovery = time.Now().Format(time.RFC3339) + " · " + addr.IP.String()
		s.discoveryCount++
		s.Unlock()
		host := localIPForPeer(addr.IP)
		if host == "" {
			continue
		}
		out, _ := json.Marshal(map[string]interface{}{"service": "SERIDOPLAST_NOTES_V1", "host": "http://" + host + ":" + strconv.Itoa(s.port), "token": s.db.Token})
		_, _ = conn.WriteToUDP(out, addr)
	}
}

// O anúncio não contém a chave de acesso. O coletor o utiliza apenas para
// descobrir o novo IP via requisição UDP e validar a resposta com HTTP.
const notesBeacon = "SERIDOPLAST_NOTES_BEACON_V1"
const notesBeaconGroup = "239.255.42.99"
const notesBeaconPort = 8767

// Envia anúncios leves em cada interface IPv4 privada ativa; o endereço é
// descoberto pelo receptor a partir do remetente do datagrama.
func announceLAN() {
	addr := &net.UDPAddr{IP: net.ParseIP(notesBeaconGroup), Port: notesBeaconPort}
	for {
		ifaces, err := net.Interfaces()
		if err == nil {
			for _, iface := range ifaces {
				if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 || iface.Flags&net.FlagMulticast == 0 {
					continue
				}
				addrs, _ := iface.Addrs()
				for _, item := range addrs {
					ipnet, ok := item.(*net.IPNet)
					if !ok || ipnet.IP.To4() == nil || !ipnet.IP.IsPrivate() {
						continue
					}
					conn, err := net.DialUDP("udp4", &net.UDPAddr{IP: ipnet.IP.To4()}, addr)
					if err == nil {
						_ = conn.SetWriteDeadline(time.Now().Add(700 * time.Millisecond))
						_, _ = conn.Write([]byte(notesBeacon))
						_ = conn.Close()
					}
				}
			}
		}
		time.Sleep(12 * time.Second)
	}
}

func localIPForPeer(peer net.IP) string {
	ifaces, _ := net.Interfaces()
	for _, iface := range ifaces {
		addrs, _ := iface.Addrs()
		for _, a := range addrs {
			if n, ok := a.(*net.IPNet); ok && n.IP.To4() != nil && n.Contains(peer) && !n.IP.IsLoopback() {
				return n.IP.String()
			}
		}
	}
	return ""
}
func localIPs() []string {
	out := []string{}
	addrs, e := net.InterfaceAddrs()
	if e != nil {
		return out
	}
	for _, a := range addrs {
		ipnet, ok := a.(*net.IPNet)
		if !ok {
			continue
		}
		ip := ipnet.IP.To4()
		if ip == nil || ip.IsLoopback() {
			continue
		}
		if (ip[0] == 10) || (ip[0] == 192 && ip[1] == 168) || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31) {
			out = append(out, ip.String())
		}
	}
	sort.Strings(out)
	return out
}
func main() {
	port := flag.Int("port", 8765, "porta do servidor local")
	flag.Parse()
	if *port < 1024 || *port > 65535 {
		startupError("Porta inválida.")
		return
	}
	home, e := os.UserConfigDir()
	if e != nil {
		startupError("Não foi possível acessar a pasta de dados: " + e.Error())
		return
	}
	path := filepath.Join(home, "Seridoplast", "ObservacoesPedidos", "dados.json")
	server, e := newServer(path, *port)
	if e != nil {
		startupError("Não foi possível abrir o banco de observações: " + e.Error())
		return
	}
	listen, e := net.Listen("tcp", fmt.Sprintf("0.0.0.0:%d", *port))
	if e != nil {
		if alreadyRunningSeridoplast(*port) {
			// A instância anterior já mantém o servidor ativo: reabrir o painel.
			launchDesktop(server, *port)
			return
		}
		startupError(fmt.Sprintf("A porta %d já está ocupada por outro programa. Feche a versão antiga do painel antes de abrir esta central.\n\n%v", *port, e))
		return
	}
	fmt.Print("\nSERIDOPLAST | OBSERVACOES DE PEDIDOS\n\n")
	fmt.Println("Painel no PC: http://127.0.0.1:" + strconv.Itoa(*port))
	for _, ip := range localIPs() {
		fmt.Printf("Coletores: http://%s:%d\n", ip, *port)
	}
	fmt.Println("Mantenha este programa aberto. Dados salvos em:", path)
	fmt.Println("Descoberta: UDP 8766 (pedido/resposta) + anúncio multicast 239.255.42.99:8767.")
	fmt.Println("Libere TCP", *port, "e UDP 8766 no Firewall Privado; não exponha à internet.")
	fmt.Println("Os coletores descobrem o painel sem cadastrar IP/chave. Use apenas LAN corporativa confiavel.")
	go server.serveDiscovery()
	go announceLAN()
	go func() {
		if err := http.Serve(listen, http.HandlerFunc(server.handle)); err != nil {
			log.Printf("Servidor encerrado: %v", err)
		}
	}()
	launchDesktop(server, *port)
}
