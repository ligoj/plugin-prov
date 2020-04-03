/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['sparkline'], function () {
	var current = {

		table: null,
		$table: null,

		initialize: function () {
			current.initializeDataTable();
		},

		/**
		 * Initialize the search UI components
		 */
		initializeDataTable: function () {
			current.$table = _('table').on('click', '.import', current.importCatalog).on('click', '.cancel', current.cancelImportCatalog);
			current.table = current.$table.dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				serverSide: false,
				searching: true,
				ajax: {
					url: REST_PATH + 'service/prov/catalog',
					dataSrc: '',
				},
				initComplete: function (settings, catalogs) {
					Object.keys(catalogs).forEach(function (index) {
						if (catalogs[index].status.start && (catalogs[index].status.end || 0) === 0) {
							current.poll(catalogs[index].node.id);
						}
					});
				},
				rowCallback: function (row, data) {
					$(row).attr('data-node', data.node.id);
				},
				columns: [{
					data: 'node.id',
					width: '100px',
					type: 'string'
				}, {
					data: 'node.name',
					type: 'string',
					class: 'hidden-xs',
				}, {
					data: 'status.lastSuccess',
					render: function (date, mode) {
						if (mode === 'sort') {
							return date;
						}
						return formatManager.formatDateTime(date);
					},
					class: 'hidden-xs hidden-sm',
					type: 'num'
				}, {
					data: 'nbQuotes',
					type: 'num',
					width: '16px',
				}, {
					data: 'status.nbLocations',
					type: 'num',
					width: '16px'
				}, {
					data: 'status.nbStorageTypes',
					type: 'num',
					width: '16px'
				}, {
					data: 'status.nbInstanceTypes',
					type: 'num',
					width: '16px'
				}, {
					data: 'status.nbInstancePrices',
					type: 'num',
					width: '32px'
				}, {
					data: 'status.end',
					width: '16px',
					type: 'num',
					render: function (_i, mode, catalog) {
						if (mode === 'sort') {
							return catalog.status.end;
						}
						return '<div class="catalog-status" data-toggle="tooltip" title="' + current.toStatusText(catalog) + '">' + current.toStatus(catalog) + '</div>';
					}
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function (_i, _j, catalog) {
						if (catalog.canImport) {
							if (catalog.status.start && (catalog.status.end || 0) === 0) {
								// Stop button
								return '<a class="cancel"><i class="fas fa-stop text-danger" data-toggle="tooltip" title="' + current.$messages.stop + '"></i></a>';
							}
							// Refresh button
							return `<div class="input-group-btn catalog-import">
								<button class="btn btn-default dropdown-toggle" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
									<span><i class="fas fa-undo-alt fa-flip-horizontal" data-toggle="tooltip" title="${current.$messages.update}"></i></span>
									<span class="caret"></span>
								</button>
								<ul class="dropdown-menu dropdown-menu-right">
									<li data-toggle="tooltip" title="${current.$messages['update-standard-help']}"><a class="import"><i class="fas fa-undo-alt"></i> ${current.$messages['update-standard']}</a></li>
									<li data-toggle="tooltip" title="${current.$messages['update-force-help']}"><a class="import force"><i class="fas fa-sync-alt"></i> ${current.$messages['update-force']}</a></li>
								</ul>
							</div>`
						}
						// No update support
						return '';
					}
				}]
			});
		},

		/**
		 * Return the text corresponding to the catalog status.
		 */
		toStatusText: function (catalog) {
			var status = catalog.status;
			if (catalog.canImport) {
				// Catalog update is supported
				if (status.end && status.start) {
					// Update is finished
					if (status.failed) {
						// Failed
						return Handlebars.compile(current.$messages['status-finished-ko'])(
							[
								formatManager.formatDateTime(status.start),
								status.author,
								formatManager.formatDateTime(status.end),
								current.toStep(status),
								current.toProgress(status),
								status.done,
								status.workload,
								formatManager.formatDateTime(status.lastSuccess)
							]);
					}
					// Success
					return Handlebars.compile(current.$messages['status-finished-ok'])(
						[
							formatManager.formatDateTime(status.end),
							status.author,
							moment.duration(status.end - status.start).humanize()
						]);
				}

				// Not finished
				if (status.start && status.lastSuccess) {
					// Updating
					return Handlebars.compile(current.$messages['status-updating'])(
						[
							current.toProgress(status),
							status.done,
							status.workload,
							formatManager.formatDateTime(status.start),
							status.author,
							current.toStep(status),
							formatManager.formatDateTime(status.lastSuccess)
						]);
				}
				if (status.start) {
					// Started, not finished and first time
					return Handlebars.compile(current.$messages['status-initializing'])(
						[
							current.toProgress(status),
							status.done,
							status.workload,
							formatManager.formatDateTime(status.start),
							status.author,
							current.toStep(status)
						]);
				}
				// Last case : never started
				return current.$messages['status-new'];
			}

			// No catalog update support
			if (status.lastSuccess) {
				// Fixed update date
				return Handlebars.compile(current.$messages['status-not-supported'])(formatManager.formatDateTime(status.lastSuccess));
			}
			// No information at all
			return current.$messages['status-no-version'];
		},

		toStep: function (status) {
			if (status.phase) {
				return status.phase + (status.location ? '@' + status.location : '');
			}
			return status.location ? '@' + status.location : 'init';
		},

		toProgress: function (status) {
			return status.workload ? Math.round(status.done / status.workload * 100) : '0';
		},

		/**
		 * Request a catalog update.
		 */
		importCatalog: function () {
			var catalog = current.table.fnGetData($(this).closest('tr')[0]);
			var id = catalog.node.id;
			var name = catalog.node.name;
			var force = $(this).is('.force');
			$.ajax({
				type: 'POST',
				url: REST_PATH + 'service/prov/catalog/' + id + '?force=' + force,
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages['status-started'])(name));
					catalog.status.end = null;
					catalog.status.start = new Date().getTime();
					current.poll(id);
				}
			});
		},

		/**
		 * Cancel a catalog update.
		 */
		cancelImportCatalog: function () {
			var catalog = current.table.fnGetData($(this).closest('tr')[0]);
			var id = catalog.node.id;
			var name = catalog.node.name;
			$.ajax({
				type: 'DELETE',
				url: REST_PATH + 'service/prov/catalog/' + id,
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages['status-canceled'])(name));
					current.poll(id);
				}
			});
		},

		/**
		 * Redraw a catalog with its new status.
		 */
		updateStatus: function (status, node) {
			current.$table.DataTable().rows(function (index, data) {
				if (data.node.id === node) {
					// Update the status in the model
					data.status = status;
					return true;
				}
				return false;
			}).invalidate().draw();
		},

		/**
		 * Interval identifier for the refresh
		 */
		polling: {},

		/**
		 * Start polling for started catalog import.
		 * @param node The node identifier.
		 */
		poll: function (node) {
			if (current.polling[node]) {
				// Already polling
				return;
			}
			current.synchronizeUploadStep(node);
		},

		unscheduleUploadStep: function (node) {
			current.polling[node] && clearInterval(current.polling[node]);
			delete current.polling[node];
		},

		scheduleUploadStep: function (node) {
			current.polling[node] = setInterval(function () {
				current.synchronizeUploadStep(node);
			}, 5000);
		},

		synchronizeUploadStep: function (node) {
			current.unscheduleUploadStep(node);
			current.polling[node] = '-';
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/catalog/' + node,
				type: 'GET',
				success: function (status) {
					current.isSameTransaction(current.$transaction) {
						current.updateStatus(status, node);
						if (status.end) {
							current.unscheduleUploadStep(node);
						} else {
							// Continue polling for this catalog
							current.scheduleUploadStep(node);
						}
					} else {
						current.unscheduleUploadStep(node);
					}
						
				},
				error: function () {
					current.unscheduleUploadStep(node);
				}
			});
		},

		/**
		 * Generate a pie corresponding to the given catalog status
		 */
		toStatus: function (catalog) {
			var id = catalog.node.id;
			var status = catalog.status;
			if (catalog.canImport) {
				// Catalog update is supported
				if (status.end && status.start) {
					// Update is finished
					if (status.failed) {
						// Failed
						return '<i class="text-danger far fa-circle"></i>';
					}
					// Success
					return '<i class="text-success far fa-circle"></i>';
				}

				// Not finished
				if (status.start) {
					// Updating
					if (catalog.status.workload && catalog.status.done) {
						// Generate a pie
						window.setTimeout(function () {
							_('table').find('[data-node="' + id + '"] .catalog-status').sparkline([catalog.status.done, catalog.status.workload - catalog.status.done], {
								type: 'pie',
								sliceColors: ['#000000', '#FFFFFF'],
								offset: '-90',
								width: '12px',
								height: '12px',
								fillColor: 'white',
								disableTooltips: true,
								borderWidth: '2',
								borderColor: '#000000'
							});
						}, 0);
						return '';
					}
					// Started but not enough information to display a relevant pie chart
					return '<i class="fas fa-circle-notch fa-spin"></i>';
				}
				// Last case : never started
				return '<i class="far far-circle"></i>';
			}

			// No catalog update support
			if (status.lastSuccess) {
				// Fixed update date
				return '<i class="far far-circle"></i>';
			}
			// No information at all
			return '<i class="far fa-dot-circle"></i>';
		}
	};
	return current;
});
