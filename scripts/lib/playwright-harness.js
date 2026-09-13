#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The shared harness for every scripts/*-playwright-checks.js browser check.
 *
 * WHY THIS EXISTS AS ONE MODULE. The suite grew to 75 checks with 33 private
 * copies of login(), 31 of validateBaseUrl() (issue #3317), 35 private mysql
 * helpers, 23 signal handlers where there should be 75 (issue #3600) and 51
 * unconditional ignoreHTTPSErrors (issue #3598). Each copy is a place a fix has
 * to be repeated and a place a security guard can be missing. Everything a
 * check needs that is not specific to its own workflow belongs here.
 *
 * WHY IT NEVER require()s PLAYWRIGHT AT LOAD TIME. scripts/*.test.js runs in CI
 * with `npm ci --ignore-scripts` and no browser binary. The browser is pulled in
 * lazily inside launchBrowser() so the whole harness stays unit-testable there.
 *
 * The four rules the checks follow, and which helper enforces each:
 *   1. Drive the browser's own event path      -> ui.* (never page.evaluate(handler))
 *   2. Enter through the opener, not a URL     -> ui.clickOpensPopup / the navigation map
 *   3. Fail on the JavaScript signals          -> wireStrictPage + assertStrictPage
 *   4. Assert the round trip the user sees     -> ui.expectOpenerRefresh, ui.dataTableRows
 * See docs/ui-tests/playwright-coverage-plan-2026.08.md for the reasoning.
 */

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const { createGracefulSignalCancellation, settleOperations } = require('../graceful-signal-cancellation');

const SAFE_ARTIFACT_BASENAME_RE = /^[A-Za-z0-9._-]+$/;
const SAFE_ARTIFACT_EXTENSION_RE = /^\.[A-Za-z0-9]+$/;

/** Exit codes the runner and CI distinguish. A missing fixture is not a failure. */
const EXIT_PASS = 0;
const EXIT_FAIL = 1;
const EXIT_SKIP = 2;

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

/**
 * Thrown by a check that cannot run because a fixture or credential it needs is
 * absent. runCheck() reports SKIP and exits 2, so the runner can tell "nothing
 * to test here" apart from "the application is broken" -- issue #3313 listed two
 * suites as failures when they were only missing EFORM_CORPUS_DIR / CONSULT_DEMO_NO.
 */
class SkipCheck extends Error {
  constructor(message) {
    super(message);
    this.name = 'SkipCheck';
  }
}

function resolveArtifactDir(rawDir) {
  assert(typeof rawDir === 'string' && rawDir.trim() !== '', 'Artifact directory must be a non-empty string');
  const resolvedDir = path.resolve(rawDir); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- artifact dirs are restricted to /tmp or the current workspace before use
  const allowedRoots = [path.resolve('/tmp'), path.resolve(process.cwd())];
  assert(allowedRoots.some((root) => resolvedDir === root || resolvedDir.startsWith(`${root}${path.sep}`)), `Artifact directory must be under ${allowedRoots.join(' or ')}, got ${resolvedDir}`);
  fs.mkdirSync(resolvedDir, { recursive: true });
  return resolvedDir;
}

function buildArtifactPath(artifactDir, baseName, extension = '.png') {
  assert(SAFE_ARTIFACT_BASENAME_RE.test(baseName), `Invalid artifact name: ${baseName}`);
  assert(SAFE_ARTIFACT_EXTENSION_RE.test(extension), `Invalid artifact extension: ${extension}`);
  return path.join(resolveArtifactDir(artifactDir), `${baseName}${extension}`); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- output path stays under a validated artifact directory and uses a sanitized basename
}

/*
 * Kept textually self-contained: scripts/base-url-credentials.test.js extracts
 * this function from every script by source slice and runs it in a bare vm
 * context holding only URL and process. Do not call helpers from inside it, and
 * do not introduce a closing brace in column 1 before the end of the function.
 */
function validateBaseUrl(rawBaseUrl, env = process.env) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Credentials in the URL would ride every navigation and surface in failure diagnostics; the
  // checks log in through the form with TEST_USER/TEST_PASSWORD instead.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
  }

  const host = parsed.hostname.toLowerCase();
  const normalizedHost = host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
  const loopbackHosts = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);
  if (!loopbackHosts.has(normalizedHost) && env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-loopback BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }

  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

/*
 * The fixture-writing checks seed and delete rows through the mysql client. A
 * mistyped MYSQL_HOST must not point that at a shared or production database,
 * so the host has to be loopback unless the caller opts in for a disposable
 * non-local test database. Mirrors validateBaseUrl's loopback rule.
 */
function validateMysqlHost(rawHost, env = process.env) {
  const host = String(rawHost || '').trim().toLowerCase();
  const normalizedHost = host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
  const loopbackHosts = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);
  if (!loopbackHosts.has(normalizedHost) && env.ALLOW_NON_LOCAL_MYSQL_HOST !== 'true') {
    throw new Error(`Refusing to seed fixtures into non-loopback MYSQL_HOST ${host}; set ALLOW_NON_LOCAL_MYSQL_HOST=true only for a disposable test database`);
  }
  return rawHost;
}

/**
 * True when the target is loopback or RFC1918, i.e. a host whose TLS certificate
 * is a local self-signed one that no CA can vouch for.
 *
 * issue #3598: 51 checks passed ignoreHTTPSErrors:true unconditionally while
 * posting TEST_PASSWORD, so a run aimed at a real host would have accepted any
 * certificate at all. Certificate errors are only ignorable because the target
 * is a disposable local deployment; anywhere else they are the finding.
 */
function isLocalTlsTarget(baseUrl) {
  const host = String(baseUrl && baseUrl.hostname ? baseUrl.hostname : baseUrl || '')
    .toLowerCase()
    .replace(/^\[|\]$/g, '');
  if (['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1'].includes(host)) {
    return true;
  }
  return /^(?:10\.|192\.168\.|172\.(?:1[6-9]|2\d|3[01])\.|127\.)/.test(host);
}

function appUrl(baseUrl, appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

/**
 * One environment contract for the whole suite.
 *
 * issue #3313 asked for this directly: "It would help to document the required
 * environment for each suite (or give them discoverable defaults), since today
 * the requirement is only discoverable by running the suite and reading the
 * throw." Defaults here are the devcontainer's; scripts/playwright-suite.json
 * records the per-check extras.
 */
function readConfig(options = {}) {
  const env = options.env || process.env;
  const baseUrl = validateBaseUrl(options.baseUrl || env.BASE_URL || 'http://127.0.0.1:8080/carlos', env);
  const config = {
    baseUrl,
    chromePath: env.CHROME_PATH || env.CHROMIUM_PATH || '',
    testUser: env.TEST_USER || env.CARLOS_USER || 'carlosdoc',
    testPassword: env.TEST_PASSWORD || env.CARLOS_PASSWORD || 'carlos2026',
    testPin: env.TEST_PIN || env.CARLOS_PIN || '2026',
    resetPassword: env.RESET_PASSWORD || '',
    // Only meaningful through the packaged nginx + ModSecurity front door: the
    // WAF-sensitive checks prove nothing against bare Tomcat, where there is no
    // WAF to false-positive on clinical prose.
    expectFrontDoor: env.EXPECT_FRONT_DOOR === 'true',
    headless: env.HEADLESS !== 'false',
    screenshotDir: options.screenshotDir || env.SCREENSHOT_DIR || '',
    mysql: {
      host: env.MYSQL_HOST || 'localhost',
      user: env.MYSQL_USER || 'root',
      password: env.MYSQL_PASSWORD,
      database: env.MYSQL_DATABASE || 'carlos',
    },
  };
  // Never unconditionally: see isLocalTlsTarget.
  config.ignoreHTTPSErrors = isLocalTlsTarget(baseUrl);
  for (const name of options.require || []) {
    if (!env[name]) {
      throw new SkipCheck(`${name} is not set; this check needs it (see scripts/playwright-suite.json)`);
    }
  }
  return config;
}

/**
 * Undo the escaping `mysql -B` applies to its output.
 *
 * docs/ui-tests/clinical-workflow-browser-checks.md records the trap: a column
 * holding a backslash comes back with that backslash doubled, so a check
 * comparing note or message text byte-for-byte fails on content that is actually
 * correct. Every private copy of sql() in the suite skipped this. One
 * left-to-right pass, so an escaped backslash is never re-read as the start of
 * another escape.
 */
function unescapeMysqlBatchValue(raw) {
  if (raw === 'NULL') {
    return null;
  }
  return String(raw).replace(/\\(.)/g, (_match, character) => {
    switch (character) {
      case '0': return String.fromCharCode(0);
      case 'n': return '\n';
      case 'r': return '\r';
      case 't': return '\t';
      case 'Z': return String.fromCharCode(26);
      default: return character;
    }
  });
}

function parseMysqlBatchOutput(stdout) {
  const text = String(stdout).replace(/\n$/, '');
  if (text === '') {
    return [];
  }
  return text.split('\n').map((line) => line.split('\t').map(unescapeMysqlBatchValue));
}

/**
 * A mysql client bound to one throwaway 0600 option file.
 *
 * The password never reaches argv (it would be world-readable in /proc), stderr
 * is captured rather than inherited (a failing statement otherwise echoes the
 * clinical text it carried), and dispose() removes the option file even when the
 * check throws. 35 scripts each reimplemented a piece of this.
 */
function createSqlRunner(mysqlConfig, options = {}) {
  const exec = options.exec || execFileSync;
  const host = validateMysqlHost(mysqlConfig.host, options.env || process.env);
  const { password } = mysqlConfig;
  assert(password !== undefined && password !== null, 'MYSQL_PASSWORD must be set for a database-asserting check');
  assert(!/[\r\n]/.test(password), 'MYSQL_PASSWORD must not contain newline characters');

  const directory = (options.mkdtemp || fs.mkdtempSync)(path.join(os.tmpdir(), 'carlos-playwright-sql-'));
  const optionFile = path.join(directory, 'client.cnf');
  const quoted = String(password).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  (options.writeFile || fs.writeFileSync)(optionFile, `[client]\npassword="${quoted}"\n`, { mode: 0o600 });

  function run(query) {
    return exec('mysql', [
      `--defaults-extra-file=${optionFile}`,
      '-h', host,
      '-u', mysqlConfig.user || 'root',
      mysqlConfig.database || 'carlos',
      '-N', '-B', '-e', query,
    ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 30000 });
  }

  return {
    /** One scalar, unescaped. Empty string when the query returned no row. */
    value(query) {
      const rows = parseMysqlBatchOutput(run(query));
      return rows.length ? rows[0][0] : '';
    },
    /** Every row as an array of unescaped column values. */
    rows(query) {
      return parseMysqlBatchOutput(run(query));
    },
    /** For INSERT/UPDATE/DELETE, where the output is not interesting. */
    execute(query) {
      run(query);
    },
    dispose() {
      (options.rm || fs.rmSync)(directory, { recursive: true, force: true, maxRetries: 2, retryDelay: 50 });
    },
  };
}

/** Single-quoted SQL string literal. Checks build fixture statements, never user input. */
function sqlString(value) {
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

function createRecorder() {
  return {
    badResponses: [],
    consoleIssues: [],
    pageErrors: [],
    requestLog: [],
    requestFailures: [],
    dialogs: [],
    unexpectedDialogs: [],
  };
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (
    responseUrl.endsWith('/favicon.ico')
    || /\/imageRenderingServlet\?/.test(responseUrl)
    || /\/eform\/displayImage\?imagefile=signature_pad\.min\.js(?:$|&)/.test(responseUrl)
    || /\/eform\/displayImage\?imagefile=BNK\.png(?:$|&)/.test(responseUrl)
  );
}

function isIgnorableConsoleMessage(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text)
    || /window\.print/i.test(text);
}

function isExpectedLegacyConsoleIssue(text, location = {}) {
  const source = `${location.url || ''} ${text}`;
  return /signature_pad\.min\.js|BNK\.png/.test(source);
}

function isSevereConsoleMessage(message) {
  if (isIgnorableConsoleMessage(message)) {
    return false;
  }
  const text = message.text();
  if (isExpectedLegacyConsoleIssue(text, message.location())) {
    return false;
  }
  if (message.type() === 'error') {
    return true;
  }
  return /(ReferenceError|TypeError|SyntaxError|\$ is not defined|jQuery is not defined|Cannot read|Cannot set|is not defined)/i.test(text);
}

let baselineCache = null;

/**
 * The suite-wide allow-list of legacy browser noise, each entry keyed to the
 * issue that will remove it.
 *
 * A per-check `allow` array is invisible: nobody can see which regressions the
 * suite as a whole can no longer detect. One reviewed file with an issue number
 * per entry makes the list a burn-down rather than a growing blind spot.
 */
function loadConsoleBaseline(baselinePath = path.join(__dirname, 'console-baseline.json')) {
  if (baselineCache && baselineCache.path === baselinePath) {
    return baselineCache.entries;
  }
  const parsed = JSON.parse(fs.readFileSync(baselinePath, 'utf8'));
  const entries = parsed.allow.map((entry) => {
    assert(entry.pattern && entry.issue && entry.note,
      'Every console-baseline entry needs a pattern, the issue that removes it, and a note');
    return { ...entry, regex: new RegExp(entry.pattern) };
  });
  baselineCache = { path: baselinePath, entries };
  return entries;
}

function isBaselinedText(text, baseline) {
  return baseline.some((entry) => entry.regex.test(text));
}

/**
 * A page wired so that the JavaScript layer failing is a failure of the check.
 *
 * CARLOS is popup/opener/AJAX-driven (827 popup openers, 1,158 inline script
 * blocks, 171 AJAX call sites), so the thing that breaks for a user is usually
 * not the Struts action: it is the onclick, the opener callback, the DataTables
 * init, the autocomplete, or a script served with the wrong MIME type. Every
 * defect below shipped green past a check that only asserted its own POST:
 * "aSubmit is not defined" (add patient), "contextPath is not defined" (Inbox,
 * issue #3313), "parent.parent.resizeIframe" (schedule wizard), the white Select
 * Forms panel (issue #3377).
 *
 * Recording rather than throwing: a Playwright event handler cannot fail the
 * check from inside the listener, so assertStrictPage() is what throws.
 *
 * @param options.dialogHandler  optional; there is ONE dialog listener per page
 *   because Playwright delivers a dialog to every listener and a second one that
 *   accepts races the default dismiss (the delete that never posts, see
 *   docs/ui-tests/clinical-workflow-browser-checks.md).
 */
function wireStrictPage(page, label, recorder, options = {}) {
  const dialogHandler = options.dialogHandler || null;
  const baseline = options.baseline || loadConsoleBaseline();
  // The signals no check asserted before this harness existed. wirePage() turns
  // them off so the 75 scripts that have not migrated keep recording exactly what
  // they recorded before -- a migration must be a deliberate, reviewed change to
  // a check, never a silent new failure mode arriving underneath it.
  const strictSignals = options.strictSignals !== false;

  page.on('dialog', async (dialog) => {
    const entry = { label, type: dialog.type(), text: dialog.message() };
    recorder.dialogs.push(entry);
    if (dialogHandler) {
      await dialogHandler(dialog, entry);
      return;
    }
    // An unexpected confirm() is a finding: dismissing it silently is how a
    // delete that no longer asks, or one that now asks twice, goes unnoticed.
    if (strictSignals) {
      recorder.unexpectedDialogs.push(entry);
    }
    await dialog.dismiss().catch(() => {});
  });
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    const contentType = response.headers()['content-type'] || '';
    const resourceType = response.request().resourceType();
    recorder.requestLog.push({
      label, status, method: response.request().method(), url: responseUrl, contentType, resourceType,
    });
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      recorder.badResponses.push({
        label, status, method: response.request().method(), url: responseUrl, contentType, resourceType,
      });
      return;
    }
    // A script answered with text/html is an error page where code should be.
    // The browser refuses to execute it and the page half-initialises -- the
    // displayImage.do?imagefile=stamps.js failure in issue #3313 item 3, where
    // the editor never came up and the popup was an error page by the time the
    // check looked at it.
    if (strictSignals && status < 400 && resourceType === 'script' && /^\s*text\/html/i.test(contentType)) {
      recorder.badResponses.push({
        label, status, method: 'GET', url: responseUrl, contentType, resourceType, reason: 'script served as text/html',
      });
    }
  });
  page.on('requestfailed', (request) => {
    if (!strictSignals) {
      return;
    }
    const failure = request.failure();
    const url = request.url();
    if (isExpectedMissingAsset(404, url)) {
      return;
    }
    // Playwright reports a navigation the page itself aborted as a failure; that
    // is ordinary (a click that replaces a pending load), so only subresources
    // count. No check in the suite asserted this at all before now.
    if (request.resourceType() === 'document') {
      return;
    }
    recorder.requestFailures.push({
      label, url, resourceType: request.resourceType(), errorText: failure ? failure.errorText : 'unknown',
    });
  });
  page.on('console', (message) => {
    if (!isSevereConsoleMessage(message)) {
      return;
    }
    const text = message.text();
    if (isBaselinedText(text, baseline)) {
      return;
    }
    recorder.consoleIssues.push({
      label, type: message.type(), text, location: message.location(),
    });
  });
  page.on('pageerror', (error) => {
    const text = error.stack || error.message;
    if (isBaselinedText(text, baseline)) {
      return;
    }
    recorder.pageErrors.push({ label, text });
  });
  return page;
}

/**
 * Backwards-compatible wiring for the checks not yet migrated.
 *
 * Records exactly what the pre-harness wirePage recorded: no requestfailed, no
 * script-MIME finding, no unexpected-dialog finding, and no console baseline.
 * Migrating a check to wireStrictPage is then a visible change to that check,
 * reviewed and validated against a running deployment, rather than 75 checks
 * silently gaining new ways to fail on the day this module landed.
 */
function wirePage(page, label, recorder, dialogHandler = null) {
  return wireStrictPage(page, label, recorder, {
    strictSignals: false,
    baseline: [],
    dialogHandler: dialogHandler || (async (dialog) => {
      await dialog.dismiss().catch(() => {});
    }),
  });
}

/**
 * Fail the check on anything the browser reported that is not baselined.
 *
 * @param labels  page labels to consider; omit for every page
 */
function assertStrictPage(recorder, labels = null) {
  const scoped = (entries) => entries.filter((entry) => !labels || labels.includes(entry.label));
  const problems = [];
  for (const entry of scoped(recorder.pageErrors)) {
    problems.push(`[${entry.label}] uncaught ${entry.text.split('\n')[0]}`);
  }
  for (const entry of scoped(recorder.consoleIssues)) {
    problems.push(`[${entry.label}] console ${entry.type}: ${entry.text.split('\n')[0]}`);
  }
  for (const entry of scoped(recorder.requestFailures)) {
    problems.push(`[${entry.label}] ${entry.resourceType} request failed (${entry.errorText})`);
  }
  for (const entry of scoped(recorder.badResponses)) {
    problems.push(`[${entry.label}] HTTP ${entry.status}${entry.reason ? ` (${entry.reason})` : ''} on a ${entry.resourceType || 'resource'}`);
  }
  for (const entry of scoped(recorder.unexpectedDialogs)) {
    problems.push(`[${entry.label}] an unexpected ${entry.type} dialog was raised and dismissed`);
  }
  assert(problems.length === 0,
    `The browser reported ${problems.length} JavaScript-layer problem(s): ${problems.join(' | ')}`);
}

/**
 * Fail if the browser reported an uncaught JS error on any of the given pages.
 * Retained for checks that assert page errors without the full strict contract.
 */
function assertNoPageErrors(recorder, labels = null, allow = []) {
  const relevant = recorder.pageErrors.filter((entry) => {
    if (labels && !labels.includes(entry.label)) {
      return false;
    }
    return !allow.some((pattern) => pattern.test(entry.text));
  });
  assert(
    relevant.length === 0,
    `The browser raised ${relevant.length} uncaught JavaScript error(s): `
      + relevant.map((entry) => `[${entry.label}] ${entry.text.split('\n')[0]}`).join(' | '),
  );
}

function getLatestRequest(recorder, predicate) {
  const matches = recorder.requestLog.filter(predicate);
  return matches.length ? matches[matches.length - 1] : null;
}

function buildFailureDetails(recorder) {
  return {
    badResponses: recorder.badResponses,
    consoleIssues: recorder.consoleIssues,
    pageErrors: recorder.pageErrors,
    requestFailures: recorder.requestFailures,
    dialogs: recorder.dialogs,
  };
}

function getLaunchOptions(chromePath) {
  // --no-sandbox is required when Chromium runs as root (the devcontainer/CI default).
  // Set EFORM_RENDER_ENABLE_CHROMIUM_SANDBOX=true to keep the Chromium sandbox enabled
  // on deployments that run the renderer as an unprivileged user.
  const args = ['--disable-dev-shm-usage'];
  if (process.env.EFORM_RENDER_ENABLE_CHROMIUM_SANDBOX !== 'true') {
    args.unshift('--no-sandbox');
  }
  const launchOptions = { headless: true, args };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }
  return launchOptions;
}

/** Lazy so the harness stays require()-able in CI, which has no browser binary. */
async function launchBrowser(config) {
  // Deliberately not a top-level require: see the module header.
  const { chromium } = require('playwright');
  return chromium.launch({ ...getLaunchOptions(config.chromePath), headless: config.headless !== false });
}

async function newContext(browser, config) {
  return browser.newContext({ ignoreHTTPSErrors: config.ignoreHTTPSErrors === true });
}

async function gotoApp(page, baseUrl, appPath, waitUntil = 'domcontentloaded') {
  return page.goto(appUrl(baseUrl, appPath), { waitUntil, timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback by default
}

async function assertNotErrorPage(page, label) {
  const text = await page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report|Whitelabel Error Page/i.test(text), `${label} rendered an error page`);
  assert(text.trim().length > 0, `${label} rendered a blank page`);
}

/**
 * Log in, handling every branch the login page can take.
 *
 * The 33 private copies each handled a different subset, which is why issue
 * #3313 saw "every suite except login lands on the forced-reset page and fails
 * at its login helper". Branches: the schedule (normal), the forced reset (a
 * freshly installed deb flags its generated credential), the MFA challenge, and
 * the facility chooser for a provider in more than one facility.
 */
async function login(context, config, recorder, options = {}) {
  const page = await context.newPage();
  wireStrictPage(page, options.label || 'login', recorder, options);
  await gotoApp(page, config.baseUrl, '/');
  await page.waitForLoadState('load', { timeout: 30000 });
  await page.locator('#username').fill(config.testUser);
  await page.locator('#password').fill(config.testPassword);
  const pinInput = page.locator('#pin');
  if (await pinInput.count() > 0) {
    await pinInput.fill(config.testPin);
    assert(await pinInput.inputValue() === config.testPin, 'login PIN field changed before submit');
  }
  assert(await page.locator('#username').inputValue() === config.testUser, 'login username field changed before submit');
  assert(await page.locator('#password').inputValue() === config.testPassword, 'login password field changed before submit');
  await settleOperations([
    page.waitForURL(/providercontrol|appointment|forcepasswordreset|loginMfa|select_facility/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  if (/loginMfa/i.test(page.url())) {
    assert(typeof options.mfaCode === 'function',
      `${config.testUser} is enrolled in MFA; pass options.mfaCode to supply the challenge response`);
    await page.locator('input[name="mfaCode"], #mfaCode').first().fill(await options.mfaCode());
    await settleOperations([
      page.waitForURL(/providercontrol|appointment|forcepasswordreset|select_facility/i, { timeout: 30000 }),
      page.locator('input[type="submit"], button[type="submit"]').first().click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  }

  if (/forcepasswordreset/i.test(page.url())) {
    assert(
      config.resetPassword,
      `${config.testUser} must change its password before it can be used: a fresh carlos-emr`
      + ' install flags its generated admin credential for a forced reset. Complete it ONCE, in an'
      + ' isolated run outside any suite loop, with RESET_PASSWORD set to a new password meeting'
      + ' the policy, then export TEST_PASSWORD as that new password for every later run. Doing'
      + ' it inside a loop leaves the scripts that ran before it unreset and the ones after it'
      + ' authenticating with the old password.',
    );
    await page.locator('input[name="oldPassword"]').fill(config.testPassword);
    await page.locator('input[name="newPassword"]').fill(config.resetPassword);
    await page.locator('input[name="confirmPassword"]').fill(config.resetPassword);
    await settleOperations([
      page.waitForURL(/providercontrol|appointment|select_facility/i, { timeout: 30000 }),
      page.locator('input[type="submit"], button[type="submit"]').first().click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  }

  if (/select_facility/i.test(page.url())) {
    await settleOperations([
      page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
      page.locator('input[type="submit"], button[type="submit"], a').first().click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  }
  return page;
}

async function screenshot(page, screenshotDir, name) {
  const outputPath = buildArtifactPath(screenshotDir, name);
  await page.screenshot({ path: outputPath, fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
  return outputPath;
}

/**
 * The one entry point a check's main() should use.
 *
 * Standardises what 75 scripts each did differently: the SIGINT/SIGTERM handler
 * (issue #3600 -- 52 scripts had none, so an interrupted run left its fixture
 * rows behind), the PASS/FAIL/SKIP reporting line the runner parses, the exit
 * code, and running cleanup in a finally even when the body threw.
 */
async function runCheck(options) {
  const { name } = options;
  assert(name, 'runCheck needs a name');
  const out = options.stdout || console;
  const processRef = options.processRef || process;
  const createCancellation = options.createCancellation || createGracefulSignalCancellation;
  const cancellation = createCancellation({ signalProcess: processRef });
  const started = options.now ? options.now() : Date.now();
  let outcome = 'PASS';
  let detail = '';
  let value;
  try {
    value = await options.run({ cancellation, throwIfCancelled: cancellation.throwIfCancelled });
  } catch (error) {
    if (error instanceof SkipCheck || (error && error.name === 'SkipCheck')) {
      outcome = 'SKIP';
      detail = error.message;
    } else if (cancellation.isCancellation(error)) {
      outcome = 'FAIL';
      detail = 'interrupted';
    } else {
      outcome = 'FAIL';
      detail = (error && error.message) || 'check failed';
    }
  } finally {
    if (options.cleanup) {
      try {
        await options.cleanup();
      } catch (cleanupError) {
        // A cleanup failure must be visible: the next run inherits the rows.
        outcome = outcome === 'PASS' ? 'FAIL' : outcome;
        detail = `${detail ? `${detail}; ` : ''}cleanup failed: ${(cleanupError && cleanupError.message) || 'unknown'}`;
      }
    }
    cancellation.dispose();
  }
  const durationMs = (options.now ? options.now() : Date.now()) - started;
  out.log(`${outcome} ${name}${detail ? ` -- ${detail}` : ''}`);
  const record = {
    name, outcome, detail, durationMs,
  };
  const resultPath = (options.env || processRef.env || {}).RESULT_JSON;
  if (resultPath) {
    (options.writeFile || fs.writeFileSync)(resultPath, `${JSON.stringify(record, null, 2)}\n`);
  }
  const codes = { PASS: EXIT_PASS, FAIL: EXIT_FAIL, SKIP: EXIT_SKIP };
  processRef.exitCode = cancellation.exitCode === undefined ? codes[outcome] : cancellation.exitCode;
  return { ...record, value };
}

module.exports = {
  EXIT_FAIL,
  EXIT_PASS,
  EXIT_SKIP,
  SkipCheck,
  appUrl,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  assertStrictPage,
  buildArtifactPath,
  buildFailureDetails,
  createRecorder,
  createSqlRunner,
  getLatestRequest,
  getLaunchOptions,
  gotoApp,
  isLocalTlsTarget,
  launchBrowser,
  loadConsoleBaseline,
  login,
  newContext,
  parseMysqlBatchOutput,
  readConfig,
  runCheck,
  screenshot,
  sqlString,
  unescapeMysqlBatchValue,
  validateBaseUrl,
  validateMysqlHost,
  wirePage,
  wireStrictPage,
};
