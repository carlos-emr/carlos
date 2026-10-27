-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Import checkpoints are separate from patient admission/status and survive retries.
-- All tables are InnoDB: clinical unit, checkpoint and strict audit share one transaction.

CREATE TABLE cds_import_run (
    id VARCHAR(36) NOT NULL,
    submission_key VARCHAR(128) NOT NULL,
    dry_run BOOLEAN NOT NULL,
    report_state VARCHAR(48) NOT NULL,
    cleanup_state VARCHAR(48) NOT NULL,
    safe_error_code VARCHAR(96) NULL,
    review_version BIGINT NOT NULL,
    discovery_complete BOOLEAN NOT NULL,
    discovered_files BIGINT NOT NULL,
    patient_xml_files BIGINT NOT NULL,
    source_bytes BIGINT NOT NULL,
    operator VARCHAR(128) NOT NULL,
    provider_no VARCHAR(16) NOT NULL,
    source_namespace VARCHAR(128) NOT NULL,
    profile VARCHAR(64) NOT NULL,
    source_digest VARCHAR(64) NOT NULL,
    options_digest VARCHAR(64) NOT NULL,
    ruleset_digest VARCHAR(64) NOT NULL,
    snapshot_location VARCHAR(1024) NOT NULL,
    state VARCHAR(48) NOT NULL,
    manifest_digest VARCHAR(64) NULL,
    execution_digest VARCHAR(64) NULL,
    expected_patients BIGINT NOT NULL,
    expected_units BIGINT NOT NULL,
    owner_epoch BIGINT NOT NULL,
    cancel_requested BOOLEAN NOT NULL,
    report_generation BIGINT NOT NULL,
    report_location VARCHAR(1024) NULL,
    report_digest VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_submission (operator, submission_key),
    KEY idx_cds_run_state (state, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_lineage (
    id VARCHAR(64) NOT NULL,
    source_namespace VARCHAR(128) NOT NULL,
    source_patient_id VARCHAR(128) NOT NULL,
    copy_key VARCHAR(64) NOT NULL,
    source_digest VARCHAR(64) NOT NULL,
    options_digest VARCHAR(64) NOT NULL,
    ruleset_digest VARCHAR(64) NOT NULL,
    target_demographic_no INT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_source_lineage (source_namespace, source_patient_id, copy_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_patient (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    patient_key VARCHAR(64) NOT NULL,
    source_patient_id VARCHAR(128) NOT NULL,
    copy_key VARCHAR(64) NOT NULL,
    lineage_key VARCHAR(64) NOT NULL,
    source_digest VARCHAR(64) NOT NULL,
    target_demographic_no INT NULL,
    state VARCHAR(48) NOT NULL,
    held BOOLEAN NOT NULL,
    expected_units BIGINT NOT NULL,
    execution_digest VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_run_patient (run_id, patient_key),
    KEY idx_cds_patient_target_hold (target_demographic_no, held),
    CONSTRAINT fk_cds_patient_run FOREIGN KEY (run_id) REFERENCES cds_import_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_unit (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    patient_key VARCHAR(64) NOT NULL,
    unit_key VARCHAR(64) NOT NULL,
    lineage_key VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    ordinal BIGINT NOT NULL,
    source_digest VARCHAR(64) NOT NULL,
    spool_location VARCHAR(1024) NOT NULL,
    state VARCHAR(48) NOT NULL,
    target_references LONGTEXT NULL,
    target_digest VARCHAR(64) NULL,
    committed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_expected_unit (run_id, unit_key),
    KEY idx_cds_units_patient (run_id, patient_key, unit_key),
    KEY idx_cds_units_pending (run_id, state, unit_key),
    CONSTRAINT fk_cds_unit_patient FOREIGN KEY (run_id, patient_key) REFERENCES cds_import_patient(run_id, patient_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_dependency (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    unit_key VARCHAR(64) NOT NULL,
    depends_on_unit_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_dependency (run_id, unit_key, depends_on_unit_key),
    KEY idx_cds_dependency_target (run_id, depends_on_unit_key),
    CONSTRAINT fk_cds_dependency_unit FOREIGN KEY (run_id, unit_key) REFERENCES cds_import_unit(run_id, unit_key),
    CONSTRAINT fk_cds_dependency_target FOREIGN KEY (run_id, depends_on_unit_key) REFERENCES cds_import_unit(run_id, unit_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_checkpoint (
    id VARCHAR(64) NOT NULL,
    lineage_key VARCHAR(64) NOT NULL,
    source_digest VARCHAR(64) NOT NULL,
    options_digest VARCHAR(64) NOT NULL,
    ruleset_digest VARCHAR(64) NOT NULL,
    target_references LONGTEXT NOT NULL,
    target_digest VARCHAR(64) NOT NULL,
    first_run_id VARCHAR(36) NOT NULL,
    committed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cds_checkpoint_lineage (lineage_key),
    CONSTRAINT fk_cds_checkpoint_lineage FOREIGN KEY (lineage_key) REFERENCES cds_import_lineage(id),
    CONSTRAINT fk_cds_checkpoint_run FOREIGN KEY (first_run_id) REFERENCES cds_import_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_field (
    source_namespace VARCHAR(250) NOT NULL,
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    patient_key VARCHAR(64) NOT NULL,
    unit_key VARCHAR(64) NOT NULL,
    demographic_no INT NOT NULL,
    category VARCHAR(64) NOT NULL,
    source_path VARCHAR(2048) NOT NULL,
    occurrence BIGINT NOT NULL,
    value_type VARCHAR(64) NOT NULL,
    date_precision VARCHAR(32) NULL,
    original_value LONGTEXT NULL,
    normalized_value LONGTEXT NULL,
    payload_location VARCHAR(1024) NULL,
    payload_digest VARCHAR(64) NULL,
    payload_offset BIGINT NOT NULL,
    payload_length BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cds_field_category (demographic_no, category, id),
    KEY idx_cds_field_unit (run_id, unit_key),
    CONSTRAINT fk_cds_field_unit FOREIGN KEY (run_id, unit_key) REFERENCES cds_import_unit(run_id, unit_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_diagnostic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(36) NOT NULL,
    patient_key VARCHAR(64) NULL,
    unit_key VARCHAR(64) NULL,
    severity VARCHAR(16) NOT NULL,
    code VARCHAR(96) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    source_path VARCHAR(2048) NULL,
    safe_summary VARCHAR(2048) NOT NULL,
    protected_detail_location VARCHAR(1024) NULL,
    blocking BOOLEAN NOT NULL,
    resolved BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cds_diagnostic_page (run_id, id),
    KEY idx_cds_diagnostic_review (run_id, patient_key, blocking, resolved),
    CONSTRAINT fk_cds_diagnostic_run FOREIGN KEY (run_id) REFERENCES cds_import_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_file (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    location_key VARCHAR(64) NOT NULL,
    location VARCHAR(1024) NOT NULL,
    digest VARCHAR(64) NOT NULL,
    byte_length BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    generation BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_file_location (location_key),
    CONSTRAINT fk_cds_file_run FOREIGN KEY (run_id) REFERENCES cds_import_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_file_reference (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    unit_key VARCHAR(64) NOT NULL,
    file_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_unit_file (run_id, unit_key, file_id),
    KEY idx_cds_file_reference (file_id),
    CONSTRAINT fk_cds_file_reference_unit FOREIGN KEY (run_id, unit_key) REFERENCES cds_import_unit(run_id, unit_key),
    CONSTRAINT fk_cds_file_reference_object FOREIGN KEY (file_id) REFERENCES cds_import_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(36) NOT NULL,
    unit_key VARCHAR(64) NULL,
    epoch BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    operator VARCHAR(128) NOT NULL,
    provider_no VARCHAR(16) NOT NULL,
    observation_context VARCHAR(1024) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cds_event_page (run_id, id),
    CONSTRAINT fk_cds_event_run FOREIGN KEY (run_id) REFERENCES cds_import_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cds_import_admission (
    maintenance_active BOOLEAN NOT NULL DEFAULT FALSE,
    maintenance_generation BIGINT NOT NULL DEFAULT 0,
    maintenance_owner_token VARCHAR(36) NULL,
    maintenance_connection_id BIGINT NULL,
    id VARCHAR(64) NOT NULL,
    owner_run_id VARCHAR(36) NULL,
    reserved_bytes BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Preserve an existing owner and reservation during adopted-schema initialization.
INSERT INTO cds_import_admission (id, owner_run_id, reserved_bytes)
SELECT 'global', NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM cds_import_admission WHERE id='global');

INSERT INTO cds_import_admission (id, owner_run_id, reserved_bytes)
SELECT 'clinical-maintenance', NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM cds_import_admission WHERE id='clinical-maintenance');


-- Preserve supported clinical values without changing existing defaults or collation.
-- New import-specific semantics are NULL for every pre-existing row; never infer history.
ALTER TABLE `drugs` ADD COLUMN `cds_currentness` VARCHAR(16) NULL DEFAULT NULL,
    ADD COLUMN `cds_dose_text` VARCHAR(120) NULL DEFAULT NULL;

-- Nullable source dates/indicators retain the actual legacy default for ordinary native inserts.
SET @cds_native_ddl = (
    SELECT IF(data_type = 'date',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `rx_date` ', column_type, ' NULL',
            IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
            ' COMMENT ', QUOTE(column_comment)),
        'SELECT cds_import_native_rx_date_type_requires_review FROM `drugs`')
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='rx_date'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;
SET @cds_native_ddl = (
    SELECT IF(data_type = 'date',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `end_date` ', column_type, ' NULL',
            IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
            ' COMMENT ', QUOTE(column_comment)),
        'SELECT cds_import_native_end_date_type_requires_review FROM `drugs`')
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='end_date'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;
SET @cds_native_ddl = (
    SELECT IF(data_type IN ('tinyint','smallint','int','bigint'),
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `nosubs` ', column_type, ' NULL',
            IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
            ' COMMENT ', QUOTE(column_comment)),
        'SELECT cds_import_native_nosubs_type_requires_review FROM `drugs`')
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='nosubs'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.customName: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 120, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `customName` VARCHAR(120)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='customName'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.freqcode: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 120, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `freqcode` VARCHAR(120)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='freqcode'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.duration: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 1024, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `duration` VARCHAR(1024)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='duration'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.quantity: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 1024, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `quantity` VARCHAR(1024)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='quantity'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.unit: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `unit` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='unit'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.unitName: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `unitName` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='unitName'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.route: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 120, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `route` VARCHAR(120)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='route'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.drug_form: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 120, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `drug_form` VARCHAR(120)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='drug_form'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.eTreatmentType: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `eTreatmentType` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='eTreatmentType'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.dosage: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `dosage` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='dosage'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.comment: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `comment` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='comment'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.special: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `special` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='special'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- drugs.special_instruction: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `special_instruction` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='special_instruction'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- allergies.DESCRIPTION: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 120, 'SELECT 1',
        CONCAT('ALTER TABLE `allergies` MODIFY COLUMN `DESCRIPTION` VARCHAR(120)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='allergies' AND column_name='DESCRIPTION'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- preventionsExt.val: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        CONCAT('ALTER TABLE `preventionsExt` MODIFY COLUMN `val` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='preventionsExt' AND column_name='val'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- demographic.first_name: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        CONCAT('ALTER TABLE `demographic` MODIFY COLUMN `first_name` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographic' AND column_name='first_name'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- demographic.last_name: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        CONCAT('ALTER TABLE `demographic` MODIFY COLUMN `last_name` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographic' AND column_name='last_name'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- demographic.city: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 80, 'SELECT 1',
        CONCAT('ALTER TABLE `demographic` MODIFY COLUMN `city` VARCHAR(80)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographic' AND column_name='city'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- demographic.residentialCity: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 80, 'SELECT 1',
        CONCAT('ALTER TABLE `demographic` MODIFY COLUMN `residentialCity` VARCHAR(80)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographic' AND column_name='residentialCity'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- demographic.patient_status: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 50, 'SELECT 1',
        CONCAT('ALTER TABLE `demographic` MODIFY COLUMN `patient_status` VARCHAR(50)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographic' AND column_name='patient_status'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- demographic.chart_no: widen only; preserve wider operator-approved storage and its metadata.
SET @cds_native_ddl = (
    SELECT IF(character_maximum_length >= 15, 'SELECT 1',
        CONCAT('ALTER TABLE `demographic` MODIFY COLUMN `chart_no` VARCHAR(15)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='demographic' AND column_name='chart_no'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- NumberOfRefills has int32 semantics. Preserve default/nullability and never shrink an existing BIGINT.
SET @cds_native_ddl = (
    SELECT IF(data_type='bigint' OR data_type IN ('int','integer'), 'SELECT 1',
        IF(data_type IN ('tinyint','smallint','mediumint'),
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `repeat` INT',
            IF(LOCATE('unsigned',column_type)>0,' UNSIGNED',''),
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_import_native_repeat_type_requires_review FROM `drugs`'))
    FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='repeat'
);
PREPARE cds_native_statement FROM @cds_native_ddl;
EXECUTE cds_native_statement;
DEALLOCATE PREPARE cds_native_statement;

-- Latest precision metadata is read by patient/rules/report consumers using this lookup.
CREATE INDEX cds_partial_date_lookup ON partial_date (table_name,field_name,table_id,id);
