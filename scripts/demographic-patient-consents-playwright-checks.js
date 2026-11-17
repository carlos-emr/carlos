#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Master Record patient consents (the consent module: USE_NEW_PATIENT_CONSENT_MODULE and privateConsentEnabled, both
 * true in the shipped carlos.properties; the ON and BC seeds carry two consent types, of which the electronic
 * communication consent is active).
 * User path: Schedule ▸ Search ▸ Master Record ▸ Edit ▸ a consent type's Opt In ▸ Update Record; Edit ▸ Opt Out ▸
 * Update Record; Edit ▸ Clear ▸ confirm ▸ Update Record; Edit ▸ Opt In ▸ Update Record ▸ point at the consent's name.
 * Asserts: Opt In stores one live Consent row (explicit, not opted out, consent date today) and the read view shows it
 * Consented; Opt Out flips the same row and stamps its opt-out date, and both the read view and the reopened Edit form
 * show Opted Out; a GET to Update Record carrying a consent choice is refused and stores nothing; Clear marks the row
 * deleted and the read view no longer lists the consent; a consent opted in again shows its description in the popup
 * when pointed at, without a script error.
 * Requires: the server running with USE_NEW_PATIENT_CONSENT_MODULE=true and privateConsentEnabled=true.
 * Fixtures: the runWorkflow FAKE- patient; its Consent rows (and the update's archive rows) are removed in cleanup.
 */
const { authenticatedProbe } = require('./lib/authenticated-probe');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { prepareOwnedMasterPostal, assertOwnedMasterPostal } = require('./lib/owned-master-postal');

const TIMEOUT = 20000;
const TYPE = 'electronic_communication_consent'; // the seeds ship it active; the demonstration consent is inactive

async function openEdit(master) {
  await master.locator('#editBtn').click();
  await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
  await master.locator('#updateButton').waitFor({ state: 'visible', timeout: TIMEOUT });
}

async function update(s) {
  await assertOwnedMasterPostal(s);
  const { master } = s;
  await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Update Record' });
}

async function workflow(s) {
  const { sql, patient, master, config } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM Consent WHERE demographic_no=${patient};
      DELETE FROM demographicExtArchive WHERE demographic_no=${patient};
      DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM Consent WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient})`) === '0',
    'The owned patient\'s consent, custom-field or extension archive rows were not removed');
  });
  const [typeId, typeName, typeDescription] = sql.rows(`SELECT id, name, IFNULL(description,'') FROM consentType
    WHERE type=${h.sqlString(TYPE)} AND active=1`)[0] || [];
  h.assert(typeId, `The consent type ${TYPE} is not active in this database`);
  const consents = () => sql.rows(`SELECT id, explicit, optout, IFNULL(DATE(consent_date),''), IFNULL(DATE(optout_date),''), deleted
    FROM Consent WHERE demographic_no=${patient} AND consent_type_id=${typeId} ORDER BY id`);
  const today = sql.value('SELECT CURDATE()');
  const consentSection = () => master.locator('#consent');
  let consentId;

  await prepareOwnedMasterPostal(s);

  await s.step('Opt In stores one consent dated today and the read view shows it Consented', async () => {
    await openEdit(master);
    const optIn = master.locator(`#optin_${TYPE}`);
    h.assert(await optIn.count() === 1, 'The Edit form offers no consent choices: run the server with the consent module on');
    h.assert(!(await optIn.isChecked()) && !(await master.locator(`#optout_${TYPE}`).isChecked()), 'A new patient opened with a consent choice');
    await optIn.check();
    await update(s);
    const rows = consents();
    h.assert(rows.length === 1, `Opt In stored ${rows.length} consent rows`);
    [consentId] = rows[0];
    h.assert(JSON.stringify(rows[0].slice(1)) === JSON.stringify(['1', '0', today, '', '0']),
      `The stored consent is ${JSON.stringify(rows[0].slice(1))}`);
    const shown = (await consentSection().innerText()).replace(/\s+/g, ' ');
    h.assert(shown.includes(typeName) && shown.includes(`Consented:${today}`), `The read view shows: ${shown.slice(0, 200)}`);
  });

  await s.step('Opt Out flips the same consent with its opt-out date, shown on the read view and the reopened Edit form', async () => {
    await openEdit(master);
    h.assert(await master.locator(`#optin_${TYPE}`).isChecked(), 'The Edit form lost the stored Opt In');
    await master.locator(`#optout_${TYPE}`).check();
    await update(s);
    const rows = consents();
    h.assert(rows.length === 1 && rows[0][0] === consentId, 'Opt Out did not change the same consent row');
    h.assert(rows[0][2] === '1' && rows[0][4] === today && rows[0][5] === '0', `The opted-out consent is ${JSON.stringify(rows[0].slice(1))}`);
    const shown = (await consentSection().innerText()).replace(/\s+/g, ' ');
    h.assert(shown.includes(`Opted Out:${today}`), `The read view shows: ${shown.slice(0, 200)}`);
    await openEdit(master);
    h.assert(await master.locator(`#optout_${TYPE}`).isChecked(), 'The reopened Edit form does not show Opt Out');
    h.assert((await master.locator(`#consentDate_${TYPE}`).innerText()).includes(`Opted Out:${today}`), 'The Edit form does not show the opt-out date');
  });

  await s.step('a GET to Update Record carrying a consent choice is refused and stores nothing', async () => {
    const before = JSON.stringify(consents());
    const url = new URL('demographic/DemographicUpdate', `${config.baseUrl.href.replace(/\/$/, '')}/`);
    url.searchParams.set('demographic_no', patient);
    url.searchParams.set(TYPE, '0');
    const response = await authenticatedProbe(s, url.href, { maxRedirects: 0 });
    h.assert(response.status() === 405 || response.status() === 403, `GET Update Record answered ${response.status()}`);
    h.assert(JSON.stringify(consents()) === before, 'A GET to Update Record changed the consent');
  });

  await s.step('Clear, once confirmed, marks the consent deleted and the read view no longer lists it', async () => {
    const dialogs = await h.withExpectedDialogs(master, () => master.locator(`input[name="clearRadio_${TYPE}_btn"]`).click());
    h.assert(dialogs.length === 1, 'Clear did not ask for confirmation');
    h.assert(!(await master.locator(`#optin_${TYPE}`).isChecked()) && !(await master.locator(`#optout_${TYPE}`).isChecked()),
      'Clear left a consent choice ticked');
    h.assert(await master.locator(`#deleteConsent_${TYPE}`).inputValue() === '1', 'Clear did not mark the stored consent for deletion');
    await update(s);
    const rows = consents();
    h.assert(rows.length === 1 && rows[0][0] === consentId && rows[0][5] === '1', `After Clear the consent rows are ${JSON.stringify(rows)}`);
    const shown = (await consentSection().innerText()).replace(/\s+/g, ' ');
    h.assert(!shown.includes(typeName), `The read view still lists the cleared consent: ${shown.slice(0, 200)}`);
  });

  await s.step('a consent opted in again shows its description when pointed at, without a script error', async () => {
    await openEdit(master);
    await master.locator(`#optin_${TYPE}`).check();
    await update(s);
    const live = consents().filter(row => row[5] === '0');
    h.assert(live.length === 1 && live[0][2] === '0', `Opting in again left ${JSON.stringify(live)}`);
    const name = consentSection().locator('.popup.label').filter({ hasText: typeName }).first();
    await name.hover();
    await master.waitForTimeout(500);
    h.assertStrictPage(s.recorder);
    // js/popup.js shows its text in div.pup: it must be the consent's description.
    const popup = master.locator('div.pup');
    await popup.waitFor({ state: 'visible', timeout: TIMEOUT });
    const shown = (await popup.innerText()).replace(/\s+/g, ' ').trim();
    h.assert(shown === typeDescription.replace(/\s+/g, ' ').trim(), `The popup shows "${shown.slice(0, 120)}", not the consent's description`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-patient-consents', workflow, { openPatient: true });
