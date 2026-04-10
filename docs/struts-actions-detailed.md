# CARLOS EMR Struts Actions Reference

## Overview

This document provides a comprehensive reference of all Struts actions in the CARLOS EMR system. These actions serve as the primary entry points for handling HTTP requests and coordinating business logic within the web application.

**Configuration**: Actions are defined across 17 domain-specific XML files (`struts-*.xml`) included from the parent `struts.xml`. See `docs/struts-actions-summary.md` for the modular architecture and module-to-file mapping.

## Statistics

- **Total Actions:** ~476
- **Module Files:** 17 (domain-specific)
- **Primary Functional Areas:**
  - Clinical workflows (encounters, measurements, prescriptions)
  - Administrative functions (billing, demographics, reporting)
  - Document management and lab integration
  - System configuration and user management

---

## Admin Module

Administrative functions for system configuration, user management, and maintenance tasks.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| admin/AuditLogPurge | io.github.carlos_emr.carlos.admin.web.AuditLogPurge2Action | Purges old audit log entries from the system |
| admin/Flowsheet | io.github.carlos_emr.carlos.flowsheet.Flowsheet2Action | Manages flowsheet templates and configurations |
| admin/ForwardingRules | io.github.carlos_emr.carlos.oscarLab.pageUtil.ForwardingRules2Action | Configures lab result forwarding rules |
| admin/GenerateTraceabilityReportAction | io.github.carlos_emr.carlos.admin.traceability.GenerateTraceabilityReport2Action | Generates system traceability reports for compliance |
| admin/GenerateTraceAction | io.github.carlos_emr.carlos.admin.traceability.GenerateTrace2Action | Creates audit trails for data changes |
| admin/GroupPreference | io.github.carlos_emr.carlos.commn.web.GroupPreference2Action | Manages user group preferences and settings |
| admin/GstControl | io.github.carlos_emr.carlos.billings.ca.on.administration.GstControl2Action | Controls GST/HST billing settings for Ontario |
| admin/ManageBillingReferral | io.github.carlos_emr.carlos.commn.web.BillingreferralEdit2Action | Manages billing referral configurations |
| admin/ManageClinic | io.github.carlos_emr.carlos.commn.web.ClinicManage2Action | Administers clinic information and settings |
| admin/manageCSSStyles | io.github.carlos_emr.carlos.billing.CA.ON.web.ManageCSS2Action | Manages custom CSS styles for billing forms |
| admin/ManageEmails | io.github.carlos_emr.carlos.email.admin.ManageEmails2Action | Configures email server settings and templates |
| admin/ManageFaxes | io.github.carlos_emr.carlos.fax.admin.ManageFaxes2Action | Manages fax server configurations |
| admin/ManageFax | io.github.carlos_emr.carlos.fax.admin.ConfigureFax2Action | Configures individual fax settings |
| admin/ManageSites | io.github.carlos_emr.carlos.commn.web.SitesManage2Action | Manages multi-site configurations |
| admin/MergeRecords | io.github.carlos_emr.carlos.demographic.pageUtil.DemographicMergeRecord2Action | Merges duplicate demographic records |
| admin/oscarStatus | io.github.carlos_emr.carlos.util.OscarStatus2Action | Displays system status and health information |
| admin/uploadEntryText | io.github.carlos_emr.carlos.login.UploadLoginText2Action | Uploads custom login page text |

## Appointment Module

Appointment scheduling and management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| appointment/appointmentTypeAction | io.github.carlos_emr.carlos.appt.web.AppointmentType2Action | Manages appointment types and durations |
| appointment/apptStatusSetting | io.github.carlos_emr.carlos.appt.status.web.AppointmentStatus2Action | Configures appointment status settings |
| appointment/printAppointmentReceiptAction | io.github.carlos_emr.carlos.report.pageUtil.PrintAppointmentReceipt2Action | Generates printable appointment receipts |

## Archive Module

Document and record archiving functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| ArchiveView | io.github.carlos_emr.carlos.casemgmt.web.ArchiveView2Action | Views archived case management records |

## Attach Module

Document attachment functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| attachDocs | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationAttachDocs2Action | Attaches documents to consultation requests |

## Billing Module

Comprehensive billing management for various Canadian provinces.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| billing/CA/BC/AddReferralDoc | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.AddReferralDoc2Action | Adds referral documents for BC billing |
| billing/CA/BC/associateCodesAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.AssociateCodes2Action | Associates billing codes with services in BC |
| billing/CA/BC/billingAddCode | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingAddCode2Action | Adds new billing codes for BC |
| billing/CA/BC/billingEditCode | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingEditCode2Action | Edits existing BC billing codes |
| billing/CA/BC/billingTeleplanCorrectionWCB | io.github.carlos_emr.carlos.billings.ca.bc.administration.TeleplanCorrectionActionWCB2Action | Processes WCB Teleplan billing corrections |
| billing/CA/BC/billingView | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingView2Action | Views BC billing records |
| billing/CA/BC/CreateBilling | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingCreateBilling2Action | Creates new billing records for BC |
| billing/CA/BC/createBillingReportAction | io.github.carlos_emr.carlos.billings.ca.bc.MSP.CreateBillingReport2Action | Generates BC MSP billing reports |
| billing/CA/BC/deleteServiceCodeAssoc | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.DeleteServiceCodeAssoc2Action | Deletes service code associations |
| billing/CA/BC/editServiceCodeAssocAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.EditServiceCodeAssoc2Action | Edits service code associations |
| billing/CA/BC/formwcb | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.WCBAction22Action | Processes WCB forms for BC |
| billing/CA/BC/GenerateTeleplanFile | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.GenerateTeleplanFile2Action | Generates Teleplan submission files |
| billing/CA/BC/ManageTeleplan | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ManageTeleplan2Action | Manages Teleplan billing configurations |
| billing/CA/BC/ProcessRemittance | io.github.carlos_emr.carlos.billings.ca.bc.MSP.GenTa2Action | Processes MSP remittance files |
| billing/CA/BC/receivePaymentAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ReceivePayment2Action | Records payment receipts |
| billing/CA/BC/reprocessBill | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingReProcessBill2Action | Reprocesses rejected bills |
| billing/CA/BC/saveAssocAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SaveAssoc2Action | Saves code associations |
| billing/CA/BC/SaveBilling | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSaveBilling2Action | Saves billing records |
| billing/CA/BC/saveBillingPreferencesAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SaveBillingPreferences2Action | Saves billing preferences |
| billing/CA/BC/showServiceCodeAssocs | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ShowServiceCodeAssocs2Action | Displays service code associations |
| billing/CA/BC/SimulateTeleplanFile | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SimulateTeleplanFile2Action | Simulates Teleplan file generation |
| billing/CA/BC/supServiceCodeAssocAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SupServiceCodeAssoc2Action | Manages superior service code associations |
| billing/CA/BC/UpdateBilling | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingUpdateBilling2Action | Updates existing billing records |
| billing/CA/BC/viewBillingPreferencesAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ViewBillingPreferences2Action | Views billing preferences |
| billing/CA/BC/viewformwcb | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ViewWCB2Action | Views WCB forms |
| billing/CA/BC/viewReceivePaymentAction | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ViewReceivePayment2Action | Views payment records |
| billing/CA/ON/ApplyPractitionerPremium | io.github.carlos_emr.carlos.commn.web.ApplyPractitionerPremium2Action | Applies practitioner premiums for Ontario |
| billing/CA/ON/BatchBill | io.github.carlos_emr.carlos.billing.CA.ON.web.BatchBill2Action | Processes batch billing for Ontario |
| billing/CA/ON/benefitScheduleChange | io.github.carlos_emr.carlos.billings.ca.on.OHIP.ScheduleOfBenefitsUpdate2Action | Updates OHIP benefit schedules |
| billing/CA/ON/benefitScheduleUpload | io.github.carlos_emr.carlos.billings.ca.on.OHIP.ScheduleOfBenefitsUpload2Action | Uploads OHIP benefit schedules |
| billing/CA/ON/billingON3rdPayments | io.github.carlos_emr.carlos.billing.CA.ON.web.BillingONPayments2Action | Manages third-party payments for Ontario |
| billing/CA/ON/BillingONCorrection | io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingCorrection2Action | Handles billing corrections for Ontario |
| billing/ca/on/DisplayInvoiceLogo | io.github.carlos_emr.carlos.billing.CA.ON.util.DisplayInvoiceLogo2Action | Displays invoice logos for Ontario |
| billing/CA/ON/endYearStatement | io.github.carlos_emr.carlos.billings.ca.on.pageUtil.PatientEndYearStatement2Action | Generates end-of-year patient statements |
| billing/CA/ON/managePaymentType | io.github.carlos_emr.carlos.billings.ca.on.pageUtil.PaymentType2Action | Manages payment types for Ontario |
| billing/CA/ON/moveMOHFiles | io.github.carlos_emr.carlos.billing.CA.ON.web.ArchiveMOHFile2Action | Archives MOH billing files |
| billing/CA/ON/moveMOHFiles | io.github.carlos_emr.carlos.billing.CA.ON.web.MoveMOHFiles2Action | Moves MOH billing files |
| BillingInvoice | io.github.carlos_emr.carlos.commn.web.BillingInvoice2Action | Generates billing invoices |
| BillingONReview | io.github.carlos_emr.carlos.commn.web.BillingONReview2Action | Reviews Ontario billing submissions |
| billing | io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.Billing2Action | Main billing interface |

## Case Management Module

Clinical case management and documentation.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| CaseManagementEntry | io.github.carlos_emr.carlos.casemgmt.web.CaseManagementEntry2Action | Creates new case management entries |
| CaseManagementView | io.github.carlos_emr.carlos.casemgmt.web.CaseManagementView2Action | Views case management records |
| casemgmt/ExtPrintRegistry | io.github.carlos_emr.carlos.casemgmt.web.ExtPrintRegistry2Action | Manages external print registry |
| casemgmt/NotePermissions | io.github.carlos_emr.carlos.casemgmt.web.NotePermissions2Action | Configures note access permissions |
| casemgmt/RegisterCppCode | io.github.carlos_emr.carlos.casemgmt.web.RegisterCppCode2Action | Registers CPP diagnostic codes |

## Client Module

Client and patient image management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| ClientImage | io.github.carlos_emr.carlos.casemgmt.web.ClientImage2Action | Manages client/patient images |

## Clinical Connect Module

Clinical Connect EHR integration has been removed and this module is deprecated; no Struts actions are currently available.

## Code Search Module

Medical code search functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| CodeSearch | io.github.carlos_emr.carlos.commn.web.CodeSearchService2Action | Searches medical diagnostic codes |

## Immunization Configuration Module

Immunization setup and configuration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| CreateImmunizationSetConfig | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetConfig2Action | Creates immunization set configurations |
| CreateInitImmunization | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctCreateImmunizationSetInit2Action | Initializes immunization configurations |
| DeleteImmunizationSets | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmDeleteImmunizationSet2Action | Deletes immunization sets |
| ImmunizationSetDisplay | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmImmunizationSetDisplay2Action | Displays immunization set configurations |

## CVC Module

Clinical validation and connectivity testing.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| cvc | io.github.carlos_emr.carlos.integration.born.CVCTester2Action | Tests BORN CVC integration connectivity |

## Default Module

Default encounter configurations.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| DefaultEncounterIssue | org.caisi.core.web.DefaultEncounterIssue2Action | Sets default encounter issues |

## Demographics Module

Patient demographic management and related functions.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| demographic/AddRelation | io.github.carlos_emr.carlos.demographic.pageUtil.AddDemographicRelationship2Action | Adds patient relationships |
| demographic/Contact | io.github.carlos_emr.carlos.commn.web.Contact2Action | Manages patient contact information |
| demographic/DeleteRelation | io.github.carlos_emr.carlos.demographic.pageUtil.DeleteDemographicRelationship2Action | Deletes patient relationships |
| demographic/DemographicExport | io.github.carlos_emr.carlos.demographic.pageUtil.DemographicExportAction42Action | Exports demographic data |
| demographic/eRourkeExport | io.github.carlos_emr.carlos.demographic.pageUtil.RourkeExport2Action | Exports Rourke assessment data |
| DemographicExtService | io.github.carlos_emr.carlos.commn.web.DemographicExtService2Action | Provides external demographic services |
| demographic/printClientLabLabelAction | io.github.carlos_emr.carlos.demographic.PrintClientLabLabel2Action | Prints client lab labels |
| demographic/printDemoAddressLabelAction | io.github.carlos_emr.carlos.demographic.PrintDemoAddressLabel2Action | Prints patient address labels |
| demographic/printDemoChartLabelAction | io.github.carlos_emr.carlos.demographic.PrintDemoChartLabel2Action | Prints patient chart labels |
| demographic/printDemoLabelAction | io.github.carlos_emr.carlos.demographic.PrintDemoLabel2Action | Prints general patient labels |
| demographic/SearchDemographic | io.github.carlos_emr.carlos.commn.web.SearchDemographicAutoComplete2Action | Provides demographic search autocomplete |
| demographicSupport | io.github.carlos_emr.carlos.commn.web.Demographic2Action | General demographic support functions |
| demographic/ValidateSwipeCard | io.github.carlos_emr.carlos.integration.mchcv.ValidateSwipeCard2Action | Validates health card swipe data |

## DHIR Module

Digital Health Immunization Repository integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| dhir/submit | io.github.carlos_emr.carlos.integration.dhir.SubmitImmunization2Action | Submits immunizations to DHIR |

## Document Management Module

Document management and processing functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| DocumentDescriptionTemplate | io.github.carlos_emr.carlos.www.provider.DocumentDescriptionTemplate2Action | Manages document description templates |
| documentManager/addDocumentType | io.github.carlos_emr.carlos.documentManager.actions.AddDocumentType2Action | Adds new document types |
| documentManager/addEditDocument | io.github.carlos_emr.carlos.documentManager.actions.AddEditDocument2Action | Adds or edits documents |
| documentManager/addEditHtml | io.github.carlos_emr.carlos.documentManager.actions.AddEditHtml2Action | Adds or edits HTML documents |
| documentManager/addLink | io.github.carlos_emr.carlos.documentManager.actions.AddEditHtml2Action | Adds document links |
| documentManager/changeDocStatus | io.github.carlos_emr.carlos.documentManager.actions.ChangeDocStatus2Action | Changes document status |
| documentManager/combinePDFs | io.github.carlos_emr.carlos.documentManager.actions.CombinePDF2Action | Combines multiple PDF documents |
| documentManager/documentUpload | io.github.carlos_emr.carlos.documentManager.actions.DocumentUpload2Action | Uploads documents to the system |
| documentManager/inboxManage | io.github.carlos_emr.carlos.documentManager.actions.DmsInboxManage2Action | Manages document inbox |
| documentManager/ManageDocument | io.github.carlos_emr.carlos.documentManager.actions.ManageDocument2Action | General document management |
| documentManager/SplitDocument | io.github.carlos_emr.carlos.documentManager.actions.SplitDocument2Action | Splits documents into parts |

## DX Code Search Module

Diagnostic code search functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| dxCodeSearchJSON | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxCodeSearchJSON2Action | Provides JSON-based diagnostic code search |

## E-Consult Module

Electronic consultation functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| econsult | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EConsult2Action | Manages electronic consultations |
| econsultSSOLogin | io.github.carlos_emr.carlos.login.SSOLogin2Action | Provides SSO login for e-consults |

## Edit Provider Module

Provider information editing functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| EditAddress | io.github.carlos_emr.carlos.providers.pageUtil.ProEditAddress2Action | Edits provider addresses |
| EditFaxNum | io.github.carlos_emr.carlos.providers.pageUtil.ProEditFaxNum2Action | Edits provider fax numbers |
| EditPhoneNum | io.github.carlos_emr.carlos.providers.pageUtil.ProEditPhoneNum2Action | Edits provider phone numbers |
| EditPrinter | io.github.carlos_emr.carlos.providers.pageUtil.ProEditPrinter2Action | Edits provider printer settings |

## EForm Module

Electronic forms management and processing.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| eform/addEForm | io.github.carlos_emr.carlos.eform.actions.AddEForm2Action | Adds new electronic forms |
| eform/addGroup | io.github.carlos_emr.carlos.eform.actions.AddGroup2Action | Adds form groups |
| eform/addToGroup | io.github.carlos_emr.carlos.eform.actions.AddToGroup2Action | Adds forms to groups |
| eform/attachDoc | io.github.carlos_emr.carlos.eform.EFormAttachDocs2Action | Attaches documents to forms |
| eform/delEForm | io.github.carlos_emr.carlos.eform.actions.DelEForm2Action | Deletes electronic forms |
| eform/deleteImage | io.github.carlos_emr.carlos.eform.actions.DelImage2Action | Deletes images from forms |
| eform/displayImage | io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action | Displays form images |
| eform/editForm | io.github.carlos_emr.carlos.eform.actions.HtmlEdit2Action | Edits HTML forms |
| eform/efmOpenEformByName | io.github.carlos_emr.carlos.eform.actions.OpenEFormByName2Action | Opens forms by name |
| eform/efmPrintPDF | io.github.carlos_emr.carlos.eform.actions.PrintPDF2Action | Prints forms as PDF |
| eform/eFormAttachmentForm | io.github.carlos_emr.carlos.eform.upload.UploadEFormAttachment2Action | Uploads form attachments |
| eform/FetchUpdatedData | io.github.carlos_emr.carlos.eform.actions.FetchUpdatedData2Action | Fetches updated form data |
| eform/imageUpload | io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action | Uploads images to forms |
| eform/logEformError | io.github.carlos_emr.carlos.eform.EformLogError2Action | Logs form errors |
| eform/manageEForm | io.github.carlos_emr.carlos.eform.actions.ManageEForm2Action | Manages electronic forms |
| eform/removeEForm | io.github.carlos_emr.carlos.eform.actions.RemEForm2Action | Removes forms |
| eform/restoreEForm | io.github.carlos_emr.carlos.eform.actions.RestoreEForm2Action | Restores deleted forms |
| eforms/delGroup | io.github.carlos_emr.carlos.eform.actions.DeleteGroup2Action | Deletes form groups |
| eforms/removeFromGroup | io.github.carlos_emr.carlos.eform.actions.RemoveFromGroup2Action | Removes forms from groups |
| eform/unRemoveEForm | io.github.carlos_emr.carlos.eform.actions.UnRemEForm2Action | Un-removes forms |
| eform/uploadHtml | io.github.carlos_emr.carlos.eform.upload.HtmlUpload2Action | Uploads HTML forms |

## Email Module

Email management and communication.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| email/emailComposeAction | io.github.carlos_emr.carlos.email.action.EmailCompose2Action | Composes new emails |
| email/emailSendAction | io.github.carlos_emr.carlos.email.action.EmailSend2Action | Sends composed emails |

## Signature Module

Provider signature management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| EnterSignature | io.github.carlos_emr.carlos.providers.pageUtil.ProEditSignature2Action | Enters or edits provider signatures |

## Episode Module

Clinical episode management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Episode | io.github.carlos_emr.carlos.commn.web.Episode2Action | Manages clinical episodes |

## Facility Module

Facility management and messaging.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| FacilityManager | io.github.carlos_emr.carlos.facility.FacilityManager2Action | Manages facility information |
| FacilityMessage | org.caisi.core.web.FacilityMessage2Action | Handles facility messaging |

## Fax Module

Fax functionality and management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| fax/faxAction | io.github.carlos_emr.carlos.fax.action.Fax2Action | Manages fax operations |

## Form Module

Clinical forms and data import functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| form/AddRHWorkFlow | io.github.carlos_emr.carlos.form.pageUtil.FrmFormAddRHWorkFlow2Action | Adds reproductive health workflows |
| form/BCAR2020 | io.github.carlos_emr.carlos.form.FrmBCAR20202Action | Processes BC AR 2020 forms |
| formBPMH | io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.web.BpmhFormRetrieve2Action | Retrieves Best Possible Medication History forms |
| formeCARES | io.github.carlos_emr.carlos.form.eCARES.EcaresForm2Action | Processes eCARES forms |
| form/formname | io.github.carlos_emr.carlos.form.Frm2Action | Generic form processor |
| form/forwardshortcutname | io.github.carlos_emr.carlos.form.pageUtil.FormForward2Action | Forwards form shortcuts |
| form/importLogDownload | io.github.carlos_emr.carlos.demographic.pageUtil.ImportLogDownload2Action | Downloads import logs |
| form/importUpload | io.github.carlos_emr.carlos.demographic.pageUtil.ImportDemographicDataAction42Action | Uploads demographic import data |
| form/RHPrevention | io.github.carlos_emr.carlos.form.pageUtil.FrmFormRHPrevention2Action | Manages reproductive health prevention forms |
| form/select | io.github.carlos_emr.carlos.form.pageUtil.FrmSelect2Action | Selects forms |
| form/SetupForm | io.github.carlos_emr.carlos.form.pageUtil.FrmSetupForm2Action | Sets up new forms |
| form/setupSelect | io.github.carlos_emr.carlos.form.pageUtil.FrmSetupSelect2Action | Sets up form selection |
| form/SubmitForm | io.github.carlos_emr.carlos.form.pageUtil.FrmForm2Action | Submits completed forms |
| form/xmlUpload | io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action | Uploads XML form data |

## Professional Specialist Module

Professional specialist registry.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| getProfessionalSpecialist | io.github.carlos_emr.carlos.contactRegistry.ProfessionalSpecialist2Action | Retrieves professional specialist information |

## Home Module

System home page functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Home | io.github.carlos_emr.carlos.web.PMmodule.Home2Action | Displays system home page |

## Hospital Report Manager Module

Hospital report management and integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| hospitalReportManager/Display | io.github.carlos_emr.carlos.hospitalReportManager.HRMDisplayReport2Action | Displays hospital reports |
| hospitalReportManager/HRMDownloadFile | io.github.carlos_emr.carlos.hospitalReportManager.HRMDownloadFile2Action | Downloads HRM files |
| hospitalReportManager/hrmKeyUploader | io.github.carlos_emr.carlos.hospitalReportManager.HRMUploadKey2Action | Uploads HRM encryption keys |
| hospitalReportManager/hrm | io.github.carlos_emr.carlos.hospitalReportManager.v2018.HRM2Action | Main HRM interface |
| hospitalReportManager/HRMPreferences | io.github.carlos_emr.carlos.hospitalReportManager.HRMPreferences2Action | Manages HRM preferences |
| hospitalReportManager/Mapping | io.github.carlos_emr.carlos.hospitalReportManager.HRMMapping2Action | Maps HRM data fields |
| hospitalReportManager/Modify | io.github.carlos_emr.carlos.hospitalReportManager.HRMModifyDocument2Action | Modifies HRM documents |
| hospitalReportManager/PrintHRMReport | io.github.carlos_emr.carlos.hospitalReportManager.PrintHRMReport2Action | Prints HRM reports |
| hospitalReportManager/Statement | io.github.carlos_emr.carlos.hospitalReportManager.HRMStatementModify2Action | Modifies HRM statements |
| hospitalReportManager/UploadLab | io.github.carlos_emr.carlos.hospitalReportManager.HRMUploadLab2Action | Uploads lab results to HRM |

## Indivica Module

Health card search functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| indivica/HCSearch | io.github.carlos_emr.carlos.commn.web.HealthCardSearch2Action | Searches health card database |

## Infirm Module

Infirmary management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| infirm | org.caisi.core.web.Infirm2Action | Manages infirmary operations |

## Integrator Module

System integration functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| integrator/IntegratorPush | io.github.carlos_emr.carlos.commn.web.IntegratorPush2Action | Pushes data to external integrators |

## Issue Admin Module

Issue administration functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| issueAdmin | org.caisi.core.web.IssueAdmin2Action | Administers clinical issues |

## Lab Module

Laboratory result management and integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| lab/CA/ALL/createLabLabel | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.CreateLabLabel2Action | Creates lab labels for HL7 results |
| lab/CA/ALL/Forward | io.github.carlos_emr.carlos.mds.pageUtil.ReportReassign2Action | Forwards lab reports to providers |
| lab/CA/ALL/insideLabUpload | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action | Uploads inside lab results |
| lab/CA/ALL/oruR01Upload | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.OruR01Upload2Action | Uploads ORU R01 lab messages |
| lab/CA/ALL/PrintOLISLab | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.PrintOLISLab2Action | Prints individual OLIS lab results |
| lab/CA/ALL/PrintOLIS | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.PrintOLISLabs2Action | Prints multiple OLIS lab results |
| lab/CA/ALL/PrintPDF | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.PrintLabs2Action | Prints lab results as PDF |
| lab/CA/ALL/UnlinkDemographic | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.UnlinkDemographic2Action | Unlinks demographics from lab results |
| lab/CA/BC/Forward | io.github.carlos_emr.carlos.mds.pageUtil.ReportReassign2Action | Forwards BC lab reports |
| lab/CA/ON/Forward | io.github.carlos_emr.carlos.mds.pageUtil.ReportReassign2Action | Forwards Ontario lab reports |
| lab/CMLlabUpload | io.github.carlos_emr.carlos.lab.ca.on.CML.Upload.LabUpload2Action | Uploads CML lab results |
| lab/DownloadEmbeddedDocumentFromLab | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.DownloadEmbeddedDocumentFromLab2Action | Downloads embedded lab documents |
| lab/labUpload | io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil.LabUpload2Action | Uploads PathNet lab results |
| lab/newLabUpload | io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabUpload2Action | Uploads new lab results |

## Login Module

User authentication and session management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| login | io.github.carlos_emr.carlos.login.Login2Action | Processes user login |
| login/recordLogin | io.github.carlos_emr.carlos.login.LoginAgreement2Action | Records login agreements |
| logout | io.github.carlos_emr.carlos.login.Logout2Action | Processes user logout |

## Lookup Module

System lookup table management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| lookupListManagerAction | io.github.carlos_emr.carlos.admin.lookUpLists.LookupListManager2Action | Manages lookup list configurations |
| Lookup/LookupCodeEdit | io.github.carlos_emr.carlos.www.lookup.LookupCodeEdit2Action | Edits lookup codes |
| Lookup/LookupCodeList | io.github.carlos_emr.carlos.www.lookup.LookupCodeList2Action | Lists lookup codes |
| Lookup/LookupList | io.github.carlos_emr.carlos.www.lookup.LookupList2Action | Manages lookup lists |
| Lookup/LookupTableList | io.github.carlos_emr.carlos.www.lookup.LookupTableList2Action | Lists lookup tables |

## MCEDT Module

Ministry Claims Electronic Data Transfer integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| mcedt/addUpload | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action | Adds MCEDT uploads |
| mcedt/autoUpload | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action | Performs automatic MCEDT uploads |
| mcedt/download | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Download2Action | Downloads MCEDT files |
| mcedt/info | io.github.carlos_emr.carlos.integration.mcedt.Info2Action | Displays MCEDT information |
| mcedt/kaiautodl | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Download2Action | Kai automatic downloads |
| mcedt/kaichpass | io.github.carlos_emr.carlos.integration.mcedt.mailbox.User2Action | Changes Kai passwords |
| mcedt/kaimcedt | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Resource2Action | Manages Kai MCEDT resources |
| mcedt/mcedt | io.github.carlos_emr.carlos.integration.mcedt.Resource2Action | Main MCEDT interface |
| mcedt/openAutoUpload | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Resource2Action | Opens automatic upload interface |
| mcedt/resourceInfo | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Info2Action | Displays MCEDT resource information |
| mcedt/reSubmit | io.github.carlos_emr.carlos.integration.mcedt.mailbox.ReSubmit2Action | Resubmits MCEDT data |
| mcedt/update | io.github.carlos_emr.carlos.integration.mcedt.Update2Action | Updates MCEDT configurations |
| mcedt/upload | io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action | Uploads MCEDT files |
| mcedt/uploads | io.github.carlos_emr.carlos.integration.mcedt.Upload2Action | Manages MCEDT uploads |

## Measurement Module

Clinical measurement data management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| MeasurementHL7Uploader | io.github.carlos_emr.carlos.encounter.oscarMeasurements.hl7.MeasurementHL7Uploader2Action | Uploads measurements via HL7 |

## Notification Module

Provider notification system.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| notification/create | io.github.carlos_emr.carlos.commn.web.ProviderNotification2Action | Creates provider notifications |

## OLIS Module

Ontario Laboratory Information System integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| olis/AddToInbox | io.github.carlos_emr.carlos.olis.OLISAddToInbox2Action | Adds OLIS results to inbox |
| olis/Preferences | io.github.carlos_emr.carlos.olis.OLISPreferences2Action | Manages OLIS preferences |
| olis/Results | io.github.carlos_emr.carlos.olis.OLISResults2Action | Retrieves OLIS lab results |
| olis/Search | io.github.carlos_emr.carlos.olis.OLISSearch2Action | Searches OLIS database |
| olis/UploadSimulationData | io.github.carlos_emr.carlos.olis.OLISUploadSimulationData2Action | Uploads OLIS simulation data |

## Oscar Billing Module

OSCAR billing system functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarBilling/DocumentErrorReportUpload | io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingDocumentErrorReportUpload2Action | Uploads billing error reports |

## Oscar Chart Module

Chart printing functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| OscarChartPrint | io.github.carlos_emr.carlos.casemgmt.web.EChartPrint2Action | Prints patient charts |

## Oscar Consultation Module

Consultation request management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarConsultationRequest/consultationClinicalData | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationClinicalData2Action | Manages consultation clinical data |

## Oscar Encounter Module

Clinical encounter management and workflow.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| encounter/AddDepartment | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConAddDepartment2Action | Adds consultation departments |
| encounter/AddInstitution | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConAddInstitution2Action | Adds consultation institutions |
| encounter/AddService | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConAddService2Action | Adds consultation services |
| encounter/AddSpecialist | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConAddSpecialist2Action | Adds specialists |
| encounter/decisionSupport/guidelineAction | io.github.carlos_emr.carlos.decisionSupport.web.DSGuideline2Action | Processes decision support guidelines |
| encounter/DelService | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConDeleteServices2Action | Deletes consultation services |
| encounter/displayAllergy | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayAllergy2Action | Displays patient allergies in encounter |
| encounter/displayAppointmentHistory | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayAppointmentHistory2Action | Displays appointment history |
| encounter/displayBilling | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayBilling2Action | Displays billing information |
| encounter/displayConReport | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayConReport2Action | Displays consultation reports |
| encounter/displayConsultation | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayConsult2Action | Displays consultations |
| encounter/displayContacts | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayContacts2Action | Displays patient contacts |
| encounter/displayDecisionSupportAlerts | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayDecisionSupportAlerts2Action | Displays decision support alerts |
| encounter/displayDiagrams | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayDiagram2Action | Displays clinical diagrams |
| encounter/displayDisease | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayDx2Action | Displays diagnoses |
| encounter/displayDocuments | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayDocs2Action | Displays encounter documents |
| encounter/displayEconsultation | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayEconsult2Action | Displays e-consultations |
| encounter/displayEForms | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayEForm2Action | Displays electronic forms |
| encounter/displayEHR | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayEHR2Action | Displays electronic health records |
| encounter/displayEpisodes | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayEpisode2Action | Displays clinical episodes |
| encounter/displayExaminationHistory | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayExaminationHistory2Action | Displays examination history |
| encounter/displayForms | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayForm2Action | Displays clinical forms |
| encounter/displayHRM | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayHRM2Action | Displays hospital reports |
| encounter/displayIssues | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayIssues2Action | Displays patient issues |
| encounter/displayLabs | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayLabAction22Action | Displays lab results |
| encounter/displayMacro | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayMacro2Action | Displays text macros |
| encounter/displayMeasurements | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayMeasurements2Action | Displays clinical measurements |
| encounter/displayMessages | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayMsg2Action | Displays messages |
| encounter/displayOcularProcedure | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayOcularProcedure2Action | Displays ocular procedures |
| encounter/displayPhotos | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayPhotos2Action | Displays patient photos |
| encounter/displayPregnancies | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayPregnancy2Action | Displays pregnancy information |
| encounter/displayPrevention | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayPrevention2Action | Displays prevention records |
| encounter/displayResolvedIssues | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayResolvedIssues2Action | Displays resolved issues |
| encounter/displayRx | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayRx2Action | Displays prescriptions |
| encounter/displaySpecsHistory | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplaySpecsHistory2Action | Displays prescription history |
| encounter/displayTickler | io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayTickler2Action | Displays ticklers |
| encounter/EditDepartments | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConEditDepartments2Action | Edits consultation departments |
| encounter/EditInstitutions | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConEditInstitutions2Action | Edits consultation institutions |
| encounter/EditSpecialists | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConEditSpecialists2Action | Edits specialists |
| encounter/EnableConRequestResponse | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConEnableReqResp2Action | Enables consultation request responses |
| encounter/eRefer | io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.ERefer2Action | Processes electronic referrals |
| encounter/FormUpdate | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.FormUpdate2Action | Updates measurement forms |
| encounter/GraphMeasurements | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementGraphAction22Action | Graphs clinical measurements |
| encounter/immunization/config/CreateImmunizationSetConfig | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetConfig2Action | Creates immunization configurations |
| encounter/immunization/config/CreateInitImmunization | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetInit2Action | Initializes immunization sets |
| encounter/immunization/config/deleteImmunizationSet | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmInitConfigDeleteImmuSet2Action | Deletes immunization sets |
| encounter/immunization/config/DeleteImmunizationSets | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmDeleteImmunizationSet2Action | Deletes multiple immunization sets |
| encounter/immunization/config/ImmunizationSetDisplay | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmImmunizationSetDisplay2Action | Displays immunization sets |
| encounter/immunization/config/initConfig | io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmInitConfig2Action | Initializes immunization config |
| encounter/immunization/deleteSchedule | io.github.carlos_emr.carlos.encounter.immunization.pageUtil.EctImmDeleteImmSchedule2Action | Deletes immunization schedules |
| encounter/immunization/initSchedule | io.github.carlos_emr.carlos.encounter.immunization.pageUtil.EctImmInitSchedule2Action | Initializes immunization schedules |
| encounter/immunization/loadConfig | io.github.carlos_emr.carlos.encounter.immunization.pageUtil.EctImmLoadConfig2Action | Loads immunization configurations |
| encounter/immunization/loadSchedule | io.github.carlos_emr.carlos.encounter.immunization.pageUtil.EctImmLoadSchedule2Action | Loads immunization schedules |
| encounter/immunization/saveConfig | io.github.carlos_emr.carlos.encounter.immunization.pageUtil.EctImmSaveConfig2Action | Saves immunization configurations |
| encounter/immunization/saveSchedule | io.github.carlos_emr.carlos.encounter.immunization.pageUtil.EctImmSaveSchedule2Action | Saves immunization schedules |
| encounter/IncomingConsultation | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctIncomingConsultation2Action | Processes incoming consultations |
| encounter/IncomingEncounter | io.github.carlos_emr.carlos.encounter.pageUtil.EctIncomingEncounter2Action | Processes incoming encounters |
| encounter/InsertTemplate | io.github.carlos_emr.carlos.encounter.pageUtil.EctInsertTemplate2Action | Inserts encounter templates |
| encounter/MeasurementData | io.github.carlos_emr.carlos.measurements.web.MeasurementData2Action | Manages measurement data |
| encounter/Measurements2 | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action | Manages encounter measurements |
| encounter/Measurements | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action | Manages clinical measurements |
| encounter/oscarConsultation/printAttached | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationPrintDocs2Action | Prints attached consultation documents |
| encounter/oscarConsultationRequest/ConsultationFormFax | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormFax2Action | Faxes consultation forms |
| encounter/oscarConsultationRequest/printPdf2 | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestPrintAction22Action | Prints consultation PDFs |
| encounter/oscarMeasurements/AddMeasurementGroup | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementGroup2Action | Adds measurement groups |
| encounter/oscarMeasurements/AddMeasurementMap | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementMap2Action | Adds measurement mappings |
| encounter/oscarMeasurements/AddMeasurementStyleSheet | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementStyleSheet2Action | Adds measurement stylesheets |
| encounter/oscarMeasurements/AddMeasurementType | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementType2Action | Adds measurement types |
| encounter/oscarMeasurements/AddMeasuringInstruction | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasuringInstruction2Action | Adds measuring instructions |
| encounter/oscarMeasurements/adminFlowsheet/FlowSheetCustomAction | io.github.carlos_emr.carlos.commn.web.FlowSheetCustom2Action | Manages custom flowsheets |
| encounter/oscarMeasurements/DefineNewMeasurementGroup | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDefineNewMeasurementGroup2Action | Defines new measurement groups |
| encounter/oscarMeasurements/DeleteData2 | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDeleteData2Action | Deletes measurement data |
| encounter/oscarMeasurements/DeleteData | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDeleteData2Action | Deletes measurement data |
| encounter/oscarMeasurements/DeleteMeasurementStyleSheet | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDeleteMeasurementStyleSheet2Action | Deletes measurement stylesheets |
| encounter/oscarMeasurements/DeleteMeasurementTypes | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDeleteMeasurementTypes2Action | Deletes measurement types |
| encounter/oscarMeasurements/EditMeasurementGroup | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctEditMeasurementGroup2Action | Edits measurement groups |
| encounter/oscarMeasurements/EditMeasurementStyle | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctEditMeasurementStyle2Action | Edits measurement styles |
| encounter/oscarMeasurements/FlowSheetDrugAction | io.github.carlos_emr.carlos.commn.web.FlowSheetDrug2Action | Manages flowsheet drug information |
| encounter/oscarMeasurements/NewMeasurementMap | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementMap2Action | Creates new measurement maps |
| encounter/oscarMeasurements/RemapMeasurementMap | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctRemoveMeasurementMap2Action | Remaps measurement mappings |
| encounter/oscarMeasurements/RemoveMeasurementMap | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctRemoveMeasurementMap2Action | Removes measurement mappings |
| encounter/oscarMeasurements/SelectMeasurementGroup | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSelectMeasurementGroup2Action | Selects measurement groups |
| encounter/oscarMeasurements/SetupAddMeasurementGroup | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupAddMeasurementGroup2Action | Sets up new measurement groups |
| encounter/oscarMeasurements/SetupAddMeasurementType | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupAddMeasurementType2Action | Sets up new measurement types |
| encounter/oscarMeasurements/SetupAddMeasuringInstruction | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupAddMeasuringInstruction2Action | Sets up measuring instructions |
| encounter/oscarMeasurements/SetupDisplayHistory | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupDisplayHistory2Action | Sets up measurement history display |
| encounter/oscarMeasurements/SetupDisplayMeasurementStyleSheet | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupDisplayMeasurementStyleSheet2Action | Sets up measurement stylesheet display |
| encounter/oscarMeasurements/SetupDisplayMeasurementTypes | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupDisplayMeasurementTypes2Action | Sets up measurement types display |
| encounter/oscarMeasurements/SetupEditMeasurementGroup | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupEditMeasurementGroup2Action | Sets up measurement group editing |
| encounter/oscarMeasurements/SetupGroupList | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupGroupList2Action | Sets up measurement group lists |
| encounter/oscarMeasurements/SetupHistoryIndex | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupHistoryIndex2Action | Sets up measurement history index |
| encounter/oscarMeasurements/SetupMeasurements | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupMeasurements2Action | Sets up measurements interface |
| encounter/oscarMeasurements/SetupStyleSheetList | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSetupStyleSheetList2Action | Sets up stylesheet list |
| encounter/oscarMeasurements/TrackerSlimUpdate | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.FormUpdate2Action | Updates slim tracker forms |
| encounter/oscarMeasurements/TrackerUpdate | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.FormUpdate2Action | Updates tracker forms |
| encounter/RequestConsultation | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequest2Action | Requests consultations |
| encounter/SaveEncounter2 | io.github.carlos_emr.carlos.encounter.pageUtil.EctSaveEncounter2Action | Saves clinical encounters |
| encounter/ShowAllInstitutions | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConShowAllInstitutions2Action | Shows all consultation institutions |
| encounter/ShowAllServices | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConShowAllServices2Action | Shows all consultation services |
| encounter/UpdateInstitutionDepartment | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayInstitution2Action | Updates institution departments |
| encounter/UpdateServiceSpecialists | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayService2Action | Updates service specialists |
| encounter/ViewAttachment | io.github.carlos_emr.carlos.encounter.pageUtil.EctViewAttachment2Action | Views encounter attachments |
| encounter/ViewConsultation | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequests2Action | Views consultation requests |
| encounter/ViewRequest | io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewRequest2Action | Views consultation requests |

## Oscar MDS Module

Medical Data Services functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarMDS/FileLabs | io.github.carlos_emr.carlos.lab.pageUtil.FileLabs2Action | Files lab results |
| oscarMDS/ForwardingRules | io.github.carlos_emr.carlos.lab.pageUtil.ForwardingRules2Action | Manages lab forwarding rules |
| oscarMDS/Forward | io.github.carlos_emr.carlos.mdss.pageUtil.ReportReassign2Action | Forwards MDS reports |
| oscarMDS/PatientMatch | io.github.carlos_emr.carlos.mds.pageUtil.PatientMatch2Action | Matches patients to reports |
| oscarMDS/ReportReassign | io.github.carlos_emr.carlos.mds.pageUtil.ReportReassign2Action | Reassigns reports to providers |
| oscarMDS/RunMacro | io.github.carlos_emr.carlos.mds.pageUtil.ReportMacro2Action | Runs report processing macros |
| oscarMDS/SearchPatient | io.github.carlos_emr.carlos.mds.pageUtil.SearchPatient2Action | Searches for patients |
| oscarMDS/SendMRP | io.github.carlos_emr.carlos.mds.pageUtil.SendMostResponProv2Action | Sends to most responsible provider |
| oscarMDS/SubmitLab | io.github.carlos_emr.carlos.lab.ca.all.web.SubmitLabByForm2Action | Submits lab results by form |
| oscarMDS/UpdateStatus | io.github.carlos_emr.carlos.mds.pageUtil.ReportStatusUpdate2Action | Updates report status |

## Oscar Measurement Module

Clinical measurement functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarMeasurement/AddShortMeasurement | io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddShortMeasurement2Action | Adds short-form measurements |

## Oscar Messenger Module

Internal messaging system.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| messenger/AddGroup | io.github.carlos_emr.carlos.messenger.config.pageUtil.MsgMessengerCreateGroup2Action | Adds messenger groups |
| messenger/ClearMessage | io.github.carlos_emr.carlos.messenger.pageUtil.MsgClearMessage2Action | Clears messages |
| messenger/CreateMessage | io.github.carlos_emr.carlos.messenger.pageUtil.MsgCreateMessage2Action | Creates new messages |
| messenger/DisplayDemographicMessages | io.github.carlos_emr.carlos.messenger.pageUtil.MsgDisplayDemographicMessages2Action | Displays demographic messages |
| messenger/DisplayMessages | io.github.carlos_emr.carlos.messenger.pageUtil.MsgDisplayMessages2Action | Displays messages |
| messenger/Doc2PDF | io.github.carlos_emr.carlos.messenger.pageUtil.MsgAttachPDF2Action | Converts documents to PDF |
| messenger/HandleMessages | io.github.carlos_emr.carlos.messenger.pageUtil.MsgHandleMessages2Action | Handles message processing |
| messenger/ImportDemographic | io.github.carlos_emr.carlos.messenger.pageUtil.ImportDemographic2Action | Imports demographic data |
| messenger | io.github.carlos_emr.carlos.messenger.config.pageUtil.MsgMessengerAdmin2Action | Administers messenger system |
| messenger/ProcessDoc2PDF | io.github.carlos_emr.carlos.messenger.pageUtil.MsgDoc2PDF2Action | Processes document to PDF conversion |
| messenger/ReDisplayMessages | io.github.carlos_emr.carlos.messenger.pageUtil.MsgReDisplayMessages2Action | Re-displays messages |
| messenger/SendDemoMessage | io.github.carlos_emr.carlos.messenger.pageUtil.MsgSendDemographicMessage2Action | Sends demographic messages |
| messenger/SendMessage | io.github.carlos_emr.carlos.messenger.pageUtil.MsgSendMessage2Action | Sends messages |
| messenger/Transfer/Proceed | io.github.carlos_emr.carlos.messenger.pageUtil.MsgProceed2Action | Proceeds with message transfer |
| messenger/ViewAttach | io.github.carlos_emr.carlos.messenger.pageUtil.MsgViewAttachment2Action | Views message attachments |
| messenger/ViewMessage | io.github.carlos_emr.carlos.messenger.pageUtil.MsgViewMessage2Action | Views messages |
| messenger/ViewPDFAttach | io.github.carlos_emr.carlos.messenger.pageUtil.MsgViewPDFAttachment2Action | Views PDF attachments |
| messenger/ViewPDFFile | io.github.carlos_emr.carlos.messenger.pageUtil.MsgViewPDF2Action | Views PDF files |
| messenger/WriteToEncounter | io.github.carlos_emr.carlos.messenger.pageUtil.MsgWriteToEncounter2Action | Writes messages to encounters |

## Oscar Prevention Module

Preventive care management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarPrevention/AddPrevention | io.github.carlos_emr.carlos.prevention.pageUtil.AddPrevention2Action | Adds prevention records |
| oscarPrevention/PreventionReport | io.github.carlos_emr.carlos.prevention.pageUtil.PreventionReport2Action | Generates prevention reports |
| oscarPrevention/printPrevention | io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrint2Action | Prints prevention records |

## Oscar Report Module

Comprehensive reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarReport/FluBilling | io.github.carlos_emr.carlos.report.pageUtil.RptFluBilling2Action | Generates flu billing reports |
| oscarReport/obec | io.github.carlos_emr.carlos.report.pageUtil.Obec2Action | Generates OBEC reports |
| oscarReport/oscarMeasurements/InitializeFrequencyOfRelevantTestsCDMReport | io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil.RptInitializeFrequencyOfRelevantTestsCDMReport2Action | Initializes CDM test frequency reports |
| oscarReport/oscarMeasurements/InitializePatientsInAbnormalRangeCDMReport | io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil.RptInitializePatientsInAbnormalRangeCDMReport2Action | Initializes abnormal range CDM reports |
| oscarReport/oscarMeasurements/InitializePatientsMetGuidelineCDMReport | io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil.RptInitializePatientsMetGuidelineCDMReport2Action | Initializes guideline met CDM reports |
| oscarReport/oscarMeasurements/SelectCDMReport | io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil.RptSelectCDMReport2Action | Selects CDM reports |
| oscarReport/oscarMeasurements/SetupSelectCDMReport | io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil.RptSetupSelectCDMReport2Action | Sets up CDM report selection |
| oscarReport/reportByTemplate/actions/addGroup | io.github.carlos_emr.carlos.report.reportByTemplate.actions.RBTAddGroup2Action | Adds template report groups |
| oscarReport/reportByTemplate/actions/delGroup | io.github.carlos_emr.carlos.report.reportByTemplate.actions.RBTDeleteGroup2Action | Deletes template report groups |
| oscarReport/reportByTemplate/actions/rbtAddToGroup | io.github.carlos_emr.carlos.report.reportByTemplate.actions.RBTAddToGroup2Action | Adds templates to groups |
| oscarReport/reportByTemplate/actions/remFromGroup | io.github.carlos_emr.carlos.report.reportByTemplate.actions.RBTRemoveFromGroup2Action | Removes templates from groups |
| oscarReport/reportByTemplate/actions/tempInGroup | io.github.carlos_emr.carlos.report.reportByTemplate.actions.RBTGetTemplatesInGroup2Action | Gets templates in groups |
| oscarReport/reportByTemplate/addEditTemplatesAction | io.github.carlos_emr.carlos.report.reportByTemplate.actions.ManageTemplates2Action | Manages report templates |
| oscarReport/reportByTemplate/exportTemplateAction | io.github.carlos_emr.carlos.report.reportByTemplate.actions.ExportTemplate2Action | Exports report templates |
| oscarReport/reportByTemplate/generateOutFilesAction | io.github.carlos_emr.carlos.report.reportByTemplate.actions.GenerateOutFiles2Action | Generates output files |
| oscarReport/reportByTemplate/GenerateReportAction | io.github.carlos_emr.carlos.report.reportByTemplate.actions.GenerateReport2Action | Generates template-based reports |
| oscarReport/reportByTemplate/rbtGroup | io.github.carlos_emr.carlos.report.reportByTemplate.actions.RBTGetGroup2Action | Gets report template groups |
| oscarReport/reportByTemplate/uploadTemplates | io.github.carlos_emr.carlos.report.reportByTemplate.actions.UploadTemplates2Action | Uploads report templates |
| oscarReport/RptByExample | io.github.carlos_emr.carlos.report.pageUtil.RptByExample2Action | Generates reports by example |
| oscarReport/RptByExamplesAllFavorites | io.github.carlos_emr.carlos.report.pageUtil.RptByExamplesAllFavorites2Action | Shows all favorite example reports |
| oscarReport/RptByExamplesFavorite | io.github.carlos_emr.carlos.report.pageUtil.RptByExamplesFavorite2Action | Manages favorite example reports |
| oscarReport/RptViewAllQueryByExamples | io.github.carlos_emr.carlos.report.pageUtil.RptViewAllQueryByExamples2Action | Views all query examples |
| oscarReport/ShowConsult | io.github.carlos_emr.carlos.report.pageUtil.RptShowConsult2Action | Shows consultation reports |

## Oscar Research Module

Medical research and diagnostic code management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarResearch/oscarDxResearch/dxResearchCodeSearch | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxResearchCodeSearch2Action | Searches diagnostic research codes |
| oscarResearch/oscarDxResearch/dxResearchLoadAssociations | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxResearchLoadAssociations2Action | Loads research code associations |
| oscarResearch/oscarDxResearch/dxResearchLoadQuickListItems | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxResearchLoadQuickListItems2Action | Loads quick list items |
| oscarResearch/oscarDxResearch/dxResearchLoadQuickList | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxResearchLoadQuickList2Action | Loads research quick lists |
| oscarResearch/oscarDxResearch/dxResearch | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxResearch2Action | Main diagnostic research interface |
| oscarResearch/oscarDxResearch/dxResearchUpdate | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxResearchUpdate2Action | Updates research data |
| oscarResearch/oscarDxResearch/dxResearchUpdateQuickList | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxResearchUpdateQuickList2Action | Updates research quick lists |
| oscarResearch/oscarDxResearch/setupDxResearch | io.github.carlos_emr.carlos.dxResearch.pageUtil.dxSetupResearch2Action | Sets up diagnostic research |

## Oscar Rx Module

Prescription and medication management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarRx/addAllergy2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddAllergy2Action | Adds patient allergies |
| oscarRx/addAllergy | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddAllergy2Action | Adds medication allergies |
| oscarRx/addFavorite2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddFavorite2Action | Adds prescription favorites |
| oscarRx/addFavoriteStaticScript | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddFavorite2Action | Adds static script favorites |
| oscarRx/addFavoriteViewScript | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddFavorite2Action | Adds view script favorites |
| oscarRx/addFavoriteWriteScript | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddFavorite2Action | Adds write script favorites |
| oscarRx/addReaction2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddReaction2Action | Adds drug reactions |
| oscarRx/addReaction | io.github.carlos_emr.carlos.prescript.pageUtil.RxAddReaction2Action | Adds allergic reactions |
| oscarRx/chooseDrug | io.github.carlos_emr.carlos.prescript.pageUtil.RxChooseDrug2Action | Selects medications |
| oscarRx/choosePatient | io.github.carlos_emr.carlos.prescript.pageUtil.RxChoosePatient2Action | Selects patients for prescriptions |
| oscarRx/clearPending | io.github.carlos_emr.carlos.prescript.pageUtil.RxClearPending2Action | Clears pending prescriptions |
| oscarRx/copyFavorite2 | io.github.carlos_emr.carlos.prescript.web.CopyFavorites2Action | Copies prescription favorites |
| oscarRx/copyFavorite | io.github.carlos_emr.carlos.prescript.web.CopyFavorites2Action | Copies favorite prescriptions |
| oscarRx/deleteAllergy2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteAllergy2Action | Deletes patient allergies |
| oscarRx/deleteAllergy | io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteAllergy2Action | Deletes allergy records |
| oscarRx/deleteFavorite2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteFavorite2Action | Deletes prescription favorites |
| oscarRx/deleteFavorite | io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteFavorite2Action | Deletes favorite prescriptions |
| oscarRx/deleteRx | io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteRx2Action | Deletes prescriptions |
| oscarRx/drugInfo | io.github.carlos_emr.carlos.prescript.pageUtil.RxDrugInfo2Action | Displays drug information |
| oscarRx/GetmyDrugrefInfo | io.github.carlos_emr.carlos.prescript.pageUtil.RxMyDrugrefInfo2Action | Gets drug reference information |
| oscarRx/GetRxPageSizeInfo | io.github.carlos_emr.carlos.prescript.pageUtil.RxRxPageSizeInfo2Action | Gets prescription page size info |
| oscarRx/hideCpp | io.github.carlos_emr.carlos.prescript.web.RxHideCpp2Action | Hides CPP information |
| oscarRx/managePharmacy2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxManagePharmacy2Action | Manages pharmacy information |
| oscarRx/managePharmacy | io.github.carlos_emr.carlos.prescript.pageUtil.RxManagePharmacy2Action | Manages pharmacy settings |
| oscarRx/reorderDrug | io.github.carlos_emr.carlos.prescript.web.RxReorder2Action | Reorders medications |
| oscarRx/rePrescribe2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxRePrescribe2Action | Re-prescribes medications |
| oscarRx/rePrescribe | io.github.carlos_emr.carlos.prescript.pageUtil.RxRePrescribe2Action | Repeats prescriptions |
| oscarRx/RxReason | io.github.carlos_emr.carlos.prescript.pageUtil.RxReason2Action | Manages prescription reasons |
| oscarRx/rxStashDelete | io.github.carlos_emr.carlos.prescript.pageUtil.RxStash2Action | Deletes prescription stash |
| oscarRx/searchAllergy2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxSearchAllergy2Action | Searches for allergies |
| oscarRx/searchAllergy | io.github.carlos_emr.carlos.prescript.pageUtil.RxSearchAllergy2Action | Searches allergy database |
| oscarRx/searchDrug | io.github.carlos_emr.carlos.prescript.pageUtil.RxSearchDrug2Action | Searches for medications |
| oscarRx/showAllergy | io.github.carlos_emr.carlos.prescript.pageUtil.RxShowAllergy2Action | Displays patient allergies |
| oscarRx/stash | io.github.carlos_emr.carlos.prescript.pageUtil.RxStash2Action | Manages prescription stash |
| oscarRx/updateDrugrefDB | io.github.carlos_emr.carlos.prescript.pageUtil.RxUpdateDrugref2Action | Updates drug reference database |
| oscarRx/updateFavorite2 | io.github.carlos_emr.carlos.prescript.pageUtil.RxUpdateFavorite2Action | Updates prescription favorites |
| oscarRx/updateFavorite | io.github.carlos_emr.carlos.prescript.pageUtil.RxUpdateFavorite2Action | Updates favorite prescriptions |
| oscarRx/UpdateScript | io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteScript2Action | Updates prescription scripts |
| oscarRx/useFavorite | io.github.carlos_emr.carlos.prescript.pageUtil.RxUseFavorite2Action | Uses favorite prescriptions |
| oscarRx/viewScript | io.github.carlos_emr.carlos.prescript.pageUtil.RxViewScript2Action | Views prescription scripts |
| oscarRx/WriteScript | io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteScript2Action | Writes new prescriptions |
| oscarRx/writeScript | io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteScript2Action | Creates prescription scripts |
| oscarRx/WriteToEncounter | io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteToEncounter2Action | Writes prescriptions to encounters |

## Oscar Waiting List Module

Patient waiting list management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| waitinglist/SetupDisplayPatientWaitingList | io.github.carlos_emr.carlos.waitinglist.pageUtil.WLSetupDisplayPatientWaitingList2Action | Sets up patient waiting list display |
| waitinglist/SetupDisplayWaitingList | io.github.carlos_emr.carlos.waitinglist.pageUtil.WLSetupDisplayWaitingList2Action | Sets up waiting list display |
| waitinglist/WLEditWaitingListNameAction | io.github.carlos_emr.carlos.waitinglist.pageUtil.WLEditWaitingListName2Action | Edits waiting list names |

## Page Monitoring Module

System monitoring functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PageMonitoringService | io.github.carlos_emr.carlos.commn.web.PageMonitoring2Action | Monitors page performance |

## PM Module

Program Management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PMmodule/Admin/DefaultRoleAccess | io.github.carlos_emr.carlos.admin.web.PMmodule.DefaultRoleAccess2Action | Manages default role access |
| PMmodule/Admin/SysAdmin | io.github.carlos_emr.carlos.admin.web.PMmodule.AdminHome2Action | System administration interface |
| PMmodule/AgencyManager | io.github.carlos_emr.carlos.admin.web.PMmodule.AgencyManager2Action | Manages agencies |
| PMmodule/AllVacancies | io.github.carlos_emr.carlos.web.PMmodule.AllWaitingList2Action | Views all program vacancies |
| PMmodule/ClientManager | io.github.carlos_emr.carlos.web.PMmodule.ClientManager2Action | Manages program clients |
| PMmodule/ClientSearch2 | io.github.carlos_emr.carlos.web.PMmodule.ClientSearchAction22Action | Searches for clients |
| PMmodule/FacilityManager | io.github.carlos_emr.carlos.admin.web.PMmodule.FacilityManager2Action | Manages program facilities |
| PMmodule/HealthSafety | io.github.carlos_emr.carlos.PMmodule.web.HealthSafety2Action | Manages health and safety |
| PMmodule/ProgramManager | io.github.carlos_emr.carlos.admin.web.PMmodule.ProgramManager2Action | Manages programs |
| PMmodule/ProgramManagerView | io.github.carlos_emr.carlos.admin.web.PMmodule.ProgramManagerView2Action | Views program management |
| PMmodule/ProviderInfo | io.github.carlos_emr.carlos.web.PMmodule.ProviderInfo2Action | Displays provider information |
| PMmodule/ProviderSearch | io.github.carlos_emr.carlos.web.PMmodule.ProviderSearch2Action | Searches for providers |
| PMmodule/Reports/BasicReport | io.github.carlos_emr.carlos.reports.web.PMmodule.BasicReport2Action | Generates basic reports |
| PMmodule/Reports/ClientListsReport | io.github.carlos_emr.carlos.reports.web.PMmodule.ClientListsReport2Action | Generates client list reports |
| PMmodule/Reports/ProgramActivityReport | io.github.carlos_emr.carlos.reports.web.PMmodule.ActivityReport2Action | Generates program activity reports |
| PMmodule/StaffManager | io.github.carlos_emr.carlos.admin.web.PMmodule.StaffManager2Action | Manages program staff |
| PMmodule/VacancyClientMatch | io.github.carlos_emr.carlos.web.PMmodule.VacancyClientMatch2Action | Matches clients to vacancies |

## Population Module

Population reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PopulationReport | io.github.carlos_emr.carlos.commn.web.PopulationReport2Action | Generates population reports |

## Pregnancy Module

Pregnancy management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Pregnancy | io.github.carlos_emr.carlos.commn.web.Pregnancy2Action | Manages pregnancy records |

## Preview Module

Document preview functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| previewDocs | io.github.carlos_emr.carlos.documentManager.actions.DocumentPreview2Action | Previews documents |

## Printer Module

Printer management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PrinterList | io.github.carlos_emr.carlos.printer.PrinterList2Action | Lists available printers |
| printReferralLabelAction | io.github.carlos_emr.carlos.commn.web.PrintReferralLabel2Action | Prints referral labels |

## Provider Module

Provider management and preferences.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| provider/CppPreferences | io.github.carlos_emr.carlos.www.provider.CppPreferences2Action | Manages CPP preferences |
| provider/OlisPreferences | io.github.carlos_emr.carlos.www.provider.OlisPreferences2Action | Manages OLIS preferences |
| provider/rxInteractionWarningLevel | io.github.carlos_emr.carlos.www.provider.ProviderRxInteractionWarningLevel2Action | Sets drug interaction warning levels |
| provider/SearchProvider | io.github.carlos_emr.carlos.commn.web.SearchProviderAutoComplete2Action | Provides provider search autocomplete |
| Provider/showPersonal | io.github.carlos_emr.carlos.www.provider.DisplayPersonalInfoAppointment2Action | Shows provider personal information |
| provider/UserPreference | io.github.carlos_emr.carlos.www.provider.UserPreference2Action | Manages user preferences |

## Quick Billing Module

Quick billing functionality for BC.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| quickBillingBC | io.github.carlos_emr.carlos.billings.ca.bc.quickbilling.QuickBillingBC2Action | Quick billing interface for BC |

## Renal Module

Renal care management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| renal/CkdDSA | io.github.carlos_emr.carlos.renal.web.CkdDSA2Action | Chronic kidney disease decision support |
| renal/Renal | io.github.carlos_emr.carlos.renal.web.Renal2Action | Renal care management |

## Report Module

General reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| report/CreateDemographicSet | io.github.carlos_emr.carlos.report.pageUtil.RptCreateDemographicSet2Action | Creates demographic report sets |
| report/DeleteDemographicReport | io.github.carlos_emr.carlos.report.pageUtil.RptDemographQueryFavouriteDelete2Action | Deletes demographic reports |
| report/DeleteLetter | io.github.carlos_emr.carlos.report.pageUtil.DeletePatientLetters2Action | Deletes patient letters |
| report/DemographicReport | io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Action | Generates demographic reports |
| report/DemographicSetEdit | io.github.carlos_emr.carlos.report.pageUtil.DemographicSetEdit2Action | Edits demographic sets |
| report/DownloadLetter | io.github.carlos_emr.carlos.report.pageUtil.DownloadPatientLetters2Action | Downloads patient letters |
| report/DxresearchReport | io.github.carlos_emr.carlos.commn.web.DxresearchReport2Action | Generates diagnostic research reports |
| report/GenerateEnvelopes | io.github.carlos_emr.carlos.report.pageUtil.GenerateEnvelopes2Action | Generates mailing envelopes |
| report/GenerateLetters | io.github.carlos_emr.carlos.report.pageUtil.GeneratePatientLetters2Action | Generates patient letters |
| report/GenerateSpreadsheet | io.github.carlos_emr.carlos.report.pageUtil.GeneratePatientSpreadSheetList2Action | Generates patient spreadsheets |
| report/ManageLetters | io.github.carlos_emr.carlos.report.pageUtil.ManagePatientLetters2Action | Manages patient letters |
| report/printLabDaySheetAction | io.github.carlos_emr.carlos.report.pageUtil.printLabDaySheet2Action | Prints lab day sheets |
| report/RemoveClinicalReport | io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil.RemoveClinicalReportFromHistory2Action | Removes clinical reports from history |
| report/SetEligibility | io.github.carlos_emr.carlos.report.pageUtil.DemographicSetEligibility2Action | Sets demographic eligibility |

## Run Clinical Report Module

Clinical report execution.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| RunClinicalReport | io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil.RunClinicalReport2Action | Runs clinical reports |

## Save Quick Billing Module

Quick billing save functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| saveQuickBillingBC | io.github.carlos_emr.carlos.billings.ca.bc.quickbilling.QuickBillingBCSave2Action | Saves BC quick billing |

## Save Work View Module

Work view management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| saveWorkView | io.github.carlos_emr.carlos.www.provider.ProviderView2Action | Saves provider work views |

## Scratch Module

Scratch pad functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Scratch | io.github.carlos_emr.carlos.scratch.Scratch2Action | Manages scratch pad notes |

## Search Professional Module

Professional specialist search.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| searchProfessionalSpecialist | io.github.carlos_emr.carlos.contactRegistry.ProfessionalSpecialist2Action | Searches professional specialists |

## Security Module

Security and authentication functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| securityRecord/mfa | io.github.carlos_emr.carlos.security.MfaActions2Action | Manages multi-factor authentication |

## Set Provider Module

Provider configuration functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| setProviderColour | io.github.carlos_emr.carlos.providers.pageUtil.ProEditColour2Action | Sets provider color preferences |
| setProviderStaleDate | io.github.carlos_emr.carlos.www.provider.ProviderProperty2Action | Sets provider stale date |
| setTicklerPreferences | io.github.carlos_emr.carlos.www.provider.ProviderProperty2Action | Sets tickler preferences |

## Shelter Module

Shelter selection functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|

## SSO Module

Single Sign-On functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| ssoLogin | io.github.carlos_emr.carlos.login.SSOLogin2Action | Processes SSO login |

## System Message Module

System messaging functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| SystemMessage | org.caisi.core.web.SystemMessage2Action | Manages system messages |

## Tickler Module

Tickler management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| tickler/AddTickler | io.github.carlos_emr.carlos.tickler.pageUtil.AddTickler2Action | Adds new ticklers |
| tickler/EditTickler | io.github.carlos_emr.carlos.tickler.pageUtil.EditTickler2Action | Edits existing ticklers |
| tickler/EditTicklerTextSuggest | io.github.carlos_emr.carlos.tickler.pageUtil.EditTickler2Action | Edits tickler text suggestions |
| tickler/ForwardDemographicTickler | io.github.carlos_emr.carlos.tickler.pageUtil.ForwardDemographicTickler2Action | Forwards demographic ticklers |

## Vaccine Module

Vaccine reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| VaccineProviderReport | io.github.carlos_emr.carlos.vaccine.VaccineProviderReport2Action | Generates vaccine provider reports |

## Web Dashboard Module

Web-based dashboard functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| web/dashboard/display/AssignTickler | io.github.carlos_emr.carlos.dashboard.admin.AssignTickler2Action | Assigns ticklers from dashboard |
| web/dashboard/display/BulkPatientAction | io.github.carlos_emr.carlos.dashboard.admin.BulkPatientDashboard2Action | Performs bulk patient actions |
| web/dashboard/display/DashboardDisplay | io.github.carlos_emr.carlos.dashboard.display.DisplayDashboard2Action | Displays main dashboard |
| web/dashboard/display/DisplayIndicator | io.github.carlos_emr.carlos.dashboard.display.DisplayIndicator2Action | Displays dashboard indicators |
| web/dashboard/display/DrilldownDisplay | io.github.carlos_emr.carlos.dashboard.display.DisplayDrilldown2Action | Displays dashboard drilldown data |
| web/dashboard/display/ExportResults | io.github.carlos_emr.carlos.dashboard.admin.ExportResults2Action | Exports dashboard results |
| web/dashboard/OutcomesDashboard | io.github.carlos_emr.carlos.integration.dashboard.OutcomesDashboard2Action | Displays outcomes dashboard |

---

## Summary

This comprehensive reference includes all 507 Struts actions organized into 29 functional modules. Each action is documented with its purpose and functionality based on naming conventions and class structure. The actions cover the full spectrum of EMR functionality including clinical workflows, administrative tasks, billing operations, reporting capabilities, and system integrations.

The modular organization reflects OSCAR's comprehensive approach to healthcare management, supporting everything from basic patient demographics to complex clinical decision support and inter-system data exchange.