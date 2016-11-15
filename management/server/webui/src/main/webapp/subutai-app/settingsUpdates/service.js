"use strict";

angular.module("subutai.settings-updates.service", [])
    .factory("SettingsUpdatesSrv", SettingsUpdatesSrv);

SettingsUpdatesSrv.$inject = ["$http"];

function SettingsUpdatesSrv($http) {
	var BASE_URL = SERVER_URL + "rest/v1/system/";
	var HISTORY_URL = BASE_URL + "updates_history";

    var SettingsUpdatesSrv = {
        getConfig: getConfig,
        update: update,
        getHistory: getHistory,
        getHistoryUrl: function () {
            return HISTORY_URL
        }
    };

    function getConfig() {
        return $http.get(BASE_URL + "management_updates", {
            withCredentials: true,
            headers: {'Content-Type': 'application/json'}
        });
    }

    function getHistory() {
        return $http.get(HISTORY_URL, {
            withCredentials: true,
            headers: {'Content-Type': 'application/json'}
        });
    }

    function update() {
        return $http.post(BASE_URL + "update_management", {
            withCredentials: true,
            headers: {'Content-Type': 'application/x-www-form-urlencoded'}
        });
    }


    return SettingsUpdatesSrv;
}
