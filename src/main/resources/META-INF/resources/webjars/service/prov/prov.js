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
		 * Current configuration.
		 */
		model: null,

		/**
		 * Instance identifier.
		 */
		currentId: null,

		/**
		 * Show the members of the given group
		 */
		configure: function (subscription) {
			current.model = subscription;
			current.initializeD3();
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
				}
			});
		},

		/**
		 * Request to refresh the cost and trgger a global update as needed
		 */
		refreshCost: function () {
			var min = current.model.configuration.cost.min;
			var max = current.model.configuration.cost.max;
			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/refresh',
				dataType: 'json',
				success: function (updatedTotalCost) {
					if (updatedTotalCost.min !== min || updatedTotalCost.max !== max) {
						// The cost has been updated
						current.model.configuration.cost = updatedTotalCost;
						notifyManager.notify(Handlebars.compile(current.$messages['service:prov:refresh-needed'])());
						current.reload();
					}
				}
			});
		},

		/**
		 * Render LDAP.
		 */
		renderFeatures: function (subscription) {
			// Add quote configuration link
			var result = current.$super('renderServicelink')('calculator', '#/home/project/' + subscription.project + '/subscription/' + subscription.id, 'service:prov:manage');

			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:prov:help');
			return result;
		},

		/**
		 * Display the details of the quote
		 */
		renderDetailsFeatures: function (subscription) {
			if (subscription.data.quote && subscription.data.quote.cost) {
				return '<span data-toggle="tooltip" title="' + current.$messages['service:prov:cost-title'] + '" class="label label-default">' + current.formatCost(subscription.data.quote.cost) + '$</span>';
			}
		},

		/**
		 * Render provisioning details : cpu, ram, nbVm, storages.
		 */
		renderDetailsKey: function (subscription) {
			var quote = subscription.data.quote;

			return current.$super('generateCarousel')(subscription, [
				['name', quote.name],
				['service:prov:resources',
					current.$super('icon')('microchip', 'service:prov:total-ram') + current.formatRam(quote.totalRam) + ', ' + current.$super('icon')('bolt',
						'service:prov:total-cpu') + quote.totalCpu + ' CPU, ' + current.$super('icon')('database', 'service:prov:total-storage') + (current.formatStorage(quote.totalStorage) ||
						'0')
				],
				['service:prov:nb-instances', current.$super('icon')('server', 'service:prov:nb-instances') + quote.nbInstances]
			], 1);
		},

		/**
		 * Format instance detail
		 */
		formatInstance: function (name, mode, qi) {
			var instance = qi ? qi.instancePrice.instance : null;
			name = instance ? instance.name : name;
			if (mode === 'sort' || (instance && typeof instance.id === 'undefined')) {
				// Use only the name
				return name;
			}
			// Instance details are available
			var details = instance.description || '';
			details += '<br><i class=\'fa fa-bolt fa-fw\'></i> ';
			if (instance.cpu) {
				details += instance.cpu;
				details += ' ' + current.formatConstant(instance.constant);
			} else {
				details += current.$messages['service:prov:instance-custom'];
			}
			if (instance.ram) {
				details += '<br><i class=\'fa fa-microchip fa-fw\'></i> ';
				details += current.formatRam(instance.ram);
			}

			return '<u class="instance" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
		},

		/**
		 * Format instance quantity
		 */
		formatQuantity: function(quantity, mode, instance) {
			instance = typeof instance === 'undefined' ? quantity : (instance.quoteInstance || instance);
			if (mode === 'sort' || typeof instance === 'undefined') {
				return quantity;
			}
			
			var min = instance.minQuantity || 0;
			var max = instance.maxQuantity;
			if (typeof max === 'undefined') {
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
		 */
		formatCost: function (cost, mode, obj) {
			if (mode === 'sort') {
				return cost;
			}
			var costStr = '';
			obj = typeof obj === 'undefined' ? cost : obj;
			if (typeof obj.cost === 'undefined' && typeof obj.min === 'undefined') {
				// Standard cost
				return formatManager.formatCost(cost, 3, '$');
			}
			// A floating cost
			var min = obj.cost || obj.min || 0;
			var max = typeof obj.maxCost === 'undefined' ? obj.max : obj.maxCost;
			if ((typeof max === 'undefined') || max === min) {
				// Max cost is equal to min cost, no range
				costStr = formatManager.formatCost(obj.cost || obj.min || 0, 3, '$');
			} else {
				// Max cost, is different, display a range
				costStr = formatManager.formatCost(min, 3, '$') + '-' + formatManager.formatCost(max, 3, '$');
			}
			return cost.unboundedCost ? costStr + '+' : costStr;
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
		formatStorageHtml: function (qs) {
			return current.formatStorageFrequency(qs.type.frequency) + (qs.type.optimized ? ' ' + current.formatStorageOptimized(qs.type.optimized) : '') + ' ' + formatManager.formatSize(qs.size * 1024 * 1024 * 1024, 3);
		},

		/**
		 * Format an attached storages
		 */
		formatQiStorages: function (instance, mode) {
			// Compute the sum
			var storages = instance.storages;
			var sum = 0;
			if (storages) {
				for (var i = 0; i < storages.length; i++) {
					sum += storages[i].size;
				}
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
			'rhe': ['Red Hat Enterprise', 'icon-redhat fa-fw']
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
			conf.instancesById = {};
			conf.instanceCost = 0;
			conf.storageCost = 0;
			for (var i = 0; i < instances.length; i++) {
				var instance = instances[i];
				// Optimize id access
				conf.instancesById[instance.id] = instance;
				conf.instanceCost += instance.cost;
			}
			conf.storagesById = {};
			var storages = conf.storages;
			for (i = 0; i < storages.length; i++) {
				var storage = storages[i];
				conf.storageCost += storage.cost;
				if (storage.quoteInstance) {
					// Attached storage
					storage.quoteInstance = conf.instancesById[storage.quoteInstance];
					storage.quoteInstance.storages = storage.quoteInstance.storages || [];
					storage.quoteInstance.storages.push(storage);
				}

				// Optimize id access
				conf.storagesById[storage.id] = storage;
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
			// Disable the submit while checking the resource
			current.disableCreate($popup);
			// Build the query
			$form.find('.resource-query').each(function () {
				var $item = $(this);
				var value = $item.is('.input-group-btn') ? $item.find('li.active').data('value') : $item.val();
				var queryParam = value && current.toQueryName(type, $item);
				value = queryParam && current['toQueryValue' + queryParam.capitalize()] ? current['toQueryValue' + queryParam.capitalize()](value, $item) : value;
				if (queryParam && value) {
					// Add as query
					queries.push(queryParam + '=' + current.cleanData('' + value));
				}
			});
			// Check the availability of this instance for these requirements
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/' + current.model.subscription + '/' + type + '-lookup/?' + queries.join('&'),
				type: 'GET',
				success: function (price) {
					var callbackUi = current[type + 'SetUiPrice'];
					var valid = current[type + 'ValidatePrice'](price);
					if (valid) {
						// The resource is valid, enable the create
						current.enableCreate($popup);
					}
					callbackUi(valid);
				}
			});
		},

		instanceValidatePrice: function (price) {
			var instances = [];
			price.instance && instances.push(price.instance);
			price.customInstance && instances.push(price.customInstance);
			if (instances.length) {
				// There is at least one valid instance
				// For now, renders only the lowest priced instance
				var lowest;
				if (instances.length == 1) {
					lowest = instances[0];
				} else if (instances[0].cost < instances[1].cost) {
					lowest = instances[0];
				} else {
					lowest = instances[1];
				}
				// TODO Add warning about custom instance
				return lowest;
			}
			// Out of bound requirements
			traceLog('Out of bounds for this requirement');
		},

		storageValidatePrice: function (price) {
			return price.length ? price[0] : null;
		},

		/**
		 * Set the current storage price.
		 */
		storageSetUiPrice: function (price) {
			current.model.storagePrice = price;
			_('storage').select2('data', price || null).val(price ? price.type.name + ' (' + current.formatCost(price.cost) + '/m)' : '');
		},

		/**
		 * Set the current instance price.
		 */
		instanceSetUiPrice: function (price) {
			price = price || {};
			current.model.instancePrice = price;

			if (price.instance) {
				_('instance').val(price.instance.instance.name + ' (' + current.formatCost(price.cost) + '/m)');
				_('instance-price-type').select2('data', price.instance.type).val(price.instance.type.id);
			} else {
				_('instance').val('');
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
				var model = ($tr.length && dataTable.fnGetData($tr[0])) || {};
				$(this).find('input[type="submit"]').removeClass('btn-primary btn-success').addClass(model.id ? 'btn-primary' : 'btn-success');
				current.disableCreate($popup);
				model.id && current.enableCreate($popup);
				current.toUi(type, model);
			});
		},

		deleteCallback: function (type, resource) {
			// Update the model
			var relatedResources = current[type + 'Delete'](resource.id);

			// Update the UI
			notifyManager.notify(Handlebars.compile(current.$messages['service:prov:' + type + '-deleted'])([resource.id, resource.name]));
			$('.tooltip').remove();
			var $table = _('prov-' + type + 's');
			$table.find('tr[data-id="' + resource.id + '"]').each(function () {
				$table.DataTable().row($(this)[0]).remove().draw(false);
			});
			
			// With related cost, other UI table need to be updated
			if (relatedResources) {
				var $relatedTable = _('prov-storages');
				Object.keys(relatedResources).forEach((id) => {
					$relatedTable.find('tr[data-id="' + id + '"]').each(function () {
						$relatedTable.DataTable().row($(this)[0]).remove().draw(false);
					});
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
					complete: function (id) {
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
			$('.prov-project-edit').on('click', function () {
				bootbox.prompt({
					title: current.$messages.name,
					callback: current.updateQuoteName,
					value: current.model.configuration.name
				});
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
				escapeMarkup: function (m, d) {
					return m;
				},
				data: function (term, re) {
					return {
						results: current.model.configuration.instances
					};
				}
			});
			_('instance-os').select2({
				formatSelection: current.formatOs,
				formatResult: current.formatOs,
				escapeMarkup: function (m, d) {
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
					id: 'RHE',
					text: 'RHE'
				}]
			});

			_('instance-internet').select2({
				formatSelection: current.formatInternet,
				formatResult: current.formatInternet,
				escapeMarkup: function (m, d) {
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
				escapeMarkup: function (m, d) {
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
				escapeMarkup: function (m, d) {
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
			_('instance-price-type').select2(current.instancePriceTypeSelect2());
			_('instance-price-type-upload').select2(current.instancePriceTypeSelect2(true));
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
			_('instance-auto-scale').prop('checked', _('instance-min-quantity').val() !== _('instance-max-quantity').val());
		},

		/**
		 * Update the quote name
		 */
		updateQuoteName: function (name) {
			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/' + current.model.subscription,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify({
					name: name,
					description: current.model.configuration.description
				}),
				success: function () {
					$('.quote-name').text(name);
					notifyManager.notify(Handlebars.compile(current.$messages.updated)(name));
				}
			});
		},

		instancePriceTypeSelect2: function (allowClear) {
			return {
				formatSelection: current.priceTypeToText,
				formatResult: current.priceTypeToText,
				escapeMarkup: function (m) {
					return m;
				},
				allowClear: allowClear,
				formatSearching: function () {
					return current.$messages.loading;
				},
				ajax: {
					url: function () {
						return REST_PATH + 'service/prov/' + current.model.subscription + '/instance-price-type';
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
								text: current.priceTypeToText(this)
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

		priceTypeToText: function (priceType) {
			return priceType.name || priceType.text || priceType;
		},

		storageCommitToModel: function (data, model, costContext) {
			model.size = parseInt(data.size, 10);
			model.type = costContext.type;

			// Manage the attached quote instance
			if (model.quoteInstance) {
				current.redrawInstance(model.quoteInstance);
				current.detachStrorage(model);
			}

			if (data.quoteInstance) {
				model.quoteInstance = current.model.configuration.instancesById[data.quoteInstance];
				model.quoteInstance.storages = model.quoteInstance.storages ? model.quoteInstance.storages : [];
				model.quoteInstance.storages.push(model);
				current.redrawInstance(model.quoteInstance);
			}
		},

		/**
		 * Redraw an instance table row from its identifier
		 * @param {number|Object} instance Quote instance or its identifier.
		 */
		redrawInstance: function (instance) {
			instance = instance && (instance.id || instance);
			if (instance) {
				// The instance is valid
				var $itable = _('prov-instances');
				var $row = $itable.find('tr[data-id="' + instance + '"]');
				if ($row.length) {
					// This has been found and can be drawn
					$itable.DataTable().row($row[0]).invalidate().draw();
				}
			}
		},

		instanceCommitToModel: function (data, model, costContext, updatedCost) {
			model.cpu = parseFloat(data.cpu, 10);
			model.ram = parseInt(data.ram, 10);
			model.maxVariableCost = parseFloat(data.maxVariableCost, 10);
			model.internet = data.internet;
			model.minQuantity = parseInt(data.minQuantity, 10);
			model.maxQuantity = parseInt(data.maxQuantity, 10);
			model.constant = data.constant;
			model.instancePrice = costContext.instance;
			
			// Also update the related resources costs
			var conf = current.model.configuration;
			var storageCost = 0;
			Object.keys(updatedCost.relatedCosts).forEach((id) => {
				var storage = conf.storagesById[id];
				conf.storageCost += updatedCost.relatedCosts[id].min - storage.cost.min;
				storage.cost = updatedCost.relatedCosts[id];
			});
		},

		storageUiToData: function (data) {
			data.size = current.cleanInt(_('storage-size').val());
			data.type = current.model.storagePrice.type.id;
			data.quoteInstance = current.cleanInt(_('storage-instance').val());
			return current.model.storagePrice;
		},

		instanceUiToData: function (data) {
			data.cpu = current.cleanFloat(_('instance-cpu').val());
			data.ram = current.toQueryValueRam(_('instance-ram').val());
			data.maxVariableCost = current.cleanFloat(_('instance-max-variable-cost').val());
			data.internet = _('instance-internet').val().toLowerCase();
			data.minQuantity = current.cleanInt(_('instance-min-quantity').val()) || 0;
			data.maxQuantity = current.cleanInt(_('instance-max-quantity').val()) || null;
			data.constant = current.toQueryValueConstant(_('instance-constant').find('li.active').data('value'));
			data.instancePrice = current.model.instancePrice.instance.id;
			return current.model.instancePrice;
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
			current.currentId = model.id;
			_(type + '-name').val(model.name || current.findNewName(current.model.configuration[type + 's'], type));
			_(type + '-description').val(model.description || '');
			current[type + 'ToUi'](model);
		},

		/**
		 * Fill the instance popup with given entity or default values.
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		instanceToUi: function (model) {
			_('instance-cpu').val(model.cpu || 1);
			current.adaptRamUnit(model.ram || 2048);
			_('instance-constant').find('li.active').removeClass('active');
			if (model.constant === true) {
				_('instance-constant').find('li[data-value="constant"]').addClass('active');
			} else if (model.constant === false) {
				_('instance-constant').find('li[data-value="variable"]').addClass('active');
			} else {
				_('instance-constant').find('li:first-child').addClass('active');
			}
			_('instance-max-variable-cost').val(model.maxVariableCost || null);
			_('instance-min-quantity').val((typeof model.minQuantity === 'undefined') ? 1 : model.minQuantity);
			_('instance-max-quantity').val((typeof model.maxQuantity === 'undefined') ? 1 : model.maxQuantity);
			_('instance-os').select2('data', current.select2IdentityData((model.id && model.instancePrice.os) || 'LINUX'));
			_('instance-internet').select2('data', current.select2IdentityData(model.internet || 'PUBLIC'));
			current.updateAutoScale();
			current.instanceSetUiPrice(model.id && {
				cost: model.cost,
				instance: model.instancePrice
			});
			model.id || $.proxy(current.checkResource, _('popup-prov-instance'))();
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
		storageToUi: function (model) {
			_('storage-size').val(model.size || '10');
			_('storage-frequency').select2('data', current.select2IdentityData((model.type && model.type.frequency) || 'HOT'));
			_('storage-optimized').select2('data', current.select2IdentityData(model.type && model.type.optimized));
			_('storage-instance').select2('data', model.quoteInstance);

			current.storageSetUiPrice(model.id && {
				cost: model.cost,
				type: model.type
			});
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
				id: current.currentId,
				name: _(type + '-name').val(),
				description: _(type + '-description').val(),
				subscription: current.model.subscription
			};
			// Complete the data from the UI and backup the price context
			var priceContext = current[type + 'UiToData'](data);

			// Trim the data
			Object.keys(data).forEach((key) => (data[key] === null || data[key] === '') && delete data[key]);
			$.ajax({
				type: data.id ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/' + type,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (updatedCost) {
					current.saveCallback(type, updatedCost, data, priceContext);
					$popup.modal('hide');
				}
			});
		},

		/**
		 * Commit to the model the saved data (server side) and update the computed cost.
		 * @param {string} type Resource type to save.
		 * @param {string} updatedCost The new updated cost with identifier, total cost, resource cost and related resources costs.
		 * @param {object} data The original data sent to the back-end.
		 * @param {object} priceContext The price context to commit in addition of data.
		 * @return {object} The updated or created model.
		 */
		saveCallback: function (type, updatedCost, data, priceContext) {
			var conf = current.model.configuration;
			var id = updatedCost.id;
			if (current.currentId) {
				notifyManager.notify(Handlebars.compile(current.$messages.updated)(data.name));
			} else {
				notifyManager.notify(Handlebars.compile(current.$messages.created)(data.name));
			}

			// Update the model
			conf.cost = updatedCost.totalCost;
			var model = conf[type + 'sById'][data.id] || {
				id: id,
				cost: 0
			};

			// Common data
			model.name = data.name;
			model.description = data.description;

			// Specific data
			current[type + 'CommitToModel'](data, model, priceContext, updatedCost);

			// Update the model cost
			var delta = updatedCost.resourceCost.min - model.cost;
			conf[type + 'Cost'] += delta;
			model.cost = updatedCost.resourceCost.min;
			model.maxCost = updatedCost.resourceCost.max;

			// Update the UI
			var $table = _('prov-' + type + 's');
			if (data.id) {
				// Update : Redraw the row
				$table.find('tr[data-id="' + data.id + '"]').each(function () {
					$table.DataTable().row($(this)[0]).invalidate().draw();
				});
				
				// With related cost, other UI table need to be updated
				var $relatedTable = _('prov-storages');
				Object.keys(updatedCost.relatedCosts).forEach((id) => {
					$relatedTable.find('tr[data-id="' + id + '"]').each(function () {
						$relatedTable.DataTable().row($(this)[0]).invalidate().draw();
					});
				});
			} else {
				// Create
				conf[type + 's'].push(model);
				conf[type + 'sById'][id] = model;

				// Add the new row
				$table.DataTable().row.add(model).draw(false);
			}

			// Update the UI costs only now
			current.updateUiCost();
			return model;
		},

		/**
		 * Update the total cost of the quote.
		 */
		updateUiCost: function () {
			$('.cost').text(current.formatCost(current.model.configuration.cost) || '-');
			$('.nav-pills [href="#tab-instance"] > .badge').text(current.model.configuration.instances.length || '');
			$('.nav-pills [href="#tab-storage"] > .badge').text(current.model.configuration.storages.length || '');
			// Update the total resource usage
			require(['d3', '../main/service/prov/lib/sunburst'], function (d3, sunburst) {
				var usage = current.usageGlobalRate();
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
				if (current.model.configuration.cost.min) {
					sunburst.init('#sunburst', current.toD3());
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
			for (var i = 0; i < conf.instances.length; i++) {
				var instance = conf.instances[i];
				nb = instance.minQuantity || 1;
				cpuAvailable += instance.instancePrice.instance.cpu * nb;
				cpuUsed += instance.cpu * nb;
				ramAvailable += instance.instancePrice.instance.ram * nb;
				ramUsed += instance.ram * nb;
			}
			for (i = 0; i < conf.storages.length; i++) {
				var storage = conf.storages[i];
				nb = storage.quoteInstance ? storage.quoteInstance.minQuantity || 1 : 1;
				storageAvailable += Math.max(storage.size, storage.type.minimal) * nb;
				storageUsed += storage.size * nb;
			}
			return {
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
					current.detachStrorage(storage);
					if (id) {
						// Unique item to delete
						break;
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
				var instance = conf.instances[i];
				if (typeof id === 'undefined' || instance.id === id) {
					conf.instances.splice(i, 1);
					conf.instanceCost -= instance.cost;
					delete conf.instancesById[instance.id];
					// Also delete the related storages
					for (var s = conf.storages.length; s-- > 0;) {
						var storage = conf.storages[s];
						if (storage.quoteInstance && storage.quoteInstance.id === instance.id) {
							// Delete the associated storages
							conf.storages.splice(s, 1);
							conf.storageCost -= storage.cost;
							delete conf.storagesById[storage.id];
							deletedStorages.push(storage.id);
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
			require(['d3', '../main/service/prov/lib/liquidFillGauge'], function (d3, gauge) {
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
				var instance = conf.instances[i];
				var oss = allOss[instance.instancePrice.os];
				if (typeof oss === 'undefined') {
					// First OS
					oss = {
						name: current.formatOs(instance.instancePrice.os, true, ' fa-2x'),
						value: 0,
						children: []
					};
					allOss[instance.instancePrice.os] = oss;
					instances.children.push(oss);
				}
				oss.value += instance.cost;
				instances.value += instance.cost;
				oss.children.push({
					name: instance.name,
					size: instance.cost
				});
			}
			var allFrequencies = {};
			for (i = 0; i < conf.storages.length; i++) {
				var storage = conf.storages[i];
				var frequencies = allFrequencies[storage.type.frequency];
				if (typeof frequencies === 'undefined') {
					// First OS
					frequencies = {
						name: current.formatStorageFrequency(storage.type.frequency, true, ' fa-2x'),
						value: 0,
						children: []
					};
					allFrequencies[storage.type.frequency] = frequencies;
					storages.children.push(frequencies);
				}
				frequencies.value += storage.cost;
				storages.value += storage.cost;
				frequencies.children.push({
					name: storage.name,
					size: storage.cost
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
				textVertPosition: 0.2,
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
							return current.formatStorageHtml(qs) + ' ' + qs.type.name + '<span class="pull-right text-small">' + current.formatCost(qs.cost) + '/m</span>';
						},
						formatSelection: current.formatStorageHtml,
						ajax: {
							url: REST_PATH + 'service/prov/' + current.model.subscription + '/storage-lookup?instance=' + qi.id,
							dataType: 'json',
							data: function (term, page) {
								return {
									size: $.isNumeric(term) ? parseInt(term, 10) : 1, // search term
								};
							},
							results: function (data, page) {
								// Completed the requested identifier
								for (var item of data) {
									item.id = item.type.id + '-' + new Date().getMilliseconds();
									item.text = item.type.name;
								}
								return {
									more: false,
									results: data
								};
							}
						}
					}).select2('data', qi.storages || []).off('change').on('change', function (event) {
						if (event.added) {
							// New storage
							var storagePrice = event.added;
							var $that = $(this);
							var data = {
								name: current.findNewName(current.model.configuration.storages, qi.name),
								type: storagePrice.type.id,
								size: storagePrice.size,
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
									storagePrice.qs = current.saveCallback('storage', updatedCost, data, storagePrice);

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
					data: 'instancePrice.os',
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
					data: 'instancePrice.type.name',
					className: 'hidden-xs hidden-sm'
				}, {
					data: 'instancePrice.instance.name',
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
		 * @param {string} prefix The prefered name (if available) and used as prefix when there is collision.
		 * @param {string} increment The current increment number of collision. Will starts from 1 when not specified.
		 * @param {string} resourcesByName The namespace where key is the unique name.
		 */
		findNewName: function (resources, prefix, increment, resourcesByName) {
			if (typeof resourcesByName === 'undefined') {
				// Build the name based index
				resourcesByName = {};
				for (var resource of resources) {
					resourcesByName[resource.name] = resource;
				}
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
					data: 'type.frequency',
					className: 'truncate hidden-xs',
					render: current.formatStorageFrequency
				}, {
					data: 'type.optimized',
					className: 'truncate hidden-xs',
					render: current.formatStorageOptimized
				}, {
					data: 'type.name',
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
