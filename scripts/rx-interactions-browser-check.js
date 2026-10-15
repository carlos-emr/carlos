#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
'use strict';
// Isolated Chromium regression of the production interaction client. No EMR credentials or database.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const {chromium} = require('playwright');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/rx-interactions.js'));
const card = id => `<div id="set_${id}"><input name="draftRevision_${id}" value="v1">
  <span id="major_${id}" style="display:none"></span><span id="moderate_${id}" style="display:none"></span>
  <span id="minor_${id}" style="display:none"></span><span id="unknown_${id}" style="display:none"></span></div>`;

async function main() {
    let reply = {warnings: [], checkFailed: false};
    let release;
    let delay = false;
    let calls = 0;
    const server = http.createServer((request, response) => {
        const url = new URL(request.url, 'http://fixture');
        if (url.pathname === '/client.js') {
            response.setHeader('Content-Type', 'application/javascript');
            response.end(source);
        } else if (url.pathname === '/warnings') {
            assert.equal(url.searchParams.get('demographicNo'), '42');
            calls++;
            const body = JSON.stringify(reply);
            const send = () => { response.setHeader('Content-Type', 'application/json'); response.end(body); };
            if (delay) release = send;
            else send();
        } else {
            response.setHeader('Content-Type', 'text/html');
            response.end(`<!doctype html><script src="/client.js" data-endpoint="/warnings" data-demographic-no="42"></script>
              <div id="interactingDrugErrorMsg"></div><div id="rxText"></div>`);
        }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const browser = await chromium.launch({headless: true,
        executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined,
        args: ['--no-sandbox']});
    try {
        const page = await browser.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.goto(`http://127.0.0.1:${server.address().port}/`); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- this script's own server is bound to 127.0.0.1 on an OS-allocated port; no user input enters the URL
        const hostile = '<img src=x onerror="window.injected=true"> / theophylline (major)';
        reply = {warnings: [{cardId: '1', severity: 'major', text: hostile}], checkFailed: false};
        await page.locator('#rxText').evaluate((stage, html) => { stage.innerHTML = html; }, card(1));
        await page.waitForFunction(text => document.getElementById('major_1')?.textContent === text, hostile);
        assert.equal(await page.locator('#major_1 img').count(), 0);
        assert.equal(await page.evaluate(() => window.injected), undefined);
        const firstCalls = calls;
        await page.waitForTimeout(200);
        assert.equal(calls, firstCalls, 'Rendering warnings must not retrigger the observer');

        reply = {warnings: [], checkFailed: true};
        await page.locator('#rxText').evaluate((stage, html) => { stage.innerHTML = html; }, card(2));
        await page.waitForFunction(() => document.getElementById('interactingDrugErrorMsg').textContent.includes('unavailable'));
        assert.equal(await page.locator('#major_2').isVisible(), false);

        delay = true;
        reply = {warnings: [{cardId: '3', severity: 'major', text: 'Old response'}], checkFailed: false};
        await page.locator('#rxText').evaluate((stage, html) => { stage.innerHTML = html; }, card(3));
        await page.waitForRequest(request => request.url().includes('/warnings'));
        await page.locator('#rxText').evaluate(stage => { stage.textContent = ''; });
        // Wait until the server holds the delayed request; there is no application-side polling.
        for (let i = 0; !release && i < 20; i++) await page.waitForTimeout(25);
        assert.ok(release);
        release();
        await page.waitForTimeout(200);
        assert.equal(await page.locator('#interactingDrugErrorMsg').innerText(), '');
        assert.equal(await page.locator('#rxText').innerText(), '');
        assert.deepEqual(errors, []);
        console.log('PASS: warnings render as text, errors remain visible, removals reject stale responses, no observer loop');
    } finally {
        await browser.close();
        await new Promise(resolve => server.close(resolve));
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
