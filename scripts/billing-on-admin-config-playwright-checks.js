#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario billing-form, location, private-code and code-table administration check.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage Billing Form (ManageBillingform in the
 * #dynamic-content iframe) ▸ Add / service codes / dx codes / bill type / premium codes / Delete
 * Billing Form; ▸ Add Billing Location; ▸ Manage Private Billing Code; Master Record ▸ Create
 * Invoice (billingON) ▸ Billing form chooser, dx description, code autocompletes, Edit (super
 * codes, ViewBillingONFavourite); Master Record ▸ Billing History ▸ Edit (correction) ▸ code and dx
 * Search popups ▸ update description (BillingDigUpdate / BillingCodeUpdate).
 *
 * Asserts every save against MariaDB (ctl_billingservice, ctl_diagcode, ctl_billingtype,
 * ctl_billingservice_premium, clinic_location, billingservice, diagnosticcode,
 * billing_on_favourite), that the owned form, its codes, the private code and the location are
 * offered on the bill form for the owned patient, and that GET against the add mutator writes
 * nothing. Fixtures: two owned OHIP-shaped service codes and two unused dx codes (descriptions
 * carry the run marker), one owned bill for the correction page (seedOwnedBill); the form, the
 * location, the private code and the favourite are created through the UI. Cleanup deletes every
 * owned row by code/marker and asserts it is gone. Implements coverage-plan §2.7
 * billing-on-admin-config.
 */

const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { seedOwnedBill, openHistory, openCorrection, billDate } = require('./billing-on-invoice-3rdparty-playwright-checks');

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

/** A self-closing result popup (posted into a named window): wait for it to open and close. */
async function postsToClosingPopup(s, click) {
  const opened = s.context.waitForEvent('page', { timeout: 20000 });
  await click();
  const popup = await opened;
  if (!popup.isClosed()) await popup.waitForEvent('close', { timeout: 20000 });
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
  const locationName = `${marker} loc`;
  const descA = `${marker} svc A`;
  const descB = `${marker} svc B`;
  const dxDescA = `${token} dx A`;
  const dxDescB = `${token} dx B`;
  const codes = [codeA, codeB, privateCode].map(h.sqlString).join(',');

  s.cleanup(() => {
    sql.execute(`DELETE FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId)};
      DELETE FROM ctl_diagcode WHERE servicetype=${h.sqlString(typeId)};
      DELETE FROM ctl_billingtype WHERE servicetype=${h.sqlString(typeId)};
      DELETE FROM ctl_billingservice_premium WHERE service_code IN (${codes});
      DELETE FROM clinic_location WHERE clinic_location_no=${h.sqlString(location)} AND clinic_location_name=${h.sqlString(locationName)};
      DELETE FROM billing_on_favourite WHERE name=${h.sqlString(marker)};
      DELETE FROM billingservice WHERE service_code IN (${codes});
      DELETE FROM diagnosticcode WHERE diagnostic_code IN (${h.sqlString(dxA)},${h.sqlString(dxB)}) AND description LIKE ${h.sqlString(`${token}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId)})
      + (SELECT COUNT(*) FROM ctl_diagcode WHERE servicetype=${h.sqlString(typeId)})
      + (SELECT COUNT(*) FROM ctl_billingtype WHERE servicetype=${h.sqlString(typeId)})
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
    VALUES (${h.sqlString(dxA)}, ${h.sqlString(dxDescA)}, 'A', 'ON'), (${h.sqlString(dxB)}, ${h.sqlString(dxDescB)}, 'A', 'ON')`);
  h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code IN (${codes})`) === '2'
    && sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE description LIKE ${h.sqlString(`${token}%`)}`) === '2',
  'The owned service and dx code fixtures were not created');

  const ctlServices = () => sql.rows(`SELECT service_group, service_group_name, service_code, servicetype_name
    FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId)} ORDER BY service_group, service_order, service_code`)
    .map(row => row.join('|')).join(';');
  const ctlDx = () => sql.rows(`SELECT diagnostic_code FROM ctl_diagcode WHERE servicetype=${h.sqlString(typeId)}
    ORDER BY diagnostic_code`).map(row => row[0]).join(',');
  const billType = () => sql.value(`SELECT billtype FROM ctl_billingtype WHERE servicetype=${h.sqlString(typeId)}`);

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

  await s.step('Add Billing Form refuses an empty id in the browser and GET at the server, then adds the owned form', async () => {
    frame = await adminFrame(admin, FORM_ROUTE, 'form[name="serviceform"]');
    await manageForm(admin, frame, '000');
    const add = frame.locator('form[name="servicetypeform"]');
    await add.waitFor({ state: 'visible' });
    const dialogs = await h.withExpectedDialogs(admin, () => add.locator('input[name="addForm"]').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'An empty service type id did not raise the required-field alert');
    const refused = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/DbManageBillingformAdd'), {
      params: { typeid: typeId, type: formName, group1: 'G1', group2: 'G2', group3: 'G3', billtype: 'ODP' }, maxRedirects: 0,
    });
    h.assert(refused.status() === 405, 'DbManageBillingformAdd must reject GET');
    h.assert(ctlServices() === '' && ctlDx() === '' && billType() === '', 'A refused add wrote billing form rows');
    await add.locator('input[name="typeid"]').fill(typeId);
    await add.locator('input[name="type"]').fill(formName);
    await add.locator('input[name="group1"]').fill('FAKE Group One');
    await add.locator('input[name="group2"]').fill('FAKE Group Two');
    await add.locator('input[name="group3"]').fill('FAKE Group Three');
    await add.locator('select[name="billtype"]').selectOption('ODP');
    await navigates(admin, frame, add.locator('input[name="addForm"]'));
    await expectValue(sql, `SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId)}`, '3',
      'Adding the billing form did not write its three service groups');
    h.assert(ctlServices() === ['Group1|FAKE Group One', 'Group2|FAKE Group Two', 'Group3|FAKE Group Three']
      .map(group => `${group}|A007A|${formName}`).join(';'), 'The added form rows do not carry the typed name and groups');
    h.assert(ctlDx() === '000' && billType() === 'ODP', 'The added form did not seed its dx row and default bill type');
    await manageForm(admin, frame, '000');
    h.assert(await frame.locator('a[title="Manage Billing Form"]', { hasText: formName }).count() === 1,
      'The existing-forms list does not show the added form');
    h.assert(await frame.locator(`form[name="serviceform"] select[name="billingform"] option[value="${typeId}"]`).count() === 1,
      'The form chooser does not offer the added form');
  });

  await s.step('a second add with the same id is refused with a message and writes nothing', async () => {
    const add = frame.locator('form[name="servicetypeform"]');
    await add.locator('input[name="typeid"]').fill(typeId);
    await add.locator('input[name="type"]').fill(`${formName} dup`);
    await navigates(admin, frame, add.locator('input[name="addForm"]'));
    h.assert((await frame.locator('form[name="servicetypeform"]').innerText()).includes(`Service Type ID '${typeId}' already exists`),
      'The duplicate add did not explain the refusal');
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(typeId)}`) === '3',
      'The duplicate add wrote rows');
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

  await s.step('the form\'s default bill type changes through the manage-type panel', async () => {
    await manageForm(admin, frame, '000');
    await frame.locator('a[title="Manage Billing Form"]', { hasText: typeId }).first().click();
    const panel = frame.locator('#manage_type');
    await panel.locator('select[name="billtype_new"]').waitFor({ state: 'visible' });
    h.assert(await panel.locator('input[name="billtype_old"]').inputValue() === 'ODP', 'The manage-type panel did not load the bill type');
    await panel.locator('select[name="billtype_new"]').selectOption('WCB');
    await postsToClosingPopup(s, () => panel.locator('input[type="button"][value="Change"]').click());
    await expectValue(sql, `SELECT billtype FROM ctl_billingtype WHERE servicetype=${h.sqlString(typeId)}`, 'WCB',
      'The bill type change did not reach ctl_billingtype');
    await frame.waitForLoadState('domcontentloaded');
  });

  await s.step('a premium code is added and removed again from the premium list', async () => {
    await manageForm(admin, frame, '***');
    const addForm = frame.locator('form[action="DbManageBillingformPremium"]');
    await addForm.locator('input[name="service1"]').fill(codeA);
    await navigates(admin, frame, addForm.locator('input[type="submit"]'));
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(status), MAX(servicetype_name)) FROM ctl_billingservice_premium
      WHERE service_code=${h.sqlString(codeA)}`, '1|A|Office', 'The premium add did not write one active row');
    await manageForm(admin, frame, '***');
    const deleteForm = frame.locator('form[action="DbManageBillingformPremiumDelete"]');
    const box = deleteForm.locator(`input[type="checkbox"][value="${codeA}"]`);
    h.assert(await box.count() === 1, 'The premium list does not offer the owned code');
    await box.check();
    await navigates(admin, frame, deleteForm.locator('input[type="submit"]'));
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
    h.assert(await frame.locator('tr', { hasText: locationName }).count() === 1, 'The location list does not show the owned location');
  });
}

if (require.main === module) runWorkflow('billing-on-admin-config', workflow, { openPatient: true });
module.exports = { workflow, openAdmin, adminFrame, navigates };
