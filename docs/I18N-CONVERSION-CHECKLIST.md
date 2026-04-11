# CARLOS EMR — i18n Conversion Checklist

Use this checklist when converting a JSP file to full i18n compliance. It applies to
both fully-hardcoded files (audit category: `none`) and partially-converted files
(category: `partial`).

**Related documents:**
- [I18N-STANDARDS.md](I18N-STANDARDS.md) — key naming rules, patterns, encoding requirements
- [JSP-REFACTORING-GUIDE.md](JSP-REFACTORING-GUIDE.md) — full JSP modernization workflow

---

## Part A: New i18n Files (category: `none`)

For JSPs with zero existing `fmt:message` usage.

### Step 1: Add the `fmt` Taglib Declaration

Add `<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>` to the taglib block at the
top of the file. Place it alongside existing `c:`, `fn:`, and OWASP encoder declarations.

```jsp
<%@ taglib uri="jakarta.tags.fmt"      prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core"     prefix="c"   %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e"   %>
```

- [ ] `fmt` taglib declaration added after other taglib declarations
- [ ] OWASP encoder taglib present: `<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>` (required for `${e:forHtmlAttribute(...)}` and related EL functions used in Steps 6–7)

### Step 2: Add the Bundle Declaration

Add a **single** `<fmt:setBundle>` immediately after all taglib declarations, before
`<!DOCTYPE html>`. Do not repeat it on individual lines.

```jsp
<fmt:setBundle basename="oscarResources"/>
```

- [ ] `<fmt:setBundle basename="oscarResources"/>` added (one occurrence, pre-DOCTYPE)

### Step 3: Set the HTML lang Attribute

Change `<html>` (or `<html lang="en">`) to use the dynamic locale:

```jsp
<html lang="${pageContext.request.locale.language}">
```

- [ ] `<html>` tag uses `${pageContext.request.locale.language}`, not hardcoded `"en"`

### Step 4: Inventory All Hardcoded Strings

Before replacing, identify all user-visible hardcoded strings. Group them by UI section
for efficient batched key creation. Check these locations:

- [ ] `<title>` tag
- [ ] `<h1>`, `<h2>`, `<h3>` headings
- [ ] `<td>` and `<th>` cell text
- [ ] `<label>` element text
- [ ] `<button>` element text
- [ ] `<option>` element text
- [ ] `<input type="submit/button/reset" value="...">` text
- [ ] `title="..."` tooltip attributes
- [ ] `placeholder="..."` attributes
- [ ] `alert("...")` and `confirm("...")` calls in JavaScript

Use the audit script to estimate the count:
```bash
awk -F',' '$1 ~ /path\/to\/file/' i18n-coverage-report.csv
```

### Step 5: Create New Keys in Properties Files

For each hardcoded string:

1. Choose the correct key name using the `<domain>.<jspFilename>.<elementDescription>`
   convention. Check `global.*` keys first — avoid creating duplicates.
2. Add the key to **all five locale files** in a single commit. For non-English locales,
   add a `# TODO: translate` comment on the **preceding line** (not appended to the value —
   inline `# ...` text is not a comment in `.properties` syntax and becomes part of the value):
   - `oscarResources_en.properties` — with the English value
   - `oscarResources_es.properties` — English value as placeholder, preceded by `# TODO: translate`
   - `oscarResources_fr.properties` — English value as placeholder, preceded by `# TODO: translate`
   - `oscarResources_pl.properties` — English value as placeholder, preceded by `# TODO: translate`
   - `oscarResources_pt_BR.properties` — English value as placeholder, preceded by `# TODO: translate`
3. Non-ASCII characters may appear directly in UTF-8 files; `\uXXXX` escapes are also valid.

```properties
# oscarResources_en.properties
admin.configureFax.title=Configure Fax Settings
admin.configureFax.btnSave=Save Configuration

# oscarResources_fr.properties
# TODO: translate
admin.configureFax.title=Configure Fax Settings
# TODO: translate
admin.configureFax.btnSave=Save Configuration
```

- [ ] Keys added to `oscarResources_en.properties`
- [ ] Keys added to `oscarResources_es.properties` (with placeholder)
- [ ] Keys added to `oscarResources_fr.properties` (with placeholder)
- [ ] Keys added to `oscarResources_pl.properties` (with placeholder)
- [ ] Keys added to `oscarResources_pt_BR.properties` (with placeholder)
- [ ] No duplicate keys created (checked `global.*` first)
- [ ] All non-ASCII chars use `\uXXXX` escapes

### Step 6: Replace Hardcoded Text with `<fmt:message>`

Working top-to-bottom through the file, replace hardcoded text with `fmt:message` calls.

```jsp
<%-- Before --%>
<title>Configure Fax Settings</title>
<h1>Configure Fax Settings</h1>
<label for="provider">Fax Provider:</label>
<button type="submit">Save Configuration</button>

<%-- After --%>
<title><fmt:message key="admin.configureFax.title"/></title>
<h1><fmt:message key="admin.configureFax.title"/></h1>
<label for="provider"><fmt:message key="admin.configureFax.labelProvider"/>:</label>
<button type="submit"><fmt:message key="admin.configureFax.btnSave"/></button>
```

For attribute values (`title=`, `placeholder=`), use `fmt:message var`:
```jsp
<fmt:message key="admin.configureFax.tooltipHelp" var="tooltipHelp"/>
<button title="${e:forHtmlAttribute(tooltipHelp)}">?</button>
```

- [ ] All `<title>` and heading text replaced
- [ ] All `<label>` text replaced
- [ ] All `<button>` and submit input values replaced
- [ ] All `<td>` and `<th>` static text replaced
- [ ] All `<option>` static text replaced
- [ ] All `title=` and `placeholder=` attribute values replaced
- [ ] `fmt:message var` used for attribute injection (not `fmt:message` inline in attributes)

### Step 7: Handle JavaScript Strings

For strings used in `alert()`, `confirm()`, or dynamic DOM manipulation:

1. Add the ResourceBundle scriptlet to the top scriptlet block:
   ```jsp
   <%@ page import="org.owasp.encoder.Encode" %>
   <%
       java.util.ResourceBundle oscarResources =
           java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
   %>
   ```

2. For 4+ JS strings, use the `var i18n = {}` object pattern:
   ```jsp
   <script>
       var i18n = {
           msgConfirmDelete: '<%= Encode.forJavaScript(oscarResources.getString("admin.configureFax.msgConfirmDelete")) %>',
           msgSaveSuccess:   '<%= Encode.forJavaScript(oscarResources.getString("admin.configureFax.msgSaveSuccess")) %>'
       };
   </script>
   ```

3. For 1–3 JS strings, use individual `const` declarations.

4. In JavaScript, replace `alert("Hardcoded text")` with `alert(i18n.msgKey)`.

- [ ] ResourceBundle scriptlet added if JS strings are needed
- [ ] `org.owasp.encoder.Encode` import added for `Encode.forJavaScript()` calls
- [ ] All `alert("...")` and `confirm("...")` calls use i18n variables
- [ ] All JS strings encoded with `Encode.forJavaScript()`
- [ ] i18n keys for JS strings added to all five locale files

### Step 8: Remove Any Legacy Inline Bundle Calls

If the file previously used the legacy inline pattern, consolidate to the single
top-level declaration already placed in Step 2.

```jsp
<%-- Remove every occurrence of: --%>
<fmt:setBundle basename="oscarResources"/>  <%-- if not the top-level one --%>
```

- [ ] No repeated inline `<fmt:setBundle>` within the HTML body

### Step 9: Verify with the Audit Script

Re-run the audit script on the converted file:

```bash
./scripts/audit-i18n-coverage.sh --output /tmp/audit.csv
awk -F',' '$1 ~ /configureFax/' /tmp/audit.csv
```

The `hardcoded_count` column should be 0, and `category` should be `full`.

- [ ] Audit script reports `category=full` for this file
- [ ] `hardcoded_count` is 0 (or near 0 for dynamic-only files)
- [ ] `legacy_bundle_count` is 0

### Step 10: Multi-Locale Testing

Test the page with at least two locale settings before committing.

1. Log in as a provider with locale set to English — verify all text renders correctly.
2. Change locale to French (`fr`) or Spanish (`es`) — verify the page renders without
   missing-key errors (CARLOS renders the key name when a key is missing; this will
   appear as raw text like `admin.configureFax.title`).
3. Check the browser console for any JavaScript errors caused by the i18n changes.
4. For pages with `confirm()` or `alert()`, trigger those dialogs to verify the text.

- [ ] Renders correctly in English locale
- [ ] Renders correctly in at least one non-English locale (no raw key names visible)
- [ ] No JavaScript console errors
- [ ] Dialogs (alert/confirm) show translated text, not raw keys

---

## Part B: Partially Converted Files (category: `partial`)

For JSPs that already use some `fmt:message` but still have hardcoded text.

### Step B1: Review Existing Key Namespace

Before adding new keys, read the file's existing `fmt:message` calls to identify the
established namespace (e.g., the file already uses `admin.configureFax.*`). Keep new
keys consistent with that namespace.

- [ ] Existing key namespace identified

### Step B2: Consolidate Legacy Bundle Declarations

If the file uses repeated inline `fmt:setBundle`, consolidate to a single declaration
at the top of the file (before `<!DOCTYPE>`). Remove all inline occurrences.

- [ ] `<fmt:setBundle>` appears exactly once, before `<!DOCTYPE html>`

### Step B3: Fix the HTML lang Attribute

If `<html lang="en">` is hardcoded, update to the dynamic locale:
```jsp
<html lang="${pageContext.request.locale.language}">
```

- [ ] `<html>` tag uses dynamic locale

### Step B4: Run the Audit Script for Remaining Gaps

```bash
./scripts/audit-i18n-coverage.sh --output /tmp/audit.csv
awk -F',' '$1 ~ /filename/' /tmp/audit.csv
```

Review the `hardcoded_count` to identify how many strings remain.

- [ ] Audit script output reviewed for this file

### Steps B5–B10

Follow Steps 4–10 from Part A for the remaining hardcoded strings.

- [ ] Remaining hardcoded strings inventoried (Step 4)
- [ ] New keys added to all five locale files (Step 5)
- [ ] Text replaced with `fmt:message` (Step 6)
- [ ] JavaScript strings handled (Step 7)
- [ ] Legacy inline bundles removed (Step 8)
- [ ] Audit confirms `category=full` (Step 9)
- [ ] Multi-locale testing complete (Step 10)

---

## Quick Reference: Key Creation Template

```properties
# ── <domain>/<jspFilename>.jsp ────────────────────────────────────────────
#   Added: YYYY-MM-DD  (for tracking; do not commit stale dates)

# Page title and headings
<domain>.<jspFilename>.title=

# Labels
<domain>.<jspFilename>.label<FieldName>=

# Buttons
<domain>.<jspFilename>.btn<Action>=

# Messages
<domain>.<jspFilename>.msg<Outcome>=

# Table headers
<domain>.<jspFilename>.th<ColumnName>=
```

---

## Quick Reference: Encoding Lookup

Non-English locale files that need `\uXXXX` escapes — common characters:

| Char | Escape | Char | Escape | Char | Escape |
|------|--------|------|--------|------|--------|
| à | `\u00e0` | Â | `\u00c2` | ç | `\u00e7` |
| â | `\u00e2` | è | `\u00e8` | é | `\u00e9` |
| ê | `\u00ea` | ë | `\u00eb` | î | `\u00ee` |
| ï | `\u00ef` | ô | `\u00f4` | ù | `\u00f9` |
| û | `\u00fb` | ü | `\u00fc` | ñ | `\u00f1` |
| ó | `\u00f3` | ú | `\u00fa` | ą | `\u0105` |
| ę | `\u0119` | ł | `\u0142` | ś | `\u015b` |
| ź | `\u017a` | ż | `\u017c` | ã | `\u00e3` |
| õ | `\u00f5` | | | | |