-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Keep a long patient alert whole (#6264, #5869). The alert is demographiccust.cust3, and every
-- save copies the previous row into demographiccustArchive.cust3. Both were VARCHAR(255), so the
-- add, edit and REST saves had to refuse an alert longer than 255 characters (an empty sql_mode
-- would have cut it). Both columns become TEXT: 65,535 bytes, so at least 16,383 characters
-- whatever the characters, which is DemographicCust.ALERT_MAX_LENGTH.
-- Widening only: no value changes and nothing accepted before is refused. The character set,
-- collation, nullability, default and comment of each column are kept.
-- A TEXT column can only be indexed by a prefix. The shipped single-column index `cust3` is rebuilt
-- in the same ALTER on the same number of leading characters it indexed before, so its key length
-- does not change (1,020 bytes in utf8mb4, fitting InnoDB's 3,072-byte DYNAMIC row-format limit
-- just as the existing VARCHAR(255) index did). Any other index that reads the whole column is an
-- explicit upgrade blocker, never silently dropped or changed. Both tables are checked before
-- either is altered.
-- A column that is already TEXT, MEDIUMTEXT or LONGTEXT is left alone, so a replay, or a column an
-- installation widened itself, changes nothing.
SET @carlos_patient_alert_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO';

DROP PROCEDURE IF EXISTS carlos_patient_alert_text_1_0_59_54;
DELIMITER $$
CREATE PROCEDURE carlos_patient_alert_text_1_0_59_54(IN target_table VARCHAR(64), IN apply_change BOOLEAN)
SQL SECURITY INVOKER
body: BEGIN
    DECLARE table_count INTEGER;
    DECLARE column_type_name VARCHAR(64) DEFAULT NULL;
    DECLARE column_generated VARCHAR(16) DEFAULT NULL;
    DECLARE stock_count INTEGER;
    DECLARE blocking_count INTEGER;
    DECLARE failure_message VARCHAR(128);
    SELECT COUNT(*) INTO table_count FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_name=target_table AND table_type='BASE TABLE';
    IF table_count <> 1 THEN
        SET failure_message=CONCAT('Resolve missing ', target_table, ' table before upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=failure_message;
    END IF;
    SELECT LOWER(data_type), is_generated INTO column_type_name, column_generated
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name=target_table AND column_name='cust3';
    IF column_type_name IN ('text', 'mediumtext', 'longtext') AND column_generated='NEVER' THEN
        LEAVE body;
    END IF;
    IF column_type_name IS NULL OR column_type_name NOT IN ('varchar', 'char')
       OR column_generated IS NULL OR column_generated <> 'NEVER' THEN
        SET failure_message=CONCAT('Review ', target_table, '.cust3 before upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=failure_message;
    END IF;
    -- The shipped index: named cust3, not unique, a B-tree on the whole of cust3 and nothing else.
    SELECT COUNT(*) INTO stock_count FROM (
        SELECT index_name FROM information_schema.statistics
          WHERE table_schema=DATABASE() AND table_name=target_table AND index_name='cust3'
          GROUP BY index_name
          HAVING COUNT(*)=1 AND MIN(column_name)='cust3' AND MIN(sub_part) IS NULL
             AND MIN(non_unique)=1 AND MIN(index_type)='BTREE'
    ) AS stock_index;
    -- Any other index on the whole column would stop the conversion half way: refuse it up front.
    SELECT COUNT(DISTINCT index_name) INTO blocking_count FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name=target_table AND column_name='cust3'
        AND sub_part IS NULL AND index_type <> 'FULLTEXT'
        AND NOT (stock_count=1 AND index_name='cust3');
    IF blocking_count <> 0 THEN
        SET failure_message=CONCAT('Review the indexes on ', target_table, '.cust3 before upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=failure_message;
    END IF;
    IF NOT apply_change THEN
        LEAVE body;
    END IF;
    SELECT CONCAT('ALTER TABLE `', REPLACE(target_table, '`', '``'), '` ',
            IF(stock_count=1, 'DROP INDEX `cust3`, ', ''),
            'MODIFY COLUMN `cust3` TEXT CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES', ' NULL', ' NOT NULL'),
            IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
            ' COMMENT ', QUOTE(column_comment),
            IF(stock_count=1, CONCAT(', ADD INDEX `cust3` (`cust3`(', character_maximum_length, '))'), ''))
      INTO @carlos_patient_alert_ddl
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name=target_table AND column_name='cust3';
    PREPARE carlos_patient_alert_statement FROM @carlos_patient_alert_ddl;
    EXECUTE carlos_patient_alert_statement;
    DEALLOCATE PREPARE carlos_patient_alert_statement;
END$$
DELIMITER ;

CALL carlos_patient_alert_text_1_0_59_54('demographiccust', FALSE);
CALL carlos_patient_alert_text_1_0_59_54('demographiccustArchive', FALSE);
CALL carlos_patient_alert_text_1_0_59_54('demographiccust', TRUE);
CALL carlos_patient_alert_text_1_0_59_54('demographiccustArchive', TRUE);

DROP PROCEDURE carlos_patient_alert_text_1_0_59_54;
SET @carlos_patient_alert_ddl=NULL;
SET SESSION sql_mode = @carlos_patient_alert_previous_sql_mode;
SET @carlos_patient_alert_previous_sql_mode=NULL;
