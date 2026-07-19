-- ============================================================================
-- GenericIntake Module Removal Information
-- ============================================================================
-- Date: 2025-08-14
-- Purpose: Document removal of GenericIntake module from PMmodule
-- 
-- The GenericIntake functionality has been removed from the application.
-- This was a form builder/editor system used in the PMmodule for creating
-- customizable intake forms for clients.
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
-- GenericIntake Module Tables:
-- -----------------------------
SELECT 'GenericIntake Tables Row Counts:' AS 'Information';
SELECT 'intake' AS 'Table Name', COUNT(*) AS 'Row Count' FROM intake
UNION ALL
SELECT 'intake_answer', COUNT(*) FROM intake_answer
UNION ALL
SELECT 'intake_answer_element', COUNT(*) FROM intake_answer_element
UNION ALL
SELECT 'intake_answer_validation', COUNT(*) FROM intake_answer_validation
UNION ALL
SELECT 'intake_node', COUNT(*) FROM intake_node
UNION ALL
SELECT 'intake_node_js', COUNT(*) FROM intake_node_js
UNION ALL
SELECT 'intake_node_label', COUNT(*) FROM intake_node_label
UNION ALL
SELECT 'intake_node_template', COUNT(*) FROM intake_node_template
UNION ALL
SELECT 'intake_node_type', COUNT(*) FROM intake_node_type;

-- Related Form Tables:
-- --------------------
SELECT '' AS '';
SELECT 'Related Form Tables Row Counts:' AS 'Information';
SELECT 'formIntakeInfo' AS 'Table Name', COUNT(*) AS 'Row Count' FROM formIntakeInfo
UNION ALL
SELECT 'formIntakeHx', COUNT(*) FROM formIntakeHx
UNION ALL
SELECT 'formintakea', COUNT(*) FROM formintakea
UNION ALL
SELECT 'formintakeb', COUNT(*) FROM formintakeb
UNION ALL
SELECT 'formintakec', COUNT(*) FROM formintakec
UNION ALL
SELECT 'IntakeRequiredFields', COUNT(*) FROM IntakeRequiredFields;

-- Foreign Key Dependencies:
-- -------------------------
SELECT '' AS '';
SELECT 'Tables with Foreign Key References:' AS 'Information';
SELECT 'program_queue (intake_id column)' AS 'Table', 
       COUNT(DISTINCT intake_id) AS 'Distinct intake_id values' 
FROM program_queue WHERE intake_id IS NOT NULL;

-- ============================================================================
-- MANUAL CLEANUP INSTRUCTIONS
-- ============================================================================
-- 
-- After reviewing the data in the above tables, if you decide to drop them,
-- use the following commands (UNCOMMENT AND RUN MANUALLY):
--
-- -- Drop GenericIntake tables:
-- -- ALTER TABLE program_queue DROP FOREIGN KEY IF EXISTS FK_program_queue_intake;
-- -- ALTER TABLE program_queue DROP COLUMN IF EXISTS intake_id;
-- -- DROP TABLE IF EXISTS intake_answer_validation;
-- -- DROP TABLE IF EXISTS intake_answer_element;
-- -- DROP TABLE IF EXISTS intake_answer;
-- -- DROP TABLE IF EXISTS intake_node_js;
-- -- DROP TABLE IF EXISTS intake_node_label;
-- -- DROP TABLE IF EXISTS intake_node_template;
-- -- DROP TABLE IF EXISTS intake_node_type;
-- -- DROP TABLE IF EXISTS intake_node;
-- -- DROP TABLE IF EXISTS intake;
--
-- -- Drop Related Form tables:
-- -- DROP TABLE IF EXISTS IntakeRequiredFields;
-- -- DROP TABLE IF EXISTS formintakec;
-- -- DROP TABLE IF EXISTS formintakeb;
-- -- DROP TABLE IF EXISTS formintakea;
-- -- DROP TABLE IF EXISTS formIntakeHx;
-- -- DROP TABLE IF EXISTS formIntakeInfo;
--
-- ============================================================================
-- REMOVED FILES AND DIRECTORIES
-- ============================================================================
-- The following directories and their contents have been removed:
-- - /src/main/webapp/PMmodule/GenericIntake/ (entire directory)
--
-- Java packages removed:
-- - org.oscarehr.PMmodule.model.Intake* (all intake model classes)
-- - org.oscarehr.PMmodule.daos.GenericIntake* (all intake DAOs)
-- - org.oscarehr.PMmodule.service.GenericIntakeManager*
-- - org.oscarehr.PMmodule.www.GenericIntake* (all intake actions)
-- - org.oscarehr.PMmodule.exporter.DATIS* (DATIS export functionality)
-- - org.oscarehr.PMmodule.streethealth.StreetHealthIntakeReport*
--
-- Configuration files updated:
-- - Spring beans removed from applicationContextCaisi.xml
-- - Struts action mappings removed from struts.xml
-- - Hibernate mappings removed for intake models
--
-- CSS/JavaScript files removed:
-- - /css/genericIntake.css
-- - /css/genericIntakeReport.css
-- - /js/genericIntake.js
--
-- ============================================================================
-- NOTES
-- ============================================================================
-- The GenericIntake module was deeply integrated with:
-- - PMmodule ClientManager (forms display)
-- - Program Queue (intake_id foreign key)
-- - OCAN export functionality
-- - Street Health reporting
-- - DATIS export system
--
-- These integrations have been updated to function without the intake module.
--
-- Note: Before dropping tables, ensure you have a backup of your database
-- and verify that no custom reports or integrations depend on this data.
-- ============================================================================
