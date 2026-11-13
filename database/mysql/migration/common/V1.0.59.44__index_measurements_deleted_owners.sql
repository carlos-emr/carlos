-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- measurementsDeleted had only its primary key. The CDS export reads it by patient (every
-- capture count and page, and the extension scope, which also follows originalId) and by the
-- original measurement id (the check that no deleted id is shared with another chart), so each
-- of those reads scanned the whole table, once per page (issue #6200, CARE-4). HL7 results
-- superseded by a newer report add rows here for every refiled lab, so the table grows with the
-- clinic, not the chart.
-- Nonunique indexes only: no row is changed and nothing accepted before is refused.
-- A same-name index of another shape is an explicit upgrade blocker, never silently replaced.
-- Every index is checked before the first one is added, so a replay after an interrupted
-- upgrade (one ALTER committed) completes the other and changes nothing else.
DROP PROCEDURE IF EXISTS carlos_measurements_deleted_index_1_0_59_44;
DELIMITER $$
CREATE PROCEDURE carlos_measurements_deleted_index_1_0_59_44(
    IN expected_index VARCHAR(64), IN required_columns TEXT, IN expected_signature TEXT,
    IN index_definition TEXT, IN apply_change BOOLEAN)
SQL SECURITY INVOKER
body: BEGIN
    DECLARE actual_signature TEXT;
    DECLARE column_count INTEGER;
    DECLARE error_message VARCHAR(128);
    SELECT COUNT(*) INTO column_count FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='measurementsDeleted'
        AND FIND_IN_SET(column_name, required_columns)>0;
    IF column_count <> 1 + LENGTH(required_columns) - LENGTH(REPLACE(required_columns, ',', '')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve missing measurementsDeleted columns before upgrade';
    END IF;
    SELECT CONCAT(MIN(non_unique), ':', MIN(index_type), ':', MIN(ignored), ':',
        GROUP_CONCAT(CONCAT(HEX(column_name), ':', COALESCE(sub_part,0), ':', COALESCE(collation,'-'))
                     ORDER BY seq_in_index SEPARATOR ',')) INTO actual_signature
      FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='measurementsDeleted' AND index_name=expected_index;
    IF actual_signature IS NOT NULL AND BINARY(actual_signature) <> BINARY(expected_signature) THEN
        SET error_message=CONCAT('Review existing measurementsDeleted index ',expected_index,' before upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=error_message;
    END IF;
    IF actual_signature IS NULL AND apply_change THEN
        SET @carlos_measurements_deleted_index_ddl=CONCAT('ALTER TABLE `measurementsDeleted` ADD INDEX `',
            REPLACE(expected_index,'`','``'),'` ',index_definition);
        PREPARE carlos_measurements_deleted_index_statement FROM @carlos_measurements_deleted_index_ddl;
        EXECUTE carlos_measurements_deleted_index_statement;
        DEALLOCATE PREPARE carlos_measurements_deleted_index_statement;
    END IF;
END$$
DELIMITER ;

-- Check every index before the first DDL change.
CALL carlos_measurements_deleted_index_1_0_59_44('idx_measurementsDeleted_demographicNo','demographicNo','1:BTREE:NO:64656D6F677261706869634E6F:0:A','(`demographicNo`) USING BTREE',0);
CALL carlos_measurements_deleted_index_1_0_59_44('idx_measurementsDeleted_originalId_demographicNo','originalId,demographicNo','1:BTREE:NO:6F726967696E616C4964:0:A,64656D6F677261706869634E6F:0:A','(`originalId`,`demographicNo`) USING BTREE',0);

-- The patient's deleted versions in id order (capture pages: demographicNo, then the primary key).
CALL carlos_measurements_deleted_index_1_0_59_44('idx_measurementsDeleted_demographicNo','demographicNo','1:BTREE:NO:64656D6F677261706869634E6F:0:A','(`demographicNo`) USING BTREE',1);
-- The versions of one original id, with their chart, read from the index alone.
CALL carlos_measurements_deleted_index_1_0_59_44('idx_measurementsDeleted_originalId_demographicNo','originalId,demographicNo','1:BTREE:NO:6F726967696E616C4964:0:A,64656D6F677261706869634E6F:0:A','(`originalId`,`demographicNo`) USING BTREE',1);

DROP PROCEDURE carlos_measurements_deleted_index_1_0_59_44;
SET @carlos_measurements_deleted_index_ddl=NULL;
