#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Administration: search, edit and reopen an owned, unusable login record.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const username = s.marker.replace(/[^A-Za-z0-9]/g, '').slice(0, 26);
  const renamed = `${username}X`;
  let id;
  s.cleanup(() => {
    if (!id) return;
    h.assert(s.sql.value(`SELECT COUNT(*) FROM security WHERE security_no=${id} AND user_name IN (${h.sqlString(username)},${h.sqlString(renamed)})`) === '1',
      'Owned security fixture changed identity');
    s.sql.execute(`DELETE FROM security WHERE security_no=${id} AND user_name IN (${h.sqlString(username)},${h.sqlString(renamed)})`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM security WHERE security_no=${id}`) === '0', 'Security fixture cleanup failed');
  });
  id = s.sql.value(`INSERT INTO security (user_name,password,provider_no,pin,b_ExpireSet,b_RemoteLockSet,b_LocalLockSet,forcePasswordReset)
    VALUES (${h.sqlString(username)},'!disabled-coverage-fixture!',${h.sqlString(s.provider)},'!disabled!',0,0,0,0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'Security fixture was not created');
  const originalSecrets = s.sql.rows(`SELECT password,pin FROM security WHERE security_no=${id}`);
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'security-admin' });
  const menu = admin.locator('a[rel$="/admin/ViewSecuritySearchRecordsHtm"]');
  await menu.first().waitFor({ state: 'attached' });
  const panel = menu.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
  if (await panel.count() && !await panel.isVisible()) {
    await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
  }
  await menu.first().click();
  let page = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
  h.assert(page, 'Security search iframe did not load');
  const search = async value => {
    await page.locator('[name="search_mode"][value="search_username"]').check();
    await page.locator('[name="keyword"]').fill(value);
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.locator('input[type="submit"], button[type="submit"]').first().click()]);
  };
  await s.step('search isolates the owned username and masks credentials', async () => {
    await search(username);
    const row = page.locator('#tblResults tbody tr').filter({ hasText: username });
    h.assert(await row.count() === 1, 'Security search did not return exactly the owned login');
    h.assert(!((await row.innerText()).includes('!disabled')), 'Security search disclosed stored credentials');
    await row.getByRole('link', { name: username, exact: true }).click();
    await page.locator('input[name="user_name"]').waitFor();
    h.assert(await page.locator('[name="security_no"]').inputValue() === id, 'Security editor selected another record');
  });
  await s.step('rename login without changing its password or PIN', async () => {
    await page.locator('[name="user_name"]').fill(renamed);
    await page.locator('[name="subbutton"]').click();
    await expectValue(s.sql, `SELECT user_name FROM security WHERE security_no=${id}`, renamed, 'Security rename did not persist');
    h.assert(JSON.stringify(s.sql.rows(`SELECT password,pin FROM security WHERE security_no=${id}`)) === JSON.stringify(originalSecrets),
      'Renaming a login changed its unchanged password or PIN');
  });
  await s.step('reopen renamed login through security search', async () => {
    // Re-enter via the same visible administration menu after the result page.
    await menu.first().click();
    page = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
    h.assert(page, 'Security search iframe did not reopen');
    await search(renamed);
    await page.getByRole('link', { name: renamed, exact: true }).click();
    await page.locator('input[name="user_name"]').waitFor();
    h.assert(await page.locator('[name="user_name"]').inputValue() === renamed, 'Reopened security record lost the rename');
    h.assert(await page.locator('[name="provider_no"]').inputValue() === s.provider, 'Security update changed provider ownership');
  });
}
if (require.main === module) runWorkflow('admin-security-workflow', workflow, { openPatient: false });
module.exports = { workflow };
