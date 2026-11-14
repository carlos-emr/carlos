#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""Exercise interrupted unpublished CDS DDL on the harness's newly owned MariaDB."""
import hashlib
from pathlib import Path


def verify_export_migration_reapply(repository: Path, query):
    """The caller supplies the isolated fixture root connection, never a deployed database."""
    folder = repository / 'database/mysql/migration/common'
    names = ('V1.0.59.40__cds_native_field_baselines.sql',
             'V1.0.59.41__cds_export_ledger.sql',
             'V1.0.59.42__cds_export_artifact_attempts.sql',
             'V1.0.59.44__index_cds_hrm_patient_scope.sql',
             'V1.0.59.45__cds_export_held_patients.sql',
             'V1.0.59.46__index_measurements_deleted_owners.sql',
             'V1.0.59.47__index_cds_laboratory_result_native_lab.sql')
    migrations = [folder.joinpath(name).read_text() for name in names]
    def first_statement(sql):
        # Semicolons in explanatory comments are not SQL statement boundaries.
        return '\n'.join(line for line in sql.splitlines() if not line.lstrip().startswith('--')).split(';', 1)[0] + ';'
    database = 'cds_export_reapply_fixture'
    # Deliberately refuse reuse: the enclosing harness owns a fresh MariaDB server.
    query('CREATE DATABASE ' + database + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_bin')
    def execute(sql):
        return query('USE ' + database + ';\n' + sql)
    try:
        execute('CREATE TABLE cds_import_unit (id INT PRIMARY KEY);'
                'CREATE TABLE cds_import_checkpoint (id INT PRIMARY KEY);'
                "INSERT INTO cds_import_unit VALUES (1); INSERT INTO cds_import_checkpoint VALUES (2);"
                'CREATE TABLE secObjectName (objectName VARCHAR(80), description VARCHAR(255), orgapplicable INT);'
                'CREATE TABLE secObjPrivilege (roleUserGroup VARCHAR(80),objectName VARCHAR(80),privilege VARCHAR(80),priority INT);'
                "INSERT INTO secObjPrivilege VALUES ('admin','_cds_export','|0|',0);")
        # Crash after the first implicit-commit ALTER, retaining already captured evidence.
        execute(first_statement(migrations[0]))
        execute("UPDATE cds_import_unit SET native_baseline='preserved native witness'")
        execute(migrations[0]); execute(migrations[0])
        if execute("SELECT CONCAT(COUNT(*),':',SUM(data_type='longtext' AND is_nullable='YES')) "
                   "FROM information_schema.columns WHERE table_schema=DATABASE() "
                   "AND table_name IN ('cds_import_unit','cds_import_checkpoint') AND column_name='native_baseline'") != ['2:2']:
            raise RuntimeError('Partial native-witness DDL did not recover exactly')
        if execute('SELECT native_baseline FROM cds_import_unit') != ['preserved native witness']:
            raise RuntimeError('Migration replay replaced a native witness')
        # Crash after creating only the run table, before its index/event/privilege steps.
        execute(first_statement(migrations[1]))
        execute(migrations[1]); execute(migrations[1])
        execute("INSERT INTO cds_export_run "
                "(id,submission_key,operator,provider_no,request_document,request_digest,compatibility_digest,state,"
                "owner_epoch,cancel_requested,processed_patients,blocked_patients,created_at,updated_at,revision) VALUES "
                "('00000000-0000-4000-8000-000000000001','one','unix:fixture','100','{}',REPEAT('a',64),REPEAT('b',64),'FAILED',1,0,0,0,NOW(6),NOW(6),0),"
                "('00000000-0000-4000-8000-000000000002','two','unix:fixture','100','{}',REPEAT('a',64),REPEAT('b',64),'FAILED',2,0,0,0,NOW(6),NOW(6),0)")
        # Crash after CREATE and one existing attempt. Replay must preserve that row
        # and backfill only the missing run/epoch, rather than inserting fresh UUIDs.
        execute(first_statement(migrations[2]))
        execute("INSERT INTO cds_export_attempt "
                "(id,run_id,epoch,request_digest,state,context_document,created_at,updated_at,expires_at,revision) "
                "VALUES ('00000000-0000-4000-8000-000000000003','00000000-0000-4000-8000-000000000001',1,"
                "REPEAT('a',64),'CAPTURING','preserved attempt',NOW(6),NOW(6),NOW(6),0)")
        execute(migrations[2]); execute(migrations[2])
        if execute("SELECT CONCAT(COUNT(*),':',SUM(state='LEGACY_UNVERIFIED'),':',SUM(context_document='preserved attempt')) FROM cds_export_attempt") != ['2:1:1']:
            raise RuntimeError('Attempt replay lost evidence or duplicated a generation')
        if execute("SELECT privilege FROM secObjPrivilege WHERE roleUserGroup='admin' AND objectName='_cds_export'") != ['|0|']:
            raise RuntimeError('Migration replay changed an explicit privilege denial')
        if execute("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() "
                   "AND table_name='cds_export_run' AND index_name='idx_cds_export_history'") != ['3']:
            raise RuntimeError('Partial history-index DDL did not recover')
        execute('CREATE TABLE HRMDocumentToDemographic (id INT PRIMARY KEY, demographicNo VARCHAR(20) NULL);'
                "INSERT INTO HRMDocumentToDemographic VALUES (1,'00027suffix'),(2,'  +27suffix'),"
                "(3,'garbage'),(4,NULL),(5,'99999999999999999999');")
        original = execute('SELECT id,HEX(demographicNo) FROM HRMDocumentToDemographic ORDER BY id')
        # Simulate the virtual column having committed before its index was created.
        execute("ALTER TABLE HRMDocumentToDemographic ADD COLUMN cds_patient_key DECIMAL(20,0) "
                "GENERATED ALWAYS AS (CAST(NULLIF(REGEXP_SUBSTR(demographicNo, "
                "'^[[:space:]]*[+]?[0-9]+'), '') AS DECIMAL(20,0))) VIRTUAL INVISIBLE")
        execute(migrations[3]); execute(migrations[3])
        if execute('SELECT id,HEX(demographicNo) FROM HRMDocumentToDemographic ORDER BY id') != original:
            raise RuntimeError('HRM patient index replay changed a source owner')
        execute("INSERT INTO HRMDocumentToDemographic VALUES(6,'28')")
        if execute('SELECT id,demographicNo FROM HRMDocumentToDemographic WHERE cds_patient_key=28') != ['6\t28']:
            raise RuntimeError('HRM patient index broke a legacy positional insert')
        if execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                   "AND table_name='HRMDocumentToDemographic' AND column_name='cds_patient_key' "
                   "AND extra='VIRTUAL GENERATED, INVISIBLE'") != ['1']:
            raise RuntimeError('HRM derived key is not invisible to legacy row shapes')
        if execute('SELECT id FROM HRMDocumentToDemographic WHERE cds_patient_key=27 ORDER BY id') != ['1', '2']:
            raise RuntimeError('HRM patient index lost a numeric alias')
        if execute("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() "
                   "AND table_name='HRMDocumentToDemographic' AND index_name='idx_cds_hrm_patient_key' "
                   "AND column_name='cds_patient_key'") != ['1']:
            raise RuntimeError('HRM patient index did not recover after partial DDL')
        execute(migrations[4]); execute(migrations[4])
        execute("INSERT INTO cds_export_held_patient (id,run_id,epoch,demographic_no,safe_code,created_at) "
                "VALUES ('00000000-0000-4000-8000-0000000000a1','00000000-0000-4000-8000-000000000001',1,27,"
                "'CDS_EXPORT_UNMAPPED_DATA',NOW(6))")
        execute(migrations[4])
        if execute('SELECT COUNT(*) FROM cds_export_held_patient') != ['1']:
            raise RuntimeError('Held-patient DDL replay changed recorded held patients')
        # measurementsDeleted indexes (#6200): crash after the first ALTER committed; the replay
        # adds only the other one, changes no row and refuses a same-name index of another shape.
        execute('CREATE TABLE measurementsDeleted (id INT UNSIGNED PRIMARY KEY, '
                'demographicNo INT NOT NULL DEFAULT 0, originalId INT UNSIGNED NOT NULL, '
                'dataField MEDIUMTEXT NOT NULL);'
                "INSERT INTO measurementsDeleted VALUES (1,27,7,'75'),(2,28,7,'76'),(3,27,8,'170');")
        rows = 'SELECT id,demographicNo,originalId,HEX(dataField) FROM measurementsDeleted ORDER BY id'
        deleted = execute(rows)
        execute('ALTER TABLE measurementsDeleted ADD INDEX idx_measurementsDeleted_demographicNo (demographicNo)')
        execute(migrations[5]); execute(migrations[5])
        indexes = ("SELECT index_name,GROUP_CONCAT(column_name ORDER BY seq_in_index) "
                   "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                   "AND table_name='measurementsDeleted' AND index_name<>'PRIMARY' "
                   "GROUP BY index_name ORDER BY index_name")
        if execute(indexes) != ['idx_measurementsDeleted_demographicNo\tdemographicNo',
                                'idx_measurementsDeleted_originalId_demographicNo\toriginalId,demographicNo']:
            raise RuntimeError('Partial measurementsDeleted index DDL did not recover exactly')
        if execute(rows) != deleted:
            raise RuntimeError('measurementsDeleted index replay changed a deleted version')
        execute('ALTER TABLE measurementsDeleted DROP INDEX idx_measurementsDeleted_demographicNo, '
                'ADD INDEX idx_measurementsDeleted_demographicNo (originalId)')
        try:
            execute(migrations[5])
            refused = False
        except Exception:
            refused = True
        if not refused:
            raise RuntimeError('A same-name measurementsDeleted index of another shape was accepted')
        # cds_laboratory_result index by local report: a replay changes nothing, no imported
        # result is changed, and a same-name index of another shape is refused.
        execute('CREATE TABLE cds_laboratory_result (id CHAR(64) PRIMARY KEY, demographicNo INT NOT NULL, '
                'nativeLabNo INT NULL, resultText MEDIUMTEXT NULL);'
                "INSERT INTO cds_laboratory_result VALUES ('a',27,7,'6.1'),('b',27,NULL,'7.2'),('c',28,8,'5.0');")
        results = 'SELECT id,demographicNo,COALESCE(nativeLabNo,-1),HEX(resultText) FROM cds_laboratory_result ORDER BY id'
        imported = execute(results)
        execute(migrations[6]); execute(migrations[6])
        laboratory_indexes = ("SELECT index_name,GROUP_CONCAT(column_name ORDER BY seq_in_index) "
                              "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                              "AND table_name='cds_laboratory_result' AND index_name<>'PRIMARY' "
                              "GROUP BY index_name ORDER BY index_name")
        if execute(laboratory_indexes) != [
                'idx_cds_laboratory_result_nativeLabNo_demographicNo\tnativeLabNo,demographicNo']:
            raise RuntimeError('cds_laboratory_result index replay did not end with exactly its index')
        if execute(results) != imported:
            raise RuntimeError('cds_laboratory_result index replay changed an imported result')
        execute('ALTER TABLE cds_laboratory_result DROP INDEX idx_cds_laboratory_result_nativeLabNo_demographicNo, '
                'ADD INDEX idx_cds_laboratory_result_nativeLabNo_demographicNo (demographicNo)')
        try:
            execute(migrations[6])
            refused = False
        except Exception:
            refused = True
        if not refused:
            raise RuntimeError('A same-name cds_laboratory_result index of another shape was accepted')
        return {'protocol': 1, 'status': 'PARTIAL_DDL_REPLAY_PASSED',
                'nativeWitnessPreserved': True, 'existingAttemptPreserved': True,
                'missingAttemptBackfilledOnce': True, 'explicitDenialPreserved': True,
                'hrmSourceOwnersPreserved': True, 'hrmPatientIndexRecovered': True,
                'hrmLegacyInsertCompatible': True, 'heldPatientsPreserved': True,
                'measurementsDeletedIndexesRecovered': True,
                'laboratoryResultIndexReplayed': True,
                'migrations': {name: hashlib.sha256(folder.joinpath(name).read_bytes()).hexdigest() for name in names}}
    finally:
        query('DROP DATABASE ' + database)
