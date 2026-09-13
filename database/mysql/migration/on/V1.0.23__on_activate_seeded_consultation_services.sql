-- Activate the Ontario reference consultation services on installs that never curated them.
--
-- V1.0.2__on_data.sql seeds all 257 consultationServices rows with active = '02'
-- (ConsultationServiceDao.INACTIVE) where the BC seed ships the same rows as '1'
-- (ACTIVE). ConsultationServiceDao.findActive() filters on '1', so a fresh Ontario
-- install has no selectable service on the consultation request form and no specialty
-- on Add Specialist until an administrator activates them one by one.
--
-- Only an install where NO service is active is touched: a single '1' row means an
-- administrator has curated the list, and the services they chose to leave inactive are
-- kept that way. Idempotent: once anything is active the statement is a no-op. The
-- derived table is what lets MariaDB read consultationServices inside its own UPDATE.
UPDATE consultationServices
   SET active = '1'
 WHERE active = '02'
   AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM consultationServices WHERE active = '1' LIMIT 1) curated);
