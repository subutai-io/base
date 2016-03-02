package lib

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"github.com/pivotal-golang/archiver/extractor"
	"github.com/subutai-io/Subutai/agent/config"
	"github.com/subutai-io/Subutai/agent/lib/container"
	"github.com/subutai-io/Subutai/agent/lib/gpg"
	"github.com/subutai-io/Subutai/agent/lib/template"
	"github.com/subutai-io/Subutai/agent/log"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"runtime"
	"strings"
)

func templMd5(templ, arch, version, token string) string {
	// tr := &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}
	// client := &http.Client{Transport: tr}
	client := &http.Client{}
	url := config.Management.Kurjun + "/public/get?name=" + templ + "&type=md5&sptoken=" + token
	if len(version) != 0 {
		url = config.Management.Kurjun + "/public/get?name=" + templ + "&version=" + version + "&type=md5&sptoken=" + token
	}
	response, err := client.Get(url)
	log.Debug(config.Management.Kurjun + "/public/get?name=" + templ + "&type=md5&sptoken=" + token)
	if log.Check(log.WarnLevel, "Getting kurjun response", err) || response.StatusCode != 200 {
		return ""
	}

	hash, err := ioutil.ReadAll(response.Body)
	response.Body.Close()
	if log.Check(log.WarnLevel, "Reading response body", err) {
		return ""
	}

	return string(hash)
}

func md5sum(file string) string {
	var hashChannel = make(chan []byte, 1)
	a, err := ioutil.ReadFile(file)
	log.Check(log.FatalLevel, "Reading file "+file, err)
	s := md5.Sum(a)
	hashChannel <- s[:]
	return hex.EncodeToString(<-hashChannel)
}

func checkLocal(templ, md5, arch string) string {
	var response string
	files, _ := ioutil.ReadDir(config.Agent.LxcPrefix + "lxc-data/tmpdir")
	for _, f := range files {
		file := strings.Split(f.Name(), "-subutai-template_")
		if len(file) == 2 && file[0] == templ && strings.Contains(file[1], arch) {
			if len(md5) == 0 {
				fmt.Println("Cannot check md5 of local template. Trust anyway? (y/n)")
				_, err := fmt.Scanln(&response)
				log.Check(log.FatalLevel, "Reading input", err)
				if response == "y" {
					return config.Agent.LxcPrefix + "lxc-data/tmpdir/" + f.Name()
				}
			}
			if md5 == md5sum(config.Agent.LxcPrefix+"lxc-data/tmpdir/"+f.Name()) {
				return config.Agent.LxcPrefix + "lxc-data/tmpdir/" + f.Name()
			}
		}
	}
	return ""
}

func download(file, md5, token string) string {
	out, err := os.Create(config.Agent.LxcPrefix + "lxc-data/tmpdir/" + file)
	log.Check(log.FatalLevel, "Creating file "+file, err)
	defer out.Close()
	// tr := &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}
	// client := &http.Client{Transport: tr}
	client := &http.Client{}
	response, err := client.Get(config.Management.Kurjun + "/public/get?md5=" + md5 + "&sptoken=" + token)
	log.Check(log.FatalLevel, "Getting "+file, err)
	defer response.Body.Close()
	_, err = io.Copy(out, response.Body)
	log.Check(log.FatalLevel, "Writing file "+file, err)
	if md5 == md5sum(config.Agent.LxcPrefix+"lxc-data/tmpdir/"+file) {
		return config.Agent.LxcPrefix + "lxc-data/tmpdir/" + file
	}
	log.Error("Failed to check MD5 after download. Please check your connection and try again.")
	return ""
}

func LxcImport(templ, version, token string) {
	if container.IsTemplate(templ) {
		log.Info(templ + " template exist")
		return
	}

	config.CheckKurjun()
	fullname := templ + "-subutai-template_" + config.Misc.Version + "_" + config.Misc.Arch + ".tar.gz"
	// if len(token) == 0 {
	token = gpg.GetToken()
	// }
	md5 := templMd5(templ, runtime.GOARCH, version, token)

	archive := checkLocal(templ, md5, runtime.GOARCH)
	if len(archive) == 0 && len(md5) != 0 {
		log.Info("Downloading " + templ)
		archive = download(fullname, md5, token)
	} else if len(archive) == 0 && len(md5) == 0 {
		log.Error(templ + " " + version + " template not found")
	}

	log.Info("Unpacking template " + templ)
	tgz := extractor.NewTgz()
	tgz.Extract(archive, config.Agent.LxcPrefix+"lxc-data/tmpdir/"+templ)
	templdir := config.Agent.LxcPrefix + "lxc-data/tmpdir/" + templ
	parent := container.GetConfigItem(templdir+"/config", "subutai.parent")
	if parent != "" && parent != templ && !container.IsTemplate(parent) {
		log.Info("Parent template required: " + parent)
		LxcImport(parent, "", token)
	}

	log.Info("Installing template " + templ)
	template.Install(parent, templ)
	// TODO following lines kept for back compatibility with old templates, should be deleted when all templates will be replaced.
	os.Rename(config.Agent.LxcPrefix+templ+"/"+templ+"-home", config.Agent.LxcPrefix+templ+"/home")
	os.Rename(config.Agent.LxcPrefix+templ+"/"+templ+"-var", config.Agent.LxcPrefix+templ+"/var")
	os.Rename(config.Agent.LxcPrefix+templ+"/"+templ+"-opt", config.Agent.LxcPrefix+templ+"/opt")

	if templ == "management" {
		template.MngInit()
	}

	container.SetContainerConf(templ, [][]string{
		{"lxc.rootfs", config.Agent.LxcPrefix + templ + "/rootfs"},
		{"lxc.mount", config.Agent.LxcPrefix + templ + "/fstab"},
		{"lxc.hook.pre-start", ""},
		{"lxc.include", config.Agent.AppPrefix + "share/lxc/config/ubuntu.common.conf"},
		{"lxc.include", config.Agent.AppPrefix + "share/lxc/config/ubuntu.userns.conf"},
		{"subutai.config.path", config.Agent.AppPrefix + "etc"},
		{"lxc.network.script.up", config.Agent.AppPrefix + "bin/create_ovs_interface"},
		{"lxc.mount.entry", config.Agent.LxcPrefix + templ + "/home home none bind,rw 0 0"},
		{"lxc.mount.entry", config.Agent.LxcPrefix + templ + "/opt opt none bind,rw 0 0"},
		{"lxc.mount.entry", config.Agent.LxcPrefix + templ + "/var var none bind,rw 0 0"},
	})
	log.Check(log.FatalLevel, "Removing temp dir "+templdir, os.RemoveAll(templdir))
}
