#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later.
"""Verify archive DDL, real constraints, defaults, and grants on disposable schemas.

Run against a test MariaDB server only (MYSQL_PWD supplies the password).
Uses the real baseline reference definitions and both archive migrations.
Only uniquely named fixture schemas created by this process are changed.
"""
import argparse
import re

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = spec_from_file_location('archive_engines', ROOT / 'scripts/verify-outbound-archive-engines.py')
ENGINES = module_from_spec(SPEC)
SPEC.loader.exec_module(ENGINES)

TABLE_COLUMNS = {
    'outboundEmailArchive': 'id emailLogId demographicNo providerNo configId documentNo artifactType transportType providerName providerMessageId providerResponse contentType fileName originalFileName sha256Hash byteSize storageType retentionPolicy legalHold deleted sendStatus archivedAt sendAttemptedAt sentAt deletedAt deletedByProviderNo deleteReason lastUpdateUser lastUpdateDate',
    'outboundEmailArchiveAttachment': 'id archiveId documentNo fileName contentType sha256Hash byteSize sourceDocumentType sourceDocumentId createdAt lastUpdateUser lastUpdateDate',
    'outboundEmailArchiveDeletion': 'id archiveId emailLogId demographicNo documentNo fileName contentType sha256Hash byteSize deletedByProviderNo deletedAt deleteReason lastUpdateUser lastUpdateDate',
    'outboundEmailArchiveLegalHoldEvent': 'id archiveId action providerNo reason eventAt lastUpdateUser lastUpdateDate',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    checks = ENGINES.DatabaseChecks(parser.parse_args())
    baseline = (ENGINES.MIGRATIONS / 'V1__baseline_schema.sql').read_text()
    paths = list(ENGINES.MIGRATIONS.glob('V*__outbound_email_archive.sql'))
    if len(paths) != 1:
        raise AssertionError('Expected exactly one archive schema migration')
    migration = paths[0].read_text()
    for existing_grant in (None, 'o', 'r', 'x'):
        with checks.fixture(('InnoDB',) * 3) as database:
            def sql(statement, error=None):
                # Assert nullability with strict DML even when the baseline bootstrap
                # server uses the project's legacy-compatible SQL mode.
                return checks.sql("SET SESSION sql_mode='STRICT_ALL_TABLES';" + statement, database, error)

            for table in ('secObjectName', 'secObjPrivilege'):
                definition = re.search(r'CREATE TABLE `' + table + r'` \([\s\S]+?;', baseline)
                if definition is None:
                    raise AssertionError('Missing baseline table: ' + table)
                sql(definition.group())
            if existing_grant is not None:
                sql("INSERT INTO secObjectName VALUES ('_admin.edocdelete','Clinic description',0);")
                sql("INSERT INTO secObjPrivilege VALUES ('admin','_admin.edocdelete','"
                    + existing_grant + "',7,'999998');")
            sql(checks.migration)
            sql(migration)
            for table, columns in TABLE_COLUMNS.items():
                actual = sql("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE "
                             "TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "'")
                assert set(actual.splitlines()) == set(columns.split()), (table, actual)
                assert sql("SELECT ENGINE FROM information_schema.TABLES WHERE "
                           "TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "'") == 'InnoDB'
            assert sql("SELECT privilege FROM secObjPrivilege WHERE roleUserGroup='admin' "
                       "AND objectName='_admin.edocdelete'") == (existing_grant or 'x')
            assert sql("SELECT COUNT(*) FROM secObjPrivilege WHERE objectName='_admin.edocdelete'") == '1'
            if existing_grant is not None:
                assert sql("SELECT priority,provider_no FROM secObjPrivilege WHERE "
                           "objectName='_admin.edocdelete'") == '7\t999998'
                assert sql("SELECT description FROM secObjectName WHERE "
                           "objectName='_admin.edocdelete'") == 'Clinic description'
            else:
                assert sql("SELECT priority,provider_no IS NULL FROM secObjPrivilege WHERE "
                           "objectName='_admin.edocdelete'") == '0\t1'

            sql("INSERT INTO document(document_no,restrictToProgram) VALUES (7,0);"
                "INSERT INTO emailConfig(id) VALUES (8); INSERT INTO emailLog(id,configId) VALUES (9,8);")
            sql("INSERT INTO outboundEmailArchive(id,emailLogId,demographicNo,configId,documentNo,"
                "artifactType,transportType,contentType,fileName,sha256Hash,byteSize,lastUpdateUser) "
                "VALUES (1,9,123,8,7,'SMTP_RFC822','SMTP','message/rfc822','message.eml',REPEAT('a',64),4294967296,'999998')")
            assert sql('SELECT legalHold,deleted,storageType,retentionPolicy,sendStatus,byteSize '
                       'FROM outboundEmailArchive') == '1\t0\tEDOC\tPERMANENT\tARCHIVED\t4294967296'
            sql("INSERT INTO outboundEmailArchiveAttachment(id,archiveId,documentNo,fileName,sha256Hash,"
                "byteSize,lastUpdateUser) VALUES (2,1,7,'report.pdf',REPEAT('b',64),1,'999998')")
            # Metadata-only attachments deliberately have no eDoc reference.
            sql("INSERT INTO outboundEmailArchiveAttachment(archiveId,fileName,sha256Hash,byteSize,lastUpdateUser) "
                "VALUES (1,'external.pdf',REPEAT('c',64),0,'999998')")
            tombstone = ("INSERT INTO outboundEmailArchiveDeletion(archiveId,emailLogId,demographicNo,documentNo,"
                         "fileName,sha256Hash,byteSize,deletedByProviderNo,deleteReason,lastUpdateUser) "
                         "VALUES (1,9,123,7,'message.eml',REPEAT('a',64),4294967296,'999998',REPEAT('é',1000),'999998')")
            sql(tombstone)
            sql(tombstone, error='1062')
            assert sql('SELECT CHAR_LENGTH(deleteReason),byteSize FROM outboundEmailArchiveDeletion') == '1000\t4294967296'
            sql("INSERT INTO outboundEmailArchiveLegalHoldEvent(id,archiveId,action,providerNo,reason,lastUpdateUser) "
                "VALUES (3,1,'RELEASED','999998','Counsel approved','999998')")
            # Exercise every declared FK rather than merely counting metadata entries.
            for table, column in [('outboundEmailArchive', c) for c in ('emailLogId','configId','documentNo')] + [
                    ('outboundEmailArchiveAttachment','archiveId'), ('outboundEmailArchiveAttachment','documentNo'),
                    ('outboundEmailArchiveDeletion','archiveId'), ('outboundEmailArchiveDeletion','emailLogId'),
                    ('outboundEmailArchiveLegalHoldEvent','archiveId')]:
                sql('UPDATE ' + table + ' SET ' + column + '=999999', error='1452')
            sql('DELETE FROM document WHERE document_no=7', error='1451')
            sql('DELETE FROM outboundEmailArchive WHERE id=1', error='1451')
            sql('UPDATE outboundEmailArchive SET documentNo=NULL', error='1048')
            sql('UPDATE outboundEmailArchive SET contentType=NULL', error='1048')
            sql('UPDATE outboundEmailArchiveDeletion SET documentNo=NULL', error='1048')
            before = [sql('SELECT * FROM ' + table) for table in TABLE_COLUMNS]
            definitions = [sql('SHOW CREATE TABLE ' + table) for table in TABLE_COLUMNS]
            sql(migration)
            assert before == [sql('SELECT * FROM ' + table) for table in TABLE_COLUMNS]
            assert definitions == [sql('SHOW CREATE TABLE ' + table) for table in TABLE_COLUMNS]
        print('PASS archive schema, constraints, defaults, retry; existing grant:', existing_grant)


if __name__ == '__main__':
    main()
