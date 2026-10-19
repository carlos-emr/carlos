#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Upload Agreement (admin/uploadEntryText, uploadEntryText.jsp): how long a
 * user's acceptance of the acceptable-use agreement stays valid, and the agreement's text file.
 * No browser check opened the page.
 *
 * User path: Schedule ▸ Administration ▸ Upload Agreement ▸ validity ▸ agreement file ▸ Upload.
 * Asserted: the upload stores the validity as a new aua_valid_duration property ("<n> <period>")
 * and writes the uploaded text to DOCUMENT_DIR/OSCARloginText.txt; the page opened again shows the
 * saved validity; a GET carrying the same fields is refused with 405 and stores nothing.
 *
 * Env: DOCUMENT_DIR (the deployment's document directory, readable by the runner); SKIP without it.
 * SKIP too when the menu has no Upload Agreement entry (it is shown only with LOGINTEST=yes).
 * Fixtures: none created. The aua_* property rows are snapshotted byte-exact and restored, and the
 * text file is put back as it was (or removed if there was none). EXCLUSIVE=1: the agreement settings
 * are clinic-wide.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openAdminPage, submitInFrame, reopenAdminPage } = require('./lib/admin-frame');

const ROUTE = 'admin/uploadEntryText';
const FILE_NAME = 'OSCARloginText.txt';

async function workflow(s) {
  const { sql, marker } = s;
  const dir = process.env.DOCUMENT_DIR;
  if (!dir || !fs.existsSync(dir)) throw new h.SkipCheck('DOCUMENT_DIR is not set to the deployment\'s readable document directory');
  const file = path.join(dir, FILE_NAME);
  const fileBefore = fs.existsSync(file) ? fs.readFileSync(file) : null;
  const rows = () => sql.rows("SELECT id, name, CONCAT('X', HEX(value)) FROM property WHERE name LIKE 'aua\\_%' ORDER BY id");
  const before = rows();
  const firstNew = sql.value('SELECT COALESCE(MAX(id), 0) FROM property');
  s.cleanup(() => {
    sql.execute(`DELETE FROM property WHERE name LIKE 'aua\\_%' AND id > ${firstNew}`);
    h.assert(JSON.stringify(rows()) === JSON.stringify(before), 'The aua_* properties were not restored');
    if (fileBefore === null) fs.rmSync(file, { force: true });
    else fs.writeFileSync(file, fileBefore);
  });
  const text = `FAKE acceptable-use agreement ${marker}\n`;
  const latest = () => sql.value("SELECT value FROM property WHERE name='aua_valid_duration' ORDER BY id DESC LIMIT 1");

  let admin;
  let frame;
  await s.step('Upload stores the validity and writes the agreement text', async () => {
    ({ admin, frame } = await openAdminPage(s, ROUTE, { label: 'upload-agreement',
      skipReason: 'Administration shows Upload Agreement only with LOGINTEST=yes in the CARLOS properties' }));
    const form = frame.locator(`form[action$="/${ROUTE}"]`);
    await form.locator('select[name="validDurationNumber"]').selectOption('2');
    await form.locator('select[name="validDurationPeriod"]').selectOption('weeks');
    if (await form.locator('input[name="validForever"]').isChecked()) await form.locator('input[name="validForever"]').uncheck();
    await form.locator('input[name="importFile"]').setInputFiles({ name: 'fake-agreement.txt', mimeType: 'text/plain', buffer: Buffer.from(text) });
    ({ frame } = await submitInFrame(admin, form.locator('input[type="submit"]'), ROUTE));
    await h.assertNotErrorPage(frame, 'Upload Agreement after the upload');
    await expectValue(sql, "SELECT value FROM property WHERE name='aua_valid_duration' ORDER BY id DESC LIMIT 1", '2 weeks',
      'The upload did not store the validity');
    h.assert(fs.existsSync(file) && fs.readFileSync(file, 'utf8') === text, `${FILE_NAME} does not hold the uploaded text`);
  });

  await s.step('the page opened again shows the saved validity', async () => {
    // The left-nav link again, as a user reopens the page (the Administration page may be the day
    // sheet's own window, so the menu is not opened a second time).
    frame = await reopenAdminPage(admin, ROUTE);
    const form = frame.locator(`form[action$="/${ROUTE}"]`);
    h.assert(await form.locator('select[name="validDurationNumber"]').inputValue() === '2', 'The page does not show the saved number');
    h.assert(await form.locator('select[name="validDurationPeriod"]').inputValue() === 'weeks', 'The page does not show the saved period');
  });

  await s.step('a GET carrying the save is refused with 405 and stores nothing', async () => {
    const saved = latest();
    const count = sql.value("SELECT COUNT(*) FROM property WHERE name LIKE 'aua\\_%'");
    const params = new URLSearchParams({ validDurationNumber: '9', validDurationPeriod: 'days' });
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, `/${ROUTE}?${params}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET save answered ${response.status()}, not 405`);
    h.assert(latest() === saved && sql.value("SELECT COUNT(*) FROM property WHERE name LIKE 'aua\\_%'") === count,
      'A refused GET stored a validity');
  });
}

if (require.main === module) runWorkflow('admin-login-agreement-upload', workflow, { openPatient: false });
module.exports = { workflow };
