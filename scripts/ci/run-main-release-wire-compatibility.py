#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Compare unchanged clients against pinned main and candidate binaries in isolated class loaders."""
import argparse
from decimal import Decimal
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import tempfile
import zipfile

import defusedxml.ElementTree as ET
import release_verification as gate
from cds_war_manifest import build_manifest
from junit_evidence import EvidenceError

TRANSFER_CASES = {name + '-' + sample + '-' + transport
    for name in ('FilterDefinitionTransfer', 'AppointmentArchiveTransfer')
    for sample in ('default', 'values') for transport in ('xml', 'session-json', 'oauth-json')}
EXPECTED_CASES = TRANSFER_CASES | {'provider-session-json', 'provider-oauth-json', 'opt-in-soap-payload-leaked',
                                 'changed-public-java-api'}
CORPUS = gate.ROOT / 'src/test/resources/xml/jaxb-moxy-baseline.json'
EXPECTED_CASES |= {'legacy-input:' + name + ':' + sample + ':xml'
                   for name, samples in gate.read_json(CORPUS).items() for sample in samples}
LIMIT = 1 << 30
TOMCAT_CORE_SHA256 = 'b71785ff101c0df501064b3e3b8c4a02e4f9cc3047a7fa686c41fc4edc2f0cb4'


def canonical(row):
    if not isinstance(row, dict) or set(row) != {'kind', 'value'} or not isinstance(row['value'], str):
        raise EvidenceError('Missing typed wire observation')
    if row['kind'] in ('json', 'api'):
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result: raise EvidenceError('Ambiguous duplicate JSON wire key')
                result[key] = value
            return result
        def typed(value):
            if value is None: return ('null',)
            if type(value) is bool: return ('boolean', value)
            if isinstance(value, Decimal): return ('number', value)
            if isinstance(value, str): return ('string', value)
            if isinstance(value, list): return ('array', [typed(item) for item in value])
            return ('object', sorted((key, typed(item)) for key, item in value.items()))
        def reject_constant(_value): raise EvidenceError('Non-finite JSON wire value')
        value = json.loads(row['value'], object_pairs_hook=unique, parse_int=Decimal,
                           parse_float=Decimal, parse_constant=reject_constant)
        if row['kind'] == 'api':
            if (not isinstance(value, dict) or not value or any(
                    not isinstance(name, str) or not isinstance(members, list)
                    or any(not isinstance(member, str) for member in members)
                    or len(members) != len(set(members)) for name, members in value.items())):
                raise EvidenceError('Missing complete typed public API inventory')
            return value
        return typed(value)
    if row['kind'] == 'xml':
        scopes, pending, namespaces = [], {}, {}
        parser = ET.iterparse(io.StringIO(row['value']), events=('start-ns', 'start', 'end'), forbid_dtd=True)
        for event, item in parser:
            if event == 'start-ns': pending[item[0] or ''] = item[1]
            elif event == 'start':
                scope = {**(scopes[-1] if scopes else {}), **pending}
                pending = {}; scopes.append(scope); namespaces[item] = scope
            else: scopes.pop()
        def tree(element):
            children = list(element)
            attributes = dict(element.attrib)
            key = '{http://www.w3.org/2001/XMLSchema-instance}type'
            if key in attributes:
                prefix, separator, local = attributes[key].partition(':')
                if not separator: prefix, local = '', prefix
                if prefix and prefix not in namespaces[element]: raise EvidenceError('Unbound XML type namespace')
                attributes[key] = '{' + namespaces[element].get(prefix, '') + '}' + local
            return [element.tag, sorted(attributes.items()), element.text or '',
                    [[tree(child), child.tail or ''] for child in children]]
        return tree(parser.root)
    if row['kind'] == 'boolean' and row['value'] in ('true', 'false'):
        return row['value'] == 'true'
    raise EvidenceError('Unknown wire observation type')


def compare(main, release):
    problems, findings, matched = [], [], []
    for label, report in [('main', main), ('release', release)]:
        if (not isinstance(report, dict) or type(report.get('schemaVersion')) is not int
                or report['schemaVersion'] != 1 or not isinstance(report.get('cases'), dict)
                or not isinstance(report.get('errors'), dict)):
            raise EvidenceError('Missing complete binary-client observation: ' + label)
        if set(report['cases']) | set(report['errors']) != EXPECTED_CASES or set(report['cases']) & set(report['errors']):
            raise EvidenceError('Missing, duplicated or unreviewed binary-client cases: ' + label)
        for name, error in report['errors'].items():
            if label == 'main' and name in release.get('cases', {}):
                findings.append({'case': name, 'classification': 'fixed-main-client-rejection', 'errorType': error})
            problems.append({'case': name, 'side': label, 'reason': 'client-rejected-input', 'errorType': error})
    for name in sorted(set(main['cases']) & set(release['cases'])):
        before, after = main['cases'][name], release['cases'][name]
        expected_kind = ('api' if name == 'changed-public-java-api' else 'boolean'
                         if name == 'opt-in-soap-payload-leaked' else 'xml' if name.endswith('xml') else 'json')
        if before.get('kind') != expected_kind or after.get('kind') != expected_kind:
            problems.append({'case': name, 'reason': 'unexpected-wire-observation-type'}); continue
        if before['kind'] != after['kind']:
            problems.append({'case': name, 'reason': 'wire-type-changed'}); continue
        try:
            new = canonical(after)
        except Exception as failure:
            problems.append({'case': name, 'side': 'release', 'reason': 'invalid-wire-output',
                             'errorType': type(failure).__name__})
            continue
        try:
            old = canonical(before)
        except Exception as failure:
            findings.append({'case': name, 'classification': 'fixed-main-invalid-wire',
                             'errorType': type(failure).__name__})
            problems.append({'case': name, 'side': 'main', 'reason': 'unqualified-main-wire-output',
                             'errorType': type(failure).__name__})
            continue
        if name == 'changed-public-java-api':
            for owner, members in old.items():
                if members == ['not-public']: continue
                if not members and not new.get(owner):
                    problems.append({'case': name, 'reason': 'unresolved-public-api-inventory', 'type': owner})
                    continue
                removed = sorted(set(members) - set(new.get(owner, [])))
                if removed: problems.append({'case': name, 'reason': 'public-api-removed-or-changed',
                                             'type': owner, 'removedMembers': removed})
            if not any(p['case'] == name for p in problems): matched.append(name)
        elif name == 'opt-in-soap-payload-leaked':
            if old or new:
                findings.append({'case': name, 'classification': 'pre-existing' if old and new else
                                 'fixed' if old else 'regression', 'mainFailedPrivacy': old, 'releaseFailedPrivacy': new})
            if not old and new: problems.append({'case': name, 'reason': 'new-payload-exposure'})
        elif old != new:
            problems.append({'case': name, 'reason': 'wire-value-changed'})
        else: matched.append(name)
    return {'schemaVersion': 1, 'ready': not problems, 'expectedCases': len(EXPECTED_CASES),
            'matchedWireCases': matched, 'problems': problems, 'findings': findings}


def bounded_copy(source, target, budget):
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open('xb') as output:
        while data := source.read(65536):
            budget[0] += len(data)
            if budget[0] > LIMIT: raise EvidenceError('Binary client inputs exceed the extraction bound')
            output.write(data)


def extract_main(package, destination, log):
    prefix = ('usr', 'share', 'carlos-emr', 'webapp', 'carlos')
    process = subprocess.Popen(['dpkg-deb', '--fsys-tarfile', str(package)], stdout=subprocess.PIPE, stderr=log)
    budget = [0]
    try:
        with tarfile.open(fileobj=process.stdout, mode='r|') as archive:
            for member in archive:
                name = PurePosixPath(member.name.removeprefix('./'))
                if name.is_absolute() or '..' in name.parts:
                    raise EvidenceError('Main package contains an escaping member')
                if name.parts[:len(prefix)] != prefix: continue
                relative = PurePosixPath(*name.parts[len(prefix):])
                if len(relative.parts) < 2 or relative.parts[:2] not in {('WEB-INF', 'classes'), ('WEB-INF', 'lib')}: continue
                if member.isdir(): continue
                if not member.isfile(): raise EvidenceError('Main application binary contains a link or special file')
                with archive.extractfile(member) as source:
                    bounded_copy(source, destination / relative, budget)
        if process.wait() != 0: raise EvidenceError('Main package extraction failed')
    finally:
        if process.poll() is None: process.kill(); process.wait()
        process.stdout.close()


def extract_release(war, destination):
    build_manifest(war)  # Check bounded, unambiguous candidate ZIP paths before opening any member.
    budget = [0]
    with zipfile.ZipFile(war) as archive:
        for member in archive.infolist():
            name = PurePosixPath(member.filename)
            if name.parts[:2] not in {('WEB-INF', 'classes'), ('WEB-INF', 'lib')} or member.is_dir(): continue
            with archive.open(member) as source: bounded_copy(source, destination / name, budget)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--main-package', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--tomcat-core', type=Path, required=True)
    args = parser.parse_args()
    if not args.candidate.is_file() or not args.main_package.is_file():
        raise EvidenceError('Main package and source-bound candidate WAR are both required')
    if not args.tomcat_core.is_file() or gate.digest(args.tomcat_core) != TOMCAT_CORE_SHA256:
        raise EvidenceError('Tomcat 11.0.27 class-loader implementation differs from its reviewed pin')
    baseline = gate.read_json(gate.ROOT / 'docs/testing/release-2026.08-lifecycle-baselines.json')
    plan = gate.load_plan()
    if baseline['sourceCommit'] != plan['baselines']['currentMain']:
        raise EvidenceError('The main binary pin differs from the reviewed main source')
    asset = next(row for row in baseline['assets'] if row['name'].endswith('_amd64.deb'))
    if gate.digest(args.main_package) != asset['sha256']:
        raise EvidenceError('Main binary differs from the independently pinned published asset')
    directory = args.output.parent
    directory.mkdir(parents=True, exist_ok=True)
    changed = subprocess.check_output(['git', 'diff', '--no-renames', '--name-only', '-z', '--diff-filter=ACDMRT',
        plan['baselines']['currentMain'], 'HEAD', '--', 'src/main/java'], cwd=gate.ROOT).decode().split('\0')
    api_inventory = directory / 'changed-public-types.txt'
    types = sorted({path.removeprefix('src/main/java/').removesuffix('.java').replace('/', '.')
                    for path in changed if path.endswith('.java') and not path.endswith('/package-info.java')})
    if not types: raise EvidenceError('Main/release changed production API inventory is empty')
    with api_inventory.open('x') as output: output.write('\n'.join(types) + '\n')
    metadata = directory / 'binary-inputs.json'
    gate.write_json(metadata, {'schemaVersion': 1, 'mainCommit': baseline['sourceCommit'],
        'mainPackageSha256': gate.digest(args.main_package), 'releaseSource': gate.source_identity(),
        'releaseWarSha256': gate.digest(args.candidate), 'tomcatCoreSha256': gate.digest(args.tomcat_core),
        'clientCorpusSha256': gate.digest(CORPUS)})
    with tempfile.TemporaryDirectory(prefix='binary-clients-', dir=directory) as temporary:
        temporary = Path(temporary); classes = temporary / 'probe'; classes.mkdir()
        subprocess.run(['javac', '-d', str(classes), str(gate.ROOT / 'scripts/ci/WarWireCompatibilityProbe.java')], check=True)
        for side in ('main', 'release'):
            webapp = temporary / side; webapp.mkdir()
            with (directory / ('private-' + side + '-probe.log')).open('xb') as log:
                if side == 'main': extract_main(args.main_package, webapp, log)
                else: extract_release(args.candidate, webapp)
                result = subprocess.run(['java', '--add-opens=java.base/java.lang=ALL-UNNAMED',
                    '--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED',
                    '-Duser.timezone=America/Toronto', '-Duser.language=en',
                    '-Duser.country=CA', '-cp', str(classes) + os.pathsep + str(args.tomcat_core.resolve()),
                    'WarWireCompatibilityProbe', str(webapp),
                    str(directory / (side + '-wire.json')), str(CORPUS), str(api_inventory)], stdout=log, stderr=log)
                if result.returncode and not (directory / (side + '-wire.json')).is_file():
                    raise EvidenceError('Binary client probe did not retain observations')
            shutil.rmtree(webapp)
    result = compare(gate.read_json(directory / 'main-wire.json'), gate.read_json(directory / 'release-wire.json'))
    details = directory / 'comparison-details.json'; gate.write_json(details, result)
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': {
        'both-binary-clients-complete': not any(p['reason'] == 'client-rejected-input' for p in result['problems']),
        'no-unreviewed-wire-breaks': result['ready']}, 'inputs': [
        {'path': file.name, 'sha256': gate.digest(file)} for file in
        [metadata, api_inventory, directory / 'main-wire.json', directory / 'release-wire.json', details]]})
    return 0 if result['ready'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
