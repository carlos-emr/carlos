#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Generate an explicitly reviewed complete-table descriptor from pinned ON/BC contracts.

This does not decide source ownership, clinical mappings or the table ruling. Partial
projections and synthetic descriptors require their own reviewed recipes.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def descriptor(table, schema):
    tables = [row for row in schema['tables'] if row[0] == table]
    if len(tables) != 1 or tables[0][1] != 'InnoDB':
        raise ValueError('A single physical InnoDB table is required')
    columns = [{'name': row[1], 'type': row[2], 'nullable': row[3],
                'charset': row[6], 'collation': row[7], 'expression': row[8]}
               for row in schema['columns'] if row[0] == table]
    primary = [row[4] for row in sorted(schema['indexes'], key=lambda row: (row[0], row[1], row[3]))
               if row[0] == table and row[1] == 'PRIMARY']
    if not columns or not primary:
        raise ValueError('A complete column recipe and primary key are required')
    return {'table': table, 'engine': tables[0][1], 'columns': columns,
            'primaryKeyColumns': primary}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--table', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,63}', args.table):
        parser.error('A literal physical table name is required')
    contracts = [json.loads((ROOT / 'database/mysql/schema-contract' / (p + '.json')).read_text())
                 for p in ('on', 'bc')]
    recipes = [descriptor(args.table, contract['schema']) for contract in contracts]
    if recipes[0] != recipes[1]:
        raise ValueError('ON/BC recipes differ; review a profile-specific projection')
    output = ROOT / 'src/main/resources/cds-export/source-schema' / (args.table + '.json')
    output.write_text(json.dumps(recipes[0], indent=2) + '\n')
    print('Generated reviewed complete-table descriptor: ' + args.table)


if __name__ == '__main__':
    main()
