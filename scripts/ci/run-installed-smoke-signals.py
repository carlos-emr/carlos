#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Qualify six installed real-OS interruptions with independent SQL/file restoration."""
import argparse
import os
from pathlib import Path
import subprocess
import release_verification as gate
from junit_evidence import EvidenceError

CHECKPOINTS = {'document-upload': 26, 'tickler-crud': 8, 'login': 24}
EXPECTED = {(name, signal) for name in CHECKPOINTS for signal in ('SIGINT', 'SIGTERM')}


def validate(rows):
    identities = [(row.get('name'), row.get('requestedSignal')) for row in rows]
    complete = len(identities) == len(EXPECTED) and set(identities) == EXPECTED
    executed = complete and all(row.get('outcome') == 'PASS' and row.get('detail') == 'interrupted'
        and row.get('delivery') == 'os' and row.get('signal') is None
        and type(row.get('exit')) is int and row['exit'] == (130 if row['requestedSignal'] == 'SIGINT' else 143)
        and row.get('checkpoint') == CHECKPOINTS[row['name']] for row in rows)
    restored = complete and all(row.get('residue') == [] and isinstance(row.get('fileChanges'), dict)
        and set(row['fileChanges']) == {'added', 'removed', 'changed'}
        and all(type(value) is int and value == 0 for value in row['fileChanges'].values())
        and (row['name'] != 'login' or row.get('credentialRestored') is True) for row in rows)
    return {'all-six-real-os-signals': executed, 'all-owned-state-restored': restored}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=('ON', 'BC'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get('CARLOS_DISPOSABLE_HOST') != 'true' or os.environ.get('CARLOS_SUITE_PROVINCE') != args.profile:
        raise EvidenceError('Installed signal qualification needs its named disposable province')
    rows, inputs, completions = [], [], []
    for name, checkpoint in CHECKPOINTS.items():
        for signal in ('SIGINT', 'SIGTERM'):
            directory = args.output.parent / ('signals-' + name + '-' + signal)
            directory.mkdir()
            completion = directory / 'completion.json'
            result = subprocess.run(['node', 'scripts/signal-cleanup-audit.js', '--only', name,
                '--checkpoint', str(checkpoint), '--signal', signal, '--delivery', 'os',
                '--output-dir', str(directory)], cwd=gate.ROOT,
                env={**os.environ, 'RESULT_JSON': str(completion)}, timeout=240)
            report = directory / 'results.json'
            if report.is_file(): rows.extend(gate.read_json(report)); inputs.append(report)
            if completion.is_file():
                record = gate.read_json(completion)
                completions.append(result.returncode == 0 and record.get('name') == 'signal-cleanup-audit'
                    and record.get('outcome') == 'PASS' and not record.get('cleanupFailed'))
                inputs.append(completion)
            else: completions.append(False)
    assertions = validate(rows)
    assertions['all-six-real-os-signals'] &= len(completions) == 6 and all(completions)
    try:
        gate.require_locked_chromium({row.get('browserVersion', '') for row in rows})
        assertions['locked-chromium'] = len(rows) == 6
    except EvidenceError: assertions['locked-chromium'] = False
    details = args.output.parent / 'signal-details.json'
    gate.write_json(details, {'schemaVersion': 1, 'profile': args.profile, 'observations': rows})
    inputs.append(details)
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': assertions, 'inputs': [
        {'path': str(path.relative_to(args.output.parent)), 'sha256': gate.digest(path)} for path in inputs]})
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
