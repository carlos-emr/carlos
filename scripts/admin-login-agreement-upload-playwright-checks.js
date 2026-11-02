#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Upload Agreement (admin/uploadEntryText, uploadEntryText.jsp): how long a
 * user's acceptance of the acceptable-use agreement stays valid, and the agreement's text file.
 * No browser check opened the page.
 *
 * User path: Schedule ▸ Administration ▸ Upload Agreement ▸ validity ▸ agreement file ▸ Upload.
 * Asserted: the upload stores the validity as a new aua_valid_duration property ("<n> <period>")
 * and writes the uploaded bytes to BASE_DOCUMENT_DIR/login/AcceptableUseAgreement.txt, the one file
 * the login page reads (it used to land in an unread DOCUMENT_DIR/OSCARloginText.txt, #3792); the
 * page's preview shows the agreement's formatting but none of its script, event handlers or images,
 * and nothing in it runs; the page opened again shows the saved validity; a GET carrying the same
 * fields is refused with 405 and stores nothing.
 *
 * Env: BASE_DOCUMENT_DIR (the deployment's base document directory, writable by the runner so the
 * agreement can be put back); SKIP without it. SKIP too when the menu has no Upload Agreement entry
 * (it is shown only with LOGINTEST=yes).
 * Fixtures: none created. The aua_* property rows are snapshotted byte-exact and restored, and the
 * agreement file is put back as it was, with its owner and mode (or removed if there was none, with
 * the login/ directory the upload created). EXCLUSIVE=1: the agreement settings are clinic-wide.
 */
const { authenticatedProbe } = require('./lib/authenticated-probe');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openAdminPage, submitInFrame, reopenAdminPage } = require('./lib/admin-frame');

const ROUTE = 'admin/uploadEntryText';
const FILE_NAME = 'AcceptableUseAgreement.txt';

async function workflow(s) {
  const { sql, marker } = s;
  const base = process.env.BASE_DOCUMENT_DIR;
  if (!base || !fs.existsSync(base)) throw new h.SkipCheck('BASE_DOCUMENT_DIR is not set to the deployment\'s base document directory');
  const dir = path.join(fs.realpathSync(base), 'login');
  const file = path.join(dir, FILE_NAME);
  const dirBefore = fs.existsSync(dir);
  const statBefore = fs.existsSync(file) ? fs.lstatSync(file) : null;
  h.assert(!statBefore || statBefore.isFile(), `${FILE_NAME} is not a regular file; refusing to replace it`);
  const fileBefore = statBefore ? fs.readFileSync(file) : null;
  const rows = () => sql.rows("SELECT id, name, CONCAT('X', HEX(value)) FROM property WHERE name LIKE 'aua\\_%' ORDER BY id");
  const before = rows();
  const firstNew = sql.value('SELECT COALESCE(MAX(id), 0) FROM property');
  s.cleanup(() => {
    sql.execute(`DELETE FROM property WHERE name LIKE 'aua\\_%' AND id > ${firstNew}`);
    h.assert(JSON.stringify(rows()) === JSON.stringify(before), 'The aua_* properties were not restored');
    if (fileBefore === null) {
      fs.rmSync(file, { force: true });
      if (!dirBefore && fs.existsSync(dir) && fs.readdirSync(dir).length === 0) fs.rmdirSync(dir);
    } else {
      // The upload replaces the file (a new inode, the application's owner and a private mode),
      // so put back the original's owner and mode as well as its bytes.
      fs.writeFileSync(file, fileBefore);
      const now = fs.statSync(file);
      if (now.uid !== statBefore.uid || now.gid !== statBefore.gid) fs.chownSync(file, statBefore.uid, statBefore.gid);
      fs.chmodSync(file, statBefore.mode & 0o7777);
      h.assert(fs.readFileSync(file).equals(fileBefore), `${FILE_NAME} was not restored`);
    }
  });
  // Formatting the preview must keep, and script, an event handler and an image it must drop.
  // Nothing here may run: the flag stays unset in the page if the sanitizer holds.
  const text = `<p>FAKE acceptable-use agreement ${marker} <b>kept</b></p>`
    + '<script>window.__carlosAuaInjected = 1;</script>'
    + '<img src="data:," onerror="window.__carlosAuaInjected = 2">\n';
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
    const preview = frame.locator('#auaText');
    h.assert((await preview.innerText()).includes(marker), 'The preview does not show the uploaded agreement');
    h.assert(await preview.locator('b').count() === 1, 'The preview dropped the agreement\'s formatting');
    h.assert(await preview.locator('script, img, [onerror]').count() === 0, 'The preview kept script, an image or an event handler');
    h.assert(await frame.evaluate(() => window.__carlosAuaInjected === undefined), 'Script in the uploaded agreement ran in the preview');
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
    const response = await authenticatedProbe(s, h.appUrl(s.config.baseUrl, `/${ROUTE}?${params}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET save answered ${response.status()}, not 405`);
    h.assert(latest() === saved && sql.value("SELECT COUNT(*) FROM property WHERE name LIKE 'aua\\_%'") === count,
      'A refused GET stored a validity');
  });
}

if (require.main === module) runWorkflow('admin-login-agreement-upload', workflow, { openPatient: false });
module.exports = { workflow };
