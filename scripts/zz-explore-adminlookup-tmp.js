#!/usr/bin/env node
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
(async () => {
  const config = h.readConfig();
  const browser = await h.launchBrowser(config);
  const context = await h.newContext(browser, config);
  const rec = h.createRecorder();
  const schedule = await h.login(context, config, rec);
  console.log('facility_message html:', (await schedule.locator('#facility_message').innerHTML()).slice(0, 300));
  const {page: admin} = await clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(), {context, recorder: rec, label: 'x', timeout: 20000});
  console.log(admin.url());
  const heads = await admin.locator('.accordion-button').allInnerTexts();
  console.log('accordions', heads.join(' | '));
  const links = await admin.locator('a[rel*="ookup"], a[rel*="Facility"], a[rel*="DefaultEnc"]').evaluateAll(as => as.map(a => a.textContent.trim() + ' -> ' + a.getAttribute('rel')));
  console.log(links.join('\n'));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
