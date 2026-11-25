#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture, submitLoginForm } = require('./lib/throwaway-login-fixture');
const { settleOperations } = require('./graceful-signal-cancellation');

function ensureIntakeAssignment(sql, providerNo) {
  const role = 'Patient Intake';
  const activeCount = () => sql.value(`SELECT COUNT(*) FROM secUserRole
    WHERE provider_no=? AND role_name=? AND activeyn=1`, [providerNo, role]);
  // Keep the template's active assignments and add the role only when it was absent.
  if (activeCount() === '0') {
    sql.execute(`INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate)
      SELECT ?,?,MIN(orgcd),1,NOW() FROM secUserRole WHERE provider_no=?`,
    [providerNo, role, providerNo]);
  }
  h.assert(Number(activeCount()) > 0, 'The throwaway login does not carry the retained Patient Intake assignment');
}

async function assertScheduleLanding(page, providerNo) {
  h.assert(/providercontrol|appointment/i.test(new URL(page.url()).pathname),
    'The retained role did not reach the supported schedule');
  await page.waitForLoadState('domcontentloaded');
  h.assert(!h.isApplicationErrorPage(await page.content()), 'The schedule landing displayed an application error');
  const displayedProvider = page.locator('#scheduleLoggedInProviderNo');
  h.assert(await displayedProvider.count() === 1 && await displayedProvider.inputValue() === providerNo,
    'The schedule landing did not render the expected provider');
}

async function workflow(s) {
  const fixture = throwawayLoginFixture({ sql: s.sql, marker: s.marker,
    provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  ensureIntakeAssignment(s.sql, fixture.providerNo);

  async function freshContext() {
    const context = await h.newContext(s.context.browser(), s.config);
    s.cleanup(() => context.close());
    context.on('page', page => h.wireStrictPage(page, 'legacy-intake', s.recorder));
    return context;
  }

  await s.step('a retained Patient Intake assignment reaches the supported schedule after ordinary login', async () => {
    const context = await freshContext();
    const page = await h.login(context, { ...s.config, testUser: fixture.username }, s.recorder);
    await assertScheduleLanding(page, fixture.providerNo);
  });

  await s.step('the retained role reaches the supported schedule after completing a forced password reset', async () => {
    const context = await freshContext();
    s.sql.execute('UPDATE security SET forcePasswordReset=1 WHERE security_no=?', [fixture.securityNo]);
    const { page, landing } = await submitLoginForm(context, s.config,
      { username: fixture.username, password: s.config.testPassword, pin: s.config.testPin });
    h.assert(/\/forcepasswordreset$/.test(landing), 'The forced-reset fixture did not reach password reset');
    const replacement = ['Changed', s.marker.slice(-8), 'Aa8!'].join('');
    await page.locator('input[name="oldPassword"]').fill(s.config.testPassword);
    await page.locator('input[name="newPassword"]').fill(replacement);
    await page.locator('input[name="confirmPassword"]').fill(replacement);
    await settleOperations([
      page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
      page.locator('input[type="submit"]').click(),
    ]);
    await assertScheduleLanding(page, fixture.providerNo);
    h.assert(s.sql.value('SELECT forcePasswordReset FROM security WHERE security_no=?', [fixture.securityNo]) === '0',
      'The completed reset did not clear its pending flag');
    h.assert(s.sql.value('SELECT COUNT(*) FROM security WHERE security_no=? AND password=?',
      [fixture.securityNo, fixture.passwordHash]) === '0',
    'The completed reset retained the original password hash');
    const newContext = await freshContext();
    const signedIn = await h.login(newContext,
      { ...s.config, testUser: fixture.username, testPassword: replacement }, s.recorder);
    await assertScheduleLanding(signedIn, fixture.providerNo);
  });
}

if (require.main === module) runWorkflow('login-legacy-intake', workflow, { openPatient: false });
module.exports = { assertScheduleLanding, ensureIntakeAssignment, workflow };
