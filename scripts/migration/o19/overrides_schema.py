# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Hand-curated schema-manifest overlay for the OSCAR 19 importer.

generate_manifests.py deep-merges this over its mechanical O19/CARLOS schema
diff. This file is the durable curation home — regeneration never touches it.

Classification principles (see docs/oscar19-to-carlos-migration-plan.md §4):

* Shared tables default to class "copy". Overrides here mark:
  - CLASS_REFERENCE  — CARLOS's Flyway-seeded rows win outright; the dump's
    rows are ignored. Only for code-owned/ministry data that clinics do not
    author (ICD, security objects, billing error codes, ...).
  - REPLACE_SEED     — the table is seeded by Flyway but its PRIMARY KEYS ARE
    REFERENCED BY CLINIC DATA (issue ids in casemgmt_issue, program ids in
    admissions, the clinic row, schedule config, role matrix). The importer
    deletes the seed rows and copies the clinic's rows id-intact so those
    references stay valid. Safe because P0 has verified the table holds
    exactly the seed rows.
  - CLASS_MERGE      — union semantics on a NATURAL key: CARLOS seed rows
    win, clinic-added rows are appended. For tables where both systems seed
    near-identical standard rows and clinics add custom ones (encounter form
    registry, billing service codes, lookup lists). Tables whose natural key
    is not the PK carry surrogate-id handling in the ETL (M4).
* O19-only tables (absent from CARLOS) are ARCHIVE_PATIENT (clinical /
  patient-authored -> o19_archive + CSV, preflight B1 blocker),
  ARCHIVE_OTHER (clinic config, templates, logs -> o19_archive, advisory),
  or DROP (infrastructure of removed modules, temp tables, reloadable
  ministry reference — allowed only for zero-CARLOS-reference tables of
  documented-removed modules). Anything unlisted stays "unknown", which the
  integrity test refuses to ship.

Legacy-twin note: OSCAR carried duplicate legacy/entity table pairs, and
CARLOS kept only the entity-named survivor. The twins still present in a
patched O19 database — `group_note_link` (GroupNoteLink), `recycle_bin`
(recyclebin), `report_filter` (reportFilter) — are archived below. Twins
that O19's own update scripts already dropped (`facility`, `Vacancy`, temp
tables) are absent from the generated diff; on a clinic database that never
ran those updates they surface through preflight's unknown-table flow (B2,
archive-by-default).
"""

SCHEMA_MAP_VERSION = "o19map-1"

# --- shared-table class overrides -----------------------------------------

# CARLOS-owned data: the dump's rows are ignored entirely.
CLASS_REFERENCE = {
    "icd9", "icd10", "measurementMap", "diagnosticcode", "ichppccode",
    "billing_on_errorCode", "country_codes",
    "secObjectName", "secObjPrivilege", "secPrivilege",
    "gstControl", "specialistsJavascript", "oscarcommlocations",
    "OscarJob", "OscarJobType", "OscarCode", "oscar_msg_type",
    "fax_config",                    # CARLOS fax is SRFax/DB-configured
    "documentDescriptionTemplate",   # CARLOS-era feature seed
    "HRMCategory", "CdsFormOption", "batchEligibility", "CtlRelationships",
    "specialty", "ContactSpecialty",
    "config_Immunization", "consentType",
}

# Seeded tables whose ids clinic data references: delete seeds, copy clinic
# rows id-intact. (provider/security are handled by CARLOSDOC_SEED_DELETES +
# the ordered seed-reconciliation script, not here.)
REPLACE_SEED = {
    "clinic", "clinic_location", "clinic_nbr", "provider_facility",
    "property", "issue", "program", "program_provider",
    "caisi_role", "access_type", "default_role_access", "secRole",
    "secUserRole", "mygroup", "scheduletemplate", "scheduletemplatecode",
    "scheduleholiday", "queue", "groups_tbl", "agency", "Facility",
    "FunctionalCentre", "bed_type", "report", "reportprovider",
    "lst_admission_status", "lst_discharge_reason", "lst_field_category",
    "lst_gender", "lst_organization", "lst_orgcd", "lst_program_type",
    "lst_sector", "lst_service_restriction",
}

# Union on a natural key: CARLOS seeds win, clinic-added rows append.
# Value = the natural-key column list used by the anti-join.
CLASS_MERGE = {
    "encounterForm": ["form_name"],
    "billingservice": ["service_code", "billingservice_date"],
    "ctl_billingservice": ["service_code", "servicetype"],
    "ctl_billingservice_premium": ["service_code", "servicetype_name"],
    "ctl_diagcode": ["servicetype", "diagnostic_code"],
    "ctl_doc_class": ["reportclass", "subclass"],
    "ctl_doctype": ["module", "doctype"],
    "ctl_document": ["module", "module_id", "document_no"],
    "ctl_frequency": ["freqcode"],
    "ctl_specialinstructions": ["description"],
    "appointment_status": ["status"],
    "billing_payment_type": ["payment_type"],
    "billcenter": ["billcenter_code"],
    "consultationServices": ["serviceDesc"],
    "encountertemplate": ["encountertemplate_name"],
    "frm_labreq_preset": ["lab_type", "prop_name"],
    "criteria_type": ["FIELD_NAME"],
    "criteria_type_option": ["CRITERIA_TYPE_ID", "OPTION_VALUE"],
    "HL7HandlerMSHMapping": ["hospital_site", "facility"],
    "Icd9Synonym": ["dxCode", "patientFriendly"],
    "app_lookuptable": ["table_name"],
    "app_lookuptable_fields": ["tableid", "fieldname"],
    "LookupList": ["name"],
    "LookupListItem": ["lookupListId", "value"],
    "quickList": ["quickListName", "dxResearchCode"],
    "tickler_category": ["category"],
    "tickler_text_suggest": ["suggested_text"],
    "measurementType": ["type", "measuringInstruction"],
    "validations": ["name"],
    # seeded by V1.0.10 via statements the seed counter cannot count as
    # rows; clinics customize measurement groups, so union semantics
    "measurementGroup": ["name", "typeDisplayName"],
    "measurementGroupStyle": ["groupName"],
}

# --- O19-only table dispositions ------------------------------------------

ARCHIVE_PATIENT = {
    # deprecated encounter forms (patient-entered)
    "formAR", "formAdf", "formBCAR2007", "formIntakeHx", "formONAR",
    "formONAREnhanced", "formONAREnhancedRecord", "formONAREnhancedRecordExt1",
    "formONAREnhancedRecordExt2", "formType2Diabetes", "form_hsfo_visit",
    "formfollowup", "formovulation",
    # HSFO study
    "hsfo_patient",
    # generic intake answers
    "intake", "intake_answer", "intake_answer_element",
    # OCAN assessments
    "OcanClientForm", "OcanClientFormData", "OcanStaffForm",
    "OcanStaffFormData", "OcanSubmissionLog",
    # eyeform clinical records
    "Eyeform", "EyeformConsultationReport", "EyeformFollowUp",
    "EyeformOcularProcedure", "EyeformSpecsHistory", "EyeformTestBook",
    # drug dispensing records
    "DrugDispensing", "DrugDispensingMapping",
    # CAISI residential care (patient-linked)
    "bed_demographic", "bed_demographic_historical", "bed_demographic_status",
    "room_demographic", "functionalCentreAdmission", "complaint", "incident",
    "oncall_questionnaire",
    # PHR / patient-shared documents and consents
    "phr_documents", "phr_document_ext", "phr_actions", "indivoDocs",
    "PHRVerification",
    # Integrator patient consents
    "IntegratorConsent", "IntegratorConsentComplexExitInterview",
    "IntegratorConsentShareDataMap",
    # sharing center patient-facing records
    "sharing_patient_document", "sharing_patient_network",
    "sharing_patient_policy_consent", "sharing_document_export",
    "sharing_exported_doc", "sharing_mapping_edoc", "sharing_mapping_eform",
    "sharing_mapping_misc",
    # chart annotations, prevention-billing links, messages, site links
    "oscar_annotations", "preventionsBilling", "resident_oscarMsg",
    "demographicSite",
    # legacy twin of the surviving GroupNoteLink (note-to-group linkage)
    "group_note_link",
}

ARCHIVE_OTHER = {
    # form/intake definitions and options (clinic-authored config)
    "intake_answer_validation", "intake_node", "intake_node_js",
    "intake_node_label", "intake_node_template", "intake_node_type",
    "OcanConnexOption", "OcanFormOption",
    "EyeformMacro", "EyeformProcedureBook", "eyeform_macro_billing",
    "eyeform_macro_def",
    "DrugProductTemplate", "ProductLocation",
    "hsfo_system",
    # CAISI facility config
    "bed_check_time", "room_bed", "room_bed_historical", "room_type",
    "onCallClinicDates", "caisi_form_question", "doc_category", "doc_manager",
    "MyGroupProgram", "ProgramContactType", "ProgramEncounterType",
    "ContactType", "EncounterType", "app_module", "site_role_mpg", "secSite",
    # report-runner (clinic-authored report templates)
    "report_template", "report_template_criteria", "report_template_org",
    "report_date_sp", "report_doctext", "report_document", "report_filter",
    "report_lk_reportgroup", "report_option", "report_qgviewfield",
    "report_qgviewsummary", "report_role",
    # CAISI lookup lists (possibly clinic-edited)
    "lst_aboriginal", "lst_actions_content", "lst_bed_type", "lst_casestatus",
    "lst_complaint_method", "lst_complaint_outcome", "lst_complaint_section",
    "lst_complaint_source", "lst_complaint_subsection",
    "lst_componentofservice", "lst_country", "lst_cursleeparrangement",
    "lst_documentcategory", "lst_documenttype", "lst_encounter_type",
    "lst_family_relationship", "lst_fieldtype", "lst_incident_clientissues",
    "lst_incident_disposition", "lst_incident_nature", "lst_incident_others",
    "lst_intake_reject_reason", "lst_language", "lst_lengthofhomeless",
    "lst_livedbefore", "lst_message_type", "lst_operator", "lst_province",
    "lst_reason_notsign", "lst_reasonforhomeless", "lst_reasonforservice",
    "lst_reasonnoadmit", "lst_referredby", "lst_referredto", "lst_room_type",
    "lst_shelter", "lst_shelter_standards", "lst_sourceincome",
    "lst_statusincanada", "lst_title", "lst_transportation_type",
    # transmission/report logs of removed integrations
    "BornTransmissionLog", "ORNCkdScreeningReportLog",
    "ORNPreImplementationReportLog", "SentToPHRTracking",
    # OLIS preferences (module removed — advisory)
    "OLISProviderPreferences", "OLISSystemPreferences",
    # misc small clinic config / survey remnants
    "survey_test_data", "survey_test_instance", "uploadfile_from",
    # legacy twin of the surviving `recyclebin` (deleted-item store)
    "recycle_bin",
}

DROP = {
    # Integrator sync machinery (no clinical content)
    "IntegratorControl", "IntegratorProgress", "IntegratorProgressItem",
    "RemoteIntegratedDataCopy",
    # sharing center infrastructure
    "sharing_acl_definition", "sharing_actor", "sharing_affinity_domain",
    "sharing_clinic_info", "sharing_code_mapping", "sharing_code_value",
    "sharing_infrastructure", "sharing_mapping_code", "sharing_mapping_site",
    "sharing_policy_definition", "sharing_value_set",
    # cookie-revolver auth infrastructure
    "cr_cert", "cr_iprange", "cr_machine", "cr_policy",
    "cr_securityquestion", "cr_user", "cr_userrole",
    # reloadable ministry reference of removed modules
    "BORNPathwayMapping", "OLISRequestNomenclature", "OLISResultNomenclature",
    "mdsZCL", "mdsZCT",
    # temp tables
    "caisi_form_instance_tmpsave",
}

# --- column-level curation ------------------------------------------------

# Dropped columns whose non-default usage is a preflight B3 blocker (the
# clinic actively used a workflow CARLOS removed). All other dropped columns
# still get shadow-table capture, just without blocking.
B3_COLUMNS = {
    ("drugs", "dispensingUnits"),
    ("drugs", "outside_provider"),
    ("document", "fileSignature"),
}
# (demographic.preferred_lang is NOT dropped: O19's own update-2009-02-23
# renamed it to official_lang, which is shared and copies. A pre-2009
# unpatched database surfaces it through preflight's unknown-column flow.)

# Big tables copied in PK windows (single-column integer PK verified by the
# generator at emission time).
CHUNK_TABLES = {
    "hl7TextMessage", "document", "casemgmt_note", "eform_data",
    "measurements", "measurementsExt", "casemgmt_note_ext",
    "appointment", "log", "billing_on_cheader1", "billing_on_item",
    "billing_on_transaction", "dxresearch", "hl7TextInfo",
}

# Narrative text columns sampled for latin1/utf8 double-encoding (§4.4).
CHARSET_SCAN = {
    "demographic": ["last_name", "first_name", "address", "city"],
    "casemgmt_note": ["note"],
    "drugs": ["special", "comment"],
    "document": ["docdesc"],
    "appointment": ["reason", "notes", "name"],
    "tickler": ["message"],
    "messagetbl": ["thesubject", "themessage"],
    "consultationRequests": ["reason", "clinicalInfo"],
    "demographiccust": ["content"],
}

# Rows Flyway seeds for the default clinician; deleted (in order) after the
# break-glass admin exists and before providers/security copy. WHERE clauses
# are fragments the ETL wraps in DELETE statements.
CARLOSDOC_SEED_DELETES = [
    ("secUserRole", "provider_no = '999998'"),
    ("ProviderPreference", "providerNo = '999998'"),
    ("providersite", "provider_no = '999998'"),
    ("security", "user_name = 'carlosdoc'"),
    ("provider", "provider_no = '999998'"),
]

# CARLOS-added NOT NULL columns without defaults: the value the import
# synthesizes for them (SQL over the staging row alias `s`). The ETL
# pre-check aborts naming any such column that lacks an entry here.
VALUE_EXPRS = {
    # uid groups pharmacy record revisions in CARLOS; each imported O19
    # pharmacy heads its own group.
    "pharmacyInfo": {"uid": "s.`recordID`"},
}

SEED_PROVIDER_NO = "999998"
SEED_USER_NAME = "carlosdoc"

# Property prefixes of removed modules, embedded into o19_preflight.py for
# the dropped-keys advisory (ldap. escalates to blocker B5 there).
PREFLIGHT_DROPPED_PROP_PREFIXES = [
    "ldap.", "born", "INTEGRATOR_", "MY_OSCAR", "MYOSCAR", "myoscar",
    "oscar_myoscar", "mymeds", "CBI_", "OLIS_", "olis_", "util.erx.",
    "clinicaid", "indivica", "consultation_indivica", "spire_",
    "redirectstudysite_", "sharingcenter", "cr_security", "RX3",
    "loginlogo", "logintext", "logintitle", "MYDRUGREF", "eaaps.",
    "health_tracker", "streethealth",
]
