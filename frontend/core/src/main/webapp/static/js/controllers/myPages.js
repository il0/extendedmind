/*global angular*/
/*jslint plusplus: true*/

( function() {'use strict';

    angular.module('em.app').controller('MyPagesController', ['$location', '$rootScope', '$scope', 'Enum', 'errorHandler', 'itemsArray', 'itemsRequest', 'location', 'notesArray', 'slideIndex', 'tagsArray', 'tasksArray',
    function($location, $rootScope, $scope, Enum, errorHandler, itemsArray, itemsRequest, location, notesArray, slideIndex, tagsArray, tasksArray) {

      $scope.slide = slideIndex;

      $scope.errorHandler = errorHandler;

      itemsRequest.getItems(function(itemsResponse) {

        itemsArray.setItems(itemsResponse.items);
        notesArray.setNotes(itemsResponse.notes);
        tagsArray.setTags(itemsResponse.tags);
        tasksArray.setTasks(itemsResponse.tasks);

      }, function(error) {
      });

      $rootScope.$on('event:slideIndexChanged', function() {
        switch($scope.slide) {
          case Enum.my.notes:
            if ($location.path() !== '/my/notes') {
              location.skipReload().path('/my/notes');
            }
            break;
          case Enum.my.my:
            if ($location.path() !== '/my') {
              location.skipReload().path('/my');
            }
            break;
          case Enum.my.tasks:
            if ($location.path() !== '/my/tasks') {
              location.skipReload().path('/my/tasks');
            }
            break;
          default:
            break;
        }
      });

    }]);
  }());