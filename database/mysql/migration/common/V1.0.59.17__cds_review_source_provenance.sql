-- Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
-- Index reviewed source identity so one run's other patients never inherit its correction attribution.
ALTER TABLE cds_import_review
    ADD COLUMN source_patient_id VARCHAR(128) NULL,
    ADD COLUMN category VARCHAR(64) NULL,
    ADD COLUMN applied_at TIMESTAMP(6) NULL,
    ADD COLUMN safe_error_code VARCHAR(96) NULL,
    ADD COLUMN reviewed_source_xpath VARCHAR(2048) NULL,
    ADD COLUMN review_reason VARCHAR(4000) NULL,
    ADD KEY cds_review_source_patient (run_id, source_patient_id, category, review_version);
