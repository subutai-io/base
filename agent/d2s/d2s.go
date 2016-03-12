package main

import (
	"fmt"
	"github.com/subutai-io/base/agent/d2s/parser"
	"github.com/subutai-io/base/agent/lib/template"
	"io/ioutil"
	"os"
	"strings"
)

func main() {
	tmpdir := "/home/ubuntu/tmpfs/"

	if len(os.Args) <= 1 {
		fmt.Println("Please specify path to Dockerfile")
		os.Exit(1)
	}
	dockerfile := os.Args[1]
	if !strings.HasSuffix(strings.ToUpper(dockerfile), "DOCKERFILE") {
		dockerfile = dockerfile + "/Dockerfile"
	}

	head := `#!/bin/bash
#mkdir -p /opt/docker2subutai/
cd /opt/docker2subutai/
#uudecode $0 |tar zxf -
. .env
`

	//parse
	out, env, cmd, _, user := parser.Parce(dockerfile)

	// if img != "" {
	// 	cmd := exec.Command("subutai", "import", img)
	// 	cmd.Stdout = os.Stdout
	// 	cmd.Stderr = os.Stderr
	// 	cmd.Run()
	// 	// TODO add exec check here.
	// }

	// create cmd file
	out = out + `
#create cmd file
cat > /opt/docker2subutai/cmd <<- EndOfCMD
#!/bin/bash
cd /opt/docker2subutai/
. .env
` + cmd + `
EndOfCMD

chmod a+x /opt/docker2subutai/cmd
`

	// !! move system to runlvl 1 and amke services
	out = out + `
if [ -f "/etc/systemd/system/default.target" ]; then
	mv /etc/systemd/system/default.target /etc/systemd/system/default.target.orig
	ln -s /lib/systemd/system/rescue.target /etc/systemd/system/default.target
fi

if [ -f "/etc/init/rc-sysinit.conf" ]; then
	sed -i 's/env DEFAULT_RUNLEVEL=2/env DEFAULT_RUNLEVEL=1/g' /etc/init/rc-sysinit.conf
fi

#create systemd service
mkdir -p /etc/systemd/system/
cat > /etc/systemd/system/docker2subutai.service <<- EndOfSystemD
[Unit]
Description=docker2subutai Service
After=rescue.target

[Service]
User=root
Group=root
ExecStart=/opt/docker2subutai/cmd
Restart=always

[Install]
WantedBy=rescue.target
EndOfSystemD

mkdir /etc/systemd/system/rescue.target.wants
ln -s /etc/systemd/system/docker2subutai.service /etc/systemd/system/rescue.target.wants/docker2subutai.service
ln -s /lib/systemd/system/ssh.service /etc/systemd/system/rescue.target.wants/

#create upstart service
cat > /etc/init/docker2subutai.conf <<- EndOfUpstart
description     "docker2subutai service"

start on stopped rc RUNLEVEL=[1]
stop on runlevel [!1]

respawn

exec /opt/docker2subutai/cmd
EndOfUpstart
if [ -f "/etc/init/ssh.conf" ]; then
	sed -i 's/start on runlevel \[2/start on runlevel \[12/g' /etc/init/ssh.conf
fi
`
	if user != "" {
		out = out + "sed -i 's/User=root/User=" + user + "/g' /etc/systemd/system/docker2subutai.service\n"
		out = out + "sed -i -e '$i setuid=" + user + "\n' /etc/init/docker2subutai.conf\n"
	}
	out = head + out + "\nexit 0\n\n"
	// create .env
	ioutil.WriteFile(strings.Trim(dockerfile, "Dockerfile")+".env", []byte(env), 0644)

	// compress arch into script
	// data, _ := exec.Command("uuencode", tmpdir+"archive.tar.gz", "-").Output()
	// out = out + string(data) // add archived data

	// write script
	ioutil.WriteFile(strings.Trim(dockerfile, "Dockerfile")+"install.sh", []byte(out), 0755)

	// make arch
	template.Tar(strings.Trim(dockerfile, "Dockerfile"), tmpdir+"archive.tar.gz")

	// clean
	// _ = os.Remove(tmpdir + "archive.tar.gz")

}
