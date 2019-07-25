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
				/* LEFT JOIN */
				'lj': function (collectionById) {
					return function (value) {
						return !value || !!collectionById[value.id];
					};
				},
				/* INNER JOIN (IN) */
				'in': function (collectionById) {
					return function (value) {
						return value && !!collectionById[value.id];
					};
				},
				'!in': function (collectionById) {
					return function (value) {
						return !value || !collectionById[value.id];
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

		function newCustomFilter(settings, property, operator, value) {
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

		function buildJoin(specific, join) {
			var specificKeys = [];
			for (var i = 0; i < (join.filters || []).length; i++) {
				var filter = join.filters[i];
				var property = filter.property;
				if (filter.table) {
					// There is a provide table, use ' IN ' condition
					var collection = filter.table.api().rows({ filter: 'applied' }).data();
					var collectionById = {};
					for (var j = 0; j < collection.length; j++) {
						collectionById[collection[j].id] = true;
					}
					specific[property] = propertyNewCustomFilter(property, operators['string'][filter.op](collectionById));
					specificKeys.push(property);
				}
			}
			return specificKeys;
		}

		function buildKeys(specific, settings, global, filters) {
			var specificKeys = [];
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
					specific[property] = newCustomFilter(settings, property, operator, value);
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
			return specificKeys;
		}

		function build(settings, type, search, join) {
			if (cacheFilter[type] && cacheFilter[type].search === search && cacheFilter[type].join === join.cache) {
				// Use cache
				return cacheFilter[type].filter;
			}

			// Need to build the cache configuration for this filter
			var filters = search.split(',');
			var specific = {};
			var global = [];

			// Update the cache
			cacheFilter[type] = {
				search: search,
				join: join.cache,
				filter: {
					specific: specific,
					specificOrKeys: buildJoin(specific, join),
					specificAndKeys: buildKeys(specific, settings, global, filters),
					global: global
				}
			}
			return cacheFilter[type].filter;
		}

		function accept(settings, type, dataFilter, data, search, join) {
			var filter = build(settings, type, search, join);
			if (filter.specificAndKeys.length === 0 && filter.specificOrKeys.length === 0 && filter.global.length === 0) {
				// No filter
				return true;
			}
			var found = false;
			if (filter.specificOrKeys.length) {
				for (var i = 0; i < filter.specificOrKeys.length; i++) {
					if (filter.specific[filter.specificOrKeys[i]](dataFilter, data)) {
						found = true;
						break;
					}
				}
				// Expect at least one match among the OR conditions
				if (!found) {
					return false;
				}
			}
			if (filter.specificAndKeys.length) {
				// All filters must match
				for (var i = 0; i < filter.specificAndKeys.length; i++) {
					if (!filter.specific[filter.specificAndKeys[i]](dataFilter, data)) {
						return false;
					}
				}
			}
			if (filter.global.length) {
				// All globals must match
				for (i = 0; i < filter.global.length; i++) {
					found = false;
					for (var j = 0; j < settings.aoColumns.length; j++) {
						if (filter.global[i](dataFilter[j], data)) {
							found = true;
							break;
						}
					}
					// Expect at least one match among the columns
					if (!found) {
						return false;
					}
				}
			}
			return true;
		}

		// Exports
		return {
			build: build,
			accept: accept
		};

	}).call(this);
});