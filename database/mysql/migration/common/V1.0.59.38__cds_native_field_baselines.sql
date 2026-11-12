-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Additional field witnesses captured in the original native unit transaction.
-- NULL means legacy/unknown evidence. Do not backfill from current clinical rows.
ALTER TABLE cds_import_unit ADD COLUMN IF NOT EXISTS native_baseline LONGTEXT NULL;
ALTER TABLE cds_import_checkpoint ADD COLUMN IF NOT EXISTS native_baseline LONGTEXT NULL;
