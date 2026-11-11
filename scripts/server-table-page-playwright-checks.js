/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
const {getLaunchOptions} = require('./lib/playwright-harness');

async function main() {
    const paging = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/server-table-page.js'), 'utf8');
    const csrf = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/csrfTokenFetch.js'), 'utf8');
    const region = number => `<div data-server-page-region><table><tbody><tr><td>Page ${number}</td></tr></tbody></table>
        <form id="pager" data-server-page-form data-context="" action="/search" method="post">
        <input name="method" type="hidden" value="attachSearch"><input name="criteria.firstName" type="hidden" value="Synthetic">
        <input name="noteId" type="hidden" value="7"><input name="sourceChartContext" type="hidden" value="source-note-context">
        <button name="tablePage" value="${number + 1}">Next</button><span data-server-page-error data-error="Could not load; entries kept" role="alert"></span>
        </form></div>`;
    const browser = await chromium.launch(getLaunchOptions(process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.CARLOS_SMOKE_CHROMIUM));
    try {
        const page = await browser.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        let tokenNumber = 0;
        let requests = 0;
        let fail = false;
        let missingResults = false;
        await page.route('http://carlos.test/**', async route => {
            const request = route.request();
            const url = new URL(request.url());
            if (url.pathname === '/csrfguard') {
                return route.fulfill({contentType: 'text/javascript', body: `var masterTokenValue="token-${++tokenNumber}";`});
            }
            if (url.pathname === '/search') {
                requests++;
                const fields = new URLSearchParams(request.postData());
                assert.equal(request.method(), 'POST');
                assert.equal(fields.get('criteria.firstName'), 'Synthetic');
                assert.equal(fields.get('noteId'), '7');
                assert.equal(fields.get('sourceChartContext'), 'source-note-context');
                assert.equal(request.headers()['csrf-token'], `token-${tokenNumber}`);
                if (fail) return route.fulfill({status: 503, contentType: 'text/html', body: 'Unavailable'});
                return route.fulfill({contentType: 'text/html', body: missingResults ? '<p>Login required</p>' : region(Number(fields.get('tablePage')))});
            }
            return route.fulfill({contentType: 'text/html', body: `<!doctype html><input id="draft" value="unsaved clinical draft">${region(0)}`});
        });
        await page.goto('http://carlos.test/');
        await page.addScriptTag({content: csrf});
        await page.addScriptTag({content: paging});
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        await page.getByText('Page 1', {exact: true}).waitFor();
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        await page.getByText('Page 2', {exact: true}).waitFor();
        assert.equal(tokenNumber, 2, 'replacement forms bootstrap a fresh token for subsequent POSTs');
        assert.equal(await page.locator('#draft').inputValue(), 'unsaved clinical draft');
        fail = true;
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        await page.getByRole('alert').filter({hasText: 'Could not load'}).waitFor();
        assert.equal(await page.getByText('Page 2', {exact: true}).count(), 1);
        fail = false; missingResults = true;
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        await page.getByRole('alert').filter({hasText: 'Could not load'}).waitFor();
        assert.equal(await page.locator('#draft').inputValue(), 'unsaved clinical draft');
        assert.equal(requests, 4);
        assert.deepEqual(errors, []);
        console.log('PASS: repeated POST paging, fresh CSRF, note/context identity, retained drafts and visible loading failures');
    } finally { await browser.close(); }
}
require('./lib/legacy-check').runLegacyCheck('server-table-page', async () => {
    await main().catch(error => {console.error(error); process.exitCode = 1;});
});
