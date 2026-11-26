package main

import (
	"encoding/json"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestTempoPorProduto(t *testing.T) {
	s, err := newServer(filepath.Join(t.TempDir(), "dados.json"), 8765)
	if err != nil {
		t.Fatal(err)
	}
	day := time.Date(2026, 11, 26, 0, 0, 0, 0, productivityZone)
	base := day.Add(time.Hour).UnixMilli()
	e := func(dev, kind, order string, secs int64) ProductivityEvent {
		return ProductivityEvent{DeviceID: dev, Kind: kind, Order: order, Operator: "joao", Code: "123", At: base + secs*1000}
	}
	s.productivity = []ProductivityEvent{e("a", "product", "1", 30), e("a", "product", "1", 0), e("a", "product", "1", 10), e("b", "product", "1", 20), e("a", "note", "", 31), e("a", "product", "2", 32), e("a", "product", "2", 42), e("a", "product", "2", 400), e("a", "product", "2", 400)}
	r := s.productivityReport(day, day.AddDate(0, 0, 1), "").Rows[0]
	if r.Intervals != 3 || r.Products != 8 || r.TP != 40.0/3 || r.PPH != 270 {
		t.Fatalf("unexpected metrics: %+v", r)
	}
}
func TestResetAndActivity(t *testing.T) {
	s, err := newServer(filepath.Join(t.TempDir(), "dados.json"), 8765)
	if err != nil {
		t.Fatal(err)
	}
	id := strings.Repeat("a", 32)
	hb := map[string]interface{}{"id": id, "name": "Joao", "model": "TC60", "accessibility": true, "lastActivityAgeMs": int64(1800000), "battery": 80}
	if w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, hb); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := testAPI(t, s, "POST", "/api/admin/device/reset-name", "", map[string]interface{}{"all": true}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, hb)
	var resp map[string]interface{}
	_ = json.Unmarshal(w.Body.Bytes(), &resp)
	if resp["preferredName"] != "TC60 · aaaa" || resp["resetName"] != true {
		t.Fatal(resp)
	}
	hb["name"] = "TC60 · aaaa"
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, hb)
	if w.Code != 200 || s.devices[id].ResetNamePending {
		t.Fatal(w.Body.String())
	}
	event := ProductivityEvent{ID: strings.Repeat("1", 32), At: time.Now().Add(-time.Second).UnixMilli(), Kind: "product", Operator: "joao", Code: "123"}
	w = testAPI(t, s, "POST", "/api/device/productivity", s.db.Token, map[string]interface{}{"id": id, "events": []ProductivityEvent{event}})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	recent := s.devices[id].LastActivityAt
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, hb)
	if w.Code != 200 || s.devices[id].LastActivityAt != recent {
		t.Fatal("old heartbeat overwrote scan")
	}
}
func TestRemoteWebGuard(t *testing.T) {
	s, err := newServer(filepath.Join(t.TempDir(), "dados.json"), 8765)
	if err != nil {
		t.Fatal(err)
	}
	h := s.webAdminHandler("admin", "a-long-test-password")
	check := func(method, path, origin string, auth bool, want int) {
		r := httptest.NewRequest(method, "https://central.local"+path, nil)
		r.RemoteAddr = "192.168.1.5:1234"
		if auth {
			r.SetBasicAuth("admin", "a-long-test-password")
		}
		if origin != "" {
			r.Header.Set("Origin", origin)
		}
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		if w.Code != want {
			t.Fatalf("%s %d want %d", path, w.Code, want)
		}
		if path == "/api/admin/state" && want == 200 && strings.Contains(w.Body.String(), s.db.Token) {
			t.Fatal("token exposed")
		}
	}
	check("GET", "/api/admin/state", "", false, 401)
	check("GET", "/api/admin/state", "", true, 200)
	check("POST", "/api/admin/device/reset-name", "https://evil.local", true, 403)
	check("GET", "/api/ping", "", true, 404)
}
func TestActivityState(t *testing.T) {
	now := time.Now()
	d := Device{Online: true, Accessibility: true, LastActivityAt: now.Add(-25 * time.Minute).Format(time.RFC3339)}
	if activityState(d, now) != "idle" {
		t.Fatal("idle")
	}
	d.RecentActivity = true
	if activityState(d, now) != "active" {
		t.Fatal("recent")
	}
	d.RecentActivity = false
	d.Accessibility = false
	if activityState(d, now) != "unknown" {
		t.Fatal("unknown")
	}
	d.LastActivityAt = now.Add(-time.Second).Format(time.RFC3339)
	if activityState(d, now) != "active" {
		t.Fatal("scan")
	}
}
