/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Manual check against an enabled isolated CARLOS instance and a committed synthetic note file.
// Uses an existing authenticated storage state; never embeds credentials or seeds patient records.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
    const base = process.env.DOCUMENT_SUMMARY_URL;
    const id = process.env.DOCUMENT_SUMMARY_ID;
    const state = process.env.DOCUMENT_SUMMARY_STORAGE_STATE;
    const output = process.env.DOCUMENT_SUMMARY_OUTPUT_DIR;
    assert(base && /^[1-9][0-9]*$/.test(id) && state && output,
        'Set DOCUMENT_SUMMARY_URL, DOCUMENT_SUMMARY_ID, DOCUMENT_SUMMARY_STORAGE_STATE and DOCUMENT_SUMMARY_OUTPUT_DIR');
    fs.mkdirSync(output, { recursive: true });
    const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
    try {
        const context = await browser.newContext({ storageState: state, viewport: { width: 1200, height: 1000 } });
        const page = await context.newPage();
        const errors = [];
        page.on('pageerror', e => errors.push(e.message));
        const preview = `${base}/documentManager/AiDocumentSummary?documentId=${id}`;
        const generate = `${base}/documentManager/GenerateAiDocumentSummary`;
        const response = await page.goto(preview);
        assert.equal(response.status(), 200);
        assert.match(response.headers()['cache-control'], /no-store/);
        assert.equal(await page.locator('#document-summary-form').count(), 1);
        assert(await page.locator('input[name="CSRF-TOKEN"]').inputValue());
        assert(await page.locator('button[type="submit"]').isEnabled());
        assert.match(await page.locator('.alert-warning').innerText(), /Unverified AI draft/);
        // Browser fetch preserves Secure localhost cookies, unlike a separate APIRequestContext.
        const check = (url, options = {}) => page.evaluate(async ([target, init]) => {
            const response = await fetch(target, init);
            return { status: response.status, body: await response.text() };
        }, [url, options]);
        const wrongMethod = await check(`${generate}?documentId=${id}`);
        assert.equal(wrongMethod.status, 405);
        const invalid = await check(`${base}/documentManager/AiDocumentSummary?documentId=0`);
        assert.equal(invalid.status, 400);
        const csrf = await check(generate, { method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: `documentId=${id}` });
        assert(csrf.status >= 400 || /csrf|security|forbidden/i.test(csrf.body), 'A tokenless POST must be rejected');
        assert(!csrf.body.includes('id="overview-heading"'), 'A tokenless POST cannot render a summary');
        assert(await page.evaluate(() => Array.from(document.styleSheets)
            .some(sheet => sheet.href?.includes('/bootstrap/') && sheet.cssRules.length > 0)),
            'The shared Bootstrap stylesheet must load');
        await page.screenshot({ path: path.join(output, 'before.png'), fullPage: true });
        const start = Date.now();
        await Promise.all([
            page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 650000 }),
            page.locator('button[type="submit"]').click()
        ]);
        assert.equal(await page.locator('.alert-danger').count(), 0,
            'Generation was rejected; inspect the isolated gateway and application logs');
        assert.equal(await page.locator('#overview-heading').count(), 1);
        const points = await page.locator('article.card').count();
        assert(points > 0);
        assert(await page.locator('.alert-info').isVisible(), 'Extraction limits remain visible');
        await page.locator('details summary').first().click();
        assert(await page.locator('blockquote').first().isVisible());
        assert(await page.locator('.document-summary-text').evaluateAll(elements =>
            elements.length > 0 && elements.every(element => getComputedStyle(element).whiteSpace === 'pre-wrap')),
            'Source headings, line breaks and dosage lists must remain readable');
        assert.deepEqual(errors, []);
        await page.screenshot({ path: path.join(output, 'after.png'), fullPage: true });
        const result = { passed: true, points, milliseconds: Date.now() - start,
            checks: ['preview', 'POST only', 'invalid ID', 'CSRF rejection', 'no-store', 'live generation', 'visible evidence', 'extraction warning', 'no page errors'] };
        fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify(result, null, 2));
        console.log(JSON.stringify(result));
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
