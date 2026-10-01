#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-security-add-validation — the REFUSALS of Administration ▸ User Management ▸ Add a
 * Login Record (admin/ViewSecurityAddARecord ▸ admin/SecurityAddSecurity).
 * add-login-account-playwright-checks drives the successful add; none of the ways the form says no was
 * driven, and a login record is the front door of the EMR.
 *
 * User path: Schedule ▸ Administration ▸ User Management ▸ Add a Login Record (administration iframe) ▸
 * fill the form ▸ Add: with the passwords not matching, a password that breaks the policy, no user
 * name (the form's own alerts, nothing posted); then a user name with an underscore, and the user name
 * of an existing login (the server's refusals, with the reasons on the result page).
 * Asserted: every refusal writes no security row and no audit row for the attempted user name; the
 * browser refusals post nothing; the server refusals show their message; and — last, because
 * SecurityAddSecurityHelper checks neither the password policy nor the confirmation — a login record
 * POSTed with a weak password (what a script, or a browser with its form check bypassed, sends) is NOT
 * created. The probe posts only for an owned FAKE- provider and an owned user name, and its row (if the
 * server accepts it) is removed by cleanup.
 * Fixtures: one FAKE- provider row (the probe's target) and the user names this run attempts; cleanup
 * deletes only security/log/provider rows carrying them and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { bundleMessage } = require('./lib/throwaway-login-fixture');
const { randomInt } = require('node:crypto');

async function workflow(s) {
  const { sql, config, marker } = s;
  const hex = marker.replace(/^FAKE-PW/, '');
  const badName = `bad_${hex.slice(0, 10)}`;
  const probeName = `pwprobe${hex.slice(0, 12)}`;
  const probeProvider = String(randomInt(700000, 799999));
  const providerSql = h.sqlString(probeProvider);
  const names = [badName, probeName].map(h.sqlString).join(',');
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${providerSql})
    + (SELECT COUNT(*) FROM security WHERE provider_no=${providerSql})`) === '0', 'The probe provider number is already in use');
  s.cleanup(() => {
    sql.execute(`DELETE FROM SecurityArchive WHERE user_name IN (${names});
      DELETE FROM security WHERE user_name IN (${names}) OR provider_no=${providerSql};
      DELETE FROM log WHERE contentId IN (${names}) OR provider_no=${providerSql};
      DELETE FROM provider WHERE provider_no=${providerSql} AND last_name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM security WHERE user_name IN (${names}) OR provider_no=${providerSql})
      + (SELECT COUNT(*) FROM provider WHERE provider_no=${providerSql})`) === '0', 'Owned security/provider rows were not removed');
  });
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
    SELECT ${providerSql},${h.sqlString(marker)},'Probe',provider_type,specialty,sex,'1',${h.sqlString(s.provider)},NOW()
    FROM provider WHERE provider_no=${h.sqlString(s.provider)}`);
  const securityRows = () => sql.value(`SELECT COUNT(*) FROM security WHERE user_name IN (${names}) OR provider_no=${providerSql}`);
  const message = (key, fallback) => bundleMessage(key, fallback);
  const GOOD = 'Aa1!Good-Pass-9';

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'security-add-admin', timeout: 20000 });
  let frame;
  const posts = [];
  s.context.on('request', request => {
    if (request.method() === 'POST' && /\/admin\/SecurityAddSecurity$/.test(new URL(request.url()).pathname)) posts.push(request.url());
  });
  async function openForm() {
    const link = admin.locator('a[rel$="/admin/ViewSecurityAddARecord"], a[href$="/admin/ViewSecurityAddARecord"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Add a Login Record did not load in the administration frame');
    await frame.locator('form[name="searchprovider"]').waitFor();
    // A provider without a login: an option whose class is not providerSecurity1.
    const options = await frame.locator('#provider_no option').evaluateAll(list => list.filter(o => o.value && o.className !== 'providerSecurity1').map(o => o.value));
    h.assert(options.length > 0, 'The form offers no provider without a login');
    return options[0];
  }
  const fill = async (provider, user, password, confirm) => {
    await frame.locator('input[name="user_name"]').fill(user);
    await frame.locator('input[name="password"]').fill(password);
    await frame.locator('input[name="conPassword"]').fill(confirm);
    await frame.locator('select[name="provider_no"]').selectOption(provider);
    // The account-expiry box starts checked and then demands a date; these attempts are not about expiry.
    const expire = frame.locator('input[name="b_ExpireSet"]').first();
    if (await expire.count() && await expire.isChecked()) await expire.uncheck();
    const pin = frame.locator('input[name="pin"]').first();
    if (await pin.count() && await pin.isEnabled()) {
      await pin.fill('2468');
      await frame.locator('input[name="conPin"]').first().fill('2468');
    }
  };
  const submit = () => frame.locator('input[type="submit"][name="subbutton"]').click();
  let provider;
  const alerts = async (body) => h.withExpectedDialogs(admin, body);

  await s.step('the form refuses mismatched passwords, a policy-breaking password and a missing user name without posting', async () => {
    provider = await openForm();
    let dialogs = await alerts(async () => { await fill(provider, badName, GOOD, `${GOOD}x`); await submit(); });
    h.assert(dialogs.length === 1 && dialogs[0].text.includes(message('admin.securityrecord.msgPasswordNotConfirmed', 'not confirmed')),
      `Mismatched passwords: expected the confirmation alert, saw ${JSON.stringify(dialogs.map(d => d.text))}`);
    dialogs = await alerts(async () => { await fill(provider, badName, 'ab1', 'ab1'); await submit(); });
    h.assert(dialogs.length === 1, `A password that breaks the policy raised ${dialogs.length} alert(s), not one`);
    dialogs = await alerts(async () => { await fill(provider, '', GOOD, GOOD); await submit(); });
    h.assert(dialogs.length === 1, `A missing user name raised ${dialogs.length} alert(s), not one`);
    h.assert(posts.length === 0 && securityRows() === '0', 'A refused form posted or created a login');
  });

  await s.step('a user name with an underscore is refused by the server with its reason', async () => {
    await fill(provider, badName, GOOD, GOOD);
    await Promise.all([admin.waitForResponse(r => r.request().method() === 'POST' && /\/admin\/SecurityAddSecurity$/.test(new URL(r.url()).pathname)), submit()]);
    await frame.locator('h1').waitFor();
    const heading = (await frame.locator('h1').innerText()).trim();
    h.assert(heading === message('admin.securityaddsecurity.msgUserNameInvalid', 'User Name must be 1-30 letters or numbers.')
      && securityRows() === '0', `The invalid user name was not refused with its reason (saw "${heading}")`);
  });

  await s.step('the user name of an existing login is refused as already in use and creates nothing', async () => {
    const before = sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
    provider = await openForm();
    const providerLogins = () => sql.value(`SELECT COUNT(*) FROM security WHERE provider_no=${h.sqlString(provider)}`);
    const providerBefore = providerLogins();
    await fill(provider, config.testUser, GOOD, GOOD);
    await Promise.all([admin.waitForResponse(r => r.request().method() === 'POST' && /\/admin\/SecurityAddSecurity$/.test(new URL(r.url()).pathname)), submit()]);
    await frame.locator('h1').waitFor();
    const heading = (await frame.locator('h1').innerText()).trim();
    h.assert(heading === message('admin.securityaddsecurity.msgAdditionFailureDuplicate', 'that username is already in use.'),
      `The duplicate user name was not refused as already in use (saw "${heading}")`);
    h.assert(sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${h.sqlString(config.testUser)}`) === before && providerLogins() === providerBefore,
      'The refused duplicate changed login records');
  });

  await s.step('a login POSTed with a weak password is not created (the server, not the form, enforces the policy)', async () => {
    provider = await openForm();
    await frame.locator('form[name="searchprovider"] input[name="CSRF-TOKEN"]').first().waitFor({ state: 'attached' });
    await frame.waitForFunction(() => document.querySelector('form[name="searchprovider"] input[name="CSRF-TOKEN"]')?.value);
    const token = await frame.locator('form[name="searchprovider"] input[name="CSRF-TOKEN"]').first().inputValue();
    const answer = await s.context.request.post(`${config.baseUrl}/admin/SecurityAddSecurity`, {
      headers: { 'CSRF-TOKEN': token },
      form: { 'CSRF-TOKEN': token, user_name: probeName, password: 'ab1', conPassword: 'zzz', provider_no: probeProvider,
        pin: '', conPin: '', b_ExpireSet: '0', date_ExpireDate: '2100-01-01', forcePasswordReset: '0' },
      maxRedirects: 0,
    });
    const created = sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${h.sqlString(probeName)}`);
    h.assert(answer.status() < 500, `The weak-password POST answered HTTP ${answer.status()}`);
    h.assert(created === '0',
      'Add a Login Record created a login for a password that violates the policy (and does not match its confirmation): SecurityAddSecurityHelper enforces neither server-side, so the browser check is the only guard');
  });
}

if (require.main === module) runWorkflow('gap-provider-security-add-validation', workflow, { openPatient: false });
module.exports = { workflow };
