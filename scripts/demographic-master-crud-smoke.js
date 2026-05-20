#!/usr/bin/env node

/*
 * Browser smoke test for the demographic master record workflow.
 *
 * Prerequisites:
 *   - CARLOS is running locally, usually at http://localhost:8080/carlos
 *   - Playwright is available in node_modules or globally
 *   - A Chromium executable exists at CHROMIUM_PATH or Playwright's default browser
 *
 * Useful env vars:
 *   BASE_URL=http://localhost:8080/carlos
 *   CARLOS_USER=carlosdoc
 *   CARLOS_PASSWORD=carlos2026
 *   CARLOS_PIN=2026
 *   HEADLESS=false
 *   KEEP_OPEN=true
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://localhost:8080/carlos'),
  username: process.env.CARLOS_USER || 'carlosdoc',
  password: process.env.CARLOS_PASSWORD || 'carlos2026',
  pin: process.env.CARLOS_PIN || '2026',
  chromiumPath: process.env.CHROMIUM_PATH || '/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome',
  headless: process.env.HEADLESS !== 'false',
  keepOpen: process.env.KEEP_OPEN === 'true',
  timeout: Number(process.env.PLAYWRIGHT_TIMEOUT || 30000),
};

const stamp = process.env.SMOKE_STAMP || String(Date.now());
const patient = {
  lastName: `SMOKEDEM${stamp}`,
  firstName: 'CRUD',
  updatedFirstName: 'UPDATED',
  address: '123 Smoke Test Ave',
  updatedAddress: '456 Smoke Test Rd',
  city: 'Hamilton',
  postal: 'L8P1A1',
  phone: '9055550101',
  cell: '9055550102',
  email: `smoke.dem.${stamp}@example.test`,
  dob: '1980-01-02',
  sex: 'M',
  chartNo: `SMOKE-${stamp}`.slice(0, 20),
};

const findings = [];
const results = [];

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
  const url = new URL(config.baseUrl.href);
  url.pathname = `${config.baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

function safeGoto(page, appPath, options) {
  return page.goto(appUrl(appPath), options); // nosemgrep // NOSONAR - appUrl validates local-only BASE_URL and root-relative paths.
}

function record(status, label, detail) {
  results.push({ status, label, detail });
  const suffix = detail ? ` - ${detail}` : '';
  console.log(`${status}: ${label}${suffix}`);
}

function fail(label, detail) {
  findings.push({ label, detail });
  record('FAIL', label, detail);
}

async function expectNoErrorPage(page, label) {
  const body = await page.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(body)) {
    fail(label, `error page at ${page.url()}: ${body.slice(0, 300).replace(/\s+/g, ' ')}`);
    return false;
  }
  return true;
}

async function fillIfPresent(page, selector, value) {
  const locator = page.locator(selector).first();
  if (await locator.count()) {
    const visible = await locator.isVisible().catch(() => false);
    if (visible) {
      await locator.fill(value);
    } else {
      await locator.evaluate((element, newValue) => { // nosemgrep // NOSONAR - value is passed as a Playwright argument, not interpolated.
        element.value = newValue;
        element.dispatchEvent(new Event('input', { bubbles: true }));
        element.dispatchEvent(new Event('change', { bubbles: true }));
      }, value);
    }
    return true;
  }
  return false;
}

async function selectIfPresent(page, selector, value, options = {}) {
  const locator = page.locator(selector).first();
  if (await locator.count()) {
    const values = await locator.locator('option').evaluateAll(options => options.map(option => ({
      value: option.value,
      label: option.textContent.trim(),
    }))).catch(() => []);
    const hasValue = values.some(option => option.value === value);
    const hasLabel = values.some(option => option.label === value);
    if (!hasValue && !hasLabel) {
      if (!options.optional) {
        fail(`select ${selector}`, `missing option ${value}; options=${JSON.stringify(values.slice(0, 12))}`);
      }
      return false;
    }
    if (hasValue) {
      await locator.selectOption(value);
    } else {
      await locator.selectOption({ label: value });
    }
    return true;
  }
  return false;
}

async function login(page) {
  await safeGoto(page, '/', { waitUntil: 'domcontentloaded' });
  await page.locator('#username').fill(config.username);
  await page.locator('#password').fill(config.password);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(config.pin);
  }
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('form[name="loginForm"] input[type="submit"]').click(),
  ]);
  await page.waitForTimeout(1500);
  if (/\/logout\?login=failed/.test(page.url()) || await page.locator('form[name="loginForm"]').count()) {
    const body = await page.locator('body').innerText().catch(() => '');
    throw new Error(`Login failed at ${page.url()}: ${body.slice(0, 300).replace(/\s+/g, ' ')}`);
  }
  record('OK', 'login', page.url());
}

async function openSearchPopup(context, mainPage) {
  const searchLink = mainPage.locator('a').filter({ hasText: /^Search$/ }).first();
  if (!await searchLink.count()) {
    throw new Error('Could not find the top-nav Search link');
  }
  const [searchPage] = await Promise.all([
    context.waitForEvent('page'),
    searchLink.click(),
  ]);
  await searchPage.waitForLoadState('domcontentloaded');
  await expectNoErrorPage(searchPage, 'open demographic search popup');
  record('OK', 'open demographic search popup', searchPage.url());
  return searchPage;
}

async function searchByName(searchPage, keyword) {
  await searchPage.locator('#search_mode').selectOption('search_name');
  await searchPage.locator('#keyword').fill(keyword);
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    searchPage.locator('input[type="submit"][value="Search"]').click(),
  ]);
  await searchPage.waitForTimeout(1000);
  await expectNoErrorPage(searchPage, `search demographic ${keyword}`);
  record('OK', `search demographic ${keyword}`, searchPage.url());
}

async function createDemographic(searchPage) {
  const createLink = searchPage.locator('a', { hasText: /Create Demographic/i }).first();
  if (!await createLink.count()) {
    throw new Error('Could not find Create Demographic link after no-results search');
  }
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    createLink.click(),
  ]);
  await searchPage.waitForTimeout(1000);
  if (!await expectNoErrorPage(searchPage, 'open create demographic form')) return null;

  await fillIfPresent(searchPage, 'input[name="last_name"]', patient.lastName);
  await fillIfPresent(searchPage, 'input[name="first_name"]', patient.firstName);
  await fillIfPresent(searchPage, 'input[name="address"]', patient.address);
  await fillIfPresent(searchPage, 'input[name="city"]', patient.city);
  await fillIfPresent(searchPage, 'input[name="postal"]', patient.postal);
  await fillIfPresent(searchPage, 'input[name="phone"]', patient.phone);
  await fillIfPresent(searchPage, 'input[name="demo_cell"]', patient.cell);
  await fillIfPresent(searchPage, 'input[name="email"]', patient.email);
  await fillIfPresent(searchPage, 'input[name="inputDOB"]', patient.dob);
  await fillIfPresent(searchPage, 'input[name="year_of_birth"]', '1980');
  await fillIfPresent(searchPage, 'input[name="month_of_birth"]', '01');
  await fillIfPresent(searchPage, 'input[name="date_of_birth"]', '02');
  await fillIfPresent(searchPage, 'input[name="chart_no"]', patient.chartNo);
  await selectIfPresent(searchPage, 'select[name="sex"]', patient.sex);
  await selectIfPresent(searchPage, 'select[name="province"]', 'ON', { optional: true });
  await selectIfPresent(searchPage, 'select[name="patient_status"]', 'AC');
  await selectIfPresent(searchPage, 'select[name="roster_status"]', 'FS');

  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    searchPage.locator('#btnAddRecord, input[name="btnAddRecord"]').first().click(),
  ]);
  await searchPage.waitForTimeout(1500);

  let body = await searchPage.locator('body').innerText().catch(() => '');
  let hasErrorPage = /CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(body);
  if (hasErrorPage) {
    fail('submit create demographic', `post-create flow reached error page at ${searchPage.url()}: ${body.slice(0, 300).replace(/\s+/g, ' ')}`);
    await safeGoto(searchPage, `/demographic/DemographicSearch?search_mode=search_name&keyword=${encodeURIComponent(patient.lastName)}&orderby=last_name%2C+first_name&dboperation=search_titlename&limit1=0&limit2=10&displaymode=Search&ptstatus=all&fromMessenger=false&outofdomain=`, {
      waitUntil: 'domcontentloaded',
    });
    await searchPage.waitForTimeout(1000);
    body = await searchPage.locator('body').innerText().catch(() => '');
  } else if (!await expectNoErrorPage(searchPage, 'submit create demographic')) {
    return null;
  }

  let match = body.match(/DemographicEdit\?demographic_no=(\d+)/) || searchPage.url().match(/demographic_no=(\d+)/);
  if (!match) {
    const editHref = await searchPage.locator('a[href*="DemographicEdit?demographic_no="]').first().getAttribute('href').catch(() => '');
    match = editHref ? editHref.match(/demographic_no=(\d+)/) : null;
  }
  const demographicNo = match ? match[1] : null;
  if (!demographicNo) {
    fail('create demographic did not expose demographic_no', body.slice(0, 500).replace(/\s+/g, ' '));
    return null;
  }
  record(hasErrorPage ? 'WARN' : 'OK', 'create demographic', `demographic_no=${demographicNo}`);
  return demographicNo;
}

async function openEditMode(page) {
  const editButton = page.locator('#editBtn').first();
  if (await editButton.count() && await editButton.isVisible().catch(() => false)) {
    await editButton.click();
    await page.waitForTimeout(500);
  }
}

async function readDemographic(searchPage, demographicNo) {
  await safeGoto(searchPage, `/demographic/DemographicEdit?demographic_no=${encodeURIComponent(demographicNo)}`, {
    waitUntil: 'domcontentloaded',
  });
  await searchPage.waitForTimeout(1000);
  if (!await expectNoErrorPage(searchPage, 'open demographic edit/master record')) return false;
  const lastNameValue = await searchPage.locator('input[name="last_name"]').first().inputValue().catch(() => '');
  if (lastNameValue !== patient.lastName) {
    fail('read demographic master record', `expected last_name ${patient.lastName}, saw ${lastNameValue}`);
    return false;
  }
  record('OK', 'read demographic master record', `demographic_no=${demographicNo}`);
  return true;
}

async function updateDemographic(searchPage, demographicNo) {
  await openEditMode(searchPage);
  await fillIfPresent(searchPage, 'input[name="first_name"]', patient.updatedFirstName);
  await fillIfPresent(searchPage, 'input[name="address"]', patient.updatedAddress);
  await fillIfPresent(searchPage, 'input[name="email"]', `updated.${patient.email}`);
  await fillIfPresent(searchPage, 'input[name="roster_date_year"]', '2026');
  await fillIfPresent(searchPage, 'input[name="roster_date_month"]', '05');
  await fillIfPresent(searchPage, 'input[name="roster_date_day"]', '19');
  await selectIfPresent(searchPage, 'select[name="roster_enrolled_to"]', '999998', { optional: true });

  const updateButton = searchPage.locator('input[type="submit"], button[type="submit"]').filter({
    hasText: /Update|Save/i,
  }).first();
  const updateByClass = searchPage.locator('input.btn-toolbar-update[type="submit"]').first();
  const button = await updateButton.count() ? updateButton : updateByClass;
  if (!await button.count()) {
    fail('update demographic master record', 'could not find update submit button');
    return false;
  }

  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    button.click(),
  ]);
  await searchPage.waitForTimeout(1500);
  if (!await expectNoErrorPage(searchPage, 'submit demographic update')) return false;

  await safeGoto(searchPage, `/demographic/DemographicEdit?demographic_no=${encodeURIComponent(demographicNo)}`, {
    waitUntil: 'domcontentloaded',
  });
  await searchPage.waitForTimeout(1000);
  const firstNameValue = await searchPage.locator('input[name="first_name"]').first().inputValue().catch(() => '');
  const addressValue = await searchPage.locator('input[name="address"]').first().inputValue().catch(() => '');
  if (firstNameValue !== patient.updatedFirstName || addressValue !== patient.updatedAddress) {
    fail('verify demographic update', `first_name=${firstNameValue}, address=${addressValue}`);
    return false;
  }
  record('OK', 'update demographic master record', `demographic_no=${demographicNo}`);
  return true;
}

async function inactivateDemographic(searchPage, demographicNo) {
  await safeGoto(searchPage, `/demographic/DemographicEdit?demographic_no=${encodeURIComponent(demographicNo)}`, {
    waitUntil: 'domcontentloaded',
  });
  await searchPage.waitForTimeout(1000);
  await openEditMode(searchPage);
  await selectIfPresent(searchPage, 'select[name="patient_status"]', 'IN');
  await fillIfPresent(searchPage, 'input[name="roster_date_year"]', '2026');
  await fillIfPresent(searchPage, 'input[name="roster_date_month"]', '05');
  await fillIfPresent(searchPage, 'input[name="roster_date_day"]', '19');
  await selectIfPresent(searchPage, 'select[name="roster_enrolled_to"]', '999998', { optional: true });

  const button = searchPage.locator('input.btn-toolbar-update[type="submit"], input[type="submit"][value*="Update"], input[type="submit"][value*="Save"]').first();
  if (!await button.count()) {
    fail('inactivate demographic master record', 'could not find update submit button');
    return false;
  }
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    button.click(),
  ]);
  await searchPage.waitForTimeout(1500);
  if (!await expectNoErrorPage(searchPage, 'submit demographic inactivation')) return false;

  await safeGoto(searchPage, `/demographic/DemographicEdit?demographic_no=${encodeURIComponent(demographicNo)}`, {
    waitUntil: 'domcontentloaded',
  });
  await searchPage.waitForTimeout(1000);
  const status = await searchPage.locator('select[name="patient_status"]').first().inputValue().catch(() => '');
  if (status !== 'IN') {
    fail('verify demographic inactivation', `patient_status=${status}`);
    return false;
  }
  record('OK', 'inactivate demographic master record', `demographic_no=${demographicNo}`);
  return true;
}

async function returnToDemographicSearch(searchPage) {
  const backLink = searchPage.locator('a', { hasText: /Back to Demographic Search Page/i }).first();
  if (await backLink.count()) {
    await Promise.all([
      searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
      backLink.click(),
    ]);
  } else {
    await safeGoto(searchPage, '/demographic/ViewSearch', { waitUntil: 'domcontentloaded' });
  }
  await searchPage.waitForTimeout(1000);
  await expectNoErrorPage(searchPage, 'return to demographic search form');
  record('OK', 'return to demographic search form', searchPage.url());
}

async function main() {
  const launchOptions = { headless: config.headless };
  if (config.chromiumPath) {
    launchOptions.executablePath = config.chromiumPath;
  }

  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
  context.setDefaultTimeout(config.timeout);

  context.on('page', page => {
    page.on('console', msg => {
      const text = msg.text();
      if (msg.type() === 'error' || /\b404\b|DataTable is not a function|redeclaration|Cannot read|Cannot set/i.test(text)) {
        findings.push({ label: 'browser console', detail: `${msg.type()}: ${text}` });
      }
    });
    page.on('pageerror', error => {
      findings.push({ label: 'browser pageerror', detail: error.message });
    });
    page.on('response', response => {
      const status = response.status();
      if (status >= 500) {
        findings.push({ label: 'HTTP 5xx', detail: `${status} ${response.url()}` });
      }
    });
  });

  const page = await context.newPage();
  let demographicNo = null;
  try {
    await login(page);
    const searchPage = await openSearchPopup(context, page);
    await searchByName(searchPage, patient.lastName);
    demographicNo = await createDemographic(searchPage);
    if (demographicNo) {
      await returnToDemographicSearch(searchPage);
      await searchByName(searchPage, patient.lastName);
      await readDemographic(searchPage, demographicNo);
      await updateDemographic(searchPage, demographicNo);
      await inactivateDemographic(searchPage, demographicNo);
    }
  } finally {
    console.log(JSON.stringify({ stamp, patient, demographicNo, results, findings }, null, 2));
    if (config.keepOpen) {
      console.log('KEEP_OPEN=true, leaving browser open.');
      await new Promise(() => {});
    }
    await browser.close();
  }

  if (findings.length) {
    process.exitCode = 1;
  }
}

main().catch(error => {
  console.error(error.stack || error.message);
  process.exit(1);
});
