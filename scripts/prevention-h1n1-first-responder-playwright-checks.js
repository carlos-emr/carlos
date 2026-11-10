#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 4: the pandemic-flu (H1N1) form's Household Contact, First Responder Police and First Responder
 * Fire boxes.
 * User path: E-Chart ▸ Preventions ▸ H1N1 ▸ Completed, date, lot, comment, Pregnant, Chronic Condition Diabetes,
 * Health Care Worker Laboratory and Household Contact ▸ Save; the record ▸ each of the cases below ▸ Save. LAST, the
 * record ▸ Delete, then a record saved before the police and fire boxes were split (one "firstresponder" value) ▸
 * reopened ▸ Save.
 * Asserts, for each case (household alone, police alone, fire alone, all three, none): each ticked box is stored as
 * "true" and each clear box has no row (no stale value survives clearing); the archived record keeps its own boxes;
 * the record reopens with exactly those boxes ticked; and the pregnancy, chronic-condition and healthcare-worker values,
 * the lot and the comment are never changed. A legacy record's single first-responder value reopens as both boxes
 * ticked, and saving it stores both.
 * Fixtures: the runWorkflow FAKE- patient; the legacy record is seeded by SQL as an owned fixture; its preventions
 * and their ext rows are removed in cleanup.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitClose } = require('./lib/prevention-coverage');
const { GIVEN_ON, flagWorkflow } = require('./lib/prevention-h1n1-options');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const UNRELATED = Object.freeze({ pregnant: 'true', chronic: 'diabetes', healthcareworker: 'laboratory' });

async function workflow(s) {
  const { forms } = await flagWorkflow(s, {
    flags: ['householdcontact', 'firstresponderpolice', 'firstresponderfire'],
    unrelated: UNRELATED,
    async setUnrelated(editor) {
      await editor.locator('input[type="checkbox"][name="pregnant"]').check();
      await editor.locator('select[name="chronic"]').selectOption(UNRELATED.chronic);
      await editor.locator('select[name="healthcareworker"]').selectOption(UNRELATED.healthcareworker);
    },
    cases: [
      { label: 'Household Contact alone is stored and reopens ticked without touching the other details', ticked: ['householdcontact'] },
      { label: 'First Responder Police alone replaces Household Contact with no stale value', ticked: ['firstresponderpolice'] },
      { label: 'First Responder Fire alone replaces Police with no stale value', ticked: ['firstresponderfire'] },
      { label: 'Household Contact, Police and Fire together are all stored and reopen ticked',
        ticked: ['householdcontact', 'firstresponderpolice', 'firstresponderfire'] },
      { label: 'clearing all three stores none of them and keeps the unrelated details', ticked: [] },
    ],
  });

  await s.step('a record with the single legacy first-responder value reopens with Police and Fire ticked and saving stores both', async () => {
    const { sql, patient, provider } = s;
    const live = forms.active();
    // The clinician deletes the record the cases left, so the legacy one is the only live H1N1.
    const current = await forms.open(live);
    await clickAndAwaitClose(current, current.locator('input[name="delete"]'));
    await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${live}`, '1', 'Delete did not archive the record');
    // The closing popup refreshes the Preventions page; the deleted row going away shows that refresh has finished.
    await forms.record(live).waitFor({ state: 'detached' });
    // The record a clinician would find after an upgrade: the same vaccination, saved before the boxes were split.
    const legacy = sql.value(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,
        deleted,refused,never,creator,lastUpdateDate)
      VALUES (${patient},NOW(),${h.sqlString(`${GIVEN_ON} 00:00:00`)},${h.sqlString(provider)},'H1N1','0','0','0',${h.sqlString(provider)},NOW());
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(legacy), 'The legacy record fixture was not created');
    sql.execute(`INSERT INTO preventionsExt (prevention_id,keyval,val) VALUES (${legacy},'firstresponder','true'),
      (${legacy},'lot',${h.sqlString(`PWLOT${s.marker.slice(-8)}`)})`);
    await forms.index.reload();
    await forms.index.getByText('Immunization Schedules', { exact: false }).first().waitFor({ state: 'visible' });
    const editor = await forms.open(legacy);
    for (const flag of ['firstresponderpolice', 'firstresponderfire']) {
      h.assert(await editor.locator(`input[type="checkbox"][name="${flag}"]`).isChecked(), `The legacy first responder reopened with ${flag} clear`);
    }
    h.assert(!(await editor.locator('input[type="checkbox"][name="householdcontact"]').isChecked()), 'The legacy record reopened with Household Contact ticked');
    const replacement = await forms.saveAmendment(editor, legacy);
    h.assert(forms.ext(replacement, 'firstresponderpolice') === 'true' && forms.ext(replacement, 'firstresponderfire') === 'true',
      `Saving the legacy record stored police ${forms.ext(replacement, 'firstresponderpolice')} and fire ${forms.ext(replacement, 'firstresponderfire')}`);
    h.assert(forms.ext(legacy, 'firstresponder') === 'true', 'Archiving the legacy record changed its stored value');
  });
}

if (require.main === module) runWorkflow('prevention-h1n1-first-responder', workflow);
module.exports = { workflow };
