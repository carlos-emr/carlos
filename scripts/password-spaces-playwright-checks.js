#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Changes an owned login's password through Preferences, preserving internal,
 * repeated, leading and trailing spaces, then authenticates in a fresh context.
 * Failed-password variants are covered by Java tests to avoid shared IP lockout.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');

async function workflow(s) {
  const account = throwawayLoginFixture({ sql: s.sql, marker: s.marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => account.cleanup());
  account.create();
  const hashQuery = `SELECT password FROM security WHERE security_no=${account.securityNo}`;
  const suffix = s.marker.slice(-8);
  const candidates = [
    `Fake1! ${suffix}`, // ggignore - synthetic, per-run throwaway credential
    ` Fake2!  ${suffix} `, // ggignore - synthetic, per-run throwaway credential
    `Fake3!${suffix}`, // ggignore - synthetic, per-run throwaway credential
  ];
  let currentPassword = s.config.testPassword;
  let context;

  async function login(password) {
    context = await h.newContext(s.context.browser(), s.config);
    context.on('page', page => h.wireStrictPage(page, 'space-password-session', s.recorder));
    return h.login(context, { ...s.config, testUser: account.username, testPassword: password }, s.recorder);
  }

  let schedule = await login(currentPassword);
  for (const [index, password] of candidates.entries()) {
    await s.step(`password change ${index + 1} preserves every character and the saved password logs in`, async () => {
      const prefs = await clickOpensPopup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
        { context, recorder: s.recorder, label: 'space-password-preferences', timeout: 20000 });
      const link = prefs.locator('a[href$="/provider/ViewProviderChangePassword"]');
      await revealAuditLink(prefs, link, 20000);
      const label = `space-password-change-${index}`;
      const page = await clickOpensPopup(prefs, link, { context, recorder: s.recorder, label, timeout: 20000 });
      await page.locator('input[name="oldpassword"]').fill(currentPassword);
      await page.locator('input[name="mypassword"]').fill(password);
      await page.locator('input[name="confirmpassword"]').fill(password);
      h.assert(await page.locator('input[name="oldpassword"]').inputValue() === currentPassword,
        'The change form altered the current password');
      h.assert(await page.locator('input[name="mypassword"]').inputValue() === password,
        'The change form altered the new password');
      const oldHash = s.sql.value(hashQuery);
      const since = s.recorder.requestFailures.length;
      const closed = page.waitForEvent('close', { timeout: 20000 });
      await page.locator('input[type="submit"]').click({ noWaitAfter: true });
      await closed;
      // The success popup closes while its injected CSRFGuard script is loading.
      const aborted = s.recorder.requestFailures.slice(since).findIndex(entry => entry.label === label
        && entry.resourceType === 'script' && entry.errorText === 'net::ERR_ABORTED'
        && /\/csrfguard$/.test(new URL(entry.url).pathname));
      if (aborted >= 0) s.recorder.requestFailures.splice(since + aborted, 1);
      await expectValue(s.sql, `SELECT password<>${h.sqlString(oldHash)} FROM security WHERE security_no=${account.securityNo}`,
        '1', 'Password change did not replace the stored hash');
      const savedHash = s.sql.value(hashQuery);
      h.assert(savedHash.startsWith('{bcrypt}'), 'Password change did not store a bcrypt hash');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM SecurityArchive WHERE user_name=${h.sqlString(account.username)}
        AND password=${h.sqlString(oldHash)}`) === '1', 'Previous password was not archived exactly once');
      await context.close();
      schedule = await login(password);
      h.assert(/providercontrol|appointment/i.test(schedule.url()), 'The saved password did not reach the schedule');
      h.assert(s.sql.value(hashQuery) === savedHash, 'Login changed a current bcrypt hash');
      currentPassword = password;
    });
  }
  await context.close();
}

if (require.main === module) runWorkflow('password-spaces', workflow, { openPatient: false });
module.exports = { workflow };
