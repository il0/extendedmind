'use strict';

function HomeController($scope, $location, itemsRequest, UserSessionService, AuthenticationService) {

  $scope.omniBarActive = false;
  $scope.menuActive = false;
  $scope.collectives = UserSessionService.getCollectives();

  $scope.toggleMenu = function toggleMenu() {
    $scope.menuActive = !$scope.menuActive;
  };

  $scope.setCollectiveActive = function(uuid) {
    AuthenticationService.switchActiveUUID(uuid);
    $location.path('/collective/' + uuid + '/tasks');
    $scope.menuActive = false;
  };
  
  $scope.setMyActive = function() {
    AuthenticationService.switchActiveUUID(UserSessionService.getUserUUID());
    $location.path('/my/tasks');
    $scope.menuActive = false;
  };

  $scope.logout = function() {
    AuthenticationService.logout().then(function() {
      $location.path('/login');
    });
  };

  $scope.useCollectives = function () {
    if ($scope.collectives && Object.keys($scope.collectives).length > 1) {
      return true;
    }
  };

  $scope.addNewItem = function(omnibarText) {
    if (omnibarText){
      // FIXME: refactor jQuery into directive!
      // $('#omniItem').focus();
      $scope.omnibarText = {};
      $scope.focusOmnibar = true;
      itemsRequest.putItem(omnibarText);
    }else{
      $location.path($scope.prefix + '/items/new');
    }
  };

  $scope.omniBarFocus = function(focus) {
    if (focus) {
      $scope.omniBarActive = true;
    } else {
      if ($scope.omnibarText == null || $scope.omnibarText.title == null || $scope.omnibarText.title.length === 0) {
        $scope.omniBarActive = false;
      }
    }
  };
}

angular.module('em.app').controller('HomeController', HomeController);
HomeController.$inject = ['$scope', '$location','itemsRequest', 'UserSessionService', 'AuthenticationService'];
