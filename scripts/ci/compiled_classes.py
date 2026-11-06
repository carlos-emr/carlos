# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Discard stale compiler outputs without deleting retained qualification evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat

OUTPUTS = ('classes', 'test-classes', 'maven-status', 'generated-sources', 'generated-test-sources')


def reset(root, evidence=None):
    target = root / 'target'
    if target.is_symlink():
        raise ValueError('Compilation target cannot be a symlink')
    for name in OUTPUTS:
        directory = target / name
        if directory.is_symlink() or (directory.exists() and not directory.is_dir()):
            raise ValueError('Compilation output must be a directory, not a symlink')
        if evidence is not None and evidence.resolve().is_relative_to(directory.resolve()):
            raise ValueError('Evidence cannot be stored in compiler output directories')
    for name in OUTPUTS:
        directory = target / name
        if directory.exists():
            shutil.rmtree(directory)


def fingerprints(root):
    directory = root / 'target/classes'
    if (root / 'target').is_symlink() or directory.is_symlink():
        raise ValueError('Compilation target cannot be a symlink')
    files = {}
    for path in sorted(directory.rglob('*.class')):
        for parent in (path, *path.parents):
            if parent.is_symlink():
                raise ValueError('Production classes contain a symlink')
            if parent == directory:
                break
        descriptor = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0) | getattr(os, 'O_NONBLOCK', 0))
        with os.fdopen(descriptor, 'rb') as stream:
            if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                raise ValueError('Production class is not a regular file')
            os.set_blocking(stream.fileno(), True)
            files[path.relative_to(directory).as_posix()] = hashlib.file_digest(stream, 'sha256').hexdigest()
    if not files:
        raise ValueError('No compiled production classes for coverage identity')
    return files


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('reset', 'fingerprints'))
    args = parser.parse_args()
    if args.operation == 'reset':
        reset(Path.cwd())
    else:
        print(json.dumps(fingerprints(Path.cwd()), sort_keys=True))
