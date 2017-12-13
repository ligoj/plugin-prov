/*jshint esversion: 6*/
define(function () {
	var current = {

		/**
		 * Instance table
		 */
		instanceTable: null,

		/**
		 * Storage table
		 */
		storageTable: null,

		/**
		 * Current quote.
		 */
		model: null,

		/**
		 * Show the members of the given group
		 */
		configure: function (subscription) {
			current.model = subscription;
			current.initializeD3();
			current.initOdometer();
			current.optimizeModel();
			current.initializeForm();
			current.initializeUpload();
			_('subscribe-configuration-prov').removeClass('hide');
			$('.provider').text(current.model.node.name);
			_('name-prov').val(current.model.configuration.name);
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
					$instances.rows.add(current.model.configuration.instances).draw();
					$storages.rows.add(current.model.configuration.storages).draw();
					_('quote-location').select2('data', current.model.configuration.location);
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
		 */
		reloadAsNeed: function (newCost) {
			if (newCost.min !== current.model.configuration.cost.min || newCost.max !== current.model.configuration.cost.max) {
				// The cost has been updated
				current.model.configuration.cost = newCost;
				notifyManager.notify(Handlebars.compile(current.$messages['service:prov:refresh-needed'])());
				current.reload();
			} else {
				// The cost still the same
				notifyManager.notify(Handlebars.compile(current.$messages['service:prov:refresh-no-change'])());				
			}
		},

		/**
		 * Render Provisioning management link.
		 */
		renderFeatures: function (subscription) {
			// Add quote configuration link
			var result = current.$super('renderServicelink')('calculator', '#/home/project/' + subscription.project + '/subscription/' + subscription.id, 'service:prov:manage');

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
			if (quote.nbInstances) {
				resources.push(current.$super('icon')('server', 'service:prov:nb-instances') + quote.nbInstances + 'VM');
				resources.push(current.$super('icon')('bolt', 'service:prov:total-cpu') + quote.totalCpu + ' ' + current.$messages['service:prov:cpu']);
				resources.push(current.$super('icon')('microchip', 'service:prov:total-ram') + current.formatRam(quote.totalRam));
			}
			if (quote.nbPublicAccess) {
				resources.push(current.$super('icon')('globe', 'service:prov:nb-public-access') + quote.nbPublicAccess);
			}
			if (quote.totalStorage) {
				resources.push(current.$super('icon')('database', 'service:prov:total-storage') + current.formatStorage(quote.totalStorage));
			}

			return current.$super('generateCarousel')(subscription, [
				['name', quote.name],
				['service:prov:resources', resources.join(', ')],
				['service:prov:location', current.$super('icon')('map-marker', 'service:prov:location') +  quote.location]
			], 1);
		},

		/**
		 * Format instance detail
		 */
		formatInstance: function (name, mode, qi) {
			var type = qi ? qi.price.type : null;
			name = type ? type.name : name;
			if (mode === 'sort' || (type && typeof type.id === 'undefined')) {
				// Use only the name
				return name;
			}
			// Instance details are available
			var details = type.description || '';
			details += '<br><i class=\'fa fa-bolt fa-fw\'></i> ';
			if (type.cpu) {
				details += type.cpu;
				details += ' ' + current.formatConstant(type.constant);
			} else {
				details += current.$messages['service:prov:instance-custom'];
			}
			if (type.ram) {
				details += '<br><i class=\'fa fa-microchip fa-fw\'></i> ';
				details += current.formatRam(type.ram);
			}

			return '<u class="instance" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
		},

		/**
		 * Format instance quantity
		 */
		formatQuantity: function (quantity, mode, instance) {
			instance = typeof instance === 'undefined' ? quantity : (instance.quoteInstance || instance);
			if (mode === 'sort' || typeof instance === 'undefined') {
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
			if (mode === 'sort') {
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
			var minStr = '';
			var maxStr = '';
			obj = (typeof obj === 'undefined' || obj === null) ? cost : obj;
			if (typeof obj.cost === 'undefined' && typeof obj.min !== 'number') {
				// Standard cost
				$cost.find('.cost-min').addClass('hidden');
				return formatter(cost, true, $cost, noRichText);
			}
			// A floating cost
			var min = obj.cost || obj.min || 0;
			var max = typeof obj.maxCost === 'number' ? obj.maxCost : obj.max;
			if ((typeof max !== 'number') || max === min) {
				// Max cost is equal to min cost, no range
				$cost.find('.cost-min').addClass('hidden');
				return formatter(obj.cost || obj.min || 0, true, $cost, noRichText);
			}

			// Max cost, is different, display a range
			return formatter(min, false, $cost, noRichText) + '-' + formatter(max, true, $cost, noRichText);
		},
		
		
		/**
		 * Configure Odometer components
		 */
		initOdometer: function() {
			var $cost = $('.cost');
			var weightUnit = '<span class="cost-weight"></span><span class="cost-unit"></span>';
			$cost.append('<span class="cost-min hidden"><span class="cost-value"></span>' + weightUnit + '<span class="cost-separator">-</span></span>');
			$cost.append('<span class="cost-max"><span class="cost-value"></span>' + weightUnit + '</span>');
			require(['../main/service/prov/lib/odometer'], function (Odometer) {
				// Odometer component
				current.registerOdometer(Odometer, $cost.find('.cost-min .cost-value'));
				current.registerOdometer(Odometer, $cost.find('.cost-max .cost-value'));
				current.registerOdometer(Odometer, $('.nav-pills [href="#tab-instance"] > .badge'));
				current.registerOdometer(Odometer, $('.nav-pills [href="#tab-storage"] > .badge'));
				var $summary = $('.nav-pills [href="#tab-instance"] .summary> .badge');
				current.registerOdometer(Odometer, $summary.filter('.cpu').find('span'));
				current.registerOdometer(Odometer, $summary.filter('.internet').find('span'));
			});
		},
		registerOdometer: function(Odometer, $container) {
			new Odometer({ el: $container[0], theme: 'minimal', duration:0}).render();
		},

		formatCostOdometer: function(cost, isMax, $cost, noRichTest) {
			if (isMax) {
				formatManager.formatCost(cost, 3, '$', 'cost-unit', function(value, weight, unit) {
					var $wrapper = $cost.find('.cost-max');
					$wrapper.find('.cost-value').html(value);
					$wrapper.find('.cost-weight').html(weight + (cost.unbound ? '+' : ''));
					$wrapper.find('.cost-unit').html(unit);
				});
			} else {
				formatManager.formatCost(cost, 3, '$', 'cost-unit', function(value, weight, unit) {
					var $wrapper = $cost.find('.cost-min').removeClass('hidden');
					$wrapper.find('.cost-value').html(value);
					$wrapper.find('.cost-weight').html(weight);
					$wrapper.find('.cost-unit').html(unit);
				});
			}
		},

		formatCostText: function(cost, isMax, _i, noRichText) {
			return formatManager.formatCost(cost, 3, '$', noRichText === true ? '' : 'cost-unit') + ((cost.unbound && isMax) ? '+' : '');
		},

		/**
		 * Format the memory size.
		 */
		formatRam: function (sizeMB) {
			return formatManager.formatSize(sizeMB * 1024 * 1024, 3);
		},

		/**
		 * Format the storage size.
		 */
		formatStorage: function (sizeGB, mode) {
			return mode === 'sort' ? sizeGB : formatManager.formatSize(sizeGB * 1024 * 1024 * 1024, 3);
		},

		/**
		 * Format the storage size to html markup.
		 * @param {object} qs Quote storage with price, type and size.
		 */
		formatStorageHtml: function (qs, showName) {
			var type = qs.price.type;
			return (showName === true ? type.name + ' ' : '') + current.formatStorageFrequency(type.frequency) +
				(type.optimized ? ' ' + current.formatStorageOptimized(type.optimized) : '') +
				' ' + formatManager.formatSize(qs.size * 1024 * 1024 * 1024, 3) +
				((qs.size < type.minimal) ? ' (' + formatManager.formatSize(type.minimal * 1024 * 1024 * 1024, 3) + ')' : '');
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
			if (mode === 'sort') {
				// Return only the sum
				return sum;
			}

			// Need to build a Select2 tags markup
			return '<input type="text" class="storages-tags" data-instance="' + instance.id + '">';
		},

		/**
		 * OS key to markup/label mapping.
		 */
		os: {
			'linux': ['Linux', 'fa fa-linux fa-fw'],
			'windows': ['Windows', 'fa fa-windows fa-fw'],
			'suse': ['SUSE', 'icon-suse fa-fw'],
			'rhel': ['Red Hat Enterprise', 'icon-redhat fa-fw'],
			'centos': ['CentOS', 'icon-centos fa-fw'],
			'debian': ['Debian', 'icon-debian fa-fw']
		},

		/**
		 * Internet Access key to markup/label mapping.
		 */
		internet: {
			'public': ['Public', 'fa fa-globe fa-fw'],
			'private': ['Private', 'fa fa-lock fa-fw'],
			'private_nat': ['NAT', 'fa fa-low-vision fa-fw']
		},

		/**
		 * Storage type key to markup/label mapping.
		 */
		storageFrequency: {
			'cold': 'fa fa-snowflake-o fa-fw',
			'hot': 'fa fa-thermometer-full fa-fw',
			'archive': 'fa fa-archive fa-fw'
		},

		/**
		 * Storage optimized key to markup/label mapping.
		 */
		storageOptimized: {
			'throughput': 'fa fa-database fa-fw',
			'iops': 'fa fa-flash fa-fw'
		},

		/**
		 * Return the HTML markup from the OS key name.
		 */
		formatOs: function (os, mode, clazz) {
			var cfg = current.os[(os.id || os || 'linux').toLowerCase()] || current.os.linux;
			if (mode === 'sort') {
				return cfg[0];
			}
			clazz = cfg[1] + (typeof clazz === 'string' ? clazz : '');
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
		},

		/**
		 * Return the HTML markup from the Internet privacy key name.
		 */
		formatInternet: function (internet, mode, clazz) {
			var cfg = (internet && current.internet[(internet.id || internet || 'public').toLowerCase()]) || current.internet.public;
			if (mode === 'sort') {
				return cfg[0];
			}
			clazz = cfg[1] + (typeof clazz === 'string' ? clazz : '');
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (mode === 'display' ? '' : ' ' + cfg[0]);
		},

		/**
		 * Return the HTML markup from the quote instance model.
		 */
		formatQuoteInstance: function (quoteInstance) {
			return quoteInstance.name;
		},

		/**
		 * Return the HTML markup from the storage frequency.
		 */
		formatStorageFrequency: function (frequency, mode, clazz) {
			var id = (frequency.id || frequency || 'cold').toLowerCase();
			var text = current.$messages['service:prov:storage-frequency-' + id];
			clazz = current.storageFrequency[id] + (typeof clazz === 'string' ? clazz : '');
			if (mode === 'sort') {
				return text;
			}

			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (mode ? ' ' + text : '');
		},

		/**
		 * Return the HTML markup from the storage optimized.
		 */
		formatStorageOptimized: function (optimized, withText, clazz) {
			if (optimized) {
				var id = (optimized.id || optimized || 'throughput').toLowerCase();
				var text = current.$messages['service:prov:storage-optimized-' + id];
				clazz = current.storageOptimized[id] + (typeof clazz === 'string' ? clazz : '');
				return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (withText ? ' ' + text : '');
			}
		},

		/**
		 * Associate the storages to the instances
		 */
		optimizeModel: function () {
			var conf = current.model.configuration;
			var instances = conf.instances;
			var i;
			conf.instancesById = {};
			conf.instanceCost = 0;
			conf.storageCost = 0;
			for (i = 0; i < instances.length; i++) {
				var qi = instances[i];
				// Optimize id access
				conf.instancesById[qi.id] = qi;
				conf.instanceCost += qi.cost;
			}
			conf.storagesById = {};
			var storages = conf.storages;
			for (i = 0; i < storages.length; i++) {
				var qs = storages[i];
				conf.storageCost += qs.cost;
				if (qs.quoteInstance) {
					// Attached storage
					qs.quoteInstance = conf.instancesById[qs.quoteInstance];
					qs.quoteInstance.storages = qs.quoteInstance.storages || [];
					qs.quoteInstance.storages.push(qs);
				}

				// Optimize id access
				conf.storagesById[qs.id] = qs;
			}
			current.updateUiCost();
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
				var value = $item.is('.input-group-btn') ? $item.find('li.active').data('value') : $item.val();
				var queryParam = value && current.toQueryName(type, $item);
				value = value && $item.is('[type="checkbox"]') ? $item.is(':checked') : value;
				value = queryParam && current['toQueryValue' + queryParam.capitalize()] ? current['toQueryValue' + queryParam.capitalize()](value, $item) : value;
				if (queryParam && value) {
					// Add as query
					queries.push(queryParam + '=' + current.cleanData('' + value));
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
				_('storage').select2({
					data: suggests,
					formatSelection: function(qs) {
						return current.formatStorageHtml(qs, true);
					},
					formatResult: function(qs) {
						return current.formatStorageHtml(qs, true);
					}
				}).select2('data', suggest);
			} else {
				_('storage').select2('data', null);
			}
		},

		/**
		 * Set the current instance price.
		 */
		instanceSetUiPrice: function (quote) {
			if (quote && quote.price) {
				var suggests = [quote];
				_('instance').select2({
					data : suggests,
					formatSelection: function(qi) {
						return qi.price.type.name + ' (' + current.formatCost(qi.cost, null, null, true) + '/m)';
					},
					formatResult: function(qi) {
						return qi.price.type.name + ' (' + current.formatCost(qi.cost, null, null, true) + '/m)';
					}
				}).select2('data', quote);
				_('instance-term').select2('data', quote.price.term).val(quote.price.term.id);
			} else {
				_('instance').select2('data', null);
			}
		},

		/**
		 * Initialize data tables and popup event : delete and details
		 */
		initializeDataTableEvents: function (type) {
			// Delete the selected instance from the quote
			var $table = _('prov-' + type + 's');
			var dataTable = current[type + 'NewTable']();
			// Delete a single row/item
			$table.on('click', '.delete', function () {
				var resource = dataTable.fnGetData($(this).closest('tr')[0]);
				$.ajax({
					type: 'DELETE',
					url: REST_PATH + 'service/prov/' + type + '/' + resource.id,
					success: function (updatedTotalCost) {
						current.model.configuration.cost = updatedTotalCost;
						current.deleteCallback(type, resource);
					}
				});
			});
			// Delete all items
			$table.on('click', '.delete-all', function () {
				$.ajax({
					type: 'DELETE',
					url: REST_PATH + 'service/prov/' + current.model.subscription + '/' + type,
					success: function (updatedTotalCost) {
						// Update the model
						current.model.configuration.cost = updatedTotalCost;
						current[type + 'Delete']();
						// Update the UI
						notifyManager.notify(Handlebars.compile(current.$messages['service:prov:' + type + '-cleared'])());
						$table.DataTable().clear().draw();
						current.updateUiCost();
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
				current.disableCreate($popup);
				quote.id && current.enableCreate($popup);
				current.model.quote = quote;
				current.toUi(type, quote);
			});
		},

		deleteCallback: function (type, resource) {
			// Update the model
			var relatedResources = current[type + 'Delete'](resource.id) || [];

			// Update the UI
			notifyManager.notify(Handlebars.compile(current.$messages['service:prov:' + type + '-deleted'])([resource.id, resource.name]));
			$('.tooltip').remove();
			_('prov-' + type + 's').DataTable().rows(function (index, data) {
				return data.id === resource.id;
			}).remove().draw(false);

			// With related cost, other UI table need to be updated
			var relatedType = type === 'instance' ? 'storage' : 'instance';
			if (type === 'instance') {
				Object.keys(relatedResources).forEach(function (index) {
					_('prov-storages').DataTable().rows(function (_i, data) {
						return data.id === relatedResources[index];
					}).remove().draw(false);
				});
			} else {
				Object.keys(relatedResources).forEach(function (index) {
					current.redrawResource(relatedType, relatedResources[index]);
				});
			}
			current.updateUiCost();
		},

		initializeUpload: function () {
			var $popup = _('popup-prov-instance-import');
			$popup.on('shown.bs.modal', function () {
				_('csv-file').trigger('focus');
			}).on('show.bs.modal', function () {
				$('.import-summary').addClass('hidden');
			}).on('submit', function (e) {
				// Avoid useless empty optional inputs
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
					},
					success: function () {
						$popup.modal('hide');

						// Refresh the data
						current.reload();
					},
					complete: function () {
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
			$('.subscribe-configuration-prov-search').on('keyup', function () {
				var type = $(this).closest('[data-prov-type]').data('prov-type');
				var table = current[type + 'Table'];
				table && table.fnFilter($(this).val());
			});
			$('input.resource-query').on('change keyup', current.checkResource);
			current.initializeDataTableEvents('instance');
			current.initializeDataTableEvents('storage');
			$('.quote-name').text(current.model.configuration.name);

			_('popup-prov-update').on('shown.bs.modal', function () {
				_('quote-name').trigger('focus');
			}).on('submit', function (e) {
				e.preventDefault();
				current.updateQuote();
			}).on('show.bs.modal', function (event) {
				_('quote-name').val(current.model.configuration.name);
				_('quote-description').val(current.model.configuration.description || '');
				_('quote-location').select2('data', current.model.configuration.location);
			});
			$('#prov-terraform-download').attr('href', REST_PATH + 'service/prov/' + current.model.subscription + '/terraform-' + current.model.subscription + '.tf');
			$('#prov-terraform-execute').on('click', current.terraform);
			$('.cost-refresh').on('click', current.refreshCost);
			$('#instance-min-quantity, #instance-max-quantity').on('change', current.updateAutoScale);

			// Related instance of the storage
			_('storage-instance').select2({
				formatSelection: current.formatQuoteInstance,
				formatResult: current.formatQuoteInstance,
				placeholder: current.$messages['service:prov:instance'],
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
				}]
			});

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
				placeholder: current.$messages['service:prov:storage-optimized-help'],
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
				}]
			});

			_('storage-frequency').select2({
				formatSelection: current.formatStorageFrequency,
				formatResult: current.formatStorageFrequency,
				escapeMarkup: function (m) {
					return m;
				},
				data: [{
					id: 'COLD',
					text: 'COLD'
				}, {
					id: 'HOT',
					text: 'HOT'
				}, {
					id: 'ARCHIVE',
					text: 'ARCHIVE'
				}]
			});

			// Memory unit, CPU constant/variable selection
			_('popup-prov-instance').on('click', '.input-group-btn li', function () {
				var $select = $(this).closest('.input-group-btn');
				$select.find('li.active').removeClass('active');
				var $active = $(this).addClass('active').find('a');
				$select.find('.btn span:first-child').html($active.find('i').length ? $active.find('i').prop('outerHTML') : $active.html());
				// Also trigger the change of the value
				_('instance-cpu').trigger('change');
			});
			_('instance-term').select2(current.instanceTermSelect2());
			_('instance-term-upload').select2(current.instanceTermSelect2(true));
			_('quote-location').select2(current.locationSelect2(false)).select2('data', current.model.configuration.location).on('change', function (event) {
				if (event.added.data) {
					current.updateLocation(event.added.data);
				}
			});
			_('instance-location').select2(current.locationSelect2(true));
			_('storage-location').select2(current.locationSelect2(true));
		},

		/**
		 * Location Select2 configuration.
		 */
		locationSelect2: function (allowClear) {
			return current.genericSelect2(true, current.locationToText, 'location');
		},

		/**
		 * Price term Select2 configuration.
		 */
		instanceTermSelect2: function (allowClear) {
			return current.genericSelect2(true, current.termToText, 'instance-price-term');
		},

		/**
		 * Generic Ajax Select2 configuration.
		 */
		genericSelect2: function (allowClear, renderer, path) {
			return {
				formatSelection: renderer,
				formatResult: renderer,
				escapeMarkup: function (m) {
					return m;
				},
				allowClear: allowClear,
				formatSearching: function () {
					return current.$messages.loading;
				},
				ajax: {
					url: function () {
						return REST_PATH + 'service/prov/' + current.model.subscription + '/' + path;
					},
					dataType: 'json',
					data: function (term, page) {
						return {
							'search[value]': term, // search term
							'q': term, // search term
							'rows': 15,
							'page': page,
							'start': (page - 1) * 15,
							'filters': '{}',
							'sidx': 'name',
							'length': 15,
							'columns[0][name]': 'name',
							'order[0][column]': 0,
							'order[0][dir]': 'asc',
							'sord': 'asc'
						};
					},
					results: function (data, page) {
						var result = [];
						$(data.data).each(function () {
							result.push({
								id: this.id,
								data: this,
								text: renderer(this)
							});
						});
						return {
							more: data.recordsFiltered > page * 10,
							results: result
						};
					}
				}
			};
		},

		/**
		 * Execute the terraform deployment
		 */
		terraform: function () {
			$.ajax({
				type: 'POST',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/terraform',
				dataType: 'json',
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages['service:prov:terraform:started'])());
				}
			});
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
		 * Update the quote name and description
		 */
		updateQuote: function () {
			var $popup = _('popup-prov-update');
			var data = {
				name: _('quote-name').val(),
				description: _('quote-description').val(),
				location: _('quote-location').val()
			};
			current.disableCreate($popup);
			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/' + current.model.subscription,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (newCost) {
					// Update the UI
					$('.quote-name').text(data.name);

					// Commit to the model
					current.model.configuration.name = data.name;
					current.model.configuration.description = data.description;
					notifyManager.notify(Handlebars.compile(current.$messages.updated)(data.name));
					$popup.modal('hide');
					current.reloadAsNeed(newCost);
				},
				complete: function () {
					current.enableCreate($popup);
				}
			});
		},

		/**
		 * Update the quote's location. If the total cost is updated, the quote will be updated.
		 * @param {object} location The location data, including identifier and name.
		 */
		updateLocation: function (location) {
			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/' + current.model.subscription,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify({
					name: current.model.configuration.name,
					description: current.model.configuration.description,
					location: location.name
				}),
				success: function (newCost) {
					// Commit to the model
					current.model.configuration.location = location;
					notifyManager.notify(Handlebars.compile(current.$messages.updated)(location.name));

					// Handle updated cost
					current.reloadAsNeed(newCost);
				}, error : function() {
					// Restore the old value
					notifyManager.notifyDanger(Handlebars.compile(current.$messages['service:prov:location-failed'])(location.name));
					_('quote-location').select2('data', current.model.configuration.location);				
				}
			});
		},

		/**
		 * Location text renderer.
		 */
		locationToText: function (location) {
			return location.name || location.text || location;
		},

		/**
		 * Price term text renderer.
		 */
		termToText: function (term) {
			return term.name || term.text || term;
		},

		/**
		 * Redraw an resource table row from its identifier
		 * @param {String} type Resource type : 'instance', 'storage'.
		 * @param {number|Object} resource Quote resource or its identifier.
		 */
		redrawResource: function (type, resource) {
			resource = resource && (resource.id || resource);
			if (resource) {
				// The instance is valid
				_('prov-' + type + 's').DataTable().rows(function (index, data) {
					return data.id === resource;
				}).invalidate().draw();
			}
		},

		storageCommitToModel: function (data, model) {
			model.size = parseInt(data.size, 10);

			// Redraw the previous instance
			if (model.quoteInstance) {
				current.redrawResource('instance', model.quoteInstance);
				current.detachStrorage(model);
			}

			// Redraw the newly attached instance
			if (data.quoteInstance) {
				model.quoteInstance = current.model.configuration.instancesById[data.quoteInstance];
				model.quoteInstance.storages = model.quoteInstance.storages ? model.quoteInstance.storages : [];
				model.quoteInstance.storages.push(model);
				current.redrawResource('instance', model.quoteInstance);
			}
		},

		instanceCommitToModel: function (data, model, updatedCost) {
			model.cpu = parseFloat(data.cpu, 10);
			model.ram = parseInt(data.ram, 10);
			model.maxVariableCost = parseFloat(data.maxVariableCost, 10);
			model.internet = data.internet;
			model.minQuantity = parseInt(data.minQuantity, 10);
			model.maxQuantity = data.maxQuantity ? parseInt(data.maxQuantity, 10) : null;
			model.constant = data.constant;
			model.os = data.os;

			// Also update the related resources costs
			var conf = current.model.configuration;
			Object.keys(updatedCost.relatedCosts).forEach(function (id) {
				var qs = conf.storagesById[id];
				conf.storageCost += updatedCost.relatedCosts[id].min - qs.cost;
				qs.cost = updatedCost.relatedCosts[id].min;
				qs.maxCost = updatedCost.relatedCosts[id].max;
			});
		},

		storageUiToData: function (data) {
			data.size = current.cleanInt(_('storage-size').val());
			data.quoteInstance = current.cleanInt(_('storage-instance').val());
			var suggest = _('storage').select2('data');
			data.type = suggest.price.type.name;
			return suggest;
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
			data.constant = current.toQueryValueConstant(_('instance-constant').find('li.active').data('value'));
			var suggest = _('instance').select2('data');
			data.price = suggest.price.id;
			return suggest;
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
			return (data && data.replace(',', '.').replace(' ', '')) || null;
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
			current[type + 'ToUi'](model);
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
			_('instance-min-quantity').val((typeof quote.minQuantity === 'number') ? quote.minQuantity : 1);
			_('instance-max-quantity').val((typeof quote.maxQuantity === 'number') ? quote.maxQuantity : 1);
			_('instance-os').select2('data', current.select2IdentityData((quote.id && (quote.os || quote.price.os)) || 'LINUX'));
			_('instance-internet').select2('data', current.select2IdentityData(quote.internet || 'PUBLIC'));
			current.updateAutoScale();
			current.instanceSetUiPrice(quote);
			quote.id || $.proxy(current.checkResource, _('popup-prov-instance'))();
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

		/**
		 * Fill the storage popup with given entity.
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		storageToUi: function (quote) {
			_('storage-size').val(quote.size || '10');
			_('storage-frequency').select2('data', current.select2IdentityData((quote.price.type && quote.price.type.frequency) || 'HOT'));
			_('storage-optimized').select2('data', current.select2IdentityData(quote.price && quote.price.type.optimized));
			_('storage-instance').select2('data', quote.quoteInstance);
			current.storageSetUiPrice(quote);
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

			// Build the playload for business service
			var data = {
				id: current.model.quote.id,
				name: _(type + '-name').val(),
				description: _(type + '-description').val(),
				subscription: current.model.subscription
			};
			// Complete the data from the UI and backup the price context
			var price = current[type + 'UiToData'](data);

			// Trim the data
			Object.keys(data).forEach(function (key) {
				(data[key] === null || data[key] === '') && delete data[key];
			});

			current.disableCreate($popup);
			$.ajax({
				type: data.id ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/' + type,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (updatedCost) {
					current.saveAndUpdateCosts(type, updatedCost, data, price);
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
		 * @param {object} suggest The last know price suggest replacing the current price. When undefined, the original price is used.
		 * @return {object} The updated or created model.
		 */
		saveAndUpdateCosts: function (type, updatedCost, data, suggest) {
			var conf = current.model.configuration;
			var id = updatedCost.id;
			if (data.id) {
				notifyManager.notify(Handlebars.compile(current.$messages.updated)(data.name));
			} else {
				notifyManager.notify(Handlebars.compile(current.$messages.created)(data.name));
			}

			// Update the model
			conf.cost = updatedCost.totalCost;
			var qx = conf[type + 'sById'][data.id] || {
				id: id,
				cost: 0
			};

			// Common data
			qx.price = (suggest || current.model.quote || qx).price;
			qx.name = data.name;
			qx.description = data.description;

			// Specific data
			current[type + 'CommitToModel'](data, qx, updatedCost);

			// Update the model cost
			var delta = updatedCost.resourceCost.min - qx.cost;
			conf[type + 'Cost'] += delta;
			qx.cost = updatedCost.resourceCost.min;
			qx.maxCost = updatedCost.resourceCost.max;

			// Update the UI
			var $table = _('prov-' + type + 's');
			if (data.id) {
				// Update : Redraw the row
				current.redrawResource(type, data.id);
			} else {
				// Create
				conf[type + 's'].push(qx);
				conf[type + 'sById'][id] = qx;

				// Add the new row
				$table.DataTable().row.add(qx).draw(false);
			}

			// With related cost, other UI table need to be updated
			var relatedType = type === 'instance' ? 'storage' : 'instance';
			Object.keys(updatedCost.relatedCosts).forEach(function (id) {
				current.redrawResource(relatedType, parseInt(id, 10));
			});

			// Update the UI costs only now
			current.updateUiCost();
			return qx;
		},

		/**
		 * Update the total cost of the quote.
		 */
		updateUiCost: function () {
			var conf = current.model.configuration;

			// Compute the new usage and costs
			var usage = current.usageGlobalRate();

			// Update the global counts
			current.formatCost(conf.cost, $('.cost'));

			$('.nav-pills [href="#tab-instance"] > .badge').first().text(conf.instances.length || '');
			$('.nav-pills [href="#tab-storage"] > .badge').first().text(conf.storages.length || '');

			// Update the summary
			var $summary = $('.nav-pills [href="#tab-instance"] .summary> .badge');
			if (usage.cpu.available) {
				$summary.removeClass('hidden');
				$summary.filter('.cpu').find('span').text(usage.cpu.available);
				$summary.filter('.ram').find('span').text(current.formatRam(usage.ram.available).replace('</span>','').replace('<span class="unit">',''));
				$summary.filter('.internet').find('span').text(usage.publicAccess);
			} else {
				$summary.addClass('hidden');
			}
			$summary = $('.nav-pills [href="#tab-storage"] .summary> .badge.size');
			if (usage.storage.available) {
				$summary.removeClass('hidden');
				$summary.text(current.formatStorage(usage.storage.available));
			} else {
				$summary.addClass('hidden');
			}

			// Update the total resource usage
			require(['d3', '../main/service/prov/lib/sunburst'], function (d3, sunburst) {
				var weight = 0;
				var weightUsage = 0;
				if (usage.cpu.available) {
					weightUsage += 50 * usage.cpu.used / usage.cpu.available;
					weight += 50;
				}
				if (usage.ram.available) {
					weightUsage += 10 * usage.ram.used / usage.ram.available;
					weight += 10;
				}
				if (usage.storage.available) {
					weightUsage += usage.storage.used / usage.storage.available;
					weight += 1;
				}
				if (d3.select('#gauge-global').on('valueChanged') && weight) {
					$('#gauge-global').removeClass('hidden');
					// Weight average of average...
					d3.select('#gauge-global').on('valueChanged')(Math.floor(weightUsage * 100 / weight));
				} else {
					$('#gauge-global').addClass('hidden');
				}
				if (conf.cost.min) {
					sunburst.init('#sunburst', current.toD3(), function (data) {
						return data.name + ', cost: ' + current.formatCost(data.size || data.value);
					});
					$('#sunburst').removeClass('hidden');
				} else {
					$('#sunburst').addClass('hidden');
				}
			});
		},

		/**
		 * Compute the global resource usage of this quote. Only minimal quantities are considered and with minimal to 1.
		 * Maximal quantities is currently ignored.
		 */
		usageGlobalRate: function () {
			var conf = current.model.configuration;
			var ramAvailable = 0;
			var ramUsed = 0;
			var cpuAvailable = 0;
			var cpuUsed = 0;
			var storageAvailable = 0;
			var storageUsed = 0;
			var nb = 0;
			var publicAccess = 0;
			for (var i = 0; i < conf.instances.length; i++) {
				var qi = conf.instances[i];
				nb = qi.minQuantity || 1;
				cpuAvailable += qi.price.type.cpu * nb;
				cpuUsed += qi.cpu * nb;
				ramAvailable += qi.price.type.ram * nb;
				ramUsed += qi.ram * nb;
				publicAccess += (qi.internet === 'public') ? 1 : 0;
			}
			for (i = 0; i < conf.storages.length; i++) {
				var qs = conf.storages[i];
				nb = (qs.quoteInstance && qs.quoteInstance.minQuantity) || 1;
				storageAvailable += Math.max(qs.size, qs.price.type.minimal) * nb;
				storageUsed += qs.size * nb;
			}
			return {
				publicAccess: publicAccess,
				ram: {
					available: ramAvailable,
					used: ramUsed
				},
				cpu: {
					available: cpuAvailable,
					used: cpuUsed
				},
				storage: {
					available: storageAvailable,
					used: storageUsed
				}
			};
		},

		/**
		 * Update the model a deleted quote storage
		 * @param id Option identifier to delete. When not defined, all items are deleted.
		 */
		storageDelete: function (id) {
			var conf = current.model.configuration;
			for (var i = conf.storages.length; i-- > 0;) {
				var storage = conf.storages[i];
				if (typeof id === 'undefined' || storage.id === id) {
					conf.storages.splice(i, 1);
					conf.storageCost -= storage.cost;
					var instance = storage.quoteInstance && storage.quoteInstance.id;
					current.detachStrorage(storage);
					if (id) {
						// Unique item to delete
						return instance && [instance];
					}
				}
			}
		},

		/**
		 * Update the model to detach a storage from its instance
		 * @param storage The storage model to detach.
		 */
		detachStrorage: function (storage) {
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
		 * Update the model and the association with a deleted quote instance
		 * @param id Option identifier to delete. When not defined, all items are deleted.
		 * @return The related storage resource identifiers also deleted.
		 */
		instanceDelete: function (id) {
			var conf = current.model.configuration;
			var deletedStorages = [];
			for (var i = conf.instances.length; i-- > 0;) {
				var qi = conf.instances[i];
				if (typeof id === 'undefined' || qi.id === id) {
					conf.instances.splice(i, 1);
					conf.instanceCost -= qi.cost;
					delete conf.instancesById[qi.id];
					// Also delete the related storages
					for (var s = conf.storages.length; s-- > 0;) {
						var qs = conf.storages[s];
						if (qs.quoteInstance && qs.quoteInstance.id === qi.id) {
							// Delete the associated storages
							conf.storages.splice(s, 1);
							conf.storageCost -= qs.cost;
							delete conf.storagesById[qs.id];
							deletedStorages.push(qs.id);
						}
					}
					if (id) {
						// Unique item to delete
						break;
					}
				}
			}
			return deletedStorages;
		},

		/**
		 * Initialize D3 graphics with default empty data.
		 */
		initializeD3: function () {
			require(['d3', '../main/service/prov/lib/gauge'], function (d3) {
				current.initializeD3Gauge(d3);
				// First render
				current.updateUiCost();
			});
		},

		toD3: function () {
			var conf = current.model.configuration;
			var data = {
				name: 'Total',
				value: conf.cost,
				children: []
			};
			var instances;
			var storages;
			var allOss = {};
			if (conf.instances.length) {
				instances = {
					name: '<i class="fa fa-server fa-2x"></i> ' + current.$messages['service:prov:instances-block'],
					value: 0,
					children: []
				};
				data.children.push(instances);
			}
			if (conf.storages.length) {
				storages = {
					name: '<i class="fa fa-database fa-2x"></i> ' + current.$messages['service:prov:storages-block'],
					value: 0,
					children: []
				};
				data.children.push(storages);
			}
			for (var i = 0; i < conf.instances.length; i++) {
				var qi = conf.instances[i];
				var oss = allOss[qi.os];
				if (typeof oss === 'undefined') {
					// First OS
					oss = {
						name: current.formatOs(qi.os, true, ' fa-2x'),
						value: 0,
						children: []
					};
					allOss[qi.os] = oss;
					instances.children.push(oss);
				}
				oss.value += qi.cost;
				instances.value += qi.cost;
				oss.children.push({
					name: qi.name,
					size: qi.cost
				});
			}
			var allFrequencies = {};
			for (i = 0; i < conf.storages.length; i++) {
				var qs = conf.storages[i];
				var frequencies = allFrequencies[qs.price.type.frequency];
				if (typeof frequencies === 'undefined') {
					// First OS
					frequencies = {
						name: current.formatStorageFrequency(qs.price.type.frequency, true, ' fa-2x'),
						value: 0,
						children: []
					};
					allFrequencies[qs.price.type.frequency] = frequencies;
					storages.children.push(frequencies);
				}
				frequencies.value += qs.cost;
				storages.value += qs.cost;
				frequencies.children.push({
					name: qs.name,
					size: qs.cost
				});
			}

			return data;
		},

		/**
		 * Initialize the gauge
		 */
		initializeD3Gauge: function (d3) {
			d3.select('#gauge-global').call(d3.liquidfillgauge, 1, {
				textColor: '#FF4444',
				textVertPosition: 0.6,
				waveAnimateTime: 1200,
				waveHeight: 0.9,
				backgroundColor: '#e0e0e0'
			});
		},

		/**
		 * Initialize the instance datatables from the whole quote
		 */
		instanceNewTable: function () {
			current.instanceTable = _('prov-instances').dataTable({
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
						formatResult: function (qs) {
							return current.formatStorageHtml(qs) + ' ' + qs.price.type.name + '<span class="pull-right text-small">' + current.formatCost(qs.cost) + '<span class="cost-unit">/m</span></span>';
						},
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
									current.saveAndUpdateCosts('storage', updatedCost, data, suggest);

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
								success: function (updatedTotalCost) {
									current.model.configuration.cost = updatedTotalCost;
									current.deleteCallback('storage', qs);
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
					render: current.formatQuantity
				}, {
					data: 'os',
					className: 'truncate',
					width: '24px',
					render: current.formatOs
				}, {
					data: 'cpu',
					className: 'truncate',
					width: '48px'
				}, {
					data: 'ram',
					className: 'truncate',
					width: '48px',
					render: current.formatRam
				}, {
					data: 'price.term.name',
					className: 'hidden-xs hidden-sm price-term'
				}, {
					data: 'price.type.name',
					className: 'truncate hidden-xs hidden-sm hidden-md',
					render: current.formatInstance
				}, {
					data: null,
					className: 'truncate hidden-xs hidden-sm',
					render: current.formatQiStorages
				}, {
					data: 'internet',
					className: 'hidden-xs',
					render: current.formatInternet
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
							'<a class="update" data-toggle="modal" data-target="#popup-prov-instance"><i class="fa fa-pencil" data-toggle="tooltip" title="' +
							current.$messages.update + '"></i></a>';
						link += '<a class="delete"><i class="fa fa-trash" data-toggle="tooltip" title="' + current.$messages.delete +
							'"></i></a>';
						return link;
					}
				}]
			});

			return current.instanceTable;
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
			current.storageTable = _('prov-storages').dataTable({
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
					render: current.formatQuantity
				}, {
					data: 'size',
					width: '36px',
					className: 'truncate',
					render: current.formatStorage
				}, {
					data: 'price.type.frequency',
					className: 'truncate hidden-xs',
					render: current.formatStorageFrequency
				}, {
					data: 'price.type.optimized',
					className: 'truncate hidden-xs',
					render: current.formatStorageOptimized
				}, {
					data: 'price.type.name',
					className: 'truncate hidden-xs hidden-sm hidden-md'
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
							'<a class="update" data-toggle="modal" data-target="#popup-prov-storage"><i class="fa fa-pencil" data-toggle="tooltip" title="' + current.$messages.update + '"></i></a>';
						links += '<a class="delete"><i class="fa fa-trash" data-toggle="tooltip" title="' + current.$messages.delete + '"></i></a>';
						return links;
					}
				}]
			});
			return current.storageTable;
		}
	};
	return current;
});
