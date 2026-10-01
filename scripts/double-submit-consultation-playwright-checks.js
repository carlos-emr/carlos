#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Consultation request Submit (a duplicate referral is clinically significant).
 *
 * User path: Schedule > Search > Master Record > E-Chart > Consultations "+" > pick service and consultant
 * (autocompletes) > reason > Submit Consultation Request. For each rapid activation (dblclick(), two
 * back-to-back clicks, slow-response re-click; the Submit is a type=button so Enter submits nothing)
 * the check files ONE request with its own marker reason and asserts EXACTLY ONE consultationRequests
 * row for the owned patient and that reason. Nothing is faxed or e-mailed (Save-only button).
 *
 * Fixtures: the owned FAKE- patient; cleanup deletes this patient's consultationRequests rows (and their
 * attachment rows) carrying the marker and asserts they are gone. Wave-6 sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer } = require('./lib/double-submit-helpers');

async function pickFirst(page, selector) {
  await page.locator(selector).click();
  const item = page.locator('ul.ui-autocomplete:visible li.ui-menu-item').first();
  await item.waitFor({ state: 'visible', timeout: 15000 });
  await item.click();
  await page.waitForTimeout(250);
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const like = h.sqlString(`${marker}-%`);
  const ids = () => sql.rows(`SELECT requestId FROM consultationRequests WHERE demographicNo=${patient} AND reason LIKE ${like}`).map(([id]) => id);
  s.cleanup(() => {
    const list = ids();
    for (const id of list) h.assert(/^[1-9]\d*$/.test(id), 'Owned request id is invalid');
    if (list.length) {
      const csv = list.join(',');
      for (const table of ['consultdocs']) {
        try { sql.execute(`DELETE FROM ${table} WHERE requestId IN (${csv})`); } catch (error) { /* absent */ }
      }
      sql.execute(`DELETE FROM consultationRequests WHERE requestId IN (${csv}) AND demographicNo=${patient}`);
    }
    // The consultation form files one stamp row per submit for the patient (FK to demographic).
    sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
    h.assert(ids().length === 0, 'Owned consultation requests were not removed');
  });
  const chart = await s.chart();
  const v = verdicts('consultation-submit');

  for (const mode of MODES.filter((m) => m.key !== 'doubleEnter')) {
    await s.step(`Submit Consultation Request via ${mode.label} files exactly one request`, async () => {
      const form = await s.popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first(), 'consult-form');
      await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: 30000 });
      await pickFirst(form, '#serviceInput');
      await pickFirst(form, '#specialistInput');
      await form.locator('#urgency').selectOption('1');
      const reason = `${marker}-${mode.tag} double submit`;
      await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
      const route = /\/encounter\/RequestConsultation$/;
      const posts = watchPosts(form.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      await rapid(mode.key, form.locator('input[name="submitSaveOnly"]'));
      const count = await settledCount(sql, `SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient}
        AND reason=${h.sqlString(reason)}`, { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${posts.seen.length} RequestConsultation POST(s))`);
      v.record(mode.label, count, { exactly: 1 });
      if (!form.isClosed()) await form.close().catch(() => {});
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-consultation', workflow, { openPatient: true });
