# GPL-2-Only File Audit Report

**Date:** 2026-02-16
**Scope:** All `.java`, `.jsp`, `.jspf`, `.js.jsp` files with GPL-2-only (not GPL-2+) license headers
**Repository:** carlos-emr/carlos

## Executive Summary

**108 files** carry GPL-2-only license headers (no "or later" language), all originating from **Indivica Inc. (2008-2012)**. Of these:

| Category | Files | Description |
|----------|------:|-------------|
| **Java** | 79 | Source files in `src/main/java/` |
| **JSP** | 29 | View files in `src/main/webapp/` |
| **JSPF** | 0 | None found |
| **JS.JSP** | 0 | None found |

### Removability Breakdown

| Removability | Files | Description |
|-------------|------:|-------------|
| **HIGH** (remove now) | 30 | Dead code, trivially replaceable, or security risks |
| **MEDIUM** (rewrite needed) | 30 | Active but small scope; straightforward to rewrite under GPL-2+ |
| **LOW** (core infrastructure) | 48 | Deeply integrated; requires significant refactoring to replace |

### License Header Pattern

All 108 files share the identical Indivica Inc. GPL-2-only header:

```
Copyright (c) 2008-2012 Indivica Inc.

This software is made available under the terms of the
GNU General Public License, Version 2, 1991 (GPLv2).
License details are available via "indivica.ca/gplv2"
and "gnu.org/licenses/gpl-2.0.html".
```

This is GPL-2-only because it references "Version 2, 1991 (GPLv2)" specifically with **no** "or later", "or (at your option) any later version", or similar upgrade language.

---

## TIER 1: HIGH Removability -- Recommended for Immediate Action

These 30 files can be removed with minimal or no impact. They are dead code, trivially replaceable, or have existing GPL-2+ alternatives.

### 1.1 Dead Code (Zero References -- Safe to Delete)

| # | File | Rationale |
|---|------|-----------|
| 1 | `src/main/java/.../form/model/FormBooleanValuePK.java` | Superseded by `FormValuePK`; zero imports anywhere |
| 2 | `src/main/java/.../documentManager/data/DocumentUpload2Form.java` | Zero references from any Java, JSP, or XML file |
| 3 | `src/main/java/.../commn/dao/ReadLabDao.java` | Zero external Java consumers |
| 4 | `src/main/java/.../commn/dao/ReadLabDaoImpl.java` | Zero external Java consumers |
| 5 | `src/main/java/.../commn/model/ReadLab.java` | Zero external Java consumers (table referenced in raw SQL only) |
| 6 | `src/main/webapp/oscarMDS/SelectProviderSimple.jsp` | Zero references; `SelectProvider.jsp` (GPL-2+) is the active version |
| 7 | `src/main/webapp/documentManager/documentUploaderFirefox36.jsp` | Zero references; superseded by `documentUploader.jsp` |
| 8 | `src/main/webapp/oscarEncounter/oscarConsultationRequest/attachConsultation2.jsp` | Explicitly deprecated ("Please use attachDocument.jsp"); zero references |

**Action:** Delete these 8 files immediately. No code changes needed elsewhere.

### 1.2 Trivially Replaceable (< 50 lines of logic)

| # | File | Lines | What It Does | Replacement Effort |
|---|------|------:|--------------|-------------------|
| 9 | `src/main/webapp/oscarMDS/Splitclose.jsp` | 4 | `opener.location.reload(); self.close();` | 5 minutes: write 4-line GPL-2+ JSP |
| 10 | `src/main/webapp/hospitalReportManager/ajaxResponse.jsp` | 4 | Outputs "Success" or "Error encountered" | 5 minutes: write 4-line GPL-2+ JSP |
| 11 | `src/main/webapp/oscarEncounter/oscarMeasurements/FlowUpdate.jsp` | 32 | Displays flowsheet update error message | 15 minutes: write simple error page |
| 12 | `src/main/webapp/eform/efmformrtl_templates.jsp` | 37 | Generates dropdown of RTL templates | 15 minutes: rewrite template list |
| 13 | `src/main/webapp/admin/rebootConfirmation.jsp` | ~40 | Confirmation dialog for reboot | 15 minutes: simple form rewrite |
| 14 | `src/main/java/.../billing/CA/ON/util/EDTFolder.java` | ~30 | Enum with 4 values (INBOX/OUTBOX/SENT/ARCHIVE) | 10 minutes: trivial enum rewrite |
| 15 | `src/main/java/.../lab/ca/all/pageUtil/ORUR01Manager.java` | ~50 | HL7 version casting utility; mostly dead code | 10 minutes: inline the one used method |

**Action:** Rewrite each under CARLOS GPL-2+ header. Total effort: ~2 hours.

### 1.3 Regional Lab Handlers (Unused Plugin-Style Handlers)

These are HL7 handlers for specific regional Canadian labs (ICL, MEDVUE, PFHT, TDIS) from the Indivica era (2008-2012). They are loaded via XML config reflection only -- no direct Java imports. Removing them causes the system to fall back to `DefaultGenericHandler`.

| # | File | Lab System |
|---|------|-----------|
| 16 | `src/main/java/.../lab/ca/all/upload/handlers/ICLHandler.java` | ICL (Independent Clinical Labs) |
| 17 | `src/main/java/.../lab/ca/all/upload/handlers/MEDVUEHandler.java` | MEDVUE (radiology) |
| 18 | `src/main/java/.../lab/ca/all/upload/handlers/PFHTHandler.java` | PFHT (Perth/Smiths Falls) |
| 19 | `src/main/java/.../lab/ca/all/upload/handlers/TDISHandler.java` | TDIS (Toronto District) |
| 20 | `src/main/java/.../lab/ca/all/util/ICLUtilities.java` | ICL file splitter |
| 21 | `src/main/java/.../lab/ca/all/util/MEDVUEUtilities.java` | MEDVUE file splitter |
| 22 | `src/main/java/.../lab/ca/all/util/PFHTUtilities.java` | PFHT file splitter |

**Action:** Remove these 7 files + their entries in `message_config.xml` (both parsers and upload). The generic `DefaultGenericHandler` handles standard HL7 ORU_R01 messages.

**Caveat:** The 4 *parser* handlers (in `parsers/` -- separate from `upload/handlers/`) are rated MEDIUM (see Tier 2) because they have string-based references in JSP display code that would become dead branches.

### 1.4 Security Risk

| # | File | Issue |
|---|------|-------|
| 23 | `src/main/java/.../util/OscarStatus2Action.java` | Uses `Runtime.exec()` with unsanitized paths and DB credentials as command-line args. Replace with proper monitoring. |

**Action:** Remove and replace with a secure implementation or use devcontainer `server` and `db-connect` scripts.

### 1.5 HRM Internal-Only Files (Removable if HRM Module Replaced)

These files have zero external references outside the HRM package. They can be removed freely *if* the HRM module is being replaced/rewritten.

| # | File | Purpose |
|---|------|---------|
| 24 | `src/main/java/.../hospitalReportManager/dao/HRMCategoryDao.java` | Category CRUD |
| 25 | `src/main/java/.../hospitalReportManager/dao/HRMProviderConfidentialityStatementDao.java` | Confidentiality statement CRUD |
| 26 | `src/main/java/.../hospitalReportManager/dao/HRMSubClassDao.java` | Subclass lookup |
| 27 | `src/main/java/.../hospitalReportManager/model/HRMCategory.java` | Category entity |
| 28 | `src/main/java/.../hospitalReportManager/model/HRMProviderConfidentialityStatement.java` | Statement entity |
| 29 | `src/main/java/.../hospitalReportManager/model/HRMReportCriteria.java` | Display criteria POJO |
| 30 | `src/main/java/.../hospitalReportManager/model/HRMSubClass.java` | Subclass mapping entity |

**Action:** These are only removable as part of a complete HRM module replacement. See Tier 3 for the HRM module assessment.

---

## TIER 2: MEDIUM Removability -- Rewrite Candidates

These 30 files are actively used but have limited scope. Each could be rewritten under GPL-2+ with moderate effort.

### 2.1 Admin/Configuration Pages (Simple Forms)

| # | File | Refs | Rewrite Effort |
|---|------|-----:|---------------|
| 1 | `src/main/webapp/admin/clinicNbrManage.jsp` | 2 | Small: CRUD form using existing GPL-2+ ClinicNbrDao |
| 2 | `src/main/webapp/admin/displayDocumentCategories.jsp` | 2 | Small: read-only display using GPL-2+ EDocUtil |
| 3 | `src/main/webapp/admin/oscarStatus.jsp` | 2 | Medium: system status dashboard (pairs with OscarStatus2Action) |
| 4 | `src/main/webapp/documentManager/addNewDocumentCategories.jsp` | 1 | Small: simple add form |
| 5 | `src/main/webapp/documentManager/changeStatus.jsp` | 1 | Small: simple status change form |
| 6 | `src/main/webapp/eform/efmformrtl_config.jsp` | 3 | Small: single checkbox config toggle |
| 7 | `src/main/webapp/hospitalReportManager/hospitalReportManager.jsp` | 5 | Medium: confidentiality statement form |

**Note:** Files 1-2 and 4-5 form a natural **document categories cluster** that can be rewritten together.

### 2.2 Regional Lab Parsers (String-Referenced)

These parser handlers have no direct Java imports but have string-based type checks in JSP display code (e.g., `getMsgType().equals("MEDVUE")`). Removing them makes those JSP branches dead code -- no errors, just unused conditional paths.

| # | File | String Refs | Notes |
|---|------|------------:|-------|
| 8 | `src/main/java/.../lab/ca/all/parsers/ICLHandler.java` | 0 | Cleanest removal |
| 9 | `src/main/java/.../lab/ca/all/parsers/MEDVUEHandler.java` | 7 | JSP/PDF display conditionals |
| 10 | `src/main/java/.../lab/ca/all/parsers/PFHTHandler.java` | 10 | JSP/PDF/export conditionals |
| 11 | `src/main/java/.../lab/ca/all/parsers/TDISHandler.java` | 6 | Uses HL7 v2.5 (no generic fallback) |

**Action:** Remove + clean up string-based conditionals in `labDisplayAjax.jsp`, `labDisplay.jsp`, `LabPDFCreator.java`, `DemographicExportAction42Action.java`.

### 2.3 Struts2 Actions (Active Mappings, Limited Scope)

| # | File | Struts Mapping | Rewrite Effort |
|---|------|---------------|---------------|
| 12 | `src/main/java/.../documentManager/actions/AddDocumentType2Action.java` | `documentManager/addDocumentType` | Small |
| 13 | `src/main/java/.../documentManager/actions/ChangeDocStatus2Action.java` | `documentManager/changeDocStatus` | Small |
| 14 | `src/main/java/.../documentManager/actions/SplitDocument2Action.java` | `documentManager/SplitDocument` | Medium |
| 15 | `src/main/java/.../eform/actions/FetchUpdatedData2Action.java` | `eform/fetchUpdatedData` | Small |
| 16 | `src/main/java/.../eform/actions/RTLSettings2Action.java` | `eform/IndivicaRichTextLetterSettings` | Small |
| 17 | `src/main/java/.../encounter/oscarMeasurements/pageUtil/FormUpdate2Action.java` | `oscarEncounter/FormUpdate` | Medium |
| 18 | `src/main/java/.../encounter/pageUtil/EctDisplayHRM2Action.java` | Encounter left-nav HRM display | Medium |
| 19 | `src/main/java/.../commn/web/HealthCardSearch2Action.java` | `common/healthCardSearch` | Medium (verify JSP usage) |
| 20 | `src/main/java/.../hospitalReportManager/HRMDisplayReport2Action.java` | `hospitalReportManager/Display` | Medium |
| 21 | `src/main/java/.../hospitalReportManager/HRMModifyDocument2Action.java` | `hospitalReportManager/ModifyDocument` | Large (12 methods) |
| 22 | `src/main/java/.../hospitalReportManager/HRMStatementModify2Action.java` | `hospitalReportManager/StatementModify` | Small |

### 2.4 Other Medium-Removability Files

| # | File | Refs | Notes |
|---|------|-----:|-------|
| 23 | `src/main/webapp/documentManager/documentUploader.jsp` | 2 | Active upload UI; pairs with DocumentUpload2Action |
| 24 | `src/main/webapp/oscarEncounter/oscarConsultationRequest/displayImage.jsp` | 2 | Still used by `attachEform.jsp` |
| 25 | `src/main/webapp/oscarEncounter/oscarMeasurements/DiabFlowSheet.jsp` | 1 | Generic flowsheet exists as fallback |
| 26 | `src/main/webapp/provider/formIntake.jsp` | 1 | Behind disabled-by-default property flag |
| 27 | `src/main/webapp/library/eforms/signatureControl.jsp` | 1 | JS wrapper; direct iframe pattern exists |
| 28 | `src/main/java/.../commn/dao/HL7HandlerMSHMappingDao.java` + Impl + Model | 1 | Only used by TDISHandler (3 files) |
| 29 | `src/main/java/.../commn/dao/InboxResultsRepository.java` + Impl | 0? | Interface empty; Impl may be injected by bean name (2 files) |
| 30 | `src/main/java/.../casemgmt/web/NotePermission.java` | 1 | DTO companion to NotePermissions2Action |

---

## TIER 3: LOW Removability -- Core Infrastructure (Rewrite Required)

These 48 files are deeply integrated into the EMR. Removal requires comprehensive rewriting, not just deletion. They are grouped by functional module.

### 3.1 Hospital Report Manager (HRM) -- Ontario Module (19 files)

HRM is Ontario-specific (`isOntarioBillingRegion()` guard) but deeply integrated across **8 external modules** (inbox, lab data, consultations, eForms, email, encounter display, document manager, demographic import/export) totaling **31+ external files**.

**Cannot be removed as a standalone module without refactoring 31+ files.**

| File | External Refs | Integration Points |
|------|:------------:|--------------------|
| `HRMReport.java` | 3 | Lab module, demographic import |
| `HRMReportParser.java` | 3 | Report ingestion, inbox routing |
| `HRMUtil.java` | 8 | Primary API surface for all external modules |
| `HRMDocumentDao.java` | 4 | Central DAO, inbox queries |
| `HRMDocumentToDemographicDao.java` | 5 | eForm/consultation attachment, lab data |
| `HRMDocumentToProviderDao.java` | 3 | Inbox counts, provider sign-off |
| `HRMDocument.java` (model) | 12+ | Entity name in JPQL queries across inbox |
| `HRMDocumentToDemographic.java` (model) | 5+ | Used in eForm, consultation, lab data |
| `HRMDocumentToProvider.java` (model) | 3+ | Entity name in inbox JPQL queries |
| `HRMDocumentCommentDao.java` | 2 | Demographic export/import |
| `HRMDocumentSubClassDao.java` | 1 | Demographic import |
| `HRMDocumentComment.java` (model) | 2 | Demographic export/import |
| `HRMDocumentSubClass.java` (model) | 1 | Demographic import |
| `HRMResultsData.java` | 5 | Core inbox HRM integration |
| `displayHRMDocList.jsp` | 2 | Encounter sidebar HRM view |
| `displayHRMReport.jsp` | 2 | Full HRM report viewer (1011 lines) |

Plus 3 more HRM JSPs listed in other tiers.

### 3.2 Inbox/Lab Infrastructure (10 files)

| File | External Refs | Why Critical |
|------|:------------:|-------------|
| `InboxResultsDao.java` + Impl | 3 | Core inbox document/lab querying (669 lines of SQL) |
| `ProviderLabRoutingDao.java` + Impl + Model | 18+ | Most-referenced DAO: lab routing, document mgmt, MDS, fax |
| `Hl7TextMessageInfo.java` | 7 | Used by 5 different HL7 parser handlers |
| `DemographicExtKey.java` | 4 | Core demographic enum (80+ keys), used by DemographicManager |
| `RouteReportResults.java` | 8 | Shared infrastructure for 7+ upload handlers |
| `CreateLabelTDIS2Action.java` | 5 | Misnamed -- actually generic lab label creator for ALL lab types |

### 3.3 Consultation PDF/Fax Pipeline (4 files)

| File | External Refs | Why Critical |
|------|:------------:|-------------|
| `ConsultationPDFCreator.java` | 3 | 810 lines of iText PDF generation for consultations |
| `ImagePDFCreator.java` | 3 | PDF from attached images, extends ConsultationPDFCreator |
| `EctConsultationFormFax2Action.java` | 2 | Core consultation faxing workflow |
| `EctConsultationFormRequestPrintAction22Action.java` | 2 | Core consultation print workflow |

### 3.4 eForm PDF Generation Pipeline (3 files)

| File | Web.xml Registration | Why Critical |
|------|---------------------|-------------|
| `EFormViewForPdfGenerationServlet.java` | `/EFormViewForPdfGenerationServlet` | Main eForm-to-PDF servlet |
| `EFormImageViewForPdfGenerationServlet.java` | `/EFormImageViewForPdfGenerationServlet` | Image rendering in eForm PDFs |
| `EFormSignatureViewForPdfGenerationServlet.java` | `/EFormSignatureViewForPdfGenerationServlet` | Signature rendering in eForm PDFs |

### 3.5 Document Upload & Case Management (4 files)

| File | External Refs | Why Critical |
|------|:------------:|-------------|
| `DocumentUpload2Action.java` | struts.xml | Primary document upload handler |
| `NotePermissions2Action.java` | struts.xml + JS | Active AJAX endpoint for note permissions |
| `EReferAttachmentDataDaoImpl.java` | 1 | Ocean eReferral integration |
| `ClinicNbrDao.java` + Impl + Model | 7+ JSPs | Ontario billing admin pages |

### 3.6 Core View Pages (5 JSP files)

| File | External Refs | Why Critical |
|------|:------------:|-------------|
| `oscarMDS/Index.jsp` | 2 | **The entire lab/document inbox UI** |
| `oscarMDS/Page.jsp` | 2 | Paginated inbox content renderer |
| `oscarMDS/Split.jsp` | 3 | Document page splitter (no alternative) |
| `signature_pad/tabletSignature.jsp` | 3 | Only signature capture UI in the system |
| `billing/CA/ON/billingLreport.jsp` | 3 | Ontario MOH billing XML report viewer |
| `billing/CA/ON/viewMOHFiles.jsp` | 5+ | Ontario MOH file browser/manager |
| `eform/efmformapconfig_lookup.jsp` | 1 | eForm auto-populate AJAX backend |

---

## Recommended Action Plan

### Phase 1: Quick Wins (Estimated: 1-2 days)

1. **Delete 8 dead code files** (Tier 1.1) -- zero risk
2. **Remove 7 regional lab upload handlers + utilities** (Tier 1.3) + XML config entries
3. **Remove `OscarStatus2Action.java`** (Tier 1.4) -- security risk; replace with secure monitoring
4. **Rewrite 7 trivially small files** (Tier 1.2) under GPL-2+ headers

**Result:** 23 GPL-2-only files eliminated.

### Phase 2: Form/Admin Rewrites (Estimated: 1-2 weeks)

1. **Rewrite document categories cluster** (4 files: `displayDocumentCategories.jsp`, `addNewDocumentCategories.jsp`, `changeStatus.jsp`, `AddDocumentType2Action`, `ChangeDocStatus2Action`)
2. **Rewrite `clinicNbrManage.jsp`** + companion JSON using existing GPL-2+ ClinicNbrDao
3. **Rewrite small Struts2 actions** (`RTLSettings2Action`, `FetchUpdatedData2Action`, `FormUpdate2Action`, `SplitDocument2Action`, `HRMStatementModify2Action`)
4. **Remove 4 regional lab parsers** + clean up string-based JSP conditionals
5. **Rewrite `documentUploader.jsp`** + `DocumentUpload2Action` under GPL-2+

**Result:** ~25 more GPL-2-only files eliminated.

### Phase 3: Module-Level Rewrites (Estimated: 2-4 weeks each)

1. **HRM Module Rewrite** -- 29 GPL-2-only files (Java + JSP). Create an abstraction layer first, then rewrite the implementation under GPL-2+. Touches 31+ external files across 8 modules.
2. **Consultation PDF Pipeline** -- 4 files (810+ lines of iText PDF code). Rewrite `ConsultationPDFCreator`, `ImagePDFCreator`, and associated actions.
3. **eForm PDF Pipeline** -- 3 servlet files. Rewrite the PDF generation servlets.
4. **Inbox Infrastructure** -- `InboxResultsDao` (669 lines), `ProviderLabRoutingDao` (18+ consumers). Most impactful rewrite.

### Phase 4: Core Rewrites (Estimated: 1-2 months)

1. **`DemographicExtKey.java`** -- Core enum with 80+ keys. Straightforward rewrite but needs thorough testing.
2. **`CreateLabelTDIS2Action.java`** -- Rename and rewrite (it's generic, not TDIS-specific).
3. **`NotePermissions2Action.java`** + `NotePermission.java` -- Active AJAX backend for note permissions.
4. **Inbox/MDS views** (`Index.jsp`, `Page.jsp`, `Split.jsp`) -- The most complex JSP rewrites in the codebase.
5. **`tabletSignature.jsp`** -- Only signature capture UI; needs careful replacement.
6. **`RouteReportResults.java`** -- Trivial class (3 lines) but 8+ consumers; easy rewrite.

---

## Appendix: Complete File List by Package

### Java Files (79 total)

#### billing/CA/ON/util/ (1 file)
- `EDTFolder.java` -- HIGH

#### casemgmt/web/ (2 files)
- `NotePermission.java` -- LOW
- `NotePermissions2Action.java` -- LOW

#### commn/dao/ (14 files)
- `ClinicNbrDao.java` -- LOW
- `ClinicNbrDaoImpl.java` -- LOW
- `EReferAttachmentDataDaoImpl.java` -- LOW
- `HL7HandlerMSHMappingDao.java` -- MEDIUM
- `HL7HandlerMSHMappingDaoImpl.java` -- MEDIUM
- `InboxResultsDao.java` -- LOW
- `InboxResultsDaoImpl.java` -- LOW
- `InboxResultsRepository.java` -- MEDIUM
- `InboxResultsRepositoryImpl.java` -- MEDIUM
- `ProviderLabRoutingDao.java` -- LOW
- `ProviderLabRoutingDaoImpl.java` -- LOW
- `ReadLabDao.java` -- HIGH (dead code)
- `ReadLabDaoImpl.java` -- HIGH (dead code)

#### commn/model/ (6 files)
- `ClinicNbr.java` -- LOW
- `HL7HandlerMSHMapping.java` -- MEDIUM
- `Hl7TextMessageInfo.java` -- LOW
- `ProviderLabRoutingModel.java` -- LOW
- `ReadLab.java` -- HIGH (dead code)
- `enumerator/DemographicExtKey.java` -- LOW

#### commn/web/ (1 file)
- `HealthCardSearch2Action.java` -- MEDIUM

#### documentManager/actions/ (4 files)
- `AddDocumentType2Action.java` -- MEDIUM
- `ChangeDocStatus2Action.java` -- MEDIUM
- `DocumentUpload2Action.java` -- LOW
- `SplitDocument2Action.java` -- MEDIUM

#### documentManager/data/ (1 file)
- `DocumentUpload2Form.java` -- HIGH (dead code)

#### eform/actions/ (2 files)
- `FetchUpdatedData2Action.java` -- MEDIUM
- `RTLSettings2Action.java` -- MEDIUM

#### eform/util/ (3 files)
- `EFormImageViewForPdfGenerationServlet.java` -- LOW
- `EFormSignatureViewForPdfGenerationServlet.java` -- LOW
- `EFormViewForPdfGenerationServlet.java` -- LOW

#### encounter/oscarConsultationRequest/pageUtil/ (4 files)
- `ConsultationPDFCreator.java` -- LOW
- `EctConsultationFormFax2Action.java` -- LOW
- `EctConsultationFormRequestPrintAction22Action.java` -- LOW
- `ImagePDFCreator.java` -- LOW

#### encounter/oscarMeasurements/pageUtil/ (1 file)
- `FormUpdate2Action.java` -- MEDIUM

#### encounter/pageUtil/ (1 file)
- `EctDisplayHRM2Action.java` -- MEDIUM

#### form/model/ (1 file)
- `FormBooleanValuePK.java` -- HIGH (dead code)

#### hospitalReportManager/ (6 files)
- `HRMDisplayReport2Action.java` -- MEDIUM
- `HRMModifyDocument2Action.java` -- MEDIUM
- `HRMReport.java` -- LOW
- `HRMReportParser.java` -- LOW
- `HRMStatementModify2Action.java` -- MEDIUM
- `HRMUtil.java` -- LOW

#### hospitalReportManager/dao/ (8 files)
- `HRMCategoryDao.java` -- HIGH (internal only)
- `HRMDocumentCommentDao.java` -- MEDIUM
- `HRMDocumentDao.java` -- LOW
- `HRMDocumentSubClassDao.java` -- MEDIUM
- `HRMDocumentToDemographicDao.java` -- LOW
- `HRMDocumentToProviderDao.java` -- LOW
- `HRMProviderConfidentialityStatementDao.java` -- HIGH (internal only)
- `HRMSubClassDao.java` -- HIGH (internal only)

#### hospitalReportManager/model/ (9 files)
- `HRMCategory.java` -- HIGH (internal only)
- `HRMDocument.java` -- LOW
- `HRMDocumentComment.java` -- MEDIUM
- `HRMDocumentSubClass.java` -- MEDIUM
- `HRMDocumentToDemographic.java` -- LOW
- `HRMDocumentToProvider.java` -- LOW
- `HRMProviderConfidentialityStatement.java` -- HIGH (internal only)
- `HRMReportCriteria.java` -- HIGH (internal only)
- `HRMSubClass.java` -- HIGH (internal only)

#### lab/ca/all/pageUtil/ (2 files)
- `CreateLabelTDIS2Action.java` -- LOW (misnamed; generic lab label creator)
- `ORUR01Manager.java` -- HIGH (mostly dead code)

#### lab/ca/all/parsers/ (4 files)
- `ICLHandler.java` -- MEDIUM
- `MEDVUEHandler.java` -- MEDIUM
- `PFHTHandler.java` -- MEDIUM
- `TDISHandler.java` -- MEDIUM

#### lab/ca/all/upload/ (1 file)
- `RouteReportResults.java` -- LOW (8+ consumers)

#### lab/ca/all/upload/handlers/ (4 files)
- `ICLHandler.java` -- HIGH (XML-only registration)
- `MEDVUEHandler.java` -- HIGH (XML-only registration)
- `PFHTHandler.java` -- HIGH (XML-only registration)
- `TDISHandler.java` -- HIGH (XML-only registration)

#### lab/ca/all/util/ (3 files)
- `ICLUtilities.java` -- HIGH (only used by ICLHandler)
- `MEDVUEUtilities.java` -- HIGH (only used by MEDVUEHandler)
- `PFHTUtilities.java` -- HIGH (only used by PFHTHandler)

#### lab/ca/on/ (1 file)
- `HRMResultsData.java` -- LOW (core inbox HRM integration)

#### util/ (1 file)
- `OscarStatus2Action.java` -- HIGH (security risk)

### JSP Files (29 total)

#### admin/ (4 files)
- `clinicNbrManage.jsp` -- MEDIUM
- `displayDocumentCategories.jsp` -- MEDIUM
- `oscarStatus.jsp` -- MEDIUM
- `rebootConfirmation.jsp` -- HIGH (child of oscarStatus)

#### billing/CA/ON/ (2 files)
- `billingLreport.jsp` -- LOW
- `viewMOHFiles.jsp` -- LOW

#### documentManager/ (4 files)
- `addNewDocumentCategories.jsp` -- MEDIUM
- `changeStatus.jsp` -- MEDIUM
- `documentUploader.jsp` -- MEDIUM
- `documentUploaderFirefox36.jsp` -- HIGH (dead code)

#### eform/ (3 files)
- `efmformapconfig_lookup.jsp` -- LOW
- `efmformrtl_config.jsp` -- MEDIUM
- `efmformrtl_templates.jsp` -- HIGH

#### hospitalReportManager/ (4 files)
- `ajaxResponse.jsp` -- HIGH
- `displayHRMDocList.jsp` -- LOW
- `displayHRMReport.jsp` -- LOW
- `hospitalReportManager.jsp` -- MEDIUM

#### library/eforms/ (1 file)
- `signatureControl.jsp` -- MEDIUM

#### oscarEncounter/ (4 files)
- `oscarConsultationRequest/attachConsultation2.jsp` -- HIGH (deprecated)
- `oscarConsultationRequest/displayImage.jsp` -- MEDIUM
- `oscarMeasurements/DiabFlowSheet.jsp` -- MEDIUM
- `oscarMeasurements/FlowUpdate.jsp` -- HIGH

#### oscarMDS/ (5 files)
- `Index.jsp` -- LOW
- `Page.jsp` -- LOW
- `SelectProviderSimple.jsp` -- HIGH (dead code)
- `Split.jsp` -- LOW
- `Splitclose.jsp` -- HIGH

#### provider/ (1 file)
- `formIntake.jsp` -- MEDIUM

#### signature_pad/ (1 file)
- `tabletSignature.jsp` -- LOW
