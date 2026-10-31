#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ SMS (admin/ConfigureSms, configureSms.jsp): the clinic's SMS provider, sender
 * number and secrets. No browser check opened the page.
 *
 * User path: Schedule ▸ Administration ▸ SMS ▸ edit ▸ Save.
 * Asserted: the page opens with every secret field empty; Save stores the sender number with SMS
 * and its scheduler left OFF, keeps the typed webhook secret out of the page afterwards (secrets are
 * write-only) and reports the save; a GET carrying the same save (method=configure) is refused with
 * 405 and changes nothing.
 *
 * Never pressed: "Send system test", which sends a real message. SMS stays disabled throughout.
 * FAKE values only: a 555-01xx number and a marker webhook secret; no provider credentials.
 *
 * Fixtures: none created. sms_config is snapshotted byte-exact (hex, every column) before the first
 * click; cleanup deletes the rows the run made and restores the snapshot. EXCLUSIVE=1: clinic-wide.
 * SKIP: a database without sms_config (a build without the SMS settings page), or a login without _admin.sms.
 */
const { authenticatedProbe } = require('./lib/authenticated-probe');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openAdminPage, submitInFrame } = require('./lib/admin-frame');

const COLUMNS = ['id', 'version', 'provider_type', 'enabled', 'scheduler_enabled', 'sender_number', 'webhook_secret',
  'credentials', 'updated_at', 'updated_by'];
const SENDER = '+15550100';

async function workflow(s) {
  const { sql, marker } = s;
  // The SMS settings page and its table (#3836) are on develop, not on every release line: a database without
  // sms_config has no page to check, and the snapshot below would fail on the missing table before any skip.
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sms_config'") !== '1') {
    throw new h.SkipCheck('The database has no sms_config table: the SMS settings page is not in this build, or its migration has not run');
  }
  // 'N' is NULL; 'X' + hex is the value. CAST ... AS BINARY makes HEX read a number's text (id, version, the flags)
  // rather than its value, so UNHEX puts the same number back; HEX of the bare number would restore a one-byte string.
  const snapshot = () => sql.rows(`SELECT ${COLUMNS.map(c => `IF(${c} IS NULL,'N',CONCAT('X',HEX(CAST(${c} AS BINARY))))`).join(',')} FROM sms_config ORDER BY id`);
  const before = snapshot();
  s.cleanup(() => {
    const value = cell => (cell === 'N' ? 'NULL' : `UNHEX('${cell.slice(1)}')`);
    sql.execute('DELETE FROM sms_config');
    for (const row of before) {
      sql.execute(`INSERT INTO sms_config (${COLUMNS.join(',')}) VALUES (${row.map(value).join(',')})`);
    }
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'sms_config was not restored exactly');
  });
  const secret = `FAKE-webhook-${marker}`;
  // The menu entry needs _admin.sms read. A database loaded from the development snapshot after its
  // migrations can lack that grant (re-run V1.0.71); without it there is no page to check.
  const canRead = sql.value(`SELECT COUNT(*) FROM secObjPrivilege p JOIN secUserRole r ON r.role_name=p.roleUserGroup
    WHERE r.provider_no=${h.sqlString(s.provider)} AND p.objectName='_admin.sms' AND p.privilege REGEXP '^[rwux]$'`);
  if (canRead === '0') throw new h.SkipCheck('The test login holds no _admin.sms right, so Administration has no SMS entry');

  let admin;
  let frame;
  await s.step('Administration ▸ SMS opens with every secret field empty', async () => {
    ({ admin, frame } = await openAdminPage(s, 'admin/ConfigureSms', { label: 'sms' }));
    h.assert(await frame.locator('#smsConfigForm').count() === 1, 'The SMS page has no settings form');
    const secrets = await frame.locator('#smsConfigForm input[type="password"]').evaluateAll(inputs => inputs.map(i => i.value));
    h.assert(secrets.every(v => v === ''), 'A secret field was filled in from the stored settings');
  });

  await s.step('Save stores the sender number with SMS left off, and keeps the typed secret out of the page', async () => {
    const form = frame.locator('#smsConfigForm');
    await form.locator('#senderNumber').fill(SENDER);
    await form.locator('#webhookSecret').fill(secret);
    for (const box of ['#enabled', '#schedulerEnabled']) await form.locator(box).setChecked(false);
    ({ frame } = await submitInFrame(admin, form.locator('#smsConfigSave'), 'admin/ConfigureSms', { afterQuery: 'result=' }));
    h.assert(new URL(frame.url()).searchParams.get('result') === 'saved', `Save answered result=${new URL(frame.url()).searchParams.get('result')}, not saved`);
    await h.assertNotErrorPage(frame, 'SMS after Save');
    await expectValue(sql, 'SELECT COUNT(*) FROM sms_config', '1', 'Save did not leave exactly one settings row');
    await expectValue(sql, 'SELECT CONCAT_WS(\'|\', sender_number, enabled, scheduler_enabled) FROM sms_config',
      `${SENDER}|0|0`, 'Save did not store the sender number with SMS and its scheduler off');
    h.assert(sql.value('SELECT webhook_secret IS NOT NULL FROM sms_config') === '1', 'Save did not store the webhook secret');
    h.assert(!(await frame.content()).includes(secret), 'The page shows the saved webhook secret');
    h.assert(await frame.locator('#senderNumber').inputValue() === SENDER, 'After Save the page does not show the sender number');
  });

  await s.step('a GET carrying the save is refused with 405 and changes nothing', async () => {
    const saved = JSON.stringify(snapshot());
    const params = new URLSearchParams({ method: 'configure', senderNumber: '+15550199', enabled: 'true' });
    const response = await authenticatedProbe(s, h.appUrl(s.config.baseUrl, `/admin/ConfigureSms?${params}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET save answered ${response.status()}, not 405`);
    h.assert(JSON.stringify(snapshot()) === saved, 'A refused GET changed the SMS settings');
  });
}

if (require.main === module) runWorkflow('admin-configure-sms', workflow, { openPatient: false });
module.exports = { workflow };
