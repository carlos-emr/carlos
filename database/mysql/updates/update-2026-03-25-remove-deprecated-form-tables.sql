-- ============================================================================
-- Remove Deprecated Form Tables: formAR, formintakeb, formONAR, formONAREnhanced*
-- ============================================================================
-- Date: 2026-03-25
-- Purpose: Remove deprecated antenatal record and intake form tables that
--          are no longer used in CARLOS EMR and cause row-size errors on
--          fresh installations (ERROR 1118: Row size too large > 8126).
--
-- Tables removed:
--   formAR                    - Legacy antenatal record form
--   formintakeb               - Legacy intake form B (generic intake module)
--   formONAR                  - Ontario Antenatal Record form
--   formONAREnhancedRecord    - Enhanced Ontario Antenatal Record
--   formONAREnhancedRecordExt1 - Extension table 1
--   formONAREnhancedRecordExt2 - Extension table 2
-- ============================================================================

-- Remove encounterForm entries referencing deprecated tables
DELETE FROM encounterForm WHERE form_table IN (
    'formAR',
    'formintakeb',
    'formONAR',
    'formONAREnhancedRecord'
);

-- Drop deprecated form tables (safe to run multiple times)
DROP TABLE IF EXISTS formONAREnhancedRecordExt2;
DROP TABLE IF EXISTS formONAREnhancedRecordExt1;
DROP TABLE IF EXISTS formONAREnhancedRecord;
DROP TABLE IF EXISTS formONAR;
DROP TABLE IF EXISTS formintakeb;
DROP TABLE IF EXISTS formAR;
