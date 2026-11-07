#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Run a local synthetic SOAP logging oracle against the compiled candidate classes."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

import release_verification as gate
from junit_evidence import EvidenceError


def assertions(metrics):
    if (not isinstance(metrics, dict) or set(metrics) != {'schemaVersion', 'syntheticOnly', 'soapPayloadLeaked'}
            or type(metrics['schemaVersion']) is not int or metrics['schemaVersion'] != 1
            or metrics['syntheticOnly'] is not True or type(metrics['soapPayloadLeaked']) is not bool):
        raise EvidenceError('Missing or malformed independent SOAP privacy observation')
    return {'payload-free-logging': not metrics['soapPayloadLeaked']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.classpath_file.is_symlink() or not args.classpath_file.is_file() or args.classpath_file.stat().st_size > 1 << 20:
        raise EvidenceError('Missing bounded Java dependency classpath')
    dependencies = args.classpath_file.read_text().strip()
    if not dependencies or '\n' in dependencies or '\r' in dependencies:
        raise EvidenceError('Malformed Java dependency classpath')
    root = args.output.parent
    classes = root / 'classes.json'
    gate.write_json(classes, gate.production_class_identity())
    metrics = root / 'privacy-metrics.json'
    with tempfile.TemporaryDirectory(prefix='privacy-probe-', dir=root) as temporary:
        classpath = str(gate.ROOT / 'target/classes') + os.pathsep + dependencies
        subprocess.run(['javac', '-cp', classpath, '-d', temporary,
                        str(gate.ROOT / 'scripts/ci/EbsPayloadPrivacyProbe.java')], check=True)
        subprocess.run(['java', '-cp', temporary + os.pathsep + classpath,
                        'EbsPayloadPrivacyProbe', str(metrics)], check=True)
    result = assertions(gate.read_json(metrics))
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': result,
        'inputs': [{'path': file.name, 'sha256': gate.digest(file)} for file in (metrics, classes)]})
    return 0 if all(result.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
