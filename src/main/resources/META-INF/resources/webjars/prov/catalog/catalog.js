/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['sparkline'], function () {

	/**
	 * Generic Ajax Select2 configuration.
	 * @param path {string|function} Either a string, either a function returning a relative path suffix to 'service/prov/$subscription/$path'
	 */
	function genericSelect2(placeholder, formatSelection, path, formatResult, customComparator, matcherFn) {
		const pageSize = 15;
		return {
			formatSelection: formatSelection,
			formatResult: formatResult || formatSelection,
			escapeMarkup: m => m,
			dropdownAutoWidth: true,
			allowClear: placeholder !== false,
			placeholder: placeholder ? placeholder : null,
			formatSearching: () => current.$messages.loading,
			ajax: {
				url: () => REST_PATH + 'service/prov/location/' + (typeof path === 'function' ? path() : path),
				dataType: 'json',
				data: (term, page, opt) => typeof pageSize === 'undefined' ? { term: term, page: page, opt: opt } : {
					'search[value]': term, // search term
					'q': term, // search term
					'rows': pageSize,
					'page': page,
					'start': (page - 1) * pageSize,
					'filters': '{}',
					'sidx': 'name',
					'length': pageSize,
					'columns[0][name]': 'name',
					'order[0][column]': 0,
					'order[0][dir]': 'asc',
					'sortd': 'asc'
				},
				transport: typeof customComparator === 'function' ? function (options) {
					const $this = this;
					if ($this.cachedData) {
						filterSelect2Result($this.cachedData, options, customComparator, matcherFn);
					} else {
						// No yet cached data
						let success = options.success;
						options.success = function (data) {
							$this.cachedData = data
							// Restore the original success function
							options.success = success;
							filterSelect2Result(data, options, customComparator, matcherFn);
						}
						$.fn.select2.ajaxDefaults.transport(options);
					}
				} : $.fn.select2.ajaxDefaults.transport,
				results: function (data, page) {
					const result = ((typeof data.data === 'undefined') ? (data.results || data) : data.data).map(item => {
						if (typeof item === 'string') {
							item = {
								id: item,
								text: formatSelection(item)
							};
						} else {
							item.text = formatSelection(item);
						}
						return item;
					});
					return {
						more: typeof data.more === 'undefined' ? data.recordsFiltered > page * pageSize : data.more,
						results: result
					};
				}
			}
		};
	}

	function filterSelect2Result(items, options, customComparator, customMatcher) {
		// No new Ajax query is needed
		let filtered = [];
		customMatcher = customMatcher || matcher;
		const pageSize = options.data.length;
		let i = 0;
		for (; i < items.length && filtered.length < pageSize * options.data.page; i++) {
			let datum = items[i];
			let term = options.data.q;
			if (customMatcher(term, datum)) {
				filtered.push(datum);
			}
		}
		filtered = filtered.sort(customComparator);
		options.success({
			results: filtered.slice((options.data.page - 1) * pageSize, options.data.page * pageSize),
			more: i < items.length
		});
	}

	/**
     * Location html renderer.
     */
	function locationToHtml(location, map, short) {
		const id = location.name;
		const subRegion = location.subRegion && (current.$messages[location.subRegion] || location.subRegion);
		const m49 = location.countryM49 && current.$messages.m49[parseInt(location.countryM49, 10)];
		const placement = subRegion || (location.placement && current.$messages[location.placement]) || location.placement;
		let html = map === true ? locationMap(location) : '';
		if (location.countryA2) {
			const a2 = (location.countryA2 === 'UK' ? 'GB' : location.countryA2).toLowerCase();
			let tooltip = m49 || id;
			const img = `<img class="flag-icon prov-location-flag" src="main/service/prov/flag-icon-css/flags/4x3/${a2}.svg" alt=""`;
			if (short === true) {
				// Only flag
				tooltip += (placement && placement !== html) ? '<br>Placement: ' + placement : '';
				tooltip += '<br>Id: ' + id;
				return `<u class="details-help" data-toggle="popover" data-content="${tooltip}" title="${location.name}">${img}></u>`;
			}
			html += img + ' title="' + location.name + '">';
		}
		html += m49 || id;
		html += (placement && placement !== html) ? ` <span class="small">(${placement})</span>` : '';
		html += (subRegion || m49) ? `<span class="prov-location-api">${id}</span>` : id;
		return html;
	}

	function locationComparator(l1, l2) {
		return locationToStringCompare(l1).localeCompare(l2);
	}

	function locationToStringCompare(location) {
		return location.name
			+ '#' + (location.subRegion && (current.$messages[location.subRegion] || location.subRegion))
			+ '#' + location.countryM49 && current.$messages.m49[parseInt(location.countryM49, 10)]
			+ '#' + location.id;
	}

	function locationMatcher(term, location) {
		return matcher(term, location.name)
			|| location.subRegion && matcher(term, (current.$messages[location.subRegion] || location.subRegion))
			|| location.description && matcher(term, location.description)
			|| location.countryM49 && matcher(term, current.$messages.m49[parseInt(location.countryM49, 10)])
			|| location.countryA2 && (matcher(term, location.countryA2) || location.countryA2 === 'UK' && matcher(term, 'GB'));
	}


	/**
	 * Return true when the term is found in the text.
	 * @param {string} term The term to find.
	 * @param {string} text The text candidate.
	 */
	function matcher(term, text) {
		return window.Select2.util.stripDiacritics('' + text).toUpperCase().includes(window.Select2.util.stripDiacritics('' + term).toUpperCase());
	}

	function initializePopupInnerEvents(node) {
		_('instance-location').select2(current.locationSelect2(current.$messages['service:prov:default'], node));
	}

	function initializePopupEvents() {
		// Resource edition pop-up
		const $popup = _('popup-location');
		let $node = null;
		$popup.on('shown.bs.modal', function () {
			_('instance-location').trigger('focus');
			if ($('.select2-chosen').text() !== ' ') {
				current.enableCreate($popup)
			} else current.disableCreate($popup)
		}).on('change', _('instance-location'), function (e) {
			if ($('.select2-chosen').text() !== ' ') {
				current.enableCreate($popup)
			} else current.disableCreate($popup)
		}).on('submit', function (e) {
			e.preventDefault();
			current.save($(this), $node);
		}).on('show.bs.modal', function (event) {
			const $source = $(event.relatedTarget);
			const $tr = $source.closest('tr')[0];
			$node = $tr.dataset.node;
			_('generic-modal-title').text(current.table.dataTable().fnGetData($tr).node.name);
			initializePopupInnerEvents($node);
			_('instance-location').select2('data', current.table.dataTable().fnGetData($tr).preferredLocation)
		});
	}

	const current = {

		table: null,
		$table: null,

		initialize: function () {
			current.initializeDataTable();
			initializePopupEvents();
		},

		/**
		 * Location Select2 configuration.
		 */
		locationSelect2: function (_, node) {
			return genericSelect2(false, locationToHtml, node, null, locationComparator, locationMatcher);
		},

		/**
		 * Save a preferred location from the corresponding popup. And update the database.
		 * @param {string} node Resource node.
		 */
		save: function (i, node) {
			const $popup = _('popup-location');
			const location = _('instance-location').select2('data')
			const data = {
				node: node,
				preferredLocation: location.id || null
			}
			$.ajax({
				type: 'PUT',
				url: REST_PATH + 'service/prov/catalog',
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(data),
				success: function () {
					current.table.api().ajax.reload()
					$popup.modal('hide');
				},
				error: () => console.log('error')
			});
		},

		/**
		 * Redraw an resource table.
		 * @param {String} node Resource node.
		 */
		redrawResource: function (node) {
			_('table').DataTable().rows((_, data) => data.node.id === node).invalidate().draw(false);
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
		 * Initialize the search UI components
		 */
		initializeDataTable: function () {
			current.$table = _('table').on('click', '.import', current.importCatalog).on('click', '.cancel', current.cancelImportCatalog).on('click', '.update', current.locationSelect2);
			current.table = current.$table.dataTable({
				dom: 'rt<"row"<"col-xs-6"i><"col-xs-6"p>>',
				serverSide: false,
				searching: true,
				ajax: {
					url: REST_PATH + 'service/prov/catalog',
					dataSrc: '',
				},
				initComplete: function (_settings, catalogs) {
					Object.keys(catalogs).forEach(function (index) {
						if (catalogs[index].status.start && (catalogs[index].status.end || 0) === 0) {
							current.poll(catalogs[index].node.id);
						}
					});
				},
				rowCallback: (row, data) => $(row).attr('data-node', data.node.id),
				columns: [{
					data: 'node.id',
					width: '100px',
					type: 'string',
					class: 'truncate',
				}, {
					data: 'node.name',
					type: 'string',
					class: 'hidden-xs',
				}, {
					data: 'status.lastSuccess',
					render: {
						display: value => formatManager.formatDateTime(value)
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
					width: '16px',
				}, {
					data: 'preferredLocation',
					className: 'hidden-xs hidden-sm preferredLocation',
					width: '16px',
					type: 'string',
					render: {
					    display: value => value ? locationToHtml(value, false, true) : ''
					}
				}, {
					data: 'status.nbTypes',
					type: 'num',
					width: '16px'
				}, {
					data: 'status.nbPrices',
					type: 'num',
					width: '32px'
				}, {
					data: 'status.nbCo2Prices',
					type: 'num',
					width: '32px',
					render: {
					    display: (data, mode, object) => data && object?.status?.nbPrices ? `${Math.round(data / object.status.nbPrices * 1_000) / 10}%` : '',
                        _: (data, mode, object) => data && object?.status?.nbPrices ? data / object.status.nbPrices : 0
					}
				}, {
					data: 'status.end',
					width: '16px',
					type: 'str',
					render: {
					    display: (_i, mode, object) => `<div class="catalog-status" data-toggle="tooltip" title="${current.toStatusText(object)}">${current.toStatus(object)}</div>`,
					    filter: (_i, mode, object) => `${object?.failed}/${object?.done || 0}`
					}
				}, {
					data: null,
					width: '32px',
					orderable: false,
					render: function (_i, _j, catalog) {
						if (catalog.canImport) {
							if (catalog.status.start && (catalog.status.end || 0) === 0) {
								// Stop button
								return `<a class="cancel"><i class="fas fa-stop text-danger" data-toggle="tooltip" title="${current.$messages.stop}"></i></a>`;
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
				}, {
					width: '17px',
					orderable: false,
					render: () => `<a class="update" data-toggle="modal" data-target="#popup-location"><i class="fas fa-pencil-alt" data-toggle="tooltip" title="" data-original-title="${current.$messages.update}"></i></a>`
				}]
			});
		},
		/**
		 * Return the text corresponding to the catalog status.
		 */
		toStatusText: function (catalog) {
			const status = catalog.status;
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
			return status.workload ? Math.round(status.done / status.workload * 100) : 0;
		},

		/**
		 * Request a catalog update.
		 */
		importCatalog: function () {
			const catalog = current.table.fnGetData($(this).closest('tr')[0]);
			const id = catalog.node.id;
			const name = catalog.node.name;
			const force = $(this).is('.force');
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
			const catalog = current.table.fnGetData($(this).closest('tr')[0]);
			const id = catalog.node.id;
			const name = catalog.node.name;
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

		unScheduleUploadStep: function (node) {
			current.polling[node] && clearInterval(current.polling[node]);
			delete current.polling[node];
		},

		scheduleUploadStep: function (node) {
			current.polling[node] = setInterval(function () {
				current.synchronizeUploadStep(node);
			}, 5000);
		},

		synchronizeUploadStep: function (node) {
			current.unScheduleUploadStep(node);
			current.polling[node] = '-';
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/prov/catalog/' + node,
				type: 'GET',
				success: function (status) {
					if (current.$cascade.isSameTransaction(current.$transaction)) {
						current.updateStatus(status, node);
						if (status.end) {
							current.unScheduleUploadStep(node);
						} else {
							// Continue polling for this catalog
							current.scheduleUploadStep(node);
						}
					} else {
						current.unScheduleUploadStep(node);
					}
				},
				error: function () {
					current.unScheduleUploadStep(node);
				}
			});
		},

		/**
		 * Generate a pie corresponding to the given catalog status
		 */
		toStatus: function (catalog) {
			const id = catalog.node.id;
			const status = catalog.status;
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
