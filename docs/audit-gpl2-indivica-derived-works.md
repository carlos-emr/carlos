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
| **Total (with overlaps removed)** | **~50** |
