package main

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

func TestLocalAPKDistribution(t *testing.T) {
	path := filepath.Join(t.TempDir(), "dados.json")
	s, e := newServer(path, 8765)
	if e != nil {
		t.Fatal(e)
	}
	var buf bytes.Buffer
	z := zip.NewWriter(&buf)
	f, _ := z.Create("AndroidManifest.xml")
	f.Write([]byte("test fixture, NOT an installable APK"))
	h := &zip.FileHeader{Name: "payload", Method: zip.Store}
	f, _ = z.CreateHeader(h)
	f.Write(make([]byte, 60000))
	z.Close()
	upload := func(remote, tag string) *httptest.ResponseRecorder {
		r := httptest.NewRequest("POST", "/api/admin/apk/upload", bytes.NewReader(buf.Bytes()))
		r.RemoteAddr = remote
		r.Header.Set("X-APK-Version", tag)
		w := httptest.NewRecorder()
		s.handle(w, r)
		return w
	}
	if w := upload("192.168.1.10:1234", "v3.6.26"); w.Code != 403 {
		t.Fatal("remote upload accepted")
	}
	w := upload("127.0.0.1:1234", "v3.6.26")
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var pkg APKPackage
	json.Unmarshal(w.Body.Bytes(), &pkg)
	if w := upload("127.0.0.1:1234", "v3.6.26"); w.Code != 200 || len(s.db.Packages) != 1 {
		t.Fatal("duplicate cache")
	}
	a, b, c := "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccccccccccccccccccccc"
	heartbeat := func(id, version string) *httptest.ResponseRecorder {
		return testAPI(t, s, "POST", "/api/device/heartbeat", s.db.Token, map[string]interface{}{"id": id, "name": id[:4], "bridgeVersion": version})
	}
	for _, id := range []string{a, b} {
		if w := heartbeat(id, "3.6.25"); w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	heartbeat(c, "3.6.24")
	w = testAPI(t, s, "POST", "/api/admin/apk/deploy", "", map[string]interface{}{"packageId": pkg.ID, "all": false, "ids": []string{a}})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var response struct {
		Deployment *Deployment `json:"deployment"`
	}
	json.Unmarshal(heartbeat(a, "3.6.25").Body.Bytes(), &response)
	if response.Deployment == nil || response.Deployment.DeviceID != a {
		t.Fatal("no assigned job")
	}
	job := response.Deployment.ID
	json.Unmarshal(heartbeat(b, "3.6.25").Body.Bytes(), &response)
	if response.Deployment != nil {
		t.Fatal("wrong target")
	}
	url := "/api/device/apk?id=" + a + "&job=" + job
	if w := testAPI(t, s, "GET", url, "", nil); w.Code != 401 {
		t.Fatal("missing auth")
	}
	if w := testAPI(t, s, "GET", "/api/device/apk?id="+b+"&job="+job, s.db.Token, nil); w.Code != 403 {
		t.Fatal("foreign job")
	}
	if w := testAPI(t, s, "GET", url, s.db.Token, nil); w.Code != 200 || !bytes.Equal(w.Body.Bytes(), buf.Bytes()) {
		t.Fatal("cache bytes mismatch", w.Code)
	}
	w = testAPI(t, s, "POST", "/api/device/apk/status", s.db.Token, map[string]string{"id": a, "job": job, "status": "installing", "detail": "test"})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if s.db.Deployments[0].Status != "installing" {
		t.Fatal("premature confirmation")
	}
	heartbeat(a, "3.6.26")
	if s.db.Deployments[0].Status != "installed" {
		t.Fatal("not confirmed")
	}
	w = testAPI(t, s, "POST", "/api/admin/apk/deploy", "", map[string]interface{}{"packageId": pkg.ID, "all": true, "ids": []string{}})
	if w.Code != 200 || len(s.db.Deployments) != 2 || s.db.Deployments[1].DeviceID != b {
		t.Fatal("all eligible", w.Body.String())
	}
	s2, e := newServer(path, 8765)
	if e != nil || len(s2.db.Deployments) != 2 {
		t.Fatal("persistence", e)
	}
	job2 := s2.db.Deployments[1].ID
	w = testAPI(t, s2, "POST", "/api/admin/apk/cancel", "", map[string]string{"id": job2})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := testAPI(t, s2, "GET", "/api/device/apk?id="+b+"&job="+job2, s2.db.Token, nil); w.Code != 403 {
		t.Fatal("cancelled download")
	}
}
