package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func sendFleet(t *testing.T, s *Server, path, body string) {
	t.Helper()
	r := httptest.NewRequest(http.MethodPost, path, strings.NewReader(body))
	r.Header.Set("Content-Type", "application/json")
	r.Header.Set("X-Bridge-Token", s.db.Token)
	r.RemoteAddr = "192.168.1.10:5555"
	w := httptest.NewRecorder()
	s.handle(w, r)
	if w.Code != 200 {
		t.Fatalf("%s: %d %s", path, w.Code, w.Body.String())
	}
}
func TestTelemetryRenameOfflineAndPersist(t *testing.T) {
	id := "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	db := filepath.Join(t.TempDir(), "dados.json")
	s, e := newServer(db, 8765)
	if e != nil {
		t.Fatal(e)
	}
	body := `{"id":"` + id + `","name":"TC60 · 0001","order":"00136","bridgeVersion":"3.6.15","androidVersion":"13","model":"TC60","shizuku":true,"accessibility":true,"mlogForeground":true,"recentActivity":true,"battery":79}`
	sendFleet(t, s, "/api/device/heartbeat", body)
	if !s.devices[id].Online || s.devices[id].OrdersDetected != 1 || s.devices[id].BridgeVersion != "3.6.15" {
		t.Fatalf("telemetry missing: %+v", s.devices[id])
	}
	sendFleet(t, s, "/api/device/heartbeat", body)
	if s.devices[id].OrdersDetected != 1 {
		t.Fatal("duplicate heartbeat counted same order twice")
	}
	r := httptest.NewRequest("POST", "/api/admin/device/rename", strings.NewReader(`{"id":"`+id+`","name":"Ismael - Coletor 01"}`))
	r.Header.Set("Content-Type", "application/json")
	r.RemoteAddr = "127.0.0.1:1234"
	w := httptest.NewRecorder()
	s.handle(w, r)
	if w.Code != 200 {
		t.Fatalf("rename %d: %s", w.Code, w.Body.String())
	}
	rr := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/device/heartbeat", strings.NewReader(body))
	req.RemoteAddr = "192.168.1.10:5555"
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Bridge-Token", s.db.Token)
	s.handle(rr, req)
	var reply map[string]interface{}
	if e := json.Unmarshal(rr.Body.Bytes(), &reply); e != nil {
		t.Fatal(e)
	}
	if reply["preferredName"] != "Ismael - Coletor 01" {
		t.Fatalf("unexpected rename sync: %v", reply)
	}
	if s.devices[id].NamePending {
		t.Fatal("rename pending after sync")
	}
	// A fresh process should remember offline devices, even before any new heartbeat.
	newer, e := newServer(db, 8765)
	if e != nil {
		t.Fatal(e)
	}
	d := newer.allDevicesLocked()
	if len(d) != 1 {
		t.Fatalf("expected remembered device: %+v", d)
	}
	// The old heartbeat may have been seconds ago; simulate a truly offline collector.
	x := newer.devices[id]
	x.LastSeen = time.Now().Add(-3 * time.Hour).Format(time.RFC3339)
	newer.devices[id] = x
	if newer.allDevicesLocked()[0].Online {
		t.Fatal("offline device still marked online")
	}
}
func TestBackwardsCompatibleHeartbeat(t *testing.T) {
	s, e := newServer(filepath.Join(t.TempDir(), "data.json"), 8765)
	if e != nil {
		t.Fatal(e)
	}
	sendFleet(t, s, "/api/device/heartbeat", `{"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","name":"Antigo","order":"12"}`)
	if !s.devices["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"].Online {
		t.Fatal("legacy heartbeat rejected")
	}
}
