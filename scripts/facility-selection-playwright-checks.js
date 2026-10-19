#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const existing = s.sql.value(`SELECT pf.facility_id FROM provider_facility pf JOIN Facility f ON f.id=pf.facility_id
    WHERE pf.provider_no=${h.sqlString(s.provider)} AND f.disabled=0 ORDER BY f.id LIMIT 1`);
  if (!/^[1-9]\d*$/.test(existing)) throw new h.SkipCheck('The configured provider needs an existing active facility');
  const name = `${s.marker} O'Neil & <Test>`;
  let id;
  s.cleanup(() => {
    if (!id) return;
    h.assert(s.sql.value(`SELECT COUNT(*) FROM Facility WHERE id=${id} AND name=${h.sqlString(name)}`) === '1',
      'Facility fixture ownership changed');
    s.sql.execute(`DELETE FROM provider_facility WHERE facility_id=${id} AND provider_no=${h.sqlString(s.provider)};
      DELETE FROM Facility WHERE id=${id} AND name=${h.sqlString(name)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM Facility WHERE id=${id}`) === '0', 'Facility cleanup failed');
  });
  // Clone configuration only, never provider membership or clinical data.
  const columns = s.sql.rows("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Facility' AND COLUMN_NAME NOT IN ('id','name','lastUpdated') ORDER BY ORDINAL_POSITION").flat();
  h.assert(columns.length > 10 && columns.every(column => /^[A-Za-z][A-Za-z0-9_]*$/.test(column)), 'Unexpected facility schema');
  const fields = columns.map(column => `\`${column}\``).join(',');
  id = s.sql.value(`INSERT INTO Facility (name,lastUpdated,${fields}) SELECT ${h.sqlString(name)},NOW(),${fields} FROM Facility WHERE id=${existing}; SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id) && id !== existing, 'Owned facility was not created');
  s.sql.execute(`INSERT INTO provider_facility(provider_no,facility_id) VALUES (${h.sqlString(s.provider)},${id})`);
  const page = await s.context.newPage();
  await s.step('render authorized facility names literally without executable markup', async () => {
    await h.gotoApp(page, s.config.baseUrl, '/select_facility');
    const button = page.getByRole('button', { name, exact: true });
    await button.waitFor();
    h.assert(await button.locator('test').count() === 0, 'Facility name became HTML');
    h.assert(await button.locator('xpath=..').locator('input[name="selectedFacilityId"]').inputValue() === id,
      'Facility button targets a different facility');
  });
  await s.step('reject GET selection and tokenless POST without an audit of a successful selection', async () => {
    const count = () => s.sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${h.sqlString(s.provider)} AND action='log in' AND content='login' AND contentId=${h.sqlString('facilityId='+id)}`);
    const before = count();
    const action = await page.getByRole('button', { name, exact: true }).locator('xpath=..').getAttribute('action');
    const url = new URL(action, page.url()).href;
    const get = await s.context.request.get(url, { params: { selectedFacilityId: id }, maxRedirects: 0 });
    h.assert(get.status() === 405 && get.headers().allow === 'POST', 'GET facility mutation was not refused');
    const post = await s.context.request.post(url, { form: { selectedFacilityId: id }, maxRedirects: 0 });
    h.assert(post.status() === 403, 'Facility selection without CSRF was not refused');
    h.assert(count() === before, 'Rejected facility request produced a successful selection audit');
  });
  await s.step('select the owned facility using the actual tokenized form and reach the schedule', async () => {
    const logStart = s.sql.value('SELECT COALESCE(MAX(id),0) FROM log');
    await Promise.all([page.waitForURL(/providercontrol|appointment/i), page.getByRole('button', { name, exact: true }).click()]);
    await page.locator('#admin-panel, #admin2').first().waitFor();
    await expectValue(s.sql, `SELECT COUNT(*) FROM log WHERE id>${logStart} AND provider_no=${h.sqlString(s.provider)}
      AND action='log in' AND content='login' AND contentId=${h.sqlString('facilityId='+id)}`, '1', 'Facility selection did not produce exactly one audit for the owned facility');
  });
  await s.step('revoke facility membership and reject a stale selector without creating a success audit', async () => {
    await h.gotoApp(page, s.config.baseUrl, '/select_facility');
    await page.getByRole('button', { name, exact: true }).waitFor();
    s.sql.execute(`DELETE FROM provider_facility WHERE provider_no=${h.sqlString(s.provider)} AND facility_id=${id}`);
    const logStart = s.sql.value('SELECT COALESCE(MAX(id),0) FROM log');
    await page.getByRole('button', { name, exact: true }).click();
    await page.locator('#username').waitFor();
    h.assert(!/providercontrol|appointment/i.test(page.url()), 'Revoked facility selection kept the authenticated session');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM log WHERE id>${logStart} AND provider_no=${h.sqlString(s.provider)}
      AND action='log in' AND content='login' AND contentId=${h.sqlString('facilityId='+id)}`) === '0',
      'Revoked facility produced a success audit');
  });
}
if (require.main === module) runWorkflow('facility-selection', workflow, { openPatient: false });
module.exports = { workflow };
