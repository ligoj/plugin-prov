define(['d3', 'jquery'], function (d3) {
	var current = {
		calcPercent: function (percent) {
			return [percent, 100 - percent];
		},

		create: function (selector, value, width, height, duration, transition) {
			duration = duration || 1500;
			transition = transition || 200;
			width = width || window.innerWidth - 20;
			height = height || window.innerHeight - 20;

			var context = {};
			var radius = Math.min(width, height) / 3;
			context.pie = d3.pie().sort(null);
			current.format = d3.format(".0%");

			context.arc = d3.arc()
				.innerRadius(radius * .8)
				.outerRadius(radius);

			var svg = d3.select(selector).append("svg")
				.attr("width", width)
				.attr("height", height)
				.append("g")
				.attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");

			context.path = svg.selectAll("path")
				.data(context.pie(current.calcPercent(0)))
				.enter().append("path")
				.attr("class", function (d, i) {
					return "color" + i
				})
				.attr("d", context.arc)
				.each(function (d) {
					this._current = d;
				});

			context.text = svg.append("text")
				.attr("text-anchor", "middle")
				.attr("dy", ".3em");


			context.progress = 0;
			var timeout = setTimeout(function () {
				clearTimeout(timeout);
				current.update(context, value);
			}, 200);

			return context;
		},

		update: function (context, value) {
			context.path = context.path.data(context.pie(current.calcPercent(value)));
			context.path.transition().duration(context.duration).attrTween("d", function (a) {
				var i = d3.interpolate(this._current, a);
				var i2 = d3.interpolate(context.progress, value)
				this._current = i(0);
				return function (t) {
					context.text.text(current.format(i2(t) / 100));
					return context.arc(i(t));
				};
			});
		}
	};
	return current;
});