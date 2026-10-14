#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Security Records password edits preserve 20/32-character values, reject values
 * beyond the character/encoder limits, and never partially update a rejected record.
 * Uses an owned provider/login, inherited test-account roles, and application saves.
 * BASE_URL, TEST_*, MYSQL_* and CHROME_PATH use the shared harness configuration.
 */
'use strict';
const {randomBytes} = require('node:crypto');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {throwawayLoginFixture} = require('./lib/throwaway-login-fixture');

async function workflow(s) {
  const {sql, context, config, recorder} = s;
  const account = throwawayLoginFixture({sql, marker: s.marker, provider: s.provider, testUser: config.testUser});
  const rejectedUserName = `${account.username}x`;
  s.cleanup(() => {
    // A regression in the rejected POST could apply our attempted rename. Restore
    // only that exact owned alias so the login fixture can still remove its row.
    if (account.securityNo) sql.execute(`UPDATE security SET user_name=${h.sqlString(account.username)}
      WHERE security_no=${account.securityNo} AND provider_no=${h.sqlString(account.providerNo)}
      AND user_name=${h.sqlString(rejectedUserName)} AND EXISTS
        (SELECT 1 FROM provider WHERE provider_no=${h.sqlString(account.providerNo)} AND last_name=${h.sqlString(s.marker)})`);
    account.cleanup();
  });
  account.create();
  const page = await context.newPage();
  const fields = () => ({password: page.locator('input[name="password"]'), confirmation: page.locator('input[name="conPassword"]')});
  async function openEdit() {
    await h.gotoApp(page, config.baseUrl, `/admin/ViewSecurityUpdateSecurity?keyword=${account.securityNo}`);
    await fields().password.waitFor();
  }
  async function fill(value, confirmation = value) {
    await fields().password.fill(value);
    await fields().confirmation.fill(confirmation);
  }
  async function save() {
    await Promise.all([
      page.waitForURL(/\/admin\/SecurityUpdate(?:\?|$)/),
      page.locator('input[name="subbutton"]').click(),
    ]);
    h.assert(/Security Record Updated Successfully/.test(await page.locator('body').innerText()), 'Security update did not report success');
  }
  const snapshot = () => JSON.stringify(sql.rows(`SELECT user_name,password,provider_no,pin,b_ExpireSet,date_ExpireDate,
    b_LocalLockSet,b_RemoteLockSet,passwordUpdateDate,pinUpdateDate,forcePasswordReset,usingMfa,lastUpdateDate
    FROM security WHERE security_no=${account.securityNo}`));
  const credentials = () => JSON.stringify(sql.rows(`SELECT password,pin,passwordUpdateDate,pinUpdateDate
    FROM security WHERE security_no=${account.securityNo}`));

  for (const length of [20, 32]) {
    await s.step(`the full ${length}-character password is saved and signs in`, async () => {
      await openEdit();
      const value = `Ab1!${randomBytes(16).toString('hex')}`.slice(0, length);
      await fill(value);
      h.assert(await fields().password.inputValue() === value && await fields().confirmation.inputValue() === value,
        `The edit form truncated the ${length}-character password`);
      await save();
      const fullContext = await h.newContext(context.browser(), config);
      fullContext.on('page', p => h.wireStrictPage(p, 'full-password-login', recorder));
      try { await h.login(fullContext, {...config, testUser: account.username, testPassword: value}, recorder); }
      finally { await fullContext.close(); }
      // The encoder unit tests also reject the old prefix. Avoid failed browser
      // logins here: some installations key the lockout counter by shared client IP.
    });
  }

  const overCharacters = `Ab1!${randomBytes(16).toString('hex')}`.slice(0, 33);
  const overBytes = `Ab1!${'\u20ac'.repeat(22)}xxx`; // 29 characters, 73 UTF-8 bytes.
  for (const [label, value, message] of [
    ['character limit', overCharacters, /no more than 32 characters/],
    ['encoder limit', overBytes, /too long to store/],
  ]) {
    await s.step(`the form explains the ${label}, keeps the full input and sends no update`, async () => {
      await openEdit();
      const before = snapshot();
      await fill(value);
      h.assert(await fields().password.inputValue() === value && await fields().confirmation.inputValue() === value,
        'An overlong password was silently truncated');
      let posted = false;
      const listener = req => { if (req.method() === 'POST' && h.pathOnly(req.url()).endsWith('/admin/SecurityUpdate')) posted = true; };
      page.on('request', listener);
      try {
        const dialogs = await h.withExpectedDialogs(page, () => page.locator('input[name="subbutton"]').click());
        h.assert(dialogs.length === 1 && message.test(dialogs[0].text), 'The length limit was not explained');
        h.assert(!posted && snapshot() === before, 'The rejected browser submission changed the security record');
      } finally { page.off('request', listener); }
    });
  }

  await s.step('direct POST validation rejects length, confirmation and policy errors before every account-field change', async () => {
    await openEdit();
    const form = await page.locator('form[name="updatearecord"]').evaluate(element => Object.fromEntries(new FormData(element)));
    const csrf = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const candidate = `Ab1!${randomBytes(8).toString('hex')}`;
    const cases = [
      [overCharacters, overCharacters, /no more than 32 characters/],
      [overBytes, overBytes, /too long to store/],
      [candidate, `${candidate}x`, /not confirmed/],
      ['', '', /required.*password requirements/],
      ['weak', 'weak', /required.*password requirements/],
      [null, null, /required.*password requirements/],
    ];
    for (const [password, confirmation, message] of cases) {
      const before = snapshot();
      const body = {...form, user_name: rejectedUserName, password, conPassword: confirmation,
        pin: '1234', conPin: '1234', b_LocalLockSet: '1', forcePasswordReset: '1', 'CSRF-TOKEN': csrf};
      if (password === null) { delete body.password; delete body.conPassword; }
      const response = await context.request.post(h.appUrl(config.baseUrl, '/admin/SecurityUpdate'), {
        headers: {'CSRF-TOKEN': csrf}, form: body,
      });
      try {
        const html = await response.text();
        h.assert(response.status() === 200 && message.test(html) && !/Security Record Updated Successfully/.test(html),
          'A rejected direct update did not show its validation error');
        h.assert(snapshot() === before, 'A rejected direct update changed account fields or credentials');
      } finally { await response.dispose(); }
    }
  });

  await s.step('an unchanged password/PIN sentinel preserves both credentials and their update dates', async () => {
    await openEdit();
    const before = credentials();
    await save();
    h.assert(credentials() === before, 'An unchanged sentinel rehashed or changed the existing credentials');
  });
}

if (require.main === module) runWorkflow('security-password-length', workflow, {openPatient: false});
module.exports = {workflow};
