# Provider Day Schedule Screen Migration Plan

## Executive Summary

Migrate the provider day schedule screen (`appointmentprovideradminday.jsp`) — the primary post-login screen in CARLOS EMR — from a monolithic 2400-line JSP with embedded business logic to a proper MVC architecture using Struts2 Actions, JSTL/EL views, Bootstrap 5, and FullCalendar Standard (MIT, v6.1.x).

**Goals:**
- Proper MVC separation: all business logic in Struts2 Action, all rendering in JSP with JSTL/EL
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
  const hash = new URLSearchParams(state).toString();
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

**What goes to the AJAX endpoint as POST body** (not URL params):
- The `ScheduleDayData2Action` receives its parameters as a JSON POST body, not query parameters. This is cleaner and avoids URL length limits:
```javascript
fetch(`${config.contextPath}/schedule/DayData.do`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    date: state.date,
    groupNo: state.group,
    site: state.site,
    viewAll: state.viewAll,
    providerNo: state.provider
  })
});
```

### Backward Compatibility

For links from other pages that still pass query parameters (e.g., `provider/Day.do?year=2026&month=2&day=8`), the `ProviderDaySchedule2Action` converts them to hash fragments on the initial page load:
```java
// In ProviderDaySchedule2Action
String year = request.getParameter("year");
if (year != null) {
    // Set attribute so JSP can emit a redirect-to-hash script
    request.setAttribute("legacyRedirectHash",
        "date=" + year + "-" + month + "-" + day + "&...");
}
```
The JSP then includes:
```jsp
<c:if test="${not empty legacyRedirectHash}">
  <script>
    if (window.location.search) {
      window.location.replace(
        window.location.pathname + '#' + '${fn:escapeXml(legacyRedirectHash)}'
      );
    }
  </script>
</c:if>
```

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

src/main/java/.../schedule/
├── web/
│   └── ScheduleDayData2Action.java         # NEW: AJAX JSON endpoint for calendar data
└── dto/
    ├── DayViewResponse.java                # NEW: Top-level JSON response DTO
    ├── ProviderScheduleDto.java            # NEW: Per-provider schedule + appointments
    ├── AppointmentSlotDto.java             # NEW: Individual appointment for FullCalendar
    ├── TemplateSlotDto.java                # NEW: Background event for slot coloring
    └── ProviderHeaderDto.java              # NEW: Provider header metadata

src/main/webapp/
├── WEB-INF/classes/struts.xml              # MODIFY: Add new action mappings
├── provider/
│   ├── providerDaySchedule.jsp             # NEW: Main day view (JSTL/EL, no scriptlets)
│   └── providercontrol.jsp                 # MODIFY: Route "day" to new action
├── common/
│   ├── header.jspf                         # NEW: Reusable Bootstrap 5 navbar
│   ├── head-includes.jspf                  # NEW: Shared CSS/JS includes
│   └── footer.jspf                         # NEW: Shared footer/scripts
├── js/
│   ├── schedule/
│   │   ├── provider-day-schedule.js        # NEW: FullCalendar initialization + event handling
│   │   ├── schedule-utils.js               # NEW: Shared schedule utilities
│   │   └── nav-alerts.js                   # NEW: Evolved from topnav.js
│   └── vendor/
│       ├── fullcalendar/
│       │   ├── index.global.min.js         # FullCalendar 6.1.x bundle
│       │   └── bootstrap5.global.min.js    # FullCalendar Bootstrap 5 plugin
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
                └── bootstrap-icons.min.css  # NEW: Bootstrap Icons CSS
```

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
     Data Load    │ GET/POST  │  ──── Struts2/JSON ───────→ │ ScheduleDay-     │
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
   - `src/main/webapp/js/vendor/fullcalendar/index.global.min.js` (FullCalendar 6.1.x standard bundle — includes core, dayGrid, timeGrid, list, interaction plugins)
   - `src/main/webapp/js/vendor/fullcalendar/bootstrap5.global.min.js` (Bootstrap 5 theme plugin)

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

**File**: `src/main/webapp/common/header.jspf`

This is the single-source-of-truth navigation bar, replacing the 4 current copies. Design decisions:

**HTML structure** (Bootstrap 5 navbar):
```html
<nav class="navbar navbar-expand-lg navbar-light bg-white border-bottom fixed-top carlos-nav"
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
   document.addEventListener('DOMContentLoaded', () => {
     refreshAllTabAlerts();
     setInterval(refreshAllTabAlerts, 30000); // 30-second polling
   });

   function refreshAllTabAlerts() {
     ['oscar_new_lab', 'oscar_new_msg', 'oscar_new_tickler',
      'oscar_aged_consults', 'oscar_scratch'].forEach(refreshTabAlert);
   }

   function refreshTabAlert(id) {
     fetch(`${contextPath}/provider/tabAlertsRefresh.jsp?id=${id}`)
       .then(r => r.text())
       .then(html => {
         const el = document.getElementById(id);
         if (el) el.innerHTML = html;
       });
   }
   ```

3. **Active page highlighting**: The including page passes a parameter:
   ```jsp
   <c:set var="activeNav" value="schedule" scope="request" />
   <jsp:include page="/common/header.jspf" />
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

**`src/main/webapp/common/head-includes.jspf`**:
```jsp
<%-- Common CSS --%>
<link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/icons/bootstrap-icons.min.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/carlos-common.css">
<%-- CSRF Guard --%>
<script src="${pageContext.request.contextPath}/csrfguard"></script>
```

**`src/main/webapp/common/footer.jspf`**:
```jsp
<%-- Common JS --%>
<script src="${pageContext.request.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/js/schedule/nav-alerts.js"></script>
<script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
```

### Phase 1.4: RoleName Interceptor (Eliminates Scriptlets from Header)

**File**: `src/main/java/.../web/interceptor/RoleNameInterceptor.java`

A Struts2 interceptor that runs before every action and sets:
- `request.setAttribute("roleName", ...)` — for security tags
- `request.setAttribute("curProviderNo", ...)` — logged-in provider number
- `request.setAttribute("curProviderName", ...)` — display name
- `request.setAttribute("contextPath", ...)` — context path

Register in `struts.xml` interceptor stack so every 2Action benefits.

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
    private ConfigDto config;                       // View configuration
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
    private String reasonCodeLabel;
    private String type;
    private String notes;
    private String urgency;
    private String billing;
    private String location;
    private String resources;

    // Provider context
    private String providerNo;
    private String siteName;            // Multi-site
    private String siteColor;           // Multi-site color

    // Quick links context
    private String encounterUrl;
    private String billingUrl;
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
    private String confirm;         // Booking confirmation requirement
    private int bookingLimit;
}
```

### Phase 2.2: ScheduleDayData2Action

**File**: `src/main/java/.../schedule/web/ScheduleDayData2Action.java`

```java
public class ScheduleDayData2Action extends ActionSupport {
    // Spring beans
    private SecurityInfoManager securityInfoManager;
    private ScheduleManager scheduleManager;
    private DemographicManager demographicManager;
    private TicklerManager ticklerManager;
    private PreventionManager preventionManager;
    private UserPropertyDAO userPropertyDao;
    private AppointmentStatusMgr appointmentStatusMgr;
    private ScheduleTemplateCodeDao scheduleTemplateCodeDao;
    private ScheduleDateDao scheduleDateDao;
    private MyGroupDao myGroupDao;
    private ProviderDao providerDao;
    private SiteDao siteDao;
    private LookupListManager lookupListManager;

    // Request parameters
    private String date;            // yyyy-MM-dd
    private String providerNos;     // Comma-separated or group name
    private String groupNo;
    private String site;            // Multi-site filter
    private boolean viewAll;        // Show all providers or scheduled only

    public String execute() {
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

### Phase 2.3: Struts Configuration

Add to `struts.xml`:
```xml
<action name="schedule/DayData"
        class="io.github.carlos_emr.carlos.schedule.web.ScheduleDayData2Action">
    <result name="success" type="json">
        <param name="root">dayViewResponse</param>
    </result>
</action>
```

Or use the direct response writing pattern (write JSON to `response.getWriter()` and return `null`), which is the convention used by existing 2Actions that return JSON.

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
        request.setAttribute("scheduleDate", resolvedDate);     // java.time.LocalDate
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

        return "success";
    }
}
```

### Phase 3.2: Struts Configuration

```xml
<action name="provider/Day"
        class="io.github.carlos_emr.carlos.provider.web.ProviderDaySchedule2Action">
    <result name="success">/provider/providerDaySchedule.jsp</result>
    <result name="security-error">/securityError.jsp</result>
</action>
```

### Phase 3.3: Update providercontrol.jsp Router

Modify the `opToFile` array to route `"day"` to the new action:
```java
{"day", "../provider/Day.do"},  // Forward to new Struts2 action
```

Or better: have the login redirect go directly to `provider/Day.do` instead of `providercontrol.jsp?displaymode=day`.

---

## Phase 4: The JSP View — Bootstrap 5 + FullCalendar

**Objective**: Build the new day schedule JSP using only JSTL/EL, Bootstrap 5, semantic HTML, and FullCalendar.

### Phase 4.1: Page Structure

**File**: `src/main/webapp/provider/providerDaySchedule.jsp`

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
    <jsp:include page="/common/head-includes.jspf" />

    <%-- FullCalendar --%>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/js/vendor/fullcalendar/index.global.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/schedule/provider-day-schedule.css">
</head>
<body>

  <%-- === SHARED HEADER === --%>
  <c:set var="activeNav" value="schedule" scope="request" />
  <jsp:include page="/common/header.jspf" />

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
  <script id="schedule-config" type="application/json">
    {
      "contextPath": "${pageContext.request.contextPath}",
      "date": "${scheduleDate}",
      "year": ${year},
      "month": ${month},
      "day": ${day},
      "startHour": "${startHour}",
      "endHour": "${endHour}",
      "everyMin": ${everyMin},
      "providerNo": "${curProviderNo}",
      "myGroupNo": "${fn:escapeXml(myGroupNo)}",
      "view": ${view},
      "viewAll": ${viewAll},
      "selectedSite": "${fn:escapeXml(selectedSite)}",
      "billingRegion": "${fn:escapeXml(billingRegion)}",
      "hasBillingRights": ${hasBillingRights},
      "hasDoctorLinkRights": ${hasDoctorLinkRights},
      "hasMasterLinkRights": ${hasMasterLinkRights},
      "reasonCodes": ${reasonCodesJson},
      "refreshInterval": 300000,
      "quickLinks": ${quickLinksJson},
      "formLinks": ${formLinksJson},
      "eFormLinks": ${eFormLinksJson}
    }
  </script>

  <%-- === SCRIPTS === --%>
  <jsp:include page="/common/footer.jspf" />
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
  const config = JSON.parse(
    document.getElementById('schedule-config').textContent
  );
  const calendars = new Map();  // providerNo -> FullCalendar instance

  // === INITIALIZATION ===

  async function init() {
    const data = await fetchDayData();
    renderProviderCalendars(data);
    loadSystemMessages();
    startAutoRefresh();
  }

  // === DATA FETCHING ===

  async function fetchDayData() {
    const params = new URLSearchParams({
      date: config.date,
      groupNo: config.myGroupNo,
      site: config.selectedSite,
      viewAll: config.viewAll
    });
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

      // Provider header
      col.innerHTML = `
        <div class="card border-0">
          <div class="card-header py-1 px-2 d-flex justify-content-between
                      align-items-center bg-primary text-white"
               style="font-size: 12px;">
            <div class="d-flex align-items-center gap-1">
              <span class="fw-bold">${escapeHtml(provider.providerName)}</span>
              <span class="badge bg-light text-dark">${provider.appointmentCount}</span>
            </div>
            <div class="d-flex gap-1">
              <button class="btn btn-sm btn-outline-light py-0 px-1 toggle-reason-btn"
                      data-provider="${provider.providerNo}" title="Toggle reasons">
                <i class="bi bi-chat-left-text"></i>
              </button>
              <a href="..." class="btn btn-sm btn-outline-light py-0 px-1" title="Week view">
                <i class="bi bi-calendar-week"></i>
              </a>
              <a href="..." class="btn btn-sm btn-outline-light py-0 px-1" title="Search">
                <i class="bi bi-search"></i>
              </a>
            </div>
          </div>
          <div class="card-body p-0">
            <div id="cal-${provider.providerNo}" class="provider-calendar"></div>
          </div>
        </div>
      `;

      row.appendChild(col);

      // Initialize FullCalendar instance
      const calEl = col.querySelector(`#cal-${provider.providerNo}`);
      const calendar = createCalendar(calEl, provider, data);
      calendars.set(provider.providerNo, calendar);
      calendar.render();
    });
  }

  function getColumnClass(count) {
    // Responsive grid: each provider gets equal width
    // On small screens, stack vertically
    if (count === 1) return 'col-12';
    if (count === 2) return 'col-12 col-md-6';
    if (count === 3) return 'col-12 col-md-4';
    if (count === 4) return 'col-12 col-md-3';
    if (count <= 6) return 'col-12 col-md-2';
    // 7+ providers: use custom fractional widths
    return 'col';
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

    return new FullCalendar.Calendar(el, {
      plugins: ['timeGrid', 'interaction', 'bootstrap5'],
      // NOTE: With global bundle, plugins are auto-registered
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
      expandRows: true,
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
      eventContent: function(arg) {
        if (arg.event.display === 'background') return null;
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

      dateClick: function(info) {
        // Open "Add Appointment" popup
        openAddAppointment(provider.providerNo, info.dateStr);
      },

      eventClick: function(info) {
        info.jsEvent.preventDefault();
        const appt = info.event.extendedProps;
        // Open "Edit Appointment" popup
        openEditAppointment(appt.id, appt.demographicNo);
      },

      selectable: false,  // Disable range selection (use dateClick for new appointments)
      editable: false      // No drag-and-drop (preserve existing workflow)
    });
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
    statusImg.src = config.contextPath + '/images/' + appt.statusIcon;
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

    if (config.hasDoctorLinkRights) {
      actions.appendChild(createActionLink('E', appt.encounterUrl, 'Encounter'));
    }
    if (config.hasBillingRights) {
      actions.appendChild(createActionLink('B', appt.billingUrl, 'Billing'));
    }
    if (config.hasMasterLinkRights) {
      actions.appendChild(createActionLink('M', appt.masterUrl, 'Master Record'));
    }
    if (config.hasDoctorLinkRights) {
      actions.appendChild(createActionLink('Rx', appt.rxUrl, 'Prescription'));
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
        // Refresh calendar data
        refreshAllCalendars();
      }
    } catch (err) {
      console.error('Status update failed:', err);
    }
  }

  // === SYNCHRONIZED NAVIGATION ===

  function navigateAllCalendars(date) {
    calendars.forEach(cal => cal.gotoDate(date));
  }

  async function refreshAllCalendars() {
    const data = await fetchDayData();
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

  function openAddAppointment(providerNo, dateStr) {
    const time = dateStr.split('T')[1] || '09:00';
    const url = `${config.contextPath}/appointment/addappointment.jsp` +
      `?provider_no=${providerNo}&year=${config.year}&month=${config.month}` +
      `&day=${config.day}&start_time=${time}&end_time=&duration=`;
    window.open(url, 'apptProvider', 'width=780,height=600,scrollbars=yes');
  }

  function openEditAppointment(appointmentNo, demographicNo) {
    const url = `${config.contextPath}/appointment/appointmentcontrol.jsp` +
      `?displaymode=edit&appointment_no=${appointmentNo}` +
      `&demographic_no=${demographicNo}`;
    window.open(url, 'apptProvider', 'width=860,height=535,scrollbars=yes');
  }

  // === UTILITY FUNCTIONS ===

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function createActionLink(label, url, title) {
    const a = document.createElement('a');
    a.href = '#';
    a.className = 'badge bg-secondary text-white action-link';
    a.textContent = label;
    a.title = title;
    a.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      window.open(url, label === 'Rx' ? 'oscarRx_appt' : 'apptProvider',
        'width=1024,height=700,scrollbars=yes');
    });
    return a;
  }

  // === INITIALIZATION ===
  document.addEventListener('DOMContentLoaded', init);

  // === GLOBAL REFRESH (called by popup windows via window.opener) ===
  window.refreshSchedule = refreshAllCalendars;

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
```

---

## Phase 5: Feature Parity Checklist

Every feature of the current screen must be preserved. This checklist tracks each:

### Navigation (in header.jspf)
- [ ] CARLOS logo → home
- [ ] Schedule / View All toggle
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
- [ ] Status icon (click to advance through cycle: t→T→H→P→E→N→C→t)
- [ ] Status background color
- [ ] Signed/Verified status modifiers
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

### Behavior
- [ ] Auto-refresh (configurable interval)
- [ ] Scroll position preservation on refresh
- [ ] Password expiration check on load
- [ ] Popup blocker detection
- [ ] Booking confirmation dialog (Yes/Day/Week/OnCall modes)
- [ ] `window.opener.refresh()` support for popup-initiated refreshes
- [ ] Health card reader support (property-gated)
- [ ] Mobile responsive layout
- [ ] Print support (`.noprint` classes)

---

## Phase 6: Implementation Order and Dependencies

### Recommended Build Sequence

```
Phase 1 (Foundation) ─────────────────────────────────────────────
  │
  ├── 1.1 Vendor libraries (FullCalendar, Bootstrap 5.3.3)     [1 day]
  ├── 1.2 Shared CSS (carlos-common.css, variables)             [1 day]
  ├── 1.3 Shared head-includes.jspf, footer.jspf                [0.5 day]
  ├── 1.4 RoleName interceptor                                   [0.5 day]
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
  └── 2.3 Struts configuration + endpoint testing               [0.5 day]
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
The existing popup windows (appointment edit, encounter, billing, prescription, etc.) call `window.opener.refresh()` to trigger a schedule reload. The new view exposes `window.refreshSchedule()` as the global refresh function, and also aliases it to the patterns existing popups use:
```javascript
// Compatibility shims for existing popup callbacks
window.refresh = window.refreshSchedule;
window.refresh1 = window.refreshSchedule;
```

### 3. Performance Budget
The current page makes 34+ DB queries on every load. The new architecture targets:
- Initial page load: 0 DB queries (HTML shell only)
- AJAX data fetch: 8-10 batched queries (down from 34+)
- Status update: 1 query (AJAX, no page reload)
- Auto-refresh: Same 8-10 queries on timer

### 4. Security Preservation
Every security check in the current JSP must have an equivalent in the new architecture:
- Page-level access: `ProviderDaySchedule2Action.execute()` checks `_appointment,_day`
- Data endpoint access: `ScheduleDayData2Action.execute()` checks `_appointment`
- Per-feature visibility: Security flags passed as booleans to JSP, then to JS config
- Per-patient tickler access: Checked in `ScheduleDayData2Action` during data assembly
- OWASP encoding: `textContent` in JS (XSS-safe), `fn:escapeXml()` in JSTL, `Encode.forHtml()` in Action

### 5. FullCalendar Limitations and Workarounds

| Limitation | Workaround |
|-----------|-----------|
| No native multi-resource view (Premium only) | Multiple FullCalendar instances in Bootstrap grid columns |
| Uniform slot duration only | Use smallest configured duration; background events for visual slot type grouping |
| No built-in appointment status cycling | Custom `eventContent` with click handler → AJAX POST |
| No row-spanning for multi-slot appointments | FullCalendar handles this natively — events with start/end spanning multiple slots render as tall blocks |
| Performance with 10+ calendar instances | Lazy render: only create calendars for visible providers; use `IntersectionObserver` for off-screen |

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
2. Update the JSP to `<jsp:include page="/common/header.jspf" />`
3. Remove the inline header code
4. Test security gating and badge counts

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
| `src/main/java/.../schedule/dto/ProviderHeaderDto.java` | Provider header DTO |
| `src/main/java/.../schedule/dto/ConfigDto.java` | Config DTO |
| `src/main/java/.../schedule/dto/StatusDto.java` | Status rendering DTO |
| `src/main/java/.../web/interceptor/RoleNameInterceptor.java` | Security interceptor |
| `src/main/webapp/common/header.jspf` | Reusable navbar |
| `src/main/webapp/common/head-includes.jspf` | Shared CSS/JS includes |
| `src/main/webapp/common/footer.jspf` | Shared footer |
| `src/main/webapp/provider/providerDaySchedule.jsp` | New day view |
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
| `src/main/webapp/WEB-INF/classes/struts.xml` | Add action mappings |
| `src/main/webapp/provider/providercontrol.jsp` | Route "day" to new action |

### Deleted (After Validation)
| File | Reason |
|------|--------|
| `src/main/webapp/provider/providerheader-classic.jspf` | Replaced by header.jspf |
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

## Summary

This migration transforms a 2400-line monolithic JSP into a clean MVC architecture:

| Aspect | Before | After |
|--------|--------|-------|
| Business logic | Inline scriptlets in JSP | Struts2 Action classes |
| Data access | 15 Spring beans + 34 queries in JSP | Batched queries in Action with DTOs |
| View rendering | Table-based HTML with scriptlets | Bootstrap 5 + JSTL/EL + FullCalendar |
| CSS framework | Custom + Bootstrap 3 fragments | Bootstrap 5.3.3 |
| Calendar | Custom HTML table grid | FullCalendar 6.1.x Standard (MIT) |
| JavaScript | jQuery 1.12 + Prototype.js + inline | Vanilla JS + FullCalendar + Bootstrap |
| Navigation | 4 duplicate copies | 1 shared header.jspf |
| Security | oscarSec tags + inline checks | Interceptor + Action + oscarSec tags |
| Status updates | Full page reload | AJAX POST + local refresh |
| Mobile support | Separate CSS file | Bootstrap 5 responsive grid |
