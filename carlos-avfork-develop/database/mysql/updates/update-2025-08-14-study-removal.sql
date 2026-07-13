-- Migration script to remove study-related and HSFO2 tables
-- Date: 2025-08-14
-- Description: Removes all study module and HSFO2 tables and related data

-- Drop foreign key constraints first if they exist
-- Note: Add any foreign key drops here if needed

-- Drop study-related tables
DROP TABLE IF EXISTS studydata;
DROP TABLE IF EXISTS studylogin;
DROP TABLE IF EXISTS demographicstudy;
DROP TABLE IF EXISTS providerstudy;
DROP TABLE IF EXISTS rehabStudy2004;
DROP TABLE IF EXISTS study;

-- Drop HSFO (Heart and Stroke Foundation of Ontario) related tables
DROP TABLE IF EXISTS form_hsfo2_visit;
DROP TABLE IF EXISTS form_hsfo_visit;
DROP TABLE IF EXISTS hsfo2_patient;
DROP TABLE IF EXISTS hsfo2_system;
DROP TABLE IF EXISTS hsfo_patient;
DROP TABLE IF EXISTS hsfo_system;
DROP TABLE IF EXISTS hsfo_recommit_schedule;

-- Remove HSFO form entries from encounterForm table
DELETE FROM encounterForm WHERE form_table IN ('form_hsfo2_visit', 'form_hsfo_visit');
DELETE FROM encounterForm WHERE form_name IN ('HMP Form', 'HMI form', 'HSFO2');
