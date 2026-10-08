-- Patient portal booking prompt security object (issue #3849).
--
--   _portal.booking_prompt  -> portal.booking_prompt.manage
--
-- Staff ask a patient through the portal to book a follow-up, see whether it was read, and withdraw
-- it. Listing needs read; creating and withdrawing need write.
INSERT IGNORE INTO secObjectName (objectName, description, orgapplicable) VALUES
    ('_portal.booking_prompt', 'Ask patients through the portal to book a follow-up', '0');

-- Meant for front-desk staff and providers (#3849): admin, receptionist, and the doctor and nursing
-- roles the baseline data ships in every province. The key is (roleUserGroup, objectName), so a role
-- that already has a _portal.booking_prompt row keeps the clinic's own setting.
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES
    ('admin', '_portal.booking_prompt', 'x', 0, '999998'),
    ('receptionist', '_portal.booking_prompt', 'x', 0, '999998'),
    ('doctor', '_portal.booking_prompt', 'x', 0, '999998'),
    ('locum', '_portal.booking_prompt', 'x', 0, '999998'),
    ('psychiatrist', '_portal.booking_prompt', 'x', 0, '999998'),
    ('nurse', '_portal.booking_prompt', 'x', 0, '999998'),
    ('Nurse Manager', '_portal.booking_prompt', 'x', 0, '999998'),
    ('RN', '_portal.booking_prompt', 'x', 0, '999998'),
    ('RPN', '_portal.booking_prompt', 'x', 0, '999998');
