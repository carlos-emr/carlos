#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Configure Email's clinic footer settings (issue #3981, Ben's demo of 15 Oct 2026).
 *
 * User path: Schedule ▸ Administration ▸ Emails ▸ Configure Email (#myFrame) ▸ Clinic email footer
 * ▸ Edit footer (window) ▸ Apply, then the Clinic Email Logo form; My Email Footer
 * (/email/myEmailFooter) for the staff view.
 *
 * Asserted:
 *  - Footer text typed but not saved survives the logo form:
 *    - a picture over 100 KB is refused on the page, naming both limits, without a reload;
 *    - a picture the server refuses reloads the page with the text back in the editor, marked not
 *      saved, and nothing saved.
 *  - Required (the default) and Off:
 *    - Off saves with the text by the same button and is audited;
 *    - My Email Footer then says the clinic footer is off;
 *    - back to Required.
 *  - The logo emails carry, as the page loads it:
 *    - the CARLOS logo when the clinic has none of its own (skipped when one is uploaded);
 *    - "Add a logo to emails" off answers 404 and is audited, and on again serves it.
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
  const typed = `${marker} typed but not saved`;
  // The logo address as the pages load it, asked from here with the browser's own session: a 404 in
  // a page would fail the strict-page check, and Playwright's request context can leave out the
  // Secure session cookie over plain HTTP.
  async function logoAddress() {
    const cookies = await context.cookies(config.baseUrl);
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
  async function typeFooterOverSaved(page) {
    await page.locator('[data-footer-editor-target="clinicFooter"]').click();
    const editor = page.locator('#footerEditorText');
    await editor.waitFor({state: 'visible', timeout: TIMEOUT});
    await editor.fill(typed);
    await page.locator('#footerEditorApply').click();
    await page.locator('#footerEditorModal').waitFor({state: 'hidden', timeout: TIMEOUT});
    h.assert((await page.locator('#clinicFooter').inputValue()).includes(typed), 'Apply did not put the typed text in the form');
  }

  let page = await configureEmail();
  await s.step('Configure Email offers Required (chosen by default) or Off for the clinic footer', async () => {
    h.assert(await page.locator('#clinicFooterModeRequired').isChecked() !== rows.some(r => r.name === SETTINGS[1] && r.value === 'off'),
      'The page does not show the saved choice');
    h.assert(await page.locator('#clinicFooterModeOff').count() === 1, 'The page offers no Off');
  });

  await s.step('a picture over 100 KB is refused on the page, naming both limits, and the typed text stays', async () => {
    await typeFooterOverSaved(page);
    const url = page.url();
    await page.locator('#logoFile').setInputFiles({name: 'fake-big-logo.png', mimeType: 'image/png',
      buffer: Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(150 * 1024)])});
    await page.locator('#clinicLogoForm button[name="logoAction"][value="upload"]').click();
    const check = page.locator('#clinicLogoCheck');
    await check.waitFor({state: 'visible', timeout: TIMEOUT});
    const text = await check.innerText();
    h.assert(text.includes('151 KB') && text.includes('100 KB') && text.includes('600'), `The page said ${JSON.stringify(text)}`);
    h.assert(page.url() === url, 'The refused picture reloaded the page');
    h.assert((await page.locator('#clinicFooter').inputValue()).includes(typed), 'The typed text was lost');
  });

  await s.step('a picture the server refuses brings the page back with the typed text in the editor, not saved', async () => {
    await page.locator('#logoFile').setInputFiles({name: 'fake-broken-logo.png', mimeType: 'image/png',
      buffer: Buffer.from(`${marker} is not a picture`)});
    await page.locator('#clinicLogoForm button[name="logoAction"][value="upload"]').click();
    page = await frameAt('/admin/ViewConfigureEmail', 'logoError=NOT_AN_IMAGE');
    await page.locator('#clinicLogoError').waitFor({state: 'visible', timeout: TIMEOUT});
    await page.locator('#clinicFooterDraftKept').waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert((await page.locator('#clinicFooter').inputValue()).includes(typed), 'The typed text did not come back');
    h.assert((await page.locator('#clinicFooterPreview').innerText()).includes(typed), 'The preview does not show the typed text');
    await expectValue(sql, `SELECT value FROM property WHERE name=${q(SETTINGS[0])} AND ${CLINIC} ORDER BY id LIMIT 1`,
      savedFooter, 'The refused logo saved the footer text');
  });

  await s.step('Off saves with the text by the same button, is audited, and staff see the footer is off', async () => {
    await page.locator('#clinicFooterModeOff').check();
    await page.locator('#clinicFooterForm button[type="submit"]').click();
    page = await frameAt('/admin/ViewConfigureEmail', 'clinicFooterOutcome=saved');
    await page.locator('#clinicFooterOffNote').waitFor({state: 'visible', timeout: TIMEOUT});
    await expectValue(sql, `SELECT value FROM property WHERE name=${q(SETTINGS[1])} AND ${CLINIC}`, 'off',
      'Off was not saved');
    h.assert(sql.value(`SELECT value FROM property WHERE name=${q(SETTINGS[0])} AND ${CLINIC} ORDER BY id LIMIT 1`).includes(typed),
      'The typed text was not saved with it');
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE id > ${firstLog} AND provider_no=${q(s.provider)}
      AND content='emailFooterClinicMode' AND contentId='off'`, '1', 'Turning the footer off was not audited');
    const mine = await context.newPage();
    try {
      await mine.goto(h.appUrl(config.baseUrl, '/email/myEmailFooter'));
      await h.assertNotErrorPage(mine, 'My Email Footer');
      await mine.locator('#clinicFooterOffNote').waitFor({state: 'visible', timeout: TIMEOUT});
    } finally {
      await mine.close();
    }
  });

  await s.step('Required again: saved as a setting change and audited', async () => {
    await page.locator('#clinicFooterModeRequired').check();
    await page.locator('#clinicFooterForm button[type="submit"]').click();
    page = await frameAt('/admin/ViewConfigureEmail', 'clinicFooterOutcome=modeSaved');
    h.assert(await page.locator('#clinicFooterOffNote').count() === 0, 'The page still says the footer is off');
    await expectValue(sql, `SELECT value FROM property WHERE name=${q(SETTINGS[1])} AND ${CLINIC}`, 'required',
      'Required was not saved');
  });

  await s.step('emails carry the CARLOS logo until the clinic uploads its own', async () => {
    const own = sql.value('SELECT COUNT(*) FROM emailFooterLogo WHERE removedAt IS NULL');
    const response = await logoAddress();
    h.assert(response.status === 200, `The logo address answered ${response.status}`);
    h.assert(response.type.startsWith('image/'), 'The logo address did not send a picture');
    if (own === '0') {
      const sha = data => crypto.createHash('sha256').update(data).digest('hex');
      h.assert(sha(response.body) === sha(fs.readFileSync(DEFAULT_LOGO)), 'The clinic has no logo, but the page did not get the CARLOS logo');
      await page.locator('#clinicLogoDefault').waitFor({state: 'visible', timeout: TIMEOUT});
    } else {
      console.log('  NOTE email-footer-settings: the clinic has its own logo, so the CARLOS default was not compared');
    }
  });

  await s.step('"Add a logo to emails" off stops the logo, is audited, and on brings it back', async () => {
    await page.locator('#logoShown').uncheck();
    await page.locator('#clinicLogoForm button[name="logoAction"][value="switch"]').click();
    page = await frameAt('/admin/ViewConfigureEmail', 'logoSwitch=off');
    await page.locator('#clinicLogoHidden').waitFor({state: 'visible', timeout: TIMEOUT});
    const off = await logoAddress();
    h.assert(off.status === 404, `With the logo off, the logo address answered ${off.status}`);
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE id > ${firstLog} AND provider_no=${q(s.provider)}
      AND content='emailFooterLogoShown' AND contentId='false'`, '1', 'Turning the logo off was not audited');

    await page.locator('#logoShown').check();
    await page.locator('#clinicLogoForm button[name="logoAction"][value="switch"]').click();
    page = await frameAt('/admin/ViewConfigureEmail', 'logoSwitch=on');
    const on = await logoAddress();
    h.assert(on.status === 200 && on.type.startsWith('image/'), `With the logo on again, the logo address answered ${on.status}`);
  });
}

if (require.main === module) runWorkflow('email-footer-settings', workflow, {openPatient: false});
module.exports = {workflow};
