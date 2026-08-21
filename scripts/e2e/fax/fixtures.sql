-- Minimal fixtures for the fax outbound E2E tests. Contains NO credentials.
-- Idempotent-ish: safe to run on a fresh test database.

-- A patient to attach clinical documents (consult/eForm/Rx/letter) to.
INSERT INTO demographic (last_name, first_name, sex, provider_no, roster_status,
       patient_status, hin, year_of_birth, month_of_birth, date_of_birth, date_joined)
SELECT 'Loopback','Faxtest','M','999998','RO','AC','','1980','01','01', NOW()
WHERE NOT EXISTS (SELECT 1 FROM demographic WHERE last_name='Loopback' AND first_name='Faxtest');

-- A simple eForm template to instantiate and fax.
INSERT INTO eform (form_name, subject, file_name, form_html, showLatestFormOnly,
       patientIndependent, roleType, status)
SELECT 'Loopback Fax Test Form','E2E fax test','loopback.html',
       '<html><body><h2>CARLOS Fax E2E Test</h2><p>Patient: [demographic_name]</p>'
       '<p>DOB: [demographic_dob]</p><p>Loopback fax test document.</p></body></html>',
       0,0,'doctor',1
WHERE NOT EXISTS (SELECT 1 FROM eform WHERE form_name='Loopback Fax Test Form');
