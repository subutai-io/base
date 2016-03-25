package lib

import (
	"github.com/subutai-io/base/agent/config"
	"github.com/subutai-io/base/agent/lib/container"
	"github.com/subutai-io/base/agent/lib/net"
	"github.com/subutai-io/base/agent/lib/net/p2p"
)

func Cleanup(vlan string) {
	for _, name := range container.Containers() {
		if container.GetConfigItem(config.Agent.LxcPrefix+name+"/config", "#vlan_id") == vlan {
			LxcDestroy(name)
		}
	}

	net.DeleteGateway(vlan)
	ClearVlan(vlan)
	p2p.RemoveByIface("p2p-" + vlan)
	ProxyDel(vlan, "", true)
}
