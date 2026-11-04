#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Installed #4054 regression. Disposable database only; do not run concurrently
// with other checks changing this provider's default document queue.
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, clickAndAwaitReload } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NAME = 'document-queue-identity';

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const adminName = `Q${marker.slice(-16)}A`;
  const providerName = `Q${marker.slice(-16)}P`;
  const names = `${q(adminName)},${q(providerName)}`;
  const predicate = `provider_no=${q(provider)} AND name='doc_default_queue'`;
  const snapshot = () => sql.rows(`SELECT id,COALESCE(HEX(value),''),value IS NULL FROM property WHERE ${predicate} ORDER BY id`);
  const original = snapshot();
  s.cleanup(() => {
    const restore = [`DELETE FROM property WHERE ${predicate}`];
    for (const [id, hex, isNull] of original) {
      h.assert(/^\d+$/.test(id) && /^[0-9a-f]*$/i.test(hex), 'Invalid default queue snapshot');
      restore.push(`INSERT INTO property(id,provider_no,name,value) VALUES
        (${id},${q(provider)},'doc_default_queue',${isNull === '1' ? 'NULL' : `UNHEX('${hex}')`})`);
    }
    restore.push(`DELETE FROM secObjectName WHERE description IN (${names}) AND objectName LIKE '\\_queue.%'`);
    restore.push(`DELETE FROM queue WHERE name IN (${names})`);
    sql.execute(`START TRANSACTION;${restore.join(';')};COMMIT`);
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(original), 'Default queue was not restored');
    h.assert(sql.value(`SELECT COUNT(*) FROM queue WHERE name IN (${names})`) === '0', 'Owned queues remain');
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjectName WHERE description IN (${names})`) === '0', 'Owned security objects remain');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM queue WHERE name IN (${names})`) === '0', 'Marker queue already exists');

  // This legacy preference has no current Preferences link. Open its registered
  // view route, then drive its actual form and the Administration Add Queue UI.
  const editor = await s.context.newPage();
  const openPreference = () => h.gotoApp(editor, s.config.baseUrl, '/setProviderStaleDate?method=viewDefaultDocQueue');
  let providerId;
  await s.step('the default queue form creates and saves its own queue ID', async () => {
    await openPreference();
    await editor.locator('a[onclick="showhide();"]').last().click();
    await editor.locator('[name="newDefaultDocQueueProperty.value"]').fill(providerName);
    await clickAndAwaitReload(editor, editor.locator('input[type="submit"]'));
    await expectValue(sql, `SELECT COUNT(*) FROM queue WHERE name=${q(providerName)}`, '1', 'Provider queue was not created');
    providerId = sql.value(`SELECT id FROM queue WHERE name=${q(providerName)}`);
    await expectValue(sql, `SELECT value FROM property WHERE ${predicate}`, providerId, 'Default is not the created queue ID');
    h.assert((await editor.locator('body').innerText()).includes('Default Document Queue saved'), 'Save success was not displayed');
  });

  await s.step('Administration Add Queue associates its security object with the inserted ID', async () => {
    const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });
    const link = admin.locator('a.contentLink[href$="/admin/ViewAddQueue"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.click();
    await admin.locator('#dynamic-content #newQueueName').fill(adminName);
    await admin.locator('#dynamic-content #add-btn').click();
    await expectValue(sql, `SELECT COUNT(*) FROM queue q JOIN secObjectName s ON s.objectName=CONCAT('_queue.',q.id)
      WHERE q.name=${q(adminName)} AND s.description=${q(adminName)}`, '1', 'Security object belongs to a different queue');
    h.assert(sql.value('SELECT MAX(id) FROM queue') !== providerId, 'A later queue is required for the failure regression');
  });

  await s.step('a duplicate-name insert reports not saved and preserves the previous default', async () => {
    await openPreference();
    await editor.locator('a[onclick="showhide();"]').last().click();
    await editor.locator('[name="newDefaultDocQueueProperty.value"]').fill(providerName);
    await clickAndAwaitReload(editor, editor.locator('input[type="submit"]'));
    h.assert((await editor.locator('body').innerText()).includes('NOT been saved'), 'Creation failure was reported as success');
    h.assert(sql.value(`SELECT value FROM property WHERE ${predicate}`) === providerId, 'Failed insert changed the provider default');
    h.assert(sql.value(`SELECT COUNT(*) FROM queue WHERE name=${q(providerName)}`) === '1', 'Failed insert created another row');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
