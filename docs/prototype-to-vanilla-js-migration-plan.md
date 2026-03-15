# Migration Plan: Prototype.js / Scriptaculous → Vanilla JS + Bootstrap 5.3

## Context

CARLOS EMR still loads **Prototype.js 1.5.1.1** (2007) and **Scriptaculous 1.7.1** (2007) across **71 JSP/JSPF files**. These libraries conflict with jQuery (requiring `jQuery.noConflict()` workarounds), add 200KB+ of dead weight, and use APIs incompatible with modern browsers' security policies (e.g., `evalScripts`). The project already has jQuery 3.6.4 and Bootstrap 5.3.3 loaded via `global-head.jspf`, and some files already use modern vanilla JS patterns (fetch, addEventListener, querySelector). This migration removes the legacy libraries incrementally, replacing their functionality with vanilla JS, Bootstrap 5 components, and CSS transitions.

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
// .insert(), .setStyle(), .getHeight(), .getWidth(), etc.
// bindAsEventListener (used 15+ times in encounter/casemgmt)
// Element.observe / Element.stopObserving (82 calls across 6 files)
// Event.stop, Event.element
// String.prototype.evalJSON (19 calls across 8 files — replace with JSON.parse())
// Form.serialize (replace with new FormData() or URLSearchParams)
// Position.page, Position.positionedOffset (replace with getBoundingClientRect())
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

Wraps `fetch()` / `XMLHttpRequest` in an API that **preserves the exact Prototype.js callback execution order**. This is critical — changing callback timing will break dependent code.

#### Prototype.js Callback Contract (MUST preserve)

**`Ajax.Request` callback order** (from prototype.js lines 1131-1160):
1. `onSuccess(transport, json)` — or `onFailure(transport, json)` if HTTP error
2. `evalResponse()` — auto-evals if `Content-Type` is `application/javascript`
3. `onComplete(transport, json)` — always fires, success or failure
4. `Ajax.Responders.dispatch('onComplete')` — global responder notification

**`Ajax.Updater` callback order** (extends Ajax.Request, lines 1196-1232):
1. `onSuccess(transport, json)` — fires first (inherited)
2. `updateContent()` — inserts response HTML into target element:
   - If `evalScripts: false` (default): strips `<script>` tags from response
   - If `evalScripts: true`: preserves `<script>` tags in inserted HTML
   - If `insertion` option (e.g., `Insertion.Bottom`): uses that insertion strategy
   - Otherwise: replaces element content via `.update(response)`
3. User's `onComplete(transport, json)` — fires AFTER DOM update

**`transport` object shape** — callbacks receive the raw `XMLHttpRequest` object. Code accesses:
- `transport.responseText` — raw response body (very common, 40+ uses)
- `transport.responseText.evalJSON()` — Prototype String extension, replace with `JSON.parse()` (19 uses)
- `transport.status` — HTTP status code (used in error display)

#### `CarlosAjax` Implementation Requirements

```javascript
const CarlosAjax = {
    request(url, options = {}) {
        // For asynchronous: true (default) — use fetch()
        // For asynchronous: false — use XMLHttpRequest synchronous mode
        // Callback order: onSuccess/onFailure → onComplete
        // transport object must expose: .responseText, .status
    },
    updater(elementOrId, url, options = {}) {
        // Same as request(), but AFTER onSuccess and BEFORE onComplete:
        //   1. Get response text
        //   2. If evalScripts !== true: strip <script> tags
        //   3. Insert into element using insertion strategy or innerHTML
        //   4. THEN call onComplete
        // Supports: insertion: 'bottom'|'top'|'before'|'after' (replaces Insertion.* enum)
    }
};
```

#### Chained Request Patterns

Some code nests Ajax.Request calls inside `onSuccess` callbacks (e.g., SearchDrug3.jsp line 1631-1643: `findInteractingDrugList` then `UpdateInteractingDrugs`). These are naturally sequential — the inner request fires only after the outer completes. With `fetch()`, this pattern becomes:
```javascript
// Prototype chained pattern:
new Ajax.Request(url1, { onSuccess: function(t1) {
    new Ajax.Request(url2, { onSuccess: function(t2) { /* use t2 */ }});
}});

// Vanilla equivalent (preserves ordering):
fetch(url1).then(r => r.text()).then(text1 => {
    return fetch(url2).then(r => r.text()).then(text2 => { /* use text2 */ });
});

// Or with async/await:
const r1 = await fetch(url1); const text1 = await r1.text();
const r2 = await fetch(url2); const text2 = await r2.text();
```

#### `evalScripts` Behavior

When `evalScripts: true` on `Ajax.Updater`, Prototype **preserves `<script>` tags** in the response HTML when inserting into the DOM. Modern browsers do NOT execute `<script>` tags inserted via innerHTML — this is a critical behavioral difference.

**The `CarlosAjax.updater()` must:**
1. Parse response HTML for `<script>` tags
2. Insert the non-script HTML into the element
3. Extract and execute each script by creating dynamic `<script>` DOM elements and appending them to the document (this is the standard safe pattern — the browser handles execution of DOM-appended script elements)

**Security context:** These scripts come from the same CARLOS EMR server (trusted origin, same-origin JSP responses). This is the same trust model as the existing Prototype evalScripts behavior. The server responses are OWASP-encoded and generated by internal JSPs — not user-supplied content.

Files relying on `evalScripts: true`:
- `oscarEncounter/js/encounter.js` — navbar section loading (3 calls)
- `js/newCaseManagementView.js.jsp` — notes loading, layout updates
- `oscarRx/SearchDrug3.jsp` — drug form rendering (multiple calls)
- `lab/CumulativeLabValues*.jsp` — lab section rendering (6 calls)
- `oscarMDS/documentsInQueues.jsp`
- `casemgmt/newEncounterLayout.jsp`

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

**Prototype String extension: `.evalJSON()`** — Used 19 times across 8 files (SearchDrug3.jsp alone has 8 calls). This is a Prototype extension on `String.prototype` that parses JSON. Direct replacement: `JSON.parse(responseText)`. Files affected:
- `oscarRx/SearchDrug3.jsp` (8 calls)
- `oscarMDS/documentsInQueues.jsp` (4 calls)
- `admin/displayDocumentDescriptionTemplate.jsp` (2 calls)
- `js/newCaseManagementView.js.jsp` (1 call)
- `lab/CA/ALL/labDisplayAjax.jsp` (1 call)
- `billing/CA/BC/billingEditCode.jsp` (1 call)
- `documentManager/incomingDocs.jsp` (1 call)
- `oscarRx/ViewScript2.jsp` (1 call)

**`evalScripts: true` scope** — Used in more files than initially documented:
- `lab/CumulativeLabValues*.jsp` (6 calls)
- `oscarRx/SearchDrug3.jsp` (multiple calls)
- `oscarEncounter/js/encounter.js` (3 calls in navBarLoader)
- `js/newCaseManagementView.js.jsp` (multiple calls)
- `oscarMDS/documentsInQueues.jsp`
- `casemgmt/newEncounterLayout.jsp`

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
| `admin/displayDocumentDescriptionTemplate.jsp` | 7 `Ajax.Request` → `fetch()`, 2 `.evalJSON()` → `JSON.parse()` |
| `admin/securityupdatesecurity.jsp` | 1 `Ajax.Request` → `fetch()` |
| `admin/manageFlowsheets.jsp` | Remove Prototype include |
| `admin/manageCSSStyles.jsp` | Remove Prototype + Scriptaculous includes |
| `admin/sitesAdmin.jsp` | Remove Prototype include |

- Remove `<script src="prototype.js">` includes
- Replace `Ajax.Request` with `fetch()` using `CarlosAjax.request()`
- No effects to replace

### 1b. Forms
| File | Changes |
|------|---------|
| `form/formRhImmuneGlobulin.jsp` | 2 `Ajax.Updater`/`Ajax.Request` → `CarlosAjax.updater()`/`fetch()` |
| `form/addRhInjection.jsp` | Remove commented-out `Form.Element.Observer` |
| `form/formPositionHazard.jsp` | Remove Prototype include |
| `form/formMentalHealthForm1.jsp` | Remove Prototype include |
| `form/formMentalHealthForm14.jsp` | Remove Prototype include |
| `form/formMentalHealthForm42.jsp` | Remove Prototype include |
| `form/formlabreq.jsp` | Remove Prototype include |
| `form/formlabreq07.jsp` | Remove Prototype include |
| `form/formlabreq10.jsp` | Remove Prototype include |
| `form/formrourke2009complete.jsp` | Remove Prototype include |
| `form/formrourke2017complete.jsp` | Remove Prototype include |
| `form/formrourke2020complete.jsp` | Remove Prototype include |

### 1c. Document Manager
| File | Changes |
|------|---------|
| `documentManager/uploadMultiDocument.jsp` | 1 `Ajax.Request` → `fetch()`, 1 `Effect.SlideUp` → CSS transition |
| `documentManager/incomingDocs.jsp` | 1 `Ajax.Request` → `fetch()`, `Ajax.Autocompleter` → vanilla autocomplete (pattern from `showDocument.js`) |
| `documentManager/MultiPageDocDisplay.jsp` | Remove `jQuery.noConflict()` if Prototype no longer loaded |
| `documentManager/editDocument.jsp` | Remove Prototype + Scriptaculous includes |
| `documentManager/addedithtmldocument.jsp` | Remove Prototype + Scriptaculous includes |
| `documentManager/html5AddDocuments.jsp` | Remove Prototype + Scriptaculous includes |
| `report/reportdaysheet.jsp` | Remove Prototype include |
| `report/GenerateLetters.jsp` | `Ajax.Request` → `fetch()` |
| `oscarReport/reportByTemplate/resultReport.jsp` | `Ajax.Request` → `fetch()` |

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
| `lab/CA/ALL/labDisplayAjax.jsp` | `.evalJSON()` → `JSON.parse()`, Effect.BlindUp → CSS transition |
| `oscarMDS/documentsInQueues.jsp` | Prototype + effects + controls, 4 `.evalJSON()` → `JSON.parse()`, `evalScripts` |
| `oscarMDS/SelectProviderAltView.jsp` | Prototype + Scriptaculous + effects + controls (Autocompleter) |
| `hospitalReportManager/displayHRMReport.jsp` | Prototype + effects + controls |
| `oscarPrevention/PreventionReporting.jsp` | Prototype + sortable.js |
| `oscarResearch/oscarDxResearch/dxResearch.jsp` | Prototype include |
| `billing/CA/BC/billingBC.jsp` | Prototype + Ajax.Updater |
| `billing/CA/BC/adjustBill.jsp` | Prototype + Ajax.Updater |
| `billing/CA/BC/billingEditCode.jsp` | Prototype + `.evalJSON()` → `JSON.parse()` |
| `billing/CA/BC/teleplan/ManageBillingCodes.jsp` | Prototype include |

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
| `oscarRx/SearchDrug3.jsp` | Ajax calls, Effects, LightWindow, Insertion enum, 8 `.evalJSON()`, `evalScripts`, dragiframe.js |
| `oscarRx/ViewScript2.jsp` | Ajax calls, LightWindow, 1 `.evalJSON()` |
| `oscarRx/ViewScript.jsp` | Ajax calls |
| `oscarRx/WriteScript.jsp` | 2 Ajax.Updater (renal dosing) |
| `oscarRx/ChooseAllergy2.jsp` | Effect.BlindDown/BlindUp |
| `oscarRx/ChooseAllergy.jsp` | Prototype + Scriptaculous + effects |
| `oscarRx/ChooseDrug.jsp` | Prototype include |
| `oscarRx/SearchDrug.jsp` | Prototype include |
| `oscarRx/StaticScript2.jsp` | 3 Ajax calls, `asynchronous: false` |
| `oscarRx/SelectPharmacy2.jsp` | LightWindow + Event handling |
| `oscarRx/prescribe.jsp` | Effect.BlindDown/BlindUp |
| `oscarRx/displayMedHistory.jsp` | dragiframe.js reference |
| `oscarRx/ListDrugs.jsp` | Element.observe (1 call) |

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
| `Element.observe(el, event, fn)` | 3 | `el.addEventListener(event, fn)` |
| `Element.stopObserving(el, event, fn)` | 3 | `el.removeEventListener(event, fn)` |
| `bindAsEventListener(obj, ...)` | 3 | `fn.bind(obj)` or closure |
| `Ajax.Request` | 5 | `fetch()` / `CarlosAjax.request()` |
| `$("id")` | ~20 | `document.getElementById("id")` |
| `$F("id")` | 3 | `document.getElementById("id").value` |
| `$A(nodeList)` | 3 | `Array.from(nodeList)` |
| `Event.stop(e)` | 1 | `e.preventDefault(); e.stopPropagation()` |
| `el.getHeight()` | 2 | `el.offsetHeight` |
| `el.update(html)` | 3 | See security note below |
| Autocompleter.Local (calculators) | 1 | Vanilla autocomplete or datalist |

### 4b. `newCaseManagementView.js.jsp` migration (largest single file)
**File**: `src/main/webapp/js/newCaseManagementView.js.jsp` (~42KB)

| Pattern | Count | Replacement |
|---------|-------|-------------|
| `$("id")` | 398 | `document.getElementById("id")` |
| `.update(html)` | 55+ | See security note below |
| `.hide()` / `.show()` | many | `.style.display` or `.classList.toggle('d-none')` |
| `Ajax.Request` / `Ajax.Updater` | 19 | `fetch()` / `CarlosAjax` |
| `Element.observe` / `Element.stopObserving` | 66 | `addEventListener` / `removeEventListener` |
| `Event.stop(e)` / `Event.element(e)` | 15 | `e.preventDefault()` / `e.target` |
| `bindAsEventListener` | 12 | `fn.bind(obj)` or closure with extra args |
| `Form.serialize()` | 2 | `new URLSearchParams(new FormData(form))` |
| `.evalJSON()` | 1 | `JSON.parse()` |
| `Position.page(el)` | 2 | `el.getBoundingClientRect()` + `window.scrollY` |
| `Position.positionedOffset(el)` | 1 | `el.offsetLeft` / `el.offsetTop` |
| `.getHeight()` / `.getWidth()` | 50+ | `el.offsetHeight` / `el.offsetWidth` |

**Security note on `.update()` → DOM replacement:** The existing `.update()` calls set `innerHTML` with server responses from trusted internal endpoints. The encounter.js header documents this: "AJAX responses that use $(div).update() are from trusted internal server endpoints only. XSS is mitigated by server-side OWASP encoding in those response JSPs." During migration, these become direct `element.innerHTML = response` assignments, preserving the same trust model. No additional sanitization is needed because the content source (server-side JSPs with OWASP encoding) remains unchanged.

**Synchronous AJAX patterns (ordering-critical — must not break):**

| Location | Pattern | Why Synchronous | Migration |
|---|---|---|---|
| Line 186 | `releaseNoteLock` on `beforeunload` | Lock must release before page unloads | `navigator.sendBeacon()` |
| Line 1912 | `NoteisLocked()` returns lock status | Caller uses return value immediately | Refactor caller to `async/await`, or `XMLHttpRequest` sync interim |
| Line 3022 | `autoSave(async)` — accepts sync flag | Dead code: only ever called as `autoSave(true)` | Remove `async` parameter, always use `fetch()` |
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

### 4d. Other encounter/casemgmt files
| File | Changes |
|------|---------|
| `casemgmt/newEncounterLayout.jsp` | Remove Prototype includes, update inline JS, `evalScripts` |
| `casemgmt/ChartNotes.jsp` | 6 Element.observe/stopObserving calls → addEventListener |
| `casemgmt/ChartNotesAjax.jsp` | 5 Element.observe calls, Autocompleter.Local → vanilla autocomplete |
| `oscarEncounter/Index.jsp` | Legacy encounter page, Prototype + Ajax.Request |
| `oscarEncounter/LeftNavBarDisplay.jsp` | Generates `bindAsEventListener` / `Element.observe` JS dynamically from Java |
| `oscarEncounter/oscarMeasurements/AddMeasurementData.jsp` | Remove Prototype include |

### 4e. Provider settings pages (script-tag-only — no Prototype API usage)
These files load Prototype/Scriptaculous but **don't call any Prototype APIs** — they just need the `<script>` tags removed:
| File |
|------|
| `provider/UserPreferences.jsp` |
| `provider/providerpreference.jsp` |
| `provider/appointmentprovideradminday.jsp` |
| `provider/appointmentprovideradminmonth.jsp` |
| `provider/cpp_preferences.jsp` |
| `provider/setGenRxProfileViewProperty.jsp` |
| `provider/setDocDefaultQueue.jsp` |
| `provider/setGenRxPageSizeProperty.jsp` |
| `provider/setEncounterWindowSize.jsp` |
| `provider/setNoteStaleDate.jsp` |
| `provider/setShowPatientDOB.jsp` |
| `provider/setCppSingleLine.jsp` |
| `provider/setLabAckComment.jsp` |
| `provider/setToUseRx3.jsp` |
| `provider/setAppointmentCardPrefs.jsp` |
| `provider/setGenProperty.jsp` |
| `demographic/demographiceditdemographic.jsp` |

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
src/main/webapp/share/javascript/sortable.js          ← DELETE (used by PreventionReporting.jsp)
src/main/webapp/share/javascript/dragiframe.js        ← DELETE (used by SearchDrug3.jsp, displayMedHistory.jsp)
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

All files from the original appendix have been incorporated into the phase definitions above.
