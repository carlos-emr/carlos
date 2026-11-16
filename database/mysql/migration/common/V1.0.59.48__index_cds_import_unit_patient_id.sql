-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- cds_import_unit had no index that orders one imported chart's units by id. The CDS export
-- captures those units in id order, a page at a time, and each page had to collect and sort
-- every unit of the chart to find its next thirty-two: an imported chart of about two thousand
-- notes and two thousand laboratory results spent about a minute of its capture doing that 257
-- times, and the cost grew with the square of the chart.
-- idx_cds_units_patient (run_id, patient_key, unit_key) stays: the importer reads units by unit key.
-- A nonunique index only: no row is changed and nothing accepted before is refused.
-- A same-name index of another shape is an explicit upgrade blocker, never silently replaced.
DROP PROCEDURE IF EXISTS carlos_cds_import_unit_index_1_0_59_48;
DELIMITER $$
CREATE PROCEDURE carlos_cds_import_unit_index_1_0_59_48(
    IN expected_index VARCHAR(64), IN required_columns TEXT, IN expected_signature TEXT,
    IN index_definition TEXT)
SQL SECURITY INVOKER
body: BEGIN
    DECLARE actual_signature TEXT;
    DECLARE column_count INTEGER;
    DECLARE error_message VARCHAR(128);
    SELECT COUNT(*) INTO column_count FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='cds_import_unit'
        AND FIND_IN_SET(column_name, required_columns)>0;
    IF column_count <> 1 + LENGTH(required_columns) - LENGTH(REPLACE(required_columns, ',', '')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve missing cds_import_unit columns before upgrade';
    END IF;
    SELECT CONCAT(MIN(non_unique), ':', MIN(index_type), ':', MIN(ignored), ':',
        GROUP_CONCAT(CONCAT(HEX(column_name), ':', COALESCE(sub_part,0), ':', COALESCE(collation,'-'))
                     ORDER BY seq_in_index SEPARATOR ',')) INTO actual_signature
      FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='cds_import_unit' AND index_name=expected_index;
    IF actual_signature IS NOT NULL AND BINARY(actual_signature) <> BINARY(expected_signature) THEN
        SET error_message=CONCAT('Review existing cds_import_unit index ',expected_index,' before upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=error_message;
    END IF;
    IF actual_signature IS NULL THEN
        SET @carlos_cds_import_unit_index_ddl=CONCAT('ALTER TABLE `cds_import_unit` ADD INDEX `',
            REPLACE(expected_index,'`','``'),'` ',index_definition);
        PREPARE carlos_cds_import_unit_index_statement FROM @carlos_cds_import_unit_index_ddl;
        EXECUTE carlos_cds_import_unit_index_statement;
        DEALLOCATE PREPARE carlos_cds_import_unit_index_statement;
    END IF;
END$$
DELIMITER ;

-- One imported chart's units of one run in id order, with their category, read from the index alone.
CALL carlos_cds_import_unit_index_1_0_59_48('idx_cds_units_patient_id','run_id,patient_key,id,category','1:BTREE:NO:72756E5F6964:0:A,70617469656E745F6B6579:0:A,6964:0:A,63617465676F7279:0:A','(`run_id`,`patient_key`,`id`,`category`) USING BTREE');

DROP PROCEDURE carlos_cds_import_unit_index_1_0_59_48;
SET @carlos_cds_import_unit_index_ddl=NULL;
