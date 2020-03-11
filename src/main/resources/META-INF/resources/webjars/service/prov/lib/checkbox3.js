/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery', 'cascade', 'jquery-ui'], function ($) {
    return (function () {
        if ($.fn.checkbox3) {
            return $.fn.checkbox3;
        }
        $.widget("ligoj.checkbox3", {
            options: {
                value: null
            },
            _create: function () {
                let onClick = function () {
                    var cb = this;
                    if (cb.readOnly) cb.checked = cb.readOnly = false;
                    else if (!cb.checked) cb.readOnly = cb.indeterminate = true;
                };
                this.element.on('click', onClick);
                $.proxy(this.onClick, this);
                return this;
            },
            value: function (value) {
                var cb = this.element[0];
                if (typeof value === 'undefined') {
                    // Get value
                    if (cb.indeterminate === true) {
                        return null;
                    }
                    return cb.checked;
                } else if (value === null) {
                    // Set to indeterminate
                    cb.readOnly = cb.indeterminate = true;
                    return this;
                } else {
                    // Set to true/false
                    cb.readOnly = false;
                    cb.checked = value;
                    return this;
                }
            }
        });
    }).call(this);
});