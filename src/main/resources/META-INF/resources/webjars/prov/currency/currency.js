/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(function () {
	const current = {

		table: null,
		currentId: null,

		initialize: function () {
			current.initializeDataTable();
			// update focus when modal popup is down
			_('popup').on('shown.bs.modal', function () {
				_('name').focus();
			}).on('show.bs.modal', function (event) {
				const $source = $(event.relatedTarget);
				const $tr = $source.closest('tr');
				const uc = ($tr.length && current.table.fnGetData($tr[0])) || {};
				current.currentId = uc.id;
				_('name').val(uc.name || '');
				_('description').val(uc.description || '');
				_('unit').val(uc.unit || '');
				_('rate').val(uc.rate || '1.0');
				if (uc.nbQuotes) {
					_('editing-used-currency').removeClass('hidden');
				} else {
					_('editing-used-currency').addClass('hidden');
				}
				$('.modal-title').text(current.$messages[uc.id ? 'update' : 'create']);
				if (uc.id) {
					_('create').addClass('hidden');
					_('save').removeClass('hidden');
				} else {
					_('create').removeClass('hidden');
					_('save').addClass('hidden');
				}
				validationManager.reset($(this));
			}).on('submit', current.saveOrUpdate);
		},

		/**
		 * Initialize the search UI components
		 */
		initializeDataTable: function () {
			current.table = _('table').dataTable({
				dom: '<"row"<"col-xs-6"B><"col-xs-6"f>r>t<"row"<"col-xs-6"i><"col-xs-6"p>>',
				serverSide: true,
				processing: true,
				searching: true,
				ajax: {
					url: REST_PATH + 'service/prov/currency'
				},
				createdRow: function (nRow) {
					$(nRow).find('.delete').on('click', current.deleteButton);
				},
				columns: [{
					data: 'name'
				}, {
					class: 'hidden-sm',
					data: 'description'
				}, {
					width: '32px',
					data: 'unit',
				}, {
					width: '32px',
					data: 'rate',
					type: 'num'
				}, {
					data: 'nbQuotes',
					type: 'num',
					width: '16px'
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function (_i, _j, data) {
						let result = '';
						// Edit button
						result += '<a data-toggle="modal" data-target="#popup"><i class="fas fa-pencil-alt" data-toggle="tooltip" title="' + current.$messages.update + '"></i></a>';
						if (data.nbQuotes === 0) {
							// Delete button
							result += '<a class="delete"><i class="far fa-trash-alt" data-toggle="tooltip" title="' + current.$messages['delete'] + '"></i></a>';
						}
						return result;
					}
				}],
				buttons: [{
					extend: 'popup',
					className: 'btn-success btn-raised'
				}]
			});
		},

		// Helper function to serialize all the form fields into a JSON string
		formToJSON: function () {
			return JSON.stringify({
				id: current.currentId,
				name: _('name').val(),
				description: _('description').val(),
				unit: _('unit').val(),
				rate: parseFloat(_('rate').val(), 10)
			});
		},

		delete: function () {
			const tr = $(this).parents('tr');
			const uc = current.table.fnGetData(tr[0]);
			bootbox.confirmDelete(function (confirmed) {
				confirmed && current.deleteEntity(uc.id);
			}, uc.name);
		},

		// create button management
		create: function () {
			_('popup').modal('show');
			return false;
		},

		deleteEntity: function (id) {
			$.ajax({
				type: 'DELETE',
				url: REST_PATH + 'service/prov/currency/' + id,
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages.deleted)(id));
					// Refresh the table
					current.table?.api().ajax.reload();
				},
				error: function () {
					notifyManager.notifyDanger(Handlebars.compile(current.$messages.notDeleted)(id));
				}
			});
		},

		saveOrUpdate: function () {
			const data = current.formToJSON();
			$.ajax({
				type: data.id ? 'PUT' : 'POST',
				url: REST_PATH + 'service/prov/currency',
				dataType: 'json',
				contentType: 'application/json',
				data: current.formToJSON(),
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages.created)(data.name));
					_('popup').modal('hide');
					current.table?.api().ajax.reload();
				}
			});
		}
	};
	return current;
});
