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
  'WEB-INF/carlos-tag.tld', 'css/ai-chart-updates.css', 'share/css/global.css', 'js/ai-chart-updates.js', 'js/ai-chart-updates-matching.js', 'js/ai-chart-updates-evidence.js', 'js/ai-chart-updates-navigation.js', 'js/ai-chart-updates-modal.js', 'WEB-INF/jspf/chart-update-workflow-dialog.jspf', 'css/ai-chart-updates-navigation.css', 'WEB-INF/jspf/chart-update-error-dialog.jspf', 'library/bootstrap/5.3.8/css/bootstrap.min.css']) copy(file);
fs.writeFileSync(path.join(webroot, 'fixture-picker.jsp'), `<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %><%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/><!doctype html><html><head><title>Synthetic document list</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="/carlos/css/ai-chart-updates-navigation.css"></head><body>
<h1>Synthetic patient document list</h1><input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
<a class="chart-update-document-link" href="/carlos/documentManager/AiChartUpdates?documentId=42"
 data-document-title="Synthetic &lt;img src=x onerror=window.titleExecuted=true&gt;" data-original-url="/carlos/fixture/original">Review chart updates</a>
<%@ include file="/WEB-INF/jspf/chart-update-error-dialog.jspf" %>
<%@ include file="/WEB-INF/jspf/chart-update-workflow-dialog.jspf" %>
<script src="/carlos/js/ai-chart-updates-navigation.js"></script>
<script src="/carlos/js/ai-chart-updates-modal.js"></script></body></html>`);
fs.writeFileSync(path.join(webroot, 'fixture-echart.jsp'), `<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %><%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/><!doctype html><html><head><title>Synthetic eChart</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="/carlos/library/bootstrap/5.3.8/css/bootstrap.min.css">
<link rel="stylesheet" href="/carlos/css/ai-chart-updates-navigation.css"></head><body>
<h1>Synthetic eChart</h1><textarea aria-label="Encounter draft"></textarea>
<a class="chart-update-workflow-link" target="_blank" href="/carlos/documentManager/ViewDocumentReport?function=demographic&amp;functionid=3001&amp;chartUpdates=1">Review chart updates</a>
<%@ include file="/WEB-INF/jspf/chart-update-workflow-dialog.jspf" %>
<script src="/carlos/js/ai-chart-updates-modal.js"></script></body></html>`);
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
    await change(page, 'repeat-source');
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    await generate(page);
    assert.equal(await page.locator('article').count(), 2);
    const fullSource = await page.locator('#chart-update-source').textContent();
    assert.equal(await page.locator('.source-highlight').count(), 2);
    const historyPassage = await card(page, 'Chart entry').locator('.proposal-evidence blockquote').textContent();
    await card(page, 'Chart entry').getByRole('link', { name: 'Show passage in document' }).click();
    assert.deepEqual(await page.locator('.source-highlight').allTextContents(), [historyPassage]);
    assert.equal(await page.locator('#chart-update-source').textContent(), fullSource);
    assert.equal(await page.locator('#chart-update-source script').count(), 0);
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="dueDate"]').inputValue(), '');
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="assignee"]').inputValue(), '101');
    assert.equal(await card(page, 'Chart entry').locator('[name="destination"]').inputValue(), 'Concerns');
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
    await click(page, card(page, 'Chart entry').getByRole('button', { name: 'Dismiss', exact: true }));
    await page.reload();
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="dueDate"]').inputValue(), '2026-11-02');
    assert.equal(await card(page, 'Follow-up reminder').locator('[name="confirmed"]').isChecked(), false);
  });
  await scenario('unavailable document opens a modal and keeps the document list usable', async page => {
    await change(page, 'unavailable');
    await page.goto(`${base}/fixture/picker`);
    const originalUrl = page.url();
    const link = page.getByRole('link', { name: 'Review chart updates', exact: true });
    await link.click();
    const dialog = page.getByRole('dialog', { name: 'Document review unavailable' });
    await dialog.waitFor();
    assert.equal(page.url(), originalUrl);
    assert.match(await dialog.innerText(), /Document text is unavailable. Reopen the original/);
    assert.match(await dialog.locator('.chart-update-error-document').innerText(), /<img/);
    assert.equal(await page.evaluate(() => window.titleExecuted), undefined);
    assert.equal(await dialog.getByRole('link', { name: 'Open original' }).getAttribute('href'), `${base}/fixture/original`);
    assert.equal(await dialog.getByRole('button', { name: 'Close', exact: true }).evaluate(el => el === document.activeElement), true);
    const opened = page.waitForEvent('popup');
    await dialog.getByRole('link', { name: 'Open original' }).click();
    const original = await opened;
    await original.waitForLoadState('domcontentloaded');
    assert.match(await original.locator('body').innerText(), /Readable synthetic original/);
    await original.close();
    await page.setViewportSize({ width: 390, height: 844 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.screenshot({ path: path.join(runDir, 'unavailable-document-modal.png'), fullPage: true });
    await page.keyboard.press('Escape');
    assert.equal(await dialog.isVisible(), false);
    assert.equal(await link.evaluate(el => el === document.activeElement), true);
    await change(page, 'missing-original');
    await link.click();
    await dialog.waitFor();
    assert.match(await dialog.innerText(), /original document file is missing/);
    assert.equal(await dialog.locator('.chart-update-original').isVisible(), false);
    assert.equal(await dialog.locator('.chart-update-original').getAttribute('href'), null);
    await dialog.getByRole('button', { name: 'Close', exact: true }).click();
    await page.route('**/documentManager/AiChartUpdates?documentId=42', route => route.fulfill({ status: 403, body: 'Forbidden' }));
    await link.click();
    await dialog.waitFor();
    assert.match(await dialog.innerText(), /Could not open this review/);
    assert.equal(await dialog.locator('.chart-update-original').isVisible(), false);
    await dialog.getByRole('button', { name: 'Close', exact: true }).click();
    await page.unroute('**/documentManager/AiChartUpdates?documentId=42');
    await change(page, 'available');
    await link.click();
    await page.frameLocator('#chart-update-workflow-frame').getByRole('button', { name: 'Generate new proposals', exact: true }).waitFor();
    assert.equal(page.url(), originalUrl);
    assert.equal(context.pages().length, 1);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('eChart modal keeps drafts, reviews one item at a time and closes safely', async page => {
    await change(page, 'unavailable');
    await page.goto(`${base}/fixture/echart`);
    await page.getByRole('textbox', { name: 'Encounter draft' }).fill('Unsaved encounter text');
    const parentUrl = page.url();
    const launch = page.getByRole('link', { name: 'Review chart updates', exact: true });
    await launch.click();
    const modal = page.getByRole('dialog', { name: 'Review chart updates', exact: true });
    const frame = page.frameLocator('#chart-update-workflow-frame');
    await frame.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    const error = frame.getByRole('dialog', { name: 'Document review unavailable' });
    await error.waitFor();
    await page.keyboard.press('Escape');
    await error.waitFor({ state: 'hidden' });
    assert.equal(await modal.isVisible(), true);
    const available = await page.request.post(`${base}/fixture/available`, {
      form: { 'CSRF-TOKEN': await frame.locator('input[name="CSRF-TOKEN"]').first().inputValue() },
    });
    assert.equal(available.status(), 204);
    await frame.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    await frame.getByRole('button', { name: 'Generate new proposals', exact: true }).click();
    await frame.locator('.review-steps:not([hidden])').waitFor();
    await frame.locator('article.proposal:visible').waitFor();
    assert.equal(await frame.locator('article.proposal:visible').count(), 1);
    assert.equal(await frame.locator('article.proposal').count(), 2);
    assert.equal(await frame.locator('[data-review-position]').innerText(), '1 / 2');
    await frame.locator('article.proposal:visible [name="entryText"]').fill('Edited reminder for later');
    await frame.getByRole('button', { name: 'Next', exact: true }).click();
    assert.equal(await frame.locator('[data-review-position]').innerText(), '2 / 2');
    assert.deepEqual(await frame.locator('.source-highlight').allTextContents(),
      [await frame.locator('article.proposal:visible .proposal-evidence blockquote').textContent()]);
    await frame.getByRole('button', { name: 'Previous', exact: true }).click();
    assert.equal(await frame.locator('article.proposal:visible [name="entryText"]').inputValue(), 'Edited reminder for later');
    page.once('dialog', dialog => dialog.dismiss());
    await modal.getByRole('button', { name: 'Close', exact: true }).click();
    assert.equal(await modal.isVisible(), true);
    await frame.getByRole('button', { name: 'Next', exact: true }).click();
    // Dismissing the current item carries the other card's unsaved edits in the existing POST.
    await frame.locator('article.proposal:visible').getByRole('button', { name: 'Dismiss', exact: true }).click();
    await frame.getByRole('heading', { name: 'Follow-up reminder', exact: true }).waitFor();
    assert.equal(await frame.locator('article.proposal:visible [name="entryText"]').inputValue(), 'Edited reminder for later');
    assert.equal(await frame.locator('[name="confirmed"]:checked').count(), 0);
    await page.setViewportSize({ width: 390, height: 844 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    assert(await frame.locator('body').evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.screenshot({ path: path.join(runDir, 'echart-review-modal-mobile.png'), fullPage: true });
    await modal.getByRole('button', { name: 'Close', exact: true }).click();
    assert.equal(await modal.isVisible(), false);
    assert.equal(await launch.evaluate(el => el === document.activeElement), true);
    assert.equal(await page.getByRole('textbox', { name: 'Encounter draft' }).inputValue(), 'Unsaved encounter text');
    assert.equal(page.url(), parentUrl);
    assert.equal(context.pages().length, 1);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    await launch.click();
    await frame.getByRole('link', { name: 'Review chart updates', exact: true }).waitFor();
    await page.keyboard.press('Escape');
    await modal.waitFor({ state: 'hidden' });
    await launch.click();
    await frame.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    await frame.locator('.review-steps:not([hidden])').waitFor();
    await frame.locator('article.proposal:visible [name="entryText"]').fill('Unsaved modal edit to discard');
    page.once('dialog', dialog => { assert.equal(dialog.type(), 'confirm'); return dialog.accept(); });
    await modal.getByRole('button', { name: 'Close', exact: true }).click();
    await modal.waitFor({ state: 'hidden' });
    assert.equal(await page.getByRole('textbox', { name: 'Encounter draft' }).inputValue(), 'Unsaved encounter text');
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('modal stays open while saving and retains edited drafts after approval', async page => {
    await page.goto(`${base}/fixture/echart`);
    await page.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    const modal = page.getByRole('dialog', { name: 'Review chart updates', exact: true });
    const frame = page.frameLocator('#chart-update-workflow-frame');
    await frame.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    await frame.getByRole('button', { name: 'Generate new proposals', exact: true }).click();
    await frame.locator('.review-steps:not([hidden])').waitFor();
    await frame.locator('article.proposal:visible [name="entryText"]').waitFor();
    await frame.locator('article.proposal:visible [name="entryText"]').fill('Reminder draft retained after history save');
    await frame.getByRole('button', { name: 'Next', exact: true }).click();
    await frame.locator('article.proposal:visible [name="confirmed"]').check();
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    await page.route('**/documentManager/ApplyAiChartUpdate', async route => { await gate; await route.continue(); });
    await frame.getByRole('button', { name: 'Accept and save', exact: true }).click({ noWaitAfter: true });
    await page.waitForFunction(() => document.querySelector('[data-close-chart-update-workflow]').disabled);
    assert.equal(await modal.isVisible(), true);
    await page.keyboard.press('Escape');
    assert.equal(await modal.isVisible(), true);
    release();
    await frame.locator('article.proposal:visible [name="entryText"]').waitFor();
    await page.waitForFunction(() => !document.querySelector('[data-close-chart-update-workflow]').disabled);
    assert.equal(await frame.locator('article.proposal:visible [name="entryText"]').inputValue(), 'Reminder draft retained after history save');
    assert.deepEqual(await stats(page), { reminders: 0, histories: 1, receipts: 1 });
    await frame.getByRole('link', { name: 'Back', exact: true }).click();
    await frame.getByRole('link', { name: 'Review chart updates', exact: true }).waitFor();
    await modal.getByRole('button', { name: 'Close', exact: true }).click();
    assert.equal(context.pages().length, 1);
  });
  await scenario('matching chart text is visible before approval and duplicate saves are blocked', async page => {
    await change(page, 'matching-chart');
    await generate(page);
    const history = card(page, 'Chart entry');
    await history.locator('[name="entryText"]').focus();
    assert.equal(await history.locator('.chart-match-notice').isVisible(), true);
    await history.locator('.chart-match-links a').click();
    assert.equal(await page.locator('#chart-entry-note-duplicate').evaluate(el => el.open), true);
    assert.equal(await page.locator('#chart-entry-note-duplicate').evaluate(el => el.classList.contains('chart-entry-match')), true);
    await history.locator('[name="confirmed"]').check();
    await click(page, history.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await page.locator('.alert-danger').innerText(), /Matching text is already recorded/);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    assert.equal(await page.locator('article.proposal').count(), 2);
    const reminder = card(page, 'Follow-up reminder');
    assert.equal(await reminder.locator('.chart-match-notice').isVisible(), false);
    await reminder.locator('[name="entryText"]').fill('Previous clinician entry: seasonal symptoms.');
    assert.equal(await reminder.locator('.chart-match-notice').isVisible(), false, 'A reminder must not be treated as a duplicate of a history note');
    await reminder.locator('[name="entryText"]').fill('New reminder text');
    assert.equal(await reminder.locator('.chart-match-notice').isVisible(), false);
    await history.getByRole('link', { name: 'Show passage in document' }).click();
    await page.screenshot({ path: path.join(runDir, 'source-highlight-chart-match.png'), fullPage: true });
  });
  await scenario('coverage audit links to modal cards without changing drafts or approvals', async page => {
    await change(page, 'broad');
    await page.goto(`${base}/fixture/echart`);
    await page.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    const frame = page.frameLocator('#chart-update-workflow-frame');
    await frame.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    await frame.getByRole('button', { name: 'Generate new proposals', exact: true }).click();
    await frame.locator('.review-steps:not([hidden])').waitFor();
    await frame.locator('article.proposal:visible [name="entryText"]').fill('Draft social history');
    const audit = frame.locator('#coverage-audit');
    await audit.locator(':scope > summary').click();
    assert.match(await audit.innerText(), /1 source sections processed/);
    assert.match(await audit.innerText(), /not a count of clinical facts or proof of completeness/);
    await audit.locator('.coverage-section > summary').click();
    assert.match(await audit.locator('.coverage-gap').textContent(), /Unselected finding <img/);
    await audit.locator('.coverage-rejected > summary').click();
    assert.match(await audit.locator('.coverage-rejected').innerText(), /Untrusted explanation <script>/);
    assert.equal(await audit.locator('script, img').count(), 0);
    // New-tab routes (modified or middle click, context menu) use the GET review, never the frame's URL.
    const suggestion = audit.getByRole('link', { name: 'Suggestion 3', exact: true });
    const href = await suggestion.getAttribute('href');
    assert.match(href, /\/documentManager\/AiChartUpdates\?documentId=\d+#proposal-/);
    const standalone = await page.context().newPage();
    assert.equal((await standalone.goto(new URL(href, base).href)).status(), 200);
    assert.equal(await standalone.locator('article.proposal:target').getAttribute('data-proposal-key'),
        await suggestion.getAttribute('data-review-proposal'));
    await standalone.close();
    await audit.getByRole('link', { name: 'Suggestion 3', exact: true }).dispatchEvent('click', { button: 0, ctrlKey: true });
    assert.equal(await frame.locator('[data-review-position]').textContent(), '1 / 5');
    await audit.getByRole('link', { name: 'Suggestion 3', exact: true }).click();
    assert.equal(await frame.locator('[data-review-position]').textContent(), '3 / 5');
    assert.equal(await frame.locator('article.proposal:visible').getAttribute('data-kind'), 'review');
    await audit.getByRole('link', { name: 'Suggestion 1', exact: true }).click();
    assert.equal(await frame.locator('article.proposal:visible [name="entryText"]').inputValue(), 'Draft social history');
    assert.equal(await frame.locator('article.proposal:visible [name="confirmed"]').isChecked(), false);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('coverage remains visible when no suggestions survive', async page => {
    await change(page, 'broad');
    await change(page, 'empty-coverage');
    await generate(page);
    assert.equal(await page.locator('article.proposal').count(), 0);
    await page.locator('#coverage-audit > summary').click();
    await page.locator('.coverage-section > summary').click();
    assert.equal(await page.locator('.coverage-gap').textContent(), await page.locator('#chart-update-source').textContent());
    assert.equal(await page.locator('[data-review-proposal]').count(), 0);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('legacy results honestly show the coverage audit as unavailable', async page => {
    await generate(page);
    await page.locator('#coverage-audit > summary').click();
    assert.match(await page.locator('#coverage-audit').innerText(), /no section coverage audit/);
    assert.equal(await page.locator('.coverage-section').count(), 0);
  });
  await scenario('broad facts use section-aware comparison and explicit native handoff', async page => {
    await change(page, 'broad');
    await generate(page);
    assert.equal(await page.locator('article').count(), 5);
    const histories = card(page, 'Chart entry');
    const social = histories.filter({ hasText: 'Lives with daughter' });
    assert.equal(await social.locator('[name="destination"]').inputValue(), 'SocHistory');
    assert.equal(await social.locator('[name="destination"] option').count(), 8);
    const diagnosis = histories.filter({ hasText: 'Hypertension' });
    assert.equal(await diagnosis.locator('.chart-match-notice').isVisible(), false);
    await diagnosis.locator('[name="destination"]').selectOption('FamHistory');
    assert.equal(await diagnosis.locator('.chart-match-notice').isVisible(), true);
    assert.match(await diagnosis.locator('.chart-match-links').innerText(), /Family history/);
    await diagnosis.locator('[name="destination"]').selectOption('');
    assert.equal(await diagnosis.locator('.chart-match-notice').isVisible(), false);
    await diagnosis.locator('[name="destination"]').selectOption('MedHistory');
    assert.equal(await diagnosis.locator('.chart-match-notice').isVisible(), false);
    for (const name of ['Medications', 'Allergies']) {
      const native = card(page, name);
      assert.equal(await native.locator('[name="confirmed"]').count(), 0);
      assert.equal(await native.getByRole('button', { name: 'Accept and save', exact: true }).count(), 0);
      assert.equal(await native.locator('.native-review-open').count(), 0);
      assert.match(await native.innerText(), /Closing this suggestion does not save a record/);
    }
    const prevention = page.locator('article[data-destination="Preventions"]');
    await prevention.getByRole('button', { name: 'Open normal chart form' }).click();
    const dialog = page.locator('#native-chart-review');
    await dialog.waitFor({ state: 'visible' });
    const nativeFrame = page.frameLocator('#native-chart-review iframe');
    assert.equal(await nativeFrame.locator('[name="demographic_no"]').inputValue(), '3001');
    assert.equal(await dialog.locator('.native-review-source').textContent(), 'Immunization: influenza given.');
    await page.setViewportSize({ width: 1000, height: 600 });
    const frameBox = await dialog.locator('iframe').boundingBox();
    assert(frameBox.height >= 160 && frameBox.y + frameBox.height <= 600, 'Native form fits a short eChart window');
    await nativeFrame.getByRole('textbox', { name: 'Native draft' }).fill('Unsaved native draft');
    page.once('dialog', event => event.dismiss());
    await dialog.getByRole('button', { name: 'Close', exact: true }).click();
    assert.equal(await dialog.isVisible(), true);
    page.once('dialog', event => event.accept());
    await dialog.getByRole('button', { name: 'Close', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    assert.equal(context.pages().length, 1);
    await click(page, card(page, 'Allergies').getByRole('button', { name: 'Done reviewing this item', exact: true }));
    assert.match(await card(page, 'Allergies').innerText(), /did not save a record/);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
    await social.locator('[name="confirmed"]').check();
    await click(page, social.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.deepEqual(await stats(page), { reminders: 0, histories: 1, receipts: 1 });
    assert.match(await page.locator('#chart-entry-note-201 summary').innerText(), /Social history/);
  });
  await scenario('paraphrased chart matches show exact passages and respect clinical qualifiers', async page => {
    await change(page, 'paraphrased-chart');
    await generate(page);
    const history = card(page, 'Chart entry');
    await history.locator('[name="entryText"]').fill('Hypertension');
    assert.equal(await history.locator('.chart-match-notice').isVisible(), true);
    assert.equal(await history.locator('.chart-match-links a').count(), 1);
    assert.equal(await history.locator('.chart-match-passage').textContent(), 'HTN');
    await history.locator('.chart-match-links a').click();
    assert.equal(await page.locator('#chart-entry-note-paraphrase').evaluate(el => el.open), true);
    for (const draft of ['OA of the left knee', 'Left knee OA']) {
      await history.locator('[name="entryText"]').fill(draft);
      assert.equal(await history.locator('.chart-match-passage').textContent(), 'Left knee osteoarthritis.');
    }
    await page.screenshot({ path: path.join(runDir, 'paraphrased-chart-match.png'), fullPage: true });
    for (const draft of ['Right knee OA', 'Bilateral knee OA', 'No hypertension', 'Asthma']) {
      await history.locator('[name="entryText"]').fill(draft);
      // Identical whole-entry negation can still match; positive asthma cannot match "No asthma".
      assert.equal(await history.locator('.chart-match-notice').isVisible(), draft === 'No hypertension');
    }
    const markup = 'Synthetic <img src=x onerror=window.matchExecuted=true> entry';
    await history.locator('[name="entryText"]').fill(markup);
    assert.equal(await history.locator('.chart-match-passage').textContent(), markup);
    assert.equal(await history.locator('.chart-match-passage img').count(), 0);
    assert.equal(await page.evaluate(() => window.matchExecuted), undefined);
    assert.equal(await page.locator('[name="confirmed"]:checked').count(), 0);
    assert.equal(await page.locator('article.proposal').count(), 2);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('related suggestions stay editable and comparisons display text safely', async page => {
    await generate(page);
    const history = card(page, 'Chart entry');
    const reminder = card(page, 'Follow-up reminder');
    await history.locator('[name="entryText"]').fill('Subdural hygroma causing acute confusion.');
    const text = 'Acute confusion secondary to subdural hygroma. <img src=x onerror=window.relatedExecuted=true>';
    await reminder.locator('[name="entryText"]').fill(text);
    assert.equal(await history.locator('.related-proposal-notice').isVisible(), true);
    await history.locator('.related-proposal-notice summary').click();
    assert.equal(await history.locator('.related-proposal-quotes blockquote').textContent(), text);
    assert.equal(await history.locator('.related-proposal-quotes img').count(), 0);
    assert.equal(await page.evaluate(() => window.relatedExecuted), undefined);
    assert.equal(await page.locator('[name="confirmed"]:checked').count(), 0);
    assert.equal(await page.locator('article.proposal').count(), 2);
    await reminder.locator('[name="entryText"]').fill('Arrange an unrelated appointment.');
    assert.equal(await history.locator('.related-proposal-notice').isVisible(), false);
    assert.equal(await reminder.locator('.related-proposal-notice').isVisible(), false);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('regeneration requires confirmation before discarding edited drafts', async page => {
    await generate(page);
    const history = card(page, 'Chart entry');
    await history.locator('[name="entryText"]').fill('Keep this edited draft');
    page.once('dialog', dialog => dialog.dismiss());
    const regeneration = page.locator('details.regenerate:not([open]) > summary');
    if (await regeneration.count()) await regeneration.click();
    await page.getByRole('button', { name: 'Generate new proposals', exact: true }).click();
    assert.equal(await history.locator('[name="entryText"]').inputValue(), 'Keep this edited draft');
    assert.equal(await page.evaluate(() => window.CarlosChartUpdateReview.busy), false);
    // Saving another card persists this draft server-side, then reloads with edited=false.
    const reminder = card(page, 'Follow-up reminder');
    await reminder.locator('[name="dueDate"]').fill('2026-10-12');
    await reminder.locator('[name="confirmed"]').check();
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.equal(await page.evaluate(() => window.CarlosChartUpdateReview.dirty), false);
    await page.locator('details.regenerate:not([open]) > summary').click();
    page.once('dialog', dialog => dialog.dismiss());
    await page.getByRole('button', { name: 'Generate new proposals', exact: true }).click();
    assert.equal(await card(page, 'Chart entry').locator('[name="entryText"]').inputValue(), 'Keep this edited draft');
    page.once('dialog', dialog => dialog.accept());
    await generate(page);
    assert.notEqual(await card(page, 'Chart entry').locator('[name="entryText"]').inputValue(), 'Keep this edited draft');
    assert.deepEqual(await stats(page), { reminders: 1, histories: 0, receipts: 1 });
  });
  await scenario('dismiss bypasses required fields and creates no chart entry', async page => {
    await generate(page);
    await click(page, card(page, 'Follow-up reminder').getByRole('button', { name: 'Dismiss', exact: true }));
    assert.match(await card(page, 'Follow-up reminder').innerText(), /Dismissed. Nothing saved/);
    assert.deepEqual(await stats(page), { reminders: 0, histories: 0, receipts: 0 });
  });
  await scenario('saving one item preserves other edits without carrying approval', async page => {
    await generate(page);
    let history = card(page, 'Chart entry');
    const edited = '<b>Clinician reviewed history</b>';
    await history.locator('[name="entryText"]').fill(edited);
    await history.locator('[name="destination"]').selectOption('Concerns');
    await history.locator('[name="confirmed"]').check();
    const reminder = await fillReminder(page);
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(page.url(), /\/AiChartUpdates\?documentId=42$/);
    history = card(page, 'Chart entry');
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
    let history = card(page, 'Chart entry');
    await history.locator('[name="destination"]').selectOption('MedHistory');
    await history.locator('[name="confirmed"]').check();
    await click(page, history.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.match(await card(page, 'Chart entry').innerText(), /Saved: history/);
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
    const history = card(page, 'Chart entry');
    await history.locator('[name="entryText"]').fill('First history edit');
    // Keep the submitted DOM alive, then deliver the lifecycle event emitted when
    // a browser restores that document. This avoids browser-specific cache eligibility.
    await reminder.locator('form').evaluate(form => form.addEventListener('submit', event => event.preventDefault(), { once: true }));
    await reminder.getByRole('button', { name: 'Accept and save', exact: true }).click();
    await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })));
    await history.locator('[name="entryText"]').fill('Latest history edit');
    await click(page, reminder.getByRole('button', { name: 'Accept and save', exact: true }));
    assert.equal(await page.getByRole('alert').count(), 0);
    assert.equal(await card(page, 'Chart entry').locator('[name="entryText"]').inputValue(), 'Latest history edit');
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
