define(function () {
	var current = {

		/**
		 * VM table
		 */
		vmTable: null,

		/**
		 * Current configuration.
		 */
		model: null,

		/**
		 * Managed status.
		 */
		vmStatus: {
			powered_on: 'fa fa-play text-success',
			powered_off: 'fa fa-stop text-danger',
			suspended: 'fa fa-pause text-warning'
		},

		/**
		 * Managed operations.
		 */
		vmOperations: {
			OFF: 'fa fa-power-off',
			ON: 'fa fa-play',
			SUSPEND: 'fa fa-pause',
			SHUTDOWN: 'fa fa-stop',
			RESET: 'fa fa-repeat',
			REBOOT: 'fa fa-refresh'
		},

		initialize: function () {
			current.$super('$view').on('click', '.subscriptions .service-vm-operation', current.serviceVmOperation);
		},

		/**
		 * Synchronize the VM configuration UI with the retrieved data
		 */
		configure: function (configuration) {
			current.model = configuration;
			_('vm-name').text(configuration.parameters['service:vm:vcloud:organization'] + ' / ' + configuration.parameters['service:vm:vcloud:id']);
			require(['later/later.mod', 'pretty-cron/pretty-cron'], function (later) {
				current.initializeVmConfiguration(later);
				later.date.localTime();
				var index;
				var schedules = configuration.configuration.schedules;
				for (index = 0; index < schedules.length; index++) {
					current.fillVmScheduleTr(later, _('vm-schedules').find('tbody>tr[data-id="' + schedules[index].operation + '"]'), schedules[index].cron);
				}
				_('subscribe-configuration-vm').removeClass('hide');
			});
		},

		initializeTable: function () {
			current.vmTable && current.vmTable.fnDestroy();
			_('vm-schedules').find('tbody>tr').remove();
			var operation;
			var operations = [];
			for (operation in current.vmOperations) {
				if (current.vmOperations.hasOwnProperty(operation)) {
					operations.push(operation);
				}
			}
			current.vmTable = _('vm-schedules').dataTable({
				dom: '<"row"<"col-xs-6">>t',
				pageLength: -1,
				createdRow: function (nRow, operation) {
					$(nRow).attr('data-id', operation.toLowerCase());
				},
				columns: [{
					data: null,
					orderable: false,
					render: function (_i, _j, operation) {
						var label = current.$messages['service:vm:' + operation.toLowerCase()];
						var help = current.$messages['service:vm:' + operation.toLowerCase() + '-help'];
						return '<i class="' + current.vmOperations[operation] + '" data-html="true" data-toggle="tooltip" title="' + label + '<br>' + help + '" data-container="#_ucDiv"></i><span class="hidden-xs hidden-sm"> ' + label + '</span>';
					}
				}, {
					data: null,
					className: 'vm-schedules-cron'
				}, {
					data: null,
					className: 'vm-schedules-description hidden-sm hidden-xs'
				}, {
					data: null,
					className: 'vm-schedules-next responsive-datetime'
				}, {
					data: null,
					orderable: false,
					width: '40px',
					render: function () {
						var result = '<td><a class="update" data-toggle="modal" data-target="#vm-schedules-popup"><i class="fa fa-pencil" data-toggle="tooltip" title="';
						result += current.$messages.update;
						result += '"></i></a> <a class="delete"><i class="fa fa-trash" data-toggle="tooltip" title="' + current.$messages['delete'] + '"></i></a></td></tr>';
						return result;
					}
				}],
				destroy: true,
				data: operations
			});
		},

		/**
		 * Initialize VM configuration UI components
		 */
		initializeVmConfiguration: function (later) {
			current.initializeTable();

			// Next schedule preview
			_('cron').on('change', function () {
				var cron = $(this).val();
				_('cron-next').val(cron ? moment(later.schedule(later.parse.cron(cron, true)).next(1))
					.format(formatManager.messages.shortdateMomentJs + ' HH:mm:ss') : '');
			});
			// VM operation schedule helper in popup
			_('vm-schedules-popup').on('show.bs.modal', function (event) {
				validationManager.reset($(this));
				var $tr = $(event.relatedTarget).closest('tr');
				var operation = $tr.attr('data-id');
				_('vm-schedulesmodal-operation').attr('data-id', operation).text(current.$messages['service:vm:' + operation]);
				require(['cron-gen/cron-gen'], function () {
					// Reset the state of helper UI
					_('CronGenMainDiv').remove();
					_('cron-next').val('');
					_('cron').val('').cronGen();
				});
			}).on('submit', function (e) {
				e.preventDefault();
				var operation = _('vm-schedulesmodal-operation').attr('data-id');
				var $tr = _('vm-schedules').find('tbody>tr[data-id="' + operation + '"]');
				var cron = _('cron').val();
				if (cron) {
					// Save or update the schedule with a new value
					current.saveOrUpdateVmSchedule({
						cron: cron,
						operation: operation
					}, function () {
						current.fillVmScheduleTr(later, $tr, cron);
						$tr.fadeIn(1500);
					});
				} else {
					// Delete this schedule
					current.deleteVmSchedule(operation.toUpperCase(), function () {
						current.fillVmScheduleTr(later, $tr);
					});
				}
				return false;
			});
			_('vm-schedules').find('tbody>tr .delete').on('click', function () {
				var $tr = $(this).closest('tr');
				current.deleteVmSchedule($tr.attr('data-id').toUpperCase(), function () {
					current.fillVmScheduleTr(later, $tr);
				});
			});
		},

		/**
		 * Render VM operations.
		 */
		renderFeatures: function (subscription) {
			var result = '';
			var operation;

			// Add Off,On,Shutdown,Reset,Reboot,Suspend
			for (operation in current.vmOperations) {
				if (current.vmOperations.hasOwnProperty(operation)) {
					result += current.renderServiceServiceVmOperationButton(current.vmOperations[operation], operation);
				}
			}

			// Add scheduler
			result += current.$super('renderServicelink')('calendar', '#/home/project/' + subscription.project + '/subscription/' + subscription.id, 'service:vm:schedule');

			return result;
		},

		renderServiceServiceVmOperationButton: function (icon, operation) {
			return '<button class="btn-link feature service-vm-operation service-vm-operation-' + operation.toLowerCase() +
				'" data-operation="' + operation + '" data-toggle="tooltip" title="' + current.$messages['service:vm:' +
					operation.toLowerCase()] + '"><i class="' + icon + '"></i></button>';
		},

		/**
		 * Display the status of the VM
		 */
		renderDetailsFeatures: function (subscription) {
			var status = subscription.data.vm.status.toLowerCase();
			var busy = subscription.data.vm.busy;
			var deployed = status === 'powered_off' && subscription.data.vm.deployed;
			return '<i data-toggle="tooltip" data-html="true" title="' + (current.$messages['service:vm:' + status] || status) +
				(busy ? ' (' + current.$messages['service:vm:busy'] + ')' : '') +
				(deployed ? '<br>[' + current.$messages['service:vm:deployed'] + ']' : '') + '"  class="' +
				(current.vmStatus[status] || 'fa fa-question-circle-o text-muted') +
				(busy ? ' faa-flash animated' : '') + (deployed ? ' deployed' : '') + '"></i>';
		},

		/**
		 * Delete a scheduled operation.
		 * @param {String} Operation name to unschedule.
		 * @param {function} The optional callback on success.
		 */
		deleteVmSchedule: function (operation, callback) {
			// Save business hours on server
			var subscription = current.model.subscription;
			$.ajax({
				type: 'DELETE',
				url: REST_PATH + 'service/vm/' + subscription + '/' + operation,
				dataType: 'json',
				contentType: 'application/json',
				success: function () {
					callback && callback();
					notifyManager.notify((Handlebars.compile(current.$messages.deleted))(subscription + ' : ' + operation));
				}
			});
		},

		/**
		 * Save or update a schedule.
		 * @param {Object} schedule to update/save : operation+CRON
		 * @param {function} The optional callback on success.
		 */
		saveOrUpdateVmSchedule: function (schedule, callback) {
			// Save business hours on server
			var subscription = current.model.subscription;
			$.ajax({
				type: 'POST',
				url: REST_PATH + 'service/vm/' + subscription,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(schedule),
				success: function () {
					callback && callback();
					notifyManager.notify((Handlebars.compile(current.$messages.updated))(subscription + ' : ' + schedule.operation.toUpperCase()));
				}
			});
		},

		/**
		 * Update the state of the line of schedule. If CRON is empty, the schedule is unscheduled.
		 */
		fillVmScheduleTr: function (later, $tr, cron) {
			_('vm-schedules-popup').modal('hide');
			if (cron) {
				$tr.find('.vm-schedules-cron').text(cron);
				$tr.find('.vm-schedules-description').text(prettyCron.toString(cron, true));
				$tr.find('.vm-schedules-next').text(moment(later.schedule(later.parse.cron(cron, true)).next(1))
					.format(formatManager.messages.shortdateMomentJs + ' HH:mm:ss'));
				$tr.addClass('scheduled');
			} else {
				$tr.find('.vm-schedules-cron').html('&nbsp;');
				$tr.find('.vm-schedules-description').html('&nbsp;');
				$tr.find('.vm-schedules-next').html('&nbsp;');
				$tr.removeClass('scheduled');
			}
		},

		/**
		 * Execute a VM operation.
		 */
		serviceVmOperation: function () {
			var subscription = current.$super('subscriptions').fnGetData($(this).closest('tr')[0]);
			var operation = $(this).attr('data-operation');
			var vm = subscription.parameters['service:vm:vcloud:id'];
			var id = subscription.id;
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/vm/' + id + '/' + operation,
				contentType: 'application/json',
				type: 'POST',
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages['vm-operation-success'])([vm, operation]));
				}
			});
		}

	};
	return current;
});
