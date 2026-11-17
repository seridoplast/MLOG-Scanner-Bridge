//go:build !windows

package main

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"
)

// O painel e uma aplicacao nativa Windows. No Linux mantemos o servidor para testes.
func launchDesktop(_ *Server, port int) {
	fmt.Printf("Painel Windows nativo; servidor de teste na porta %d. Ctrl+C encerra.\n", port)
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt, syscall.SIGTERM)
	<-ch
}

func startupError(message string) { fmt.Fprintln(os.Stderr, message) }
