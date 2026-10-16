#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Access Control (admin/GroupNoAcl, groupnoacl.jsp): which providers a schedule
 * group is restricted from (MyGroupAccessRestriction). No browser check opened the page.
 *
 * User path: Schedule ▸ Administration ▸ Access Control ▸ choose a group ▸ tick or untick a
 * provider ▸ Save.
 * Asserted: the page lists the owned group; choosing it lists the providers with none restricted;
 * ticking one and saving stores exactly that restriction for the group, and the page shows it ticked
 * after the reload; unticking and saving removes it; a GET carrying the save (method=save) is refused
 * with 405 and stores nothing.
 *
 * Fixtures: an owned schedule group (mygroup, named for the run) whose one member is the test login.
 * The restricted provider is an existing FAKE- demo provider; only rows of the owned group are ever
 * written. Cleanup deletes the owned group and its restrictions and asserts both are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openAdminPage, frameAt } = require('./lib/admin-frame');

const ROUTE = 'admin/GroupNoAcl';

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const group = `FK${marker.slice(-8)}`;
  // The page lists ProviderDao.getActiveProviders(): status 1 and a provider number not starting "-".
  const restricted = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND last_name LIKE 'FAKE-%'
    AND provider_no NOT LIKE '-%' AND provider_no<>${q(provider)} ORDER BY provider_no LIMIT 1`);
  h.assert(restricted, 'The install has no active FAKE- provider to restrict');
  s.cleanup(() => {
    sql.execute(`DELETE FROM MyGroupAccessRestriction WHERE myGroupNo=${q(group)};
      DELETE FROM mygroup WHERE mygroup_no=${q(group)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM MyGroupAccessRestriction WHERE myGroupNo=${q(group)})
      + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(group)})`) === '0', 'The owned group or its restrictions remain');
  });
  sql.execute(`INSERT INTO mygroup (mygroup_no, provider_no, last_name, first_name, vieworder)
    SELECT ${q(group)}, provider_no, LEFT(last_name, 30), LEFT(first_name, 30), '1' FROM provider WHERE provider_no=${q(provider)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(group)}`) === '1', 'The owned group was not created');
  const restrictions = () => sql.value(`SELECT COALESCE(GROUP_CONCAT(providerNo ORDER BY providerNo), '')
    FROM MyGroupAccessRestriction WHERE myGroupNo=${q(group)}`);
  const box = frame => frame.locator(`input[type="checkbox"][name="data"][value="${restricted}"]`);

  let admin;
  let frame;
  // Changing the group and Save both post the page's form back to itself.
  async function post(action) {
    await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(`/${ROUTE}`), { timeout: 30000 }),
      action(),
    ]);
    frame = await frameAt(admin, ROUTE);
    await frame.waitForLoadState('load');
    await h.assertNotErrorPage(frame, 'Access Control');
  }

  await s.step('Access Control lists the owned group', async () => {
    ({ admin, frame } = await openAdminPage(s, ROUTE, { label: 'access-control' }));
    h.assert(await frame.locator(`#chosen_group option[value="${group}"]`).count() === 1, 'The owned group is not offered');
  });

  await s.step('choosing the owned group lists the providers with none restricted', async () => {
    await post(() => frame.locator('#chosen_group').selectOption(group));
    h.assert(await frame.locator('#chosen_group').inputValue() === group, 'The page did not keep the chosen group');
    h.assert(await box(frame).count() === 1, 'The FAKE provider is not listed');
    h.assert(await frame.locator('input[type="checkbox"][name="data"]:checked').count() === 0, 'A provider is already restricted for the new group');
  });

  await s.step('ticking a provider and saving stores that one restriction, and the page shows it ticked', async () => {
    await box(frame).check();
    await post(() => frame.locator('input[type="button"][name="Submit"]').click());
    await expectValue(sql, `SELECT COALESCE(GROUP_CONCAT(providerNo ORDER BY providerNo), '') FROM MyGroupAccessRestriction
      WHERE myGroupNo=${q(group)}`, restricted, 'Save did not store exactly the ticked restriction');
    if (await frame.locator('#chosen_group').inputValue() !== group) {
      await post(() => frame.locator('#chosen_group').selectOption(group));
    }
    h.assert(await box(frame).isChecked(), 'After the save the provider is not shown restricted');
  });

  await s.step('unticking and saving removes the restriction', async () => {
    await box(frame).uncheck();
    await post(() => frame.locator('input[type="button"][name="Submit"]').click());
    await expectValue(sql, `SELECT COUNT(*) FROM MyGroupAccessRestriction WHERE myGroupNo=${q(group)}`, '0',
      'Save did not remove the unticked restriction');
  });

  await s.step('a GET carrying the save is refused with 405 and stores nothing', async () => {
    const params = new URLSearchParams({ method: 'save', chosen_group: group, data: restricted });
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, `/${ROUTE}?${params}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET save answered ${response.status()}, not 405`);
    h.assert(restrictions() === '', 'A refused GET stored a restriction');
  });
}

if (require.main === module) runWorkflow('admin-group-access-control', workflow, { openPatient: false });
module.exports = { workflow };
