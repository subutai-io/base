package lib

import (
	"github.com/subutai-io/Subutai/agent/config"
	"github.com/subutai-io/Subutai/agent/lib/container"
	"github.com/subutai-io/Subutai/agent/lib/gpg"
	"github.com/subutai-io/Subutai/agent/log"
	"io/ioutil"
	"net"
	"os"
	"strings"
)

func LxcClone(parent, child, envId, addr, token string) {
	if !container.IsTemplate(parent) {
		LxcImport(parent, "", token)
	}
	if container.IsContainer(child) {
		log.Error("Container " + child + " already exist")
	}

	container.Clone(parent, child)
	gpg.GenerateKey(child)

	if len(token) != 0 {
		gpg.ExchageAndEncrypt(child, token)
	}

	if len(envId) != 0 {
		setEnvironmentId(child, envId)
	}

	if len(addr) != 0 {
		addNetConf(child, addr)
	}

	container.SetContainerUid(child)
	setDns(child)
	LxcStart(child)

	container.AptUpdate(child)
	// container.Start(child)
	// log.Info(child + " successfully cloned")
}

func setEnvironmentId(name, envId string) {
	err := os.MkdirAll(config.Agent.LxcPrefix+name+"/rootfs/etc/subutai", 755)
	log.Check(log.FatalLevel, "Creating etc/subutai directory", err)

	config, err := os.Create(config.Agent.LxcPrefix + name + "/rootfs/etc/subutai/lxc-config")
	log.Check(log.FatalLevel, "Creating lxc-config file", err)
	defer config.Close()

	_, err = config.WriteString("[Subutai-Agent]\n" + envId + "\n")
	log.Check(log.FatalLevel, "Writing environment id to config", err)

	config.Sync()
}

func setDns(name string) {
	dns := container.GetConfigItem(config.Agent.LxcPrefix+name+"/config", "lxc.network.ipv4.gateway")
	if len(dns) == 0 {
		dns = "10.10.0.254"
	}

	conf, err := ioutil.ReadFile(config.Agent.LxcPrefix + name + "/rootfs/etc/resolvconf/resolv.conf.d/original")
	log.Check(log.ErrorLevel, "Opening resolv.conf", err)

	lines := strings.Split(string(conf), "\n")

	for k, line := range lines {
		if strings.Contains(line, "nameserver 10.10.10.1") {
			lines[k] = "nameserver " + dns
		}
	}
	result := strings.Join(lines, "\n")
	err = ioutil.WriteFile(config.Agent.LxcPrefix+name+"/rootfs/etc/resolvconf/resolv.conf.d/original", []byte(result), 0644)
	log.Check(log.ErrorLevel, "Writing resolv.conf", err)
}

func setStaticNetwork(name string) {
	data, err := ioutil.ReadFile(config.Agent.LxcPrefix + name + "/rootfs/etc/network/interfaces")
	log.Check(log.WarnLevel, "Opening /etc/network/interfaces", err)

	err = ioutil.WriteFile(config.Agent.LxcPrefix+name+"/rootfs/etc/network/interfaces",
		[]byte(strings.Replace(string(data), "dhcp", "manual", 1)), 0644)
	log.Check(log.WarnLevel, "Setting internal eth0 interface to manual", err)
}

func addNetConf(name, addr string) {
	ipvlan := strings.Fields(addr)
	_, network, _ := net.ParseCIDR(ipvlan[0])
	gw := []byte(network.IP)
	gw[3]++
	container.SetContainerConf(name, [][]string{
		{"lxc.network.ipv4", ipvlan[0]},
		{"lxc.network.ipv4.gateway", net.IP(gw).String()},
		{"lxc.network.mtu", "1340"},
		{"#vlan_id", ipvlan[1]},
	})
	setStaticNetwork(name)
}
