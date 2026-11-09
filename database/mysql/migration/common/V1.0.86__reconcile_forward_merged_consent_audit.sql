-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Reconcile develop-first and release-first consent-audit shapes without changing
-- or deleting any recorded consent or audit value. Released SQL stays frozen.
-- The guarded pre-migration callback supplies recorded_at's default before release
-- 59.30 runs on develop 77's table; it never fabricates audit rows.
-- Refuse unknown shapes before any DDL, including when a caller uses non-strict
-- sql_mode. In particular, never narrow BIGINT audit identities or DATETIME(6) values.
SET @carlos_consent_audit_86_shape = (
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit'
        AND TABLE_TYPE='BASE TABLE' AND ENGINE='InnoDB') = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit') IN (7,10)
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit'
            AND COLUMN_NAME IN ('consent_id','migration','reason','prior_explicit','prior_optout','prior_deleted','recorded_at')) = 7
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit'
           AND NOT COALESCE((
             ((COLUMN_NAME='consent_id' AND COLUMN_TYPE IN ('int(10)','int(11)'))
               OR (COLUMN_NAME='migration' AND COLUMN_TYPE='varchar(64)')
               OR (COLUMN_NAME='reason' AND COLUMN_TYPE='varchar(32)')
               OR (COLUMN_NAME IN ('prior_explicit','prior_optout','prior_deleted') AND COLUMN_TYPE='tinyint(1)')
               OR (COLUMN_NAME='recorded_at' AND COLUMN_TYPE='datetime')
               OR (COLUMN_NAME='id' AND COLUMN_TYPE='bigint(20)')
               OR (COLUMN_NAME='lastUpdateUser' AND COLUMN_TYPE='varchar(100)')
               OR (COLUMN_NAME='lastUpdateDate' AND COLUMN_TYPE='timestamp'))
             AND IS_NULLABLE=IF(COLUMN_NAME IN ('prior_explicit','prior_optout','prior_deleted'),'YES','NO')
             AND EXTRA=IF(COLUMN_NAME='id','auto_increment','')
             AND (CHARACTER_SET_NAME IS NULL OR CHARACTER_SET_NAME='utf8mb4')
             AND (COLLATION_NAME IS NULL OR COLLATION_NAME='utf8mb4_general_ci')
             AND ((COLUMN_NAME IN ('prior_explicit','prior_optout','prior_deleted') AND (COLUMN_DEFAULT IS NULL OR COLUMN_DEFAULT='NULL'))
               OR (COLUMN_NAME='recorded_at' AND (COLUMN_DEFAULT IS NULL OR COLUMN_DEFAULT='current_timestamp()'))
               OR (COLUMN_NAME='lastUpdateDate' AND COLUMN_DEFAULT='current_timestamp()')
               OR (COLUMN_NAME='lastUpdateUser' AND COLUMN_DEFAULT=QUOTE('system'))
               OR (COLUMN_NAME IN ('id','consent_id','migration','reason') AND COLUMN_DEFAULT IS NULL))),0))
    AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
           FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit' AND INDEX_NAME='PRIMARY')
        IN ('consent_id,migration,reason','id')
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit' AND INDEX_NAME='uq_consent_migration_reason') IN (0,3)
    AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit' AND INDEX_NAME='uq_consent_migration_reason'
           AND (NON_UNIQUE<>0 OR SUB_PART IS NOT NULL))
    AND COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent_migration_audit' AND INDEX_NAME='uq_consent_migration_reason'),
        'consent_id,migration,reason')='consent_id,migration,reason'
    AND NOT EXISTS (SELECT 1 FROM information_schema.TRIGGERS
         WHERE EVENT_OBJECT_SCHEMA=DATABASE() AND LOWER(EVENT_OBJECT_TABLE)='consent_migration_audit')
    AND NOT EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE REFERENCED_TABLE_SCHEMA=DATABASE() AND LOWER(REFERENCED_TABLE_NAME)='consent_migration_audit')
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent' AND COLUMN_NAME='deleted'
            AND COLUMN_TYPE='tinyint(1)' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IN ('0','1')) = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='consent' AND COLUMN_NAME='live_demographic_no'
            AND COLUMN_TYPE IN ('int(10)','int(11)') AND EXTRA LIKE 'STORED GENERATED%'
            AND GENERATION_EXPRESSION='if(`deleted` = 0,`demographic_no`,NULL)') = 1
);
SET @carlos_consent_audit_86_guard = IF(COALESCE(@carlos_consent_audit_86_shape,0)=1,
    'DO 0', 'SELECT carlos_consent_audit_shape_requires_review_before_upgrade');
PREPARE carlos_consent_audit_86 FROM @carlos_consent_audit_86_guard;
EXECUTE carlos_consent_audit_86;
DEALLOCATE PREPARE carlos_consent_audit_86;

ALTER TABLE Consent_migration_audit
    ADD COLUMN IF NOT EXISTS id BIGINT NOT NULL AUTO_INCREMENT FIRST,
    MODIFY COLUMN consent_id INT NOT NULL,
    MODIFY COLUMN recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system',
    ADD COLUMN IF NOT EXISTS lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id),
    ADD UNIQUE KEY IF NOT EXISTS uq_consent_migration_reason (consent_id, migration, reason);

-- Keep develop 77's final default in both histories; existing decisions stay untouched.
ALTER TABLE Consent
    MODIFY COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN live_demographic_no INT GENERATED ALWAYS AS (IF(deleted = 0, demographic_no, NULL)) STORED;

SET @carlos_consent_audit_86_shape = NULL;
SET @carlos_consent_audit_86_guard = NULL;
