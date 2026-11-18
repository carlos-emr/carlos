#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Select the expensive CDS workflows for related changes or any PR to main."""
import fnmatch
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def related_paths():
    directory = Path(__file__).parent
    patterns = [line.strip() for line in (directory / 'cds-paths.txt').read_text().splitlines()
                if line.strip() and not line.lstrip().startswith('#')]
    if not patterns:
        raise ValueError('CDS path inventory is empty')
    # Reuse the reviewed coordinated inventory, including shared tests outside
    # the CDS packages and tests whose names do not start with Cds.
    for selector in json.loads((directory / 'cds-coordinated-test-classes.json').read_text()):
        patterns.append('src/test/java/' + (selector.replace('.', '/') if '.' in selector
                                            else '*/' + selector) + '.java')
    return patterns


def is_related(path, patterns):
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def commit(value):
    if not isinstance(value, str) or not re.fullmatch(r'[0-9a-f]{40}', value):
        raise ValueError('Change detection requires an exact event commit')
    return value


def select(event_name, event, root=ROOT):
    if event_name == 'workflow_dispatch':
        return True, 'Explicit manual run'
    if event_name == 'pull_request':
        pr = event['pull_request']
        if pr['base']['ref'] == 'main':
            return True, 'Every pull request to main runs CDS tests'
        # Compare with the merge base, so changes made only on the target
        # branch do not turn an unrelated PR into a CDS change.
        comparison = [commit(pr['base']['sha']) + '...' + commit(pr['head']['sha'])]
    elif event_name == 'push':
        before, after = commit(event['before']), commit(event['after'])
        if '0' * 40 in (before, after):
            return True, 'No previous branch revision available; run CDS tests'
        comparison = [before, after]
    else:
        raise ValueError('Unsupported CDS workflow event: ' + event_name)
    # A failed comparison must fail the gate, never report an unrelated change.
    changed = subprocess.check_output(
        ['git', 'diff', '--no-renames', '--name-only', '-z', *comparison, '--'], cwd=root)
    paths = [os.fsdecode(path) for path in changed.split(b'\0') if path]
    patterns = related_paths()
    matched = sum(is_related(path, patterns) for path in paths)
    return bool(matched), f'{matched} CDS-related paths among {len(paths)} changed paths'


def main():
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    run, reason = select(os.environ['GITHUB_EVENT_NAME'], event)
    with Path(os.environ['GITHUB_OUTPUT']).open('a') as output:
        output.write('run-tests=' + str(run).lower() + '\n')
    print(reason)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
            summary.write(f'CDS tests: **{"run" if run else "skip"}**. {reason}.\n')


if __name__ == '__main__':
    main()
