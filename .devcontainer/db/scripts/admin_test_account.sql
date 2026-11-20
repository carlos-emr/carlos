-- CARLOS EMR devcontainer-only login for the Administration lock-test provider.
--
-- admin_test_data.sql seeds provider 999996 ("Account, Lock Test") for BOTH the
-- devcontainer and the deb demo dataset. This file attaches a login to it and
-- is loaded by the devcontainer init script ONLY (populate_db.sh, after
-- admin_test_data.sql). It is deliberately not shipped with the deb: the deb
-- demo load never introduces security rows (scripts/demo-additive-exclude.txt,
-- SEC section), and the hash below is the published carlosdoc development
-- hash that `carlos-ctl bootstrap-admin` exists to replace.
--
-- A sacrificial account lets developers test login failures and the Unlock
-- Account screen without locking carlosdoc. Account locks live in Tomcat's
-- in-memory LoginList, so deliberately enter the wrong password for `locktest`
-- until it appears in Administration > Unlock Account.
--
-- Credentials (development only, never a production default):
--   username locktest / password carlos2026 / PIN 2026
-- Every insert is guarded with WHERE NOT EXISTS so the file is repeatable.

START TRANSACTION;

INSERT INTO security
    (user_name, password, provider_no, pin, b_ExpireSet, forcePasswordReset,
     passwordUpdateDate, pinUpdateDate, lastUpdateUser, lastUpdateDate)
SELECT
    'locktest',
    '{bcrypt}$2a$10$RcoNeqhcLzkfBzAoTQ5C5.nnsOs15iOasQCp0/smjDAuTtkMQ.Uju',
    '999996', '2026', 0, 0, NOW(), NOW(), '999998', NOW()
WHERE EXISTS (SELECT 1 FROM provider WHERE provider_no = '999996')
  AND NOT EXISTS (
      SELECT 1 FROM security WHERE user_name = 'locktest'
  );

INSERT INTO secUserRole
    (provider_no, role_name, orgcd, activeyn, lastUpdateDate)
SELECT '999996', 'receptionist', 'R0000001', 1, NOW()
WHERE EXISTS (SELECT 1 FROM provider WHERE provider_no = '999996')
  AND NOT EXISTS (
      SELECT 1
      FROM secUserRole
      WHERE provider_no = '999996'
        AND role_name = 'receptionist'
        AND activeyn = 1
  );

COMMIT;
