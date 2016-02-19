'use strict';

angular.module('subutai.identity.service', [])
	.factory('identitySrv', identitySrv);


identitySrv.$inject = ['$http'];

function identitySrv($http) {
	var BASE_URL = SERVER_URL + 'rest/ui/identity/';
	var USERS_URL = BASE_URL;
	var ROLES_URL = BASE_URL + 'roles/';
	var TOKENS_URL = BASE_URL + 'users/tokens/';

	var identitySrv = {
		getTokens: getTokens,
		addToken: addToken,
		editToken: editToken,
		deleteToken: deleteToken,
		getUsers: getUsers,
		addUser : addUser,
		deleteUser: deleteUser,
		getRoles: getRoles,
		addRole: addRole,
		deleteRole: deleteRole,
		getTokenTypes: getTokenTypes,
		getPermissionsScops: getPermissionsScops,
		signUp: signUp,
		approve: approve,
		getKey: getKey,
		getCurrentUser: getCurrentUser,

		updatePublicKey: updatePublicKey,
		createIdentityDelegateDocument: createIdentityDelegateDocument,
		getIdentityDelegateDocument: getIdentityDelegateDocument,
		approveIdentityDelegate: approveIdentityDelegate,

		getPublicKeyData: getPublicKeyData,
		checkUserKey: checkUserKey,

		getUsersUrl : function(){ return USERS_URL },
		getRolesUrl : function(){ return ROLES_URL },
		getTokensUrl : function(){ return TOKENS_URL }
	};

	return identitySrv;

	//// Implementation

	
	function getTokens() {
		return $http.get(TOKENS_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function addToken(token) {
		var postData = 'token=' + token.token + '&period=' + token.period + '&userId=' + token.userId;
		return $http.post(
			TOKENS_URL,
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function editToken(token) {
		var postData = 'token=' + token.token 
			+ '&period=' + token.period 
			+ '&userId=' + token.userId 
			+ '&newToken=' + token.newToken;
		return $http.put(
			TOKENS_URL,
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}	

	function deleteToken(token) {
		return $http.delete(TOKENS_URL + token);
	}

	function getUsers() {
		return $http.get(USERS_URL + "all", {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function addUser(postData) {
		return $http.post(
			USERS_URL,
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function deleteUser(userId) {
		return $http.delete(USERS_URL + userId);
	}

	function getRoles() {
		return $http.get(ROLES_URL, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function addRole(postData) {
		return $http.post(
			ROLES_URL,
			postData, 
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function deleteRole(roleId) {
		return $http.delete(ROLES_URL + roleId);
	}

	function getTokenTypes() {
		return $http.get(USERS_URL + 'tokens/types', {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function getPermissionsScops() {
		return $http.get(USERS_URL + 'permissions/scopes', {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function getCurrentUser() {
		return $http.get (SERVER_URL + 'rest/ui/identity/user');
	}

	function signUp (username, fullName, password, email, publicKey) {
		var postData = "username=" + username + "&full_name=" + fullName + "&password=" + password + "&email=" + email + "&public_key=" + publicKey;
		return $http.post(
			USERS_URL + "signup",
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function updatePublicKey (publicKey) {
		var postData = "publicKey=" + publicKey;
		return $http.post(
			USERS_URL + "set-public-key",
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function createIdentityDelegateDocument() {
		return $http.post(
			USERS_URL + "delegate-identity",
			"",
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function getIdentityDelegateDocument() {
		return $http.get( USERS_URL + "delegate-identity" );
	}

	function approveIdentityDelegate(signedDocument) {
		var postData = "signedDocument="+signedDocument;
		return $http.post(
			USERS_URL + "approve-delegate",
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function approve (username, roles) {
		var postData = "username=" + username + "&roles=" + roles;
		console.log (postData);
		return $http.post(
			USERS_URL + "approve",
			postData,
			{withCredentials: true, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
		);
	}

	function getKey (id) {
		return $http.get (SERVER_URL + "rest/v1/security/keyman/getpublickey", {params: {hostid: id}});
	}

	function getPublicKeyData(userId) {
		return $http.get(USERS_URL + 'key-data/' + userId, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}

	function checkUserKey(userId) {
		return $http.get(USERS_URL + 'check-user-key/' + userId, {withCredentials: true, headers: {'Content-Type': 'application/json'}});
	}
}
