-- Coordinate acknowledgement/status writes even when a provider routing row does not
-- exist yet. The primary-key upsert is held through the caller's transaction, including
-- unassigned-row archival/cleanup. Numeric report ids share a lock across lab types and
-- providers; multi-report callers acquire ids in ascending order to avoid lock inversion.
-- This contains only coordination ids. It does not rewrite, delete, or impose uniqueness
-- on existing clinical routing records (which may contain distinct legacy comments).
CREATE TABLE providerLabRoutingLock (
    lab_no INT NOT NULL,
    PRIMARY KEY (lab_no)
) ENGINE=InnoDB;
