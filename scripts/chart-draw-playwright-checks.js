/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The chart draw script the renderer runs inside headless Chromium (library/chartjs/carlos-chart-draw.js),
 * exercised with the bundled Chart.js in a Playwright browser, without the driver service:
 *
 *   1. A medication-only chart (ChartMeds) fills the whole image with the medication panel; there
 *      is no empty measurement axis above it.
 *   2. A chart with measurement series and medications keeps the two aligned panels.
 *   3. Long medication names are shortened for the category axis, as the former chart did.
 *   4. A series observed within one day labels its date axis with the time, not the same date on
 *      every tick.
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const { getLaunchOptions } = require('./lib/playwright-harness');

const LIBRARY = path.join(__dirname, '../src/main/webapp/library/chartjs');

async function draw(page, spec, width, height) {
    return page.evaluate(([spec, width, height]) => {
        const charts = window.carlosDrawChart(spec, width, height);
        return {
            canvases: [...document.querySelectorAll('canvas')].map(c => ({ width: c.width, height: c.height })),
            labels: charts.map(chart => Object.values(chart.scales).map(scale => ({
                id: scale.id, ticks: scale.ticks.map(tick => String(tick.label ?? tick.value)) }))),
        };
    }, [spec, width, height]);
}

async function main() {
    const day = 86_400_000;
    const t0 = Date.UTC(2024, 0, 15, 9, 30);
    let browser;
    try {
        browser = await chromium.launch(getLaunchOptions(
            process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.CARLOS_SMOKE_CHROMIUM));
        const page = await browser.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setContent('<!doctype html><html><body></body></html>');
        for (const file of ['chart.umd.min.js', 'carlos-chart-draw.js']) {
            await page.addScriptTag({ path: path.join(LIBRARY, file) });
        }
        const longName = 'SYNTHETIC MEDICATION WITH A VERY LONG PRODUCT NAME 500MG TABLET';
        const medications = Array.from({ length: 12 }, (_, i) => ({
            name: i === 0 ? longName : `SYNTHETIC MEDICATION ${i + 1}`, start: t0 + i * 10 * day, end: t0 + (i + 20) * day }));
        const base = { title: 'Synthetic', xLabel: 'Days', yLabel: 'MEDS', datasets: [], medications,
            dateAxis: true, timeZone: 'UTC', pointLabels: 'none', fixedDecimals: false };

        const medsOnly = await draw(page, base, 1000, 360);
        assert.equal(medsOnly.canvases.length, 1, 'a medication-only chart draws one panel');
        assert.deepEqual(medsOnly.canvases[0], { width: 1000, height: 360 }, 'the medication panel fills the image');
        const names = medsOnly.labels[0].find(scale => scale.id === 'y').ticks;
        assert.equal(names.length, 12, 'every medication keeps its row label');
        assert.ok(names.every(label => label.length <= 33), `long names are shortened for the axis: ${names[0]}`);
        assert.ok(names[0].endsWith('...') && longName.startsWith(names[0].slice(0, -3)), 'a shortened name keeps its prefix');

        const series = { label: 'Weight', data: [{ x: t0, y: 61 }, { x: t0 + 3 * 3_600_000, y: 63 }],
            showLine: true, yAxisID: 'y', borderColor: 'red', pointRadius: 3 };
        const mixed = await draw(page, { ...base, datasets: [series] }, 1000, 600);
        assert.equal(mixed.canvases.length, 2, 'series plus medications draw two aligned panels');
        assert.equal(mixed.canvases[0].height + mixed.canvases[1].height, 600, 'the panels share the image height');
        const sameDay = await draw(page, { ...base, datasets: [series], medications: [] }, 1000, 600);
        const dateTicks = sameDay.labels[0].find(scale => scale.id === 'x').ticks;
        assert.ok(dateTicks.some(label => /\d{2}:\d{2}/.test(label)), `a same-day series shows times on its date axis: ${dateTicks}`);

        const twoYears = { ...base, datasets: [{ ...series, data: [{ x: t0, y: 61 }, { x: t0 + 700 * day, y: 63 }] }], medications: [] };
        const long = await draw(page, twoYears, 1000, 600);
        const longTicks = long.labels[0].find(scale => scale.id === 'x').ticks;
        assert.ok(longTicks.every(label => /^\d{4}-\d{2}-\d{2}$/.test(label)), `a multi-year series shows dates only: ${longTicks}`);
        assert.deepEqual(errors, []);
        console.log('PASS: medication-only layout, aligned panels, shortened names, time-aware date ticks');
    } finally {
        if (browser) await browser.close();
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
