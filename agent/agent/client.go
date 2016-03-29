package agent

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"io/ioutil"
	"math/big"
	"os"
	"time"

	"github.com/subutai-io/base/agent/agent/connect"
	"github.com/subutai-io/base/agent/config"
	"github.com/subutai-io/base/agent/log"
)

func Instance() {
	email := config.Management.GpgUser
	hostname, _ := os.Hostname()
	if email == "" {
		mgn := connect.Instance()
		pk := mgn.GetKey()
		if pk != nil {
			email = pk.ExtractKeyEmail()
		}
	}

	config.Management.GpgUser = email
	config.Agent.GpgUser = hostname + "@subutai.io"
}

func x509generate() {
	hostname, _ := os.Hostname()
	host := []string{hostname}
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if log.Check(log.WarnLevel, "Generating private key", err) {
		return
	}

	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, err := rand.Int(rand.Reader, serialNumberLimit)
	if log.Check(log.WarnLevel, "Generating serial number", err) {
		return
	}

	var notBefore time.Time
	notBefore = time.Now()
	notAfter := notBefore.Add(3650 * 24 * time.Hour)
	template := x509.Certificate{
		SerialNumber:          serialNumber,
		Subject:               pkix.Name{Organization: []string{"Subutai Social Foundation"}},
		NotBefore:             notBefore,
		NotAfter:              notAfter,
		KeyUsage:              x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
		DNSNames:              host,
	}

	derBytes, err := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)
	if log.Check(log.WarnLevel, "Creating certificate", err) {
		return
	}

	os.MkdirAll(config.Agent.DataPrefix+"ssl", 0700)

	certOut, err := os.Create(config.Agent.DataPrefix + "ssl/cert.pem")
	if log.Check(log.WarnLevel, "Opening cert.pem for writing", err) {
		return
	}
	pem.Encode(certOut, &pem.Block{Type: "CERTIFICATE", Bytes: derBytes})
	certOut.Close()

	keyOut, err := os.OpenFile(config.Agent.DataPrefix+"ssl/key.pem", os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0600)
	if log.Check(log.WarnLevel, "Opening key.pem for writing", err) {
		return
	}
	pem.Encode(keyOut, &pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(priv)})
	keyOut.Close()
}

func newTLSConfig() *tls.Config {
	clientCert, err := ioutil.ReadFile(config.Agent.DataPrefix + "ssl/cert.pem")
	if log.Check(log.WarnLevel, "Checking cert.pem file", err) {
		return nil
	}
	privateKey, err := ioutil.ReadFile(config.Agent.DataPrefix + "ssl/key.pem")
	if log.Check(log.WarnLevel, "Checking key.pem file", err) {
		return nil
	}

	cert, err := tls.X509KeyPair(clientCert, privateKey)
	if log.Check(log.WarnLevel, "Loading x509 keypair", err) {
		return nil
	}

	if len(cert.Certificate) != 0 {
		cert.Leaf, err = x509.ParseCertificate(cert.Certificate[0])
		if log.Check(log.WarnLevel, "Parsing client certificates", err) {
			return nil
		}
	}

	// Create tls.Config with desired tls properties
	return &tls.Config{
		ClientAuth:         tls.NoClientCert,
		ClientCAs:          nil,
		InsecureSkipVerify: true,
		Certificates:       []tls.Certificate{cert},
	}
}
