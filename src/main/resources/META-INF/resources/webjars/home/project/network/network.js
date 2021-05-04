/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery', 'cascade'], function ($, $cascade) {

	/**
	 * Return the HTML markup from the quote instance model.
	 */
	function formatQuoteResource(resource) {
		if (resource) {
			return (resource.resourceType === 'instance' ? '<i class="fas fa-server"></i>' : '<i class="fas fa-database"></i>') + ' ' + resource.name;
		}
		return '';
	}

	function addRow($table, inbound, link) {
		var $tbody = $table.find('tbody');
		var conf = $cascade.$current.currentSubscription.configuration;
		var $tr = $(`
		<tr>
			<td class="hidden-xs"><input type="text" class="form-control network-name" maxlength="100"/></td>
			<td><input type="text" required class="form-control network-peer" autocomplete="off" placeholder="${$cascade.$current.$messages[inbound ? 'source' : 'target']}" /></td>
			<td><input type="number" min="1" max="65535" required autocomplete="off" class="form-control network-port"/></td>
			<td><input type="number" min="0" autocomplete="off" class="form-control network-rate" /></td>
			<td><input type="number" min="0" autocomplete="off" class="form-control network-throughput"/></td>
			<td><a class="delete"><i class="fas fa-trash-alt" role="button" data-toggle="tooltip" title="${$cascade.$current.$messages.delete}"></i></a></td>
		</tr>`);
		$tbody.append($tr);
		var $peer = $tr.find('.network-peer');
		$peer.select2({
			formatSelection: formatQuoteResource,
			formatResult: formatQuoteResource,
			placeholder: $cascade.$current.$messages['service:prov:no-attached-instance'],
			allowClear: true,
			id: function (r) {
				return r.resourceType + r.id;
			},
			escapeMarkup: function (m) {
				return m;
			},
			data: () => {
				return {
					results: conf.instances.concat(conf.databases).concat(conf.storages.filter(s => s.price.type.network)).map(r => {
						r.text = r.name;
						return r;
					})
				};
			}
		});
		if (link && link.source) {
			// Complete the data
			var peer = conf[link[inbound ? 'sourceType' : 'targetType'] + 'sById'][link[inbound ? 'source' : 'target']];
			$peer.select2('data', peer);
			$tr.find('.network-name').val(link.name || null);
			$tr.find('.network-port').val(link.port || null);
			$tr.find('.network-rate').val(link.rate || null);
			$tr.find('.network-throughput').val(link.throughput || null);
		}
	}
	function deleteRow() {
		$(this).closest('tr').empty().remove();
	}
	function deleteAll() {
		$(this).closest('table').find('tbody').find('tr').empty().remove();
	}

	function configure($service, resourceId, resourceType) {
		let conf = $service.model.configuration;

		// Update the popup context
		_('u-network-resource-name').text($cascade.$current.$messages.network + ': ' + conf[resourceType + 'sById'][resourceId].name);
		_('popup-prov-network').attr('data-id', resourceId).attr('data-prov-type', resourceType);

		// Add the IO
		var $in = $('.network-in');
		var $out = $('.network-out');
		$in.find('tbody').empty();
		$out.find('tbody').empty();
		var networks = conf.networks;
		for (let link of networks) {
			if (link.sourceType === resourceType && link.source === resourceId) {
				// Outgoing
				addRow($out, false, link);
			} else if (link.targetType === resourceType && link.target === resourceId) {
				// Incoming
				addRow($in, true, link);
			}
		}
	}

	function initialize() {
		var $popup = _('popup-prov-network');
		$popup.on('shown.bs.modal', function () {
			_('quote-name').trigger('focus');
		}).on('submit', function (e) {
			e.preventDefault();
			var resourceId = parseInt($(this).attr('data-id'), 10);
			var resourceType = $(this).attr('data-prov-type');
			var io = [];
			var subscription = $cascade.$current.currentSubscription;
			var conf = subscription.configuration;
			var networks = conf.networks;

			$('#popup-prov-network').find('tbody').find('tr').each(function () {
				var $tr = $(this);
				io.push({
					inbound: $tr.closest('.network-in').length === 1,
					name: $tr.find('input.network-name').val() || null,
					port: parseInt($tr.find('input.network-port').val() || 0, 10) || null,
					rate: parseInt($tr.find('input.network-rate').val() || 0, 10) || null,
					throughput: parseInt($tr.find('.network-throughput').val() || 0, 10) || null,
					peer: $tr.find('input.network-peer').select2('data').id,
					peerType: $tr.find('input.network-peer').select2('data').resourceType,
				});
			})

			$.ajax({
				type: 'PUT',
				url: `${REST_PATH}service/prov/${subscription.id}/network/${resourceType}/${resourceId}`,
				dataType: 'json',
				contentType: 'application/json',
				data: JSON.stringify(io),
				beforeSend: () => $('.loader-wrapper').removeClass('hidden'),
				complete: () => $('.loader-wrapper').addClass('hidden'),
				success: function () {
					// Update the modal
					// Remove the previous links
					var link, i;
					for (i = networks.length; i-- > 0;) {
						link = networks[i];
						if (link.sourceType === resourceType && link.source === resourceId || link.targetType === resourceType && link.target === resourceId) {
							// Delete this old link
							networks.splice(i, 1);
						}
					}

					// Add the new links
					for (i = 0; i < io.length; i++) {
						link = io[i];
						link.source = link.inbound ? link.peer : resourceId;
						link.sourceType = link.inbound ? link.peerType : resourceType;
						link.target = link.inbound ? resourceId : link.peer;
						link.targetType = link.inbound ? resourceType : link.peerType;
						delete link.inbound;
						delete link.peer;
						delete link.peerType;
						networks.push(link);
					}

					$popup.modal('hide');
				}
			});
		}).on('show.bs.modal', function (e) {
			var $tr = $(e.relatedTarget).closest('tr');
			var resourceId = parseInt($tr.attr('data-id'), 10);
			var resourceType = $tr.closest('[data-prov-type]').attr('data-prov-type');
			$cascade.$current.$super('requireService')($cascade.$current.$parent, 'service:prov', $service => {
				if ($service) {
					configure($service, resourceId, resourceType);
				}
			});
		}).on('click', '.add', function () {
			addRow($(this).closest('table'));
		}).on('click', 'tbody .delete', deleteRow).on('click', 'thead .delete', deleteAll);

	}

	return {
		initialize: initialize
	};
});