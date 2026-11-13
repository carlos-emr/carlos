#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: two administrators edit / delete the same security (login) record.
 *
 * User path (both sessions of the shared test login): Schedule > Administration > User Management >
 * Search/Edit/Delete Security Records (admin/ViewSecuritySearchRecordsHtm, #myFrame iframe) > search by
 * user name > click the user name > Update Record (admin/SecurityUpdate) / Delete Record.
 * Session A opens the edit page of a login; session B opens the same page, sets "Force password reset" to true and
 * saves; session A, still holding the page it loaded before that, ticks the expiry flag and saves.
 * Asserted: B's force-reset lands (control), and B's force-reset survives A's stale save.
 * The save used to copy every posted control onto the row (forcePasswordReset included) with no
 *      comparison against what the form was rendered from, so the stale "false" silently cleared the
 *      security setting an administrator just made (ledger finding 355). The form now carries the version it
 *      was rendered from (securityVersion); SecurityRecordUpdateService compares it under the row lock and
 *      refuses A's stale form whole: HTTP 409 and the conflict page, none of its fields stored (not even the
 *      expiry flag A ticked), and an offer to reopen the record. Reopened, the record shows B's reset and A's
 *      change saves from it.
 * (Delete-while-editing cannot be driven: Delete Record itself fails with the known detached-remove defect, ISSUES L20.)
 * The edit page's known missing stylesheet (admin/bcArStyle.css, L19) is consumed explicitly.
 * Fixtures: one marker-named provider with a security row (password hash / PIN copied from the test login),
 * one doctor role, seeded by SQL and removed by cleanup, which asserts them gone. No demo row is touched.
 * Wave-7 sweep "concurrency".
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openSecondSession, failureMark, consumeKnownConsole, consumeExpectedFailure } = require('./lib/concurrency-support');
const { openSection, navigateFrame, searchByUserName } = require('./security-record-admin-playwright-checks');

const TIMEOUT = 20000;
const PROVIDER_PREFERENCE_TABLES = ['ProviderPreferenceAppointmentScreenEForm', 'ProviderPreferenceAppointmentScreenForm',
  'ProviderPreferenceAppointmentScreenQuickLink', 'ProviderPreference'];
const PROVIDER_LINKED_TABLES = ['secUserRole', 'program_provider', 'provider_facility', 'providersite', 'property'];

function pickUnusedProviderNo(sql, taken) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const candidate = String(randomInt(700000, 999000));
    if (taken.includes(candidate)) continue;
    const c = h.sqlString(candidate);
    const used = sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${c}) + (SELECT COUNT(*) FROM security WHERE provider_no=${c})
      + (SELECT COUNT(*) FROM log WHERE provider_no=${c})
      + ${[...PROVIDER_LINKED_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${c})`),
        ...PROVIDER_PREFERENCE_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE providerNo=${c})`)].join(' + ')}`);
    if (used === '0') return candidate;
  }
  h.assert(false, 'No unused provider number was found in the fixture range');
  return null;
}

async function openEditPage(context, schedule, recorder, label, userName) {
  const mark = failureMark(recorder);
  const { page: admin } = await clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label, timeout: TIMEOUT });
  const frame = await openSection(admin, 'input[name="keyword"]');
  await searchByUserName(admin, frame, userName);
  await navigateFrame(admin, frame, frame.getByRole('link', { name: userName, exact: true }));
  h.assert(await frame.locator('input[name="user_name"]').inputValue() === userName, 'The edit page shows another login');
  await admin.waitForTimeout(300);
  consumeKnownConsole(recorder, mark, /bcArStyle\.css/);
  return { admin, frame };
}

async function workflow(s) {
  const { sql, config } = s;
  const logins = [];
  s.cleanup(() => {
    for (const login of logins) {
      const no = h.sqlString(login.providerNo);
      sql.execute(`DELETE FROM log WHERE provider_no=${no}`);
      sql.execute(`DELETE FROM security WHERE provider_no=${no} AND user_name=${h.sqlString(login.userName)}`);
      for (const table of PROVIDER_LINKED_TABLES) sql.execute(`DELETE FROM ${table} WHERE provider_no=${no}`);
      for (const table of PROVIDER_PREFERENCE_TABLES) sql.execute(`DELETE FROM ${table} WHERE providerNo=${no}`);
      sql.execute(`DELETE FROM provider WHERE provider_no=${no} AND last_name=${h.sqlString(s.marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM security WHERE provider_no=${no}) + (SELECT COUNT(*) FROM provider WHERE provider_no=${no})
        + (SELECT COUNT(*) FROM secUserRole WHERE provider_no=${no})`) === '0', 'Owned security/provider fixtures were not removed');
    }
  });
  const [[hash, pin]] = sql.rows(`SELECT password,pin FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
  h.assert(hash && pin, 'The configured test login has no stored password hash and PIN to copy');
  const seed = (tag) => {
    const providerNo = pickUnusedProviderNo(sql, logins.map(login => login.providerNo));
    const userName = `pw${tag}${s.marker.slice(-14)}`;
    const login = { providerNo, userName, securityNo: null };
    logins.push(login); // registered before the first insert so cleanup covers a half-created fixture
    sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,team,sex,status,lastUpdateUser,lastUpdateDate)
      VALUES (${h.sqlString(providerNo)},${h.sqlString(s.marker)},'Login','doctor','','','M','1',${h.sqlString(s.provider)},NOW())`);
    sql.execute(`INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate)
      VALUES (${h.sqlString(providerNo)},'doctor','R0000001',1,NOW())`);
    login.securityNo = sql.value(`INSERT INTO security (user_name,password,provider_no,pin,b_ExpireSet,date_ExpireDate,b_LocalLockSet,
      b_RemoteLockSet,forcePasswordReset,lastUpdateUser,usingMfa)
      VALUES (${h.sqlString(userName)},${h.sqlString(hash)},${h.sqlString(providerNo)},${h.sqlString(pin)},0,'2100-01-01',1,1,0,
      ${h.sqlString(s.provider)},0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(login.securityNo), 'The security fixture was not created');
    return login;
  };
  const record = seed('k');
  const row = () => sql.value(`SELECT CONCAT(forcePasswordReset,'|',b_ExpireSet) FROM security WHERE security_no=${record.securityNo}`);
  const b = await openSecondSession(s, { label: 'second-session', openMaster: false });
  let refused = null; // session A's edit page, left on the conflict page by the stale save

  await s.step('session A\'s stale Update Record does not undo session B\'s force-password-reset', async () => {
    const a = await openEditPage(s.context, s.schedule, s.recorder, 'security-admin-a2', record.userName);
    const other = await openEditPage(b.context, b.schedule, s.recorder, 'security-admin-b2', record.userName);
    await other.frame.locator('select[name="forcePasswordReset"]').selectOption('1');
    await navigateFrame(other.admin, other.frame, other.frame.locator('input[name="subbutton"]'));
    h.assert(row() === '1|0', 'Session B\'s force-password-reset did not reach the database');
    await a.frame.locator('input[name="b_ExpireSet"]').check();
    const mark = failureMark(s.recorder);
    const response = a.admin.waitForResponse(r => r.request().method() === 'POST'
      && /\/admin\/SecurityUpdate$/.test(new URL(r.url()).pathname), { timeout: TIMEOUT });
    await navigateFrame(a.admin, a.frame, a.frame.locator('input[name="subbutton"]'));
    const status = (await response).status();
    const [force, expire] = row().split('|');
    h.assert(force === '1',
      `Session A's stale Update Record (expiry flag now ${expire}) silently cleared the force-password-reset session B had just set (forcePasswordReset=${force}). `
      + 'The save copies every posted control onto the row; without comparing the form\'s securityVersion with the locked row, the later save wins '
      + 'without a warning to either administrator.');
    // A stale form is refused whole: HTTP 409 and the conflict page, with none of its fields stored (not even the
    // expiry flag session A ticked), and an offer to reopen the record as it is now.
    h.assert(status === 409, `The stale Update Record answered HTTP ${status} instead of refusing it with 409`);
    h.assert(expire === '0', 'The refused stale Update Record still stored part of its form (the expiry flag): a refusal must write nothing');
    await a.frame.locator('#securityEditConflict').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(!/Security Record Updated Successfully/.test(await a.frame.locator('body').innerText()),
      'The refused stale Update Record still reported success');
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/admin\/SecurityUpdate$/ });
    const reopen = a.frame.locator('#securityEditConflictReopen');
    h.assert(await reopen.count() === 1
      && new URL(await reopen.getAttribute('href'), a.frame.url()).searchParams.get('keyword') === record.securityNo,
    'The conflict page does not offer to reopen the login it refused to overwrite');
    refused = { ...a, reopen };
  });

  await s.step('the reopened record shows session B\'s reset and session A\'s change then saves', async () => {
    const a = refused;
    h.assert(a, 'The stale-save step did not leave its conflict page to reopen from');
    const mark = failureMark(s.recorder);
    await navigateFrame(a.admin, a.frame, a.reopen);
    consumeKnownConsole(s.recorder, mark, /bcArStyle\.css/);
    h.assert(await a.frame.locator('input[name="user_name"]').inputValue() === record.userName, 'The reopened edit page shows another login');
    h.assert(await a.frame.locator('select[name="forcePasswordReset"]').inputValue() === '1',
      'The reopened edit page does not show the force-password-reset session B saved');
    await a.frame.locator('input[name="b_ExpireSet"]').check();
    await navigateFrame(a.admin, a.frame, a.frame.locator('input[name="subbutton"]'));
    h.assert(/Security Record Updated Successfully/.test(await a.frame.locator('h2').first().innerText()),
      'Session A\'s update from the reopened record was not accepted');
    h.assert(row() === '1|1', `Session A's update from the reopened record stored ${row()} instead of keeping B's reset and adding the expiry flag (1|1)`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-security-record', workflow, { openPatient: false });
