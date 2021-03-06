/*jslint eqeq: true, white: true */
'use strict';

angular.module('em.services').factory('TagsService', ['itemsArray',
  function(itemsArray) {
    var tags;
    tags = [];

    return {
      setTags : function(tagsResponse) {

        itemsArray.clearArray(tags);

        if (tagsResponse != null) {
          var i = 0;

          while (tagsResponse[i]) {
            this.setTag(tagsResponse[i]);
            i++;
          }
        }
      },
      setTag : function(tag) {
        if (!itemsArray.itemInArray(tags, tag.uuid)) {
          tags.push(tag);
        }
      },
      getTags : function() {
        return tags;
      },
      putNewTag : function(tag) {
        if (!itemsArray.itemInArray(tags, tag.title)) {
          tags.push(tag);
        }
      },
      getTagByUUID : function(uuid) {
        return itemsArray.getItemByUUID(tags, uuid);
      }
    };
  }]);
