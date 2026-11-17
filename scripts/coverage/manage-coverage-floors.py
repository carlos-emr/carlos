#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Migrate legacy floors or import selected source evidence without shared-file edits."""
import argparse
from pathlib import Path

from coverage_policy import import_sources, migrate_policy


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--floors', type=Path,
                        default=Path(__file__).with_name('release-coverage-floors.json'))
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--migrate', action='store_true', help='Freeze a v1 policy as immutable seed evidence')
    action.add_argument('--import-from', dest='incoming', type=Path, help='Source-bound v1 evidence from a measured run or existing PR')
    parser.add_argument('--source', action='append', default=[], help='Java path to update; repeat for multiple paths')
    args = parser.parse_args()
    if args.migrate:
        if args.source:
            parser.error('--source is only valid with --import-from')
        migrate_policy(args.floors)
    else:
        import_sources(args.floors, args.incoming, args.source)


if __name__ == '__main__':
    main()
