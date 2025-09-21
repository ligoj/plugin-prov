/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery'], function ($) {
	return (function () {
		let current = null;
		function build(newCurrent) {
			current = newCurrent;
			return this;
		}

		function toTags(resource) {
			return (current.model?.configuration.tags[resource.resourceType] || {})[resource.id] || [];
		}

		function formatResult(tag) {
			return tag.text || (tag.name + (typeof tag.value === 'undefined' ? '' : (':' + tag.value)));
		}

		function formatSelection(tag) {
			return tag.text || (tag.name + (typeof tag.value === 'undefined' ? '' : (':' + tag.value)));
		}

		function format(tag) {
			if (typeof tag.name === 'string') {
				return tag.name + (typeof tag.value === 'undefined' ? '' : (':' + tag.value));
			}
			return tag.text;
		}
		function toTagsString(resource) {
			return toTags(resource).map(format);
		}

		function suggest(term, resource) {
			// Get tags of current resource
			const tags = toTagsString(resource);
			let keys = {};
			let keyValues = {};
			Object.keys((current.model?.configuration.tags) || {}).forEach(type => Object.keys(current.model.configuration.tags[type] || {}).forEach(rId =>
				current.model.configuration.tags[type][rId].forEach(
					t => {
						keys[t.name] = true;
						if (t.value) {
							keyValues[t.name + ':' + t.value] = true;
						}
					})));
			keys = Object.keys(keys);
			keyValues = Object.keys(keyValues);

			const parts = term.split(':');
			const key = parts[0];
			const value = parts.length > 1 ? parts[1] : null;
			const suggests = [];
			if (value === null) {
				// Key mode
				// First add the term itself
				if (tags.indexOf(term) === -1) {
					suggests.push({ text: term, id: term });
				}

				// Then add keys starting with the 'key'
				keys.filter(k => k.startsWith(key)).sort().forEach(k => suggests.push({ text: k + ':', id: k + ':' }))

				// Then add the values starting with the term and not yet attached to current resource
				keyValues.filter(v => v.startsWith(term) && tags.indexOf(v) === -1).sort().forEach(v => suggests.push({ text: v, id: v }))

				// Then add the keys containing the 'key'
				keys.filter(k => k.indexOf(key) > 1).forEach(k => suggests.push({ text: k + ':', id: k + ':' }))

				// Then add the values containing with the term and not yet attached to current resource
				keyValues.filter(v => v.indexOf(term) > 1 && tags.indexOf(v) === -1).sort().forEach(v => suggests.push({ text: v, id: v }))
			} else {
				// Value mode
				// First add the values starting with the term
				keyValues.filter(v => v.startsWith(term) && tags.indexOf(v) === -1).sort().forEach(v => suggests.push({ text: v, id: v }))

				// Then add the values containing with the term
				keyValues.filter(v => v.indexOf(term) > 1 && tags.indexOf(v) === -1).sort().forEach(v => suggests.push({ text: v, id: v }))
				// Then add the term itself
				if (tags.indexOf(term) === -1) {
					if (term.endsWith(':')) {
						// Itself but without the trailing (useless) ':'
						var part = term.substring(0, term.indexOf(':'));
						suggests.push({ text: part, id: part });
					} else if (!keyValues.includes(term)){
						// Itself, a well formed 'key:value'
						suggests.push({ text: term, id: term });
					}
				}
			}
			return suggests;
		}
		function render(_i, mode, resource) {
			if (mode === 'sort' || mode === 'filter') {
				return toTags(resource).map(formatResult).join(',');
			}
			// Render the tags
			return '<input type="text" class="resource-tags" data-resource="' + resource.id + '" autocomplete="off" name="resource-tags">';
		}

		function select2(node, resource) {
			$(node).find('.resource-tags').select2('destroy').select2({
				multiple: true,
				minimumInputLength: 1,
				createSearchChoice: () => null,
				formatInputTooShort: current.$messages['service:prov:tag-select'],
				formatResult: formatResult,
				formatSelection: formatSelection,
				data: function () {
					var term = $('.select2-container-active.resource-tags').find('input.select2-input').val();
					return {
						results: term ? suggest(term, resource) : [],
						text: format
					};
				}
			}).select2('data', toTags(resource)).off('change').on('change', function (event) {
				const uType = resource.resourceType;
				let tag = null;
				if (event.added) {
					// New tag
					tag = event.added;
					const parts = tag.text ? tag.text.split(':') : [tag.name, tag.value]
					const data = {
						name: parts[0],
						value: parts[1],
						type: resource.resourceType,
						resource: resource.id
					};

					// Replicate tag properties for Select2
					tag.name = data.name;
					tag.value = data.value;
					tag.type = data.type;
					tag.resource = data.resource;

					current.$main.trimObject(data);
					$.ajax({
						type: 'POST',
						url: REST_PATH + 'service/prov/' + current.model.subscription + '/tag',
						dataType: 'json',
						contentType: 'application/json',
						data: JSON.stringify(data),
						success: function (id) {
							data.id = id;
							tag.id = id;
							if (current.model?.configuration?.tags) {
								// Update the model
								const tags = current.model.configuration.tags;
								let tTags = tags[uType];
								if (typeof tTags === 'undefined') {
									tTags = {};
									tags[uType] = tTags;
								}
								let rTags = tTags[data.resource];
								if (typeof rTags === 'undefined') {
									rTags = [];
									tTags[data.resource] = rTags;
								}
								rTags.push(data);
							}
							notifyManager.notify(Handlebars.compile(current.$messages[data.id ? 'updated' : 'created'])(format(data)));
						}
					});
				} else if (event.removed) {
					// Tag to delete
					tag = event.removed.tag || event.removed;
					const $this = $(this);
					$.ajax({
						type: 'DELETE',
						url: REST_PATH + 'service/prov/' + current.model.subscription + '/tag/' + tag.id,
						success: function () {
							notifyManager.notify(Handlebars.compile(current.$messages['deleted'])(format(tag)));
							const tModel = current.model?.configuration?.tags;
							let i = 0;
							if (tModel) {
								// Update the model
								const ttModel = tModel[uType][resource.id];
								for (i = 0; i < ttModel.length; i++) {
									if (ttModel[i].id === tag.id) {
										ttModel.splice(i, 1);
										break;
									}
								}
							}

							// Update the Select2 model
							const sModel = ($this.val() || '').split(',');
							for (i = 0; i < sModel.length; i++) {
								if (sModel[i] === tag.text) {
									sModel.splice(i, 1);
									break;
								}
							}
							$this.val(sModel.join(','));
						}
					});
					return true;
				}
			}).on('select2-selecting', function (e) {
				if (e.val.endsWith(':')) {
					$(function () {
						const $input = $('.select2-dropdown-open.resource-tags').find('input.select2-input');
						$input.val(e.val).focus();
						$input.trigger('input');
					})
					e.preventDefault()
					return false;
				}
			});
		}

		// Exports
		return {
			render: render,
			select2: select2,
			build: build,
			toTagsString: toTagsString
		};

	}).call(this);
});