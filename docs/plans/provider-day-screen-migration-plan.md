# Provider Day Schedule Screen Migration Plan

## Executive Summary

Migrate the provider day schedule screen (`appointmentprovideradminday.jsp`) — the primary post-login screen in CARLOS EMR — from a monolithic 2400-line JSP with embedded business logic to a proper MVC architecture using Struts 6.8.0 Actions (*2Action pattern), JSTL/EL views, Bootstrap 5, and FullCalendar Standard (MIT, v6.1.x).

> **Struts version note:** CARLOS runs Struts **6.8.0** (upgraded from 2.5.33 in January 2026). The `*2Action.java` naming convention and `com.opensymphony.xwork2.*` imports are retained for backward compatibility. Throughout this plan, "2Action" refers to the codebase's action naming convention, not the Struts major version.

**Goals:**
- Proper MVC separation: all business logic in Action classes, all rendering in JSP with JSTL/EL
- No scriptlets in views (unless truly unavoidable for tag library interop)
- Bootstrap 5 semantic HTML with responsive design
- FullCalendar Standard (MIT) replacing the custom HTML table grid
- Reusable header/navbar JSPF template for rollout to all modules
- Preserve every feature and configurable option of the current screen
- Classic OSCAR/Juno visual familiarity with modern implementation
- Vanilla JS + specific libraries stored locally (no CDN dependencies)

**Non-Goals:**
- Changing the database schema
- Modifying existing REST endpoints (we'll add new ones where needed)
- Migrating popup windows (appointment edit, encounter, billing, etc.) — those remain as-is initially
- Rewriting the CAISI infirmary view (deferred)

---

## URL and State Management Strategy

### Moving Away from URL Query Parameters

The current screen passes **~15 request parameters** via URL query strings (`?year=2026&month=2&day=8&view=0&viewall=1&curProvider=999998&...`). This creates long, fragile URLs and leaks internal state into browser history. The new architecture minimizes URL parameters.

### Approach: Hash-Based Client-Side State

The new day view uses a **hash fragment** strategy (`#`) for client-side navigation state, combined with minimal server-side parameters only where needed:

**What goes in the hash fragment** (client-side state, managed by JavaScript):
```
/provider/Day.do#date=2026-02-08&group=100&site=Main&viewAll=true
```
- `date` — the selected date (default: today)
- `group` — the selected provider group number
- `site` — the selected site (multi-site only)
- `viewAll` — show all providers vs. scheduled only
- `provider` — single provider view (when zoomed)
- `scroll` — scroll position for refresh preservation

**Benefits of hash fragments:**
- Parameters are **never sent to the server** — reduces request complexity
- Browser back/forward navigation works (hashchange events)
- Bookmarkable: users can share/save specific views
- No server-side parsing needed — JavaScript reads `window.location.hash`
- Clean URL: `/provider/Day.do` is the only server route
- Refresh preserves state: hash survives page reload

**JavaScript hash management:**
```javascript
// Read state from hash
function getStateFromHash() {
  const params = new URLSearchParams(window.location.hash.substring(1));
  return {
    date: params.get('date') || new Date().toISOString().split('T')[0],
    group: params.get('group') || config.defaultGroupNo,
    site: params.get('site') || config.defaultSite,
    viewAll: params.get('viewAll') !== 'false',
    provider: params.get('provider') || null,
    scroll: parseInt(params.get('scroll') || '0')
  };
}

// Update hash when user navigates
function updateHash(updates) {
  const state = { ...getStateFromHash(), ...updates };
  // Filter out null/undefined values to prevent them from being serialized
  // as the literal strings "null"/"undefined" in the URL hash.
  const filtered = Object.fromEntries(
    Object.entries(state).filter(([_, v]) => v != null)
  );
  const hash = new URLSearchParams(filtered).toString();
  window.location.hash = hash;
}

// React to hash changes (back/forward button)
window.addEventListener('hashchange', () => {
  const state = getStateFromHash();
  refreshAllCalendars(state);
});
```

**What remains as server-side URL parameters (minimal):**
- Login redirect: `provider/Day.do` with no parameters (action defaults to today)
- Direct links from other pages: `provider/Day.do#date=2026-02-08` (hash only)
- Popup callbacks: `window.opener.refreshSchedule()` (no URL manipulation)

**What goes to the AJAX endpoint as GET query parameters:**
- The `ScheduleDayData2Action` receives its parameters as GET query parameters. This is an **idempotent read-only** operation (no state mutation), so GET is the correct HTTP method. Using GET also avoids CSRF complications (see Security Note below):
```javascript
const params = new URLSearchParams({
  date: state.date,
  groupNo: state.group,
  site: state.site,
  viewAll: state.viewAll,
  providerNo: state.provider
});
fetch(`${config.contextPath}/schedule/DayData.do?${params}`);
```

> **CSRF Security Note**: The CARLOS CSRF Guard (`OscarCsrfGuardFilter` + `Owasp.CsrfGuard.properties`) only protects `POST`, `PUT`, and `DELETE` methods. Furthermore, CSRF Guard's JavaScript integration (`csrfguard.js`) patches `XMLHttpRequest.open()` to inject the CSRF token, but does **NOT patch `fetch()`**. This means:
> 1. **GET requests via `fetch()`**: Safe — CSRF Guard does not protect GET (idempotent reads don't need CSRF protection).
> 2. **POST requests via `fetch()`**: **UNSAFE** — the CSRF token will NOT be automatically injected. You must either:
>    - Manually add the token header: `headers: { 'CSRF-TOKEN': document.querySelector("meta[name='csrf-token']").content }`
>    - Or use `XMLHttpRequest` instead of `fetch()` for any POST/PUT/DELETE operations.
> 3. The existing REST endpoints at `/ws/rs/*` are exempt from CSRF (`unprotected.Rest` pattern in CsrfGuard config), so `fetch()` calls to REST endpoints for status updates are safe.
> 4. The `OscarCsrfGuardFilter` is currently in **log-only mode** (no redirect on violation), but this could change — design for correctness now.

### Backward Compatibility

For links from other pages that still pass query parameters (e.g., `provider/Day.do?year=2026&month=2&day=8`), the `ProviderDaySchedule2Action` converts them to hash fragments on the initial page load:
```java
// In ProviderDaySchedule2Action
String year = request.getParameter("year");
if (year != null) {
    // SECURITY: Validate date parameters before building hash string
    // to prevent hash fragment injection via malicious query parameters.
    if (!year.matches("\\d{4}") || !month.matches("\\d{1,2}") || !day.matches("\\d{1,2}")) {
        throw new SecurityException("Invalid date parameters");
    }
    int m = Integer.parseInt(month);
    int d = Integer.parseInt(day);
    if (m < 1 || m > 12 || d < 1 || d > 31) {
        throw new SecurityException("Date out of range");
    }
    // Set attribute so JSP can emit a redirect-to-hash script
    request.setAttribute("legacyRedirectHash",
        "date=" + year + "-" + month + "-" + day + "&...");
}
```
The JSP then includes:
```jsp
<%-- Note: legacyRedirectHash is pre-sanitized in the Action using Encode.forJavaScript().
     Do NOT use fn:escapeXml() here — it produces HTML entities that break JavaScript strings.
     Alternatively, use a data attribute + JS reader pattern for defense-in-depth. --%>
<c:if test="${not empty legacyRedirectHash}">
  <script>
    if (window.location.search) {
      window.location.replace(
        window.location.pathname + '#' + '${legacyRedirectHash}'
      );
    }
  </script>
</c:if>
```
> **Encoding note**: `fn:escapeXml()` is correct for HTML body/attribute contexts but **wrong** for JavaScript string contexts (it produces `&amp;` instead of `\u0026`). For JavaScript contexts, use `Encode.forJavaScript()` in the Action class before setting the request attribute, or use the data-attribute pattern:
> ```jsp
> <div id="legacy-redirect" data-hash="${fn:escapeXml(legacyRedirectHash)}" class="d-none"></div>
> <script>
>   var el = document.getElementById('legacy-redirect');
>   if (el && el.dataset.hash && window.location.search) {
>     window.location.replace(window.location.pathname + '#' + el.dataset.hash);
>   }
> </script>
> ```
> The data-attribute approach uses `fn:escapeXml()` in its correct context (HTML attribute) and lets JavaScript read the decoded value via `dataset`.

This one-time redirect converts old-style URLs to hash-based URLs transparently.

---

## Current State Analysis

### The Problem

`appointmentprovideradminday.jsp` (2422 lines) is a textbook example of the "God JSP" anti-pattern:

- **15 Spring beans** declared as shared instance variables (thread-safety concern)
- **34+ database queries** per page load, many inside the rendering loop (N+1 per appointment)
- **~15 request parameters** consumed and processed with scriptlet logic
- **~15 session attributes** read/written
- **~300 lines** of inline JavaScript
- **Zero separation** of model, view, and controller concerns
- HTML rendered via deeply nested `<table>` layout with inline styles
- Mix of Bootstrap 3 classes, jQuery, Prototype.js, and inline SVG icons
- Business logic (date math, name formatting, status cycling, security checks, demographic lookups) interleaved with HTML generation

### Files Involved

| File | Role | Lines | Disposition |
|------|------|-------|-------------|
| `provider/providercontrol.jsp` | Router/dispatcher | 179 | Replace with Struts2 action routing |
| `provider/appointmentprovideradminday.jsp` | Day view (the "God JSP") | 2422 | Replace entirely |
| `provider/schedulePage.js.jsp` | JS functions (JSP-generated) | ~200 | Extract to static JS |
| `css/receptionistapptstyle.css` | Schedule CSS | 512 | Replace with Bootstrap 5 + minimal custom CSS |
| `provider/providerheader-classic.jspf` | Unused legacy header | 306 | Delete after new header ships |
| `provider/mainMenu.jsp` | Month view header (duplicate) | 370 | Refactor to use shared header |
| `layouts/nonPatientContextHeader.jspf` | Admin header (another duplicate) | 380 | Refactor to use shared header |
| `js/topnav.js` | Nav JS (cleanest existing version) | ~80 | Evolve into shared nav JS |
| `provider/tabAlertsRefresh.jsp` | AJAX badge counts | ~50 | Keep (works fine) |

### Existing REST Endpoints Available

The audit found **20 existing JSON REST endpoints** under `/ws/rs/schedule/` and related services that the current JSP does NOT use (it queries the database directly). Key ones for this migration:

| Endpoint | Method | Purpose | Gap? |
|----------|--------|---------|------|
| `/ws/rs/schedule/{providerNo}/day/{date}` | GET | Day appointments for a provider | Returns `PatientListApptItemBean` — **missing**: template codes, slot colors, demographic details, ticklers, alerts |
| `/ws/rs/schedule/fetchDays/{sDate}/{eDate}/{providers}` | GET | Multi-provider date range with demographics | **Closest match** — has demographic name/phone but **missing**: status colors, template codes, tickler/alert indicators |
| `/ws/rs/schedule/statuses` | GET | All appointment statuses with colors/icons | Sufficient |
| `/ws/rs/schedule/codes` | GET | Schedule template codes | Sufficient |
| `/ws/rs/schedule/reasons` | GET | Appointment reason codes | Sufficient |
| `/ws/rs/schedule/types` | GET | Appointment types | Sufficient |
| `/ws/rs/schedule/appointment/{id}/updateStatus` | POST | Update appointment status | Sufficient for AJAX status cycling |
| `/ws/rs/providerService/providers_json` | GET | Active providers list | Sufficient |
| `/ws/rs/demographics/summary/{id}` | GET | Quick patient summary | Useful for tooltips |
| `/ws/rs/tickler/{demoNo}/count/overdue` | GET | Overdue tickler count | Useful for indicators |
| `provider/tabAlertsRefresh.jsp?id=...` | GET | Navbar badge counts (HTML fragment) | Keep as-is |

**REST Endpoint Notes:**
- The `GET /ws/rs/scheduleTempCode/get` endpoint in `ScheduleTemplateCodeService.java` is **commented out** (dead code). The live equivalent is `GET /ws/rs/schedule/codes` — this is the one listed above.
- `updateAppointmentUrgency` in `ScheduleService.java` is missing its `@POST` annotation (existing bug — not blocking for this migration, but should be fixed).
- The `/ws/rs/*` path is exempt from CSRF Guard protection (`unprotected.Rest` pattern), so `fetch()` calls to these REST endpoints work without CSRF tokens.
- REST endpoints support **dual auth**: session-based at `/ws/rs/schedule/` and OAuth 1.0a at `/ws/services/schedule/`. This migration uses the session-based path exclusively.

### Key Gap: No Single "Day View Data" Endpoint

The current JSP performs 34+ queries to assemble the complete day view data model. No single existing REST endpoint provides all of this in one call. We need a **new dedicated endpoint** that returns:

1. Provider schedule template (time slots with codes and colors)
2. Appointments for the day with status colors and icons
3. Per-appointment indicators (tickler count, alert flag, notes flag, version check, roster status, prevention warnings, birthday)
4. Appointment count (excluding cancelled/no-show)
5. Provider availability (scheduled vs. unscheduled)

---

## Architecture Overview

### New File Structure

```
src/main/java/.../provider/web/
├── ProviderDaySchedule2Action.java        # NEW: Main Struts2 action (MVC controller)
└── (existing actions remain unchanged)

src/main/java/.../web/filter/
└── RoleNameFilter.java                    # NEW: Servlet filter for header attributes

src/main/java/.../schedule/
├── web/
│   └── ScheduleDayData2Action.java         # NEW: AJAX JSON endpoint for calendar data
└── dto/
    ├── DayViewResponse.java                # NEW: Top-level JSON response DTO
    ├── ProviderScheduleDto.java            # NEW: Per-provider schedule + appointments
    ├── AppointmentSlotDto.java             # NEW: Individual appointment for FullCalendar
    ├── TemplateSlotDto.java                # NEW: Background event for slot coloring
    ├── TemplateCodeDto.java                # NEW: Template code metadata (one per code char)
    └── StatusDto.java                      # NEW: Status rendering metadata

src/main/webapp/
├── WEB-INF/
│   ├── classes/struts.xml                  # MODIFY: Add new action mappings
│   └── views/                              # NEW: Protected JSP directory
│       ├── common/
│       │   ├── header.jspf                 # NEW: Reusable Bootstrap 5 navbar
│       │   ├── head-includes.jspf          # NEW: Shared CSS/JS includes
│       │   └── footer.jspf                 # NEW: Shared footer/scripts
│       └── provider/
│           └── providerDaySchedule.jsp     # NEW: Main day view (JSTL/EL, no scriptlets)
├── provider/
│   └── providercontrol.jsp                 # MODIFY: Route "day" to new action
├── js/
│   ├── schedule/
│   │   ├── provider-day-schedule.js        # NEW: FullCalendar initialization + event handling
│   │   ├── schedule-utils.js               # NEW: Shared schedule utilities
│   │   └── nav-alerts.js                   # NEW: Evolved from topnav.js
│   └── vendor/
│       ├── fullcalendar/
│       │   ├── index.global.min.js         # FullCalendar 6.1.x bundle (includes CSS — NO separate .css file needed)
│       │   └── bootstrap5.global.min.js    # FullCalendar Bootstrap 5 theme plugin
│       └── (bootstrap JS already available)
├── css/
│   ├── carlos-common.css                   # NEW: Shared base styles
│   └── schedule/
│       └── provider-day-schedule.css       # NEW: Schedule-specific overrides
└── library/
    └── bootstrap/
        └── 5.3.3/                          # UPGRADE: Bootstrap 5.3.3 (from 5.0.2)
            ├── css/bootstrap.min.css
            ├── js/bootstrap.bundle.min.js
            └── icons/
                ├── bootstrap-icons.min.css  # NEW: Bootstrap Icons CSS
                └── fonts/
                    ├── bootstrap-icons.woff2  # NEW: Icon font (required by CSS)
                    └── bootstrap-icons.woff   # NEW: Icon font fallback
```

### WEB-INF Protection: Incremental Adoption Strategy

**The codebase currently places all JSPs in public webapp directories.** This migration introduces the `WEB-INF/views/` pattern for new files, establishing a convention that future module migrations will follow.

**Why this works incrementally:**
- Struts2 actions forward to `WEB-INF/views/` paths — the servlet container handles the internal dispatch
- Old public JSPs continue working unchanged — no mass migration needed
- Shared JSPFs in `WEB-INF/views/common/` are accessible via `<jsp:include>` from ANY JSP (the include is server-side, so WEB-INF paths resolve correctly)
- Each module migration moves its new JSP into `WEB-INF/views/` as part of its rewrite — the old public JSP stays as a fallback until validation

**How old pages use the new shared header:**
```jsp
<%-- This works from a public JSP like /provider/appointmentprovideradminmonth.jsp --%>
<jsp:include page="/WEB-INF/views/common/header.jspf" />
```
The `<jsp:include>` is a server-side RequestDispatcher forward — it can reach WEB-INF because it never goes through the public URL resolution path. The browser never sees or accesses the JSPF directly.

**Migration progression:**
```
Today:      100% public JSPs, 0% WEB-INF
Phase 1:    Shared templates in WEB-INF/views/common/ (header, footer, head-includes)
Phase 2-4:  Schedule day view in WEB-INF/views/provider/
Next module: WEB-INF/views/<module>/
...
Eventually: Most active JSPs in WEB-INF/views/, legacy JSPs still public
```

**What stays public (must be directly URL-accessible):**
- Static resources: CSS, JS, images, vendor libraries
- Legacy JSPs that are still accessed directly (until each is migrated)
- `providercontrol.jsp` (the router — until it's replaced by the Action)

### Data Flow

```
                     Browser                                       Server
                  ┌──────────┐                              ┌──────────────────┐
  1. Initial   →  │ GET       │  ──── Struts2 ────────────→ │ ProviderDay-     │
     Page Load    │ provider/ │                              │ Schedule2Action   │
                  │ Day.do    │  ←── JSP (HTML shell) ─────  │ (prepares model  │
                  │           │      with config data,       │  attributes for  │
                  │           │      security flags,         │  JSP rendering)  │
                  │           │      FullCalendar init       │                  │
                  └──────────┘                              └──────────────────┘
                       │
  2. Calendar   →  ┌──────────┐                              ┌──────────────────┐
     Data Load    │ GET       │  ──── Struts2/JSON ───────→ │ ScheduleDay-     │
     (AJAX)       │ schedule/ │                              │ Data2Action       │
                  │ DayData.do│  ←── JSON ─────────────────  │ (assembles full  │
                  │           │      { providers: [{         │  day view data   │
                  │           │         slots: [...],        │  from managers)  │
                  │           │         appointments: [...], │                  │
                  │           │      }] }                    │                  │
                  └──────────┘                              └──────────────────┘
                       │
  3. Status     →  ┌──────────┐                              ┌──────────────────┐
     Click        │ POST      │  ──── REST ────────────────→ │ ScheduleService  │
     (AJAX)       │ ws/rs/    │                              │ .updateStatus()  │
                  │ schedule/ │  ←── JSON ─────────────────  │ (existing REST   │
                  │ appt/{id}/│      { appointment: {...} }  │  endpoint)       │
                  │ updateSts │                              │                  │
                  └──────────┘                              └──────────────────┘
```

**Key architectural decision**: The initial page load delivers an HTML shell with configuration data as `data-*` attributes or a `<script>` block with a JSON config object. FullCalendar then fetches the actual schedule data via AJAX. This gives us:
- Fast initial page load (no 34+ queries blocking render)
- Clean separation: Action prepares config/security context, AJAX endpoint provides data
- FullCalendar's built-in event refetching for real-time updates

---

## Phase 1: Foundation — Reusable Header Template and Shared Assets

**Objective**: Create the reusable header/navbar JSPF and shared CSS/JS infrastructure that all future module migrations will use.

### Phase 1.1: Bootstrap 5 and Vendor Library Setup

**Files to create/modify:**

1. **Download and store FullCalendar locally**
   - `src/main/webapp/js/vendor/fullcalendar/index.global.min.js` — from `fullcalendar` npm package (free/standard bundle — includes core, dayGrid, timeGrid, list, interaction, multiMonth plugins; all auto-register, no `plugins` array needed in JS)
   - `src/main/webapp/js/vendor/fullcalendar/bootstrap5.global.min.js` — from `@fullcalendar/bootstrap5` npm package (separate package, NOT included in the main bundle; free/standard, not premium). **NOTE**: The source package also names its file `index.global.min.js` — rename to `bootstrap5.global.min.js` to avoid collision with the main bundle in the same directory. Load via `<script>` tag AFTER the main bundle; auto-registers. Required for `themeSystem: 'bootstrap5'`
   - **No separate FullCalendar CSS file needed** — FullCalendar 6.x injects its own CSS via JavaScript at runtime. Only include Bootstrap 5's own CSS and Bootstrap Icons CSS.

2. **Upgrade Bootstrap to 5.3.3**
   - `src/main/webapp/library/bootstrap/5.3.3/css/bootstrap.min.css`
   - `src/main/webapp/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js`
   - `src/main/webapp/library/bootstrap/5.3.3/icons/bootstrap-icons.min.css`
   - Include the `bootstrap-icons.woff2` font file

3. **Create shared base CSS** — `src/main/webapp/css/carlos-common.css`
   - CSS custom properties (variables) for the CARLOS color palette:
     - `--carlos-primary: #486ebd` (the classic OSCAR blue)
     - `--carlos-nav-bg: #ffffff`
     - `--carlos-nav-hover: #486ebd`
     - `--carlos-nav-text: #00283c`
     - `--carlos-input-bg: #F4EaD7`
     - `--carlos-input-border: #0097cf`
     - `--carlos-time-00: #3EA4E1` (on-the-hour slot color)
     - `--carlos-time-not00: #00A488` (off-hour slot color)
     - `--carlos-schedule-bg: #486ebd` (schedule grid background)
   - Global reset/normalization rules
   - Form input styling (the `#F4EaD7` background, `#0097cf` border — classic OSCAR look)
   - Print styles (`.noprint`)
   - Alert badge styles (`.tabalert`)

### Phase 1.2: Reusable Header JSPF

**File**: `src/main/webapp/WEB-INF/views/common/header.jspf`

This is the single-source-of-truth navigation bar, replacing the 4 current copies. Design decisions:

**HTML structure** (Bootstrap 5 navbar):
```html
<nav class="navbar navbar-expand-lg navbar-light bg-white border-bottom fixed-top carlos-nav"
     data-context-path="${pageContext.request.contextPath}"
     style="height: 30px; min-height: 30px;">
  <div class="container-fluid px-2">
    <!-- Logo -->
    <a class="navbar-brand p-0" href="${pageContext.request.contextPath}/provider/Day.do">
      <img src="${pageContext.request.contextPath}/images/carlos-logo.png" height="22" alt="CARLOS">
    </a>

    <!-- Collapsible nav items -->
    <div class="collapse navbar-collapse" id="carlosMainNav">
      <ul class="navbar-nav me-auto">
        <!-- Each item gated by <security:oscarSec> -->
        <!-- Schedule, Caseload, Resources, Search, Reports, Billing,
             Lab/Inbox, Messages, Consultations, eConsult, eDocs,
             Tickler, Workflow, Admin, Dashboards, Help -->
      </ul>

      <!-- Right side: user settings -->
      <ul class="navbar-nav">
        <!-- Scratch Pad, Preferences, Username, Logout -->
      </ul>
    </div>
  </div>
</nav>
```

**Key design requirements:**

1. **Security gating**: Use `<security:oscarSec>` tags exactly as today. The tag requires `roleName` as a runtime expression (`rtexprvalue`), so we need ONE small scriptlet block at the top of header.jspf (or have the including Action set it as a request attribute):
   ```jsp
   <%-- This is the ONLY scriptlet needed — sets roleName for security tags --%>
   <%
     String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
     request.setAttribute("roleName", roleName$);
   %>
   ```
   Then use: `<security:oscarSec roleName="${roleName}" objectName="_billing" rights="r">`

   **Alternative (preferred)**: Move `roleName$` assembly into a servlet filter or Struts2 interceptor that always sets `request.setAttribute("roleName", ...)` before any JSP renders. Then header.jspf has zero scriptlets.

2. **Badge/alert system**: Keep `tabAlertsRefresh.jsp` and evolve the AJAX refresh into a shared `nav-alerts.js`:
   ```javascript
   // nav-alerts.js — loaded by header.jspf
   // contextPath is read from a data attribute set by header.jspf:
   //   <nav ... data-context-path="${pageContext.request.contextPath}">
   document.addEventListener('DOMContentLoaded', () => {
     refreshAllTabAlerts();
     setInterval(refreshAllTabAlerts, 30000); // 30-second polling
   });

   function getContextPath() {
     var nav = document.querySelector('.carlos-nav');
     return nav ? nav.dataset.contextPath : '';
   }

   function refreshAllTabAlerts() {
     ['oscar_new_lab', 'oscar_new_msg', 'oscar_new_tickler',
      'oscar_aged_consults', 'oscar_scratch'].forEach(refreshTabAlert);
   }

   function refreshTabAlert(id) {
     fetch(getContextPath() + '/provider/tabAlertsRefresh.jsp?id=' + encodeURIComponent(id))
       .then(function(r) { return r.text(); })
       .then(function(html) {
         var el = document.getElementById(id);
         if (el) {
           // tabAlertsRefresh.jsp returns trusted HTML fragments (badge counts).
           // This is server-generated HTML, not user input. Using innerHTML is
           // acceptable here because the JSP is a server-controlled template.
           // If this is ever changed to accept user input, switch to textContent.
           el.innerHTML = html;
         }
       });
   }
   ```

3. **Active page highlighting**: The including page passes a parameter:
   ```jsp
   <c:set var="activeNav" value="schedule" scope="request" />
   <jsp:include page="/WEB-INF/views/common/header.jspf" />
   ```
   The header uses: `<li class="nav-item ${activeNav == 'schedule' ? 'active' : ''}">`

4. **Module-conditional items**: Preserve `<caisi:isModuleLoad>`, `<oscar:oscarPropertiesCheck>` tags for eConsult, Workflow, Referrals, etc.

5. **Multi-site support**: The site dropdown (with color-coded backgrounds) is schedule-specific and belongs in the schedule sub-navigation, NOT in the shared header.

6. **Compact height**: Match the current 25-30px navbar height. Override Bootstrap defaults:
   ```css
   .carlos-nav { height: 30px; min-height: 30px; }
   .carlos-nav .nav-link { padding: 2px 6px; font-size: 12px; font-weight: bold; }
   .carlos-nav .nav-link:hover { background-color: var(--carlos-nav-hover); color: white; }
   ```

7. **Keyboard shortcuts**: Maintain all current Alt+key shortcuts. Move to a data-attribute pattern in the header and a small shared `keyboard-shortcuts.js`.

### Phase 1.3: Shared Head Includes and Footer

**`src/main/webapp/WEB-INF/views/common/head-includes.jspf`**:
```jsp
<%-- Common CSS --%>
<link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/icons/bootstrap-icons.min.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/carlos-common.css">
<%-- CSRF Guard --%>
<script src="${pageContext.request.contextPath}/csrfguard"></script>
```

**`src/main/webapp/WEB-INF/views/common/footer.jspf`**:
```jsp
<%-- Common JS --%>
<script src="${pageContext.request.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/js/schedule/nav-alerts.js"></script>
<script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
```

### Phase 1.4: RoleName Servlet Filter (Eliminates Scriptlets from Header)

**File**: `src/main/java/.../web/filter/RoleNameFilter.java`

> **Design decision: Servlet Filter (not Struts interceptor).** The struts.xml currently defines no custom interceptor stacks — all actions inherit `defaultStack` from `struts-default`. Adding a custom interceptor stack would require modifying every action mapping or creating a new package. A servlet Filter registered in `web.xml` is lower-risk: it runs for all requests (including non-Struts JSPs that include the header), requires no Struts configuration changes, and benefits old pages that `<jsp:include>` the shared header.

A servlet filter that runs before every request and sets request attributes for the shared header:
- `request.setAttribute("roleName", ...)` — for `<security:oscarSec>` tags
- `request.setAttribute("curProviderNo", ...)` — logged-in provider number
- `request.setAttribute("curProviderName", ...)` — display name
- `request.setAttribute("contextPath", ...)` — context path

**Registration in `web.xml`** (after authentication filter, before Struts filter):
```xml
<filter>
    <filter-name>RoleNameFilter</filter-name>
    <filter-class>io.github.carlos_emr.carlos.web.filter.RoleNameFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>RoleNameFilter</filter-name>
    <url-pattern>*.do</url-pattern>
</filter-mapping>
<filter-mapping>
    <filter-name>RoleNameFilter</filter-name>
    <url-pattern>*.jsp</url-pattern>
</filter-mapping>
```

This filter only sets attributes if a valid session exists (no security check — it just reads session values that the authentication filter already validated).

---

## Phase 2: Day Schedule Data Endpoint (The JSON API)

**Objective**: Create a Struts2 action that returns JSON data for FullCalendar to consume, assembling the complete day view data model in one call.

### Phase 2.1: Data Transfer Objects

**`DayViewResponse.java`** — top-level JSON response:
```java
public class DayViewResponse {
    private String date;                           // ISO date
    private List<ProviderScheduleDto> providers;   // One per provider column
    private Map<String, StatusDto> statuses;        // Status code -> rendering info
    private Map<Character, TemplateCodeDto> templateCodes; // Code char -> color/desc
    // NOTE: No ConfigDto here — config is passed via ProviderDaySchedule2Action
    // as a pre-serialized JSON string in the JSP, NOT in the AJAX data response.
}
```

**`ProviderScheduleDto.java`** — per-provider data:
```java
public class ProviderScheduleDto {
    private String providerNo;
    private String providerName;
    private String providerColor;             // Provider's configured color
    private boolean available;                // Has schedule for this day
    private int appointmentCount;             // Excluding cancelled/no-show
    private int slotDurationMinutes;          // Template slot duration
    private String startTime;                 // "08:00"
    private String endTime;                   // "17:00"
    private List<TemplateSlotDto> slots;      // Background events (template coloring)
    private List<AppointmentSlotDto> appointments; // Foreground events
}
```

**`AppointmentSlotDto.java`** — maps to FullCalendar event:
```java
public class AppointmentSlotDto {
    // FullCalendar standard fields
    private int id;                     // appointment_no
    private String title;               // Patient name (formatted)
    private String start;               // ISO datetime "2026-02-08T09:00:00"
    private String end;                 // ISO datetime
    private String backgroundColor;     // From status
    private String borderColor;
    private String textColor;

    // Extended properties (FullCalendar extendedProps)
    private int demographicNo;
    private String status;              // Status code string
    private String statusIcon;          // Icon filename
    private String nextStatus;          // Next status in cycle
    private String shortLetters;        // Status short letters
    private String shortLetterColor;

    // Patient indicators
    private boolean hasTickler;
    private boolean hasAlert;
    private boolean hasNotes;
    private boolean versionMismatch;    // Health card version check
    private String rosterStatus;        // FS, NR, PL, etc.
    private boolean hasPrevWarning;     // Prevention stop sign
    private boolean isBirthday;

    // Appointment details
    private String reason;
    private String reasonCode;          // Appointment reason code (for encounter URL)
    private String reasonCodeLabel;
    private String type;
    private String notes;               // Parsed from XML via SxmlMisc.getXmlContent("<unotes>")
    private String urgency;
    private String billing;
    private String location;
    private String resources;
    private String alias;               // Patient alias (from Demographic)
    private String pronoun;             // Patient pronoun (from Demographic)

    // Provider context
    private String providerNo;
    private String siteName;            // Multi-site
    private String siteColor;           // Multi-site color

    // Quick links context — URLs built server-side in ScheduleDayData2Action
    private String encounterUrl;
    private String billingUrl;
    private String billingMode;        // "create" | "edit" | "delete" — controls B link label
    private String masterUrl;
    private String rxUrl;
}
```

**`TemplateSlotDto.java`** — maps to FullCalendar background event:
```java
public class TemplateSlotDto {
    private String start;           // ISO datetime
    private String end;             // ISO datetime
    private String display;         // "background"
    private String backgroundColor; // From ScheduleTemplateCode.color
    private char code;              // Template code character
    private String description;     // Template code description
    private String duration;        // Default appointment duration (minutes) from ScheduleTemplateCode
    private String confirm;         // Booking confirmation requirement ("Yes", "Day", "Wk", "Onc", or "")
    private int bookingLimit;       // Max bookings per slot (0 = unlimited)
    // NOTE: ScheduleTemplateCode entity getter is getBookinglimit() (lowercase 'l').
    // Manual mapping needed: dto.setBookingLimit(entity.getBookinglimit())
}
```

**`TemplateCodeDto.java`** — template code metadata (one per code character):
```java
public class TemplateCodeDto {
    private char code;              // 'A', 'B', 'C', etc.
    private String description;     // "Available", "Booked", etc.
    private String duration;        // Default appointment duration (minutes)
    private String color;           // Background color hex
    private String confirm;         // Booking confirmation requirement
    private int bookingLimit;       // Max bookings per slot
}
```

**`StatusDto.java`** — status rendering metadata:
```java
public class StatusDto {
    private String code;            // "t", "T", "H", "P", "E", "N", "C", "B", etc.
    private String description;     // "To Do", "Here", "Picked", etc.
    private String bgColor;         // Background color hex
    private String icon;            // Image filename (e.g., "todo.gif")
    private String shortLetters;    // 1-2 char abbreviation
    private String shortLetterColor;// Color for short letter text
    private String nextStatus;      // Next status in cycle ("" for terminal B/BS/BV)
    private String title;           // Tooltip text for status icon
}
```

> **Note**: `ProviderHeaderDto.java` listed in the original file structure was redundant — its fields are already included in `ProviderScheduleDto`. Removed from the file inventory. `ConfigDto` was also redundant — the page action passes config as a serialized JSON string via `scheduleConfigJson`, and the AJAX data endpoint does not return config data.

### Phase 2.2: ScheduleDayData2Action

**File**: `src/main/java/.../schedule/web/ScheduleDayData2Action.java`

```java
public class ScheduleDayData2Action extends ActionSupport {
    // Field-level request/response — matches codebase convention for all *2Action classes.
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    // Spring beans (via SpringUtils.getBean — matches codebase convention)
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ScheduleManager scheduleManager = SpringUtils.getBean(ScheduleManager.class);
    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);
    private UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
    // NOTE: AppointmentStatusMgr is NOT a Spring-managed bean (no @Service annotation).
    // Instantiate directly, matching existing pattern in AppointmentStatus2Action.
    private AppointmentStatusMgr appointmentStatusMgr = new AppointmentStatusMgrImpl();
    private ScheduleTemplateCodeDao scheduleTemplateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
    private ScheduleDateDao scheduleDateDao = SpringUtils.getBean(ScheduleDateDao.class);
    private MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    private SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
    private LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);

    public String execute() {

        // Read parameters via request.getParameter() — this is the codebase convention.
        // Struts2 property injection (getter/setter) is NOT used in this codebase.
        String date = request.getParameter("date");          // yyyy-MM-dd
        String groupNo = request.getParameter("groupNo");
        String site = request.getParameter("site");          // Multi-site filter
        boolean viewAll = "true".equals(request.getParameter("viewAll"));

        // 1. Security check
        // 2. Resolve provider list (from group, site, viewAll)
        // 3. For each provider:
        //    a. Get DayWorkSchedule (template slots)
        //    b. Get appointments
        //    c. For each appointment, enrich with:
        //       - Status rendering (via ApptStatusData)
        //       - Demographic data (name, alias, pronouns, HIN version, roster)
        //       - Tickler count
        //       - Alert/notes from DemographicCust
        //       - Prevention warnings
        //       - Birthday check
        // 4. Assemble DayViewResponse
        // 5. Write JSON to response
    }
}
```

**Critical performance consideration**: The current JSP makes N+1 queries per appointment (demographic lookup, tickler search, alert lookup, prevention warnings). The new action should:
- Batch-load demographics for all appointment demographic_nos in one query
- Batch-load tickler counts
- Batch-load DemographicCust records
- Batch-load prevention warnings
- This should reduce 34+ queries to ~8-10 queries regardless of appointment count

**Quick-link URL construction** (in the per-appointment enrichment loop):

Each `AppointmentSlotDto` needs 4 pre-built URLs. These MUST be constructed server-side (not client-side) because they require data not available in the DTO:

```java
// Encounter URL — matches current JSP lines 2070-2091
String eURL = request.getContextPath() + "/oscarEncounter/IncomingEncounter.do"
    + "?providerNo=" + curProviderNo
    + "&appointmentNo=" + appointment.getId()
    + "&demographicNo=" + demographicNo
    + "&curProviderNo=" + curProviderNo
    + "&reason=" + URLEncoder.encode(reason, "UTF-8")
    + "&reasonCode=" + URLEncoder.encode(reasonCode != null ? reasonCode : "", "UTF-8")
    + "&encType=" + encType
    + "&userName=" + URLEncoder.encode(userName, "UTF-8")
    + "&curDate=" + dateStr
    + "&appointmentDate=" + dateStr
    + "&startTime=" + startTime
    + "&status=" + status
    + "&providerview=" + providerNo;

// Master URL — matches current JSP line 2136
String masterURL = request.getContextPath()
    + "/demographic/demographiccontrol.jsp"
    + "?demographic_no=" + demographicNo
    + "&apptProvider=" + providerNo
    + "&appointment=" + appointment.getId()
    + "&displaymode=edit&dboperation=search_detail";

// Rx URL — matches current JSP line 2146
String rxURL = request.getContextPath()
    + "/oscarRx/choosePatient.do"
    + "?providerNo=" + loggedInProviderNo
    + "&demographicNo=" + demographicNo;

// Billing URL — THREE MODES based on status (matches JSP lines 2107-2124)
String billingMode;
String billingURL;
if (status.indexOf('B') < 0) {
    // No billing yet → Create billing
    billingMode = "create";
    billingURL = request.getContextPath() + "/billing.do"
        + "?billRegion=" + URLEncoder.encode(billingRegion, "UTF-8")
        + "&billForm=" + URLEncoder.encode(defaultBillView, "UTF-8")
        + "&appointment_no=" + appointment.getId()
        + "&demographic_name=" + URLEncoder.encode(patientName, "UTF-8")
        + "&status=" + status
        + "&demographic_no=" + demographicNo
        + "&providerview=" + providerNo
        + "&user_no=" + loggedInProviderNo
        + "&apptProvider_no=" + providerNo
        + "&appointment_date=" + dateStr
        + "&start_time=" + startTime
        + "&hotclick=&xml_provider="
        + "&bNewForm=1";
// NOTE: caisiPref is loaded from userPropertyDao: caisiBillingPreferenceNotDelete
} else if ("ON".equals(billingRegion) && "1".equals(caisiPref)) {
    // Ontario + CAISI → Edit billing (no delete allowed)
    billingMode = "edit";
    billingURL = request.getContextPath()
        + "/billing/CA/ON/billingEditWithApptNo.jsp"
        + "?appointment_no=" + appointment.getId()
        + "&demographic_no=" + demographicNo + "...";
} else {
    // Billing exists → Delete/Rebill
    billingMode = "delete";
    billingURL = request.getContextPath()
        + "/billing/CA/" + billingRegion
        + "/billingDeleteWithoutNo.jsp"
        + "?status=" + status
        + "&appointment_no=" + appointment.getId();
}

dto.setEncounterUrl(eURL);
dto.setMasterUrl(masterURL);
dto.setRxUrl(rxURL);
dto.setBillingUrl(billingURL);
dto.setBillingMode(billingMode);
```

### Phase 2.3: REST Endpoint for Drag-and-Drop / Resize (Reschedule)

Add a new **slim REST endpoint** specifically for time/duration changes from FullCalendar. This is separate from the existing `POST /schedule/updateAppointment` (which has a **known bug**: `AppointmentConverter.getAsDomainObject()` returns `null` — this causes NPE, making the endpoint unusable). The new endpoint only modifies time fields — it's a targeted, safe mutation.

**File**: Add to `ScheduleService.java` (existing REST service)

**Required new field declarations** (add to `ScheduleService` alongside existing `@Autowired` fields):
```java
@Autowired
private OscarAppointmentDao appointmentDao;
@Autowired
private AppointmentArchiveDao appointmentArchiveDao;
```

```java
/**
 * Reschedule an appointment — update only time/date fields.
 * Used by FullCalendar drag-and-drop (eventDrop) and resize (eventResize).
 *
 * Only modifies: appointmentDate, startTime, endTime, lastUpdateUser, updateDateTime.
 * Does NOT modify: status, demographic, notes, reason, billing, etc.
 *
 * Archives the old appointment before updating (audit trail).
 *
 * @param id  appointment_no
 * @param payload  JSON with startTime ("HH:mm"), endTime ("HH:mm"), appointmentDate ("yyyy-MM-dd")
 */
@POST
@Path("/appointment/{id}/reschedule")
@Consumes("application/json")
@Produces("application/json")
public Response rescheduleAppointment(@PathParam("id") Integer id, Map<String, String> payload) {
    LoggedInInfo loggedInInfo = getLoggedInInfo();

    // Security check — requires write access to _appointment
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
        return Response.status(Response.Status.FORBIDDEN).build();
    }

    // Load existing appointment
    Appointment existing = appointmentDao.find(id);
    if (existing == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    // Don't allow rescheduling billed appointments
    String status = existing.getStatus();
    if (status != null && (status.equals("B") || status.equals("BS") || status.equals("BV"))) {
        return Response.status(Response.Status.CONFLICT)
            .entity("{\"error\":\"Cannot reschedule a billed appointment\"}")
            .build();
    }

    // Parse new times
    String startTimeStr = payload.get("startTime");   // "14:30"
    String endTimeStr = payload.get("endTime");         // "15:00"
    String dateStr = payload.get("appointmentDate");    // "2026-02-08"

    // Validate format
    if (startTimeStr == null || endTimeStr == null || dateStr == null
        || !startTimeStr.matches("\\d{2}:\\d{2}")
        || !endTimeStr.matches("\\d{2}:\\d{2}")
        || !dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Invalid time format\"}")
            .build();
    }

    // Archive old appointment (audit trail — matches existing update pattern)
    appointmentArchiveDao.archiveAppointment(existing);

    // Update only time fields
    SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat timeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    try {
        Date appointmentDate = dateFmt.parse(dateStr);
        Date startTime = timeFmt.parse(dateStr + " " + startTimeStr);
        Date endTime = timeFmt.parse(dateStr + " " + endTimeStr);

        existing.setAppointmentDate(appointmentDate);
        existing.setStartTime(startTime);
        existing.setEndTime(endTime);
    } catch (ParseException e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Could not parse date/time\"}")
            .build();
    }

    existing.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
    existing.setUpdateDateTime(new Date());

    appointmentDao.merge(existing);

    LogAction.addLogSynchronous(loggedInInfo, "ScheduleService.rescheduleAppointment",
        "id=" + id + ",start=" + startTimeStr + ",end=" + endTimeStr);

    return Response.ok("{\"success\":true}").build();
}
```

**Key design decisions:**
- **Slim endpoint** — only updates time fields, not the full appointment. Reduces risk of accidental data loss from a drag gesture.
- **Billed check** — refuses to reschedule billed appointments (status B/BS/BV). The client also checks this, but the server is the authority.
- **Archive before update** — matches the existing `AppointmentManagerImpl.updateAppointment()` pattern. Every time change is auditable.
- **No conflict checking** — same as the current system. The current JSP allows double-booking; conflict detection happens in addappointment.jsp only. We match that behavior. (Conflict checking could be added as a future enhancement.)
- **At `/ws/rs/schedule/appointment/{id}/reschedule`** — CSRF-exempt (under `/ws/rs/*` pattern), so `fetch()` works without CSRF tokens.

### Phase 2.4: Struts Configuration and JSON Response Pattern

**JSON convention in this codebase:** There is no struts2-json-plugin. All 143+ JSON-returning actions write directly to `response.getWriter()` using Jackson's `ObjectMapper` and return `null` (which tells Struts to skip result processing). This is the established pattern we follow:

```java
// In ScheduleDayData2Action.execute()
DayViewResponse data = buildDayViewResponse(...);

response.setContentType("application/json");
response.setCharacterEncoding("UTF-8");
new ObjectMapper().writeValue(response.getWriter(), data);
return null;  // Struts skips result mapping — response already committed
```

Add to `struts.xml` (no `<result>` elements needed since the action always returns `null`):
```xml
<action name="schedule/DayData"
        class="io.github.carlos_emr.carlos.schedule.web.ScheduleDayData2Action" />
```

---

## Phase 3: The Struts2 Page Action

**Objective**: Create the Struts2 action that prepares the model for the JSP view (configuration, security context, user preferences — NOT the schedule data itself).

### Phase 3.1: ProviderDaySchedule2Action

**File**: `src/main/java/.../provider/web/ProviderDaySchedule2Action.java`

This action handles the initial page load. It does NOT query schedule/appointment data (that's the AJAX endpoint's job). It prepares:

```java
public class ProviderDaySchedule2Action extends ActionSupport {

    public String execute() {
        // Security: verify _appointment,_day read access
        // Then set request attributes for the JSP:

        // 1. Date context
        // NOTE: <fmt:formatDate> only accepts java.util.Date, NOT java.time.LocalDate.
        // Convert LocalDate to Date for JSTL, or pass a pre-formatted string.
        request.setAttribute("scheduleDate",
            java.util.Date.from(resolvedDate.atStartOfDay(
                java.time.ZoneId.systemDefault()).toInstant()));  // java.util.Date for <fmt:formatDate>
        request.setAttribute("scheduleDateStr", resolvedDate.toString()); // ISO string for JS
        request.setAttribute("year", year);
        request.setAttribute("month", month);
        request.setAttribute("day", day);

        // 2. View preferences
        request.setAttribute("view", view);           // 0=multi, 1=single
        request.setAttribute("viewAll", viewAll);      // show all providers?
        request.setAttribute("myGroupNo", myGroupNo);  // current group

        // 3. Provider preferences (from ProviderPreference)
        request.setAttribute("startHour", startHour);
        request.setAttribute("endHour", endHour);
        request.setAttribute("everyMin", everyMin);
        request.setAttribute("quickLinks", quickLinks);
        request.setAttribute("formLinks", formLinks);
        request.setAttribute("eFormLinks", eFormLinks);
        request.setAttribute("hideOldEchartLink", hideOldEchartLink);
        request.setAttribute("patientNameLength", patientNameLength);

        // 4. Security flags (pre-computed booleans for JSTL)
        request.setAttribute("hasBillingRights", hasBillingRights);
        request.setAttribute("hasDoctorLinkRights", hasDoctorLinkRights);
        request.setAttribute("hasMasterLinkRights", hasMasterLinkRights);
        request.setAttribute("hasResourceRights", hasResourceRights);
        request.setAttribute("hasSearchRights", hasSearchRights);
        request.setAttribute("hasReportRights", hasReportRights);
        request.setAttribute("hasTicklerRights", hasTicklerRights);
        request.setAttribute("hasAdminRights", hasAdminRights);
        request.setAttribute("hasDashboardRights", hasDashboardRights);
        // ... all other security objects

        // 5. Multi-site data
        request.setAttribute("multiSiteEnabled", multiSiteEnabled);
        request.setAttribute("sites", sites);
        request.setAttribute("selectedSite", selectedSite);
        request.setAttribute("siteBgColors", siteBgColorMap);

        // 6. Groups and providers
        request.setAttribute("groups", groups);
        request.setAttribute("providers", providers);
        request.setAttribute("groupAccessRestrictions", restrictions);

        // 7. System configuration
        request.setAttribute("billingRegion", billingRegion);
        request.setAttribute("eConsultEnabled", eConsultEnabled);
        request.setAttribute("workflowEnabled", workflowEnabled);
        request.setAttribute("healthCardEnabled", healthCardEnabled);
        request.setAttribute("anonymousEnabled", anonymousEnabled);
        request.setAttribute("phoneEncounterEnabled", phoneEncounterEnabled);
        request.setAttribute("caisiEnabled", caisiEnabled);
        request.setAttribute("daySheetEnabled", daySheetEnabled);
        request.setAttribute("pregnancyEnabled", pregnancyEnabled);

        // 8. Dashboard list
        request.setAttribute("dashboards", dashboards);

        // 9. Reason codes (for tooltip display)
        request.setAttribute("reasonCodesJson", reasonCodesJson);

        // 10. JSON config block for JavaScript (serialized by Jackson)
        //     This avoids EL/JSTL encoding issues — ObjectMapper handles JSON escaping.
        Map<String, Object> configMap = new LinkedHashMap<>();
        configMap.put("contextPath", request.getContextPath());
        configMap.put("date", resolvedDate.toString());
        configMap.put("year", year);
        configMap.put("month", month);
        configMap.put("day", day);
        configMap.put("startHour", startHour);
        configMap.put("endHour", endHour);
        configMap.put("everyMin", everyMin);
        configMap.put("providerNo", curProviderNo);
        configMap.put("myGroupNo", myGroupNo);
        configMap.put("view", view);
        configMap.put("viewAll", viewAll);
        configMap.put("selectedSite", selectedSite);
        configMap.put("billingRegion", billingRegion);
        configMap.put("hasBillingRights", hasBillingRights);
        configMap.put("hasDoctorLinkRights", hasDoctorLinkRights);
        configMap.put("hasMasterLinkRights", hasMasterLinkRights);
        configMap.put("reasonCodes", reasonCodes);
        // Read from OscarProperties (NOT ProviderPreference). "-1" disables refresh.
        String refreshSecs = OscarProperties.getInstance().getProperty(
            "refresh.appointmentprovideradminday.jsp", "-1");
        int refreshMs = "-1".equals(refreshSecs) ? 0 : Integer.parseInt(refreshSecs) * 1000;
        configMap.put("refreshInterval", refreshMs);
        configMap.put("quickLinks", quickLinks);
        configMap.put("formLinks", formLinks);
        configMap.put("eFormLinks", eFormLinks);

        // i18n messages for client-side dialogs (from resource bundle)
        Map<String, String> messages = new LinkedHashMap<>();
        messages.put("confirmBooking",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.confirmBooking"));
        messages.put("sameDayRestriction",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.sameDay"));
        messages.put("sameWeekRestriction",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.sameWeek"));
        // Drag-and-drop messages (new — add keys to resource bundle)
        // NOTE: LocaleUtils.getMessage() has NO default-value overload. All keys must
        // exist in the resource bundle. The JavaScript fallbacks (|| 'default text')
        // handle missing keys at runtime as a safety net, but the keys should be added
        // to the .properties files before deployment.
        messages.put("confirmMove",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.confirmMove"));
        messages.put("confirmResize",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.confirmResize"));
        messages.put("cannotMoveBilled",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.cannotMoveBilled"));
        messages.put("updateFailed",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.updateFailed"));
        messages.put("unbilledConfirm",
            LocaleUtils.getMessage(request, "provider.appointmentProviderAdminDay.unbilledConfirm"));
        configMap.put("messages", messages);

        request.setAttribute("scheduleConfigJson",
            new ObjectMapper().writeValueAsString(configMap));

        return "success";
    }
}
```

### Phase 3.2: Struts Configuration

```xml
<action name="provider/Day"
        class="io.github.carlos_emr.carlos.provider.web.ProviderDaySchedule2Action">
    <result name="success">/WEB-INF/views/provider/providerDaySchedule.jsp</result>
    <result name="security-error">/securityError.jsp</result>
</action>
```

Note: The result path points to `WEB-INF/views/` — the JSP is not directly URL-accessible. Only the Struts2 action can forward to it. This is the first action to use the new protected path convention.

### Phase 3.3: Update Login Redirect to Bypass providercontrol.jsp

> **IMPORTANT**: `pageContext.forward()` (used by `providercontrol.jsp`) is a `RequestDispatcher.forward()` — it can forward to JSP files and servlet paths, but it **cannot** forward to a `.do` URL because the Struts filter chain processes `.do` requests **before** the JSP compilation pipeline. Forwarding to `../provider/Day.do` would result in a 404.

**Correct approach**: Change the login redirect to go directly to `provider/Day.do` instead of `providercontrol.jsp?displaymode=day`.

The login flow (`Login2Action` and the providercontrol.jsp self-redirect logic) currently builds URLs like:
```
/provider/providercontrol.jsp?year=2026&month=2&day=8&view=0&displaymode=day&...
```

Change these redirects to go directly to the new action:
```
/provider/Day.do
```

The `ProviderDaySchedule2Action` handles all parameter defaults internally (today's date, default group, etc.), so no URL parameters are needed.

**For backward compatibility**, keep `providercontrol.jsp` routing `"day"` to the old `appointmentprovideradminday.jsp` as a fallback. The migration flag (`schedule.new_day_view=true`) controls which path is used:

```java
// In providercontrol.jsp — keep existing "day" mapping unchanged
{"day", "appointmentprovideradminday.jsp"},

// In Login2Action or wherever the post-login redirect is built:
// NOTE: Use isPropertyActive() — getBooleanProperty("key", "false") checks if
// the value equals "false", which is the OPPOSITE of what we want.
if (OscarProperties.getInstance().isPropertyActive("schedule.new_day_view")) {
    response.sendRedirect(request.getContextPath() + "/provider/Day.do");
} else {
    response.sendRedirect(request.getContextPath() + "/provider/providercontrol.jsp?...");
}
```

This avoids the `pageContext.forward()` limitation entirely — the browser makes a new request to `provider/Day.do`, which Struts routes normally.

---

## Phase 4: The JSP View — Bootstrap 5 + FullCalendar

**Objective**: Build the new day schedule JSP using only JSTL/EL, Bootstrap 5, semantic HTML, and FullCalendar.

### Phase 4.1: Page Structure

**File**: `src/main/webapp/WEB-INF/views/provider/providerDaySchedule.jsp`

```jsp
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>

<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="provider.appointmentProviderAdminDay.title"/></title>

    <%-- Shared includes --%>
    <jsp:include page="/WEB-INF/views/common/head-includes.jspf" />

    <%-- FullCalendar — NO separate CSS file needed; FullCalendar 6.x injects its CSS
         at runtime via JavaScript. Only include the JS bundles (in footer). --%>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/schedule/provider-day-schedule.css">
</head>
<body>

  <%-- === SHARED HEADER === --%>
  <c:set var="activeNav" value="schedule" scope="request" />
  <jsp:include page="/WEB-INF/views/common/header.jspf" />

  <%-- === SCHEDULE SUB-NAVIGATION === --%>
  <nav class="navbar navbar-light bg-light fixed-top carlos-subnav"
       style="top: 30px;" aria-label="Schedule navigation">
    <div class="container-fluid px-2">
      <%-- Left: Date navigation --%>
      <div class="d-flex align-items-center gap-2">
        <a href="..." class="btn btn-sm btn-outline-secondary" title="Previous Day">
          <i class="bi bi-chevron-left"></i>
        </a>
        <span class="fw-bold" id="schedule-date">
          <fmt:formatDate value="${scheduleDate}" pattern="EEEE, MMMM d, yyyy" />
        </span>
        <a href="..." class="btn btn-sm btn-outline-secondary" title="Next Day">
          <i class="bi bi-chevron-right"></i>
        </a>
        <button class="btn btn-sm btn-outline-primary" id="todayBtn">Today</button>
        <a href="..." class="btn btn-sm btn-outline-secondary">Month</a>

        <%-- View toggle --%>
        <div class="btn-group btn-group-sm" role="group">
          <button class="btn btn-outline-secondary ${viewAll ? 'active' : ''}"
                  id="viewAllBtn">View All</button>
          <button class="btn btn-outline-secondary ${!viewAll ? 'active' : ''}"
                  id="viewScheduledBtn">Scheduled</button>
        </div>
      </div>

      <%-- Right: Group/Site selection --%>
      <div class="d-flex align-items-center gap-2">
        <%-- Site dropdown (multi-site only) --%>
        <c:if test="${multiSiteEnabled}">
          <select class="form-select form-select-sm" id="siteSelect"
                  style="width: auto; max-width: 150px;">
            <c:forEach items="${sites}" var="site">
              <option value="${fn:escapeXml(site.name)}"
                      ${site.name == selectedSite ? 'selected' : ''}
                      style="background-color: ${fn:escapeXml(site.bgColor)}">
                <c:out value="${site.name}" />
              </option>
            </c:forEach>
          </select>
        </c:if>

        <%-- Group dropdown --%>
        <select class="form-select form-select-sm" id="groupSelect"
                style="width: auto; max-width: 200px;">
          <c:forEach items="${groups}" var="group">
            <option value="${fn:escapeXml(group.value)}"
                    ${group.value == myGroupNo ? 'selected' : ''}>
              <c:out value="${group.label}" />
            </option>
          </c:forEach>
        </select>

        <%-- Provider search --%>
        <div class="input-group input-group-sm" style="width: 200px;">
          <input type="text" class="form-control" placeholder="Find provider..."
                 id="providerSearch">
          <button class="btn btn-outline-secondary" type="button" id="providerSearchBtn">
            <i class="bi bi-search"></i>
          </button>
        </div>
      </div>
    </div>
  </nav>

  <%-- === SYSTEM/FACILITY MESSAGES === --%>
  <div id="system-message" class="alert alert-info alert-dismissible mx-2 mt-1 d-none"
       role="alert" style="margin-top: 62px;"></div>
  <div id="facility-message" class="alert alert-warning alert-dismissible mx-2 d-none"
       role="alert"></div>

  <%-- === MAIN SCHEDULE AREA === --%>
  <main class="container-fluid px-1" style="margin-top: 62px;" id="schedule-main">
    <%-- FullCalendar instances are injected here by JavaScript --%>
    <div class="row g-1" id="provider-calendars-row">
      <%-- JS will create: for each provider, a <div class="col"> with a calendar --%>
    </div>
  </main>

  <%-- === PAGE CONFIGURATION (for JavaScript) === --%>
  <%-- SECURITY: JSON config is embedded in a data attribute (not a <script> tag) to prevent
       </script> injection. fn:escapeXml() is correct here because the HTML parser decodes
       entities in attribute values BEFORE JavaScript reads them via dataset.
       The JSON is generated by Jackson ObjectMapper in the Action class. --%>
  <div id="schedule-config" data-config="${fn:escapeXml(scheduleConfigJson)}" class="d-none"></div>

  <%-- === SCRIPTS === --%>
  <jsp:include page="/WEB-INF/views/common/footer.jspf" />
  <script src="${pageContext.request.contextPath}/js/vendor/fullcalendar/index.global.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/vendor/fullcalendar/bootstrap5.global.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/schedule/schedule-utils.js"></script>
  <script src="${pageContext.request.contextPath}/js/schedule/provider-day-schedule.js"></script>
</body>
</html>
```

### Phase 4.2: FullCalendar Initialization (provider-day-schedule.js)

This is the main JavaScript file. Key aspects:

```javascript
'use strict';

(function() {
  // Config is in a data attribute (not <script> tag) to prevent </script> injection.
  // The browser's HTML parser decodes &amp; → & etc. before dataset provides it.
  var config;
  try {
    config = JSON.parse(
      document.getElementById('schedule-config').dataset.config
    );
  } catch (e) {
    console.error('Failed to parse schedule config:', e);
    document.getElementById('provider-calendars-row').textContent =
      'Configuration error. Please refresh the page.';
    return; // Exit IIFE — nothing can work without config
  }
  const calendars = new Map();  // providerNo -> FullCalendar instance

  // === INITIALIZATION ===

  async function init() {
    try {
      const data = await fetchDayData();
      renderProviderCalendars(data);
      loadSystemMessages();
      startAutoRefresh();
      setupHashNavigation();
    } catch (err) {
      console.error('Schedule initialization failed:', err);
      document.getElementById('provider-calendars-row').textContent =
        'Failed to load schedule. Please refresh the page.';
    }
  }

  // === DATA FETCHING ===

  async function fetchDayData() {
    const paramObj = {
      date: config.date,
      groupNo: config.myGroupNo,
      viewAll: config.viewAll
    };
    // Only include site param when multi-site is enabled (avoids "null" string)
    if (config.selectedSite) paramObj.site = config.selectedSite;
    const params = new URLSearchParams(paramObj);
    const resp = await fetch(
      `${config.contextPath}/schedule/DayData.do?${params}`
    );
    if (!resp.ok) throw new Error('Failed to load schedule');
    return resp.json();
  }

  // === MULTI-PROVIDER CALENDAR RENDERING ===

  function renderProviderCalendars(data) {
    const row = document.getElementById('provider-calendars-row');
    row.innerHTML = '';

    const providerCount = data.providers.length;
    // Bootstrap column sizing based on provider count
    const colClass = getColumnClass(providerCount);

    data.providers.forEach(provider => {
      const col = document.createElement('div');
      col.className = colClass;

      // Provider header — built with DOM API (not innerHTML) to prevent XSS.
      // All user/DB-controlled values use textContent or setAttribute.
      const card = document.createElement('div');
      card.className = 'card border-0';

      const header = document.createElement('div');
      header.className = 'card-header py-1 px-2 d-flex justify-content-between align-items-center bg-primary text-white';
      header.style.fontSize = '12px';

      const leftGroup = document.createElement('div');
      leftGroup.className = 'd-flex align-items-center gap-1';
      const nameSpan = document.createElement('span');
      nameSpan.className = 'fw-bold';
      nameSpan.textContent = provider.providerName;  // textContent = XSS-safe
      leftGroup.appendChild(nameSpan);
      const countBadge = document.createElement('span');
      countBadge.className = 'badge bg-light text-dark';
      countBadge.textContent = String(provider.appointmentCount);
      leftGroup.appendChild(countBadge);

      const rightGroup = document.createElement('div');
      rightGroup.className = 'd-flex gap-1';
      // Toggle reason button
      const reasonBtn = document.createElement('button');
      reasonBtn.className = 'btn btn-sm btn-outline-light py-0 px-1 toggle-reason-btn';
      reasonBtn.setAttribute('data-provider', provider.providerNo);  // setAttribute = safe
      reasonBtn.title = 'Toggle reasons';
      reasonBtn.innerHTML = '<i class="bi bi-chat-left-text"></i>';  // static HTML, no user data
      rightGroup.appendChild(reasonBtn);
      // Week view and Search buttons (similar pattern — static HTML only)
      rightGroup.insertAdjacentHTML('beforeend',
        '<a href="#" class="btn btn-sm btn-outline-light py-0 px-1" title="Week view">' +
          '<i class="bi bi-calendar-week"></i></a>' +
        '<a href="#" class="btn btn-sm btn-outline-light py-0 px-1" title="Search">' +
          '<i class="bi bi-search"></i></a>');

      header.appendChild(leftGroup);
      header.appendChild(rightGroup);

      const body = document.createElement('div');
      body.className = 'card-body p-0';
      const calDiv = document.createElement('div');
      calDiv.id = 'cal-' + provider.providerNo;  // id attribute — providerNo is numeric from DB
      calDiv.className = 'provider-calendar';
      body.appendChild(calDiv);

      card.appendChild(header);
      card.appendChild(body);
      col.appendChild(card);
      row.appendChild(col);

      // Initialize FullCalendar instance
      const calendar = createCalendar(calDiv, provider, data);
      calendars.set(provider.providerNo, calendar);
      calendar.render();
    });
  }

  function getColumnClass(count) {
    // Responsive grid: each provider gets equal width
    // On small screens (<768px), stack vertically (col-12)
    // On tablet (768-991px), show 2-3 columns
    // On desktop (992px+), show all providers
    if (count === 1) return 'col-12';
    if (count === 2) return 'col-12 col-md-6';
    if (count === 3) return 'col-12 col-md-4';
    if (count === 4) return 'col-12 col-md-6 col-lg-3';
    if (count <= 6) return 'col-12 col-md-4 col-lg-2';
    // 7+ providers: stack on mobile, 3-col tablet, equal-width desktop
    return 'col-12 col-md-4 col-lg';
  }

  // === FULLCALENDAR INSTANCE CREATION ===

  function createCalendar(el, provider, data) {
    // Build events from appointments
    const events = provider.appointments.map(appt => ({
      id: String(appt.id),
      title: appt.title,
      start: appt.start,
      end: appt.end,
      backgroundColor: appt.backgroundColor,
      borderColor: appt.borderColor || appt.backgroundColor,
      textColor: appt.textColor || '#000000',
      extendedProps: { ...appt }  // All appointment data accessible
    }));

    // Build background events from template slots
    const bgEvents = provider.slots.map(slot => ({
      start: slot.start,
      end: slot.end,
      display: 'background',
      backgroundColor: slot.backgroundColor,
      extendedProps: {
        code: slot.code,
        description: slot.description,
        bookingLimit: slot.bookingLimit,
        confirm: slot.confirm
      }
    }));

    // NOTE: With the global bundle (index.global.min.js), all included plugins
    // (core, dayGrid, timeGrid, list, interaction, multiMonth) auto-register.
    // The Bootstrap 5 plugin auto-registers from its own global script.
    // Do NOT pass a `plugins` array — that's only for the ES6 module approach.
    return new FullCalendar.Calendar(el, {
      themeSystem: 'bootstrap5',
      initialView: 'timeGridDay',
      initialDate: config.date,

      // Schedule boundaries
      slotMinTime: provider.startTime || config.startHour + ':00:00',
      slotMaxTime: provider.endTime || config.endHour + ':00:00',
      slotDuration: { minutes: provider.slotDurationMinutes || config.everyMin },
      slotLabelInterval: { minutes: 30 },

      // Display
      headerToolbar: false,        // We handle our own header
      allDaySlot: false,
      nowIndicator: true,
      height: 'auto',
      slotEventOverlap: true,

      // Slot label format: "9:00a" style (compact)
      slotLabelFormat: {
        hour: 'numeric',
        minute: '2-digit',
        omitZeroMinute: true,
        meridiem: 'narrow'
      },

      // Events
      events: [...events, ...bgEvents],

      // === CUSTOM EVENT RENDERING ===
      // eventContent return values (FullCalendar 6.x):
      //   { domNodes: [...] } — custom DOM nodes (XSS-safe via textContent)
      //   true — render default content
      //   null/undefined — render empty container (v6 breaking change from v5)
      eventContent: function(arg) {
        if (arg.event.display === 'background') return true;
        return renderAppointmentContent(arg, provider);
      },

      eventDidMount: function(info) {
        if (info.event.display === 'background') return;
        mountAppointmentTooltip(info);
      },

      eventWillUnmount: function(info) {
        // Clean up Bootstrap tooltips
        const tooltip = bootstrap.Tooltip.getInstance(info.el);
        if (tooltip) tooltip.dispose();
      },

      // === INTERACTION CALLBACKS ===

      // dateClick fires on empty slots AND background events (template slots),
      // but NOT on foreground events (those go to eventClick).
      // info.dateStr includes timezone in timeGrid: "2026-02-08T14:30:00-05:00"
      dateClick: function(info) {
        openAddAppointment(provider.providerNo, info, data);
      },

      eventClick: function(info) {
        info.jsEvent.preventDefault();
        const appt = info.event.extendedProps;
        // Pass demographicNo so editappointment.jsp can display patient context.
        // Current JSP passes actual demographic_no when clicking patient name (line 2052).
        openEditAppointment(appt.id, provider.providerNo, appt.demographicNo);
      },

      // === DRAG-AND-DROP & RESIZE ===
      // Requires interaction plugin (included in global bundle, auto-registered).
      editable: true,               // Enable drag-and-drop + resize
      eventStartEditable: true,     // Allow dragging events to new times
      eventDurationEditable: true,  // Allow resizing events (drag bottom edge)
      selectable: false,            // Disable range selection (use dateClick for new appointments)
      snapDuration: { minutes: provider.slotDurationMinutes || config.everyMin },

      // Drag confirmation + AJAX update when event is moved to a new time
      eventDrop: function(info) {
        handleEventTimeChange(info, provider.providerNo, 'move');
      },

      // Resize confirmation + AJAX update when event duration is changed
      eventResize: function(info) {
        handleEventTimeChange(info, provider.providerNo, 'resize');
      }
    });
  }

  // === DRAG-AND-DROP / RESIZE HANDLER ===

  /**
   * Handles appointment time changes from FullCalendar drag-and-drop or resize.
   *
   * Cross-provider drag: NOT supported in this implementation. Each provider has
   * its own FullCalendar instance, and native drag only works within a single
   * instance. To move to a different provider, use the edit popup. (Cross-provider
   * would require Premium resource view or custom HTML5 inter-instance drag.)
   *
   * @param info  FullCalendar EventDropArg or EventResizeArg
   * @param providerNo  The provider this calendar belongs to
   * @param action  'move' or 'resize' (for confirmation message)
   */
  function handleEventTimeChange(info, providerNo, action) {
    var event = info.event;
    var appt = event.extendedProps;

    // Don't allow moving terminal-status appointments (Billed)
    if (appt.status && (appt.status === 'B' || appt.status === 'BS' || appt.status === 'BV')) {
      info.revert();
      alert(config.messages.cannotMoveBilled || 'Cannot move a billed appointment.');
      return;
    }

    // Format new times for confirmation dialog
    var newStart = event.start;
    var newEnd = event.end;
    var startStr = newStart.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    var endStr = newEnd.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    var msg = action === 'move'
      ? (config.messages.confirmMove || 'Move appointment to') + ' ' + startStr + ' - ' + endStr + '?'
      : (config.messages.confirmResize || 'Change duration to') + ' ' + startStr + ' - ' + endStr + '?';

    if (!confirm(msg)) {
      info.revert();
      return;
    }

    // Build minimal update payload — only time fields, not the full appointment
    var payload = {
      appointmentNo: appt.id,
      providerNo: providerNo,
      startTime: formatTime(newStart),           // "14:30"
      endTime: formatTime(newEnd),               // "15:00"
      appointmentDate: formatDate(newStart)       // "2026-02-08"
    };

    // POST to new REST endpoint (CSRF-exempt at /ws/rs/*)
    fetch(config.contextPath + '/ws/rs/schedule/appointment/' + appt.id + '/reschedule', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(function(resp) {
      if (!resp.ok) throw new Error('Update failed: ' + resp.status);
      return resp.json();
    })
    .then(function() {
      // Refresh all calendars to pick up side effects
      refreshAllCalendars();
    })
    .catch(function(err) {
      console.error('Appointment update failed:', err);
      info.revert();
      alert(config.messages.updateFailed || 'Failed to update appointment. Change reverted.');
    });
  }

  // Format Date → "HH:mm"
  function formatTime(date) {
    return String(date.getHours()).padStart(2, '0') + ':' +
           String(date.getMinutes()).padStart(2, '0');
  }

  // Format Date → "yyyy-MM-dd"
  function formatDate(date) {
    return date.getFullYear() + '-' +
           String(date.getMonth() + 1).padStart(2, '0') + '-' +
           String(date.getDate()).padStart(2, '0');
  }

  // === CUSTOM EVENT CONTENT RENDERING ===
  // Uses domNodes approach for XSS safety (textContent, not innerHTML)

  function renderAppointmentContent(arg, provider) {
    const appt = arg.event.extendedProps;
    const container = document.createElement('div');
    container.className = 'appt-content d-flex flex-column';

    // Row 1: Status icon + Patient name + Action links
    const row1 = document.createElement('div');
    row1.className = 'd-flex align-items-center gap-1';

    // Status icon (clickable to advance status)
    const statusBtn = document.createElement('button');
    statusBtn.className = 'btn btn-xs p-0 border-0 status-btn';
    statusBtn.title = appt.statusTitle || '';
    statusBtn.dataset.apptId = appt.id;
    statusBtn.dataset.nextStatus = appt.nextStatus;
    const statusImg = document.createElement('img');
    // Validate statusIcon to prevent path traversal/injection
    const safeIcon = (appt.statusIcon && /^[a-zA-Z0-9._-]+$/.test(appt.statusIcon))
      ? appt.statusIcon : 'unknown.gif';
    statusImg.src = config.contextPath + '/images/' + safeIcon;
    statusImg.width = 14;
    statusImg.height = 14;
    statusBtn.appendChild(statusImg);
    statusBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      advanceStatus(appt.id, appt.nextStatus);
    });
    row1.appendChild(statusBtn);

    // Indicator icons (tickler, alert, notes, version, prevention)
    if (appt.hasTickler) {
      const ticklerSpan = document.createElement('span');
      ticklerSpan.className = 'text-danger fw-bold';
      ticklerSpan.textContent = '!';
      ticklerSpan.title = 'Has active tickler';
      row1.appendChild(ticklerSpan);
    }
    // ... similar for alert (A), notes (N), version (*), prevention (stop)

    // Patient name
    const nameSpan = document.createElement('span');
    nameSpan.className = 'text-truncate appt-name';
    nameSpan.textContent = appt.title;  // textContent = XSS safe
    row1.appendChild(nameSpan);

    // Action links: E, B, M, Rx
    const actions = document.createElement('span');
    actions.className = 'ms-auto d-flex gap-1 appt-actions';

    // Action links — each has specific window name and dimensions from current JSP.
    // storeApptNo() is called before E/M/Rx to set session attribute for cross-popup context.
    // Billing has 3 modes based on appointment billing status (see Appendix D checklist).
    if (config.hasDoctorLinkRights) {
      actions.appendChild(createActionLink('E', appt.encounterUrl, 'Encounter',
        'encounter', 710, 1024, appt.id));
    }
    if (config.hasBillingRights) {
      // Billing mode: appt.billingMode = 'create' | 'edit' | 'delete'
      // Label: 'B' (create), '=B' (edit), '-B' (delete/rebill)
      var bLabel = appt.billingMode === 'edit' ? '=B' :
                   appt.billingMode === 'delete' ? '-B' : 'B';
      actions.appendChild(createActionLink(bLabel, appt.billingUrl, 'Billing',
        'apptProvider', 755, 1200, null));
    }
    if (config.hasMasterLinkRights) {
      actions.appendChild(createActionLink('M', appt.masterUrl, 'Master Record',
        'master', 700, 1024, appt.id));
    }
    if (config.hasDoctorLinkRights) {
      // Rx window name matches popupWithApptNo → popupOscarRx which uses 'oscarRx'
      actions.appendChild(createActionLink('Rx', appt.rxUrl, 'Prescription',
        'oscarRx', 700, 1027, appt.id));
    }

    row1.appendChild(actions);
    container.appendChild(row1);

    // Row 2: Reason (toggleable)
    if (appt.reason || appt.reasonCodeLabel) {
      const reasonDiv = document.createElement('div');
      reasonDiv.className = `appt-reason reason-${provider.providerNo} small text-muted`;
      reasonDiv.textContent = [appt.reasonCodeLabel, appt.reason]
        .filter(Boolean).join(' - ');
      container.appendChild(reasonDiv);
    }

    return { domNodes: [container] };
  }

  // === STATUS ADVANCEMENT (AJAX) ===

  async function advanceStatus(appointmentId, nextStatus) {
    if (!nextStatus) return;
    try {
      const resp = await fetch(
        `${config.contextPath}/ws/rs/schedule/appointment/${appointmentId}/updateStatus`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status: nextStatus })
        }
      );
      if (resp.ok) {
        refreshAllCalendars();
      } else {
        console.error('Status update failed: HTTP ' + resp.status);
        // Refresh to revert optimistic UI state back to server truth
        refreshAllCalendars();
      }
    } catch (err) {
      console.error('Status update failed:', err);
      refreshAllCalendars();
    }
  }

  // === SYNCHRONIZED NAVIGATION ===

  function navigateAllCalendars(date) {
    calendars.forEach(cal => cal.gotoDate(date));
  }

  async function refreshAllCalendars() {
    const data = await fetchDayData();
    // Destroy existing FullCalendar instances to prevent memory leaks.
    // Simply removing DOM nodes does NOT clean up FC's internal timers/listeners.
    calendars.forEach(function(cal) { cal.destroy(); });
    calendars.clear();
    // Re-render with fresh data
    renderProviderCalendars(data);
  }

  // === AUTO-REFRESH ===

  function startAutoRefresh() {
    if (config.refreshInterval > 0) {
      setInterval(refreshAllCalendars, config.refreshInterval);
    }
  }

  // === POPUP HELPERS (maintain existing popup behavior) ===

  /**
   * Opens the "Add Appointment" popup. This MUST replicate the full current flow:
   *
   * Current flow: user clicks empty slot → confirmPopupPage(600, 780, url, doConfirm, allowDay, allowWeek)
   *   → checks booking confirmation mode from ScheduleTemplateCode.confirm
   *   → shows confirm dialog / checks day/week restrictions
   *   → popupPage() opens addappointment.jsp with all parameters
   *
   * FullCalendar dateClick info.dateStr for timeGridDay includes timezone:
   *   "2026-02-08T14:30:00-05:00" — we need to extract "14:30" only.
   *
   * The DayViewResponse includes template slot data in extendedProps (code, confirm,
   * duration, bookingLimit) so we can look up the template code for the clicked time.
   */
  function openAddAppointment(providerNo, info, data) {
    // 1. Parse time from dateStr (strip seconds and timezone offset)
    //    info.dateStr = "2026-02-08T14:30:00-05:00" for timeGridDay
    const timePart = info.dateStr.split('T')[1];  // "14:30:00-05:00"
    const startTime = timePart ? timePart.substring(0, 5) : '09:00';  // "14:30"

    // 2. Find the template slot for this time to get confirm mode and duration
    const provider = data.providers.find(p => p.providerNo === providerNo);
    const clickedTime = info.date.getTime();
    let templateSlot = null;
    if (provider && provider.slots) {
      templateSlot = provider.slots.find(slot => {
        const slotStart = new Date(slot.start).getTime();
        const slotEnd = new Date(slot.end).getTime();
        return clickedTime >= slotStart && clickedTime < slotEnd;
      });
    }

    // 3. Extract template code attributes
    const doConfirm = templateSlot ? (templateSlot.confirm || '') : '';
    const duration = templateSlot ? (templateSlot.duration || '') : '';
    const slotDuration = provider ? provider.slotDurationMinutes : config.everyMin;

    // 4. Compute end_time from start + duration
    const startParts = startTime.split(':');
    const startMinutes = parseInt(startParts[0]) * 60 + parseInt(startParts[1]);
    const endMinutes = startMinutes + (parseInt(duration) || slotDuration);
    const endHour = String(Math.floor(endMinutes / 60)).padStart(2, '0');
    const endMin = String(endMinutes % 60).padStart(2, '0');
    const endTime = endHour + ':' + endMin;

    // 5. Compute allowDay and allowWeek (same logic as current JSP lines 415-432)
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const apptDate = new Date(config.year, config.month - 1, config.day);
    apptDate.setHours(0, 0, 0, 0);

    const allowDay = (apptDate.getTime() === today.getTime()) ? 'Yes' : 'No';
    const oneWeekLater = new Date(today);
    oneWeekLater.setDate(oneWeekLater.getDate() + 7);
    const allowWeek = (apptDate < oneWeekLater) ? 'Yes' : 'No';

    // 6. Build the popup URL with URLSearchParams (proper encoding)
    const params = new URLSearchParams({
      provider_no: providerNo,
      bFirstDisp: 'true',
      year: String(config.year),
      month: String(config.month),
      day: String(config.day),
      start_time: startTime,
      end_time: endTime,
      duration: String(duration)
    });
    const url = config.contextPath + '/appointment/addappointment.jsp?' + params.toString();

    // 7. Apply booking confirmation logic (matches confirmPopupPage in schedulePage.js.jsp)
    if (doConfirm === 'Yes') {
      // Simple confirmation required
      if (!confirm(config.messages.confirmBooking || 'Confirm booking?')) {
        return;
      }
      openPopup(url, 'apptProvider', 600, 780);
    } else if (doConfirm === 'Day') {
      if (allowDay === 'No') {
        alert(config.messages.sameDayRestriction || 'Same-day booking not allowed for this slot.');
        return;
      }
      openPopup(url, 'apptProvider', 600, 780);
    } else if (doConfirm === 'Wk') {
      if (allowWeek === 'No') {
        alert(config.messages.sameWeekRestriction || 'Same-week booking not allowed for this slot.');
        return;
      }
      openPopup(url, 'apptProvider', 600, 780);
    } else if (doConfirm === 'Onc') {
      // On-Call Urgent
      if (allowDay === 'No') {
        if (!confirm('This is an On Call Urgent appointment. Are you sure you want to book?')) {
          return;
        }
      }
      openPopup(url, 'apptProvider', 600, 780);
    } else {
      // No confirmation needed
      openPopup(url, 'apptProvider', 600, 780);
    }
  }

  /**
   * Opens the appointment edit popup. Maps to appointmentcontrol.jsp which
   * routes displaymode=edit to editappointment.jsp.
   *
   * NOTE: Appointment DELETION is handled INSIDE the edit popup (editappointment.jsp
   * has Delete/Cancel/No Show buttons). The day schedule does NOT handle deletion
   * directly — it only opens the edit popup, which manages all lifecycle operations.
   *
   * Current JSP has TWO edit paths with different window sizes:
   *   - Empty slot tooltip: demographic_no=0, 600×780
   *   - Patient name click: demographic_no=<actual>, 535×860
   * We use the patient name path since FullCalendar eventClick gives us the appointment
   * data including demographicNo.
   */
  function openEditAppointment(appointmentNo, providerNo, demographicNo) {
    const params = new URLSearchParams({
      appointment_no: String(appointmentNo),
      provider_no: providerNo,
      bFirstDisp: 'true',
      year: String(config.year),
      month: String(config.month),
      day: String(config.day),
      start_time: '',
      demographic_no: String(demographicNo || 0),
      displaymode: 'edit',
      dboperation: 'search'
    });
    const url = config.contextPath + '/appointment/appointmentcontrol.jsp?' + params.toString();
    // 535×860 matches the "patient name click" path in current JSP (line 2052)
    openPopup(url, 'apptProvider', 535, 860);
  }

  function openPopup(url, windowName, height, width) {
    var props = 'height=' + height + ',width=' + width +
      ',location=no,scrollbars=yes,menubars=no,toolbars=no,' +
      'resizable=yes,screenX=50,screenY=50,top=0,left=0';
    var popup = window.open(url, windowName, props);
    if (popup != null) {
      if (popup.opener == null) popup.opener = self;
      popup.focus();
    }
  }

  // === UTILITY FUNCTIONS ===

  // escapeHtml() is available as a utility but may not be needed since
  // all rendering uses textContent (XSS-safe by default).
  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  /**
   * Creates an action link (E, B, M, Rx) for an appointment cell.
   * Each link type has a specific window name and dimensions from the current JSP.
   * E/M/Rx call storeApptNo() to save appointment_no in the HTTP session before opening.
   */
  function createActionLink(label, url, title, windowName, height, width, apptNoForSession) {
    const a = document.createElement('a');
    a.href = '#';
    a.className = 'badge bg-secondary text-white action-link';
    a.textContent = label;
    a.title = title;
    a.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();

      // Billing delete mode gets a confirmation dialog (matches onUnbilled in current JSP)
      if (label === '-B') {
        if (!confirm(config.messages.unbilledConfirm || 'Delete billing record?')) return;
      }

      // Store appointment_no in session for E/M/Rx popups (matches popupWithApptNo)
      if (apptNoForSession) {
        storeApptNo(apptNoForSession);
      }

      openPopup(url, windowName, height, width);
    });
    return a;
  }

  /**
   * Stores appointment_no in the HTTP session via AJAX (for cross-popup context).
   * Matches storeApptInSession.jsp which sets session.setAttribute("cur_appointment_no", ...).
   * Used by E/M/Rx popups that need to know which appointment they were launched from.
   */
  function storeApptNo(apptNo) {
    fetch(config.contextPath + '/provider/storeApptInSession.jsp?appointment_no=' +
      encodeURIComponent(apptNo));
  }

  // === INITIALIZATION ===
  document.addEventListener('DOMContentLoaded', init);

  // === SYSTEM / FACILITY MESSAGES ===

  function loadSystemMessages() {
    fetch(config.contextPath + '/SystemMessage.do')
      .then(function(r) { return r.text(); })
      .then(function(html) {
        var el = document.getElementById('system-message');
        if (el && html && html.trim()) { el.innerHTML = html; el.classList.remove('d-none'); }
      })
      .catch(function() { /* non-critical */ });
    fetch(config.contextPath + '/FacilityMessage.do')
      .then(function(r) { return r.text(); })
      .then(function(html) {
        var el = document.getElementById('facility-message');
        if (el && html && html.trim()) { el.innerHTML = html; el.classList.remove('d-none'); }
      })
      .catch(function() { /* non-critical */ });
  }

  // === TOOLTIP MOUNTING ===

  function mountAppointmentTooltip(info) {
    var appt = info.event.extendedProps;
    var lines = [appt.title];
    if (appt.reason) lines.push(appt.reason);
    if (appt.notes) lines.push(appt.notes);
    new bootstrap.Tooltip(info.el, {
      title: lines.join('\n'),
      placement: 'top',
      trigger: 'hover',
      container: 'body'
    });
  }

  // === HASH-BASED NAVIGATION (connects URL strategy to implementation) ===

  function setupHashNavigation() {
    window.addEventListener('hashchange', function() {
      // Re-read state from hash and refresh calendars
      var params = new URLSearchParams(window.location.hash.substring(1));
      if (params.get('date')) config.date = params.get('date');
      if (params.get('group')) config.myGroupNo = params.get('group');
      if (params.get('site')) config.selectedSite = params.get('site');
      if (params.has('viewAll')) config.viewAll = params.get('viewAll') !== 'false';
      refreshAllCalendars();
    });
  }

  // === GLOBAL REFRESH (called by popup windows via window.opener) ===
  window.refreshSchedule = refreshAllCalendars;
  // Compatibility shims: existing popups call window.opener.refresh() / refresh1()
  window.refresh = refreshAllCalendars;
  window.refresh1 = refreshAllCalendars;

})();
```

### Phase 4.3: Schedule CSS (provider-day-schedule.css)

Minimal custom CSS — Bootstrap 5 handles most layout:

```css
/* Provider calendar cards */
.provider-calendar {
  font-size: 11px;
}

/* Compact FullCalendar slots */
.provider-calendar .fc-timegrid-slot {
  height: 24px;
}

/* Appointment content styling */
.appt-content {
  font-size: 11px;
  line-height: 1.2;
  padding: 1px 2px;
  overflow: hidden;
}

.appt-name {
  font-weight: bold;
  color: #00283c;
}

.appt-actions .action-link {
  font-size: 9px;
  padding: 0 2px;
  cursor: pointer;
}

.appt-actions .action-link:hover {
  background-color: var(--carlos-nav-hover) !important;
}

/* Status button */
.status-btn {
  background: none;
  line-height: 1;
}

/* Reason toggle */
.appt-reason {
  font-style: italic;
  font-size: 10px;
}

.reason-hidden .appt-reason {
  display: none;
}

/* Provider header - match classic OSCAR blue */
.card-header.bg-primary {
  background-color: var(--carlos-primary) !important;
}

/* Background events (template slots) - reduce opacity */
.provider-calendar .fc-bg-event {
  opacity: 0.15;
}

/* Time slot labels - classic OSCAR colors */
.provider-calendar .fc-timegrid-slot-label {
  font-size: 10px;
  font-weight: bold;
}

/* Birthday indicator */
.birthday-cake::after {
  content: '\1F382';
  font-size: 10px;
}

/* Indicator badges */
.indicator-tickler { color: red; font-weight: bold; }
.indicator-alert { color: orange; }
.indicator-notes { color: purple; }
.indicator-version { color: red; }
.indicator-prevention { color: red; }

/* Drag-and-drop visual feedback */
.provider-calendar .fc-event {
  cursor: grab;
}
.provider-calendar .fc-event.fc-event-dragging {
  cursor: grabbing;
  opacity: 0.7;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.3);
}

/* Resize handle on event bottom edge */
.provider-calendar .fc-event .fc-event-resizer {
  height: 6px;
  cursor: ns-resize;
}
.provider-calendar .fc-event .fc-event-resizer:hover {
  background-color: rgba(0, 0, 0, 0.15);
}
```

### Phase 4.4: Shared Schedule Utilities (schedule-utils.js)

**File**: `src/main/webapp/js/schedule/schedule-utils.js`

Contains shared utility functions used by `provider-day-schedule.js` and potentially future schedule views (week, month):
- `formatTime(date)` — Date → "HH:mm"
- `formatDate(date)` — Date → "yyyy-MM-dd"
- `escapeHtml(text)` — safe HTML entity encoding via DOM
- `openPopup(url, name, h, w)` — standardized `window.open()` wrapper
- `getContextPath()` — reads from `data-context-path` on nav element

> **NOTE**: In the current plan, these functions are defined inline within the `provider-day-schedule.js` IIFE. During implementation, extract them to `schedule-utils.js` for reuse. The IIFE can import them or they can be loaded as a preceding `<script>` tag (they would go on the `window` object).

---

## Phase 5: Feature Parity Checklist

Every feature of the current screen must be preserved. This checklist tracks each. New features (drag-and-drop) are in a separate section below the parity items.

### Navigation (in header.jspf)
- [ ] CARLOS logo → home
- [ ] Schedule link
- [ ] Caseload link
- [ ] Clinical Resources link (`_resource` gated)
- [ ] Patient Search popup (`_search` gated)
- [ ] Reports popup (`_report` gated)
- [ ] Billing Reports popup (`_billing` gated)
- [ ] Lab/Inbox link with badge count (`_appointment.doctorLink` gated)
- [ ] Unclaimed labs link (`_appointment.doctorLink` gated)
- [ ] Messages link with badge count (`_msg` gated)
- [ ] Consultations link with aged count (`_con` gated)
- [ ] eConsult link (Ontario, property-gated)
- [ ] eDocs link (`_edoc` gated)
- [ ] Tickler link with badge count (`_tickler` gated)
- [ ] Referral management (property-gated)
- [ ] Workflow link (property-gated)
- [ ] Administration link (`_admin` gated)
- [ ] Dashboard dropdown (`_dashboardDisplay` gated)
- [ ] Help link (configurable URL)
- [ ] Scratch Pad link
- [ ] User preferences link with name display
- [ ] Logout button
- [ ] All keyboard shortcuts (Alt+A through Alt+W, Ctrl+Q)
- [ ] Alt+click for Inboxhub on lab link

### Sub-Navigation (schedule-specific)
- [ ] Previous/Next day arrows
- [ ] Date display
- [ ] Calendar popup for date jumping
- [ ] Today button
- [ ] Month view link
- [ ] View All / Scheduled toggle
- [ ] Site dropdown (multi-site with color backgrounds)
- [ ] Group dropdown (with access restrictions)
- [ ] Provider search (find provider form)
- [ ] Anonymous client creation (property-gated)
- [ ] Phone encounter creation (property-gated)

### System Messages
- [ ] System message banner (from SystemMessage.do)
- [ ] Facility message banner (from FacilityMessage.do)

### Per-Provider Column
- [ ] Provider name display
- [ ] Appointment count badge
- [ ] Week view button
- [ ] Day Sheet button (property-gated)
- [ ] Search button (appointment search)
- [ ] Flip view radio
- [ ] Zoom/single provider view
- [ ] Reason toggle button (per-provider, persists to localStorage)
- [ ] Template slot coloring (background events)

### Per-Appointment Cell
- [ ] Status icon (click to advance through cycle — see Appendix D for full 24-code system)
- [ ] Status background color
- [ ] Signed/Verified/Billed status modifiers (S and V suffixes, B/BS/BV are terminal)
- [ ] Short letter display with color
- [ ] Urgency warning icon
- [ ] Tickler indicator (!)
- [ ] Alert indicator (A)
- [ ] Notes indicator (N)
- [ ] Health card version indicator (*)
- [ ] Roster status indicators ($, #)
- [ ] Prevention stop sign warnings
- [ ] Patient name (with alias and pronouns)
- [ ] Birthday cake icon
- [ ] Multi-site color indicator
- [ ] Provider color indicator
- [ ] **E** (Encounter) link → opens encounter popup
- [ ] **B** (Billing) link → opens billing (or delete/edit if already billed)
- [ ] **M** (Master File) link → opens demographic popup
- [ ] **Rx** (Prescription) link → opens prescription popup
- [ ] Reason display (toggleable per-provider)
- [ ] Dynamic form links (from provider preferences)
- [ ] Dynamic eForm links (from provider preferences)
- [ ] Dynamic quick links (from provider preferences)
- [ ] eForm Library link (property-gated)
- [ ] Intake Form link (property-gated)
- [ ] Pregnancy indicator (property-gated)
- [ ] Tooltip with patient name + reason + notes
- [ ] Rowspan for multi-slot appointments

### NEW FEATURE: Drag-and-Drop & Resize (not parity — new capability)
- [ ] Drag appointment to new time slot (within same provider) → confirm → AJAX update
- [ ] Resize appointment (drag bottom edge) to change duration → confirm → AJAX update
- [ ] Revert on server error (FullCalendar `info.revert()`)
- [ ] Block drag/resize for billed appointments (B/BS/BV terminal status)
- [ ] Snap to slot grid (`snapDuration` from template)
- [ ] Visual feedback during drag (FullCalendar provides ghost event)
- [ ] Confirmation dialog before saving (shows new start/end times)
- [ ] REST endpoint: `POST /ws/rs/schedule/appointment/{id}/reschedule`
- [ ] Audit trail: archive old appointment before time update
- [ ] Cross-provider drag: NOT supported (separate FullCalendar instances — use edit popup)

### Per-Appointment Cell (continued)
- [ ] `storeApptNo()` — saves appointment number to HTTP session (via `storeApptInSession.jsp`) for cross-popup context
- [ ] Dynamic patient name truncation (configurable `patientNameLength` from provider preferences)
- [ ] Double-booking indicator (multiple appointments in same time slot)
- [ ] Appointment `type` display (configurable visibility)
- [ ] Appointment `location` display (multi-site)
- [ ] Appointment `resources` display
- [ ] Appointment `notes` tooltip content
- [ ] Right-click context menu compatibility (existing popup behavior)

### Week View Links (Per-Provider Header)
- [ ] Week view button links to `providercontrol.jsp?displaymode=week` or equivalent
- [ ] 5-day vs 7-day week view toggle (`scheduler.view.weekViews` property)
- [ ] Hidden E/B/Rx action links in week view cells (different layout from day view)

### Billing Links (3 Modes)
- [ ] Create Billing — when no billing exists for appointment
- [ ] Delete/Rebill — when billing already exists
- [ ] Edit Billing — when billing exists and needs modification
- [ ] Billing region-specific URLs (ON vs BC vs generic — `billregion` property)

### CAISI/PMmodule Integration
- [ ] Program selection dropdown (CAISI module only)
- [ ] `_grp_` prefix convention for CAISI group names
- [ ] Program-filtered provider list
- [ ] Infirmary view mode — **DEFERRED** (listed as Non-Goal; preserve redirect to old JSP for CAISI infirmary)
- [ ] `GoToCaisiViewFromOscarView` redirect parameter support
- [ ] `TORONTO_RFQ` module load checks (hides nav items when active)
- [ ] `caisi.search.workflow` property (routes search to `PMmodule/ClientSearch2.do`)
- [ ] `useProgramLocation` property (adds programId filter to appointment queries)
- [ ] CBI Reminder Window alert on page load (CAISI mode)
- [ ] `|P` (Program Management) link per appointment when CAISI enabled

### Security Objects (Access Control)
- [ ] `_site_access_privacy` — restricts visible sites/providers to user's assigned sites
- [ ] `_team_access_privacy` — restricts visible teams/providers
- [ ] `_team_schedule_only` — forces single-provider view (ignores group selection)
- [ ] `_month` — gates Month link in sub-navigation
- [ ] `_day` — gates Today link in sub-navigation
- [ ] `_dashboardCommonLink` — gates Common Provider Dashboard sub-item
- [ ] `_admin.*` compound check — Administration link checks 11 admin sub-objects

### Role-Based Routing (providercontrol.jsp)
- [ ] `er_clerk` role → redirects to `er_clerk.jsp` (bypasses schedule entirely)
- [ ] `Vaccine Provider` role → redirects to `vaccine_provider.jsp`

### Dynamic Layout
- [ ] Dynamic patient name truncation based on provider count (5+: 2-3 chars, 2: 20 chars, 1: 30 chars)
- [ ] Provider column alternating header colors (`#bfefff` / `silver`)
- [ ] `[Not On Schedule]` indicator for providers with no schedule for the day
- [ ] `noCountStatus` exclusion list (`C, CS, CV, N, NS, NV` excluded from appointment count)
- [ ] Multi-site appointment filtering with `CurrentSiteMap` privacy logic
- [ ] `view` parameter edge case forcing (when `displaymode=day` and `viewall!=1`, force `view=1`)
- [ ] Provider group sort ordering via `MyGroup.MyGroupNoViewOrderComparator`
- [ ] Title bar includes provider name prefix (`Lastname, F-CARLOS Schedule`)
- [ ] `archiveView` session attribute suppresses auto-refresh
- [ ] `record` and `module` context preservation parameters

### Behavior
- [ ] Auto-refresh via `<meta http-equiv="refresh">` → migrate to JS `setInterval` (see Appendix E)
- [ ] Configurable refresh interval from `ProviderPreference.refreshEvery`
- [ ] Scroll position preservation on refresh
- [ ] Password expiration check on load (`ProviderPreference.passwordExpiredDate`)
- [ ] Popup blocker detection (property-gated)
- [ ] Booking confirmation dialog (Yes/Day/Wk/Onc modes per template code `confirm` field)
- [ ] Template code lookup on slot click (find confirm mode, duration, bookingLimit for clicked time)
- [ ] `allowDay` computation (is appointment date == today?)
- [ ] `allowWeek` computation (is appointment date within 7 days of today?)
- [ ] `window.opener.refresh()` support for popup-initiated refreshes
- [ ] `window.refresh1()` alias for backward compatibility (note: original `refresh1()` also switches `view=1→0`; new implementation is a simple refresh — acceptable since new view uses hash state, not URL params)
- [ ] Health card reader support (property-gated, `IS_HEALTH_CARD_ENABLED`)
- [ ] Mobile responsive layout (see Appendix G — currently a separate code path)
- [ ] Print support (`.noprint` classes)
- [ ] Login redirect default handling (no-param → today's date with defaults)
- [ ] Session attribute management (`curProvider`, `curProviderNo`, `myGroupNo`, etc.)
- [ ] Database-driven status mode support (`ENABLE_EDIT_APPT_STATUS`, see Appendix D)
- [ ] System message polling (from `SystemMessage.do`)
- [ ] Facility message display (from `FacilityMessage.do`)
- [ ] Integrator message count badge (`integratorMessageCount` — purple color)
- [ ] Demographic message count badge (`demographicMessageCount` — red color)
- [ ] `switchLocale` parameter support (language switching)

### OscarProperties Feature Gates (see Appendix E)
- [ ] All 18+ property checks listed in Appendix E
- [ ] Default values match current behavior when properties are not set

---

## Phase 6: Implementation Order and Dependencies

### Recommended Build Sequence

```
Phase 1 (Foundation) ─────────────────────────────────────────────
  │
  ├── 1.1 Vendor libraries (FullCalendar, Bootstrap 5.3.3)     [1 day]
  ├── 1.2 Shared CSS (carlos-common.css, variables)             [1 day]
  ├── 1.3 Shared head-includes.jspf, footer.jspf                [0.5 day]
  ├── 1.4 RoleName servlet filter                                 [0.5 day]
  └── 1.5 header.jspf (reusable navbar)                         [2-3 days]
          ├── Security gating for all menu items
          ├── Badge/alert AJAX (nav-alerts.js)
          ├── Keyboard shortcuts
          ├── Mobile responsive collapse
          └── Testing across existing pages
  │
Phase 2 (Data Layer) ─────────────────────────────────────────────
  │
  ├── 2.1 DTO classes (DayViewResponse, etc.)                   [1 day]
  ├── 2.2 ScheduleDayData2Action                                [3-4 days]
  │        ├── Security checks
  │        ├── Provider resolution (group/site/viewAll)
  │        ├── Schedule template loading
  │        ├── Appointment loading with batch enrichment
  │        ├── Status rendering integration
  │        └── JSON serialization
  ├── 2.3 REST reschedule endpoint (drag-and-drop backend)       [1 day]
  │        ├── POST /schedule/appointment/{id}/reschedule
  │        ├── Time-only update with archive + audit
  │        ├── Billed appointment guard
  │        └── Input validation
  └── 2.4 Struts configuration + endpoint testing               [0.5 day]
  │
Phase 3 (Page Action) ────────────────────────────────────────────
  │
  ├── 3.1 ProviderDaySchedule2Action                            [2 days]
  │        ├── All preference loading
  │        ├── Security flag computation
  │        ├── Multi-site data
  │        └── Configuration assembly
  ├── 3.2 Struts configuration                                  [0.5 day]
  └── 3.3 providercontrol.jsp router update                     [0.5 day]
  │
Phase 4 (View Layer) ─────────────────────────────────────────────
  │
  ├── 4.1 providerDaySchedule.jsp (page structure)              [2 days]
  │        ├── Sub-navigation bar
  │        ├── Message areas
  │        ├── Calendar container
  │        └── Config JSON block
  ├── 4.2 provider-day-schedule.js                              [4-5 days]
  │        ├── Multi-instance FullCalendar setup
  │        ├── Custom event rendering (eventContent)
  │        ├── Status click handling (AJAX)
  │        ├── All popup window integrations
  │        ├── Drag-and-drop (eventDrop) + resize (eventResize)
  │        ├── Reason toggle (per-provider + localStorage)
  │        ├── Synchronized navigation
  │        ├── Auto-refresh
  │        └── Keyboard shortcuts
  ├── 4.3 schedule-utils.js (shared utilities)                  [1 day]
  └── 4.4 provider-day-schedule.css                             [1 day]
  │
Phase 5 (Feature Parity) ─────────────────────────────────────────
  │
  ├── 5.1 Remaining indicators (birthday, prevention, etc.)     [1-2 days]
  ├── 5.2 Multi-site color coding                               [1 day]
  ├── 5.3 CAISI integration (if needed)                         [1-2 days]
  ├── 5.4 Health card reader integration                        [0.5 day]
  ├── 5.5 Booking confirmation dialogs                          [1 day]
  └── 5.6 Edge cases (week view link, caseload toggle, etc.)    [1-2 days]
  │
Phase 6 (Testing & Rollout) ──────────────────────────────────────
  │
  ├── 6.1 Unit tests for Action classes                         [2 days]
  ├── 6.2 Integration testing with UI test suite                [2 days]
  ├── 6.3 Cross-browser testing                                 [1 day]
  ├── 6.4 Performance comparison (old vs new)                   [1 day]
  ├── 6.5 Legacy fallback (keep old JSP accessible via flag)    [0.5 day]
  └── 6.6 Cleanup (remove old files after validation)           [0.5 day]
```

---

## Phase 7: Risk Mitigation

### 1. Parallel Availability
Keep the old `appointmentprovideradminday.jsp` accessible during migration. Add a system property `schedule.new_day_view=true` (default `false`) that controls which view `providercontrol.jsp` routes to. This allows:
- Gradual rollout (enable per-clinic)
- Instant rollback if issues discovered
- Side-by-side comparison during testing

### 2. Popup Window Compatibility

> **Appointment lifecycle operations (edit, delete, cancel, no-show, cut, copy) all happen INSIDE the existing popup windows** — they are NOT handled directly by the day schedule. The day schedule's only responsibility is to open the correct popup with the right parameters. The popup then calls `self.opener.refresh()` when done, which triggers `refreshAllCalendars()` on the schedule page.

The existing popup windows call `window.opener.refresh()` to trigger a schedule reload. The new view's IIFE exposes `window.refreshSchedule`, `window.refresh`, and `window.refresh1` as global aliases for `refreshAllCalendars()`, ensuring all existing popup callbacks work. See the `provider-day-schedule.js` IIFE for the alias declarations.

### 3. Performance Budget
The current page makes 34+ DB queries on every load. The new architecture targets:
- Initial page load: 0 DB queries (HTML shell only)
- AJAX data fetch: 8-10 batched queries (down from 34+)
- Status update: 1 query (AJAX, no page reload)
- Reschedule (drag/resize): 1 query (archive) + 1 query (merge) + refresh
- Auto-refresh: Same 8-10 queries on timer

### 4. Security Preservation
Every security check in the current JSP must have an equivalent in the new architecture:
- Page-level access: `ProviderDaySchedule2Action.execute()` checks `_appointment,_day`
- Data endpoint access: `ScheduleDayData2Action.execute()` checks `_appointment`
- Per-feature visibility: Security flags passed as booleans to JSP, then to JS config
- Per-patient tickler access: Checked in `ScheduleDayData2Action` during data assembly
- OWASP encoding: `textContent` in JS (XSS-safe), `fn:escapeXml()` in JSTL HTML contexts, `Encode.forHtml()` in Action for HTML, `Encode.forJavaScript()` for JS string contexts, Jackson `ObjectMapper` for JSON config blocks (never `fn:escapeXml()` in JSON/JS)

### 5. FullCalendar Limitations and Workarounds

| Limitation | Workaround |
|-----------|-----------|
| No native multi-resource view (Premium only) | Multiple FullCalendar instances in Bootstrap grid columns |
| Uniform slot duration only | Use smallest configured duration; background events for visual slot type grouping |
| No built-in appointment status cycling | Custom `eventContent` with click handler → AJAX POST |
| Performance with 10+ calendar instances | **Phase 5 optimization**: Lazy render with `IntersectionObserver` for off-screen providers. Initial implementation (Phase 4) creates all instances eagerly — acceptable for typical 3-8 provider groups. Lazy rendering deferred to Phase 5 if performance testing shows need. |

### 6. Accessibility
The new view improves accessibility over the current table-based layout:
- Semantic HTML (`<nav>`, `<main>`, `<button>`, proper `<label>` elements)
- ARIA labels on interactive elements
- Keyboard navigation (FullCalendar has built-in keyboard support)
- Screen reader compatibility via Bootstrap 5's accessibility features
- Focus management for popups and modals

---

## Appendix A: Existing Endpoint Gap Analysis for ScheduleDayData2Action

The new `ScheduleDayData2Action` needs data that no single existing endpoint provides. Here's what must be assembled:

| Data Need | Existing Source | New Action Approach |
|-----------|----------------|-------------------|
| Provider list for group | `MyGroupDao.getGroupByGroupNo()` | Same DAO call |
| Provider availability | `ScheduleDateDao.findByProviderNoAndDate()` | Batch: one query for all providers |
| Template timecodes | `ScheduleDateDao.search_appttimecode()` | Batch: one query for all providers |
| Template code colors | `ScheduleTemplateCodeDao.findAll()` | Single query, cache result |
| Appointments | `OscarAppointmentDao.searchappointmentday()` | Per-provider (existing DAO) |
| Status rendering | `ApptStatusData` | Use existing class |
| Patient demographics | `DemographicManager.getDemographic()` | **Batch**: collect all demo IDs, single `WHERE IN` query |
| Tickler counts | `TicklerManager.search_tickler()` | **Batch**: collect all demo IDs, single count query |
| Alerts/notes | `DemographicManager.getDemographicCust()` | **Batch**: single `WHERE IN` query |
| Prevention warnings | `PreventionManager.getWarnings()` | **Batch**: single query |
| Reason code labels | `LookupListManager` | Single query, cache |

**New batch DAO methods needed** (add to existing DAOs):
```java
// DemographicDao - new method
Map<Integer, Demographic> getDemographicsByIds(Collection<Integer> ids);

// TicklerDao - new method
Map<Integer, Integer> getActiveTicklerCountsByDemographicNos(
    Collection<Integer> demographicNos, Date date);

// DemographicCustDao - new method
Map<Integer, DemographicCust> getDemographicCustByIds(Collection<Integer> ids);
```

---

## Appendix B: Header JSPF Rollout Plan

After the schedule migration proves the header works, roll it out to other pages:

| Priority | Page | Current Header | Migration Effort |
|----------|------|---------------|-----------------|
| 1 | Month schedule view | `mainMenu.jsp` (inline) | Low — replace include |
| 2 | Encounter/E-Chart | Custom inline header | Medium — different layout |
| 3 | Administration | `nonPatientContextHeader.jspf` | Medium — admin-specific items |
| 4 | Tickler management | Minimal header | Low |
| 5 | Document manager | Minimal header | Low |
| 6 | Billing pages | Province-specific headers | High — complex variations |
| 7 | CAISI/PMmodule | Custom headers | High — CAISI-specific logic |

Each migration follows the same pattern:
1. Create a `*2Action.java` that prepares the model
2. Create new JSP in `WEB-INF/views/<module>/` with `<jsp:include page="/WEB-INF/views/common/header.jspf" />`
3. Point Struts action result to the WEB-INF path
4. Remove the inline header code from old JSP (or replace old JSP entirely)
5. Test security gating and badge counts

**Transitional option**: If a page can't be fully migrated yet but should use the shared header, existing public JSPs can include it too:
```jsp
<%-- Works from /provider/appointmentprovideradminmonth.jsp (public) --%>
<jsp:include page="/WEB-INF/views/common/header.jspf" />
```
This lets the shared header roll out ahead of each page's full migration to WEB-INF.

---

## Appendix C: File Inventory — Files Created, Modified, Deleted

### Created (New Files)
| File | Purpose |
|------|---------|
| `src/main/java/.../provider/web/ProviderDaySchedule2Action.java` | Page action |
| `src/main/java/.../schedule/web/ScheduleDayData2Action.java` | AJAX data endpoint |
| `src/main/java/.../schedule/dto/DayViewResponse.java` | Response DTO |
| `src/main/java/.../schedule/dto/ProviderScheduleDto.java` | Provider schedule DTO |
| `src/main/java/.../schedule/dto/AppointmentSlotDto.java` | Appointment DTO |
| `src/main/java/.../schedule/dto/TemplateSlotDto.java` | Template slot DTO |
| `src/main/java/.../schedule/dto/TemplateCodeDto.java` | Template code metadata DTO |
| `src/main/java/.../schedule/dto/StatusDto.java` | Status rendering DTO |
| `src/main/java/.../web/filter/RoleNameFilter.java` | Servlet filter for header attributes |
| `src/main/webapp/WEB-INF/views/common/header.jspf` | Reusable navbar (protected) |
| `src/main/webapp/WEB-INF/views/common/head-includes.jspf` | Shared CSS/JS includes (protected) |
| `src/main/webapp/WEB-INF/views/common/footer.jspf` | Shared footer (protected) |
| `src/main/webapp/WEB-INF/views/provider/providerDaySchedule.jsp` | New day view (protected) |
| `src/main/webapp/js/schedule/provider-day-schedule.js` | FullCalendar init |
| `src/main/webapp/js/schedule/schedule-utils.js` | Shared utilities |
| `src/main/webapp/js/schedule/nav-alerts.js` | Badge refresh |
| `src/main/webapp/css/carlos-common.css` | Shared base CSS |
| `src/main/webapp/css/schedule/provider-day-schedule.css` | Schedule CSS |
| `src/main/webapp/js/vendor/fullcalendar/index.global.min.js` | FullCalendar |
| `src/main/webapp/js/vendor/fullcalendar/bootstrap5.global.min.js` | FC Bootstrap 5 |
| `src/main/webapp/library/bootstrap/5.3.3/` | Bootstrap 5.3.3 |

### Modified (Existing Files)
| File | Change |
|------|--------|
| `src/main/webapp/WEB-INF/classes/struts.xml` | Add action mappings for `provider/Day` and `schedule/DayData` |
| `src/main/webapp/WEB-INF/web.xml` | Add `RoleNameFilter` registration |
| `src/main/java/.../login/Login2Action.java` | Change post-login redirect to `provider/Day.do` (when flag enabled) |
| `src/main/webapp/provider/providercontrol.jsp` | No change needed (keep old "day" mapping as fallback) |

### Deleted (After Validation)
| File | Reason |
|------|--------|
| `src/main/webapp/provider/providerheader-classic.jspf` | Replaced by WEB-INF/views/common/header.jspf |
| `src/main/webapp/provider/schedulePage.js.jsp` | Logic moved to static JS |
| (Optional) `src/main/webapp/css/receptionistapptstyle.css` | Replaced (keep until all pages migrated) |

### Preserved (No Changes)
| File | Reason |
|------|--------|
| `src/main/webapp/provider/appointmentprovideradminday.jsp` | Kept as fallback |
| `src/main/webapp/provider/tabAlertsRefresh.jsp` | Still used by nav-alerts.js |
| `src/main/webapp/provider/provideraddstatus.jsp` | May still be used by other pages |
| `src/main/webapp/appointment/appointmentcontrol.jsp` | Popup — not migrated yet |
| `src/main/webapp/appointment/addappointment.jsp` | Popup — not migrated yet |
| All existing REST endpoints | Unchanged, new endpoint added alongside |
| All existing DAO/Manager classes | Unchanged, new batch methods added |

---

## Appendix D: Appointment Status System — Complete Reference

The appointment status system in `ApptStatusData.java` is more complex than a simple cycle. It supports **24 status codes** across 3 tiers (base, signed, verified), with terminal states and an optional database-driven mode.

### Status Code Arrays (from `ApptStatusData.java`)

```
Base statuses:    t → T → H → P → E → N → C → B (terminal)
Signed (S):       tS → TS → HS → PS → ES → NS → CS → BS (terminal)
Verified (V):     tV → TV → HV → PV → EV → NV → CV → BV (terminal)
```

**Status meanings (default set):**
| Code | Description | Icon | Notes |
|------|-------------|------|-------|
| `t` | To Do (initial) | `todo.gif` | Default for new appointments |
| `T` | Tagalong | `tagalong.gif` | |
| `H` | Here | `here.gif` | Patient has arrived |
| `P` | Picked | `picked.gif` | Chart picked up |
| `E` | Empty Room | `empty_room.gif` | Patient in exam room |
| `N` | Not Here | `not_here.gif` | Patient didn't arrive |
| `C` | Cancelled | `cancelled.gif` | |
| `B` | Billed | `billed.gif` | **Terminal** — no further cycling |
| `*S` | Signed variant | (same + signed) | Chart signed by provider |
| `*V` | Verified variant | (same + verified) | Chart verified |

**Key behaviors:**
- **Terminal states**: `B`, `BS`, `BV` — clicking does NOT advance further (next status is empty string `""`)
- **Signed modifier**: Appending `S` to any base code creates the signed variant
- **Verified modifier**: Appending `V` to any base code creates the verified variant
- **Special case**: Lowercase `h` is handled separately (see JSP line ~960) — it maps to "Here" with distinct styling

### Database-Driven Mode

When `OscarProperties.ENABLE_EDIT_APPT_STATUS=yes`:
- Status codes, descriptions, icons, colors, and cycling order are loaded from the `appointmentStatus` database table
- The `AppointmentStatusMgr` class reads from DB instead of using hardcoded arrays
- Administrators can customize the status workflow without code changes
- The cycling logic uses `nextStatus` field from the DB row

### Status REST Endpoint

`POST /ws/rs/schedule/appointment/{id}/updateStatus` is a **dumb setter** — it accepts an `AppointmentTo1` body and reads `.getStatus()` to set. It does NOT validate the status transition; the caller must provide the correct next status code. The current JSP computes `nextStatus` client-side from the `ApptStatusData` arrays.

**Implementation note for new view:** The `ScheduleDayData2Action` should include `nextStatus` in each `AppointmentSlotDto` so the client can advance status with a single click. For database-driven mode, query the `appointmentStatus` table for the cycling map.

### Status Colors and Icons

Each status has:
- **Background color**: Used for the appointment slot/cell background
- **Icon**: Small GIF image displayed as the status indicator
- **Short letters**: 1-2 character abbreviation with configurable color (displayed in the appointment cell)
- **Short letter color**: Color for the short letter text

These are returned by `GET /ws/rs/schedule/statuses` as `AppointmentStatusTo1` objects.

---

## Appendix E: Feature Gates — Properties, Facility Settings, and User Properties

The current JSP checks **30+ configuration values** from three different sources: OscarProperties (system-wide `.properties` file), Facility settings (database `Facility` table), and UserProperty (per-user database settings). The new actions must check all of these and pass them as request attributes.

### OscarProperties (System-Wide)

| Property Key | Default | Controls |
|-------------|---------|----------|
| `ENABLE_EDIT_APPT_STATUS` | `no` | Database-driven status codes vs hardcoded |
| `IS_HEALTH_CARD_ENABLED` | `true` | HIN version checking indicator (*) |
| `eform_in_appointment` | `false` | eForm Library link in appointment actions |
| `appt_intake_form` | `off` | Intake Form link in appointment actions (check value `on`) |
| `pregnancy_enabled` | `false` | Pregnancy indicator for patients |
| `hide_eConsult_link` | `false` | eConsult nav link — **inverted logic**: `true` = HIDDEN. Also requires `billregion=ON` |
| `WORKFLOW` | `yes` | Workflow module nav link (check value `yes`) |
| `referral_menu` | `no` | Referral management nav link (check value `yes`) |
| `default_schedule_viewall` | `true` | Default view-all vs scheduled-only |
| `view.appointmentdaysheetbutton` | `false` | Day Sheet button per provider |
| `dashboard_display` | (varies) | Dashboard dropdown visibility |
| `billregion` | `ON` | Billing region (ON/BC/generic) — affects billing links |
| `refresh.appointmentprovideradminday.jsp` | `-1` | Auto-refresh interval in seconds (`-1` = disabled) |
| `receptionist_alt_view` | `no` | Alternate time slot depth calculation using template period length |
| `SHOW_APPT_REASON` | `yes` | Default reason display (via `oscarPropertiesCheck` tag) |
| `SHOW_APPT_REASON_TOOLTIP` | `yes` | Include reason/notes in appointment tooltips |
| `APPT_SHOW_SHORT_LETTERS` | `false` | Short letter status display vs icon image display |
| `APPT_SHOW_FULL_NAME` | `false` | Longer patient name display (`lenLimitedL = 25` vs `11`) |
| `displayAlertsOnScheduleScreen` | `false` | Show "A" alert indicator from DemographicCust |
| `displayNotesOnScheduleScreen` | `false` | Show "N" notes indicator from DemographicCust |
| `ENABLE_APPT_DOC_COLOR` | `no` | Show provider color indicator for patient's primary provider |
| `SHOW_APPT_TYPE_WITH_REASON` | `no` | Prepend appointment type to reason display |
| `TOGGLE_REASON_BY_PROVIDER` | `yes` | Show per-provider reason toggle button |
| `NOT_FOR_CAISI` | `yes` | When `no`, hides billing/lab links in TORONTO_RFQ mode |
| `caisi.search.workflow` | `false` | Routes search to `PMmodule/ClientSearch2.do` (CAISI mode) |
| `useProgramLocation` | `false` | Adds programId location filter to appointment queries (CAISI) |
| `resource_base_url` | (varies) | Help link URL base |
| `indivica_hc_read_enabled` | `false` | Health card reader JavaScript/CSS loading |

### Facility Settings (Database — `loggedInInfo.getCurrentFacility()`)

| Setting | Default | Controls |
|---------|---------|----------|
| `isEnableAnonymous()` | `false` | "Add Anonymous Client" button in sub-nav |
| `isEnablePhoneEncounter()` | `false` | "Phone Encounter" button in sub-nav |

### UserProperty (Per-User Database Settings — `UserPropertyDAO`)

| Property Constant | Default | Controls |
|-------------------|---------|----------|
| `UserProperty.HIDE_OLD_ECHART_LINK_IN_APPT` | `false` | Hide legacy encounter link |
| `UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS` | `false` | 5-day vs 7-day week view toggle |
| `UserProperty.COLOR_PROPERTY` | (none) | Provider's display color |
| `resource_helpHtml` user property | (none) | Inline help HTML panel (sliding div with close button) |
| `resource_baseurl` user property | (none) | Per-user help URL override |
| `caisiBillingPreferenceNotDelete` | `0` | Controls billing edit vs delete mode in CAISI |
| `defaultServiceType` | (varies) | Default service type for add-appointment popup |

### Auto-Refresh Mechanism

The current JSP uses a **`<meta http-equiv="refresh">`** tag (NOT a JavaScript `setInterval`):
```html
<meta http-equiv="refresh" content="300">
```
This causes a full page reload every 5 minutes (300 seconds). The configurable interval comes from `OscarProperties.refresh.appointmentprovideradminday.jsp` (NOT `ProviderPreference.refreshEvery` — that is a different setting). A value of `"-1"` disables auto-refresh.

**New approach:** Replace with JavaScript `setInterval` calling `refreshAllCalendars()`. Read the interval from the OscarProperties value. This is better UX (no full page reload, preserves scroll position, doesn't flash the screen).

---

## Appendix F: Output Encoding Quick Reference

Correct encoding depends on the output context. Using the wrong encoder is a security vulnerability:

| Context | Correct Encoder | Wrong | Example |
|---------|----------------|-------|---------|
| HTML body | `fn:escapeXml()` or `<c:out>` | — | `<span>${fn:escapeXml(name)}</span>` |
| HTML attribute | `fn:escapeXml()` or `<c:out>` | — | `<input value="${fn:escapeXml(val)}" />` |
| JSON config block | Jackson `ObjectMapper` | `fn:escapeXml()` | `<script type="application/json">${jsonFromAction}</script>` |
| JavaScript string | `Encode.forJavaScript()` | `fn:escapeXml()` | Action pre-encodes, JSP uses `${encoded}` |
| CSS value | `Encode.forCssString()` | `fn:escapeXml()` | Rare — use data attributes instead |
| URL parameter | `Encode.forUriComponent()` | `fn:escapeXml()` | Action builds URLs server-side |

**Key rule:** `fn:escapeXml()` produces HTML entities (`&amp;`, `&lt;`, `&#39;`). These are correct for HTML parsers but are NOT decoded by JavaScript parsers or JSON parsers. Using `fn:escapeXml()` in a `<script>` block will produce corrupted data.

---

## Appendix G: Mobile View Strategy

The current `appointmentprovideradminday.jsp` has a **separate mobile code path** (detected via user agent or screen width) that renders a simplified layout. This is NOT simply CSS responsive — it's a different rendering branch.

**Current behavior:**
- Mobile view shows a single-provider schedule with simplified appointment cells
- Navigation collapses differently (hamburger menu was added in some versions)
- Some action links (E, B, M, Rx) are hidden or rearranged

**New approach with Bootstrap 5:**
- The multi-instance FullCalendar grid naturally stacks on small screens (`col-12` breakpoints)
- On mobile, show one provider at a time with a provider selector dropdown
- Use Bootstrap's responsive utilities (`d-none d-md-flex`) to hide/show elements
- FullCalendar has built-in touch support (swipe to navigate dates)
- This replaces the server-side mobile detection with client-side responsive design

**Implementation note:** Add a mobile-specific variant to the column rendering:
```javascript
function getColumnClass(count) {
  // On mobile (<768px), always show one provider full-width
  // On tablet (768-991px), show 2-3 providers
  // On desktop (992px+), show all providers
  if (count === 1) return 'col-12';
  if (count === 2) return 'col-12 col-md-6';
  if (count === 3) return 'col-12 col-md-4';
  if (count === 4) return 'col-12 col-md-6 col-lg-3';
  if (count <= 6) return 'col-12 col-md-4 col-lg-2';
  return 'col-12 col-md-4 col-lg';
}
```

---

## Summary

This migration transforms a 2400-line monolithic JSP into a clean MVC architecture:

| Aspect | Before | After |
|--------|--------|-------|
| Business logic | Inline scriptlets in JSP | Struts 6.8.0 Action classes (*2Action pattern) |
| Data access | 15 Spring beans + 34 queries in JSP | Batched queries in Action with DTOs |
| View rendering | Table-based HTML with scriptlets | Bootstrap 5 + JSTL/EL + FullCalendar |
| CSS framework | Custom + Bootstrap 3 fragments | Bootstrap 5.3.3 |
| Calendar | Custom HTML table grid | FullCalendar 6.1.x Standard (MIT) |
| JavaScript | jQuery 1.12 + Prototype.js + inline | Vanilla JS + FullCalendar + Bootstrap |
| Navigation | 4 duplicate copies | 1 shared header.jspf in WEB-INF |
| JSP protection | All public (URL-accessible) | New views in WEB-INF/views/ (incremental) |
| Security | oscarSec tags + inline checks | Servlet filter + Action + oscarSec tags |
| Reschedule | Manual edit via popup only | Drag-and-drop + resize + popup edit |
| Status updates | Full page reload | AJAX POST to REST endpoint + local refresh |
| Auto-refresh | `<meta http-equiv="refresh">` (full reload) | JS `setInterval` (AJAX, no flash) |
| Mobile support | Separate server-side code path | Bootstrap 5 responsive grid (client-side) |
| Encoding | Mixed/inconsistent | Context-appropriate (see Appendix F) |
| Status system | Partially documented | Full 24-code system documented (see Appendix D) |
