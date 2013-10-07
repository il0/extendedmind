/*global angular*/

( function() {'use strict';

    angular.module('em.directives').directive('notes', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/my/notes.html'
      };
    }]);

    angular.module('em.directives').directive('notesList', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/notes/notesList.html',
        transclude : true,
        link : function(scope, element, attrs) {
          var notesFilterAttr = attrs.notesfilter;

          scope.$watch(notesFilterAttr, function(newValue) {
            scope.notesListFilter = newValue;
          });
        }
      };
    }]);

    angular.module('em.directives').directive('noteContent', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/notes/noteContent.html'
      };
    }]);

    angular.module('em.directives').directive('newNote', [
    function() {
      return {
        restrict : 'A',
        templateUrl : 'static/partials/templates/notes/new.html'
      };
    }]);
  }());