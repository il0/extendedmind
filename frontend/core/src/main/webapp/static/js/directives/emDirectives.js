/*global $, angular*/

( function() {'use strict';

    angular.module('em.directives').directive('appVersion', ['version',
    function(version) {
      return function(scope, element, attrs) {
        return element.text(version);
      };
    }]);

    angular.module('em.directives').directive('errorAlertBar', ['$parse',
    function($parse) {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/errorMessage.html',
        link : function(scope, elem, attrs) {
          var alertMessageAttr = attrs.alertmessage;
          scope.errorMessage = null;

          scope.$watch(alertMessageAttr, function(newValue) {
            scope.errorMessage = newValue;
          });

          scope.hideAlert = function() {
            scope.errorMessage = null;
            $parse(alertMessageAttr).assign(scope, null);
          };
        }
      };
    }]);

    angular.module('em.directives').directive('urlList', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/urlList.html'
      };
    }]);

    angular.module('em.directives').directive('newTag', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/newTag.html',
        link : function(scope, element, attrs) {
          scope.showEditNewTag = false;

          scope.editNewTag = function editNewTag() {
            scope.showEditNewTag = !scope.showEditNewTag;
          };
        }
      };
    }]);

    angular.module('em.directives').directive('contextsList', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/contextsList.html',
        transclude : true
      };
    }]);

    angular.module('em.directives').directive('my', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/my.html'
      };
    }]);

    angular.module('em.directives').directive('projectsList', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/projectsList.html',
        transclude : true
      };
    }]);
  }());
