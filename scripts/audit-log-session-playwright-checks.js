#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of the session lifecycle: failed login, login, logout (wave 7 sweep `audit-log`).
 *
 * User path: login page > wrong password > Login (refused); login page > right credentials > Login >
 * Schedule; Schedule top bar > Logout (#logoutButton, the page that posts /logout).
 * Runs as a throwaway login (lib/throwaway-login-fixture.js) so every row in the audit log that carries its
 * provider number or username is its own and no other check's rows can be counted.
 *
 * Asserts: the refused attempt writes exactly one failed/login row naming the typed user name and the client
 * address and not the typed password; the successful login writes exactly ONE `log in`/login row for the
 * provider (one sign-in is one audit event, not two), with the provider number and the client address; the
 * logout writes exactly one `log out`/login row for the provider with the client address; the failed row
 * is not attributed to the provider of the later success. Every expectation is evaluated and the violated
 * ones are reported in the last step.
 *
 * Fixtures: the throwaway login and its audit rows; cleanup removes them and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { submitLoginForm, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { phiLeaks } = require('./lib/audit-log-helpers');

async function workflow(s) {
  const { sql, config, recorder } = s;
  const q = h.sqlString;
  const fixture = throwawayLoginFixture({ sql, marker: s.marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const { username } = fixture;
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  const columns = `id,COALESCE(provider_no,'~NULL~'),action,COALESCE(content,'~NULL~'),COALESCE(contentId,'~NULL~'),COALESCE(ip,'~NULL~'),COALESCE(data,'~NULL~')`;
  const rowsOf = where => sql.rows(`SELECT ${columns} FROM log WHERE ${where} ORDER BY id`)
    .map(([id, provider, action, content, contentId, ip, data]) => ({ id, provider, action, content, contentId, ip, data }));
  const failedWhere = `action='failed' AND content='login' AND contentId=${q(username)}`;
  const providerWhere = `provider_no=${q(fixture.providerNo)}`;
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  // LogAction.addLog commits on a background executor: poll until the expected row is visible, then wait a
  // beat more so a late duplicate is counted too. Returns the rows after the settle (possibly none on timeout).
  const rowsAfterWrite = async (where, timeout = 15000) => {
    const deadline = Date.now() + timeout;
    while (!rowsOf(where).length && Date.now() < deadline) await sleep(250);
    await sleep(1500);
    return rowsOf(where);
  };
  const context = await h.newContext(s.context.browser(), config);
  context.on('page', page => h.wireStrictPage(page, 'audit-log-session', recorder));
  s.cleanup(() => context.close());

  await s.step('a wrong password is refused and audited once, without the password', async () => {
    const wrong = `${s.marker}-wrong`;
    const { page, outcome } = await submitLoginForm(context, config, { username, password: wrong, pin: config.testPin });
    h.assert(outcome === 'failed', `The wrong password was not refused as a failed login (${outcome})`);
    await page.close();
    const failed = await rowsAfterWrite(failedWhere);
    h.assert(failed.length === 1, `The refused login wrote ${failed.length} failed/login rows, expected 1`);
    expect(Boolean(failed[0].ip) && failed[0].ip !== '~NULL~', 'The failed/login row carries no client address');
    expect(!phiLeaks([{ action: 'failed', content: 'login', contentId: failed[0].contentId, data: failed[0].data }], [wrong, config.testPassword]).length,
      'The failed/login row carries the typed password');
  });

  let schedule;
  await s.step('the right credentials sign in (log in rows observed)', async () => {
    schedule = await h.login(context, { ...config, testUser: username }, recorder, { label: 'audit-log-session-login' });
    const ins = await rowsAfterWrite(`${providerWhere} AND action='log in'`);
    h.assert(ins.length >= 1, 'The sign-in wrote no log in row');
    expect(ins.length === 1, `One sign-in wrote ${ins.length} log in rows (${ins.map(r => `contentId ${r.contentId === '~NULL~' ? 'empty' : 'set'}`).join(' + ')}), expected 1`);
    expect(ins.every(r => r.content === 'login' && Boolean(r.ip) && r.ip !== '~NULL~'), 'A log in row lacks the login content type or the client address');
    expect(rowsOf(`${failedWhere} AND provider_no=${q(fixture.providerNo)}`).length === 0, 'The failed row was attributed to the provider');
  });

  await s.step('Logout from the top bar signs out (log out rows observed)', async () => {
    const before = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM log'));
    await schedule.locator('#logoutButton').click();
    await schedule.waitForURL(/\/index|\/logout/, { timeout: 30000 });
    const outs = await rowsAfterWrite(`${providerWhere} AND action='log out' AND id>${before}`);
    expect(outs.length === 1, `Logging out wrote ${outs.length} log out rows, expected 1`);
    expect(outs.every(r => r.content === 'login' && Boolean(r.ip) && r.ip !== '~NULL~'), 'The log out row lacks the login content type or the client address');
  });

  await s.step('every expectation of the session audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the session lifecycle:\n  - ${defects.join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-session', workflow, { openPatient: false });
module.exports = { workflow };
