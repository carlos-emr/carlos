# OpenO EMR Struts Actions Reference

## Overview

This document provides a comprehensive reference of all Struts actions in the OpenO EMR system. These actions serve as the primary entry points for handling HTTP requests and coordinating business logic within the web application.

## Statistics

- **Total Actions:** 507
- **Modules:** 29
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
| admin/AuditLogPurge | ca.openosp.openo.admin.web.AuditLogPurge2Action | Purges old audit log entries from the system |
| admin/Flowsheet | ca.openosp.openo.flowsheet.Flowsheet2Action | Manages flowsheet templates and configurations |
| admin/ForwardingRules | ca.openosp.openo.oscarLab.pageUtil.ForwardingRules2Action | Configures lab result forwarding rules |
| admin/GenerateTraceabilityReportAction | ca.openosp.openo.admin.traceability.GenerateTraceabilityReport2Action | Generates system traceability reports for compliance |
| admin/GenerateTraceAction | ca.openosp.openo.admin.traceability.GenerateTrace2Action | Creates audit trails for data changes |
| admin/GroupPreference | ca.openosp.openo.commn.web.GroupPreference2Action | Manages user group preferences and settings |
| admin/GstControl | ca.openosp.openo.billings.ca.on.administration.GstControl2Action | Controls GST/HST billing settings for Ontario |
| admin/ManageBillingReferral | ca.openosp.openo.commn.web.BillingreferralEdit2Action | Manages billing referral configurations |
| admin/ManageClinic | ca.openosp.openo.commn.web.ClinicManage2Action | Administers clinic information and settings |
| admin/manageCSSStyles | ca.openosp.openo.billing.CA.ON.web.ManageCSS2Action | Manages custom CSS styles for billing forms |
| admin/ManageEmails | ca.openosp.openo.email.admin.ManageEmails2Action | Configures email server settings and templates |
| admin/ManageFaxes | ca.openosp.openo.fax.admin.ManageFaxes2Action | Manages fax server configurations |
| admin/ManageFax | ca.openosp.openo.fax.admin.ConfigureFax2Action | Configures individual fax settings |
| admin/ManageSites | ca.openosp.openo.commn.web.SitesManage2Action | Manages multi-site configurations |
| admin/MergeRecords | ca.openosp.openo.demographic.pageUtil.DemographicMergeRecord2Action | Merges duplicate demographic records |
| admin/oncallClinic | ca.openosp.openo.admin.reports.SaveOnCallClinic2Action | Manages on-call clinic schedules |
| admin/oscarStatus | ca.openosp.openo.util.OscarStatus2Action | Displays system status and health information |
| admin/uploadEntryText | ca.openosp.openo.login.UploadLoginText2Action | Uploads custom login page text |

## Appointment Module

Appointment scheduling and management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| appointment/appointmentTypeAction | ca.openosp.openo.appt.web.AppointmentType2Action | Manages appointment types and durations |
| appointment/apptStatusSetting | ca.openosp.openo.appt.status.web.AppointmentStatus2Action | Configures appointment status settings |
| appointment/printAppointmentReceiptAction | ca.openosp.openo.report.pageUtil.PrintAppointmentReceipt2Action | Generates printable appointment receipts |

## Archive Module

Document and record archiving functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| ArchiveView | ca.openosp.openo.casemgmt.web.ArchiveView2Action | Views archived case management records |

## Attach Module

Document attachment functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| attachDocs | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.ConsultationAttachDocs2Action | Attaches documents to consultation requests |

## Billing Module

Comprehensive billing management for various Canadian provinces.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| billing/CA/BC/AddReferralDoc | ca.openosp.openo.billings.ca.bc.pageUtil.AddReferralDoc2Action | Adds referral documents for BC billing |
| billing/CA/BC/associateCodesAction | ca.openosp.openo.billings.ca.bc.pageUtil.AssociateCodes2Action | Associates billing codes with services in BC |
| billing/CA/BC/billingAddCode | ca.openosp.openo.billings.ca.bc.pageUtil.BillingAddCode2Action | Adds new billing codes for BC |
| billing/CA/BC/billingEditCode | ca.openosp.openo.billings.ca.bc.pageUtil.BillingEditCode2Action | Edits existing BC billing codes |
| billing/CA/BC/billingTeleplanCorrectionWCB | ca.openosp.openo.billings.ca.bc.administration.TeleplanCorrectionActionWCB2Action | Processes WCB Teleplan billing corrections |
| billing/CA/BC/billingView | ca.openosp.openo.billings.ca.bc.pageUtil.BillingView2Action | Views BC billing records |
| billing/CA/BC/CreateBilling | ca.openosp.openo.billings.ca.bc.pageUtil.BillingCreateBilling2Action | Creates new billing records for BC |
| billing/CA/BC/createBillingReportAction | ca.openosp.openo.billings.ca.bc.MSP.CreateBillingReport2Action | Generates BC MSP billing reports |
| billing/CA/BC/deleteServiceCodeAssoc | ca.openosp.openo.billings.ca.bc.pageUtil.DeleteServiceCodeAssoc2Action | Deletes service code associations |
| billing/CA/BC/editServiceCodeAssocAction | ca.openosp.openo.billings.ca.bc.pageUtil.EditServiceCodeAssoc2Action | Edits service code associations |
| billing/CA/BC/formwcb | ca.openosp.openo.billings.ca.bc.pageUtil.WCBAction22Action | Processes WCB forms for BC |
| billing/CA/BC/GenerateTeleplanFile | ca.openosp.openo.billings.ca.bc.pageUtil.GenerateTeleplanFile2Action | Generates Teleplan submission files |
| billing/CA/BC/ManageTeleplan | ca.openosp.openo.billings.ca.bc.pageUtil.ManageTeleplan2Action | Manages Teleplan billing configurations |
| billing/CA/BC/ProcessRemittance | ca.openosp.openo.billings.ca.bc.MSP.GenTa2Action | Processes MSP remittance files |
| billing/CA/BC/receivePaymentAction | ca.openosp.openo.billings.ca.bc.pageUtil.ReceivePayment2Action | Records payment receipts |
| billing/CA/BC/reprocessBill | ca.openosp.openo.billings.ca.bc.pageUtil.BillingReProcessBill2Action | Reprocesses rejected bills |
| billing/CA/BC/saveAssocAction | ca.openosp.openo.billings.ca.bc.pageUtil.SaveAssoc2Action | Saves code associations |
| billing/CA/BC/SaveBilling | ca.openosp.openo.billings.ca.bc.pageUtil.BillingSaveBilling2Action | Saves billing records |
| billing/CA/BC/saveBillingPreferencesAction | ca.openosp.openo.billings.ca.bc.pageUtil.SaveBillingPreferences2Action | Saves billing preferences |
| billing/CA/BC/showServiceCodeAssocs | ca.openosp.openo.billings.ca.bc.pageUtil.ShowServiceCodeAssocs2Action | Displays service code associations |
| billing/CA/BC/SimulateTeleplanFile | ca.openosp.openo.billings.ca.bc.pageUtil.SimulateTeleplanFile2Action | Simulates Teleplan file generation |
| billing/CA/BC/supServiceCodeAssocAction | ca.openosp.openo.billings.ca.bc.pageUtil.SupServiceCodeAssoc2Action | Manages superior service code associations |
| billing/CA/BC/UpdateBilling | ca.openosp.openo.billings.ca.bc.pageUtil.BillingUpdateBilling2Action | Updates existing billing records |
| billing/CA/BC/viewBillingPreferencesAction | ca.openosp.openo.billings.ca.bc.pageUtil.ViewBillingPreferences2Action | Views billing preferences |
| billing/CA/BC/viewformwcb | ca.openosp.openo.billings.ca.bc.pageUtil.ViewWCB2Action | Views WCB forms |
| billing/CA/BC/viewReceivePaymentAction | ca.openosp.openo.billings.ca.bc.pageUtil.ViewReceivePayment2Action | Views payment records |
| billing/CA/ON/ApplyPractitionerPremium | ca.openosp.openo.commn.web.ApplyPractitionerPremium2Action | Applies practitioner premiums for Ontario |
| billing/CA/ON/BatchBill | ca.openosp.openo.billing.CA.ON.web.BatchBill2Action | Processes batch billing for Ontario |
| billing/CA/ON/benefitScheduleChange | ca.openosp.openo.billings.ca.on.OHIP.ScheduleOfBenefitsUpdate2Action | Updates OHIP benefit schedules |
| billing/CA/ON/benefitScheduleUpload | ca.openosp.openo.billings.ca.on.OHIP.ScheduleOfBenefitsUpload2Action | Uploads OHIP benefit schedules |
| billing/CA/ON/billingON3rdPayments | ca.openosp.openo.billing.CA.ON.web.BillingONPayments2Action | Manages third-party payments for Ontario |
| billing/CA/ON/BillingONCorrection | ca.openosp.openo.billings.ca.on.pageUtil.BillingCorrection2Action | Handles billing corrections for Ontario |
| billing/ca/on/DisplayInvoiceLogo | ca.openosp.openo.billing.CA.ON.util.DisplayInvoiceLogo2Action | Displays invoice logos for Ontario |
| billing/CA/ON/endYearStatement | ca.openosp.openo.billings.ca.on.pageUtil.PatientEndYearStatement2Action | Generates end-of-year patient statements |
| billing/CA/ON/managePaymentType | ca.openosp.openo.billings.ca.on.pageUtil.PaymentType2Action | Manages payment types for Ontario |
| billing/CA/ON/moveMOHFiles | ca.openosp.openo.billing.CA.ON.web.ArchiveMOHFile2Action | Archives MOH billing files |
| billing/CA/ON/moveMOHFiles | ca.openosp.openo.billing.CA.ON.web.MoveMOHFiles2Action | Moves MOH billing files |
| BillingInvoice | ca.openosp.openo.commn.web.BillingInvoice2Action | Generates billing invoices |
| BillingONReview | ca.openosp.openo.commn.web.BillingONReview2Action | Reviews Ontario billing submissions |
| billing | ca.openosp.openo.billings.ca.bc.pageUtil.Billing2Action | Main billing interface |

## Caseload Module

Patient caseload management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| caseload/CaseloadContent | ca.openosp.openo.caseload.CaseloadContent2Action | Manages patient caseload content |

## Case Management Module

Clinical case management and documentation.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| CaseManagementEntry | ca.openosp.openo.casemgmt.web.CaseManagementEntry2Action | Creates new case management entries |
| CaseManagementView | ca.openosp.openo.casemgmt.web.CaseManagementView2Action | Views case management records |
| casemgmt/ExtPrintRegistry | ca.openosp.openo.casemgmt.web.ExtPrintRegistry2Action | Manages external print registry |
| casemgmt/NotePermissions | ca.openosp.openo.casemgmt.web.NotePermissions2Action | Configures note access permissions |
| casemgmt/RegisterCppCode | ca.openosp.openo.casemgmt.web.RegisterCppCode2Action | Registers CPP diagnostic codes |

## Client Module

Client and patient image management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| ClientImage | ca.openosp.openo.casemgmt.web.ClientImage2Action | Manages client/patient images |

## Clinical Connect Module

Clinical Connect EHR integration has been removed and this module is deprecated; no Struts actions are currently available.

## Code Search Module

Medical code search functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| CodeSearch | ca.openosp.openo.commn.web.CodeSearchService2Action | Searches medical diagnostic codes |

## Immunization Configuration Module

Immunization setup and configuration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| CreateImmunizationSetConfig | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetConfig2Action | Creates immunization set configurations |
| CreateInitImmunization | ca.openosp.openo.encounter.immunization.config.pageUtil.EctCreateImmunizationSetInit2Action | Initializes immunization configurations |
| DeleteImmunizationSets | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmDeleteImmunizationSet2Action | Deletes immunization sets |
| ImmunizationSetDisplay | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmImmunizationSetDisplay2Action | Displays immunization set configurations |

## CVC Module

Clinical validation and connectivity testing.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| cvc | ca.openosp.openo.integration.born.CVCTester2Action | Tests BORN CVC integration connectivity |

## Default Module

Default encounter configurations.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| DefaultEncounterIssue | org.caisi.core.web.DefaultEncounterIssue2Action | Sets default encounter issues |

## Demographics Module

Patient demographic management and related functions.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| demographic/AddRelation | ca.openosp.openo.demographic.pageUtil.AddDemographicRelationship2Action | Adds patient relationships |
| demographic/cihiExportOMD4 | ca.openosp.openo.demographic.pageUtil.CihiExport2Action | Exports CIHI OMD4 data |
| demographic/cihiExportPHC_VRS | ca.openosp.openo.demographic.pageUtil.CihiExportPHC_VRS2Action | Exports PHC VRS data to CIHI |
| demographic/Contact | ca.openosp.openo.commn.web.Contact2Action | Manages patient contact information |
| demographic/DeleteRelation | ca.openosp.openo.demographic.pageUtil.DeleteDemographicRelationship2Action | Deletes patient relationships |
| demographic/DemographicExport | ca.openosp.openo.demographic.pageUtil.DemographicExportAction42Action | Exports demographic data |
| demographic/eRourkeExport | ca.openosp.openo.demographic.pageUtil.RourkeExport2Action | Exports Rourke assessment data |
| DemographicExtService | ca.openosp.openo.commn.web.DemographicExtService2Action | Provides external demographic services |
| demographic/printClientLabLabelAction | ca.openosp.openo.demographic.PrintClientLabLabel2Action | Prints client lab labels |
| demographic/printDemoAddressLabelAction | ca.openosp.openo.demographic.PrintDemoAddressLabel2Action | Prints patient address labels |
| demographic/printDemoChartLabelAction | ca.openosp.openo.demographic.PrintDemoChartLabel2Action | Prints patient chart labels |
| demographic/printDemoLabelAction | ca.openosp.openo.demographic.PrintDemoLabel2Action | Prints general patient labels |
| demographic/SearchDemographic | ca.openosp.openo.commn.web.SearchDemographicAutoComplete2Action | Provides demographic search autocomplete |
| demographicSupport | ca.openosp.openo.commn.web.Demographic2Action | General demographic support functions |
| demographic/ValidateSwipeCard | ca.openosp.openo.integration.mchcv.ValidateSwipeCard2Action | Validates health card swipe data |

## DHIR Module

Digital Health Immunization Repository integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| dhir/submit | ca.openosp.openo.integration.dhir.SubmitImmunization2Action | Submits immunizations to DHIR |

## Document Management Module

Document management and processing functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| DocumentDescriptionTemplate | ca.openosp.openo.www.provider.DocumentDescriptionTemplate2Action | Manages document description templates |
| documentManager/addDocumentType | ca.openosp.openo.documentManager.actions.AddDocumentType2Action | Adds new document types |
| documentManager/addEditDocument | ca.openosp.openo.documentManager.actions.AddEditDocument2Action | Adds or edits documents |
| documentManager/addEditHtml | ca.openosp.openo.documentManager.actions.AddEditHtml2Action | Adds or edits HTML documents |
| documentManager/addLink | ca.openosp.openo.documentManager.actions.AddEditHtml2Action | Adds document links |
| documentManager/changeDocStatus | ca.openosp.openo.documentManager.actions.ChangeDocStatus2Action | Changes document status |
| documentManager/combinePDFs | ca.openosp.openo.documentManager.actions.CombinePDF2Action | Combines multiple PDF documents |
| documentManager/documentUpload | ca.openosp.openo.documentManager.actions.DocumentUpload2Action | Uploads documents to the system |
| documentManager/inboxManage | ca.openosp.openo.documentManager.actions.DmsInboxManage2Action | Manages document inbox |
| documentManager/ManageDocument | ca.openosp.openo.documentManager.actions.ManageDocument2Action | General document management |
| documentManager/SplitDocument | ca.openosp.openo.documentManager.actions.SplitDocument2Action | Splits documents into parts |

## DX Code Search Module

Diagnostic code search functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| dxCodeSearchJSON | ca.openosp.openo.dxResearch.pageUtil.dxCodeSearchJSON2Action | Provides JSON-based diagnostic code search |

## E-Consult Module

Electronic consultation functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| econsult | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EConsult2Action | Manages electronic consultations |
| econsultSSOLogin | ca.openosp.openo.login.SSOLogin2Action | Provides SSO login for e-consults |

## Edit Provider Module

Provider information editing functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| EditAddress | ca.openosp.openo.providers.pageUtil.ProEditAddress2Action | Edits provider addresses |
| EditFaxNum | ca.openosp.openo.providers.pageUtil.ProEditFaxNum2Action | Edits provider fax numbers |
| EditPhoneNum | ca.openosp.openo.providers.pageUtil.ProEditPhoneNum2Action | Edits provider phone numbers |
| EditPrinter | ca.openosp.openo.providers.pageUtil.ProEditPrinter2Action | Edits provider printer settings |

## EForm Module

Electronic forms management and processing.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| eform/addEForm | ca.openosp.openo.eform.actions.AddEForm2Action | Adds new electronic forms |
| eform/addGroup | ca.openosp.openo.eform.actions.AddGroup2Action | Adds form groups |
| eform/addToGroup | ca.openosp.openo.eform.actions.AddToGroup2Action | Adds forms to groups |
| eform/attachDoc | ca.openosp.openo.eform.EFormAttachDocs2Action | Attaches documents to forms |
| eform/delEForm | ca.openosp.openo.eform.actions.DelEForm2Action | Deletes electronic forms |
| eform/deleteImage | ca.openosp.openo.eform.actions.DelImage2Action | Deletes images from forms |
| eform/displayImage | ca.openosp.openo.eform.actions.DisplayImage2Action | Displays form images |
| eform/editForm | ca.openosp.openo.eform.actions.HtmlEdit2Action | Edits HTML forms |
| eform/efmOpenEformByName | ca.openosp.openo.eform.actions.OpenEFormByName2Action | Opens forms by name |
| eform/efmPrintPDF | ca.openosp.openo.eform.actions.PrintPDF2Action | Prints forms as PDF |
| eform/eFormAttachmentForm | ca.openosp.openo.eform.upload.UploadEFormAttachment2Action | Uploads form attachments |
| eform/FetchUpdatedData | ca.openosp.openo.eform.actions.FetchUpdatedData2Action | Fetches updated form data |
| eform/imageUpload | ca.openosp.openo.eform.upload.ImageUpload2Action | Uploads images to forms |
| eform/IndivicaRichTextLetterSettings | ca.openosp.openo.eform.actions.RTLSettings2Action | Configures rich text letter settings |
| eform/logEformError | ca.openosp.openo.eform.EformLogError2Action | Logs form errors |
| eform/manageEForm | ca.openosp.openo.eform.actions.ManageEForm2Action | Manages electronic forms |
| eform/removeEForm | ca.openosp.openo.eform.actions.RemEForm2Action | Removes forms |
| eform/restoreEForm | ca.openosp.openo.eform.actions.RestoreEForm2Action | Restores deleted forms |
| eforms/delGroup | ca.openosp.openo.eform.actions.DeleteGroup2Action | Deletes form groups |
| eforms/removeFromGroup | ca.openosp.openo.eform.actions.RemoveFromGroup2Action | Removes forms from groups |
| eform/unRemoveEForm | ca.openosp.openo.eform.actions.UnRemEForm2Action | Un-removes forms |
| eform/uploadHtml | ca.openosp.openo.eform.upload.HtmlUpload2Action | Uploads HTML forms |

## Email Module

Email management and communication.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| email/emailComposeAction | ca.openosp.openo.email.action.EmailCompose2Action | Composes new emails |
| email/emailSendAction | ca.openosp.openo.email.action.EmailSend2Action | Sends composed emails |

## Signature Module

Provider signature management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| EnterSignature | ca.openosp.openo.providers.pageUtil.ProEditSignature2Action | Enters or edits provider signatures |

## Episode Module

Clinical episode management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Episode | ca.openosp.openo.commn.web.Episode2Action | Manages clinical episodes |

## Facility Module

Facility management and messaging.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| FacilityManager | ca.openosp.openo.facility.FacilityManager2Action | Manages facility information |
| FacilityMessage | org.caisi.core.web.FacilityMessage2Action | Handles facility messaging |

## Fax Module

Fax functionality and management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| fax/faxAction | ca.openosp.openo.fax.action.Fax2Action | Manages fax operations |

## Form Module

Clinical forms and data import functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| form/AddRHWorkFlow | ca.openosp.openo.form.pageUtil.FrmFormAddRHWorkFlow2Action | Adds reproductive health workflows |
| form/BCAR2020 | ca.openosp.openo.form.FrmBCAR20202Action | Processes BC AR 2020 forms |
| formBPMH | ca.openosp.openo.form.pharmaForms.formBPMH.web.BpmhFormRetrieve2Action | Retrieves Best Possible Medication History forms |
| formeCARES | ca.openosp.openo.form.eCARES.EcaresForm2Action | Processes eCARES forms |
| form/formname | ca.openosp.openo.form.Frm2Action | Generic form processor |
| form/forwardshortcutname | ca.openosp.openo.form.pageUtil.FormForward2Action | Forwards form shortcuts |
| form/importLogDownload | ca.openosp.openo.demographic.pageUtil.ImportLogDownload2Action | Downloads import logs |
| form/importUpload | ca.openosp.openo.demographic.pageUtil.ImportDemographicDataAction42Action | Uploads demographic import data |
| form/RHPrevention | ca.openosp.openo.form.pageUtil.FrmFormRHPrevention2Action | Manages reproductive health prevention forms |
| form/select | ca.openosp.openo.form.pageUtil.FrmSelect2Action | Selects forms |
| form/SetupForm | ca.openosp.openo.form.pageUtil.FrmSetupForm2Action | Sets up new forms |
| form/setupSelect | ca.openosp.openo.form.pageUtil.FrmSetupSelect2Action | Sets up form selection |
| form/SubmitForm | ca.openosp.openo.form.pageUtil.FrmForm2Action | Submits completed forms |
| form/xmlUpload | ca.openosp.openo.form.pageUtil.FrmXmlUpload2Action | Uploads XML form data |

## Professional Specialist Module

Professional specialist registry.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| getProfessionalSpecialist | ca.openosp.openo.contactRegistry.ProfessionalSpecialist2Action | Retrieves professional specialist information |

## Home Module

System home page functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Home | ca.openosp.openo.web.PMmodule.Home2Action | Displays system home page |

## Hospital Report Manager Module

Hospital report management and integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| hospitalReportManager/Display | ca.openosp.openo.hospitalReportManager.HRMDisplayReport2Action | Displays hospital reports |
| hospitalReportManager/HRMDownloadFile | ca.openosp.openo.hospitalReportManager.HRMDownloadFile2Action | Downloads HRM files |
| hospitalReportManager/hrmKeyUploader | ca.openosp.openo.hospitalReportManager.HRMUploadKey2Action | Uploads HRM encryption keys |
| hospitalReportManager/hrm | ca.openosp.openo.hospitalReportManager.v2018.HRM2Action | Main HRM interface |
| hospitalReportManager/HRMPreferences | ca.openosp.openo.hospitalReportManager.HRMPreferences2Action | Manages HRM preferences |
| hospitalReportManager/Mapping | ca.openosp.openo.hospitalReportManager.HRMMapping2Action | Maps HRM data fields |
| hospitalReportManager/Modify | ca.openosp.openo.hospitalReportManager.HRMModifyDocument2Action | Modifies HRM documents |
| hospitalReportManager/PrintHRMReport | ca.openosp.openo.hospitalReportManager.PrintHRMReport2Action | Prints HRM reports |
| hospitalReportManager/Statement | ca.openosp.openo.hospitalReportManager.HRMStatementModify2Action | Modifies HRM statements |
| hospitalReportManager/UploadLab | ca.openosp.openo.hospitalReportManager.HRMUploadLab2Action | Uploads lab results to HRM |

## Indivica Module

Health card search functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| indivica/HCSearch | ca.openosp.openo.commn.web.HealthCardSearch2Action | Searches health card database |

## Infirm Module

Infirmary management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| infirm | org.caisi.core.web.Infirm2Action | Manages infirmary operations |

## Integrator Module

System integration functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| integrator/IntegratorPush | ca.openosp.openo.commn.web.IntegratorPush2Action | Pushes data to external integrators |

## Issue Admin Module

Issue administration functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| issueAdmin | org.caisi.core.web.IssueAdmin2Action | Administers clinical issues |

## Lab Module

Laboratory result management and integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| lab/CA/ALL/createLabelTDIS | ca.openosp.openo.lab.ca.all.pageUtil.CreateLabelTDIS2Action | Creates TDIS lab labels |
| lab/CA/ALL/Forward | ca.openosp.openo.mds.pageUtil.ReportReassign2Action | Forwards lab reports to providers |
| lab/CA/ALL/insideLabUpload | ca.openosp.openo.lab.ca.all.pageUtil.InsideLabUpload2Action | Uploads inside lab results |
| lab/CA/ALL/oruR01Upload | ca.openosp.openo.lab.ca.all.pageUtil.OruR01Upload2Action | Uploads ORU R01 lab messages |
| lab/CA/ALL/PrintOLISLab | ca.openosp.openo.lab.ca.all.pageUtil.PrintOLISLab2Action | Prints individual OLIS lab results |
| lab/CA/ALL/PrintOLIS | ca.openosp.openo.lab.ca.all.pageUtil.PrintOLISLabs2Action | Prints multiple OLIS lab results |
| lab/CA/ALL/PrintPDF | ca.openosp.openo.lab.ca.all.pageUtil.PrintLabs2Action | Prints lab results as PDF |
| lab/CA/ALL/UnlinkDemographic | ca.openosp.openo.lab.ca.all.pageUtil.UnlinkDemographic2Action | Unlinks demographics from lab results |
| lab/CA/BC/Forward | ca.openosp.openo.mds.pageUtil.ReportReassign2Action | Forwards BC lab reports |
| lab/CA/ON/Forward | ca.openosp.openo.mds.pageUtil.ReportReassign2Action | Forwards Ontario lab reports |
| lab/CMLlabUpload | ca.openosp.openo.lab.ca.on.CML.Upload.LabUpload2Action | Uploads CML lab results |
| lab/DownloadEmbeddedDocumentFromLab | ca.openosp.openo.lab.ca.all.pageUtil.DownloadEmbeddedDocumentFromLab2Action | Downloads embedded lab documents |
| lab/labUpload | ca.openosp.openo.lab.ca.bc.PathNet.pageUtil.LabUpload2Action | Uploads PathNet lab results |
| lab/newLabUpload | ca.openosp.openo.lab.ca.all.pageUtil.LabUpload2Action | Uploads new lab results |

## Login Module

User authentication and session management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| login | ca.openosp.openo.login.Login2Action | Processes user login |
| login/recordLogin | ca.openosp.openo.login.LoginAgreement2Action | Records login agreements |
| logout | ca.openosp.openo.login.Logout2Action | Processes user logout |

## Lookup Module

System lookup table management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| lookupListManagerAction | ca.openosp.openo.admin.lookUpLists.LookupListManager2Action | Manages lookup list configurations |
| Lookup/LookupCodeEdit | ca.openosp.openo.www.lookup.LookupCodeEdit2Action | Edits lookup codes |
| Lookup/LookupCodeList | ca.openosp.openo.www.lookup.LookupCodeList2Action | Lists lookup codes |
| Lookup/LookupList | ca.openosp.openo.www.lookup.LookupList2Action | Manages lookup lists |
| Lookup/LookupTableList | ca.openosp.openo.www.lookup.LookupTableList2Action | Lists lookup tables |

## MCEDT Module

Ministry Claims Electronic Data Transfer integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| mcedt/addUpload | ca.openosp.openo.integration.mcedt.mailbox.Upload2Action | Adds MCEDT uploads |
| mcedt/autoUpload | ca.openosp.openo.integration.mcedt.mailbox.Upload2Action | Performs automatic MCEDT uploads |
| mcedt/download | ca.openosp.openo.integration.mcedt.mailbox.Download2Action | Downloads MCEDT files |
| mcedt/info | ca.openosp.openo.integration.mcedt.Info2Action | Displays MCEDT information |
| mcedt/kaiautodl | ca.openosp.openo.integration.mcedt.mailbox.Download2Action | Kai automatic downloads |
| mcedt/kaichpass | ca.openosp.openo.integration.mcedt.mailbox.User2Action | Changes Kai passwords |
| mcedt/kaimcedt | ca.openosp.openo.integration.mcedt.mailbox.Resource2Action | Manages Kai MCEDT resources |
| mcedt/mcedt | ca.openosp.openo.integration.mcedt.Resource2Action | Main MCEDT interface |
| mcedt/openAutoUpload | ca.openosp.openo.integration.mcedt.mailbox.Resource2Action | Opens automatic upload interface |
| mcedt/resourceInfo | ca.openosp.openo.integration.mcedt.mailbox.Info2Action | Displays MCEDT resource information |
| mcedt/reSubmit | ca.openosp.openo.integration.mcedt.mailbox.ReSubmit2Action | Resubmits MCEDT data |
| mcedt/update | ca.openosp.openo.integration.mcedt.Update2Action | Updates MCEDT configurations |
| mcedt/upload | ca.openosp.openo.integration.mcedt.mailbox.Upload2Action | Uploads MCEDT files |
| mcedt/uploads | ca.openosp.openo.integration.mcedt.Upload2Action | Manages MCEDT uploads |

## Measurement Module

Clinical measurement data management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| MeasurementHL7Uploader | ca.openosp.openo.encounter.oscarMeasurements.hl7.MeasurementHL7Uploader2Action | Uploads measurements via HL7 |

## Notification Module

Provider notification system.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| notification/create | ca.openosp.openo.commn.web.ProviderNotification2Action | Creates provider notifications |

## OLIS Module

Ontario Laboratory Information System integration.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| olis/AddToInbox | ca.openosp.openo.olis.OLISAddToInbox2Action | Adds OLIS results to inbox |
| olis/Preferences | ca.openosp.openo.olis.OLISPreferences2Action | Manages OLIS preferences |
| olis/Results | ca.openosp.openo.olis.OLISResults2Action | Retrieves OLIS lab results |
| olis/Search | ca.openosp.openo.olis.OLISSearch2Action | Searches OLIS database |
| olis/UploadSimulationData | ca.openosp.openo.olis.OLISUploadSimulationData2Action | Uploads OLIS simulation data |

## OnCall Module

On-call questionnaire functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| OnCallQuestionnaire | ca.openosp.openo.casemgmt.web.OnCallQuestionnaire2Action | Manages on-call questionnaires |

## Oscar Billing Module

OSCAR billing system functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarBilling/DocumentErrorReportUpload | ca.openosp.openo.billings.ca.on.pageUtil.BillingDocumentErrorReportUpload2Action | Uploads billing error reports |

## Oscar Chart Module

Chart printing functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| OscarChartPrint | ca.openosp.openo.casemgmt.web.EChartPrint2Action | Prints patient charts |

## Oscar Consultation Module

Consultation request management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarConsultationRequest/consultationClinicalData | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.ConsultationClinicalData2Action | Manages consultation clinical data |

## Oscar Encounter Module

Clinical encounter management and workflow.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarEncounter/AddDepartment | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConAddDepartment2Action | Adds consultation departments |
| oscarEncounter/AddInstitution | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConAddInstitution2Action | Adds consultation institutions |
| oscarEncounter/AddService | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConAddService2Action | Adds consultation services |
| oscarEncounter/AddSpecialist | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConAddSpecialist2Action | Adds specialists |
| oscarEncounter/decisionSupport/guidelineAction | ca.openosp.openo.decisionSupport.web.DSGuideline2Action | Processes decision support guidelines |
| oscarEncounter/DelService | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConDeleteServices2Action | Deletes consultation services |
| oscarEncounter/displayAllergy | ca.openosp.openo.encounter.pageUtil.EctDisplayAllergy2Action | Displays patient allergies in encounter |
| oscarEncounter/displayAppointmentHistory | ca.openosp.openo.encounter.pageUtil.EctDisplayAppointmentHistory2Action | Displays appointment history |
| oscarEncounter/displayBilling | ca.openosp.openo.encounter.pageUtil.EctDisplayBilling2Action | Displays billing information |
| oscarEncounter/displayConReport | ca.openosp.openo.encounter.pageUtil.EctDisplayConReport2Action | Displays consultation reports |
| oscarEncounter/displayConsultation | ca.openosp.openo.encounter.pageUtil.EctDisplayConsult2Action | Displays consultations |
| oscarEncounter/displayContacts | ca.openosp.openo.encounter.pageUtil.EctDisplayContacts2Action | Displays patient contacts |
| oscarEncounter/displayDecisionSupportAlerts | ca.openosp.openo.encounter.pageUtil.EctDisplayDecisionSupportAlerts2Action | Displays decision support alerts |
| oscarEncounter/displayDiagrams | ca.openosp.openo.encounter.pageUtil.EctDisplayDiagram2Action | Displays clinical diagrams |
| oscarEncounter/displayDisease | ca.openosp.openo.encounter.pageUtil.EctDisplayDx2Action | Displays diagnoses |
| oscarEncounter/displayDocuments | ca.openosp.openo.encounter.pageUtil.EctDisplayDocs2Action | Displays encounter documents |
| oscarEncounter/displayEconsultation | ca.openosp.openo.encounter.pageUtil.EctDisplayEconsult2Action | Displays e-consultations |
| oscarEncounter/displayEForms | ca.openosp.openo.encounter.pageUtil.EctDisplayEForm2Action | Displays electronic forms |
| oscarEncounter/displayEHR | ca.openosp.openo.encounter.pageUtil.EctDisplayEHR2Action | Displays electronic health records |
| oscarEncounter/displayEpisodes | ca.openosp.openo.encounter.pageUtil.EctDisplayEpisode2Action | Displays clinical episodes |
| oscarEncounter/displayExaminationHistory | ca.openosp.openo.encounter.pageUtil.EctDisplayExaminationHistory2Action | Displays examination history |
| oscarEncounter/displayForms | ca.openosp.openo.encounter.pageUtil.EctDisplayForm2Action | Displays clinical forms |
| oscarEncounter/displayHRM | ca.openosp.openo.encounter.pageUtil.EctDisplayHRM2Action | Displays hospital reports |
| oscarEncounter/displayIssues | ca.openosp.openo.encounter.pageUtil.EctDisplayIssues2Action | Displays patient issues |
| oscarEncounter/displayLabs | ca.openosp.openo.encounter.pageUtil.EctDisplayLabAction22Action | Displays lab results |
| oscarEncounter/displayMacro | ca.openosp.openo.encounter.pageUtil.EctDisplayMacro2Action | Displays text macros |
| oscarEncounter/displayMeasurements | ca.openosp.openo.encounter.pageUtil.EctDisplayMeasurements2Action | Displays clinical measurements |
| oscarEncounter/displayMessages | ca.openosp.openo.encounter.pageUtil.EctDisplayMsg2Action | Displays messages |
| oscarEncounter/displayOcularProcedure | ca.openosp.openo.encounter.pageUtil.EctDisplayOcularProcedure2Action | Displays ocular procedures |
| oscarEncounter/displayPhotos | ca.openosp.openo.encounter.pageUtil.EctDisplayPhotos2Action | Displays patient photos |
| oscarEncounter/displayPregnancies | ca.openosp.openo.encounter.pageUtil.EctDisplayPregnancy2Action | Displays pregnancy information |
| oscarEncounter/displayPrevention | ca.openosp.openo.encounter.pageUtil.EctDisplayPrevention2Action | Displays prevention records |
| oscarEncounter/displayResolvedIssues | ca.openosp.openo.encounter.pageUtil.EctDisplayResolvedIssues2Action | Displays resolved issues |
| oscarEncounter/displayRx | ca.openosp.openo.encounter.pageUtil.EctDisplayRx2Action | Displays prescriptions |
| oscarEncounter/displaySpecsHistory | ca.openosp.openo.encounter.pageUtil.EctDisplaySpecsHistory2Action | Displays prescription history |
| oscarEncounter/displayTickler | ca.openosp.openo.encounter.pageUtil.EctDisplayTickler2Action | Displays ticklers |
| oscarEncounter/EditDepartments | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConEditDepartments2Action | Edits consultation departments |
| oscarEncounter/EditInstitutions | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConEditInstitutions2Action | Edits consultation institutions |
| oscarEncounter/EditSpecialists | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConEditSpecialists2Action | Edits specialists |
| oscarEncounter/EnableConRequestResponse | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConEnableReqResp2Action | Enables consultation request responses |
| oscarEncounter/eRefer | ca.openosp.openo.oscarEncounter.oceanEReferal.pageUtil.ERefer2Action | Processes electronic referrals |
| oscarEncounter/FormUpdate | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.FormUpdate2Action | Updates measurement forms |
| oscarEncounter/GraphMeasurements | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.MeasurementGraphAction22Action | Graphs clinical measurements |
| oscarEncounter/immunization/config/CreateImmunizationSetConfig | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetConfig2Action | Creates immunization configurations |
| oscarEncounter/immunization/config/CreateInitImmunization | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetInit2Action | Initializes immunization sets |
| oscarEncounter/immunization/config/deleteImmunizationSet | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmInitConfigDeleteImmuSet2Action | Deletes immunization sets |
| oscarEncounter/immunization/config/DeleteImmunizationSets | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmDeleteImmunizationSet2Action | Deletes multiple immunization sets |
| oscarEncounter/immunization/config/ImmunizationSetDisplay | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmImmunizationSetDisplay2Action | Displays immunization sets |
| oscarEncounter/immunization/config/initConfig | ca.openosp.openo.encounter.immunization.config.pageUtil.EctImmInitConfig2Action | Initializes immunization config |
| oscarEncounter/immunization/deleteSchedule | ca.openosp.openo.encounter.immunization.pageUtil.EctImmDeleteImmSchedule2Action | Deletes immunization schedules |
| oscarEncounter/immunization/initSchedule | ca.openosp.openo.encounter.immunization.pageUtil.EctImmInitSchedule2Action | Initializes immunization schedules |
| oscarEncounter/immunization/loadConfig | ca.openosp.openo.encounter.immunization.pageUtil.EctImmLoadConfig2Action | Loads immunization configurations |
| oscarEncounter/immunization/loadSchedule | ca.openosp.openo.encounter.immunization.pageUtil.EctImmLoadSchedule2Action | Loads immunization schedules |
| oscarEncounter/immunization/saveConfig | ca.openosp.openo.encounter.immunization.pageUtil.EctImmSaveConfig2Action | Saves immunization configurations |
| oscarEncounter/immunization/saveSchedule | ca.openosp.openo.encounter.immunization.pageUtil.EctImmSaveSchedule2Action | Saves immunization schedules |
| oscarEncounter/IncomingConsultation | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctIncomingConsultation2Action | Processes incoming consultations |
| oscarEncounter/IncomingEncounter | ca.openosp.openo.encounter.pageUtil.EctIncomingEncounter2Action | Processes incoming encounters |
| oscarEncounter/InsertTemplate | ca.openosp.openo.encounter.pageUtil.EctInsertTemplate2Action | Inserts encounter templates |
| oscarEncounter/MeasurementData | ca.openosp.openo.measurements.web.MeasurementData2Action | Manages measurement data |
| oscarEncounter/Measurements2 | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action | Manages encounter measurements |
| oscarEncounter/Measurements | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action | Manages clinical measurements |
| oscarEncounter/oscarConsultation/printAttached | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.ConsultationPrintDocs2Action | Prints attached consultation documents |
| oscarEncounter/oscarConsultationRequest/ConsultationFormFax | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormFax2Action | Faxes consultation forms |
| oscarEncounter/oscarConsultationRequest/printPdf2 | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestPrintAction22Action | Prints consultation PDFs |
| oscarEncounter/oscarMeasurements/AddMeasurementGroup | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctAddMeasurementGroup2Action | Adds measurement groups |
| oscarEncounter/oscarMeasurements/AddMeasurementMap | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctAddMeasurementMap2Action | Adds measurement mappings |
| oscarEncounter/oscarMeasurements/AddMeasurementStyleSheet | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctAddMeasurementStyleSheet2Action | Adds measurement stylesheets |
| oscarEncounter/oscarMeasurements/AddMeasurementType | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctAddMeasurementType2Action | Adds measurement types |
| oscarEncounter/oscarMeasurements/AddMeasuringInstruction | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctAddMeasuringInstruction2Action | Adds measuring instructions |
| oscarEncounter/oscarMeasurements/adminFlowsheet/FlowSheetCustomAction | ca.openosp.openo.commn.web.FlowSheetCustom2Action | Manages custom flowsheets |
| oscarEncounter/oscarMeasurements/DefineNewMeasurementGroup | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctDefineNewMeasurementGroup2Action | Defines new measurement groups |
| oscarEncounter/oscarMeasurements/DeleteData2 | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctDeleteData2Action | Deletes measurement data |
| oscarEncounter/oscarMeasurements/DeleteData | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctDeleteData2Action | Deletes measurement data |
| oscarEncounter/oscarMeasurements/DeleteMeasurementStyleSheet | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctDeleteMeasurementStyleSheet2Action | Deletes measurement stylesheets |
| oscarEncounter/oscarMeasurements/DeleteMeasurementTypes | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctDeleteMeasurementTypes2Action | Deletes measurement types |
| oscarEncounter/oscarMeasurements/EditMeasurementGroup | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctEditMeasurementGroup2Action | Edits measurement groups |
| oscarEncounter/oscarMeasurements/EditMeasurementStyle | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctEditMeasurementStyle2Action | Edits measurement styles |
| oscarEncounter/oscarMeasurements/FlowSheetDrugAction | ca.openosp.openo.commn.web.FlowSheetDrug2Action | Manages flowsheet drug information |
| oscarEncounter/oscarMeasurements/NewMeasurementMap | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctAddMeasurementMap2Action | Creates new measurement maps |
| oscarEncounter/oscarMeasurements/RemapMeasurementMap | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctRemoveMeasurementMap2Action | Remaps measurement mappings |
| oscarEncounter/oscarMeasurements/RemoveMeasurementMap | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctRemoveMeasurementMap2Action | Removes measurement mappings |
| oscarEncounter/oscarMeasurements/SelectMeasurementGroup | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSelectMeasurementGroup2Action | Selects measurement groups |
| oscarEncounter/oscarMeasurements/SetupAddMeasurementGroup | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupAddMeasurementGroup2Action | Sets up new measurement groups |
| oscarEncounter/oscarMeasurements/SetupAddMeasurementType | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupAddMeasurementType2Action | Sets up new measurement types |
| oscarEncounter/oscarMeasurements/SetupAddMeasuringInstruction | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupAddMeasuringInstruction2Action | Sets up measuring instructions |
| oscarEncounter/oscarMeasurements/SetupDisplayHistory | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupDisplayHistory2Action | Sets up measurement history display |
| oscarEncounter/oscarMeasurements/SetupDisplayMeasurementStyleSheet | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupDisplayMeasurementStyleSheet2Action | Sets up measurement stylesheet display |
| oscarEncounter/oscarMeasurements/SetupDisplayMeasurementTypes | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupDisplayMeasurementTypes2Action | Sets up measurement types display |
| oscarEncounter/oscarMeasurements/SetupEditMeasurementGroup | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupEditMeasurementGroup2Action | Sets up measurement group editing |
| oscarEncounter/oscarMeasurements/SetupGroupList | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupGroupList2Action | Sets up measurement group lists |
| oscarEncounter/oscarMeasurements/SetupHistoryIndex | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupHistoryIndex2Action | Sets up measurement history index |
| oscarEncounter/oscarMeasurements/SetupMeasurements | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupMeasurements2Action | Sets up measurements interface |
| oscarEncounter/oscarMeasurements/SetupStyleSheetList | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctSetupStyleSheetList2Action | Sets up stylesheet list |
| oscarEncounter/oscarMeasurements/TrackerSlimUpdate | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.FormUpdate2Action | Updates slim tracker forms |
| oscarEncounter/oscarMeasurements/TrackerUpdate | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.FormUpdate2Action | Updates tracker forms |
| oscarEncounter/RequestConsultation | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequest2Action | Requests consultations |
| oscarEncounter/SaveEncounter2 | ca.openosp.openo.encounter.pageUtil.EctSaveEncounter2Action | Saves clinical encounters |
| oscarEncounter/SaveEncounter | ca.openosp.openo.encounter.pageUtil.EctSaveEncounter2Action | Saves encounter data |
| oscarEncounter/ShowAllInstitutions | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConShowAllInstitutions2Action | Shows all consultation institutions |
| oscarEncounter/ShowAllServices | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConShowAllServices2Action | Shows all consultation services |
| oscarEncounter/UpdateInstitutionDepartment | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayInstitution2Action | Updates institution departments |
| oscarEncounter/UpdateServiceSpecialists | ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayService2Action | Updates service specialists |
| oscarEncounter/ViewAttachment | ca.openosp.openo.encounter.pageUtil.EctViewAttachment2Action | Views encounter attachments |
| oscarEncounter/ViewConsultation | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequests2Action | Views consultation requests |
| oscarEncounter/ViewRequest | ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctViewRequest2Action | Views consultation requests |

## Oscar MDS Module

Medical Data Services functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarMDS/FileLabs | ca.openosp.openo.lab.pageUtil.FileLabs2Action | Files lab results |
| oscarMDS/ForwardingRules | ca.openosp.openo.lab.pageUtil.ForwardingRules2Action | Manages lab forwarding rules |
| oscarMDS/Forward | ca.openosp.openo.mdss.pageUtil.ReportReassign2Action | Forwards MDS reports |
| oscarMDS/PatientMatch | ca.openosp.openo.mds.pageUtil.PatientMatch2Action | Matches patients to reports |
| oscarMDS/ReportReassign | ca.openosp.openo.mds.pageUtil.ReportReassign2Action | Reassigns reports to providers |
| oscarMDS/RunMacro | ca.openosp.openo.mds.pageUtil.ReportMacro2Action | Runs report processing macros |
| oscarMDS/SearchPatient | ca.openosp.openo.mds.pageUtil.SearchPatient2Action | Searches for patients |
| oscarMDS/SendMRP | ca.openosp.openo.mds.pageUtil.SendMostResponProv2Action | Sends to most responsible provider |
| oscarMDS/SubmitLab | ca.openosp.openo.lab.ca.all.web.SubmitLabByForm2Action | Submits lab results by form |
| oscarMDS/UpdateStatus | ca.openosp.openo.mds.pageUtil.ReportStatusUpdate2Action | Updates report status |

## Oscar Measurement Module

Clinical measurement functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarMeasurement/AddShortMeasurement | ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctAddShortMeasurement2Action | Adds short-form measurements |

## Oscar Messenger Module

Internal messaging system.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| messenger/AddGroup | ca.openosp.openo.messenger.config.pageUtil.MsgMessengerCreateGroup2Action | Adds messenger groups |
| messenger/ClearMessage | ca.openosp.openo.messenger.pageUtil.MsgClearMessage2Action | Clears messages |
| messenger/CreateMessage | ca.openosp.openo.messenger.pageUtil.MsgCreateMessage2Action | Creates new messages |
| messenger/DisplayDemographicMessages | ca.openosp.openo.messenger.pageUtil.MsgDisplayDemographicMessages2Action | Displays demographic messages |
| messenger/DisplayMessages | ca.openosp.openo.messenger.pageUtil.MsgDisplayMessages2Action | Displays messages |
| messenger/Doc2PDF | ca.openosp.openo.messenger.pageUtil.MsgAttachPDF2Action | Converts documents to PDF |
| messenger/HandleMessages | ca.openosp.openo.messenger.pageUtil.MsgHandleMessages2Action | Handles message processing |
| messenger/ImportDemographic | ca.openosp.openo.messenger.pageUtil.ImportDemographic2Action | Imports demographic data |
| messenger | ca.openosp.openo.messenger.config.pageUtil.MsgMessengerAdmin2Action | Administers messenger system |
| messenger/ProcessDoc2PDF | ca.openosp.openo.messenger.pageUtil.MsgDoc2PDF2Action | Processes document to PDF conversion |
| messenger/ReDisplayMessages | ca.openosp.openo.messenger.pageUtil.MsgReDisplayMessages2Action | Re-displays messages |
| messenger/SendDemoMessage | ca.openosp.openo.messenger.pageUtil.MsgSendDemographicMessage2Action | Sends demographic messages |
| messenger/SendMessage | ca.openosp.openo.messenger.pageUtil.MsgSendMessage2Action | Sends messages |
| messenger/Transfer/Proceed | ca.openosp.openo.messenger.pageUtil.MsgProceed2Action | Proceeds with message transfer |
| messenger/ViewAttach | ca.openosp.openo.messenger.pageUtil.MsgViewAttachment2Action | Views message attachments |
| messenger/ViewMessage | ca.openosp.openo.messenger.pageUtil.MsgViewMessage2Action | Views messages |
| messenger/ViewPDFAttach | ca.openosp.openo.messenger.pageUtil.MsgViewPDFAttachment2Action | Views PDF attachments |
| messenger/ViewPDFFile | ca.openosp.openo.messenger.pageUtil.MsgViewPDF2Action | Views PDF files |
| messenger/WriteToEncounter | ca.openosp.openo.messenger.pageUtil.MsgWriteToEncounter2Action | Writes messages to encounters |

## Oscar Prevention Module

Preventive care management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarPrevention/AddPrevention | ca.openosp.openo.prevention.pageUtil.AddPrevention2Action | Adds prevention records |
| oscarPrevention/PreventionReport | ca.openosp.openo.prevention.pageUtil.PreventionReport2Action | Generates prevention reports |
| oscarPrevention/printPrevention | ca.openosp.openo.prevention.pageUtil.PreventionPrint2Action | Prints prevention records |

## Oscar Report Module

Comprehensive reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarReport/FluBilling | ca.openosp.openo.report.pageUtil.RptFluBilling2Action | Generates flu billing reports |
| oscarReport/obec | ca.openosp.openo.report.pageUtil.Obec2Action | Generates OBEC reports |
| oscarReport/oscarMeasurements/InitializeFrequencyOfRelevantTestsCDMReport | ca.openosp.openo.report.oscarMeasurements.pageUtil.RptInitializeFrequencyOfRelevantTestsCDMReport2Action | Initializes CDM test frequency reports |
| oscarReport/oscarMeasurements/InitializePatientsInAbnormalRangeCDMReport | ca.openosp.openo.report.oscarMeasurements.pageUtil.RptInitializePatientsInAbnormalRangeCDMReport2Action | Initializes abnormal range CDM reports |
| oscarReport/oscarMeasurements/InitializePatientsMetGuidelineCDMReport | ca.openosp.openo.report.oscarMeasurements.pageUtil.RptInitializePatientsMetGuidelineCDMReport2Action | Initializes guideline met CDM reports |
| oscarReport/oscarMeasurements/SelectCDMReport | ca.openosp.openo.report.oscarMeasurements.pageUtil.RptSelectCDMReport2Action | Selects CDM reports |
| oscarReport/oscarMeasurements/SetupSelectCDMReport | ca.openosp.openo.report.oscarMeasurements.pageUtil.RptSetupSelectCDMReport2Action | Sets up CDM report selection |
| oscarReport/reportByTemplate/actions/addGroup | ca.openosp.openo.report.reportByTemplate.actions.RBTAddGroup2Action | Adds template report groups |
| oscarReport/reportByTemplate/actions/delGroup | ca.openosp.openo.report.reportByTemplate.actions.RBTDeleteGroup2Action | Deletes template report groups |
| oscarReport/reportByTemplate/actions/rbtAddToGroup | ca.openosp.openo.report.reportByTemplate.actions.RBTAddToGroup2Action | Adds templates to groups |
| oscarReport/reportByTemplate/actions/remFromGroup | ca.openosp.openo.report.reportByTemplate.actions.RBTRemoveFromGroup2Action | Removes templates from groups |
| oscarReport/reportByTemplate/actions/tempInGroup | ca.openosp.openo.report.reportByTemplate.actions.RBTGetTemplatesInGroup2Action | Gets templates in groups |
| oscarReport/reportByTemplate/addEditTemplatesAction | ca.openosp.openo.report.reportByTemplate.actions.ManageTemplates2Action | Manages report templates |
| oscarReport/reportByTemplate/exportTemplateAction | ca.openosp.openo.report.reportByTemplate.actions.ExportTemplate2Action | Exports report templates |
| oscarReport/reportByTemplate/generateOutFilesAction | ca.openosp.openo.report.reportByTemplate.actions.GenerateOutFiles2Action | Generates output files |
| oscarReport/reportByTemplate/GenerateReportAction | ca.openosp.openo.report.reportByTemplate.actions.GenerateReport2Action | Generates template-based reports |
| oscarReport/reportByTemplate/rbtGroup | ca.openosp.openo.report.reportByTemplate.actions.RBTGetGroup2Action | Gets report template groups |
| oscarReport/reportByTemplate/uploadTemplates | ca.openosp.openo.report.reportByTemplate.actions.UploadTemplates2Action | Uploads report templates |
| oscarReport/RptByExample | ca.openosp.openo.report.pageUtil.RptByExample2Action | Generates reports by example |
| oscarReport/RptByExamplesAllFavorites | ca.openosp.openo.report.pageUtil.RptByExamplesAllFavorites2Action | Shows all favorite example reports |
| oscarReport/RptByExamplesFavorite | ca.openosp.openo.report.pageUtil.RptByExamplesFavorite2Action | Manages favorite example reports |
| oscarReport/RptViewAllQueryByExamples | ca.openosp.openo.report.pageUtil.RptViewAllQueryByExamples2Action | Views all query examples |
| oscarReport/ShowConsult | ca.openosp.openo.report.pageUtil.RptShowConsult2Action | Shows consultation reports |

## Oscar Research Module

Medical research and diagnostic code management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarResearch/oscarDxResearch/dxResearchCodeSearch | ca.openosp.openo.dxResearch.pageUtil.dxResearchCodeSearch2Action | Searches diagnostic research codes |
| oscarResearch/oscarDxResearch/dxResearchLoadAssociations | ca.openosp.openo.dxResearch.pageUtil.dxResearchLoadAssociations2Action | Loads research code associations |
| oscarResearch/oscarDxResearch/dxResearchLoadQuickListItems | ca.openosp.openo.dxResearch.pageUtil.dxResearchLoadQuickListItems2Action | Loads quick list items |
| oscarResearch/oscarDxResearch/dxResearchLoadQuickList | ca.openosp.openo.dxResearch.pageUtil.dxResearchLoadQuickList2Action | Loads research quick lists |
| oscarResearch/oscarDxResearch/dxResearch | ca.openosp.openo.dxResearch.pageUtil.dxResearch2Action | Main diagnostic research interface |
| oscarResearch/oscarDxResearch/dxResearchUpdate | ca.openosp.openo.dxResearch.pageUtil.dxResearchUpdate2Action | Updates research data |
| oscarResearch/oscarDxResearch/dxResearchUpdateQuickList | ca.openosp.openo.dxResearch.pageUtil.dxResearchUpdateQuickList2Action | Updates research quick lists |
| oscarResearch/oscarDxResearch/setupDxResearch | ca.openosp.openo.dxResearch.pageUtil.dxSetupResearch2Action | Sets up diagnostic research |

## Oscar Rx Module

Prescription and medication management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarRx/addAllergy2 | ca.openosp.openo.prescript.pageUtil.RxAddAllergy2Action | Adds patient allergies |
| oscarRx/addAllergy | ca.openosp.openo.prescript.pageUtil.RxAddAllergy2Action | Adds medication allergies |
| oscarRx/addFavorite2 | ca.openosp.openo.prescript.pageUtil.RxAddFavorite2Action | Adds prescription favorites |
| oscarRx/addFavoriteStaticScript | ca.openosp.openo.prescript.pageUtil.RxAddFavorite2Action | Adds static script favorites |
| oscarRx/addFavoriteViewScript | ca.openosp.openo.prescript.pageUtil.RxAddFavorite2Action | Adds view script favorites |
| oscarRx/addFavoriteWriteScript | ca.openosp.openo.prescript.pageUtil.RxAddFavorite2Action | Adds write script favorites |
| oscarRx/addReaction2 | ca.openosp.openo.prescript.pageUtil.RxAddReaction2Action | Adds drug reactions |
| oscarRx/addReaction | ca.openosp.openo.prescript.pageUtil.RxAddReaction2Action | Adds allergic reactions |
| oscarRx/chooseDrug | ca.openosp.openo.prescript.pageUtil.RxChooseDrug2Action | Selects medications |
| oscarRx/choosePatient | ca.openosp.openo.prescript.pageUtil.RxChoosePatient2Action | Selects patients for prescriptions |
| oscarRx/clearPending | ca.openosp.openo.prescript.pageUtil.RxClearPending2Action | Clears pending prescriptions |
| oscarRx/copyFavorite2 | ca.openosp.openo.prescript.web.CopyFavorites2Action | Copies prescription favorites |
| oscarRx/copyFavorite | ca.openosp.openo.prescript.web.CopyFavorites2Action | Copies favorite prescriptions |
| oscarRx/deleteAllergy2 | ca.openosp.openo.prescript.pageUtil.RxDeleteAllergy2Action | Deletes patient allergies |
| oscarRx/deleteAllergy | ca.openosp.openo.prescript.pageUtil.RxDeleteAllergy2Action | Deletes allergy records |
| oscarRx/deleteFavorite2 | ca.openosp.openo.prescript.pageUtil.RxDeleteFavorite2Action | Deletes prescription favorites |
| oscarRx/deleteFavorite | ca.openosp.openo.prescript.pageUtil.RxDeleteFavorite2Action | Deletes favorite prescriptions |
| oscarRx/deleteRx | ca.openosp.openo.prescript.pageUtil.RxDeleteRx2Action | Deletes prescriptions |
| oscarRx/Dispense | ca.openosp.openo.dispensary.rx.Dispensary2Action | Manages medication dispensing |
| oscarRx/drugInfo | ca.openosp.openo.prescript.pageUtil.RxDrugInfo2Action | Displays drug information |
| oscarRx/GetmyDrugrefInfo | ca.openosp.openo.prescript.pageUtil.RxMyDrugrefInfo2Action | Gets drug reference information |
| oscarRx/GetRxPageSizeInfo | ca.openosp.openo.prescript.pageUtil.RxRxPageSizeInfo2Action | Gets prescription page size info |
| oscarRx/hideCpp | ca.openosp.openo.prescript.web.RxHideCpp2Action | Hides CPP information |
| oscarRx/managePharmacy2 | ca.openosp.openo.prescript.pageUtil.RxManagePharmacy2Action | Manages pharmacy information |
| oscarRx/managePharmacy | ca.openosp.openo.prescript.pageUtil.RxManagePharmacy2Action | Manages pharmacy settings |
| oscarRx/reorderDrug | ca.openosp.openo.prescript.web.RxReorder2Action | Reorders medications |
| oscarRx/rePrescribe2 | ca.openosp.openo.prescript.pageUtil.RxRePrescribe2Action | Re-prescribes medications |
| oscarRx/rePrescribe | ca.openosp.openo.prescript.pageUtil.RxRePrescribe2Action | Repeats prescriptions |
| oscarRx/RxReason | ca.openosp.openo.prescript.pageUtil.RxReason2Action | Manages prescription reasons |
| oscarRx/rxStashDelete | ca.openosp.openo.prescript.pageUtil.RxStash2Action | Deletes prescription stash |
| oscarRx/searchAllergy2 | ca.openosp.openo.prescript.pageUtil.RxSearchAllergy2Action | Searches for allergies |
| oscarRx/searchAllergy | ca.openosp.openo.prescript.pageUtil.RxSearchAllergy2Action | Searches allergy database |
| oscarRx/searchDrug | ca.openosp.openo.prescript.pageUtil.RxSearchDrug2Action | Searches for medications |
| oscarRx/showAllergy | ca.openosp.openo.prescript.pageUtil.RxShowAllergy2Action | Displays patient allergies |
| oscarRx/stash | ca.openosp.openo.prescript.pageUtil.RxStash2Action | Manages prescription stash |
| oscarRx/updateDrugrefDB | ca.openosp.openo.prescript.pageUtil.RxUpdateDrugref2Action | Updates drug reference database |
| oscarRx/updateFavorite2 | ca.openosp.openo.prescript.pageUtil.RxUpdateFavorite2Action | Updates prescription favorites |
| oscarRx/updateFavorite | ca.openosp.openo.prescript.pageUtil.RxUpdateFavorite2Action | Updates favorite prescriptions |
| oscarRx/UpdateScript | ca.openosp.openo.prescript.pageUtil.RxWriteScript2Action | Updates prescription scripts |
| oscarRx/useFavorite | ca.openosp.openo.prescript.pageUtil.RxUseFavorite2Action | Uses favorite prescriptions |
| oscarRx/viewScript | ca.openosp.openo.prescript.pageUtil.RxViewScript2Action | Views prescription scripts |
| oscarRx/WriteScript | ca.openosp.openo.prescript.pageUtil.RxWriteScript2Action | Writes new prescriptions |
| oscarRx/writeScript | ca.openosp.openo.prescript.pageUtil.RxWriteScript2Action | Creates prescription scripts |
| oscarRx/WriteToEncounter | ca.openosp.openo.prescript.pageUtil.RxWriteToEncounter2Action | Writes prescriptions to encounters |

## Oscar Waiting List Module

Patient waiting list management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| oscarWaitingList/SetupDisplayPatientWaitingList | ca.openosp.openo.waitinglist.pageUtil.WLSetupDisplayPatientWaitingList2Action | Sets up patient waiting list display |
| oscarWaitingList/SetupDisplayWaitingList | ca.openosp.openo.waitinglist.pageUtil.WLSetupDisplayWaitingList2Action | Sets up waiting list display |
| oscarWaitingList/WLEditWaitingListNameAction | ca.openosp.openo.waitinglist.pageUtil.WLEditWaitingListName2Action | Edits waiting list names |

## Page Monitoring Module

System monitoring functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PageMonitoringService | ca.openosp.openo.commn.web.PageMonitoring2Action | Monitors page performance |

## PM Module

Program Management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PMmodule/Admin/DefaultRoleAccess | ca.openosp.openo.admin.web.PMmodule.DefaultRoleAccess2Action | Manages default role access |
| PMmodule/Admin/SysAdmin | ca.openosp.openo.admin.web.PMmodule.AdminHome2Action | System administration interface |
| PMmodule/AgencyManager | ca.openosp.openo.admin.web.PMmodule.AgencyManager2Action | Manages agencies |
| PMmodule/AllVacancies | ca.openosp.openo.web.PMmodule.AllWaitingList2Action | Views all program vacancies |
| PMmodule/ClientManager | ca.openosp.openo.web.PMmodule.ClientManager2Action | Manages program clients |
| PMmodule/ClientSearch2 | ca.openosp.openo.web.PMmodule.ClientSearchAction22Action | Searches for clients |
| PMmodule/FacilityManager | ca.openosp.openo.admin.web.PMmodule.FacilityManager2Action | Manages program facilities |
| PMmodule/HealthSafety | ca.openosp.openo.PMmodule.web.HealthSafety2Action | Manages health and safety |
| PMmodule/ProgramManager | ca.openosp.openo.admin.web.PMmodule.ProgramManager2Action | Manages programs |
| PMmodule/ProgramManagerView | ca.openosp.openo.admin.web.PMmodule.ProgramManagerView2Action | Views program management |
| PMmodule/ProviderInfo | ca.openosp.openo.web.PMmodule.ProviderInfo2Action | Displays provider information |
| PMmodule/ProviderSearch | ca.openosp.openo.web.PMmodule.ProviderSearch2Action | Searches for providers |
| PMmodule/Reports/BasicReport | ca.openosp.openo.reports.web.PMmodule.BasicReport2Action | Generates basic reports |
| PMmodule/Reports/ClientListsReport | ca.openosp.openo.reports.web.PMmodule.ClientListsReport2Action | Generates client list reports |
| PMmodule/Reports/ProgramActivityReport | ca.openosp.openo.reports.web.PMmodule.ActivityReport2Action | Generates program activity reports |
| PMmodule/StaffManager | ca.openosp.openo.admin.web.PMmodule.StaffManager2Action | Manages program staff |
| PMmodule/VacancyClientMatch | ca.openosp.openo.web.PMmodule.VacancyClientMatch2Action | Matches clients to vacancies |

## Population Module

Population reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PopulationReport | ca.openosp.openo.commn.web.PopulationReport2Action | Generates population reports |

## Pregnancy Module

Pregnancy management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Pregnancy | ca.openosp.openo.commn.web.Pregnancy2Action | Manages pregnancy records |

## Preview Module

Document preview functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| previewDocs | ca.openosp.openo.documentManager.actions.DocumentPreview2Action | Previews documents |

## Printer Module

Printer management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| PrinterList | ca.openosp.openo.printer.PrinterList2Action | Lists available printers |
| printReferralLabelAction | ca.openosp.openo.commn.web.PrintReferralLabel2Action | Prints referral labels |

## Provider Module

Provider management and preferences.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| provider/CppPreferences | ca.openosp.openo.www.provider.CppPreferences2Action | Manages CPP preferences |
| provider/OlisPreferences | ca.openosp.openo.www.provider.OlisPreferences2Action | Manages OLIS preferences |
| provider/rxInteractionWarningLevel | ca.openosp.openo.www.provider.ProviderRxInteractionWarningLevel2Action | Sets drug interaction warning levels |
| provider/SearchProvider | ca.openosp.openo.commn.web.SearchProviderAutoComplete2Action | Provides provider search autocomplete |
| Provider/showPersonal | ca.openosp.openo.www.provider.DisplayPersonalInfoAppointment2Action | Shows provider personal information |
| provider/UserPreference | ca.openosp.openo.www.provider.UserPreference2Action | Manages user preferences |

## Quick Billing Module

Quick billing functionality for BC.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| quickBillingBC | ca.openosp.openo.billings.ca.bc.quickbilling.QuickBillingBC2Action | Quick billing interface for BC |

## Renal Module

Renal care management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| renal/CkdDSA | ca.openosp.openo.renal.web.CkdDSA2Action | Chronic kidney disease decision support |
| renal/Renal | ca.openosp.openo.renal.web.Renal2Action | Renal care management |

## Report Module

General reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| report/CreateDemographicSet | ca.openosp.openo.report.pageUtil.RptCreateDemographicSet2Action | Creates demographic report sets |
| report/DeleteDemographicReport | ca.openosp.openo.report.pageUtil.RptDemographQueryFavouriteDelete2Action | Deletes demographic reports |
| report/DeleteLetter | ca.openosp.openo.report.pageUtil.DeletePatientLetters2Action | Deletes patient letters |
| report/DemographicReport | ca.openosp.openo.report.pageUtil.RptDemographicReport2Action | Generates demographic reports |
| report/DemographicSetEdit | ca.openosp.openo.report.pageUtil.DemographicSetEdit2Action | Edits demographic sets |
| report/DownloadLetter | ca.openosp.openo.report.pageUtil.DownloadPatientLetters2Action | Downloads patient letters |
| report/DxresearchReport | ca.openosp.openo.commn.web.DxresearchReport2Action | Generates diagnostic research reports |
| report/GenerateEnvelopes | ca.openosp.openo.report.pageUtil.GenerateEnvelopes2Action | Generates mailing envelopes |
| report/GenerateLetters | ca.openosp.openo.report.pageUtil.GeneratePatientLetters2Action | Generates patient letters |
| report/GenerateSpreadsheet | ca.openosp.openo.report.pageUtil.GeneratePatientSpreadSheetList2Action | Generates patient spreadsheets |
| report/ManageLetters | ca.openosp.openo.report.pageUtil.ManagePatientLetters2Action | Manages patient letters |
| report/printLabDaySheetAction | ca.openosp.openo.report.pageUtil.printLabDaySheet2Action | Prints lab day sheets |
| report/RemoveClinicalReport | ca.openosp.openo.report.ClinicalReports.PageUtil.RemoveClinicalReportFromHistory2Action | Removes clinical reports from history |
| report/SetEligibility | ca.openosp.openo.report.pageUtil.DemographicSetEligibility2Action | Sets demographic eligibility |

## Run Clinical Report Module

Clinical report execution.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| RunClinicalReport | ca.openosp.openo.report.ClinicalReports.PageUtil.RunClinicalReport2Action | Runs clinical reports |

## Save Quick Billing Module

Quick billing save functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| saveQuickBillingBC | ca.openosp.openo.billings.ca.bc.quickbilling.QuickBillingBCSave2Action | Saves BC quick billing |

## Save Work View Module

Work view management.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| saveWorkView | ca.openosp.openo.www.provider.ProviderView2Action | Saves provider work views |

## Scratch Module

Scratch pad functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| Scratch | ca.openosp.openo.scratch.Scratch2Action | Manages scratch pad notes |

## Search Professional Module

Professional specialist search.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| searchProfessionalSpecialist | ca.openosp.openo.contactRegistry.ProfessionalSpecialist2Action | Searches professional specialists |

## Security Module

Security and authentication functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| securityRecord/mfa | ca.openosp.openo.security.MfaActions2Action | Manages multi-factor authentication |

## Set Provider Module

Provider configuration functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| setProviderColour | ca.openosp.openo.providers.pageUtil.ProEditColour2Action | Sets provider color preferences |
| setProviderStaleDate | ca.openosp.openo.www.provider.ProviderProperty2Action | Sets provider stale date |
| setTicklerPreferences | ca.openosp.openo.www.provider.ProviderProperty2Action | Sets tickler preferences |

## Shelter Module

Shelter selection functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|

## SSO Module

Single Sign-On functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| ssoLogin | ca.openosp.openo.login.SSOLogin2Action | Processes SSO login |

## System Message Module

System messaging functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| SystemMessage | org.caisi.core.web.SystemMessage2Action | Manages system messages |

## Tickler Module

Tickler management functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| tickler/AddTickler | ca.openosp.openo.tickler.pageUtil.AddTickler2Action | Adds new ticklers |
| tickler/EditTickler | ca.openosp.openo.tickler.pageUtil.EditTickler2Action | Edits existing ticklers |
| tickler/EditTicklerTextSuggest | ca.openosp.openo.tickler.pageUtil.EditTickler2Action | Edits tickler text suggestions |
| tickler/ForwardDemographicTickler | ca.openosp.openo.tickler.pageUtil.ForwardDemographicTickler2Action | Forwards demographic ticklers |

## Vaccine Module

Vaccine reporting functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| VaccineProviderReport | ca.openosp.openo.vaccine.VaccineProviderReport2Action | Generates vaccine provider reports |

## Web Dashboard Module

Web-based dashboard functionality.

| Action Name | Class Name | Description |
|-------------|------------|-------------|
| web/dashboard/display/AssignTickler | ca.openosp.openo.dashboard.admin.AssignTickler2Action | Assigns ticklers from dashboard |
| web/dashboard/display/BulkPatientAction | ca.openosp.openo.dashboard.admin.BulkPatientDashboard2Action | Performs bulk patient actions |
| web/dashboard/display/DashboardDisplay | ca.openosp.openo.dashboard.display.DisplayDashboard2Action | Displays main dashboard |
| web/dashboard/display/DisplayIndicator | ca.openosp.openo.dashboard.display.DisplayIndicator2Action | Displays dashboard indicators |
| web/dashboard/display/DrilldownDisplay | ca.openosp.openo.dashboard.display.DisplayDrilldown2Action | Displays dashboard drilldown data |
| web/dashboard/display/ExportResults | ca.openosp.openo.dashboard.admin.ExportResults2Action | Exports dashboard results |
| web/dashboard/OutcomesDashboard | ca.openosp.openo.integration.dashboard.OutcomesDashboard2Action | Displays outcomes dashboard |

---

## Summary

This comprehensive reference includes all 507 Struts actions organized into 29 functional modules. Each action is documented with its purpose and functionality based on naming conventions and class structure. The actions cover the full spectrum of EMR functionality including clinical workflows, administrative tasks, billing operations, reporting capabilities, and system integrations.

The modular organization reflects OSCAR's comprehensive approach to healthcare management, supporting everything from basic patient demographics to complex clinical decision support and inter-system data exchange.