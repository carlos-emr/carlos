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
 * Browser CRUD check for schedule day templates (Schedule Setting > Template
 * Setting popup, /schedule/EditTemplate).
 *
 * schedule-setting-playwright-checks.js applies an EXISTING template through
 * the three-step wizard; it never creates one. This check covers the making of
 * a template, which alpha-11 testers reported working:
 *
 *   1. Schedule Setting page -> pick the provider in the "Template Setting"
 *      selector -> click the link, which opens the DAY TEMPLATE SETTING popup;
 *   2. fill name + summary, paint a block of 15-minute cells with a real
 *      template code, Save; assert the scheduletemplate row (provider, name,
 *      summary, a 96-character timecode with the code exactly where painted and
 *      '_' elsewhere);
 *   3. reopen the template through the picker's Edit button, assert the cells
 *      come back painted, change the summary and repaint one cell, Save,
 *      assert the row changed;
 *   4. Delete through the form button and assert the row is gone.
 *
 * Cleanup removes the row by (provider_no, name) in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: TEMPLATE_PROVIDER_NO (999998), TEMPLATE_CODE (1, must exist in
 *   scheduletemplatecode).
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
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const providerNo = process.env.TEMPLATE_PROVIDER_NO || '999998';
const templateCode = process.env.TEMPLATE_CODE || '1';
assert(/^\d+$/.test(providerNo), 'TEMPLATE_PROVIDER_NO must be numeric');
assert(/^[A-Za-z0-9]$/.test(templateCode), 'TEMPLATE_CODE must be a single template code character');

// scheduletemplate.name is varchar(20); "PWT" + 13-digit millis = 16 chars.
const templateName = `PWT${Date.now()}`;
const summaryCreated = 'PW created summary';
const summaryEdited = 'PW edited summary';
// 15-minute cells: index = hour*4 + quarter. Paint 09:00-11:45 (36..47) on create.
const paintedOnCreate = Array.from({ length: 12 }, (_, i) => 36 + i);
const paintedOnEdit = [48]; // 12:00

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'schedule-template-'));
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

function templateRow() {
  const out = sql(`SELECT summary, timecode FROM scheduletemplate WHERE provider_no='${escapeSql(providerNo)}' AND name='${escapeSql(templateName)}'`);
  if (!out) {
    return null;
  }
  const [summary, timecode] = out.split('\t');
  return { summary, timecode };
}
function cleanupRows() {
  sql(`DELETE FROM scheduletemplate WHERE provider_no='${escapeSql(providerNo)}' AND name='${escapeSql(templateName)}'`);
}
function expectedTimecode(paintedIndexes) {
  const cells = Array(96).fill('_');
  for (const index of paintedIndexes) {
    cells[index] = templateCode;
  }
  return cells.join('');
}

async function waitForTemplateForm(page) {
  await page.locator('form[name="addtemplatecode"] input[name="timecode95"]').waitFor({ state: 'attached', timeout: 30000 });
  assert((await page.locator('form[name="addtemplatecode"] input[name^="timecode"]').count()) === 96,
    'template editor did not render 96 fifteen-minute cells');
}

async function saveTemplate(page) {
  await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/schedule/EditTemplate'), { timeout: 30000 }),
    page.locator('form[name="addtemplatecode"] input[value="Save"]').click(),
  ]);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await assertNotErrorPage(page, 'template editor after save');
}

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  cleanupRows();
  assert(sql(`SELECT COUNT(*) FROM scheduletemplatecode WHERE code='${escapeSql(templateCode)}'`) === '1',
    `template code ${templateCode} is not defined in scheduletemplatecode`);
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    await login(context, config, recorder);

    // 1. Schedule Setting page -> Template Setting popup for the provider.
    const settingPage = await context.newPage();
    wirePage(settingPage, 'schedule-setting', recorder);
    await gotoApp(settingPage, config.baseUrl, '/schedule/TemplateSetting');
    await settingPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(settingPage, 'schedule setting');
    await settingPage.locator('select[name="providerid"]').selectOption(providerNo);
    const popupPromise = context.waitForEvent('page', { timeout: 30000 });
    await settingPage.locator('a[onclick*="go()"]').first().click();
    const editor = await popupPromise;
    wirePage(editor, 'template-editor', recorder);
    await editor.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(editor, 'template editor');
    assert(/\/schedule\/EditTemplate/.test(editor.url()), `Template Setting link opened ${editor.url()}`);
    assert((await editor.locator('form[name="addtemplatecode"] input[name="providerid"]').inputValue()) === providerNo,
      'template editor did not open for the selected provider');
    await waitForTemplateForm(editor);

    // 2. Create.
    const form = editor.locator('form[name="addtemplatecode"]');
    await form.locator('input[name="name"]').fill(templateName);
    await form.locator('input[name="summary"]').fill(summaryCreated);
    for (const index of paintedOnCreate) {
      await form.locator(`input[name="timecode${index}"]`).fill(templateCode);
    }
    await saveTemplate(editor);
    let row = templateRow();
    assert(row, 'template row was not created');
    assert(row.summary === summaryCreated, `created summary was "${row.summary}"`);
    assert(row.timecode === expectedTimecode(paintedOnCreate), `created timecode was ${row.timecode}`);
    assert((await editor.locator('select[name="name"] option').evaluateAll((options, name) => options.some((option) => option.value === name), templateName)),
      'saved template did not appear in the template picker');

    // 3. Edit through the picker.
    await editor.locator('select[name="name"]').selectOption(templateName);
    await Promise.all([
      editor.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/schedule/EditTemplate'), { timeout: 30000 }),
      editor.locator('input[value="Edit"]').first().click(),
    ]);
    await editor.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await waitForTemplateForm(editor);
    assert((await form.locator('input[name="name"]').inputValue()) === templateName, 'Edit did not load the template name');
    assert((await form.locator('input[name="summary"]').inputValue()) === summaryCreated, 'Edit did not load the summary');
    for (const index of paintedOnCreate) {
      assert((await form.locator(`input[name="timecode${index}"]`).inputValue()) === templateCode, `Edit did not repaint cell ${index}`);
    }
    // Unpainted cells are stored as '_' and come back as '_' (or blank).
    assert(/^_?$/.test((await form.locator('input[name="timecode0"]').inputValue()).trim()), 'Edit painted a cell that was left blank');
    await form.locator('input[name="summary"]').fill(summaryEdited);
    for (const index of paintedOnEdit) {
      await form.locator(`input[name="timecode${index}"]`).fill(templateCode);
    }
    await saveTemplate(editor);
    row = templateRow();
    assert(row, 'template row disappeared after edit');
    assert(row.summary === summaryEdited, `edited summary was "${row.summary}"`);
    assert(row.timecode === expectedTimecode([...paintedOnCreate, ...paintedOnEdit]), `edited timecode was ${row.timecode}`);
    assert(sql(`SELECT COUNT(*) FROM scheduletemplate WHERE provider_no='${escapeSql(providerNo)}' AND name='${escapeSql(templateName)}'`) === '1',
      'edit created a second template row instead of replacing the first');

    // 4. Delete.
    await editor.locator('select[name="name"]').selectOption(templateName);
    await Promise.all([
      editor.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/schedule/EditTemplate'), { timeout: 30000 }),
      editor.locator('input[value="Edit"]').first().click(),
    ]);
    await editor.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await waitForTemplateForm(editor);
    await Promise.all([
      editor.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/schedule/EditTemplate'), { timeout: 30000 }),
      form.locator('input[value="Delete"]').click(),
    ]);
    await editor.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(editor, 'template editor after delete');
    assert(templateRow() === null, 'Delete did not remove the template row');
    assert(!(await editor.locator('select[name="name"] option').evaluateAll((options, name) => options.some((option) => option.value === name), templateName)),
      'deleted template still appears in the template picker');

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS schedule template ${templateName} created, edited and deleted through the Template Setting UI`);
  } catch (error) {
    console.error(`FAIL schedule template CRUD check: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close().catch(() => {});
    try { cleanupRows(); } catch (cleanupError) { console.error(`cleanup failed: ${cleanupError.message}`); }
    cleanupMysqlDefaults();
  }
})();
