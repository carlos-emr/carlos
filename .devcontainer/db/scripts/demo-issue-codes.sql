-- ---------------------------------------------------------------------------
-- CARLOS demo/development data — demo-only issue codes
--
-- The issue catalog is Flyway-owned (71 rows in V1.0.2) and excluded from
-- the additive demo transform, but the dev snapshot's catalog carries ONE
-- extra row the migrations do not: issue_id 73 'ExternalNote', the system
-- issue NoteDisplayLocal.isExternalNote() keys on. The snapshot's
-- casemgmt_issue rows (which DO load additively) reference it, and an
-- unresolved issue pointing at a missing catalog row breaks the whole
-- eChart issues module for that patient ("Could not retrieve data for
-- unresolvedIssues", failed note locks, 500 on the encounter reload).
--
-- Seed exactly that row, guarded so it never duplicates an ExternalNote a
-- future migration might add under another id, and IGNOREd so a foreign
-- occupant of id 73 fails soft rather than aborting the demo load. In the
-- devcontainer flow development.sql truncate-reloads the catalog with this
-- row included, so this file is a no-op there.
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO issue (issue_id, code, description, role, update_date, priority, type, sortOrderId)
SELECT 73, 'ExternalNote', 'External Note', 'nurse', '2024-01-29 23:18:48', NULL, 'system', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM issue WHERE code = 'ExternalNote');
