/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
/*jshint esversion: 6*/
define(function () {

	var initializedPopupEvents = false;
	var initializedPopupUsage = false;

	/**
	 * OS key to markup/label mapping.
	 */
	var os = {
		'linux': ['Linux', 'fab fa-linux fa-fw'],
		'windows': ['Windows', 'fab fa-windows fa-fw'],
		'suse': ['SUSE', 'fab fa-suse fa-fw'],
		'rhel': ['Red Hat Enterprise', 'fab fa-redhat fa-fw'],
		'centos': ['CentOS', 'fab fa-centos fa-fw'],
		'debian': ['Debian', 'icon-debian fa-fw'],
		'fedora': ['Fedora', 'fab fa-fedora fa-fw'],
		'ubuntu': ['Ubuntu', 'fab fa-ubuntu fa-fw'],
		'freebsd': ['FreeBSD', 'fab fa-freebsd fa-fw']
	};

	/**
	 * Engine key to markup/label mapping.
	 */
	var databaseEngines = {
		'MYSQL': ['MySQL', 'icon-mysql'],
		'ORACLE': ['Oracle', 'icon-oracle'],
		'MARIADB': ['MariaDB', 'icon-mariadb'],
		'AURORA MYSQL': ['Aurora MySQL', 'icon-aws'],
		'AURORA POSTGRESQL': ['Aurora PostgreSQL', 'icon-aws'],
		'POSTGRESQL': ['PostgreSQL', 'icon-postgres'],
		'SQL SERVER': ['SQL Server', 'icon-mssql'],
	};

	/**
	 * Internet Access key to markup/label mapping.
	 */
	var internet = {
		'public': ['Public', 'fas fa-globe fa-fw'],
		'private': ['Private', 'fas fa-lock fa-fw'],
		'private_nat': ['NAT', 'fas fa-low-vision fa-fw']
	};

	/**
	 * Rate name (identifier) to class mapping. Classes are distributed across 5 values.
	 */
	var rates = {
		'worst': 'far fa-star text-danger fa-fw',
		'low': 'fas fa-star-half text-danger fa-fw',
		'medium': 'fas fa-star-half fa-fw',
		'good': 'fas fa-star text-primary fa-fw',
		'best': 'fas fa-star text-success fa-fw',
		'invalid': 'fas fa-ban fa-fw'
	};

	/**
	 * Rate name (identifier) to class mapping. Classes are ditributed across 3 values.
	*/
	var rates3 = {
		'low': 'far fa-star-half fa-fw',
		'medium': 'far fa-star text-success fa-fw',
		'good': 'fas fa-star text-success fa-fw',
		'invalid': 'fas fa-ban fa-fw'
	};

	/**
	 * Storage optimized key to markup/label mapping.
	 */
	var storageOptimized = {
		'throughput': 'fas fa-angle-double-right fa-fw',
		'durability': 'fas fa-archive fa-fw',
		'iops': 'fas fa-bolt fa-fw'
	};

	/**
	 * Support access type key to markup/label mapping.
	 */
	var supportAccessType = {
		'technical': 'fas fa-wrench fa-fw',
		'billing': 'fas fa-dollar-sign fa-fw',
		'all': 'fas fa-users fa-fw'
	};

	var maxOpts = {
		width: 250,
		labels: ['max', 'reserved'],
		values: [false, false],
		toInternal: v => Math.log2(v / 4 + 1),
		toValue: (w, maxWidth, maxValue, toInternal) => (Math.pow(2, w / maxWidth * toInternal(maxValue)) - 1) * 4,
		label: 'reserved',
	};

	function delay(callback, ms) {
		var timer = 0;
		return function () {
			var context = this, args = arguments;
			clearTimeout(timer);
			timer = setTimeout(function () {
				callback.apply(context, args);
			}, ms || 0);
		};
	}
	function ascendingComparator(o1, o2) {
		return o1.localeCompare(o2);
	}
	function locationComparator(l1, l2) {
		return locationToStringCompare(l1).localeCompare(l2);
	}

	function locationToStringCompare(location) {
		return location.name
			+ '#' + (location.subRegion && (current.$messages[location.subRegion] || location.subRegion))
			+ '#' + location.countryM49 && current.$messages.m49[parseInt(location.countryM49, 10)]
			+ '#' + location.id;
	}
	function locationMatcher(term, location) {
		return matcher(term, location.name)
			|| location.subRegion && matcher(term, (current.$messages[location.subRegion] || location.subRegion))
			|| location.description && matcher(term, location.description)
			|| location.countryM49 && matcher(term, current.$messages.m49[parseInt(location.countryM49, 10)])
			|| location.countryA2 && (matcher(term, location.countryA2) || location.countryA2 === 'UK' && matcher(term, 'GB'));
	}
	function newProcessorOpts(type) {
		return {
			placeholder: current.$messages['service:prov:processor-default'],
			allowClear: true,
			createSearchChoice: term => {
				if (current.model) {
					term = term.toLowerCase();
					var processors = current.model.configuration.processors;
					// Must be found in all resource types
					if (processors.instance.filter(p => p.toLowerCase().indexOf(term) >= 0).length && processors.database.filter(p => p.toLowerCase().indexOf(term) >= 0).length) {
						return { id: term, text: '[' + term + ']' };
					}
				}
				// Invalid processor
				return null;
			},
			data: () => {
				if (current.model) {
					var processors = current.model.configuration.processors;
					return { results: ((typeof type === 'function' ? processors[type()] : processors.instance.concat(processors.database)) || []).map(p => ({ id: p, text: p })) };
				}
				return { results: [] };
			}
		};
	}

	/**
	 * Format the constant CPU.
	 */
	function format3States(property, value) {
		return current.$messages['service:prov:' + property + '-' + (typeof value === 'boolean' ? value : 'null')];
	}

	/**
	 * Return the constant CPU query parameter value to use to filter some other inputs.
	 */
	function toQueryValue3States($element) {
		let value = ($element.is('li.active') ? $element : $element.find('li.active')).data('value');
		return (value === 'true' || value === true) ? true : ((value === 'false' || value === false) ? false : null);
	}

	/**
	 * Update the button text corresponding to the selected value in the dropdown list.
	 */
	function synchronizeDropdownText() {
		var $select = $(this).closest('.input-group-btn');
		$select.find('li.active').removeClass('active');
		var $active = $(this).addClass('active').find('a');
		$select.find('.btn span:first-child').html($active.find('i').length ? $active.find('i').prop('outerHTML') : $active.html());
	}

	/**
	 * Register a 3 states component.
	 * @param {jquery} $element UI component.
	 * @param {booelan} value  TRUE, FALSE or NULL value.
	 */
	function update3States($element, value) {
		$element.find('li.active').removeClass('active');
		if (value === true) {
			$element.find(`li[data-value="true"]`).addClass('active').each(synchronizeDropdownText);
		} else if (value === false) {
			$element.find(`li[data-value="false"]`).addClass('active').each(synchronizeDropdownText);
		} else {
			$element.find('li:first-child').addClass('active').each(synchronizeDropdownText);
		}
	}

	function formatDatabaseEngine(engine, mode, clazz) {
		var cfg = databaseEngines[(engine.id || engine || 'MYSQL').toUpperCase()] || databaseEngines.MYSQL;
		if (mode === 'sort' || mode === 'filter') {
			return cfg[0];
		}
		clazz = cfg[1] + (typeof clazz === 'string' ? clazz : '');
		return '<i class="' + clazz + '" data-toggle="tooltip" title="' + cfg[0] + '"></i> ' + cfg[0];
	}

	/**
	 * Format the efficiency of a data depending on the rate against the maximum value.
	 * @param value {number} Current value.
	 * @param max {number} Maximal value.
	 * @param formatter {function} Option formatter function of the value. 
	 * @returns {string} The value to display containing the rate.
	 */
	function formatEfficiency(value, max, formatter) {
		var fullClass = null;
		max = max || value || 1;
		if (value === 0) {
			value = max;
		} else if (max / 2.0 > value) {
			fullClass = 'far fa-circle text-danger';
		} else if (max / 1.65 > value) {
			fullClass = 'fas fa-adjust fa-rotate-270 text-danger';
		} else if (max / 1.5 > value) {
			fullClass = 'fas fa-adjust fa-rotate-270 text-warning';
		} else if (max / 1.3 > value) {
			fullClass = 'fas fa-circle text-primary';
		} else if (max / 1.01 > value) {
			fullClass = 'fas fa-circle text-success';
		}
		var rate = Math.round(value * 100 / max);
		return (formatter ? formatter(value) : value) + (fullClass ? '<span class="efficiency pull-right"><i class="' + fullClass + '" data-toggle="tooltip" title="' +
			Handlebars.compile(current.$messages['service:prov:usage-partial'])((formatter ? [formatter(value), formatter(max), rate] : [value, max, rate])) + '"></i></span>' : '');
	}

	/**
	 * Format the memory size.
	 */
	function formatCpu(value, mode, instance) {
		if (instance) {
			if (current.model.configuration.reservationMode === 'max' && instance.cpuMax) {
				value = instance.cpuMax;
			} else {
				value = instance.cpu;
			}
		}
		if (mode === 'sort' || mode === 'filter') {
			return value;
		}
		if (instance) {
			return formatEfficiency(value, instance.price.type.cpu);
		}
		return value;
	}

	/**
	 * Format the memory size.
	 */
	function formatRam(sizeMB, mode, instance) {
		if (instance) {
			if (current.model.configuration.reservationMode === 'max' && instance.ramMax) {
				sizeMB = instance.ramMax;
			} else {
				sizeMB = instance.ram;
			}
		}
		if (mode === 'sort' || mode === 'filter') {
			return sizeMB;
		}
		if (instance) {
			return formatEfficiency(sizeMB, instance.price.type.ram, function (value) {
				return formatManager.formatSize(value * 1024 * 1024, 3);
			});
		}
		return formatManager.formatSize(sizeMB * 1024 * 1024, 3);
	}

	function formatLicense(license) {
		return license.text || current.$messages['service:prov:license-' + license.toLowerCase()] || license;
	}

	function formatReservationMode(mode) {
		return current.$messages['service:prov:reservation-mode-' + mode.toLowerCase()] || mode;
	}

	/**
	 * Format instance type details.
	 */
	function formatInstanceType(name, mode, qi) {
		var type = qi ? qi.price.type : {};
		name = type ? type.name : name;
		if (mode !== 'display' || (typeof type.id === 'undefined')) {
			// Use only the name
			return name;
		}
		// Instance type details are available
		var details = type.description ? type.description.replace(/"/g, '') + '<br>' : '';
		details += '<i class=\'fas fa-bolt fa-fw\'></i> ';
		details += type.cpuRate ? '<i class=\'' + rates[type.cpuRate] + '\'></i> ' : '';
		if (type.cpu) {
			details += '#' + type.cpu;
			details += ' ' + format3States('constant', type.constant);
			if (typeof type.physical === "boolean") {
				details += ' (' + format3States('physical', type.physical) + ')';
			}
		} else {
			details += current.$messages['service:prov:instance-custom'];
		}

		if (type.ram) {
			details += '<br><i class=\'fas fa-memory fa-fw\'></i> ';
			details += type.ramRate ? '<i class=\'' + rates[type.ramRate] + '\'></i> ' : '';
			details += formatRam(type.ram);
		}

		if (type.storageRate) {
			details += '<br><i class=\'far fa-hdd fa-fw\'></i> ';
			details += type.ramRate ? '<i class=\'' + rates[type.storageRate] + '\'></i>' : '';
			// TODO Add instance storage
		}

		if (type.networkRate) {
			details += '<br><i class=\'fas fa-globe fa-fw\'></i> ';
			details += type.ramRate ? '<i class=\'' + rates[type.networkRate] + '\'></i>' : '';
			// TODO Add memory type
		}
		return '<u class="details-help" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
	}

	function formatStorageType(type, mode) {
		type = type || {};
		var name = type.name;
		if (mode !== 'display' || (typeof type.id === 'undefined')) {
			// Use only the name
			return name;
		}
		// Storage type details are available
		var details = type.description ? type.description.replace(/"/g, '') + '<br>' : '';
		details += '<i class=\'far fa-hdd fa-fw\'></i> ';
		details += formatManager.formatSize((type.minimal || 1) * 1024 * 1024 * 1024, 3) + ' - ';
		details += type.maximal ? formatManager.formatSize(type.maximal * 1024 * 1024 * 1024, 3) : '∞';

		if (type.latency) {
			details += '<br><i class=\'fas fa-fw fa-stopwatch\'></i> <i class=\'' + rates[type.latency] + '\'></i>';
		}
		if (type.iops) {
			details += '<br><i class=\'' + storageOptimized.iops + '\'></i> ' + type.iops + ' IOPS';
		}
		if (type.throughput) {
			details += '<br><i class=\'' + storageOptimized.throughput + '\'></i> ' + type.throughput + ' MB/s';
		}
		if (type.durability9) {
			var nines = '9'.repeat(type.durability9);
			nines = type.durability9 < 3 ? '0'.repeat(3 - type.durability9) : nines;
			details += '<br><i class=\'far fa-fw fa-gem\'></i> ' + nines.substring(0, 2) + '.' + nines.substring(2) + '%';
		}
		if (type.availability) {
			details += '<br><i class=\'fas fa-fw fa-thumbs-up\'></i> ' + type.availability + '%';
		}
		return '<u class="details-help" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
	}

	/**
	 * Format instance term detail
	 */
	function formatInstanceTerm(name, mode, qi) {
		var term = qi ? qi.price.term : null;
		name = term ? term.name : name;
		if (mode === 'sort' || mode === 'filter' || (term && typeof term.id === 'undefined')) {
			// Use only the name
			return name;
		}
		// Instance details are available
		var details = '<i class=\'fas fa-clock\'></i> ';
		if (term && term.period) {
			details += term.period + ' months period';
		} else {
			details = 'on demand, hourly (or less) billing period';
		}
		if (qi.price.initialCost) {
			details += '<br/>Initial cost: $' + qi.price.initialCost;
		}

		return '<u class="details-help" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
	}

	/**
	 * Format instance quantity
	 */
	function formatQuantity(quantity, mode, instance) {
		var min = typeof instance === 'undefined' ? quantity : (instance.minQuantity || 0);
		if (mode === 'sort' || mode === 'filter' || typeof instance === 'undefined') {
			return min;
		}

		var max = instance.maxQuantity;
		if (typeof max !== 'number') {
			return min + '+';
		}
		if (max === min) {
			return min;
		}

		// A range
		return min + '-' + max;
	}

	function getCurrencyUnit() {
		return current.model && current.model.configuration && current.model.configuration.currency && current.model.configuration.currency.unit || '$';
	}

	function getCurrencyRate() {
		return current.model && current.model.configuration && current.model.configuration.currency && current.model.configuration.currency.rate || 1.0;
	}

	function formatCostText(cost, isMax, _i, noRichText, unbound, currency) {
		return formatManager.formatCost(cost * (currency ? currency.rate || 1 : getCurrencyRate()), 3, (currency && currency.unit) || getCurrencyUnit(), noRichText === true ? '' : 'cost-unit') + (unbound ? '+' : '');
	}

	function formatCostOdometer(cost, isMax, $cost, noRichTest, unbound) {
		if (isMax) {
			formatManager.formatCost(cost * getCurrencyRate(), 3, getCurrencyUnit(), 'cost-unit', function (value, weight, unit) {
				var $wrapper = $cost.find('.cost-max');
				$wrapper.find('.cost-value').html(value);
				$wrapper.find('.cost-weight').html(weight + ((cost.unbound || unbound) ? '+' : ''));
				$wrapper.find('.cost-unit').html(unit);
			});
		} else {
			formatManager.formatCost(cost * getCurrencyRate(), 3, getCurrencyUnit(), 'cost-unit', function (value, weight, unit) {
				var $wrapper = $cost.find('.cost-min').removeClass('hidden');
				$wrapper.find('.cost-value').html(value);
				$wrapper.find('.cost-weight').html(weight);
				$wrapper.find('.cost-unit').html(unit);
			});
		}
	}

	/**
	 * Format the cost.
	 * @param {number} cost The cost value. May contains "min", "max" and "currency" attributes.
	 * @param {String|jQuery} mode Either 'sort' for a raw value, either a JQuery container for advanced format with "odometer". Otherwise will be simple format.
	 * @param {object} obj The optional cost object taking precedence over the cost parameter. May contains "min" and "max" attributes.
	 * @param {boolean} noRichText When true, the cost will be in plain text, no HTML markup.
	 * @return The formatted cost.
	 */
	function formatCost(cost, mode, obj, noRichText) {
		if (mode === 'sort' || mode === 'filter') {
			return cost;
		}

		var formatter = formatCostText;
		var $cost = $();
		if (mode instanceof jQuery) {
			// Odomoter format
			formatter = formatCostOdometer;
			$cost = mode;
		}

		// Computation part
		obj = (typeof obj === 'undefined' || obj === null) ? cost : obj;
		if (typeof obj.cost === 'undefined' && typeof obj.min !== 'number') {
			// Standard cost
			$cost.find('.cost-min').addClass('hidden');
			return formatter(cost, true, $cost, noRichText, cost && cost.unbound, cost && cost.currency);
		}
		// A floating cost
		var min = obj.cost || obj.min || 0;
		var max = typeof obj.maxCost === 'number' ? obj.maxCost : obj.max;
		var unbound = obj.unbound || (cost && cost.unbound) || (typeof obj.minQuantity === 'number' && (obj.maxQuantity === null || typeof obj.maxQuantity === 'undefined'));
		if ((typeof max !== 'number') || max === min) {
			// Max cost is equal to min cost, no range
			$cost.find('.cost-min').addClass('hidden');
			return formatter(min, true, $cost, noRichText, unbound, cost && cost.currency);
		}

		// Max cost, is different, display a range
		return formatter(min, false, $cost, noRichText) + '-' + formatter(max, true, $cost, noRichText, unbound, cost && cost.currency);
	}

	/**
	 * Return the HTML markup from the storage latency.
	 */
	function formatStorageLatency(latency, mode, clazz) {
		var id = latency ? (latency.id || latency).toLowerCase() : 'invalid';
		var text = id && current.$messages['service:prov:storage-latency-' + id];
		if (mode === 'sort' || mode === 'filter') {
			return text;
		}

		clazz = rates[id] + (typeof clazz === 'string' ? clazz : '');
		return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode ? ' ' + text : '');
	}

	/**
	 * Format the storage size.
	 */
	function formatStorage(sizeGB, mode, data) {
		if (mode === 'sort' || mode === 'filter') {
			return sizeGB;
		}
		if (data && data.price.type.minimal > sizeGB) {
			// Enable efficiency display
			return formatEfficiency(sizeGB, data.price.type.minimal, function (value) {
				return formatManager.formatSize(value * 1024 * 1024 * 1024, 3);
			});
		}

		// No efficiency rendering can be done
		return formatManager.formatSize(sizeGB * 1024 * 1024 * 1024, 3);
	}

	/**
	 * Return the HTML markup from the storage optimized.
	 */
	function formatStorageOptimized(optimized, withText, clazz) {
		if (optimized) {
			var id = (optimized.id || optimized).toLowerCase();
			var text = current.$messages['service:prov:storage-optimized-' + id];
			clazz = storageOptimized[id] + (typeof clazz === 'string' ? clazz : '');
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (withText ? ' ' + text : '');
		}
	}

	/**
	 * Format the storage size to html markup.
	 * @param {object} qs Quote storage with price, type and size.
	 * @param {boolean} showName When true, the type name is displayed. Default is false.
	 * @return {string} The HTML markup representing the quote storage : type and flags.
	 */
	function formatStorageHtml(qs, showName) {
		var type = qs.price.type;
		return (showName === true ? type.name + ' ' : '') + formatStorageLatency(type.latency) +
			(type.optimized ? ' ' + formatStorageOptimized(type.optimized) : '') +
			' ' + formatManager.formatSize(qs.size * 1024 * 1024 * 1024, 3) +
			((qs.size < type.minimal) ? ' (' + formatManager.formatSize(type.minimal * 1024 * 1024 * 1024, 3) + ')' : '');
	}

	/**
	 * Format the storage price to html markup.
	 * @param {object} qs Quote storage with price, type and size.
	 * @return {string} The HTML markup representing the quote storage : cost, type and flags.
	 */
	function formatStoragePriceHtml(qs) {
		return formatStorageHtml(qs, false) + ' ' + qs.price.type.name + '<span class="pull-right text-small">' + formatCost(qs.cost) + '<span class="cost-unit">/m</span></span>';
	}

	/**
	 * Format an attached storages
	 */
	function formatQiStorages(instance, mode) {
		if (mode === 'filter') {
			return '';
		}

		if (mode === 'sort') {
			// Compute the sum
			var storages = instance.storages;
			var sum = 0;
			if (storages) {
				storages.forEach(storage => sum += storage.size);
			}
			return sum;
		}
		// Need to build a Select2 tags markup
		return '<input type="text" class="storage-tags" data-instance="' + instance.id + '" autocomplete="off" name="storage-tags">';
	}

	/**
	 * Return the HTML markup from the OS key name.
	 */
	function formatOs(value, mode, clazz) {
		if (mode === 'sort' || mode === 'filter') {
			return value.id || value || 'linux';
		}
		var cfg = os[(value.id || value || 'linux').toLowerCase()] || os.linux;
		clazz = cfg[1] + (typeof clazz === 'string' ? clazz : '');
		return '<i class="' + clazz + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
	}

	/**
	 * Return the HTML markup from the Internet privacy key name.
	 */
	function formatInternet(value, mode, clazz) {
		var cfg = (value && internet[(value.id || value).toLowerCase()]) || current.value.public || 'public';
		if (mode === 'sort' || mode === 'filter') {
			return cfg[0];
		}
		clazz = cfg[1] + (typeof clazz === 'string' ? clazz : '');
		return '<i class="' + clazz + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
	}

	function formatUsageTemplate(usage, mode) {
		if (mode === 'sort' || mode === 'filter') {
			return usage ? usage.text || usage.name : current.model.configuration.usage ? usage.name : 'default';
		}
		if (usage) {
			usage = {
				name: (mode ? usage.name || usage.text : (usage.text || usage.name)) || usage,
				rate: usage.id,
				duration: false,
				start: false
			}
		} else {
			usage = current.model.configuration.usage || { rate: 100, duration: 1, name: '<i>default</i>' };
		}
		var tooltip = current.title('name') + usage.name;
		tooltip += '<br>' + current.title('usage-rate') + (usage.rate || 100) + '%';
		if (usage.duration !== false) {
			tooltip += '<br>' + current.title('usage-duration') + (usage.duration || 1) + ' month(s)';
		}
		if (usage.start !== false) {
			tooltip += '<br>' + current.title('usage-start') + (usage.start || 0) + ' month(s)';
		}
		return '<span data-toggle="tooltip" title="' + tooltip + '">' + usage.name + '</span>';
	}

	function locationMap(location) {
		if (location.longitude) {
			// https://www.google.com/maps/place/33%C2%B048'00.0%22S+151%C2%B012'00.0%22E/@-33.8,151.1978113,3z
			// http://www.google.com/maps/place/49.46800006494457,17.11514008755796/@49.46800006494457,17.11514008755796,17z
			//				html += '<a href="https://maps.google.com/?q=' + location.latitude + ',' + location.longitude + '" target="_blank"><i class="fas fa-location-arrow"></i></a> ';
			return '<a href="http://www.google.com/maps/place/' + location.latitude + ',' + location.longitude + '/@' + location.latitude + ',' + location.longitude + ',3z" target="_blank"><i class="fas fa-location-arrow"></i></a> ';
		} else {
			return '';
		}
	}

	/**
	 * Location html renderer.
	 */
	function locationToHtml(location, map, short) {
		var id = location.name;
		var subRegion = location.subRegion && (current.$messages[location.subRegion] || location.subRegion);
		var m49 = location.countryM49 && current.$messages.m49[parseInt(location.countryM49, 10)];
		var placement = subRegion || (location.placement && current.$messages[location.placement]) || location.placement;
		var html = map === true ? locationMap(location) : '';
		if (location.countryA2) {
			var a2 = (location.countryA2 === 'UK' ? 'GB' : location.countryA2).toLowerCase();
			var tooltip = m49 || id;
			var img = '<img class="flag-icon prov-location-flag" src="' + current.$path + 'flag-icon-css/flags/4x3/' + a2 + '.svg" alt=""';
			if (short === true) {
				// Only flag
				tooltip += (placement && placement !== html) ? '<br>Placement: ' + placement : '';
				tooltip += '<br>Id: ' + id;
				return '<u class="details-help" data-toggle="popover" data-content="' + tooltip + '" title="' + location.name + '">' + img + '></u>';
			}
			html += img + ' title="' + location.name + '">';
		}
		html += m49 || id;
		html += (placement && placement !== html) ? ' <span class="small">(' + placement + ')</span>' : '';
		html += (subRegion || m49) ? '<span class="prov-location-api">' + id + '</span>' : id;
		return html;
	}

	function formatLocation(location, mode, data) {
		var conf = current.model.configuration;
		var obj;
		if (location) {
			if (location.id) {
				obj = location;
			} else {
				obj = conf.locationsById[location];
			}
		} else if (data.price && data.price.location) {
			obj = conf.locationsById[data.price.location];
		} else if (data.quoteInstance && data.quoteInstance.price.location) {
			obj = conf.locationsById[data.quoteInstance.price.location];
		} else if (data.quoteDatabase && data.quoteDatabase.price.location) {
			obj = conf.locationsById[data.quoteDatabase.price.location];
		} else {
			obj = current.model.configuration.location;
		}

		if (mode === 'sort' || mode === 'filter') {
			return obj ? obj.name : '';
		}
		return locationToHtml(obj, false, true);
	}

	/**
	 * Return the HTML markup from the quote instance model.
	 */
	function formatQuoteResource(resource) {
		if (resource) {
			return (resource.resourceType === 'instance' ? '<i class="fas fa-server"></i>' : '<i class="fas fa-database"></i>') + ' ' + resource.name;
		}
		return '';
	}

	/**
	 * Return the HTML markup from the support level.
	 */
	function formatSupportLevel(level, mode, clazz) {
		var id = level ? (level.id || level).toLowerCase() : '';
		if (id) {
			var text = current.$messages['service:prov:support-level-' + id];
			clazz = rates3[id] + (typeof clazz === 'string' ? clazz : '');
			if (mode === 'sort' || mode === 'filter') {
				return text;
			}

			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode ? ' ' + text : '');
		}
		return '';
	}

	/**
	 * Return the HTML markup from the support seats.
	 */
	function formatSupportSeats(seats, mode) {
		if (mode === 'sort' || mode === 'filter') {
			return seats || 0;
		}
		return seats ? seats : '∞';
	}

	/**
	 * Return the HTML markup from the support access type.
	 */
	function formatSupportAccess(type, mode) {
		if (type) {
			var id = (type.id || type).toLowerCase();
			var text = current.$messages['service:prov:support-access-' + id];
			if (mode === 'sort' || mode === 'filter') {
				return text;
			}
			var clazz = supportAccessType[id];
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode === 'display' ? '' : (' ' + text));
		}
	}

	function formatSupportType(type, mode) {
		type = type || {};
		var name = type.name;
		if (mode !== 'display' || (typeof type.id === 'undefined')) {
			return name;
		}
		// Support type details are available
		const description = type.description;
		var descriptionIsLink = false;
		var details = '';
		if (description && !description.startsWith('http://') && !description.startsWith('https://')) {
			details = type.description.replace(/"/g, '') + '</br>';
			descriptionIsLink = true;
		}

		var slaText;
		if (type.slaEndTime) {
			if (type.slaStartTime === 0 && type.slaEndTime === 86400000) {
				slaText = '24' + (type.slaWeekEnd && '/7' || 'h Business days');
			} else {
				slaText = momentManager.time(type.slaStartTime || 0) + '-' + momentManager.time(type.slaEndTime) + (type.slaWeekEnd ? '' : ' Business days');
			}
		} else {
			slaText = 'No SLA'
		}
		details += '<i class=\'fas fa-fw fa-clock\'></i> SLA: ' + slaText;
		if (type.commitment) {
			details += '<br><i class=\'calendar-alt\'></i> ' + current.$messages['service:prov:support-commitment'] + ': ' + moment.duration(type.commitment, 'months').humanize();
		}

		var markup = '';
		if (descriptionIsLink) {
			// Description as a link
			markup = '<a href="' + description + '" target="_blank">';
		}
		markup += '<u class="details-help" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
		if (descriptionIsLink) {
			// Description as a link
			markup += '</a>';
		}
		return markup;
	}

	/**
	 * Return true when the term is found in the text.
	 * @param {string} term The term to find.
	 * @param {string} text The text candidate.
	 */
	function matcher(term, text) {
		return window.Select2.util.stripDiacritics('' + text).toUpperCase().indexOf(window.Select2.util.stripDiacritics('' + term).toUpperCase()) >= 0;
	}

	function filterSelect2Result(items, options, customComparator, customMatcher) {
		// No new Ajax query is needed
		let filtered = [];
		customMatcher = customMatcher || matcher;
		const pageSize = options.data.length;
		let i = 0;
		for (; i < items.length && filtered.length < pageSize * options.data.page; i++) {
			let datum = items[i];
			let term = options.data.q;
			if (customMatcher(term, datum)) {
				filtered.push(datum);
			}
		}
		filtered = filtered.sort(customComparator);
		options.success({
			results: filtered.slice((options.data.page - 1) * pageSize, options.data.page * pageSize),
			more: i < items.length
		});
	}

	/**
	 * Generic Ajax Select2 configuration.
	 * @param path {string|function} Either a string, either a function returning a relative path suffix to 'service/prov/$subscription/$path'
	 */
	function genericSelect2(placeholder, formatSelection, path, formatResult, customComparator, matcherFn) {
		const pageSize = 15;
		return {
			formatSelection: formatSelection,
			formatResult: formatResult || formatSelection,
			escapeMarkup: m => m,
			allowClear: placeholder !== false,
			placeholder: placeholder ? placeholder : null,
			formatSearching: () => current.$messages.loading,
			ajax: {
				url: () => REST_PATH + 'service/prov/' + current.model.subscription + '/' + (typeof path === 'function' ? path() : path),
				dataType: 'json',
				data: (term, page, opt) => typeof pageSize === 'undefined' ? { term: term, page: page, opt: opt } : {
					'search[value]': term, // search term
					'q': term, // search term
					'rows': pageSize,
					'page': page,
					'start': (page - 1) * pageSize,
					'filters': '{}',
					'sidx': 'name',
					'length': pageSize,
					'columns[0][name]': 'name',
					'order[0][column]': 0,
					'order[0][dir]': 'asc',
					'sortd': 'asc'
				},
				transport: typeof customComparator === 'function' ? function (options) {
					let $this = this;
					if ($this.cachedData) {
						filterSelect2Result($this.cachedData, options, customComparator, matcherFn);
					} else {
						// No yet cached data
						let success = options.success;
						options.success = function (data) {
							$this.cachedData = data
							// Restore the original success funtion
							options.success = success;
							filterSelect2Result(data, options, customComparator, matcherFn);
						}
						$.fn.select2.ajaxDefaults.transport(options);
					}
				} : $.fn.select2.ajaxDefaults.transport,
				results: function (data, page) {
					let result = ((typeof data.data === 'undefined') ? (data.results || data) : data.data).map(item => {
						if (typeof item === 'string') {
							item = {
								id: item,
								text: formatSelection(item)
							};
						} else {
							item.text = formatSelection(item);
						}
						return item;
					});
					return {
						more: typeof data.more === 'undefined' ? data.recordsFiltered > page * pageSize : data.more,
						results: result
					};
				}
			}
		};
	}

	/**
	 * Return the query parameter name to use to filter some other inputs.
	 */
	function toQueryName(type, $item) {
		var id = $item.attr('id');
		return id.indexOf(type + '-') === 0 && id.substring((type + '-').length);
	}

	/**
	 * Return the memory weight: 1 or 1024.
	 */
	function getRamUnitWeight() {
		return parseInt(_('instance-ram-unit').find('li.active').data('value'), 10);
	}

	function initializePopupInnerEvents() {
		// Memory unit, physical, CPU constant/variable selection
		$('#popup-prov-generic').on('click', '.input-group-btn li', function (e) {
			$.proxy(synchronizeDropdownText, $(this))();
			// Also trigger the change of the value
			$(e.target).closest('.input-group-btn').prev('input').trigger('keyup');
		});
		$('#instance-min-quantity, #instance-max-quantity').on('change', current.updateAutoScale);
		$('input.resource-query').not('[type="number"]').on('change', current.checkResource);
		$('input.resource-query[type="number"]').on('keyup', delay(function (event) {
			if (event.which !== 16 && event.which !== 91 && event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
				$(this).trigger('change');
				$.proxy(current.checkResource, $(this))();
			}
		}, 50));
		_('instance-usage').select2(current.usageSelect2(current.$messages['service:prov:default']));
		_('instance-processor').select2(newProcessorOpts(() => _('popup-prov-generic').provType()));
		_('instance-license').select2(genericSelect2(current.$messages['service:prov:default'], formatLicense, function () {
			return _('instance-license').provType() === 'instance'
				? 'instance-license/' + _('instance-os').val()
				: ('database-license/' + _('database-engine').val())
		}));
		_('instance-location').select2(current.locationSelect2(current.$messages['service:prov:default']));
		_('storage-location').select2(current.locationSelect2(current.$messages['service:prov:default']));
		_('storage-instance').select2({
			formatSelection: formatQuoteResource,
			formatResult: formatQuoteResource,
			placeholder: current.$messages['service:prov:no-attached-instance'],
			allowClear: true,
			id: function (r) {
				return r.resourceType + r.id;
			},
			escapeMarkup: function (m) {
				return m;
			},
			data: function () {
				return {
					results: current.model.configuration.instances.concat(current.model.configuration.databases).map(r => {
						r.text = r.name;
						return r;
					})
				};
			}
		});

		$('.support-access').select2({
			formatSelection: formatSupportAccess,
			formatResult: formatSupportAccess,
			allowClear: true,
			placeholder: 'None',
			escapeMarkup: m => m,
			data: [{
				id: 'technical',
				text: 'technical'
			}, {
				id: 'billing',
				text: 'billing'
			}, {
				id: 'all',
				text: 'all'
			}]
		});
		_('support-level').select2({
			formatSelection: formatSupportLevel,
			formatResult: formatSupportLevel,
			allowClear: true,
			placeholder: 'None',
			escapeMarkup: m => m,
			data: [{
				id: 'LOW',
				text: 'LOW'
			}, {
				id: 'MEDIUM',
				text: 'MEDIUM'
			}, {
				id: 'GOOD',
				text: 'GOOD'
			}]
		});

		_('instance-os').select2({
			formatSelection: formatOs,
			formatResult: formatOs,
			escapeMarkup: m => m,
			data: [{
				id: 'LINUX',
				text: 'LINUX'
			}, {
				id: 'WINDOWS',
				text: 'WINDOWS'
			}, {
				id: 'SUSE',
				text: 'SUSE'
			}, {
				id: 'RHEL',
				text: 'RHEL'
			}, {
				id: 'CENTOS',
				text: 'CENTOS'
			}, {
				id: 'DEBIAN',
				text: 'DEBIAN'
			}, {
				id: 'UBUNTU',
				text: 'UBUNTU'
			}, {
				id: 'FEDORA',
				text: 'FEDORA'
			}]
		});
		_('instance-software').select2(genericSelect2(current.$messages['service:prov:software-none'], current.defaultToText, () => 'instance-software/' + _('instance-os').val()));
		_('database-engine').select2(genericSelect2(null, formatDatabaseEngine, 'database-engine', null, ascendingComparator));
		_('database-edition').select2(genericSelect2(current.$messages['service:prov:database-edition'], current.defaultToText, () => 'database-edition/' + _('database-engine').val()));
		_('instance-internet').select2({
			formatSelection: formatInternet,
			formatResult: formatInternet,
			formatResultCssClass: function (data) {
				if (data.id === 'PRIVATE_NAT' && _('instance-internet').provType() === 'database') {
					return 'hidden';
				}
			},
			escapeMarkup: m => m,
			data: [{
				id: 'PUBLIC',
				text: 'PUBLIC'
			}, {
				id: 'PRIVATE',
				text: 'PRIVATE'
			}, {
				id: 'PRIVATE_NAT',
				text: 'PRIVATE_NAT'
			}]
		});

		_('storage-optimized').select2({
			placeholder: current.$messages['service:prov:no-requirement'],
			allowClear: true,
			formatSelection: formatStorageOptimized,
			formatResult: formatStorageOptimized,
			escapeMarkup: m => m,
			data: [{
				id: 'THROUGHPUT',
				text: 'THROUGHPUT'
			}, {
				id: 'IOPS',
				text: 'IOPS'
			}, {
				id: 'DURABILITY',
				text: 'DURABILITY'
			}]
		});
		_('storage-latency').select2({
			placeholder: current.$messages['service:prov:no-requirement'],
			allowClear: true,
			formatSelection: formatStorageLatency,
			formatResult: formatStorageLatency,
			escapeMarkup: m => m,
			data: [{
				id: 'BEST',
				text: 'BEST'
			}, {
				id: 'GOOD',
				text: 'GOOD'
			}, {
				id: 'MEDIUM',
				text: 'MEDIUM'
			}, {
				id: 'LOW',
				text: 'LOW'
			}, {
				id: 'WORST',
				text: 'WORST'
			}]
		});
		_('instance-term').select2(current.instanceTermSelect2(false));
	}


	/**
	 * Initialize data tables and popup event : delete and details
	 */
	function initializePopupEvents(type) {
		// Resource edition pop-up
		var popupType = (type == 'instance' || type == 'database') ? 'generic' : type;
		var $popup = _('popup-prov-' + popupType);
		$popup.on('shown.bs.modal', function () {
			var inputType = (type == 'instance' || type == 'database') ? 'instance' : type;
			_(inputType + '-name').trigger('focus');
		}).on('submit', function (e) {
			e.preventDefault();
			current.save($(this).provType());
		}).on('show.bs.modal', function (event) {
			if (initializedPopupEvents === false) {
				initializedPopupEvents = true;
				initializePopupInnerEvents();
			}
			var $source = $(event.relatedTarget);
			var $tr = $source.closest('tr');
			var dynaType = $source.provType();
			var $table = _('prov-' + dynaType + 's');
			var quote = ($tr.length && $table.dataTable().fnGetData($tr[0])) || {};
			_('generic-modal-title').html(current.$messages['service:prov:' + dynaType]);
			$(this).attr('data-prov-type', dynaType)
				.find('input[type="submit"]')
				.removeClass('btn-primary btn-success')
				.addClass(quote.id ? 'btn-primary' : 'btn-success');
			$popup.find('.old-required').removeClass('old-required').attr('required', 'required');
			$popup.find('[data-exclusive]').not('[data-exclusive="' + dynaType + '"]').find(':required').addClass('old-required').removeAttr('required');
			if (quote.id) {
				current.enableCreate($popup);
			} else {
				current.disableCreate($popup);
			}
			current.model.quote = quote;
			current.toUi(dynaType, quote);
		});
	}


	function copyToData($form, prefix, data) {
		$form.find('input[id^=' + prefix + ']:not(.ui-only)').each(function () {
			let $input = $(this);
			let name = $input.attr('id').substring(prefix.length);
			if (typeof data[name] === 'undefined') {
				if ($input.data('ligojCheckbox3')) {
					data[name] = $input.checkbox3('value');
				} else if ($input.is('[type="checkbox"]')) {
					data[name] = $input.is(':checked');
				} else if ($input.is('[type="number"]') || name === 'id') {
					data[name] = cleanInt($input.val());
				} else {
					data[name] = $input.val();
				}
			}
		});
		return data;
	}

	function copyToUi($form, prefix, data) {
		$form.find('input[id^=' + prefix + ']:not(.ui-only)').each(function () {
			let $input = $(this);
			let name = $input.attr('id').substring(prefix.length);
			if ($input.data('ligojCheckbox3')) {
				$input.checkbox3('value', typeof data[name] === 'boolean' ? data[name] : null);
			} else if ($input.is('[type="checkbox"]')) {
				$input.prop('checked', data[name] === true);
			} else if (typeof data[name] !== 'undefined') {
				$input.val(data[name]);
			} else if ($input.is('[type="number"]')) {
				$input.val(0);
			}
		});
	}

	function initializeUsageInnerEvents() {
		_('popup-prov-usage').find('.checkbox3').checkbox3({ value: null });
		_('popup-prov-usage').on('shown.bs.modal', function () {
			_('usage-name').trigger('focus');
		}).on('submit', function (e) {
			e.preventDefault();
			current.saveOrUpdateUsage(copyToData($(this), 'usage-', {
				rate: parseInt(_('usage-rate').val() || '100', 10),
				duration: parseInt(_('usage-duration').val() || '1', 10)
			}));
		});

		$('.usage-inputs input').on('change', current.synchronizeUsage).on('keyup', current.synchronizeUsage);
		_('prov-usage-delete').click(() => current.deleteUsage(parseInt(_('usage-id').val()), 10));

		// Usage rate template
		var usageTemplates = [
			{
				id: 29,
				text: moment().day(1).format('dddd') + ' - ' + moment().day(5).format('dddd') + ', 8h30 - 18h30'
			}, {
				id: 35,
				text: moment().day(1).format('dddd') + ' - ' + moment().day(5).format('dddd') + ', 8h00 - 20h00'
			}, {
				id: 100,
				text: current.$messages['service:prov:usage-template-full']
			}
		];
		for (var i = 0; i < usageTemplates.length; i++) {
			current.usageTemplates[usageTemplates[i].id] = usageTemplates[i];
		}
		_('usage-template').select2({
			placeholder: current.$messages['service:prov:template'],
			allowClear: true,
			formatSelection: formatUsageTemplate,
			formatResult: formatUsageTemplate,
			escapeMarkup: m => m,
			data: usageTemplates
		});

	}

	/**
	 * Configure usage.
	 */
	function initializeUsage() {
		_('popup-prov-usage').on('show.bs.modal', function (event) {
			if (initializedPopupUsage == false) {
				initializedPopupUsage = true;
				initializeUsageInnerEvents();
			}
			current.enableCreate(_('popup-prov-usage'));
			if ($(event.relatedTarget).is('.btn-success')) {
				// Create mode
				_('usage-id').val('');
				_('usage-name').val('');
				_('usage-rate').val(100);
				_('usage-duration').val(1);
				_('usage-start').val(0);
				$(this).find('input[type="checkbox"]').checkbox3('value', null);
			} else {
				// Update mode
				copyToUi($(this), 'usage-', event.relatedTarget);
			}
			validationManager.reset($(this));
			validationManager.mapping.name = 'usage-name';
			validationManager.mapping.rate = 'usage-rate';
			validationManager.mapping.duration = 'usage-duration';
			validationManager.mapping.start = 'usage-start';
			_('usage-rate').trigger('change');
		});
		_('instance-usage-upload').select2(current.usageSelect2(current.$messages['service:prov:default']));
		_('quote-usage').select2(current.usageSelect2(current.$messages['service:prov:usage-100']))
			.on('change', function (event) {
				current.updateQuote({
					usage: event.added || null
				}, { name: 'usage', ui: 'quote-usage', previous: event.removed }, true);
			});
		var $usageSelect2 = _('quote-usage').data('select2');
		if (typeof $usageSelect2.originalSelect === 'undefined') {
			$usageSelect2.originalSelect = $usageSelect2.onSelect;
		}
		$usageSelect2.onSelect = (function (fn) {
			return function (data, options) {
				if (options) {
					var $target = $(options.target).closest('.prov-usage-select2-action');
					if ($target.is('.update')) {
						// Update action
						_('quote-usage').select2('close');
						_('popup-prov-usage').modal('show', data);
						return;
					}
				}
				return fn.apply(this, arguments);
			};
		})($usageSelect2.originalSelect);
	}



	function cleanData(data) {
		return (typeof data === 'string') ? data.replace(',', '.').replace(' ', '') || null : data;
	}

	function getResourceValue($item) {
		var value = '';
		if ($item.is('.input-group-btn')) {
			value = $item.find('li.active').data('value');
		} else if ($item.prev().is('.select2-container')) {
			var data = ($item.select2('data') || {});
			value = $item.is('.named') ? data.name || (data.data && data.data.name) : (data.id || $item.val());
		} else if ($item.data('ligojProvSlider')) {
			value = $item.provSlider('value', 'reserved');
		} else if ($item.is('[type="number"]')) {
			value = parseInt(cleanData($item.val()) || "0", 10);
		} else if (!$item.is('.select2-container')) {
			value = cleanData($item.val());
		}
		return value;
	}

	function cleanFloat(data) {
		let cData = cleanData(data);
		return cData && parseFloat(cData, 10);
	}
	function cleanInt(data) {
		let cData = cleanData(data);
		return cData && parseInt(cData, 10);
	}
	function cleanRam(mode) {
		let cData = cleanInt(_('instance-ram').provSlider('value', mode));
		if (cData) {
			return cData * getRamUnitWeight();
		}
		return cData;
	}

	var current = {

		/**
		 * Current quote.
		 */
		model: null,

		/**
		 * Usage rate templates
		 */
		usageTemplates: {},

		/**
		 * Tag manager
		 */
		tagManager: null,

		/**
		 * Filter manager.
		 */
		filterManager: null,

		contextDonut: null,

		/**
		 * Enable resource type.
		 */
		types: ['instance', 'database', 'storage', 'support'],

		/**
		 * Threshold
		*/
		changesReloadThreshold: 200,

		/**
		 * Show the members of the given group
		 */
		configure: function (subscription) {
			current.model = subscription;
			current.cleanup();
			$('.loader-wrapper').addClass('hidden');
			require(['text!../main/service/prov/menu.html', '../main/service/prov/prov-tag', '../main/service/prov/prov-filter', '../main/service/prov/prov-slider', '../main/service/prov/lib/checkbox3'], function (menu, tagManager, filterManager) {
				_('service-prov-menu').empty().remove();
				current.$cascade.trigger('html', _('extra-menu').append($(Handlebars.compile(menu)(current.$messages))));
				current.tagManager = tagManager.build(current);
				current.filterManager = filterManager;
				current.initOdometer();
				current.optimizeModel();
				current.initializeForm();
				current.initializeUpload();
				_('subscribe-configuration-prov').removeClass('hide');
				$('.provider').text(current.model.node.name);
				_('name-prov').val(current.model.configuration.name);
				var now = moment();
				$('.prov-export-instances-inline').attr('href', REST_PATH + 'service/prov/' + subscription.id + '/ligoj-prov-instances-inline-storage-' + subscription.id + '-' + now.format('YYYY-MM-DD') + '.csv');
				$('.prov-export-instances-split').attr('href', REST_PATH + 'service/prov/' + subscription.id + '/ligoj-prov-split-' + subscription.id + '-' + now.format('YYYY-MM-DD') + '.csv');
				$('.prov-export-full-json').attr('href', REST_PATH + 'subscription/' + subscription.id + '/configuration').attr('download', 'ligoj-full-' + subscription.id + '-' + now.format('YYYY-MM-DD') + '.json');
			});
		},

		/**
		 * Cleanup the UI component cache.
		 */
		cleanup: function () {
			delete current.contextDonut;
			delete current.d3Arc;
			delete current.d3Bar
			delete current.d3Gauge;
			initializedPopupEvents = false;
			initializedPopupUsage = false;
			current.types.forEach(type => delete current[type + 'Table']);
		},

		unload: function () {
			// Clean the shared menu
			_('service-prov-menu').empty().remove();
			current.cleanup();
			$(window).off('resize.barchart');
		},

		/**
		 * Reload the model
		 */
		reload: function () {
			// Clear the tables
			current.redrawAll();
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'subscription/' + current.model.subscription + '/configuration',
				type: 'GET',
				success: function (data) {
					current.model = data;
					current.optimizeModel();
					var configuration = data.configuration;
					current.types.forEach(type => _('prov-' + type + 's').DataTable().rows.add(current.model.configuration[type + 's']).draw(false));
					current.updateUiAssumptions(configuration);
					current.updateUiCost();
				}
			});
		},

		updateUiAssumptions: function (conf) {
			_('quote-location').select2('data', conf.location);
			$('.location-wrapper').html(locationMap(conf.location));
			_('quote-support').select2('data', conf.supports);
			conf.reservationMode = conf.reservationMode || 'reserved';
			_('quote-reservation-mode').select2('data', { id: conf.reservationMode, text: formatReservationMode(conf.reservationMode) });
			update3States(_('quote-physical'), conf.physical);
			_('quote-processor').select2('data', conf.processor ? { id: conf.processor, text: conf.processor } : null);
			_('quote-license').select2('data', conf.license ? { id: conf.license, text: formatLicense(conf.license) } : null);
			require(['jquery-ui'], function () {
				$('#quote-ram-adjust').slider({
					value: conf.ramAdjustedRate,
				});
			});
			_('quote-usage').select2('data', conf.usage)
		},

		/**
		 * Redraw all tables
		 */
		redrawAll: function () {
			current.types.forEach(type => {
				_('prov-' + type + 's').DataTable().clear().draw(false);
			});
		},

		/**
		 * Request to refresh the cost and trigger a global update as needed.
		 */
		refreshCost: function () {
			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/refresh',
				dataType: 'json',
				success: current.reloadAsNeed
			});
		},

		/**
		 * Reload the whole quote if the new cost is different from the previous one.
		 * @param {object} newCost The new cost.
		 * @param {function} forceUpdateUi When true, the UI is always refreshed, even when the cost has not been updated.
		 */
		reloadAsNeed: function (newCost, forceUpdateUi) {
			if (newCost.min !== current.model.configuration.cost.min || newCost.max !== current.model.configuration.cost.max) {
				// The cost has been updated
				current.model.configuration.cost = newCost;
				notifyManager.notify(current.$messages['service:prov:refresh-needed']);
				current.reload();
			} else {
				// The cost still the same
				notifyManager.notify(current.$messages['service:prov:refresh-no-change']);
				if (forceUpdateUi) {
					current.updateUiCost();
				}
			}
		},

		/**
		 * Render Provisioning management link.
		 */
		renderFeatures: function (subscription) {
			// Add quote configuration link
			var result = current.$super('renderServiceLink')('calculator', '#/home/project/' + subscription.project + '/subscription/' + subscription.id, 'service:prov:manage');

			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:prov:help');
			return result;
		},

		/**
		 * Display the cost of the quote.
		 */
		renderDetailsFeatures: function (subscription) {
			if (subscription.data.quote && (subscription.data.quote.cost.min || subscription.data.quote.cost.max)) {
				subscription.data.quote.cost.currency = subscription.data.quote.currency;
				var price = formatCost(subscription.data.quote.cost, null, null, true);
				return '<span data-toggle="tooltip" title="' + current.$messages['service:prov:cost-help'] + ' : ' + price + '" class="price label label-default">' + price + '</span>';
			}
		},

		/**
		 * Render provisioning details : cpu, ram, nbVm, storages.
		 */
		renderDetailsKey: function (subscription) {
			var quote = subscription.data.quote;
			var resources = [];
			if (quote.nbInstances) {
				resources.push('<span class="sub-item">' + current.$super('icon')('server', 'service:prov:nb-instances') + quote.nbInstances + ' VM</span>');
			}
			if (quote.nbDatabases) {
				resources.push('<span class="sub-item">' + current.$super('icon')('database', 'service:prov:nb-databases') + quote.nbDatabases + ' DB</span>');
			}
			if (quote.nbInstances || quote.nbDatabases) {
				resources.push('<span class="sub-item">' + current.$super('icon')('bolt', 'service:prov:total-cpu') + quote.totalCpu + ' ' + current.$messages['service:prov:cpu'] + '</span>');
				resources.push('<span class="sub-item">' + current.$super('icon')('memory', 'service:prov:total-ram') + formatRam(quote.totalRam) + '</span>');
			}
			if (quote.nbPublicAccess) {
				resources.push('<span class="sub-item">' + current.$super('icon')('globe', 'service:prov:nb-public-access') + quote.nbPublicAccess + '</span>');
			}
			if (quote.totalStorage) {
				resources.push('<span class="sub-item">' + current.$super('icon')('fas fa-hdd', 'service:prov:total-storage') + formatStorage(quote.totalStorage) + '</span>');
			}

			return current.$super('generateCarousel')(subscription, [
				['name', quote.name],
				['service:prov:resources', resources.join(', ')],
				['service:prov:location', current.$super('icon')('map-marker-alt', 'service:prov:location') + locationToHtml(quote.location, true)]
			], 1);
		},

		/**
		 * Configure Odometer components
		 */
		initOdometer: function () {
			var $cost = $('.cost');
			var weightUnit = '<span class="cost-weight"></span><span class="cost-unit"></span>';
			$cost.append('<span class="cost-min hidden"><span class="odo-wrapper cost-value"></span>' + weightUnit + '<span class="cost-separator">-</span></span>');
			$cost.append('<span class="cost-max"><span class="odo-wrapper cost-value"></span>' + weightUnit + '</span>');
			require(['../main/service/prov/lib/odometer', 'domReady'], function (Odometer) {
				// Odometer component
				current.registerOdometer(Odometer, $('#service-prov-menu').find('.odo-wrapper'));
				current.updateUiCost();
			});
		},
		registerOdometer: function (Odometer, $container) {
			$container.each(function () {
				new Odometer({
					el: $(this)[0],
					theme: 'minimal',
					duration: 0
				}).render();
			});
		},

		/**
		 * Associate the storages to the instances
		 */
		optimizeModel: function () {
			var conf = current.model.configuration;
			var i, qi;

			// Usages by id
			conf.usagesById = {};
			var usages = conf.usages;
			for (i = 0; i < usages.length; i++) {
				var u = usages[i];
				// Optimize id access
				conf.usagesById[u.id] = u;
			}

			// Tags case issue
			var tags = current.model && current.model.configuration.tags || {};
			Object.keys(tags).forEach(type => {
				let tagT = tags[type];
				delete tags[type];
				tags[type.toLowerCase()] = tagT;
			});

			// Instances
			conf.instancesById = {};
			conf.instanceCost = 0;
			var instances = conf.instances;
			for (i = 0; i < instances.length; i++) {
				qi = instances[i];
				if (qi.usage) {
					qi.usage = conf.usagesById[qi.usage];
				}
				// Optimize id access
				conf.instancesById[qi.id] = qi;
				conf.instanceCost += qi.cost;
			}

			// Databases
			conf.databasesById = {};
			conf.databaseCost = 0;
			var databases = conf.databases;
			for (i = 0; i < databases.length; i++) {
				qi = databases[i];
				if (qi.usage) {
					qi.usage = conf.usagesById[qi.usage];
				}
				// Optimize id access
				conf.databasesById[qi.id] = qi;
				conf.databaseCost += qi.cost;
			}

			// Storage
			conf.storagesById = {};
			conf.storageCost = 0;
			var storages = conf.storages;
			for (i = 0; i < storages.length; i++) {
				var qs = storages[i];
				conf.storageCost += qs.cost;
				current.attachStorage(qs, 'instance', qs.quoteInstance, true);
				current.attachStorage(qs, 'database', qs.quoteDatabase, true);

				// Optimize id access
				conf.storagesById[qs.id] = qs;
			}

			// Locations
			conf.locationsById = {};
			var locations = conf.locations;
			for (i = 0; i < locations.length; i++) {
				var loc = locations[i];
				// Optimize id access
				conf.locationsById[loc.name] = loc;
			}

			// Support
			conf.supportsById = {};
			conf.supportCost = 0;
			var supports = conf.supports;
			for (i = 0; i < supports.length; i++) {
				var qs2 = supports[i];
				// Optimize id access
				conf.supportsById[qs2.id] = qs2;
				conf.supportCost += qs2.cost;
			}
			current.initializeTerraformStatus();
		},

		/**
		 * Refresh the Terraform status embedded in the quote.
		 */
		initializeTerraformStatus: function () {
			if (current.model.configuration.terraformStatus) {
				current.updateTerraformStatus(current.model.configuration.terraformStatus, true);
				if (typeof current.model.configuration.terraformStatus.end === 'undefined') {
					// At least one snapshot is pending: track it
					setTimeout(function () {
						// Poll the unfinished snapshot
						current.pollStart('terraform-' + current.model.subscription, current.model.subscription, current.synchronizeTerraform);
					}, 10);
				}
			} else {
				_('prov-terraform-status').addClass('invisible');
				current.enableTerraform();
			}
		},

		/**
		 * Disable the create/update button
		 * @return the related button.
		 */
		disableCreate: function ($popup) {
			return $popup.find('input[type="submit"]').attr('disabled', 'disabled').addClass('disabled');
		},

		/**
		 * Enable the create/update button
		 * @return the related button.
		 */
		enableCreate: function ($popup) {
			return $popup.find('input[type="submit"]').removeAttr('disabled').removeClass('disabled');
		},

		/**
		 * Checks there is at least one instance matching to the requirement. 
		 * Uses the current "this" jQuery context to get the UI context.
		 */
		checkResource: function () {
			var $form = $(this).prov();
			var queries = {};
			var type = $form.provType();
			var popupType = (type == 'instance' || type == 'database') ? 'generic' : type;
			var $popup = _('popup-prov-' + popupType);

			// Build the query
			$form.find('.resource-query').filter(function () {
				return $(this).closest('[data-exclusive]').length === 0 || $(this).closest('[data-exclusive]').attr('data-exclusive') === type;
			}).each(function () {
				current.addQuery(type, $(this), queries);
				if (type === 'database') {
					// Also include the instance inputs
					current.addQuery('instance', $(this), queries);
				}
			});
			if (type === 'storage' && queries['instance'] && _('storage-instance').select2('data') && _('storage-instance').select2('data').resourceType === 'database') {
				// Replace the resource lookup
				queries['database'] = queries['instance'];
				delete queries['instance'];
			}
			var queriesArray = [];
			Object.keys(queries).forEach(q => queriesArray.push(q + '=' + queries[q]));

			// Check the availability of this instance for these requirements
			current.disableCreate($popup);
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/' + type + '-lookup/?' + queriesArray.join('&'),
				type: 'GET',
				success: function (suggest) {
					current[type + 'SetUiPrice'](suggest);
					if (suggest && (suggest.price || ($.isArray(suggest) && suggest.length))) {
						// The resource is valid, enable the create
						current.enableCreate($popup);
					}
				},
				error: function () {
					current.enableCreate($popup);
				}
			});
		},

		/**
		 * Return the memory query parameter value to use to filter some other inputs.
		 */
		toQueryValueRam: function (value) {
			return (cleanInt(value) || 0) * getRamUnitWeight();
		},

		addQuery(type, $item, queries) {
			var value = getResourceValue($item);
			var queryParam = value && toQueryName(type, $item);
			if (queryParam) {
				value = $item.is('[type="checkbox"]') ? $item.is(':checked') : value;
				var toValue = current['toQueryValue' + queryParam.capitalize()];
				value = toValue ? toValue(value, $item) : value;
				if (value || value === false) {
					// Add as query
					queries[queryParam] = encodeURIComponent(value);
				}
			}
		},

		/**
		 * Set the current storage price.
		 * @param {object|Array} Quote or prices
		 */
		storageSetUiPrice: function (quote) {
			var suggests = current.toSuggests(quote);
			if (suggests) {
				var suggest = suggests[0];
				_('storage-price').select2('destroy').select2({
					data: suggests,
					formatSelection: formatStoragePriceHtml,
					formatResult: formatStoragePriceHtml
				}).select2('data', suggest);
			} else {
				_('storage-price').select2('data', null);
			}
		},

		/**
		 * Set the current instance/database price.
		 */
		genericSetUiPrice: function (quote) {
			if (quote && quote.price) {
				var suggests = [quote];
				_('instance-price').select2('destroy').select2({
					data: suggests,
					formatSelection: function (qi) {
						return qi.price.type.name + ' (' + formatCost(qi.cost, null, null, true) + '/m)';
					},
					formatResult: function (qi) {
						return qi.price.type.name + ' (' + formatCost(qi.cost, null, null, true) + '/m)';
					}
				}).select2('data', quote);
				_('instance-term').select2('data', quote.price.term).val(quote.price.term.id);
			} else {
				_('instance-price').select2('data', null);
			}
		},

		/**
		 * Set the current instance price.
		 */
		instanceSetUiPrice: function (quote) {
			current.genericSetUiPrice(quote);
		},

		/**
		 * Set the current instance price.
		 */
		databaseSetUiPrice: function (quote) {
			current.genericSetUiPrice(quote);
		},

		/**
		 * Set the current support price.
		 */
		supportSetUiPrice: function (quote) {
			var suggests = current.toSuggests(quote);
			if (suggests) {
				var suggest = suggests[0];
				_('support-price').select2('destroy').select2({
					data: suggests,
					formatSelection: function (qi) {
						return qi.price.type.name + ' (' + formatCost(qi.cost, null, null, true) + '/m)';
					},
					formatResult: function (qi) {
						return qi.price.type.name + ' (' + formatCost(qi.cost, null, null, true) + '/m)';
					}
				}).select2('data', suggest);
			} else {
				_('support-price').select2('data', null);
			}
		},

		/**
		 * Return the suggests array from the quote parameter.
		 * @param {Quote|Quote[]} quote The quote data with their price.
		 * @returns The  suggests array. May be null when the quote parameter is not accepted.
		 */
		toSuggests: function (quote) {
			if (quote && (($.isArray(quote) && quote.length) || quote.price)) {
				var suggests = quote;
				if (!$.isArray(quote)) {
					// Single price
					suggests = [quote];
				}
				for (var i = 0; i < suggests.length; i++) {
					suggests[i].id = suggests[i].id || suggests[i].price.id;
				}
				return suggests;
			}
			return null;
		},

		/**
		 * Initialize data tables and popup event : delete and details
		 */
		initializeDataTableEvents: function (type) {
			var oSettings = current[type + 'NewTable']();
			var popupType = (type == 'instance' || type == 'database') ? 'generic' : type;
			var $table = _('prov-' + type + 's');
			$.extend(oSettings, {
				provType: type,
				data: current.model.configuration[type + 's'] || [],
				dom: 'Brt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				destroy: true,
				stateSave: true,
				deferRender: true,
				stateDuration: 0,
				stateLoadCallback: function (settings, callback) {
					try {
						var data = JSON.parse(localStorage.getItem('service:prov/' + type));
						settings.oPreviousSearch.sSearchAlt = data.searchAlt;
						current[type + 'TableFilter'] = data.searchAlt;
						return data;
					} catch (e) {
						// Ignore the state error log
						callback(null);
					}
				},
				stateLoadParams: function (settings) {
					$table.prov().find('.subscribe-configuration-prov-search').val(settings.oPreviousSearch.sSearchAlt || '');
				},
				stateSaveCallback: function (settings, data) {
					try {
						data.searchAlt = settings.oPreviousSearch.sSearchAlt;
						localStorage.setItem('service:prov/' + type, JSON.stringify(data));
					} catch (e) {
						// Ignore the state error log
					}
				},

				searching: true,
				createdRow: (nRow, data) => $(nRow).attr('data-id', data.id),
				buttons: [{
					extend: 'colvis',
					postfixButtons: ['colvisRestore'],
					columns: ':not(.noVis)',
					columnText: (dt, idx) => dt.settings()[0].aoColumns[idx].sTitle,
				}],
				columnDefs: [{
					targets: 'noVisDefault',
					visible: false
				}],
				language: {
					buttons: {
						colvis: '<i class="fas fa-cog"></i>',
						colvisRestore: current.$messages['restore-visibility']
					}
				},
			});
			oSettings.columns.splice(0, 0, {
				data: 'name',
				className: 'truncate'
			});
			oSettings.columns.push(
				{
					data: null,
					orderable: false,
					className: 'truncate hidden-xs',
					type: 'string',
					filterName : 'tags',
					render: current.tagManager.render
				}, {
				data: 'cost',
				className: 'truncate hidden-xs',
				type: 'num',
				render: formatCost
			}, {
				data: null,
				width: '51px',
				orderable: false,
				searchable: false,
				type: 'string',
				render: function () {
					return `<a class="update" data-toggle="modal" data-target="#popup-prov-${popupType}"><i class="fas fa-pencil-alt" data-toggle="tooltip" title="${current.$messages.update}"></i></a>`
						+ `<a class="network" data-toggle="modal-ajax" data-cascade="true" data-ajax="/main/home/project/network" data-plugins="css,i18n,html,js" data-target="#popup-prov-network"><i class="fas fa-link" data-toggle="tooltip" title="${current.$messages['service:prov:network']}"></i></a>`
						+ `<a class="delete"><i class="fas fa-trash-alt" data-toggle="tooltip" title="${current.$messages.delete}"></i></a>`;
				}
			});
			$table.on('column-visibility.dt', function (e, settings, idCOl, visibility) {
				if (visibility) {
					$(this).DataTable().draw('page');
				}
			})
			current[type + 'Table'] = $table.dataTable(oSettings);
		},

		initializeUpload: function () {
			var $popup = _('popup-prov-instance-import');
			_('csv-headers-included').on('change', function () {
				if ($(this).is(':checked')) {
					// Useless input headers
					_('csv-headers').closest('.form-group').addClass('hidden');
				} else {
					_('csv-headers').closest('.form-group').removeClass('hidden');
				}
			});
			$popup.on('shown.bs.modal', function () {
				_('csv-file').trigger('focus');
			}).on('show.bs.modal', function () {
				$('.import-summary').addClass('hidden');
			}).on('submit', function (e) {
				// Avoid useless empty optional inputs
				_('instance-usage-upload-name').val((_('instance-usage-upload').select2('data') || {}).name || null);
				_('csv-headers-included').val(_('csv-headers-included').is(':checked') ? 'true' : 'false');
				$popup.find('input[type="text"]').not('[readonly]').not('.select2-focusser').not('[disabled]').filter(function () {
					return $(this).val() === '';
				}).attr('disabled', 'disabled').attr('readonly', 'readonly').addClass('temp-disabled').closest('.select2-container').select2('enable', false);
				$(this).ajaxSubmit({
					url: REST_PATH + 'service/prov/' + current.model.subscription + '/upload',
					type: 'POST',
					dataType: 'json',
					beforeSubmit: function () {
						// Reset the summary
						current.disableCreate($popup);
						validationManager.reset($popup);
						validationManager.mapping.DEFAULT = 'csv-file';
						$('.import-summary').html('Processing...');
						$('.loader-wrapper').removeClass('hidden');
					},
					success: function () {
						$popup.modal('hide');

						// Refresh the data
						current.reload();
					},
					complete: function () {
						$('.loader-wrapper').addClass('hidden');
						$('.import-summary').html('').addClass('hidden');
						// Restore the optional inputs
						$popup.find('input.temp-disabled').removeAttr('disabled').removeAttr('readonly').removeClass('temp-disabled').closest('.select2-container').select2('enable', true);
						current.enableCreate($popup);
					}
				});
				e.preventDefault();
				return false;
			});
		},

		initializeForm: function () {
			// Global datatables filter
			if ($.fn.dataTable.ext.search.length === 0) {
				$.fn.dataTable.ext.search.push(
					function (settings, dataFilter, dataIndex, data) {
						var type = settings.oInit.provType;
						// Save the last used filter
						if (typeof type === 'undefined') {
							return true;
						}

						var filter = settings.oPreviousSearch.sSearchAlt || '';
						if (type === 'storage' && (current.databaseTableFilter !== '' || current.instanceTableFilter !== '')) {
							// Only storage rows unrelated to filtered instance/database can be displayed
							// There are 2 operators: 
							// - 'in' = 's.instance NOT NULL AND s.instance IN (:table)'
							// - 'lj' = 's.instance IS NULL OR s.instance IN (:table)'
							return current.filterManager.accept(settings, type, dataFilter, data, filter, {
								cache: current.databaseTableFilter + '/' + current.instanceTableFilter, filters: [
									{ property: 'quoteDatabase', op: 'in', table: current.databaseTableFilter && current.databaseTable },
									{ property: 'quoteInstance', op: 'in', table: current.instanceTableFilter && current.instanceTable },
								]
							});
						}
						if (filter === '') {
							return true;
						}
						return current.filterManager.accept(settings, type, dataFilter, data, filter, {});
					}
				);
			}
			$.fn.extend({
				prov: function () {
					return $(this).closest('[data-prov-type]');
				},
				provType: function () {
					return $(this).prov().attr('data-prov-type');
				}
			});

			$('.subscribe-configuration-prov-search').on('keyup', delay(function (event) {
				if (event.which !== 16 && event.which !== 91) {
					var type = $(this).provType();
					var table = current[type + 'Table'];
					if (table) {
						table.fnSettings().oPreviousSearch.sSearch = '§force§';
						var filter = $(this).val()
						table.fnSettings().oPreviousSearch.sSearchAlt = filter;
						current[type + 'TableFilter'] = filter;
						table.fnFilter('');

						if (type === 'instance' || type === 'database') {
							// Refresh the storage
							var tableS = current['storageTable'];
							tableS.fnSettings().oPreviousSearch.sSearch = '§force§';
							tableS.fnFilter('');
						}
						current.updateUiCost();
					}
				}
			}, 200));
			current.types.forEach(type => current[type + 'TableFilter'] = '');
			current.types.forEach(type => {
				current.initializeDataTableEvents(type);
				if (type !== 'database') {
					initializePopupEvents(type);
				}
			});
			$('#subscribe-configuration-prov table').on('click', '.delete', function () {
				// Delete a single row/item
				var type = $(this).provType();
				var dataTable = current[type + 'Table'];
				var resource = dataTable.fnGetData($(this).closest('tr')[0]);
				$.ajax({
					type: 'DELETE',
					url: `${REST_PATH}service/prov/${type}/${resource.id}`,
					success: updatedCost => current.defaultCallback(type, updatedCost)
				});
			}).on('click', '.delete-all', function () {
				// Delete all items
				var type = $(this).provType();
				$.ajax({
					type: 'DELETE',
					url: `${REST_PATH}service/prov/${current.model.subscription}/${type}`,
					success: updatedCost => current.defaultCallback(type, updatedCost)
				});
			});

			$('.quote-name').text(current.model.configuration.name);

			_('popup-prov-update').on('shown.bs.modal', function () {
				_('quote-name').trigger('focus');
			}).on('submit', function (e) {
				e.preventDefault();
				current.updateQuote(copyToData($(this), 'quote-', {}));
			}).on('show.bs.modal', function () {
				copyToUi($(this), 'quote-', current.model.configuration);
			});

			$('.cost-refresh').on('click', current.refreshCost);
			current.initializeTerraform();
			initializeUsage();
			current.initializeOtherAssumptionsComponents();
			current.updateUiAssumptions(current.model.configuration);
		},

		/**
		 * Configure RAM adjust rate.
		 */
		initializeOtherAssumptionsComponents: function () {
			_('quote-location').select2(current.locationSelect2(false)).on('change', function (event) {
				if (event.added) {
					current.updateQuote({
						location: event.added
					}, { name: 'location', ui: 'quote-location', previous: event.removed });
					$('.location-wrapper').html(locationMap(event.added));
				}
			});
			$('.location-wrapper').html(locationMap(current.model.configuration.location));
			_('quote-license').select2(genericSelect2(current.$messages['service:prov:license-included'], formatLicense, () => 'instance-license/WINDOWS'))
				.on('change', function (event) {
					current.updateQuote({
						license: event.added || null
					}, { name: 'license', ui: 'quote-license', previous: event.removed }, true);
				});
			_('quote-reservation-mode').select2({
				placeholder: 'Reserved',
				escapeMarkup: m => m,
				data: [{ id: 'max', text: formatReservationMode('max') }, { id: 'reserved', text: formatReservationMode('reserved') }]
			}).on('change', function (event) {
				current.updateQuote({
					reservationMode: event.added || null
				}, { name: 'reservationMode', ui: 'quote-reservation-mode', previous: event.removed }, true);
			});
			_('quote-processor').select2(newProcessorOpts()).on('change', function (event) {
				current.updateQuote({
					processor: event.added || null
				}, { name: 'processor', ui: 'quote-processor', previous: event.removed }, true);
			});

			_('quote-physical').on('click', 'li', function () {
				$.proxy(synchronizeDropdownText, $(this))();
				current.updateQuote({
					physical: toQueryValue3States($(this))
				}, { name: 'physical', ui: 'quote-physical', previous: current.model.configuration.physical }, true);
			});

			require(['jquery-ui'], function () {
				var handle = $('#quote-ram-adjust-handle');
				$('#quote-ram-adjust').slider({
					min: 50,
					max: 150,
					animate: true,
					step: 5,
					value: current.model.configuration.ramAdjustedRate,
					create: function () {
						handle.text($(this).slider('value') + '%');
					},
					slide: (_, ui) => handle.text(ui.value + '%'),
					change: function (event, slider) {
						if (event.currentTarget && slider.value !== current.model.configuration.ramAdjustedRate) {
							current.updateQuote({
								ramAdjustedRate: slider.value
							}, { name: 'ramAdjustedRate', ui: 'quote-ram-adjust', previous: current.model.configuration.ramAdjustedRate }, true);
						}
					}
				});
			});
		},

		/**
		 * Configure Terraform.
		 */
		initializeTerraform: function () {
			_('prov-terraform-download').attr('href', `${REST_PATH}service/prov/${current.model.subscription}/terraform-${current.model.subscription}.zip`);
			_('prov-terraform-status').find('.terraform-logs a').attr('href', `${REST_PATH}service/prov/${current.model.subscription}/terraform.log`);
			_('popup-prov-terraform')
				.on('shown.bs.modal', () => _('terraform-cidr').trigger('focus'))
				.on('show.bs.modal', function () {
					if (_('terraform-key-name').val() === "") {
						_('terraform-key-name').val(current.$parent.model.pkey);
					}

					// Target platform
					current.updateTerraformTarget();
				}).on('submit', current.terraform);
			_('popup-prov-terraform-destroy')
				.on('shown.bs.modal', () => _('terraform-confirm-destroy').trigger('focus'))
				.on('show.bs.modal', function () {
					current.updateTerraformTarget();
					_('terraform-destroy-confirm').val('').trigger('change');
					$('.terraform-destroy-alert').html(Handlebars.compile(current.$messages['service:prov:terraform:destroy-alert'])(current.$parent.model.pkey));
				}).on('submit', function () {
					// Delete only when exact match
					if (_('terraform-destroy-confirm').val() === current.$parent.model.pkey) {
						current.terraformDestroy();
					}
				});

			// Live state ot Terraform destroy buttons
			_('terraform-destroy-confirm').on('change keyup', function () {
				if (_('terraform-destroy-confirm').val() === current.$parent.model.pkey) {
					_('popup-prov-terraform-destroy').find('input[type="submit"]').removeClass('disabled');
				} else {
					_('popup-prov-terraform-destroy').find('input[type="submit"]').addClass('disabled');
				}
			})

			// render the dashboard
			current.$super('requireTool')(current.$parent, current.model.node.id, function ($tool) {
				var $dashboard = _('prov-terraform-status').find('.terraform-dashboard');
				if ($tool && $tool.dashboardLink) {
					$dashboard.removeClass('hidden').find('a').attr('href', $tool.dashboardLink(current.model));
				} else {
					$dashboard.addClass('hidden');
				}
			});
		},

		synchronizeUsage: function () {
			var $input = $(this);
			var id = $input.attr('id');
			var val = $input.val();
			var percent; // [1-100]
			if (val) {
				val = parseInt(val, 10);
				if (id === 'usage-month') {
					percent = val / 7.32;
				} else if (id === 'usage-week') {
					percent = val / 1.68;
				} else if (id === 'usage-day') {
					percent = val / 0.24;
				} else if (id === 'usage-template') {
					percent = _('usage-template').select2('data').id;
				} else {
					percent = val;
				}
				if (id !== 'usage-day') {
					_('usage-day').val(Math.ceil(percent * 0.24));
				}
				if (id !== 'usage-month') {
					_('usage-month').val(Math.ceil(percent * 7.32));
				}
				if (id !== 'usage-week') {
					_('usage-week').val(Math.ceil(percent * 1.68));
				}
				if (id !== 'usage-rate') {
					_('usage-rate').val(Math.ceil(percent));
				}
				if (id !== 'usage-template') {
					_('usage-template').select2('data',
						current.usageTemplates[Math.ceil(percent)]
						|| current.usageTemplates[Math.ceil(percent - 1)]
						|| null);
				}
				current.updateD3UsageRate(percent);
			}
		},

		/**
		 * Location Select2 configuration.
		 */
		locationSelect2: function (placeholder) {
			return genericSelect2(placeholder, locationToHtml, 'location', null, locationComparator, locationMatcher);
		},

		/**
		 * Usage Select2 configuration.
		 */
		usageSelect2: function (placeholder) {
			return genericSelect2(placeholder, current.usageToText, 'usage', function (usage) {
				return `${usage.name}<span class="select2-usage-summary pull-right"><span class="x-small">(${usage.rate}%) </span><a class="update prov-usage-select2-action"><i data-toggle="tooltip" title="${current.$messages.update}" class="fas fa-fw fa-pencil-alt"></i><a></span>`;
			});
		},

		/**
		 * Price term Select2 configuration.
		 */
		instanceTermSelect2: function () {
			return genericSelect2(current.$messages['service:prov:default'], current.defaultToText, 'instance-price-term');
		},

		/**
		 * Interval identifiers for polling
		 */
		polling: {},

		/**
		 * Stop the timer for polling
		 */
		pollStop: function (key) {
			if (current.polling[key]) {
				clearInterval(current.polling[key]);
			}
			delete current.polling[key];
		},

		/**
		 * Timer for the polling.
		 */
		pollStart: function (key, id, synchronizeFunction) {
			current.polling[key] = setInterval(function () {
				synchronizeFunction(key, id);
			}, 1000);
		},

		/**
		 * Get the new Terraform status.
		 */
		synchronizeTerraform: function (key, subscription) {
			current.pollStop(key);
			current.polling[key] = '-';
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/' + subscription + '/terraform',
				type: 'GET',
				success: function (status) {
					current.updateTerraformStatus(status);
					if (status.end) {
						return;
					}
					// Continue polling for this Terraform
					current.pollStart(key, subscription, current.synchronizeTerraform);
				}
			});
		},

		/**
		 * Update the Terraform status.
		 */
		updateTerraformStatus: function (status, reset) {
			if (status.end) {
				// Stop the polling, update the buttons
				current.enableTerraform();
			} else {
				current.disableTerraform();
			}
			require(['../main/service/prov/terraform'], function (terraform) {
				terraform[reset ? 'reset' : 'update'](status, _('prov-terraform-status'), current.$messages);
			});
		},

		enableTerraform: function () {
			_('prov-terraform-execute').enable();
		},
		disableTerraform: function () {
			_('prov-terraform-execute').disable();
		},

		/**
		 * Update the Terraform target data.
		 */
		updateTerraformTarget: function () {
			var target = ['<strong>' + current.$messages.node + '</strong>&nbsp;' + current.$super('toIcon')(current.model.node, null, null, true) + ' ' + current.$super('getNodeName')(current.model.node)];
			target.push('<strong>' + current.$messages.project + '</strong>&nbsp;' + current.$parent.model.pkey);
			$.each(current.model.parameters, function (parameter, value) {
				target.push('<strong>' + current.$messages[parameter] + '</strong>&nbsp;' + value);
			});
			$('.terraform-target').html(target.join('<br>'));
		},

		/**
		 * Execute the terraform destroy
		 */
		terraformDestroy: function () {
			current.terraformAction(_('popup-prov-terraform-destroy'), {}, 'DELETE');
		},

		/**
		 * Execute the terraform action.
		 * @param {jQuery} $popup The JQuery popup source to hide on success.
		 * @param {Number} context The Terraform context.
		 * @param {Number} method The REST method.
		 */
		terraformAction: function ($popup, context, method) {
			var subscription = current.model.subscription;
			current.disableTerraform();

			// Complete the Terraform inputs
			var data = {
				context: context
			}
			$.ajax({
				type: method,
				url: REST_PATH + 'service/prov/' + subscription + '/terraform',
				dataType: 'json',
				data: JSON.stringify(data),
				contentType: 'application/json',
				success: function () {
					notifyManager.notify(current.$messages['service:prov:terraform:started']);
					setTimeout(function () {
						// Poll the unfinished Terraform
						current.pollStart('terraform-' + subscription, subscription, current.synchronizeTerraform);
					}, 10);
					$popup.modal('hide');
					current.updateTerraformStatus({}, true);
				},
				error: () => current.enableTerraform()
			});
		},

		/**
		 * Execute the terraform deployment
		 */
		terraform: function () {
			current.terraformAction(_('popup-prov-terraform'), {
				'key_name': _('terraform-key-name').val(),
				'private_subnets': '"' + $.map(_('terraform-private-subnets').val().split(','), s => s.trim()).join('","') + '"',
				'public_subnets': '"' + $.map(_('terraform-public-subnets').val().split(','), s => s.trim()).join('","') + '"',
				'public_key': _('terraform-public-key').val(),
				'cidr': _('terraform-cidr').val()
			}, 'POST');
		},

		/**
		 * Update the auto-scale  flag from the provided quantities.
		 */
		updateAutoScale: function () {
			var min = _('instance-min-quantity').val();
			min = min && parseInt(min, 10);
			var max = _('instance-max-quantity').val();
			max = max && parseInt(max, 10);
			if (min && max !== '' && min > max) {
				max = min;
				_('instance-max-quantity').val(min);
			}
			_('instance-auto-scale').prop('checked', (min !== max));
		},

		/**
		 * Update the quote's details. If the total cost is updated, the quote will be updated.
		 * @param {object} data The optional data overriding the on from the model.
		 * @param {String} context The optional context with property, field and previous value. Used for the feedback UI and the rollback.
		 * @param {function } forceUpdateUi When true, the UI is always refreshed, even when the cost has not been updated.
		 */
		updateQuote: function (data, context, forceUpdateUi) {
			var conf = current.model.configuration;
			var $popup = _('popup-prov-update');

			// Build the new data
			var jsonData = $.extend({
				name: conf.name,
				description: conf.description,
				location: conf.location,
				license: conf.license,
				processor: conf.processor,
				physical: conf.physical,
				reservationMode: conf.reservationMode,
				ramAdjustedRate: conf.ramAdjustedRate || 100,
				usage: conf.usage
			}, data || {});
			jsonData.location = jsonData.location.name || jsonData.location;

			if (jsonData.processor) {
				jsonData.processor = jsonData.processor.id || jsonData.processor;
			}
			if (jsonData.reservationMode) {
				jsonData.reservationMode = jsonData.reservationMode.id || jsonData.reservationMode;
			}
			if (jsonData.license) {
				jsonData.license = jsonData.license.id || jsonData.license;
			}
			if (jsonData.usage) {
				jsonData.usage = jsonData.usage.name;
			} else {
				delete jsonData.usage;
			}

			// Check the changes
			if (conf.name === jsonData.name
				&& conf.description === jsonData.description
				&& (conf.location && conf.location.name) === jsonData.location
				&& (conf.usage && conf.usage.name) === jsonData.usage
				&& conf.license === jsonData.license
				&& conf.processor === jsonData.processor
				&& conf.physical === jsonData.physical
				&& conf.reservationMode === jsonData.reservationMode
				&& conf.ramAdjustedRate === jsonData.ramAdjustedRate) {
				// No change
				$popup.modal('hide');
				return;
			}

			current.disableCreate($popup);
			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/' + current.model.subscription,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(jsonData),
				beforeSend: () => $('.loader-wrapper').removeClass('hidden'),
				complete: () => $('.loader-wrapper').addClass('hidden'),
				success: function (newCost) {
					// Update the UI
					$('.quote-name').text(jsonData.name);

					// Commit to the model
					conf.name = jsonData.name;
					conf.description = jsonData.description;
					conf.location = data.location || conf.location;
					conf.usage = data.usage || conf.usage;
					conf.license = jsonData.license;
					conf.processor = jsonData.processor;
					conf.physical = jsonData.physical;
					conf.reservationMode = jsonData.reservationMode;
					conf.ramAdjustedRate = jsonData.ramAdjustedRate;

					// UI feedback
					$popup.modal('hide');
					current.enableCreate($popup);

					// Handle updated cost
					if (context) {
						current.reloadAsNeed(newCost, forceUpdateUi);
					} else if (forceUpdateUi) {
						current.updateUiCost();
					}
				},
				error: function () {
					if (context) {
						var eMsg = current.$messages['service:prov:' + context.name + '-failed'];
						var value = data[context.name].name || data[context.name].text || data[context.name].id || data[context.name];
						if (eMsg) {
							notifyManager.notifyDanger(Handlebars.compile(eMsg)(value));
						} else {
							// Unmanaged UI error
							notifyManager.notifyDanger(Handlebars.compile(current.$messages['service:prov:default-failed'])({ name: context.name, value: value }));
						}
						// Restore the old UI property value
						let $ui = _(context.ui);
						if ($ui.prev('.select2-container').length) {
							// Select2 input
							$ui.select2('data', context.previous);
						} else if ($ui.is('.input-group-btn')) {
							// Three state input
							update3States($ui, context.previous);
						} else {
							// Simple input
							$ui.val(context.previous);
						}
					}
					current.enableCreate($popup);
				}
			});
		},

		/**
		 * Delete an usage by its name.
		 * @param {string} id The usage identifier to delete.
		 */
		deleteUsage: function (id) {
			var conf = current.model.configuration;
			var name = conf.usagesById[id].name;
			var $popup = _('popup-prov-usage');
			current.disableCreate($popup);
			$.ajax({
				type: 'DELETE',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/usage/' + id,
				dataType: 'json',
				contentType: 'application/json',
				success: function (newCost) {
					// Commit to the model
					if (conf.usage && conf.usage.id === id) {
						// Update the usage of the quote
						delete conf.usage;
						_('prov-usage').select2('data', null);
					}
					delete conf.usagesById[id];

					// UI feedback
					notifyManager.notify(Handlebars.compile(current.$messages.deleted)(name));
					$popup.modal('hide');

					// Handle updated cost
					current.reloadAsNeed(newCost, true);
				},
				error: () => current.enableCreate($popup)
			});
		},

		/**
		 * Update a quote usage.
		 * @param {string} data The usage data to persist.
		 */
		saveOrUpdateUsage: function (data) {
			var conf = current.model.configuration;
			var $popup = _('popup-prov-usage');
			current.disableCreate($popup);
			var method = data.id ? 'PUT' : 'POST'
			$.ajax({
				type: method,
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/usage',
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (newCost) {
					// Commit to the model
					data.id = data.id || newCost.id || newCost;
					if (conf.usagesById[data.id]) {
						Object.assign(conf.usagesById[data.id], data);
					} else {
						conf.usagesById[data.id] = data;
					}
					if (conf.usage && conf.usage.id === data.id) {
						// Update the usage of the quote
						conf.usage = data;
						_('quote-usage').select2('data', data);
					}

					// UI feedback
					notifyManager.notify(Handlebars.compile(current.$messages[method === 'PUT' ? 'updated' : 'created'])(data.name));
					$popup.modal('hide');

					// Handle updated cost
					if (newCost.total) {
						current.reloadAsNeed(newCost.total, true);
					}
				},
				error: () => current.enableCreate($popup)
			});
		},

		/**
		 * Location text renderer.
		 */
		usageToText: function (usage) {
			return usage.text || (usage.name + '<span class="pull-right">(' + usage.rate + '%)<span>');
		},

		/**
		 * Price term text renderer.
		 */
		defaultToText: function (term) {
			return term.text || term.name || term;
		},

		/**
		 * Redraw an resource table row from its identifier
		 * @param {String} type Resource type : 'instance', 'storage'.
		 * @param {number|Object} resourceOrId Quote resource or its identifier.
		 */
		redrawResource: function (type, resourceOrId) {
			var id = resourceOrId && (resourceOrId.id || resourceOrId);
			if (id) {
				// The instance is valid
				_('prov-' + type + 's').DataTable().rows((_, data) => data.id === id).invalidate().draw(false);
			}
		},

		storageCommitToModel: function (data, model) {
			model.size = parseInt(data.size, 10);
			model.latency = data.latency;
			model.optimized = data.optimized;
			// Update the attachment
			current.attachStorage(model, 'instance', data.quoteInstance);
			current.attachStorage(model, 'database', data.quoteDatabase);
		},

		supportCommitToModel: function (data, model) {
			model.seats = parseInt(data.seats, 10);
			model.level = data.level;
			model.accessApi = data.accessApi;
			model.accessPhone = data.accessPhone;
			model.accessChat = data.accessChat;
			model.accessEmail = data.accessEmail;
		},

		genericCommitToModel: function (data, model) {
			model.cpu = parseFloat(data.cpu, 10);
			model.ram = parseInt(data.ram, 10);
			model.internet = data.internet;
			model.minQuantity = parseInt(data.minQuantity, 10);
			model.maxQuantity = data.maxQuantity ? parseInt(data.maxQuantity, 10) : null;
			model.constant = data.constant;
			model.physical = data.physical;
		},

		instanceCommitToModel: function (data, model) {
			current.genericCommitToModel(data, model);
			model.maxVariableCost = parseFloat(data.maxVariableCost, 10);
			model.ephemeral = data.ephemeral;
			model.os = data.os;
		},

		databaseCommitToModel: function (data, model) {
			current.genericCommitToModel(data, model);
			model.engine = data.engine;
			model.edition = data.edition;
		},

		storageUiToData: function (data) {
			delete data.quoteInstance;
			delete data.quoteDatabase;
			if (_('storage-instance').select2('data')) {
				if (_('storage-instance').select2('data').resourceType === 'database') {
					data.quoteDatabase = (_('storage-instance').select2('data') || {}).id;
				} else {
					data.quoteInstance = (_('storage-instance').select2('data') || {}).id;
				}
			}
			data.size = cleanInt(_('storage-size').val());
			data.optimized = _('storage-optimized').val();
			data.latency = _('storage-latency').val();
			data.type = _('storage-price').select2('data').price.type.name;
		},

		supportUiToData: function (data) {
			data.seats = cleanInt(_('support-seats').val());
			data.accessApi = (_('support-access-api').select2('data') || {}).id || null;
			data.accessEmail = (_('support-access-email').select2('data') || {}).id || null;
			data.accessPhone = (_('support-access-phone').select2('data') || {}).id || null;
			data.accessChat = (_('support-access-chat').select2('data') || {}).id || null;
			data.level = (_('support-level').select2('data') || {}).id || null;
			data.type = _('support-price').select2('data').price.type.name;
		},

		genericUiToData: function (data) {
			data.cpu = cleanFloat(_('instance-cpu').provSlider('value', 'reserved'));
			data.cpuMax = cleanFloat(_('instance-cpu').provSlider('value', 'max'));
			data.ram = cleanRam('reserved');
			data.ramMax = cleanRam('max');
			data.internet = _('instance-internet').val().toLowerCase();
			data.processor = _('instance-processor').val().toLowerCase();
			data.minQuantity = cleanInt(_('instance-min-quantity').val()) || 0;
			data.maxQuantity = cleanInt(_('instance-max-quantity').val()) || null;
			data.license = _('instance-license').val().toLowerCase() || null;
			data.constant = toQueryValue3States(_('instance-constant'));
			data.physical = toQueryValue3States(_('instance-physical'));
			data.price = _('instance-price').select2('data').price.id;
		},

		instanceUiToData: function (data) {
			current.genericUiToData(data)
			data.maxVariableCost = cleanFloat(_('instance-max-variable-cost').val());
			data.ephemeral = _('instance-ephemeral').is(':checked');
			data.os = _('instance-os').val().toLowerCase();
			data.software = _('instance-software').val().toLowerCase() || null;
		},

		databaseUiToData: function (data) {
			current.genericUiToData(data)
			data.engine = _('database-engine').val().toUpperCase();
			data.edition = _('database-edition').val().toUpperCase() || null;
		},

		/**
		 * Fill the popup from the model
		 * @param {string} type, The entity type (instance/storage)
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		toUi: function (type, model) {
			var popupType = (type == 'instance' || type == 'database') ? 'generic' : type;
			var inputType = (type == 'instance' || type == 'database') ? 'instance' : type;
			var $popup = _('popup-prov-' + popupType);
			validationManager.reset($popup);
			_(inputType + '-name').val(model.name || current.findNewName(current.model.configuration[type + 's'], type));
			_(inputType + '-description').val(model.description || '');
			_(inputType + '-location').select2('data', model.location || null);
			_(inputType + '-usage').select2('data', model.usage || null);
			$popup.attr('data-prov-type', type);
			current[type + 'ToUi'](model);
			$.proxy(current.checkResource, $popup)();
		},

		/**
		 * Fill the instance popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		genericToUi: function (quote) {
			current.adaptRamUnit(quote.ram || 2048);
			_('instance-processor').select2('data', current.select2IdentityData(quote.processor || null));
			_('instance-cpu').provSlider($.extend(maxOpts, { format: formatCpu, max: 128 })).provSlider('value', [quote.cpuMax || false, quote.cpu || 1]);
			_('instance-ram').provSlider($.extend(maxOpts, { format: v => formatRam(v * getRamUnitWeight()), max: 1024 })).provSlider('value', [quote.ramMax ? Math.max(1, Math.round(quote.ramMax / 1024)) : false, Math.max(1, Math.round((quote.ram || 1024) / 1024))]);
			_('instance-min-quantity').val((typeof quote.minQuantity === 'number') ? quote.minQuantity : (quote.id ? 0 : 1));
			_('instance-max-quantity').val((typeof quote.maxQuantity === 'number') ? quote.maxQuantity : (quote.id ? '' : 1));
			var license = (quote.id && (quote.license || quote.price.license)) || null;
			_('instance-license').select2('data', license ? {
				id: license,
				text: formatLicense(license)
			} : null);

			// Update the CPU constraint
			update3States(_('instance-constant'), quote.constant);
			update3States(_('instance-physical'), quote.physical);
		},

		/**
		 * Fill the instance popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		instanceToUi: function (quote) {
			current.genericToUi(quote);
			_('instance-max-variable-cost').val(quote.maxVariableCost || null);
			_('instance-ephemeral').prop('checked', quote.ephemeral);
			_('instance-os').select2('data', current.select2IdentityData((quote.id && (quote.os || quote.price.os)) || 'LINUX'));
			_('instance-internet').select2('data', current.select2IdentityData(quote.internet || 'PUBLIC'));
			current.updateAutoScale();
			current.instanceSetUiPrice(quote);
		},

		/**
		 * Fill the database popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		databaseToUi: function (quote) {
			current.genericToUi(quote);
			_('database-engine').select2('data', current.select2IdentityData(quote.engine || 'MYSQL'));
			_('database-edition').select2('data', current.select2IdentityData(quote.edition || null));
			_('instance-internet').select2('data', current.select2IdentityData(quote.internet || 'PRIVATE'));
			current.instanceSetUiPrice(quote);
		},

		/**
		 * Fill the support popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		supportToUi: function (quote) {
			_('support-seats').val(quote.id ? quote.seats || null : 1);
			_('support-level').select2('data', current.select2IdentityData(quote.level || null));

			// Access types
			_('support-access-api').select2('data', quote.accessApi || null);
			_('support-access-email').select2('data', quote.accessEmail || null);
			_('support-access-phone').select2('data', quote.accessPhone || null);
			_('support-access-chat').select2('data', quote.accessChat || null);
			current.supportSetUiPrice(quote);
		},

		/**
		 * Fill the storage popup with given entity.
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		storageToUi: function (quote) {
			_('storage-size').val((quote && quote.size) || '10');
			_('storage-latency').select2('data', current.select2IdentityData((quote.latency) || null));
			_('storage-optimized').select2('data', current.select2IdentityData((quote.optimized) || null));
			_('storage-instance').select2('data', quote.quoteInstance || quote.quoteDatabase || null);
			current.storageSetUiPrice(quote);
		},

		/**
		 * Auto select the right RAM unit depending on the RAM amount.
		 * @param {int} ram, the RAM value in MB.
		 */
		adaptRamUnit: function (ram) {
			_('instance-ram-unit').find('li.active').removeClass('active');
			if (ram && ram >= 1024 && (ram / 1024) % 1 === 0) {
				// Auto select GB
				_('instance-ram-unit').find('li:last-child').addClass('active');
				ram = ram / 1024
			} else {
				// Keep MB
				_('instance-ram-unit').find('li:first-child').addClass('active');
			}
			_('instance-ram').val(ram);
			_('instance-ram-unit').find('.btn span:first-child').text(_('instance-ram-unit').find('li.active a').text());
		},

		select2IdentityData: function (id) {
			return id && {
				id: id,
				text: id
			};
		},

		/**
		 * Save a storage or an instance in the database from the corresponding popup. Handle the cost delta,  update the model, then the UI.
		 * @param {string} type Resource type to save.
		 */
		save: function (type) {
			var popupType = (type == 'instance' || type == 'database') ? 'generic' : type;
			var inputType = (type == 'instance' || type == 'database') ? 'instance' : type;
			var $popup = _('popup-prov-' + popupType);

			// Build the playload for API service
			var suggest = {
				price: _(inputType + '-price').select2('data'),
				usage: _(inputType + '-usage').select2('data'),
				location: _(inputType + '-location').select2('data')
			};
			var data = {
				id: current.model.quote.id,
				name: _(inputType + '-name').val(),
				description: _(inputType + '-description').val(),
				location: (suggest.location || {}).name,
				usage: (suggest.usage || {}).name,
				subscription: current.model.subscription
			};
			// Complete the data from the UI and backup the price context
			current[type + 'UiToData'](data);

			// Trim the data
			current.$main.trimObject(data);

			current.disableCreate($popup);
			$.ajax({
				type: data.id ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/' + type,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (updatedCost) {
					current.saveAndUpdateCosts(type, updatedCost, data, suggest.price, suggest.usage, suggest.location);
					$popup.modal('hide');
				},
				error: () => current.enableCreate($popup)
			});
		},

		/**
		 * Commit to the model the saved data (server side) and update the computed cost.
		 * @param {string} type Resource type to save.
		 * @param {string} updatedCost The new updated cost with identifier, total cost, resource cost and related resources costs.
		 * @param {object} data The original data sent to the back-end.
		 * @param {object} price The last know price suggest replacing the current price. When undefined, the original price is used.
		 * @param {object} usage The last provided usage.
		 * @param {object} location The last provided usage.
		 * @return {object} The updated or created model.
		 */
		saveAndUpdateCosts: function (type, updatedCost, data, price, usage, location) {
			var conf = current.model.configuration;

			// Update the model
			var qx = conf[type + 'sById'][updatedCost.id] || {
				id: updatedCost.id,
				cost: 0
			};

			// Common data
			qx.price = (price || current.model.quote || qx).price;
			qx.name = data.name;
			qx.description = data.description;
			qx.location = location;
			qx.usage = usage;
			qx.resourceType = type;

			// Specific data
			current[type + 'CommitToModel'](data, qx);

			// With related cost, other UI table need to be updated
			current.defaultCallback(type, updatedCost, qx);
			return qx;
		},

		/**
		 * Update the D3 instance types bar chart.
		 * @param {object} usage 
		 */
		updateInstancesBarChart: function (usage) {
			require(['d3', '../main/service/prov/lib/stacked'], function (d3, d3Bar) {
				var numDataItems = usage.timeline.length;
				var data = [];
				for (var i = 0; i < numDataItems; i++) {
					var value = usage.timeline[i];
					var stack = {
						date: value.date
					};
					current.types.forEach(type => stack[type] = value[type]);
					data.push(stack);
				}

				if (usage.cost) {
					$("#prov-barchart").removeClass('hidden');
					if (typeof current.d3Bar === 'undefined') {
						current.d3Bar = d3Bar;
						d3Bar.create("#prov-barchart .prov-barchart-svg", false, parseInt($('#prov-barchart').css('width')), 150, data, (d, bars) => {
							// Tooltip of barchart
							var tooltip = current.$messages['service:prov:date'] + ': ' + d.x;
							tooltip += '<br/>' + current.$messages['service:prov:total'] + ': ' + formatCost(bars.reduce((cost, bar) => cost + bar.height0, 0));
							current.types.forEach(type => {
								var cost = bars.filter(bar => bar.cluster === type);
								if (cost.length && cost[0].height0) {
									tooltip += '<br/><span' + (d.cluster === type ? ' class="strong">' : '>') + current.$messages['service:prov:' + type] + ': ' + formatCost(cost[0].height0) + '</span>';
								}
							});
							return '<span class="tooltip-text">' + tooltip + '</span>';
						}, d => {
							// Hover of barchart -> update sunburst and global cost
							current.filterDate = d && d['x-index'];
							current.updateUiCost();
						}, (d, bars, clicked) => {
							// Hover of barchart -> update sunburst and global cost
							current.fixedDate = clicked && d && d['x-index'];
							current.updateUiCost();
						}, d => formatCost(d, null, null, true), (a, b) => current.types.indexOf(a) - current.types.indexOf(b));
						$(window).off('resize.barchart').resize('resize.barchart', e => current.d3Bar
							&& typeof e.target.screenLeft === 'number'
							&& $('#prov-barchart').length
							&& current.d3Bar.resize(parseInt($('#prov-barchart').css('width'))));
					} else {
						d3Bar.update(data);
					}
				} else {
					$("#prov-barchart").addClass('hidden');
				}
			});
		},

		/**
		 * Update the total cost of the quote.
		 */
		updateUiCost: function () {
			var conf = current.model.configuration;

			// Compute the new capacity and costs
			var usage = current.computeUsage();

			// Update the global counts
			var filtered = usage.cost !== conf.cost.min;
			if (filtered) {
				// Filtered cost
				formatCost({
					min: usage.cost,
					max: usage.cost,
					unbound: usage.unbound > 0
				}, $('.cost'));
			} else {
				// Full cost
				formatCost(conf.cost, $('.cost'));
			}

			if (typeof current.filterDate !== 'number' && typeof current.fixedDate !== 'number') {
				// Do not update itself
				current.updateInstancesBarChart(usage);
			}

			// Separated resource counters
			current.types.forEach(type => {
				var $usage = $('.nav-pills [href="#tab-' + type + '"] .prov-resource-counter');
				if (usage[type].nb) {
					$usage.removeClass('hide').find('.odo-wrapper').text(usage[type].nb);
					$usage.find('.odo-wrapper-unbound').text((usage[type].min && usage[type].min > usage[type].nb || usage[type].unbound) ? '+' : '');
				} else {
					$usage.addClass('hide');
				}
			});

			// Instance summary
			var $summary = $('.nav-pills [href="#tab-instance"] .summary> .badge');
			if (usage.instance.cpu.available) {
				current.updateSummary($summary, usage.instance);
				var $oss = $summary.filter('[data-os]').addClass('hidden');
				Object.keys(usage.instance.oss).forEach(o => $oss.filter('[data-os="' + o + '"]').removeClass('hidden').find('span').text(usage.instance.oss[o]));
			} else {
				$summary.addClass('hidden');
			}

			// Database summary
			$summary = $('.nav-pills [href="#tab-database"] .summary> .badge');
			if (usage.database.cpu.available) {
				current.updateSummary($summary, usage.database);
				var $engines = $summary.filter('[data-engine]').addClass('hidden');
				Object.keys(usage.database.engines).forEach(engine => $engines.filter('[data-engine="' + engine + '"]').removeClass('hidden').find('span').text(usage.database.engines[engine]));
			} else {
				$summary.addClass('hidden');
			}

			// Storage summary
			$summary = $('.nav-pills [href="#tab-storage"] .summary> .badge.size');
			if (usage.storage.available) {
				$summary.removeClass('hidden');
				$summary.text(formatStorage(usage.storage.available));
			} else {
				$summary.addClass('hidden');
			}

			// Support summary
			$summary = $('.nav-pills [href="#tab-support"] .summary');
			if (usage.support.first) {
				$summary.removeClass('hidden').find('.support-first').text(usage.support.first).attr("title", usage.support.first);
				if (usage.support.more) {
					$summary.find('.support-more').removeClass('hidden').text(usage.support.more);
				} else {
					$summary.find('.support-more').addClass('hidden');
				}
			} else {
				$summary.addClass('hidden');
			}

			// Update the gauge : reserved / available
			require(['d3', '../main/service/prov/lib/gauge'], function (d3) {
				if (typeof current.d3Gauge === 'undefined' && usage.cost) {
					current.d3Gauge = d3;
					d3.select('#prov-gauge').call(d3.liquidfillgauge, 1, {
						textColor: '#FF4444',
						textVertPosition: 0.5,
						waveAnimateTime: 600,
						waveHeight: 0.9,
						textSize: 1.5,
						backgroundColor: '#e0e0e0'
					});
					$(function () {
						current.updateGauge(d3, usage);
					});
				} else {
					current.updateGauge(d3, usage);
				}
			});

			// Update the sunburst total resource capacity
			require(['d3', '../main/service/prov/lib/sunburst'], function (d3, sunburst) {
				if (usage.cost) {
					sunburst.init('#prov-sunburst', current.toD3(usage), function (a, b) {
						return current.types.indexOf(a.data.type) - current.types.indexOf(b.data.type);
					}, current.sunburstTooltip);
					_('prov-sunburst').removeClass('hidden');
				} else {
					_('prov-sunburst').addClass('hidden');
				}
			});
		},

		updateSummary($summary, resource) {
			$summary.removeClass('hidden');
			$summary.filter('.cpu').find('span').text(resource.cpu.available);
			var memoryText = formatRam(resource.ram.available);
			var memoryUnit = memoryText.replace(/[0-9,.]*/, '');
			var memoryValue = memoryText.replace(/[^0-9,.]*/, '');
			$summary.filter('.ram').find('span').first().text(memoryValue);
			$summary.filter('.ram').find('.unit').text(memoryUnit);
			if (resource.publicAccess) {
				$summary.filter('.internet').removeClass('hidden').find('span').text(resource.publicAccess);
			} else {
				$summary.filter('.internet').addClass('hidden');
			}
		},

		sunburstTooltip: function (data, d) {
			var tooltip = current.sunburstBaseTooltip(data)
			return '<span class="tooltip-text">' + tooltip
				+ '</br>' + current.$messages['service:prov:cost'] + ': ' + formatCost(data.size || data.value)
				+ current.recursivePercent(d, true, 100)
				+ (d.depth && data.children ? '</br>' + current.$messages['service:prov:nb'] + ': ' + (data.min || data.nb || data.children.length) : '') + '</span>';
		},

		title: function (key) {
			return (current.$messages['service:prov:' + key] || current.$messages[key]) + ': ';
		},
		sunburstBaseTooltip: function (data) {
			var conf = current.model.configuration;
			switch (data.type) {
				case 'latency':
					return current.title('storage-latency') + formatStorageLatency(data.name, true);
				case 'os':
					return formatOs(data.name, true, ' fa-2x');
				case 'engine':
					return formatDatabaseEngine(data.name, true, ' fa-2x');
				case 'instance':
					var instance = conf.instancesById[data.name];
					return current.title('name') + instance.name
						+ '<br>' + current.title('instance-type') + instance.price.type.name
						+ '<br>' + current.title('os') + formatOs(instance.price.os, true)
						+ '<br>' + current.title('term') + instance.price.term.name
						+ '<br>' + current.title('usage') + (instance.usage ? instance.usage.name : ('(' + current.$messages['service:prov:default'] + ') ' + (conf.usage ? conf.usage.name : '100%')));
				case 'storage':
					var storage = conf.storagesById[data.name];
					return current.title('name') + storage.name
						+ '<br>' + current.title('storage-type') + storage.price.type.name
						+ '<br>' + current.title('storage-latency') + formatStorageLatency(storage.price.type.latency, true)
						+ '<br>' + current.title('storage-optimized') + storage.price.type.optimized;
				case 'support':
					var support = conf.supportsById[data.name];
					return current.title('name') + support.name
						+ '<br>' + current.title('support-type') + support.price.type.name;
				case 'database':
					var database = conf.databasesById[data.name];
					return current.title('name') + database.name
						+ '<br>' + current.title('database-type') + database.price.type.name
						+ '<br>' + current.title('database-engine') + formatDatabaseEngine(database.price.engine, true) + (database.price.edition ? '/' + database.price.edition : '')
						+ '<br>' + current.title('term') + database.price.term.name
						+ '<br>' + current.title('usage') + (database.usage ? database.usage.name : ('(' + current.$messages['service:prov:default'] + ') ' + (conf.usage ? conf.usage.name : '100%')));
				case 'root-storage':
					return '<i class="far fa-hdd fa-2x"></i><br>' + data.name;
				case 'root-instance':
					return '<i class="fas fa-server fa-2x"></i><br>' + data.name;
				case 'root-database':
					return '<i class="fas fa-database fa-2x"></i><br>' + data.name;
				case 'root-support':
					return '<i class="fas fa-ambulance fa-2x"></i><br>' + data.name;
			}
			return data.name;
		},

		recursivePercent: function (d, first, rate) {
			if (!d.depth || Math.round(rate) === 0) {
				return first ? '' : ')';
			}
			if (d.value === d.parent.value) {
				// 100%
				return current.recursivePercent(d.parent, first, rate);
			}
			rate = d.value * rate / d.parent.value;
			return ((first ? ' (' : ', ') + Math.round(rate) + '% of ' + d.parent.data.name + current.recursivePercent(d.parent, false, rate));
		},

		/**
		 * Update the gauge value depending on the computed usage.
		 */
		updateGauge: function (d3, usage) {
			if (d3.select('#prov-gauge').on('valueChanged') && usage.costNoSupport) {
				var weightCost = 0;
				if (usage.instance.cpu.available) {
					weightCost += usage.instance.cost * 0.8 * usage.instance.cpu.reserved / usage.instance.cpu.available;
				}
				if (usage.instance.ram.available) {
					weightCost += usage.instance.cost * 0.2 * usage.instance.ram.reserved / usage.instance.ram.available;
				}
				if (usage.database.cpu.available) {
					weightCost += usage.database.cost * 0.8 * usage.database.cpu.reserved / usage.database.cpu.available;
				}
				if (usage.database.ram.available) {
					weightCost += usage.database.cost * 0.2 * usage.database.ram.reserved / usage.database.ram.available;
				}
				if (usage.storage.available) {
					weightCost += usage.storage.cost * usage.storage.reserved / usage.storage.available;
				}
				_('prov-gauge').removeClass('hidden');
				// Weight average of average...
				d3.select('#prov-gauge').on('valueChanged')(Math.floor(weightCost * 100 / usage.costNoSupport));
			} else {
				$('#prov-gauge').addClass('hidden');
			}
		},

		getFilteredData: function (type) {
			var result = [];
			if (current[type + 'Table']) {
				var data = _('prov-' + type + 's').DataTable().rows({ filter: 'applied' }).data();
				for (var index = 0; index < data.length; index++) {
					result.push(data[index]);
				}
			} else {
				result = current.model.configuration[type + 's'] || {};
			}
			if ((type === 'instance' || type === 'database' || type === 'storage') && (typeof current.filterDate === 'number' || typeof current.fixedDate === 'number')) {
				var usage = (current.model.configuration.usage || {});
				var date = typeof current.filterDate === 'number' ? current.filterDate : current.fixedDate;
				return result.filter(qi => (((qi.quoteInstance || qi.quoteDatabase || qi).usage || usage).start || 0) <= date);
			}
			return result;
		},

		/**
		 * Compute the global resource usage of this quote and the available capacity. Only minimal quantities are considered and with minimal to 1.
		 * Maximal quantities is currently ignored.
		 */
		computeUsage: function () {
			var conf = current.model.configuration;
			var nb = 0;
			var i, t, qi, cost;

			// Timeline
			var timeline = [];
			var defaultUsage = conf.usage || { rate: 100, start: 0 };
			var duration = 36;
			var date = moment().startOf('month');
			for (i = 0; i < 36; i++) {
				timeline.push({ cost: 0, month: date.month(), year: date.year(), date: date.format('MM/YYYY'), instance: 0, storage: 0, support: 0, database: 0 });
				date.add(1, 'months');
			}

			// Instance statistics
			var publicAccess = 0;
			var instances = current.getFilteredData('instance');
			var ramAvailable = 0;
			var ramReserved = 0;
			var cpuAvailable = 0;
			var cpuReserved = 0;
			var instanceCost = 0;
			var ramAdjustedRate = conf.ramAdjustedRate / 100;
			var reservationModeMax = conf.reservationMode === 'max';
			var minInstances = 0;
			var maxInstancesUnbound = false;
			var enabledInstances = {};
			var oss = {};
			for (i = 0; i < instances.length; i++) {
				qi = instances[i];
				cost = qi.cost.min || qi.cost || 0;
				nb = qi.minQuantity || 1;
				minInstances += nb;
				maxInstancesUnbound |= (qi.maxQuantity !== nb);
				cpuAvailable += qi.price.type.cpu * nb;
				cpuReserved += ((reservationModeMax && qi.cpuMax) ? qi.cpuMax : qi.cpu) * nb;
				ramAvailable += qi.price.type.ram * nb;
				ramReserved += ((reservationModeMax && qi.ramMax) ? qi.ramMax : qi.ram) * ramAdjustedRate * nb;
				instanceCost += cost;
				publicAccess += (qi.internet === 'public') ? 1 : 0;
				enabledInstances[qi.id] = true;
				oss[qi.os] = (oss[qi.os] || 0) + 1;
				for (t = (qi.usage || defaultUsage).start || 0; t < duration; t++) {
					timeline[t].instance += cost;
					timeline[t].cost += cost;
				}
			}

			// Database statistics
			var publicAccessD = 0;
			var databases = current.getFilteredData('database');
			var ramAvailableD = 0;
			var ramReservedD = 0;
			var cpuAvailableD = 0;
			var cpuReservedD = 0;
			var instanceCostD = 0;
			var minInstancesD = 0;
			var engines = {};
			var maxInstancesUnboundD = false;
			var enabledInstancesD = {};
			for (i = 0; i < databases.length; i++) {
				qi = databases[i];
				cost = qi.cost.min || qi.cost || 0;
				nb = qi.minQuantity || 1;
				minInstancesD += nb;
				maxInstancesUnboundD |= (qi.maxQuantity !== nb);
				cpuAvailableD += qi.price.type.cpu * nb;
				cpuReservedD += ((reservationModeMax && qi.cpuMax) ? qi.cpuMax : qi.cpu) * nb;
				ramAvailableD += qi.price.type.ram * nb;
				ramReservedD += ((reservationModeMax && qi.ramMax) ? qi.ramMax : qi.ram) * ramAdjustedRate * nb;
				instanceCostD += cost;
				var engine = qi.engine.replace(/AURORA .*/, 'AURORA');
				engines[engine] = (engines[engine] || 0) + 1;
				publicAccessD += (qi.internet === 'public') ? 1 : 0;
				enabledInstancesD[qi.id] = true;
				for (t = (qi.usage || defaultUsage).start || 0; t < duration; t++) {
					timeline[t].database += cost;
					timeline[t].cost += instanceCostD;
				}
			}

			// Storage statistics
			var storageAvailable = 0;
			var storageReserved = 0;
			var storageCost = 0;
			var storages = current.getFilteredData('storage');
			for (i = 0; i < storages.length; i++) {
				var qs = storages[i];
				if (qs.quoteInstance && enabledInstances[qs.quoteInstance.id]) {
					nb = qs.quoteInstance.minQuantity || 1;
				} else if (qs.quoteDatabase && enabledInstancesD[qs.quoteDatabase.id]) {
					nb = qs.quoteDatabase.minQuantity || 1;
				} else {
					nb = 1;
				}
				var qsSize = (reservationModeMax && qs.sizeMax) ? qs.sizeMax : qs.size;
				storageAvailable += Math.max(qsSize, qs.price.type.minimal) * nb;
				storageReserved += qsSize * nb;
				storageCost += qs.cost;
				var quoteVm = qs.quoteDatabase || qs.quoteInstance;
				if (quoteVm) {
					for (t = (quoteVm.usage || defaultUsage).start || 0; t < duration; t++) {
						timeline[t].storage += qs.cost;
						timeline[t].cost += qs.cost;
					}
				} else {
					for (t = timeline.length; t-- > 0;) {
						timeline[t].cost += qs.cost;
					}
				}
			}

			// Support statistics
			var supportCost = 0;
			var supports = current.getFilteredData('support');
			for (i = 0; i < supports.length; i++) {
				supportCost += supports[i].cost;
			}
			for (t = 0; t < duration; t++) {
				timeline[t].support = supportCost;
				timeline[t].cost += supportCost;
			}

			return {
				cost: instanceCost + instanceCostD + storageCost + supportCost,
				costNoSupport: instanceCost + instanceCostD + storageCost,
				unbound: maxInstancesUnbound || maxInstancesUnboundD,
				timeline: timeline,
				instance: {
					nb: instances.length,
					min: minInstances,
					unbound: maxInstancesUnbound,
					oss: oss,
					ram: {
						available: ramAvailable,
						reserved: ramReserved
					},
					cpu: {
						available: cpuAvailable,
						reserved: cpuReserved
					},
					publicAccess: publicAccess,
					filtered: instances,
					cost: instanceCost
				},
				database: {
					nb: databases.length,
					min: minInstancesD,
					unbound: maxInstancesUnboundD,
					ram: {
						available: ramAvailableD,
						reserved: ramReservedD
					},
					cpu: {
						available: cpuAvailableD,
						reserved: cpuReservedD
					},
					engines: engines,
					publicAccess: publicAccessD,
					filtered: databases,
					cost: instanceCostD
				},
				storage: {
					nb: storages.length,
					available: storageAvailable,
					reserved: storageReserved,
					filtered: storages,
					cost: storageCost
				},
				support: {
					nb: supports.length,
					filtered: supports,
					first: supports.length ? supports[0].price.type.name : null,
					more: supports.length > 1,
					cost: supportCost
				}
			};
		},

		/**
		 * Update the model to detach a storage from its instance
		 * @param storage The storage model to detach.
		 * @param property The storage property of attachment.
		 */
		detachStorage: function (storage, property) {
			if (storage[property]) {
				var qis = storage[property].storages || [];
				for (var i = qis.length; i-- > 0;) {
					if (qis[i] === storage) {
						qis.splice(i, 1);
						break;
					}
				}
				delete storage[property];
			}
		},
		/**
		 * Update the model to attach a storage to its resource
		 * @param storage The storage model to attach.
		 * @param type The resource type to attach.
		 * @param resource The resource model or identifier to attach.
		 * @param force When <code>true</code>, the previous resource will not be detached.
		 */
		attachStorage: function (storage, type, resource, force) {
			if (typeof resource === 'number') {
				resource = current.model.configuration[type + 'sById'][resource];
			}
			var property = 'quote' + type.charAt(0).toUpperCase() + type.slice(1);
			if (force !== true && storage[property] === resource) {
				// Already attached or nothing to attach to the target resource
				return;
			}
			if (force !== true && storage[property]) {
				// Detach the old resource
				current.detachStorage(storage, property);
			}

			// Update the model
			if (resource) {
				if ($.isArray(resource.storages)) {
					resource.storages.push(storage);
				} else {
					resource.storages = [storage];
				}
				storage[property] = resource;
			}
		},

		toD3: function (usage) {
			var root = {
				name: current.$messages['service:prov:total'],
				value: usage.cost,
				children: []
			};
			current.types.forEach(type => {
				var data = {
					value: 0,
					type: 'root-' + type,
					children: [],
					nb: usage[type].nb,
					min: usage[type].min,
					unbound: usage[type].unbound
				};
				current[type + 'ToD3'](data, usage);
				root.children.push(data);
			});
			return root;
		},

		instanceToD3: function (data, usage) {
			var allOss = {};
			var instances = usage.instance.filtered;
			data.name = current.$messages['service:prov:instances-block'];
			for (var i = 0; i < instances.length; i++) {
				var qi = instances[i];
				var oss = allOss[qi.os];
				if (typeof oss === 'undefined') {
					// First OS
					oss = {
						name: qi.os,
						type: 'os',
						value: 0,
						children: []
					};
					allOss[qi.os] = oss;
					data.children.push(oss);
				}
				oss.value += qi.cost;
				data.value += qi.cost;
				oss.children.push({
					name: qi.id,
					type: 'instance',
					size: qi.cost
				});
			}
		},
		databaseToD3: function (data, usage) {
			var allEngines = {};
			var databases = usage.database.filtered;
			data.name = current.$messages['service:prov:databases-block'];
			for (var i = 0; i < databases.length; i++) {
				var qi = databases[i];
				var engines = allEngines[qi.engine];
				if (typeof engines === 'undefined') {
					// First Engine
					engines = {
						name: qi.engine,
						type: 'engine',
						value: 0,
						children: []
					};
					allEngines[qi.engine] = engines;
					data.children.push(engines);
				}
				engines.value += qi.cost;
				data.value += qi.cost;
				engines.children.push({
					name: qi.id,
					type: 'database',
					size: qi.cost
				});
			}
		},

		storageToD3: function (data, usage) {
			var storages = usage.storage.filtered;
			data.name = current.$messages['service:prov:storages-block'];
			var allOptimizations = {};
			for (var i = 0; i < storages.length; i++) {
				var qs = storages[i];
				var optimizations = allOptimizations[qs.price.type.latency];
				if (typeof optimizations === 'undefined') {
					// First optimization
					optimizations = {
						name: qs.price.type.latency,
						type: 'latency',
						value: 0,
						children: []
					};
					allOptimizations[qs.price.type.latency] = optimizations;
					data.children.push(optimizations);
				}
				optimizations.value += qs.cost;
				data.value += qs.cost;
				optimizations.children.push({
					name: qs.id,
					type: 'storage',
					size: qs.cost
				});
			}
		},

		supportToD3: function (data, usage) {
			var supports = usage.support.filtered;
			data.name = current.$messages['service:prov:support-block'];
			for (var i = 0; i < supports.length; i++) {
				var support = supports[i];
				data.value += support.cost;
				data.children.push({
					name: support.id,
					type: 'support',
					size: support.cost
				});
			}
		},

		/**
		 * Initialize the instance datatables from the whole quote
		 */
		instanceNewTable: function () {
			return current.genericInstanceNewTable('instance', [{
				data: 'minQuantity',
				className: 'hidden-xs',
				type: 'num',
				render: formatQuantity
			}, {
				data: 'os',
				className: 'truncate',
				width: '24px',
				type: 'string',
				render: formatOs
			}]);
		},

		/**
		 * Initialize the support datatables from the whole quote
		 */
		supportNewTable: function () {
			return {
				rowCallback: function (nRow, data) {
					current.rowCallback($(nRow), data, 'support');
				},
				columns: [{
					data: 'level',
					width: '128px',
					type: 'string',
					render: formatSupportLevel
				}, {
					data: 'seats',
					className: 'hidden-xs',
					type: 'num',
					render: formatSupportSeats
				}, {
					data: 'accessApi',
					className: 'hidden-xs hidden-sm hidden-md',
					type: 'string',
					render: formatSupportAccess
				}, {
					data: 'accessPhone',
					className: 'hidden-xs hidden-sm hidden-md',
					type: 'string',
					render: formatSupportAccess
				}, {
					data: 'accessEmail',
					className: 'hidden-xs hidden-sm hidden-md',
					type: 'string',
					render: formatSupportAccess
				}, {
					data: 'accessChat',
					className: 'hidden-xs hidden-sm hidden-md',
					type: 'string',
					render: formatSupportAccess
				}, {
					data: 'price.type',
					className: 'truncate',
					type: 'string',
					render: formatSupportType
				}]
			};
		},

		/**
		 * Find a free name within the given namespace.
		 * @param {array} resources The namespace of current resources.
		 * @param {string} prefix The preferred name (if available) and used as prefix when there is collision.
		 * @param {string} increment The current increment number of collision. Will starts from 1 when not specified.
		 * @param {string} resourcesByName The namespace where key is the unique name.
		 */
		findNewName: function (resources, prefix, increment, resourcesByName) {
			if (typeof resourcesByName === 'undefined') {
				// Build the name based index
				resourcesByName = {};
				resources.forEach(resource => resourcesByName[resource.name] = resource);
			}
			if (resourcesByName[prefix]) {
				increment = increment || 1;
				if (resourcesByName[prefix + '-' + increment]) {
					return current.findNewName(resourcesByName, prefix, increment + 1, resourcesByName);
				}
				return prefix + '-' + increment;
			}
			return prefix;
		},

		/**
		 * Initialize the storage datatables from the whole quote
		 */
		storageNewTable: function () {
			return {
				rowCallback: function (nRow, data) {
					current.rowCallback($(nRow), data, 'storage');
				},
				columns: [{
					data: null,
					type: 'num',
					className: 'hidden-xs',
					render: (_i, mode, data) => formatQuantity(null, mode, (data.quoteInstance || data.quoteDatabase))
				}, {
					data: 'size',
					width: '36px',
					className: 'truncate',
					type: 'num',
					render: formatStorage
				}, {
					data: 'price.type.latency',
					type: 'string',
					className: 'truncate hidden-xs',
					render: formatStorageLatency
				}, {
					data: 'price.type.optimized',
					type: 'string',
					className: 'truncate hidden-xs',
					render: formatStorageOptimized
				}, {
					data: 'price.type',
					type: 'string',
					className: 'truncate hidden-xs hidden-sm hidden-md',
					render: formatStorageType
				}, {
					data: null,
					type: 'string',
					className: 'truncate hidden-xs hidden-sm',
					render: (_i, mode, data) => formatQuoteResource(data.quoteInstance || data.quoteDatabase)
				}]
			};
		},

		/**
		 * Donut of usage
		 * @param {integer} rate The rate percentage 1-100%
		 */
		updateD3UsageRate: function (rate) {
			require(['d3', '../main/service/prov/lib/donut'], function (d3, donut) {
				if (current.contextDonut) {
					donut.update(current.contextDonut, rate);
				} else {
					current.contextDonut = donut.create("#usage-chart", rate, 250, 250);
				}
			});
		},

		/**
		 * Initialize the database datatables from the whole quote
		 */
		databaseNewTable: function () {
			return current.genericInstanceNewTable('database', [{
				data: 'minQuantity',
				className: 'hidden-xs',
				type: 'num',
				render: formatQuantity
			}, {
				data: 'price.engine',
				className: 'truncate',
				type: 'string',
				render: formatDatabaseEngine
			}, {
				data: 'price.edition',
				type: 'string',
				className: 'truncate'
			}]);
		},

		rowCallback: function ($row, data) {
			current.tagManager.select2($row, data);
		},

		/**
		 * Initialize the database datatables from the whole quote
		 */
		genericInstanceNewTable: function (type, columns) {
			return {
				rowCallback: function (nRow, qi) {
					current.rowCallback($(nRow), qi);
					$(nRow).find('.storage-tags').select2('destroy').select2({
						multiple: true,
						minimumInputLength: 1,
						createSearchChoice: () => null,
						formatInputTooShort: current.$messages['service:prov:storage-select'],
						formatResult: formatStoragePriceHtml,
						formatSelection: formatStorageHtml,
						ajax: {
							url: REST_PATH + 'service/prov/' + current.model.subscription + '/storage-lookup?' + type + '=' + qi.id,
							dataType: 'json',
							data: function (term) {
								return {
									size: $.isNumeric(term) ? parseInt(term, 10) : 1, // search term
								};
							},
							results: function (data) {
								// Completed the requested identifier
								data.forEach(quote => {
									quote.id = quote.price.id + '-' + new Date().getMilliseconds();
									quote.text = quote.price.type.name;
								});
								return {
									more: false,
									results: data
								};
							}
						}
					}).select2('data', qi.storages || []).off('change').on('change', function (event) {
						if (event.added) {
							// New storage
							var suggest = event.added;
							var data = {
								name: current.findNewName(current.model.configuration.storages, qi.name),
								type: suggest.price.type.name,
								size: suggest.size,
								quoteInstance: type === 'instance' && qi.id,
								quoteDatabase: type === 'database' && qi.id,
								subscription: current.model.subscription
							};
							current.$main.trimObject(data);
							$.ajax({
								type: 'POST',
								url: REST_PATH + 'service/prov/storage',
								dataType: 'json',
								contentType: 'application/json',
								data: JSON.stringify(data),
								success: function (updatedCost) {
									current.saveAndUpdateCosts('storage', updatedCost, data, suggest, null, qi.location);

									// Keep the focus on this UI after the redraw of the row
									$(function () {
										_('prov-' + type + 's').find('tr[data-id="' + qi.id + '"]').find('.storage-tags .select2-input').trigger('focus');
									});
								}
							});
						} else if (event.removed) {
							// Storage to delete
							var qs = event.removed.qs || event.removed;
							$.ajax({
								type: 'DELETE',
								url: REST_PATH + 'service/prov/storage/' + qs.id,
								success: updatedCost => current.defaultCallback('storage', updatedCost)
							});
						}
					});
				},
				columns: columns.concat([{
					className: 'truncate',
					width: '48px',
					type: 'num',
					render: formatCpu
				}, {
					className: 'truncate',
					width: '64px',
					type: 'num',
					render: formatRam
				}, {
					data: 'price.term',
					className: 'hidden-xs hidden-sm price-term',
					type: 'string',
					render: formatInstanceTerm
				}, {
					data: 'price.type',
					className: 'truncate hidden-xs hidden-sm hidden-md',
					type: 'string',
					render: formatInstanceType
				}, {
					data: 'usage',
					className: 'hidden-xs hidden-sm usage',
					type: 'string',
					render: formatUsageTemplate,
					filter : opFunction=> (value, data) => opFunction(data.usage ? data.usage.name : current.model.configuration.usage ? current.model.configuration.usage.name : 'default')
				}, {
					data: 'location',
					className: 'hidden-xs hidden-sm location',
					width: '24px',
					type: 'string',
					render: formatLocation
				}, {
					data: null,
					className: 'truncate hidden-xs hidden-sm',
					type: 'num',
					render: formatQiStorages
				}])
			};
		},

		updateCost: function (conf, type, newCost, resource) {
			// Update the sums
			conf[type + 'Cost'] += newCost.min - resource.cost;
			conf.cost.min -= resource.cost - newCost.min;
			conf.cost.max -= (resource.maxCost || resource.cost) - (newCost.max || 0);

			// Upate the resource cost
			resource.cost = newCost.min;
			resource.maxCost = newCost.max || 0;
		},

		/**
		 * Default Ajax callback after a deletion, update or create. This function looks the updated resources (identifiers), the deleted resources and the new updated costs.
		 * @param {string} type The related resource type.
		 * @param {object} updatedCost The new costs details.
		 * @param {object} resource Optional resource to update or create. When null, its a deletion.
		 */
		defaultCallback: function (type, updatedCost, resource) {
			var related = updatedCost.related || {};
			var deleted = updatedCost.deleted || {};
			var conf = current.model.configuration;
			var nbCreated = 0;
			var nbUpdated = 0;
			var nbDeleted = 0;
			var createdSample = null;
			var updatedSample = null;
			var deletedSample = null;
			var nb = 0;
			Object.keys(deleted).forEach(t => nb += deleted[t].length);
			Object.keys(related).forEach(t => nb += Object.keys(related[t]).length);
			if (nb > current.changesReloadThreshold) {
				// Global reload is more efficient
				current.reload();
				return
			}

			// Look the deleted resources
			Object.keys(deleted).forEach(t => {
				// For each deleted resource of this type, update the UI and the cost in the model
				for (var i = deleted[t].length; i-- > 0;) {
					var deletedR = current.delete(t.toLowerCase(), deleted[t][i]);
					if (nbDeleted++ === 0) {
						deletedSample = deletedR.name;
					}
				}
			});

			// Look the updated resources
			Object.keys(related).forEach(key => {
				// For each updated resource of this type, update the UI and the cost in the model
				Object.keys(related[key]).forEach(id => {
					var relatedType = key.toLowerCase();
					var relatedR = conf[relatedType + 'sById'][id];
					current.updateCost(conf, relatedType, related[key][id], relatedR);

					if (nbUpdated++ === 0) {
						updatedSample = relatedR.name;
					}
					current.redrawResource(relatedType, id);
				});
			});

			// Update the current object
			if (resource) {
				current.updateCost(conf, type, updatedCost.cost, resource);

				if (conf[type + 'sById'][updatedCost.id]) {
					// Update : Redraw the row
					nbUpdated++;
					updatedSample = resource.name;
					current.redrawResource(type, updatedCost.id);
				} else {
					// Create
					conf[type + 's'].push(resource);
					conf[type + 'sById'][updatedCost.id] = resource;
					resource.id = updatedCost.id;
					nbCreated++;
					createdSample = resource.name;
					_('prov-' + type + 's').DataTable().row.add(resource).draw(false);
				}
			} else if (updatedCost.id) {
				// Delete this object
				nbDeleted++;
				deletedSample = current.delete(type, updatedCost.id).name;
			}

			if (conf.cost.min !== updatedCost.total.min || conf.cost.max !== updatedCost.total.max || conf.cost.unbound !== updatedCost.total.unbound) {
				console.log('Need to reajust the compted cost: min=' + (updatedCost.total.min - conf.cost.min)
					+ ' max=' + (updatedCost.total.max - conf.cost.max) + ' unbound=' + (updatedCost.total.unbound && !conf.cost.unbound || !updatedCost.total.unbound && conf.cost.unbound));
				conf.cost = updatedCost.total;
			}

			// Notify callback
			var message = [];
			if (nbCreated) {
				message.push(Handlebars.compile(current.$messages['service:prov:created'])({ count: nbCreated, sample: createdSample, more: nbCreated - 1 }));
			}
			if (nbUpdated) {
				message.push(Handlebars.compile(current.$messages['service:prov:updated'])({ count: nbUpdated, sample: updatedSample, more: nbUpdated - 1 }));
			}
			if (nbDeleted) {
				message.push(Handlebars.compile(current.$messages['service:prov:deleted'])({ count: nbDeleted, sample: deletedSample, more: nbDeleted - 1 }));
			}
			if (message.length) {
				notifyManager.notify(message.join('<br>'));
			}
			$('.tooltip').remove(); // TODO Generalize
			current.updateUiCost();
		},

		/**
		 * Delete a resource.
		 * @param {string} type The resource type.
		 * @param {integer} id The resource identifier.
		 */
		delete: function (type, id) {
			var conf = current.model.configuration;
			var resources = conf[type + 's'];
			var EMPTY_COST = { min: 0, max: 0, unbound: false };
			for (var i = resources.length; i-- > 0;) {
				var resource = resources[i];
				if (resource.id === id) {
					resources.splice(i, 1);
					delete conf[type + 'sById'][resource.id];
					current.updateCost(conf, type, EMPTY_COST, resource);
					if (type === 'storage') {
						var qr = resource.quoteInstance || resource.quoteDatabase;
						if (qr) {
							// Also redraw the instance
							var attachedType = resource.quoteInstance ? 'instance' : 'database';
							current.detachStorage(resource, 'quote' + attachedType.charAt(0).toUpperCase() + attachedType.slice(1));
							current.redrawResource(attachedType, qr.id);
						}
					}

					_('prov-' + type + 's').DataTable().rows((_, data) => data.id === id).remove().draw(false);
					return resource;
				}
			}
		},
		formatQuoteResource: formatQuoteResource,
		formatCost: formatCost,
		formatCpu: formatCpu,
		formatRam: formatRam,
		formatOs: formatOs,
		formatDatabaseEngine: formatDatabaseEngine,
		formatStorage: formatStorage
	};
	return current;
});
