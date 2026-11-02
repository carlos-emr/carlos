/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');

async function main() {
    const script = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/carlos-tables.js'));
    const rows = [[12, '100'], [2, '00123'], [100, '02']].map(([number, code]) =>
        `<tr><td data-sort-value="${number}" data-sort-type="number">${number}</td>` +
        `<td data-sort-value="${code}" data-sort-type="text">${code}</td>` +
        `<td><button type="button" class="edit" data-id="${number}">Edit</button></td></tr>`).join('');
    const html = `<!doctype html><html><body><form id="fixture"><table id="codes" data-carlos-table
        data-page-size="2" data-default-sort="1" data-default-order="descending">
        <thead><tr><th><button type="button" class="table-sort">Number</button></th>
        <th><button type="button" class="table-sort">Code</button></th><th>Options</th></tr></thead>
        <tbody>${rows}</tbody></table></form><script src="/tables.js"></script></body></html>`;
    const server = http.createServer((request, response) => {
        response.setHeader('Content-Type', request.url === '/tables.js' ? 'text/javascript' : 'text/html');
        response.end(request.url === '/tables.js' ? script : html);
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    let browser;
    try {
        browser = await chromium.launch({ executablePath: process.env.CARLOS_SMOKE_CHROMIUM || '/usr/bin/chromium',
            headless: true, args: ['--no-sandbox'] });
        const page = await browser.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.goto(`http://127.0.0.1:${server.address().port}`);
        const visible = () => page.locator('#codes tbody tr:visible td:first-child').allTextContents();
        assert.deepEqual(await visible(), ['100', '12']);
        assert.equal(await page.getByRole('button', {name: 'Previous', exact: true}).isDisabled(), true);
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        assert.deepEqual(await visible(), ['2']);
        await page.getByRole('button', {name: 'Number', exact: true}).click();
        assert.deepEqual(await visible(), ['2', '12']);
        assert.equal(await page.locator('#codes th').first().getAttribute('aria-sort'), 'ascending');
        await page.getByRole('button', {name: 'Code', exact: true}).click();
        assert.deepEqual(await visible(), ['2', '100']);
        await page.evaluate(() => {
            window.edited = null;
            document.querySelector('#fixture').addEventListener('submit', event => {
                event.preventDefault(); throw new Error('sorting must not submit the form');
            });
            document.querySelectorAll('.edit').forEach(button => button.addEventListener('click', () => {
                window.edited = button.dataset.id;
            }));
        });
        await page.locator('#codes tbody tr:visible .edit').first().click();
        assert.equal(await page.evaluate(() => window.edited), '2');
        await page.getByRole('button', {name: 'Number', exact: true}).focus();
        await page.keyboard.press('Enter');
        assert.deepEqual(errors, []);
        console.log('PASS: typed sorting, default order, paging, keyboard control, row actions, and form isolation');
    } finally {
        if (browser) await browser.close();
        await new Promise(resolve => server.close(resolve));
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
