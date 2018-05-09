define([], function () {
    var current = {
        template: '<div class="progress-bar" data-toggle="tooltip" title=""></div>',

        reset: function (status, $status, $messages) {
            $status.find('.progress').find('.progress-bar').remove();
            current.update(status, $status, $messages);
        },

		/**
		 * Update the Terraform status.
		 */
        update: function (status, $status, $messages) {
            $status.removeClass('invisible');
            var sequence = (status.sequence || '').split(',');
            var commandIndex = typeof status.commandIndex === 'undefined' ? -1 : status.commandIndex;
            status.command = commandIndex === -1 ? '?' : sequence[commandIndex];

            // Compute the progress percentage since "init" and "apply" commands are optional
            var configuration = {
                generate: { width: 2 },
                clean: { width: 2 },
                secrets: { width: 2 },
                show: { width: 2 },
                state: { width: 2 },
                init: { width: 4 },
                plan: { width: 16 },
                apply: { width: 50, classes: 'progress-bar-success', details: true },
                destroy: { width: 20, classes: 'progress-bar-warning', details: true }
            };

            var sum = 0;
            $.each(configuration, function (command, configurationI) {
                if ($.inArray(command, sequence) === -1) {
                    configurationI.width = 0;
                } else {
                    sum += configurationI.width;
                }
            });

            // Complement to 100%
            var ratio = 100 / (sum || 0.01);
            $.each(configuration, function (command, configurationI) {
                configurationI.width *= ratio;
            });

            // Update the progress tooltips and witdh
            var $progress = $status.find('.progress');
            var finished = typeof status.end !== 'undefined';
            for (var i = 0; i <= commandIndex; i++) {
                var commandI = sequence[i];
                var active = i === commandIndex;
                var previous = i < commandIndex;
                var configurationI = configuration[commandI];
                var $progressI = $progress.find('.status-' + commandI);
                if ($progressI.length === 0 && (configurationI.width || configurationI.details)) {
                    // Not yet progress for this command
                    $progressI = $(current.template).addClass('status-' + commandI);
                    $progress.append($progressI);
                    if (configurationI.details && active) {
                        // Detailed progress to split : completed & completing bars
                        $progressI.addClass('completed');
                        if (typeof status.end === 'undefined' || status.failed) {
                            $progress.append($(current.template)
                                .addClass('status-' + commandI)
                                .addClass(configurationI.classes)
                                .addClass('completing'));
                        }
                    }
                    $progressI = $progress.find('.status-' + commandI);
                    $progressI.addClass(configurationI.classes || 'progress-bar-inverse')
                }

                // Animation toggles
                if (active) {
                    $progressI.addClass('active');
                } else {
                    $progressI.html('').removeClass('active');
                }

                var widthI = configurationI.width;
                var text = '';
                if (configurationI.details) {
                    if (active) {
                        // Update 2 progressbars: each one has a part of the reserved width
                        var total = status.toAdd + status.toDestroy + status.toUpdate + status.toReplace * 2;
                        var full = total === 0 || status.end && !status.failed;
                        var completed = full ? configurationI.width : (configurationI.width * status.completed / total);
                        var completing = full ? 0 : (configurationI.width * status.completing / total);

                        // Add percent text only for the apply
                        text = '&nbsp;' + (full ? '100' : Math.round(status.completed * 100 / total)) + '%&nbsp;';
                        $progressI.filter('.completed')
                            .attr('data-original-title', Handlebars.compile($messages['service:prov:terraform:status-completed'])([commandI, full ? total : status.completed, total]))
                            .css('width', completed + '%');
                        $progressI.filter('.completing')
                            .attr('data-original-title', Handlebars.compile($messages['service:prov:terraform:status-completing'])([commandI, status.completing]))
                            .css('width', completing + '%');
                        configurationI.width = completed + completing;
                    } else {
                        // Remove useless 'completing'  bar
                        $progressI.filter('.completing').remove();

                        // Full with for the completed bar
                        $progressI.filter('.completed').css('width', widthI + '%');
                    }
                } else {
                    $progressI
                        .attr('data-original-title', '<i class="fas fa-' + (status.failed ? 'exclamation-circle' : 'check-circle') + '"></i>&nbsp;' + $messages['service:prov:terraform:status-' + commandI])
                        .css('width', widthI + '%');

                    if (!active) {
                        $progressI.removeClass('progress-bar-striped').removeClass('active');
                    }
                }
                if (active && (status.failed || !finished)) {
                    // Add an overlay text above the progres
                    $progressI.not('.completing')
                        .html('<span class="progress-text">' + ($messages['service:prov:terraform:status-' + status.command] || Handlebars.compile($messages['service:prov:terraform:status-command'])(status.command)) + '</span>' + text);
                } else {
                    $progressI.not('.completing').empty();
                }
            }

            var $lastProgress = $progress.find('.progress-bar:last-child').filter(':not(.completed)');
            if (status.failed) {
                // The completing part failed
                $lastProgress.removeClass('active')
                    .addClass('error')
                    .addClass('progress-bar-striped')
                    .addClass('progress-bar-danger')
                    .removeClass('progress-bar-success').removeClass('progress-bar-warning').removeClass('progress-bar-primary').removeClass('progress-bar-info').removeClass('progress-bar-inverse');
            } else if (status.end) {
                // The last progress is not running anymore
                $lastProgress.removeClass('active').removeClass('progress-bar-striped');
            } else {
                // The last progress is running
                $lastProgress.addClass('active').addClass('progress-bar-striped');
            }

            // Update the status text
            status.startDate = formatManager.formatDateTime(status.start);
            status.endDate = status.end ? formatManager.formatDateTime(status.end) : '';
            $status.find('.status').html(Handlebars.compile($messages['service:prov:terraform:status'])(status));

            // Update the error style of progress bars
            if (status.failed && $progress.find('.status-error').length === 0) {
                var notExecuted = 100;
                for (var i = 0; i <= commandIndex; i++) {
                    var configurationI = configuration[sequence[i]];
                    notExecuted -= configurationI.width;
                }
                $progress.append($(current.template).addClass('status-error').addClass('progress-bar-danger').css('width', notExecuted + '%'));
            }
        },

        toWidth: function ($element) {
            var width = $element.width();
            var parent = $element.parent().width();
            if (width && parent) {
                return width * 100 / parent;
            }
            return 0;
        }
    };


    return current;
})