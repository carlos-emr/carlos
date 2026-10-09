'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const filter = path.join(__dirname, 'filter_suppressed_sarif.py');

test('SARIF filtering retains unsuppressed findings and scan metadata across both report types', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-sarif-'));
  try {
    for (const name of ['semgrep.sarif', 'semgrep-carlos.sarif']) {
      const report = path.join(directory, name);
      const visible = [
        { ruleId: 'valid-path-finding', level: 'error', locations: [{ uri: 'unsafe.java' }] },
        { ruleId: 'empty-suppression', suppressions: [] },
      ];
      const metadata = { driver: { name: 'Semgrep', rules: [{ id: 'valid-path-finding' }] } };
      fs.writeFileSync(report, JSON.stringify({ version: '2.1.0', runs: [
        { tool: metadata, results: [
          { ruleId: 'reviewed-interface-boundary', suppressions: [{ kind: 'inSource' }] },
          ...visible,
        ] },
        { tool: metadata, results: [{ ruleId: 'another-valid-finding' }] },
      ] }), { mode: 0o600 });
      const result = spawnSync('python3', [filter, report], { encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
      const actual = JSON.parse(fs.readFileSync(report, 'utf8'));
      assert.deepEqual(actual.runs[0].results, visible);
      assert.deepEqual(actual.runs[0].tool, metadata);
      assert.deepEqual(actual.runs[1].results, [{ ruleId: 'another-valid-finding' }]);
      assert.equal(actual.version, '2.1.0');
      assert.equal(fs.statSync(report).mode & 0o777, 0o600);
      assert.match(result.stdout, /1 removed, 3 kept, 4 total/);
    }
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

test('a malformed SARIF report fails without replacing the scanner evidence', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-sarif-'));
  try {
    const report = path.join(directory, 'semgrep-carlos.sarif');
    fs.writeFileSync(report, '{incomplete');
    const result = spawnSync('python3', [filter, report], { encoding: 'utf8' });
    assert.notEqual(result.status, 0);
    assert.equal(fs.readFileSync(report, 'utf8'), '{incomplete');
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
