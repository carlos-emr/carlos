-- Fix missing _admin.schedule privilege that left all schedule admin pages
-- inaccessible, including TemplateSetting, TemplateCodeSetting,
-- TemplateApplying, HolidaySetting, EditTemplate, and CreateDate.
--
-- New installs receive the row from oscardata.sql. This migration backfills
-- existing databases that already ran older seed data without the matching
-- admin privilege row.

INSERT IGNORE INTO `secObjectName` (`objectName`) VALUES ('_admin.schedule');
INSERT IGNORE INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
    VALUES ('admin', '_admin.schedule', 'x', 0, '999998');
