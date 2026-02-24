# GPL License Audit Report — CARLOS EMR

**Date**: 2026-02-24
**Scope**: All source files under `src/` (`.java`, `.jsp`, `.js`, `.css`, `.xml`, `.properties`)
**Total files scanned**: ~6,425

---

## Executive Summary

The CARLOS EMR codebase is predominantly licensed under **GPL-2.0+** ("version 2 or any later version"), inherited from the McMaster/OSCAR project heritage. However, **107 source files** carry a restrictive **GPL-2.0-only** (strict) license header from three commercial contributors: Indivica Inc. (93 files), Magenta Health (11 files), and WELL EMR Group Inc. (3 files). These files lack the "or any later version" clause, which has implications for future license upgrades and downstream compatibility.

Additionally, **1 JavaScript file** (`APCache.js`) has an Indivica copyright notice with **no license statement at all**.

---

## License Distribution Summary

| License | Files | % of Total | Notes |
|---------|------:|:----------:|-------|
| GPL-2.0+ (v2 or later) | 5,011 | 78.0% | Standard McMaster/OSCAR header |
| GPL-2.0-strict (v2 only) | 107 | 1.7% | Indivica/WELL EMR/Magenta headers |
| No license detected | 1,206 | 18.8% | Mix of generated code, vendored assets, and unlicensed originals |
| MIT | 51 | 0.8% | Third-party frontend libraries |
| Apache-2.0 | 22 | 0.3% | Bootstrap, third-party utilities |
| BSD | 19 | 0.3% | OWASP CSRFGuard, YUI, utilities |
| LGPL | 9 | 0.1% | Calendar widgets (Dynarch, X library) |
| GPL-unversioned | 1 | <0.1% | Polish locale file |
| **Total** | **~6,426** | | |

---

## GPL-2.0-Strict Files: Detailed Inventory

### Header Patterns

All 107 GPL-2.0-strict files use a short-form header that cites GPLv2 by name **without** the "or any later version" escape clause.

**Pattern A — Indivica Inc. (93 files)**:
```java
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
```

**Pattern B — Magenta Health (11 files)**:
Uses the same Indivica GPLv2 header structure but with `Copyright (c) 2025. Magenta Health. All Rights Reserved.` as the copyright holder. These files inherit the strict GPLv2 pattern from the Indivica header template.

**Pattern C — WELL EMR Group Inc. (3 files)**:
```java
/**
 * Copyright (c) 2021 WELL EMR Group Inc.
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "gnu.org/licenses/gpl-2.0.html".
 */
```
Note: WELL EMR headers omit the `indivica.ca/gplv2` reference.

### Contrast with GPL-2.0+ Header

The majority (5,011 files) use the standard McMaster header with the crucial "or later" clause:
```java
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University.
 * ...
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
```

The defining difference: `"either version 2 of the License, or (at your option) any later version"` is **present** in GPL-2.0+ and **absent** in GPL-2.0-strict.

---

### Complete File List by Module

#### Hospital Report Manager (17 Java + 4 JSP = 21 files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `hospitalReportManager/HRMDisplayReport2Action.java` | Action | Indivica |
| 2 | `hospitalReportManager/HRMModifyDocument2Action.java` | Action | Indivica |
| 3 | `hospitalReportManager/HRMStatementModify2Action.java` | Action | Indivica |
| 4 | `hospitalReportManager/HRMReport.java` | Parser | Indivica |
| 5 | `hospitalReportManager/HRMReportParser.java` | Parser | Indivica |
| 6 | `hospitalReportManager/HRMUtil.java` | Utility | Indivica |
| 7 | `hospitalReportManager/dao/HRMCategoryDao.java` | DAO | Indivica |
| 8 | `hospitalReportManager/dao/HRMDocumentCommentDao.java` | DAO | Indivica |
| 9 | `hospitalReportManager/dao/HRMDocumentDao.java` | DAO | Indivica |
| 10 | `hospitalReportManager/dao/HRMDocumentSubClassDao.java` | DAO | Indivica |
| 11 | `hospitalReportManager/dao/HRMDocumentToDemographicDao.java` | DAO | Indivica |
| 12 | `hospitalReportManager/dao/HRMDocumentToProviderDao.java` | DAO | Indivica |
| 13 | `hospitalReportManager/dao/HRMProviderConfidentialityStatementDao.java` | DAO | Indivica |
| 14 | `hospitalReportManager/dao/HRMSubClassDao.java` | DAO | Indivica |
| 15 | `hospitalReportManager/model/HRMCategory.java` | Model | Indivica |
| 16 | `hospitalReportManager/model/HRMDocument.java` | Model | Indivica |
| 17 | `hospitalReportManager/model/HRMDocumentComment.java` | Model | Indivica |
| 18 | `hospitalReportManager/model/HRMDocumentSubClass.java` | Model | Indivica |
| 19 | `hospitalReportManager/model/HRMDocumentToDemographic.java` | Model | Indivica |
| 20 | `hospitalReportManager/model/HRMDocumentToProvider.java` | Model | Indivica |
| 21 | `hospitalReportManager/model/HRMProviderConfidentialityStatement.java` | Model | Indivica |
| 22 | `hospitalReportManager/model/HRMReportCriteria.java` | Model | Magenta |
| 23 | `hospitalReportManager/model/HRMSubClass.java` | Model | Indivica |
| 24 | `webapp/hospitalReportManager/ajaxResponse.jsp` | JSP | Indivica |
| 25 | `webapp/hospitalReportManager/displayHRMDocList.jsp` | JSP | Indivica |
| 26 | `webapp/hospitalReportManager/displayHRMReport.jsp` | JSP | Indivica |
| 27 | `webapp/hospitalReportManager/hospitalReportManager.jsp` | JSP | Indivica |

#### Lab Parsers & Handlers (15 Java files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `lab/ca/all/pageUtil/CreateLabelTDIS2Action.java` | Action | Indivica |
| 2 | `lab/ca/all/pageUtil/ORUR01Manager.java` | Manager | Indivica |
| 3 | `lab/ca/all/parsers/ICLHandler.java` | Parser | Indivica |
| 4 | `lab/ca/all/parsers/MEDVUEHandler.java` | Parser | Indivica |
| 5 | `lab/ca/all/parsers/PFHTHandler.java` | Parser | Indivica |
| 6 | `lab/ca/all/parsers/TDISHandler.java` | Parser | Indivica |
| 7 | `lab/ca/all/upload/handlers/ICLHandler.java` | Handler | Indivica |
| 8 | `lab/ca/all/upload/handlers/MEDVUEHandler.java` | Handler | Indivica |
| 9 | `lab/ca/all/upload/handlers/PFHTHandler.java` | Handler | Indivica |
| 10 | `lab/ca/all/upload/handlers/TDISHandler.java` | Handler | Indivica |
| 11 | `lab/ca/all/upload/RouteReportResults.java` | Router | Indivica |
| 12 | `lab/ca/all/util/ICLUtilities.java` | Utility | Indivica |
| 13 | `lab/ca/all/util/MEDVUEUtilities.java` | Utility | Indivica |
| 14 | `lab/ca/all/util/PFHTUtilities.java` | Utility | Indivica |
| 15 | `lab/ca/on/HRMResultsData.java` | Data | Indivica |

#### Common DAO & Model (16 Java files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `commn/dao/ClinicNbrDao.java` | DAO Interface | Magenta |
| 2 | `commn/dao/ClinicNbrDaoImpl.java` | DAO Impl | Magenta |
| 3 | `commn/dao/EReferAttachmentDataDaoImpl.java` | DAO Impl | WELL EMR |
| 4 | `commn/dao/HL7HandlerMSHMappingDao.java` | DAO Interface | Magenta |
| 5 | `commn/dao/HL7HandlerMSHMappingDaoImpl.java` | DAO Impl | Magenta |
| 6 | `commn/dao/InboxResultsDao.java` | DAO Interface | Magenta |
| 7 | `commn/dao/InboxResultsDaoImpl.java` | DAO Impl | Magenta |
| 8 | `commn/dao/InboxResultsRepository.java` | Repository | Magenta |
| 9 | `commn/dao/InboxResultsRepositoryImpl.java` | Repository | Magenta |
| 10 | `commn/dao/ProviderLabRoutingDao.java` | DAO Interface | Magenta |
| 11 | `commn/dao/ProviderLabRoutingDaoImpl.java` | DAO Impl | Magenta |
| 12 | `commn/model/ClinicNbr.java` | Model | Indivica |
| 13 | `commn/model/HL7HandlerMSHMapping.java` | Model | Indivica |
| 14 | `commn/model/Hl7TextMessageInfo.java` | Model | Indivica |
| 15 | `commn/model/ProviderLabRoutingModel.java` | Model | Indivica |
| 16 | `commn/model/ReadLab.java` | Model | Indivica |

#### Document Manager (4 Java + 3 JSP = 7 files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `documentManager/actions/AddDocumentType2Action.java` | Action | Indivica |
| 2 | `documentManager/actions/ChangeDocStatus2Action.java` | Action | Indivica |
| 3 | `documentManager/actions/DocumentUpload2Action.java` | Action | Indivica |
| 4 | `documentManager/actions/SplitDocument2Action.java` | Action | Indivica |
| 5 | `webapp/documentManager/addNewDocumentCategories.jsp` | JSP | Indivica |
| 6 | `webapp/documentManager/changeStatus.jsp` | JSP | Indivica |
| 7 | `webapp/documentManager/documentUploaderFirefox36.jsp` | JSP | Indivica |

#### EForm (5 Java + 3 JSP = 8 files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `eform/actions/FetchUpdatedData2Action.java` | Action | Indivica |
| 2 | `eform/actions/RTLSettings2Action.java` | Action | Indivica |
| 3 | `eform/util/EFormImageViewForPdfGenerationServlet.java` | Servlet | Indivica |
| 4 | `eform/util/EFormSignatureViewForPdfGenerationServlet.java` | Servlet | Indivica |
| 5 | `eform/util/EFormViewForPdfGenerationServlet.java` | Servlet | Indivica |
| 6 | `webapp/eform/efmformapconfig_lookup.jsp` | JSP | Indivica |
| 7 | `webapp/eform/efmformrtl_config.jsp` | JSP | Indivica |
| 8 | `webapp/eform/efmformrtl_templates.jsp` | JSP | Indivica |

#### Encounter / Consultation (5 Java + 4 JSP = 9 files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `encounter/oscarConsultationRequest/pageUtil/ConsultationPDFCreator.java` | PDF | Indivica |
| 2 | `encounter/oscarConsultationRequest/pageUtil/EctConsultationFormFax2Action.java` | Action | Indivica |
| 3 | `encounter/oscarConsultationRequest/pageUtil/EctConsultationFormRequestPrintAction22Action.java` | Action | Indivica |
| 4 | `encounter/oscarConsultationRequest/pageUtil/ImagePDFCreator.java` | PDF | Indivica |
| 5 | `encounter/oscarMeasurements/pageUtil/FormUpdate2Action.java` | Action | Indivica |
| 6 | `encounter/pageUtil/EctDisplayHRM2Action.java` | Action | Indivica |
| 7 | `webapp/oscarEncounter/oscarConsultationRequest/attachConsultation2.jsp` | JSP | Indivica |
| 8 | `webapp/oscarEncounter/oscarConsultationRequest/displayImage.jsp` | JSP | Indivica |
| 9 | `webapp/oscarEncounter/oscarMeasurements/DiabFlowSheet.jsp` | JSP | Indivica |
| 10 | `webapp/oscarEncounter/oscarMeasurements/FlowUpdate.jsp` | JSP | Indivica |

#### MDS / Lab Inbox (5 JSP files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `webapp/oscarMDS/Index.jsp` | JSP | Indivica |
| 2 | `webapp/oscarMDS/Page.jsp` | JSP | Indivica |
| 3 | `webapp/oscarMDS/SelectProviderSimple.jsp` | JSP | Indivica |
| 4 | `webapp/oscarMDS/Split.jsp` | JSP | Indivica |
| 5 | `webapp/oscarMDS/Splitclose.jsp` | JSP | Indivica |

#### Admin (4 JSP files)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `webapp/admin/clinicNbrManage.jsp` | JSP | Indivica |
| 2 | `webapp/admin/displayDocumentCategories.jsp` | JSP | Indivica |
| 3 | `webapp/admin/oscarStatus.jsp` | JSP | Indivica |
| 4 | `webapp/admin/rebootConfirmation.jsp` | JSP | Indivica |

#### Remaining Files (scattered across modules)

| # | File | Type | Copyright |
|---|------|------|-----------|
| 1 | `billing/CA/ON/util/EDTFolder.java` | Utility | Indivica |
| 2 | `casemgmt/web/NotePermission.java` | Model | Indivica |
| 3 | `casemgmt/web/NotePermissions2Action.java` | Action | Indivica |
| 4 | `commn/model/enumerator/DemographicExtKey.java` | Enum | WELL EMR |
| 5 | `commn/web/HealthCardSearch2Action.java` | Action | Indivica |
| 6 | `form/model/FormBooleanValuePK.java` | Model | WELL EMR |
| 7 | `util/OscarStatus2Action.java` | Action | Indivica |
| 8 | `webapp/billing/CA/ON/billingLreport.jsp` | JSP | Indivica |
| 9 | `webapp/billing/CA/ON/viewMOHFiles.jsp` | JSP | Indivica |
| 10 | `webapp/casemgmt/noteProgram.css` | CSS | Indivica |
| 11 | `webapp/casemgmt/noteProgram.js` | JS | Indivica |
| 12 | `webapp/js/eform_highlight.js` | JS | Indivica |
| 13 | `webapp/library/eforms/signatureControl.jsp` | JSP | Indivica |
| 14 | `webapp/provider/formIntake.jsp` | JSP | Indivica |
| 15 | `webapp/signature_pad/tabletSignature.jsp` | JSP | Indivica |

---

### Copyright Holder Summary

| Copyright Holder | Files | Years | Notes |
|------------------|------:|-------|-------|
| Indivica Inc. | 93 | 2008-2012 | Original contributor, company now defunct |
| Magenta Health | 11 | 2025 | Used Indivica's GPLv2-strict header template |
| WELL EMR Group Inc. | 3 | 2021 | Slight header variation (no indivica.ca URL) |
| **Total** | **107** | | |

### File Type Summary

| File Type | Count | Total Lines |
|-----------|------:|------------:|
| Java (.java) | 76 | ~15,790 |
| JSP (.jsp) | 28 | ~7,600 |
| JavaScript (.js) | 2 | ~570 |
| CSS (.css) | 1 | ~170 |
| **Total** | **107** | **~24,130** |

---

## Additional Finding: Indivica File Without License

**File**: `src/main/webapp/library/eforms/APCache.js`

This file has a copyright notice (`Copyright 2011 (c) Indivica, Author: Adam Balanga`) but **no license statement whatsoever** — no GPL, no permissive license, nothing. This makes its license status legally ambiguous. Since Indivica contributed other files under GPL-2.0-strict, this file likely was intended to be GPLv2 as well, but the omission is a compliance gap.

---

## Other License Categories (Non-GPL-2.0-Strict)

### GPL-2.0+ Files (5,011 files)
The vast majority of the codebase. Standard McMaster/OSCAR heritage header with "either version 2 of the License, or (at your option) any later version."

### Third-Party Libraries (bundled in `src/main/webapp/`)

| License | Files | Examples |
|---------|------:|---------|
| MIT | 51 | jQuery, DataTables, Font Awesome, Bootstrap 3+, wysihtml5, angular-ui-router |
| Apache-2.0 | 22 | Bootstrap 2.x, Hogan.js, datepicker |
| BSD | 19 | OWASP CSRFGuard, YUI, jQuery plugins, PasswordHash.java, ResultSetBuilder.java |
| LGPL | 9 | Dynarch DHTML Calendar, X library (Cross-Browser.com) |

### Dual-Licensed Files (7 files)
- **jszip.js** — MIT **or** GPLv3 (user's choice)
- **jSignature.js** (+ min variants) — MIT primary, BSD for Simplify.js component

### Files Without License Headers (1,206 files)
- ~57 XMLBeans-generated schema classes
- ~61 JAXB-generated Ontario health service stubs (`ca.ontario.health.*`)
- ~46 WSDL-generated implementation stubs
- ~347 minified/vendored JS files (headers stripped during minification)
- ~292 CSS files (mostly generated or stripped)
- ~100+ CARLOS-authored Java files missing headers (OAuth module, newer Managers, etc.)

---

## Legal Implications

### What GPL-2.0-Strict Means

1. **No automatic upgrade**: These 107 files cannot be relicensed under GPL-3.0 or later without explicit permission from the copyright holders. The rest of the codebase (GPL-2.0+) can be.

2. **Compatibility**: GPL-2.0-strict is compatible with GPL-2.0+ code (both allow distribution under GPLv2). The combined work remains distributable under GPLv2. However, if the project ever wanted to move to GPLv3-only, these files would block that.

3. **Copyleft obligations**: All GPL-2.0-strict obligations still apply — source must be provided, modifications must be under GPLv2, and the license cannot be changed.

4. **Practical impact today**: **Minimal**. The project is already GPL-2.0+, so distribution under GPLv2 is the effective license. The strict files don't create any current incompatibility.

### Indivica Inc. Status

Indivica Inc. appears to be defunct (copyright dates 2008-2012, website `indivica.ca` referenced in headers). This means:
- Obtaining relicensing permission would require identifying the legal successor or copyright assignee
- The original code remains under GPLv2-strict indefinitely unless rights holders agree to change

### WELL EMR Group & Magenta Health

Both are active organizations. If relicensing is desired:
- **WELL EMR** (3 files) — contactable for relicensing to GPL-2.0+
- **Magenta Health** (11 files) — contactable for relicensing to GPL-2.0+

---

## Recommendations

### Tier 1: No Action Required (Current State)

The GPL-2.0-strict files create **no practical issue** for the project today. The project distributes under GPLv2, which is compatible with both GPL-2.0-strict and GPL-2.0+ files. No immediate action is needed.

### Tier 2: Low-Effort Improvements

1. **Add license to `APCache.js`**: Add a GPL-2.0 header (matching other Indivica files) to clarify its license status. Since it's Indivica code and all other Indivica code is GPLv2, this is a safe assumption.

2. **Add headers to unlicensed CARLOS-authored files**: The ~100+ Java files authored by CARLOS contributors that lack license headers should receive the standard CARLOS GPL-2.0+ header.

3. **Contact WELL EMR and Magenta Health**: Request voluntary relicensing of their 14 combined files from GPL-2.0-strict to GPL-2.0+. This is a straightforward ask since both organizations have already contributed GPL-2.0+ code to the project.

### Tier 3: Document and Track

1. **Record this audit**: Keep this report as a compliance artifact for future reference.

2. **License header in CI**: Consider adding a CI check that validates new files include a proper license header, preventing the "no license" category from growing.

3. **SPDX identifiers**: Consider adding SPDX license identifiers (`SPDX-License-Identifier: GPL-2.0-only` vs `GPL-2.0-or-later`) to make automated scanning more reliable.

### Tier 4: Future Consideration

If the project ever considers a GPL-3.0 upgrade:
- The 93 Indivica files would need to be rewritten or their rights holders contacted
- This is a significant effort and should only be undertaken with clear strategic motivation

---

## Methodology

### Search Strategy

1. **Primary pattern**: `grep -r "General Public License, Version 2, 1991"` across all source files
2. **Verification**: Each file's header was cross-checked to confirm absence of "or any later version" clause
3. **Copyright attribution**: Files were categorized by `grep` for "Indivica", "WELL EMR", and "Magenta Health"
4. **GPL-2.0+ verification**: Separate `grep` for "or (at your option) any later version" confirmed 5,011 files
5. **Multiline awareness**: JSP/XML files wrap headers across lines; patterns were validated against normalized content

### Known Limitations

- **Multiline headers in JSP/XML**: Some files wrap the GPL text across physical lines, which can cause false positives/negatives with single-line grep. This audit used multiple search patterns to compensate.
- **Generated code**: Auto-generated files (JAXB, XMLBeans, WSDL stubs) typically inherit the license of the project they belong to, but this is not explicitly stated in their file headers.
- **Minified JS/CSS**: Minification strips license comments. The original unminified versions may have had license headers.

---

*Generated with Claude Code for the CARLOS EMR Project*
