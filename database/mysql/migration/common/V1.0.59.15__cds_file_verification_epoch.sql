-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Operational proof cache, scoped to the immutable file owner's run and current fenced epoch.
ALTER TABLE cds_import_file ADD COLUMN verified_epoch BIGINT NOT NULL DEFAULT 0;

-- Preserve expanded schema QNames and ordinary edit/archive capacities without shrinking
-- operator-approved wider columns or changing existing null/default/charset/collation/comment.
-- cds_import_field.value_type: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 4608, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `cds_import_field` MODIFY COLUMN `value_type` VARCHAR(4608)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `cds_import_field`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='cds_import_field' AND column_name='value_type'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- appointmentArchive.notes: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `appointmentArchive` MODIFY COLUMN `notes` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `appointmentArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='appointmentArchive' AND column_name='notes'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- demographicArchive.first_name: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `demographicArchive` MODIFY COLUMN `first_name` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `demographicArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographicArchive' AND column_name='first_name'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- demographicArchive.last_name: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `demographicArchive` MODIFY COLUMN `last_name` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `demographicArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographicArchive' AND column_name='last_name'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- demographicArchive.city: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 80, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `demographicArchive` MODIFY COLUMN `city` VARCHAR(80)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `demographicArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographicArchive' AND column_name='city'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- demographicArchive.residentialCity: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 80, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `demographicArchive` MODIFY COLUMN `residentialCity` VARCHAR(80)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `demographicArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographicArchive' AND column_name='residentialCity'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- demographicArchive.patient_status: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `demographicArchive` MODIFY COLUMN `patient_status` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `demographicArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographicArchive' AND column_name='patient_status'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
-- demographicArchive.chart_no: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 15, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `demographicArchive` MODIFY COLUMN `chart_no` VARCHAR(15)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `demographicArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographicArchive' AND column_name='chart_no'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;

-- appointmentArchive.imported_status: never shrink wider storage or discard adopted metadata.
SET @cds_capacity_ddl = (
    SELECT IF(character_maximum_length >= 250, 'SELECT 1',
        IF(data_type IN ('char','varchar','tinytext','text','mediumtext','longtext'),
        CONCAT('ALTER TABLE `appointmentArchive` MODIFY COLUMN `imported_status` VARCHAR(250)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_native_capacity_type_requires_review FROM `appointmentArchive`'))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='appointmentArchive' AND column_name='imported_status'
);
PREPARE cds_capacity_statement FROM @cds_capacity_ddl;
EXECUTE cds_capacity_statement;
DEALLOCATE PREPARE cds_capacity_statement;
