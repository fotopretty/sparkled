const angular = require('angular');
const selectorConfig = require('./selector.config');
const selectorController = require('./selector.controller');
const songEntry = require('./song-entry.directive');

const selectorModule = angular.module('app.selector', [
    'app.filters',
    'app.navigation',
    'app.rest'
]);

selectorModule
    .config(selectorConfig)
    .controller(selectorController.name, selectorController.fn)
    .directive(songEntry.name, songEntry.fn);

module.exports = selectorModule;