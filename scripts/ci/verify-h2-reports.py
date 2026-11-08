#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Reject failed/empty H2 runs and skips without an exact release-plan route."""
import argparse
from copy import deepcopy
import json
from pathlib import Path
import sys

from junit_evidence import EvidenceError
from release_verification import junit_results

ROOT = Path(__file__).resolve().parents[2]
PERMISSION_CLASS = 'io.github.carlos_emr.CarlosPropertiesModernChartPersistenceUnitTest'
PERMISSION_CASE = 'shouldRefuseCutover_whenUnsafeSourceIsReadOnly'
PERMISSION_CASES = json.loads(Path(__file__).with_name('permission-cases.json').read_text())


def verify(reports, permission_reports):
    """Require routed H2 skips and the exact passing permission executions."""
    plan = json.loads((ROOT / 'docs/testing/release-2026.08-verification.json').read_text())
    check = deepcopy(next(row for row in plan['checks'] if row['id'] == 'java-all'))
    # Every exact root-sensitive skip must pass in the separate lane. Other
    # skipped methods, including new assumptions, still fail this gate.
    for row in PERMISSION_CASES:
        route = check.setdefault('routedSkips', {}).setdefault(row['classname'], {
            'check': 'ci-unprivileged-permission', 'cases': []})
        # A class may also contain independently routed native/environment cases.
        # Give only the permission method its own destination, preserving others.
        identity = (row['name'], row['container'])
        route['cases'] = [case for case in route['cases'] if
            ((case, '') if isinstance(case, str) else (case['name'], case.get('container', ''))) != identity]
        route['cases'].append({'name': row['name'], 'container': row['container'],
                               'check': 'ci-unprivileged-permission'})
    summary = junit_results(reports, check)
    if not summary['ready']:
        raise EvidenceError('H2 run failed, lost required coverage, or contains unrouted skips')
    cases = []
    permission = junit_results(permission_reports, {'minimumCases': len(PERMISSION_CASES)}, cases_out=cases)
    required = {(row['classname'], row['name'], row['container'], 'pass') for row in PERMISSION_CASES}
    actual = [(case.classname, case.name, case.container, case.outcome) for case in cases]
    if not permission['ready'] or len(actual) != len(required) or set(actual) != required:
        raise EvidenceError('Required permission cases missing, replaced, skipped, or failed')
    return {key: summary[key] for key in ('tests', 'failed', 'skipped', 'ready')}


def main():
    """Validate either the H2/permission pair or the exact historical oracle lane."""
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
            required = {(family, row['name'], row.get('container', ''), 'pass') for row in h2['routedSkips'][family]['cases']}
            cases = []
            summary = junit_results(args.legacy_reports, {'minimumCases': len(required)}, cases_out=cases)
            actual = [(case.classname, case.name, case.container, case.outcome) for case in cases]
            if not summary['ready'] or len(actual) != len(required) or set(actual) != required:
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
