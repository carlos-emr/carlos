-- V1.0.3 — performance indexes for the hottest CARLOS query paths (common schema).
--
-- Every index below is justified by an actual DAO predicate (WHERE/JOIN/ORDER BY mined from the
-- code; full justification table on PR #3150). Shipped as a forward migration — NOT folded into
-- the V1 baseline — so that BOTH populations receive it: fresh installs (V1 executes, then this
-- applies) and OpenO/oscar19 conversions (V1 is baseline-STAMPED without executing, then this
-- applies). Converted datadirs are heterogeneous (some already carry a subset of these indexes
-- from the legacy updates/ patches), hence every statement is idempotent.
-- note, this create index if not exists only works in mariadb

-- ---- Guard composites BEFORE the shadow drops. Each drop below is justified by a composite that
-- ---- exists in the V1 baseline — but a CONVERTED (baseline-stamped) datadir may predate some of
-- ---- them (all_index in particular was never shipped by any legacy update). Creating them here
-- ---- first is a no-op on fresh installs and guarantees no drop ever leaves a column unindexed.
CREATE INDEX IF NOT EXISTS `appointment_ikey` ON `appointment` (`demographic_no`,`updatedatetime`);
CREATE INDEX IF NOT EXISTS `casemgmt_note_ikey` ON `casemgmt_note` (`demographic_no`,`update_date`,`locked`);
CREATE INDEX IF NOT EXISTS `measurement_integrator` ON `measurements` (`demographicNo`,`dateEntered`);
CREATE INDEX IF NOT EXISTS `idx_tickler_status_service_date` ON `tickler` (`status`,`service_date`);
CREATE INDEX IF NOT EXISTS `all_index` ON `patientLabRouting` (`lab_type`,`lab_no`,`demographic_no`);
CREATE INDEX IF NOT EXISTS `scheduledate_key1` ON `scheduledate` (`sdate`,`provider_no`,`hour`,`status`);
CREATE INDEX IF NOT EXISTS `demoMap_messageID_demographic_no` ON `msgDemoMap` (`messageID`,`demographic_no`);

-- ---- Drop redundant indexes (exact/PK duplicates and left-prefix shadows whose reads are fully
-- ---- covered by a guarded-above or newly-added composite; InnoDB secondary indexes carry the
-- ---- PK implicitly, so these drops are read-equivalent and save pure write amplification).
ALTER TABLE `appointment` DROP INDEX IF EXISTS `demographic_no`;              -- prefix of appointment_ikey (guarded above)
ALTER TABLE `casemgmt_note` DROP INDEX IF EXISTS `demographic_no`;            -- prefix of casemgmt_note_ikey (guarded above)
ALTER TABLE `measurements` DROP INDEX IF EXISTS `demographicNo`;              -- prefix of measurement_integrator (guarded above)
ALTER TABLE `tickler` DROP INDEX IF EXISTS `statusIndex`;                     -- prefix of idx_tickler_status_service_date (guarded above)
ALTER TABLE `patientLabRouting` DROP INDEX IF EXISTS `lab_type_index`;        -- prefix of all_index (guarded above)
ALTER TABLE `scheduledate` DROP INDEX IF EXISTS `scheduledate_sdate`;         -- prefix of scheduledate_key1 (guarded above)
ALTER TABLE `drugs` DROP INDEX IF EXISTS `drugs_demographic_no`;              -- prefix of idx_drugs_demographic_no_archived (added below)
ALTER TABLE `log` DROP INDEX IF EXISTS `provider_noIndex`;                    -- prefix of idx_log_provider_no_dateTime (added below)
ALTER TABLE `eform_data` DROP INDEX IF EXISTS `idx_eform_data_demographic_no`; -- prefix of idx_eform_data_demographic_status (added below)
ALTER TABLE `eform_data` DROP INDEX IF EXISTS `id`;                           -- UNIQUE duplicate of the PK (fdid)
ALTER TABLE `providerLabRouting` DROP INDEX IF EXISTS `provider_lab_status_index`; -- legacy provider_no(3) prefix; replaced below
ALTER TABLE `msgDemoMap` DROP INDEX IF EXISTS `messageID`;                    -- exact duplicate of demoMap_messageID_demographic_no (guarded above)
ALTER TABLE `measurementType` DROP INDEX IF EXISTS `id`;                      -- duplicate of the PK

-- ---- Schedule screen: the provider day view had no provider_no-leading index.
CREATE INDEX IF NOT EXISTS `idx_appointment_provider_date_time` ON `appointment` (`provider_no`,`appointment_date`,`start_time`);
CREATE INDEX IF NOT EXISTS `idx_appointmentArchive_demographic_no` ON `appointmentArchive` (`demographic_no`);

-- ---- Consultations (patient panel + team inbox + attachments/responses).
CREATE INDEX IF NOT EXISTS `idx_consultationRequests_demographicNo` ON `consultationRequests` (`demographicNo`);
CREATE INDEX IF NOT EXISTS `idx_consultationRequests_sendTo_status` ON `consultationRequests` (`sendTo`,`status`);
CREATE INDEX IF NOT EXISTS `idx_consultdocs_requestId_doctype` ON `consultdocs` (`requestId`,`doctype`);
CREATE INDEX IF NOT EXISTS `idx_consultationResponse_demographicNo` ON `consultationResponse` (`demographicNo`);
CREATE INDEX IF NOT EXISTS `idx_professionalSpecialists_lName_fName` ON `professionalSpecialists` (`lName`,`fName`);

-- ---- Encounter panels: episodes/pregnancy, allergies, CPP, eForms.
CREATE INDEX IF NOT EXISTS `idx_Episode_demographicNo_status` ON `Episode` (`demographicNo`,`status`);
CREATE INDEX IF NOT EXISTS `idx_allergies_demographic_no_archived` ON `allergies` (`demographic_no`,`archived`);
CREATE INDEX IF NOT EXISTS `idx_casemgmt_cpp_demographic_no` ON `casemgmt_cpp` (`demographic_no`);
CREATE INDEX IF NOT EXISTS `idx_eform_data_demographic_status` ON `eform_data` (`demographic_no`,`status`);

-- ---- Encounter notes: e-chart note list orders by observation_date.
CREATE INDEX IF NOT EXISTS `idx_casemgmt_note_demo_observation` ON `casemgmt_note` (`demographic_no`,`observation_date`);

-- ---- Prescriptions: Rx list, favorites, interaction checks.
CREATE INDEX IF NOT EXISTS `idx_prescription_demographic_no` ON `prescription` (`demographic_no`);
CREATE INDEX IF NOT EXISTS `idx_drugs_demographic_no_archived` ON `drugs` (`demographic_no`,`archived`);
CREATE INDEX IF NOT EXISTS `idx_drugs_regional_identifier` ON `drugs` (`regional_identifier`);
CREATE INDEX IF NOT EXISTS `idx_drugs_ATC` ON `drugs` (`ATC`);
CREATE INDEX IF NOT EXISTS `idx_favorites_provider_no` ON `favorites` (`provider_no`);

-- ---- Measurements: typed vitals / flowsheet ordering.
CREATE INDEX IF NOT EXISTS `idx_measurements_demo_type_observed` ON `measurements` (`demographicNo`,`type`,`dateObserved`);

-- ---- Messaging.
CREATE INDEX IF NOT EXISTS `idx_msgDemoMap_demographic_no` ON `msgDemoMap` (`demographic_no`);
CREATE INDEX IF NOT EXISTS `idx_messagetbl_sentbyNo_sentByLocation` ON `messagetbl` (`sentbyNo`,`sentByLocation`);

-- ---- Labs: provider inbox routing + lab version matching.
CREATE INDEX IF NOT EXISTS `idx_providerLabRouting_prov_status_type` ON `providerLabRouting` (`provider_no`,`status`,`lab_type`);
CREATE INDEX IF NOT EXISTS `idx_hl7TextInfo_filler_order_num` ON `hl7TextInfo` (`filler_order_num`);

-- ---- Demographic roster + audit-log recent-patients widgets.
CREATE INDEX IF NOT EXISTS `idx_demographic_provider_no` ON `demographic` (`provider_no`);
CREATE INDEX IF NOT EXISTS `idx_log_provider_no_dateTime` ON `log` (`provider_no`,`dateTime`);
