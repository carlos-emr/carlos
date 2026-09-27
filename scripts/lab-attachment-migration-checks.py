#!/usr/bin/env python3
"""Validate source backfill against the shipped schema in an isolated local MariaDB database.

Run as a database account with CREATE/DROP DATABASE privileges:
  python3 scripts/lab-attachment-migration-checks.py
MYSQL_DEFAULTS_FILE may name a client option file. The connection uses the local socket.
The randomly named test database is removed even when an assertion fails.
"""
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
import os
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parent.parent
DATABASE = 'carlos_lab_source_test_' + uuid.uuid4().hex
CLIENT = ['mariadb']
if os.environ.get('MYSQL_DEFAULTS_FILE'):
    CLIENT.append('--defaults-extra-file=' + os.environ['MYSQL_DEFAULTS_FILE'])
CLIENT.extend(['--protocol=socket', '--batch', '--skip-column-names'])
ATTACHMENTS = [('consultdocs', 'requestId', 'document_no', 'doctype', 'attach_date', 'provider_no'),
               ('EFormDocs', 'fdid', 'document_no', 'doctype', 'attach_date', 'provider_no'),
               ('consultResponseDoc', 'responseId', 'documentNo', 'docType', 'attachDate', 'providerNo')]


def query(sql, database=None):
    return subprocess.run(CLIENT + ([database] if database else []), input=sql,
                          text=True, capture_output=True, check=True).stdout.strip()


def expect(sql, expected):
    actual = query(sql, DATABASE)
    assert actual == expected, f'Expected {expected!r}, got {actual!r}: {sql}'


def main():
    schema = (ROOT / 'database/mysql/migration/common/V1__baseline_schema.sql').read_text()
    migration = (ROOT / 'database/mysql/migration/common/V1.0.38__consultation_eform_lab_sources.sql').read_text()
    query(f'CREATE DATABASE `{DATABASE}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci')
    try:
        for table in ['consultationRequests', 'consultationResponse', 'eform_data', 'patientLabRouting'] + [row[0] for row in ATTACHMENTS]:
            match = re.search(r'CREATE TABLE `' + table + r'` \(.*?;', schema, re.S)
            assert match, f'Missing baseline table {table}'
            query(match.group(0), DATABASE)
        query("""
INSERT INTO consultationRequests (requestId,demographicNo,lastUpdateDate) VALUES (1,42,NOW());
INSERT INTO consultationResponse (responseId,demographicNo) VALUES (1,42);
INSERT INTO eform_data (fdid,demographic_no,showLatestFormOnly,patient_independent) VALUES (1,42,0,0);
INSERT INTO patientLabRouting (lab_no,lab_type,demographic_no,created) VALUES
 (10,'HL7',42,NOW()), (11,'MDS',42,NOW()),
 (12,'HL7',42,NOW()), (12,'MDS',42,NOW()),
 (13,'HL7',43,NOW()), (13,'MDS',42,NOW()),
 (14,'DOC',42,NOW()), (16,'HL7',43,NOW()),
 (17,'HL7',42,NOW()), (17,'HL7',42,NOW());
""", DATABASE)
        for table, parent, number, kind, date, provider in ATTACHMENTS:
            for lab in range(10, 18):
                query(f"INSERT INTO `{table}` (id,`{parent}`,`{number}`,`{kind}`,`{date}`,`{provider}`) VALUES ({lab},1,{lab},'L','2026-01-02','999998')", DATABASE)
            query(f"INSERT INTO `{table}` (id,`{parent}`,`{number}`,`{kind}`,deleted,`{provider}`) VALUES (20,1,10,'L','Y','999998'),(21,1,10,'D',NULL,'999998')", DATABASE)
        query(migration, DATABASE)
        for table, parent, number, kind, date, provider in ATTACHMENTS:
            expect(f"SELECT id,IFNULL(lab_type,'UNRESOLVED') FROM `{table}` ORDER BY id",
                   '10\tHL7\n11\tMDS\n12\tUNRESOLVED\n13\tMDS\n14\tUNRESOLVED\n15\tUNRESOLVED\n16\tUNRESOLVED\n17\tHL7\n20\tHL7\n21\tUNRESOLVED')
            expect(f"SELECT deleted FROM `{table}` WHERE id=20", 'Y')
            expect(f"SELECT COUNT(*) FROM `{table}` WHERE `{provider}`='999998'", '10')
            expect(f"SELECT COUNT(*) FROM `{table}` WHERE `{date}`='2026-01-02'", '8')
            # A confirmed choice must survive a later rerun, even while routing is ambiguous.
            query(f"UPDATE `{table}` SET lab_type='MDS' WHERE id=12", DATABASE)
        query("INSERT INTO patientLabRouting (lab_no,lab_type,demographic_no,created) VALUES (11,'HL7',42,NOW())", DATABASE)
        snapshots = {table: query(f'SELECT * FROM `{table}` ORDER BY id', DATABASE) for table, *_ in ATTACHMENTS}
        query(migration, DATABASE)
        for table, before in snapshots.items():
            assert query(f'SELECT * FROM `{table}` ORDER BY id', DATABASE) == before, f'Rerun changed {table}'
        print('PASS: all 3 attachment stores preserve unique patient sources, ambiguous/missing/foreign rows, deleted flags, metadata, confirmed choices and rerun idempotence')
    finally:
        query(f'DROP DATABASE `{DATABASE}`')


if __name__ == '__main__':
    main()
