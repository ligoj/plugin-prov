/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 * @see https://bl.ocks.org/martinjc/7fa5deb1782da2fc6da15c3fad02c88b
 */
define(['d3', 'jquery'], function (d3) {
    var current = {
        xscale: null,
        svg: null,
        width:0,
        height:0,

        create: function (selector, width, height, initAmount) {
            var margin = {
                top: 20,
                left: 30,
                right: 30,
                bottom: 30
            };

            current.svg = d3.select(selector)
                .append("svg")
                .attr('width', width)
                .attr('height', height)
                .append("g")
                .attr("transform", "translate(" + margin.top + "," + margin.left + ")");

            width = width - margin.left - margin.right;
            height = height - margin.top - margin.bottom;
            current.height = height;
           	current.width = width;

            var yscale = d3.scaleLinear()
                .range([height, 0])
                .domain([0, initAmount]);
            current.yscale = yscale;

            var xscale = d3.scaleBand()
                .range([0, width])
                .padding(0.1);
            current.xscale = xscale;
            current.svg.append('g')
                .attr('transform', 'translate(0, ' + (height) + ')')
                .attr('class', 'x axis');
            current.svg.append('g')
                .attr('class', 'y axis');
        },

        update: function (exampleData) {
            var duration = 1000;
            var maxValue = d3.max(exampleData);
            var xscale = current.xscale;
            var yscale = current.yscale;
            var svg = current.svg;
            var xaxis = d3.axisBottom(xscale);
            var yaxis = d3.axisLeft(yscale);
            var height = current.height;
            var width = current.width;
            xscale.domain(d3.range(exampleData.length));
            yscale.domain([0, maxValue]);
            var bars = svg.selectAll(".bar")
                .data(exampleData);

            bars
                .enter()
                .append('rect')
                .attr('class', 'bar')
                .attr("fill", "red")
                .attr('width', xscale.bandwidth())
                .attr('height', 0)
                .attr('y', height)
                .merge(bars)
                .transition()
                .duration(duration)
                .attr("height", function (d, i) {
                    return height - yscale(d);
                })
                .attr("y", function (d, i) {
                    return yscale(d);
                })
                .attr("width", xscale.bandwidth())
                .attr("x", function (d, i) {
                    return xscale(i);
                });

            bars
                .exit()
                .transition()
                .duration(duration)
                .attr('height', 0)
                .attr('y', height)
                .remove();

            var labels = svg.selectAll('.label')
                .data(exampleData);

            var new_labels = labels
                .enter()
                .append('text')
                .attr('class', 'label')
                .attr('opacity', 0)
                .attr('y', height)
                .attr('fill', 'white')
                .attr('text-anchor', 'middle')

            new_labels.merge(labels)
                .transition()
                .duration(duration)
                .attr('opacity', 1)
                .attr('x', function (d, i) {
                    return xscale(i) + xscale.bandwidth() / 2;
                })
                .attr('y', function (d) {
                    return yscale(d) + 20;
                })
                .text(function (d) {
                    return d;
                });

            labels
                .exit()
                .transition()
                .duration(duration)
                .attr('y', height)
                .attr('opacity', 0)
                .remove();

            svg.select('.x.axis')
                .transition()
                .duration(duration)
                .call(xaxis);

            svg.select('.y.axis')
                .transition()
                .duration(duration)
                .call(yaxis);

        }
    };
    return current;
});
