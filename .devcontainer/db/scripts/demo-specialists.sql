-- ---------------------------------------------------------------------------
-- CARLOS demo/development data -- 60 clearly-fake referral specialists
--
-- Loaded into BOTH the Ontario and BC demo/dev data sets by:
--   * the devcontainer init script (.devcontainer/db/scripts/populate_db.sh)
--   * the deb demo loader (carlos-ctl demo-data)
--
-- Every name carries the FAKE- prefix, phone/fax numbers use the reserved
-- 555 exchange, referral numbers use the synthetic 99001-99060 range, and
-- addresses are placeholders. This list deliberately replaces any use of a
-- real provincial specialist directory in demo/dev environments.
--
-- Idempotency / Flyway-precedence contract:
--   * professionalSpecialists: explicit specId 9001-9060 with INSERT IGNORE
--     -- re-runs are no-ops, and neither province's migrations seed this
--     table, so no Flyway row can collide.
--   * billingreferral rows are derived from the specialist rows and guarded
--     per referral_no (the table has an auto-increment PK, so IGNORE alone
--     would duplicate on re-run).
--   * serviceSpecialists links join on consultationServices.serviceDesc
--     (identical label catalogs in ON and BC) rather than hard-coded
--     serviceIds, and are guarded by WHERE NOT EXISTS (the table has no
--     primary key, so INSERT IGNORE alone could not deduplicate re-runs).
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO professionalSpecialists
  (specId, fName, lName, proLetters, address, phone, fax, website, email,
   specType, eDataUrl, eDataOscarKey, eDataServiceKey, eDataServiceName,
   lastUpdated, annotation, referralNo, privatePhoneNumber, cellPhoneNumber,
   pagerNumber, salutation, institutionId, departmentId, eformId,
   hideFromView, deleted, province)
VALUES
  (9001, 'FAKE-Alice', 'FAKE-Specialist-01', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9001', '555-555-8001', NULL, NULL, 'Cardiology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99001', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9002, 'FAKE-Benjamin', 'FAKE-Specialist-02', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9002', '555-555-8002', NULL, NULL, 'Cardiology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99002', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9003, 'FAKE-Clara', 'FAKE-Specialist-03', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9003', '555-555-8003', NULL, NULL, 'Cardiology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99003', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9004, 'FAKE-Daniel', 'FAKE-Specialist-04', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9004', '555-555-8004', NULL, NULL, 'Cardiology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99004', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9005, 'FAKE-Elena', 'FAKE-Specialist-05', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9005', '555-555-8005', NULL, NULL, 'Cardiology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99005', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9006, 'FAKE-Felix', 'FAKE-Specialist-06', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9006', '555-555-8006', NULL, NULL, 'Dermatology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99006', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9007, 'FAKE-Grace', 'FAKE-Specialist-07', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9007', '555-555-8007', NULL, NULL, 'Dermatology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99007', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9008, 'FAKE-Henry', 'FAKE-Specialist-08', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9008', '555-555-8008', NULL, NULL, 'Dermatology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99008', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9009, 'FAKE-Iris', 'FAKE-Specialist-09', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9009', '555-555-8009', NULL, NULL, 'Dermatology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99009', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9010, 'FAKE-Jonas', 'FAKE-Specialist-10', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9010', '555-555-8010', NULL, NULL, 'Dermatology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99010', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9011, 'FAKE-Karin', 'FAKE-Specialist-11', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9011', '555-555-8011', NULL, NULL, 'Endocrinology and Metabolism', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99011', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9012, 'FAKE-Liam', 'FAKE-Specialist-12', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9012', '555-555-8012', NULL, NULL, 'Endocrinology and Metabolism', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99012', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9013, 'FAKE-Mona', 'FAKE-Specialist-13', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9013', '555-555-8013', NULL, NULL, 'Endocrinology and Metabolism', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99013', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9014, 'FAKE-Noel', 'FAKE-Specialist-14', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9014', '555-555-8014', NULL, NULL, 'Endocrinology and Metabolism', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99014', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9015, 'FAKE-Opal', 'FAKE-Specialist-15', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9015', '555-555-8015', NULL, NULL, 'Endocrinology and Metabolism', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99015', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9016, 'FAKE-Pavel', 'FAKE-Specialist-16', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9016', '555-555-8016', NULL, NULL, 'General Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99016', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9017, 'FAKE-Quinn', 'FAKE-Specialist-17', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9017', '555-555-8017', NULL, NULL, 'General Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99017', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9018, 'FAKE-Rosa', 'FAKE-Specialist-18', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9018', '555-555-8018', NULL, NULL, 'General Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99018', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9019, 'FAKE-Simon', 'FAKE-Specialist-19', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9019', '555-555-8019', NULL, NULL, 'General Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99019', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9020, 'FAKE-Tara', 'FAKE-Specialist-20', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9020', '555-555-8020', NULL, NULL, 'General Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99020', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9021, 'FAKE-Uma', 'FAKE-Specialist-21', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9021', '555-555-8021', NULL, NULL, 'Internal Medicine', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99021', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9022, 'FAKE-Victor', 'FAKE-Specialist-22', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9022', '555-555-8022', NULL, NULL, 'Internal Medicine', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99022', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9023, 'FAKE-Wanda', 'FAKE-Specialist-23', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9023', '555-555-8023', NULL, NULL, 'Internal Medicine', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99023', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9024, 'FAKE-Xavier', 'FAKE-Specialist-24', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9024', '555-555-8024', NULL, NULL, 'Internal Medicine', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99024', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9025, 'FAKE-Yara', 'FAKE-Specialist-25', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9025', '555-555-8025', NULL, NULL, 'Internal Medicine', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99025', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9026, 'FAKE-Zane', 'FAKE-Specialist-26', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9026', '555-555-8026', NULL, NULL, 'Medical Oncology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99026', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9027, 'FAKE-Ada', 'FAKE-Specialist-27', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9027', '555-555-8027', NULL, NULL, 'Medical Oncology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99027', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9028, 'FAKE-Boris', 'FAKE-Specialist-28', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9028', '555-555-8028', NULL, NULL, 'Medical Oncology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99028', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9029, 'FAKE-Celia', 'FAKE-Specialist-29', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9029', '555-555-8029', NULL, NULL, 'Medical Oncology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99029', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9030, 'FAKE-Dmitri', 'FAKE-Specialist-30', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9030', '555-555-8030', NULL, NULL, 'Medical Oncology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99030', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9031, 'FAKE-Edith', 'FAKE-Specialist-31', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9031', '555-555-8031', NULL, NULL, 'Nephrology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99031', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9032, 'FAKE-Frank', 'FAKE-Specialist-32', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9032', '555-555-8032', NULL, NULL, 'Nephrology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99032', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9033, 'FAKE-Gilda', 'FAKE-Specialist-33', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9033', '555-555-8033', NULL, NULL, 'Nephrology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99033', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9034, 'FAKE-Hugo', 'FAKE-Specialist-34', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9034', '555-555-8034', NULL, NULL, 'Nephrology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99034', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9035, 'FAKE-Ines', 'FAKE-Specialist-35', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9035', '555-555-8035', NULL, NULL, 'Nephrology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99035', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9036, 'FAKE-Jack', 'FAKE-Specialist-36', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9036', '555-555-8036', NULL, NULL, 'Neurology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99036', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9037, 'FAKE-Kyla', 'FAKE-Specialist-37', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9037', '555-555-8037', NULL, NULL, 'Neurology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99037', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9038, 'FAKE-Lars', 'FAKE-Specialist-38', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9038', '555-555-8038', NULL, NULL, 'Neurology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99038', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9039, 'FAKE-Mira', 'FAKE-Specialist-39', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9039', '555-555-8039', NULL, NULL, 'Neurology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99039', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9040, 'FAKE-Nils', 'FAKE-Specialist-40', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9040', '555-555-8040', NULL, NULL, 'Neurology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99040', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9041, 'FAKE-Olga', 'FAKE-Specialist-41', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9041', '555-555-8041', NULL, NULL, 'Obstetrics and Gynecology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99041', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9042, 'FAKE-Piotr', 'FAKE-Specialist-42', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9042', '555-555-8042', NULL, NULL, 'Obstetrics and Gynecology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99042', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9043, 'FAKE-Rhea', 'FAKE-Specialist-43', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9043', '555-555-8043', NULL, NULL, 'Obstetrics and Gynecology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99043', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9044, 'FAKE-Sven', 'FAKE-Specialist-44', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9044', '555-555-8044', NULL, NULL, 'Obstetrics and Gynecology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99044', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9045, 'FAKE-Thea', 'FAKE-Specialist-45', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9045', '555-555-8045', NULL, NULL, 'Obstetrics and Gynecology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99045', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9046, 'FAKE-Ulric', 'FAKE-Specialist-46', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9046', '555-555-8046', NULL, NULL, 'Ophthalmology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99046', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9047, 'FAKE-Vera', 'FAKE-Specialist-47', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9047', '555-555-8047', NULL, NULL, 'Ophthalmology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99047', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9048, 'FAKE-Wade', 'FAKE-Specialist-48', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9048', '555-555-8048', NULL, NULL, 'Ophthalmology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99048', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9049, 'FAKE-Xena', 'FAKE-Specialist-49', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9049', '555-555-8049', NULL, NULL, 'Ophthalmology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99049', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9050, 'FAKE-Yusuf', 'FAKE-Specialist-50', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9050', '555-555-8050', NULL, NULL, 'Ophthalmology', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99050', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9051, 'FAKE-Zelda', 'FAKE-Specialist-51', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9051', '555-555-8051', NULL, NULL, 'Orthopedic Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99051', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9052, 'FAKE-Arno', 'FAKE-Specialist-52', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9052', '555-555-8052', NULL, NULL, 'Orthopedic Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99052', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9053, 'FAKE-Bella', 'FAKE-Specialist-53', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9053', '555-555-8053', NULL, NULL, 'Orthopedic Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99053', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9054, 'FAKE-Cyrus', 'FAKE-Specialist-54', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9054', '555-555-8054', NULL, NULL, 'Orthopedic Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99054', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9055, 'FAKE-Dora', 'FAKE-Specialist-55', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9055', '555-555-8055', NULL, NULL, 'Orthopedic Surgery', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99055', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9056, 'FAKE-Emil', 'FAKE-Specialist-56', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9056', '555-555-8056', NULL, NULL, 'Psychiatry', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99056', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9057, 'FAKE-Faye', 'FAKE-Specialist-57', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9057', '555-555-8057', NULL, NULL, 'Psychiatry', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99057', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9058, 'FAKE-Gord', 'FAKE-Specialist-58', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9058', '555-555-8058', NULL, NULL, 'Psychiatry', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99058', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9059, 'FAKE-Hana', 'FAKE-Specialist-59', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9059', '555-555-8059', NULL, NULL, 'Psychiatry', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99059', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL),
  (9060, 'FAKE-Ivan', 'FAKE-Specialist-60', 'MD (FAKE)', '123 Example St, Faketown', '555-555-9060', '555-555-8060', NULL, NULL, 'Psychiatry', NULL, NULL, NULL, NULL, '2026-01-01 00:00:00', NULL, '99060', NULL, NULL, NULL, 'Dr.', 0, 0, NULL, 0, 0, NULL);

-- Referring-practitioner rows for the billing workflow, derived from the
-- specialist rows above so the two directories always agree. Guarded per
-- referral_no because billingreferral's PK is a plain auto-increment.
INSERT INTO billingreferral
  (referral_no, last_name, first_name, specialty, address1, address2, city,
   province, country, postal, phone, fax)
SELECT ps.referralNo, ps.lName, ps.fName, LEFT(ps.specType, 30),
       '123 Example St', NULL, 'Faketown', NULL, 'Canada', 'X0X 0X0',
       ps.phone, ps.fax
FROM professionalSpecialists ps
WHERE ps.specId BETWEEN 9001 AND 9060
  AND NOT EXISTS (
    SELECT 1 FROM billingreferral br WHERE br.referral_no = ps.referralNo
  );

-- Consultation-picker links. Joining on serviceDesc keeps this file
-- province-neutral: a label absent from a province's catalog simply links
-- nothing.
INSERT INTO serviceSpecialists (serviceId, specId)
SELECT cs.serviceId, ps.specId
FROM consultationServices cs
JOIN professionalSpecialists ps
  ON ps.specType = cs.serviceDesc
 AND ps.specId BETWEEN 9001 AND 9060
WHERE NOT EXISTS (
    SELECT 1 FROM serviceSpecialists ss
    WHERE ss.serviceId = cs.serviceId AND ss.specId = ps.specId
  );
