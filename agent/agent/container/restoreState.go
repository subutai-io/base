package container

import (
	"github.com/subutai-io/Subutai/agent/config"
	lxcContainer "github.com/subutai-io/Subutai/agent/lib/container"
	"github.com/subutai-io/Subutai/agent/log"
	"os"
	"time"
)

func ContainersRestoreState() {
	containersStatus := make(map[string]int)

	for {
		Containerslist := lxcContainer.All()
		for _, container := range Containerslist {
			var start, stop bool

			switch containersStatus[container] {
			case 100:
			case 5:
				{
					log.Debug("Failed to START sontainer " + container + " after 5 attempts")
					containersStatus[container] = 100
				}
			case -5:
				{
					log.Debug("Failed to STOP sontainer " + container + " after 5 attempts")
					containersStatus[container] = 100
				}
			case 10:
				{
					log.Debug(".start and .stop files exist on " + container + " container ")
					containersStatus[container] = 100
				}
			default:
				{
					if _, err := os.Stat(config.Agent.LxcPrefix + container + "/.start"); err == nil {
						start = true
					}
					if _, err := os.Stat(config.Agent.LxcPrefix + container + "/.stop"); err == nil {
						stop = true
					}
					if start && stop {
						containersStatus[container] = 10
						break
					}
					switch {
					case start && lxcContainer.State(container) != "RUNNING":
						{
							log.Debug("Trying start " + container)
							lxcContainer.Start(container)
							containersStatus[container]++
						}
					case stop && lxcContainer.State(container) != "STOPPED":
						{
							log.Debug("Trying stop " + container)
							lxcContainer.Stop(container)
							containersStatus[container]--

						}
					default:
						containersStatus[container] = 0
					}
				}
			}
		}
		time.Sleep(10 * time.Second)
	}
}
