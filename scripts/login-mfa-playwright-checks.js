#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * MFA enrolment and challenge for a throwaway login: coverage plan §2.2 login-mfa.
 *
 * User path: Schedule ▸ Administration ▸ User Management ▸ Search/Edit/Delete Security
 * Records ▸ the throwaway's record ▸ Enable MFA ▸ Update Record; then the login page
 * (password + PIN) ▸ the enrolment QR ▸ one-time code (mfa/loginMfa); the record's
 * Reset MFA link (securityRecord/mfa); finally clearing Enable MFA.
 *
 * Asserted: enabling writes security.usingMfa with no secret; sign-in then serves the
 * enrolment QR (decoded here, RFC 6238 codes computed in node) without a session; a
 * wrong code is refused (no session, no secret, audited) and the right one lands on
 * the schedule and stores the secret encrypted; an enrolled login is challenged
 * without a QR, refuses a wrong code and accepts the authenticator code through
 * h.login; Reset MFA refuses GET, then clears the secret and sign-in asks to enrol
 * again; clearing Enable MFA restores plain password-and-PIN login.
 *
 * Fixtures: one throwaway login (scripts/lib/throwaway-login-fixture.js) -- never the
 * shared test account -- whose rows, including its audit rows, cleanup removes.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { bundleMessage, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { decodeQrPng, parseOtpauthUrl, totp, wrongTotp } = require('./lib/mfa-otp');
const { openSection, navigateFrame, searchByUserName } = require('./security-record-admin-playwright-checks');
const { settleOperations } = require('./graceful-signal-cancellation');

const TIMEOUT = 20000;
const QR = 'img[src^="data:image/png"]';

async function workflow(s) {
  const { sql, context, config, recorder } = s;
  const login = throwawayLoginFixture({ sql, marker: s.marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => login.cleanup());
  login.create();
  const securityNo = login.securityNo;
  const credentials = { ...config, testUser: login.username };
  // mysql -B prints SQL NULL as NULL, which the runner reads back as null: spell it out.
  const mfaStateQuery = `SELECT CONCAT(usingMfa,'|',IF(mfaSecret IS NULL,'none',mfaSecret)) FROM security WHERE security_no=${securityNo}`;
  const mfaState = () => sql.value(mfaStateQuery);
  const audits = content => sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${h.sqlString(login.providerNo)}
    AND action='login' AND content=${h.sqlString(content)}`);
  const contexts = [];
  s.cleanup(async () => { for (const ctx of contexts) await ctx.close().catch(() => {}); });

  async function freshContext(label) {
    const ctx = await h.newContext(context.browser(), config);
    contexts.push(ctx);
    ctx.on('page', p => h.wireStrictPage(p, label, recorder));
    return ctx;
  }
  // The heartbeat answers whether a request's session carries an authenticated user.
  async function sessionValid(ctx) {
    const response = await ctx.request.get(h.appUrl(config.baseUrl, '/status/SessionHeartbeat'));
    h.assert(response.status() === 200, `The session heartbeat answered HTTP ${response.status()}`);
    return (await response.json()).valid;
  }
  async function scheduleRefused(ctx) {
    const response = await ctx.request.get(h.appUrl(config.baseUrl, '/provider/providercontrol'), { maxRedirects: 0 });
    return response.status() === 302 && /\/logoutPage$/.test(new URL(response.headers().location, config.baseUrl).pathname);
  }
  // Password + PIN on the login form, as a person types them; returns the landing page.
  async function submitPassword(ctx) {
    const page = await ctx.newPage();
    await h.gotoApp(page, config.baseUrl, '/');
    await page.locator('#username').fill(login.username);
    await page.locator('#password').fill(config.testPassword);
    await page.locator('#pin').fill(config.testPin);
    await settleOperations([
      page.waitForURL(url => /providercontrol|loginfailed|login=failed/i.test(String(url)) || /\/login$/.test(new URL(String(url)).pathname),
        { timeout: TIMEOUT }),
      page.locator('input[type="submit"], button[type="submit"]').first().click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    return page;
  }
  // mfa_otp_handler.jsp submits its own form once six digits are typed.
  async function typeCode(page, code, landing) {
    await settleOperations([page.waitForURL(landing, { timeout: TIMEOUT }), page.locator('#otpInput').fill(code)]);
    await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  }
  async function expectRefusedCode(page, ctx) {
    h.assert(h.pathOnly(page.url()).endsWith('/mfa/loginMfa'), `A wrong code landed on ${h.pathOnly(page.url())}`);
    await page.locator('#otpInput.is-invalid').waitFor({ timeout: TIMEOUT });
    h.assert((await page.locator('#otpInputFeedback').innerText()).trim() === 'Invalid MFA Code', 'The refusal did not say the code was invalid');
    h.assert(await sessionValid(ctx) === false && await scheduleRefused(ctx), 'A wrong code produced an authenticated session');
  }

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'mfa-admin', timeout: TIMEOUT });
  // KNOWN DEFECT, ASSERTED LAST: securityupdatesecurity.jsp links a relative
  // bcArStyle.css that resolves to the missing admin/bcArStyle.css (an HTML answer the
  // browser refuses as a stylesheet; see the security-record-admin manifest note). The
  // entries are set aside here so every MFA step is still proven, and the final step
  // fails on them until the page is fixed.
  const stylesheetDefect = [];
  function setAsideStylesheetDefect() {
    const broken = entry => /\/admin\/bcArStyle\.css/.test(`${entry.url || ''} ${entry.text || ''}`);
    for (const list of [recorder.consoleIssues, recorder.requestFailures]) {
      for (let i = list.length - 1; i >= 0; i--) if (broken(list[i])) stylesheetDefect.push(...list.splice(i, 1));
    }
  }
  async function openRecord() {
    const frame = await openSection(admin, 'input[name="keyword"]');
    await searchByUserName(admin, frame, login.username);
    await navigateFrame(admin, frame, frame.getByRole('link', { name: login.username, exact: true }));
    h.assert(new URL(frame.url()).searchParams.get('keyword') === securityNo, 'The edit page opened a record other than the throwaway login');
    setAsideStylesheetDefect();
    return frame;
  }
  async function saveRecord(frame) {
    await navigateFrame(admin, frame, frame.locator('input[name="subbutton"]'));
    h.assert(/Security Record Updated Successfully/.test(await frame.locator('h2').first().innerText()), 'The security update did not report success');
  }

  await s.step('Enable MFA on the security record sets usingMfa and stores no secret yet', async () => {
    h.assert(mfaState() === '0|none', 'The throwaway login did not start without MFA');
    const frame = await openRecord();
    const enable = frame.locator('input[name="enableMfa"]');
    h.assert(!(await enable.isChecked()) && await frame.locator('#resetMfaLink').count() === 0, 'The record showed MFA state it does not have');
    await enable.check();
    h.assert(await frame.locator('#mfaNote').isVisible(), 'Ticking Enable MFA did not show the first-login enrolment note');
    const dbgq = `SELECT CONCAT(pin IS NULL,'|',b_LocalLockSet,'|',b_RemoteLockSet) FROM security WHERE security_no=${securityNo}`;
    console.log('DEBUG before', sql.value(dbgq));
    await saveRecord(frame);
    console.log('DEBUG after', sql.value(dbgq));
    h.assert(mfaState() === '1|none', 'Enabling MFA did not set usingMfa without a secret');
  });

  let secret;
  let enrolPage;
  let enrolContext;
  await s.step('password and PIN lead to the enrolment QR for this login, with no session yet', async () => {
    enrolContext = await freshContext('mfa-enrol');
    enrolPage = await submitPassword(enrolContext);
    h.assert(h.pathOnly(enrolPage.url()).endsWith('/login') && await enrolPage.locator('#otpInput').isVisible(),
      `Sign-in landed on ${h.pathOnly(enrolPage.url())} instead of the MFA challenge`);
    h.assert((await enrolPage.locator('body').innerText()).includes(bundleMessage('mfa.registration.title',
      'You are required to configure Multi-Factor authentication before you can continue.')), 'The enrolment instructions were not shown');
    const otpauth = parseOtpauthUrl(decodeQrPng(await enrolPage.locator(QR).getAttribute('src')));
    h.assert(otpauth.account === login.username && otpauth.issuer && otpauth.issuer === otpauth.labelIssuer,
      'The enrolment QR does not name this login and its issuer');
    secret = otpauth.secret;
    h.assert(await sessionValid(enrolContext) === false && await scheduleRefused(enrolContext),
      'Password and PIN alone produced an authenticated session for an MFA login');
    h.assert(mfaState() === '1|none', 'Showing the enrolment QR stored a secret');
  });

  await s.step('a wrong enrolment code is refused: same QR, no session, no secret, audited', async () => {
    await typeCode(enrolPage, wrongTotp(secret), /\/mfa\/loginMfa/);
    await expectRefusedCode(enrolPage, enrolContext);
    h.assert(parseOtpauthUrl(decodeQrPng(await enrolPage.locator(QR).getAttribute('src'))).secret === secret,
      'The retry did not keep the same enrolment secret');
    h.assert(mfaState() === '1|none', 'A refused enrolment code stored a secret');
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE provider_no=${h.sqlString(login.providerNo)} AND action='login' AND content='mfa_failed'`,
      '1', 'The refused code was not audited as mfa_failed');
  });

  await s.step('the authenticator code completes enrolment on the schedule and stores the secret encrypted', async () => {
    await typeCode(enrolPage, totp(secret), /providercontrol/);
    h.assert(await sessionValid(enrolContext) === true, 'Completing enrolment did not produce an authenticated session');
    const [usingMfa, stored] = mfaState().split('|');
    h.assert(usingMfa === '1' && stored.startsWith('{ENC}') && !stored.includes(secret), 'The enrolment secret was not stored, or not encrypted');
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE provider_no=${h.sqlString(login.providerNo)} AND action='login' AND content='mfa_success'`,
      '1', 'The completed enrolment was not audited as mfa_success');
    await enrolContext.close();
  });

  await s.step('an enrolled login is challenged without a QR and a wrong code leaves no session', async () => {
    const ctx = await freshContext('mfa-challenge');
    const page = await submitPassword(ctx);
    h.assert(await page.locator('#otpInput').isVisible() && await page.locator(QR).count() === 0,
      'An enrolled login was not challenged, or was shown an enrolment QR again');
    h.assert(await sessionValid(ctx) === false, 'The challenge page already had an authenticated session');
    await typeCode(page, wrongTotp(secret), /\/mfa\/loginMfa/);
    await expectRefusedCode(page, ctx);
    h.assert(audits('mfa_failed') === '2' && audits('mfa_success') === '1', 'The refused challenge was not audited exactly once');
    await ctx.close();
  });

  await s.step('h.login answers the challenge with the authenticator code and reaches the schedule', async () => {
    const ctx = await freshContext('mfa-login');
    const schedule = await h.login(ctx, credentials, recorder, { label: 'mfa-login', mfaCode: () => totp(secret) });
    h.assert(/providercontrol/.test(h.pathOnly(schedule.url())) && await sessionValid(ctx) === true,
      'The authenticator code did not reach the schedule');
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE provider_no=${h.sqlString(login.providerNo)} AND action='login' AND content='mfa_success'`,
      '2', 'The second MFA sign-in was not audited as mfa_success');
    await ctx.close();
  });

  await s.step('Reset MFA refuses GET, then clears the stored secret from the record', async () => {
    const enrolled = mfaState();
    const probe = await context.request.get(h.appUrl(config.baseUrl, '/securityRecord/mfa'),
      { params: { method: 'resetMfa', securityId: securityNo }, maxRedirects: 0 });
    h.assert(probe.status() === 405 && mfaState() === enrolled, `Reset MFA answered GET with HTTP ${probe.status()} or changed the record`);
    const frame = await openRecord();
    const reset = frame.locator('#resetMfaLink');
    h.assert(await reset.isVisible() && await frame.locator('input[name="enableMfa"]').isChecked(),
      'An enrolled record did not offer Reset MFA');
    let response;
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      [response] = await settleOperations([
        admin.waitForResponse(r => r.url().includes('/securityRecord/mfa') && r.request().method() === 'POST', { timeout: TIMEOUT }),
        reset.click(),
      ]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === bundleMessage('admin.securityAddRecord.mfa.reset.confirm',
      'User will need to re-register for MFA. Are you sure you want to reset MFA settings for the user?'), 'Reset MFA did not ask for confirmation');
    h.assert(response.status() === 200, `Reset MFA answered HTTP ${response.status()}`);
    await frame.locator('#mfaNote').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(!(await reset.isVisible()), 'The Reset MFA link stayed visible after the reset');
    await expectValue(sql, mfaStateQuery, '1|none', 'Reset MFA did not clear the stored secret');
  });

  await s.step('after the reset, sign-in asks to enrol again with a new secret', async () => {
    const ctx = await freshContext('mfa-reenrol');
    const page = await submitPassword(ctx);
    h.assert(await page.locator('#otpInput').isVisible() && await page.locator(QR).count() === 1, 'Sign-in after the reset did not ask to enrol');
    const otpauth = parseOtpauthUrl(decodeQrPng(await page.locator(QR).getAttribute('src')));
    h.assert(otpauth.account === login.username && otpauth.secret !== secret, 'The re-enrolment QR reused the reset secret');
    h.assert(await sessionValid(ctx) === false, 'The re-enrolment page had an authenticated session');
    await ctx.close();
  });

  await s.step('clearing Enable MFA restores plain password-and-PIN login', async () => {
    const frame = await openRecord();
    await frame.locator('input[name="enableMfa"]').uncheck();
    await saveRecord(frame);
    h.assert(mfaState().startsWith('0|'), 'Clearing Enable MFA did not clear usingMfa');
    const ctx = await freshContext('mfa-disabled-login');
    // h.login fails if a challenge is served without an mfaCode callback.
    const schedule = await h.login(ctx, credentials, recorder, { label: 'mfa-disabled-login' });
    h.assert(/providercontrol/.test(h.pathOnly(schedule.url())) && await sessionValid(ctx) === true,
      'The login with MFA cleared did not reach the schedule');
    h.assert(audits('mfa_success') === '2', 'A sign-in without MFA was audited as an MFA success');
    await ctx.close();
  });

  await s.step('the security record edit page loads its stylesheet', async () => {
    setAsideStylesheetDefect();
    h.assert(stylesheetDefect.length === 0, `The security record edit page requested the missing admin/bcArStyle.css `
      + `(${stylesheetDefect.length} console/network report(s)): the relative link in securityupdatesecurity.jsp is broken`);
  });
}

if (require.main === module) runWorkflow('login-mfa', workflow, { openPatient: false });
module.exports = { workflow };
