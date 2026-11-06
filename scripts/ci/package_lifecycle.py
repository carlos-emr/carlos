# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Validate container lifecycle records without requiring a host installation."""
from pathlib import Path
import re
import subprocess

from command_evidence import relative_file
from junit_evidence import EvidenceError
from lifecycle_baselines import required_baselines

ROLES = {'clean', 'main', 'historical'}
CELL_ASSERTIONS = {'ubuntu-systemd-prerequisite', 'pinned-installation', 'forced-first-login',
    'unchanged-client-before', 'real-transition', 'clinical-cleanup-preserved',
    'documents-cleanup-preserved', 'unchanged-client-after', 'candidate-application-bound',
    'service-health', 'private-recovery-retained', 'dependency-contract', 'read-only-state-preserved'}


def validate_inventory(document, source, profile, artifact_hash, directory, read_json, digest, required_roles=None, *, target_package_hash):
    if (not isinstance(target_package_hash, str) or not re.fullmatch('[0-9a-f]{64}', target_package_hash)
            or not isinstance(document, dict) or document.get('targetPackageSha256') != target_package_hash):
        raise EvidenceError('Lifecycle target package differs from the configured receipt binding')
    expected = ROLES if required_roles is None else set(required_roles)
    if (not expected or not expected <= ROLES or (required_roles is not None
            and (not isinstance(required_roles, list) or len(required_roles) != len(expected)))):
        raise EvidenceError('Lifecycle has an invalid required role selection')
    if (not isinstance(document, dict) or type(document.get('schemaVersion')) is not int or document.get('schemaVersion') != 1
            or document.get('source') != source or document.get('profile') != profile
            or document.get('warSha256') != artifact_hash
            or not isinstance(document.get('cells'), list) or document.get('failureType')):
        raise EvidenceError('Lifecycle belongs to different source, province or WAR')
    rows = document['cells']
    if (any(not isinstance(row, dict) for row in rows)
            or len(rows) != len(expected) or {row.get('role') for row in rows} != expected
            or (required_roles is not None and document.get('requestedRoles') != required_roles)):
        raise EvidenceError('Lifecycle omitted or duplicated a required install/upgrade baseline')
    baselines = required_baselines(read_json(Path(__file__).resolve().parents[2]
        / 'docs/testing/release-2026.08-lifecycle-baselines.json'))
    predecessor_pins = {role: next(asset['sha256'] for asset in baseline['assets']
        if asset['name'].startswith('carlos-emr_')) for role, baseline in zip(('main', 'historical'), baselines)}
    predecessor_pins['clean'] = target_package_hash
    for row in rows:
        path = relative_file(directory, row.get('path'))
        if digest(path) != row.get('sha256'):
            raise EvidenceError('Lifecycle cell changed after collection')
        cell = read_json(path)
        assertions = cell.get('assertions', {})
        binding = cell.get('candidateDeployment', {})
        initial = cell.get('initialDeployment', {})
        dependency = cell.get('dependencyContract', {})
        if (cell.get('source') != source or cell.get('profile') != profile
                or cell.get('role') != row['role'] or cell.get('warSha256') != artifact_hash
                or set(assertions) != CELL_ASSERTIONS or any(value is not True for value in assertions.values())
                or cell.get('failureType') is not None or cell.get('recoveryFailureType')
                or initial.get('scope') != 'installed-package-application'
                or not re.fullmatch('[0-9a-f]{64}', initial.get('packageSha256', ''))
                or not re.fullmatch('[0-9a-f]{64}', initial.get('contentSha256', ''))
                or initial.get('packageSha256') != predecessor_pins[row['role']]
                or dependency.get('floor') != '1.3.1'
                or (row['role'] != 'historical' and dependency.get('installedCli') != '1.3.1')
                or (row['role'] == 'historical' and (dependency.get('installedCli') != '1.3.0'
                    or any(dependency.get(key) is not True for key in
                           ('olderCliRefused', 'compatibleCliResolved', 'statePreserved'))))
                or binding.get('warSha256') != artifact_hash
                or binding.get('scope') != 'installed-package-application'
                or not re.fullmatch('[0-9a-f]{64}', binding.get('contentSha256', ''))):
            raise EvidenceError('Lifecycle lacks complete installed candidate acceptance')
    return {'warSha256': artifact_hash, 'packageSha256': target_package_hash, 'scope': 'orchestrated-package-lifecycle',
            'contentSha256': digest(relative_file(directory, 'lifecycle.json'))}


def require_checkout_playwright(root, read_json):
    """Refuse missing/stale checkout modules before any package installation."""
    lock = read_json(root / 'package-lock.json')
    directories = [root / 'node_modules' / name for name in ('playwright', 'playwright-core')]
    for directory in directories:
        expected = lock.get('packages', {}).get('node_modules/' + directory.name, {}).get('version')
        if (not isinstance(expected, str) or not directory.resolve().is_relative_to(root.resolve())
                or read_json(directory / 'package.json').get('version') != expected):
            raise EvidenceError('Run npm ci in the checkout: Playwright must match its lock')
    browsers = read_json(directories[1] / 'browsers.json').get('browsers', [])
    if len([row for row in browsers if row.get('name') == 'chromium' and row.get('browserVersion')]) != 1:
        raise EvidenceError('Checkout Playwright lacks its locked Chromium identity')
    try:
        loaded = subprocess.run(['node', '-e', 'require(process.argv[1]);require(process.argv[2]);',
            *map(str, directories)], cwd=root, capture_output=True, timeout=15)
    except (OSError, subprocess.TimeoutExpired) as failure:
        raise EvidenceError('Checkout Playwright cannot load; provision Node and run npm ci') from failure
    if loaded.returncode:
        raise EvidenceError('Checkout Playwright cannot load; run npm ci before qualification')


def validate_config(document):
    if (not isinstance(document, dict) or set(document) != {'runtimeImage', 'caFile', 'browserDirectory', 'packages'}
            or not re.fullmatch('sha256:[0-9a-f]{64}', document.get('runtimeImage', ''))
            or not isinstance(document.get('packages'), dict)
            or set(document['packages']) != {'target', '2026.08.0-beta6', '2026.08.0-beta5'}):
        raise EvidenceError('Lifecycle needs an immutable runtime, trust/browser paths and all package baselines')
    for packages in document['packages'].values():
        if not isinstance(packages, dict) or set(packages) != {'emr', 'drugref', 'ctl'}:
            raise EvidenceError('Lifecycle package set is incomplete')
        for row in packages.values():
            if (not isinstance(row, dict) or set(row) != {'path', 'sha256'}
                    or not isinstance(row['path'], str) or not Path(row['path']).is_absolute()
                    or not re.fullmatch('[0-9a-f]{64}', row.get('sha256', ''))):
                raise EvidenceError('Lifecycle package needs its absolute path and digest')
    for name in ('caFile', 'browserDirectory'):
        if not isinstance(document[name], str) or not Path(document[name]).is_absolute():
            raise EvidenceError('Lifecycle runtime paths must be absolute')
    return document
