define(['d3'], function (d3) {
	var sunburst = {};
	sunburst.update = function (data) {
		sunburst.path
			.data(partition.value(data).nodes)
			.transition()
			.duration(1500)
			.attrTween("d", arcTween);

	};
	sunburst.init = function ($element, data) {
		var width = 300;
		var height = 300;
		var radius = (Math.min(width, height) / 2) - 10;
		var formatNumber = d3.format(",d");
		var x = d3.scaleLinear().range([0, 2 * Math.PI]);
		var y = d3.scaleSqrt().range([0, radius]);
		var color = d3.scaleOrdinal(d3.schemeCategory20);
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
		var svg = d3.select($element).append("svg")
			.attr("width", width)
			.attr("height", height)
			.append("g")
			.attr("transform", "translate(" + width / 2 + "," + (height / 2) + ")");

		var tooltip = d3.select("body")
			.append("div")
			.attr('class', 'tooltip d3-tooltip tooltip-inner');

		var root = d3.hierarchy(data);
		root.sum(function (d) {
			return d.size;
		});

		svg.selectAll("path")
			.data(partition(root).descendants())
			.enter().append("g").attr("class", "node");

		sunburst.path = svg.selectAll(".node")
			.append("path")
			.attr("d", arc)
			.style("fill", function (d) {
				return color((d.children ? d : d.parent).data.name);
			})
			.on("click", click)
			.on("mouseover", function (d) {
				return tooltip.text(d.data.name + ', cost: ' + formatManager.formatCost(d.data.size || d.data.value, 3, '$')).style("visibility", "visible");
			})
			.on("mousemove", function () {
				return tooltip.style("top", (d3.event.pageY - 10) + "px").style("left", (d3.event.pageX + 10) + "px");
			})
			.on("mouseout", function () {
				return tooltip.style("visibility", "hidden");
			});

		function click(d) {
			var depth = d.depth + 1;
			svg.transition()
				.duration(750)
				.tween("scale", function () {
					var xd = d3.interpolate(x.domain(), [d.x0, d.x1]),
						yd = d3.interpolate(y.domain(), [d.y0, 1]),
						yr = d3.interpolate(y.range(), [d.y0 ? 20 : 0, radius]);
					return function (t) {
						x.domain(xd(t));
						y.domain(yd(t)).range(yr(t));
					};
				})
				.selectAll("path")
				.attrTween("d", function (d) {
					return function () {
						return arc(d);
					};
				});
		}
		d3.select(self.frameElement).style("height", height + "px");
	};
	return sunburst;
});
