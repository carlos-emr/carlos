#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Provider Preferences: validate, add, reopen and remove an owned quick link.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const provider = h.sqlString(s.provider);
  const name = `${s.marker} & Quick Link`;
  const url = `https://example.invalid/${s.marker}?q=A%2BB&safe=1`;
  const table = 'ProviderPreferenceAppointmentScreenQuickLink';
  const baseline = s.sql.rows(`SELECT name,url FROM ${table} WHERE providerNo=${provider} ORDER BY name,url`);
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM ${table} WHERE providerNo=${provider} AND name=${h.sqlString(name)}`);
    h.assert(JSON.stringify(s.sql.rows(`SELECT name,url FROM ${table} WHERE providerNo=${provider} ORDER BY name,url`))
      === JSON.stringify(baseline), 'Quick-link cleanup changed another provider preference');
  });
  const open = () => s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'provider-quick-links');
  let page = await open();
  const add = () => page.locator('button[onclick="addQuickLink()"]');
  const remove = () => page.locator('div.scroll-box > div').filter({ hasText: name }).locator('input[type="button"]');
  const query = `SELECT url FROM ${table} WHERE providerNo=${provider} AND name=${h.sqlString(name)}`;
  await s.step('empty link is visibly rejected without a write', async () => {
    await page.locator('[name="quickLinkName"]').fill('');
    await page.locator('[name="quickLinkUrl"]').fill('');
    const dialogs = await h.withExpectedDialogs(page, () => add().click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'Missing quick-link fields did not trigger one validation alert');
    h.assert(s.sql.value(query) === '', 'Invalid quick link was persisted');
  });
  await s.step('add literal link text and preserve the exact URL', async () => {
    await page.locator('[name="quickLinkName"]').fill(name);
    await page.locator('[name="quickLinkUrl"]').fill(url);
    const dialogs = await h.withExpectedDialogs(page, async () => {
      await add().click();
      await expectValue(s.sql, query, url, 'Quick-link URL was not persisted exactly');
      await remove().waitFor();
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Quick-link add did not confirm navigation');
    h.assert((await remove().locator('..').innerText()).includes(name), 'Saved quick-link label changed its literal text');
  });
  await s.step('reopen the preference and remove only the owned link', async () => {
    await page.close(); page = await open();
    await remove().waitFor();
    h.assert((await remove().locator('..').innerText()).includes(name), 'Reopened quick link lost its label');
    await remove().click();
    await expectValue(s.sql, `SELECT COUNT(*) FROM ${table} WHERE providerNo=${provider} AND name=${h.sqlString(name)}`,
      '0', 'Quick-link removal did not persist');
    await remove().waitFor({ state: 'hidden' });
    await page.close(); page = await open();
    h.assert(await remove().count() === 0, 'Removed quick link returned when preferences reopened');
  });
}
if (require.main === module) runWorkflow('provider-quick-links', workflow, { openPatient: false });
module.exports = { workflow };
