#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later.
"""Exercise the archive engine migration against disposable MariaDB schemas.

Run against a test server only. Credentials use MYSQL_PWD; the account needs
CREATE/DROP DATABASE and DDL privileges. Only uniquely named fixture schemas
created by this process are changed. No application data is read.
"""
import argparse
import contextlib
import re
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / 'database/mysql/migration/common'
TABLES = ('document', 'emailConfig', 'emailLog')


class DatabaseChecks:
    """Run SQL and fixtures using the actual baseline table definitions."""

    def __init__(self, args) -> None:
        self.client = ['mariadb', '--no-defaults', '--host=' + args.host,
                       '--port=' + str(args.port), '--user=' + args.user,
                       '--batch', '--skip-column-names', '--binary-as-hex']
        baseline = (MIGRATIONS / 'V1__baseline_schema.sql').read_text()
        self.definitions = {}
        for table in TABLES:
            match = re.search(r'CREATE TABLE `' + re.escape(table) + r'` \([\s\S]+?;', baseline)
            if match is None:
                raise AssertionError('Baseline definition missing: ' + table)
            self.definitions[table] = match.group()
        paths = list(MIGRATIONS.glob('V*__prepare_outbound_email_archive_reference_engines.sql'))
        if len(paths) != 1:
            raise AssertionError('Expected exactly one archive engine migration')
        self.migration = paths[0].read_text()
        self.case_sensitive = self.sql('SELECT @@lower_case_table_names') == '0'
        self.passed = 0

    def sql(self, statement, database=None, error=None):
        """Fail on any unexpected SQL error, including an expected error not occurring."""
        result = subprocess.run(self.client + ([database] if database else []),
                                input=statement, text=True, capture_output=True, timeout=120, check=False)
        if error is not None:
            if result.returncode == 0 or error not in result.stderr:
                raise AssertionError((result.returncode, result.stdout, result.stderr))
        elif result.returncode:
            raise AssertionError(result.stderr)
        return result.stdout.strip()

    @contextlib.contextmanager
    def fixture(self, engines, missing=None, view=None, wrong_case=None):
        """Create only our unique schema and remove it even when a check fails."""
        database = 'archive_engine_test_' + uuid.uuid4().hex
        self.sql('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4')
        try:
            for table, engine in zip(TABLES, engines, strict=True):
                if table == missing:
                    continue
                if table == view:
                    self.sql('CREATE VIEW `' + table + '` AS SELECT 1 AS id', database)
                    continue
                ddl = self.definitions[table].replace('ENGINE=InnoDB', 'ENGINE=' + engine)
                # Legacy MyISAM has no FKs. Failure fixtures also need to create
                # emailLog when its referenced emailConfig is intentionally absent.
                if engine != 'InnoDB' or missing or view or wrong_case:
                    ddl = re.sub(r',\n  CONSTRAINT [^\n]+', '', ddl)
                if table == wrong_case:
                    ddl = ddl.replace('`' + table + '`', '`' + table.upper() + '`')
                self.sql(ddl, database)
            yield database
        finally:
            self.sql('DROP DATABASE `' + database + '`')

    def engines(self, database):
        """Snapshot all fixture object engines to detect partial conversion."""
        return self.sql('SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES '
                        'WHERE BINARY TABLE_SCHEMA=BINARY DATABASE() ORDER BY BINARY TABLE_NAME', database)

    def success_case(self, engines, wrong_case=None):
        """Verify conversion, row/index preservation, FK eligibility, and repeat safety."""
        with self.fixture(engines, wrong_case=wrong_case) as database:
            self.sql("INSERT INTO document(document_no,docdesc,restrictToProgram) VALUES (7,'sentinel',0);"
                     "INSERT INTO emailConfig(id,configDetails) VALUES (8,'synthetic-config');"
                     "INSERT INTO emailLog(id,configId,body) VALUES (9,8,UNHEX('0001FEFF'));", database)
            rows = [self.sql('SELECT * FROM `' + t + '`', database) for t in TABLES]
            indexes = [self.sql('SHOW INDEX FROM `' + t + '`', database) for t in TABLES]
            # Cardinality and index implementation may legitimately change with the engine.
            def index_columns(values):
                return [tuple(row.split('\t')[i] for i in (1, 2, 3, 4))
                        for row in values.splitlines()]
            table_ids = self.sql("SELECT NAME, TABLE_ID FROM information_schema.INNODB_SYS_TABLES "
                                 "WHERE NAME LIKE CONCAT(DATABASE(), '/%') ORDER BY NAME", database)
            self.sql(self.migration, database)
            if engines == ('InnoDB',) * 3 and table_ids != self.sql(
                    "SELECT NAME, TABLE_ID FROM information_schema.INNODB_SYS_TABLES "
                    "WHERE NAME LIKE CONCAT(DATABASE(), '/%') ORDER BY NAME", database):
                raise AssertionError('Compliant InnoDB tables were rebuilt')
            if self.sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE "
                        "BINARY TABLE_SCHEMA=BINARY DATABASE() AND ENGINE='InnoDB'", database) != '3':
                raise AssertionError('Not all references converted')
            for table, before, before_indexes in zip(TABLES, rows, indexes, strict=True):
                if self.sql('SELECT * FROM `' + table + '`', database) != before:
                    raise AssertionError('Data changed: ' + table)
                if index_columns(self.sql('SHOW INDEX FROM `' + table + '`', database)) != index_columns(before_indexes):
                    raise AssertionError('Index definition changed: ' + table)
            self.sql('CREATE TABLE archive_probe (documentId INT, configId BIGINT, logId BIGINT, '
                     'FOREIGN KEY (documentId) REFERENCES document(document_no), '
                     'FOREIGN KEY (configId) REFERENCES emailConfig(id), '
                     'FOREIGN KEY (logId) REFERENCES emailLog(id)) ENGINE=InnoDB;'
                     'INSERT INTO archive_probe VALUES (7,8,9);', database)
            self.sql('INSERT INTO archive_probe VALUES (99,8,9)', database, error='1452')
            definitions = [self.sql('SHOW CREATE TABLE `' + t + '`', database) for t in TABLES]
            self.sql(self.migration, database)
            if definitions != [self.sql('SHOW CREATE TABLE `' + t + '`', database) for t in TABLES]:
                raise AssertionError('Repeated migration changed schema')
        self.passed += 1

    def failure_case(self, kind, table):
        """Reject unavailable reference tables before any other engine is altered."""
        with self.fixture(('MyISAM',) * 3, **{kind: table}) as database:
            before = self.engines(database)
            self.sql(self.migration, database, error='1347' if kind == 'view' else '1146')
            if self.engines(database) != before:
                raise AssertionError('Preflight allowed partial conversion: ' + kind + ' ' + table)
        self.passed += 1

    def neighboring_schema_case(self):
        """Ignore same-spelling schemas with different case in preflight and lookup."""
        for missing in ('emailLog', None):
            with self.fixture(('MyISAM',) * 3, missing=missing) as database:
                neighbor = database.upper()
                self.sql('CREATE DATABASE `' + neighbor + '`')
                try:
                    for ddl in self.definitions.values():
                        self.sql(ddl, neighbor)
                    before = self.engines(database)
                    self.sql(self.migration, database, error='1146' if missing else None)
                    after = self.engines(database)
                    if missing and before != after:
                        raise AssertionError('Neighbor schema bypassed preflight')
                    if not missing and ('MyISAM' in after or after.count('InnoDB') != 3):
                        raise AssertionError('Neighbor schema changed engine lookup')
                finally:
                    self.sql('DROP DATABASE `' + neighbor + '`')
            self.passed += 1

    def run(self):
        """Cover compliant, legacy, mixed, missing, view, and case-folded schemas."""
        for engines in [('InnoDB',) * 3, ('MyISAM',) * 3,
                        ('MyISAM', 'InnoDB', 'InnoDB'), ('InnoDB', 'InnoDB', 'MyISAM'),
                        ('Aria', 'MyISAM', 'MyISAM')]:
            self.success_case(engines)
        for table in TABLES:
            self.failure_case('missing', table)
            self.failure_case('view', table)
            if self.case_sensitive:
                self.failure_case('wrong_case', table)
        if self.case_sensitive:
            self.neighboring_schema_case()
        else:
            for table in TABLES:
                self.success_case(('MyISAM',) * 3, wrong_case=table)
        print(f'Passed {self.passed} live archive-engine scenarios; case_sensitive={self.case_sensitive}')


def main():
    """Read the test server connection without placing passwords on argv."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    DatabaseChecks(parser.parse_args()).run()


if __name__ == '__main__':
    main()
