---
description: Run UI Test 6 (encounter & E-Chart - vitals, diagnosis, notes, panels)
allowed-tools:
  - mcp__playwright__browser_navigate
  - mcp__playwright__browser_snapshot
  - mcp__playwright__browser_take_screenshot
  - mcp__playwright__browser_click
  - mcp__playwright__browser_type
  - mcp__playwright__browser_fill_form
  - mcp__playwright__browser_select_option
  - mcp__playwright__browser_tabs
  - mcp__playwright__browser_close
  - mcp__playwright__browser_handle_dialog
  - mcp__playwright__browser_console_messages
  - mcp__playwright__browser_network_requests
  - mcp__playwright__browser_press_key
  - mcp__playwright__browser_wait_for
  - mcp__playwright__browser_run_code
  - mcp__playwright__browser_evaluate
  - Bash(mkdir ui-test-runs/*)
  - Bash(cp .playwright-mcp/ui-test-runs/* ui-test-runs/*)
  - Bash(cp /workspace/.playwright-mcp/* ui-test-runs/*)
  - Bash(ls ui-test-runs/*)
  - Bash(ls /workspace/.playwright-mcp/*)
  - Bash(wc *)
  - Bash(curl * http://localhost:8080/*)
  - Bash(mysql -h db -uroot -ppassword oscar *)
  - Bash(mysql -h db -uroot -ppassword oscar -e *)
  - Bash(mysql * oscar * 2>&1 | tail -1)
  - Bash(date *)
  - Bash(TIMESTAMP=*)
  - Write(path:ui-test-runs/**)
model: claude-sonnet-4-5-20250929
---

# Run UI Test 6: Encounter & E-Chart

Automated execution of Test 6 (clinical documentation and E-Chart workflow) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-6/test-6-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-6/test-6-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 6 validates the core clinical documentation workflow:
- Access E-Chart from patient search
- View patient clinical summary
- Create new encounter
- Add vital signs (BP, HR, Temp)
- Add diagnosis with ICD code
- Enter assessment and plan notes
- Navigate E-Chart panels (allergies, meds, labs, prevention)
- Save encounter and view history
- Print preview

**Duration**: ~20-25 minutes
**Steps**: 22
**Screenshots**: 22

## Pre-Flight Checks

Before starting, verify application and database are ready:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200`

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no FROM demographic WHERE demographic_no = 1;"`
   - Expected: Patient ID 1 exists

**If checks fail**: Run `server start` to start Tomcat, or check `server log` for errors.

## Test Execution Instructions

Follow the 22-step workflow defined in `docs/ui-tests/test-6/test-6-EXECUTION.md`:

### Phase 1: Authentication & E-Chart Access (Steps 1-4)
1. **Login Page** - Navigate to http://localhost:8080/oscar, screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), screenshot
3. **Patient Search** - Search "FAKE-J", screenshot results
4. **E-Chart Link** - Click E-Chart link for patient, screenshot

### Phase 2: E-Chart Overview (Steps 5-6)
5. **E-Chart Overview** - View main E-Chart page, screenshot
6. **Patient Header** - View patient info header, screenshot

### Phase 3: Create Encounter (Steps 7-9)
7. **New Encounter** - Click "+" to create encounter, screenshot
8. **Encounter Editor** - View encounter editor, screenshot
9. **Encounter Type** - Select encounter type, screenshot

### Phase 4: Vital Signs (Steps 10-11)
10. **Vitals Form** - Enter BP=120/80, HR=72, Temp=37.0, screenshot
11. **Vitals Saved** - Save measurements, screenshot

### Phase 5: Diagnosis & Issues (Steps 12-13)
12. **Diagnosis Search** - Search "upper respiratory" or ICD J06, screenshot
13. **Diagnosis Added** - Link diagnosis to encounter, screenshot

### Phase 6: Clinical Notes (Steps 14-15)
14. **Assessment Text** - Enter assessment note, screenshot
15. **Plan Text** - Enter plan note, screenshot

### Phase 7: Panel Navigation (Steps 16-19)
16. **Allergies Panel** - View allergies, screenshot
17. **Medications Panel** - View medications, screenshot
18. **Labs Panel** - View lab results, screenshot
19. **Prevention Panel** - View prevention records, screenshot

### Phase 8: Finalize Encounter (Steps 20-22)
20. **Encounter Saved** - Save encounter, screenshot
21. **Print Preview** - Print encounter note preview, screenshot
22. **Encounter History** - View encounter history list, screenshot

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-6/screenshots/test-6-{01-22}-*.png`
- **IMPORTANT**: Press Enter after typing in search fields
- E-Chart may open in new window/tab - handle appropriately
- Capture browser console messages after test completes
- Close all browser tabs when done

## Critical Execution Notes

### ⚠️ E-Chart Entry Point - CRITICAL

**DO NOT** navigate directly to `CaseManagementEntry.do` - this causes 500 errors due to missing session state.

**MUST USE** `IncomingEncounter.do` as the entry point. This servlet properly initializes the session before redirecting to CaseManagementEntry.

**Correct URL Pattern:**
```
/oscar/oscarEncounter/IncomingEncounter.do?providerNo={providerNo}&appointmentNo=&demographicNo={demographicNo}&curProviderNo=&reason=Tel-Progress+Note&encType=&curDate={YYYY-M-DD}&appointmentDate=&startTime=&status=
```

**Example:**
```
http://localhost:8080/oscar/oscarEncounter/IncomingEncounter.do?providerNo=999998&appointmentNo=&demographicNo=1373&curProviderNo=&reason=Tel-Progress+Note&encType=&curDate=2026-1-19&appointmentDate=&startTime=&status=
```

**How to get the correct URL:**
1. Use `browser_evaluate` to extract the onclick handler from the E-Chart "E" link:
   ```javascript
   (element) => { return element.getAttribute('onclick'); }
   ```
2. The onclick contains: `popupEChart(710,1024,'/oscar/oscarEncounter/IncomingEncounter.do?...')`
3. Extract the URL and navigate directly to it in the same tab

### E-Chart Tab Switching Issues

**AVOID using `browser_tabs select`** on E-Chart pages - the heavy AJAX/DOM updates cause Playwright timeouts.

**RECOMMENDED APPROACH:**
1. Extract the E-Chart URL from the link's onclick handler using `browser_evaluate`
2. Navigate directly to the URL in the **same tab** using `browser_navigate`
3. This avoids tab switching issues entirely

### Diagnosis Search Autocomplete (Steps 12-13)
The diagnosis/ICD code search may use an **autocomplete widget**:
1. **Type slowly** using `browser_type` with `slowly: true` parameter
2. Wait for **dropdown suggestions** to appear with matching diagnoses
3. **Click on the diagnosis item** from the dropdown to select it
4. If no autocomplete appears, try pressing Enter to search

**Example interaction:**
```
1. browser_type(ref=diagnosisField, text="J06", slowly=true)
2. browser_snapshot() - verify dropdown appears with ICD options
3. browser_click(ref=diagnosisOption) - click on "J06.9 - Upper respiratory infection"
```

**Recommended search terms:** "J06" (respiratory), "I10" (hypertension), "E11" (diabetes)

### Hover Menus and Click Interception

E-Chart panels have hover menus that can intercept clicks. When clicking "+" buttons:

**Problem:** Hovering triggers a dropdown menu that blocks the click action, causing timeouts.

**Solution:** Use `browser_run_code` with Playwright's `force: true` option:
```javascript
async (page) => {
  const link = await page.locator('#menuTitle3 > h3 > a').first();
  await link.click({ force: true });
}
```

### Measurements Panel Configuration

**Known Limitation:** The Measurements panel depends on configured measurement groups.

- Default installation may only have "Test" group (which is empty)
- Vital signs groups (BP, HR, TEMP) may not be configured
- If only "Test" appears in the menu, vital signs entry will be limited

**Workaround:** Document the limitation and proceed with other test phases.

### Issue Search Behavior

The "Search Issue" field searches for **existing patient issues**, NOT ICD codes.

- Searching "J06" or "respiratory" returns "No search results" if no matching issues exist
- To add new diagnoses, use the Disease Registry or encounter note templates

### JavaScript Dialogs
Various E-Chart actions may trigger **confirmation dialogs**:
- Use `browser_handle_dialog` with `accept: true` to confirm
- Common triggers: saving encounter, adding issues, panel navigation

## Test Data

### Vital Signs
```
Blood Pressure: 120/80 mmHg
Heart Rate: 72 bpm
Temperature: 37.0 C
```

### Diagnosis
```
ICD Code: J06.9
Description: Acute upper respiratory infection
```

### Clinical Notes
```
Assessment: Patient presents with symptoms of upper respiratory infection.
Plan: Rest, fluids, symptomatic treatment. Follow up in 1 week if not improved.
```

## Post-Test Verification

After completing all 22 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 22 screenshots captured
3. **Database Verification**:
   ```bash
   mysql -h db -uroot -ppassword oscar -e "
   SELECT id, observation_date FROM casemgmt_note
   WHERE demographic_no = 1 ORDER BY id DESC LIMIT 1;"
   ```

4. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-6/reports/test-6-results.md`
5. **Update Overview**: Add results to `ui-test-runs/$TIMESTAMP/overview.md`

**Expected Warnings**:
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings

## Success Criteria

Test passes when:
- All 22 steps complete
- All 22 screenshots captured
- E-Chart opens successfully
- Encounter created and saved
- Vital signs recorded
- Diagnosis added
- Clinical notes saved
- All panels accessible
- Print preview works
- Only expected console warnings present

See full criteria in `docs/ui-tests/test-6/test-6-README.md`
