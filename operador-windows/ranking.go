package main

import (
	"encoding/json"
	"net/http"
	"os"
	"runtime"
	"sort"
	"strings"
	"time"
)

type NoteSession struct {
	DeviceID string `json:"deviceId"`
	Name     string `json:"name"`
	Operator string `json:"operator"`
	Note     string `json:"note"`
	Order    string `json:"order"`
	Started  int64  `json:"started"`
	Finished int64  `json:"finished"`
	Products int    `json:"products"`
	// Inferred transition, not ERP completion.
	NextNote string `json:"nextNote,omitempty"`
	eligible bool
}

func (s *Server) rankingEligible(e ProductivityEvent) bool {
	return s.db.RankingParticipants[e.DeviceID] && !s.db.RankingBlacklist[strings.ToLower(strings.TrimSpace(e.Operator))] && !strings.HasPrefix(e.Screen, "conferencia_")
}
func (s *Server) collectorName(id string) string {
	if d, ok := s.devices[id]; ok && strings.TrimSpace(d.Name) != "" {
		return d.Name
	}
	return "Coletor " + id
}
func (s *Server) noteSessions(events []ProductivityEvent) []NoteSession {
	active := map[string]*NoteSession{}
	sessions := []NoteSession{}
	for _, e := range events {
		n := active[e.DeviceID]
		switch e.Kind {
		case "note":
			if n != nil && n.Note == e.Code && strings.EqualFold(n.Operator, e.Operator) {
				continue
			}
			if n != nil {
				if n.Note != e.Code {
					n.Finished = e.At
					n.NextNote = e.Code
				}
				sessions = append(sessions, *n)
			}
			active[e.DeviceID] = &NoteSession{DeviceID: e.DeviceID, Name: s.collectorName(e.DeviceID), Operator: e.Operator, Note: e.Code, Started: e.At, eligible: s.rankingEligible(e)}
		case "order":
			if n != nil && strings.EqualFold(n.Operator, e.Operator) && n.Order == "" {
				n.Order = e.Order
			}
		case "product":
			if n != nil && strings.EqualFold(n.Operator, e.Operator) {
				if n.Order == "" {
					n.Order = e.Order
				}
				if e.Order == "" || n.Order == e.Order {
					n.Products++
				}
			}
		}
	}
	for _, n := range active {
		sessions = append(sessions, *n)
	}
	sort.SliceStable(sessions, func(i, j int) bool { return sessions[i].Started > sessions[j].Started })
	return sessions
}
func (s *Server) rankingReport(start, end time.Time, selected, search string) productivityReport {
	events := append([]ProductivityEvent(nil), s.productivity...)
	sort.SliceStable(events, func(i, j int) bool { return events[i].At < events[j].At })
	// Reuse the legacy metrics with collector names as the grouping key. An excluded
	// scan breaks the timing interval instead of bridging over a conference scan.
	filtered := []ProductivityEvent{}
	unknown := 0
	for _, e := range events {
		if !s.rankingEligible(e) {
			if e.Kind == "product" || e.Kind == "note" {
				e.Kind = "break"
				e.Operator = s.collectorName(e.DeviceID)
				filtered = append(filtered, e)
			}
			continue
		}
		if e.Operator == "" && e.At >= start.UnixMilli() && e.At < end.UnixMilli() {
			unknown++
		}
		e.RankLogin = e.Operator
		e.Operator = s.collectorName(e.DeviceID)
		filtered = append(filtered, e)
	}
	tmp := &Server{devices: s.devices, productivity: filtered}
	out := tmp.productivityReport(start, end, selected)
	out.Unknown = unknown
	byKey := map[string]*ProductivityRow{}
	for _, row := range out.Rows {
		byKey[row.Key] = row
	}
	out.Sessions = []NoteSession{}
	search = strings.ToLower(strings.TrimSpace(search))
	for _, n := range s.noteSessions(events) {
		if n.eligible && n.Finished >= start.UnixMilli() && n.Finished < end.UnixMilli() {
			key := "user:" + strings.ToLower(n.Name)
			row := byKey[key]
			if row == nil {
				row = &ProductivityRow{Key: key, Name: n.Name, Identified: true, Devices: []string{n.Name}, LastAt: n.Finished}
				out.Rows = append(out.Rows, row)
				byKey[key] = row
			}
			row.Completed++
			out.Completed++
		}
		// Include notes started or closed in the period; search includes login and collector.
		if !(n.Started >= start.UnixMilli() && n.Started < end.UnixMilli() || n.Finished >= start.UnixMilli() && n.Finished < end.UnixMilli()) {
			continue
		}
		if search != "" && !strings.Contains(strings.ToLower(n.Note+" "+n.Order+" "+n.Name+" "+n.Operator), search) {
			continue
		}
		out.SessionsTotal++
		if len(out.Sessions) < 500 {
			out.Sessions = append(out.Sessions, n)
		}
	}
	return out
}
func (s *Server) rankingSettings(w http.ResponseWriter, r *http.Request) {
	if !s.admin(w, r) {
		return
	}
	s.Lock()
	defer s.Unlock()
	if r.Method == "GET" {
		users := map[string]string{}
		for _, d := range s.devices {
			for _, u := range []string{d.MlogOperator, d.LastMlogOperator} {
				if u != "" {
					users[strings.ToLower(u)] = u
				}
			}
		}
		for _, e := range s.productivity {
			if e.Operator != "" {
				users[strings.ToLower(e.Operator)] = e.Operator
			}
		}
		for u := range s.db.RankingBlacklist {
			if users[u] == "" {
				users[u] = u
			}
		}
		reply(w, 200, map[string]interface{}{"participants": s.db.RankingParticipants, "blacklist": s.db.RankingBlacklist, "users": users})
		return
	}
	if r.Method != "POST" {
		fail(w, 405, "metodo")
		return
	}
	var in struct {
		DeviceID string `json:"deviceId"`
		Operator string `json:"operator"`
		Enabled  bool   `json:"enabled"`
	}
	if !readJSON(w, r, &in) {
		return
	}
	if (in.DeviceID == "") == (in.Operator == "") {
		fail(w, 400, "selecione um coletor ou login")
		return
	}
	oldP, oldB := s.db.RankingParticipants, s.db.RankingBlacklist
	s.db.RankingParticipants = copyFlags(oldP)
	s.db.RankingBlacklist = copyFlags(oldB)
	if in.DeviceID != "" {
		if _, ok := s.devices[in.DeviceID]; !ok {
			s.db.RankingParticipants = oldP
			s.db.RankingBlacklist = oldB
			fail(w, 404, "coletor não encontrado")
			return
		}
		if in.Enabled {
			s.db.RankingParticipants[in.DeviceID] = true
		} else {
			delete(s.db.RankingParticipants, in.DeviceID)
		}
	} else {
		u := strings.ToLower(strings.TrimSpace(in.Operator))
		if !validMlogUsername(u) {
			s.db.RankingParticipants = oldP
			s.db.RankingBlacklist = oldB
			fail(w, 400, "login inválido")
			return
		}
		if in.Enabled {
			s.db.RankingBlacklist[u] = true
		} else {
			delete(s.db.RankingBlacklist, u)
		}
	}
	if err := s.save(); err != nil {
		s.db.RankingParticipants = oldP
		s.db.RankingBlacklist = oldB
		fail(w, 500, err.Error())
		return
	}
	reply(w, 200, map[string]bool{"ok": true})
}
func copyFlags(m map[string]bool) map[string]bool {
	n := map[string]bool{}
	for k, v := range m {
		n[k] = v
	}
	return n
}
func (s *Server) deleteCollector(w http.ResponseWriter, r *http.Request) {
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
	d, ok := s.devices[in.ID]
	if !ok {
		fail(w, 404, "coletor não encontrado")
		return
	}
	if t, err := time.Parse(time.RFC3339, d.LastSeen); err == nil && t.After(time.Now().Add(-75*time.Second)) {
		fail(w, 409, "desligue o coletor e aguarde ficar offline")
		return
	}
	// Persist a cutoff before pruning the journal; an old offline batch cannot restore history.
	old, _ := json.Marshal(s.db)
	if s.db.DeletedCollectorBefore == nil {
		s.db.DeletedCollectorBefore = map[string]int64{}
	}
	s.db.DeletedCollectorBefore[in.ID] = time.Now().UnixMilli()
	delete(s.db.Devices, in.ID)
	delete(s.db.RankingParticipants, in.ID)
	for i := len(s.db.DeviceOrder) - 1; i >= 0; i-- {
		if s.db.DeviceOrder[i] == in.ID {
			s.db.DeviceOrder = append(s.db.DeviceOrder[:i], s.db.DeviceOrder[i+1:]...)
		}
	}
	for i := len(s.db.Actions) - 1; i >= 0; i-- {
		if s.db.Actions[i].DeviceID == in.ID {
			s.db.Actions = append(s.db.Actions[:i], s.db.Actions[i+1:]...)
		}
	}
	for i := len(s.db.Deployments) - 1; i >= 0; i-- {
		if s.db.Deployments[i].DeviceID == in.ID {
			s.db.Deployments = append(s.db.Deployments[:i], s.db.Deployments[i+1:]...)
		}
	}
	for i := range s.db.Broadcasts {
		b := &s.db.Broadcasts[i]
		for j := len(b.Targets) - 1; j >= 0; j-- {
			if b.Targets[j] == in.ID {
				b.Targets = append(b.Targets[:j], b.Targets[j+1:]...)
			}
		}
		delete(b.Delivered, in.ID)
		delete(b.Received, in.ID)
		delete(b.Seen, in.ID)
		delete(b.TargetNames, in.ID)
	}
	// Preserve shared order observations and their completion state, unlink collector identity.
	for i := range s.db.Notes {
		n := &s.db.Notes[i]
		if n.CompletedByID == in.ID {
			n.CompletedByID = ""
			n.CompletedBy = ""
		}
	}
	if err := s.save(); err != nil {
		_ = json.Unmarshal(old, &s.db)
		fail(w, 500, err.Error())
		return
	}
	delete(s.devices, in.ID)
	kept := []ProductivityEvent{}
	s.productivityIDs = map[string]bool{}
	for _, e := range s.productivity {
		if e.DeviceID != in.ID {
			kept = append(kept, e)
			s.productivityIDs[e.key()] = true
		}
	}
	s.productivity = kept
	if err := s.compactProductivity(); err != nil {
		reply(w, 200, map[string]interface{}{"ok": true, "warning": "Coletor removido; limpeza física do histórico falhou: " + err.Error()})
		return
	}
	reply(w, 200, map[string]bool{"ok": true})
}
func (s *Server) compactProductivity() error {
	path := s.productivityPath()
	tmp := path + ".tmp"
	bak := path + ".bak"
	f, err := os.OpenFile(tmp, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0600)
	if err != nil {
		return err
	}
	enc := json.NewEncoder(f)
	for i := 0; i < len(s.productivity); i += 256 {
		end := i + 256
		if end > len(s.productivity) {
			end = len(s.productivity)
		}
		if err = enc.Encode(productivityBatch{s.productivity[i:end]}); err != nil {
			f.Close()
			return err
		}
	}
	err = f.Sync()
	closeErr := f.Close()
	if err != nil {
		return err
	}
	if closeErr != nil {
		return closeErr
	}
	if runtime.GOOS == "windows" {
		_ = os.Remove(bak)
		if _, err = os.Stat(path); err == nil {
			if err = os.Rename(path, bak); err != nil {
				return err
			}
		}
		if err = os.Rename(tmp, path); err != nil {
			_ = os.Rename(bak, path)
			return err
		}
		_ = os.Remove(bak)
		return nil
	}
	return os.Rename(tmp, path)
}
