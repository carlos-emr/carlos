/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

async function main() {
    const script = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/carlos-tables.js'));
    const rows = [[12, '100'], [2, '00123'], [100, '02']].map(([number, code]) =>
        `<tr><td data-sort-value="${number}" data-sort-type="number">${number}</td>` +
        `<td data-sort-value="${code}" data-sort-type="text">${code}</td>` +
        `<td><input type="checkbox" name="records" value="${number}"><input type="radio" name="head" value="${number}">` +
        `<button type="button" class="edit" data-id="${number}">Edit</button></td></tr>`).join('');
    const html = `<!doctype html><html><body><form id="fixture"><table id="codes" data-carlos-table
        data-page-size="2" data-default-sort="1" data-default-order="descending">
        <thead><tr><th><span data-table-sort>Number</span></th>
        <th><span data-table-sort>Code</span></th><th>Options</th></tr></thead>
        <tbody>${rows}</tbody></table></form></body></html>`;
    let browser;
    try {
        browser = await chromium.launch({ executablePath: process.env.CARLOS_SMOKE_CHROMIUM || '/usr/bin/chromium',
            headless: true, args: ['--no-sandbox'] });
        const page = await browser.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setContent(html);
        await page.addScriptTag({content: script.toString('utf8')});
        await page.evaluate(() => {
            window.submits = 0;
            window.edited = null;
            document.querySelector('#fixture').addEventListener('submit', event => {
                event.preventDefault(); window.submits++;
            });
            document.querySelectorAll('.edit').forEach(button => button.addEventListener('click', () => {
                window.edited = button.dataset.id;
            }));
        });
        const visible = () => page.locator('#codes tbody tr:visible td:first-child').allTextContents();
        assert.deepEqual(await visible(), ['100', '12']);
        assert.equal(await page.getByRole('button', {name: 'Previous', exact: true}).isDisabled(), true);
        await page.locator('input[name="records"][value="100"]').check();
        await page.locator('input[name="head"][value="100"]').check();
        await page.getByRole('button', {name: 'Next', exact: true}).click();
        assert.deepEqual(await visible(), ['2']);
        assert.deepEqual(await page.evaluate(() => [...new FormData(document.querySelector('#fixture')).entries()]), []);
        assert.equal(await page.getByRole('button', {name: 'Page 2', exact: true}).evaluate(el => el === document.activeElement), true);
        await page.getByRole('button', {name: 'Page 1', exact: true}).click();
        assert.equal(await page.getByRole('button', {name: 'Page 1', exact: true}).evaluate(el => el === document.activeElement), true);
        assert.equal(await page.locator('input[name="records"][value="100"]').isChecked(), false);
        await page.getByRole('button', {name: 'Number', exact: true}).click();
        assert.deepEqual(await visible(), ['2', '12']);
        assert.equal(await page.locator('#codes th').first().getAttribute('aria-sort'), 'ascending');
        await page.getByRole('button', {name: 'Code', exact: true}).click();
        assert.deepEqual(await visible(), ['2', '100']);
        await page.locator('#codes tbody tr:visible .edit').first().click();
        assert.equal(await page.evaluate(() => window.edited), '2');
        await page.getByRole('button', {name: 'Number', exact: true}).focus();
        await page.keyboard.press('Enter');
        assert.deepEqual(await visible(), ['2', '12']);
        assert.equal(await page.locator('#codes th').first().getAttribute('aria-sort'), 'ascending');
        await page.keyboard.press('Enter');
        assert.deepEqual(await visible(), ['100', '12']);
        assert.equal(await page.locator('#codes th').first().getAttribute('aria-sort'), 'descending');
        assert.equal(await page.evaluate(() => window.submits), 0);
        assert.deepEqual(errors, []);
        const noScript = await browser.newContext({javaScriptEnabled: false});
        const plain = await noScript.newPage();
        await plain.setContent(html);
        assert.equal(await plain.locator('#codes tbody tr:visible').count(), 3);
        assert.equal(await plain.locator('.table-sort').count(), 0);
        await noScript.close();
        console.log('PASS: typed sorting, default order, paging, keyboard control, row actions, and form isolation');
    } finally {
        if (browser) await browser.close();
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
