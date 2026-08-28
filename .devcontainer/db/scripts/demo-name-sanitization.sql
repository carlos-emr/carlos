-- ---------------------------------------------------------------------------
-- CARLOS demo/development data — name sanitization v2 (common-schema tables)
--
-- Applied AFTER the development/demo dataset is loaded, by:
--   * the devcontainer init script (.devcontainer/db/scripts/populate_db.sh)
--   * the deb demo loader (carlos-ctl demo-data)
--
-- Supersedes database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql
-- (which only covered demographic). That file is frozen history; do not edit it.
--
-- Contract for every statement in this file:
--   1. The prefix is exactly 'FAKE-'. Every prefixing UPDATE is guarded with
--      NOT LIKE 'FAKE-%' so re-running this file can never produce FAKE-FAKE-.
--   2. Flyway-seeded rows must come out byte-identical. Each UPDATE states why
--      it cannot match a row the schema migrations seed. The only person-name
--      rows Flyway seeds are provider -1 (system) and 999998 (carlosdoc,
--      'doctor, doctor' — login checks depend on it) and the mygroup row for
--      provider 88888 ('Support','IT'); those are exempted by allowlist.
--   3. Known real-person names in the dev snapshot are REPLACED, not just
--      prefixed (see the replacement block at the end).
--
-- Every table below exists in the province-neutral baseline
-- (database/mysql/migration/common/V1__baseline_schema.sql), so this file is
-- safe to run on both ON and BC databases. Ontario-only tables are handled in
-- demo-name-sanitization-on.sql.
-- ---------------------------------------------------------------------------

-- === Patients ==============================================================
-- Flyway seeds zero demographic rows; every row here is demo data.
UPDATE demographic
SET first_name = CONCAT('FAKE-', first_name)
WHERE first_name NOT LIKE 'FAKE-%'
  AND first_name IS NOT NULL
  AND first_name != '';

UPDATE demographic
SET last_name = CONCAT('FAKE-', last_name)
WHERE last_name NOT LIKE 'FAKE-%'
  AND last_name IS NOT NULL
  AND last_name != '';

-- === Providers =============================================================
-- Allowlist: -1 (system) and 999998 (carlosdoc 'doctor, doctor') are the only
-- provider rows Flyway seeds, and the login/Playwright checks depend on their
-- names staying as-is. Everything else is demo data.
UPDATE provider
SET first_name = CONCAT('FAKE-', first_name)
WHERE provider_no NOT IN ('-1', '999998')
  AND first_name NOT LIKE 'FAKE-%'
  AND first_name IS NOT NULL
  AND first_name != '';

UPDATE provider
SET last_name = CONCAT('FAKE-', last_name)
WHERE provider_no NOT IN ('-1', '999998')
  AND last_name NOT LIKE 'FAKE-%'
  AND last_name IS NOT NULL
  AND last_name != '';

-- providerArchive: Flyway seeds none; same allowlist for archived copies of
-- the functional accounts so archive history matches the live row.
UPDATE providerArchive
SET first_name = CONCAT('FAKE-', first_name)
WHERE provider_no NOT IN ('-1', '999998')
  AND first_name NOT LIKE 'FAKE-%'
  AND first_name IS NOT NULL
  AND first_name != '';

UPDATE providerArchive
SET last_name = CONCAT('FAKE-', last_name)
WHERE provider_no NOT IN ('-1', '999998')
  AND last_name NOT LIKE 'FAKE-%'
  AND last_name IS NOT NULL
  AND last_name != '';

-- mygroup carries denormalized provider names. Flyway seeds one row
-- (provider 88888, 'Support','IT') in both provinces — exempted alongside the
-- functional accounts so the seeded row stays byte-identical.
UPDATE mygroup
SET first_name = CONCAT('FAKE-', first_name)
WHERE provider_no NOT IN ('-1', '999998', '88888')
  AND first_name NOT LIKE 'FAKE-%'
  AND first_name IS NOT NULL
  AND first_name != '';

UPDATE mygroup
SET last_name = CONCAT('FAKE-', last_name)
WHERE provider_no NOT IN ('-1', '999998', '88888')
  AND last_name NOT LIKE 'FAKE-%'
  AND last_name IS NOT NULL
  AND last_name != '';

-- === Denormalized patient names ===========================================
-- appointment.name / appointmentArchive.name hold 'LAST,First' copies of the
-- patient name. Flyway seeds zero appointments. The single guarded statement
-- both prefixes the last name and injects the prefix after the comma; once a
-- row starts with FAKE- it is never touched again.
UPDATE appointment
SET name = CONCAT('FAKE-', REPLACE(name, ',', ',FAKE-'))
WHERE name NOT LIKE 'FAKE-%'
  AND name IS NOT NULL
  AND name != '';

UPDATE appointmentArchive
SET name = CONCAT('FAKE-', REPLACE(name, ',', ',FAKE-'))
WHERE name NOT LIKE 'FAKE-%'
  AND name IS NOT NULL
  AND name != '';

-- === HL7 lab metadata ======================================================
-- Flyway seeds zero hl7TextInfo rows. The dev lab rows use the sentinel names
-- TEST/PATIEN* that the lab UI checks and the seeded lab PDFs match on, so
-- sentinels are exempt; anything else gets the prefix.
UPDATE hl7TextInfo
SET first_name = CONCAT('FAKE-', first_name)
WHERE first_name NOT LIKE 'FAKE-%'
  AND first_name NOT LIKE 'TEST%'
  AND first_name NOT LIKE 'PATIEN%'
  AND first_name IS NOT NULL
  AND first_name != '';

UPDATE hl7TextInfo
SET last_name = CONCAT('FAKE-', last_name)
WHERE last_name NOT LIKE 'FAKE-%'
  AND last_name NOT LIKE 'TEST%'
  AND last_name NOT LIKE 'PATIEN%'
  AND last_name IS NOT NULL
  AND last_name != '';

-- === Forms with embedded patient names ====================================
-- Flyway seeds zero rows in any form* table.
-- formMMSE.pName holds 'LAST, First' (comma + space).
UPDATE formMMSE
SET pName = CONCAT('FAKE-', REPLACE(pName, ', ', ', FAKE-'))
WHERE pName NOT LIKE 'FAKE-%'
  AND pName IS NOT NULL
  AND pName != '';

UPDATE formRourke
SET c_pName = CONCAT('FAKE-', c_pName)
WHERE c_pName NOT LIKE 'FAKE-%'
  AND c_pName IS NOT NULL
  AND c_pName != '';

UPDATE formRourke2020
SET c_pName = CONCAT('FAKE-', c_pName)
WHERE c_pName NOT LIKE 'FAKE-%'
  AND c_pName IS NOT NULL
  AND c_pName != '';

-- === Denormalized provider names in clinical rows =========================
-- Flyway seeds zero preventions/drugs rows. provider_name copies the provider
-- display name; the functional accounts' names ('doctor', 'system') are
-- exempted so they match the un-prefixed live provider rows.
UPDATE preventions
SET provider_name = CONCAT('FAKE-', provider_name)
WHERE provider_name NOT LIKE 'FAKE-%'
  AND provider_name NOT IN ('doctor, doctor', 'doctor doctor', 'system, system', 'system')
  AND provider_name IS NOT NULL
  AND provider_name != '';

UPDATE drugs
SET outside_provider_name = CONCAT('FAKE-', outside_provider_name)
WHERE outside_provider_name NOT LIKE 'FAKE-%'
  AND outside_provider_name IS NOT NULL
  AND outside_provider_name != '';

-- === Referral specialists ==================================================
-- Flyway seeds zero professionalSpecialists rows in either province (the real
-- BC directory lives in billingreferral, handled by the demo loader). The demo
-- specialist seed (demo-specialists.sql) already ships FAKE- names, so its
-- rows are skipped by the guard; this covers the 3 legacy dev rows.
UPDATE professionalSpecialists
SET fName = CONCAT('FAKE-', fName)
WHERE fName NOT LIKE 'FAKE-%'
  AND fName IS NOT NULL
  AND fName != '';

UPDATE professionalSpecialists
SET lName = CONCAT('FAKE-', lName)
WHERE lName NOT LIKE 'FAKE-%'
  AND lName IS NOT NULL
  AND lName != '';

-- === Known real-person replacements =======================================
-- The dev snapshot carries a few names that match real people. These are
-- REPLACED (not just prefixed), longest string first so partial forms cannot
-- survive. REPLACE() is a no-op once the text is gone, so re-runs are safe.
-- All targeted tables (provider, casemgmt_note, drugs, document, hl7TextInfo)
-- have zero Flyway-seeded rows containing these strings.

-- Provider -1001 'Pomedli(Vaccine), Stephen' — replaced outright.
UPDATE provider
SET last_name = 'FAKE-VaxDoc', first_name = 'FAKE-Provider'
WHERE provider_no = '-1001'
  AND last_name LIKE '%Pomedli%';

UPDATE providerArchive
SET last_name = 'FAKE-VaxDoc', first_name = 'FAKE-Provider'
WHERE provider_no = '-1001'
  AND last_name LIKE '%Pomedli%';

-- Free-text occurrences (encounter notes, prescription instructions,
-- document descriptions, lab requesting-client).
UPDATE casemgmt_note
SET note = REPLACE(note, 'Stephen Pomedli', 'FAKE-Provider FAKE-VaxDoc')
WHERE note LIKE '%Stephen Pomedli%';

UPDATE casemgmt_note
SET note = REPLACE(note, 'Pomedli', 'FAKE-VaxDoc')
WHERE note LIKE '%Pomedli%';

UPDATE drugs
SET special = REPLACE(special, 'Stephen Pomedli', 'FAKE-Provider FAKE-VaxDoc')
WHERE special LIKE '%Stephen Pomedli%';

UPDATE drugs
SET special = REPLACE(special, 'Pomedli', 'FAKE-VaxDoc')
WHERE special LIKE '%Pomedli%';

UPDATE drugs
SET outside_provider_name = REPLACE(outside_provider_name, 'Pomedli', 'FAKE-VaxDoc')
WHERE outside_provider_name LIKE '%Pomedli%';

UPDATE hl7TextInfo
SET requesting_client = 'FAKE-REQUESTING-MD'
WHERE requesting_client LIKE '%AZIZI NAMINI%';

UPDATE document
SET docdesc = REPLACE(docdesc, 'Jacky Jones', 'FAKE-Jacky FAKE-Jones')
WHERE docdesc LIKE '%Jacky Jones%';
