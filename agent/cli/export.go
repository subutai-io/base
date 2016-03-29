package lib

import (
	"github.com/subutai-io/base/agent/cli/lib"
	"github.com/subutai-io/base/agent/config"
	"github.com/subutai-io/base/agent/lib/container"
	"github.com/subutai-io/base/agent/lib/fs"
	"github.com/subutai-io/base/agent/lib/template"
	"github.com/subutai-io/base/agent/log"
	"os"
	"runtime"
)

// cfg declared in promote.go
// LxcExport exports the given name if it suits the needs.
func LxcExport(name string) {
	tmpDIR := config.Agent.LxcPrefix + "tmpdir/"
	packageName := tmpDIR + name + "-subutai-template"
	packageVersion := config.Template.Version
	packageArch := runtime.GOARCH
	tarPackageName := packageName + "_" + packageVersion + "_" + packageArch
	tarFile := tarPackageName + ".tar.gz"

	if !container.IsTemplate(name) {
		LxcPromote(name)
	}
	// check: parent is template
	parent := container.GetParent(name)
	if !container.IsTemplate(parent) {
		log.Error("Parent " + parent + " is not a template")
	}

	containerTmpFolder := tarPackageName
	deltaFolder := containerTmpFolder + "/deltas"
	diffFolder := containerTmpFolder + "/diff"
	os.MkdirAll(containerTmpFolder, 0755)
	os.MkdirAll(deltaFolder, 0755)
	os.MkdirAll(diffFolder, 0755)

	fs.Send(config.Agent.LxcPrefix+parent+"/rootfs", config.Agent.LxcPrefix+name+"/rootfs", deltaFolder+"/rootfs.delta")
	fs.Send(config.Agent.LxcPrefix+parent+"/home", config.Agent.LxcPrefix+name+"/home", deltaFolder+"/home.delta")
	fs.Send(config.Agent.LxcPrefix+parent+"/opt", config.Agent.LxcPrefix+name+"/opt", deltaFolder+"/opt.delta")
	fs.Send(config.Agent.LxcPrefix+parent+"/var", config.Agent.LxcPrefix+name+"/var", deltaFolder+"/var.delta")

	// changeConfigFile(name, packageVersion, tarPackageName)
	container.SetContainerConf(name, [][]string{
		{"subutai.template.package", tarPackageName + ".tar.gz"},
		{"subutai.template.version", packageVersion},
	})

	src := config.Agent.LxcPrefix + name
	lib.CopyFile(src+"/fstab", containerTmpFolder+"/fstab")
	lib.CopyFile(src+"/config", containerTmpFolder+"/config")
	lib.CopyFile(src+"/packages", containerTmpFolder+"/packages")
	if parent != name {
		lib.CopyFile(src+"/diff/var.diff", containerTmpFolder+"/diff/var.diff")
		lib.CopyFile(src+"/diff/opt.diff", containerTmpFolder+"/diff/opt.diff")
		lib.CopyFile(src+"/diff/home.diff", containerTmpFolder+"/diff/home.diff")
		lib.CopyFile(src+"/diff/rootfs.diff", containerTmpFolder+"/diff/rootfs.diff")
	}

	template.Tar(containerTmpFolder, tarFile)
	log.Check(log.FatalLevel, "Remove tmpdir", os.RemoveAll(containerTmpFolder))
	log.Info(name + " exported to " + containerTmpFolder + ".tar.gz")
}
