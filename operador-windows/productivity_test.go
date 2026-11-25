package main

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestProductivityOfflineReplayAndMonthlyReset(t *testing.T) {
	path := filepath.Join(t.TempDir(), "dados.json")
	s, err := newServer(path, 8765)
	if err != nil {
		t.Fatal(err)
	}
	id := strings.Repeat("a", 32)
	id2 := strings.Repeat("b", 32)
	for _, dev := range []string{id, id2} {
		w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": dev, "name": "TC " + dev[:1], "battery": 80})
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	nov := time.Date(2026, 11, 30, 23, 59, 59, 0, productivityZone).UnixMilli()
	dec := nov + 1000
	e := func(i, kind, user, code, order string, at int64) ProductivityEvent {
		return ProductivityEvent{ID: strings.Repeat(i, 32), At: at, Kind: kind, Operator: user, Code: code, Order: order}
	}
	events := []ProductivityEvent{e("1", "note", "JOAO", "000123", "", nov), e("2", "note", "joao", "000123", "", nov), e("3", "product", "JOAO", "7890000000105", "000123", nov), e("4", "order", "JOAO", "000123", "000123", nov), e("5", "product", "", "789", "", nov), e("6", "note", "JOAO", "000124", "", dec)}
	body := map[string]interface{}{"id": id, "events": events}
	for i := 0; i < 2; i++ {
		w := testAPI(t, s, "POST", "/api/device/productivity", s.db.Token, body)
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
		var a struct {
			Accepted []string `json:"accepted"`
		}
		_ = json.Unmarshal(w.Body.Bytes(), &a)
		if len(a.Accepted) != 6 {
			t.Fatal(a)
		}
	}
	w := testAPI(t, s, "POST", "/api/device/productivity", s.db.Token, map[string]interface{}{"id": id2, "events": []ProductivityEvent{e("1", "product", "JOAO", "789", "000123", nov)}})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	// A torn final line must not corrupt committed batches after a restart.
	f, err := os.OpenFile(s.productivityPath(), os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		t.Fatal(err)
	}
	_, _ = f.WriteString("{\"events\":[")
	f.Close()
	s, err = newServer(path, 8765)
	if err != nil {
		t.Fatal(err)
	}
	if len(s.productivity) != 7 {
		t.Fatal(len(s.productivity))
	}
	w = testAPI(t, s, "GET", "/api/admin/productivity?period=month&date=2026-11-25&operator=user%3Ajoao", "", nil)
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var r productivityReport
	if err = json.Unmarshal(w.Body.Bytes(), &r); err != nil {
		t.Fatal(err)
	}
	if r.Notes != 1 || r.NoteScans != 2 || r.Products != 3 || r.Orders != 1 || r.Unknown != 1 || len(r.Details) != 3 {
		t.Fatalf("unexpected November: %+v", r)
	}
	w = testAPI(t, s, "GET", "/api/admin/productivity?period=month&date=2026-12-01", "", nil)
	var next productivityReport
	_ = json.Unmarshal(w.Body.Bytes(), &next)
	if next.Notes != 1 || next.Products != 0 || next.NoteScans != 1 {
		t.Fatalf("unexpected December: %+v", next)
	}
	if w = testAPI(t, s, "POST", "/api/device/productivity", "bad", body); w.Code != http.StatusUnauthorized {
		t.Fatal(w.Code)
	}
	if w = testAPI(t, s, "POST", "/api/device/productivity", s.db.Token, map[string]interface{}{"id": id, "events": []ProductivityEvent{e("7", "invalid", "JOAO", "123", "", nov)}}); w.Code != 400 {
		t.Fatal(w.Code)
	}
	start, end, err := productivityPeriod("week", "2026-11-25", time.Now())
	if err != nil || start.Format("2006-01-02") != "2026-11-23" || end.Format("2006-01-02") != "2026-11-30" {
		t.Fatal(start, end, err)
	}
}
