-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Restricted laboratory results have no unrestricted HL7, measurement, portal or outbound mirror.
CREATE TABLE cds_laboratory_result (
    id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    demographicNo INT NOT NULL,
    runId CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sourcePath VARCHAR(2048) NOT NULL,
    laboratoryName VARCHAR(120) NOT NULL,
    reportedTestName VARCHAR(120) NULL, testName VARCHAR(120) NULL, testCode VARCHAR(50) NULL,
    accessionNumber VARCHAR(120) NULL, resultValue VARCHAR(120) NULL, unitOfMeasure VARCHAR(120) NULL,
    lowLimit VARCHAR(1024) NULL, highLimit VARCHAR(1024) NULL, referenceRange VARCHAR(1024) NULL,
    resultStatus VARCHAR(20) NULL, abnormalFlag VARCHAR(50) NOT NULL, sourceBlocked CHAR(1) NULL,
    collectionDate VARCHAR(128) NULL, collectionPrecision VARCHAR(32) NOT NULL,
    requisitionDate VARCHAR(128) NULL, requisitionPrecision VARCHAR(32) NOT NULL,
    informationFromLab MEDIUMTEXT NULL, notesFromLab MEDIUMTEXT NULL, physiciansNotes MEDIUMTEXT NULL,
    nativeLabNo INT NULL, importedBy VARCHAR(16) NOT NULL,
    KEY cds_lab_patient_restriction (demographicNo, sourceBlocked, id),
    KEY cds_lab_run (runId, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve full finite source values and four-byte Unicode capacity without shrinking
-- operator-approved wider columns, changing native defaults/nullability or resetting collation.
-- hl7TextInfo.first_name: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `hl7TextInfo` MODIFY COLUMN `first_name` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `hl7TextInfo`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='hl7TextInfo' AND column_name='first_name'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- hl7TextInfo.last_name: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `hl7TextInfo` MODIFY COLUMN `last_name` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `hl7TextInfo`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='hl7TextInfo' AND column_name='last_name'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- appointment.imported_status: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 250, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `appointment` MODIFY COLUMN `imported_status` VARCHAR(250)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `appointment`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='appointment' AND column_name='imported_status'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- appointment.notes: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `appointment` MODIFY COLUMN `notes` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `appointment`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='appointment' AND column_name='notes'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- casemgmt_note.note: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `casemgmt_note` MODIFY COLUMN `note` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `casemgmt_note`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='casemgmt_note' AND column_name='note'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;

-- Match the documented ASCII-space normalization without repeated full-table REPLACE scans.
-- Original patient identifiers and non-space punctuation remain untouched.
-- Overlong adopted identifiers are excluded from this finite source lookup, never truncated.
ALTER TABLE demographic ADD COLUMN cds_identity_hin VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
    GENERATED ALWAYS AS (CASE WHEN CHAR_LENGTH(REPLACE(hin, ' ', '')) <= 20
        THEN REPLACE(hin, ' ', '') ELSE NULL END) PERSISTENT,
    ADD KEY cds_identity_hin_lookup (cds_identity_hin);

-- Register the capability without granting it to any existing role or user.
INSERT INTO secObjectName (objectName, description, orgapplicable)
SELECT '_lab_sensitive', 'Read source-restricted CDS laboratory results', 0
WHERE NOT EXISTS (SELECT 1 FROM secObjectName WHERE objectName='_lab_sensitive');
