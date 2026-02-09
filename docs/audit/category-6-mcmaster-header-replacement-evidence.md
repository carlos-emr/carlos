# Category 6 Supplement: McMaster GPL-2+ Header Replacements - Code Evidence

> **Source**: Full unshallowed clone of authoritative McMaster repo
> `bitbucket.org/oscaremr/oscarpro` (commit history back to 2004)

---

## Overview

This document provides code-level evidence for files where McMaster University
GPL-2+ copyright headers were removed and replaced with Indivica Inc. GPL-2-only
headers. It covers:

- **Category C**: 3 pre-existing McMaster files with header-only swaps (93-100%
  McMaster code retained)
- **Category E**: Index.jsp, a substantial rewrite retaining ~20% McMaster code

Under GPL, removing a copyright notice from a derivative work violates the license.
The McMaster copyright must be restored on all files containing McMaster-authored code.

---

## Category C: Pre-Existing McMaster Files - Header-Only Swaps

All three files had their headers replaced in a single commit:

- **Commit**: `6e0e37c0` (2013-04-16)
- **Author**: Marc Dumontier `<marc@mdumontier.com>`
- **Message**: "Fixed Indivica licenses in HRM work"
- **Change pattern**: Identical across all 3 files: 5 insertions, 21 deletions
  (header block replacement only, zero code changes)

### The Header Replacement (identical for all 3 files)

```diff
 <%--

-    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
-    This software is published under the GPL GNU General Public License.
-    This program is free software; you can redistribute it and/or
-    modify it under the terms of the GNU General Public License
-    as published by the Free Software Foundation; either version 2
-    of the License, or (at your option) any later version.
-
-    This program is distributed in the hope that it will be useful,
-    but WITHOUT ANY WARRANTY; without even the implied warranty of
-    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
-    GNU General Public License for more details.
-
-    You should have received a copy of the GNU General Public License
-    along with this program; if not, write to the Free Software
-    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
-
-    This software was written for the
-    Department of Family Medicine
-    McMaster University
-    Hamilton
-    Ontario, Canada
+    Copyright (c) 2008-2012 Indivica Inc.
+
+    This software is made available under the terms of the
+    GNU General Public License, Version 2, 1991 (GPLv2).
+    License details are available via "indivica.ca/gplv2"
+    and "gnu.org/licenses/gpl-2.0.html".

 --%>
```

---

### C-1: `hrmKeyUploader.jsp` — 100% McMaster Code, Zero Indivica Changes

| Field | Value |
|---|---|
| Path | `src/main/webapp/hospitalReportManager/hrmKeyUploader.jsp` |
| Original creator | danwright523, 2007-07-13 |
| Original license | McMaster University, GPL-2+ |
| Lines at header swap | 144 total (118 code + 26 header) |
| Indivica code changes before swap | **None. Zero commits by any Indivica employee.** |
| Header swap commit | `6e0e37c0` (2013-04-16, Marc Dumontier) |
| Code body after swap | **Byte-for-byte identical to McMaster original** |

**Full commit history** (only 3 commits ever touched this file before the swap):

```
2007-07-13  danwright523             # File created (new HRM module)
2012-04-20  Marc Dumontier           # Added McMaster GPL-2+ header (mass licensing)
2013-04-16  Marc Dumontier           # Replaced McMaster header with Indivica
```

**The entire code body is McMaster community work.** Below is the complete file
content that was relabeled. Every line below the header comment was written by
`danwright523` in 2007 and has never been modified:

```jsp
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%
if(session.getValue("user") == null) response.sendRedirect("../../logout.jsp");
%>

<%@page contentType="text/html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar"%>

<%
String outcome = (String) request.getAttribute("outcome");
String filePath = (String) request.getAttribute("filePath");
String type = (String) request.getAttribute("type");
if(outcome != null){
    if(outcome.equals("success")){
%><script type="text/javascript">alert("Key uploaded successfully");opener.updateLink(<%=filePath%>,<%=type%>);</script>
<%
    }else if(outcome.equals("exception")){
%><script type="text/javascript">alert("Exception uploading the Key");</script>
<%
    }else{
%><script type="text/javascript">alert("Failed to upload Key");</script>
<%
    }
}
%>


<html>
<head>
<script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>HRM Key Uploader</title>
<link rel="stylesheet" type="text/css"
    href="../../../share/css/OscarStandardLayout.css">
<link rel="stylesheet" type="text/css"
    href="../share/css/OscarStandardLayout.css">
<script type="text/javascript" src="../../../share/javascript/Oscar.js"></script>
<script type="text/javascript" src="../share/javascript/Oscar.js"></script>
<script type="text/javascript">
            function selectOther(){
                if (document.UPLOAD.type.value == "PRIVATEKEY")
                    document.getElementById('PRIVATEKEY').style.visibility = "visible";
                else
                    document.getElementById('PRIVATEKEY').style.visibility = "hidden";
            }
            function checkInput(){
                if (document.UPLOAD.lab.value ==""){
                    alert("Please select a file to upload");
                    return false;
                }else if (document.UPLOAD.type.value == "PRIVATEKEY" && document.UPLOAD.otherType.value == ""){
                    alert("Please specify the other message type");
                    return false;
                }else{
                    var lab = document.UPLOAD.lab.value;
                    var ext = lab.substring((lab.length - 3), lab.length);
                  //  if (ext != 'hl7' && ext != 'xml'){
                  //      alert("Error: The lab must be either a .xml or .hl7 file");
                  //      return false;
                  //  }
                }
                return true;
            }
        </script>
</head>

<body>
<form method='POST' name="UPLOAD" enctype="multipart/form-data"
    action='<%=request.getContextPath()%>/hospitalReportManager/hrmKeyUploader.do'>
<table align="center" class="MainTable">
    <tr class="MainTableTopRow">
        <td class="MainTableTopRowLeftColumn" width="175"><bean:message
            key="demographic.demographiceditdemographic.msgPatientDetailRecord" />
        </td>
        <td class="MainTableTopRowRightColumn">
        <table class="TopStatusBar">
            <tr>
                <td>Upload <!--i18n--></td>
                <td>&nbsp;</td>
                <td style="text-align: right"><a
                    href="javascript:popupStart(300,400,'Help.jsp')"><bean:message
                    key="global.help" /></a> | <a
                    href="javascript:popupStart(300,400,'About.jsp')"><bean:message
                    key="global.about" /></a> | <a
                    href="javascript:popupStart(300,400,'License.jsp')"><bean:message
                    key="global.license" /></a></td>
            </tr>
        </table>
        </td>
    </tr>
    <tr>
        <td><input type="submit" value="Upload the KEY"> </td>
<!--            onclick="return checkInput()"> -->
        <td>
        <table>
            <tr>
                <td>Please select the key file:</td>
                <td><input type="file" name="importFile"></td>
            </tr>
            <tr>
                <td>Key type:</td>
                <td><select name="type" onClick="selectOther()">
                    <option value="PRIVATEKEY">PRIVATE KEY</option>
                    <option value="DECRYPTIONKEY">DECRYPTION KEY</option>
                    </select></td>
            </tr>
            <tr>
                <td><input type='hidden' name="filePath" value="filePath"></td>
            <tr>
        </table>
        </td>
    </tr>
</table>
</form>

</body>
</html>
```

---

### C-2: `hospitalReportManager.jsp` — Created 2008, ~83% Original McMaster Code

| Field | Value |
|---|---|
| Path | `src/main/webapp/hospitalReportManager/hospitalReportManager.jsp` |
| Original creator | jaygallagher (McMaster), 2008-06-26 |
| Original license | McMaster University, GPL-2+ |
| Lines at creation | 153 (code only, no header) |
| Lines at header swap | 205 (including 26-line header) |
| Indivica additions | +33 lines (HRM upload form, confidentiality statement) |
| McMaster lines removed | 7 (security block relocated) |
| Header swap commit | `6e0e37c0` (2013-04-16, Marc Dumontier) |

**The diff below shows ALL code changes made between McMaster's original creation
(2008) and the header swap (2013).** The `-` lines are McMaster code removed,
`+` lines are Indivica additions. Everything not shown is unchanged McMaster code:

```diff
 <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
 "http://www.w3.org/TR/html4/loose.dtd">
 <%-- This JSP is the first page you see when you enter 'report by template' --%>
+<%@page import="org.oscarehr.util.LoggedInInfo"%>
 <%@ taglib uri="/WEB-INF/security.tld" prefix="security"%>

-<security:oscarSec roleName="<%=roleName$%>"
-    objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
-    <%response.sendRedirect("../logout.jsp");%>
-</security:oscarSec>
-
 <%@ page import="org.oscarehr.util.SpringUtils"%>
-<%@ page import="org.oscarehr.hospitalReportManager.SFTPConnector" %>
+<%@ page import="org.oscarehr.hospitalReportManager.SFTPConnector,
+  org.oscarehr.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao" %>

 [... 130+ lines of McMaster code unchanged ...]

+    <tr>                                              ← Indivica: HRM upload form
+        <td class="MainTableLeftColumn">&nbsp;</td>
+        <td class="MainTableRightColumn">
+        <% if (request.getAttribute("success") != null) { %>
+                <%=((Boolean)request.getAttribute("success")) ? "Successful" : "Error" %>
+        <% } %>
+        <form enctype="multipart/form-data"
+          action="<%=request.getContextPath() %>/hospitalReportManager/UploadLab.do"
+          method="post">
+        Upload an HRM report: <input type="file" name="importFile" />
+        <input type="submit" name="submit" value="Upload" />
+        </form>
+        </td>
+    </tr>
+    <tr>                                              ← Indivica: Confidentiality statement
+        <td class="MainTableLeftColumn">&nbsp;</td>
+        <td class="MainTableRightColumn">
+        <%
+        HRMProviderConfidentialityStatementDao dao = ...
+        String statement = dao.getConfidentialityStatementForProvider(...);
+        %>
+        <form action=".../Statement.do" method="post">
+        Provider Confidentiality Statement:<br />
+        <textarea name="statement"><%=statement %></textarea><br />
+        <input type="submit" value="Save Statement" />
+        </form>
+        <input type="button" value="I don't want to receive any more HRM
+          outtage messages..." onclick="..." />
+        </td>
+    </tr>
```

**Summary**: McMaster wrote the entire page layout, CSS classes, security checks,
SFTP configuration table, and form structure (130+ lines). Indivica added 2 table
rows (HRM upload form + confidentiality statement, ~33 lines). The McMaster code
is the structural foundation; Indivica's additions are incremental features bolted on.

---

### C-3: `hrmPreferences.jsp` — Created 2008, ~79% Original McMaster Code

| Field | Value |
|---|---|
| Path | `src/main/webapp/hospitalReportManager/hrmPreferences.jsp` |
| Original creator | jaygallagher (McMaster), 2008-06-26 |
| Original license | McMaster University, GPL-2+ |
| Lines at creation | 201 (code only, no header) |
| Lines at header swap | 256 (including 26-line header) |
| Indivica additions | +43 lines (UserPropertyDAO data access, polling interval field) |
| McMaster lines removed | 14 (old SFTPConnector static calls replaced) |
| Header swap commit | `6e0e37c0` (2013-04-16, Marc Dumontier) |

**The diff below shows ALL code changes between creation and header swap:**

```diff
 <%@ page import="java.util.*,oscar.OscarProperties,
   oscar.oscarReport.reportByTemplate.*,
   org.oscarehr.hospitalReportManager.*,
-  org.oscarehr.util.SpringUtils"%>
+  org.oscarehr.util.SpringUtils,
+  org.oscarehr.common.dao.UserPropertyDAO,          ← Indivica: added import
+  org.oscarehr.common.model.UserProperty"%>

-    String userName = ""; // = getSettings().getId().getUUID().toString();
+    UserPropertyDAO userPropertyDao =                ← Indivica: replaced static calls
+      (UserPropertyDAO) SpringUtils.getBean("UserPropertyDAO");
+
+    String userName = "";
+    String location = "";
+    String interval = "30";
+    String privateKey = "";
+    String decryptionKey = "";
+
+    try {                                            ← Indivica: 5 try/catch blocks
+        userName = userPropertyDao.getProp("hrm_username").getValue();
+    } catch (Exception e) { userName = ""; }
+    try {
+        location = userPropertyDao.getProp("hrm_location").getValue();
+    } catch (Exception e) { location = ""; }
+    try {
+        interval = userPropertyDao.getProp("hrm_interval").getValue();
+    } catch (Exception e) { interval = "30"; }
+    try {
+        privateKey = userPropertyDao.getProp("hrm_privateKey").getValue();
+    } catch (Exception e) { privateKey = ""; }
+    try {
+        decryptionKey = userPropertyDao.getProp("hrm_decryptionKey").getValue();
+    } catch (Exception e) { decryptionKey = ""; }

-   String location = SFTPConnector.getDownloadsDirectory();  ← McMaster: removed
-   String privateKey = SFTPConnector.getOMD_keyLocation();
-   String decryptionKey = SFTPConnector.getDecryptionKey();

 [... 160+ lines of McMaster HTML/CSS/JS/form unchanged ...]

-<form action="<%=...%>/HRMPreferences.do">
+<form action="<%=...%>/HRMPreferences.do" method="post">  ← Indivica: added method

-<input readonly type="text" name="userName" value=<%=userName%> >
+<input readonly type="text" name="userName" value="<%=userName%>" >  ← Indivica: fixed quoting

+            <tr>                                      ← Indivica: added polling interval
+                <td>Auto Polling Interval</td>
+                <td colspan=2><input type="text" name="interval"
+                  value="<%=interval %>"></td>
+            </tr>
```

**Summary**: McMaster wrote the entire page structure: DOCTYPE, security checks,
session validation, CSS styling, JavaScript, the full preferences form layout with
fields for username/SFTP location/private key/decryption key, status bar, and
navigation. Indivica replaced the data access layer (SFTPConnector static calls →
UserPropertyDAO), added a polling interval field, and fixed HTML quoting on input
values. The page's visual design, layout, security model, and form structure are
entirely McMaster's work.

---

## Category E: `Index.jsp` — Substantial Rewrite Retaining McMaster Code

| Field | Value |
|---|---|
| Path | `src/main/webapp/oscarMDS/Index.jsp` |
| Original creator | jhbwood (McMaster), 2004-02-04 |
| Original license | McMaster University, GPL-2+ |
| Rewrite by | Jen `<jennifer@indivica.com>`, 2012-08-17 |
| Commit | `beafce1d` "ID: 3488010 - Inbox" |
| McMaster version | 657 lines (420 unique non-blank) |
| Indivica version | 593 lines (392 unique non-blank) |
| Diff scale | 507 insertions, 571 deletions |
| **Lines surviving from McMaster** | **84 unique non-blank lines (~20%)** |

### What Survived from McMaster (Organized by Type)

#### 1. Import and Taglib Declarations (8 lines retained)

These are the framework wiring that defines what Java classes and tag libraries
the page uses:

```jsp
<%@ page import="java.util.*" %>
<%@ page import="org.apache.commons.collections.MultiHashMap" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security"%>
<%@page import="org.oscarehr.common.hl7.v2.oscar_to_oscar.OscarToOscarUtils"%>
```

#### 2. Request Attribute Variable Declarations (19 lines retained)

These variable declarations define the page's data contract with the backend
controller. Jen's version comments them out but retains them verbatim inside
a `/* */` block. Their presence in the Indivica version confirms derivation:

```jsp
Integer pageNum=(Integer)request.getAttribute("pageNum");
Hashtable docType=(Hashtable)request.getAttribute("docType");
Hashtable patientDocs=(Hashtable)request.getAttribute("patientDocs");
String providerNo=(String)request.getAttribute("providerNo");
String searchProviderNo=(String)request.getAttribute("searchProviderNo");
Hashtable patientIdNames=(Hashtable)request.getAttribute("patientIdNames");
String patientIdNamesStr=(String)request.getAttribute("patientIdNamesStr");
Hashtable docStatus=(Hashtable)request.getAttribute("docStatus");
String patientIdStr =(String)request.getAttribute("patientIdStr");
Hashtable typeDocLab =(Hashtable)request.getAttribute("typeDocLab");
String demographicNo=(String)request.getAttribute("demographicNo");
String ackStatus = (String)request.getAttribute("ackStatus");
Integer totalDocs=(Integer) request.getAttribute("totalDocs");
Integer totalHL7=(Integer)request.getAttribute("totalHL7");
List labdocs=(List)request.getAttribute("labdocs");
List<String> normals=(List<String>)request.getAttribute("normals");
List<String> abnormals=(List<String>)request.getAttribute("abnormals");
Integer totalNumDocs=(Integer)request.getAttribute("totalNumDocs");
Hashtable patientNumDoc=(Hashtable)request.getAttribute("patientNumDoc");
```

#### 3. JavaScript Library Includes (11 lines retained)

The exact same JS/CSS library includes from the McMaster version, with paths
changed from relative (`../share/`) to absolute (`<%=request.getContextPath()%>/share/`):

```jsp
<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>
<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/scriptaculous.js"></script>
<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/effects.js"></script>
<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
<script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/share/yui/js/yahoo-dom-event.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/share/yui/js/connection-min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/share/yui/js/animation-min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/share/yui/js/datasource-min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/share/yui/js/autocomplete-min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/js/demographicProviderAutocomplete.js"></script>
```

#### 4. CSS Stylesheet Includes (4 lines retained)

```jsp
<link rel="stylesheet" type="text/css" href="<%=...%>/share/yui/css/fonts-min.css"/>
<link rel="stylesheet" type="text/css" href="<%=...%>/share/yui/css/autocomplete.css"/>
<link rel="stylesheet" type="text/css" media="all" href="<%=...%>/share/css/demographicProviderAutocomplete.css"/>
<link rel="stylesheet" type="text/css" media="all" href="<%=...%>/share/css/oscarMDSIndex.css"/>
```

#### 5. HTML Structure and Navigation Links (12+ lines retained)

```jsp
<html:base/>
<form name="reassignForm" method="post" action="ReportReassign.do" id="lab_form">
<td class="MainTableTopRowRightColumn" colspan="10" align="left">
<input type="hidden" name="favorites" value="" />
<%= (request.getParameter("fname") == null ? "" : "<input type=\"hidden\" ...>") %>
<%= (request.getParameter("lname") == null ? "" : "<input type=\"hidden\" ...>") %>
<%= (request.getParameter("hnum") == null ? "" : "<input type=\"hidden\" ...>") %>
| <a href="javascript:popupPage(400, 400,'<html:rewrite
    page="/hospitalReportManager/hospitalReportManager.jsp"/>')"
    style="color: #FFFFFF;">HRM Status/Upload</a>
| <a href="javascript:popupStart(800,1000, '<%=request.getContextPath()
    %>/olis/Search.jsp')" style="color: #FFFFFF;">
    <bean:message key="olis.olisSearch" /></a>
```

#### 6. Calendar Setup Comments (4 lines retained verbatim)

```jsp
<!-- main calendar program -->
<!-- language for the calendar -->
<!-- the following script defines the Calendar.setup helper function, which makes
       adding a calendar a matter of 1 or 2 lines of code. -->
<!-- calendar style sheet -->
```

#### 7. Patient Document List Structure (6+ lines retained)

```jsp
<dl id="patientsdoclabs">
<dl id="labdoc<%=patientId%>showSublist" style="display:none" >
<% if (demographicNo == null) { %>
<col width="120">
```

### What Jen Rewrote (~80% of the file)

- **New data model**: `ArrayList<PatientInfo>` with typed Java generics replacing
  raw `Hashtable` lookups
- **New AJAX loading system**: `Ajax.Updater` with infinite scroll, pagination,
  category filtering
- **New sidebar navigation**: Left-panel category tree (All/Documents/Labs/Normal/
  Abnormal/Per-patient)
- **New JavaScript**: ~200 lines of new JS for dynamic content loading, view
  switching (list vs preview), search functionality
- **New UI layout**: Complete CSS restructuring with new class names

### Assessment

The 84 surviving lines include substantive code: the request attribute interface
(defining what data the page expects), the JS/CSS library stack, HTML form
structure, navigation links, and calendar setup. These are not boilerplate — they
define the page's integration contract with the OSCAR framework and its dependency
chain. The Indivica version is built atop this McMaster foundation.

This is a **derived work** under copyright law: substantial new creative expression
was added by Indivica, but identifiable McMaster expression was retained and forms
part of the combined work. Both copyright holders should be credited.

---

## Recommended Header Corrections

For all 4 files, the McMaster copyright notice must be restored. The recommended
dual-copyright header format:

```jsp
<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University.
    All Rights Reserved.
    Copyright (c) 2008-2012 Indivica Inc.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    ...

--%>
```

Note: The license grant must use "either version 2 of the License, or (at your
option) any later version" (GPL-2+) because the original McMaster code was licensed
under those terms, and you cannot retroactively restrict the license of code you
did not author.

---

## Appendix: Git Evidence Summary

| File | McMaster Created | Indivica Header Swap | Code Change at Swap | McMaster Repo Commit |
|---|---|---|---|---|
| hrmKeyUploader.jsp | 2007-07-13, danwright523 | 2013-04-16, `6e0e37c0` | **None** | bitbucket.org/oscaremr/oscarpro |
| hospitalReportManager.jsp | 2008-06-26, jaygallagher | 2013-04-16, `6e0e37c0` | **None** (code changes were in earlier commits) | bitbucket.org/oscaremr/oscarpro |
| hrmPreferences.jsp | 2008-06-26, jaygallagher | 2013-04-16, `6e0e37c0` | **None** (code changes were in earlier commits) | bitbucket.org/oscaremr/oscarpro |
| Index.jsp | 2004-02-04, jhbwood | 2012-08-17, `beafce1d` | 507 ins, 571 del (rewrite) | bitbucket.org/oscaremr/oscarpro |
