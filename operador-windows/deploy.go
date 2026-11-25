package main

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const maxAPK int64 = 120 * 1024 * 1024

var tagPattern = regexp.MustCompile(`^v(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$`)

func versionNumber(v string) int {
	m := tagPattern.FindStringSubmatch("v" + strings.TrimPrefix(v, "v"))
	if m == nil {
		return -1
	}
	a, _ := strconv.Atoi(m[1])
	b, _ := strconv.Atoi(m[2])
	c, _ := strconv.Atoi(m[3])
	return a*10000 + b*100 + c
}

type APKPackage struct {
	ID      string `json:"id"`
	Tag     string `json:"tag"`
	Size    int64  `json:"size"`
	Created string `json:"created"`
}
type Deployment struct {
	ID         string `json:"id"`
	PackageID  string `json:"packageId"`
	Tag        string `json:"tag"`
	DeviceID   string `json:"deviceId"`
	DeviceName string `json:"deviceName"`
	Status     string `json:"status"`
	Detail     string `json:"detail"`
	Created    string `json:"created"`
}

func (s *Server) apkPath(id string) string {
	return filepath.Join(filepath.Dir(s.path), "apks", id+".apk")
}
func (s *Server) deploymentForLocked(d Device) *Deployment {
	for i := range s.db.Deployments {
		j := &s.db.Deployments[i]
		if j.DeviceID != d.ID || j.Status == "installed" || j.Status == "cancelled" || j.Status == "rejected" {
			continue
		}
		if versionNumber(d.BridgeVersion) >= versionNumber(j.Tag) {
			if strings.TrimPrefix(j.Tag, "v") == d.BridgeVersion {
				j.Status = "installed"
				j.Detail = "Versão confirmada pelo coletor"
			} else {
				j.Status = "cancelled"
				j.Detail = "Versão superior já instalada"
			}
			_ = s.save()
			continue
		}
		return j
	}
	return nil
}
func (s *Server) deployHandle(w http.ResponseWriter, r *http.Request) {
	p := r.URL.Path
	if strings.HasPrefix(p, "/api/admin/") {
		if !s.admin(w, r) {
			return
		}
	} else {
		if !s.authorized(w, r) {
			return
		}
	}
	switch p {
	case "/api/admin/apk/upload":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		tag := r.Header.Get("X-APK-Version")
		if !tagPattern.MatchString(tag) {
			fail(w, 400, "Informe a versão vX.Y.Z")
			return
		}
		if err := os.MkdirAll(filepath.Dir(s.apkPath("x")), 0700); err != nil {
			fail(w, 500, err.Error())
			return
		}
		f, e := os.CreateTemp(filepath.Dir(s.apkPath("x")), "upload-*")
		if e != nil {
			fail(w, 500, e.Error())
			return
		}
		defer os.Remove(f.Name())
		h := sha256.New()
		n, e := io.Copy(io.MultiWriter(f, h), http.MaxBytesReader(w, r.Body, maxAPK))
		ce := f.Close()
		if e != nil || ce != nil || n < 50*1024 {
			fail(w, 400, "APK inválido ou maior que 120 MB")
			return
		}
		z, e := zip.OpenReader(f.Name())
		if e != nil {
			fail(w, 400, "Arquivo não é APK válido")
			return
		}
		manifest := false
		for _, entry := range z.File {
			if entry.Name == "AndroidManifest.xml" {
				manifest = true
				break
			}
		}
		z.Close()
		if !manifest {
			fail(w, 400, "APK sem manifesto")
			return
		}
		id := hex.EncodeToString(h.Sum(nil))
		s.Lock()
		defer s.Unlock()
		for _, a := range s.db.Packages {
			if a.ID == id {
				if a.Tag != tag {
					fail(w, 409, "Arquivo já cadastrado com outra versão")
					return
				}
				reply(w, 200, a)
				return
			}
		}
		for _, a := range s.db.Packages {
			if a.Tag == tag {
				fail(w, 409, "Versão já importada com outro hash; use uma nova versão")
				return
			}
		}
		if e = os.Rename(f.Name(), s.apkPath(id)); e != nil {
			fail(w, 500, e.Error())
			return
		}
		a := APKPackage{id, tag, n, time.Now().Format(time.RFC3339)}
		s.db.Packages = append(s.db.Packages, a)
		if e = s.save(); e != nil {
			s.db.Packages = s.db.Packages[:len(s.db.Packages)-1]
			os.Remove(s.apkPath(id))
			fail(w, 500, e.Error())
			return
		}
		reply(w, 200, a)
	case "/api/admin/apk/deploy":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			PackageID string   `json:"packageId"`
			All       bool     `json:"all"`
			IDs       []string `json:"ids"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		s.Lock()
		defer s.Unlock()
		var a APKPackage
		for _, x := range s.db.Packages {
			if x.ID == in.PackageID {
				a = x
				break
			}
		}
		if a.ID == "" {
			fail(w, 404, "APK não encontrado")
			return
		}
		targets := map[string]bool{}
		for _, id := range in.IDs {
			targets[id] = true
		}
		count, skipped := 0, 0
		old := append([]Deployment(nil), s.db.Deployments...)
		for _, d := range s.devices {
			if !in.All && !targets[d.ID] {
				continue
			}
			if versionNumber(d.BridgeVersion) < 30625 || versionNumber(d.BridgeVersion) >= versionNumber(a.Tag) {
				skipped++
				continue
			}
			busy := false
			for _, j := range s.db.Deployments {
				if j.DeviceID == d.ID && j.Status != "installed" && j.Status != "cancelled" && j.Status != "rejected" {
					busy = true
					break
				}
			}
			if busy {
				skipped++
				continue
			}
			s.db.Deployments = append(s.db.Deployments, Deployment{uid(), a.ID, a.Tag, d.ID, d.Name, "queued", "Aguardando coletor", time.Now().Format(time.RFC3339)})
			count++
		}
		if count == 0 {
			fail(w, 409, "Nenhum coletor elegível: requer Bridge 3.6.25+, versão inferior e sem atualização pendente")
			return
		}
		if e := s.save(); e != nil {
			s.db.Deployments = old
			fail(w, 500, e.Error())
			return
		}
		reply(w, 200, map[string]int{"queued": count, "skipped": skipped})
	case "/api/device/apk":
		if r.Method != "GET" {
			fail(w, 405, "metodo")
			return
		}
		id := r.URL.Query().Get("id")
		job := r.URL.Query().Get("job")
		s.Lock()
		var pkg string
		for _, j := range s.db.Deployments {
			if j.ID == job && j.DeviceID == id && j.Status != "installed" && j.Status != "cancelled" && j.Status != "rejected" {
				pkg = j.PackageID
				break
			}
		}
		s.Unlock()
		if pkg == "" {
			fail(w, 403, "Atualização não destinada ao coletor")
			return
		}
		w.Header().Set("Content-Type", "application/vnd.android.package-archive")
		http.ServeFile(w, r, s.apkPath(pkg))
	case "/api/device/apk/status":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			ID     string `json:"id"`
			Job    string `json:"job"`
			Status string `json:"status"`
			Detail string `json:"detail"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if len(in.Detail) > 500 || !(in.Status == "waiting" || in.Status == "downloading" || in.Status == "installing" || in.Status == "retry" || in.Status == "rejected") {
			fail(w, 400, "status inválido")
			return
		}
		s.Lock()
		defer s.Unlock()
		for i := range s.db.Deployments {
			j := &s.db.Deployments[i]
			if j.ID == in.Job && j.DeviceID == in.ID && j.Status != "installed" && j.Status != "cancelled" && j.Status != "rejected" {
				if j.Status == in.Status && j.Detail == in.Detail {
					reply(w, 200, map[string]bool{"ok": true})
					return
				}
				prev := *j
				j.Status = in.Status
				j.Detail = in.Detail
				if e := s.save(); e != nil {
					*j = prev
					fail(w, 500, e.Error())
					return
				}
				reply(w, 200, map[string]bool{"ok": true})
				return
			}
		}
		fail(w, 404, "Atualização não encontrada")
	case "/api/admin/apk/cancel":
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
		for i := range s.db.Deployments {
			j := &s.db.Deployments[i]
			if j.ID == in.ID && (j.Status == "queued" || j.Status == "waiting" || j.Status == "retry") {
				prev := *j
				j.Status = "cancelled"
				j.Detail = "Cancelado pelo administrador"
				if e := s.save(); e != nil {
					*j = prev
					fail(w, 500, e.Error())
					return
				}
				reply(w, 200, map[string]bool{"ok": true})
				return
			}
		}
		fail(w, 409, "Não é possível cancelar nesta etapa")
	default:
		fail(w, 404, "rota inválida")
	}
}
