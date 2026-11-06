#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The acceptable-use agreement on the login page (#3790).
 *
 * WHY. Administration ▸ Upload Agreement publishes BASE_DOCUMENT_DIR/login/AcceptableUseAgreement.txt,
 * and the login page writes that file as markup before anyone signs in. That makes the file a path
 * from an _admin account to every user's credentials, so the page must write a sanitized form, and
 * only while show_aua enables the agreement. The upload checks (admin-login-agreement-upload,
 * admin-login-text) see the Administration preview; this is the only check that loads the login page
 * itself, in a session-less browser, the way every user meets it.
 *
 * Asserted. The check writes a synthetic agreement carrying a heading, emphasis, script, an image with
 * an event handler and a style attribute, then opens the login page with no session:
 *  - LOGIN_AGREEMENT_EXPECT=shown (show_aua=true or always): the page shows the agreement's text,
 *    heading and emphasis, without the script, image, handler or style; nothing in it ran; the
 *    Show/Hide link reveals it; the file was picked up without a restart.
 *  - LOGIN_AGREEMENT_EXPECT=hidden (show_aua unset or off, the packaged default): the page carries no
 *    agreement block and none of the file's text.
 * show_aua is read once at startup, so the check cannot switch it: the caller names the deployment's
 * setting, and runs it once per setting to cover both.
 *
 * Env: BASE_DOCUMENT_DIR (the deployment's base document directory, writable by the runner, so it
 * runs on the application host) and LOGIN_AGREEMENT_EXPECT=shown|hidden. SKIP without either.
 * Fixtures: the agreement file only, no rows. An agreement the deployment already has is put back
 * byte-exact with its owner and mode; otherwise the file, and a login/ directory the check created,
 * are removed. EXCLUSIVE=1: the login page is clinic-wide.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');

const FILE_NAME = 'AcceptableUseAgreement.txt';
const EXPECTATIONS = ['shown', 'hidden'];

// Set once the fixture is written, so cleanup puts back exactly what the run changed.
let restore = null;

function fixture(marker) {
  return `<h2>${marker} agreement</h2><p>Use this record <b>only</b> for patient care.</p>`
    + '<script>window.__carlosAuaInjected = 1;</script>'
    + '<img src="data:," onerror="window.__carlosAuaInjected = 2">'
    + '<div style="position:fixed;inset:0;background:#fff">overlay</div>\n';
}

function installFixture(base, marker) {
  const dir = path.join(fs.realpathSync(base), 'login');
  const file = path.join(dir, FILE_NAME);
  const dirBefore = fs.existsSync(dir);
  const statBefore = fs.existsSync(file) ? fs.lstatSync(file) : null;
  h.assert(!statBefore || statBefore.isFile(), `${FILE_NAME} is not a regular file; refusing to replace it`);
  const bytesBefore = statBefore ? fs.readFileSync(file) : null;
  const written = Buffer.from(fixture(marker), 'utf8');
  const putBack = () => {
    const current = fs.existsSync(file) ? fs.readFileSync(file) : null;
    // Anything but our own bytes (or the original) means someone else changed it meanwhile:
    // leave it for a person rather than overwrite their change.
    h.assert(current === null || current.equals(written) || (bytesBefore && current.equals(bytesBefore)),
      `${FILE_NAME} changed outside this check; left in place for recovery`);
    if (bytesBefore) {
      fs.writeFileSync(file, bytesBefore);
      const now = fs.statSync(file);
      if (now.uid !== statBefore.uid || now.gid !== statBefore.gid) fs.chownSync(file, statBefore.uid, statBefore.gid);
      fs.chmodSync(file, statBefore.mode & 0o7777);
      h.assert(fs.readFileSync(file).equals(bytesBefore), `${FILE_NAME} was not restored`);
    } else {
      fs.rmSync(file, { force: true });
      if (!dirBefore && fs.existsSync(dir) && fs.readdirSync(dir).length === 0) fs.rmdirSync(dir);
    }
  };
  if (!dirBefore) fs.mkdirSync(dir, { mode: 0o755 });
  // In place when the file exists, so the application's owner and mode survive the run.
  fs.writeFileSync(file, written, { mode: 0o644 });
  return putBack;
}

async function main() {
  const config = h.readConfig();
  const expectation = process.env.LOGIN_AGREEMENT_EXPECT;
  if (!EXPECTATIONS.includes(expectation)) {
    throw new h.SkipCheck('Set LOGIN_AGREEMENT_EXPECT=shown (show_aua=true or always) or hidden (show_aua off) '
      + 'to match the deployment');
  }
  const base = process.env.BASE_DOCUMENT_DIR;
  if (!base || !fs.existsSync(base)) {
    throw new h.SkipCheck('BASE_DOCUMENT_DIR is not set to the deployment\'s base document directory');
  }
  const marker = `FAKE-PW-AUA-${Date.now()}`;
  restore = installFixture(base, marker);

  const recorder = h.createRecorder();
  const browser = await h.launchBrowser(config, { logoutOnClose: false });
  try {
    // No session: the login page as every user first meets it.
    const context = await browser.newContext({ ignoreHTTPSErrors: config.ignoreHTTPSErrors === true });
    const page = await context.newPage();
    h.wireStrictPage(page, 'login', recorder);
    await h.gotoApp(page, config.baseUrl, '/');
    await page.waitForLoadState('load', { timeout: 30000 });
    await page.locator('#username').waitFor({ timeout: 30000 });
    const agreement = page.locator('#auaText');

    if (expectation === 'hidden') {
      h.assert(await agreement.count() === 0, 'The login page carries the agreement block although show_aua is off');
      h.assert(!(await page.content()).includes(marker), 'The login page carries the agreement text although show_aua is off');
      console.log('  show_aua off: the login page carries no agreement block and none of its text');
    } else {
      h.assert(await agreement.count() === 1, 'The login page shows no agreement although show_aua enables it');
      const body = agreement.locator('.card-body');
      h.assert((await body.textContent()).includes(`${marker} agreement`),
        'The login page does not show the agreement just written (a stale cached copy?)');
      h.assert(await body.locator('h2').count() === 1 && await body.locator('b').count() === 1,
        'The login page dropped the agreement\'s heading or emphasis');
      h.assert(await body.locator('script, img, iframe, [onerror], [style]').count() === 0,
        'The login page kept script, an image, an event handler or a style attribute from the agreement');
      h.assert(await page.evaluate(() => window.__carlosAuaInjected === undefined),
        'Script in the agreement ran on the login page');
      if (!await agreement.isVisible()) {
        // show_aua=true: the agreement starts hidden behind the Show/Hide link.
        await page.locator('a[onclick*="showHideItem(\'auaText\')"]').click();
      }
      await agreement.waitFor({ state: 'visible', timeout: 10000 });
      console.log('  show_aua on: the login page shows the agreement sanitized, and nothing in it ran');
    }
    h.assertStrictPage(recorder, ['login']);
    await context.close();
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  h.runCheck({ name: 'login-agreement-display', run: main, cleanup: () => (restore ? restore() : undefined) });
}

module.exports = { fixture, installFixture };
