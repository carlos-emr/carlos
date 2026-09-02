# Rich Text Letter eForm — Architecture & Setup

> **Version**: 2026.3.0 (March 2026)
> **Migration**: `database/mysql/updates/update-2026-03-22-rtl-2026.3.0-modernize.sql`
> **Dependencies**: Font Awesome 6, jQuery 3.7.1, jQuery UI 1.14.2 (injected by host page)

## Overview

The Rich Text Letter (RTL) is a WYSIWYG letter-writing eForm built into CARLOS EMR. It allows
clinicians to compose formatted letters with one-click insertion of patient data (demographics,
allergies, prescriptions, lab results, vitals, preventions) and supports attachments, faxing,
emailing, and PDF export.

The RTL is the only eForm shipped by default with CARLOS. All other eForms are uploaded by clinic
administrators through the eForm Manager UI.

---

## Architecture

The RTL spans three layers:

### 1. Database-Stored Form HTML (`eform.form_html`)

The `eform` table stores the complete HTML document for the RTL in its `form_html` column.
This includes:
- CSS styles for the editor UI
- Inline JavaScript functions (`saveRTL()`, `fpreventions()`, `updateAttached()`, etc.)
- Editor configuration variables (`cfg_width`, `cfg_height`, `cfg_layout`, etc.)
- Button sidebar HTML (Letterhead, Allergies, Prescriptions, etc.)
- The `<form>` wrapper and hidden fields

**Important**: The `addHeadJavascript()` method in `EForm.java` triggers JSoup DOM parsing via
`ConvertToEdoc.getDocument()`. JSoup's `validateResourcePaths()` method validates **all** `<script>`
and `<img>` tags in the entire document (not just `<head>`) by checking if their referenced files
exist on disk. Tags referencing files that don't exist are **removed from the DOM**. This means:

- `EFormAssetDeployer` must deploy `editControl2.js`, `blank.rtl`, and `editor_help.html` to the
  eForm images directory **before** any RTL eForm is loaded. If the files are missing, JSoup
  silently removes the `<script>` tags and the editor fails to render.
- The 2026.3.0 form_html places `editControl2.js`, `stamps.js`, and the `insertEditControl()` config
  block in `<body>` (after the `<form>` tag) rather than `<head>`. This ensures the form's DOM
  elements exist before the editor script executes, and keeps the editor initialization adjacent
  to the content it creates.

### 2. Static Asset Files (deployed by `EFormAssetDeployer`)

Three files are bundled in the WAR at `WEB-INF/eform-assets/` and deployed to the eForm images
directory on Tomcat startup:

| File | Purpose | Size |
|------|---------|------|
| `editControl2.js` | WYSIWYG editor engine (toolbar, iframe, formatting commands) | ~62 KB |
| `blank.rtl` | Default blank letter template | ~500 B |
| `editor_help.html` | Help popup for the editor toolbar | ~5 KB |

**Not auto-deployed**: `stamps.js` (clinic-specific doctor signature mappings) is intentionally
excluded. Clinics create this file themselves.

Files are only deployed if they **do not already exist** in the target directory. This prevents
overwriting clinic-customized versions.

### 3. Server-Side Endpoints

| Endpoint | Class/File | Purpose |
|----------|-----------|---------|
| `eform/rtlPreventions` (+ `.do` alias) | `RtlPreventions2Action` | Returns OWASP-encoded prevention data (replaces SQL injection vulnerability) |
| `eform/efmformrtl_templates.jsp` | JSP | Returns `<option>` elements for the template dropdown |
| `eform/attachEform.jsp` | JSP | Popup UI for attaching documents to the letter |
| `eform/displayAttachedFiles.jsp` | JSP | AJAX endpoint returning attached file list HTML |
| `eform/attachDoc.do` | `EFormAttachDocs2Action` | Handles attachment form submission |
| `eform/displayImage.do` | `DisplayImage2Action` | Serves `editControl2.js` and other assets from the eForm images directory |
| `eform/addEForm` | `AddEForm2Action` | Save; also the PDF/print workflows below |

---

## Print and PDF

The RTL page exposes three print/PDF entry points. The server-rendered Download and PDF-button
flows work from the **saved** record: every server render
(`DocumentAttachmentManager.renderEFormPacketWithCompleteness`) needs an `fdid`, so there is no
"PDF without saving" path. Toolbar Print is the exception: it prints the editor iframe as it
stands and only then saves, when the letter is dirty.

| Control | Path |
|---------|------|
| Toolbar **Print** (`remotePrint()` in `eform_floating_toolbar.js`) | Clicks the form's hidden `PrintButton`, which calls `print()` on the **editor iframe** (so only the letter prints, not the sidebar), then saves through `remoteSave()` when the letter is dirty (`needToConfirm`). |
| Toolbar **Download** (`remoteDownload()`) | Posts `saveAndDownloadEForm=true`; `AddEForm2Action` saves, renders the PDF, and hands it back base64-encoded on `efmshowform_data.jsp`, which triggers the browser download. |
| Form **PDF** / **Submit & PDF** buttons (injected by `library/eforms/printControl.js`) | Post `print=true`. `AddEForm2Action` treats that flag as the legacy alias of `saveAndDownloadEForm=true`. Before 2026.09 these buttons were a plain Save with no PDF: `printControl.js` guarded its hidden inputs on a jQuery object's truthiness (never false), so the flag was never posted — and had it been, the action returned a `print` result that `struts-eform.xml` never mapped. `skipSave` is advisory only, but it still tells the two buttons apart: **Submit & PDF** is a submission, so the result page starts the download, shows the saved alert and then closes the window (the action sets `isSuccess_Autoclose`, exactly as a plain Submit does; if the completeness gate refuses the render first, the approval page carries the intent as a hidden `autoClose` input so the approved download still closes), while **PDF** leaves the window open. The eForm Generator and Visual Editor emit `printControl.js` into generated clinic eForms too, so the alias covers them as well. |

Two invariants keep these working:

- `printControl.js` serializes the letter through `saveRTL()` when it is defined, so the stored
  `Letter` value carries the same entity escaping as a plain Save. Both readers of that value
  (`editControl2.js` on reopen and `EFormRenderPdfHtmlComposer.decodeStoredLetter()` for PDF)
  decode unconditionally, so a raw write would come back mangled.
- `editControl2.js` re-registers its dirty-flag listener (`attachDirtyFlagListener()`) after every
  template load. Loading `blank.rtl` navigates the editor iframe, which replaces its `Window` and
  drops listeners registered on the old one; before this, typing into a new letter never set
  `needToConfirm`, so toolbar Print printed without saving and closing never warned.

Three related invariants were fixed at the same time:

- `eform/rtlPreventions.do` is a compatibility alias of `eform/rtlPreventions` in
  `struts-eform.xml`, because the shipped form_html calls the `.do` spelling from the
  Preventions sidebar button (it rendered "Error loading preventions." without it).
- Every `*.rtl` file in the eForm image directory is served by `DisplayImage2Action` without
  the stored-asset `sandbox` CSP, like `blank.rtl`: the template dropdown offers exactly those
  files and the editor navigates its iframe to the chosen one, so a sandboxed template made the
  frame cross-origin and broke editing.
- `editControl2.js` turns `designMode` on in the editor frame before every template parse
  (`enableEditorDesignMode()`): the parent's `iframe.onload` runs before the template's own
  `<body onload>` does, so `seteditControlContents()` used to refuse the write, log
  "cannot set editor contents" on every new letter, and drop clinic templates' content.

Regression coverage: `AddEForm2ActionPrintAliasTest` (server alias, mapped results),
`RichTextLetterPrintAssetRegressionTest` (browser assets), `DisplayImage2ActionUnitTest`
(template serving), `EFormJspMigrationRegressionTest` (the `.do` alias), and the live browser
check `scripts/eform-rtl-print-pdf-playwright-checks.js`
(`npm run test:eform-rtl-print-pdf-playwright`), which drives Preventions, Download, the form's
PDF button, toolbar Print, "Submit & Print" and (with `RTL_TEMPLATE_NAME`) a clinic template
against a running CARLOS and verifies real PDF bytes come back.

Attachments ride the same download: both paths save the letter first and then render the packet
(`DocumentAttachmentManager.renderEFormPacketWithCompleteness`), which appends everything attached
to that `fdid` after the letter: other eForms first, then eDocs, labs, HRM reports and PDF-ready
encounter forms. The
letter offers two ways to attach: the floating toolbar's Attach dialog (selections travel as
hidden `docNo`/`labNo`/`hrmNo`/`eFormNo`/`formNo` inputs and are persisted on save) and the
editor's own paperclip, which posts to `eform/attachDoc` against the saved `fdid` and is what the
"Attached Files" panel (`eform/displayAttachedFiles`) reflects. A save re-submits the hidden
inputs as the complete set, so the saved view embeds every current attachment before any
download. `scripts/eform-rtl-attachment-pdf-playwright-checks.js`
(`npm run test:eform-rtl-attachment-pdf-playwright`) attaches one item of each family to its own
letter and proves it shows on the saved letter and adds pages to the PDF from both paths; HRM
needs the report fixture in `.devcontainer/db/db_data/hrm/`, and
`HRMReportParserFixtureUnitTest` pins the JAXB enum mappings that fixture depends on.

---

## Required Directories

The RTL eForm requires the eForm images directory to exist before Tomcat starts. The path
is resolved by `CarlosProperties.getEformImageDirectory()` using a two-tier lookup:

1. **Explicit property**: `EFORM_IMAGES_DIR` in `carlos.properties` (if set)
2. **Fallback**: `Paths.get(BASE_DOCUMENT_DIR, "eform", "images")` — i.e., `BASE_DOCUMENT_DIR/eform/images/`

The devcontainer explicitly sets `EFORM_IMAGES_DIR=/var/lib/CarlosDocument/carlos/eform/images/`
in its `carlos.properties`. On a fresh install where only `BASE_DOCUMENT_DIR` is configured,
the path would be `BASE_DOCUMENT_DIR/eform/images/` (no context segment).

For the default devcontainer:

```bash
/var/lib/CarlosDocument/carlos/eform/images/
```

### Creating the Directory

**DevContainer setup** (add to `populate_db.sh` or container init):
```bash
mkdir -p /var/lib/CarlosDocument/carlos/eform/images/
```

**Production setup**: The directory should be created as part of the initial CARLOS deployment.
The `EFormAssetDeployer` logs a warning and skips deployment if the directory doesn't exist:
```
WARN EFormAssetDeployer - eForm image directory does not exist: /var/lib/CarlosDocument/carlos/eform/images/; skipping asset deployment
```

If you see this warning in the Tomcat logs after a fresh install, create the directory and
restart Tomcat.

---

## Database Setup

The RTL eForm is seeded through a sequence of SQL migration scripts:

### Fresh Install (DevContainer)

Run in order via `populate_db.sh`:

```bash
# 1. Seed the original RTL eForm (creates the eform row)
mysql ... < database/mysql/updates/update-2012-07-12.sql

# 2. Modernize to 2026.3.0 (full replacement of form_html)
mysql ... < database/mysql/updates/update-2026-03-22-rtl-2026.3.0-modernize.sql

# 3. Enable the eForm (set status=1)
mysql ... < database/mysql/updates/update-2026-03-12-rtl-enable-direct.sql
```

### Migration from v2.1

The 2026.3.0 migration script (`update-2026-03-22-rtl-2026.3.0-modernize.sql`) does a **full replacement**
of `form_html`. It matches on `form_name = 'Rich Text Letter' AND subject LIKE 'Rich Text Letter Generator%'`
and replaces the entire content with the known-good 2026.3.0 HTML.

The script is idempotent: running it multiple times produces the same result.

---

## 2026.3.0 Changes from v2.1

| Change | Description |
|--------|-------------|
| Remove jQuery 1.12.4 CDN | Host page now injects jQuery 3.7.1 |
| Remove jQuery UI 1.8.18 | Host page now injects jQuery UI 1.14.2 |
| Replace Font Awesome 4 CSS | Updated to `fontawesome-all.min.css` (FA6) |
| Remove colorPicker plugin | Color prompts now use browser `prompt()` |
| Fix `saveRTL()` escaping | Chain replacements from `myNewString` (not `theRTL`); add `&` escaping |
| Fix `fpreventions()` SQL injection | Replaced raw SQL via `RptByExample.do` with safe AJAX to `rtlPreventions.do` |
| Fix `popupEformUpload()` | Pass `requestId` so attachments link to the eForm instance |
| Move scripts to `<body>` | Ensures form DOM exists before editor script executes; keeps editor init adjacent to content |

### Critical: Script Placement in `<body>`

The v2.1 form_html placed `editControl2.js`, `stamps.js`, and the `insertEditControl()` config
block inside `<head>`. This worked when `efmformadd_data.jsp` output the form_html as a raw string.

In the current codebase, `addHeadJavascript()` (called at line 136-138 of `efmformadd_data.jsp`)
triggers JSoup parsing of the form_html via `getDocument()`. JSoup's `validateResourcePaths()`
then validates **all** script tags in the entire document (both `<head>` and `<body>`) by checking
if their referenced files exist on disk. Script tags with `src` attributes pointing to files
served via `displayImage.do?imagefile=<filename>` are resolved to the eForm images directory
and checked with `Files.exists()`. If the file doesn't exist (because the directory hasn't been
created or `EFormAssetDeployer` hasn't run), the script tag is **silently removed** from the DOM.

The 2026.3.0 form_html moves these scripts to `<body>` so that the form's DOM elements (`<form>`,
`<textarea>`, etc.) are already parsed before `insertEditControl()` runs. The real protection
against JSoup stripping is `EFormAssetDeployer`, which deploys the files to disk at startup
before any eForm is rendered.

---

## Token System

The form_html uses custom tokens that are replaced server-side by `EForm.java` before rendering:

| Token | Replaced By | Method |
|-------|------------|--------|
| `${oscar_javascript_path}` | `/carlos/library/` | `setContextPath()` |
| `${oscar_image_path}` | `/carlos/eform/displayImage.do?imagefile=` | `setImagePath()` |
| `${fdid}` | The eForm data record ID (e.g., `12345`) | `setFdid()` |

The `${oscar_javascript_path}` token is used for shared eForm library scripts (`APCache.js`,
`faxControl.js`, etc.). The `${oscar_image_path}` token is available for scripts served from
the eForm images directory, but the RTL uses relative paths (`../eform/displayImage.do?imagefile=`)
for `editControl2.js` and `stamps.js` instead.

---

## EFormAssetDeployer

**Class**: `io.github.carlos_emr.carlos.eform.EFormAssetDeployer`
**Bean**: Registered in `applicationContext.xml` as `eFormAssetDeployer`

Implements `InitializingBean` and `ServletContextAware`. On Spring context startup
(`afterPropertiesSet()`), it:

1. Reads `CarlosProperties.getEformImageDirectory()` for the target path
2. Checks the directory exists (logs warning and skips if not)
3. For each asset in `WEB-INF/eform-assets/`:
   - Checks if the file already exists in the target directory
   - If not, copies it from the WAR using `ServletContext.getResourceAsStream()`

This ensures clinics get the default assets on first deployment without overwriting any
customized versions on subsequent restarts.

---

## Troubleshooting

### Editor Not Showing (No Toolbar, No Iframe)

**Symptom**: Page loads with sidebar buttons but no WYSIWYG editor in the center.
Console shows `ReferenceError: insertEditControl is not defined`.

**Causes**:
1. **eForm images directory doesn't exist** — Check Tomcat logs for
   `EFormAssetDeployer - eForm image directory does not exist`. Create the directory and restart.
2. **`editControl2.js` not deployed** — Check if the file exists in the eForm images directory.
   If missing, restart Tomcat to trigger `EFormAssetDeployer`.
3. **Script tags in `<head>`** — If custom-editing `form_html`, ensure `editControl2.js` and
   `insertEditControl()` are in `<body>`, not `<head>`.

### Template Dropdown Shows "loading..."

**Symptom**: The template selector shows "loading..." and never populates.

**Cause**: `efmformrtl_templates.jsp` is returning an error. Check:
- OWASP taglib URI: must be `owasp.encoder.jakarta` (not the legacy HTTPS URI)
- `EFormUtil.listRichTextLetterTemplates()` needs the eForm images directory to exist
- Only `.rtl` files in the images directory are listed as templates

### `${fdid}` Token Not Replaced

**Symptom**: Console shows requests to `displayAttachedFiles.jsp?requestId=${fdid}` (literal token).

**Cause**: The `setFdid()` method only runs when loading a saved eForm instance (with an `fdid`
URL parameter). For new forms (via `efmformadd_data.jsp`), there is no `fdid` yet — the token
remains as a literal string. This is expected behavior: attachments are only available after
the form has been saved at least once.

---

## File Reference

### Source Files
```
src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java
src/main/java/io/github/carlos_emr/carlos/eform/actions/RtlPreventions2Action.java
src/main/webapp/WEB-INF/eform-assets/editControl2.js
src/main/webapp/WEB-INF/eform-assets/blank.rtl
src/main/webapp/WEB-INF/eform-assets/editor_help.html
src/main/webapp/eform/efmformrtl_templates.jsp
src/main/webapp/eform/attachEform.jsp
src/main/webapp/eform/displayAttachedFiles.jsp
```

### Configuration
```
src/main/resources/applicationContext.xml          (bean: eFormAssetDeployer)
src/main/webapp/WEB-INF/classes/struts-eform.xml    (actions: eform/rtlPreventions, eform/addEForm)
```

### Database
```
database/mysql/updates/update-2012-07-12.sql        (v1.0 seed)
database/mysql/updates/update-2026-03-12-rtl-enable-direct.sql  (enable/disable)
database/mysql/updates/update-2026-03-22-rtl-2026.3.0-modernize.sql  (2026.3.0 full replacement)
```

### Release
```
release/editControl2.js                             (Debian package copy)
```
