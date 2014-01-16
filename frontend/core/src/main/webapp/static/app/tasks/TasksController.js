/*global angular */
'use strict';

function TasksController($location, $scope, OwnerService, activeItem, tasksRequest, tasksResponse, tasksArray) {

  $scope.editTaskTitle = function(task) {
    tasksRequest.putExistingTask(task);
  }

  $scope.editTask = function(task) {
    $location.path(OwnerService.getPrefix() + '/tasks/edit/' + task.uuid);
  }

  $scope.taskChecked = function(task) {
    if (task.completed) {
      tasksArray.deleteTaskProperty(task, 'completed');
      tasksRequest.uncompleteTask(task).then(function(uncompleteTaskResponse) {
        tasksResponse.putTaskContent(task, uncompleteTaskResponse);
      });
    } else {
      tasksRequest.completeTask(task).then(function(completeTaskResponse) {
        tasksResponse.putTaskContent(task, completeTaskResponse);
      });
    }
  };

  $scope.taskToProject = function(task) {
    $location.path(OwnerService.getPrefix() + '/tasks/new');
    activeItem.setItem(task);
    task.project = true;
    tasksRequest.putExistingTask(task);
  };

  $scope.deleteTask = function(task) {
    tasksArray.removeTask(task);
    tasksRequest.deleteTask(task).then(function(deleteTaskResponse) {
      tasksResponse.putTaskContent(task, deleteTaskResponse);
    });
  };

  $scope.addSubtask = function(subtask) {
    $scope.subtask = {};

    // Quick hack to save the possible due date and project to prevent 
    // bug with adding a second subtask in view
    // TODO: Refactor task lists handling.
    if (subtask.due){
      $scope.subtask.due = subtask.due;
    }
    if (subtask.relationships && subtask.relationships.parentTask){
      $scope.subtask.relationships = {
        parentTask: subtask.relationships.parentTask
      };
    }

    tasksRequest.putTask(subtask).then(function(putTaskResponse) {
      tasksResponse.putTaskContent(subtask, putTaskResponse);
      tasksArray.putNewTask(subtask);
    });
  };

  $scope.getSubtaskButtonClass = function(task) {
    if (!task.project && !task.relationships.parentTask){
      return "left-of-two";
    }
  }

  $scope.getDeleteButtonClass = function(task) {
    if (!task.project){
      if (!task.relationships.parentTask){
        return "right-of-two";
      }else{
        return "wide-button"
      }
    }
  }

}

TasksController.$inject = ['$location', '$scope', 'OwnerService', 'activeItem', 'tasksRequest', 'tasksResponse', 'tasksArray'];
angular.module('em.app').controller('TasksController', TasksController);
