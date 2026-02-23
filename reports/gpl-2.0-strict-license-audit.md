# GPL 2.0 Strict License Audit Report

**Date**: 2026-02-23
**Scope**: All source files (.java, .jsp, .jspf, .js, .css, .json, .xml, .svg) in CARLOS EMR
**Objective**: Identify files licensed under GPL 2.0 strict (no "or later" clause), assess their content, and recommend actions

---

## Executive Summary

The CARLOS EMR project is licensed GPL-2.0+ (v2 or later) per `NOTICE.md` and `COPYING.md`. However, **106 source files** carry GPL 2.0 **strict** headers (no "or later" clause), primarily from two contributors:

| Copyright Holder | Files | Years | Header Style |
|-----------------|-------|-------|-------------|
| Indivica Inc. | 102 | 2008-2012 | `GNU General Public License, Version 2, 1991 (GPLv2)` |
| WELL EMR Group Inc. | 3 | 2021 | Same format as Indivica |
| Magenta Health (using Indivica template) | 1 | 2025 | Same format (likely copy-paste of template) |

Additionally, **5 Oracle JDK javadoc files** in `docs/static/` use "GPL version 2 only" with Classpath Exception, and **2 third-party JS libraries** have GPL 2.0 strict options (but are dual-licensed with MIT/BSD alternatives).

**Key finding**: No file has dual GPL 2.0 strict + GPL 2.0+ headers. The licensing is cleanly separated per file.

---

## 1. Complete Inventory of GPL 2.0 Strict Files

### 1.1 Java Files (75 files)

#### Models / POJOs (15 files) — Minimal copyrightable content

All are pure data-holder classes with only getters, setters, constructors, and trivial computed fields:

| File | Lines | Copyright | Classification |
|------|-------|-----------|---------------|
| `hospitalReportManager/model/HRMCategory.java` | 66 | Indivica | PURE_POJO |
| `hospitalReportManager/model/HRMDocument.java` | 355 | Indivica | MOSTLY_POJO (3 static Comparators) |
| `hospitalReportManager/model/HRMDocumentComment.java` | 83 | Indivica | PURE_POJO |
| `hospitalReportManager/model/HRMDocumentSubClass.java` | 103 | Indivica | PURE_POJO |
| `hospitalReportManager/model/HRMDocumentToDemographic.java` | 66 | Indivica | PURE_POJO |
| `hospitalReportManager/model/HRMDocumentToProvider.java` | 85 | Indivica | PURE_POJO |
| `hospitalReportManager/model/HRMProviderConfidentialityStatement.java` | 46 | Indivica | PURE_POJO |
| `hospitalReportManager/model/HRMReportCriteria.java` | 99 | Magenta Health* | PURE_POJO |
| `hospitalReportManager/model/HRMSubClass.java` | 99 | Indivica | PURE_POJO |
| `commn/model/ClinicNbr.java` | 74 | Indivica | MOSTLY_POJO (trim in setters) |
| `commn/model/HL7HandlerMSHMapping.java` | 86 | Indivica | PURE_POJO |
| `commn/model/Hl7TextMessageInfo.java` | 90 | Indivica | PURE_POJO |
| `commn/model/ProviderLabRoutingModel.java` | 136 | Indivica | MOSTLY_POJO (trim/null-guard) |
| `commn/model/ReadLab.java` | 66 | Indivica | PURE_POJO |
| `form/model/FormBooleanValuePK.java` | 64 | WELL EMR | PURE_POJO |

\* *HRMReportCriteria.java uses the Indivica GPL 2.0 header template (even references `indivica.ca/gplv2`) but the copyright holder is Magenta Health — likely a copy-paste of the header template.*

#### DAO Interfaces (8 files) — Method signatures only

| File | Lines | Methods | Notes |
|------|-------|---------|-------|
| `commn/dao/ClinicNbrDao.java` | 31 | 3 | Simple CRUD signatures |
| `commn/dao/HL7HandlerMSHMappingDao.java` | 24 | 1 | `findByFacility(String)` |
| `commn/dao/InboxResultsDao.java` | 43 | 5 | Inbox population methods |
| `commn/dao/InboxResultsRepository.java` | 27 | 0 | **DEAD CODE** — all methods commented out |
| `commn/dao/ProviderLabRoutingDao.java` | 75 | ~16 | Lab routing query signatures |
| `hospitalReportManager/dao/HRMCategoryDao.java` | 80 | ~8 | Category CRUD |
| `hospitalReportManager/dao/HRMDocumentCommentDao.java` | 47 | ~4 | Comment CRUD |
| `hospitalReportManager/dao/HRMDocumentSubClassDao.java` | 56 | ~5 | Subclass queries |
| `hospitalReportManager/dao/HRMDocumentToDemographicDao.java` | 156 | ~12 | Demographic linking |
| `hospitalReportManager/dao/HRMDocumentToProviderDao.java` | 200 | ~15 | Provider routing |
| `hospitalReportManager/dao/HRMDocumentDao.java` | 275 | ~20 | Core HRM document queries |
| `hospitalReportManager/dao/HRMProviderConfidentialityStatementDao.java` | 50 | ~3 | Statement CRUD |
| `hospitalReportManager/dao/HRMSubClassDao.java` | 139 | ~10 | Subclass management |

#### DAO Implementations (5 files) — Substantive query logic

| File | Lines | Logic Lines | Complexity |
|------|-------|-------------|-----------|
| `commn/dao/ClinicNbrDaoImpl.java` | 67 | ~20 | LOW — 3 simple CRUD operations |
| `commn/dao/HL7HandlerMSHMappingDaoImpl.java` | 40 | ~5 | TRIVIAL — single query |
| `commn/dao/ProviderLabRoutingDaoImpl.java` | 299 | ~150 | HIGH — 16 methods, complex native SQL with 7-table JOINs |
| `commn/dao/InboxResultsDaoImpl.java` | 668 | ~450 | HIGH — massive native SQL builder, ⚠️ **SQL injection in `isSentToProvider()`** |
| `commn/dao/InboxResultsRepositoryImpl.java` | 591 | ~400 | HIGH — modern inbox query engine, properly parameterized |
| `commn/dao/EReferAttachmentDataDaoImpl.java` | 48 | ~12 | LOW — single query with lazy-load init |

#### Actions & Servlets (20 files) — Controller logic

| File | Lines | Complexity | Notes |
|------|-------|-----------|-------|
| `documentManager/actions/AddDocumentType2Action.java` | 79 | LOW | Add document category |
| `documentManager/actions/ChangeDocStatus2Action.java` | 88 | LOW | Change document status |
| `documentManager/actions/DocumentUpload2Action.java` | 539 | HIGH | File upload processing |
| `documentManager/actions/SplitDocument2Action.java` | 351 | HIGH | PDF splitting |
| `eform/actions/FetchUpdatedData2Action.java` | 106 | MODERATE | EForm data fetch |
| `eform/actions/RTLSettings2Action.java` | 47 | LOW | Rich text settings toggle |
| `eform/util/EFormImageViewForPdfGenerationServlet.java` | 49 | LOW | Image servlet for PDF |
| `eform/util/EFormSignatureViewForPdfGenerationServlet.java` | 73 | LOW | Signature servlet for PDF |
| `eform/util/EFormViewForPdfGenerationServlet.java` | 122 | MODERATE | EForm PDF generation |
| `encounter/.../ConsultationPDFCreator.java` | 810 | HIGH | PDF creation for consultations |
| `encounter/.../EctConsultationFormFax2Action.java` | 387 | HIGH | Consultation faxing |
| `encounter/.../EctConsultationFormRequestPrintAction22Action.java` | 254 | HIGH | Consultation printing |
| `encounter/.../ImagePDFCreator.java` | 126 | MODERATE | Image to PDF conversion |
| `encounter/.../FormUpdate2Action.java` | 364 | HIGH | Flowsheet measurement updates |
| `encounter/pageUtil/EctDisplayHRM2Action.java` | 122 | MODERATE | HRM display in encounter |
| `hospitalReportManager/HRMDisplayReport2Action.java` | 65 | LOW | Display HRM report |
| `hospitalReportManager/HRMModifyDocument2Action.java` | 447 | HIGH | HRM document modification |
| `hospitalReportManager/HRMStatementModify2Action.java` | 60 | LOW | Confidentiality statement CRUD |
| `casemgmt/web/NotePermissions2Action.java` | 465 | HIGH | ⚠️ Note permission mgmt, **missing security check** |
| `util/OscarStatus2Action.java` | 312 | HIGH | System status/restart |

#### Utility, Parser & Manager Files (12 files)

| File | Lines | Complexity | Notes |
|------|-------|-----------|-------|
| `hospitalReportManager/HRMReport.java` | 483 | HIGH | HRM report model with parsing logic |
| `hospitalReportManager/HRMReportParser.java` | 620 | HIGH | XML parser for HRM reports |
| `hospitalReportManager/HRMUtil.java` | 370 | HIGH | HRM utilities |
| `lab/ca/all/pageUtil/ORUR01Manager.java` | 62 | LOW | ORU R01 lab message manager |
| `lab/ca/all/pageUtil/CreateLabelTDIS2Action.java` | 105 | MODERATE | Lab label creation |
| `lab/ca/all/parsers/ICLHandler.java` | 617 | HIGH | ICL lab format parser |
| `lab/ca/all/parsers/MEDVUEHandler.java` | 737 | HIGH | MEDVUE lab format parser |
| `lab/ca/all/parsers/PFHTHandler.java` | 843 | HIGH | PFHT lab format parser |
| `lab/ca/all/parsers/TDISHandler.java` | 1061 | HIGH | TDIS lab format parser |
| `lab/ca/all/upload/handlers/ICLHandler.java` | 104 | MODERATE | ICL upload handler |
| `lab/ca/all/upload/handlers/MEDVUEHandler.java` | 105 | MODERATE | MEDVUE upload handler |
| `lab/ca/all/upload/handlers/PFHTHandler.java` | 98 | MODERATE | PFHT upload handler |
| `lab/ca/all/upload/handlers/TDISHandler.java` | 121 | MODERATE | TDIS upload handler |
| `lab/ca/all/upload/RouteReportResults.java` | 19 | TRIVIAL | Interface with 1 method |
| `lab/ca/all/util/ICLUtilities.java` | 156 | MODERATE | ICL utilities |
| `lab/ca/all/util/MEDVUEUtilities.java` | 173 | MODERATE | MEDVUE utilities |
| `lab/ca/all/util/PFHTUtilities.java` | 178 | MODERATE | PFHT utilities |
| `lab/ca/on/HRMResultsData.java` | 234 | HIGH | HRM results data processing |

#### Other Java Files (3 files)

| File | Lines | Complexity | Notes |
|------|-------|-----------|-------|
| `billing/CA/ON/util/EDTFolder.java` | 42 | LOW | 4-constant enum for EDT folders |
| `casemgmt/web/NotePermission.java` | 89 | TRIVIAL | POJO with constructor |
| `commn/web/HealthCardSearch2Action.java` | 97 | LOW | HIN search, returns JSON |
| `commn/model/enumerator/DemographicExtKey.java` | 177 | MODERATE | 88-constant enum, domain knowledge |

### 1.2 JSP/JSPF Files (28 files)

All copyrighted by Indivica Inc. (2008-2012).

**Trivial (4 files)**:
- `hospitalReportManager/ajaxResponse.jsp` (19 lines) — success/error message
- `oscarMDS/Splitclose.jsp` (19 lines) — window close script
- `oscarEncounter/oscarMeasurements/FlowUpdate.jsp` (31 lines) — error message display
- `eform/efmformrtl_templates.jsp` (37 lines) — option list generation

**Non-trivial (24 files)** across these modules:
- Hospital Report Manager (4 files)
- MDS / Lab Results (5 files, including Index.jsp at 815 lines)
- Document Manager (3 files)
- EForms (3 files)
- Ontario Billing (2 files)
- Encounter/Consultation (3 files)
- Admin (4 files)
- Signature (2 files)
- Provider (1 file — formIntake.jsp at 1,335 lines)

### 1.3 JavaScript/CSS Files (3 files)

All copyrighted by Indivica Inc. (2008-2012).

| File | Lines | Type | Notes |
|------|-------|------|-------|
| `js/eform_highlight.js` | 53 | JS | EForm field highlight toggle |
| `casemgmt/noteProgram.js` | 341 | JS | Clinical note program selector |
| `casemgmt/noteProgram.css` | 192 | CSS | Styles for note program selector |

### 1.4 Special Cases

#### Oracle JDK Javadoc Files (5 files in `docs/static/javadoc/`)

Licensed "GNU General Public License version 2 only" with **Classpath Exception**:
- `script.js`, `search.js`, `search-page.js`, `copy.svg`, `link.svg`

These are auto-generated JDK javadoc tooling files. The Classpath Exception makes them effectively more permissive than standard GPL 2.0. **No action required** — standard JDK output files.

#### Dual-Licensed Third-Party Libraries

| File | License | Alternative |
|------|---------|------------|
| `library/bootstrap/3.0.0/assets/js/respond.min.js` | MIT / GPLv2 | **Use under MIT** |
| `js/jquery.dataTables.js` | GPLv2 / BSD | **Use under BSD** |

These are not concerns since the non-GPL alternative license applies.

#### Unlicensed Indivica File

| File | Lines | Notes |
|------|-------|-------|
| `library/eforms/APCache.js` | 315 | Has `Copyright 2011 © Indivica` but **no license statement at all** |

This is a separate concern — copyright without license grant is more restrictive than GPL 2.0 strict.

---

## 2. POJO / Non-Copyrightable Content Assessment

### Pure POJOs (12 files) — Likely below copyright threshold

These files contain only getters, setters, constructors, and JPA annotations. They express no creative choices beyond the most obvious/standard implementation:

1. HRMCategory, HRMDocumentComment, HRMDocumentSubClass, HRMDocumentToDemographic
2. HRMDocumentToProvider, HRMProviderConfidentialityStatement, HRMSubClass
3. HL7HandlerMSHMapping, Hl7TextMessageInfo, ReadLab, FormBooleanValuePK
4. HRMReportCriteria (no JPA — plain form object)

### Mostly POJOs (3 files) — Trivial beyond threshold

- **HRMDocument.java** — POJOs + 3 trivial `Comparator` constants
- **ClinicNbr.java** — POJOs + `trimToNull()` in 2 setters
- **ProviderLabRoutingModel.java** — POJOs + trim/null-guard helpers

### DAO Interfaces (8 files) — Method signatures only

Interfaces define method names and parameter types. The creative expression is minimal — the signatures are dictated by the domain model.

---

## 3. Dead Code / Removable Files

| File | Reason | Impact |
|------|--------|--------|
| `commn/dao/InboxResultsRepository.java` | All methods commented out, empty interface | None — already identified in `docs/unused-classes-analysis.md` |
| `eform/efmformrtl_templates.jsp` | Trivial option list, 37 lines | Minimal |
| `hospitalReportManager/ajaxResponse.jsp` | 4 lines of logic (success/error string) | Minimal |
| `oscarMDS/Splitclose.jsp` | 3 lines of JS (reload + close) | Minimal |
| `oscarEncounter/oscarMeasurements/FlowUpdate.jsp` | Simple error message display | Minimal |

---

## 4. Files with GPL 2.0+ Alternatives or Substitution Potential

**No direct duplicates exist.** The GPL 2.0 strict files do not have GPL 2.0+ copies elsewhere in the codebase. However:

### Functional overlaps worth investigating:

1. **InboxResultsRepositoryImpl.java** (GPL 2.0 strict, 591 lines) is a modernized replacement for **InboxResultsDaoImpl.java** (also GPL 2.0 strict, 668 lines). Both are from Indivica, so replacing one with the other doesn't change the license situation, but **InboxResultsDaoImpl has a SQL injection vulnerability** and contains dead code paths.

2. **IsPropertiesOn.java** (GPL 2.0+, from St. Michael's Hospital) — This file was initially misidentified. It is actually **GPL 2.0+**, so no concern.

3. **EFormDao.java / EFormDaoImpl.java** — Both are **GPL 2.0+** (St. Michael's Hospital + Magenta Health). Not a concern.

---

## 5. Security Issues Discovered During Audit

| File | Issue | Severity |
|------|-------|----------|
| `commn/dao/InboxResultsDaoImpl.java:~119` | SQL injection in `isSentToProvider()` via string concatenation | HIGH |
| `casemgmt/web/NotePermissions2Action.java` | Missing `securityInfoManager.hasPrivilege()` check | MEDIUM |

---

## 6. Recommendations

### Tier 1: Immediate (No Risk)

1. **Delete `InboxResultsRepository.java`** — Dead code, all methods commented out
2. **Use dual-licensed libraries under permissive license** — `respond.min.js` (MIT) and `jquery.dataTables.js` (BSD)
3. **Fix SQL injection** in `InboxResultsDaoImpl.java:isSentToProvider()` regardless of license
4. **Add missing security check** in `NotePermissions2Action.java`

### Tier 2: Low Risk — Trivial Files Replaceable

These 4 trivial JSP files (7-37 lines each) contain no creative expression beyond boilerplate:
- `ajaxResponse.jsp` — replace with 4-line success/error check
- `Splitclose.jsp` — replace with 3-line reload+close script
- `FlowUpdate.jsp` — replace with simple error display
- `efmformrtl_templates.jsp` — replace with simple option loop

### Tier 3: POJO Replacement

The 12 pure POJO files and 8 DAO interface files contain minimal copyrightable expression. They could be rewritten from their method contracts/schema definitions:
- 12 model POJOs (getters/setters/JPA annotations driven by database schema)
- 8 DAO interfaces (method signatures driven by domain requirements)

### Tier 4: Medium Effort — Moderate Logic Files

These files have bounded logic that could be reimplemented:
- `EDTFolder.java` (42 lines, 4-constant enum)
- `NotePermission.java` (89 lines, POJO with constructor)
- `HealthCardSearch2Action.java` (97 lines, simple search)
- `ClinicNbrDaoImpl.java` (67 lines, 3 CRUD operations)
- `HL7HandlerMSHMappingDaoImpl.java` (40 lines, single query)
- `EReferAttachmentDataDaoImpl.java` (48 lines, single query)
- Various low-complexity 2Actions (AddDocumentType, ChangeDocStatus, HRMDisplayReport, HRMStatementModify, RTLSettings)

### Tier 5: High Effort — Core System Files

These would require significant effort to rewrite and are deeply integrated:

**Lab Parsers (4 files, 3,258 lines total)**:
- `ICLHandler.java`, `MEDVUEHandler.java`, `PFHTHandler.java`, `TDISHandler.java`
- These implement specific lab result format parsing. Each handles a unique Ontario lab data format.

**Hospital Report Manager (3 files, 1,473 lines total)**:
- `HRMReport.java`, `HRMReportParser.java`, `HRMUtil.java`
- Core HRM functionality for Ontario hospital report integration.

**Inbox/Lab Routing (3 files, 1,558 lines total)**:
- `InboxResultsDaoImpl.java`, `InboxResultsRepositoryImpl.java`, `ProviderLabRoutingDaoImpl.java`
- Core inbox and lab routing queries.

**Consultation/PDF (3 files, 1,451 lines total)**:
- `ConsultationPDFCreator.java`, `EctConsultationFormFax2Action.java`, `EctConsultationFormRequestPrintAction22Action.java`

**Document Management (2 files, 890 lines total)**:
- `DocumentUpload2Action.java`, `SplitDocument2Action.java`

**Other complex files**:
- `NotePermissions2Action.java` (465 lines) — CAISI program-based access control
- `OscarStatus2Action.java` (312 lines) — System status and restart
- `FormUpdate2Action.java` (364 lines) — Flowsheet measurement updates
- `HRMModifyDocument2Action.java` (447 lines) — HRM document modification
- `DemographicExtKey.java` (177 lines) — 88 healthcare domain constants

### Tier 6: Investigate Further

- **`APCache.js`** (315 lines) — Indivica copyright with **no license statement**. This needs legal review — it's technically "all rights reserved" without an explicit license grant.

---

## 7. File Count Summary

| Category | Count | Total Lines |
|----------|-------|-------------|
| Java — POJOs/Models | 15 | ~1,485 |
| Java — DAO Interfaces | 8-13 | ~750 |
| Java — DAO Implementations | 6 | ~1,713 |
| Java — Actions/Servlets | 20 | ~4,436 |
| Java — Parsers/Utilities | 17 | ~4,705 |
| Java — Other | 4 | ~405 |
| JSP/JSPF — Trivial | 4 | ~106 |
| JSP/JSPF — Non-trivial | 24 | ~5,900 |
| JS/CSS | 3 | ~586 |
| **Total GPL 2.0 Strict** | **106** | **~20,086** |
| Oracle JDK (Classpath Exception) | 5 | N/A |
| Dual-licensed (MIT/BSD available) | 2 | N/A |
| Unlicensed Indivica | 1 | 315 |

---

## 8. Methodology

### Search Patterns Used
1. `"GNU General Public License, Version 2, 1991"` — Primary identifier for Indivica/WELL EMR headers
2. `"GNU General Public License version 2 only"` — Oracle JDK javadoc files
3. `"GPL v2"` / `"GPLv2"` without `+` — Third-party libraries
4. `"GPL-2.0-only"` SPDX identifier — No matches found
5. Cross-referenced all "Indivica" and "WELL EMR" mentions to find any files with these contributors but different license headers
6. Verified all files individually for "or later" / "any later version" language

### Verification
- Each identified file was read and its header verified
- Files that mention Indivica/WELL EMR but use GPL 2.0+ headers (e.g., `EFormDao.java`, `IsPropertiesOn.java`) were correctly excluded
- Model files were individually analyzed for POJO classification
- DAO implementations were reviewed for complexity and security

---

*Generated with Claude Code*
