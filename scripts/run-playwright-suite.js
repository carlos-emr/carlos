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
 * Run the browser-check suite from scripts/playwright-suite.json.
 *
 * WHAT THIS REPLACES. docs/ui-tests/deb-install-validation.md section 6 carried
 * the suite as a bash for-loop with two `case` exclusions, one per-script timeout
 * override computed inline, and a separate hand-written phase for the check that
 * has to run last. That loop could not be run in CI, could not select a tier,
 * produced no machine-readable result, and every new check needed the prose
 * edited in two places. It also silently conflated a check that skipped for a
 * missing fixture with one that failed (issue #3313 reported two such skips as
 * failures).
 *
 * WHAT IT ADDS OVER THE LOOP.
 *   - tier selection, so CI can run a 12-minute smoke tier and the deb runbook
 *     the full pass, from one definition;
 *   - exit code 2 from a check means SKIP, not failure;
 *   - a refusal to run database-mutating checks against a non-local target
 *     without the explicit opt-in, which the loop could not enforce;
 *   - the build-identity guard the loop did by counting systemd restarts: the
 *     application must be the same process at the end as at the start, so a
 *     suite cannot finish green having tested two different deployments;
 *   - JUnit XML for CI, and a summary table a human can read;
 *   - known-failure bookkeeping. A check asserts correct behaviour, so while a
 *     logged defect stands it fails at the step that exercises it. The manifest
 *     records that as `expectedFailure: { finding, step }` (finding = a row of
 *     docs/ui-tests/app-findings-log.md that is not `fixed`; step = the label the
 *     script gives s.step()), and the run reports one of three outcomes:
 *       known-fail        failed at exactly that step: reported, does not fail the run
 *       unexpected-pass   passed although a failure was expected: reported, does not
 *                         fail the run (the defect may be fixed; update the manifest)
 *       failed-elsewhere  failed at any other step, outside a labelled step, in its
 *                         cleanup, by timeout or interruption: FAILS the run, so a
 *                         known defect cannot hide a new one.
 *   - the browser version. A failure that comes from the browser (Chromium 154 refuses a
 *     beforeunload prompt from a handler that removes itself, and names a non-ASCII download
 *     "download" under the POSIX locale) looks exactly like one that comes from the application, so
 *     a result is only readable next to the browser that produced it. A check that starts its
 *     browser through the harness's launchBrowser() reports it in its RESULT_JSON record
 *     (`browserVersion`); the runner keeps it on that check's result, prints the distinct versions
 *     once under the summary, and writes them into the JUnit <properties> as `browserVersion`. A
 *     check that never launched one (or drives Playwright itself) reports none.
 *   - --residue-audit. A check that changes the shared install and does not put it back
 *     poisons every check after it (finding 180: fax-configure left a fake SRFax account
 *     polling, and FaxImporter logged an ERROR a minute until the next restart). With the flag
 *     the runner takes a baseline of fax_config, the encounterForm registrations and the
 *     property rows the selected checks' manifest `mutates` names BEFORE the first check, and
 *     audits AFTER the last (scripts/lib/residue-audit.js): marker-named fixture rows that
 *     survive, any difference from the baseline, and any table that has MORE or FEWER ROWS than the
 *     baseline's exact count of every base table (so a table with no marker column cannot leak
 *     unseen, and a check cannot delete rows it does not own unseen; only the tables that grow on
 *     every run, ROW_GROWTH_ALLOWED, are exempt from "more", and are named in a line of their own;
 *     the diff is net, so a delete and an insert in one table cancel out). It prints
 *     `residue: <table> <count>` per table (`<count> (rows added)` or `(rows removed)` for the
 *     row-count diff), never a row, and exits non-zero on residue; a clean audit prints
 *     `residue audit: no residue`. It needs MYSQL_* like a database-asserting check, and a run
 *     that cannot take its baseline stops before any check starts rather than pass unaudited.
 *     It also needs the install to itself: another session using the application during an audited
 *     run adds and removes rows that are read as that run's residue.
 *
 * Usage:
 *   node scripts/run-playwright-suite.js --tier smoke
 *   node scripts/run-playwright-suite.js --tier core --tier front-door --junit out.xml
 *   node scripts/run-playwright-suite.js --only tickler-crud --only login
 *   node scripts/run-playwright-suite.js --only fax-configure --residue-audit
 *   node scripts/run-playwright-suite.js --list
 */

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const {
  EXIT_FAIL, EXIT_PASS, EXIT_SKIP, createSqlRunner, isLocalTlsTarget, readConfig, validateBaseUrl, validateMysqlHost,
} = require('./lib/playwright-harness');
const { auditResidueDetailed, captureBaseline, describeResidue, formatResidue } = require('./lib/residue-audit');

const MANIFEST_PATH = path.join(__dirname, 'playwright-suite.json');

function loadManifest(manifestPath = MANIFEST_PATH) {
  const parsed = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  if (!Array.isArray(parsed.checks)) {
    throw new Error(`${manifestPath} has no checks array`);
  }
  return parsed.checks;
}

function parseArguments(argv) {
  const options = {
    tiers: [], only: [], skip: [], province: '', junit: '', list: false, dryRun: false, residueAudit: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    const takeValue = () => {
      const value = argv[index + 1];
      if (value === undefined || value.startsWith('--')) {
        throw new Error(`${argument} needs a value`);
      }
      index += 1;
      return value;
    };
    switch (argument) {
      case '--tier': options.tiers.push(takeValue()); break;
      case '--only': options.only.push(takeValue()); break;
      case '--skip': options.skip.push(takeValue()); break;
      case '--province': options.province = takeValue(); break;
      case '--junit': options.junit = takeValue(); break;
      case '--list': options.list = true; break;
      case '--dry-run': options.dryRun = true; break;
      case '--residue-audit': options.residueAudit = true; break;
      case '--help': options.help = true; break;
      default: throw new Error(`Unknown argument ${argument}`);
    }
  }
  return options;
}

function selectChecks(checks, options) {
  // A typo in one --only used to silently omit that check when another name
  // matched. The resulting green report did not cover the requested selection.
  const names = new Set(checks.map(check => check.name));
  for (const name of [...options.only, ...options.skip]) {
    if (!names.has(name)) throw new Error(`Unknown check name: ${name}; use --list for exact names`);
  }
  // A manual check is destructive to the shared credential (the packaged
  // install's first login changes the administrator password) and needs
  // input the harness contract does not carry; it runs only when asked for
  // by name, never as part of a tier or of the whole-suite default.
  // --list still shows it, marked, so the name is discoverable.
  let selected = checks.filter((check) => !check.manual || options.list || options.only.includes(check.name));
  if (options.tiers.length) {
    selected = selected.filter((check) => check.tiers.some((tier) => options.tiers.includes(tier)));
  }
  if (options.only.length) {
    selected = selected.filter((check) => options.only.includes(check.name));
  }
  if (options.skip.length) {
    selected = selected.filter((check) => !options.skip.includes(check.name));
  }
  if (options.province) {
    selected = selected.filter((check) => check.provinces.includes('all') || check.provinces.includes(options.province));
  }
  // A check that mutates shared state the others read must not run in the middle
  // of them: login's deliberate failed-password probes can lock the shared test
  // account for every check that has not run yet.
  return [
    ...selected.filter((check) => !check.runLast),
    ...selected.filter((check) => check.runLast),
  ];
}

/**
 * Refuse to seed and delete rows in a database that is not demonstrably
 * disposable. The bash loop had no way to check this; a mistyped BASE_URL
 * pointed the whole mutating suite at whatever answered.
 */
/**
 * Refuse to drive an unfamiliar deployment without an explicit opt-in.
 *
 * WHY THE GUARD IS NOT SCOPED TO assertsDatabase. It used to be, and that was
 * wrong in the unsafe direction: assertsDatabase means "this check reads rows
 * back out of MariaDB to prove what happened", which is a different question
 * from "this check writes". eform-admin-crud, allergy-rx-alert and
 * demographic-master-crud-smoke all create and delete records through the UI
 * with assertsDatabase false, so the old test let every one of them run
 * unguarded against any host. Every check in this suite logs in as a real
 * provider and clicks through a live EMR; none of them is safe to point at a
 * deployment someone else is using. So the opt-in is required for a non-local
 * BASE_URL whatever is selected, and assertsDatabase only sharpens the message.
 */
function assertSafeTarget(checks, env) {
  if (!checks.length) {
    return;
  }
  // THE DATABASE TARGET IS A SEPARATE QUESTION FROM THE WEB TARGET, and it is
  // the more dangerous one: eleven checks in this suite still read MYSQL_HOST
  // themselves rather than going through createSqlRunner, so nothing else in the
  // run validates it. A local BASE_URL with a remote MYSQL_HOST would otherwise
  // sail through this gate and then seed, update and delete rows in that remote
  // database. The runner spawns every one of them, so this is the one place that
  // can close it for all of them at once.
  if (env.MYSQL_HOST) {
    validateMysqlHost(env.MYSQL_HOST, env);
  }
  const baseUrl = validateBaseUrl(env.BASE_URL || DEFAULT_BASE_URL, env);
  if (isLocalTlsTarget(baseUrl) || env.ALLOW_NON_LOCAL_BASE_URL === 'true') {
    return;
  }
  const asserting = checks.filter((check) => check.assertsDatabase).length;
  throw new Error(
    `${checks.length} selected check(s) drive a live CARLOS session as a real provider`
    + `${asserting ? ` (${asserting} of them also read rows straight out of MariaDB)` : ''}, `
    + `and BASE_URL points at the non-local host ${baseUrl.hostname}. Set `
    + 'ALLOW_NON_LOCAL_BASE_URL=true only for a disposable test deployment.',
  );
}

// The devcontainer deployment. Shared by the target gate and the build-identity
// probe so a run cannot be gated against one address and fingerprinted against
// another -- or, as it was, fingerprinted against nothing.
const DEFAULT_BASE_URL = 'http://127.0.0.1:8080/carlos';

/**
 * The application's build identity, used to prove the suite tested one process.
 *
 * The runbook did this by reading systemd's NRestarts, which only works on a
 * packaged install. The build tag itself is authenticated-only (see
 * docs/build-identity.md) and the runner holds no session, so the unauthenticated
 * stand-in is the validator Tomcat serves for a static asset: redeploying a WAR
 * re-extracts it and changes its Last-Modified and ETag.
 *
 * WHAT THIS DOES AND DOES NOT DETECT. It detects the case that actually produces
 * misleading results -- the WAR being replaced under a running suite. It does NOT
 * detect a bare JVM restart with the same WAR, which would surface as mass login
 * failures anyway. Returning null (no CDN-style validators, or BASE_URL unset)
 * disables the comparison rather than inventing a verdict.
 */
function readBuildIdentity(env, run = spawnSync) {
  // The SAME default the target gate and the harness use. Reading BASE_URL
  // alone meant the commonest case of all -- a devcontainer run with nothing
  // exported -- fingerprinted nothing and disabled the redeploy comparison,
  // while the suite ran happily against that default deployment. The guard was
  // present, ran, and could not fire.
  const base = (env.BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, '');
  if (!base) {
    return null;
  }
  // HEAD a static asset and fingerprint the validators Tomcat serves for it.
  // The earlier version wrote the body to /dev/null and returned %{http_code},
  // which is "200" before and "200" after -- the comparison below could never
  // fire, so the guard existed but did nothing.
  //
  // -k ONLY for a loopback/private target, matching the same rule the browser
  // config uses (isLocalTlsTarget). The devcontainer serves a self-signed cert
  // and would otherwise disable the guard entirely; a remote deployment reached
  // under ALLOW_NON_LOCAL_BASE_URL=true must still have its certificate checked,
  // because "skip verification" there means this fingerprint can be supplied by
  // anyone on the path and the restart guard proves nothing.
  let local = false;
  try {
    local = isLocalTlsTarget(new URL(base));
  } catch {
    return null;
  }
  const result = run(
    'curl',
    [local ? '-sSkI' : '-sSI', '--max-time', '10', `${base}/images/favicon.ico`],
    { encoding: 'utf8' },
  );
  if (result.status !== 0) {
    return null;
  }
  const headers = String(result.stdout || '');
  if (!/^HTTP\/[\d.]+ 200\b/im.test(headers)) {
    return null;
  }
  // A line scan rather than a built regex: the repo's Semgrep rules flag
  // new RegExp(...) on principle, and nothing here needs one.
  const lines = headers.split(/\r?\n/);
  const pick = (name) => {
    const prefix = `${name.toLowerCase()}:`;
    const line = lines.find((candidate) => candidate.toLowerCase().startsWith(prefix));
    return line ? line.slice(prefix.length).trim() : '';
  };
  const fingerprint = [pick('ETag'), pick('Last-Modified'), pick('Content-Length')]
    .filter(Boolean).join('|');
  return fingerprint || null;
}

/**
 * The record runCheck() wrote to RESULT_JSON, or null when the child wrote none (it crashed
 * before reaching runCheck, was killed, or is a script that does not use it).
 */
function readCheckRecord(resultPath) {
  try {
    const record = JSON.parse(fs.readFileSync(resultPath, 'utf8'));
    return record && typeof record === 'object' ? record : null;
  } catch {
    return null;
  }
}

/**
 * The browser version a child reported, or undefined. The record is a file the child wrote, so the value
 * is data: only a short run of plain version characters is kept, because it is printed to the console and
 * written into the JUnit report.
 */
function cleanBrowserVersion(value) {
  return typeof value === 'string' && /^[A-Za-z0-9][\w .+-]{0,79}$/.test(value) ? value : undefined;
}

/** The distinct browser versions the results report, in order first seen (none when no check reported one). */
function browserVersionsOf(results) {
  return [...new Set(results.map((result) => result.browserVersion).filter(Boolean))];
}

/** Outcomes that fail the run. known-fail, unexpected-pass, SKIP and PASS do not. */
const FAILING_OUTCOMES = new Set(['FAIL', 'failed-elsewhere']);

function describeExpectation(expected) {
  return `step "${expected.step}" (finding ${expected.finding})`;
}

/**
 * Turn what a check did into the outcome the run reports, given what the manifest expects.
 *
 * `raw` is { outcome: PASS|FAIL|SKIP, detail, failedStep?, cleanupFailed? }. Without an
 * expectedFailure nothing changes. With one:
 *   PASS                                        -> unexpected-pass
 *   FAIL at exactly expectedFailure.step,
 *        cleanup clean                          -> known-fail
 *   any other FAIL (another step, no labelled
 *        step, cleanup, timeout, signal)        -> failed-elsewhere
 *   SKIP                                        -> SKIP (nothing was tested either way)
 * A failure with a leaking cleanup is never a clean known failure: the fixtures it left behind
 * are a new problem the known defect must not excuse.
 */
function classifyResult(check, raw) {
  const expected = check.expectedFailure;
  if (!expected || raw.outcome === 'SKIP') {
    return { outcome: raw.outcome, detail: raw.detail };
  }
  const label = describeExpectation(expected);
  if (raw.outcome === 'PASS') {
    return {
      outcome: 'unexpected-pass',
      detail: `passed although it was expected to fail at ${label}; if the finding is fixed, mark it fixed in `
        + 'app-findings-log.md and remove expectedFailure',
    };
  }
  if (raw.failedStep === expected.step && !raw.cleanupFailed) {
    return { outcome: 'known-fail', detail: `known failure at ${label}` };
  }
  const reasons = [];
  if (raw.failedStep && raw.failedStep !== expected.step) {
    reasons.push(`failed at step "${raw.failedStep}"`);
  } else if (!raw.failedStep) {
    reasons.push(`failed without reaching a labelled step (${raw.detail})`);
  }
  if (raw.cleanupFailed) {
    reasons.push('its cleanup failed, so owned fixtures may remain');
  }
  return { outcome: 'failed-elsewhere', detail: `expected to fail at ${label} but ${reasons.join(' and ')}` };
}

function runOne(check, options, run = spawnSync) {
  const started = Date.now();
  // Resolved against the repository, not the working directory. Manifest paths
  // are repo-relative, so invoking the runner from anywhere but the repo root --
  // which some CI wrappers do -- made Node fail to find the script and every
  // check "fail to start" for a reason that had nothing to do with the check.
  const script = path.resolve(__dirname, '..', check.script);
  // A private, per-check result record. runCheck() writes its outcome, failing step and
  // cleanup status to RESULT_JSON; the runner needs the failing step to tell a known failure
  // from a new one. The file is created fresh for each child and removed afterwards, so a
  // record can never be a previous check's, and the runner owns the variable for the children
  // it starts (an exported RESULT_JSON used to be overwritten by every check in turn).
  let resultDirectory = null;
  try {
    resultDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-check-result-'));
  } catch {
    // An unwritable temp directory costs the failing-step detail, not the run: a check
    // with an expectedFailure then reads failed-elsewhere, which fails safe.
  }
  const resultPath = resultDirectory ? path.join(resultDirectory, 'result.json') : null;
  try {
    const result = run(process.execPath, [script], {
      stdio: 'inherit',
      timeout: check.timeoutSec * 1000,
      // envSet lets one script back several named checks: the table-driven
      // families (surface-audit, direct-response-contract) are one engine plus a
      // row selector, and the runner has to pass the selector that picks the row.
      // Anything already exported still wins for the shared contract variables.
      // The CALLER's environment, not the process's. main() validates BASE_URL
      // and MYSQL_HOST out of the env it was handed, so reading process.env here
      // meant a caller passing an explicit environment gated one target and ran
      // the child against another -- exactly the disassociation assertSafeTarget
      // exists to prevent.
      //
      // envSet comes SECOND on purpose and the order is load-bearing: it is how
      // one script backs several named checks (SURFACE=inbox, SURFACE=report),
      // so a per-check selector has to beat an ambient value of the same name.
      // Spread the other way and every surface-audit row would run whichever
      // SURFACE happened to be exported, i.e. the same surface ten times.
      env: {
        ...(options.env || process.env),
        ...(check.envSet || {}),
        ...(resultPath ? { RESULT_JSON: resultPath } : {}),
      },
    });
    const durationMs = Date.now() - started;
    // Read once, for every outcome: the failing step matters only to a failure, but the browser a check
    // used is worth knowing for a pass too. A timeout wrote no record (it is written when the check ends).
    const record = resultPath ? readCheckRecord(resultPath) : null;
    const browserVersion = cleanBrowserVersion(record && record.browserVersion);
    let raw;
    if (result.error && result.error.code === 'ETIMEDOUT') {
      raw = { outcome: 'FAIL', detail: `timed out after ${check.timeoutSec}s` };
    } else if (result.status === EXIT_PASS) {
      raw = { outcome: 'PASS', detail: '' };
    } else if (result.status === EXIT_SKIP) {
      raw = { outcome: 'SKIP', detail: 'a fixture or credential this check needs is not configured' };
    } else {
      // The failing step counts only for an ordinary failure (exit 1) whose record says FAIL:
      // exit 130/143 is an interruption, and a missing record means the child never reached runCheck.
      const ordinary = result.status === EXIT_FAIL && record && record.outcome === 'FAIL';
      const failedStep = ordinary && typeof record.failedStep === 'string' && record.failedStep
        ? record.failedStep : undefined;
      raw = {
        outcome: 'FAIL',
        detail: `exit ${result.status === null ? 'signal' : result.status}${failedStep ? ` (failed at step "${failedStep}")` : ''}`,
        failedStep,
        cleanupFailed: Boolean(ordinary && record.cleanupFailed),
      };
    }
    return { name: check.name, ...classifyResult(check, raw), durationMs, ...(browserVersion ? { browserVersion } : {}) };
  } finally {
    if (resultDirectory) fs.rmSync(resultDirectory, { recursive: true, force: true });
  }
}

function escapeXml(value) {
  return String(value).replace(/[<>&"']/g, (character) => ({
    '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;',
  }[character]));
}

function toJUnit(results) {
  // failed-elsewhere is a failure (it fails the run). known-fail is a skipped testcase whose
  // message names the finding, so CI shows it without counting it as red; unexpected-pass is a
  // passing testcase that carries its warning in <system-out>.
  const failures = results.filter((result) => FAILING_OUTCOMES.has(result.outcome)).length;
  const skipped = results.filter((result) => result.outcome === 'SKIP' || result.outcome === 'known-fail').length;
  const cases = results.map((result) => {
    const time = (result.durationMs / 1000).toFixed(3);
    const open = `    <testcase classname="playwright-suite" name="${escapeXml(result.name)}" time="${time}">`;
    // Only the new outcomes are prefixed, so the original FAIL/SKIP messages are unchanged.
    const message = (fallback) => `${['FAIL', 'SKIP'].includes(result.outcome) ? '' : `${result.outcome}: `}${result.detail || fallback}`;
    if (FAILING_OUTCOMES.has(result.outcome)) {
      return `${open}\n      <failure message="${escapeXml(message('failed'))}"/>\n    </testcase>`;
    }
    if (result.outcome === 'SKIP' || result.outcome === 'known-fail') {
      return `${open}\n      <skipped message="${escapeXml(message('skipped'))}"/>\n    </testcase>`;
    }
    if (result.outcome === 'unexpected-pass') {
      return `${open}\n      <system-out>${escapeXml(message('unexpected pass'))}</system-out>\n    </testcase>`;
    }
    return `${open}</testcase>`;
  }).join('\n');
  // The browser goes in <properties> (the JUnit place for run metadata), once for the suite, and only when a
  // check reported one, so a report from a run that launched no browser is unchanged.
  const versions = browserVersionsOf(results);
  const properties = versions.length
    ? `\n    <properties>\n      <property name="browserVersion" value="${escapeXml(versions.join(', '))}"/>\n    </properties>`
    : '';
  return `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n  <testsuite name="carlos-playwright-suite" tests="${results.length}" failures="${failures}" skipped="${skipped}">${properties}\n${cases}\n  </testsuite>\n</testsuites>\n`;
}

function summarise(results, out = console) {
  const width = Math.max(...results.map((result) => result.name.length), 4);
  // known-fail, unexpected-pass and failed-elsewhere are longer than PASS/FAIL/SKIP.
  const outcomeWidth = Math.max(6, ...results.map((result) => result.outcome.length));
  out.log('');
  out.log(`  ${'RESULT'.padEnd(outcomeWidth)}  ${'CHECK'.padEnd(width)}  TIME`);
  for (const result of results) {
    out.log(`  ${result.outcome.padEnd(outcomeWidth)}  ${result.name.padEnd(width)}  ${(result.durationMs / 1000).toFixed(1)}s${result.detail ? `  -- ${result.detail}` : ''}`);
  }
  const counts = results.reduce((totals, result) => ({ ...totals, [result.outcome]: (totals[result.outcome] || 0) + 1 }), {});
  // The three known-failure outcomes are appended only when present, so a run with no
  // expectedFailure prints exactly the line it always did.
  const expectation = ['known-fail', 'unexpected-pass', 'failed-elsewhere']
    .filter((outcome) => counts[outcome]).map((outcome) => `${counts[outcome]} ${outcome}`);
  out.log('');
  out.log(`  ${counts.PASS || 0} passed, ${counts.FAIL || 0} failed, ${counts.SKIP || 0} skipped${expectation.length ? `, ${expectation.join(', ')}` : ''}`);
  // Once per run, not per row: the browser is a property of the run, and a second version is worth a line.
  const versions = browserVersionsOf(results);
  if (versions.length) out.log(`  browser: ${versions.join(', ')}`);
}

/** 1 when any result is FAIL or failed-elsewhere; known-fail and unexpected-pass are reported only. */
function exitCodeFor(results) {
  return results.some((result) => FAILING_OUTCOMES.has(result.outcome)) ? EXIT_FAIL : EXIT_PASS;
}

/**
 * Problems with a manifest entry's expectedFailure, as strings (none means valid).
 *
 * `statuses` maps findings-log row number -> status; `scriptSource` is the text of the check's
 * script alone and `source` that text plus the modules it requires (where step labels may live). The finding must be a row that
 * is not `fixed` -- a fixed defect must not keep excusing a failure -- and the step must be a
 * label the script really passes to step(), or failedStep could never equal it and the check
 * would read failed-elsewhere forever.
 */
function validateExpectedFailure(check, { statuses, source, scriptSource }) {
  const expected = check.expectedFailure;
  if (expected === null || typeof expected !== 'object' || Array.isArray(expected)) {
    return ['expectedFailure must be an object { finding, step }'];
  }
  const problems = [];
  const extra = Object.keys(expected).filter((key) => key !== 'finding' && key !== 'step');
  if (extra.length) {
    problems.push(`expectedFailure takes only finding and step; remove ${extra.join(', ')}`);
  }
  if (!Number.isInteger(expected.finding) || expected.finding < 1) {
    problems.push('expectedFailure.finding must be a positive integer (a row number in docs/ui-tests/app-findings-log.md)');
  } else if (!statuses.has(expected.finding)) {
    problems.push(`finding ${expected.finding} is not a row in docs/ui-tests/app-findings-log.md`);
  } else if (statuses.get(expected.finding) === 'fixed') {
    problems.push(`finding ${expected.finding} is fixed; remove expectedFailure (or cite the open finding the check fails on now)`);
  }
  // failedStep reaches the runner only through runCheck()'s RESULT_JSON record, and only for a
  // step that tags its error: runWorkflow's s.step() does, a script's own step helper must call
  // markFailedStep(). This reads the script's OWN text, never `source`: `source` includes the
  // modules the script requires, and lib/playwright-harness.js defines markFailedStep() and
  // runCheck() itself, so searching it would make every script look conforming. A caller that
  // passes no scriptSource therefore fails the guard instead of silently passing it.
  const own = typeof scriptSource === 'string' ? scriptSource : '';
  if (!/\brunWorkflow\(/.test(own) && !(/\brunCheck\(/.test(own) && /\bmarkFailedStep\(/.test(own))) {
    problems.push(`${check.script} reports through neither runWorkflow() nor a runCheck() whose steps call markFailedStep(), `
      + 'so the runner can never see its failing step');
  }
  if (typeof expected.step !== 'string' || expected.step.trim() === '') {
    problems.push('expectedFailure.step must be a non-empty string (the label the script gives step())');
  } else {
    // The label as it appears inside a quoted string literal in the script.
    const spellings = [
      expected.step,
      expected.step.replace(/\\/g, '\\\\').replace(/'/g, "\\'"),
      expected.step.replace(/\\/g, '\\\\').replace(/"/g, '\\"'),
      expected.step.replace(/\\/g, '\\\\').replace(/`/g, '\\`'),
    ];
    if (!spellings.some((spelling) => source.includes(spelling))) {
      problems.push(`${check.script} has no step labelled "${expected.step}"`);
    }
  }
  return problems;
}

/** The selected checks' manifest `mutates`, each entry once, in the order first seen. */
function mutatesOf(checks) {
  return [...new Set(checks.flatMap((check) => check.mutates || []))];
}

/**
 * The residue audit a real run uses: the harness mysql client, the baseline of scripts/lib/
 * residue-audit.js, and an audit against it. `begin` throws when the database cannot be reached
 * (MYSQL_PASSWORD unset, a non-loopback MYSQL_HOST without its opt-in), and main() then refuses
 * to run any check, because a run it cannot audit must not read as a clean one.
 */
const databaseResidueAudit = Object.freeze({
  begin({ env, mutates }) {
    const sql = createSqlRunner(readConfig({ env }).mysql, { env });
    let since;
    try {
      since = captureBaseline({ sql, mutates });
    } catch (error) {
      sql.dispose();
      throw error;
    }
    return {
      finish: () => auditResidueDetailed({ sql, since }),
      dispose: () => sql.dispose(),
    };
  },
});

/**
 * Run the audit, print its verdict and return the result row that makes residue fail the run
 * (null when clean). An audit that itself fails is a failure, never a pass: it proves nothing.
 */
function finishResidueAudit(audit, out) {
  let report;
  try {
    report = audit.finish();
  } catch (error) {
    out.error(`residue audit: could not run (${error.message})`);
    return {
      name: 'residue-audit',
      outcome: 'FAIL',
      detail: `the audit could not run (${error.message}), so the run is not known to be clean`,
      durationMs: 0,
    };
  }
  for (const line of formatResidue(report.residue)) out.log(line);
  // Say what the audit did NOT cover, so a clean verdict is not read as wider than it is.
  if (report.absent.length) out.log(`residue audit: not installed here: ${report.absent.join(', ')}`);
  // A table named in `mutates` is still counted (rows added or removed); only a row changed in place goes unseen. A file is not covered at all.
  const notDiffedFiles = report.notDiffed.filter((entry) => entry.startsWith('file:'));
  const notDiffedTables = report.notDiffed.filter((entry) => !entry.startsWith('file:'));
  if (notDiffedTables.length) out.log(`residue audit: not diffed: ${notDiffedTables.join(', ')} (only rows added or removed are counted)`);
  if (notDiffedFiles.length) out.log(`residue audit: not diffed: ${notDiffedFiles.join(', ')} (files are not covered)`);
  // The tables that grow on every run by design are not residue, but a reader should see how much the allow-list absorbed.
  if ((report.allowedGrowth || []).length) {
    out.log(`residue audit: rows added to tables that grow on every run (not residue): ${report.allowedGrowth.map(({ table, count }) => `${table} ${count}`).join(', ')}`);
  }
  if (!report.residue.length) return null;
  return {
    name: 'residue-audit',
    outcome: 'FAIL',
    detail: report.residue.map(describeResidue).join(', '),
    durationMs: 0,
  };
}

/**
 * `deps` lets a test supply the manifest, the child-process spawner, the build probe and the
 * residue audit; a real run uses scripts/playwright-suite.json, spawnSync, curl and MariaDB.
 */
function main(argv = process.argv.slice(2), env = process.env, out = console, deps = {}) {
  const run = deps.run || spawnSync;
  const probeIdentity = deps.readBuildIdentity || readBuildIdentity;
  let options;
  try {
    options = parseArguments(argv);
  } catch (error) {
    out.error(error.message);
    return EXIT_FAIL;
  }
  if (options.help) {
    out.log('Usage: node scripts/run-playwright-suite.js [--tier T]... [--only NAME]... [--skip NAME]... [--province ON|BC] [--junit FILE] [--residue-audit] [--list] [--dry-run]');
    return EXIT_PASS;
  }

  let selected;
  try {
    selected = selectChecks(deps.checks || loadManifest(), options);
  } catch (error) {
    out.error(error.message);
    return EXIT_FAIL;
  }
  if (!selected.length) {
    out.error('No checks matched the selection');
    return EXIT_FAIL;
  }
  if (options.list) {
    for (const check of selected) {
      out.log(`${check.name.padEnd(42)} ${check.tiers.join(',')}${check.manual ? ' (manual: runs only with --only)' : ''}`);
    }
    return EXIT_PASS;
  }

  try {
    assertSafeTarget(selected, env);
  } catch (error) {
    out.error(error.message);
    return EXIT_FAIL;
  }
  if (options.dryRun) {
    out.log(`Would run ${selected.length} check(s): ${selected.map((check) => check.name).join(', ')}`);
    return EXIT_PASS;
  }

  // The baseline comes BEFORE the first check and the audit AFTER the last, so everything a check
  // leaves behind is measured against the state the run started from.
  let audit = null;
  if (options.residueAudit) {
    try {
      audit = (deps.residueAudit || databaseResidueAudit).begin({ env, mutates: mutatesOf(selected) });
    } catch (error) {
      out.error(`residue audit: could not take the baseline, so no check was run (${error.message})`);
      return EXIT_FAIL;
    }
  }

  const results = [];
  try {
    const identityBefore = probeIdentity(env);
    for (const check of selected) {
      out.log(`\n--- ${check.name} (${check.tiers.join(',')}) ---`);
      // `env`, not process.env: main() validated BASE_URL and MYSQL_HOST out of
      // the environment it was HANDED, so the child has to receive that same one
      // or the gate and the run are about different deployments.
      results.push(runOne(check, { ...options, env }, run));
    }
    const identityAfter = probeIdentity(env);

    if (identityBefore && identityAfter && identityBefore !== identityAfter) {
      results.push({
        name: 'application-identity',
        outcome: 'FAIL',
        detail: 'the deployment changed while the suite ran, so these results span two different applications',
        durationMs: 0,
      });
    }
    const residue = audit ? finishResidueAudit(audit, out) : null;
    if (residue) results.push(residue);
  } finally {
    if (audit) audit.dispose();
  }

  summarise(results, out);
  if (options.junit) {
    fs.writeFileSync(options.junit, toJUnit(results));
    out.log(`  JUnit written to ${options.junit}`);
  }
  return exitCodeFor(results);
}

if (require.main === module) {
  process.exitCode = main();
}

module.exports = {
  assertSafeTarget, browserVersionsOf, classifyResult, cleanBrowserVersion, exitCodeFor, loadManifest, main, mutatesOf,
  parseArguments, readBuildIdentity, runOne, selectChecks, summarise, toJUnit, validateExpectedFailure,
};
