#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  let script;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM favorites WHERE provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)};
      DELETE FROM drugs WHERE demographic_no=${s.patient} AND customName=${h.sqlString(s.marker)}`);
    if (script) s.sql.execute(`DELETE FROM prescription WHERE script_no=${script} AND demographic_no=${s.patient}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM favorites WHERE provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)}`) === '0', 'Owned favorite cleanup failed');
  });
  script = s.sql.value(`INSERT INTO prescription(provider_no,demographic_no,date_prescribed,date_printed,textView,lastUpdateDate)
    VALUES(${h.sqlString(s.provider)},${s.patient},CURDATE(),CURDATE(),'Synthetic prescription',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9][0-9]*$/.test(script), 'Owned prescription was not created');
  const drug = s.sql.value(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,BN,customName,special,script_no,create_date,lastUpdateDate)
    VALUES(${h.sqlString(s.provider)},${s.patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 7 DAY),CURDATE(),${h.sqlString(s.marker)},${h.sqlString(s.marker)},'One tablet daily',${script},NOW(),NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9][0-9]*$/.test(drug), 'Owned drug was not created');
  const page = await s.context.newPage();
  await s.step('load a prescription with schema-legal NULL dose, flags, repeats and quantity', async () => {
    await page.goto(`${s.config.baseUrl}/rx/choosePatient?demographicNo=${s.patient}`);
    await page.locator('#searchString').waitFor({ state: 'visible' });
    await page.waitForLoadState('networkidle');
  });
  await s.step('save the legacy prescription as a favorite through its history control', async () => {
    await page.goto(`${s.config.baseUrl}/rx/ViewStaticScript2?cn=${encodeURIComponent(s.marker)}`);
    const button = page.locator(`[onclick*="addFavorite2(${drug},"]`);
    await button.waitFor({ state: 'visible' });
    const dialogs = await h.withExpectedDialogs(page, async () => {
      const [response] = await Promise.all([
        page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rx/addFavorite2') && r.request().method() === 'POST'),
        button.click(),
      ]);
      h.assert(response.ok(), 'Saving the legacy favorite failed');
    }, { promptText: s.marker });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Favorite naming prompt was not shown');
    await expectValue(s.sql, `SELECT COUNT(*) FROM favorites WHERE provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)} AND \`repeat\`=0 AND special='One tablet daily'`, '1', 'Legacy favorite did not preserve instructions and default repeats');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE drugid=${drug} AND demographic_no=${s.patient} AND \`repeat\` IS NULL AND quantity IS NULL`) === '1', 'Reading the legacy drug rewrote its nullable fields');
  });
  await s.step('stage re-prescribing only after both protected history requests succeed', async () => {
    await page.waitForLoadState('networkidle');
    await page.locator('input[value="Represcribe"]').click();
    await page.waitForURL('**/rx/prescribing');
    await page.waitForLoadState('networkidle');
    await page.locator('[id^="quantity_"]').first().waitFor({ state: 'attached' });
    h.assert(s.sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${s.patient}`) === '1',
      'Staging re-prescription unexpectedly saved another prescription');
  });
}
if (require.main === module) runWorkflow('rx-legacy-null-fields', workflow);
module.exports = { workflow };
