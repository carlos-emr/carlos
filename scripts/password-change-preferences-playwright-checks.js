#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Password change from Preferences (coverage plan §2.2 password-change-preferences).
 *
 * User path: Schedule ▸ personal settings icon (Preferences popup) ▸ Account & Advanced ▸
 * Change Password (provider/ViewProviderChangePassword) ▸ Update (POST provider/ViewProviderUpdatePassword).
 *
 * Driven as a THROWAWAY login (lib/throwaway-login-fixture.js) in its own browser context, never
 * the shared test account. Asserts, against the UI and the `security` table:
 *   1. a wrong current password is refused, the form reports it, the hash is untouched;
 *   2. a policy-violating new password is stopped by the form's own check (alert, no request);
 *   3. a compliant new password is saved: the popup self-closes, the hash changes, the old hash is
 *      archived in SecurityArchive, passwordUpdateDate moves, the audit `log` row is written;
 *   4. the old password no longer logs in (audited as a failed login) and the new one reaches the schedule;
 *   5. negative probes, after the UI path: GET on the update route answers 405, and a policy-violating
 *      password POSTed without the browser check must not replace the stored hash.
 * Fixtures: the throwaway provider/security/secUserRole rows; cleanup deletes every owned row
 * (including SecurityArchive, provider_facility and audit rows) and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopup } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const { bundleMessage, submitLoginForm, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

// Too short for password_min_length=8 and one character group short of password_min_groups=3.
const WEAK_PASSWORD = 'ab1';
const UPDATE_ROUTE = /\/provider\/ViewProviderUpdatePassword$/;

// The success page carries self.close() in its <head>, so the window closes while the
// CSRFGuard script tag injected after it is still loading: Chromium reports that one
// request as net::ERR_ABORTED. Consume exactly that entry, only after the close was
// observed; any other failure on the page stays strict.
function consumeSelfCloseAbort(recorder, label, since) {
  const added = recorder.requestFailures.slice(since);
  const index = added.findIndex(entry => entry.label === label && entry.resourceType === 'script'
    && entry.errorText === 'net::ERR_ABORTED' && /\/csrfguard$/.test(new URL(entry.url).pathname));
  if (index >= 0) recorder.requestFailures.splice(since + index, 1);
}

async function workflow(s) {
  const { sql, config, recorder } = s;
  const fixture = throwawayLoginFixture({ sql, marker: s.marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const { username } = fixture;
  const user = h.sqlString(username);
  const originalHash = fixture.passwordHash;
  // Upper, lower, digit and special: four groups, well over the minimum length, never the old one.
  const newPassword = `Fake-Pw1${s.marker.slice(-8)}`;
  const hashQuery = `SELECT password FROM security WHERE user_name=${user}`;
  const archiveCount = `SELECT COUNT(*) FROM SecurityArchive WHERE user_name=${user}`;
  const updatePosts = () => recorder.requestLog.filter(entry => entry.method === 'POST'
    && UPDATE_ROUTE.test(new URL(entry.url).pathname)).length;

  const throwaway = await h.newContext(s.context.browser(), config);
  throwaway.on('page', page => h.wireStrictPage(page, 'throwaway-session', recorder));
  const schedule = await h.login(throwaway, { ...config, testUser: username }, recorder, { label: 'throwaway-login' });
  const prefs = await clickOpensPopup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
    { context: throwaway, recorder, label: 'throwaway-preferences', timeout: 20000 });

  async function openChangePassword(label, from = prefs) {
    const link = from.locator('a[href$="/provider/ViewProviderChangePassword"]');
    await revealAuditLink(from, link, 20000);
    const popup = await clickOpensPopup(from, link, { context: from.context(), recorder, label, timeout: 20000 });
    await popup.locator('form[name="updatepassword"] input[name="oldpassword"]').waitFor({ state: 'visible' });
    return popup;
  }
  async function fillPasswords(popup, oldPassword, password) {
    await popup.locator('input[name="oldpassword"]').fill(oldPassword);
    await popup.locator('input[name="mypassword"]').fill(password);
    await popup.locator('input[name="confirmpassword"]').fill(password);
  }

  await s.step('a wrong current password is refused on the form and the stored hash is unchanged', async () => {
    const popup = await openChangePassword('change-password-wrong-current');
    await fillPasswords(popup, `${config.testPassword}-WRONG`, newPassword);
    await clickAndAwaitReload(popup, popup.locator('input[type="submit"]'), { label: 'Update with a wrong current password' });
    h.assert(/\/provider\/ViewProviderChangePassword/.test(popup.url()), 'The refusal did not return to the change-password form');
    const text = (await popup.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes("Password doesn't match the existing one in the system"), 'The wrong current password was not reported');
    h.assert(sql.value(hashQuery) === originalHash, 'A wrong current password changed the stored hash');
    h.assert(sql.value(archiveCount) === '0', 'A refused change archived the security record');
    await popup.close();
  });

  await s.step('a policy-violating new password is stopped by the form before any request is sent', async () => {
    const popup = await openChangePassword('change-password-weak');
    const before = updatePosts();
    await fillPasswords(popup, config.testPassword, WEAK_PASSWORD);
    const lengthMessage = bundleMessage('password.policy.violation.msgPasswordLengthError', 'Password is too short, minimum length is');
    const dialogs = await h.withExpectedDialogs(popup, () => popup.locator('input[type="submit"]').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text.includes(lengthMessage),
      `Expected one password-length alert, saw ${dialogs.length} dialog(s)`);
    await popup.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    h.assert(/\/provider\/ViewProviderChangePassword/.test(popup.url()) && updatePosts() === before,
      'The form submitted a password its own check rejected');
    h.assert(sql.value(hashQuery) === originalHash, 'A browser-rejected password reached the security row');
    await popup.close();
  });

  await s.step('a compliant new password is saved: hash replaced, old hash archived, audit row written', async () => {
    const popup = await openChangePassword('change-password-good');
    await fillPasswords(popup, config.testPassword, newPassword);
    // The success page carries self.close(): the popup closes instead of navigating.
    const closed = popup.waitForEvent('close', { timeout: 20000 });
    const failuresBefore = recorder.requestFailures.length;
    await popup.locator('input[type="submit"]').click({ noWaitAfter: true });
    await closed;
    consumeSelfCloseAbort(recorder, 'change-password-good', failuresBefore);
    await expectValue(sql, `SELECT password<>${h.sqlString(originalHash)} FROM security WHERE user_name=${user}`, '1',
      'The password change did not reach the security row');
    const newHash = sql.value(hashQuery);
    h.assert(newHash !== originalHash && !newHash.includes(newPassword), 'The new password was stored in clear or not at all');
    h.assert(sql.value(`SELECT COUNT(*) FROM SecurityArchive WHERE user_name=${user} AND password=${h.sqlString(originalHash)}`) === '1',
      'The previous hash was not archived exactly once');
    h.assert(sql.value(`SELECT passwordUpdateDate>DATE_SUB(NOW(),INTERVAL 10 MINUTE) FROM security WHERE user_name=${user}`) === '1',
      'passwordUpdateDate was not moved by the change');
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE provider_no=${h.sqlString(fixture.providerNo)}
      AND action='update' AND content='Password/PIN update.'`, '1', 'No audit row recorded the password change');
  });

  await prefs.close();
  await throwaway.close();
  const relogin = await h.newContext(s.context.browser(), config);
  relogin.on('page', page => h.wireStrictPage(page, 'throwaway-relogin', recorder));
  let schedule2;
  await s.step('the old password no longer logs in and the new one reaches the schedule', async () => {
    const refused = await submitLoginForm(relogin, config, { username, password: config.testPassword, pin: config.testPin });
    h.assert(refused.outcome === 'failed', `The old password was not refused with the failed-login alert (landed on ${refused.landing})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE action='failed' AND content='login' AND contentId=${user}`) === '1',
      'The refused old-password login was not audited');
    await refused.page.close();
    schedule2 = await h.login(relogin, { ...config, testUser: username, testPassword: newPassword }, recorder, { label: 'throwaway-new-password' });
    h.assert(/providercontrol|appointment/i.test(schedule2.url()), 'The new password did not reach the schedule');
    await expectValue(sql, `SELECT COUNT(*)>=2 FROM log WHERE provider_no=${h.sqlString(fixture.providerNo)}
      AND action='log in' AND content='login'`, '1', 'The new-password login was not audited');
  });

  await s.step('negative probes: GET is rejected and a policy-violating password submitted without the form check does not replace the hash', async () => {
    const prefs2 = await clickOpensPopup(schedule2, schedule2.getByTitle(/Edit your personal setting/i).first(),
      { context: relogin, recorder, label: 'throwaway-preferences-probe', timeout: 20000 });
    const popup = await openChangePassword('change-password-probe', prefs2);
    await popup.waitForFunction(() => document.querySelector('input[name="CSRF-TOKEN"]')?.value);
    const token = await popup.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const action = await popup.locator('form[name="updatepassword"]').evaluate(form => form.action);
    const hashBefore = sql.value(hashQuery);
    const archivesBefore = sql.value(archiveCount);
    const rejected = await relogin.request.get(action, { maxRedirects: 0 });
    h.assert(rejected.status() === 405, `GET on the password update route answered HTTP ${rejected.status()}, expected 405`);
    h.assert(sql.value(hashQuery) === hashBefore, 'A rejected GET changed the stored hash');
    const weak = await relogin.request.post(action, {
      headers: { 'CSRF-TOKEN': token },
      form: { 'CSRF-TOKEN': token, oldpassword: newPassword, mypassword: WEAK_PASSWORD, confirmpassword: WEAK_PASSWORD,
        pin: '', newpin: '', confirmpin: '' },
      maxRedirects: 0,
    });
    const accepted = weak.status() === 200 && (await weak.text()).includes('self.close()');
    h.assert(sql.value(hashQuery) === hashBefore && sql.value(archiveCount) === archivesBefore && !accepted,
      'The server stored a password that violates the configured policy: the browser-side check is the only guard');
    await popup.close();
    await prefs2.close();
  });
  await relogin.close();
}

if (require.main === module) runWorkflow('password-change-preferences', workflow, { openPatient: false });
module.exports = { WEAK_PASSWORD, workflow };
