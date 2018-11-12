/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
/*jshint esversion: 6*/
define(function () {
	var current = {

		/**
		 * Current quote.
		 */
		model: null,

		/**
		 * Usage rate templates
		 */
		usageTemplates: {},

		contextDonut: null,

		/**
		 * Show the members of the given group
		 */
		configure: function (subscription) {
			current.model = subscription;
			delete current.d3Arc;
			delete current.d3Gauge;
			$('.loader-wrapper').addClass('hidden');
			require(['text!../main/service/prov/menu.html'], function (menu) {
				_('service-prov-menu').empty().remove();
				_('extra-menu').append($(Handlebars.compile(menu)(current.$messages)));
				current.initOdometer();
				current.optimizeModel();
				current.initializeForm();
				current.initializeUpload();
				_('subscribe-configuration-prov').removeClass('hide');
				$('.provider').text(current.model.node.name);
				_('name-prov').val(current.model.configuration.name);
				var now = moment();
				$('.prov-export-instances-inline').attr('href', REST_PATH + 'service/prov/' + subscription.id + '/ligoj-prov-instances-inline-storage-' + subscription.id + '-' + now.format('YYYY-MM-DD') + '.csv');
				$('.prov-export-instances-split').attr('href', REST_PATH + 'service/prov/' + subscription.id + '/ligoj-prov-instances-split-storage-' + subscription.id + '-' + now.format('YYYY-MM-DD') + '.csv');
			});
		},

		unload: function () {
			// Clean the shared menu
			_('service-prov-menu').empty().remove();
		},

		/**
		 * Reload the model
		 */
		reload: function () {
			// Clear the table
			var $instances = _('prov-instances').DataTable();
			var $storages = _('prov-storages').DataTable();
			$instances.clear().draw();
			$storages.clear().draw();
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'subscription/' + current.model.subscription + '/configuration',
				type: 'GET',
				success: function (data) {
					current.model = data;
					current.optimizeModel();
					var configuration = data.configuration;
					$instances.rows.add(configuration.instances).draw();
					$storages.rows.add(configuration.storages).draw();
					_('quote-location').select2('data', configuration.location);
					$('.location-wrapper').html(current.locationMap(configuration.location));
					_('quote-usage').select2('data', configuration.usage);
					_('quote-support').select2('data', configuration.supports);
					_('quote-license').select2('data', configuration.license ? {
						id: configuration.license,
						text: current.formatLicense(configuration.license)
					} : null);
				}
			});
		},

		/**
		 * Request to refresh the cost and trigger a global update as needed
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
			var dirty = true;
			if (newCost.min !== current.model.configuration.cost.min || newCost.max !== current.model.configuration.cost.max) {
				// The cost has been updated
				current.model.configuration.cost = newCost;
				notifyManager.notify(current.$messages['service:prov:refresh-needed']);
				dirty = false;
				current.reload();
			} else {
				// The cost still the same
				notifyManager.notify(current.$messages['service:prov:refresh-no-change']);
			}
			if (dirty && forceUpdateUi) {
				current.updateUiCost();
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
				var price = current.formatCost(subscription.data.quote.cost, null, null, true);
				return '<span data-toggle="tooltip" title="' + current.$messages['service:prov:cost-title'] + ' : ' + price + '" class="price label label-default">' + price + '</span>';
			}
		},

		/**
		 * Render provisioning details : cpu, ram, nbVm, storages.
		 */
		renderDetailsKey: function (subscription) {
			var quote = subscription.data.quote;
			var resources = [];
			if (quote.nb) {
				resources.push('<span class="sub-item">' + current.$super('icon')('server', 'service:prov:nb-instances') + quote.nb(quote.nbInstances > quote.nb ? '+' : '') + 'VM</span>');
				resources.push('<span class="sub-item">' + current.$super('icon')('bolt', 'service:prov:total-cpu') + quote.totalCpu + ' ' + current.$messages['service:prov:cpu'] + '</span>');
				resources.push('<span class="sub-item">' + current.$super('icon')('microchip', 'service:prov:total-ram') + current.formatRam(quote.totalRam) + '</span>');
			}
			if (quote.nbPublicAccess) {
				resources.push('<span class="sub-item">' + current.$super('icon')('globe', 'service:prov:nb-public-access') + quote.nbPublicAccess + '</span>');
			}
			if (quote.totalStorage) {
				resources.push('<span class="sub-item">' + current.$super('icon')('fas fa-hdd', 'service:prov:total-storage') + current.formatStorage(quote.totalStorage) + '</span>');
			}

			return current.$super('generateCarousel')(subscription, [
				['name', quote.name],
				['service:prov:resources', resources.join(', ')],
				['service:prov:location', current.$super('icon')('map-marker-alt', 'service:prov:location') + current.locationToHtml(quote.location, true)]
			], 1);
		},

		/**
		 * Format instance type details.
		 */
		formatInstanceType: function (name, mode, qi) {
			var type = qi ? qi.price.type : {};
			name = type ? type.name : name;
			if (mode !== 'display' || (typeof type.id === 'undefined')) {
				// Use only the name
				return name;
			}
			// Instance type details are available
			var details = type.description ? type.description.replace(/"/g, '') + '<br>' : '';
			details += '<i class=\'fas fa-bolt fa-fw\'></i> ';
			details += type.cpuRate ? '<i class=\'' + current.rates[type.cpuRate] + '\'></i> ' : '';
			if (type.cpu) {
				details += '#' + type.cpu;
				details += ' ' + current.formatConstant(type.constant);
			} else {
				details += current.$messages['service:prov:instance-custom'];
			}

			if (type.ram) {
				details += '<br><i class=\'fas fa-microchip fa-fw\'></i> ';
				details += type.ramRate ? '<i class=\'' + current.rates[type.ramRate] + '\'></i> ' : '';
				details += current.formatRam(type.ram);
			}

			if (type.storageRate) {
				details += '<br><i class=\'far fa-hdd fa-fw\'></i> ';
				details += type.ramRate ? '<i class=\'' + current.rates[type.storageRate] + '\'></i>' : '';
				// TODO Add instance storage
			}

			if (type.networkRate) {
				details += '<br><i class=\'fas fa-globe fa-fw\'></i> ';
				details += type.ramRate ? '<i class=\'' + current.rates[type.networkRate] + '\'></i>' : '';
				// TODO Add memory type
			}
			return '<u class="instance" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
		},

		formatStorageType: function (name, mode, qs) {
			var type = qs ? qs.price.type : {};
			name = type ? type.name : name;
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
				details += '<br><i class=\'fas fa-fw fa-stopwatch\'></i> <i class=\'' + current.rates[type.latency] + '\'></i>';
			}
			if (type.iops) {
				details += '<br><i class=\'' + current.storageOptimized.iops + '\'></i> ' + type.iops + ' IOPS';
			}
			if (type.throughput) {
				details += '<br><i class=\'' + current.storageOptimized.throughput + '\'></i> ' + type.throughput + ' MB/s';
			}
			if (type.durability9) {
				var nines = '9'.repeat(type.durability9);
				nines = type.durability9 < 3 ? '0'.repeat(3 - type.durability9) : nines;
				details += '<br><i class=\'far fa-fw fa-gem\'></i> ' + nines.substring(0, 2) + '.' + nines.substring(2) + '%';
			}
			if (type.availability) {
				details += '<br><i class=\'fas fa-fw fa-thumbs-up\'></i> ' + type.availability + '%';
			}
			return '<u class="instance" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
		},

		/**
		 * Format instance term detail
		 */
		formatInstanceTerm: function (name, mode, qi) {
			var term = qi ? qi.price.term : null;
			name = term ? term.name : name;
			if (mode === 'sort' || (term && typeof term.id === 'undefined')) {
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

			return '<u class="instance" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
		},


		/**
		 * Format instance quantity
		 */
		formatQuantity: function (quantity, mode, instance) {
			instance = typeof instance === 'undefined' ? quantity : (instance.quoteInstance || instance);
			if (mode === 'sort' || mode === 'filter' || typeof instance === 'undefined') {
				return quantity;
			}

			var min = instance.minQuantity || 0;
			var max = instance.maxQuantity;
			if (typeof max !== 'number') {
				return min + '+';
			}
			if (max === min) {
				return min;
			}

			// A range
			return min + '-' + max;
		},

		/**
		 * Format the constant CPU.
		 */
		formatConstant: function (constant) {
			return current.$messages[constant === true ? 'service:prov:cpu-constant' : 'service:prov:cpu-variable'];
		},

		/**
		 * Format the cost.
		 * @param {number} cost The cost value. May contains "min" and "max" attributes.
		 * @param {String|jQuery} mode Either 'sort' for a raw value, either a JQuery container for advanced format with "odometer". Otherwise will be simple format.
		 * @param {object} obj The optional cost object taking precedence over the cost parameter. May contains "min" and "max" attributes.
		 * @param {boolean} noRichText When true, the cost will not use HTML code.
		 * @return The formated cost.
		 */
		formatCost: function (cost, mode, obj, noRichText) {
			if (mode === 'sort' || mode === 'filter') {
				return cost;
			}

			var formatter = current.formatCostText;
			var $cost = $();
			if (mode instanceof jQuery) {
				// Odomoter format
				formatter = current.formatCostOdometer;
				$cost = mode;
			}

			// Computation part
			obj = (typeof obj === 'undefined' || obj === null) ? cost : obj;
			if (typeof obj.cost === 'undefined' && typeof obj.min !== 'number') {
				// Standard cost
				$cost.find('.cost-min').addClass('hidden');
				return formatter(cost, true, $cost, noRichText, cost && cost.unbound);
			}
			// A floating cost
			var min = obj.cost || obj.min || 0;
			var max = typeof obj.maxCost === 'number' ? obj.maxCost : obj.max;
			var unbound = (min !== max) || obj.unbound || (cost && cost.unbound) || (obj.minQuantity != obj.maxQuantity);
			if ((typeof max !== 'number') || max === min) {
				// Max cost is equal to min cost, no range
				$cost.find('.cost-min').addClass('hidden');
				return formatter(obj.cost || obj.min || 0, true, $cost, noRichText, unbound);
			}

			// Max cost, is different, display a range
			return formatter(min, false, $cost, noRichText) + '-' + formatter(max, true, $cost, noRichText, unbound);
		},

		/**
		 * Configure Odometer components
		 */
		initOdometer: function () {
			var $cost = $('.cost');
			var weightUnit = '<span class="cost-weight"></span><span class="cost-unit"></span>';
			$cost.append('<span class="cost-min hidden"><span class="cost-value"></span>' + weightUnit + '<span class="cost-separator">-</span></span>');
			$cost.append('<span class="cost-max"><span class="cost-value"></span>' + weightUnit + '</span>');
			require(['../main/service/prov/lib/odometer'], function (Odometer) {
				// Odometer component
				current.registerOdometer(Odometer, $cost.find('.cost-min .cost-value'));
				current.registerOdometer(Odometer, $cost.find('.cost-max .cost-value'));
				current.registerOdometer(Odometer, $('.nav-pills [href="#tab-instance"] > .badge .odo-wrapper'));
				current.registerOdometer(Odometer, $('.nav-pills [href="#tab-storage"] > .badge'));
				var $summary = $('.nav-pills [href="#tab-instance"] .summary> .badge');
				current.registerOdometer(Odometer, $summary.filter('.cpu').find('span'));
				current.registerOdometer(Odometer, $summary.filter('.internet').find('span'));
				current.updateUiCost();
			});
		},
		registerOdometer: function (Odometer, $container) {
			new Odometer({
				el: $container[0],
				theme: 'minimal',
				duration: 0
			}).render();
		},

		formatCostOdometer: function (cost, isMax, $cost, noRichTest, unbound) {
			if (isMax) {
				formatManager.formatCost(cost, 3, '$', 'cost-unit', function (value, weight, unit) {
					var $wrapper = $cost.find('.cost-max');
					$wrapper.find('.cost-value').html(value);
					$wrapper.find('.cost-weight').html(weight + ((cost.unbound || unbound) ? '+' : ''));
					$wrapper.find('.cost-unit').html(unit);
				});
			} else {
				formatManager.formatCost(cost, 3, '$', 'cost-unit', function (value, weight, unit) {
					var $wrapper = $cost.find('.cost-min').removeClass('hidden');
					$wrapper.find('.cost-value').html(value);
					$wrapper.find('.cost-weight').html(weight);
					$wrapper.find('.cost-unit').html(unit);
				});
			}
		},

		formatCostText: function (cost, isMax, _i, noRichText, unbound) {
			return formatManager.formatCost(cost, 3, '$', noRichText === true ? '' : 'cost-unit') + (unbound ? '+' : '');
		},

		/**
		 * Format the memory size.
		 */
		formatRam: function (sizeMB, mode, instance) {
			if (mode === 'sort' || mode === 'filter') {
				return sizeMB;
			}
			if (mode === 'display' && instance) {
				return current.formatEfficiency(sizeMB, instance.price.type.ram, function (value) {
					return formatManager.formatSize(value * 1024 * 1024, 3);
				});
			}
			return formatManager.formatSize(sizeMB * 1024 * 1024, 3);
		},

		/**
		 * Format the memory size.
		 */
		formatCpu: function (value, mode, instance) {
			if (mode === 'display' && instance) {
				return current.formatEfficiency(value, instance.price.type.cpu);
			}
			return value;
		},

		/**
		 * Format the efficiency of a data depending on the rate against the maximum value.
		 * @param value {number} Current value.
		 * @param max {number} Maximal value.
		 * @param formatter {function} Option formatter function of the value. 
		 * @returns {string} The value to display containing the rate.
		 */
		formatEfficiency: function (value, max, formatter) {
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
		},

		/**
		 * Format the storage size.
		 */
		formatStorage: function (sizeGB, mode, data) {
			if (mode === 'sort' || mode === 'filter') {
				return sizeGB;
			}
			if (data && data.price.type.minimal > sizeGB) {
				// Enable efficiency display
				return current.formatEfficiency(sizeGB, data.price.type.maximal, function (value) {
					return formatManager.formatSize(value * 1024 * 1024 * 1024, 3);
				});
			}

			// No efficiency rendering can be done
			return formatManager.formatSize(sizeGB * 1024 * 1024 * 1024, 3);
		},

		/**
		 * Format the storage size to html markup.
		 * @param {object} qs Quote storage with price, type and size.
		 * @param {boolean} showName When true, the type name is displayed. Default is false.
		 * @return {string} The HTML markup representing the quote storage : type and flags.
		 */
		formatStorageHtml: function (qs, showName) {
			var type = qs.price.type;
			return (showName === true ? type.name + ' ' : '') + current.formatStorageLatency(type.latency) +
				(type.optimized ? ' ' + current.formatStorageOptimized(type.optimized) : '') +
				' ' + formatManager.formatSize(qs.size * 1024 * 1024 * 1024, 3) +
				((qs.size < type.minimal) ? ' (' + formatManager.formatSize(type.minimal * 1024 * 1024 * 1024, 3) + ')' : '');
		},

		/**
		 * Format the storage price to html markup.
		 * @param {object} qs Quote storage with price, type and size.
		 * @param {boolean} showName When true, the type name is displayed. Default is true.
		 * @return {string} The HTML markup representing the quote storage : cost, type and flags.
		 */
		formatStoragePriceHtml: function (qs, showName) {
			return current.formatStorageHtml(qs, showName === false ? showName : true) + ' ' + qs.price.type.name + '<span class="pull-right text-small">' + current.formatCost(qs.cost) + '<span class="cost-unit">/m</span></span>';
		},

		/**
		 * Format an attached storages
		 */
		formatQiStorages: function (instance, mode) {
			// Compute the sum
			var storages = instance.storages;
			var sum = 0;
			if (storages) {
				storages.forEach(function (storage) {
					sum += storage.size;
				});
			}
			if (mode === 'display') {
				// Need to build a Select2 tags markup
				return '<input type="text" class="storages-tags" data-instance="' + instance.id + '">';
			}
			return sum;
		},

		/**
		 * OS key to markup/label mapping.
		 */
		os: {
			'linux': ['Linux', 'fab fa-linux fa-fw'],
			'windows': ['Windows', 'fab fa-windows fa-fw'],
			'suse': ['SUSE', 'icon-suse fa-fw'],
			'rhel': ['Red Hat Enterprise', 'icon-redhat fa-fw'],
			'centos': ['CentOS', 'icon-centos fa-fw'],
			'debian': ['Debian', 'icon-debian fa-fw'],
			'fedora': ['Fedora', 'icon-fedora fa-fw'],
			'ubuntu': ['Ubuntu', 'icon-ubuntu fa-fw']
		},

		/**
		 * Internet Access key to markup/label mapping.
		 */
		internet: {
			'public': ['Public', 'fas fa-globe fa-fw'],
			'private': ['Private', 'fas fa-lock fa-fw'],
			'private_nat': ['NAT', 'fas fa-low-vision fa-fw']
		},

		/**
		 * Rate name (identifier) to class mapping. Classes are ditributed across 5 values.
		 */
		rates: {
			'worst': 'far fa-star text-danger fa-fw',
			'low': 'fas fa-star-half text-danger fa-fw',
			'medium': 'fas fa-star-half fa-fw',
			'good': 'fas fa-star text-primary fa-fw',
			'best': 'fas fa-star text-success fa-fw',
			'invalid': 'fas fa-ban fa-fw'
		},

		/**
		 * Rate name (identifier) to class mapping. Classes are ditributed across 3 values.
	    */
		rates3: {
			'low': 'far fa-star-half fa-fw',
			'medium': 'far fa-star text-success fa-fw',
			'good': 'fas fa-star text-success fa-fw',
			'invalid': 'fas fa-ban fa-fw'
		},

		/**
		 * Storage optimized key to markup/label mapping.
		 */
		storageOptimized: {
			'throughput': 'fas fa-angle-double-right fa-fw',
			'durability': 'fas fa-archive fa-fw',
			'iops': 'fas fa-bolt fa-fw'
		},

		/**
		 * Support access type key to markup/label mapping.
		 */
		supportAccessType: {
			'technical': 'fas fa-wrench fa-fw',
			'billing': 'fas fa-dollar-sign fa-fw',
			'all': 'fas fa-users fa-fw'
		},

		/**
		 * Return the HTML markup from the OS key name.
		 */
		formatOs: function (os, mode, clazz) {
			var cfg = current.os[(os.id || os || 'linux').toLowerCase()] || current.os.linux;
			if (mode === 'sort' || mode === 'filter') {
				return cfg[0];
			}
			clazz = cfg[1] + (typeof clazz === 'string' ? clazz : '');
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
		},

		/**
		 * Return the HTML markup from the Internet privacy key name.
		 */
		formatInternet: function (internet, mode, clazz) {
			var cfg = (internet && current.internet[(internet.id || internet).toLowerCase()]) || current.internet.public || 'public';
			if (mode === 'sort') {
				return cfg[0];
			}
			clazz = cfg[1] + (typeof clazz === 'string' ? clazz : '');
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
		},

		formatUsageTemplate: function (usage) {
			return usage.text
		},

		/**
		 * Return the HTML markup from the quote instance model.
		 */
		formatQuoteInstance: function (quoteInstance) {
			return quoteInstance.name;
		},

		/**
		 * Return the HTML markup from the support level.
		 */
		formatSupportLevel: function (level, mode, clazz) {
			var id = level ? (level.id || level).toLowerCase() : '';
			if (id) {
				var text = current.$messages['service:prov:support-level-' + id];
				clazz = current.rates3[id] + (typeof clazz === 'string' ? clazz : '');
				if (mode === 'sort' || mode === 'filter') {
					return text;
				}

				return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode ? ' ' + text : '');
			}
			return '';
		},

		/**
		 * Return the HTML markup from the support seats.
		 */
		formatSupportSeats: function (seats, mode) {
			if (mode === 'sort' || mode === 'filter') {
				return seats || 0;
			}
			return seats ? seats : '∞';
		},

		/**
		 * Return the HTML markup from the storage latency.
		 */
		formatStorageLatency: function (latency, mode, clazz) {
			var id = latency ? (latency.id || latency).toLowerCase() : 'invalid';
			var text = id && current.$messages['service:prov:storage-latency-' + id];
			if (mode === 'sort' || mode === 'filter') {
				return text;
			}

			clazz = current.rates[id] + (typeof clazz === 'string' ? clazz : '');
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode ? ' ' + text : '');
		},

		/**
		 * Return the HTML markup from the storage optimized.
		 */
		formatStorageOptimized: function (optimized, withText, clazz) {
			if (optimized) {
				var id = (optimized.id || optimized).toLowerCase();
				var text = current.$messages['service:prov:storage-optimized-' + id];
				clazz = current.storageOptimized[id] + (typeof clazz === 'string' ? clazz : '');
				return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (withText ? ' ' + text : '');
			}
		},

		/**
		 * Return the HTML markup from the support access type.
		 */
		formatSupportAccess: function (type, mode) {
			if (type) {
				var id = (type.id || type).toLowerCase();
				var text = current.$messages['service:prov:support-access-' + id];
				if (mode === 'sort' || mode === 'filter') {
					return text;
				}
				var clazz = current.supportAccessType[id];
				return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode === 'display' ? '' : (' ' + text));
			}
		},

		/**
		 * Associate the storages to the instances
		 */
		optimizeModel: function () {
			var conf = current.model.configuration;
			var i;

			// Instances
			conf.instancesById = {};
			conf.instanceCost = 0;
			var instances = conf.instances;
			for (i = 0; i < instances.length; i++) {
				var qi = instances[i];
				// Optimize id access
				conf.instancesById[qi.id] = qi;
				conf.instanceCost += qi.cost;
			}

			// Storage
			conf.storagesById = {};
			conf.storageCost = 0;
			var storages = conf.storages;
			for (i = 0; i < storages.length; i++) {
				var qs = storages[i];
				conf.storageCost += qs.cost;
				current.attachStorage(qs, qs.quoteInstance, true);

				// Optimize id access
				conf.storagesById[qs.id] = qs;
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
			current.updateUiCost();
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
		 * Return the query parameter name to use to filter some other inputs.
		 */
		toQueryName: function (type, $item) {
			var id = $item.attr('id');
			return id.indexOf(type + '-') === 0 && id.substring((type + '-').length);
		},

		/**
		 * Return the memory query parameter value to use to filter some other inputs.
		 */
		toQueryValueRam: function (value) {
			return (current.cleanInt(value) || 0) * parseInt(_('instance-ram-unit').find('li.active').data('value'), 10);
		},

		/**
		 * Return the constant CPU query parameter value to use to filter some other inputs.
		 */
		toQueryValueConstant: function (value) {
			return value ? value === 'constant' : null;
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
			var $form = $(this).closest('[data-prov-type]');
			var queries = [];
			var type = $form.data('prov-type');
			var $popup = _('popup-prov-' + type);

			// Build the query
			$form.find('.resource-query').each(function () {
				var $item = $(this);
				var value = '';
				if ($item.is('.input-group-btn')) {
					value = $item.find('li.active').data('value');
				} else if ($item.prev().is('.select2-container')) {
					var data = ($item.select2('data') || {});
					value = data.name || (data.data ? data.data.name : (data.id || $item.val()));
				} else if ($item.is('[type="number"]')) {
					value = parseInt(current.cleanData($item.val()) || "0", 10);
				} else if (!$item.is('.select2-container')) {
					value = current.cleanData($item.val());
				}
				var queryParam = value && current.toQueryName(type, $item);
				value = value && $item.is('[type="checkbox"]') ? $item.is(':checked') : value;
				value = queryParam && current['toQueryValue' + queryParam.capitalize()] ? current['toQueryValue' + queryParam.capitalize()](value, $item) : value;
				if (queryParam && value) {
					// Add as query
					queries.push(queryParam + '=' + value);
				}
			});
			// Check the availability of this instance for these requirements
			current.disableCreate($popup);
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/' + type + '-lookup/?' + queries.join('&'),
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
		 * Set the current storage price.
		 * @param {object|Array} Quote or prices
		 */
		storageSetUiPrice: function (quote) {
			if (quote && (($.isArray(quote) && quote.length) || quote.price)) {
				var suggests = quote;
				if (!$.isArray(quote)) {
					// Single price
					suggests = [quote];
				}
				for (var i = 0; i < suggests.length; i++) {
					suggests[i].id = suggests[i].id || suggests[i].price.id;
				}
				var suggest = suggests[0];
				_('storage-price').select2('destroy').select2({
					data: suggests,
					formatSelection: current.formatStoragePriceHtml,
					formatResult: current.formatStoragePriceHtml
				}).select2('data', suggest);
			} else {
				_('storage-price').select2('data', null);
			}
			current.updateInstanceCompatible(_('storage-price').select2('data'));
		},

		/**
		 * Depending on the current storage type, enable/disable the instance selection
		 */
		updateInstanceCompatible: function (suggest) {
			if (suggest && suggest.price && suggest.price.type.instanceCompatible === false) {
				// Disable
				_('storage-instance').select2('data', null).select2('disable');
				_('storage-instance').prev('.select2-container').find('.select2-chosen').text(current.$messages['service:prov:cannot-attach-instance']);
			} else {
				// Enable
				_('storage-instance').select2('enable');
				if (_('storage-instance').select2('data') === null) {
					_('storage-instance').prev('.select2-container').find('.select2-chosen').text(current.$messages['service:prov:no-attached-instance']);
				}
			}
		},

		/**
		 * Set the current instance price.
		 */
		instanceSetUiPrice: function (quote) {
			if (quote && quote.price) {
				var suggests = [quote];
				_('instance-price').select2('destroy').select2({
					data: suggests,
					formatSelection: function (qi) {
						return qi.price.type.name + ' (' + current.formatCost(qi.cost, null, null, true) + '/m)';
					},
					formatResult: function (qi) {
						return qi.price.type.name + ' (' + current.formatCost(qi.cost, null, null, true) + '/m)';
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
			if (quote && (($.isArray(quote) && quote.length) || quote.price)) {
				var suggests = quote;
				if (!$.isArray(quote)) {
					// Single price
					suggests = [quote];
				}
				for (var i = 0; i < suggests.length; i++) {
					suggests[i].id = suggests[i].id || suggests[i].price.id;
				}
				var suggest = suggests[0];
				_('support-price').select2('destroy').select2({
					data: suggests,
					formatSelection: function (qi) {
						return qi.price.type.name + ' (' + current.formatCost(qi.cost, null, null, true) + '/m)';
					},
					formatResult: function (qi) {
						return qi.price.type.name + ' (' + current.formatCost(qi.cost, null, null, true) + '/m)';
					}
				}).select2('data', suggest);
			} else {
				_('support-price').select2('data', null);
			}
		},

		/**
		 * Initialize data tables and popup event : delete and details
		 */
		initializeDataTableEvents: function (type) {
			// Delete the selected instance from the quote
			var $table = _('prov-' + type + 's');
			var dataTable = current[type + 'NewTable']();
			current[type + 'Table'] = dataTable;
			// Delete a single row/item
			$table.on('click', '.delete', function () {
				var resource = dataTable.fnGetData($(this).closest('tr')[0]);
				$.ajax({
					type: 'DELETE',
					url: REST_PATH + 'service/prov/' + type + '/' + resource.id,
					success: function (updatedCost) {
						current.defaultCallback(type, updatedCost);
					}
				});
			});
			// Delete all items
			$table.on('click', '.delete-all', function () {
				$.ajax({
					type: 'DELETE',
					url: REST_PATH + 'service/prov/' + current.model.subscription + '/' + type,
					success: function (updatedCost) {
						current.defaultCallback(type, updatedCost);
					}
				});
			});
			// Resource edition pop-up
			var $popup = _('popup-prov-' + type);
			$popup.on('shown.bs.modal', function () {
				_(type + '-name').trigger('focus');
			}).on('submit', function (e) {
				e.preventDefault();
				current.save(type);
			}).on('show.bs.modal', function (event) {
				var $source = $(event.relatedTarget);
				var $tr = $source.closest('tr');
				var quote = ($tr.length && dataTable.fnGetData($tr[0])) || {};
				$(this).find('input[type="submit"]').removeClass('btn-primary btn-success').addClass(quote.id ? 'btn-primary' : 'btn-success');
				if (quote.id) {
					current.enableCreate($popup);
				} else {
					current.disableCreate($popup);
				}
				current.model.quote = quote;
				current.toUi(type, quote);
			});
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
			$('.subscribe-configuration-prov-search').on('keyup', function (event) {
				if (event.which !== 16 && event.which !== 91) {
					var table = current[$(this).closest('[data-prov-type]').data('prov-type') + 'Table'];
					if (table) {
						table.fnFilter($(this).val());
						current.updateUiCost();
					}
				}
			});
			$('input.resource-query').not('[type="number"]').on('change', current.checkResource);
			$('input.resource-query[type="number"]').on('keyup', function (event) {
				if (event.which !== 16 && event.which !== 91) {
					$.proxy(current.checkResource, $(this))();
				}
			});
			$(['instance', 'storage', 'support']).each(function (_i, type) {
				current.initializeDataTableEvents(type);
			});
			$('.quote-name').text(current.model.configuration.name);

			_('popup-prov-update').on('shown.bs.modal', function () {
				_('quote-name').trigger('focus');
			}).on('submit', function (e) {
				e.preventDefault();
				current.updateQuote({
					name: _('quote-name').val(),
					description: _('quote-description').val()
				});
			}).on('show.bs.modal', function () {
				_('quote-name').val(current.model.configuration.name);
				_('quote-description').val(current.model.configuration.description || '');
			});

			$('.cost-refresh').on('click', current.refreshCost);
			$('#instance-min-quantity, #instance-max-quantity').on('change', current.updateAutoScale);

			// Related instance of the storage
			_('storage-instance').select2({
				formatSelection: current.formatQuoteInstance,
				formatResult: current.formatQuoteInstance,
				placeholder: current.$messages['service:prov:no-attached-instance'],
				allowClear: true,
				escapeMarkup: function (m) {
					return m;
				},
				data: function () {
					return {
						results: current.model.configuration.instances
					};
				}
			});

			$('.support-access').select2({
				formatSelection: current.formatSupportAccess,
				formatResult: current.formatSupportAccess,
				allowClear: true,
				placeholder: 'None',
				escapeMarkup: function (m) {
					return m;
				},
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
				formatSelection: current.formatSupportLevel,
				formatResult: current.formatSupportLevel,
				allowClear: true,
				placeholder: 'None',
				escapeMarkup: function (m) {
					return m;
				},
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
				formatSelection: current.formatOs,
				formatResult: current.formatOs,
				escapeMarkup: function (m) {
					return m;
				},
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

			_('instance-software').select2(current.genericSelect2(current.$messages['service:prov:software-none'], current.termToText, function () {
				return 'instance-software/' + _('instance-os').val();
			}));

			_('instance-internet').select2({
				formatSelection: current.formatInternet,
				formatResult: current.formatInternet,
				escapeMarkup: function (m) {
					return m;
				},
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
				formatSelection: current.formatStorageOptimized,
				formatResult: current.formatStorageOptimized,
				escapeMarkup: function (m) {
					return m;
				},
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
				formatSelection: current.formatStorageLatency,
				formatResult: current.formatStorageLatency,
				escapeMarkup: function (m) {
					return m;
				},
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

			// Memory unit, CPU constant/variable selection
			_('popup-prov-instance').on('click', '.input-group-btn li', function () {
				var $select = $(this).closest('.input-group-btn');
				$select.find('li.active').removeClass('active');
				var $active = $(this).addClass('active').find('a');
				$select.find('.btn span:first-child').html($active.find('i').length ? $active.find('i').prop('outerHTML') : $active.html());
				// Also trigger the change of the value
				_('instance-cpu').trigger('keyup');
			});
			_('instance-term').select2(current.instanceTermSelect2(false));
			_('storage-price').on('change', function (event) {
				current.updateInstanceCompatible(event.added);
			});
			current.initializeTerraform();
			current.initializeLocation();
			current.initializeUsage();
			current.initializeLicense();
			current.initializeRamAdjustedRate();
		},

		/**
		 * Configure RAM adjust rate.
		 */
		initializeRamAdjustedRate: function () {
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
					slide: function (event, ui) {
						handle.text(ui.value + '%');
					},
					change: function (event, slider) {
						current.updateQuote({
							ramAdjustedRate: slider.value
						}, 'ramAdjustedRate', true);
					}
				});
			});
		},

		/**
		 * Configure Terraform.
		 */
		initializeTerraform: function () {
			_('prov-terraform-download').attr('href', REST_PATH + 'service/prov/' + current.model.subscription + '/terraform-' + current.model.subscription + '.zip');
			_('prov-terraform-status').find('.terraform-logs a').attr('href', REST_PATH + 'service/prov/' + current.model.subscription + '/terraform.log');
			_('popup-prov-terraform').on('shown.bs.modal', function () {
				_('terraform-cidr').trigger('focus');
			}).on('show.bs.modal', function () {
				if (_('terraform-key-name').val() === "") {
					_('terraform-key-name').val(current.$parent.model.pkey);
				}

				// Target platform
				current.updateTerraformTarget()
			}).on('submit', current.terraform);
			_('popup-prov-terraform-destroy').on('shown.bs.modal', function () {
				_('terraform-confirm-destroy').trigger('focus');
			}).on('show.bs.modal', function () {
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

		/**
		 * Configure location.
		 */
		initializeLocation: function () {
			_('quote-location').select2(current.locationSelect2(false)).select2('data', current.model.configuration.location).on('change', function (event) {
				if (event.added) {
					current.updateQuote({
						location: event.added
					}, 'location');
					$('.location-wrapper').html(current.locationMap(event.added));
				}
			});
			$('.location-wrapper').html(current.locationMap(current.model.configuration.location));
			_('instance-location').select2(current.locationSelect2(current.$messages['service:prov:default']));
			_('storage-location').select2(current.locationSelect2(current.$messages['service:prov:default']));
		},

		/**
		 * Configure usage.
		 */
		initializeUsage: function () {
			_('popup-prov-usage').on('shown.bs.modal', function () {
				_('usage-name').trigger('focus');
			}).on('submit', function (e) {
				e.preventDefault();
				current.saveOrUpdateUsage({
					name: _('usage-name').val(),
					rate: parseInt(_('usage-rate').val() || '100', 10),
					duration: parseInt(_('usage-duration').val() || '1', 10)
				}, _('usage-old-name').val());
			}).on('show.bs.modal', function (event) {
				if ($(event.relatedTarget).is('.btn-success')) {
					// Create mode
					_('usage-old-name').val('');
					_('usage-name').val('');
					_('usage-rate').val(100);
					_('usage-duration').val(1);
				} else {
					// Update mode
					var usage = event.relatedTarget;
					_('usage-old-name').val(usage.name);
					_('usage-name').val(usage.name);
					_('usage-rate').val(usage.rate);
					_('usage-duration').val(usage.duration);
				}
				validationManager.reset($(this));
				validationManager.mapping.name = 'usage-name';
				validationManager.mapping.rate = 'usage-rate';
				validationManager.mapping.duration = 'usage-duration';
				_('usage-rate').trigger('change');
			});
			$('.usage-inputs input').on('change', current.synchronizeUsage);
			$('.usage-inputs input').on('keyup', current.synchronizeUsage);
			_('prov-usage-delete').click(function () {
				current.deleteUsage(_('usage-old-name').val());
			});

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
				placeholder: 'Template',
				allowClear: true,
				formatSelection: current.formatUsageTemplate,
				formatResult: current.formatUsageTemplate,
				escapeMarkup: function (m) {
					return m;
				},
				data: usageTemplates
			});
			_('instance-usage-upload').select2(current.usageSelect2(current.$messages['service:prov:default']));
			_('quote-usage').select2(current.usageSelect2(current.$messages['service:prov:usage-100']))
				.select2('data', current.model.configuration.usage)
				.on('change', function (event) {
					current.updateQuote({
						usage: event.added || null
					}, 'usage', true);
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
			_('instance-usage').select2(current.usageSelect2(current.$messages['service:prov:default']));
		},

		/**
		 * Configure license.
		 */
		initializeLicense: function () {
			_('instance-license').select2(current.genericSelect2(current.$messages['service:prov:default'], current.formatLicense, function () {
				return 'instance-license/' + _('instance-os').val();
			}));
			_('quote-license').select2(current.genericSelect2(current.$messages['service:prov:license-included'], current.formatLicense, function () {
				return 'instance-license/WINDOWS';
			})).select2('data', current.model.configuration.license ? {
				id: current.model.configuration.license,
				text: current.formatLicense(current.model.configuration.license)
			} : null)
				.on('change', function (event) {
					current.updateQuote({
						license: event.added || null
					}, 'license', true);
				});
		},

		formatLicense: function (license) {
			return license.text || current.$messages['service:prov:license-' + license.toLowerCase()] || license;
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
			return current.genericSelect2(placeholder, current.locationToHtml, 'location', null, current.orderByName, 100);
		},

		/**
		 * Order the array items by their 'text'
		 */
		orderByName: function (data) {
			return data.sort(function (a, b) {
				a = a.text.toLowerCase();
				b = b.text.toLowerCase();
				if (a > b) {
					return 1;
				} else if (a < b) {
					return -1;
				}
				return 0;
			});
		},

		/**
		 * Usage Select2 configuration.
		 */
		usageSelect2: function (placeholder) {
			return current.genericSelect2(placeholder, current.usageToText, 'usage', function (usage) {
				return usage.name + '<span class="select2-usage-summary pull-right"><span class="x-small">(' + usage.rate + '%) </span>' +
					'<a class="update prov-usage-select2-action"><i data-toggle="tooltip" title="' + current.$messages.update + '" class="fas fa-fw fa-pencil-alt"></i><a></span>';
			});
		},

		/**
		 * Price term Select2 configuration.
		 */
		instanceTermSelect2: function () {
			return current.genericSelect2(current.$messages['service:prov:default'], current.termToText, 'instance-price-term');
		},

		/**
		 * Generic Ajax Select2 configuration.
		 * @param path {string|function} Either a string, either a function returning a relative path suffix to 'service/prov/$subscription/$path'
		 */
		genericSelect2: function (placeholder, renderer, path, rendererResult, orderCallback, pageSize) {
			pageSize = pageSize || 15;
			return {
				formatSelection: renderer,
				formatResult: rendererResult || renderer,
				escapeMarkup: function (m) {
					return m;
				},
				allowClear: placeholder !== false,
				placeholder: placeholder ? placeholder : null,
				formatSearching: function () {
					return current.$messages.loading;
				},
				ajax: {
					url: function () {
						return REST_PATH + 'service/prov/' + current.model.subscription + '/' + (typeof path === 'function' ? path() : path);
					},
					dataType: 'json',
					data: function (term, page) {
						return {
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
							'sord': 'asc'
						};
					},
					results: function (data, page) {
						var result = [];
						$((typeof data.data === 'undefined') ? data : data.data).each(function (index, item) {
							if (typeof item === 'string') {
								item = {
									id: item,
									text: renderer(item)
								};
							} else {
								item.text = renderer(item);
							}
							result.push(item);
						});
						if (orderCallback) {
							orderCallback(result);
						}
						return {
							more: data.recordsFiltered > page * pageSize,
							results: result
						};
					}
				}
			};
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
				error: function () {
					current.enableTerraform();
				}
			});
		},

		/**
		 * Execute the terraform deployment
		 */
		terraform: function () {
			current.terraformAction(_('popup-prov-terraform'), {
				'key_name': _('terraform-key-name').val(),
				'private_subnets': '"' + $.map(_('terraform-private-subnets').val().split(','), function (s) { return s.trim(); }).join('","') + '"',
				'public_subnets': '"' + $.map(_('terraform-public-subnets').val().split(','), function (s) { return s.trim(); }).join('","') + '"',
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
		 * @param {String} property The optional highlighted updated property name. Used for the feedback UI.
		 * @param {function } forceUpdateUi When true, the UI is always refreshed, even when the cost has not been updated.
		 */
		updateQuote: function (data, property, forceUpdateUi) {
			var conf = current.model.configuration;
			var $popup = _('popup-prov-update');
			current.disableCreate($popup);

			// Build the new data
			var jsonData = $.extend({
				name: conf.name,
				description: conf.description,
				location: conf.location,
				license: conf.license,
				ramAdjustedRate: conf.ramAdjustedRate || 100,
				usage: conf.usage
			}, data || {});
			jsonData.location = jsonData.location.name || jsonData.location;

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
				&& conf.ramAdjustedRate === jsonData.ramAdjustedRate) {
				// No change
				return;
			}

			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/' + current.model.subscription,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(jsonData),
				beforeSend: function () {
					$('.loader-wrapper').removeClass('hidden');
				},
				success: function (newCost) {
					// Update the UI
					$('.quote-name').text(jsonData.name);

					// Commit to the model
					conf.name = jsonData.name;
					conf.description = jsonData.description;
					conf.location = data.location || conf.location;
					conf.usage = data.usage || conf.usage;
					conf.license = jsonData.license;
					conf.ramAdjustedRate = jsonData.ramAdjustedRate;

					// UI feedback
					$popup.modal('hide');

					// Handle updated cost
					if (property) {
						current.reloadAsNeed(newCost, forceUpdateUi);
					} else if (forceUpdateUi) {
						current.updateUiCost();
					}
				},
				complete: function () {
					$('.loader-wrapper').addClass('hidden');
					current.enableCreate($popup);
				},
				error: function () {
					// Restore the old property value
					if (property) {
						notifyManager.notifyDanger(Handlebars.compile(current.$messages['service:prov:' + property + '-failed'])(data[property].name));
						_('quote-' + property).select2('data', current.model.configuration[property]);
					}
				}
			});
		},

		/**
		 * Delete an usage by its name.
		 * @param {string} name The usage name to delete.
		 */
		deleteUsage: function (name) {
			var conf = current.model.configuration;
			var $popup = _('popup-prov-usage');
			current.disableCreate($popup);
			$.ajax({
				type: 'DELETE',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/usage/' + encodeURIComponent(name),
				dataType: 'json',
				contentType: 'application/json',
				success: function (newCost) {
					// Commit to the model
					if (conf.usage && conf.usage.name === name) {
						// Update the usage of the quote
						delete conf.usage;
						_('prov-usage').select2('data', null);
					}

					// UI feedback
					notifyManager.notify(Handlebars.compile(current.$messages.deleted)(name));
					$popup.modal('hide');

					// Handle updated cost
					current.reloadAsNeed(newCost);
				},
				complete: function () {
					current.enableCreate($popup);
				}
			});

		},

		/**
		 * Update a quote usage.
		 * @param {string} name The usage data to persist.
		 * @param {string} oldName The optional old name. Required for 'update' mode.
		 * @param {function } forceUpdateUi When true, the UI is always refreshed, even when the cost has not been updated.
		 */
		saveOrUpdateUsage: function (data, oldName, forceUpdateUi) {
			var conf = current.model.configuration;
			var $popup = _('popup-prov-usage');
			current.disableCreate($popup);
			$.ajax({
				type: oldName ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/usage' + (oldName ? '/' + encodeURIComponent(oldName) : ''),
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (newCost) {
					// Commit to the model
					if (conf.usage && conf.usage.name === oldName) {
						// Update the usage of the quote
						conf.usage.name = data.name;
						conf.usage.rate = data.rate;
						data.id = 0;
						_('quote-usage').select2('data', data);
						forceUpdateUi = true;
					}

					// UI feedback
					notifyManager.notify(Handlebars.compile(current.$messages[data.id ? 'updated' : 'created'])(data.name));
					$popup.modal('hide');

					// Handle updated cost
					if (newCost.tota) {
						current.reloadAsNeed(newCost.total, forceUpdateUi);
					}
				},
				complete: function () {
					current.enableCreate($popup);
				}
			});
		},

		locationMap: function (location) {
			if (location.longitude) {
				// https://www.google.com/maps/place/33%C2%B048'00.0%22S+151%C2%B012'00.0%22E/@-33.8,151.1978113,3z
				// http://www.google.com/maps/place/49.46800006494457,17.11514008755796/@49.46800006494457,17.11514008755796,17z
				//				html += '<a href="https://maps.google.com/?q=' + location.latitude + ',' + location.longitude + '" target="_blank"><i class="fas fa-location-arrow"></i></a> ';
				return '<a href="http://www.google.com/maps/place/' + location.latitude + ',' + location.longitude + '/@' + location.latitude + ',' + location.longitude + ',3z" target="_blank"><i class="fas fa-location-arrow"></i></a> ';
			} else {
				return '';
			}
		},

		/**
		 * Location html renderer.
		 */
		locationToHtml: function (location, map) {
			var id = location.name;
			var subRegion = location.subRegion && (current.$messages[location.subRegion] || location.subRegion);
			var m49 = location.countryM49 && current.$messages.m49[parseInt(location.countryM49, 10)];
			var placement = subRegion || (location.placement && current.$messages[location.placement]) || location.placement;
			var html = map === true ? current.locationMap(location) : '';
			if (location.countryA2) {
				var a2 = (location.countryA2 === 'UK' ? 'GB' : location.countryA2).toLowerCase();
				html += '<img class="flag-icon prov-location-flag" src="' + current.$path + 'flag-icon-css/flags/4x3/' + a2 + '.svg" alt="" data-toggle="tooltip" title="' + m49 + '">';
			}
			html += m49 || id;
			html += (placement && placement !== html) ? ' <span class="small">(' + placement + ')</span>' : '';
			html += (subRegion || m49) ? '<span class="prov-location-api">' + id + '</span>' : id;
			return html;
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
		termToText: function (term) {
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
				_('prov-' + type + 's').DataTable().rows(function (index, data) {
					return data.id === id;
				}).invalidate().draw();
			}
		},

		storageCommitToModel: function (data, model) {
			model.size = parseInt(data.size, 10);
			model.latency = data.latency;
			model.optimized = data.optimized;
			// Update the attachment
			current.attachStorage(model, data.quoteInstance);
		},

		supportCommitToModel: function (data, model) {
			model.seats = parseInt(data.seats, 10);
			model.level = data.level;
			model.accessApi = data.accessApi;
			model.accessPhone = data.accessPhone;
			model.accessChat = data.accessChat;
			model.accessEmail = data.accessEmail;
		},

		instanceCommitToModel: function (data, model) {
			model.cpu = parseFloat(data.cpu, 10);
			model.ram = parseInt(data.ram, 10);
			model.maxVariableCost = parseFloat(data.maxVariableCost, 10);
			model.internet = data.internet;
			model.minQuantity = parseInt(data.minQuantity, 10);
			model.maxQuantity = data.maxQuantity ? parseInt(data.maxQuantity, 10) : null;
			model.constant = data.constant;
			model.os = data.os;
		},

		storageUiToData: function (data) {
			data.size = current.cleanInt(_('storage-size').val());
			data.quoteInstance = (_('storage-price').select2('data') || {}).name;
			data.optimized = _('storage-optimized').val();
			data.latency = _('storage-latency').val();
			data.type = _('storage-price').select2('data').price.type.name;
		},

		supportUiToData: function (data) {
			data.seats = current.cleanInt(_('support-seats').val());
			data.accessApi = (_('support-access-api').select2('data') || {}).id || null;
			data.accessEmail = (_('support-access-email').select2('data') || {}).id || null;
			data.accessPhone = (_('support-access-phone').select2('data') || {}).id || null;
			data.accessChat = (_('support-access-chat').select2('data') || {}).id || null;
			data.level = (_('support-level').select2('data') || {}).id || null;
			data.type = _('support-price').select2('data').price.type.name;
		},

		instanceUiToData: function (data) {
			data.cpu = current.cleanFloat(_('instance-cpu').val());
			data.ram = current.toQueryValueRam(_('instance-ram').val());
			data.maxVariableCost = current.cleanFloat(_('instance-max-variable-cost').val());
			data.ephemeral = _('instance-ephemeral').is(':checked');
			data.internet = _('instance-internet').val().toLowerCase();
			data.minQuantity = current.cleanInt(_('instance-min-quantity').val()) || 0;
			data.maxQuantity = current.cleanInt(_('instance-max-quantity').val()) || null;
			data.os = _('instance-os').val().toLowerCase();
			data.license = _('instance-license').val().toLowerCase() || null;
			data.software = _('instance-software').val().toLowerCase() || null;
			data.constant = current.toQueryValueConstant(_('instance-constant').find('li.active').data('value'));
			data.price = _('instance-price').select2('data').price.id;
		},

		cleanFloat: function (data) {
			data = current.cleanData(data);
			return data && parseFloat(data, 10);
		},

		cleanInt: function (data) {
			data = current.cleanData(data);
			return data && parseInt(data, 10);
		},

		cleanData: function (data) {
			return (typeof data === 'string') ? data.replace(',', '.').replace(' ', '') || null : data;
		},

		/**
		 * Fill the popup from the model
		 * @param {string} type, The entity type (instance/storage)
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		toUi: function (type, model) {
			validationManager.reset(_('popup-prov-' + type));
			_(type + '-name').val(model.name || current.findNewName(current.model.configuration[type + 's'], type));
			_(type + '-description').val(model.description || '');
			_(type + '-location').select2('data', model.location || null);
			_(type + '-usage').select2('data', model.usage || null);
			current[type + 'ToUi'](model);
			$.proxy(current.checkResource, _('popup-prov-' + type))();
		},

		/**
		 * Fill the instance popup with given entity or default values.
		 * @param {Object} quote, the entity corresponding to the quote.
		 */
		instanceToUi: function (quote) {
			_('instance-cpu').val(quote.cpu || 1);
			current.adaptRamUnit(quote.ram || 2048);
			_('instance-constant').find('li.active').removeClass('active');
			if (quote.constant === true) {
				_('instance-constant').find('li[data-value="constant"]').addClass('active');
			} else if (quote.constant === false) {
				_('instance-constant').find('li[data-value="variable"]').addClass('active');
			} else {
				_('instance-constant').find('li:first-child').addClass('active');
			}
			_('instance-max-variable-cost').val(quote.maxVariableCost || null);
			_('instance-ephemeral').prop('checked', quote.ephemeral);
			_('instance-min-quantity').val((typeof quote.minQuantity === 'number') ? quote.minQuantity : (quote.id ? 0 : 1));
			_('instance-max-quantity').val((typeof quote.maxQuantity === 'number') ? quote.maxQuantity : (quote.id ? '' : 1));
			_('instance-os').select2('data', current.select2IdentityData((quote.id && (quote.os || quote.price.os)) || 'LINUX'));
			var license = (quote.id && (quote.license || quote.price.license)) || null;
			if (license) {
				license = {
					id: license,
					text: current.formatLicense(license)
				}
			}
			_('instance-license').select2('data', license);
			_('instance-software').select2('data', current.select2IdentityData((quote.id && (quote.software || quote.price.software)) || null));
			_('instance-internet').select2('data', current.select2IdentityData(quote.internet || 'PUBLIC'));
			current.updateAutoScale();
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
			_('storage-instance').select2('data', quote.quoteInstance || null);
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
				_('instance-ram').val(ram / 1024);
			} else {
				// Keep MB
				_('instance-ram-unit').find('li:first-child').addClass('active');
				_('instance-ram').val(ram);
			}

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
			var $popup = _('popup-prov-' + type);

			// Build the playload for API service
			var suggest = {
				price: _(type + '-price').select2('data'),
				usage: _(type + '-usage').select2('data'),
				location: _(type + '-location').select2('data')
			};
			var data = {
				id: current.model.quote.id,
				name: _(type + '-name').val(),
				description: _(type + '-description').val(),
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
				complete: function () {
					current.enableCreate($popup);
				}
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

			// Specific data
			current[type + 'CommitToModel'](data, qx);

			// With related cost, other UI table need to be updated
			current.defaultCallback(type, updatedCost, qx);
			return qx;
		},

		/**
		 * Update the total cost of the quote.
		 */
		updateUiCost: function () {
			var conf = current.model.configuration;

			// Compute the new capacity and costs
			var usage = current.computeUsage();

			// Update the global counts
			current.formatCost(conf.cost, $('.cost'));

			// Instance summary
			var $instance = $('.nav-pills [href="#tab-instance"] .prov-resource-counter');
			$instance.find('.odo-wrapper').text(usage.instance.nb || 0);
			$instance.find('.odo-wrapper-unbound').text((usage.instance.nbInstances > usage.instance.nb || usage.instance.unbound) ? '+' : '');
			var $summary = $('.nav-pills [href="#tab-instance"] .summary> .badge');
			if (usage.instance.cpu.available) {
				$summary.removeClass('hidden');
				$summary.filter('.cpu').find('span').text(usage.instance.cpu.available);
				$summary.filter('.ram').find('span').text(current.formatRam(usage.instance.ram.available).replace('</span>', '').replace('<span class="unit">', ''));
				$summary.filter('.internet').find('span').text(usage.instance.publicAccess);
			} else {
				$summary.addClass('hidden');
			}

			// Storage summary
			$('.nav-pills [href="#tab-storage"] .prov-resource-counter').text(usage.storage.nb || '');
			$summary = $('.nav-pills [href="#tab-storage"] .summary> .badge.size');
			if (usage.storage.available) {
				$summary.removeClass('hidden');
				$summary.text(current.formatStorage(usage.storage.available));
			} else {
				$summary.addClass('hidden');
			}

			// Support summary
			$('.nav-pills [href="#tab-support"] .prov-resource-counter').text(usage.support.nb || '');
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
						textVertPosition: 0.6,
						waveAnimateTime: 600,
						waveHeight: 0.9,
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
				if (conf.cost.min) {
					sunburst.init('#prov-sunburst', current.toD3(usage), function (data) {
						var tooltip;
						if (data.type === 'latency') {
							tooltip = 'Latency: ' + current.formatStorageLatency(data.name, true);
						} else if (data.type === 'os') {
							tooltip = current.formatOs(data.name, true, ' fa-2x');
						} else if (data.type === 'instance') {
							var instance = current.model.configuration.instancesById[data.name];
							tooltip = 'Name: ' + instance.name
								+ '</br>Type: ' + instance.price.type.name
								+ '</br>OS: ' + current.formatOs(instance.price.os, true)
								+ '</br>Term: ' + instance.price.term.name
								+ '</br>Usage: ' + (instance.usage ? instance.usage.name : ('(default) ' + (current.model.configuration.usage ? current.model.configuration.usage.name : '100%')));
						} else if (data.type === 'storage') {
							var storage = current.model.configuration.storagesById[data.name];
							tooltip = 'Name: ' + storage.name + '</br>Type: ' + storage.price.type.name + '</br>Latency: ' + current.formatStorageLatency(storage.price.type.latency, true) + '</br>Optimized: ' + storage.price.type.optimized;
						} else if (data.type === 'support') {
							var support = current.model.configuration.supportsById[data.name];
							tooltip = 'Name: ' + support.name + '</br>Type: ' + support.price.type.name;
						} else {
							tooltip = data.name;
						}
						return '<span class="tooltip-text">' + tooltip + '<br/>Cost: ' + current.formatCost(data.size || data.value) + '</span>';
					});
					_('prov-sunburst').removeClass('hidden');
				} else {
					_('prov-sunburst').addClass('hidden');
				}
			});
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
			if (current[type + 'Table'] && current[type + 'Table'].fnSettings().oPreviousSearch.sSearch) {
				var data = _('prov-' + type + 's').DataTable().rows({ filter: 'applied' }).data();
				for (var index = 0; index < data.length; index++) {
					result.push(data[index]);
				}
			} else {
				result = current.model.configuration[type + 's'] || {};
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
			var i;

			// Instance statistics
			var publicAccess = 0;
			var instances = current.getFilteredData('instance');
			var ramAvailable = 0;
			var ramReserved = 0;
			var cpuAvailable = 0;
			var cpuReserved = 0;
			var instanceCost = 0;
			var ramAdjustedRate = conf.ramAdjustedRate / 100;
			var nbInstances = 0;
			var nbInstancesUnbound = false;
			for (i = 0; i < instances.length; i++) {
				var qi = instances[i];
				var cost = qi.cost.min || qi.cost || 0;
				nb = qi.minQuantity || 1;
				nbInstances += nb;
				nbInstancesUnbound |= (qi.maxQuantity !== nb);
				cpuAvailable += qi.price.type.cpu * nb;
				cpuReserved += qi.cpu * nb;
				ramAvailable += qi.price.type.ram * nb;
				ramReserved += (ramAdjustedRate > 1 ? qi.ram * ramAdjustedRate : qi.ram) * nb;
				instanceCost += cost;
				publicAccess += (qi.internet === 'public') ? 1 : 0;
			}

			// Storage statistics
			var storageAvailable = 0;
			var storageReserved = 0;
			var storageCost = 0;
			var storages = current.getFilteredData('storage');
			for (i = 0; i < storages.length; i++) {
				var qs = storages[i];
				nb = (qs.quoteInstance && qs.quoteInstance.minQuantity) || 1;
				storageAvailable += Math.max(qs.size, qs.price.type.minimal) * nb;
				storageReserved += qs.size * nb;
				storageCost += qs.cost;
			}

			// Support statistics
			var supportCost = 0;
			var supports = current.getFilteredData('support');
			for (i = 0; i < supports.length; i++) {
				supportCost += supports[i].cost;
			}

			return {
				cost: instanceCost + storageCost + supportCost,
				costNoSupport: instanceCost + storageCost,
				instance: {
					nb: instances.length,
					ram: {
						available: ramAvailable,
						reserved: ramReserved
					},
					cpu: {
						available: cpuAvailable,
						reserved: cpuReserved
					},
					publicAccess: publicAccess,
					nbInstances: nbInstances,
					unbound: nbInstancesUnbound,
					filtered: instances,
					cost: instanceCost
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
		 */
		detachStorage: function (storage) {
			if (storage.quoteInstance) {
				var qis = storage.quoteInstance.storages;
				for (var s = qis.length; s-- > 0;) {
					if (storage.quoteInstance.storages[s] === storage) {
						qis.splice(s, 1);
						break;
					}
				}
				delete storage.quoteInstance;
			}
		},
		/**
		 * Update the model to attach a storage to its instance
		 * @param storage The storage model to attach.
		 * @param instance The instance model or identifier to attach.
		 * @param force When <code>true</code>, the resources are attached even if they were already.
		 */
		attachStorage: function (storage, instance, force) {
			if (typeof instance === 'number') {
				instance = current.model.configuration.instancesById[instance];
			}
			if (force !== true && storage.quoteInstance === instance) {
				// Already attached or nothing to attach to the target instance
				return;
			}
			if (force !== true && storage.quoteInstance) {
				// Ddetach the old instance
				current.detachStorage(storage);
			}

			// Update the model
			if (instance) {
				if ($.isArray(instance.storages)) {
					instance.storages.push(storage);
				} else {
					instance.storages = [storage];
				}
				storage.quoteInstance = instance;
			}
		},

		toD3: function (usage) {
			var data = {
				name: 'Total',
				value: usage.cost,
				children: []
			};
			current.storageToD3(data, usage);
			current.instanceToD3(data, usage);
			current.supportToD3(data, usage);
			return data;
		},

		instanceToD3: function (data, usage) {
			var allOss = {};
			var instances = usage.instance.filtered;
			var d3instances = {
				name: '<i class="fas fa-server fa-2x"></i> ' + current.$messages['service:prov:instances-block'],
				value: 0,
				children: []
			};
			data.children.push(d3instances);
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
					d3instances.children.push(oss);
				}
				oss.value += qi.cost;
				d3instances.value += qi.cost;
				oss.children.push({
					name: qi.id,
					type: 'instance',
					size: qi.cost
				});
			}
		},

		storageToD3: function (data, usage) {
			var storages = usage.storage.filtered;
			var d3storages = {
				name: '<i class="far fa-hdd fa-2x"></i> ' + current.$messages['service:prov:storages-block'],
				value: 0,
				children: []
			};
			data.children.push(d3storages);
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
					d3storages.children.push(optimizations);
				}
				optimizations.value += qs.cost;
				d3storages.value += qs.cost;
				optimizations.children.push({
					name: qs.id,
					type: 'storage',
					size: qs.cost
				});
			}
		},

		supportToD3: function (data, usage) {
			var supports = usage.support.filtered;
			var d3supports = {
				name: '<i class="fas fa-ambulance fa-2x"></i> ' + current.$messages['service:prov:support-block'],
				value: 0,
				children: []
			};
			data.children.push(d3supports);
			for (var i = 0; i < supports.length; i++) {
				var support = supports[i];
				d3supports.value += support.cost;
				d3supports.children.push({
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
			return _('prov-instances').dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				data: current.model.configuration.instances,
				destroy: true,
				searching: true,
				createdRow: function (nRow, data) {
					$(nRow).attr('data-id', data.id);
				},
				rowCallback: function (nRow, qi) {
					$(nRow).find('.storages-tags').select2('destroy').select2({
						multiple: true,
						minimumInputLength: 1,
						createSearchChoice: function () {
							// Disable additional values
							return null;
						},
						formatInputTooShort: current.$messages['service:prov:storage-select'],
						formatResult: current.formatStoragePriceHtml,
						formatSelection: current.formatStorageHtml,
						ajax: {
							url: REST_PATH + 'service/prov/' + current.model.subscription + '/storage-lookup?instance=' + qi.id,
							dataType: 'json',
							data: function (term) {
								return {
									size: $.isNumeric(term) ? parseInt(term, 10) : 1, // search term
								};
							},
							results: function (data) {
								// Completed the requested identifier
								data.forEach(function (quote) {
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
								quoteInstance: qi.id,
								subscription: current.model.subscription
							};
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
										_('prov-instances').find('tr[data-id="' + qi.id + '"]').find('.storages-tags .select2-input').trigger('focus');
									});
								}
							});
						} else if (event.removed) {
							// Storage to delete
							var qs = event.removed.qs || event.removed;
							$.ajax({
								type: 'DELETE',
								url: REST_PATH + 'service/prov/storage/' + qs.id,
								success: function (updatedCost) {
									current.defaultCallback('storage', updatedCost);
								}
							});
						}
					});
				},
				columns: [{
					data: 'name',
					className: 'truncate'
				}, {
					data: 'minQuantity',
					className: 'hidden-xs',
					type: 'num',
					render: current.formatQuantity
				}, {
					data: 'os',
					className: 'truncate',
					width: '24px',
					render: current.formatOs
				}, {
					data: 'cpu',
					className: 'truncate',
					width: '48px',
					type: 'num',
					render: current.formatCpu
				}, {
					data: 'ram',
					className: 'truncate',
					width: '64px',
					type: 'num',
					render: current.formatRam
				}, {
					data: 'price.term.name',
					className: 'hidden-xs hidden-sm price-term',
					render: current.formatInstanceTerm
				}, {
					data: 'price.type.name',
					className: 'truncate hidden-xs hidden-sm hidden-md',
					render: current.formatInstanceType
				}, {
					data: null,
					className: 'truncate hidden-xs hidden-sm',
					render: current.formatQiStorages
				}, {
					data: 'cost',
					className: 'truncate hidden-xs',
					render: current.formatCost
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function () {
						var link =
							'<a class="update" data-toggle="modal" data-target="#popup-prov-instance"><i class="fas fa-pencil-alt" data-toggle="tooltip" title="' +
							current.$messages.update + '"></i></a>';
						link += '<a class="delete"><i class="fas fa-trash-alt" data-toggle="tooltip" title="' + current.$messages.delete +
							'"></i></a>';
						return link;
					}
				}]
			});
		},

		/**
		 * Initialize the instance datatables from the whole quote
		 */
		supportNewTable: function () {
			return _('prov-supports').dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				data: current.model.configuration.supports,
				destroy: true,
				searching: true,
				createdRow: function (nRow, data) {
					$(nRow).attr('data-id', data.id);
				},
				columns: [{
					data: 'name',
					className: 'truncate'
				}, {
					data: 'level',
					width: '128px',
					render: current.formatSupportLevel
				}, {
					data: 'seats',
					className: 'hidden-xs',
					type: 'num',
					render: current.formatSupportSeats
				}, {
					data: 'accessApi',
					className: 'hidden-xs hidden-sm hidden-md',
					render: current.formatSupportAccess
				}, {
					data: 'accessPhone',
					className: 'hidden-xs hidden-sm hidden-md',
					render: current.formatSupportAccess
				}, {
					data: 'accessEmail',
					className: 'hidden-xs hidden-sm hidden-md',
					render: current.formatSupportAccess
				}, {
					data: 'accessChat',
					className: 'hidden-xs hidden-sm hidden-md',
					render: current.formatSupportAccess
				}, {
					data: 'price.type.name',
					className: 'truncate'
				}, {
					data: 'cost',
					className: 'truncate',
					render: current.formatCost
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function () {
						var link =
							'<a class="update" data-toggle="modal" data-target="#popup-prov-support"><i class="fas fa-pencil-alt" data-toggle="tooltip" title="' +
							current.$messages.update + '"></i></a>';
						link += '<a class="delete"><i class="fas fa-trash-alt" data-toggle="tooltip" title="' + current.$messages.delete +
							'"></i></a>';
						return link;
					}
				}]
			});
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
				resources.forEach(function (resource) {
					resourcesByName[resource.name] = resource;
				});
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
			return _('prov-storages').dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				data: current.model.configuration.storages,
				destroy: true,
				searching: true,
				createdRow: function (nRow, data) {
					$(nRow).attr('data-id', data.id);
				},
				columns: [{
					data: 'name',
					className: 'truncate'
				}, {
					data: 'quoteInstance.minQuantity',
					className: 'hidden-xs',
					render: function (value, mode, data) {
						if (value) {
							return current.formatQuantity(value, mode, data);
						}
						// No related instance
						return 1;
					}
				}, {
					data: 'size',
					width: '36px',
					className: 'truncate',
					type: 'num',
					render: current.formatStorage
				}, {
					data: 'price.type.latency',
					className: 'truncate hidden-xs',
					render: current.formatStorageLatency
				}, {
					data: 'price.type.optimized',
					className: 'truncate hidden-xs',
					render: current.formatStorageOptimized
				}, {
					data: 'price.type',
					className: 'truncate hidden-xs hidden-sm hidden-md',
					render: current.formatStorageType
				}, {
					data: 'quoteInstance.name',
					className: 'truncate hidden-xs hidden-sm'
				}, {
					data: 'cost',
					className: 'truncate hidden-xs',
					render: current.formatCost
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function () {
						var links =
							'<a class="update" data-toggle="modal" data-target="#popup-prov-storage"><i class="fas fa-pencil-alt" data-toggle="tooltip" title="' + current.$messages.update + '"></i></a>';
						links += '<a class="delete"><i class="fas fa-trash-alt" data-toggle="tooltip" title="' + current.$messages.delete + '"></i></a>';
						return links;
					}
				}]
			});
		},

		/**
		 * Donut of usage
		 * @param {integer} rate The rate percent tage 1-100%
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
		 * Default Ajax callback after a deletion, update or create. This function looks the updated resources (identifiers), the deletd resources and the new updated costs.
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
			conf.cost = updatedCost.total;

			// Look the deleted resources
			Object.keys(deleted).forEach(function (type) {
				// For each deleted resource of this type, update the UI and the cost in the model
				for (var i = deleted[type].length; i-- > 0;) {
					var resource = current.delete(type.toLowerCase(), deleted[type][i]);
					if (nbDeleted++ === 0) {
						deletedSample = resource.name;
					}
				}
			});

			// Look the updated resources
			Object.keys(related).forEach(function (key) {
				// For each updated resource of this type, update the UI and the cost in the model
				Object.keys(related[key]).forEach(function (id) {
					var type = key.toLowerCase();
					var resource = conf[type + 'sById'][id];
					var cost = related[key][id];
					conf[type + 'Cost'] += cost.min - resource.cost;
					resource.cost = cost.min;
					resource.maxCost = cost.max;
					if (nbUpdated++ === 0) {
						updatedSample = resource.name;
					}
					current.redrawResource(type, id);
				});
			});

			// Update the current object
			if (resource) {
				resource.cost = updatedCost.cost.min;
				resource.maxCost = updatedCost.cost.max;
				if (conf[type + 'sById'][updatedCost.id]) {
					// Update : Redraw the row
					nbUpdated++;
					updatedSample = resource.name;
					current.redrawResource(type, updatedCost.id);
				} else {
					conf[type + 's'].push(resource);
					conf[type + 'sById'][updatedCost.id] = resource;
					resource.id = updatedCost.id;
					nbCreated++;
					createdSample = resource.name;
					_('prov-' + type + 's').DataTable().row.add(resource).draw(false);
				}
			} else if (updatedCost.id) {
				// Delete this object
				deleted++;
				deletedSample = current.delete(type, updatedCost.id).name;
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
			var resourcesById = conf[type + 'sById'][id];
			var resources = conf[type + 's'];
			for (var i = resources.length; i-- > 0;) {
				var resource = resources[i];
				if (resource.id === id) {
					resources.splice(i, 1);
					conf[type + 'Cost'] -= resource.cost;
					delete resourcesById[resource.id];
					if (type === 'storage' && resource.quoteInstance && resource.quoteInstance.id) {
						// Also redraw the instance
						var qi = resource.quoteInstance.id;
						current.detachStorage(resource);
						current.redrawResource('instance', qi);
					}
					_('prov-' + type + 's').DataTable().rows(function (_i, data) {
						return data.id === id;
					}).remove().draw(false);
					return resource;
				}
			}
		},

	};
	return current;
});
