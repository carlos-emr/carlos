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
  // The NORMALISED value, not the raw one. createSqlRunner passes this straight
  // to `mysql -h`, so returning the input would send the whitespace or the
  // brackets that were stripped before validating -- the guard would pass and the
  // connection would fail, or worse, connect somewhere else.
  return normalizedHost;
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
  // The host must be a literal IPv4 address before any private-range test. A
  // prefix match on the string alone accepted names like "10.example.com" as
  // private, which with ALLOW_NON_LOCAL_BASE_URL=true would have waived
  // certificate verification for a public host while posting TEST_PASSWORD --
  // the exact boundary issue #3598 is about.
  const octets = host.split('.');
  if (octets.length !== 4 || !octets.every((octet) => /^\d{1,3}$/.test(octet) && Number(octet) <= 255)) {
    return false;
  }
  const [first, second] = octets.map(Number);
  if (first === 10 || first === 127) {
    return true;
  }
  if (first === 192 && second === 168) {
    return true;
  }
  return first === 172 && second >= 16 && second <= 31;
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
  // AMBIGUOUS BY DESIGN OF THE CLIENT, NOT BY CHOICE HERE. `mysql -B` prints
  // SQL NULL and the four-character string 'NULL' identically, so no parser can
  // separate them from the output alone. Reading it as SQL NULL is the safer of
  // the two readings for the suite's comparisons, but a caller that will WRITE
  // the value back must not rely on it: select a companion `col IS NULL` flag
  // and branch on that (see the restore in
  // demographic-edit-update-playwright-checks.js), or the restore turns a real
  // 'NULL' string into a null column.
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
 * is captured rather than inherited AND the client's own error message is
 * replaced with a bounded one (a failing statement otherwise echoes the clinical
 * text it carried, first through stderr and then through the thrown message
 * runCheck logs), and dispose() removes the option file even when the check
 * throws. 35 scripts each reimplemented a piece of this.
 */
function createSqlRunner(mysqlConfig, options = {}) {
  // Full schema/reference catalogs exceed Node's 1MiB default. Keep both captured
  // streams bounded even when a catalog or query unexpectedly grows.
  const maxBuffer = 8 * 1024 * 1024;
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
    try {
      return exec('mysql', [
        `--defaults-extra-file=${optionFile}`,
        '-h', host,
        '-u', mysqlConfig.user || 'root',
        mysqlConfig.database || 'carlos',
        '-N', '-B', '-e', query,
      ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 30000, maxBuffer });
    } catch (error) {
      // CAPTURING stderr IS NOT ENOUGH. execFileSync folds the captured stderr
      // into the thrown error's own message, and runCheck() logs that message --
      // so a failing statement would print the client's echo of the query, and
      // the query can carry a patient's name or a clinical note. Rethrow a
      // bounded reason instead: enough to tell a timeout from a refusal, and
      // nothing of the statement or the row.
      // Buffer overflow also terminates the child with SIGTERM. Classify its
      // code first, and never infer a 30s timeout from the signal alone.
      const outputLimit = error && (error.code === 'ENOBUFS' || error.code === 'ERR_CHILD_PROCESS_STDIO_MAXBUFFER');
      const timedOut = error && error.code === 'ETIMEDOUT';
      const status = error && typeof error.status === 'number' ? ` (mysql exit ${error.status})` : '';
      const failure = new Error(outputLimit
        ? 'the database query exceeded its 8MiB output limit; its text and any rows it carried are withheld deliberately'
        : timedOut ? 'the database query timed out after 30s'
          : `the database query failed${status}; its text and any rows it carried are withheld deliberately`);
      failure.cause = undefined;
      throw failure;
    }
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

/**
 * Runs one fixture INSERT and returns its LAST_INSERT_ID() from the same
 * session. Throws unless the id is a positive integer, so a check never builds
 * later SQL or cleanup from an empty or malformed id.
 */
function insertId(sql, statement, what) {
  const id = sql.value(`${statement}; SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(String(id)), `The owned ${what} fixture was not created`);
  return String(id);
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
/*
 * Entries match as LITERAL SUBSTRINGS, not regular expressions.
 *
 * Every entry the baseline needs is the name of the function or request at fault
 * ("expandPreview", "getActiveText"), so compiling each one to a RegExp bought no
 * expressiveness and cost two things: a pathological pattern committed to the
 * file would hang the check instead of failing it, and it is a dynamic RegExp
 * construction, which Semgrep's detect-non-literal-regexp flags on sight. A
 * literal substring removes both. If an entry ever genuinely needs a pattern,
 * that is a deliberate change to this loader with its own review.
 */
const MINIMUM_BASELINE_MATCH_LENGTH = 8;
const REGEX_METACHARACTERS_RE = /[.*+?^${}()|[\]\\]/;

function loadConsoleBaseline(baselinePath = path.join(__dirname, 'console-baseline.json')) {
  if (baselineCache && baselineCache.path === baselinePath) {
    return baselineCache.entries;
  }
  const parsed = JSON.parse(fs.readFileSync(baselinePath, 'utf8'));
  const entries = parsed.allow.map((entry) => {
    assert(entry.match && entry.issue && entry.note,
      'Every console-baseline entry needs a match, the issue that removes it, and a note');
    // A short fragment would blanket-suppress far more than the defect it names.
    assert(entry.match.length >= MINIMUM_BASELINE_MATCH_LENGTH,
      `Console-baseline match ${JSON.stringify(entry.match)} is too short to identify one defect; use the failing symbol's full name`);
    // Fail loudly rather than silently matching a regex as literal text.
    assert(!REGEX_METACHARACTERS_RE.test(entry.match),
      `Console-baseline match ${JSON.stringify(entry.match)} looks like a regular expression; entries are matched as literal substrings`);
    return { ...entry };
  });
  baselineCache = { path: baselinePath, entries };
  return entries;
}

function isBaselinedText(text, baseline) {
  return baseline.some((entry) => String(text).includes(entry.match));
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
/*
 * One wiring per page, with a label that can change.
 *
 * WHY A MUTABLE LABEL. A control can navigate the SAME page rather than opening
 * a popup -- the schedule's Search is a same-tab href under the caisi module and
 * a popup otherwise -- so the page a check goes on to assert against is often
 * the page it logged in on, still carrying the label 'login'. Every caller that
 * then scopes assertStrictPage(recorder, ['patient-search', ...]) is scoping to
 * a label nothing was ever recorded under: the assertion runs, finds nothing,
 * and passes. That is the same shape as the guards this suite has already had to
 * fix twice, so the label is a box the handlers read at event time, not a
 * constant they closed over.
 *
 * WHY ONE WIRING. Re-wiring a page would add a second set of listeners, so every
 * finding would be recorded twice and -- worse -- a second dialog listener races
 * the first, which is exactly the failure documented on dialogHandler below.
 */
const WIRED_PAGES = new WeakMap();

/**
 * Point an already-wired page's recordings at a new label.
 *
 * Safe to call on a page that was never wired: it reports false rather than
 * pretending, so a caller cannot believe it relabelled something it did not.
 */
function relabelStrictPage(page, label) {
  const wiring = WIRED_PAGES.get(page);
  if (!wiring) {
    return false;
  }
  wiring.label = label;
  return true;
}

/**
 * Run `body` with dialogs on this page EXPECTED rather than recorded as findings.
 *
 * WHY THIS EXISTS AND A SECOND LISTENER DOES NOT. Playwright delivers a dialog to
 * every registered listener, so adding `page.on('dialog', ...)` alongside the
 * strict wiring does not replace it: the strict handler still records the dialog
 * in unexpectedDialogs and still races to dismiss it. A check that deliberately
 * triggers an alert -- proving a validation fires -- would then fail its own
 * assertStrictPage() precisely when the application behaves correctly. That is
 * the trap the note on `dialogHandler` warns about, and this is the supported way
 * out of it: the ONE listener's handler is swapped for the duration.
 *
 * @returns the dialogs seen, in order, so the caller can assert on them.
 */
async function withExpectedDialogs(page, body, options = {}) {
  const wiring = WIRED_PAGES.get(page);
  assert(wiring,
    'withExpectedDialogs() needs a page wired by wireStrictPage(); on an unwired page there is no strict '
    + 'handler to stand in for, and a dialog would go unanswered');
  const seen = [];
  const previous = wiring.dialogHandler;
  wiring.dialogHandler = async (dialog, entry) => {
    seen.push(entry);
    if (options.accept === false) {
      await dialog.dismiss().catch(() => {});
      return;
    }
    await dialog.accept(options.promptText).catch(() => {});
  };
  try {
    await body();
  } finally {
    wiring.dialogHandler = previous;
  }
  return seen;
}

function wireStrictPage(page, label, recorder, options = {}) {
  // Already wired: move the label rather than adding a second set of listeners.
  // See WIRED_PAGES above for why both halves of that matter.
  if (relabelStrictPage(page, label)) {
    return page;
  }
  const wiring = { label, dialogHandler: options.dialogHandler || null };
  WIRED_PAGES.set(page, wiring);

  const baseline = options.baseline || loadConsoleBaseline();
  // The signals no check asserted before this harness existed. wirePage() turns
  // them off so the 75 scripts that have not migrated keep recording exactly what
  // they recorded before -- a migration must be a deliberate, reviewed change to
  // a check, never a silent new failure mode arriving underneath it.
  const strictSignals = options.strictSignals !== false;

  page.on('dialog', async (dialog) => {
    const entry = { label: wiring.label, type: dialog.type(), text: dialog.message() };
    // Legacy custom handlers decide which dialogs are findings themselves.
    // Strict wiring always retains the full dialog audit trail.
    if (strictSignals || !wiring.dialogHandler) recorder.dialogs.push(entry);
    // Read at event time, not closed over: withExpectedDialogs() swaps it for the
    // duration of a step that deliberately raises one.
    if (wiring.dialogHandler) {
      await wiring.dialogHandler(dialog, entry);
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
      label: wiring.label, status, method: response.request().method(), url: responseUrl, contentType, resourceType,
    });
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      recorder.badResponses.push({
        label: wiring.label, status, method: response.request().method(), url: responseUrl, contentType, resourceType,
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
        label: wiring.label, status, method: 'GET', url: responseUrl, contentType, resourceType, reason: 'script served as text/html',
      });
    }
  });
  // Which document each request was issued from (its frame and that frame's address then), so a failure can
  // later be proven to be the browser abandoning a load because its document went away (the frame moved to
  // another address, was detached, or the page closed) rather than the application cancelling it. Decided at
  // read time (navigatedAway), because a navigation can commit after the failure event it caused. A reload of
  // the same address is not proof, so such a failure stays a failure.
  const issuedFrom = new WeakMap();
  page.on('request', (request) => {
    try {
      const frame = request.frame();
      if (frame) issuedFrom.set(request, { frame, url: frame.url() });
    } catch {
      // A service-worker request has no frame: its failure is never presumed abandoned.
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
    const entry = {
      label: wiring.label, url, resourceType: request.resourceType(), errorText: failure ? failure.errorText : 'unknown',
    };
    const origin = issuedFrom.get(request);
    // Non-enumerable: the entry's data shape (compared and serialised elsewhere) is unchanged. False when the
    // issuing document is unknown, so only a proven abandonment can be treated as one.
    Object.defineProperty(entry, 'navigatedAway', {
      enumerable: false,
      value: () => Boolean(origin) && ((typeof page.isClosed === 'function' && page.isClosed())
        || origin.frame.isDetached() || origin.frame.url() !== origin.url),
    });
    recorder.requestFailures.push(entry);
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
      label: wiring.label, type: message.type(), text, location: message.location(),
    });
  });
  page.on('pageerror', (error) => {
    const text = error.stack || error.message;
    if (isBaselinedText(text, baseline)) {
      return;
    }
    recorder.pageErrors.push({ label: wiring.label, text });
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
    dialogHandler,
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

/**
 * The address without its query string, for a diagnostic a human will read.
 *
 * CARLOS puts PHI-CORRELATING IDENTIFIERS IN THE QUERY: the Master Record is
 * demographiccontrol?demographic_no=NNN, and clickAndAwaitReload is called on
 * exactly that page. runCheck() writes a thrown message to stdout AND into
 * RESULT_JSON, which CI archives, so printing the whole address put a patient
 * key into the artifacts of every failed save. The path alone says which page
 * did not navigate, which is the entire point of the message.
 */
/**
 * The same cut applied to arbitrary TEXT, for a message we did not compose.
 *
 * An error thrown by Playwright is not under this suite's control: an
 * APIRequestContext failure can render the address it was given, and the
 * anonymous-access check hands it addresses catalogued from an AUTHENTICATED
 * session -- which is to say, addresses carrying demographic_no by
 * construction. Interpolating the message applied printableRoute() to the route
 * and nothing at all to the text beside it.
 *
 * Cutting every url-shaped token at its query keeps the diagnosis (which
 * endpoint, what went wrong) and drops the identifier.
 */
function withoutQueryStrings(text) {
  return String(text == null ? '' : text)
    .split(/(\s+)/)
    .map((token) => (/^https?:\/\//i.test(token) || token.startsWith('/')
      ? token.split(/[?#]/)[0]
      : token))
    .join('');
}

function pathOnly(rawUrl) {
  const raw = String(rawUrl || '');
  try {
    const parsed = new URL(raw);
    // http/https only. 'about:blank' PARSES, and its origin is the string
    // 'null' with pathname 'blank', so the generic origin+pathname form
    // rendered it as 'nullblank' -- a diagnostic naming a page that does not
    // exist. Every other scheme falls through to the plain cut below.
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return `${parsed.origin}${parsed.pathname}`;
    }
  } catch {
    // Not absolute at all (a relative url, or an empty one on a page mid
    // teardown). Fall through.
  }
  // Cut at the first delimiter rather than returning something unbounded.
  return raw.split(/[?#]/)[0];
}

/**
 * What runCheck() serialises into RESULT_JSON when a check fails.
 *
 * THE URLS ARE STRIPPED HERE, NOT IN THE RECORDER. CARLOS routes carry
 * PHI-correlating identifiers in the query -- demographic_no, provider_no,
 * appointment and billing ids -- and every recorded bad response and request
 * failure holds the full address. Handing those through verbatim put a patient
 * key into the CI artifacts of every failed run, for every check in the suite.
 *
 * The recorder keeps the whole url on purpose: getLatestRequest() and the
 * checks' own predicates match on query parameters, and stripping at capture
 * would break them. Sanitising at the boundary where the data LEAVES for disk
 * keeps both properties.
 *
 * WHAT THIS DOES NOT CLOSE, stated rather than implied. consoleIssues,
 * pageErrors and dialogs carry text the PAGE wrote. A url inside that text is
 * cut like any other, but a dialog reading "Delete appointment for <name>?" or
 * a console.log printing a record is free text, and there is no sanitiser for
 * it that leaves the message useful -- the message is the finding. The
 * mitigation is the target, not the filter: the suite refuses a non-loopback
 * BASE_URL and MYSQL_HOST without an explicit opt-in, and the devcontainer
 * dataset is synthetic (FAKE- names). Pointing these checks at real patient
 * data would be the defect; this boundary cannot make that safe.
 */
function buildFailureDetails(recorder) {
  const clean = (entries) => entries.map((entry) => {
    if (!entry || typeof entry !== 'object') {
      return entry;
    }
    const copy = { ...entry };
    if (typeof copy.url === 'string') {
      copy.url = pathOnly(copy.url);
    }
    // THE MESSAGE TEXT TOO, not only the structured url field. A pageerror
    // carries a stack trace and a console message carries whatever the page
    // printed -- both routinely quote the address they were working on, which
    // in CARLOS carries demographic_no. Cutting url-shaped tokens out of the
    // text closes that without destroying the message, which IS the finding
    // ("contextPath is not defined" is the whole point of recording it).
    for (const field of ['text', 'message', 'errorText', 'reason']) {
      if (typeof copy[field] === 'string') {
        copy[field] = withoutQueryStrings(copy[field]);
      }
    }
    return copy;
  });
  return {
    badResponses: clean(recorder.badResponses),
    consoleIssues: clean(recorder.consoleIssues),
    pageErrors: clean(recorder.pageErrors),
    requestFailures: clean(recorder.requestFailures),
    dialogs: clean(recorder.dialogs),
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

/**
 * The version of the browser a check drove, for the run's record ("chromium 154.0.8025.0"), or undefined
 * when the object does not say.
 *
 * WHY IT IS RECORDED. A browser's rules change between releases and under the host's settings: Chromium
 * 154 refuses a beforeunload prompt from a handler that removes itself, and names a download "download"
 * under the POSIX locale. A failure that comes from the browser looks exactly like one that comes from the
 * application, so a result is only interpretable next to the browser that produced it (alpha19 validation,
 * docs/ui-tests/deb-install-validation.md).
 *
 * `browser` is a Playwright Browser. Only the plain version text is kept (the runner copies it into
 * JUnit and the console, so it is cut down to a short, harmless string).
 */
function describeBrowserVersion(browser) {
  try {
    const version = browser && typeof browser.version === 'function' ? browser.version() : undefined;
    if (typeof version !== 'string' || !version.trim()) return undefined;
    const type = typeof browser.browserType === 'function' ? browser.browserType() : undefined;
    const name = type && typeof type.name === 'function' ? type.name() : undefined;
    const text = `${typeof name === 'string' ? `${name} ` : ''}${version}`.replace(/[^\w .+-]/g, '').trim();
    return text ? text.slice(0, 80) : undefined;
  } catch {
    return undefined;
  }
}

/** What launchBrowser() saw in this process; one check is one process, so it is that check's browser. */
let launchedBrowserVersion;

/** Remember a launched browser's version for runCheck() to write into its record. null forgets it (tests). */
function recordBrowserVersion(browser) {
  launchedBrowserVersion = browser === null ? undefined : describeBrowserVersion(browser);
  return launchedBrowserVersion;
}

/** Lazy so the harness stays require()-able in CI, which has no browser binary. */
async function launchBrowser(config) {
  // Deliberately not a top-level require: see the module header.
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ ...getLaunchOptions(config.chromePath), headless: config.headless !== false });
  recordBrowserVersion(browser);
  return browser;
}

/**
 * A browser context for the application under test.
 *
 * `options` is merged over the defaults for the checks that need a context the
 * default cannot express -- in particular a browser that asks for a language
 * other than the runner's, which is how a locale check proves the server
 * answers the BROWSER's Accept-Language rather than its own JVM locale. Callers
 * that pass nothing get exactly the previous behaviour.
 */
async function newContext(browser, config, options = {}) {
  return browser.newContext({ ...options, ignoreHTTPSErrors: config.ignoreHTTPSErrors === true });
}

async function gotoApp(page, baseUrl, appPath, waitUntil = 'domcontentloaded') {
  return page.goto(appUrl(baseUrl, appPath), { waitUntil, timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback by default
}

async function assertNotErrorPage(page, label, options = {}) {
  const text = await page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report|Whitelabel Error Page/i.test(text), `${label} rendered an error page`);
  if (!text.trim() && options.allowPdf && await page.locator('embed[type="application/pdf"], link[href^="chrome-extension://"][href$="/pdf_embedder.css"]').count() > 0) {
    // Chromium's native PDF viewer (embed or extension-backed) has no body
    // text. Accept it only after the
    // UI's actual destination returns a successful PDF with complete file bytes;
    // an HTML login/error response or a truncated PDF must still fail.
    const response = await page.context().request.get(page.url(), { timeout: 20000 });
    try {
      assert(response.status() === 200 && /^application\/pdf(?:;|$)/i.test(response.headers()['content-type'] || ''),
        `${label} did not return a successful PDF response`);
      const bytes = await response.body();
      assert(bytes.length > 100 && bytes.subarray(0, 5).toString() === '%PDF-'
        && /%%EOF\s*$/.test(bytes.subarray(-1024).toString()), `${label} returned an incomplete PDF`);
      return 'Validated PDF document';
    } finally {
      await response.dispose();
    }
  }
  assert(text.trim().length > 0, `${label} rendered a blank page (${withoutQueryStrings(page.url())})`);
  return text;
}

// The one-time-code field of mfa_otp_handler.jsp (the older ids kept for other skins).
const MFA_CODE_INPUT = '#otpInput, input[name="code"][autocomplete="one-time-code"], input[name="mfaCode"], #mfaCode';

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
    // Login2Action renders the MFA challenge as a forward, not a redirect, so it
    // arrives at the form's own /login address: that landing counts too, and the
    // loop below recognises the challenge by its code field.
    page.waitForURL(url => /providercontrol|appointment|forcepasswordreset|loginMfa|select_facility/i.test(String(url))
      || /\/login$/.test(new URL(String(url)).pathname), { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  // A LOOP, NOT A FIXED ORDER. Login2Action can present these stages more than
  // once and in either order. Its forced-reset branch runs BEFORE the MFA
  // branch, so an enrolled account with a flagged password sees the reset form
  // first -- and after a successful reset the action sets forcedpasswordchange
  // = false and falls through to the shared path, which reaches
  // beginPendingMfaChallenge (Login2Action:630) and serves the MFA page a
  // second time. Handling each stage once, in order, left such an account
  // sitting on the MFA page with login() reporting success.
  //
  // Bounded so a stage that keeps re-serving itself (a wrong OTP, a reset the
  // server rejects) fails with a diagnosis instead of spinning.
  const STAGES = 4;
  for (let stage = 0; stage < STAGES; stage += 1) {
    const url = page.url();
    // The challenge (mfa_otp_handler.jsp) is served at /login, /mfa/loginMfa or
    // /forcepasswordresetSubmit, so it is recognised by its code field first.
    if (/loginMfa/i.test(url) || await page.locator(MFA_CODE_INPUT).count() > 0) {
      assert(typeof options.mfaCode === 'function',
        `${config.testUser} is enrolled in MFA; pass options.mfaCode to supply the challenge response`);
      const code = String(await options.mfaCode());
      // Wait for the main frame to navigate, not for a URL pattern: the challenge
      // itself can already sit on /mfa/loginMfa or /forcepasswordresetSubmit, and a
      // URL wait that matches the current address returns before the page's own
      // six-digit auto-submit lands, so the loop would type the code a second time.
      const landed = page.waitForEvent('framenavigated', { predicate: frame => frame === page.mainFrame(), timeout: 30000 });
      landed.catch(() => {});
      const codeInput = page.locator(MFA_CODE_INPUT).first();
      // Only mfa_otp_handler.jsp's #otpInput submits its own form once six digits
      // are typed; any other code field (and a code that is not six digits) needs
      // Verify pressed. Never both, so the one-time challenge is posted once.
      const autoSubmits = await codeInput.evaluate(input => input.id === 'otpInput');
      await codeInput.fill(code);
      if (!autoSubmits || !/^\d{6}$/.test(code)) {
        await page.locator('#verifyButton, input[type="submit"], button[type="submit"]').first().click();
      }
      await landed;
      await page.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
      await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      continue;
    }

    if (/forcepasswordreset/i.test(url)) {
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
      // Wait for the main frame to commit the submit's response, not for a URL:
      // when the reset hands straight to the MFA challenge, Login2Action forwards
      // mfa_otp_handler.jsp in place, so the challenge sits at
      // /forcepasswordresetSubmit and a URL list would time out. The next turn of
      // this loop recognises the challenge by its code field.
      await settleOperations([
        page.waitForEvent('framenavigated', { predicate: frame => frame === page.mainFrame(), timeout: 30000 }),
        page.locator('input[type="submit"], button[type="submit"]').first().click(),
      ]);
      await page.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
      await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      continue;
    }

    break;
  }

  assert(await page.locator(MFA_CODE_INPUT).count() === 0,
    `login is still on the MFA challenge (${pathOnly(page.url())}): the one-time code is being refused`);
  // Accepting a /login landing (the MFA forward) must not turn a refused
  // password into a silent success: a re-rendered login form is a refusal.
  assert(await page.locator('#username, input[name="password"]').count() === 0,
    `login is still on the login form (${pathOnly(page.url())}): the credentials were refused`);
  assert(!/loginMfa|forcepasswordreset/i.test(page.url()),
    `login is still on ${pathOnly(page.url())} after working through the authentication stages, so the `
    + 'credentials or the OTP are being refused rather than the flow having more steps');

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
  // SCREENSHOT_DIR is optional (readConfig defaults it to ''): screenshots are diagnostics, so an
  // unset directory captures nothing instead of failing a check that otherwise passed. A directory
  // that IS set still goes through the artifact-path validation below.
  if (screenshotDir === undefined || screenshotDir === null || String(screenshotDir).trim() === '') return null;
  const outputPath = buildArtifactPath(screenshotDir, name);
  await page.screenshot({ path: outputPath, fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
  return outputPath;
}

/**
 * Playwright launch options that stop Playwright installing its own SIGINT and
 * SIGTERM handlers. Spread them into chromium.launch() beside
 * installCleanupSignalHandlers(): Playwright's handler calls process.exit() as
 * soon as it has closed the browser, which can land before the fixture cleanup
 * has run, so the two must not both be registered.
 */
const NO_PLAYWRIGHT_SIGNAL_HANDLING = Object.freeze({ handleSIGINT: false, handleSIGTERM: false });

/**
 * Run `cleanup` when the process is interrupted, then exit 130 (SIGINT) or 143
 * (SIGTERM) (issue #3600).
 *
 * A `finally` does not run when the process is killed, so a Ctrl-C or a CI
 * timeout after fixture creation would otherwise leave synthetic patients,
 * appointments and prescriptions behind, and any cleartext-password file the
 * check wrote. `cleanup` may be sync or async and must be idempotent and touch
 * only rows this run created -- the same function the check's `finally` calls.
 *
 * The in-flight promise is memoised so a second signal cannot start a second
 * concurrent cleanup. A cleanup failure is reported, never swallowed, and still
 * exits with the signal code. A signal arriving while the check's own `finally`
 * is mid-cleanup runs `cleanup` again, which is why it must be idempotent.
 *
 * Returns { dispose() } which removes the listeners; call it once the check has
 * finished and its own cleanup has run.
 */
function installCleanupSignalHandlers(cleanup, options = {}) {
  assert(typeof cleanup === 'function', 'installCleanupSignalHandlers needs a cleanup function');
  const signalProcess = options.signalProcess || process;
  const exit = options.exit || ((code) => process.exit(code));
  const logError = options.logError || ((message) => console.error(message));
  let inFlight;
  const handlers = new Map();
  for (const [signal, exitCode] of [['SIGINT', 130], ['SIGTERM', 143]]) {
    const handler = () => {
      if (!inFlight) {
        logError(`${signal} received; running fixture cleanup before exiting.`);
        inFlight = Promise.resolve()
          .then(cleanup)
          .catch((error) => logError(`Cleanup after ${signal} failed: ${(error && error.message) || error}`));
      }
      return inFlight.finally(() => exit(exitCode));
    };
    handlers.set(signal, handler);
    // process.on, not once: a second signal while cleanup runs must be absorbed
    // here rather than fall through to Node's default terminate-immediately.
    signalProcess.on(signal, handler);
  }
  return {
    dispose() {
      for (const [signal, handler] of handlers) signalProcess.removeListener(signal, handler);
    },
  };
}

/**
 * Record on `error` the label of the step that threw it, so runCheck() can report `failedStep`.
 *
 * WHY. The manifest's `expectedFailure: { finding, step }` says a check fails at one specific step
 * while a logged defect stands. The runner can only tell "that step" from "a new failure somewhere
 * else" if the failing step's label travels with the failure. The INNERMOST label wins, so a
 * wrapper such as `const step = (label, body) => s.step(label, ...)` or an outer step around a
 * nested one cannot relabel a failure a deeper step already named.
 *
 * The property is non-enumerable: console.error(error) and JSON serialisation of the error stay
 * exactly as they were. A thrown primitive cannot carry a property and passes through unchanged.
 * Returns `error` so a catch block can `throw markFailedStep(error, label)`.
 */
function markFailedStep(error, label) {
  if (error !== null && typeof error === 'object' && typeof label === 'string' && label
    && typeof error.failedStep !== 'string') {
    try {
      Object.defineProperty(error, 'failedStep', { value: label, enumerable: false, configurable: true });
    } catch {
      // A frozen or exotic error object: the failure is still reported, only unlabelled.
    }
  }
  return error;
}

/** A header the application's own filters add to every response and the WAF's nginx error page lacks. */
const APPLICATION_HEADER = 'x-permitted-cross-domain-policies';

/**
 * The statuses a caller may add to assertRefused's refusals (alsoRefusedBy), each a deliberate answer
 * to the request's content rather than to its address: 409 (a conflicting or replayed submission),
 * 415 (a content type the resource does not consume) and 422 (an entity it will not process).
 */
const ALSO_REFUSED_STATUSES = Object.freeze([409, 415, 422]);

/**
 * The front door's block page: nginx + ModSecurity answer 403 to text the application would have
 * accepted (the check's own fixture text can trip CRS rules). It says nothing about the route.
 */
function isWafPage(status, body) {
  return status === 403 && /ModSecurity|<center>nginx<\/center>/i.test(body || '');
}

/** CARLOS's refusal page (securityError.jsp, the global SecurityException result). */
function isSecurityErrorBody(body) {
  return /Security Exception/i.test(body || '') && /insufficient privileges/i.test(body || '');
}

/**
 * CARLOS's generic error page (errorpage.jsp, titled "Error Page"), which a sendError(405) renders.
 * Only a fallback proof of origin for a 405: the header is the primary proof, and the title is
 * localised, so a deployment in another locale relies on the header.
 */
function isApplicationErrorPage(body) {
  return /<title>\s*Error Page\s*<\/title>/i.test(body || '');
}

/** Normalise a Playwright APIResponse/Response, or a pre-read { status, body, headers }. */
async function readRefusalEvidence(response) {
  assert(response && typeof response === 'object', 'assertRefused needs the response of the request under test');
  const read = (member) => (typeof response[member] === 'function' ? response[member]() : response[member]);
  const rawHeaders = read('headers') || {};
  const headers = {};
  for (const [name, value] of Object.entries(rawHeaders)) headers[name.toLowerCase()] = value;
  let body = '';
  if (typeof response.text === 'function') {
    body = await response.text().catch(() => '');
  } else if (typeof response.body === 'string') {
    body = response.body;
  }
  return { status: read('status'), headers, body: String(body || '') };
}

/**
 * Did the APPLICATION refuse? Returns { refused, evidence } or { refused: false, problem }.
 * Nothing here echoes the body or the address: the message lands in stdout and RESULT_JSON.
 */
function judgeRefusal({ status, headers, body }, alsoRefusedBy = []) {
  if (isWafPage(status, body)) {
    return {
      refused: false,
      problem: 'WAF refusal, not an application refusal: the 403 is the ModSecurity/nginx front door\'s page, '
        + 'so it says nothing about the route under test',
    };
  }
  const fromApplication = Object.prototype.hasOwnProperty.call(headers, APPLICATION_HEADER);
  if (status === 405) {
    // A 405 is no more self-evidently the application's than a 403: the nginx front door and the
    // container's default servlet answer 405 too (a POST to a static resource, a disallowed
    // verb), and neither says anything about the route under test.
    if (fromApplication) return { refused: true, evidence: `HTTP 405 carrying ${APPLICATION_HEADER}` };
    if (isApplicationErrorPage(body)) return { refused: true, evidence: 'HTTP 405 application error page' };
    return {
      refused: false,
      problem: `HTTP 405 whose origin cannot be shown to be the application (no ${APPLICATION_HEADER} header `
        + 'and not the application\'s error page)',
    };
  }
  if (status === 403) {
    if (fromApplication) return { refused: true, evidence: `HTTP 403 carrying ${APPLICATION_HEADER}` };
    if (isSecurityErrorBody(body)) return { refused: true, evidence: 'HTTP 403 securityError page' };
    return {
      refused: false,
      problem: `HTTP 403 whose origin cannot be shown to be the application (no ${APPLICATION_HEADER} header `
        + 'and not the securityError page)',
    };
  }
  // A status the caller named as this request's deliberate refusal (a JAX-RS resource that
  // @Consumes JSON answers any other content type with 415 before the method runs) counts only
  // with the application header: the front door and the container can answer it too.
  if (alsoRefusedBy.includes(status)) {
    if (fromApplication) return { refused: true, evidence: `HTTP ${status} carrying ${APPLICATION_HEADER}` };
    return {
      refused: false,
      problem: `HTTP ${status} whose origin cannot be shown to be the application (no ${APPLICATION_HEADER} header)`,
    };
  }
  // An include()d gate cannot set a status, so the securityError page can arrive under a 200.
  if (status >= 200 && status < 300 && isSecurityErrorBody(body)) {
    return { refused: true, evidence: `HTTP ${status} securityError page` };
  }
  if (status >= 300 && status < 400 && /securityError|noRights/i.test(headers.location || '')) {
    return { refused: true, evidence: `HTTP ${status} redirect to the securityError page` };
  }
  let hint = '';
  if (status === 404) hint = ' (route not found)';
  else if (status >= 500) hint = ' (server error)';
  else if (status >= 200 && status < 300) hint = ' (the route served the request)';
  else if (status >= 300 && status < 400) hint = ` (redirect to ${pathOnly(headers.location) || 'nowhere'})`;
  return {
    refused: false,
    problem: `HTTP ${status}${hint} is not the application's 403/405/securityError refusal`,
  };
}

/**
 * Assert that the application refused a request AND wrote nothing.
 *
 * THE SHARED REFUSAL ASSERTION for every GET-reject, CSRF and authorization probe. It passes only
 * when BOTH hold:
 *   1. the answer is the application's own refusal: a 405 or a 403 that CARLOS wrote (its
 *      X-Permitted-Cross-Domain-Policies header, which ResponseDefaultsFilter adds to every
 *      response and the front door's nginx pages lack; or, for a 403, its securityError page, and
 *      for a 405, its error page), or a redirect to securityError. A bare status is NOT enough, 405
 *      included (the front door and the default servlet answer 405 too): a 404 is
 *      a mistyped route, a 5xx is a crash, and a ModSecurity 403 is the front door reacting to the
 *      check's own fixture text, so a check that accepted any of them would pass without ever
 *      reaching the code under test;
 *   2. `SELECT COUNT(*) FROM table WHERE where` equals `before`, the count the caller took BEFORE
 *      sending the request. Rows unchanged alone prove nothing either (the WAF blocks before the
 *      application runs), which is why the two are required together.
 * Every problem found is named in one error.
 *
 * `response` is a Playwright APIResponse or Response, or a pre-read { status, body, headers }.
 * `table` must be a plain table name and `where` a fragment that selects only rows the calling
 * check owns; both come from the check's source, never from request data. `label` (optional) names
 * the request in the message. `alsoRefusedBy` (optional, default none) names further HTTP statuses
 * that are THIS request's deliberate refusal, such as 415 from a JAX-RS resource that @Consumes JSON
 * when the probe sends another content type; one of them counts only when it carries the
 * application header. Only ALSO_REFUSED_STATUSES can be named: a 404 (a mistyped route) or a 400 says
 * nothing about the defence under test, so it can never be declared a refusal. Returns
 * { status, evidence, rows }.
 */
async function assertRefused(s, {
  response, table, where, before, label = 'the request', alsoRefusedBy = [],
} = {}) {
  assert(Array.isArray(alsoRefusedBy) && alsoRefusedBy.every(status => ALSO_REFUSED_STATUSES.includes(status)),
    `assertRefused: alsoRefusedBy names only ${ALSO_REFUSED_STATUSES.join(', ')}`);
  assert(s && s.sql && typeof s.sql.value === 'function', 'assertRefused needs the workflow session (s.sql.value)');
  assert(before !== undefined && before !== null && String(before).trim() !== '',
    'assertRefused needs the COUNT(*) taken before the request (before)');
  assert(typeof table === 'string' && /^[A-Za-z_][A-Za-z0-9_]*$/.test(table),
    'assertRefused: table must be a plain table name');
  assert(typeof where === 'string' && where.trim() !== '',
    'assertRefused needs a where clause that selects only the rows the check owns');
  const evidence = await readRefusalEvidence(response);
  const verdict = judgeRefusal(evidence, alsoRefusedBy);
  const rows = String(s.sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${where}`));
  const problems = [];
  if (!verdict.refused) problems.push(verdict.problem);
  if (rows !== String(before)) {
    problems.push(`${label} changed ${table}: COUNT(*) was ${before} before the request and ${rows} after`);
  }
  assert(problems.length === 0, `${label} was not shown to be refused: ${problems.join('; ')}`);
  return { status: evidence.status, evidence: verdict.evidence, rows };
}

/**
 * The one entry point a check's main() should use.
 *
 * Standardises what 75 scripts each did differently: the SIGINT/SIGTERM handler
 * (issue #3600 -- 52 scripts had none, so an interrupted run left its fixture
 * rows behind), the PASS/FAIL/SKIP reporting line the runner parses, the exit
 * code, and running cleanup in a finally even when the body threw.
 *
 * A failure also reports WHICH labelled step threw (`failedStep`, set by markFailedStep, which
 * workflow-session's step() calls) and whether cleanup failed (`cleanupFailed`). A script that
 * runs through runCheck directly and labels its own steps wraps each body with markFailedStep to
 * take part in the manifest's expectedFailure bookkeeping. The record also carries `browserVersion`
 * ("chromium 154.0.8025.0") when launchBrowser() started a browser in this process.
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
  // The label of the step that threw (see markFailedStep), for the runner's expectedFailure
  // comparison. Never set for a skip or an interruption: neither is a failure AT a step.
  let failedStep;
  let cleanupFailed = false;
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
      if (error && typeof error.failedStep === 'string' && error.failedStep) {
        failedStep = error.failedStep;
      }
    }
  } finally {
    if (options.cleanup) {
      try {
        await options.cleanup();
      } catch (cleanupError) {
        // ANY cleanup failure is a failure, including one on a skipped check.
        // Promoting only from PASS left a SKIP reporting exit 2 -- "nothing to
        // test here" -- while its fixture rows stayed in the database for the
        // next run to inherit.
        outcome = 'FAIL';
        cleanupFailed = true;
        detail = `${detail ? `${detail}; ` : ''}cleanup failed: ${(cleanupError && cleanupError.message) || 'unknown'}`;
      }
    }
    cancellation.dispose();
  }
  const durationMs = (options.now ? options.now() : Date.now()) - started;
  out.log(`${outcome} ${name}${detail ? ` -- ${detail}` : ''}`);
  // failedStep and cleanupFailed are written only when they carry information, so a passing
  // record keeps its original four fields. The runner reads both: a failure at the step the
  // manifest expects is a known failure only if its cleanup also left nothing behind.
  // browserVersion only when this process launched a browser through launchBrowser(): a check that
  // skipped before launching, or drives Playwright itself, records none rather than a guess.
  const browserVersion = launchedBrowserVersion;
  const record = {
    name, outcome, detail, durationMs,
    ...(failedStep ? { failedStep } : {}),
    ...(cleanupFailed ? { cleanupFailed: true } : {}),
    ...(browserVersion ? { browserVersion } : {}),
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
  APPLICATION_HEADER,
  ALSO_REFUSED_STATUSES,
  assertNoPageErrors,
  assertNotErrorPage,
  assertRefused,
  isApplicationErrorPage,
  assertStrictPage,
  buildArtifactPath,
  buildFailureDetails,
  pathOnly,
  withoutQueryStrings,
  createRecorder,
  createSqlRunner,
  getLatestRequest,
  getLaunchOptions,
  gotoApp,
  installCleanupSignalHandlers,
  NO_PLAYWRIGHT_SIGNAL_HANDLING,
  insertId,
  isLocalTlsTarget,
  isWafPage,
  launchBrowser,
  describeBrowserVersion,
  recordBrowserVersion,
  loadConsoleBaseline,
  login,
  markFailedStep,
  newContext,
  parseMysqlBatchOutput,
  readConfig,
  runCheck,
  screenshot,
  sqlString,
  unescapeMysqlBatchValue,
  validateBaseUrl,
  relabelStrictPage,
  validateMysqlHost,
  withExpectedDialogs,
  wirePage,
  wireStrictPage,
};
