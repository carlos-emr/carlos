-- A linked patient's baseline first_name(30), comma and last_name(30) can exceed
-- the old appointment.name(50). Preserve the complete name in live/history rows.
-- DDL commits separately on MariaDB: each table independently skips an already
-- widened column so rerunning after the first ALTER preserves its data/attributes.
-- MariaDB COLUMN_DEFAULT is a quoted literal or SQL expression, not raw text;
-- append that server-supplied expression verbatim to retain NULL/string defaults.
-- Keep each column's charset, collation, nullability, default, comment and EXTRA.
-- Generated or unexpected narrow column types fail explicitly instead of losing
-- their semantics. Already-wider adopted text columns are left entirely intact.
SET @appointment_name_ddl = COALESCE((
    SELECT CASE
        WHEN character_maximum_length >= 255 THEN 'SELECT 1'
        WHEN data_type <> 'varchar' OR COALESCE(generation_expression, '') <> ''
            THEN 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''Unsupported narrow appointment.name column'''
        ELSE CONCAT('ALTER TABLE appointment MODIFY COLUMN name VARCHAR(255) CHARACTER SET ',
            character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
            IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
            ' COMMENT ', QUOTE(column_comment),
            IF(extra = '', '', CONCAT(' ', extra)))
        END
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'name'
), 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''Missing appointment.name column''');
PREPARE appointment_name_statement FROM @appointment_name_ddl;
EXECUTE appointment_name_statement;
DEALLOCATE PREPARE appointment_name_statement;

SET @appointment_archive_name_ddl = COALESCE((
    SELECT CASE
        WHEN character_maximum_length >= 255 THEN 'SELECT 1'
        WHEN data_type <> 'varchar' OR COALESCE(generation_expression, '') <> ''
            THEN 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''Unsupported narrow appointmentArchive.name column'''
        ELSE CONCAT('ALTER TABLE appointmentArchive MODIFY COLUMN name VARCHAR(255) CHARACTER SET ',
            character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
            IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
            ' COMMENT ', QUOTE(column_comment),
            IF(extra = '', '', CONCAT(' ', extra)))
        END
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointmentArchive' AND column_name = 'name'
), 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''Missing appointmentArchive.name column''');
PREPARE appointment_archive_name_statement FROM @appointment_archive_name_ddl;
EXECUTE appointment_archive_name_statement;
DEALLOCATE PREPARE appointment_archive_name_statement;
