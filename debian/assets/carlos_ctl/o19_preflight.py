# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""o19_preflight — OSCAR 19 -> CARLOS migration feasibility check (experimental).

Runs the go/no-go gate of docs/oscar19-to-carlos-migration-plan.md section 6.1
in two modes with identical checks:

* ASSESSMENT MODE (standalone, at the clinic): copy THIS ONE FILE to the
  OSCAR 19 server and run it against the live database before any backup is
  shipped. It is deliberately self-contained and runs on the old Python 3
  found on 2014-era Ubuntu (no f-strings, stdlib only) and talks to MySQL by
  shelling out to the mysql/mariadb command-line client:

      python3 o19_preflight.py --db oscar --mysql-cmd mysql \\
          --mysql-arg=-uroot --mysql-password-file /root/.o19pw \\
          --properties /path/to/oscar.properties

  (--mysql-arg values start with '-', so the =form is required; the
  password travels via MYSQL_PWD from --mysql-password-file, never argv —
  a bare interactive -p would prompt once per query and is refused.)

* IMPORT MODE: `carlos-ctl import-o19` imports this module and calls
  run_checks() against the restored o19_import staging schema, passing the
  full schema manifest for column-level checks.

Exit codes: 0 = go; 1 = go-with-acknowledgements (blockers exist but every
one names the --accept flag that clears it); 2 = no-go (a blocker needs
remediation, not a flag). The JSON report (--json) is the machine contract.

Migration output should receive a technical review — verification report,
spot checks, UI smoke — before clinical use.

The data between the GENERATED markers is written by
scripts/migration/o19/generate_manifests.py — do not edit it by hand.
"""

from __future__ import print_function

import argparse
import datetime
import json
import re
import subprocess
import sys

# === BEGIN GENERATED DATA (generate_manifests.py) ===
SCHEMA_MAP_VERSION = 'o19map-1'
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
    'HRMCategory': 'reference',
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
    'SecurityToken': 'copy',
    'SentToPHRTracking': 'archive',
    'ServiceAccessToken': 'copy',
    'ServiceClient': 'copy',
    'ServiceRequestToken': 'copy',
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
    'consentType': 'reference',
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
    'ctl_document': 'merge',
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
    'lst_gender': 'copy',
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
    'property': 'copy',
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
    'secObjPrivilege': 'reference',
    'secObjectName': 'reference',
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
        'fileSignature': "`fileSignature` IS NOT NULL AND `fileSignature` <> ''",
    },
    'drugs': {
        'dispensingUnits': "`dispensingUnits` IS NOT NULL AND `dispensingUnits` <> ''",
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
    'born',
    'INTEGRATOR_',
    'MY_OSCAR',
    'MYOSCAR',
    'myoscar',
    'oscar_myoscar',
    'mymeds',
    'CBI_',
    'OLIS_',
    'olis_',
    'util.erx.',
    'clinicaid',
    'indivica',
    'consultation_indivica',
    'spire_',
    'redirectstudysite_',
    'sharingcenter',
    'cr_security',
    'RX3',
    'loginlogo',
    'logintext',
    'logintitle',
    'MYDRUGREF',
    'eaaps.',
    'health_tracker',
    'streethealth',
]
# === END GENERATED DATA ===

BLOCKER = "blocker"
ADVISORY = "advisory"
INFO = "info"

# Core-table inventory reported as sanity anchors for later row-parity.
INVENTORY_TABLES = [
    "demographic", "provider", "security", "appointment", "casemgmt_note",
    "document", "drugs", "hl7TextMessage", "eform_data", "preventions",
    "billing_on_cheader1", "tickler",
]

# UTF-8 bytes of a double-encoded latin1 lead byte ("A-tilde" = C3 83):
# their presence in narrative text is the classic OSCAR mojibake signature.
MOJIBAKE_HEX = "C383"


def finding(fid, severity, title, detail="", accept=None, data=None):
    f = {"id": fid, "severity": severity, "title": title, "detail": detail}
    if accept:
        f["accept"] = accept
    if data is not None:
        f["data"] = data
    return f


_UNESCAPE = {"n": "\n", "t": "\t", "r": "\r", "f": "\f"}


def _unescape_property(text):
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "\\" and i + 1 < n:
            nxt = text[i + 1]
            if nxt == "u" and re.match(r"[0-9A-Fa-f]{4}$", text[i + 2:i + 6]):
                out.append(chr(int(text[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(_UNESCAPE.get(nxt, nxt))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def parse_properties_text(text):
    """Active key/value pairs with java.util.Properties semantics ('=', ':'
    or whitespace separators, backslash line continuation, escapes,
    trailing whitespace preserved); last occurrence wins. Mirrors
    o19props.parse_properties_text (this file must stay standalone)."""
    props = {}
    logical = []
    for raw in text.splitlines():
        line = raw.lstrip()
        if not logical and (not line or line[0] in ("#", "!")):
            continue
        stripped = line.rstrip("\\")
        if (len(line) - len(stripped)) % 2 == 1:
            logical.append(stripped)
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


def _ident(name):
    """Backtick-quote an identifier so EVERY table name the dump carries
    can be counted — a vendor-fork table with an unusual name must be
    checked, never filtered out of the unknown-table blocker."""
    return "`" + name.replace("`", "``") + "`"


INTERACTIVE_PASSWORD_ARGS = ("-p", "--password")


def interactive_password_arg(mysql_args):
    """The client argument that would PROMPT for a password, if any: every
    preflight query is a fresh client process, so an interactive prompt
    would repeat dozens of times — the password must come from
    --mysql-password-file (MYSQL_PWD) or a client defaults file."""
    for a in mysql_args:
        if a in INTERACTIVE_PASSWORD_ARGS:
            return a
    return None


def make_cli_query(mysql_cmd, mysql_args, db, env=None):
    """Return query(sql) -> list of rows (lists of strings) via the CLI.
    env, when given, replaces the client's environment (used to hand the
    password over as MYSQL_PWD instead of argv)."""
    def query(sql):
        argv = [mysql_cmd] + list(mysql_args) + ["-N", "-B", "-e", sql, db]
        out = subprocess.check_output(argv, env=env)
        text = out.decode("utf-8", "replace")
        rows = []
        for line in text.splitlines():
            rows.append(line.split("\t"))
        return rows
    return query


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

    def count(table, where=None):
        """_count that FAILS CLOSED: an error is recorded (and becomes a
        no-go blocker below) and counts as 0 only for the purpose of not
        double-reporting the table."""
        n = _count(query, table, where)
        if isinstance(n, tuple):
            label = table if where is None else "{0} [{1}]".format(
                table, where[:60])
            query_errors[label] = n[1]
            return 0
        return n

    # --- province gate ----------------------------------------------------
    if province != "on":
        findings.append(finding(
            "province", BLOCKER,
            "province '{0}' is not supported yet".format(province),
            "Only the Ontario manifest is curated; the BC pass is tracked in "
            "the migration plan. No --accept flag exists for this."))

    # --- live table inventory --------------------------------------------
    if db_name:
        schema_expr = "'{0}'".format(db_name)
    else:
        schema_expr = "DATABASE()"
    tables = {}
    for row in query(
            "SELECT TABLE_NAME FROM information_schema.TABLES "
            "WHERE TABLE_SCHEMA = {0} AND TABLE_TYPE = 'BASE TABLE'"
            .format(schema_expr)):
        if row and row[0]:
            tables[row[0]] = True

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

    # --- B1: patient data in tables CARLOS does not have ------------------
    patient_rows = {}
    for t in PATIENT_DATA_TABLES:
        if t not in tables:
            continue
        n = count(t)
        if n > 0:
            patient_rows[t] = n
    if patient_rows:
        findings.append(finding(
            "B1-patient-data", BLOCKER,
            "patient data in {0} table(s) that do not exist in CARLOS"
            .format(len(patient_rows)),
            "These records become ARCHIVE-ONLY after migration (o19_archive "
            "schema + CSV export) — the forms/modules were removed from "
            "CARLOS. The clinic must sign this off.",
            accept="archived-forms", data=patient_rows))

    # --- archive-class config/log tables with rows (advisory) -------------
    archive_rows = {}
    olis_rows = {}
    for t, cls in KNOWN_TABLES.items():
        if cls != "archive" or t in PATIENT_DATA_TABLES or t not in tables:
            continue
        n = count(t)
        if n > 0:
            if t.upper().startswith("OLIS"):
                olis_rows[t] = n
            else:
                archive_rows[t] = n
    if archive_rows:
        findings.append(finding(
            "archive-config", ADVISORY,
            "{0} removed-module config/log table(s) become archive-only"
            .format(len(archive_rows)),
            data=archive_rows))
    if olis_rows:
        findings.append(finding(
            "olis-in-use", BLOCKER,
            "OLIS was configured on this system",
            "The OLIS module does not exist in CARLOS: lab querying via OLIS "
            "stops working at cutover. Tell the clinic before migrating.",
            accept="olis-gone", data=olis_rows))

    # --- B3: data in columns CARLOS dropped -------------------------------
    b3_hits = {}
    for t, cols in B3_FLAGGED_COLUMNS.items():
        if t not in tables:
            continue
        for col, predicate in cols.items():
            n = count(t, predicate)
            if n > 0:
                b3_hits["{0}.{1}".format(t, col)] = n
    if b3_hits:
        findings.append(finding(
            "B3-dropped-columns", BLOCKER,
            "data in {0} column(s) CARLOS removed".format(len(b3_hits)),
            "The clinic actively used a workflow whose column was dropped "
            "(values are preserved in o19_archive shadow tables).",
            accept="dropped-columns", data=b3_hits))

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
        # B4: encrypted casemgmt notes
        if properties.get("casemgmt.note.password.enabled", "") \
                .lower() in ("true", "yes", "on"):
            findings.append(finding(
                "B4-encrypted-notes", BLOCKER,
                "password-protected casemgmt notes are enabled",
                "Key handling for encrypted notes is outside the standard "
                "import path. No --accept flag exists for this."))
        dropped = {}
        for key in sorted(properties):
            for prefix in DROPPED_PROP_PREFIXES:
                if key.startswith(prefix) and not key.startswith("ldap."):
                    dropped.setdefault(prefix, []).append(key)
                    break
        if dropped:
            findings.append(finding(
                "dropped-properties", ADVISORY,
                "{0} configured key(s) belong to removed modules"
                .format(sum(len(v) for v in dropped.values())),
                "These oscar.properties settings have no CARLOS equivalent "
                "and will be itemized (not carried) by the props phase.",
                data=dropped))
    else:
        findings.append(finding(
            "no-properties", ADVISORY,
            "oscar.properties not provided",
            "Property-based checks (LDAP, encrypted notes, removed-module "
            "keys) were skipped — rerun with --properties for the full "
            "assessment."))

    # --- charset / mojibake sampling --------------------------------------
    mojibake = {}
    for t, cols in CHARSET_SCAN.items():
        if t not in tables:
            continue
        for col in cols:
            n = count(t, "HEX(`{0}`) LIKE '%{1}%'".format(col, MOJIBAKE_HEX))
            if n > 0:
                mojibake["{0}.{1}".format(t, col)] = n
    if mojibake:
        findings.append(finding(
            "charset-mojibake", ADVISORY,
            "double-encoded text detected in {0} column(s)"
            .format(len(mojibake)),
            "The ETL's charset repair handles this, gated by "
            "--accept charset-repair; text the repair cannot round-trip "
            "blocks there (B8).", data=mojibake))

    # --- column-level unknowns (import mode only) -------------------------
    if schema_map is not None:
        col_map = {}
        for row in query(
                "SELECT TABLE_NAME, COLUMN_NAME FROM "
                "information_schema.COLUMNS WHERE TABLE_SCHEMA = {0}"
                .format(schema_expr)):
            if len(row) == 2:
                col_map.setdefault(row[0], set()).add(row[1])
        unknown_cols = {}
        for t, entry in schema_map.TABLES.items():
            if entry.get("class") not in ("copy", "merge") or t not in col_map:
                continue
            renames = entry.get("renames", {})
            # MySQL column names are case-insensitive — fold case
            known = set(renames.get(c, c).lower()
                        for c in entry.get("cols", []))
            known.update(c.lower() for c in entry.get("dropped", {}))
            extra = sorted(c for c in col_map[t]
                           if c.lower() not in known)
            if extra:
                unknown_cols[t] = extra
        if unknown_cols:
            findings.append(finding(
                "B2-unknown-columns", BLOCKER,
                "{0} table(s) carry columns the manifest does not know"
                .format(len(unknown_cols)),
                "Vendor-fork columns: their data is captured to o19_archive "
                "shadow tables if accepted, never silently dropped.",
                accept="unknown-as-archive", data=unknown_cols))
    else:
        findings.append(finding(
            "column-checks-deferred", INFO,
            "column-level unknown detection runs in import mode",
            "The standalone assessment checks tables, flagged columns, "
            "properties and text encoding; full column inventory happens "
            "when carlos-ctl stages the dump."))

    # --- inventory (sanity anchors) ---------------------------------------
    inv = {}
    for t in INVENTORY_TABLES:
        if t in tables:
            inv[t] = count(t)
    try:
        size_rows = query(
            "SELECT ROUND(SUM(DATA_LENGTH + INDEX_LENGTH)/1048576) FROM "
            "information_schema.TABLES WHERE TABLE_SCHEMA = {0}"
            .format(schema_expr))
        inv["_database_mb"] = int(float(size_rows[0][0]))
    except Exception:
        pass
    inv["_tables"] = len(tables)
    findings.append(finding("inventory", INFO, "database inventory",
                            data=inv))

    # --- query errors: a check that could not run is a hard no-go --------
    if query_errors:
        findings.append(finding(
            "query-errors", BLOCKER,
            "{0} check(s) could not be completed".format(len(query_errors)),
            "A count failed (privilege, corrupt table, ...). An unreadable "
            "table is never treated as empty: fix the access and rerun. "
            "No --accept flag exists for this.", data=query_errors))

    # --- verdict ----------------------------------------------------------
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
        verdict, exit_code = "no-go", 2
    elif outstanding:
        verdict, exit_code = "go-with-acknowledgements", 1
    else:
        verdict, exit_code = "go", 0

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
    lines.append("")
    lines.append("Next step on a 'go': bundle the three inputs on this "
                 "server and ship them to the CARLOS host —")
    lines.append("  mysqldump --single-transaction --quick <db> | gzip "
                 "> o19.sql.gz")
    lines.append("  tar -C /var/lib/OscarDocument -czf o19-documents.tar.gz "
                 "<context-dir>")
    lines.append("  tar -czf - o19.sql.gz o19-documents.tar.gz "
                 "oscar.properties \\")
    lines.append("    | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt "
                 "-pass file:PASSFILE \\")
    lines.append("    -out o19-bundle.tar.gz.enc")
    lines.append("Migration output must receive a technical review before "
                 "clinical use.")
    return "\n".join(lines) + "\n"


def main(argv=None):
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
                    metavar="CLASS", help="acknowledge a blocker class "
                    "(repeatable)")
    ap.add_argument("--json", metavar="PATH",
                    help="also write the machine-readable report here")
    args = ap.parse_args(argv)

    if SCHEMA_MAP_VERSION == "unpopulated":
        print("ERROR: this copy of o19_preflight.py carries no generated "
              "manifest data;\nregenerate it with "
              "scripts/migration/o19/generate_manifests.py", file=sys.stderr)
        return 2

    props = None
    if args.properties:
        props = parse_properties(args.properties)

    bad = interactive_password_arg(args.mysql_arg)
    if bad:
        print("ERROR: '{0}' would prompt for the password on every query "
              "(each check runs a fresh client). Pass the password via "
              "--mysql-password-file PATH or "
              "--mysql-arg=--defaults-extra-file=PATH instead."
              .format(bad), file=sys.stderr)
        return 2
    env = None
    if args.mysql_password_file:
        import os
        with open(args.mysql_password_file, "rb") as fh:
            password = fh.read().decode("utf-8", "replace").rstrip("\r\n")
        env = dict(os.environ)
        env["MYSQL_PWD"] = password

    query = make_cli_query(args.mysql_cmd, args.mysql_arg, args.db, env=env)
    try:
        query("SELECT 1")
    except Exception as exc:
        print("ERROR: cannot query database '{0}': {1}"
              .format(args.db, exc), file=sys.stderr)
        return 2

    report = run_checks(query, properties=props, province=args.province,
                        accepted=args.accept)
    sys.stdout.write(render_text(report))
    if args.json:
        with open(args.json, "w") as fh:
            json.dump(report, fh, indent=1, sort_keys=True)
        print("json report written to " + args.json)
    return report["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
