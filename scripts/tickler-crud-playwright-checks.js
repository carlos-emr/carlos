#!/usr/bin/env node
/*
 * Browser CRUD checks for the CARLOS Tickler interface.
 *
 * The script logs in, creates a uniquely stamped tickler through the add UI,
 * verifies it in the DataTables-backed list, edits it through the edit UI,
 * completes it, deletes it, and cleans up rows it created.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:tickler-crud-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=oscar
 *   TICKLER_DEMOGRAPHIC_NO=1
 *   TICKLER_PROVIDER_NO=999998
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = process.env.MYSQL_HOST || 'db';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';
const demographicNo = process.env.TICKLER_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.TICKLER_PROVIDER_NO || '999998';
const stamp = `PW_TICKLER_CRUD_${Date.now()}`;
const createdMessage = `${stamp} created through add UI`;
const editedMessage = `${stamp} edited through edit UI`;

const mysqlDefaults = createMysqlDefaultsFile();
const badResponses = [];
const consoleIssues = [];

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

function appUrl(appPath, query = null) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, value);
    }
  }
  return url.toString();
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-tickler-mysql-'));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  return { dir, file };
}

function cleanupMysqlDefaultsFile() {
  fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
}

function sql(query) {
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost,
    '-u', mysqlUser,
    mysqlDatabase,
    '-N',
    '-B',
    '-e',
    query,
  ], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function cleanupRows() {
  const escapedStamp = escapeSql(`${stamp}%`);
  sql(`DELETE FROM tickler_comments WHERE tickler_no IN (SELECT tickler_no FROM tickler WHERE message LIKE '${escapedStamp}')`);
  sql(`DELETE FROM tickler WHERE message LIKE '${escapedStamp}'`);
}

function getTicklerRows() {
  const escapedStamp = escapeSql(`${stamp}%`);
  const out = sql(
    `SELECT tickler_no, status, priority, task_assigned_to, DATE(service_date), message`
      + ` FROM tickler WHERE message LIKE '${escapedStamp}' ORDER BY tickler_no`
  );
  if (!out) {
    return [];
  }
  return out.split('\n').map((line) => {
    const [id, status, priority, assignee, serviceDate, message] = line.split('\t');
    return { id, status, priority, assignee, serviceDate, message };
  });
}

function getCommentRows(ticklerNo) {
  const out = sql(
    `SELECT message FROM tickler_comments WHERE tickler_no=${Number(ticklerNo)} ORDER BY id`
  );
  return out ? out.split('\n') : [];
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    const status = response.status();
    const responseUrl = response.url();
    if (status >= 400 && !(status === 404 && /\/imageRenderingServlet\?/.test(responseUrl))) {
      badResponses.push({ label, status, url: responseUrl });
    }
  });
  page.on('console', (message) => {
    const text = message.text();
    if (/(ReferenceError|SyntaxError|TypeError|DataTables AJAX error|Server did not return expected success response|Form submission timed out|Failed to save|Cannot reset buffer)/i.test(text)) {
      consoleIssues.push({ label, type: message.type(), text, location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function gotoApp(page, appPath, waitUntil = 'domcontentloaded', query = null) {
  return page.goto(appUrl(appPath, query), { waitUntil, timeout: 30000 });
}

async function login(page) {
  await gotoApp(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

async function waitForTicklerListReady(page) {
  await page.locator('#ticklerResults').waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForFunction(() => (
    window.jQuery
      && window.jQuery.fn
      && window.jQuery.fn.DataTable
      && window.jQuery.fn.DataTable.isDataTable('#ticklerResults')
      && window.jQuery('#ticklerResults').DataTable().settings()[0].bInitialised
  ), null, { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

async function setTicklerListStatus(page, status) {
  await page.locator('#ticklerview').selectOption(status);
  await page.waitForFunction((expectedStatus) => {
    const table = window.jQuery && window.jQuery('#ticklerResults').DataTable();
    return table && document.getElementById('ticklerview').value === expectedStatus;
  }, status, { timeout: 30000 }).catch(() => {});
}

async function findRowInList(page, message, expectedStatus) {
  await page.locator('#ticklerResults_filter input[type="search"]').fill('');
  await page.evaluate(() => {
    window.jQuery('#ticklerResults').DataTable().search('').order([4, 'desc']).draw();
  });
  try {
    await page.waitForFunction((needle) => {
      const table = window.jQuery && window.jQuery('#ticklerResults').DataTable();
      if (!table) {
        return false;
      }
      return table.rows({ page: 'current' }).data().toArray().some((row) => row.message === needle);
    }, message, { timeout: 30000 });
  } catch (error) {
    const debugState = await page.evaluate(() => {
      const table = window.jQuery && window.jQuery('#ticklerResults').DataTable();
      return {
        url: window.location.href,
        ticklerview: document.getElementById('ticklerview') && document.getElementById('ticklerview').value,
        demoview: document.querySelector('input[name=demoview]') && document.querySelector('input[name=demoview]').value,
        pageInfo: table ? table.page.info() : null,
        rows: table ? table.rows({ page: 'current' }).data().toArray().slice(0, 5) : [],
      };
    });
    throw new Error(`tickler list current page did not show ${message}; state=${JSON.stringify(debugState)}`, { cause: error });
  }

  const row = await page.evaluate((needle) => {
    const table = window.jQuery('#ticklerResults').DataTable();
    return table.rows({ page: 'current' }).data().toArray().find((item) => item.message === needle);
  }, message);
  assert(row, `tickler list did not contain ${message}`);
  if (expectedStatus) {
    assert(row.status === expectedStatus || row.statusDesc === expectedStatus, `tickler row had unexpected status ${row.status || row.statusDesc}`);
  }
  return row;
}

async function probeTicklerSearch(page, message) {
  await page.locator('#ticklerResults_filter input[type="search"]').fill(message);
  await page.evaluate((needle) => {
    window.jQuery('#ticklerResults').DataTable().search(needle).draw();
  }, message);
  await page.waitForTimeout(1500);
  const found = await page.evaluate((needle) => {
    const table = window.jQuery('#ticklerResults').DataTable();
    return table.rows({ search: 'applied' }).data().toArray().some((row) => row.message === needle);
  }, message);
  await page.locator('#ticklerResults_filter input[type="search"]').fill('');
  await page.evaluate(() => {
    window.jQuery('#ticklerResults').DataTable().search('').draw();
  });
  return found;
}

async function openDemoTicklerList(page) {
  await gotoApp(page, '/tickler/ViewTicklerMain', 'domcontentloaded', { demoview: demographicNo, ticklerview: 'A' });
  await waitForTicklerListReady(page);
}

async function createTickler(context) {
  const page = await context.newPage();
  wirePage(page, 'tickler-add');
  await gotoApp(page, '/tickler/ViewAddTickler', 'domcontentloaded', {
    updateParent: 'true',
    bFirstDisp: 'false',
    demographic_no: demographicNo,
  });
  await page.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('textarea[name="ticklerMessage"]').fill(createdMessage);
  await page.locator('input[name="xml_appointment_date"]').fill('2026-02-18');
  await page.locator('select[name="priority"]').selectOption('High');
  await page.locator('select[name="task_assigned_to"]').selectOption(providerNo);
  await page.locator('input.btn-primary[name="Button"]').first().click();
  await page.waitForFunction(() => {
    const frame = document.getElementById('ticklerSubmitFrame');
    return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-save-ok');
  }, null, { timeout: 30000 });

  await page.waitForTimeout(750);
  await page.close().catch(() => {});

  const rows = getTicklerRows();
  assert(rows.length === 1, `expected one created tickler row, found ${rows.length}`);
  assert(rows[0].status === 'A', `created tickler status was ${rows[0].status}`);
  assert(rows[0].priority === 'High', `created tickler priority was ${rows[0].priority}`);
  assert(rows[0].serviceDate === '2026-02-18', `created tickler service date was ${rows[0].serviceDate}`);
  assert(rows[0].assignee === providerNo, `created tickler assignee was ${rows[0].assignee}`);
  return rows[0].id;
}

async function editTicklerFromList(context, listPage, ticklerNo) {
  await findRowInList(listPage, createdMessage, 'A');
  const rowLocator = listPage.locator('#ticklerResults tbody tr').filter({ hasText: createdMessage }).first();
  const popupPromise = context.waitForEvent('page');
  await rowLocator.locator('a[onclick*="openTicklerEdit"]').click();
  const editPage = await popupPromise;
  wirePage(editPage, 'tickler-edit');
  await editPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await editPage.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
  await editPage.locator('#newMessage').fill(editedMessage);
  await editPage.locator('#priority').selectOption('Low');
  await editPage.locator('#xml_appointment_date').fill('2026-02-19');
  await editPage.locator('input[name="updateTickler"]').click();
  await editPage.waitForFunction(() => {
    const frame = document.getElementById('ticklerEditFrame');
    return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-edit-ok');
  }, null, { timeout: 30000 });
  await editPage.waitForTimeout(750).catch(() => {});
  await editPage.close().catch(() => {});

  const rows = getTicklerRows();
  assert(rows.length === 1 && rows[0].id === String(ticklerNo), `edited tickler row was not found for ${ticklerNo}`);
  assert(rows[0].priority === 'Low', `edited tickler priority was ${rows[0].priority}`);
  assert(rows[0].serviceDate === '2026-02-19', `edited tickler service date was ${rows[0].serviceDate}`);
  const comments = getCommentRows(ticklerNo);
  assert(comments.includes(editedMessage), 'edited tickler comment was not saved');
}

async function completeTicklerViaEdit(context, ticklerNo) {
  const page = await context.newPage();
  wirePage(page, 'tickler-complete-edit');
  await gotoApp(page, '/tickler/ViewTicklerEdit', 'domcontentloaded', { tickler_no: ticklerNo });
  await page.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('#status').selectOption('C');
  await page.locator('input[name="updateTickler"]').click();
  await page.waitForFunction(() => {
    const frame = document.getElementById('ticklerEditFrame');
    return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-edit-ok');
  }, null, { timeout: 30000 });
  await page.close().catch(() => {});

  const rows = getTicklerRows();
  assert(rows.length === 1 && rows[0].status === 'C', `completed tickler status was ${rows[0] && rows[0].status}`);
}

async function deleteTicklerFromList(page, message) {
  await setTicklerListStatus(page, 'C');
  await findRowInList(page, message, 'C');
  const rowLocator = page.locator('#ticklerResults tbody tr').filter({ hasText: message }).first();
  await rowLocator.locator('input[name="checkbox"]').check();
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator("form[name='ticklerform'] input.btn-danger").click(),
  ]);
  await waitForTicklerListReady(page);

  const rows = getTicklerRows();
  assert(rows.length === 1 && rows[0].status === 'D', `deleted tickler status was ${rows[0] && rows[0].status}`);
}

(async () => {
  cleanupRows();

  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    const schedulePage = await context.newPage();
    wirePage(schedulePage, 'login-schedule');
    await login(schedulePage);

    const ticklerNo = await createTickler(context);

    const listPage = await context.newPage();
    wirePage(listPage, 'tickler-list');
    await openDemoTicklerList(listPage);
    const activeRow = await findRowInList(listPage, createdMessage, 'A');
    assert(String(activeRow.id) === String(ticklerNo), `list row id ${activeRow.id} did not match created id ${ticklerNo}`);
    const searchFoundCreated = await probeTicklerSearch(listPage, createdMessage);
    if (!searchFoundCreated) {
      console.log(`WARN tickler list search did not return the created message ${createdMessage}`);
    }

    await editTicklerFromList(context, listPage, ticklerNo);
    await listPage.reload({ waitUntil: 'domcontentloaded' });
    await waitForTicklerListReady(listPage);
    const editedRow = await findRowInList(listPage, createdMessage, 'A');
    assert(String(editedRow.id) === String(ticklerNo), `edited list row id ${editedRow.id} did not match ${ticklerNo}`);

    await completeTicklerViaEdit(context, ticklerNo);
    await listPage.reload({ waitUntil: 'domcontentloaded' });
    await waitForTicklerListReady(listPage);
    await setTicklerListStatus(listPage, 'C');
    await findRowInList(listPage, createdMessage, 'C');

    await deleteTicklerFromList(listPage, createdMessage);
    await setTicklerListStatus(listPage, 'D');
    await findRowInList(listPage, createdMessage, 'D');

    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(consoleIssues, null, 2)}`);

    console.log(`PASS tickler CRUD interface flow for tickler ${ticklerNo}`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupRows();
    cleanupMysqlDefaultsFile();
  }
})().catch((error) => {
  cleanupMysqlDefaultsFile();
  console.error(`FAIL tickler CRUD interface flow: ${error.stack || error.message}`);
  process.exit(1);
});
