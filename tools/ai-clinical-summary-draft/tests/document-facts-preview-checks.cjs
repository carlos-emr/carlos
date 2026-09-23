/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Check a local synthetic comparison preview. No gateway call or external assets needed.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
(async () => {
    const preview = process.env.DOCUMENT_FACTS_PREVIEW;
    const output = process.env.DOCUMENT_FACTS_OUTPUT_DIR;
    assert(preview && output, 'Set DOCUMENT_FACTS_PREVIEW and DOCUMENT_FACTS_OUTPUT_DIR');
    fs.mkdirSync(output, { recursive: true });
    const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
    try {
        const page = await browser.newPage({ viewport: { width: 1500, height: 1100 } });
        const errors = [];
        const network = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('request', request => { if (/^https?:/.test(request.url())) network.push(request.url()); });
        await page.goto(pathToFileURL(path.resolve(preview)).href);
        const example = page.locator('.case').filter({ has: page.locator('.facts table') }).first();
        assert.equal(await example.count(), 1, 'Need an accepted experimental draft to inspect');
        assert(await example.locator('.synopsis').isVisible());
        await example.screenshot({ path: path.join(output, 'desktop.png') });
        await example.locator('.facts table details summary').first().click();
        assert(await example.locator('.facts table blockquote').first().isVisible());
        await page.setViewportSize({ width: 390, height: 844 });
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Mobile horizontal overflow');
        await example.screenshot({ path: path.join(output, 'mobile.png') });
        assert.deepEqual(errors, []);
        assert.deepEqual(network, [], 'The preview must not fetch external resources');
        const result = { passed: true, checks: ['synopsis', 'facts table', 'expandable evidence',
            '390px mobile without horizontal overflow', 'no external requests', 'no page errors'] };
        fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify(result, null, 2));
        console.log(JSON.stringify(result));
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
