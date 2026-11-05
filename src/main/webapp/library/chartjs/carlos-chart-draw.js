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
    const DAY = 86400000;
    const LABEL_LIMIT = 33;

    function drawChart(spec, width, height) {
        document.body.replaceChildren();
        document.body.style.cssText = 'margin:0;background:white';
        // A medication started and stopped the same day keeps a one-day extent, so its bar is
        // still visible, as the former chart's minimum bar width made it.
        const medications = (spec.medications || []).map(m => ({ ...m, end: Math.max(m.end, m.start + DAY) }));
        const datasets = spec.datasets || [];
        const allDates = datasets.flatMap(ds => ds.data.map(p => p.x));
        medications.forEach(m => allDates.push(m.start, m.end));
        const span = allDates.length ? Math.max(...allDates) - Math.min(...allDates) : 0;
        const dateOptions = { timeZone: spec.timeZone, year: 'numeric', month: '2-digit', day: '2-digit' };
        // Within two days every tick would repeat one date, so those axes label the time as well.
        const tickOptions = span < 2 * DAY
            ? { ...dateOptions, hour: '2-digit', minute: '2-digit', hour12: false } : dateOptions;
        const dateLabel = value => new Date(value).toLocaleString('en-CA', tickOptions);
        const pointDateLabel = value => new Date(value).toLocaleDateString('en-CA', dateOptions);
        const scales = {
            x: { type: 'linear', title: { display: true, text: spec.xLabel }, ticks: spec.dateAxis ? { callback: dateLabel } : {} },
            y: { type: 'linear', beginAtZero: false, title: { display: true, text: spec.yLabel } },
        };
        if (spec.secondAxis) {
            scales.y2 = { type: 'linear', position: 'right', beginAtZero: false, grid: { drawOnChartArea: false },
                title: { display: true, text: spec.secondAxis } };
        }
        if (medications.length && allDates.length) {
            scales.x.min = Math.min(...allDates);
            scales.x.max = Math.max(...allDates);
        }
        const referenceRanges = { id: 'referenceRanges', afterDatasetsDraw(chart) {
            datasets.forEach((ds, index) => {
                const ctx = chart.ctx;
                ctx.save(); ctx.strokeStyle = 'green'; ctx.lineWidth = 1;
                if (!ds.referenceRanges) {
                    if (spec.pointLabels !== 'none') {
                        ctx.font = '8px Helvetica'; ctx.textAlign = 'center'; ctx.fillStyle = '#333';
                        const elements = chart.getDatasetMeta(index).data;
                        ds.data.forEach((p, i) => {
                            const element = elements[i];
                            if (!element) return;
                            const value = spec.fixedDecimals ? Number(p.y).toFixed(2) : String(Number(Number(p.y).toFixed(3)));
                            ctx.fillText(value, element.x, element.y - 5);
                            if (spec.pointLabels === 'dateValue') ctx.fillText(pointDateLabel(p.x), element.x, element.y - 16);
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
            const medicationChart = new Chart(meds, { type: 'bar', data: { labels: names, datasets: [{ label: 'Meds',
                data: medications.map(m => ({ y: m.name, x: [m.start, m.end] })), backgroundColor: '#4c78a8' }] },
                options: { indexAxis: 'y', responsive: false, animation: false, devicePixelRatio: 1,
                    scales: { x: { type: 'linear', min: scales.x.min, max: scales.x.max, ticks: { callback: dateLabel },
                        title: { display: true, text: 'Date/Time' } },
                        y: { type: 'category', labels: names, title: { display: true, text: 'Meds' }, ticks: { autoSkip: false, callback(value) {
                            // Long product names used to be shortened to thirty characters on the former axis.
                            const label = this.getLabelForValue(value);
                            return label.length > LABEL_LIMIT ? label.slice(0, LABEL_LIMIT - 3) + '...' : label;
                        } } } },
                    plugins: { legend: { display: false }, title: { display: !mainChart, text: spec.title } } } });
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
