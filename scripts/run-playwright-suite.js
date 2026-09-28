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
 *   - JUnit XML for CI, and a summary table a human can read.
 *
 * Usage:
 *   node scripts/run-playwright-suite.js --tier smoke
 *   node scripts/run-playwright-suite.js --tier core --tier front-door --junit out.xml
 *   node scripts/run-playwright-suite.js --only tickler-crud --only login
 *   node scripts/run-playwright-suite.js --list
 */

const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const {
  EXIT_FAIL, EXIT_PASS, EXIT_SKIP, isLocalTlsTarget, validateBaseUrl, validateMysqlHost,
} = require('./lib/playwright-harness');

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
    tiers: [], only: [], skip: [], province: '', junit: '', list: false, dryRun: false,
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
  let selected = checks;
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

function runOne(check, options, run = spawnSync) {
  const started = Date.now();
  // Resolved against the repository, not the working directory. Manifest paths
  // are repo-relative, so invoking the runner from anywhere but the repo root --
  // which some CI wrappers do -- made Node fail to find the script and every
  // check "fail to start" for a reason that had nothing to do with the check.
  const script = path.resolve(__dirname, '..', check.script);
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
    env: { ...(options.env || process.env), ...(check.envSet || {}) },
  });
  const durationMs = Date.now() - started;
  if (result.error && result.error.code === 'ETIMEDOUT') {
    return { name: check.name, outcome: 'FAIL', detail: `timed out after ${check.timeoutSec}s`, durationMs };
  }
  if (result.status === EXIT_PASS) {
    return { name: check.name, outcome: 'PASS', detail: '', durationMs };
  }
  if (result.status === EXIT_SKIP) {
    return { name: check.name, outcome: 'SKIP', detail: 'a fixture or credential this check needs is not configured', durationMs };
  }
  return { name: check.name, outcome: 'FAIL', detail: `exit ${result.status === null ? 'signal' : result.status}`, durationMs };
}

function escapeXml(value) {
  return String(value).replace(/[<>&"']/g, (character) => ({
    '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;',
  }[character]));
}

function toJUnit(results) {
  const failures = results.filter((result) => result.outcome === 'FAIL').length;
  const skipped = results.filter((result) => result.outcome === 'SKIP').length;
  const cases = results.map((result) => {
    const time = (result.durationMs / 1000).toFixed(3);
    const open = `    <testcase classname="playwright-suite" name="${escapeXml(result.name)}" time="${time}">`;
    if (result.outcome === 'FAIL') {
      return `${open}\n      <failure message="${escapeXml(result.detail || 'failed')}"/>\n    </testcase>`;
    }
    if (result.outcome === 'SKIP') {
      return `${open}\n      <skipped message="${escapeXml(result.detail || 'skipped')}"/>\n    </testcase>`;
    }
    return `${open}</testcase>`;
  }).join('\n');
  return `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n  <testsuite name="carlos-playwright-suite" tests="${results.length}" failures="${failures}" skipped="${skipped}">\n${cases}\n  </testsuite>\n</testsuites>\n`;
}

function summarise(results, out = console) {
  const width = Math.max(...results.map((result) => result.name.length), 4);
  out.log('');
  out.log('  RESULT  CHECK'.padEnd(width + 12) + 'TIME');
  for (const result of results) {
    out.log(`  ${result.outcome.padEnd(6)}  ${result.name.padEnd(width)}  ${(result.durationMs / 1000).toFixed(1)}s${result.detail ? `  -- ${result.detail}` : ''}`);
  }
  const counts = results.reduce((totals, result) => ({ ...totals, [result.outcome]: (totals[result.outcome] || 0) + 1 }), {});
  out.log('');
  out.log(`  ${counts.PASS || 0} passed, ${counts.FAIL || 0} failed, ${counts.SKIP || 0} skipped`);
}

function main(argv = process.argv.slice(2), env = process.env, out = console) {
  let options;
  try {
    options = parseArguments(argv);
  } catch (error) {
    out.error(error.message);
    return EXIT_FAIL;
  }
  if (options.help) {
    out.log('Usage: node scripts/run-playwright-suite.js [--tier T]... [--only NAME]... [--skip NAME]... [--province ON|BC] [--junit FILE] [--list] [--dry-run]');
    return EXIT_PASS;
  }

  let selected;
  try {
    selected = selectChecks(loadManifest(), options);
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
      out.log(`${check.name.padEnd(42)} ${check.tiers.join(',')}`);
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

  const identityBefore = readBuildIdentity(env);
  const results = selected.map((check) => {
    out.log(`\n--- ${check.name} (${check.tiers.join(',')}) ---`);
    // `env`, not process.env: main() validated BASE_URL and MYSQL_HOST out of
    // the environment it was HANDED, so the child has to receive that same one
    // or the gate and the run are about different deployments.
    return runOne(check, { ...options, env });
  });
  const identityAfter = readBuildIdentity(env);

  if (identityBefore && identityAfter && identityBefore !== identityAfter) {
    results.push({
      name: 'application-identity',
      outcome: 'FAIL',
      detail: 'the deployment changed while the suite ran, so these results span two different applications',
      durationMs: 0,
    });
  }

  summarise(results, out);
  if (options.junit) {
    fs.writeFileSync(options.junit, toJUnit(results));
    out.log(`  JUnit written to ${options.junit}`);
  }
  return results.some((result) => result.outcome === 'FAIL') ? EXIT_FAIL : EXIT_PASS;
}

if (require.main === module) {
  process.exitCode = main();
}

module.exports = {
  assertSafeTarget, loadManifest, main, parseArguments, readBuildIdentity, runOne, selectChecks, toJUnit,
};
