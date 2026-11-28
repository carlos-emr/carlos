/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { launchBrowser } = require('./lib/playwright-harness');
const { runLegacyCheck } = require('./lib/legacy-check');

function staffPanel() {
  // Exercise the shipped staff controls with synthetic servlet values and an in-memory API.
  const jsp = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingONPortalPaymentRequest.jsp'), 'utf8');
  return jsp.slice(jsp.indexOf('<section'))
    .replace(/data-endpoint="[^"]+"/, 'data-endpoint="/payment"')
    .replace('<%= portalPaymentPatient %>', '123').replace('<%= portalPaymentBilling %>', '55')
    .replace('<%= portalPaymentMayWrite %>', 'true')
    .replace(/<carlos:encode[^\n]+?\/>/, '2026-12-01').replace(/<%[^\n]*?%>/g, '');
}

async function run({ throwIfCancelled }) {
  const panel = staffPanel();
  const browser = await launchBrowser({ chromePath: process.env.CHROME_PATH });
  try {
    const page = await browser.newPage();
    const origin = 'http://127.0.0.1';
    const calls = [];
    let item;
    await page.route(origin + '/invoice', route => route.fulfill({contentType: 'text/html',
      body: '<input name="CSRF-TOKEN" value=""><script>document.addEventListener("DOMContentLoaded", () => {' +
        'window.csrfTokenReady = new Promise(resolve => setTimeout(() => {' +
        'document.querySelector("input").value = "FAKE-token"; resolve(); }, 20)); });</script>' + panel}));
    await page.route(origin + '/payment', async route => {
      const request = route.request();
      const fields = Object.fromEntries(new URLSearchParams(request.postData()));
      calls.push({method: request.method(), token: request.headers()['csrf-token'], fields});
      if (fields.method === 'create') {
        item = {id: 7, status: 'open', category: fields.category, amountCents: 4500, dueOn: fields.dueOn};
      } else if (fields.method === 'withdraw') {
        item.status = 'withdrawn';
      }
      await route.fulfill({json: {ok: true, created: true, requests: item ? [item] : []}});
    });
    await page.goto(origin + '/invoice');
    const send = page.getByRole('button', {name: 'Send payment request'});
    await page.waitForFunction(() => document.querySelector('[name=CSRF-TOKEN]').value !== '');
    await page.locator('[data-role=category]').selectOption('form_or_note');
    await page.locator('[data-role=dueOn]').fill('2026-12-01');
    await throwIfCancelled();
    await send.click();
    const row = page.getByRole('row').filter({hasText: 'A form or note'});
    await row.getByText('$45.00', {exact: true}).waitFor();
    await throwIfCancelled();
    await row.getByRole('button', {name: 'Withdraw'}).click();
    await row.getByText('Withdrawn', {exact: true}).waitFor();
    assert.equal(await row.getByRole('button').count(), 0);
    assert.deepEqual(calls.map(call => call.fields.method), ['list', 'create', 'list', 'withdraw', 'list']);
    for (const call of calls) {
      assert.equal(call.method, 'POST');
      assert.equal(call.token, 'FAKE-token');
      assert.equal(call.fields.demographicNo, '123');
      assert.equal(call.fields.billingNo, '55');
    }
    assert.equal(calls[3].fields.requestId, '7');
    console.log('1 staff payment request journey passed');
  } finally {
    await browser.close();
  }
}
runLegacyCheck('portal-payment-request', run);
