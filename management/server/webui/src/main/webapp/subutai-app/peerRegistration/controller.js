'use strict';

angular.module('subutai.peer-registration.controller', [])
	.controller('PeerRegistrationCtrl', PeerRegistrationCtrl)
	.controller('PeerRegistrationPopupCtrl', PeerRegistrationPopupCtrl);

PeerRegistrationCtrl.$inject = ['$scope', 'peerRegistrationService', 'DTOptionsBuilder', 'DTColumnBuilder', '$resource', '$compile', 'SweetAlert', 'ngDialog', 'cfpLoadingBar'];
PeerRegistrationPopupCtrl.$inject = ['$scope', 'peerRegistrationService', 'ngDialog', 'SweetAlert'];

function PeerRegistrationCtrl($scope, peerRegistrationService, DTOptionsBuilder, DTColumnBuilder, $resource, $compile, SweetAlert, ngDialog, cfpLoadingBar) {

	var vm = this;

	cfpLoadingBar.start();
	angular.element(document).ready(function () {
		cfpLoadingBar.complete();
	});

	// functions
	vm.peerFrom = peerFrom;
	vm.rejectPeerRequest = rejectPeerRequest;
	vm.approvePeerRequest = approvePeerRequest;
	vm.unregisterPeer = unregisterPeer;
	vm.cancelPeerRequest = cancelPeerRequest;

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
		DTColumnBuilder.newColumn('peerInfo.name').withTitle('Name'),
		DTColumnBuilder.newColumn('peerInfo.ip').withTitle(' IP'),
		DTColumnBuilder.newColumn(null).withTitle('Status').renderWith(statusHTML),
		DTColumnBuilder.newColumn(null).withTitle('').notSortable().renderWith(actionButton),
	];

	function createdRow(row, data, dataIndex) {
		$compile(angular.element(row).contents())($scope);
	}

	function statusHTML(data, type, full, meta) {
		vm.users[data.id] = data;
		return '<div class="b-status-icon b-status-icon_' + data.status + '" title="' + data.status + '"></div>';
	}

	function actionButton(data, type, full, meta) {
		var result = '';
		if(data.status == 'APPROVED') {
			result += '<a href class="b-btn b-btn_red subt_button__peer-unregister" ng-click="peerRegistrationCtrl.unregisterPeer(\'' + data.peerInfo.id + '\')">Unregister</a>';
		} else if(data.status == 'WAIT') {
			result += '<a href class="b-btn b-btn_blue subt_button__peer-cancel" ng-click="peerRegistrationCtrl.cancelPeerRequest(\'' + data.peerInfo.id + '\')">Cancel</a>';
		} else if(data.status == 'REQUESTED') {
			result += '<a href class="b-btn b-btn_green subt_button__peer-approve" ng-click="peerRegistrationCtrl.approvePeerRequest(\'' + data.peerInfo.id + '\')">Approve</a>';
			result += '<a href class="b-btn b-btn_red subt_button__peer-reject" ng-click="peerRegistrationCtrl.rejectPeerRequest(\'' + data.peerInfo.id + '\')">Reject</a>';
		}

		return result;
	}

	function peerFrom() {
		ngDialog.open({
			template: 'subutai-app/peerRegistration/partials/peerForm.html',
			controller: 'PeerRegistrationPopupCtrl',
			controllerAs: 'peerRegistrationPopupCtrl',
			preCloseCallback: function(value) {
				vm.dtInstance.reloadData(null, false);
			}
		});
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

	function approvePeerRequest(peerId) {
		ngDialog.open({
			template: 'subutai-app/peerRegistration/partials/peerApprove.html',
			controller: 'PeerRegistrationPopupCtrl',
			controllerAs: 'peerRegistrationPopupCtrl',
			data: {"peerId": peerId},
			preCloseCallback: function(value) {
				vm.dtInstance.reloadData(null, false);
			}
		});
	}

	function unregisterPeer(peerId) {
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
			if (isConfirm) {
				peerRegistrationService.unregisterPeerRequest(peerId).success(function (data) {
					SweetAlert.swal("Unregistered!", "Your peer request has been unregistered.", "success");
					vm.dtInstance.reloadData(null, false);
				}).error(function (data) {
					SweetAlert.swal("ERROR!", data.ERROR, "error");
				});
			}
		});
	}

	function cancelPeerRequest(peerId) {
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
			if (isConfirm) {
				peerRegistrationService.cancelPeerRequest(peerId).success(function (data) {
					SweetAlert.swal("Canceled!", "Your peer request has been canceled.", "success");
					vm.dtInstance.reloadData(null, false);
				}).error(function (data) {
					SweetAlert.swal("ERROR!", data.ERROR, "error");
				});
			}
		});
	}	

}

function PeerRegistrationPopupCtrl($scope, peerRegistrationService, ngDialog, SweetAlert) {

	var vm = this;
	vm.peerId = null;

	if($scope.ngDialogData !== undefined) {
		vm.peerId = $scope.ngDialogData.peerId;
	}	

	vm.addPeer = addPeer;	
	vm.approvePeerRequest = approvePeerRequest;	

	function addPeer(newPeer) {
		var postData = 'ip=' + newPeer.ip + '&key_phrase=' + newPeer.keyphrase;
		peerRegistrationService.registerRequest(postData).success(function (data) {
			ngDialog.closeAll();
		}).error(function(error){
			SweetAlert.swal("ERROR!", "Peer request error: " + error, "error");
		});
	}

	function approvePeerRequest(keyPhrase) {
		peerRegistrationService.approvePeerRequest(vm.peerId, keyPhrase).success(function (data) {
			ngDialog.closeAll();
		}).error(function(error){
			SweetAlert.swal("ERROR!", "Peer approve error: " + error, "error");
		});
	}

}

