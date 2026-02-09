# GPL Header Cleanup Review: Indivica Lab Module Files

**Date**: 2026-02-09
**Type**: Maintenance / License header correction
**Scope**: 17 files in the lab parsing, upload handling, utility, and display subsystems

---

## Summary

Seventeen files in the lab module carry a GPL 2.0-only header (Indivica Inc., 2008-2012) but contain code derived from McMaster University files that are licensed under GPL 2.0-or-later. Under GPL Section 2(b), derivative works must be licensed "under the terms of this License," which means the "or later version" grant from the upstream McMaster files flows through to derivative works. The current GPL 2.0-only headers on these 17 files are inconsistent with the upstream license terms and should be updated to GPL 2.0-or-later to reflect the correct licensing.

Each file also requires a copyright attribution line for McMaster University to acknowledge the upstream origin of the derived code, per GPL Section 1 and standard open-source attribution practice.

This document provides the file-by-file evidence supporting the header correction.

---

## Current Headers (For Reference)

### Header Present on All 17 Files (Indivica GPL 2.0-only)

**Java files:**
```java
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
```

**JSP files:**
```jsp
<%--
    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".
--%>
```

This header specifies "Version 2, 1991 (GPLv2)" and references `gnu.org/licenses/gpl-2.0.html` specifically. It does not include the "or (at your option) any later version" clause.

### Header Present on All McMaster Precursor Files (GPL 2.0+)

```java
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * ...
 */
```

This header includes the standard FSF boilerplate with "either version 2 of the License, or (at your option) any later version," making it GPL 2.0-or-later.

---

## Required Correction

For each of the 17 files listed below, the header should be updated to:

1. **Change the license designation** from GPL 2.0-only to GPL 2.0-or-later, to match the upstream McMaster license grant.
2. **Add a McMaster copyright attribution line** acknowledging the upstream origin, with the McMaster copyright date range (2001-2002).
3. **Preserve the existing Indivica copyright line** (2008-2012), as Indivica contributed modifications to the derived code.
4. **Preserve the existing CARLOS maintenance notice**.

---

## File-by-File Evidence

### File 1: `parsers/ICLHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/ICLHandler.java` |
| **Lines** | 617 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `parsers/GDMLHandler.java` (949 lines, GPL 2.0+) via `PATH7Handler.java` |

**Explicit derivation statement** (lines 15-20):
```java
/*
 * ICLHandler.java
 * Created on Mar 28, 2009
 * Modified by David Daley, Ithream
 * Derived from PATH7Handler.java, by wrighd
 */
```

The author "wrighd" is credited as the author of McMaster's `GDMLHandler.java` (line 73: `@author wrighd`). PATH7Handler was another McMaster-era handler in the same package, no longer present in the codebase.

**Copied code — `getFullDocName(XCN)` private helper** (ICLHandler line 566 vs. GDMLHandler line 840):

ICLHandler:
```java
private String getFullDocName(XCN docSeg) {
    String docName = "";
    if (docSeg.getPrefixEgDR().getValue() != null)
        docName = docSeg.getPrefixEgDR().getValue();
    if (docSeg.getGivenName().getValue() != null) {
        if (docName.equals("")) {
            docName = docSeg.getGivenName().getValue();
        } else {
            docName = docName + " " + docSeg.getGivenName().getValue();
        }
    }
    if (docSeg.getMiddleInitialOrName().getValue() != null)
        docName = docName + " " + docSeg.getMiddleInitialOrName().getValue();
    if (docSeg.getFamilyName().getValue() != null)
        docName = docName + " " + docSeg.getFamilyName().getValue();
    if (docSeg.getSuffixEgJRorIII().getValue() != null)
        docName = docName + " " + docSeg.getSuffixEgJRorIII().getValue();
    if (docSeg.getDegreeEgMD().getValue() != null)
        docName = docName + " " + docSeg.getDegreeEgMD().getValue();
    return (docName);
}
```

GDMLHandler:
```java
private String getFullDocName(XCN docSeg) {
    String docName = "";
    if (docSeg.getPrefixEgDR().getValue() != null)
        docName = docSeg.getPrefixEgDR().getValue();
    if (docSeg.getGivenName().getValue() != null) {
        if (docName.equals(""))
            docName = docSeg.getGivenName().getValue();
        else
            docName = docName + " " + docSeg.getGivenName().getValue();
    }
    if (docSeg.getMiddleInitialOrName().getValue() != null) {
        if (docName.equals(""))
            docName = docSeg.getMiddleInitialOrName().getValue();
        else
            docName = docName + " " + docSeg.getMiddleInitialOrName().getValue();
    }
    // ... same pattern continues for FamilyName, Suffix, Degree
```

This is a private helper method not required by the `MessageHandler` interface. Same method signature, same XCN type, same field order (Prefix, GivenName, MiddleInitial, FamilyName, Suffix, Degree), same name-building pattern. ICLHandler's version is a simplified copy that omits some empty-check guards.

---

### File 2: `parsers/TDISHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/TDISHandler.java` |
| **Lines** | 1,061 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `parsers/GDMLHandler.java` (949 lines, GPL 2.0+) |

**Explicit derivation statement** (lines 15-19):
```java
/*
 * HL7Handler.java
 * An HL7 lab parser, structure was borrowed from GDML template. Fixed to handle TDIS reports.
 */
```

**Copied code — `obrSegMap` data structure** (TDISHandler line 69 vs. GDMLHandler line 79):

Both files use the same non-obvious data structure design:
```java
// TDISHandler line 69:
private HashMap<OBR, ArrayList<OBX>> obrSegMap = null;

// GDMLHandler line 79:
HashMap<OBR, ArrayList<OBX>> obrSegMap = null;
```

This is an implementation choice (mapping OBR segments to their OBX children), not dictated by the `MessageHandler` interface.

**Copied code — `getOBXReferenceRange()`** (TDISHandler line 473 vs. GDMLHandler line 263):

Both implementations share identical comments, identical algorithm structure, and the same **typo** — "reception range" instead of "reference range":

TDISHandler line 510:
```java
logger.error("Exception retrieving reception range", e);
```

GDMLHandler line 298:
```java
logger.error("Exception retrieving reception range", e);
```

The full method body (comments, split-on-dash logic, operator handling, trailing-dot stripping, `.br` replacement) matches line-for-line between the two files.

**Copied code — `formatDateTime()`** (TDISHandler line 1003 vs. GDMLHandler line 884):

TDISHandler lines 1004-1011 are character-for-character identical to GDMLHandler lines 887-893. The `dateFormat.substring(0, plain.length())` combined with `stringFormat.lastIndexOf()` approach is a distinctive algorithm.

**Copied code — `getOBRComment()`** (TDISHandler line 596 vs. GDMLHandler line 369):

Same algorithm: increment `j`, iterate through OBX segments looking for "FT" value type, count matches, decrement `l`, then iterate comment sub-components. Same variable names (`comment`, `obxCount`, `count`, `l`, `obxSeg`, `k`, `nextComment`), same while-loop structure.

**Copied code — `getFullDocName()`** (TDISHandler line 961 vs. GDMLHandler line 840):

Same field-assembly pattern adapted from HL7 v2.3 XCN to v2.5 XCN API. Same field order, same empty-check logic.

---

### File 3: `parsers/HRMXMLHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/HRMXMLHandler.java` |
| **Lines** | 423 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `parsers/GDMLHandler.java` (GPL 2.0+), via TDISHandler |

**Explicit derivation statement** (lines 15-19):
```java
/*
 * HL7Handler.java
 * An HL7 lab parser, structure was borrowed from GDML template. Fixed to handle TDIS reports.
 */
```

This comment block is a verbatim copy of TDISHandler's derivation comment (TDISHandler lines 15-19). It still references "HL7Handler.java" as the filename and "TDIS reports" — neither is accurate for an HRM XML handler.

**Copy-paste artifact — TDIS-specific JavaDoc** (lines 56-59):
```java
/**
 * TDIS HL7 Report Parser/Handler. Handles incoming HL7 files
 * containing ITS or DEPARTMENTAL reports as part of the OBR/OBX segments.
 * @author dritan
 */
public class HRMXMLHandler implements MessageHandler {
```

The class JavaDoc describes "TDIS HL7 Report Parser/Handler" and "ITS or DEPARTMENTAL reports," but this is `HRMXMLHandler`, which parses XML using JAXB, not HL7. This JavaDoc was copied from TDISHandler (lines 56-62).

**Copy-paste artifact — ITS/DPT documentation** (lines 162-165):

The file contains detailed documentation about ITS/DPT report handling that is irrelevant to an XML parser — this documentation was copied from TDISHandler (lines 294-308).

---

### File 4: `parsers/MEDVUEHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/MEDVUEHandler.java` |
| **Lines** | 737 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `parsers/GDMLHandler.java` (949 lines, GPL 2.0+) |

No explicit derivation comment.

**Copied code — `formatDateTime()`** (MEDVUEHandler line 684 vs. GDMLHandler line 884):

Lines 685-692 are identical to GDMLHandler lines 887-893, using the same `dateFormat.substring(0, plain.length())` technique.

**Copied code — commented-out `getFullDocName(XCN)`** (MEDVUEHandler lines 642-682):

A verbatim copy of TDISHandler's `getFullDocName()` (which is itself adapted from GDMLHandler) appears commented out but retained in the file. Uses HL7 v2.5 XCN API with the same field order and empty-check pattern.

**Copied code — commented-out `getCCDocs()` ZDR segment code** (MEDVUEHandler lines 586-610):

A verbatim copy of GDMLHandler's `getCCDocs()` method (GDMLHandler lines 653-677), including the ZDR Terser path pattern. MEDVUE does not use ZDR segments; this code was copied and commented out.

---

### File 5: `parsers/PFHTHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/PFHTHandler.java` |
| **Lines** | 843 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `parsers/GDMLHandler.java` (949 lines, GPL 2.0+) |

No explicit derivation comment.

**Copied code — `getFullDocName(XCN)`** (PFHTHandler line 748 vs. GDMLHandler line 840):

Identical to GDMLHandler's version. Same v2.3 XCN type, same empty-check pattern on every field, same field order.

**Copied code — `formatDateTime()`** (PFHTHandler line 792 vs. GDMLHandler line 884):

Identical to GDMLHandler lines 887-893.

**Copied code — `getOBXReferenceRange()`** (PFHTHandler line 318 vs. GDMLHandler line 263):

Identical to GDMLHandler. Same comments, same algorithm, same "reception range" typo (line 352), same `.br` replacement on return.

**Copied code — `obrSegMap` data structure** (PFHTHandler line 54):
```java
private HashMap<OBR, ArrayList<OBX>> obrSegMap = null;
```
Same as GDMLHandler line 79.

---

### File 6: `parsers/OLISHL7Handler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/parsers/OLISHL7Handler.java` |
| **Lines** | 3,030 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `parsers/GDMLHandler.java` (949 lines, GPL 2.0+) |

No explicit derivation comment (lines 15-17 contain only `/* OLISHL7Handler.java */`).

**Copied code — `getFullDocName()`** (OLISHL7Handler line 2634 vs. GDMLHandler line 840):

Same field-assembly pattern (prefix, given, middle, family, suffix, degree) with the same `if (docName.equals(""))` empty-check logic. Adapted to Terser path-based access instead of XCN type access.

**Copied code — `formatDateTime()`** (OLISHL7Handler line 2733 vs. GDMLHandler line 884):

Lines 2741-2746 are identical to GDMLHandler lines 887-893. The core `dateFormat.substring(0, plain.length())` algorithm is the same. OLIS adds timezone offset handling around this core.

---

### File 7: `upload/handlers/ICLHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/ICLHandler.java` |
| **Lines** | 104 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `upload/handlers/GDMLHandler.java` (149 lines, GPL 2.0+) |

**Explicit derivation statement** (lines 15-20):
```java
/*
 * ICLHandler.java
 * Created on Feb. 23, 2009
 * Modified by David Daley, Indivica
 * Derived from GDMLHandler.java, by wrighd
 */
```

**Copied code — `parse()` method** (ICLHandler line 46 vs. GDMLHandler line 68):

Same workflow: call `separateMessages()`, iterate through messages calling `MessageUploader.routeReport()`, then call `updateLabStatus()`.

**Copied code — `updateLabStatus()` nested while-loop** (ICLHandler line 71 vs. GDMLHandler line 113):

Same algorithm: get all Hl7TextInfo records, iterate checking if result status is already "A", get a parser handler via Factory, and use a nested while-loop structure (`while resultStatus.equals("") && i < h.getOBRCount()` with inner `while j < h.getOBXCount(i)`) to check for abnormal OBX results. ICL uses an Iterator instead of a for-loop for the outer iteration; the inner algorithm is identical.

---

### File 8: `upload/handlers/TDISHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/TDISHandler.java` |
| **Lines** | 121 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `upload/handlers/GDMLHandler.java` (149 lines, GPL 2.0+) |

No explicit derivation comment (lines 15-19 contain generic "HL7Handler / Upload handler").

**Copy-paste artifact — GDML-specific comment** (lines 68-72):
```java
// Since the gdml labs show more than one lab on the same page when
// grouped
// by accession number their abnormal status must be updated to
// reflect the
// other labs that they are grouped with aswell
```

This comment refers to "gdml labs" and GDML's accession number grouping behavior. It is present in a TDIS handler file, where it does not describe TDIS behavior. This comment originates from McMaster's GDMLHandler upload handler (lines 96-98).

**Copied code — `updateLabStatus()`** (TDISHandler line 85 vs. GDMLHandler line 113):

Same nested while-loop algorithm. Same `parse()` workflow.

---

### File 9: `upload/handlers/HRMXMLHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/HRMXMLHandler.java` |
| **Lines** | 113 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `upload/handlers/GDMLHandler.java` (149 lines, GPL 2.0+) |

No explicit derivation comment (lines 15-19 contain generic "HL7Handler / Upload handler").

**Copy-paste artifact — GDML-specific comment** (lines 62-66):
```java
// Since the gdml labs show more than one lab on the same page when
// grouped
// by accession number their abnormal status must be updated to
// reflect the
// other labs that they are grouped with aswell
```

This is the same GDML-specific comment as in File 8 (TDISHandler), present in an HRM XML handler where GDML accession number grouping is not applicable.

**Copied code — `updateLabStatus()`** (HRMXMLHandler line 79 vs. GDMLHandler line 113):

Same nested while-loop algorithm. Uses raw JDBC instead of DAO for the outer query, but the inner abnormal-status checking logic is identical.

---

### File 10: `upload/handlers/MEDVUEHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/MEDVUEHandler.java` |
| **Lines** | 105 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `upload/handlers/GDMLHandler.java` (149 lines, GPL 2.0+) |

No explicit derivation comment.

**Copied code — `updateLabStatus()`** (MEDVUEHandler line 70 vs. GDMLHandler line 113):

Same nested while-loop algorithm. The logger message at line 87 (`"obr(" + i + ") obx(" + j + ") abnormal ? : " + h.getOBXAbnormalFlag(i, j)`) is identical to GDMLHandler line 130.

**Copied code — `parse()` method** (MEDVUEHandler line 40):

Same workflow as GDMLHandler.

---

### File 11: `upload/handlers/PFHTHandler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/PFHTHandler.java` |
| **Lines** | 98 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `upload/handlers/GDMLHandler.java` (149 lines, GPL 2.0+) |

No explicit derivation comment.

**Copy-paste artifact — GDML-specific comment** (lines 51-53):
```java
// Since the gdml labs show more than one lab on the same page when grouped
// by accession number their abnormal status must be updated to reflect the
// other labs that they are grouped with aswell
```

Same GDML-specific comment in a PFHT handler.

**Copied code — `updateLabStatus()`** (PFHTHandler line 67 vs. GDMLHandler line 113):

Same nested while-loop algorithm.

---

### File 12: `upload/handlers/OLISHL7Handler.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/OLISHL7Handler.java` |
| **Lines** | 102 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `upload/handlers/GDMLHandler.java` (149 lines, GPL 2.0+) |

No explicit derivation comment (lines 15-19 contain generic "HL7Handler / Upload handler").

**Copied code — `parse()` method** (OLISHL7Handler line 53 vs. GDMLHandler line 68):

Same core pattern: call `Utilities.separateMessages()` (McMaster's utility, GPL 2.0+), iterate through messages, call `MessageUploader.routeReport()`. OLIS adds duplicate checking and provider routing around this core. Notably, this file directly calls the McMaster `Utilities.separateMessages()` method (line 63), establishing a direct code dependency on the McMaster GPL 2.0+ file.

---

### File 13: `util/ICLUtilities.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/util/ICLUtilities.java` |
| **Lines** | 156 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `util/Utilities.java` (251 lines, GPL 2.0+) |

**Explicit derivation statement** (lines 15-20):
```java
/*
 * ICLUtilities.java
 * Created on Feb. 27, 2009
 * Modified by David Daley, Indivica
 * Derived from Utilities.java by wrighd
 */
```

**Copied code — `separateMessages()` method** (ICLUtilities lines 50-101 vs. Utilities lines 71-121):

The two-flag state machine (`firstPIDflag`, `firstMSHflag`) for HL7 message boundary detection is copied verbatim. Variable names, logic flow, and comment text are identical between lines 58-82 (ICLUtilities) and lines 78-101 (Utilities). The one substantive addition is at ICLUtilities line 85:
```java
sb.append(line + "|2.3\r\n");
```
This appends `"|2.3"` to MSH lines to force HL7 version 2.3. In Utilities.java, the corresponding line 102 is simply:
```java
sb.append(line + "\r\n");
```

The entire remainder of the algorithm (approximately 45 lines of message-splitting logic) is unchanged from the McMaster original.

---

### File 14: `util/PFHTUtilities.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/util/PFHTUtilities.java` |
| **Lines** | 178 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `util/Utilities.java` (GPL 2.0+), via `ICLUtilities.java` |

**Explicit derivation statement** (lines 15-18):
```java
/*
 * Modified by Divya Mantha, Indivica
 * Derived from ICLUtilities.java by David Daley, Indivica
 */
```

This establishes a two-step derivation chain: McMaster `Utilities.java` -> Indivica `ICLUtilities.java` -> Indivica `PFHTUtilities.java`.

**Copy-paste artifact — wrong class name in JavaDoc** (lines 44-47):
```java
/**
 * Creates a new instance of ICLUtilities
 */
public PFHTUtilities() {
```

The constructor JavaDoc says "ICLUtilities" but the class is `PFHTUtilities`.

**Copied code — `separateMessages()` method** (PFHTUtilities lines 50-84 vs. ICLUtilities lines 52-83):

The `firstPIDflag`/`firstMSHflag` algorithm is identical to ICLUtilities (which is itself identical to Utilities.java). PFHTUtilities replaces the ICL-specific `"|2.3"` addition with MDM-to-ORU message type rewriting (lines 85-101).

---

### File 15: `util/MEDVUEUtilities.java`

| Field | Value |
|-------|-------|
| **Path** | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/util/MEDVUEUtilities.java` |
| **Lines** | 173 |
| **Current header** | Indivica GPL 2.0-only (lines 1-13) |
| **Precursor** | McMaster `util/Utilities.java` (GPL 2.0+), via `ICLUtilities.java` |

No explicit derivation comment in the header.

**Copy-paste artifact — wrong class name in JavaDoc** (lines 37-40):
```java
/**
 * Creates a new instance of ICLUtilities
 */
public MEDVUEUtilities() {
```

The constructor JavaDoc says "ICLUtilities" but the class is `MEDVUEUtilities`. This confirms the file was created by copying ICLUtilities.java.

**Copied code — `separateMessages()` method** (MEDVUEUtilities lines 43-111 vs. ICLUtilities lines 50-101):

Lines 46-77 are identical to ICLUtilities lines 52-83. The `firstPIDflag`/`firstMSHflag` algorithm matches. MEDVUEUtilities replaces the ICL version-forcing logic with encoding character modification (changing `^~\&` to `^~\\` at MSH field index 1). The `for (int a = 0; ...)` loop structure for MSH segment reassembly (lines 83-88) is identical to PFHTUtilities lines 90-94.

---

### File 16: `labDisplayOLIS.jsp`

| Field | Value |
|-------|-------|
| **Path** | `src/main/webapp/lab/CA/ALL/labDisplayOLIS.jsp` |
| **Lines** | 2,161 |
| **Current header** | Indivica GPL 2.0-only (lines 1-15) |
| **Precursor** | McMaster `labDisplay.jsp` (2,780 lines, GPL 2.0+) |

No explicit derivation comment.

**Copied code — parameter handling** (labDisplayOLIS line 55 vs. labDisplay line 98):
```java
String segmentID = request.getParameter("segmentID");
```

**Copied code — Factory handler creation** (labDisplayOLIS line 96 vs. labDisplay line 220):
```java
handlerMain = Factory.getHandler(segmentID);
```

**Copied code — audit logging** (labDisplayOLIS lines 73-77 vs. labDisplay lines 194-199):

Both files use the identical `LogAction.addLog()` call pattern with the same arguments:
```java
LogAction.addLog((String) session.getAttribute("user"), LogConst.READ,
    LogConst.CON_HL7_LAB, segmentID, request.getRemoteAddr(), demographicID);
```

**Copied code — acknowledgement retrieval** (labDisplayOLIS line 80 vs. labDisplay line 1055):
```java
AcknowledgementData.getAcknowledgements(segmentID)
```

**Structural evidence — mutual forwarding**:

The two files form a complementary pair. labDisplay.jsp (McMaster, lines 235-237) forwards OLIS labs to labDisplayOLIS.jsp:
```jsp
if (handler instanceof OLISHL7Handler) {
%><jsp:forward page="labDisplayOLIS.jsp"/><%
```

labDisplayOLIS.jsp (Indivica, lines 108-112) forwards non-OLIS labs back to labDisplay.jsp:
```jsp
} else {
%><jsp:forward page="labDisplay.jsp"/><%
}
```

This mutual-forwarding architecture confirms labDisplayOLIS.jsp was created as a specialized variant of labDisplay.jsp for OLIS-specific display, sharing common initialization code.

---

### File 17: `SelectProviderSimple.jsp`

| Field | Value |
|-------|-------|
| **Path** | `src/main/webapp/oscarMDS/SelectProviderSimple.jsp` |
| **Lines** | 75 |
| **Current header** | Indivica GPL 2.0-only (lines 1-15) |
| **Precursor** | McMaster `SelectProviderAltView.jsp` (116 lines, GPL 2.0+) |

No explicit derivation comment.

**Copied code — `doStuff()` JavaScript function** (SelectProviderSimple lines 32-51 vs. SelectProviderAltView lines 55-77):

Lines 34-45 (the selection-building loop) are character-for-character identical to lines 57-68:
```javascript
var allSelected = "";
if (document.providerSelectForm.selectedProviders.selectedIndex == -1) {
    alert("Please select at least one providers");
} else {
    for (i = 0; i < document.providerSelectForm.selectedProviders.options.length; i++) {
        if (document.providerSelectForm.selectedProviders.options[i].selected) {
            if (allSelected != "") {
                allSelected = allSelected + ",";
            }
            allSelected = allSelected + document.providerSelectForm.selectedProviders.options[i].value;
        }
    }
```

**Copy-paste artifact — shared grammatical error** (SelectProviderSimple line 36, SelectProviderAltView line 59):
```javascript
alert("Please select at least one providers");
```

Both files contain the same grammatical error ("one providers" instead of "one provider"). The presence of an identical typo in both files is consistent with copy-paste.

**Copied code — form structure** (SelectProviderSimple lines 56-67 vs. SelectProviderAltView lines 96-107):

Identical: same form name (`providerSelectForm`), same action (`AssignLab.do`), same select element (`selectedProviders` with `size="10" multiple`), same `ProviderData.getProviderList()` iteration, same option value construction using the same `ArrayList` indexing pattern, same CSS links (`encounterStyles.css`, `extractedFromPages.css`), same i18n keys.

---

## Derivation Chains (Summary)

```
Chain 1: Parser Handlers
  McMaster GDMLHandler.java (parsers, 949 lines, GPL 2.0+)
    ├── [1] ICLHandler.java (parsers) — explicit: "Derived from PATH7Handler.java"
    ├── [2] TDISHandler.java (parsers) — explicit: "borrowed from GDML template"
    ├── [3] HRMXMLHandler.java (parsers) — explicit: "borrowed from GDML template"
    ├── [4] MEDVUEHandler.java (parsers) — code evidence: formatDateTime, getFullDocName, getCCDocs
    ├── [5] PFHTHandler.java (parsers) — code evidence: getFullDocName, formatDateTime, getOBXReferenceRange
    └── [6] OLISHL7Handler.java (parsers) — code evidence: getFullDocName, formatDateTime

Chain 2: Upload Handlers
  McMaster GDMLHandler.java (upload/handlers, 149 lines, GPL 2.0+)
    ├── [7] ICLHandler.java (upload) — explicit: "Derived from GDMLHandler.java"
    ├── [8] TDISHandler.java (upload) — code evidence: updateLabStatus, GDML comment
    ├── [9] HRMXMLHandler.java (upload) — code evidence: updateLabStatus, GDML comment
    ├── [10] MEDVUEHandler.java (upload) — code evidence: updateLabStatus, parse()
    ├── [11] PFHTHandler.java (upload) — code evidence: updateLabStatus, GDML comment
    └── [12] OLISHL7Handler.java (upload) — code evidence: parse() with Utilities.separateMessages()

Chain 3: Utilities
  McMaster Utilities.java (251 lines, GPL 2.0+)
    └── [13] ICLUtilities.java — explicit: "Derived from Utilities.java"
         ├── [14] PFHTUtilities.java — explicit: "Derived from ICLUtilities.java"
         └── [15] MEDVUEUtilities.java — code evidence: "Creates a new instance of ICLUtilities" leftover

Chain 4: JSP Display Files
  McMaster labDisplay.jsp (2,780 lines, GPL 2.0+)
    └── [16] labDisplayOLIS.jsp — code evidence: shared initialization code, mutual forwarding

  McMaster SelectProviderAltView.jsp (116 lines, GPL 2.0+)
    └── [17] SelectProviderSimple.jsp — code evidence: identical doStuff(), "one providers" typo
```

---

## Recommended Header Correction

For each file, the corrected header should contain:

1. The McMaster copyright line (as upstream origin)
2. The Indivica copyright line (as modifier)
3. The GPL 2.0-or-later license text (matching the upstream grant)
4. The CARLOS maintenance notice (existing)

### Corrected Java Header Template

```java
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
```

### Corrected JSP Header Template

```jsp
<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    Copyright (c) 2008-2012 Indivica Inc.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
```

### Notes on Derivation Comments

The existing derivation comments (e.g., "Derived from GDMLHandler.java, by wrighd") should be **preserved** in each file. They provide useful provenance information and do not conflict with the corrected header. Files that lack derivation comments do not require one to be added — the header correction itself is sufficient.

---

## Complete File List

| # | File Path | Lines | Derivation Type | Precursor |
|---|-----------|------:|-----------------|-----------|
| 1 | `src/main/java/.../parsers/ICLHandler.java` | 617 | Explicit comment | PATH7Handler (McMaster) |
| 2 | `src/main/java/.../parsers/TDISHandler.java` | 1,061 | Explicit comment | GDMLHandler (McMaster) |
| 3 | `src/main/java/.../parsers/HRMXMLHandler.java` | 423 | Explicit comment | GDMLHandler via TDIS |
| 4 | `src/main/java/.../parsers/MEDVUEHandler.java` | 737 | Code evidence | GDMLHandler (McMaster) |
| 5 | `src/main/java/.../parsers/PFHTHandler.java` | 843 | Code evidence | GDMLHandler (McMaster) |
| 6 | `src/main/java/.../parsers/OLISHL7Handler.java` | 3,030 | Code evidence | GDMLHandler (McMaster) |
| 7 | `src/main/java/.../upload/handlers/ICLHandler.java` | 104 | Explicit comment | GDMLHandler upload (McMaster) |
| 8 | `src/main/java/.../upload/handlers/TDISHandler.java` | 121 | Code evidence + GDML comment | GDMLHandler upload (McMaster) |
| 9 | `src/main/java/.../upload/handlers/HRMXMLHandler.java` | 113 | Code evidence + GDML comment | GDMLHandler upload (McMaster) |
| 10 | `src/main/java/.../upload/handlers/MEDVUEHandler.java` | 105 | Code evidence | GDMLHandler upload (McMaster) |
| 11 | `src/main/java/.../upload/handlers/PFHTHandler.java` | 98 | Code evidence + GDML comment | GDMLHandler upload (McMaster) |
| 12 | `src/main/java/.../upload/handlers/OLISHL7Handler.java` | 102 | Code evidence | GDMLHandler upload (McMaster) |
| 13 | `src/main/java/.../util/ICLUtilities.java` | 156 | Explicit comment | Utilities.java (McMaster) |
| 14 | `src/main/java/.../util/PFHTUtilities.java` | 178 | Explicit comment | ICLUtilities <- Utilities (McMaster) |
| 15 | `src/main/java/.../util/MEDVUEUtilities.java` | 173 | Code evidence + copy-paste artifact | ICLUtilities <- Utilities (McMaster) |
| 16 | `src/main/webapp/lab/CA/ALL/labDisplayOLIS.jsp` | 2,161 | Code evidence + mutual forwarding | labDisplay.jsp (McMaster) |
| 17 | `src/main/webapp/oscarMDS/SelectProviderSimple.jsp` | 75 | Code evidence + shared typo | SelectProviderAltView.jsp (McMaster) |
