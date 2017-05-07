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
				[
					'name', quote.name
				],
				[
					'service:prov:resources', current.$super('icon')('microchip', 'service:prov:total-ram') + current.formatRam(quote.totalRam) + ', ' + current.$super('icon')('bolt', 'service:prov:total-cpu') + quote.totalCpu + ' CPU, ' + current.$super('icon')('database', 'service:prov:total-storage') + (current.formatStorage(quote.totalStorage) || '0')
				],
				[
					'service:prov:nb-instances', current.$super('icon')('server', 'service:prov:nb-instances') + quote.nbInstances
				]
			], 1);
		},

		/**
		 * Format the cost.
		 */
		formatCost: function (cost) {
			return formatManager.formatCost(cost, 3, '$');
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
		formatStorage: function (sizeGB) {
			return formatManager.formatSize(sizeGB * 1024 * 1024 * 1024, 3);
		},

		/**
		 * OS key to markup/label mapping.
		 */
		os: {
			'linux': [
				'Linux', 'fa fa-linux'
			],
			'windows': [
				'Windows', 'fa fa-windows'
			],
			'suse': [
				'SUSE', 'icon-suse'
			],
			'rhe': ['Red Hat Enterprise', 'icon-redhat']
		},

		/**
		 * Storage type key to markup/label mapping.
		 */
		storageFrequency: {
			'cold': 'fa fa-snowflake-o text-info',
			'hot': 'fa fa-thermometer-full text-danger',
			'archive': 'fa fa-archive'
		},

		/**
		 * Storage optimized key to markup/label mapping.
		 */
		storageOptimized: {
			'throughput': 'fa fa-database',
			'iops': 'fa fa-flash'
		},

		/**
		 * Return the HTML markup from the OS key name.
		 */
		formatOs: function (os, withText) {
			var cfg = current.os[(os.id || os || 'linux').toLowerCase()] || current.os.linux;
			return '<i class="' + cfg[1] + '" data-toggle="tooltip" title="' + cfg[0] + '"></i>' + (withText ? ' ' + cfg[0] : '');
		},

		/**
		 * Return the HTML markup from the storage frequency.
		 */
		formatStorageFrequency: function (frequency, withText) {
			var id = (frequency.id || frequency || 'cold').toLowerCase();
			var clazz = current.storageFrequency[id];
			var text = current.$messages['service:prov:storage-frequency-' + id];
			return '<i class="' + clazz + '" data-toggle="tooltip" title="' + text + '"></i>' + (withText ? ' ' + text : '');
		},

		/**
		 * Return the HTML markup from the storage optimized.
		 */
		formatStorageOptimized: function (optimized, withText) {
			if (optimized) {
				var id = (optimized.id || optimized || 'throughput').toLowerCase();
				var clazz = current.storageOptimized[id];
				var text = current.$messages['service:prov:storage-optimized-' + id];
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
			for (var i = 0; i < instances.length; i++) {
				var instance = instances[i];
				// Optimize id access
				conf.instancesById[instance.id] = instance;
			}
			conf.detachedStorages = [];
			conf.storagesById = {};
			var storages = conf.storages;
			for (i = 0; i < storages.length; i++) {
				var storage = storages[i];
				if (storage.quoteInstance) {
					// Attached storage
					storage.quoteInstance = conf.instancesById[storage.quoteInstance];
				} else {
					// Detached storage
					conf.detachedStorages.push[storage];
				}

				// Optimize id access
				conf.storagesById[storage.id] = storage;
			}
			current.updateUiCost();
		},

		/**
		 * Return the query parameter name to use to filter the associated input value.
		 */
		toQueryName: function (type, $item) {
			var id = $item.attr('id');
			return id.indexOf(type + '-') === 0 && id.substring((type + '-').length);
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
		 * Check there is at least one instance matching to the requirement
		 * @param $form Optional jQuery form holding the resources to filter
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
				var value = $item.val();
				var queryParam = value && current.toQueryName(type, $item);
				if (queryParam) {
					// Add as query
					queries.push(queryParam + '=' + current.cleanData(value));
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
			return price;
		},

		/**
		 * Set the current storage price.
		 */
		storageSetUiPrice: function (price) {
			current.model.storagePrice = price;
			_('storage').val(price ? price.type.name + ' (' + current.formatCost(price.cost) + '/m)' : '');
		},

		/**
		 * Set the current instance price.
		 */
		instanceSetUiPrice: function (price) {
			current.model.instancePrice = price || {};
			if (current.model.instancePrice.instance) {
				_('instance').val(price.instance.instance.name + ' (' + current.formatCost(price.cost) + '/m)');
				_('instance-price-type').select2('data', price.instance.type).val(price.instance.type.id);
			} else {
				_('instance').val('');

				// Find now the best instance from the default inputs
				$.proxy(current.checkResource, _('popup-prov-instance'))();
			}
		},

		/**
		 * Initialize data tables and popup event : delete and details
		 */
		initializeDataTableEvents: function (type) {
			// Delete the selected instance from the quote
			var $table = _('prov-' + type + 's');
			var dataTable = current[type + 'NewTable']();
			$table.on('click', '.delete', function () {
				var $tr = $(this).closest('tr');
				var qi = dataTable.fnGetData($tr[0]);
				$.ajax({
					url: REST_PATH + 'service/prov/' + type + '/' + qi.id,
					type: 'DELETE',
					success: function () {
						// Update the model
						current[type + 'Delete'](qi.id);

						// Update the UI
						notifyManager.notify(Handlebars.compile(current.$messages['service:prov:' + type + '-deleted'])([qi.id, qi.name]));
						$('.tooltip').remove();
						$table.DataTable().row($tr).remove().draw(false);
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

		initializeUpload: function () {
			var $popup = _('popup-prov-instance-import');
			$popup.on('shown.bs.modal', function () {
				_('csv-file').trigger('focus');
			}).on('show.bs.modal', function () {
				$('.import-summary').addClass('hidden');
			}).on('submit', function (e) {
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

			$('.resource-query').on('change', current.checkResource);
			$('.resource-query').on('keyup', current.checkResource);
			current.initializeDataTableEvents('instance');
			current.initializeDataTableEvents('storage');

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

			_('instance-price-type').select2(current.instancePriceTypeSelect2());
			_('instance-price-type-upload').select2(current.instancePriceTypeSelect2(true));
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
		},

		instanceCommitToModel: function (data, model, costContext) {
			model.cpu = parseFloat(data.cpu, 10);
			model.ram = parseInt(data.ram, 10);
			model.instancePrice = costContext.instance;
		},

		storageUiToData: function (data) {
			data.size = current.cleanData(_('storage-size').val());
			data.type = current.model.storagePrice.type.id;
			return current.model.storagePrice;
		},

		instanceUiToData: function (data) {
			data.cpu = current.cleanData(_('instance-cpu').val()) || null;
			data.ram = current.cleanData(_('instance-ram').val()) || null;
			data.instancePrice = current.model.instancePrice.instance.id;
			return current.model.instancePrice;
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
			_(type + '-name').val(model.name || '');
			_(type + '-description').val(model.description || '');
			current[type + 'ToUi'](model);
		},

		/**
		 * Fill the instance popup with given entity.
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		instanceToUi: function (model) {
			_('instance-cpu').val(model.cpu || '1');
			_('instance-ram').val(model.ram || '2048');
			_('instance-os').select2('data', current.select2IdentityData((model.id && model.instancePrice.os) || 'LINUX'));
			_('instance-price-type').select2('data', (model.id && model.instancePrice.type) || null);
			current.instanceSetUiPrice(model.id && {
				cost: model.cost,
				instance: model.instancePrice
			});
		},

		/**
		 * Fill the storage popup with given entity.
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		storageToUi: function (model) {
			_('storage-size').val(model.size || '10');
			_('storage-frequency').select2('data', current.select2IdentityData((model.storagePrice && model.storagePrice.frequency) || 'COLD'));
			_('storage-optimized').select2('data', current.select2IdentityData(model.storagePrice && model.storagePrice.optimized));
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

		save: function (type) {
			var $popup = _('popup-prov-' + type);

			// Build the playload for business service
			var data = {
				id: current.currentId,
				name: (_(type + '-name').val() || ''),
				description: _(type + '-description').val() || '',
				subscription: current.model.subscription
			};
			// Backup the stored context
			var costContext = current[type + 'UiToData'](data);
			var conf = current.model.configuration;

			$.ajax({
				type: data.id ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/' + type,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (id) {
					if (current.currentId) {
						notifyManager.notify(Handlebars.compile(current.$messages.updated)(data.name));
					} else {
						notifyManager.notify(Handlebars.compile(current.$messages.created)(data.name));
					}

					// Update the model
					var model = conf[type + 'sById'][data.id] || {
						id: data.id
					};
					model.name = data.name;
					model.description = data.description;
					current[type + 'CommitToModel'](data, model, costContext);

					// Update the model and the total cost
					var $table = _('prov-' + type + 's');
					if (data.id) {
						// Update
						conf.cost += costContext.cost - model.cost;
						model.cost = costContext.cost;
						$table.DataTable().row($table.find('tbody>tr[data-id="' + data.id + '"]').first()[0]).invalidate().draw();
					} else {
						// Create
						conf[type + 's'].push(model);
						conf[type + 'sById'][id] = model;
						conf.cost += costContext.cost;
						model.cost = costContext.cost;
						$table.DataTable().row.add(model).draw(false);
					}
					current.updateUiCost();
					$popup.modal('hide');
				}
			});
		},

		/**
		 * Update the total cost of the quote.
		 */
		updateUiCost: function () {
			$('.cost').text(current.formatCost(current.model.configuration.cost) || '-');
			$('.nav-pills [href="#tab-instance"] > .badge').text(current.model.configuration.instances.length || '');
			$('.nav-pills [href="#tab-storage"] > .badge').text(current.model.configuration.storages.length || '');

			// Update the total resource usage
			require(['d3'], function (d3) {
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
				if (d3.select("#gauge-global").on("valueChanged") && weight) {
					$('#gauge-global').removeClass('hidden');
					// Weight average of average...
					d3.select("#gauge-global").on("valueChanged")(Math.floor(weightUsage * 100 / weight));
				} else {
					$('#gauge-global').addClass('hidden');
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
		 */
		storageDelete: function (id) {
			var conf = current.model.configuration;
			for (var i = 0; i < conf.storages.length; i++) {
				var storage = conf.storages[i];
				if (storage.id === id) {
					conf.storages.splice(i, 1);
					conf.cost -= storage.cost;
					break;
				}
			}
		},

		/**
		 * Update the model and the association with a deleted quote instance
		 */
		instanceDelete: function (id) {
			var conf = current.model.configuration;
			for (var i = 0; i < conf.instances.length; i++) {
				var instance = conf.instances[i];
				if (instance.id === id) {
					conf.instances.splice(i, 1);
					conf.cost -= instance.cost;
					delete conf.instancesById[instance.id];

					// Also delete the related storages
					for (var s = conf.storages.length; s-- > 0;) {
						var storage = conf.storages[s];
						if (storage.quoteInstance && storage.quoteInstance.id === id) {
							// Delete the associated storages
							conf.storages.splice(s, 1);
							conf.cost -= storage.cost;
							delete conf.storagesById[storage.id];
						}
					}
					break;
				}
			}
		},

		/**
		 * Initialize D3 graphics with default empty data.
		 */
		initializeD3: function () {
			require([
				'd3', '../main/service/prov/lib/liquidFillGauge'
			], function (d3) {
				d3.select("#gauge-global").call(d3.liquidfillgauge, 1, {
					textColor: "#FF4444",
					textVertPosition: 0.2,
					waveAnimateTime: 1200,
					waveHeight: 0.9,
					backgroundColor: "#e0e0e0"
				});

				// First render
				current.updateUiCost();
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
				columns: [{
					data: 'name',
					className: 'truncate'
				}, {
					data: 'instancePrice.os',
					render: function (os) {
						return current.formatOs(os, true);
					}
				}, {
					data: 'cpu'
				}, {
					data: 'ram',
					render: current.formatRam
				}, {
					// Usage type for an instance
					data: 'instancePrice.type.name'
				}, {
					data: 'storage',
					render: current.formatStorage
				}, {
					data: 'cost',
					render: current.formatCost
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function () {
						return '<a class="update" data-toggle="modal" data-target="#popup-prov-instance"><i class="fa fa-pencil" data-toggle="tooltip" title="' + current.$messages.update + '"></i></a>' + '<a class="delete"><i class="fa fa-times" data-toggle="tooltip" title="' + current.$messages.delete + '"></i></a>';
					}
				}]
			});
			return current.instanceTable;
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
					render: current.formatStorage
				}, {
					data: 'type.frequency',
					render: current.formatStorageFrequency
				}, {
					data: 'type.optimized',
					render: current.formatStorageOptimized
				}, {
					data: 'type.name'
				}, {
					data: 'cost',
					render: current.formatCost
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function () {
						return '<a class="update" data-toggle="modal" data-target="#popup-prov-storage"><i class="fa fa-pencil" data-toggle="tooltip" title="' + current.$messages.update + '"></i></a>' + '<a class="delete"><i class="fa fa-times" data-toggle="tooltip" title="' + current.$messages.delete + '"></i></a>';
					}
				}]
			});
			return current.storageTable;
		}
	};
	return current;
});
