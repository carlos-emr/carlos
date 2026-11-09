#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Native objects and unchanged readers from independently pinned published binaries.

This narrower matrix supplements, and never overrides, the strict whole-release
wire/API comparison. Pre-existing reader failures are retained per case.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import defusedxml.ElementTree as ET
import release_verification as gate
from junit_evidence import EvidenceError

SPEC = importlib.util.spec_from_file_location('binary_clients', Path(__file__).with_name('run-main-release-wire-compatibility.py'))
BINARY = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = BINARY
SPEC.loader.exec_module(BINARY)
INVENTORY = gate.ROOT / 'docs/testing/issue-5721-native-xml-cases.json'
PROBE = Path(__file__).with_name('NativeXmlContractProbe.java')
INVENTORY_SHA256 = '41d1fc8b1eba3c63effa2ead971490549c8c2d9a52d2b1d81a68bbe309a91693'
PROVIDERS = {'glassfish': 'org.glassfish.jaxb.runtime.v2.runtime.JAXBContextImpl',
             'moxy': 'org.eclipse.persistence.jaxb.JAXBContext'}


def xml_tree(value):
    def tree(node):
        return (node.tag, tuple(sorted(node.attrib.items())), node.text or '', tuple(tree(child) for child in node))
    return tree(ET.fromstring(value))


def require_cases(report, ids, provider):
    if report.get('provider') != provider or set(report.get('cases', {})) != set(ids):
        raise EvidenceError('Native XML matrix omitted cases or changed provider')
    for row in report['cases'].values():
        if 'error' not in row and row.get('provider') != PROVIDERS[provider]:
            raise EvidenceError('Native XML case silently substituted its provider')
    return report['cases']


def compare_readers(before, after):
    results = {}
    for name, original in before.items():
        candidate = after[name]
        issues = []
        if 'error' not in original and (original.get('schemaCompiles') is not True
                or candidate.get('schemaCompiles') is not True or candidate.get('strictSchemaUnavailable')):
            issues.append('strict-schema-unavailable')
        if original.get('error') != candidate.get('error'):
            issues.append('context-outcome-changed')
        modes = {}
        for mode in ('defaultRead', 'strictRead'):
            if mode in original:
                if mode not in candidate:
                    issues.append(mode + '-new-failure')
                elif xml_tree(original[mode]) != xml_tree(candidate[mode]):
                    issues.append(mode + '-bound-state-changed')
                modes[mode] = 'compared' if mode in candidate else 'failed'
            elif original.get(mode + 'Error'):
                modes[mode] = 'pre-existing-failure'
                if original.get(mode + 'Error') != candidate.get(mode + 'Error'):
                    issues.append(mode + '-failure-changed')
            elif 'error' not in original:
                issues.append(mode + '-missing-observation')
        results[name] = {'contextError': original.get('error'), 'readers': modes, 'problems': issues}
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--main-package', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--tomcat-jsp-api', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--result', type=Path)
    args = parser.parse_args()
    baseline = gate.read_json(gate.ROOT / 'docs/testing/release-2026.08-lifecycle-baselines.json')
    pin = next(row for row in baseline['assets'] if row['name'].endswith('_amd64.deb'))
    if gate.digest(args.main_package) != pin['sha256']:
        raise EvidenceError('Native XML published binary differs from its independent pin')
    if gate.digest(args.tomcat_jsp_api) != BINARY.TOMCAT_JSP_API_SHA256:
        raise EvidenceError('Provided JSP API differs from its reviewed pin')
    if gate.digest(INVENTORY) != INVENTORY_SHA256:
        raise EvidenceError('Native XML issue inventory differs from its reviewed pin')
    ids = gate.read_json(INVENTORY)
    if not isinstance(ids, list) or len(ids) != 56 or len(set(ids)) != 56:
        raise EvidenceError('Native XML inventory must contain all 56 unique issue observations')
    args.output.mkdir(parents=True, exist_ok=False)
    gate.write_json(args.output / 'inputs.json', {'mainSource': baseline['sourceCommit'],
        'mainPackageSha256': gate.digest(args.main_package), 'candidateSource': gate.source_identity(),
        'candidateWarSha256': gate.digest(args.candidate), 'probeSha256': gate.digest(PROBE),
        'inventorySha256': gate.digest(INVENTORY), 'providedJspApiSha256': gate.digest(args.tomcat_jsp_api)})
    with tempfile.TemporaryDirectory(prefix='native-xml-', dir=args.output) as temporary:
        root = Path(temporary); old = root / 'old'; new = root / 'new'; classes = root / 'probe'
        old.mkdir(); new.mkdir(); classes.mkdir()
        with (args.output / 'private-extraction.log').open('wb') as log:
            BINARY.extract_main(args.main_package, old, log)
        BINARY.extract_release(args.candidate, new)
        def classpath(webapp):
            return os.pathsep.join(map(str, [classes, webapp / 'WEB-INF/classes', webapp / 'WEB-INF/lib/*', args.tomcat_jsp_api.resolve()]))
        subprocess.run(['javac', '-cp', classpath(new), '-d', str(classes), str(PROBE)], check=True)
        changed_inventory = root / 'changed-inventory.json'
        changed_inventory.write_bytes(INVENTORY.read_bytes().replace(b'commn.model.Demographic', b'commn.model.Xemographic', 1))
        oversized_inventory = root / 'oversized-inventory.json'
        oversized_inventory.write_bytes(INVENTORY.read_bytes() + b' ')
        input_guards = []
        for label, provider, inventory, expected in [
                ('unsupported-provider', 'java.lang.Runtime', INVENTORY, 'Unknown native probe provider or profile'),
                ('changed-inventory', 'glassfish', changed_inventory, 'Native XML inventory differs from its reviewed pin'),
                ('oversized-inventory', 'glassfish', oversized_inventory, 'Native XML inventory differs from its reviewed pin')]:
            rejected_output = root / (label + '-output')
            refusal = subprocess.run(['java', '-cp', classpath(new), 'NativeXmlContractProbe',
                provider, 'none', str(inventory), str(rejected_output)], capture_output=True, text=True)
            if (refusal.returncode == 0 or rejected_output.exists()
                    or 'java.lang.IllegalArgumentException: ' + expected not in refusal.stderr):
                raise EvidenceError('Native XML probe did not refuse ' + label + ' before output')
            input_guards.append(label)
        runs = [('old-glassfish', old, 'glassfish', 'none', None),
                ('old-moxy', old, 'moxy', 'none', None),
                ('published', new, 'glassfish', 'published', None),
                ('glassfish-legacy', new, 'glassfish', 'glassfish-legacy', None),
                ('moxy-legacy', new, 'glassfish', 'moxy-legacy', None),
                ('old-glassfish-reading-candidate', old, 'glassfish', 'none', 'glassfish-legacy'),
                ('old-moxy-reading-candidate', old, 'moxy', 'none', 'moxy-legacy')]
        observations = {}
        for name, webapp, provider, profile, reader_input in runs:
            output = args.output / name
            with (args.output / ('private-' + name + '.log')).open('wb') as log:
                subprocess.run(['java', '-Duser.language=en', '-Duser.country=CA', '-cp', classpath(webapp),
                    'NativeXmlContractProbe', provider, profile, str(INVENTORY), str(output)]
                    + ([str(args.output / reader_input)] if reader_input else []), stdout=log, stderr=log, check=True)
            observations[name] = require_cases(gate.read_json(output / 'observations.json'), ids, provider)
    comparisons = {provider: compare_readers(observations['old-' + provider],
        observations['old-' + provider + '-reading-candidate']) for provider in ('glassfish', 'moxy')}
    invalid = [name for name, row in observations['published'].items()
               if row.get('schemaValid') is not True or 'xml' not in row or 'error' in row]
    assertions = {'published-56-native-writes-valid': not invalid,
        **{provider + '-unchanged-readers-no-new-breaks': not any(row['problems'] for row in rows.values())
           for provider, rows in comparisons.items()}}
    details = args.output / 'comparison-details.json'
    gate.write_json(details, {'schemaVersion': 1, 'scope': 'issue-5721-native-objects-and-explicit-profiles',
        'wholeReleaseQualified': False, 'inputGuards': input_guards, 'invalidPublishedCases': invalid, 'comparisons': comparisons})
    result = args.result or args.output / 'result.json'
    if result.parent.resolve() not in (args.output.resolve(), args.output.parent.resolve()):
        raise EvidenceError('Native XML result must remain inside its evidence run')
    inputs = [args.output / 'inputs.json', details] + [args.output / name / 'observations.json' for name, *_ in runs]
    gate.write_json(result, {'schemaVersion': 1, 'assertions': assertions, 'inputs': [
        {'path': str(path.relative_to(result.parent)), 'sha256': gate.digest(path)} for path in inputs]})
    print(json.dumps({'nativeMatrixPassed': all(assertions.values()), 'cases': 56,
                      'wholeReleaseQualified': False}))
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
