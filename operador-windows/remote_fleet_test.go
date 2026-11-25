package main

import (
	"encoding/json"
	"path/filepath"
	"testing"
	"time"
)

func TestFleetActionAllowlistAndReceipt(t *testing.T) {
	s, e := newServer(filepath.Join(t.TempDir(), "dados.json"), 8765)
	if e != nil {
		t.Fatal(e)
	}
	id1 := "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	id2 := "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
	for _, v := range []struct {
		id    string
		ready bool
	}{{id1, true}, {id2, false}} {
		w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": v.id, "name": "Coletor", "shizuku": v.ready, "battery": 80})
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	// Arbitrary commands must never be accepted over LAN.
	w := testAPI(t, s, "POST", "/api/admin/device/command", "", map[string]string{"kind": "shell", "target": "all_online"})
	if w.Code != 400 {
		t.Fatalf("unexpected remote shell acceptance: %d", w.Code)
	}
	// Removed third-party assist and keep explicit individual update requests.
	w = testAPI(t, s, "POST", "/api/admin/device/command", "", map[string]string{"kind": "shizuku_assist", "target": "all_online"})
	if w.Code != 400 {
		t.Fatal("obsolete command accepted")
	}
	w = testAPI(t, s, "POST", "/api/admin/device/command", "", map[string]string{"kind": "check_stable_update", "target": "single", "deviceId": id2})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var result struct {
		Queued  int `json:"queued"`
		Skipped int `json:"skipped"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &result); err != nil {
		t.Fatal(err)
	}
	if result.Queued != 1 || result.Skipped != 0 {
		t.Fatalf("unexpected counts: %+v", result)
	}
	action := s.db.Actions[0]
	if action.DeviceID != id2 {
		t.Fatalf("wrong recipient: %+v", action)
	}
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id2, "name": "Coletor", "shizuku": false})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var response struct {
		Commands []FleetAction `json:"commands"`
	}
	_ = json.Unmarshal(w.Body.Bytes(), &response)
	if len(response.Commands) != 1 || response.Commands[0].ID != action.ID {
		t.Fatalf("command not delivered: %s", w.Body.String())
	}
	// Acknowledgements must be bound to the intended collector.
	w = testAPI(t, s, "POST", "/api/device/command-result", s.db.Token, map[string]string{"id": id1, "commandId": action.ID, "result": "fake"})
	if w.Code != 404 {
		t.Fatalf("foreign ack accepted: %d", w.Code)
	}
	w = testAPI(t, s, "POST", "/api/device/command-result", s.db.Token, map[string]string{"id": id2, "commandId": action.ID, "result": "Consulta agendada"})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id2, "name": "Coletor", "shizuku": false})
	_ = json.Unmarshal(w.Body.Bytes(), &response)
	if len(response.Commands) != 0 {
		t.Fatal("already acknowledged command redelivered")
	}
}
func TestPersistOfflineObservationsAndLastActivity(t *testing.T) {
	filename := filepath.Join(t.TempDir(), "dados.json")
	s, e := newServer(filename, 8765)
	if e != nil {
		t.Fatal(e)
	}
	id := "cccccccccccccccccccccccccccccccc"
	w := testAPI(t, s, "POST", "/api/admin/save", "", map[string]string{"order": "0000123", "text": "Verificar etiqueta de lote"})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id, "name": "Separador 01", "order": "0000123", "battery": 50, "shizuku": true, "recentActivity": false, "lastActivityAgeMs": int64(22 * 60 * 1000)})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	first := s.devices[id]
	if first.LastActivityAt == "" || len(first.LastObservations) != 1 {
		t.Fatalf("not preserved: %+v", first)
	}
	at, err := time.Parse(time.RFC3339, first.LastActivityAt)
	if err != nil || time.Since(at) < 20*time.Minute {
		t.Fatal("idle time incorrectly calculated")
	}
	another, e := newServer(filename, 8765)
	if e != nil {
		t.Fatal(e)
	}
	d := another.devices[id]
	if len(d.LastObservations) != 1 || d.LastObservations[0].Text != "Verificar etiqueta de lote" || d.LastOrder != "0000123" {
		t.Fatalf("offline state not saved: %+v", d)
	}
	// Once the collector is actually offline, keep historical observations in the UI.
	d.LastSeen = time.Now().Add(-10 * time.Minute).Format(time.RFC3339)
	another.devices[id] = d
	if another.allDevicesLocked()[0].Online {
		t.Fatal("should be offline")
	}
	// Delete means delete from historical snapshots as well.
	w = testAPI(t, another, "POST", "/api/admin/delete", "", map[string]string{"id": another.db.Notes[0].ID})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(another.devices[id].LastObservations) != 0 {
		t.Fatal("deleted observation survives")
	}
}

func TestMlogOperatorSavedInCollectorProfile(t *testing.T) {
	filename := filepath.Join(t.TempDir(), "dados.json")
	s, err := newServer(filename, 8765)
	if err != nil {
		t.Fatal(err)
	}
	id := "dddddddddddddddddddddddddddddddd"
	w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{
		"id": id, "name": "Coletor 04", "mlogOperator": "JARLENE", "lastMlogOperator": "JARLENE"})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if s.devices[id].MlogOperator != "JARLENE" {
		t.Fatal("operator missing")
	}
	reopened, err := newServer(filename, 8765)
	if err != nil {
		t.Fatal(err)
	}
	if reopened.db.Devices[id].LastMlogOperator != "JARLENE" {
		t.Fatal("last known operator not persisted")
	}
	w = testAPI(t, reopened, "POST", "/api/device/heartbeat", reopened.db.Token, map[string]interface{}{
		"id": id, "name": "Coletor 04", "mlogOperator": "", "lastMlogOperator": "JARLENE"})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if reopened.devices[id].MlogOperator != "" || reopened.devices[id].LastMlogOperator != "JARLENE" {
		t.Fatal("logout state incorrect")
	}
}

func TestPausedMessagesNotOffered(t *testing.T) {
	s, err := newServer(filepath.Join(t.TempDir(), "paused.json"), 8765)
	if err != nil {
		t.Fatal(err)
	}
	id := "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
	hb := map[string]interface{}{"id": id, "name": "TC60", "messagesPaused": true}
	if w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, hb); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]string{"action": "send", "text": "historico"}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, hb)
	var result struct {
		Broadcasts []Broadcast `json:"broadcasts"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &result); err != nil {
		t.Fatal(err)
	}
	if len(result.Broadcasts) != 0 || len(s.db.Broadcasts[0].Delivered) != 0 {
		t.Fatal("paused message was offered or marked delivered")
	}
}
