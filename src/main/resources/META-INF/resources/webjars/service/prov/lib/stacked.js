/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 * @see https://bl.ocks.org/martinjc/7fa5deb1782da2fc6da15c3fad02c88b
 */
define(['d3', 'jquery'], function (d3) {
    return (function () {
        var params = {};
        var marginTop = 20;

        // formatting Data to a more d3-friendly format
        // extracting binNames and clusterNames
        function formatData(data, sort) {
            var clusterNames = d3.keys(data[0]).filter(k => k !== 'date').sort(sort);
            var binNames = [];
            var blockData = [];
            let ranges = {};
            clusterNames.forEach(k => ranges[k] = { min: Number.MAX_VALUE, max: -1 });
            for (var i = 0; i < data.length; i++) {
                var y = 0;
                binNames.push(data[i].date);
                for (var j = 0; j < clusterNames.length; j++) {
                    let key = clusterNames[j];
                    let value = parseFloat(data[i][key]);
                    ranges[key].min = Math.min(ranges[key].min, value);
                    ranges[key].max = Math.max(ranges[key].max, value);
                    y += value;
                    blockData.push({
                        'y0': y,
                        'y': y,
                        'height0': value,
                        'height': value,
                        'x': data[i].date,
                        'x-index': i,
                        'cluster-index': j,
                        'cluster': key
                    });
                }
            }
            return {
                blockData: blockData,
                binNames: binNames,
                ranges: ranges,
                clusterNames: clusterNames
            };
        }

        function updateData() {
            var input = params.input,
                formattedData = formatData(input.data, params.sort),
                blockData = formattedData.blockData,
                clusterNames = formattedData.clusterNames;
            params.clusterNames = formattedData.clusterNames;
            params.binNames = formattedData.binNames;
            params.ranges = formattedData.ranges;
            params.blockData = blockData;
            params.heights = setUpHeights(clusterNames, blockData);
            params.maxPerBin = setUpMax(clusterNames, blockData);
            params.scales = initializeScales(params.width, params.height);
            params.filteredClusterNames = params.clusterNames.filter(k => params.ranges[k].max > 0);
            return formattedData;
        }

        function getLegendY(d) {
            var i = params.filteredClusterNames.indexOf(d);
            if (i > params.filteredClusterNames.indexOf(params.chosen.cluster)) {
                return choice(params.chosen.cluster, d, 20 * (params.filteredClusterNames.length - i), marginTop, marginTop);
            }
            return choice(params.chosen.cluster, d, 20 * (params.filteredClusterNames.length - i), marginTop, marginTop);
        }

        function initialize() {

            // unpacking params
            var canvas = params.canvas;

            // unpacking canvas
            var svg = canvas.svg,
                margin = canvas.margin,
                height = params.height = canvas.height;
            params.width = canvas.width;

            // processing Data and extracting binNames and clusterNames
            var formattedData = updateData(),
                blockData = formattedData.blockData,
                clusterNames = formattedData.clusterNames;

            // initialize color
            var color = setUpColors().domain(clusterNames);
            params.color = color;

            // initialize scales and axis
            var scales = params.scales,
                x = scales.x,
                y = scales.y;

            x.domain(formattedData.binNames);
            y.domain([0, d3.max(blockData, d => d.y)]);

            initializeAxis(svg, x, y, height);

            // initialize bars
            var bar = params.bar = svg.selectAll('.bar')
                .data(blockData)
                .enter().append('g')
                .attr('class', 'bar');

            bar.append('rect')
                .attr('x', d => x(d.x))
                .attr('y', () => y(0))
                .attr('width', x.bandwidth())
                .attr('height', 0)
                .attr('fill', d => color(d.cluster));

            // variable to store chosen cluster when bar is clicked
            var chosen = params.chosen = {
                cluster: null
            };

            // initialize legend
            var legend = params.legend = svg.selectAll('.legend')
                .data(params.filteredClusterNames)
                .enter().append('g')
                .attr('class', 'legend')
                .on('click', function (d) {
                    chosen.cluster = chosen.cluster === d ? null : d;
                    refresh();
                });

            legend.append('rect')
                .attr('x', margin.left - 63)
                .attr('y', getLegendY)
                .attr('height', 18)
                .attr('width', 18)
                .attr('fill', d => color(d));

            legend.append('text')
                .attr('x', margin.left - 70)
                .attr('y', getLegendY)
                .text(d => d)
                .attr('dy', '.95em')
                .style('text-anchor', 'end');

            // initialize checkbox options
            if (params.percentCB) {
                d3.select(params.percentCB).on('change', () => refresh());
            }
            params.percentView = false;
        }

        // handy function to play the update game with the bars and legend
        function choice(variable, target, nullCase, targetCase, notTargetCase) {
            switch (variable) {
                case null:
                    return nullCase;
                case target:
                    return targetCase;
                default:
                    return notTargetCase;
            }
        }
        function update(data) {
            params.input.data = data;
            updateData();
            refresh();
        }

        function tooltip() {
            if ($('body').has('.d3-tooltip.tooltip-inner').length === 0) {
                return d3.select('body')
                    .append('div')
                    .attr('class', 'tooltip d3-tooltip tooltip-inner');
            } else {
                return d3.select('body .d3-tooltip.tooltip-inner');
            }
        }

        function refresh() {

            // retrieving params to avoid putting params.x everywhere
            var svg = params.canvas.svg,
                margin = params.canvas.margin,
                y = params.scales.y,
                blockData = params.blockData,
                heights = params.heights,
                chosen = params.chosen,
                width = params.width,
                height = params.height,
                bar = params.bar,
                clusterNames = params.clusterNames,
                binNames = params.binNames,
                legend = params.legend,
                maxPerBin = params.maxPerBin;

            var transDuration = 700;

            // re-scaling data if view is changed to percentage
            // and re-scaling back if normal view is selected
            var percentView = params.percentCB ? d3.select(params.percentCB).property("checked") : false;
            if (percentView) {
                blockData.forEach(d => {
                    d.y = d.y0 / maxPerBin[d.x];
                    d.height = d.height0 / maxPerBin[d.x];
                });
                heights = setUpHeights(clusterNames, blockData);
            } else {
                blockData.forEach(d => {
                    d.y = d.y0;
                    d.height = d.height0;
                });
                heights = setUpHeights(clusterNames, blockData);
                params.percentView = percentView;
            }

            // update Y axis
            if (percentView) {
                y.domain([0, 1]); // Base 100
            } else if (chosen.cluster == null) {
                y.domain([0, d3.max(blockData, d => d.y)]);
            } else {
                y.domain([0, d3.max(heights[chosen.cluster])]);
            }

            var axisY = d3.axisLeft(y)
                .tickSize(3)
                .ticks(5);

            if (percentView) {
                axisY.tickFormat(d3.format(".0%"));
            } else if (params.axisY) {
                axisY.tickFormat(params.axisY);
            }

            svg.selectAll('.axisY')
                .transition()
                .duration(transDuration)
                .call(axisY);

            // Update legend
            legend.selectAll('rect')
                .transition()
                .duration(transDuration)
                .attr('height', d => choice(chosen.cluster, d, 18, 18, 0))
                .attr('y', getLegendY)
            legend.selectAll('text')
                .transition()
                .duration(transDuration)
                .attr('y', getLegendY)
                .style('font-size', d => choice(chosen.cluster, d, '16px', '16px', '0px'))
                .attr('x', function (d) {
                    return choice(chosen.cluster, d,
                        margin.left - 70,
                        margin.left - 70,
                        margin.left - 70 - this.getComputedTextLength() / 2);
                });

            // Update bars
            bar.selectAll('rect')
                .on('contextmenu', d => {
                    chosen.cluster = chosen.cluster === d.cluster ? null : d.cluster;
                    d3.event.preventDefault();
                    refresh();
                })
                .on('click', d => {
                    if (params.click) {
                        var isClicked = d.clicked;
                        if (params.clicked) {
                            // Uselect the previous selection
                            bar.selectAll('rect')
                                .filter(o => o.clicked)
                                .each(o => o.clicked = false)
                                .attr('class', '')
                                .attr('fill', o => params.color(o.cluster));
                        }
                        var bars = bar.selectAll('rect').filter(f => f.x === d.x);
                        if (isClicked) {
                            params.clicked = false;
                            bars.attr('class', 'selected')
                                .attr('fill', o => d3.rgb(params.color(o.cluster)).brighter());
                        } else {
                            // Change the current selection
                            params.clicked = true;
                            bars.each(o => o.clicked = true)
                                .attr('class', 'clicked')
                                .attr('fill', o => d3.rgb(params.color(o.cluster)).darker());
                        }
                        params.click(d, blockData.filter(f => f.x === d.x), params.clicked);
                    }
                })
                .on('mouseleave', function (d) {
                    var sameCost = false;
                    if (d3.event.relatedTarget && d3.event.target && d3.event.relatedTarget.__data__ && d3.event.target.__data__) {
                        var data1 = d3.event.target.__data__;
                        var data2 = d3.event.relatedTarget.__data__;
                        if (data1.x === data2.x) {
                            return;
                        }
                        var bars1 = bar.selectAll('rect').filter(f => f.x === data1.x);
                        var bars2 = bar.selectAll('rect').filter(f => f.x === data2.x);
                        sameCost = clusterNames.filter(cluster => {
                            var cost1 = bars1.filter(o => o.cluster === cluster);
                            var cost2 = bars2.filter(o => o.cluster === cluster);
                            return cost1.size() && cost2.size() && cost1.data()[0].height0 === cost2.data()[0].height0;
                        }).length === clusterNames.length;
                    }

                    var bars = bar.selectAll('rect').filter(f => f.x === d.x);
                    if (d.clicked) {
                        // Restore the clicked state of the full bar
                        bars.attr('class', 'clicked')
                            .attr('fill', o => d3.rgb(params.color(o.cluster)).darker());
                    } else {
                        // Unselect the full bar
                        bars.attr('class', '')
                            .attr('fill', o => params.color(o.cluster));
                    }
                    svg.selectAll('.limit').remove();
                    if (params.hover && !sameCost) {
                        params.hover();
                    }
                })
                .on('mouseenter', d => {
                    var bars = bar.selectAll('rect')
                        .filter(f => f.x === d.x)
                        .attr('fill', o => d3.rgb(params.color(o.cluster)).brighter());
                    if (d.clicked) {
                        bars.attr('class', 'clicked selected');
                    } else {
                        bars.attr('class', 'selected');
                    }
                    var total = y(d3.sum(blockData, f => (f.x === d.x && (chosen.cluster === null || chosen.cluster === f.cluster)) ? f.height : 0));
                    svg.append('line')
                        .attr('class', 'limit')
                        .attr('stroke-dasharray', '4,4')
                        .attr('x1', params.canvas.margin.left)
                        .attr('y1', total)
                        .attr('x2', width - params.canvas.margin.right)
                        .attr('y2', total);
                    if (params.hover) {
                        params.hover(d, bars);
                    }
                })
                .on('mouseover', d => {
                    if (params.tooltip) {
                        tooltip().html(params.tooltip(d, blockData.filter(f => f.x === d.x))).style('visibility', 'visible');
                    }
                })
                .on('mousemove', () => tooltip().style('top', (d3.event.pageY - 10) + 'px').style('left', (d3.event.pageX + 10) + 'px'))
                .on('mouseout', () => tooltip().style('visibility', 'hidden'))
                .transition()
                .duration(transDuration)
                .attr('y', d => {
                    refreshItem(d);
                    return choice(chosen.cluster, d.cluster,
                        y(d.y),
                        y(d.height),
                        myHeight(chosen, d, clusterNames, binNames, y, heights));
                })
                .attr('height', d => choice(chosen.cluster, d.cluster,
                    height - y(d.height),
                    height - y(d.height),
                    0));
            return params;
        }

        function refreshItem(d) {
            var newItem = params.blockData[d['x-index'] * params.clusterNames.length + d['cluster-index']];
            d.y0 = newItem.y0;
            d.y = newItem.y;
            d.height0 = newItem.height0;
            d.height = newItem.height;
            return newItem;
        }

        // heights is a dictionary to store bar height by cluster
        // this hierarchy is important for animation purposes 
        function setUpHeights(clusterNames, blockData) {
            var heights = {};
            clusterNames.forEach(function (cluster) {
                var clusterVec = [];
                blockData.filter(d => d.cluster == cluster).forEach(d => clusterVec.push(d.height));
                heights[cluster] = clusterVec;
            });
            return heights;
        }

        // Max value of each bin, to convert back and forth to percentage
        function setUpMax(clusterNames, blockData) {
            var maxDict = {};
            blockData.filter(d => d.cluster === clusterNames[clusterNames.length - 1]).forEach(d => maxDict[d.x] = d.y);
            return maxDict;
        }

        // custom function to provide correct animation effect
        // bars should fade into the top of the remaining bar
        function myHeight(chosen, d, clusterNames, binNames, y, heights) {
            if (chosen.cluster == null) {
                return 0;
            }
            if (clusterNames.indexOf(chosen.cluster) > clusterNames.indexOf(d.cluster)) {
                return y(0);
            }
            return y(heights[chosen.cluster][binNames.indexOf(d.x)]);
        }

        function initializeScales(width, height) {
            return {
                x: d3.scaleBand()
                    .rangeRound([params.canvas.margin.left, width - params.canvas.margin.right])
                    .padding(0),
                y: d3.scaleLinear()
                    .range([height, 0])
            };
        }

        function initializeAxis(svg, x, y, height) {
            var yAxis = d3.axisLeft(y)
                .tickSize(3)
                .ticks(5);
            if (params.axisY) {
                yAxis.tickFormat(params.axisY);
            }
            var xAxis = d3.axisBottom(x)
                .tickSizeOuter(5)
                .tickSizeInner(3)
                .tickFormat((text, index) => {
                    var fragments = text.split('/');
                    if (fragments[0] === '01') {
                        return fragments[1];
                    }
                    if (index === 0 && fragments[0] < '09') {
                        return text;
                    }
                    return '';
                });

            svg.append('g')
                .attr('class', 'axisY')
                .attr('transform', 'translate(' + params.canvas.margin.left + ',0)')
                .call(yAxis);

            svg.append('g')
                .attr('class', 'axisX')
                .attr('transform', 'translate(0,' + height + ')')
                .call(xAxis)
                .selectAll("text")
                .attr("class", "x-label");
        }

        function setUpSvgCanvas(input, selector) {
            // Set up the svg canvas
            var margin = { top: 10, left: 80, bottom: 20, right: 10 };
            var svg = d3.select(selector)
                .attr('width', input.width)
                .attr('height', input.height)
                .append('g')
                .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

            return {
                svg: svg,
                margin: margin,
                width: input.width - margin.left - margin.right,
                height: input.height - margin.top - margin.bottom
            };
        }

        const setUpColors = () => d3.scaleOrdinal(d3.schemeCategory10);
        function create(selector, selectorPercentCB, width, height, data, tooltipCB, hover, click, axisY, sort) {
            var input = { 'data': data, 'width': width, 'height': height };
            params.input = input;
            params.selector = selector;
            params.percentCB = selectorPercentCB;
            params.canvas = setUpSvgCanvas(input, selector);
            params.tooltip = tooltipCB;
            params.hover = hover;
            params.click = click;
            params.clicked = null;
            params.axisY = axisY;
            params.sort = sort;
            initialize();
            refresh();
        }

        function resize(width) {
            params.canvas.svg.html(null);
            create(params.selector, params.percentCB, width, params.input.height, params.input.data, params.tooltip, params.hover, params.click, params.axisY, params.sort);
        }

        // Exports
        return {
            create: create,
            refresh: refresh,
            update: update,
            resize: resize
        };
    }).call(this);
});
