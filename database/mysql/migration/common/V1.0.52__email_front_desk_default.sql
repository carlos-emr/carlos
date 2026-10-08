-- Front-desk staff send patient email by default (#4374, maintainer decision of 8 Oct 2026):
-- the receptionist, Medical Secretary and secretary roles get the same _email rights as doctors.
--
-- Only a role with no _email line at all gets one. INSERT IGNORE leaves alone a role that
-- already has an _email line at any level, including 'o' (no access), so a clinic's own choice
-- is kept. Limit: a clinic that deleted the line entirely cannot be told apart from one that
-- never set it, and gets the default.
--
-- Renumber before merge: take the next free version above the highest in BOTH develop and release/2026.08 at merge time.
INSERT IGNORE INTO secObjPrivilege
    (roleUserGroup, objectName, privilege, priority, provider_no)
VALUES
    ('receptionist',      '_email', 'x', 0, '999998'),
    ('Medical Secretary', '_email', 'x', 0, '999998'),
    ('secretary',         '_email', 'x', 0, '999998');
