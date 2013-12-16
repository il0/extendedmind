/*global angular */
'use strict';

function emLocation($location, $rootScope, $route) {
  // https://github.com/angular/angular.js/issues/1699#issuecomment-22511464
  $location.skipReload = function() {
    var lastRoute, un;
    lastRoute = $route.current;

    un = $rootScope.$on('$locationChangeSuccess', function() {
      $route.current = lastRoute;
      un();
    });
    return $location;
  };
  return $location;
}
angular.module('em.services').factory('emLocation', emLocation);
emLocation.$inject = ['$location', '$rootScope', '$route'];

function userPrefix(userSessionStorage) {
  var prefix = 'my';

  return {
    setCollectivePrefix: function() {
      this.setPrefix('collective' + '/' + userSessionStorage.getActiveUUID());
    },
    setMyPrefix: function() {
      this.setPrefix('my');
    },
    setPrefix: function(name) {
      prefix = name;
    },
    getPrefix: function() {
      return prefix;
    }
  };
}
angular.module('em.services').factory('userPrefix', userPrefix);
userPrefix.$inject = ['userSessionStorage'];

function Enum() {
  var slide = {
    INBOX: 0,
    HOME: 1,
    DATES: 2,
    LISTS: 3,
    PROJECTS: 4,
    NOTES: 2
  };

  return slide;
}
angular.module('em.services').factory('Enum', Enum);
