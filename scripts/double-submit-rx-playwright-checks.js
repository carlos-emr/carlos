#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Prescription Save (Rx module "Save" button; a duplicate prescription is
 * clinically significant).
 *
 * User path: Schedule > Search > Master Record > Prescriptions > drug search > staged card > Save
 * (#saveOnlyButton, rx/WriteScript updateSaveAllDrugs). For each rapid activation (dblclick(), two
 * back-to-back clicks, slow-response re-click; the instructions box is a textarea so there is no Enter
 * mode) the check stages ONE drug and asserts the owned patient's `drugs` table gains EXACTLY ONE row
 * (and one prescription row). Nothing is printed, faxed or sent (Save only).
 *
 * Fixtures: the owned FAKE- patient; between modes and in cleanup the patient's drugs, prescription,
 * drugReason, partial_date, DigitalSignature and chart-note rows are deleted; cleanup asserts they are
 * gone. Needs DrugRef (RX_EDIT_DRUG_TERM / RX_EDIT_DRUG_NAME, default LIPITOR 20 / LIPITOR 20MG).
 * Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep, recorderMark, forgiveAbortedSecondRequest } = require('./lib/double-submit-helpers');

const DRUG_TERM = process.env.RX_EDIT_DRUG_TERM || 'LIPITOR 20';
const DRUG_NAME = process.env.RX_EDIT_DRUG_NAME || 'LIPITOR 20MG';
const INSTRUCTIONS = '1 tab PO BID x 14 days';
const isPost = (route) => (response) => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);

async function stage(rx) {
  const before = await rx.locator('[id^="drugName_"]').count();
  await Promise.all([
    rx.waitForResponse((r) => h.pathOnly(r.url()).endsWith('/rx/searchDrug') && r.request().method() === 'POST', { timeout: 60000 }),
    rx.locator('#searchString').pressSequentially(DRUG_TERM, { delay: 60 }),
  ]);
  const option = rx.locator('ul.ui-autocomplete li.ui-menu-item').filter({ hasText: DRUG_NAME }).first();
  await option.waitFor({ state: 'visible' });
  const [staged] = await Promise.all([
    rx.waitForResponse((r) => isPost('/rx/WriteScript')(r) && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'createNewRx'),
    option.click(),
  ]);
  await rx.locator('[id^="drugName_"]').nth(before).waitFor({ state: 'attached' });
  return new URLSearchParams(staged.request().postData()).get('randomId');
}

async function workflow(s) {
  const { sql, patient } = s;
  const wipe = () => {
    const drugIds = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map((row) => row[0]);
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map((row) => row[0]);
    h.assert([...drugIds, ...notes].every((id) => /^[1-9]\d*$/.test(id)), 'Owned Rx row id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      ${drugIds.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugIds.join(',')});` : ''}
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
  };
  s.cleanup(() => {
    wipe();
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  const v = verdicts('rx-save');

  for (const mode of MODES.filter((m) => m.key !== 'doubleEnter')) {
    await s.step(`Rx Save via ${mode.label} writes one drug`, async () => {
      wipe();
      const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
      await rx.locator('#searchString').waitFor({ state: 'visible' });
      await rx.waitForLoadState('networkidle');
      const card = await stage(rx);
      await rx.locator(`#instructions_${card}`).fill(INSTRUCTIONS);
      await Promise.all([rx.waitForResponse(isPost('/rx/UpdateScript')), rx.locator(`label[for="jsonDxSearch_${card}"]`).click()]);
      const route = /\/rx\/WriteScript\?[^#]*updateSaveAllDrugs/;
      const posts = watchPosts(rx.context(), /\/rx\/WriteScript$/);
      const since = recorderMark(s.recorder);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      const before = posts.seen.length;
      await rapid(mode.key, rx.locator('#saveOnlyButton'));
      const count = await settledCount(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      const scripts = sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient}`);
      await sleep(300);
      forgiveAbortedSecondRequest(s.recorder, since, /\/rx\/WriteScript/);
      console.log(`    (${posts.seen.length - before} WriteScript POST(s) after the save click; ${scripts} prescription row(s))`);
      v.record(mode.label, count, { exactly: 1 });
      // One save writes one prescription header for its drug; zero or several is a duplicate or lost write.
      v.record(`${mode.label} (prescription rows)`, Number(scripts), { exactly: 1 });
      if (!rx.isClosed()) await rx.close().catch(() => {});
    });
  }
  wipe();
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-rx', workflow, { openPatient: true });
