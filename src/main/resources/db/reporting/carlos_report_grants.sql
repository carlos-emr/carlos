-- Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
--
-- Query by Example reporting account: table classification and grant sync (issue #3372).
--
-- Query by Example runs every submitted SELECT as a dedicated read-only database account
-- (`carlos_report` unless @carlos_report_user names another), never as the application
-- account. This file is the one place that decides what that account may read. It lists
-- every CARLOS table as `reportable` or `restricted`, then rebuilds the account's privileges
-- from that list. See docs/query-by-example-least-privilege.md.
--
-- HOW TO RUN IT
--   As a MariaDB administrator, with the CARLOS schema as the default database, after the
--   account exists:
--
--     mariadb --protocol=socket --user=root carlos < carlos_report_grants.sql
--
--   On a packaged install `carlos-ctl db-users` and `carlos-ctl db-migrate` run the copy
--   shipped in the deployed webapp (WEB-INF/classes/db/reporting/). The devcontainer runs it
--   when it builds its database. Running it again is safe and is how a change to this list
--   reaches the database: every run revokes everything the account holds, then grants the
--   current list.
--
-- THE RULES
--   * Only a `reportable` table that EXISTS in the selected schema is granted, and only
--     SELECT. A table that a later migration adds stays unreadable until it is listed here;
--     QueryByExampleReportingClassificationUnitTest fails the build until it is.
--   * A `restricted` table holds credentials, tokens, keys, live session identifiers or
--     integration configuration. It is never granted, and
--     QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES must name the same tables, so the
--     application refuses them too.
--   * `excluded_columns` hides one or more secret columns of an otherwise reportable table:
--     every other column is granted. The database then refuses `SELECT *` on that table, so
--     a report names the columns it needs.
--   * The account may hold at most 6 connections, and each statement may run for at most
--     30 seconds. The application stops a query after 15 seconds; this limit is the fallback
--     if that cancel fails.
--   * Nothing else is granted: no writes, no DDL, no FILE, no PROCESS, no routines and no
--     other schema. Roles and PROXY grants held by the account are revoked.
--   * The run refuses to grant anything while PUBLIC holds privileges reaching the schema.
--     It revokes the account's own privileges first, so a refused run leaves it with none.
--
-- ROW FORMAT (tests and tooling parse it, so keep one row per line)
--   ('<table>', 'reportable', NULL, NULL),
--   ('<table>', 'reportable', '<column>[,<column>]', '<why the columns are hidden>'),
--   ('<table>', 'restricted', NULL, '<why the table is never reportable>'),
--   Names are written exactly as the schema spells them. Reasons are plain text with no quotes.

SET SESSION sql_log_bin = 0;

DROP TEMPORARY TABLE IF EXISTS carlos_report_tables;
CREATE TEMPORARY TABLE carlos_report_tables (
    table_name VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    access ENUM('reportable', 'restricted') NOT NULL,
    excluded_columns VARCHAR(512) NULL,
    reason VARCHAR(255) NULL
) ENGINE = MEMORY;

INSERT INTO carlos_report_tables (table_name, access, excluded_columns, reason) VALUES
('access_type', 'reportable', NULL, NULL),
('admission', 'reportable', NULL, NULL),
('agency', 'reportable', NULL, NULL),
('allergies', 'reportable', NULL, NULL),
('app_lookuptable', 'reportable', NULL, NULL),
('app_lookuptable_fields', 'reportable', NULL, NULL),
('AppDefinition', 'restricted', NULL, 'External application definitions; config holds integration consumer keys and secrets'),
('appointment', 'reportable', NULL, NULL),
('appointment_status', 'reportable', NULL, NULL),
('appointmentArchive', 'reportable', NULL, NULL),
('appointmentType', 'reportable', NULL, NULL),
('AppUser', 'restricted', NULL, 'Per-provider authentication data for external applications'),
('batch_billing', 'reportable', NULL, NULL),
('batchEligibility', 'reportable', NULL, NULL),
('bcpEligibleCodes', 'reportable', NULL, NULL),
('bed', 'reportable', NULL, NULL),
('bed_type', 'reportable', NULL, NULL),
('bill_recipients', 'reportable', NULL, NULL),
('billactivity', 'reportable', NULL, NULL),
('billcenter', 'reportable', NULL, NULL),
('billing', 'reportable', NULL, NULL),
('billing_cdm_service_codes', 'reportable', NULL, NULL),
('billing_history', 'reportable', NULL, NULL),
('billing_msp_servicecode_times', 'reportable', NULL, NULL),
('billing_on_3rdPartyAddress', 'reportable', NULL, NULL),
('billing_on_cheader1', 'reportable', NULL, NULL),
('billing_on_cheader2', 'reportable', NULL, NULL),
('billing_on_diskname', 'reportable', NULL, NULL),
('billing_on_eareport', 'reportable', NULL, NULL),
('billing_on_errorCode', 'reportable', NULL, NULL),
('billing_on_ext', 'reportable', NULL, NULL),
('billing_on_favourite', 'reportable', NULL, NULL),
('billing_on_filename', 'reportable', NULL, NULL),
('billing_on_header', 'reportable', NULL, NULL),
('billing_on_item', 'reportable', NULL, NULL),
('billing_on_item_payment', 'reportable', NULL, NULL),
('billing_on_payment', 'reportable', NULL, NULL),
('billing_on_premium', 'reportable', NULL, NULL),
('billing_on_proc', 'reportable', NULL, NULL),
('billing_on_repo', 'reportable', NULL, NULL),
('billing_on_transaction', 'reportable', NULL, NULL),
('billing_payment_type', 'reportable', NULL, NULL),
('billing_preferences', 'reportable', NULL, NULL),
('billing_private_transactions', 'reportable', NULL, NULL),
('billing_service_code_conditions', 'reportable', NULL, NULL),
('billing_trayfees', 'reportable', NULL, NULL),
('billingdetail', 'reportable', NULL, NULL),
('billinginr', 'reportable', NULL, NULL),
('billinglocation', 'reportable', NULL, NULL),
('billingmaster', 'reportable', NULL, NULL),
('billingnote', 'reportable', NULL, NULL),
('billingperclimit', 'reportable', NULL, NULL),
('billingreferral', 'reportable', NULL, NULL),
('billingservice', 'reportable', NULL, NULL),
('billingstatus_types', 'reportable', NULL, NULL),
('billingtypes', 'reportable', NULL, NULL),
('billingvisit', 'reportable', NULL, NULL),
('caisi_form', 'reportable', NULL, NULL),
('caisi_form_data', 'reportable', NULL, NULL),
('caisi_form_data_tmpsave', 'reportable', NULL, NULL),
('caisi_form_instance', 'reportable', NULL, NULL),
('caisi_role', 'reportable', NULL, NULL),
('casemgmt_cpp', 'reportable', NULL, NULL),
('casemgmt_issue', 'reportable', NULL, NULL),
('casemgmt_issue_notes', 'reportable', NULL, NULL),
('casemgmt_note', 'reportable', 'password', 'Legacy note-lock password column, plaintext in imported OSCAR data'),
('casemgmt_note_ext', 'reportable', NULL, NULL),
('casemgmt_note_link', 'reportable', NULL, NULL),
('casemgmt_note_lock', 'restricted', NULL, 'Live HTTP session identifiers of clinicians editing notes'),
('casemgmt_tmpsave', 'reportable', NULL, NULL),
('CdsClientForm', 'reportable', NULL, NULL),
('CdsClientFormData', 'reportable', NULL, NULL),
('CdsFormOption', 'reportable', NULL, NULL),
('CdsHospitalisationDays', 'reportable', NULL, NULL),
('client_image', 'reportable', NULL, NULL),
('client_referral', 'reportable', NULL, NULL),
('ClientLink', 'reportable', NULL, NULL),
('clinic', 'reportable', NULL, NULL),
('clinic_location', 'reportable', NULL, NULL),
('clinic_nbr', 'reportable', NULL, NULL),
('config_Immunization', 'reportable', NULL, NULL),
('Consent', 'reportable', NULL, NULL),
('Consent_migration_audit', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('emailFooterLogo', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('outboundEmailArchive', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('outboundEmailArchiveAttachment', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('outboundEmailArchiveDeletion', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('outboundEmailArchiveLegalHoldEvent', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('patient_portal_invite_delivery', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('portal_booking_offer', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('sms_config', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('sms_provider_rate_limit', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('sms_transaction', 'restricted', NULL, 'Private messaging, consent or integration state; managed through application workflows'),
('consentType', 'reportable', NULL, NULL),
('consultationRequestExt', 'reportable', NULL, NULL),
('consultationRequestExtArchive', 'reportable', NULL, NULL),
('consultationRequests', 'reportable', NULL, NULL),
('consultationRequestsArchive', 'reportable', NULL, NULL),
('consultationResponse', 'reportable', NULL, NULL),
('consultationServices', 'reportable', NULL, NULL),
('consultdocs', 'reportable', NULL, NULL),
('consultResponseDoc', 'reportable', NULL, NULL),
('Contact', 'reportable', NULL, NULL),
('ContactSpecialty', 'reportable', NULL, NULL),
('country_codes', 'reportable', NULL, NULL),
('criteria', 'reportable', NULL, NULL),
('criteria_selection_option', 'reportable', NULL, NULL),
('criteria_type', 'reportable', NULL, NULL),
('criteria_type_option', 'reportable', NULL, NULL),
('cssStyles', 'reportable', NULL, NULL),
('ctl_billingservice', 'reportable', NULL, NULL),
('ctl_billingservice_age_rules', 'reportable', NULL, NULL),
('ctl_billingservice_premium', 'reportable', NULL, NULL),
('ctl_billingservice_sex_rules', 'reportable', NULL, NULL),
('ctl_billingtype', 'reportable', NULL, NULL),
('ctl_diagcode', 'reportable', NULL, NULL),
('ctl_doc_class', 'reportable', NULL, NULL),
('ctl_doctype', 'reportable', NULL, NULL),
('ctl_document', 'reportable', NULL, NULL),
('ctl_frequency', 'reportable', NULL, NULL),
('ctl_servicecodes_dxcodes', 'reportable', NULL, NULL),
('ctl_specialinstructions', 'reportable', NULL, NULL),
('CtlRelationships', 'reportable', NULL, NULL),
('custom_filter', 'reportable', NULL, NULL),
('custom_filter_assignees', 'reportable', NULL, NULL),
('custom_filter_providers', 'reportable', NULL, NULL),
('CVCImmunization', 'reportable', NULL, NULL),
('CVCMapping', 'reportable', NULL, NULL),
('CVCMedication', 'reportable', NULL, NULL),
('CVCMedicationGTIN', 'reportable', NULL, NULL),
('CVCMedicationLotNumber', 'reportable', NULL, NULL),
('dashboard', 'reportable', NULL, NULL),
('dataExport', 'reportable', NULL, NULL),
('default_issue', 'reportable', NULL, NULL),
('default_role_access', 'reportable', NULL, NULL),
('demographic', 'reportable', NULL, NULL),
('demographic_merged', 'reportable', NULL, NULL),
('demographicaccessory', 'reportable', NULL, NULL),
('demographicArchive', 'reportable', NULL, NULL),
('DemographicContact', 'reportable', NULL, NULL),
('demographiccust', 'reportable', NULL, NULL),
('demographiccustArchive', 'reportable', NULL, NULL),
('demographicExt', 'reportable', NULL, NULL),
('demographicExtArchive', 'reportable', NULL, NULL),
('demographicPharmacy', 'reportable', NULL, NULL),
('demographicQueryFavourites', 'reportable', NULL, NULL),
('demographicSets', 'reportable', NULL, NULL),
('demographicstudy', 'reportable', NULL, NULL),
('Department', 'reportable', NULL, NULL),
('desannualreviewplan', 'reportable', NULL, NULL),
('desaprisk', 'reportable', NULL, NULL),
('DHIRSubmissionLog', 'reportable', NULL, NULL),
('diagnosticcode', 'reportable', NULL, NULL),
('DigitalSignature', 'reportable', 'signatureImage', 'Handwritten signature images used to sign prescriptions and forms'),
('diseases', 'reportable', NULL, NULL),
('document', 'reportable', NULL, NULL),
('document_review', 'reportable', NULL, NULL),
('document_storage', 'reportable', NULL, NULL),
('documentDescriptionTemplate', 'reportable', NULL, NULL),
('DocumentExtraReviewer', 'reportable', NULL, NULL),
('DrugProduct', 'reportable', NULL, NULL),
('drugReason', 'reportable', NULL, NULL),
('drugs', 'reportable', NULL, NULL),
('dsGuidelineProviderMap', 'reportable', NULL, NULL),
('dsGuidelines', 'reportable', NULL, NULL),
('dx_associations', 'reportable', NULL, NULL),
('dxCodeTranslations', 'reportable', NULL, NULL),
('dxphcpgroup', 'reportable', NULL, NULL),
('dxresearch', 'reportable', NULL, NULL),
('eChart', 'reportable', NULL, NULL),
('eform', 'reportable', NULL, NULL),
('eform_data', 'reportable', NULL, NULL),
('eform_groups', 'reportable', NULL, NULL),
('eform_values', 'reportable', NULL, NULL),
('EFormDocs', 'reportable', NULL, NULL),
('EFormReportTool', 'reportable', NULL, NULL),
('emailAttachment', 'reportable', NULL, NULL),
('emailConfig', 'restricted', NULL, 'Outbound email account configuration, including provider credentials'),
('emailLog', 'restricted', NULL, 'Sent-email log, including passwords and password hints for encrypted messages'),
('encounter', 'reportable', NULL, NULL),
('encounterForm', 'reportable', NULL, NULL),
('encountertemplate', 'reportable', NULL, NULL),
('encounterWindow', 'reportable', NULL, NULL),
('Episode', 'reportable', NULL, NULL),
('erefer_attachment', 'reportable', NULL, NULL),
('erefer_attachment_data', 'reportable', NULL, NULL),
('Facility', 'reportable', NULL, NULL),
('facility_message', 'reportable', NULL, NULL),
('favorites', 'reportable', NULL, NULL),
('favoritesprivilege', 'reportable', NULL, NULL),
('fax_config', 'restricted', NULL, 'Fax gateway accounts and passwords'),
('FaxClientLog', 'reportable', NULL, NULL),
('faxes', 'reportable', NULL, NULL),
('fileUploadCheck', 'reportable', NULL, NULL),
('Flowsheet', 'reportable', NULL, NULL),
('flowsheet_customization', 'reportable', NULL, NULL),
('flowsheet_drug', 'reportable', NULL, NULL),
('flowsheet_dx', 'reportable', NULL, NULL),
('FlowSheetUserCreated', 'reportable', NULL, NULL),
('form', 'reportable', NULL, NULL),
('form2MinWalk', 'reportable', NULL, NULL),
('form_boolean_value', 'reportable', NULL, NULL),
('form_hsfo2_visit', 'reportable', NULL, NULL),
('formAdfV2', 'reportable', NULL, NULL),
('formAlpha', 'reportable', NULL, NULL),
('formAnnual', 'reportable', NULL, NULL),
('formAnnualV2', 'reportable', NULL, NULL),
('formBCAR', 'reportable', NULL, NULL),
('formBCAR2007', 'reportable', NULL, NULL),
('formBCAR2012', 'reportable', NULL, NULL),
('formBCAR2020', 'reportable', NULL, NULL),
('formBCAR2020Data', 'reportable', NULL, NULL),
('formBCAR2020Text', 'reportable', NULL, NULL),
('formBCBirthSumMo', 'reportable', NULL, NULL),
('formBCBirthSumMo2008', 'reportable', NULL, NULL),
('formBCClientChartChecklist', 'reportable', NULL, NULL),
('formBCHP', 'reportable', NULL, NULL),
('formBCINR', 'reportable', NULL, NULL),
('formBCNewBorn', 'reportable', NULL, NULL),
('formBCNewBorn2008', 'reportable', NULL, NULL),
('formBPMH', 'reportable', NULL, NULL),
('formCaregiver', 'reportable', NULL, NULL),
('formCESD', 'reportable', NULL, NULL),
('formchf', 'reportable', NULL, NULL),
('formConsult', 'reportable', NULL, NULL),
('formCostQuestionnaire', 'reportable', NULL, NULL),
('formCounseling', 'reportable', NULL, NULL),
('formDischargeSummary', 'reportable', NULL, NULL),
('formECARES', 'reportable', NULL, NULL),
('formFalls', 'reportable', NULL, NULL),
('formGripStrength', 'reportable', NULL, NULL),
('formGrowth0_36', 'reportable', NULL, NULL),
('formGrowthChart', 'reportable', NULL, NULL),
('formHomeFalls', 'reportable', NULL, NULL),
('formImmunAllergy', 'reportable', NULL, NULL),
('formIntakeInfo', 'reportable', NULL, NULL),
('formInternetAccess', 'reportable', NULL, NULL),
('formLabReq', 'reportable', NULL, NULL),
('formLabReq07', 'reportable', NULL, NULL),
('formLabReq10', 'reportable', NULL, NULL),
('formLateLifeFDIDisability', 'reportable', NULL, NULL),
('formLateLifeFDIFunction', 'reportable', NULL, NULL),
('formMentalHealth', 'reportable', NULL, NULL),
('formMentalHealthForm1', 'reportable', NULL, NULL),
('formMentalHealthForm14', 'reportable', NULL, NULL),
('formMentalHealthForm42', 'reportable', NULL, NULL),
('formMMSE', 'reportable', NULL, NULL),
('formNoShowPolicy', 'reportable', NULL, NULL),
('formPalliativeCare', 'reportable', NULL, NULL),
('formPeriMenopausal', 'reportable', NULL, NULL),
('formPositionHazard', 'reportable', NULL, NULL),
('formreceptionassessment', 'reportable', NULL, NULL),
('formRhImmuneGlobulin', 'reportable', NULL, NULL),
('formRourke', 'reportable', NULL, NULL),
('formRourke2006', 'reportable', NULL, NULL),
('formRourke2009', 'reportable', NULL, NULL),
('formRourke2017', 'reportable', NULL, NULL),
('formRourke2020', 'reportable', NULL, NULL),
('formSatisfactionScale', 'reportable', NULL, NULL),
('formSelfAdministered', 'reportable', NULL, NULL),
('formSelfAssessment', 'reportable', NULL, NULL),
('formSelfEfficacy', 'reportable', NULL, NULL),
('formSelfManagement', 'reportable', NULL, NULL),
('formSF36', 'reportable', NULL, NULL),
('formSF36Caregiver', 'reportable', NULL, NULL),
('formTreatmentPref', 'reportable', NULL, NULL),
('formVTForm', 'reportable', NULL, NULL),
('formWCB', 'reportable', NULL, NULL),
('frm_labreq_preset', 'reportable', NULL, NULL),
('functional_user_type', 'reportable', NULL, NULL),
('FunctionalCentre', 'reportable', NULL, NULL),
('groupMembers_tbl', 'reportable', NULL, NULL),
('GroupNoteLink', 'reportable', NULL, NULL),
('groups_tbl', 'reportable', NULL, NULL),
('gstControl', 'reportable', NULL, NULL),
('hash_audit', 'reportable', NULL, NULL),
('health_safety', 'reportable', NULL, NULL),
('hl7_link', 'reportable', NULL, NULL),
('hl7_message', 'reportable', NULL, NULL),
('hl7_msh', 'reportable', NULL, NULL),
('hl7_obr', 'reportable', NULL, NULL),
('hl7_obx', 'reportable', NULL, NULL),
('hl7_orc', 'reportable', NULL, NULL),
('hl7_pid', 'reportable', NULL, NULL),
('HL7HandlerMSHMapping', 'reportable', NULL, NULL),
('hl7TextInfo', 'reportable', NULL, NULL),
('hl7TextMessage', 'reportable', NULL, NULL),
('HnrDataValidation', 'reportable', NULL, NULL),
('HRMCategory', 'reportable', NULL, NULL),
('HRMDocument', 'reportable', NULL, NULL),
('HRMDocumentComment', 'reportable', NULL, NULL),
('HRMDocumentSubClass', 'reportable', NULL, NULL),
('HRMDocumentToDemographic', 'reportable', NULL, NULL),
('HRMDocumentToProvider', 'reportable', NULL, NULL),
('HRMProviderConfidentialityStatement', 'reportable', NULL, NULL),
('HRMSubClass', 'reportable', NULL, NULL),
('hsfo2_patient', 'reportable', NULL, NULL),
('hsfo2_system', 'reportable', NULL, NULL),
('hsfo_recommit_schedule', 'reportable', NULL, NULL),
('icd10', 'reportable', NULL, NULL),
('icd9', 'reportable', NULL, NULL),
('Icd9Synonym', 'reportable', NULL, NULL),
('ichppccode', 'reportable', NULL, NULL),
('immunizations', 'reportable', NULL, NULL),
('incomingLabRules', 'reportable', NULL, NULL),
('incomingLabRulesType', 'reportable', NULL, NULL),
('indicatorTemplate', 'reportable', NULL, NULL),
('Institution', 'reportable', NULL, NULL),
('InstitutionDepartment', 'reportable', NULL, NULL),
('ISO36612', 'reportable', NULL, NULL),
('issue', 'reportable', NULL, NULL),
('IssueGroup', 'reportable', NULL, NULL),
('IssueGroupIssues', 'reportable', NULL, NULL),
('joint_admissions', 'reportable', NULL, NULL),
('labPatientPhysicianInfo', 'reportable', NULL, NULL),
('labReportInformation', 'reportable', NULL, NULL),
('labRequestReportLink', 'reportable', NULL, NULL),
('labTestResults', 'reportable', NULL, NULL),
('log', 'reportable', NULL, NULL),
('log_letters', 'reportable', NULL, NULL),
('log_teleplantx', 'reportable', NULL, NULL),
('LookupCodeValue', 'reportable', NULL, NULL),
('LookupList', 'reportable', NULL, NULL),
('LookupListItem', 'reportable', NULL, NULL),
('lst_admission_status', 'reportable', NULL, NULL),
('lst_discharge_reason', 'reportable', NULL, NULL),
('lst_field_category', 'reportable', NULL, NULL),
('lst_gender', 'reportable', NULL, NULL),
('lst_organization', 'reportable', NULL, NULL),
('lst_orgcd', 'reportable', NULL, NULL),
('lst_program_type', 'reportable', NULL, NULL),
('lst_sector', 'reportable', NULL, NULL),
('lst_service_restriction', 'reportable', NULL, NULL),
('mdsMSH', 'reportable', NULL, NULL),
('mdsNTE', 'reportable', NULL, NULL),
('mdsOBR', 'reportable', NULL, NULL),
('mdsOBX', 'reportable', NULL, NULL),
('mdsPID', 'reportable', NULL, NULL),
('mdsPV1', 'reportable', NULL, NULL),
('mdsZFR', 'reportable', NULL, NULL),
('mdsZLB', 'reportable', NULL, NULL),
('mdsZMC', 'reportable', NULL, NULL),
('mdsZMN', 'reportable', NULL, NULL),
('mdsZRG', 'reportable', NULL, NULL),
('measurementCSSLocation', 'reportable', NULL, NULL),
('measurementGroup', 'reportable', NULL, NULL),
('measurementGroupStyle', 'reportable', NULL, NULL),
('measurementMap', 'reportable', NULL, NULL),
('measurements', 'reportable', NULL, NULL),
('measurementsDeleted', 'reportable', NULL, NULL),
('measurementsExt', 'reportable', NULL, NULL),
('measurementType', 'reportable', NULL, NULL),
('measurementTypeDeleted', 'reportable', NULL, NULL),
('messagelisttbl', 'reportable', NULL, NULL),
('messagetbl', 'reportable', NULL, NULL),
('messengerMembershipLock', 'reportable', NULL, NULL),
('msgDemoMap', 'reportable', NULL, NULL),
('mygroup', 'reportable', NULL, NULL),
('MyGroupAccessRestriction', 'reportable', NULL, NULL),
('OceanSetting', 'restricted', NULL, 'Ocean integration settings document; may carry integration credentials'),
('oscar_msg_type', 'reportable', NULL, NULL),
('OscarCode', 'reportable', NULL, NULL),
('oscarcommlocations', 'restricted', NULL, 'Remote messaging locations and their authentication data'),
('OscarJob', 'restricted', NULL, 'Scheduled job definitions with free-form configuration strings'),
('OscarJobType', 'reportable', NULL, NULL),
('oscarKeys', 'restricted', NULL, 'Messaging key pairs, including private keys'),
('other_id', 'reportable', NULL, NULL),
('PageMonitor', 'restricted', NULL, 'Live HTTP session identifiers of clinicians viewing shared pages'),
('partial_date', 'reportable', NULL, NULL),
('patientLabRouting', 'reportable', NULL, NULL),
('pharmacyInfo', 'reportable', NULL, NULL),
('pmm_log', 'reportable', NULL, NULL),
('prescribe', 'reportable', NULL, NULL),
('prescription', 'reportable', NULL, NULL),
('PreventionReport', 'reportable', NULL, NULL),
('preventions', 'reportable', NULL, NULL),
('preventionsExt', 'reportable', NULL, NULL),
('PreventionsLotNrs', 'reportable', NULL, NULL),
('PrintResourceLog', 'reportable', NULL, NULL),
('professionalSpecialists', 'restricted', NULL, 'eData integration keys and endpoints stored per specialist'),
('program', 'reportable', NULL, NULL),
('program_access', 'reportable', NULL, NULL),
('program_access_roles', 'reportable', NULL, NULL),
('program_client_restriction', 'reportable', NULL, NULL),
('program_clientstatus', 'reportable', NULL, NULL),
('program_functional_user', 'reportable', NULL, NULL),
('program_provider', 'reportable', NULL, NULL),
('program_provider_team', 'reportable', NULL, NULL),
('program_queue', 'reportable', NULL, NULL),
('program_team', 'reportable', NULL, NULL),
('programSignature', 'reportable', NULL, NULL),
('property', 'restricted', NULL, 'Provider and system properties, including integration credentials'),
('provider', 'reportable', NULL, NULL),
('provider_default_program', 'reportable', NULL, NULL),
('provider_facility', 'reportable', NULL, NULL),
('providerArchive', 'reportable', NULL, NULL),
('providerbillcenter', 'reportable', NULL, NULL),
('providerExt', 'reportable', NULL, NULL),
('providerLabRouting', 'reportable', NULL, NULL),
('providerLabRoutingFavorites', 'reportable', NULL, NULL),
('providerLabRoutingLock', 'reportable', NULL, NULL),
('ProviderPreference', 'reportable', NULL, NULL),
('ProviderPreferenceAppointmentScreenEForm', 'reportable', NULL, NULL),
('ProviderPreferenceAppointmentScreenForm', 'reportable', NULL, NULL),
('ProviderPreferenceAppointmentScreenQuickLink', 'reportable', NULL, NULL),
('providersite', 'reportable', NULL, NULL),
('providerstudy', 'reportable', NULL, NULL),
('publicKeys', 'restricted', NULL, 'Service key pairs, including private keys'),
('queue', 'reportable', NULL, NULL),
('queue_document_link', 'reportable', NULL, NULL),
('quickList', 'reportable', NULL, NULL),
('quickListUser', 'reportable', NULL, NULL),
('radetail', 'reportable', NULL, NULL),
('raheader', 'reportable', NULL, NULL),
('rbt_groups', 'reportable', NULL, NULL),
('recyclebin', 'reportable', NULL, NULL),
('rehabStudy2004', 'reportable', NULL, NULL),
('relationships', 'reportable', NULL, NULL),
('remoteAttachments', 'reportable', NULL, NULL),
('RemoteDataLog', 'reportable', NULL, NULL),
('RemoteReferral', 'reportable', NULL, NULL),
('report', 'reportable', NULL, NULL),
('report_date', 'restricted', NULL, 'Legacy report scratch table keyed by HTTP session identifier'),
('report_letters', 'reportable', NULL, NULL),
('reportagesex', 'reportable', NULL, NULL),
('reportByExamples', 'reportable', NULL, NULL),
('reportByExamplesFavorite', 'reportable', NULL, NULL),
('reportConfig', 'reportable', NULL, NULL),
('reportFilter', 'reportable', NULL, NULL),
('reportItem', 'reportable', NULL, NULL),
('reportprovider', 'reportable', NULL, NULL),
('reportTableFieldCaption', 'reportable', NULL, NULL),
('reporttemp', 'reportable', NULL, NULL),
('reportTemplates', 'reportable', NULL, NULL),
('ResourceStorage', 'reportable', NULL, NULL),
('room', 'reportable', NULL, NULL),
('rschedule', 'reportable', NULL, NULL),
('scheduledate', 'reportable', NULL, NULL),
('scheduleholiday', 'reportable', NULL, NULL),
('scheduletemplate', 'reportable', NULL, NULL),
('scheduletemplatecode', 'reportable', NULL, NULL),
('scratch_pad', 'reportable', NULL, NULL),
('secObjectName', 'reportable', NULL, NULL),
('secObjPrivilege', 'reportable', NULL, NULL),
('secPrivilege', 'reportable', NULL, NULL),
('secRole', 'reportable', NULL, NULL),
('security', 'restricted', NULL, 'Login password hashes, PINs and MFA secrets'),
('SecurityArchive', 'restricted', NULL, 'Historical password hashes and PINs'),
('SecurityToken', 'restricted', NULL, 'Password-reset and one-time login tokens'),
('secUserRole', 'reportable', NULL, NULL),
('ServiceAccessToken', 'restricted', NULL, 'REST API OAuth access tokens and token secrets'),
('ServiceClient', 'restricted', NULL, 'REST API OAuth client keys and client secrets'),
('ServiceOAuthNonce', 'restricted', NULL, 'REST API OAuth replay-protection records with consumer keys'),
('ServiceRequestToken', 'restricted', NULL, 'REST API OAuth request tokens, token secrets and verifiers'),
('serviceSpecialists', 'reportable', NULL, NULL),
('site', 'reportable', NULL, NULL),
('specialistsJavascript', 'reportable', NULL, NULL),
('specialty', 'reportable', NULL, NULL),
('study', 'reportable', NULL, NULL),
('studydata', 'reportable', NULL, NULL),
('studylogin', 'restricted', NULL, 'Research-study portal usernames and passwords'),
('survey', 'reportable', NULL, NULL),
('surveyData', 'reportable', NULL, NULL),
('SystemMessage', 'reportable', NULL, NULL),
('SystemPreferences', 'reportable', NULL, NULL),
('table_modification', 'reportable', NULL, NULL),
('teleplan_adj_codes', 'reportable', NULL, NULL),
('teleplan_refusal_code', 'reportable', NULL, NULL),
('teleplan_response_log', 'reportable', NULL, NULL),
('teleplan_submission_link', 'reportable', NULL, NULL),
('teleplanC12', 'reportable', NULL, NULL),
('teleplanS00', 'reportable', NULL, NULL),
('teleplanS21', 'reportable', NULL, NULL),
('teleplanS22', 'reportable', NULL, NULL),
('teleplanS23', 'reportable', NULL, NULL),
('teleplanS25', 'reportable', NULL, NULL),
('tickler', 'reportable', NULL, NULL),
('tickler_category', 'reportable', NULL, NULL),
('tickler_comments', 'reportable', NULL, NULL),
('tickler_link', 'reportable', NULL, NULL),
('tickler_text_suggest', 'reportable', NULL, NULL),
('tickler_update', 'reportable', NULL, NULL),
('ticklerdocs', 'reportable', NULL, NULL),
('user_ds_message_prefs', 'reportable', NULL, NULL),
('vacancy', 'reportable', NULL, NULL),
('vacancy_client_match', 'reportable', NULL, NULL),
('vacancy_template', 'reportable', NULL, NULL),
('validations', 'reportable', NULL, NULL),
('view', 'reportable', NULL, NULL),
('waitingList', 'reportable', NULL, NULL),
('waitingListName', 'reportable', NULL, NULL),
('wcb', 'reportable', NULL, NULL),
('wcb_bp_code', 'reportable', NULL, NULL),
('wcb_noi_code', 'reportable', NULL, NULL),
('wcb_side', 'reportable', NULL, NULL),
('workflow', 'reportable', NULL, NULL);

DELIMITER //
BEGIN NOT ATOMIC
    DECLARE v_schema VARCHAR(64) DEFAULT DATABASE();
    DECLARE v_user VARCHAR(128) DEFAULT COALESCE(NULLIF(@carlos_report_user, ''), 'carlos_report');
    -- With lower_case_table_names=0 (the Linux default) table names are case-sensitive, so a
    -- table is granted only when its exact spelling is listed. A server that folds names
    -- stores them in lower case, so the comparison folds too.
    DECLARE v_fold BOOLEAN DEFAULT @@lower_case_table_names <> 0;
    DECLARE v_privilege TEXT;

    IF v_schema IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'carlos_report_grants.sql: select the CARLOS database before running this script';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM mysql.global_priv WHERE User = v_user) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'carlos_report_grants.sql: create the reporting account before granting it tables';
    END IF;
    -- Every host entry of the account, captured before the grants below change anything.
    DROP TEMPORARY TABLE IF EXISTS carlos_report_hosts;
    CREATE TEMPORARY TABLE carlos_report_hosts ENGINE = MEMORY
        AS SELECT Host AS host FROM mysql.global_priv WHERE User = v_user;

    -- Start from nothing: no privileges, no proxy grants, no roles, no default role. Then the
    -- resource limits. (REVOKE ALL leaves PROXY grants in place, so they go one by one.) This
    -- runs before any refusal below, so a sync that stops still leaves nothing stale behind.
    FOR account IN (SELECT host FROM carlos_report_hosts) DO
        EXECUTE IMMEDIATE CONCAT('REVOKE ALL PRIVILEGES, GRANT OPTION FROM ',
            QUOTE(v_user), '@', QUOTE(account.host));
        FOR proxied IN (SELECT Proxied_user AS proxied_user, Proxied_host AS proxied_host
                          FROM mysql.proxies_priv WHERE User = v_user AND Host = account.host) DO
            EXECUTE IMMEDIATE CONCAT('REVOKE PROXY ON ', QUOTE(proxied.proxied_user), '@',
                QUOTE(proxied.proxied_host), ' FROM ', QUOTE(v_user), '@', QUOTE(account.host));
        END FOR;
        FOR granted IN (SELECT Role AS role FROM mysql.roles_mapping
                         WHERE User = v_user AND Host = account.host) DO
            EXECUTE IMMEDIATE CONCAT('REVOKE `', REPLACE(granted.role, '`', '``'), '` FROM ',
                QUOTE(v_user), '@', QUOTE(account.host));
        END FOR;
        EXECUTE IMMEDIATE CONCAT('SET DEFAULT ROLE NONE FOR ', QUOTE(v_user), '@', QUOTE(account.host));
        EXECUTE IMMEDIATE CONCAT('ALTER USER ', QUOTE(v_user), '@', QUOTE(account.host),
            ' WITH MAX_USER_CONNECTIONS 6 MAX_STATEMENT_TIME 30');
    END FOR;

    -- A privilege granted to PUBLIC applies to every account, this one included, and no revoke
    -- above can take it back. Refuse rather than leave a boundary that is not really there.
    IF EXISTS (SELECT 1 FROM mysql.global_priv
                WHERE User = 'PUBLIC' AND COALESCE(JSON_VALUE(Priv, '$.access'), 0) <> 0)
       OR EXISTS (SELECT 1 FROM mysql.db WHERE User = 'PUBLIC' AND v_schema LIKE Db)
       OR EXISTS (SELECT 1 FROM mysql.tables_priv WHERE User = 'PUBLIC' AND Db = v_schema)
       OR EXISTS (SELECT 1 FROM mysql.columns_priv WHERE User = 'PUBLIC' AND Db = v_schema)
       OR EXISTS (SELECT 1 FROM mysql.procs_priv WHERE User = 'PUBLIC' AND Db = v_schema) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'carlos_report_grants.sql: PUBLIC holds privileges that reach the CARLOS schema; revoke them (SHOW GRANTS FOR PUBLIC) and run this script again';
    END IF;

    -- SELECT on every reportable table that exists here, minus any excluded columns.
    FOR reportable IN (
        SELECT t.TABLE_NAME AS table_name, r.excluded_columns AS excluded_columns
          FROM information_schema.TABLES t
          JOIN carlos_report_tables r
            ON r.access = 'reportable'
           AND (CASE WHEN v_fold THEN LOWER(r.table_name) = LOWER(t.TABLE_NAME)
                     ELSE r.table_name = CONVERT(t.TABLE_NAME USING utf8mb4) COLLATE utf8mb4_bin END)
         WHERE t.TABLE_SCHEMA = v_schema AND t.TABLE_TYPE = 'BASE TABLE'
    ) DO
        IF reportable.excluded_columns IS NULL THEN
            SET v_privilege = 'SELECT';
        ELSE
            SET v_privilege = NULL;
            SELECT CONCAT('SELECT (', GROUP_CONCAT(CONCAT('`', REPLACE(c.COLUMN_NAME, '`', '``'), '`')
                                          ORDER BY c.ORDINAL_POSITION SEPARATOR ', '), ')')
              INTO v_privilege
              FROM information_schema.COLUMNS c
             WHERE c.TABLE_SCHEMA = v_schema
               AND c.TABLE_NAME = reportable.table_name
               AND FIND_IN_SET(LOWER(c.COLUMN_NAME),
                               LOWER(REPLACE(reportable.excluded_columns, ' ', ''))) = 0;
        END IF;
        -- NULL when every column is excluded: then the table is simply not granted.
        IF v_privilege IS NOT NULL THEN
            FOR account IN (SELECT host FROM carlos_report_hosts) DO
                EXECUTE IMMEDIATE CONCAT('GRANT ', v_privilege, ' ON `', REPLACE(v_schema, '`', '``'),
                    '`.`', REPLACE(reportable.table_name, '`', '``'), '` TO ',
                    QUOTE(v_user), '@', QUOTE(account.host));
            END FOR;
        END IF;
    END FOR;

    -- The privileges were rebuilt from nothing above, so this fails only if this file is wrong.
    IF EXISTS (SELECT 1 FROM mysql.global_priv
                WHERE User = v_user AND COALESCE(JSON_VALUE(Priv, '$.access'), 0) <> 0)
       OR EXISTS (SELECT 1 FROM mysql.db WHERE User = v_user)
       OR EXISTS (SELECT 1 FROM mysql.procs_priv WHERE User = v_user)
       OR EXISTS (SELECT 1 FROM mysql.proxies_priv WHERE User = v_user)
       OR EXISTS (SELECT 1 FROM mysql.roles_mapping WHERE User = v_user)
       OR EXISTS (SELECT 1 FROM mysql.tables_priv tp
                   WHERE tp.User = v_user
                     AND (tp.Db <> v_schema
                          OR tp.Table_priv NOT IN ('', 'Select')
                          OR tp.Column_priv NOT IN ('', 'Select')
                          OR NOT EXISTS (
                              SELECT 1 FROM carlos_report_tables r
                               WHERE r.access = 'reportable'
                                 AND (CASE WHEN v_fold THEN LOWER(r.table_name) = LOWER(tp.Table_name)
                                           ELSE r.table_name = CONVERT(tp.Table_name USING utf8mb4) COLLATE utf8mb4_bin END)))) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'carlos_report_grants.sql: the reporting account holds a privilege outside the reportable list after the sync';
    END IF;

    DROP TEMPORARY TABLE carlos_report_hosts;
END//
DELIMITER ;

DROP TEMPORARY TABLE carlos_report_tables;
