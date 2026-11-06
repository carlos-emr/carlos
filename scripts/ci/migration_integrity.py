# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Compare exact bytes of every shared published SQL migration, including comments."""
import hashlib
import subprocess

from junit_evidence import EvidenceError


def sql_objects(root, revision):
    data = subprocess.check_output(['git', 'ls-tree', '-r', '-z', revision,
                                    'database/mysql/migration'], cwd=root)
    result = {}
    for item in data.split(b'\0'):
        if not item: continue
        metadata, name = item.split(b'\t', 1)
        path = name.decode('utf-8')
        if not path.endswith('.sql'): continue
        mode, kind, digest = metadata.decode('ascii').split()
        if mode not in ('100644', '100755') or kind != 'blob' or path in result:
            raise EvidenceError('Migration inventory contains a link or ambiguous entry')
        result[path] = digest
    return result


def compare_shared(root, base, head, minimum=0):
    before, after = sql_objects(root, base), sql_objects(root, head)
    shared = sorted(before.keys() & after.keys())
    if type(minimum) is not int or minimum < 0 or len(shared) < minimum:
        raise EvidenceError('Shared migration inventory is smaller than its reviewed baseline')
    rows = []
    for path in shared:
        def checksum(blob):
            content = subprocess.check_output(['git', 'cat-file', 'blob', blob], cwd=root)
            return hashlib.sha256(content).hexdigest()
        old_hash, new_hash = checksum(before[path]), checksum(after[path])
        rows.append({'path': path, 'mainSha256': old_hash, 'candidateSha256': new_hash,
                     'identical': old_hash == new_hash})
    return {'mainFiles': len(before), 'candidateFiles': len(after), 'sharedFiles': len(rows),
            'shared': rows, 'rewrittenSharedMigrations': [row['path'] for row in rows if not row['identical']],
            'ready': all(row['identical'] for row in rows)}
