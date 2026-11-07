/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Draws the chart specification produced by MeasurementChart.toConfig() with the bundled Chart.js,
 * reproducing the JFreeChart 1.5 charts it replaced. BrowserChartRenderer injects this file after
 * chart.umd.min.js into the renderer's headless Chromium and calls
 * window.carlosDrawChart(spec, width, height); scripts/chart-draw-playwright-checks.js exercises the
 * same function against recordings of the JFreeChart output (scripts/fixtures/chart/jfreechart). It
 * returns the Chart instances it drew (measurement panel first, medication panel last).
 *
 * What is reproduced, and where JFreeChart did it:
 *  - the looks: ChartFactory's "JFree" theme (Tahoma 20/14/12 bold titles and labels in dark gray, a
 *    light gray plot with white dashed gridlines, a black outline) and the plain defaults of a chart
 *    built with new JFreeChart(title, plot) (SansSerif 18/12/10 in black, a white plot with light
 *    gray gridlines, a gray outline), which the combined medication charts used;
 *  - the series paint sequence (ChartColor.createDefaultPaintArray), handed out in drawing order
 *    across renderers and subplots to every series without an explicit paint;
 *  - ValueAxis auto ranges: margins, the include-zero and sticky-zero rules of NumberAxis, and the
 *    symbol axis range of -0.5 to n-0.5;
 *  - tick units: NumberAxis's 1-2-5 units and DateAxis's standard units with their formats, chosen by
 *    JFreeChart's two-pass estimate from the label width (horizontal) or height (vertical) in Java's
 *    integer font metrics, and dated ticks anchored the way DateAxis.previousStandardDate anchors them;
 *  - the renderers: 1px lines, 6x6 filled markers (square/circle for BP), high-low range bars with 2px open
 *    and close ticks, item labels 4px above a point, and XYBarRenderer's flat bars with their gray
 *    shadow offset by 4px (a zero-length prescription stays a one pixel line);
 *  - the legend at the bottom with each renderer's legend graphic, and a combined plot's equal
 *    subplot heights with a 5px gap under one shared date axis.
 */
(function (global) {
    'use strict';
    const SECOND = 1000;
    const MINUTE = 60 * SECOND;
    const HOUR = 60 * MINUTE;
    const DAY = 24 * HOUR;
    const LABEL_LIMIT = 33;
    const FONT = '"DejaVu Sans", "Liberation Sans", Arial, sans-serif';
    // DejaVu Sans ascent plus descent, as Java's LineMetrics reported it for the logical fonts in the
    // renderer's container (13.969px at 12px).
    const LINE_HEIGHT = 1.1641;
    const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const PALETTE = ['#ff5555', '#5555ff', '#55ff55', '#ffff55', '#ff55ff', '#55ffff', '#ffafaf', '#808080',
        '#c00000', '#0000c0', '#00c000', '#c0c000', '#c000c0', '#00c0c0', '#404040',
        '#ff4040', '#4040ff', '#40ff40', '#ffff40', '#ff40ff', '#40ffff', '#c0c0c0',
        '#800000', '#000080', '#008000', '#808000', '#800080', '#008080',
        '#ff8080', '#8080ff', '#80ff80', '#ffff80', '#ff80ff', '#80ffff'];
    const LOOKS = {
        jfree: { title: 20, axisLabel: { size: 14, weight: 'bold', color: '#404040' }, tick: { size: 12, color: '#404040' },
            legend: { size: 12, color: '#404040' }, itemLabel: 12, outline: 'rgba(0, 0, 0, 0.5)' },
        plain: { title: 18, axisLabel: { size: 12, weight: 'normal', color: '#000000' }, tick: { size: 10, color: '#000000' },
            legend: { size: 12, color: '#000000' }, itemLabel: 10, outline: 'rgba(128, 128, 128, 0.5)' },
    };
    const DATE_UNITS = [];
    [1, 5, 10, 30].forEach(n => DATE_UNITS.push({ field: 'second', count: n, size: n * SECOND, format: 'HH:mm:ss' }));
    [1, 2, 5, 10, 15, 20, 30].forEach(n => DATE_UNITS.push({ field: 'minute', count: n, size: n * MINUTE, format: 'HH:mm' }));
    [[1, 'HH:mm'], [2, 'HH:mm'], [4, 'd-MMM, HH:mm'], [6, 'd-MMM, HH:mm'], [12, 'd-MMM']]
        .forEach(([n, format]) => DATE_UNITS.push({ field: 'hour', count: n, size: n * HOUR, format }));
    [1, 2, 7, 15].forEach(n => DATE_UNITS.push({ field: 'day', count: n, size: n * DAY, format: 'd-MMM' }));
    [1, 2, 3, 4, 6].forEach(n => DATE_UNITS.push({ field: 'month', count: n, size: n * 31 * DAY, format: 'MMM-yyyy' }));
    [1, 2, 5, 10, 25, 50, 100].forEach(n => DATE_UNITS.push({ field: 'year', count: n, size: n * 365 * DAY, format: 'yyyy' }));
    // The axis' initial unit, whose label (Java's default SimpleDateFormat) seeds the first estimate.
    const INITIAL_DATE_UNIT = { field: 'day', count: 1, size: DAY, format: 'M/d/yy, h:mm a' };
    const NUMBER_UNITS = [];
    for (let k = -9; k <= 308; k++) [1, 2, 5].forEach(m => {
        const unit = m * Math.pow(10, k);
        if (Number.isFinite(unit)) NUMBER_UNITS.push(Number(unit.toPrecision(12)));
    });

    // --- text metrics as Java measured them ----------------------------------------------------

    const scratch = document.createElement('canvas').getContext('2d');

    function font(size, weight) {
        return `${weight === 'bold' ? 'bold ' : ''}${size}px ${FONT}`;
    }

    /** Java sums integer glyph advances; Chromium's advances rounded per glyph give the same widths. */
    function textWidth(text, size, weight) {
        scratch.font = font(size, weight);
        let width = 0;
        for (const character of text) width += Math.round(scratch.measureText(character).width);
        return width;
    }

    function textHeight(size) {
        return LINE_HEIGHT * size;
    }

    function shorten(label) {
        return label.length > LABEL_LIMIT ? label.slice(0, LABEL_LIMIT - 3) + '...' : label;
    }

    // --- calendar arithmetic in the chart's zone ---------------------------------------------------

    function zoneOffset(instant, timeZone) {
        const name = new Intl.DateTimeFormat('en-US', { timeZone, timeZoneName: 'longOffset' })
            .formatToParts(new Date(instant)).find(part => part.type === 'timeZoneName');
        const match = /GMT([+-])(\d{2}):?(\d{2})?/.exec(name ? name.value : '');
        if (!match) return 0;
        return (match[1] === '-' ? -1 : 1) * (Number(match[2]) * 60 + Number(match[3] || 0)) * 60000;
    }

    /** The instant of the zone-local calendar time (month zero-based; overflow in any field allowed). */
    function zonedInstant(timeZone, y, m, d, h, mi, s) {
        const naive = Date.UTC(y, m, d, h || 0, mi || 0, s || 0);
        const once = naive - zoneOffset(naive, timeZone);
        return naive - zoneOffset(once, timeZone);
    }

    function zonedParts(instant, timeZone) {
        const parts = {};
        new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric',
            minute: 'numeric', second: 'numeric', hourCycle: 'h23' })
            .formatToParts(new Date(instant)).forEach(part => { if (part.type !== 'literal') parts[part.type] = Number(part.value); });
        return { year: parts.year, month: parts.month - 1, day: parts.day, hour: parts.hour % 24, minute: parts.minute, second: parts.second };
    }

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function formatDate(instant, format, timeZone) {
        const p = zonedParts(instant, timeZone);
        switch (format) {
            case 'HH:mm:ss': return `${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}`;
            case 'HH:mm': return `${pad(p.hour)}:${pad(p.minute)}`;
            case 'd-MMM, HH:mm': return `${p.day}-${MONTHS[p.month]}, ${pad(p.hour)}:${pad(p.minute)}`;
            case 'd-MMM': return `${p.day}-${MONTHS[p.month]}`;
            case 'MMM-yyyy': return `${MONTHS[p.month]}-${p.year}`;
            case 'yyyy': return String(p.year);
            case 'yyyy.MM.dd': return `${p.year}.${pad(p.month + 1)}.${pad(p.day)}`;
            default: return `${p.month + 1}/${p.day}/${pad(p.year % 100)}, ${p.hour % 12 || 12}:${pad(p.minute)} ${p.hour < 12 ? 'AM' : 'PM'}`;
        }
    }

    /** DateAxis.previousStandardDate for tick marks at the start of the period. */
    function previousStandardDate(instant, unit, timeZone) {
        const p = zonedParts(instant, timeZone);
        const count = unit.count;
        const build = (field, value) => {
            switch (field) {
                case 'second': return zonedInstant(timeZone, p.year, p.month, p.day, p.hour, p.minute, value);
                case 'minute': return zonedInstant(timeZone, p.year, p.month, p.day, p.hour, value, 0);
                case 'hour': return zonedInstant(timeZone, p.year, p.month, p.day, value, 0, 0);
                case 'day': return zonedInstant(timeZone, p.year, p.month, value, 0, 0, 0);
                // Months align on multiples of the count counted from January as month one.
                case 'month': return zonedInstant(timeZone, p.year, value - 1, 1, 0, 0, 0);
                default: return zonedInstant(timeZone, value, 0, 1, 0, 0, 0);
            }
        };
        const current = { second: p.second, minute: p.minute, hour: p.hour, day: p.day, month: p.month + 1, year: p.year }[unit.field];
        const value = count * Math.floor(current / count);
        let date = build(unit.field, value);
        if (date >= instant) date = build(unit.field, value - (unit.field === 'month' ? count : 1));
        return date;
    }

    function addToDate(instant, unit, timeZone) {
        const p = zonedParts(instant, timeZone);
        switch (unit.field) {
            case 'second': case 'minute': case 'hour': return instant + unit.size;
            case 'day': return zonedInstant(timeZone, p.year, p.month, p.day + unit.count, p.hour, p.minute, p.second);
            case 'month': return zonedInstant(timeZone, p.year, p.month + unit.count, p.day, p.hour, p.minute, p.second);
            default: return zonedInstant(timeZone, p.year + unit.count, p.month, p.day, p.hour, p.minute, p.second);
        }
    }

    // --- JFreeChart's auto ranges and tick units ---------------------------------------------------

    /** NumberAxis.autoAdjustRange: include zero on request, then margins that never cross zero. */
    function numberRange(values, margins, includesZero) {
        if (!values.length) return [0, 1];
        let lower = Math.min(...values), upper = Math.max(...values);
        if (includesZero) { lower = Math.min(lower, 0); upper = Math.max(upper, 0); }
        // A fixed epsilon disappears at large magnitudes (for example a viral load of 100,000,000).
        // Keep the expansion representable so a constant series cannot produce an unbounded tick loop.
        const minimum = Math.max(1e-8, Math.max(Math.abs(lower), Math.abs(upper)) * 1e-12);
        if (upper - lower < minimum) { const middle = lower + (upper - lower) / 2; upper = middle + minimum / 2; lower = middle - minimum / 2; }
        const range = upper - lower;
        upper = upper <= 0 ? Math.min(0, upper + margins[1] * range) : upper + margins[1] * range;
        lower = lower >= 0 ? Math.max(0, lower - margins[0] * range) : lower - margins[0] * range;
        return [lower, upper];
    }

    /** DateAxis.autoAdjustRange; a single instant gets half a day each side rather than JFreeChart's two milliseconds. */
    function dateRange(values, margins) {
        const lower = Math.min(...values), upper = Math.max(...values);
        const span = upper - lower;
        if (span < SECOND) return [lower - 12 * HOUR, upper + 12 * HOUR];
        return [lower - margins[0] * span, upper + margins[1] * span];
    }

    function numberDecimals(unit) {
        return Math.max(0, -Math.floor(Math.log10(unit) + 1e-9));
    }

    function formatNumber(value, decimals) {
        return Number(value).toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
    }

    /**
     * ValueAxis.selectAutoTickUnit: estimate the unit from the current unit's label size, take the
     * ceiling unit of the estimate, and step up once if that unit's own labels do not fit. The axis
     * is laid out twice (once to reserve space, once to draw), so the choice is made twice, the
     * second time starting from the first choice.
     */
    function selectUnit(units, initial, pixelsPerSize, labelSize) {
        let current = initial;
        for (let pass = 0; pass < 2; pass++) {
            const unit1 = units.find(unit => unit.size >= current.size) || units[units.length - 1];
            const guess = labelSize(current) / (pixelsPerSize * unit1.size) * unit1.size;
            let index = units.findIndex(unit => unit.size >= guess);
            if (index < 0) index = units.length - 1;
            if (pixelsPerSize * units[index].size < labelSize(units[index])) index = Math.min(index + 1, units.length - 1);
            current = units[index];
        }
        return current;
    }

    function numberTicks(lower, upper, pixels, vertical, fontSize) {
        if (!Number.isFinite(lower) || !Number.isFinite(upper) || !(upper > lower)) {
            throw new Error('Chart numeric axis has invalid bounds');
        }
        const units = NUMBER_UNITS.map(size => ({ size }));
        const pixelsPerSize = pixels / (upper - lower);
        const labelSize = vertical
            ? () => textHeight(fontSize) + 4
            : unit => 8 + Math.max(textWidth(formatNumber(lower, numberDecimals(unit.size)), fontSize), textWidth(formatNumber(upper, numberDecimals(unit.size)), fontSize));
        const unit = selectUnit(units, { size: 1 }, pixelsPerSize, labelSize).size;
        const decimals = numberDecimals(unit);
        const ticks = [];
        // The same double arithmetic as NumberAxis.calculateLowestVisibleTickValue and calculateVisibleTickCount.
        const first = Math.ceil(lower / unit), last = Math.floor(upper / unit);
        const count = last - first + 1;
        if (!Number.isSafeInteger(first) || !Number.isSafeInteger(last) || count > 1000) {
            throw new Error('Chart numeric axis has too many ticks');
        }
        for (let offset = 0; offset < count; offset++) {
            const value = Number(((first + offset) * unit).toPrecision(15));
            ticks.push({ value, label: formatNumber(value, decimals) });
        }
        return { unit, ticks };
    }

    function dateTicks(lower, upper, pixels, fontSize, timeZone) {
        const pixelsPerMs = pixels / (upper - lower);
        const labelSize = unit => 8 + Math.max(textWidth(formatDate(lower, unit.format, timeZone), fontSize), textWidth(formatDate(upper, unit.format, timeZone), fontSize));
        const unit = selectUnit(DATE_UNITS, INITIAL_DATE_UNIT, pixelsPerMs, labelSize);
        const ticks = [];
        let tick = addToDate(previousStandardDate(lower, unit, timeZone), unit, timeZone);
        for (let guard = 0; tick < upper && guard < 1000; guard++) {
            ticks.push({ value: tick, label: formatDate(tick, unit.format, timeZone) });
            tick = addToDate(tick, unit, timeZone);
        }
        return { unit, ticks };
    }

    // --- the drawing -----------------------------------------------------------------------------

    function hex(colour) {
        return colour;
    }

    function legendGraphic(dataset, circle) {
        // The high-low renderer's legend item is a square, for its ranges and for a series it drew.
        if (dataset.kind === 'range' || dataset.highLow) return 'shape8x8';
        if (dataset.lines !== false && dataset.shapes) return circle ? 'lineAndCircle' : 'lineAndShape';
        if (dataset.lines !== false) return 'line';
        return 'shape6x6';
    }

    function drawMarker(ctx, x, y, circle) {
        if (circle) {
            ctx.beginPath(); ctx.arc(x, y, 3, 0, 2 * Math.PI); ctx.fill();
        } else {
            ctx.fillRect(x - 3, y - 3, 6, 6);
        }
    }

    function drawLegendGraphic(ctx, item, x, y) {
        ctx.fillStyle = item.colour; ctx.strokeStyle = item.colour; ctx.lineWidth = 1;
        if (['line', 'lineAndShape', 'lineAndCircle'].includes(item.graphic)) {
            ctx.beginPath(); ctx.moveTo(x, y + 0.5); ctx.lineTo(x + 14, y + 0.5); ctx.stroke();
        }
        if (['lineAndShape', 'lineAndCircle', 'shape6x6'].includes(item.graphic)) {
            drawMarker(ctx, x + 7, y, item.graphic === 'lineAndCircle');
        }
        if (item.graphic === 'shape8x8') ctx.fillRect(x + 3, y - 4, 8, 8);
        if (item.graphic === 'shape6x10') ctx.fillRect(x + 4, y - 5, 6, 10);
    }

    function graphicWidth(graphic) {
        return graphic === 'line' || graphic === 'lineAndShape' ? 14 : graphic === 'shape6x10' || graphic === 'shape6x6' ? 14 : 14;
    }

    /** JFreeChart's legend: items flow left to right, wrap, and sit centred under the plot. */
    function legendLayout(items, width, look) {
        const rowHeight = Math.ceil(textHeight(look.legend.size)) + 6;
        const rows = [[]];
        let rowWidth = 0;
        items.forEach(item => {
            item.width = graphicWidth(item.graphic) + 3 + textWidth(item.label, look.legend.size) + 8;
            if (rowWidth + item.width > width - 4 && rows[rows.length - 1].length) { rows.push([]); rowWidth = 0; }
            rows[rows.length - 1].push(item);
            rowWidth += item.width;
        });
        return { rows, rowHeight, height: rows.length * rowHeight + 4 };
    }

    function drawLegend(chart, layout, look) {
        const ctx = chart.ctx;
        const canvasWidth = chart.width, canvasHeight = chart.height;
        ctx.save();
        ctx.font = font(look.legend.size);
        ctx.textBaseline = 'middle';
        ctx.textAlign = 'left';
        let top = canvasHeight - layout.height + 2;
        layout.rows.forEach(row => {
            const rowWidth = row.reduce((sum, item) => sum + item.width, 0);
            let x = Math.round((canvasWidth - rowWidth) / 2);
            const y = top + layout.rowHeight / 2;
            row.forEach(item => {
                drawLegendGraphic(ctx, item, x + 2, y);
                ctx.fillStyle = look.legend.color;
                ctx.fillText(item.label, x + graphicWidth(item.graphic) + 3, y);
                x += item.width;
            });
            top += layout.rowHeight;
        });
        ctx.restore();
    }

    function axisTitle(text, look, colour) {
        return { display: !!text, text: text || '', color: colour || look.axisLabel.color,
            font: { family: FONT, size: look.axisLabel.size, weight: look.axisLabel.weight, lineHeight: LINE_HEIGHT }, padding: { top: 5, bottom: 3 } };
    }

    function tickOptions(look, colour, extra) {
        return { display: true, color: colour || look.tick.color, font: { family: FONT, size: look.tick.size, lineHeight: LINE_HEIGHT },
            padding: 2, maxRotation: 0, autoSkip: false, includeBounds: false, callback(value) { return tickLabelFor(this, value); }, ...(extra || {}) };
    }

    function tickLabelFor(scale, value) {
        const tick = (scale.carlosTicks || []).find(t => t.value === value);
        return tick ? tick.label : '';
    }

    function gridOptions(colour) {
        const rgba = colour ? colour.replace('#', '') : null;
        const gridColour = rgba ? `rgba(${parseInt(rgba.slice(0, 2), 16)}, ${parseInt(rgba.slice(2, 4), 16)}, ${parseInt(rgba.slice(4, 6), 16)}, 0.5)` : 'rgba(0,0,0,0)';
        // Declare the image contract explicitly, independently of Chart.js defaults.
        return { display: true, drawOnChartArea: true, color: gridColour, lineWidth: 1, drawTicks: true, tickLength: 2, tickColor: 'rgba(128, 128, 128, 0.5)' };
    }

    function drawChart(spec, width, height) {
        document.body.replaceChildren();
        document.body.style.cssText = 'margin:0;background:white';
        const style = { theme: 'jfree', plotBackground: '#c0c0c0', gridlines: '#ffffff', domainMargins: [0.02, 0.02], rangeMargins: [0.05, 0.05],
            rangeIncludesZero: false, domainIncludesZero: false, rangeAxisPaint: null, itemLabels: null, ...(spec.style || {}) };
        const timeZone = spec.timeZone;
        const dateAxis = spec.dateAxis !== false;
        const datasets = spec.datasets || [];
        const medications = spec.medications || [];
        const combined = medications.length > 0;
        const panelLook = LOOKS[style.theme] || LOOKS.jfree;
        const chartLook = combined ? LOOKS.plain : panelLook;
        const errorsOf = [];
        void errorsOf;

        // Paints: the sequence runs through the datasets in drawing order, then the medication series.
        let next = 0;
        const paintOf = paint => paint || PALETTE[next++ % PALETTE.length];
        const colours = datasets.map(dataset => paintOf(dataset.paint));
        const names = [...new Set(medications.map(m => m.name))];
        const drugColours = names.map(() => paintOf(null));

        // The domain range covers every dataset and, in a combined plot, the prescriptions.
        const xs = datasets.flatMap(dataset => dataset.data.map(point => point.x));
        if (combined) medications.forEach(m => xs.push(m.start, m.end));
        const domainMargins = combined ? [0.05, 0.05] : style.domainMargins;
        const [xLower, xUpper] = xs.length ? (dateAxis ? dateRange(xs, domainMargins) : numberRange(xs, domainMargins, style.domainIncludesZero)) : [0, 1];

        const rangeValues = axis => datasets.flatMap(dataset => {
            if (dataset.yAxisID !== axis && !(axis === 'y' && !dataset.yAxisID)) return [];
            // Ranges mapped to the value axis widened it; those on a second index of the same axis did not.
            if (dataset.kind === 'range') return dataset.widensAxis ? dataset.data.flatMap(point => [point.low, point.high]) : [];
            return dataset.data.map(point => point.y);
        });
        const [yLower, yUpper] = numberRange(rangeValues('y'), style.rangeMargins, style.rangeIncludesZero);
        const [y2Lower, y2Upper] = numberRange(rangeValues('y2'), [0.05, 0.05], false);

        // ScatterPlotChartServlet's two-series BP renderer assigned a square to systolic
        // and a circle to diastolic. Other measurement renderers use only the first shape.
        const circleMarker = index => !dateAxis && index === 1;
        const legendItems = [];
        datasets.forEach((dataset, index) => legendItems.push({ label: dataset.label, graphic: legendGraphic(dataset, circleMarker(index)), colour: colours[index],
            fontSize: chartLook.legend.size, textColour: chartLook.legend.color }));
        names.forEach((name, index) => legendItems.push({ label: name, graphic: 'shape6x10', colour: drugColours[index],
            fontSize: chartLook.legend.size, textColour: chartLook.legend.color }));
        const legend = legendLayout(legendItems, width, chartLook);

        const charts = [];
        const xScale = (look, shared, gridColour = style.gridlines) => ({
            type: 'linear', min: xLower, max: xUpper, offset: false,
            afterBuildTicks(scale) {
                const length = Math.max(1, scale.maxWidth);
                const chosen = dateAxis ? dateTicks(xLower, xUpper, length, look.tick.size, timeZone)
                    : numberTicks(xLower, xUpper, length, false, look.tick.size);
                scale.carlosTicks = chosen.ticks;
                scale.ticks = chosen.ticks.map(tick => ({ value: tick.value }));
            },
            ticks: tickOptions(look, null, shared ? {} : { display: false }),
            grid: { ...gridOptions(gridColour), drawTicks: shared },
            border: { display: shared, color: 'rgba(128, 128, 128, 0.5)', width: 1, dash: [2, 2] },
            title: shared ? axisTitle(combined ? 'Date/Time' : spec.xLabel, look) : { display: false },
        });
        const yScale = (id, lower, upper, look, label, colour, position) => ({
            type: 'linear', min: lower, max: upper, position, offset: false,
            afterBuildTicks(scale) {
                const chosen = numberTicks(lower, upper, Math.max(1, scale.maxHeight), true, look.tick.size);
                scale.carlosTicks = chosen.ticks;
                scale.ticks = chosen.ticks.map(tick => ({ value: tick.value }));
            },
            ticks: tickOptions(look, colour),
            grid: id === 'y' ? gridOptions(style.gridlines) : { display: false, drawTicks: true, tickLength: 2, tickColor: 'rgba(128, 128, 128, 0.5)' },
            border: { display: true, color: 'rgba(128, 128, 128, 0.5)', width: 1, dash: [2, 2] },
            title: axisTitle(label, look, colour),
        });

        /** Plot background before, outline after, and the JFreeChart renderer details Chart.js lacks. */
        const jfreePlugin = (look, panel) => ({
            id: 'jfreeLook',
            beforeDraw(chart) {
                const area = chart.chartArea;
                chart.ctx.save();
                chart.ctx.fillStyle = panel.background;
                chart.ctx.fillRect(area.left, area.top, area.right - area.left, area.bottom - area.top);
                chart.ctx.restore();
            },
            beforeDatasetsDraw(chart) {
                if (!panel.bars) return;
                const ctx = chart.ctx;
                chart.carlosShadows = [];
                ctx.save();
                ctx.fillStyle = '#808080';
                chart.getDatasetMeta(0).data.forEach(bar => {
                    const left = Math.round(Math.min(bar.x, bar.base)), right = Math.max(left + 1, Math.round(Math.max(bar.x, bar.base)));
                    const top = Math.round(bar.y - bar.height / 2);
                    ctx.fillRect(left + 4, top + 4, right - left, Math.round(bar.height));
                    chart.carlosShadows.push({ dx: 4, dy: 4, colour: '#808080' });
                });
                ctx.restore();
            },
            afterDatasetsDraw(chart) {
                const ctx = chart.ctx;
                const area = chart.chartArea;
                ctx.save();
                ctx.beginPath(); ctx.rect(area.left, area.top, area.right - area.left, area.bottom - area.top); ctx.clip();
                if (panel.bars) {
                    // A zero-length interval is still drawn: a one pixel line in the series colour.
                    const fills = [].concat(chart.data.datasets[0].backgroundColor);
                    chart.getDatasetMeta(0).data.forEach((bar, index) => {
                        if (Math.abs(bar.x - bar.base) > 1.5) return;
                        ctx.fillStyle = fills[index];
                        ctx.fillRect(Math.round(Math.min(bar.x, bar.base)), Math.round(bar.y - bar.height / 2), 1, Math.round(bar.height));
                    });
                }
                if (panel.series) {
                    chart.carlosRanges = [];
                    chart.carlosItemLabels = [];
                    chart.data.datasets.forEach((dataset, index) => {
                        const source = datasets[index];
                        const colour = colours[index];
                        const yAxis = chart.scales[dataset.yAxisID] || chart.scales.y;
                        if (source.kind === 'range') {
                            ctx.strokeStyle = colour; ctx.lineWidth = 1;
                            source.data.forEach(point => {
                                const x = Math.round(chart.scales.x.getPixelForValue(point.x)) + 0.5;
                                const low = yAxis.getPixelForValue(point.low), high = yAxis.getPixelForValue(point.high);
                                ctx.beginPath(); ctx.moveTo(x, low); ctx.lineTo(x, high);
                                ctx.moveTo(x - 2, low); ctx.lineTo(x, low); ctx.moveTo(x, high); ctx.lineTo(x + 2, high); ctx.stroke();
                                chart.carlosRanges.push({ x: point.x, low: point.low, high: point.high, colour });
                            });
                            return;
                        }
                        const elements = chart.getDatasetMeta(index).data;
                        if (source.shapes) {
                            ctx.fillStyle = colour;
                            elements.forEach(element => drawMarker(ctx, Math.round(element.x), Math.round(element.y), circleMarker(index)));
                        }
                        const labels = style.itemLabels;
                        if (!labels || source.kind !== 'line') return;
                        const primary = datasets.findIndex(d => d.kind === 'line') === index;
                        const size = labels.fontSize || look.itemLabel;
                        ctx.font = font(size); ctx.fillStyle = '#000000'; ctx.textAlign = 'center'; ctx.textBaseline = 'bottom';
                        source.data.forEach((point, i) => {
                            const element = elements[i];
                            if (!element) return;
                            const text = itemLabel(point, primary ? labels.format : '{2}', primary ? labels.decimals : '0.###', timeZone);
                            ctx.fillText(text, element.x, element.y - 4);
                            chart.carlosItemLabels.push({ text, x: element.x, y: element.y - 4, fontSize: size });
                        });
                    });
                }
                ctx.restore();
            },
            afterDraw(chart) {
                const area = chart.chartArea;
                const ctx = chart.ctx;
                ctx.save();
                ctx.strokeStyle = look.outline; ctx.lineWidth = 1;
                ctx.strokeRect(area.left + 0.5, area.top + 0.5, area.right - area.left - 1, area.bottom - area.top - 1);
                ctx.restore();
                if (panel.legend) { drawLegend(chart, legend, chartLook); chart.carlosLegend = legendItems; }
            },
        });

        const titleOptions = show => ({ display: show, text: spec.title || '', color: '#000000', align: 'center',
            font: { family: FONT, size: chartLook.title, weight: 'bold', lineHeight: LINE_HEIGHT }, padding: { top: 2, bottom: 4 } });
        const basePadding = { left: 8, right: 12, top: 4, bottom: 4 };

        let mainChart = null;
        let medicationChart = null;
        const createPanels = topHeight => {
            charts.length = 0;
            document.querySelectorAll('canvas').forEach(canvas => canvas.remove());
            mainChart = null; medicationChart = null;
            // No matching prescriptions is a valid medication request; retain its title and axes.
            if (datasets.length || !combined) {
                const main = document.createElement('canvas');
                main.style.display = 'block';
                main.width = width;
                main.height = combined ? topHeight : height;
                document.body.append(main);
                const scales = { x: xScale(combined ? LOOKS.plain : panelLook, !combined), y: yScale('y', yLower, yUpper, panelLook, spec.yLabel, style.rangeAxisPaint, 'left') };
                if (spec.secondAxis) scales.y2 = yScale('y2', y2Lower, y2Upper, LOOKS.plain, spec.secondAxis, null, 'right');
                mainChart = new Chart(main, {
                    type: 'scatter',
                    data: { datasets: datasets.map((dataset, index) => ({
                        label: dataset.label, carlosKind: dataset.kind, yAxisID: dataset.yAxisID || 'y',
                        data: dataset.kind === 'range' ? [] : dataset.data.map(point => ({ x: point.x, y: point.y })),
                        showLine: dataset.kind !== 'range' && dataset.lines !== false, borderColor: colours[index], backgroundColor: colours[index],
                        borderWidth: 1, pointRadius: 0, pointHitRadius: 0, tension: 0, carlosShapes: !!dataset.shapes })) },
                    plugins: [jfreePlugin(panelLook, { background: style.plotBackground, series: true, legend: !combined })],
                    options: { responsive: false, animation: false, devicePixelRatio: 1, scales,
                        layout: { padding: { ...basePadding, bottom: basePadding.bottom + (combined ? 0 : legend.height) } },
                        plugins: { title: titleOptions(true), legend: { display: false }, tooltip: { enabled: false } } } });
                charts.push(mainChart);
            }
            if (combined) {
                const meds = document.createElement('canvas');
                meds.style.display = 'block';
                meds.width = width;
                meds.height = mainChart ? height - topHeight : height;
                document.body.append(meds);
                const colourOf = name => drugColours[names.indexOf(name)];
                medicationChart = new Chart(meds, {
                    type: 'bar',
                    // Category identity uses the full name. Shortening it here would put repeated
                    // prescriptions on the wrong row when two medication names share a prefix.
                    data: { labels: names, datasets: [{ label: 'Meds', carlosKind: 'bar',
                        data: medications.map(m => ({ y: m.name, x: [m.start, m.end] })),
                        backgroundColor: medications.map(m => colourOf(m.name)), borderWidth: 0, categoryPercentage: 0.6, barPercentage: 1, minBarLength: 1 }] },
                    plugins: [jfreePlugin(LOOKS.plain, { background: '#ffffff', bars: true, legend: true })],
                    options: { indexAxis: 'y', responsive: false, animation: false, devicePixelRatio: 1,
                        scales: { x: xScale(LOOKS.plain, true, '#c0c0c0'),
                            // reverse: the first medication listed sits at the bottom, as on the former symbol axis.
                            y: { type: 'category', labels: names, reverse: true, offset: true,
                                ticks: { ...tickOptions(LOOKS.plain, '#000000'), callback(value) { return shorten(this.getLabelForValue(value)); } },
                                grid: { ...gridOptions('#c0c0c0'), offset: false }, border: { display: true, color: 'rgba(128, 128, 128, 0.5)', width: 1, dash: [2, 2] },
                                title: axisTitle('Meds', LOOKS.plain, '#000000') } },
                        layout: { padding: { ...basePadding, top: mainChart ? 5 : basePadding.top, bottom: basePadding.bottom + legend.height } },
                        plugins: { title: titleOptions(!mainChart), legend: { display: false }, tooltip: { enabled: false } } } });
                charts.push(medicationChart);
            }
        };
        let topHeight = Math.round(height / 2);
        createPanels(topHeight);
        if (mainChart && medicationChart) {
            // Equal subplot heights with a 5px gap: JFreeChart gave each subplot the same weight.
            for (let attempt = 0; attempt < 4; attempt++) {
                const heights = [mainChart, medicationChart].map(chart => chart.chartArea.bottom - chart.chartArea.top);
                if (Math.abs(heights[1] - heights[0]) <= 1) break;
                topHeight += Math.round((heights[1] - heights[0]) / 2);
                [mainChart, medicationChart].forEach(chart => chart.destroy());
                createPanels(topHeight);
            }
            // Matching date bounds alone is insufficient: long medication names consume more left-axis
            // space. Align the plot rectangles before producing a print image.
            const panels = [mainChart, medicationChart];
            for (let attempt = 0; attempt < 4; attempt++) {
                const left = Math.max(...panels.map(chart => chart.chartArea.left));
                const right = Math.min(...panels.map(chart => chart.chartArea.right));
                if (panels.every(chart => Math.abs(chart.chartArea.left - left) <= 1 && Math.abs(chart.chartArea.right - right) <= 1)) break;
                panels.forEach(chart => {
                    const padding = chart.options.layout.padding;
                    chart.options.layout.padding = { top: padding.top || 0, bottom: padding.bottom || 0,
                        left: (padding.left || 0) + left - chart.chartArea.left, right: (padding.right || 0) + chart.chartArea.right - right };
                    chart.update('none');
                });
            }
            if (Math.abs(mainChart.chartArea.left - medicationChart.chartArea.left) > 1
                    || Math.abs(mainChart.chartArea.right - medicationChart.chartArea.right) > 1) {
                throw new Error('Chart date axes could not be aligned');
            }
        }
        // Ticks were chosen while the layout was still settling; settle them against the final plot size.
        charts.forEach(chart => chart.update('none'));
        return charts;
    }

    function itemLabel(point, format, decimals, timeZone) {
        const value = decimals === '0.00' ? Number(point.y).toFixed(2)
            : Number(Number(point.y).toFixed(3)).toLocaleString('en-US', { maximumFractionDigits: 3 });
        const date = formatDate(point.x, 'yyyy.MM.dd', timeZone);
        // JFreeChart drew the generator's "\n" as nothing visible, leaving the spaces around it.
        return format.replaceAll('{1}', date).replaceAll('{2}', value).replaceAll('\n', '');
    }

    void hex;
    global.carlosDrawChart = drawChart;
})(window);
