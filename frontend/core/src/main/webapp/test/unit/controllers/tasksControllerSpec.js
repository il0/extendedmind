"use strict";

describe('em.controllers', function() {
  beforeEach(module('em.controllers'));

  describe('TasksController', function() {
    var $controller, $scope;
    var items;

    beforeEach(inject(function(_$controller_, _$rootScope_) {
      items = getJSONFixture('itemsResponse.json');

      $scope = _$rootScope_.$new();
      $controller = _$controller_('TasksController', {
        $scope : $scope
      });
    }));

    it('should return logged user\'s tasks', function() {
      expect($scope.items).toBe(undefined);
      $scope.items = items;
      expect($scope.items.length).toBe(3);
    });
  });
});
