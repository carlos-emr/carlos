-- ============================================================================
-- Remove Deprecated Form Tables: formAR, formintakeb, formONAR, formONAREnhanced*,
--   formType2Diabetes, formAdf, formIntakeHx
-- ============================================================================
-- Date: 2026-03-25
-- Purpose: Remove deprecated form tables that are no longer used in CARLOS EMR.
--          Several cause row-size errors on fresh installations
--          (ERROR 1118: Row size too large > 8126).
--
-- Tables removed:
--   formAR                    - Legacy antenatal record form
--   formintakeb               - Legacy intake form B (generic intake module)
--   formONAR                  - Ontario Antenatal Record form
--   formONAREnhancedRecord    - Enhanced Ontario Antenatal Record
--   formONAREnhancedRecordExt1 - Extension table 1
--   formONAREnhancedRecordExt2 - Extension table 2
--   formType2Diabetes         - Type 2 Diabetes form (replaced by newer forms)
--   formAdf                   - ADF form (replaced by formAdfV2)
--   formIntakeHx              - Intake History form (deprecated)
-- ============================================================================

-- Remove encounterForm entries referencing deprecated tables
DELETE FROM encounterForm WHERE form_table IN (
    'formAR',
    'formintakeb',
    'formONAR',
    'formONAREnhancedRecord',
    'formONAREnhancedRecordExt1',
    'formONAREnhancedRecordExt2',
    'formType2Diabetes',
    'formAdf',
    'formIntakeHx'
);

-- Drop deprecated form tables (safe to run multiple times)
DROP TABLE IF EXISTS formONAREnhancedRecordExt2;
DROP TABLE IF EXISTS formONAREnhancedRecordExt1;
DROP TABLE IF EXISTS formONAREnhancedRecord;
DROP TABLE IF EXISTS formONAR;
DROP TABLE IF EXISTS formintakeb;
DROP TABLE IF EXISTS formAR;
DROP TABLE IF EXISTS formType2Diabetes;
DROP TABLE IF EXISTS formAdf;
DROP TABLE IF EXISTS formIntakeHx;
