package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
	"unicode/utf8"
)

type ProductivityEvent struct {
	ID        string `json:"id"`
	At        int64  `json:"at"`
	Kind      string `json:"kind"`
	Operator  string `json:"operator"`
	Code      string `json:"code"`
	Order     string `json:"order"`
	DeviceID  string `json:"deviceId,omitempty"`
	Screen    string `json:"screen,omitempty"`
	RankLogin string `json:"-"`
}
type productivityBatch struct {
	Events []ProductivityEvent `json:"events"`
}

func (e ProductivityEvent) key() string { return e.DeviceID + ":" + e.ID }
func (e ProductivityEvent) operatorKey() string {
	if e.Operator == "" {
		return "device:" + e.DeviceID
	}
	return "user:" + strings.ToLower(e.Operator)
}
func (s *Server) productivityPath() string {
	return filepath.Join(filepath.Dir(s.path), "produtividade.jsonl")
}

// The journal is separate from dados.json so heartbeats do not rewrite the entire history.
// An incomplete final line (power failure during append) is discarded. A malformed complete line is an error.
func (s *Server) loadProductivity() error {
	s.productivityIDs = map[string]bool{}
	if _, err := os.Stat(s.productivityPath()); errors.Is(err, os.ErrNotExist) {
		if _, e := os.Stat(s.productivityPath() + ".bak"); e == nil {
			_ = os.Rename(s.productivityPath()+".bak", s.productivityPath())
		}
	}
	f, err := os.OpenFile(s.productivityPath(), os.O_RDWR, 0600)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	defer f.Close()
	r := bufio.NewReader(f)
	var offset int64
	for {
		line, err := r.ReadBytes('\n')
		if err == io.EOF {
			if len(line) > 0 {
				return f.Truncate(offset)
			}
			return nil
		}
		if err != nil {
			return err
		}
		var b productivityBatch
		if err = json.Unmarshal(line, &b); err != nil {
			return fmt.Errorf("histórico de produtividade inválido na posição %d: %w", offset, err)
		}
		for _, e := range b.Events {
			if e.At > s.db.DeletedCollectorBefore[e.DeviceID] && !s.productivityIDs[e.key()] {
				s.productivityIDs[e.key()] = true
				s.productivity = append(s.productivity, e)
			}
		}
		offset += int64(len(line))
	}
}
func (s *Server) appendProductivity(events []ProductivityEvent) error {
	if len(events) == 0 {
		return nil
	}
	line, err := json.Marshal(productivityBatch{events})
	if err != nil {
		return err
	}
	line = append(line, '\n')
	f, err := os.OpenFile(s.productivityPath(), os.O_CREATE|os.O_RDWR|os.O_APPEND, 0600)
	if err != nil {
		return err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return err
	}
	n, err := f.Write(line)
	if err == nil && n != len(line) {
		err = io.ErrShortWrite
	}
	if err == nil {
		err = f.Sync()
	}
	if err != nil {
		_ = f.Truncate(info.Size())
		_ = f.Sync()
		return err
	}
	for _, e := range events {
		s.productivityIDs[e.key()] = true
		s.productivity = append(s.productivity, e)
	}
	return nil
}
func validProductivity(e ProductivityEvent) bool {
	if !validDeviceID(e.ID) || e.At <= 0 || e.At > 253402300799999 || len(e.Code) < 1 || len(e.Code) > 128 || utf8.RuneCountInString(e.Operator) > 60 || strings.ContainsAny(e.Operator, " \r\n\x00") || len(e.Order) > 72 {
		return false
	}
	if len(e.Screen) > 40 || strings.ContainsAny(e.Screen, " \r\n\x00") {
		return false
	}
	if e.Kind != "product" && e.Kind != "note" && e.Kind != "order" {
		return false
	}
	for _, r := range e.Code {
		if !(r >= '0' && r <= '9' || r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || strings.ContainsRune("-._/+", r)) {
			return false
		}
	}
	if e.Order != "" && !validOrder(e.Order) {
		return false
	}
	return e.Kind != "order" || e.Code == e.Order
}
func (s *Server) productivityIngest(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		fail(w, 405, "metodo")
		return
	}
	if !s.authorized(w, r) {
		return
	}
	var body struct {
		ID     string              `json:"id"`
		Events []ProductivityEvent `json:"events"`
	}
	if !readJSON(w, r, &body) {
		return
	}
	if !validDeviceID(body.ID) || len(body.Events) == 0 || len(body.Events) > 12 {
		fail(w, 400, "lote inválido")
		return
	}
	s.Lock()
	defer s.Unlock()
	if _, ok := s.devices[body.ID]; !ok {
		fail(w, 409, "coletor ainda não cadastrado")
		return
	}
	pending := []ProductivityEvent{}
	accepted := []string{}
	seen := map[string]bool{}
	for _, e := range body.Events {
		if !validProductivity(e) {
			fail(w, 400, "evento inválido")
			return
		}
		e.DeviceID = body.ID
		if seen[e.ID] {
			continue
		}
		seen[e.ID] = true
		accepted = append(accepted, e.ID)
		if e.At > s.db.DeletedCollectorBefore[body.ID] && !s.productivityIDs[e.key()] {
			pending = append(pending, e)
		}
	}
	if err := s.appendProductivity(pending); err != nil {
		fail(w, 500, "não foi possível gravar o histórico")
		return
	}
	// Use original event time, not the upload time: offline replay must not fake activity.
	d := s.devices[body.ID]
	now := time.Now()
	for _, e := range pending {
		if e.Kind == "product" || e.Kind == "note" {
			at := time.UnixMilli(e.At)
			if !at.After(now) {
				markActivity(&d, at)
			}
		}
	}
	s.devices[body.ID] = d
	reply(w, 200, map[string]interface{}{"accepted": accepted})
}

var productivityZone = time.FixedZone("BRT", -3*3600)

func productivityPeriod(period, date string, now time.Time) (time.Time, time.Time, error) {
	now = now.In(productivityZone)
	day := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, productivityZone)
	if date != "" {
		d, err := time.ParseInLocation("2006-01-02", date, productivityZone)
		if err != nil {
			return day, day, err
		}
		day = d
	}
	switch period {
	case "day":
		return day, day.AddDate(0, 0, 1), nil
	case "week":
		start := day.AddDate(0, 0, -(int(day.Weekday())+6)%7)
		return start, start.AddDate(0, 0, 7), nil
	case "month":
		start := time.Date(day.Year(), day.Month(), 1, 0, 0, 0, 0, productivityZone)
		return start, start.AddDate(0, 1, 0), nil
	default:
		return day, day, errors.New("período inválido")
	}
}

type ProductivityRow struct {
	Completed  int     `json:"completed"`
	Key        string  `json:"key"`
	Name       string  `json:"name"`
	Identified bool    `json:"identified"`
	Products   int     `json:"products"`
	Notes      int     `json:"notes"`
	NoteScans  int     `json:"noteScans"`
	Orders     int     `json:"orders"`
	LastAt     int64   `json:"lastAt"`
	TP         float64 `json:"tp"`
	PPH        float64 `json:"pph"`
	Intervals  int     `json:"intervals"`
	intervalMs int64
	Devices    []string `json:"devices"`
	notes      map[string]bool
	orders     map[string]bool
	devices    map[string]bool
}
type ProductivityDetail struct {
	Kind   string `json:"kind"`
	Code   string `json:"code"`
	Count  int    `json:"count"`
	LastAt int64  `json:"lastAt"`
}
type productivityReport struct {
	Completed     int                   `json:"completed"`
	Sessions      []NoteSession         `json:"sessions"`
	SessionsTotal int                   `json:"sessionsTotal"`
	Rows          []*ProductivityRow    `json:"rows"`
	Details       []*ProductivityDetail `json:"details"`
	Start         string                `json:"start"`
	End           string                `json:"end"`
	Products      int                   `json:"products"`
	Notes         int                   `json:"notes"`
	NoteScans     int                   `json:"noteScans"`
	Orders        int                   `json:"orders"`
	Unknown       int                   `json:"unknown"`
}

func (s *Server) productivityReport(start, end time.Time, selected string) productivityReport {
	out := productivityReport{Rows: []*ProductivityRow{}, Details: []*ProductivityDetail{}, Start: start.Format("2006-01-02"), End: end.AddDate(0, 0, -1).Format("2006-01-02")}
	rows := map[string]*ProductivityRow{}
	details := map[string]*ProductivityDetail{}
	// Offline batches can arrive out of order; calculate only successive scans on the same collector.
	events := append([]ProductivityEvent(nil), s.productivity...)
	sort.SliceStable(events, func(i, j int) bool { return events[i].At < events[j].At })
	previous := map[string]ProductivityEvent{}
	for _, e := range events {
		if e.At < start.UnixMilli() || e.At >= end.UnixMilli() {
			continue
		}
		if e.Kind == "break" {
			delete(previous, e.DeviceID)
			continue
		}
		key := e.operatorKey()
		row := rows[key]
		name := e.Operator
		dname := s.devices[e.DeviceID].Name
		if dname == "" {
			dname = "Coletor " + e.DeviceID
		}
		if name == "" {
			name = "Sem login · " + dname
		}
		if row == nil {
			row = &ProductivityRow{Key: key, Name: name, Identified: e.Operator != "", notes: map[string]bool{}, orders: map[string]bool{}, devices: map[string]bool{}}
			rows[key] = row
		}
		if e.At > row.LastAt {
			row.LastAt = e.At
			row.Name = name
		}
		row.devices[dname] = true
		if !row.Identified {
			out.Unknown++
		}
		switch e.Kind {
		case "product":
			prev, ok := previous[e.DeviceID]
			delta := e.At - prev.At
			if ok && prev.operatorKey() == key && strings.EqualFold(prev.RankLogin, e.RankLogin) && prev.Order == e.Order && delta > 0 && delta <= 300000 && time.UnixMilli(prev.At).In(productivityZone).Format("2006-01-02") == time.UnixMilli(e.At).In(productivityZone).Format("2006-01-02") {
				row.Intervals++
				row.intervalMs += delta
			}
			previous[e.DeviceID] = e
			row.Products++
			out.Products++
		case "note":
			delete(previous, e.DeviceID)
			row.NoteScans++
			out.NoteScans++
			row.notes[e.Code] = true
		case "order":
			if prev, ok := previous[e.DeviceID]; ok && prev.Order != e.Order {
				delete(previous, e.DeviceID)
			}
			row.orders[e.Code] = true
		}
		if key == selected {
			kind, code := e.Kind, e.Code
			if kind == "product" {
				kind = "items"
				code = e.Order
				if code == "" {
					code = "Sem pedido associado"
				}
			}
			dk := kind + ":" + code
			v := details[dk]
			if v == nil {
				v = &ProductivityDetail{Kind: kind, Code: code}
				details[dk] = v
			}
			v.Count++
			if e.At > v.LastAt {
				v.LastAt = e.At
			}
		}
	}
	for _, r := range rows {
		if r.Intervals > 0 {
			r.TP = float64(r.intervalMs) / float64(r.Intervals) / 1000
			r.PPH = 3600 / r.TP
		}
		r.Notes = len(r.notes)
		r.Orders = len(r.orders)
		out.Notes += r.Notes
		out.Orders += r.Orders
		for n := range r.devices {
			r.Devices = append(r.Devices, n)
		}
		sort.Strings(r.Devices)
		out.Rows = append(out.Rows, r)
	}
	sort.Slice(out.Rows, func(i, j int) bool { return out.Rows[i].Key < out.Rows[j].Key })
	for _, d := range details {
		out.Details = append(out.Details, d)
	}
	sort.Slice(out.Details, func(i, j int) bool {
		if out.Details[i].LastAt != out.Details[j].LastAt {
			return out.Details[i].LastAt > out.Details[j].LastAt
		}
		return out.Details[i].Code < out.Details[j].Code
	})
	return out
}
func (s *Server) productivityAdmin(w http.ResponseWriter, r *http.Request) {
	if !s.admin(w, r) {
		return
	}
	if r.Method != "GET" {
		fail(w, 405, "metodo")
		return
	}
	start, end, err := productivityPeriod(r.URL.Query().Get("period"), r.URL.Query().Get("date"), time.Now())
	if err != nil {
		fail(w, 400, "período inválido")
		return
	}
	s.Lock()
	var result productivityReport
	if r.URL.Query().Get("ranking") == "1" {
		result = s.rankingReport(start, end, r.URL.Query().Get("operator"), r.URL.Query().Get("noteSearch"))
	} else {
		result = s.productivityReport(start, end, r.URL.Query().Get("operator"))
	}
	s.Unlock()
	reply(w, 200, result)
}
