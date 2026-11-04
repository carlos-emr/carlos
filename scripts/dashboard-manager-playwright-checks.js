#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

// Administration > Reports > Dashboard Manager (#2244): create, edit, inactivate,
// reactivate, reload, and refuse a GET save. Only synthetic dashboards and a
// throwaway login are created; cleanup removes the owned rows and grants.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { authenticatedProbe } = require('./lib/authenticated-probe');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

async function workflow(s) {
  const { sql, marker, recorder } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    if (!fixture.providerNo) return;
    const owner = h.sqlString(fixture.providerNo);
    const owned = `creator=${owner} AND name LIKE ${h.sqlString(marker + '%')}`;
    sql.execute(`DELETE FROM dashboard WHERE ${owned};
      DELETE FROM secObjPrivilege WHERE roleUserGroup=${owner}
      AND objectName IN ('_dashboardManager','_admin','_admin.reporting')`);
    h.assert(sql.value(`SELECT COUNT(*) FROM dashboard WHERE ${owned}`) === '0', 'Owned dashboards were not removed');
  });
  fixture.create();
  for (const [object, right] of [['_dashboardManager', 'x'], ['_admin', 'r'], ['_admin.reporting', 'r']]) {
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${h.sqlString(fixture.providerNo)},${h.sqlString(object)},${h.sqlString(right)},0,${h.sqlString(s.provider)})`);
  }
  const context = await h.newContext(s.context.browser(), s.config);
  context.on('page', page => h.wireStrictPage(page, 'dashboard-manager', recorder));
  const schedule = await h.login(context, { ...s.config, testUser: fixture.username }, recorder);
  let frame;
  let id;
  const name = `${marker} O'Neil & report`;
  const renamed = `${marker} Updated & report`;
  const description = 'Synthetic indicator dashboard';
  const row = () => frame.locator(`tr[data-dashboard-id="${id}"]`);
  const form = () => frame.locator('#dashboard-editor');
  async function navigate(action) {
    await Promise.all([frame.waitForNavigation({ waitUntil: 'load' }), action()]);
  }
  await s.step('Dashboard Manager opens through Administration and Reports', async () => {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(),
      { context, recorder, label: 'dashboard-manager-admin', timeout: 20000 });
    const link = admin.getByRole('link', { name: 'Dashboard Manager', exact: true, includeHidden: true });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Dashboard Manager iframe did not load');
    await form().waitFor();
  });
  await s.step('an invalid save shows an alert and writes no dashboard', async () => {
    await form().locator('input[name="CSRF-TOKEN"]').waitFor({ state: 'attached' });
    const mark = failureMark(recorder);
    await navigate(() => form().evaluate(element => element.submit()));
    h.assert(await frame.getByRole('alert').isVisible(), 'The validation alert is hidden');
    h.assert(sql.value(`SELECT COUNT(*) FROM dashboard WHERE creator=${h.sqlString(fixture.providerNo)}`) === '0',
      'An invalid save created a dashboard');
    consumeExpectedFailure(recorder, mark, { status: 400, method: 'POST', path: /\/web\/dashboard\/admin\/DashboardManager$/ });
  });
  await s.step('a new dashboard saves active and survives reload', async () => {
    await form().getByLabel('Name', { exact: true }).fill(name);
    await form().getByLabel('Description', { exact: true }).fill(description);
    await navigate(() => form().getByRole('button', { name: 'Save', exact: true }).click());
    id = sql.value(`SELECT id FROM dashboard WHERE creator=${h.sqlString(fixture.providerNo)} AND name=${h.sqlString(name)}`);
    h.assert(/^[1-9]\d*$/.test(id), 'Created dashboard was not persisted');
    h.assert(sql.value(`SELECT active+0 FROM dashboard WHERE id=${id}`) === '1', 'New dashboard is not active');
    await frame.goto(frame.url());
    h.assert(await row().locator('th').innerText() === name, 'Dashboard name did not round-trip as plain text');
  });
  await s.step('edit changes name and description while preserving creator and active state', async () => {
    await navigate(() => row().getByRole('link', { name: 'Edit Dashboard', exact: true }).click());
    h.assert(await form().getByLabel('Name', { exact: true }).inputValue() === name, 'Edit did not load the stored name');
    await form().getByLabel('Name', { exact: true }).fill(renamed);
    await form().getByLabel('Description', { exact: true }).fill(description + ' edited');
    await navigate(() => form().getByRole('button', { name: 'Save', exact: true }).click());
    h.assert(sql.value(`SELECT CONCAT(name,'|',description,'|',creator,'|',active+0) FROM dashboard WHERE id=${id}`)
      === `${renamed}|${description} edited|${fixture.providerNo}|1`, 'Edit lost dashboard fields or metadata');
    h.assert(await row().locator('th').innerText() === renamed, 'Renamed dashboard is not listed');
  });
  for (const [button, active] of [['Inactivate', '0'], ['Activate', '1']]) {
    await s.step(`${button} persists after reload`, async () => {
      await navigate(() => row().getByRole('button', { name: button, exact: true }).click());
      await frame.goto(frame.url());
      h.assert(sql.value(`SELECT active+0 FROM dashboard WHERE id=${id}`) === active, `${button} did not persist`);
      h.assert((await row().locator('td').nth(1).innerText()).trim() === (active === '1' ? 'Active' : 'Inactive'),
        'Listed active state differs from the database');
    });
  }
  await s.step('stored dashboard markup stays literal in the list and editor', async () => {
    const literalName = renamed + ' <b>literal</b>';
    const literalDescription = 'Text & </textarea><b>literal</b>';
    sql.execute(`UPDATE dashboard SET name=${h.sqlString(literalName)}, description=${h.sqlString(literalDescription)} WHERE id=${id}`);
    await frame.goto(frame.url());
    h.assert(await row().locator('th').innerText() === literalName, 'Stored name was interpreted as markup');
    h.assert(await row().locator('b').count() === 0, 'Stored text created HTML elements');
    await navigate(() => row().getByRole('link', { name: 'Edit Dashboard', exact: true }).click());
    h.assert(await form().getByLabel('Name', { exact: true }).inputValue() === literalName, 'Edit name did not round-trip');
    h.assert(await form().getByLabel('Description', { exact: true }).inputValue() === literalDescription,
      'Textarea content was not escaped correctly');
    await h.screenshot(frame.page(), s.config.screenshotDir, 'dashboard-manager');
  });
  await s.step('a POST without the CSRF token is refused and changes nothing', async () => {
    const where = `creator=${h.sqlString(fixture.providerNo)} AND name LIKE ${h.sqlString(marker + '%')}`;
    const before = sql.value(`SELECT COUNT(*) FROM dashboard WHERE ${where}`);
    const response = await context.request.post(h.appUrl(s.config.baseUrl, '/web/dashboard/admin/DashboardManager'),
      { form: { operation: 'save', name: marker + ' CSRF probe' }, maxRedirects: 0 });
    await h.assertRefused(s, { response, table: 'dashboard', where, before, label: 'Dashboard save without CSRF token' });
  });
  await s.step('a GET save is refused and changes nothing', async () => {
    const url = new URL(h.appUrl(s.config.baseUrl, '/web/dashboard/admin/DashboardManager'));
    url.searchParams.set('operation', 'save');
    url.searchParams.set('name', marker + ' Get probe');
    const where = `creator=${h.sqlString(fixture.providerNo)} AND name LIKE ${h.sqlString(marker + '%')}`;
    const before = sql.value(`SELECT COUNT(*) FROM dashboard WHERE ${where}`);
    const response = await authenticatedProbe({ ...s, context }, url.href);
    await h.assertRefused(s, { response, table: 'dashboard', where, before, label: 'GET dashboard save' });
  });
}

if (require.main === module) runWorkflow('dashboard-manager', workflow, { openPatient: false });
module.exports = { workflow };
