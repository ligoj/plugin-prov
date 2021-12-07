/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(['d3', 'jquery'], function (d3, $) {
	var sunburst = {};
	sunburst.arcTween = function (newAngle) {
		return function (d) {
			var interpolate = d3.interpolate(d.endAngle, newAngle);
			return function (t) {
				d.endAngle = interpolate(t);
				return arc(d);
			};
		};
	};

	sunburst.init = function ($element, data, sort, tooltipFunction, colorScheme) {
		// short the data by weight and short the color with ressource(instance/database...)
		var i=0;
		data.children.forEach(element => {	
			element.color= colorScheme[i];
			i++;
		});
		data.children.sort(function compare(a, b) {
			if (a.value < b.value)
			   return -1;
			if (a.value > b.value )
			   return 1;
			return 0;
		});
		i=0;
		data.children.forEach(element => {	
			colorScheme[i]=element.color;
			i++;
		});
		var width = 200;
		var height = 200;
		var radius = (Math.min(width, height) / 2) - 10;
		var x = d3.scaleLinear().range([0, 2 * Math.PI]);
		var y = d3.scaleSqrt().range([0, radius]);
		var color = function (d) {

			// This function builds the total
			// color palette incrementally so
			// we don't have to iterate through
			// the entire data structure.

			// We're going to need a color scale.
			// Normally we'll distribute the colors
			// in the scale to child nodes.
			var colors;

			// The root node is special since
			// we have to seed it with our
			// desired palette.
			if (!d.parent) {

				// Create a categorical color
				// scale to use both for the
				// root node's immediate
				// children. We're using the
				// 10-color predefined scale,
				// so set the domain to be
				// [0, ... 9] to ensure that
				// we can predictably generate
				// correct individual colors.
				colors = d3.scaleOrdinal(colorScheme)
					.domain(d3.range(0, 10));

				// White for the root node
				// itself.
				d.color = "#fff";

			} else if (d.children) {

				// Since this isn't the root node,
				// we construct the scale from the
				// node's assigned color. Our scale
				// will range from darker than the
				// node's color to brighter than the
				// node's color.
				var startColor = d3.hcl(d.color)
					.darker(),
					endColor = d3.hcl(d.color)
						.brighter();

				// Create the scale
				colors = d3.scaleLinear()
					.interpolate(d3.interpolateHcl)
					.range([
						startColor.toString(),
						endColor.toString()
					])
					.domain([0, d.children.length + 1]);

			}

			if (d.children) {

				// Now distribute those colors to
				// the child nodes. We want to do
				// it in sorted order, so we'll
				// have to calculate that. Because
				// JavaScript sorts arrays in place,
				// we use a mapped version.
				d.children.map(function (child, i) {
					return {
						value: child.value,
						idx: i
					};
				}).forEach(function (child, i) {
					d.children[child.idx].color = colors(i);
				});
			}

			return d.color;
		};
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

		function click(e, d) {
			// depth => d.depth + 1
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
				.attrTween('d', function (a) {
					return function () {
						return arc(a);
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
		function mouseout() {
			// Transition each segment to full opacity and then reactivate it.
			svg.selectAll('path').style('opacity', 1);
		}

		function mouseover(e, d) {
			var sequenceArray = getAncestors(d);

			// Fade all the segments.
			svg.selectAll('path').style('opacity', 0.3);

			// Then highlight only those that are an ancestor of the current segment.
			svg.selectAll('path')
				.filter(function (node) {
					return (sequenceArray.indexOf(node) >= 0);
				})
				.attr('class', 'sunburst-part')
				.style('opacity', 1);
		}

		sunburst.update = function (d) {
			sunburst.path
				.data(partition.value(d).nodes)
				.transition()
				.duration(1500)
				.attrTween('d', sunburst.arcTween);
		};

		/* For the drop shadow filter... */
		var defs = svg.append("defs");
		var filter = defs.append("filter")
			.attr("id", "sunburst-filter");
		filter.append("feGaussianBlur")
			.attr("in", "SourceAlpha")
			.attr("stdDeviation", 4)
			.attr("result", "blur");
		filter.append("feOffset")
			.attr("in", "blur")
			.attr("dx", 2)
			.attr("dy", 2)
			.attr("result", "offsetBlur");

		var feMerge = filter.append("feMerge");
		feMerge.append("feMergeNode")
			.attr("in", "offsetBlur");
		feMerge.append("feMergeNode")
			.attr("in", "SourceGraphic");

		function tooltip() {
			if ($('body').has('.d3-tooltip.tooltip-inner').length === 0) {
				return d3.select('body')
					.append('div')
					.attr('class', 'tooltip d3-tooltip tooltip-inner');
			} else {
				return d3.select('body .d3-tooltip.tooltip-inner');
			}
		}

		var root = d3.hierarchy(data);
		root.sum(function (d) {
			return d.size;
		}).sort(sort);

		svg.selectAll('path')
			.data(partition(root).descendants())
			.enter().append('g').attr('class', 'node');

		sunburst.path = svg.selectAll('.node')
			.append('path')
			.attr('d', arc)
			.attr("fill-rule", "evenodd")
			.attr('class', 'sunburst-part')
			.style('fill', color)
			.on('click', click)
			.on('mouseover', function (e, d) {
				mouseover(e, d);
				tooltip().html(tooltipFunction(d.data, d)).style('visibility', 'visible');
			})
			.on('mousemove', e => tooltip().style('top', (e.pageY - 10) + 'px').style('left', (e.pageX + 10) + 'px'))
			.on('mouseout', function () {
				mouseout();
				return tooltip().style('visibility', 'hidden');
			});
	};
	return sunburst;
});
