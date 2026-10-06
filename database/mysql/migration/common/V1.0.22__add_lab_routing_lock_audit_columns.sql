-- Ensure audit metadata also exists if V1.0.21 encountered a pre-existing
-- coordination table. Every clause is safe to repeat after an interrupted DDL step.
-- These rows represent application coordination keys, not clinical author actions;
-- actual acknowledgement/archival audits remain in their existing clinical tables.
ALTER TABLE providerLabRoutingLock
    ADD COLUMN IF NOT EXISTS lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE providerLabRoutingLock
    ADD COLUMN IF NOT EXISTS lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
