#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Verify export coverage rulings and their exact canonical physical-schema binding.

New tables require an explicit ruling. This tool never guesses patient ownership,
classifies a populated source as operational, or qualifies a clinical engine.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = ROOT / 'src/main/resources/cds-export/source-catalogue.json'
SCOPE = ROOT / 'src/main/resources/cds-export/source-schema/clinical_source_catalogue.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh-contract-hashes', action='store_true',
                        help='refresh exact physical hashes after explicit table rulings have been reviewed')
    args = parser.parse_args()
    catalogue = json.loads(CATALOGUE.read_text())
    assert catalogue['format'] == 1
    actual = set()
    contracts = {}
    for province in ('on', 'bc'):
        path = ROOT / 'database/mysql/schema-contract' / (province + '.json')
        contract = json.loads(path.read_text())
        contracts[province] = contract
        assert contract['format'] == 2 and contract['province'] == province
        actual.update(row[0] for row in contract['schema']['tables'])
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if args.refresh_contract_hashes:
            catalogue['profiles'][province]['physicalContractSha256'] = digest
        else:
            assert catalogue['profiles'][province]['physicalContractSha256'] == digest, province + ' contract drift'
    assert actual == set(catalogue['tables']), 'Every physical table requires its own explicit ruling'
    assert set(catalogue['tables'].values()) <= {'MAPPED_SOURCE', 'REFERENCE', 'OPERATIONAL', 'ACCESS_CONTROL', 'REQUIRE_EMPTY'}
    for field, allowed in [('patientScopes', {'REQUIRE_EMPTY', 'ACCESS_CONTROL'}),
                           ('clinicalRemainders', {'ACCESS_CONTROL'})]:
        mapping = catalogue[field]
        assert isinstance(mapping, dict)
        for table, names in mapping.items():
            assert catalogue['tables'][table] in allowed
            assert isinstance(names, list) and 1 <= len(names) <= 4 and len(set(names)) == len(names)
            for province in ('on', 'bc'):
                contract = contracts[province]
                if table not in {row[0] for row in contract['schema']['tables']}:
                    continue
                columns = {row[1]: row[2] for row in contract['schema']['columns'] if row[0] == table}
                assert set(names) <= set(columns), table + ' scope column drift'
                if field == 'patientScopes':
                    assert all(columns[name].startswith(('tinyint(', 'smallint(', 'mediumint(', 'int(', 'bigint(', 'varchar('))
                               for name in names), table + ' owner type drift'
    for table, disposition in catalogue['tables'].items():
        if disposition == 'ACCESS_CONTROL':
            assert table in catalogue['patientScopes'] and table in catalogue['clinicalRemainders']
    declared = {name for names in catalogue['requiredScopes'].values() for name in names}
    mapped = set()
    for name in declared:
        descriptor = json.loads((ROOT / 'src/main/resources/cds-export/source-schema' / (name + '.json')).read_text())
        if descriptor['engine'] == 'InnoDB':
            mapped.add(descriptor['table'])
    assert all(table in mapped for table, ruling in catalogue['tables'].items() if ruling == 'MAPPED_SOURCE')
    scope = json.loads(SCOPE.read_text())
    if args.refresh_contract_hashes:
        CATALOGUE.write_text(json.dumps(catalogue, indent=2) + '\n')
        scope['columns'][0]['expression'] = hashlib.sha256(CATALOGUE.read_bytes()).hexdigest()
        SCOPE.write_text(json.dumps(scope, indent=2) + '\n')
    assert scope['columns'][0]['expression'] == hashlib.sha256(CATALOGUE.read_bytes()).hexdigest()
    print('Export source catalogue verified: ' + str(len(actual)) + ' explicit table rulings; clinical qualification remains separate.')


if __name__ == '__main__':
    main()
