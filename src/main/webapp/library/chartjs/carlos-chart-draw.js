/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Draws the chart specification produced by MeasurementChart.toConfig() with the bundled Chart.js.
 * BrowserChartRenderer injects this file after chart.umd.min.js into the renderer's headless
 * Chromium and calls window.carlosDrawChart(spec, width, height); the same function is exercised
 * in an ordinary browser by scripts/chart-draw-playwright-checks.js. It returns the Chart
 * instances it drew (measurement panel first, medication panel last).
 */
(function (global) {
    'use strict';
    const HOUR = 3600000;
    const DAY = 86400000;
    const LABEL_LIMIT = 33;
    // Distinct, print-safe fills for the medication rows (the former chart coloured each drug).
    const PALETTE = ['#4c78a8', '#e45756', '#54a24b', '#f58518', '#72b7b2', '#b279a2',
        '#eeca3b', '#9d755d', '#ff9da6', '#79706e', '#d67195', '#1f77b4'];

    function shorten(label) {
        return label.length > LABEL_LIMIT ? label.slice(0, LABEL_LIMIT - 3) + '...' : label;
    }

    /** The zone's UTC offset at an instant, in milliseconds. */
    function zoneOffset(instant, timeZone) {
        const name = new Intl.DateTimeFormat('en-US', { timeZone, timeZoneName: 'longOffset' })
            .formatToParts(new Date(instant)).find(part => part.type === 'timeZoneName');
        const match = /GMT([+-])(\d{2}):?(\d{2})?/.exec(name ? name.value : '');
        if (!match) return 0;
        return (match[1] === '-' ? -1 : 1) * (Number(match[2]) * 60 + Number(match[3] || 0)) * 60000;
    }

    /** The instant of the zone-local calendar time y-m-d h:00 (month zero-based; overflow allowed). */
    function zonedInstant(y, m, d, h, timeZone) {
        const naive = Date.UTC(y, m, d, h);
        const once = naive - zoneOffset(naive, timeZone);
        return naive - zoneOffset(once, timeZone);
    }

    function zonedParts(instant, timeZone) {
        const parts = {};
        new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', hourCycle: 'h23' })
            .formatToParts(new Date(instant)).forEach(part => { if (part.type !== 'literal') parts[part.type] = Number(part.value); });
        return parts;
    }

    /**
     * Tick instants on calendar boundaries in the zone (hours, days, weeks, months or years by
     * span), which Chart.js's linear scale would otherwise place at round numbers of milliseconds.
     */
    function calendarTicks(min, max, timeZone) {
        const span = max - min;
        const p = zonedParts(min, timeZone);
        const y = p.year, m = p.month - 1, d = p.day;
        let unit, step;
        if (span <= 3 * DAY) { unit = 'hour'; step = span <= 8 * HOUR ? 1 : span <= DAY ? 3 : 6; }
        else if (span <= 24 * DAY) { unit = 'day'; step = span <= 12 * DAY ? 1 : 2; }
        else if (span <= 130 * DAY) { unit = 'day'; step = 7; }
        else if (span <= 3 * 365 * DAY) { unit = 'month'; step = span <= 400 * DAY ? 1 : span <= 800 * DAY ? 2 : 3; }
        else { unit = 'year'; step = Math.max(1, Math.ceil(span / (365 * DAY) / 10)); }
        const at = k => unit === 'hour' ? zonedInstant(y, m, d, Math.floor(p.hour / step) * step + k * step, timeZone)
            : unit === 'day' ? zonedInstant(y, m, d + k * step, 0, timeZone)
            : unit === 'month' ? zonedInstant(y, m + k * step, 1, 0, timeZone)
            : zonedInstant(y + k * step, 0, 1, 0, timeZone);
        const ticks = [];
        for (let k = 0; k < 400; k++) {
            const t = at(k);
            if (t > max) break;
            if (t >= min) ticks.push(t);
        }
        return { unit, ticks };
    }

    function tickLabel(instant, unit, timeZone) {
        const date = new Date(instant);
        // Two lines for hourly ticks: the date over the time, so neighbouring labels do not collide.
        if (unit === 'hour') return [date.toLocaleDateString('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }),
            date.toLocaleTimeString('en-CA', { timeZone, hour: '2-digit', minute: '2-digit', hour12: false })];
        if (unit === 'month') return date.toLocaleDateString('en-CA', { timeZone, year: 'numeric', month: '2-digit' });
        if (unit === 'year') return date.toLocaleDateString('en-CA', { timeZone, year: 'numeric' });
        return date.toLocaleDateString('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' });
    }

    function drawChart(spec, width, height) {
        document.body.replaceChildren();
        document.body.style.cssText = 'margin:0;background:white';
        // A medication started and stopped the same day keeps a one-day extent, so its bar is
        // still visible, as the former chart's minimum bar width made it.
        const medications = (spec.medications || []).map(m => ({ ...m, end: Math.max(m.end, m.start + DAY) }));
        const datasets = spec.datasets || [];
        const allDates = datasets.flatMap(ds => ds.data.map(p => p.x));
        medications.forEach(m => allDates.push(m.start, m.end));
        const timeZone = spec.timeZone;
        const dateOptions = { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' };
        const pointDateLabel = value => new Date(value).toLocaleDateString('en-CA', dateOptions);
        const scales = {
            x: { type: 'linear', title: { display: true, text: spec.xLabel } },
            y: { type: 'linear', beginAtZero: false, title: { display: true, text: spec.yLabel } },
        };
        if (spec.secondAxis) {
            scales.y2 = { type: 'linear', position: 'right', beginAtZero: false, grid: { drawOnChartArea: false },
                title: { display: true, text: spec.secondAxis } };
        }
        let dateAxis = null;
        if (spec.dateAxis && allDates.length) {
            // A margin on both sides, as the former chart had, so a bar or point never sits on the
            // axis line; ticks on calendar boundaries in the chart's zone, labelled by their unit.
            const first = Math.min(...allDates), last = Math.max(...allDates);
            const margin = Math.max((last - first) * 0.04, 12 * HOUR);
            const calendar = calendarTicks(first - margin, last + margin, timeZone);
            dateAxis = {
                min: first - margin, max: last + margin,
                afterBuildTicks(scale) {
                    scale.ticks = calendarTicks(scale.min, scale.max, timeZone).ticks.map(value => ({ value }));
                },
                ticks: { autoSkip: true, includeBounds: false, callback: value => tickLabel(value, calendar.unit, timeZone) },
            };
            Object.assign(scales.x, dateAxis);
        }
        const referenceRanges = { id: 'referenceRanges', afterDatasetsDraw(chart) {
            datasets.forEach((ds, index) => {
                const ctx = chart.ctx;
                ctx.save(); ctx.strokeStyle = 'green'; ctx.lineWidth = 1;
                if (!ds.referenceRanges) {
                    if (spec.pointLabels !== 'none') {
                        ctx.font = '10px Helvetica'; ctx.textAlign = 'center'; ctx.fillStyle = '#333';
                        const elements = chart.getDatasetMeta(index).data;
                        ds.data.forEach((p, i) => {
                            const element = elements[i];
                            if (!element) return;
                            const value = spec.fixedDecimals ? Number(p.y).toFixed(2) : String(Number(Number(p.y).toFixed(3)));
                            ctx.fillText(value, element.x, element.y - 8);
                            if (spec.pointLabels === 'dateValue') ctx.fillText(pointDateLabel(p.x), element.x, element.y - 20);
                        });
                    }
                    ctx.restore();
                    return;
                }
                ds.data.forEach(p => {
                    if (p.isRangeEnd) return;
                    const x = chart.scales.x.getPixelForValue(p.x);
                    const low = chart.scales.y.getPixelForValue(p.y);
                    const high = chart.scales.y.getPixelForValue(p.high);
                    ctx.beginPath(); ctx.moveTo(x, low); ctx.lineTo(x, high);
                    ctx.moveTo(x - 3, low); ctx.lineTo(x + 3, low); ctx.moveTo(x - 3, high); ctx.lineTo(x + 3, high); ctx.stroke();
                });
                ctx.restore();
            });
        } };
        const charts = [];
        let mainChart = null;
        let mainHeight = 0;
        if (datasets.length) {
            // A medication-only chart (ChartMeds) has no measurement panel at all.
            const main = document.createElement('canvas');
            main.style.display = 'block';
            main.width = width;
            mainHeight = medications.length ? Math.floor(height * 0.7) : height;
            main.height = mainHeight;
            document.body.append(main);
            mainChart = new Chart(main, { type: 'scatter', data: { datasets }, plugins: [referenceRanges],
                options: { responsive: false, animation: false, devicePixelRatio: 1, scales,
                    plugins: { title: { display: true, text: spec.title }, legend: { display: true } } } });
            charts.push(mainChart);
        }
        if (medications.length) {
            const meds = document.createElement('canvas');
            meds.width = width;
            meds.height = height - mainHeight;
            meds.style.display = 'block';
            document.body.append(meds);
            const names = [...new Set(medications.map(m => m.name))];
            const colour = name => PALETTE[names.indexOf(name) % PALETTE.length];
            const medicationChart = new Chart(meds, { type: 'bar', data: { labels: names, datasets: [{ label: 'Meds',
                data: medications.map(m => ({ y: m.name, x: [m.start, m.end] })),
                backgroundColor: medications.map(m => colour(m.name)) }] },
                options: { indexAxis: 'y', responsive: false, animation: false, devicePixelRatio: 1,
                    scales: { x: { type: 'linear', ...(dateAxis || { min: scales.x.min, max: scales.x.max }),
                        title: { display: true, text: 'Date/Time' } },
                        // reverse: the first medication listed sits at the bottom, as on the former symbol axis.
                        y: { type: 'category', labels: names, reverse: true, title: { display: true, text: 'Meds' },
                            ticks: { autoSkip: false, callback(value) {
                                // Long product names used to be shortened to thirty characters on the former axis.
                                return shorten(this.getLabelForValue(value));
                            } } } },
                    plugins: { title: { display: !mainChart, text: spec.title },
                        legend: { display: true, position: 'bottom', labels: { generateLabels: () => names.map((name, index) => ({
                            text: shorten(name), fillStyle: colour(name), strokeStyle: colour(name), lineWidth: 0, hidden: false, index })) } } } } });
            charts.push(medicationChart);
            if (mainChart) {
                // Matching date bounds alone is insufficient: long medication names consume more
                // left-axis space. Align the plot rectangles before producing a print image.
                const panels = [mainChart, medicationChart];
                for (let attempt = 0; attempt < 3; attempt++) {
                    const left = Math.max(...panels.map(chart => chart.chartArea.left));
                    const right = Math.min(...panels.map(chart => chart.chartArea.right));
                    if (panels.every(chart => Math.abs(chart.chartArea.left - left) <= 1 && Math.abs(chart.chartArea.right - right) <= 1)) break;
                    panels.forEach(chart => {
                        const padding = chart.options.layout.padding;
                        chart.options.layout.padding = {
                            top: padding.top || 0, bottom: padding.bottom || 0,
                            left: (padding.left || 0) + left - chart.chartArea.left,
                            right: (padding.right || 0) + chart.chartArea.right - right };
                        chart.update('none');
                    });
                }
                if (Math.abs(mainChart.chartArea.left - medicationChart.chartArea.left) > 1
                        || Math.abs(mainChart.chartArea.right - medicationChart.chartArea.right) > 1) {
                    throw new Error('Chart date axes could not be aligned');
                }
            }
        }
        return charts;
    }

    global.carlosDrawChart = drawChart;
})(window);
