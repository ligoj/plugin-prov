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
			var name = instance ? instance.name : name;
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
				details += current.$messages['service:prov:instance-custom']
			}
			if (instance.ram) {
				details += '<br><i class=\'fa fa-microchip fa-fw\'></i> ';
				details += current.formatRam(instance.ram)
			}

			return '<u class="instance" data-toggle="popover" title="' + name + '" data-content="' + details + '">' + name + '</u>';
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
		formatCost: function (cost, mode) {
			return mode === 'sort' ? cost : formatManager.formatCost(cost, 3, '$');
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
				url: REST_PATH + 'service/prov/' + type + '-lookup/' + current.model.subscription + '?' + queries.join('&'),
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
			_('storage').select2('data',  price || null).val(price ? price.type.name + ' (' + current.formatCost(price.cost) + '/m)' : '');
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
					success: function () {
						current.deleteCallback(type, resource);
					}
				});
			});
			// Delete all items
			$table.on('click', '.delete-all', function () {
				$.ajax({
					type: 'DELETE',
					url: REST_PATH + 'service/prov/' + type + '/reset/' + current.model.subscription,
					success: function () {
						// Update the model
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
			current[type + 'Delete'](resource.id);

			// Update the UI
			notifyManager.notify(Handlebars.compile(current.$messages['service:prov:' + type + '-deleted'])([resource.id, resource.name]));
			$('.tooltip').remove();
			var $table = _('prov-' + type + 's');
			$table.find('tr[data-id="' + resource.id + '"]').each(function () {
				$table.DataTable().row($(this)[0]).remove().draw(false);
			});
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
					url: REST_PATH + 'service/prov/upload/' + current.model.subscription,
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
			$('#prov-terraform-download').attr('href', REST_PATH + 'service/prov/' + current.model.subscription +'/terraform-' + current.model.subscription + '.tf');
			$('#prov-terraform-execute').on('click',current.terraform);
			
			// Related instance of the storage
			_('storage-instance').select2({
				formatSelection: current.formatQuoteInstance,
				formatResult: current.formatQuoteInstance,
				placeholder: current.$messages['service:prov:instance'],
				allowClear: true,
				escapeMarkup: function (m, d) {
					return m;
				},
				data: function(term, re) {
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
				url: REST_PATH + 'service/prov/' + current.model.subscription +'/terraform',
				dataType: 'json',
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages['service:prov:terraform:started'])());
				}
			});
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
						return REST_PATH + 'service/prov/instance-price-type/' + current.model.subscription;
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
		redrawInstance: function(instance) {
			instance = instance && (instance.id || instance);
			if (instance) {
				// The instance is valid
				var $itable = _('prov-instances');
				var $row = $itable.find('tr[data-id="' + instance +'"]');
				if ($row.length) {
					// This has been found and can be drawn
					$itable.DataTable().row($row[0]).invalidate().draw();
				}
			}
		},

		instanceCommitToModel: function (data, model, costContext) {
			model.cpu = parseFloat(data.cpu, 10);
			model.ram = parseInt(data.ram, 10);
			model.instancePrice = costContext.instance;
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
			_('instance-os').select2('data', current.select2IdentityData((model.id && model.instancePrice.os) || 'LINUX'));
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
				success: function (id) {
					current.saveCallback(type, id, data, priceContext);
					$popup.modal('hide');
				}
			});
		},

		/**
		 * Commit to the model the saved data and update the computed cost.
		 * @param {string} type Resource type to save.
		 * @param {string} id Resource identifier, never null.
		 * @param {object} data The original data sent to the back-end.
		 * @param {object} priceContext The price context to commit in addition of data.
		 * @return {object} The updated or created model.
		 */
		saveCallback: function (type, id, data, priceContext) {
			var conf = current.model.configuration;
			if (current.currentId) {
				notifyManager.notify(Handlebars.compile(current.$messages.updated)(data.name));
			} else {
				notifyManager.notify(Handlebars.compile(current.$messages.created)(data.name));
			}

			// Update the model
			var model = conf[type + 'sById'][data.id] || {
				id: id,
				cost: 0
			};
			model.name = data.name;
			model.description = data.description;
			current[type + 'CommitToModel'](data, model, priceContext);

			// Update the model and the total cost
			conf.cost += priceContext.cost - model.cost;
			conf[type + 'Cost'] += priceContext.cost - model.cost;
			model.cost = priceContext.cost;

			// Update the UI
			var $table = _('prov-' + type + 's');
			if (data.id) {
				// Update : Redraw the row
				$table.find('tr[data-id="' + data.id + '"]').each(function () {
					$table.DataTable().row($(this)[0]).invalidate().draw();
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
			current.model.configuration.cost = (Math.round(current.model.configuration.cost * 1000) / 1000) || 0;
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
				if (current.model.configuration.cost) {
					sunburst.init('#sunburst', current.toD3());
					$('#sunburst').removeClass('hidden');
				} else {
					$('#sunburst').addClass('hidden');
				}
			});
		},

		/**
		 * Compute the global resource usage of this quote.
		 */
		usageGlobalRate: function () {
			var conf = current.model.configuration;
			var ramAvailable = 0;
			var ramUsed = 0;
			var cpuAvailable = 0;
			var cpuUsed = 0;
			var storageAvailable = 0;
			var storageUsed = 0;
			for (var i = 0; i < conf.instances.length; i++) {
				var instance = conf.instances[i];
				cpuAvailable += instance.instancePrice.instance.cpu;
				cpuUsed += instance.cpu;
				ramAvailable += instance.instancePrice.instance.ram;
				ramUsed += instance.ram;
			}
			for (i = 0; i < conf.storages.length; i++) {
				var storage = conf.storages[i];
				storageAvailable += Math.max(storage.size, storage.type.minimal);
				storageUsed += storage.size;
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
					conf.cost -= storage.cost;
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
		 */
		instanceDelete: function (id) {
			var conf = current.model.configuration;
			for (var i = conf.instances.length; i-- > 0;) {
				var instance = conf.instances[i];
				if (typeof id === 'undefined' || instance.id === id) {
					conf.instances.splice(i, 1);
					conf.cost -= instance.cost;
					conf.instanceCost -= instance.cost;
					delete conf.instancesById[instance.id];
					// Also delete the related storages
					for (var s = conf.storages.length; s-- > 0;) {
						var storage = conf.storages[s];
						if (storage.quoteInstance && storage.quoteInstance.id === instance.id) {
							// Delete the associated storages
							conf.storages.splice(s, 1);
							conf.cost -= storage.cost;
							conf.storageCost -= storage.cost;
							delete conf.storagesById[storage.id];
						}
					}
					if (id) {
						// Unique item to delete
						break;
					}
				}
			}
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
							url: REST_PATH + 'service/prov/storage-lookup/' + current.model.subscription + '?instance=' + qi.id,
							dataType: 'json',
							data: function (term, page) {
								return {
									size: $.isNumeric(term) ? parseInt(term, 10) : 1, // search term
								};
							},
							results: function (data, page) {
								// Completed the requested identifier
								for (item of data) {
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
								success: function (id) {
									storagePrice.qs = current.saveCallback('storage', id, data, storagePrice);
								}
							});
						} else if (event.removed) {
							// Storage to delete
							var qs = event.removed.qs || event.removed;
							$.ajax({
								type: 'DELETE',
								url: REST_PATH + 'service/prov/storage/' + qs.id,
								success: function () {
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
					// Usage type for an instance
					data: 'instancePrice.type.name',
					className: 'truncate'
				}, {
					data: 'instancePrice.instance.name',
					width: '64px',
					className: 'truncate',
					render: current.formatInstance
				}, {
					data: null,
					render: current.formatQiStorages
				}, {
					data: 'cost',
					className: 'truncate',
					width: '64px',
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
				for (resource of resources) {
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
					data: 'size',
					width: '64px',
					className: 'truncate',
					render: current.formatStorage
				}, {
					data: 'type.frequency',
					width: '80px',
					className: 'truncate',
					render: current.formatStorageFrequency
				}, {
					data: 'type.optimized',
					width: '80px',
					className: 'truncate',
					render: current.formatStorageOptimized
				}, {
					data: 'type.name',
					className: 'truncate',
					width: '80px'
				}, {
					data: 'quoteInstance.name',
					className: 'truncate'
				}, {
					data: 'cost',
					className: 'truncate',
					width: '64px',
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
