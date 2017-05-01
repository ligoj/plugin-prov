define(function () {
	var current = {

		/**
		 * Instance table
		 */
		table: null,

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
		 * Return the HTML markup from the OS key name.
		 */
		formatOs: function(os, withText) {
			var cfg = current.os[(os.id || os || 'linux').toLowerCase()] || current.os.linux;
			return '<i class="' + cfg[1] + '"></i>' + (withText ? ' ' + cfg[0] : '');
		},
		
		/**
		 * Associate the storages to the instances
		 */ 
		optimizeModel: function () {
			var instances = current.model.configuration.instances;
			current.model.configuration.instancesById = {};
			for (var i = 0; i < instances.length; i++) {
				var instance = instances[i];
				// Optimize id access
				current.model.configuration.instancesById[instance.id] = instance;
				instance.index = i;
			}
			current.model.configuration.detachedStorages = [];
			current.model.configuration.storagesById = {};
			var storages = current.model.configuration.storages;
			for (i = 0; i < storages.length; i++) {
				var storage = storages[i];
				if (storage.quoteInstance) {
					// Attached storage
					storage.quoteInstance = current.model.configuration.instancesById[storage.quoteInstance];
				} else {
					// Detached storage
					current.model.configuration.detachedStorages.push[storage];
				}
				
				// Optimize id access
				current.model.configuration.storagesById[storage.id] = storage;
				storage.index = i;
			}
		},
		
		/**
		 * Return the query parameter name to use to filter the associated input value.
		 */
		toQueryName: function($item) {
			var id = $item.attr('id');
			return id.indexOf('instance-') === 0 && id.substring('instance-'.length);
		},

		/**
		 * Check there is at least one instance matching to the requirement
		 */
		checkInstance: function() {
			var queries = [];
			_('instance-create').attr('disabled', 'disabled').addClass('disabled');
			
			// Build the query
			$('.instance-query').each(function() {
				var $item = $(this);
				var value = $item.val();
				var queryParam = value && current.toQueryName($item);
				if (queryParam) {
					// Add as query
					queries.push(queryParam + '=' + value);
				}
			});
			
			// Check the availability of this instance for these requirements
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/instance/' + current.model.subscription + '?' + queries.join('&'),
				type: 'GET',
				success: function (price) {
					var instances = [];
					price.instance && instances.push(price.instance);
					price.customInstance && instances.push(price.customInstance);
					if (instances.length) {
						// There is at least one valid instance
						_('instance-create').removeAttr('disabled', 'disabled').removeClass('disabled');
						
						// For now, renders only the lowest priced instance
						var lowest;
						if (instances.length == 1) {
							lowest = instances[0];
						} else if (instances[0].cost < instances[1].cost){
							lowest = instances[0];
						} else {
							lowest = instances[1];
						}
						current.setInstance(lowest);
						// TODO Add warning about custom instance
					} else {
						// Out of bound requirements
						traceLog('Out of bounds for this requirement');
						current.setInstance(null);
					}
				}
			});
		},
		
		/**
		 * Set the current instance price.
		 */
		setInstance: function (lowest) {
			current.model.lowest = lowest || {};
			_('instance').val(current.model.lowest.instance ? lowest.instance.instance.name + ' (' + lowest.cost + ' $/m)' : '');
		},

		initializeForm: function () {

			// Global datatables filter
			_('subscribe-configuration-prov-search').on('keyup', function () {
				current.table && current.table.fnFilter($(this).val());
			});
			
			$('.instance-query').on('change',current.checkInstance);
			$('.instance-query').on('keyup',current.checkInstance);

			// Data tables tools
			_('create').on('click', current.showPopup);
			_('upload-new').on('click', current.showPopupImport);

			// Remove the selected user from the current group
			_('prov-instances').on('click', '.delete', function () {
				var $tr = $(this).closest('tr');
				var qi = current.table.fnGetData($tr[0]);
				$.ajax({
					url: REST_PATH + 'service/prov/instance/' + qi.id,
					type: 'DELETE',
					success: function () {
						// Update the model
						current.deleteInstance(qi.id);

						// Update the UI
						notifyManager.notify(Handlebars.compile(current.$messages['service:prov:deleted-instance'])([qi.id, qi.name]));
						$('.tooltip').remove();
						_('prov-instances').DataTable().row($tr).remove().draw(false);
					}
				});
			});

			// User edition pop-up
			_('popup-prov').on('shown.bs.modal', function () {
				_('instance-name').trigger('focus');
			}).on('submit', function (e) {
				e.preventDefault();
				current.save();
			}).on('show.bs.modal', function (event) {
				var $source = $(event.relatedTarget);
				var $tr = $source.closest('tr');
				var uc = ($tr.length && current.table.fnGetData($tr[0])) || {};
				_('instance-create').removeAttr('disabled', 'disabled').removeClass('disabled');
				current.fillPopup(uc);
			});
			
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
							return REST_PATH + 'service/prov/price-type/' + current.model.subscription;
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

		save: function () {
			// Selected instanced and OS are ignored since embedded in the instance price
			var lowest = current.model.lowest;
			var data = {
				id: current.currentId,
				name: (_('instance-name').val() || ''),
				description: _('instance-description').val() || '',
				cpu: _('instance-cpu').val() || null,
				ram: _('instance-ram').val() || null,
				subscription: current.model.subscription,
				instancePrice: lowest.instance.id
			};
			$.ajax({
				type: data.id ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/instance',
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function (id) {
					if (current.currentId) {
						notifyManager.notify(Handlebars.compile(current.$messages.updated)(data.name));
					} else {
						notifyManager.notify(Handlebars.compile(current.$messages.created)(data.name));
					}
					var instance = current.model.configuration.instancesById[id] || { id: id };
					instance.name = data.name;
					instance.description = data.description;
					instance.cpu = data.cpu;
					instance.ram = data.ram;
					instance.instancePrice = lowest.instance;

					// Update the model and the total cost
					if (data.id) {
						// Update
						current.model.cost += lowest.cost - instance.cost;
						instance.cost = lowest.cost;
						_('prov-instances').DataTable().draw(false);
					} else {
						// Create
						current.model.configuration.instances.push(instance);
						current.model.configuration.instancesById[id] = instance;
						current.model.cost += lowest.cost;
						instance.cost = lowest.cost;
						_('prov-instances').DataTable().row.add(instance).draw(false);
					}
					_('popup-prov').modal('hide');
				}
			});
		},

		/**
		 * Fill the popup with given entity.
		 * @param {Object} uc, the entity corresponding to the quote.
		 */
		fillPopup: function (uc) {
			validationManager.reset(_('popup-prov'));
			current.currentId = uc.id;
			_('instance-name').val(uc.name || '');
			_('instance-description').val(uc.description || '');
			_('instance-cpu').val(uc.cpu || '1');
			_('instance-ram').val(uc.ram || '2048');
			_('instance-os').select2('val', (uc.instancePrice && uc.instancePrice.os) || 'LINUX');
			_('instance-price-type').select2('val', (uc.instancePrice && uc.instancePrice.type) || null);
			current.setInstance(uc.id && {cost: uc.cost, instance: uc.instancePrice});
		},
		
		/**
		 * Update the model and the association with a deleted quote instance
		 */
		deleteInstance: function (id) {
			current.model.configuration.instances[current.model.configuration.instancesById[id].index] = null;
			delete current.model.configuration.instancesById[id];
			for (i = 0; i < current.model.configuration.storages.length; i++) {
				var storage = storages[i];
				if (storage.quoteInstance && storage.quoteInstance.id === id) {
					// Delete the associated storages
					current.model.configuration.storages[i] = null;
					delete current.model.configuration.storagesById[storage.id];
				}
			}
		},

		showPopup: function ($context) {
			_('popup-prov').modal('show', $context);
		},
		showPopupImport: function ($context) {
			_('importPopup-prov').modal('show', $context);
		},

		/**
		 * Initialize the instance datatables from the whole quote
		 */
		initializeDataTable: function () {
			current.table = _('prov-instances').dataTable({
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
		}
	};
	return current;
});
