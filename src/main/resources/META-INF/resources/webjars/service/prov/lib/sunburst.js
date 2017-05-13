define(['d3'], function (d3) {
	var sunburst = {};
	var colors = function (s) {
		return s.match(/.{6}/g).map(function (x) {
			return '#' + x;
		});
	};
	sunburst.update = function (data) {
		sunburst.path
			.data(partition.value(data).nodes)
			.transition()
			.duration(1500)
			.attrTween('d', arcTween);

	};
	sunburst.init = function ($element, data) {
		var width = 300;
		var height = 300;
		var radius = (Math.min(width, height) / 2) - 10;
		var formatNumber = d3.format(',d');
		var x = d3.scaleLinear().range([0, 2 * Math.PI]);
		var y = d3.scaleSqrt().range([0, radius]);
		var color = d3.scaleOrdinal(colors('e41a1c377eb84daf4a984ea3ff7f00ffff33a65628f781bf999999'));
		var partition = d3.partition();
		var arc = d3.arc()
			.startAngle(function (d) {
				return Math.max(0, Math.min(2 * Math.PI, x(d.x0)));
			}).endAngle(function (d) {
				return Math.max(0, Math.min(2 * Math.PI, x(d.x1)));
			}).innerRadius(function (d) {
				return Math.max(0, y(d.y0));
			}).outerRadius(function (d) {
				return Math.max(0, y(d.y1));
			});

		$($element).empty();
		var svg = d3.select($element).append('svg')
			.attr('width', width)
			.attr('height', height)
			.append('g')
			.attr('transform', 'translate(' + width / 2 + ',' + (height / 2) + ')');

		var tooltip = d3.select('body')
			.append('div')
			.attr('class', 'tooltip d3-tooltip tooltip-inner')
			.html('<i class="fa icon-fixed-width icon-user"></i>');

		var root = d3.hierarchy(data);
		root.sum(function (d) {
			return d.size;
		});

		svg.selectAll('path')
			.data(partition(root).descendants())
			.enter().append('g').attr('class', 'node');

		sunburst.path = svg.selectAll('.node')
			.append('path')
			.attr('d', arc)
			.style('fill', function (d) {
				return color((d.children ? d : d.parent).data.name);
			})
			.on('click', click)
			.on('mouseover', function (d) {
				mouseover(d);
				tooltip.html(d.data.name + ', cost: ' + formatManager.formatCost(d.data.size || d.data.value, 3, '$')).style('visibility', 'visible');
			})
			.on('mousemove', function () {
				return tooltip.style('top', (d3.event.pageY - 10) + 'px').style('left', (d3.event.pageX + 10) + 'px');
			})
			.on('mouseout', function (d) {
				mouseout(d);
				return tooltip.style('visibility', 'hidden');
			});

		function click(d) {
			var depth = d.depth + 1;
			svg.transition()
				.duration(750)
				.tween('scale', function () {
					var xd = d3.interpolate(x.domain(), [d.x0, d.x1]),
						yd = d3.interpolate(y.domain(), [d.y0, 1]),
						yr = d3.interpolate(y.range(), [d.y0 ? 20 : 0, radius]);
					return function (t) {
						x.domain(xd(t));
						y.domain(yd(t)).range(yr(t));
					};
				})
				.selectAll('path')
				.attrTween('d', function (d) {
					return function () {
						return arc(d);
					};
				});
		}

		// Given a node in a partition layout, return an array of all of its ancestor
		// nodes, highest first, but excluding the root.
		function getAncestors(node) {
			var path = [];
			var current = node;
			while (current.parent) {
				path.unshift(current);
				current = current.parent;
			}
			return path;
		}

		// Restore everything to full opacity when moving off the visualization.
		function mouseout(d) {
			// Transition each segment to full opacity and then reactivate it.
			d3.selectAll('path').style('opacity', 1);
		}

		function mouseover(d) {
			var sequenceArray = getAncestors(d);

			// Fade all the segments.
			d3.selectAll('path').style('opacity', 0.3);

			// Then highlight only those that are an ancestor of the current segment.
			svg.selectAll('path')
				.filter(function (node) {
					return (sequenceArray.indexOf(node) >= 0);
				})
				.style('opacity', 1);
		}
		d3.select(self.frameElement).style('height', height + 'px');
	};
	return sunburst;
});
