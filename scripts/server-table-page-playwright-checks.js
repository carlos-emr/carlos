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
    const region = (number, reverse = false) => `<div data-server-page-region><form name="mergeform" data-server-page-preserve-selection>
        <table><thead><tr><th><button name="tableSortChange" value="2" form="pager">Name</button></th></tr></thead><tbody>
        <tr><td>Page ${number}</td></tr>${(reverse ? ['42', '41'] : ['41', '42']).map(id => `<tr><td>
        <input type="radio" name="head" value="${id}"><input type="checkbox" name="records" value="${id}"></td></tr>`).join('')}</tbody></table></form>
        <form id="pager" data-server-page-form data-context="" action="/search" method="post">
        <input name="tablePage" type="hidden" value="${number}">
        <input name="method" type="hidden" value="attachSearch"><input name="criteria.firstName" type="hidden" value="Synthetic">
        <input name="noteId" type="hidden" value="7"><input name="sourceChartContext" type="hidden" value="source-note-context">
        <button name="tablePage" data-server-page-focus="previous" value="${number - 1}" ${number === 0 ? 'disabled' : ''}>Previous</button>
        <button name="tablePage" data-server-page-focus="next" value="${number + 1}" ${number === 3 ? 'disabled' : ''}>Next</button><span data-server-page-error data-error="Could not load; entries kept" role="alert"></span>
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
                return route.fulfill({contentType: 'text/html', body: missingResults ? '<p>Login required</p>' : region(Number(fields.get('tablePage')), fields.has('tableSortChange'))});
            }
            return route.fulfill({contentType: 'text/html', body: `<!doctype html><input id="draft" value="unsaved clinical draft">${region(0)}`});
        });
        await page.goto('http://carlos.test/');
        await page.addScriptTag({content: csrf});
        await page.addScriptTag({content: paging});
        await page.getByRole('button', {name: 'Next', exact: true}).focus();
        await page.keyboard.press('Enter');
        await page.getByText('Page 1', {exact: true}).waitFor();
        assert.equal(await page.evaluate(() => document.activeElement.dataset.serverPageFocus), 'next', 'keyboard paging keeps focus on Next');
        await page.locator('input[name="head"][value="41"]').check();
        await page.locator('input[name="records"][value="42"]').check();
        const oldRegion = await page.locator('[data-server-page-region]').elementHandle();
        await page.getByRole('button', {name: 'Name', exact: true}).click();
        await page.waitForFunction(old => !old.isConnected, oldRegion);
        assert.equal(await page.locator('input[name="head"][value="41"]').isChecked(), true);
        assert.equal(await page.locator('input[name="records"][value="42"]').isChecked(), true);
        assert.equal(await page.locator('input[name="head"][value="42"]').isChecked(), false);
        assert.equal(await page.locator('input[name="records"][value="41"]').isChecked(), false);
        assert.equal(await page.evaluate(() => document.activeElement.name), 'tableSortChange', 'sort focus is restored');
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        await page.getByText('Page 2', {exact: true}).waitFor();
        assert.equal(tokenNumber, 3, 'replacement forms bootstrap a fresh token for subsequent POSTs');
        assert.equal(await page.locator('#draft').inputValue(), 'unsaved clinical draft');
        fail = true;
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        await page.getByRole('alert').filter({hasText: 'Could not load'}).waitFor();
        assert.equal(await page.getByText('Page 2', {exact: true}).count(), 1);
        fail = false; missingResults = true;
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        await page.getByRole('alert').filter({hasText: 'Could not load'}).waitFor();
        assert.equal(await page.locator('#draft').inputValue(), 'unsaved clinical draft');
        missingResults = false;
        await page.getByRole('button', {name: 'Next', exact: true}).focus();
        await page.keyboard.press('Enter');
        await page.getByText('Page 3', {exact: true}).waitFor();
        assert.equal(await page.evaluate(() => document.activeElement.dataset.serverPageFocus), 'previous', 'last-page focus moves to an enabled paging control');
        assert.equal(requests, 6);
        await page.clock.install();
        for (const stalledPath of ['/csrfguard', '/search']) {
            // Hold one real browser request until the paging deadline expires. The operator must
            // be able to retry on this page with its active selection and draft still present.
            let arrived;
            const intercepted = new Promise(resolve => { arrived = resolve; });
            await page.route('**' + stalledPath, () => arrived(), {times: 1});
            await page.getByRole('button', {name: 'Previous', exact: true}).click();
            await intercepted;
            await page.clock.fastForward(30_000);
            await page.getByRole('alert').filter({hasText: 'Could not load'}).waitFor({timeout: 2000});
            assert.equal(await page.getByText('Page 3', {exact: true}).count(), 1);
            assert.equal(await page.locator('#draft').inputValue(), 'unsaved clinical draft');
        }
        await page.getByRole('button', {name: 'Previous', exact: true}).click();
        await page.getByText('Page 2', {exact: true}).waitFor();
        assert.deepEqual(errors, []);
        console.log('PASS: POST paging, fresh CSRF, retained drafts/merge selections, keyboard focus and visible failures');
    } finally { await browser.close(); }
}
require('./lib/legacy-check').runLegacyCheck('server-table-page', async () => {
    await main().catch(error => {console.error(error); process.exitCode = 1;});
});
