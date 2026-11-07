#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Exercise the shipped backup service and restore actual SQL/files in an owned cell.

This core does not qualify retention, interruption or terminal purge. Those
remain distinct required acceptance until their concrete phases are implemented.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import shutil
import signal
import stat
import subprocess
import tarfile
import uuid

import defusedxml.ElementTree as ET
import installed_package_state as state
import release_verification as gate
from junit_evidence import EvidenceError
from process_group import run_process_group
from release_deployment import attest, local_target

REPOSITORY = Path('/var/backups/carlos-emr/restic-repo')
BACKUP_ENV = Path('/etc/carlos-emr/backup.env')
SCRATCH = 'carlos_restore_drill'
IMPORT_WORKSPACE = Path('/var/lib/carlos-emr/o19-import')
ASSERTIONS = ('candidate-bound-before-and-after', 'installed-backup-executables-bound', 'local-fresh-repository-no-external-alerts',
    'real-backup-and-drill-passed', 'all-clinical-cells-and-ledgers-restored',
    'document-bytes-and-metadata-restored', 'encryption-key-restored',
    'named-staging-and-credential-exclusions', 'allowed-import-snapshots-and-ledgers-restored',
    'operational-config-bytes-and-metadata-restored',
    'repository-encryption-and-application-read-denials', 'no-plaintext-canary-in-repository', 'known-missing-repository-refused',
    'clinical-data-preserved', 'owned-fixtures-cleaned', 'private-recovery-escrow-retained')


def installed_profile(expected, properties=Path('/etc/carlos-emr/carlos.properties')):
    file_state(properties)
    values = re.findall(r'^\s*billregion\s*[=:]\s*([^\r\n]*)$', properties.read_text(), re.M)
    if expected not in ('ON', 'BC') or values != [expected]:
        raise EvidenceError('Installed billing region does not uniquely match the selected province')
    return expected


def run(command, log, *, timeout=420, check=True, stdin=None):
    with log.open('xb') as stream:
        os.chmod(log, 0o600)
        result = run_process_group(command, stdout=stream, stderr=stream, stdin=stdin,
                                   timeout=timeout, grace=30)
    if check and result.returncode:
        raise EvidenceError('Installed backup phase failed: ' + log.stem)
    return result.returncode


def restic_command(*arguments, wrong_password=False):
    # Source the application's known private file normally; do not dump its
    # environment or put a real credential on a command line.
    script = 'set -ae; . /etc/carlos-emr/backup.env; '
    if wrong_password: script += 'RESTIC_PASSWORD=FAKE-WRONG-QUALIFICATION-PASSWORD; export RESTIC_PASSWORD; '
    return ['bash', '-c', script + 'exec restic "$@"', 'restic-qualification', *arguments]


def table_ddl(qualified, table):
    result = subprocess.run(['mariadb', '--no-defaults', '--protocol=socket',
        '--socket=/run/mysqld/mysqld.sock', '--user=root', '--database=carlos', '--xml', '--show-warnings',
        '--default-character-set=utf8mb4', '-e', 'SHOW CREATE TABLE ' + qualified],
        capture_output=True, text=True, timeout=60)
    if result.returncode or result.stderr.strip():
        raise EvidenceError('Backup table DDL query failed')
    try:
        root = ET.fromstring(result.stdout, forbid_dtd=True)
    except (ET.ParseError, ValueError) as error:
        raise EvidenceError('Backup table DDL has incomplete XML or SQL warnings') from error
    rows = root.findall('row')
    if root.tag != 'resultset' or len(rows) != 1:
        raise EvidenceError('Backup table DDL row is missing or ambiguous')
    fields = rows[0].findall('field')
    values = {field.get('name'): field.text for field in fields}
    if (len(fields) != 2 or set(values) != {'Table', 'Create Table'} or values['Table'] != table
            or not isinstance(values['Create Table'], str) or not values['Create Table'].startswith('CREATE TABLE ')):
        raise EvidenceError('Backup table DDL is incomplete')
    return values['Create Table']


def restore_state(schema, tables):
    result = {}
    for table in tables:
        qualified = state.quote_identifier(schema) + '.' + state.quote_identifier(table)
        columns = [line.split('\t')[:2] for line in state.query('SHOW COLUMNS FROM ' + qualified)]
        if not columns or len({row[0] for row in columns}) != len(columns):
            raise EvidenceError('Backup clinical table columns are incomplete')
        rows = state.validated_rows(state.query('SELECT JSON_ARRAY(' + ','.join(state.cell_encoding(name, kind)
            for name, kind in columns) + ') FROM ' + qualified), len(columns))
        ddl_text = table_ddl(qualified, table)
        result[table] = {'columns': columns, 'rows': len(rows),
            'ddlSha256': hashlib.sha256(ddl_text.encode()).hexdigest(), 'sha256': hashlib.sha256(
            __import__('json').dumps(rows, ensure_ascii=True).encode()).hexdigest()}
    return result


def key_hash(path):
    import re
    values = re.findall(r'^encryption\.util\.secret\.key\s*[=:]\s*(.+)$', path.read_text(), re.M)
    if len(values) != 1 or not values[0]: raise EvidenceError('Restored encryption key is absent or ambiguous')
    return hashlib.sha256(values[0].encode()).hexdigest()


def contains_plaintext(path, marker):
    tail = b''
    with path.open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            combined = tail + chunk
            if marker in combined:
                return True
            tail = combined[-(len(marker) - 1):] if len(marker) > 1 else b''
    return False


def file_state(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode): raise EvidenceError('Backup file canary is not regular')
        os.set_blocking(stream.fileno(), True)
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if state.identity(info) != state.identity(os.fstat(stream.fileno())) or state.identity(info) != state.identity(path.lstat()):
            raise EvidenceError('Backup file changed during observation')
    return [info.st_size, stat.S_IMODE(info.st_mode), info.st_uid, info.st_gid, digest]


def configuration_state(base=Path('/etc/carlos-emr')):
    if base.is_symlink() or not base.is_dir(): raise EvidenceError('Backup configuration root is unavailable')
    result = {}
    def unreadable(_error): raise EvidenceError('Backup configuration inventory is unreadable')
    for directory, subdirectories, files in os.walk(base, followlinks=False, onerror=unreadable):
        parent = Path(directory); info = parent.lstat()
        if not stat.S_ISDIR(info.st_mode): raise EvidenceError('Configuration directory changed type')
        result['directory:' + parent.relative_to(base).as_posix()] = [stat.S_IMODE(info.st_mode), info.st_uid, info.st_gid]
        for name in list(subdirectories):
            path = parent / name
            if path == base / 'tls': subdirectories.remove(name); continue
            if path.is_symlink():
                result['link:' + path.relative_to(base).as_posix()] = [os.readlink(path), path.lstat().st_uid, path.lstat().st_gid]
                subdirectories.remove(name)
        for name in files:
            path = parent / name
            if path == base / 'initial-admin.txt': continue
            if path.is_symlink():
                result['link:' + path.relative_to(base).as_posix()] = [os.readlink(path), path.lstat().st_uid, path.lstat().st_gid]
            else: result['file:' + path.relative_to(base).as_posix()] = file_state(path)
    return result


def seed_named_canaries(namespace, payload, records, directories):
    def ensure_directory(path):
        if any(parent.is_symlink() for parent in (path, *path.parents)):
            raise EvidenceError('Named backup canary parent contains a link')
        if path.exists():
            if not path.is_dir(): raise EvidenceError('Named canary parent is not a directory')
            return
        ensure_directory(path.parent)
        path.mkdir(mode=0o755); path.chmod(0o755)
        info = path.lstat(); directories.append({'path': str(path), 'identity': [info.st_dev, info.st_ino]})
    def seed(label, path, excluded, existing=False):
        ensure_directory(path.parent)
        row = {'id': label, 'path': str(path), 'excluded': excluded, 'created': False, 'state': None, 'identity': None}
        records.append(row)
        if path.is_symlink(): raise EvidenceError('Named backup canary is a link')
        if path.exists():
            if not existing: raise EvidenceError('Named backup canary path was already occupied')
        else:
            with path.open('xb') as stream:
                row['created'] = True; info = os.fstat(stream.fileno()); row['identity'] = [info.st_dev, info.st_ino]
                stream.write(payload)
            path.chmod(0o644)
        row['state'] = file_state(path)
    nested = IMPORT_WORKSPACE / namespace
    # Synthetic backup-exclusion canary labels and filenames, never authentication values.
    for label, name in (('admin', 'admin-credentials.txt'), ('derived', 'o19-derived-carlos.properties'), ('source', 'source.properties')):  # ggignore
        seed(label + '-base', IMPORT_WORKSPACE / name, True, True)
        seed(label + '-suffix', IMPORT_WORKSPACE / (name + '.' + namespace), True)
        seed(label + '-nested', nested / name, True)
    seed('oscar-base', IMPORT_WORKSPACE / 'oscar.properties', True, True)
    seed('oscar-nested', nested / 'oscar.properties', True)
    for name in ('bundle', 'bundle-assess', 'bundle-sources', 'bundle-assess-sources'):
        seed(name, IMPORT_WORKSPACE / name / (namespace + '.bin'), True)
    seed('lock', IMPORT_WORKSPACE / '.lock', True, True)
    seed('tls', Path('/etc/carlos-emr/tls') / (namespace + '.bin'), True)
    seed('bootstrap', Path('/etc/carlos-emr/initial-admin.txt'), True, True)
    seed('allowed-source', nested / 'immutable-source-snapshot.bin', False)
    seed('allowed-ledger', nested / 'migration-ledger.bin', False)
    if len(records) != 20 or len({row['path'] for row in records}) != 20:
        raise EvidenceError('Named backup fixture inventory is incomplete')


def verify_named_canaries(records, destination):
    excluded = [row for row in records if row['excluded']]
    allowed = [row for row in records if not row['excluded']]
    if len(excluded) != 18 or len(allowed) != 2:
        raise EvidenceError('Named backup fixture inventory omitted an exclusion or allowed-data control')
    missing = all(not (destination / row['path'].lstrip('/')).exists()
        and not (destination / row['path'].lstrip('/')).is_symlink() for row in excluded)
    retained = all(file_state(destination / row['path'].lstrip('/')) == row['state'] for row in allowed)
    return missing, retained


def cleanup_named_canaries(records, directories):
    for row in reversed(records):
        path = Path(row['path'])
        if row['state'] is None or file_state(path) != row['state']:
            raise EvidenceError('Named canary setup is incomplete or its bytes/metadata changed')
        if row['created']:
            info = path.lstat()
            if [info.st_dev, info.st_ino] != row['identity']:
                raise EvidenceError('Owned named canary was replaced')
            path.unlink()
    for row in reversed(directories):
        path = Path(row['path']); info = path.lstat()
        if not stat.S_ISDIR(info.st_mode) or [info.st_dev, info.st_ino] != row['identity']:
            raise EvidenceError('Owned named canary directory was replaced')
        path.rmdir()


def snapshots(output, name):
    path = output / name
    run(restic_command('snapshots', '--json'), path)
    value = gate.read_json(path)
    if not isinstance(value, list) or not value:
        raise EvidenceError('Backup repository has no actual snapshot inventory')
    return value


def marker_state():
    result = {}
    for raw in ('/var/backups/carlos-emr/.repo-known', '/var/backups/carlos-emr/.last-success',
                '/var/log/carlos-emr/backup-state/.repo-known'):
        path = Path(raw)
        result[raw] = [file_state(path), path.stat().st_mtime_ns] if path.exists() else None
    return result


def require_unlinked_paths(paths):
    for path in paths:
        if any(parent.is_symlink() for parent in (path, *path.parents)):
            raise EvidenceError('Backup qualification refuses linked operational paths')


def refusal_message(records):
    subject = 'backup repository is unreachable or damaged — NOT re-initialising'
    return any(isinstance(row, dict) and isinstance(row.get('MESSAGE'), str)
        and subject in row['MESSAGE'] for row in records)


def exercise_missing_repository(output):
    unit = 'carlos-emr-backup.service'
    if not all(worker_quiescent(name) for name in (unit, 'carlos-emr-backup-verify.service')):
        raise EvidenceError('Missing-repository probe requires quiescent backup workers')
    require_unlinked_paths([REPOSITORY])
    before = snapshots(output, 'private-refusal-before.json'); markers = marker_state()
    if any(value is None for value in markers.values()):
        raise EvidenceError('Missing-repository probe requires the successful backup markers')
    run(['journalctl', '--sync'], output / 'private-refusal-journal-sync.log', timeout=30)
    run(['journalctl', '--no-pager', '-n', '1', '-o', 'json', '-u', unit],
        output / 'private-refusal-cursor.json', timeout=30)
    cursor = gate.read_json(output / 'private-refusal-cursor.json').get('__CURSOR')
    if not isinstance(cursor, str) or not cursor: raise EvidenceError('Backup journal cursor is unavailable')
    original = REPOSITORY.lstat(); moved = REPOSITORY.with_name('FAKE5700-repository-' + uuid.uuid4().hex)
    if moved.exists() or moved.is_symlink(): raise EvidenceError('Repository probe destination is occupied')
    details = {'schemaVersion': 1, 'passed': False, 'repositoryRestored': False}
    try:
        REPOSITORY.rename(moved)
        code = run(['systemctl', 'start', unit], output / 'private-refusal-start.log', timeout=120, check=False)
        details['exitCode'] = code
        run(['journalctl', '--sync'], output / 'private-refusal-journal-sync-after.log', timeout=30)
        run(['journalctl', '--no-pager', '-o', 'json', '--after-cursor=' + cursor, '-u', unit],
            output / 'private-refusal-journal.jsonl', timeout=30)
        records = [json.loads(line) for line in (output / 'private-refusal-journal.jsonl').read_text().splitlines()]
        details['specificRefusalLogged'] = refusal_message(records)
        details['replacementRepositoryAbsent'] = not REPOSITORY.exists() and not REPOSITORY.is_symlink()
    finally:
        if not stop_workers(): raise EvidenceError('Repository probe workers did not become quiescent')
        if moved.exists():
            info = moved.lstat()
            if (moved.is_symlink() or [info.st_dev, info.st_ino] != [original.st_dev, original.st_ino]
                    or REPOSITORY.exists() or REPOSITORY.is_symlink()):
                gate.write_json(output / 'repository-refusal.json', details)
                raise EvidenceError('Repository probe needs reconciliation; original history is retained')
            moved.rename(REPOSITORY)
        current = REPOSITORY.lstat()
        details['repositoryRestored'] = [current.st_dev, current.st_ino] == [original.st_dev, original.st_ino]
    after = snapshots(output, 'private-refusal-after.json')
    details['snapshotInventoryUnchanged'] = before == after
    details['markersUnchanged'] = markers == marker_state()
    details['passed'] = (details.get('exitCode') != 0 and details.get('specificRefusalLogged') is True
        and details.get('replacementRepositoryAbsent') is True and details['repositoryRestored']
        and details['snapshotInventoryUnchanged'] and details['markersUnchanged'])
    gate.write_json(output / 'repository-refusal.json', details)
    run(['systemctl', 'reset-failed', unit], output / 'private-refusal-reset.log', timeout=30)
    return details['passed']


def timers(states):
    for unit in ('carlos-emr-backup.timer', 'carlos-emr-backup-verify.timer'):
        active = subprocess.check_output(['systemctl', 'show', '--value', '-p', 'ActiveState', unit], text=True, timeout=20).strip()
        if active not in ('active', 'inactive'):
            raise EvidenceError('Backup timer state is not stable')
        states[unit] = active == 'active'
        subprocess.run(['systemctl', 'stop', unit], check=True, timeout=30)
    return states


def worker_quiescent(unit):
    if unit not in ('carlos-emr-backup.service', 'carlos-emr-backup-verify.service'): return False
    properties = subprocess.check_output(['systemctl', 'show', unit, '-p', 'ActiveState', '-p', 'MainPID',
        '-p', 'ControlPID', '-p', 'ControlGroup'], text=True, timeout=20)
    rows = [line.split('=', 1) for line in properties.splitlines()]
    if len(rows) != 4 or any(len(row) != 2 for row in rows):
        return False
    values = dict(rows)
    if (set(values) != {'ActiveState', 'MainPID', 'ControlPID', 'ControlGroup'}
            or values['ActiveState'] not in ('inactive', 'failed')
            or values['MainPID'] != '0' or values['ControlPID'] != '0'
            or values['ControlGroup'] not in ('', '/system.slice/' + unit)):
        return False
    group = Path('/sys/fs/cgroup/system.slice') / unit
    if group.is_symlink(): return False
    if not group.exists(): return True
    if not (group / 'cgroup.procs').is_file(): return False
    for membership in group.rglob('cgroup.procs'):
        if membership.is_symlink(): return False
        for raw in membership.read_text().split():
            if not raw.isdecimal() or int(raw) <= 0: return False
            try:
                fields = (Path('/proc') / raw / 'stat').read_text().rsplit(')', 1)[1].split()
            except FileNotFoundError:
                continue
            if len(fields) < 20 or fields[0] not in ('Z', 'X'): return False
    return True


def bind_backup_executables():
    pairs = [('/usr/lib/carlos-emr/carlos-emr-backup', 'debian/assets/bin/carlos-emr-backup')]
    for unit in ('carlos-emr-backup.service', 'carlos-emr-backup-verify.service'):
        fragment = subprocess.check_output(['systemctl', 'show', '--value', '-p', 'FragmentPath', unit], text=True, timeout=20).strip()
        dropins = subprocess.check_output(['systemctl', 'show', '--value', '-p', 'DropInPaths', unit], text=True, timeout=20).strip()
        if dropins or fragment not in ('/usr/lib/systemd/system/' + unit, '/lib/systemd/system/' + unit):
            raise EvidenceError('Backup service has unreviewed effective configuration')
        pairs.append((fragment, 'debian/carlos-emr.' + unit))
    if any(gate.digest(Path(installed)) != gate.digest(gate.ROOT / source) for installed, source in pairs):
        raise EvidenceError('Installed backup executable or unit differs from committed source')
    return True


def stop_workers():
    stopped = True
    for unit in ('carlos-emr-backup.service', 'carlos-emr-backup-verify.service'):
        try:
            result = subprocess.run(['systemctl', 'stop', unit], capture_output=True, timeout=120)
            stopped &= result.returncode == 0 and worker_quiescent(unit)
        except (subprocess.SubprocessError, OSError):
            stopped = False
    return stopped

def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--profile', choices=('ON', 'BC'), required=True)
    args = parser.parse_args(); output = args.output.parent
    if (os.geteuid() != 0 or os.environ.get('CARLOS_DISPOSABLE_HOST') != 'true'
            or os.environ.get('EXCLUSIVE') != '1' or not local_target(os.environ.get('BASE_URL', ''))
            or output.is_symlink() or any(parent.is_symlink() for parent in output.parents)
            or not output.is_dir() or output.stat().st_mode & 0o077 or args.output.exists()
            or args.output.is_symlink() or output.resolve().is_relative_to(gate.ROOT)):
        raise EvidenceError('Backup requires a private exclusive disposable loopback installation')
    if any(shutil.which(name) is None for name in ('restic', 'mariadb', 'systemctl', 'runuser')):
        raise EvidenceError('Named installed backup tools are unavailable')
    require_unlinked_paths([REPOSITORY, BACKUP_ENV, IMPORT_WORKSPACE,
        Path('/var/lib/carlos-emr/CarlosDocument'), Path('/var/log/carlos-emr/backup-state')])
    installed_profile(args.profile)
    assertions = dict.fromkeys(ASSERTIONS, False)
    details = {'schemaVersion': 1, 'failureType': None, 'profile': args.profile, 'assertions': assertions}
    table = 'FAKE5700_backup_' + uuid.uuid4().hex
    canary = Path('/var/lib/carlos-emr/CarlosDocument/carlos/document') / (table + '.bin')
    marker_text = table + ' caf\u00e9 \u03a9'
    payload = (marker_text + '\n').encode() + bytes(range(256))
    details.update(ownedFixtureTable=table, ownedFixtureDocument=str(canary),
        ownedFixtureMarkerSha256=hashlib.sha256(marker_text.encode()).hexdigest())
    made_table = made_file = drill_attempted = mutation_started = workers_owned = False
    timer_states = {}; expected_repository_identity = None; backup_attempted = False
    named_files = []; named_directories = []; named_attempted = named_complete = False
    try:
        details['beforeDeployment'] = attest(args.artifact, base_url=os.environ['BASE_URL'])
        assertions['installed-backup-executables-bound'] = bind_backup_executables()
        baseline_tables = (*state.TABLES, 'flyway_schema_history')
        clinical_before = restore_state('carlos', baseline_tables)
        files_before = state.stored_files()
        key_before = state.encryption_key()
        for path in ('carlos.properties', 'backup.env', 'carlos-emr.env'):
            file_state(Path('/etc/carlos-emr') / path)
        configuration_before = configuration_state()
        gate.write_json(output / 'private-baseline.json', {'clinical': clinical_before,
            'files': files_before, 'keySha256': key_before, 'configuration': configuration_before})
        if (REPOSITORY.is_symlink() or (REPOSITORY.exists() and
                (not REPOSITORY.is_dir() or any(REPOSITORY.iterdir())))):
            raise EvidenceError('Backup qualification will not prune or alter an existing repository')
        preflight = ['bash', '-c', 'set -ae; . /etc/carlos-emr/backup.env; '
            'test "$RESTIC_REPOSITORY" = /var/backups/carlos-emr/restic-repo && '
            'test -z "${CARLOS_BACKUP_ALERT_WEBHOOK:-}" && test -z "${CARLOS_BACKUP_ALERT_EMAIL:-}"']
        run(preflight, output / 'private-preflight.log', timeout=20)
        if state.query("SELECT table_name FROM information_schema.tables WHERE table_schema='" + SCRATCH + "'"):
            raise EvidenceError('Restore scratch database is not empty; refusing to remove prior state')
        scratch_ddl = state.query('SHOW CREATE DATABASE ' + state.quote_identifier(SCRATCH))
        if len(scratch_ddl) != 1 or '\tCREATE DATABASE ' not in scratch_ddl[0]:
            raise EvidenceError('Installer-provisioned restore database is missing')
        scratch_create = scratch_ddl[0].split('\t', 1)[1]
        for catalog, column in (('routines', 'routine_schema'), ('events', 'event_schema')):
            if state.query('SELECT 1 FROM information_schema.' + catalog + ' WHERE ' + column + "='" + SCRATCH + "'"):
                raise EvidenceError('Restore scratch database has pre-existing routines or events')
        mutation_started = True
        timers(timer_states)
        for unit in ('carlos-emr-backup.service', 'carlos-emr-backup-verify.service'):
            if not worker_quiescent(unit):
                raise EvidenceError('Backup worker was already active before qualification')
        workers_owned = True
        assertions['local-fresh-repository-no-external-alerts'] = True
        state.query('CREATE TABLE ' + state.quote_identifier(table)
            + ' (id INT PRIMARY KEY, marker VARCHAR(128) CHARACTER SET utf8mb4, bytes LONGBLOB, '
              'fraction DECIMAL(65,30), missing_value TEXT NULL) ENGINE=InnoDB')
        made_table = True
        state.query('INSERT INTO ' + state.quote_identifier(table) + " VALUES (1,CONVERT(X'"
            + marker_text.encode().hex() + "' USING utf8mb4),X'" + payload.hex()
            + "',12345678901234567890.000000000000000000000000000001,NULL)")
        owner = pwd.getpwnam('carlos')
        with canary.open('xb') as stream:
            made_file = True
            info = os.fstat(stream.fileno()); owned_identity = (info.st_dev, info.st_ino)
            stream.write(payload)
        os.chown(canary, owner.pw_uid, owner.pw_gid); canary.chmod(0o640)
        named_attempted = True
        seed_named_canaries(table, payload, named_files, named_directories)
        named_complete = True
        gate.write_json(output / 'private-named-canaries.json', {'files': named_files, 'directories': named_directories})
        expected_files = state.stored_files()
        expected = restore_state('carlos', (*baseline_tables, table))
        backup_attempted = True
        run(['systemctl', 'start', 'carlos-emr-backup.service'], output / 'private-backup-start.log', timeout=900)
        repository_info = REPOSITORY.lstat()
        expected_repository_identity = [repository_info.st_dev, repository_info.st_ino]
        drill_attempted = True
        run(['systemctl', 'start', 'carlos-emr-backup-verify.service'], output / 'private-drill-start.log', timeout=900)
        assertions['real-backup-and-drill-passed'] = True
        restored = restore_state(SCRATCH, (*baseline_tables, table))
        assertions['all-clinical-cells-and-ledgers-restored'] = expected == restored
        gate.write_json(output / 'private-sql-restore.json', {'before': expected, 'restored': restored})
        host = subprocess.check_output(['hostname', '-s'], text=True, timeout=10).strip() + '-carlos'
        destination = output / 'private-files-restore'
        run(restic_command('restore', 'latest', '--tag', 'files', '--host', host, '--target', str(destination)),
            output / 'private-files-restore.log', timeout=900)
        restored_store = destination / 'var/lib/carlos-emr/CarlosDocument'
        actual_files = state.stored_files(restored_store)
        assertions['document-bytes-and-metadata-restored'] = expected_files == actual_files
        assertions['encryption-key-restored'] = key_before == key_hash(destination / 'etc/carlos-emr/carlos.properties')
        excluded, allowed = verify_named_canaries(named_files, destination)
        assertions['named-staging-and-credential-exclusions'] = excluded
        assertions['allowed-import-snapshots-and-ledgers-restored'] = allowed
        configuration_restored = configuration_state(destination / 'etc/carlos-emr')
        assertions['operational-config-bytes-and-metadata-restored'] = configuration_before == configuration_restored
        gate.write_json(output / 'private-configuration-restore.json', {'before': configuration_before, 'restored': configuration_restored})
        gate.write_json(output / 'private-file-restore.json', {'before': expected_files, 'restored': actual_files})
        # A successfully opened repository and a specifically rejected wrong
        # password distinguish encryption from an unrelated path/tool failure.
        run(restic_command('cat', 'config'), output / 'private-repository-config.json')
        wrong = run(restic_command('cat', 'config', wrong_password=True),
                    output / 'private-wrong-password.log', check=False)
        wrong_text = (output / 'private-wrong-password.log').read_text().lower()
        readable = subprocess.run(['runuser', '-u', 'carlos', '--', 'test', '-r', str(canary)], timeout=20).returncode == 0
        denied = readable and all(subprocess.run(['runuser', '-u', 'carlos', '--', 'test', '-r', str(path)],
                     timeout=20).returncode == 1 for path in (BACKUP_ENV, REPOSITORY / 'config'))
        assertions['repository-encryption-and-application-read-denials'] = (
            wrong != 0 and ('wrong password' in wrong_text or 'no key found' in wrong_text) and denied)
        repository_files = [path for path in REPOSITORY.rglob('*') if path.is_file()]
        if not repository_files or any(path.is_symlink() for path in REPOSITORY.rglob('*')):
            raise EvidenceError('Repository has no complete regular encrypted file inventory')
        marker_bytes = marker_text.encode()
        assertions['no-plaintext-canary-in-repository'] = all(not contains_plaintext(path, marker_bytes) for path in repository_files)
        assertions['known-missing-repository-refused'] = exercise_missing_repository(output)
        run(['systemctl', 'start', 'carlos-emr-backup.service'], output / 'private-backup-recovery.log', timeout=900)
        assertions['clinical-data-preserved'] = expected == restore_state('carlos', (*baseline_tables, table))
        # All recovery bytes remain private; public receipts contain only hashes.
        with tarfile.open(output / 'private-backup-recovery.tar.gz', 'x:gz') as archive:
            archive.add(REPOSITORY, arcname='repository')
            archive.add(BACKUP_ENV, arcname='configuration/backup.env')
            archive.add('/etc/carlos-emr/carlos.properties', arcname='configuration/carlos.properties')
        assertions['private-recovery-escrow-retained'] = True
    except (Exception, KeyboardInterrupt) as failure:
        details['failureType'] = type(failure).__name__
        if isinstance(failure, EvidenceError): details['failureReason'] = str(failure)
    finally:
        cleanup_errors = []
        workers_stopped = not workers_owned or stop_workers()
        details['ownedWorkersQuiescent'] = workers_stopped
        details['mutationAttempted'] = mutation_started
        if not workers_stopped: cleanup_errors.append('ServiceQuiescenceUnverified')
        if named_attempted and not named_complete: cleanup_errors.append('NamedCanarySetupIncomplete')
        if workers_stopped and named_complete:
            try: cleanup_named_canaries(named_files, named_directories)
            except Exception as failure: cleanup_errors.append(type(failure).__name__)
        if backup_attempted:
            try:
                info = REPOSITORY.lstat()
                if (expected_repository_identity is None or REPOSITORY.is_symlink()
                        or [info.st_dev, info.st_ino] != expected_repository_identity):
                    raise EvidenceError('Original backup repository identity was not reconciled')
            except Exception as failure: cleanup_errors.append(type(failure).__name__)
        if workers_stopped and made_file:
            try:
                if (canary.is_symlink() or (canary.stat().st_dev, canary.stat().st_ino) != owned_identity
                        or canary.read_bytes() != payload): raise EvidenceError('Owned document replaced')
                canary.unlink()
            except Exception as failure: cleanup_errors.append(type(failure).__name__)
        if workers_stopped and made_table:
            try:
                # A random table name plus its exact marker row is the ownership predicate.
                rows = state.query('SELECT HEX(marker) FROM ' + state.quote_identifier(table))
                if rows != [marker_text.encode().hex().upper()]: raise EvidenceError('Owned SQL fixture replaced')
                state.query('DROP TABLE ' + state.quote_identifier(table))
            except Exception as failure: cleanup_errors.append(type(failure).__name__)
        if workers_stopped and drill_attempted:
            try:
                state.query('DROP DATABASE ' + state.quote_identifier(SCRATCH) + '; ' + scratch_create)
                if state.query('SHOW CREATE DATABASE ' + state.quote_identifier(SCRATCH)) != scratch_ddl:
                    raise EvidenceError('Restore scratch schema metadata was not preserved')
            except Exception as failure: cleanup_errors.append(type(failure).__name__)
        for unit, active in timer_states.items():
            if workers_stopped:
                try:
                    if active: subprocess.run(['systemctl', 'start', unit], check=True, timeout=30)
                    actual = subprocess.check_output(['systemctl', 'show', '--value', '-p', 'ActiveState', unit], text=True, timeout=20).strip()
                    if actual != ('active' if active else 'inactive'):
                        raise EvidenceError('Backup timer state was not restored')
                except Exception as failure: cleanup_errors.append(type(failure).__name__)
        try:
            assertions['owned-fixtures-cleaned'] = (mutation_started and not cleanup_errors and not canary.exists()
                and not state.query("SELECT table_name FROM information_schema.tables WHERE table_schema='carlos' AND table_name='" + table + "'")
                and not state.query("SELECT table_name FROM information_schema.tables WHERE table_schema='" + SCRATCH + "'")
                and clinical_before == restore_state('carlos', baseline_tables)
                and files_before == state.stored_files() and key_before == state.encryption_key()
                and configuration_before == configuration_state()
                and (not named_attempted or named_complete))
            details['afterDeployment'] = attest(args.artifact, base_url=os.environ['BASE_URL'])
            assertions['candidate-bound-before-and-after'] = details['beforeDeployment'] == details['afterDeployment']
        except Exception as failure: cleanup_errors.append(type(failure).__name__)
        details['cleanupFailureTypes'] = cleanup_errors
        details['restorationVerified'] = assertions['owned-fixtures-cleaned'] and assertions['candidate-bound-before-and-after']
        details['hostRequiresReconciliation'] = not details['restorationVerified']
        gate.write_json(output / 'backup-core-details.json', details)
    inputs = [{'path': str(path.relative_to(output)), 'sha256': gate.digest(path)}
        for path in sorted(output.rglob('*')) if path.is_file() and not path.is_symlink() and path != args.output]
    if any(path.is_symlink() for path in output.rglob('*')):
        raise EvidenceError('Backup evidence contains an unbound link')
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': assertions, 'inputs': inputs})
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    def interrupted(_number, _frame):
        raise KeyboardInterrupt
    previous = signal.signal(signal.SIGTERM, interrupted)
    try:
        raise SystemExit(main())
    finally:
        signal.signal(signal.SIGTERM, previous)
