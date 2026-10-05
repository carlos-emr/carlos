#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: two sessions edit the same patient's demographics (lost update).
 *
 * User path (both sessions): Schedule > Search > the patient row > Master Record > Edit >
 * change a field > Update Record. The patient is first saved once through the form (so its
 * demographicExt rows exist, as for any patient a clinic has already edited). Session A then
 * opens the edit form; session B changes the phone number and saves; session A, still holding the
 * form it loaded BEFORE B's save, changes the city and saves.
 *
 * Asserted: B's save lands (control); A's save is answered by a page, not a server error; and B's
 * phone number is NOT silently reverted to the stale blank A's form carried. DemographicUpdate2Action
 * rewrites every column from the posted form with no version / last-update comparison, so a refusal
 * or a merge is the correct behaviour and a silent overwrite is the defect. Fails at the last step.
 *
 * `concurrency-demographic-ext-insert` (same file, prime=false) runs the same race on a patient
 * whose demographicExt rows were never created: A's stale form re-INSERTs them and B's rows
 * collide (HTTP 500 and A's whole edit lost).
 *
 * Fixtures: the workflow's owned FAKE- patient (its demographic / demographicExt / demographicArchive
 * rows are removed by the session cleanup). Both sessions are the shared test login.
 * §2.4 demographic-edit-update, wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { openSecondSession, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { openEditForm } = require('./demographic-edit-update-playwright-checks');

const TIMEOUT = 20000;
const UPDATE = /\/demographic\/DemographicUpdate$/;

async function save(page) {
  const submit = page.locator('#updateButton input[type="submit"]').first();
  h.assert(await submit.count() > 0, 'The edit form offers no Update Record control');
  await clickAndAwaitReload(page, submit, { timeout: TIMEOUT, label: 'Update Record' });
}

function makeWorkflow({ prime }) {
  return async function workflow(s) {
    const { sql, patient } = s;
    const phoneB = '555-0187';
    const cityA = `CITY-${s.marker.slice(-8)}`;
    const col = (name) => `SELECT IFNULL(${name},'') FROM demographic WHERE demographic_no=${patient}`;
    const extRows = () => sql.value(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${patient}`);
    let b;
    // The harness's parent cleanup removes only demographicExt and demographicArchive rows; each save through the form also
    // writes demographicExtArchive (keyed by archive id) and demographiccust. Register their removal before the first save
    // and, because cleanups run before the parent's, while the archive rows they hang from still exist.
    s.cleanup(() => {
      sql.execute(`DELETE FROM demographicExtArchive WHERE archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient})
        OR demographic_no=${patient};
        DELETE FROM demographiccust WHERE demographic_no=${patient}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient}
          OR archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient}))`) === '0',
      'The owned patient\'s custom-field and extension archive rows were not removed');
    });
    // The edit form refuses a blank Canadian postal code; the fixture needs a valid one to be savable.
    sql.execute(`UPDATE demographic SET postal='K1A0B1' WHERE demographic_no=${patient}`);
    await s.master.reload({ waitUntil: 'domcontentloaded' });

    if (prime) {
      await s.step('the patient is saved once through the form so its extension rows exist', async () => {
        await openEditForm(s.master, TIMEOUT);
        await s.master.locator('[name="phone2"]').first().fill('555-0100');
        await save(s.master);
        h.assert(Number(extRows()) > 0, 'The first save created no demographicExt rows');
        await s.master.reload({ waitUntil: 'domcontentloaded' });
      });
    } else {
      h.assert(extRows() === '0', 'The fixture patient already has demographicExt rows');
    }

    await s.step('session A opens the edit form first and will hold it while B saves', async () => {
      await openEditForm(s.master, TIMEOUT);
      h.assert(await s.master.locator('[name="phone"]').first().inputValue() === '', 'The owned patient already has a phone number');
    });
    await s.step('session B edits the same patient: its phone number is saved', async () => {
      b = await openSecondSession(s, { label: 'second-session' });
      await openEditForm(b.master, TIMEOUT);
      await b.master.locator('[name="phone"]').first().fill(phoneB);
      await save(b.master);
      await expectValue(sql, col('phone'), phoneB, 'Session B\'s phone number did not reach the database');
    });
    let outcome;
    await s.step('session A saves a different field from its stale form', async () => {
      await s.master.locator('[name="city"]').first().fill(cityA);
      const mark = failureMark(s.recorder);
      const [response] = await Promise.all([
        s.master.waitForResponse(r => r.request().method() === 'POST' && UPDATE.test(new URL(r.url()).pathname), { timeout: TIMEOUT }),
        s.master.locator('#updateButton input[type="submit"]').first().click(),
      ]);
      outcome = { status: response.status(), phone: sql.value(col('phone')), city: sql.value(col('city')) };
      await s.master.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      await s.master.waitForTimeout(500); // the console entry for a failed load arrives after the response
      // The status is asserted by the next step; consume the recorded failure so it is reported once, there.
      if (response.status() >= 400) consumeExpectedFailure(s.recorder, mark, { status: response.status(), path: UPDATE });
    });
    await s.step('session A\'s stale save is answered normally, not by a server error', async () => {
      h.assert(outcome.status < 400,
        `Session A's stale save answered HTTP ${outcome.status}: its form carried an empty demographicExt id, so DemographicUpdate2Action `
        + 'INSERTed a second demographicExt row that session B\'s save had already created (uk_demo_ext violation) and the whole save rolled back. '
        + `Afterwards phone ${outcome.phone === phoneB ? 'was kept' : 'was reverted'}, city ${outcome.city === cityA ? 'was stored' : 'was not stored'}.`);
    });
    await s.step('session B\'s phone number survives session A\'s stale save', async () => {
      const phone = sql.value(col('phone'));
      h.assert(phone === phoneB,
        `Session A's stale save silently reverted session B's phone number (phone is now ${phone === '' ? 'blank' : 'another value'}). `
        + 'DemographicUpdate2Action overwrites every column with no version or last-update comparison, so the later save wins without any warning to either user.');
    });
  };
}

const workflow = makeWorkflow({ prime: true });
module.exports = { workflow, makeWorkflow };
if (require.main === module) runWorkflow('concurrency-demographic-edit', workflow, { openPatient: true });
