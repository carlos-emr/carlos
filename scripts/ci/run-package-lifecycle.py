#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Host controller for clean, unchanged-client upgrade and reinstall cells.

Requires an explicit local disposable Docker daemon. Failed containers and all
private evidence remain available; only successful owned containers are removed.
Unsupported-upgrade and interrupted-transaction qualification remain separately
required by the full lifecycle check.
"""
import argparse
import copy
import json
import os
from pathlib import Path
import stat
import signal
import subprocess
import uuid

import release_verification as gate
from junit_evidence import EvidenceError
from lifecycle_baselines import required_baselines
from package_lifecycle import validate_config, validate_inventory
from process_group import run_process_group


def docker_environment():
    # Endpoint selectors must not redirect operations away from this disposable
    # host. Keep DOCKER_CONFIG, registry credentials and the configured proxy.
    selectors = {'DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH'}
    return {key: value for key, value in os.environ.items() if key not in selectors}


def docker(*arguments, capture=False, timeout=60):
    environment = docker_environment()
    command = ['docker', '--host=unix:///var/run/docker.sock', *arguments]
    if capture:
        result = subprocess.run(command, env=environment, capture_output=True, text=True, timeout=timeout)
        if result.returncode: raise EvidenceError('Local Docker operation failed')
        return result.stdout
    return run_process_group(command, env=environment, timeout=timeout, grace=30).returncode


def checked_file(path, expected=None):
    path = Path(path)
    if path.is_symlink() or not stat.S_ISREG(path.lstat().st_mode) or (expected and gate.digest(path) != expected):
        raise EvidenceError('Lifecycle input is nonregular or differs from its pin')
    return path.resolve()


def stop_failed_cell(name):
    """Stopping docker exec's client does not stop the command inside Docker."""
    previous = {number: signal.signal(number, signal.SIG_IGN) for number in (signal.SIGINT, signal.SIGTERM)}
    try:
        command = ['docker', '--host=unix:///var/run/docker.sock']
        result = subprocess.run(command + ['stop', '--time', '30', name], env=docker_environment(),
                                capture_output=True, text=True, timeout=45)
        if result.returncode:
            return False
        result = subprocess.run(command + ['inspect', '--format', '{{.State.Running}}', name],
            env=docker_environment(), capture_output=True, text=True, timeout=15)
        return result.returncode == 0 and result.stdout.strip() == 'false'
    except (OSError, subprocess.TimeoutExpired):
        return False
    finally:
        for number, handler in previous.items():
            signal.signal(number, handler)


def retained_inputs(directory, output, passed):
    # A timed-out root process may leave private files unreadable to the host
    # user, or still changing if shutdown failed. Preserve them in place, but
    # always emit the failed result bound to the host-written diagnosis.
    if passed:
        paths = sorted(directory.rglob('*'))
    else:
        failure = directory / 'lifecycle-failure.json'
        paths = [failure if failure.exists() else directory / 'lifecycle.json']
    inputs = []
    for path in paths:
        if path.is_symlink(): raise EvidenceError('Lifecycle evidence contains a link')
        if path.is_file() and path != output:
            inputs.append({'path': str(path.relative_to(directory)), 'sha256': gate.digest(path)})
    return inputs


def package_inputs(config, baselines):
    for baseline in baselines:
        for name, prefix in (('emr', 'carlos-emr_'), ('drugref', 'carlos-emr-drugref_'), ('ctl', 'carlos-ctl_')):
            pin = next(row for row in baseline['assets'] if row['name'].startswith(prefix))
            supplied = config['packages'][baseline['tag']][name]
            if supplied['sha256'] != pin['sha256']:
                raise EvidenceError('Lifecycle predecessor is not the reviewed published package')
    ctl = next(row for row in baselines[0]['assets'] if row['name'].startswith('carlos-ctl_'))
    if config['packages']['target']['ctl']['sha256'] != ctl['sha256']:
        raise EvidenceError('Lifecycle target CLI differs from its reviewed pin')
    for packages in config['packages'].values():
        for row in packages.values(): checked_file(row['path'], row['sha256'])


def run_lifecycle():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=('ON', 'BC'), required=True)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get('CARLOS_DISPOSABLE_HOST') != 'true' or os.environ.get('EXCLUSIVE') != '1':
        raise EvidenceError('Lifecycle needs an explicitly exclusive disposable local Docker host')
    directory = args.output.parent.resolve()
    if directory.stat().st_mode & 0o077 or directory.is_relative_to(gate.ROOT):
        raise EvidenceError('Lifecycle evidence must be private and outside the source checkout')
    source = gate.source_identity()
    if not source['clean']: raise EvidenceError('Lifecycle requires frozen committed source')
    config_path = checked_file(os.environ['CARLOS_PACKAGE_LIFECYCLE_CONFIG'])
    config = validate_config(gate.read_json(config_path))
    pins = gate.read_json(gate.ROOT / 'docs/testing/release-2026.08-lifecycle-baselines.json')
    baselines = required_baselines(pins); package_inputs(config, baselines)
    artifact = checked_file(args.artifact); artifact_hash = gate.digest(artifact)
    checked_file(config['caFile']); checked_file(Path(config['browserDirectory']) / 'chrome')
    image = json.loads(docker('image', 'inspect', config['runtimeImage'], capture=True))
    if len(image) != 1 or image[0].get('Id') != config['runtimeImage']:
        raise EvidenceError('Lifecycle runtime image differs from its immutable identity')
    document = {'schemaVersion': 1, 'source': source, 'profile': args.profile,
                'warSha256': artifact_hash, 'runtimeImage': config['runtimeImage'], 'cells': []}
    passed = False
    active_container = None
    try:
        for role, baseline in [('clean', None), ('main', baselines[0]), ('historical', baselines[1])]:
            cell = directory / role; cell.mkdir(mode=0o700)
            context = {'schemaVersion': 1, 'role': role, 'profile': args.profile,
                       'source': source, 'war': {'path': '/candidate.war', 'sha256': artifact_hash}}
            mounts = ['--mount', f'type=bind,source={gate.ROOT},target=/source,readonly',
                      '--mount', f'type=bind,source={cell},target=/qualification',
                      '--mount', f'type=bind,source={artifact},target=/candidate.war,readonly',
                      '--mount', f'type=bind,source={config["caFile"]},target=/session-ca.pem,readonly',
                      '--mount', f'type=bind,source={config["browserDirectory"]},target=/browser,readonly']
            for key, packages in [('target', config['packages']['target']),
                                  *([('baseline', config['packages'][baseline['tag']])] if baseline else [])]:
                context[key] = copy.deepcopy(packages)
                for name, row in packages.items():
                    target = '/' + key + '-' + name + '.deb'
                    mounts += ['--mount', f'type=bind,source={checked_file(row["path"])},target={target},readonly']
                    context[key][name]['path'] = target
            if baseline: context['baseline']['tag'] = baseline['tag']
            gate.write_json(cell / 'context.json', context)
            name = 'carlos-5700-lifecycle-' + uuid.uuid4().hex[:16]
            active_container = name
            gate.write_json(cell / 'container.json', {'name': name, 'image': config['runtimeImage']})
            with (cell / 'private-controller.log').open('xb') as log:
                command = ['docker', '--host=unix:///var/run/docker.sock', 'run', '-d', '--name', name,
                    '--label', 'carlos.issue=5700', '--privileged', '--cgroupns=private',
                    # Docker's client defaults contain the reachable proxy.
                    # Forwarding the outer-only proxy hostname overrides it.
                    '--tmpfs', '/run', '--tmpfs', '/run/lock', *mounts,
                    config['runtimeImage'], '/sbin/init']
                result = subprocess.run(command, stdout=log, stderr=log, timeout=60,
                    env=docker_environment())
                if result.returncode: raise EvidenceError('Could not create lifecycle cell')
                # Wait for systemd's explicit ready/degraded state, never a blind sleep.
                code = docker('exec', name, 'systemctl', 'is-system-running', '--wait', timeout=90)
                if code not in (0, 1): raise EvidenceError('Lifecycle systemd did not become ready')
                code = docker('exec', '-w', '/source', name, 'python3',
                    'scripts/ci/run-package-lifecycle-cell.py', '--context', '/qualification/context.json', timeout=5400)
                # Return only this owned evidence tree to the host user. No
                # application state, ownership or source permissions are changed.
                if docker('exec', name, 'chown', '-hR', f'{os.getuid()}:{os.getgid()}', '/qualification'):
                    raise EvidenceError('Could not retain readable lifecycle evidence; container retained')
                if code: raise EvidenceError('Lifecycle cell failed; container retained: ' + role)
                record = cell / 'cell.json'
                document['cells'].append({'role': role, 'path': str(record.relative_to(directory)), 'sha256': gate.digest(record)})
                if docker('stop', '--time', '45', name, timeout=60) or docker('rm', name):
                    raise EvidenceError('Successful owned lifecycle container could not be removed')
                active_container = None
        gate.write_json(directory / 'lifecycle.json', document)
        validate_inventory(document, source, args.profile, artifact_hash, directory, gate.read_json, gate.digest)
        if gate.source_identity() != source or gate.digest(artifact) != artifact_hash:
            raise EvidenceError('Lifecycle source or artifact changed during execution')
        package_inputs(config, baselines)
        passed = True
    except (Exception, KeyboardInterrupt) as failure:
        document['failureType'] = type(failure).__name__
        if isinstance(failure, EvidenceError): document['failureReason'] = str(failure)
        if active_container:
            document['reconciliation'] = {'container': active_container,
                'containerStopped': stop_failed_cell(active_container),
                'restorationVerified': False, 'hostRequiresReconciliation': True}
        failure_path = directory / ('lifecycle-failure.json' if (directory / 'lifecycle.json').exists() else 'lifecycle.json')
        gate.write_json(failure_path, document)
    inputs = retained_inputs(directory, args.output, passed)
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': {'complete-lifecycle-core': passed}, 'inputs': inputs})
    return 0 if passed else 1


def main():
    def interrupted(_signum, _frame):
        raise KeyboardInterrupt
    # Cover creation and evidence phases too, outside docker exec's own handler.
    previous = signal.signal(signal.SIGTERM, interrupted)
    try:
        return run_lifecycle()
    finally:
        signal.signal(signal.SIGTERM, previous)


if __name__ == '__main__':
    raise SystemExit(main())
