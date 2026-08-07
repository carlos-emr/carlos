-- CARLOS EMR local-development fixtures for the Administration panel.
--
-- development.sql already supplies the large clinical/demo dataset. This file
-- fills the admin-only gaps with obviously synthetic records and is loaded last
-- so it can reference the Rich Text Letter eForm installed by populate_db.sh.
-- Every insert is repeatable to make the file safe to run by hand while working
-- on an existing local database. It must never be used for production data.

START TRANSACTION;

-- User Management -----------------------------------------------------------
-- A sacrificial account lets developers test login failures and the Unlock
-- Account screen without locking carlosdoc. Account locks live in Tomcat's
-- in-memory LoginList, so deliberately enter the wrong password for `locktest`
-- until it appears in Administration > Unlock Account.
INSERT INTO provider
    (provider_no, last_name, first_name, provider_type, specialty, sex, status,
     lastUpdateUser, lastUpdateDate)
SELECT
    '999996', 'Account', 'Lock Test', 'receptionist', 'Local testing', 'X', '1',
    '999998', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM provider WHERE provider_no = '999996'
);

INSERT INTO security
    (user_name, password, provider_no, pin, b_ExpireSet, forcePasswordReset,
     passwordUpdateDate, pinUpdateDate, lastUpdateUser, lastUpdateDate)
SELECT
    'locktest',
    '{bcrypt}$2a$10$RcoNeqhcLzkfBzAoTQ5C5.nnsOs15iOasQCp0/smjDAuTtkMQ.Uju',
    '999996', '2026', 0, 0, NOW(), NOW(), '999998', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM security WHERE user_name = 'locktest'
);

INSERT INTO secUserRole
    (provider_no, role_name, orgcd, activeyn, lastUpdateDate)
SELECT '999996', 'receptionist', 'R0000001', 1, NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM secUserRole
    WHERE provider_no = '999996'
      AND role_name = 'receptionist'
      AND activeyn = 1
);

INSERT INTO providersite (provider_no, site_id)
SELECT '999996', s.site_id
FROM site s
WHERE s.site_id = 1
  AND NOT EXISTS (
      SELECT 1
      FROM providersite ps
      WHERE ps.provider_no = '999996'
        AND ps.site_id = s.site_id
  );

-- Billing -------------------------------------------------------------------
-- These cover active style listing, preview, editing, deletion, and use from
-- Ontario service-code administration.
INSERT INTO cssStyles (name, style, status)
SELECT 'Local Test - Highlight', 'font-weight:bold;background-color:#fff3cd;', 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM cssStyles WHERE name = 'Local Test - Highlight' AND status = 'A'
);

INSERT INTO cssStyles (name, style, status)
SELECT 'Local Test - Muted', 'color:#6c757d;font-style:italic;', 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM cssStyles WHERE name = 'Local Test - Muted' AND status = 'A'
);

INSERT INTO cssStyles (name, style, status)
SELECT 'Local Test - Urgent', 'color:#b02a37;font-weight:bold;text-decoration:underline;', 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM cssStyles WHERE name = 'Local Test - Urgent' AND status = 'A'
);

-- Labs / Inbox --------------------------------------------------------------
-- One current rule exercises rendering, status editing, removal, and the
-- per-message-type child collection on the forwarding-rules screen.
INSERT INTO incomingLabRules (provider_no, status, frwdProvider_no, archive)
SELECT '999998', 'N', '1', '0'
WHERE EXISTS (SELECT 1 FROM provider WHERE provider_no = '999998')
  AND EXISTS (SELECT 1 FROM provider WHERE provider_no = '1')
  AND NOT EXISTS (
      SELECT 1
      FROM incomingLabRules
      WHERE provider_no = '999998'
        AND frwdProvider_no = '1'
        AND archive = '0'
  );

INSERT INTO incomingLabRulesType (forward_rule_id, type)
SELECT r.id, t.type
FROM incomingLabRules r
CROSS JOIN (
    SELECT 'HL7' AS type
    UNION ALL SELECT 'DOC'
    UNION ALL SELECT 'HRM'
) t
WHERE r.provider_no = '999998'
  AND r.frwdProvider_no = '1'
  AND r.archive = '0'
  AND NOT EXISTS (
      SELECT 1
      FROM incomingLabRulesType rt
      WHERE rt.forward_rule_id = r.id
        AND rt.type = t.type
  );

-- Forms / eForms ------------------------------------------------------------
INSERT INTO eform_groups (fid, group_name)
SELECT e.fid, 'Local Admin Tests'
FROM eform e
WHERE e.form_name = 'Rich Text Letter'
  AND e.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM eform_groups eg
      WHERE eg.fid = e.fid
        AND eg.group_name = 'Local Admin Tests'
  )
ORDER BY e.fid
LIMIT 1;

-- Patient-independent eForms are backed by saved eform_data rows rather than
-- the eform library alone. Seed two small, self-contained definitions and a
-- mix of current/deleted instances so both Administration views are useful.
INSERT INTO eform
    (form_name, file_name, subject, form_date, form_time, form_creator, status,
     form_html, showLatestFormOnly, patient_independent, roleType, stable)
SELECT
    'Local Test - Independent Checklist',
    'local-test-independent-checklist.html',
    'Reusable checklist for local administration testing',
    '2026-08-01', '09:00:00', '999998', 1,
    '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Local Test - Independent Checklist</title></head><body><h1>Local Test - Independent Checklist</h1><form method="post" action="" name="LocalIndependentChecklist"><label>Review title <input type="text" name="review_title"></label><br><label><input type="checkbox" name="review_complete" value="1"> Review complete</label><br><label>Notes <textarea name="notes"></textarea></label><br><input type="submit" name="SubmitButton" value="Save"></form></body></html>',
    0, 1, NULL, 1
WHERE EXISTS (SELECT 1 FROM provider WHERE provider_no = '999998')
  AND NOT EXISTS (
      SELECT 1
      FROM eform
      WHERE form_name = 'Local Test - Independent Checklist'
        AND patient_independent = 1
  );

INSERT INTO eform
    (form_name, file_name, subject, form_date, form_time, form_creator, status,
     form_html, showLatestFormOnly, patient_independent, roleType, stable)
SELECT
    'Local Test - Shared Operations Note',
    'local-test-shared-operations-note.html',
    'Shared note for local administration testing',
    '2026-08-01', '09:05:00', '999998', 1,
    '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Local Test - Shared Operations Note</title></head><body><h1>Local Test - Shared Operations Note</h1><form method="post" action="" name="LocalSharedOperationsNote"><label>Topic <input type="text" name="topic"></label><br><label>Details <textarea name="details"></textarea></label><br><input type="submit" name="SubmitButton" value="Save"></form></body></html>',
    0, 1, NULL, 1
WHERE EXISTS (SELECT 1 FROM provider WHERE provider_no = '999998')
  AND NOT EXISTS (
      SELECT 1
      FROM eform
      WHERE form_name = 'Local Test - Shared Operations Note'
        AND patient_independent = 1
  );

-- Current patient-independent examples.
INSERT INTO eform_data
    (fid, form_name, subject, demographic_no, status, form_date, form_time,
     form_provider, form_data, showLatestFormOnly, patient_independent, roleType)
SELECT
    e.fid, e.form_name, 'Local Test - Monthly Safety Review', 0, 1,
    '2026-08-03', '09:30:00', '999998',
    '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Local Test - Independent Checklist</title></head><body><h1>Local Test - Independent Checklist</h1><form method="post" action="" name="LocalIndependentChecklist"><label>Review title <input type="text" name="review_title" value="Monthly safety review"></label><br><label><input type="checkbox" name="review_complete" value="1" checked> Review complete</label><br><label>Notes <textarea name="notes">Synthetic current fixture for local testing.</textarea></label><br><input type="submit" name="SubmitButton" value="Save"></form></body></html>',
    0, 1, NULL
FROM eform e
WHERE e.form_name = 'Local Test - Independent Checklist'
  AND e.status = 1
  AND e.patient_independent = 1
  AND NOT EXISTS (
      SELECT 1
      FROM eform_data ed
      WHERE ed.fid = e.fid
        AND ed.subject = 'Local Test - Monthly Safety Review'
        AND ed.patient_independent = 1
  )
ORDER BY e.fid
LIMIT 1;

INSERT INTO eform_data
    (fid, form_name, subject, demographic_no, status, form_date, form_time,
     form_provider, form_data, showLatestFormOnly, patient_independent, roleType)
SELECT
    e.fid, e.form_name, 'Local Test - Quarterly Operations Review', 0, 1,
    '2026-07-15', '14:15:00', '999998',
    '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Local Test - Shared Operations Note</title></head><body><h1>Local Test - Shared Operations Note</h1><form method="post" action="" name="LocalSharedOperationsNote"><label>Topic <input type="text" name="topic" value="Quarterly operations review"></label><br><label>Details <textarea name="details">Synthetic shared note for local testing.</textarea></label><br><input type="submit" name="SubmitButton" value="Save"></form></body></html>',
    0, 1, NULL
FROM eform e
WHERE e.form_name = 'Local Test - Shared Operations Note'
  AND e.status = 1
  AND e.patient_independent = 1
  AND NOT EXISTS (
      SELECT 1
      FROM eform_data ed
      WHERE ed.fid = e.fid
        AND ed.subject = 'Local Test - Quarterly Operations Review'
        AND ed.patient_independent = 1
  )
ORDER BY e.fid
LIMIT 1;

-- Deleted patient-independent example for the Deleted eForms view.
INSERT INTO eform_data
    (fid, form_name, subject, demographic_no, status, form_date, form_time,
     form_provider, form_data, showLatestFormOnly, patient_independent, roleType)
SELECT
    e.fid, e.form_name, 'Local Test - Archived Safety Draft', 0, 0,
    '2026-06-30', '16:45:00', '999998',
    '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Local Test - Independent Checklist</title></head><body><h1>Local Test - Independent Checklist</h1><form method="post" action="" name="LocalIndependentChecklist"><label>Review title <input type="text" name="review_title" value="Archived safety draft"></label><br><label><input type="checkbox" name="review_complete" value="1"> Review complete</label><br><label>Notes <textarea name="notes">Synthetic deleted fixture for local testing.</textarea></label><br><input type="submit" name="SubmitButton" value="Save"></form></body></html>',
    0, 1, NULL
FROM eform e
WHERE e.form_name = 'Local Test - Independent Checklist'
  AND e.status = 1
  AND e.patient_independent = 1
  AND NOT EXISTS (
      SELECT 1
      FROM eform_data ed
      WHERE ed.fid = e.fid
        AND ed.subject = 'Local Test - Archived Safety Draft'
        AND ed.patient_independent = 1
  )
ORDER BY e.fid
LIMIT 1;

-- Reports -------------------------------------------------------------------
-- Read-only examples are compatible with the Query By Example validator and
-- avoid exposing patient information in a developer's screenshots.
INSERT INTO reportByExamplesFavorite (providerNo, query, name)
SELECT
    '999998',
    'SELECT provider_no, last_name, first_name, status FROM provider ORDER BY last_name, first_name LIMIT 100;',
    'Local Test - Provider Directory'
WHERE NOT EXISTS (
    SELECT 1
    FROM reportByExamplesFavorite
    WHERE providerNo = '999998'
      AND name = 'Local Test - Provider Directory'
);

INSERT INTO reportByExamplesFavorite (providerNo, query, name)
SELECT
    '999998',
    'SELECT name, duration, location FROM appointmentType ORDER BY name;',
    'Local Test - Appointment Types'
WHERE NOT EXISTS (
    SELECT 1
    FROM reportByExamplesFavorite
    WHERE providerNo = '999998'
      AND name = 'Local Test - Appointment Types'
);

INSERT INTO demographicQueryFavourites
    (selects, age, startYear, endYear, firstName, lastName, rosterStatus,
     sex, providerNo, patientStatus, queryName, archived, demoIds)
SELECT
    '<root><item value="demographic_no"/><item value="last_name"/><item value="first_name"/><item value="provider_name"/><item value="patient_status"/></root>',
    '0', '', '', '', '', '<root/>', '0', '<root/>', '<root/>',
    'Local Test - Basic Demographics', '1', ''
WHERE NOT EXISTS (
    SELECT 1
    FROM demographicQueryFavourites
    WHERE queryName = 'Local Test - Basic Demographics'
      AND archived = '1'
);

-- Schedule Management -------------------------------------------------------
INSERT INTO appointmentType
    (name, notes, reason, location, resources, duration)
SELECT
    'Local Test - Standard Visit', 'Routine in-person test appointment',
    'Routine follow-up', 'Exam Room 1', 'ROOM1', 15
WHERE NOT EXISTS (
    SELECT 1 FROM appointmentType WHERE name = 'Local Test - Standard Visit'
);

INSERT INTO appointmentType
    (name, notes, reason, location, resources, duration)
SELECT
    'Local Test - Extended Visit', 'Longer in-person test appointment',
    'Complex follow-up', 'Exam Room 2', 'ROOM2', 30
WHERE NOT EXISTS (
    SELECT 1 FROM appointmentType WHERE name = 'Local Test - Extended Visit'
);

INSERT INTO appointmentType
    (name, notes, reason, location, resources, duration)
SELECT
    'Local Test - Virtual Visit', 'Video appointment test case',
    'Virtual follow-up', 'Virtual', 'VIDEO', 20
WHERE NOT EXISTS (
    SELECT 1 FROM appointmentType WHERE name = 'Local Test - Virtual Visit'
);

-- System Management ---------------------------------------------------------
-- Include active lots plus a soft-deleted lot so add/reactivate and search/
-- delete paths can both be exercised.
INSERT INTO PreventionsLotNrs
    (creationDate, providerNo, preventionType, lotNr, deleted, lastUpdateDate)
SELECT NOW(), '999998', 'Flu', 'LOCAL-FLU-2026-A', FALSE, NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM PreventionsLotNrs
    WHERE preventionType = 'Flu'
      AND lotNr = 'LOCAL-FLU-2026-A'
);

INSERT INTO PreventionsLotNrs
    (creationDate, providerNo, preventionType, lotNr, deleted, lastUpdateDate)
SELECT NOW(), '999998', 'COVID-19', 'LOCAL-COVID-2026-B', FALSE, NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM PreventionsLotNrs
    WHERE preventionType = 'COVID-19'
      AND lotNr = 'LOCAL-COVID-2026-B'
);

INSERT INTO PreventionsLotNrs
    (creationDate, providerNo, preventionType, lotNr, deleted, lastUpdateDate)
SELECT NOW(), '999998', 'DTaP', 'LOCAL-DTAP-ARCHIVED', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM PreventionsLotNrs
    WHERE preventionType = 'DTaP'
      AND lotNr = 'LOCAL-DTAP-ARCHIVED'
);

COMMIT;
