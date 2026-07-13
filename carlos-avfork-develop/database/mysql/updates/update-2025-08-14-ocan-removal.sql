-- ============================================================================
-- OCAN Module Removal Information
-- ============================================================================
-- Date: 2025-08-14
-- Purpose: Document removal of OCAN (Ontario Common Assessment of Need) module
-- 
-- The OCAN functionality has been removed from the application.
-- This was a comprehensive mental health assessment tool used for evaluating
-- client needs across multiple domains. The system supported both OCAN 1.0
-- and OCAN 2.0 (FULL, CORE, and SELF assessment types).
--
-- ============================================================================
-- DATABASE TABLES INFORMATION (NOT AUTOMATICALLY DROPPED)
-- ============================================================================
--
-- IMPORTANT: The following tables are NO LONGER USED by the application.
-- This script does NOT automatically drop these tables to prevent data loss.
-- Please review each table and manually drop them if they contain no important data.
--
-- To check row counts in these tables, run the queries below:
--
-- OCAN Module Tables:
-- -------------------
SELECT 'OCAN Tables Row Counts:' AS 'Information';
SELECT 'OcanStaffForm' AS 'Table Name', COUNT(*) AS 'Row Count' FROM OcanStaffForm
UNION ALL
SELECT 'OcanStaffFormData', COUNT(*) FROM OcanStaffFormData
UNION ALL
SELECT 'OcanClientForm', COUNT(*) FROM OcanClientForm
UNION ALL
SELECT 'OcanClientFormData', COUNT(*) FROM OcanClientFormData
UNION ALL
SELECT 'OcanFormOption', COUNT(*) FROM OcanFormOption
UNION ALL
SELECT 'OcanConnexOption', COUNT(*) FROM OcanConnexOption
UNION ALL
SELECT 'OcanSubmissionLog', COUNT(*) FROM OcanSubmissionLog
UNION ALL
SELECT 'OcanSubmissionRecordLog', COUNT(*) FROM OcanSubmissionRecordLog;

-- ============================================================================
-- Manual Cleanup Steps (if needed):
-- ============================================================================
--
-- If you decide to remove the OCAN data after review, use these commands:
-- 
-- -- DROP TABLE IF EXISTS OcanSubmissionRecordLog;
-- -- DROP TABLE IF EXISTS OcanSubmissionLog;
-- -- DROP TABLE IF EXISTS OcanStaffFormData;
-- -- DROP TABLE IF EXISTS OcanClientFormData;
-- -- DROP TABLE IF EXISTS OcanStaffForm;
-- -- DROP TABLE IF EXISTS OcanClientForm;
-- -- DROP TABLE IF EXISTS OcanFormOption;
-- -- DROP TABLE IF EXISTS OcanConnexOption;
--
-- ============================================================================
-- Files Removed:
-- ============================================================================
-- - /webapp/ocan/ directory with PDF documentation
-- - 14 OCAN form JSP files
-- - 2 CBI form JSP files  
-- - 3 OCAN report JSP files
-- - 200+ Java domain classes in /oscar/ocan/domain/
-- - 8 model classes
-- - 14 DAO classes
-- - 6 action/controller classes
-- - OCAN report generators and beans
-- - OCAN service and task processors
--
-- Configuration changes:
-- - Removed from struts.xml
-- - Removed from applicationContextCaisi.xml
-- - Removed from navigation menus
-- ============================================================================
