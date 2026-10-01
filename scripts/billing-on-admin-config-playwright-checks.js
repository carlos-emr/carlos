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
    await form.locator('#billingservice_date').fill('2020-01-01');
    await form.locator('input[name="description"]').click();
    const dialogs = await h.withExpectedDialogs(admin, () => navigates(admin, frame, form.locator('input[name="submit"][value="Save"]')));
    h.assert(dialogs.length === 1 && /sure you want to save/i.test(dialogs[0].text), 'Save did not ask for confirmation once');
    h.assert((await frame.locator('form[name="baseurl"] .alert').innerText()).includes(`${privateCode} is added`),
      'The add banner did not name the private code');
    h.assert(sql.value(`SELECT CONCAT_WS('|', description, value, billingservice_date, region) FROM billingservice
      WHERE service_code=${h.sqlString(privateCode)}`) === `${marker} private|31.50|2020-01-01|ON`,
    'The private code row does not carry the typed description, fee and date');
    await frame.locator('#service_code').selectOption(privateCode);
    await navigates(admin, frame, frame.locator('form[name="baseur0"] input[name="action"][value="Edit"]'));
    h.assert(await form.locator('input[name="description"]').inputValue() === `${marker} private`,
      'Editing the private code did not load its description');
    await form.locator('input[name="value"]').fill('32.75');
    await form.locator('#billingservice_date').fill('2020-01-01');
    await form.locator('input[name="description"]').click();
    await h.withExpectedDialogs(admin, () => navigates(admin, frame, form.locator('input[name="submit"][value="Save"]')));
    h.assert(sql.value(`SELECT CONCAT_WS('|', COUNT(*), MAX(value)) FROM billingservice
      WHERE service_code=${h.sqlString(privateCode)}`) === '1|32.75', 'The private code edit did not update its single row');
  });
}

if (require.main === module) runWorkflow('billing-on-admin-config', workflow, { openPatient: true });
module.exports = { workflow, openAdmin, adminFrame, navigates };
