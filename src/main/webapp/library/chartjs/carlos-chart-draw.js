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
 *  - the renderers: 1px lines, 6x6 markers filled and outlined (7px) from DefaultDrawingSupplier's shape sequence (a square for
 *    the first series, a circle for the second, as blood pressure showed them), high-low range bars with 2px open
 *    and close ticks, item labels 4px above a point, and XYBarRenderer's flat bars with their gray
 *    shadow offset by 4px (a zero-length prescription stays a one pixel line);
 *  - the legend at the bottom with each renderer's legend graphic (one centred row, or rows flowing from
 *    the left edge once FlowArrangement wraps them), and a combined plot's equal
 *    subplot heights with a 5px gap under one shared date axis.
 */
(function (global) {
    'use strict';
    const SECOND = 1000;
    const MINUTE = 60 * SECOND;
    const HOUR = 60 * MINUTE;
    const DAY = 24 * HOUR;
    // SymbolAxis rows: names longer than 30 characters were cut to 30 plus "...".
    const LABEL_LIMIT = 30;
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
    // DateAxis.createStandardDateTickUnits as JFreeChart 1.5.6 lists them.
    const DATE_UNITS = [];
    [1, 5, 10, 25, 50, 100, 250, 500].forEach(n => DATE_UNITS.push({ field: 'millisecond', count: n, size: n, format: 'HH:mm:ss.SSS' }));
    [1, 5, 10, 30].forEach(n => DATE_UNITS.push({ field: 'second', count: n, size: n * SECOND, format: 'HH:mm:ss' }));
    [1, 2, 5, 10, 15, 20, 30].forEach(n => DATE_UNITS.push({ field: 'minute', count: n, size: n * MINUTE, format: 'HH:mm' }));
    [[1, 'HH:mm'], [2, 'HH:mm'], [4, 'HH:mm'], [6, 'HH:mm'], [12, 'd-MMM, HH:mm']]
        .forEach(([n, format]) => DATE_UNITS.push({ field: 'hour', count: n, size: n * HOUR, format }));
    [1, 2, 7, 15].forEach(n => DATE_UNITS.push({ field: 'day', count: n, size: n * DAY, format: 'd-MMM' }));
    [1, 2, 3, 4, 6].forEach(n => DATE_UNITS.push({ field: 'month', count: n, size: n * 31 * DAY, format: 'MMM-yyyy' }));
    [1, 2, 5, 10, 25, 50, 100].forEach(n => DATE_UNITS.push({ field: 'year', count: n, size: n * 365 * DAY, format: 'yyyy' }));
    // The axis' initial unit, whose label (Java's default SimpleDateFormat) seeds the first estimate.
    const INITIAL_DATE_UNIT = { field: 'day', count: 1, size: DAY, format: 'M/d/yy, h:mm a' };
    const NUMBER_UNITS = [];
    // NumberTickUnitSource has no lower bound: a constant series' 1e-8 range picks units near 1e-10.
    for (let k = -30; k <= 308; k++) [1, 2, 5].forEach(m => {
        const unit = m * Math.pow(10, k);
        if (Number.isFinite(unit)) NUMBER_UNITS.push(Number(unit.toPrecision(12)));
    });

    // --- text metrics as Java measured them ----------------------------------------------------

    const scratch = document.createElement('canvas').getContext('2d');

    function font(size, weight) {
        return `${weight === 'bold' ? 'bold ' : ''}${size}px ${FONT}`;
    }

    // Java's integer advances for DejaVu Sans (what "Tahoma", "SansSerif" and "helvetica" resolved to in
    // the renderer's Ubuntu 26.04 container), U+0020 to U+00FF at the plain and bold sizes the charts
    // draw, recorded with JFreeChart's rendering hints by scripts/fixtures/chart/jfreechart/JavaAdvances.java.
    const JAVA_ADVANCES = {
        8: [3,3,4,7,5,8,6,2,3,3,4,7,3,3,3,3,5,5,5,5,5,5,5,5,5,5,3,3,7,7,7,4,8,5,5,6,6,5,5,6,6,2,2,5,4,7,6,6,5,6,6,5,5,6,5,8,5,5,5,3,3,3,7,4,4,5,5,4,5,5,3,5,5,2,2,5,2,8,5,5,5,5,3,4,3,5,5,7,5,5,4,5,3,5,7,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,3,5,5,5,5,3,4,4,8,4,5,7,0,8,4,4,7,3,3,4,5,5,3,4,3,4,5,8,8,8,4,5,5,5,5,5,5,8,6,5,5,5,5,2,2,2,2,6,6,6,6,6,6,6,7,6,6,6,6,6,5,5,5,5,5,5,5,5,5,8,4,5,5,5,5,2,2,2,2,5,5,5,5,5,5,5,7,5,5,5,5,5,5,5,5],
        10: [3,4,5,8,6,10,9,3,4,4,5,8,3,4,3,3,6,6,6,6,6,6,6,6,6,6,3,3,8,8,8,5,11,7,7,8,8,7,6,8,8,3,3,7,6,9,8,8,7,8,7,7,5,8,7,9,6,7,6,4,3,4,8,5,5,6,6,5,6,6,4,6,6,2,2,5,2,10,6,6,6,6,4,5,4,6,6,8,6,6,5,6,3,6,8,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,4,6,6,6,6,3,5,5,10,5,6,8,0,10,5,5,8,4,4,5,7,6,3,5,4,5,6,10,10,10,5,7,7,7,7,7,7,10,8,7,7,7,7,3,3,3,3,8,8,8,8,8,8,8,8,8,8,8,8,8,7,7,6,6,6,6,6,6,6,10,5,6,6,6,6,2,2,2,2,6,6,6,6,6,6,6,8,6,6,6,6,6,6,6,6],
        12: [4,5,5,10,8,11,10,3,5,5,6,10,4,4,4,4,8,8,8,8,8,8,8,8,8,8,4,4,10,10,10,6,13,8,8,8,9,8,7,9,9,3,3,7,6,10,9,9,8,9,8,8,7,9,8,11,7,7,9,5,4,5,10,6,6,8,8,7,8,8,4,8,8,3,3,7,3,11,8,8,8,8,5,7,5,8,6,9,6,6,5,8,4,8,10,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,5,8,8,8,8,4,6,6,12,6,7,10,0,12,6,6,10,5,5,6,8,8,4,6,5,6,7,12,12,12,6,8,8,8,8,8,8,12,8,8,8,8,8,3,3,3,3,9,9,9,9,9,9,9,10,9,9,9,9,9,7,8,8,8,8,8,8,8,8,12,7,8,8,8,8,3,3,3,3,8,8,8,8,8,8,8,10,8,8,8,8,8,6,8,6],
        bold12: [4,5,6,10,8,12,10,4,5,5,6,10,5,5,5,4,8,8,8,8,8,8,8,8,8,8,5,5,10,10,10,7,12,9,9,9,10,8,8,10,10,4,4,9,8,12,10,11,9,10,9,9,8,10,9,14,9,8,10,5,4,5,10,6,6,9,9,7,9,9,5,9,9,4,4,8,4,12,9,9,9,9,6,8,6,9,7,12,7,8,8,9,4,9,10,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,5,8,8,8,8,4,6,6,12,7,8,10,0,12,6,6,10,5,5,6,9,8,5,6,5,7,8,12,12,12,7,9,9,9,9,9,9,13,9,8,8,8,8,4,4,4,4,10,10,11,11,11,11,11,10,11,10,10,10,10,8,9,9,9,9,9,9,9,9,14,7,9,9,9,9,4,4,4,4,9,9,9,9,9,9,9,10,9,9,9,9,9,8,9,8],
        bold14: [5,6,7,12,10,14,13,4,6,6,7,12,5,6,5,5,10,10,10,10,10,10,10,10,10,10,6,6,12,12,12,8,14,11,10,11,11,9,9,12,11,4,4,11,9,13,11,12,10,12,10,10,10,11,11,15,10,10,11,6,5,6,12,7,7,9,10,9,10,10,6,10,10,4,4,9,4,14,10,10,10,10,7,9,6,10,9,14,9,9,9,10,5,10,12,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5,6,10,10,9,10,5,7,7,14,8,9,12,0,14,7,7,12,6,6,7,10,9,5,7,6,8,9,15,15,15,8,11,11,11,11,11,11,15,11,9,9,9,9,4,4,4,4,11,11,12,12,12,12,12,12,12,11,11,11,11,10,10,10,9,9,9,9,9,9,16,9,10,10,10,10,4,4,4,4,10,10,10,10,10,10,10,12,10,10,10,10,10,9,10,9],
        bold18: [6,8,9,15,13,18,16,6,8,8,9,15,7,7,7,7,13,13,13,13,13,13,13,13,13,13,7,7,15,15,15,10,18,14,14,13,15,12,12,15,16,7,7,14,11,18,15,15,13,15,14,13,12,16,14,20,14,13,13,8,7,8,15,9,9,12,13,11,13,12,8,13,13,6,6,12,6,18,13,12,13,13,9,11,9,13,12,16,12,12,10,13,7,13,15,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,8,13,13,11,13,7,9,9,18,10,12,15,0,18,9,9,15,8,8,9,13,11,7,9,8,10,12,19,19,19,10,14,14,14,14,14,14,20,13,12,12,12,12,7,7,7,7,15,15,15,15,15,15,15,15,15,16,16,16,16,13,13,14,12,12,12,12,12,12,18,11,12,12,12,12,6,6,6,6,12,13,12,12,12,12,12,15,12,13,13,13,13,12,13,12],
        bold20: [7,9,10,17,14,20,17,6,9,9,10,17,8,8,8,7,14,14,14,14,14,14,14,14,14,14,8,8,17,17,17,12,20,15,15,15,17,14,14,16,17,7,7,16,13,20,17,17,15,17,15,14,14,16,15,22,15,14,15,9,7,9,17,10,10,14,14,12,14,14,9,14,14,7,7,13,7,21,14,14,14,14,10,12,10,14,13,18,13,13,12,14,7,14,17,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,7,9,14,14,13,14,7,10,10,20,11,13,17,0,20,10,10,17,9,9,10,15,13,8,10,9,11,13,21,21,21,12,15,15,15,15,15,15,22,15,14,14,14,14,7,7,7,7,17,17,17,17,17,17,17,17,17,16,16,16,16,14,15,14,14,14,14,14,14,14,21,12,14,14,14,14,7,7,7,7,14,14,14,14,14,14,14,17,14,14,14,14,14,13,14,13],
    };

    /**
     * Java sums integer glyph advances. Text in the recorded range uses Java's own advances; anything
     * else (other sizes, other scripts) falls back to Chromium's advances rounded per glyph, which
     * agree with Java's for most glyphs.
     */
    function glyphAdvances(text, size, weight) {
        const table = JAVA_ADVANCES[weight === 'bold' ? `bold${size}` : size];
        scratch.font = font(size, weight);
        return Array.from(text, glyph => {
            const code = glyph.codePointAt(0);
            const java = table && code >= 32 && code <= 255 ? table[code - 32] : 0;
            return { glyph, advance: java || Math.round(scratch.measureText(glyph).width) };
        });
    }

    function textWidth(text, size, weight) {
        return glyphAdvances(text, size, weight).reduce((width, glyph) => width + glyph.advance, 0);
    }

    /**
     * Java2D placed each glyph at the previous glyph's integer advance, without kerning, so its labels
     * were exactly as wide as textWidth says. Chromium spaces a string by fractional advances: a long
     * medication name in the legend came out four pixels wider and ran into the next item's marker.
     * Latin-1 text is therefore drawn a glyph at a time at those advances. Other scripts keep
     * Chromium's own shaping, which joining and combining marks need.
     */
    function javaText(canvas) {
        const ctx = canvas.getContext('2d');
        const fillText = ctx.fillText;
        ctx.fillText = function (text, x, y, maxWidth) {
            const value = String(text);
            y += this.carlosDrop || 0;
            const size = /(\d+(?:\.\d+)?)px/.exec(this.font);
            // eslint-disable-next-line no-control-regex
            if (!size || !value || /[^\u0020-\u00ff]/.test(value)) {
                return maxWidth === undefined ? fillText.call(this, value, x, y) : fillText.call(this, value, x, y, maxWidth);
            }
            const advances = glyphAdvances(value, Number(size[1]), /\bbold\b|\b[6-9]00\b/.test(this.font) ? 'bold' : 'normal');
            const width = advances.reduce((sum, glyph) => sum + glyph.advance, 0);
            const align = this.textAlign, baseline = this.textBaseline;
            let left = align === 'center' ? x - width / 2 : (align === 'right' || align === 'end') ? x - width : x;
            let top = y - this.measureText('H').alphabeticBaseline;
            const t = this.getTransform();
            // Glyphs sit on a whole-pixel baseline, rounded as Chromium itself rounds it.
            if (t.b === 0 && t.c === 0 && t.d === 1) top = Math.floor(top + t.f + 0.5) - t.f;
            this.textAlign = 'left'; this.textBaseline = 'alphabetic';
            try {
                advances.forEach(glyph => { fillText.call(this, glyph.glyph, left, top); left += glyph.advance; });
            } finally {
                this.textAlign = align; this.textBaseline = baseline;
            }
        };
        return canvas;
    }

    function textHeight(size) {
        return LINE_HEIGHT * size;
    }

    /**
     * Java2D's antialiased stroke normalisation moved each coordinate to a quarter pixel, so a 0.5px
     * outline, axis line, tick mark or gridline filled the one pixel at floor(v + 0.25) at half
     * strength. Plot edges and tick positions are fractional, as JFreeChart's were: a 1px canvas line
     * at half opacity through that pixel's centre draws the same pixels.
     */
    function javaPixel(v) {
        return Math.floor(v + 0.25) + 0.5;
    }

    /** Chart.js centres a gridline on the rounded tick pixel; put it where JFreeChart drew it. */
    function javaGrid(scale) {
        if (scale.carlosGrid) return;
        scale.carlosGrid = true;
        const compute = scale._computeGridLineItems;
        scale._computeGridLineItems = function (area) {
            const items = compute.call(this, area);
            // One item per tick; anything else is a layout this adjustment does not know.
            if (items.length !== this.ticks.length) return items;
            items.forEach((item, index) => {
                const pixel = javaPixel(this.getPixelForTick(index));
                if (this.isHorizontal()) { item.tx1 = item.tx2 = item.x1 = item.x2 = pixel; } else { item.ty1 = item.ty2 = item.y1 = item.y2 = pixel; }
            });
            return items;
        };
    }

    // Java's integer FontMetrics ascent and descent for DejaVu Sans at the tick label sizes (the
    // fixtures' "metrics"), and its LineMetrics descent per pixel of font size.
    const JAVA_FONT_METRICS = { 8: [8, 2], 10: [10, 3], 12: [12, 3], 14: [13, 4] };
    const JAVA_DESCENT = 0.2358;

    /**
     * How far below the middle of Chart.js's label line Java put the baseline of a label hung under an
     * axis (TextAnchor.TOP_CENTER): the FontMetrics height less the LineMetrics descent below the
     * label's top.
     */
    function javaTopDrop(size) {
        const [ascent, descent] = JAVA_FONT_METRICS[size] || [Math.ceil(size * (LINE_HEIGHT - JAVA_DESCENT)), Math.ceil(size * JAVA_DESCENT)];
        scratch.font = font(size);
        scratch.textBaseline = 'middle';
        const chromium = -scratch.measureText('H').alphabeticBaseline;
        scratch.textBaseline = 'alphabetic';
        return ascent + descent - size * JAVA_DESCENT - textHeight(size) / 2 - chromium;
    }

    /** Draw a horizontal axis' labels {@code drop} pixels lower than Chart.js laid them out. */
    function droppedLabels(scale, drop) {
        if (scale.carlosDrop !== undefined) return;
        scale.carlosDrop = drop;
        const drawLabels = scale.drawLabels;
        scale.drawLabels = function (area) {
            this.ctx.carlosDrop = drop;
            try { drawLabels.call(this, area); } finally { this.ctx.carlosDrop = 0; }
        };
    }

    /**
     * How far below Chromium's "middle" baseline Java put a centred label's baseline. TextUtils centred
     * a vertical axis' tick label (TextAnchor.CENTER_RIGHT/LEFT) with its baseline half the
     * FontMetrics height, less the LineMetrics descent, below the tick: about 1.5px lower than a
     * canvas label centred on the same tick.
     */
    function javaCentreShift(size) {
        const [ascent, descent] = JAVA_FONT_METRICS[size] || [Math.ceil(size * (LINE_HEIGHT - JAVA_DESCENT)), Math.ceil(size * JAVA_DESCENT)];
        scratch.font = font(size);
        scratch.textBaseline = 'middle';
        const chromium = -scratch.measureText('H').alphabeticBaseline;
        scratch.textBaseline = 'alphabetic';
        return (ascent + descent) / 2 - size * JAVA_DESCENT - chromium;
    }

    function shorten(label) {
        return label.length > LABEL_LIMIT ? label.slice(0, LABEL_LIMIT) + '...' : label;
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
        // Zone offsets are whole seconds, so the zone-local millisecond is the instant's.
        const millisecond = ((instant % SECOND) + SECOND) % SECOND;
        return { year: parts.year, month: parts.month - 1, day: parts.day, hour: parts.hour % 24, minute: parts.minute, second: parts.second, millisecond };
    }

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    let formattingLocale = "en-US", localizedMonths = MONTHS, amPm = ["AM", "PM"];

    function formatDate(instant, format, timeZone) {
        const p = zonedParts(instant, timeZone);
        switch (format) {
            case 'HH:mm:ss.SSS': return `${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}.${String(p.millisecond).padStart(3, '0')}`;
            case 'HH:mm:ss': return `${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}`;
            case 'HH:mm': return `${pad(p.hour)}:${pad(p.minute)}`;
            case 'd-MMM, HH:mm': return `${p.day}-${localizedMonths[p.month]}, ${pad(p.hour)}:${pad(p.minute)}`;
            case 'd-MMM': return `${p.day}-${localizedMonths[p.month]}`;
            case 'MMM-yyyy': return `${localizedMonths[p.month]}-${p.year}`;
            case 'yyyy': return String(p.year);
            case 'yyyy.MM.dd': return `${p.year}.${pad(p.month + 1)}.${pad(p.day)}`;
            default: return `${p.month + 1}/${p.day}/${pad(p.year % 100)}, ${p.hour % 12 || 12}:${pad(p.minute)} ${p.hour < 12 ? amPm[0] : amPm[1]}`;
        }
    }

    /** DateAxis.previousStandardDate for tick marks at the start of the period. */
    function previousStandardDate(instant, unit, timeZone) {
        const p = zonedParts(instant, timeZone);
        const count = unit.count;
        const build = (field, value) => {
            switch (field) {
                case 'millisecond': return zonedInstant(timeZone, p.year, p.month, p.day, p.hour, p.minute, p.second) + value;
                case 'second': return zonedInstant(timeZone, p.year, p.month, p.day, p.hour, p.minute, value);
                case 'minute': return zonedInstant(timeZone, p.year, p.month, p.day, p.hour, value, 0);
                case 'hour': return zonedInstant(timeZone, p.year, p.month, p.day, value, 0, 0);
                case 'day': return zonedInstant(timeZone, p.year, p.month, value, 0, 0, 0);
                // Months align on multiples of the count counted from January as month one.
                case 'month': return zonedInstant(timeZone, p.year, value - 1, 1, 0, 0, 0);
                default: return zonedInstant(timeZone, value, 0, 1, 0, 0, 0);
            }
        };
        const current = { millisecond: p.millisecond, second: p.second, minute: p.minute, hour: p.hour, day: p.day, month: p.month + 1, year: p.year }[unit.field];
        const value = count * Math.floor(current / count);
        let date = build(unit.field, value);
        if (date >= instant) date = build(unit.field, value - (unit.field === 'month' || unit.field === 'millisecond' ? count : 1));
        return date;
    }

    function addToDate(instant, unit, timeZone) {
        const p = zonedParts(instant, timeZone);
        switch (unit.field) {
            case 'millisecond': case 'second': case 'minute': case 'hour': return instant + unit.size;
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
        // The margins apply to the data's own span: a constant series is widened to the 1e-8 minimum
        // and gets no margin. Where that widening is lost to rounding (a constant 100,000,000),
        // JFreeChart widened by a tenth of the value instead (its bug 1549218 fix).
        const range = upper - lower;
        if (range === 0) {
            const padding = lower === 0 ? 1 : (Math.abs(lower) * 0.1 || Number.MIN_VALUE);
            lower -= padding; upper += padding;
        } else if (range < 1e-8) {
            const expand = (1e-8 - range) / 2;
            upper += expand; lower -= expand;
            if (lower === upper) { const adjust = Math.abs(lower) / 10; lower -= adjust; upper += adjust; }
        }
        upper = upper <= 0 ? Math.min(0, upper + margins[1] * range) : upper + margins[1] * range;
        lower = lower >= 0 ? Math.max(0, lower - margins[0] * range) : lower - margins[0] * range;
        return [lower, upper];
    }

    /**
     * DateAxis.autoAdjustRange in whole milliseconds: a range under the two millisecond minimum is
     * widened to it (a single reading shows a two millisecond axis, as it did), then the margins of the
     * original span are added; no data at all is DateRange's default of 0 to 1 ms.
     */
    function dateRange(values, margins) {
        if (!values.length) return [0, 1];
        let lower = Math.min(...values), upper = Math.max(...values);
        const span = upper - lower;
        if (span < 2) { upper += DAY; lower -= DAY; }
        return [lower - Math.trunc(span * margins[0]), upper + Math.trunc(span * margins[1])];
    }

    function numberPower(unit) {
        return Math.floor(Math.log10(unit) + 1e-9);
    }

    /** DecimalFormat("#.######E0"): one integer digit, up to six fraction digits, no exponent sign for positives. */
    function scientific(value) {
        if (value === 0) return '0E0';
        let exponent = Math.floor(Math.log10(Math.abs(value)));
        let mantissa = Number((value / Math.pow(10, exponent)).toFixed(6));
        if (Math.abs(mantissa) >= 10) { mantissa = Number((mantissa / 10).toFixed(6)); exponent += 1; }
        return `${mantissa.toLocaleString(formattingLocale, { useGrouping: false, maximumFractionDigits: 6 })}E${exponent}`;
    }

    /**
     * NumberTickUnitSource's label formats by the unit's power of ten: "0.0000" to "0.0" for 1e-4 to
     * 1e-1, "#,##0" (with grouping) from 1 to 5,000,000, and "#.######E0" outside those.
     */
    function formatTick(value, unit) {
        const power = numberPower(unit);
        if (power >= -4 && power <= -1) return Number(value).toLocaleString(formattingLocale, { useGrouping: false, minimumFractionDigits: -power, maximumFractionDigits: -power });
        if (power >= 0 && power <= 6) return Math.round(value).toLocaleString(formattingLocale, { maximumFractionDigits: 0 });
        return scientific(value);
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
            : unit => 8 + Math.max(textWidth(formatTick(lower, unit.size), fontSize), textWidth(formatTick(upper, unit.size), fontSize));
        const unit = selectUnit(units, { size: 1 }, pixelsPerSize, labelSize).size;
        const ticks = [];
        // The same double arithmetic as NumberAxis.calculateLowestVisibleTickValue and calculateVisibleTickCount.
        const first = Math.ceil(lower / unit), last = Math.floor(upper / unit);
        const count = last - first + 1;
        if (!Number.isSafeInteger(first) || !Number.isSafeInteger(last) || count > 1000) {
            throw new Error('Chart numeric axis has too many ticks');
        }
        for (let offset = 0; offset < count; offset++) {
            const value = Number(((first + offset) * unit).toPrecision(15));
            ticks.push({ value, label: formatTick(value, unit) });
        }
        return { unit, ticks };
    }

    function dateTicks(lower, upper, pixels, fontSize, timeZone) {
        const pixelsPerMs = pixels / (upper - lower);
        const labelSize = unit => 8 + Math.max(textWidth(formatDate(lower, unit.format, timeZone), fontSize), textWidth(formatDate(upper, unit.format, timeZone), fontSize));
        const selected = selectUnit(DATE_UNITS, INITIAL_DATE_UNIT, pixelsPerMs, labelSize);
        // Localized month lengths vary. Checking the endpoints alone can select a
        // unit whose interior labels overlap (for example French août and déc.).
        for (let index = DATE_UNITS.indexOf(selected); index < DATE_UNITS.length; index++) {
            const unit = DATE_UNITS[index], ticks = [];
            let tick = addToDate(previousStandardDate(lower, unit, timeZone), unit, timeZone);
            for (let guard = 0; tick < upper && guard < 1000; guard++) {
                ticks.push({ value: tick, label: formatDate(tick, unit.format, timeZone) });
                tick = addToDate(tick, unit, timeZone);
            }
            const overlap = ticks.some((current, position) => position > 0 &&
                (current.value - ticks[position - 1].value) * pixelsPerMs <
                (textWidth(current.label, fontSize) + textWidth(ticks[position - 1].label, fontSize)) / 2 + 8);
            if (!overlap || index === DATE_UNITS.length - 1) return { unit, ticks };
        }
        throw new Error('Chart date tick unit is unavailable');
    }

    // --- the drawing -----------------------------------------------------------------------------

    // DefaultDrawingSupplier's series shapes, handed out by series index: square, circle, up-triangle,
    // diamond, horizontal rectangle, down-triangle, horizontal ellipse, right-triangle, vertical
    // rectangle and left-triangle, 6px across.
    const SHAPES = ['square', 'circle', 'upTriangle', 'diamond', 'hRect', 'downTriangle', 'hEllipse', 'rightTriangle', 'vRect', 'leftTriangle'];

    function shapeOf(index) {
        return SHAPES[index % SHAPES.length];
    }

    function legendGraphic(dataset, shape) {
        // The high-low renderer's legend item is a square, for its ranges and for a series it drew.
        if (dataset.kind === 'range' || dataset.highLow) return 'shape8x8';
        if (dataset.lines !== false && dataset.shapes) return shape === 'circle' ? 'lineAndCircle' : shape === 'square' ? 'lineAndShape' : `lineAnd:${shape}`;
        if (dataset.lines !== false) return 'line';
        return 'shape6x6';
    }

    function drawMarker(ctx, x, y, shape) {
        const polygon = points => { ctx.beginPath(); points.forEach(([dx, dy], i) => (i ? ctx.lineTo(x + dx, y + dy) : ctx.moveTo(x + dx, y + dy))); ctx.closePath(); ctx.fill(); };
        switch (shape === true ? 'circle' : shape || 'square') {
            case 'circle': ctx.beginPath(); ctx.arc(x, y, 3, 0, 2 * Math.PI); ctx.fill(); break;
            case 'upTriangle': polygon([[0, -3], [3, 3], [-3, 3]]); break;
            case 'diamond': polygon([[0, -3], [3, 0], [0, 3], [-3, 0]]); break;
            case 'hRect': ctx.fillRect(x - 3, y - 1.5, 6, 3); break;
            case 'downTriangle': polygon([[-3, -3], [3, -3], [0, 3]]); break;
            case 'hEllipse': ctx.beginPath(); ctx.ellipse(x, y, 3, 1.5, 0, 0, 2 * Math.PI); ctx.fill(); break;
            case 'rightTriangle': polygon([[-3, -3], [3, 0], [-3, 3]]); break;
            case 'vRect': ctx.fillRect(x - 1.5, y - 3, 3, 6); break;
            case 'leftTriangle': polygon([[-3, 0], [3, -3], [3, 3]]); break;
            default: ctx.fillRect(x - 3, y - 3, 6, 6);
        }
    }

    /**
     * A plotted item's marker. XYLineAndShapeRenderer filled the 6x6 shape and then drew its outline in
     * the series paint with the series' 1px stroke, so a marker covers 7px. The recorded markers have
     * crisp edges: their coordinates sit on pixel centres.
     */
    function drawPlotMarker(ctx, x, y, shape) {
        const kind = shape === true ? 'circle' : shape || 'square';
        ctx.save();
        // A rectangle and its outline cover whole pixels: one exact block.
        const block = (halfWidth, halfHeight) => {
            const left = Math.floor(x - halfWidth), top = Math.floor(y - halfHeight);
            ctx.fillRect(left, top, Math.floor(x + halfWidth) - left + 1, Math.floor(y + halfHeight) - top + 1);
        };
        const q = v => Math.floor(v) + 0.5;
        const path = new Path2D();
        const polygon = points => {
            points.forEach(([dx, dy], i) => (i ? path.lineTo(q(x + dx), q(y + dy)) : path.moveTo(q(x + dx), q(y + dy))));
            path.closePath();
        };
        const ellipse = (rx, ry) => {
            const left = q(x - rx), right = q(x + rx), top = q(y - ry), bottom = q(y + ry);
            path.ellipse((left + right) / 2, (top + bottom) / 2, (right - left) / 2, (bottom - top) / 2, 0, 0, 2 * Math.PI);
        };
        switch (kind) {
            case 'square': block(3, 3); break;
            case 'hRect': block(3, 1.5); break;
            case 'vRect': block(1.5, 3); break;
            case 'circle': ellipse(3, 3); break;
            case 'upTriangle': polygon([[0, -3], [3, 3], [-3, 3]]); break;
            case 'diamond': polygon([[0, -3], [3, 0], [0, 3], [-3, 0]]); break;
            case 'downTriangle': polygon([[-3, -3], [3, -3], [0, 3]]); break;
            case 'hEllipse': ellipse(3, 1.5); break;
            case 'rightTriangle': polygon([[-3, -3], [3, 0], [-3, 3]]); break;
            case 'leftTriangle': polygon([[-3, 0], [3, -3], [3, 3]]); break;
            default: block(3, 3);
        }
        if (!['square', 'hRect', 'vRect'].includes(kind)) {
            // BasicStroke's defaults: mitred joins and square caps, whatever Chart.js last left set.
            ctx.strokeStyle = ctx.fillStyle; ctx.lineWidth = 1; ctx.lineJoin = 'miter'; ctx.lineCap = 'square'; ctx.setLineDash([]);
            ctx.fill(path); ctx.stroke(path);
        }
        ctx.restore();
    }

    // LegendTitle's item geometry: the graphic and the label each padded 2px on every side, items 2px
    // apart, and wrapped rows starting 2px in from the chart's edge.
    const GRAPHIC_PAD = 2;
    const LABEL_PAD = 2;
    const ITEM_GAP = 2;
    const LEGEND_LEFT = 2;

    /**
     * LegendGraphic's width is the union of its line and shape bounds, visible or not: a line renderer's
     * graphic is its 14px line even when only the point is drawn; bars and high-low items have no line.
     */
    function graphicWidth(graphic) {
        if (graphic === 'shape6x10') return 6;
        if (graphic === 'shape8x8') return 8;
        return 14;
    }

    /**
     * {@code x} is the graphic's left edge and {@code y} the pixel centre of its row. JFreeChart drew a
     * legend line with square caps (half a pixel past each end) and outlined a legend marker in its own
     * paint, so the marker covers 7px, like the plot's.
     */
    function drawLegendGraphic(ctx, item, x, y) {
        ctx.fillStyle = item.colour; ctx.strokeStyle = item.colour; ctx.lineWidth = 1;
        const other = item.graphic.startsWith('lineAnd:') ? item.graphic.slice('lineAnd:'.length) : null;
        const outlined = (cx, shape) => {
            drawMarker(ctx, cx, y, shape);
            ctx.save(); ctx.fillStyle = 'rgba(0, 0, 0, 0)'; ctx.globalCompositeOperation = 'source-over';
            if (shape === 'circle') { ctx.beginPath(); ctx.arc(cx, y, 3, 0, 2 * Math.PI); ctx.stroke(); } else { ctx.strokeRect(cx - 3, y - 3, 6, 6); }
            ctx.restore();
        };
        if (['line', 'lineAndShape', 'lineAndCircle'].includes(item.graphic) || other) {
            ctx.beginPath(); ctx.moveTo(x - 0.5, y); ctx.lineTo(x + 14.5, y); ctx.stroke();
        }
        if (['lineAndShape', 'lineAndCircle'].includes(item.graphic) || other) {
            outlined(x + 7, other || (item.graphic === 'lineAndCircle' ? 'circle' : 'square'));
        }
        if (item.graphic === 'shape6x6') outlined(x + 7, 'square');
        if (item.graphic === 'shape8x8') ctx.fillRect(x, y - 4, 8, 8);
        if (item.graphic === 'shape6x10') ctx.fillRect(x, y - 5, 6, 10);
    }

    /**
     * JFreeChart's legend: FlowArrangement lays items out left to right and wraps them. A legend that
     * fits on one row is centred under the plot; once it wraps, the arrangement takes the full width
     * and every row starts at its left edge. A plot without legend items reserves no legend space.
     */
    function legendLayout(items, width, look) {
        // An item is its label's line height plus 2px label padding above and below, and the rows sat
        // a further 3px apart.
        const rowHeight = Math.round(textHeight(look.legend.size) + 7);
        // An empty legend block still kept its 1px margins.
        if (!items.length) return { rows: [], rowHeight, height: 2, wrapped: false };
        // FlowArrangement.arrangeFN: an item joins the row while it fits the legend's width.
        const available = width - 4;
        const rows = [[]];
        let x = 0;
        items.forEach(item => {
            item.width = graphicWidth(item.graphic) + 2 * GRAPHIC_PAD + textWidth(item.label, look.legend.size) + 2 * LABEL_PAD;
            if (x + item.width > available && rows[rows.length - 1].length) { rows.push([]); x = 0; }
            rows[rows.length - 1].push(item);
            x += item.width + ITEM_GAP;
        });
        return { rows, rowHeight, height: rows.length * rowHeight + 4, wrapped: rows.length > 1 };
    }

    function drawLegend(chart, layout, look) {
        const ctx = chart.ctx;
        const canvasWidth = chart.width, canvasHeight = chart.height;
        ctx.save();
        ctx.font = font(look.legend.size);
        ctx.textBaseline = 'middle';
        ctx.textAlign = 'left';
        const base = canvasHeight - layout.height;
        // Labels sit 3px into the legend's row: its 2px padding plus Java's lower baseline.
        let top = base + 3;
        layout.rows.forEach((row, index) => {
            // FlowArrangement.arrangeNN: gaps between items only, centred without rounding.
            const rowWidth = row.reduce((sum, item) => sum + item.width, 0) + ITEM_GAP * (row.length - 1);
            let x = layout.wrapped ? LEGEND_LEFT : (canvasWidth - rowWidth) / 2;
            const y = top + layout.rowHeight / 2;
            // Graphics sit on a whole pixel row, so a 1px line is one crisp row as JFreeChart drew it.
            const graphicY = base + 13.5 + index * layout.rowHeight;
            row.forEach(item => {
                drawLegendGraphic(ctx, item, x + GRAPHIC_PAD, graphicY);
                ctx.fillStyle = look.legend.color;
                ctx.fillText(item.label, x + 2 * GRAPHIC_PAD + graphicWidth(item.graphic) + LABEL_PAD, y);
                x += item.width + ITEM_GAP;
            });
            top += layout.rowHeight;
        });
        ctx.restore();
    }

    /** An axis label 3px (its insets) clear of the tick labels; a date axis label hangs below its ticks. */
    function axisTitle(text, look, colour, vertical, inner = 0) {
        return { display: !!text, text: text || '', color: colour || look.axisLabel.color,
            font: { family: FONT, size: look.axisLabel.size, weight: look.axisLabel.weight, lineHeight: LINE_HEIGHT },
            padding: vertical ? { top: 5, bottom: inner } : { top: 2, bottom: 3 } };
    }

    /**
     * An axis label wider than its axis ran past the plot, centred, as JFreeChart drew it (the scatter
     * servlet's long "Tests (note: ...)" label is clipped at the image edge); Chart.js would squeeze the
     * text horizontally to the axis length instead.
     */
    function uncompressedTitle(scale) {
        if (scale.carlosTitle) return;
        scale.carlosTitle = true;
        const drawTitle = scale.drawTitle;
        scale.drawTitle = function () {
            const ctx = this.ctx;
            const fillText = ctx.fillText;
            ctx.fillText = (text, x, y) => fillText.call(ctx, text, x, y);
            try { drawTitle.call(this); } finally { ctx.fillText = fillText; }
        };
    }

    /**
     * Size the label space as Java measured it and let edge labels overhang, as JFreeChart did: Chart.js
     * measures labels with fractional advances (a long medication name comes out a few pixels wider than
     * Java's integer advances) and shrinks the plot so the first and last labels fit inside the canvas.
     */
    function fitLikeJava(scale, size) {
        uncompressedTitle(scale);
        javaGrid(scale);
        if (scale.isHorizontal()) { scale.paddingLeft = 0; scale.paddingRight = 0; return; }
        scale.paddingTop = 0; scale.paddingBottom = 0;
        if (scale.options.ticks.display === false) return;
        // A symbol axis without symbols still reserved its label insets around an empty label.
        if (!scale.ticks.length) { scale.width += 2 * scale.options.ticks.padding; return; }
        scratch.font = font(size);
        const labels = scale.ticks.map(tick => String(tick.label ?? ''));
        const measured = Math.max(0, ...labels.map(label => scratch.measureText(label).width));
        const java = Math.max(0, ...labels.map(label => textWidth(label, size)));
        scale.width = Math.max(0, scale.width + java - measured);
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
        formattingLocale = spec.locale || 'en-US';
        localizedMonths = spec.months || MONTHS;
        amPm = spec.amPm || ['AM', 'PM'];
        document.body.replaceChildren();
        document.body.style.cssText = 'margin:0;background:white';
        const style = { theme: 'jfree', plotBackground: '#c0c0c0', gridlines: '#ffffff', domainMargins: [0.02, 0.02], rangeMargins: [0.05, 0.05],
            rangeIncludesZero: false, domainIncludesZero: false, rangeAxisPaint: null, itemLabels: null, ...(spec.style || {}) };
        const timeZone = spec.timeZone;
        const dateAxis = spec.dateAxis !== false;
        const datasets = spec.datasets || [];
        const medications = spec.medications || [];
        // The former chart was a combined plot whenever it had a medication subplot, including when no
        // prescription matched: an empty "Meds" panel rather than a measurement-style chart.
        const combined = spec.medicationPanel === true || medications.length > 0;
        const panelLook = LOOKS[style.theme] || LOOKS.jfree;
        const chartLook = combined ? LOOKS.plain : panelLook;

        // Paints: the sequence runs through the datasets in drawing order, then the medication series.
        let next = 0;
        const paintOf = paint => paint || PALETTE[next++ % PALETTE.length];
        const colours = datasets.map(dataset => paintOf(dataset.paint));
        // One row, colour and legend item per medication series (the former TaskSeries per DIN, named by
        // its first prescription), so two DINs sharing a brand name keep separate rows as they did.
        const rowOf = m => (m.row === undefined || m.row === null ? m.name : String(m.row));
        const rows = [...new Set(medications.map(rowOf))];
        const names = rows.map(row => medications.find(m => rowOf(m) === row).name);
        const drugColours = rows.map(() => paintOf(null));

        // The domain range covers every dataset and, in a combined plot, the prescriptions.
        const xs = datasets.flatMap(dataset => dataset.data.map(point => point.x));
        if (combined) medications.forEach(m => xs.push(m.start, m.end));
        const domainMargins = combined ? [0.05, 0.05] : style.domainMargins;
        const [xLower, xUpper] = dateAxis ? dateRange(xs, domainMargins) : xs.length ? numberRange(xs, domainMargins, style.domainIncludesZero) : [0, 1];

        const rangeValues = axis => datasets.flatMap(dataset => {
            if (dataset.yAxisID !== axis && !(axis === 'y' && !dataset.yAxisID)) return [];
            // Ranges mapped to the value axis widened it; those on a second index of the same axis did not.
            if (dataset.kind === 'range') return dataset.widensAxis ? dataset.data.flatMap(point => [point.low, point.high]) : [];
            return dataset.data.map(point => point.y);
        });
        const [yLower, yUpper] = numberRange(rangeValues('y'), style.rangeMargins, style.rangeIncludesZero);
        const [y2Lower, y2Upper] = numberRange(rangeValues('y2'), [0.05, 0.05], false);

        // The plot's drawing supplier hands shapes out by series in legend order, like the paints: the
        // first series a square, the second (blood pressure's systolic or diastolic) a circle.
        const shapeIndex = index => shapeOf(index);
        const legendItems = [];
        datasets.forEach((dataset, index) => legendItems.push({ label: dataset.label, graphic: legendGraphic(dataset, shapeIndex(index)), colour: colours[index],
            fontSize: chartLook.legend.size, textColour: chartLook.legend.color }));
        names.forEach((name, index) => legendItems.push({ label: name, graphic: 'shape6x10', colour: drugColours[index],
            fontSize: chartLook.legend.size, textColour: chartLook.legend.color }));
        const legend = legendLayout(legendItems, width, chartLook);

        const charts = [];
        // XYPlot.axisOffset: the "JFree" theme kept its axes 4px clear of the plot on every side, with the
        // axis line and 2px tick marks drawn in that gap; the combined charts' plain medication subplot had
        // none. Tick labels sat their insets (4px across, 2px along) plus 2px beyond the axis line.
        const AXIS_LINE = 'rgba(128, 128, 128, 0.5)';
        const AXIS_OFFSET = 4;
        // Chart.js reserves ticks.padding on both sides of the labels, but JFreeChart's offset and tick
        // mark lie only between the labels and the plot: reserve them as the tick mark length. In an
        // offset plot that length is transparent and drawOffsetAxes draws the real line and marks.
        // The label space below a date axis, as JFreeChart reserved it; droppedLabels then draws the
        // labels on Java's baseline within it.
        const TEXT_DROP = 1.5;
        const tickGap = (shown, offset, horizontal) => (shown
            ? { drawTicks: true, tickLength: offset + 2 + (horizontal ? TEXT_DROP : 0), tickColor: offset || horizontal ? 'rgba(0, 0, 0, 0)' : AXIS_LINE }
            : { drawTicks: false });
        const xScale = (look, shared, gridColour = style.gridlines, offset = 0) => ({
            type: 'linear', min: xLower, max: xUpper, offset: false,
            afterBuildTicks(scale) {
                const length = Math.max(1, scale.maxWidth);
                const chosen = dateAxis ? dateTicks(xLower, xUpper, length, look.tick.size, timeZone)
                    : numberTicks(xLower, xUpper, length, false, look.tick.size);
                scale.carlosTicks = chosen.ticks;
                scale.ticks = chosen.ticks.map(tick => ({ value: tick.value }));
            },
            ticks: tickOptions(look, null, shared ? { padding: 2 } : { display: false }),
            grid: { ...gridOptions(gridColour), ...tickGap(shared, offset, true) },
            // drawOffsetAxes draws the axis line; Chart.js's border keeps only the gridline dash.
            border: { display: false, color: AXIS_LINE, width: 1, dash: [2, 2] },
            title: shared ? axisTitle(combined ? 'Date/Time' : spec.xLabel, look) : { display: false },
            afterFit(scale) { fitLikeJava(scale, look.tick.size); droppedLabels(scale, javaTopDrop(look.tick.size) - TEXT_DROP); },
        });
        const yScale = (id, lower, upper, look, label, colour, position, offset = 0) => ({
            type: 'linear', min: lower, max: upper, position, offset: false,
            afterBuildTicks(scale) {
                const chosen = numberTicks(lower, upper, Math.max(1, scale.maxHeight), true, look.tick.size);
                scale.carlosTicks = chosen.ticks;
                scale.ticks = chosen.ticks.map(tick => ({ value: tick.value }));
            },
            ticks: tickOptions(look, colour, { padding: 4, labelOffset: javaCentreShift(look.tick.size) }),
            grid: { ...gridOptions(style.gridlines), ...(id === 'y' ? {} : { drawOnChartArea: false }), ...tickGap(true, offset) },
            border: { display: false, color: AXIS_LINE, width: 1, dash: [2, 2] },
            title: axisTitle(label, look, colour, true),
            afterFit(scale) { fitLikeJava(scale, look.tick.size); },
        });

        /**
         * The axis lines, and the outward tick marks of an offset plot, drawn in the gap around the plot.
         * Without an offset the axis line lies on the plot outline, darkening it as JFreeChart's did;
         * Chart.js still draws that plot's vertical tick marks, and its date axis tick marks are drawn
         * here because that axis reserves extra transparent tick length for its labels.
         */
        const drawOffsetAxes = (chart, offset) => {
            const ctx = chart.ctx;
            const area = chart.chartArea;
            ctx.save();
            ctx.strokeStyle = AXIS_LINE; ctx.lineWidth = 1;
            Object.values(chart.scales).forEach(scale => {
                if (scale.options.display === false || scale.options.ticks.display === false) return;
                const ticks = (scale.carlosTicks || []).filter(tick => tick.label !== '');
                ctx.beginPath();
                if (scale.isHorizontal()) {
                    const y = javaPixel(area.bottom + offset);
                    ctx.moveTo(javaPixel(area.left), y); ctx.lineTo(javaPixel(area.right), y);
                    ticks.forEach(tick => { const x = javaPixel(scale.getPixelForValue(tick.value)); ctx.moveTo(x, y); ctx.lineTo(x, y + 2); });
                } else {
                    const left = scale.position === 'left';
                    const x = javaPixel(left ? area.left - offset : area.right + offset);
                    ctx.moveTo(x, javaPixel(area.top)); ctx.lineTo(x, javaPixel(area.bottom));
                    // Without an offset, Chart.js draws a vertical axis' tick marks.
                    if (offset) ticks.forEach(tick => { const y = javaPixel(scale.getPixelForValue(tick.value)); ctx.moveTo(x, y); ctx.lineTo(left ? x - 2 : x + 2, y); });
                }
                ctx.stroke();
            });
            ctx.restore();
        };

        /** Plot background before, outline after, and the JFreeChart renderer details Chart.js lacks. */
        const jfreePlugin = (look, panel) => ({
            id: 'jfreeLook',
            afterLayout(chart) {
                // JFreeChart centred its title on the image; Chart.js centres it inside the layout padding,
                // which the combined chart widens to align its panels.
                if (chart.titleBlock) { chart.titleBlock.left = 0; chart.titleBlock.right = chart.width; chart.titleBlock.width = chart.width; }
            },
            beforeDraw(chart) {
                const area = chart.chartArea;
                chart.ctx.save();
                chart.ctx.fillStyle = panel.background;
                chart.ctx.fillRect(area.left, area.top, area.right - area.left, area.bottom - area.top);
                chart.ctx.restore();
            },
            // XYBarRenderer painted each prescription's shadow and then its bar, one prescription at a time,
            // so a later prescription of the same medication left its shadow's edge across an earlier,
            // longer bar on that row. The bars are drawn here in that order instead of by Chart.js.
            beforeDatasetDraw(chart, args) {
                if (panel.bars && args.index === 0) return false;
                return undefined;
            },
            beforeDatasetsDraw(chart) {
                if (!panel.bars) return;
                const ctx = chart.ctx;
                const area = chart.chartArea;
                const fills = [].concat(chart.data.datasets[0].backgroundColor);
                chart.carlosShadows = [];
                chart.getDatasetMeta(0).data.forEach((bar, index) => {
                    const left = Math.round(Math.min(bar.x, bar.base)), right = Math.max(left + 1, Math.round(Math.max(bar.x, bar.base)));
                    const top = Math.round(bar.y - bar.height / 2);
                    ctx.save();
                    ctx.fillStyle = '#808080';
                    ctx.fillRect(left + 4, top + 4, right - left, Math.round(bar.height));
                    chart.carlosShadows.push({ dx: 4, dy: 4, colour: '#808080' });
                    ctx.beginPath(); ctx.rect(area.left, area.top, area.right - area.left, area.bottom - area.top); ctx.clip();
                    bar.draw(ctx, area);
                    // A zero-length interval is still drawn: a one pixel line in the series colour.
                    if (Math.abs(bar.x - bar.base) <= 1.5) {
                        ctx.fillStyle = fills[index];
                        ctx.fillRect(left, top, 1, Math.round(bar.height));
                    }
                    ctx.restore();
                });
            },
            afterDatasetsDraw(chart) {
                const ctx = chart.ctx;
                const area = chart.chartArea;
                ctx.save();
                ctx.beginPath(); ctx.rect(area.left, area.top, area.right - area.left, area.bottom - area.top); ctx.clip();
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
                        if (source.shapes || source.data.length === 1) {
                            ctx.fillStyle = colour;
                            elements.forEach(element => drawPlotMarker(ctx, element.x, element.y, shapeIndex(index)));
                        }
                        const labels = style.itemLabels;
                        if (!labels || source.kind !== 'line') return;
                        // Only the first series had a label generator (setSeriesItemLabelGenerator(0, ...));
                        // the others, such as blood pressure's systolic, drew no labels.
                        const primary = datasets.findIndex(d => d.kind === 'line') === index;
                        if (!primary) return;
                        const size = labels.fontSize || look.itemLabel;
                        ctx.font = font(size); ctx.fillStyle = '#000000'; ctx.textAlign = 'center'; ctx.textBaseline = 'bottom';
                        source.data.forEach((point, i) => {
                            const element = elements[i];
                            if (!element) return;
                            const text = itemLabel(point, labels.format, labels.decimals, timeZone);
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
                const left = javaPixel(area.left), top = javaPixel(area.top);
                ctx.strokeRect(left, top, javaPixel(area.right) - left, javaPixel(area.bottom) - top);
                ctx.restore();
                drawOffsetAxes(chart, panel.offset || 0);
                if (panel.legend) { drawLegend(chart, legend, chartLook); chart.carlosLegend = legendItems; }
            },
        });

        // The plot's axis offset also separates the title from an offset plot.
        const titleOptions = (show, offset = 0) => ({ display: show, text: spec.title || '', color: '#000000', align: 'center',
            font: { family: FONT, size: chartLook.title, weight: 'bold', lineHeight: LINE_HEIGHT }, padding: { top: 2, bottom: 4 + offset } });
        const basePadding = { left: 8, right: 8, top: 1, bottom: 1 };

        let mainChart = null;
        let medicationChart = null;
        const createPanels = topHeight => {
            charts.length = 0;
            document.querySelectorAll('canvas').forEach(canvas => canvas.remove());
            mainChart = null; medicationChart = null;
            // No matching prescriptions is a valid medication request; retain its title and axes.
            if (datasets.length || !combined) {
                const main = javaText(document.createElement('canvas'));
                main.style.display = 'block';
                main.width = width;
                main.height = combined ? topHeight : height;
                document.body.append(main);
                const scales = { x: xScale(combined ? LOOKS.plain : panelLook, !combined, style.gridlines, AXIS_OFFSET),
                    y: yScale('y', yLower, yUpper, panelLook, spec.yLabel, style.rangeAxisPaint, 'left', AXIS_OFFSET) };
                if (spec.secondAxis) scales.y2 = yScale('y2', y2Lower, y2Upper, LOOKS.plain, spec.secondAxis, null, 'right', AXIS_OFFSET);
                mainChart = new Chart(main, {
                    type: 'scatter',
                    data: { datasets: datasets.map((dataset, index) => ({
                        label: dataset.label, carlosKind: dataset.kind, yAxisID: dataset.yAxisID || 'y',
                        data: dataset.kind === 'range' ? [] : dataset.data.map(point => ({ x: point.x, y: point.y })),
                        showLine: dataset.kind !== 'range' && dataset.lines !== false, borderColor: colours[index], backgroundColor: colours[index],
                        borderWidth: 1, pointRadius: 0, pointHitRadius: 0, tension: 0, carlosShapes: !!dataset.shapes || dataset.data.length === 1 })) },
                    plugins: [jfreePlugin(panelLook, { background: style.plotBackground, series: true, legend: !combined, offset: AXIS_OFFSET })],
                    options: { responsive: false, animation: false, devicePixelRatio: 1, scales,
                        layout: { padding: { ...basePadding, right: basePadding.right + (spec.secondAxis ? 0 : AXIS_OFFSET),
                            bottom: (combined ? AXIS_OFFSET : legend.height) } },
                        plugins: { title: titleOptions(true, AXIS_OFFSET), legend: { display: false }, tooltip: { enabled: false } } } });
                charts.push(mainChart);
            }
            if (combined) {
                const meds = javaText(document.createElement('canvas'));
                meds.style.display = 'block';
                meds.width = width;
                meds.height = mainChart ? height - topHeight : height;
                document.body.append(meds);
                const colourOf = m => drugColours[rows.indexOf(rowOf(m))];
                medicationChart = new Chart(meds, {
                    type: 'bar',
                    // Category identity uses the full name. Shortening it here would put repeated
                    // prescriptions on the wrong row when two medication names share a prefix.
                    data: { labels: rows, datasets: [{ label: 'Meds', carlosKind: 'bar',
                        data: medications.map(m => ({ y: rowOf(m), x: [m.start, m.end] })),
                        backgroundColor: medications.map(m => colourOf(m)), borderWidth: 0, categoryPercentage: 0.6, barPercentage: 1, minBarLength: 1 }] },
                    plugins: [jfreePlugin(LOOKS.plain, { background: '#ffffff', bars: true, legend: true })],
                    options: { indexAxis: 'y', responsive: false, animation: false, devicePixelRatio: 1,
                        scales: { x: xScale(LOOKS.plain, true, '#c0c0c0'),
                            // reverse: the first medication listed sits at the bottom, as on the former symbol axis.
                            y: { type: 'category', labels: rows, reverse: true, offset: true,
                                ticks: { ...tickOptions(LOOKS.plain, '#000000'), padding: 4, labelOffset: javaCentreShift(LOOKS.plain.tick.size), callback(value) { return shorten(names[rows.indexOf(this.getLabelForValue(value))] ?? ''); } },
                                grid: { ...gridOptions('#c0c0c0'), offset: false }, border: { display: false, color: 'rgba(128, 128, 128, 0.5)', width: 1, dash: [2, 2] },
                                // The row names sat their 4px insets clear of the tick mark and of the
                                // axis label ("Meds"), which keeps no extra gap of its own.
                                title: axisTitle('Meds', LOOKS.plain, '#000000', true),
                                afterFit(scale) { fitLikeJava(scale, LOOKS.plain.tick.size); } } },
                        layout: { padding: { ...basePadding, top: mainChart ? 5 : basePadding.top, bottom: basePadding.bottom + legend.height } },
                        plugins: { title: titleOptions(!mainChart), legend: { display: false }, tooltip: { enabled: false } } } });
                charts.push(medicationChart);
            }
        };
        let topHeight = Math.round(height / 2);
        createPanels(topHeight);
        if (mainChart && medicationChart) {
            // Equal subplot slots with a 5px gap: JFreeChart gave each subplot the same weight, and the
            // upper (measurement) plot then kept its 4px axis offset inside its slot on every side.
            for (let attempt = 0; attempt < 4; attempt++) {
                const heights = [mainChart, medicationChart].map(chart => chart.chartArea.bottom - chart.chartArea.top);
                heights[0] += 2 * AXIS_OFFSET;
                if (Math.abs(heights[1] - heights[0]) <= 1) break;
                topHeight += Math.round((heights[1] - heights[0]) / 2);
                [mainChart, medicationChart].forEach(chart => chart.destroy());
                createPanels(topHeight);
            }
            // Matching date bounds alone is insufficient: long medication names consume more left-axis
            // space. Align the plot rectangles before producing a print image.
            const panels = [mainChart, medicationChart];
            const inset = chart => (chart === mainChart ? AXIS_OFFSET : 0);
            for (let attempt = 0; attempt < 4; attempt++) {
                const left = Math.max(...panels.map(chart => chart.chartArea.left - inset(chart)));
                const right = Math.min(...panels.map(chart => chart.chartArea.right + inset(chart)));
                if (panels.every(chart => Math.abs(chart.chartArea.left - inset(chart) - left) <= 1
                        && Math.abs(chart.chartArea.right + inset(chart) - right) <= 1)) break;
                panels.forEach(chart => {
                    const padding = chart.options.layout.padding;
                    chart.options.layout.padding = { top: padding.top || 0, bottom: padding.bottom || 0,
                        left: (padding.left || 0) + left - (chart.chartArea.left - inset(chart)),
                        right: (padding.right || 0) + (chart.chartArea.right + inset(chart)) - right };
                    chart.update('none');
                });
            }
            if (Math.abs(mainChart.chartArea.left - AXIS_OFFSET - medicationChart.chartArea.left) > 1
                    || Math.abs(mainChart.chartArea.right + AXIS_OFFSET - medicationChart.chartArea.right) > 1) {
                throw new Error('Chart date axes could not be aligned');
            }
        }
        // Ticks were chosen while the layout was still settling; settle them against the final plot size.
        charts.forEach(chart => chart.update('none'));
        return charts;
    }

    function itemLabel(point, format, decimals, timeZone) {
        // DecimalFormat("0.00") / ("0.###"): no grouping separator, trailing zeros dropped for "#".
        const value = point.labelValue ?? (decimals === '0.00' ? Number(point.y).toFixed(2) : String(Number(Number(point.y).toFixed(3))));
        const date = point.labelDate ?? formatDate(point.x, 'yyyy.MM.dd', timeZone);
        // JFreeChart drew the generator's "\n" as nothing visible, leaving the spaces around it.
        return format.replaceAll('{1}', date).replaceAll('{2}', value).replaceAll('\n', '');
    }

    global.carlosDrawChart = drawChart;
})(window);
