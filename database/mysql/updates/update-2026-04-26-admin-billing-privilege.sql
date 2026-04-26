-- update-2026-04-26-admin-billing-privilege.sql
--
-- Grant `_admin.billing` access to the `admin` role. This privilege has
-- existed in `secObjectName` since `oscardata.sql:1511` but the seed
-- never inserted a corresponding `secObjPrivilege` row for the admin
-- role. As a result, admin users hitting any 2Action that enforces
-- `_admin.billing` (e.g. AddEditServiceCode2Action) got a 500 from
-- `SecurityException: missing required sec object (_admin.billing)`.
--
-- The dev DB's admin role already has `_admin x` plus many specific
-- `_admin.*` entries (caisi, demographic, document, eform, fax, etc.);
-- `_admin.billing` is just missing from that set. Adding it brings the
-- admin role's privilege bag in line with what the menu links
-- ("Manage Billing Service Code", etc.) require.
--
-- Idempotent via INSERT IGNORE — safe to re-run on databases that
-- already have the row.
INSERT IGNORE INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('admin', '_admin.billing', 'x', 0, '999998');
