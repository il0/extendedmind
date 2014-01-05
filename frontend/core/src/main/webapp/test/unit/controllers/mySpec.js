/*global module, beforeEach, inject, describe, afterEach, it, expect */
'use strict';

beforeEach(module('em.app'));

describe('HomeController', function() {
  beforeEach(module('em.services', 'em.mockHelpers'));

  var $controller, $httpBackend, $scope, mockHttpBackendResponse, slide, userItems;

  beforeEach(inject(function(_$controller_, _$httpBackend_, _$rootScope_, _mockHttpBackendResponse_) {
    $httpBackend = _$httpBackend_;
    mockHttpBackendResponse = _mockHttpBackendResponse_;
    userItems = mockHttpBackendResponse.getItemsResponse();

    $scope = _$rootScope_.$new();
    $controller = _$controller_('HomeController', {
      $scope : $scope,
      slide : slide
    });
  }));

  afterEach(function() {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
    mockHttpBackendResponse.clearSessionStorage();
  });

  it('should return logged user\'s items', inject(function() {
    expect($scope.items).toEqual([]);
    $scope.items = userItems.items;
    expect($scope.items.length).toBe(2);
  }));

  it('should return logged user\'s notes', inject(function() {
    $scope.notes = userItems.notes;
    expect($scope.notes.length).toBe(3);
  }));

  it('should return logged user\'s tasks', inject(function() {
    $scope.tasks = userItems.tasks;
    expect($scope.tasks.length).toBe(8);
  }));

  it('should add new item', inject(function(_HttpBasicAuthenticationService_, _itemsArray_, _SessionStorageService_) {
    var HttpBasicAuthenticationService, itemsArray, SessionStorageService;

    HttpBasicAuthenticationService = _HttpBasicAuthenticationService_;

    itemsArray = _itemsArray_;
    SessionStorageService = _SessionStorageService_;

    $scope.newItem = {
      title : 'Buy more milk'
    };
  }));
});
