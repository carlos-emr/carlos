#!/usr/bin/env node
/**
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
 */

/**
 * Shared browser-check harness for the clinical `scripts/*-playwright-checks.js`
 * family.
 *
 * WHY THIS MODULE EXISTS. Every check script needs the same five things: a
 * BASE_URL that cannot be pointed at a production host by accident, a URL
 * builder that cannot be talked into an absolute redirect, a page recorder that
 * turns silent HTTP 500s and browser exceptions into failures, a login that
 * survives both schedule landing pages, and a MySQL client for asserting what
 * actually reached the database. Before this module those five were copied into
 * every script, which is why a fix to one (for example the CSRFGuard console
 * noise filter) reached only the scripts someone remembered to edit.
 *
 * `eform-local-playwright-utils.js` is the eForm-specific counterpart and is
 * deliberately left alone: it carries eForm editor/attachment knowledge that
 * clinical checks must not inherit.
 *
 * SECURITY POSTURE. These checks drive a real EMR with write access. The guards
 * here are what keep a mis-set environment variable from writing to a clinic's
 * live system: loopback/private hosts only unless ALLOW_NON_LOCAL_BASE_URL is
 * set deliberately, no credentials in BASE_URL, root-relative application paths
 * only, and the MySQL password passed through a 0600 defaults file rather than
 * a command line every process on the host can read.
 */

const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

/**
 * Parses and constrains BASE_URL.
 *
 * The host allow-list is loopback plus RFC1918, which covers the devcontainer,
 * the packaged-install VM and a CI runner without ever covering a clinic's
 * public hostname. ALLOW_NON_LOCAL_BASE_URL is the single deliberate escape
 * hatch and is never set by the npm scripts.
 *
 * @param rawBaseUrl the BASE_URL value, with or without a trailing slash
 * @return a URL whose pathname has no trailing slash, ready for appUrl()
 */
function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Credentials in the URL would ride every navigation and land in failure
  // diagnostics; these checks log in through the form instead.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
  }

  const host = parsed.hostname.toLowerCase();
  const normalizedHost = host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(normalizedHost);
  if (!localHosts.has(normalizedHost) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }

  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

/**
 * Builds an absolute URL for an application path under BASE_URL.
 *
 * Rejecting anything that is not root-relative (and specifically `//host`) is
 * what lets callers pass a path assembled from an environment variable without
 * the result ever escaping the configured deployment.
 *
 * @param baseUrl the URL returned by validateBaseUrl()
 * @param appPath a root-relative path, optionally carrying a query string
 * @param query optional extra query parameters merged over appPath's own
 */
function appUrl(baseUrl, appPath, query = null) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

function createRecorder() {
  return {
    badResponses: [],
    consoleIssues: [],
    pageErrors: [],
    requestLog: [],
    dialogs: [],
  };
}

/**
 * 404s that a healthy CARLOS deployment serves anyway.
 *
 * Each entry is a legacy asset reference the application emits unconditionally;
 * they are enumerated rather than blanket-ignored so a genuinely missing asset
 * still fails the check that touches it.
 */
function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (
    responseUrl.endsWith('/favicon.ico')
    || /\/imageRenderingServlet\?/.test(responseUrl)
    || /\/eform\/displayImage\?imagefile=signature_pad\.min\.js(?:$|&)/.test(responseUrl)
    || /\/eform\/displayImage\?imagefile=BNK\.png(?:$|&)/.test(responseUrl)
    // The prevention grid probes for an admin-uploaded vaccine-brands.json in the
    // eForm image directory and falls back to the catalogue bundled in the WAR.
    // The probe 404s on every deployment that has not uploaded a custom
    // catalogue, which is most of them; the assertion that matters is that the
    // fallback loaded, and prevention-immunization-playwright-checks.js makes it.
    || /\/eform\/displayImage\?imagefile=vaccine-brands\.json(?:$|&)/.test(responseUrl)
  );
}

/**
 * Console output that is noise rather than a defect.
 *
 * CSRFGuard's client script narrates its own token rotation at info level on
 * every page, and the report-only CSP header is reported as a violation by
 * design. Treating either as a failure would make every check flaky.
 */
function isIgnorableConsoleMessage(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text)
    || /\[DOM\] Found \d+ elements with non-unique id/i.test(text)
    || /window\.print/i.test(text);
}

function isSevereConsoleMessage(message) {
  if (isIgnorableConsoleMessage(message)) {
    return false;
  }
  // Chromium reports every failed subresource load as a console error as well as
  // a response, so an asset the deployment is EXPECTED to probe for and not find
  // has to be excused in both places or the whitelist above buys nothing.
  const location = message.location() || {};
  if (/Failed to load resource/i.test(message.text()) && isExpectedMissingAsset(404, location.url || '')) {
    return false;
  }
  if (message.type() === 'error') {
    return true;
  }
  return /(ReferenceError|TypeError|SyntaxError|\$ is not defined|jQuery is not defined|Cannot read|Cannot set|is not defined|DataTables .* error)/i.test(message.text());
}

/**
 * Attaches the failure recorders to a page.
 *
 * Dialogs are dismissed, not accepted: a check that depends on a confirm()
 * answer must say so at the call site, and silently accepting every dialog is
 * how a "delete?" prompt gets answered yes by a read-only check.
 */
const ACCEPT_QUEUES = new WeakMap();

function wirePage(page, label, recorder) {
  // ONE dialog listener per page, deliberately. Playwright delivers a dialog to
  // every registered listener, so a second listener that accepts races this one's
  // dismiss and whichever loses throws "already handled" -- which showed up as a
  // delete that never posted. acceptNextDialog() therefore enqueues an intent
  // that this handler reads instead of registering a competing listener.
  page.on('dialog', async (dialog) => {
    const queue = ACCEPT_QUEUES.get(page);
    const pending = queue && queue.length ? queue.shift() : null;
    recorder.dialogs.push({
      label: pending ? pending.label : label,
      type: dialog.type(),
      text: dialog.message(),
      accepted: Boolean(pending),
    });
    if (pending) {
      await dialog.accept(pending.promptText).catch(() => {});
      pending.resolve(dialog.message());
      return;
    }
    await dialog.dismiss().catch(() => {});
  });
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    const entry = {
      label,
      status,
      method: response.request().method(),
      url: responseUrl,
      contentType: response.headers()['content-type'] || '',
    };
    recorder.requestLog.push(entry);
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      recorder.badResponses.push(entry);
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      recorder.consoleIssues.push({
        label,
        type: message.type(),
        text: message.text(),
        location: message.location(),
      });
    }
  });
  page.on('pageerror', (error) => {
    recorder.pageErrors.push({ label, text: error.stack || error.message });
  });
}

/**
 * Accepts the next dialog on a page instead of dismissing it.
 *
 * Deletes and cancellations in CARLOS are gated on confirm(); a check that
 * means to go through one opts in per action so the default stays "dismiss".
 */
/**
 * Queues an intent to accept the next dialog on this page.
 *
 * @param promptText answer to type when the dialog is a prompt(); omit to accept
 *   the prompt's default. Ignored for alert/confirm, which carry no input.
 */
function acceptNextDialog(page, recorder, label, promptText) {
  if (!ACCEPT_QUEUES.has(page)) {
    ACCEPT_QUEUES.set(page, []);
  }
  return new Promise((resolve) => {
    ACCEPT_QUEUES.get(page).push({ label, resolve, promptText });
  });
}

/**
 * Drops a queued accept intent that no dialog consumed.
 *
 * Needed because not every action that *may* prompt actually does (an empty
 * schedule slot prompts only when its template carries a confirmation message),
 * and a leftover intent would silently accept the NEXT dialog the check meant to
 * dismiss.
 */
function clearPendingDialogAccepts(page) {
  const queue = ACCEPT_QUEUES.get(page);
  if (queue) {
    queue.length = 0;
  }
}

async function gotoApp(page, baseUrl, appPath, waitUntil = 'domcontentloaded', query = null) {
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback/private ranges
  return page.goto(appUrl(baseUrl, appPath, query), { waitUntil, timeout: 45000 });
}

/**
 * Logs in through the real form and lands on the schedule.
 *
 * Both landing routes are accepted: `providercontrol` is the classic schedule
 * and `ViewAppointmentAdminDay` is the gate action a packaged install can land
 * on, and a check that pinned only one of them would fail on the other
 * deployment for no clinical reason.
 */
async function login(context, config, recorder, label = 'login') {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/');
  await page.locator('#username').fill(config.testUser);
  await page.locator('#password').fill(config.testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(config.testPin);
  }
  await Promise.all([
    page.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/, { timeout: 45000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function assertNotErrorPage(page, label) {
  const text = await page.locator('body').innerText({ timeout: 15000 }).catch(() => '');
  assert(!/CARLOS has encountered an unexpected error|CARLOS Error|HTTP Status 5\d\d|Exception Report|Whitelabel Error Page/i.test(text),
    `${label} rendered an error page: ${text.slice(0, 400)}`);
  assert(text.trim().length > 0, `${label} rendered a blank page`);
  return text;
}

function assertNoPageErrors(recorder, context) {
  assert(recorder.badResponses.length === 0,
    `${context}: unexpected HTTP failures ${JSON.stringify(recorder.badResponses, null, 2)}`);
  assert(recorder.pageErrors.length === 0,
    `${context}: uncaught browser errors ${JSON.stringify(recorder.pageErrors, null, 2)}`);
  assert(recorder.consoleIssues.length === 0,
    `${context}: severe console output ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
}

/**
 * Reads the CSRFGuard token a rendered page carries.
 *
 * Returns null rather than throwing so a caller can distinguish "this page has
 * no token" — itself a defect worth asserting on, see the CSRF bootstrapping
 * rule in CLAUDE.md — from "the token is wrong".
 */
async function readCsrfToken(page) {
  return page.evaluate(() => {
    const input = document.querySelector('input[name="CSRF-TOKEN"]');
    return input ? input.value : null;
  });
}

function getLaunchOptions(chromePath) {
  // --no-sandbox is required when Chromium runs as root, which is the
  // devcontainer, CI and packaged-install-VM default. A deployment that runs
  // the checks unprivileged can keep the sandbox with CARLOS_PW_SANDBOX=true.
  const args = ['--disable-dev-shm-usage'];
  if (process.env.CARLOS_PW_SANDBOX !== 'true') {
    args.unshift('--no-sandbox');
  }
  const launchOptions = { headless: true, args };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }
  return launchOptions;
}

/**
 * Builds a MySQL client that asserts on what actually reached the database.
 *
 * The password goes into a 0600 defaults file in a private temp directory
 * rather than onto the command line, where `ps` would expose it to every
 * account on the host for the life of each query.
 *
 * Call `close()` in a finally — including on the failure path — so the
 * credential file does not outlive the run.
 *
 * NOTE ON OUTPUT ENCODING: queries run through `mysql -B`, which escapes
 * backslashes, tabs and newlines in the values it prints. A column holding
 * `a\_b` therefore reads back as `a\\_b`, and a column holding a newline reads
 * back as a literal `\n`. Assert on normalised text when the column can contain
 * any of those; `rows()` splits on real tabs and newlines precisely because the
 * embedded ones arrive escaped.
 */
function createSqlClient(options = {}) {
  const host = options.host || process.env.MYSQL_HOST || 'db';
  const user = options.user || process.env.MYSQL_USER || 'root';
  const password = options.password || process.env.MYSQL_PASSWORD || 'password';
  const database = options.database || process.env.MYSQL_DATABASE || 'carlos';
  const namespace = options.namespace || 'carlos-pw';

  if (/[\r\n]/.test(password)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  assert(/^[A-Za-z0-9_-]+$/.test(namespace), `SQL client namespace must be simple, got ${namespace}`);

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `${namespace}-mysql-`));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword=${password}\n`, { mode: 0o600 });

  function run(query) {
    return execFileSync('mysql', [
      `--defaults-extra-file=${file}`,
      '-h', host,
      '-u', user,
      database,
      '-N',
      '-B',
      '-e',
      query,
    ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  }

  return {
    /** Runs a statement and returns trimmed stdout. */
    exec: run,
    /** Runs a SELECT and returns an array of column arrays (tab-separated output split). */
    rows(query) {
      const out = run(query);
      return out ? out.split('\n').map((line) => line.split('\t')) : [];
    },
    /** Runs a SELECT and returns the first column of the first row, or null. */
    scalar(query) {
      const out = run(query);
      if (!out) {
        return null;
      }
      const value = out.split('\n')[0].split('\t')[0];
      return value === 'NULL' ? null : value;
    },
    close() {
      fs.rmSync(dir, { recursive: true, force: true });
    },
  };
}

/** Escapes a value for single-quoted SQL literals. */
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

/** Asserts a value is a bare positive integer before it is interpolated into SQL. */
function requireId(value, label) {
  assert(/^\d+$/.test(String(value)), `${label} must be a positive integer, got ${value}`);
  return String(value);
}

/** Polls until `probe()` returns a truthy value, then returns it. */
async function waitFor(probe, { timeoutMs = 30000, intervalMs = 250, description = 'condition' } = {}) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await probe();
    if (last) {
      return last;
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(`timed out waiting for ${description}; last value=${JSON.stringify(last)}`);
}

/** Reads the standard TEST_USER/TEST_PASSWORD/TEST_PIN/BASE_URL contract. */
function readConfig() {
  return {
    baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
    chromePath: process.env.CHROME_PATH || '',
    testUser: process.env.TEST_USER || 'carlosdoc',
    testPassword: process.env.TEST_PASSWORD || 'carlos2026',
    testPin: process.env.TEST_PIN || '2026',
  };
}

module.exports = {
  acceptNextDialog,
  clearPendingDialogAccepts,
  appUrl,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  createRecorder,
  createSqlClient,
  escapeSql,
  getLaunchOptions,
  gotoApp,
  login,
  readConfig,
  readCsrfToken,
  requireId,
  validateBaseUrl,
  waitFor,
  wirePage,
};
