/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['jquery'], function () {
  return (function () {
    var COUNT_FRAMERATE, COUNT_MS_PER_FRAME, DIGIT_FORMAT, DIGIT_HTML, DIGIT_SPEEDBOOST, DURATION, FORMAT_MARK_HTML, FORMAT_PARSER, FRAMERATE, FRAMES_PER_VALUE, MS_PER_FRAME, MutationObserver, Odometer, RIBBON_HTML, TRANSITION_END_EVENTS, TRANSITION_SUPPORT, VALUE_HTML, addClass, createFromHTML, fractionalPart, now, removeClass, requestAnimationFrame, round, transitionCheckStyles, trigger, truncate, wrapJQuery, _jQueryWrapped, _old, _ref, _ref1,
      __slice = [].slice;

    VALUE_HTML = '<span class="odometer-value"></span>';

    RIBBON_HTML = '<span class="odometer-ribbon"><span class="odometer-ribbon-inner">' + VALUE_HTML + '</span></span>';

    DIGIT_HTML = '<span class="odometer-digit"><span class="odometer-digit-spacer">8</span><span class="odometer-digit-inner">' + RIBBON_HTML + '</span></span>';

    FORMAT_MARK_HTML = '<span class="odometer-formatting-mark"></span>';

    DIGIT_FORMAT = '(,ddd).dd';

    FORMAT_PARSER = /^\(?([^)]*)\)?(?:(.)(d+))?$/;

    FRAMERATE = 30;

    DURATION = 2000;

    COUNT_FRAMERATE = 20;

    FRAMES_PER_VALUE = 2;

    DIGIT_SPEEDBOOST = .5;

    MS_PER_FRAME = 1000 / FRAMERATE;

    COUNT_MS_PER_FRAME = 1000 / COUNT_FRAMERATE;

    TRANSITION_END_EVENTS = 'transitionend webkitTransitionEnd oTransitionEnd otransitionend MSTransitionEnd';

    transitionCheckStyles = document.createElement('div').style;

    TRANSITION_SUPPORT = (transitionCheckStyles.transition != null) || (transitionCheckStyles.webkitTransition != null) || (transitionCheckStyles.mozTransition != null) || (transitionCheckStyles.oTransition != null);

    requestAnimationFrame = window.requestAnimationFrame || window.mozRequestAnimationFrame || window.webkitRequestAnimationFrame || window.msRequestAnimationFrame;

    MutationObserver = window.MutationObserver || window.WebKitMutationObserver || window.MozMutationObserver;

    createFromHTML = function (html) {
      var el;
      el = document.createElement('div');
      el.innerHTML = html;
      return el.children[0];
    };

    removeClass = function (el, name) {
      el.className = el.className.replace(new RegExp("(^| )" + (name.split(' ').join('|')) + "( |$)", 'gi'), ' ');
      return el.className;
    };

    addClass = function (el, name) {
      removeClass(el, name);
      el.className += " " + name;
      return el.className;
    };

    trigger = function (el, name) {
      var evt;
      if (document.createEvent != null) {
        evt = document.createEvent('HTMLEvents');
        evt.initEvent(name, true, true);
        return el.dispatchEvent(evt);
      }
    };

    now = function () {
      if (window.performance && typeof window.performance.now === 'function') {
        return _ref1.now();
      }
      return +(new Date);
    };

    round = function (val, precision) {
      if (precision == null) {
        precision = 0;
      }
      if (!precision) {
        return Math.round(val);
      }
      val *= Math.pow(10, precision);
      val += 0.5;
      val = Math.floor(val);
      return val / Math.pow(10, precision);
    };

    truncate = function (val) {
      if (val < 0) {
        return Math.ceil(val);
      } else {
        return Math.floor(val);
      }
    };

    fractionalPart = function (val) {
      return val - round(val);
    };

    _jQueryWrapped = false;

    wrapJQuery = function () {
      if (_jQueryWrapped) {
        return;
      }
      if (window.jQuery != null) {
        _jQueryWrapped = true;
        let properties = ['html', 'text'];
        return properties.map(property => {
          var old = window.jQuery.fn[property];
          let fn = function (val) {
            if ((val == null) || ((this[0] != null ? this[0].odometer : void 0) == null)) {
              return old.apply(this, arguments);
            }
            return this[0].odometer.update(val);
          };
          window.jQuery.fn[property] = fn;
          return fn;
        });
      }
    };
    wrapJQuery();

    setTimeout(wrapJQuery, 0);

    var exportOdometer = (function () {
      function OdometerPriv(options) {
        const _this = this;
        this.options = options;
        this.el = this.options.el;
        if (this.el.odometer != null) {
          return this.el.odometer;
        }
        this.el.odometer = this;
        const options2 = OdometerPriv.options;
        for (let k in options2) {
          let v = options2[k];
          if (this.options[k] == null) {
            this.options[k] = v;
          }
        }
        const _base = this.options;
        if (_base.duration == null) {
          _base.duration = DURATION;
        }
        this.MAX_VALUES = ((this.options.duration / MS_PER_FRAME) / FRAMES_PER_VALUE) | 0;
        this.resetFormat();
        const cleanValue = this.options.value;
        this.value = this.cleanValue(cleanValue == null ? '' : _ref1);
        this.renderInside();
        this.render();
        try {
          ['innerHTML', 'innerText', 'textContent'].forEach(property => {
            if (this.el[property] != null) {
              (function (p) {
                return Object.defineProperty(_this.el, p, {
                  get: () => {
                    if (p === 'innerHTML') {
                      return _this.inside.outerHTML;
                    }
                    let _ref3 = _this.inside.innerText;
                    return _ref3 == null ? _this.inside.textContent : _ref3;
                  },
                  set: val => { _this.update(val); }
                });
              })(property);
            }
          });
        } catch (_error) {
          // Ignore
          this.watchForMutations();
        }
      }

      OdometerPriv.prototype.renderInside = function () {
        this.inside = document.createElement('div');
        this.inside.className = 'odometer-inside';
        this.el.innerHTML = '';
        return this.el.appendChild(this.inside);
      };

      OdometerPriv.prototype.watchForMutations = function () {
        var _this = this;
        if (MutationObserver == null) {
          return;
        }
        try {
          if (this.observer == null) {
            this.observer = new MutationObserver(function (mutations) {
              var newVal;
              newVal = _this.el.innerText;
              _this.renderInside();
              _this.render(_this.value);
              return _this.update(newVal);
            });
          }
          this.watchMutations = true;
          return this.startWatchingMutations();
        } catch (_error) {
          // Ignore
        }
      };

      OdometerPriv.prototype.startWatchingMutations = function () {
        if (this.watchMutations) {
          return this.observer.observe(this.el, {
            childList: true
          });
        }
      };

      OdometerPriv.prototype.stopWatchingMutations = function () {
        return (this.observer && _ref.disconnect()) || void 0;
      };

      OdometerPriv.prototype.cleanValue = function (val) {
        if (typeof val === 'string') {
          val = val.replace(this.format.radix || '.', '<radix>');
          val = val.replace(/[.,]/g, '');
          val = val.replace('<radix>', '.');
          val = parseFloat(val, 10) || 0;
        }
        return round(val, this.format.precision);
      };

      OdometerPriv.prototype.bindTransitionEnd = function () {
        var event, renderEnqueued, _i, _len, _results,
          _this = this;
        if (this.transitionEndBound) {
          return;
        }
        this.transitionEndBound = true;
        renderEnqueued = false;
        let events = TRANSITION_END_EVENTS.split(' ');
        _results = [];
        for (_i = 0, _len = events.length; _i < _len; _i++) {
          event = events[_i];
          _results.push(this.el.addEventListener(event, function () {
            if (!renderEnqueued) {
              renderEnqueued = true;
              setTimeout(function () {
                _this.render();
                renderEnqueued = false;
                return trigger(_this.el, 'odometerdone');
              }, 0);
            }
            return true;
          }, false));
        }
        return _results;
      };

      OdometerPriv.prototype.resetFormat = function () {
        var format, fractional, parsed, precision, radix, repeating;
        format = this.options.format || DIGIT_FORMAT;
        parsed = FORMAT_PARSER.exec(format);
        if (!parsed) {
          throw new Error("OdometerPriv: Unparsable digit format");
        }
        let digits = parsed.slice(1, 4);
        repeating = digits[0];
        radix = digits[1];
        fractional = digits[2];
        precision = (fractional === null ? void 0 : fractional.length) || 0;
        this.format = {
          repeating: repeating,
          radix: radix,
          precision: precision
        };
        return this.format;
      };

      OdometerPriv.prototype.render = function (value) {
        var _i, _len;
        if (value == null) {
          value = this.value;
        }
        this.stopWatchingMutations();
        this.resetFormat();
        this.inside.innerHTML = '';
        let theme = this.options.theme;
        let classes = this.el.className.split(' ');
        let newClasses = [];
        for (_i = 0, _len = classes.length; _i < _len; _i++) {
          let cls = classes[_i];
          if (!cls.length) {
            continue;
          }
          let match = /^odometer-theme-(.+)$/.exec(cls)
          if (match) {
            theme = match[1];
            continue;
          }
          if (/^odometer(-|$)/.test(cls)) {
            continue;
          }
          newClasses.push(cls);
        }
        newClasses.push('odometer');
        if (!TRANSITION_SUPPORT) {
          newClasses.push('odometer-no-transitions');
        }
        if (theme) {
          newClasses.push("odometer-theme-" + theme);
        } else {
          newClasses.push("odometer-auto-theme");
        }
        this.el.className = newClasses.join(' ');
        this.ribbons = {};
        this.formatDigits(value);
        return this.startWatchingMutations();
      };

      OdometerPriv.prototype.formatDigits = function (value) {
        var digit, valueDigit, valueString, wholePart, _i, _j, _len, _len1, digits;
        this.digits = [];
        if (this.options.formatFunction) {
          valueString = this.options.formatFunction(value);
          digits = valueString.split('').reverse();
          for (_i = 0, _len = digits.length; _i < _len; _i++) {
            valueDigit = digits[_i];
            if (valueDigit.match(/0-9/)) {
              digit = this.renderDigit();
              digit.querySelector('.odometer-value').innerHTML = valueDigit;
              this.digits.push(digit);
              this.insertDigit(digit);
            } else {
              this.addSpacer(valueDigit);
            }
          }
        } else {
          wholePart = !this.format.precision || !fractionalPart(value) || false;
          digits = value.toString().split('').reverse();
          for (_j = 0, _len1 = digits.length; _j < _len1; _j++) {
            digit = digits[_j];
            if (digit === '.') {
              wholePart = true;
            }
            this.addDigit(digit, wholePart);
          }
        }
      };

      OdometerPriv.prototype.update = function (newValue) {
        let _this = this;
        newValue = this.cleanValue(newValue);
        let diff = newValue - this.value
        if (!diff) {
          return;
        }
        removeClass(this.el, 'odometer-animating-up odometer-animating-down odometer-animating');
        if (diff > 0) {
          addClass(this.el, 'odometer-animating-up');
        } else {
          addClass(this.el, 'odometer-animating-down');
        }
        this.stopWatchingMutations();
        this.animate(newValue);
        this.startWatchingMutations();
        setTimeout(function () {
          return addClass(_this.el, 'odometer-animating');
        }, 0);
        this.value = newValue;
        return this.value;
      };

      OdometerPriv.prototype.renderDigit = function () {
        return createFromHTML(DIGIT_HTML);
      };

      OdometerPriv.prototype.insertDigit = function (digit, before) {
        if (before != null) {
          return this.inside.insertBefore(digit, before);
        }
        if (!this.inside.children.length) {
          return this.inside.appendChild(digit);
        }
        return this.inside.insertBefore(digit, this.inside.children[0]);
      };

      OdometerPriv.prototype.addSpacer = function (chr, before, extraClasses) {
        var spacer = createFromHTML(FORMAT_MARK_HTML);
        spacer.innerHTML = chr;
        if (extraClasses) {
          addClass(spacer, extraClasses);
        }
        return this.insertDigit(spacer, before);
      };

      OdometerPriv.prototype.addDigit = function (value, repeating) {
        var chr, digit, resetted;
        if (repeating == null) {
          repeating = true;
        }
        if (value === '-') {
          return this.addSpacer(value, null, 'odometer-negation-mark');
        }
        if (value === '.') {
          let radix = this.format.radix;
          return this.addSpacer(radix != null ? radix : '.', null, 'odometer-radix-mark');
        }
        if (repeating) {
          resetted = false;
          while (true) {
            if (!this.format.repeating.length) {
              if (resetted) {
                throw new Error("Bad odometer format without digits");
              }
              this.resetFormat();
              resetted = true;
            }
            chr = this.format.repeating[this.format.repeating.length - 1];
            this.format.repeating = this.format.repeating.substring(0, this.format.repeating.length - 1);
            if (chr === 'd') {
              break;
            }
            this.addSpacer(chr);
          }
        }
        digit = this.renderDigit();
        digit.querySelector('.odometer-value').innerHTML = value;
        this.digits.push(digit);
        return this.insertDigit(digit);
      };

      OdometerPriv.prototype.animate = function (newValue) {
        if (!TRANSITION_SUPPORT || this.options.animation === 'count') {
          return this.animateCount(newValue);
        } else {
          return this.animateSlide(newValue);
        }
      };

      OdometerPriv.prototype.animateCount = function (newValue) {
        var cur, diff = +newValue - this.value, last, start, tick,
          _this = this;
        if (!diff) {
          return;
        }
        start = last = now();
        cur = this.value;
        tick = function () {
          var delta, dist, fraction;
          if ((now() - start) > _this.options.duration) {
            _this.value = newValue;
            _this.render();
            trigger(_this.el, 'odometerdone');
            return;
          }
          delta = now() - last;
          if (delta > COUNT_MS_PER_FRAME) {
            last = now();
            fraction = delta / _this.options.duration;
            dist = diff * fraction;
            cur += dist;
            _this.render(Math.round(cur));
          }
          if (requestAnimationFrame != null) {
            return requestAnimationFrame(tick);
          }
          return setTimeout(tick, COUNT_MS_PER_FRAME);
        };
        return tick();
      };

      OdometerPriv.prototype.getDigitCount = function () {
        var i, max, value, values, _i, _len;
        values = 1 <= arguments.length ? __slice.call(arguments, 0) : [];
        for (i = _i = 0, _len = values.length; _i < _len; i = ++_i) {
          value = values[i];
          values[i] = Math.abs(value);
        }
        max = Math.max.apply(Math, values);
        return Math.ceil(Math.log(max + 1) / Math.log(10));
      };

      OdometerPriv.prototype.getFractionalDigitCount = function () {
        var i, parser, parts, value, values, _i, _len;
        values = 1 <= arguments.length ? __slice.call(arguments, 0) : [];
        parser = /^\-?\d*\.(\d*?)0*$/;
        for (i = _i = 0, _len = values.length; _i < _len; i = ++_i) {
          value = values[i];
          values[i] = value.toString();
          parts = parser.exec(values[i]);
          if (parts == null) {
            values[i] = 0;
          } else {
            values[i] = parts[1].length;
          }
        }
        return Math.max.apply(Math, values);
      };

      OdometerPriv.prototype.resetDigits = function () {
        this.digits = [];
        this.ribbons = [];
        this.inside.innerHTML = '';
        return this.resetFormat();
      };

      OdometerPriv.prototype.animateSlide = function (newValue) {
        var boosted, cur, digitCount, digits, dist, end, fractionalCount, frame, frames, i, incr, j, mark, numEl, oldValue, start, _i, _k, _l, _len, _len1, _len2, _m, _results;
        oldValue = this.value;
        fractionalCount = this.getFractionalDigitCount(oldValue, newValue);
        if (fractionalCount) {
          newValue = newValue * Math.pow(10, fractionalCount);
          oldValue = oldValue * Math.pow(10, fractionalCount);
        }
        let diff = newValue - oldValue
        if (!diff) {
          return;
        }
        this.bindTransitionEnd();
        digitCount = this.getDigitCount(oldValue, newValue);
        digits = [];
        boosted = 0;
        for (i = _i = 0; 0 <= digitCount ? _i < digitCount : _i > digitCount; i = 0 <= digitCount ? ++_i : --_i) {
          start = truncate(oldValue / Math.pow(10, digitCount - i - 1));
          end = truncate(newValue / Math.pow(10, digitCount - i - 1));
          dist = end - start;
          if (Math.abs(dist) > this.MAX_VALUES) {
            frames = [];
            incr = dist / (this.MAX_VALUES + this.MAX_VALUES * boosted * DIGIT_SPEEDBOOST);
            cur = start;
            if (dist > 0) {
              while (cur < end) {
                frames.push(Math.round(cur));
                cur += incr;
              }
            } else if (dist < 0) {
              while (cur > end) {
                frames.push(Math.round(cur));
                cur += incr;
              }
            }
            if (frames[frames.length - 1] !== end) {
              frames.push(end);
            }
            boosted++;
          } else {
            frames = (function () {
              _results = [];
              for (let _j = start; start <= end ? _j <= end : _j >= end; start <= end ? _j++ : _j--) { _results.push(_j); }
              return _results;
            }).apply(this);
          }
          for (i = _k = 0, _len = frames.length; _k < _len; i = ++_k) {
            frame = frames[i];
            frames[i] = Math.abs(frame % 10);
          }
          digits.push(frames);
        }
        this.resetDigits();
        digits.reverse();
        for (i = _l = 0, _len1 = digits.length; _l < _len1; i = ++_l) {
          frames = digits[i];
          if (!this.digits[i]) {
            this.addDigit(' ', i >= fractionalCount);
          }
          let _base = this.ribbons;
          if (_base[i] == null) {
            _base[i] = this.digits[i].querySelector('.odometer-ribbon-inner');
          }
          this.ribbons[i].innerHTML = '';
          if (diff < 0) {
            frames.reverse();
          }
          for (j = _m = 0, _len2 = frames.length; _m < _len2; j = ++_m) {
            frame = frames[j];
            numEl = document.createElement('div');
            numEl.className = 'odometer-value';
            numEl.innerHTML = frame;
            this.ribbons[i].appendChild(numEl);
            if (j === frames.length - 1) {
              addClass(numEl, 'odometer-last-value');
            }
            if (j === 0) {
              addClass(numEl, 'odometer-first-value');
            }
          }
        }
        if (start < 0) {
          this.addDigit('-');
        }
        mark = this.inside.querySelector('.odometer-radix-mark');
        if (mark != null) {
          mark.parent.removeChild(mark);
        }
        if (fractionalCount) {
          return this.addSpacer(this.format.radix, this.digits[fractionalCount - 1], 'odometer-radix-mark');
        }
      };

      return OdometerPriv;

    })();
    Odometer = exportOdometer;

    Odometer.options = window.odometerOptions || {};

    setTimeout(function () {
      if (window.odometerOptions) {
        let _results = [];
        let options = window.odometerOptions;
        for (let k in options) {
          let v = options[k];
          let _base = Odometer.options[k];
          _results.push(_base === null ? v : _base);
        }
        return _results;
      }
    }, 0);

    Odometer.init = function () {
      var el, elements, _i, _len, _results;
      if (document.querySelectorAll == null) {
        return;
      }
      elements = document.querySelectorAll(Odometer.options.selector || '.odometer');
      _results = [];
      for (_i = 0, _len = elements.length; _i < _len; _i++) {
        el = elements[_i];
        let innerText = el.innerText;
        el.odometer = new Odometer({
          el: el,
          value: innerText !== null ? innerText : el.textContent
        });
        _results.push(el.odometer);
      }
      return _results;
    };

    if ((((_ref1 = document.documentElement) != null ? _ref1.doScroll : void 0) != null) && (document.createEventObject != null)) {
      _old = document.onreadystatechange;
      document.onreadystatechange = function () {
        if (document.readyState === 'complete' && Odometer.options.auto !== false) {
          Odometer.init();
        }
        return _old != null ? _old.apply(this, arguments) : void 0;
      };
    } else {
      document.addEventListener('DOMContentLoaded', function () {
        if (Odometer.options.auto !== false) {
          return Odometer.init();
        }
      }, false);
    }

    return Odometer;

  }).call(this);
});