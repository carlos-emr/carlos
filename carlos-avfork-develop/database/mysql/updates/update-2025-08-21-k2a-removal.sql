-- K2A (Know2Act) Service Removal Migration Script
-- Date: 2025-08-21
-- Description: Removes references to the discontinued K2A service from the database
-- 
-- IMPORTANT: All destructive operations are COMMENTED OUT for safety.
-- Review carefully and uncomment the appropriate sections when ready to execute.
-- Consider running archive operations first before any deletions.

-- ========================================================================
-- STEP 1: CREATE ARCHIVE TABLES (Safe - Creates backups)
-- ========================================================================
-- These commands create backup tables for any K2A-related data
-- Uncomment to create archives before deletion

-- Archive AppDefinition entries related to K2A
-- CREATE TABLE IF NOT EXISTS AppDefinition_k2a_archive_20250821 AS 
-- SELECT * FROM AppDefinition 
-- WHERE appName LIKE '%k2a%' 
--    OR appName LIKE '%know2act%'
--    OR appName = 'K2A'
--    OR description LIKE '%k2a%'
--    OR description LIKE '%know2act%';

-- Archive AppUser entries related to K2A applications
-- CREATE TABLE IF NOT EXISTS AppUser_k2a_archive_20250821 AS 
-- SELECT au.* FROM AppUser au
-- INNER JOIN AppDefinition ad ON au.appId = ad.id
-- WHERE ad.appName LIKE '%k2a%' 
--    OR ad.appName LIKE '%know2act%'
--    OR ad.appName = 'K2A';

-- Archive property table entries related to K2A
-- CREATE TABLE IF NOT EXISTS property_k2a_archive_20250821 AS
-- SELECT * FROM property 
-- WHERE name LIKE '%K2A%'
--    OR name LIKE '%k2a%'
--    OR name LIKE '%know2act%'
--    OR value LIKE '%know2act.org%'
--    OR value LIKE '%K2A%';

-- Archive any providers preferences related to K2A
-- CREATE TABLE IF NOT EXISTS provider_preference_k2a_archive_20250821 AS
-- SELECT * FROM provider_preference
-- WHERE prop_name LIKE '%k2a%'
--    OR prop_name LIKE '%K2A%'
--    OR prop_name LIKE '%know2act%'
--    OR prop_value LIKE '%k2a%'
--    OR prop_value LIKE '%know2act%';

-- ========================================================================
-- STEP 2: VERIFY ARCHIVES (Run these queries to verify backup data)
-- ========================================================================
-- Check archive counts before proceeding with deletions

-- SELECT 'AppDefinition_k2a_archive' as table_name, COUNT(*) as record_count 
-- FROM AppDefinition_k2a_archive_20250821
-- UNION ALL
-- SELECT 'AppUser_k2a_archive', COUNT(*) 
-- FROM AppUser_k2a_archive_20250821
-- UNION ALL
-- SELECT 'property_k2a_archive', COUNT(*) 
-- FROM property_k2a_archive_20250821
-- UNION ALL
-- SELECT 'provider_preference_k2a_archive', COUNT(*) 
-- FROM provider_preference_k2a_archive_20250821;

-- ========================================================================
-- STEP 3: DELETE K2A DATA (DANGEROUS - Currently commented out)
-- ========================================================================
-- WARNING: These operations will permanently remove data.
-- Only uncomment after confirming archives are complete and correct.

-- Delete AppUser entries for K2A applications
-- DELETE au FROM AppUser au
-- INNER JOIN AppDefinition ad ON au.appId = ad.id
-- WHERE ad.appName LIKE '%k2a%' 
--    OR ad.appName LIKE '%know2act%'
--    OR ad.appName = 'K2A';

-- Delete AppDefinition entries for K2A
-- DELETE FROM AppDefinition 
-- WHERE appName LIKE '%k2a%' 
--    OR appName LIKE '%know2act%'
--    OR appName = 'K2A'
--    OR description LIKE '%k2a%'
--    OR description LIKE '%know2act%';

-- Delete property entries related to K2A
-- DELETE FROM property 
-- WHERE name LIKE '%K2A%'
--    OR name LIKE '%k2a%'
--    OR name LIKE '%know2act%'
--    OR value LIKE '%know2act.org%'
--    OR value LIKE '%K2A%';

-- Delete providers preferences related to K2A
-- DELETE FROM provider_preference
-- WHERE prop_name LIKE '%k2a%'
--    OR prop_name LIKE '%K2A%'
--    OR prop_name LIKE '%know2act%'
--    OR prop_value LIKE '%k2a%'
--    OR prop_value LIKE '%know2act%';

-- ========================================================================
-- STEP 4: UPDATE REFERENCES (Currently commented out)
-- ========================================================================
-- Update any remaining references that might point to K2A URLs

-- Update any drugref URLs that still point to know2act.org
-- UPDATE property 
-- SET value = 'https://drugref.org/backend/api'  -- Replace with your preferred drugref URL
-- WHERE name = 'mydrugref_url' 
--   AND value LIKE '%know2act.org%';

-- Clear any K2A OAuth tokens that might still exist
-- UPDATE property
-- SET value = ''
-- WHERE name IN ('K2A_OAUTH_TOKEN', 'K2A_OAUTH_SECRET', 'K2A_ACCESS_TOKEN')
--    OR name LIKE '%k2a%token%';

-- ========================================================================
-- STEP 5: VERIFICATION QUERIES (Safe to run)
-- ========================================================================
-- Use these queries to verify K2A data has been removed

-- Check for remaining K2A references in AppDefinition
SELECT 'AppDefinition' as table_name, COUNT(*) as remaining_k2a_records 
FROM AppDefinition 
WHERE appName LIKE '%k2a%' 
   OR appName LIKE '%know2act%'
   OR appName = 'K2A'
   OR description LIKE '%k2a%'
   OR description LIKE '%know2act%';

-- Check for remaining K2A references in property table
SELECT 'property' as table_name, COUNT(*) as remaining_k2a_records 
FROM property 
WHERE name LIKE '%K2A%'
   OR name LIKE '%k2a%'
   OR name LIKE '%know2act%'
   OR value LIKE '%know2act.org%'
   OR value LIKE '%K2A%';

-- Check for remaining K2A references in provider_preference
SELECT 'provider_preference' as table_name, COUNT(*) as remaining_k2a_records 
FROM provider_preference
WHERE prop_name LIKE '%k2a%'
   OR prop_name LIKE '%K2A%'
   OR prop_name LIKE '%know2act%'
   OR prop_value LIKE '%k2a%'
   OR prop_value LIKE '%know2act%';

-- ========================================================================
-- ROLLBACK INSTRUCTIONS (If needed)
-- ========================================================================
-- If you need to restore K2A data after deletion, use these commands:
-- 
-- INSERT INTO AppDefinition SELECT * FROM AppDefinition_k2a_archive_20250821;
-- INSERT INTO AppUser SELECT * FROM AppUser_k2a_archive_20250821;
-- INSERT INTO property SELECT * FROM property_k2a_archive_20250821;
-- INSERT INTO provider_preference SELECT * FROM provider_preference_k2a_archive_20250821;

-- ========================================================================
-- CLEANUP ARCHIVE TABLES (Run after confirming everything works)
-- ========================================================================
-- After 30-60 days, if no issues, drop the archive tables:
-- 
-- DROP TABLE IF EXISTS AppDefinition_k2a_archive_20250821;
-- DROP TABLE IF EXISTS AppUser_k2a_archive_20250821;
-- DROP TABLE IF EXISTS property_k2a_archive_20250821;
-- DROP TABLE IF EXISTS provider_preference_k2a_archive_20250821;