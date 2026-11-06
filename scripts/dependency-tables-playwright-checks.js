/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const { getLaunchOptions } = require('./lib/playwright-harness');

async function main() {
    const script = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/carlos-tables.js'));
    const rows = [[12, '100'], [2, '00123'], [100, '02']].map(([number, code]) =>
        `<tr><td data-sort-value="${number}" data-sort-type="number">${number}</td>` +
        `<td data-sort-value="${code}" data-sort-type="text">${code}</td>` +
        `<td><input type="checkbox" name="records" value="${number}"><input type="radio" name="head" value="${number}">` +
        `<button type="button" class="edit" data-id="${number}">Edit</button></td></tr>`).join('');
    const html = `<!doctype html><html><body>
        <table id="no-body" data-carlos-table><thead><tr><th>Header only</th></tr></thead></table>
        <table id="no-head" data-carlos-table><tbody><tr><td>Body only</td></tr></tbody></table>
        <table id="no-header-row" data-carlos-table><thead></thead><tbody></tbody></table>
        <form id="fixture"><table id="codes" data-carlos-table
        data-page-size="2" data-default-sort="1" data-default-order="descending">
        <thead><tr><th><span data-table-sort>Number</span></th>
        <th><span data-table-sort>Code</span></th><th>Options</th></tr></thead>
        <tbody>${rows}</tbody></table></form>
        <table id="participation" data-carlos-table data-default-sort="1">
          <thead><tr><th><span data-table-sort>Participation</span></th></tr></thead>
          <tbody><tr><td data-sort-value="10" data-sort-type="number">10/20 (0 waiting)</td></tr>
          <tr><td data-sort-value="9" data-sort-type="number">9/20 (0 waiting)</td></tr>
          <tr><td data-sort-value="2" data-sort-type="number">2/20 (0 waiting)</td></tr></tbody>
        </table>
        <table id="prices" data-carlos-table data-default-sort="1">
          <thead><tr><th><span data-table-sort>Price</span></th></tr></thead>
          <tbody><tr><td data-sort-value="10.00" data-sort-type="number">10.00</td></tr>
          <tr><td data-sort-value="2.00" data-sort-type="number">2.00</td></tr>
          <tr><td data-sort-value="0.50" data-sort-type="number">0.50</td></tr></tbody>
        </table></body></html>`;
    let browser;
    try {
        browser = await chromium.launch(getLaunchOptions(
            process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.CARLOS_SMOKE_CHROMIUM));
        const page = await browser.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        // html contains only literal markup and the fixed synthetic rows above, with no external input or URLs.
        // nosemgrep: javascript.playwright.security.audit.playwright-setcontent-injection.playwright-setcontent-injection
        await page.setContent(html);
        await page.addScriptTag({content: script.toString('utf8')});
        assert.deepEqual(await page.locator('#no-body, #no-head, #no-header-row')
            .evaluateAll(tables => tables.map(table => table.dataset.tableReady)), [undefined, undefined, undefined]);
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
        assert.deepEqual(await page.locator('#participation tbody td').allTextContents(),
            ['2/20 (0 waiting)', '9/20 (0 waiting)', '10/20 (0 waiting)']);
        assert.deepEqual(await page.locator('#prices tbody td').allTextContents(), ['0.50', '2.00', '10.00']);
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
        // Reuse the same static fixture to verify readability with JavaScript disabled; no user data enters html.
        // nosemgrep: javascript.playwright.security.audit.playwright-setcontent-injection.playwright-setcontent-injection
        await plain.setContent(html);
        assert.equal(await plain.locator('#codes tbody tr:visible').count(), 3);
        assert.equal(await plain.locator('.table-sort').count(), 0);
        await noScript.close();
        await checkRegistryResults(browser);
        console.log('PASS: typed sorting, paging, keyboard control, form isolation, and registry result filtering');
    } finally {
        if (browser) await browser.close();
    }
}

async function checkRegistryResults(browser) {
    const webapp = path.join(__dirname, '../src/main/webapp');
    const jsp = fs.readFileSync(path.join(webapp, 'WEB-INF/jsp/oscarReport/oscarReportDxReg.jsp'), 'utf8');
    const opening = jsp.match(/<table\b[^>]*\bid="listview"[^>]*>/)?.[0];
    assert.ok(opening, 'registry results table is present');
    const headers = jsp.slice(jsp.indexOf(opening) + opening.length).match(/<thead>[\s\S]*?<\/thead>/)?.[0];
    const initializer = jsp.match(/\$\('#listview'\)\.DataTable\(\{[\s\S]*?\}\);/)?.[0];
    assert.ok(headers && initializer, 'registry results have headers and their DataTables initializer');
    const columns = headers.match(/<th\b/g).length;
    const rows = Array.from({length: 12}, (_, index) => {
        const cells = Array(columns).fill('Synthetic');
        cells[0] = `Fixture${String(index + 1).padStart(2, '0')}`;
        cells[1] = index === 0 || index === 11 ? 'SyntheticSelected' : 'SyntheticOther';
        return `<tr>${cells.map(value => `<td>${value}</td>`).join('')}</tr>`;
    }).join('');
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    // opening and headers come only from the checked-in JSP; rows contain the fixed synthetic strings above.
    // No request, environment value, or external response enters this fixture or supplies a resource URL.
    // nosemgrep: javascript.playwright.security.audit.playwright-setcontent-injection.playwright-setcontent-injection
    await page.setContent(`<!doctype html><html><body>${opening}${headers}<tbody>${rows}</tbody></table></body></html>`);
    for (const file of ['library/jquery/jquery-3.7.1.min.js',
        'library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js',
        'library/DataTables/Responsive-2.4.1/js/dataTables.responsive.min.js', 'js/carlos-tables.js']) {
        await page.addScriptTag({path: path.join(webapp, file)});
    }
    await page.addScriptTag({content: initializer});
    const firstNames = () => page.locator('#listview tbody tr td:first-child').allTextContents();
    assert.equal(await page.locator('#listview_wrapper').count(), 1);
    assert.equal(await page.locator('#listview').getAttribute('data-table-ready'), null);
    assert.equal(await page.locator('#listview .table-sort').count(), 0);
    assert.equal(await page.locator('#listview tbody tr:visible').count(), 12);
    await page.locator('#listview_filter input').fill('SyntheticSelected');
    assert.deepEqual(await firstNames(), ['Fixture01', 'Fixture12']);
    await page.locator('#listview th').first().click();
    assert.deepEqual(await firstNames(), ['Fixture12', 'Fixture01']);
    await page.locator('#listview_filter input').fill('');
    assert.equal(await page.locator('#listview tbody tr:visible').count(), 12);
    assert.deepEqual(errors, []);
    await page.close();
}
main().catch(error => { console.error(error); process.exitCode = 1; });
