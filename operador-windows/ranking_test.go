package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestRankingV4ParticipationBlacklistAndSessions(t *testing.T) {
	s, err := newServer(filepath.Join(t.TempDir(), "dados.json"), 8765)
	if err != nil {
		t.Fatal(err)
	}
	a, b := strings.Repeat("a", 32), strings.Repeat("b", 32)
	for id, name := range map[string]string{a: "Arthur Silva", b: "Conferente"} {
		w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id, "name": name, "battery": 80})
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	start, end, _ := productivityPeriod("day", "2026-11-27", time.Now())
	at := start.UnixMilli() + 10000
	event := func(id, kind, op, code, screen, dev string, delta int64) ProductivityEvent {
		return ProductivityEvent{ID: strings.Repeat(id, 32), At: at + delta, Kind: kind, Operator: op, Code: code, Screen: screen, DeviceID: dev}
	}
	// Deliberately append out of order: close on the next distinct scan, not on replay or repeat.
	es := []ProductivityEvent{event("1", "note", "ARTHURJ", "001", "notas", a, 0), event("2", "note", "ARTHURJ", "002", "notas", a, 5000), event("3", "product", "ARTHURJ", "789", "separacao", a, 2000), event("4", "note", "ARTHURJ", "001", "notas", a, 1000), event("5", "product", "CONFERENTE", "999", "separacao", a, 6000), event("6", "product", "ARTHURJ", "998", "conferencia_produto", a, 7000), event("7", "product", "ARTHURJ", "888", "separacao", b, 3000)}
	if err = s.appendProductivity(es); err != nil {
		t.Fatal(err)
	}
	if r := s.rankingReport(start, end, "", ""); len(r.Rows) != 0 || r.SessionsTotal != 2 {
		t.Fatalf("expected opt-in and history: %+v", r)
	}
	for _, body := range []map[string]interface{}{{"deviceId": a, "enabled": true}, {"operator": "Conferente", "enabled": true}} {
		w := testAPI(t, s, "POST", "/api/admin/ranking-settings", "", body)
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	r := s.rankingReport(start, end, "user:arthur silva", "")
	if len(r.Rows) != 1 || r.Rows[0].Name != "Arthur Silva" || r.Products != 1 || r.Completed != 1 || r.NoteScans != 3 || r.Rows[0].Completed != 1 {
		t.Fatalf("unexpected rank: %+v rows %+v", r, r.Rows)
	}
	if r.Sessions[0].Note != "002" || r.Sessions[0].Finished != 0 || r.Sessions[1].Note != "001" || r.Sessions[1].Finished != at+5000 || r.Sessions[1].Products != 1 {
		t.Fatalf("sessions %+v", r.Sessions)
	}
	if r = s.rankingReport(start, end, "", "001"); r.SessionsTotal != 1 || r.Completed != 1 {
		t.Fatal(r)
	}
	s, err = newServer(s.path, 8765)
	if err != nil {
		t.Fatal(err)
	}
	if !s.db.RankingParticipants[a] || !s.db.RankingBlacklist["conferente"] {
		t.Fatal("settings not persisted")
	}
	w := testAPI(t, s, "GET", "/api/admin/productivity?ranking=1&period=day&date=2026-11-27", "", nil)
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if err = json.Unmarshal(w.Body.Bytes(), &r); err != nil || r.Completed != 1 {
		t.Fatal(r, err)
	}
}
func TestDeleteCollectorOfflineAndOldReplay(t *testing.T) {
	path := filepath.Join(t.TempDir(), "dados.json")
	s, err := newServer(path, 8765)
	if err != nil {
		t.Fatal(err)
	}
	a, b := strings.Repeat("a", 32), strings.Repeat("b", 32)
	for _, id := range []string{a, b} {
		w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id, "name": "TC", "battery": 80})
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	old := ProductivityEvent{ID: strings.Repeat("1", 32), At: time.Now().Add(-time.Hour).UnixMilli(), Kind: "note", Code: "123", DeviceID: a}
	other := old
	other.DeviceID = b
	if err = s.appendProductivity([]ProductivityEvent{old, other}); err != nil {
		t.Fatal(err)
	}
	s.db.RankingParticipants = map[string]bool{a: true, b: true}
	s.db.Actions = []FleetAction{{DeviceID: a}, {DeviceID: b}}
	s.db.Deployments = []Deployment{{DeviceID: a}, {DeviceID: b}}
	w := testAPI(t, s, "POST", "/api/admin/device/delete", "", map[string]string{"id": a})
	if w.Code != 409 {
		t.Fatal(w.Code, w.Body.String())
	}
	d := s.devices[a]
	d.LastSeen = time.Now().Add(-2 * time.Minute).Format(time.RFC3339)
	s.devices[a] = d
	w = testAPI(t, s, "POST", "/api/admin/device/delete", "", map[string]string{"id": a})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if _, ok := s.devices[a]; ok || s.db.RankingParticipants[a] || len(s.db.Actions) != 1 || len(s.db.Deployments) != 1 || len(s.productivity) != 1 {
		t.Fatal("not removed")
	}
	data, err := os.ReadFile(s.productivityPath())
	if err != nil || strings.Contains(string(data), a) {
		t.Fatal("journal still contains deleted device", err)
	}
	s, err = newServer(path, 8765)
	if err != nil {
		t.Fatal(err)
	}
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": a, "name": "TC", "battery": 80})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if s.db.RankingParticipants[a] || s.devices[a].OrdersDetected != 0 {
		t.Fatal("old collector state restored")
	}
	newer := old
	newer.ID = strings.Repeat("2", 32)
	newer.At = s.db.DeletedCollectorBefore[a] + 1
	old.DeviceID = ""
	newer.DeviceID = ""
	w = testAPI(t, s, "POST", "/api/device/productivity", s.db.Token, map[string]interface{}{"id": a, "events": []ProductivityEvent{old, newer}})
	if w.Code != 200 || len(s.productivity) != 2 {
		t.Fatal(w.Code, w.Body.String(), len(s.productivity))
	}
}
