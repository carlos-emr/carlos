#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5: populated history, actual graph bytes, selected-row delete.
const { assert, sqlString } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient}`));
  const ids = [];
  for (const [date, value] of [['2026-01-02','61.2'],['2026-02-03','62.4']]) {
    ids.push(sql.value(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
      VALUES('WT',${patient},${sqlString(provider)},${sqlString(value)},'in kg',${sqlString(marker)},${sqlString(date)},NOW()); SELECT LAST_INSERT_ID()`));
  }
  const chart = await s.chart();
  const index = await s.popup(chart, chart.getByRole('link', { name: 'Measurements', exact: true }).first(), 'measurement-history-index');
  let history;
  await s.step('history displays both dated values', async () => {
    history = await s.popup(index, index.locator('a[onclick*="SetupDisplayHistory?type=WT"]').first(), 'measurement-history');
    for (const [id, value] of ids.map((id,i)=>[id,['61.2','62.4'][i]])) {
      const row = history.locator('tr.data').filter({ has: history.locator(`input[name="deleteCheckbox"][value="${id}"]`) });
      assert((await row.innerText()).includes(value), 'Measurement history lost a recorded value');
    }
  });
  await s.step('delete removes only the selected measurement and refreshes history', async () => {
    await history.locator(`input[name="deleteCheckbox"][value="${ids[0]}"]`).check();
    await clickAndAwaitReload(history, history.locator('input[onclick="submit();"]'));
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${ids[0]} AND demographicNo=${patient}`, '0',
      'Selected measurement was not deleted');
    assert(sql.value(`SELECT dataField FROM measurements WHERE id=${ids[1]} AND demographicNo=${patient}`) === '62.4',
      'Delete changed the unselected measurement');
    assert(await history.locator(`input[value="${ids[0]}"][name="deleteCheckbox"]`).count() === 0,
      'Deleted measurement remains in the history');
    await history.locator(`input[value="${ids[1]}"][name="deleteCheckbox"]`).waitFor({ state: 'visible' });
  });
  await s.step('the Plot control loads an image with real PNG bytes', async () => {
    // global.js reuses a named window which may already belong to another
    // measurements page. Observe the actual UI-generated image response rather
    // than assuming a new popup or navigating to a guessed endpoint.
    const rendered = s.context.waitForEvent('response', {
      predicate: response => new URL(response.url()).pathname.endsWith('/GraphMeasurements')
        && response.request().isNavigationRequest(), timeout: 20000,
    });
    rendered.catch(() => {});
    await history.locator('input[onclick*="GraphMeasurements"]').click();
    const response = await rendered;
    const url = new URL(response.url());
    assert(url.origin === new URL(s.config.baseUrl).origin, 'Measurement graph points outside the application');
    assert(response.status() === 200, 'Graph image request failed');
    assert((response.headers()['content-type'] || '').startsWith('image/png'), 'Graph did not declare PNG content');
    const body = await response.body();
    assert(body.length > 100 && body.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])),
      'Graph returned an error document or invalid PNG instead of plot bytes');

  });
}
if (require.main === module) runWorkflow('measurement-history', workflow);
module.exports = { workflow };
