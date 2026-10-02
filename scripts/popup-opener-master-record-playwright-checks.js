#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Popup -> window.opener value pickers reached from the Master Record (risk sweep
 * "lost popup openers"; coverage plan §2.4 demographic record, opener contracts).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Edit ▸ Referral Doctor "Search #"
 * (billing/CA/ON/ViewSearchRefDoc) ▸ pick ▸ Update Record; Master Record ▸ Other
 * Contacts ▸ Manage Contacts (demographic/Contact?method=manage) ▸ [Add] personal and
 * professional rows ▸ Search: patient (demographic/DemographicSearch caisi mode),
 * external contact (ViewContactSearch), provider (ViewReceptionistFindProvider
 * custom mode), professional specialist (ViewProfessionalSpecialistSearch) ▸ Submit.
 * Asserts each picked value lands in the parent form (name AND id) and the picker
 * closes itself, the saves reach demographic.family_doctor and DemographicContact,
 * and the contacts Submit closes the popup and reloads the Master Record listing them.
 * A failing pick names every document hop of the popup with its COOP header.
 * Fixtures: the owned FAKE- patient, a second owned FAKE- patient, one marker Contact
 * row and one marker professionalSpecialists row (unused 6-digit referral number).
 * Cleanup deletes DemographicContact rows of the two owned patients, then the owned
 * contact, specialist and second patient, and asserts they are gone.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, markOpener } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { documentChain, pickInPopup, lostOpenerMessage, assertId } = require('./lib/popup-opener-helpers');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const chain = documentChain(s.context);
  const ownedPatients = () => [s.patient, second].filter(Boolean).join(',');
  let second;
  let contactId;
  let specialistId;
  s.cleanup(() => {
    if (second) {
      sql.execute(`DELETE FROM DemographicContact WHERE demographicNo IN (${ownedPatients()})
        OR (type=1 AND contactId IN (${ownedPatients().split(',').map(q).join(',')}))`);
    } else {
      sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${s.patient}`);
    }
    sql.execute(`DELETE FROM Contact WHERE lastName=${q(marker)}`);
    if (specialistId) sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${specialistId} AND lName=${q(marker)}`);
    if (second) {
      sql.execute(`DELETE FROM demographicArchive WHERE demographic_no=${second};
        DELETE FROM demographic WHERE demographic_no=${second} AND last_name=${q(marker)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo IN (${ownedPatients()}))
      + (SELECT COUNT(*) FROM Contact WHERE lastName=${q(marker)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(marker)})
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${q(marker)} AND demographic_no<>${s.patient})`) === '0',
    'Owned contact, specialist or second-patient rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${s.patient}`) === '0',
    'The owned patient unexpectedly has contact associations');
  second = assertId(sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,
      date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(marker)},'Relative','1975','03','04','M','AC',${q(s.provider)},'ON','ON','NR',NOW());
    SELECT LAST_INSERT_ID()`), 'The second owned patient was not created');
  contactId = assertId(sql.value(`INSERT INTO Contact (type,lastName,firstName,deleted,updateDate)
    VALUES ('Contact',${q(marker)},'External',0,NOW()); SELECT LAST_INSERT_ID()`), 'The owned contact was not created');
  const proContactId = assertId(sql.value(`INSERT INTO Contact (type,lastName,firstName,systemId,deleted,updateDate)
    VALUES ('ProfessionalContact',${q(marker)},'Professional','FAKE',0,NOW()); SELECT LAST_INSERT_ID()`),
  'The owned professional contact was not created');
  let referralNo;
  do { referralNo = String(randomInt(800000, 899999)); }
  while (sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE referralNo=${q(referralNo)}`) !== '0');
  specialistId = assertId(sql.value(`INSERT INTO professionalSpecialists (fName,lName,referralNo,specType,lastUpdated,
      institutionId,departmentId,hideFromView,deleted)
    VALUES ('Referrer',${q(marker)},${q(referralNo)},'FAKE',NOW(),0,0,0,0); SELECT LAST_INSERT_ID()`),
  'The owned specialist was not created');

  const master = s.master;
  const referralName = `${marker},Referrer`;

  let contacts;
  // Opens a contact-row picker, searches, picks, and returns null on success or the
  // failure text; the caller asserts (the last step collects several before failing).
  const tryPicker = async (label, rowSearch, popupReady, keyword, pick, fields, expected) => {
    const popup = await s.popup(contacts, rowSearch, label);
    await popup.locator(popupReady).first().waitFor();
    if (keyword) {
      const box = popup.locator('form[name="titlesearch"] input[name="keyword"]');
      await box.fill(keyword);
      await Promise.all([popup.waitForNavigation({ waitUntil: 'domcontentloaded' }), box.press('Enter')]);
    }
    const closed = await pickInPopup(popup, popup.locator(pick));
    const id = await contacts.locator(`[name="${fields}.contactId"]`).inputValue();
    const name = await contacts.locator(`[name="${fields}.contactName"]`).inputValue();
    let failure = null;
    if (id !== expected.id || !name.toLowerCase().includes(expected.name.toLowerCase())) {
      failure = closed ? `${label}: the picker closed but the contact row does not hold the picked id and name`
        : await lostOpenerMessage(label, popup, chain);
    } else if (!closed) failure = `${label}: the picker filled the row but did not close itself`;
    if (!popup.isClosed()) await popup.close();
    return failure;
  };
  const pickers = async (...args) => {
    const failure = await tryPicker(...args);
    h.assert(!failure, failure);
  };
  /** Click [Add] under a contact list and return the new row's field prefix. */
  const addRow = async (kind) => {
    const counter = contacts.locator(`#${kind}_num`);
    const before = Number(await counter.inputValue());
    await contacts.locator(`a[onclick="${kind === 'contact' ? 'addContact' : 'addProContact'}();"]`).click();
    const prefix = `${kind}_${before + 1}`;
    await contacts.locator(`[name="${prefix}.contactName"]`).waitFor();
    return prefix;
  };

  await s.step('Manage Contacts opens from the Master Record with empty personal and professional lists', async () => {
    contacts = await s.popup(master, master.locator('a[onclick*="/demographic/Contact?method=manage"]').first(), 'manage-contacts');
    await contacts.locator('form#contactForm').waitFor();
    h.assert(await contacts.locator('#contact_container > *').count() === 0
      && await contacts.locator('#procontact_container > *').count() === 0, 'Manage Contacts opened with rows for a patient that has none');
  });

  await s.step('personal Internal search: the picked patient fills the contact row', async () => {
    h.assert(await addRow('contact') === 'contact_1', 'The first personal row is not contact_1');
    await contacts.locator('[name="contact_1.type"]').selectOption('1');
    await pickers('patient-contact-search', contacts.locator('a[onclick^="doPersonalSearch(\'1\')"]'),
      'form[name="titlesearch"]', marker, `input[name="pick_demographic"][value="${second}"]`,
      'contact_1', { id: second, name: marker });
  });

  await s.step('personal External search: the picked contact fills the contact row', async () => {
    h.assert(await addRow('contact') === 'contact_2', 'The second personal row is not contact_2');
    await contacts.locator('[name="contact_2.type"]').selectOption('2');
    await pickers('external-contact-search', contacts.locator('a[onclick^="doPersonalSearch(\'2\')"]'),
      'form[name="titlesearch"]', marker, `[onclick*="selectResult('${contactId}'"]`,
      'contact_2', { id: contactId, name: marker });
  });

  await s.step('professional Internal search: the picked provider fills the contact row', async () => {
    const providerName = sql.rows(`SELECT last_name FROM provider WHERE provider_no=${q(s.provider)}`)[0][0];
    h.assert(await addRow('procontact') === 'procontact_1', 'The first professional row is not procontact_1');
    await contacts.locator('[name="procontact_1.type"]').selectOption('0');
    await pickers('provider-contact-search', contacts.locator('a[onclick^="doProfessionalSearch(\'1\')"]'),
      'body', '', `a[onclick*="selectProviderCustom('${s.provider}'"]`,
      'procontact_1', { id: s.provider, name: providerName });
  });

  await s.step('Submit stores the three contacts, closes Manage Contacts and reloads the Master Record listing them', async () => {
    const sentinel = await markOpener(master);
    const closed = contacts.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
    const reloaded = master.waitForEvent('load', { timeout: TIMEOUT }).then(() => true, () => false);
    await contacts.locator('#contactForm input[type="submit"]').click({ noWaitAfter: true });
    await expectValue(sql, `SELECT GROUP_CONCAT(CONCAT(category,':',type,':',contactId) ORDER BY category,type SEPARATOR '|')
      FROM DemographicContact WHERE demographicNo=${s.patient} AND deleted=0`,
    `personal:1:${second}|personal:2:${contactId}|professional:0:${s.provider}`,
    'DemographicContact does not hold exactly the three picked contacts');
    h.assert(await closed, `Submit saved the contacts but Manage Contacts did not close (documents: ${chain.describe(contacts)})`);
    h.assert(await reloaded && await master.evaluate(name => window[name], sentinel.marker) !== sentinel.token,
      'Manage Contacts closed without reloading the Master Record (its window.opener.location.reload() was lost)');
    const listed = (await master.locator('body').innerText()).toLowerCase();
    h.assert(listed.split(marker.toLowerCase()).length - 1 >= 2, 'The reloaded Master Record does not list the saved contacts');
  });

  // Last, and collected: the pickers a defect breaks. Every working path above is proven
  // first; this step tries all three and names each one that fails.
  await s.step('Specialist, professional-contact and Referral Doctor pickers each hand the pick back and it saves', async () => {
    const failures = [];
    contacts = await s.popup(master, master.locator('a[onclick*="/demographic/Contact?method=manage"]').first(), 'manage-contacts-2');
    await contacts.locator('form#contactForm').waitFor();
    const specialistRow = await addRow('procontact');
    await contacts.locator(`[name="${specialistRow}.type"]`).selectOption('3');
    failures.push(await tryPicker('specialist-contact-search',
      contacts.locator(`a[onclick^="doProfessionalSearch('${specialistRow.split('_')[1]}')"]`),
      'form[name="titlesearch"]', marker, `[onclick*="${specialistId}"]`, specialistRow, { id: specialistId, name: marker }));
    const proRow = await addRow('procontact');
    await contacts.locator(`[name="${proRow}.type"]`).selectOption('2');
    failures.push(await tryPicker('professional-contact-search',
      contacts.locator(`a[onclick^="doProfessionalSearch('${proRow.split('_')[1]}')"]`),
      'form[name="titlesearch"]', marker, `[onclick*="${proContactId}"]`, proRow, { id: proContactId, name: marker }));
    if (!failures.some(Boolean)) {
      const closed = contacts.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
      await contacts.locator('#contactForm input[type="submit"]').click({ noWaitAfter: true });
      await expectValue(sql, `SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${s.patient} AND deleted=0
        AND category='professional' AND ((type=3 AND contactId=${q(specialistId)}) OR (type=2 AND contactId=${q(proContactId)}))`,
      '2', 'The picked specialist and professional contact were not stored');
      h.assert(await closed, 'Submit saved the professional contacts but Manage Contacts did not close');
    } else if (!contacts.isClosed()) await contacts.close();

    if (master.isClosed()) throw new Error('The Master Record closed during the contacts save');
    await master.locator('#editBtn').click();
    await master.locator('#editDemographic').waitFor({ state: 'visible' });
    const popup = await s.popup(master, master.locator('a[href*="referralScriptAttach2(\'r_doctor_ohip\',\'r_doctor\')"]'),
      'referral-doctor-search');
    await popup.locator('#tblDocs').waitFor();
    await popup.locator('#tblDocs_filter input').fill(marker);
    const closed = await pickInPopup(popup, popup.locator('#tblDocs tbody tr', { hasText: marker }));
    const ohip = await master.locator('[name="r_doctor_ohip"]').inputValue();
    const name = await master.locator('[name="r_doctor"]').inputValue();
    if (ohip !== referralNo || name !== referralName) {
      failures.push(closed ? 'Referral Doctor search: the picker closed but the referral fields do not hold the picked specialist'
        : await lostOpenerMessage('Referral Doctor search (billing/CA/ON/ViewSearchRefDoc)', popup, chain));
      if (!popup.isClosed()) await popup.close();
    } else {
      h.assert(closed, 'The referral doctor picker filled the fields but did not close itself');
      await clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(),
        { timeout: TIMEOUT, label: 'Update Record' });
      await expectValue(sql, `SELECT family_doctor LIKE ${q(`%<rdohip>${referralNo}</rdohip>%<rd>${referralName}</rd>%`)}
        FROM demographic WHERE demographic_no=${s.patient}`, '1', 'The saved record does not carry the picked referral doctor');
    }
    const broken = failures.filter(Boolean);
    h.assert(!broken.length, `${broken.length} picker(s) never handed the value back: ${broken.join(' | ')}`);
  });
}

if (require.main === module) runWorkflow('popup-opener-master-record', workflow, { openPatient: true });
module.exports = { workflow };
