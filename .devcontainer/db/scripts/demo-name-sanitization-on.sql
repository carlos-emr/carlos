-- ---------------------------------------------------------------------------
-- CARLOS demo/development data — name sanitization v2, Ontario supplement
--
-- formLabReq07 / formLabReq10 exist only in the Ontario schema
-- (database/mysql/migration/on/V1.0.1__on_schema.sql), so their sanitization
-- lives here and is applied only when the database was migrated for Ontario.
-- Running this file against a BC database would fail on the missing tables.
--
-- Same contract as demo-name-sanitization.sql: prefix is exactly 'FAKE-',
-- every UPDATE carries a NOT LIKE 'FAKE-%' guard (idempotent, never
-- FAKE-FAKE-), and Flyway seeds zero rows in either table so no seeded row
-- can be touched.
-- ---------------------------------------------------------------------------

UPDATE formLabReq07
SET patientFirstName = CONCAT('FAKE-', patientFirstName)
WHERE patientFirstName NOT LIKE 'FAKE-%'
  AND patientFirstName IS NOT NULL
  AND patientFirstName != '';

UPDATE formLabReq07
SET patientLastName = CONCAT('FAKE-', patientLastName)
WHERE patientLastName NOT LIKE 'FAKE-%'
  AND patientLastName IS NOT NULL
  AND patientLastName != '';

UPDATE formLabReq07
SET patientName = CONCAT('FAKE-', patientName)
WHERE patientName NOT LIKE 'FAKE-%'
  AND patientName IS NOT NULL
  AND patientName != '';

UPDATE formLabReq10
SET patientFirstName = CONCAT('FAKE-', patientFirstName)
WHERE patientFirstName NOT LIKE 'FAKE-%'
  AND patientFirstName IS NOT NULL
  AND patientFirstName != '';

UPDATE formLabReq10
SET patientLastName = CONCAT('FAKE-', patientLastName)
WHERE patientLastName NOT LIKE 'FAKE-%'
  AND patientLastName IS NOT NULL
  AND patientLastName != '';

UPDATE formLabReq10
SET patientName = CONCAT('FAKE-', patientName)
WHERE patientName NOT LIKE 'FAKE-%'
  AND patientName IS NOT NULL
  AND patientName != '';

-- "Copy results to" recipient on the lab requisition.
UPDATE formLabReq10
SET copyFname = CONCAT('FAKE-', copyFname)
WHERE copyFname NOT LIKE 'FAKE-%'
  AND copyFname IS NOT NULL
  AND copyFname != '';

UPDATE formLabReq10
SET copyLname = CONCAT('FAKE-', copyLname)
WHERE copyLname NOT LIKE 'FAKE-%'
  AND copyLname IS NOT NULL
  AND copyLname != '';
