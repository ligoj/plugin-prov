/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery'], function ($) {
	return (function () {
		const cacheFilter = {};
		const operators = {
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

		const propertyMatchers = ['filterName', 'data'];

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
			const specificKeys = [];
			(join.filters || []).forEach(filter => {
				const property = filter.property;
				if (filter.table) {
					// There is a provide table, use ' IN ' condition
					const collection = filter.table.api().rows({ filter: 'applied' }).data();
					const collectionById = {};
					for (let index = collection.length; index-- > 0;) {
						collectionById[collection[index].id] = true;
					}
					specificKeys.push({ property: property, filter: propertyNewCustomFilter(property, operators['string'][filter.op](collectionById)) });
				}
			});
			return specificKeys;
		}

		function buildKeys(settings, global, filters) {
			const specificKeys = [];
			for (let filter0 of filters) {
				let filter = filter0.trim();
				if (filter.length === 0) {
					continue;
				}
				const filterParts = filter.split('|');
				let operator = null;
				let value = null;
				if (filterParts.length === 3) {
					// Specific filter
					const property = filterParts[0];
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
			const filters = search.split(',');
			const global = [];

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
			const filter = build(settings, type, search, join);
			// Expect at least one match among the OR conditions 
			// and all AND conditions
			// and all globals must match
			return (filter.specificOrKeys.length === 0 || !filter.specificOrKeys.some(s => s.filter(dataFilter, data)))
				&& (filter.specificAndKeys.length === 0 || filter.specificAndKeys.every(s => !s.filter(dataFilter, data)))
				&& (filter.global.length === 0 || filter.global.every(global => {
					for (let j = settings.aoColumns.length; j-- > 0;) {
						if (global(dataFilter[j], data)) {
							return true;
						}
					}
					// Expect at least one match among the columns
					return false;
				}));
		}

		// Exports
		return {
			accept: accept
		};

	}).call(this);
});