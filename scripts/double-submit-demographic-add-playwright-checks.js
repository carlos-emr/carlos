#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Add Demographic (new patient record) -- a duplicate patient is HIGH severity
 * (two charts for one person).
 *
 * User path: Schedule > Search > a search with no match > Create Demographic > fill the form >
 * Add Record. For each rapid activation (dblclick(), two back-to-back clicks, double Enter in the postal
 * field, slow-response re-click) the check registers ONE FAKE- patient (own marker last name) and asserts
 * EXACTLY ONE demographic row for that last name.
 *
 * Fixtures: patients named FAKE-PW<hex>-<tag>; cleanup deletes the demographic row(s) matching that
 * marker prefix (plus admission/archive/ext/custom rows keyed to them) and asserts they are gone.
 * Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer } = require('./lib/double-submit-helpers');

async function workflow(s) {
  const { sql, marker } = s;
  const like = h.sqlString(`${marker}-%`);
  const owned = () => sql.rows(`SELECT demographic_no FROM demographic WHERE last_name LIKE ${like}`).map(([id]) => id);
  s.cleanup(() => {
    const ids = owned();
    for (const id of ids) h.assert(/^[1-9]\d*$/.test(id), 'Owned demographic id is invalid');
    if (ids.length) {
      const list = ids.join(',');
      // Every child table the Add action writes, by its real key column (demographiccust and its archives key on
      // demographic_no; the add action also writes demographicExtArchive rows).
      const children = [['admission', 'client_id'], ['demographicArchive', 'demographic_no'],
        ['demographicExt', 'demographic_no'], ['demographicExtArchive', 'demographic_no'],
        ['demographiccust', 'demographic_no'], ['demographiccustArchive', 'demographic_no'],
        ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no']];
      for (const [table, column] of children) sql.execute(`DELETE FROM ${table} WHERE ${column} IN (${list})`);
      for (const [table, column] of children) {
        h.assert(sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${column} IN (${list})`) === '0', `Child rows remain in ${table}`);
      }
      sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${list}) AND last_name LIKE ${like}`);
    }
    h.assert(owned().length === 0, 'Owned new patients were not removed');
  });
  const v = verdicts('demographic-add');

  for (const mode of MODES) {
    await s.step(`Add Record via ${mode.label} registers at most one patient`, async () => {
      const lastName = `${marker}-${mode.tag}`;
      const search = await s.popup(s.schedule, s.schedule.locator('a').filter({ hasText: /^Search$/ }), 'patient-search');
      await search.waitForLoadState('domcontentloaded', { timeout: 30000 });
      await search.locator('#keyword, input[name="keyword"]').first().fill(lastName);
      await clickAndAwaitReload(search, search.locator("input[type='submit']").first(), { required: false });
      await search.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await search.locator("form[action$='/demographic/ViewDemographicAddARecordHtm'] button[type='submit']").first().click();
      await search.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await h.assertNotErrorPage(search, 'add-demographic form');
      const form = search.locator('form[name="adddemographic"]');
      await form.locator('input[name="last_name"]').fill(lastName);
      await form.locator('input[name="first_name"]').fill('Check');
      await form.locator('select[name="sex"]').selectOption('F');
      await form.locator('input[name="inputDOB"]').fill('1990-01-15');
      await form.locator('input[name="postal"]').fill('M5W 1E6');
      const route = /\/demographic\/DemographicAddRecord$/;
      const posts = watchPosts(search.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      // A second activation can reach the form's own "other patients with the same first and last name" confirm once the
      // first record exists. Dismissing it (Cancel) is the correct user response and creates nothing, so it is expected here.
      let count;
      const dialogs = await h.withExpectedDialogs(search, async () => {
        await rapid(mode.key, search.locator('input[type="submit"][value="Add Record"]').first(),
          { textField: form.locator('input[name="postal"]') });
        count = await settledCount(sql, `SELECT COUNT(*) FROM demographic WHERE last_name=${h.sqlString(lastName)}`,
          { min: mode.key === 'doubleEnter' ? 0 : 1 });
      }, { accept: false });
      h.assert(dialogs.every(dialog => dialog.type === 'confirm' && /same first and last name/i.test(dialog.text || '')),
        `An unexpected dialog was raised and dismissed: ${dialogs.map(dialog => `${dialog.type} "${dialog.text}"`).join(' | ')}`);
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${posts.seen.length} add-record POST(s))`);
      v.record(mode.label, count, mode.key === 'doubleEnter' ? { atMost: 1 } : { exactly: 1 });
      if (!search.isClosed()) await search.close().catch(() => {});
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-demographic-add', workflow, { openPatient: false });
