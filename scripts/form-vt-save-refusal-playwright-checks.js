#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4674, the Vascular Tracker (VT) save. The validation result was overwritten by each later check, so an
// invalid value was saved whenever the visit date was valid; a refused save then redirected to a fresh form that had
// lost both the typed values and the reason; and Save & Exit committed its measurements before the form row.
//
// User path: the VT registration is hidden by default, so the form is opened the way the chart's form list opens it,
// by its Setup route (/form/SetupForm?formName=VTForm&demographic_no=N&formId=0). Blood pressure 120/80 and an
// out-of-range weight (999 kg; the form allows 0 to 300) are typed and Update VT is pressed: the same form comes back
// with the reason listed and both values still in their boxes, and nothing is stored. The weight is corrected and
// Save & Exit is pressed and confirmed: one VT record and the two measurements are stored.
//
// Fixtures: the owned synthetic patient; the VT record and measurements Save & Exit stores. Nothing is added to the
// Forms menu, so this check does not need EXCLUSIVE=1. Cleanup deletes the patient's formVTForm and measurements rows
// and asserts them gone.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const NAME = 'form-vt-save-refusal';

function count(sql, table, column, patient) {
  return Number(sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${column}=${patient}`));
}

async function workflow(s) {
  const { sql, patient, context } = s;
  h.assert(/^[1-9]\d*$/.test(patient), 'The owned synthetic patient was not created');
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient}; DELETE FROM formVTForm WHERE demographic_no=${patient}`);
    h.assert(count(sql, 'measurements', 'demographicNo', patient) === 0, "The owned patient's measurements were not removed");
    h.assert(count(sql, 'formVTForm', 'demographic_no', patient) === 0, "The owned patient's VT records were not removed");
  });

  const page = await context.newPage();
  try {
    await s.step('Vascular Tracker: a new form opens for the owned patient', async () => {
      const response = await h.gotoApp(page, s.config.baseUrl, `/form/SetupForm?formName=VTForm&demographic_no=${patient}&formId=0`);
      h.assert(response && response.status() === 200, `The new VT form answered HTTP ${response && response.status()}`);
      await h.assertNotErrorPage(page, 'Vascular Tracker');
      await page.locator('#vt-WT-value').waitFor({ state: 'visible', timeout: 20000 });
    });

    await s.step('Vascular Tracker: an out-of-range weight is refused with its reason and the typed values kept', async () => {
      await page.locator('#vt-BP-value').fill('120/80');
      await page.locator('#vt-WT-value').fill('999');
      await Promise.all([
        page.waitForURL(url => /\/form\/SubmitForm$/.test(url.pathname), { waitUntil: 'load' }),
        page.getByRole('button', { name: 'Update VT', exact: true }).click(),
      ]);
      await h.assertNotErrorPage(page, 'refused Vascular Tracker save');
      const reasons = await page.locator('.action-errors li').allInnerTexts();
      h.assert(reasons.length > 0, 'The refused form lists no reason');
      h.assert(reasons.some(reason => /Weight/.test(reason) && /0/.test(reason) && /300/.test(reason)),
        `No reason names the weight and its 0 to 300 range: ${reasons.join(' | ')}`);
      h.assert(!reasons.join(' ').includes('<li>'), 'A reason prints the bundle\'s list markup as text');
      h.assert(await page.locator('#vt-WT-value').inputValue() === '999', 'The refused form lost the typed weight');
      h.assert(await page.locator('#vt-BP-value').inputValue() === '120/80', 'The refused form lost the typed blood pressure');
      h.assert(count(sql, 'formVTForm', 'demographic_no', patient) === 0, 'A refused save stored a VT record');
      h.assert(count(sql, 'measurements', 'demographicNo', patient) === 0, 'A refused save stored measurements');
    });

    await s.step('Vascular Tracker: Save & Exit after the correction stores the record and both measurements', async () => {
      await page.locator('#vt-WT-value').fill('75');
      const saved = page.waitForResponse(answer => /\/form\/SubmitForm$/.test(new URL(answer.url()).pathname)
        && answer.request().method() === 'POST');
      await h.withExpectedDialogs(page, () => page.getByRole('button', { name: 'Save and Exit', exact: true }).click({ noWaitAfter: true }),
        { accept: true });
      const answer = await saved;
      h.assert(answer.status() === 200, `Save & Exit answered HTTP ${answer.status()}`);
      const deadline = Date.now() + 10000;
      while (count(sql, 'formVTForm', 'demographic_no', patient) === 0 && Date.now() < deadline) {
        await new Promise(resolve => setTimeout(resolve, 200));
      }
      h.assert(count(sql, 'formVTForm', 'demographic_no', patient) === 1, 'Save & Exit did not store exactly one VT record');
      const stored = sql.rows(`SELECT type, dataField FROM measurements WHERE demographicNo=${patient} ORDER BY type`);
      h.assert(JSON.stringify(stored) === JSON.stringify([['BP', '120/80'], ['WT', '75']]),
        `Save & Exit stored measurements ${JSON.stringify(stored)}, not BP 120/80 and WT 75`);
    });
  } finally {
    await page.close().catch(() => {});
  }
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow };
