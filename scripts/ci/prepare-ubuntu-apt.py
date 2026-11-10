#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Use official Ubuntu HTTPS mirrors before CI installs packages."""
import argparse
from pathlib import Path
import re


UBUNTU_MIRROR = re.compile(
    rb'https?://(?P<host>(?:azure\.)?archive\.ubuntu\.com|security\.ubuntu\.com)'
    rb'/ubuntu(?=/|\s|$)')


def prepare(apt_root):
    """Preserve suites, components, signing keys and unrelated repositories."""
    paths = [apt_root / 'sources.list', apt_root / 'apt-mirrors.txt']
    parts = apt_root / 'sources.list.d'
    paths.extend(sorted(parts.glob('*.list')))
    paths.extend(sorted(parts.glob('*.sources')))
    changed = []
    for path in paths:
        if not path.is_file():
            continue
        original = path.read_bytes()
        updated = UBUNTU_MIRROR.sub(lambda match: b'https://'
            + (b'security.ubuntu.com' if match['host'] == b'security.ubuntu.com'
               else b'archive.ubuntu.com') + b'/ubuntu', original)
        if updated != original:
            path.write_bytes(updated)
            changed.append(path)
    return changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apt-root', type=Path, default=Path('/etc/apt'))
    args = parser.parse_args()
    print(f'Ubuntu APT source files redirected to official HTTPS mirrors: {len(prepare(args.apt_root))}')


if __name__ == '__main__':
    main()
