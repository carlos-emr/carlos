-- Grant admin users access to site-aware admin views.
--
-- Fresh installs seed this privilege in oscardata.sql. Existing installs need
-- the same row so migrated admin gates do not fail with a 500 for admin users.

INSERT IGNORE INTO `secObjectName`
    (`objectName`, `description`, `orgapplicable`)
VALUES
    ('_site_access_privacy', 'restrict access to only the assigned sites of a provider', 0);

INSERT IGNORE INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('admin', '_site_access_privacy', 'x', 0, '999998');
