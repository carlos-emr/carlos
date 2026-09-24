#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Integration check of the installed Rx page and its real DrugRef endpoint.
// INACTIVE_DRUG_DIN and INACTIVE_DRUG_DATE must describe a known inactive entry
// in the deployed reference dataset. This does not create or save a prescription.
// A temporary status element exercises the page's shipped callback; intercepted
// responses below test the browser failure display, not a DrugRef outage.
// The final step selects the same product through the real Rx search, reading its
// search-index row from DRUGREF_TEST_DATABASE (default drugref2); it only adds the
// drug to the unsaved Rx editor and asserts that no prescription was persisted.
const { assert, assertNotErrorPage, SkipCheck, sqlString } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

function isCalendarDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000-')) return false;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return Number.isFinite(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value;
}

async function workflow(s) {
  const din = process.env.INACTIVE_DRUG_DIN;
  const date = process.env.INACTIVE_DRUG_DATE;
  if (!din || !date) {
    throw new SkipCheck('Set INACTIVE_DRUG_DIN and INACTIVE_DRUG_DATE from the installed reference dataset');
  }
  assert(/^\d{8}$/.test(din) && isCalendarDate(date),
    'Set INACTIVE_DRUG_DIN and INACTIVE_DRUG_DATE from the installed reference dataset');
  const page = await s.context.newPage();
  await page.goto(`${s.config.baseUrl}/rx/choosePatient?demographicNo=${s.patient}`);
  await assertNotErrorPage(page, 'Rx profile');
  await page.waitForFunction(() => typeof checkIfInactive === 'function');
  const id = s.marker;
  await page.evaluate(value => {
    const status = document.createElement('span');
    status.id = `inactive_${value}`;
    document.body.appendChild(status);
  }, id);
  const target = page.locator(`#inactive_${id}`);
  const invoke = () => page.evaluate(({ id, din }) => checkIfInactive(id, din), { id, din });
  await s.step('real inactive date survives DrugRef XML-RPC and CARLOS JSON serialization', async () => {
    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/rx/searchDrug')
        && new URLSearchParams(r.request().postData() || '').get('method') === 'inactiveDate'),
      invoke(),
    ]);
    assert(response.status() === 200, `Inactive lookup returned HTTP ${response.status()}`);
    assert((response.headers()['content-type'] || '').includes('application/json'), 'Lookup did not return JSON');
    const result = await response.json();
    assert(result.checked === true && result.inactiveDate === date, 'Inactive lookup lost its checked calendar date');
    await target.filter({ hasText: `Inactive Drug Since: ${date}` }).waitFor();
  });
  const pattern = '**/rx/searchDrug';
  for (const body of ['{}', '{"checked":false}', '{"checked":true,"inactiveDate":"2026-02-31"}', '<html>failure</html>']) {
    await s.step(`unusable response is visibly unchecked: ${body}`, async () => {
      const handler = route => new URLSearchParams(route.request().postData() || '').get('method') === 'inactiveDate'
        ? route.fulfill({ status: 200, contentType: 'application/json', body }) : route.continue();
      await page.route(pattern, handler);
      try {
        await invoke();
        await target.filter({ hasText: 'Drug status could not be checked' }).waitFor();
      } finally { await page.unroute(pattern, handler); }
    });
  }
  await s.step('a subsequent real lookup recovers after failed responses', async () => {
    await invoke();
    await target.filter({ hasText: `Inactive Drug Since: ${date}` }).waitFor();
  });
  await s.step('selecting the inactive product from Rx search displays its date on the new row', async () => {
    const database = process.env.DRUGREF_TEST_DATABASE || 'drugref2';
    assert(/^[A-Za-z0-9_]+$/.test(database), 'Invalid DRUGREF_TEST_DATABASE');
    const rows = s.sql.rows(`SELECT ds.id,ds.name,DATE(ip.history_date) FROM ${database}.cd_inactive_products ip
      JOIN ${database}.cd_drug_search ds ON ds.drug_code=CAST(ip.drug_code AS CHAR)
      WHERE ip.drug_identification_number=${sqlString(din)} AND ds.category=13 ORDER BY ds.id LIMIT 1`);
    assert(rows.length === 1, 'INACTIVE_DRUG_DIN has no brand-name row in the DrugRef search index');
    const [searchId, searchName, storedDate] = rows[0];
    assert(storedDate === date, 'INACTIVE_DRUG_DATE does not match the DrugRef inactive-product date');
    s.cleanup(() => {
      assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`) === '0',
        'Selecting the temporary Rx drug unexpectedly persisted a prescription; retain the fixture for investigation');
    });
    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/rx/searchDrug')
        && new URLSearchParams(r.request().postData() || '').get('query') === searchName.toUpperCase()),
      page.locator('#searchString').pressSequentially(searchName, { delay: 30 }),
    ]);
    assert(response.ok(), 'Inactive product search failed');
    const index = (await response.json()).results.findIndex(item => String(item.id) === searchId);
    assert(index >= 0, 'The inactive product is missing from the rendered search result payload');
    const [status] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/rx/searchDrug')
        && new URLSearchParams(r.request().postData() || '').get('method') === 'inactiveDate'
        && new URLSearchParams(r.request().postData() || '').get('id') !== id),
      page.locator('ul.ui-autocomplete li.ui-menu-item').nth(index).click(),
    ]);
    assert(new URLSearchParams(status.request().postData()).get('din') === din, 'The selected drug does not match the fixture DIN');
    await page.locator('[id^="inactive_"]').filter({ hasText: `Inactive Drug Since: ${date}` })
      .and(page.locator(`:not(#inactive_${id})`)).first().waitFor();
  });
}
if (require.main === module) runWorkflow('inactive-drug-status', workflow);
module.exports = { workflow };
