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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for adding a consultant (professional specialist) including
 * the CPSO registry lookup, which alpha-11 testers reported working.
 *
 * The CPSO widget on the Add Specialist page debounces the name inputs and
 * GETs /encounter/CpsoSearch, a server-side proxy to register.cpso.on.ca. A
 * regression check cannot depend on the live registry, so the check fulfils
 * that route with a canned payload in the shape CpsoSearch2Action returns
 * and asserts the widget renders it and copies the picked physician's name,
 * address, phone and fax into the form. The server proxy itself is probed
 * once without the stub and must answer 200 with either results or the
 * documented CPSO_SERVICE_UNAVAILABLE degradation, never an error page.
 *
 *   1. Administration > Add Specialist page (ViewAddSpecialist);
 *   2. CPSO lookup by last name -> pick the result -> form fields populated;
 *   3. specialty, professional letters, referral number -> "Add Specialist";
 *      assert "Specialist ... has been saved." and the professionalSpecialists
 *      row (names, address, phone, fax, specType, referralNo, not deleted);
 *   4. the consultant is listed on the Edit Specialists page; they are then
 *      assigned to their service through Show All Services > <service> >
 *      "Update these Services Specialists" (the consultation request's
 *      consultant picker only offers specialists in the serviceSpecialists
 *      join table -- the Add Specialist specialty alone is not enough), and
 *      the picker must then offer them;
 *   5. delete through the Edit Specialists list and assert the soft delete.
 *
 * The row is removed for good in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  validateBaseUrl,
  validateMysqlHost,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

const stampDigits = String(Date.now()).slice(-8);
const specialist = {
  lastName: `PLAYWRIGHT-CPSO-${stampDigits}`,
  firstName: 'Consult',
  address: '123 Registry Way, Toronto, ON, M5H 2N2',
  phone: '416-555-0100',
  fax: '416-555-0199',
  cpsoNumber: `9${stampDigits.slice(0, 5)}`,
  referralNo: `${stampDigits.slice(0, 6)}`,
  proLetters: 'MD FRCPC',
};

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'specialist-cpso-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function specialistRow() {
  const out = sql(`SELECT specId, fName, lName, proLetters, address, phone, fax, specType, referralNo, deleted FROM professionalSpecialists WHERE lName='${escapeSql(specialist.lastName)}' ORDER BY specId DESC LIMIT 1`);
  if (!out) {
    return null;
  }
  const [specId, fName, lName, proLetters, address, phone, fax, specType, referralNo, deleted] = out.split('\t');
  return { specId, fName, lName, proLetters, address, phone, fax, specType, referralNo, deleted };
}
function cleanupRows() {
  const ids = sql(`SELECT specId FROM professionalSpecialists WHERE lName='${escapeSql(specialist.lastName)}'`);
  for (const id of ids ? ids.split('\n') : []) {
    sql(`DELETE FROM serviceSpecialists WHERE specId=${Number(id)}`);
    sql(`DELETE FROM professionalSpecialists WHERE specId=${Number(id)}`);
  }
}

function serviceLabelFor(serviceId) {
  const label = sql(`SELECT serviceDesc FROM consultationServices WHERE serviceId=${Number(serviceId)}`);
  assert(label, `consultationServices has no row for service ${serviceId}`);
  return label;
}

function cannedCpsoPayload() {
  return {
    totalcount: 1,
    results: [{
      name: `${specialist.lastName}, ${specialist.firstName}`,
      street1: '123 Registry Way',
      street2: '',
      street3: '',
      city: 'Toronto',
      province: 'ON',
      postalcode: 'M5H 2N2',
      specialties: 'Cardiology',
      phonenumber: specialist.phone,
      fax: specialist.fax,
      cpsonumber: specialist.cpsoNumber,
      registrationstatus: 'Active',
    }],
  };
}

async function openAddSpecialist(context, recorder, label) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/encounter/oscarConsultationRequest/config/ViewAddSpecialist');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'add specialist');
  await page.locator('form[action$="/encounter/AddSpecialist"]').waitFor({ state: 'attached', timeout: 30000 });
  return page;
}

let browser = null;
let cleanupDone = false;
// Runs once from the finally block or the signal handler: every step is attempted
// and a step that fails marks the run as failed, because a fixture left behind is
// a failure of this check even when every assertion passed.
function runCleanup() {
  if (cleanupDone || !mysqlDefaults) {
    return;
  }
  cleanupDone = true;
  for (const step of [cleanupRows]) {
    try {
      step();
    } catch (cleanupError) {
      console.error(`FAIL cleanup step ${step.name} failed: ${cleanupError.message}`);
      process.exitCode = 1;
    }
  }
}
// Node does not run finally blocks on SIGINT/SIGTERM (the suite loop's `timeout`
// sends TERM), so restore the fixtures here too before exiting.
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    console.error(`${signal} received; restoring fixtures before exiting.`);
    runCleanup();
    cleanupMysqlDefaults();
    process.exit(130);
  });
}

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  // Staging and the browser launch sit inside the protected scope so a failure in
  // either still reaches the fixture cleanup below.
  try {
    cleanupRows();
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    await login(context, config, recorder);

    // Live proxy probe: the server must degrade gracefully when the registry
    // is unreachable (the usual state of a test host) and never error.
    const probe = await openAddSpecialist(context, recorder, 'cpso-live-probe');
    const [liveResponse] = await Promise.all([
      probe.waitForResponse((response) => /\/encounter\/CpsoSearch\?/.test(response.url()), { timeout: 30000 }),
      probe.locator('#cpsoLastName').fill('Smith'),
    ]);
    assert(liveResponse.status() === 200, `CpsoSearch proxy answered HTTP ${liveResponse.status()}`);
    const liveJson = await liveResponse.json().catch(() => null);
    assert(liveJson && (Array.isArray(liveJson.results) || liveJson.errorCode === 'CPSO_SERVICE_UNAVAILABLE'),
      `CpsoSearch proxy returned an unexpected body: ${JSON.stringify(liveJson).slice(0, 200)}`);
    await probe.locator('#cpsoResults').waitFor({ state: 'visible', timeout: 15000 });
    const liveText = await probe.locator('#cpsoResults').innerText();
    assert(/CPSO search unavailable|No results|Too many results|CPSO/i.test(liveText), `CPSO widget rendered unexpected text: ${liveText}`);
    await probe.close();

    // 1-2. Deterministic lookup through a stubbed registry answer.
    const page = await openAddSpecialist(context, recorder, 'add-specialist');
    await page.route('**/encounter/CpsoSearch**', (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(cannedCpsoPayload()),
    }));
    await page.locator('#cpsoLastName').fill(specialist.lastName);
    const resultRow = page.locator('#cpsoResults .cpso-result-item').first();
    await resultRow.waitFor({ state: 'visible', timeout: 15000 });
    const resultText = await resultRow.innerText();
    assert(resultText.includes(specialist.lastName) && resultText.includes(specialist.cpsoNumber) && /Active/.test(resultText),
      `CPSO result did not render the physician: ${resultText}`);
    await resultRow.click();
    assert((await page.locator('#lastName').inputValue()) === specialist.lastName, 'CPSO pick did not fill the last name');
    assert((await page.locator('#firstName').inputValue()) === specialist.firstName, 'CPSO pick did not fill the first name');
    assert((await page.locator('#address').inputValue()) === specialist.address, `CPSO pick filled address "${await page.locator('#address').inputValue()}"`);
    assert((await page.locator('#phone').inputValue()) === specialist.phone, 'CPSO pick did not fill the phone');
    assert((await page.locator('#fax').inputValue()) === specialist.fax, 'CPSO pick did not fill the fax');
    assert(!(await page.locator('#cpsoResults').isVisible()), 'CPSO results stayed open after picking a physician');

    // 3. Complete and submit.
    const specTypeSelect = page.locator('#specType');
    const specTypeValue = await specTypeSelect.locator('option').evaluateAll((options) => {
      const real = options.find((option) => option.value && option.value !== '0');
      return real ? real.value : null;
    });
    assert(specTypeValue, 'Add Specialist offered no specialty to choose');
    await specTypeSelect.selectOption(specTypeValue);
    await page.locator('#proLetters').fill(specialist.proLetters);
    await page.locator('#referralNo').fill(specialist.referralNo);
    await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/encounter/AddSpecialist'), { timeout: 30000 }),
      page.locator('input[name="transType"][value="Add Specialist"]').click(),
    ]);
    await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(page, 'add specialist result');
    const savedBanner = await page.locator('.alert-success').first().innerText().catch(() => '');
    assert(savedBanner.includes(`Specialist ${specialist.firstName} ${specialist.lastName} has been saved`),
      `add specialist banner was "${savedBanner}"`);
    const row = specialistRow();
    assert(row, 'professionalSpecialists row was not created');
    assert(row.fName === specialist.firstName && row.lName === specialist.lastName, `saved names were ${row.fName} ${row.lName}`);
    assert(row.address === specialist.address, `saved address was "${row.address}"`);
    assert(row.phone === specialist.phone && row.fax === specialist.fax, `saved phone/fax were ${row.phone}/${row.fax}`);
    assert(row.proLetters === specialist.proLetters, `saved letters were "${row.proLetters}"`);
    assert(row.specType === specTypeValue, `saved specType ${row.specType}, expected ${specTypeValue}`);
    assert(row.referralNo === specialist.referralNo, `saved referral number was ${row.referralNo}`);
    assert(row.deleted === '0', 'new specialist was created already deleted');
    await page.unroute('**/encounter/CpsoSearch**');

    // 4. Listed for editing and offered as a consultant.
    const listPage = await context.newPage();
    // The Delete button asks through window.confirm; answer in-page so the
    // shared recorder (which dismisses stray dialogs) cannot cancel it.
    await listPage.addInitScript(() => {
      window.confirm = (message) => {
        try { sessionStorage.setItem('pwLastConfirm', String(message)); } catch (ignored) { /* storage unavailable */ }
        return true;
      };
    });
    wirePage(listPage, 'edit-specialists', recorder);
    await gotoApp(listPage, config.baseUrl, '/encounter/oscarConsultationRequest/config/ViewEditSpecialists');
    await listPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(listPage, 'edit specialists');
    const editLink = listPage.locator(`a[href*="/encounter/EditSpecialists?specId=${row.specId}"]`).first();
    assert(await editLink.count(), 'Edit Specialists list did not show the new consultant');

    // Assign the consultant to the chosen service the way an operator does.
    const servicesPage = await context.newPage();
    wirePage(servicesPage, 'show-all-services', recorder);
    await gotoApp(servicesPage, config.baseUrl, '/encounter/oscarConsultationRequest/config/ViewShowAllServices');
    await servicesPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(servicesPage, 'show all services');
    await servicesPage.locator(`a[href*="/encounter/ShowAllServices?serviceId=${specTypeValue}&"]`).first().click();
    await servicesPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(servicesPage, 'service specialists');
    const assignedBefore = Number(sql(`SELECT COUNT(*) FROM serviceSpecialists WHERE serviceId=${Number(specTypeValue)}`));
    const preChecked = await servicesPage.locator('input[name="specialists"]:checked').count();
    assert(preChecked === assignedBefore, `service page pre-checked ${preChecked} specialists but ${assignedBefore} are assigned; updating would clobber assignments`);
    const newBox = servicesPage.locator(`input[name="specialists"][value="${row.specId}"]`);
    assert(await newBox.count(), 'service page did not list the new consultant');
    await newBox.check();
    await Promise.all([
      servicesPage.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/encounter/UpdateServiceSpecialists'), { timeout: 30000 }),
      servicesPage.locator('form[action$="/encounter/UpdateServiceSpecialists"] input[type="submit"]').click(),
    ]);
    await servicesPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(servicesPage, 'service specialists after update');
    assert(sql(`SELECT COUNT(*) FROM serviceSpecialists WHERE serviceId=${Number(specTypeValue)} AND specId=${Number(row.specId)}`) === '1',
      'Update these Services Specialists did not write the serviceSpecialists row');
    assert(Number(sql(`SELECT COUNT(*) FROM serviceSpecialists WHERE serviceId=${Number(specTypeValue)}`)) === assignedBefore + 1,
      'service update changed the other specialists assigned to the service');
    await servicesPage.close();

    const consultPage = await context.newPage();
    wirePage(consultPage, 'consult-picker', recorder);
    await gotoApp(consultPage, config.baseUrl, '/encounter/oscarConsultationRequest/ViewConsultationFormRequest?de=1&teamVar=&appNo=');
    await consultPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    // The picker loads its specialist list asynchronously; typing (not
    // fill) drives the jQuery UI autocomplete search.
    // The form expects a service first (clicking the consultant box without
    // one hands focus back to the service box), so pick the service the new
    // consultant was assigned to, then type the consultant's name.
    await consultPage.waitForFunction(() => Array.isArray(window.allSpecialistsData) && window.allSpecialistsData.length > 0, null, { timeout: 30000 });
    await consultPage.locator('#serviceInput').click();
    const serviceEntry = consultPage.locator('ul.ui-autocomplete:visible li.ui-menu-item', { hasText: serviceLabelFor(specTypeValue) }).first();
    await serviceEntry.waitFor({ state: 'visible', timeout: 15000 });
    await serviceEntry.click();
    assert((await consultPage.locator('#service').inputValue()) === specTypeValue, 'consult form did not select the service');
    await consultPage.locator('#specialistInput').click();
    await consultPage.keyboard.type(specialist.lastName.slice(0, 16), { delay: 30 });
    const pickerEntry = consultPage.locator('ul.ui-autocomplete:visible li.ui-menu-item', { hasText: specialist.lastName }).first();
    await pickerEntry.waitFor({ state: 'visible', timeout: 15000 });
    await pickerEntry.click();
    assert((await consultPage.locator('#specialist').inputValue()) === row.specId, 'consult picker did not select the new consultant');
    await consultPage.close();

    // 5. Soft delete through the list.
    const rowCheckbox = listPage.locator(`input[name="specialists"][value="${row.specId}"]`);
    await rowCheckbox.check();
    await Promise.all([
      listPage.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/encounter/EditSpecialists'), { timeout: 30000 }),
      listPage.locator('input[name="delete"]').click(),
    ]);
    await listPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(listPage, 'edit specialists after delete');
    const lastConfirm = await listPage.evaluate(() => { try { return sessionStorage.getItem('pwLastConfirm') || ''; } catch (ignored) { return ''; } });
    assert(/delete the selected specialists/i.test(lastConfirm), `delete did not ask for confirmation (last prompt: "${lastConfirm}")`);
    const deletedRow = specialistRow();
    assert(deletedRow && deletedRow.deleted === '1', `delete did not soft-delete the consultant (deleted=${deletedRow && deletedRow.deleted})`);
    assert(!(await listPage.locator(`a[href*="/encounter/EditSpecialists?specId=${row.specId}"]`).count()), 'deleted consultant is still listed');

    const isMissingSignatureImage = (entry) => /providerSignatureImage/.test(entry.url || (entry.location && entry.location.url) || '');
    const badResponses = recorder.badResponses.filter((entry) => !(entry.status === 404 && isMissingSignatureImage(entry)));
    const consoleIssues = recorder.consoleIssues.filter((entry) => !isMissingSignatureImage(entry));
    assertNoPageErrors(recorder);
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
    console.log(`PASS consultant ${specialist.lastName} added through CPSO lookup (specId ${row.specId}), listed, offered in the consult picker and deleted`);
  } catch (error) {
    console.error(`FAIL specialist add / CPSO check: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
    runCleanup();
    cleanupMysqlDefaults();
  }
})();
