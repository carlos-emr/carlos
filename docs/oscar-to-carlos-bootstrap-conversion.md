# OSCAR-to-CARLOS Bootstrap Conversion Guide

This document describes the problem with legacy OSCAR-style page layouts in CARLOS EMR and
provides a standard reference for AI assistants and developers when converting or editing
old-format OSCAR pages.

---

## The Problem

Legacy OSCAR pages (and many surviving CARLOS pages inherited from OSCAR) use a
**table-based layout** from the early 2000s. This pattern has several well-known issues:

| Problem | Impact |
|---------|--------|
| `<table>` used for page layout (not data) | Not responsive — breaks on mobile/tablet screens |
| Nested tables up to 4–5 levels deep | Difficult to read, maintain, and style |
| Deprecated HTML elements (`<font>`, `vlink`, `bgcolor`) | Fails W3C validation; browser quirks-mode rendering |
| Invalid HTML structure (`<form>` inside `<table>` but outside `<td>`) | Unpredictable browser handling, broken form submission in strict parsers |
| Inline `style` attributes for layout dimensions | Cannot be overridden by responsive breakpoints |
| No `<!DOCTYPE html>` declaration | Triggers quirks mode in modern browsers |
| No `lang` attribute on `<html>` | Accessibility failure (screen readers, search engines) |
| No viewport meta tag | Fixed-width layout on mobile devices |
| CSS pixel values without units (e.g. `width:660`) | Ignored in standards mode |
| Hard-coded pixel widths throughout | Cannot adapt to different screen sizes or zoom levels |

### Legacy OSCAR Layout Pattern (problematic)

```html
<html>                                          <!-- no DOCTYPE, no lang -->
<body vlink="#0000FF" class="BodyStyle">        <!-- deprecated vlink -->
<table class="MainTable" name="encounterTable"> <!-- table-for-layout -->
  <form action="/something.do" method="post">   <!-- form INSIDE table, outside td — invalid HTML -->
    <tr class="MainTableTopRow">
      <td>Short Title</td>
      <td><table class="TopStatusBar">          <!-- nested table for header -->
            <tr><td>Long Title</td></tr>
          </table></td>
    </tr>
    <tr>
      <td class="MainTableLeftColumn">          <!-- sidebar in a td -->
        <table>
          <tr><td><a href="#">link 1</a></td></tr>
        </table>
      </td>
      <td class="MainTableRightColumn">         <!-- content in a td -->
        <table>
          <tr><td><textarea style="width:660">  <!-- hard-coded px without unit -->
          </textarea></td></tr>
          ...
        </table>
      </td>
    </tr>
  </form>
</table>
</body>
</html>
```

---

## The CARLOS Bootstrap Standard

New and converted CARLOS pages use **Bootstrap 5** with an HTML5-compliant structure.
The reference template is `docs/CARLOS_bootstrap_layout_Example.html`.

### Key structural elements (in order)

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    Bootstrap CSS (CDN or local)
    CARLOS extractedFromPages.css
    global-head.jspf include (JSP) OR the needed components individually
  <body>
    <div class="container">           ← outermost wrapper
      jsAlertBanner                   ← hidden alert area
      <div class="page-header-bar">   ← replaces MainTableTopRow
      <div class="bg-light border rounded p-2">  ← content card
        <form>
          <div class="row g-2">
            col-12 col-md-2           ← replaces MainTableLeftColumn
            col-12 col-md-10          ← replaces MainTableRightColumn
    Bootstrap JS bundle (CDN or local)
```

### OSCAR → CARLOS class/element mapping

| OSCAR legacy | CARLOS Bootstrap equivalent |
|---|---|
| `<table class="MainTable">` | `<div class="container">` |
| `<tr class="MainTableTopRow">` + nested `TopStatusBar` table | `<div class="page-header-bar d-flex ...">` |
| `<td class="MainTableTopRowLeftColumn">` (short title) | `<span class="fw-semibold">Short Title</span>` |
| `<td class="MainTableTopRowRightColumn">` (long title) | `<div class="text-muted small">Long Title</div>` |
| `<td class="MainTableLeftColumn">` | `<div class="col-12 col-md-2">` |
| `<td class="MainTableRightColumn">` | `<div class="col-12 col-md-10">` |
| `<td class="MainTableBottomRowLeftColumn">` | _(omit or use `<div class="mt-3">`)_ |
| `<font size="2" face="Tahoma">` | `class="small"` or `class="form-label form-label-sm"` |
| `<input type="button" value="Primary Button">` | `<button type="button" class="btn btn-primary btn-sm">` |
| `<input type="button" value="Secondary">` | `<button type="button" class="btn btn-outline-secondary btn-sm">` |
| `<select style="width:660">` | `<select class="form-select form-select-sm flex-grow-1">` |
| `<textarea cols="80" rows="4">` | `<textarea class="form-control form-control-sm" rows="4">` |
| `vlink="#0000FF"` on `<body>` | Remove — style visited links with CSS `:visited` if needed |
| `bgcolor="#D6D5C5"` on tables | Remove — use Bootstrap background utilities (`bg-light`, etc.) |
| `nowrap` attribute on `<td>` | `class="text-nowrap"` |
| `valign="top"` on `<td>` | `class="align-top"` or Bootstrap `align-items-start` on row |

---

## Standard Alert Banner

Every converted page must include the alert banner immediately inside `.container`:

```html
<div id="jsAlertBanner"
     class="alert alert-danger alert-dismissible"
     style="display:none"
     role="alert">
    <span id="jsAlertText"></span>
    <button type="button"
            class="btn-close"
            onclick="this.closest('.alert').style.display='none'"
            aria-label="Close"></button>
</div>
```

Show it from JavaScript:

```javascript
document.getElementById('jsAlertText').textContent = errorMessage;
document.getElementById('jsAlertBanner').style.display = '';
```

---

## Standard Page Header Bar

```html
<div class="page-header-bar d-flex align-items-center justify-content-between
            py-2 mb-3 border-bottom" id="header">
    <div class="d-flex align-items-center gap-2">
        <!-- Optional FontAwesome Icon — remove <i> if not needed -->
        <i class="fa-regular fa-file" aria-hidden="true"></i>
        <span class="fw-semibold">Short Title</span>
    </div>
    <div class="text-muted small">Long Title / Patient Name / Context Info</div>
</div>
```

---

## JSP-Specific Notes

When the converted file is a `.jsp` (not a static `.html`):

1. **Replace the `<head>` block** with a single JSP include:

   ```jsp
   <%@ include file="/includes/global-head.jspf" %>
   ```

   `global-head.jspf` provides: viewport meta, Bootstrap CSS/JS, jQuery, Font Awesome, and the CSRF guard script.
   You will need to add an explicit `<meta charset>`
   and remove explicit Bootstrap `<link>`, and `<script src="global.js">` tags.

2. **Add OWASP encoding** for every value rendered from server-side data:

   ```jsp
   <%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
   ...
   ${e:forHtml(someValue)}
   ${e:forHtmlAttribute(someAttr)}
   ```


3. **CSRF tokens are auto-injected** by `CsrfGuardScriptInjectionFilter` — do not manually add
   hidden `<input name="CSRF-TOKEN">` elements in JSP markup; the filter handles injection
   automatically for standard form submits. However, if you convert a form submit to
   `fetch()` or XHR, you must read the auto-injected token and include it explicitly:
   ```javascript
   const csrfToken = document.querySelector('input[name="CSRF-TOKEN"]')?.value;
   // include csrfToken in the request body or as a header
   ```
   
   A shared `getCsrfToken()` helper is available in
   `src/main/webapp/share/javascript/oscarMDSIndex.js` and is the recommended pattern for
   fetch/XHR CSRF inclusion.

4. **Preserve all `name` attributes** on form fields exactly — back-end Actions read these by name.

5. **Preserve form `action` URLs** — do not change `.do` action paths.

---

## AI Assistant Directions for Editing OSCAR-format Pages

> **For AI assistants (Claude, GitHub Copilot, etc.):** When you open a CARLOS EMR JSP or
> HTML file and identify the legacy OSCAR table-based layout pattern, apply the following
> rules. These rules are in addition to the general guidance in `CLAUDE.md`.

### Detection: Is this a legacy OSCAR-format page?

A page is considered legacy OSCAR format if it contains **two or more** of these signals:

- `<table class="MainTable"` or `id="MainTable"`
- `<tr class="MainTableTopRow"` or `class="MainTableBottomRow"`
- `<td class="MainTableLeftColumn"` or `class="MainTableRightColumn"`
- `<form` placed directly inside `<table>` but outside `<tr>`/`<td>`
- `<body vlink=`
- `<font size=` or `<font face=`
- `<table class="TopStatusBar"`
- `bgcolor=` as a table or td attribute
- `<div id="Layer1"` with `visibility: hidden` for popup scaffolding

### Conversion rules

When asked to edit or convert a legacy OSCAR-format page, follow these rules **in addition
to all rules in `CLAUDE.md`**:

1. **Scope your change to what was asked.** Do not convert entire pages as a side effect
   of a small feature change. If you notice conversion opportunities, mention them as
   follow-up suggestions — do not implement them unless explicitly requested.

2. **Use the CARLOS Bootstrap template as the structural reference.**
   File: `docs/CARLOS_bootstrap_layout_Example.html`
   The HTML5 skeleton, Bootstrap CDN links, alert banner, page-header-bar, and
   two-column grid in that file represent the project standard.

3. **HTML5 compliance is required for all new or converted markup:**
   - Add `<!DOCTYPE html>` if missing.
   - Add `lang="en"` on `<html>` if missing.
   - Add `<meta charset="UTF-8">` and viewport meta if missing (unless `global-head.jspf` is included).
   - Remove or replace all deprecated elements and attributes:
     `<font>`, `vlink`, `alink`, `bgcolor`, `cellpadding`, `cellspacing`, `border` on non-data tables, `nowrap` (use `class="text-nowrap"`), `valign` (use Bootstrap utilities).

4. **Fix the form / table nesting bug.** The pattern `<table><form>...<tr>` is invalid HTML.
   The correct structure is `<form><table>...<tr>` (form wraps the table) or, preferably
   in converted pages, the form wraps a Bootstrap grid `<div class="row">` with no table.

5. **Do not use tables for page layout.** Tables are only appropriate for genuinely tabular
   data (e.g., lab results grids, billing line items). Replace layout tables with Bootstrap
   grid (`<div class="row">` / `<div class="col-...">`).

6. **Form control classes.** Apply Bootstrap form classes to all controls in converted markup:
   - Text inputs: `class="form-control form-control-sm"`
   - Selects: `class="form-select form-select-sm"`
   - Textareas: `class="form-control form-control-sm"`
   - Labels: `class="form-label form-label-sm"`
   - Primary buttons: `class="btn btn-primary btn-sm"`
   - Secondary / cancel buttons: `class="btn btn-outline-secondary btn-sm"`
   - Danger / delete buttons: `class="btn btn-danger btn-sm"`

7. **Never change form field `name` attributes or form `action` URLs.**
   Back-end Struts Actions and Managers depend on these exact values.

8. **Preserve the CSRF token behaviour.** Do not manually add hidden CSRF token `<input>`
   elements in JSP markup — they are auto-injected by `CsrfGuardScriptInjectionFilter` for
   standard form submits. If converting a form to `fetch()` or XHR, read the injected token
   via `document.querySelector('input[name="CSRF-TOKEN"]')?.value` and include it in the
   request body or header. Use the `getCsrfToken()` helper from
   `src/main/webapp/share/javascript/oscarMDSIndex.js` where available.

9. **Preserve GPL copyright headers** at the top of JSP files. Do not remove or alter them.

10. **OWASP encoding is mandatory** for every server-side value rendered into HTML.
    Use `${e:forHtml(value)}` (EL) or `Encode.forHtml(value)` (scriptlet) as appropriate.
    See the OWASP Encoding section in `CLAUDE.md` for the full context-specific reference.

11. **Security checks must be preserved.** Do not remove or weaken `SecurityInfoManager
    .hasPrivilege()` checks in backing Action classes when refactoring the accompanying JSP.

12. **Test after conversion.** Run `make install` and verify the page renders correctly.
    Check for JavaScript console errors, broken form submissions, and missing data.

### Quick conversion checklist

When converting a complete page (not just editing a small section):

- [ ] `<!DOCTYPE html>` present
- [ ] `<html lang="en">` present
- [ ] `global-head.jspf` included (JSP) or Bootstrap CSS referenced (static HTML) with global.js optional
- [ ] `<div class="container">` as outermost wrapper
- [ ] `jsAlertBanner` div present immediately inside `.container`
- [ ] `<div class="page-header-bar" id="header">` present with short + long title
- [ ] Content wrapped in `<div class="bg-light border rounded p-2">`
- [ ] Two-column layout uses Bootstrap grid (`col-12 col-md-2` / `col-12 col-md-10`)
- [ ] All form controls have Bootstrap form classes (`form-control`, `form-select`, etc.)
- [ ] No deprecated HTML elements or attributes remain
- [ ] `<form>` is not nested inside `<table>` outside `<td>`
- [ ] All dynamic values use OWASP encoding
- [ ] All form field `name` attributes and `action` URLs are unchanged
- [ ] Copyright header preserved

---

## Related Documents

- `docs/OSCAR_standard_layout_Example.html` — annotated example of the legacy OSCAR layout
- `docs/CARLOS_bootstrap_layout_Example.html` — reference Bootstrap 5 equivalent
- `docs/JSP-REFACTORING-GUIDE.md` — detailed JSP refactoring process (scriptlets, JSTL migration, phased approach)
- `CLAUDE.md` → _OWASP Encoding — XSS Prevention_ section — encoding rules for all output contexts
- `CLAUDE.md` → _Struts2 Migration Pattern ("2Action")_ section — backing Action class conventions
