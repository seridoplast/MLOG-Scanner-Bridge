package main

import (
	"bytes"
	"encoding/json"
	"net"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func testAPI(t *testing.T, s *Server, method, path, token string, body interface{}) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	if body != nil {
		_ = json.NewEncoder(&buf).Encode(body)
	}
	req := httptest.NewRequest(method, path, &buf)
	req.RemoteAddr = "127.0.0.1:54321"
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("X-Bridge-Token", token)
	}
	w := httptest.NewRecorder()
	s.handle(w, req)
	return w
}
func TestLifecycleAndAuth(t *testing.T) {
	path := filepath.Join(t.TempDir(), "data.json")
	s, e := newServer(path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	if len(s.db.Token) != 64 {
		t.Fatalf("token length %d", len(s.db.Token))
	}
	if w := testAPI(t, s, "GET", "/api/notes?order=0000123", "bad", nil); w.Code != 401 {
		t.Fatalf("unauthorized returned %d", w.Code)
	}
	if w := testAPI(t, s, "POST", "/api/admin/save", "", map[string]string{"order": "0000123", "text": "Separar 2 caixas"}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := testAPI(t, s, "POST", "/api/admin/save", "", map[string]string{"order": "0000123", "text": "Conferir lacres"}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	w := testAPI(t, s, "GET", "/api/notes?order=0000123", s.db.Token, nil)
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var resp struct {
		Notes []Note `json:"notes"`
	}
	if e := json.Unmarshal(w.Body.Bytes(), &resp); e != nil {
		t.Fatal(e)
	}
	if len(resp.Notes) != 2 {
		t.Fatalf("expected 2 notes got %d", len(resp.Notes))
	}
	if w := testAPI(t, s, "GET", "/api/notes?order=123", s.db.Token, nil); w.Code != 200 {
		t.Fatal(w.Body.String())
	} else {
		var other struct {
			Notes []Note `json:"notes"`
		}
		_ = json.Unmarshal(w.Body.Bytes(), &other)
		if len(other.Notes) != 0 {
			t.Fatal("leading zeroes were discarded")
		}
	}
	ids := []string{resp.Notes[0].ID, resp.Notes[1].ID}
	w = testAPI(t, s, "POST", "/api/complete", s.db.Token, map[string]interface{}{"order": "0000123", "ids": ids, "device": "Coletor-1"})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := testAPI(t, s, "GET", "/api/notes?order=0000123", s.db.Token, nil); w.Code != 200 {
		t.Fatal(w.Body.String())
	} else {
		var zero struct {
			Notes []Note `json:"notes"`
		}
		_ = json.Unmarshal(w.Body.Bytes(), &zero)
		if len(zero.Notes) != 0 {
			t.Fatal("complete should clear pending notes")
		}
	}
	reopened, e := newServer(path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	if len(reopened.db.Notes) != 2 || reopened.db.Notes[0].Status != "done" {
		t.Fatal("completion was not persisted")
	}
	if w := testAPI(t, reopened, "POST", "/api/admin/status", "", map[string]string{"id": ids[0], "status": "pending"}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := testAPI(t, reopened, "GET", "/api/notes?order=0000123", s.db.Token, nil); w.Code != 200 {
		t.Fatal(w.Body.String())
	} else {
		var one struct {
			Notes []Note `json:"notes"`
		}
		_ = json.Unmarshal(w.Body.Bytes(), &one)
		if len(one.Notes) != 1 {
			t.Fatal("reopened note not served")
		}
	}
}
func TestAdminRestrictedToLoopback(t *testing.T) {
	s, e := newServer(filepath.Join(t.TempDir(), "db.json"), 8765)
	if e != nil {
		t.Fatal(e)
	}
	req := httptest.NewRequest("GET", "/api/admin/state", nil)
	req.RemoteAddr = "192.168.1.44:1234"
	w := httptest.NewRecorder()
	s.handle(w, req)
	if w.Code != 403 {
		t.Fatalf("remote admin allowed: %d", w.Code)
	}
}
func TestOrderValidation(t *testing.T) {
	for _, order := range []string{"  ", "1 2", "a?3"} {
		if validOrder(normalizeOrder(order)) {
			t.Fatalf("accepted %q", order)
		}
	}
	for _, order := range []string{"000001", "A-8/1", "1_2"} {
		if !validOrder(order) {
			t.Fatalf("rejected %q", order)
		}
	}
}
func TestFrontendEmbedded(t *testing.T) {
	s, e := newServer(filepath.Join(t.TempDir(), "db.json"), 8765)
	if e != nil {
		t.Fatal(e)
	}
	w := testAPI(t, s, "GET", "/", "", nil)
	if w.Code != 200 || !bytes.Contains(w.Body.Bytes(), []byte("Observações")) {
		t.Fatalf("frontend not embedded %d", w.Code)
	}
}

func TestBroadcastAllConnectedAndAcks(t *testing.T) {
	s, err := newServer(filepath.Join(t.TempDir(), "broadcasts.json"), 8765)
	if err != nil {
		t.Fatal(err)
	}
	d1 := "00000000000000000000000000000001"
	d2 := "00000000000000000000000000000002"
	for _, d := range []string{d1, d2} {
		w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": d, "name": "Coletor " + d[len(d)-1:], "order": ""})
		if w.Code != 200 {
			t.Fatalf("heartbeat %s: %s", d, w.Body.String())
		}
	}
	w := testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]interface{}{"action": "send", "text": "Pedido prioritario no CD"})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var sent struct {
		Recipients int `json:"recipients"`
	}
	_ = json.Unmarshal(w.Body.Bytes(), &sent)
	if sent.Recipients != 2 {
		t.Fatalf("wanted 2 targets got %d", sent.Recipients)
	}
	if len(s.db.Broadcasts) != 1 {
		t.Fatal("missing announcement")
	}
	bid := s.db.Broadcasts[0].ID
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": d1, "name": "Coletor 1", "order": "00001"})
	var received struct {
		Broadcasts []Broadcast `json:"broadcasts"`
	}
	_ = json.Unmarshal(w.Body.Bytes(), &received)
	if len(received.Broadcasts) != 1 {
		t.Fatalf("not delivered: %s", w.Body.String())
	}
	if len(s.db.Broadcasts[0].Received) != 0 {
		t.Fatal("server response must not count as app receipt")
	}
	if w = testAPI(t, s, "POST", "/api/device/received", s.db.Token, map[string]interface{}{"id": d1, "broadcastIds": []string{bid}}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(s.db.Broadcasts[0].Received) != 1 {
		t.Fatal("missing collector acknowledgement")
	}
	// Other collectors may not acknowledge someone else's broadcast.
	if w = testAPI(t, s, "POST", "/api/device/received", s.db.Token,
		map[string]interface{}{"id": "00000000000000000000000000000003", "broadcastIds": []string{bid}}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(s.db.Broadcasts[0].Received) != 1 {
		t.Fatal("unauthorized target marked as received")
	}
	if w = testAPI(t, s, "POST", "/api/device/seen", s.db.Token, map[string]string{"id": d1, "broadcastId": bid}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": d1, "name": "Coletor 1"})
	_ = json.Unmarshal(w.Body.Bytes(), &received)
	if len(received.Broadcasts) != 0 {
		t.Fatal("seen broadcast reappeared")
	}
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": d2, "name": "Coletor 2"})
	var second struct {
		Broadcasts []Broadcast `json:"broadcasts"`
	}
	_ = json.Unmarshal(w.Body.Bytes(), &second)
	if len(second.Broadcasts) != 1 {
		t.Fatal("second collector did not receive message")
	}
	d3 := "00000000000000000000000000000003"
	w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": d3, "name": "Late collector"})
	var late struct {
		Broadcasts []Broadcast `json:"broadcasts"`
	}
	_ = json.Unmarshal(w.Body.Bytes(), &late)
	if len(late.Broadcasts) != 0 {
		t.Fatal("broadcast sent to a collector that was offline when announced")
	}
	loaded, e := newServer(s.path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	if len(loaded.db.Broadcasts) != 1 || len(loaded.db.Broadcasts[0].Seen) != 1 || len(loaded.db.Broadcasts[0].Delivered) < 2 {
		t.Fatal("missing persisted delivery or seen")
	}
	w = testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]interface{}{"action": "close", "id": bid})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
}

func TestDiscoveryHostUsesCurrentLANInterface(t *testing.T) {
	ips := localIPs()
	if len(ips) == 0 {
		t.Skip("sem IP privado na rede de testes")
	}
	peer := net.ParseIP(ips[0])
	if peer == nil {
		t.Fatal("IP invalido")
	}
	if !sameLocalSubnet(peer) {
		t.Fatalf("host %s nao reconheceu sua propria rede", peer)
	}
	if got := localIPForPeer(peer); got != "" && net.ParseIP(got) == nil {
		t.Fatalf("endereco ruim %q", got)
	}
	if !strings.HasPrefix(notesBeacon, "SERIDOPLAST_NOTES_BEACON_") {
		t.Fatal("identificador anuncio invalido")
	}
	if strings.Contains(notesBeacon, "token") {
		t.Fatal("anuncio nao pode transmitir chave")
	}
}

func TestDeleteBroadcastAndNote(t *testing.T) {
	s, e := newServer(filepath.Join(t.TempDir(), "db.json"), 8765)
	if e != nil {
		t.Fatal(e)
	}
	if w := testAPI(t, s, "POST", "/api/admin/save", "", map[string]string{"order": "0012", "text": "Entregar no portao"}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	n := s.db.Notes[0].ID
	if w := testAPI(t, s, "POST", "/api/admin/delete", "", map[string]string{"id": n}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(s.db.Notes) != 0 {
		t.Fatal("observacao nao excluida")
	}
	s.Lock()
	s.devices["0123456789abcdef0123456789abcdef"] = Device{ID: "0123456789abcdef0123456789abcdef", Name: "TC60", LastSeen: time.Now().Format(time.RFC3339)}
	s.Unlock()
	if w := testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]string{"action": "send", "text": "Aviso para todos"}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(s.db.Broadcasts) != 1 {
		t.Fatal("envio falhou")
	}
	id := s.db.Broadcasts[0].ID
	if w := testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]string{"action": "delete", "id": id}); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(s.db.Broadcasts) != 0 {
		t.Fatal("aviso nao excluido")
	}
	reopened, e := newServer(s.path, 8765)
	if e != nil || len(reopened.db.Broadcasts) != 0 || len(reopened.db.Notes) != 0 {
		t.Fatal("exclusao nao foi persistida", e)
	}
}

func TestTargetedBroadcastOnlySelectedOnline(t *testing.T) {
	s, err := newServer(filepath.Join(t.TempDir(), "selective.json"), 8765)
	if err != nil {
		t.Fatal(err)
	}
	ids := []string{"00000000000000000000000000000001", "00000000000000000000000000000002"}
	for _, id := range ids {
		w := testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id, "name": "TC60"})
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	w := testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]interface{}{"action": "send", "text": "Somente para um", "targetMode": "selected", "targetIds": []string{ids[1]}})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(s.db.Broadcasts) != 1 || len(s.db.Broadcasts[0].Targets) != 1 || s.db.Broadcasts[0].Targets[0] != ids[1] {
		t.Fatal("selected target mismatch")
	}
	for i, id := range ids {
		w = testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id, "name": "TC60"})
		var resp struct {
			Broadcasts []Broadcast `json:"broadcasts"`
		}
		if e := json.Unmarshal(w.Body.Bytes(), &resp); e != nil {
			t.Fatal(e)
		}
		if len(resp.Broadcasts) != i {
			t.Fatalf("device %d received %d notices", i, len(resp.Broadcasts))
		}
	}
	w = testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]interface{}{"action": "send", "text": "Bad", "targetMode": "selected", "targetIds": []string{ids[0], "ffffffffffffffffffffffffffffffff"}})
	if w.Code != 409 {
		t.Fatalf("unknown device should be rejected, got %d", w.Code)
	}
	w = testAPI(t, s, "POST", "/api/admin/broadcast", "", map[string]interface{}{"action": "send", "text": "Bad", "targetMode": "selected"})
	if w.Code != 400 {
		t.Fatalf("empty selected audience must not become broadcast-all, got %d", w.Code)
	}
}
