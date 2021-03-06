'use strict';

function errorAlertBar($parse) {
  return {
    restrict: 'A',
    templateUrl: 'static/app/base/errorMessage.html',
    link: function(scope, elem, attrs) {
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
}
angular.module('em.directives').directive('errorAlertBar', errorAlertBar);
errorAlertBar.$inject = ['$parse'];

function emPassword() {
  // http://stackoverflow.com/a/18014975
  return {
    restrict: 'A',
    require: '?ngModel',
    link: function(scope, elem, attrs, ngModel) {
      if(!ngModel) {
        return;
      }

      var validate = function() {
        var val1 = ngModel.$viewValue;
        var val2 = attrs.equals;

        ngModel.$setValidity('equals', val1 === val2 && val1.length >= 8);
      };

      // watch own value and re-validate on change
      scope.$watch(attrs.ngModel, function() {
        validate();
      });

      // observe the other value and re-validate on change
      attrs.$observe('equals', function() {
        validate();
      });
    }
  };
}
angular.module('em.directives').directive('emPassword', emPassword);

function scrollTo($location, $anchorScroll) {
  return function(scope, element, attrs) {
    element.bind('click', function(event) {
      event.stopPropagation();
      scope.$on('$locationChangeStart', function(ev) {
        ev.preventDefault();
      });
      var location = attrs.scrollTo;
      $location.hash(location);
      $anchorScroll();
    });
  };
}
angular.module('em.directives').directive('scrollTo', scrollTo);
