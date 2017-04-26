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
		 * Show the members of the given group
		 */
		configure: function (configuration) {
			current.model = configuration;
			current.optimizeModel();
			current.initializeForm();
			current.initializeDataTable();
			_('subscribe-configuration-prov').removeClass('hide');
		},
		
		/**
		 * Associate the storages to the instances
		 */ 
		optimizeModel: function () {
			var instances = current.model.instances;
			current.model.instancesById = {};
			for (var i = 0; i < instances.size(); i++) {
				var instance = instances[i];
				// Optimize id access
				current.model.instancesById[instance.id] = instance;
				instance.index = i;
			}
			current.model.detachedStorages = [];
			current.model.storagesById = {};
			for (i = 0; i < storages.size(); i++) {
				var storage = storages[i];
				if (storage.quoteInstance) {
					// Attached storage
					storage.quoteInstance = current.model.instancesById[storage.quoteInstance];
				} else {
					// Detached storage
					current.model.detachedStorages.push[storage];
				}
				
				// Optimize id access
				current.model.storagesById[storage.id] = storage;
				storage.index = i;
			}
		},

		initializeForm: function () {

			// Global datatables filter
			_('subscribe-configuration-prov-search').on('keyup', function () {
				current.table && current.table.fnFilter($(this).val());
			});

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
		},
		
		/**
		 * Update the model and the association with a deleted quote instance
		 */
		deleteInstance: function (id) {
			current.model.instances[current.model.instancesById[id].index] = null;
			delete current.model.instancesById[id];
			for (i = 0; i < current.model.storages.size(); i++) {
				var storage = storages[i];
				if (storage.quoteInstance && storage.quoteInstance.id === id) {
					// Delete the associated storages
					current.model.storages[i] = null;
					delete current.model.storagesById[storage.id];
				}
			}
		},

		/**
		 * Initialize the instance datatables from the whole quote
		 */
		initializeDataTable: function () {
			current.table = _('members-table').dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				serverSide: false,
				data : current.model.instances,
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
					data: 'instancePrice.type.name'
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
