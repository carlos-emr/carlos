#!/usr/bin/env python3
"""Verify the remittance-column migration on disposable local MariaDB schemas.

Requires CREATE DATABASE access. MYSQL_HOST must be local; MYSQL_USER defaults
to root. Credentials come from the client's option file or MYSQL_PWD.
"""
import os
from pathlib import Path
import shutil
import subprocess
import unittest
import uuid

MIGRATION = (Path(__file__).resolve().parents[1] /
             'database/mysql/migration/common/V1.0.55__preserve_remittance_health_number_version.sql').read_text()


class RemittanceHealthNumberMigration(unittest.TestCase):
    def setUp(self):
        host = os.environ.get('MYSQL_HOST', 'localhost')
        self.assertIn(host, ('localhost', '127.0.0.1', '::1'))
        client = shutil.which('mariadb') or shutil.which('mysql')
        self.assertIsNotNone(client, 'A MariaDB client is required')
        self.command = [client, '-N', '-B', '--default-character-set=utf8mb4',
                        '--host=' + host, '--user=' + os.environ.get('MYSQL_USER', 'root')]
        self.database = 'carlos_ra_hin_test_' + uuid.uuid4().hex
        self.sql('CREATE DATABASE `' + self.database + '`', database=False)
        self.addCleanup(lambda: self.sql('DROP DATABASE `' + self.database + '`', database=False))

    def sql(self, statement, *, database=True):
        result = subprocess.run(self.command + ([self.database] if database else []),
                                input="SET SESSION sql_mode='STRICT_ALL_TABLES';\n" + statement,
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def create(self, definition="VARCHAR(12) NOT NULL DEFAULT ''"):
        self.sql('CREATE TABLE radetail (id INT PRIMARY KEY, hin ' + definition +
                 ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci')

    def rows(self):
        return self.sql("SELECT id,COALESCE(HEX(hin),'NULL') FROM radetail ORDER BY id")

    def attributes(self):
        return self.sql("SELECT IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'NO_DEFAULT'),"
                        "CHARACTER_SET_NAME,COLLATION_NAME,HEX(COLUMN_COMMENT) "
                        "FROM information_schema.columns WHERE table_schema=DATABASE() "
                        "AND table_name='radetail' AND column_name='hin'")

    def width(self):
        return self.sql("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                        "WHERE table_schema=DATABASE() AND table_name='radetail' AND column_name='hin'")

    def test_existing_values_and_full_version_survive_widening_and_rerun(self):
        self.create()
        self.sql("INSERT INTO radetail VALUES (1,'1234567890  '),(2,'')")
        before, attributes = self.rows(), self.attributes()
        self.sql(MIGRATION)
        self.assertEqual(self.width(), '14')
        self.assertEqual(self.rows(), before)
        self.assertEqual(self.attributes(), attributes)
        self.sql("INSERT INTO radetail VALUES (3,'1234567890  ZZ')")
        self.assertEqual(self.sql('SELECT HEX(hin) FROM radetail WHERE id=3'),
                         '1234567890  ZZ'.encode().hex().upper())
        after = self.rows()
        self.sql(MIGRATION)
        self.assertEqual(self.rows(), after)
        self.assertEqual(self.width(), '14')

    def test_already_wider_adopted_column_is_not_shortened(self):
        self.create("VARCHAR(24) NULL DEFAULT 'UNKNOWN' COMMENT 'adopted field'")
        self.sql("INSERT INTO radetail VALUES (1,'123456789012345678'),(2,NULL)")
        before, attributes = self.rows(), self.attributes()
        self.sql(MIGRATION)
        self.assertEqual(self.width(), '24')
        self.assertEqual(self.rows(), before)
        self.assertEqual(self.attributes(), attributes)

    def test_nullable_values_default_collation_and_comment_are_retained(self):
        self.create("VARCHAR(12) CHARACTER SET latin1 COLLATE latin1_bin NULL "
                    "DEFAULT 'UNKNOWN' COMMENT 'operator''s field'")
        self.sql("INSERT INTO radetail VALUES (1,NULL),(2,'ABC123')")
        before, attributes = self.rows(), self.attributes()
        self.sql(MIGRATION)
        self.assertEqual(self.width(), '14')
        self.assertEqual(self.rows(), before)
        self.assertEqual(self.attributes(), attributes)
        self.sql('INSERT INTO radetail (id) VALUES (3)')
        self.assertEqual(self.sql('SELECT hin FROM radetail WHERE id=3'), 'UNKNOWN')

    def test_not_null_column_without_default_keeps_that_contract(self):
        self.create('VARCHAR(12) NOT NULL')
        attributes = self.attributes()
        self.sql(MIGRATION)
        self.assertEqual(self.attributes(), attributes)
        self.assertEqual(self.width(), '14')

    def test_empty_baseline_table_accepts_full_health_number_and_version(self):
        self.create()
        self.sql(MIGRATION)
        self.sql("INSERT INTO radetail VALUES (1,'123456789012ZZ')")
        self.assertEqual(self.sql('SELECT hin FROM radetail'), '123456789012ZZ')


if __name__ == '__main__':
    unittest.main()
