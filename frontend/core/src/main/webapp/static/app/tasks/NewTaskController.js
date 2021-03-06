/*global angular */
'use strict';

function NewTaskController($routeParams, $scope, activeItem, ErrorHandlerService, FilterService, TagsService, tasksArray, tasksRequest, tasksResponse, OwnerService) {

  $scope.errorHandler = ErrorHandlerService;
  $scope.prefix = OwnerService.getPrefix();
  $scope.filterService = FilterService;

  $scope.contexts = TagsService.getTags();
  $scope.tasks = tasksArray.getTasks();

  function newEmptyTask() {
    $scope.task = {
      relationships: {
        parentTask: '',
        tags: []
      }
    };
  }

  newEmptyTask();

  if (activeItem.getItem()) {
    $scope.parentTask = activeItem.getItem();
    $scope.task.relationships.parentTask = $scope.parentTask.uuid;
  }

  $scope.editTask = function() {
    cleanContext($scope.task);
    tasksResponse.checkDate($scope.task);
    tasksResponse.checkParentTask($scope.task);
    tasksResponse.checkContexts($scope.task);

    tasksArray.putNewTask($scope.task);

    tasksRequest.putTask($scope.task).then(function(putTaskResponse) {
      tasksResponse.putTaskContent($scope.task, putTaskResponse);
      newEmptyTask();
    });
    
    activeItem.setItem();
    window.history.back();
  };

  $scope.cancelEdit = function() {
    if (activeItem.getItem()) {
      tasksArray.deleteTaskProperty(activeItem.getItem(), 'project');
      activeItem.setItem();
    }
    window.history.back();
  };

  var cleanContext = function(task) {
    if (task.relationships && task.relationships.context){
      task.relationships.tags = [task.relationships.context];
      delete task.relationships.context;
    }
  }
}

NewTaskController.$inject = ['$routeParams', '$scope', 'activeItem', 'ErrorHandlerService','FilterService', 'TagsService', 'tasksArray', 'tasksRequest', 'tasksResponse', 'OwnerService'];
angular.module('em.app').controller('NewTaskController', NewTaskController);
