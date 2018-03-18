define(['d3', 'jquery'], function (d3, jQuery) {
	var that = {
		radius: 100,
		thickness: 15,
		oldData: '',
		init: function (selector, data, radius, thickness) {
			var preparedData = this.setData(jQuery.extend(true, {}, data));
			this.oldData = preparedData;
			this.radius = radius || 100;
			this.thickness = thickness || 15;
			this.setup(selector, preparedData);
		},
		update: function (data) {
			var preparedData = this.setData(jQuery.extend(true, {}, data));
			this.animate(preparedData);
			this.oldData = preparedData;
		},
		animate: function (data) {
			var chart = d3.select('.arcchart');
			this.generateArcs(chart, data);
		},
		setData: function (data) {
			var diameter = 2 * Math.PI * this.radius;
			var localData = [];
			var segmentValueSum = 100;
			$.each(data.segments, function (ri, value) {
				var segmentValue = value.value;
				var fraction = segmentValue / segmentValueSum;
				var arcBatchLength = fraction * 2 * Math.PI;
				var arcPartition = arcBatchLength;
				var startAngle = 0;
				var endAngle = arcPartition;
				data.segments[ri].startAngle = startAngle;
				data.segments[ri].endAngle = endAngle;
				data.segments[ri].index = ri;
			});

			localData.push(data.segments);
			return localData[0];
		},

		// Restore everything to full opacity when moving off the visualization.
		mouseout: function () {
			// Transition each segment to full opacity and then reactivate it.
			d3.selectAll('path').style('opacity', 1);
		},

		mouseover: function (d) {
			// Fade all the segments.
			d3.selectAll('path').style('opacity', 0.3);

			// Then highlight only those that are an ancestor of the current segment.
			d3.selectAll('path')
				.filter(function (node) {
					return d === node;
				})
				.attr('class', 'sunburst-part')
				.style('opacity', 1);
		},

		arcTween: function (b) {
			var prev = JSON.parse(JSON.stringify(b));
			prev.endAngle = b.previousEndAngle;
			var i = d3.interpolate(prev, b);

			return function (t) {
				return that.getArc()(i(t));
			};
		},

		generateArcs: function (chart, data) {
			//append previous value to it.			
			$.each(data, function (index, value) {
				if (that.oldData[index] != undefined) {
					data[index].previousEndAngle = that.oldData[index].endAngle;
				} else {
					data[index].previousEndAngle = 0;
				}
			});

			var path = chart.selectAll('path.slice')
				.data(data)
				.on('mouseover', function (d) {
					that.mouseover(d);
					that.tooltip.html(d.tooltip).style('visibility', 'visible');
				})
				.on('mousemove', function () {
					return that.tooltip.style('top', (d3.event.pageY - 10) + 'px').style('left', (d3.event.pageX + 10) + 'px');
				})
				.on('mouseout', function (d) {
					that.mouseout(d);
					return that.tooltip.style('visibility', 'hidden');
				});

			var gs = path.enter().append('g');

			// Add the background arc
			gs.append('svg:path')
				.style('fill', '#ddd')
				.attr('d', that.getArc(true));

			var t = d3.transition()
				.duration(750);

			// Add the animated arc
			gs.append('svg:path')
				.attr('class', 'slice')
				.style('fill', function (d, i) {
					return d.color;
				}).transition(t)
				.attrTween('d', that.arcTween);

			path.transition(t)
				.attrTween('d', that.arcTween);

			gs.exit().remove();
		},
		setup: function (selector, data) {
			var chart = d3.select(selector).append('svg:svg')
				.attr('class', 'chart')
				.attr('width', this.radius * 2 + 15)
				.attr('height', this.radius * 2)
				.append('svg:g')
				.attr('class', 'arcchart')
				.attr('transform', 'translate(' + (this.radius + 5) + ',' + this.radius + ')');

			/* For the drop shadow filter... */
			var defs = chart.append('defs');
			var filter = defs.append('filter')
				.attr('id', 'arc-filter');
			filter.append('feGaussianBlur')
				.attr('in', 'SourceAlpha')
				.attr('stdDeviation', 3)
				.attr('result', 'blur');
			filter.append('feOffset')
				.attr('in', 'blur')
				.attr('dx', 2)
				.attr('dy', 2)
				.attr('result', 'offsetBlur');

			var feMerge = filter.append('feMerge');
			feMerge.append('feMergeNode')
				.attr('in', 'offsetBlur');
			feMerge.append('feMergeNode')
				.attr('in', 'SourceGraphic');

			this.tooltip = d3.select('body')
				.append('div')
				.attr('class', 'tooltip d3-tooltip tooltip-inner')
				.html('<i class="fas fa-fw fa-user"></i>');

			this.generateArcs(chart, data);
		},
		getArc: function (background) {
			var radiusArray = [100, 80];

			function getRadiusRing(i) {
				return that.radius - (i * 20);
			}

			var arc = d3.arc()
				.innerRadius(function (d) {
					return getRadiusRing(d.index) - (background ? 3 : 0);
				})
				.outerRadius(function (d) {
					return getRadiusRing(d.index) - that.thickness - (background ? 3 : 0);
				})
				.startAngle(function (d, i) {
					return -5 / 8 * Math.PI - (background ? 0.04 : 0);
				})
				.endAngle(function (d, i) {
					return -5 / 8 * Math.PI + (background ? 5 / 4 * Math.PI + 0.04 : (d.endAngle * 5 / 8));
				});
			return arc;
		}
	};
	return that;
});
