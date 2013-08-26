/*global angular*/

( function() {'use strict';

    angular.module('em.services').factory('userItemsFactory', ['itemFactory', 'itemsFactory',
    function(itemFactory, itemsFactory) {
      return {
        getItems : function() {
          itemFactory.getItems(function(items) {
            itemsFactory.setUserItems(items);
          }, function(error) {
          });
        }
      };
    }]);

    angular.module('em.services').factory('itemFactory', ['httpRequestHandler', 'itemsFactory', 'userSessionStorage',
    function(httpRequestHandler, itemsFactory, userSessionStorage) {
      return {
        getItems : function(success, error) {
          httpRequestHandler.get('/api/' + userSessionStorage.getUserUUID() + '/items', function(userItems) {
            success(userItems);
          }, function(userItems) {
            error(userItems);
          });
        },
        putItem : function(item, success, error) {
          httpRequestHandler.put('/api/' + userSessionStorage.getUserUUID() + '/item', item, function(putItemResponse) {
            itemsFactory.putUserItem(item, putItemResponse);
            success(putItemResponse);
          }, function(putItemResponse) {
            error(putItemResponse);
          });
        },
        editItem : function(item, success, error) {
          httpRequestHandler.put('/api/' + userSessionStorage.getUserUUID() + '/item' + item.uuid, item, function(editItemResponse) {
            success(editItemResponse);
          }, function(editItemResponse) {
            error(editItemResponse);
          });
        },
        deleteItem : function(itemUUID, success, error) {
          httpRequestHandler.put('/api/' + userSessionStorage.getUserUUID() + '/item' + itemUUID, function(deleteItemResponse) {
            success(deleteItemResponse);
          }, function(deleteItemResponse) {
            error(deleteItemResponse);
          });
        }
      };
    }]);

    angular.module('em.services').factory('itemsFactory', [
    function() {
      var itemInArray, userItemsFactory, userNewItems = [];

      itemInArray = function(title) {
        angular.forEach(userNewItems, function(userNewItem) {
          if (userNewItem.title === title) {
            return true;
          }
        });
      };
      return {
        setUserItems : function(items) {
          userItemsFactory = items;
        },
        getUserItems : function() {
          return userItemsFactory;
        },
        getUserNewItems : function() {
          return userNewItems;
        },
        putUserItem : function(item, itemUUID) {
          if (item === undefined || item.title === '') {
            return;
          }
          if (!itemInArray(item.title)) {
            var newItem = [];
            newItem.title = item.title;
            newItem.uuid = itemUUID;
            userNewItems.push(newItem);
          }
        }
      };
    }]);
  }());