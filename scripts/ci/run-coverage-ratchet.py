#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Merge same-class, source-bound JaCoCo runs and apply committed per-file floors."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

import release_verification as gate
from junit_evidence import EvidenceError


def resolve_lane(evidence, check, plan_path, root=gate.ROOT, profile='all', iteration=1):
    source = gate.source_identity(root)
    if not source['clean']:
        raise EvidenceError('Coverage requires a frozen clean source tree')
    matches = []
    for path in evidence.glob('*/receipt.json'):
        receipt = gate.read_json(path)
        if (receipt.get('checkId'), receipt.get('profile'), receipt.get('iteration')) == (check['id'], profile, iteration):
            matches.append((path, receipt))
    if len(matches) != 1:
        raise EvidenceError('Coverage lane is missing or ambiguous: ' + check['id'])
    path, receipt = matches[0]
    if receipt.get('source') != source or receipt.get('planSha256') != gate.digest(plan_path):
        raise EvidenceError('Coverage belongs to another source or plan')
    if receipt.get('outcome') != 'PASS' or receipt.get('exitCode') != 0 or receipt.get('cancelled'):
        raise EvidenceError('Coverage lane did not pass')
    if receipt.get('command') != check['command'] or not gate.runtime_matches(receipt.get('runtimes', {}), check.get('runtimeConstraints', {})):
        raise EvidenceError('Coverage command or runtime is unqualified')
    if receipt.get('runId') != path.parent.name:
        raise EvidenceError('Coverage receipt has the wrong execution identity')
    names = set()
    inputs = receipt.get('inputs', [])
    for row in inputs:
        file = gate.relative_file(path.parent, row['path'])
        if row['path'] in names or gate.digest(file) != row['sha256']:
            raise EvidenceError('Coverage input is duplicated or changed')
        names.add(row['path'])
    if not {'jacoco.exec', 'coverage/jacoco.xml', 'classes.json'} <= names:
        raise EvidenceError('Coverage lane omitted required inputs')
    if names != {file.relative_to(path.parent).as_posix() for file in gate.evidence_files(check, path.parent)}:
        raise EvidenceError('Coverage receipt omitted underlying lane evidence')
    if gate.summarize(dict(check, selectedProfile=profile), path.parent, 0, root) != receipt['summary']:
        raise EvidenceError('Coverage counters disagree with the underlying report')
    if gate.read_json(path.parent / 'classes.json') != gate.production_class_identity(root):
        raise EvidenceError('Cannot merge JaCoCo executions from different production classes')
    return path.parent


def check_floors(report, floors_path, base, output, root=gate.ROOT):
    """Run the standalone checker with its sibling imports and repository context."""
    checked = subprocess.run([sys.executable, str(gate.ROOT / 'scripts/coverage/check-coverage-floors.py'),
        str(report), '--floors', str(floors_path), '--base', base, '--output', str(output)], cwd=root)
    if checked.returncode not in (0, 1):
        raise EvidenceError('Coverage floor checker could not complete')
    result = gate.read_json(output)
    if type(result.get('ready')) is not bool or result['ready'] != (checked.returncode == 0):
        raise EvidenceError('Coverage floor result disagrees with its exit status')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--jacoco-cli', type=Path, required=True)
    parser.add_argument('--plan', type=Path, default=gate.DEFAULT_PLAN)
    args = parser.parse_args()
    plan = gate.load_plan(args.plan)
    expected_cli = (gate.ROOT / 'scripts/coverage/jacoco-cli-0.8.16.sha256').read_text().strip()
    if gate.digest(args.jacoco_cli) != expected_cli:
        raise EvidenceError('JaCoCo CLI differs from the pinned artifact')
    checks = [check for check in plan['checks'] if check.get('requireCoverage')]
    if not checks:
        raise EvidenceError('No required Java coverage lanes')
    directory = args.output.parent
    retained = []
    merged_inputs = []
    executions = [(check, profile, iteration) for check in checks for profile in check['profiles']
                  for iteration in range(1, check.get('repetitions', 1) + 1)]
    for check, profile, iteration in executions:
        lane = resolve_lane(args.evidence, check, args.plan, profile=profile, iteration=iteration)
        destination = directory / 'coverage-inputs' / check['id'] / lane.name
        destination.mkdir(parents=True, mode=0o700)
        for name in ['receipt.json', *(row['path'] for row in gate.read_json(lane / 'receipt.json')['inputs'])]:
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            with (lane / name).open('rb') as source, target.open('xb') as output:
                os.chmod(target, 0o600)
                shutil.copyfileobj(source, output)
            retained.append(target)
        merged_inputs.append(destination / 'jacoco.exec')
    merged = directory / 'merged-jacoco.exec'
    report = directory / 'union-jacoco.xml'
    subprocess.run(['java', '-jar', str(args.jacoco_cli), 'merge', *map(str, merged_inputs), '--destfile', str(merged)], check=True)
    subprocess.run(['java', '-jar', str(args.jacoco_cli), 'report', str(merged), '--classfiles',
                    str(gate.ROOT / 'target/classes'), '--sourcefiles', str(gate.ROOT / 'src/main/java'),
                    '--xml', str(report)], check=True)
    floors_path = gate.ROOT / 'scripts/coverage/release-coverage-floors.json'
    floors = gate.read_json(floors_path)
    if floors.get('base') != plan['baselines']['currentMain']:
        raise EvidenceError('Coverage floors use a different reviewed base')
    details = directory / 'coverage-details.json'
    result = check_floors(report, floors_path, floors['base'], details)
    retained.extend([merged, report, details])
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': {
        'same-source-and-classes': True, 'all-required-java-lanes': True, 'reviewed-floors': result['ready']},
        'inputs': [{'path': str(path.relative_to(directory)), 'sha256': gate.digest(path)} for path in retained]})
    return 0 if result['ready'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
