#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Qualify six installed real-OS interruptions with independent SQL/file restoration."""
import argparse
import os
from pathlib import Path
import release_verification as gate
from junit_evidence import EvidenceError
from process_group import ProcessGroupFailure, run_process_group

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


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=('ON', 'BC'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    if os.environ.get('CARLOS_DISPOSABLE_HOST') != 'true' or os.environ.get('CARLOS_SUITE_PROVINCE') != args.profile:
        raise EvidenceError('Installed signal qualification needs its named disposable province')
    rows, inputs, completions = [], [], []
    failure_details = None
    manifest = gate.read_json(gate.ROOT / 'scripts/playwright-suite.json')['checks']
    timeouts = {row['name']: row['timeoutSec'] for row in manifest if row['name'] in CHECKPOINTS}
    for name, checkpoint in CHECKPOINTS.items():
        for signal in ('SIGINT', 'SIGTERM'):
            directory = args.output.parent / ('signals-' + name + '-' + signal)
            directory.mkdir()
            completion = directory / 'completion.json'
            try:
                result = run_process_group(['node', 'scripts/signal-cleanup-audit.js', '--only', name,
                    '--checkpoint', str(checkpoint), '--signal', signal, '--delivery', 'os',
                    '--output-dir', str(directory)], cwd=gate.ROOT,
                    env={**os.environ, 'RESULT_JSON': str(completion)}, timeout=timeouts[name] + 120,
                    track_descendants=True)
            except ProcessGroupFailure as failure:
                failure_details = {'name': name, 'requestedSignal': signal, 'failureReason': str(failure),
                    'processGroupStopped': failure.quiescent, 'restorationVerified': False,
                    'hostRequiresReconciliation': True}
                break
            report = directory / 'results.json'
            try:
                observed = gate.read_json(report)
                record = gate.read_json(completion)
                if (not isinstance(observed, list) or not observed
                        or any(not isinstance(row, dict) for row in observed) or not isinstance(record, dict)):
                    raise EvidenceError('Signal audit evidence has an invalid shape')
                rows.extend(observed); inputs.extend([report, completion])
                completions.append(result.returncode == 0 and record.get('name') == 'signal-cleanup-audit'
                    and record.get('outcome') == 'PASS' and not record.get('cleanupFailed'))
            except (EvidenceError, OSError, ValueError, TypeError):
                failure_details = {'name': name, 'requestedSignal': signal,
                    'failureReason': 'Signal audit evidence is unreadable or invalid',
                    'processGroupStopped': True, 'restorationVerified': False, 'hostRequiresReconciliation': True}
                break
            if result.returncode != 0 or not completions[-1] or any(
                    row.get('outcome') != 'PASS' for row in rows):
                failure_details = {'name': name, 'requestedSignal': signal,
                    'failureReason': 'Signal audit failed; stop before another workflow',
                    'restorationVerified': False, 'hostRequiresReconciliation': True}
                break
        if failure_details:
            break
    assertions = validate(rows)
    assertions['all-six-real-os-signals'] &= len(completions) == 6 and all(completions)
    try:
        gate.require_locked_chromium({row.get('browserVersion', '') for row in rows})
        assertions['locked-chromium'] = len(rows) == 6
    except EvidenceError: assertions['locked-chromium'] = False
    details = args.output.parent / 'signal-details.json'
    gate.write_json(details, {'schemaVersion': 1, 'profile': args.profile, 'observations': rows,
                              'failure': failure_details})
    inputs.append(details)
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': assertions, 'inputs': [
        {'path': str(path.relative_to(args.output.parent)), 'sha256': gate.digest(path)} for path in inputs]})
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
