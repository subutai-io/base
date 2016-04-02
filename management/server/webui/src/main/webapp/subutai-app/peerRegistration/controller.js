'use strict';

angular.module('subutai.peer-registration.controller', [])
	.controller('PeerRegistrationCtrl', PeerRegistrationCtrl);

PeerRegistrationCtrl.$inject = ['$scope', 'peerRegistrationService', 'DTOptionsBuilder', 'DTColumnBuilder', '$resource', '$compile', 'SweetAlert', 'ngDialog', 'cfpLoadingBar'];

function PeerRegistrationCtrl($scope, peerRegistrationService, DTOptionsBuilder, DTColumnBuilder, $resource, $compile, SweetAlert, ngDialog, cfpLoadingBar) {

	var vm = this;
	vm.peerId = null;
	vm.test = 'lolololol';

	cfpLoadingBar.start();
	angular.element(document).ready(function () {
		cfpLoadingBar.complete();
	});

	// functions
	vm.peerFrom = peerFrom;
	vm.rejectPeerRequest = rejectPeerRequest;
	vm.approvePeerRequestForm = approvePeerRequestForm;
	vm.unregisterPeer = unregisterPeer;
	vm.cancelPeerRequest = cancelPeerRequest;
	vm.addPeer = addPeer;	
	vm.approvePeerRequest = approvePeerRequest;	

	vm.dtInstance = {};
	vm.users = {};
	vm.dtOptions = DTOptionsBuilder
		.fromFnPromise(function() {
			return $resource( peerRegistrationService.getPeersUrl() ).query().$promise;
		})
		.withPaginationType('full_numbers')
		.withOption('createdRow', createdRow)
		.withOption('columnDefs', [
			{className: "b-main-table__buttons-group", "targets": 3},
			{className: "b-main-table__peer-status-col", "targets": 2}
		])
		.withOption('stateSave', true);

	vm.dtColumns = [
		DTColumnBuilder.newColumn('registrationData.peerInfo.name').withTitle('Name'),
		DTColumnBuilder.newColumn('registrationData.peerInfo.ip').withTitle(' IP'),
		DTColumnBuilder.newColumn(null).withTitle('Status').renderWith(statusHTML),
		DTColumnBuilder.newColumn(null).withTitle('').notSortable().renderWith(actionButton),
	];

	function createdRow(row, data, dataIndex) {
		$compile(angular.element(row).contents())($scope);
	}

	function statusHTML(data, type, full, meta) {
		var status = data.registrationData.status;
		var statusText = data.registrationData.status;
		
		if( data.registrationData.status == "APPROVED" )
		{
			if(data.isOnline == false)
			{
				status = 'false';
				statusText = 'OFFLINE';
			}
			else
			{
				status = 'true';
				statusText = 'ONLINE';
			}
		}
		return '<div class="b-status-icon b-status-icon_' + status + '" title="' + statusText + '"></div>';
	}

	function actionButton(data, type, full, meta) {
		var result = '';
		if(data.registrationData.status == 'APPROVED') {
			result += '<a href class="b-btn b-btn_red subt_button__peer-unregister" ng-click="peerRegistrationCtrl.unregisterPeer(\'' + data.registrationData.peerInfo.id + '\')">Unregister</a>';
		} else if(data.registrationData.status == 'WAIT') {
			result += '<a href class="b-btn b-btn_blue subt_button__peer-cancel" ng-click="peerRegistrationCtrl.cancelPeerRequest(\'' + data.registrationData.peerInfo.id + '\')">Cancel</a>';
		} else if(data.registrationData.status == 'REQUESTED') {
			result += '<a href class="b-btn b-btn_green subt_button__peer-approve" ng-click="peerRegistrationCtrl.approvePeerRequestForm(\'' + data.registrationData.peerInfo.id + '\')">Approve</a>';
			result += '<a href class="b-btn b-btn_red subt_button__peer-reject" ng-click="peerRegistrationCtrl.rejectPeerRequest(\'' + data.registrationData.peerInfo.id + '\')">Reject</a>';
		}

		return result;
	}

	function rejectPeerRequest(peerId) {
		peerRegistrationService.rejectPeerRequest(peerId).success(function (data) {
			vm.dtInstance.reloadData(null, false);
		}).error(function(error){
			if(error.ERROR !== undefined) {
				SweetAlert.swal("ERROR!", error.ERROR, "error");
			} else {
				SweetAlert.swal("ERROR!", error, "error");
			}
		});
	}

	function peerFrom() {
		ngDialog.open({
			template: 'subutai-app/peerRegistration/partials/peerForm.html',
			scope: $scope
		});
	}

	function approvePeerRequestForm(peerId) {
		vm.peerId = peerId;
		ngDialog.open({
			template: 'subutai-app/peerRegistration/partials/peerApprove.html',
			scope: $scope
		});
	}

	function addPeer(newPeer) {
		var postData = 'ip=' + newPeer.ip + '&key_phrase=' + newPeer.keyphrase;
		ngDialog.closeAll();
		LOADING_SCREEN();
		peerRegistrationService.registerRequest(postData).success(function (data) {
			LOADING_SCREEN('none');
			if(Object.keys(vm.dtInstance).length !== 0) {
				vm.dtInstance.reloadData(null, false);
			}
		}).error(function(error){
			LOADING_SCREEN('none');
			SweetAlert.swal("ERROR!", "Peer request error: " + error, "error");
		});
	}

	function approvePeerRequest(keyPhrase) {
		ngDialog.closeAll();
		LOADING_SCREEN();
		peerRegistrationService.approvePeerRequest(vm.peerId, keyPhrase).success(function (data) {
			LOADING_SCREEN('none');
			if(Object.keys(vm.dtInstance).length !== 0) {
				vm.dtInstance.reloadData(null, false);
			}
		}).error(function(error){
			LOADING_SCREEN('none');
			SweetAlert.swal("ERROR!", "Peer approve error: " + error, "error");
		});
	}

	function unregisterPeer(peerId) {
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
			title: "Are you sure?",
			text: "Your unregister peer request!",
			type: "warning",
			showCancelButton: true,
			confirmButtonColor: "#ff3f3c",
			confirmButtonText: "Unregister",
			cancelButtonText: "Cancel",
			closeOnConfirm: false,
			closeOnCancel: true,
			showLoaderOnConfirm: true
		},
		function (isConfirm) {
			window.onkeydown = previousWindowKeyDown;
			if (isConfirm) {
				peerRegistrationService.unregisterPeerRequest(peerId).success(function (data) {
					SweetAlert.swal("Unregistered!", "Your peer request has been unregistered.", "success");
					vm.dtInstance.reloadData(null, false);
				}).error(function (error) {
					SweetAlert.swal("ERROR!", error, "error");
				});
			}
		});
	}

	function cancelPeerRequest(peerId) {
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
			title: "Are you sure?",
			text: "Your cancel peer request!",
			type: "warning",
			showCancelButton: true,
			confirmButtonColor: "#ff3f3c",
			confirmButtonText: "Cancel request",
			cancelButtonText: "No",
			closeOnConfirm: false,
			closeOnCancel: true,
			showLoaderOnConfirm: true
		},
		function (isConfirm) {
			window.onkeydown = previousWindowKeyDown;
			if (isConfirm) {
				peerRegistrationService.cancelPeerRequest(peerId).success(function (data) {
					SweetAlert.swal("Canceled!", "Your peer request has been canceled.", "success");
					vm.dtInstance.reloadData(null, false);
				}).error(function (error) {
					SweetAlert.swal("ERROR!", error, "error");
				});
			}
		});
	}	

}

