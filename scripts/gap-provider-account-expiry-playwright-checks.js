#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-account-expiry — login-time handling of an account with an expiry date
 * (security.b_ExpireSet / date_ExpireDate, set on the Add/Edit a Login Record forms).
 *
 * User paths, as a THROWAWAY login (lib/throwaway-login-fixture.js):
 *   login with the account expiring in 5 days ▸ the day sheet shows the "account will expire soon;
 *   days remaining" banner ▸ its Change Password button (provider/ViewChangePassword, which shows how an
 *   administrator renews the account);
 *   login again with the expiry 30 days out ▸ no banner;
 *   login with the expiry yesterday ▸ refused at the front door with the expired-account message.
 * Nothing drove the banner (appointmentprovideradminday.jsp onLoad ▸ showPasswordExpiryWarning), the
 * 10-day warning window in LoginCheckLoginBean, the banner's Change Password route or the expired
 * refusal (security-record-admin only sets the flag and checks a DELETED login).
 *
 * Asserted: inside the window the banner shows the remaining whole days and the session carries it;
 * its button opens the account-renewal notice (provider/ViewChangePassword) without an error; outside the
 * window there is no banner; an expired account is refused with the expired message, never reaches
 * the schedule, and is audited (log action `expired`); the refused attempt wrote nothing else.
 * Fixtures: the throwaway login only; its security row's expiry columns are the only thing changed
 * and the fixture deletes the row. EXCLUSIVE=1: the expired attempt is a failed login, which the
 * address-keyed lockout counter (login_lock off) or the username counter (login_lock on) records;
 * running alone keeps that count away from other checks' own login assertions.
 * Implements coverage plan §2.2 (authentication, session, authorisation: account expiry).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { bundleMessage, submitLoginForm, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

async function workflow(s) {
  const { sql, config, recorder, marker } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const user = h.sqlString(fixture.username);
  // The expired refusal is audited as action 'expired', which the fixture's own cleanup does not sweep.
  s.cleanup(() => {
    sql.execute(`DELETE FROM log WHERE action='expired' AND content='login' AND contentId=${user}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE action='expired' AND contentId=${user}`) === '0', 'The owned expired-login audit row was not removed');
  });
  const setExpiry = interval => sql.execute(`UPDATE security SET b_ExpireSet=1, date_ExpireDate=${interval} WHERE user_name=${user}`);
  const expiredMessage = bundleMessage('login.errorAccountExpired', 'Your account is expired. Please contact your administrator.');
  async function signIn(label) {
    const context = await h.newContext(s.context.browser(), config);
    context.setDefaultTimeout(20000);
    context.on('page', page => h.wireStrictPage(page, label, recorder));
    const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label });
    return { context, schedule };
  }

  await s.step('inside the 10-day window the day sheet shows the days remaining and offers Change Password', async () => {
    // date_ExpireDate is a DATE (midnight), and LoginCheckLoginBean floors (midnight - now) / 24h, so an expiry six
    // date boundaries away leaves between five and six days at any time of day: five whole days remain.
    setExpiry('DATE_ADD(CURDATE(), INTERVAL 6 DAY)');
    const { context, schedule } = await signIn('expiring-login');
    const banner = schedule.locator('#password-expiry-warning');
    await banner.waitFor({ state: 'visible' });
    const text = (await banner.innerText()).replace(/\s+/g, ' ');
    h.assert(/days remaining: 5\b/.test(text), `The banner does not show 5 days remaining: "${text}"`);
    const button = banner.locator('a.btn', { hasText: /change password/i });
    await Promise.all([schedule.waitForNavigation({ waitUntil: 'domcontentloaded' }), button.click()]);
    h.assert(h.pathOnly(schedule.url()).endsWith('/provider/ViewChangePassword'), 'The banner button did not open the Change Password page');
    await h.assertNotErrorPage(schedule, 'expiry banner button');
    // The route (provider/changePassword.jsp) is the renewal notice: an expiring ACCOUNT is renewed by an
    // administrator on the security record, not by the user's own password change.
    const notice = (await schedule.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(/will be expired soon, please contact your administrator to renew your account/i.test(notice),
      'The banner button did not open the account-renewal notice');
    await context.close();
  });

  await s.step('outside the window (30 days) there is no banner', async () => {
    setExpiry('DATE_ADD(NOW(), INTERVAL 30 DAY)');
    const { context, schedule } = await signIn('far-expiry-login');
    h.assert(await schedule.locator('#password-expiry-warning').count() === 0, 'A 30-day expiry still showed the expiring-soon banner');
    await context.close();
  });

  await s.step('an expired account is refused with the expired message and never reaches the schedule', async () => {
    setExpiry('DATE_SUB(NOW(), INTERVAL 1 DAY)');
    const context = await h.newContext(s.context.browser(), config);
    context.on('page', page => h.wireStrictPage(page, 'expired-login', recorder));
    const expiredRows = () => sql.value(`SELECT COUNT(*) FROM log WHERE action='expired' AND content='login' AND contentId=${user}`);
    const before = expiredRows();
    const attempt = await submitLoginForm(context, config, { username: fixture.username, password: config.testPassword, pin: config.testPin });
    const text = (await attempt.page.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(attempt.outcome !== 'schedule' && !/providercontrol/.test(attempt.landing), 'An expired account reached the schedule');
    h.assert(text.includes(expiredMessage), `The refusal does not say the account is expired (landed on ${attempt.landing})`);
    h.assert(expiredRows() === String(Number(before) + 1), 'The refused expired login was not audited (action expired)');
    h.assert(sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${user} AND b_ExpireSet=1`) === '1', 'The refusal changed the expiry flag');
    await attempt.page.close();
    await context.close();
  });
}

if (require.main === module) runWorkflow('gap-provider-account-expiry', workflow, { openPatient: false });
module.exports = { workflow };
