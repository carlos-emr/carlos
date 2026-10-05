#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Tickler add.
 *
 * User path: Schedule > Search > Master Record > Tickler > New Tickler > Save.
 * For each rapid activation (dblclick(), two back-to-back click({noWaitAfter}), a double
 * Enter in a text field) the check saves ONE tickler with its own marker text and asserts the
 * database holds EXACTLY ONE tickler row for the owned patient and that marker (Enter may
 * legitimately not submit a button-only form; it must never create two). ticklerAdd.jsp disables its
 * buttons in validate(); this proves it holds against a real double activation.
 *
 * Fixtures: the owned FAKE- patient from lib/workflow-session.js; every tickler row (and its
 * update/comment rows) for that patient carrying the run marker is deleted and asserted gone.
 * Implements the "double-submit" wave-6 pattern sweep (create-action idempotency).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer } = require('./lib/double-submit-helpers');

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const owned = (tag) => `demographic_no=${patient} AND message LIKE ${h.sqlString(`${marker}-${tag}%`)}`;

  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE demographic_no=${patient}
      AND message LIKE ${h.sqlString(`${marker}-%`)}`).map(([id]) => id);
    for (const id of ids) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned tickler id is invalid');
      sql.execute(`DELETE FROM tickler_comments WHERE tickler_no=${id}`);
      sql.execute(`DELETE FROM tickler_update WHERE tickler_no=${id}`);
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id=${id}`);
    }
    sql.execute(`DELETE FROM tickler WHERE demographic_no=${patient} AND message LIKE ${h.sqlString(`${marker}-%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient}
      AND message LIKE ${h.sqlString(`${marker}-%`)}`) === '0', 'Tickler cleanup left rows behind');
  });

  const list = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'tickler-list');
  await list.waitForLoadState('domcontentloaded', { timeout: 20000 });
  await h.assertNotErrorPage(list, 'the patient tickler list');
  const v = verdicts('tickler-add');

  for (const mode of MODES) {
    await s.step(`New Tickler Save via ${mode.label} writes at most one row`, async () => {
      const add = await s.popup(list, list.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
      await add.waitForLoadState('domcontentloaded', { timeout: 20000 });
      await add.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
      await add.locator('textarea[name="ticklerMessage"]').fill(`${marker}-${mode.tag} double submit`);
      await add.locator('select[name="task_assigned_to"]').first().selectOption(provider);
      const posts = watchPosts(add.context(), /\/tickler\/DbTicklerAdd$/);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, /\/tickler\/DbTicklerAdd/) : null;
      await rapid(mode.key, add.locator('input.btn-primary[name="Button"]').first(),
        { textField: add.locator('input[name="xml_appointment_date"]') });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM tickler WHERE ${owned(mode.tag)}`,
        { min: mode.key === 'doubleEnter' ? 0 : 1 });
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${posts.seen.length} DbTicklerAdd POST(s) sent)`);
      v.record(mode.label, count, mode.key === 'doubleEnter' ? { atMost: 1 } : { exactly: 1 });
      if (!add.isClosed()) await add.close().catch(() => {});
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-tickler', workflow, { openPatient: true });
