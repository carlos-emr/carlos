# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Require every reviewed Node case, including repeated generated invocations."""
from collections import Counter
from pathlib import Path
import re

from command_evidence import relative_file
from junit_evidence import EvidenceError


def case_key(case, engine=False):
    parents = case.get('parents')
    if not isinstance(parents, list): raise EvidenceError('Node case lacks parent identities')
    if engine:
        if any(not isinstance(parent, dict) for parent in parents):
            raise EvidenceError('Node engine parent identity is malformed')
        parents = [parent.get('name') for parent in parents]
    if (not isinstance(case.get('file'), str) or not isinstance(case.get('name'), str)
            or not case['name'] or any(not isinstance(parent, str) or not parent for parent in parents)
            or any(type(case.get(name)) is not int or case[name] < 1 for name in ('line', 'column'))):
        raise EvidenceError('Node case lacks a stable source identity')
    return case['file'], tuple(parents), case['name'], case['line'], case['column']


def create(report, root, digest):
    if not re.fullmatch(r'v22\.\d+\.\d+', report.get('runtime', '')):
        raise EvidenceError('Node inventory requires the qualified engine')
    files = sorted(report['files'])
    sources = report.get('sourceFiles')
    if (report.get('schemaVersion') != 3 or report.get('sourceChanged') is not False
            or not isinstance(sources, dict) or set(sources) != set(files)
            or any(digest(relative_file(root, name)) != sources[name] for name in files)):
        raise EvidenceError('Node inventory requires unchanged execution-time test fingerprints')
    counts = Counter(case_key(row, engine=True) for row in report['cases'])
    return {'schemaVersion': 1, 'runtimeMajor': 22,
        'files': {name: sources[name] for name in files}, 'cases': [
            {'file': key[0], 'parents': list(key[1]), 'name': key[2], 'line': key[3], 'column': key[4], 'count': count}
            for key, count in sorted(counts.items())]}


def validate(manifest, cases, root, digest):
    if (not isinstance(manifest, dict) or set(manifest) != {'schemaVersion', 'runtimeMajor', 'files', 'cases'}
            or type(manifest['schemaVersion']) is not int or manifest['schemaVersion'] != 1
            or type(manifest['runtimeMajor']) is not int or manifest['runtimeMajor'] != 22
            or not isinstance(manifest['files'], dict) or not isinstance(manifest['cases'], list)
            or not manifest['cases']):
        raise EvidenceError('Malformed reviewed Node case inventory')
    actual_files = {str(path.relative_to(root)) for path in (root / 'scripts').glob('*.test.js')}
    if set(manifest['files']) != actual_files:
        raise EvidenceError('Reviewed Node files differ from discovery')
    for name, expected in manifest['files'].items():
        if not isinstance(expected, str) or not re.fullmatch('[0-9a-f]{64}', expected) or digest(relative_file(root, name)) != expected:
            raise EvidenceError('Reviewed Node test source changed; regenerate after a complete passing run')
    expected = Counter()
    for row in manifest['cases']:
        if not isinstance(row, dict) or set(row) != {'file', 'parents', 'name', 'line', 'column', 'count'}:
            raise EvidenceError('Malformed reviewed Node case')
        key = case_key(row)
        if key in expected or key[0] not in actual_files or type(row['count']) is not int or row['count'] < 1:
            raise EvidenceError('Duplicate or invalid reviewed Node case identity')
        expected[key] = row['count']
    if Counter(case_key(row, engine=True) for row in cases) != expected:
        raise EvidenceError('Node execution omitted, replaced or added a case outside its reviewed inventory')

