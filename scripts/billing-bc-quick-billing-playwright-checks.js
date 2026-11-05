#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Administration ▸ Billing ▸ BC MSP Quick Billing: a batch of MSP bills for one billing physician,
 * service location and day, one line per patient, saved together. No browser check drove this page.
 * User path: Schedule ▸ Administration ▸ Billing ▸ BC MSP Quick Billing (quickBillingBC, in the
 * #dynamic-content iframe) ▸ Billing Physician, Service Location, Visit Date (today, from the
 * calendar) ▸ for each patient: Pt. Name (the name search; pick the patient), Billing Code, DX Code ▸
 * add (the page reloads with the line listed) ▸ remove on a line ▸ Submit (saveQuickBillingBC) ▸
 * "N Invoice(s) Saved".
 * Asserted: the page lists the owned billing physician and the BC service locations; the first add
 * lists the owned patient's line (PHN, name, the owned code and its fee) and locks the batch's
 * header; a second patient's add lists a second line; remove takes out only the line it is pressed
 * on; a GET carrying an add or a remove is refused with 405 (the saved count below shows the list was
 * not changed by it); reopening the page retains both queued invoices and the locked header;
 * Submit says 2 invoices were saved and writes, for each listed patient, one MSP
 * bill (billing, status O) and one billingmaster line with the code, fee, diagnosis, PHN, the
 * physician's practitioner and payee numbers, claim code C02, the service location and today's
 * service date, plus its billing-history archive and the quick-billing note; a replay of that
 * Submit saves nothing more; a GET to Save is refused and writes nothing.
 * Known failure, recorded in the manifest notes: on release/2026.08 the page answers HTTP 500 on
 * every request (#4772; #4789 is its release fix), so the first step fails. With #4789 every step
 * passes. The GET Save refusal comes from HttpMethodGuardFilter (the "save" action prefix).
 * Fixtures: one billing physician (bc.createBillingPhysician), one BC fee code (billingservice,
 * 9xxxx, 12.34, described by the run marker), the workflow's own FAKE patient and a second FAKE
 * patient (same marker last name), both with a synthetic BC PHN and an admission to the login's
 * first program (the name search lists only patients in the login's programs). Cleanup deletes every
 * BC billing row of the two patients and their admissions, then the second patient, the code and the
 * physician, and asserts each is gone. BC installs only (billregion=BC).
 */
const { authenticatedProbe } = require('./lib/authenticated-probe');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { expectValue, runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const ROUTE = '/quickBillingBC';
const SAVE_ROUTE = '/saveQuickBillingBC';
const FEE = '12.34';
const DX = '780';
const LOCATION = 'A';
const B_FIRST_NAME = 'PatientBravo';
const NOTE = 'MSP Billing done by quick billing method.';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ BC MSP Quick Billing opens and lists the owned billing physician and the BC service locations',
  first: "the first add lists the owned patient's line with its PHN, code and fee, and locks the header",
  second: "a second patient's add lists a second line",
  remove: 'remove takes out only the line it is pressed on',
  getChange: 'a GET carrying an add or a remove is refused with 405',
  save: 'Submit saves one MSP bill per listed patient and says how many',
  replay: 'a replay of the same Submit saves nothing more',
  getSave: 'a GET to Save is refused and writes nothing',
};

/**
 * The second patient, owned by this run; removed after its bills (cleanups run last-registered first).
 * The cleanup is registered before the insert and finds the patient by the marker and first name, so
 * a row whose id read was lost is still removed.
 */
function createSecondPatient(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const owned = `last_name=${q(marker)} AND first_name=${q(B_FIRST_NAME)}`;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT demographic_no FROM demographic WHERE ${owned}`).map(([id]) => id).filter(id => /^\d+$/.test(id));
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(['demographicExt', 'demographicArchive']
        .map(table => `DELETE FROM ${table} WHERE demographic_no IN (${list})`).join(';'));
      sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${list}) AND ${owned}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE ${owned}`) === '0', 'The second patient was not removed');
  });
  return h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
      date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'BC', 'BC', 'NR', NOW())`,
  'second patient');
}

/**
 * The name search lists only patients admitted to one of the login's programs
 * (DemographicDaoImpl PROGRAM_DOMAIN_RESTRICTION), so each owned patient gets one owned admission
 * row, removed by its client id before the patient.
 */
function admitOwnedPatients(s, patients) {
  const { sql, provider } = s;
  const q = h.sqlString;
  const list = patients.join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM admission WHERE client_id IN (${list})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM admission WHERE client_id IN (${list})`) === '0', 'The owned admissions were not removed');
  });
  const program = sql.value(`SELECT program_id FROM program_provider WHERE provider_no=${q(provider)} ORDER BY program_id LIMIT 1`);
  h.assert(/^[1-9]\d*$/.test(program), 'The test login belongs to no program, so no patient is searchable');
  for (const patient of patients) {
    sql.execute(`INSERT INTO admission (client_id,program_id,provider_no,admission_date,admission_from_transfer,
        discharge_from_transfer,admission_status,lastUpdateDate)
      VALUES (${patient},${program},${q(provider)},NOW(),0,0,'current',NOW())`);
  }
}

/** One BC fee code, described by the run marker. */
function createFeeCode(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const owned = {};
  s.cleanup(() => {
    if (owned.code) sql.execute(`DELETE FROM billingservice WHERE service_code=${q(owned.code)} AND description=${q(`${marker} fee`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE description LIKE ${q(`${marker}%`)}`) === '0', 'The owned fee code was not removed');
  });
  owned.code = bc.unusedNumber('9', 5, c => sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${q(c)}`) !== '0');
  sql.execute(`INSERT INTO billingservice (service_code,description,value,percentage,billingservice_date,specialty,region,anaesthesia)
    VALUES (${q(owned.code)},${q(`${marker} fee`)},${q(FEE)},'',DATE_SUB(CURDATE(),INTERVAL 1 YEAR),'','BC','00')`);
  return owned.code;
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
  if (sql.value(`SELECT COUNT(*) FROM billingvisit WHERE region='BC' AND visittype=${h.sqlString(LOCATION)}`) !== '1') {
    throw new h.SkipCheck(`The BC service location ${LOCATION} is not in billingvisit`);
  }
}

async function workflow(s) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const physician = bc.createBillingPhysician(s);
  const code = createFeeCode(s);
  const second = createSecondPatient(s);
  admitOwnedPatients(s, [patient, second]);
  const phnA = bc.makeBcPatient(s);
  const phnB = bc.makeBcPatient({ ...s, patient: second });
  bc.registerOwnedBcBillCleanup(s);
  bc.registerOwnedBcBillCleanup({ ...s, patient: second });
  const patients = `${patient},${second}`;
  const bills = () => sql.value(`SELECT (SELECT COUNT(*) FROM billing WHERE demographic_no IN (${patients}))
    + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no IN (${patients}))`);
  let serviceDate;

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-quick-billing-admin' });
  let frame;
  const lines = () => frame.locator('#inputList tr').filter({ has: frame.locator('a#removeBill') });
  const reloaded = (pathEnd, query) => admin.waitForResponse(r => r.request().method() === 'POST'
    && new URL(r.url()).pathname.endsWith(pathEnd) && (!query || new URL(r.url()).searchParams.has(query)), { timeout: TIMEOUT });
  // add, remove and Submit are form submits in the iframe: the response arrives before the new document
  // commits, so each waits for the frame's own navigation before it reads the page.
  const navigation = () => {
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
    navigated.catch(() => {}); // awaited in settle(); a step that fails before then reports its own error
    return navigated;
  };
  const settle = async (response, navigated, label) => {
    h.assert(response.status() === 200, `${label} answered HTTP ${response.status()}`);
    await navigated;
    await frame.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(frame, label);
  };
  const add = async (firstName, label) => {
    await frame.locator('#ptName').fill(marker);
    const item = frame.locator('ul.ui-autocomplete li').filter({ hasText: firstName });
    await item.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await item.count() === 1, `The name search did not list the owned patient ${firstName} exactly once`);
    await item.first().click();
    await frame.locator('#billingCode').fill(code);
    await frame.locator('#dxCode1').fill(DX);
    const navigated = navigation();
    const [response] = await Promise.all([reloaded(ROUTE, 'data'), frame.locator('#addDemo').click()]);
    await settle(response, navigated, label);
  };
  const lineFor = (firstName) => lines().filter({ hasText: `${marker}, ${firstName}` });

  await s.step(STEP.open, async () => {
    const link = admin.locator(`a.xlink[rel$="${ROUTE}"]`).first();
    await link.waitFor({ state: 'attached', timeout: TIMEOUT });
    await revealAuditLink(admin, link, 20000);
    const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(ROUTE), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `BC MSP Quick Billing answered HTTP ${response.status()}`);
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({ timeout: TIMEOUT });
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'BC MSP Quick Billing did not load into the Administration panel');
    await frame.locator('#providers').waitFor({ timeout: TIMEOUT });
    await h.assertNotErrorPage(frame, 'BC MSP Quick Billing');
    h.assert(await frame.locator(`#providers option[value="${physician.providerNo}"]`).count() === 1,
      'Quick Billing does not list the owned billing physician');
    h.assert(await frame.locator(`#visitLocation option[value="${LOCATION}"]`).count() === 1,
      `Quick Billing does not list the BC service location ${LOCATION}`);
  });

  await s.step(STEP.first, async () => {
    await frame.locator('#providers').selectOption(physician.providerNo);
    await frame.locator('#visitLocation').selectOption(LOCATION);
    // The patient line appears once the visit date's calendar closes on a picked day.
    await frame.locator('#visitDate').click();
    await frame.locator('.flatpickr-calendar.open .flatpickr-day.today').click();
    await frame.locator('#ptName').waitFor({ state: 'visible', timeout: TIMEOUT });
    // The expected service date is the day the calendar wrote (the browser's today), as dd-mm-yyyy.
    const picked = /^(\d{2})-(\d{2})-(\d{4})$/.exec(await frame.locator('#visitDate').inputValue());
    h.assert(picked, 'The calendar did not write a dd-mm-yyyy visit date');
    serviceDate = `${picked[3]}${picked[2]}${picked[1]}`;
    await add('Workflow', 'The first add');
    h.assert(await lines().count() === 1, `The first add lists ${await lines().count()} lines, not 1`);
    const text = await lineFor('Workflow').innerText();
    h.assert(text.includes(phnA) && text.includes(code) && text.includes(`${marker} fee`) && text.includes(FEE),
      `The first line does not show the PHN, the owned code and its fee: ${JSON.stringify(text)}`);
    h.assert(await frame.locator('#providers').isDisabled() && await frame.locator('#visitLocation').isDisabled()
      && await frame.locator('#visitDate').isDisabled(), 'The batch header is not locked after the first add');
  });

  await s.step(STEP.second, async () => {
    await add(B_FIRST_NAME, 'The second add');
    h.assert(await lines().count() === 2, `After the second add the list has ${await lines().count()} lines, not 2`);
    h.assert((await lineFor(B_FIRST_NAME).innerText()).includes(phnB), "The second line does not show the second patient's PHN");
  });

  await s.step(STEP.remove, async () => {
    await add('Workflow', 'The third add');
    h.assert(await lines().count() === 3, `After the third add the list has ${await lines().count()} lines, not 3`);
    const navigated = navigation();
    const [response] = await Promise.all([reloaded(ROUTE, 'remove'), lines().nth(2).locator('a#removeBill').click()]);
    await settle(response, navigated, 'Remove');
    h.assert(await lines().count() === 2, `After remove the list has ${await lines().count()} lines, not 2`);
    h.assert(await lineFor('Workflow').count() === 1 && await lineFor(B_FIRST_NAME).count() === 1,
      'Remove did not leave one line for each patient');
    h.assert(bills() === '0', 'Adding and removing lines wrote bills');
  });

  await s.step(STEP.getChange, async () => {
    const data = JSON.stringify({ provider: physician.providerNo, visitLocation: LOCATION, visitDate: '', ptName: '',
      ptNumber: String(patient), billingCode: code, halfBilling: '1.0', dxCode1: DX });
    for (const query of [`data=${encodeURIComponent(data)}`, 'remove=0']) {
      const response = await authenticatedProbe(s, h.appUrl(s.config.baseUrl, `${ROUTE}?${query}`), { maxRedirects: 0 });
      h.assert(response.status() === 405, `A GET ${query.split('=')[0]} answered ${response.status()}, not 405`);
    }
    h.assert(bills() === '0', 'A refused GET wrote bills');
    const reopened = await frame.goto(h.appUrl(s.config.baseUrl, ROUTE));
    h.assert(reopened.status() === 200, `Reopening Quick Billing answered HTTP ${reopened.status()}`);
    await h.assertNotErrorPage(frame, 'Reopened Quick Billing');
    h.assert(await lines().count() === 2, 'Reopening Quick Billing discarded queued invoices');
    h.assert(await lineFor('Workflow').count() === 1 && await lineFor(B_FIRST_NAME).count() === 1,
      'Reopening Quick Billing changed the queued patients');
    h.assert(await frame.locator('#providers').inputValue() === physician.providerNo
      && await frame.locator('#visitLocation').inputValue() === LOCATION,
    'Reopening Quick Billing changed the batch header');
    h.assert(await frame.locator('#providers').isDisabled() && await frame.locator('#visitDate').isDisabled(),
      'Reopening Quick Billing unlocked the queued batch header');
  });

  let submitted;
  await s.step(STEP.save, async () => {
    const captured = admin.waitForRequest(r => r.method() === 'POST' && new URL(r.url()).pathname.endsWith(SAVE_ROUTE), { timeout: TIMEOUT });
    const navigated = navigation();
    const [response] = await Promise.all([reloaded(SAVE_ROUTE), frame.locator('#submitList').click()]);
    submitted = await captured;
    await settle(response, navigated, 'Submit');
    await frame.locator('#saved').waitFor({ state: 'visible', timeout: TIMEOUT });
    const said = (await frame.locator('#saved').innerText()).replace(/\s+/g, ' ').trim();
    h.assert(said === '2 Invoice(s) Saved', `Submit says "${said}", not "2 Invoice(s) Saved"`);
    await expectValue(sql, `SELECT COUNT(*) FROM billingmaster WHERE demographic_no IN (${patients})`, '2',
      'Submit did not write exactly two billingmaster lines');
    for (const [who, phn] of [[patient, phnA], [second, phnB]]) {
      const header = sql.value(`SELECT CONCAT_WS('|',COUNT(*),MAX(billingtype),MAX(status),MAX(provider_no),MAX(appointment_no))
        FROM billing WHERE demographic_no=${who}`);
      h.assert(header === `1|MSP|O|${physician.providerNo}|0`, `Patient ${who}'s bill header is ${header}, not 1|MSP|O|${physician.providerNo}|0`);
      const line = sql.value(`SELECT CONCAT_WS('|',billing_code,CAST(bill_amount AS DECIMAL(9,2)),dx_code1,phn,practitioner_no,payee_no,
          claimcode,service_location,service_date,billingstatus) FROM billingmaster WHERE demographic_no=${who}`);
      const expected = [code, FEE, DX, phn, physician.ohipNo, physician.payeeNo, 'C02', LOCATION, serviceDate, 'O'].join('|');
      h.assert(line === expected, `Patient ${who}'s billingmaster line is ${line}, not ${expected}`);
      const extras = sql.value(`SELECT CONCAT_WS('|',
          (SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=bm.billingmaster_no),
          (SELECT COUNT(*) FROM billingnote WHERE billingmaster_no=bm.billingmaster_no AND note=${q(NOTE)}))
        FROM billingmaster bm WHERE bm.demographic_no=${who}`);
      h.assert(extras === '1|1', `Patient ${who}'s line has ${extras} history|note rows, not 1|1`);
    }
    h.assert(await lines().count() === 0, 'The saved page still lists lines');
  });

  await s.step(STEP.replay, async () => {
    const response = await s.context.request.post(submitted.url(), { headers: { 'content-type': submitted.headers()['content-type'] },
      data: submitted.postData() || '', maxRedirects: 0 });
    h.assert(response.status() < 400, `The replayed Submit answered ${response.status()}`);
    h.assert(bills() === '4', `After the replay the two patients have ${bills()} bill rows, not 4`);
  });

  await s.step(STEP.getSave, async () => {
    // A fresh batch with one line, so a GET that saves would have something to write.
    await frame.locator('#providers').selectOption(physician.providerNo);
    await frame.locator('#visitLocation').selectOption(LOCATION);
    await frame.locator('#visitDate').click();
    await frame.locator('.flatpickr-calendar.open .flatpickr-day.today').click();
    await frame.locator('#ptName').waitFor({ state: 'visible', timeout: TIMEOUT });
    await add('Workflow', 'The add before the GET');
    h.assert(await lines().count() === 1, `The new batch lists ${await lines().count()} lines, not 1`);
    const response = await authenticatedProbe(s, h.appUrl(s.config.baseUrl, SAVE_ROUTE), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET Save answered ${response.status()}, not 405`);
    h.assert(bills() === '4', `A refused GET Save left ${bills()} bill rows, not 4`);
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-quick-billing', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
