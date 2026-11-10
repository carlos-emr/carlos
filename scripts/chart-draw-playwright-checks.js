/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The chart draw script the renderer runs inside headless Chromium (library/chartjs/carlos-chart-draw.js),
 * exercised with the bundled Chart.js in a Playwright browser, without the driver service, against
 * recordings of the JFreeChart 1.5.6 charts it replaced.
 *
 * scripts/fixtures/chart/jfreechart/*.json were produced by JfcReference.java in the same directory:
 * it builds each chart exactly as the former MeasurementGraphAction22Action and ScatterPlotChartServlet
 * did, with synthetic data, and records what JFreeChart chose: the axis ranges after its margins and
 * zero rules, the tick unit, every tick position and label, the series paints and strokes, the legend
 * items, and the fonts and colours of its theme. Each fixture also carries the renderer input in the
 * shape MeasurementChart.toConfig() produces. For every fixture this check draws the input and
 * requires the same ranges, ticks, labels, colours, fonts, legend and bar geometry, plus the
 * JFreeChart drawing details a reader notices: the plot background and outline, the bar shadows, a
 * zero-length medication drawn as a line, item labels and reference-range bars.
 * Set CHART_SCREENSHOT_DIR to retain each synthetic fixture before its assertions, including
 * the failing image when a visual regression is detected. CI publishes these as an artifact.
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const { getLaunchOptions, buildArtifactPath } = require('./lib/playwright-harness');

const LIBRARY = path.join(__dirname, '../src/main/webapp/library/chartjs');
const FIXTURES = path.join(__dirname, 'fixtures/chart/jfreechart');
const SECOND = 1000;
// Pixels by which a plot edge may differ from JFreeChart's data area (text metrics round differently).
const PLOT_TOLERANCE = 2;
// labRef mapped its green ranges to a second index of the same axis object, so JFreeChart drew that
// value axis twice side by side; the replacement draws it once, and its plot starts further left.
const DOCUMENTED_PLOT_DEPARTURES = { labRef: ['left'] };

/** What JFreeChart's legend graphic was for an item: a line, a shape, or both. */
function legendGraphic(item) {
    if (item.lineVisible && item.shapeVisible && item.shapeType === 'ellipse') return 'lineAndCircle';
    if (item.lineVisible && item.shapeVisible) return 'lineAndShape';
    if (item.lineVisible) return 'line';
    return `shape${item.shape[2]}x${item.shape[3]}`;
}

function fontSize(javaFont) {
    return Number(javaFont.split(' ').pop());
}

function isBold(javaFont) {
    return javaFont.includes('bold');
}

async function draw(page, input, width, height) {
    // Only checked-in synthetic chart fixtures and numeric dimensions enter this literal callback.
    // Arguments are structured-cloned data; the callback neither evaluates strings nor makes requests.
    // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection
    return page.evaluate(([input, width, height]) => {
        const charts = window.carlosDrawChart(input, width, height);
        const scale = s => s && ({ min: s.min, max: s.max, ticks: s.ticks.map(t => ({ value: t.value, label: String(t.label ?? '') })),
            tickDisplay: s.options.ticks.display !== false, tickFont: s.options.ticks.font, tickColor: s.options.ticks.color,
            titleFont: s.options.title.font, titleColor: s.options.title.color, titleText: s.options.title.display ? s.options.title.text : '' });
        const pixel = (chart, x, y) => {
            const d = chart.ctx.getImageData(Math.round(x), Math.round(y), 1, 1).data;
            return '#' + [d[0], d[1], d[2]].map(v => v.toString(16).padStart(2, '0')).join('');
        };
        const gridPixels = (chart, axis) => {
            const area = chart.chartArea;
            const background = chart.config.type === 'bar' ? '#ffffff' : input.style.plotBackground;
            const rgb = [1, 3, 5].map(offset => parseInt(background.slice(offset, offset + 2), 16));
            const rgba = axis.options.grid.color.match(/[\d.]+/g).map(Number);
            const expected = rgb.map((value, index) => Math.round(value * (1 - rgba[3]) + rgba[index] * rgba[3]));
            const pixels = chart.ctx.getImageData(0, 0, chart.width, chart.height).data;
            const horizontal = axis.isHorizontal();
            const otherAxis = horizontal ? chart.scales.y : chart.scales.x;
            const crossings = otherAxis.ticks.map((tick, index) => otherAxis.getPixelForTick(index));
            const first = Math.ceil(horizontal ? area.top : area.left) + 8;
            const last = Math.floor(horizontal ? area.bottom : area.right) - 8;
            const counts = { colour: axis.options.grid.color, dash: [...axis.options.border.dash], painted: 0, gaps: 0 };
            axis.ticks.forEach((tick, index) => {
                // JFreeChart's gridline filled the pixel at floor(v + 0.25) (Java2D stroke normalisation).
                const cross = Math.floor(axis.getPixelForTick(index) + 0.25);
                if (cross < (horizontal ? area.left : area.top) + 3 || cross > (horizontal ? area.right : area.bottom) - 3) return;
                for (let along = first; along < last; along++) {
                    // A perpendicular gridline must not make an absent line pass this check.
                    if (crossings.some(crossing => Math.abs(crossing - along) <= 3)) continue;
                    const offset = ((horizontal ? along : cross) * chart.width + (horizontal ? cross : along)) * 4;
                    const matches = colour => colour.every((value, channel) => Math.abs(pixels[offset + channel] - value) <= 2);
                    if (matches(expected)) counts.painted++;
                    if (matches(rgb)) counts.gaps++;
                }
            });
            return counts;
        };
        return {
            canvases: [...document.querySelectorAll('canvas')].map(c => ({ width: c.width, height: c.height })),
            charts: charts.map(chart => ({
                area: { ...chart.chartArea },
                title: chart.options.plugins.title,
                x: scale(chart.scales.x), y: scale(chart.scales.y), y2: scale(chart.scales.y2),
                datasets: chart.data.datasets.map((ds, i) => ({ label: ds.label, kind: ds.carlosKind, borderColor: ds.borderColor, backgroundColor: ds.backgroundColor,
                    borderWidth: ds.borderWidth, shapes: ds.carlosShapes === true, showLine: ds.showLine,
                    firstMarkerPixel: ds.carlosShapes && chart.getDatasetMeta(i).data.length
                        ? pixel(chart, chart.getDatasetMeta(i).data[0].x, chart.getDatasetMeta(i).data[0].y) : null,
                    markerPixels: ds.carlosShapes ? chart.getDatasetMeta(i).data.slice(1, 2).map(point => ({
                        centre: pixel(chart, Math.round(point.x), Math.round(point.y)),
                        corner: pixel(chart, Math.round(point.x) - 3, Math.round(point.y) - 3),
                    })) : [],
                    bars: chart.config.type === 'bar' ? chart.getDatasetMeta(i).data.map(b => ({ x: b.x, base: b.base, y: b.y, height: b.height })) : undefined })),
                legend: chart.carlosLegend, itemLabels: chart.carlosItemLabels, ranges: chart.carlosRanges, shadows: chart.carlosShadows,
                plotPixel: pixel(chart, chart.chartArea.left + 3, chart.chartArea.top + 3),
                gridPixels: { x: gridPixels(chart, chart.scales.x), y: gridPixels(chart, chart.scales.y) },
                pixelAt: (() => null)(),
            })),
            pixelAt: null,
        };
    }, [input, width, height]);
}

async function pixelAt(page, chartIndex, x, y) {
    // Numeric canvas coordinates come from this test's own rendered chart; no URL or code is constructed.
    // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection
    return page.evaluate(([chartIndex, x, y]) => {
        const canvas = document.querySelectorAll('canvas')[chartIndex];
        const d = canvas.getContext('2d').getImageData(Math.round(x), Math.round(y), 1, 1).data;
        return '#' + [d[0], d[1], d[2]].map(v => v.toString(16).padStart(2, '0')).join('');
    }, [chartIndex, x, y]);
}

/** Health Tracker and dashboard charts use Chart.js defaults, without the image adapter. */
async function checkBundledGrids(page, screenshotDirectory) {
    await page.setViewportSize({ width: 600, height: 400 });
    for (const type of ['line', 'bar']) {
        // Only these two literal chart types and fixed synthetic observations enter the callback.
        // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection
        const grids = await page.evaluate(type => {
            Object.values(Chart.instances).forEach(chart => chart.destroy());
            document.body.replaceChildren();
            document.body.style.cssText = 'margin:0;background:white';
            const canvas = document.createElement('canvas');
            canvas.width = 600;
            canvas.height = 400;
            document.body.append(canvas);
            const chart = new Chart(canvas, {
                type,
                data: { labels: ['First', 'Second', 'Third'], datasets: [{ label: 'Synthetic observations',
                    data: [2, 8, 5], borderColor: '#ff0000', backgroundColor: '#ff0000' }] },
                options: { responsive: false, animation: false, devicePixelRatio: 1 },
            });
            const area = chart.chartArea;
            const pixels = chart.ctx.getImageData(0, 0, chart.width, chart.height).data;
            const gridPositions = axis => {
                const ticks = axis.ticks.map((_, index) => axis.getPixelForTick(index));
                // Category bar grids run between the ticks; line charts put them on the ticks.
                return axis.options.grid.offset ? ticks.slice(1).map((value, index) => (ticks[index] + value) / 2) : ticks;
            };
            return ['x', 'y'].map(id => {
                const axis = chart.scales[id];
                const horizontal = axis.isHorizontal();
                const other = chart.scales[horizontal ? 'y' : 'x'];
                const crossings = gridPositions(other);
                let painted = 0;
                gridPositions(axis).forEach(position => {
                    const cross = Math.round(position);
                    if (cross < (horizontal ? area.left : area.top) + 4
                            || cross > (horizontal ? area.right : area.bottom) - 4) return;
                    const first = Math.ceil(horizontal ? area.top : area.left) + 8;
                    const last = Math.floor(horizontal ? area.bottom : area.right) - 8;
                    for (let along = first; along <= last; along++) {
                        if (crossings.some(value => Math.abs(value - along) < 4)) continue;
                        // Chart.js snaps a one-pixel stroke to a pixel centre, possibly on the
                        // neighbouring pixel of a fractional category boundary.
                        for (const delta of [-1, 0, 1]) {
                            const x = horizontal ? cross + delta : along;
                            const y = horizontal ? along : cross + delta;
                            const offset = (y * chart.width + x) * 4;
                            // Default grid is translucent black. Red series and transparent gaps cannot pass.
                            if (pixels[offset] === 0 && pixels[offset + 1] === 0 && pixels[offset + 2] === 0
                                    && pixels[offset + 3] > 0 && pixels[offset + 3] < 100) painted++;
                        }
                    }
                });
                return { axis: id, painted };
            });
        }, type);
        if (screenshotDirectory) {
            await page.screenshot({ path: buildArtifactPath(screenshotDirectory, `native-${type}`) });
        }
        for (const grid of grids) {
            assert.ok(grid.painted > 4, `bundled ${type}: ${grid.axis} grid has visible pixels (${grid.painted})`);
        }
    }
}

function expectAxis(name, actual, expected, dateAxis) {
    assert.ok(actual, `${name}: the axis is missing`);
    const tolerance = dateAxis ? SECOND : Math.max(1e-6, Math.abs(expected.upper - expected.lower) * 1e-6);
    assert.ok(Math.abs(actual.min - expected.lower) <= tolerance, `${name}: lower bound ${actual.min} should be ${expected.lower}`);
    assert.ok(Math.abs(actual.max - expected.upper) <= tolerance, `${name}: upper bound ${actual.max} should be ${expected.upper}`);
    const major = expected.ticks.filter(t => t.label !== '');
    assert.deepEqual(actual.ticks.map(t => t.label), major.map(t => t.label), `${name}: tick labels`);
    major.forEach((tick, i) => assert.ok(Math.abs(actual.ticks[i].value - tick.value) <= tolerance,
        `${name}: tick ${i} at ${actual.ticks[i].value} should be at ${tick.value} (${tick.label})`));
    assert.equal(actual.tickFont.size, fontSize(expected.tickLabelFont), `${name}: tick label size`);
    assert.equal(actual.tickColor, expected.tickLabelPaint, `${name}: tick label colour`);
    assert.equal(actual.titleFont.size, fontSize(expected.labelFont), `${name}: axis label size`);
    assert.equal(actual.titleFont.weight, isBold(expected.labelFont) ? 'bold' : 'normal', `${name}: axis label weight`);
    assert.equal(actual.titleColor, expected.labelPaint, `${name}: axis label colour`);
}

function checkFixture(fixture, result) {
    const name = fixture.name;
    const combined = fixture.combined === true;
    const charts = result.charts;
    assert.equal(result.canvases.length, fixture.subplots.length, `${name}: one canvas per JFreeChart subplot`);
    assert.equal(result.canvases.reduce((sum, c) => sum + c.height, 0), fixture.height, `${name}: the canvases share the image height`);
    result.canvases.forEach(c => assert.equal(c.width, fixture.width, `${name}: canvas width`));

    // Title on the first canvas, in JFreeChart's font and colour.
    const title = charts[0].title;
    assert.equal(title.display, true, `${name}: title shown`);
    assert.equal(title.text, fixture.title, `${name}: title text`);
    assert.equal(title.font.size, fontSize(fixture.titleFont), `${name}: title size`);
    assert.equal(title.font.weight, 'bold', `${name}: title weight`);
    assert.equal(title.color, '#000000', `${name}: title colour`);
    charts.slice(1).forEach(chart => assert.equal(chart.title.display, false, `${name}: a lower panel repeats no title`));

    // The shared or single domain axis, read from the bottom panel.
    const bottom = charts[charts.length - 1];
    const domain = combined ? fixture.domain : fixture.subplots[0].domain;
    expectAxis(`${name} x`, bottom.x, domain, domain.type === 'DateAxis');
    if (combined && charts.length > 1) {
        assert.equal(charts[0].x.tickDisplay, false, `${name}: the upper panel shows no x tick labels`);
        // Equal slots; the measurement plot keeps its 4px axis offset inside its slot on every side.
        const heights = charts.map(c => c.area.bottom - c.area.top);
        assert.ok(Math.abs(heights[0] + 8 - heights[1]) <= 2, `${name}: subplots share the height equally (${heights})`);
        assert.ok(Math.abs(charts[0].area.left - 4 - charts[1].area.left) <= 1 && Math.abs(charts[0].area.right + 4 - charts[1].area.right) <= 1,
            `${name}: panel plot rectangles align`);
    }
    // Each plot rectangle lies where JFreeChart drew its data area, so the title, axes, labels and
    // legend around it keep their former positions.
    let top = 0;
    fixture.subplots.forEach((subplot, i) => {
        const area = charts[i].area;
        const [x, y, w, h] = subplot.dataArea;
        const actual = [area.left, area.top + top, area.right, area.bottom + top];
        [x, y, x + w, y + h].forEach((edge, j) => {
            const side = ['left', 'top', 'right', 'bottom'][j];
            if ((DOCUMENTED_PLOT_DEPARTURES[name] || []).includes(side)) return;
            assert.ok(Math.abs(actual[j] - edge) <= PLOT_TOLERANCE,
                `${name}: plot ${side} edge ${actual[j].toFixed(1)} should be at ${edge} (JFreeChart)`);
        });
        top += result.canvases[i].height;
    });

    fixture.subplots.forEach((subplot, i) => {
        const chart = charts[i];
        const symbol = subplot.ranges[0].type === 'SymbolAxis';
        if (symbol) {
            assert.deepEqual(chart.y.ticks.map(t => t.label), subplot.ranges[0].symbols, `${name}: medication rows`);
            assert.equal(chart.y.tickFont.size, fontSize(subplot.ranges[0].tickLabelFont), `${name}: row label size`);
        } else {
            expectAxis(`${name} y`, chart.y, subplot.ranges[0], false);
            if (fixture.input.secondAxis) expectAxis(`${name} y2`, chart.y2, subplot.ranges[1], false);
        }
        assert.equal(chart.plotPixel, subplot.background, `${name}: plot background`);
        // A gridline JFreeChart drew on the plot edge (the only tick of an empty or single-instant axis)
        // hides under the outline, so only ticks inside the plot must leave visible grid pixels.
        const interior = axis => axis.ticks.some(t => t.value > axis.lower + (axis.upper - axis.lower) * 0.01
            && t.value < axis.upper - (axis.upper - axis.lower) * 0.01);
        const axisFixtures = { x: combined ? fixture.domain : subplot.domain, y: subplot.ranges[0] };
        for (const [axis, colour] of [['x', subplot.domainGridlines], ['y', subplot.rangeGridlines]]) {
            const grid = chart.gridPixels[axis];
            const rgb = [1, 3, 5].map(offset => parseInt(colour.slice(offset, offset + 2), 16));
            assert.equal(grid.colour, `rgba(${rgb.join(', ')}, 0.5)`, `${name}: ${axis} grid colour`);
            assert.deepEqual(grid.dash, subplot.gridlineStroke.dash, `${name}: ${axis} grid dash pattern`);
            if (colour !== subplot.background && interior(axisFixtures[axis])) {
                assert.ok(grid.painted > 4, `${name}: ${axis} grid has visible pixels (${grid.painted})`);
                assert.ok(grid.gaps > 4, `${name}: ${axis} grid has visible dash gaps (${grid.gaps})`);
            }
        }
        const lines = subplot.datasets.filter(d => d.renderer !== 'XYBarRenderer' && d.renderer !== 'HighLowRenderer' || d.key === 'ALT' && d.renderer === 'HighLowRenderer');
        subplot.datasets.forEach(expected => {
            if (expected.renderer === 'XYBarRenderer') return;
            const actual = chart.datasets.find(d => d.label === expected.key);
            assert.ok(actual, `${name}: series ${expected.key} drawn`);
            if (expected.renderer === 'HighLowRenderer' && fixture.input.datasets.find(d => d.label === expected.key).kind === 'range') {
                assert.equal(actual.kind, 'range', `${name}: ${expected.key} is a reference range`);
                assert.equal(actual.borderColor, expected.paint, `${name}: range colour`);
                return;
            }
            assert.equal(actual.borderColor, expected.paint, `${name}: ${expected.key} colour`);
            assert.equal(actual.borderWidth, expected.stroke.width, `${name}: ${expected.key} stroke`);
            assert.equal(actual.shapes, expected.shapes === true, `${name}: ${expected.key} shapes`);
            if (expected.shapes) {
                assert.equal(actual.markerPixels.length, 1, `${name}: ${expected.key} marker sampled`);
                const marker = actual.markerPixels[0];
                assert.equal(marker.centre, expected.paint, `${name}: ${expected.key} marker centre`);
                assert.equal(marker.corner === expected.paint, expected.shapeType !== 'ellipse',
                    `${name}: ${expected.key} marker matches JFreeChart's ${expected.shapeType}`);
            }
            assert.equal(actual.showLine, expected.lines !== false, `${name}: ${expected.key} line`);
        });
        void lines;
        if (symbol) {
            const barDatasets = subplot.datasets;
            const bars = chart.datasets[0].bars;
            assert.equal(bars.length, fixture.input.medications.length, `${name}: one bar per prescription`);
            const spacing = (chart.area.bottom - chart.area.top) / subplot.ranges[0].symbols.length;
            bars.forEach(bar => assert.ok(Math.abs(bar.height / spacing - 0.6) < 0.03, `${name}: bar thickness ${bar.height / spacing} should be 0.6 of the row`));
            const colours = [].concat(chart.datasets[0].backgroundColor);
            fixture.input.medications.forEach((m, j) => {
                const series = barDatasets.find(d => d.key === m.name);
                assert.equal(colours[j], series.paint, `${name}: ${m.name} bar colour`);
            });
            assert.ok(chart.shadows && chart.shadows.length === bars.length, `${name}: every bar has a shadow`);
            assert.ok(chart.shadows.every(s => s.colour === '#808080' && s.dx === 4 && s.dy === 4), `${name}: shadows are gray and offset 4px`);
        }
        // Only the line-and-shape renderer draws item labels; the high-low renderer ignores its generator.
        const labelled = subplot.datasets.filter(d => d.itemLabels && d.itemLabel0 && d.renderer === 'XYLineAndShapeRenderer');
        if (labelled.length) {
            const first = labelled[0];
            assert.ok(chart.itemLabels && chart.itemLabels.length > 0, `${name}: item labels drawn`);
            assert.equal(chart.itemLabels[0].text, first.itemLabel0.replaceAll('\n', ''), `${name}: first item label`);
            assert.equal(chart.itemLabels[0].fontSize, fontSize(first.itemLabelFont), `${name}: item label size`);
        } else {
            assert.ok(!chart.itemLabels || chart.itemLabels.length === 0, `${name}: no item labels`);
        }
        if (!symbol) {
            const ranges = fixture.input.datasets.filter(d => d.kind === 'range').flatMap(d => d.data);
            assert.equal((chart.ranges || []).length, ranges.length, `${name}: reference range bars`);
        }
    });

    // The legend lists JFreeChart's items in order, with its graphics, on the bottom panel.
    const legend = bottom.legend;
    assert.ok(legend, `${name}: legend drawn`);
    assert.deepEqual(legend.map(l => l.label), fixture.legend.map(l => l.label), `${name}: legend labels`);
    assert.deepEqual(legend.map(l => l.graphic), fixture.legend.map(legendGraphic), `${name}: legend graphics`);
    assert.deepEqual(legend.map(l => l.colour), fixture.legend.map(l => l.fill), `${name}: legend colours`);
    if (fixture.legend.length) {
        assert.equal(legend[0].fontSize, fontSize(fixture.legendFont), `${name}: legend font size`);
        assert.equal(legend[0].textColour, fixture.legendPaint, `${name}: legend text colour`);
    }
    charts.slice(0, -1).forEach(chart => assert.ok(!chart.legend || chart.legend.length === 0, `${name}: only the bottom panel has a legend`));
}

async function main() {
    let browser;
    try {
        const screenshotDirectory = process.env.CHART_SCREENSHOT_DIR;
        browser = await chromium.launch(getLaunchOptions(
            process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.CARLOS_SMOKE_CHROMIUM));
        const page = await browser.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setContent('<!doctype html><html><body></body></html>');
        for (const file of ['chart.umd.min.js', 'carlos-chart-draw.js']) {
            await page.addScriptTag({ path: path.join(LIBRARY, file) });
        }
        await checkBundledGrids(page, screenshotDirectory);
        const names = fs.readdirSync(FIXTURES).filter(f => f.endsWith('.json')).sort();
        assert.ok(names.length >= 10, 'the JFreeChart fixtures are present');
        for (const file of names) {
            const fixture = JSON.parse(fs.readFileSync(path.join(FIXTURES, file), 'utf8'));
            await page.setViewportSize({ width: fixture.width, height: fixture.height });
            const result = await draw(page, fixture.input, fixture.width, fixture.height);
            if (screenshotDirectory) {
                await page.screenshot({ path: buildArtifactPath(screenshotDirectory, file.replace(/\.json$/, '')) });
            }
            if (fixture.name === 'default-1pt') {
                // #5814 deliberately replaces the legacy invisible point / 2ms domain.
                assert.ok(result.charts[0].x.max - result.charts[0].x.min >= 86_400_000);
                assert.ok(result.charts[0].y.max > 70 && result.charts[0].y.min < 70);
                assert.ok(new Set(result.charts[0].y.ticks.map(t => t.label)).size > 1);
                assert.equal(result.charts[0].datasets[0].shapes, true);
                assert.equal(result.charts[0].datasets[0].firstMarkerPixel, "#ff5555", "single point has actual visible marker pixels");
            } else {
                checkFixture(fixture, result);
            }
            assert.deepEqual(errors, [], `${fixture.name}: page errors`);
        }

        const frenchInput = JSON.parse(fs.readFileSync(path.join(__dirname,
            '../docs/images/pr6102/configs/after-fr/qualified-results.json'), 'utf8'));
        await draw(page, frenchInput, 1000, 600);
        const localizedTicks = await page.evaluate(() => {
            const chart = Object.values(Chart.instances).find(item => item.canvas.isConnected && item.scales.x);
            const ctx = chart.ctx; ctx.save();
            ctx.font = chart.scales.x.options.ticks.font.size + 'px "DejaVu Sans"';
            const ticks = chart.scales.x.ticks.map((tick, index) => ({
                label: tick.label, x: chart.scales.x.getPixelForTick(index), width: ctx.measureText(tick.label).width,
            }));
            ctx.restore(); return ticks;
        });
        assert.ok(localizedTicks.length > 1, 'localized dates retain useful ticks');
        localizedTicks.slice(1).forEach((tick, index) => assert.ok(
            tick.x - localizedTicks[index].x >= (tick.width + localizedTicks[index].width) / 2 + 2,
            'French date labels do not overlap using independent browser text measurements'));

        // A zero-length prescription is still visible: JFreeChart drew it as a one pixel line with its shadow.
        const thirteen = JSON.parse(fs.readFileSync(path.join(FIXTURES, 'chartmeds-13.json'), 'utf8'));
        const result = await draw(page, thirteen.input, thirteen.width, thirteen.height);
        const bars = result.charts[0].datasets[0].bars;
        const single = bars[bars.length - 1];
        assert.ok(Math.abs(single.x - single.base) <= 1, 'a same-day prescription keeps its zero-length interval');
        const colour = await pixelAt(page, 0, single.base, single.y);
        assert.equal(colour, '#c000c0', `a same-day prescription is drawn as a line in its colour (saw ${colour})`);
        const shadow = await pixelAt(page, 0, single.base + 4, single.y + single.height / 2 + 2);
        assert.equal(shadow, '#808080', `its shadow is drawn (saw ${shadow})`);

        // XYBarRenderer painted each prescription's shadow and then its bar, in order: a medication
        // prescribed again inside an earlier prescription left its shadow's edge across the earlier bar.
        const repeat = JSON.parse(fs.readFileSync(path.join(FIXTURES, 'chartmeds-repeat.json'), 'utf8'));
        const repeatBars = (await draw(page, repeat.input, repeat.width, repeat.height)).charts[0].datasets[0].bars;
        const inner = repeatBars[2];
        const innerEnd = Math.round(Math.max(inner.x, inner.base));
        const seam = await pixelAt(page, 0, innerEnd + 2, inner.y);
        assert.equal(seam, '#808080', `the later prescription's shadow edge crosses the earlier bar (saw ${seam})`);
        const after = await pixelAt(page, 0, innerEnd + 8, inner.y);
        assert.equal(after, '#5555ff', `the earlier bar continues past that edge (saw ${after})`);

        // A series observed within one day falls back to hourly ticks with the time on each label.
        const t0 = Date.UTC(2024, 0, 15, 9, 30);
        const hourly = { ...thirteen.input, medications: [], datasets: [{ label: 'Weight', kind: 'line', yAxisID: 'y', paint: null, lines: true, shapes: false,
            data: [{ x: t0, y: 61 }, { x: t0 + 3 * 3_600_000, y: 63 }] }], timeZone: 'UTC' };
        const sameDay = await draw(page, hourly, 1000, 600);
        assert.ok(sameDay.charts[0].x.ticks.some(t => /\d{2}:\d{2}/.test(t.label)), `a same-day series shows times on its date axis: ${sameDay.charts[0].x.ticks.map(t => t.label)}`);

        // Distinct full names can share the shortened axis label. Every repeat prescription must
        // stay on its own medication's row, independently of its position in the input array.
        const firstName = 'Synthetic medication with shared prefix A';
        const secondName = 'Synthetic medication with shared prefix B';
        const intervals = [firstName, firstName, secondName].map((name, index) => ({
            name, start: t0 + index * 2 * 86_400_000, end: t0 + (index * 2 + 1) * 86_400_000,
        }));
        const repeated = await draw(page, { ...thirteen.input, medications: intervals }, 1000, 600);
        const repeatedBars = repeated.charts[0].datasets[0].bars;
        assert.equal(repeatedBars[0].y, repeatedBars[1].y, 'repeat prescriptions remain on the same medication row');
        assert.notEqual(repeatedBars[1].y, repeatedBars[2].y, 'distinct names with the same shortened label retain separate rows');
        assert.deepEqual(repeated.charts[0].legend.map(item => item.label), [firstName, secondName]);

        // A valid request with no matching prescriptions keeps the former combined chart: one empty
        // white medication panel titled as before, "Date/Time" under "Meds", with no bars and no legend
        // (chartmeds-empty records what JFreeChart drew).
        const empty = await draw(page, { ...thirteen.input, medications: [] }, 1000, 600);
        assert.equal(empty.canvases.length, 1, 'an empty medication request renders a chart');
        assert.equal(empty.charts[0].title.text, thirteen.input.title);
        assert.equal(empty.charts[0].x.titleText, 'Date/Time', 'the empty medication panel keeps its date axis label');
        assert.equal(empty.charts[0].y.titleText, 'Meds', 'the empty medication panel keeps its row axis label');
        assert.equal(empty.charts[0].plotPixel, '#ffffff', 'the empty medication panel stays white');
        assert.deepEqual(empty.charts[0].datasets.flatMap(dataset => dataset.bars || []), []);
        assert.deepEqual(empty.charts[0].legend || [], []);

        // Each DIN was its own TaskSeries named by its first prescription: two DINs sharing a brand
        // name keep two rows and two legend items.
        const sharedBrand = await draw(page, { ...thirteen.input, medications: [
            { row: 'din-1', name: 'SYNTHETIC SHARED BRAND', start: t0, end: t0 + 86_400_000 },
            { row: 'din-2', name: 'SYNTHETIC SHARED BRAND', start: t0 + 2 * 86_400_000, end: t0 + 3 * 86_400_000 }] }, 1000, 600);
        const sharedBars = sharedBrand.charts[0].datasets[0].bars;
        assert.notEqual(sharedBars[0].y, sharedBars[1].y, 'two DINs sharing a brand name keep separate rows');
        assert.deepEqual(sharedBrand.charts[0].legend.map(item => item.label), ['SYNTHETIC SHARED BRAND', 'SYNTHETIC SHARED BRAND']);

        // The fixed 1e-8 range expansion previously rounded away at this magnitude and left an
        // infinite tick loop. A wide range must also choose units beyond the former 5e12 ceiling.
        for (const values of [[0.05], [-0.05], [0], [99], [5_000_000], [12_500_000], [45_000_000], [-6_000_000], [12_500_000, 12_500_000], [100_000_000], [0, 1e20], [1e308]]) {
            const input = { ...hourly, datasets: [{ ...hourly.datasets[0], data: values.map((y, index) => ({ x: t0 + index * 86_400_000, y })) }] };
            const large = await draw(page, input, 1000, 600);
            const axis = large.charts[0].y;
            assert.ok(Number.isFinite(axis.min) && Number.isFinite(axis.max) && axis.max > axis.min, 'large values retain a finite nonzero axis range');
            assert.ok(axis.ticks.length > 0 && axis.ticks.length <= 1000, 'large values render a bounded number of ticks');
            assert.equal(new Set(axis.ticks.map(tick => tick.value)).size, axis.ticks.length, 'tick coordinates do not collapse at large magnitudes');
            assert.ok(axis.min <= Math.min(...values), 'axis includes the smallest observation');
            assert.ok(axis.max >= Math.max(...values), 'axis includes the largest observation');
            assert.ok(new Set(axis.ticks.map(tick => tick.label)).size > 1, 'value labels remain distinguishable');
            if (values.length === 1) {
                assert.equal(large.charts[0].datasets[0].firstMarkerPixel, large.charts[0].datasets[0].backgroundColor,
                    'single observation is visibly painted in its series colour');
                if (values[0] !== 0) assert.ok(axis.max - axis.min <= Math.abs(values[0]), 'constant range scales with observation magnitude');
            }
        }
        assert.deepEqual(errors, []);
        console.log(`PASS: ${names.length} chart fixtures passed (unaffected JFreeChart parity plus intentional single-point repair); constant-value regressions passed`);
    } finally {
        if (browser) await browser.close();
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
