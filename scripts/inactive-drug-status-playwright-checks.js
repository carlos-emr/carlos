#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Integration check of the installed Rx page and its real DrugRef endpoint.
// INACTIVE_DRUG_DIN and INACTIVE_DRUG_DATE must describe a known inactive entry
// in the deployed reference dataset. This does not create or save a prescription.
// A temporary status element exercises the page's shipped callback; intercepted
// responses below test the browser failure display, not a DrugRef outage.
const { assert, assertNotErrorPage } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const din = process.env.INACTIVE_DRUG_DIN;
  const date = process.env.INACTIVE_DRUG_DATE;
  assert(/^\d{8}$/.test(din || '') && /^\d{4}-\d{2}-\d{2}$/.test(date || ''),
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
}
if (require.main === module) runWorkflow('inactive-drug-status', workflow);
module.exports = { workflow };
