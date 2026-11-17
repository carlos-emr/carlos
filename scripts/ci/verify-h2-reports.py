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
PERMISSION_CASES = json.loads(Path(__file__).with_name('permission-cases.json').read_text())
PERMISSION_CHECK = 'java-unprivileged-permission'


def verify(reports, permission_reports):
    """Require routed H2 skips and the exact passing permission executions."""
    plan = json.loads((ROOT / 'docs/testing/release-2026.08-verification.json').read_text())
    check = next(row for row in plan['checks'] if row['id'] == 'java-all')
    # Every exact root-sensitive skip must pass in the separate lane. Other
    # skipped methods, including new assumptions, still fail this gate.
    for row in PERMISSION_CASES:
        route = check.get('routedSkips', {}).get(row['classname'], {})
        matches = [case for case in route.get('cases', []) if isinstance(case, dict)
                   and (case.get('name'), case.get('container', '')) == (row['name'], row['container'])]
        if (len(matches) != 1 or matches[0].get('check', route.get('check')) != PERMISSION_CHECK
                or not any(lane['id'] == PERMISSION_CHECK for lane in plan['checks'])):
            raise EvidenceError('Permission skip has no exact registered release-plan route')
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


def verify_services(reports):
    """Require real executions of every native footer, booking and SMS skip destination."""
    plan = json.loads((ROOT / 'docs/testing/release-2026.08-verification.json').read_text())
    check_id = 'java-native-release-services'
    check = next(row for row in plan['checks'] if row['id'] == check_id)
    h2 = next(row for row in plan['checks'] if row['id'] == 'java-all')
    required = set()
    for family, route in h2['routedSkips'].items():
        for item in route['cases']:
            case = {'name': item} if isinstance(item, str) else item
            if case.get('check', route['check']) != check_id:
                continue
            for destination in case.get('destinations', [case]):
                required.add((family, destination['name'], destination.get('container', '')))
    cases = []
    summary = junit_results(reports, check, cases_out=cases)
    passed = {(case.classname, case.name, case.container) for case in cases if case.outcome == 'pass'}
    if not summary['ready'] or not required or not required <= passed:
        raise EvidenceError('Native service cases missing, replaced, skipped, or failed')
    return {key: summary[key] for key in ('tests', 'failed', 'skipped', 'ready')}


def main():
    """Validate H2 permissions, native service destinations, or historical compatibility oracles."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports', type=Path)
    parser.add_argument('--permission-reports', type=Path)
    parser.add_argument('--legacy-reports', type=Path)
    parser.add_argument('--service-reports', type=Path)
    args = parser.parse_args()
    try:
        if args.service_reports:
            print(json.dumps(verify_services(args.service_reports)))
        elif args.legacy_reports:
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
