/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * A disposable login for checks that must NOT touch the shared test account:
 * password changes, lockouts, anything that alters or blocks a credential.
 *
 * The fixture copies the shared test login's `security` row (its bcrypt hash,
 * PIN and lock flags exactly as stored, so the throwaway signs in with
 * config.testPassword / config.testPin), gives it its own `provider` row and
 * the test login's active role assignments, and removes every row it owns on
 * cleanup -- including the rows the application writes on the throwaway's
 * behalf (SecurityArchive on a password change, provider_facility on first
 * login, the audit `log` rows that carry its provider number or username).
 *
 * Usernames are alphanumeric only: Login2Action rejects anything else before
 * authentication (`[a-zA-Z0-9]{1,30}`), so the run marker's hex tail is used
 * without the `FAKE-PW` hyphen.
 */
const fs = require('node:fs');
const path = require('node:path');
const { randomInt } = require('node:crypto');
const { assert, gotoApp, pathOnly, sqlString } = require('./playwright-harness');
const { settleOperations } = require('../graceful-signal-cancellation');

const PROVIDER_LINKED_TABLES = ['provider_facility', 'program_provider', 'providersite', 'property', 'secUserRole'];
// Logging in creates a ProviderPreference row (keyed providerNo, not provider_no),
// and the preferences popup can add its appointment-screen children. Leaving them
// behind accumulated orphan preference rows for providers that no longer exist.
const PROVIDER_PREFERENCE_TABLES = ['ProviderPreferenceAppointmentScreenEForm', 'ProviderPreferenceAppointmentScreenForm',
  'ProviderPreferenceAppointmentScreenQuickLink', 'ProviderPreference'];

/**
 * English text of a bundle key, read from the source tree so a reworded
 * message moves the assertion with it; the fallback covers a packaged VM
 * that runs the scripts without src/.
 */
function bundleMessage(key, fallback) {
  const bundle = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'oscarResources_en.properties');
  try {
    const line = fs.readFileSync(bundle, 'utf8').split('\n').find(candidate => candidate.startsWith(`${key}=`));
    return line ? line.slice(key.length + 1).trim() : fallback;
  } catch (error) {
    return fallback;
  }
}

/**
 * Build the fixture. Nothing is written until create(): register cleanup()
 * with the workflow session first, then call create().
 *
 * @param sql the session's createSqlRunner result
 * @param marker the run marker (`FAKE-PW<16 hex>`)
 * @param provider provider_no of the shared test login (lastUpdateUser, role source)
 * @param testUser user_name of the shared test login whose security row is copied
 */
function throwawayLoginFixture({ sql, marker, provider, testUser }) {
  const hex = marker.replace(/^FAKE-PW/, '');
  assert(/^[0-9a-f]{16}$/.test(hex), 'The run marker is not in the FAKE-PW<hex> form this fixture relies on');
  const username = `FAKEPW${hex}`;
  const state = { providerNo: null, securityNo: null, passwordHash: null, created: false };

  function ownedLogPredicate() {
    return `(provider_no=${sqlString(state.providerNo)} OR (contentId=${sqlString(username)} AND action IN ('failed','unlock')))`;
  }

  return {
    username,
    get providerNo() { return state.providerNo; },
    get securityNo() { return state.securityNo; },
    /** The bcrypt hash the throwaway started with (identical to the test login's). */
    get passwordHash() { return state.passwordHash; },

    create() {
      assert(!state.created, 'The throwaway login was already created');
      assert(sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${sqlString(testUser)}`) === '1',
        'The shared test login has no single security row to copy');
      assert(sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${sqlString(username)}`) === '0',
        'A security row already carries this run\'s throwaway username');
      // provider_no is varchar(6); pick an unused number well away from the seeded range.
      for (let attempt = 0; attempt < 20 && !state.providerNo; attempt++) {
        const candidate = String(randomInt(800000, 899999));
        const taken = sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${sqlString(candidate)})
          + (SELECT COUNT(*) FROM security WHERE provider_no=${sqlString(candidate)})`);
        if (taken === '0') state.providerNo = candidate;
      }
      assert(state.providerNo, 'No unused provider number was found for the throwaway login');
      const providerNo = sqlString(state.providerNo);
      state.created = true;
      sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
        SELECT ${providerNo},${sqlString(marker)},'Throwaway',provider_type,specialty,sex,'1',${sqlString(provider)},NOW()
        FROM provider WHERE provider_no=${sqlString(provider)}`);
      assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${providerNo} AND last_name=${sqlString(marker)}`) === '1',
        'The throwaway provider row was not created');
      // Copy the credential columns verbatim: whatever the install stores (encoder
      // id, PIN encryption, PIN-on-WAN/LAN flags) applies to the throwaway too.
      // forcePasswordReset and MFA are explicitly off so the login lands on the schedule.
      state.securityNo = sql.value(`INSERT INTO security (user_name,password,provider_no,pin,b_ExpireSet,date_ExpireDate,
          b_LocalLockSet,b_RemoteLockSet,forcePasswordReset,passwordUpdateDate,pinUpdateDate,lastUpdateUser,lastUpdateDate,usingMfa,mfaSecret)
        SELECT ${sqlString(username)},password,${providerNo},pin,b_ExpireSet,date_ExpireDate,b_LocalLockSet,b_RemoteLockSet,
          0,NOW(),NOW(),${sqlString(provider)},NOW(),0,NULL
        FROM security WHERE user_name=${sqlString(testUser)}; SELECT LAST_INSERT_ID()`);
      assert(/^[1-9]\d*$/.test(state.securityNo), 'The throwaway security row was not created');
      state.passwordHash = sql.value(`SELECT password FROM security WHERE security_no=${state.securityNo}`);
      assert(state.passwordHash
        && state.passwordHash === sql.value(`SELECT password FROM security WHERE user_name=${sqlString(testUser)}`),
      'The throwaway did not inherit the test login\'s password hash');
      sql.execute(`INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate)
        SELECT ${providerNo},role_name,orgcd,activeyn,NOW() FROM secUserRole
        WHERE provider_no=${sqlString(provider)} AND activeyn=1`);
      assert(Number(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${providerNo}`)) > 0,
        'The throwaway received no role assignment');
      return this;
    },

    /** Delete every owned row and prove it; safe after a create() that failed midway. */
    cleanup() {
      if (!state.created) return;
      const providerNo = sqlString(state.providerNo);
      const user = sqlString(username);
      // Ownership first: the provider row must still carry this run's marker and
      // the security row must still be the throwaway's before anything is deleted.
      assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${providerNo} AND last_name<>${sqlString(marker)}`) === '0',
        'Throwaway provider ownership changed; refusing to delete');
      assert(sql.value(`SELECT COUNT(*) FROM security WHERE provider_no=${providerNo} AND user_name<>${user}`) === '0',
        'Throwaway security ownership changed; refusing to delete');
      const statements = [
        `DELETE FROM log WHERE ${ownedLogPredicate()}`,
        `DELETE FROM SecurityArchive WHERE user_name=${user} OR provider_no=${providerNo}`,
        ...PROVIDER_LINKED_TABLES.map(table => `DELETE FROM ${table} WHERE provider_no=${providerNo}`),
        ...PROVIDER_PREFERENCE_TABLES.map(table => `DELETE FROM ${table} WHERE providerNo=${providerNo}`),
        `DELETE FROM security WHERE user_name=${user} AND provider_no=${providerNo}`,
        `DELETE FROM provider WHERE provider_no=${providerNo} AND last_name=${sqlString(marker)}`,
      ];
      sql.execute(statements.join(';'));
      const remaining = [
        `(SELECT COUNT(*) FROM log WHERE ${ownedLogPredicate()})`,
        `(SELECT COUNT(*) FROM SecurityArchive WHERE user_name=${user} OR provider_no=${providerNo})`,
        ...PROVIDER_LINKED_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${providerNo})`),
        ...PROVIDER_PREFERENCE_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE providerNo=${providerNo})`),
        `(SELECT COUNT(*) FROM security WHERE user_name=${user} OR provider_no=${providerNo})`,
        `(SELECT COUNT(*) FROM provider WHERE provider_no=${providerNo})`,
      ];
      assert(sql.value(`SELECT ${remaining.join('+')}`) === '0', 'Throwaway login rows were not all removed');
    },
  };
}

/**
 * Drive the login form the way a person does, for attempts whose outcome is
 * expected to be a refusal (the harness login() asserts it reached the
 * schedule). The caller's context wiring labels the page.
 *
 * @return {page, outcome, landing} with outcome one of 'failed' (the generic
 *   wrong-credential alert), 'locked' (the lockout message), 'schedule', 'other'.
 */
async function submitLoginForm(context, config, credentials) {
  const failedText = bundleMessage('loginApplication.formFailedLabel', 'Login failed. Username or Password is incorrect.');
  const lockedText = bundleMessage('login.errorAccountLocked',
    'Oops! Your account is now locked due to incorrect password attempts.');
  const page = await context.newPage();
  await gotoApp(page, config.baseUrl, '/');
  await page.waitForLoadState('load', { timeout: 30000 });
  await page.locator('#username').fill(credentials.username);
  await page.locator('#password').fill(credentials.password);
  const pinInput = page.locator('#pin');
  if (await pinInput.count() > 0) await pinInput.fill(credentials.pin);
  await settleOperations([
    page.waitForURL(/login=failed|loginfailed|providercontrol|appointment|forcepasswordreset|loginMfa|select_facility/i,
      { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const url = page.url();
  const text = (await page.locator('body').innerText({ timeout: 10000 }).catch(() => '')).replace(/\s+/g, ' ');
  let outcome = 'other';
  if (/loginfailed/i.test(url) && text.includes(lockedText)) outcome = 'locked';
  else if (/login=failed/i.test(url) && text.includes(failedText)) outcome = 'failed';
  else if (/providercontrol|appointment/i.test(url)) outcome = 'schedule';
  return { page, outcome, landing: pathOnly(url) };
}

module.exports = {
  bundleMessage,
  submitLoginForm,
  throwawayLoginFixture,
};
