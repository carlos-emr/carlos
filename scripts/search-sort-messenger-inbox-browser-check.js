#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const ui = require('./lib/playwright-ui');
const { sortHeader } = require('./search-sort-messenger-inbox-playwright-checks');

// DisplayMessages.jsp renders box tabs before the table. Tabs retain orderby,
// while header links toggle its ! prefix. Reproduce that collision in a real DOM.
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'],
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  try {
    const page = await browser.newPage();
    const columns = ['subject', 'from', 'status', 'date', 'linked'];
    await page.route('http://127.0.0.1/messenger/DisplayMessages?*', route => {
      const order = new URL(route.request().url()).searchParams.get('orderby') || '';
      // All interpolated fields come from this fixture's fixed column list.
      assert.ok(order === '' || columns.includes(order.replace(/^!/, '')));
      const tabs = [0, 1, 2].map(box => `<a data-kind="box" href="?boxType=${box}&amp;orderby=${order}">Box ${box}</a>`).join('');
      const headers = columns.map(column => `<th><a href="?orderby=${order === column ? '!' : ''}${column}&amp;boxType=0">${column}</a></th>`).join('');
      return route.fulfill({ contentType: 'text/html', body: `<!doctype html>
        ${tabs}<table class="table-striped"><thead><tr>${headers}</tr></thead><tbody></tbody></table>
        <a title="next page" href="?boxType=0&amp;orderby=${order}&amp;offset=25">Next</a>` });
    });
    await page.goto('http://127.0.0.1/messenger/DisplayMessages?boxType=0');
    for (const column of columns) {
      for (const direction of ['ascending', 'descending']) {
        if (direction === 'descending') {
          const old = page.locator(`a[href*="orderby=${column}"]`).first();
          assert.equal(await old.getAttribute('data-kind'), 'box', 'Negative control: the old selector clicks a box tab');
        }
        const header = sortHeader(page, column, direction);
        assert.equal(await header.count(), 1, `${column} ${direction} must identify exactly one header`);
        await ui.clickAndAwaitReload(page, header, { timeout: 5000, label: `${column} ${direction}` });
        const current = new URL(page.url());
        assert.equal(current.searchParams.get('orderby'), `${direction === 'descending' ? '!' : ''}${column}`);
        assert.equal(current.searchParams.get('boxType'), '0');
        assert.equal(current.searchParams.has('offset'), false, 'A sort header returns to page one');
        await ui.clickAndAwaitReload(page, page.locator('a[title="next page"]'), { timeout: 5000, label: 'page two' });
        assert.equal(new URL(page.url()).searchParams.get('offset'), '25');
      }
    }
    console.log('PASS Messenger inbox: all five real header locators toggle both ways from page two; box tabs excluded');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
