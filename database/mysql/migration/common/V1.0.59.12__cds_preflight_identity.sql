-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Protected preflight-only identity inventory. Clinical writes begin only after its plan seals.
CREATE TABLE cds_import_identity (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    patient_key VARCHAR(64) NOT NULL,
    copy_key VARCHAR(64) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    hin VARCHAR(20) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_identity_patient (run_id, patient_key),
    KEY idx_cds_identity_hin (run_id, copy_key, hin),
    KEY idx_cds_identity_name (run_id, copy_key, last_name, first_name, birth_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Equality must follow the deployed clinical columns, including customized deployments.
-- Charset/collation identifiers originate exclusively in MariaDB's schema catalogue.
SET @cds_identity_collation_sql = (
    SELECT CONCAT('ALTER TABLE cds_import_identity ',
        GROUP_CONCAT(CONCAT('MODIFY COLUMN `', COLUMN_NAME, '` VARCHAR(',
            CASE WHEN COLUMN_NAME='hin' THEN '20' ELSE '50' END,
            ') CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME,
            CASE WHEN COLUMN_NAME='hin' THEN ' NULL' ELSE ' NOT NULL' END)
            ORDER BY COLUMN_NAME SEPARATOR ', '))
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demographic'
      AND COLUMN_NAME IN ('first_name','last_name','hin')
);
PREPARE cds_identity_collation_statement FROM @cds_identity_collation_sql;
EXECUTE cds_identity_collation_statement;
DEALLOCATE PREPARE cds_identity_collation_statement;
