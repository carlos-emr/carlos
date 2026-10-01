#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Account lockout and administrative unlock (coverage plan §2.2 account-lockout-unlock).
 *
 * User path: wrong passwords on the login form for a THROWAWAY login until Login2Action refuses
 * it with the lockout message (login_max_failed_times, in-memory LoginList keyed by username when
 * login_lock=true), then as the admin test login: Schedule ▸ Administration ▸ User Management ▸
 * Unlock Account (admin/UnLock in the #dynamic-content iframe) ▸ select the throwaway ▸ Unlock.
 *
 * Asserts: every pre-lock failure is audited in `log` (action 'failed'); the locked attempt and the
 * correct password are both refused with the lockout text and add no audit row; the Unlock page
 * lists the throwaway, the unlock confirms by name, removes it from the list and writes the
 * 'unlock'/'adminUnlock' audit row; the throwaway then logs in to the schedule (audited 'log in').
 * The shared test account is never submitted on the login form: all probes use a second browser
 * context and the throwaway username only. If the lockout turns out to be keyed by address
 * (login_lock unset), the entry this run created is unlocked before the check fails. Any failure
 * from the first probe through the unlock step first releases this run's lock (the username and
 * the client address its own failures were audited from) with a direct POST from the admin page,
 * because the Unlock page itself can answer 500 and the browser is closed before cleanups run.
 * Fixtures: the throwaway provider/security/secUserRole rows (lib/throwaway-login-fixture.js);
 * cleanup deletes every owned row, including its audit rows, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const { submitLoginForm, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

// Bounded above the devcontainer's login_max_failed_times=10 (the shipped default is 3).
const MAX_PROBES = 12;
// With RateLimitFilter enforcing the /login=10/60 tier (or the nginx login zone) for a
// non-exempt client, the lock is recorded by the tenth wrong password and the POST that must
// observe it is refused 429 instead. A 429 is not an authentication outcome: wait out the window
// and repeat the same attempt, at most once per probe.
const RATE_LIMIT_WAIT_MS = 65000;

async function workflow(s) {
  const { sql, config, recorder } = s;
  const fixture = throwawayLoginFixture({ sql, marker: s.marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const { username } = fixture;
  const user = h.sqlString(username);
  const failedLogins = `SELECT COUNT(*) FROM log WHERE action='failed' AND content='login' AND contentId=${user}`;
  const successfulLogins = `SELECT COUNT(*) FROM log WHERE provider_no=${h.sqlString(fixture.providerNo)} AND action='log in' AND content='login'`;

  // Administration ▸ User Management ▸ Unlock Account, opened before any probe so the lock list
  // can be compared with what this run adds to it.
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder, label: 'administration', timeout: 20000 });
  async function openUnlock() {
    const link = admin.locator('#adminNav a[rel$="/admin/UnLock"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor();
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'The Unlock Account iframe did not load');
    // The shell appends the iframe without a src and assigns it afterwards, so wait for the
    // real document, then name a server error on it before waiting on its controls: with a
    // non-empty lock list the page's securityDao.findByProviderSite() call can answer 500.
    await frame.waitForURL(/\/admin\/UnLock/, { timeout: 20000, waitUntil: 'load' });
    h.assertStrictPage(recorder, ['administration']);
    await frame.locator('form[name="baseurl"] select[name="userName"]').waitFor({ state: 'attached', timeout: 20000 });
    await frame.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return frame;
  }
  const lockList = frame => frame.locator('select[name="userName"] option').evaluateAll(options => options.map(option => option.value));
  async function unlockSelected(frame, entry) {
    await frame.locator('select[name="userName"]').selectOption(entry);
    const navigated = admin.waitForEvent('framenavigated', { predicate: candidate => candidate === frame, timeout: 20000 });
    navigated.catch(() => {});
    await frame.locator('input[name="submit"]').click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return (await frame.locator('.alert-success').innerText({ timeout: 10000 }).catch(() => '')).replace(/\s+/g, ' ').trim();
  }
  // UnLock2Action removes the entry on a POST before it renders the page, so this works even
  // when rendering the Unlock page fails. The XHR goes through the admin page, where CSRFGuard's
  // hijacked XMLHttpRequest adds the session token. A key that is not locked is a no-op.
  const logMark = sql.value('SELECT IFNULL(MAX(id), 0) FROM log');
  async function releaseOwnLocks() {
    const addresses = sql.rows(`SELECT DISTINCT ip FROM log WHERE action='failed' AND content='login' AND contentId=${user}`)
      .map(row => row[0]).filter(ip => ip && ip !== 'NULL');
    const url = h.appUrl(config.baseUrl, '/admin/UnLock');
    for (const key of [username, ...addresses]) {
      const status = await admin.evaluate(({ url, key }) => new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.open('POST', url);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.onloadend = () => resolve(xhr.status);
        xhr.send(new URLSearchParams({ userName: key, submit: 'Unlock' }).toString());
      }), { url, key });
      console.log(`  Released lock entry for ${key === username ? 'the throwaway username' : 'this run\'s client address'} (HTTP ${status})`);
    }
    // Address unlocks are audited against the address, outside the fixture's owned-log predicate.
    if (addresses.length) {
      sql.execute(`DELETE FROM log WHERE id > ${logMark} AND action='unlock' AND content='adminUnlock'
        AND provider_no=${h.sqlString(s.provider)} AND contentId IN (${addresses.map(h.sqlString).join(',')})`);
    }
  }

  let frame = await openUnlock();
  const before = await lockList(frame);
  h.assert(!before.includes(username), 'The throwaway was in the lock list before any failed login');

  const probes = await h.newContext(s.context.browser(), config);
  probes.on('page', page => h.wireStrictPage(page, 'lockout-probe', recorder));
  const wrongPassword = `${config.testPassword}-WRONG`;
  async function probe(password) {
    for (let attempt = 0; ; attempt++) {
      const since = { responses: recorder.badResponses.length, console: recorder.consoleIssues.length };
      let limited = null;
      const watch = response => {
        if (response.status() === 429 && /\/login[^/]*$/.test(new URL(response.url()).pathname)) limited = response;
      };
      probes.on('response', watch);
      let result;
      let error;
      try {
        result = await submitLoginForm(probes, config, { username, password, pin: config.testPin });
      } catch (caught) { error = caught; } finally { probes.off('response', watch); }
      if (!limited) {
        if (error) throw error;
        return result;
      }
      h.assert(attempt === 0, 'The /login rate limit still refused the attempt after its window reset');
      // Consume exactly the 429 this probe drew; any other signal stays strict.
      for (let i = recorder.badResponses.length - 1; i >= since.responses; i--) {
        if (recorder.badResponses[i].status === 429) recorder.badResponses.splice(i, 1);
      }
      for (let i = recorder.consoleIssues.length - 1; i >= since.console; i--) {
        if (/status of 429 \(/.test(recorder.consoleIssues[i].text)) recorder.consoleIssues.splice(i, 1);
      }
      for (const page of probes.pages()) await page.close();
      const retryAfter = Number((await limited.allHeaders())['retry-after']);
      await new Promise(resolve => setTimeout(resolve,
        Number.isFinite(retryAfter) && retryAfter > 0 ? Math.min(retryAfter * 1000 + 1000, RATE_LIMIT_WAIT_MS) : RATE_LIMIT_WAIT_MS));
    }
  }
  let failures = 0;
  try {
    await s.step('repeated wrong passwords lock the throwaway login: each failure audited, then the lockout message', async () => {
      let locked = false;
      for (let attempt = 1; attempt <= MAX_PROBES && !locked; attempt++) {
        const result = await probe(wrongPassword);
        await result.page.close();
        if (result.outcome === 'locked') { locked = true; break; }
        h.assert(result.outcome === 'failed',
          `Wrong-password attempt ${attempt} did not show the failed-login alert (landed on ${result.landing})`);
        failures++;
        h.assert(sql.value(failedLogins) === String(failures), `Failed login ${attempt} was not audited`);
      }
      h.assert(locked, `No lockout after ${MAX_PROBES} wrong passwords: login_max_failed_times is not enforced for this client`
        + ' (the address may match login_local_ip, or lockout is disabled)');
      h.assert(failures >= 2, 'The login locked after fewer than two failures');
      h.assert(sql.value(failedLogins) === String(failures), 'The refused (locked) attempt was audited as an authentication failure');
    });

    await s.step('the correct password is refused with the lockout message while the login is locked', async () => {
      const result = await probe(config.testPassword);
      h.assert(result.outcome === 'locked', `A locked login accepted or mis-reported its correct password (landed on ${result.landing})`);
      await result.page.close();
      h.assert(sql.value(failedLogins) === String(failures), 'A refused locked attempt was audited as an authentication failure');
      h.assert(sql.value(successfulLogins) === '0', 'A locked login was audited as logged in');
    });

    await s.step('Administration ▸ Unlock Account lists the locked login, unlocks it by name and audits the unlock', async () => {
      frame = await openUnlock();
      const listed = await lockList(frame);
      const added = listed.filter(entry => !before.includes(entry));
      if (!added.includes(username)) {
        // The lock is keyed by something other than the username (login_lock is not true, so
        // LoginCheckLogin tracked the client address). Release only the address this run's own
        // audited failures came from (LoginCheckLoginBean logs the same ip it locks), so later
        // checks can still log in from this runner; any other new entry is left untouched.
        const ownAddresses = sql.rows(`SELECT DISTINCT ip FROM log WHERE action='failed' AND content='login' AND contentId=${user}`)
          .map(row => row[0]);
        const released = added.filter(entry => ownAddresses.includes(entry));
        for (const entry of released) await unlockSelected(frame, entry);
        h.assert(false, `The lockout is not keyed by username: this run added ${added.length} non-username entr(y/ies) to the lock list`
          + ` and unlocked the ${released.length} matching its own audited client address; set login_lock=true in carlos.properties`
          + ' for the username lockout this check covers');
      }
      const message = await unlockSelected(frame, username);
      h.assert(message === `Account unlocked: ${username}`, 'The unlock did not confirm the throwaway by name');
      const after = await lockList(frame);
      h.assert(!after.includes(username), 'The unlocked login is still in the lock list');
      h.assert(JSON.stringify(after) === JSON.stringify(listed.filter(entry => entry !== username)),
        'Unlocking the throwaway changed other lock-list entries');
      await expectValue(sql, `SELECT COUNT(*) FROM log WHERE action='unlock' AND content='adminUnlock' AND contentId=${user}
        AND provider_no=${h.sqlString(s.provider)}`, '1', 'The unlock was not audited against the administrator');
    });
  } catch (error) {
    await releaseOwnLocks().catch(release => console.log(`  Failure-safe lock release failed: ${release.message}`));
    throw error;
  }

  await s.step('the unlocked login signs in with its correct password and is audited', async () => {
    const schedule = await h.login(probes, { ...config, testUser: username }, recorder, { label: 'throwaway-unlocked-login' });
    h.assert(/providercontrol|appointment/i.test(schedule.url()), 'The unlocked login did not reach the schedule');
    await expectValue(sql, successfulLogins, '1', 'The post-unlock login was not audited');
    h.assert(sql.value(failedLogins) === String(failures), 'The successful login was audited as a failure');
    await probes.close();
  });
}

if (require.main === module) runWorkflow('account-lockout-unlock', workflow, { openPatient: false });
module.exports = { MAX_PROBES, workflow };
