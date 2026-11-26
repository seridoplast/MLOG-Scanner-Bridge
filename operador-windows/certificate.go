package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"errors"
	"fmt"
	"math/big"
	"net"
	"os"
	"strings"
	"time"
)

func makeWebCertificate(host, certPath, keyPath string) error {
	host = strings.TrimSpace(host)
	if host == "" || certPath == "" || keyPath == "" || certPath == keyPath || strings.ContainsAny(host, " /\\\r\n") {
		return errors.New("informe nome DNS ou IP e caminhos PEM distintos")
	}
	if ip := net.ParseIP(host); ip == nil && strings.Contains(host, ":") {
		return errors.New("informe nome DNS ou IP sem porta")
	}
	for _, p := range []string{certPath, keyPath} {
		if _, err := os.Stat(p); !os.IsNotExist(err) {
			return fmt.Errorf("não sobrescreve arquivo existente: %s", p)
		}
	}
	k, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return err
	}
	now := time.Now()
	t := x509.Certificate{SerialNumber: serial, Subject: pkix.Name{CommonName: host}, NotBefore: now.Add(-5 * time.Minute), NotAfter: now.AddDate(1, 0, 0), BasicConstraintsValid: true, KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}
	if ip := net.ParseIP(host); ip != nil {
		t.IPAddresses = []net.IP{ip}
	} else {
		t.DNSNames = []string{host}
	}
	der, err := x509.CreateCertificate(rand.Reader, &t, &t, &k.PublicKey, k)
	if err != nil {
		return err
	}
	priv, err := x509.MarshalPKCS8PrivateKey(k)
	if err != nil {
		return err
	}
	write := func(path, typ string, data []byte) error {
		f, e := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
		if e != nil {
			return e
		}
		e = pem.Encode(f, &pem.Block{Type: typ, Bytes: data})
		ce := f.Close()
		if e != nil {
			_ = os.Remove(path)
			return e
		}
		return ce
	}
	if err = write(keyPath, "PRIVATE KEY", priv); err != nil {
		return err
	}
	if err = write(certPath, "CERTIFICATE", der); err != nil {
		_ = os.Remove(keyPath)
		return err
	}
	sum := sha256.Sum256(der)
	fmt.Printf("SHA-256 do certificado: %X\n", sum)
	return nil
}
