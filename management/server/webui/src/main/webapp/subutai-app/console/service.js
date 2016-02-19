'use strict';

angular.module('subutai.console.service', [])
	.factory('consoleService', consoleService);

consoleService.$inject = ['$http', 'environmentService'];

function consoleService($http, environmentService) {
	var BASE_URL = SERVER_URL + 'rest/ui/commands/';

	var consoleService = {
		getEnvironments: getEnvironments,
		sendCommand: sendCommand,
		getSSH: getSSH,
	};

	return consoleService;

	// Implementation

	function getEnvironments() {
		return environmentService.getEnvironments();
	}

	function getSSH(environmentId, hostId, period) {
		if(environmentId === undefined || environmentId.length == 0) {
			environmentId = null;
		}
		return $http.get(
			SERVER_URL + 'rest/ui/environments/' + environmentId + '/containers/' + hostId + '/ssh', 
			{withCredentials: true, headers: {'Content-Type': 'application/json'}}
		);		
	}

	function sendCommand(cmd, peerId, path, daemon, timeOut, environmentId) {
		var postData = 'command=' + cmd + '&hostId=' + peerId + '&path=' + path;

		if(daemon) {
			postData += '&daemon=true';
		}
		if(timeOut !== undefined && timeOut > 0) {
			postData += '&timeOut=' + parseInt(timeOut);
		}
		if(environmentId && environmentId.length > 0) {
			postData += '&environmentId=' + environmentId;
		}

		return $http.post(
			BASE_URL,
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}
}
