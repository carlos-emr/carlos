#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Execute the exact CI browser fixtures with real-provider and assertion evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal

import release_verification as gate
from junit_evidence import EvidenceError
from package_lifecycle import require_checkout_playwright
from process_group import run_process_group
from command_evidence import relative_file

ASSERTIONS = ('complete-ci-browser-selection', 'real-locked-browser-and-assertions',
              'all-node-browser-cases-passed', 'owned-processes-stopped', 'fixture-source-unchanged')


def workflow_commands(text):
    return [match[1].split() for match in re.finditer(
        r'^\s*run: (node(?: --test)? scripts/[^\s]+\.js)\s*$', text, re.M) if 'browser' in match[1]]


def validate_contract(contract, workflow):
    fixtures = contract.get('fixtures') if isinstance(contract, dict) else None
    if (not isinstance(fixtures, list) or not fixtures or type(contract.get('schemaVersion')) is not int
            or contract['schemaVersion'] != 1
            or any(not isinstance(row, dict) for row in fixtures)):
        raise EvidenceError('Empty or malformed CI browser fixture contract')
    if contract.get('workflowSha256') != hashlib.sha256(workflow.encode('utf-8')).hexdigest():
        raise EvidenceError('CI browser workflow changed without a reviewed fixture contract')
    if len({row.get('id') for row in fixtures}) != len(fixtures):
        raise EvidenceError('Duplicate CI fixture selection')
    for row in fixtures:
        command = row.get('command'); source = row.get('source'); required = row.get('requiredCases')
        if (not isinstance(source, str) or not source.startswith('scripts/') or not source.endswith('.js')
                or command not in (['node', source], ['node', '--test', source])
                or row.get('id') != Path(source).stem or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]+', row['id'])
                or not isinstance(row.get('sourceSha256'), str) or not re.fullmatch('[0-9a-f]{64}', row['sourceSha256'])
                or type(row.get('minimumAssertions')) is not int or row['minimumAssertions'] < 1
                or (('--test' in command) != (required is not None))
                or (required is not None and (not isinstance(required, list) or not required
                    or any(not isinstance(name, str) or not name for name in required)
                    or len(set(required)) != len(required)))):
            raise EvidenceError('CI fixture lacks exact command, source, assertion or engine-case bindings')
    if workflow_commands(workflow) != [row['command'] for row in fixtures]:
        raise EvidenceError('CI browser fixture selection changed without a reviewed contract')
    return fixtures


def validate_proofs(fixture, records, engines, expected_version, source):
    if not records:
        raise EvidenceError('Fixture emitted no browser observations')
    launches = []; assertions = 0
    for record in records:
        if (not isinstance(record, dict) or type(record.get('schemaVersion')) is not int or record['schemaVersion'] != 1
                or record.get('source') != str(source)
                or record.get('sourceBefore') != fixture['sourceSha256']
                or record.get('sourceAfter') != fixture['sourceSha256']
                or type(record.get('exitCode')) is not int or record['exitCode'] != 0
                or type(record.get('assertions')) is not int or record['assertions'] < 0
                or not isinstance(record.get('launches'), list)):
            raise EvidenceError('Fixture observations have incomplete source, outcome or assertion bindings')
        assertions += record['assertions']; launches.extend(record['launches'])
    if (assertions < fixture['minimumAssertions'] or not launches or any(
            not isinstance(row, dict) or row.get('family') != 'chromium'
            or row.get('version') != expected_version or row.get('disconnected') is not True for row in launches)):
        raise EvidenceError('Fixture omitted required assertions, used another browser or left its browser connected')
    required = fixture.get('requiredCases')
    if required is not None:
        if len(engines) != 1:
            raise EvidenceError('Node browser fixture has no unique engine report')
        report = engines[0]; summary = report.get('summary') or {}; counts = summary.get('counts') or {}
        cases = report.get('cases') or []
        if (type(report.get('schemaVersion')) is not int or report['schemaVersion'] != 1
                or summary.get('success') is not True or type(counts.get('tests')) is not int
                or counts['tests'] != len(required) or type(counts.get('passed')) is not int
                or counts['passed'] != len(required)
                or any(type(counts.get(key)) is not int or counts[key] != 0
                       for key in ('failed', 'cancelled', 'skipped', 'todo'))
                or len(cases) != len(required) or len({row.get('name') for row in cases}) != len(required)
                or {row.get('name') for row in cases} != set(required)
                or any(row.get('outcome') != 'PASS' or row.get('file') != str(source) for row in cases)):
            raise EvidenceError('Node browser fixture omitted, skipped or failed a required engine case')
    elif engines:
        raise EvidenceError('Unexpected unregistered Node browser cases')
    return {'assertions': assertions, 'browserLaunches': len(launches), 'nodeCases': len(required or [])}


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--contract', type=Path, default=gate.ROOT / 'docs/testing/release-2026.08-ci-browser-fixtures.json')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); output = args.output.parent.resolve()
    if (output.is_relative_to(gate.ROOT) or output.stat().st_mode & 0o077 or args.output.exists()
            or args.output.is_symlink() or any(parent.is_symlink() for parent in args.output.parents)):
        raise EvidenceError('Fixture evidence requires fresh private output outside source')
    assertions = dict.fromkeys(ASSERTIONS, False)
    details = {'schemaVersion': 1, 'kind': 'isolated-source-fixtures', 'fixtures': [], 'failure': None}
    try:
        require_checkout_playwright(gate.ROOT, gate.read_json)
        workflow = gate.ROOT / '.github/workflows/script-regressions.yml'
        fixtures = validate_contract(gate.read_json(args.contract), workflow.read_text())
        details.update(contractSha256=gate.digest(args.contract), workflowSha256=gate.digest(workflow))
        locked = gate.read_json(gate.ROOT / 'node_modules/playwright-core/browsers.json')
        expected = next(row['browserVersion'] for row in locked['browsers'] if row['name'] == 'chromium')
        browser = Path(os.environ['CARLOS_FIXTURE_CHROMIUM']).resolve(strict=True)
        if not browser.is_file(): raise EvidenceError('Named fixture Chromium is not a regular executable')
        if not Path(os.environ['PLAYWRIGHT_BROWSERS_PATH']).is_dir():
            raise EvidenceError('Install the locked Playwright default browser cache before running CI fixtures')
        browser_hash = gate.digest(browser)
        details.update(browserExecutableSha256=browser_hash, browserVersion=expected)
        for fixture in fixtures:
            source = relative_file(gate.ROOT, fixture['source'])
            if (not source.is_relative_to(gate.ROOT) or not source.is_file()
                    or gate.digest(source) != fixture['sourceSha256']
                    or type(fixture['minimumAssertions']) is not int or fixture['minimumAssertions'] < 1):
                raise EvidenceError('CI fixture source or assertion floor differs from its reviewed contract')
            row = {'id': fixture['id'], 'passed': False}; details['fixtures'].append(row)
            directory = output / fixture['id']; directory.mkdir(mode=0o700)
            proofs = directory / 'proofs'; proofs.mkdir(mode=0o700)
            command = ['node', '--require', str(Path(__file__).with_name('observe-fixture-browser.cjs'))]
            if '--test' in fixture['command']:
                command += ['--test', '--test-reporter=' + str(Path(__file__).with_name('fixture-test-reporter.cjs'))]
            command += [str(source)]
            environment = {**os.environ, 'CARLOS_FIXTURE_SOURCE': str(source), 'CARLOS_FIXTURE_ROOT': str(gate.ROOT),
                'CARLOS_FIXTURE_PROOFS': str(proofs), 'TZ': 'UTC',
                'RELEASE_JS_COVERAGE': str(directory / 'javascript-coverage.json'), **{key: str(browser) for key in
                    ('CHROME_PATH', 'CHROMIUM_PATH', 'CHROMIUM_EXECUTABLE', 'CHROME_BIN')}}
            with (directory / 'private.log').open('xb') as log:
                result = run_process_group(command, cwd=gate.ROOT, env=environment, stdout=log, stderr=log,
                    timeout=180, grace=10, track_descendants=True)
            if result.returncode != 0: raise EvidenceError('CI browser fixture failed: ' + fixture['id'])
            if any(path.is_symlink() for path in proofs.iterdir()):
                raise EvidenceError('Fixture proof contains a link')
            records = [gate.read_json(path) for path in sorted(proofs.glob('*-browser.json'))]
            engines = [gate.read_json(path) for path in sorted(proofs.glob('*-tests.json'))]
            row.update(validate_proofs(fixture, records, engines, expected, source))
            if gate.digest(source) != fixture['sourceSha256']:
                raise EvidenceError('Fixture source changed during execution')
            row['passed'] = True
        if gate.digest(browser) != browser_hash:
            raise EvidenceError('Fixture browser executable changed during execution')
        assertions.update(dict.fromkeys(ASSERTIONS, True))
    except (Exception, KeyboardInterrupt) as failure:
        details['failure'] = {'type': type(failure).__name__, 'quiescent': getattr(failure, 'quiescent', None)}
        if isinstance(failure, EvidenceError): details['failure']['reason'] = str(failure)
    finally:
        gate.write_json(output / 'fixture-details.json', details)
    inputs = []
    for path in sorted(output.rglob('*')):
        if path.is_symlink(): raise EvidenceError('Fixture evidence contains a link')
        if path.is_file() and path != args.output:
            inputs.append({'path': path.relative_to(output).as_posix(), 'sha256': gate.digest(path)})
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': assertions, 'inputs': inputs})
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    def interrupted(_number, _frame): raise KeyboardInterrupt
    previous = signal.signal(signal.SIGTERM, interrupted)
    try: raise SystemExit(main())
    finally: signal.signal(signal.SIGTERM, previous)
