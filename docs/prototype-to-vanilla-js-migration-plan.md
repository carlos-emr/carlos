# Migration Plan: Prototype.js / Scriptaculous → Vanilla JS + Bootstrap 5.3

## Context

CARLOS EMR still loads **Prototype.js 1.5.1.1** (2007) and **Scriptaculous 1.7.1** (2007) across **~84 files** (72 that load the library directly, plus files that use Prototype APIs via transitive dependencies, shared JS includes, or AJAX-loaded contexts). These libraries conflict with jQuery (requiring `jQuery.noConflict()` workarounds), add 200KB+ of dead weight, and use APIs incompatible with modern browsers' security policies (e.g., `evalScripts`). The project already has jQuery 3.6.4 and Bootstrap 5.3.3 loaded via `global-head.jspf`, and some files already use modern vanilla JS patterns (fetch, addEventListener, querySelector). This migration removes the legacy libraries incrementally, replacing their functionality with vanilla JS, Bootstrap 5 components, and CSS transitions.

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
// .up(selector, level), .down(selector) — DOM traversal (lines 1888-1891, 3394, 3496, 3547)
// .toggle() — alternates display (lines 1077, 2132, 2157, 2160, 2163, 2744)
// bindAsEventListener (used 12 times in encounter/casemgmt — 9 in newCaseMgmt, 3 in encounter.js)
// Element.observe / Element.stopObserving (82 calls across 6 files)
// Element.toggle (standalone function form)
// Event.stop, Event.element
// String.prototype.evalJSON (19 calls across 8 files — replace with JSON.parse())
// Form.serialize (replace with new FormData() or URLSearchParams)
// HTMLFormElement.prototype.serialize — instance method form: $("formId").serialize()
//   (line 997 in newCaseManagementView.js.jsp — different from Form.serialize() static call)
// Position.page, Position.positionedOffset (replace with getBoundingClientRect())

// $$() — CSS selector function (equivalent to querySelectorAll, returns Array not NodeList):
//   window.$$ = function(selector) { return Array.from(document.querySelectorAll(selector)); };
//   Used in: reportdaysheet.jsp ($$('tr.oscar')), SearchDrug3.jsp ($$('div.hiddenResource')),
//            WriteScript.jsp ($$('div.untrustedResource'), $$('div.hiddenResource'))
//   IMPORTANT: Returns Array (not NodeList) so .invoke(), .each() etc. work via compat shim

// String.prototype.strip() — equivalent to native .trim()
//   Used in: SearchDrug3.jsp line 1323 (prnStr.strip())
//   Migration: replace .strip() → .trim() (no shim needed, just search-and-replace)
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
**Documentation**: `docs/carlos-ajax.md` — full API reference, usage guide, migration cheat sheet, and when NOT to use it

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
        //
        // IMPORTANT: Must support two target forms:
        //   - String: CarlosAjax.updater('divId', url, opts) — always update
        //   - Object: CarlosAjax.updater({success: 'divId'}, url, opts) — only update on success
        // The {success: id} form is used in newCaseManagementView.js.jsp (lines 2399, 2840)
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
| Lock release on page unload | `navigator.sendBeacon(url, data)` via `visibilitychange` event (NOT `beforeunload` — see note below) |
| Synchronous return value (`NoteisLocked`) | Refactor to `async`/`await` + make callers async, or use `XMLHttpRequest` sync as interim |
| Autosave-on-close | `navigator.sendBeacon()` for the save, set `okToClose` optimistically |
| Sequential drug operations | `async`/`await` with `fetch()` — chain with `await` instead of blocking |

`navigator.sendBeacon()` is the correct modern replacement for "fire a request during page unload" — it's specifically designed for this and is supported by all modern browsers.

**Important: Use `visibilitychange`, not `beforeunload`/`unload`**: MDN and the Page Lifecycle API specification recommend using `document.addEventListener('visibilitychange', ...)` instead of `beforeunload`/`unload` for sendBeacon. The `beforeunload` and `unload` events are unreliable on mobile browsers (pages are frequently killed without firing these events) and prevent browsers from using the back-forward cache (bfcache). The correct pattern is:
```javascript
document.addEventListener('visibilitychange', function() {
    if (document.visibilityState === 'hidden') {
        navigator.sendBeacon(url, new URLSearchParams({
            noteId: 123,
            'CSRF-TOKEN': getCsrfToken()
        }));
    }
});
```
The `visibilitychange` event with `document.visibilityState === 'hidden'` fires in all the same scenarios as `beforeunload` (tab close, navigation, app switch) plus additional mobile scenarios where `beforeunload` does not fire.

For the synchronous-return-value pattern, the proper fix is to refactor callers to be async, but `XMLHttpRequest` sync can be used as an interim bridge in the shim.

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

### 0e. Fix pre-existing CSRF bugs in existing `fetch()` calls
These files already use `fetch()` for POST requests today but are **missing CSRF tokens** and the `X-Requested-With` header. They are broken today (CSRF validation failures silently redirect to error page). Fix them as part of Phase 0 to establish the correct patterns before the main migration:

| File | Issue |
|------|-------|
| `documentManager/showDocument.jsp` | `fetch()` POST without CSRF token |
| `lab/CA/ALL/labDisplayAjax.jsp` | `fetch()` POST without CSRF token |
| `oscarRx/Preview2.jsp` | `fetch()` POST without CSRF token |
| `oscarRx/EditFavorites2.jsp` | `fetch()` POST without CSRF token |
| `share/javascript/oscarMDSIndex.js` (`postForm()`) | `fetch()` POST without CSRF token |

Fix: Add `'X-Requested-With': 'XMLHttpRequest'` header, `credentials: 'same-origin'`, and `'CSRF-TOKEN': getCsrfToken()` as a request **header** on each of these calls. These fixes are independent of the Prototype migration and should be merged first.

### Verification
- Load any page → confirm no console errors
- Shim file loads without conflicting with existing Prototype.js (guarded by `if` checks)
- Verify fixed `fetch()` calls in 0e work correctly with CSRF validation

---

## Phase 1: Low-Risk Modules — Full Rewrite (Admin, Forms, Document Manager)

**26 files. Minimal UI impact. Direct vanilla JS rewrite, no shim.**

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
| `report/reportdaysheet.jsp` | Remove Prototype include. Has live `$$('tr.oscar')` + `.invoke('hide'/'show')` calls (lines ~119-124) — replace with `document.querySelectorAll()` + `.forEach()`. |
| `report/GenerateLetters.jsp` | `Ajax.Request` → `fetch()` |
| `oscarReport/reportByTemplate/resultReport.jsp` | `Ajax.Request` in `clearSession()` on `<body onunload>` → `navigator.sendBeacon()`. **No Prototype load tag** — `Ajax.Request` call is currently broken/silently fails. Fix independently of Prototype removal. |

### Verification
- `/admin/` pages: test document description template CRUD, security update saves
- `/form/` pages: test Rh immunoglobulin form submission
- `/documentManager/`: test multi-document upload, incoming docs autocomplete search
- Run UI test suites: `/test1` (smoke), `/test6` (encounter/e-chart)

---

## Phase 2: Lab Module — Full Rewrite

**13 files. Medium complexity due to `evalScripts` and Autocompleter. Direct vanilla JS rewrite, no shim.**

| File | Changes |
|------|---------|
| `lab/CumulativeLabValues.jsp` | 2 `Ajax.Updater` with `evalScripts` → `CarlosAjax.updater()` |
| `lab/CumulativeLabValues2.jsp` | 2 `Ajax.Updater` → `CarlosAjax.updater()` |
| `lab/CumulativeLabValues3.jsp` | 2 `Ajax.Updater` → `CarlosAjax.updater()` |
| `lab/CA/ALL/labDisplayAjax.jsp` | 1 `Effect.BlindUp` → Bootstrap collapse or CSS transition, `.evalJSON()` → `JSON.parse()` |
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

**16 files, 40+ Prototype API calls. High complexity — includes LightWindow replacement. Direct vanilla JS rewrite, no shim.**

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
| `oscarRx/SearchDrug3.jsp` | Ajax calls, Effects, LightWindow, Insertion enum, 8 `.evalJSON()`, `evalScripts`, dragiframe.js, `$$('div.hiddenResource')` + `.invoke()`, `.strip()` → `.trim()` (line 1323), `.getStyle()` (lines 249, 1242, 1324) |
| `oscarRx/ViewScript2.jsp` | Ajax calls, LightWindow, 1 `.evalJSON()` |
| `oscarRx/ViewScript.jsp` | Ajax calls |
| `oscarRx/ChooseAllergy2.jsp` | Effect.BlindDown/BlindUp |
| `oscarRx/ChooseAllergy.jsp` | Prototype + Scriptaculous + effects |
| `oscarRx/ChooseDrug.jsp` | Prototype include |
| `oscarRx/SearchDrug.jsp` | Prototype include |
| `oscarRx/StaticScript2.jsp` | 3 Ajax calls, `asynchronous: false` |
| `oscarRx/SelectPharmacy2.jsp` | LightWindow + Event handling |
| `oscarRx/prescribe.jsp` | Effect.BlindDown/BlindUp, `$().getStyle()` — loaded via AJAX into SearchDrug3.jsp's `#rxText` div. **Must migrate simultaneously with SearchDrug3.jsp** or Effects will break. |
| `oscarRx/ShowAllergies2.jsp` | `Effect.BlindDown`/`Effect.BlindUp` inside jQuery `$.fn.toggleSection` (lines ~179, ~182). Standalone page (no Prototype load tag) — relies on parent loading Scriptaculous. Replace with CSS transitions. |
| `oscarRx/SelectReason.jsp` | Loads Prototype. Remove include, migrate any inline API calls. |
| `oscarRx/Preview2.jsp` | Loads Prototype. Pre-existing CSRF bug with `fetch()` POST (see Phase 0e). Remove Prototype include after CSRF fix. |
| `oscarRx/displayMedHistory.jsp` | dragiframe.js reference |
| `oscarRx/ListDrugs.jsp` | Element.observe (1 call) |
| `oscarRx/WriteScript.jsp` | 2 Ajax.Updater (renal dosing), `$$('div.untrustedResource')` + `$$('div.hiddenResource')` + `.invoke()` |

### Verification
- Full prescription workflow: search drug → select → write script → view script
- Allergy selection with collapsible categories
- Pharmacy selection modal
- Renal dosing lookup
- Run UI test: `/test4` (prescriptions)

---

## Phase 4: Encounter + Case Management — Shim-First (Largest Scope)

**29 files (across sub-phases 4a-4f), 215+ Prototype API calls. Very high complexity — core patient chart UI.**
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
| `$("id")` | 248 | `document.getElementById("id")` |
| `.update(html)` | 29 | See security note below |
| `.hide()` / `.show()` | 8 | `.style.display` or `.classList.toggle('d-none')` |
| `.toggle()` | 6 | `el.style.display = (el.style.display === 'none') ? '' : 'none'` |
| `Ajax.Request` | 16 | `fetch()` / `CarlosAjax.request()` |
| `Ajax.Updater` | 3 | `CarlosAjax.updater()` (supports `{success: div}` form) |
| `Element.observe` / `Element.stopObserving` | 32 | `addEventListener` / `removeEventListener` |
| `Event.stop(e)` | 15 | `e.preventDefault(); e.stopPropagation()` |
| `Event.element(e)` | 7 | `e.target` |
| `Event.pointerX/Y(e)` | 2 | `e.clientX` / `e.clientY` |
| `bindAsEventListener` | 9 | `fn.bind(obj)` or closure with extra args |
| `$F()` | 62 | `document.getElementById(id).value` |
| `Form.serialize()` | 7 | `new URLSearchParams(new FormData(form))` (incl. 1 instance method form) |
| `.evalJSON()` | 1 | `JSON.parse()` |
| `evalScripts: true` | 15 | CarlosAjax script extraction |
| `Insertion.Top` / `Insertion.Bottom` | 41 | `insertAdjacentHTML('afterbegin'/'beforeend')` |
| `Element.remove()` | 41 | `el.remove()` (native) |
| `Position.page(el)` | 1 | `el.getBoundingClientRect()` + `window.scrollY` |
| `Position.positionedOffset(el)` | 1 | `el.offsetLeft` / `el.offsetTop` |
| `.getHeight()` | 5 | `el.offsetHeight` |
| `.up()` / `.down()` | 8 | `el.closest()` / `el.querySelector()` |
| `$A()` | 5 | `Array.from()` |
| `Effect.Fade` / `Effect.Appear` | 10 | CSS transitions |
| `Effect.BlindUp` / `Effect.BlindDown` | 2 | Bootstrap collapse or CSS transitions |
| `.setStyle()` / `.addClassName()` | 2 | `el.style.*` / `el.classList.add()` |

**Security note on `.update()` → DOM replacement:** The existing `.update()` calls set `innerHTML` with server responses from trusted internal endpoints. The encounter.js header documents this: "AJAX responses that use $(div).update() are from trusted internal server endpoints only. XSS is mitigated by server-side OWASP encoding in those response JSPs." During migration, these become direct `element.innerHTML = response` assignments, preserving the same trust model. No additional sanitization is needed because the content source (server-side JSPs with OWASP encoding) remains unchanged.

**Synchronous AJAX patterns (ordering-critical — must not break):**

| Location | Pattern | Why Synchronous | Migration |
|---|---|---|---|
| Line 181 | `releaseNoteLock` in `onClosing()` | Lock must release before page unloads | `navigator.sendBeacon()` via `visibilitychange`. **Note**: `onClosing()` is NOT wired to `beforeunload` in this JS file — it must be called from the surrounding JSP (likely `newEncounterLayout.jsp`). Migration must trace and update the wiring in the JSP. |
| Line 1912 | `NoteisLocked()` returns lock status | Caller uses return value immediately | Refactor caller to `async/await`, or `XMLHttpRequest` sync interim. **Note**: also has `evalScripts: true` — unusual for a JSON-returning endpoint. CarlosAjax sync mode must handle evalScripts. |
| Line 3013 | `autoSave(async)` — accepts sync flag | Dead code: only ever called as `autoSave(true)` | Remove `async` parameter, always use `fetch()`. **Also**: line 3020 uses deprecated `escape()` for encoding — replace with `encodeURIComponent()`. |
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

Remove `jQuery.noConflict()` — no longer needed. `$` will be provided by the compat shim. The `$j` variable is defined on line 3 of encounter-head.jspf but is **never used anywhere in the codebase** (zero references) — it's dead code that can be safely deleted.

### 4d. Other encounter/casemgmt files
| File | Changes |
|------|---------|
| `share/javascript/select.js` | Hard Prototype/Scriptaculous dependency: `Class.create()`, `Autocompleter.Base`, `Object.extend()`. Must be fully rewritten or replaced with a vanilla autocomplete component. Loaded by `casemgmt/newEncounterLayout.jsp`, lab pages, eform pages. |
| `casemgmt/newEncounterLayout.jsp` | Remove Prototype includes, update inline JS, `evalScripts` |
| `casemgmt/ChartNotes.jsp` | 6 Element.observe/stopObserving calls → addEventListener |
| `casemgmt/ChartNotesAjax.jsp` | 5 Element.observe calls, Autocompleter.Local → vanilla autocomplete |
| `casemgmt/noteIssueList.jsp` | Statically included by `ChartNotesAjax.jsp` (lines ~409, ~863). Contains `new Autocompleter.SelectBox(selectEnc)` (line ~533) which depends on `select.js` → `Autocompleter.Base` from `controls.js`. Must replace with vanilla autocomplete when `select.js` is rewritten. |
| `oscarEncounter/Index.jsp` | Legacy encounter page, Prototype + Ajax.Request |
| `oscarEncounter/LeftNavBarDisplay.jsp` | Generates `bindAsEventListener` / `Element.observe` JS dynamically from Java |
| `oscarEncounter/oscarMeasurements/AddMeasurementData.jsp` | Remove Prototype include |

### 4e. Provider/demographic pages with live Prototype API usage
These files load Prototype/Scriptaculous AND call Prototype APIs — they need both tag removal and API migration:

| File | Changes |
|------|---------|
| `provider/providerpreference.jsp` | 2 `Ajax.Request` POST calls (lines ~1576, ~1609) for auto-save of Rx Interaction Warning Level and Review Messages Time → `fetch()`. **Patient-affecting**: controls drug interaction alert settings. |
| `provider/appointmentprovideradminmonth.jsp` | 1 `Ajax.Updater` GET (line ~452) for loading appointment detail → `CarlosAjax.updater()` |
| `provider/appointmentprovideradminday.jsp` | Includes `schedulePage.js.jsp` which has 1 `Ajax.Request` POST in `storeApptNo()` (line ~55) → `fetch()` |
| `provider/schedulePage.js.jsp` | 1 `Ajax.Request` POST in `storeApptNo()` — shared JS include loaded by both appointment admin pages. Must be migrated when either appointment admin page is migrated. |
| `provider/setDocDefaultQueue.jsp` | `Effect.BlindDown()` + `Effect.BlindUp()` (lines ~112-117) → CSS transitions or Bootstrap collapse |
| `demographic/demographiceditdemographic.jsp` | 1 `Ajax.Request` GET (line ~5021) in `callEligibilityWebService()` for health insurance eligibility check → `fetch()` |

### 4f. Provider settings pages (script-tag-only — no Prototype API usage)
These files load Prototype/Scriptaculous but **don't call any Prototype APIs** — they just need the `<script>` tags removed:
| File |
|------|
| `provider/UserPreferences.jsp` |
| `provider/cpp_preferences.jsp` |
| `provider/setGenRxProfileViewProperty.jsp` |
| `provider/setGenRxPageSizeProperty.jsp` |
| `provider/setEncounterWindowSize.jsp` |
| `provider/setNoteStaleDate.jsp` |
| `provider/setShowPatientDOB.jsp` |
| `provider/setCppSingleLine.jsp` |
| `provider/setLabAckComment.jsp` |
| `provider/setToUseRx3.jsp` |
| `provider/setAppointmentCardPrefs.jsp` |
| `provider/setGenProperty.jsp` |

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
src/main/webapp/share/javascript/select.js            ← DELETE (rewritten in Phase 4d)
src/main/webapp/share/lightwindow/javascript/lightwindow.js  ← DELETE
src/main/webapp/share/lightwindow/css/lightwindow.css       ← DELETE
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
**29 files** contain `jQuery.noConflict()` calls. The `$j` variable defined in `encounter-head.jspf` is **never used anywhere** — it's defined but has zero references across the entire codebase.

**Removal groups** (after Prototype is removed from each page):

| Group | Files | Action |
|-------|-------|--------|
| **A: No Prototype, no $j** (20 files) | `appointmentstatussetting.jsp`, `editappointment.jsp`, `billingON*.jsp` (4), `ticklerDemoMain.jsp`, `AddMeasurementData.jsp`, `demographic*.jsp` (4), `admin.jsp`, `appointmentprovideradmin*.jsp` (2), `EnrollmentHistory.jsp`, `ManageContacts.jsp`, `SegmentDisplay.jsp`, `ChartNotes.jsp` | Safe to remove immediately — dead calls |
| **B: Prototype loaded, no $() in JSP** (4 files) | `dxResearch.jsp`, `demographiceditdemographic.jsp` (also has Ajax.Request — see Phase 4e), `UserPreferences.jsp`, `manageFlowsheets.jsp` | Remove after Prototype `<script>` tag removed and Ajax calls migrated |
| **C: Active Prototype $() usage** (5 files) | `encounter-head.jspf`, `newEncounterLayout.jsp`, `billingBC.jsp`, `SearchDrug3.jsp`, `MultiPageDocDisplay.jsp` | Remove ONLY after Prototype code migrated (Phases 1-4) |
| **D: Third-party plugin** (1 file) | `js/jquery.fileDownload.js` — `var $ = jQuery.noConflict()` as module-local pattern | Leave as-is |

**ACTIVE BUG — `demographicMeasurementModal.jsp`**: Uses `jQuery.noConflict(true)` (deep release) at line 50, which destroys BOTH `$` AND `jQuery` globals. This modal is included by `formrourke2020complete.jsp` and `formBCAR2020pg1.jsp` — after the include fires, the `jQuery` global is gone, breaking any deferred `jQuery(...)` callbacks on the parent page. Fix: change to `jQuery.noConflict()` (without `true`) or restructure the include ordering. This is a pre-existing bug independent of the Prototype migration.

**`MultiPageDocDisplay.jsp` anomaly**: Calls `jQuery.noConflict()` inside a JSP `<c:forEach>` loop — invoked once per document row rendered. Functionally harmless (idempotent) but should be moved outside the loop during cleanup.

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

### Rollback Strategy

Each phase should be a separate PR. Rollback for any phase is `git revert` of the merged PR.

| Phase | Rollback |
|-------|----------|
| Phase 0 | Revert `global-head.jspf` changes, remove new files (`carlos-ajax.js`, `prototype-compat.js`, `transitions.css`) |
| Phases 1-3 | Revert individual JSP changes (each phase is one PR) |
| Phase 4 | Revert `encounter-head.jspf` back to loading `prototype.js` + `scriptaculous.js` |
| Phase 5 | Cannot roll back library deletions without also reverting all prior phases — execute last |

---

## Critical Migration Gotchas (from industry experience)

These are common mistakes when migrating from Prototype.js, gathered from web research and known issues. Each is addressed in the behavioral contracts below.

### G1. `fetch()` defaults to GET — Prototype defaults to POST
Forgetting `method: 'POST'` silently changes behavior. `CarlosAjax` defaults to `'POST'` to match Prototype.

### G2. `fetch()` does NOT reject on HTTP errors
A 404/500 resolves the Promise normally. Must check `response.ok` or `response.status`. Prototype auto-routed to `onSuccess`/`onFailure` by status code. `CarlosAjax` preserves this routing.

### G3. `fetch()` network errors (DNS, connection refused) are different from HTTP errors
`fetch()` rejects (throws) on network errors — neither `onSuccess` nor `onFailure` would fire without explicit handling. `CarlosAjax` MUST catch rejected promises and route them to `onFailure` with a synthetic transport `{status: 0, responseText: 'Network error'}`, then fire `onComplete`. Prototype fired `onException` for this; application code has 17+ `onFailure` callbacks that expect to catch all errors.

### G4. `X-Requested-With: XMLHttpRequest` header is NOT sent by fetch()
Prototype sends this automatically. Three server-side filters + CSRFGuard's Ajax mode depend on it. `CarlosAjax` adds it automatically.

### G5. CSRF token validation path depends on `X-Requested-With` header
With `Ajax=true`, CSRFGuard validates from request **header** when `X-Requested-With` is present, or from request **body** when absent. `CarlosAjax` sends the token as a **header**. `sendBeacon` (no custom headers) sends it in the **body**. Both paths work correctly.

### G6. `querySelectorAll()` returns NodeList, not Array
Prototype's `$$()` returns an enhanced Array with `.invoke()`, `.each()`, etc. `querySelectorAll()` returns a NodeList (no `.map()`, `.filter()`). Always wrap: `Array.from(document.querySelectorAll(selector))`.

### G7. CSS transitions do NOT work on `display: none`
When replacing Scriptaculous `Effect.Fade`/`Effect.Appear`, you cannot simply transition `display`. Pattern: toggle an opacity class, then set `display: none` via `transitionend` event listener (or use Bootstrap's Collapse component which handles this).

### G8. Prototype's `$()` extends elements — `getElementById()` does not
Prototype's `$()` adds methods like `.show()`, `.hide()`, `.addClassName()` to the returned element. `document.getElementById()` returns a plain element. Chained calls like `$('foo').show().addClassName('active')` must be rewritten as separate statements since native DOM methods don't return `this`.

### G9. `innerHTML` does NOT execute `<script>` tags
Prototype's `evalScripts` manually extracted and eval'd scripts. Modern `innerHTML` silently drops `<script>` execution. `CarlosAjax.updater()` handles this by creating dynamic `<script>` DOM elements.

### G10. `sendBeacon()` cannot set custom headers
Cannot send `X-Requested-With` or `CSRF-TOKEN` as headers. Alternative: `fetch()` with `keepalive: true` supports custom headers and survives page unload (same 64KB limit).

### G11. No application code uses `Ajax.Responders`
Confirmed by codebase grep — `Ajax.Responders` (global AJAX lifecycle hooks) is only used within `prototype.js` itself. No global responder registration exists in application code, so this feature does not need replacement.

---

## Behavioral Contracts — Must Preserve During Migration

This section documents every behavioral contract from Prototype.js and Scriptaculous that CARLOS EMR code depends on. Violating any of these contracts will cause silent breakage. Each contract specifies the Prototype behavior, the vanilla JS replacement, and the files affected.

### Contract 1: `X-Requested-With: XMLHttpRequest` Header (CRITICAL — Server-Side)

**Prototype behavior**: Automatically adds `X-Requested-With: XMLHttpRequest` header to ALL Ajax requests (prototype.js line 1089).

**Why it matters**: Three server-side Java servlet filters check this header to distinguish AJAX requests from page loads:
- `PrivacyStatementAppendingFilter.java` (line 145) — skips privacy statement injection for AJAX
- `CsrfGuardScriptInjectionFilter.java` (lines 75-76) — skips CSRF script tag injection for AJAX
- `LogoutBroadcastFilter.java` (lines 101-102) — skips logout broadcast for AJAX

**Breaking change if omitted**: AJAX responses will have privacy statements and CSRF script tags appended, corrupting JSON/HTML responses.

**Migration rule**: `CarlosAjax` and ALL `fetch()` calls MUST include:
```javascript
headers: { 'X-Requested-With': 'XMLHttpRequest' }
```

### Contract 1b: CSRF Token Injection (CRITICAL — Will Break ALL POST Requests)

**Prototype behavior**: CSRFGuard 4.5's `csrfguard.js` patches `XMLHttpRequest.prototype.open` and `.send` to automatically inject a `CSRF-TOKEN` parameter into every mutating XHR request. Since Prototype.js uses `XMLHttpRequest` internally, all 173+ POST requests in the codebase get CSRF tokens injected automatically today.

**Why it matters**: CSRFGuard 4.5 does **NOT** intercept the `fetch()` API. `fetch()` is a completely separate browser API — CSRFGuard cannot patch it. If we migrate from Prototype's `Ajax.Request` (which uses XHR) to `fetch()` without manually including the CSRF token, **every POST/PUT/DELETE/PATCH request will be rejected by the server** with a CSRF validation error.

**Protected methods**: POST, PUT, DELETE, PATCH (configured in `Owasp.CsrfGuard.properties` line 97)
**Token name**: `CSRF-TOKEN` (line 270)
**Token source**: Hidden `<input name="CSRF-TOKEN">` field auto-injected into all `<form>` elements by `csrfguard.js` via MutationObserver

**Breaking change if omitted**: Server rejects ALL mutating requests. Users cannot save notes, prescriptions, appointments, or any other data.

**Migration rule**: `CarlosAjax` MUST extract the CSRF token from the DOM and include it as a **request header** (not body parameter) for all POST/PUT/DELETE/PATCH requests. This is because CarlosAjax also sends `X-Requested-With: XMLHttpRequest`, which triggers CSRFGuard's Ajax mode — and in Ajax mode, CSRFGuard validates the token from the **header**, not the body:
```javascript
function getCsrfToken() {
    const el = document.querySelector('input[name="CSRF-TOKEN"]');
    return el ? el.value : '';
}

// CarlosAjax sends CSRF token as a REQUEST HEADER on every mutating request:
headers: {
    'X-Requested-With': 'XMLHttpRequest',
    'CSRF-TOKEN': getCsrfToken()
}
```

For `navigator.sendBeacon()` (used in page unload handlers), the CSRF token goes in the **body** — sendBeacon cannot set custom headers, and it also doesn't send `X-Requested-With`, so CSRFGuard falls back to body parameter validation:
```javascript
navigator.sendBeacon(url, new URLSearchParams({
    noteId: 123,
    'CSRF-TOKEN': getCsrfToken()
}));
```

For cases where header-based CSRF validation is needed during page unload, use `fetch()` with `keepalive: true` instead of sendBeacon (see CSRFGuard Token Path Switching section below).

**See**: `docs/carlos-ajax.md` for full implementation details and `docs/csrf-protection-architecture.md` for CSRFGuard architecture.

### Contract 1c: CSRF Failure Redirect Detection (CRITICAL — Silent Data Corruption)

**The problem**: When CSRFGuard rejects a request (missing/invalid CSRF token), it does NOT return a 403 status code. Instead, it performs a **302 redirect to `errorpage.jsp`** (configured in `Owasp.CsrfGuard.properties` line 258). The `fetch()` API follows redirects transparently by default (`redirect: 'follow'`), so the caller receives the full HTML of `errorpage.jsp` with an **HTTP 200 status**. The `onSuccess` callback fires with error page HTML as `responseText`.

**Why it matters**:
- `CarlosAjax.request()` callers will parse error page HTML as if it were valid JSON or data
- `CarlosAjax.updater()` will **inject the error page HTML into the DOM**, replacing functional content with "Looks like something went wrong..."
- `JSON.parse()` on error page HTML will throw, but callers may not handle exceptions
- The failure is **silent** — no error callback fires, no console warning, no visible indication of what went wrong
- This creates a debugging nightmare: the CSRF violation is logged server-side, but the client has no idea

**How `fetch()` exposes this**: The `Response` object has two properties that detect followed redirects:
- `response.redirected` — `true` if the response came from a redirect
- `response.url` — the final URL after following redirects (e.g., `.../errorpage.jsp`)

For synchronous `XMLHttpRequest`, the equivalent is `xhr.responseURL` which also shows the final URL after redirects.

**Migration rule**: `CarlosAjax` MUST detect CSRF redirect failures and route them to `onFailure`:
```javascript
// In CarlosAjax.request() — after fetch() resolves:
const response = await fetch(url, fetchOptions);

// Detect CSRF rejection (redirect to error page)
if (response.redirected && response.url.includes('errorpage.jsp')) {
    const transport = {
        responseText: 'CSRF validation failed — request was rejected by the server.',
        status: 403  // Synthetic status — actual was 200 after redirect
    };
    if (options.onFailure) options.onFailure(transport);
    if (options.onComplete) options.onComplete(transport);
    return;
}

// For synchronous XHR:
if (xhr.responseURL && xhr.responseURL.includes('errorpage.jsp')) {
    // Same handling — treat as failure
}
```

**Additional defense**: As a secondary check, CarlosAjax should also verify that responses expected to be JSON actually parse as JSON, and that HTML responses don't contain the error page signature (`"Looks like something went wrong"`). This catches edge cases where the redirect path changes.

**Pre-existing bugs**: Several existing `fetch()` calls in the codebase already suffer from this exact problem today (they were written without CSRF tokens and without redirect detection):
- `documentManager/showDocument.jsp` — fetch() POST without CSRF token
- `lab/CA/ALL/labDisplayAjax.jsp` — fetch() POST without CSRF token
- `oscarRx/Preview2.jsp` — fetch() POST without CSRF token
- `oscarRx/EditFavorites2.jsp` — fetch() POST without CSRF token
- `share/javascript/oscarMDSIndex.js` `postForm()` — fetch() POST without CSRF token
These should be fixed as part of Phase 0 (prerequisites).

### Contract 1d: `credentials: 'same-origin'` — Session Cookie Inclusion

**The problem**: `fetch()` defaults to `credentials: 'same-origin'` in modern browsers, which includes cookies for same-origin requests. However, older browser versions and some configurations may default to `credentials: 'omit'`, which would NOT send the JSESSIONID session cookie — causing the server to see the request as unauthenticated.

**Migration rule**: `CarlosAjax` MUST explicitly set `credentials: 'same-origin'` on all fetch() calls for defense-in-depth:
```javascript
fetch(url, {
    credentials: 'same-origin',  // Explicitly include session cookies
    // ... other options
});
```

This ensures consistent behavior across all browser versions and configurations. `XMLHttpRequest` (used for synchronous requests) automatically includes cookies by default, so no change needed there.

### Server-Side AJAX Detection Methods

Beyond the three HTTP header-based filters above, several server-side 2Actions use **query/form parameters** to detect AJAX requests:
- `EctMeasurements2Action` — checks `request.getParameter("ajax")` to return different JSP results
- `EctSaveEncounter2Action` — checks `submitMethod=ajax` parameter
- `CaseManagementEntry2Action` — checks `ajax` parameter, returns `"issueList_ajax"` result
- `CreateLabLabel2Action` — checks `ajaxcall` parameter

These parameter-based detections are independent of HTTP headers and will continue working after migration without any changes. However, they are important context: the server-side code was designed for AJAX from the start, and the header-based detection is the critical contract to preserve.

### CSRFGuard Token Path Switching (`Ajax=true` mode) — RESOLVED

**Critical finding**: With `org.owasp.csrfguard.Ajax=true` (line 171 of `Owasp.CsrfGuard.properties`), CSRFGuard's JavaScript patches XHR to inject the CSRF token as a **request header** (not a form parameter). When CSRFGuard receives a request with `X-Requested-With: XMLHttpRequest`, it validates the token from the **request header** named `CSRF-TOKEN`. Without that header, it falls back to validating from the **POST body parameter**.

**Decision**: `CarlosAjax` MUST send the CSRF token as a **request header**, not a body parameter, because it also sends `X-Requested-With: XMLHttpRequest`:
```javascript
headers: {
    'X-Requested-With': 'XMLHttpRequest',
    'CSRF-TOKEN': getCsrfToken()   // MUST be a header, not body param
}
```

For `navigator.sendBeacon()`, which **cannot set custom headers**, the CSRF token goes in the **body** — and `X-Requested-With` is also absent, so CSRFGuard falls back to body parameter validation. This is correct behavior.

For `fetch()` with `keepalive: true` (preferred over `sendBeacon` when headers are needed — e.g., for page unload handlers where you want to guarantee CSRF header validation):
```javascript
fetch(url, {
    method: 'POST',
    keepalive: true,  // Survives page unload like sendBeacon
    credentials: 'same-origin',
    headers: {
        'X-Requested-With': 'XMLHttpRequest',
        'CSRF-TOKEN': getCsrfToken(),
        'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams({ noteId: 123 })
});
```
`fetch()` with `keepalive: true` is the recommended alternative to `sendBeacon` when CSRF headers are needed, because it supports custom headers and has the same page-unload resilience. The `keepalive` flag limits total in-flight body size to 64KB (same as sendBeacon).

### Server-Side Response Format Dependencies

**All AJAX-targeted actions read parameters via `request.getParameter()`** — none parse the request body directly (no `getInputStream()` or `getReader()` usage). This means the `Content-Type: application/x-www-form-urlencoded` requirement is absolute. Switching to `application/json` bodies would silently break ALL parameter reads.

**5 actions return `text/javascript` content type** (legacy pattern — actual content is JSON):
- `DocumentPreview2Action.generateResponse()`
- `ImportDemographicDataAction42Action.generateResponse()`
- `ConsultationAttachDocs2Action.generateResponse()`
- `ConsultationClinicalData2Action` (4 response paths)
- `EctConsultationFormRequest2Action.generateResponse()`

These work today because Prototype treats the response as text regardless. With `fetch()`, callers using `response.json()` will still work, but `Content-Type` sniffing would be wrong. Not a breaking issue, but worth noting for future cleanup.

**4 actions write JSON without setting Content-Type** (defaults to container default):
- `CaseManagementEntry2Action.isNoteEdited()`, `.ajaxsave()`
- `CaseManagementView2Action.listNotes()`
- `DmsInboxManage2Action.isDocumentLinkedToDemographic()`, `.isLabLinkedToDemographic()`

Callers must parse via `JSON.parse(responseText)` (not `response.json()` which may fail on wrong content-type).

### JSP Fragments with Inline Scripts (`evalScripts: true` Critical)

**`casemgmt/unlockAjax.jsp`** (line 153-156) contains:
```html
<script>$('passwd').focus()</script>
```
This script executes today because `evalScripts: true` is set on the `Ajax.Updater` call. If `evalScripts` support is removed or broken, this focus call will **silently stop working** — the password field won't receive focus after unlock. `CarlosAjax.updater()` must extract and execute this script after DOM insertion.

### Contract 2: `Content-Type: application/x-www-form-urlencoded` Default

**Prototype behavior**: Default `contentType` is `'application/x-www-form-urlencoded'` (prototype.js line 1005). Parameters are encoded as `key=value&key2=value2`.

**Why it matters**: Server-side actions (Struts 2, servlets) expect `application/x-www-form-urlencoded` for POST body parsing via `request.getParameter()`. If Content-Type changes, parameters will be invisible to the server.

**Migration rule**: When sending POST data with `fetch()`, use:
```javascript
headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
body: new URLSearchParams(params).toString()
```
Or use `new FormData()` only with `multipart/form-data` (file uploads). Do NOT rely on `fetch()` defaults.

### Contract 3: `Form.serialize()` Output Format

**Prototype behavior**: `Form.serialize(formElement)` returns a URL-encoded query string: `field1=value1&field2=value2`. This string is used as `postBody` or concatenated with `params +=`.

**Files affected** (14+ calls):
- `js/newCaseManagementView.js.jsp` (lines 997, 2241, 2242, 2396, 2489, 2838, 3006) — string concatenation with `params +=`
  - **Line 997**: Uses instance method form `$("frmIssueNotes").serialize()` (not `Form.serialize(el)`)
  - **Line 2838**: `p.note_edit = ''` after `Form.serialize(frm)` — this is a **no-op** because `p` is a string, and property assignment on a string wrapper is discarded. This is a latent bug in the existing code. During migration, if `p` becomes a `URLSearchParams` or object, this would start working (different behavior). Review whether `note_edit` exclusion is intentional.
- `oscarEncounter/Index.jsp` (lines 783, 808) — used as `postBody`
- `oscarRx/SearchDrug3.jsp` (lines 2663, 2696)
- `oscarMDS/SelectProviderAltView.jsp` (line 82) — cross-window form access
- `report/GenerateLetters.jsp` (line 291)
- `form/formRhImmuneGlobulin.jsp` (line 653)

**Breaking change**: `new FormData()` does NOT produce a string. `new URLSearchParams(new FormData(form)).toString()` does, but the concatenation pattern (`params += "&" + Form.serialize(otherForm)`) requires the string format.

**Migration rule**: Replace `Form.serialize(form)` with `new URLSearchParams(new FormData(form)).toString()`. For the compat shim:
```javascript
window.Form = {
    serialize: function(formOrId) {
        var form = typeof formOrId === 'string' ? document.getElementById(formOrId) : formOrId;
        return new URLSearchParams(new FormData(form)).toString();
    }
};
```

### Contract 4: `$()` Returns Prototype-Extended Element (Chaining)

**Prototype behavior**: `$(id)` returns a DOM element extended with Prototype methods (`.update()`, `.hide()`, `.show()`, `.observe()`, `.addClassName()`, `.invoke()`, `.getHeight()`, etc.). Code chains these methods directly.

**Chaining patterns found**:
- `$("id").update(html)` — 55+ calls in newCaseManagementView.js.jsp
- `$("id").hide()` / `$("id").show()` — many calls
- `$("id").addClassName("sig")` — line 2957
- `$(rowIDs[i]).invoke("show")` — lines 941-947 (batch operations)
- `$("id").focus()` — lines 205-295
- `$("id").click()` — line 277
- `$("id").getHeight()` / `.getWidth()` — 50+ calls

**Files**: `js/newCaseManagementView.js.jsp` (398 `$()` calls), `oscarEncounter/js/encounter.js` (~20), `oscarEncounter/LeftNavBarDisplay.jsp`, `oscarEncounter/Index.jsp`

**Migration rule (compat shim)**: The shim's `$()` must return a plain element — all chained methods must be added to `HTMLElement.prototype`. The shim must provide: `.update()`, `.hide()`, `.show()`, `.toggle()`, `.observe()`, `.stopObserving()`, `.getHeight()`, `.getWidth()`, `.addClassName()`, `.removeClassName()`, `.insert()`, `.setStyle()`, `.up(selector, level)`, `.down(selector)`.

The `.up()` and `.down()` traversal methods are used for DOM navigation:
```javascript
// .up('div', 2) — find 2nd ancestor matching 'div'
HTMLElement.prototype.up = function(selector, level) {
    var el = this; level = level || 0;
    for (var i = 0; i <= level; i++) el = el.closest(selector + ':not(:scope)');
    // OR iterate parentElement.closest() level+1 times
    return el;
};
// .down('div') — find first descendant matching 'div'
HTMLElement.prototype.down = function(selector) { return this.querySelector(selector); };
```

The shim must also add `.serialize()` to `HTMLFormElement.prototype` (instance method form used at line 997: `$("frmIssueNotes").serialize()`).

### Contract 5: `Element.observe()` / `Element.stopObserving()` — Function Reference Identity

**Prototype behavior**: `bindAsEventListener(obj, arg1, arg2)` creates a new function with bound `this` and prepended event argument. The returned reference is stored for later removal via `Element.stopObserving(el, event, storedRef)`.

**Critical pattern** (encounter/casemgmt):
```javascript
// Store bound function reference
imgfunc[midName] = clickListDisplay.bindAsEventListener(this, midName, topName);
Element.observe(midImage, "click", imgfunc[midName]);
// Later, remove using exact same reference
Element.stopObserving(midImage, "click", imgfunc[midName]);
// Re-bind with new arguments
imgfunc[midName] = differentHandler.bindAsEventListener(this, newArgs);
Element.observe(midImage, "click", imgfunc[midName]);
```

**Files affected**:
- `js/newCaseManagementView.js.jsp` — 12 `bindAsEventListener` calls, 66 observe/stopObserving
- `oscarEncounter/js/encounter.js` — 3 `bindAsEventListener`, 6 observe/stopObserving
- `oscarEncounter/LeftNavBarDisplay.jsp` — dynamically generated from Java
- `casemgmt/ChartNotesAjax.jsp` — `addIssueFunc` stored and used
- `share/javascript/controls.js` — Autocompleter internal event handling

**Breaking change**: Vanilla `addEventListener`/`removeEventListener` requires the EXACT same function reference. If `bindAsEventListener` is replaced with `fn.bind()`, each `.bind()` call creates a NEW function — `removeEventListener` will silently fail.

**Migration rule**: Store bound function references in variables BEFORE adding listeners:
```javascript
// Correct pattern:
const handler = fn.bind(obj, arg1, arg2);
el.addEventListener('click', handler);
// Store for later removal
storedHandlers[name] = handler;
// To remove:
el.removeEventListener('click', storedHandlers[name]);
```

The compat shim must implement `bindAsEventListener`, `Element.observe`, and `Element.stopObserving` using this stored-reference pattern.

**Known leak**: `newCaseManagementView.js.jsp` line 813 passes `openAnnotation.bindAsEventListener(...)` directly as a listener argument without storing the reference. The listener is never removed, so it stacks on repeated `showEdit()` calls. This is a pre-existing bug — consider fixing during migration by storing the reference and removing the previous listener before adding a new one.

### Contract 6: `Insertion.Bottom` / `Insertion.Top` — Content Accumulation

**Prototype behavior**: `Insertion.Bottom` appends HTML to an element WITHOUT replacing existing content. `Insertion.Top` prepends. This is critical for building up drug rows, notes, etc.

**Files affected** (17+ calls):
- `oscarRx/SearchDrug3.jsp` — 15+ `Insertion.Bottom` calls accumulating drug form rows in `#rxText`
- `js/newCaseManagementView.js.jsp` — `Insertion.Top` for notes loading (line 537)
- `share/lightwindow/lightwindow.js` — `Insertion.After`, `Insertion.Top`

**Breaking change**: Setting `innerHTML =` destroys existing content. Using `innerHTML +=` causes re-parsing and event listener loss.

**Migration rule**: Replace with `insertAdjacentHTML()`:
```javascript
// Insertion.Bottom -> insertAdjacentHTML('beforeend', html)
// Insertion.Top    -> insertAdjacentHTML('afterbegin', html)
// Insertion.After  -> insertAdjacentHTML('afterend', html)
// Insertion.Before -> insertAdjacentHTML('beforebegin', html)
```

`CarlosAjax.updater()` must support an `insertion` option that maps to these positions.

### Contract 7: `Element.show()` / `Element.hide()` — Display Value Reset

**Prototype behavior**: `.show()` sets `style.display = ''` (empty string, returns to CSS default). `.hide()` sets `style.display = 'none'`. `.show()` does NOT remember the previous display value.

**Files affected**: `js/newCaseManagementView.js.jsp`, `oscarMDS/documentsInQueues.jsp` (15+ calls), `share/javascript/controls.js`

**Migration rule**: Direct replacement is safe — `el.style.display = ''` and `el.style.display = 'none'` match Prototype behavior exactly. Do NOT use jQuery's `.show()` which tries to restore previous display values. Alternatively, use Bootstrap's `.d-none` class for toggle patterns.

**`.toggle()`**: Used at lines 1077, 2132, 2157, 2160, 2163, 2744 in `newCaseManagementView.js.jsp`. Alternates between `show()` and `hide()`. Replace with:
```javascript
el.style.display = (el.style.display === 'none') ? '' : 'none';
// Or: el.classList.toggle('d-none');
```
The compat shim must also provide `Element.toggle()` (standalone function form) and `.toggle()` (element instance method).

### Contract 8: `$F()` — Form Field Value Access

**Prototype behavior**: `$F(id)` returns the `.value` property of the element with the given ID. Works on `<input>`, `<select>`, and `<textarea>`.

**Files affected** (62 calls in `newCaseManagementView.js.jsp`, 4 in `encounter.js`)

**Migration rule**: Replace with `document.getElementById(id).value`. The compat shim provides:
```javascript
window.$F = function(id) { return document.getElementById(id)?.value ?? ''; };
```

### Contract 9: `$A()` — Static Array Copy from Live Collections

**Prototype behavior**: `$A(iterable)` creates a static `Array` copy. Critical when converting `arguments` objects or live `NodeList`s that change during iteration.

**Files affected**: `oscarEncounter/js/encounter.js` (3 calls), `js/newCaseManagementView.js.jsp` (5 calls)

**Migration rule**: Replace with `Array.from()`. Exact same semantics.

### Contract 10: `.evalJSON()` — JSON Response Parsing

**Prototype behavior**: Prototype adds `.evalJSON()` to `String.prototype`. Parses JSON string to object.

**Files affected** (19 calls):
- `oscarRx/SearchDrug3.jsp` (8 calls)
- `oscarMDS/documentsInQueues.jsp` (4 calls)
- `admin/displayDocumentDescriptionTemplate.jsp` (2 calls)
- `js/newCaseManagementView.js.jsp` (1 call)
- `lab/CA/ALL/labDisplayAjax.jsp` (1 call)
- `billing/CA/BC/billingEditCode.jsp` (1 call)
- `documentManager/incomingDocs.jsp` (1 call)
- `oscarRx/ViewScript2.jsp` (1 call)

**Migration rule**: Replace `transport.responseText.evalJSON()` with `JSON.parse(transport.responseText)`.

### Contract 11: Effect Completion Callbacks (`afterFinish`, `afterUpdate`)

**Scriptaculous behavior**: Effects accept `afterFinish` callback that fires AFTER animation completes, and `afterUpdate` that fires on each animation frame. `duration` controls timing in seconds.

**Files affected**:
- `share/lightwindow/lightwindow.js` — 7 `afterFinish` callbacks (chained animations)
- `lab/CA/ALL/labDisplayAjax.jsp` — 1 `Effect.BlindUp`
- `oscarMDS/documentsInQueues.jsp` — 2 `Effect.BlindUp`
- `share/javascript/controls.js` — 2 effects (Autocompleter show/hide)

**Breaking change**: CSS transitions do NOT provide automatic callbacks. Must use `transitionend` event.

**Migration rule**: For effects with `afterFinish`:
```javascript
el.addEventListener('transitionend', function handler() {
    el.removeEventListener('transitionend', handler);
    // afterFinish code here
}, { once: true });
el.classList.add('carlos-collapsed'); // trigger transition
```
For simple effects without callbacks, CSS transitions suffice without `transitionend`.

### Contract 12: `Autocompleter.Local` — Client-Side Autocomplete

**Scriptaculous behavior**: `new Autocompleter.Local(inputId, listId, dataArray, options)` creates a dropdown autocomplete. Key options:
- `afterUpdateElement(inputElement, selectedItem)` — callback when user selects an item
- `colours` — custom option (used in encounter template selector)
- `onShow` / `onHide` — visibility callbacks (default uses `Effect.Appear`/`Effect.Fade`)

**Files affected**:
- `oscarEncounter/js/encounter.js` (lines 656-659) — template autocomplete
- `casemgmt/ChartNotesAjax.jsp` (line 1024-1027) — template autocomplete with `afterUpdateElement: menuAction`

**Migration rule**: Replace with vanilla JS autocomplete using `<datalist>` element or custom dropdown. Must preserve:
1. Keyboard navigation (up/down arrows, enter to select, escape to close)
2. `afterUpdateElement` callback with `(input, selectedLI)` parameters
3. Fuzzy/partial matching behavior

### Contract 13: `Position.page()` / `Position.clone()` — Element Positioning

**Prototype behavior**: `Position.page(el)` returns `[x, y]` coordinates relative to the page. `Position.clone(source, target, options)` copies position from one element to another.

**Files affected**:
- `oscarRx/SearchDrug3.jsp` (line 1592) — `Position.page($('drugProfile'))`
- `share/javascript/controls.js` (lines 70, 116) — `Position.clone()` in Autocompleter positioning
- `js/newCaseManagementView.js.jsp` — `Position.page()`, `Position.positionedOffset()`

**Migration rule**: Replace with `getBoundingClientRect()`:
```javascript
// Position.page(el):
const rect = el.getBoundingClientRect();
const pos = [rect.left + window.scrollX, rect.top + window.scrollY];

// Position.positionedOffset(el):
const offset = { left: el.offsetLeft, top: el.offsetTop };
```

### Contract 14: `Element.addClassName()` / `Element.removeClassName()`

**Prototype behavior**: Adds/removes CSS classes, checking for duplicates.

**Files affected**: `share/javascript/controls.js` (8 calls), `share/javascript/slider.js` (2 calls), `js/newCaseManagementView.js.jsp` (1 call)

**Migration rule**: Replace with `element.classList.add()` / `element.classList.remove()`. Exact same semantics — `classList.add()` also prevents duplicates.

### Contract 15: Event Constants and Methods

**Prototype behavior**: `Event.KEY_TAB` (9), `Event.KEY_RETURN` (13), `Event.KEY_ESC` (27), `Event.KEY_UP` (38), `Event.KEY_DOWN` (40). `Event.stop(e)` calls both `preventDefault()` and `stopPropagation()`. `Event.findElement(e, 'LI')` finds ancestor matching selector.

**Files affected**: `share/javascript/controls.js` (lines 139-185 — Autocompleter keyboard handling), `js/newCaseManagementView.js.jsp` (lines 267-268)

**Migration rule**:
```javascript
// Event.KEY_RETURN -> e.key === 'Enter' or e.keyCode === 13
// Event.KEY_ESC    -> e.key === 'Escape' or e.keyCode === 27
// Event.stop(e)    -> e.preventDefault(); e.stopPropagation();
// Event.findElement(e, 'LI') -> e.target.closest('li')
```

### Contract 16: `Prototype.Browser.IE` — Browser Detection

**Prototype behavior**: Boolean flag detecting Internet Explorer.

**Files affected**: `js/newCaseManagementView.js.jsp` (line 802), `share/javascript/controls.js` (IE iframe fix)

**Migration rule**: Remove IE-specific branches entirely. CARLOS EMR does not support Internet Explorer. Replace `if (Prototype.Browser.IE) { ... }` blocks with nothing.

### Contract 17: `Object.extend()` — Object Merging

**Prototype behavior**: Copies all properties from source to destination object (like `Object.assign()`).

**Files affected**: `share/javascript/effects.js` (10+ calls), `share/javascript/controls.js`, `share/javascript/select.js`

**Migration rule**: Replace with `Object.assign(dest, source)`. These are in library files that will be deleted — only relevant if any application code calls `Object.extend()` directly (none found).

### Contract 18: `.invoke()` — Batch Method Invocation on Multiple Elements

**Prototype behavior**: `$$(selector).invoke('show')` calls `.show()` on every matched element.

**Files affected**: `js/newCaseManagementView.js.jsp` (lines 941-947) — batch show/hide of form row elements

**Migration rule**: Replace with explicit loop:
```javascript
// $(rowIDs[2], rowIDs[4], rowIDs[5]).invoke("show"):
[rowIDs[2], rowIDs[4], rowIDs[5]].forEach(id => document.getElementById(id).style.display = '');
```

Note: Prototype's multi-argument `$(id1, id2, id3)` returns an array of elements. The compat shim's `$()` must handle single-ID (returns element) vs multi-ID (returns array) cases, OR these call sites must be rewritten.

### Contract 19: `postBody` vs `parameters` — Request Body Semantics

**Prototype behavior**: `Ajax.Request` accepts either `postBody` (raw string body) or `parameters` (object or string, auto-encoded). When `postBody` is provided, it is sent as-is. When `parameters` is an object, Prototype serializes it to `key=value&key2=value2` and sets Content-Type to `application/x-www-form-urlencoded`.

**Files affected**:
- `oscarEncounter/Index.jsp` (lines 787, 812) — `postBody: pars` (pre-serialized Form.serialize result)
- `js/newCaseManagementView.js.jsp` (line 3006) — `postBody: Form.serialize(frm)`

**Migration rule**: Map both to `fetch()` body:
```javascript
// postBody: direct pass-through
fetch(url, { method: 'POST', body: postBody, headers: {'Content-Type': 'application/x-www-form-urlencoded'} });

// parameters (object): serialize with URLSearchParams
fetch(url, { method: 'POST', body: new URLSearchParams(params), headers: {'Content-Type': 'application/x-www-form-urlencoded'} });
```

### Contract 20: `Element.collectTextNodes()` — DOM Text Extraction

**Prototype behavior**: Extracts all text nodes from an element's children. Used in Autocompleter matching.

**Files affected**: `share/javascript/controls.js` (lines 250-251)

**Migration rule**: Replace with `element.textContent`. This is only used inside the Autocompleter which will be replaced entirely.

---

## Summary: Migration Risk by Contract

| Contract | Severity | Mechanical? | Files |
|----------|----------|-------------|-------|
| CSRF token injection | **CRITICAL** | Yes — CarlosAjax handles | All POST/PUT/DELETE/PATCH |
| CSRF redirect detection | **CRITICAL** | Yes — CarlosAjax handles | All AJAX files |
| `credentials: 'same-origin'` | **CRITICAL** | Yes — set on all fetch() | All AJAX files |
| `X-Requested-With` header | **CRITICAL** | Yes — add to all fetch calls | All AJAX files |
| `Content-Type` default | **CRITICAL** | Yes — set explicitly | All POST requests |
| `Form.serialize()` format | **HIGH** | Yes — URLSearchParams | 7 files, 13+ calls |
| `observe/stopObserving` + `bindAsEventListener` | **HIGH** | No — manual refactor | 5 files, 80+ calls |
| `Insertion.Bottom/Top` | **HIGH** | Yes — insertAdjacentHTML | 3 files, 17+ calls |
| AJAX callback ordering | **HIGH** | No — CarlosAjax handles | All AJAX files |
| `evalScripts: true` | **HIGH** | No — script extraction | 6 files |
| `asynchronous: false` | **HIGH** | No — async/await refactor | 3 files |
| `$()` element chaining | **MEDIUM** | Yes — compat shim | 5 files, 400+ calls |
| `show()`/`hide()` display value | **MEDIUM** | Yes — style.display | 4 files |
| `.evalJSON()` | **LOW** | Yes — JSON.parse() | 8 files, 19 calls |
| `$F()` field access | **LOW** | Yes — .value | 2 files, 62+ calls (62 in newCaseManagementView.js.jsp alone) |
| `$A()` conversion | **LOW** | Yes — Array.from() | 2 files, 7 calls |
| `addClassName/removeClassName` | **LOW** | Yes — classList API | 3 files |
| Event constants | **LOW** | Yes — e.key / e.keyCode | 2 files |
| `Prototype.Browser.IE` | **LOW** | Yes — delete | 2 files |
