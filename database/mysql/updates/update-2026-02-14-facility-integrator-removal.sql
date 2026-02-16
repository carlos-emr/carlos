-- =====================================================
-- Facility Integrator Column Removal
-- Date: 2026-02-14
-- =====================================================
--
-- This script removes CAISI integrator columns and the allowSims
-- column from the Facility table. These columns are no longer
-- referenced by any application code after the complete removal
-- of the CAISI integrator subsystem.
--
-- Columns removed:
--   integratorEnabled          - Whether this facility synced with the integrator
--   integratorUrl              - URL of the CAISI integrator web service
--   integratorUser             - Username for integrator authentication
--   integratorPassword         - Password for integrator authentication
--   enableIntegratedReferrals  - Whether cross-facility referrals were enabled
--   allowSims                  - SIMS (System for Integrated Management of Services) opt-in
--
-- IMPORTANT: All application code references to these columns have been removed.
-- These ALTER statements will drop the orphaned columns from the database schema.
-- =====================================================

-- Step 1: Remove integrator connection columns
ALTER TABLE Facility DROP COLUMN IF EXISTS integratorEnabled;
ALTER TABLE Facility DROP COLUMN IF EXISTS integratorUrl;
ALTER TABLE Facility DROP COLUMN IF EXISTS integratorUser;
ALTER TABLE Facility DROP COLUMN IF EXISTS integratorPassword;

-- Step 2: Remove integrated referrals flag
ALTER TABLE Facility DROP COLUMN IF EXISTS enableIntegratedReferrals;

-- Step 3: Remove SIMS flag
ALTER TABLE Facility DROP COLUMN IF EXISTS allowSims;

-- =====================================================
-- CAISI Integrator Removal Summary:
-- - 555+ Java/JSP/XML files deleted (integrator subsystem)
-- - caisi_integrator_client_stubs and integrator-objects JARs removed
-- - All Facility.isIntegratorEnabled() callers removed
-- - All integrator Spring beans and scheduled jobs removed
-- - All integrator HBM mappings and DAO classes removed
-- =====================================================
