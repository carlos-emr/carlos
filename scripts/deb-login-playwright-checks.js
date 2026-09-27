/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 * deb-login-playwright-checks.js — the first login on a PACKAGED install.
 *
 * A browser drives what an operator does right after `apt install` (or an
 * upgrade): open the front door, sign in with the credential the installer
 * wrote to /etc/carlos-emr/initial-admin.txt, land on the forced password
 * reset the package deliberately leaves in place, complete it, reach the
 * schedule, and sign in again with the new password. It proves the whole
 * chain the packages assemble -- nginx + ModSecurity in front, TLS, Tomcat,
 * the migrated schema, the reset administrator credential -- from the
 * outside, which `carlos-ctl check` probes from the inside.
 *
 * Run from any machine that can reach the host (a disposable install: the
 * reset CHANGES the administrator password):
 *
 *   ADMIN_USER=carlosdoc ADMIN_PASSWORD=... ADMIN_PIN=... \
 *   BASE_URL=https://<host> node scripts/deb-login-playwright-checks.js
 *
 * or, on the host itself, ADMIN_FILE=/etc/carlos-emr/initial-admin.txt in
 * place of the three values (the file is parsed for user/password/PIN).
 * NEW_PASSWORD sets the password the reset chooses (a generated one
 * otherwise; it is printed at the end so the host stays usable). The
 * certificate is the package's self-signed one by default, so TLS errors
 * are ignored unless STRICT_TLS=1.
 *
 * Exit status: the number of failed checks.
 */
'use strict';

const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

function loadPlaywright() {
  try {
    return require('playwright');
  } catch (err) {
    // the global install (npm root -g) when the repo has no node_modules
    const globalRoot = process.env.NODE_PATH
      || path.join(path.dirname(path.dirname(process.execPath)), 'lib', 'node_modules');
    return require(path.join(globalRoot, 'playwright'));
  }
}
const { chromium } = loadPlaywright();
const { clickAndAwaitReload } = require('./lib/playwright-ui');

const baseUrl = (process.env.BASE_URL || 'https://127.0.0.1').replace(/\/+$/, '');
const parsedBase = new URL(baseUrl);
if (parsedBase.username || parsedBase.password) {
  throw new Error('BASE_URL must not embed a username or password');
}
const appPath = '/carlos';
const appUrl = (p) => `${baseUrl}${appPath}${p}`;

function credentials() {
  let user = process.env.ADMIN_USER;
  let password = process.env.ADMIN_PASSWORD;
  let pin = process.env.ADMIN_PIN;
  if (process.env.ADMIN_FILE) {
    const text = fs.readFileSync(process.env.ADMIN_FILE, 'utf8');
    // "  user:  carlosdoc" lines; matched by splitting, not by a RegExp
    // assembled from the field name
    const field = (name) => {
      for (const line of text.split(/\r?\n/)) {
        const [key, ...rest] = line.trim().split(/\s+/);
        if (key === `${name}:` && rest.length === 1) return rest[0];
      }
      return undefined;
    };
    user = user || field('user');
    password = password || field('password');
    pin = pin || field('PIN');
  }
  if (!user || !password || !pin) {
    throw new Error('ADMIN_USER, ADMIN_PASSWORD and ADMIN_PIN (or ADMIN_FILE) are required');
  }
  return { user, password, pin };
}

function generatedPassword() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
  let out = '';
  for (let i = 0; i < 16; i += 1) {
    out += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  // the policy wants an upper, a lower, a digit and a symbol
  return `${out}!Aa1`;
}

async function bodyText(page) {
  return (await page.locator('body').innerText({ timeout: 10000 })).trim();
}

async function fillLogin(page, { user, password, pin }) {
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded' }); // nosemgrep
  await page.locator('#username').waitFor({ timeout: 15000 });
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(password);
  await page.locator('#pin').fill(pin);
  // armed before the click: the login POST always navigates (to the schedule,
  // the forced reset, or the login failure page), and a wait armed after the
  // click could be satisfied by the form that is already there
  await clickAndAwaitReload(page, page.locator('input[type="submit"], button[type="submit"]').first(),
    { label: 'the login button', timeout: 30000 });
}

async function expectSchedulePage(page, label) {
  await page.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/, { timeout: 30000 });
  const html = await page.content();
  assert(html.includes('/csrfguard'), `${label}: csrfguard script missing from the schedule page`);
  assert(/Schedule|appointment|provider/i.test(html), `${label}: did not look like a schedule page`);
}

(async () => {
  const creds = credentials();
  const newPassword = process.env.NEW_PASSWORD || generatedPassword();
  const strictTls = process.env.STRICT_TLS === '1';
  const results = [];
  const record = async (name, fn) => {
    try {
      await fn();
      results.push({ name, ok: true });
      console.log(`PASS ${name}`);
    } catch (err) {
      results.push({ name, ok: false, err });
      console.log(`FAIL ${name}: ${err && err.message ? err.message : err}`);
    }
  };

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: !strictTls });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

  try {
    await record('front door serves the login page over TLS', async () => {
      const response = await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded' }); // nosemgrep
      assert(response && response.status() === 200, `login page status ${response && response.status()}`);
      assert(parsedBase.protocol === 'https:', 'BASE_URL is not https');
      await page.locator('#username').waitFor({ timeout: 15000 });
      await page.locator('#password').waitFor({ timeout: 5000 });
      await page.locator('#pin').waitFor({ timeout: 5000 });
      const headers = response.headers();
      assert(headers['strict-transport-security'], 'no Strict-Transport-Security header from the front door');
    });

    await record('a wrong PIN is refused and the page stays on the login form', async () => {
      await fillLogin(page, { ...creds, pin: creds.pin === '0000' ? '0001' : '0000' });
      await page.waitForLoadState('domcontentloaded').catch(() => {});
      const text = await bodyText(page);
      assert(!/forcepasswordreset|providercontrol/.test(page.url()), `wrong PIN reached ${page.url()}`);
      assert(/invalid|incorrect|failed|denied|error/i.test(text) || (await page.locator('#username').count()) > 0,
        'wrong PIN produced neither an error nor the login form');
    });

    await record('the installer credential signs in and lands on the forced password reset', async () => {
      await fillLogin(page, creds);
      await page.waitForURL(/forcepasswordreset/, { timeout: 30000 });
      const text = await bodyText(page);
      assert(text.length > 0, 'forced reset page rendered blank');
      const action = await page.locator('form').first().getAttribute('action');
      assert(action && action.endsWith('/forcepasswordresetSubmit'), `reset form action was ${action}`);
      await page.locator('input[name="oldPassword"]').waitFor({ timeout: 10000 });
    });

    await record('completing the reset reaches the provider schedule', async () => {
      await page.locator('input[name="oldPassword"]').fill(creds.password);
      await page.locator('input[name="newPassword"]').fill(newPassword);
      await page.locator('input[name="confirmPassword"]').fill(newPassword);
      await clickAndAwaitReload(page, page.locator('input[type="submit"]').first(),
        { label: 'the password reset button', timeout: 30000 });
      await expectSchedulePage(page, 'after the forced reset');
    });

    await record('a fresh session signs in with the new password straight to the schedule', async () => {
      const fresh = await context.browser().newContext({ ignoreHTTPSErrors: !strictTls });
      const p2 = await fresh.newPage();
      try {
        await fillLogin(p2, { ...creds, password: newPassword });
        await expectSchedulePage(p2, 'second login');
      } finally {
        await fresh.close();
      }
    });

    await record('the old installer password no longer signs in', async () => {
      const fresh = await context.browser().newContext({ ignoreHTTPSErrors: !strictTls });
      const p3 = await fresh.newPage();
      try {
        await fillLogin(p3, creds);
        await p3.waitForLoadState('domcontentloaded').catch(() => {});
        assert(!/forcepasswordreset|providercontrol/.test(p3.url()), `old password reached ${p3.url()}`);
      } finally {
        await fresh.close();
      }
    });

    await record('no uncaught browser errors on the pages visited', async () => {
      const serious = consoleErrors.filter((t) => !/favicon|net::ERR_CERT/i.test(t));
      assert(serious.length === 0, `console errors: ${serious.slice(0, 3).join(' | ')}`);
    });
  } finally {
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok).length;
  console.log(`\n== ${results.length - failed} passed, ${failed} failed ==`);
  if (failed === 0) {
    console.log(`The administrator password is now the NEW_PASSWORD given or generated for this run.`);
    if (!process.env.NEW_PASSWORD) console.log(`generated password: ${newPassword}`);
  }
  process.exit(failed);
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
