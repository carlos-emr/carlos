-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Measurement deletion and HL7 replacement copy measurements.dataField verbatim.
-- Match its capacity without narrowing a larger adopted type or changing metadata.
SET @cds_measurements_deleted_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        CONCAT('ALTER TABLE `measurementsDeleted` MODIFY COLUMN `dataField` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='measurementsDeleted' AND column_name='dataField'
);
PREPARE cds_measurements_deleted_statement FROM @cds_measurements_deleted_ddl;
EXECUTE cds_measurements_deleted_statement;
DEALLOCATE PREPARE cds_measurements_deleted_statement;
