/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');
const script = path.join(__dirname, 'summarize_security_locations.py');
const poison = 'PRIVATE_MESSAGE_METAVARIABLE_SNIPPET_ENV_SENTINEL';

function report(result) {
    return { version: '2.1.0', runs: [{ tool: { driver: { rules: [{ id: 'rule.indexed' }] } },
        properties: { secret: poison }, results: [result] }] };
}

function finding() {
    return { ruleId: 'java.security.example', level: 'warning', baselineState: 'new',
        message: { text: poison }, properties: { metavariables: poison },
        locations: [{ physicalLocation: { artifactLocation: { uri: 'src/main/java/Example.java' },
            region: { startLine: 7, endLine: 9, snippet: { text: poison } } } }],
        relatedLocations: [{ message: { text: poison } }] };
}

function run(inputs) {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-security-locations-'));
    try {
        const paths = inputs.map((input, index) => {
            const name = path.join(directory, `report-${index}.sarif`);
            if (input !== null) fs.writeFileSync(name, typeof input === 'string' ? input : JSON.stringify(input));
            return name;
        });
        return spawnSync('python3', [script, ...paths], { encoding: 'utf8', cwd: directory });
    } finally { fs.rmSync(directory, { recursive: true, force: true }); }
}

test('actual summary prints only allowlisted metadata and never scanner prose or snippets', () => {
    const result = run([report(finding())]);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), { ruleId: 'java.security.example', level: 'warning',
        path: 'src/main/java/Example.java', startLine: 7, endLine: 9, baselineState: 'new' });
    assert.equal(result.stderr, '');
    assert.equal((result.stdout + result.stderr).includes(poison), false);
});

test('a missing cloud report does not suppress the independent repository report', () => {
    const value = finding();
    delete value.baselineState;
    const result = run([null, report(value)]);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stdout.trim().split('\n').length, 1);
    assert.equal(Object.hasOwn(JSON.parse(result.stdout), 'baselineState'), false);
});

test('indexed rules and omitted end lines retain declared metadata without guessing severity', () => {
    const value = finding();
    delete value.ruleId;
    delete value.level;
    delete value.locations[0].physicalLocation.region.endLine;
    value.ruleIndex = 0;
    const result = run([report(value)]);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), { ruleId: 'rule.indexed', level: null,
        path: 'src/main/java/Example.java', startLine: 7, endLine: 7, baselineState: 'new' });
});

test('empty or absent reports emit no fabricated findings', () => {
    const result = run([null, { runs: [] }]);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stdout, '');
});

for (const uri of ['../outside.java', '/private/outside.java', 'https://example.invalid/token?secret=' + poison,
    'src/%2e%2e/%2e%2e/outside.java', 'src/main/java/Example.java?secret=' + poison]) {
    test(`summary refuses non-repository location ${uri.split('?')[0]}`, () => {
        const value = finding();
        value.locations[0].physicalLocation.artifactLocation.uri = uri;
        const result = run([report(value)]);
        assert.equal(result.status, 1);
        assert.equal(result.stdout, '');
        assert.equal(result.stderr, 'Security location diagnostics unavailable: invalid report\n');
        assert.equal(result.stderr.includes(poison), false);
    });
}

test('malformed JSON and unsafe metadata fail without echoing input or a partial finding list', () => {
    const invalid = [poison, report({ ...finding(), ruleId: poison + '\n::error::injected' }),
        report({ ...finding(), baselineState: poison }), report({ ...finding(), level: poison })];
    for (const input of invalid) {
        const result = run([report(finding()), input]);
        assert.equal(result.status, 1);
        assert.equal(result.stdout, '');
        assert.equal(result.stderr, 'Security location diagnostics unavailable: invalid report\n');
        assert.equal(result.stderr.includes(poison), false);
    }
});

test('both workflows add non-gating summaries of existing filtered/generated SARIF before upload', () => {
    for (const [file, inputs] of [['semgrep.yml', 'semgrep.sarif semgrep-carlos.sarif'],
        ['spotbugs.yml', 'spotbugs-security-report.sarif']]) {
        const workflow = fs.readFileSync(path.join(__dirname, '..', '.github', 'workflows', file), 'utf8');
        const start = workflow.indexOf('      - name: Summarize security finding locations');
        assert.ok(start > 0);
        const next = workflow.indexOf('\n      - name:', start + 1);
        const step = workflow.slice(start, next);
        assert.match(step, /if: always\(\) && .*hashFiles/);
        assert.match(step, /continue-on-error: true/);
        assert.ok(step.includes(`run: python3 scripts/summarize_security_locations.py ${inputs}`));
        const upload = file === 'semgrep.yml' ? '      - name: Upload SARIF to GitHub Security tab'
            : '      - name: Upload security SARIF to GitHub Code Scanning';
        assert.ok(start < workflow.indexOf(upload));
        if (file === 'semgrep.yml') assert.ok(start > workflow.indexOf('      - name: Filter suppressed CARLOS'));
        else assert.ok(start > workflow.indexOf('Converted {len(results)} security findings to SARIF'));
    }
});
