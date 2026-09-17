#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Search -> Master Record -> E-Chart -> Messenger tab (not the + composer).
 * Open two patients in one session and sort each inbox in both directions.
 * MESSENGER_CONTEXT_DEMOGRAPHICS defaults to 1,2; both demo patients must exist.
 * Creates uniquely named messages linked only to these test patients. Removes
 * its own messages/maps on success or failure; sends no messages to recipients.
 */
const { randomUUID } = require('crypto');
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  launchBrowser, login, newContext, readConfig, runCheck, wireStrictPage,
} = require('./lib/playwright-harness');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

async function main() {
  const config = readConfig();
  const demos = (process.env.MESSENGER_CONTEXT_DEMOGRAPHICS || '1,2').split(',');
  assert(demos.length === 2 && demos.every((id) => /^\d+$/.test(id)) && demos[0] !== demos[1],
    'MESSENGER_CONTEXT_DEMOGRAPHICS needs two different numeric demo patient IDs');
  const sql = createSqlRunner(config.mysql);
  const marker = `PW_MSG_CONTEXT_${randomUUID().replaceAll('-', '')}`;
  const ids = [];
  let browser;
  try {
    const patients = demos.map((id) => sql.rows(`SELECT last_name,first_name FROM demographic WHERE demographic_no=${id}`)[0]);
    if (patients.some((row) => !row)) throw new SkipCheck('both configured demo patients must exist');
    for (const demo of demos) {
      const id = sql.value(`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentbyNo,sentByLocation,type)
        VALUES(CURRENT_DATE,CURRENT_TIME,'UI context regression','${marker}_${demo}','UI test','999998',0,0);
        SELECT LAST_INSERT_ID();`);
      assert(/^\d+$/.test(id), 'message fixture insert returned no numeric ID');
      ids.push(id);
      sql.execute(`INSERT INTO msgDemoMap(messageID,demographic_no) VALUES(${id},${demo})`);
    }
    const recorder = createRecorder();
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    // Keep the eChart handoff inspectable until its destination has loaded.
    await context.addInitScript(() => { window.close = () => {}; });
    const schedule = await login(context, config, recorder);
    for (let index = 0; index < demos.length; index++) {
      const demo = demos[index];
      const { masterPage } = await openMasterRecord(context, schedule, recorder, {
        searchTerm: patients[index].join(','), preferredDemographicNo: demo, timeout: 45000,
      });
      const chart = await openChart(context, masterPage, recorder, 45000);
      await waitForNavbars(chart, 45000);
      // The click handler belongs to the heading, while the child link includes
      // formatting whitespace. Click its visible text as a clinician does.
      const [inbox] = await Promise.all([
        context.waitForEvent('page', { timeout: 45000 }),
        chart.locator('#leftNavBar a,#rightNavBar a').filter({ hasText: /^\s*Messenger\s*$/ }).first().click(),
      ]);
      wireStrictPage(inbox, `patient-inbox-${index}`, recorder);
      await inbox.waitForLoadState('domcontentloaded');
      async function assertPatientContext() {
        await assertNotErrorPage(inbox, 'patient Messenger inbox');
        const heading = await inbox.locator('h2').innerText();
        assert(patients[index].every((name) => heading.includes(name)), 'Messenger heading switched to another patient');
        const text = await inbox.locator('body').innerText();
        assert(text.includes(`${marker}_${demo}`), 'Messenger lost the current patient message');
        assert(!text.includes(`${marker}_${demos[1 - index]}`), 'Messenger displayed another patient message');
      }
      await assertPatientContext();
      for (const field of ['subject', 'date']) {
        for (let direction = 0; direction < 2; direction++) {
          const [response] = await Promise.all([
            inbox.waitForResponse((r) => r.request().isNavigationRequest() && r.frame() === inbox.mainFrame()),
            inbox.waitForEvent('domcontentloaded', { timeout: 45000 }),
            inbox.locator(`a[href*="orderby=${field}"]`).first().click(),
          ]);
          assert(response.status() === 200, `patient message sort returned HTTP ${response.status()}`);
          await inbox.waitForLoadState('domcontentloaded');
          await assertPatientContext();
        }
      }
      // Close the named Search window too, so the next iteration can observe
      // a fresh popup rather than an existing window gaining focus.
      for (const page of context.pages()) if (page !== schedule) await page.close();
    }
    assertStrictPage(recorder);
    return { patients: 2, sorts: 8 };
  } finally {
    try { if (browser) await browser.close(); }
    finally {
      try {
        if (ids.length) sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${ids.join(',')});
          DELETE FROM messagetbl WHERE messageid IN (${ids.join(',')})`);
      } finally { sql.dispose(); }
    }
  }
}
if (require.main === module) runCheck({ name: 'patient-messenger-context', run: main });
module.exports = { main };
