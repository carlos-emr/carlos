#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC billing popups: the bill form's diagnosis reference and referral-doctor search, and the private receipt's
 * bill-to address book. The other BC bill checks fill the codes by hand or through the fee and diagnosis searches;
 * none used the patient's earlier diagnoses, the referral search or the bill-to address popups.
 *
 * User path: Schedule (today's day sheet) ▸ B on the owned appointment (the BC bill form, in its own window) ▸
 * billing physician ▸ the dx reference list (billing/CA/BC/ViewDxReference, loaded into the form) ▸ Referral 1 and 2
 * searches (billing/CA/BC/ViewBillingReferCodeSearch popup; with several matches, tick one and Confirm, posting to
 * ViewBillingReferCodeUpdate) ▸ fee code ▸ Continue ▸ Save Bill. Master record ▸ Invoice List (Bill Status) ▸ Bill
 * Patient ▸ the private invoice (receipt) ▸ bill-to Search (billing/CA/BC/ViewOnSearch3rdBillAddr popup) ▸ an
 * address; and in the same popup Add/Edit Address (billing/CA/BC/OnAddEdit3rdAddr) ▸ a new name ▸ Search ▸ Save.
 * Asserted:
 *   1. B opens the bill form for the owned appointment, and its dx reference lists the patient's earlier diagnosis;
 *   2. picking that diagnosis fills the first diagnosis box;
 *   3. a referral search with one match fills Referral 1 with that doctor's number and closes;
 *   4. a referral search with two matches lists exactly those two, and Confirm with one ticked fills Referral 2;
 *   5. Save Bill writes the claim with the picked diagnosis and both referral doctors;
 *   6. the receipt's bill-to search finds the owned address by name, and picking it fills the recipient;
 *   7. Add/Edit Address saves a new name with its address, and the bill-to search then finds it.
 *
 * Fixtures: one billing physician (bc.createBillingPhysician); the workflow's own FAKE patient made a BC patient,
 * with one earlier MSP claim (diagnosis 780, in 1995) and one private bill; one appointment for it today on the
 * test login's day sheet; two referral doctors and one bill-to address, named with the run marker. The new address
 * of step 7 is named with the marker too. The physician, the referral doctors and the addresses are clinic-wide
 * lists, so the check runs EXCLUSIVE. Cleanup deletes every BC billing row of the patient, its appointment and
 * archive rows, the addresses and referral doctors named with the marker, and the physician, and asserts each is gone.
 * BC installs only (billregion=BC).
 * Expected (see the manifest notes): on release/2026.08 the last step fails until #4962 is fixed (Add/Edit Address's
 * Search and Save throw a script error, so Save never asks to confirm).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const CODE = '00100';
const DX = '780';
const EARLIER_SERVICE_DATE = '1995-06-15';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: "the day sheet's B opens the BC bill form, whose dx reference lists the patient's earlier diagnosis",
  dxPick: 'picking the earlier diagnosis fills the first diagnosis box',
  referOne: 'a referral search with one match fills Referral 1 with that doctor',
  referTwo: 'a referral search with two matches lists both, and Confirm fills Referral 2 with the one ticked',
  save: 'Save Bill writes the claim with the picked diagnosis and both referral doctors',
  billTo: "the receipt's bill-to search finds the owned address and fills the recipient with it",
  addAddress: 'Add/Edit Address saves a new address, and the bill-to search then finds it',
};

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

/** The owned patient's appointment today and its archive rows; removed after the bills, before the patient. */
function ownAppointment(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${patient};
      DELETE FROM appointment WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient})`) === '0', 'Owned appointments were not removed');
  });
  const day = sql.value('SELECT CURDATE()');
  const id = h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, notes, reason, status, createdatetime, updatedatetime, creator, lastupdateuser)
    VALUES (${q(provider)}, ${q(day)}, '09:00:00', '09:14:00', ${q(`${marker},Workflow`)}, ${patient}, ${q(marker)}, ${q(marker)},
      't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'appointment');
  return { day, id };
}

/** Two referral doctors with the run marker as last name, and the bill-to address book entries named with it. */
function ownReferralsAndAddresses(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  s.cleanup(() => {
    sql.execute(`DELETE FROM billingreferral WHERE last_name=${q(marker)};
      DELETE FROM billing_on_3rdPartyAddress WHERE company_name LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billingreferral WHERE last_name=${q(marker)})
      + (SELECT COUNT(*) FROM billing_on_3rdPartyAddress WHERE company_name LIKE ${q(`${marker}%`)})`) === '0',
    'The owned referral doctors and addresses were not removed');
  });
  const referral = (firstName) => {
    const number = bc.unusedNumber('9', 5, c => sql.value(`SELECT (SELECT COUNT(*) FROM billingreferral WHERE referral_no=${q(c)})
      + (SELECT COUNT(*) FROM billingmaster WHERE referral_no1=${q(c)} OR referral_no2=${q(c)})`) !== '0');
    sql.execute(`INSERT INTO billingreferral (referral_no, last_name, first_name, specialty, city, province, phone)
      VALUES (${q(number)}, ${q(marker)}, ${q(firstName)}, 'FAKE Specialty', 'Testville', 'BC', '555-010-0000')`);
    return number;
  };
  const alpha = referral('Alpha');
  const beta = referral('Beta');
  const address = { company: `${marker} Clinic`, attention: 'Billing Desk', street: '1 FAKE Payer Road', city: 'Testville',
    province: 'BC', postcode: 'V0V0V0' };
  sql.execute(`INSERT INTO billing_on_3rdPartyAddress (attention, company_name, address, city, province, postcode, telephone, fax)
    VALUES (${q(address.attention)}, ${q(address.company)}, ${q(address.street)}, ${q(address.city)}, ${q(address.province)},
      ${q(address.postcode)}, '555-010-0001', '')`);
  return { alpha, beta, address };
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

async function workflow(s) {
  const { sql, patient, context, marker, recorder } = s;
  const q = h.sqlString;
  const physician = bc.createBillingPhysician(s);
  bc.makeBcPatient(s);
  const owned = ownReferralsAndAddresses(s);
  // Registered after the appointment's cleanup, so the bills go first.
  const appointment = ownAppointment(s);
  bc.registerOwnedBcBillCleanup(s);
  bc.seedOwnedBcBill(s, { serviceDate: EARLIER_SERVICE_DATE, code: CODE, dx: DX });
  const privateBill = bc.seedOwnedBcBill(s, { billingtype: 'Pri', status: 'P', amount: '45.00' });

  const [year, month, day] = appointment.day.split('-');
  const daySheetPath = `/provider/providercontrol?${new URLSearchParams({ year, month: String(Number(month)),
    day: String(Number(day)), view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' })}`;
  const sheet = s.schedule;

  /** Open the referral search for one box and return the popup it opens. */
  const referralSearch = async (form, box, text) => {
    await form.locator(`input[name="${box}"]`).fill(text);
    const [popup] = await Promise.all([context.waitForEvent('page', { timeout: TIMEOUT }),
      form.locator(`[onclick="ReferralScriptAttach('${box}')"]`).click()]);
    return popup;
  };

  let form;
  await s.step(STEP.open, async () => {
    await h.gotoApp(sheet, s.config.baseUrl, daySheetPath);
    await settle(sheet, 'day sheet');
    const link = sheet.locator(`a[onclick*="/billing?billRegion="][onclick*="appointment_no=${appointment.id}&"]`).first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    [form] = await Promise.all([context.waitForEvent('page', { timeout: TIMEOUT }), link.click()]);
    await settle(form, 'the bill form');
    h.assert(await form.locator('#bcBillingForm').count() === 1, 'B did not open the BC bill form');
    // The physician first: choosing one reloads the form, which would clear the boxes filled below.
    await bc.changeBillFormSelect(form, '#xml_provider', physician.providerNo, 2, 'choosing the billing physician');
    const earlier = form.locator('#DX_REFERENCE option', { hasText: new RegExp(`^\\s*${DX} - \\d+m\\s*$`) });
    await earlier.waitFor({ timeout: TIMEOUT });
    h.assert(await earlier.count() === 1, `The dx reference does not list the patient's earlier diagnosis ${DX} once`);
  });

  await s.step(STEP.dxPick, async () => {
    h.assert(await form.locator('input[name="xml_diagnostic_detail1"]').inputValue() === '', 'The first diagnosis box is not empty before the pick');
    await form.locator('#DX_REFERENCE option', { hasText: new RegExp(`^\\s*${DX} - `) }).click();
    const value = await form.locator('input[name="xml_diagnostic_detail1"]').inputValue();
    h.assert(value === DX, `Picking the earlier diagnosis filled the first diagnosis box with ${JSON.stringify(value)}`);
  });

  await s.step(STEP.referOne, async () => {
    const popup = await referralSearch(form, 'xml_refer1', `${marker},Alpha`);
    // With one match the popup fills the box and closes itself.
    if (!popup.isClosed()) await popup.waitForEvent('close', { timeout: TIMEOUT });
    const value = await form.locator('input[name="xml_refer1"]').inputValue();
    h.assert(value === owned.alpha, `The one-match referral search filled Referral 1 with ${JSON.stringify(value)}, not ${owned.alpha}`);
  });

  await s.step(STEP.referTwo, async () => {
    const popup = await referralSearch(form, 'xml_refer2', marker);
    await settle(popup, 'the referral search');
    const listed = await popup.locator('input[type="checkbox"][name^="code_"]').evaluateAll(boxes => boxes.map(b => b.name.slice(5)));
    h.assert(listed.length === 2 && listed.includes(owned.alpha) && listed.includes(owned.beta),
      `The two-match referral search listed ${JSON.stringify(listed)}, not ${owned.alpha} and ${owned.beta}`);
    await popup.locator(`input[name="code_${owned.beta}"]`).check();
    const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
    await popup.locator('input[type="submit"][name="update"][value="Confirm"]').click();
    await closed;
    const value = await form.locator('input[name="xml_refer2"]').inputValue();
    h.assert(value === owned.beta, `Confirm filled Referral 2 with ${JSON.stringify(value)}, not ${owned.beta}`);
    h.assert(await form.locator('input[name="xml_refer1"]').inputValue() === owned.alpha, 'Confirm changed Referral 1');
  });

  await s.step(STEP.save, async () => {
    await form.locator('input[name="xml_other1"]').fill(CODE);
    await form.locator('input[name="xml_other1_unit"]').fill('1');
    await form.locator('#ignoreWarn').setChecked(true);
    const [continued] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/BC/CreateBilling'),
        { timeout: TIMEOUT }),
      form.locator('#buttonRow input[type="submit"][value="Continue"]').click(),
    ]);
    h.assert(continued.status() === 200, `Continue answered HTTP ${continued.status()}`);
    await settle(form, 'the bill review');
    h.assert(await form.locator('form[action$="/billing/CA/BC/SaveBilling"]').count() === 1, 'Continue did not reach the bill review');
    const closed = form.waitForEvent('close', { timeout: TIMEOUT });
    const [saved] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/BC/SaveBilling'),
        { timeout: TIMEOUT }),
      form.locator('input[type="submit"][name="submit"][value="Save Bill"]').click(),
    ]);
    h.assert(saved.status() < 400, `Save Bill answered HTTP ${saved.status()}`);
    await closed;
    const claim = sql.value(`SELECT CONCAT_WS('|', bm.dx_code1, bm.referral_no1, bm.referral_no2, bm.billing_code) FROM billingmaster bm
      JOIN billing b ON b.billing_no=bm.billing_no WHERE b.appointment_no=${appointment.id} AND b.demographic_no=${patient}`);
    h.assert(claim === `${DX}|${owned.alpha}|${owned.beta}|${CODE}`,
      `The saved claim carries diagnosis|referral 1|referral 2|code ${JSON.stringify(claim)}, not ${DX}|${owned.alpha}|${owned.beta}|${CODE}`);
  });

  let receipt;
  /** The bill-to search popup, searched by name; returns it with its result rows loaded. */
  const billToSearch = async (keyword) => {
    const [popup] = await Promise.all([context.waitForEvent('page', { timeout: TIMEOUT }),
      receipt.locator('a[onclick*="scriptAttach(\'billto\')"]').click()]);
    await settle(popup, 'the bill-to search');
    // The search form opens inside a table, so the browser lifts its controls out of the <form> element; they still
    // belong to it, but a form-scoped selector finds none of them.
    await popup.locator('input[name="keyword"]').fill(keyword);
    await Promise.all([
      popup.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/BC/ViewOnSearch3rdBillAddr'), { timeout: TIMEOUT }),
      ui.clickAndAwaitReload(popup, popup.locator('input[type="submit"][value="Search"]').first(),
        { timeout: TIMEOUT, label: 'the bill-to Search' }),
    ]);
    await settle(popup, 'the bill-to search results');
    return popup;
  };
  const resultRow = (popup, company) => popup.locator('tr[onclick^="fillForm("]', { hasText: company });

  await s.step(STEP.billTo, async () => {
    const statusPage = await bc.openBillStatus(s, ui);
    await bc.reportBillStatus(statusPage, ui, { status: 'P' });
    receipt = await s.popup(statusPage, statusPage.locator(`a[href*="billingView?billing_no=${privateBill.billingNo}&receipt=yes"]`), 'bc-receipt');
    await settle(receipt, 'the receipt');
    const popup = await billToSearch(owned.address.company);
    const row = resultRow(popup, owned.address.company);
    h.assert(await row.count() === 1, `The bill-to search did not list ${owned.address.company} once`);
    const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
    await row.click();
    await closed;
    const filled = {
      name: await receipt.locator('input[name="recipientName"]').inputValue(),
      address: await receipt.locator('input[name="recipientAddress"]').inputValue(),
      city: await receipt.locator('input[name="recipientCity"]').inputValue(),
      province: await receipt.locator('input[name="recipientProvince"]').inputValue(),
      postal: await receipt.locator('input[name="recipientPostal"]').inputValue(),
    };
    const expected = { name: `${owned.address.company} Attn: ${owned.address.attention}`, address: owned.address.street,
      city: owned.address.city, province: owned.address.province, postal: owned.address.postcode };
    h.assert(JSON.stringify(filled) === JSON.stringify(expected),
      `Picking the address filled the recipient with ${JSON.stringify(filled)}, not ${JSON.stringify(expected)}`);
  });

  await s.step(STEP.addAddress, async () => {
    const added = { company: `${marker} New Payer`, attention: 'Accounts', street: '2 FAKE Added Street', city: 'Newtown',
      province: 'BC', postcode: 'V1V1V1', telephone: '555-010-0002' };
    const search = await billToSearch(added.company);
    h.assert(await resultRow(search, added.company).count() === 0, `${added.company} is already in the address book`);
    await ui.clickAndAwaitReload(search, search.locator('form[action$="/billing/CA/BC/OnAddEdit3rdAddr"] button[type="submit"]'),
      { timeout: TIMEOUT, label: 'Add/Edit Address' });
    await settle(search, 'Add/Edit Address');
    await search.locator('input[type="text"][name="company_name"]').fill(added.company);
    await ui.clickAndAwaitReload(search, search.locator('input[type="submit"][name="submit"][value="Search"]').first(),
      { timeout: TIMEOUT, label: 'the Add/Edit Address name search' });
    await settle(search, 'Add/Edit Address after the name search');
    h.assert(/It is a NEW name\. You can add it\./.test(await search.locator('body').innerText()),
      `Add/Edit Address did not offer to add ${added.company}`);
    for (const [field, value] of [['attention', added.attention], ['address', added.street], ['city', added.city],
      ['province', added.province], ['postcode', added.postcode], ['telephone', added.telephone]]) {
      await search.locator(`input[type="text"][name="${field}"]`).fill(value);
    }
    // Save asks "Are you sure you want to save?" before it posts.
    const saved = search.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/billing/CA/BC/OnAddEdit3rdAddr'), { timeout: TIMEOUT });
    const dialogs = await h.withExpectedDialogs(search,
      () => search.locator('[type="submit"][name="submit"][onclick*="onSave"]').click());
    h.assert((await saved).status() === 200, 'Save did not post the new address');
    await settle(search, 'Add/Edit Address after Save');
    h.assert((await search.locator('body').innerText()).includes(`${added.company} is added.`), `Save did not report ${added.company} added`);
    const row = sql.value(`SELECT CONCAT_WS('|', attention, address, city, province, postcode, telephone) FROM billing_on_3rdPartyAddress
      WHERE company_name=${q(added.company)}`);
    h.assert(row === [added.attention, added.street, added.city, added.province, added.postcode, added.telephone].join('|'),
      `The saved address is ${JSON.stringify(row)}`);
    await search.close();
    const again = await billToSearch(added.company);
    h.assert(await resultRow(again, added.company).count() === 1, `The bill-to search did not find the new ${added.company}`);
    await again.close();
    // Last, so the save itself is checked first: on release the page's Search and Save handlers throw before the
    // confirmation (see the manifest notes).
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `Save did not ask to confirm (dialogs: ${JSON.stringify(dialogs)})`);
  });
  await receipt.close();
}

if (require.main === module) runWorkflow('billing-bc-bill-form-popups', workflow, { preflight });
module.exports = { workflow, preflight, STEP };
