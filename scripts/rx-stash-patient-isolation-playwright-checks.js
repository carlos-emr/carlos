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

/*
 * The prescription stash saves exactly what the prescriber sees, for the patient on the page
 * (#3908: issues #3869, #3870, #3872 and #3875; adapted from MagentaHealth/Open-O and the CARLOS
 * contributors credited on that PR).
 *
 *   1. Closing one of two staged drugs with its X saves only the other. The X sent the drug id
 *      rather than the card's stash key, so the server kept the closed card and saved it anyway.
 *   2. With Rx open for a second patient in another tab, saving the first tab saves to the first
 *      patient. The module kept ONE session bean, so opening the second patient's Rx switched the
 *      first tab's stash to the second patient: a medication staged for one patient was saved to
 *      another's chart.
 *   3. Unticking ReRx removes the staged card for that drug, and the source prescription is not
 *      archived by a ReRx that was never saved.
 *   4. The Rx Print patient chooser renders its search form for a session that has not opened
 *      any patient's Rx yet (it runs first, before this check opens one). It resolved a per-patient
 *      Rx bean and sent such a session to error.html before the form.
 *   5. Re-prescribing a saved drug from the static-script page stages it. The page read its CSRF
 *      token while <head> parsed, before the token existed, so the POST was refused.
 *
 * The check owns two synthetic patients and every drug row it saves (removed afterwards). It
 * stages custom drugs through the Custom Drug button, so it needs no DrugRef lookup.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function openRx(session, demographicNo) {
  const page = await session.context.newPage();
  await h.gotoApp(page, session.config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await h.assertNotErrorPage(page, 'Rx page');
  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  return page;
}

/** Stage a custom drug the prescriber's way; returns the card's stash key (random id). */
async function stageCustomDrug(page, name) {
  const before = await page.locator("[id^='drugName_']").evaluateAll((els) => els.map((el) => el.id));
  await page.locator('#searchString').fill(name);
  // The Custom Drug button confirms first; accept it like the prescriber does.
  await h.withExpectedDialogs(page, () => page.locator('#customDrug').click(), { accept: true });
  await page.waitForFunction((count) => document.querySelectorAll("[id^='drugName_']").length > count,
    before.length, { timeout: 30000 });
  const after = await page.locator("[id^='drugName_']").evaluateAll((els) => els.map((el) => el.id));
  const added = after.filter((id) => !before.includes(id));
  h.assert(added.length === 1, `staging ${name} added ${added.length} cards`);
  return added[0].split('_')[1];
}

/**
 * "Save Only" saves the stash over AJAX. Callers wait on the database (expectValue polls), which
 * is the outcome that matters; racing waitForLoadState against the click could not tell the
 * post-save page from the pre-save one.
 */
async function saveOnly(page) {
  // Wait for the save itself so a refused or failed save is reported with its status and the
  // server's answer, not only as a missing row once the database poll times out.
  const saved = page.waitForResponse((response) => response.request().method() === 'POST'
    && /\/rx\/WriteScript\?[^#]*parameterValue=updateSaveAllDrugs/.test(response.url()), { timeout: 30000 });
  await page.locator('#saveOnlyButton').click();
  const response = await saved;
  if (response.status() >= 400) {
    const body = (await response.text().catch(() => '')).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 300);
    throw new Error(`Save Only answered HTTP ${response.status()}: ${body}`);
  }
}

function drugsFor(sql, demographicNo, marker) {
  return sql.rows(`SELECT customName, archived FROM drugs WHERE demographic_no=${demographicNo}
    AND customName LIKE ${h.sqlString(`${marker}%`)} ORDER BY drugid`);
}

async function workflow(session) {
  const { sql, patient, provider, marker } = session;
  const second = sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
    date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (${h.sqlString(marker)}, 'Second', '1975', '03', '04', 'M', 'AC', ${h.sqlString(provider)},
    'ON', 'ON', 'NR', NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(second), 'the second patient fixture was not created');
  const clearDrugs = (demo) => sql.execute(`DELETE FROM prescription WHERE demographic_no=${demo}
    AND script_no IN (SELECT script_no FROM drugs WHERE demographic_no=${demo}
      AND customName LIKE ${h.sqlString(`${marker}%`)});
    DELETE FROM drugs WHERE demographic_no=${demo} AND customName LIKE ${h.sqlString(`${marker}%`)}`);
  session.cleanup(() => sql.execute(`DELETE FROM demographic WHERE demographic_no=${second}
    AND last_name=${h.sqlString(marker)}`));
  session.cleanup(() => clearDrugs(second));
  session.cleanup(() => clearDrugs(patient));

  // First, while this login has not opened any patient's Rx.
  await session.step('the Print patient chooser renders with no Rx patient open', async () => {
    const page = await session.context.newPage();
    await h.gotoApp(page, session.config.baseUrl, '/rx/ViewPrint');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'Rx Print patient chooser');
    h.assert(new URL(page.url()).pathname.endsWith('/rx/ViewPrint'),
      'the Rx Print patient chooser redirected away without an Rx patient');
    await page.locator('form[action$="/rx/searchPatient"] input[name="surname"]')
      .waitFor({ state: 'visible', timeout: 20000 });
    await page.close();
  });

  await session.step('closing one staged drug saves only the other', async () => {
    const rx = await openRx(session, patient);
    const closed = await stageCustomDrug(rx, `${marker}-A`);
    await stageCustomDrug(rx, `${marker}-B`);
    // The X on the card, as the prescriber clicks it.
    await rx.locator(`#set_${closed} a[onclick^='removePrescribingDrug']`).first().click();
    await rx.locator(`#drugName_${closed}`).waitFor({ state: 'detached', timeout: 10000 });
    await saveOnly(rx);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(`${marker}-B`)}`, '1', 'the drug left on the pad was not saved');
    h.assert(!drugsFor(sql, patient, marker).some(([name]) => name === `${marker}-A`),
      'the drug closed with its X was saved anyway');
    await rx.close();
  });

  await session.step('a second patient opened in another tab does not capture the first tab\'s stash', async () => {
    const first = await openRx(session, patient);
    await stageCustomDrug(first, `${marker}-C`);
    const other = await openRx(session, second);
    await saveOnly(first);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(`${marker}-C`)}`, '1', 'the first tab\'s drug was not saved to the first patient');
    h.assert(drugsFor(sql, second, marker).length === 0,
      'a drug staged for one patient was saved to the patient open in the other tab');
    await other.close();
    await first.close();
  });

  await session.step('unticking ReRx removes its card and does not archive the source', async () => {
    const rx = await openRx(session, patient);
    const source = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(`${marker}-B`)} AND archived=0`);
    h.assert(/^\d+$/.test(source), 'the saved drug is not available to re-prescribe');
    const box = rx.locator(`#reRxCheckBox_${source}`);
    await box.waitFor({ state: 'attached', timeout: 20000 });
    await box.check();
    await rx.locator('#reRxConfirmBox input[name="stage"]').click();
    const card = rx.locator(`fieldset[data-drug-ref-id="${source}"]`);
    await card.waitFor({ state: 'visible', timeout: 30000 });
    await rx.locator(`#reRxCheckBox_${source}`).uncheck();
    await card.waitFor({ state: 'detached', timeout: 10000 });
    await rx.close();
    h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${source}`) === '0',
      'a ReRx that was never saved archived its source prescription');
  });

  await session.step('re-prescribing from the static-script page stages the drug', async () => {
    const source = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(`${marker}-B`)} AND archived=0`);
    h.assert(/^\d+$/.test(source), 'the saved drug is not available to re-prescribe');
    const page = await session.context.newPage();
    await h.gotoApp(page, session.config.baseUrl, `/rx/ViewStaticScript2?demographicNo=${patient}`
      + `&cn=${encodeURIComponent(`${marker}-B`)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'static-script page');
    const button = page.locator(`input[value="Represcribe"][onclick*="'${source}'"]`);
    await button.waitFor({ state: 'visible', timeout: 20000 });
    // A refused stage raises an alert (failing the strict page) instead of opening the search.
    await Promise.all([
      page.waitForURL(/\/rx\/searchDrug/, { timeout: 30000 }),
      button.click(),
    ]);
    await page.close();
    const rx = await openRx(session, patient);
    await rx.locator(`fieldset[data-drug-ref-id="${source}"]`).waitFor({ state: 'visible', timeout: 30000 });
    await rx.close();
  });
}

if (require.main === module) runWorkflow('rx-stash-patient-isolation', workflow);
module.exports = { workflow, openRx, stageCustomDrug };
