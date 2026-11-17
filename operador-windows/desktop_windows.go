//go:build windows

package main

// Portátil: executa o painel em janela separada do Edge, sem abas, endereço
// ou menus de navegação. Edge é o motor de renderização presente no Windows
// moderno; os dados continuam no servidor local embutido no executável.
import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"syscall"
	"unsafe"
)

func startupError(message string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	proc := user32.NewProc("MessageBoxW")
	wText, _ := syscall.UTF16PtrFromString(message)
	wTitle, _ := syscall.UTF16PtrFromString("Seridóplast · Central de Operações")
	proc.Call(0, uintptr(unsafe.Pointer(wText)), uintptr(unsafe.Pointer(wTitle)), 0x30)
}

func findWebEngine() string {
	programFiles := []string{os.Getenv("PROGRAMFILES(X86)"), os.Getenv("PROGRAMFILES"), os.Getenv("LOCALAPPDATA")}
	for _, dir := range programFiles {
		if dir == "" {
			continue
		}
		for _, sub := range []string{
			filepath.Join("Microsoft", "Edge", "Application", "msedge.exe"),
			filepath.Join("Google", "Chrome", "Application", "chrome.exe"),
		} {
			full := filepath.Join(dir, sub)
			if info, err := os.Stat(full); err == nil && !info.IsDir() {
				return full
			}
		}
	}
	for _, exe := range []string{"msedge.exe", "chrome.exe"} {
		if full, err := exec.LookPath(exe); err == nil {
			return full
		}
	}
	return ""
}

func launchDesktop(_ *Server, port int) {
	exe := findWebEngine()
	if exe == "" {
		startupError("Não encontramos Microsoft Edge ou Google Chrome. Instale o Edge para abrir a interface do painel. O painel não requer instalação, mas utiliza o motor de navegação disponível no Windows.")
		return
	}
	config, err := os.UserCacheDir()
	if err != nil {
		startupError("Não foi possível abrir a pasta do perfil da interface: " + err.Error())
		return
	}
	profile := filepath.Join(config, "Seridoplast", "PainelOperacional", "Interface")
	if err = os.MkdirAll(profile, 0700); err != nil {
		startupError("Não foi possível preparar a interface: " + err.Error())
		return
	}
	url := "http://127.0.0.1:" + strconv.Itoa(port)
	cmd := exec.Command(exe, "--app="+url, "--user-data-dir="+profile, "--no-first-run", "--no-default-browser-check", "--disable-background-mode", "--disable-extensions", "--window-size=1340,870")
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	if err = cmd.Run(); err != nil {
		startupError(fmt.Sprintf("Não foi possível abrir o painel em janela de aplicativo. Verifique o Microsoft Edge.\n\n%v", err))
	}
	// Ao fechar a janela, o processo Edge isolado encerra e a aplicação volta.
}
