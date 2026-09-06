# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""o19_preflight — OSCAR 19 -> CARLOS migration feasibility check
(experimental).

Runs the go/no-go gate of docs/oscar19-to-carlos-migration-plan.md section 6.1
in two modes with identical checks:

* ASSESSMENT MODE (standalone, at the clinic): copy THIS ONE FILE to the
  OSCAR 19 server and run it against the live database before any backup is
  shipped. It is deliberately self-contained and runs on the old Python 3
  found on 2014-era Ubuntu — Python 3.4 and newer (no f-strings, no
  annotations, stdlib only) — and talks to MySQL by shelling out to the
  mysql/mariadb command-line client:

      python3 o19_preflight.py --db oscar --mysql-cmd mysql \\
          --mysql-arg=-uroot --mysql-password-file /root/.o19pw \\
          --properties /path/to/oscar.properties --province on

  Add `--digests o19-digests.json` and ship that file in the bundle: it
  carries a content digest of every table, which is what lets the CARLOS
  host prove the dump, the transfer and the restore carried every VALUE
  rather than merely the right number of rows. It costs one full scan.

  (--mysql-arg values start with '-', so the =form is required; the
  password travels via MYSQL_PWD from --mysql-password-file, never argv —
  a bare interactive -p would prompt once per query and is refused.
  --province on|bc selects the provincial profile the checks assume,
  default on.)

* IMPORT MODE: `carlos-ctl import-o19` imports this module and calls
  run_checks() against the restored o19_import staging schema, passing the
  full schema manifest for column-level checks.

Exit codes: 0 = go; 1 = go-with-acknowledgements (blockers exist but every
one names the --accept flag that clears it); 2 = no-go (a blocker needs
remediation, not a flag); 3 = the check itself could not run (bad
arguments, unreadable file, database error) — never confused with a
verdict. Blocker classes this mode can acknowledge (--accept, repeatable):
archived-forms, unknown-as-archive, olis-gone, dropped-columns,
carry-credentials (B9 — live OAuth consumer secrets or signing keys the
copy would carry verbatim; rotate or verify them before go-live) and
charset-repair (double-encoded text repaired per row on the copy; B8 is
the neighbouring HARD stop for text that does not round-trip, which no
flag clears). The
remaining import-side sign-offs belong to phases this mode never runs.
The JSON report (--json) is the machine contract.

Migration output should receive a technical review — verification report,
spot checks, UI smoke — before clinical use.

The data between the GENERATED markers is written by
scripts/migration/o19/generate_manifests.py — do not edit it by hand.
"""

from __future__ import print_function

import argparse
import datetime
import json
import os
import re
import shlex
import subprocess
import sys

# === BEGIN GENERATED DATA (generate_manifests.py) ===
SCHEMA_MAP_VERSION = 'o19map-2+d6061d0a'
O19_PROFILE = 'on'
REQUIRED_TABLES = [
    'Facility',
    'clinic',
    'provider',
    'security',
    'secRole',
    'secUserRole',
    'secObjPrivilege',
    'secObjectName',
    'program',
    'program_provider',
    'provider_facility',
    'preventions',
    'eform',
    'property',
]
PATIENT_DATA_TABLES = [
    'DrugDispensing',
    'DrugDispensingMapping',
    'Eyeform',
    'EyeformConsultationReport',
    'EyeformFollowUp',
    'EyeformOcularProcedure',
    'EyeformSpecsHistory',
    'EyeformTestBook',
    'IntegratorConsent',
    'IntegratorConsentComplexExitInterview',
    'IntegratorConsentShareDataMap',
    'OcanClientForm',
    'OcanClientFormData',
    'OcanStaffForm',
    'OcanStaffFormData',
    'OcanSubmissionLog',
    'PHRVerification',
    'bed_demographic',
    'bed_demographic_historical',
    'bed_demographic_status',
    'complaint',
    'demographicSite',
    'formAR',
    'formAdf',
    'formBCAR2007',
    'formIntakeHx',
    'formONAR',
    'formONAREnhanced',
    'formONAREnhancedRecord',
    'formONAREnhancedRecordExt1',
    'formONAREnhancedRecordExt2',
    'formType2Diabetes',
    'form_hsfo_visit',
    'formfollowup',
    'formovulation',
    'functionalCentreAdmission',
    'group_note_link',
    'hsfo_patient',
    'incident',
    'indivoDocs',
    'intake',
    'intake_answer',
    'intake_answer_element',
    'oncall_questionnaire',
    'oscar_annotations',
    'phr_actions',
    'phr_document_ext',
    'phr_documents',
    'preventionsBilling',
    'resident_oscarMsg',
    'room_demographic',
    'sharing_document_export',
    'sharing_exported_doc',
    'sharing_mapping_edoc',
    'sharing_mapping_eform',
    'sharing_mapping_misc',
    'sharing_patient_document',
    'sharing_patient_network',
    'sharing_patient_policy_consent',
]
KNOWN_TABLES = {
    'AppDefinition': 'copy',
    'AppUser': 'copy',
    'BORNPathwayMapping': 'drop',
    'BornTransmissionLog': 'archive',
    'CdsClientForm': 'copy',
    'CdsClientFormData': 'copy',
    'CdsFormOption': 'reference',
    'CdsHospitalisationDays': 'copy',
    'ClientLink': 'copy',
    'Consent': 'copy',
    'Contact': 'copy',
    'ContactSpecialty': 'reference',
    'ContactType': 'archive',
    'CtlRelationships': 'reference',
    'DemographicContact': 'copy',
    'Department': 'copy',
    'DigitalSignature': 'copy',
    'DrugDispensing': 'archive',
    'DrugDispensingMapping': 'archive',
    'DrugProduct': 'copy',
    'DrugProductTemplate': 'archive',
    'EFormReportTool': 'copy',
    'EncounterType': 'archive',
    'Episode': 'copy',
    'Eyeform': 'archive',
    'EyeformConsultationReport': 'archive',
    'EyeformFollowUp': 'archive',
    'EyeformMacro': 'archive',
    'EyeformOcularProcedure': 'archive',
    'EyeformProcedureBook': 'archive',
    'EyeformSpecsHistory': 'archive',
    'EyeformTestBook': 'archive',
    'Facility': 'copy',
    'FaxClientLog': 'copy',
    'FlowSheetUserCreated': 'copy',
    'Flowsheet': 'copy',
    'FunctionalCentre': 'copy',
    'GroupNoteLink': 'copy',
    'HL7HandlerMSHMapping': 'merge',
    'HRMCategory': 'merge',
    'HRMDocument': 'copy',
    'HRMDocumentComment': 'copy',
    'HRMDocumentSubClass': 'copy',
    'HRMDocumentToDemographic': 'copy',
    'HRMDocumentToProvider': 'copy',
    'HRMProviderConfidentialityStatement': 'copy',
    'HRMSubClass': 'copy',
    'HnrDataValidation': 'copy',
    'Icd9Synonym': 'merge',
    'Institution': 'copy',
    'InstitutionDepartment': 'copy',
    'IntegratorConsent': 'archive',
    'IntegratorConsentComplexExitInterview': 'archive',
    'IntegratorConsentShareDataMap': 'archive',
    'IntegratorControl': 'drop',
    'IntegratorProgress': 'drop',
    'IntegratorProgressItem': 'drop',
    'IssueGroup': 'copy',
    'IssueGroupIssues': 'copy',
    'LookupList': 'merge',
    'LookupListItem': 'merge',
    'MyGroupAccessRestriction': 'copy',
    'MyGroupProgram': 'archive',
    'OLISProviderPreferences': 'archive',
    'OLISRequestNomenclature': 'drop',
    'OLISResultNomenclature': 'drop',
    'OLISSystemPreferences': 'archive',
    'ORNCkdScreeningReportLog': 'archive',
    'ORNPreImplementationReportLog': 'archive',
    'OcanClientForm': 'archive',
    'OcanClientFormData': 'archive',
    'OcanConnexOption': 'archive',
    'OcanFormOption': 'archive',
    'OcanStaffForm': 'archive',
    'OcanStaffFormData': 'archive',
    'OcanSubmissionLog': 'archive',
    'OscarCode': 'reference',
    'OscarJob': 'reference',
    'OscarJobType': 'reference',
    'PHRVerification': 'archive',
    'PageMonitor': 'copy',
    'PreventionsLotNrs': 'copy',
    'PrintResourceLog': 'copy',
    'ProductLocation': 'archive',
    'ProgramContactType': 'archive',
    'ProgramEncounterType': 'archive',
    'ProviderPreference': 'copy',
    'ProviderPreferenceAppointmentScreenEForm': 'copy',
    'ProviderPreferenceAppointmentScreenForm': 'copy',
    'ProviderPreferenceAppointmentScreenQuickLink': 'copy',
    'RemoteDataLog': 'copy',
    'RemoteIntegratedDataCopy': 'drop',
    'RemoteReferral': 'copy',
    'ResourceStorage': 'copy',
    'SecurityArchive': 'copy',
    'SecurityToken': 'archive',
    'SentToPHRTracking': 'archive',
    'ServiceAccessToken': 'archive',
    'ServiceClient': 'copy',
    'ServiceRequestToken': 'archive',
    'SystemMessage': 'copy',
    'access_type': 'copy',
    'admission': 'copy',
    'agency': 'copy',
    'allergies': 'copy',
    'app_lookuptable': 'merge',
    'app_lookuptable_fields': 'merge',
    'app_module': 'archive',
    'appointment': 'copy',
    'appointmentArchive': 'copy',
    'appointmentType': 'copy',
    'appointment_status': 'merge',
    'batchEligibility': 'reference',
    'batch_billing': 'copy',
    'bed': 'copy',
    'bed_check_time': 'archive',
    'bed_demographic': 'archive',
    'bed_demographic_historical': 'archive',
    'bed_demographic_status': 'archive',
    'bed_type': 'copy',
    'billactivity': 'copy',
    'billcenter': 'merge',
    'billing': 'copy',
    'billing_on_3rdPartyAddress': 'copy',
    'billing_on_cheader1': 'copy',
    'billing_on_cheader2': 'copy',
    'billing_on_diskname': 'copy',
    'billing_on_eareport': 'copy',
    'billing_on_errorCode': 'reference',
    'billing_on_ext': 'copy',
    'billing_on_favourite': 'copy',
    'billing_on_filename': 'copy',
    'billing_on_header': 'copy',
    'billing_on_item': 'copy',
    'billing_on_item_payment': 'copy',
    'billing_on_payment': 'copy',
    'billing_on_premium': 'copy',
    'billing_on_proc': 'copy',
    'billing_on_repo': 'copy',
    'billing_on_transaction': 'copy',
    'billing_payment_type': 'merge',
    'billingdetail': 'copy',
    'billinginr': 'copy',
    'billingperclimit': 'copy',
    'billingreferral': 'copy',
    'billingservice': 'merge',
    'caisi_form': 'copy',
    'caisi_form_data': 'copy',
    'caisi_form_data_tmpsave': 'copy',
    'caisi_form_instance': 'copy',
    'caisi_form_instance_tmpsave': 'drop',
    'caisi_form_question': 'archive',
    'caisi_role': 'copy',
    'casemgmt_cpp': 'copy',
    'casemgmt_issue': 'copy',
    'casemgmt_issue_notes': 'copy',
    'casemgmt_note': 'copy',
    'casemgmt_note_ext': 'copy',
    'casemgmt_note_link': 'copy',
    'casemgmt_note_lock': 'copy',
    'casemgmt_tmpsave': 'copy',
    'client_image': 'copy',
    'client_referral': 'copy',
    'clinic': 'copy',
    'clinic_location': 'copy',
    'clinic_nbr': 'copy',
    'complaint': 'archive',
    'config_Immunization': 'reference',
    'consentType': 'merge',
    'consultResponseDoc': 'copy',
    'consultationRequestExt': 'copy',
    'consultationRequests': 'copy',
    'consultationResponse': 'copy',
    'consultationServices': 'merge',
    'consultdocs': 'copy',
    'country_codes': 'reference',
    'cr_cert': 'drop',
    'cr_iprange': 'drop',
    'cr_machine': 'drop',
    'cr_policy': 'drop',
    'cr_securityquestion': 'drop',
    'cr_user': 'drop',
    'cr_userrole': 'drop',
    'criteria': 'copy',
    'criteria_selection_option': 'copy',
    'criteria_type': 'merge',
    'criteria_type_option': 'merge',
    'cssStyles': 'copy',
    'ctl_billingservice': 'merge',
    'ctl_billingservice_premium': 'merge',
    'ctl_billingtype': 'copy',
    'ctl_diagcode': 'merge',
    'ctl_doc_class': 'merge',
    'ctl_doctype': 'merge',
    'ctl_document': 'copy',
    'ctl_frequency': 'merge',
    'ctl_specialinstructions': 'merge',
    'custom_filter': 'copy',
    'custom_filter_assignees': 'copy',
    'custom_filter_providers': 'copy',
    'dashboard': 'copy',
    'dataExport': 'copy',
    'default_issue': 'copy',
    'default_role_access': 'copy',
    'demographic': 'copy',
    'demographicArchive': 'copy',
    'demographicExt': 'copy',
    'demographicExtArchive': 'copy',
    'demographicPharmacy': 'copy',
    'demographicQueryFavourites': 'copy',
    'demographicSets': 'copy',
    'demographicSite': 'archive',
    'demographic_merged': 'copy',
    'demographicaccessory': 'copy',
    'demographiccust': 'copy',
    'demographiccustArchive': 'copy',
    'demographicstudy': 'copy',
    'desannualreviewplan': 'copy',
    'desaprisk': 'copy',
    'diagnosticcode': 'reference',
    'diseases': 'copy',
    'doc_category': 'archive',
    'doc_manager': 'archive',
    'document': 'copy',
    'documentDescriptionTemplate': 'reference',
    'document_storage': 'copy',
    'drugReason': 'copy',
    'drugs': 'copy',
    'dsGuidelineProviderMap': 'copy',
    'dsGuidelines': 'copy',
    'dx_associations': 'copy',
    'dxresearch': 'copy',
    'eChart': 'copy',
    'eform': 'copy',
    'eform_data': 'copy',
    'eform_groups': 'copy',
    'eform_values': 'copy',
    'encounter': 'copy',
    'encounterForm': 'merge',
    'encounterWindow': 'copy',
    'encountertemplate': 'merge',
    'eyeform_macro_billing': 'archive',
    'eyeform_macro_def': 'archive',
    'facility_message': 'copy',
    'favorites': 'copy',
    'favoritesprivilege': 'copy',
    'fax_config': 'reference',
    'faxes': 'copy',
    'fileUploadCheck': 'copy',
    'flowsheet_customization': 'copy',
    'flowsheet_drug': 'copy',
    'flowsheet_dx': 'copy',
    'form': 'copy',
    'form2MinWalk': 'copy',
    'formAR': 'archive',
    'formAdf': 'archive',
    'formAdfV2': 'copy',
    'formAlpha': 'copy',
    'formAnnual': 'copy',
    'formAnnualV2': 'copy',
    'formBCAR2007': 'archive',
    'formBCHP': 'copy',
    'formBPMH': 'copy',
    'formCESD': 'copy',
    'formCaregiver': 'copy',
    'formConsult': 'copy',
    'formCostQuestionnaire': 'copy',
    'formCounseling': 'copy',
    'formDischargeSummary': 'copy',
    'formFalls': 'copy',
    'formGripStrength': 'copy',
    'formGrowth0_36': 'copy',
    'formGrowthChart': 'copy',
    'formHomeFalls': 'copy',
    'formImmunAllergy': 'copy',
    'formIntakeHx': 'archive',
    'formIntakeInfo': 'copy',
    'formInternetAccess': 'copy',
    'formLabReq': 'copy',
    'formLabReq07': 'copy',
    'formLabReq10': 'copy',
    'formLateLifeFDIDisability': 'copy',
    'formLateLifeFDIFunction': 'copy',
    'formMMSE': 'copy',
    'formMentalHealth': 'copy',
    'formMentalHealthForm1': 'copy',
    'formMentalHealthForm14': 'copy',
    'formMentalHealthForm42': 'copy',
    'formNoShowPolicy': 'copy',
    'formONAR': 'archive',
    'formONAREnhanced': 'archive',
    'formONAREnhancedRecord': 'archive',
    'formONAREnhancedRecordExt1': 'archive',
    'formONAREnhancedRecordExt2': 'archive',
    'formPalliativeCare': 'copy',
    'formPeriMenopausal': 'copy',
    'formPositionHazard': 'copy',
    'formRhImmuneGlobulin': 'copy',
    'formRourke': 'copy',
    'formRourke2006': 'copy',
    'formRourke2009': 'copy',
    'formSF36': 'copy',
    'formSF36Caregiver': 'copy',
    'formSatisfactionScale': 'copy',
    'formSelfAdministered': 'copy',
    'formSelfAssessment': 'copy',
    'formSelfEfficacy': 'copy',
    'formSelfManagement': 'copy',
    'formTreatmentPref': 'copy',
    'formType2Diabetes': 'archive',
    'formVTForm': 'copy',
    'form_hsfo2_visit': 'copy',
    'form_hsfo_visit': 'archive',
    'formchf': 'copy',
    'formfollowup': 'archive',
    'formovulation': 'archive',
    'formreceptionassessment': 'copy',
    'frm_labreq_preset': 'merge',
    'functionalCentreAdmission': 'archive',
    'functional_user_type': 'copy',
    'groupMembers_tbl': 'copy',
    'group_note_link': 'archive',
    'groups_tbl': 'copy',
    'gstControl': 'reference',
    'hash_audit': 'copy',
    'health_safety': 'copy',
    'hl7TextInfo': 'copy',
    'hl7TextMessage': 'copy',
    'hsfo2_patient': 'copy',
    'hsfo2_system': 'copy',
    'hsfo_patient': 'archive',
    'hsfo_recommit_schedule': 'copy',
    'hsfo_system': 'archive',
    'icd10': 'reference',
    'icd9': 'reference',
    'ichppccode': 'reference',
    'immunizations': 'copy',
    'incident': 'archive',
    'incomingLabRules': 'copy',
    'indicatorTemplate': 'copy',
    'indivoDocs': 'archive',
    'intake': 'archive',
    'intake_answer': 'archive',
    'intake_answer_element': 'archive',
    'intake_answer_validation': 'archive',
    'intake_node': 'archive',
    'intake_node_js': 'archive',
    'intake_node_label': 'archive',
    'intake_node_template': 'archive',
    'intake_node_type': 'archive',
    'issue': 'copy',
    'joint_admissions': 'copy',
    'labPatientPhysicianInfo': 'copy',
    'labReportInformation': 'copy',
    'labRequestReportLink': 'copy',
    'labTestResults': 'copy',
    'log': 'copy',
    'log_letters': 'copy',
    'lst_aboriginal': 'archive',
    'lst_actions_content': 'archive',
    'lst_admission_status': 'copy',
    'lst_bed_type': 'archive',
    'lst_casestatus': 'archive',
    'lst_complaint_method': 'archive',
    'lst_complaint_outcome': 'archive',
    'lst_complaint_section': 'archive',
    'lst_complaint_source': 'archive',
    'lst_complaint_subsection': 'archive',
    'lst_componentofservice': 'archive',
    'lst_country': 'archive',
    'lst_cursleeparrangement': 'archive',
    'lst_discharge_reason': 'copy',
    'lst_documentcategory': 'archive',
    'lst_documenttype': 'archive',
    'lst_encounter_type': 'archive',
    'lst_family_relationship': 'archive',
    'lst_field_category': 'copy',
    'lst_fieldtype': 'archive',
    'lst_gender': 'merge',
    'lst_incident_clientissues': 'archive',
    'lst_incident_disposition': 'archive',
    'lst_incident_nature': 'archive',
    'lst_incident_others': 'archive',
    'lst_intake_reject_reason': 'archive',
    'lst_language': 'archive',
    'lst_lengthofhomeless': 'archive',
    'lst_livedbefore': 'archive',
    'lst_message_type': 'archive',
    'lst_operator': 'archive',
    'lst_organization': 'copy',
    'lst_orgcd': 'copy',
    'lst_program_type': 'copy',
    'lst_province': 'archive',
    'lst_reason_notsign': 'archive',
    'lst_reasonforhomeless': 'archive',
    'lst_reasonforservice': 'archive',
    'lst_reasonnoadmit': 'archive',
    'lst_referredby': 'archive',
    'lst_referredto': 'archive',
    'lst_room_type': 'archive',
    'lst_sector': 'copy',
    'lst_service_restriction': 'copy',
    'lst_shelter': 'archive',
    'lst_shelter_standards': 'archive',
    'lst_sourceincome': 'archive',
    'lst_statusincanada': 'archive',
    'lst_title': 'archive',
    'lst_transportation_type': 'archive',
    'mdsMSH': 'copy',
    'mdsNTE': 'copy',
    'mdsOBR': 'copy',
    'mdsOBX': 'copy',
    'mdsPID': 'copy',
    'mdsPV1': 'copy',
    'mdsZCL': 'drop',
    'mdsZCT': 'drop',
    'mdsZFR': 'copy',
    'mdsZLB': 'copy',
    'mdsZMC': 'copy',
    'mdsZMN': 'copy',
    'mdsZRG': 'copy',
    'measurementCSSLocation': 'copy',
    'measurementGroup': 'merge',
    'measurementGroupStyle': 'merge',
    'measurementMap': 'reference',
    'measurementType': 'merge',
    'measurementTypeDeleted': 'copy',
    'measurements': 'copy',
    'measurementsDeleted': 'copy',
    'measurementsExt': 'copy',
    'messagelisttbl': 'copy',
    'messagetbl': 'copy',
    'msgDemoMap': 'copy',
    'mygroup': 'copy',
    'onCallClinicDates': 'archive',
    'oncall_questionnaire': 'archive',
    'oscarKeys': 'copy',
    'oscar_annotations': 'archive',
    'oscar_msg_type': 'reference',
    'oscarcommlocations': 'reference',
    'other_id': 'copy',
    'partial_date': 'copy',
    'patientLabRouting': 'copy',
    'pharmacyInfo': 'copy',
    'phr_actions': 'archive',
    'phr_document_ext': 'archive',
    'phr_documents': 'archive',
    'pmm_log': 'copy',
    'prescribe': 'copy',
    'prescription': 'copy',
    'preventions': 'copy',
    'preventionsBilling': 'archive',
    'preventionsExt': 'copy',
    'professionalSpecialists': 'copy',
    'program': 'copy',
    'programSignature': 'copy',
    'program_access': 'copy',
    'program_access_roles': 'copy',
    'program_client_restriction': 'copy',
    'program_clientstatus': 'copy',
    'program_functional_user': 'copy',
    'program_provider': 'copy',
    'program_provider_team': 'copy',
    'program_queue': 'copy',
    'program_team': 'copy',
    'property': 'merge',
    'provider': 'copy',
    'providerArchive': 'copy',
    'providerExt': 'copy',
    'providerLabRouting': 'copy',
    'providerLabRoutingFavorites': 'copy',
    'provider_default_program': 'copy',
    'provider_facility': 'copy',
    'providerbillcenter': 'copy',
    'providersite': 'copy',
    'providerstudy': 'copy',
    'publicKeys': 'copy',
    'queue': 'copy',
    'queue_document_link': 'copy',
    'quickList': 'merge',
    'quickListUser': 'copy',
    'radetail': 'copy',
    'raheader': 'copy',
    'recycle_bin': 'archive',
    'recyclebin': 'copy',
    'rehabStudy2004': 'copy',
    'relationships': 'copy',
    'remoteAttachments': 'copy',
    'report': 'copy',
    'reportByExamples': 'copy',
    'reportByExamplesFavorite': 'copy',
    'reportConfig': 'copy',
    'reportFilter': 'copy',
    'reportItem': 'copy',
    'reportTableFieldCaption': 'copy',
    'reportTemplates': 'copy',
    'report_date': 'copy',
    'report_date_sp': 'archive',
    'report_doctext': 'archive',
    'report_document': 'archive',
    'report_filter': 'archive',
    'report_letters': 'copy',
    'report_lk_reportgroup': 'archive',
    'report_option': 'archive',
    'report_qgviewfield': 'archive',
    'report_qgviewsummary': 'archive',
    'report_role': 'archive',
    'report_template': 'archive',
    'report_template_criteria': 'archive',
    'report_template_org': 'archive',
    'reportagesex': 'copy',
    'reportprovider': 'copy',
    'reporttemp': 'copy',
    'resident_oscarMsg': 'archive',
    'room': 'copy',
    'room_bed': 'archive',
    'room_bed_historical': 'archive',
    'room_demographic': 'archive',
    'room_type': 'archive',
    'rschedule': 'copy',
    'scheduledate': 'copy',
    'scheduleholiday': 'copy',
    'scheduletemplate': 'copy',
    'scheduletemplatecode': 'copy',
    'scratch_pad': 'copy',
    'secObjPrivilege': 'merge',
    'secObjectName': 'merge',
    'secPrivilege': 'reference',
    'secRole': 'copy',
    'secSite': 'archive',
    'secUserRole': 'copy',
    'security': 'copy',
    'serviceSpecialists': 'copy',
    'sharing_acl_definition': 'drop',
    'sharing_actor': 'drop',
    'sharing_affinity_domain': 'drop',
    'sharing_clinic_info': 'drop',
    'sharing_code_mapping': 'drop',
    'sharing_code_value': 'drop',
    'sharing_document_export': 'archive',
    'sharing_exported_doc': 'archive',
    'sharing_infrastructure': 'drop',
    'sharing_mapping_code': 'drop',
    'sharing_mapping_edoc': 'archive',
    'sharing_mapping_eform': 'archive',
    'sharing_mapping_misc': 'archive',
    'sharing_mapping_site': 'drop',
    'sharing_patient_document': 'archive',
    'sharing_patient_network': 'archive',
    'sharing_patient_policy_consent': 'archive',
    'sharing_policy_definition': 'drop',
    'sharing_value_set': 'drop',
    'site': 'copy',
    'site_role_mpg': 'archive',
    'specialistsJavascript': 'reference',
    'specialty': 'reference',
    'study': 'copy',
    'studydata': 'copy',
    'studylogin': 'copy',
    'survey': 'copy',
    'surveyData': 'copy',
    'survey_test_data': 'archive',
    'survey_test_instance': 'archive',
    'table_modification': 'copy',
    'tickler': 'copy',
    'tickler_category': 'merge',
    'tickler_comments': 'copy',
    'tickler_link': 'copy',
    'tickler_text_suggest': 'merge',
    'tickler_update': 'copy',
    'uploadfile_from': 'archive',
    'user_ds_message_prefs': 'copy',
    'vacancy': 'copy',
    'vacancy_client_match': 'copy',
    'vacancy_template': 'copy',
    'validations': 'merge',
    'view': 'copy',
    'waitingList': 'copy',
    'waitingListName': 'copy',
    'workflow': 'copy',
}
B3_FLAGGED_COLUMNS = {
    'document': {
        'fileSignature':
            "`fileSignature` IS NOT NULL AND `fileSignature` <> ''",
    },
    'drugs': {
        'dispensingUnits':
            "`dispensingUnits` IS NOT NULL AND `dispensingUnits` <> ''",
    },
}
CHARSET_SCAN = {
    'appointment': ['reason', 'notes', 'name'],
    'casemgmt_note': ['note'],
    'consultationRequests': ['reason', 'clinicalInfo'],
    'demographic': ['last_name', 'first_name', 'address', 'city'],
    'demographiccust': ['content'],
    'document': ['docdesc'],
    'drugs': ['special', 'comment'],
    'messagetbl': ['thesubject', 'themessage'],
    'tickler': ['message'],
}
DROPPED_PROP_PREFIXES = [
    'ldap.',
    'OLIS_',
    'olis_',
    'util.erx.',
    'born',
    'INTEGRATOR_',
    'MY_OSCAR',
    'MYOSCAR',
    'myoscar',
    'myOSCAR',
    'oscar_myoscar',
    'mymeds',
    'MYDRUGREF',
    'CBI_',
    'clinicaid',
    'indivica',
    'consultation_indivica',
    'eform_generator_indivica',
    'spire_',
    'redirectstudysite_',
    'sharingcenter',
    'cr_security',
    'RX3',
    'loginlogo',
    'logintext',
    'logintitle',
    'eaaps.',
    'health_tracker',
    'streethealth',
    'hsfo',
    'osp.',
    'vendor',
    'software',
]
DROPPED_PROP_KEYS = [
    'ALT_DISCHARGE_REASON',
    'AdtA09Handler.CHECK_IN_EARLY_ALLOWANCE',
    'AdtA09Handler.CHECK_IN_LATE_ALLOWANCE',
    'CASELOAD_DEFAULT_ALL_PROVIDERS',
    'DOCUMENT_DOWNLOAD_METHOD',
    'DOC_FORWARD',
    'EA_FORWORD',
    'HELP_SEARCH_URL',
    'HL7_A04_TRANSPORT_ADDR',
    'HL7_A04_TRANSPORT_PORT',
    'OMD_HRM_AUTH_KEY_FILENAME',
    'OMD_HRM_IP',
    'OMD_HRM_PORT',
    'OMD_HRM_REMOTE_DIR',
    'OMD_HRM_USER',
    'ORN_PILOT',
    'RA_FORWORD',
    'TA_FORWARD',
    'TESTING',
    'TRANSPORTATION_TIME_MANDATORY',
    'USE_CAISI_LOGO',
    'USE_NEW_ECHART',
    'VMSTAT_LOGGING_PERIOD',
    'admin.hph',
    'appt_intake_form',
    'casemgmt.note.password.enabled',
    'ckd_notification_scheme',
    'clientdropbox',
    'cobalt',
    'contact.required.program',
    'documentUploader.maxNumberOfFiles',
    'enable_create_child_record',
    'enable_rx_custom_methodone_suboxone',
    'enable_wait_list_email_notifications',
    'faxIdentifier',
    'faxKeystore',
    'faxLogo',
    'faxURI',
    'groupModuleEnabled',
    'hl7_a04_fail_dir',
    'l7_a04_sent_dir',
    'labreq_CKD',
    'ontariomd_cds_diabetes_link',
    'oscarMeasurement_css',
    'oscarMeasurement_css_download_method',
    'professionalContact.required.program',
    'professionalContact.required.workPhone',
    'rx.enable_internal_dispensing',
    'schedule.groupsFromPrograms',
    'wait_list_email_notification_period',
    'wait_list_email_notification_program_ids',
]
STOCK_ROLE_NAMES = [
    'CAISI ADMIN',
    'Case Manager',
    'Client Service Worker',
    'Clinical Assistant',
    'Clinical Case Manager',
    'Clinical Social Worker',
    'Counselling Intern',
    'Field Note Admin',
    'HRMAdmin',
    'Housing Worker',
    'Medical Secretary',
    'Nurse Manager',
    'Partner Doctor',
    'RN',
    'RPN',
    'Recreation Therapist',
    'Site Manager',
    'Support Counsellor',
    'Support Worker',
    'Vaccine Provider',
    'admin',
    'counsellor',
    'doctor',
    'er_clerk',
    'external',
    'locum',
    'moderator',
    'nurse',
    'property staff',
    'psychiatrist',
    'receptionist',
    'secretary',
    'student',
]
LEGACY_PREVENTION_TYPES = [
    'CHOLERA',
    'CTC',
    'DTaP-HBV-IPV-Hib',
    'Dukoral',
    'Flu',
    'H1N1',
    'HPV Vaccine',
    'HZV',
    'HepA',
    'HepA+B',
    'HepAB',
    'HepB',
    'Influenza',
    'MMRV',
    'Measles',
    'Men-B',
    'MenC-C',
    'Pneu',
    'Pneu-C-7',
    'Pneumococcus',
    'Pneumovax',
    'Poliovirus',
    'Rabies',
    'Rot',
    'Rotavirus',
    'TdP',
    'TdP-IPV',
    'Tetanus',
    'Typh',
    'Typhoid',
    'Typhoid-I',
    'VZ',
    'Varicella',
    'Zos',
    'Zostavax',
    'dTaP',
    'dTap',
    'fIPV',
]
# === END GENERATED DATA ===

BLOCKER = "blocker"
ADVISORY = "advisory"
INFO = "info"
#: the blocker classes an assessment can acknowledge (a subset of the
#: import verb's --accept classes; a typo must not read as "accepted")
ACCEPT_IDS = ("archived-forms", "unknown-as-archive", "olis-gone",
              "dropped-columns", "carry-credentials", "charset-repair")
#: copy-class tables whose rows are live credentials (OAuth consumer
#: secrets, signing keys); mirrors o19map_schema.CREDENTIAL_TABLES
CREDENTIAL_TABLES = ("ServiceClient", "oscarKeys", "publicKeys")

# exit code for "the check itself failed" — distinct from every verdict
EXIT_TOOL_ERROR = 3

#: findings that mean "a P4 refusal was NOT measured here". They are not
#: blockers -- nothing was found -- but nothing was LOOKED for either,
#: and every class behind them refuses at P4 with no --accept, so
#: render_text repeats them beside the bundling recipe: a `go` above
#: that recipe otherwise reads as a green light for the whole import.
UNMEASURED_FINDING_IDS = ("column-checks-deferred", "charset-b8-unmeasured")

#: the finding whose data are view names the bundling recipe must
#: exclude. render_text appends its --ignore-table flags to the
#: mysqldump line, so the recipe an operator copies is one that works on
#: THIS schema rather than the generic one that would fail at P1.
VIEWS_FINDING = "views-block-the-restore"

# Core-table inventory reported as sanity anchors for later row-parity.
INVENTORY_TABLES = [
    "demographic", "provider", "security", "appointment", "casemgmt_note",
    "document", "drugs", "hl7TextMessage", "eform_data", "preventions",
    "billing_on_cheader1", "tickler",
]

# UTF-8 bytes of a double-encoded latin1 lead byte ("A-tilde" = C3 83):
# their presence in narrative text is the classic OSCAR mojibake signature.
MOJIBAKE_HEX = "C383"

#: one repair hop, exactly as the ETL applies it per row (a deliberate
#: copy of o19etl.REPAIR_TEMPLATE, pinned by
#: tests/test_sql_escape_contract.py). The B8 stop asks whether the
#: repair is a FIXED POINT: text that still looks double-encoded after
#: one hop was encoded more than twice and cannot be repaired here.
REPAIR_TEMPLATE = "CONVERT(BINARY CONVERT({0} USING latin1) USING utf8mb4)"
#: the mojibake byte signature (o19etl.MOJIBAKE_MARKER_RE). It needs a
#: regex engine that understands \x{} escapes: the Spencer engine of
#: MySQL < 8 / MariaDB < 10.0.5 reads it as a literal bracket
#: expression, so it is only used behind _hex_escape_regexp_supported().
MOJIBAKE_MARKER_RE = "'[\\\\x{C3}\\\\x{C2}][\\\\x{80}-\\\\x{BF}]'"
#: proves the engine really understands the escape: a latin1 'Ã©' must
#: match and plain ASCII must not. Spencer answers 0 to both (or errors
#: on the reversed range), which is what makes the two-sided test
#: necessary -- a one-sided probe cannot tell "no match" from "no engine"
MOJIBAKE_MARKER_PROBE = (
    "SELECT CONVERT(0xC3A9 USING latin1) REGEXP {0}, 'ab' REGEXP {0}"
    .format(MOJIBAKE_MARKER_RE))


def finding(fid, severity, title, detail="", accept=None, data=None):
    """One report entry. `accept` names the `--accept` class that clears a
    blocker (omitted when nothing can); `data` carries the structured
    detail the JSON report keeps and the text rendering summarises."""
    f = {"id": fid, "severity": severity, "title": title, "detail": detail}
    if accept:
        f["accept"] = accept
    if data is not None:
        f["data"] = data
    return f


_UNESCAPE = {"n": "\n", "t": "\t", "r": "\r", "f": "\f"}


def _unescape_property(text):
    r"""Undo `java.util.Properties` value escapes (`\n`, `\t`, `\r`,
    `\f`, `\uXXXX`, and `\x` -> `x` for anything else).

    A malformed `\u` escape is a file `java.util.Properties` rejects
    outright, so it is not silently passed through."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "\\" and i + 1 < n:
            nxt = text[i + 1]
            if nxt == "u":
                if not re.match(r"[0-9A-Fa-f]{4}$", text[i + 2:i + 6]):
                    # java.util.Properties rejects this file outright
                    raise ValueError("malformed \\uXXXX escape in "
                                     "properties text")
                out.append(chr(int(text[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(_UNESCAPE.get(nxt, nxt))
            i += 2
            continue
        out.append(c)
        i += 1
    return _join_surrogates("".join(out))


def _join_surrogates(text):
    """Two adjacent \\uXXXX escapes forming a UTF-16 pair decode to one
    character (the rule java.util.Properties applies); an unpaired
    surrogate is kept (mirrors o19props._join_surrogates)."""
    if not any(0xD800 <= ord(c) <= 0xDFFF for c in text):
        return text
    out = []
    i = 0
    while i < len(text):
        cp = ord(text[i])
        if (0xD800 <= cp <= 0xDBFF and i + 1 < len(text)
                and 0xDC00 <= ord(text[i + 1]) <= 0xDFFF):
            out.append(chr(0x10000 + ((cp - 0xD800) << 10)
                           + ord(text[i + 1]) - 0xDC00))
            i += 2
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def parse_properties_text(text):
    """Active key/value pairs with java.util.Properties semantics ('=', ':'
    or whitespace separators, backslash line continuation, escapes,
    trailing whitespace preserved); last occurrence wins. Mirrors
    o19props.parse_properties_text (this file must stay standalone)."""
    props = {}
    logical = []
    # java.util.Properties ends a line at \n, \r or \r\n only (never at
    # \f, \x85 or the other characters str.splitlines() honours) and
    # strips only space, tab and form feed
    physical = re.split(r"\r\n|\r|\n", text)
    # a continuation backslash on the last physical line still yields the
    # record (Java drops the backslash and keeps the pair)
    physical.append("")
    for raw in physical:
        line = raw.lstrip(" \t\f")
        if not logical and (not line or line[0] in ("#", "!")):
            continue
        # an odd run of trailing backslashes: the LAST one continues the
        # line, the rest are escaped backslashes that stay in the value
        if (len(line) - len(line.rstrip("\\"))) % 2 == 1:
            logical.append(line[:-1])
            continue
        logical.append(line)
        full = "".join(logical)
        logical = []
        j = 0
        key_chars = []
        while j < len(full):
            c = full[j]
            if c == "\\" and j + 1 < len(full):
                key_chars.append(full[j:j + 2])
                j += 2
                continue
            if c in "=: \t\f":
                break
            key_chars.append(c)
            j += 1
        key = _unescape_property("".join(key_chars))
        if not key:
            continue
        rest = full[j:].lstrip(" \t\f")
        if rest[:1] in ("=", ":"):
            rest = rest[1:].lstrip(" \t\f")
        props[key] = _unescape_property(rest)
    return props


def parse_properties(path):
    """Active key=value pairs of a java .properties file (last wins)."""
    with open(path, "rb") as fh:
        text = fh.read().decode("latin-1")
    return parse_properties_text(text)


def double_encoded_predicate(col):
    """Row predicate: `col` holds UTF-8 text that was itself stored through
    a latin1 hop (mojibake such as 'Ã©' for 'é'). Byte-ALIGNED and
    lossless by construction: the value must round-trip down to latin1
    unchanged (every char representable), those latin1 bytes must form
    valid UTF-8 (converting them back to bytes loses nothing), and the
    value must contain a non-ASCII character at all. A substring match on
    a hex dump ('%C383%') is NOT aligned and flags innocent text such as
    '1,800' — which is why this predicate exists."""
    return double_encoded_expr("`{0}`".format(col))


def double_encoded_expr(c):
    """The same test over an already-quoted SQL EXPRESSION.

    Split out from `double_encoded_predicate` because the thrice-encoded
    stop has to apply the test to the value AFTER one repair hop, which
    is an expression and not a column."""
    # normalise to utf8mb4 first: O19 tables are usually latin1, and a
    # BINARY comparison across charsets compares different byte strings
    u = "CONVERT({0} USING utf8mb4)".format(c)
    down = "CONVERT({0} USING latin1)".format(u)
    # "contains a non-ASCII character" as a byte-vs-character length test:
    # every server evaluates it identically, unlike a REGEXP class with
    # \x escapes, which the Spencer engine of MySQL < 8 / MariaDB < 10.0.5
    # (the assessment hosts) reads as a literal bracket expression
    return ("{0} IS NOT NULL AND LENGTH({1}) <> CHAR_LENGTH({1}) AND "
            "BINARY CONVERT({2} USING utf8mb4) = BINARY {1} AND "
            "BINARY CONVERT(CONVERT(BINARY {2} USING utf8mb4) USING binary) "
            "= BINARY {2}".format(c, u, down))


def _ident(name):
    """Backtick-quote an identifier so EVERY table name the dump carries
    can be counted — a vendor-fork table with an unusual name must be
    checked, never filtered out of the unknown-table blocker."""
    return "`" + name.replace("`", "``") + "`"


INTERACTIVE_PASSWORD_ARGS = ("-p", "--password")


def password_arg_problem(mysql_args):
    """A client argument that carries or prompts for the password, if any.

    A bare -p / --password would PROMPT once per query (every check is a
    fresh client process); an attached -pSECRET / --password=SECRET puts
    the credential in the process list and in any diagnostic that echoes
    argv. The password must come from --mysql-password-file (MYSQL_PWD)
    or a client defaults file. The offending VALUE is never returned —
    only its shape — so it cannot leak through the refusal message."""
    for a in mysql_args:
        if a in INTERACTIVE_PASSWORD_ARGS:
            return "'{0}' (interactive prompt)".format(a)
        if a.startswith("--password="):
            return "'--password=...' (password in argv)"
        if a.startswith("-p") and not a.startswith("--"):
            return "'-p...' (password in argv)"
    return None


def interactive_password_arg(mysql_args):
    """Back-compat name: the bare prompting form, if present."""
    for a in mysql_args:
        if a in INTERACTIVE_PASSWORD_ARGS:
            return a
    return None


def make_cli_query(mysql_cmd, mysql_args, db, env=None):
    """Return query(sql) -> list of rows (lists of strings) via the CLI.
    env, when given, replaces the client's environment (used to hand the
    password over as MYSQL_PWD instead of argv). Statements travel via
    stdin; a failure raises RuntimeError carrying the CLIENT'S stderr
    (never the argv, which may hold credentials)."""
    def query(sql):
        argv = [mysql_cmd] + list(mysql_args) + \
            ["--default-character-set=utf8mb4", "-N", "-B", db]
        proc = subprocess.Popen(argv, stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=env)
        out, err = proc.communicate(sql.encode("utf-8"))
        if proc.returncode != 0:
            message = err.decode("utf-8", "replace").strip()
            raise RuntimeError(message.splitlines()[-1] if message
                               else "client exited {0}".format(
                                   proc.returncode))
        text = out.decode("utf-8", "replace")
        # batch output escapes \0 \t \n \\ in values; only "\n" ends a
        # row, and each value is decoded after the split (an indicator
        # template's line breaks must not glue the escape letter onto the
        # next word)
        lines = text.split("\n")
        if lines and lines[-1] == "":
            lines.pop()
        return [[_unescape_batch(v) for v in line.split("\t")]
                for line in lines]
    return query


_BATCH_ESCAPES = {"0": "\0", "t": "\t", "n": "\n", "\\": "\\"}


def _unescape_batch(value):
    """Undo the client's batch-mode escaping of one value. (SQL NULL is
    printed as the four letters NULL, like a stored string 'NULL'; the
    checks here only count and list NOT NULL columns, so nothing needs
    to tell the two apart.)"""
    if "\\" not in value:
        return value
    out = []
    i = 0
    n = len(value)
    while i < n:
        c = value[i]
        if c == "\\" and i + 1 < n and value[i + 1] in _BATCH_ESCAPES:
            out.append(_BATCH_ESCAPES[value[i + 1]])
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def _sql_literal(value):
    """Escape a string for a single-quoted SQL literal.

    A deliberate copy of carlos_ctl.util.sql_escape: this file is carried
    alone to a 2014-era OSCAR 19 server and may import nothing from the
    package. The two are pinned against each other by
    tests/test_sql_escape_contract.py, because this copy had already lost
    the NUL case while the other three kept it -- reachable only from
    manifest constants today, but the next caller to reach for it with a
    clinic value would have got the weaker escape.

    CR is escaped for the transport, not for the server: statements go to
    the mysql CLI on stdin, which strips the CR of a CRLF as a line
    terminator before the server parses them -- inside a quoted literal
    too (measured on MariaDB 10.11: `'a\r\nb'` stored as `a\nb`)."""
    return (value.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\0", "\\0").replace("\r", "\\r"))


def _like_prefix(value):
    """A LIKE pattern matching values that start with `value` literally."""
    return _sql_literal(value).replace("_", "\\_").replace("%", "\\%") + "%"


def _chunks(seq, size):
    """`seq` in slices of at most `size`. IN () lists go to the batch
    client as one command line, so the long exact-key list is split."""
    return [seq[i:i + size] for i in range(0, len(seq), size)]


_WORD_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def dropped_table_references(text, dropped_tables):
    """Table names of removed modules that a free-text SQL/XML template
    mentions as whole words (dashboard indicators keep SQL in XML)."""
    if not text:
        return []
    # case-insensitive: the O19 host may have run with
    # lower_case_table_names=1, so a template may spell a table either way
    words = set(w.lower() for w in _WORD_RE.findall(text))
    return sorted(t for t in dropped_tables if t.lower() in words)


#: the two server errors that mean "this table or column is not at this
#: patch level": 1146 unknown table, 1054 unknown column.
_ABSENT_OBJECT_CODES = ("1054", "1146")
_ERROR_CODE_RE = re.compile(r"ERROR\s+(\d+)")
#: the English texts, for a client that reports no numeric code. NOT a
#: bare "doesn't exist": MariaDB's 1932 reads "doesn't exist in engine"
#: and means a CORRUPT table, which must stay a hard no-go rather than
#: be waved through as patch-level variance.
_ABSENT_OBJECT_TEXT_RE = re.compile(
    r"Unknown column|doesn't exist(?!\s+in\s+engine)", re.I)


def _absent_object(text):
    """True when a failed query names a table or column the dump does not
    carry. The importer anticipates patch-level variance -- a lower patch
    level simply lacks columns the manifest's superset knows, and
    o19etl.effective_entry skips them WITH a report line -- so this must
    not be reported as a failed check; everything else must be, so the
    numeric code decides whenever the client gave one.

    An absent column is not automatically harmless, though: a few of them
    ARE no-flag P4 refusals (Facility.disabled, a merge key, a NOT NULL
    target column without a default). Callers that know they are asking
    about one of those must raise their own blocker rather than file the
    error here -- see check_facility_and_clinic."""
    text = text or ""
    code = _ERROR_CODE_RE.search(text)
    if code:
        return code.group(1) in _ABSENT_OBJECT_CODES
    return bool(_ABSENT_OBJECT_TEXT_RE.search(text))


#: the identifier class o19etl.IDENTIFIER_RE accepts; a name outside it
#: is refused before the first write, with no --accept
IDENTIFIER_RE = re.compile(r"^[A-Za-z0-9_$]+$")


def _count(query, table, where=None):
    """Row count, or an ("error", message) tuple when the count could not
    be taken (missing privilege, odd storage engine, ...). Callers route
    the tuple into the query-errors blocker: an unreadable table can never
    be reported as empty."""
    sql = "SELECT COUNT(*) FROM {0}".format(_ident(table))
    if where:
        sql += " WHERE {0}".format(where)
    try:
        rows = query(sql)
        return int(rows[0][0])
    except Exception as exc:  # table missing, permission, ...
        text = str(exc).strip()
        return ("error", text.splitlines()[-1] if text else "")


# --- content digests (a deliberate copy of carlos_ctl.o19digest) ---------
#
# The clinic's live database is the ONLY place the pre-dump content can be
# measured, and this file is the only tool that runs there. It may import
# nothing from the package (see the module docstring), so the digest SQL
# exists twice; the two are pinned against each other by
# tests/test_sql_escape_contract.py, the same way `_sql_literal` and the
# property parser are.
#
# What the numbers are for: `carlos-ctl import-o19` compares them against
# the restored staging schema at P2, which is the only check that can say
# the dump, the transfer and the restore carried every VALUE rather than
# merely the right NUMBER of rows.
#
# Every part of the encoding is load-bearing; the reasoning (CONCAT_WS
# skipping NULLs, a forgeable marker, BIT_XOR blind to a deleted identical
# pair, SUM going inexact past 2^53) is written out once, in o19digest.py.

#: field separator inside a row's hashed form
DIGEST_SEP = "0x1f"
#: what a NULL contributes; it cannot collide with a value, which always
#: starts with a digit (the length prefix)
DIGEST_NULL_MARK = "'~'"
#: column types whose bytes are not text and must be HEXed. CONVERT is
#: not injective over them: measured on MariaDB 10.11, two different BIT
#: values (0xC3, 0xAA) both render as `?`, so a change between them would
#: be invisible to the digest
DIGEST_HEXED_TYPES = (
    "blob", "tinyblob", "mediumblob", "longblob",
    "binary", "varbinary", "bit",
    "geometry", "point", "linestring", "polygon",
    "multipoint", "multilinestring", "multipolygon",
    "geometrycollection",
)
#: types with ONE unambiguous string rendering, which CONVERT produces.
#: Numbers must not be HEXed instead: HEX() treats a numeric argument as
#: a longlong, so HEX(1.4) is '1' and HEX(1.5) is '2'
DIGEST_CONVERTED_TYPES = (
    "char", "varchar", "tinytext", "text", "mediumtext", "longtext",
    "enum", "set", "json",
    "tinyint", "smallint", "mediumint", "int", "integer", "bigint",
    "decimal", "numeric", "float", "double", "real",
    "date", "time", "datetime", "timestamp", "year",
    "inet4", "inet6", "uuid",
)
#: prelude every digest statement carries: a TIMESTAMP is stored as UTC
#: and RENDERED in the session's time zone (measured on MariaDB 10.11,
#: one instant reads 12:00:00 at +00:00 and 17:30:00 at +05:30), and the
#: clinic's server and the CARLOS host keep different local time. Without
#: this every table with a TIMESTAMP would disagree on a faithful
#: transfer. DATETIME is unaffected; the setting is unconditional anyway
#: so the two sides cannot differ about when it applies.
DIGEST_UTC_SESSION = "SET time_zone = '+00:00'"
#: types whose ONE value can reach megabytes (TEXT/BLOB past 64 KB, and
#: JSON, a LONGTEXT underneath). CONCAT returns NULL, not an error, when
#: its result would exceed the server's max_allowed_packet -- measured:
#: an 8.4 MB document HEXes to 16.8 MB and under the stock 16M setting
#: the length-prefixed CONCAT was NULL, filed as a NULL by the IFNULL,
#: and the table digested differently under 16M and 1G. So a large value
#: is hashed on its own first and only its 64 characters are joined.
DIGEST_LARGE_TYPES = (
    "text", "mediumtext", "longtext", "json",
    "blob", "mediumblob", "longblob",
)
#: version of the digest document this file emits. 2: large values
#: hashed on their own, NULL-propagating row join, the `unhashed` lane
DIGEST_FORMAT = 2


def digest_value_expr(col, coltype):
    """The normalised, unambiguous contribution of one column.

    `coltype` is the information_schema DATA_TYPE. The clinic stores
    latin1 and live CARLOS is utf8mb4, so text is converted before
    hashing or the two sides would disagree on every accented row.

    An unrecognised type raises ValueError and the table is reported as
    unmeasured, rather than guessed at: neither rendering is safe for the
    other's types, and the wrong one gives a digest that AGREES while the
    data differs."""
    quoted = "`{0}`".format(col.replace("`", "``"))
    normalised = (coltype or "").lower()
    if normalised in DIGEST_HEXED_TYPES:
        rendered = "HEX({0})".format(quoted)
    elif normalised in DIGEST_CONVERTED_TYPES:
        rendered = "CONVERT({0} USING utf8mb4)".format(quoted)
    else:
        raise ValueError(
            "column `{0}` has type {1!r}, which the digest has no "
            "rendering for; neither HEX nor CONVERT is safe for an "
            "unknown type".format(col, coltype))
    if normalised in DIGEST_LARGE_TYPES:
        # hashed on its own so the CONCAT joins a length and 64 hex
        # characters, never the megabytes the value may be
        return ("IFNULL(CONCAT(CHAR_LENGTH({0}), ':', SHA2({0}, 256)), "
                "{1})".format(rendered, DIGEST_NULL_MARK))
    return ("IFNULL(CONCAT(CHAR_LENGTH({0}), ':', {0}), {1})"
            .format(rendered, DIGEST_NULL_MARK))


def digest_row_hash_expr(columns, types):
    """SHA-256 of one row over `columns`, in the order given.

    CONCAT, not CONCAT_WS: every piece is non-NULL (the IFNULL above), so
    the only NULL that can reach the join is a piece the server refused
    to render, and it must make the whole row hash NULL -- which the
    fourth lane counts -- rather than be dropped from the row."""
    if not columns:
        raise ValueError("a row hash needs at least one column")
    parts = (", " + DIGEST_SEP + ", ").join(
        digest_value_expr(c, types.get(c, "")) for c in columns)
    return "SHA2(CONCAT({0}), 256)".format(parts)


def digest_sql(schema, table, columns, types, where=None):
    """Two statements: the UTC prelude, then `SELECT rows, total,
    parity, unhashed` for one table. The fourth lane counts rows whose
    hash is NULL: SUM and BIT_XOR both ignore a NULL, so without it a
    row the server would not render vanishes from the digest -- the
    same way on both sides, which would read as agreement.

    The prelude is part of the result, not decoration: every query runs
    in its own client process, so the session time zone has to be pinned
    in the same batch or a TIMESTAMP renders in whatever local time the
    clinic's host keeps. Callers must not strip or reorder it; the batch
    returns one row, since `SET` produces no result set.

    `schema` may be None, which leaves the table unqualified: the clinic
    runs one `mysql <db>` per query and has no second schema in reach."""
    h = digest_row_hash_expr(columns, types)
    ident = "`{0}`".format(table.replace("`", "``"))
    if schema is not None:
        ident = "`{0}`.{1}".format(schema.replace("`", "``"), ident)
    clause = " WHERE {0}".format(where) if where else ""
    return (
        DIGEST_UTC_SESSION + ";\n"
        "SELECT COUNT(*), "
        "IFNULL(SUM(CAST(CONV(SUBSTR({h}, 1, 16), 16, 10) "
        "AS DECIMAL(30, 0))), 0), "
        "IFNULL(BIT_XOR(CONV(SUBSTR({h}, 17, 16), 16, 10)), 0), "
        "IFNULL(SUM({h} IS NULL), 0) "
        "FROM {t}{w}".format(h=h, t=ident, w=clause))


def digest_entry(columns, rows, total, parity, unhashed):
    """One table's entry in the digest document.

    The column list travels WITH the numbers because the other side must
    hash the same columns in the same order, and must be able to say so
    when it cannot. `total` and `parity` are strings: the SUM lane is a
    DECIMAL(30, 0) and outruns a 64-bit integer, which several JSON
    readers round without saying so."""
    return {"columns": [[c, t] for c, t in columns],
            "rows": int(rows),
            "total": str(total),
            "parity": str(parity),
            "unhashed": int(unhashed)}


def column_types(query, schema_expr):
    """`{table: [(column, DATA_TYPE), ...]}` in ORDINAL_POSITION order.

    One query for the whole schema rather than one per table: a clinic
    carries ~580 of them and each query here is a fresh client process."""
    out = {}
    for row in query(
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE "
            "FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA = {0} "
            "ORDER BY TABLE_NAME, ORDINAL_POSITION".format(schema_expr)):
        if len(row) >= 3 and row[0]:
            out.setdefault(row[0], []).append((row[1], row[2]))
    return out


def collect_digests(query, schema_expr, table_names, province="on",
                    db_name=None):
    """Digest every named table; return the document the bundle carries.

    A table that cannot be read is recorded in `errors` and left OUT of
    `tables`. That is the fail-closed half: the import treats a table it
    has no clinic digest for as UNVERIFIED and says so, rather than
    quietly reporting a clean transfer for the one table nobody could
    measure."""
    types_by_table = column_types(query, schema_expr)
    tables = {}
    errors = {}
    for name in sorted(table_names):
        spec = types_by_table.get(name)
        if not spec:
            # a table information_schema does not describe cannot be
            # hashed; it is almost always a permission boundary
            errors[name] = "information_schema reports no columns"
            continue
        cols = [c for c, _t in spec]
        try:
            # ValueError here is the type refusal, raised while BUILDING
            # the statement: a column whose type has no safe rendering
            # makes the whole table unmeasurable, never partly measured
            rows = query(digest_sql(None, name, cols, dict(spec)))
        except Exception as exc:                      # noqa: BLE001
            text = str(exc).strip()
            errors[name] = text.splitlines()[-1] if text else "query failed"
            continue
        if not rows or len(rows[0]) < 4:
            errors[name] = "digest query returned no row"
            continue
        try:
            entry = digest_entry(spec, int(rows[0][0] or 0),
                                 int(rows[0][1] or 0), int(rows[0][2] or 0),
                                 int(rows[0][3] or 0))
        except (TypeError, ValueError) as exc:
            # a server that answers a digest with something other than
            # four integers has not been measured; recording the table
            # as digested with zeros would read as "empty and verified"
            errors[name] = "digest query returned {0!r} ({1})".format(
                rows[0][:4], exc)
            continue
        tables[name] = entry
    return {
        "digest_format": DIGEST_FORMAT,
        "schema_map_version": SCHEMA_MAP_VERSION,
        "generated_at": datetime.datetime.now().isoformat(),
        "province": province,
        "database": db_name or "",
        "tables": tables,
        "errors": errors,
    }


class PreflightState(object):
    """Everything the check groups below share.

    ``count`` and ``count_live`` are CLOSURES that fail CLOSED: a failed
    count is recorded in ``query_errors`` (a hard no-go) or, when it names
    an object this patch level does not carry, in ``absent_objects`` (an
    INFO line), and returns 0 only so the table is not double-reported.
    They must be the SAME two closures for every group -- rebuilding them
    per group would split those two dicts and quietly change the verdict.

    ``tables`` maps the manifest spelling of a table to its live spelling
    and ``live_to_manifest`` is its inverse; both matter on a server
    running lower_case_table_names=1.
    """

    def __init__(self, query, count, count_live, findings, accepted,
                 query_errors, absent_objects, schema_expr, tables,
                 live_names, live_to_manifest, known_live_lower):
        self.query = query
        self.count = count
        self.count_live = count_live
        self.findings = findings
        self.accepted = accepted
        self.query_errors = query_errors
        self.absent_objects = absent_objects
        self.schema_expr = schema_expr
        self.tables = tables
        self.live_names = live_names
        self.live_to_manifest = live_to_manifest
        self.known_live_lower = known_live_lower


def base_table_names(query, schema_expr):
    """Every BASE TABLE in the schema, as the server spells them.

    Views are excluded deliberately: they carry no rows of their own, so
    counting or digesting one would double-report the table underneath."""
    names = []
    for row in query(
            "SELECT TABLE_NAME FROM information_schema.TABLES "
            "WHERE TABLE_SCHEMA = {0} AND TABLE_TYPE = 'BASE TABLE'"
            .format(schema_expr)):
        if row and row[0]:
            names.append(row[0])
    return names


def live_table_inventory(query, findings, db_name):
    """Introspect the live schema; return what every later check reads.

    Returns ``(schema_expr, tables, live_names, live_to_manifest,
    known_live_lower)``. An exact-spelling match against the manifest
    always wins; case folding only stands in where no exact match exists,
    so a case-sensitive server holding a vendor twin reports a blocker
    rather than silently mistaking one table for the other.
    """
    # --- live table inventory --------------------------------------------
    if db_name:
        schema_expr = "'{0}'".format(db_name)
    else:
        schema_expr = "DATABASE()"
    # MySQL table names are case-insensitive on servers running
    # lower_case_table_names=1 (information_schema then reports them in
    # lower case), so every lookup against the manifest folds case and
    # `tables` maps the manifest spelling to the live spelling
    known_lower = dict((t.lower(), t) for t in KNOWN_TABLES)
    live_names = base_table_names(query, schema_expr)
    # an exact-spelling match always wins; case folding only stands in
    # when no exact match exists (lower_case_table_names=1 servers). On a
    # case-sensitive server a vendor table that differs from a manifest
    # table only by case would otherwise be mistaken for it: such twins
    # are reported as a blocker and the odd one flows to the unknown path
    tables = {}
    case_collisions = {}
    for live in sorted(live_names, key=lambda n: (n not in KNOWN_TABLES, n)):
        if live in KNOWN_TABLES:
            manifest = live
        else:
            manifest = known_lower.get(live.lower(), live)
        if manifest in tables and tables[manifest] != live:
            case_collisions.setdefault(manifest, []).append(live)
            tables[live] = live
            continue
        tables[manifest] = live
    live_to_manifest = dict((live, manifest)
                            for manifest, live in tables.items())
    known_live_lower = set(n.lower() for n in live_names)
    if case_collisions:
        findings.append(finding(
            "case-colliding-tables", BLOCKER,
            "{0} table name(s) differ from a manifest table only by case"
            .format(len(case_collisions)),
            "The server is case-sensitive and holds twins the importer "
            "cannot tell apart; rename or drop the vendor twin before "
            "shipping the dump. No --accept flag exists for this.",
            data=case_collisions))
    return (schema_expr, tables, live_names, live_to_manifest,
            known_live_lower)


_SCHEMA_WIDE_GRANT_RE = re.compile(
    r"^GRANT\s+(?P<privs>.+?)\s+ON\s+(?P<obj>\S+)\s+TO\s", re.I)


def _grant_covers_schema(line, db_lower):
    """True when one `SHOW GRANTS` line gives SELECT over the WHOLE schema.

    Only `ON *.*` and `` ON `db`.* `` prove the account can see every
    table; a table-scoped grant proves the opposite. Role-grant lines
    (`GRANT `role` TO `user``) carry no ON clause and match nothing."""
    m = _SCHEMA_WIDE_GRANT_RE.match(line.strip())
    if not m:
        return False
    privs = m.group("privs").upper()
    if "SELECT" not in privs and "ALL PRIVILEGES" not in privs:
        return False
    obj = m.group("obj").replace("`", "").rstrip(",")
    if obj == "*.*":
        return True
    return (obj.lower() == db_lower + ".*") if db_lower else False


def check_account_reach(c):
    """Whether the assessment account can SEE the whole schema at all.

    Every other check begins `if t not in tables: continue`, and
    `tables` comes from information_schema.TABLES -- which MySQL and
    MariaDB filter BY PRIVILEGE. A table the account holds no privilege
    on is not "readable but empty" (that case fails closed into
    query-errors); it is simply absent from the listing, so B1, B2, B9,
    the archive/drop counts and the charset scan all skip it without a
    word. Measured: the same database gave `go-with-acknowledgements`
    with three blockers as root and a clean `go` under an account
    granted SELECT on fifteen of its eighteen tables.

    The dump is then taken as root (render_text's own next-step block
    says so), so the bundle carries the tables the assessment never saw
    and the import raises their blockers at P2 -- against a clinic
    sign-off that covers none of them. There is no way to enumerate what
    was hidden, so the honest report is that the reach could not be
    proved: a no-go with a remedy, never a `go` over an unknown."""
    query = c.query
    findings = c.findings
    db_lower = (c.schema_expr or "").strip("'").lower()
    if c.schema_expr == "DATABASE()":
        rows = None
        try:
            rows = query("SELECT DATABASE()")
        except Exception:                                  # noqa: BLE001
            rows = None
        db_lower = (rows[0][0] or "").lower() if rows and rows[0] else ""
    try:
        grants = [r[0] for r in query("SHOW GRANTS FOR CURRENT_USER()")
                  if r and r[0]]
    except Exception as exc:                               # noqa: BLE001
        text = str(exc).strip()
        findings.append(finding(
            "account-reach-unknown", BLOCKER,
            "cannot tell whether this account sees the whole schema",
            "SHOW GRANTS failed, so the table listing every check above "
            "reads may be privilege-filtered: a table this account "
            "cannot see produces no finding at all, which is "
            "indistinguishable from a table that does not exist. Re-run "
            "as an account with SELECT on the whole schema (the "
            "documented -uroot). No --accept flag exists for this.",
            data={"SHOW GRANTS": text.splitlines()[-1] if text else ""}))
        return
    if any(_grant_covers_schema(g, db_lower) for g in grants):
        return
    findings.append(finding(
        "account-reach-partial", BLOCKER,
        "this account holds no schema-wide SELECT, so the table listing "
        "may be incomplete",
        "information_schema.TABLES is filtered by privilege: a table "
        "this account cannot see is absent from the listing, so B1, B2, "
        "B9, the archive/drop counts and the charset scan skip it "
        "silently -- a clean `go` over tables nobody looked at. The dump "
        "is taken as root and carries them anyway. Re-run as an account "
        "with SELECT on the whole schema (the documented -uroot). No "
        "--accept flag exists for this.",
        data={"tables visible to this account": len(c.live_names)}))


def check_carried_credentials(c):
    """B9: credentials the copy carries verbatim into CARLOS.

    Tokens issued by the OSCAR 19 install keep working after cutover, so
    carrying them is a sign-off, not a default."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    # --- B9: live credentials the copy carries verbatim ------------------
    carried = {}
    for t in CREDENTIAL_TABLES:
        if t in tables:
            n = count_live(t)
            if n:
                carried[t] = n
    if carried:
        findings.append(finding(
            "B9-credentials-carried", BLOCKER,
            "{0} credential table(s) carry live secrets ({1} row(s))".format(
                len(carried), sum(carried.values())),
            "OAuth consumer secrets and signing keys are copied verbatim and "
            "keep working against the migrated system. Acknowledge with "
            "--accept carry-credentials and rotate/verify them before "
            "go-live.", accept="carry-credentials", data=carried))


def check_locked_notes(c):
    """B4: password-protected (encrypted) casemgmt notes.

    Returns the row count, which check_properties needs: the stock O19
    default casemgmt.note.password.enabled=true says nothing on its
    own, so the property is only worth reporting when NO note is
    actually locked.
    """
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    # --- B4: password-protected (encrypted) casemgmt notes ---------------
    # judged on the data: a note with a password is stored encrypted and
    # the key handling is outside the standard import path
    locked_notes = 0
    if "casemgmt_note" in tables:
        locked_notes = count_live(
            "casemgmt_note", "password IS NOT NULL AND password <> ''")
    if locked_notes:
        findings.append(finding(
            "B4-encrypted-notes", BLOCKER,
            "{0} password-protected (encrypted) casemgmt note(s)"
            .format(locked_notes),
            "Key handling for encrypted notes is outside the standard "
            "import path. No --accept flag exists for this."))
    return locked_notes


def check_unknown_tables(c):
    """B2: tables the manifest has never heard of, and their row counts."""
    count = c.count
    findings = c.findings
    tables = c.tables
    # --- B2: tables the manifest does not know ---------------------------
    unknown = sorted(t for t in tables if t not in KNOWN_TABLES)
    unknown_with_rows = {}
    for t in unknown:
        n = count(t)
        if n == 0:
            continue
        unknown_with_rows[t] = n
    if unknown_with_rows:
        findings.append(finding(
            "B2-unknown-tables", BLOCKER,
            "{0} table(s) not classified by manifest {1}"
            .format(len(unknown_with_rows), SCHEMA_MAP_VERSION),
            "Vendor-fork or unpatched-legacy tables holding rows. They are "
            "never migrated silently: classify them in the manifest, or "
            "accept archive-by-default.",
            accept="unknown-as-archive", data=unknown_with_rows))


def check_patient_data_in_absent_tables(c):
    """B1: patient data in tables CARLOS does not have."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    # --- B1: patient data in tables CARLOS does not have ------------------
    patient_rows = {}
    for t in PATIENT_DATA_TABLES:
        if t not in tables:
            continue
        n = count_live(t)
        if n > 0:
            patient_rows[t] = n
    if patient_rows:
        findings.append(finding(
            "B1-patient-data", BLOCKER,
            "patient data in {0} table(s) that do not exist in CARLOS"
            .format(len(patient_rows)),
            "These records become ARCHIVE-ONLY after migration (o19_archive "
            "schema + CSV export) - the forms/modules were removed from "
            "CARLOS. The clinic must sign this off.",
            accept="archived-forms", data=patient_rows))


def check_preserved_class_rows(c):
    """Row counts for the archive-, drop- and OLIS-class tables.

    Advisory rather than blocking: none of these rows is lost -- they are
    preserved in o19_archive and as import_archived_ twins -- but the
    operator should know what will not be live afterwards."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    # --- archive-class config/log tables with rows (advisory) -------------
    archive_rows = {}
    olis_rows = {}
    drop_rows = {}
    for t, cls in KNOWN_TABLES.items():
        if t in PATIENT_DATA_TABLES or t not in tables:
            continue
        # drop-class tables are preserved rather than migrated (the
        # import copies each to o19_archive and to an import_archived_
        # twin, and verifies both by count), so a non-empty one is an
        # advisory rather than a loss — but it still has to be COUNTED
        # here: the OLIS nomenclature tables are drop-class, and an OLIS
        # clinic that never wrote a preference row would otherwise raise
        # no blocker at all
        if cls not in ("archive", "drop"):
            continue
        n = count_live(t)
        if n > 0:
            if t.upper().startswith("OLIS"):
                olis_rows[t] = n
            elif cls == "drop":
                drop_rows[t] = n
            else:
                archive_rows[t] = n
    if drop_rows:
        findings.append(finding(
            "drop-tables-with-rows", ADVISORY,
            "{0} table(s) of removed modules hold rows CARLOS has no home "
            "for".format(len(drop_rows)),
            "These are infrastructure of modules CARLOS removed "
            "(Integrator caches, sharing policy, ministry reference "
            "reloads). Nothing in CARLOS reads them, so they are not "
            "migrated -- but they are not deleted either: the import "
            "preserves each one at o19_archive.<table> and at "
            "<target>.import_archived_<table>, and verifies both by row "
            "count. Review the list before migrating.", data=drop_rows))
    if archive_rows:
        findings.append(finding(
            "archive-config", ADVISORY,
            "{0} removed-module config/log table(s) are preserved rather "
            "than migrated".format(len(archive_rows)),
            "CARLOS has no table to copy these into. The import keeps "
            "each at o19_archive.<table> and at "
            "<target>.import_archived_<table>.", data=archive_rows))
    if olis_rows:
        findings.append(finding(
            "olis-in-use", BLOCKER,
            "OLIS was configured on this system",
            "The OLIS module does not exist in CARLOS: lab querying via OLIS "
            "stops working at cutover. Tell the clinic before migrating.",
            accept="olis-gone", data=olis_rows))


def check_dropped_column_data(c):
    """B3: data sitting in columns CARLOS removed."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    # --- B3: data in columns CARLOS dropped -------------------------------
    b3_hits = {}
    for t, cols in B3_FLAGGED_COLUMNS.items():
        if t not in tables:
            continue
        for col, predicate in cols.items():
            n = count_live(t, predicate)
            if n > 0:
                b3_hits["{0}.{1}".format(t, col)] = n
    if b3_hits:
        findings.append(finding(
            "B3-dropped-columns", BLOCKER,
            "data in {0} column(s) CARLOS removed".format(len(b3_hits)),
            "The clinic actively used a workflow whose column CARLOS "
            "does not have. No value is lost: each one is preserved on "
            "the live table as import_archived_<column>, with the source "
            "type and every row's value, and captured to an o19_archive "
            "shadow table as well. What stops is the workflow, not the "
            "data -- review it with the clinic before migrating.",
            accept="dropped-columns", data=b3_hits))


def check_required_tables(c):
    """Tables the import cannot run without.

    CARLOS's privilege check is exact-match, deny-by-default and counts
    only secUserRole rows with activeyn = 1; the importer reconciles the
    role matrix, so the findings from here on tell the clinic what the
    import will do rather than block it."""
    findings = c.findings
    known_live_lower = c.known_live_lower
    # --- roles, privileges and CARLOS-required data (M8) -----------------
    # two blockers the importer refuses on as well, then advisories
    # CARLOS's privilege check is exact-match, deny-by-default and counts
    # only secUserRole rows with activeyn = 1; the importer reconciles the
    # role matrix, so these findings tell the clinic what the import will
    # do rather than block it.
    # --- tables the import cannot run without -----------------------------
    missing_required = [t for t in REQUIRED_TABLES
                        if t.lower() not in known_live_lower]
    if missing_required:
        findings.append(finding(
            "required-tables-missing", BLOCKER,
            "{0} table(s) the import requires are absent".format(
                len(missing_required)),
            "The ETL refuses a dump without them, before writing — but "
            "only at P4, after the pre-import snapshot and the full "
            "staging restore. Re-take the dump without --ignore-table. "
            "No --accept flag exists for this.",
            data=dict((t, "absent") for t in missing_required)))


def check_identifier_class(c):
    """Table AND column names outside the identifier class the ETL
    accepts.

    The import refuses [^A-Za-z0-9_$] before its first write and no flag
    clears it, so counting such a name as merely "unknown, acknowledge
    it" would send the operator to a refusal the sign-off cannot reach.

    o19etl.unsafe_identifiers applies the same regex to every COLUMN as
    well, and o19etl.py:2617 die()s on the result at the top of run_etl
    (P4) — after the pre-import snapshot and the full staging restore.
    Columns were never scanned here, although nothing structural stopped
    it: the character-class test needs only
    information_schema.COLUMNS, not the schema manifest that
    check_unknown_columns waits for. Measured on MariaDB 10.11:
    `vendorlabs`.`result-value` gave verdict `go`, exit 0 and no
    identifier finding, while o19etl.unsafe_identifiers over the same
    schema returned ['vendorlabs.result-value']."""
    query = c.query
    findings = c.findings
    live_names = c.live_names
    schema_expr = c.schema_expr
    # --- identifier class -------------------------------------------------
    # the ETL refuses any table or column name outside [A-Za-z0-9_$]
    # before its first write, and no flag clears it; an assessment that
    # counts such a table and calls it "unknown, acknowledge it" sends the
    # operator to a refusal the sign-off cannot reach
    odd_names = sorted(n for n in live_names if not IDENTIFIER_RE.match(n))
    odd = dict((n, "outside [A-Za-z0-9_$]") for n in odd_names)
    # a failure here must not read as "no odd columns": it is a check
    # that could not run, which report_query_diagnostics turns into the
    # same no-go the count closures produce
    try:
        rows = query(
            "SELECT TABLE_NAME, COLUMN_NAME FROM "
            "information_schema.COLUMNS WHERE TABLE_SCHEMA = {0}"
            .format(schema_expr))
    except Exception as exc:                               # noqa: BLE001
        text = str(exc).strip()
        c.query_errors["information_schema.COLUMNS [identifier class]"] = (
            text.splitlines()[-1] if text else "")
        rows = []
    # COLUMNS spans VIEWS too, while `live_names` -- and the dump the
    # operator is told to take, and the ETL's own introspection -- is
    # BASE TABLE only. A view's odd column name is not an identifier the
    # import will ever meet, and this finding is a no-go no --accept
    # clears, so it must not be raised on one. (A view in the schema is
    # separately a blocker of its own: see check_views.)
    # EXACTLY, not case-folded: both sides of this comparison come from
    # the same information_schema on the same schema, so the spellings
    # already agree -- and where MySQL allows `Foo` (a table) beside
    # `foo` (a view), folding would hand the view's columns to the table
    # and raise the blocker this filter exists to prevent.
    base_tables = set(live_names)
    for row in rows:
        if len(row) < 2 or not row[0] or row[0] in odd:
            continue
        if row[0] not in base_tables:
            continue
        if not IDENTIFIER_RE.match(row[1] or ""):
            odd["{0}.{1}".format(row[0], row[1])] = "outside [A-Za-z0-9_$]"
    if odd:
        findings.append(finding(
            "identifier-class", BLOCKER,
            "{0} table/column name(s) outside the identifier class the "
            "import accepts".format(len(odd)),
            "Not an OSCAR 19 clinic schema as shipped. Rename them in the "
            "source and re-export. No --accept flag exists for this.",
            data=odd))


def check_views(c):
    """A view anywhere in the clinic schema aborts the staging restore.

    mysqldump writes every view with a `/*!50013 DEFINER=... */` clause,
    and P1 restores as a throwaway account scoped to the staging schema
    with deliberately no SUPER (see grant_staging_account: there is no
    fallback on purpose). Creating a view with a foreign DEFINER needs
    SUPER or SET USER, so the restore dies with ERROR 1227 -- measured
    on MariaDB 10.11 against a dump taken with the documented command.

    The tool had anticipated this for TRIGGERS only, so P1's own remedy
    named `--skip-triggers`, `--set-gtid-purged=OFF` and "no
    --databases" -- all three already true of the documented recipe, and
    none of which strips a view's DEFINER. There is no mysqldump flag
    that does.

    The remedy is to leave the views out, which costs the import
    nothing: it migrates base tables only (`base_table_names` and the
    ETL's introspection both filter TABLE_TYPE='BASE TABLE'), so a view
    would be ignored even if it restored. `--ignore-table` accepts a
    view, and the check names the exact flags because it knows the
    names."""
    query = c.query
    schema_expr = c.schema_expr
    try:
        rows = query(
            "SELECT TABLE_NAME FROM information_schema.VIEWS "
            "WHERE TABLE_SCHEMA = {0} ORDER BY TABLE_NAME"
            .format(schema_expr))
    except Exception as exc:                               # noqa: BLE001
        # a failure must not read as "no views": it is a check that could
        # not run, which report_query_diagnostics turns into a no-go
        text = str(exc).strip()
        c.query_errors["information_schema.VIEWS"] = (
            text.splitlines()[-1] if text else "")
        return
    names = [r[0] for r in rows if r and r[0]]
    if not names:
        return
    c.findings.append(finding(
        VIEWS_FINDING, BLOCKER,
        "{0} view(s) in the schema; a dump carrying one fails the "
        "staging restore".format(len(names)),
        "mysqldump writes each view with a DEFINER clause, and the "
        "import restores as an account scoped to the staging schema "
        "with no SUPER: the restore stops at ERROR 1227, after the "
        "pre-import snapshot has been taken. No --accept clears it and "
        "no mysqldump flag strips a DEFINER. Leave the views out of the "
        "dump -- the import migrates base tables only, so nothing is "
        "lost: " + " ".join(ignore_table_flags(names)),
        data=names))


#: a view name that needs no shell quoting in the copy-paste recipe
PLAIN_IDENTIFIER_RE = re.compile(r"\A[A-Za-z0-9_]+\Z")


def ignore_table_flags(names):
    """The mysqldump flags that leave `names` out of the dump.

    `<db>` rather than the real schema name: it is the same placeholder
    the bundling recipe uses on the line these flags are appended to.

    These flags are printed for an operator to COPY INTO A SHELL, and a
    view name is an arbitrary MySQL identifier read off the clinic's own
    database -- backquoted, it may hold a space, a quote, a `$` or a
    `;`. Anything but a plain identifier is single-quoted, so a hostile
    or merely awkward name becomes one argument instead of a second
    command."""
    flags = []
    for n in names:
        flag = "--ignore-table=<db>.{0}".format(n)
        flags.append(flag if PLAIN_IDENTIFIER_RE.match(n)
                     else shlex.quote(flag))
    return flags


def check_facility_and_clinic(c):
    """The two rows CARLOS cannot log anyone in without.

    A missing table counts as zero rows -- the importer refuses both. A
    count that FAILED is not zero rows: the query-errors blocker already
    covers that, so the row-count blocker is not raised on top of it."""
    query = c.query
    findings = c.findings
    tables = c.tables
    query_errors = c.query_errors
    absent_objects = c.absent_objects
    # a missing table counts as zero rows: the importer refuses both. A
    # count that FAILED is not zero rows — the query-errors blocker
    # already covers it, so the row-count blocker is not raised on top
    n = (_count(query, tables["Facility"], "disabled = 0")
         if "Facility" in tables else 0)
    if isinstance(n, tuple):
        if _absent_object(n[1]):
            # NOT patch-level variance to be waved through. The count
            # only runs when the TABLE is present, so an absent-object
            # error means `disabled` itself is gone -- and
            # o19etl.etl_precheck_problems refuses exactly that
            # ("a patch level older than the import supports"), inside
            # run_etl at P4, with no --accept. Routing it to the INFO
            # lane also short-circuited the `elif n == 0` arm below, so a
            # Facility that was BOTH column-less and empty verdicted
            # `go` twice over.
            findings.append(finding(
                "facility-no-disabled-column", BLOCKER,
                "the Facility table has no `disabled` column",
                "A patch level older than the import supports. The ETL "
                "refuses the dump at P4 — after the pre-import snapshot "
                "and the full staging restore — and nothing here can "
                "tell whether a Facility is enabled either. Re-export "
                "from a patch level that carries the column. No "
                "--accept flag exists for this.",
                data={"Facility [disabled = 0]": n[1]}))
        else:
            query_errors["Facility [disabled = 0]"] = n[1]
    elif n == 0:
        findings.append(finding(
            "facility-none-enabled", BLOCKER,
            "no enabled Facility row" if "Facility" in tables
            else "no Facility table",
            "CARLOS cannot log anyone in without an enabled Facility; "
            "the import refuses the dump before writing. Enable a "
            "Facility in the source and re-export. No --accept flag "
            "exists for this."))
    n = _count(query, tables["clinic"]) if "clinic" in tables else 0
    if isinstance(n, tuple):
        if _absent_object(n[1]):
            absent_objects["clinic"] = n[1]
        else:
            query_errors["clinic"] = n[1]
    elif n == 0:
        findings.append(finding(
            "clinic-missing", BLOCKER,
            "the clinic table is empty" if "clinic" in tables
            else "no clinic table",
            "Letterheads, requisitions and consultations dereference the "
            "clinic row; the import refuses the dump before writing. No "
            "--accept flag exists for this."))


def check_roles_and_assignments(c):
    """Clinic-custom roles, and the assignments that will not grant."""
    query = c.query
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    query_errors = c.query_errors
    if "secRole" in tables:
        custom = []
        # compared like the column's collation and like the importer
        stock = set(r.lower() for r in STOCK_ROLE_NAMES)
        try:
            for row in query("SELECT role_name FROM {0} ORDER BY role_name"
                             .format(_ident(tables["secRole"]))):
                if row and row[0] and row[0].lower() not in stock:
                    custom.append(row[0])
        except Exception as exc:
            text = str(exc).strip()
            query_errors["secRole [role list]"] = (
                text.splitlines()[-1] if text else "")
        if custom:
            findings.append(finding(
                "roles-custom", ADVISORY,
                "{0} clinic-custom role(s) not in the CARLOS role catalogue"
                .format(len(custom)),
                "Their O19 grants are carried. Roles holding at least one "
                "grant get the CARLOS-era privileges (fax, email, pharmacy "
                "edit, ...) of the closest stock role, reported for review "
                "(--role-template overrides the choice); a role granting "
                "nothing is left as it is. A stock role the clinic "
                "renamed appears here too and keeps its grants.",
                data={"roles": custom}))
    if "secUserRole" in tables:
        n = count_live("secUserRole", "activeyn IS NULL")
        if n:
            will = 0
            if "provider" in tables and "security" in tables:
                # exactly the rows the import activates
                will = count_live(
                    "secUserRole",
                    "activeyn IS NULL AND LOWER(role_name) <> 'admin' AND "
                    "provider_no IN (SELECT provider_no FROM {0}) AND "
                    "provider_no IN (SELECT provider_no FROM {1} WHERE "
                    "status = '1')".format(_ident(tables["security"]),
                                           _ident(tables["provider"])))
            findings.append(finding(
                "roles-activeyn-null", ADVISORY,
                "{0} role assignment(s) have activeyn NULL".format(n),
                "CARLOS counts only activeyn = 1; the import sets it to 1 "
                "for {0} of them (non-admin roles of live accounts) and "
                "leaves {1} as they are (admin assignments, accounts "
                "without a login, inactive providers); all listed in "
                "roles-details.txt.".format(will, max(n - will, 0))))
        if "provider" in tables and "security" in tables:
            # the import's P7 advisory after the activeyn-NULL rows of
            # live accounts have been activated: an active provider WITH
            # a login, no active role and no NULL row the import will
            # activate (those are counted in roles-activeyn-null)
            n = count_live(
                "provider",
                "status = '1' AND provider_no IN (SELECT provider_no FROM "
                "{1}) AND provider_no NOT IN (SELECT provider_no FROM {0} "
                "WHERE activeyn = 1 OR (activeyn IS NULL AND "
                "LOWER(role_name) <> 'admin'))".format(
                    _ident(tables["secUserRole"]),
                    _ident(tables["security"])))
            if n:
                findings.append(finding(
                    "roles-providers-without-active-role", ADVISORY,
                    "{0} active account(s) will hold no active role after "
                    "import".format(n),
                    "They can log in but reach nothing until a role is "
                    "assigned in Administration (a NULL admin assignment "
                    "is deliberately not activated by the import)."))


def check_expired_logins(c):
    """Accounts whose password expiry has already passed."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    if "security" in tables:
        n = count_live("security", "b_ExpireSet = 1 AND (date_ExpireDate IS "
                                   "NULL OR date_ExpireDate < NOW())")
        if n:
            findings.append(finding(
                "security-locked", ADVISORY,
                "{0} login(s) will be refused after import (expired)"
                .format(n),
                "b_ExpireSet with a past or missing expiry makes CARLOS "
                "refuse the login; the import leaves the rows as they are "
                "and lists them - extend or clear the expiry before "
                "go-live."))


def check_prevention_types(c):
    """Prevention type codes CARLOS cannot render."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    if "preventions" in tables and LEGACY_PREVENTION_TYPES:
        legacy = ", ".join("'{0}'".format(_sql_literal(t))
                           for t in LEGACY_PREVENTION_TYPES)
        # BINARY: legacy 'dTaP' must not count the valid 'DTaP' rows
        n = count_live("preventions",
                       "BINARY prevention_type IN ({0})".format(legacy))
        if n:
            findings.append(finding(
                "prevention-legacy-types", ADVISORY,
                "{0} prevention(s) use legacy type codes".format(n),
                "CARLOS renders Health Canada codes (Flu -> Inf, VZ -> Var, "
                "...); the import normalises the codes it knows (exact, "
                "case-sensitive match) and reports every other code it "
                "cannot render, which stays as it is for review."))


def check_rich_text_letter(c):
    """The Rich Text Letter eform, which the roles step modernises."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    if "eform" in tables:
        n = count_live(
            "eform",
            "form_html LIKE '%<title>Rich Text Letter</title>%' AND "
            "(form_name <> 'Rich Text Letter' OR form_html NOT LIKE "
            "'%RTL 2026.3.0%')")
        if n:
            findings.append(finding(
                "rtl-legacy-form", ADVISORY,
                "{0} legacy Rich Text Letter form(s)".format(n),
                "The O19 form carries a raw-SQL sink and pre-CARLOS routes. "
                "The import brings the stock row (named 'Rich Text Letter', "
                "subject starting 'Rich Text Letter Generator') to "
                "2026.3.0 (a disabled one stays disabled), disables other "
                "RTL-titled copies, seeds a new ENABLED form when no stock "
                "row exists, and reports a row whose subject was edited "
                "for hand review."))


def check_removed_module_properties(c):
    """Property keys belonging to modules CARLOS removed."""
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    if "property" in tables:
        removed = {}
        for prefix in DROPPED_PROP_PREFIXES:
            n = count_live("property", "name LIKE '{0}'".format(
                _like_prefix(prefix)))
            if n:
                removed[prefix] = n
        # exact-named keys as well as prefixes: the props overlay drops
        # most removed-module keys by prefix, but a good number by exact
        # name (KEYS entries), and the import prunes BOTH — counting only
        # the prefixes understated what the operator is about to lose.
        # Chunked because a clinic property table is queried through the
        # batch client and this list is long.
        for chunk in _chunks(DROPPED_PROP_KEYS, 200):
            # count_live() already files a failed count into the
            # query-errors blocker and returns 0, exactly as it does for
            # the prefix counts above — a tuple never reaches here, and
            # storing one would make the sum() below raise instead
            n = count_live("property", "name IN ({0})".format(
                ", ".join("'{0}'".format(_sql_literal(k)) for k in chunk)))
            if n:
                removed["(exact keys)"] = removed.get("(exact keys)", 0) + n
        if removed:
            findings.append(finding(
                "property-removed-module-keys", ADVISORY,
                "{0} property-table row(s) belong to removed modules"
                .format(sum(removed.values())),
                "Keys of modules CARLOS removed (Integrator, MyOSCAR, OLIS, "
                "...) in the property table; the import prunes them.",
                data=removed))


def check_indicator_templates(c):
    """Dashboard indicators whose drill-down SQL names a dropped table."""
    query = c.query
    findings = c.findings
    tables = c.tables
    query_errors = c.query_errors
    if "indicatorTemplate" in tables:
        dropped_tables = sorted(t for t, c in KNOWN_TABLES.items()
                                if c in ("archive", "drop"))
        hits = {}
        try:
            for row in query("SELECT id, name, template FROM {0}".format(
                    _ident(tables["indicatorTemplate"]))):
                if len(row) < 3:
                    continue
                refs = dropped_table_references(row[2], dropped_tables)
                if refs:
                    hits["{0} (id {1})".format(row[1] or "?", row[0])] = \
                        ", ".join(refs)
        except Exception as exc:
            text = str(exc).strip()
            query_errors["indicatorTemplate [scan]"] = (
                text.splitlines()[-1] if text else "")
        if hits:
            findings.append(finding(
                "indicator-templates-dropped-refs", ADVISORY,
                "{0} dashboard indicator(s) query tables CARLOS removed"
                .format(len(hits)),
                "Their drill-down SQL names archived/dropped tables and "
                "will fail at run time; retire or rewrite them.",
                data=hits))


def check_properties(c, properties, locked_notes):
    """The checks driven by the clinic's oscar.properties."""
    findings = c.findings
    # --- properties-driven checks ----------------------------------------
    if properties is not None:
        # B5: LDAP authentication
        if properties.get("ldap.enabled", "").lower() in ("true", "yes", "on"):
            findings.append(finding(
                "B5-ldap", BLOCKER,
                "LDAP authentication is enabled",
                "CARLOS has no LDAP authentication: staff could not log in "
                "after cutover. Provision local credentials first. No "
                "--accept flag exists for this."))
        # B4: encrypted casemgmt notes — judged on the rows below, not on
        # the property: casemgmt.note.password.enabled=true is the stock
        # O19 default and says nothing about whether a note was ever locked
        if properties.get("casemgmt.note.password.enabled", "") \
                .lower() in ("true", "yes", "on") and not locked_notes:
            findings.append(finding(
                "notes-password-enabled", ADVISORY,
                "casemgmt.note.password.enabled is set (the stock O19 "
                "default) but no note carries a password",
                "Nothing is encrypted; CARLOS has no reader for that key "
                "and the props phase drops it."))
        dropped = {}
        exact = set(DROPPED_PROP_KEYS)
        for key in sorted(properties):
            if key.startswith("ldap."):
                continue
            for prefix in DROPPED_PROP_PREFIXES:
                if key.startswith(prefix):
                    dropped.setdefault(prefix, []).append(key)
                    break
            else:
                # not prefix-classified: the overlay may still drop it by
                # exact name, and the props phase itemises it either way
                if key in exact:
                    dropped.setdefault("(exact keys)", []).append(key)
        if dropped:
            findings.append(finding(
                "dropped-properties", ADVISORY,
                "{0} configured key(s) belong to removed modules"
                .format(sum(len(v) for v in dropped.values())),
                "These oscar.properties settings have no CARLOS equivalent "
                "and will be itemized (not carried) by the props phase.",
                data=dropped))
    else:
        # a BLOCKER: one of the checks this skips (ldap.enabled) is a
        # no-accept refusal at import time, so an assessment without the
        # file can return "go" for a clinic whose every staff login
        # breaks at cutover. No accept class — supply the file.
        findings.append(finding(
            "no-properties", BLOCKER,
            "oscar.properties not provided",
            "Property-based checks are skipped without it, and one of "
            "them (LDAP authentication) is a refusal the import cannot "
            "acknowledge. Rerun with --properties. No --accept flag "
            "exists for this."))


def _hex_escape_regexp_supported(query):
    """Whether this server's regex engine understands `\\x{NN}` escapes.

    MySQL < 8 and MariaDB < 10.0.5 -- the assessment hosts this file
    targets -- use the Spencer engine, which reads the escape as a
    LITERAL bracket expression. Running the B8 marker scan there would
    not fail; it would answer confidently about the wrong characters. So
    the probe is two-sided: a latin1 'Ã©' must match and plain ASCII
    must not. Anything else (including a server that errors on the
    reversed range Spencer sees) means the scan cannot be trusted here."""
    try:
        rows = query(MOJIBAKE_MARKER_PROBE)
    except Exception:                                      # noqa: BLE001
        return False
    if not rows or len(rows[0]) < 2:
        return False
    return (str(rows[0][0]).strip() == "1"
            and str(rows[0][1]).strip() == "0")


def check_text_encoding(c):
    """The charset scan, all three of the ETL's predicates.

    o19etl.detect_repairs runs THREE counts over each scanned column and
    only the first was measured here:

    * `n`     -- round-trips through latin1: repairable, and the ETL
                 refuses it unacknowledged (--accept charset-repair);
    * `multi` -- still looks double-encoded after ONE repair hop, i.e.
                 encoded more than twice. It SATISFIES the repairable
                 predicate, so the assessment used to report it as
                 ordinary mojibake and hand the operator the very flag
                 that cannot clear it;
    * `bad`   -- carries the mojibake byte signature but does NOT
                 round-trip (mixed-encoding text: mojibake bytes beside
                 raw latin1 bytes, the ordinary state of a database that
                 has been through a charset move).

    The last two are B8: `die("...no flag overrides it")` at P4, after
    the pre-import snapshot and the full staging restore. `multi` is pure
    CONVERT/LENGTH and runs anywhere; `bad` needs a regex engine the
    2014-era assessment host may not have, so when the probe says it
    cannot be trusted the report SAYS the stop was not measured rather
    than letting a silent `go` stand in for it."""
    count_live = c.count_live
    query = c.query
    findings = c.findings
    tables = c.tables
    # --- charset / mojibake sampling --------------------------------------
    mojibake = {}
    unrepairable = {}
    marker_scan = _hex_escape_regexp_supported(query)
    for t, cols in CHARSET_SCAN.items():
        if t not in tables:
            continue
        for col in cols:
            label = "{0}.{1}".format(t, col)
            quoted = "`{0}`".format(col)
            n = count_live(t, double_encoded_predicate(col))
            if n > 0:
                mojibake[label] = n
            multi = count_live(t, "({0}) AND ({1})".format(
                double_encoded_predicate(col),
                double_encoded_expr(REPAIR_TEMPLATE.format(quoted))))
            if multi > 0:
                # both halves can hit the same column; neither may
                # overwrite the other's count in the report
                unrepairable.setdefault(label, []).append(
                    "{0} row(s) still double-encoded after one repair — "
                    "encoded more than twice".format(multi))
            if not marker_scan:
                continue
            bad = count_live(t, "{0} IS NOT NULL AND {0} REGEXP {1} AND "
                                "NOT ({2})".format(
                                    quoted, MOJIBAKE_MARKER_RE,
                                    double_encoded_predicate(col)))
            if bad > 0:
                unrepairable.setdefault(label, []).append(
                    "{0} row(s) carry mojibake bytes that do not "
                    "round-trip".format(bad))
    if unrepairable:
        # B8: no --accept clears this one on either side. The ETL dies on
        # it before the charset-repair question is even asked, so the
        # assessment must not offer a flag here either.
        findings.append(finding(
            "B8-charset-unrepairable", BLOCKER,
            "text in {0} column(s) that the latin1->utf8mb4 repair "
            "cannot fix".format(len(unrepairable)),
            "The ETL stops outright on this (B8) at P4 — after the "
            "pre-import snapshot and the full staging restore — and no "
            "--accept flag overrides it. Repair the text in the source "
            "and re-export.",
            data=dict((k, "; ".join(v)) for k, v in unrepairable.items())))
    if mojibake:
        # a BLOCKER, not an advisory: the ETL refuses outright without
        # --accept charset-repair, and it refuses at P4 — after the
        # pre-import snapshot and the full staging restore. An assessment
        # that says "go" here costs the clinic a cutover window.
        findings.append(finding(
            "charset-mojibake", BLOCKER,
            "double-encoded text detected in {0} column(s)"
            .format(len(mojibake)),
            "The ETL's per-row charset repair handles this but refuses "
            "to run unacknowledged; text the repair cannot round-trip "
            "blocks there outright (B8).", accept="charset-repair",
            data=mojibake))
    if not marker_scan:
        findings.append(finding(
            "charset-b8-unmeasured", ADVISORY,
            "one half of the B8 charset stop could not be measured on "
            "this server",
            "This server's regex engine does not understand the "
            "\\x{NN} escapes the mojibake byte signature needs (MySQL < "
            "8 / MariaDB < 10.0.5), so mixed-encoding text — mojibake "
            "bytes beside raw latin1 bytes — was NOT counted. The ETL "
            "does count it, at P4, and stops there with no --accept. "
            "The thrice-encoded half of B8 WAS measured. Treat a `go` "
            "as silent about this one class."))


def unknown_columns(entry, col_names):
    """Staged columns the manifest neither copies, renames nor lists as
    dropped (vendor-fork additions) -- matched case-insensitively.

    A deliberate copy of o19etl.unknown_columns, which this file may not
    import, and the two must agree EXACTLY: this copy decides whether the
    operator is shown the B2-unknown-columns blocker and what
    `--accept unknown-as-archive` is signing off on ("each is preserved
    on the live table as import_archived_<column> ... never silently
    dropped"), while the ETL copy, through archived_column_plan, decides
    what is actually preserved. A divergence is invisible downstream:
    o19etl.archived_column_parity iterates the `import_archived_` columns
    the ETL side PRODUCED, so a column the preflight called unknown and
    the ETL called known has no target column to iterate and P7 reports a
    clean import over an orphaned column. Pinned in
    tests/test_sql_escape_contract.py."""
    known = set(entry.get("renames", {}).get(c, c).lower()
                for c in entry.get("cols", []))
    known.update(c.lower() for c in entry.get("dropped", {}))
    return sorted(c for c in col_names if c.lower() not in known)


def check_unknown_columns(c, schema_map):
    """Columns the manifest has no home for (import mode only).

    Needs the schema manifest, so the standalone assessment says so
    rather than reporting a clean column inventory it never took."""
    query = c.query
    findings = c.findings
    live_to_manifest = c.live_to_manifest
    schema_expr = c.schema_expr
    # --- column-level unknowns (import mode only) -------------------------
    if schema_map is not None:
        col_map = {}
        for row in query(
                "SELECT TABLE_NAME, COLUMN_NAME FROM "
                "information_schema.COLUMNS WHERE TABLE_SCHEMA = {0}"
                .format(schema_expr)):
            if len(row) == 2:
                # keyed by the manifest spelling, through the same
                # live->manifest mapping the table inventory uses
                col_map.setdefault(live_to_manifest.get(row[0], row[0]),
                                   set()).add(row[1])
        unknown_cols = {}
        for t, entry in schema_map.TABLES.items():
            if entry.get("class") not in ("copy", "merge") or t not in col_map:
                continue
            extra = unknown_columns(entry, col_map[t])
            if extra:
                unknown_cols[t] = extra
        if unknown_cols:
            findings.append(finding(
                "B2-unknown-columns", BLOCKER,
                "{0} table(s) carry columns the manifest does not know"
                .format(len(unknown_cols)),
                "Vendor-fork columns. If accepted, each is preserved on "
                "the live table as import_archived_<column> (source type, "
                "every row) and captured to an o19_archive shadow table "
                "as well -- never silently dropped.",
                accept="unknown-as-archive", data=unknown_cols))
    else:
        # An ADVISORY, not an INFO, and worded as a gap rather than as a
        # schedule. "column-level unknown detection runs in import mode"
        # reads as "the vendor-column inventory happens later"; what it
        # actually means is that a whole class of P4 refusals NO --accept
        # can clear is unmeasured here, so a `go` is silence about them
        # rather than a clearance.
        findings.append(finding(
            "column-checks-deferred", ADVISORY,
            "column-level checks could not run without a CARLOS schema "
            "to compare against",
            "Two things are unmeasured here and both refuse at P4 — "
            "after the pre-import snapshot and the full staging restore "
            "— with no --accept. (1) The vendor-column inventory (B2 "
            "unknown columns) needs the schema manifest carlos-ctl "
            "supplies. (2) o19etl's overlength and numeric-coercion "
            "pre-checks compare every value against the TARGET column's "
            "capacity and type, which exist only on the CARLOS host: the "
            "manifest carries no widths. The realistic instance is "
            "charset-driven — TEXT/BLOB capacity is BYTES, so a latin1 "
            "`text` holding 65535 CHARACTERS at the clinic becomes a "
            "utf8mb4 `text` holding 65535 BYTES in CARLOS, and an "
            "identically-declared column has genuinely narrowed for any "
            "accented content (casemgmt_note.note, eform_data, document "
            "descriptions). Nothing here can predict either; --dry-run "
            "cannot either, since it returns after P2."))


def record_inventory(c):
    """Row counts for the sanity-anchor tables, plus database size."""
    query = c.query
    count_live = c.count_live
    findings = c.findings
    tables = c.tables
    schema_expr = c.schema_expr
    # --- inventory (sanity anchors) ---------------------------------------
    inv = {}
    for t in INVENTORY_TABLES:
        if t in tables:
            inv[t] = count_live(t)
    # The size figure is an operator convenience, not an input to any
    # verdict, so it must never abort the assessment: a locked-down account
    # can be refused information_schema (RuntimeError from the client), and a
    # schema whose tables hold no data pages answers NULL, which the batch
    # reader hands back as text (ValueError). Record WHY it is missing rather
    # than swallowing the error -- an inventory that silently omits the line
    # reads as "this database has no size", and the operator has no way to
    # tell a refused query from a genuinely empty one. The client's stderr
    # carries no credentials by construction (see make_cli_query).
    try:
        size_rows = query(
            "SELECT ROUND(SUM(DATA_LENGTH + INDEX_LENGTH)/1048576) FROM "
            "information_schema.TABLES WHERE TABLE_SCHEMA = {0}"
            .format(schema_expr))
        inv["_database_mb"] = int(float(size_rows[0][0]))
    except (RuntimeError, IndexError, ValueError, TypeError) as exc:
        inv["_database_mb_unavailable"] = str(exc)
    inv["_tables"] = len(tables)
    findings.append(finding("inventory", INFO, "database inventory",
                            data=inv))


def report_query_diagnostics(c):
    """Patch-level variance (INFO) and failed checks (BLOCKER).

    A check that could not RUN is never treated as "zero rows": an
    unreadable table is a hard no-go with no --accept flag, while a
    table or column this patch level simply does not carry is reported
    and nothing more."""
    findings = c.findings
    query_errors = c.query_errors
    absent_objects = c.absent_objects
    # --- patch-level variance: reported, never a verdict -----------------
    if absent_objects:
        findings.append(finding(
            "absent-objects", INFO,
            "{0} check(s) named a table or column this dump does not "
            "carry".format(len(absent_objects)),
            "A lower patch level than the manifest's superset. The "
            "importer skips what is absent and reports it; the target's "
            "default stands for a missing column.", data=absent_objects))

    # --- query errors: a check that could not run is a hard no-go --------
    if query_errors:
        findings.append(finding(
            "query-errors", BLOCKER,
            "{0} check(s) could not be completed".format(len(query_errors)),
            "A count failed (privilege, corrupt table, ...). An unreadable "
            "table is never treated as empty: fix the access and rerun. "
            "No --accept flag exists for this.", data=query_errors))


def preflight_verdict(findings, accepted):
    """Reduce the findings to a verdict.

    Returns ``(verdict, exit_code, acknowledged, outstanding)``. A blocker
    with no --accept flag can never be signed off; one whose flag the
    operator passed is acknowledged; one whose flag they did not pass is
    what stands between them and a `go`.
    """
    outstanding = []
    acknowledged = []
    hard = []
    for f in findings:
        if f["severity"] != BLOCKER:
            continue
        flag = f.get("accept")
        if flag and flag in accepted:
            acknowledged.append(f["id"])
        elif flag:
            outstanding.append(f)
        else:
            hard.append(f)
    if hard:
        return "no-go", 2, acknowledged, outstanding
    if outstanding:
        return "go-with-acknowledgements", 1, acknowledged, outstanding
    return "go", 0, acknowledged, outstanding


def run_checks(query, properties=None, province="on", accepted=(),
               schema_map=None, db_name=None):
    """Run every preflight check; return the report dict.

    query        callable(sql) -> list of rows (lists of strings)
    properties   parsed clinic properties dict, or None if not provided
    accepted     iterable of --accept class names already granted
    schema_map   the o19map_schema module (import mode only) — enables
                 column-level unknown detection
    db_name      schema to introspect via information_schema (defaults to
                 DATABASE() of the connection)
    """
    accepted = set(accepted)
    findings = []
    query_errors = {}
    # tables/columns this patch level does not carry: reported, never a
    # blocker (see _absent_object)
    absent_objects = {}

    def count(table, where=None):
        """_count that FAILS CLOSED: an error is recorded (and becomes a
        no-go blocker below) and counts as 0 only for the purpose of not
        double-reporting the table.

        An error naming an absent table or column is the exception. The
        importer anticipates patch-level variance — o19etl.effective_entry
        skips a column the dump does not carry and lets the target's
        default stand — so routing it into the query-errors blocker
        produces an unacceptable `no-go` for a clinic the import
        supports, with a diagnosis (privileges, corruption) that is
        simply wrong. Where an absent column is instead a P4 REFUSAL the
        caller raises its own blocker; see check_facility_and_clinic."""
        n = _count(query, table, where)
        if isinstance(n, tuple):
            label = table if where is None else "{0} [{1}]".format(
                table, where[:60])
            if _absent_object(n[1]):
                absent_objects[label] = n[1]
            else:
                query_errors[label] = n[1]
            return 0
        return n

    # --- province gate ----------------------------------------------------
    # The generated block above was curated for ONE province and stamps
    # which (O19_PROFILE). A host configured for another one would be
    # assessed against the wrong table classes, the wrong patient-data
    # list and the wrong dropped columns -- quietly, since every ruling
    # would still be a ruling. Asserted, not string-tested against 'on',
    # so the check is the same one when a second profile ships.
    if province != O19_PROFILE:
        findings.append(finding(
            "province", BLOCKER,
            "this check carries the '{0}' manifest and the host is "
            "province '{1}'".format(O19_PROFILE, province),
            "Every table ruling here was curated against one province's "
            "CARLOS schema. Install a carlos-ctl whose manifest profile "
            "matches the host, or correct the host's province. No "
            "--accept flag exists for this."))

    (schema_expr, tables, live_names, live_to_manifest,
     known_live_lower) = live_table_inventory(query, findings, db_name)

    def count_live(manifest_name, where=None):
        return count(tables[manifest_name], where)

    c = PreflightState(query, count, count_live, findings, accepted,
                       query_errors, absent_objects, schema_expr, tables,
                       live_names, live_to_manifest, known_live_lower)
    # first: everything below reads `tables`, and `tables` is only as
    # complete as this account's privileges make it
    check_account_reach(c)
    check_carried_credentials(c)
    locked_notes = check_locked_notes(c)
    check_unknown_tables(c)
    check_patient_data_in_absent_tables(c)
    check_preserved_class_rows(c)
    check_dropped_column_data(c)
    check_required_tables(c)
    check_identifier_class(c)
    check_views(c)
    check_facility_and_clinic(c)
    check_roles_and_assignments(c)
    check_expired_logins(c)
    check_prevention_types(c)
    check_rich_text_letter(c)
    check_removed_module_properties(c)
    check_indicator_templates(c)
    check_properties(c, properties, locked_notes)
    check_text_encoding(c)
    check_unknown_columns(c, schema_map)
    record_inventory(c)
    report_query_diagnostics(c)

    verdict, exit_code, acknowledged, outstanding = preflight_verdict(
        findings, accepted)

    return {
        "verdict": verdict,
        "exit_code": exit_code,
        "schema_map_version": SCHEMA_MAP_VERSION,
        "generated_at": datetime.datetime.now().isoformat(),
        "province": province,
        "accepted": sorted(accepted),
        "acknowledged": acknowledged,
        "required_accepts": sorted(set(
            f["accept"] for f in outstanding)),
        "findings": findings,
    }


def render_text(report):
    """The human-readable report: blockers first, then advisories, then
    info. The JSON report is the machine-readable twin."""
    lines = []
    lines.append("OSCAR 19 -> CARLOS migration preflight (experimental)  "
                 "[manifest {0}]".format(report["schema_map_version"]))
    lines.append("=" * 72)
    order = {BLOCKER: 0, ADVISORY: 1, INFO: 2}
    for f in sorted(report["findings"], key=lambda x: order[x["severity"]]):
        lines.append("")
        lines.append("[{0}] {1}".format(f["severity"].upper(), f["title"]))
        if f.get("detail"):
            lines.append("  " + f["detail"])
        if f.get("accept"):
            lines.append("  cleared by: --accept " + f["accept"])
        data = f.get("data")
        if isinstance(data, dict):
            for k in sorted(data):
                lines.append("    {0}: {1}".format(k, data[k]))
    lines.append("")
    lines.append("-" * 72)
    lines.append("VERDICT: " + report["verdict"])
    if report["required_accepts"]:
        lines.append("would proceed with: " + " ".join(
            "--accept " + a for a in report["required_accepts"]))
    if report["acknowledged"]:
        lines.append("acknowledged: " + ", ".join(report["acknowledged"]))
    # A `go` printed straight above the bundling recipe reads as a
    # green light. Some P4 refusals cannot be measured from the clinic
    # at all, and none of them has an --accept, so the honest report
    # repeats what it could not predict right where the operator decides
    # to ship.
    unmeasured = [f for f in report["findings"]
                  if f["id"] in UNMEASURED_FINDING_IDS]
    if unmeasured:
        lines.append("")
        lines.append("NOT MEASURED HERE — a 'go' is silent about these, "
                     "not a clearance of them:")
        for f in unmeasured:
            lines.append("  - " + f["title"])
        lines.append("  Each is a refusal the import makes at P4, after "
                     "the pre-import snapshot and the")
        lines.append("  full staging restore, and no --accept clears "
                     "one. See the findings above.")
    lines.append("")
    lines.append("Next step on a 'go': bundle the three inputs on this "
                 "server and ship them to the CARLOS host:")
    lines.append("  # stop Tomcat FIRST: OSCAR 19 tables are usually "
                 "MyISAM, for which")
    lines.append("  # --single-transaction gives no consistency at all "
                 "against a live database")
    # the flags are built from THIS schema's views, so the operator
    # copies a command that works here rather than the generic one,
    # which would die at P1 on the first view's DEFINER clause
    views = [f for f in report["findings"] if f["id"] == VIEWS_FINDING]
    view_flags = ""
    if views:
        view_flags = " " + " ".join(ignore_table_flags(views[0]["data"]))
    lines.append("  mysqldump --single-transaction --quick --skip-triggers "
                 "{0}<db> | gzip > o19.sql.gz".format(
                     view_flags.strip() + " " if view_flags else ""))
    lines.append("    (MySQL 5.6+: add --set-gtid-purged=OFF; the import "
                 "refuses a dump that sets GTID_PURGED)")
    if views:
        lines.append("    (the --ignore-table flags leave this schema's "
                     "view(s) out: mysqldump writes each")
        lines.append("     with a DEFINER the import's restore account "
                     "cannot set, and the import needs")
        lines.append("     none of them — it migrates base tables only)")
    lines.append("  tar -C /var/lib/OscarDocument -czf o19-documents.tar.gz "
                 "<context-dir>")
    lines.append("  # run this check again with --digests "
                 "o19-digests.json if you have not; the")
    lines.append("  # CARLOS host uses it to prove the transfer carried "
                 "every value")
    lines.append("  tar -czf - o19.sql.gz o19-documents.tar.gz "
                 "oscar.properties o19-digests.json \\")
    lines.append("    | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt "
                 "-pass file:PASSFILE \\")
    lines.append("    -out o19-bundle.tar.gz.enc")
    lines.append("    (OpenSSL 1.0.x, e.g. Ubuntu 14.04, has neither "
                 "-pbkdf2 nor -iter: use")
    lines.append("     `openssl enc -aes-256-cbc -md sha256 -salt` and tell "
                 "the CARLOS operator,")
    lines.append("     who then passes --bundle-openssl-opt -md "
                 "--bundle-openssl-opt sha256)")
    lines.append("  sha256sum o19-bundle.tar.gz.enc   # send the digest with "
                 "the "
                 "password, separately from the file")
    lines.append("Migration output must receive a technical review before "
                 "clinical use.")
    return "\n".join(lines) + "\n"


def write_private_json(path, report):
    """A machine-readable document at 0600 (the report, or the digests).

    This is the one tool in the set that runs on the SOURCE OSCAR 19
    server, as an ordinary user on a host the clinic still works on, so
    "root-only anyway" is not the excuse it is inside the import's own
    0700 workspace. The report's `data` carries the clinic's table
    names, role names, dashboard indicator names and property keys, and
    the digest document carries every table and column name in the
    schema; a plain `open()` would leave either one 0644 under the usual
    umask.

    `fchmod` after the open, not the `os.open` mode: that mode applies
    to a NEW file only, so re-running the assessment over an existing
    report would keep whatever mode it already had."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(report, fh, indent=1, sort_keys=True)


def main(argv=None):
    """Standalone entrypoint, run on the CLINIC's OSCAR 19 server before
    any bundle is built.

    Kept Python 3.4-compatible and dependency-free so it runs unchanged
    on an Ubuntu 14.04 host. Returns the verdict as the exit code: 0 go,
    1 go with acknowledgements, 2 no-go, 3 tool error."""
    ap = argparse.ArgumentParser(
        description="OSCAR 19 -> CARLOS migration feasibility check "
                    "(assessment mode; experimental)")
    ap.add_argument("--db", required=True, help="OSCAR database name")
    ap.add_argument("--mysql-cmd", default="mysql",
                    help="mysql/mariadb client command (default: mysql)")
    ap.add_argument("--mysql-arg", action="append", default=[],
                    metavar="ARG",
                    help="argument passed to the client (repeatable); use "
                         "the =form for values starting with '-', e.g. "
                         "--mysql-arg=-uroot --mysql-arg=--host=127.0.0.1")
    ap.add_argument("--mysql-password-file", metavar="PATH",
                    help="file holding the client password (handed over "
                         "as MYSQL_PWD, never on the command line); "
                         "alternatively use a client defaults file via "
                         "--mysql-arg=--defaults-extra-file=PATH")
    ap.add_argument("--properties", help="path to the deployed "
                    "oscar.properties (recommended)")
    ap.add_argument("--province", default="on", choices=["on", "bc"])
    ap.add_argument("--accept", action="append", default=[],
                    metavar="CLASS", choices=list(ACCEPT_IDS),
                    help="acknowledge a blocker class (repeatable): "
                    + ", ".join(ACCEPT_IDS))
    ap.add_argument("--json", metavar="PATH",
                    help="also write the machine-readable report here")
    ap.add_argument("--digests", metavar="PATH",
                    help="also write per-table content digests here, and "
                         "ship the file in the bundle: it is what lets "
                         "the CARLOS host prove the dump and restore "
                         "carried every VALUE, not merely the right "
                         "number of rows. Costs one full scan of the "
                         "database (~200k rows/s)")
    try:
        args = ap.parse_args(argv)
    except SystemExit as exc:
        # argparse exits 2 on a bad argument and 0 after --help; only the
        # latter is not a tool error, and 2 is a verdict code here
        return 0 if exc.code == 0 else EXIT_TOOL_ERROR

    if SCHEMA_MAP_VERSION == "unpopulated":
        print("ERROR: this copy of o19_preflight.py carries no generated "
              "manifest data;\nregenerate it with "
              "scripts/migration/o19/generate_manifests.py", file=sys.stderr)
        return EXIT_TOOL_ERROR

    props = None
    if args.properties:
        try:
            props = parse_properties(args.properties)
        except (IOError, OSError, ValueError) as exc:
            print("ERROR: cannot read --properties '{0}': {1}"
                  .format(args.properties, exc), file=sys.stderr)
            return EXIT_TOOL_ERROR

    bad = password_arg_problem(args.mysql_arg)
    if bad:
        print("ERROR: client argument {0} refused: a bare -p would prompt "
              "on every query (each check runs a fresh client) and an "
              "attached password lands in the process list. Pass the "
              "password via --mysql-password-file PATH or "
              "--mysql-arg=--defaults-extra-file=PATH instead."
              .format(bad), file=sys.stderr)
        return EXIT_TOOL_ERROR
    env = None
    if args.mysql_password_file:
        import os
        try:
            with open(args.mysql_password_file, "rb") as fh:
                password = fh.read().decode("utf-8", "replace") \
                    .rstrip("\r\n")
        except (IOError, OSError) as exc:
            print("ERROR: cannot read --mysql-password-file '{0}': {1}"
                  .format(args.mysql_password_file, exc), file=sys.stderr)
            return EXIT_TOOL_ERROR
        env = dict(os.environ)
        env["MYSQL_PWD"] = password

    query = make_cli_query(args.mysql_cmd, args.mysql_arg, args.db, env=env)
    try:
        query("SELECT 1")
    except Exception as exc:
        print("ERROR: cannot query database '{0}': {1}"
              .format(args.db, exc), file=sys.stderr)
        return EXIT_TOOL_ERROR

    # anything that breaks from here on is a TOOL failure, reported as
    # such — never as a verdict exit code
    try:
        report = run_checks(query, properties=props,
                            province=args.province, accepted=args.accept)
        # the machine report first: a rendering problem must never cost it
        if args.json:
            write_private_json(args.json, report)
        if args.digests:
            # the one step here that scans every row; say so before it
            # starts, on stderr, so the report on stdout stays the
            # machine-readable-adjacent thing it is
            print("taking content digests (one full scan of '{0}')..."
                  .format(args.db), file=sys.stderr)
            schema_expr = "'{0}'".format(args.db)
            digests = collect_digests(
                query, schema_expr, base_table_names(query, schema_expr),
                province=args.province, db_name=args.db)
            write_private_json(args.digests, digests)
        text = render_text(report)
        # bytes, not str: a 2014-era Python under LANG=C would refuse any
        # non-ASCII character in a role or table name on the way out
        buf = getattr(sys.stdout, "buffer", None)
        if buf is not None:
            sys.stdout.flush()
            buf.write(text.encode("utf-8"))
            buf.flush()
        else:
            sys.stdout.write(text)
        if args.json:
            print("json report written to " + args.json)
        if args.digests:
            print("content digests written to {0} ({1} table(s))".format(
                args.digests, len(digests["tables"])))
            # A table left out of `tables` is the fail-closed half of
            # collect_digests, and the operator is the only person who
            # can act on it while still AT the clinic. Buried in the
            # JSON it costs a second trip: at P2 the import reports the
            # table as unverified and demands --accept no-content-digests
            # or a re-run of the assessment. Most of these tables are
            # copy-class clinical or billing tables that no CHECK counts,
            # so the query-errors blocker never sees them and the verdict
            # stays a clean `go`. On stderr, beside the "taking content
            # digests" line, so the report on stdout is unchanged.
            errors = digests.get("errors") or {}
            if errors:
                print("WARNING: {0} table(s) could NOT be measured and "
                      "carry no digest; the import will report them as "
                      "unverified:".format(len(errors)), file=sys.stderr)
                for name in sorted(errors):
                    print("  {0}: {1}".format(name, errors[name]),
                          file=sys.stderr)
    except Exception as exc:
        print("ERROR: preflight could not complete: {0}".format(exc),
              file=sys.stderr)
        return EXIT_TOOL_ERROR
    return report["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
