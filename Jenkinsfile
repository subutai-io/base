#!groovy

// Configuring builder:
// Manage Jenkins -> Global Tool Configuration -> Maven installations -> Add Maven:
// Name - M3
// MAVEN_HOME - path to Maven3 home dir
//
// Manage Jenkins -> Configure System -> Environment variables
// SS_TEST_NODE - ip of SS node for smoke tests 
//
// Approve methods:
// in build job log you will see 
// Scripts not permitted to use new <method>
// Goto http://jenkins.domain/scriptApproval/
// and approve methods denied methods
//
// TODO:
// - refactor getVersion function on native groovy
// - Stash and unstash for built artifacts (?)

import groovy.json.JsonSlurperClassic

notifyBuildDetails = ""

node() {
	// Send job started notifications
	try {
	notifyBuild('STARTED')

	def mvnHome = tool 'M3'
	def workspace = pwd() 
	String artifactDir = "/tmp/jenkins/${env.JOB_NAME}"
	
	stage("Build management deb/template")
	// Use maven to to build deb and template files of management
	notifyBuildDetails = "\nFailed Step - Build management deb/template"

	checkout scm
	def artifactVersion = getVersion("management/pom.xml")
	String debFileName = "management-${env.BRANCH_NAME}.deb"
	String templateFileName = "management-subutai-template_${artifactVersion}-${env.BRANCH_NAME}_amd64.tar.gz"

	commitId = sh (script: "git rev-parse HEAD", returnStdout: true)
	String serenityReportDir = "/var/lib/jenkins/www/serenity/${commitId}"

	// create dir for artifacts
	sh """
		if test ! -d ${artifactDir}; then mkdir -p ${artifactDir}; fi
	"""

	// build deb
	sh """
		cd management
		export GIT_BRANCH=${env.BRANCH_NAME}
		if [[ "${env.BRANCH_NAME}" == "dev" ]]; then
			${mvnHome}/bin/mvn clean install -P deb -Dgit.branch=${env.BRANCH_NAME} sonar:sonar -Dsonar.branch=${env.BRANCH_NAME}
		elif [[ "${env.BRANCH_NAME}" == "hotfix-"* ]]; then
			${mvnHome}/bin/mvn clean install -P deb -Dgit.branch=${env.BRANCH_NAME}
		else 
			${mvnHome}/bin/mvn clean install -Dmaven.test.skip=true -P deb -Dgit.branch=${env.BRANCH_NAME}
		fi		
		find ${workspace}/management/server/server-karaf/target/ -name *.deb | xargs -I {} mv {} ${artifactDir}/${debFileName}
	"""
	// Start MNG-RH Lock
	lock('rh-node') {
		// create management template
		sh """
			set +x
			ssh root@gw.intra.lan <<- EOF
			set -e
			
			/apps/bin/subutai destroy management
			/apps/bin/subutai clone openjre8 management
			/bin/sleep 5
			/bin/cp /mnt/lib/lxc/jenkins/rootfs/${artifactDir}/${debFileName} /mnt/lib/lxc/management/rootfs/tmp/
			/apps/bin/lxc-attach -n management -- apt-get update
			/apps/bin/lxc-attach -n management -- sync
			/apps/bin/lxc-attach -n management -- apt-get -y --force-yes install --only-upgrade procps
			/apps/bin/lxc-attach -n management -- apt-get -y --force-yes install --only-upgrade udev
			/apps/bin/lxc-attach -n management -- apt-get -y --force-yes install --only-upgrade libdbus-1-3
			/apps/bin/lxc-attach -n management -- apt-get -y --force-yes install subutai-dnsmasq subutai-influxdb curl gorjun
			/apps/bin/lxc-attach -n management -- dpkg -i /tmp/${debFileName}
			/apps/bin/lxc-attach -n management -- sync
			/bin/rm /mnt/lib/lxc/management/rootfs/tmp/${debFileName}
			/apps/bin/subutai export management -v ${artifactVersion}-${env.BRANCH_NAME}

			mv /mnt/lib/lxc/tmpdir/management-subutai-template_${artifactVersion}-${env.BRANCH_NAME}_amd64.tar.gz /mnt/lib/lxc/jenkins/rootfs/${artifactDir}
		EOF"""
	}

	stage("Update management on test node")
	// Deploy built template to remore test-server
	notifyBuildDetails = "\nFailed on Stage - Update management on test node"

	// Start Test-Peer Lock
	if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME ==~ /hotfix-.*/) {
		lock('test-node') {
			// destroy existing management template on test node and install latest available snap
			sh """
				set +x
				ssh root@${env.SS_TEST_NODE} <<- EOF
				set -e
				subutai destroy everything
				if test -f /var/lib/apps/subutai/current/p2p.save; then rm /var/lib/apps/subutai/current/p2p.save; fi
				if test -f /mnt/lib/lxc/tmpdir/management-subutai-template_*; then rm /mnt/lib/lxc/tmpdir/management-subutai-template_*; fi
				/apps/subutai/current/bin/curl https://cdn.subut.ai:8338/kurjun/rest/raw/get?name=subutai_${artifactVersion}_amd64-dev.snap -o /tmp/subutai-latest.snap
				if test -f /var/lib/apps/subutai/current/agent.gcfg; then rm /var/lib/apps/subutai/current/agent.gcfg; fi
				snappy install --allow-unauthenticated /tmp/subutai-latest.snap
			EOF"""

			// update rh on test node
			// def rhUpdateStatus = sh (script: "ssh root@${env.SS_TEST_NODE} /apps/subutai/current/bin/subutai update rh -c | cut -d '=' -f4 | tr -d '\"' | tr -d '\n'", returnStdout: true)
			// if (rhUpdateStatus == '[Update is available] ') {
			// 	sh """
			// 		ssh root@${env.SS_TEST_NODE} <<- EOF
			// 		set -e
			// 		subutai update rh
			// 	"""
			// }

			// copy generated management template on test node
			sh """
				set +x
				scp ${artifactDir}/management-subutai-template_${artifactVersion}-${env.BRANCH_NAME}_amd64.tar.gz root@${env.SS_TEST_NODE}:/mnt/lib/lxc/tmpdir
			"""

			// install generated management template
			sh """
				set +x
				ssh root@${env.SS_TEST_NODE} <<- EOF
				set -e
				sed 's/branch = .*/branch = ${env.BRANCH_NAME}/g' -i /var/lib/apps/subutai/current/agent.gcfg
				sed 's/cdn.subut.ai/cdn.local/g' -i /var/lib/apps/subutai/current/agent.gcfg
				echo y | subutai import management
				sed 's/cdn.local/cdn.subut.ai/g' -i /mnt/lib/lxc/management/rootfs/etc/apt/sources.list.d/subutai-repo.list
				sed 's/cdn.local/cdn.subut.ai/g' -i /var/lib/apps/subutai/current/agent.gcfg
			EOF"""

			/* wait until SS starts */
			timeout(time: 5, unit: 'MINUTES') {
				sh """
					set +x
					echo "Waiting SS"
					while [ \$(curl -k -s -o /dev/null -w %{http_code} 'https://${env.SS_TEST_NODE}:8443/rest/v1/peer/ready') != "200" ]; do
						sleep 5
					done
				"""
			}

			stage("Integration tests")
			// Run Serenity Tests
			notifyBuildDetails = "\nFailed on Stage - Integration tests\nSerenity Tests Results:\n${env.JENKINS_URL}serenity/${commitId}"

			git url: "https://github.com/subutai-io/playbooks.git"
			sh """
				set +e
				./run_tests_qa.sh -m ${env.SS_TEST_NODE}
				./run_tests_qa.sh -s all
				${mvnHome}/bin/mvn integration-test -Dwebdriver.firefox.profile=src/test/resources/profilePgpFF
				OUT=\$?
				${mvnHome}/bin/mvn serenity:aggregate
				cp -rl target/site/serenity ${serenityReportDir}
				if [ \$OUT -ne 0 ];then
					exit 1
				fi
			"""
		}
	}

	if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'dev') {
		stage("Deploy artifacts on kurjun")
		// Deploy built and tested artifacts to cdn
		notifyBuildDetails = "\nFailed on Stage - Deploy artifacts on kurjun"

		// cdn auth creadentials 
		String url = "https://eu0.cdn.subut.ai:8338/kurjun/rest"
		String user = "jenkins"
		def authID = sh (script: """
			set +x
			curl -s -k ${url}/auth/token?user=${user} | gpg --clearsign --no-tty
			""", returnStdout: true)
		def token = sh (script: """
			set +x
			curl -s -k -Fmessage=\"${authID}\" -Fuser=${user} ${url}/auth/token
			""", returnStdout: true)

		// upload artifacts on cdn
		// upload deb
		String responseDeb = sh (script: """
			set +x
			curl -s -k https://eu0.cdn.subut.ai:8338/kurjun/rest/apt/info?name=${debFileName}
			""", returnStdout: true)
		sh """
			set +x
			curl -s -k -Ffile=@${artifactDir}/${debFileName} -Ftoken=${token} ${url}/apt/upload
		"""
		// def signatureDeb = sh (script: "curl -s -k -Ffile=@${artifactDir}/${debFileName} -Ftoken=${token} ${url}/apt/upload | gpg --clearsign --no-tty", returnStdout: true)
		// sh "curl -s -k -Ftoken=${token} -Fsignature=\"${signatureDeb}\" ${url}/auth/sign"

		// delete old deb
		if (responseDeb != "Not found") {
			def jsonDeb = jsonParse(responseDeb)	
			sh """
				set +x
				curl -s -k -X DELETE ${url}/apt/delete?id=${jsonDeb["id"]}'&'token=${token}
			"""
		}

		// upload template
		String responseTemplate = sh (script: """
			set +x
			curl -s -k https://eu0.cdn.subut.ai:8338/kurjun/rest/template/info?name=management'&'version=${env.BRANCH_NAME}
			""", returnStdout: true)
		def signatureTemplate = sh (script: """
			set +x
			curl -s -k -Ffile=@${artifactDir}/${templateFileName} -Ftoken=${token} ${url}/template/upload | gpg --clearsign --no-tty
			""", returnStdout: true)
		sh """
			set +x
			curl -s -k -Ftoken=${token} -Fsignature=\"${signatureTemplate}\" ${url}/auth/sign
		"""

		// delete old template
		if (responseTemplate != "Not found") {
			def jsonTemplate = jsonParse(responseTemplate)
			sh """
				set +x
				curl -s -k -X DELETE ${url}/template/delete?id=${jsonTemplate["id"]}'&'token=${token}
			"""
		}
	}
	} catch (e) { 
		currentBuild.result = "FAILED"
		throw e
	} finally {
		// Success or failure, always send notifications
		notifyBuild(currentBuild.result, notifyBuildDetails)
	}
}

def getVersionFromPom(pom) {
	def matcher = readFile(pom) =~ '<version>(.+)</version>'
	matcher ? matcher[1][1] : null
}

def String getVersion(pom) {
	def pomver = getVersionFromPom(pom)
	def ver = sh (script: "/bin/echo ${pomver} | cut -d '-' -f 1", returnStdout: true)
	return "${ver}".trim()
}

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

// https://jenkins.io/blog/2016/07/18/pipline-notifications/
def notifyBuild(String buildStatus = 'STARTED', String details = '') {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESSFUL'

  // Default values
  def colorName = 'RED'
  def colorCode = '#FF0000'
  def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"  	
  def summary = "${subject} (${env.BUILD_URL})"

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color = 'YELLOW'
    colorCode = '#FFFF00'  
  } else if (buildStatus == 'SUCCESSFUL') {
    color = 'GREEN'
    colorCode = '#00FF00'
  } else {
    color = 'RED'
    colorCode = '#FF0000'
	summary = "${subject} (${env.BUILD_URL})${details}"
  }
  // Get token
  def slackToken = getSlackToken('ss-bots-slack-token')
  // Send notifications
  slackSend (color: colorCode, message: summary, teamDomain: 'subutai-io', token: "${slackToken}")
}

// get slack token from global jenkins credentials store
@NonCPS
def getSlackToken(String slackCredentialsId){
	// id is ID of creadentials
	def jenkins_creds = Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0]

	String found_slack_token = jenkins_creds.getStore().getDomains().findResult { domain ->
	  jenkins_creds.getCredentials(domain).findResult { credential ->
	    if(slackCredentialsId.equals(credential.id)) {
	      credential.getSecret()
	    }
	  }
	}
	return found_slack_token
}