/*global angular*/

( function() {'use strict';

    angular.module('em.app').controller('ContextController', ['$scope', '$routeParams', 'activeItem', 'errorHandler', 'itemsArray', 'itemsRequest', 'tagsArray', 'tasksArray',
    function($scope, $routeParams, activeItem, errorHandler, itemsArray, itemsRequest, tagsArray, tasksArray) {

      $scope.errorHandler = errorHandler;

      if (activeItem.getItem()) {

        $scope.context = activeItem.getItem();
        $scope.subtasks = itemsArray.getTagItems(tasksArray.getTasks(), $scope.context.uuid);

      } else {

        itemsRequest.getItems(function(itemsResponse) {

          itemsArray.setItems(itemsResponse.items);
          tagsArray.setTags(itemsResponse.tags);
          tasksArray.setTasks(itemsResponse.tasks);

          $scope.context = itemsArray.getItemByUuid(tagsArray.getTags(), $routeParams.uuid);

          $scope.subtasks = itemsArray.getTagItems(tasksArray.getTasks(), $scope.context.uuid);

        }, function(error) {
        });
      }

      $scope.setActiveItem = function(item) {
        activeItem.setItem(item);
      };

    }]);
  }());