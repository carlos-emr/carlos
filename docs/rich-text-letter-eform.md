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

These files are bundled in the WAR at `WEB-INF/eform-assets/` and deployed to the eForm images
directory on Tomcat startup:

| File | Purpose | Size |
|------|---------|------|
| `editControl2.js` | WYSIWYG editor engine (toolbar, iframe, formatting commands) | ~62 KB |
| `blank.rtl` | Default blank letter template | ~500 B |
| `clinic_letter.rtl` | Starter template: letterhead, date, addressee, closing salutation | ~600 B |
| `consultation_letter.rtl` | Starter template: referring block, Re: line, History/Examination/Impression/Plan headings | ~800 B |
| `patient_letter.rtl` | Starter template: letter addressed to the patient | ~600 B |
| `editor_help.html` | Help popup for the editor toolbar | ~5 KB |

The template dropdown is built by `efmformrtl_templates`, which lists every `*.rtl` file it finds
in the eForm images directory — so a template that is not deployed does not exist as far as a
clinician is concerned. That is why the starter templates ship here rather than being left for an
administrator to upload. Every one of them declares the same print block:

```html
<style type="text/css" media="print">
* { color: #000000; }
.DoNotPrint { display: none; }
@page { margin: 2cm; }
</style>
```

See [Print and PDF](#print-and-pdf) for how that `@page` rule survives to the PDF — the editor does
not store it.

**Authoring rule for templates.** `populateTemplate()` in `editControl2.js` falls back to a browser
`prompt()` for any `##placeholder##` whose cached value is empty, so a template may only use
placeholders that are *always* populated for a real patient. `##_ReferringBlock##` is the trap — it
is empty for any patient with no referring doctor on file, which is most of them, and a template
carrying it pops a dialog on every new letter. Conditional content belongs on a sidebar button, not
in a template. `RichTextLetterTemplateRegressionTest` pins the allowed set.

**Not auto-deployed**: `stamps.js` (clinic-specific doctor signature mappings) is intentionally
excluded. Clinics create this file themselves; see [Signature stamps](#signature-stamps).

Files are only deployed if they **do not already exist** in the target directory. This prevents
overwriting clinic-customized versions. `editControl2.js` is the exception — it is a *managed*
asset, replaced on startup whenever the shipped bytes differ, because it is application code
rather than configuration.

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

### Page margins in the generated PDF

The editor stores `body.innerHTML` and nothing else (`editControlContents()`), so a template's
`<head>` — and with it the `@media print` block above — is gone by the time the letter is saved.
The PDF renderer then applies its own baseline print stylesheet, which zeroes `@page { margin }`
because scanned-background eForms depend on that. The combination is why a letter written on a
2cm-margin template used to print flush to the paper edge with a wide blank gutter on the right.

`EFormRenderPdfHtmlComposer.wrapLetterHtmlForPrint()` re-declares the template's print rules on the
render surface and publishes the margin as `data-carlos-page-margin` on `<body>`.
`EFormBrowserPdfService.PREPARE_PRINT_JS` reads that attribute and appends a matching `@page` rule
**after** its own baseline (cascade order is load-bearing: the baseline is injected into `<head>` at
print time and beats any earlier rule on a specificity tie). Forms that publish no attribute keep
the zero margin unchanged.

The margin defaults to `2cm` and is overridable with the `eform_rtl_letter_page_margin` property.
The value is written into a stylesheet, so it is validated as a CSS length on both sides and an
unparseable override falls back to the default rather than being emitted.

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

Regression coverage: `AddEForm2ActionPrintAliasUnitTest` (server alias, mapped results),
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

# 4. Rewire the attachment routes to the gated Struts actions
mysql ... < database/mysql/updates/update-2026-06-29-rtl-attachment-route-fix.sql

# 5. Add the hidden provider fields the signature stamp is chosen from
mysql ... < database/mysql/updates/update-2026-09-20-rtl-provider-stamp-fields.sql
```

Steps 4 and 5 must run **after** step 2, which replaces `form_html` wholesale. `carlos-ctl`'s O19
importer applies the same chain from its packaged copies (`o19roles.RTL_FIXUP_SCRIPTS`), deciding
per install which ones are still due by inspecting the live `form_html` — a row that already
carries the 2026.3.0 marker but is missing `id="user_ohip_no"` gets step 5 alone.

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

## Signature stamps

The **Stamp** and **Closing Salutation** sidebar buttons both resolve a signature image through
`pickStamp()` in `editControl2.js`. It prefers the provider's own stored signature —
`consult_sig_<provider_no>.png` in the eForm images directory, the same file the consultation
module and the Visual eForm Editor stamp with — and falls back to the legacy sources only when no
such file exists.

Which provider signs is a delegation rule, mirroring `sign()` in `visualEformEditor.jsp`:

| `user_ohip_no` | Signed by | Rationale |
|---|---|---|
| `> 1000` | the logged-in user (`user_id`) | a billing practitioner (MD / NP / RMW) signs their own letters |
| otherwise | the patient's MRP (`doctor_provider_no`) | a non-billing account — resident, nurse, clerical, or a room/resource pseudo-provider that exists only to hold a schedule — is writing under the MRP's direction |

> The Visual eForm Editor uses a threshold of `100` for the same decision. The RTL uses `1000`, which
> leaves a wider band of numbers available for non-billing providers who need a schedule.

The three values arrive as hidden inputs the eForm framework populates server-side from their
`oscarDB=` attributes (added by `update-2026-09-20-rtl-provider-stamp-fields.sql`):

| Input | AP key |
|---|---|
| `user_id` | `current_user_id` |
| `user_ohip_no` | `current_user_ohip_no` |
| `doctor_provider_no` | `doctor_provider_no` |

The same AP keys are listed on the `stamp` and `_ClosingSalutation` cache mappings, so an install
whose stored `form_html` predates those inputs — a clinic that customized the Rich Text Letter row,
or one that has not run the migration — still resolves them over APCache.

**Fallbacks.** `Start()` probes the per-provider file once at load. If it is absent, `pickStamp()`
falls back to `stamps.js` (a clinic-authored `ImgArray` of `"doctor|SignatureFile.png"` pairs in
the eForm images directory, matched against `current_user` first and then the MRP) and finally to a
single shared `stamp.png`. Those two were the *only* sources before 2026.09, which is why a
multi-provider clinic without a `stamps.js` signed every letter with the same image.

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

The letter templates (`blank.rtl` and the three starter templates) are *seeded* this way: a clinic
is expected to edit them, and an edit must survive the next redeploy. `editControl2.js` is
*managed* instead — it is compared against the shipped bytes on every startup and replaced when it
differs, because a stale copy of the editor engine is a defect that would otherwise follow the
install forever. `MANAGED_ASSETS` in the deployer is the list; adding a filename to it declares
that clinic edits to that file are unsupported.

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
src/main/webapp/WEB-INF/eform-assets/clinic_letter.rtl
src/main/webapp/WEB-INF/eform-assets/consultation_letter.rtl
src/main/webapp/WEB-INF/eform-assets/patient_letter.rtl
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
database/mysql/updates/update-2026-06-29-rtl-attachment-route-fix.sql  (gated attachment routes)
database/mysql/updates/update-2026-09-20-rtl-provider-stamp-fields.sql (provider fields for the signature stamp)
database/mysql/migration/common/V1.0.24__rename_placeholder_demo_clinic.sql  (##letterhead## clinic name)
```

### Release
```
release/editControl2.js                             (Debian package copy)
```
