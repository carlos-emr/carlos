# AJAX Endpoints Audit Report

**Date**: 2026-02-16
**Scope**: All AJAX-serving endpoints in CARLOS EMR (Struts2 actions, REST services, JSP AJAX handlers)
**Methodology**: Systematic identification of all endpoints that serve AJAX/JSON/direct-response data, followed by exhaustive cross-referencing against all JSP, JavaScript, and Java callers.

---

## Executive Summary

| Category | Total Audited | Active | Orphaned |
|----------|--------------|--------|----------|
| Struts2 2Action AJAX Endpoints | 44 | 41 | **3** |
| CXF REST Web Services | 32 | 24 | **8** |
| JSP AJAX Response Files | 6 | 6 | 0 |
| **Total** | **82** | **71** | **11** |

---

## Orphaned Endpoints

### Struts2 Actions (3 orphaned)

#### 1. `TestActionW2Action` - CONFIRMED ORPHANED

- **File**: `src/main/java/io/github/carlos_emr/carlos/decisionSupport/web/TestActionW2Action.java`
- **Struts Mapping**: None (not mapped in struts.xml)
- **Callers**: Zero
- **Evidence**:
  - No action mapping in struts.xml - action is unreachable via HTTP
  - No JSP, JavaScript, or Java file references this class
  - No Spring bean definition exists
  - Returns `null` instead of a result string (non-standard pattern)
  - Naming suggests it was a prototype/test harness (`Test` + `ActionW`)
- **Functionality**: Invokes `DSService.evaluateAndGetConsequences()` and writes HTML output directly to response
- **Recommendation**: Safe to remove. The working equivalent is `DSGuideline2Action` which IS properly configured in struts.xml.

#### 2. `ManageDashboard2Action` - CONFIRMED ORPHANED

- **File**: `src/main/java/io/github/carlos_emr/carlos/dashboard/admin/ManageDashboard2Action.java`
- **Struts Mapping**: None (not mapped in struts.xml)
- **Callers**: Zero
- **Evidence**:
  - No action mapping in struts.xml despite being fully implemented (~480 lines)
  - No JSP, JavaScript, or Java file references this class
  - No Spring bean definition exists
  - The 3 other actions in the same package (`AssignTickler2Action`, `BulkPatientDashboard2Action`, `ExportResults2Action`) ARE mapped and active
- **Functionality**: Dashboard template import/export (XML), dashboard assignment, save, and toggle-active operations. Includes XXE prevention and file upload validation via PathValidationUtils.
- **Recommendation**: Safe to remove. Dashboard management functionality is not exposed through any UI entry point.

#### 3. `ConsultationAttachDocs2Action` - CONFIRMED ORPHANED

- **File**: `src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/pageUtil/ConsultationAttachDocs2Action.java`
- **Struts Mapping**: `attachDocs` (exists in struts.xml but is a dead entry)
- **Callers**: Zero
- **Evidence**:
  - Struts mapping exists but the result JSP (`/oscarEncounter/oscarConsultationRequest/attachConsultation.jsp`) does NOT exist on the filesystem (only `attachConsultation2.jsp` exists)
  - No JSP or JavaScript file ever calls `attachDocs.do`
  - No Java file references this class
  - Methods (`fetchAll`, `getDocumentPDF`, `getLabPDF`, `getFormPDF`, `getEFormPDF`, `getHRMPDF`) are never invoked
- **Functionality**: Fetches and renders consultation attachment documents as PDFs
- **Recommendation**: Safe to remove, along with the dead struts.xml mapping. The consultation attachment workflow has been superseded by other mechanisms.

---

### CXF REST Web Services (8 orphaned)

All orphaned REST services are registered in `src/main/resources/applicationContextREST.xml` but have zero callers in JSP, JavaScript, or Java source files.

#### 4. `PreventionService` (`/ws/rs/preventions`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/PreventionService.java`
- **Endpoints**: `/active`, `/immunizations/{demographicNo}`
- **Callers**: Zero - no JSP/JS/Java file calls these endpoints
- **Note**: The prevention module uses Struts2 actions and direct JSP logic instead, not this REST service

#### 5. `ProgramService` (`/ws/rs/program`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/ProgramService.java`
- **Endpoints**: `/patientList`, `/programList`
- **Callers**: Zero

#### 6. `RecordUxService` (`/ws/rs/recordUX`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/RecordUxService.java`
- **Endpoints**: `/{demographicNo}/recordMenu`, `/{demographicNo}/summary/{summaryName}`
- **Callers**: Zero

#### 7. `ReportByTemplateService` (`/ws/rs/reportByTemplate`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/ReportByTemplateService.java`
- **Callers**: Zero - report-by-template JSP pages exist but never call these REST endpoints

#### 8. `ResourceService` (`/ws/rs/resources`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/ResourceService.java`
- **Endpoints**: `/currentPreventionRulesVersion`, `/currentLuCodesVersion`
- **Callers**: Zero

#### 9. `RxLookupService` (`/ws/rs/rxlookup`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/RxLookupService.java`
- **Endpoints**: `/search`, `/details`, `/parse`
- **Callers**: Zero - drug lookup uses other mechanisms (direct DAO queries, existing RX actions)

#### 10. `MessagingService` (`/ws/rs/messaging`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/MessagingService.java`
- **Endpoints**: `/unread`, `/count`
- **Callers**: Zero - messaging uses Struts2 `messenger.do` actions instead

#### 11. `StatusService` (`/ws/rs/status`)

- **File**: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/StatusService.java`
- **Endpoints**: `/checkIfAuthed`
- **Callers**: Zero - auth status is checked via other mechanisms (session checks, login actions)
- **Note**: Not to be confused with `PatientDetailStatusService` which IS actively used

---

## Active Endpoints (Verified with Callers)

### JSP AJAX Response Files (6/6 active)

| File | Called By | Purpose |
|------|-----------|---------|
| `casemgmt/ChartNotesAjax.jsp` | `CaseManagementView2Action` (viewNotes, viewNotesOpt) | Renders clinical notes via AJAX |
| `casemgmt/unlockAjax.jsp` | `newCaseManagementView.js.jsp` → `CaseManagementView2Action.do_unlock_ajax()` | Note unlock with password |
| `casemgmt/noteIssueList.jsp` | Included by `ChartNotesAjax.jsp`; result of `CaseManagementEntry` action | Note issue assignment UI |
| `hospitalReportManager/ajaxResponse.jsp` | `hrmActions.js` (11 functions) → `HRMModifyDocument2Action` | HRM operation status response |
| `lab/CA/ALL/labDisplayAjax.jsp` | `Page.jsp`, `documentsInQueues.jsp`, `oscarMDSIndex.js` | HL7 lab result display |
| `demographic/demographiceditdemographic.jsp` | Multiple demographic actions | Demographic edit AJAX responses |

### Struts2 AJAX Actions (41/44 active)

<details>
<summary>Click to expand full list of 41 active Struts2 AJAX actions</summary>

| Action Class | Struts URL | Primary Callers |
|---|---|---|
| CodeSearchService2Action | `CodeSearch.do` | episodeForm.jsp |
| dxCodeSearchJSON2Action | `dxCodeSearchJSON.do` | dxJSONCodeSearch.js, billingBC.jsp, prescribe.jsp |
| PageMonitoring2Action | `PageMonitoringService.do` | addappointment.jsp, formonarenhanced*.jsp |
| NotePermissions2Action | `casemgmt/NotePermissions.do` | noteProgram.js |
| HealthCardSearch2Action | `indivica/HCSearch.do` | hcHandlerAppointment.js |
| DemographicExtService2Action | `DemographicExtService.do` | newCaseManagementView.js.jsp |
| IntegratorPush2Action | `integrator/IntegratorPush.do` | integratorPushStatus.jsp |
| BillingreferralEdit2Action | `admin/ManageBillingReferral.do` | billingreferralAdmin.jsp |
| DxresearchReport2Action | `report/DxresearchReport.do` | oscarReportDxReg.jsp |
| PrintReferralLabel2Action | `printReferralLabelAction.do` | billingreferralAdmin.jsp |
| Scratch2Action | `Scratch.do` | scratch/index.jsp, scratch/version.jsp |
| dxResearchLoadAssociations2Action | `dxResearchLoadAssociations.do` | dxResearchSelectAssociations.jsp |
| ConsultationLookup2Action | `ConsultationLookup2Action.do` | ConsultationFormRequest.jsp |
| ConsultationClinicalData2Action | `consultationClinicalData.do` | ConsultationFormRequest.jsp, formBCAR2020Attachments.jsp |
| ERefer2Action | `oscarEncounter/eRefer.do` | conreq.js |
| DSGuideline2Action | `guidelineAction.do` | guidelineList.jsp |
| CaseManagementEntry2Action | `CaseManagementEntry.do` | 54+ JSP/JS references |
| ProfessionalSpecialist2Action | `getProfessionalSpecialist.do` | ConsultationFormRequest.jsp |
| ManageEmails2Action | `admin/ManageEmails.do` | manageEmails.jsp, leftNav.jspf |
| Fax2Action | `fax/faxAction.do` | CoverPage.jsp |
| ConfigureFax2Action | `admin/ManageFax.do` | configureFax.jsp |
| ManageFaxes2Action | `admin/ManageFaxes.do` | manageFaxes.jsp, viewFax.jsp |
| Frm2Action | `form/formname.do` | 268+ medical form JSPs |
| EcaresForm2Action | `formeCARES.do` | formeCARES.jsp, eCARES_v1.js |
| UnlinkDemographic2Action | `lab/CA/ALL/UnlinkDemographic.do` | labDisplay.jsp |
| AssignTickler2Action | `AssignTickler.do` | AssignTickler.jsp, drilldownDisplayController.js |
| BulkPatientDashboard2Action | `BulkPatientAction.do` | DrilldownDisplay.jsp, drilldownDisplayController.js |
| ExportResults2Action | `ExportResults.do` | DrilldownDisplay.jsp |
| SaveOnCallClinic2Action | `admin/oncallClinic.do` | oscarOnCallClinic.jsp |
| ProgramManager2Action | `PMmodule/ProgramManager.do` | 14+ PMmodule admin JSPs |
| DisplayInvoiceLogo2Action | `DisplayInvoiceLogo.do` | billingON3rdInv.jsp |
| BillingONPayments2Action | `billingON3rdPayments.do` | billingON3rdPayments.jsp |
| CreateBillingReport2Action | `createBillingReportAction.do` | billingAccountReports.jsp |
| PatientEndYearStatement2Action | `endYearStatement.do` | admin.jsp, endYearStatement.jsp |
| PaymentType2Action | `managePaymentType.do` | manageBillingPaymentType.jsp |
| BillingInvoice2Action | `BillingInvoice.do` | billingON3rdInv.jsp, billingONStatus.jsp |
| Contact2Action | `demographic/Contact.do` | 10+ contact/demographic JSPs |
| Demographic2Action | `demographicSupport.do` | demographiceditdemographic.js.jsp |
| Pregnancy2Action | `Pregnancy.do` | formonarenhancedpg1/2.jsp (20+ calls) |
| DisplayImage2Action | `eform/displayImage.do` | eformGenerator.jsp, efmimagemanager.jsp |
| ManageEForm2Action | `eform/manageEForm.do` | efmformmanager*.jsp |

Additional active actions verified but not listed in detail: `AddEditDocument2Action`, `AddEditHtml2Action`, `CombinePDF2Action`, `DocumentPreview2Action`, `DocumentUpload2Action`, `ManageDocument2Action`, `SplitDocument2Action`, `ImportDemographicDataAction42Action`, `ImportLogDownload2Action`, `PrintClientLabLabel2Action`, `PrintDemoAddressLabel2Action`, `PrintDemoChartLabel2Action`, `MeasurementData2Action`, `SearchDemographicAutoComplete2Action`, `SearchProviderAutoComplete2Action`.

</details>

### CXF REST Services (24/32 active)

Active REST services confirmed with callers (not exhaustively listed here) include: `AllergyService`, `AppService`, `BillingService`, `ConsentService`, `DemographicService`, `DocumentService`, `FormsService`, `InboxService`, `LabService`, `MeasurementService`, `NotesService`, `PersonaService`, `ProductDispensingService`, `ProviderService`, `ReportingService`, `ScheduleService`, `TicklerWebService`, and others.

---

## Summary of Recommendations

### Immediate Removal Candidates (Low Risk)

1. **`TestActionW2Action`** - Test/prototype code, no mapping, no callers, no dependencies
2. **`ConsultationAttachDocs2Action`** + its struts.xml `attachDocs` mapping - Dead entry pointing to non-existent JSP

### Removal Candidates (Medium Risk - verify no external integrations)

3. **`ManageDashboard2Action`** - Fully implemented but never wired. Verify no planned UI work depends on it.
4. **8 orphaned REST services** - These are registered in CXF and accept HTTP requests even though no internal code calls them. They represent unnecessary attack surface. However, verify that no external integrations (mobile apps, third-party systems, API consumers) depend on them before removal.

### Security Note

The 8 orphaned REST services are actively deployed and accepting requests despite having no internal callers. If external API consumers (e.g., mobile apps, integration partners) don't use them either, they should be removed or disabled to reduce the attack surface. Each orphaned REST endpoint is a potential vector for unauthorized data access.

---

*Report generated by Claude Code audit on 2026-02-16*
*Methodology: Exhaustive cross-referencing of all AJAX endpoints against JSP, JavaScript, and Java source files*
