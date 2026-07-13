-- Update script for name sanitization
-- Prefixes all demographic first and last names with "FAKE-" for the devcontainer environment

-- Update first names
UPDATE demographic
SET first_name = CONCAT('FAKE-', first_name)
WHERE first_name NOT LIKE 'FAKE-%'
  AND first_name IS NOT NULL
  AND first_name != '';

-- Update last names
UPDATE demographic
SET last_name = CONCAT('FAKE-', last_name)
WHERE last_name NOT LIKE 'FAKE-%'
  AND last_name IS NOT NULL
  AND last_name != '';
