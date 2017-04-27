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
			var result = current.$super('renderServicelink')('server', '#/home/project/' + subscription.project + '/subscription/' + subscription.id, 'service:prov:manage');

			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:prov:help');
			return result;
		},

		/**
		 * Display the details of the quote
		 */
		renderDetailsFeatures: function (subscription) {
			if (subscription.data.quote) {
				return '<span data-toggle="tooltip" title="' + current.$messages['service:prov:cost-title'] + '" class="label label-default">' + subscription.data.quote.cost + '</span>';
			}
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
		
		toQueryName: function($item) {
			var id = $item.attr('id');
			if (id.indexOf('instance-') === 0) {
				return id.substring('instance-'.length);
			}
			return id;
		},

		initializeForm: function () {

			// Global datatables filter
			_('subscribe-configuration-prov-search').on('keyup', function () {
				current.table && current.table.fnFilter($(this).val());
			});
			
			$('.instance-query').on('change', function() {
				var queries = [];
				_('confirmCreate').attr('disabled', 'disabled');
				$('.instance-query').each(function() {
					var $item = $(this);
					var value = $item.val();
					if (value) {
						// Add as query
						queries.push(current.toQueryName($item) + '=' + value);
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
							_('confirmCreate').removeAttr('disabled', 'disabled');
						} else {
							// Out of bound requirements
							traceLog('out of bounds for this requirement');
						}
					}
				});
				
			});

			// Data tables tools
			_('create').on('click', current.showPopup);
			_('upload-new').on('click', current.showPopupImport);

			// Remove the selected user from the current group
			_('prov-instances').on('click', '.delete', function () {
				var qi = current.table.fnGetData($(this).closest('tr')[0]);
				$.ajax({
					dataType: 'json',
					url: REST_PATH + 'service/prov/' + qi.id,
					type: 'DELETE',
					success: function () {
						notifyManager.notify(Handlebars.compile(current.$messages['service:prov:deleted-instance'])([qi.id, qi.name]));
						current.deleteInstance(qi.id);
						current.table && current.table.api().ajax.reload();
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
				_('confirmCreate').removeAttr('disabled', 'disabled');
				current.fillPopup(uc);
			});
			
			_('instance-os').select2({
				escapeMarkup: function (m) { return m; },
				data:[{id:'LINUX',text:'<i class="fa fa-linux"></i> LINUX'},{id:'WINDOWS',text:'<i class="fa fa-windows"></i> WINDOWS'},{id:'RHE',text:'<i class="icon-redhat"></i> Red Hat Enterprise'}]
			});
			_('instance-price-type').select2({
				minimumInputLength: 0,
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
								id: (idProperty && this[idProperty]) || this.id || this,
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
			debugger;
			return priceType.name;
		},

		formToObject: function () {
			return {
				name: (_('instance-name').val() || ''),
				description: _('instance-description').val() || '',
				cpu: _('instance-cpu').val() || null,
				ram: _('instance-ram').val() || null,
				instancePrice: _('instance-price').val()
			};
		},

		save: function () {
			var data = current.formToObject();
			$.ajax({
				type: current.currentId ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/instance',
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function () {
					if (current.currentId) {
						notifyManager.notify(Handlebars.compile(current.$messages.updated)(data.name));
					} else {
						notifyManager.notify(Handlebars.compile(current.$messages.created)(data.name));
					}
					current.table && current.table.api().ajax.reload();
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
			_('instance-ram').val(uc.ram || '2');
			_('instance-os').select2('val', (uc.instancePrice && uc.instancePrice.os) || null);
			_('instance').select2('val', (uc.instancePrice && uc.instancePrice.instance) || null);
			_('instance-usage').select2('val', (uc.instancePrice && uc.instancePrice.type) || null);
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
					data: 'instancePrice.os'
				}, {
					data: 'cpu'
				}, {
					data: 'ram'
				}, {
					// Usage type for an instance
					data: 'instancePrice.type.name'
				}, {
					data: 'storage'
				}, {
					data: 'cost'
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
