'use strict';

angular.module('subutai.login.controller', [])
	.controller('LoginCtrl', LoginCtrl)
	.controller('ChangePassCtrl', ChangePassCtrl)
	.directive('pwCheck', pwCheck);

LoginCtrl.$inject = ['loginSrv', '$http', '$location', '$rootScope', '$state'];
ChangePassCtrl.$inject = ['$scope', 'loginSrv', '$http', '$location', '$rootScope', '$state', 'SweetAlert'];

function ChangePassCtrl( $scope, loginSrv, $http, $location, $rootScope, $state, SweetAlert) {
	var vm = this;

	vm.changePass = changePass;

	function changePass(passObj) {
		if ($scope.changePassForm.$valid) {		
			LOADING_SCREEN();
			loginSrv.changePass(passObj).success(function(data){
				LOADING_SCREEN('none');
				SweetAlert.swal ("Success!", "You have successfully changed password.", "success");
			}).error(function(error){
				LOADING_SCREEN('none');
				SweetAlert.swal ("ERROR!", "Error: " + error, "error");
			});
		}
	}
}

function pwCheck() {
	return {
		require: 'ngModel',
		link: function (scope, elem, attrs, ctrl) {
			var firstPassword = '#' + attrs.pwCheck;
			elem.add(firstPassword).on('keyup', function () {
				scope.$apply(function () {
					ctrl.$setValidity('pwmatch', elem.val() === $(firstPassword).val());
				});
			});
		}
	}
};

function LoginCtrl( loginSrv, $http, $location, $rootScope, $state )
{
	var vm = this;

	vm.name = "";
	vm.pass = "";
	vm.fingerprint = "";
	vm.sptoken = "";
	vm.errorMessage = false;
	vm.activeMode = 'username';

	//functions
	vm.login = login;
	vm.changeMode = changeMode;

	function changeMode(modeStatus) {
		if(modeStatus) {
			vm.activeMode = 'sptoken';
		} else {
			vm.activeMode = 'username';
		}
	}

	function login() {

		var postData = '';
		if(vm.activeMode == 'sptoken') {
			postData =
				'username=' + vm.fingerprint +
				'&password=' + vm.sptoken;
		} else {
			postData =
				'username=' + vm.name +
				'&password=' + vm.pass;
		}

		loginSrv.login( postData ).success(function(data){
			localStorage.setItem('currentUser', vm.name);
			$rootScope.currentUser = vm.name;
			$http.defaults.headers.common['sptoken'] = getCookie('sptoken');
			//$state.go('home');
			window.location = '/';
		}).error(function(error){
			console.log(error);
			vm.errorMessage = error;
		});
	}
}
