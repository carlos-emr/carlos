-- Fix missing DEFAULT value on usingMfa column in security table.
-- oscarinit_2025.sql added this column as NOT NULL without a DEFAULT,
-- which causes INSERT failures in strict SQL mode when usingMfa is not
-- explicitly specified. This patch makes the column idempotent for fresh
-- installs by ensuring the column has DEFAULT FALSE.
-- Idempotent: safe to run multiple times.

ALTER TABLE security MODIFY COLUMN usingMfa BOOL NOT NULL DEFAULT FALSE;
