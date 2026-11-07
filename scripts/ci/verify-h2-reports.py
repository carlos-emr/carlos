#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Reject failed/empty H2 runs and skips without an exact release-plan route."""
import argparse
import json
from pathlib import Path
import sys

from junit_evidence import EvidenceError
from release_verification import junit_results

ROOT = Path(__file__).resolve().parents[2]
PERMISSION_CLASS = 'io.github.carlos_emr.CarlosPropertiesModernChartPersistenceUnitTest'
PERMISSION_CASE = 'shouldRefuseCutover_whenUnsafeSourceIsReadOnly'


def verify(reports, permission_reports):
    plan = json.loads((ROOT / 'docs/testing/release-2026.08-verification.json').read_text())
    check = next(row for row in plan['checks'] if row['id'] == 'java-all')
    # Root-run containers cannot exercise POSIX write refusal; a separate
    # unprivileged execution below must pass that exact case without a skip.
    check['routedSkips'][PERMISSION_CLASS] = {
        'check': 'ci-unprivileged-permission', 'cases': [PERMISSION_CASE]}
    summary = junit_results(reports, check)
    if not summary['ready']:
        raise EvidenceError('H2 run failed, lost required coverage, or contains unrouted skips')
    permission = junit_results(permission_reports, {'minimumCases': 1})
    required = PERMISSION_CLASS.rsplit('.', 1)[-1]
    if not permission['ready'] or permission['tests'] != 1 or permission['families'].get(required) != 1:
        raise EvidenceError('Unprivileged permission execution is incomplete')
    # Inspect the actual case identity too: another passing method cannot
    # substitute for the read-only startup-refusal regression.
    from junit_evidence import read_surefire
    paths = list(permission_reports.glob('TEST-*.xml'))
    cases = [case for path in paths for case in read_surefire(path, path.stem.removeprefix('TEST-'))]
    if [(case.classname, case.name, case.outcome) for case in cases] != [(PERMISSION_CLASS, PERMISSION_CASE, 'pass')]:
        raise EvidenceError('Required permission case was replaced')
    return {key: summary[key] for key in ('tests', 'failed', 'skipped', 'ready')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports', type=Path)
    parser.add_argument('--permission-reports', type=Path)
    parser.add_argument('--legacy-reports', type=Path)
    args = parser.parse_args()
    try:
        if args.legacy_reports:
            plan = json.loads((ROOT / 'docs/testing/release-2026.08-verification.json').read_text())
            h2 = next(row for row in plan['checks'] if row['id'] == 'java-all')
            family = 'io.github.carlos_emr.carlos.login.LegacyDependencyCompatibilityUnitTest'
            from junit_evidence import read_surefire
            cases = [case for path in args.legacy_reports.glob('TEST-*.xml')
                     for case in read_surefire(path, path.stem.removeprefix('TEST-'))]
            required = {(family, row['name'], 'pass') for row in h2['routedSkips'][family]['cases']}
            actual = [(case.classname, case.name, case.outcome) for case in cases]
            if len(actual) != len(required) or set(actual) != required:
                raise EvidenceError('Legacy compatibility cases missing, replaced, skipped, or failed')
            print(json.dumps({'legacyCases': len(actual), 'ready': True}))
        else:
            if not args.reports or not args.permission_reports:
                parser.error('--reports and --permission-reports are required together')
            print(json.dumps(verify(args.reports, args.permission_reports)))
        return 0
    except Exception as failure:
        reason = str(failure) if isinstance(failure, EvidenceError) else type(failure).__name__
        print('H2 evidence refused: ' + reason, file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
