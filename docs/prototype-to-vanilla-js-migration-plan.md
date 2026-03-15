# Migration Plan: Prototype.js / Scriptaculous → Vanilla JS + Bootstrap 5.3

## Context

CARLOS EMR still loads **Prototype.js 1.5.1.1** (2007) and **Scriptaculous 1.7.1** (2007) across ~42 JSP files. These libraries conflict with jQuery (requiring `jQuery.noConflict()` workarounds), add 200KB+ of dead weight, and use APIs incompatible with modern browsers' security policies (e.g., `evalScripts`). The project already has jQuery 3.6.4 and Bootstrap 5.3.3 loaded via `global-head.jspf`, and some files already use modern vanilla JS patterns (fetch, addEventListener, querySelector). This migration removes the legacy libraries incrementally, replacing their functionality with vanilla JS, Bootstrap 5 components, and CSS transitions.

**Goal**: Remove Prototype.js, Scriptaculous, and all dependent code (LightWindow, legacy jQuery versions) — replacing with vanilla JS + Bootstrap 5.3 + CSS transitions. jQuery 3.6.4 remains as-is (separate future initiative).

---

## Phase 0: Foundation — Compatibility Shim + CSS Transitions

**Purpose**: Create a thin compatibility layer and reusable CSS utilities so subsequent phases can migrate incrementally without breaking pages.

### 0a. Create `prototype-compat.js` shim (Encounter module only)
**File**: `src/main/webapp/share/javascript/prototype-compat.js` (NEW)

This shim is **only** for the encounter/case management module (Phase 4) where 42KB+ of JS makes full rewrite impractical in one pass. All other modules (Phases 1-3) will be fully rewritten to vanilla JS directly — no shim.

Provides drop-in replacements for the most common Prototype APIs using vanilla JS:
```javascript
// Only define if Prototype's $ is not loaded
if (typeof window.$ === 'undefined' || !window.Prototype) {
    window.$ = function(id) { return document.getElementById(id); };
}
window.$F = function(id) { return document.getElementById(id)?.value ?? ''; };
window.$A = function(iterable) { return Array.from(iterable); };

// Element extensions (applied to HTMLElement.prototype)
if (!HTMLElement.prototype.update) {
    HTMLElement.prototype.update = function(html) { this.innerHTML = html; return this; };
}
if (!HTMLElement.prototype.hide) {
    HTMLElement.prototype.hide = function() { this.style.display = 'none'; return this; };
}
if (!HTMLElement.prototype.show) {
    HTMLElement.prototype.show = function() { this.style.display = ''; return this; };
}
// .insert(), .setStyle(), .getHeight(), etc.
```

This shim lets us swap `prototype.js` → `prototype-compat.js` in the encounter module without changing the calling code, then progressively remove shim usage in later cleanup passes.

### 0b. Create CSS transition utilities
**File**: `src/main/webapp/share/css/transitions.css` (NEW)

```css
.carlos-fade     { transition: opacity 0.3s ease; }
.carlos-fade-out { opacity: 0; }
.carlos-collapse { overflow: hidden; transition: max-height 0.3s ease; }
.carlos-collapsed { max-height: 0 !important; }
```

These replace Scriptaculous `Effect.Fade`, `Effect.Appear`, `Effect.BlindUp/Down` with CSS-only animations. Bootstrap's `.collapse` component can also be used where markup allows.

### 0c. Create `carlos-ajax.js` utility
**File**: `src/main/webapp/share/javascript/carlos-ajax.js` (NEW)

Wraps `fetch()` in an API similar to Ajax.Request/Ajax.Updater for easier migration:
```javascript
const CarlosAjax = {
    request(url, options = {}) {
        // Maps Prototype-style options to fetch()
        // Handles: method, parameters, onSuccess, onFailure, onComplete
    },
    updater(elementOrId, url, options = {}) {
        // fetch() + innerHTML update, with optional insertion position
    }
};
```

Pattern already proven in `documentManager/showDocument.js`.

**Critical: synchronous request support.** `CarlosAjax` must support a `synchronous: true` option that uses `XMLHttpRequest` (not `fetch()`, which has no synchronous mode). This preserves Prototype's `asynchronous: false` behavior used for:
- Note lock release on `beforeunload` (must complete before page unloads)
- Note lock checks that return values synchronously
- Autosave-on-close (must complete before window closes)
- Sequential drug operations in Rx module

Modern replacement strategy per pattern:
| Synchronous Pattern | Modern Replacement |
|---|---|
| Lock release on `beforeunload` | `navigator.sendBeacon(url, data)` — fire-and-forget, survives page unload |
| Synchronous return value (`NoteisLocked`) | Refactor to `async`/`await` + make callers async, or use `XMLHttpRequest` sync as interim |
| Autosave-on-close | `navigator.sendBeacon()` for the save, set `okToClose` optimistically |
| Sequential drug operations | `async`/`await` with `fetch()` — chain with `await` instead of blocking |

`navigator.sendBeacon()` is the correct modern replacement for "fire a request during page unload" — it's specifically designed for this and is supported by all modern browsers. For the synchronous-return-value pattern, the proper fix is to refactor callers to be async, but `XMLHttpRequest` sync can be used as an interim bridge in the shim.

### 0d. Add `global-head.jspf` includes
Add `transitions.css` and `carlos-ajax.js` to the global head so they're available everywhere.

### Verification
- Load any page → confirm no console errors
- Shim file loads without conflicting with existing Prototype.js (guarded by `if` checks)

---

## Phase 1: Low-Risk Modules — Full Rewrite (Admin, Forms, Document Manager)

**13 files, ~16 Prototype API calls total. Minimal UI impact. Direct vanilla JS rewrite, no shim.**

### 1a. Admin pages
| File | Changes |
|------|---------|
| `admin/displayDocumentDescriptionTemplate.jsp` | 7 `Ajax.Request` → `fetch()` or `CarlosAjax.request()` |
| `admin/securityupdatesecurity.jsp` | 1 `Ajax.Request` → `fetch()` |

- Remove `<script src="prototype.js">` includes
- Replace `Ajax.Request` with `fetch()` using `CarlosAjax.request()`
- No effects to replace

### 1b. Forms
| File | Changes |
|------|---------|
| `form/formRhImmuneGlobulin.jsp` | 2 `Ajax.Updater`/`Ajax.Request` → `CarlosAjax.updater()`/`fetch()` |
| `form/addRhInjection.jsp` | Remove commented-out `Form.Element.Observer` |

### 1c. Document Manager
| File | Changes |
|------|---------|
| `documentManager/uploadMultiDocument.jsp` | 1 `Ajax.Request` → `fetch()`, 1 `Effect.SlideUp` → CSS transition |
| `documentManager/incomingDocs.jsp` | 1 `Ajax.Request` → `fetch()`, `Ajax.Autocompleter` → vanilla autocomplete (pattern from `showDocument.js`) |
| `documentManager/MultiPageDocDisplay.jsp` | Remove `jQuery.noConflict()` if Prototype no longer loaded |

### Verification
- `/admin/` pages: test document description template CRUD, security update saves
- `/form/` pages: test Rh immunoglobulin form submission
- `/documentManager/`: test multi-document upload, incoming docs autocomplete search
- Run UI test suites: `/test1` (smoke), `/test6` (encounter/e-chart)

---

## Phase 2: Lab Module — Full Rewrite

**4 files, ~10 Prototype API calls. Medium complexity due to `evalScripts`. Direct vanilla JS rewrite, no shim.**

| File | Changes |
|------|---------|
| `lab/CumulativeLabValues.jsp` | 2 `Ajax.Updater` with `evalScripts` → `CarlosAjax.updater()` |
| `lab/CumulativeLabValues2.jsp` | 2 `Ajax.Updater` → `CarlosAjax.updater()` |
| `lab/CumulativeLabValues3.jsp` | 2 `Ajax.Updater` → `CarlosAjax.updater()` |
| `lab/labDisplayAjax.jsp` | 1 `Effect.BlindUp` → Bootstrap collapse or CSS transition |

**`evalScripts` handling**: The `CarlosAjax.updater()` utility will parse response HTML for `<script>` tags and execute them. This preserves existing behavior while centralizing the pattern. Long-term, server responses should avoid inline scripts.

### Verification
- Navigate to cumulative lab values view for a patient with lab history
- Verify expandable/collapsible lab sections work
- Run UI test: `/test8` (lab results)

---

## Phase 3: Prescription Module — Full Rewrite

**7 files, 40+ Prototype API calls. High complexity — includes LightWindow replacement. Direct vanilla JS rewrite, no shim.**

### 3a. Replace LightWindow with Bootstrap 5 Modals
**Files affected**: `oscarRx/SearchDrug3.jsp`, `oscarRx/ViewScript2.jsp`, `oscarRx/SelectPharmacy2.jsp`

LightWindow (`share/lightwindow/lightwindow.js`, 2000 lines) is built entirely on Prototype/Scriptaculous. Replace with Bootstrap 5 Modal:
- Create modal markup in JSP (or dynamic modal creation via JS)
- Replace `myLightWindow.activateWindow({href: url})` → `fetch(url)` + populate Bootstrap modal + `modal.show()`
- Replace `parent.myLightWindow.deactivate()` → `bootstrap.Modal.getInstance(el).hide()`

### 3b. Replace Effects
| Pattern | Replacement |
|---------|-------------|
| `Effect.BlindDown(el)` | `el.classList.remove('carlos-collapsed')` or Bootstrap `collapse.show()` |
| `Effect.BlindUp(el)` | `el.classList.add('carlos-collapsed')` or Bootstrap `collapse.hide()` |
| `Effect.Appear(el)` | `el.classList.remove('carlos-fade-out'); el.style.display = '';` |
| `Effect.Fade(el)` | `el.classList.add('carlos-fade-out'); setTimeout(() => el.style.display = 'none', 300);` |

### 3c. Replace Ajax calls
- 15+ `Ajax.Request`/`Ajax.Updater` in SearchDrug3.jsp → `fetch()` / `CarlosAjax`
- `Insertion.Bottom` / `Insertion.Top` → `insertAdjacentHTML('beforeend'/'afterbegin')`
- 8+ calls in ViewScript2.jsp → `fetch()`

**Synchronous AJAX in Rx (ordering-critical):** SearchDrug3.jsp has **10+ synchronous requests** (`asynchronous: false`) for sequential drug operations — re-prescribe, instruction save, drug interaction checks. These must execute in order. Migration: refactor the calling functions to `async` and chain operations with `await fetch()`. Example:
```javascript
// Before (Prototype synchronous):
new Ajax.Request(url, { asynchronous: false, onSuccess: function(t) { result = t.responseText; }});
doNextThing(result);

// After (vanilla async/await):
const response = await fetch(url, { method: 'POST', body: data });
const result = await response.text();
doNextThing(result);
```
StaticScript2.jsp line 165 also uses `asynchronous: false` for the same reason.

### 3d. Files
| File | Key Changes |
|------|------------|
| `oscarRx/SearchDrug3.jsp` | Ajax calls, Effects, LightWindow, Insertion enum |
| `oscarRx/ViewScript2.jsp` | Ajax calls, LightWindow |
| `oscarRx/ViewScript.jsp` | Ajax calls |
| `oscarRx/WriteScript.jsp` | 2 Ajax.Updater (renal dosing) |
| `oscarRx/ChooseAllergy2.jsp` | Effect.BlindDown/BlindUp |
| `oscarRx/StaticScript2.jsp` | 3 Ajax calls |
| `oscarRx/SelectPharmacy2.jsp` | LightWindow + Event handling |

### Verification
- Full prescription workflow: search drug → select → write script → view script
- Allergy selection with collapsible categories
- Pharmacy selection modal
- Renal dosing lookup
- Run UI test: `/test4` (prescriptions)

---

## Phase 4: Encounter + Case Management — Shim-First (Largest Scope)

**6 files, 215+ Prototype API calls. Very high complexity — core patient chart UI.**
**Strategy**: Use `prototype-compat.js` shim first for safe swap, then progressively rewrite to vanilla JS.

### 4a. `encounter.js` migration
**File**: `src/main/webapp/oscarEncounter/js/encounter.js`

| Pattern | Count | Replacement |
|---------|-------|-------------|
| `Element.observe(el, event, fn)` | ~20 | `el.addEventListener(event, fn)` |
| `Ajax.Request` | 5 | `fetch()` / `CarlosAjax.request()` |
| `$("id")` | many | `document.getElementById("id")` |
| Autocompleter.Local (calculators) | 1 | Vanilla autocomplete or datalist |

### 4b. `newCaseManagementView.js.jsp` migration (largest single file)
**File**: `src/main/webapp/js/newCaseManagementView.js.jsp` (~42KB)

| Pattern | Count | Replacement |
|---------|-------|-------------|
| `$("id")` | 398 | `document.getElementById("id")` |
| `.update(html)` | 55+ | `.innerHTML = html` |
| `.hide()` / `.show()` | many | `.style.display` or `.classList.toggle('d-none')` |
| `Ajax.Request` / `Ajax.Updater` | 20+ | `fetch()` / `CarlosAjax` |
| `Position.page(el)` | several | `el.getBoundingClientRect()` |
| `Position.positionedOffset(el)` | several | `el.offsetLeft` / `el.offsetTop` |
| `.getHeight()` | several | `el.offsetHeight` |

**Synchronous AJAX patterns (ordering-critical — must not break):**

| Location | Pattern | Why Synchronous | Migration |
|---|---|---|---|
| Line 186 | `releaseNoteLock` on `beforeunload` | Lock must release before page unloads | `navigator.sendBeacon()` |
| Line 1912 | `NoteisLocked()` returns lock status | Caller uses return value immediately | Refactor caller to `async/await`, or `XMLHttpRequest` sync interim |
| Line 3022 | `autoSave(async)` — sync when closing | Save must complete before close | `navigator.sendBeacon()` for close path; `fetch()` for normal autosave |
| Line 2840 | `ajaxUpdateIssues` with `Form.serialize()` | Sequential issue update | `fetch()` + `new FormData()` (already async, just needs API swap) |

**Strategy for this file**:
1. First pass: swap `prototype.js` → `prototype-compat.js` in encounter-head.jspf. This should make everything work with zero changes to the 42KB file.
2. Second pass: migrate synchronous AJAX patterns first (highest risk), then incrementally replace remaining shim calls with direct vanilla JS, function by function, testing after each batch.

### 4c. Update `encounter-head.jspf`
**File**: `src/main/webapp/oscarEncounter/includes/encounter-head.jspf`

```diff
- <script>var $j = jQuery.noConflict();</script>
- <script src="${ctx}/share/javascript/prototype.js" type="text/javascript"></script>
- <script src="${ctx}/share/javascript/scriptaculous.js" type="text/javascript"></script>
+ <script src="${ctx}/share/javascript/prototype-compat.js" type="text/javascript"></script>
+ <script src="${ctx}/share/javascript/carlos-ajax.js" type="text/javascript"></script>
```

Remove `jQuery.noConflict()` — no longer needed. `$` will be provided by the compat shim. `$j` references in encounter code should be changed to `$` (jQuery) or `document.getElementById`.

### 4d. Other encounter files
| File | Changes |
|------|---------|
| `casemgmt/newEncounterLayout.jsp` | Remove Prototype includes, update inline JS |
| `casemgmt/ChartNotes.jsp` / `ChartNotesAjax.jsp` | Ajax calls → fetch() |

### Verification
- **Critical**: This is the most-used screen in the entire EMR
- Open encounter for a test patient → verify all left-nav sections load
- Click through: preventions, ticklers, dx, forms, eforms, docs, labs, HRM, msgs, measurements
- Test case management notes: create, edit, save, CPP sections
- Test calculator autocomplete dropdown
- Verify no `$j is not defined` or `$ is not a function` errors
- Run UI tests: `/test1` (smoke), `/test5` (ticklers), `/test6` (encounter)

---

## Phase 5: Cleanup

### 5a. Remove library files
```
src/main/webapp/share/javascript/prototype.js        ← DELETE
src/main/webapp/share/javascript/scriptaculous.js     ← DELETE
src/main/webapp/share/javascript/effects.js           ← DELETE
src/main/webapp/share/javascript/controls.js          ← DELETE
src/main/webapp/share/javascript/builder.js           ← DELETE
src/main/webapp/share/javascript/slider.js            ← DELETE
src/main/webapp/share/lightwindow/lightwindow.js      ← DELETE
src/main/webapp/share/lightwindow/lightwindow.css     ← DELETE
```

### 5b. Remove old jQuery versions
```
src/main/webapp/js/jquery-1.12.3.js                   ← DELETE
src/main/webapp/js/jquery-1.7.1.min.js                ← DELETE
src/main/webapp/js/jquery-1.9.1.js                    ← DELETE
src/main/webapp/js/jquery-1.9.1.min.js                ← DELETE
src/main/webapp/library/jquery/jquery-1.12.0.min.js   ← DELETE
src/main/webapp/share/javascript/jquery/jquery-1.4.2.js ← DELETE
```

Update any JSP files that reference these old versions to use the standard `global-head.jspf` include instead.

### 5c. Remove all `jQuery.noConflict()` calls
Search all 30+ files with `jQuery.noConflict()` and remove the calls + rename `$j` → `jQuery` or `$` as appropriate.

### 5d. Graduate encounter module from compat shim
Once Phase 4 encounter/case management code is fully migrated to native APIs, remove `prototype-compat.js` and the HTMLElement.prototype extensions. This can be done incrementally — each function in `newCaseManagementView.js.jsp` can be rewritten to vanilla JS and the shim method removed once no callers remain.

### Verification
- `grep -r "prototype.js\|scriptaculous.js\|effects.js\|controls.js" src/main/webapp/` → zero results
- `grep -r "noConflict" src/main/webapp/` → zero results
- Full UI test suite: `/test-fullsuite`
- `make install --run-tests` passes

---

## Libraries Retained / Added

| Library | Status | Rationale |
|---------|--------|-----------|
| jQuery 3.6.4 | **KEEP** | Too deeply embedded; separate future initiative |
| Bootstrap 5.3.3 | **KEEP** | Target UI framework, replaces Effects + LightWindow |
| jQuery UI 1.12.1 | **KEEP** | Used for datepickers, already loaded |
| `prototype-compat.js` | **NEW → TEMPORARY** | Bridge for encounter module (Phase 4) only, removed in Phase 5d |
| `carlos-ajax.js` | **NEW → KEEP** | Clean fetch() wrapper, useful long-term |
| `transitions.css` | **NEW → KEEP** | Lightweight CSS transitions |

No additional third-party libraries required. All replacements use vanilla JS, Bootstrap 5 components, or CSS transitions.

---

## Execution Order & Dependencies

```
Phase 0 (foundation) ─── must complete first
  │
  ├── Phase 1 (admin/forms/docs) ──┐
  ├── Phase 2 (lab) ───────────────┤── can run in parallel
  │                                │
  ├── Phase 3 (prescription) ──────┘── depends on Phase 0 only
  │
  └── Phase 4 (encounter/casemgmt) ─── depends on Phase 0; largest scope
        │
        └── Phase 5 (cleanup) ─── after ALL phases complete
```

Phases 1-3 are independent and can be done in any order. Phase 4 is the highest-risk and should be done last (except cleanup). Each phase should be a separate PR for reviewability.

---

## Appendix: Additional Modules Not Yet Categorized

The full grep found **~85 JSP files** loading Prototype.js — significantly more than the initial ~42 estimate. These additional files should be folded into the phases above based on complexity:

### Fold into Phase 1 (simple Ajax.Request replacements):
| File | Prototype Usage |
|------|----------------|
| `admin/manageFlowsheets.jsp` | Prototype include, likely simple AJAX |
| `admin/manageCSSStyles.jsp` | Prototype + Scriptaculous |
| `admin/sitesAdmin.jsp` | Prototype include |
| `form/formPositionHazard.jsp` | Prototype include |
| `form/formMentalHealthForm1.jsp` | Prototype include |
| `form/formMentalHealthForm14.jsp` | Prototype include |
| `form/formMentalHealthForm42.jsp` | Prototype include |
| `form/formlabreq.jsp` | Prototype include |
| `form/formlabreq07.jsp` | Prototype include |
| `form/formlabreq10.jsp` | Prototype include |
| `form/formrourke2009complete.jsp` | Prototype include |
| `form/formrourke2017complete.jsp` | Prototype include |
| `form/formrourke2020complete.jsp` | Prototype include |
| `report/reportdaysheet.jsp` | Prototype include |
| `report/GenerateLetters.jsp` | Prototype + Ajax.Request |
| `oscarReport/reportByTemplate/resultReport.jsp` | Prototype + Ajax.Request |

### Fold into Phase 2 (medium complexity):
| File | Prototype Usage |
|------|----------------|
| `oscarMDS/documentsInQueues.jsp` | Prototype + effects + controls (Autocompleter) |
| `oscarMDS/SelectProviderAltView.jsp` | Prototype + Scriptaculous + effects + controls |
| `hospitalReportManager/displayHRMReport.jsp` | Prototype + effects + controls |
| `oscarPrevention/PreventionReporting.jsp` | Prototype include |
| `oscarResearch/oscarDxResearch/dxResearch.jsp` | Prototype include |

### Fold into Phase 3 (Rx):
| File | Prototype Usage |
|------|----------------|
| `oscarRx/ChooseAllergy.jsp` | Prototype + Scriptaculous + effects |
| `oscarRx/ChooseDrug.jsp` | Prototype include |
| `oscarRx/SearchDrug.jsp` | Prototype include |

### Fold into Phase 4 (encounter/provider):
| File | Prototype Usage |
|------|----------------|
| `oscarEncounter/Index.jsp` | Prototype + Ajax.Request (legacy encounter page) |
| `oscarEncounter/oscarMeasurements/AddMeasurementData.jsp` | Prototype include |
| `provider/UserPreferences.jsp` | Prototype include |
| `provider/providerpreference.jsp` | Prototype include |
| `provider/appointmentprovideradminday.jsp` | Prototype include |
| `provider/appointmentprovideradminmonth.jsp` | Prototype include |
| `provider/cpp_preferences.jsp` | Prototype + Scriptaculous |
| `provider/setGenRxProfileViewProperty.jsp` | Prototype + Scriptaculous |
| `provider/setDocDefaultQueue.jsp` | Prototype + Scriptaculous |
| `provider/setGenRxPageSizeProperty.jsp` | Prototype + Scriptaculous |
| `provider/setEncounterWindowSize.jsp` | Prototype + Scriptaculous |
| `provider/setNoteStaleDate.jsp` | Prototype + Scriptaculous |
| `provider/setShowPatientDOB.jsp` | Prototype + Scriptaculous |
| `provider/setCppSingleLine.jsp` | Prototype + Scriptaculous |
| `provider/setLabAckComment.jsp` | Prototype + Scriptaculous |
| `provider/setToUseRx3.jsp` | Prototype + Scriptaculous |
| `provider/setAppointmentCardPrefs.jsp` | Prototype + Scriptaculous |
| `provider/setGenProperty.jsp` | Prototype + Scriptaculous |
| `demographic/demographiceditdemographic.jsp` | Prototype include |
| `documentManager/editDocument.jsp` | Prototype + Scriptaculous |
| `documentManager/addedithtmldocument.jsp` | Prototype + Scriptaculous |
| `documentManager/html5AddDocuments.jsp` | Prototype + Scriptaculous |

### New Phase: Billing Module (BC)
| File | Prototype Usage |
|------|----------------|
| `billing/CA/BC/billingBC.jsp` | Prototype + Ajax.Updater |
| `billing/CA/BC/adjustBill.jsp` | Prototype + Ajax.Updater |
| `billing/CA/BC/billingEditCode.jsp` | Prototype include |
| `billing/CA/BC/teleplan/ManageBillingCodes.jsp` | Prototype include |

These should be a separate Phase 2b or folded into Phase 2, as they follow the same Ajax.Updater patterns as the lab module.
