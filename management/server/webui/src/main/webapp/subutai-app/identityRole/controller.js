'use strict';

angular.module('subutai.identity-role.controller', [])
	.controller('IdentityRoleCtrl', IdentityRoleCtrl)
	.controller('IdentityRoleFormCtrl', IdentityRoleFormCtrl);

IdentityRoleCtrl.$inject = ['$scope', 'identitySrv', 'DTOptionsBuilder', 'DTColumnBuilder', '$resource', '$compile', 'SweetAlert', 'ngDialog', 'cfpLoadingBar'];
IdentityRoleFormCtrl.$inject = ['$scope', 'identitySrv', 'SweetAlert', 'ngDialog'];

function IdentityRoleCtrl($scope, identitySrv, DTOptionsBuilder, DTColumnBuilder, $resource, $compile, SweetAlert, ngDialog, cfpLoadingBar) {

	var vm = this;

	cfpLoadingBar.start();
	angular.element(document).ready(function () {
		cfpLoadingBar.complete();
	});

	vm.permissions2Add = angular.copy(permissionsDefault);
	vm.role2Add = {}

	vm.rolesTypes = {
		1: "Systemt",
		2: "Regular",
	};

	//functions
	vm.roleForm = roleForm;
	vm.deleteRole = deleteRole;
	vm.removePermissionFromRole = removePermissionFromRole;

	function roleForm(role) {
		if(role === undefined || role === null) role = false;

		ngDialog.open({
			template: 'subutai-app/identityRole/partials/roleForm.html',
			controller: 'IdentityRoleFormCtrl',
			controllerAs: 'identityRoleFormCtrl',
			data: role,
			preCloseCallback: function(value) {
				if(Object.keys(vm.dtInstance).length !== 0) {
					vm.dtInstance.reloadData(null, false);
				}
			}
		});
	}

	vm.dtInstance = {};
	vm.roles = {};
	vm.dtOptions = DTOptionsBuilder
		.fromFnPromise(function() {
			return $resource( identitySrv.getRolesUrl() ).query().$promise;
		})
		.withPaginationType('full_numbers')
		.withOption('stateSave', true)
		.withOption('order', [[ 1, "asc" ]])
		.withOption('createdRow', createdRow);

	vm.dtColumns = [
		DTColumnBuilder.newColumn(null).withTitle('').notSortable().renderWith(actionEdit),
		DTColumnBuilder.newColumn('name').withTitle('Roles'),
		DTColumnBuilder.newColumn('type').withTitle('Role type').renderWith(getRoleType),
		DTColumnBuilder.newColumn(null).withTitle('Role permissions').renderWith(permissionsTags),
		DTColumnBuilder.newColumn(null).withTitle('').notSortable().renderWith(actionDelete)
	];

	function createdRow(row, data, dataIndex) {
		$compile(angular.element(row).contents())($scope);
	}

	function getRoleType(type) {
		return vm.rolesTypes[type];
	}

	function actionEdit(data, type, full, meta) {
		vm.roles[data.id] = data;
		return '<a href class="b-icon b-icon_edit" ng-click="identityRoleCtrl.roleForm(identityRoleCtrl.roles[' + data.id + '])"></a>';
	}	

	function permissionsTags(data, type, full, meta) {
		var permissionsHTML = '';
		for(var i = 0; i < data.permissions.length; i++) {
			var perrmission = $.grep(permissionsDefault, function(element, index) {
				return (element.object === data.permissions[i].object);
			})[0];
			permissionsHTML += '<span class="b-tags b-tags_grey">' 
				+ perrmission.name 
				+ ' <a href ng-click="identityRoleCtrl.removePermissionFromRole(identityRoleCtrl.roles[' + data.id + '], ' + i + ')"><i class="fa fa-times"></i></a>' 
			+ '</span>';
		}
		return permissionsHTML;
	}

	function actionDelete(data, type, full, meta) {
		return '<a href class="b-icon b-icon_remove" ng-click="identityRoleCtrl.deleteRole(' + data.id + ')"></a>';
	}

	function removePermissionFromRole(role, permissionKey) {
		var perrmission = $.grep(permissionsDefault, function(element, index) {
			return (element.object === role.permissions[permissionKey].object);
		})[0];
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
			title: "Are you sure?",
			text: 'Remove "' + perrmission.name + '" permission from role!',
			type: "warning",
			showCancelButton: true,
			confirmButtonColor: "#ff3f3c",
			confirmButtonText: "Remove",
			cancelButtonText: "Cancel",
			closeOnConfirm: false,
			closeOnCancel: true,
			showLoaderOnConfirm: true
		},
		function (isConfirm) {
			window.onkeydown = previousWindowKeyDown;
			if (isConfirm) {

				role.permissions.splice(permissionKey, 1);

				var postData = 'rolename=' + role.name;
				if(role.id !== undefined && role.id > 0) {
					postData += '&role_id=' + role.id;
				}
				postData += '&permission=' + JSON.stringify(role.permissions);

				identitySrv.addRole(postData).success(function (data) {
					SweetAlert.swal("Removed!", "Permission has been removed.", "success");
					vm.dtInstance.reloadData(null, false);
				}).error(function (data) {
					SweetAlert.swal("ERROR!", "Role permission is safe :). Error: " + data, "error");
				});
			}
		});
	}

	function deleteRole(roleId) {
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
			title: "Are you sure?",
			text: "You will not be able to recover this Role!",
			type: "warning",
			showCancelButton: true,
			confirmButtonColor: "#ff3f3c",
			confirmButtonText: "Delete",
			cancelButtonText: "Cancel",
			closeOnConfirm: false,
			closeOnCancel: true,
			showLoaderOnConfirm: true
		},
		function (isConfirm) {
			window.onkeydown = previousWindowKeyDown;
			if (isConfirm) {
				identitySrv.deleteRole(roleId).success(function (data) {
					SweetAlert.swal("Deleted!", "Role has been deleted.", "success");
					vm.dtInstance.reloadData(null, false);
				});
			}
		});
	}

};

function IdentityRoleFormCtrl($scope, identitySrv, SweetAlert, ngDialog) {

	var vm = this;

	vm.permissions2Add = angular.copy(permissionsDefault);
	vm.role2Add = {}
	vm.editRole = false;

	if($scope.ngDialogData !== undefined) {
		//vm.role2Add = $scope.ngDialogData;
		vm.editRole = true;

		var role = $scope.ngDialogData;
		for(var i = 0; i < role.permissions.length; i++) {
			for(var j = 0; j < vm.permissions2Add.length; j++) {
				if(vm.permissions2Add[j].object == role.permissions[i].object) {
					vm.permissions2Add[j].selected = true;
					vm.permissions2Add[j].read = role.permissions[i].read;
					vm.permissions2Add[j].write = role.permissions[i].write;
					vm.permissions2Add[j].update = role.permissions[i].update;
					vm.permissions2Add[j].delete = role.permissions[i].delete;
					break;
				}
			}
		}
		vm.role2Add = role;
	}

	//functions
	vm.addPermission2Stack = addPermission2Stack;
	vm.addRole = addRole;

	function addPermission2Stack(permission) {
		permission.selected = !permission.selected;
	}

	function removePermissionFromStack(key) {
		vm.permissions2Add.splice(key, 1);
	}

	function addRole() {
		if(vm.role2Add.name === undefined || vm.role2Add.name.length < 1) return;

		var postData = 'rolename=' + vm.role2Add.name;
		if(vm.role2Add.id !== undefined && vm.role2Add.id > 0) {
			postData += '&role_id=' + vm.role2Add.id;
		}

		var permissionsArray = [];
		for(var i = 0; i < vm.permissions2Add.length; i++) {
			if(vm.permissions2Add[i].selected === true) {
				permissionsArray.push(vm.permissions2Add[i]);
			}
		}

		if(permissionsArray.length > 0) {
			postData += '&permission=' + JSON.stringify(permissionsArray);
		}

		identitySrv.addRole(postData).success(function (data) {
			ngDialog.closeAll();
		});
	}
	
}

