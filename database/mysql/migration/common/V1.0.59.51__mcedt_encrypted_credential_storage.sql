-- Copyright (c) 2026 CARLOS Contributors.
-- SPDX-License-Identifier: GPL-2.0-or-later
-- A separate namespace distinguishes arbitrary legacy plaintext (including a
-- literal {ENC} prefix) from authenticated ciphertext. Seed the stable lock
-- target before application traffic, including the first-ever password save.
CREATE TABLE IF NOT EXISTS mcedt_credential (
    id TINYINT NOT NULL,
    encrypted_password LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system',
    lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_mcedt_credential_singleton CHECK (id = 1)
) ENGINE=InnoDB;
INSERT IGNORE INTO mcedt_credential (id, encrypted_password, lastUpdateUser) VALUES (1, NULL, 'migration');

-- Legacy migration must lock only the matching namespace, not scan unrelated
-- property rows. A non-unique index preserves existing duplicate-name behavior.
CREATE INDEX IF NOT EXISTS idx_property_name ON property (name);
