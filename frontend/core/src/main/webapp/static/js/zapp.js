// Generated by CoffeeScript 1.4.0
(function() {
  "use strict";

  angular.module('em', ['em.filters', 'em.services', 'em.directives']).config(
    function($routeProvider, $locationProvider) {
      $locationProvider.html5Mode(true);
      $routeProvider.when('/', {
        templateUrl: '/static/partials/home.html',
        controller: ContentCtrl
      });
      $routeProvider.when('/about', {
        templateUrl: '/static/partials/about.html',
        controller: AboutCtrl
      });
      $routeProvider.when('/people', {
        templateUrl: '/static/partials/people.html',
        controller: PeopleCtrl
      });
      $routeProvider.when('/people/:personId', {
        templateUrl: '/static/partials/person.html',
        controller: PersonCtrl
      });
      $routeProvider.when('/categories', {
        templateUrl: '/static/partials/categories.html',
        controller: CategoriesCtrl
      });
      $routeProvider.when('/search', {
        templateUrl: '/static/partials/search.html',
        controller: SearchCtrl
      });
      $routeProvider.when('/search/:searchString', {
        templateUrl: '/static/partials/search.html',
        controller: SearchCtrl
      });
      $routeProvider.when('/login', {
        templateUrl: '/static/partials/login.html',
        controller: LoginCtrl
      });
      $routeProvider.when('/my', {
        templateUrl: '/static/partials/my.html',
        controller: MyCtrl
      });
      $routeProvider.when('/my/notes', {
        templateUrl: '/static/partials/notes.html',
        controller: NotesCtrl
      });
      $routeProvider.when('/my/tasks', {
        templateUrl: '/static/partials/tasks.html',
        controller: TasksCtrl
      });
      $routeProvider.when('/my/email', {
        templateUrl: '/static/partials/email.html',
        controller: EmailCtrl
      });
      $routeProvider.when('/people/timo-tiuraniemi/on-philosophy-of-technology', {
        templateUrl: '/static/partials/test/tech.html',
        controller: NoteCtrl
      });
      $routeProvider.when('/people/henri-ylikotila/essential-firefox-keyboard-shortcuts', {
        templateUrl: '/static/partials/test/firefox.html',
        controller: NoteCtrl
      });
      $routeProvider.when('/people/antti-takalahti/avocado-pasta', {
        templateUrl: '/static/partials/test/pasta.html',
        controller: NoteCtrl
      });
      $routeProvider.when('/people/timo-tiuraniemi/notes-on-productivity', {
        templateUrl: '/static/partials/test/productivity.html',
        controller: NoteCtrl
      });
      $routeProvider.when('/people/lauri-jarvilehto/why-its-great-to-be-a-nerd', {
        templateUrl: '/static/partials/test/nerd.html',
        controller: NoteCtrl
      });
      $routeProvider.when('/people/timo-tiuraniemi/how-to-break-out-of-facebook', {
        templateUrl: '/static/partials/test/nofacebook.html',
        controller: NoteCtrl
      });
      return $routeProvider.otherwise({
        redirectTo: '/'
      });
    });

  angular.bootstrap(document, ['em']);

}).call(this);