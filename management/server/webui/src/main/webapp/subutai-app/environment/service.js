'use strict';

angular.module('subutai.environment.service', [])
	.factory('environmentService', environmentService);


environmentService.$inject = ['$http', '$q'];

function environmentService($http, $q) {

	var ENVIRONMENTS_URL = SERVER_URL + 'rest/ui/environments/';
	var NOT_REGISTERED_CONTAINERS_URL = SERVER_URL + 'rest/ui/local/containers/notregistered';

	var ENVIRONMENT_START_BUILD = ENVIRONMENTS_URL + 'build/';
	var ENVIRONMENT_ADVANCED_BUILD = ENVIRONMENTS_URL + 'build/advanced';

	var CONTAINERS_URL = ENVIRONMENTS_URL + 'containers/';
	var CONTAINER_TYPES_URL = CONTAINERS_URL + 'types/';
	var DOMAINS_URL = ENVIRONMENTS_URL + 'domains/';

	var STRATEGIES_URL = ENVIRONMENTS_URL + 'strategies/';

	var TEMPLATES_URL = ENVIRONMENTS_URL + 'templates/';

	var PRIVATE_TEMPLATES_URL = ENVIRONMENTS_URL + 'templates/private';

	var VERIFIED_TEMPLATE_URL = ENVIRONMENTS_URL + 'templates/verified/';

	var PEERS_URL = ENVIRONMENTS_URL + 'peers/';

	var RH_URL = ENVIRONMENTS_URL + 'resourcehosts/';

	var TENANTS_URL = ENVIRONMENTS_URL + 'tenants';


	var environmentService = {
		getTemplates: getTemplates,
		getPrivateTemplates: getPrivateTemplates,
		getVerifiedTemplate: getVerifiedTemplate,

		getStrategies : getStrategies,


		getEnvironments : getEnvironments,
		getTenants: getTenants,
		startEnvironmentAdvancedBuild : startEnvironmentAdvancedBuild,
		startEnvironmentAutoBuild: startEnvironmentAutoBuild,
		destroyEnvironment: destroyEnvironment,
		modifyEnvironment: modifyEnvironment,


		setSshKey : setSshKey,
		getSshKey : getSshKey,
		removeSshKey : removeSshKey,


		getDomainStrategies : getDomainStrategies,
		getDomain : getDomain,
		setDomain : setDomain,
		removeDomain : removeDomain,


		getContainerStatus : getContainerStatus,
		destroyContainer : destroyContainer,
		switchContainer : switchContainer,
		getContainerDomainNPort : getContainerDomainNPort,
		setContainerDomainNPort : setContainerDomainNPort,
		setContainerName : setContainerName,
		createTemplate : createTemplate,


		getContainersType : getContainersType,
		getContainersTypesInfo : getContainersTypesInfo,
		setTags : setTags,
		removeTag : removeTag,


		getEnvQuota: getEnvQuota,
		updateQuota: updateQuota,

        getUploadProgress: getUploadProgress,

		getPeers : getPeers,

		getResourceHosts: getResourceHosts,


		getShared: getShared,
		share: share,

		revoke: revoke,

		getInstalledPlugins: getInstalledPlugins,

		getNotRegisteredContainers: getNotRegisteredContainers,
		deleteNotRegisteredContainer: deleteNotRegisteredContainer,

		getServerUrl : function getServerUrl() { return ENVIRONMENTS_URL; },
		getTenantsUrl : function getTenantsUrl() { return TENANTS_URL; }
	};

	return environmentService;



	//// Implementation

	function getVerifiedTemplate(name){
        return $http.get(VERIFIED_TEMPLATE_URL + name, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function getTemplates() {
		var callF = $q.defer();

		$http.get(TEMPLATES_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}})
			.success(function(data) {
				var res = {};
                res['other'] = [];

                for( var i = 0; i < data.length; i++ ){
                    if(data[i].tags){
                        for( var j = 0; j < data[i].tags.length; j++ ){
                            if ( res[data[i].tags[j]] == undefined ) {
                                res[data[i].tags[j]] = [];
                            }
                            res[data[i].tags[j]].push(data[i]);
                        }
                    }else{
                       res['other'].push(data[i]);
                    }
                }

				callF.resolve(res);
			});

		return callF.promise;
	}

    function getPrivateTemplates() {
		var callF = $q.defer();

		$http.get(PRIVATE_TEMPLATES_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}})
			.success(function(data) {
				callF.resolve(data);
			});

		return callF.promise;
	}

	function getUploadProgress(templateName) {
		return $http.get(SERVER_URL + "rest/v1/peer/templatesprogress/" + templateName);
	}


	function getEnvironments() {
		return $http.get(ENVIRONMENTS_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function getTenants() {
		return $http.get(TENANTS_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}


	function startEnvironmentAutoBuild(environmentName, containers) {
		var postData = 'name=' + environmentName + "&topology=" + containers;
		return $http.post(
			ENVIRONMENT_START_BUILD,
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function startEnvironmentAdvancedBuild(environmentName, containers) {
		var postData = 'name=' + environmentName + "&topology=" + containers;
		return $http.post(
			ENVIRONMENT_ADVANCED_BUILD,
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function destroyEnvironment(environmentId) {
		return $http.delete(ENVIRONMENTS_URL + environmentId);
	}

	function modifyEnvironment(containers, advanced) {
		if(advanced == undefined || advanced == null) advanced = '';
		var postData = 'topology=' + JSON.stringify( containers.topology )
			+ '&removedContainers=' + JSON.stringify( containers.removedContainers )
			+ '&quotaContainers=' + JSON.stringify( containers.changingContainers );

		return $http.post(
			ENVIRONMENTS_URL + containers.environmentId + '/modify/' + advanced,
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function switchContainer(containerId, type) {
		return $http.put(
			CONTAINERS_URL + containerId + '/' + type,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function getContainerStatus(containerId) {
		return $http.get(
			CONTAINERS_URL + containerId + '/state',
			{withCredentials: true, headers: {'Content-Type': 'application/json'}}
		);
	}

	function destroyContainer(containerId) {
		return $http.delete(CONTAINERS_URL + containerId);
	}


	function setSshKey(sshKey, environmentId) {
		var postData = 'key=' + window.btoa(sshKey);
		return $http.post(
			ENVIRONMENTS_URL + environmentId + '/keys',
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function getSshKey(environmentId) {
		return $http.get(ENVIRONMENTS_URL + environmentId + '/keys');
	}

	function removeSshKey(environmentId, sshKey) {
		return $http.delete(ENVIRONMENTS_URL + environmentId + '/keys?key=' + window.btoa(sshKey));
	}


	function getDomain(environmentId) {
		return $http.get(
			ENVIRONMENTS_URL + environmentId + '/domain',
			{withCredentials: true, headers: {'Content-Type': 'application/json'}}
		);
	}


	function setContainerDomainNPort(container, state, port) {
		return $http.put(ENVIRONMENTS_URL + container.environmentId + '/containers/' + container.id + '/domainnport' +
			'?state=' + state + '&port=' + port);
	}

	function getContainerDomainNPort(container, state) {
		return $http.get(
			ENVIRONMENTS_URL + container.environmentId + '/containers/' + container.id + '/domainnport',
			{withCredentials: true, headers: {'Content-Type': 'application/json'}}
		);
	}


	function setContainerName( container, name ) {
		return $http.put( ENVIRONMENTS_URL + container.environmentId + '/containers/' + container.id + '/name' +
			'?name=' + name );
	}

	function createTemplate( container,name, version, isPrivate ) {
	    var URL = ENVIRONMENTS_URL + container.environmentId + '/containers/' + container.id + '/export/' + name + "/" + version + "/" + ( isPrivate == true ? "true" : "false" ) ;
		return $http.post( URL );
	}


	function getDomainStrategies() {
		return $http.get(
			DOMAINS_URL + 'strategies/',
			{withCredentials: true, headers: {'Content-Type': 'application/json'}}
		);
	}


	function setDomain(domain, envId, file) {
		var fd = new FormData();
		fd.append('hostName', domain.name);
		fd.append('strategy', domain.strategy);
		if( !jQuery.isEmptyObject(file) )
		{
			fd.append('file', file);
		}
		else
		{
			fd.append('file', "");
		}

		return $http.post(
			ENVIRONMENTS_URL + envId + '/domains',
			fd,
			{transformRequest: angular.identity, headers: {'Content-Type': undefined}}
		);
	}

	function removeDomain( envId ) {
		return $http.delete( ENVIRONMENTS_URL + envId + '/domains' );
	}


	function getContainersType() {
		return $http.get(CONTAINER_TYPES_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function getContainersTypesInfo() {
		return $http.get(CONTAINER_TYPES_URL + "info", {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}


	function getEnvQuota(containerId) {
		return $http.get(
			CONTAINERS_URL + containerId + '/quota',
			{withCredentials: true, headers: {'Content-Type': 'application/json'}}
		);
	}	

	function updateQuota(containerId, postData) {
		return $http.post(
			CONTAINERS_URL + containerId + '/quota',
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}


	function getStrategies() {
		return $http.get(STRATEGIES_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}


	function getPeers() {
		return $http.get(PEERS_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function getResourceHosts() {
		return $http.get(RH_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function setTags(environmentId, containerId, tags) {
		var postData = 'tags=' + JSON.stringify(tags);
		return $http.post(
			ENVIRONMENTS_URL + environmentId + '/containers/' + containerId + '/tags',
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function removeTag(environmentId, containerId, tag) {
		return $http.delete(ENVIRONMENTS_URL + environmentId + '/containers/' + containerId + '/tags/' + tag);		
	}


	function getShared (environmentId) {
		return $http.get (ENVIRONMENTS_URL + "shared/users/" + environmentId);
	}

	function share (users, environmentId) {
		var postData = "users=" + users;
		return $http.post(
			ENVIRONMENTS_URL + environmentId + "/share",
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function revoke (environmentId) {
		return $http.put (ENVIRONMENTS_URL + environmentId + "/revoke");
	}

	function getInstalledPlugins() {
		return $http.get(SERVER_URL + 'js/plugins.json', {headers: {'Content-Type': 'application/json'}});
	}

	function getNotRegisteredContainers() {
		return $http.get(NOT_REGISTERED_CONTAINERS_URL, {headers: {'Content-Type': 'application/json'}});
	}

	function deleteNotRegisteredContainer(containerId) {
		return $http.delete(NOT_REGISTERED_CONTAINERS_URL + '/' + containerId);
	}
}
