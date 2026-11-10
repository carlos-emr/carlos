#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Perform one owned upgrade/reinstall with independent whole-data preservation.

This is the inner operation used by lifecycle orchestration, not a substitute for
clean install, old-client reopen, unsupported-upgrade or both-province evidence.
"""
import argparse
import os
from pathlib import Path
import pwd
import stat
import subprocess
import uuid

import installed_package_state as state
import release_verification as gate
from junit_evidence import EvidenceError
from lifecycle_baselines import candidate_cli, select_baseline
from release_deployment import attest, attest_package, local_target, package_contents
from process_group import ProcessGroupFailure, run_process_group

ASSERTIONS = ('package-pins-and-version-order', 'predecessor-application-bound',
    'package-transaction-succeeded', 'upgrade-contract-passed', 'clinical-cells-preserved',
    'document-bytes-and-metadata-preserved', 'encryption-key-preserved',
    'candidate-application-bound', 'owned-file-canary-cleaned')


def package_field(path, field):
    result = subprocess.run(['dpkg-deb', '-f', str(path), field], capture_output=True, text=True, timeout=20)
    if result.returncode or not result.stdout.strip() or '\n' in result.stdout.strip():
        raise EvidenceError('Candidate package metadata is incomplete')
    return result.stdout.strip()


def installed_version(package):
    result = subprocess.run(['dpkg-query', '-W', '-f=${Version}', package], capture_output=True, text=True, timeout=20)
    if result.returncode or not result.stdout.strip():
        raise EvidenceError('Required predecessor package is not installed')
    return result.stdout.strip()


def pinned(path, digest):
    return (not path.is_symlink() and stat.S_ISREG(path.lstat().st_mode)
            and gate.digest(path) == digest)


def baseline(path, environment):
    with path.open('xb') as output:
        os.chmod(path, 0o600)
        result = run_process_group(['bash', str(gate.ROOT / 'scripts/deb-upgrade-baseline.sh')],
                                   env=environment, stdout=output, timeout=90)
        if result.returncode:
            raise EvidenceError('Package preservation baseline failed')
    fields = {}
    for line in path.read_text().splitlines():
        key, separator, value = line.partition('=')
        if not separator or key in fields:
            raise EvidenceError('Package preservation baseline is ambiguous')
        fields[key] = value
    return fields


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=('upgrade', 'reinstall'), required=True)
    parser.add_argument('--profile', choices=('ON', 'BC'), required=True)
    parser.add_argument('--emr', type=Path, required=True)
    parser.add_argument('--emr-sha256', required=True)
    parser.add_argument('--drugref', type=Path, required=True)
    parser.add_argument('--drugref-sha256', required=True)
    parser.add_argument('--ctl', type=Path, required=True)
    parser.add_argument('--before-package', type=Path)
    parser.add_argument('--baseline-tag', help='Reviewed predecessor tag; defaults to current main')
    parser.add_argument('--candidate-war', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if (os.geteuid() != 0 or os.environ.get('CARLOS_DISPOSABLE_HOST') != 'true'
            or os.environ.get('CARLOS_SUITE_PROVINCE') != args.profile or os.environ.get('EXCLUSIVE') != '1'
            or not local_target(os.environ.get('BASE_URL', ''))):
        raise EvidenceError('Package transition requires an exclusive named disposable loopback install')
    directory = args.output.parent
    if directory.is_symlink() or not directory.is_dir() or directory.stat().st_mode & 0o077:
        raise EvidenceError('Package transition evidence requires a private fresh directory')
    assertions = dict.fromkeys(ASSERTIONS, False)
    inputs = []
    details = {'schemaVersion': 1, 'mode': args.mode, 'profile': args.profile, 'transactionAttempted': False}
    failure_type = None
    try:
        pins = gate.read_json(gate.ROOT / 'docs/testing/release-2026.08-lifecycle-baselines.json')
        if args.baseline_tag and args.mode != 'upgrade':
            raise EvidenceError('Predecessor selection applies only to an upgrade')
        selected = select_baseline(pins, args.baseline_tag)
        ctl_pin = candidate_cli(pins)
        if (not pinned(args.emr, args.emr_sha256) or not pinned(args.drugref, args.drugref_sha256)
                or not pinned(args.ctl, ctl_pin['sha256'])
                or package_field(args.emr, 'Package') != 'carlos-emr'
                or package_field(args.drugref, 'Package') != 'carlos-emr-drugref'
                or package_field(args.ctl, 'Package') != 'carlos-ctl'
                or package_field(args.ctl, 'Version') != ctl_pin['version']):
            raise EvidenceError('Package transition inputs do not match their named pins')
        version = package_field(args.emr, 'Version')
        prior_version = installed_version('carlos-emr')
        if (package_field(args.drugref, 'Version') != version
                or not state.version_allowed(args.mode, prior_version, version)):
            raise EvidenceError('Candidate is not the required upgrade/equal-version reinstall; no package transaction attempted')
        candidate = package_contents(args.emr, args.emr_sha256)
        if 'buildIdentity' not in candidate:
            raise EvidenceError('Candidate package lacks its expected public build identity')
        expected_identity = directory / 'expected-build.properties'
        with expected_identity.open('x') as output:
            output.write(''.join(key + '=' + value + '\n' for key, value in candidate['buildIdentity'].items()))
        inputs.append(expected_identity)
        assertions['package-pins-and-version-order'] = True
        details['candidateVersion'] = version
        details['previousVersion'] = prior_version
        details['candidatePackageSha256'] = args.emr_sha256
        details['candidateDrugrefSha256'] = args.drugref_sha256
        if args.mode == 'reinstall':
            previous_contents = candidate
            previous = attest_package(args.emr, args.emr_sha256, base_url=os.environ['BASE_URL'])
        else:
            if not args.before_package:
                raise EvidenceError('Upgrade requires its pinned predecessor package')
            pin = next(row for row in selected['assets'] if row['name'].startswith('carlos-emr_'))
            previous_contents = package_contents(args.before_package, pin['sha256'])
            previous = attest_package(args.before_package, pin['sha256'], base_url=os.environ['BASE_URL'])
            if package_field(args.before_package, 'Version') != prior_version:
                raise EvidenceError('Installed predecessor version differs from its reviewed package')
            details['baselineTag'] = selected['tag']
            details['baselineSourceCommit'] = selected['sourceCommit']
        prior_cli = selected['cliVersion'] if args.mode == 'upgrade' else package_field(args.ctl, 'Version')
        if installed_version('carlos-emr-drugref') != prior_version or installed_version('carlos-ctl') != prior_cli:
            raise EvidenceError('Predecessor DrugRef or reviewed client version differs')
        details['previousDeployment'] = previous
        assertions['predecessor-application-bound'] = True
        environment = {**os.environ, 'ADMIN_USER': os.environ.get('TEST_USER', 'carlosdoc')}
        pre = directory / 'baseline-before.txt'
        fields = baseline(pre, environment)
        if (fields.get('cfg.province', '').lower() != args.profile.lower()
                or fields.get('admin.forceReset') != '0' or fields.get('service.active') != 'active'):
            raise EvidenceError('Predecessor has the wrong province, is stopped or has not completed first login')
        inputs.append(pre)
        owner = pwd.getpwnam('carlos')
        canary = Path('/var/lib/carlos-emr/CarlosDocument/carlos/document') / ('FAKE-5700-transition-' + uuid.uuid4().hex + '.txt')
        content = b'FAKE installed preservation canary caf\xc3\xa9 \xce\xa9\n'
        with canary.open('xb') as stream:
            stream.write(content)
        os.chown(canary, owner.pw_uid, owner.pw_gid); canary.chmod(0o640)
        details['ownedCanary'] = str(canary)
        clinical = state.clinical_state()
        before = {'clinical': clinical, 'files': state.stored_files(), 'keySha256': state.encryption_key()}
        before_file = directory / 'private-state-before.json'; gate.write_json(before_file, before); inputs.append(before_file)
        details['transactionAttempted'] = True
        log = directory / 'private-package-transaction.log'
        with log.open('xb') as output:
            command = ['apt-get', 'install', '-y', '--no-remove']
            if args.mode == 'reinstall': command.append('--reinstall')
            result = run_process_group(command + list(map(str, (args.emr, args.drugref, args.ctl))),
                env={**environment, 'DEBIAN_FRONTEND': 'noninteractive'}, stdout=output, stderr=output, timeout=1200)
        assertions['package-transaction-succeeded'] = (result.returncode == 0
            and installed_version('carlos-emr') == version and installed_version('carlos-emr-drugref') == version
            and installed_version('carlos-ctl') == package_field(args.ctl, 'Version'))
        after = {'clinical': state.clinical_state({table: row['columns'] for table, row in clinical.items()}),
                 'files': state.stored_files(), 'keySha256': state.encryption_key()}
        after_file = directory / 'private-state-after.json'; gate.write_json(after_file, after); inputs.append(after_file)
        assertions['clinical-cells-preserved'] = before['clinical'] == after['clinical']
        documents = state.document_preservation(before['files'], after['files'],
            previous_contents['entries'], candidate['entries'])
        assertions['document-bytes-and-metadata-preserved'] = documents['preserved']
        details['managedDocumentAssetUpdates'] = documents['managedAssetUpdates']
        assertions['encryption-key-preserved'] = before['keySha256'] == after['keySha256']
        with (directory / 'private-preservation-check.log').open('xb') as output:
            verified = run_process_group(['bash', str(gate.ROOT / 'scripts/deb-upgrade-verify.sh')],
                env={**environment, 'PRE': str(pre), 'POST': str(directory / 'baseline-after.txt'),
                     'UPGRADE_LOG': str(log), 'CHECK_LOG': str(directory / 'private-health-check.log'),
                     'TARGET_BUILD_PROPERTIES': str(expected_identity)}, stdout=output, stderr=output, timeout=420)
        assertions['upgrade-contract-passed'] = verified.returncode == 0
        details['candidateDeployment'] = attest(args.candidate_war, base_url=os.environ['BASE_URL'])
        assertions['candidate-application-bound'] = True
        if all(value for name, value in assertions.items() if name != 'owned-file-canary-cleaned'):
            if not canary.is_symlink() and canary.read_bytes() == content:
                canary.unlink()
                assertions['owned-file-canary-cleaned'] = not canary.exists()
    except Exception as failure:
        failure_type = type(failure).__name__
        if isinstance(failure, EvidenceError): details['failureReason'] = str(failure)
        if isinstance(failure, ProcessGroupFailure):
            details.update(processGroupStopped=failure.quiescent, restorationVerified=False,
                           hostRequiresReconciliation=True)
        # Never retry an uncertain package transaction or remove its canary here.
    details['assertions'] = assertions
    details['failureType'] = failure_type
    summary = directory / 'transition-details.json'; gate.write_json(summary, details); inputs.append(summary)
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': assertions, 'inputs': [
        {'path': path.name, 'sha256': gate.digest(path)} for path in inputs]})
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
