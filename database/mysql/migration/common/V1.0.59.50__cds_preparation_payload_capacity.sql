-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- A CDS import keeps each unit's prepared native record in cds_import_preparation.payload between
-- preparation and write, and the CDS export reads it back when it exports an imported chart.
-- V1.0.59.14 gave that MEDIUMTEXT column a check holding a record to 1,048,576 bytes. The number
-- was arbitrary: a note whose prepared record did not fit was stored as the documented placeholder,
-- its text only in the import's source fields, so a CARLOS-to-CARLOS transfer changed it.
-- The check is removed; the only bound left is the column's own capacity (MEDIUMTEXT,
-- 16,777,215 bytes, or a wider type an installation adopted).
-- No row is changed and nothing accepted before is refused. The column keeps its type, character
-- set, collation, nullability and comment.
-- MariaDB names a column-level check after its column and cannot drop it with DROP CONSTRAINT: the
-- column is redefined without it. A table-level check of the same clause is dropped by its name.
-- The check is found by its clause, so a replay after it was removed changes nothing. Any other
-- check that names the column is an explicit upgrade blocker, never silently dropped.
DROP PROCEDURE IF EXISTS carlos_cds_preparation_capacity_1_0_59_50;
DELIMITER $$
CREATE PROCEDURE carlos_cds_preparation_capacity_1_0_59_50()
SQL SECURITY INVOKER
body: BEGIN
    DECLARE table_count INTEGER;
    DECLARE other_count INTEGER;
    DECLARE column_signature TEXT;
    DECLARE check_name VARCHAR(64);
    DECLARE check_level VARCHAR(16);
    SELECT COUNT(*) INTO table_count FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_name='cds_import_preparation' AND table_type='BASE TABLE';
    IF table_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve missing cds_import_preparation table before upgrade';
    END IF;
    SELECT CONCAT(data_type, ':', is_nullable, ':', COALESCE(generation_expression, '-'))
      INTO column_signature
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='cds_import_preparation' AND column_name='payload';
    IF column_signature IS NULL
       OR column_signature NOT IN ('mediumtext:NO:-', 'longtext:NO:-') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Review cds_import_preparation.payload before upgrade';
    END IF;
    -- The clause V1.0.59.14 wrote, compared without spaces, parentheses or letter case.
    SELECT COUNT(*) INTO other_count FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE() AND table_name='cds_import_preparation'
        AND LOCATE('`payload`', LOWER(check_clause)) > 0
        AND REPLACE(REPLACE(REPLACE(LOWER(check_clause), ' ', ''), '(', ''), ')', '')
            <> 'octet_length`payload`<=1048576';
    IF other_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Review the checks on cds_import_preparation.payload before upgrade';
    END IF;
    removal: LOOP
        SET check_name = NULL, check_level = NULL;
        SELECT constraint_name, level INTO check_name, check_level
          FROM information_schema.check_constraints
          WHERE constraint_schema=DATABASE() AND table_name='cds_import_preparation'
            AND REPLACE(REPLACE(REPLACE(LOWER(check_clause), ' ', ''), '(', ''), ')', '')
                = 'octet_length`payload`<=1048576'
          ORDER BY constraint_name LIMIT 1;
        IF check_name IS NULL THEN
            LEAVE removal;
        END IF;
        IF check_level = 'Column' THEN
            SELECT CONCAT('ALTER TABLE `cds_import_preparation` MODIFY COLUMN `payload` ', column_type,
                    ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name, ' NOT NULL',
                    ' COMMENT ', QUOTE(column_comment))
              INTO @carlos_cds_preparation_capacity_ddl
              FROM information_schema.columns
              WHERE table_schema=DATABASE() AND table_name='cds_import_preparation' AND column_name='payload';
        ELSE
            SET @carlos_cds_preparation_capacity_ddl=CONCAT('ALTER TABLE `cds_import_preparation` DROP CONSTRAINT `',
                REPLACE(check_name, '`', '``'), '`');
        END IF;
        PREPARE carlos_cds_preparation_capacity_statement FROM @carlos_cds_preparation_capacity_ddl;
        EXECUTE carlos_cds_preparation_capacity_statement;
        DEALLOCATE PREPARE carlos_cds_preparation_capacity_statement;
    END LOOP removal;
END$$
DELIMITER ;

CALL carlos_cds_preparation_capacity_1_0_59_50();

DROP PROCEDURE carlos_cds_preparation_capacity_1_0_59_50;
SET @carlos_cds_preparation_capacity_ddl=NULL;
