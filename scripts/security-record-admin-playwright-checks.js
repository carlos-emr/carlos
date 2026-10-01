#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Security (login) record administration: coverage plan §2.2 security-record-admin.
 *
 * User path: Schedule ▸ Administration ▸ User Management ▸ Search/Edit/Delete
 * Security Records (admin/ViewSecuritySearchRecordsHtm in the #myFrame iframe)
 * ▸ search by user name ▸ click the user name ▸ Update Record (admin/SecurityUpdate)
 * ▸ search again ▸ Delete Record (admin/SecurityDelete).
 *
 * Asserted: the search lists exactly the owned login; the edit page reflects the
 * row; saving a rename plus two flag changes writes exactly those columns (the
 * password/PIN sentinels leave the credentials untouched); the renamed login can
 * still sign in; both mutators refuse GET without writing; the delete removes the
 * row, the search no longer lists it, and the deleted login is refused at the
 * front door (lands on /loginfailed, never on the schedule).
 *
 * Fixtures: one marker-named provider row, its security row (password hash and PIN
 * copied from the configured test login so config.testPassword/testPin sign it in)
 * and one doctor role, all seeded by SQL and removed by cleanup, which asserts they
 * are gone. No demo row is touched.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, dataTableRows } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { randomInt } = require('node:crypto');

const TIMEOUT = 20000;
const LINK = 'Search/Edit/Delete Security Records';

// provider_no is varchar(6); choose a high value nobody holds in provider or security.
function pickUnusedProviderNo(sql) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const candidate = String(randomInt(700000, 999000));
    const used = sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM security WHERE provider_no=${h.sqlString(candidate)})`);
    if (used === '0') return candidate;
  }
  h.assert(false, 'No unused provider number was found in the fixture range');
  return null;
}

// Open a User Management section through the Administration left nav and hand back
// the frame the shell injected for it (.xlink replaces #myFrame on every click).
async function openSection(admin, selector) {
  const link = admin.locator('#adminNav').getByRole('link', { name: LINK, exact: true, includeHidden: true });
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The security search iframe did not load');
  await frame.locator(selector).waitFor();
  return frame;
}

// Submit or click inside the frame and wait for the frame's own navigation.
async function navigateFrame(admin, frame, locator) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
  navigated.catch(() => {});
  await locator.click();
  await navigated;
  await frame.waitForLoadState('networkidle', { timeout: TIMEOUT });
}

async function searchByUserName(admin, frame, userName) {
  await frame.locator('input[name="search_mode"][value="search_username"]').check();
  await frame.locator('input[name="keyword"]').fill(userName);
  // securitysearchrecordshtm.jsp nests the form inside a <table>, so the parser
  // foster-parents it and its controls are not DOM descendants of the form.
  await navigateFrame(admin, frame, frame.locator('input[name="button"], button[name="button"]').first());
  return dataTableRows(frame, '#tblResults', { timeout: TIMEOUT });
}

async function workflow(s) {
  const { sql, context, config, recorder } = s;
  // user_name must match [a-zA-Z0-9]{1,30}; the marker's hex tail keeps it per-run.
  const userName = 'pw' + s.marker.slice(-16);
  const renamed = userName + 'r';
  const providerNo = pickUnusedProviderNo(sql);
  let securityNo;
  s.cleanup(() => {
    const no = h.sqlString(providerNo);
    sql.execute(`DELETE FROM security WHERE provider_no=${no} AND user_name IN (${h.sqlString(userName)},${h.sqlString(renamed)})`);
    for (const table of ['secUserRole', 'program_provider', 'provider_facility', 'providersite', 'property']) {
      sql.execute(`DELETE FROM ${table} WHERE provider_no=${no}`);
    }
    sql.execute(`DELETE FROM provider WHERE provider_no=${no} AND last_name=${h.sqlString(s.marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM security WHERE provider_no=${no})
      + (SELECT COUNT(*) FROM secUserRole WHERE provider_no=${no}) + (SELECT COUNT(*) FROM provider WHERE provider_no=${no})`) === '0',
    'Owned security/provider fixtures were not removed');
  });
  const [[hash, pin]] = sql.rows(`SELECT password,pin FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
  h.assert(hash && pin, 'The configured test login has no stored password hash and PIN to copy');
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,team,sex,status,lastUpdateUser,lastUpdateDate)
    VALUES (${h.sqlString(providerNo)},${h.sqlString(s.marker)},'Login','doctor','','','M','1',${h.sqlString(s.provider)},NOW())`);
  sql.execute(`INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate)
    VALUES (${h.sqlString(providerNo)},'doctor','R0000001',1,NOW())`);
  securityNo = sql.value(`INSERT INTO security (user_name,password,provider_no,pin,b_ExpireSet,date_ExpireDate,b_LocalLockSet,
    b_RemoteLockSet,forcePasswordReset,lastUpdateUser,usingMfa)
    VALUES (${h.sqlString(userName)},${h.sqlString(hash)},${h.sqlString(providerNo)},${h.sqlString(pin)},0,'2100-01-01',1,1,0,
    ${h.sqlString(s.provider)},0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(securityNo), 'The security fixture was not created');
  // Every column the update page can touch except the audit timestamp.
  const snapshotQuery = `SELECT user_name,password,provider_no,pin,b_ExpireSet,date_ExpireDate,b_LocalLockSet,b_RemoteLockSet,
    COALESCE(forcePasswordReset,'NULL'),COALESCE(passwordUpdateDate,'NULL'),COALESCE(pinUpdateDate,'NULL'),COALESCE(lastUpdateUser,'NULL'),
    COALESCE(oneIdKey,'NULL'),COALESCE(oneIdEmail,'NULL'),COALESCE(delegateOneIdEmail,'NULL'),usingMfa,COALESCE(mfaSecret,'NULL')
    FROM security WHERE security_no=${securityNo}`;
  const snapshot = () => sql.rows(snapshotQuery)[0];
  const before = snapshot();
  h.assert(before && before[0] === userName, 'The security fixture snapshot is incomplete');

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'security-admin', timeout: TIMEOUT });
  let frame;
  await s.step('search by user name lists exactly the owned login with its provider number', async () => {
    frame = await openSection(admin, 'input[name="keyword"]');
    const { rows } = await searchByUserName(admin, frame, userName);
    const row = rows.filter({ has: frame.getByRole('link', { name: userName, exact: true }) });
    h.assert(await row.count() === 1, 'Search did not list exactly the owned login');
    h.assert((await row.locator('td').nth(2).innerText()).trim() === providerNo, 'The listed login shows the wrong provider number');
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Searching modified the security record');
  });
  await s.step('the edit page opens the owned record and reflects its stored flags', async () => {
    await navigateFrame(admin, frame, frame.getByRole('link', { name: userName, exact: true }));
    h.assert(new URL(frame.url()).searchParams.get('keyword') === securityNo, 'The edit page opened a record other than the owned login');
    // The update form is also table-nested, so its inputs are located from the frame.
    const form = frame;
    h.assert(await form.locator('input[name="user_name"]').inputValue() === userName, 'The edit page shows the wrong user name');
    h.assert(await form.locator('input[name="provider_no"]').inputValue() === providerNo, 'The edit page shows the wrong provider number');
    h.assert(await form.locator('input[name="security_no"]').inputValue() === securityNo, 'The edit page carries the wrong security_no');
    h.assert(!(await form.locator('input[name="b_ExpireSet"]').isChecked()), 'The expiry flag was shown set for an unexpiring login');
    h.assert(await form.locator('input[name="b_RemoteLockSet"]').isChecked() && await form.locator('input[name="b_LocalLockSet"]').isChecked(),
      'The PIN lock flags were not shown as stored');
    h.assert(await form.locator('input[name="date_ExpireDate"]').inputValue() === '2100-01-01', 'The expiry date was not shown as stored');
  });
  await s.step('Update Record saves the rename and flag changes and nothing else', async () => {
    const form = frame;
    await form.locator('input[name="user_name"]').fill(renamed);
    await form.locator('input[name="b_ExpireSet"]').check();
    await form.locator('input[name="b_RemoteLockSet"]').uncheck();
    await navigateFrame(admin, frame, form.locator('input[name="subbutton"]'));
    h.assert(/Security Record Updated Successfully/.test(await frame.locator('h2').first().innerText())
      && new URL(frame.url()).pathname.endsWith('/admin/SecurityUpdate'), 'The update did not report success');
    const expected = [...before];
    expected[0] = renamed; expected[4] = '1'; expected[7] = '0';
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(expected),
      'The security row did not change exactly as submitted (credentials, expiry date or other flags moved)');
    h.assert(sql.value(`SELECT lastUpdateDate >= NOW() - INTERVAL 5 MINUTE FROM security WHERE security_no=${securityNo}`) === '1',
      'The update did not stamp lastUpdateDate');
  });
  await s.step('the renamed login still signs in with the unchanged password and PIN', async () => {
    const ctx2 = await h.newContext(context.browser(), config);
    ctx2.on('page', p => h.wireStrictPage(p, 'security-renamed-login', recorder));
    try {
      const schedule = await h.login(ctx2, { ...config, testUser: renamed }, recorder, { label: 'security-renamed-login' });
      h.assert(/providercontrol|appointment/i.test(schedule.url()), 'The renamed login did not reach the schedule');
    } finally { await ctx2.close(); }
  });
  await s.step('SecurityUpdate and SecurityDelete refuse GET without writing', async () => {
    const after = snapshot();
    const update = await context.request.get(h.appUrl(config.baseUrl, '/admin/SecurityUpdate'), {
      params: { security_no: securityNo, user_name: userName + 'g', provider_no: providerNo, password: 'Rejected1!', conPassword: 'Rejected1!',
        pin: '0000', conPin: '0000', b_ExpireSet: '1', date_ExpireDate: '2000-01-01' }, maxRedirects: 0 });
    h.assert(update.status() === 405, `SecurityUpdate answered GET with HTTP ${update.status()}, expected 405`);
    const remove = await context.request.get(h.appUrl(config.baseUrl, '/admin/SecurityDelete'), { params: { keyword: securityNo }, maxRedirects: 0 });
    h.assert(remove.status() === 405 && remove.headers().allow === 'POST', 'SecurityDelete must reject GET with Allow: POST');
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(after), 'A rejected GET changed the security record');
  });
  await s.step('Delete Record removes the security row and leaves the provider', async () => {
    frame = await openSection(admin, 'input[name="keyword"]');
    await searchByUserName(admin, frame, renamed);
    await navigateFrame(admin, frame, frame.getByRole('link', { name: renamed, exact: true }));
    h.assert(await frame.locator('form#deleteSecurityForm').count() === 1
      && await frame.locator('input[name="keyword"]').inputValue() === securityNo,
      'The delete form targets a record other than the owned login');
    // securityupdatesecurity.jsp submits #deleteSecurityForm directly: no confirm() is raised.
    await navigateFrame(admin, frame, frame.locator('input[type="button"][value="Delete Record"]'));
    const landed = h.pathOnly(frame.url());
    const reported = (await frame.locator('h2').first().innerText().catch(() => '')).trim();
    h.assert(landed.endsWith('/admin/SecurityDelete') && reported === `Security entry deleted for user: ${renamed}`,
      `The delete did not report the deleted login (landed on ${landed}, heading "${reported}")`);
    h.assert(sql.value(`SELECT COUNT(*) FROM security WHERE security_no=${securityNo} OR user_name IN (${h.sqlString(userName)},${h.sqlString(renamed)})`) === '0',
      'The security row was not deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(providerNo)}`) === '1', 'Deleting the login removed the provider');
  });
  await s.step('search no longer lists the deleted login', async () => {
    frame = await openSection(admin, 'input[name="keyword"]');
    await searchByUserName(admin, frame, renamed);
    h.assert(await frame.locator('#tblResults a[href*="ViewSecurityUpdateSecurity"]').count() === 0, 'The deleted login is still listed');
  });
  await s.step('the deleted login is refused at the front door', async () => {
    const ctx3 = await h.newContext(context.browser(), config);
    ctx3.on('page', p => h.wireStrictPage(p, 'security-deleted-login', recorder));
    try {
      const page = await ctx3.newPage();
      await h.gotoApp(page, config.baseUrl, '/');
      await page.locator('#username').fill(renamed);
      await page.locator('#password').fill(config.testPassword);
      await page.locator('#pin').fill(config.testPin);
      // Login2Action sends an unknown login back to /index?login=failed (expired or
      // locked ones to /loginfailed); either is the front door, never the schedule.
      await Promise.all([
        page.waitForURL(/login=failed|loginfailed|providercontrol|appointment|forcepasswordreset|loginMfa|select_facility/i, { timeout: TIMEOUT }),
        page.locator('input[type="submit"], button[type="submit"]').first().click(),
      ]);
      await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      const url = new URL(page.url());
      const refused = url.searchParams.get('login') === 'failed' || url.pathname.endsWith('/loginfailed');
      h.assert(refused, `The deleted login reached ${h.pathOnly(page.url())} instead of the failed-login page`);
      const body = await page.locator('body').innerText();
      h.assert(/Login failed\. Username or Password is incorrect\.|Please correct and try again/.test(body)
        && await page.locator('a.adhour').count() === 0, 'The deleted login was not refused');
      if (url.searchParams.get('login') === 'failed') h.assert(await page.locator('#username').isVisible(), 'The login form was not offered again');
    } finally { await ctx3.close(); }
  });
}
if (require.main === module) runWorkflow('security-record-admin', workflow, { openPatient: false });
module.exports = { workflow, openSection, navigateFrame, searchByUserName };
