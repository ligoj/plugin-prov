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
				'lj': function (collectionById) { /* LEFT JOIN */
					return function (value) {
						return !value || !!collectionById[value.id];
					};
				},
				'in': function (collectionById) { /* INNER JOIN (IN) */
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
				'~': function (search) { // Contains
					search = (search || '').toLowerCase();
					return function (value) {
						return value.toLowerCase().indexOf(search) >= 0;
					};
				},
				'*=': function (search) { // Contains
					search = (search || '').toLowerCase();
					return function (value) {
						return value.toLowerCase().indexOf(search) >= 0;
					};
				},
				'^=': function (search) { // Starts with
					search = (search || '').toLowerCase();
					return function (value) {
						return value.toLowerCase().indexOf(search) === 0;
					};
				},
				'=': function (search) { // Equals
					search = (search || '').toLowerCase();
					return function (value) {
						return value.toLowerCase() === search;
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
			for (let propertyMatcher of propertyMatchers) {
				for (let column of settings.aoColumns) {
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

		function buildJoin(join) {
			var specificKeys = [];
			for (let filter of (join.filters || [])) {
				var property = filter.property;
				if (filter.table) {
					// There is a provide table, use ' IN ' condition
					var collection = filter.table.api().rows({ filter: 'applied' }).data();
					var collectionById = {};
					for (let col of collection) {
						collectionById[col.id] = true;
					}
					specificKeys.push({ property: property, filter: propertyNewCustomFilter(property, operators['string'][filter.op](collectionById)) });
				}
			}
			return specificKeys;
		}

		function buildKeys(settings, global, filters) {
			var specificKeys = [];
			for (let filter0 of filters) {
				let filter = filter0.trim();
				if (filter.length === 0) {
					continue;
				}
				var filterParts = filter.split('|');
				var operator;
				var value;
				if (filterParts.length === 3) {
					// Specific filter
					var property = filterParts[0];
					operator = filterParts[1];
					value = filterParts[2];
					specificKeys.push({ property: property, filter: newCustomFilter(settings, property, operator, value) });
				} else {
					// Global filter
					if (filterParts.length === 1) {
						// Contains value
						operator = '~';
						value = filterParts[0];
					} else {
						// Specific operator
						operator = filterParts[0];
						value = filterParts[1];
					}
					global.push((operators['string'][operator] || operators['string']['~'])(value));
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
			var global = [];

			// Update the cache
			cacheFilter[type] = {
				search: search,
				join: join.cache,
				filter: {
					specificOrKeys: buildJoin(join),
					specificAndKeys: buildKeys(settings, global, filters),
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
			var i;
			if (filter.specificOrKeys.length) {
				for (i = 0; i < filter.specificOrKeys.length; i++) {
					if (filter.specificOrKeys[i].filter(dataFilter, data)) {
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
				for (i = 0; i < filter.specificAndKeys.length; i++) {
					if (!filter.specificAndKeys[i].filter(dataFilter, data)) {
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
			accept: accept
		};

	}).call(this);
});