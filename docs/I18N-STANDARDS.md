# CARLOS EMR — Internationalization (i18n) Standards

This document defines the i18n conventions and patterns for all JSP files in CARLOS EMR.
Follow these standards when adding new pages, refactoring existing JSPs, or resolving
i18n coverage gaps identified by the audit tooling.

**Related documents:**
- [I18N-CONVERSION-CHECKLIST.md](I18N-CONVERSION-CHECKLIST.md) — per-file conversion checklist
- [JSP-REFACTORING-GUIDE.md](JSP-REFACTORING-GUIDE.md) — full JSP modernization workflow

---

## Table of Contents

1. [Key Naming Convention](#key-naming-convention)
2. [Global Namespace Rules](#global-namespace-rules)
3. [Bundle Declaration Rule](#bundle-declaration-rule)
4. [HTML lang Attribute](#html-lang-attribute)
5. [Message Usage Patterns](#message-usage-patterns)
6. [Multiline and Parameterized Messages](#multiline-and-parameterized-messages)
7. [JavaScript i18n Pattern](#javascript-i18n-pattern)
8. [UTF-8 Encoding Requirements (Java 21)](#utf-8-encoding-requirements-java-21)
9. [CI Validation](#ci-validation)
10. [Domain Priority Order](#domain-priority-order)

---

## Key Naming Convention

All i18n keys follow a three-segment dot-notation pattern:

```
<domain>.<jspFilename>.<elementDescription>
```

| Segment | Rule | Example |
|---------|------|---------|
| `domain` | Matches the JSP directory name | `admin`, `demographic`, `tickler` |
| `jspFilename` | Matches the JSP file name (camelCase, no extension) | `addPatient`, `billingSettings` |
| `elementDescription` | Semantic description of the UI element | `title`, `labelFirstName`, `btnSave` |

### Element Description Vocabulary

Use these consistent prefixes for element descriptions:

| Prefix | Use for |
|--------|---------|
| `title` | `<title>` tag and `<h1>`/`<h2>` page headings |
| `label*` | `<label>` elements (e.g., `labelFirstName`, `labelDOB`) |
| `btn*` | Button and submit input text (e.g., `btnSave`, `btnSearch`, `btnDelete`) |
| `msg*` | User-facing messages, errors, confirmations (e.g., `msgSuccess`, `msgValidationError`) |
| `th*` | Table column headings (e.g., `thPatientName`, `thDate`) |
| `td*` | Table cell labels (only when static and not a heading) |
| `opt*` | `<option>` element text (e.g., `optMale`, `optFemale`) |
| `tooltip*` | `title` attribute tooltip text |
| `placeholder*` | `placeholder` attribute text |
| `section*` | Section headings within a page |
| `tab*` | Tab labels in a tabbed interface |
| `link*` | Hyperlink text |

### Examples

```properties
# admin/configureFax.jsp
admin.configureFax.title=Configure Fax Settings
admin.configureFax.labelProvider=Fax Provider
admin.configureFax.labelPhoneNumber=Fax Number
admin.configureFax.btnSave=Save Configuration
admin.configureFax.msgSaveSuccess=Fax settings saved successfully.
admin.configureFax.msgTestFailed=Test fax failed. Check credentials and try again.

# demographic/demographicSearch.jsp
demographic.demographicSearch.title=Patient Search
demographic.demographicSearch.labelLastName=Last Name
demographic.demographicSearch.labelFirstName=First Name
demographic.demographicSearch.btnSearch=Search
demographic.demographicSearch.thPatientName=Patient Name
demographic.demographicSearch.thDOB=Date of Birth
demographic.demographicSearch.msgNoResults=No patients found matching the search criteria.
```

### WEB-INF/jsp Subdirectory Files

For JSPs under `WEB-INF/jsp/<subdomain>/`, include the subdomain as part of the domain segment:

```properties
# WEB-INF/jsp/tickler/ticklerList.jsp
tickler.ticklerList.title=Tickler List
```

---

## Global Namespace Rules

Use `global.*` keys for values that appear **5 or more times across different JSP files**.
This prevents key proliferation and ensures consistent wording throughout the UI.

### When to Use global.* Keys

- A button label like "Save" or "Cancel" that appears on dozens of pages
- Common error messages that are domain-independent
- Province names, status values, or other reference data
- Navigation labels used in the top menu

### When NOT to Use global.* Keys

- A button whose label may need to differ by context (e.g., "Save Patient" vs "Save")
- Domain-specific messages where the precise wording matters

### Existing global.* Keys (Reference)

These keys already exist in all locale files. Use them instead of creating duplicates:

**Buttons:**
```properties
global.btnSave=Save
global.btnCancel=Cancel
global.btnClose=Close
global.btnDelete=Delete
global.btnAdd=Add
global.btnBack=Back
global.btnSubmit=Submit
global.btnConfirm=Confirm
global.btnContinue=Continue
global.btnExit=Exit
global.btnExport=Export
global.btnLogout=Log Out
global.btnPrint=Print
global.btnRestore=Restore
```

**Messages:**
```properties
global.error=Error
global.msgSomethingWrong=CARLOS has encountered an unexpected error
global.msgInputKeyword=You forgot to input a keyword!
```

**Status / Common:**
```properties
global.normal=Normal
global.abnormal=Abnormal
global.today=<u>T</u>oday
global.year=Year
global.day=Day
global.default=default
global.hello=Hello
global.disclaimer=Disclaimer
```

**Gender (added 2026):**
```properties
global.gender.male=Male
global.gender.female=Female
global.gender.other=Other
global.gender.intersex=Intersex
global.gender.undisclosed=Undisclosed
```

**Provinces/Territories:**
```properties
global.BC=British Columbia
global.Ontario=Ontario
global.Alberta=Alberta
global.Quebec=Quebec
# ... (full list in oscarResources_en.properties)
```

**JavaScript:**
```properties
global.javascript.calendar=en
```

---

## Bundle Declaration Rule

### Single Declaration Per File

Place **one** `<fmt:setBundle>` declaration per JSP, immediately after the taglib declarations
and before `<!DOCTYPE html>`. Do **not** repeat it inline before each `fmt:message`.

```jsp
<%-- Copyright header --%>
<%@ page import="..." %>

<%-- Taglib declarations --%>
<%@ taglib uri="jakarta.tags.fmt"   prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core"  prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>

<%-- i18n bundle — SINGLE declaration, placed here, before DOCTYPE --%>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
```

### Legacy Pattern to Avoid

The following **inline** pattern must be avoided in new code and should be cleaned up
during conversion. It causes the bundle to be re-loaded on every message lookup:

```jsp
<%-- WRONG: repeated inline bundle setup --%>
<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.foo.title"/>
<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.foo.labelBar"/>
<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnSave"/>
```

The audit script (`scripts/audit-i18n-coverage.sh`) reports the legacy bundle count
per file in the `legacy_bundle_count` column of the CSV output.

---

## HTML lang Attribute

Always use the **dynamic** locale from the request, not a hardcoded `"en"`:

```jsp
<%-- CORRECT: dynamic locale --%>
<html lang="${pageContext.request.locale.language}">

<%-- WRONG: hardcoded to English --%>
<html lang="en">
```

This ensures screen readers and browser translation tools receive the correct language
signal when a user's session locale is French, Spanish, Polish, or Portuguese.

---

## Message Usage Patterns

### Basic Usage (preferred in HTML body)

```jsp
<fmt:message key="admin.configureFax.title"/>
```

### In Attribute Values

For text inside HTML attributes, use `fmt:message` with `var` to capture then encode:

```jsp
<%-- Capture the message into a variable first --%>
<fmt:message key="admin.configureFax.tooltipHelp" var="tooltipText"/>
<button title="${e:forHtmlAttribute(tooltipText)}">?</button>
```

Alternatively, use the scriptlet form only when inside a scriptlet-heavy legacy block:

```jsp
<input placeholder="<%= Encode.forHtmlAttribute(oscarResources.getString("admin.configureFax.placeholder")) %>">
```

### In JavaScript Strings — DO NOT use fmt:message

Do **not** use `fmt:message` inside `<script>` blocks. Use the JavaScript i18n
pattern described in the next section instead.

---

## Multiline and Parameterized Messages

### Split Keys for Multiline Content

For messages spanning multiple lines in the UI, create numbered key variants:

```properties
# oscarResources_en.properties
admin.configureFax.instructionLine1=Configure the fax provider below.
admin.configureFax.instructionLine2=Changes take effect after restarting the fax scheduler.
```

```jsp
<p><fmt:message key="admin.configureFax.instructionLine1"/></p>
<p><fmt:message key="admin.configureFax.instructionLine2"/></p>
```

### Dynamic Parameters with fmt:param

For messages containing dynamic values (names, counts, dates), use `fmt:param`:

```properties
# Properties file — use {0}, {1}, {2} as placeholders
tickler.ticklerList.msgFoundCount=Found {0} ticklers for {1}.
```

```jsp
<fmt:message key="tickler.ticklerList.msgFoundCount">
    <fmt:param value="${ticklerCount}"/>
    <fmt:param value="${e:forHtml(providerName)}"/>
</fmt:message>
```

Always OWASP-encode `fmt:param` values that originate from user input or database data.

---

## JavaScript i18n Pattern

Never embed hardcoded English strings in `<script>` blocks. Use one of the two
approved patterns below.

### Pattern A: i18n Object (recommended for multiple strings)

Pre-compute all JavaScript-facing strings server-side as a single `var i18n = {}`
object. This keeps all translations in one place and avoids scattered inline scriptlets.

```jsp
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    // Load the locale-appropriate resource bundle once
    java.util.ResourceBundle oscarResources =
        java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>

<script>
    // Pre-computed i18n strings, safely encoded for JavaScript embedding.
    // Encode.forJavaScript() prevents XSS and broken JS string literals.
    var i18n = {
        msgSaveSuccess:    '<%= Encode.forJavaScript(oscarResources.getString("admin.configureFax.msgSaveSuccess")) %>',
        msgTestFailed:     '<%= Encode.forJavaScript(oscarResources.getString("admin.configureFax.msgTestFailed")) %>',
        btnCancel:         '<%= Encode.forJavaScript(oscarResources.getString("global.btnCancel")) %>',
        msgConfirmDelete:  '<%= Encode.forJavaScript(oscarResources.getString("admin.configureFax.msgConfirmDelete")) %>'
    };
</script>
```

Usage in JavaScript:
```javascript
if (!confirm(i18n.msgConfirmDelete)) return;
alert(i18n.msgSaveSuccess);
```

### Pattern B: Individual Constants (for small numbers of strings, ≤3)

When a file needs only one or two translated strings:

```jsp
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    java.util.ResourceBundle oscarBundle =
        java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>

<script>
    const i18nSaveError = '<%= Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSaveFailed")) %>';
    const i18nNetworkError = '<%= Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorNetworkFailed")) %>';
</script>
```

### Security Requirement

**Always** use `Encode.forJavaScript()` (from `org.owasp.encoder.Encode`) when embedding
strings into JavaScript. Never use `${someVar}` directly inside `<script>` blocks.

| Context | Correct encoding |
|---------|-----------------|
| JS string literal | `Encode.forJavaScript(value)` |
| JS in HTML event attribute | `Encode.forJavaScriptAttribute(value)` |

### Reference Implementations

- [`demographic/demographiceditdemographic.jsp`](../src/main/webapp/demographic/demographiceditdemographic.jsp) — Pattern A (`var i18n = {}` object)
- [`tickler/ticklerAdd.jsp`](../src/main/webapp/tickler/ticklerAdd.jsp) — Pattern B (individual constants) + `alert()` i18n

---

## UTF-8 Encoding Requirements (Java 21)

Since Java 9, `ResourceBundle.getBundle()` — the primary mechanism used by JSP
`<fmt:message>` tags — reads `.properties` files as **UTF-8** by default. CARLOS
runs on Java 21, so all `oscarResources_*.properties` files must be saved as UTF-8.
The old `Properties.load()` ISO 8859-1 default no longer applies to
`ResourceBundle`-based loading.

### Encoding Rules

1. All `oscarResources_*.properties` files must be saved with **UTF-8** encoding.
2. Non-ASCII characters (accented letters, special symbols) may appear directly in
   the file without `\uXXXX` escaping.
3. Do **not** use ISO 8859-1 or ASCII-only encoding — doing so will cause
   `ResourceBundle` to misread multi-byte characters on Java 9+.
4. `\uXXXX` escape sequences remain valid and will continue to work, but they are no
   longer required for non-ASCII content.

### UTF-8 Character Examples

| Character | Direct UTF-8 | `\uXXXX` equivalent (still valid) |
|-----------|-------------|-----------------------------------|
| é (e acute) | `é` | `\u00e9` |
| à (a grave) | `à` | `\u00e0` |
| ç (c cedilla) | `ç` | `\u00e7` |
| ó (o acute) | `ó` | `\u00f3` |
| ñ (n tilde) | `ñ` | `\u00f1` |
| ł (l with stroke) | `ł` | `\u0142` |

**Preferred**: use direct UTF-8 characters in new translations for readability.
**Acceptable**: `\uXXXX` escapes in existing files (no migration required).

### Validation

Run the encoding check script before committing any properties file changes:

```bash
./scripts/check-i18n-properties.sh
```

The `UTF-8 Encoding Compliance` section of the report flags files with encoding issues.
All current locale files are valid UTF-8 as of April 2026 (baseline).

---

## CI Validation

### Workflow Location

The i18n validation workflow lives at
[.github/workflows/i18n-validation-workflow.yml](../.github/workflows/i18n-validation-workflow.yml)
and is already active in this repository.

If you need the same checks in another repository or fork, copy that workflow file
into `.github/workflows/`.

### What the CI Checks Validate

The workflow runs on every PR that touches `.properties` files or JSPs and enforces:

| Job | What it checks | What fails |
|-----|---------------|-----------|
| **`i18n-key-parity`** | Full properties sync across all 5 locales | Any missing or orphaned keys |
| **`i18n-new-key-parity`** | New English keys in the PR diff have matching entries in all locales | Keys added to English only |
| **`i18n-encoding`** | All `.properties` files are valid UTF-8 | Files with invalid UTF-8 encoding |
| **`i18n-jsp-bundle`** | Changed JSPs using `fmt:message` have `fmt:setBundle`; no hardcoded `lang="en"` | Missing bundle declaration or static lang |

### How to Fix Each Failure

**Key parity failure** — You added keys to `oscarResources_en.properties` but not to
all other locales:
```properties
# Add to oscarResources_es.properties, _fr, _pl, _pt_BR:
# TODO: translate
admin.newFeature.btnActivate=Activate
```

**Encoding violation** — A `.properties` file is not valid UTF-8:
```bash
# Validate UTF-8 encoding
iconv -f UTF-8 -t UTF-8 src/main/resources/oscarResources_fr.properties >/dev/null
# Fix: ensure your editor/IDE is configured to save the file as UTF-8
```

**Missing fmt:setBundle** — A new or modified JSP uses `fmt:message` without a bundle:
```jsp
<%-- Add after taglib declarations, before <!DOCTYPE> --%>
<fmt:setBundle basename="oscarResources"/>
```

**Hardcoded lang="en"** — A JSP has a static language attribute:
```jsp
<%-- Change from: --%>
<html lang="en">
<%-- To: --%>
<html lang="${pageContext.request.locale.language}">
```

### Running Validation Locally

Run these before pushing any `.properties` or JSP changes:

```bash
# Check all locale files for missing/orphaned keys and encoding issues
./scripts/check-i18n-properties.sh

# Full JSP i18n coverage audit (writes i18n-coverage-report.csv)
./scripts/audit-i18n-coverage.sh

# Quick check of a specific file
awk -F',' '$1 ~ /configureFax/' i18n-coverage-report.csv
```

---

## Domain Priority Order

When planning i18n conversion work, address domains in this order based on clinical
impact and user frequency:

| Priority | Domains | Rationale |
|----------|---------|-----------|
| **1 — Highest** | `admin/`, `provider/`, `demographic/` | Core administrative and patient-facing workflows used daily |
| **2 — High** | `appointment/`, `tickler/`, `schedule/` | Scheduling and task management used throughout the day |
| **3 — Medium** | `billing/`, `encounter/`, `casemgmt/` | Clinical documentation and billing, touched per-encounter |
| **4 — Lower** | `oscarReport/`, `report/`, `oscarResearch/` | Reporting tools, used periodically |
| **5 — Specialized** | `form/` | Clinical forms requiring domain expert translation review |

Within each domain, convert files with `category=none` first (no existing i18n), then
files with `category=partial` (incomplete coverage). Use the audit report CSV to track:

```bash
# List all "none" files in the admin domain, sorted by hardcoded string count
awk -F',' '$2=="admin" && $3=="none" {print $5, $1}' i18n-coverage-report.csv | sort -rn
```
