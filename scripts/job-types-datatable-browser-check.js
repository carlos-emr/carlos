#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * Exercise the actual jobTypes.jsp script and body with vendored jQuery, jQuery UI and
 * DataTables in Chromium. Only the REST responses and translated fixture labels are supplied
 * here. No CARLOS server, database, credentials or scheduled jobs are used.
 *
 * Run: node scripts/job-types-datatable-browser-check.js
 * Set JOB_TYPES_JSP=/path/to/the/original.jsp for the old-behavior regression check.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');

const ROOT = path.join(__dirname, '..');
const JSP = process.env.JOB_TYPES_JSP || path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/admin/jobTypes.jsp');
const LABELS = {
  'admin.jobTypes.addNew': 'Add job type',
  'admin.jobTypes.editorTitle': 'Job type',
};
const ASSETS = new Map([
  ['/jquery.js', 'library/jquery/jquery-3.7.1.min.js'],
  ['/jquery-ui.js', 'library/jquery/jquery-ui-1.14.2.min.js'],
  ['/jquery-ui.css', 'library/jquery/jquery-ui.structure-1.14.2.min.css'],
  ['/datatables.js', 'library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js'],
]);

function renderFixture() {
  const jsp = fs.readFileSync(JSP, 'utf8');
  const inline = [...jsp.matchAll(/<script>\s*([\s\S]*?)<\/script>/g)]
    .map(match => match[1]).find(script => script.includes('function getJobTypes()'));
  assert.ok(inline, 'The JSP inline job types script was not found');
  const script = inline
    .replaceAll('${carlos:forJavaScript(jobTypesSaveLabel)}', 'Save')
    .replaceAll('${carlos:forJavaScript(jobTypesCancelLabel)}', 'Cancel')
    .replaceAll('${carlos:forJavaScript(jobTypesSaveErrorLabel)}', 'FAKE save failed')
    .replaceAll('${pageContext.request.contextPath}', '')
    .replace(/<%=\s*request\.getContextPath\(\)\s*%>/g, '');
  assert.ok(!/<%|\$\{/.test(script), 'A server-side expression remains in the fixture script');
  const bodyMatch = jsp.match(/<body\b[^>]*>([\s\S]*?)<\/body>/i);
  assert.ok(bodyMatch, 'The JSP body was not found');
  const body = bodyMatch[1].replace(/<fmt:message\s+key=['"]([^'"]+)['"]\s*\/>/g,
    (_, key) => LABELS[key] || key);
  return `<!doctype html><html><head><link rel="stylesheet" href="/jquery-ui.css">
    <script src="/jquery.js"></script><script src="/jquery-ui.js"></script>
    <script src="/datatables.js"></script><script>${script}</script></head><body>${body}</body></html>`;
}

const jobs = () => Array.from({ length: 30 }, (_, index) => ({
  id: index + 1, name: `FAKE job ${String(index + 1).padStart(2, '0')}`,
  description: 'FAKE description', className: 'FAKE.DisabledJob', currentlyValid: false,
  enabled: false, updated: '2026-10-22T12:00:00Z',
}));

async function withFixture(browser, exercise) {
  const state = { rows: jobs(), mode: 'array', saves: [], alerts: [], expectedAlerts: [], errors: [] };
  const server = http.createServer((request, response) => {
    try {
      const url = new URL(request.url, 'http://fixture');
      if (ASSETS.has(url.pathname)) {
        response.setHeader('Content-Type', url.pathname.endsWith('.css') ? 'text/css' : 'application/javascript');
        response.end(fs.readFileSync(path.join(ROOT, 'src/main/webapp', ASSETS.get(url.pathname))));
      } else if (url.pathname === '/') {
        response.setHeader('Content-Type', 'text/html');
        response.end(renderFixture());
      } else if (url.pathname === '/ws/rs/jobs/types/all') {
        response.setHeader('Content-Type', 'application/json');
        response.end(JSON.stringify(state.mode === 'empty' ? {} : {
          types: state.mode === 'single' ? state.rows[0] : state.rows,
        }));
      } else if (url.pathname === '/ws/rs/jobs/saveJobType' && request.method === 'POST') {
        let body = '';
        request.setEncoding('utf8');
        request.on('data', chunk => { body += chunk; });
        request.on('end', () => { state.saves.push({ response, body }); });
      } else if (url.pathname === '/favicon.ico') {
        response.writeHead(204).end();
      } else {
        state.errors.push(`Unexpected fixture request: ${request.method} ${url.pathname}`);
        response.writeHead(404).end();
      }
    } catch (error) {
      state.errors.push(error.message);
      response.writeHead(500).end('FAKE fixture setup error');
    }
  });
  const context = await browser.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => { state.errors.push(error.message); });
  page.on('dialog', async dialog => {
    state.alerts.push(dialog.message());
    await dialog.dismiss().catch(() => {}); // The context may be closing after a failed assertion.
  });
  try {
    await new Promise((resolve, reject) => {
      server.once('error', reject);
      server.listen(0, '127.0.0.1', resolve);
    });
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForFunction(() => typeof jobTypeTable !== 'undefined'
      && jobTypeTable && jobTypeTable.rows().count() === 30 && jQuery.active === 0);
    await exercise(page, state);
    assert.deepEqual(state.errors, [], 'The actual JSP script should not cause page or request errors');
    assert.deepEqual(state.alerts, state.expectedAlerts, 'Only the requested save-error alert is expected');
  } finally {
    try {
      await context.close();
    } finally {
      await new Promise(resolve => { server.close(resolve); server.closeAllConnections(); });
    }
  }
}

async function waitForSave(page, state, count) {
  await page.waitForFunction(() => jQuery.active > 0);
  const deadline = Date.now() + 5000;
  while (state.saves.length < count && Date.now() < deadline) {
    await new Promise(resolve => setTimeout(resolve, 10));
  }
  assert.equal(state.saves.length, count, 'Unexpected number of actual POST requests');
}

function completeSave(save, status) {
  save.response.writeHead(status, { 'Content-Type': 'application/json' });
  save.response.end('{}');
}

async function openNew(page) {
  await page.getByRole('button', { name: 'Add job type', exact: true }).click();
  await page.locator('#jobTypeName').fill('FAKE new job');
}

const CASES = [
  ['refresh preserves page, ordering, search and one DataTable instance', async (page) => {
    const before = await page.evaluate(() => {
      jobTypeTable.page.len(10).order([[0, 'desc']]).search('FAKE').page(1).draw(false);
      return { page: jobTypeTable.page.info().page, order: jobTypeTable.order(), search: jobTypeTable.search() };
    });
    assert.equal(before.page, 1, 'The control should start on page two');
    await page.evaluate(async () => { await getJobTypes(); await getJobTypes(); });
    const after = await page.evaluate(() => ({
      page: jobTypeTable.page.info().page, order: jobTypeTable.order(), search: jobTypeTable.search(),
      instances: jQuery.fn.dataTable.settings.length,
    }));
    assert.equal(after.page, before.page, 'Refreshing rows reset the current page');
    assert.deepEqual(after.order, before.order);
    assert.equal(after.search, before.search);
    assert.equal(after.instances, 1);
  }],
  ['pending Save is disabled and duplicate handler calls send only one POST', async (page, state) => {
    await openNew(page);
    const active = await page.evaluate(() => {
      const dialog = jQuery('#new-jobtype');
      const save = dialog.dialog('option', 'buttons').saveJobType.click;
      save.call(dialog[0]);
      save.call(dialog[0]);
      return jQuery.active;
    });
    assert.equal(active, 1, 'Two rapid save callbacks started duplicate requests');
    await waitForSave(page, state, 1);
    assert.equal(await page.getByRole('button', { name: 'Save', exact: true }).isDisabled(), true);
    completeSave(state.saves[0], 200);
    await page.waitForFunction(() => !jQuery('#new-jobtype').dialog('isOpen') && jQuery.active === 0);
    assert.equal(await page.locator('.ui-dialog-buttonpane button').first().isDisabled(), false);
  }],
  ['failed Save keeps the dialog and inputs; retry succeeds and closes it', async (page, state) => {
    state.expectedAlerts = ['FAKE save failed'];
    await openNew(page);
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await waitForSave(page, state, 1);
    completeSave(state.saves[0], 500);
    await page.waitForFunction(() => jQuery.active === 0);
    assert.equal(await page.evaluate(() => jQuery('#new-jobtype').dialog('isOpen')), true);
    assert.equal(await page.locator('#jobTypeName').inputValue(), 'FAKE new job');
    assert.deepEqual(state.alerts, ['FAKE save failed']);
    assert.equal(await page.getByRole('button', { name: 'Save', exact: true }).isDisabled(), false);
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await waitForSave(page, state, 2);
    assert.equal(new URLSearchParams(state.saves[1].body).get('jobType.name'), 'FAKE new job');
    completeSave(state.saves[1], 200);
    await page.waitForFunction(() => !jQuery('#new-jobtype').dialog('isOpen') && jQuery.active === 0);
  }],
  ['request setup failure restores Save and keeps the dialog open', async (page, state) => {
    state.expectedAlerts = ['FAKE save failed'];
    await openNew(page);
    await page.evaluate(() => { jQuery.post = () => { throw new Error('FAKE request setup failure'); }; });
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await page.waitForFunction(() => jQuery.active === 0);
    assert.equal(await page.evaluate(() => jQuery('#new-jobtype').dialog('isOpen')), true);
    assert.equal(await page.getByRole('button', { name: 'Save', exact: true }).isDisabled(), false);
    assert.deepEqual(state.alerts, ['FAKE save failed']);
    assert.equal(state.saves.length, 0);
  }],
  ['single and empty responses retain text-safe rows and the same table', async (page, state) => {
    const name = '<img src=x onerror="window.__unsafeJobName=true">';
    state.rows = [{ ...state.rows[0], name }];
    state.mode = 'single';
    await page.evaluate(async () => { await getJobTypes(); });
    assert.equal(await page.locator('#jobTypeTable tbody td').first().innerText(), name);
    assert.equal(await page.locator('#jobTypeTable tbody img').count(), 0);
    state.mode = 'empty';
    await page.evaluate(async () => { await getJobTypes(); });
    assert.equal(await page.evaluate(() => jobTypeTable.rows().count()), 0);
    assert.equal(await page.evaluate(() => jQuery.fn.dataTable.settings.length), 1);
  }],
];

async function main() {
  const browser = await chromium.launch({ headless: true });
  const results = [];
  try {
    for (const [name, exercise] of CASES) {
      try {
        await withFixture(browser, exercise);
        results.push({ name, passed: true });
      } catch (error) {
        results.push({ name, passed: false, error: error.message });
      }
    }
  } finally {
    await browser.close();
  }
  console.log(JSON.stringify({ jsp: JSP, results }, null, 2));
  if (results.some(result => !result.passed)) process.exitCode = 1;
}

if (require.main === module) main().catch(error => { console.error(error); process.exitCode = 1; });
