-- Restore current baseline Administration privileges after development.sql
-- replaces secObjPrivilege with its older demo snapshot. This local-only seed
-- is also safe to run against an existing development database.

INSERT IGNORE INTO `secObjectName`
    (`objectName`, `description`, `orgapplicable`)
VALUES
    ('_admin.flowsheet', 'Manage Flowsheets', 0),
    ('_admin.invoices', 'Restrict invoice admin to current provider', 0),
    ('_admin.schedule.groupCreate', 'Create schedule provider groups', 0);

INSERT INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('admin', '_admin.auditLogPurge', 'x', 0, '999998'),
    ('admin', '_admin.flowsheet', 'x', 0, '999998'),
    ('admin', '_admin.invoices', 'r', 0, '999998'),
    ('admin', '_admin.misc', 'x', 0, '999998'),
    ('admin', '_admin.schedule', 'x', 0, '999998'),
    ('admin', '_admin.schedule.groupCreate', 'x', 0, '999998'),
    ('admin', '_site_access_privacy', 'x', 0, '999998'),
    ('999998', '_admin.schedule.groupCreate', 'o', 1, '999998')
ON DUPLICATE KEY UPDATE
    `privilege` = VALUES(`privilege`),
    `priority` = VALUES(`priority`),
    `provider_no` = VALUES(`provider_no`);

-- Keep the development snapshot aligned with the current baseline cleanup.
DELETE FROM `secObjPrivilege`
WHERE `objectName` = '_admin.traceability';

DELETE FROM `secObjectName`
WHERE `objectName` = '_admin.traceability';
