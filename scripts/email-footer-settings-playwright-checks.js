#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Configure Email's clinic footer settings (issue #3981, Ben's demo of 15 Oct 2026).
 *
 * User path: Schedule ▸ Administration ▸ Emails ▸ Configure Email (#myFrame) ▸ Clinic email footer
 * ▸ Edit footer (window: text, Required/Off, Add a logo to emails) ▸ Save footer, then the Clinic
 * Email Logo form; My Email Footer (/email/myEmailFooter) for the staff view.
 *
 * Asserted:
 *  - One way to save: the card has no Save button of its own; the window's button is Save footer.
 *  - Save footer sends the text, Required/Off and "Add a logo to emails" in one POST:
 *    - each change is saved and audited;
 *    - with Off, My Email Footer says the clinic footer is off;
 *    - with the logo off, the logo address answers 404;
 *    - settings alone are saved as settings, and the logo serves again.
 *  - The logo emails carry is the CARLOS logo when the clinic has none of its own (skipped when one is
 *    uploaded).
 *  - A save over a footer changed meanwhile is refused: the current footer is in the editor, the
 *    typed text beside it, and the setting is unchanged.
 *  - Pictures: over 100 KB is refused on the page, naming both limits, without a reload; one the
 *    server refuses changes nothing.
 *
 * Fixtures: the clinic footer settings are clinic-wide, so the run snapshots every clinic row of
 * email_footer_clinic_default, email_footer_clinic_mode and email_footer_logo_shown and restores them
 * exactly (same ids and values) in cleanup. It saves a FAKE footer when none is saved. It never
 * uploads a logo: both pictures it offers are refused. Cleanup also removes the audit rows this run
 * wrote. EXCLUSIVE=1: other checks that send email read these settings while it runs.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 30000;
const SETTINGS = ['email_footer_clinic_default', 'email_footer_clinic_mode', 'email_footer_logo_shown'];
const CLINIC = '(provider_no IS NULL OR provider_no=\'\')';
const AUDITS = ['emailFooterClinicDefault', 'emailFooterClinicMode', 'emailFooterLogoShown'];
const DEFAULT_LOGO = path.join(__dirname, '..', 'src/main/resources/email/carlos-default-email-logo.png');

async function workflow(s) {
  const {sql, marker, context, config} = s;
  const q = h.sqlString;
  const names = SETTINGS.map(q).join(',');
  const rows = sql.rows(`SELECT id, name, value FROM property WHERE name IN (${names}) AND ${CLINIC} ORDER BY id`)
    .map(([id, name, value]) => ({id, name, value}));
  const firstLog = sql.value('SELECT COALESCE(MAX(id), 0) FROM log');
  s.cleanup(() => {
    sql.execute(`DELETE FROM property WHERE name IN (${names}) AND ${CLINIC}`);
    for (const row of rows) {
      sql.execute(`INSERT INTO property (id, name, value, provider_no) VALUES (${row.id}, ${q(row.name)}, ${q(row.value)}, NULL)`);
    }
    sql.execute(`DELETE FROM log WHERE id > ${firstLog} AND provider_no=${q(s.provider)} AND content IN (${AUDITS.map(q).join(',')})`);
    const now = sql.rows(`SELECT id, name, value FROM property WHERE name IN (${names}) AND ${CLINIC} ORDER BY id`)
      .map(([id, name, value]) => ({id, name, value}));
    h.assert(JSON.stringify(now) === JSON.stringify(rows), 'The clinic footer settings were not restored');
  });
  if (!rows.some(row => row.name === SETTINGS[0])) {
    sql.execute(`INSERT INTO property (name, value, provider_no) VALUES (${q(SETTINGS[0])}, ${q(`${marker} clinic footer`)}, NULL)`);
  }
  const savedFooter = sql.value(`SELECT value FROM property WHERE name=${q(SETTINGS[0])} AND ${CLINIC} ORDER BY id LIMIT 1`);
  const typed = `${marker} clinic footer typed`;
  // The logo address as the pages load it, asked from here with the browser's own session: a 404 in
  // a page would fail the strict-page check, and Playwright's request context can leave out the
  // Secure session cookie over plain HTTP.
  async function logoAddress() {
    const cookies = await context.cookies(String(config.baseUrl));
    const response = await fetch(h.appUrl(config.baseUrl, '/email/clinicEmailLogo'), {
      redirect: 'manual', headers: {cookie: cookies.map(c => `${c.name}=${c.value}`).join('; ')}});
    return {status: response.status, type: response.headers.get('content-type') || '',
      body: Buffer.from(await response.arrayBuffer())};
  }

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder: s.recorder, label: 'email-administration', timeout: TIMEOUT});
  async function configureEmail() {
    const link = admin.getByRole('link', {name: 'Configure Email', exact: true, includeHidden: true});
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    return frameAt('/admin/ViewConfigureEmail');
  }
  // The section's frame once it shows `route` (a reload or redirect inside the frame included).
  async function frameAt(route, query = '') {
    const deadline = Date.now() + TIMEOUT;
    let frame;
    while (!(frame = admin.frames().find(f => f.parentFrame() && f.url().includes(route) && f.url().includes(query)))) {
      h.assert(Date.now() < deadline, `The administration frame did not show ${route}${query}`);
      await admin.waitForTimeout(100);
    }
    await frame.waitForLoadState('domcontentloaded');
    return frame;
  }
  // The Administration page grows its frame to the frame's content, so the footer window inside
  // it is as tall as the frame and its lower controls can sit below the browser window. Playwright
  // cannot scroll a fixed window inside a frame into view, so scroll the page itself, as a user's
  // mouse wheel over the window does; measure again each time, since the window may still be
  // sliding in.
  async function reach(locator) {
    const height = admin.viewportSize().height;
    const deadline = Date.now() + TIMEOUT;
    for (;;) {
      const box = await locator.boundingBox();
      if (box && box.y >= 0 && box.y + box.height <= height) return locator;
      h.assert(Date.now() < deadline, 'Scrolling the page did not bring the footer window\'s control into view');
      if (box) await admin.evaluate(dy => window.scrollBy(0, dy), Math.round(box.y + box.height / 2 - height / 2));
      await admin.waitForTimeout(100);
    }
  }
  // Edit footer ▸ (type) ▸ choose ▸ Save footer: one POST of the text and both settings.
  async function editAndSave(page, {text, mode, logo, outcome}) {
    await page.locator('[data-footer-editor-target="clinicFooter"]').click();
    const editor = page.locator('#footerEditorText');
    await editor.waitFor({state: 'visible', timeout: TIMEOUT});
    if (text !== undefined) await editor.fill(text);
    if (mode) await (await reach(page.locator(mode === 'off' ? '#clinicFooterModeOff' : '#clinicFooterModeRequired'))).check();
    if (logo === true) await (await reach(page.locator('#logoShown'))).check();
    if (logo === false) await (await reach(page.locator('#logoShown'))).uncheck();
    const apply = await reach(page.locator('#footerEditorApply'));
    await Promise.all([
      admin.waitForRequest(request => request.method() === 'POST'
        && new URL(request.url()).pathname.endsWith('/admin/saveClinicEmailFooter'), {timeout: TIMEOUT}),
      apply.click()]);
    return frameAt('/admin/ViewConfigureEmail', `clinicFooterOutcome=${outcome}`);
  }
  // Audit rows are written on a separate thread (LogAction.addLog), so they are polled for.
  const audited = (content, value, message) => expectValue(sql, `SELECT COUNT(*) FROM log WHERE id > ${firstLog}
    AND provider_no=${q(s.provider)} AND content=${q(content)} AND COALESCE(contentId, '')=${q(value)}`, '1', message);
  const setting = name => sql.value(`SELECT value FROM property WHERE name=${q(name)} AND ${CLINIC} ORDER BY id LIMIT 1`);

  let page = await configureEmail();
  await s.step('the clinic footer card has one way to save: Edit footer, whose button is Save footer', async () => {
    h.assert(await page.locator('#clinicFooterForm button[type="submit"]').count() === 0, 'The card still has its own Save button');
    h.assert((await page.locator('#footerEditorApply').textContent()).trim() === 'Save footer', 'The window\'s button is not Save footer');
    h.assert((await page.locator('#clinicFooterModeShown').innerText()).includes(
      rows.some(r => r.name === SETTINGS[1] && r.value === 'off') ? 'Off:' : 'Required:'), 'The card does not show the saved choice');
  });

  await s.step('Save footer saves the text, Off and no logo in one POST, each audited, and staff see the footer is off', async () => {
    page = await editAndSave(page, {text: typed, mode: 'off', logo: false, outcome: 'saved'});
    await page.locator('#clinicFooterOffNote').waitFor({state: 'visible', timeout: TIMEOUT});
    await page.locator('#clinicLogoHidden').waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert(setting(SETTINGS[0]).includes(typed), 'The typed text was not saved');
    await expectValue(sql, `SELECT value FROM property WHERE name=${q(SETTINGS[1])} AND ${CLINIC}`, 'off', 'Off was not saved');
    h.assert(setting(SETTINGS[2]) === 'false', 'No logo was not saved');
    await audited('emailFooterClinicDefault', '', 'The footer text change was not audited');
    await audited('emailFooterClinicMode', 'off', 'Off was not audited');
    await audited('emailFooterLogoShown', 'false', 'No logo was not audited');
    const off = await logoAddress();
    h.assert(off.status === 404, `With the logo off, the logo address answered ${off.status}`);
    const mine = await context.newPage();
    try {
      await mine.goto(h.appUrl(config.baseUrl, '/email/myEmailFooter'));
      await h.assertNotErrorPage(mine, 'My Email Footer');
      await mine.locator('#clinicFooterOffNote').waitFor({state: 'visible', timeout: TIMEOUT});
    } finally {
      await mine.close();
    }
  });

  await s.step('Save footer with only the settings changed saves them as settings: Required and the logo again', async () => {
    page = await editAndSave(page, {mode: 'required', logo: true, outcome: 'settingsSaved'});
    h.assert(await page.locator('#clinicFooterOffNote').count() === 0, 'The page still says the footer is off');
    await expectValue(sql, `SELECT value FROM property WHERE name=${q(SETTINGS[1])} AND ${CLINIC}`, 'required', 'Required was not saved');
    h.assert(setting(SETTINGS[2]) === 'true', 'The logo was not turned back on');
    const on = await logoAddress();
    h.assert(on.status === 200 && on.type.startsWith('image/'), `With the logo on again, the logo address answered ${on.status}`);
  });

  await s.step('emails carry the CARLOS logo until the clinic uploads its own', async () => {
    const own = sql.value('SELECT COUNT(*) FROM emailFooterLogo WHERE removedAt IS NULL');
    if (own === '0') {
      const sha = data => crypto.createHash('sha256').update(data).digest('hex');
      h.assert(sha((await logoAddress()).body) === sha(fs.readFileSync(DEFAULT_LOGO)), 'The clinic has no logo, but the page did not get the CARLOS logo');
      await page.locator('#clinicLogoDefault').waitFor({state: 'visible', timeout: TIMEOUT});
    } else {
      console.log('  NOTE email-footer-settings: the clinic has its own logo, so the CARLOS default was not compared');
    }
  });

  await s.step('a save over a footer changed meanwhile is refused, the typed text shown beside the current one', async () => {
    // Another administrator saves a different footer while this page is open.
    const newer = `${marker} saved meanwhile`;
    sql.execute(`UPDATE property SET value=${q(newer)} WHERE name=${q(SETTINGS[0])} AND ${CLINIC}`);
    const stale = `${marker} typed over an older footer`;
    page = await editAndSave(page, {text: stale, mode: 'off', outcome: 'stale'});
    h.assert((await page.locator('#clinicFooterAttempted').innerText()).includes(stale), 'The typed text is not shown');
    h.assert((await page.locator('#clinicFooter').inputValue()).includes(newer), 'The editor does not show the current footer');
    await expectValue(sql, `SELECT value FROM property WHERE name=${q(SETTINGS[1])} AND ${CLINIC}`, 'required',
      'A stale save changed the setting');
  });

  await s.step('a picture over 100 KB is refused on the page, naming both limits, without a reload', async () => {
    const url = page.url();
    await page.locator('#logoFile').setInputFiles({name: 'fake-big-logo.png', mimeType: 'image/png',
      buffer: Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(150 * 1024)])});
    await page.locator('#clinicLogoForm button[name="logoAction"][value="upload"]').click();
    const check = page.locator('#clinicLogoCheck');
    await check.waitFor({state: 'visible', timeout: TIMEOUT});
    const text = await check.innerText();
    h.assert(text.includes('151 KB') && text.includes('100 KB') && text.includes('600'), `The page said ${JSON.stringify(text)}`);
    h.assert(page.url() === url, 'The refused picture reloaded the page');
  });

  await s.step('a picture the server refuses changes nothing', async () => {
    const before = setting(SETTINGS[0]);
    const logos = sql.value('SELECT COUNT(*) FROM emailFooterLogo');
    await page.locator('#logoFile').setInputFiles({name: 'fake-broken-logo.png', mimeType: 'image/png',
      buffer: Buffer.from(`${marker} is not a picture`)});
    await page.locator('#clinicLogoForm button[name="logoAction"][value="upload"]').click();
    page = await frameAt('/admin/ViewConfigureEmail', 'logoError=NOT_AN_IMAGE');
    await page.locator('#clinicLogoError').waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert(setting(SETTINGS[0]) === before, 'The refused logo changed the footer');
    h.assert(sql.value('SELECT COUNT(*) FROM emailFooterLogo') === logos, 'The refused picture was stored');
  });
}

if (require.main === module) runWorkflow('email-footer-settings', workflow, {openPatient: false});
module.exports = {workflow};
