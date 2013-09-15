
$.fn.formToJSON = function() {
  var objectGraph = {};

  function add(objectGraph, name, value) {
    if (name.length == 1) {
      //if the array is now one element long, we're done
      objectGraph[name[0]] = value;
    } else {
      //else we've still got more than a single element of depth
      if (objectGraph[name[0]] == null) {
        //create the node if it doesn't yet exist
        objectGraph[name[0]] = {};
      }
      //recurse, chopping off the first array element
      add(objectGraph[name[0]], name.slice(1), value);
    }
  };

  //loop through all of the input/textarea elements of the form
  $(this).find('*').each(function() {
    //ignore the submit button
    if ($(this).is("input") && $(this).attr('name') != 'submit') {
      //split the dot notated names into arrays and pass along with the value
      add(objectGraph, $(this).attr('name').split('.'), $(this).val());
    }
  });
  return JSON.stringify(objectGraph);
};

$.ajaxSetup({
  contentType : "application/json; charset=utf-8",
  dataType : "json"
});

$(document).ready(function() {
  $('#input').click(function() {
    var send = $("#emailForm").formToJSON();
    $.ajax({
      url : "/api/invite/request",
      type : "POST",
      data : send,
      error : function(xhr, errors) {
        $('#result').html('<div class="alert">' + getErrorMessage(xhr.responseText, xhr.status) + '</div>');
      },
      success : function(data) {
        $('#result').html('<div class="alert">Thank you. You are now on the beta waiting list.</div>');
      }
    });
    return false;
  });
  setQueueNumber(QueryString.uuid);
});

var getErrorMessage = function(responseText, status) {
  return ((responseText.length > 17) && (responseText.length < 100) ?
             responseText.slice(0,-15) : 'An unrecognized error occured: ' + status)
}

var setQueueNumber = function(uuid) {
  $.getJSON('/api/invite/request/' + uuid)
    .done(function( json ) {
      $('#number').html('<h1>' + json.queueNumber + '</h1>');
    })
    .fail(function( xhr, textStatus, error ) {
      $('#number').html('<div class="alert">' + getErrorMessage(xhr.responseText, xhr.status) + '</div>');
    });
}

var QueryString = function () {
  // This function is anonymous, is executed immediately and
  // the return value is assigned to QueryString!
  var query_string = {};
  var query = window.location.search.substring(1);
  var vars = query.split("&");
  for (var i=0;i<vars.length;i++) {
    var pair = vars[i].split("=");
    	// If first entry with this name
    if (typeof query_string[pair[0]] === "undefined") {
      query_string[pair[0]] = pair[1];
    	// If second entry with this name
    } else if (typeof query_string[pair[0]] === "string") {
      var arr = [ query_string[pair[0]], pair[1] ];
      query_string[pair[0]] = arr;
    	// If third or later entry with this name
    } else {
      query_string[pair[0]].push(pair[1]);
    }
  }
    return query_string;
} ();