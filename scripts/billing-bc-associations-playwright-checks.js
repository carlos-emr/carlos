#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
async function workflow(s) {
  if (s.sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billing_trayfees'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required');
  }
  const ids = []; const codes = [];
  s.cleanup(() => {
    if (!ids.length) return;
    s.sql.execute(`DELETE FROM billing_trayfees WHERE billingServiceNo IN (${ids.join(',')}) OR billingServiceTrayNo IN (${ids.join(',')})`);
    s.sql.execute(`DELETE FROM billingservice WHERE billingservice_no IN (${ids.join(',')}) AND description=${h.sqlString(s.marker)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM billingservice WHERE billingservice_no IN (${ids.join(',')})`) === '0', 'BC service fixtures were not removed');
  });
  for (let i = 0; i < 3; i++) {
    let code;
    do { code = String(randomInt(90000, 99999)); } while (s.sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(code)}`) !== '0');
    codes.push(code);
    const id = s.sql.value(`INSERT INTO billingservice (service_code,description,value,billingservice_date,region)
      VALUES (${h.sqlString(code)},${h.sqlString(s.marker)},'1.00',CURDATE(),'BC'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'BC service fixture was not created'); ids.push(id);
  }
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-association-admin' });
  const link = admin.locator('a[rel$="/billing/CA/BC/supServiceCodeAssocAction"]');
  const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
  if (!await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
  await link.click();
  const page = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
  h.assert(page, 'BC association editor did not load');
  const count = `SELECT COUNT(*) FROM billing_trayfees WHERE billingServiceNo=${ids[0]}`;
  const save = async () => {
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.locator('[name="submitButton"]').click()]);
  };
  await s.step('invalid code is visibly rejected without persistence', async () => {
    await page.locator('#primaryCode').fill(''); await page.locator('#secondaryCode').fill(codes[1]); await save();
    await page.locator('.errorMessage').first().waitFor();
    h.assert(s.sql.value(count) === '0', 'Invalid association was persisted');
  });
  await s.step('create and reopen the owned association', async () => {
    await page.locator('#primaryCode').fill(codes[0]); await page.locator('#secondaryCode').fill(codes[1]); await save();
    await expectValue(s.sql, count, '1', 'Valid BC association was not saved');
    h.assert(s.sql.value(`SELECT billingServiceTrayNo FROM billing_trayfees WHERE billingServiceNo=${ids[0]}`) === ids[1], 'BC association saved the wrong tray fee');
    await page.goto(page.url(), { waitUntil: 'domcontentloaded' });
    const row = page.locator('table.displayGrid tbody tr').filter({ hasText: codes[0] });
    h.assert(await row.count() === 1, 'Saved BC association was not listed');
    await row.getByRole('link', { name: 'Edit', exact: true }).click();
    h.assert(await page.locator('#secondaryCode').inputValue() === codes[1], 'Reopened association lost its fee');
  });
  await s.step('update then delete only the owned association', async () => {
    await page.locator('#secondaryCode').fill(codes[2]); await save();
    await expectValue(s.sql, `SELECT billingServiceTrayNo FROM billing_trayfees WHERE billingServiceNo=${ids[0]}`, ids[2], 'BC association edit did not persist');
    const row = page.locator('table.displayGrid tbody tr').filter({ hasText: codes[0] });
    await h.withExpectedDialogs(admin, async () => {
      await row.getByRole('link', { name: 'Delete', exact: true }).click();
      await expectValue(s.sql, count, '0', 'BC association delete did not persist');
    }, { accept: true });
  });
}
if (require.main === module) runWorkflow('billing-bc-associations', workflow, { openPatient: false });
module.exports = { workflow };
