/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery'], function ($) {
	return (function () {
		var cacheFilter = {};
		var operators = {
			'num': {
				'=': function (search) {
					search = parseInt(search, 10);
					return function (value) {
						return value === search;
					};
				},
				'>': function (search) {
					search = parseInt(search, 10);
					return function (value) {
						return value > search;
					};
				},
				'>=': function (search) {
					search = parseInt(search, 10);
					return function (value) {
						return value >= search;
					};
				},
				'<': function (search) {
					search = parseInt(search, 10);
					return function (value) {
						return value < search;
					};
				},
				'<=': function (search) {
					search = parseInt(search, 10);
					return function (value) {
						return value <= search;
					};
				},
				'!=': function (search) {
					search = parseInt(search, 10);
					return function (value) {
						return value !== search;
					};
				}
			},
			'string': {
				'=': function (search) {
					search = (search || '').toLowerCase();
					return function (value) {
						return value.toLowerCase() === search;
					};
				},
				'!=': function (search) {
					search = (search || '').toLowerCase();
					return function (value) {
						return value.toLowerCase() !== search;
					};
				},
				'~': function (search) {
					search = (search || '').toLowerCase();
					return function (value) {
						return value.toLowerCase().indexOf(search) >= 0;
					};
				}
			}
		};

		function indexNewCustomFilter(index, opFunction) {
			return function (dataFilter, resource) {
				return opFunction(dataFilter[index])
			}
		}
		function propertyNewCustomFilter(property, opFunction) {
			return function (dataFilter, resource) {
				return opFunction(resource[property])
			}
		}

		var propertyMatchers = ['filterName', 'data'];

		function newCustomFilter(settings, property, value, operator) {
			for (var j = 0; j < propertyMatchers.length; j++) {
				var propertyMatcher = propertyMatchers[j];
				for (var i = 0; i < settings.aoColumns.length; i++) {
					var column = settings.aoColumns[i];
					if (column[propertyMatcher] === property) {
						// Column matching to the property has been found
						var type = column.type || 'string';
						var opFunction = operators[type][operator](value);
						if (typeof column.filter === 'function') {
							// Return custom column filter function
							return column.filter(opFunction);
						}
						// Generic filter
						if (type === 'string') {
							return indexNewCustomFilter(i, opFunction);
						}
						return propertyNewCustomFilter(property, opFunction);
					}
				}
			}
			console.log('Not mapped property', property);
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

			// Need to build the cache configuration for this filter
			var filters = search.split(',');
			var specificKeys = [];
			var specific = {};
			var global = [];
			for (var i = 0; i < filters.length; i++) {
				var filter = filters[i].trim();
				if (filter.length === 0) {
					continue;
				}
				var filterParts = filter.split('|');
				var operator;
				var value;
				if (filter.startsWith('|')) {
					// Specific filter
					var property = filterParts[1];
					operator = filterParts[2];
					value = filterParts[3];
					specific[property] = newFilter(settings, property, operator, value);
					specificKeys.push(property);
				} else {
					// Global filter
					if (filterParts.length === 1) {
						operator = '~';
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
			if (filter.specificKeys.length === 0 && filter.global.length === 0) {
				// No filter
				return true;
			}
			for (var i = 0; i < filter.specificKeys.length; i++) {
				if (filter.specific[specificKeys[i]](dataFilter, data)) {
					return true;
				}
			}
			for (i = 0; i < filter.global.length; i++) {
				for (var j = 0; j < settings.aoColumns.length; j++) {
					if (filter.global[i](dataFilter[j], data)) {
						return true;
					}
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