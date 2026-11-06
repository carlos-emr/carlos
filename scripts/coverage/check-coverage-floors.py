#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Apply reviewed per-file changed-line, instruction and branch ratchets to a union report."""
import argparse
import json
import math
from pathlib import Path
import subprocess

from changed_line_audit import read_jacoco_lines, changed_lines, audit, audit_details


def percentage(covered, missed):
    return 100.0 * covered / (covered + missed) if covered + missed else 100.0


def measure(report, diff):
    source = read_jacoco_lines(report, include_branches=True)
    changes = changed_lines(diff)
    lines, unmapped = audit(source, changes)
    details = audit_details(source, changes)
    result = {}
    for path, (covered, missed, missed_lines) in lines.items():
        row = details[path]
        result[path] = {
            'lines': percentage(covered, missed),
            'instructions': percentage(row['covered_instructions'], row['missed_instructions']),
            'branches': percentage(row['covered_branches'], row['missed_branches']),
            'missedLines': missed_lines, 'missedBranchLines': row['missed_branch_lines'],
            'executableLines': covered + missed}
    return result, unmapped


def check_floors(report, floors, diff):
    if floors.get('schemaVersion') != 1 or not floors.get('baselineCommit') or not floors.get('files'):
        raise ValueError('Floors require a committed nonempty baseline')
    actual, unmapped = measure(report, diff)
    counters_by_source = read_jacoco_lines(report, include_branches=True)
    exclusions = floors.get('exclusions', {})
    problems = []
    for path, row in exclusions.items():
        if not isinstance(row, dict) or not row.get('reason') or not row.get('tests'):
            raise ValueError('Exclusions require rationale and concrete tests')
        if not path.endswith(('package-info.java', '.jrxml')):
            raise ValueError('Executable sources cannot be excluded from coverage')
    for path in unmapped:
        if path not in exclusions:
            problems.append({'path': path, 'reason': 'unmapped-source'})
    for path, row in actual.items():
        if path in exclusions:
            continue
        floor = floors['files'].get(path)
        if not floor:
            problems.append({'path': path, 'reason': 'missing-floor'})
            continue
        for metric in ('lines', 'instructions', 'branches'):
            threshold = floor.get(metric)
            if type(threshold) not in (int, float) or not math.isfinite(threshold) or not 0 <= threshold <= 100:
                raise ValueError('Floors must be finite percentages for all three metrics')
            if row[metric] + 1e-9 < threshold:
                problems.append({'path': path, 'reason': metric, 'actual': row[metric], 'floor': threshold})
        for line in floor.get('requiredRefusalLines', []):
            if type(line) is not int or line < 1:
                raise ValueError('Refusal line must be a positive source line')
            counters = counters_by_source.get(path, {}).get(line)
            if not counters or counters[0] < 1 or counters[1] or counters[3]:
                problems.append({'path': path, 'reason': 'uncovered-refusal', 'line': line})
    stale = set(floors['files']) - set(actual) - set(unmapped)
    if stale:
        problems.extend({'path': path, 'reason': 'floor-outside-comparison'} for path in sorted(stale))
    return {'schemaVersion': 1, 'ready': not problems, 'files': actual, 'problems': problems}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jacoco', type=Path)
    parser.add_argument('--floors', type=Path, required=True)
    parser.add_argument('--base', required=True)
    parser.add_argument('--head', default='HEAD')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    diff = subprocess.check_output(['git', 'diff', '--no-renames', '-U0', args.base, args.head,
                                    '--', 'src/main/java'], text=True)
    floors = json.loads(args.floors.read_text())
    if floors.get('base') != subprocess.check_output(['git', 'rev-parse', args.base], text=True).strip():
        raise ValueError('Floors name a different comparison base')
    result = check_floors(args.jacoco, floors, diff)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'ready': result['ready'], 'files': len(result['files']), 'problems': len(result['problems'])}))
    return 0 if result['ready'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
