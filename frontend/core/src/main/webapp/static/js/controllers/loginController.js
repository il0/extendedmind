"use strict";

emControllers.controller('LoginController', ['$rootScope', '$scope', 'User', 'UserAuthenticate',
function($rootScope, $scope, User, UserAuthenticate) {

  $scope.user = {
    "email" : 'timo@ext.md',
    "password" : 'timo',
    "remember" : User.isUserRemembered()
  };

  $scope.userLogin = function() {
    // Auth.setCredentials($scope.user.email, $scope.user.password);
    //
    // UserAuthenticate.userLogin(function(authenticateResponse) {
    // User.setUser(authenticateResponse.token, $scope.user.remember);
    // Auth.setCredentials('token', authenticateResponse.token.toString());
    // $rootScope.$broadcast('event:loginSuccess');
    // }, function(error) {
    // });

    if (UserAuthenticate.userAuthenticate($scope.user.email, $scope.user.password, $scope.user.remember)) {
      $rootScope.$broadcast('event:loginSuccess');
    }
  };
}]);
