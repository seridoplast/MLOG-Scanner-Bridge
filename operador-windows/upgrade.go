package main

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"errors"
	"net"
	"net/http"
	"os"
	"strings"
	"time"
)

func markActivity(d *Device, at time.Time) {
	prev, err := time.Parse(time.RFC3339, d.LastActivityAt)
	if err != nil || at.Truncate(time.Second).After(prev) {
		d.LastActivityAt = at.Format(time.RFC3339)
	}
}
func originalName(d Device) string {
	model := strings.TrimSpace(d.Model)
	if model == "" {
		model = "Coletor"
	}
	runes := []rune(model)
	if len(runes) > 55 {
		model = string(runes[:55])
	}
	id := d.ID
	if len(id) > 4 {
		id = id[len(id)-4:]
	}
	return model + " · " + id
}
func (s *Server) resetNames(w http.ResponseWriter, r *http.Request) {
	if !s.admin(w, r) {
		return
	}
	if r.Method != "POST" {
		fail(w, 405, "metodo")
		return
	}
	var in struct {
		ID  string `json:"id"`
		All bool   `json:"all"`
	}
	if !readJSON(w, r, &in) {
		return
	}
	if (in.All && in.ID != "") || (!in.All && !validDeviceID(in.ID)) {
		fail(w, 400, "selecione um coletor ou todos")
		return
	}
	s.Lock()
	defer s.Unlock()
	if !in.All {
		if _, ok := s.devices[in.ID]; !ok {
			fail(w, 404, "coletor não cadastrado")
			return
		}
	}
	count := 0
	old := map[string]Device{}
	persisted := map[string]Device{}
	for id, d := range s.devices {
		if !in.All && id != in.ID {
			continue
		}
		old[id] = d
		persisted[id] = s.db.Devices[id]
		d.Name = originalName(d)
		d.NamePending = true
		d.ResetNamePending = true
		s.devices[id] = d
		s.db.Devices[id] = d
		count++
	}
	if err := s.save(); err != nil {
		for id, d := range old {
			s.devices[id] = d
			s.db.Devices[id] = persisted[id]
		}
		fail(w, 500, err.Error())
		return
	}
	reply(w, 200, map[string]int{"reset": count})
}

// Separate authenticated TLS listener. Collector endpoints are never available here.
type webAdminKey struct{}

func (s *Server) webAdminHandler(user, password string) http.Handler {
	uh := sha256.Sum256([]byte(user))
	ph := sha256.Sum256([]byte(password))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Referrer-Policy", "no-referrer")
		if r.TLS == nil {
			http.Error(w, "HTTPS obrigatório", 403)
			return
		}
		u, p, ok := r.BasicAuth()
		a := sha256.Sum256([]byte(u))
		b := sha256.Sum256([]byte(p))
		if !ok || subtle.ConstantTimeCompare(a[:], uh[:])&subtle.ConstantTimeCompare(b[:], ph[:]) != 1 {
			w.Header().Set("WWW-Authenticate", `Basic realm="Central Seridoplast", charset="UTF-8"`)
			http.Error(w, "Autenticação necessária", 401)
			return
		}
		if r.URL.Path != "/" && !strings.HasPrefix(r.URL.Path, "/assets/") && !strings.HasPrefix(r.URL.Path, "/api/admin/") {
			http.NotFound(w, r)
			return
		}
		if r.Method != "GET" && r.Method != "HEAD" && r.Header.Get("Origin") != "https://"+r.Host {
			http.Error(w, "Origem recusada", 403)
			return
		}
		if o := r.Header.Get("Origin"); o != "" && o != "https://"+r.Host {
			http.Error(w, "Origem recusada", 403)
			return
		}
		s.handle(w, r.WithContext(context.WithValue(r.Context(), webAdminKey{}, true)))
	})
}
func (s *Server) startWebAdmin(addr, cert, key string) error {
	user := os.Getenv("SERIDOPLAST_WEB_USER")
	password := os.Getenv("SERIDOPLAST_WEB_PASSWORD")
	if user == "" || len(password) < 16 || cert == "" || key == "" {
		return errors.New("modo web remoto requer SERIDOPLAST_WEB_USER, senha de pelo menos 16 caracteres, certificado e chave TLS")
	}
	pair, err := tls.LoadX509KeyPair(cert, key)
	if err != nil {
		return err
	}
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		return err
	}
	srv := &http.Server{Handler: s.webAdminHandler(user, password), ReadHeaderTimeout: 10 * time.Second, IdleTimeout: 90 * time.Second, TLSConfig: &tls.Config{MinVersion: tls.VersionTLS12, Certificates: []tls.Certificate{pair}}}
	go func() {
		if err := srv.ServeTLS(ln, "", ""); err != nil && err != http.ErrServerClosed {
			panic(err)
		}
	}()
	return nil
}

// A conexão é distinta de atividade; falta de telemetria nunca vira ociosidade.
func activityState(d Device, now time.Time) string {
	if !d.Online {
		return "offline"
	}
	if d.RecentActivity {
		return "active"
	}
	at, err := time.Parse(time.RFC3339, d.LastActivityAt)
	if err == nil && !at.After(now) && now.Sub(at) < 2*time.Minute {
		return "active"
	}
	if !d.Accessibility || err != nil || at.After(now) {
		return "unknown"
	}
	if now.Sub(at) >= 20*time.Minute {
		return "idle"
	}
	return "online"
}
