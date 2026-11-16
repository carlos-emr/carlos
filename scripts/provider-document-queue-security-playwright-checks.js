#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {clickAndAwaitReload} = require('./lib/playwright-ui');

function preflightConfig() {
  if (process.env.CARLOS_DISPOSABLE_HOST !== 'true' || process.env.EXCLUSIVE !== '1') {
    throw new h.SkipCheck('Requires a disposable exclusive application host');
  }
}

async function workflow(s) {
  const {sql, marker, provider, config} = s;
  // Keep the literal name within queue.name's existing 40-character limit.
  const name = `Q${marker.slice(-16)}<b>x</b>`;
  const snapshot = () => sql.rows('SELECT id,HEX(value),value IS NULL FROM property '
    + "WHERE provider_no=? AND name='doc_default_queue' ORDER BY id", [provider]);
  const original = snapshot();
  h.assert(sql.value('SELECT COUNT(*) FROM queue WHERE name=?', [name]) === '0', 'Queue marker already exists');
  s.cleanup(() => {
    for (const [id] of sql.rows('SELECT id FROM queue WHERE name=?', [name])) {
      h.assert(/^[1-9]\d*$/.test(id), 'Invalid owned queue ID');
      sql.execute('DELETE FROM secObjPrivilege WHERE objectName=?', [`_queue.${id}`]);
      sql.execute('DELETE FROM secObjectName WHERE objectName=?', [`_queue.${id}`]);
      sql.execute('DELETE FROM queue WHERE id=? AND name=?', [id, name]);
      h.assert(sql.value('SELECT COUNT(*) FROM secObjectName WHERE objectName=?', [`_queue.${id}`]) === '0',
        'Owned security object remains');
    }
    sql.execute("DELETE FROM property WHERE provider_no=? AND name='doc_default_queue'", [provider]);
    for (const [id, hex, isNull] of original) {
      h.assert(/^\d+$/.test(id) && (isNull === '1' || /^[0-9a-f]*$/i.test(hex)), 'Invalid default queue snapshot');
      sql.execute("INSERT INTO property(id,provider_no,name,value) VALUES(?,?,'doc_default_queue',UNHEX(?))",
        [id, provider, isNull === '1' ? null : hex]);
    }
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(original), 'Original default queue was not restored exactly');
    h.assert(sql.value('SELECT COUNT(*) FROM queue WHERE name=?', [name]) === '0', 'Owned queue remains');
  });
  const editor = await s.context.newPage();
  async function openNew() {
    await h.gotoApp(editor, config.baseUrl, '/setProviderStaleDate?method=viewDefaultDocQueue');
    await editor.locator('a[onclick="showhide();"]').last().click();
  }
  let id;
  await s.step('a provider-created queue commits its security object and default with the same ID', async () => {
    await openNew();
    await editor.locator('[name="newDefaultDocQueueProperty.value"]').fill(name);
    await clickAndAwaitReload(editor, editor.locator('input[type="submit"]'));
    await expectValue(sql, 'SELECT COUNT(*) FROM queue q JOIN secObjectName s ON s.objectName=CONCAT(\'_queue.\',q.id) '
      + 'WHERE q.name=? AND s.description=? AND s.orgapplicable=0', '1', 'Queue security entry is missing', [name, name]);
    id = sql.value('SELECT id FROM queue WHERE name=?', [name]);
    h.assert(/^[1-9]\d*$/.test(id), 'Queue ID is invalid');
    h.assert(sql.value("SELECT value FROM property WHERE provider_no=? AND name='doc_default_queue'", [provider]) === id,
      'Provider default refers to a different queue');
    h.assert(sql.value('SELECT COUNT(*) FROM secObjPrivilege WHERE objectName=?', [`_queue.${id}`]) === '0',
      'Creating a queue unexpectedly granted role privileges');
    h.assert((await editor.locator('body').innerText()).includes('Default Document Queue saved'), 'Save success was not displayed');
  });
  await s.step('the role privilege form offers the new security object and renders its description literally', async () => {
    const admin = await s.context.newPage();
    await h.gotoApp(admin, config.baseUrl, '/admin/ProviderPrivilege');
    await admin.locator('#addtbl_filter input[type="search"]').fill(`_queue.${id}`);
    const checkbox = admin.locator(`input[type="checkbox"][name="object$_queue.${id}"]`);
    await checkbox.waitFor();
    const cell = checkbox.locator('..');
    h.assert((await cell.innerText()).includes(name), 'The grantable queue description was not rendered literally');
    h.assert(await cell.locator('b').count() === 0, 'Queue markup was interpreted as HTML');
  });
  await s.step('a blank name reports not saved and preserves the previous queue and default', async () => {
    const before = sql.value("SELECT COUNT(*) FROM queue WHERE name IS NULL OR TRIM(name)=''");
    await openNew();
    await editor.locator('[name="newDefaultDocQueueProperty.value"]').fill('   ');
    await clickAndAwaitReload(editor, editor.locator('input[type="submit"]'));
    h.assert((await editor.locator('body').innerText()).includes('NOT been saved'), 'Blank queue was reported as saved');
    h.assert(sql.value("SELECT COUNT(*) FROM queue WHERE name IS NULL OR TRIM(name)=''") === before, 'A blank queue was persisted');
    h.assert(sql.value("SELECT value FROM property WHERE provider_no=? AND name='doc_default_queue'", [provider]) === id,
      'Blank queue changed the provider default');
    h.assert(sql.value('SELECT COUNT(*) FROM queue WHERE id=? AND name=?', [id, name]) === '1', 'Previous queue changed');
  });
}

if (require.main === module) runWorkflow('provider-document-queue-security', workflow, {openPatient: false, preflightConfig});
module.exports = {preflightConfig, workflow};
