-- Patient portal integration security objects (issue #3475).
--
-- Each CARLOS object gates one portal permission. The action holding the object sends the matching
-- permission in X-CARLOS-Permissions; a provider without the object never has the permission
-- claimed on their behalf. See PortalStaffContextResolver, which is the only place a portal
-- identity is built.
--
--   _portal.invite          -> portal.invite.manage
--   _portal.account         -> portal.account.manage
--   _portal.account.unlock  -> portal.account.unlock
--   _portal.secret          -> portal.secret.manage
--   _portal.contact.review  -> portal.contact.review
--   _admin.portal           -> configure the CARLOS-to-portal connection (not a portal permission)
--
-- Unlock is split from general account management on the _admin.fax / _admin.fax.restart
-- precedent: clearing a lockout forces a password reset on the patient, which is a heavier act
-- than reading account status and belongs to a narrower group.
INSERT INTO secObjectName (objectName, description, orgapplicable) VALUES
    ('_portal.invite', 'Issue and revoke patient portal invitations', '0'),
    ('_portal.account', 'View and enable/disable patient portal accounts', '0'),
    ('_portal.account.unlock', 'Clear a patient portal lockout, forcing a password reset', '0'),
    ('_portal.secret', 'Manage passphrases for encrypted patient messages', '0'),
    ('_portal.contact.review', 'Review patient portal contact changes', '0'),
    ('_admin.portal', 'Configure the patient portal connection', '0');

-- Granted to admin only. Whether front-desk staff issue invitations is a clinic's workflow
-- decision, not this migration's, so other roles are added per deployment.
INSERT INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES
    ('admin', '_portal.invite', 'x', 0, '999998'),
    ('admin', '_portal.account', 'x', 0, '999998'),
    ('admin', '_portal.account.unlock', 'x', 0, '999998'),
    ('admin', '_portal.secret', 'x', 0, '999998'),
    ('admin', '_portal.contact.review', 'x', 0, '999998'),
    ('admin', '_admin.portal', 'x', 0, '999998');
