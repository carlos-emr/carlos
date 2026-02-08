# GPL-2 Derived Works Audit: Indivica Files

**Date**: 2026-02-08
**Scope**: All files with Indivica Inc. copyright (2008-2012) licensed under GPL-2-only
**Finding**: ~50 Indivica GPL-2-only files are demonstrably derived works of pre-existing GPL-2+ code

---

## Executive Summary

Approximately 50 files in the codebase carry Indivica Inc. copyright headers with
GPL-2-only licensing ("GNU General Public License, Version 2, 1991") but are derived
works of pre-existing GPL-2+ code ("either version 2 of the License, or (at your
option) any later version") from McMaster University (2001-2002) and CRIICH/St.
Michael's Hospital (2005-2012).

The evidence includes:
- **6 files** with explicit "Derived from" statements written by Indivica developers
- **12 files** implementing GPL-2+ `MessageHandler` interfaces
- **16 files** extending GPL-2+ `AbstractDaoImpl` base class
- **17 files** extending GPL-2+ `AbstractModel` base class
- **1 file** extending GPL-2+ `EctDisplayAction` base class

---

## Category 1: Explicit Self-Declared Derived Works

These files contain Indivica's own comments stating derivation from GPL-2+ sources.

### ICLHandler.java (parsers)

**File**: `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/ICLHandler.java`
**License**: Indivica GPL-2-only (2008-2012)
**Line 19**: `Derived from PATH7Handler.java, by wrighd`

### TDISHandler.java (parsers)

**File**: `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/TDISHandler.java`
**License**: Indivica GPL-2-only (2008-2012)
**Line 17**: `An HL7 lab parser, structure was borrowed from GDML template`
**Source**: `GDMLHandler.java` - McMaster 2001-2002, GPL-2+

### HRMXMLHandler.java (parsers)

**File**: `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/HRMXMLHandler.java`
**License**: Indivica GPL-2-only (2008-2012)
**Line 16**: `An HL7 lab parser, structure was borrowed from GDML template`
**Source**: `GDMLHandler.java` - McMaster 2001-2002, GPL-2+

### ICLHandler.java (upload/handlers)

**File**: `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/ICLHandler.java`
**License**: Indivica GPL-2-only (2008-2012)
**Line 19**: `Derived from GDMLHandler.java, by wrighd`
**Source**: `upload/handlers/GDMLHandler.java` - McMaster 2001-2002, GPL-2+

### ICLUtilities.java

**File**: `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/util/ICLUtilities.java`
**License**: Indivica GPL-2-only (2008-2012)
**Line 19**: `Derived from Utilities.java by wrighd`
**Source**: `Utilities.java` - McMaster 2001-2002, GPL-2+

### PFHTUtilities.java

**File**: `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/util/PFHTUtilities.java`
**License**: Indivica GPL-2-only (2008-2012)
**Line 17**: `Derived from ICLUtilities.java by David Daley, Indivica`
**Source**: ICLUtilities.java, which itself derives from GPL-2+ Utilities.java (transitive)

---

## Category 2: Lab Parsers Implementing GPL-2+ MessageHandler

`MessageHandler.java` interface in both `parsers/` and `upload/handlers/` directories
is copyright McMaster University 2001-2002, licensed GPL-2+.

`DefaultGenericHandler.java` is also McMaster 2001-2002, GPL-2+.

### Parser implementations (6 files)

| File | Inheritance |
|---|---|
| `parsers/ICLHandler.java` | `extends DefaultGenericHandler implements MessageHandler` |
| `parsers/MEDVUEHandler.java` | `implements MessageHandler` |
| `parsers/OLISHL7Handler.java` | `implements MessageHandler` |
| `parsers/PFHTHandler.java` | `implements MessageHandler` |
| `parsers/TDISHandler.java` | `implements MessageHandler` |
| `parsers/HRMXMLHandler.java` | `implements MessageHandler` |

### Upload handler implementations (6 files)

| File | Inheritance |
|---|---|
| `upload/handlers/ICLHandler.java` | `implements MessageHandler` |
| `upload/handlers/MEDVUEHandler.java` | `implements MessageHandler` |
| `upload/handlers/OLISHL7Handler.java` | `implements MessageHandler` |
| `upload/handlers/PFHTHandler.java` | `implements MessageHandler` |
| `upload/handlers/TDISHandler.java` | `implements MessageHandler` |
| `upload/handlers/HRMXMLHandler.java` | `implements MessageHandler` |

---

## Category 3: EctDisplayHRM2Action Extending GPL-2+ EctDisplayAction

**File**: `src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayHRM2Action.java`
**License**: Indivica GPL-2-only (2008-2012)
**Line 36**: `public class EctDisplayHRM2Action extends EctDisplayAction`

**Parent**: `EctDisplayAction.java` - McMaster 2001-2002, GPL-2+

---

## Category 4: DAOs Extending GPL-2+ AbstractDaoImpl

`AbstractDaoImpl.java` is copyright CRIICH/St. Michael's Hospital 2005-2012,
licensed GPL-2+ ("either version 2... or any later version").

### Common DAOs (4 implementations + 4 interfaces)

| DAO Implementation | DAO Interface |
|---|---|
| `commn/dao/ClinicNbrDaoImpl.java` | `commn/dao/ClinicNbrDao.java` (extends `AbstractDao`) |
| `commn/dao/HL7HandlerMSHMappingDaoImpl.java` | `commn/dao/HL7HandlerMSHMappingDao.java` (extends `AbstractDao`) |
| `commn/dao/ProviderLabRoutingDaoImpl.java` | `commn/dao/ProviderLabRoutingDao.java` (extends `AbstractDao`) |
| `commn/dao/ReadLabDaoImpl.java` | `commn/dao/ReadLabDao.java` (extends `AbstractDao`) |

### HRM DAOs (8 files)

- `hospitalReportManager/dao/HRMCategoryDao.java`
- `hospitalReportManager/dao/HRMDocumentDao.java`
- `hospitalReportManager/dao/HRMDocumentCommentDao.java`
- `hospitalReportManager/dao/HRMDocumentSubClassDao.java`
- `hospitalReportManager/dao/HRMDocumentToDemographicDao.java`
- `hospitalReportManager/dao/HRMDocumentToProviderDao.java`
- `hospitalReportManager/dao/HRMProviderConfidentialityStatementDao.java`
- `hospitalReportManager/dao/HRMSubClassDao.java`

### OLIS DAOs (4 files)

- `olis/dao/OLISProviderPreferencesDao.java`
- `olis/dao/OLISRequestNomenclatureDao.java`
- `olis/dao/OLISResultNomenclatureDao.java`
- `olis/dao/OLISSystemPreferencesDao.java`

---

## Category 5: Models Extending GPL-2+ AbstractModel

`AbstractModel.java` is copyright CRIICH/St. Michael's Hospital 2005-2012,
licensed GPL-2+.

### Common Models (5 files)

- `commn/model/ClinicNbr.java`
- `commn/model/HL7HandlerMSHMapping.java`
- `commn/model/Hl7TextMessageInfo.java`
- `commn/model/ProviderLabRoutingModel.java`
- `commn/model/ReadLab.java`

### HRM Models (8 files)

- `hospitalReportManager/model/HRMCategory.java`
- `hospitalReportManager/model/HRMDocument.java`
- `hospitalReportManager/model/HRMDocumentComment.java`
- `hospitalReportManager/model/HRMDocumentSubClass.java`
- `hospitalReportManager/model/HRMDocumentToDemographic.java`
- `hospitalReportManager/model/HRMDocumentToProvider.java`
- `hospitalReportManager/model/HRMProviderConfidentialityStatement.java`
- `hospitalReportManager/model/HRMSubClass.java`

### OLIS Models (4 files)

- `olis/model/OLISProviderPreferences.java`
- `olis/model/OLISRequestNomenclature.java`
- `olis/model/OLISResultNomenclature.java`
- `olis/model/OLISSystemPreferences.java`

---

## GPL-2+ Source Files (Verified)

All parent/source files confirmed as GPL-2+ with "either version 2 of the License,
or (at your option) any later version":

| File | Copyright Holder | Year |
|---|---|---|
| `parsers/MessageHandler.java` | McMaster University | 2001-2002 |
| `parsers/DefaultGenericHandler.java` | McMaster University | 2001-2002 |
| `parsers/GDMLHandler.java` | McMaster University | 2001-2002 |
| `upload/handlers/MessageHandler.java` | McMaster University | 2001-2002 |
| `upload/handlers/GDMLHandler.java` | McMaster University | 2001-2002 |
| `util/Utilities.java` | McMaster University | 2001-2002 |
| `encounter/pageUtil/EctDisplayAction.java` | McMaster University | 2001-2002 |
| `commn/dao/AbstractDaoImpl.java` | CRIICH/St. Michael's Hospital | 2005-2012 |
| `commn/dao/AbstractDao.java` | CRIICH/St. Michael's Hospital | 2005-2012 |
| `commn/model/AbstractModel.java` | CRIICH/St. Michael's Hospital | 2005-2012 |

---

## Legal Analysis

The GPL-2+ license grants recipients the right to use the code under "either version 2
of the License, or (at your option) any later version." When a derived work is created
from GPL-2+ code, the GPL-2+ grant from the original copyright holder travels with the
original code. The derived work combines:

1. The original GPL-2+ code (from McMaster/CRIICH)
2. New GPL-2-only code (from Indivica)

The combined work must respect both licenses. While Indivica can license their new
contributions under GPL-2-only, they cannot strip the "or later" option from the
portions derived from GPL-2+ originals. The header claiming the entire file is
GPL-2-only is therefore inaccurate for these derived works.

---

## Summary

| Evidence Type | Files |
|---|---|
| Explicit "Derived from" statements | 6 |
| Implements GPL-2+ MessageHandler | 12 |
| Extends GPL-2+ DefaultGenericHandler | 1 |
| Extends GPL-2+ EctDisplayAction | 1 |
| Extends GPL-2+ AbstractDaoImpl | 16 |
| Extends GPL-2+ AbstractDao (interface) | 4 |
| Extends GPL-2+ AbstractModel | 17 |
| JSP files including GPL-2+ via `<%@ include %>` or `<jsp:include>` | 7 |
| **Total (with overlaps removed)** | **~57** |

---

## Category 6: JSP/JSPF Files Including GPL-2+ Code

A comprehensive file-by-file audit of all 45 Indivica GPL-2-only JSP/JSPF files
(plus 2 JS/CSS files) found that **7 files** include GPL-2+ code via JSP include
directives. The remaining 38 JSP files and 2 JS/CSS files contain no GPL-2+ includes
and show no evidence of derivation.

### GPL-2+ Files That Are Included

These 4 unique GPL-2+ files are statically or dynamically included by Indivica
GPL-2-only JSPs:

| Included File | Copyright Holder | License |
|---|---|---|
| `/common/webAppContextAndSuperMgr.jsp` | CRIICH/St. Michael's Hospital 2005-2012 | GPL-2+ |
| `/casemgmt/taglibs.jsp` | CRIICH/St. Michael's Hospital 2005-2012 | GPL-2+ |
| `/documentManager/showDocument.jsp` | McMaster University 2001-2002 | GPL-2+ |
| `/lab/CA/ALL/labDisplayAjax.jsp` | McMaster University 2001-2002 | GPL-2+ |
| `/images/spinner.jsp` | KAI Innovations 2014-2015 | GPL-2+ |

### 6a. Static Includes (`<%@ include %>`) - Compiled Into the JSP

Static includes cause the included file's source to be textually inserted into the
including file at compile time. The compiled servlet is a single combined work.

**DiabFlowSheet.jsp**
- **File**: `src/main/webapp/oscarEncounter/oscarMeasurements/DiabFlowSheet.jsp`
- **Line 40**: `<%@ include file="/common/webAppContextAndSuperMgr.jsp" %>`
- **Included file**: CRIICH/St. Michael's Hospital, GPL-2+
- **Impact**: GPL-2+ code compiled directly into GPL-2-only JSP

**oscarStatus.jsp**
- **File**: `src/main/webapp/admin/oscarStatus.jsp`
- **Line 17**: `<%@ include file="/casemgmt/taglibs.jsp" %>`
- **Included file**: CRIICH/St. Michael's Hospital, GPL-2+
- **Impact**: GPL-2+ taglib declarations compiled into GPL-2-only JSP

**rebootConfirmation.jsp**
- **File**: `src/main/webapp/admin/rebootConfirmation.jsp`
- **Line 17**: `<%@ include file="/casemgmt/taglibs.jsp" %>`
- **Included file**: CRIICH/St. Michael's Hospital, GPL-2+
- **Impact**: GPL-2+ taglib declarations compiled into GPL-2-only JSP

**formIntake.jsp**
- **File**: `src/main/webapp/provider/formIntake.jsp`
- **Line 21**: `<%@ include file="/common/webAppContextAndSuperMgr.jsp" %>`
- **Included file**: CRIICH/St. Michael's Hospital, GPL-2+
- **Impact**: GPL-2+ code compiled directly into GPL-2-only JSP

**olis_preferences.jsp**
- **File**: `src/main/webapp/provider/olis_preferences.jsp`
- **Line 16**: `<%@ include file="/casemgmt/taglibs.jsp" %>`
- **Included file**: CRIICH/St. Michael's Hospital, GPL-2+
- **Impact**: GPL-2+ taglib declarations compiled into GPL-2-only JSP

### 6b. Dynamic Includes (`<jsp:include>`) - Runtime Inclusion

Dynamic includes invoke the included JSP as a separate servlet at runtime. The
output is combined into a single response.

**Page.jsp**
- **File**: `src/main/webapp/oscarMDS/Page.jsp`
- **Line 249**: `<jsp:include page="/documentManager/showDocument.jsp" flush="true">`
  - McMaster University 2001-2002, GPL-2+
- **Line 280**: `<jsp:include page="/lab/CA/ALL/labDisplayAjax.jsp" flush="true">`
  - McMaster University 2001-2002, GPL-2+
- **Impact**: Dynamically includes 2 GPL-2+ JSPs as integral parts of page output

**Index.jsp**
- **File**: `src/main/webapp/oscarMDS/Index.jsp`
- **Line 131**: `<jsp:include page="/images/spinner.jsp"/>`
  - KAI Innovations 2014-2015, GPL-2+
- **Impact**: Dynamically includes GPL-2+ spinner component

---

## Category 6 - Complete File-by-File JSP Audit Results

Every Indivica GPL-2-only JSP/JSPF file was individually audited. Files marked
CLEAR have no GPL-2+ includes and no evidence of derivation.

### oscarMDS/ (5 files)

| File | Verdict | Evidence |
|---|---|---|
| `Index.jsp` | **DERIVED** | `<jsp:include page="/images/spinner.jsp"/>` (GPL-2+) |
| `Page.jsp` | **DERIVED** | `<jsp:include>` of showDocument.jsp + labDisplayAjax.jsp (both GPL-2+) |
| `SelectProviderSimple.jsp` | CLEAR | No includes, self-contained |
| `Split.jsp` | CLEAR | No includes, self-contained |
| `Splitclose.jsp` | CLEAR | No includes, minimal 20-line script |

### hospitalReportManager/ (11 files)

| File | Verdict | Evidence |
|---|---|---|
| `ajaxResponse.jsp` | CLEAR | No includes |
| `disable_msg_action.jsp` | CLEAR | No includes |
| `displayHRMDocList.jsp` | CLEAR | No includes |
| `displayHRMReport.jsp` | CLEAR | No includes |
| `hospitalReportManager.jsp` | CLEAR | No includes |
| `hrmAddClassMapping.jsp` | CLEAR | No includes |
| `hrmCategories.jsp` | CLEAR | No includes |
| `hrmKeyUploader.jsp` | CLEAR | No includes |
| `hrmPreferences.jsp` | CLEAR | No includes |
| `hrmShowMapping.jsp` | CLEAR | No includes |
| `hrm_categories_action.jsp` | CLEAR | No includes |

### olis/ (6 files)

| File | Verdict | Evidence |
|---|---|---|
| `ajaxResponse.jsp` | CLEAR | No includes |
| `Preferences.jsp` | CLEAR | No includes |
| `Results.jsp` | CLEAR | No includes |
| `Search.jsp` | CLEAR | No includes (iframe to Simulate.jsp is not a JSP include) |
| `SearchSimulator.jsp` | CLEAR | No includes |
| `Simulate.jsp` | CLEAR | No includes |

### oscarEncounter/ (4 files)

| File | Verdict | Evidence |
|---|---|---|
| `attachConsultation2.jsp` | CLEAR | No includes; no predecessor file found |
| `displayImage.jsp` | CLEAR | No includes |
| `DiabFlowSheet.jsp` | **DERIVED** | `<%@ include file="/common/webAppContextAndSuperMgr.jsp" %>` (GPL-2+) |
| `FlowUpdate.jsp` | CLEAR | No includes, minimal error page |

### documentManager/ (4 files)

| File | Verdict | Evidence |
|---|---|---|
| `addNewDocumentCategories.jsp` | CLEAR | No includes |
| `changeStatus.jsp` | CLEAR | No includes |
| `documentUploader.jsp` | CLEAR | No includes |
| `documentUploaderFirefox36.jsp` | CLEAR | No includes |

### eform/ (3 files)

| File | Verdict | Evidence |
|---|---|---|
| `efmformapconfig_lookup.jsp` | CLEAR | No includes |
| `efmformrtl_config.jsp` | CLEAR | No includes |
| `efmformrtl_templates.jsp` | CLEAR | No includes |

### admin/ (4 files)

| File | Verdict | Evidence |
|---|---|---|
| `clinicNbrManage.jsp` | CLEAR | No includes |
| `displayDocumentCategories.jsp` | CLEAR | No includes |
| `oscarStatus.jsp` | **DERIVED** | `<%@ include file="/casemgmt/taglibs.jsp" %>` (GPL-2+) |
| `rebootConfirmation.jsp` | **DERIVED** | `<%@ include file="/casemgmt/taglibs.jsp" %>` (GPL-2+) |

### billing/CA/ON/ (2 files)

| File | Verdict | Evidence |
|---|---|---|
| `billingLreport.jsp` | CLEAR | No includes |
| `viewMOHFiles.jsp` | CLEAR | No includes |

### provider/ (3 files)

| File | Verdict | Evidence |
|---|---|---|
| `caseload.jspf` | CLEAR | No includes |
| `formIntake.jsp` | **DERIVED** | `<%@ include file="/common/webAppContextAndSuperMgr.jsp" %>` (GPL-2+) |
| `olis_preferences.jsp` | **DERIVED** | `<%@ include file="/casemgmt/taglibs.jsp" %>` (GPL-2+) |

### lab/CA/ALL/ (1 file)

| File | Verdict | Evidence |
|---|---|---|
| `labDisplayOLIS.jsp` | CLEAR | No includes |

### library/eforms/ (1 file)

| File | Verdict | Evidence |
|---|---|---|
| `signatureControl.jsp` | CLEAR | No includes |

### signature_pad/ (1 file)

| File | Verdict | Evidence |
|---|---|---|
| `tabletSignature.jsp` | CLEAR | No includes |

### casemgmt/ (2 files - JS/CSS)

| File | Verdict | Evidence |
|---|---|---|
| `noteProgram.js` | CLEAR | JavaScript, no includes |
| `noteProgram.css` | CLEAR | CSS, no includes |
