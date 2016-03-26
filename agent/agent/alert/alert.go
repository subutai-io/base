package alert

import (
	"bufio"
	"bytes"
	"github.com/subutai-io/base/agent/agent/container"
	"github.com/subutai-io/base/agent/config"
	"github.com/subutai-io/base/agent/log"
	"io/ioutil"
	"os/exec"
	"runtime"
	"strconv"
	"strings"
)

type Values struct {
	Current int `json:"current,omitempty"`
	Quota   int `json:"quota,omitempty"`
}

type HDD struct {
	Partition string `json:"partition"`
	Current   int    `json:"current"`
	Quota     int    `json:"quota"`
}

type Load struct {
	Container string `json:"id,omitempty"`
	CPU       Values `json:"cpu,omitempty"`
	RAM       Values `json:"ram,omitempty"`
	Disk      []HDD  `json:"hdd,omitempty"`
}

var (
	cpu = make(map[string][]int)
)

func read(path string) (i int) {
	out, _ := ioutil.ReadFile(path)
	i, _ = strconv.Atoi(strings.TrimSpace(string(out)))
	return
}

func id() (list map[string]string) {
	list = map[string]string{}
	out, _ := exec.Command("btrfs", "subvolume", "list", config.Agent.LxcPrefix).Output()
	scanner := bufio.NewScanner(bytes.NewReader(out))
	for scanner.Scan() {
		line := strings.Fields(scanner.Text())
		if len(line) > 8 {
			list[line[8]] = line[1]
		}
	}
	return
}

func stat() string {
	out, err := exec.Command("btrfs", "qgroup", "show", "-r", "--raw", config.Agent.LxcPrefix).Output()
	log.Check(log.DebugLevel, "Geting btrfs stats", err)

	return string(out)
}

func ramQuota(cont string) []int {
	u := read("/sys/fs/cgroup/memory/lxc/" + cont + "/memory.usage_in_bytes")
	l := read("/sys/fs/cgroup/memory/lxc/" + cont + "/memory.limit_in_bytes")

	var ramUsage = []int{0, l / 1024 / 1024}
	if l != 0 {
		ramUsage[0] = u * 100 / l
	}

	return ramUsage
}

func quotaCPU(name string) int {
	cfsPeriod := 100000
	cfs_quota_us, err := ioutil.ReadFile("/sys/fs/cgroup/cpu,cpuacct/lxc/" + name + "/cpu.cfs_quota_us")
	if err != nil {
		return -1
	}
	quota, _ := strconv.Atoi(string(cfs_quota_us))
	return quota * 100 / cfsPeriod / runtime.NumCPU()
}

func cpuLoad(cont string) []int {
	avgload := []int{0, 0}
	if len(cpu[cont]) == 0 {
		cpu[cont] = []int{0, 0, 0, 0, 0}
	}
	ticks, err := ioutil.ReadFile("/sys/fs/cgroup/cpuacct/lxc/" + cont + "/cpuacct.stat")
	if err != nil {
		return avgload
	}

	tick := strings.Fields(string(ticks))
	if len(tick) != 4 {
		return avgload
	}

	usertick, _ := strconv.Atoi(tick[1])
	systick, _ := strconv.Atoi(tick[3])
	cpu[cont] = append([]int{usertick + systick}, cpu[cont][0:4]...)

	if cpu[cont][4] == 0 {
		return avgload
	}
	avgload[0] = (cpu[cont][0] - cpu[cont][4]) / runtime.NumCPU() / 20
	avgload[1] = quotaCPU(cont)
	if avgload[1] != 0 {
		avgload[0] = avgload[0] * 100 / avgload[1]
	}
	return avgload
}

func diskQuota(mountid, diskMap string) []int {
	var u, l string
	for _, line := range strings.Split(diskMap, "\n") {
		row := strings.Fields(line)
		if len(row) > 3 {
			if row[0] == "0/"+mountid {
				u, l = row[2], row[3]
			}
		}
	}

	used, err := strconv.Atoi(u)
	if err != nil {
		used = 0
	}

	limit, err := strconv.Atoi(l)
	if err != nil {
		limit = 0
	}

	var diskUsage = []int{0, limit / 1024 / 1024 / 1024}
	if limit != 0 {
		diskUsage[0] = used * 100 / limit
	}
	return diskUsage
}

func Alert(list []container.Container) []Load {
	var load []Load
	var item Load
	var hdd HDD
	var tmp []int
	diskMap := stat()
	diskIDs := id()

	for _, cont := range list {
		trigger := false
		if cont.Status != "RUNNING" {
			continue
		}

		if tmp = cpuLoad(cont.Name); tmp[0] > 80 {
			item.CPU.Current = tmp[0]
			item.CPU.Quota = tmp[1]
			trigger = true
		}

		if tmp = ramQuota(cont.Name); tmp[0] > 80 {
			item.RAM.Current = tmp[0]
			item.RAM.Quota = tmp[1]
			trigger = true
		}
		hdd.Current = tmp[0]
		hdd.Quota = tmp[1]
		if diskQuota(diskIDs[cont.Name+"/rootfs"], diskMap)[0] > 80 {
			hdd.Partition = "Rootfs"
			trigger = true
			item.Disk = append(item.Disk, hdd)
		}
		if diskQuota(diskIDs[cont.Name+"/opt"], diskMap)[0] > 80 {
			hdd.Partition = "Opt"
			trigger = true
			item.Disk = append(item.Disk, hdd)
		}
		if diskQuota(diskIDs[cont.Name+"/var"], diskMap)[0] > 80 {
			hdd.Partition = "Var"
			trigger = true
			item.Disk = append(item.Disk, hdd)
		}
		if diskQuota(diskIDs[cont.Name+"/home"], diskMap)[0] > 80 {
			hdd.Partition = "Home"
			trigger = true
			item.Disk = append(item.Disk, hdd)
		}

		if trigger {
			item.Container = cont.Id
			load = append(load, item)
		}
	}

	return load
}
