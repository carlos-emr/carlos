/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const suite = require('./run-playwright-suite');

const check = {name: 'controlled-case', script: 'scripts/controlled-case.js', tiers: ['core'],
  provinces: ['all'], assertsDatabase: false, timeoutSec: 2};
const strict = {strictRelease: true};
const quiet = {log() {}, error() {}};

test('province selection cannot silently omit lowercase legacy manifest entries', () => {
  const checks = [{...check, name: 'on-only', provinces: ['on']}, {...check, name: 'bc-only', provinces: ['BC']}];
  for (const province of ['ON', 'on', 'On']) {
    assert.deepEqual(suite.selectChecks(checks, suite.parseArguments(['--province', province])).map(c => c.name), ['on-only']);
  }
});

test('strict release accepts only nonempty all-PASS results', () => {
  assert.equal(suite.exitCodeFor([{outcome: 'PASS'}], strict), 0);
  for (const outcome of ['SKIP', 'FAIL', 'known-fail', 'unexpected-pass', 'failed-elsewhere', 'unknown']) {
    assert.equal(suite.exitCodeFor([{outcome: 'PASS'}, {outcome}], strict), 1, outcome);
  }
  assert.equal(suite.exitCodeFor([], strict), 1);
  assert.equal(suite.exitCodeFor([{outcome: 'known-fail'}]), 0, 'diagnostic mode preserves its contract');
});

test('strict JUnit exposes readiness failures without rewriting their product classification', () => {
  const xml = suite.toJUnit(['SKIP', 'known-fail', 'unexpected-pass'].map(outcome =>
    ({name: outcome, outcome, detail: outcome, durationMs: 1})), strict);
  assert.match(xml, /failures="3" skipped="0"/);
  assert.equal((xml.match(/type="release.readiness"/g) || []).length, 3);
  for (const outcome of ['SKIP', 'known-fail', 'unexpected-pass']) assert.ok(xml.includes(outcome));
});

test('strict release refuses omitted selections and duplicate check identities', () => {
  assert.throws(() => suite.parseArguments(['--strict-release', '--skip', check.name]), /does not permit/);
  assert.throws(() => suite.parseArguments(['--strict-release', '--dry-run']), /does not permit/);
  assert.throws(() => suite.selectChecks([check, check], suite.parseArguments([])), /Duplicate/);
});

test('a successful child exit requires a consistent structured outcome', () => {
  for (const record of [null, {outcome: 'FAIL'}, {outcome: 'PASS', cleanupFailed: true}, {outcome: 'PASS'}]) {
    const run = (_binary, _args, options) => {
      if (record) fs.writeFileSync(options.env.RESULT_JSON, JSON.stringify(record));
      return {status: 0};
    };
    const result = suite.runOne(check, strict, run);
    assert.equal(result.outcome, record?.outcome === 'PASS' && !record.cleanupFailed ? 'PASS' : 'FAIL');
  }
});

test('strict release refuses an unidentified deployment before running any child', () => {
  let calls = 0;
  const result = suite.main(['--strict-release'], {}, quiet,
    {checks: [check], readBuildIdentity: () => null, run: () => {calls++; return {status: 0};}});
  assert.equal(result, 1);
  assert.equal(calls, 0);
});

test('a complete strict run returns zero and writes compatible JUnit', t => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-strict-release-'));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  const output = path.join(directory, 'results.xml');
  const result = suite.main(['--strict-release', '--junit', output], {}, quiet, {
    checks: [check], readBuildIdentity: () => 'controlled-deployment',
    run: (_binary, _args, options) => {
      fs.writeFileSync(options.env.RESULT_JSON, JSON.stringify({outcome: 'PASS'}));
      return {status: 0};
    },
  });
  assert.equal(result, 0);
  assert.match(fs.readFileSync(output, 'utf8'), /tests="1" failures="0" skipped="0"/);
});

test('loss of deployment identity remains fatal after a successful check', () => {
  let probes = 0;
  const result = suite.main(['--strict-release'], {}, quiet, {
    checks: [check], readBuildIdentity: () => ++probes === 1 ? 'controlled-deployment' : null,
    run: (_binary, _args, options) => {
      fs.writeFileSync(options.env.RESULT_JSON, JSON.stringify({outcome: 'PASS'}));
      return {status: 0};
    },
  });
  assert.equal(result, 1);
});

test('strict release reports a missing private result under the actual check name', t => {
  t.mock.method(fs, 'mkdtempSync', () => {throw new Error('synthetic unavailable temp');});
  const result = suite.runOne(check, strict, () => {throw new Error('must not start a child');});
  assert.equal(result.name, check.name);
  assert.equal(result.outcome, 'FAIL');
  assert.match(result.detail, /cannot create a private structured result/);
});

test('strict standalone fixtures require structured PASS without contacting a deployment', () => {
  let children = 0;
  const result = suite.main(['--tier', 'standalone', '--strict-release'], {}, quiet, {
    checks: [{...check, tiers: ['standalone']}],
    readBuildIdentity: () => {throw new Error('fixture must not contact an application');},
    run: (_binary, _args, options) => {
      children++;
      fs.writeFileSync(options.env.RESULT_JSON, JSON.stringify({outcome: 'PASS'}));
      return {status: 0};
    },
  });
  assert.equal(result, 0);
  assert.equal(children, 1);
});

test('standalone classification cannot hide database, mutation or mixed-tier work', () => {
  for (const candidate of [
    {...check, tiers: ['standalone'], assertsDatabase: true},
    {...check, tiers: ['standalone'], mutates: ['synthetic']},
    {...check, tiers: ['standalone', 'core']},
  ]) {
    let children = 0;
    assert.equal(suite.main(['--strict-release'], {}, quiet, {
      checks: [candidate], readBuildIdentity: () => null,
      run: () => {children++; return {status: 0};},
    }), 1);
    assert.equal(children, 0);
  }
});

test('exit-zero standalone no-op remains a strict failure', () => {
  assert.equal(suite.main(['--strict-release'], {}, quiet, {
    checks: [{...check, tiers: ['standalone']}],
    readBuildIdentity: () => null, run: () => ({status: 0}),
  }), 1);
});
