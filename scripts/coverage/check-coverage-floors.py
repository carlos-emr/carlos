#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Apply reviewed per-file changed-line, instruction and branch ratchets to a union report."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess

from changed_line_audit import read_jacoco_lines, changed_lines, audit, audit_details


def source_identity(base, head='HEAD'):
    """Bind the baseline to Java blobs, independently of later test/document commits."""
    def git(*arguments):
        return subprocess.check_output(['git', *arguments])
    resolved = git('rev-parse', '--verify', base + '^{commit}').decode().strip()
    tree = git('rev-parse', '--verify', head + ':src/main/java').decode().strip()
    comparison = git('diff', '--no-ext-diff', '--no-textconv', '--no-renames',
                     '--raw', '-z', '--no-abbrev', resolved, head, '--', 'src/main/java')
    return {'base': resolved, 'sourceTree': tree,
            'comparisonSha256': hashlib.sha256(comparison).hexdigest()}


def check_freshness(floors, base, head='HEAD'):
    actual = source_identity(base, head)
    problems = [key for key, value in actual.items() if floors.get(key) != value]
    if subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=all', '--', 'src/main/java']):
        problems.append('dirtyJavaSources')
    files = floors.get('files')
    if floors.get('schemaVersion') != 1 or not isinstance(files, dict) or not files:
        raise ValueError('Floors require a nonempty source-bound baseline')
    waivers = floors.get('zeroCoverageWaivers', {})
    if not isinstance(waivers, dict):
        raise ValueError('Zero-coverage waivers must be keyed by source path')
    blobs = {}
    for entry in subprocess.check_output(['git', 'ls-tree', '-r', '-z', head, '--', 'src/main/java']).split(b'\0'):
        if entry:
            metadata, path = entry.split(b'\t', 1)
            blobs[path.decode()] = metadata.decode().split()[-1]
    for path, row in files.items():
        if not isinstance(row, dict) or row.get('sourceBlob') != blobs.get(path) or path not in blobs:
            problems.append('sourceBlob:' + path)
        for metric in ('lines', 'instructions', 'branches'):
            value = row.get(metric) if isinstance(row, dict) else None
            if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 100:
                raise ValueError('Floors must be finite percentages for all three metrics')
    exclusions = floors.get('exclusions', {})
    if not isinstance(exclusions, dict):
        raise ValueError('Exclusions must be keyed by source path')
    for path, row in exclusions.items():
        if (not path.endswith(('package-info.java', '.jrxml')) or not isinstance(row, dict)
                or not row.get('reason') or not row.get('tests')):
            raise ValueError('Only documented nonexecutable sources can be excluded')
    compared = set(subprocess.check_output(['git', 'diff', '--no-ext-diff', '--no-renames',
        '--diff-filter=ACMRT', '--name-only', '-z', actual['base'], head,
        '--', 'src/main/java']).decode().strip('\0').split('\0')) - {''}
    for path in sorted(compared - set(files) - set(exclusions)):
        problems.append('missingSourceFloor:' + path)
    for path in sorted((set(files) | set(exclusions)) - compared):
        problems.append('sourceOutsideComparison:' + path)
    if set(files) & set(exclusions):
        raise ValueError('Sources cannot have both a floor and an exclusion')
    baseline = floors.get('baselineCommit')
    if not isinstance(baseline, str) or not re.fullmatch(r'[0-9a-f]{40}', baseline):
        problems.append('baselineCommit')
    else:
        resolved = subprocess.run(['git', 'rev-parse', '--verify', baseline + ':src/main/java'],
                                  capture_output=True, text=True, check=False)
        if resolved.returncode:
            problems.append('baselineCommit')
        else:
            if resolved.stdout.strip() != actual['sourceTree']:
                problems.append('baselineSourceTree')
            if subprocess.run(['git', 'merge-base', '--is-ancestor', baseline, head], check=False).returncode:
                problems.append('baselineAncestry')
    if not isinstance(floors.get('baselineReportSha256'), str) or not re.fullmatch(
            r'[0-9a-f]{64}', floors['baselineReportSha256']):
        problems.append('baselineReportSha256')
    if not isinstance(floors.get('baselineLane'), str) or not floors['baselineLane'].strip():
        problems.append('baselineLane')
    return {'ready': not problems, 'source': actual, 'problems': problems,
            'unreviewedZeroFloors': sorted(path for path, row in files.items()
                if row['lines'] == 0 and not reviewed_zero_floor(
                    waivers.get(path), row.get('sourceBlob')))}


def reviewed_zero_floor(waiver, source_blob):
    """A zero floor needs a concrete review, not a boolean or an issue reference."""
    return (isinstance(waiver, dict)
            and isinstance(source_blob, str) and re.fullmatch(r'[0-9a-f]{40}', source_blob) is not None
            and waiver.get('sourceBlob') == source_blob
            and isinstance(waiver.get('reason'), str) and bool(waiver['reason'].strip())
            and isinstance(waiver.get('reviewer'), str) and bool(waiver['reviewer'].strip())
            and isinstance(waiver.get('reviewUrl'), str) and re.fullmatch(
                r'https://github\.com/carlos-emr/carlos/pull/[1-9][0-9]*#(?:pullrequestreview-|discussion_r)[1-9][0-9]*',
                waiver['reviewUrl']) is not None)


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
    waivers = floors.get('zeroCoverageWaivers', {})
    if not isinstance(waivers, dict):
        raise ValueError('Zero-coverage waivers must be keyed by source path')
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
        if floor['lines'] == 0 and row['executableLines'] > 0 and not reviewed_zero_floor(waivers.get(path), floor.get('sourceBlob')):
            problems.append({'path': path, 'reason': 'unreviewed-zero-floor'})
        for line in floor.get('requiredRefusalLines', []):
            if type(line) is not int or line < 1:
                raise ValueError('Refusal line must be a positive source line')
            counters = counters_by_source.get(path, {}).get(line)
            if not counters or counters[0] < 1 or counters[1] or counters[3]:
                problems.append({'path': path, 'reason': 'uncovered-refusal', 'line': line})
    stale = set(floors['files']) - set(actual) - set(unmapped)
    if stale:
        problems.extend({'path': path, 'reason': 'floor-outside-comparison'} for path in sorted(stale))
    for path in sorted(waivers):
        if path not in actual or floors['files'].get(path, {}).get('lines') != 0 or not actual[path]['executableLines']:
            problems.append({'path': path, 'reason': 'waiver-outside-zero-floor'})
    return {'schemaVersion': 1, 'ready': not problems, 'files': actual, 'problems': problems}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jacoco', type=Path, nargs='?')
    parser.add_argument('--freshness-only', action='store_true',
                        help='Check source/base identity; does not qualify coverage or approve zero floors')
    parser.add_argument('--floors', type=Path, required=True)
    parser.add_argument('--base', required=True)
    parser.add_argument('--head', default='HEAD')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.unlink(missing_ok=True)
    if args.jacoco is None and not args.freshness_only:
        parser.error('jacoco is required unless --freshness-only is selected')
    floors = json.loads(args.floors.read_text())
    freshness = check_freshness(floors, args.base, args.head)
    if args.freshness_only or not freshness['ready']:
        result = {'schemaVersion': 1, 'ready': freshness['ready'], 'freshness': freshness,
                  'coverageEvaluated': False}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps({'fresh': freshness['ready'], 'coverageEvaluated': False,
                          'unreviewedZeroFloors': len(freshness['unreviewedZeroFloors']),
                          'problems': freshness['problems']}))
        return 0 if freshness['ready'] else 1
    diff = subprocess.check_output(['git', 'diff', '--no-ext-diff', '--src-prefix=a/', '--dst-prefix=b/',
                                    '--no-renames', '-U0', args.base, args.head,
                                    '--', 'src/main/java'], text=True)
    result = check_floors(args.jacoco, floors, diff)
    result.update(freshness=freshness, coverageEvaluated=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'ready': result['ready'], 'files': len(result['files']), 'problems': len(result['problems'])}))
    return 0 if result['ready'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
