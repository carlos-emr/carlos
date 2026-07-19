--
-- PHR (Personal Health Record) Component Removal
-- Date: 2025-08-24
-- Description: Remove PHR-related database tables that are no longer used
--
-- The following DROP TABLE statements are commented out as documentation
-- of what was removed. These tables were part of the PHR/MyOSCAR integration
-- that has been discontinued.
--

-- DROP TABLE IF EXISTS `phr_actions`;
-- DROP TABLE IF EXISTS `phr_documents`;  
-- DROP TABLE IF EXISTS `phr_document_ext`;

-- Tables removed:
-- phr_actions: Tracked PHR actions and exchanges
-- phr_documents: Stored PHR document metadata and content
-- phr_document_ext: Extended PHR document properties