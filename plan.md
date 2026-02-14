# Bootstrap 5.0.2 → 5.3.x Upgrade Plan for CARLOS EMR

## Executive Summary

CARLOS EMR currently uses **Bootstrap 5.0.2** loaded from local library files at
`/library/bootstrap/5.0.2/`. The CLAUDE.md documentation incorrectly states Bootstrap 5.3.0
loaded from CDN. This plan covers upgrading to **Bootstrap 5.3.3** (latest stable 5.3.x)
and fixing all breaking changes. Bootstrap versions below 5.0 (3.x, 2.x) are **out of scope**.

**Scope**: 10 JSP files + 2 eForm injectors + 2 JSP fragments + 1 JS file + 1 custom CSS file

**Risk Level**: Low-Medium. Most Bootstrap 5.0.2 features are fully backward-compatible
with 5.3.x. Only 3 classes were deprecated/removed. However, there are pre-existing bugs
(Bootstrap 3/4 classes used in Bootstrap 5 pages) that should be fixed during this upgrade.

---

## Current State Inventory

### Files Loading Bootstrap 5.0.2

| File | CSS | JS | Notes |
|------|-----|-----|-------|
| `admin/configureEmail.jsp` | `bootstrap.min.css` | (none) | CSS only |
| `admin/manageEmails.jsp` | `bootstrap.min.css` | `bootstrap.bundle.js` | Full |
| `email/emailCompose.jsp` | `bootstrap.min.css` | `bootstrap.min.js` | **Missing Popper** |
| `eform/efmformadd_data.jsp` | `bootstrap.css` | `bootstrap.bundle.js` | Injected into eForm |
| `eform/efmshowform_data.jsp` | `bootstrap.css` | `bootstrap.bundle.js` | **Duplicate JS load** |
| `mfa/mfa_handler.jsp` | `bootstrap.min.css` | `bootstrap.min.js` | **Missing Popper** |
| `provider/setHl7LabResultPrefs.jsp` | `bootstrap.min.css` | `bootstrap.bundle.js` | Full |
| `scratch/index.jsp` | `bootstrap.min.css` | (none) | CSS only |
| `scratch/version.jsp` | `bootstrap.min.css` | (none) | CSS only |
| `web/inboxhub/Inboxhub.jsp` | `bootstrap.min.css` | `bootstrap.bundle.js` | Full |

### Fragment/Support Files (inherit Bootstrap from parent)

| File | Role |
|------|------|
| `web/inboxhub/InboxhubForm.jsp` | Included by Inboxhub.jsp |
| `admin/emailStatusResults.jspf` | AJAX-loaded by manageEmails.jsp |
| `mfa/mfa_otp_handler.jsp` | Fragment loaded inside mfa_handler.jsp |
| `js/oscar-alert.js` | Used by eForm pages |
| `web/css/Inboxhub.css` | Custom styles for InboxHub |

### Out of Scope

- `mcedt/head-includes.jsp` and `mcedt/mailbox/head-includes.jsp` — load `/css/bootstrap.min.css` (Bootstrap 2.x/3.x, not 5.x)
- All 46+ JSP files using Bootstrap 3.0.0 from `/library/bootstrap/3.0.0/`
- `form/eCARES/` files — use Bootstrap 3.0.0
- Bootstrap plugins used only with Bootstrap 3 pages (datepicker, multiselect, etc.)

---

## Phase 1: Drop-in Library File Replacement

### Step 1.1: Download Bootstrap 5.3.3

Download the compiled Bootstrap 5.3.3 distribution and place files at:
`src/main/webapp/library/bootstrap/5.3.3/`

Files needed:
```
css/
  bootstrap.css, bootstrap.css.map
  bootstrap.min.css, bootstrap.min.css.map
  bootstrap.rtl.css, bootstrap.rtl.css.map
  bootstrap.rtl.min.css, bootstrap.rtl.min.css.map
  bootstrap-grid.css, bootstrap-grid.min.css (+ maps, RTL)
  bootstrap-reboot.css, bootstrap-reboot.min.css (+ maps, RTL)
  bootstrap-utilities.css, bootstrap-utilities.min.css (+ maps, RTL)
js/
  bootstrap.js, bootstrap.js.map
  bootstrap.min.js, bootstrap.min.js.map
  bootstrap.bundle.js, bootstrap.bundle.js.map
  bootstrap.bundle.min.js, bootstrap.bundle.min.js.map
  bootstrap.esm.js, bootstrap.esm.min.js (+ maps)
```

### Step 1.2: Update Path References

Update all `5.0.2` references to `5.3.3` in these files:

| File | Line(s) | Change |
|------|---------|--------|
| `admin/configureEmail.jsp` | ~23 | `5.0.2` → `5.3.3` in CSS href |
| `admin/manageEmails.jsp` | ~25, ~33 | `5.0.2` → `5.3.3` in CSS href and JS src |
| `email/emailCompose.jsp` | ~15, ~22 | `5.0.2` → `5.3.3` in CSS href and JS src |
| `eform/efmformadd_data.jsp` | ~141, ~142 | `5.0.2` → `5.3.3` in Java addCSS/addHeadJavascript |
| `eform/efmshowform_data.jsp` | ~123, ~128, ~129 | `5.0.2` → `5.3.3` in Java addCSS/addHeadJavascript |
| `mfa/mfa_handler.jsp` | ~49, ~50 | `5.0.2` → `5.3.3` in CSS href and JS src |
| `provider/setHl7LabResultPrefs.jsp` | ~18, ~20 | `5.0.2` → `5.3.3` in CSS href and JS src |
| `scratch/index.jsp` | ~316 | `5.0.2` → `5.3.3` in CSS href |
| `scratch/version.jsp` | ~66-67 | `5.0.2` → `5.3.3` in CSS href |
| `web/inboxhub/Inboxhub.jsp` | ~37, ~45 | `5.0.2` → `5.3.3` in CSS href and JS src |

### Step 1.3: Standardize JS Loading

Fix inconsistent JS loading during the path update:

1. **`emailCompose.jsp`** (line ~22): Change `bootstrap.min.js` → `bootstrap.bundle.min.js`
   - Reason: `bootstrap.min.js` lacks Popper.js, which is required for tooltips/popovers

2. **`mfa/mfa_handler.jsp`** (line ~50): Change `bootstrap.min.js` → `bootstrap.bundle.min.js`
   - Reason: Same Popper.js issue

3. **`efmshowform_data.jsp`** (lines ~123 and ~129): Remove the duplicate `bootstrap.bundle.js` injection at line ~123
   - Reason: Double-loading causes event handler conflicts

---

## Phase 2: Fix Breaking Changes (5.0.2 → 5.3.x)

These are classes that were **deprecated or removed** between 5.0.2 and 5.3.x.

### 2.1: Remove `.navbar-light` (deprecated 5.2, removed 5.3)

**What changed**: In Bootstrap 5.3, the light navbar appearance is the default. The `.navbar-light` class was removed. Simply removing the class produces identical visual results.

| File | Line | Current | Fix |
|------|------|---------|-----|
| `web/inboxhub/Inboxhub.jsp` | 62 | `class="navbar navbar-light d-flex..."` | Remove `navbar-light` → `class="navbar d-flex..."` |

### 2.2: Replace `.text-muted` (deprecated 5.3, removal planned for 6.0)

**What changed**: `.text-muted` is deprecated in favor of `.text-body-secondary`. The class still works in 5.3 but will be removed in Bootstrap 6.

| File | Line | Context |
|------|------|---------|
| `email/emailCompose.jsp` | 505 | `class="text-muted attachmentSize"` |
| `provider/setHl7LabResultPrefs.jsp` | 38 | `class="form-text text-muted"` |
| `provider/setHl7LabResultPrefs.jsp` | 47 | `class="form-text text-muted"` |
| `mfa/mfa_otp_handler.jsp` | 60 | `class="text-muted"` |

**Fix**: Replace `text-muted` with `text-body-secondary` in all 4 occurrences.

**Note**: `text-muted` still functions in 5.3 (just deprecated), so this is forward-looking cleanup. If you prefer minimal changes, this can be deferred.

### 2.3: Replace `.btn-close-white` (deprecated 5.3, removal planned for 6.0)

**What changed**: `.btn-close-white` is deprecated. The recommended approach is to add `data-bs-theme="dark"` to a parent element instead.

| File | Line | Current | Fix |
|------|------|---------|-----|
| `web/inboxhub/InboxhubForm.jsp` | 387 | `class="btn-close btn-close-white me-2 m-auto"` | Remove `btn-close-white`, add `data-bs-theme="dark"` to the parent toast div |

**Fix**: On line ~382, add `data-bs-theme="dark"` to the toast container:
```html
<!-- BEFORE -->
<div id="ajaxErrorToast" class="toast align-items-center text-white bg-danger border-0" ...>

<!-- AFTER -->
<div id="ajaxErrorToast" class="toast align-items-center text-white bg-danger border-0" data-bs-theme="dark" ...>
```
Then remove `btn-close-white` from line ~387.

---

## Phase 3: Fix Pre-Existing Bugs (Already Broken in 5.0.2)

These are Bootstrap 3/4 classes and attributes used in pages that load Bootstrap 5. They were
already non-functional before this upgrade but should be fixed as part of the effort.

### 3.1: Fix `data-toggle` → `data-bs-toggle` (Bootstrap 4 → 5 syntax)

**Problem**: `data-toggle="tooltip"` and `data-placement` are Bootstrap 4 attributes. Bootstrap 5 requires the `data-bs-` prefix. These tooltips are currently non-functional.

| File | Line(s) | Attribute | Fix |
|------|---------|-----------|-----|
| `email/emailCompose.jsp` | ~381 | `data-toggle="tooltip"` | → `data-bs-toggle="tooltip"` |
| `email/emailCompose.jsp` | ~381 | `data-placement="auto right"` | → `data-bs-placement="right"` |
| `email/emailCompose.jsp` | ~396 | `data-toggle="tooltip"` | → `data-bs-toggle="tooltip"` |
| `email/emailCompose.jsp` | ~396 | `data-placement="auto right"` | → `data-bs-placement="right"` |
| `email/emailCompose.jsp` | ~418 | `data-toggle="tooltip"` | → `data-bs-toggle="tooltip"` |
| `email/emailCompose.jsp` | ~418 | `data-placement="auto right"` | → `data-bs-placement="right"` |
| `email/emailCompose.jsp` | ~431 | `data-toggle="tooltip"` | → `data-bs-toggle="tooltip"` |
| `email/emailCompose.jsp` | ~431 | `data-placement="auto right"` | → `data-bs-placement="right"` |

**Note**: After fixing the data attributes, tooltips also require Popper.js. Step 1.3 changes
`bootstrap.min.js` → `bootstrap.bundle.min.js` which includes Popper.js. Additionally,
Bootstrap 5 tooltips must be explicitly initialized via JavaScript:
```javascript
var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
tooltipTriggerList.map(function (el) { return new bootstrap.Tooltip(el) })
```

### 3.2: Fix `pull-right` → `float-end` (Bootstrap 3 → 5 class)

**Problem**: `pull-right` is a Bootstrap 3 class that does nothing in Bootstrap 5.

| File | Line(s) | Fix |
|------|---------|-----|
| `email/emailCompose.jsp` | ~547 | `pull-right` → `float-end` |
| `email/emailCompose.jsp` | ~551 | `pull-right` → `float-end` |
| `email/emailCompose.jsp` | ~581 | `pull-right` → `float-end` |

### 3.3: Fix `form-inline` (Removed in Bootstrap 5)

**Problem**: `form-inline` was removed in Bootstrap 5. Use grid/flex utilities instead.

| File | Line | Fix |
|------|------|-----|
| `email/emailCompose.jsp` | ~192 | Remove `form-inline` class. If inline layout is needed, use `d-flex` or `row`/`col` instead |

### 3.4: Fix `table-condensed` → `table-sm` (Bootstrap 3 → 5 class)

**Problem**: `table-condensed` is a Bootstrap 3 class. Bootstrap 5 uses `table-sm`.

| File | Line | Fix |
|------|------|----|
| `scratch/index.jsp` | ~407 | `table-condensed` → `table-sm` |
| `scratch/version.jsp` | ~175 | `table-condensed` → `table-sm` |

### 3.5: Fix `btn-check-input` → `form-check-input` (Invalid class)

**Problem**: `btn-check-input` is not a valid Bootstrap class. The correct class for checkbox/radio
inputs in forms is `form-check-input`.

| File | Lines | Fix |
|------|-------|-----|
| `web/inboxhub/InboxhubForm.jsp` | ~74, ~79, ~84, ~103, ~108, ~113, ~161, ~165, ~171, ~184, ~189, ~194, ~199, ~212, ~217, ~222 | `btn-check-input` → `form-check-input` |

---

## Phase 4: DataTables Bootstrap 5 Compatibility

### 4.1: Verify DataTables Integration

The current DataTables 1.13.4 Bootstrap 5 integration files:
- `library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.css`
- `library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css`
- `library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js`

**Expected impact**: DataTables Bootstrap 5 integration is designed to work across all
Bootstrap 5.x versions. No changes should be required. However, visual regression testing
should be performed on the InboxHub page (the primary user of DataTables + Bootstrap 5).

### 4.2: Optional - Update DataTables Styling for CSS Variables

DataTables 1.13.4 was released before Bootstrap 5.3's CSS variable system was finalized.
If dark mode support is desired in the future, consider updating the DataTables Bootstrap 5
integration files to a newer version that uses `--bs-*` CSS custom properties.

---

## Phase 5: Custom CSS Review

### 5.1: Review `web/css/Inboxhub.css`

This file overrides several Bootstrap defaults. Review for compatibility:

| Line | CSS Rule | Status |
|------|----------|--------|
| 30-32 | `.d-inline-grid` override | **OK** - Bootstrap 5.3 still has this utility |
| 34-37 | `.form-check` override | **OK** - `.form-check` unchanged in 5.3 |
| 39-41 | `.show` override | **REVIEW** - May conflict with Bootstrap 5.3's `.show` class |
| 145-147 | `.accordion-body` padding override | **OK** - Accordion unchanged |
| 149-157 | `.bootstrap-datetimepicker-widget` | **OK** - Third-party, unrelated to core Bootstrap |

**Action**: The `.show { display: block; }` override on line 39-41 could cause issues with
Bootstrap 5.3's expanded use of `.show` on various components (tabs, collapses, offcanvas).
Test the InboxHub page and remove this override if Bootstrap 5.3 handles it correctly.

### 5.2: `oscar-alert.js` Review

This file creates Bootstrap alerts programmatically. Review:

| Feature | Status |
|---------|--------|
| `alert alert-${alertType}` classes | **OK** - Unchanged in 5.3 |
| `alert-dismissible` class | **OK** - Unchanged |
| `btn-close` class | **OK** - Unchanged |
| `data-bs-dismiss="alert"` | **OK** - Unchanged |
| `close.bs.alert` event | **OK** - Unchanged |
| `fade` / `show` class toggling | **OK** - Unchanged |

**No changes required** for `oscar-alert.js`.

---

## Phase 6: Documentation Updates

### 6.1: Update CLAUDE.md

Fix the inaccurate documentation:

**Current** (line ~404):
```
Bootstrap 5.3.0: Modern UI framework loaded from CDN for responsive design
```

**Updated**:
```
Bootstrap 5.3.3: Modern UI framework loaded from local library files for responsive design
```

### 6.2: Update JSP Refactoring Guide (if exists)

The `docs/JSP-REFACTORING-GUIDE.md` references Bootstrap 5.3.0 via CDN. Update to reference
local files at `/library/bootstrap/5.3.3/` for consistency.

---

## Phase 7: Testing Plan

### 7.1: Visual Regression Testing (Manual)

Each page that loads Bootstrap 5 must be visually tested:

| Page | URL Pattern | Key Things to Test |
|------|------------|-------------------|
| Configure Email | `/admin/configureEmail.jsp` | Card layout, form fields, spacing |
| Manage Emails | `/admin/manageEmails.jsp` | Forms, datepicker, popovers, table, buttons |
| Email Compose | `/email/emailCompose.jsp` | Modal, accordion, forms, tooltips, buttons, alerts |
| eForm Add | `/eform/efmformadd_data.jsp` | eForm toolbar, oscar-alert |
| eForm Show | `/eform/efmshowform_data.jsp` | eForm toolbar, oscar-alert |
| MFA Handler | `/mfa/mfa_handler.jsp` | Card layout, form, alert |
| MFA OTP | `/mfa/mfa_otp_handler.jsp` | Form inside card, text styling |
| HL7 Lab Prefs | `/provider/setHl7LabResultPrefs.jsp` | Card, form switches, alert |
| Scratch Pad | `/scratch/index.jsp` | Table, buttons, select |
| Scratch Version | `/scratch/version.jsp` | Table, buttons, alerts |
| InboxHub | `/web/inboxhub/Inboxhub.jsp` | Navbar, DataTable, accordions, toasts, popovers |

### 7.2: Functional Testing

| Feature | Files | Test Action |
|---------|-------|-------------|
| Modal open/close | emailCompose.jsp | Click to open error modal, verify dismiss |
| Accordion expand/collapse | emailCompose.jsp, InboxhubForm.jsp | Click accordion headers |
| Toast show/dismiss | InboxhubForm.jsp | Trigger error, verify toast appears and dismisses |
| Popover display | emailStatusResults.jspf | Hover over error status, verify popover content |
| Tooltip display | emailCompose.jsp | Hover over info icons (after fixing data attributes) |
| Form switches | setHl7LabResultPrefs.jsp | Toggle switches, verify visual state |
| Alert dismiss | oscar-alert.js (eForm pages) | Trigger alert, click close button |
| DataTables | Inboxhub.jsp | Sort, filter, paginate table |

### 7.3: UI Test Suite

Run the existing UI test suite to catch regressions:
```bash
# Run relevant UI tests
/ui-tests:test1  # Smoke test - login, search
/ui-tests:test5  # Ticklers & messaging (uses InboxHub)
/ui-tests:test8  # Lab results (uses HL7 prefs page)
```

---

## Implementation Order

1. **Phase 1** (Step 1.1-1.3): Library files + path updates + JS standardization
2. **Phase 2** (Step 2.1-2.3): Fix 5.0→5.3 breaking changes (navbar-light, text-muted, btn-close-white)
3. **Phase 3** (Step 3.1-3.5): Fix pre-existing bugs (data-toggle, pull-right, form-inline, table-condensed, btn-check-input)
4. **Phase 4**: Verify DataTables compatibility
5. **Phase 5**: Review custom CSS
6. **Phase 6**: Update documentation
7. **Phase 7**: Testing

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Visual regressions from CSS variable changes | Low | Medium | Visual testing on all 10+ pages |
| DataTables styling mismatch | Low | Low | DataTables 1.13.4 supports BS 5.0-5.3 |
| eForm content using BS 5.0.2 specific behavior | Very Low | Low | eForm HTML is user-generated; BS 5.3 is backward-compatible |
| `.show` CSS override in Inboxhub.css conflicts | Medium | Medium | Test InboxHub collapse/show behavior |
| Third-party datepicker incompatibility | Low | Low | Datepicker is jQuery-based, independent of Bootstrap core |

---

## Files Changed Summary

| Category | Files Modified | Files Added |
|----------|---------------|-------------|
| Library files | 0 | ~44 (new 5.3.3 directory) |
| JSP path updates | 10 | 0 |
| Breaking change fixes | 4 (Inboxhub.jsp, InboxhubForm.jsp, emailCompose.jsp, setHl7LabResultPrefs.jsp, mfa_otp_handler.jsp) | 0 |
| Pre-existing bug fixes | 4 (emailCompose.jsp, scratch/index.jsp, scratch/version.jsp, InboxhubForm.jsp) | 0 |
| Documentation | 1 (CLAUDE.md) | 0 |
| **Total unique files** | **~12 JSP/JS files + docs** | **~44 library files** |

**Note**: The old `/library/bootstrap/5.0.2/` directory can be retained temporarily for
rollback purposes, then removed in a follow-up cleanup once the upgrade is verified stable.
