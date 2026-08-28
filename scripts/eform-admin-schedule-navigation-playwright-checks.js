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

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { chromium } = require('playwright');
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
const { shouldVerifyDeletedEformRow } = require('./eform-admin-schedule-navigation-cleanup');

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
  await page.frameLocator('#uploadFrame')
    .locator('form[action$="/eform/uploadHtml"]')
    .waitFor({ state: 'visible', timeout: 15000 });
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
  await Promise.all([
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

async function readCsrfToken(page) {
  let token = await page.locator('input[name="CSRF-TOKEN"]').first()
    .inputValue({ timeout: 5000 })
    .catch(() => '');
  if (!token) {
    const configuredContextPath = config.baseUrl.pathname === '/'
      ? ''
      : config.baseUrl.pathname.replace(/\/$/, '');
    token = await page.evaluate(async (contextPath) => {
      const response = await fetch(`${contextPath}/csrfguard`, { credentials: 'same-origin' });
      if (!response.ok) {
        return '';
      }
      const source = await response.text();
      const match = source.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
      return match ? match[1] : '';
    }, configuredContextPath).catch(() => '');
  }
  assert(token, 'Could not obtain a CSRF token for temporary eForm cleanup');
  return token;
}

async function deleteEform(page, fid, formName) {
  assert(/^\d+$/.test(String(fid)), `Refusing to delete invalid eForm fid ${fid}`);
  const token = await readCsrfToken(page);
  const action = appUrl(config.baseUrl, '/eform/delEForm');
  const formId = `schedule_navigation_delete_${fid}`;
  await page.evaluate(({ deleteAction, submittedFid, csrfToken, deleteFormId }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- all four values are passed as Playwright arguments, not interpolated into code; deleteAction is loopback-restricted by validateBaseUrl, submittedFid is regex-validated as digits-only above, and csrfToken/deleteFormId are derived from the app's own trusted session
    const form = document.createElement('form');
    form.id = deleteFormId;
    form.method = 'post';
    form.action = deleteAction;
    for (const [name, value] of Object.entries({
      'CSRF-TOKEN': csrfToken,
      fid: submittedFid,
    })) {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = name;
      input.value = value;
      form.appendChild(input);
    }
    document.body.appendChild(form);
  }, {
    deleteAction: action,
    submittedFid: String(fid),
    csrfToken: token,
    deleteFormId: formId,
  });

  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST'
      && response.url().includes('/eform/delEForm'),
    { timeout: 15000 },
  );
  const navigationPromise = page.waitForNavigation({
    waitUntil: 'domcontentloaded',
    timeout: 15000,
  }).catch(() => null);
  const [response] = await Promise.all([
    responsePromise,
    navigationPromise,
    page.locator(`#${formId}`).evaluate((form) => form.submit()),
  ]);
  // DelEForm2Action enforces CARLOS's POST-only mutator contract (see CLAUDE.md's
  // "GET/HEAD Rejection Contract") and answers a rejected request with 405 rather than
  // failing the delete. This script exists to verify schedule-navigation preservation
  // through Upload/Import, not eform deletion, so a rejected cleanup request is
  // tolerated here (and in the badResponses/consoleIssues filters below) instead of
  // failing the whole check. A delete that is rejected simply leaves the temporary
  // eForm behind; the next run's Date.now()-suffixed fixture name will not collide.
  assert(
    response.status() < 400 || response.status() === 405,
    `Temporary eForm delete failed with HTTP ${response.status()}`,
  );

  if (!shouldVerifyDeletedEformRow(response.status())) {
    return;
  }

  await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  assert(
    await page.locator('#eformTbl tbody tr', { hasText: formName }).count() === 0,
    `Temporary eForm ${formName} still appears after cleanup`,
  );
}

async function importZip(page, zipPath) {
  await page.locator('#eformOptions a[href="#import"]').click();
  await page.locator('#import').waitFor({ state: 'visible', timeout: 15000 });
  const frame = page.frameLocator('#importFrame');
  await frame.locator('#zippedForm').setInputFiles(zipPath);
  await Promise.all([
    page.waitForURL((url) => (
      url.pathname.endsWith('/administration')
      && url.searchParams.get('show') === 'Forms'
    ), { timeout: 30000 }),
    frame.locator('input.upload[type="submit"]').click(),
  ]);
}

(async () => {
  const recorder = createRecorder();
  const runId = `${Date.now()}_${process.pid}`;
  const formName = `Playwright Admin Nav ${runId}`;
  const formSubject = `Schedule navigation ${runId}`;
  const fixture = createHtmlFixture(formName);
  let browser = null;
  let context = null;
  let page = null;
  let importedFid = '';
  let importedExists = false;
  const screenshots = [];

  try {
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    context = await browser.newContext({
      acceptDownloads: true,
      // The packaged deployment serves a self-signed certificate by default.
      ignoreHTTPSErrors: true,
      viewport: { width: 1440, height: 1400 },
    });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    page = await context.newPage();
    wirePage(page, 'eform-admin-schedule-navigation', recorder);
    await openFocusedManager(page);
    screenshots.push(await screenshot(
      page,
      config.screenshotDir,
      'eform-admin-nav-before-upload',
    ));

    await uploadHtml(page, fixture.htmlPath, formName, formSubject);
    await assertFocusedAdministration(page, 'HTML Upload redirect');
    screenshots.push(await screenshot(
      page,
      config.screenshotDir,
      'eform-admin-nav-after-upload',
    ));
    const uploaded = await findLibraryEform(page, formName);
    const zipPath = await exportEform(page, uploaded.row, fixture.tempDir);
    await deleteEform(page, uploaded.fid, formName);

    await openFocusedManager(page);
    await importZip(page, zipPath);
    await assertFocusedAdministration(page, 'ZIP Import redirect');
    screenshots.push(await screenshot(
      page,
      config.screenshotDir,
      'eform-admin-nav-after-import',
    ));
    const imported = await findLibraryEform(page, formName);
    importedFid = imported.fid;
    importedExists = true;

    // Same rationale as the deleteEform() tolerance above: a rejected best-effort
    // cleanup delete is not a navigation-preservation regression.
    const unexpectedBadResponses = recorder.badResponses.filter((issue) => !(
      issue.status === 405 && issue.url.includes('/eform/delEForm')
    ));
    const unexpectedConsoleIssues = recorder.consoleIssues.filter((issue) => !(
      issue.location?.url?.includes('/eform/delEForm')
      && /status of 405/.test(issue.text)
    ));
    assert(
      unexpectedBadResponses.length === 0,
      `Unexpected HTTP errors: ${JSON.stringify(unexpectedBadResponses, null, 2)}`,
    );
    assert(
      unexpectedConsoleIssues.length === 0,
      `Unexpected browser console failures: ${JSON.stringify(unexpectedConsoleIssues, null, 2)}`,
    );
    assert(
      recorder.pageErrors.length === 0,
      `Unexpected page errors: ${JSON.stringify(recorder.pageErrors, null, 2)}`,
    );

    console.log(JSON.stringify({
      formName,
      uploadPreservedScheduleNavigation: true,
      importPreservedScheduleNavigation: true,
      screenshots,
    }, null, 2));
    console.log('PASS eForm Admin schedule navigation upload/import check');
  } catch (error) {
    console.error('FAIL eForm Admin schedule navigation upload/import check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (page && !page.isClosed() && !importedFid) {
      await gotoApp(page, config.baseUrl, '/eform/efmformmanager')
        .then(() => page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {}))
        .then(async () => {
          const row = page.locator('#eformTbl tbody tr', { hasText: formName }).first();
          if (await row.count()) {
            const imported = await findLibraryEform(page, formName);
            importedFid = imported.fid;
            importedExists = true;
          }
        })
        .catch(() => {});
    }
    if (page && !page.isClosed() && importedExists && importedFid) {
      await deleteEform(page, importedFid, formName).catch((error) => {
        console.error(`Cleanup failed: ${error.stack || error.message}`);
        process.exitCode = 1;
      });
    }
    if (browser) {
      await browser.close();
    }
    fs.rmSync(fixture.tempDir, { recursive: true, force: true });
  }
})();
