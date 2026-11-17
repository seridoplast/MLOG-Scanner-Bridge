//go:build windows

package main

// Painel Windows nativo (Win32), sem navegador ou runtime externo.
// O mesmo servidor HTTP permanece embutido para comunicar-se com os coletores.
import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"runtime"
	"sort"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

var (
	user32              = syscall.NewLazyDLL("user32.dll")
	gdi32               = syscall.NewLazyDLL("gdi32.dll")
	kernel32            = syscall.NewLazyDLL("kernel32.dll")
	createWindow        = user32.NewProc("CreateWindowExW")
	sendMessage         = user32.NewProc("SendMessageW")
	setWindowText       = user32.NewProc("SetWindowTextW")
	getWindowText       = user32.NewProc("GetWindowTextW")
	getWindowTextLength = user32.NewProc("GetWindowTextLengthW")
	moveWindow          = user32.NewProc("MoveWindow")
	msgBox              = user32.NewProc("MessageBoxW")
	defWindowProc       = user32.NewProc("DefWindowProcW")
)

const (
	wsOverlappedWindow = 0x00CF0000
	wsChild            = 0x40000000
	wsVisible          = 0x10000000
	wsTabStop          = 0x00010000
	wsVScroll          = 0x00200000
	wsExClientEdge     = 0x00000200
	esMultiline        = 0x0004
	esAutoVScroll      = 0x0040
	esWantReturn       = 0x1000
	esAutoHScroll      = 0x0080
	lbsNotify          = 0x0001
	wmCreate           = 0x0001
	wmDestroy          = 0x0002
	wmSize             = 0x0005
	wmCommand          = 0x0111
	wmTimer            = 0x0113
	wmClose            = 0x0010
	wmSetFont          = 0x0030
	wmCtlColorStatic   = 0x0138
	wmCtlColorEdit     = 0x0133
	wmCtlColorListbox  = 0x0134
	lbResetContent     = 0x0184
	lbAddString        = 0x0180
	lbGetCurSel        = 0x0188
	lbSetCurSel        = 0x0186
	lbErr              = 0xffffffff
	lbnDblClk          = 2
	mbYesNo            = 0x00000004
	mbIconWarning      = 0x00000030
	mbIconInfo         = 0x00000040
	mbOK               = 0
	idYes              = 6
	idOrder            = 101
	idNoteText         = 102
	idNotesList        = 103
	idNoticeText       = 104
	idNoticeList       = 105
	idDevicesList      = 106
	idSave             = 201
	idNew              = 202
	idDeleteNote       = 203
	idToggle           = 204
	idReload           = 205
	idSendNotice       = 206
	idDeleteNotice     = 207
	idCloseNotice      = 208
	idNoticeDetails    = 209
)

type winClass struct {
	Size        uint32
	Style       uint32
	WndProc     uintptr
	ClassExtra  int32
	WindowExtra int32
	Instance    uintptr
	Icon        uintptr
	Cursor      uintptr
	Background  uintptr
	MenuName    *uint16
	ClassName   *uint16
	IconSm      uintptr
}
type winMessage struct {
	Window  uintptr
	Message uint32
	WParam  uintptr
	LParam  uintptr
	Time    uint32
	PointX  int32
	PointY  int32
	Private uint32
}
type winRect struct{ Left, Top, Right, Bottom int32 }
type nativePanel struct {
	server                       *Server
	port                         int
	window                       uintptr
	controls                     map[int]uintptr
	font, largeFont, headingFont uintptr
	brush                        uintptr
	notes                        []Note
	notices                      []Broadcast
	devices                      []Device
	editID                       string
	message                      string
}

var native *nativePanel

func wide(s string) *uint16 { p, _ := syscall.UTF16PtrFromString(s); return p }
func nativeCall(p *syscall.LazyProc, params ...uintptr) uintptr {
	x, _, _ := p.Call(params...)
	return x
}
func winMessageBox(title, message string, flags uint32) int {
	return int(nativeCall(msgBox, nativeWindow(), uintptr(unsafe.Pointer(wide(message))), uintptr(unsafe.Pointer(wide(title))), uintptr(flags)))
}
func nativeWindow() uintptr {
	if native == nil {
		return 0
	}
	return native.window
}
func createFont(size, weight int) uintptr {
	// CreateFontW uses a negative height to specify the character height.
	return nativeCall(gdi32.NewProc("CreateFontW"), uintptr(int32(-size)), 0, 0, 0, uintptr(weight), 0, 0, 0, 1, 0, 0, 0, 0, uintptr(unsafe.Pointer(wide("Segoe UI"))))
}
func (p *nativePanel) control(id int, class, text string, style uint32, ex uint32) uintptr {
	h := nativeCall(createWindow, uintptr(ex), uintptr(unsafe.Pointer(wide(class))), uintptr(unsafe.Pointer(wide(text))), uintptr(style|wsChild|wsVisible), 0, 0, 100, 25, p.window, uintptr(id), 0, 0)
	p.controls[id] = h
	font := p.font
	if id == 301 {
		font = p.largeFont
	}
	if id == 302 || id == 303 || id == 304 {
		font = p.headingFont
	}
	nativeCall(sendMessage, h, wmSetFont, font, 1)
	return h
}
func (p *nativePanel) put(id int, s string) {
	nativeCall(setWindowText, p.controls[id], uintptr(unsafe.Pointer(wide(s))))
}
func (p *nativePanel) get(id int) string {
	h := p.controls[id]
	if h == 0 {
		return ""
	}
	n := int(nativeCall(getWindowTextLength, h))
	if n < 0 || n > 20000 {
		return ""
	}
	buf := make([]uint16, n+2)
	nativeCall(getWindowText, h, uintptr(unsafe.Pointer(&buf[0])), uintptr(len(buf)))
	return syscall.UTF16ToString(buf)
}
func (p *nativePanel) move(id, x, y, w, h int) {
	if c := p.controls[id]; c != 0 {
		nativeCall(moveWindow, c, uintptr(x), uintptr(y), uintptr(max(12, w)), uintptr(max(22, h)), 1)
	}
}
func (p *nativePanel) initialize() {
	p.controls = make(map[int]uintptr)
	p.font = createFont(16, 400)
	p.largeFont = createFont(27, 700)
	p.headingFont = createFont(19, 650)
	p.brush = nativeCall(gdi32.NewProc("CreateSolidBrush"), 0x00faf7f5)
	p.control(301, "STATIC", "SERIDÓPLAST  |  OBSERVAÇÕES", 0, 0)
	p.control(306, "STATIC", "Painel instalado no computador • avisos e pedidos em tempo real", 0, 0)
	p.control(305, "STATIC", "Preparando conexão com os coletores...", 0, 0)
	p.control(302, "STATIC", "Observações de pedidos", 0, 0)
	p.control(303, "STATIC", "Aviso geral", 0, 0)
	p.control(304, "STATIC", "Coletores conectados", 0, 0)
	p.control(307, "STATIC", "Número do pedido", 0, 0)
	p.control(308, "STATIC", "Observação", 0, 0)
	p.control(309, "STATIC", "Últimas observações (duplo clique para editar)", 0, 0)
	p.control(310, "STATIC", "Comunicados e confirmações", 0, 0)
	p.control(idOrder, "EDIT", "", wsTabStop|esAutoHScroll, wsExClientEdge)
	p.control(idNoteText, "EDIT", "", wsTabStop|esMultiline|esWantReturn|esAutoVScroll|wsVScroll, wsExClientEdge)
	p.control(idNoticeText, "EDIT", "", wsTabStop|esMultiline|esWantReturn|esAutoVScroll|wsVScroll, wsExClientEdge)
	p.control(idNotesList, "LISTBOX", "", wsTabStop|lbsNotify|wsVScroll, wsExClientEdge)
	p.control(idNoticeList, "LISTBOX", "", wsTabStop|lbsNotify|wsVScroll, wsExClientEdge)
	p.control(idDevicesList, "LISTBOX", "", wsTabStop|lbsNotify|wsVScroll, wsExClientEdge)
	p.control(idSave, "BUTTON", "Salvar observação", wsTabStop, 0)
	p.control(idNew, "BUTTON", "Nova", wsTabStop, 0)
	p.control(idDeleteNote, "BUTTON", "Excluir", wsTabStop, 0)
	p.control(idToggle, "BUTTON", "Concluir / Reabrir", wsTabStop, 0)
	p.control(idSendNotice, "BUTTON", "Enviar para conectados", wsTabStop, 0)
	p.control(idNoticeDetails, "BUTTON", "Situação", wsTabStop, 0)
	p.control(idDeleteNotice, "BUTTON", "Excluir aviso", wsTabStop, 0)
	p.control(idCloseNotice, "BUTTON", "Encerrar aviso", wsTabStop, 0)
	p.control(idReload, "BUTTON", "Atualizar painel", wsTabStop, 0)
	p.layout(1190, 760)
	nativeCall(user32.NewProc("SetTimer"), p.window, 1, 10000, 0)
	p.reload()
}
func (p *nativePanel) layout(w, h int) {
	if len(p.controls) == 0 {
		return
	}
	if w < 930 {
		w = 930
	}
	if h < 670 {
		h = 670
	}
	const left = 22
	contentWidth := w - 44
	colWidth := (contentWidth - 24) / 2
	right := left + colWidth + 24
	p.move(301, 22, 17, 700, 44)
	p.move(306, 24, 61, 850, 24)
	p.move(idReload, w-186, 24, 160, 35)
	p.move(305, 24, 91, w-48, 25)
	p.move(302, left, 131, colWidth, 27)
	p.move(303, right, 131, colWidth, 27)
	p.move(307, left, 166, 200, 23)
	p.move(idOrder, left, 191, colWidth, 31)
	p.move(308, left, 233, 200, 23)
	p.move(idNoteText, left, 259, colWidth, 90)
	p.move(idSave, left, 360, 155, 35)
	p.move(idNew, left+163, 360, 64, 35)
	p.move(idDeleteNote, left+234, 360, 87, 35)
	p.move(idToggle, left+330, 360, max(120, colWidth-330), 35)
	p.move(309, left, 411, colWidth, 23)
	p.move(idNotesList, left, 441, colWidth, h-469)
	p.move(idNoticeText, right, 166, colWidth, 133)
	p.move(idSendNotice, right, 307, 178, 35)
	p.move(idNoticeDetails, right+184, 307, 95, 35)
	p.move(idDeleteNotice, right+285, 307, 112, 35)
	p.move(idCloseNotice, right+403, 307, max(95, colWidth-403), 35)
	p.move(310, right, 356, colWidth, 23)
	p.move(idNoticeList, right, 385, colWidth, 128)
	p.move(304, right, 531, colWidth, 24)
	p.move(idDevicesList, right, 562, colWidth, h-590)
}
func (p *nativePanel) errorDialog(title string, err error) {
	if err != nil {
		winMessageBox(title, err.Error(), mbOK|mbIconWarning)
	}
}
func (p *nativePanel) request(path string, value interface{}, dest interface{}) error {
	method := "GET"
	var body io.Reader
	if value != nil {
		method = "POST"
		raw, _ := json.Marshal(value)
		body = bytes.NewReader(raw)
	}
	req, err := http.NewRequest(method, fmt.Sprintf("http://127.0.0.1:%d%s", p.port, path), body)
	if err != nil {
		return err
	}
	if value != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("painel local indisponível: %w", err)
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(io.LimitReader(resp.Body, 300000))
	if resp.StatusCode >= 300 {
		var e struct {
			Error string `json:"error"`
		}
		_ = json.Unmarshal(raw, &e)
		if e.Error == "" {
			e.Error = string(raw)
		}
		return fmt.Errorf("%s", e.Error)
	}
	if dest != nil {
		return json.Unmarshal(raw, dest)
	}
	return nil
}
func short(s string, n int) string {
	r := []rune(strings.ReplaceAll(strings.ReplaceAll(s, "\n", " "), "\r", " "))
	if len(r) > n {
		return string(r[:n]) + "…"
	}
	return string(r)
}
func (p *nativePanel) fillList(id int, items []string) {
	h := p.controls[id]
	nativeCall(sendMessage, h, lbResetContent, 0, 0)
	for _, s := range items {
		nativeCall(sendMessage, h, lbAddString, 0, uintptr(unsafe.Pointer(wide(s))))
	}
}
func (p *nativePanel) listIndex(id int) int {
	n := nativeCall(sendMessage, p.controls[id], lbGetCurSel, 0, 0)
	if n == lbErr {
		return -1
	}
	return int(n)
}
func (p *nativePanel) reload() {
	selectedNote := ""
	if i := p.listIndex(idNotesList); i >= 0 && i < len(p.notes) {
		selectedNote = p.notes[i].ID
	}
	selectedNotice := ""
	if i := p.listIndex(idNoticeList); i >= 0 && i < len(p.notices) {
		selectedNotice = p.notices[i].ID
	}
	var response struct {
		Notes          []Note      `json:"notes"`
		Broadcasts     []Broadcast `json:"broadcasts"`
		Devices        []Device    `json:"devices"`
		DiscoveryCount int         `json:"discoveryCount"`
	}
	if err := p.request("/api/admin/state", nil, &response); err != nil {
		p.put(305, "Sem conexão local — confira se outra instância está aberta.")
		return
	}
	p.notes = response.Notes
	p.notices = response.Broadcasts
	p.devices = response.Devices
	notes := make([]string, 0, len(p.notes))
	noteIdx := -1
	for i, n := range p.notes {
		if n.ID == selectedNote {
			noteIdx = i
		}
		state := "PENDENTE"
		if n.Status == "done" {
			state = "FEITO"
		}
		notes = append(notes, "Pedido "+n.Order+" | "+state+" | "+short(n.Text, 57))
	}
	p.fillList(idNotesList, notes)
	if noteIdx >= 0 {
		nativeCall(sendMessage, p.controls[idNotesList], lbSetCurSel, uintptr(noteIdx), 0)
	}
	broadcasts := make([]string, 0, len(p.notices))
	noticeIdx := -1
	for i, b := range p.notices {
		if b.ID == selectedNotice {
			noticeIdx = i
		}
		read := 0
		seen := 0
		for _, id := range b.Targets {
			if b.Received[id] != "" || b.Seen[id] != "" {
				read++
			}
			if b.Seen[id] != "" {
				seen++
			}
		}
		state := "ATIVO"
		if b.Status != "active" {
			state = "ENCERRADO"
		}
		broadcasts = append(broadcasts, fmt.Sprintf("%s | %d/%d recebidos | %d vistos | %s", state, read, len(b.Targets), seen, short(b.Text, 35)))
	}
	p.fillList(idNoticeList, broadcasts)
	if noticeIdx >= 0 {
		nativeCall(sendMessage, p.controls[idNoticeList], lbSetCurSel, uintptr(noticeIdx), 0)
	}
	devices := make([]string, 0, len(p.devices))
	sort.Slice(p.devices, func(i, j int) bool { return p.devices[i].Name < p.devices[j].Name })
	for _, d := range p.devices {
		display := d.Name + " | " + d.IP
		if d.LastOrder != "" {
			display += " | pedido " + d.LastOrder
		}
		devices = append(devices, display)
	}
	p.fillList(idDevicesList, devices)
	p.put(305, fmt.Sprintf("%d coletores online    •    %d observações    •    %d avisos enviados    •    %d buscas de rede recebidas", len(p.devices), len(p.notes), len(p.notices), response.DiscoveryCount))
}
func (p *nativePanel) clearNote() {
	p.editID = ""
	p.put(idOrder, "")
	p.put(idNoteText, "")
	p.put(idSave, "Salvar observação")
}
func (p *nativePanel) chooseNote() {
	i := p.listIndex(idNotesList)
	if i < 0 || i >= len(p.notes) {
		return
	}
	n := p.notes[i]
	p.editID = n.ID
	p.put(idOrder, n.Order)
	p.put(idNoteText, n.Text)
	p.put(idSave, "Salvar alterações")
}
func (p *nativePanel) saveNote() {
	order := strings.TrimSpace(p.get(idOrder))
	text := strings.TrimSpace(p.get(idNoteText))
	if order == "" || text == "" {
		winMessageBox("Dados incompletos", "Informe o número do pedido e a observação.", mbOK|mbIconWarning)
		return
	}
	err := p.request("/api/admin/save", map[string]string{"id": p.editID, "order": order, "text": text}, nil)
	if err != nil {
		p.errorDialog("Não foi possível salvar", err)
		return
	}
	p.clearNote()
	p.reload()
}
func (p *nativePanel) deleteNote() {
	i := p.listIndex(idNotesList)
	if i < 0 || i >= len(p.notes) {
		return
	}
	n := p.notes[i]
	if winMessageBox("Excluir observação", "Excluir definitivamente a observação do pedido "+n.Order+"?", mbYesNo|mbIconWarning) != idYes {
		return
	}
	if err := p.request("/api/admin/delete", map[string]string{"id": n.ID}, nil); err != nil {
		p.errorDialog("Falha ao excluir", err)
		return
	}
	if p.editID == n.ID {
		p.clearNote()
	}
	p.reload()
}
func (p *nativePanel) toggleNote() {
	i := p.listIndex(idNotesList)
	if i < 0 || i >= len(p.notes) {
		return
	}
	n := p.notes[i]
	next := "done"
	verb := "concluir"
	if n.Status == "done" {
		next = "pending"
		verb = "reabrir"
	}
	if winMessageBox("Confirmar", "Deseja "+verb+" esta observação do pedido "+n.Order+"?", mbYesNo|mbIconWarning) != idYes {
		return
	}
	if err := p.request("/api/admin/status", map[string]string{"id": n.ID, "status": next}, nil); err != nil {
		p.errorDialog("Não foi possível alterar", err)
		return
	}
	p.reload()
}
func (p *nativePanel) sendNotice() {
	message := strings.TrimSpace(p.get(idNoticeText))
	if message == "" {
		winMessageBox("Aviso geral", "Escreva a mensagem antes de enviar.", mbOK|mbIconWarning)
		return
	}
	if winMessageBox("Enviar aviso geral", fmt.Sprintf("Enviar este aviso aos %d coletores conectados?", len(p.devices)), mbYesNo|mbIconWarning) != idYes {
		return
	}
	var result struct {
		Recipients int `json:"recipients"`
	}
	if err := p.request("/api/admin/broadcast", map[string]string{"action": "send", "text": message}, &result); err != nil {
		p.errorDialog("Falha ao enviar", err)
		return
	}
	p.put(idNoticeText, "")
	p.reload()
	winMessageBox("Aviso enviado", fmt.Sprintf("Aviso destinado a %d coletores. O painel mostrará a confirmação individual.", result.Recipients), mbOK|mbIconInfo)
}
func (p *nativePanel) noticeDetails() {
	i := p.listIndex(idNoticeList)
	if i < 0 || i >= len(p.notices) {
		return
	}
	b := p.notices[i]
	var report strings.Builder
	report.WriteString(short(b.Text, 300) + "\n\n")
	for _, id := range b.Targets {
		name := b.TargetNames[id]
		if name == "" {
			name = "Coletor " + id[:min(len(id), 6)]
		}
		state := "Destinado"
		if b.Delivered[id] != "" {
			state = "Disponibilizado"
		}
		if b.Received[id] != "" {
			state = "Recebido"
		}
		if b.Seen[id] != "" {
			state = "Visto / Entendido"
		}
		report.WriteString(name + " — " + state + "\n")
	}
	if len(b.Targets) == 0 {
		report.WriteString("Nenhum destinatário.")
	}
	winMessageBox("Situação do aviso", report.String(), mbOK|mbIconInfo)
}

func (p *nativePanel) changeNotice(action string) {
	i := p.listIndex(idNoticeList)
	if i < 0 || i >= len(p.notices) {
		return
	}
	b := p.notices[i]
	prompt := "Encerrar este aviso para os coletores?"
	if action == "delete" {
		prompt = "Excluir definitivamente este aviso e o histórico de confirmações?"
	}
	if winMessageBox("Confirmar aviso", prompt, mbYesNo|mbIconWarning) != idYes {
		return
	}
	if err := p.request("/api/admin/broadcast", map[string]string{"action": action, "id": b.ID}, nil); err != nil {
		p.errorDialog("Falha ao atualizar aviso", err)
		return
	}
	p.reload()
}
func windowProc(h uintptr, message uint32, w, l uintptr) uintptr {
	if native != nil {
		switch message {
		case wmCommand:
			id := int(w & 0xffff)
			notification := int((w >> 16) & 0xffff)
			if notification == lbnDblClk && id == idNotesList {
				native.chooseNote()
				return 0
			}
			if notification != 0 {
				return nativeCall(defWindowProc, h, uintptr(message), w, l)
			}
			switch id {
			case idSave:
				native.saveNote()
			case idNew:
				native.clearNote()
			case idDeleteNote:
				native.deleteNote()
			case idToggle:
				native.toggleNote()
			case idReload:
				native.reload()
			case idSendNotice:
				native.sendNotice()
			case idDeleteNotice:
				native.changeNotice("delete")
			case idCloseNotice:
				native.changeNotice("close")
			}
			return 0
		case wmTimer:
			if w == 1 {
				native.reload()
				return 0
			}
		case wmSize:
			native.layout(int(l&0xffff), int((l>>16)&0xffff))
			return 0
		case wmCtlColorStatic:
			// Background compatible with flat native layout; static labels are fully transparent.
			nativeCall(gdi32.NewProc("SetBkMode"), w, 1)
			nativeCall(gdi32.NewProc("SetTextColor"), w, 0x002b281e)
			return native.brush
		case wmCtlColorEdit, wmCtlColorListbox:
			return nativeCall(gdi32.NewProc("GetStockObject"), 5) // WHITE_BRUSH
		case wmClose:
			nativeCall(user32.NewProc("DestroyWindow"), h)
			return 0
		case wmDestroy:
			nativeCall(user32.NewProc("PostQuitMessage"), 0)
			return 0
		}
	}
	return nativeCall(defWindowProc, h, uintptr(message), w, l)
}
func launchDesktop(s *Server, port int) {
	runtime.LockOSThread()
	native = &nativePanel{server: s, port: port}
	instance := nativeCall(kernel32.NewProc("GetModuleHandleW"), 0)
	className := wide("SeridoplastObservacoesNative3614")
	cls := winClass{Size: uint32(unsafe.Sizeof(winClass{})), WndProc: syscall.NewCallback(windowProc), Instance: instance, Cursor: nativeCall(user32.NewProc("LoadCursorW"), 0, 32512), Background: nativeCall(gdi32.NewProc("GetStockObject"), 5), ClassName: className}
	if nativeCall(user32.NewProc("RegisterClassExW"), uintptr(unsafe.Pointer(&cls))) == 0 {
		winMessageBox("Erro", "Não foi possível iniciar a interface Windows.", mbOK|mbIconWarning)
		return
	}
	native.window = nativeCall(createWindow, 0, uintptr(unsafe.Pointer(className)), uintptr(unsafe.Pointer(wide("Seridóplast | Observações e avisos"))), wsOverlappedWindow|wsVisible, 80, 60, 1210, 790, 0, 0, instance, 0)
	if native.window == 0 {
		winMessageBox("Erro", "Falha ao abrir janela nativa.", mbOK|mbIconWarning)
		return
	}
	native.initialize()
	nativeCall(user32.NewProc("ShowWindow"), native.window, 5)
	nativeCall(user32.NewProc("UpdateWindow"), native.window)
	var m winMessage
	for nativeCall(user32.NewProc("GetMessageW"), uintptr(unsafe.Pointer(&m)), 0, 0, 0) != 0 {
		nativeCall(user32.NewProc("TranslateMessage"), uintptr(unsafe.Pointer(&m)))
		nativeCall(user32.NewProc("DispatchMessageW"), uintptr(unsafe.Pointer(&m)))
	}
	for _, font := range []uintptr{native.font, native.largeFont, native.headingFont} {
		if font != 0 {
			nativeCall(gdi32.NewProc("DeleteObject"), font)
		}
	}
	if native.brush != 0 {
		nativeCall(gdi32.NewProc("DeleteObject"), native.brush)
	}
}

func startupError(message string) {
	winMessageBox("Seridóplast Observações", message, mbOK|mbIconWarning)
}
