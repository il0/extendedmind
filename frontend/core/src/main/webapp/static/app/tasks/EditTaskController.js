/*global angular */
'use strict';

function EditTaskController($timeout,$routeParams, $scope, ErrorHandlerService, FilterService, TagsService, tasksArray, tasksRequest, tasksResponse, OwnerService) {

  $scope.errorHandler = ErrorHandlerService;
  $scope.prefix = OwnerService.getPrefix();
  $scope.filterService = FilterService;

  $scope.contexts = TagsService.getTags();
  $scope.tasks = tasksArray.getTasks();

  if ($routeParams.uuid) {
    if (tasksArray.getTaskByUUID($routeParams.uuid)) {
      $scope.task = tasksArray.getTaskByUUID($routeParams.uuid);
    } else if (tasksArray.getProjectByUUID($routeParams.uuid)) {
      $scope.task = tasksArray.getProjectByUUID($routeParams.uuid);
    }
  }

  $scope.editTask = function() {
    tasksResponse.checkDate($scope.task);
    tasksResponse.checkParentTask($scope.task);
    tasksResponse.checkContexts($scope.task);

    tasksRequest.putExistingTask($scope.task).then(function() {
      $scope.task = {};
    });
    window.history.back();
  };

  $scope.cancelEdit = function() {
    window.history.back();
  };
}

EditTaskController.$inject = ['$timeout','$routeParams', '$scope', 'ErrorHandlerService','FilterService', 'TagsService', 'tasksArray', 'tasksRequest', 'tasksResponse', 'OwnerService'];
angular.module('em.app').controller('EditTaskController', EditTaskController);
