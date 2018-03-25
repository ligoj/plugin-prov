define(['cascade'], function ($cascade) {
	var current = {
		model: null,
		
		initialize: function () {
			current.updateTerraformInformation({});
			current.getInfo();
			_('terraform-install').on('click', function() {
				current.install(_('terraform-last-version').val());
			});
		},

		/**.
		 * Get the current Terraform version
		 */
		getInfo: function () {
			var $form = $('.terraform-details');
			$cascade.appendSpin($form, 'fa-4x');
			validationManager.reset($form);
			$.ajax({
				type: 'GET',
				url: REST_PATH + 'service/prov/terraform/install',
				success: current.updateTerraformInformation,
				complete: function() {
					$cascade.removeSpin($form);
				}
			});
		},
		
		updateTerraformInformation: function(model) {
			current.model = model;
			var $form = $('.terraform-details')
			validationManager.reset($form);
			if (model.installed) {
				if (model.version) {
					_('terraform-version').val(model.version);
				} else {
					// Error occurred while computing the Terraform version at server side
					validationManager.addError(_('terraform-version').val(''), 'terraform-cmd-error');
				}
			} else {
				// Not installed
				validationManager.addError(_('terraform-version').val(''), 'terraform-not-installed');
			}

			if (model.lastVersion) {
				_('terraform-last-version').val(model.lastVersion);
				_('terraform-changelog').removeClass('hidden').attr('href', 'https://github.com/hashicorp/terraform/blob/vVERSION/CHANGELOG.md'.replace('VERSION', model.lastVersion));
				if (model.version) {
					if (model.version === model.lastVersion) {
						// Up-to-date version, no update button to display
						_('terraform-install').addClass('hidden');
						return;
					}
				}
				// Update available
				_('terraform-install').removeClass('hidden').html('<i class="fa far-arrow-alt-circle-down"></i> ' + current.$messages.install);
			} else  {
				// Not available
				_('terraform-changelog').addClass('hidden')
				validationManager.addError(_('terraform-last-version').val(''), 'terraform-lastest-version');
			}
		},

		/**
		 * Request a Terraform binary install.
		 */
		install: function (version) {
			var $form = $('.terraform-details')
			$cascade.appendSpin($form, 'fa-4x');
			validationManager.reset($form);
			$.ajax({
				type: 'POST',
				url: REST_PATH + 'service/prov/terraform/version/' + version,
				success: function (model) {
					notifyManager.notify(Handlebars.compile(current.$messages['updated'])(model.version));
					current.updateTerraformInformation(model);
				},
				complete: function() {
					$cascade.removeSpin($form);
				}
			});
		}
	};
	return current;
});
