-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Exact HIN identity is scoped to the pinned health-card authority, never a guessed province.
-- Sentinel/unknown authority remains NULL and therefore blocks automatic HIN disambiguation.
ALTER TABLE demographic
    ADD COLUMN cds_identity_issuer VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE
        WHEN BINARY(hc_type) IN ('AB', 'BC', 'MB', 'NB', 'NL', 'NS', 'NT', 'NU', 'ON', 'PE', 'QC', 'SK', 'YT') THEN hc_type
        WHEN BINARY(hc_type) IN ('CA-AB', 'CA-BC', 'CA-MB', 'CA-NB', 'CA-NL', 'CA-NS', 'CA-NT', 'CA-NU', 'CA-ON', 'CA-PE', 'CA-QC', 'CA-SK', 'CA-YT') THEN SUBSTRING(hc_type, 4)
        WHEN BINARY(hc_type) IN ('US-AK', 'US-AL', 'US-AR', 'US-AZ', 'US-CA', 'US-CO', 'US-CT', 'US-CZ', 'US-DC', 'US-DE', 'US-FL', 'US-GA', 'US-GU', 'US-HI', 'US-IA', 'US-ID', 'US-IL', 'US-IN', 'US-KS', 'US-KY', 'US-LA', 'US-MA', 'US-MD', 'US-ME', 'US-MI', 'US-MN', 'US-MO', 'US-MS', 'US-MT', 'US-NC', 'US-ND', 'US-NE', 'US-NH', 'US-NJ', 'US-NM', 'US-NV', 'US-NY', 'US-OH', 'US-OK', 'US-OR', 'US-PA', 'US-PR', 'US-RI', 'US-SC', 'US-SD', 'US-TN', 'US-TX', 'US-UT', 'US-VA', 'US-VI', 'US-VT', 'US-WA', 'US-WI', 'US-WV', 'US-WY') THEN hc_type
        ELSE NULL END) PERSISTENT,
    ADD KEY cds_identity_authority_lookup (cds_identity_hin, cds_identity_issuer, demographic_no);

-- Historical preflight rows gain unknown authority conservatively; full unsealed scans rebuild this inventory.
-- Declined/invalid occurrences are still seen and counted but cannot poison active identity collisions.
ALTER TABLE cds_import_identity
    ADD COLUMN issuer VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
    ADD COLUMN comparable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD KEY idx_cds_identity_authority (run_id, copy_key, comparable, hin, issuer),
    ADD KEY idx_cds_identity_active_name (run_id, copy_key, comparable, last_name, first_name, birth_date);
