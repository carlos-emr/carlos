#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// #5783: native admin saves exercise the retry-reset wiring without contacting a fax provider.
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {createFaxConfigGuard} = require('./lib/fax-config-state');

function preflightConfig() {
  if (process.env.CARLOS_DISPOSABLE_HOST !== 'true' || process.env.EXCLUSIVE !== '1') {
    throw new h.SkipCheck('Requires a disposable exclusive application host');
  }
}
function preflight({sql}) {
  if (sql.value('SELECT COUNT(*) FROM fax_config WHERE active=1') !== '0') {
    throw new h.SkipCheck('Refusing to replace configuration while a fax account is active');
  }
}
async function workflow(s) {
  const {sql, marker, schedule, config} = s;
  const own = {faxUser: '123456', accountName: marker};
  const guard = createFaxConfigGuard({sql, own});
  guard.begin();
  s.cleanup(() => guard.finish());
  // The entire inactive configuration is private-snapshotted and restored byte for
  // byte. The sole offered account stays disabled, so no provider is contacted.
  sql.execute('DELETE FROM fax_config WHERE active=0 OR active IS NULL');
  const id = h.insertId(sql, `INSERT INTO fax_config
    (faxUser,faxNumber,senderEmail,accountName,active,download,providerType,queue)
    VALUES(?,?,?,?,0,0,'SRFAX',1)`, 'owned inactive fax account',
  [own.faxUser, '4165550100', 'fake@example.invalid', own.accountName]);
  await h.gotoApp(schedule, config.baseUrl, '/administration');
  await schedule.locator('button[data-bs-target="#collapseFifteen"]').click();
  const link = schedule.locator('a.xlink[rel$="/admin/ViewConfigureFax"]');
  await link.click();
  const frame = schedule.frameLocator('#myFrame');
  await frame.locator('#configFrm').waitFor();
  await frame.locator('#faxPasswd').fill('FAKE-5783-password');

  async function save(name) {
    await frame.locator('#accountName').fill('');
    await frame.locator('#accountName').pressSequentially(name);
    h.assert(!await frame.locator('#on').isChecked() && await frame.locator('#of').isChecked(),
      'Owned fax account was enabled');
    h.assert(!await frame.locator('#downloadCheckbox').isChecked(), 'Owned inbound polling was enabled');
    const [response] = await Promise.all([
      schedule.waitForResponse(answer => answer.request().method() === 'POST'
        && new URL(answer.url()).pathname.endsWith('/admin/ManageFax')
        && new URLSearchParams(answer.request().postData() || '').get('method') === 'configure', {timeout: 30000}),
      frame.locator('#submit').click(),
    ]);
    h.assert(response.status() === 200 && (await response.json()).success === true, 'Native fax configuration save was refused');
    const rows = sql.rows('SELECT accountName,active,download,faxPasswd IS NOT NULL FROM fax_config WHERE id=?', [id]);
    h.assert(JSON.stringify(rows) === JSON.stringify([[name, '0', '0', '1']]), 'Owned fax settings were not persisted exactly');
    h.assert(sql.value('SELECT COUNT(*) FROM fax_config WHERE active=1 OR download=1') === '0', 'Fax polling was enabled');
    await schedule.waitForFunction(() => document.querySelector('#myFrame')?.contentDocument
      ?.getElementById('submit')?.disabled === true, null, {timeout: 20000});
  }
  await s.step('an inactive account saves through the native admin action without enabling polling', async () => {
    await save(marker + ' saved');
  });
  await s.step('reopening and saving the masked password retains its encrypted stored bytes', async () => {
    const credential = sql.value('SELECT HEX(faxPasswd) FROM fax_config WHERE id=?', [id]);
    await link.click();
    h.assert(await frame.locator('#faxPasswd').inputValue() === '**********', 'Stored fax password was not masked');
    await save(marker + ' saved again');
    h.assert(sql.value('SELECT HEX(faxPasswd) FROM fax_config WHERE id=?', [id]) === credential, 'Masked fax credential was rewritten');
  });
}
if (require.main === module) runWorkflow('fax-configuration-save', workflow,
  {openPatient: false, preflightConfig, preflight});
module.exports = {preflightConfig, preflight, workflow};
