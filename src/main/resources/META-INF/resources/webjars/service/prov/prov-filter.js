/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery'], function ($) {
	return (function () {
		var cacheFilter = {};
		var operators = {
			'num': {
				'=': function (value) {
					value = parseInt(value, 10);
					return function (search) {
						return value === search;
					};
				},
				'>': function (value) {
					value = parseInt(value, 10);
					return function (search) {
						return value > search;
					};
				},
				'>=': function (value) {
					value = parseInt(value, 10);
					return function (search) {
						return value >= search;
					};
				},
				'<': function (value) {
					value = parseInt(value, 10);
					return function (search) {
						return value < search;
					};
				},
				'<=': function (value) {
					value = parseInt(value, 10);
					return function (search) {
						return value <= search;
					};
				},
				'!=': function (value) {
					value = parseInt(value, 10);
					return function (search) {
						return value !== search;
					};
				}
			},
			'string': {
				'=': function (value) {
					return function (search) {
						return value === search;
					};
				},
				'!=': function (value) {
					return function (search) {
						return value !== search;
					};
				},
				'~': function (value) {
					return function (search) {
						return value.indexOf(search) >= 0;
					};
				}
			}
		};

		function indexNewCustomFilter(index, type, value, operator) {
			var op = operators[type][operator](value);
			return function (dataFilter, resource) {
				return op(dataFilter[index])
			}
		}
		function propertyNewCustomFilter(property, type, value, operator) {
			var op = operators[type][operator](value);
			return function (dataFilter, resource) {
				return op(resource[property])
			}
		}

		var propertyMatchers = ['provType','name'];

		function newCustomFilter(settings, property, value, operator) {
			for (var j = 0; j < propertyMatchers.length; j++) {
				var propertyMatcher = propertyMatchers[j];
				for (var i = 0; i < settings.aoColumns.length; i++) {
					var column = settings.aoColumns[i];
					if (column[propertyMatcher] === property) {
						if (typeof column.provFilter === 'function') {
							// Custom column filter
							return column.provFilter(operators[type][operator](value));
						}
						// Generic filter
						return column[propertyMatcher.property](i, column, value, operator);
					}
				}
			}
			return genericNewCustomFilter(property, value, operator);
		}
		function stringNewCustomFilter(property, value, operator) {
			return genericNewCustomFilter(property, 'string', value, operator)
		}
		function numNewCustomFilter(property, value, operator) {
			return genericNewCustomFilter(property, 'num', value, operator)
		}
		function nameNewCustomFilter(value, operator) {
			return genericNewCustomFilter('name', 'string', value, operator);
		}

		function newFilter(settings, property, operator, value) {
			var filter = current[property + 'NewCustomFilter'];
			return newCustomFilter(settings, property, value, operator);
		}

		function build(settings, type, search) {
			if (cacheFilter[type] && cacheFilter[type].search === search) {
				// Use cache
				return cacheFilter[type].filter;
			}
			var filters = search.split(';');
			var specificKeys = [];
			var global = [];
			for (var i = 0; i < filters.length; i++) {
				var filter = filters[i];
				var filterParts = filter.split('!');
				var operator;
				var value;
				if (filter.startsWith('!')) {
					// Specific filter
					var property = filterParts[1];
					operator = filterParts[2];
					value = filterParts[3];
					specific[property] = newFilter(settings, property, operator, value);
					specificKeys.push(property);
				} else {
					// Global filter
					if (filterParts.length === 1) {
						operator = '=';
						value = filterParts[0];
					} else {
						operator = filterParts[0];
						value = filterParts[1];
					}
					global.push(operators['string'][operator](value));
				}
			}

			// Update the cache
			cacheFilter[type] = {
				search: search,
				filter: {
					specific: specific,
					specificKeys: specificKeys,
					global: global
				}
			}
			return cacheFilter[type].filter;
		}

		function accept(settings, type, dataFilter, data, search) {
			var filter = build(settings, type, search);
			for (var i = 0; i < filter.specificKeys.length; i++) {
				if (filter.specific[specificKeys[i]](dataFilter, data)) {
					return true;
				}
			}
			for (i = 0; i < filter.global.length; i++) {
				if (filter.global[i](dataFilter, data)) {
					return true;
				}
			}
			return false;
		}

		// Exports
		return {
			build: build,
			accept: accept
		};

	}).call(this);
});