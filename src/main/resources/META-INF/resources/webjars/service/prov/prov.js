/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
/*jshint esversion: 6*/
define(function () {

	let initializedPopupEvents = false;
	let initializedPopupUsage = false;
	let initializedPopupBudget = false;
	const colorScheme = ['schemeTableau10', 'schemeSet2', 'schemeSet3', 'schemeSet1', 'schemeDark2'][0];
	const ROOT_PREFIX = 'root-';

	/**
	 * Enable resource type.
	 */
	const types = ['instance', 'database', 'container', 'function', 'storage', 'support'];
	const typeIcons = {
		instance: 'fas fa-server',
		database: 'fas fa-database',
		container: 'fab fa-docker',
		function: 'fas fa-code',
		storage: 'far fa-hdd',
		support: 'fas fa-ambulance'
	};

	/**
	 * Enable resource type to relatable to storages.
	 */
	const typesStorage = ['instance', 'database', 'container', 'function'];

	/**
	 * OS key to markup/label mapping.
	 */
	const os = {
		'linux': ['Linux', 'fab fa-linux'],
		'windows': ['Windows', 'fab fa-windows'],
		'suse': ['SUSE', 'fab fa-suse'],
		'rhel': ['Red Hat Enterprise', 'fab fa-redhat'],
		'oracle': ['Oracle Linux', 'icon-oracle'],
		'centos': ['CentOS', 'fab fa-centos'],
		'debian': ['Debian', 'icon-debian'],
		'fedora': ['Fedora', 'fab fa-fedora'],
		'ubuntu': ['Ubuntu', 'fab fa-ubuntu'],
		'freebsd': ['FreeBSD', 'fab fa-freebsd']
	};

	/**
	 * Engine key to markup/label mapping.
	 */
	const databaseEngines = {
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
	const internet = {
		'public': ['Public', 'fas fa-globe fa-fw'],
		'private': ['Private', 'fas fa-lock fa-fw'],
		'private_nat': ['NAT', 'fas fa-low-vision fa-fw']
	};

	/**
	 * Rate name (identifier) to class mapping. Classes are distributed across 5 values.
	 */
	const rates = {
		'worst': 'far fa-star text-danger fa-fw',
		'low': 'far fa-star-half text-danger fa-fw',
		'medium': 'fas fa-star-half fa-fw',
		'good': 'fas fa-star text-primary fa-fw',
		'best': 'fas fa-certificate text-success fa-fw',
		'invalid': 'fas fa-ban fa-fw'
	};

	/**
	 * Rate name (identifier) to class mapping. Classes are distributed across 3 values.
	*/
	const rates3 = {
		'low': 'far fa-star-half fa-fw',
		'medium': 'far fa-star text-success fa-fw',
		'good': 'fas fa-star text-success fa-fw',
		'invalid': 'fas fa-ban fa-fw'
	};

	/**
	 * Storage optimized key to markup/label mapping.
	 */
	const storageOptimized = {
		'throughput': 'fas fa-angle-double-right fa-fw',
		'durability': 'fas fa-archive fa-fw',
		'iops': 'fas fa-bolt fa-fw'
	};

	/**
	 * Support access type key to markup/label mapping.
	 */
	const supportAccessType = {
		'technical': 'fas fa-wrench fa-fw',
		'billing': 'fas fa-dollar-sign fa-fw',
		'all': 'fas fa-users fa-fw'
	};

	const maxOpts = {
		width: 250,
		labels: ['max', 'reserved'],
		values: [false, false],
		toInternal: v => Math.log2(v / 4 + 1),
		toValue: (w, maxWidth, maxValue, toInternal) => (Math.pow(2, w / maxWidth * toInternal(maxValue)) - 1) * 4,
		label: 'reserved',
	};

	/**
	 * Create a cache by id for the given resource type.
	 * @param {object} conf The current configuration.
	 * @param {string} type The resource type.
	 * @param {string} id The identifier attribute. Default is 'id'.
	 */
	function toIds(conf, type, id = 'id') {
		let ids = {};
		let items = conf[`${type}s`];
		let cost = 0;
		items.forEach(i => {
			ids[i[id]] = i;
			if (i.usage) {
				i.usage = conf.usagesById[i.usage];
			}
			if (i.budget) {
				i.budget = conf.budgetsById[i.budget];
			}
			if (i.cost) {
				cost += i.cost;
			}
		});
		conf[`${type}sById`] = ids;
		conf[`${type}Cost`] = cost;
		return ids;
	}

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
					if (typesStorage.every(sType => processors[sType].filter(p => p.toLowerCase().includes(term)).length)) {
						return { id: term, text: '[' + term + ']' };
					}
				}
				// Invalid processor
				return null;
			},
			data: () => {
				if (current.model) {
					var processors = current.model.configuration.processors;
					return { results: (typeof type === 'function' ? processors[type()] || [] : typesStorage.map(sType => processors[sType]).flat()).map(p => ({ id: p, text: p })) };
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
		if (value === 'true' || value === true) {
			return true;
		}
		if (value === 'false' || value === false) {
			return false;
		}
		return null;
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
	 * @param {boolean} value  TRUE, FALSE or NULL value.
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

	function formatDatabaseEngine(engine, mode, className) {
		let engineId = (engine.id || engine || '').toUpperCase();
		var cfg = databaseEngines[engineId] || [engineId, 'far fa-question-circle'];
		if (mode === 'sort' || mode === 'filter') {
			return cfg[0];
		}
		className = cfg[1] + (typeof className === 'string' ? className : '');
		return '<i class="' + className + '" data-toggle="tooltip" title="' + cfg[0] + '"></i> ' + cfg[0];
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
		let formatValue;
		let formatParams;
		if (formatter) {
			formatValue = formatter(value);
			formatParams = [formatter(value), formatter(max), rate];
		} else {
			formatValue = value;
			formatParams = [value, max, rate];
		}
		return formatValue + (fullClass ? '<span class="efficiency pull-right"><i class="' + fullClass + '" data-toggle="tooltip" title="' +
			Handlebars.compile(current.$messages['service:prov:usage-partial'])(formatParams) + '"></i></span>' : '');
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
		return license ? license.text || current.$messages['service:prov:license-' + license.toLowerCase()] || license : null;
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
		}

		if (type.networkRate) {
			details += '<br><i class=\'fas fa-globe fa-fw\'></i> ';
			details += type.ramRate ? '<i class=\'' + rates[type.networkRate] + '\'></i>' : '';
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
		var formatMin = formatManager.formatCost(min)
		var formatMax = formatManager.formatCost(max)
		if ((typeof max !== 'number') || max === min || formatMin === formatMax) {
			// Max cost is equal to min cost, no range
			$cost.find('.cost-min').addClass('hidden');
			return formatter(min, true, $cost, noRichText, unbound, cost && cost.currency);
		}
		// Max cost, is different, display a range
		return formatter(min, false, $cost, noRichText) + '-' + formatter(max, true, $cost, noRichText, unbound, cost && cost.currency);
	}

	/**
	 * Return the HTML markup from the rating.
	 */
	function formatRate(rate, mode, className) {
		var id = ((rate && rate.id) || rate || 'invalid').toLowerCase();
		var text = id && current.$messages['service:prov:rate-' + id];
		if (mode === 'sort' || mode === 'filter') {
			return text;
		}

		const showText = mode && (!(mode instanceof jQuery) || !mode.is('.select2-chosen') || mode.closest('.prov-rate').is('.prov-rate-full'));
		className = rates[id] + (typeof className === 'string' ? className : '');
		return `<i class="${className}" data-toggle="tooltip" title="${text}"></i>${showText ? ' ' + text : ''}`;
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
	function formatStorageOptimized(optimized, withText, className) {
		if (optimized) {
			const id = (optimized.id || optimized).toLowerCase();
			const text = current.$messages['service:prov:storage-optimized-' + id];
			className = storageOptimized[id] + (typeof className === 'string' ? className : '');
			return '<i class="' + className + '" data-toggle="tooltip" title="' + text + '"></i>' + (withText ? ' ' + text : '');
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
		return (showName === true ? type.name + ' ' : '') + `<span data-prov-type="storage" data-id="${qs.id}">
		${formatRate(type.latency)}${type.optimized ? ' ' + formatStorageOptimized(type.optimized) : ''}
		<small>${qs.quantity && qs.quantity!==1 ? (qs.quantity+ 'x'):'' }</small>
		${formatManager.formatSize(qs.size * 1024 * 1024 * 1024, 3)}
		${(qs.size < type.minimal) ? ' (' + formatManager.formatSize(type.minimal * 1024 * 1024 * 1024, 3) + ')' : ''}
		</span>`;
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
			return (instance.storages || []).reduce((acc, s) => acc + storage.size, 0);
		}
		// Need to build a Select2 tags markup
		return `<input type="text" class="storage-tags" data-instance="${instance.id}" autocomplete="off" name="storage-tags">`;
	}

	/**
	 * Return the HTML markup from the OS key name.
	 */
	function formatOs(value, mode, className) {
		if (mode === 'sort' || mode === 'filter') {
			return value.id || value || 'linux';
		}
		const cfg = os[(value.id || value || 'linux').toLowerCase()] || os.linux;
		className = cfg[1] + (typeof className === 'string' ? className : '');
		return '<i class="' + className + ' fa-fw" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
	}

	/**
	 * Return the HTML markup from the Internet privacy key name.
	 */
	function formatInternet(value, mode, className) {
		const cfg = (value && internet[(value.id || value).toLowerCase()]) || current.value.public || 'public';
		if (mode === 'sort' || mode === 'filter') {
			return cfg[0];
		}
		className = cfg[1] + (typeof className === 'string' ? className : '');
		return '<i class="' + className + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
	}

	function filterMultiScoped(name) {
		return opFunction => (value, data) => opFunction((data[name] || current.model.configuration[name] || { name: 'default' }).name);
	}
	function formatMultiScoped(type, entity, confEntity, mode, icon, tooltip) {
		if (mode === 'sort' || mode === 'filter' || mode === 'tooltip') {
			entity = (typeof entity === 'undefined' || entity === null) ? confEntity : entity;
			const text = entity ? entity.text || entity.name : '';
			if (mode === 'tooltip') {
				return text || select2Placeholder(type);
			}
			return text;
		}
		if (entity) {
			entity = {
				name: (mode ? entity.name || entity.text : (entity.text || entity.name)) || entity,
				rate: entity.id,
				duration: false,
			}
		} else {
			entity = confEntity || { rate: 100, duration: 1, name: '<i>default</i>' };
		}
		return `<span data-toggle="tooltip" title='${current.title('name', icon)}${entity.name}${(typeof tooltip === 'function' && tooltip(entity)) || ''}'>${entity.name}</span>`;
	}

	function formatBudget(budget, mode, qi) {
		return formatMultiScoped('budget', budget, current.model.configuration.budget, mode, 'fa-wallet', e => (typeof e.initialCost === 'number' && e.initialCost > 1) ? `<br>${current.title('budget-initialCost')}${formatCost(e.initialCost)}` : '');
	}
	function formatUsage(usage, mode) {
		return formatMultiScoped('usage', usage, current.model.configuration.usage, mode, 'fa-clock', e => {
			let tooltip = '';
			if (typeof e.start === 'number' && e.duration > 1) {
				tooltip += `<br>${current.title('usage-duration')}${e.duration} month(s)`;
			}
			if (typeof e.start === 'number' && e.start > 0) {
				tooltip += `<br>${current.title('usage-start')}${e.start} month(s)`;
			}
			return tooltip;
		});
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
		} else if (data.quoteContainer && data.quoteContainer.price.location) {
			obj = conf.locationsById[data.quoteContainer.price.location];
		} else if (data.quoteDatabase && data.quoteDatabase.price.location) {
			obj = conf.locationsById[data.quoteDatabase.price.location];
		} else if (data.quoteFunction && data.quoteFunction.price.location) {
			obj = conf.locationsById[data.quoteFunction.price.location];
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
			return `<a class="update" data-toggle="modal" data-target="#popup-prov-generic" data-prov-type="${resource.resourceType}"> <i class="${typeIcons[resource.resourceType]}"></i></a> ${resource.name}`;
		}
		return '';
	}

	/**
	 * Return the HTML markup from the quote resource.
	 */
	function formatName(name, mode, obj) {
		if (mode !== 'display') {
			return name
		}
		return `<a class="update" data-toggle="modal" data-target="#popup-prov-${obj.resourceType === "storage" ? "storage" : "generic"}">${name}</a>`;
	}

	/**
	 * Return the HTML markup from the support level.
	 */
	function formatSupportLevel(level, mode, className) {
		const id = level ? (level.id || level).toLowerCase() : '';
		if (id) {
			var text = current.$messages['service:prov:support-level-' + id];
			className = rates3[id] + (typeof className === 'string' ? className : '');
			if (mode === 'sort' || mode === 'filter') {
				return text;
			}

			return `<i class="${className}" data-toggle="tooltip" title="${text}"></i>${mode ? ' ' + text : ''}`;
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
			const id = (type.id || type).toLowerCase();
			const text = current.$messages['service:prov:support-access-' + id];
			if (mode === 'sort' || mode === 'filter') {
				return text;
			}
			const className = supportAccessType[id];
			return '<i class="' + className + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode === 'display' ? '' : (' ' + text));
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
		return window.Select2.util.stripDiacritics('' + text).toUpperCase().includes(window.Select2.util.stripDiacritics('' + term).toUpperCase());
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
			dropdownAutoWidth: true,
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
							// Restore the original success function
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
		_('popup-prov-generic').on('click', '.input-group-btn li', function (e) {
			$.proxy(synchronizeDropdownText, $(this))();
			// Also trigger the change of the value
			$(e.target).closest('.input-group-btn').prev('input').trigger('keyup');
		});
		_('database-engine').select2(genericSelect2(null, formatDatabaseEngine, 'database-engine', null, ascendingComparator)).on('change', function(e){
			_('database-edition').select2('data', null);
		});
		$('#instance-min-quantity, #instance-max-quantity').on('change', current.updateAutoScale);
		$('input.resource-query').not('[type="number"]').on('change', current.checkResource);
		$('input.resource-query[type="number"]').on('keyup', delay(function (event) {
			if (event.which !== 16 && event.which !== 91 && event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
				$(this).trigger('change');
				$.proxy(current.checkResource, $(this))();
			}
		}, 50));
		_('instance-usage').select2(current.usageModalSelect2(current.$messages['service:prov:default']));
		_('instance-budget').select2(current.budgetSelect2(current.$messages['service:prov:default']));
		_('instance-processor').select2(newProcessorOpts(() => _('popup-prov-generic').provType()));
		_('instance-license').select2(genericSelect2(current.$messages['service:prov:default'], formatLicense, function () {
			if (_('instance-license').provType() === 'instance') {
				return 'instance-license/' + _('instance-os').val();
			}
			if (_('instance-license').provType() === 'container') {
				return 'container-license/' + _('instance-os').val();
			}
			if (_('instance-license').provType() === 'database') {
				return 'database-license/' + _('database-engine').val();
			}
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
					results: current.model.configuration.instances.concat(current.model.configuration.databases).concat(current.model.configuration.containers).concat(current.model.configuration.functions).map(r => {
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

		_('instance-os').select2(genericSelect2(null, formatOs, () => _('instance-os').provType() + '-os',null,ascendingComparator));
		_('instance-software').select2(genericSelect2(current.$messages['service:prov:software-none'], current.defaultToText, () => 'instance-software/' + _('instance-os').val(),null,ascendingComparator));
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
		$('.prov-rate').each(function () {
			$(this).select2({
				placeholder: $(this).is('.prov-rate-full') ? current.$messages['service:prov:no-requirement']: '',
				allowClear: true,
				formatSelection: formatRate,
				formatResult: formatRate,
				width: '48px',
				dropdownAutoWidth: true,
				escapeMarkup: m => m,
				data: Object.keys(rates).filter(r => r !== 'invalid' && r !== 'worst').map(r => ({
					id: r.toUpperCase(),
					text: r.toUpperCase()
				}))
			});
		}).closest('.input-group').addClass('with-select2');
		_('instance-term').select2(current.instanceTermSelect2(false));
	}


	/**
	 * Initialize data tables and popup event : delete and details
	 */
	function initializePopupEvents(type) {
		// Resource edition pop-up
		var popupType = typesStorage.includes(type) ? 'generic' : type;
		var $popup = _('popup-prov-' + popupType);
		$popup.on('shown.bs.modal', function () {
			var inputType = typesStorage.includes(type) ? 'instance' : type;
			_(inputType + '-name').trigger('focus');
		}).on('submit', function (e) {
			e.preventDefault();
			current.save($(this).provType());
		}).on('change','.mode-advanced input[type=checkbox]', function (e) {
			debugger;
			if(e.currentTarget.checked){
				$popup.addClass('advanced');	
			}else{
				$popup.removeClass('advanced');
			}
		}).on('show.bs.modal', function (event) {
			let $source = $(event.relatedTarget);
			const dType = $source.provType();
			var $tr = $source.closest('tr');
			var $table = $tr.closest('table');
			var quote = ($tr.length && $table.dataTable().fnGetData($tr[0])) || {};
			if (dType !== quote.resourceType && quote.resourceType !== undefined) {
				// Display sub resource
				if ($source.attr('data-id')) {
					quote = current.model.configuration[dType + 'sById'][$source.attr('data-id')];
				} else {
					quote = quote['quote' + dType.capitalize()];
				}
			}
			$(this).attr('data-prov-type', dType)
				.find('input[type="submit"]')
				.removeClass('btn-primary btn-success')
				.addClass(quote.id ? 'btn-primary' : 'btn-success');
			_('generic-modal-title').html(current.$messages['service:prov:' + dType]);
			$popup.find('.old-required').removeClass('old-required').attr('required', 'required');
			$popup.find('[data-exclusive]').removeClass('hidden').not('[data-exclusive~="' + dType + '"]').addClass('hidden').find(':required').addClass('old-required').removeAttr('required');
			$popup.find('.create-another input[type=checkbox]:checked').prop( "checked", false );
			$popup.find('div .element-advanced').addClass('advanced')
			if (initializedPopupEvents === false) {
				initializedPopupEvents = true;
				initializePopupInnerEvents();
			}
			if (quote.id) {
				current.enableCreate($popup);
			} else {
				current.disableCreate($popup);
			}
			current.model.quote = quote;
			current.toUi(dType, quote);
			_('instance-location').select2Placeholder(locationToHtml(current.model.configuration.location));
			_('instance-budget').select2Placeholder(select2Placeholder('budget'));
			_('instance-usage').select2Placeholder(select2Placeholder('usage'));
			_('instance-processor').select2Placeholder(current.model.configuration.processor || null);
			_('instance-license').select2Placeholder(formatLicense(current.model.configuration.license) || current.$messages['service:prov:license-included']);
		});
	}

	function select2Placeholder(name) {
		return current[name + 'ToText'](current.model.configuration[name]) || current.$messages[`service:prov:${name}-null`];
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
				$input.checkbox3('value', typeof data[name] === 'boolean' ? data[name] : ((data[name] !== null && typeof data[name] !== 'undefined' && data[name]) || null));
			} else if ($input.is('[type="checkbox"]')) {
				$input.prop('checked', data[name] === true || data[name]);
			} else if (typeof data[name] !== 'undefined') {
				$input.val(data[name]);
			} else if ($input.is('[type="number"]')) {
				$input.val(0);
			} else {
				$input.val('');
			}
		});
	}

	function initializeMultiScopedInnerEvents(type, toData) {
		let $popup = _(`popup-prov-${type}`).on('shown.bs.modal', function () {
			_(`${type}-name`).trigger('focus');
		}).on('submit', function (e) {
			e.preventDefault();
			current.saveOrUpdateMultiScoped(type, copyToData($(this), `${type}-`, toData()));
		});

		$popup.find('.checkbox3').checkbox3({ value: null });
		_(`prov-${type}-delete`).click(() => current.deleteMultiScoped(type, parseInt(_(`${type}-id`).val()), 10));
	}

	function initializeBudgetInnerEvents() {
		initializeMultiScopedInnerEvents('budget', () => ({
			initialCost: parseInt(_('budget-initialCost').val() || '0', 10),
		}));
	}

	function initializeUsageInnerEvents() {
		initializeMultiScopedInnerEvents('usage', () => ({
			rate: parseInt(_('usage-rate').val() || '100', 10),
			duration: parseInt(_('usage-duration').val() || '1', 10)
		}));

		$('.usage-inputs input').on('change', current.synchronizeUsage).on('keyup', current.synchronizeUsage);

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
		usageTemplates.forEach(t => current.usageTemplates[t.id] = t);
		_('usage-template').select2({
			placeholder: current.$messages['service:prov:template'],
			allowClear: true,
			formatSelection: formatUsage,
			formatResult: formatUsage,
			escapeMarkup: m => m,
			data: usageTemplates
		});
	}

	/**
	 * Configure multi-scoped resource type.
	 */
	function initializeMultiScoped(type, onShowModal, defaultData = {}) {
		let $popup = _(`popup-prov-${type}`);
		$popup.on('show.bs.modal', function (event) {
			onShowModal();
			current.enableCreate($popup);
			copyToUi($(this), `${type}-`, $(event.relatedTarget).is('.btn-success') ? defaultData : event.relatedTarget);
			validationManager.reset($(this));
			$popup.find(`input[id^=${type}-]:not(.ui-only)`).each(function () {
				let $input = $(this);
				let input = $input.attr('id');
				validationManager.mapping[input] = `${type}-${input}`;
			});
			_(`${type}-rate`).trigger('change');
		});
		_(`instance-${type}-upload`).select2(current[`${type}Select2`](current.$messages['service:prov:default']));
		let $quote = _(`quote-${type}`);
		$quote.select2(current[`${type}Select2`](current.$messages[`service:prov:${type}-null`]))
			.on('change', function (event) {
				current.updateQuote({ [type]: event.added || null }, { name: type, ui: `quote-${type}`, previous: event.removed }, true);
			});
		var $select2 = $quote.data('select2');
		if (typeof $select2.originalSelect === 'undefined') {
			$select2.originalSelect = $select2.onSelect;
		}
		$select2.onSelect = (function (fn) {
			return function (data, options) {
				if (options) {
					var $target = $(options.target).closest(`.prov-${type}-select2-action`);
					if ($target.is('.update')) {
						// Update action
						$quote.select2('close');
						$popup.modal('show', data);
						return;
					}
				}
				return fn.apply(this, arguments);
			};
		})($select2.originalSelect);
	}

	/**
	 * Configure usage.
	 */
	function initializeUsage() {
		initializeMultiScoped('usage', () => {
			if (initializedPopupUsage === false) {
				initializedPopupUsage = true;
				initializeUsageInnerEvents();
			}
			_('usage-rate').trigger('change');
		}, { rate: 100, duration: 1 });
	}

	/**
	 * Configure budget.
	 */
	function initializeBudget() {
		initializeMultiScoped('budget', () => {
			if (initializedPopupBudget === false) {
				initializedPopupBudget = true;
				initializeBudgetInnerEvents();
			}
		});
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
			initializedPopupBudget = false;
			types.forEach(type => delete current[type + 'Table']);
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
					types.forEach(type => _('prov-' + type + 's').DataTable().rows.add(current.model.configuration[type + 's']).draw(false));
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
			_('quote-budget').select2('data', conf.budget)
		},

		/**
		 * Redraw all tables
		 */
		redrawAll: function () {
			types.forEach(type => {
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
			if (quote.nbFunctions) {
				resources.push('<span class="sub-item">' + current.$super('icon')('function', 'service:prov:nb-functions') + quote.nbFunctions + ' Function</span>');
			}
			if (quote.nbContainers) {
				resources.push('<span class="sub-item">' + current.$super('icon')('fab fa-docker', 'service:prov:nb-containers') + quote.nbContainers + ' Containers</span>');
			}
			if (quote.nbInstances || quote.nbDatabases || quote.nbContainers || quote.nbFunctions) {
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
			['usage', 'budget', 'instance', 'database', 'container', 'function', 'storage', 'support'].forEach(type => toIds(conf, type));
			toIds(conf, 'location', 'name');

			// Tags case issue
			var tags = current.model && current.model.configuration.tags || {};
			Object.keys(tags).forEach(type => {
				let tagT = tags[type];
				delete tags[type];
				tags[type.toLowerCase()] = tagT;
			});

			// Storage
			conf.storageCost = 0;
			conf.storages.forEach(qs => typesStorage.forEach(type => current.attachStorage(qs, type, qs['quote' + type.capitalize()], true)));
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
			var popupType = typesStorage.includes(type) ? 'generic' : type;
			var $popup = _('popup-prov-' + popupType);

			// Build the query
			$form.find('.resource-query').filter(function () {
				return $(this).closest('[data-exclusive]').length === 0 || $(this).closest('[data-exclusive]').attr('data-exclusive').includes(type);
			}).each(function () {
				current.addQuery(type, $(this), queries);
				if (type !== 'instance' && typesStorage.includes(type)) {
					// Also include the instance inputs
					current.addQuery('instance', $(this), queries);
				}
			});
			if (type === 'storage' && queries['instance'] && _('storage-instance').select2('data')) {
				let sType = _('storage-instance').select2('data').resourceType;
				if (sType !== 'instance' && typesStorage.includes(sType)) {
					// Replace the resource lookup
					queries[sType] = queries['instance'];
					delete queries['instance'];
				}
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
					current[popupType + 'SetUiPrice'](suggest);
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
		 * Set the current instance/database/container/function price.
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
				suggests.forEach(s => s.id = s.id || s.price.id);
				return suggests;
			}
			return null;
		},

		/**
		 * Initialize data tables and popup event : delete and details
		 */
		initializeDataTableEvents: function (type) {
			var oSettings = current[type + 'NewTable']();
			var popupType = typesStorage.includes(type) ? 'generic' : type;
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
				className: 'truncate',
				type: 'string',
				render: formatName
			});
			oSettings.columns.push(
				{
					data: null,
					orderable: false,
					className: 'truncate hidden-xs',
					type: 'string',
					filterName: 'tags',
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
				_('instance-budget-upload-name').val((_('instance-budget-upload').select2('data') || {}).name || null);
				_('csv-headers-included').val(_('csv-headers-included').is(':checked') ? 'true' : 'false');
				_('csv-errors').val(_('csv-headers-included').is(':checked') ? 'true' : 'false');
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
			// Global data tables filter
			if ($.fn.dataTable.ext.search.length === 0) {
				$.fn.dataTable.ext.search.push(
					function (settings, dataFilter, dataIndex, data) {
						var type = settings.oInit.provType;
						// Save the last used filter
						if (typeof type === 'undefined') {
							return true;
						}

						var filter = settings.oPreviousSearch.sSearchAlt || '';
						if (type === 'storage' && typesStorage.some(sType => current[sType + 'TableFilter'] !== '')) {
							// Only storage rows unrelated to filtered instance/database/container/function can be displayed
							// There are 2 operators: 
							// - 'in' = 's.instance NOT NULL AND s.instance IN (:table)' - IN
							// - 'lj' = 's.instance IS NULL OR s.instance IN (:table)' - LEFT JOIN
							return current.filterManager.accept(settings, type, dataFilter, data, filter, {
								cache: typesStorage.map(sType => current[sType + 'TableFilter'] !== '').join('/'),
								filters: typesStorage.map(sType => ({ property: 'quote' + sType.capitalize(), op: 'in', table: current[sType + 'TableFilter'] && current[sType + 'Table'] }))
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

						if (typesStorage.includes(type)) {
							// Refresh the storage
							var tableS = current['storageTable'];
							tableS.fnSettings().oPreviousSearch.sSearch = '§force§';
							tableS.fnFilter('');
						}
						current.updateUiCost();
					}
				}
			}, 200));
			types.forEach(type => current[type + 'TableFilter'] = '');
			types.forEach(type => {
				current.initializeDataTableEvents(type);
				if (type !== 'database' && type !== 'container' && type !== 'function') {
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
			$('#subscribe-configuration-prov').on('mouseup', '.select2-search-choice [data-prov-type]', function () {
				$('#popup-prov-storage').modal('show', $(this))
			});
			$('.service-icon').addClass(`fa-fw ${current.model.node.tool.uiClasses}`);
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
			initializeBudget();
			current.initializeOtherAssumptionsComponents();
			current.updateUiAssumptions(current.model.configuration);
			$('.prov-currency').text(getCurrencyUnit());
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
				current.updateD3StatsRate(percent);
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
		 * Usage Modale Select2 configuration.
		 */
		usageModalSelect2: function (placeholder) {
			return genericSelect2(placeholder, current.usageToText, 'usage', function (usage) {
				return `${usage.name}<span class="select2-usage-summary pull-right"><span class="x-small">(${usage.rate}%) </span>`;
			});
		},

		/**
		 * Budget Select2 configuration.
		 */
		budgetSelect2: function (placeholder) {
			return genericSelect2(placeholder, current.budgetToText, 'budget', function (budget) {
				return `${budget.name}<span class="select2-budget-summary pull-right"><span class="x-small">(${formatCost(budget.initialCost)}) </span><a class="update prov-budget-select2-action"><i data-toggle="tooltip" title="${current.$messages.update}" class="fas fa-fw fa-pencil-alt"></i><a></span>`;
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
				usage: conf.usage,
				budget: conf.budget
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
			if (jsonData.budget) {
				jsonData.budget = jsonData.budget.name;
			} else {
				delete jsonData.budget;
			}

			// Check the changes
			if (conf.name === jsonData.name
				&& conf.description === jsonData.description
				&& (conf.location && conf.location.name) === jsonData.location
				&& (conf.usage && conf.usage.name) === jsonData.usage
				&& (conf.budget && conf.budget.name) === jsonData.budget
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
					conf.usage = data.usage === null ? null : (data.usage || conf.usage);
					conf.budget = data.budget === null ? null : (data.budget || conf.budget);
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
						var value = data[context.name] ? data[context.name].name || data[context.name].text || data[context.name].id || data[context.name] : null;
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
		 * Delete a multi-scoped entity by its name.
		 * @param {string} type The multi-scoped resource type (usage, budget,...) of resource to delete.
		 * @param {string} id The resource identifier to delete.
		 */
		deleteMultiScoped: function (type, id) {
			var conf = current.model.configuration;
			let ids = conf[type + 'sById'];
			var name = ids[id].name;
			var $popup = _(`popup-prov-${type}`);
			current.disableCreate($popup);
			$.ajax({
				type: 'DELETE',
				url: `${REST_PATH}service/prov/${current.model.subscription}/${type}/${id}`,
				dataType: 'json',
				contentType: 'application/json',
				success: function (newCost) {
					// Commit to the model
					if (conf[type] && conf[type].id === id) {
						// Update the resource of the quote
						delete conf[type];
						_(`prov-${type}`).select2('data', null);
					}
					delete ids[id];

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
		 * Update a multi scoped resource.
		 * @param {string} type The multi-scoped resource type (usage, budget,...) of resource to delete.
		 * @param {string} data The multi-scoped data to persist.
		 */
		saveOrUpdateMultiScoped: function (type, data) {
			var conf = current.model.configuration;
			var $popup = _(`popup-prov-${type}`);
			let ids = conf[`${type}sById`];
			current.disableCreate($popup);
			var method = data.id ? 'PUT' : 'POST'
			$.ajax({
				type: method,
				url: `${REST_PATH}service/prov/${current.model.subscription}/${type}`,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (newCost) {
					// Commit to the model
					data.id = data.id || newCost.id || newCost;
					if (ids[data.id]) {
						Object.assign(ids[data.id], data);
					} else {
						ids[data.id] = data;
					}
					if (conf[type] && conf[type].id === data.id) {
						// Update the resource of the quote
						conf[type] = data;
						_(`quote-${type}`).select2('data', data);
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
		 * Usage text renderer.
		 */
		usageToText: function (usage) {
			return usage ? usage.rate === 100 ? usage.name +'<small class="pull-right">'+(usage.duration === 1 ? '': usage.duration+'M')+'</small>': (usage.name +'<small class="pull-right">'+ (usage.duration === 1 ? '': usage.duration+'M') + '<span class="pull-right"> (' + usage.rate + '%)<span> <small>') : null;
		},
		/**
		 * Budget text renderer.
		 */
		budgetToText: function (budget) {
			return budget ? budget.text || (budget.name + '<span class="pull-right">(' + formatCost(budget.initialCost) + ')<span>') : null;
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
			typesStorage.forEach(type => current.attachStorage(model, type, data[type]));
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
			model.cpuRate = data.cpuRate;
			model.ramRate = data.ramRate;
			model.networkRate = data.networkRate;
			model.storageRate = data.storageRate;
			model.internet = data.internet;
			model.minQuantity = parseInt(data.minQuantity, 10);
			model.maxQuantity = data.maxQuantity ? parseInt(data.maxQuantity, 10) : null;
			model.constant = data.constant;
			model.physical = data.physical;
		},
		computeCommitToModel: function (data, model) {
			current.genericCommitToModel(data, model);
			model.maxVariableCost = parseFloat(data.maxVariableCost, 10);
			model.ephemeral = data.ephemeral;
			model.os = data.os;
		},


		instanceCommitToModel: function (data, model) {
			current.computeCommitToModel(data, model);
		},

		containerCommitToModel: function (data, model) {
			current.computeCommitToModel(data, model);
		},

		functionCommitToModel: function (data, model) {
			current.computeCommitToModel(data, model);
			model.nbRequests = data.nbRequests;
			model.concurrency = data.concurrency;
			model.duration = data.duration;

		},

		databaseCommitToModel: function (data, model) {
			current.genericCommitToModel(data, model);
			model.engine = data.engine;
			model.edition = data.edition;
		},

		storageUiToData: function (data) {
			typesStorage.forEach(sType => delete data[sType]);
			let storage = _('storage-instance').select2('data');
			if (storage) {
				data[storage.resourceType] = storage.id;
			}
			data.size = cleanInt(_('storage-size').val());
			data.optimized = _('storage-optimized').val();
			data.latency = _('storage-latency').val();
			data.type = _('storage-price').select2('data').price.type.code;
		},

		supportUiToData: function (data) {
			data.seats = cleanInt(_('support-seats').val());
			data.accessApi = (_('support-access-api').select2('data') || {}).id || null;
			data.accessEmail = (_('support-access-email').select2('data') || {}).id || null;
			data.accessPhone = (_('support-access-phone').select2('data') || {}).id || null;
			data.accessChat = (_('support-access-chat').select2('data') || {}).id || null;
			data.level = (_('support-level').select2('data') || {}).id || null;
			data.type = _('support-price').select2('data').price.type.code;
		},

		genericUiToData: function (data) {
			data.cpu = cleanFloat(_('instance-cpu').provSlider('value', 'reserved'));
			data.cpuMax = cleanFloat(_('instance-cpu').provSlider('value', 'max'));
			data.ram = cleanRam('reserved');
			data.ramMax = cleanRam('max');
			data.cpuRate = _('instance-cpuRate').val();
			data.ramRate = _('instance-ramRate').val();
			data.networkRate = _('instance-networkRate').val();
			data.storageRate = _('instance-storageRate').val();
			data.internet = _('instance-internet').val().toLowerCase();
			data.processor = _('instance-processor').val().toLowerCase();
			data.minQuantity = cleanInt(_('instance-min-quantity').val()) || 0;
			data.maxQuantity = cleanInt(_('instance-max-quantity').val()) || null;
			data.license = _('instance-license').val().toLowerCase() || null;
			data.constant = toQueryValue3States(_('instance-constant'));
			data.physical = toQueryValue3States(_('instance-physical'));
			data.price = _('instance-price').select2('data').price.id;
		},

		computeUiToData: function (data) {
			current.genericUiToData(data)
			data.maxVariableCost = cleanFloat(_('instance-max-variable-cost').val());
			data.ephemeral = _('instance-ephemeral').is(':checked');
			data.os = _('instance-os').val().toLowerCase();
		},

		instanceUiToData: function (data) {
			current.computeUiToData(data);
			data.software = _('instance-software').val().toLowerCase() || null;
		},

		containerUiToData: function (data) {
			current.computeUiToData(data);
		},

		functionUiToData: function (data) {
			current.computeUiToData(data);
			data.nbRequests = cleanInt(_('function-nbRequests').val()) || 0;
			data.concurrency = cleanInt(_('function-concurrency').val()) || 0;
			data.duration = cleanInt(_('function-duration').val()) || 0;
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
			var popupType = typesStorage.includes(type) ? 'generic' : type;
			var inputType = typesStorage.includes(type) ? 'instance' : type;
			var $popup = _('popup-prov-' + popupType);
			validationManager.reset($popup);
			_(inputType + '-name').val(model.name || current.findNewName(current.model.configuration[type + 's'], type));
			_(inputType + '-description').val(model.description || '');
			_(inputType + '-location').select2('data', model.location || null);
			_(inputType + '-usage').select2('data', model.usage || null);
			_(inputType + '-budget').select2('data', model.budget || null);
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
			_('instance-cpuRate').select2('data', current.select2IdentityData((quote.cpuRate) || null));
			_('instance-ramRate').select2('data', current.select2IdentityData((quote.ramRate) || null));
			_('instance-networkRate').select2('data', current.select2IdentityData((quote.networkRate) || null));
			_('instance-storageRate').select2('data', current.select2IdentityData((quote.storageRate) || null));
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
		 * Fill the instance like popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		computeToUi: function (quote) {
			current.genericToUi(quote);
			_('instance-max-variable-cost').val(quote.maxVariableCost || null);
			_('instance-ephemeral').prop('checked', quote.ephemeral);
			_('instance-os').select2('data', current.select2IdentityData((quote.id && (quote.os || quote.price.os)) || 'LINUX'));
			_('instance-internet').select2('data', current.select2IdentityData(quote.internet || 'PUBLIC'));
			current.updateAutoScale();
			current.genericSetUiPrice(quote);
		},

		/**
		 * Fill the instance popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		instanceToUi: function (quote) {
			current.computeToUi(quote);
		},

		/**
		 * Fill the container popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		containerToUi: function (quote) {
			current.computeToUi(quote);
		},

		/**
		 * Fill the function popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		functionToUi: function (quote) {
			current.computeToUi(quote);
			_('function-nbRequests').val(quote.nbRequests || 1);
			_('function-concurrency').val(quote.concurrency || 0);
			_('function-duration').val(quote.duration || 100);
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
			current.genericSetUiPrice(quote);
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
			_('storage-instance').select2('data', quote.quoteInstance || quote.quoteDatabase || quote.quoteContainer || quote.quoteFunction || null);
			current.storageSetUiPrice(quote);
		},

		/**
		 * Auto select the right RAM unit depending on the RAM amount.
		 * @param {int} ram, the RAM value in MB.
		 */
		adaptRamUnit: function (ram) {
			_('instance-ram-unit').find('li.active').removeClass('active');
			if (ram && ram >=1048576 && (ram / 1048576) % 1 === 0) {
				// Auto select TB
				_('instance-ram-unit').find('li:last-child').addClass('active');
				ram = ram / 1048576
			} else if (ram && ram >= 1024 && (ram / 1024) % 1 === 0) {
				// Keep GB
				_('instance-ram-unit').find('li:nth-child(2)').addClass('active');
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
		 * Save a storage or an instance/database/.. from the corresponding popup. Handle the cost delta,  update the model, then the UI.
		 * @param {string} type Resource type to save.
		 */
		save: function (type) {
			var popupType = typesStorage.includes(type) ? 'generic' : type;
			var inputType = typesStorage.includes(type) ? 'instance' : type;
			var $popup = _('popup-prov-' + popupType);

			// Build the play load for API service
			var suggest = {
				price: _(inputType + '-price').select2('data'),
				usage: _(inputType + '-usage').select2('data'),
				budget: _(inputType + '-budget').select2('data'),
				location: _(inputType + '-location').select2('data')
			};
			var data = {
				id: current.model.quote.id,
				name: _(inputType + '-name').val(),
				description: _(inputType + '-description').val(),
				location: (suggest.location || {}).name,
				usage: (suggest.usage || {}).name,
				budget: (suggest.budget || {}).name,
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
					current.saveAndUpdateCosts(type, updatedCost, data, suggest.price, suggest.usage, suggest.budget, suggest.location);
					if ($popup.find('.create-another input[type=checkbox]:checked').is(':checked')){
						current.enableCreate($popup);
						$(_(inputType + '-name')).focus();		
					} else {
						$popup.modal('hide');					
					}
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
		 * @param {object} budget The last provided budget.
		 * @param {object} location The last provided location.
		 * @return {object} The updated or created model.
		 */
		saveAndUpdateCosts: function (type, updatedCost, data, price, usage, budget, location) {
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
			qx.budget = budget;
			qx.resourceType = type;
			qx.quantity=data.quantity;

			// Specific data
			current[type + 'CommitToModel'](data, qx);

			// With related cost, other UI table need to be updated
			current.defaultCallback(type, updatedCost, qx);
			return qx;
		},

		/**
		 * Update the D3 instance types bar chart.
		 * @param {object} stats 
		 */
		updateInstancesBarChart: function (stats) {
			require(['d3', '../main/service/prov/lib/stacked'], function (d3, d3Bar) {
				var numDataItems = stats.timeline.length;
				var data = [];
				for (var i = 0; i < numDataItems; i++) {
					var value = stats.timeline[i];
					var stack = {
						date: value.date
					};
					types.forEach(type => stack[type] = value[type]);
					data.push(stack);
				}

				if (stats.cost) {
					$("#prov-barchart").removeClass('hidden');
					if (typeof current.d3Bar === 'undefined') {
						current.d3Bar = d3Bar;
						d3Bar.create("#prov-barchart .prov-barchart-svg", false, d3[colorScheme], parseInt($('#prov-barchart').css('width')), 150, data, (d, bars) => {
							// Tooltip of barchart
							var tooltip = current.$messages['service:prov:date'] + ': ' + d.x;
							tooltip += '<br/>' + current.$messages['service:prov:total'] + ': ' + formatCost(bars.reduce((cost, bar) => cost + bar.height0, 0));
							types.forEach(type => {
								var cost = bars.filter(bar => bar.cluster === type);
								if (cost.length && cost[0].height0) {
									tooltip += '<br/><span' + (d.cluster === type ? ' class="strong">' : '>') + current.$messages['service:prov:' + type] + ': ' + formatCost(cost[0].height0) + '</span>';
								}
							});
							return '<span class="tooltip-text">' + tooltip + '</span>';
						}, d => {
							// Hover of barchart -> update sunburst and global cost
							current.updateUiCost(d && d['x-index']);
						}, (d, bars, clicked) => {
							// Hover of barchart -> update sunburst and global cost
							current.updateUiCost(clicked && d && d['x-index']);
						}, d => formatCost(d, null, null, true), (a, b) => types.indexOf(a) - types.indexOf(b));
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

		updateComputeUiConst: function (stats, type) {
			let $summary = $(`.nav-pills [href="#tab-${type}"] .summary> .badge`);
			if (stats[type].cpu.available) {
				let sStats = stats[type];
				current.updateSummary($summary, sStats);
				let $oss = $summary.filter('[data-os]').addClass('hidden');
				Object.keys(sStats.oss).forEach(o => $oss.filter('[data-os="' + o + '"]').removeClass('hidden').find('span').text(sStats.oss[o]));
			} else {
				$summary.addClass('hidden');
			}
		},

		updateFunctionUiConst: function (stats) {
			let $summary = $('.nav-pills [href="#tab-function"] .summary> .badge');
			if (stats.function.nbRequests) {
				stats.cpu = { available: 0 };
				stats.ram = { available: 0 };
				current.updateSummary($summary, stats);
				$summary.find('.requests').find('span').text(stats.function.nbRequests);
			} else {
				$summary.addClass('hidden');
			}
		},

		/**
		 * Update the total cost of the quote.
		 */
		updateUiCost: function (filterDate) {
			var conf = current.model.configuration;

			// Compute the new capacity and costs
			var stats = current.computeStats(filterDate);

			// Update the global counts
			var filtered = stats.cost !== conf.cost.min;
			if (filtered) {
				// Filtered cost
				formatCost({
					min: stats.cost,
					max: stats.cost,
					unbound: stats.unbound > 0
				}, $('.cost'));
			} else {
				// Full cost
				formatCost(conf.cost, $('.cost'));
			}

			if (typeof filterDate !== 'number') {
				// Do not update itself
				current.updateInstancesBarChart(stats);
			}

			// Separated resource counters
			types.forEach(type => {
				var $stats = $('.nav-pills [href="#tab-' + type + '"] .prov-resource-counter');
				if (stats[type].nb) {
					$stats.removeClass('hide').find('.odo-wrapper').text(stats[type].nb);
					$stats.find('.odo-wrapper-unbound').text((stats[type].min && stats[type].min > stats[type].nb || stats[type].unbound) ? '+' : '');
				} else {
					$stats.addClass('hide');
				}
			});

			// Instance summary
			current.updateComputeUiConst(stats, 'instance');

			// Container summary
			current.updateComputeUiConst(stats, 'container');

			// Function summary
			current.updateFunctionUiConst(stats);

			// Database summary
			let $summary = $('.nav-pills [href="#tab-database"] .summary> .badge');
			if (stats.database.cpu.available) {
				current.updateSummary($summary, stats.database);
				var $engines = $summary.filter('[data-engine]').addClass('hidden');
				Object.keys(stats.database.engines).forEach(engine => $engines.filter('[data-engine="' + engine + '"]').removeClass('hidden').find('span').text(stats.database.engines[engine]));
			} else {
				$summary.addClass('hidden');
			}

			// Storage summary
			$summary = $('.nav-pills [href="#tab-storage"] .summary> .badge.size');
			if (stats.storage.available) {
				$summary.removeClass('hidden');
				$summary.text(formatStorage(stats.storage.available));
			} else {
				$summary.addClass('hidden');
			}

			// Support summary
			$summary = $('.nav-pills [href="#tab-support"] .summary');
			if (stats.support.first) {
				$summary.removeClass('hidden').find('.support-first').text(stats.support.first).attr("title", stats.support.first);
				if (stats.support.more) {
					$summary.find('.support-more').removeClass('hidden').text(stats.support.more);
				} else {
					$summary.find('.support-more').addClass('hidden');
				}
			} else {
				$summary.addClass('hidden');
			}

			// Update the gauge : reserved / available
			require(['d3', '../main/service/prov/lib/gauge'], function (d3) {
				if (typeof current.d3Gauge === 'undefined' && stats.cost) {
					current.d3Gauge = d3;
					d3.select('#prov-gauge').call(d3.liquidFillGauge, 1, {
						textColor: '#FF4444',
						textVertPosition: 0.5,
						waveAnimateTime: 600,
						waveHeight: 0.9,
						textSize: 1.5,
						backgroundColor: '#e0e0e0'
					});
					$(function () {
						current.updateGauge(d3, stats);
					});
				} else {
					current.updateGauge(d3, stats);
				}
			});

			// Update the sunburst total resource capacity
			require(['d3', '../main/service/prov/lib/sunburst'], function (d3, sunburst) {
				if (stats.cost) {
					sunburst.init('#prov-sunburst', current.toD3(stats), function (a, b) {
						return types.indexOf(a.data.type) - types.indexOf(b.data.type);
					}, current.sunburstTooltip, d3[colorScheme]);
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

		sunburstTooltip: function (data) {
			var tooltip = current.sunburstBaseTooltip(data)
			return '<span class="tooltip-text">' + tooltip
				+ '</br>' + current.$messages['service:prov:cost'] + ': ' + formatCost(data.size || data.value)
				+ current.recursivePercent(data, true, 100)
				+ (data.depth && data.children ? '</br>' + current.$messages['service:prov:nb'] + ': ' + (data.min || data.nb || data.children.length) : '') + '</span>';
		},

		title: function (key, icon) {
			return (typeof icon === 'string' ? `<i class="fas ${icon} fa-fw"></i> ` : '') + (current.$messages['service:prov:' + key] || current.$messages[key]) + ': ';
		},

		sunburstVmTooltip: function (entity) {
			return '<br>' + current.title('term') + entity.price.term.name
				+ '<br>' + current.title('usage') + formatUsage(entity.usage, 'tooltip')
				+ '<br>' + current.title('budget') + formatBudget(entity.budget, 'tooltip');
		},

		sunburstComputeTooltip: function (conf, data, type) {
			const entity = conf[type + 'sById'][data.name];
			return current.title('name') + entity.name
				+ '<br>' + current.title(type + '-type') + entity.price.type.name
				+ '<br>' + current.title('os') + formatOs(entity.price.os, true)
				+ current.sunburstVmTooltip(entity);
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
					return current.sunburstComputeTooltip(conf, data, 'instance');
				case 'container':
					return current.sunburstComputeTooltip(conf, data, 'container');
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
					const database = conf.databasesById[data.name];
					return current.title('name') + database.name
						+ '<br>' + current.title('database-type') + database.price.type.name
						+ '<br>' + current.title('database-engine') + formatDatabaseEngine(database.price.engine, true) + (database.price.edition ? '/' + database.price.edition : '')
						+ current.sunburstVmTooltip(database);
				default:
					return (data.type && data.type.startsWith(ROOT_PREFIX)) ? `<i class="${typeIcons[data.type.substring(ROOT_PREFIX.length)]} fa-2x"></i> ${data.name}` : data.name;
			}
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
		 * Update the gauge value depending on the computed stats.
		 */
		updateGauge: function (d3, stats) {
			if (d3.select('#prov-gauge').on('valueChanged') && stats.costNoSupport) {
				var weightCost = 0;
				typesStorage.forEach(sType => {
					if (stats[sType].cpu.available) {
						weightCost += stats[sType].cost * 0.8 * stats[sType].cpu.reserved / stats[sType].cpu.available;
					}
					if (stats[sType].ram.available) {
						weightCost += stats[sType].cost * 0.2 * stats[sType].ram.reserved / stats[sType].ram.available;
					}
				});
				if (stats.storage.available) {
					weightCost += stats.storage.cost * stats.storage.reserved / stats.storage.available;
				}
				_('prov-gauge').removeClass('hidden');
				// Weight average of average...
				d3.select('#prov-gauge').on('valueChanged')(Math.floor(weightCost * 100 / stats.costNoSupport));
			} else {
				$('#prov-gauge').addClass('hidden');
			}
		},

		getFilteredData: function (type, filterDate) {
			var result = [];
			if (current[type + 'Table']) {
				var data = _('prov-' + type + 's').DataTable().rows({ filter: 'applied' }).data();
				for (let index = 0; index < data.length; index++) {
					result.push(data[index]);
				}
			} else {
				result = current.model.configuration[type + 's'] || [];
			}
			if (typeof filterDate === 'number' && (typesStorage.includes(type) || type === 'storage')) {
				let usage = current.model.configuration.usage || {};
				return result.filter(qi => {
					let rUsage = (qi.quoteInstance || qi.quoteDatabase || qi.quoteContainer || qi.quoteFunction || qi).usage || usage;
					let start = rUsage.start || 0;
					return start <= filterDate;
				});
			}
			return result;
		},

		computeStatsType: function (conf, filterDate, reservationModeMax, defaultUsage, duration, timeline, type, result, callback, callbackQi) {
			let ramAdjustedRate = conf.ramAdjustedRate / 100;
			let publicAccess = 0;
			let instances = current.getFilteredData(type, filterDate);
			let ramAvailable = 0;
			let ramReserved = 0;
			let cpuAvailable = 0;
			let cpuReserved = 0;
			let instanceCost = 0;
			let minInstances = 0;
			let maxInstancesUnbound = false;
			let enabledInstances = {};
			let resultType = {};
			if (typeof callback === 'function') {
				callback(resultType);
			}
			instances.forEach(qi => {
				let cost = qi.cost.min || qi.cost || 0;
				let nb = qi.minQuantity || 1;
				minInstances += nb;
				maxInstancesUnbound |= (qi.maxQuantity !== nb);
				cpuAvailable += qi.price.type.cpu * nb;
				cpuReserved += ((reservationModeMax && qi.cpuMax) ? qi.cpuMax : qi.cpu) * nb;
				ramAvailable += qi.price.type.ram * nb;
				ramReserved += ((reservationModeMax && qi.ramMax) ? qi.ramMax : qi.ram) * ramAdjustedRate * nb;
				instanceCost += cost;
				publicAccess += (qi.internet === 'public') ? 1 : 0;
				enabledInstances[qi.id] = true;
				if (typeof callbackQi === 'function') {
					callbackQi(resultType, qi);
				}
				for (let t = (qi.usage || defaultUsage).start || 0; t < duration; t++) {
					timeline[t][type] += cost;
					timeline[t].cost += cost;
				}
			});
			result[type] = Object.assign(resultType, {
				nb: instances.length,
				min: minInstances,
				unbound: maxInstancesUnbound,
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
				enabled: enabledInstances,
				cost: instanceCost
			});
		},

		/**
		 * Compute the global resource stats of this quote and the available capacity. Only minimal quantities are considered and with minimal to 1.
		 * Maximal quantities is currently ignored.
		 */
		computeStats: function (filterDate) {
			var conf = current.model.configuration;
			var i, t;
			let reservationModeMax = conf.reservationMode === 'max';

			// Timeline
			var timeline = [];
			var defaultUsage = conf.usage || { rate: 100, start: 0 };
			var duration = 36;
			var date = moment().startOf('month');
			for (i = 0; i < 36; i++) {
				let monthData = { cost: 0, month: date.month(), year: date.year(), date: date.format('MM/YYYY'), storage: 0, support: 0 };
				typesStorage.forEach(type => monthData[type] = 0);
				timeline.push(monthData);
				date.add(1, 'months');
			}

			let result = {};
			// Instance statistics
			current.computeStatsType(conf, filterDate, reservationModeMax, defaultUsage, duration, timeline, 'instance', result, r => r.oss = {}, (r, qi) => r.oss[qi.os] = (r.oss[qi.os] || 0) + 1);
			current.computeStatsType(conf, filterDate, reservationModeMax, defaultUsage, duration, timeline, 'container', result, r => r.oss = {}, (r, qi) => r.oss[qi.os] = (r.oss[qi.os] || 0) + 1);
			current.computeStatsType(conf, filterDate, reservationModeMax, defaultUsage, duration, timeline, 'function', result, r => r.nbRequests = 0, (r, qi) => {
				r.nbRequests += qi.nbRequests;
			});
			current.computeStatsType(conf, filterDate, reservationModeMax, defaultUsage, duration, timeline, 'database', result, r => r.engines = {}, (r, qi) => {
				let engine = qi.engine.replace(/AURORA .*/, 'AURORA');
				r.engines[engine] = (r.engines[engine] || 0) + 1;
			});

			// Storage statistics
			var storageAvailable = 0;
			var storageReserved = 0;
			var storageCost = 0;
			var storages = current.getFilteredData('storage', filterDate);
			let nb = 0;
			storages.forEach(qs => {
				if (qs.quoteInstance) {
					nb = result.instance.enabled[qs.quoteInstance.id] && qs.quoteInstance.minQuantity || 1;
				} else if (qs.quoteDatabase) {
					nb = result.database.enabled[qs.quoteDatabase.id] && qs.quoteDatabase.minQuantity || 1;
				} else if (qs.quoteFunction) {
					nb = 1;
				} else if (qs.quoteContainer) {
					nb = result.container.enabled[qs.quoteContainer.id] && qs.quoteContainer.minQuantity || 1;
				} else {
					nb = 1;
				}

				var qsSize = (reservationModeMax && qs.sizeMax) ? qs.sizeMax : qs.size;
				storageAvailable += Math.max(qsSize, qs.price.type.minimal) * nb;
				storageReserved += qsSize * nb;
				storageCost += qs.cost;
				var quoteVm = qs.quoteDatabase || qs.quoteInstance || qs.quoteContainer || qs.quoteFunction;
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
			});

			// Support statistics
			var supportCost = 0;
			var supports = current.getFilteredData('support', filterDate);
			supports.forEach(s => supportCost += s.cost);
			for (t = 0; t < duration; t++) {
				timeline[t].support = supportCost;
				timeline[t].cost += supportCost;
			}

			let costNoSupport = typesStorage.reduce((total, sType) => total + result[sType].cost, storageCost);
			return Object.assign(result, {
				cost: costNoSupport + supportCost,
				costNoSupport: costNoSupport,
				unbound: typesStorage.some(sType => result[sType].maxInstancesUnbound),
				timeline: timeline,
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
			});
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

		toD3: function (stats) {
			var root = {
				name: current.$messages['service:prov:total'],
				value: stats.cost,
				children: []
			};
			types.forEach(type => {
				var data = {
					value: 0,
					type: ROOT_PREFIX + type,
					children: [],
					nb: stats[type].nb,
					min: stats[type].min,
					unbound: stats[type].unbound,
					name: current.$messages[`service:prov:${type}s-block`]
				};
				current[type + 'ToD3'](data, stats);
				root.children.push(data);
			});
			return root;
		},
		computeToD3: function (data, stats, type) {
			var allOss = {};
			stats[type].filtered.forEach(qi => {
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
					type: type,
					size: qi.cost
				});
			});
		},

		instanceToD3: function (data, stats) {
			current.computeToD3(data, stats, 'instance');
		},
		containerToD3: function (data, stats) {
			current.computeToD3(data, stats, 'container');
		},
		databaseToD3: function (data, stats) {
			var allEngines = {};
			stats.database.filtered.forEach(qi => {
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
			});
		},
		functionToD3: function (data, stats) {
			stats.function.filtered.forEach(qi => {
				data.value += qi.cost;
			});
		},
		storageToD3: function (data, stats) {
			data.name = current.$messages['service:prov:storages-block'];
			var allOptimizations = {};
			stats.storage.filtered.forEach(qs => {
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
			});
		},

		supportToD3: function (data, stats) {
			data.name = current.$messages['service:prov:support-block'];
			stats.support.filtered.forEach(support => {
				data.value += support.cost;
				data.children.push({
					name: support.id,
					type: 'support',
					size: support.cost
				});
			});
		},

		/**
		 * Initialize the instance like data tables from the whole quote
		 */
		computeNewTable: function (type) {
			return current.genericInstanceNewTable(type, [{
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
		 * Initialize the instance data tables from the whole quote
		 */
		instanceNewTable: function () {
			return current.computeNewTable('instance');
		},

		/**
		 * Initialize the container data tables from the whole quote
		 */
		containerNewTable: function () {
			return current.computeNewTable('container');
		},

		/**
		 * Initialize the function data tables from the whole quote
		 */
		functionNewTable: function () {
			return current.genericInstanceNewTable('function', [{
				data: 'nbRequests',
				type: 'num',
				className: 'truncate'
			}, {
				data: 'duration',
				type: 'num',
				className: 'truncate'
			}, {
				data: 'concurrency',
				type: 'num',
				className: 'truncate'
			}]);
		},
		/**
		 * Initialize the support data tables from the whole quote
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
		 * Initialize the storage data tables from the whole quote
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
					render: (_i, mode, data) => formatQuantity(null, mode, (data.quoteInstance || data.quoteDatabase || data.quoteContainer || data.quoteFunction))
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
					render: formatRate
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
					render: (_i, mode, data) => formatQuoteResource(data.quoteInstance || data.quoteDatabase || data.quoteContainer || data.quoteFunction)
				}]
			};
		},

		/**
		 * Donut of stats
		 * @param {integer} rate The rate percentage 1-100%
		 */
		updateD3StatsRate: function (rate) {
			require(['d3', '../main/service/prov/lib/donut'], function (d3, donut) {
				if (current.contextDonut) {
					donut.update(current.contextDonut, rate);
				} else {
					current.contextDonut = donut.create("#efficiency-chart", rate, 250, 250);
				}
			});
		},

		/**
		 * Initialize the database data tables from the whole quote
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
		 * Initialize the database/... data tables from the whole quote
		 */
		genericInstanceNewTable: function (type, columns) {
			return {
				rowCallback: function (nRow, qi) {
					//debugger;
					current.rowCallback($(nRow), qi);
					$(nRow).find('.storage-tags').select2('destroy').select2({
						multiple: true,
						dropdownAutoWidth: true,
						minimumInputLength: 1,
						createSearchChoice: () => null,
						formatInputTooShort: current.$messages['service:prov:storage-select'],
						formatResult: formatStoragePriceHtml,
						formatSelection: formatStorageHtml,
						ajax: {
							url: REST_PATH + 'service/prov/' + current.model.subscription + '/storage-lookup?' + type + '=' + qi.id,
							dataType: 'json',
							data: function (term) {
								const regex=/(([\d]+)\s*[*x]\s*)?(\d+)/
								const RexExp =term.match(regex)
								return {
									size: $.isNumeric(RexExp[3]) ? parseInt(RexExp[3], 10) : 1, // search term
								};
							},
							results: function (data,query_page,query) {
								const regex=/(([\d]+)\s*[*x]\s*)?(\d+)/;
								const RexExp =query.term.match(regex);

								// Completed the requested identifier
								data.forEach(quote => {
										quote.id = quote.price.id + '-' + new Date().getMilliseconds();
										quote.text = quote.price.type.name;	
										quote.quantity= parseInt(RexExp[2])								
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
								type: suggest.price.type.code,
								size: suggest.size,
								quantity: suggest.quantity,
								instance: type === 'instance' && qi.id,
								database: type === 'database' && qi.id,
								function: type === 'function' && qi.id,
								container: type === 'container' && qi.id,
								subscription: current.model.subscription,
								optimized: suggest.price.type.optimized,
								latency: suggest.price.type.latency
							};
							current.$main.trimObject(data);
							$.ajax({
								type: 'POST',
								url: REST_PATH + 'service/prov/storage',
								dataType: 'json',
								contentType: 'application/json',
								data: JSON.stringify(data),
								success: function (updatedCost) {
									current.saveAndUpdateCosts('storage', updatedCost, data, suggest, null, null, qi.location);

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
					render: formatUsage,
					filter: filterMultiScoped('usage')
				}, {
					data: 'budget',
					className: 'hidden-xs hidden-sm budget',
					type: 'string',
					render: formatBudget,
					filter: filterMultiScoped('budget')
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

			// Update the resource cost
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
				console.log('Need to readjust the computed cost: min=' + (updatedCost.total.min - conf.cost.min)
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
						var qr = resource.quoteInstance || resource.quoteDatabase || resource.quoteContainer || resource.quoteFunction;
						if (qr) {
							// Also redraw the instance
							var attachedType = qr.resourceType;
							current.detachStorage(resource, 'quote' + attachedType.capitalize());
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