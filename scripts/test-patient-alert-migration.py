#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (c) 2026 CARLOS Contributors.
"""Isolated-database regression for common/V1.0.59.54 (patient alert to TEXT, #6264).

Run against a disposable local MariaDB/MySQL server with CREATE DATABASE access.
MYSQL_HOST defaults to localhost (socket); only localhost/127.0.0.1/::1 allowed.
MYSQL_USER defaults to root. The client reads MYSQL_PWD or its normal option file.
Each case owns a randomly named database and removes it even after a failure.
"""
import os
from pathlib import Path
import shutil
import subprocess
import unittest
import uuid

MIGRATION = (Path(__file__).resolve().parents[1] /
             'database/mysql/migration/common/V1.0.59.54__widen_patient_alert.sql').read_text()

# The two tables as the V1 baseline ships them.
STOCK_CUST = ("CREATE TABLE demographiccust (demographic_no INT(10) NOT NULL DEFAULT 0, "
              "cust1 VARCHAR(255) DEFAULT NULL, cust2 VARCHAR(255) DEFAULT NULL, "
              "cust3 VARCHAR(255) DEFAULT NULL, cust4 VARCHAR(255) DEFAULT NULL, content MEDIUMTEXT, "
              "PRIMARY KEY (demographic_no), KEY cust1 (cust1), KEY cust2 (cust2), KEY cust3 (cust3), "
              "KEY cust4 (cust4)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci")
STOCK_ARCHIVE = ("CREATE TABLE demographiccustArchive (id INT(10) NOT NULL AUTO_INCREMENT, "
                 "demographic_no INT(10) NOT NULL, cust1 VARCHAR(255) DEFAULT NULL, "
                 "cust2 VARCHAR(255) DEFAULT NULL, cust3 VARCHAR(255) DEFAULT NULL, "
                 "cust4 VARCHAR(255) DEFAULT NULL, content MEDIUMTEXT, PRIMARY KEY (id)) "
                 "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci")
# DemographicCust.ALERT_MAX_LENGTH: the most characters an alert may have.
ALERT_MAX_LENGTH = 16383
# U+1F600, four bytes in utf8mb4: the widest character an alert can hold.
WIDE = 'F09F9880'


class PatientAlertMigration(unittest.TestCase):
    def setUp(self):
        host = os.environ.get('MYSQL_HOST', 'localhost')
        self.assertIn(host, ('localhost', '127.0.0.1', '::1'))
        client = shutil.which('mariadb') or shutil.which('mysql')
        self.assertIsNotNone(client, 'A MariaDB/MySQL client is required')
        self.command = [client, '-N', '-B', '--default-character-set=utf8mb4', '--host=' + host,
                        '--user=' + os.environ.get('MYSQL_USER', 'root')]
        self.database = 'carlos_alert_test_' + uuid.uuid4().hex
        self.run_sql('CREATE DATABASE `' + self.database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci',
                     database=False)
        self.addCleanup(lambda: self.run_sql('DROP DATABASE `' + self.database + '`', database=False))

    def run_sql(self, sql, *, database=True, success=True, error=None):
        result = subprocess.run(self.command + ([self.database] if database else []),
                                input=sql, text=True, capture_output=True, timeout=120)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, 'The migration must refuse this schema')
            self.assertIn(error, result.stderr)
        return result.stdout.strip()

    def stock(self):
        self.run_sql(STOCK_CUST + ';' + STOCK_ARCHIVE)

    def definition(self, table):
        return self.run_sql('SHOW CREATE TABLE `' + table + '`').split('\t', 1)[1]

    def column(self, table):
        return self.run_sql("SELECT CONCAT_WS('|', column_type, character_set_name, collation_name, is_nullable, "
                            "COALESCE(column_default, '-'), column_comment) FROM information_schema.columns "
                            "WHERE table_schema=DATABASE() AND table_name='" + table + "' AND column_name='cust3'")

    def indexes(self, table):
        return self.run_sql("SELECT CONCAT_WS('|', index_name, seq_in_index, column_name, COALESCE(sub_part, '-'), "
                            "non_unique, index_type) FROM information_schema.statistics WHERE table_schema=DATABASE() "
                            "AND table_name='" + table + "' AND column_name='cust3' ORDER BY index_name, seq_in_index")

    def snapshot(self):
        return (self.run_sql('SELECT demographic_no, COALESCE(HEX(cust3), \'NULL\') FROM demographiccust '
                             'ORDER BY demographic_no'),
                self.run_sql('SELECT id, COALESCE(HEX(cust3), \'NULL\') FROM demographiccustArchive ORDER BY id'))

    def test_stock_columns_become_text_with_every_alert_kept_and_the_index_rebuilt(self):
        self.stock()
        self.run_sql("INSERT INTO demographiccust (demographic_no, cust3) VALUES "
                     "(1, REPEAT('A', 255)), (2, REPEAT(UNHEX('" + WIDE + "'), 255)), (3, NULL), (4, ''), "
                     "(5, 'Line one\\r\\nLine two');"
                     "INSERT INTO demographiccustArchive (demographic_no, cust3) VALUES "
                     "(1, REPEAT(UNHEX('" + WIDE + "'), 255)), (2, NULL), (3, 'Allergic to penicillin')")
        before = self.snapshot()

        self.run_sql(MIGRATION)

        self.assertEqual(self.snapshot(), before)
        self.assertEqual(self.column('demographiccust'), 'text|utf8mb4|utf8mb4_general_ci|YES|NULL|')
        self.assertEqual(self.column('demographiccustArchive'), 'text|utf8mb4|utf8mb4_general_ci|YES|NULL|')
        # The same 255 leading characters are indexed as before, still not unique.
        self.assertEqual(self.indexes('demographiccust'), 'cust3|1|cust3|255|1|BTREE')
        self.assertEqual(self.indexes('demographiccustArchive'), '')
        self.assertIn('KEY `cust1` (`cust1`)', self.definition('demographiccust'))
        self.assertEqual(self.run_sql("SELECT demographic_no FROM demographiccust WHERE cust3='Line one\\r\\nLine two'"),
                         '5')

    def test_a_full_length_alert_of_four_byte_characters_is_stored_whole_and_a_longer_one_is_refused(self):
        self.stock()
        self.run_sql(MIGRATION)
        for table, key in (('demographiccust', 'demographic_no'), ('demographiccustArchive', 'demographic_no')):
            with self.subTest(table=table):
                self.run_sql("SET SESSION sql_mode='STRICT_ALL_TABLES';"
                             "INSERT INTO `" + table + "` (" + key + ", cust3) VALUES "
                             "(7, REPEAT(UNHEX('" + WIDE + "'), " + str(ALERT_MAX_LENGTH) + "))")
                self.assertEqual(self.run_sql("SELECT CONCAT(CHAR_LENGTH(cust3), '/', OCTET_LENGTH(cust3), '/', "
                                              "cust3=REPEAT(UNHEX('" + WIDE + "'), " + str(ALERT_MAX_LENGTH) + ")) "
                                              "FROM `" + table + "` WHERE " + key + "=7"),
                                 '{0}/{1}/1'.format(ALERT_MAX_LENGTH, 4 * ALERT_MAX_LENGTH))
                # Beyond the 65,535 bytes of TEXT a strict session refuses rather than cutting.
                self.run_sql("SET SESSION sql_mode='STRICT_ALL_TABLES';"
                             "INSERT INTO `" + table + "` (" + key + ", cust3) VALUES (8, REPEAT('B', 65536))",
                             success=False, error='Data too long')

    def test_a_replay_changes_nothing(self):
        self.stock()
        self.run_sql("INSERT INTO demographiccust (demographic_no, cust3) VALUES (1, 'Kept')")
        self.run_sql(MIGRATION)
        after = (self.definition('demographiccust'), self.definition('demographiccustArchive'), self.snapshot())
        self.run_sql(MIGRATION)
        self.assertEqual((self.definition('demographiccust'), self.definition('demographiccustArchive'),
                          self.snapshot()), after)

    def test_a_wider_text_column_is_never_narrowed(self):
        self.run_sql(STOCK_CUST.replace('cust3 VARCHAR(255) DEFAULT NULL', 'cust3 MEDIUMTEXT DEFAULT NULL')
                     .replace('KEY cust3 (cust3)', 'KEY cust3 (cust3(100))') + ';'
                     + STOCK_ARCHIVE.replace('cust3 VARCHAR(255)', 'cust3 LONGTEXT'))
        before = (self.definition('demographiccust'), self.definition('demographiccustArchive'))
        self.run_sql(MIGRATION)
        self.assertEqual((self.definition('demographiccust'), self.definition('demographiccustArchive')), before)

    def test_local_collation_nullability_default_comment_and_prefix_indexes_are_kept(self):
        self.run_sql(STOCK_CUST.replace(
            'cust3 VARCHAR(255) DEFAULT NULL',
            "cust3 VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'Alert box'")
            .replace('KEY cust4 (cust4)', 'KEY cust4 (cust4), KEY alert_start (cust3(20)), FULLTEXT KEY alert_words (cust3)')
            + ';' + STOCK_ARCHIVE.replace('cust3 VARCHAR(255) DEFAULT NULL', 'cust3 CHAR(40) CHARACTER SET latin1 DEFAULT NULL'))
        self.run_sql("INSERT INTO demographiccust (demographic_no, cust3) VALUES (1, 'Case'), (2, 'case');"
                     "INSERT INTO demographiccustArchive (demographic_no, cust3) VALUES (1, 'Caf\\xe9')")
        before = self.snapshot()

        self.run_sql(MIGRATION)

        self.assertEqual(self.snapshot(), before)
        self.assertEqual(self.column('demographiccust'), "text|utf8mb4|utf8mb4_bin|NO|''|Alert box")
        self.assertEqual(self.column('demographiccustArchive'), 'text|latin1|latin1_swedish_ci|YES|NULL|')
        # The stock index keeps its 200 characters; the clinic's own indexes are untouched.
        self.assertEqual(self.indexes('demographiccust'),
                         'alert_start|1|cust3|20|1|BTREE\nalert_words|1|cust3|-|1|FULLTEXT\ncust3|1|cust3|200|1|BTREE')

    def test_another_index_on_the_whole_column_stops_the_upgrade_before_either_table_changes(self):
        for index in ('KEY alert_owner (demographic_no, cust3)', 'UNIQUE KEY alert_unique (cust3)'):
            with self.subTest(index=index):
                self.run_sql('DROP TABLE IF EXISTS demographiccust, demographiccustArchive')
                self.run_sql(STOCK_CUST.replace('KEY cust4 (cust4)', 'KEY cust4 (cust4), ' + index) + ';'
                             + STOCK_ARCHIVE)
                before = (self.definition('demographiccust'), self.definition('demographiccustArchive'))
                self.run_sql(MIGRATION, success=False,
                             error='Review the indexes on demographiccust.cust3 before upgrade')
                self.assertEqual((self.definition('demographiccust'), self.definition('demographiccustArchive')),
                                 before)

    def test_a_unique_index_named_cust3_is_not_mistaken_for_the_shipped_one(self):
        self.run_sql(STOCK_CUST.replace('KEY cust3 (cust3)', 'UNIQUE KEY cust3 (cust3)') + ';' + STOCK_ARCHIVE)
        self.run_sql(MIGRATION, success=False, error='Review the indexes on demographiccust.cust3 before upgrade')
        self.assertIn('varchar(255)', self.column('demographiccust'))

    def test_a_missing_table_or_column_stops_the_upgrade_before_anything_changes(self):
        self.run_sql(STOCK_CUST)
        self.run_sql(MIGRATION, success=False, error='Resolve missing demographiccustArchive table before upgrade')
        self.assertIn('varchar(255)', self.column('demographiccust'))
        self.run_sql(STOCK_ARCHIVE.replace('cust3 VARCHAR(255) DEFAULT NULL,', ''))
        self.run_sql(MIGRATION, success=False, error='Review demographiccustArchive.cust3 before upgrade')
        self.assertIn('varchar(255)', self.column('demographiccust'))

    def test_an_unexpected_column_type_stops_the_upgrade(self):
        self.run_sql(STOCK_CUST.replace('cust3 VARCHAR(255) DEFAULT NULL', 'cust3 INT DEFAULT NULL') + ';'
                     + STOCK_ARCHIVE)
        self.run_sql(MIGRATION, success=False, error='Review demographiccust.cust3 before upgrade')
        self.assertIn('varchar(255)', self.column('demographiccustArchive'))

    def test_the_session_sql_mode_is_restored(self):
        self.stock()
        self.assertEqual(self.run_sql("SET SESSION sql_mode='NO_ENGINE_SUBSTITUTION';" + MIGRATION
                                      + ';SELECT @@SESSION.sql_mode'), 'NO_ENGINE_SUBSTITUTION')


if __name__ == '__main__':
    unittest.main(verbosity=2)
