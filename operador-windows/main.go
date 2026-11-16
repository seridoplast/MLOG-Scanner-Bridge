package main

import (
	"crypto/rand"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed web/*
var webFiles embed.FS

type Note struct {
	ID          string `json:"id"`
	Order       string `json:"order"`
	Text        string `json:"text"`
	Status      string `json:"status"`
	CreatedAt   string `json:"createdAt"`
	UpdatedAt   string `json:"updatedAt"`
	CompletedAt string `json:"completedAt,omitempty"`
	CompletedBy string `json:"completedBy,omitempty"`
}
type DB struct {
	Notes []Note `json:"notes"`
	Token string `json:"token"`
}
type Server struct {
	sync.Mutex
	db   DB
	path string
	port int
}

func uid() string {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return hex.EncodeToString(b)
}
func newServer(path string, port int) (*Server, error) {
	s := &Server{path: path, port: port}
	if _, err := os.Stat(path); errors.Is(err, os.ErrNotExist) {
		if _, backErr := os.Stat(path + ".bak"); backErr == nil {
			_ = os.Rename(path+".bak", path)
		}
	}
	if b, e := os.ReadFile(path); e == nil {
		if e = json.Unmarshal(b, &s.db); e != nil {
			return nil, fmt.Errorf("banco invalido: %w", e)
		}
	} else if !errors.Is(e, os.ErrNotExist) {
		return nil, e
	}
	if s.db.Notes == nil {
		s.db.Notes = []Note{}
	}
	if s.db.Token == "" {
		s.db.Token = uid() + uid()
		if err := s.save(); err != nil {
			return nil, err
		}
	}
	return s, nil
}
func (s *Server) save() error {
	if e := os.MkdirAll(filepath.Dir(s.path), 0700); e != nil {
		return e
	}
	data, e := json.MarshalIndent(s.db, "", "  ")
	if e != nil {
		return e
	}
	tmp := s.path + ".tmp"
	if e = os.WriteFile(tmp, data, 0600); e != nil {
		return e
	}
	// Windows refuses to replace an existing file via os.Rename.
	if runtime.GOOS == "windows" {
		backup := s.path + ".bak"
		_ = os.Remove(backup)
		if _, err := os.Stat(s.path); err == nil {
			if err := os.Rename(s.path, backup); err != nil {
				return err
			}
		}
		if err := os.Rename(tmp, s.path); err != nil {
			_ = os.Rename(backup, s.path)
			return err
		}
		_ = os.Remove(backup)
		return nil
	}
	return os.Rename(tmp, s.path)
}
func reply(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}
func fail(w http.ResponseWriter, code int, msg string) {
	reply(w, code, map[string]string{"error": msg})
}
func (s *Server) local(r *http.Request) bool {
	host, _, e := net.SplitHostPort(r.RemoteAddr)
	return e == nil && net.ParseIP(host) != nil && net.ParseIP(host).IsLoopback()
}
func (s *Server) admin(w http.ResponseWriter, r *http.Request) bool {
	if !s.local(r) {
		fail(w, 403, "painel disponivel somente neste computador")
		return false
	}
	origin := r.Header.Get("Origin")
	if origin != "" && origin != fmt.Sprintf("http://127.0.0.1:%d", s.port) && origin != fmt.Sprintf("http://localhost:%d", s.port) {
		fail(w, 403, "origem recusada")
		return false
	}
	return true
}
func (s *Server) authorized(w http.ResponseWriter, r *http.Request) bool {
	token := r.Header.Get("X-Bridge-Token")
	if len(token) != len(s.db.Token) || subtle.ConstantTimeCompare([]byte(token), []byte(s.db.Token)) != 1 {
		fail(w, 401, "chave do coletor invalida")
		return false
	}
	return true
}
func readJSON(w http.ResponseWriter, r *http.Request, v interface{}) bool {
	if r.Header.Get("Content-Type") != "application/json" {
		fail(w, 415, "use application/json")
		return false
	}
	dec := json.NewDecoder(io.LimitReader(r.Body, 8192))
	dec.DisallowUnknownFields()
	if err := dec.Decode(v); err != nil {
		fail(w, 400, "JSON invalido")
		return false
	}
	var more interface{}
	if dec.Decode(&more) != io.EOF {
		fail(w, 400, "JSON extra")
		return false
	}
	return true
}
func normalizeOrder(s string) string { return strings.TrimSpace(s) }
func validOrder(s string) bool {
	if s == "" || len(s) > 72 {
		return false
	}
	for _, r := range s {
		if !(r >= '0' && r <= '9' || r >= 'A' && r <= 'Z' || r >= 'a' && r <= 'z' || r == '-' || r == '/' || r == '_') {
			return false
		}
	}
	return true
}
func validText(s string) bool { return strings.TrimSpace(s) != "" && len([]rune(s)) <= 1500 }
func (s *Server) handle(w http.ResponseWriter, r *http.Request) {
	switch r.URL.Path {
	case "/":
		if !s.admin(w, r) {
			return
		}
		f, e := webFiles.ReadFile("web/index.html")
		if e != nil {
			http.Error(w, e.Error(), 500)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		_, _ = w.Write(f)
	case "/api/admin/state":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "GET" {
			fail(w, 405, "metodo")
			return
		}
		s.Lock()
		defer s.Unlock()
		copied := append([]Note{}, s.db.Notes...)
		sort.Slice(copied, func(i, j int) bool { return copied[i].CreatedAt > copied[j].CreatedAt })
		reply(w, 200, map[string]interface{}{"notes": copied, "token": s.db.Token, "addresses": localIPs(), "port": s.port})
	case "/api/admin/save":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var input struct {
			ID    string `json:"id"`
			Order string `json:"order"`
			Text  string `json:"text"`
		}
		if !readJSON(w, r, &input) {
			return
		}
		input.Order = normalizeOrder(input.Order)
		input.Text = strings.TrimSpace(input.Text)
		if !validOrder(input.Order) || !validText(input.Text) {
			fail(w, 400, "numero do pedido invalido ou observacao vazia/grande (max. 1500 caracteres)")
			return
		}
		s.Lock()
		defer s.Unlock()
		now := time.Now().Format(time.RFC3339)
		if input.ID != "" {
			found := false
			for i := range s.db.Notes {
				if s.db.Notes[i].ID == input.ID && s.db.Notes[i].Status == "pending" {
					s.db.Notes[i].Order = input.Order
					s.db.Notes[i].Text = input.Text
					s.db.Notes[i].UpdatedAt = now
					found = true
					break
				}
			}
			if !found {
				fail(w, 404, "observacao nao encontrada ou ja concluida")
				return
			}
		}
		if input.ID == "" {
			s.db.Notes = append(s.db.Notes, Note{ID: uid(), Order: input.Order, Text: input.Text, Status: "pending", CreatedAt: now, UpdatedAt: now})
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/admin/status":
		if !s.admin(w, r) {
			return
		}
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		var in struct {
			ID     string `json:"id"`
			Status string `json:"status"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		if in.Status != "pending" && in.Status != "done" {
			fail(w, 400, "status invalido")
			return
		}
		s.Lock()
		defer s.Unlock()
		found := false
		for i := range s.db.Notes {
			n := &s.db.Notes[i]
			if n.ID != in.ID {
				continue
			}
			found = true
			n.Status = in.Status
			n.UpdatedAt = time.Now().Format(time.RFC3339)
			if in.Status == "done" {
				n.CompletedAt = n.UpdatedAt
				n.CompletedBy = "painel"
			} else {
				n.CompletedAt = ""
				n.CompletedBy = ""
			}
			break
		}
		if !found {
			fail(w, 404, "observacao nao encontrada")
			return
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/admin/delete":
		if !s.admin(w, r) {
			return
		}
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
		found := false
		for i, n := range s.db.Notes {
			if n.ID == in.ID {
				found = true
				s.db.Notes = append(s.db.Notes[:i], s.db.Notes[i+1:]...)
				break
			}
		}
		if !found {
			fail(w, 404, "observacao nao encontrada")
			return
		}
		if err := s.save(); err != nil {
			fail(w, 500, err.Error())
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/ping":
		if r.Method != "GET" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		reply(w, 200, map[string]bool{"ok": true})
	case "/api/notes":
		if r.Method != "GET" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		order := normalizeOrder(r.URL.Query().Get("order"))
		if !validOrder(order) {
			fail(w, 400, "pedido invalido")
			return
		}
		s.Lock()
		defer s.Unlock()
		list := []Note{}
		for _, n := range s.db.Notes {
			if n.Order == order && n.Status == "pending" {
				list = append(list, n)
			}
		}
		sort.Slice(list, func(i, j int) bool { return list[i].CreatedAt < list[j].CreatedAt })
		reply(w, 200, map[string]interface{}{"notes": list, "order": order})
	case "/api/complete":
		if r.Method != "POST" {
			fail(w, 405, "metodo")
			return
		}
		if !s.authorized(w, r) {
			return
		}
		var in struct {
			Order  string   `json:"order"`
			IDs    []string `json:"ids"`
			Device string   `json:"device"`
		}
		if !readJSON(w, r, &in) {
			return
		}
		in.Order = normalizeOrder(in.Order)
		if !validOrder(in.Order) || len(in.IDs) == 0 || len(in.IDs) > 100 || len(in.Device) > 80 {
			fail(w, 400, "dados invalidos")
			return
		}
		s.Lock()
		defer s.Unlock()
		now := time.Now().Format(time.RFC3339)
		changed := 0
		for i := range s.db.Notes {
			n := &s.db.Notes[i]
			if n.Order != in.Order || n.Status != "pending" {
				continue
			}
			for _, id := range in.IDs {
				if n.ID == id {
					n.Status = "done"
					n.CompletedAt = now
					n.CompletedBy = in.Device
					n.UpdatedAt = now
					changed++
					break
				}
			}
		}
		if changed > 0 {
			if e := s.save(); e != nil {
				fail(w, 500, e.Error())
				return
			}
		}
		reply(w, 200, map[string]interface{}{"ok": true, "completed": changed})
	default:
		fail(w, 404, "nao encontrado")
	}
}
func localIPs() []string {
	out := []string{}
	addrs, e := net.InterfaceAddrs()
	if e != nil {
		return out
	}
	for _, a := range addrs {
		ipnet, ok := a.(*net.IPNet)
		if !ok {
			continue
		}
		ip := ipnet.IP.To4()
		if ip == nil || ip.IsLoopback() {
			continue
		}
		if (ip[0] == 10) || (ip[0] == 192 && ip[1] == 168) || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31) {
			out = append(out, ip.String())
		}
	}
	sort.Strings(out)
	return out
}
func openBrowser(url string) {
	if runtime.GOOS == "windows" {
		_ = exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	} else {
		_ = exec.Command("xdg-open", url).Start()
	}
}
func main() {
	port := flag.Int("port", 8765, "porta do servidor local")
	flag.Parse()
	if *port < 1024 || *port > 65535 {
		log.Fatal("porta invalida")
	}
	home, e := os.UserConfigDir()
	if e != nil {
		log.Fatal(e)
	}
	path := filepath.Join(home, "Seridoplast", "ObservacoesPedidos", "dados.json")
	server, e := newServer(path, *port)
	if e != nil {
		log.Fatal(e)
	}
	listen, e := net.Listen("tcp", fmt.Sprintf("0.0.0.0:%d", *port))
	if e != nil {
		log.Fatal("Nao foi possivel abrir porta ", *port, ": ", e)
	}
	fmt.Print("\nSERIDOPLAST | OBSERVACOES DE PEDIDOS\n\n")
	fmt.Println("Painel no PC: http://127.0.0.1:" + strconv.Itoa(*port))
	for _, ip := range localIPs() {
		fmt.Printf("Coletores: http://%s:%d\n", ip, *port)
	}
	fmt.Println("Mantenha este programa aberto. Dados salvos em:", path)
	fmt.Println("No firewall do Windows, permitir somente na rede PRIVADA. Nao exponha a porta na internet.")
	go func() { time.Sleep(600 * time.Millisecond); openBrowser(fmt.Sprintf("http://127.0.0.1:%d", *port)) }()
	log.Fatal(http.Serve(listen, http.HandlerFunc(server.handle)))
}
