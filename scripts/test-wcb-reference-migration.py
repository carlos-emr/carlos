#!/usr/bin/env python3
"""Verify #5062 on disposable local MariaDB/MySQL schemas.

Requires CREATE DATABASE access. MYSQL_HOST must be local; MYSQL_USER defaults
to root. Credentials come from the client's option file or MYSQL_PWD.
"""
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest
import uuid

MIGRATIONS = Path(__file__).resolve().parents[1] / 'database/mysql/migration/bc'
MIGRATION = (MIGRATIONS / 'V1.0.59.3__restore_wcb_reference_data.sql').read_text()
# Independent oracle: hashes of the retired oscardata_bc.sql at
# 80ce799ee564fcbe80e6dec9805f70cb789019ac^, imported into its original schema.
# Hash the HEX/NULL fields (no surrogate IDs), tab/newline separated, ordered by
# code/sidetype. This catches lost leading zeroes, notes, tabs and NULLs as well
# as missing rows, without shipping a second reference catalog as a test fixture.
CATALOG = {
    'wcb_bp_code': (85, 'b083a761e89396dee22da782df7dfb66519d1369bd4395e57ace460b566f2698'),
    'wcb_noi_code': (82, '4fc98f39be0c52f479d431811ec6e0d9cac62e5b885c4dabdbbff5373ef1637a'),
    'wcb_side': (4, 'a7e44d4697e5a26e60c311b30075017d61207670ecc1ebc676c2d64b966a1d73'),
}


class WcbReferenceMigration(unittest.TestCase):
    def setUp(self):
        host = os.environ.get('MYSQL_HOST', 'localhost')
        self.assertIn(host, ('localhost', '127.0.0.1', '::1'))
        client = shutil.which('mariadb') or shutil.which('mysql')
        self.assertIsNotNone(client, 'A MariaDB/MySQL client is required')
        self.command = [client, '-N', '-B', '--default-character-set=utf8mb4',
                        '--host=' + host, '--user=' + os.environ.get('MYSQL_USER', 'root')]
        self.database = 'carlos_wcb_test_' + uuid.uuid4().hex
        self.sql('CREATE DATABASE `' + self.database + '`', database=False)
        self.addCleanup(lambda: self.sql('DROP DATABASE `' + self.database + '`', database=False))
        schema = (MIGRATIONS / 'V1.0.6__restore_live_legacy_bc_tables_and_reference_data.sql').read_text()
        for table in CATALOG:
            definition = re.search(r'CREATE TABLE IF NOT EXISTS ' + table + r' \(.*?\n\);', schema, re.S)
            self.assertIsNotNone(definition, table)
            self.sql(definition[0])

    def sql(self, statement, *, database=True):
        result = subprocess.run(self.command + ([self.database] if database else []),
                                input="SET SESSION sql_mode='STRICT_ALL_TABLES';\n" + statement,
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.rstrip('\n')

    def snapshot(self, table, *, ids=False):
        side = table == 'wcb_side'
        columns = ['sidetype', 'sidedesc', 'usagenote'] if side else ['level1', 'level2', 'code', 'level3', 'usagenote']
        if ids and not side:
            columns.insert(0, table + '_no')
        fields = ','.join("COALESCE(HEX(" + column + "),'NULL')" for column in columns)
        return self.sql(f'SELECT {fields} FROM {table} ORDER BY ' + ('sidetype' if side else 'code'))

    def assert_catalog(self):
        for table, (count, digest) in CATALOG.items():
            with self.subTest(table=table):
                self.assertEqual(self.sql(f'SELECT COUNT(*) FROM {table}'), str(count))
                self.assertEqual(hashlib.sha256(self.snapshot(table).encode()).hexdigest(), digest)

    def test_empty_install_restores_exact_legacy_catalog_and_reruns_without_changes(self):
        self.sql(MIGRATION)
        self.assert_catalog()
        before = {table: self.snapshot(table, ids=True) for table in CATALOG}
        self.sql(MIGRATION)
        self.assertEqual({table: self.snapshot(table, ids=True) for table in CATALOG}, before)

    def test_partially_populated_install_preserves_custom_values_and_ids(self):
        for table in ('wcb_bp_code', 'wcb_noi_code'):
            code = '01100' if table == 'wcb_bp_code' else '01000'
            self.sql(f"INSERT INTO {table} ({table}_no,code,level1,level2,level3,usagenote) "
                     f"VALUES (900,'{code}','Local group','Local subgroup','Local description',NULL),"
                     "(1,'LOCAL','Local only','','','Local note')")
        self.sql("INSERT INTO wcb_side VALUES ('L','Local left','Local side note'),('X','Local side',NULL)")
        before = {table: self.snapshot(table, ids=True).splitlines() for table in CATALOG}
        self.sql(MIGRATION)
        for table, (count, _) in CATALOG.items():
            with self.subTest(table=table):
                self.assertEqual(self.sql(f'SELECT COUNT(*) FROM {table}'), str(count + 1))
                self.assertTrue(set(before[table]).issubset(self.snapshot(table, ids=True).splitlines()))
        after = {table: self.snapshot(table, ids=True) for table in CATALOG}
        self.sql(MIGRATION)
        self.assertEqual({table: self.snapshot(table, ids=True) for table in CATALOG}, after)

    def test_missing_rows_are_repaired_without_duplicating_existing_codes(self):
        self.sql(MIGRATION)
        self.sql("DELETE FROM wcb_bp_code WHERE code='01100'; "
                 "DELETE FROM wcb_noi_code WHERE code='01000'; DELETE FROM wcb_side WHERE sidetype='N'")
        self.sql(MIGRATION)
        self.assert_catalog()

    def test_adopted_duplicate_codes_are_preserved_and_not_added_to(self):
        self.sql("INSERT INTO wcb_bp_code (code,level3) VALUES ('01100','Local A'),('01100','Local B'); "
                 "INSERT INTO wcb_noi_code (code,level3) VALUES ('01000','Local A'),('01000','Local B'); "
                 "INSERT INTO wcb_side VALUES ('L','Local A',NULL),('L','Local B',NULL)")
        self.sql(MIGRATION)
        self.sql(MIGRATION)
        for table, (count, _) in CATALOG.items():
            self.assertEqual(self.sql(f'SELECT COUNT(*) FROM {table}'), str(count + 1))
        self.assertEqual(self.sql("SELECT GROUP_CONCAT(level3 ORDER BY level3) FROM wcb_bp_code WHERE code='01100'"),
                         'Local A,Local B')
        self.assertEqual(self.sql("SELECT GROUP_CONCAT(level3 ORDER BY level3) FROM wcb_noi_code WHERE code='01000'"),
                         'Local A,Local B')
        self.assertEqual(self.sql("SELECT GROUP_CONCAT(sidedesc ORDER BY sidedesc) FROM wcb_side WHERE sidetype='L'"),
                         'Local A,Local B')


if __name__ == '__main__':
    unittest.main()
