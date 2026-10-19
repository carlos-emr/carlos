#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Administration lookup add -- Consultation configuration > Add Service.
 *
 * User path: Schedule > Consultations > Consultation configuration > Add Service > type a name > Add. For each
 * rapid activation (dblclick(), two back-to-back clicks, double Enter in the name field, slow-response re-click,
 * POST replay) the check adds ONE service with its own marker name and asserts EXACTLY ONE consultationServices
 * row for that name. (The table has no unique key on the description.)
 *
 * Fixtures: services named FAKE-PW<hex>-<tag>; cleanup deletes every service row carrying the run marker (and
 * any specialist links) and asserts they are gone. Touches no clinic-wide flag. Wave-6 sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer } = require('./lib/double-submit-helpers');

async function workflow(s) {
  const { sql, marker } = s;
  const like = h.sqlString(`${marker}-%`);
  const owned = () => sql.rows(`SELECT serviceId FROM consultationServices WHERE serviceDesc LIKE ${like}`).map(([id]) => id);
  s.cleanup(() => {
    const ids = owned();
    for (const id of ids) h.assert(/^[1-9]\d*$/.test(id), 'Owned service id is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId IN (${ids.join(',')})`);
      sql.execute(`DELETE FROM consultationServices WHERE serviceId IN (${ids.join(',')}) AND serviceDesc LIKE ${like}`);
    }
    h.assert(owned().length === 0, 'Owned consultation services were not removed');
  });
  const { page: list, isPopup: opened } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const config = await s.popup(list, list.locator('a[href*="ViewShowAllServices"]'), 'consultation-config');
  const v = verdicts('add-service');

  for (const mode of MODES) {
    await s.step(`Add Service via ${mode.label} adds exactly one service`, async () => {
      await clickAndAwaitReload(config, config.locator('nav a[href$="/ViewAddService"]'), { label: 'ViewAddService' });
      const name = `${marker}-${mode.tag} double submit`;
      await config.locator('#service').fill(name);
      const route = /\/encounter\/AddService$/;
      const posts = watchPosts(config.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      await rapid(mode.key, config.locator('input[type="submit"]').first(), { textField: config.locator('#service') });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${h.sqlString(name)}`,
        { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${posts.seen.length} AddService POST(s))`);
      v.record(mode.label, count, { exactly: 1 });
      await config.waitForLoadState('domcontentloaded', { timeout: 20000 }).catch(() => {});
    });
  }
  v.finish();
  if (opened && list && !list.isClosed()) await list.close().catch(() => {});
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-admin-adds', workflow, { openPatient: false });
