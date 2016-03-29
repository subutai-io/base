package utils

import (
	"io/ioutil"
	"net"
	"strings"

	"github.com/subutai-io/base/agent/config"
	"github.com/subutai-io/base/agent/log"
)

type Iface struct {
	InterfaceName string `json:"interfaceName"`
	Ip            string `json:"ip"`
}

func GetInterfaces() []Iface {
	n_ifaces, err := net.Interfaces()
	log.Check(log.WarnLevel, "Getting network interfaces", err)

	l_ifaces := []Iface{}
	for _, ifac := range n_ifaces {
		if ifac.Name == "lo0" || ifac.Name == "lo" {
			continue
		}
		inter := new(Iface)
		inter.InterfaceName = ifac.Name

		addrs, _ := ifac.Addrs()
		for _, addr := range addrs {
			switch v := addr.(type) {
			case *net.IPNet:
				ipv4 := v.IP.To4().String()
				if ipv4 != "<nil>" {
					inter.Ip = ipv4
					l_ifaces = append(l_ifaces, *inter)
				}
			}
		}
	}
	return l_ifaces
}

func PublicCert() string {
	pemCerts, err := ioutil.ReadFile(config.Agent.DataPrefix + "ssl/cert.pem")
	if log.Check(log.WarnLevel, "Checking cert.pem file", err) {
		return ""
	}
	return string(pemCerts)
}

func InstanceType() string {
	uuid, err := ioutil.ReadFile("/sys/hypervisor/uuid")
	if !log.Check(log.DebugLevel, "Checking if AWS ec2 by reading /sys/hypervisor/uuid", err) {
		if strings.HasPrefix(string(uuid), "ec2") {
			return "EC2"
		}
	}
	return "LOCAL"
}
