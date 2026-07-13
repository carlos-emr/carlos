-- ============================================================================
-- Survey, Study and HSFO2 Module Removal Information
-- ============================================================================
-- Date: 2025-08-13 (Updated: 2025-01-14)
-- Purpose: Document removal of Survey, Study and HSFO2 modules from the codebase
-- 
-- The following functionality has been removed from the application:
-- - Survey module (questionnaire/survey management)
-- - Study module (clinical research study management)  
-- - HSFO2 module (Heart and Stroke Foundation of Ontario integration)
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
-- Survey/Study Module Tables:
-- ---------------------------
SELECT 'Survey/Study Tables Row Counts:' AS 'Information';
SELECT 'survey' AS 'Table Name', COUNT(*) AS 'Row Count' FROM survey
UNION ALL
SELECT 'surveyData', COUNT(*) FROM surveyData  
UNION ALL
SELECT 'survey_test_instance', COUNT(*) FROM survey_test_instance
UNION ALL
SELECT 'survey_test_data', COUNT(*) FROM survey_test_data
UNION ALL
SELECT 'study', COUNT(*) FROM study
UNION ALL
SELECT 'studydata', COUNT(*) FROM studydata
UNION ALL
SELECT 'studylogin', COUNT(*) FROM studylogin
UNION ALL
SELECT 'demographicstudy', COUNT(*) FROM demographicstudy
UNION ALL
SELECT 'providerstudy', COUNT(*) FROM providerstudy
UNION ALL
SELECT 'rehabStudy2004', COUNT(*) FROM rehabStudy2004;

-- HSFO2 Module Tables:
-- --------------------
SELECT '' AS '';
SELECT 'HSFO2 Tables Row Counts:' AS 'Information';
SELECT 'Hsfo2Patient' AS 'Table Name', COUNT(*) AS 'Row Count' FROM Hsfo2Patient
UNION ALL
SELECT 'Hsfo2Visit', COUNT(*) FROM Hsfo2Visit
UNION ALL
SELECT 'Hsfo2Provider', COUNT(*) FROM Hsfo2Provider
UNION ALL
SELECT 'form_hsfo2_visit', COUNT(*) FROM form_hsfo2_visit
UNION ALL
SELECT 'HsfoRecommitSchedule', COUNT(*) FROM HsfoRecommitSchedule;

-- ============================================================================
-- MANUAL CLEANUP INSTRUCTIONS
-- ============================================================================
-- 
-- After reviewing the data in the above tables, if you decide to drop them,
-- use the following commands (UNCOMMENT AND RUN MANUALLY):
--
-- -- Drop Survey/Study tables:
-- -- DROP TABLE IF EXISTS survey_test_data;
-- -- DROP TABLE IF EXISTS survey_test_instance;
-- -- DROP TABLE IF EXISTS surveyData;
-- -- DROP TABLE IF EXISTS survey;
-- -- DROP TABLE IF EXISTS studydata;
-- -- DROP TABLE IF EXISTS studylogin;
-- -- DROP TABLE IF EXISTS demographicstudy;
-- -- DROP TABLE IF EXISTS providerstudy;
-- -- DROP TABLE IF EXISTS study;
-- -- DROP TABLE IF EXISTS rehabStudy2004;
--
-- -- Drop HSFO2 tables:
-- -- DROP TABLE IF EXISTS form_hsfo2_visit;
-- -- DROP TABLE IF EXISTS Hsfo2Visit;
-- -- DROP TABLE IF EXISTS Hsfo2Patient;
-- -- DROP TABLE IF EXISTS Hsfo2Provider;
-- -- DROP TABLE IF EXISTS HsfoRecommitSchedule;
--
-- ============================================================================
-- REMOVED FILES AND DIRECTORIES
-- ============================================================================
-- The following directories and their contents have been removed:
-- - /src/main/webapp/study/
-- - /src/main/webapp/survey/
-- - /src/main/webapp/form/study/
-- - /src/main/webapp/form/hsfo2/
-- - /src/main/webapp/PMmodule/survey/
-- - /src/main/java/org/oscarehr/study/
-- - /src/main/java/org/oscarehr/survey/
-- - /src/main/java/oscar/form/study/
-- - /src/main/java/org/oscarehr/commons/daos/ (Survey/Study/HSFO-related DAOs)
-- - /src/main/java/org/oscarehr/commons/model/ (Survey/Study/HSFO-related models)
--
-- ============================================================================
-- NOTES
-- ============================================================================
-- Some Form classes in oscar.form package still contain references to rehabStudy2004
-- table for studyID field. These forms will continue to function but the studyID
-- field will no longer be populated. Consider updating these forms if needed:
-- - Frm2MinWalkRecord, FrmCESDRecord, FrmCaregiverRecord, FrmCostQuestionnaireRecord,
--   FrmFallsRecord, FrmGripStrengthRecord, FrmHomeFallsRecord, FrmIntakeInfoRecord,
--   FrmInternetAccessRecord, FrmLateLifeFDIDisabilityRecord, FrmLateLifeFDIFunctionRecord,
--   FrmSF36Record, FrmSF36CaregiverRecord, FrmSatisfactionScaleRecord,
--   FrmSelfAdministeredRecord, FrmSelfEfficacyRecord, FrmSelfManagementRecord,
--   FrmTreatmentPrefRecord
--
-- Note: Before dropping tables, ensure you have a backup of your database
-- and verify that no other parts of the system depend on this data.
-- ============================================================================