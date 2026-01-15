-- Standardize ctl_doctype module name from 'providers' (plural) to 'provider' (singular)
-- to match the naming convention used by the 'demographic' module and to match the development.sql INSERTS
UPDATE ctl_doctype SET module = 'provider' WHERE module = 'providers';