-- Security objects for the SMS module: `_sms` for sending patient text messages and viewing
-- SMS history, `_admin.sms` for configuring and operating SMS. `_msgSMS` (reading a stored
-- message body, V1.0.25) already exists and is unchanged.
--
-- Every statement is idempotent so a partially applied or re-run migration is safe, and an
-- existing clinic grant, including a narrower one or an 'o' (no-rights) row, is preserved.
--
-- Numbered V1.0.31 because V1.0.29 and V1.0.30 were held by open PRs (#3763, #3746, #3478,
-- #3681) when this was written; see database/mysql/migration/common/README.md.
--
-- secObjectName rows exist for the security-object admin UI, which lists objects from this
-- table. hasPrivilege() never reads it: the grants further down are what open the gate.

INSERT INTO `secObjectName` (`objectName`, `description`, `orgapplicable`)
SELECT '_sms', 'Send patient text messages and view SMS history', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `secObjectName` WHERE `objectName` = '_sms');

INSERT INTO `secObjectName` (`objectName`, `description`, `orgapplicable`)
SELECT '_admin.sms', 'Configure and manage SMS', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `secObjectName` WHERE `objectName` = '_admin.sms');

-- Default grants mirror `_email` / `_admin.email` (and `_msgSMS` from V1.0.25): admin and
-- doctor hold all rights on `_sms`; only admin may configure and operate SMS. No other role
-- gets anything until a clinic grants it. `_email`'s '-1' row is deliberately not copied:
-- '-1' is the seeded system provider and hasPrivilege treats a provider number as a role, so
-- that row would grant `_sms` to any future session built for the system provider.
--
-- A clinic wanting a role that can view SMS history but not send gives it 'r':
-- SecurityInfoManager treats a grant as a ladder (x > w > u > r), and a history view asks
-- for 'r' while sending asks for 'w'.
--
-- CARLOS does not infer dotted-object privileges: `admin` = 'x' on `_admin` confers nothing
-- on `_admin.sms`, so the explicit row below is required (see V1.0.28 for the same trap).
-- (roleUserGroup, objectName) is the primary key, so one row per role and object.

INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
SELECT 'admin', '_sms', 'x', 0, '999998'
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM `secObjPrivilege` WHERE `roleUserGroup` = 'admin' AND `objectName` = '_sms'
 );

INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
SELECT 'doctor', '_sms', 'x', 0, '999998'
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM `secObjPrivilege` WHERE `roleUserGroup` = 'doctor' AND `objectName` = '_sms'
 );

INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
SELECT 'admin', '_admin.sms', 'x', 0, '999998'
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM `secObjPrivilege` WHERE `roleUserGroup` = 'admin' AND `objectName` = '_admin.sms'
 );
