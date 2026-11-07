#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Pin source identities after a complete passing qualified Node execution."""
import argparse
import json
from pathlib import Path

import release_verification as gate
from junit_evidence import EvidenceError
import node_case_inventory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--replace', action='store_true')
    args = parser.parse_args()
    initial_hash = gate.digest(args.report)
    report = gate.read_json(args.report)
    if not gate.node_results(args.report)['ready']:
        raise EvidenceError('Only a complete passing Node run may define expected cases')
    document = node_case_inventory.create(report, gate.ROOT, gate.digest)
    node_case_inventory.validate(document, report['cases'], gate.ROOT, gate.digest)
    if gate.digest(args.report) != initial_hash:
        raise EvidenceError('Node inventory input changed during generation')
    if args.output.is_symlink():
        raise EvidenceError('Node inventory output cannot be a symlink')
    with args.output.open('w' if args.replace else 'x') as stream:
        # One reviewed case per line keeps generated diffs practical to inspect.
        prefix = {key: document[key] for key in ('schemaVersion', 'runtimeMajor', 'files')}
        stream.write(json.dumps(prefix, ensure_ascii=False, indent=2)[:-2])
        stream.write(',\n  "cases": [\n')
        stream.write(',\n'.join('    ' + json.dumps(row, ensure_ascii=False, separators=(',', ':'))
                                 for row in document['cases']))
        stream.write('\n  ]\n}\n')
    print(json.dumps({'files': len(document['files']), 'cases': sum(row['count'] for row in document['cases'])}))


if __name__ == '__main__':
    main()
