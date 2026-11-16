package main

import (
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"path/filepath"
	"testing"
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
