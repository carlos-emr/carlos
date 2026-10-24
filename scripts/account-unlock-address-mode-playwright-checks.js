#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administrative unlock in the packaged ADDRESS lock mode (issue #4550, finding 162).
 *
 * With `login_lock` unset (the packaged default) LoginList keys are client addresses, not
 * usernames. A site-restricted administrator (one holding `_site_access_privacy`) used to see an
 * empty Unlock Account list and every direct unlock was refused, because UnLock2Action filtered by
 * the usernames of the administrator's sites. The filter now applies only when login_lock=true.
 *
 * User path: wrong passwords on the login form for a THROWAWAY login until Login2Action refuses
 * the client address, then as the admin test login: Administration > User Management > Unlock
 * Account (admin/UnLock in the #dynamic-content iframe) > select the address > Unlock.
 *
 * Asserts: the admin holds _site_access_privacy; the lock list contains the audited client address (never the
 * throwaway username) and the page does not answer 403/500; the unlock confirms by that address,
 * removes it from the list and writes the 'unlock'/'adminUnlock' audit row; the throwaway then
 * logs in to the schedule. The complementary username-mode check is
 * account-lockout-unlock-playwright-checks.js, which requires login_lock=true; the two cannot
 * share one server configuration, so this check is manual (runs only with --only) and refuses to
 * run when login_lock=true. Run with EXCLUSIVE=1 on a disposable deployment: address lockout
 * affects every login from this client. An owned failed-login audit identifies the address even
 * when earlier failures already put it in the list (#5208). Failure-safe release uses only that
 * address, and cleanup removes only this run's unlock audit, preserving earlier audit history.
 */
const { isIP } = require('node:net');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const { submitLoginForm, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

// Bounded above the devcontainer's login_max_failed_times=10 (the shipped default is 3).
const MAX_PROBES = 12;

async function workflow(s) {
  h.assert(process.env.EXCLUSIVE === '1', 'Address lockout affects all logins from this client: run with EXCLUSIVE=1 on a disposable deployment');
  const { sql, config, recorder } = s;
  const fixture = throwawayLoginFixture({ sql, marker: s.marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const { username } = fixture;

  const roles = sql.rows(`SELECT role_name FROM secUserRole WHERE provider_no=${h.sqlString(s.provider)}
    AND COALESCE(activeyn,1)=1`).map(row => row[0]);
  h.assert(roles.length > 0, 'The test login has no active role');
  const privileged = sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE objectName='_site_access_privacy'
    AND privilege IN ('x','r') AND roleUserGroup IN (${roles.map(h.sqlString).join(',')})`);
  h.assert(Number(privileged) > 0, 'The test login must hold _site_access_privacy: this check is about site-restricted administrators');

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
    await frame.waitForURL(/\/admin\/UnLock/, { timeout: 20000, waitUntil: 'load' });
    h.assertStrictPage(recorder, ['administration']);
    await frame.locator('form[name="baseurl"] select[name="userName"]').waitFor({ state: 'attached', timeout: 20000 });
    await frame.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return frame;
  }
  const lockList = frame => frame.locator('select[name="userName"] option').evaluateAll(options => options.map(option => option.value));

  let frame;
  let address;
  let needsRelease = false;
  const beforeUnlockLogId = sql.value('SELECT COALESCE(MAX(id),0) FROM log');
  h.assert(/^\d+$/.test(beforeUnlockLogId), 'Invalid unlock audit boundary');
  const unlockAudit = () => `id>${beforeUnlockLogId} AND action='unlock' AND content='adminUnlock'
    AND contentId=${h.sqlString(address)} AND provider_no=${h.sqlString(s.provider)}`;
  s.cleanup(() => {
    if (address) {
      sql.execute(`DELETE FROM log WHERE ${unlockAudit()}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE ${unlockAudit()}`) === '0', 'Owned address unlock audit remains');
    }
  });

  const probes = await h.newContext(s.context.browser(), config);
  probes.on('page', page => h.wireStrictPage(page, 'lockout-probe', recorder));
  const wrongPassword = `${config.testPassword}-WRONG`;
  async function release() {
    if (needsRelease) {
      const status = await admin.evaluate(({ url, key }) => new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.open('POST', url);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.onloadend = () => resolve(xhr.status);
        xhr.send(new URLSearchParams({ userName: key, submit: 'Unlock' }).toString());
      }), { url: h.appUrl(config.baseUrl, '/admin/UnLock'), key: address });
      h.assert(status === 200, `The owned address release answered HTTP ${status}`);
      // A 200 can be a refusal page: prove the address is usable again before closing.
      await h.login(probes, { ...config, testUser: username }, recorder, { label: 'address-release-login' });
      needsRelease = false;
    }
  }

  try {
    await s.step('one failed password identifies the client address before lockout', async () => {
      const result = await submitLoginForm(probes, config, { username, password: wrongPassword, pin: config.testPin });
      await result.page.close();
      h.assert(result.outcome === 'failed', 'The client was already locked or the throwaway did not reach credential validation; no foreign lock will be released');
      const addresses = sql.rows(`SELECT DISTINCT ip FROM log WHERE action='failed' AND content='login'
        AND contentId=${h.sqlString(username)}`).map(row => row[0]);
      h.assert(addresses.length === 1 && isIP(addresses[0]), 'The owned failed login has no unique client address');
      [address] = addresses;
      needsRelease = true;
      // LoginList lists counters below the threshold too. Exercise that existing-entry case
      // deliberately: comparing a later list with this one used to report a false empty list.
      frame = await openUnlock();
      const listed = await lockList(frame);
      h.assert(!listed.includes(username), 'The throwaway username was tracked: login_lock=true is set; use account-lockout-unlock instead');
      h.assert(listed.includes(address), `The scoped Unlock Account list does not contain the tracked client address ${address} (finding 162)`);
    });

    await s.step('repeated wrong passwords lock the client address', async () => {
      let locked = false;
      for (let attempt = 1; attempt <= MAX_PROBES && !locked; attempt++) {
        const result = await submitLoginForm(probes, config, { username, password: wrongPassword, pin: config.testPin });
        await result.page.close();
        locked = result.outcome === 'locked';
        h.assert(locked || result.outcome === 'failed', `Attempt ${attempt} landed on ${result.landing}`);
      }
      h.assert(locked, `No lockout after ${MAX_PROBES} wrong passwords: is login_lock unset and is this client outside login_local_ip?`);
    });

    await s.step('the site-restricted administrator lists the locked address and not a username', async () => {
      frame = await openUnlock();
      const listed = await lockList(frame);
      h.assert(!listed.includes(username), 'The throwaway username was tracked: login_lock=true is set; use account-lockout-unlock instead');
      h.assert(listed.includes(address), `The scoped Unlock Account list does not contain the locked client address ${address} (finding 162)`);
    });

    await s.step('unlocking the address succeeds and is audited', async () => {
      await frame.locator('select[name="userName"]').selectOption(address);
      const navigated = admin.waitForEvent('framenavigated', { predicate: candidate => candidate === frame, timeout: 20000 });
      navigated.catch(() => {});
      await frame.locator('input[name="submit"]').click();
      await navigated;
      await frame.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
      const message = (await frame.locator('.alert-success').innerText({ timeout: 10000 }).catch(() => '')).replace(/\s+/g, ' ').trim();
      h.assert(message === `Account unlocked: ${address}`, `The unlock was refused or unconfirmed: "${message}"`);
      h.assert(!(await lockList(frame)).includes(address), 'The unlocked address is still listed');
      needsRelease = false;
      await expectValue(sql, `SELECT COUNT(*) FROM log WHERE ${unlockAudit()}`, '1',
      'The address unlock was not audited against the administrator');
    });

    await s.step('the throwaway login signs in once the address is unlocked', async () => {
      const schedule = await h.login(probes, { ...config, testUser: username }, recorder, { label: 'throwaway-unlocked-login' });
      h.assert(/providercontrol|appointment/i.test(schedule.url()), 'The unlocked login did not reach the schedule');
    });
  } catch (error) {
    await release().catch(failure => {
      error.message = `${error.message} [and the failure-safe release FAILED: ${failure.message}]`;
    });
    throw error;
  } finally {
    await probes.close();
  }
}

if (require.main === module) runWorkflow('account-unlock-address-mode', workflow, { openPatient: false });
module.exports = { MAX_PROBES, workflow };
