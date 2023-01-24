/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery', 'cascade', 'jquery-ui'], function ($, $cascade) {
    return (function () {
        if ($.fn.provSlider) {
            return $.fn.provSlider;
        }
        let uuid = 0;
        $.widget("ligoj.provSlider", {
            options: {
                // Ordered values
                values: [50],

                toInternal: value => value,
                toValue: (w, maxWidth, maxValue, toInternal) => w / maxWidth * toInternal(maxValue),


                // Maximal value for all labels
                max: 100,

                // Ordered labels by constrained ascending values
                labels: ['reserved'],

                // Default label
                label: 'reserved'
            },
            // Create a public method.
            value: function (labelOrValues, value) {
                // No label passed, act as a getter.
                if (labelOrValues === undefined) {
                    return this.options.values;
                }

                const label = labelOrValues;
                if (value === undefined) {
                    // No value, act as a getter for this label
                    if (typeof labelOrValues === 'string') {
                        return this.options.values[this.options.labelsToIndex[label]];
                    }
                    this.options.values = labelOrValues;
                    this._redraw();
                } else {
                    this.options.values[this.options.labelsToIndex[label]] = value;
                    this._synchronizeBar(this.slider.find('[data-label="' + label + '"]'), label, value, true);
                }
            },


            label: function (label) {
                if (label === undefined) {
                    return this.selectedLabel;
                }

                // Update the selected label
                this.selectedLabel = label;
                this.formLabel.find('.selected').text(label);

                // Update the input value
                const value = this.options.values[this.options.labelsToIndex[label]];
                const constraints = this._getConstraints(label);
                this.input.attr('min', constraints.min).attr('max', constraints.max);
                this._synchronizeBar(this.slider.find('[data-label="' + label + '"]'), label, value, true);
            },

            /**
             * Update the bar to reflect the business hours values.
             */
            _synchronizeBar: function ($bar, label, value, setX) {
                const valueF = `<strong>${this.options.format(value)}</strong>`;
                $bar.attr('data-original-title', valueF)
                    .tooltip('show')
                    .find('[data-toggle="tooltip"]')
                    .attr('data-original-title', valueF);
                if (typeof value === 'number') {
                    this.formGroup.find('li[data-label="' + label + '"]').addClass('has-value').find('.value').html(valueF);
                    if (label === this.selectedLabel && this.input.val() != value) {
                        this.input.val(value);
                    }
                } else {
                    this.formGroup.find('li[data-label="' + label + '"]').removeClass('has-value').find('.value').html('<i>(undefined)</i>');
                }
                if (setX) {
                    if (typeof value === 'number') {
                        const width = Math.round(this._valueToRate(value) * 10000) / 100;
                        $bar.removeClass('hidden').attr('style', 'width:' + width + '%;');
                    } else {
                        $bar.addClass('hidden').attr('style', 'width:0');
                    }
                }
            },

            /**
             * Add a new business hours UI. Data is not updated. Return the created UI.
             */
            _addRange: function (label, value) {
                const $bar = $(`<div class="progress-bar prov-slider-part" data-toggle="tooltip" data-html="true" data-placement="right" data-label="${label}"></div>`);
                $bar.append($('<span class="end" data-toggle="tooltip" data-html="true"><i class="fa fa-caret-up"></i></span>'));
                this.slider.append($bar);
                this.selector.find('.dropdown-menu').prepend(
                    `<li data-label="${label}" class="${typeof value === 'number' ? 'has-value' : ''}">
                        <a class="value"></a>
                        <p>
                            <span class="menu-right menu-label">${label}</span>
                            <a class="menu-right delete" data-toggle="tooltip" title="${$cascade.$messages.remove}"><i class="fa fa-fw fa-times"></i></a>
                            <a class="menu-right add"    data-toggle="tooltip" title="${$cascade.$messages.add}"><i class="fa fa-fw fa-plus"></i></a>
                        </p>
                    </li>`)
                this._enableResize($bar);
                this._synchronizeBar($bar, label, value, true);
                return $bar;
            },

            _onDelete: function (e) {
                const $label = $(e.target).closest('[data-label]');
                const label = this._getLabel($label);
                let remaining = null;
                for (let index = 0; index < this.options.labels.length; index++) {
                    if (typeof this.options.values[index] === 'number' && this.options.labels[index] !== label) {
                        remaining = index;
                        break;
                    }
                }
                if (remaining === null) {
                    // At least one label is required
                    return;
                }
                this.formLabel.find('li[data-label="' + label + '"]').removeClass('has-value');
                this.options.values[this.options.labelsToIndex[label]] = false;
                if (label === this.selectedLabel) {
                    // Deleting the current label, switch the label
                    this.label(this.options.labels[remaining]);
                } else {
                    this._synchronizeBar(this.slider.find('[data-label="' + label + '"]'), label, false, true);
                }
            },

            _onAdd: function (e) {
                const $label = $(e.target).closest('[data-label]');
                const label = this._getLabel($label);
                const labelIndex = this.options.labelsToIndex[label];
                let value = null;
                let index = 0;
                for (index = labelIndex + 1; index < this.options.labels.length; index++) {
                    value = this.options.values[index];
                    if (typeof value === 'number') {
                        break;
                    }
                }
                if (typeof value !== 'number') {
                    // Use the previous value
                    for (index = labelIndex; index-- > 0;) {
                        value = this.options.values[index];
                        if (typeof value === 'number') {
                            break;
                        }
                    }
                }

                // Set the value from the neighbor and select it
                this.formLabel.find('li[data-label="' + label + '"]').addClass('has-value');
                this.options.values[labelIndex] = value;
                this.label(label)
            },

            _create: function () {
                const $that = this;
                // Prepare containers
                this.input = this.element;
                this.selectedLabel = this.options.label;
                this.id = uuid++;
                this.formGroup = this.input.closest('.form-group');
                this.formLabel = this.formGroup.find('.control-label');
                this.inputGroup = this.input.closest('.input-group').addClass('with-slider');
                this.inputGroup.find('.prov-slider').empty().remove();
                this.formLabel.find('.prov-slider-selector').empty().remove();
                this.slider = $('<div class="prov-slider progress"></div>');
                this.inputGroup.append(this.slider);
                this.selector = $(`
                <div class="prov-slider-selector dropdown">
                    <button class="btn btn-default dropdown-toggle" type="button" id="${this.id}-s" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                        <span class="selected">${this.selectedLabel}</span>
                        <span class="caret"></span>
                    </button>
                    <ul class="dropdown-menu" aria-labelledby="${this.id}-s"></ul>
                </div>`);

                this.formLabel.after(this.selector);

                // Add ranges
                const labels = this.options.labels || [];
                this.options.labelsToIndex = {};
                this.slider.append($('<div class="progress-bar bar-info"></div>'));
                for (let index = labels.length; index-- > 0;) {
                    const label = labels[index];
                    this.options.labelsToIndex[label] = index;
                    this._addRange(label, this.options.values[index], label);
                }
                this.selector.on('click', '.delete', $.proxy(this._onDelete, this));
                this.selector.on('click', '.add, li[data-label]:not(.has-value)', $.proxy(this._onAdd, this));
                this.selector.on('click', 'li[data-label].has-value', function () {
                    if ($(this).hasClass('has-value')) {
                        $.proxy($that.label, $that)($that._getLabel($(this)));
                    }
                });
                this.label(this.selectedLabel);
                this.input.off('input.slider').on('input.slider', function () {
                    const val = $(this).val();
                    if ($.isNumeric(val)) {
                        $.proxy($that.value, $that)($that.selectedLabel, parseFloat(val, 10));
                    }
                    return true;
                });
            },

            _onChange: function ($bar, label, value) {
                if (this.options.onChange) {
                    $.proxy(this.options.onChange, $bar)(label, value, this.options);
                }
                this.input.trigger('input');
            },

            /**
             * Return the min and max values for the given label.
             */
            _getConstraints: function (label) {
                const result = { min: 0, max: this.options.max };
                const index = this.options.labelsToIndex[label];
                for (let i = 0; i < this.options.values.length; i++) {
                    const value = this.options.values[i];
                    if (typeof value === 'number') {
                        if (i < index) {
                            result.min = Math.max(result.min, value);
                        } else if (i > index) {
                            result.max = Math.min(result.max, value);
                        }
                    }
                }
                return result;
            },

            _getLabel: function ($bar) {
                return $bar.attr('data-label');
            },

            _resize: function ($bar, data) {
                // live update the start/end dates
                const label = this._getLabel($bar);
                let value = this._pixelToValue(data.size.width);
                if (data.element.parent().parent().children().prevObject.find("#instance-ramRate")[0]) {
                    value = Math.round(value)
                }
                const constraints = this._getConstraints(label);
                if (value < constraints.min || value > constraints.max) {
                    // Invalidate this resize
                    return false;
                }
                this._synchronizeBar($bar, label, value, false);
            },

            _enableResize: function ($bar) {
                const $this = this;
                $bar.resizable({
                    handles: 'e',
                    resize: function (_i, data) {
                        $this._resize($bar, data);
                    },
                    start: function (e, data) {
                        $this._onStartDrag(e, $bar, data);
                    },
                    stop: function (e, data) {
                        $this._onStopDrag(e, $bar, data);
                    },
                });
            },

            _redraw: function () {
                const $slider = this.slider;
                for (let index = 0; index < this.options.labels.length; index++) {
                    const label = this.options.labels[index];
                    this._synchronizeBar($slider.find('[data-label="' + label + '"]'), label, this.options.values[index], true);
                }
            },
            /**
              * Update the grid option according to the grid width.
              */
            _onStartDrag: function (e, $bar) {
                const constraints = this._getConstraints(this._getLabel($bar));
                $bar.resizable('option', 'minWidth', this._valueToPixel(constraints.min));
                $bar.resizable('option', 'maxWidth', this._valueToPixel(constraints.max));
            },

            _onStopDrag: function (e, $bar, data) {
                let value = this._pixelToValue(data.size.width);
                if (e.target.parentNode.parentElement.firstChild.nextSibling.getAttribute('id') == 's2id_instance-ramRate') {
                    value = Math.round(value);
                }        
                const label = this._getLabel($bar);
                const constraints = this._getConstraints(label);
                const previousValue = this.options.values[this.options.labelsToIndex[label]];
                if (value < constraints.min || value > constraints.max || value === previousValue) {
                    // Restore original UI
                    this._synchronizeBar($bar, label, previousValue, true);
                } else {
                    // Update business dates and then UI
                    this.value(label, value);
                    this._onChange($bar, label, value, true);
                }
            },

            /**
             * Convert a pixel value position to milliseconds.
             */
            _pixelToValue: function (width) {
                return Math.round(this.options.toValue(width, this.options.width, this.options.max, this.options.toInternal) * 10) / 4;
            },

            /**
             * Convert value to width percent.
             */
            _valueToRate: function (value) {
                return (value === 0 ? 0 : this.options.toInternal(value)) / this.options.toInternal(this.options.max);
            },

            /**
             * Convert value to width pixels.
             */
            _valueToPixel: function (value) {
                return Math.round(this._valueToRate(value) * this.options.width * 100) / 100;
            },

            _destroy: function () {
                this.slider.empty().remove();
                this.selector.empty().remove();
                this.inputGroup.removeClass('with-slider');
            }
        });
    }).call(this);
});