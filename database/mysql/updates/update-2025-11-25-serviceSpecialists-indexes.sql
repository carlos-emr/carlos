--
-- Consultation system performance improvements
--
-- This migration adds indexes to the consultation-related tables for faster queries.
-- No data is modified or deleted - only indexes are added.
--
-- Note: Uses DROP INDEX IF EXISTS before CREATE INDEX to make this migration
-- is safe to run multiple times or on new databases.
--

-- ============================================================================
-- PART 1: serviceSpecialists junction table
-- ============================================================================

-- Index on serviceId for lookups ("find all specialists for a service")
DROP INDEX IF EXISTS idx_serviceSpecialists_serviceId ON serviceSpecialists;
CREATE INDEX idx_serviceSpecialists_serviceId ON serviceSpecialists(serviceId);

-- Index on specId for reverse lookups ("find all services for a specialist")
DROP INDEX IF EXISTS idx_serviceSpecialists_specId ON serviceSpecialists;
CREATE INDEX idx_serviceSpecialists_specId ON serviceSpecialists(specId);

-- ============================================================================
-- PART 2: consultationServices table
-- ============================================================================

-- Index for findActive() and findByDescription() queries
-- Covers: WHERE active='1' ORDER BY serviceDesc
DROP INDEX IF EXISTS idx_consultationServices_active_desc ON consultationServices;
CREATE INDEX idx_consultationServices_active_desc ON consultationServices(active, serviceDesc);

-- ============================================================================
-- PART 3: professionalSpecialists table
-- ============================================================================

-- Index for name searches (findByFullName, findByLastName, search)
-- Covers: WHERE lName LIKE ? [AND fName LIKE ?] ORDER BY lName
DROP INDEX IF EXISTS idx_professionalSpecialists_name ON professionalSpecialists;
CREATE INDEX idx_professionalSpecialists_name ON professionalSpecialists(`lName`, `fName`);

-- Index for referral number lookups (findByReferralNo, getByReferralNo)
DROP INDEX IF EXISTS idx_professionalSpecialists_referralNo ON professionalSpecialists;
CREATE INDEX idx_professionalSpecialists_referralNo ON professionalSpecialists(`referralNo`);

-- Index for specialty searches (findBySpecialty)
DROP INDEX IF EXISTS idx_professionalSpecialists_specType ON professionalSpecialists;
CREATE INDEX idx_professionalSpecialists_specType ON professionalSpecialists(`specType`);

-- Index for institution foreign key lookups
DROP INDEX IF EXISTS idx_professionalSpecialists_institution ON professionalSpecialists;
CREATE INDEX idx_professionalSpecialists_institution ON professionalSpecialists(`institutionId`);

-- Index for department foreign key lookups
DROP INDEX IF EXISTS idx_professionalSpecialists_department ON professionalSpecialists;
CREATE INDEX idx_professionalSpecialists_department ON professionalSpecialists(`departmentId`);

-- ============================================================================
-- PART 4: Cleanup deprecated table
-- ============================================================================

-- Drop the specialistsJavascript table - no longer used.
-- This table stored pre-generated JavaScript for caching, which has been replaced
-- by direct database queries with proper indexing.
DROP TABLE IF EXISTS specialistsJavascript;
