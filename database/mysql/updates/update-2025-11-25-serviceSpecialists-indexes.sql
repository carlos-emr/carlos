--
-- Consultation system performance improvements
--
-- This migration adds indexes to the consultation-related tables for faster queries.
-- No data is modified or deleted - only indexes are added.
--

-- ============================================================================
-- PART 1: serviceSpecialists junction table
-- ============================================================================

-- Index on serviceId for lookups ("find all specialists for a service")
CREATE INDEX idx_serviceSpecialists_serviceId ON serviceSpecialists(serviceId);

-- Index on specId for reverse lookups ("find all services for a specialist")
CREATE INDEX idx_serviceSpecialists_specId ON serviceSpecialists(specId);

-- ============================================================================
-- PART 2: consultationServices table
-- ============================================================================

-- Index for findActive() and findByDescription() queries
-- Covers: WHERE active='1' ORDER BY serviceDesc
CREATE INDEX idx_consultationServices_active_desc ON consultationServices(active, serviceDesc);

-- ============================================================================
-- PART 3: professionalSpecialists table
-- ============================================================================

-- Index for name searches (findByFullName, findByLastName, search)
-- Covers: WHERE lastName LIKE ? [AND firstName LIKE ?] ORDER BY lastName
CREATE INDEX idx_professionalSpecialists_name ON professionalSpecialists(lastName, firstName);

-- Index for referral number lookups (findByReferralNo, getByReferralNo)
CREATE INDEX idx_professionalSpecialists_referralNo ON professionalSpecialists(referralNo);

-- Index for specialty searches (findBySpecialty)
CREATE INDEX idx_professionalSpecialists_specType ON professionalSpecialists(specialtyType);

-- Index for institution foreign key lookups
CREATE INDEX idx_professionalSpecialists_institution ON professionalSpecialists(institutionId);

-- Index for department foreign key lookups
CREATE INDEX idx_professionalSpecialists_department ON professionalSpecialists(departmentId);

-- ============================================================================
-- PART 4: Cleanup deprecated table
-- ============================================================================

-- Drop the specialistsJavascript table - no longer used.
-- This table stored pre-generated JavaScript for caching, which has been replaced
-- by direct database queries with proper indexing.
DROP TABLE IF EXISTS specialistsJavascript;
