#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Isolated synthetic UI verification. No login to, deployment of, or writes to a CARLOS installation.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { spawn, execFileSync } = require('node:child_process');
const { createRequire } = require('node:module');

const root = path.resolve(__dirname, '../../..');
const tomcat = process.env.CHART_TEST_TOMCAT || '/usr/local/tomcat';
const reports = path.join(root, 'target/surefire-reports');
const report = fs.readdirSync(reports).find(name => name.startsWith('TEST-') && name.endsWith('.xml'));
assert(report, 'Run the documented Maven tests first to produce the test classpath.');
const xml = fs.readFileSync(path.join(reports, report), 'utf8');
const deps = xml.match(/<property name="java.class.path" value="([^"]+)"/)[1]
  .replaceAll('&amp;', '&').replaceAll('&quot;', '"');
const runDir = fs.mkdtempSync(path.join(root, 'target/chart-update-browser-'));
const webroot = path.join(runDir, 'webroot');
const classes = path.join(runDir, 'classes');
fs.mkdirSync(classes, { recursive: true });
function copy(relative) {
  const dest = path.join(webroot, relative);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(path.join(root, 'src/main/webapp', relative), dest);
}
for (const file of ['WEB-INF/jsp/documentManager/aiChartUpdates.jsp', 'WEB-INF/jspf/bootstrap-css.jspf',
  'WEB-INF/carlos-tag.tld', 'css/ai-chart-updates.css', 'js/ai-chart-updates.js', 'library/bootstrap/5.3.8/css/bootstrap.min.css']) copy(file);
fs.mkdirSync(path.join(webroot, 'WEB-INF/lib'), { recursive: true });
for (const jar of deps.split(path.delimiter).filter(p => /(?:jakarta.servlet.jsp.jstl|csrfguard-jsp-tags).*\.jar$/.test(p))) {
  fs.copyFileSync(jar, path.join(webroot, 'WEB-INF/lib', path.basename(jar)));
}
const classpath = [classes, `${tomcat}/lib/*`, deps].join(path.delimiter);
execFileSync('javac', ['-proc:none', '-cp', classpath, '-d', classes, path.join(__dirname, 'ChartUpdatesBrowserHarness.java')], { stdio: 'inherit' });
const server = spawn('java', ['-Dnet.bytebuddy.experimental=true', '-XX:+EnableDynamicAgentLoading', '-cp', classpath,
  'io.github.carlos_emr.carlos.clinical.summary.web.ChartUpdatesBrowserHarness', path.join(runDir, 'tomcat'), webroot,
  path.join(root, 'src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties')], { stdio: ['ignore', 'pipe', 'pipe'] });
const log = fs.createWriteStream(path.join(runDir, 'server.log'));
server.stdout.pipe(log, { end: false });
server.stderr.pipe(log, { end: false });
let browser;
let context;
const shutdown = () => { if (!server.killed) server.kill('SIGTERM'); };
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

async function run() {
  const base = await new Promise((resolve, reject) => {
    let output = '';
    const timer = setTimeout(() => reject(new Error(`Harness startup timed out. See ${runDir}/server.log`)), 45000);
    server.once('exit', code => { clearTimeout(timer); reject(new Error(`Harness exited ${code}. See ${runDir}/server.log`)); });
    server.stdout.on('data', chunk => {
      output += chunk.toString();
      const found = output.match(/CHART_UPDATE_BROWSER_URL=(http:\/\/127\.0\.0\.1:\d+\/carlos)/);
      if (found) { clearTimeout(timer); resolve(found[1]); }
    });
  });
  let playwright;
  try { playwright = require('playwright'); }
  catch { playwright = createRequire(path.join(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim(), 'resolver.js'))('playwright'); }
  browser = await playwright.chromium.launch({ headless: true, args: ['--no-sandbox'],
    ...(process.env.CHART_TEST_CHROMIUM ? { executablePath: process.env.CHART_TEST_CHROMIUM } : {}) });
  const origin = new URL(base).origin;
  let checks = 0;
  async function scenario(name, body) {
    context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    // First navigation includes JSP compilation and instrumentation of the test doubles.
    context.setDefaultNavigationTimeout(60000);
    await context.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const response = await page.goto(`${base}/documentManager/AiChartUpdates?documentId=42`);
    assert.equal(response.status(), 200, `Page failed to render; see ${runDir}/server.log`);
    await page.getByRole('heading', { name: 'Review chart updates', exact: true }).waitFor();
    await body(page);
    assert.deepEqual(errors, [], 'Unexpected page JavaScript error');
    await context.close();
    console.log(`PASS ${name}`);
    checks++;
  }
  const click = async (page, locator) => {
    await Promise.all([page.waitForNavigation({ waitUntil: 'load' }), locator.click()]);
  };
  const generate = async page => {
    const regenerate = page.locator('details.regenerate:not([open]) > summary');
    if (await regenerate.count()) await regenerate.click();
    await click(page, page.getByRole('button', { name: 'Generate new proposals', exact: true }));
    assert.match(page.url(), /\/AiChartUpdates\?documentId=42$/, 'Generation must redirect to a refresh-safe GET');
  };
  const card = (page, kind) => page.locator('article').filter({ has: page.getByRole('heading', { name: kind, exact: true }) });
  const stats = async page => (await page.request.get(`${base}/fixture/stats`)).json();
  const token = page => page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
  const change = async (page, name) => {
    const response = await page.request.post(`${base}/fixture/${name}`, { form: { 'CSRF-TOKEN': await token(page) } });
    assert.equal(response.status(), 204);
  };
  const fillReminder = async page => {
    const reminder = card(page, 'Follow-up reminder');
    await reminder.locator('[name="dueDate"]').fill('2026-10-12');
    await reminder.locator('[name="assignee"]').selectOption('101');
    await reminder.locator('[name="confirmed"]').check();
    return reminder;
  };

  await scenario('preview and generation never write; hostile source is escaped', async page => {
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    await generate(page);
    assert.equal(await page.locator('article').count(), 2);
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="dueDate"]').inputValue(), '');
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="assignee"]').inputValue(), '101');
    assert.equal(await card(page, 'History entry').locator('[name="destination"]').inputValue(), 'Concerns');
    assert(await token(page), 'CSRF token must be present');
    await card(page, 'Follow-up reminder').getByRole('button', { name: 'Accept and save', exact: true }).click();
    assert(await card(page, 'Follow-up reminder').locator('[name="dueDate"]').evaluate(input => !input.validity.valid),
      'A missing due date must fail browser validation');
    assert.equal(await page.evaluate(() => window.sourceExecuted), undefined);
    assert.equal(await page.locator('script:not([src])').count(), 0, 'Source markup must not become executable HTML');
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    await page.screenshot({ path: path.join(runDir, 'desktop-review.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'Mobile horizontal overflow');
    await page.screenshot({ path: path.join(runDir, 'mobile-review.png'), fullPage: true });
  });
  await scenario('prefill dates and destination while retaining explicit approval and edits', async page => {
    await change(page, 'clear-timing');
    await generate(page);
    const reminder = card(page, 'Follow-up reminder');
    assert.equal(await reminder.locator('[name="dueDate"]').inputValue(), '2026-10-26');
    assert.equal(await reminder.locator('[name="assignee"]').inputValue(), '101');
    assert.match(await reminder.innerText(), /document date, 2026-09-28/);
    assert.equal(await reminder.locator('[name="confirmed"]').isChecked(), false);
    await reminder.getByRole('button', { name: 'Accept and save', exact: true }).click();
    assert.equal(await reminder.locator('[name="confirmed"]').evaluate(input => input.validity.valid), false);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    await page.screenshot({ path: path.join(runDir, 'suggested-fields-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
    await page.screenshot({ path: path.join(runDir, 'suggested-fields-mobile.png'), fullPage: true });
    await reminder.locator('[name="dueDate"]').fill('2026-11-02');
    await click(page, card(page, 'History entry').getByRole('button', { name: 'Dismiss', exact: true }));
    await page.reload();
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="dueDate"]').inputValue(), '2026-11-02');
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="confirmed"]').isChecked(), false);
  });
  await scenario('dismiss bypasses required fields and creates no chart entry', async page => {
    await generate(page);
    await click(page, card(page, 'Follow-up reminder').getByRole('button', { name: 'Dismiss', exact: true }));
    assert.match(await card(page, 'Follow-up reminder').innerText(), /Dismissed. Nothing saved/);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('saving one item preserves other edits without carrying approval', async page => {
    await generate(page);
    let history = card(page, 'History entry');
    const edited = '<b>Clinician reviewed history</b>';
    await history.locator('[name="entryText"]').fill(edited);
    await history.locator('[name="destination"]').selectOption('Concerns');
    await history.locator('[name="confirmed"]').check();
    const reminder = await fillReminder(page);
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(page.url(), /\/AiChartUpdates\?documentId=42$/);
    history = card(page, 'History entry');
    assert.equal(await history.locator('[name="entryText"]').inputValue(), edited);
    assert.equal(await history.locator('[name="destination"]').inputValue(), 'Concerns');
    assert.equal(await history.locator('[name="confirmed"]').isChecked(), false);
    await page.reload();
    assert.equal(await history.locator('[name="entryText"]').inputValue(), edited);
    assert.deepEqual(await stats(page), { reminders: 1, histories: 0, receipts: 1 });
  });
  await scenario('approve edited reminder and signed history once, then safely replay', async page => {
    await generate(page);
    let reminder = await fillReminder(page);
    const hostile = '<img src=x onerror="window.savedExecuted=true"> Clinician reviewed follow-up';
    await reminder.locator('[name="entryText"]').fill(hostile);
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await card(page, 'Follow-up reminder').innerText(), /Saved: tickler/);
    assert.equal(await page.evaluate(() => window.savedExecuted), undefined);
    let history = card(page, 'History entry');
    await history.locator('[name="destination"]').selectOption('MedHistory');
    await history.locator('[name="confirmed"]').check();
    await click(page, history.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await card(page, 'History entry').innerText(), /Saved: history/);
    assert.deepEqual(await stats(page), { reminders: 1, histories: 1, receipts: 2 });
    await generate(page);
    reminder = await fillReminder(page);
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await card(page, 'Follow-up reminder').innerText(), /Already saved: tickler/);
    assert.deepEqual(await stats(page), { reminders: 1, histories: 1, receipts: 2 });
  });
  await scenario('restored page can submit without duplicate draft fields', async page => {
    await generate(page);
    const reminder = await fillReminder(page);
    const history = card(page, 'History entry');
    await history.locator('[name="entryText"]').fill('First history edit');
    // Keep the submitted DOM alive, then deliver the lifecycle event emitted when
    // a browser restores that document. This avoids browser-specific cache eligibility.
    await reminder.locator('form').evaluate(form => form.addEventListener('submit', event => event.preventDefault(), { once: true }));
    await reminder.getByRole('button', { name: 'Accept and save', exact: true }).click();
    await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })));
    await history.locator('[name="entryText"]').fill('Latest history edit');
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.equal(await page.getByRole('alert').count(), 0);
    assert.equal(await card(page, 'History entry').locator('[name="entryText"]').inputValue(), 'Latest history edit');
    assert.deepEqual(await stats(page), { reminders: 1, histories: 0, receipts: 1 });
  });
  await scenario('stale chart blocks approval, preserves edits and permits rereview', async page => {
    await generate(page);
    let reminder = await fillReminder(page);
    await reminder.locator('[name="entryText"]').fill('Clinician edited reminder');
    await change(page, 'chart-change');
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await page.getByRole('alert').innerText(), /chart changed during review/);
    reminder = card(page, 'Follow-up reminder');
    assert.equal(await reminder.locator('[name="entryText"]').inputValue(), 'Clinician edited reminder');
    assert.equal(await reminder.locator('[name="confirmed"]').isChecked(), false);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    await reminder.locator('[name="confirmed"]').check();
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.deepEqual(await stats(page), { reminders: 1, histories: 0, receipts: 1 });
  });
  await scenario('changed source and expired review cannot write', async page => {
    await generate(page);
    const reminder = await fillReminder(page);
    await change(page, 'source-change');
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await page.getByRole('alert').innerText(), /source changed/i);
    await generate(page);
    await fillReminder(page);
    await change(page, 'expire');
    await click(page, card(page, 'Follow-up reminder').getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await page.getByRole('alert').innerText(), /expired/i);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('older tab remains stale after another tab refreshes the session review', async page => {
    await generate(page);
    const reminder = await fillReminder(page);
    await change(page, 'chart-change');
    const newerTab = await context.newPage();
    await newerTab.goto(`${base}/documentManager/AiChartUpdates?documentId=42`);
    await newerTab.getByRole('heading', { name: 'Proposed updates (2)', exact: true }).waitFor();
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await page.getByRole('alert').innerText(), /chart changed during review/);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('real CSRF filter rejects missing tokens; mutation routes reject GET', async page => {
    const csrfResponse = await page.request.post(`${base}/documentManager/GenerateAiChartUpdates`, { form: { documentId: '42' } });
    assert.equal(csrfResponse.status(), 403);
    const getResponse = await page.request.get(`${base}/documentManager/ApplyAiChartUpdate?documentId=42`);
    assert.equal(getResponse.status(), 405);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  console.log(`${checks} synthetic browser scenarios passed. Artifacts: ${runDir}`);
}
run().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  if (context) await context.close().catch(() => {});
  if (browser) await browser.close().catch(() => {});
  shutdown();
  setTimeout(() => { if (server.exitCode === null) server.kill('SIGKILL'); }, 5000).unref();
});
