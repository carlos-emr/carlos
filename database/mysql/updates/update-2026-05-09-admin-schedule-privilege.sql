-- Fix missing _admin.schedule privilege that left all schedule admin pages
-- inaccessible, including TemplateSetting, TemplateCodeSetting,
-- TemplateApplying, HolidaySetting, EditTemplate, and CreateDate.
--
-- secObjectName row for _admin.schedule was added in oscardata.sql (line 1510)
-- but the matching secObjPrivilege row for the admin role was never inserted,
-- so hasPrivilege() returned false for every user including carlosdoc, causing
-- a SecurityException on every schedule administration page.
--
-- References: Bug #2121

INSERT IGNORE INTO `secObjectName` (`objectName`) VALUES ('_admin.schedule');
INSERT IGNORE INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
    VALUES ('admin', '_admin.schedule', 'x', 0, '999998');
