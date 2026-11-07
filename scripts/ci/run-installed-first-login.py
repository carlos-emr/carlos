#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Prove the installer credential reset once, before any ordinary suite login."""
import argparse
import json
import os
from pathlib import Path
import stat
import subprocess

import release_verification as gate
from junit_evidence import EvidenceError
from process_group import ProcessGroupFailure, run_process_group

ADMIN_FILE = Path('/etc/carlos-emr/initial-admin.txt')
ASSERTIONS = ('installer-reset-required', 'browser-first-login-passed',
              'password-changed-reset-cleared', 'identity-and-pin-preserved', 'locked-chromium')


def credentials_user(path=ADMIN_FILE):
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_mode & 0o077:
        raise EvidenceError('Installer credential file must be a private regular file')
    fields = {}
    for line in path.read_text().splitlines():
        words = line.split()
        if words and words[0] in ('user:', 'password:', 'PIN:'):
            if len(words) != 2 or words[0] in fields:
                raise EvidenceError('Installer credential fields are ambiguous')
            fields[words[0]] = words[1]
    if set(fields) != {'user:', 'password:', 'PIN:'}:
        raise EvidenceError('Installer credential fields are incomplete')
    return fields['user:']


def account(user):
    query = ("SELECT JSON_ARRAY(security_no,user_name,provider_no,password,pin,forcePasswordReset,passwordUpdateDate) "
             "FROM security WHERE user_name=CONVERT(X'" + user.encode().hex() + "' USING utf8mb4)")
    result = subprocess.run(['mariadb', '--no-defaults', '--protocol=socket',
        '--socket=/run/mysqld/mysqld.sock', '--user=root', '--database=carlos',
        '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', '-e', query],
        capture_output=True, text=True, timeout=20)
    # Never include raw SQL results or diagnostics in public evidence.
    if result.returncode or len(result.stdout.splitlines()) != 1:
        raise EvidenceError('Installer account is absent, ambiguous or unreadable')
    row = json.loads(result.stdout)
    if (not isinstance(row, list) or len(row) != 7 or type(row[0]) is not int
            or row[1] != user or not row[3] or not row[4] or type(row[5]) is not int):
        raise EvidenceError('Installer account state is incomplete')
    return row


def restoration(before, after):
    return {
        'password-changed-reset-cleared': bool(after[3] and after[3] != before[3]
                                              and after[5] == 0 and after[6]),
        'identity-and-pin-preserved': before[:3] == after[:3] and before[4] == after[4],
    }


def run_browser(completion):
    return run_process_group(['node', 'scripts/deb-login-playwright-checks.js'], cwd=gate.ROOT,
        env={**os.environ, 'RESULT_JSON': str(completion)}, timeout=300, grace=10,
        track_descendants=True).returncode


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=('ON', 'BC'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    if (os.environ.get('CARLOS_DISPOSABLE_HOST') != 'true'
            or os.environ.get('CARLOS_SUITE_PROVINCE') != args.profile
            or os.environ.get('ADMIN_FILE') != str(ADMIN_FILE)):
        raise EvidenceError('First login requires the named disposable installation and installer file')
    password = os.environ.get('NEW_PASSWORD', '')
    if not password or len(password.encode('utf-16-le')) // 2 > 32:
        raise EvidenceError('Explicit retained reset password must fit the installed form')
    user = credentials_user()
    before = account(user)
    assertions = dict.fromkeys(ASSERTIONS, False)
    assertions['installer-reset-required'] = before[5] == 1
    completion = args.output.parent / 'first-login-completion.json'
    inputs = []
    failure_type = None
    reconciliation = None
    if assertions['installer-reset-required']:
        try:
            exit_code = run_browser(completion)
            if completion.is_file():
                record = gate.read_json(completion)
                assertions['browser-first-login-passed'] = (exit_code == 0
                    and record.get('name') == 'deb-login' and record.get('outcome') == 'PASS'
                    and not record.get('cleanupFailed'))
                gate.require_locked_chromium({record.get('browserVersion', '')})
                assertions['locked-chromium'] = True
                inputs.append(completion)
        except Exception as failure:
            failure_type = type(failure).__name__
            reconciliation = {'restorationVerified': False, 'hostRequiresReconciliation': True}
            if isinstance(failure, ProcessGroupFailure):
                reconciliation['processGroupStopped'] = failure.quiescent
        finally:
            # A browser timeout may follow a committed reset. Retain this outcome
            # and require manual reconciliation; never submit another reset here.
            try:
                assertions.update(restoration(before, account(user)))
            except Exception as failure:
                failure_type = type(failure).__name__
    details = args.output.parent / 'first-login-state.json'
    gate.write_json(details, {'schemaVersion': 1, 'profile': args.profile,
        'checks': assertions, 'failureType': failure_type, 'reconciliation': reconciliation})
    inputs.append(details)
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': assertions, 'inputs': [
        {'path': path.name, 'sha256': gate.digest(path)} for path in inputs]})
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
