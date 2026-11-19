#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Live regression coverage for focused Administration navigation across the
 * Manage eForms HTML Upload and ZIP Import success redirects.
 *
 * The check is intentionally restricted to a loopback app by the shared
 * validateBaseUrl helper. It creates, exports, deletes, re-imports, and finally
 * deletes one uniquely named temporary eForm.
 */


const legacyHarness=require('./lib/legacy-check');
const h=require('./lib/playwright-harness');
const {settleOperations}=require('./graceful-signal-cancellation');
const {randomBytes}=require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { chromium } = require('playwright');
const { shouldIgnoreHttpsErrors } = require('./lib/playwright-harness');
const {
  appUrl,
  assert,
  buildArtifactPath,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');
const { removeOwnedForms } = require('./eform-admin-schedule-navigation-cleanup');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.EFORM_ADMIN_NAV_SCREENSHOT_DIR || '/tmp/pr3286-eform-admin-nav',
};

function createHtmlFixture(formName) {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-eform-admin-nav-'));
  const htmlPath = path.join(tempDir, 'schedule-navigation-check.html');
  const escapedName = formName.replace(/[&<>"']/g, (character) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
  fs.writeFileSync(htmlPath, `<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>${escapedName}</title></head>
<body>
  <form method="post">
    <input type="hidden" id="demographic_no" name="demographic_no" value="">
    <p>Temporary Playwright navigation regression eForm.</p>
    <input type="submit" value="Submit">
  </form>
</body>
</html>
`);
  return { tempDir, htmlPath };
}

async function openFocusedManager(page) {
  await gotoApp(page, config.baseUrl, '/administration?scheduleNav=1');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const managerLink = page.locator('a.defaultForms:visible').first();
  await managerLink.waitFor({ state: 'visible', timeout: 15000 });
  const managerHref = await managerLink.getAttribute('href');
  assert(
    managerHref && new URL(managerHref, page.url()).searchParams.get('scheduleNav') === '1',
    `Manage eForms link did not retain scheduleNav=1: ${managerHref}`,
  );
  await managerLink.click();
  // Wait for BOTH iframes' forms before counting: count() does not auto-wait,
  // and on a cold install (first JSP compile) the import frame finishes well
  // after the upload frame, which made this check flake on fresh VMs.
  await page.frameLocator('#uploadFrame')
    .locator('form[action$="/eform/uploadHtml"]')
    .waitFor({ state: 'visible', timeout: 15000 });
  // attached, not visible: the import form is styled hidden until its tab
  // is opened, but its scheduleNav input is already in the DOM once loaded.
  await page.frameLocator('#importFrame')
    .locator('form')
    .first()
    .waitFor({ state: 'attached', timeout: 15000 });
  assert(
    await page.frameLocator('#uploadFrame').locator('input[name="scheduleNav"][value="1"]').count(),
    'HTML Upload form did not receive scheduleNav=1',
  );
  assert(
    await page.frameLocator('#importFrame').locator('input[name="scheduleNav"][value="1"]').count(),
    'ZIP Import form did not receive scheduleNav=1',
  );
}

async function assertFocusedAdministration(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  const url = new URL(page.url());
  assert(url.pathname.endsWith('/administration'), `${label}: unexpected path ${url.pathname}`);
  assert(url.searchParams.get('show') === 'Forms', `${label}: Forms section was not restored`);
  assert(url.searchParams.get('scheduleNav') === '1', `${label}: scheduleNav was lost`);
  assert(
    await page.locator('#firstTable #navlist').count(),
    `${label}: schedule navigation is absent`,
  );
  await page.locator('#eformTbl').waitFor({ state: 'visible', timeout: 15000 });
}

async function uploadHtml(page, htmlPath, formName, formSubject) {
  const frame = page.frameLocator('#uploadFrame');
  await frame.locator('input[name="formName"]').fill(formName);
  await frame.locator('input[name="formSubject"]').fill(formSubject);
  await frame.locator('#formHtml').setInputFiles(htmlPath);
  const action=await frame.locator('form').first().getAttribute('action');
  const destination=new URL(action,config.baseUrl);assert(destination.origin===config.baseUrl.origin,'Transfer action left the application origin');
  const accepted=page.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname===destination.pathname,{timeout:120000})
    .then(async response=>{const error=await response.finished();assert(!error,'Transfer response did not finish');assert(response.status()<400,'Transfer was refused');});
  await settleOperations([
    accepted,
    page.waitForURL((url) => (
      url.pathname.endsWith('/administration')
      && url.searchParams.get('show') === 'Forms'
    ), { timeout: 30000 }),
    frame.locator('input.upload[type="submit"]').click(),
  ]);
}

async function exportEform(page, row, tempDir) {
  const zipPath = buildArtifactPath(tempDir, 'schedule-navigation-export', '.zip');
  const exportLink = row.locator('a[href*="method=exportEForm"]').first();
  await exportLink.waitFor({ state: 'visible', timeout: 15000 });
  const downloadPromise = page.waitForEvent('download', { timeout: 30000 });
  await exportLink.click();
  const download = await downloadPromise;
  await download.saveAs(zipPath);
  assert(fs.statSync(zipPath).size > 0, 'Exported ZIP was empty');
  return zipPath;
}

async function importZip(page, zipPath) {
  await page.locator('#eformOptions a[href="#import"]').click();
  await page.locator('#import').waitFor({ state: 'visible', timeout: 15000 });
  const frame = page.frameLocator('#importFrame');
  await frame.locator('#zippedForm').setInputFiles(zipPath);
  const action=await frame.locator('form').first().getAttribute('action');
  const destination=new URL(action,config.baseUrl);assert(destination.origin===config.baseUrl.origin,'Transfer action left the application origin');
  const accepted=page.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname===destination.pathname,{timeout:120000})
    .then(async response=>{const error=await response.finished();assert(!error,'Transfer response did not finish');assert(response.status()<400,'Transfer was refused');});
  await settleOperations([
    accepted,
    page.waitForURL((url) => (
      url.pathname.endsWith('/administration')
      && url.searchParams.get('show') === 'Forms'
    ), { timeout: 30000 }),
    frame.locator('input.upload[type="submit"]').click(),
  ]);
}

legacyHarness.runLegacyCheck('eform-admin-schedule-navigation',async({cancellation})=>{
  if(!process.env.MYSQL_PASSWORD)throw new h.SkipCheck('eForm Upload/Import cleanup requires configured MYSQL_PASSWORD');
  const recorder = createRecorder();
  const marker='FAKE-PW'+randomBytes(8).toString('hex');
  const formName=marker+' Admin Nav',formSubject=marker+' Schedule navigation';
  const sql=h.createSqlRunner(h.readConfig().mysql),identity={marker,name:formName,subject:formSubject};
  let fixture;
  let browser = null;
  let context = null;
  let page = null;
  const screenshots = [];

  try {
    assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${h.sqlString(formName)}`)==='0','Owned form name already exists');
    fixture=createHtmlFixture(formName);
    console.log('Owned eForm fixture: '+formName);
    browser = await chromium.launch({...(getLaunchOptions(config.chromePath)),...legacyHarness.NO_PLAYWRIGHT_SIGNAL_HANDLING});
    cancellation?.throwIfCancelled();
    context = await browser.newContext({
      acceptDownloads: true,
      // The packaged deployment serves a self-signed certificate by default.
      ignoreHTTPSErrors: shouldIgnoreHttpsErrors(),
      viewport: { width: 1440, height: 1400 },
    });
    cancellation?.throwIfCancelled();
    const landingPage = await login(context, config, recorder);
    cancellation?.throwIfCancelled();
    await landingPage.close();
    cancellation?.throwIfCancelled();

    page = await context.newPage();
    cancellation?.throwIfCancelled();
    wirePage(page, 'eform-admin-schedule-navigation', recorder);
    await openFocusedManager(page);
    cancellation?.throwIfCancelled();
    screenshots.push(await screenshot(
      page,
      config.screenshotDir,
      'eform-admin-nav-before-upload',
    ));
    cancellation?.throwIfCancelled();

    await uploadHtml(page, fixture.htmlPath, formName, formSubject);
    cancellation?.throwIfCancelled();
    await assertFocusedAdministration(page, 'HTML Upload redirect');
    cancellation?.throwIfCancelled();
    screenshots.push(await screenshot(
      page,
      config.screenshotDir,
      'eform-admin-nav-after-upload',
    ));
    cancellation?.throwIfCancelled();
    const uploaded = await findLibraryEform(page, formName);
    cancellation?.throwIfCancelled();
    const zipPath = await exportEform(page, uploaded.row, fixture.tempDir);
    cancellation?.throwIfCancelled();
    removeOwnedForms(sql,identity);
    cancellation?.throwIfCancelled();

    await openFocusedManager(page);
    cancellation?.throwIfCancelled();
    await importZip(page, zipPath);
    cancellation?.throwIfCancelled();
    await assertFocusedAdministration(page, 'ZIP Import redirect');
    cancellation?.throwIfCancelled();
    screenshots.push(await screenshot(
      page,
      config.screenshotDir,
      'eform-admin-nav-after-import',
    ));
    cancellation?.throwIfCancelled();
    const imported = await findLibraryEform(page, formName);
    cancellation?.throwIfCancelled();
    assert(imported.fid,'ZIP import produced no native library row');

    assert(recorder.badResponses.length===0,'Unexpected HTTP response failure during Upload/Import');
    assert(recorder.consoleIssues.length===0,'Unexpected console failure during Upload/Import');
    assert(
      recorder.pageErrors.length === 0,
      `Unexpected page errors: ${JSON.stringify(recorder.pageErrors, null, 2)}`,
    );

    console.log(JSON.stringify({
      formName,
      uploadPreservedScheduleNavigation: true,
      importPreservedScheduleNavigation: true,
      screenshots: screenshots.filter(Boolean),
    }, null, 2));
    console.log('PASS eForm Admin schedule navigation upload/import check');
  } catch (error) {
    console.error('FAIL eForm Admin schedule navigation upload/import check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    let closed=!browser;
    try {if(browser)await browser.close();closed=true;}
    finally {
      try {assert(closed,'Browser shutdown is unconfirmed; retaining owned eForm');removeOwnedForms(sql,identity);}
      finally {sql.dispose();if(fixture)fs.rmSync(fixture.tempDir,{recursive:true,force:true});}
    }
  }
});
