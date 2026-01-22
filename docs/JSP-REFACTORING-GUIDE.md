# JSP Refactoring Guide for OpenO EMR

This document provides a step-by-step process for refactoring legacy JSP files in the OpenO EMR codebase to achieve separation of concerns, modern HTML standards, and Bootstrap 5 compatibility.

## Goals

1. **Separation of Concerns**: Move all scriptlet code to a single block at the top of the file
2. **Modern View Layer**: Use JSTL and EL exclusively in the HTML view section
3. **Semantic HTML5**: Replace table-based layouts with semantic HTML and CSS Grid/Flexbox
4. **Bootstrap 5**: Adopt Bootstrap 5 for consistent, responsive styling
5. **Modern JavaScript**: Use vanilla JavaScript with modern browser APIs
6. **Visual Consistency**: Maintain existing functionality with minor visual enhancements

## Critical Rules - DO NOT BREAK

Before refactoring, understand these non-negotiable constraints:

1. **Preserve ALL field names** - Form field `name` attributes must remain identical
2. **Preserve form action URLs** - Do not change where forms submit to
3. **Preserve hidden fields** - Many forms have hidden state fields that must be maintained
4. **Preserve CSRF tokens** - The project uses OWASP CSRF Guard; forms need the token
5. **Preserve window relationships** - Many pages are popups; don't break `window.opener`
6. **Preserve copyright headers** - Keep the GPL license header at the top of each file
7. **Test after EACH phase** - Make incremental changes, commit after each working phase

## Table of Contents

1. [Pre-Refactoring Analysis](#pre-refactoring-analysis)
2. [Phase 1: Consolidate Scriptlets](#phase-1-consolidate-scriptlets)
3. [Phase 2: Convert to JSTL/EL](#phase-2-convert-to-jstlel)
4. [Phase 3: Modernize HTML Structure](#phase-3-modernize-html-structure)
5. [Phase 4: Apply Bootstrap 5](#phase-4-apply-bootstrap-5)
6. [Phase 5: Modernize JavaScript](#phase-5-modernize-javascript)
7. [Testing and Validation](#testing-and-validation)
8. [Example: addappointment.jsp Analysis](#example-addappointmentjsp-analysis)
9. [Resources](#resources)
10. [Appendix: Common Pitfalls](#appendix-common-pitfalls)

---

## Pre-Refactoring Analysis

Before starting any refactoring work, analyze the existing JSP:

### Checklist

- [ ] **Identify all scriptlet blocks** (`<% %>`) and their purposes
- [ ] **Map data dependencies**: What variables are needed in the view?
- [ ] **List security checks**: All `security:oscarSec` and privilege checks
- [ ] **Document form submissions**: Action URLs, method types, field names
- [ ] **Catalog JavaScript functions**: Which are essential, which can be replaced
- [ ] **Note AJAX/API calls**: URLs and expected responses
- [ ] **Screenshot current appearance**: For comparison testing

### Create a Variable Map

List all variables that flow from scriptlets to the view:

```text
Variable Name     | Type          | Purpose                    | View Usage
------------------|---------------|----------------------------|------------------
curProvider_no    | String        | Current provider ID        | Hidden field, AJAX
dateString2       | String        | Formatted date             | Input default value
allStatus         | List<Status>  | Appointment statuses       | Dropdown options
bMultisites       | boolean       | Multi-site enabled         | Conditional display
```

---

## Phase 1: Consolidate Scriptlets

### Step 1.1: Create the Top Scriptlet Block

Create a single consolidated scriptlet immediately after the copyright header. **IMPORTANT**: Page imports MUST come before ANY output (including security redirects).

```jsp
<%--
Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
... (preserve full copyright header) ...
--%>

<%-- ========== PAGE IMPORTS (MUST BE FIRST) ========== --%>
<%@ page import="java.util.*" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%-- ... all other imports ... --%>

<%-- NOTE: OpenO EMR uses ca.openosp.openo.* namespace (new)
     DAOs are in ca.openosp.openo.commn.dao.* (note: "commn" not "common")
     Models are in ca.openosp.openo.commn.model.*
     Exception: ProviderDao is at ca.openosp.openo.dao.ProviderDao --%>

<%-- ========== TAGLIB DECLARATIONS ========== --%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%-- ========== SECURITY CHECK ========== --%>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment" rights="w" reverse="<%=true%>">
    <% authed = false; %>
    <% response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_appointment"); %>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%-- ========== BUSINESS LOGIC ========== --%>
<%
    // ===== 1. GET SPRING BEANS =====
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    // ... other beans ...

    // ===== 2. READ REQUEST PARAMETERS =====
    String curProvider_no = request.getParameter("provider_no");
    String demographicNo = request.getParameter("demographic_no");
    // ... other parameters ...

    // ===== 3. FETCH DATA FROM DATABASE =====
    List<AppointmentStatus> allStatus = apptStatusMgr.getAllActiveStatus();
    Provider provider = providerDao.getProvider(curProvider_no);
    // ... other queries ...

    // ===== 4. PROCESS/TRANSFORM DATA =====
    String pLastname = (provider != null) ? provider.getLastName() : "";
    String pFirstname = (provider != null) ? provider.getFirstName() : "";
    // ... other transformations ...

    // ===== 5. SET PAGE CONTEXT ATTRIBUTES FOR JSTL =====
    pageContext.setAttribute("providerNo", curProvider_no);
    pageContext.setAttribute("providerName", pLastname + ", " + pFirstname);
    pageContext.setAttribute("allStatuses", allStatus);
    pageContext.setAttribute("appointmentDate", dateString2);
    pageContext.setAttribute("isMultisite", bMultisites);
    pageContext.setAttribute("sites", sites);
    // ... all other variables needed in view ...
%>
```

### Step 1.2: Key Rules for Scriptlet Consolidation

**CRITICAL ORDERING REQUIREMENT**: Page imports and taglib declarations MUST be processed before any scriptlet code (including security checks) because security redirects use `response.sendRedirect()` which requires imports to be loaded first.

1. **Page imports MUST come first** - All `<%@ page import="..." %>` directives at the very top
2. **Taglib declarations next** - All `<%@ taglib ... %>` declarations
3. **Security checks after imports** - Authentication/authorization checks (can now use response object)
4. **Spring beans next** - Get all dependencies upfront
5. **Parameters after beans** - Read all request parameters
6. **Database queries follow** - Fetch all needed data
7. **Transformations next** - Process data for view
8. **Set pageContext attributes last** - Bridge to JSTL/EL

### Step 1.3: Error Handling in Scriptlets

Wrap database operations and external calls in try-catch:

```jsp
<%
    List<AppointmentStatus> allStatus = new ArrayList<>();
    try {
        allStatus = apptStatusMgr.getAllActiveStatus();
    } catch (Exception e) {
        MiscUtils.getLogger().error("Error loading appointment statuses", e);
        // Set empty list - view will handle gracefully
    }
    pageContext.setAttribute("allStatuses", allStatus);
%>
```

### Step 1.4: File Path Security - PathValidationUtils

**SECURITY REQUIREMENT**: ALL file operations involving user input MUST use `PathValidationUtils` to prevent path traversal attacks.

```jsp
<%@ page import="ca.openosp.openo.utility.PathValidationUtils" %>

<%
    // ===== FILE OPERATIONS - SECURITY CRITICAL =====

    // WRONG - Direct file path manipulation (path traversal vulnerability)
    String userFilename = request.getParameter("filename");
    File uploadFile = new File(uploadDir + File.separator + userFilename);  // VULNERABLE!

    // CORRECT - Use PathValidationUtils for user-provided filenames
    File safeFile = PathValidationUtils.validatePath(userFilename, allowedDir);

    // For validating existing file paths
    PathValidationUtils.validateExistingPath(existingFile, allowedDir);

    // For validating uploaded files from Struts2/Tomcat
    PathValidationUtils.validateUpload(uploadedFile);

    // Complete upload validation (source + destination)
    File destFile = PathValidationUtils.validateUpload(sourceFile, filename, destDir);

    // Check if file is in allowed temp directory
    if (PathValidationUtils.isInAllowedTempDirectory(tempFile)) {
        // Process temp file
    }
%>
```

**Why this matters**: Path traversal attacks (e.g., `../../etc/passwd`) can allow attackers to read or write files outside allowed directories. `PathValidationUtils` provides consistent, secure validation across the codebase.

See: `docs/path-validation-utils.md` for complete documentation.

### Step 1.5: Exception - Unavoidable Inline Scriptlets

Some cases require inline scriptlets. **CRITICAL**: These exceptions carry security risks.

#### ⚠️ SECURITY WARNING: Dynamic CSS Generation XSS Risk

**HIGH RISK**: Dynamic CSS generation from database data is a potential XSS vector. Malicious data can:
- Inject CSS to exfiltrate data
- Break out of the style block to execute JavaScript
- Perform UI redressing attacks

```jsp
<%-- Dynamic CSS generation (acceptable exception with MANDATORY escaping) --%>
<style>
    <c:forEach items="${allStatuses}" var="status">
    <%-- CRITICAL: Both class names AND color values MUST be escaped --%>
    .status-${fn:escapeXml(status.status)} {
        background-color: ${fn:escapeXml(status.color)};
    }
    </c:forEach>
</style>
```

**Better approach**: Pre-generate CSS classes in Java code or use data attributes with JavaScript:
```jsp
<%-- Safer: Use data attributes and JS to apply styles --%>
<div class="status-indicator" data-status-color="${fn:escapeXml(status.color)}"></div>

<script>
    // Apply styles via JavaScript after validation
    document.querySelectorAll('.status-indicator').forEach(el => {
        const color = el.dataset.statusColor;
        // Validate color format before applying
        if (/^#[0-9A-Fa-f]{6}$/.test(color)) {
            el.style.backgroundColor = color;
        }
    });
</script>
```

If JSTL cannot handle the logic, document why:

```jsp
<%-- INLINE SCRIPTLET: Required for complex date formatting
     TODO: Move to DateUtils helper class --%>
<%= complexDateFormat.format(apptDate) %>
```

---

## Phase 2: Convert to JSTL/EL

### Step 2.1: Replace Scriptlet Expressions

| Old Pattern (Scriptlet) | New Pattern (JSTL/EL) |
|-------------------------|------------------|
| `<%= variable %>` | `${variable}` (for system data) or `<c:out value="${variable}"/>` |
| `<%= request.getParameter("x") %>` | `${param.x}` |
| `<%= session.getAttribute("x") %>` | `${sessionScope.x}` |
| `<%= obj.getProperty() %>` | `${obj.property}` |

**SECURITY REQUIREMENT - OWASP Encoder for User Inputs:**

For user-provided data in JSPs, **continue using OWASP Encoder** as it provides context-aware encoding:

| Context | REQUIRED Pattern | Purpose |
|---------|-----------------|---------|
| HTML body | `<%= Encode.forHtml(userInput) %>` | Prevents XSS in HTML content |
| HTML attributes | `<%= Encode.forHtmlAttribute(userInput) %>` | Safe encoding for attribute values |
| JavaScript | `<%= Encode.forJavaScript(userInput) %>` | Safe for JS string contexts |
| CSS | `<%= Encode.forCssString(userInput) %>` | Safe for CSS values |
| URLs | `<%= Encode.forUri(userInput) %>` | Safe for URL components |

**When to use JSTL instead:**
- `<c:out value="${x}"/>` is acceptable for **system-generated, non-user data**
- `${fn:escapeXml(x)}` provides basic HTML escaping for **trusted data**

**Example distinction:**
```jsp
<%-- User input - MUST use OWASP Encoder --%>
<div><%= Encode.forHtml(userEnteredName) %></div>
<input value="<%= Encode.forHtmlAttribute(userEnteredAddress) %>">

<%-- System data - JSTL is acceptable --%>
<c:out value="${systemGeneratedId}"/>
<div>${applicationVersion}</div>
```

### Step 2.2: Replace Conditional Logic

**Simple if (no else):**
```jsp
<%-- Before --%>
<% if (showAlert) { %>
    <div class="alert">Alert message</div>
<% } %>

<%-- After - use c:if for simple cases --%>
<c:if test="${showAlert}">
    <div class="alert">Alert message</div>
</c:if>
```

**If-else pattern:**
```jsp
<%-- Before --%>
<% if (bMultisites) { %>
    <div>Multi-site content</div>
<% } else { %>
    <div>Single-site content</div>
<% } %>

<%-- After - use c:choose for if/else --%>
<c:choose>
    <c:when test="${isMultisite}">
        <div>Multi-site content</div>
    </c:when>
    <c:otherwise>
        <div>Single-site content</div>
    </c:otherwise>
</c:choose>
```

**Conditional attributes (empty vs not empty):**
```jsp
<%-- Before --%>
<input value="<%= name != null ? name : "" %>">

<%-- After --%>
<input value="${not empty name ? name : ''}">

<%-- Or simply (empty values render as empty string) --%>
<input value="${name}">
```

### Step 2.3: Replace Loops

**Before (Scriptlet):**
```jsp
<% for (int i = 0; i < allStatus.size(); i++) { %>
    <option value="<%= allStatus.get(i).getStatus() %>">
        <%= allStatus.get(i).getDescription() %>
    </option>
<% } %>
```

**After (JSTL):**
```jsp
<c:forEach items="${allStatuses}" var="status" varStatus="loop">
    <option value="${status.status}">
        <c:out value="${status.description}"/>
    </option>
</c:forEach>
```

### Step 2.4: Handle HTML Attribute Encoding

**For user input (REQUIRED - use OWASP Encoder):**
```jsp
<%-- User-provided data - MUST use OWASP Encoder --%>
<input value="<%= Encode.forHtmlAttribute(userName) %>">
<div title="<%= Encode.forHtmlAttribute(userComment) %>">
```

**For system data (JSTL acceptable):**
```jsp
<%-- System-generated values - JSTL is acceptable --%>
<input value="${fn:escapeXml(systemId)}">
<input value="<c:out value='${applicationName}'/>">
```

### Step 2.5: Set Context Path Variable

At the top of the scriptlet block, set the context path for EL:

```jsp
<%
    pageContext.setAttribute("ctx", request.getContextPath());
%>
```

Then use in view:
```jsp
<%-- Note: Bootstrap 5.3.0 is loaded from CDN per project standards (see below) --%>
<script src="${ctx}/js/global.js"></script>
```

Alternative using `<c:url>` tag (preferred for URLs with parameters):
```jsp
<a href="<c:url value='/appointment/view.do'><c:param name='id' value='${apptId}'/></c:url>">View</a>
```

### Step 2.6: Internationalization (i18n) Setup

Always include the resource bundle declaration near the top of the view:

```jsp
<fmt:setBundle basename="oscarResources"/>
```

Then use messages:
```jsp
<label><fmt:message key="Appointment.formDate"/>:</label>
```

For messages with parameters:
```jsp
<fmt:message key="appointment.welcome">
    <fmt:param value="${providerName}"/>
</fmt:message>
```

### Step 2.7: Encoding Values in JavaScript Context

When embedding server values in JavaScript, use OWASP Encoder:

```jsp
<script>
    // WRONG - XSS vulnerability
    var patientName = "${patientName}";

    // CORRECT - Use OWASP Encoder with proper variable retrieval
    var patientName = "<%= Encode.forJavaScript((String) pageContext.getAttribute(\"patientName\")) %>";
</script>
```

**Better approach** - use data attributes (recommended):
```jsp
<div id="appointmentData"
     data-provider-no="${fn:escapeXml(providerNo)}"
     data-date="${fn:escapeXml(appointmentDate)}">
</div>

<script>
    const container = document.getElementById('appointmentData');
    const providerNo = container.dataset.providerNo;
    const date = container.dataset.date;
</script>
```

---

## Phase 3: Modernize HTML Structure

### Step 3.1: Replace DOCTYPE and HTML Tag

**Before:**
```html
<!DOCTYPE HTML>
<html>
```

**After:**
```jsp
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
```

Note: Use dynamic locale from request, not hardcoded "en".

### Step 3.2: Modernize Head Section

```jsp
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><fmt:message key="appointment.addappointment.title"/></title>

    <%-- Bootstrap 5.3.0 (from CDN per project standards) --%>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">

    <%-- Page-specific styles --%>
    <style>
        /* Custom styles here - keep minimal */
    </style>
</head>
```

### Step 3.3: Replace Table Layouts with Semantic HTML

**Before (Table Layout):**
```html
<table>
    <tr>
        <td>Date:</td>
        <td><input type="date" name="appointment_date"></td>
    </tr>
    <tr>
        <td>Time:</td>
        <td><input type="time" name="start_time"></td>
    </tr>
</table>
```

**After (Semantic HTML with Bootstrap Grid):**
```html
<div class="mb-3 row">
    <label for="appointment_date" class="col-sm-3 col-form-label">Date:</label>
    <div class="col-sm-9">
        <input type="date" class="form-control" id="appointment_date" name="appointment_date">
    </div>
</div>
<div class="mb-3 row">
    <label for="start_time" class="col-sm-3 col-form-label">Time:</label>
    <div class="col-sm-9">
        <input type="time" class="form-control" id="start_time" name="start_time">
    </div>
</div>
```

### Step 3.4: Semantic Structure Guidelines

| Old Pattern | New Pattern |
|-------------|-------------|
| `<table>` for layout | `<div class="row">` / `<div class="col-*">` |
| `<font>` tags | CSS classes |
| `<center>` | `text-center` class |
| `<b>` | `<strong>` or `fw-bold` class |
| `<i>` (for emphasis) | `<em>` |
| `bgcolor` attribute | CSS `background-color` |
| `width`/`height` attributes | CSS properties |
| `onclick` attributes | `addEventListener` in JS |
| `<br>` for spacing | CSS margins/padding |

### Step 3.5: Form Structure with CSRF Protection

OpenO EMR uses OWASP CSRF Guard (configured in `web.xml` and `Owasp.CsrfGuard.properties`). **All POST forms MUST include the CSRF token** to prevent Cross-Site Request Forgery attacks.

**CSRF Token Implementation:**
The project uses `CSRFPreservingFilter` and `CsrfJavaScriptInjectionFilter` for automatic token handling.

```jsp
<%@ taglib uri="http://www.owasp.org/index.php/OWASP_CSRFGuard" prefix="csrf" %>
<form id="appointmentForm" action="${ctx}/appointment/addappointment.do" method="post">
    <%-- CSRF Token - REQUIRED for all POST forms
         The <csrf:token/> tag automatically uses the configured token name
         from Owasp.CsrfGuard.properties (typically OWASP_CSRFTOKEN)
         Token is also available as ${sessionScope['OWASP_CSRFTOKEN']} --%>
    <csrf:token/>

    <%-- Preserve all existing hidden fields --%>
    <input type="hidden" name="displaymode" value="">

    <div class="card">
        <div class="card-header">
            <h5 class="card-title mb-0">
                <fmt:message key="appointment.addappointment.title"/>
            </h5>
        </div>
        <div class="card-body">
            <%-- Form fields go here --%>
        </div>
        <div class="card-footer">
            <div class="d-flex gap-2 justify-content-end">
                <button type="submit" class="btn btn-primary">
                    <fmt:message key="global.btnAdd"/>
                </button>
                <button type="button" class="btn btn-secondary" onclick="window.close()">
                    <fmt:message key="global.btnCancel"/>
                </button>
            </div>
        </div>
    </div>
</form>
```

**Alternative: Manual Token Inclusion**
If you need to manually include the token (rare cases):
```jsp
<input type="hidden" name="OWASP_CSRFTOKEN" value="${sessionScope['OWASP_CSRFTOKEN']}">
```

**For AJAX requests**, see Phase 5, Step 5.3 for CSRF token handling in fetch() calls.

---

## Phase 4: Apply Bootstrap 5

### Step 4.1: Include Bootstrap 5 Assets

```jsp
<head>
    <%-- Bootstrap 5.3.0 (from CDN per project standards) --%>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
    <%-- Content --%>

    <%-- Bootstrap 5.3.0 JS Bundle (includes Popper) - at end of body --%>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
```

### Step 4.2: Common Component Mappings

| Legacy | Bootstrap 5 |
|--------|-------------|
| `<input class="text">` | `<input class="form-control">` |
| `<select>` | `<select class="form-select">` |
| `<textarea>` | `<textarea class="form-control">` |
| Alert box | `<div class="alert alert-warning">` |
| Panel | `<div class="card">` |
| Inline buttons | `<div class="btn-group">` |
| Link styled as button | `<a class="btn btn-link">` |

### Step 4.3: Button Styling

```html
<%-- Primary action --%>
<button type="submit" class="btn btn-primary">Save</button>

<%-- Secondary action --%>
<button type="button" class="btn btn-secondary">Cancel</button>

<%-- Danger action --%>
<button type="button" class="btn btn-danger">Delete</button>

<%-- Link-style button --%>
<button type="button" class="btn btn-link">More Options</button>
```

### Step 4.4: Visual Enhancement - Rounded Borders

Per project requirements, add slight rounding to separators and backgrounds:

```css
/* Custom styles for OpenO EMR */
.card {
    border-radius: 0.375rem; /* Slight rounding */
}

.card-header {
    border-radius: 0.375rem 0.375rem 0 0;
}

hr {
    border-radius: 0.125rem;
}

.alert {
    border-radius: 0.375rem;
}
```

### Step 4.5: Preserve Existing Spacing

When translating inline styles:

**Before:**
```html
<td style="padding: 5px; font-size: 12px;">
```

**After:**
```css
/* In page-specific styles */
.appointment-form .form-label {
    padding: 0.3125rem; /* 5px */
    font-size: 0.75rem; /* 12px */
}
```

### Step 4.6: Responsive Considerations

```html
<%-- Stack on mobile, side-by-side on larger screens --%>
<div class="row">
    <div class="col-12 col-md-6">
        <%-- Left column content --%>
    </div>
    <div class="col-12 col-md-6">
        <%-- Right column content --%>
    </div>
</div>
```

---

## Phase 5: Modernize JavaScript

### Step 5.1: Replace jQuery with Vanilla JS

**Before (jQuery):**
```javascript
$(document).ready(function() {
    $("#searchBtn").click(function() {
        // handler
    });
});
```

**After (Vanilla JS):**
```javascript
document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('searchBtn').addEventListener('click', function() {
        // handler
    });
});
```

### Step 5.2: Common jQuery to Vanilla Conversions

| jQuery | Vanilla JavaScript |
|--------|-------------------|
| `$(selector)` | `document.querySelector(selector)` |
| `$(selector).val()` | `element.value` |
| `$(selector).html()` | `element.innerHTML` |
| `$(selector).text()` | `element.textContent` |
| `$(selector).show()` | `element.style.display = ''` or `element.classList.remove('d-none')` |
| `$(selector).hide()` | `element.style.display = 'none'` or `element.classList.add('d-none')` |
| `$(selector).attr('x', 'y')` | `element.setAttribute('x', 'y')` |
| `$(selector).addClass('x')` | `element.classList.add('x')` |
| `$(selector).removeClass('x')` | `element.classList.remove('x')` |
| `$.ajax({...})` | `fetch(url, options)` |

### Step 5.3: AJAX Modernization with CSRF Token

**IMPORTANT**: All AJAX POST requests must include the CSRF token.

**Before (jQuery AJAX):**
```javascript
$.ajax({
    type: "POST",
    url: contextPath + "/someAction.do",
    data: { param1: value1 },
    dataType: 'json',
    success: function(data) {
        // handle success
    },
    error: function(xhr) {
        // handle error
    }
});
```

**After (Fetch API with CSRF):**
```javascript
// Get CSRF token from the page (set this in a data attribute or hidden field)
function getCsrfToken() {
    const tokenElement = document.querySelector('input[name="OWASP_CSRFTOKEN"]');
    return tokenElement ? tokenElement.value : '';
}

async function submitData() {
    try {
        const params = new URLSearchParams({
            param1: value1,
            'OWASP_CSRFTOKEN': getCsrfToken()  // Include CSRF token
        });

        const response = await fetch(contextPath + '/someAction.do', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: params
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const data = await response.json();
        // handle success
    } catch (error) {
        console.error('Error:', error);
        // handle error
    }
}
```

**Alternative: Use FormData for complex forms:**
```javascript
async function submitForm(formElement) {
    const formData = new FormData(formElement);
    // CSRF token should already be in the form as a hidden field

    const response = await fetch(formElement.action, {
        method: 'POST',
        body: formData
    });

    return response.json();
}
```

### Step 5.4: Form Handling

**Before:**
```javascript
function onAdd() {
    document.forms['ADDAPPT'].displaymode.value = 'Add Appointment';
    document.forms['ADDAPPT'].submit();
}
```

**After:**
```javascript
function handleAddAppointment(event) {
    const form = document.getElementById('appointmentForm');
    const displayModeInput = form.querySelector('input[name="displaymode"]');
    displayModeInput.value = 'Add Appointment';
    form.submit();
}

// Attach to button
document.getElementById('addButton').addEventListener('click', handleAddAppointment);
```

### Step 5.5: Keep Essential Legacy JS

Some JavaScript may be required for integration:
- OSCAR-specific utilities (`Oscar.js`)
- Date validation functions
- Complex medical form calculations

Document these and plan for gradual replacement:

```javascript
// TODO: Replace with modern date handling
// Legacy function required for calendar integration
<script src="${ctx}/js/checkDate.js"></script>
```

### Step 5.6: Popup Window Considerations

Many OpenO EMR pages are popup windows. Preserve these relationships:

```javascript
// Preserve window opener communication
function closeAndRefresh() {
    if (window.opener && !window.opener.closed) {
        window.opener.location.reload();
    }
    window.close();
}

// Preserve callbacks to parent window
function updateParentForm(value) {
    if (window.opener && window.opener.document) {
        const parentField = window.opener.document.getElementById('targetField');
        if (parentField) {
            parentField.value = value;
        }
    }
}
```

### Step 5.7: Form Name Backward Compatibility

Legacy code often uses `document.forms['FORMNAME']`. When adding `id`, keep the `name`:

```html
<%-- Keep BOTH name and id for backward compatibility --%>
<form id="appointmentForm" name="ADDAPPT" action="${ctx}/appointment/addappointment.do" method="post">
```

This allows:
```javascript
// Modern approach
document.getElementById('appointmentForm').submit();

// Legacy code still works
document.forms['ADDAPPT'].submit();
```

---

## Testing and Validation

### Step 6.1: Functional Testing Checklist

- [ ] Form submission works correctly
- [ ] All validation messages display properly
- [ ] AJAX calls function as expected
- [ ] Security restrictions are enforced
- [ ] Data saves to database correctly
- [ ] Search functionality works
- [ ] Dropdowns populate correctly
- [ ] Date/time inputs work
- [ ] Error handling displays properly

### Step 6.2: Visual Comparison

1. Take screenshots of the original page
2. Take screenshots of the refactored page
3. Compare:
   - Layout alignment
   - Font sizes and spacing
   - Color consistency
   - Button placement
   - Alert/message styling

### Step 6.3: Browser Testing

Test in:
- [ ] Chrome (latest)
- [ ] Firefox (latest)
- [ ] Safari (latest)
- [ ] Edge (latest)

### Step 6.4: Accessibility Check

- [ ] Form labels are associated with inputs (`for` attribute)
- [ ] Tab order is logical
- [ ] Color contrast meets WCAG standards
- [ ] Screen reader compatible

---

## Example: addappointment.jsp Analysis

The `addappointment.jsp` file demonstrates common anti-patterns found throughout the codebase.

### Current Issues Identified

#### 1. Scattered Scriptlet Blocks
The file has **15+ separate scriptlet blocks** interspersed throughout:
- Lines 29-41: Security check
- Lines 109-145: Initial setup and billing recommendations
- Lines 148-184: DAO initialization and data loading
- Lines 269-284: Dynamic CSS generation for multisites
- Lines 286-292: Dynamic CSS for status colors
- Lines 647-713: Date formatting and appointment logic
- Lines 860-878: Provider lookup
- Lines 952-1049: Patient status display logic
- And more...

#### 2. Mixed Output Methods
```jsp
<%-- Scriptlet expression --%>
value='<%=request.getParameter("start_time")%>'

<%-- EL expression --%>
${pageContext.request.contextPath}

<%-- JSTL tag --%>
<c:out value="${ reason.label }"/>
```

#### 3. Table-Based Layout
```html
<table class="table table-condensed table-responsive">
    <tr>
        <td>Date:</td>
        <td><input type="date"...></td>
    </tr>
</table>
```

#### 4. Mixed Bootstrap Versions
```html
<%-- OLD: Bootstrap 3 classes used --%>
<link href="${ctx}/library/bootstrap/3.3.7/css/bootstrap.min.css"...>

<%-- NEW: Use Bootstrap 5.3.0 from CDN --%>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
```

#### 5. jQuery UI Dependencies
```javascript
$('#type').myselectmenu({...});
```

### Refactoring Approach for addappointment.jsp

#### Phase 1: Variable Inventory
Variables needed in the view:
- `curProvider_no`, `curDoctor_no`, `curUser_no`
- `userfirstname`, `userlastname`
- `everyMin`, `duration`
- `billingRecommendations`
- `allStatus` (list)
- `sites` (list)
- `bMultisites`, `locationEnabled`
- `dateString1`, `dateString2`
- `apptnum`, `bDnb`
- `pLastname`, `pFirstname`
- Plus 20+ more variables

#### Phase 2: Consolidated Scriptlet Structure
```jsp
<%-- 1. Security --%>
<%-- 2. Page imports --%>
<%-- 3. Initialize Spring beans --%>
<%-- 4. Read request parameters --%>
<%-- 5. Query database --%>
<%-- 6. Process data --%>
<%-- 7. Set all pageContext attributes --%>
<%
    pageContext.setAttribute("providerNo", curProvider_no);
    pageContext.setAttribute("statuses", allStatus);
    pageContext.setAttribute("sites", sites);
    pageContext.setAttribute("isMultisite", bMultisites);
    pageContext.setAttribute("appointmentDate", dateString2);
    pageContext.setAttribute("dayOfWeek", dateString1);
    pageContext.setAttribute("duration", duration);
    // ... etc
%>
```

#### Phase 3: View Conversion
```jsp
<div class="mb-3 row">
    <label for="appointment_date" class="col-sm-4 col-form-label">
        <fmt:message key="Appointment.formDate"/>
        <span class="text-muted">(${dayOfWeek})</span>:
    </label>
    <div class="col-sm-8">
        <input type="date"
               class="form-control"
               id="appointment_date"
               name="appointment_date"
               value="${appointmentDate}">
    </div>
</div>
```

#### Phase 4: CSS Modernization
Replace inline styles and Bootstrap 3 classes:
```css
/* appointment-form.css */
.appointment-form {
    max-width: 800px;
    margin: 0 auto;
}

.appointment-form .status-select {
    /* Preserve existing color behavior */
}
```

#### Phase 5: JavaScript Cleanup
- Replace jQuery UI selectmenu with Bootstrap 5 select or vanilla JS
- Convert AJAX calls to fetch()
- Modernize form validation

---

## Summary Checklist

For each JSP refactoring:

- [ ] **Analyzed** - Document all scriptlets, variables, and view dependencies
- [ ] **Consolidated** - Single scriptlet block at top
- [ ] **Converted** - All view logic uses JSTL/EL
- [ ] **Modernized HTML** - Semantic HTML5, no table layouts
- [ ] **Styled** - Bootstrap 5 with slight rounded borders
- [ ] **JavaScript** - Modern vanilla JS where possible
- [ ] **Tested** - Functional, visual, and cross-browser
- [ ] **Documented** - Inline comments for complex logic

## Resources

- [Bootstrap 5.3 Documentation](https://getbootstrap.com/docs/5.3/) (project uses 5.3.0)
- [JSTL 1.2 Reference](https://docs.oracle.com/javaee/5/jstl/1.1/docs/tlddocs/)
- [MDN Web Docs - Vanilla JavaScript](https://developer.mozilla.org/en-US/docs/Web/JavaScript)
- [OWASP XSS Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [OWASP Java Encoder](https://owasp.org/www-project-java-encoder/)

## Appendix: Common Pitfalls

### A1: Don't Break Form Submission
```jsp
<%-- WRONG: Changed field name --%>
<input name="appointmentDate" ...>  <%-- Was "appointment_date" --%>

<%-- CORRECT: Keep original field names --%>
<input name="appointment_date" ...>
```

### A2: Don't Remove Hidden Fields
```jsp
<%-- These hidden fields are often critical for form processing --%>
<input type="hidden" name="displaymode" value="">
<input type="hidden" name="provider_no" value="${providerNo}">
<input type="hidden" name="demographic_no" value="${demographicNo}">
```

### A3: Check for Conditional Includes
Some JSPs conditionally include other files:
```jsp
<c:if test="${showAdvancedOptions}">
    <jsp:include page="advancedOptions.jsp"/>
</c:if>
```
Test all branches when refactoring.

### A4: Verify All Submit Buttons
Forms often have multiple submit buttons with different `displaymode` values:
```jsp
<button type="submit" onclick="setDisplayMode('Search')">Search</button>
<button type="submit" onclick="setDisplayMode('Add')">Add</button>
```
Ensure all submission paths work.

### A5: Test with Different User Roles
Security tags may show/hide different content based on roles. Test with:
- Admin user
- Doctor/Provider
- Nurse
- Reception staff
