-- update-2026-05-03-admin-invoices-privilege.sql
--
-- BillingOnPayment2Action uses `_admin.invoices` read access to restrict
-- invoice administration to the current provider. Older databases do not
-- seed the secObjectName row, so the privilege check cannot be configured
-- reliably until the object exists.
--
-- Idempotent via INSERT IGNORE — safe to re-run on databases that already
-- have either row.
INSERT IGNORE INTO `secObjectName`
    (`objectName`, `description`, `orgapplicable`)
VALUES
    ('_admin.invoices', 'Restrict invoice admin to current provider', 0);

INSERT IGNORE INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('admin', '_admin.invoices', 'r', 0, '999998');
