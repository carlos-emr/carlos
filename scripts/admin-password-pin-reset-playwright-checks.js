#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * admin-password-pin-reset — coverage plan §2.2 (an administrator resets another user's
 * credentials, and the user is made to choose their own password at the next login).
 *
 * User path: Schedule ▸ Administration ▸ User Management ▸ Search/Edit/Delete Security Records ▸
 * search the owned login ▸ open it ▸ new password and PIN, both PIN-lock flags, Force password
 * reset = true ▸ Update Record. Then, as that user: sign in with the administrator's password and
 * the new PIN ▸ the forced reset page ▸ choose a password of their own ▸ the schedule.
 *
 * Asserted: Update Record stores a new password hash and PIN and raises the reset flag; the first
 * sign-in stops at the forced reset page instead of the schedule; the reset stores the user's own
 * password and clears the flag; afterwards the administrator's password is refused, the old PIN
 * is refused, and the user's own password with the new PIN reaches the schedule directly.
 * (security-record-admin edits flags only and keeps both credentials; security-password-length
 * sets a password but never the reset flag or a PIN; login sets the flag by SQL on the shared
 * account; concurrency-security-record raises the flag but never signs in with it.)
 *
 * Refused sign-ins are kept to two. 127.0.0.1 is not on the default login_local_ip list, so each
 * one also counts against the client address (login_max_failed_times, 3 in the shipped
 * properties); the original password is proved replaced by its hash instead of a third refusal.
 * Each refusal must end on "Login failed" (lib/throwaway-login-fixture.js submitLoginForm), never a
 * lockout. Ends SKIP where legacy PINs are off (mfa.legacy.pin.enable): there is then no PIN to reset.
 *
 * Fixture: a throwaway login (lib/throwaway-login-fixture.js) copying the test login's hash, PIN
 * and roles; its cleanup removes the login, its provider, SecurityArchive, preference and log rows.
 */
const { randomBytes, randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture, submitLoginForm } = require('./lib/throwaway-login-fixture');
const { openSection, navigateFrame, searchByUserName } = require('./security-record-admin-playwright-checks');

const TIMEOUT = 20000;
const SIGNED_IN = /providercontrol|appointment/i;
const REFUSED = /login=failed|loginfailed|logout/i;

/** A password the shipped policy accepts: upper, lower, digit and symbol, well under 32. */
const policyPassword = () => `Ab1!${randomBytes(8).toString('hex')}`;

async function workflow(s) {
  const { sql, context, config, recorder } = s;
  const account = throwawayLoginFixture({ sql, marker: s.marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => account.cleanup());
  account.create();

  const adminPassword = policyPassword();
  const ownPassword = policyPassword();
  let newPin = String(randomInt(1000, 10000));
  while (newPin === config.testPin) newPin = String(randomInt(1000, 10000));
  const row = () => {
    const [values] = sql.rows(`SELECT password,COALESCE(pin,''),COALESCE(forcePasswordReset,'NULL'),b_LocalLockSet,b_RemoteLockSet,
      passwordUpdateDate >= NOW() - INTERVAL 5 MINUTE, pinUpdateDate >= NOW() - INTERVAL 5 MINUTE
      FROM security WHERE security_no=${account.securityNo}`);
    const [hash, pin, force, localLock, remoteLock, passwordFresh, pinFresh] = values;
    return { hash, pin, force, localLock, remoteLock, passwordFresh, pinFresh };
  };
  const before = row();
  h.assert(before.hash === account.passwordHash && before.force !== '1', 'The throwaway login did not start as a copy of the test login');

  /** One sign-in attempt in a fresh browser context; hands back where it landed. */
  async function signIn(label, password, pin) {
    const browserContext = await h.newContext(context.browser(), config);
    browserContext.setDefaultTimeout(TIMEOUT);
    browserContext.on('page', page => h.wireStrictPage(page, label, recorder));
    const page = await browserContext.newPage();
    await h.gotoApp(page, config.baseUrl, '/');
    await page.locator('#username').fill(account.username);
    await page.locator('#password').fill(password);
    const pinField = page.locator('#pin');
    const pinAsked = await pinField.count() > 0;
    if (pinAsked) await pinField.fill(pin);
    await Promise.all([
      page.waitForURL(url => SIGNED_IN.test(String(url)) || /forcepasswordreset/i.test(String(url)) || REFUSED.test(String(url))
        || /\/login$/.test(new URL(String(url)).pathname), { timeout: TIMEOUT }),
      page.locator('input[type="submit"], button[type="submit"]').first().click(),
    ]);
    await page.waitForLoadState('load', { timeout: TIMEOUT }).catch(() => {});
    return { browserContext, page, pinAsked };
  }
  /** A sign-in that must end on "Login failed" (not a lockout, not past the login page). */
  async function expectRefused(label, password, pin, why) {
    const browserContext = await h.newContext(context.browser(), config);
    browserContext.on('page', page => h.wireStrictPage(page, label, recorder));
    try {
      const { outcome, landing } = await submitLoginForm(browserContext, config, { username: account.username, password, pin });
      h.assert(outcome === 'failed', `${why}: the sign-in ended "${outcome}" at ${landing}, not "Login failed"`);
    } finally { await browserContext.close(); }
  }

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'security-admin', timeout: TIMEOUT });

  await s.step('Update Record stores a new password and PIN, PIN required, and raises Force password reset', async () => {
    const frame = await openSection(admin, 'input[name="keyword"]');
    const { rows } = await searchByUserName(admin, frame, account.username);
    h.assert(await rows.filter({ has: frame.getByRole('link', { name: account.username, exact: true }) }).count() === 1,
      'Search did not list exactly the owned login');
    await navigateFrame(admin, frame, frame.getByRole('link', { name: account.username, exact: true }));
    h.assert(new URL(frame.url()).searchParams.get('keyword') === String(account.securityNo), 'The edit page opened another login');
    await frame.locator('input[name="password"]').fill(adminPassword);
    await frame.locator('input[name="conPassword"]').fill(adminPassword);
    // The PIN rows render only while legacy PINs are on (MfaManager.isOscarLegacyPinEnabled, which the
    // login page's PIN box follows too); without them there is no PIN to reset.
    if (await frame.locator('input[name="pin"]').count() === 0) {
      throw new h.SkipCheck('Legacy PINs are off here (mfa.legacy.pin.enable), so Security Records offer no PIN to reset');
    }
    await frame.locator('input[name="pin"]').fill(newPin);
    await frame.locator('input[name="conPin"]').fill(newPin);
    await frame.locator('input[name="b_RemoteLockSet"]').check();
    await frame.locator('input[name="b_LocalLockSet"]').check();
    const force = frame.locator('select[name="forcePasswordReset"]');
    if (await force.count() === 0) {
      throw new h.SkipCheck('mandatory_password_reset is set to false here, so Security Records offer no Force password reset');
    }
    await force.selectOption({ value: '1' });
    await navigateFrame(admin, frame, frame.locator('input[name="subbutton"]'));
    h.assert(/Security Record Updated Successfully/.test(await frame.locator('body').innerText()), 'Update Record did not report success');
    const after = row();
    h.assert(after.hash !== before.hash && after.passwordFresh === '1', 'The administrator\'s password was not stored');
    h.assert(after.pin !== before.pin && after.pin !== '' && after.pinFresh === '1', 'The new PIN was not stored');
    h.assert(after.force === '1' && after.localLock === '1' && after.remoteLock === '1',
      'Update Record did not store Force password reset and both PIN-lock flags');
  });

  const adminHash = row().hash;
  await s.step('the first sign-in with the administrator\'s password stops at the forced reset page', async () => {
    const attempt = await signIn('forced-reset', adminPassword, newPin);
    try {
      h.assert(attempt.pinAsked, 'The login page asked for no PIN although both PIN-lock flags are set');
      h.assert(/forcepasswordreset/i.test(attempt.page.url()) && await attempt.page.locator('input[name="newPassword"]').count() === 1,
        `The sign-in did not stop at the forced reset page (${h.pathOnly(attempt.page.url())})`);
      h.assert(row().force === '1' && row().hash === adminHash, 'Reaching the reset page changed the login');

      // The page posts to forcepasswordresetSubmit; the user then lands on the schedule.
      await attempt.page.locator('input[name="oldPassword"]').fill(adminPassword);
      await attempt.page.locator('input[name="newPassword"]').fill(ownPassword);
      await attempt.page.locator('input[name="confirmPassword"]').fill(ownPassword);
      await Promise.all([
        attempt.page.waitForURL(SIGNED_IN, { timeout: TIMEOUT }),
        attempt.page.locator('input[type="submit"], button[type="submit"]').first().click(),
      ]);
      const after = row();
      h.assert(after.force === '0', 'The forced reset did not clear Force password reset');
      h.assert(after.hash !== adminHash && after.hash !== before.hash && after.passwordFresh === '1',
        'The forced reset did not store a password of the user\'s own');
    } finally { await attempt.browserContext.close(); }
  });

  await s.step('the administrator\'s password no longer signs in', async () => {
    await expectRefused('admin-password-refused', adminPassword, newPin, 'The administrator\'s password still signs in');
  });

  await s.step('the old PIN is refused', async () => {
    await expectRefused('old-pin-refused', ownPassword, config.testPin, 'The old PIN still signs in');
  });

  await s.step('the user\'s own password with the new PIN reaches the schedule directly', async () => {
    const attempt = await signIn('own-password', ownPassword, newPin);
    try {
      h.assert(SIGNED_IN.test(attempt.page.url()), `The user's own password and the new PIN did not reach the schedule (${h.pathOnly(attempt.page.url())})`);
      h.assert(row().force === '0', 'Signing in raised Force password reset again');
    } finally { await attempt.browserContext.close(); }
  });
}

if (require.main === module) runWorkflow('admin-password-pin-reset', workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
