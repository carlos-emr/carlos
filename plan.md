# Plan: Fix Rich Text Letter eForm for Current Framework

## Context

The RTL eForm v2.1 (stored as HTML in the `eform` database table) relies on libraries and assets that are missing or incompatible after the project's JavaScript/CSS framework updates. The goal is to restore full functionality using the libraries already available in the codebase (jQuery 3.7.1, jQuery UI 1.14.2, Font Awesome 6.7.2, Bootstrap 5.3.3) plus any secure third-party additions where needed.

## Available Infrastructure (already in codebase)

- **jQuery 3.7.1** at `library/jquery/jquery-3.7.1.min.js` — injected by `efmshowform_data.jsp`
- **jQuery UI 1.14.2** at `library/jquery/jquery-ui-1.14.2.min.js` — injected by `efmshowform_data.jsp`
- **Font Awesome 6.7.2** at `css/fontawesome-all.min.css` with webfonts (includes `fa-v4compatibility`)
- **Bootstrap 5.3.3** — injected by `efmshowform_data.jsp`
- **APCache.js, imageControl.js, faxControl.js, signatureControl.jsp, printControl.js** — all exist at `library/eforms/`
- `${oscar_javascript_path}` resolves to `/carlos/library/`
- `displayImage.do` serves files from the eForm images directory

## Changes Required

### 1. Update `editControl2.js` — Migrate FA3 icons to FA6 classes

**File**: `release/editControl2.js` → will also be deployed to eForm images dir

The toolbar buttons use Font Awesome 3.x `icon-*` classes (e.g., `icon-bold`, `icon-italic`). FA6 uses `fa-solid fa-*` classes. There are ~30 icon references to update:

| FA3 class | FA6 class |
|-----------|-----------|
| `icon-bold` | `fa-solid fa-bold` |
| `icon-italic` | `fa-solid fa-italic` |
| `icon-underline` | `fa-solid fa-underline` |
| `icon-strikethrough` | `fa-solid fa-strikethrough` |
| `icon-superscript` | `fa-solid fa-superscript` |
| `icon-subscript` | `fa-solid fa-subscript` |
| `icon-align-left` | `fa-solid fa-align-left` |
| `icon-align-center` | `fa-solid fa-align-center` |
| `icon-align-justify` | `fa-solid fa-align-justify` |
| `icon-align-right` | `fa-solid fa-align-right` |
| `icon-list-ul` | `fa-solid fa-list-ul` |
| `icon-list-ol` | `fa-solid fa-list-ol` |
| `icon-ellipsis-horizontal` | `fa-solid fa-ellipsis` |
| `icon-undo` | `fa-solid fa-rotate-left` |
| `icon-repeat` | `fa-solid fa-rotate-right` |
| `icon-indent-right` | `fa-solid fa-indent` |
| `icon-indent-left` | `fa-solid fa-outdent` |
| `icon-tablet` | `fa-solid fa-expand` |
| `icon-eraser` | `fa-solid fa-eraser` |
| `icon-table` | `fa-solid fa-table` |
| `icon-tint` | `fa-solid fa-droplet` |
| `icon-check-empty` | `fa-regular fa-square` |
| `icon-picture` | `fa-solid fa-image` |
| `icon-link` | `fa-solid fa-link` |
| `icon-paper-clip` | `fa-solid fa-paperclip` |
| `icon-file` | `fa-solid fa-file` |
| `icon-time` | `fa-regular fa-clock` |
| `icon-calendar` | `fa-regular fa-calendar` |
| `icon-question-sign` | `fa-solid fa-circle-question` |
| `icon-edit` | `fa-solid fa-pen-to-square` |
| `icon-save` | `fa-solid fa-floppy-disk` |
| `icon-h-sign` | `fa-solid fa-heading` |
| `icon-cut` | `fa-solid fa-scissors` |
| `icon-trash` | `fa-solid fa-trash` |
| `icon-copy` | `fa-solid fa-copy` |

Also update the `.editControlButton i` CSS selector if needed — FA6 uses `<i>` tags with multiple classes so the existing selector should still work.

### 2. Update RTL eForm HTML (SQL migration) — Fix broken references

**File**: New `database/mysql/updates/update-2026-03-22-rtl-v22-modernize.sql`

Update the `form_html` column in the `eform` table for the Rich Text Letter. Changes:

**a) Remove CDN jQuery 1.12.4** — The host page (`efmshowform_data.jsp`) already injects jQuery 3.7.1. Loading 1.12.4 from CDN causes version conflicts and requires internet access. Remove:
```html
<script src="https://code.jquery.com/jquery-1.12.4.min.js" ...></script>
```

**b) Remove jQuery UI 1.8.18 reference** — jQuery UI 1.14.2 is already injected by the host page. Remove:
```html
<script src="../js/jquery-ui-1.8.18.custom.min.js" ...></script>
```

**c) Replace `font-awesome.min.css`** — Use the existing FA6 CSS. Change:
```html
<link rel="stylesheet" href="../css/font-awesome.min.css">
```
to:
```html
<link rel="stylesheet" href="../css/fontawesome-all.min.css">
```

**d) Remove jQuery UI colorPicker** — The old `jquery.ui.colorPicker` depended on jQuery UI 1.8. The `editControl2.js` color picker uses `prompt()` dialogs (`exprompt('foreColor','Text Colour?[red]')`) which work without any colorPicker widget. Remove these two lines:
```html
<link href="../css/jquery.ui.colorPicker.css" rel="stylesheet" type="text/css" />
<script src="../js/jquery.ui.colorPicker.min.js" type="text/javascript"></script>
```

**e) Fix `saveRTL()` escaping bug** — The replace chain is broken (each line reassigns from `theRTL` instead of chaining from `myNewString`). Fix:
```javascript
function saveRTL() {
    needToConfirm=false;
    var theRTL=editControlContents('edit');
    var myNewString = theRTL.replace(/&/g, '&amp;');
    myNewString = myNewString.replace(/"/g, '&quot;');
    myNewString = myNewString.replace(/</g, '&lt;');
    myNewString = myNewString.replace(/>/g, '&gt;');
    myNewString = myNewString.replace(/'/g, '&#39;');
    document.getElementById('Letter').value=myNewString;
}
```
Key fixes: chain from `myNewString` (not `theRTL`), add `&` escaping first.

**f) Fix SQL injection in `fpreventions()`** — Replace raw SQL passed to `RptByExample.do` with a safe server-side call. Change to use a new action endpoint that takes `demographicNo` as a validated parameter:
```javascript
function fpreventions(){
    $.ajax({
        url: "../eform/rtlPreventions.do",
        data: { demographic_no: demographicNo },
        type: 'get',
        success: function(data) {
            doHtml("<font size='3'><b>Preventions:</b></font><br>" + data);
        }
    });
}
```

**g) Update version marker** — Change subject to `Rich Text Letter Generator v2.2` and update the HTML version comment.

### 3. Create `efmformrtl_templates.jsp`

**File**: `src/main/webapp/eform/efmformrtl_templates.jsp`

The `Start()` function in editControl2.js fetches this file via AJAX to populate the template dropdown. Create a JSP that queries available `.rtl` template files from the eForm images directory and returns `<option>` elements. This file was present in older versions but got lost.

### 4. Create `RtlPreventions2Action.java` — Server-side prevention query

**File**: `src/main/java/io/github/carlos_emr/carlos/eform/actions/RtlPreventions2Action.java`

A Struts2 action following the 2Action pattern that:
- Requires `_eform` read privilege via `SecurityInfoManager.hasPrivilege()`
- Takes `demographic_no` as a validated integer parameter
- Queries preventions via the existing `PreventionDao` or a parameterized query
- Returns OWASP-encoded HTML-formatted prevention data
- Registered in `struts.xml`

### 5. Create `attachEform.jsp` and `displayAttachedFiles.jsp` — Missing attachment UI

**Files**:
- `src/main/webapp/eform/attachEform.jsp` — popup for attaching documents/labs/HRM/eForms to a letter
- `src/main/webapp/eform/displayAttachedFiles.jsp` — AJAX endpoint that returns attached file list HTML

These are called by `popupEformUpload()` and `fetchAttached()` in the RTL HTML. They never existed in this codebase (upstream OSCAR files that weren't carried forward). Without them:
- The `[attach]` toolbar button opens a blank popup
- The "Attached Files" sidebar panel is always empty

Create minimal JSPs that:
- `attachEform.jsp`: Query EForm attachments for the given `fdid`/`demographic_no`, display a file selection UI with document/lab/HRM/eform categories, allow attaching via existing eForm attachment mechanisms
- `displayAttachedFiles.jsp`: Return HTML fragment listing attached files with the color-coded legend classes (`.doc`, `.lab`, `.hrm`, `.eform`)

Both require security checks and OWASP encoding.

### 6. Asset deployment mechanism — Spring startup bean

**File**: `src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java`

A Spring `@Component` that runs on application startup (`@PostConstruct` or `InitializingBean`) to ensure critical eForm assets exist in the eForm images directory. Behavior:

- Reads from a bundled resource directory (`src/main/webapp/WEB-INF/eform-assets/`)
- Copies each file to the eForm images directory **only if it doesn't already exist** (never overwrites)
- Logs which files were deployed vs. which already existed
- Assets to bundle: `editControl2.js`, `blank.rtl`, `editor_help.html`

The `stamps.js` file is intentionally NOT auto-deployed — it's clinic-specific configuration (doctor signature mappings) that admins create themselves.

**Source directory**: `src/main/webapp/WEB-INF/eform-assets/`
**Files to include**:
- `editControl2.js` (copied from `release/editControl2.js` after FA6 icon migration)
- `blank.rtl` (minimal RTL template)
- `editor_help.html` (help page referenced by the help button)

Register in `applicationContext.xml` or rely on component scanning.

### 7. Copy updated `editControl2.js` to bundled assets

After applying the FA6 icon migration (step 1), place the updated `release/editControl2.js` into `src/main/webapp/WEB-INF/eform-assets/editControl2.js`. This ensures the deployer from step 6 has the correct version.

Also keep `release/editControl2.js` updated as the canonical source.

### 8. Ensure `blank.rtl` and `editor_help.html` exist

Neither file exists anywhere in the codebase. Both are loaded via `displayImage.do?imagefile=`.

**`blank.rtl`**: Default letter template loaded into the editor iframe. `editControl2.js` has a fallback (line 334) — if `blank.rtl` isn't in the template dropdown, it creates an inline blank document via a `data:` URL. So this is a **soft dependency**, but providing a proper `blank.rtl` gives clinics a better starting template (with proper print CSS, font defaults, `designMode` on, `contenteditable`).

**`editor_help.html`**: Help page opened by the `[help]` toolbar button. Without it, clicking Help opens a blank popup. Create a simple HTML help page documenting the toolbar buttons and keyboard shortcuts.

## Execution Order

1. **Step 1** — Update FA3 → FA6 icons in `editControl2.js`
2. **Step 2e** — Fix `saveRTL()` bug in the eForm HTML
3. **Step 4** — Create `RtlPreventions2Action.java` (needed before step 2f)
4. **Step 3** — Create `efmformrtl_templates.jsp`
5. **Step 5** — Create `attachEform.jsp` and `displayAttachedFiles.jsp`
6. **Step 6** — Create `EFormAssetDeployer.java` + bundled assets directory
7. **Step 7/8** — Copy updated `editControl2.js` and `blank.rtl` to bundled assets
8. **Step 2** — Create SQL migration with all eForm HTML changes (2a-g)
9. Register new actions in `struts.xml`
10. Build and test end-to-end

## Files Created/Modified Summary

| File | Action |
|------|--------|
| `release/editControl2.js` | **Modify** — FA3→FA6 icon classes |
| `src/main/webapp/WEB-INF/eform-assets/editControl2.js` | **Create** — bundled copy |
| `src/main/webapp/WEB-INF/eform-assets/blank.rtl` | **Create** — default template |
| `src/main/webapp/WEB-INF/eform-assets/editor_help.html` | **Create** — help page |
| `src/main/webapp/eform/efmformrtl_templates.jsp` | **Create** — template dropdown |
| `src/main/webapp/eform/attachEform.jsp` | **Create** — eForm attachment popup |
| `src/main/webapp/eform/displayAttachedFiles.jsp` | **Create** — attached files list |
| `src/main/java/.../eform/actions/RtlPreventions2Action.java` | **Create** — safe prevention endpoint |
| `src/main/java/.../eform/EFormAssetDeployer.java` | **Create** — startup asset deployer |
| `src/main/webapp/WEB-INF/classes/struts.xml` | **Modify** — register new actions |
| `database/mysql/updates/update-2026-03-22-rtl-v22-modernize.sql` | **Create** — eForm HTML update |

## What stays the same

- `APCache.js`, `imageControl.js`, `faxControl.js`, `signatureControl.jsp`, `printControl.js` — all work as-is
- `efmshowform_data.jsp` — no changes needed (already injects jQuery 3.7.1, jQuery UI 1.14.2, Bootstrap 5.3.3)
- The WYSIWYG `designMode`/`execCommand` approach — deprecated but still functional in all current browsers; a full editor replacement (TinyMCE/CKEditor) would be a separate future initiative
- `stamps.js` — clinic-specific, not auto-deployed. The RTL HTML initializes `ImgArray = []` as a global, and `editControl2.js` gracefully falls back to `stamp.png` when the array is empty. If `stamps.js` fails to load (404), the global `ImgArray` is already defined so no JS error occurs — just no per-doctor stamp mapping.
- Consultant search — already uses XHR to `searchProfessionalSpecialist.json` which maps to `ProfessionalSpecialist2Action.search()` in struts.xml. Works fine.
- `DisplayImage2Action.java` — already supports JS content type

## Additional Notes

- **FileSaver.js bundled in editControl2.js**: The `doExport()` function uses `saveAs()` from FileSaver.js which is embedded at the bottom of `editControl2.js`. This is MIT-licensed and works as-is — no changes needed.
- **`imageControl.js` dynamic load**: `editControl2.js` line 584 does a fallback load of `imageControl.js` from `../share/javascript/eforms/imageControl.js`. Since the host page injects it via `${oscar_javascript_path}eforms/imageControl.js` → `/carlos/library/eforms/imageControl.js`, this fallback path is wrong. The RTL HTML explicitly loads it via `${oscar_javascript_path}` so the fallback should never trigger, but if it does it would 404. Low risk — the primary load works.
- **IE-specific code paths**: editControl2.js has `isIE()` checks throughout. IE is dead. These code paths are harmless dead code — no need to remove them in this fix, but they could be cleaned up in a future pass.
- **Bootstrap 5.3.3 class conflicts**: The host page injects Bootstrap CSS, which could affect RTL's button/table styles. The RTL uses plain `<input type="button">` with a `.butn` class — should be fine since Bootstrap doesn't aggressively style input buttons unless they have Bootstrap classes. Worth testing visually.

## Risk Notes

- The `designMode`/`execCommand` APIs are deprecated. They work today but could be removed by browser vendors. A future phase could replace the custom editor with TinyMCE or similar.
- The `window.resizeTo()` call in `maximize()` is a no-op in modern browsers — harmless, just doesn't resize the window.
- The asset deployer's "never overwrite" policy means clinics that have customized `editControl2.js` keep their changes. To force an update, the admin would need to manually delete the file from the eForm images directory first.
- **jQuery double-loading**: `efmshowform_data.jsp` injects jQuery 3.7.1 into the `<head>` of ALL eForms. The RTL v2.1 HTML also loads jQuery 1.12.4 from CDN. Step 2a removes the CDN load to eliminate the version conflict. After removal, jQuery 3.7.1 (injected by the host) is the only jQuery — all RTL code must be compatible with it. jQuery 3.x dropped some APIs from 1.x (e.g., `.live()`, `.size()`, `.andSelf()`). Verified: the RTL HTML and editControl2.js use only `.ajax()`, `.each()`, `.html()`, `.val()`, `.attr()`, `.css()`, `.prop()`, `.ready()`, `.find()` — all compatible with jQuery 3.x.
- **Synchronous XHR**: `getMeasures()` in editControl2.js uses synchronous `XMLHttpRequest.open("GET", url, false)`. This is deprecated and browsers may warn in console, but it still works. The synchronous nature is intentional — it builds an array that's used immediately after the call. A future improvement could make this async.
- **SQL migration only updates existing RTL eForms**: The SQL `UPDATE` in step 2 only modifies eForms that already have "Rich Text Letter" in the `form_name`. New deployments would need the eForm to be inserted first (covered by the original `update-2012-07-12.sql`). The migration should have a WHERE clause broad enough to match both v2.1 and older versions.
