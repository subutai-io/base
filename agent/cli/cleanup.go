package lib

import (
	"github.com/subutai-io/base/agent/config"
	"github.com/subutai-io/base/agent/lib/container"
	"github.com/subutai-io/base/agent/lib/net"
)

func Cleanup(vlan string) {
	for _, name := range container.Containers() {
		if container.GetConfigItem(config.Agent.LxcPrefix+name+"/config", "#vlan_id") == vlan {
			LxcDestroy(name)
		}
	}

	net.DeleteGateway(vlan)
	ClearVlan(vlan)
	ProxyDel(vlan, "", true)
}
