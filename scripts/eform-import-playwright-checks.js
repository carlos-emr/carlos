#!/usr/bin/env node
/*
 * App-backed browser regression checks for importing and opening a real eForm.
 *
 * This script logs into a running CARLOS app, ensures the self-referral fixture
 * images exist in the eForm image library, uploads the HTML eForm through the
 * real admin UI, opens the imported form from the eForm library, fills a few
 * representative fields, and captures a screenshot.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/eform-import-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   EFORM_SCREENSHOT_DIR=/tmp
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const screenshotDir = process.env.EFORM_SCREENSHOT_DIR || '/tmp';

const fixtureDir = path.join(__dirname, '..', 'RegionalCommunityPain-SelfReferral');
const fixturePropsPath = path.join(fixtureDir, 'eform.properties');
const fixtureHtmlPath = path.join(fixtureDir, 'RegionalCommunityPain-SelfReferral.html');
const fixtureImages = [
  '2025_06_12_PCXX108060A_Regional_Community_Pain_Self-_Management_Program_Referral_-_Form_NWM-1[1].png',
  '2025_06_12_PCXX108060A_Regional_Community_Pain_Self-_Management_Program_Referral_-_Form_NWM-2[1].png',
];
const sanitizedFixtureImages = fixtureImages.map(sanitizeFixtureFileName);

const badResponses = [];
const consoleIssues = [];
const displayImageResponses = [];

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!localHosts.has(host) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

function sanitizeFixtureFileName(fileName) {
  return fileName
    .replace(/\s+/g, '_')
    .replace(/[^a-zA-Z0-9._]/g, '')
    .replace(/\.+/g, '.');
}

function buildImportHtmlCopy() {
  let html = fs.readFileSync(fixtureHtmlPath, 'utf8');
  for (let i = 0; i < fixtureImages.length; i += 1) {
    const original = fixtureImages[i];
    const sanitized = sanitizedFixtureImages[i];
    html = html.split(original).join(sanitized);
  }
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-eform-import-'));
  const tempHtmlPath = path.join(tempDir, path.basename(fixtureHtmlPath));
  fs.writeFileSync(tempHtmlPath, html);
  return { tempDir, tempHtmlPath };
}

function readFixtureProperties() {
  const props = {};
  for (const line of fs.readFileSync(fixturePropsPath, 'utf8').split(/\r?\n/)) {
    if (!line || /^\s*#/.test(line)) {
      continue;
    }
    const index = line.indexOf('=');
    if (index === -1) {
      continue;
    }
    props[line.slice(0, index).trim()] = line.slice(index + 1).trim();
  }
  return props;
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/favicon\.ico$/.test(responseUrl) || /\/imageRenderingServlet\?/.test(responseUrl));
}

function isIgnorableConsoleMessage(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text)
    || /i\.creativecommons\.org/i.test(text)
    || /ajax\.googleapis\.com\/ajax\/libs\/jquery\/1\.7\.1/i.test(text);
}

function isSevereConsoleMessage(message) {
  if (isIgnorableConsoleMessage(message)) {
    return false;
  }
  const text = message.text();
  if (message.type() === 'error') {
    return !/i\.creativecommons\.org|ajax\.googleapis\.com/i.test(text);
  }
  return /(ReferenceError|TypeError|SyntaxError|\$ is not defined|jQuery is not defined|Cannot read|Cannot set|is not defined)/i.test(text);
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (/\/eform\/displayImage(?:\.do)?\?imagefile=/.test(responseUrl)) {
      displayImageResponses.push({ label, status, url: responseUrl, contentType: response.headers()['content-type'] || '' });
    }
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl) && !/i\.creativecommons\.org|ajax\.googleapis\.com/i.test(responseUrl)) {
      badResponses.push({ label, status, url: responseUrl, contentType: response.headers()['content-type'] || '' });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      consoleIssues.push({ label, type: message.type(), text: message.text(), location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function gotoApp(page, appPath, waitUntil = 'domcontentloaded') {
  return page.goto(appUrl(appPath), { waitUntil, timeout: 30000 });
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await gotoApp(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(testPin);
  }
  await Promise.all([
    page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function ensureImageUploaded(context, sourceImageName, savedImageName) {
  const page = await context.newPage();
  wirePage(page, `image:${savedImageName}`);
  try {
    await gotoApp(page, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const imageLink = page.locator('#tblImage a.viewImage', { hasText: savedImageName }).first();
    if (await imageLink.count()) {
      return;
    }

    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('#image').setInputFiles(path.join(fixtureDir, sourceImageName));
    await frame.locator('input.upload[type="submit"]').click();
    await page.waitForURL(/administration\?show=ImageUpload/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await page.locator('#tblImage a.viewImage', { hasText: savedImageName }).first().waitFor({ state: 'visible', timeout: 15000 });
  } finally {
    await page.close();
  }
}

async function uploadEform(context, formName, formSubject, htmlFilePath) {
  const page = await context.newPage();
  wirePage(page, 'eform-upload');
  try {
    await gotoApp(page, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('input[name="formName"]').fill(formName);
    await frame.locator('input[name="formSubject"]').fill(formSubject);
    await frame.locator('#patientIndependent').check();
    await frame.locator('#formHtml').setInputFiles(htmlFilePath);
    await frame.locator('input.upload[type="submit"]').click();

    await page.waitForURL(/administration\?show=Forms/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const row = page.locator('#eformTbl tbody tr', { hasText: formName }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });

    const editHref = await row.locator('a[href*="efmformmanageredit?fid="]').first().getAttribute('href');
    assert(editHref, `Could not find edit link for imported eForm ${formName}`);
    const parsed = new URL(editHref, baseUrl.href);
    const fid = parsed.searchParams.get('fid');
    assert(fid, `Could not extract fid from edit link ${editHref}`);
    return { page, row, fid };
  } catch (error) {
    await page.close().catch(() => {});
    throw error;
  }
}

async function openImportedEform(context, row) {
  const popupPromise = context.waitForEvent('page');
  await row.locator('td a').first().click();
  const popup = await popupPromise;
  wirePage(popup, 'eform-preview');
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return popup;
}

async function assertImageLoaded(page, selector, label) {
  const loaded = await page.locator(selector).evaluate((img) => ({
    complete: img.complete,
    width: img.naturalWidth,
    height: img.naturalHeight,
    src: img.currentSrc || img.src,
  }));
  assert(loaded.complete, `${label} did not finish loading`);
  assert(loaded.width > 0 && loaded.height > 0, `${label} failed to decode: ${loaded.src}`);
}

function assertDisplayImageFetchesSucceeded(expectedImages) {
  for (const imageName of expectedImages) {
    const matches = displayImageResponses.filter((response) => response.url.includes(`imagefile=${encodeURIComponent(imageName)}`) || response.url.includes(`imagefile=${imageName}`));
    assert(matches.length > 0, `No displayImage response captured for ${imageName}`);
    assert(matches.some((response) => response.status == 200), `displayImage never returned 200 for ${imageName}: ${JSON.stringify(matches, null, 2)}`);
  }
}

async function cleanupImportedEform(page, fid) {
  if (!fid) {
    return;
  }
  await page.evaluate((submittedFid) => {
    const form = document.createElement('form');
    form.method = 'post';
    form.action = `${window.location.origin}${window.location.pathname.replace(/\/efmformmanager.*$/, '')}/delEForm`;
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'fid';
    input.value = submittedFid;
    form.appendChild(input);
    document.body.appendChild(form);
    form.submit();
  }, fid);
  await page.waitForLoadState('domcontentloaded', { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
}

async function assertNotErrorPage(page, label) {
  const text = await page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(text), `${label} rendered an error page`);
  assert(text.trim().length > 0, `${label} rendered a blank page`);
}

(async () => {
  for (const fixtureImage of fixtureImages) {
    assert(fs.existsSync(path.join(fixtureDir, fixtureImage)), `Missing fixture image: ${fixtureImage}`);
  }
  assert(fs.existsSync(fixtureHtmlPath), `Missing fixture HTML: ${fixtureHtmlPath}`);

  const importHtml = buildImportHtmlCopy();
  const fixtureProps = readFixtureProperties();
  const timestamp = Date.now();
  const importedFormName = `${fixtureProps['form.name']} Playwright ${timestamp}`;
  const importedFormSubject = `Fixture import ${timestamp}`;
  let importedFid = null;
  let managerPage = null;

  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1100, height: 1600 } });
    const landingPage = await login(context);
    await landingPage.close();

    for (let i = 0; i < fixtureImages.length; i += 1) {
      await ensureImageUploaded(context, fixtureImages[i], sanitizedFixtureImages[i]);
    }

    const uploadResult = await uploadEform(context, importedFormName, importedFormSubject, importHtml.tempHtmlPath);
    managerPage = uploadResult.page;
    importedFid = uploadResult.fid;
    const popup = await openImportedEform(context, uploadResult.row);
    await assertNotErrorPage(popup, 'imported eForm popup');

    await assertImageLoaded(popup, '#BGImage1', 'Imported eForm page 1 background');
    await assertImageLoaded(popup, '#BGImage2', 'Imported eForm page 2 background');
    assertDisplayImageFetchesSucceeded(sanitizedFixtureImages);

    await popup.fill('#patient_namel', 'Imported');
    await popup.fill('#patient_namef', 'Fixture');
    await popup.fill('#email', 'imported.fixture@example.test');
    await popup.fill('#page2_43', 'Playwright imported this real eForm fixture through the CARLOS admin upload flow.');
    await popup.check('#page1_1');
    await popup.check('#page2_21');

    const state = await popup.evaluate(() => ({
      patientLast: document.getElementById('patient_namel').value,
      patientFirst: document.getElementById('patient_namef').value,
      email: document.getElementById('email').value,
      page2Length: document.getElementById('page2_43').value.length,
      page1Checked: document.getElementById('page1_1').checked,
      page2Checked: document.getElementById('page2_21').checked,
      title: document.title,
    }));

    assert(state.patientLast === 'Imported', 'Imported eForm last-name field was not editable');
    assert(state.patientFirst === 'Fixture', 'Imported eForm first-name field was not editable');
    assert(state.email === 'imported.fixture@example.test', 'Imported eForm email field was not editable');
    assert(state.page2Length > 20, 'Imported eForm textarea content was not entered');
    assert(state.page1Checked, 'Imported eForm page 1 checkbox did not remain checked');
    assert(state.page2Checked, 'Imported eForm page 2 checkbox did not remain checked');

    fs.mkdirSync(screenshotDir, { recursive: true });
    const screenshotPath = `${screenshotDir.replace(/\/+$/, '')}/eform-imported-self-referral.png`;
    await popup.screenshot({ path: screenshotPath, fullPage: true });

    const fatalConsoleIssues = consoleIssues.filter((issue) => issue.type !== 'dialog');
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(fatalConsoleIssues.length === 0, `unexpected browser console failures: ${JSON.stringify(fatalConsoleIssues, null, 2)}`);

    console.log(JSON.stringify({ importedFormName, importedFormSubject, importedFid, screenshotPath }, null, 2));
    console.log('PASS app-backed eForm import Playwright check');

    await popup.close();
  } finally {
    if (managerPage && !managerPage.isClosed()) {
      await cleanupImportedEform(managerPage, importedFid).catch(() => {});
      await managerPage.close().catch(() => {});
    }
    if (typeof importHtml !== 'undefined' && importHtml.tempDir) {
      fs.rmSync(importHtml.tempDir, { recursive: true, force: true });
    }
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL app-backed eForm import Playwright check');
  console.error(error.stack || error.message);
  if (badResponses.length) {
    console.error(`HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
  }
  if (consoleIssues.length) {
    console.error(`Console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
  }
  process.exit(1);
});
