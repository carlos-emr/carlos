#!/usr/bin/env python3
"""Verify billing report migrations on disposable local MariaDB schemas.

Requires CREATE DATABASE access. MYSQL_HOST must be local; MYSQL_USER defaults
 to root. Credentials come from the client's option file or MYSQL_PWD.
"""
import os
from pathlib import Path
import shutil
import subprocess
import unittest
import uuid

MIGRATIONS = Path(__file__).resolve().parents[1] / 'database/mysql/migration'


class ColumnMigrationChecks:
    """Shared preservation assertions, exercised for each concrete migration."""

    def setUp(self):
        host = os.environ.get('MYSQL_HOST', 'localhost')
        self.assertIn(host, ('localhost', '127.0.0.1', '::1'))
        client = shutil.which('mariadb') or shutil.which('mysql')
        self.assertIsNotNone(client, 'A MariaDB client is required')
        self.command = [client, '-N', '-B', '--default-character-set=utf8mb4',
                        '--host=' + host, '--user=' + os.environ.get('MYSQL_USER', 'root')]
        self.database = 'carlos_report_test_' + uuid.uuid4().hex
        self.migration = (MIGRATIONS / self.migration_path).read_text()
        self.sql('CREATE DATABASE `' + self.database + '`', database=False)
        self.addCleanup(lambda: self.sql('DROP DATABASE `' + self.database + '`', database=False))

    def sql(self, statement, *, database=True):
        result = subprocess.run(self.command + ([self.database] if database else []),
                                input="SET SESSION sql_mode='STRICT_ALL_TABLES';\n" + statement,
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def create(self, definition=None):
        if definition is None:
            definition = f"VARCHAR({self.old_width}) NOT NULL DEFAULT ''"
        self.sql(f'CREATE TABLE {self.table} (id INT PRIMARY KEY, {self.column} ' + definition +
                 self.extra_columns + ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci')

    def rows(self):
        return self.sql(f"SELECT id,COALESCE(HEX({self.column}),'NULL') FROM {self.table} ORDER BY id")

    def metadata(self, fields):
        return self.sql('SELECT ' + fields + ' FROM information_schema.columns '
                        f"WHERE table_schema=DATABASE() AND table_name='{self.table}' AND column_name='{self.column}'")

    def attributes(self):
        return self.metadata("IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'NO_DEFAULT'),"
                             "CHARACTER_SET_NAME,COLLATION_NAME,HEX(COLUMN_COMMENT)")

    def test_existing_values_and_complete_value_survive_widening_and_rerun(self):
        self.create()
        self.sql(f"INSERT INTO {self.table} (id,{self.column}) VALUES (1,'1234567890  '),(2,'')")
        before, attributes = self.rows(), self.attributes()
        self.sql(self.migration)
        self.assertEqual(self.metadata('CHARACTER_MAXIMUM_LENGTH'), str(self.width))
        self.assertEqual(self.rows(), before)
        self.assertEqual(self.attributes(), attributes)
        self.sql(f"INSERT INTO {self.table} (id,{self.column}) VALUES (3,'{self.complete_value}')")
        self.assertEqual(self.sql(f'SELECT HEX({self.column}) FROM {self.table} WHERE id=3'),
                         self.complete_value.encode().hex().upper())
        after = self.rows()
        self.sql(self.migration)
        self.assertEqual(self.rows(), after)
        self.assertEqual(self.metadata('CHARACTER_MAXIMUM_LENGTH'), str(self.width))

    def test_already_wider_adopted_column_is_not_shortened(self):
        self.create(f"VARCHAR({self.width + 10}) NULL DEFAULT 'UNKNOWN' COMMENT 'adopted field'")
        self.sql(f"INSERT INTO {self.table} (id,{self.column}) VALUES (1,'{'X' * (self.width + 1)}'),(2,NULL)")
        before, attributes = self.rows(), self.attributes()
        self.sql(self.migration)
        self.assertEqual(self.metadata('CHARACTER_MAXIMUM_LENGTH'), str(self.width + 10))
        self.assertEqual(self.rows(), before)
        self.assertEqual(self.attributes(), attributes)

    def test_nullable_values_default_collation_and_comment_are_retained(self):
        self.create(f"VARCHAR({self.old_width}) CHARACTER SET latin1 COLLATE latin1_bin NULL "
                    "DEFAULT 'UNKNOWN' COMMENT 'operator''s field'")
        self.sql(f"INSERT INTO {self.table} (id,{self.column}) VALUES (1,NULL),(2,'ABC123')")
        before, attributes = self.rows(), self.attributes()
        self.sql(self.migration)
        self.assertEqual(self.metadata('CHARACTER_MAXIMUM_LENGTH'), str(self.width))
        self.assertEqual(self.rows(), before)
        self.assertEqual(self.attributes(), attributes)
        self.sql(f'INSERT INTO {self.table} (id) VALUES (3)')
        self.assertEqual(self.sql(f'SELECT {self.column} FROM {self.table} WHERE id=3'), 'UNKNOWN')

    def test_not_null_column_without_default_keeps_that_contract(self):
        self.create(f'VARCHAR({self.old_width}) NOT NULL')
        attributes = self.attributes()
        self.sql(self.migration)
        self.assertEqual(self.attributes(), attributes)
        self.assertEqual(self.metadata('CHARACTER_MAXIMUM_LENGTH'), str(self.width))

    def test_empty_baseline_table_accepts_complete_value(self):
        self.create()
        self.sql(self.migration)
        self.sql(f"INSERT INTO {self.table} (id,{self.column}) VALUES (1,'{self.complete_value}')")
        self.assertEqual(self.sql(f'SELECT {self.column} FROM {self.table}'), self.complete_value)


class RemittanceHealthNumberMigration(ColumnMigrationChecks, unittest.TestCase):
    table, column, old_width, width = 'radetail', 'hin', 12, 14
    extra_columns = ''
    complete_value = '1234567890  ZZ'
    migration_path = 'common/V1.0.55__preserve_remittance_health_number_version.sql'


class ClaimsExplanationMigration(ColumnMigrationChecks, unittest.TestCase):
    table, column, old_width, width = 'billing_on_eareport', 'exp', 60, 255
    extra_columns = ', claim_error VARCHAR(20) NULL DEFAULT NULL'
    complete_value = '; '.join(str(i) + 'A|' + 'X' * 55 for i in range(4))
    migration_path = 'on/V1.0.56__preserve_claim_item_explanations.sql'

    def test_both_sets_of_claim_errors_fit_without_losing_existing_rows(self):
        self.create()
        self.sql("INSERT INTO billing_on_eareport (id, claim_error) VALUES (1,'VH9 E02')")
        self.sql(self.migration)
        self.assertEqual(self.sql('SELECT claim_error FROM billing_on_eareport WHERE id=1'), 'VH9 E02')
        errors = 'VH9 E02 E03 E04 E05 R01 R02 R03 R04 R05'
        self.assertEqual(len(errors), 39)
        self.assertEqual(len(self.complete_value), 238)
        self.sql("INSERT INTO billing_on_eareport (id,claim_error) VALUES (2,'" + errors + "')")
        self.sql(self.migration)
        self.assertEqual(self.sql('SELECT claim_error FROM billing_on_eareport WHERE id=2'), errors)


if __name__ == '__main__':
    unittest.main()
