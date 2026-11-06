#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Run one disposable Ubuntu/systemd package lifecycle; retain failures in place.

Invoked only by the host controller. The same read-only /source clients execute
before and after upgrade. No service credentials are passed on command lines.
"""
import argparse
import importlib.util
import os
from pathlib import Path
import pwd
import re
import secrets
import shlex
import subprocess
import tarfile

import installed_package_state as state
import release_verification as gate
from junit_evidence import EvidenceError
from process_group import run_process_group
from release_deployment import attest, attest_package, package_contents

OUTPUT = Path('/qualification')
SMOKE = ['document-upload', 'tickler-crud', 'login']
ASSERTIONS = ('ubuntu-systemd-prerequisite', 'pinned-installation', 'forced-first-login',
              'unchanged-client-before', 'real-transition', 'clinical-cleanup-preserved',
              'documents-cleanup-preserved', 'unchanged-client-after',
              'candidate-application-bound', 'service-health', 'private-recovery-retained')


def run(command, name, *, environment=None, timeout=1200, check=True):
    with (OUTPUT / (name + '.log')).open('xb') as stream:
        os.chmod(stream.name, 0o600)
        result = run_process_group(command, cwd=gate.ROOT, env=environment or os.environ,
                                   stdout=stream, stderr=stream, timeout=timeout, grace=25)
    if check and result.returncode:
        raise EvidenceError('Lifecycle phase failed: ' + name)
    return result.returncode


def load_adapter(name):
    spec = importlib.util.spec_from_file_location('lifecycle_' + name.replace('-', '_'),
        gate.ROOT / 'scripts/ci' / (name + '.py'))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


def first_login(profile, browser):
    adapter = load_adapter('run-installed-first-login')
    adapter.credentials_user()  # Validate the private file before parsing it.
    fields = {}
    for line in adapter.ADMIN_FILE.read_text().splitlines():
        words = line.split()
        if len(words) == 2 and words[0] in ('user:', 'PIN:'):
            fields[words[0]] = words[1]
    if set(fields) != {'user:', 'PIN:'}:
        raise EvidenceError('Installer identity is incomplete')
    password = 'Qa5700!' + secrets.token_urlsafe(18)
    environment = {**os.environ, 'BASE_URL': 'https://127.0.0.1/carlos',
        'CHROME_PATH': browser, 'EXPECT_FRONT_DOOR': 'true', 'MYSQL_HOST': 'localhost',
        'MYSQL_USER': 'root', 'MYSQL_PASSWORD': 'unused-socket-auth', 'MYSQL_DATABASE': 'carlos',
        'CARLOS_DISPOSABLE_HOST': 'true', 'CARLOS_SUITE_PROVINCE': profile,
        'ADMIN_FILE': str(adapter.ADMIN_FILE), 'NEW_PASSWORD': password,
        'TEST_USER': fields['user:'], 'TEST_PIN': fields['PIN:'], 'TEST_PASSWORD': password}
    run(['python3', 'scripts/ci/run-installed-first-login.py', '--profile', profile,
         '--output', str(OUTPUT / 'first-login.json')], 'first-login', environment=environment, timeout=420)
    summary, _ = gate.command_results({'file': 'first-login.json', 'assertions': list(adapter.ASSERTIONS)},
                                      OUTPUT, gate.read_json, gate.digest)
    if not summary['ready']:
        raise EvidenceError('First-login assertions are incomplete')
    environment.pop('ADMIN_FILE'); environment.pop('NEW_PASSWORD')
    base = '/var/lib/carlos-emr/CarlosDocument'
    environment.update(EXPECT_FRONT_DOOR='true', PRESCRIPTION_SIGNATURE_CLEANUP='true', BASE_DOCUMENT_DIR=base,
        LOGIN_AGREEMENT_EXPECT='hidden', STORED_DOCUMENT_EXPECT_CONTENT_UPDATES='true',
        CARLOS_DISPOSABLE_VM='true', EXCLUSIVE='1', CARLOS_LOG_JOURNAL_UNIT='carlos-emr.service',
        SCREENSHOT_DIR=str(OUTPUT / 'private-screenshots'), ARTIFACT_DIR=str(OUTPUT / 'private-artifacts'))
    for key in ('EDOC_NAV_DOCUMENT_STORE', 'LAB_UPLOAD_DOCUMENT_STORE', 'DOCUMENT_DIR',
                'LETTER_DOCUMENT_DIR', 'BOUNDARY_DOCUMENT_DIR', 'FORWARDING_SCOPE_DOCUMENT_STORE', 'RA_DOCUMENT_DIR'):
        environment[key] = base + '/carlos/document'
    environment.update(INCOMINGDOCUMENT_DIR=base + '/carlos/incomingdocs', EFORM_IMAGE_DIR=base + '/carlos/eform/images',
        OHIP_DISK_DIR=base + '/carlos/billing/download', INVOICE_DIR=base + '/carlos/billing/invoices')
    for name in ('private-screenshots', 'private-artifacts'):
        (OUTPUT / name).mkdir(mode=0o700)
    row = adapter.account(fields['user:'])
    environment['TEST_PASSWORD_HASH'] = row[3]
    return environment


def set_expected_build(environment, contents):
    identity = contents.get('buildIdentity', {})
    if set(identity) != {'build.version', 'build.job', 'build.number'}:
        raise EvidenceError('Pinned package lacks a complete build identity')
    environment.update(EXPECTED_BUILD_VERSION=identity['build.version'],
        RX_EXPECTED_BUILD_TAG=identity['build.version'] + ' (' + identity['build.job'] + ' ' + identity['build.number'] + ')')


def smoke(environment, phase):
    path = OUTPUT / (phase + '.xml')
    run(['node', 'scripts/run-playwright-suite.js', '--tier', 'smoke', '--strict-release',
         '--province', environment['CARLOS_SUITE_PROVINCE'], '--junit', str(path)],
        phase, environment=environment, timeout=600)
    result = gate.browser_results(path, {'selectedProfile': environment['CARLOS_SUITE_PROVINCE'],
                                         'selection': SMOKE}, gate.ROOT)
    if not result['ready']:
        raise EvidenceError('Lifecycle smoke contains missing or failing clients')


def transition_command(context, mode, output):
    target = context['target']
    command = ['python3', str(gate.ROOT / 'scripts/ci/run-installed-package-transition.py'),
        '--mode', mode, '--profile', context['profile'], '--emr', target['emr']['path'],
        '--emr-sha256', target['emr']['sha256'], '--drugref', target['drugref']['path'],
        '--drugref-sha256', target['drugref']['sha256'], '--ctl', target['ctl']['path'],
        '--candidate-war', context['war']['path'], '--output', str(output)]
    if mode == 'upgrade':
        command += ['--baseline-tag', context['baseline']['tag'], '--before-package', context['baseline']['emr']['path']]
    return command


def transition(context, environment):
    directory = OUTPUT / 'transition'; directory.mkdir(mode=0o700)
    output = directory / 'transition-result.json'
    mode = 'reinstall' if context['role'] == 'clean' else 'upgrade'
    command = transition_command(context, mode, output)
    before = {'clinical': state.clinical_state(), 'files': state.stored_files(), 'key': state.encryption_key()}
    gate.write_json(OUTPUT / 'private-before-owned-workflow.json', before)
    if mode == 'upgrade':
        # The unchanged client invokes this explicit installer path. Provision a
        # real controller there; never replace client code or shadow PATH tools.
        wrapper = Path('/root/carlos/scripts/lib/deb-upgrade-install.sh')
        wrapper.parent.mkdir(parents=True)
        if wrapper.exists() or wrapper.is_symlink():
            raise EvidenceError('Lifecycle installer path was already occupied')
        wrapper.write_text('#!/bin/bash\nset -euo pipefail\nexec ' + shlex.join(command) + '\n')
        wrapper.chmod(0o700)
        completion = OUTPUT / 'unchanged-upgrade-client.json'
        run(['node', 'scripts/deb-upgrade-canaries.js'], 'unchanged-upgrade-client',
            environment={**environment, 'RESULT_JSON': str(completion)}, timeout=1500)
        record = gate.read_json(completion)
        if (record.get('name') != 'deb-upgrade-canaries' or record.get('outcome') != 'PASS'
                or record.get('cleanupFailed') or record.get('failedStep') or record.get('detail')):
            raise EvidenceError('Unchanged client did not reopen and clean its retained fixtures')
        gate.require_locked_chromium([record.get('browserVersion', '')])
    else:
        run(command, 'reinstall', environment=environment, timeout=1500)
    adapter = load_adapter('run-installed-package-transition')
    result, _ = gate.command_results({'file': 'transition-result.json', 'assertions': list(adapter.ASSERTIONS)},
                                    directory, gate.read_json, gate.digest)
    if not result['ready']:
        raise EvidenceError('Real package transition assertions failed')
    after = {'clinical': state.clinical_state({t: row['columns'] for t, row in before['clinical'].items()}),
             'files': state.stored_files(), 'key': state.encryption_key()}
    gate.write_json(OUTPUT / 'private-after-owned-workflow.json', after)
    old = context['target'] if mode == 'reinstall' else context['baseline']
    old_entries = package_contents(Path(old['emr']['path']), old['emr']['sha256'])['entries']
    new_entries = package_contents(Path(context['target']['emr']['path']), context['target']['emr']['sha256'])['entries']
    return (before['clinical'] == after['clinical'] and before['key'] == after['key'],
            state.document_preservation(before['files'], after['files'], old_entries, new_entries)['preserved'])


def retain_private_recovery():
    # These private bytes are needed to reconcile an interrupted disposable cell.
    # Never place this archive or its contents in a public issue or PR.
    with tarfile.open(OUTPUT / 'private-recovery.tar.gz', 'x:gz') as archive:
        for path in (Path('/etc/carlos-emr'), Path('/var/log/carlos-emr'), Path('/var/lib/dpkg/status')):
            if path.exists(): archive.add(path, arcname=path.as_posix().lstrip('/'))
    run(['journalctl', '--no-pager', '-u', 'carlos-emr.service', '-u', 'carlos-emr-render-browser.service'],
        'private-service-journal', timeout=60)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--context', type=Path, required=True)
    args = parser.parse_args(); context = gate.read_json(args.context)
    if os.geteuid() != 0 or OUTPUT.is_symlink() or OUTPUT.stat().st_mode & 0o077:
        raise EvidenceError('Cell requires a private owned disposable root')
    assertions = dict.fromkeys(ASSERTIONS, False)
    details = {'schemaVersion': 1, 'role': context['role'], 'profile': context['profile'],
               'source': context['source'], 'warSha256': context['war']['sha256'], 'failureType': None}
    try:
        release = dict(line.split('=', 1) for line in Path('/etc/os-release').read_text().splitlines() if '=' in line)
        if (release.get('ID') != 'ubuntu' or release.get('VERSION_ID', '').strip('"') != '26.04'
                or Path('/proc/1/comm').read_text().strip() != 'systemd'
                or subprocess.check_output(['dpkg-query', '-W', '-f=${db:Status-Status}', 'carlos-emr'],
                        stderr=subprocess.DEVNULL, text=True).strip() == 'installed'):
            raise EvidenceError('Expected a fresh Ubuntu 26.04 systemd cell')
    except subprocess.CalledProcessError:
        # A missing package is the required fresh state; continue below after
        # checking OS/init independently instead of treating every probe error so.
        if release.get('ID') != 'ubuntu' or release.get('VERSION_ID', '').strip('"') != '26.04' or Path('/proc/1/comm').read_text().strip() != 'systemd':
            raise EvidenceError('Expected Ubuntu 26.04 with systemd PID 1')
    assertions['ubuntu-systemd-prerequisite'] = True
    try:
        Path('/usr/sbin/policy-rc.d').unlink(missing_ok=True)
        Path('/etc/apt/apt.conf.d/98carlos-validation-ca').write_text(
            'Acquire::https::CaInfo "/session-ca.pem";\nAcquire::http::Pipeline-Depth "0";\nAcquire::https::Pipeline-Depth "0";\n')
        selections = OUTPUT / 'private-preseed.txt'
        selections.write_text(''.join('carlos-emr carlos-emr/' + key + ' ' + value + '\n' for key, value in {
            'server-name': 'string localhost', 'bind-ip': 'string 0.0.0.0', 'province': 'select ' + context['profile'].lower(),
            'tls-mode': 'select selfsigned', 'acme-email': 'string', 'java-heap': 'string 2g',
            'reset-seed-admin': 'boolean true', 'install-demo-data': 'boolean true'}.items()))
        with selections.open('rb') as stream:
            subprocess.run(['debconf-set-selections'], stdin=stream, check=True, timeout=20)
        run(['apt-get', 'update'], 'apt-update', timeout=300)
        initial = context['target'] if context['role'] == 'clean' else context['baseline']
        for name in ('emr', 'drugref', 'ctl'):
            if gate.digest(Path(initial[name]['path'])) != initial[name]['sha256']:
                raise EvidenceError('Initial package differs from its pin')
        run(['apt-get', 'install', '-y', '--no-remove', *[initial[n]['path'] for n in ('ctl', 'emr', 'drugref')]],
            'initial-install', environment={**os.environ, 'DEBIAN_FRONTEND': 'noninteractive'})
        if Path('/var/lib/carlos-emr/.install-incomplete').exists():
            raise EvidenceError('Installation exited with incomplete provisioning')
        details['initialDeployment'] = attest_package(Path(initial['emr']['path']), initial['emr']['sha256'],
                                                      base_url='https://127.0.0.1/carlos')
        assertions['pinned-installation'] = True
        # Documented username-lock prerequisite, set before the first browser login.
        properties = Path('/etc/carlos-emr/carlos.properties'); text = properties.read_text()
        text = re.sub(r'^login_lock[^\n]*$', 'login_lock=true', text, flags=re.M) if re.search(r'^login_lock', text, re.M) else text + '\nlogin_lock=true\n'
        properties.write_text(text)
        owner = pwd.getpwnam('carlos')
        for relative in ('carlos/eform/images', 'eform/images'):
            directory = Path('/var/lib/carlos-emr/CarlosDocument') / relative
            directory.mkdir(parents=True, exist_ok=True)
            signature = directory / 'consult_sig_999998.png'
            content = (gate.ROOT / 'release/4422-84v9-1.png').read_bytes()
            if signature.exists() or signature.is_symlink():
                if signature.is_symlink() or signature.read_bytes() != content:
                    raise EvidenceError('Pre-existing signature differs; refusing overwrite')
            else:
                with signature.open('xb') as stream: stream.write(content)
                os.chown(signature, owner.pw_uid, owner.pw_gid); signature.chmod(0o640)
        run(['carlos-ctl', 'restart'], 'restart-before-first-login', timeout=420)
        environment = first_login(context['profile'], '/browser/chrome')
        assertions['forced-first-login'] = True
        set_expected_build(environment, package_contents(Path(initial['emr']['path']), initial['emr']['sha256']))
        run(['node', 'scripts/vascular-dictionary-initialize.js'], 'dictionary-initialize', environment=environment, timeout=180)
        smoke(environment, 'client-before'); assertions['unchanged-client-before'] = True
        clinical, documents = transition(context, environment)
        assertions['real-transition'] = True
        assertions['clinical-cleanup-preserved'] = clinical
        assertions['documents-cleanup-preserved'] = documents
        set_expected_build(environment, package_contents(Path(context['target']['emr']['path']), context['target']['emr']['sha256']))
        smoke(environment, 'client-after'); assertions['unchanged-client-after'] = True
        details['candidateDeployment'] = attest(Path(context['war']['path']), base_url=environment['BASE_URL'])
        assertions['candidate-application-bound'] = True
        run(['carlos-ctl', 'check'], 'final-health', timeout=180)
        assertions['service-health'] = True
    except Exception as failure:
        details['failureType'] = type(failure).__name__
        if isinstance(failure, EvidenceError): details['failureReason'] = str(failure)
    finally:
        try:
            retain_private_recovery(); assertions['private-recovery-retained'] = True
        except Exception as failure:
            details['recoveryFailureType'] = type(failure).__name__
        details['assertions'] = assertions
        gate.write_json(OUTPUT / 'cell.json', details)
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
