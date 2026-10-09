#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Finding 266: finding a patient and opening the Master Record must not write to the patient's row.
 *
 * WHY THIS EXISTS. A clinician who only LOOKS at a patient (the schedule's Search, then the result's Master Record) changes nothing,
 * and the audit trail and the row's lastUpdateDate say so. `Demographic.getPronoun()` and `getGender()` answer "" for a NULL
 * column (Demographic.java:1226-1242), and the entity is mapped on its getters, so a loaded patient with NULL pronoun or gender
 * differs from its loaded state at the end of the request and Hibernate flushes an UPDATE that turns NULL into "". Nothing logs
 * it and lastUpdateDate does not move. It was found by comparing the demo dataset with a validation install: the pronoun of 124 of
 * the 3000 demo patients (and the gender of 2) had been rewritten that way by checks that only searched for or opened them, none
 * of them edited.
 *
 * User path: Schedule > Search > the patient's Master Record, on an owned FAKE patient whose pronoun and gender were never set.
 *
 * Asserted, in order:
 *   1. control: the owned patient is stored with NULL pronoun and gender, so the later step is not vacuous;
 *   2. control: the schedule's Search finds the patient and its Master Record renders (the page's own Edit control is there);
 *   3. pinned: after that read, the row is as it was: pronoun and gender still NULL, lastUpdateDate unchanged.
 *
 * Fixtures: the workflow's owned FAKE patient only; it is removed with its chart rows by key and asserted gone. No clinic-wide
 * state is changed.
 *
 * Expected: the controls pass and the pinned step fails while finding 266 stands.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

const READ_STEP = 'the stored row is unchanged after the patient was found and its Master Record opened';

async function workflow(s) {
  const { sql, patient, marker } = s;
  const row = () => sql.rows(`SELECT pronoun IS NULL, gender IS NULL, lastUpdateDate FROM demographic WHERE demographic_no=${patient}`)[0];
  let before;

  await s.step('the owned patient is stored with NULL pronoun and gender', async () => {
    before = row();
    h.assert(before[0] === '1' && before[1] === '1',
      'The owned patient already has a pronoun or a gender, so a rewrite of NULL could not be told');
  });

  await s.step('the schedule Search finds the patient and its Master Record renders', async () => {
    const { masterPage } = await openMasterRecord(s.context, s.schedule, s.recorder, {
      searchTerm: marker, preferredDemographicNo: patient, timeout: 20000,
    });
    h.assert(new URL(masterPage.url()).searchParams.get('demographic_no') === patient,
      'The search opened a patient other than the owned fixture');
    await masterPage.locator('#editBtn').waitFor({ state: 'attached', timeout: 20000 });
    // The write, if there is one, is issued by the end of the requests above; give a late flush the same moment it needs.
    await masterPage.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  });

  // Pinned: holds only the assertion the finding breaks.
  await s.step(READ_STEP, async () => {
    const after = row();
    h.assert(after[0] === '1' && after[1] === '1' && after[2] === before[2],
      `Opening the patient rewrote its row without an edit: pronoun is ${after[0] === '1' ? 'still NULL' : 'no longer NULL'}, `
      + `gender is ${after[1] === '1' ? 'still NULL' : 'no longer NULL'}, lastUpdateDate ${after[2] === before[2] ? 'did not move' : 'moved'} `
      + '(Demographic.getPronoun()/getGender() answer "" for NULL and the entity is flushed dirty)');
  });
}

module.exports = { workflow, READ_STEP };
if (require.main === module) runWorkflow('demographic-open-writes-nothing', workflow, { openPatient: true, openMaster: false });
