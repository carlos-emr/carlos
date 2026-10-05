#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario billing-form, location, private-code and code-table administration check.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage Billing Form / Add Billing Location /
 * Manage Private Billing Code (#dynamic-content iframe); Master Record ▸ Create Invoice (billingON)
 * ▸ Billing form chooser, dx description, code autocompletes, Edit super codes
 * (ViewBillingONFavourite); Master Record ▸ Billing History ▸ Edit (correction) ▸ code / dx Search.
 *
 * Asserts against MariaDB: private code add/edit/delete (billingservice), the form's service and
 * dx grids (ctl_billingservice, ctl_diagcode), premium add/delete, location add/delete, a super-code
 * favourite saved from the bill form and applied to it (billing_on_favourite); that the owned form,
 * its codes, the private code and the location are offered on the bill form; GET against the add
 * mutator writes nothing. The LAST step drives the legacy popup saves (dx/code description update,
 * code attach, form Add / bill type Change / Delete) and fails while they are broken.
 *
 * Fixtures: the owned form (its Add is the broken save, so it is seeded the way the action writes
 * it), two OHIP-shaped codes and two unused dx codes carrying the marker, one owned bill
 * (seedOwnedBill). Cleanup deletes owned rows by id/code/marker and asserts they are gone.
 * Implements coverage-plan §2.7 billing-on-admin-config.
 */

const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { seedOwnedBill, openHistory, openCorrection, billDate } = require('./billing-on-invoice-third-party-playwright-checks');

const FORM_ROUTE = '/billing/CA/ON/ManageBillingform';
const LOCATION_ROUTE = '/billing/CA/ON/ManageBillingLocation';
const PRIVATE_ROUTE = '/billing/CA/ON/ViewBillingONEditPrivateCode';

/** Letters-only token: the dx text search strips digits from what is typed. */
function lettersOnly(marker) {
  return `FAKEPWDX${marker.slice(7).replace(/\d/g, d => 'ghijklmnop'[Number(d)]).toUpperCase()}`;
}

/** A value of `length` random characters from `alphabet` that `isFree` accepts. */
function pickFree(isFree, make, label) {
  for (let attempt = 0; attempt < 40; attempt++) {
    const value = make();
    if (isFree(value)) return value;
  }
  throw new h.SkipCheck(`no unused ${label} could be found for the fixture`);
}

/** Schedule ▸ Administration (popup or same tab). */
async function openAdmin(s) {
  const { page } = await ui.clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'billing-administration', timeout: 20000 });
  return page;
}

/** One Billing left-nav item loaded into the administration iframe. */
async function adminFrame(admin, route, ready) {
  const link = admin.locator(`a[rel$="${route}"]`).first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor({ timeout: 20000 });
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `${route} did not load in the administration frame`);
  await frame.waitForURL(url => new URL(url).pathname.endsWith(route), { timeout: 20000 });
  await frame.locator(ready).first().waitFor({ state: 'visible', timeout: 20000 });
  return frame;
}

/** Click a control that navigates `frame` (a form post, often redirected) and wait for the new document. */
async function navigates(admin, frame, locator) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 20000 });
  navigated.catch(() => {});
  await locator.click();
  await navigated;
  await frame.waitForLoadState('domcontentloaded');
  await frame.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
}

/** Manage Billing Form: pick a form (or the Add / Premium entries) and a tab, then Manage. */
async function manageForm(admin, frame, billingform, reportAction) {
  await frame.locator('form[name="serviceform"] select[name="billingform"]').selectOption(billingform);
  if (reportAction) await frame.locator(`form[name="serviceform"] input[name="reportAction"][value="${reportAction}"]`).check();
  await navigates(admin, frame, frame.locator('form[name="serviceform"] input[type="submit"][name="Submit"]'));
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const hex = marker.slice(7).toUpperCase();
  const token = lettersOnly(marker);
  const free = query => sql.value(query) === '0';

  const typeId = pickFree(id => free(`SELECT (SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(id)})
      + (SELECT COUNT(*) FROM ctl_diagcode WHERE servicetype=${h.sqlString(id)})
      + (SELECT COUNT(*) FROM ctl_billingtype WHERE servicetype=${h.sqlString(id)})`),
  () => `Z${'ABCDEFGHJKLMNPQRSTUVWXY'[randomInt(23)]}${randomInt(10)}`, 'billing form id');
  const prefix = pickFree(p => free(`SELECT (SELECT COUNT(*) FROM billingservice WHERE service_code LIKE ${h.sqlString(`${p}%`)}
      OR description LIKE ${h.sqlString(`%${p}%`)}) + (SELECT COUNT(*) FROM ctl_billingservice_premium
      WHERE service_code LIKE ${h.sqlString(`${p}%`)}) + (SELECT COUNT(*) FROM ctl_billingservice WHERE service_code LIKE ${h.sqlString(`${p}%`)})`),
  () => `${'WY'[randomInt(2)]}${String(randomInt(100)).padStart(2, '0')}`, 'service code prefix');
  const typeId2 = pickFree(id => id !== typeId && free(`SELECT (SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(id)})
      + (SELECT COUNT(*) FROM ctl_diagcode WHERE servicetype=${h.sqlString(id)})
      + (SELECT COUNT(*) FROM ctl_billingtype WHERE servicetype=${h.sqlString(id)})`),
    () => `Z${'ABCDEFGHJKLMNPQRSTUVWXY'[randomInt(23)]}${randomInt(10)}`, 'second billing form id');
  const codeA = `${prefix}1Z`;
  const codeB = `${prefix}2Z`;
  const dxFree = code => free(`SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code=${h.sqlString(code)}`);
  const dxA = pickFree(dxFree, () => String(randomInt(1000)).padStart(3, '0'), 'diagnostic code');
  const dxB = pickFree(c => c !== dxA && dxFree(c), () => String(randomInt(1000)).padStart(3, '0'), 'diagnostic code');
  const privateBare = `PW${hex.slice(0, 5)}`;
  const privateCode = `_${privateBare}`;
  h.assert(free(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(privateCode)}`), 'Private code collision');
  const location = pickFree(no => free(`SELECT COUNT(*) FROM clinic_location WHERE clinic_location_no=${h.sqlString(no)}`),
    () => `PW${String(randomInt(10000)).padStart(4, '0')}`, 'location number');
  // The private-code date field is a readonly flatpickr: pick a day the calendar offers.
  const issued = `${new Date().toISOString().slice(0, 7)}-01`;
  const formName = `${marker} form`;
  const formName2 = `${marker} form 2`;
  const types = [typeId, typeId2].map(h.sqlString).join(',');
  const locationName = `${marker} loc`;
  const descA = `${marker} svc A`;
  const descB = `${marker} svc B`;
  const dxDescA = `${token} dx A`;
  const dxDescB = `${token} dx B`;
  const codes = [codeA, codeB, privateCode].map(h.sqlString).join(',');

  s.cleanup(() => {
    sql.execute(`DELETE FROM ctl_billingservice WHERE servicetype IN (${types});
      DELETE FROM ctl_diagcode WHERE servicetype IN (${types});
      DELETE FROM ctl_billingtype WHERE servicetype IN (${types});
      DELETE FROM ctl_billingservice_premium WHERE service_code IN (${codes});
      DELETE FROM clinic_location WHERE clinic_location_no=${h.sqlString(location)} AND clinic_location_name=${h.sqlString(locationName)};
      DELETE FROM billing_on_favourite WHERE name=${h.sqlString(marker)};
      DELETE FROM billingservice WHERE service_code IN (${codes});
      DELETE FROM diagnosticcode WHERE diagnostic_code IN (${h.sqlString(dxA)},${h.sqlString(dxB)}) AND description LIKE ${h.sqlString(`${token}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype IN (${types}))
      + (SELECT COUNT(*) FROM ctl_diagcode WHERE servicetype IN (${types}))
      + (SELECT COUNT(*) FROM ctl_billingtype WHERE servicetype IN (${types}))
      + (SELECT COUNT(*) FROM ctl_billingservice_premium WHERE service_code IN (${codes}))
      + (SELECT COUNT(*) FROM clinic_location WHERE clinic_location_no=${h.sqlString(location)})
      + (SELECT COUNT(*) FROM billing_on_favourite WHERE name=${h.sqlString(marker)})
      + (SELECT COUNT(*) FROM billingservice WHERE service_code IN (${codes}))
      + (SELECT COUNT(*) FROM diagnosticcode WHERE description LIKE ${h.sqlString(`${token}%`)})`) === '0',
    'Owned billing configuration rows were not removed');
  });
  sql.execute(`INSERT INTO billingservice (service_compositecode, service_code, description, value, percentage,
      billingservice_date, specialty, region, anaesthesia, termination_date, sliFlag, gstFlag)
    VALUES ('', ${h.sqlString(codeA)}, ${h.sqlString(descA)}, '12.34', '', '2020-01-01', '', 'ON', '00', '9999-12-31', 0, 0),
      ('', ${h.sqlString(codeB)}, ${h.sqlString(descB)}, '23.45', '', '2020-01-01', '', 'ON', '00', '9999-12-31', 0, 0);
    INSERT INTO diagnosticcode (diagnostic_code, description, status, region)
    VALUES (${h.sqlString(dxA)}, ${h.sqlString(dxDescA)}, 'A', 'ON'), (${h.sqlString(dxB)}, ${h.sqlString(dxDescB)}, 'A', 'ON');
    INSERT INTO ctl_billingservice (servicetype_name, servicetype, service_code, service_group_name, service_group, status, service_order)
    VALUES (${h.sqlString(formName)}, ${h.sqlString(typeId)}, 'A007A', 'FAKE Group One', 'Group1', 'A', 1),
      (${h.sqlString(formName)}, ${h.sqlString(typeId)}, 'A007A', 'FAKE Group Two', 'Group2', 'A', 1),
      (${h.sqlString(formName)}, ${h.sqlString(typeId)}, 'A007A', 'FAKE Group Three', 'Group3', 'A', 1);
    INSERT INTO ctl_diagcode (servicetype, diagnostic_code, status) VALUES (${h.sqlString(typeId)}, '000', 'A');
    INSERT INTO ctl_billingtype (servicetype, billtype) VALUES (${h.sqlString(typeId)}, 'ODP')`);
  h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code IN (${codes})`) === '2'
    && sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE description LIKE ${h.sqlString(`${token}%`)}`) === '2',
  'The owned service and dx code fixtures were not created');

  const ctlServices = () => sql.rows(`SELECT service_group, service_group_name, service_code, servicetype_name
    FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId)} ORDER BY service_group, service_order, service_code`)
    .map(row => row.join('|')).join(';');

  const admin = await openAdmin(s);
  let frame;

  await s.step('Manage Private Billing Code adds the owned private code and edits its fee', async () => {
    frame = await adminFrame(admin, PRIVATE_ROUTE, 'form[name="baseurl"]');
    const form = frame.locator('form[name="baseurl"]');
    await form.locator('input[name="service_code"]').fill(privateBare);
    await navigates(admin, frame, form.locator('button[name="submit"][value="Search"]'));
    h.assert(await form.locator('input[name="action"]').inputValue() === `add${privateCode}`,
      'Searching an unused private code did not offer to add it');
    await form.locator('input[name="description"]').fill(`${marker} private`);
    await form.locator('input[name="value"]').fill('31.50');
    await ui.pickDate(frame, form.locator('#billingservice_date'), issued);
    const dialogs = await h.withExpectedDialogs(admin, () => navigates(admin, frame, form.locator('input[name="submit"][value="Save"]')));
    h.assert(dialogs.length === 1 && /sure you want to save/i.test(dialogs[0].text), 'Save did not ask for confirmation once');
    h.assert((await frame.locator('form[name="baseurl"] .alert').innerText()).includes(`${privateCode} is added`),
      'The add banner did not name the private code');
    h.assert(sql.value(`SELECT CONCAT_WS('|', description, value, billingservice_date, termination_date) FROM billingservice
      WHERE service_code=${h.sqlString(privateCode)}`) === `${marker} private|31.50|${issued}|9999-12-31`,
    'The private code row does not carry the typed description, fee and date');
    await frame.locator('#service_code').selectOption(privateBare);
    await navigates(admin, frame, frame.locator('form[name="baseur0"] input[name="action"][value="Edit"]'));
    h.assert(await form.locator('input[name="description"]').inputValue() === `${marker} private`,
      'Editing the private code did not load its description');
    await form.locator('input[name="value"]').fill('32.75');
    await ui.pickDate(frame, form.locator('#billingservice_date'), issued);
    await h.withExpectedDialogs(admin, () => navigates(admin, frame, form.locator('input[name="submit"][value="Save"]')));
    h.assert(sql.value(`SELECT CONCAT_WS('|', COUNT(*), MAX(value)) FROM billingservice
      WHERE service_code=${h.sqlString(privateCode)}`) === '1|32.75', 'The private code edit did not update its single row');
  });

  await s.step('Add Billing Form refuses an empty id in the browser and GET at the server, and lists the owned form', async () => {
    frame = await adminFrame(admin, FORM_ROUTE, 'form[name="serviceform"]');
    await manageForm(admin, frame, '000');
    const add = frame.locator('form[name="servicetypeform"]');
    await add.waitFor({ state: 'visible' });
    const dialogs = await h.withExpectedDialogs(admin, () => add.locator('input[name="addForm"]').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'An empty service type id did not raise the required-field alert');
    const refused = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/DbManageBillingformAdd'), {
      params: { typeid: typeId2, type: formName2, group1: 'G1', group2: 'G2', group3: 'G3', billtype: 'ODP' }, maxRedirects: 0,
    });
    h.assert(refused.status() === 405, 'DbManageBillingformAdd must reject GET');
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId2)}`) === '0',
      'A refused add wrote billing form rows');
    h.assert(await frame.locator('a[title="Manage Billing Form"]', { hasText: formName }).count() === 1,
      'The existing-forms list does not show the owned form');
    h.assert(await frame.locator(`form[name="serviceform"] select[name="billingform"] option[value="${typeId}"]`).count() === 1,
      'The form chooser does not offer the owned form');
  });

  await s.step('service codes saved on the form replace its groups with the owned and private codes', async () => {
    await manageForm(admin, frame, typeId, 'servicecode');
    const grid = frame.locator('form[action="DbManageBillingformService"]');
    h.assert(await grid.locator('input[name="group1"]').inputValue() === 'FAKE Group One', 'The service grid did not load the group names');
    h.assert(await grid.locator('input[name="group1_service0"]').inputValue() === 'A007A', 'The service grid did not load the seeded code');
    await grid.locator('input[name="group1_service0"]').fill(codeA);
    await grid.locator('input[name="group1_service0_order"]').fill('1');
    await grid.locator('input[name="group2_service0"]').fill(codeB);
    await grid.locator('input[name="group2_service0_order"]').fill('1');
    await grid.locator('input[name="group3_service0"]').fill(privateCode);
    await grid.locator('input[name="group3_service0_order"]').fill('1');
    await navigates(admin, frame, grid.locator('input[type="submit"][name="submit"]'));
    await expectValue(sql, `SELECT GROUP_CONCAT(service_code ORDER BY service_group) FROM ctl_billingservice
      WHERE servicetype=${h.sqlString(typeId)}`, [codeA, codeB, privateCode].join(','), 'The service grid save did not replace the codes');
    h.assert(ctlServices() === [`Group1|FAKE Group One|${codeA}`, `Group2|FAKE Group Two|${codeB}`,
      `Group3|FAKE Group Three|${privateCode}`].map(row => `${row}|${formName}`).join(';'), 'The saved service rows lost their group or form names');
  });

  await s.step('dx codes saved on the form replace its seeded dx row', async () => {
    await manageForm(admin, frame, typeId, 'dxcode');
    const grid = frame.locator('form[action="DbManageBillingformDx"]');
    h.assert(await grid.locator('input[name="diagcode0"]').inputValue() === '000', 'The dx grid did not load the seeded dx');
    await grid.locator('input[name="diagcode0"]').fill(dxA);
    await grid.locator('input[name="diagcode1"]').fill(dxB);
    await navigates(admin, frame, grid.locator('input[type="submit"][name="submit"]'));
    await expectValue(sql, `SELECT GROUP_CONCAT(diagnostic_code ORDER BY diagnostic_code) FROM ctl_diagcode
      WHERE servicetype=${h.sqlString(typeId)}`, [dxA, dxB].sort().join(','), 'The dx grid save did not replace the dx codes');
  });

  await s.step('a premium code is added and removed again from the premium list', async () => {
    await manageForm(admin, frame, '***');
    // The premium forms sit inside a <table>, so the parser leaves their inputs outside
    // the <form> element (still form-owned): address the controls directly.
    await frame.locator('input[name="service1"]').fill(codeA);
    await navigates(admin, frame, frame.locator('input[type="submit"][value="Add Code"]'));
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(status), MAX(servicetype_name)) FROM ctl_billingservice_premium
      WHERE service_code=${h.sqlString(codeA)}`, '1|A|Office', 'The premium add did not write one active row');
    await manageForm(admin, frame, '***');
    const box = frame.locator(`input[type="checkbox"][value="${codeA}"]`);
    h.assert(await box.count() === 1, 'The premium list does not offer the owned code');
    await box.check();
    await navigates(admin, frame, frame.locator('input[type="submit"][value="Delete Code"]'));
    await expectValue(sql, `SELECT COUNT(*) FROM ctl_billingservice_premium WHERE service_code=${h.sqlString(codeA)}`, '0',
      'The premium delete did not remove the owned row');
  });

  await s.step('Add Billing Location adds the owned location to the list', async () => {
    frame = await adminFrame(admin, LOCATION_ROUTE, 'form[action="DbManageBillingLocation"]');
    const add = frame.locator('form[action="DbManageBillingLocation"]');
    await add.locator('input[name="location1"]').fill(location);
    await add.locator('input[name="location1desc"]').fill(locationName);
    await navigates(admin, frame, add.locator('input[type="submit"][name="action"]'));
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(clinic_no), MAX(clinic_location_name)) FROM clinic_location
      WHERE clinic_location_no=${h.sqlString(location)}`, `1|1|${locationName}`, 'The location add did not write the owned row');
    h.assert(await frame.locator('table.table tr', { hasText: locationName }).count() === 1, 'The location list does not show the owned location');
  });

  // Master Record ▸ Create Invoice: the Ontario bill form for the owned patient.
  const openBillForm = async () => {
    const link = s.master.locator('a[onclick*="/billing?billRegion=ON"]').first();
    h.assert(await link.count() === 1, 'The Master Record does not offer Create Invoice');
    const page = await s.popup(s.master, link, 'bill-form');
    await page.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
    h.assert(new URL(page.url()).searchParams.get('demographic_no') === patient, 'The bill form opened for another patient');
    return page;
  };
  let bill;

  await s.step('the bill form offers the owned form with its codes, private code and location', async () => {
    bill = await openBillForm();
    await bill.locator(`a[onclick*="'Layer1','','show'"]`).first().click();
    await bill.locator('#Layer1 a', { hasText: formName }).click();
    h.assert(await bill.locator('#billForm').inputValue() === typeId, 'Choosing the owned form did not select it');
    h.assert(await bill.locator('#billFormName').inputValue() === formName.slice(0, 40), 'The chosen form name is not shown');
    const groups = [[1, codeA, descA, '12.34'], [2, codeB, descB, '23.45'], [3, privateCode, `${marker} private`, '32.75']];
    for (const [group, code, desc, fee] of groups) {
      const div = bill.locator(`#group${group}_${typeId}`);
      await div.waitFor({ state: 'visible' });
      const row = div.locator('tr', { has: bill.locator(`#xml_${code}`) });
      h.assert(await row.count() === 1, `Group ${group} of the owned form does not list its code`);
      const text = (await row.innerText()).replace(/\s+/g, ' ');
      // The grid truncates descriptions longer than 30 characters.
      h.assert(text.includes(desc.slice(0, 30)) && text.includes(fee), `Group ${group} does not show the code's description and fee`);
    }
    h.assert(await bill.locator('#group1_MFP').isHidden(), 'Another form\'s grid stayed visible');
    h.assert((await bill.locator('select[name="xml_billtype"]').inputValue()).startsWith('ODP'), 'The form\'s bill type was not selected');
    const option = bill.locator('select[name="xml_location"] option', { hasText: locationName });
    h.assert(await option.count() === 1 && (await option.getAttribute('value')).startsWith(`${location}|`),
      'The visit-location list does not offer the owned location');
    await bill.locator(`#xml_${codeA}`).check();
    h.assert(await bill.locator(`#xml_${codeA}`).isChecked(), 'The owned code checkbox could not be ticked');
  });

  await s.step('the bill form describes and autocompletes the owned dx and service codes', async () => {
    const [desc] = await Promise.all([
      s.context.waitForEvent('response', { predicate: r => new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewBillingONDxDesc') }),
      bill.locator('input[name="dxCode"]').fill(dxA).then(() => bill.locator('input[name="dxCode"]').press('Tab')),
    ]);
    h.assert(desc.status() === 200, 'The dx description lookup failed');
    await bill.locator('#code_desc', { hasText: dxDescA }).waitFor({ timeout: 10000 });
    await ui.typeAutocomplete(bill, 'input[name="dxCode1"]', dxB, { option: dxDescB });
    h.assert(await bill.locator('input[name="dxCode1"]').inputValue() === dxB, 'The dx autocomplete did not fill the owned dx');
    await ui.typeAutocomplete(bill, 'input[name="serviceCode0"]', codeB, { option: descB });
    h.assert(await bill.locator('input[name="serviceCode0"]').inputValue() === codeB, 'The code autocomplete did not fill the owned code');
  });

  await s.step('super codes: a favourite saved from the bill form fills the bill and is deleted again', async () => {
    const fav = await s.popup(bill, bill.locator('a[onclick*="/billing/CA/ON/ViewBillingONFavourite"]').first(), 'billing-favourite');
    const form = fav.locator('#baseurl');
    await form.locator('#favName').fill(marker);
    await Promise.all([fav.waitForNavigation(), form.locator('button[name="submit"][value="Search"]').click()]);
    h.assert(await fav.locator('#baseurl input[name="action"]').inputValue() === `add${marker}`, 'Searching a new favourite did not offer to add it');
    await fav.locator('#baseurl input[name="serviceCode0"]').fill(codeA);
    await fav.locator('#baseurl input[name="serviceUnit0"]').fill('2');
    await fav.locator('#baseurl input[name="dx"]').fill(dxA);
    const dialogs = await h.withExpectedDialogs(fav, () => Promise.all([fav.waitForNavigation(), fav.locator('#btnSave').click()]));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Saving the favourite did not ask for confirmation once');
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(provider_no), MAX(deleted)) FROM billing_on_favourite
      WHERE name=${h.sqlString(marker)}`, `1|${provider}|0`, 'The favourite was not saved for the operator');
    const list = sql.value(`SELECT service_dx FROM billing_on_favourite WHERE name=${h.sqlString(marker)}`);
    h.assert(list.startsWith(`${codeA}|2|`) && list.includes(dxA), 'The favourite does not store the owned code, units and dx');
    await bill.reload({ waitUntil: 'domcontentloaded' });
    await bill.locator('#cutlist').waitFor({ state: 'visible' });
    await bill.locator('#cutlist').selectOption({ label: marker });
    h.assert(await bill.locator('input[name="serviceCode0"]').inputValue() === codeA
      && await bill.locator('input[name="serviceUnit0"]').inputValue() === '2'
      && await bill.locator('input[name="dxCode"]').inputValue() === dxA, 'Choosing the favourite did not fill its code, units and dx');
    await fav.locator('#favSelect').selectOption(marker);
    await h.withExpectedDialogs(fav, () => Promise.all([fav.waitForNavigation(), fav.locator('#btnDelete').click()]));
    await expectValue(sql, `SELECT deleted FROM billing_on_favourite WHERE name=${h.sqlString(marker)}`, '1',
      'Deleting the favourite did not flag it deleted');
    await fav.close();
    await bill.close();
  });

  await s.step('the owned private code and location are deleted from their admin pages', async () => {
    frame = await adminFrame(admin, PRIVATE_ROUTE, 'form[name="baseurl"]');
    await frame.locator('#service_code').selectOption(privateBare);
    await navigates(admin, frame, frame.locator('form[name="baseur0"] input[name="action"][value="Edit"]'));
    const dialogs = await h.withExpectedDialogs(admin,
      () => navigates(admin, frame, frame.locator('form[name="baseurl"] input[name="submit"][value="Delete"]')));
    h.assert(dialogs.length === 1 && /sure you want to Delete/i.test(dialogs[0].text), 'Delete did not ask for confirmation once');
    await expectValue(sql, `SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(privateCode)}`, '0',
      'The private code was not deleted');
    h.assert(await frame.locator(`#service_code option[value="${privateBare}"]`).count() === 0, 'The deleted code is still offered');
    frame = await adminFrame(admin, LOCATION_ROUTE, 'form[action="DbManageBillingLocation"]');
    const row = frame.locator('table.table tr', { hasText: locationName });
    const asked = await h.withExpectedDialogs(admin, () => navigates(admin, frame, row.locator('input[type="submit"][value="Delete"]')));
    h.assert(asked.length === 1 && asked[0].text.includes(location), 'Location delete did not confirm the location number');
    await expectValue(sql, `SELECT COUNT(*) FROM clinic_location WHERE clinic_location_no=${h.sqlString(location)}`, '0',
      'The location was not deleted');
  });

  let correction, ownedBill;
  await s.step('correction ▸ service code Search lists exactly the owned codes by their marked descriptions', async () => {
    const owned = seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code: codeA, fee: '12.34', date: billDate(), dx: dxA });
    ownedBill = owned;
    const history = await openHistory(s);
    correction = await openCorrection(s, history, owned.headerId);
    h.assert(await correction.locator('input[name="servicecode0"]').inputValue() === codeA, 'The correction did not load the owned code');
    await correction.locator('input[name="servicecode0"]').fill(marker);
    const search = await s.popup(correction, correction.locator('a[onclick="scScriptAttach(\'servicecode0\')"]'), 'code-search');
    const rows = search.locator('#servicecode tr', { has: search.locator('input[type="checkbox"]') });
    h.assert(await rows.count() === 2, 'The service code search did not list exactly the two owned codes');
    h.assert(await search.locator(`#servicecode input[type="text"][name="${codeB}"]`).inputValue() === descB,
      'The search row does not carry the code description for editing');
    await search.close();
  });

  // Each of these saves is driven exactly as an operator drives it. They are asserted
  // together, last, so one broken legacy save cannot hide the outcome of the others.
  await s.step('legacy popup saves (dx/code description updates, code attach, form Add / bill type / Delete) work', async () => {
    const problems = [];
    const attempt = async (label, body) => {
      try {
        await body();
        console.log(`  PASS billing-on-admin-config: ${label}`);
      } catch (error) {
        problems.push(`${label}: ${error.message.split('\n')[0]}`);
      }
    };
    const posted = route => s.context.waitForEvent('response', { timeout: 20000,
      predicate: r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(route) });

    await attempt('correction ▸ dx Search ▸ Update', async () => {
      const search = await s.popup(correction, correction.locator('a[href="javascript:ScriptAttach()"]'), 'dx-search');
      try {
        await search.locator('form[name="codesearch"] input[name="codedesc"]').fill(token);
        await Promise.all([search.waitForNavigation(), search.locator('form[name="codesearch"] input[name="search1"]').click()]);
        h.assert(await search.locator('form[name="diagcode"] tbody tr').count() === 2, 'the search did not list exactly the two owned dx codes');
        await search.locator(`form[name="diagcode"] input[name="${dxB}"]`).fill(`${dxDescB} edited`);
        const [response] = await Promise.all([posted('/billing/CA/ON/BillingDigUpdate'),
          search.locator(`form[name="diagcode"] input[name="update"][value$=" ${dxB}"]`).click()]);
        h.assert(response.status() === 200, `BillingDigUpdate answered HTTP ${response.status()}`);
        await expectValue(sql, `SELECT description FROM diagnosticcode WHERE diagnostic_code=${h.sqlString(dxB)}`, `${dxDescB} edited`,
          'the description was not rewritten');
      } finally { await search.close().catch(() => {}); }
    });

    await attempt('correction ▸ code Search ▸ Confirm attaches only to the selected row without saving the bill', async () => {
      const savedBill = () => JSON.stringify(sql.rows(`SELECT service_code, fee, ser_num, dx
        FROM billing_on_item WHERE ch1_id=${ownedBill.headerId} ORDER BY id`));
      const before = savedBill();
      for (const index of [0, 1]) {
        const target = `servicecode${index}`;
        const other = `servicecode${1 - index}`;
        await correction.locator(`input[name="${target}"]`).fill(marker);
        await correction.locator(`input[name="${other}"]`).fill(codeB);
        const search = await s.popup(correction, correction.locator(`a[onclick="scScriptAttach('${target}')"]`), 'code-attach');
        try {
          await search.locator(`#servicecode input[type="checkbox"][name="code_${codeA}"]`).check();
          const closed = search.waitForEvent('close', { timeout: 10000 });
          closed.catch(() => {});
          await search.locator('#servicecode input[name="update"][value="Confirm"]').click();
          await closed.catch(() => {});
          h.assert(s.recorder.pageErrors.every(entry => entry.label !== 'code-attach'), 'the attach page raised a script error');
          h.assert(search.isClosed(), 'Confirm did not close the search popup');
          h.assert(await correction.locator(`input[name="${target}"]`).inputValue() === codeA,
            'the chosen code was not written back into the selected correction row');
          h.assert(await correction.locator(`input[name="${other}"]`).inputValue() === codeB,
            'the attachment changed a different correction row');
          h.assert(savedBill() === before, 'attaching a code persisted the unsaved bill');
        } finally { await search.close().catch(() => {}); }
      }
    });

    await attempt('correction ▸ code Search ▸ update', async () => {
      await correction.locator('input[name="servicecode0"]').fill(marker);
      const search = await s.popup(correction, correction.locator('a[onclick="scScriptAttach(\'servicecode0\')"]'), 'code-search');
      const searchUrl = search.url();
      try {
        await search.locator(`#servicecode input[type="text"][name="${codeB}"]`).fill(`${descB} edited`);
        const [response] = await Promise.all([posted('/billing/CA/ON/BillingCodeUpdate'),
          search.locator(`#servicecode input[name="update"][value="update ${codeB}"]`).click()]);
        h.assert(response.status() === 200, `BillingCodeUpdate answered HTTP ${response.status()}`);
        await expectValue(sql, `SELECT description FROM billingservice WHERE service_code=${h.sqlString(codeB)}`, `${descB} edited`,
          'the description was not rewritten');
        h.assert(sql.value(`SELECT description FROM billingservice WHERE service_code=${h.sqlString(codeA)}`) === descA,
          'another code was touched');
        await search.waitForLoadState('load');
        h.assert(s.recorder.pageErrors.every(entry => entry.label !== 'code-search'), 'the result page raised a script error');
        await search.waitForURL(searchUrl, { timeout: 20000 });
        await search.locator(`#servicecode input[type="text"][name="${codeB}"]`).waitFor({ state: 'visible' });
        h.assert(await search.locator(`#servicecode input[type="text"][name="${codeB}"]`).inputValue() === `${descB} edited`,
          'the returned search did not show the saved description');
        h.assert(await correction.locator('input[name="servicecode0"]').inputValue() === marker,
          'updating a code description discarded the unsaved bill edit');
        await search.reload({ waitUntil: 'domcontentloaded' });
        h.assert(await search.locator(`#servicecode input[name="codedesc_${codeB}"]`).inputValue() === `${descB} edited`,
          'reloading the search did not read back the persisted description');
        h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code IN (${codes})`) === '2',
          'updating the description created another service-code row');
      } finally { await search.close().catch(() => {}); }
    });

    frame = await adminFrame(admin, FORM_ROUTE, 'form[name="serviceform"]');
    await attempt('Manage Billing Form ▸ Add', async () => {
      await manageForm(admin, frame, '000');
      const add = frame.locator('form[name="servicetypeform"]');
      await add.locator('input[name="typeid"]').fill(typeId2);
      await add.locator('input[name="type"]').fill(formName2);
      await add.locator('input[name="group1"]').fill('FAKE Group One');
      await add.locator('input[name="group2"]').fill('FAKE Group Two');
      await add.locator('input[name="group3"]').fill('FAKE Group Three');
      await add.locator('select[name="billtype"]').selectOption('NOT');
      const [response] = await Promise.all([posted('/billing/CA/ON/DbManageBillingformAdd'), add.locator('input[name="addForm"]').click()]);
      h.assert(response.status() < 400, `DbManageBillingformAdd answered HTTP ${response.status()}`);
      await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(servicetype_name)) FROM ctl_billingservice
        WHERE servicetype=${h.sqlString(typeId2)}`, `3|${formName2}`, 'the three service groups were not written');
      h.assert(sql.value(`SELECT billtype FROM ctl_billingtype WHERE servicetype=${h.sqlString(typeId2)}`) === 'NOT',
        'the default bill type was not written');
    });

    frame = await adminFrame(admin, FORM_ROUTE, 'form[name="serviceform"]');
    const managePanel = async () => {
      await manageForm(admin, frame, '000');
      await frame.locator('a[title="Manage Billing Form"]', { hasText: typeId }).first().click();
      const panel = frame.locator('#manage_type');
      await panel.locator('select[name="billtype_new"]').waitFor({ state: 'visible' });
      return panel;
    };
    const popupPost = async (route, click) => {
      const opened = s.context.waitForEvent('page', { timeout: 20000 });
      const response = posted(route);
      await click();
      const popup = await opened;
      try {
        const answer = await response;
        h.assert(answer.status() === 200, `${route.split('/').pop()} answered HTTP ${answer.status()}`);
      } finally {
        if (!popup.isClosed()) await popup.waitForEvent('close', { timeout: 5000 }).catch(() => popup.close());
      }
    };
    await attempt('Manage Billing Form ▸ bill type Change', async () => {
      const panel = await managePanel();
      h.assert(await panel.locator('input[name="billtype_old"]').inputValue() === 'ODP', 'the panel did not load the bill type');
      await panel.locator('select[name="billtype_new"]').selectOption('WCB');
      await popupPost('/billing/CA/ON/DbManageBillingformBilltype', () => panel.locator('input[type="button"][value="Change"]').click());
      await expectValue(sql, `SELECT billtype FROM ctl_billingtype WHERE servicetype=${h.sqlString(typeId)}`, 'WCB',
        'ctl_billingtype was not changed');
    });

    frame = await adminFrame(admin, FORM_ROUTE, 'form[name="serviceform"]');
    await attempt('Manage Billing Form ▸ Delete Billing Form', async () => {
      const panel = await managePanel();
      const asked = await h.withExpectedDialogs(admin, () => popupPost('/billing/CA/ON/DbManageBillingformDelete',
        () => panel.locator('input[type="button"][value="Delete Billing Form"]').click()));
      h.assert(asked.length === 1 && asked[0].type === 'confirm', 'Delete Billing Form did not ask for confirmation once');
      await expectValue(sql, `SELECT (SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId)})
        + (SELECT COUNT(*) FROM ctl_diagcode WHERE servicetype=${h.sqlString(typeId)})
        + (SELECT COUNT(*) FROM ctl_billingtype WHERE servicetype=${h.sqlString(typeId)})`, '0', 'the form rows were not deleted');
    });

    h.assert(problems.length === 0, `${problems.length} legacy save(s) failed: ${problems.join(' | ')}`);
  });
}

if (require.main === module) runWorkflow('billing-on-admin-config', workflow, { openPatient: true });
module.exports = { workflow, openAdmin, adminFrame, navigates };
