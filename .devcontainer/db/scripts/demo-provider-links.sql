-- ---------------------------------------------------------------------------
-- CARLOS demo/development data — provider ↔ facility links
--
-- provider_facility is Flyway-seeded (('999998',1) in both provinces) and
-- carries FOREIGN KEYs to provider and Facility, so the raw dev-snapshot
-- statement is excluded from the additive transform and replaced by this
-- guarded version. The demo load runs with FOREIGN_KEY_CHECKS=0, so the
-- EXISTS guards below take over the FKs' job: a link is only added when both
-- endpoints exist. The UNIQUE KEY (provider_no, facility_id) plus
-- INSERT IGNORE keeps re-runs no-ops and leaves the Flyway-seeded link
-- untouched.
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO provider_facility (provider_no, facility_id)
SELECT p.provider_no, f.id
FROM provider p
JOIN Facility f ON f.id = 1
WHERE p.provider_no IN ('5', '999998');
