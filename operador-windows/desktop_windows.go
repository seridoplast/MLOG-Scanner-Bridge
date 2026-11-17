//go:build windows

package main

// Central de Operações: painel e servidor embutidos no mesmo .exe.
// Abre janela sem barra de endereço via Edge/Chrome instalado no Windows.
// O subprocesso gráfico deve ficar visível: HideWindow=true na versão
// anterior impedia a exibição da janela em alguns computadores.
import (
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
	"unicode/utf16"
	"unsafe"
)

var user32 = syscall.NewLazyDLL("user32.dll")
var messageBoxW = user32.NewProc("MessageBoxW")
var enumWindows = user32.NewProc("EnumWindows")
var isWindowVisible = user32.NewProc("IsWindowVisible")
var getWindowTextLength = user32.NewProc("GetWindowTextLengthW")
var getWindowText = user32.NewProc("GetWindowTextW")

func startupError(message string) {
	title, _ := syscall.UTF16PtrFromString("Seridóplast - Central de Operações")
	body, _ := syscall.UTF16PtrFromString(message)
	messageBoxW.Call(0, uintptr(unsafe.Pointer(body)), uintptr(unsafe.Pointer(title)), 0x10)
}

func findWebEngine() string {
	// Prova primeiro o Edge instalado por máquina; depois a instalação por usuário.
	dirs := []string{
		os.Getenv("ProgramFiles(x86)"), os.Getenv("ProgramFiles"),
		os.Getenv("ProgramW6432"), os.Getenv("LocalAppData"),
	}
	subdirs := []string{
		filepath.Join("Microsoft", "Edge", "Application", "msedge.exe"),
		filepath.Join("Google", "Chrome", "Application", "chrome.exe"),
		filepath.Join("Microsoft", "Edge Beta", "Application", "msedge.exe"),
	}
	for _, dir := range dirs {
		if dir == "" {
			continue
		}
		for _, sub := range subdirs {
			candidate := filepath.Join(dir, sub)
			if st, err := os.Stat(candidate); err == nil && !st.IsDir() {
				return candidate
			}
		}
	}
	for _, binary := range []string{"msedge.exe", "chrome.exe"} {
		if path, err := exec.LookPath(binary); err == nil {
			return path
		}
	}
	return ""
}

// Detecta a janela real do painel: se o Edge reaproveitar um processo já
// inicializado, o launcher pode terminar antes da janela ser fechada.
func panelWindowVisible() bool {
	visible := false
	cb := syscall.NewCallback(func(hwnd uintptr, _ uintptr) uintptr {
		if ok, _, _ := isWindowVisible.Call(hwnd); ok == 0 {
			return 1
		}
		n, _, _ := getWindowTextLength.Call(hwnd)
		if n == 0 || n > 512 {
			return 1
		}
		chars := make([]uint16, n+1)
		getWindowText.Call(hwnd, uintptr(unsafe.Pointer(&chars[0])), uintptr(len(chars)))
		title := strings.ToLower(string(utf16.Decode(chars[:n])))
		if strings.Contains(title, "seridóplast") && strings.Contains(title, "central de operações") {
			visible = true
			return 0
		}
		return 1
	})
	enumWindows.Call(cb, 0)
	return visible
}

func waitForPanel() bool {
	for j := 0; j < 60; j++ {
		if panelWindowVisible() {
			return true
		}
		time.Sleep(250 * time.Millisecond)
	}
	return false
}

// Se a versão anterior ainda estiver rodando e segurando a porta, um novo
// clique no executável reabre a janela sem exigir scripts externos.
func alreadyRunningSeridoplast(port int) bool {
	client := &http.Client{Timeout: 800 * time.Millisecond}
	r, err := client.Get(fmt.Sprintf("http://127.0.0.1:%d/api/admin/state", port))
	if err != nil {
		return false
	}
	defer r.Body.Close()
	// Refuse to display an outdated UI from a previous EXE that still holds port 8765.
	return r.StatusCode == http.StatusOK && r.Header.Get("X-Seridoplast-Central-Version") == centralVersion
}

func launchDesktop(_ *Server, port int) {
	executable := findWebEngine()
	if executable == "" {
		startupError("O Microsoft Edge não foi encontrado. Esta versão tem todos os arquivos do painel embutidos no executável, mas utiliza o Edge para desenhar a janela. Instale ou habilite o Microsoft Edge e abra este mesmo .exe novamente.")
		return
	}
	cacheDir, err := os.UserCacheDir()
	if err != nil {
		startupError("Falha ao localizar cache do Windows: " + err.Error())
		return
	}
	base := filepath.Join(cacheDir, "Seridoplast", "CentralOperacoes")
	if err = os.MkdirAll(base, 0700); err != nil {
		startupError("Falha ao iniciar interface: " + err.Error())
		return
	}
	// Perfil isolado POR EXECUÇÃO evita conflito com Edge aberto e com perfis
	// abandonados. Não distribui ou necessita arquivos auxiliares.
	profile, err := os.MkdirTemp(base, "janela-")
	if err != nil {
		startupError("Falha ao preparar a janela: " + err.Error())
		return
	}
	defer os.RemoveAll(profile)

	url := "http://127.0.0.1:" + strconv.Itoa(port)
	args := []string{
		"--app=" + url,
		"--user-data-dir=" + profile,
		"--no-first-run", "--no-default-browser-check", "--disable-background-mode",
		"--disable-extensions", "--window-size=1360,900",
	}
	cmd := exec.Command(executable, args...)
	// IMPORTANTE: não ocultar janela do Edge: o processo pai já é GUI.
	if err := cmd.Start(); err != nil {
		startupError("Não foi possível abrir a janela do painel.\n\n" + err.Error())
		return
	}
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	// Se o processo de inicialização morrer imediatamente, distinguir crash de
	// repasse para outro processo Edge que já abriu a janela solicitada.
	hasWindow := waitForPanel()
	if !hasWindow {
		select {
		case e := <-done:
			if e == nil {
				e = errors.New("o Edge fechou antes de abrir a janela")
			}
			startupError("A janela não abriu. Verifique se o Edge está autorizado no Windows.\n\n" + e.Error())
		default:
			startupError("O processo abriu, mas a janela não apareceu. Confira se há bloqueio do Edge ou do antivírus.")
			_ = cmd.Process.Kill()
		}
		return
	}
	// Aguarda o fechamento da janela. Isso mantém o servidor LAN ativo.
	for panelWindowVisible() {
		time.Sleep(550 * time.Millisecond)
	}
	// O navegador e a central podem encerrar normalmente sem intervenção.
	select {
	case <-done:
	default:
		_ = cmd.Process.Kill()
	}
}
