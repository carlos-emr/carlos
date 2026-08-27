-- Ensure tables referenced by the outbound email archive foreign keys are
-- InnoDB before V1.0.16__outbound_email_archive.sql runs.
--
-- Fresh installs on current CARLOS database configuration already inherit
-- InnoDB. This migration is for upgraded legacy installs where one of these
-- existing tables may still be MyISAM; MyISAM tables cannot participate in
-- InnoDB foreign key constraints.
--
-- Operator diagnostic:
--   SELECT TABLE_NAME, ENGINE
--   FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE()
--     AND TABLE_NAME IN ('document', 'emailConfig', 'emailLog');
--
-- If any row reports a non-InnoDB engine, this migration will rebuild that
-- table. Schedule the update accordingly on large legacy databases.

SET @document_innodb_sql = 'ALTER TABLE `document` ENGINE=InnoDB';
SET @email_config_innodb_sql = 'ALTER TABLE `emailConfig` ENGINE=InnoDB';
SET @email_log_innodb_sql = 'ALTER TABLE `emailLog` ENGINE=InnoDB';

SET @missing_outbound_archive_reference_table = NULL;
SELECT required_reference_tables.table_name
  INTO @missing_outbound_archive_reference_table
  FROM (
    SELECT 1 AS sort_order, 'document' AS table_name
    UNION ALL SELECT 2, 'emailConfig'
    UNION ALL SELECT 3, 'emailLog'
  ) required_reference_tables
  LEFT JOIN information_schema.TABLES actual_tables
    ON actual_tables.TABLE_SCHEMA = DATABASE()
   AND actual_tables.TABLE_NAME = required_reference_tables.table_name
   AND actual_tables.TABLE_TYPE = 'BASE TABLE'
 WHERE actual_tables.TABLE_NAME IS NULL
 ORDER BY required_reference_tables.sort_order
 LIMIT 1;

-- Fail before any engine conversion if a required table is missing. The failure
-- branch alters the required table that failed preflight, avoiding sentinel
-- names that could collide with existing schema contents while keeping the
-- migration free of persistent helper routines.
SET @outbound_archive_reference_engine_sql = CASE
  WHEN @missing_outbound_archive_reference_table = 'document'
    THEN @document_innodb_sql
  WHEN @missing_outbound_archive_reference_table = 'emailConfig'
    THEN @email_config_innodb_sql
  WHEN @missing_outbound_archive_reference_table = 'emailLog'
    THEN @email_log_innodb_sql
  ELSE 'SELECT ''outbound archive reference tables exist'' AS message'
END;
PREPARE outbound_archive_reference_engine_stmt FROM @outbound_archive_reference_engine_sql;
EXECUTE outbound_archive_reference_engine_stmt;
DEALLOCATE PREPARE outbound_archive_reference_engine_stmt;

-- Keep the three conversions explicit: MariaDB prepared statements execute one
-- statement at a time, and introducing a stored routine solely to loop over
-- three tables would make this migration depend on client delimiter handling.
SET @document_engine = NULL;
SELECT `ENGINE`
  INTO @document_engine
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'document'
   AND TABLE_TYPE = 'BASE TABLE'
 LIMIT 1;

SET @outbound_archive_reference_engine_sql = IF(
  @document_engine IS NULL OR UPPER(@document_engine) <> 'INNODB',
  @document_innodb_sql,
  'SELECT ''document already InnoDB'' AS message'
);
PREPARE outbound_archive_reference_engine_stmt FROM @outbound_archive_reference_engine_sql;
EXECUTE outbound_archive_reference_engine_stmt;
DEALLOCATE PREPARE outbound_archive_reference_engine_stmt;

SET @email_config_engine = NULL;
SELECT `ENGINE`
  INTO @email_config_engine
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'emailConfig'
   AND TABLE_TYPE = 'BASE TABLE'
 LIMIT 1;

SET @outbound_archive_reference_engine_sql = IF(
  @email_config_engine IS NULL OR UPPER(@email_config_engine) <> 'INNODB',
  @email_config_innodb_sql,
  'SELECT ''emailConfig already InnoDB'' AS message'
);
PREPARE outbound_archive_reference_engine_stmt FROM @outbound_archive_reference_engine_sql;
EXECUTE outbound_archive_reference_engine_stmt;
DEALLOCATE PREPARE outbound_archive_reference_engine_stmt;

SET @email_log_engine = NULL;
SELECT `ENGINE`
  INTO @email_log_engine
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'emailLog'
   AND TABLE_TYPE = 'BASE TABLE'
 LIMIT 1;

SET @outbound_archive_reference_engine_sql = IF(
  @email_log_engine IS NULL OR UPPER(@email_log_engine) <> 'INNODB',
  @email_log_innodb_sql,
  'SELECT ''emailLog already InnoDB'' AS message'
);
PREPARE outbound_archive_reference_engine_stmt FROM @outbound_archive_reference_engine_sql;
EXECUTE outbound_archive_reference_engine_stmt;
DEALLOCATE PREPARE outbound_archive_reference_engine_stmt;

SET @missing_outbound_archive_reference_table = NULL;
SET @outbound_archive_reference_engine_sql = NULL;
SET @document_innodb_sql = NULL;
SET @email_config_innodb_sql = NULL;
SET @email_log_innodb_sql = NULL;
SET @document_engine = NULL;
SET @email_config_engine = NULL;
SET @email_log_engine = NULL;
