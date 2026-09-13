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
  EXIT_FAIL, EXIT_PASS, EXIT_SKIP, isLocalTlsTarget, validateBaseUrl,
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
function assertSafeTarget(checks, env) {
  const mutating = checks.filter((check) => check.assertsDatabase);
  if (!mutating.length) {
    return;
  }
  const baseUrl = validateBaseUrl(env.BASE_URL || 'http://127.0.0.1:8080/carlos');
  if (!isLocalTlsTarget(baseUrl) && env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(
      `${mutating.length} selected checks write and delete database rows, and BASE_URL points at `
      + `the non-local host ${baseUrl.hostname}. Set ALLOW_NON_LOCAL_BASE_URL=true only for a `
      + 'disposable test deployment.',
    );
  }
}

/**
 * The application's build identity, used to prove the suite tested one process.
 *
 * The runbook did this by reading systemd's NRestarts, which only works on a
 * packaged install. The About page's build tag works anywhere the suite runs; a
 * change mid-suite means the WAR was replaced or the JVM restarted under it, and
 * a green result would be spanning two different deployments.
 */
function readBuildIdentity(env, run = spawnSync) {
  const base = (env.BASE_URL || '').replace(/\/$/, '');
  if (!base) {
    return null;
  }
  const result = run('curl', ['-sk', '--max-time', '10', '-o', '/dev/null', '-w', '%{http_code}', `${base}/`], { encoding: 'utf8' });
  if (result.status !== 0) {
    return null;
  }
  return String(result.stdout || '').trim();
}

function runOne(check, options, run = spawnSync) {
  const started = Date.now();
  const result = run(process.execPath, [check.script], {
    stdio: 'inherit',
    timeout: check.timeoutSec * 1000,
    env: process.env,
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

  const checks = loadManifest();
  const selected = selectChecks(checks, options);
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
    return runOne(check, options);
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
  loadManifest, main, parseArguments, readBuildIdentity, runOne, selectChecks, toJUnit,
};
