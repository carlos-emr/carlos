-- ---------------------------------------------------------------------------
-- CARLOS demo/development data — provider ↔ program links
--
-- program_provider is Flyway-seeded (V1.0.2 enrols carlosdoc in the 33
-- legacy community programs, occupying auto-increment ids 1-33) and the raw
-- dev-snapshot statement inserts its rows WITH explicit ids in that same
-- range — under the additive load's INSERT IGNORE every snapshot row
-- collides on the primary key and is silently dropped. The snapshot rows
-- are the ones that matter: they enrol the demo providers in program 10034
-- ("OSCAR", the Bed program every demo patient is admitted to), and without
-- them each patient's eChart answers "Access Denied … not in your program
-- domain". So the raw statement is excluded from the additive transform and
-- replaced by this guarded version, which lets AUTO_INCREMENT assign the ids.
--
-- The EXISTS guards keep re-runs no-ops and skip cleanly when either
-- endpoint is missing (program 10034 arrives with the additive snapshot;
-- the demo load runs with FOREIGN_KEY_CHECKS=0, so the JOINs take over the
-- FKs' job). In the devcontainer flow development.sql truncate-reloads the
-- table with these same rows first, so this file is a no-op there.
-- ---------------------------------------------------------------------------

INSERT INTO program_provider (program_id, provider_no, role_id, team_id)
SELECT pr.id, p.provider_no, m.role_id, NULL
FROM (SELECT '999998' AS provider_no, 2 AS role_id
      UNION ALL SELECT '1', 2
      UNION ALL SELECT '2', 2
      UNION ALL SELECT '3', 2
      UNION ALL SELECT '5', 3) m
JOIN provider p ON p.provider_no = m.provider_no
JOIN program pr ON pr.id = 10034
WHERE NOT EXISTS (SELECT 1 FROM program_provider x
                  WHERE x.program_id = pr.id AND x.provider_no = p.provider_no);
