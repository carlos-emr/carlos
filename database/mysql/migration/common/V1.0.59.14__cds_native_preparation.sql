-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Immutable bounded native projections avoid unbounded source IO while clinical locks are held.
CREATE TABLE cds_import_preparation (
    id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    unit_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    options_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ruleset_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_type VARCHAR(250) NOT NULL,
    payload MEDIUMTEXT NOT NULL CHECK (OCTET_LENGTH(payload) <= 1048576),
    digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY cds_preparation_unit (run_id, unit_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cds_import_patient ADD KEY cds_patient_lineage_attempt (lineage_key,run_id);
-- hl7TextInfo.health_no: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 20, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `hl7TextInfo` MODIFY COLUMN `health_no` VARCHAR(20)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `hl7TextInfo`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='hl7TextInfo' AND column_name='health_no'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;

