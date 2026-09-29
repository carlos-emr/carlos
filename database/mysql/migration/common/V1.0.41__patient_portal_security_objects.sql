-- Patient portal integration security objects (issue #3475).
--
-- Each CARLOS object gates one portal permission. The action holding the object sends the matching
-- permission in the signed X-CARLOS-Staff-Assertion; a provider without the object never has the
-- permission claimed on their behalf. See PortalStaffContextResolver, which is the only place a
-- portal identity is built.
--
--   _portal.invite          -> portal.invite.manage
--   _portal.account         -> portal.account.manage
--   _portal.account.unlock  -> portal.account.unlock
--   _portal.secret          -> portal.secret.manage
--   _portal.contact.review  -> portal.contact.review
--
-- Unlock is split from general account management on the _admin.fax / _admin.fax.restart
-- precedent: clearing a lockout forces a password reset on the patient, which is a heavier act
-- than reading account status and belongs to a narrower group.
INSERT IGNORE INTO secObjectName (objectName, description, orgapplicable) VALUES
    ('_portal.invite', 'Issue and revoke patient portal invitations', '0'),
    ('_portal.account', 'View and enable/disable patient portal accounts', '0'),
    ('_portal.account.unlock', 'Clear a patient portal lockout, forcing a password reset', '0'),
    ('_portal.secret', 'Manage passphrases for encrypted patient messages', '0'),
    ('_portal.contact.review', 'Review patient portal contact changes', '0');

-- Everything is granted to admin. Whether front-desk staff issue invitations is a clinic's workflow
-- decision, not this migration's, so other roles are added per deployment.
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES
    ('admin', '_portal.invite', 'x', 0, '999998'),
    ('admin', '_portal.account', 'x', 0, '999998'),
    ('admin', '_portal.account.unlock', 'x', 0, '999998'),
    ('admin', '_portal.secret', 'x', 0, '999998'),
    ('admin', '_portal.contact.review', 'x', 0, '999998');

-- Portal email passwords work for clinicians without an administrator granting rights by hand.
-- Sending an encrypted email through the portal needs _portal.account read (is the account usable?)
-- and _portal.secret read and write (request, publish or withdraw the password). These are the
-- doctor and nursing roles the baseline data ships in every province. Account access is read-only:
-- enabling, disabling and unlocking a portal account stay with admin. Sending still needs _email
-- write, which the baseline grants only to doctor and admin, so the other roles can send once a
-- clinic grants them _email.
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES
    ('doctor', '_portal.account', 'r', 0, '999998'),
    ('doctor', '_portal.secret', 'x', 0, '999998'),
    ('locum', '_portal.account', 'r', 0, '999998'),
    ('locum', '_portal.secret', 'x', 0, '999998'),
    ('psychiatrist', '_portal.account', 'r', 0, '999998'),
    ('psychiatrist', '_portal.secret', 'x', 0, '999998'),
    ('nurse', '_portal.account', 'r', 0, '999998'),
    ('nurse', '_portal.secret', 'x', 0, '999998'),
    ('Nurse Manager', '_portal.account', 'r', 0, '999998'),
    ('Nurse Manager', '_portal.secret', 'x', 0, '999998'),
    ('RN', '_portal.account', 'r', 0, '999998'),
    ('RN', '_portal.secret', 'x', 0, '999998'),
    ('RPN', '_portal.account', 'r', 0, '999998'),
    ('RPN', '_portal.secret', 'x', 0, '999998');
