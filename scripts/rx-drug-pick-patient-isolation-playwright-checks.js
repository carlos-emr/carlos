#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * A drug picked from the Rx search box is staged for the Rx page's own patient after another window
 * of the login showed another patient's allergies (issue #4673).
 *
 * User path: Rx for A ▸ Allergies for B in another window ▸ type a product in A's drug search box ▸
 * pick it from the suggestions ▸ Save Only; then the same on A's prescribing page (rx/prescribing)
 * after B's allergies were shown again. Each pick must be staged for A (its createNewRx request
 * names A) and saved to A's medications, with nothing for B.
 *
 * Why: the drug page named its patient in the pick request through the EL name demographicNo. On
 * routes whose action has no such property (rx/prescribing, and the stash-delete, delete and
 * clear-pending results, which the page now reaches only by AJAX) that resolved from the session,
 * where the allergies, waiting-list and registry pages wrote the patient they showed, so the drug
 * was staged and saved for that other patient. The page's own entry (rx/choosePatient) carried
 * the patient as an action property and is the regression half of this check.
 *
 * Needs carlos-emr-drugref (the suggestions come from DrugRef); without it the check reports SKIP.
 * RX_PICK_DRUG_TERM / RX_PICK_DRUG_NAME select the product (default LIPITOR 20 / LIPITOR 20MG).
 *
 * Fixtures: the workflow's FAKE patient (A) and a second FAKE patient (B). The drugs and
 * prescription rows saved for them are removed by patient key and asserted gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const DRUG_TERM = process.env.RX_PICK_DRUG_TERM || 'LIPITOR 20';
const DRUG_NAME = process.env.RX_PICK_DRUG_NAME || 'LIPITOR 20MG';
const q = h.sqlString;

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

/** The second patient, owned by this run; removed after its prescription rows. */
function createSecondPatient(s) {
  const { sql, marker, provider } = s;
  const patient = key(h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth,
      month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status,
      lastUpdateDate)
    VALUES (${q(marker)}, 'PatientBravo', '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'ON', 'ON', 'NR',
      NOW())`, 'second patient'), 'second patient');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(marker)}`) === '1', 'The second patient fixture changed owner');
    sql.execute(`DELETE FROM demographicExt WHERE demographic_no=${patient};
      DELETE FROM demographicArchive WHERE demographic_no=${patient};
      DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
      'The second patient was not removed');
  });
  return patient;
}

/** Every prescription row saved for the two owned patients. */
function ownPrescriptions(s, patients) {
  const { sql } = s;
  const list = patients.join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM prescription WHERE demographic_no IN (${list});
      DELETE FROM drugs WHERE demographic_no IN (${list});
      DELETE FROM DigitalSignature WHERE demographicId IN (${list}) AND moduleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId IN (${list}) AND moduleType='PRESCRIPTION')`) === '0',
    'Owned prescription or signature rows were not removed');
  });
}

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

async function workflow(s) {
  const { sql, context, config } = s;
  const A = key(s.patient, 'patient');
  const B = createSecondPatient(s);
  ownPrescriptions(s, [A, B]);

  /**
   * Pick DRUG_NAME from the page's search box suggestions; returns the patient the pick request
   * named. Only the first pick may report SKIP (no DrugRef); once it has shown that DrugRef answers,
   * a page that offers no suggestion is a failure.
   */
  let drugrefAnswered = false;
  async function pickDrug(rx) {
    const search = rx.locator('#searchString');
    await search.waitFor({ state: 'visible', timeout: TIMEOUT });
    const before = await rx.locator('[id^="drugName_"]').count();
    await search.fill('');
    await search.pressSequentially(DRUG_TERM, { delay: 30 });
    const suggestion = rx.locator('ul.ui-autocomplete li', { hasText: DRUG_NAME }).first();
    try {
      await suggestion.waitFor({ state: 'visible', timeout: TIMEOUT });
    } catch (error) {
      if (drugrefAnswered) throw new Error(`This page's drug search offered no "${DRUG_NAME}", which an earlier page offered`);
      throw new h.SkipCheck(`The drug search offered no "${DRUG_NAME}" (is carlos-emr-drugref installed and loaded?)`);
    }
    drugrefAnswered = true;
    const [staged] = await Promise.all([
      rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/WriteScript') && r.request().method() === 'POST'
        && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'createNewRx', { timeout: TIMEOUT }),
      suggestion.click(),
    ]);
    h.assert(staged.ok(), `Staging the picked drug answered HTTP ${staged.status()}`);
    await rx.locator('[id^="drugName_"]').nth(before).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert((await rx.locator('[id^="drugName_"]').nth(before).inputValue()).includes(DRUG_NAME),
      'The staged card does not name the picked drug');
    return new URLSearchParams(staged.request().postData() || '').get('demographicNo');
  }

  async function saveOnly(rx, expected, message) {
    const saved = rx.waitForResponse(response => response.request().method() === 'POST'
      && /\/rx\/WriteScript\?[^#]*parameterValue=updateSaveAllDrugs/.test(response.url()), { timeout: TIMEOUT });
    await rx.locator('#saveOnlyButton').click();
    h.assert((await saved).status() < 400, 'Save Only failed');
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${A} AND BN=${q(DRUG_NAME)}`, expected, message);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${B}`) === '0',
      'Saving A\'s prescription wrote a drug to B');
  }

  const allergies = await context.newPage();
  const rx = await context.newPage();
  await s.step('a drug picked on A\'s Rx page is staged and saved for A after B\'s allergies were shown', async () => {
    await h.gotoApp(rx, config.baseUrl, `/rx/choosePatient?demographicNo=${A}`);
    await settle(rx, 'A\'s Rx page');
    // Another window shows B's allergies after A's Rx page was opened.
    await h.gotoApp(allergies, config.baseUrl, `/rx/showAllergy?demographicNo=${B}`);
    await settle(allergies, 'B\'s allergies');
    const named = await pickDrug(rx);
    h.assert(named === A, `A's Rx page staged the picked drug for ${named === B ? 'B' : `patient ${named}`}`);
    await saveOnly(rx, '1', 'Save Only did not save the picked drug to A');
  });

  await s.step('the prescribing page, rendered after B\'s allergies were shown, stages its pick for A', async () => {
    // rx/prescribing renders the same page from an action that has no patient property, so the
    // page's EL name demographicNo fell back to the session, where the allergies page had put B.
    // No page links to the route any more (rx-legacy-null-fields drives it the same way).
    await h.gotoApp(allergies, config.baseUrl, `/rx/showAllergy?demographicNo=${B}`);
    await settle(allergies, 'B\'s allergies, shown again');
    await h.gotoApp(rx, config.baseUrl, `/rx/prescribing?demographicNo=${A}`);
    await settle(rx, 'A\'s prescribing page');
    const named = await pickDrug(rx);
    h.assert(named === A, `A's prescribing page staged the picked drug for ${named === B
      ? 'B, whose allergies another window showed' : `patient ${named}`}`);
    await saveOnly(rx, '2', 'Save Only did not save the second pick to A');
  });
  await rx.close();
  await allergies.close();
}

if (require.main === module) runWorkflow('rx-drug-pick-patient-isolation', workflow, { openPatient: true });
module.exports = { workflow };
