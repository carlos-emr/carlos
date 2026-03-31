# Unused Struts Action Mappings

> **Analysis Date**: 2026-03-31
> **Total Actions in struts.xml**: 477 unique
> **Unused Action Mappings**: 44 (9.2%)

## Methodology

Every action name in `src/main/webapp/WEB-INF/classes/struts.xml` was checked against
**all file types** in the codebase for any reference. Search patterns included:

1. **Direct URL**: `actionName.do` in JSP, JSPF, JS, JS.JSP, HTML, tag, Java files
2. **Path fragments**: Full and partial action paths in any string context
3. **Relative URLs**: JSP forms using relative paths (e.g., `action="SaveEncounter.do"`)
4. **Struts result redirects**: `<result>` tags in struts.xml forwarding/redirecting to other actions
5. **EctDisplayAction dispatch**: The `Actions` HashMap in `EctDisplayAction.java`
6. **Navbar loader**: `navBarLoader()` in `newCaseManagementView.js.jsp` and `encounter-head.jspf`
7. **Dynamic URL construction**: JavaScript string concatenation building `.do` URLs
8. **Spring XML beans**: `applicationContext*.xml` references to action classes
9. **Programmatic dispatch**: `sendRedirect()` and `getRequestDispatcher()` in Java code
10. **Tiles definitions**: Struts Tiles result references (note: `struts-tiles` is excluded from pom.xml)
11. **XSL/XSLT, DRL, properties, SQL, shell scripts, YAML, JSON** files
12. **JSPF includes**: `.jspf` fragment files included by JSP pages

**Excluded from consideration**: Documentation files (`docs/`), test files (`src/test/`),
JavaDoc `@see` tags, domain model/DAO/service classes that share names with action classes,
and i18n property keys that coincidentally contain action name substrings.

---

## Unused Action Mappings (44)

### PMmodule — Program Management (7 actions)

The entire PMmodule admin UI appears to be dead code. The `struts-tiles` dependency is
**excluded** from `pom.xml`, so the `page.pmm.*` result values in these actions cannot resolve.
PMmodule JSPs only reference `FacilityManager.do`, `ProgramManager.do`, `ProgramManagerView.do`,
`StaffManager.do`, and `ClientManager.do` — none of the actions below.

| # | Action Name | Action Class | Notes |
|---|-------------|-------------|-------|
| 1 | `PMmodule/Admin/DefaultRoleAccess` | `DefaultRoleAccess2Action` | Self-referencing result only; model/DAO widely used but action URL unused |
| 2 | `PMmodule/Admin/SysAdmin` | `AdminHome2Action` | |
| 3 | `PMmodule/AgencyManager` | `AgencyManager2Action` | Service interface `AgencyManager` exists but is unrelated to action |
| 4 | `PMmodule/AllVacancies` | `AllWaitingList2Action` | `displayAllVacancies` is a Facility property, not this action |
| 5 | `PMmodule/Reports/BasicReport` | `BasicReport2Action` | |
| 6 | `PMmodule/Reports/ClientListsReport` | `ClientListsReport2Action` | FormBean exists but action URL never invoked |
| 7 | `PMmodule/VacancyClientMatch` | `VacancyClientMatch2Action` | Model/DAO widely used but action URL unused |

### Encounter Display — Not in Navbar (4 actions)

These `EctDisplayAction` subclasses are not registered in:
- `EctDisplayAction.Actions` HashMap (which routes `cmd` parameter dispatch)
- `navBarLoader()` in `newCaseManagementView.js.jsp`
- `encounter-head.jspf` display config

| # | Action Name | Action Class |
|---|-------------|-------------|
| 8 | `encounter/displayAppointmentHistory` | `EctDisplayAppointmentHistory2Action` |
| 9 | `encounter/displayBilling` | `EctDisplayBilling2Action` |
| 10 | `encounter/displayDiagrams` | `EctDisplayDiagram2Action` |
| 11 | `encounter/displayEHR` | `EctDisplayEHR2Action` |

### Encounter — Other (4 actions)

| # | Action Name | Action Class | Notes |
|---|-------------|-------------|-------|
| 12 | `encounter/displayPhotos` | `EctDisplayPhotos2Action` | |
| 13 | `encounter/immunization/config/DeleteImmunizationSets` | `EctImmDeleteImmunizationSet2Action` | JSPs use `deleteImmunizationSet` (lowercase, singular) — a different action |
| 14 | `DeleteImmunizationSets` | `EctImmDeleteImmunizationSet2Action` | Duplicate root-level mapping, also unused |
| 15 | `encounter/oscarConsultation/printAttached` | `ConsultationPrintDocs2Action` | |

### Messenger (5 actions)

`MessengerAdmin.jsp` uses `messenger.do` (the main action). `CreateMessage.jsp` posts to
`CreateMessage.do`. No messenger JSP references any of these action URLs.

| # | Action Name | Action Class | Notes |
|---|-------------|-------------|-------|
| 16 | `messenger/AddGroup` | `MsgMessengerCreateGroup2Action` | Only referenced in JavaDoc `@see` |
| 17 | `messenger/ImportDemographic` | `ImportDemographic2Action` | Action itself logs "feature removed" |
| 18 | `messenger/ProcessDoc2PDF` | `MsgDoc2PDF2Action` | |
| 19 | `messenger/SendMessage` | `MsgSendMessage2Action` | Only referenced in JavaDoc `@see` |
| 20 | `messenger/Transfer/Proceed` | `MsgProceed2Action` | Legacy test exists but action URL unused |

### Eform (3 actions)

| # | Action Name | Action Class |
|---|-------------|-------------|
| 21 | `eform/eFormAttachmentForm` | `UploadEFormAttachment2Action` |
| 22 | `eform/efmOpenEformByName` | `OpenEFormByName2Action` |
| 23 | `eform/efmPrintPDF` | `PrintPDF2Action` |

### Lookup (3 actions)

Admin uses `Lookup/LookupTableList.do` and `lookupListManagerAction.do` for lookup management.
These 3 actions are not referenced from any JSP or Java code.

| # | Action Name | Action Class |
|---|-------------|-------------|
| 24 | `Lookup/LookupCodeEdit` | `LookupCodeEdit2Action` |
| 25 | `Lookup/LookupCodeList` | `LookupCodeList2Action` |
| 26 | `Lookup/LookupList` | `LookupList2Action` |

### Reports (6 actions)

| # | Action Name | Action Class |
|---|-------------|-------------|
| 27 | `oscarReport/ShowConsult` | `RptShowConsult2Action` |
| 28 | `oscarReport/oscarMeasurements/SetupSelectCDMReport` | `RptSetupSelectCDMReport2Action` |
| 29 | `oscarReport/reportByTemplate/exportTemplateAction` | `ExportTemplate2Action` |
| 30 | `report/CreateDemographicSet` | `RptCreateDemographicSet2Action` |
| 31 | `report/GenerateSpreadsheet` | `GeneratePatientSpreadSheetList2Action` |
| 32 | `report/printLabDaySheetAction` | `printLabDaySheet2Action` |

### Other (12 actions)

| # | Action Name | Action Class | Notes |
|---|-------------|-------------|-------|
| 33 | `BillingONReview` | `BillingONReview2Action` | |
| 34 | `MeasurementHL7Uploader` | `MeasurementHL7Uploader2Action` | |
| 35 | `OscarChartPrint` | `EChartPrint2Action` | `OscarChartPrinter` utility is unrelated |
| 36 | `Provider/showPersonal` | `DisplayPersonalInfoAppointment2Action` | |
| 37 | `attachDocs` | `ConsultationAttachDocs2Action` | |
| 38 | `billing/CA/BC/associateCodesAction` | `AssociateCodes2Action` | |
| 39 | `casemgmt/RegisterCppCode` | `RegisterCppCode2Action` | |
| 40 | `form/AddRHWorkFlow` | `FrmFormAddRHWorkFlow2Action` | Only in DRL comment |
| 41 | `login/recordLogin` | `LoginAgreement2Action` | |
| 42 | `mcedt/openAutoUpload` | `Resource2Action` (mailbox) | Class shared with `mcedt/mcedt` and `mcedt/kaimcedt` — only this mapping is unused |
| 43 | `notification/create` | `ProviderNotification2Action` | |
| 44 | `oscarRx/addFavoriteViewScript` | `RxAddFavorite2Action` | Class shared with `addFavorite2`, `addFavoriteWriteScript`, `addFavoriteStaticScript` — only this mapping is unused |

---

## Shared Classes — Safe to Remove Mapping Only

Two action classes are used by multiple struts mappings. Only the **specific mapping** listed
above is unused; the class itself must be kept:

- **`Resource2Action`** (`mcedt/mailbox`): Used by `mcedt/kaimcedt` and `mcedt/mcedt`
- **`RxAddFavorite2Action`**: Used by `oscarRx/addFavorite2`, `oscarRx/addFavoriteWriteScript`, `oscarRx/addFavoriteStaticScript`

## Actions Confirmed As Used (initially suspected unused)

These were false positives caught during the intensive search:

| Action | Where Referenced |
|--------|-----------------|
| `ArchiveView` | `src/main/webapp/provider/infirmarydemographiclist.jspf` (4 URL refs) |
| `encounter/displayEconsultation` | `src/main/webapp/encounter/includes/encounter-head.jspf` |
| `encounter/SaveEncounter` | `src/main/webapp/encounter/Index.jsp` (AJAX + form) |
| `encounter/SaveEncounter2` | `src/main/webapp/encounter/Index2.jsp` (form) |
| `encounter/oscarMeasurements/FlowSheetDrugAction` | `src/main/webapp/encounter/oscarMeasurements/TemplateFlowSheetPage.jspf` (2 forms) |
| `web/dashboard/OutcomesDashboard` | `src/main/webapp/web/dashboard/display/sharedOutcomesDashboard.jsp` (AJAX) |
| `PMmodule/Reports/ProgramActivityReport` | `src/main/webapp/administration/leftNav.jspf` (xlink rel) |
| `billing/CA/BC/ProcessRemittance` | Struts result from `ManageTeleplan` action |
| `encounter/oscarConsultationRequest/printPdf2` | Struts result from `ConsultationLookup2Action` |
| `encounter/immunization/*` (5 actions) | JSP relative URLs in immunization config pages |
| `oscarResearch/oscarDxResearch/dxResearchUpdate` | `src/main/webapp/oscarResearch/oscarDxResearch/dxResearch.jsp` |

---

Generated with Claude Code on 2026-03-31
