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
        function formatData(data) {
            var clusterNames = d3.keys(data[0]).filter(function (key) { return key !== 'date'; });
            var binNames = [];
            var blockData = [];
            for (var i = 0; i < data.length; i++) {
                var y = 0;
                binNames.push(data[i].date);
                for (var j = 0; j < clusterNames.length; j++) {
                    var height = parseFloat(data[i][clusterNames[j]]);
                    y += height;
                    var block = {
                        'y0': y,
                        'y': y,
                        'height0': height,
                        'height': height,
                        'x': data[i].date,
                        'x-index': i,
                        'cluster-index': j,
                        'cluster': clusterNames[j]
                    };
                    blockData.push(block);
                }
            }
            return {
                blockData: blockData,
                binNames: binNames,
                clusterNames: clusterNames
            };
        }

        function updateData() {
            var input = params.input,
                formattedData = formatData(input.data),
                blockData = formattedData.blockData,
                clusterNames = formattedData.clusterNames;
            params.clusterNames = formattedData.clusterNames;
            params.binNames = formattedData.binNames;
            params.blockData = blockData;
            params.heights = setUpHeights(clusterNames, blockData);
            params.maxPerBin = setUpMax(clusterNames, blockData);
            params.scales = initializeScales(params.width, params.height);
            return formattedData;
        }

        function initialize() {

            // unpacking params
            var canvas = params.canvas;

            // unpacking canvas
            var svg = canvas.svg,
                margin = canvas.margin,
                width = params.width = canvas.width,
                height = params.height = canvas.height;

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
            y.domain([0, d3.max(blockData, (d) => d.y)]);

            initializeAxis(svg, x, y, height, width);

            // initialize bars
            var bar = params.bar = svg.selectAll('.bar')
                .data(blockData)
                .enter().append('g')
                .attr('class', 'bar');

            bar.append('rect')
                .attr('x', (d) => x(d.x))
                .attr('y', () => y(0))
                .attr('width', x.bandwidth())
                .attr('height', 0)
                .attr('fill', (d) => color(d.cluster));

            // variable to store chosen cluster when bar is clicked
            var chosen = params.chosen = {
                cluster: null
            };

            // initialize legend
            var legend = params.legend = svg.selectAll('.legend')
                .data(clusterNames)
                .enter().append('g')
                .attr('class', 'legend');

            legend.append('rect')
                .attr('x', margin.left - 53)
                .attr('y', (_, i) => 20 * (clusterNames.length - i))
                .attr('height', 18)
                .attr('width', 18)
                .attr('fill', color)
                .on('click', function (d) {
                    chosen.cluster = chosen.cluster === d ? null : d;
                    refresh();
                });

            legend.append('text')
                .attr('x', margin.left - 60)
                .attr('y', (_, i) => 20 * (clusterNames.length - i))
                .text((d) => d)
                .attr('dy', '.95em')
                .style('text-anchor', 'end');

            // initialize checkbox options
            if (params.percentCB) {
                d3.select(params.percentCB).on("change", () => refresh());
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
                maxPerBin = params.maxPerBin,
                percentView = params.percentView;

            var transDuration = 700;

            // re-scaling data if view is changed to percentage
            // and re-scaling back if normal view is selected
            var percentView = params.percentCB ? d3.select(params.percentCB).property("checked") : false;
            if (percentView) {
                blockData.forEach(function (d) {
                    d.y = d.y0 / maxPerBin[d.x];
                    d.height = d.height0 / maxPerBin[d.x];
                });
                heights = setUpHeights(clusterNames, blockData);
            } else {
                blockData.forEach(function (d) {
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
                y.domain([0, d3.max(blockData, (d) => d.y)]);
            } else {
                y.domain([0, d3.max(heights[chosen.cluster])]);
            }

            var axisY = d3.axisLeft(y)
                .tickSize(3)
                .ticks(5);

            if (percentView) {
                axisY.tickFormat(d3.format(".0%"));
            }

            svg.selectAll('.axisY')
                .transition()
                .duration(transDuration)
                .call(axisY);

            // Update legend
            legend.selectAll('rect')
                .transition()
                .duration(transDuration)
                .attr('height', (d) => choice(chosen.cluster, d, 18, 18, 0))
                .attr('y', function (d) {
                    var i = clusterNames.indexOf(d);
                    if (i > clusterNames.indexOf(chosen.cluster)) {
                        return choice(chosen.cluster, d, 20 * (clusterNames.length - i), marginTop, marginTop);
                    }
                    return choice(chosen.cluster, d, 20 * (clusterNames.length - i), marginTop, marginTop);
                });
            legend.selectAll('text')
                .transition()
                .duration(transDuration)
                .attr('y', function (d) {
                    var i = clusterNames.indexOf(d);
                    if (i > clusterNames.indexOf(chosen.cluster)) {
                        return choice(chosen.cluster, d, 20 * (clusterNames.length - i), marginTop, marginTop);
                    }
                    return choice(chosen.cluster, d, 20 * (clusterNames.length - i), marginTop, marginTop);
                })
                .style('font-size', (d) => choice(chosen.cluster, d, '16px', '16px', '0px'))
                .attr('x', function (d) {
                    return choice(chosen.cluster, d,
                        margin.left - 60,
                        margin.left - 60,
                        margin.left - 60 - this.getComputedTextLength() / 2);
                });

            // Update bars
            bar.selectAll('rect')
                .on('contextmenu', function (d) {
                    chosen.cluster = chosen.cluster === d.cluster ? null : d.cluster;
                    d3.event.preventDefault();
                    refresh();
                })
                .on('mouseleave', function (d) {
                    bar.selectAll('rect')
                        .filter((f) => f.x === d.x)
                        .attr('class', '')
                        .attr('fill', (d) => params.color(d.cluster));
                    svg.selectAll('.limit').remove()
                })
                .on('mouseenter', function (d) {
                    var bars = bar.selectAll('rect')
                        .filter((f) => f.x === d.x)
                        .attr('class', 'selected')
                        .attr('fill', (d) => d3.rgb(params.color(d.cluster)).brighter());
                    var total = y(d3.sum(blockData, (f) => (f.x === d.x && (chosen.cluster === null || chosen.cluster === f.cluster)) ? f.height : 0));
                    svg.append('line')
                        .attr('class', 'limit')
                        .attr('stroke-dasharray', '4,4')
                        .attr('x1', params.canvas.margin.left)
                        .attr('y1', total)
                        .attr('x2', width - params.canvas.margin.right)
                        .attr('y2', total);
                })
                .transition()
                .duration(transDuration)
                .attr('y', function (d) {
                    refreshItem(d);
                    return choice(chosen.cluster, d.cluster,
                        y(d.y),
                        y(d.height),
                        myHeight(chosen, d, clusterNames, binNames, y, heights));
                })
                .attr('height', function (d) {
                    return choice(chosen.cluster, d.cluster,
                        height - y(d.height),
                        height - y(d.height),
                        0);
                });
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
                blockData.filter(function (d) { return d.cluster == cluster; }).forEach(function (d) {
                    clusterVec.push(d.height);
                });
                heights[cluster] = clusterVec;
            });
            return heights;
        }

        // getting the max value of each bin, to convert back and forth to percentage
        function setUpMax(clusterNames, blockData) {
            var lastClusterElements = blockData.filter(function (d) { return d.cluster == clusterNames[clusterNames.length - 1] })
            var maxDict = {};
            lastClusterElements.forEach(function (d) {
                maxDict[d.x] = d.y;
            });
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
                    .padding(0.15),
                y: d3.scaleLinear()
                    .range([height, 0])
            };
        }

        function initializeAxis(svg, x, y, height, width) {
            var yAxis = d3.axisLeft(y)
                .tickSize(3)
                .ticks(5);
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
            var margin = { top: 10, left: 80, bottom: 20, right: 10 },
                width = input.width - margin.left - margin.right,
                height = input.height - margin.top - margin.bottom;

            var svg = d3.select(selector)
                .attr('width', width + margin.left + margin.right)
                .attr('height', height + margin.top + margin.bottom)
                .append('g')
                .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

            return {
                svg: svg,
                margin: margin,
                width: width,
                height: height
            };
        }

        function setUpColors() {
            return d3.scaleOrdinal(d3.schemeCategory10);
        }
        function create(selector, selectorPercentCB, width, height, data) {
            var input = { 'data': data, 'width': width, 'height': height };
            params.input = input;
            params.percentCB = selectorPercentCB;
            params.canvas = setUpSvgCanvas(input, selector);
            initialize();
            refresh();
        }

        // Exports
        return {
            create: create,
            refresh: refresh,
            update: update
        };
    }).call(this);
});
