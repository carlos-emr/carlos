#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Two administrators edit the same owned login. A stale POST must return 409 without clearing B's
 * force-reset setting, preserve A's original version and draft (including entered credentials),
 * and refuse replay. A separate current-record view supports an intentional retry. Deletion
 * while editing returns 404 without resurrecting the login. Only owned fixtures are removed.
 */
const { randomInt, randomBytes } = require('node:crypto');
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

  let a;
  let originalVersion;
  let savedProof;
  const draftPassword = 'Aa1!' + randomBytes(8).toString('hex');
  const proof = () => sql.value(`SELECT SHA2(CONCAT_WS('|',user_name,password,pin,provider_no,b_ExpireSet,
    date_ExpireDate,b_LocalLockSet,b_RemoteLockSet,forcePasswordReset,usingMfa,COALESCE(mfaSecret,''),
    COALESCE(passwordUpdateDate,''),COALESCE(pinUpdateDate,''),COALESCE(lastUpdateDate,'')),256)
    FROM security WHERE security_no=${record.securityNo}`);
  async function submitRefused(editor, status) {
    const mark = failureMark(s.recorder);
    const response = editor.admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/admin/SecurityUpdate')
      && r.request().method() === 'POST', { timeout: TIMEOUT });
    await navigateFrame(editor.admin, editor.frame, editor.frame.locator('input[name="subbutton"]'));
    const result = await response;
    h.assert(result.status() === status, `Security edit must return exactly HTTP ${status}`);
    h.assert(/no-store/.test(result.headers()['cache-control'] || ''), 'A credential draft response must not be cached');
    await editor.frame.locator('#security-edit-refused').waitFor({ state: 'visible', timeout: TIMEOUT });
    consumeKnownConsole(s.recorder, mark, /bcArStyle\.css/);
    consumeExpectedFailure(s.recorder, mark, { status, path: /\/admin\/SecurityUpdate$/ });
  }

  await s.step('a stale edit retains the draft and cannot clear a newer force-password-reset', async () => {
    a = await openEditPage(s.context, s.schedule, s.recorder, 'security-admin-a', record.userName);
    originalVersion = await a.frame.locator('input[name="securityEditVersion"]').inputValue();
    h.assert(/^[a-f0-9]{64}$/.test(originalVersion), 'The original security state is missing');
    const other = await openEditPage(b.context, b.schedule, s.recorder, 'security-admin-b', record.userName);
    await other.frame.locator('select[name="forcePasswordReset"]').selectOption('1');
    await navigateFrame(other.admin, other.frame, other.frame.locator('input[name="subbutton"]'));
    h.assert(row() === '1|0', 'The newer force-password-reset did not reach the database');
    savedProof = proof();
    await a.frame.locator('input[name="b_ExpireSet"]').check();
    await a.frame.locator('input[name="password"]').fill(draftPassword);
    await a.frame.locator('input[name="conPassword"]').fill(draftPassword);
    await submitRefused(a, 409);
    h.assert(await a.frame.locator('input[name="b_ExpireSet"]').isChecked(), 'The refused expiry draft was lost');
    h.assert(await a.frame.locator('input[name="password"]').inputValue() === draftPassword
      && await a.frame.locator('input[name="conPassword"]').inputValue() === draftPassword, 'The entered credential draft was lost');
    h.assert(await a.frame.locator('input[name="securityEditVersion"]').inputValue() === originalVersion,
      'The refusal silently rebased the original version');
    h.assert(proof() === savedProof && row() === '1|0', 'The stale edit changed fields or credentials');
    h.assert(/changed|draft/i.test(await a.frame.locator('#security-edit-refused').innerText()), 'The conflict did not explain recovery');
  });

  await s.step('replaying the stale form remains refused, then a reviewed retry keeps the newer protection', async () => {
    await submitRefused(a, 409);
    h.assert(proof() === savedProof, 'Replaying the stale form changed the security row');
    const opened = s.context.waitForEvent('page');
    await a.frame.locator('#reviewCurrentSecurity').click();
    const current = await opened;
    await current.waitForLoadState('domcontentloaded');
    h.assert(await current.locator('select[name="forcePasswordReset"]').inputValue() === '1',
      'The current-record view does not show the newer protection');
    h.assert(await current.locator('input[name="securityEditVersion"]').inputValue() !== originalVersion,
      'Review did not load the current version');
    await current.locator('input[name="b_ExpireSet"]').check();
    await Promise.all([current.waitForURL(/\/admin\/SecurityUpdate$/), current.locator('input[name="subbutton"]').click()]);
    await current.locator('#security-update-ok').waitFor();
    h.assert(row() === '1|1', 'The reviewed expiry change did not preserve force-password-reset');
    h.assert(sql.value(`SELECT password=${h.sqlString(hash)} AND pin=${h.sqlString(pin)} FROM security
      WHERE security_no=${record.securityNo}`) === '1', 'The reviewed flag-only retry changed credentials');
    h.assert(await a.frame.locator('input[name="password"]').inputValue() === draftPassword,
      'The separate review discarded the original draft');
    await current.close();
  });

  await s.step('deleting an open login yields 404 and keeps its draft without resurrecting it', async () => {
    const deleted = seed('d');
    const stale = await openEditPage(s.context, s.schedule, s.recorder, 'security-deleted-a', deleted.userName);
    const remover = await openEditPage(b.context, b.schedule, s.recorder, 'security-deleted-b', deleted.userName);
    await navigateFrame(remover.admin, remover.frame, remover.frame.locator('input[type="button"][value="Delete Record"]'));
    h.assert(sql.value(`SELECT COUNT(*) FROM security WHERE security_no=${deleted.securityNo}`) === '0', 'Delete control did not remove the owned login');
    await stale.frame.locator('input[name="b_ExpireSet"]').check();
    await submitRefused(stale, 404);
    h.assert(await stale.frame.locator('input[name="b_ExpireSet"]').isChecked(), 'Deleted-row refusal lost the draft');
    h.assert(await stale.frame.locator('#reviewCurrentSecurity').count() === 0, 'Deleted-row refusal offers a nonexistent current record');
    h.assert(sql.value(`SELECT COUNT(*) FROM security WHERE security_no=${deleted.securityNo}
      OR user_name=${h.sqlString(deleted.userName)}`) === '0', 'The stale save resurrected a deleted login');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-security-record', workflow, { openPatient: false });
