---
description: Run UI Test 9 (prevention & immunization - add vaccine, history, reports)
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

# Run UI Test 9: Prevention & Immunization

Automated execution of Test 9 (prevention and immunization workflow) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-9/test-9-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-9/test-9-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 9 validates preventive care and immunization tracking:
- Access prevention module from patient chart
- View prevention records
- Add new immunization
- Select vaccine type
- Set administration date
- Save immunization record
- View immunization history
- Print prevention report

**Duration**: ~10-12 minutes
**Steps**: 12
**Screenshots**: 12

## Pre-Flight Checks

Before starting, verify application and database are ready:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200`

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no FROM demographic WHERE demographic_no = 1;"`
   - Expected: Patient ID 1 exists

**If checks fail**: Run `server start` to start Tomcat, or check `server log` for errors.

## Test Execution Instructions

Follow the 12-step workflow defined in `docs/ui-tests/test-9/test-9-EXECUTION.md`:

### Phase 1: Authentication & Prevention Access (Steps 1-4)
1. **Login Page** - Navigate to http://localhost:8080/oscar, screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), screenshot
3. **Patient Search** - Search "FAKE-J", screenshot results
4. **Patient Chart** - Open patient chart, screenshot

### Phase 2: Prevention Records (Steps 5-6)
5. **Preventions Link** - Click Preventions link, screenshot
6. **Prevention Record** - View prevention record, screenshot

### Phase 3: Add Immunization (Steps 7-10)
7. **Add Immunization** - Open add immunization form, screenshot
8. **Vaccine Type** - Select vaccine (Influenza or available), screenshot
9. **Date Set** - Set date to today, screenshot
10. **Immunization Saved** - Save immunization, screenshot

### Phase 4: History & Reports (Steps 11-12)
11. **Immunization History** - View history list, screenshot
12. **Prevention Report** - Print report preview, screenshot

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-9/screenshots/test-9-{01-12}-*.png`
- Prevention link may be in E-Chart left panel or patient demographics
- Capture browser console messages after test completes
- Close all browser tabs when done

## Critical Execution Notes

### Prevention Module Access
The Prevention module may open in a **new browser window or tab**. Use `browser_tabs` to:
1. List open tabs after clicking Preventions link
2. Select the new tab containing the prevention records
3. Continue test execution in the prevention tab

### Vaccine Type Selection (Step 8)
Vaccine selection is done by **clicking the vaccine name directly** in the prevention grid:
- The prevention grid displays all available vaccine types as clickable links
- Click on the desired vaccine name (e.g., "Flu", "Tdap-IPV", "HepB")
- This opens the "Add Prevention Data" form in a new tab with the vaccine pre-selected

**Example interaction:**
```
1. browser_snapshot() - view prevention grid with vaccine links
2. browser_click(element="Flu link", ref=<vaccine_link_ref>) - opens Add Prevention Data form
3. browser_tabs(action="list") - identify new tab
4. browser_tabs(action="select", index=<new_tab>) - switch to Add Prevention Data form
```

**Note**: The Add Prevention Data form auto-populates several fields (see Form Auto-Population section below).

### JavaScript Dialogs
**Note**: During test execution, no confirmation dialogs were encountered.
- The Save button completes the operation without a confirmation dialog
- The form automatically closes and returns to the prevention grid after save
- If dialogs do appear in other scenarios, use `browser_handle_dialog` with `accept: true` to confirm

### Print Functionality (Step 12)
Prevention report generation is a **two-stage process**:
1. **Enable Print** - Click "Enable Print" button, which adds checkboxes next to all prevention records (all checked by default)
2. **Print** - Click "Print" button to generate and download Prevention.pdf

**Note**: The Print button triggers a direct PDF download, not a print preview dialog.
Users can uncheck records before printing for selective reporting.

**Alternative**: The "Print Immunizations Only" button generates a PDF containing only immunization records (excludes screenings).

### Recommended Waits
- After saving immunization: `browser_wait_for` with `time: 2` seconds
- After tab switches: `browser_wait_for` with `time: 1` second
- After Enable Print click: `browser_wait_for` with `time: 1` second for checkboxes to appear

## Test Data

### Immunization Record
```
Vaccine Type: Flu (or other available vaccine type)
Date Administered: Today (auto-populated)
Route: Intramuscular: IM
Site: Left arm (optional)
Lot Number: TEST-LOT-001 (optional)
Provider: openodoc doctor (auto-populated)
```

### Form Auto-Population Behavior
The Add Prevention Data form automatically populates several fields when opened:
- **Prevention**: Pre-filled with the vaccine type clicked (e.g., "Flu")
- **Status**: "Completed" radio button selected by default
- **Date**: Current date/time in format YYYY-MM-DD HH:MM (e.g., 2026-01-19 15:02)
- **Provider**: Logged-in provider (e.g., "openodoc doctor")
- **Creator**: Same as provider (e.g., "openodoc doctor")

**Route Options Available**:
- Intradermal: ID
- Intramuscular: IM
- Intranasal: IN
- Oral: PO
- Subcutaneous: SC

**Required Fields**: Only Prevention type, Status, and Date are required. All other fields (Route, Location, DIN, Dose, Lot, Manufacture, Comments) are optional.

## Post-Test Verification

After completing all 12 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 12 screenshots captured
3. **Save Prevention PDF**: Copy `Prevention.pdf` from `.playwright-mcp/` to `ui-test-runs/$TIMESTAMP/test-9/reports/` directory
4. **Database Verification**:
   ```bash
   mysql -h db -uroot -ppassword oscar -e "
   SELECT id, prevention_type, prevention_date, provider_name FROM preventions
   WHERE demographic_no = 1 AND prevention_type = 'Flu'
   ORDER BY id DESC LIMIT 2;"
   ```
   Expected: New Flu immunization record with today's date

5. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-9/reports/test-9-results.md`
6. **Update Overview**: Add results to `ui-test-runs/$TIMESTAMP/overview.md`

**Expected Warnings**:
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings

## Success Criteria

Test passes when:
- All 12 steps complete
- All 12 screenshots captured
- Prevention module accessible
- Immunization added successfully
- History displays correctly
- Report preview works
- Only expected console warnings present

See full criteria in `docs/ui-tests/test-9/test-9-README.md`
