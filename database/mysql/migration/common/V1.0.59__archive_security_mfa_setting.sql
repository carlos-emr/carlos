-- Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
-- Keep a security record's MFA on/off setting in its archive copy (issue #5120).
-- SecurityManager.updateSecurityRecord archives the old `security` row before every
-- change, but the archive had no column for `security.usingMfa`, so an audit could not
-- see whether MFA was on before a change. The column keeps the source table's name.
--
-- Only the on/off flag is archived. The MFA secret (`security.mfaSecret`) stays out.
-- NULL means "not recorded": rows archived before this migration carry no MFA state,
-- and back-filling 0 would claim MFA was off when nobody knows.
--
-- The information_schema guard supports both MySQL and MariaDB and makes a retry safe.
SET @security_archive_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'SecurityArchive'
            AND column_name = 'usingMfa'),
    'SELECT 1',
    'ALTER TABLE SecurityArchive ADD COLUMN usingMfa TINYINT(1) NULL DEFAULT NULL'
);
PREPARE security_archive_statement FROM @security_archive_ddl;
EXECUTE security_archive_statement;
DEALLOCATE PREPARE security_archive_statement;
