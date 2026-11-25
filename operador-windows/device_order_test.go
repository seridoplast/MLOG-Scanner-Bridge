package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"testing"
	"time"
)

func assertOrder(t *testing.T, s *Server, want ...string) {
	t.Helper()
	got := []string{}
	for _, d := range s.allDevicesLocked() {
		got = append(got, d.ID)
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("order: got %v want %v", got, want)
	}
}
func TestFixedOrderAndRestart(t *testing.T) {
	a, b, c := "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccccccccccccccccccccc"
	path := filepath.Join(t.TempDir(), "dados.json")
	s, e := newServer(path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	sendFleet(t, s, "/api/device/heartbeat", `{"id":"`+a+`","name":"Zeta","bridgeVersion":"3.6.25"}`)
	sendFleet(t, s, "/api/device/heartbeat", `{"id":"`+b+`","name":"Alpha","bridgeVersion":"3.6.25"}`)
	assertOrder(t, s, a, b)
	d := s.devices[a]
	d.LastSeen = time.Now().Add(-time.Hour).Format(time.RFC3339)
	s.devices[a] = d
	s.db.Devices[a] = d
	assertOrder(t, s, a, b)
	sendFleet(t, s, "/api/device/heartbeat", `{"id":"`+b+`","name":"Omega","bridgeVersion":"3.6.25"}`)
	sendFleet(t, s, "/api/device/heartbeat", `{"id":"`+c+`","name":"Aardvark","bridgeVersion":"3.6.25"}`)
	assertOrder(t, s, a, b, c)
	s, e = newServer(path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	assertOrder(t, s, a, b, c)
	sendFleet(t, s, "/api/device/heartbeat", `{"id":"`+a+`","name":"AAA","bridgeVersion":"3.6.25"}`)
	assertOrder(t, s, a, b, c)
}
func TestLegacyOrderMigration(t *testing.T) {
	path := filepath.Join(t.TempDir(), "dados.json")
	db := DB{Token: "existing", Devices: map[string]Device{"a": {ID: "a", Name: "Alpha", LastSeen: time.Now().Add(-time.Hour).Format(time.RFC3339)}, "b": {ID: "b", Name: "Beta", LastSeen: time.Now().Format(time.RFC3339)}}}
	data, _ := json.Marshal(db)
	if e := os.WriteFile(path, data, 0600); e != nil {
		t.Fatal(e)
	}
	s, e := newServer(path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	assertOrder(t, s, "b", "a")
	d := s.devices["b"]
	d.LastSeen = ""
	s.devices["b"] = d
	assertOrder(t, s, "b", "a")
	s, e = newServer(path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	assertOrder(t, s, "b", "a")
}
