# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Independent complete clinical/file snapshots for owned package transitions."""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess

from cds_war_manifest import identity
from junit_evidence import EvidenceError

TABLES = ('demographic', 'appointment', 'prescription', 'drugs', 'allergies',
          'consultationRequests', 'casemgmt_note', 'preventions', 'hl7TextMessage', 'document', 'tickler')

# EFormAssetDeployer explicitly owns this application script and refreshes it
# at startup. This is a concrete stock-to-stock transition, not a glob exclusion.
# Other files (including seeded/customizable templates) remain exact comparisons.
MANAGED_DOCUMENT_ASSETS = {
    'file:carlos/eform/images/editControl2.js': 'WEB-INF/eform-assets/editControl2.js',
}


def query(sql):
    result = subprocess.run(['mariadb', '--no-defaults', '--protocol=socket',
        '--socket=/run/mysqld/mysqld.sock', '--user=root', '--database=carlos',
        '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4', '-e', sql],
        capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise EvidenceError('Independent installed clinical snapshot query failed')
    return result.stdout.splitlines()


def quote_identifier(name):
    return '`' + name.replace('`', '``') + '`'


def cell_encoding(name, column_type):
    value = quote_identifier(name)
    # MariaDB formats FLOAT using too few digits to distinguish adjacent values.
    # Widen it exactly to DOUBLE before string conversion; never widen DECIMAL.
    if column_type.lower().split('(', 1)[0].split()[0] == 'float':
        value = 'CAST(' + value + ' AS DOUBLE)'
    return 'HEX(CAST(' + value + ' AS BINARY))'


def clinical_state(prior_columns=None):
    result = {}
    if prior_columns is not None and set(prior_columns) != set(TABLES):
        raise EvidenceError('Incomplete prior clinical table inventory')
    for table in TABLES:
        columns = query('SHOW COLUMNS FROM ' + quote_identifier(table))
        actual = [line.split('\t', 1)[0] for line in columns]
        types = {row.split('\t')[0]: row.split('\t')[1] for row in columns}
        names = prior_columns[table] if prior_columns is not None else actual
        if not names or len(set(names)) != len(names) or not set(names) <= set(actual):
            raise EvidenceError('Package transition lost or duplicated a clinical column')
        # Cast before HEX: HEX(number) coerces to BIGINT and loses fractions.
        # Binary strings preserve cell values, NULL and bytes; JSON supplies unambiguous
        # boundaries. Sort whole rows, preserving duplicates and ignoring query order.
        rows = sorted(query('SELECT JSON_ARRAY(' + ','.join(cell_encoding(name, types[name])
            for name in names) + ') FROM ' + quote_identifier(table)))
        result[table] = {'columns': names, 'rows': len(rows),
            'dataSha256': hashlib.sha256(json.dumps(rows, ensure_ascii=True).encode()).hexdigest()}
    return result


def stored_files(base=Path('/var/lib/carlos-emr/CarlosDocument')):
    if base.is_symlink() or not base.is_dir():
        raise EvidenceError('Installed document store is unavailable')
    result = {}
    def unreadable(_failure):
        raise EvidenceError('Installed document store cannot be read')
    for directory, subdirectories, files in os.walk(base, followlinks=False, onerror=unreadable):
        parent = Path(directory)
        info = parent.lstat()
        if not stat.S_ISDIR(info.st_mode):
            raise EvidenceError('Document store directory changed type')
        result['directory:' + parent.relative_to(base).as_posix()] = [stat.S_IMODE(info.st_mode), info.st_uid, info.st_gid]
        if any((parent / name).is_symlink() for name in subdirectories):
            raise EvidenceError('Document store contains an unbound directory link')
        for name in files:
            path = parent / name
            descriptor = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0) | getattr(os, 'O_NONBLOCK', 0))
            with os.fdopen(descriptor, 'rb') as stream:
                before = os.fstat(stream.fileno())
                if not stat.S_ISREG(before.st_mode):
                    raise EvidenceError('Document store entry is not a regular file')
                os.set_blocking(stream.fileno(), True)
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
                if identity(before) != identity(os.fstat(stream.fileno())) or identity(before) != identity(path.lstat()):
                    raise EvidenceError('Document store changed during snapshot')
            result['file:' + path.relative_to(base).as_posix()] = [before.st_size,
                stat.S_IMODE(before.st_mode), before.st_uid, before.st_gid, digest]
    return result


def encryption_key():
    matches = re.findall(r'^encryption\.util\.secret\.key\s*[=:]\s*(.+)$',
        Path('/etc/carlos-emr/carlos.properties').read_text(), re.M)
    if len(matches) != 1 or not matches[0]:
        raise EvidenceError('Installed encryption key is absent or ambiguous')
    return hashlib.sha256(matches[0].encode()).hexdigest()


def document_preservation(before, after, old_package_entries, new_package_entries):
    updates = []
    if set(before) != set(after):
        return {'preserved': False, 'managedAssetUpdates': updates}
    for path, previous in before.items():
        current = after[path]
        if previous == current:
            continue
        entry = MANAGED_DOCUMENT_ASSETS.get(path)
        old = old_package_entries.get(entry, {})
        new = new_package_entries.get(entry, {})
        if (entry is None or len(previous) != 5 or len(current) != 5
                or previous[1:4] != current[1:4]
                or (previous[0], previous[4]) != (old.get('bytes'), old.get('sha256'))
                or (current[0], current[4]) != (new.get('bytes'), new.get('sha256'))):
            return {'preserved': False, 'managedAssetUpdates': updates}
        updates.append({'path': path, 'packageEntry': entry, 'previousSha256': previous[4], 'currentSha256': current[4]})
    return {'preserved': True, 'managedAssetUpdates': updates}


def version_allowed(mode, before, after):
    if mode not in ('upgrade', 'reinstall'):
        raise EvidenceError('Unknown package transition')
    if any(not isinstance(value, str) or not re.fullmatch(r'[0-9][0-9A-Za-z.+:~\-]*', value)
           for value in (before, after)):
        return False
    comparison = 'gt' if mode == 'upgrade' else 'eq'
    return subprocess.run(['dpkg', '--compare-versions', after, comparison, before],
                          capture_output=True, timeout=10).returncode == 0
