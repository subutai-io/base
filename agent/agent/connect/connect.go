package connect

import (
	"bytes"
	"crypto/tls"
	"github.com/subutai-io/Subutai/agent/agent/container"
	"github.com/subutai-io/Subutai/agent/agent/utils"
	"github.com/subutai-io/Subutai/agent/lib/gpg"
	"github.com/subutai-io/Subutai/agent/log"
	"net/http"
	"strconv"
	"time"
)

func Connect(host, port, user, pass string) {
	log.Debug("Connecting to " + host + ":" + port)
	log.Debug("GPG Keys of user " + user + " will be used to encrypt and decrypt messages ")
	log.Debug("Secret message(" + strconv.Itoa(len(pass)) + ") will be prompt to validate connection")

	mgn := Instance()
	mgn.Ipv4 = host
	mgn.Port = port

	rh := NewRH()
	rh.Pk = gpg.GetPk(user)
	rh.UUID = gpg.GetFingerprint(user)
	rh.Cert = utils.PublicCert()
	rh.Secret = pass
	rh.Containers = container.GetActiveContainers(true)
	log.Info(rh.Json())

	for _, cont := range rh.Containers {
		gpg.ImportMHKeyNoDefaultKeyring(cont.Name)
	}

	enMsg := ""
	for len(enMsg) == 0 {
		log.Debug("Adding management host PK")
		time.Sleep(time.Second * 5)
		pk := mgn.GetKey()
		if pk != nil {
			pk.Store()
			enMsg = gpg.EncryptWrapper(user, pk.Id, rh.Json())
		}
	}

	tr := &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}
	client := &http.Client{Transport: tr}

	resp, err := client.Post(mgn.URL(), "text/plain", bytes.NewBuffer([]byte(enMsg)))
	log.Check(log.WarnLevel, "POSTing request to "+mgn.URL(), err)
	defer resp.Body.Close()
}
