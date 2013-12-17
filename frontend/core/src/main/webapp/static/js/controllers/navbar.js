/*global angular */
'use strict';

function NavbarController($location, $scope, auth, authenticateRequest, emSwiper, userPrefix, userSessionStorage) {
  $scope.user = userSessionStorage.getUserUUID();
  $scope.collectives = userSessionStorage.getCollectives();
  $scope.prefix = userPrefix.getPrefix();

  $scope.logout = function() {
    authenticateRequest.logout().then(function() {
      $location.path('/login');
    });
  };

  $scope.setActiveUuid = function(uuid, collective) {
    auth.switchActiveUUID(uuid);
    if (collective) {
      $location.path('/collective/' + uuid);
    }
  };

  $scope.addNew = function() {
    $location.path($scope.prefix + '/tasks/new');
  };

  $scope.gotoInbox = function() {
    emSwiper.gotoInbox();
  };

  $scope.gotoHome = function() {
    emSwiper.gotoHome();
  };

  $scope.gotoTasks = function() {
    emSwiper.gotoTasks();
  };

  $scope.useCollectives = function () {
    if (userSessionStorage.getCollectives() && Object.keys(userSessionStorage.getCollectives()).length > 1) {
      return true;
    }
  };
}

NavbarController.$inject = ['$location', '$scope', 'auth', 'authenticateRequest', 'emSwiper', 'userPrefix', 'userSessionStorage'];
angular.module('em.app').controller('NavbarController', NavbarController);
