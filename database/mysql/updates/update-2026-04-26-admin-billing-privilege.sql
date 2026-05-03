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
-- `_admin.billing` is just missing from that set. Grant the narrower write
-- privilege instead of all-permissions `x`; the billing administration
-- actions introduced here require `_admin.billing` write access.
--
-- Idempotent via INSERT IGNORE — safe to re-run on databases that
-- already have the row.
INSERT IGNORE INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('admin', '_admin.billing', 'w', 0, '999998');
