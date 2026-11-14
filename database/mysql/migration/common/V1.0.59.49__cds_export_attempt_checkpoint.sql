-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- A CDS export attempt that stops before its package is ready keeps the charts it finished, so a
-- resumed attempt can adopt each unchanged one instead of building it again. The finished charts
-- are sealed, as the attempt goes, into a chain of encrypted CHART_CHECKPOINT members of the
-- attempt's own protected staging; these columns are the durable pointer to the newest member of
-- that chain (its identifier, plaintext SHA-256 and length) and the count of charts it names, and
-- how many of those charts this attempt adopted from an earlier attempt instead of building them.
-- A resumed attempt trusts a member only when it is exactly the one named here.
-- Export-owned metadata only: no clinical or importer row is read or changed, every column is
-- nullable and no existing attempt row is rewritten. NULL means the attempt kept no checkpoint.
-- A same-name column of another shape is an explicit upgrade blocker, never silently replaced.
DROP PROCEDURE IF EXISTS carlos_cds_export_attempt_checkpoint_1_0_59_49;
DELIMITER $$
CREATE PROCEDURE carlos_cds_export_attempt_checkpoint_1_0_59_49(
    IN expected_column VARCHAR(64), IN expected_signature TEXT, IN column_definition TEXT)
SQL SECURITY INVOKER
body: BEGIN
    DECLARE actual_signature TEXT;
    DECLARE table_count INTEGER;
    DECLARE error_message VARCHAR(128);
    SELECT COUNT(*) INTO table_count FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_name='cds_export_attempt' AND table_type='BASE TABLE';
    IF table_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve missing cds_export_attempt table before upgrade';
    END IF;
    SELECT CONCAT(column_type, ':', is_nullable, ':', COALESCE(column_default, '-'), ':',
        COALESCE(collation_name, '-'), ':', extra, ':', COALESCE(generation_expression, '-'))
      INTO actual_signature
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='cds_export_attempt' AND column_name=expected_column;
    IF actual_signature IS NOT NULL AND BINARY(actual_signature) <> BINARY(expected_signature) THEN
        SET error_message=CONCAT('Review existing cds_export_attempt column ',expected_column,' before upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=error_message;
    END IF;
    IF actual_signature IS NULL THEN
        SET @carlos_cds_export_attempt_checkpoint_ddl=CONCAT('ALTER TABLE `cds_export_attempt` ADD COLUMN `',
            REPLACE(expected_column,'`','``'),'` ',column_definition);
        PREPARE carlos_cds_export_attempt_checkpoint_statement FROM @carlos_cds_export_attempt_checkpoint_ddl;
        EXECUTE carlos_cds_export_attempt_checkpoint_statement;
        DEALLOCATE PREPARE carlos_cds_export_attempt_checkpoint_statement;
    END IF;
END$$
DELIMITER ;

-- The newest sealed CHART_CHECKPOINT member: its opaque staged identifier, plaintext SHA-256 and length.
CALL carlos_cds_export_attempt_checkpoint_1_0_59_49('checkpoint_member','varchar(64):YES:NULL:utf8mb4_bin::-','VARCHAR(64) NULL');
CALL carlos_cds_export_attempt_checkpoint_1_0_59_49('checkpoint_digest','varchar(64):YES:NULL:utf8mb4_bin::-','VARCHAR(64) NULL');
CALL carlos_cds_export_attempt_checkpoint_1_0_59_49('checkpoint_bytes','bigint(20):YES:NULL:-::-','BIGINT NULL');
-- The finished charts the chain names, and how many of them this attempt adopted.
CALL carlos_cds_export_attempt_checkpoint_1_0_59_49('checkpoint_charts','bigint(20):YES:NULL:-::-','BIGINT NULL');
CALL carlos_cds_export_attempt_checkpoint_1_0_59_49('adopted_charts','bigint(20):YES:NULL:-::-','BIGINT NULL');

DROP PROCEDURE carlos_cds_export_attempt_checkpoint_1_0_59_49;
SET @carlos_cds_export_attempt_checkpoint_ddl=NULL;
