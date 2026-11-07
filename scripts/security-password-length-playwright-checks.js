#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Security Records password edits preserve 20/32-character values, reject values
 * beyond the character/encoder limits, and never partially update a rejected record.
 * Where MFA and legacy PINs are both offered, a refused edit with MFA ticked shows the
 * disabled PIN as the unchanged sentinel and can be resubmitted as drafted.
 * Uses an owned provider/login, inherited test-account roles, and application saves.
 * BASE_URL, TEST_*, MYSQL_* and CHROME_PATH use the shared harness configuration.
 */
'use strict';
const {randomBytes} = require('node:crypto');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
const {failureMark, consumeExpectedFailure, consumeKnownConsole} = require('./lib/concurrency-support');

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
        // SecurityUpdate2Action refuses invalid input with HTTP 400 and re-renders the edit form
        // with the error and the submitted draft, uncached, instead of a success page.
        h.assert(response.status() === 400 && message.test(html) && !/Security Record Updated Successfully/.test(html),
          `A rejected direct update did not show its validation error (HTTP ${response.status()})`);
        h.assert(/id="security-edit-refused"/.test(html) && html.includes(`value="${rejectedUserName}"`),
          'A rejected direct update did not keep the submitted draft');
        h.assert(/no-store/.test(response.headers()['cache-control'] || ''), 'A rejected credential draft must not be cached');
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

  // Runs last: it leaves MFA enabled on the throwaway login, which the fixture then removes.
  await s.step('a refused edit with MFA ticked keeps the disabled PIN unchanged and resubmits as drafted', async () => {
    await openEdit();
    const mfa = page.locator('input[name="enableMfa"]');
    const pin = page.locator('input[name="pin"]');
    const conPin = page.locator('input[name="conPin"]');
    if (await mfa.count() === 0 || await pin.count() === 0 || await pin.isDisabled()) {
      // MFA can only disable the PIN in a draft where both controls are offered.
      console.log('security-password-length: MFA or legacy PIN controls are not offered; MFA draft step not applicable');
      return;
    }
    const before = credentials();
    await mfa.check(); // its onchange disables the PIN controls, so the browser stops submitting them
    h.assert(await pin.isDisabled() && await conPin.isDisabled(), 'Ticking MFA did not disable the PIN controls');
    // The form's own checks allow punctuation in a user name; SecurityUpdate2Action refuses it with the draft.
    await page.locator('input[name="user_name"]').fill(`${account.username}!`);
    const isUpdate = r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/admin/SecurityUpdate');
    const mark = failureMark(recorder);
    const [refusal] = await Promise.all([page.waitForResponse(isUpdate), page.locator('input[name="subbutton"]').click()]);
    h.assert(refusal.status() === 400, `The refused edit answered HTTP ${refusal.status()}`);
    await page.locator('#security-edit-refused').waitFor();
    consumeKnownConsole(recorder, mark, /bcArStyle\.css/);
    consumeExpectedFailure(recorder, mark, {status: 400, path: /\/admin\/SecurityUpdate$/});
    h.assert(credentials() === before, 'The refused edit changed the password or PIN');
    // No PIN was submitted, which means "unchanged": the draft must carry the unchanged sentinel,
    // not a blank PIN that the form's own PIN check then refuses on resubmission.
    h.assert(await pin.isDisabled() && await pin.inputValue() === '****' && await conPin.inputValue() === '****',
      'The refused draft replaced the unchanged-PIN sentinel with a blank PIN');
    await page.locator('input[name="user_name"]').fill(account.username);
    const posted = page.waitForResponse(isUpdate, {timeout: 15000}).catch(() => null);
    const dialogs = await h.withExpectedDialogs(page, () => page.locator('input[name="subbutton"]').click());
    h.assert(dialogs.length === 0, `The draft could not be resubmitted as drafted: ${dialogs.map(d => d.text).join(' | ')}`);
    const accepted = await posted;
    h.assert(accepted && accepted.status() === 200, 'The resubmitted draft was not accepted');
    await page.waitForLoadState('domcontentloaded');
    h.assert(/Security Record Updated Successfully/.test(await page.locator('body').innerText()), 'The resubmitted draft did not report success');
    h.assert(credentials() === before, 'Resubmitting the draft changed the password or PIN');
    h.assert(String(sql.value(`SELECT usingMfa FROM security WHERE security_no=${account.securityNo}`)) === '1',
      'The resubmitted draft did not enable MFA');
  });
}

if (require.main === module) runWorkflow('security-password-length', workflow, {openPatient: false});
module.exports = {workflow};
