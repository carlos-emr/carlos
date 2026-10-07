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
 * context and owned throwaway usernames only. This check requires username-based locking
 * (login_lock=true) and the test administrator's site-access privacy permission. Any failure
 * after probing releases only these owned usernames before the browser closes. Cleanup never
 * unlocks a client address or clears the global login registry.
 * Site coverage: two out-of-site tracked accounts remain hidden, tampered POSTs return the
 * application permission refusal without an unlock audit, and the tracked entries survive.
 * Fixtures: three throwaway provider/security/secUserRole rows (lib/throwaway-login-fixture.js)
 * and two owned sites plus their memberships;
 * cleanup deletes every owned row, including its audit rows, and asserts they are gone.
 */
const { randomBytes } = require('node:crypto');
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
  const deniedFixtures = [0, 1].map(() => throwawayLoginFixture({
    sql, marker: `FAKE-PW${randomBytes(8).toString('hex')}`, provider: s.provider, testUser: config.testUser,
  }));
  for (const denied of deniedFixtures) {
    s.cleanup(() => denied.cleanup());
    denied.create();
  }
  // The scoped list requires actual site memberships; the generic login fixture creates none.
  // Both sites and all links below belong to this run, including the administrator's new link.
  const siteNames = ['shared', 'outside'].map(suffix => `${s.marker}-${suffix}`);
  const ownedSiteNames = [];
  const ownedProviders = [s.provider, fixture.providerNo, ...deniedFixtures.map(item => item.providerNo)];
  s.cleanup(() => {
    for (const name of ownedSiteNames) {
      const ids = sql.rows(`SELECT site_id FROM site WHERE name=${h.sqlString(name)}`).map(row => row[0]);
      h.assert(ids.length <= 1, 'Owned unlock site name is no longer unique');
      for (const id of ids) {
        h.assert(/^[1-9]\d*$/.test(id), 'Invalid owned site id');
        const providers = sql.rows(`SELECT provider_no FROM providersite WHERE site_id=${id}`).map(row => row[0]);
        h.assert(providers.every(provider => ownedProviders.includes(provider)), 'Refusing cleanup of a site with foreign memberships');
        sql.execute(`DELETE FROM providersite WHERE site_id=${id}; DELETE FROM site WHERE site_id=${id} AND name=${h.sqlString(name)}`);
      }
      h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE name=${h.sqlString(name)}`) === '0', 'Owned unlock site remains');
    }
  });
  const sites = siteNames.map((name, index) => {
    const shortName = `U${s.marker.slice(-8)}${index}`;
    h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE name=${h.sqlString(name)} OR short_name=${h.sqlString(shortName)}`) === '0',
      'Owned site name or short name already exists');
    ownedSiteNames.push(name); // Record ownership intent only after proving the name was absent.
    const id = sql.value(`INSERT INTO site (name,short_name,bg_color,status) VALUES (${h.sqlString(name)},${h.sqlString(shortName)},'#FFFFFF',1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Owned unlock site was not created');
    return id;
  });
  function linkSite(provider, site) {
    sql.execute(`INSERT IGNORE INTO providersite (provider_no,site_id) VALUES (${h.sqlString(provider)},${site})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM providersite WHERE provider_no=${h.sqlString(provider)} AND site_id=${site}`) === '1',
      'The owned site membership was not established');
  }
  linkSite(s.provider, sites[0]);
  linkSite(fixture.providerNo, sites[0]);
  for (const denied of deniedFixtures) linkSite(denied.providerNo, sites[1]);
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
  let submittedUnlock;
  async function unlockSelected(frame, entry) {
    await frame.locator('select[name="userName"]').selectOption(entry);
    const navigated = admin.waitForEvent('framenavigated', { predicate: candidate => candidate === frame, timeout: 20000 });
    navigated.catch(() => {});
    const posted = admin.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/admin/UnLock'), { timeout: 20000 });
    posted.catch(() => {});
    await frame.locator('input[name="submit"]').click();
    submittedUnlock = (await posted).postData();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return (await frame.locator('.alert-success').innerText({ timeout: 10000 }).catch(() => '')).replace(/\s+/g, ' ').trim();
  }
  // UnLock2Action removes the entry on a POST before it renders the page, so this works even
  // when rendering the Unlock page fails. The XHR goes through the admin page, where CSRFGuard's
  // hijacked XMLHttpRequest adds the session token. A key that is not locked is a no-op.
  async function releaseOwnLocks() {
    const url = h.appUrl(config.baseUrl, '/admin/UnLock');
    // Restore access only to this run's out-of-site fixtures before releasing their tracked attempts.
    for (const denied of deniedFixtures) linkSite(denied.providerNo, sites[0]);
    for (const key of [username, ...deniedFixtures.map(item => item.username)]) {
      const status = await admin.evaluate(({ url, key }) => new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.open('POST', url);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.onloadend = () => resolve(xhr.status);
        xhr.send(new URLSearchParams({ userName: key, submit: 'Unlock' }).toString());
      }), { url, key });
      console.log(`  Release POST for an owned throwaway username answered HTTP ${status}`);
    }
    const current = await s.context.request.get(url);
    const body = await current.text();
    h.assert(current.status() === 200 && /<select[^>]+name="userName"/.test(body),
      'Could not verify the owned tracking entries after cleanup');
    for (const item of [fixture, ...deniedFixtures]) {
      h.assert(!body.includes(`value="${item.username}"`), 'An owned username remains in the lock list after cleanup');
    }
    // The status alone proves nothing (a CSRF or privilege refusal also completes), so prove the
    // release the way the lock is observed: the throwaway's correct password must no longer
    // meet the lockout message. A misconfigured address-based lock is reported, never cleared.
    const verify = await probe(config.testPassword);
    await verify.page.close();
    h.assert(verify.outcome !== 'locked', 'The lock is still in place after the release POSTs; this runner or the'
      + ' throwaway username stays blocked until login_max_duration expires');
  }

  let frame = await openUnlock();
  const before = await lockList(frame);
  h.assert(!before.includes(username), 'The throwaway was in the lock list before any failed login');

  const probes = await h.newContext(s.context.browser(), config);
  probes.on('page', page => h.wireStrictPage(page, 'lockout-probe', recorder));
  const wrongPassword = `${config.testPassword}-WRONG`;
  async function probe(password, probeUsername = username) {
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
        result = await submitLoginForm(probes, config, { username: probeUsername, password, pin: config.testPin });
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

    await s.step('two tracked accounts from another site stay out of the scoped unlock list', async () => {
      for (const denied of deniedFixtures) {
        const result = await probe(wrongPassword, denied.username);
        await result.page.close();
        h.assert(result.outcome === 'failed', 'The out-of-site fixture did not record its owned failed attempt');
      }
      frame = await openUnlock();
      const listed = await lockList(frame);
      h.assert(listed.includes(username), 'The shared-site locked account is missing');
      h.assert(deniedFixtures.every(item => !listed.includes(item.username)), 'The scoped list exposed an out-of-site account');
    });

    await s.step('Administration ▸ Unlock Account lists the locked login, unlocks it by name and audits the unlock', async () => {
      frame = await openUnlock();
      const listed = await lockList(frame);
      const added = listed.filter(entry => !before.includes(entry));
      h.assert(added.includes(username), 'The owned account is absent from the shared-site unlock list;'
        + ' this workflow requires login_lock=true and a shared site');
      const message = await unlockSelected(frame, username);
      h.assert(message === `Account unlocked: ${username}`, 'The unlock did not confirm the throwaway by name');
      const after = await lockList(frame);
      h.assert(!after.includes(username), 'The unlocked login is still in the lock list');
      h.assert(JSON.stringify(after) === JSON.stringify(listed.filter(entry => entry !== username)),
        'Unlocking the throwaway changed other lock-list entries');
      await expectValue(sql, `SELECT COUNT(*) FROM log WHERE action='unlock' AND content='adminUnlock' AND contentId=${user}
        AND provider_no=${h.sqlString(s.provider)}`, '1', 'The unlock was not audited against the administrator');
    });
    await s.step('a replay is harmless and tampered out-of-site unlock POSTs are refused without an unlock audit', async () => {
      h.assert(submittedUnlock, 'The real unlock form POST was not captured');
      async function replayFor(target) {
        const form = Object.fromEntries(new URLSearchParams(submittedUnlock));
        form.userName = target;
        return s.context.request.post(h.appUrl(config.baseUrl, '/admin/UnLock'), {
          form, headers: { Referer: frame.url(), Origin: new URL(config.baseUrl).origin },
        });
      }
      const replay = await replayFor(username);
      h.assert(replay.status() === 200 && (await replay.text()).includes('Account was not in the lock list'),
        'The captured form/CSRF replay did not reach the action harmlessly');
      await expectValue(sql, `SELECT COUNT(*) FROM log WHERE action='unlock' AND content='adminUnlock' AND contentId=${user}
        AND provider_no=${h.sqlString(s.provider)}`, '1', 'Replay wrote another unlock audit');
      for (const denied of deniedFixtures) {
        const refusal = await replayFor(denied.username);
        h.assert(refusal.status() === 403 && (await refusal.text()).includes('You tried to access a resource with insufficient privileges.'),
          'The tampered POST did not reach the application site-permission refusal');
        h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE action='unlock' AND content='adminUnlock'
          AND contentId=${h.sqlString(denied.username)}`) === '0', 'A refused unlock was audited as successful');
      }
      // Grant the owned fixtures a shared site solely for cleanup and prove their entries survived
      // the refusals before unlocking through the same UI. Do not clear the global login registry.
      for (const denied of deniedFixtures) linkSite(denied.providerNo, sites[0]);
      frame = await openUnlock();
      const stillTracked = await lockList(frame);
      h.assert(deniedFixtures.every(item => stillTracked.includes(item.username)), 'A refused POST removed an out-of-site entry');
      for (const denied of deniedFixtures) {
        h.assert(await unlockSelected(frame, denied.username) === `Account unlocked: ${denied.username}`,
          'The owned out-of-site tracking entry was not released after granting cleanup access');
      }
    });
  } catch (error) {
    // A failed release is reported with the original failure, not just logged, so a lock left
    // behind is never mistaken for a clean run of the cleanup.
    await releaseOwnLocks().catch(release => {
      error.message = `${error.message} [and the failure-safe lock release FAILED: ${release.message}]`;
    });
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
