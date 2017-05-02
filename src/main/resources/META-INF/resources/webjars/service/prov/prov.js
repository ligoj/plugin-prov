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
			current.optimizeModel();
			current.initializeForm();
			current.initializeDataTable();
			_('subscribe-configuration-prov').removeClass('hide');
			$('.provider').text(current.model.node.name);
			_('name-prov').val(current.model.configuration.name);
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
					'service:prov:resources',
					  	current.$super('icon')('microchip', 'service:prov:total-ram') + current.formatMemory(quote.totalRam) + ', '
					  + current.$super('icon')('bolt', 'service:prov:total-cpu') + quote.totalCpu + ' CPU, '
					  + current.$super('icon')('database', 'service:prov:total-storage') + (current.formatStorage(quote.totalStorage) || '0')
				],
				[
					'service:prov:nb-instances', current.$super('icon')('server', 'service:prov:nb-instances') + quote.nbInstances
				]
			], 1);
		},
		
		/**
		 * Format the cost.
		 */
		formatCost: function(cost) {
			return formatManager.formatCost(cost, 3, '$');
		},
		
		/**
		 * Format the memory size.
		 */
		formatMemory: function(sizeMB) {
			return formatManager.formatSize(sizeMB * 1024 * 1024, 3);
		},
		
		/**
		 * Format the storage size.
		 */
		formatStorage: function(sizeGB) {
			return formatManager.formatSize(sizeGB * 1024 * 1024 * 1024, 3);
		},
		
		/**
		 * OS key to markup/label mapping.
		 */
		os: {
			'linux' : ['Linux', 'fa fa-linux'],
			'windows' : ['Windows', 'fa fa-windows'],
			'suse' : ['Windows', 'icon-suse'],
			'rhe' : ['Red Hat Enterprise', 'icon-redhat']
		},
		
		/**
		 * Storage type key to markup/label mapping.
		 */
		storageFrequency: {
			'cold' : 'fa fa-snowflake-o text-info',
			'hot' : 'fa fa-thermometer-full text-danger',
			'archive' : 'fa fa-archive'
		},
		
		/**
		 * Storage optimized key to markup/label mapping.
		 */
		storageOptimized: {
			'throughput' : 'fa fa-database',
			'iops' : 'fa fa-flash'
		},

		/**
		 * Return the HTML markup from the OS key name.
		 */
		formatOs: function(os, withText) {
			var cfg = current.os[(os.id || os || 'linux').toLowerCase()] || current.os.linux;
			return '<i class="' + cfg[1] + '"></i>' + (withText ? ' ' + cfg[0] : '');
		},
		
		/**
		 * Return the HTML markup from the storage frequency.
		 */
		formatStorageFrequency: function(frequency, withText) {
			var id = (frequency.id || frequency || 'cold').toLowerCase();
			var clazz = current.storageFrequency[id];
			return '<i class="' + clazz + '"></i>' + (withText ? ' ' + current.$messages['service:prov:storage-frequency-' + id] : '');
		},

		/**
		 * Return the HTML markup from the storage optimized.
		 */
		formatStorageOptimized: function(optimized, withText) {
			var id = (optimized.id || optimized || 'throughput').toLowerCase();
			var clazz = current.storageOptimized[id];
			return '<i class="' + clazz + '"></i>' + (withText ? ' ' + current.$messages['service:prov:storage-optimized-' + id] : '');
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
		toQueryName: function(type, $item) {
			var id = $item.attr('id');
			return id.indexOf(type + '-') === 0 && id.substring((type + '-').length);
		},

		/**
		 * Check there is at least one instance matching to the requirement
		 */
		checkResource: function() {
			var queries = [];
			var type = $(this).closest('[data-prov-type]').data('prov-type');
			
			// Disable the submit while checking the resource
			var $create = _(type + '-create').attr('disabled', 'disabled').addClass('disabled');
			
			// Build the query
			$('.resource-query').each(function() {
				var $item = $(this);
				var value = $item.val();
				var queryParam = value && current.toQueryName(type, $item);
				if (queryParam) {
					// Add as query
					queries.push(queryParam + '=' + value);
				}
			});
			
			// Check the availability of this instance for these requirements
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/' + type +'-lookup/' + current.model.subscription + '?' + queries.join('&'),
				type: 'GET',
				success: function (price) {
					var callbackUi = current[type + 'SetUiPrice'];
					var valid = current[type + 'ValidatePrice'](price);
					if (valid) {
						$create.removeAttr('disabled', 'disabled').removeClass('disabled');
					}
					callbackUi(valid);
				}
			});
		},
		
		instanceValidatePrice : function (price) {
			var instances = [];
			price.instance && instances.push(price.instance);
			price.customInstance && instances.push(price.customInstance);
			if (instances.length) {
				// There is at least one valid instance
				// For now, renders only the lowest priced instance
				var lowest;
				if (instances.length == 1) {
					lowest = instances[0];
				} else if (instances[0].cost < instances[1].cost){
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

		storageValidatePrice : function (price) {
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
			_('instance').val(current.model.instancePrice.instance ? price.instance.instance.name + ' (' + current.formatCost(price.cost) + '/m)' : '');
		},
		
		/**
		 * Initialize data tables and popup event : delete and details
		 */
		initializeDataTableEvents : function (type) {
			// Delete the selected instance from the quote
			var $table = _('prov-' + type + 's');
			$table.on('click', '.delete', function () {
				var $tr = $(this).closest('tr');
				var qi = current.instanceTable.fnGetData($tr[0]);
				$.ajax({
					url: REST_PATH + 'service/prov/' + type +'/' + qi.id,
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

			// Instance edition pop-up
			var $popup = _('popup-prov-' + type);
			$popup.on('shown.bs.modal', function () {
				_(type + '-name').trigger('focus');
			}).on('submit', function (e) {
				e.preventDefault();
				current.save(type);
			}).on('show.bs.modal', function (event) {
				var $source = $(event.relatedTarget);
				var $tr = $source.closest('tr');
				var model = ($tr.length && current.instanceTable.fnGetData($tr[0])) || {};
				_(type + '-create').removeAttr('disabled', 'disabled').removeClass('disabled');
				current.toUi(type, model);
			});
		},

		initializeForm: function () {
			// Global datatables filter
			$('.subscribe-configuration-prov-search').on('keyup', function () {
				var type = $(this).closest('[data-prov-type]').data('prov-type');
				var table = current[type + 'Table'];
				table && table.fnFilter($(this).val());
			});
			
			$('.resource-query').on('change',current.checkResource);
			$('.resource-query').on('keyup',current.checkResource);
			_('instance-new').on('click', current.showInstancePopup);
			_('storage-new').on('click', current.showStoragePopup);
			_('upload-new').on('click', current.showInstancePopupImport);
			current.initializeDataTableEvents('instance');
			current.initializeDataTableEvents('storage');

			_('instance-os').select2({
				formatSelection: current.formatOs,
				formatResult: current.formatOs,
				escapeMarkup: function (m, d) { 
					return m;
				},
				data:[
					{id:'LINUX', text:'LINUX'},
					{id:'WINDOWS',text:'WINDOWS'},
					{id:'RHE',text:'RHE'}
				]
			});
			
			_('storage-optimized').select2({
				placeholder: current.$messages['service:prov:storage-optimized-help'],
				allowClear: true,
				formatSelection: current.formatStorageOptimized,
				formatResult: current.formatStorageOptimized,
				escapeMarkup: function (m, d) { 
					return m;
				},
				data:[
					{id:'THROUGHPUT', text:'THROUGHPUT'},
					{id:'IOPS',text:'IOPS'}
				]
			});
			
			_('storage-frequency').select2({
				formatSelection: current.formatStorageFrequency,
				formatResult: current.formatStorageFrequency,
				escapeMarkup: function (m, d) { 
					return m;
				},
				data:[
					{id:'COLD', text:'COLD'},
					{id:'HOT',text:'HOT'},
					{id:'ARCHIVE',text:'ARCHIVE'}
				]
			});
			
			_('instance-price-type').select2({
				initSelection: function (element, callback) {
					callback(element.val() && {
						id: element.val(),
						text: current.priceTypeToText(element.val())
					});
				},
				formatSelection: current.priceTypeToText,
				formatResult: current.priceTypeToText,
				escapeMarkup: function (m) {
					return m;
				},
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
			});
		},

		priceTypeToText: function(priceType) {
			return priceType.name || priceType.text || priceType;
		},
		
		storageCommitToModel: function(data, model, costContext) {
			model.size = data.size;
			model.type = costContext.type;
		},

		instanceCommitToModel: function(data, model, costContext) {
			model.cpu = data.cpu;
			model.ram = data.ram;
			model.instancePrice = costContext.instance;
		},

		storageUiToData: function(data) {
			data.size = _('storage-size').val() || null;
			data.type = current.model.storagePrice.type.id;
			return current.model.storagePrice;
		},

		instanceUiToData: function(data) {
			data.cpu = _('instance-cpu').val() || null;
			data.ram = _('instance-ram').val() || null;
			data.instancePrice = current.model.instancePrice.instance.id;
			return current.model.instancePrice;
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
			_('instance-os').select2('val', (model.instancePrice && model.instancePrice.os) || 'LINUX');
			_('instance-price-type').select2('val', (model.instancePrice && model.instancePrice.type) || null);
			current.instanceSetUiPrice(model.id && {cost: model.cost, instance: model.instancePrice});
		},

		/**
		 * Fill the storage popup with given entity.
		 * @param {Object} model, the entity corresponding to the quote.
		 */
		storageToUi: function (model) {
			_('storage-size').val(model.size || '10');
			_('storage-frequency').select2('val', (model.storagePrice && model.storagePrice.frequency) || 'COLD');
			_('storage-optimized').select2('val', (model.storagePrice && model.storagePrice.optimized) || null);
			current.storageSetUiPrice(model.id && model.storagePrice);
		},

		save: function (type) {
			var $popup = _('popup-prov-' + type);
			
			// Build the playload for business service
			var data = {
				id: current.currentId,
				name: (_(type + '-name').val() || ''),
				description: _(type + '-description').val() || '',
				subscription: current.model.subscription,
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
					var model = conf[type + 'sById'][id] || { id: id };
					model.name = data.name;
					model.description = data.description;
					current[type + 'CommitToModel'](data, model, costContext);

					// Update the model and the total cost
					var $table = _('prov-' + type + 's');
					if (data.id) {
						// Update
						conf.cost += costContext.cost - model.cost;
						model.cost = costContext.cost;
						$table.DataTable().draw(false);
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
			$('.cost').text(current.formatCost(current.model.configuration.cost));
			$('.nav-pills [href="#tab-instance"] > .badge').text(current.model.configuration.instances.length || '');
			$('.nav-pills [href="#tab-storage"] > .badge').text(current.model.configuration.storages.length || '');
		},
		
		/**
		 * Update the model a deleted quote storage
		 */
		storageDelete: function (id) {
			var conf = current.model.configuration;
			for (var i = 0; i < conf.storages.length; i++) {
				var storage = conf.storages[i];
				if (storage.id === id) {
					conf.storages.slice(i, 1);
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
					for (var s = conf.storages.length; s--> 0;) {
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

		showInstancePopup: function ($context) {
			_('popup-prov-instance').modal('show', $context);
		},
		showStoragePopup: function ($context) {
			_('popup-prov-storage').modal('show', $context);
		},
		showInstancePopupImport: function ($context) {
			_('importPopup-prov').modal('show', $context);
		},

		/**
		 * Initialize the instance datatables from the whole quote
		 */
		initializeDataTable: function () {
			current.instanceTable = _('prov-instances').dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				data : current.model.configuration.instances,
				destroy: true,
				searching: true,
				columns: [{
					data: 'name',
					className: 'truncate'
				}, {
					data: 'instancePrice.os',
					render: function (os) {
						return current.formatOs(os, false);
					}
				}, {
					data: 'cpu'
				}, {
					data: 'ram',
					render: current.formatMemory
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
					width: '16px',
					orderable: false,
					render: function () {
						return '<a class="delete"><i class="fa fa-times" data-toggle="tooltip" title="' + current.$messages.delete + '"></i></a>';
					}
				}]
			});
			current.storageTable = _('prov-storages').dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				data : current.model.configuration.storages,
				destroy: true,
				searching: true,
				columns: [{
					data: 'name',
					className: 'truncate'
				}, {
					data: 'size',
					render: current.formatStorage
				}, {
					data: 'type.frequency'
				}, {
					data: 'type.optimized'
				}, {
					data: 'type.name'
				}, {
					data: 'cost',
					render: current.formatCost
				}, {
					data: null,
					width: '16px',
					orderable: false,
					render: function () {
						return '<a class="delete"><i class="fa fa-times" data-toggle="tooltip" title="' + current.$messages.delete + '"></i></a>';
					}
				}]
			});
		}
	};
	return current;
});
