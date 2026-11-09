#!/usr/bin/env node
// Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.argv[2] ? path.resolve(process.argv[2]) : 'playwright');

async function main() {
    const source = fs.readFileSync(path.resolve(__dirname, '../../src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');
    const handler = source.match(/function handlePrescriptionPreviewLoad\(event\) \{[\s\S]*?\n\}/)?.[0];
    const registration = source.match(/document\.addEventListener\('load', handlePrescriptionPreviewLoad, true\);/)?.[0];
    assert.ok(handler && registration, 'The actual preview capture binding must exist');
    const frameStart = source.indexOf("<iframe id='preview'");
    assert.ok(frameStart > source.indexOf(registration), 'Register before a cached preview can finish loading');
    const frameEnd = source.indexOf('</iframe>', frameStart);
    assert.ok(frameEnd > frameStart);
    const markup = source.slice(frameStart, frameEnd);
    assert.match(markup, /title='Prescription preview'/);
    assert.doesNotMatch(markup, /\b(?:onload|tabindex)\s*=/i);
    const options = { headless: true };
    if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE) options.executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
    const browser = await chromium.launch(options);
    try {
        const page = await browser.newPage();
        page.setDefaultTimeout(10000);
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setContent(`<script>
            window.calls = [];
            function setComment() { calls.push('comment'); }
            function updateHalfLetterPrintControls() { calls.push('controls'); }
            ${handler}
            ${registration}
            </script><iframe id="preview" title="Prescription preview" srcdoc="<p>preview</p>"></iframe>
            <iframe id="other" title="Unrelated fixture" srcdoc="<p>other</p>"></iframe>`);
        await page.waitForFunction(() => calls.length === 2);
        assert.deepEqual(await page.evaluate(() => calls), ['comment', 'controls']);
        await page.evaluate(() => { document.getElementById('preview').srcdoc = '<p>reloaded preview</p>'; });
        await page.waitForFunction(() => calls.length === 4);
        assert.deepEqual(await page.evaluate(() => calls), ['comment', 'controls', 'comment', 'controls']);
        await page.evaluate(async () => {
            document.dispatchEvent(new Event('load'));
            const other = document.getElementById('other');
            await new Promise(resolve => {
                other.addEventListener('load', resolve, { once: true });
                other.srcdoc = '<p>unrelated reload</p>';
            });
        });
        assert.equal(await page.evaluate(() => calls.length), 4);
        assert.deepEqual(errors, []);
        console.log('PASS actual preview load capture, reload, ordering and unrelated-load refusal');
    } finally {
        await browser.close();
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
