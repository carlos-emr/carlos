#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Recovery check: a stale Master Record save is refused, keeps the user's entries, and both ways
 * forward work (issue #4225).
 *
 * The Update Record form carries the version of the record it was rendered from. When another user
 * saved the patient after it was opened, DemographicUpdate2Action answers HTTP 409 with a conflict
 * page instead of writing the stale form over the newer record. concurrency-demographic-edit and
 * concurrency-demographic-ext-insert pin the refusal; this check pins what the user can do next:
 *
 *   1. the conflict page lists the field the user changed (city) with their entry and the current
 *      value, and the field the other user changed (phone) with the stale blank beside the newer
 *      number; the database still holds the newer number and not the refused city;
 *   2. "Save my entries over the current record", cancelled at its confirmation, posts nothing;
 *   3. accepted, it saves the refused entries deliberately (city stored, phone back to blank) and
 *      returns to the Master Record;
 *   4. on a second race, "Open the current record" opens the patient as it is now in a new window,
 *      where the user re-applies the change and saves: both users' changes are kept.
 *
 * User path (both sessions): Schedule > Search > the patient row > Master Record > Edit > Update
 * Record. Session B is a second login of the shared test user.
 *
 * Fixtures: the workflow's owned FAKE- patient (given a valid postal code by SQL so the form is
 * savable); its demographic / demographicExt / demographicArchive rows are removed by the session
 * cleanup, and the demographicExtArchive and demographiccust rows the saves write are removed here.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickAndAwaitReload, clickOpensPopup } = require('./lib/playwright-ui');
const { openSecondSession, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { openEditForm } = require('./demographic-edit-update-playwright-checks');

const TIMEOUT = 20000;
const UPDATE = /\/demographic\/DemographicUpdate$/;

async function save(page) {
  const submit = page.locator('#updateButton input[type="submit"]').first();
  h.assert(await submit.count() > 0, 'The edit form offers no Update Record control');
  await clickAndAwaitReload(page, submit, { timeout: TIMEOUT, label: 'Update Record' });
}

/** Saves `page`'s open form, which must be stale, and returns the 409 response it gets. */
async function staleSave(s, page) {
  const mark = failureMark(s.recorder);
  const [response] = await Promise.all([
    page.waitForResponse(r => r.request().method() === 'POST' && UPDATE.test(new URL(r.url()).pathname), { timeout: TIMEOUT }),
    page.locator('#updateButton input[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
  await page.waitForTimeout(500); // the console entry for a failed load arrives after the response
  h.assert(response.status() === 409, `A stale Update Record answered HTTP ${response.status()}, expected the 409 conflict page`);
  consumeExpectedFailure(s.recorder, mark, { status: 409, path: UPDATE });
  return response;
}

/**
 * How many conflict-table rows hold exactly these "your entry" and "current" values. Compared as
 * plain text, so a blank value matches only a blank cell and no value is read as a pattern.
 */
function conflictRows(page, yours, current) {
  return page.locator('#demographicConflictDifferences tbody tr').evaluateAll((rows, [y, c]) => rows.filter(row =>
    row.querySelector('td.yours')?.textContent.trim() === y
      && row.querySelector('td.current')?.textContent.trim() === c).length, [yours, current]);
}

async function workflow(s) {
  const { sql, patient } = s;
  const phoneB = '555-0187';
  const phoneB2 = '555-0199';
  const cityA = `CITY-${s.marker.slice(-8)}`;
  const cityA2 = `TOWN-${s.marker.slice(-8)}`;
  const col = (name) => sql.value(`SELECT IFNULL(${name},'') FROM demographic WHERE demographic_no=${patient}`);
  let b;
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

  await s.step('session A opens the edit form; session B then saves a phone number', async () => {
    await openEditForm(s.master, TIMEOUT);
    h.assert(await s.master.locator('[name="phone"]').first().inputValue() === '', 'The owned patient already has a phone number');
    b = await openSecondSession(s, { label: 'second-session' });
    await openEditForm(b.master, TIMEOUT);
    await b.master.locator('[name="phone"]').first().fill(phoneB);
    await save(b.master);
    h.assert(col('phone') === phoneB, 'Session B\'s phone number did not reach the database');
  });

  await s.step('session A\'s stale save is refused with its entries and the newer values shown, and nothing is written', async () => {
    await s.master.locator('[name="city"]').first().fill(cityA);
    await staleSave(s, s.master);
    await s.master.locator('#demographicUpdateConflict').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await conflictRows(s.master, cityA, '') === 1,
      'The conflict page does not show the city session A typed beside the record\'s current (blank) city');
    h.assert(await conflictRows(s.master, '', phoneB) === 1,
      'The conflict page does not show the stale blank phone beside the number session B saved');
    h.assert(col('phone') === phoneB && col('city') === '',
      `The refused save changed the record (phone ${col('phone') === phoneB ? 'kept' : 'changed'}, city ${col('city') === '' ? 'blank' : 'written'})`);
  });

  const saveMine = s.master.locator('#saveMyEntries button[type="submit"]');
  await s.step('cancelling "Save my entries over the current record" posts nothing', async () => {
    const posts = [];
    const listener = (request) => { if (request.method() === 'POST' && UPDATE.test(new URL(request.url()).pathname)) posts.push(request); };
    s.master.on('request', listener);
    const dialogs = await h.withExpectedDialogs(s.master, async () => {
      await saveMine.click();
      await s.master.waitForTimeout(1000);
    }, { accept: false });
    s.master.off('request', listener);
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Saving over the current record asked for no confirmation');
    h.assert(posts.length === 0, 'A cancelled confirmation still posted the refused entries');
    h.assert(col('phone') === phoneB && col('city') === '', 'A cancelled confirmation changed the record');
  });

  await s.step('accepting it saves the refused entries deliberately and returns to the Master Record', async () => {
    await h.withExpectedDialogs(s.master, async () => {
      await clickAndAwaitReload(s.master, saveMine, { timeout: TIMEOUT, label: 'Save my entries over the current record' });
    });
    await h.assertNotErrorPage(s.master, 'the Master Record after saving over the current record');
    h.assert(new URL(s.master.url()).pathname.endsWith('/demographic/DemographicEdit'),
      `Saving over the current record ended on ${new URL(s.master.url()).pathname}, not the Master Record`);
    h.assert(col('city') === cityA && col('phone') === '',
      'The deliberate save did not store session A\'s entries (city and the blank phone it showed)');
  });

  await s.step('after a second race, "Open the current record" lets session A re-apply its change without losing session B\'s', async () => {
    await openEditForm(s.master, TIMEOUT);
    await b.master.reload({ waitUntil: 'domcontentloaded' });
    await openEditForm(b.master, TIMEOUT);
    await b.master.locator('[name="phone"]').first().fill(phoneB2);
    await save(b.master);
    h.assert(col('phone') === phoneB2, 'Session B\'s second phone number did not reach the database');

    await s.master.locator('[name="city"]').first().fill(cityA2);
    await staleSave(s, s.master);
    h.assert(await conflictRows(s.master, cityA2, cityA) === 1,
      'The second conflict page does not show the new city beside the stored one');
    const current = await clickOpensPopup(s.master, s.master.locator('#openCurrentRecord'),
      { context: s.context, recorder: s.recorder, label: 'current-record', timeout: TIMEOUT });
    await current.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
    h.assert(new URL(current.url()).searchParams.get('demographic_no') === patient, 'The current record opened another patient');
    await openEditForm(current, TIMEOUT);
    h.assert(await current.locator('[name="phone"]').first().inputValue() === phoneB2,
      'The current record does not show session B\'s newer phone number');
    await current.locator('[name="city"]').first().fill(cityA2);
    await save(current);
    h.assert(col('phone') === phoneB2 && col('city') === cityA2,
      'Re-applying the change on the current record did not keep both users\' changes');
    await current.close().catch(() => {});
  });

  if (b) await b.context.close().catch(() => {});
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-stale-edit-recovery', workflow, { openPatient: true });
