#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Real DrugRef lookup plus an explicit malformed-response UI fault injection.
// Selects a drug into the temporary Rx editor; never signs or saves a prescription.
const {assert, sqlString} = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
async function workflow(s) {
  const database = process.env.DRUGREF_TEST_DATABASE || 'drugref2';
  const din = process.env.INACTIVE_DRUG_DIN || '02245547';
  assert(/^[A-Za-z0-9_]+$/.test(database) && /^\d{8}$/.test(din), 'Invalid DrugRef fixture configuration');
  const rows = s.sql.rows(`SELECT ds.id,ds.name,DATE(ip.history_date) FROM ${database}.cd_inactive_products ip
    JOIN ${database}.cd_drug_search ds ON ds.drug_code=CAST(ip.drug_code AS CHAR)
    WHERE ip.drug_identification_number=${sqlString(din)} AND ds.category=13 ORDER BY ds.id LIMIT 1`);
  assert(rows.length === 1 && /^\d{4}-\d{2}-\d{2}$/.test(rows[0][2]), 'Inactive product fixture is missing from the DrugRef search data');
  const [searchId, searchName, inactiveDate] = rows[0];
  s.cleanup(() => {
    assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`) === '0',
      'Selecting the temporary Rx drug unexpectedly persisted a prescription; retain the fixture for investigation');
  });
  const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'inactive-drug-rx');
  let warning;
  let rowId;
  await s.step('selecting a real inactive product displays its database calendar date', async () => {
    const [response] = await Promise.all([
      rx.waitForResponse(r => r.url().includes('/rx/searchDrug')
        && new URLSearchParams(r.request().postData() || '').get('query') === searchName.toUpperCase()),
      rx.locator('#searchString').pressSequentially(searchName, {delay: 30}),
    ]);
    assert(response.ok(), 'Inactive product search failed');
    const payload = await response.json();
    const index = payload.results.findIndex(item => String(item.id) === searchId);
    assert(index >= 0, 'The actual inactive product is missing from the rendered search result payload');
    const [status] = await Promise.all([
      rx.waitForResponse(r => r.url().includes('/rx/searchDrug')
        && new URLSearchParams(r.request().postData() || '').get('method') === 'inactiveDate'),
      rx.locator('ul.ui-autocomplete li.ui-menu-item').nth(index).click(),
    ]);
    assert(new URLSearchParams(status.request().postData()).get('din') === din, 'The selected drug does not match the fixture DIN');
    const result = await status.json();
    assert(status.status() === 200 && result.checked === true && result.inactiveDate === inactiveDate,
      'Inactive date did not survive the database/XML-RPC/JSON round trip');
    warning = rx.locator('[id^="inactive_"]').filter({hasText: `Inactive Drug Since: ${inactiveDate}`}).first();
    await warning.waitFor({state: 'visible'});
    rowId = (await warning.getAttribute('id')).slice('inactive_'.length);
  });
  await s.step('an empty success-looking response displays an unavailable warning and recovers', async () => {
    const pattern = '**/rx/searchDrug';
    const intercept = async route => {
      if (new URLSearchParams(route.request().postData() || '').get('method') === 'inactiveDate') {
        await route.fulfill({status: 200, contentType: 'application/json', body: '{}'});
      } else await route.continue();
    };
    await s.context.route(pattern, intercept);
    try {
      // Component fault injection into the row produced by the actual selection.
      await rx.evaluate(({id, din}) => window.checkIfInactive(id, din), {id: rowId, din});
      await rx.waitForFunction(id => document.getElementById('inactive_' + id)?.textContent.includes('could not be checked'), rowId);
    } finally { await s.context.unroute(pattern, intercept); }
    await rx.evaluate(({id, din}) => window.checkIfInactive(id, din), {id: rowId, din});
    await rx.waitForFunction(({id, date}) => document.getElementById('inactive_' + id)?.textContent === 'Inactive Drug Since: ' + date,
      {id: rowId, date: inactiveDate});
  });
}
if (require.main === module) runWorkflow('inactive-drug-status', workflow);
module.exports = {workflow};
