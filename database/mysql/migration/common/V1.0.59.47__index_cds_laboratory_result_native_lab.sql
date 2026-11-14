-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- cds_laboratory_result had no index on nativeLabNo, the local HL7 report an imported result is
-- bound to. The CDS export checks that binding before every laboratory page it reads: that no
-- other imported result names the same report, and that no other chart routes it. Without an
-- index each check scanned the whole table once per result of the patient, so an imported chart
-- of about two thousand results ran into the statement timeout and its export failed, and every
-- imported chart slowed as the clinic's imported results grew.
-- A nonunique index only: no row is changed and nothing accepted before is refused.
-- A same-name index of another shape is an explicit upgrade blocker, never silently replaced.
DROP PROCEDURE IF EXISTS carlos_cds_laboratory_result_index_1_0_59_47;
DELIMITER $$
CREATE PROCEDURE carlos_cds_laboratory_result_index_1_0_59_47(
    IN expected_index VARCHAR(64), IN required_columns TEXT, IN expected_signature TEXT,
    IN index_definition TEXT)
SQL SECURITY INVOKER
body: BEGIN
    DECLARE actual_signature TEXT;
    DECLARE column_count INTEGER;
    DECLARE error_message VARCHAR(128);
    SELECT COUNT(*) INTO column_count FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='cds_laboratory_result'
        AND FIND_IN_SET(column_name, required_columns)>0;
    IF column_count <> 1 + LENGTH(required_columns) - LENGTH(REPLACE(required_columns, ',', '')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve missing cds_laboratory_result columns before upgrade';
    END IF;
    SELECT CONCAT(MIN(non_unique), ':', MIN(index_type), ':', MIN(ignored), ':',
        GROUP_CONCAT(CONCAT(HEX(column_name), ':', COALESCE(sub_part,0), ':', COALESCE(collation,'-'))
                     ORDER BY seq_in_index SEPARATOR ',')) INTO actual_signature
      FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='cds_laboratory_result' AND index_name=expected_index;
    IF actual_signature IS NOT NULL AND BINARY(actual_signature) <> BINARY(expected_signature) THEN
        SET error_message=CONCAT('Review existing cds_laboratory_result index ',expected_index,' before upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=error_message;
    END IF;
    IF actual_signature IS NULL THEN
        SET @carlos_cds_laboratory_result_index_ddl=CONCAT('ALTER TABLE `cds_laboratory_result` ADD INDEX `',
            REPLACE(expected_index,'`','``'),'` ',index_definition);
        PREPARE carlos_cds_laboratory_result_index_statement FROM @carlos_cds_laboratory_result_index_ddl;
        EXECUTE carlos_cds_laboratory_result_index_statement;
        DEALLOCATE PREPARE carlos_cds_laboratory_result_index_statement;
    END IF;
END$$
DELIMITER ;

-- The imported results bound to one local report, with their chart, read from the index alone.
CALL carlos_cds_laboratory_result_index_1_0_59_47('idx_cds_laboratory_result_nativeLabNo_demographicNo','nativeLabNo,demographicNo','1:BTREE:NO:6E61746976654C61624E6F:0:A,64656D6F677261706869634E6F:0:A','(`nativeLabNo`,`demographicNo`) USING BTREE');

DROP PROCEDURE carlos_cds_laboratory_result_index_1_0_59_47;
SET @carlos_cds_laboratory_result_index_ddl=NULL;
