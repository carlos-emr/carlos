---
description: Run UI Test 3 (appointments & scheduling - create, edit, views, cancel)
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
  - mcp__playwright__browser_wait_for
  - mcp__playwright__browser_network_requests
  - mcp__playwright__browser_press_key
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

# Run UI Test 3: Appointments & Scheduling

Automated execution of Test 3 (appointment scheduling workflow) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-3/test-3-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-3/test-3-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 3 validates the appointment scheduling workflow:
- Schedule viewing (day/week/month)
- Schedule navigation
- Appointment creation with patient selection
- Appointment editing and status changes
- Appointment history viewing
- Appointment cancellation/deletion

**Duration**: ~10-15 minutes
**Steps**: 15
**Screenshots**: 15

## Pre-Flight Checks

Before starting, verify application and database are ready:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200`

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no, last_name FROM demographic WHERE demographic_no = 1;"`
   - Expected: Shows FAKE-Jones patient exists

**If checks fail**: Run `server start` to start Tomcat, or check `server log` for errors.

## Test Execution Instructions

Follow the 15-step workflow defined in `docs/ui-tests/test-3/test-3-EXECUTION.md`:

### Phase 1: Authentication & Schedule Access (Steps 1-3)
1. **Login Page** - Navigate to http://localhost:8080/oscar, screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), screenshot
3. **Day Schedule** - View provider schedule, screenshot

### Phase 2: Schedule Navigation (Steps 4-5)
4. **Next Week** - Click "W" button for week view, screenshot
   - **Note**: Week view snapshots can be very large (100K+ chars); use screenshots for verification
5. **Return Today** - Click "Today" link to return to current day view, screenshot

### Phase 3: Create Appointment (Steps 6-9)
6. **Open Form** - Click available time slot, screenshot empty form
7. **Select Patient** - Search "FAKE-J", select FAKE-Jacky FAKE-Jones, screenshot
8. **Set Details** - Select type, enter reason "UI Test Appointment", screenshot
9. **Save Appointment** - Save, screenshot appointment on schedule

### Phase 4: Appointment Management (Steps 10-12)
10. **Edit Appointment** - Click appointment to open edit dialog, screenshot
11. **Change Status** - Update status (e.g., to "Confirmed"), screenshot
12. **View History** - View patient's appointment history, screenshot

### Phase 5: Schedule Views (Steps 13-14)
13. **Month View** - Switch to month view, screenshot
14. **Day View** - Return to day view, screenshot

### Phase 6: Cleanup (Step 15)
15. **Delete Appointment** - Cancel/delete test appointment, screenshot
    - **IMPORTANT**: Delete Appt button triggers JavaScript confirmation dialog
    - Use `browser_handle_dialog` with `accept: true` after clicking Delete
    - The dialog may auto-close the edit tab after acceptance

## Critical Execution Notes

### Patient Selection Autocomplete (Step 7)
The patient search uses an **autocomplete widget**:
1. **Type slowly** using `browser_type` with `slowly: true` parameter
2. Wait for **dropdown suggestions** to appear with matching patients
3. **Click on the patient** from the dropdown to select it
4. Do NOT rely on pressing Enter - use the dropdown selection

**Example interaction:**
```
1. browser_type(ref=patientSearchField, text="FAKE-J", slowly=true)
2. browser_snapshot() - verify dropdown appears with patient options
3. browser_click(ref=patientOption) - click on "FAKE-Jones, FAKE-Jacky"
```

### JavaScript Dialogs
- Delete Appt button triggers a confirmation dialog
- Use `browser_handle_dialog` with `accept: true` immediately after the click
- If click times out with AbortError, the dialog may still need handling

### Recommended Waits
- After "Add Appointment": `browser_wait_for` with `time: 3` seconds
- After "Update Appt": `browser_wait_for` with `time: 2` seconds
- After tab switches: `browser_wait_for` with `time: 1` second

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-3/screenshots/test-3-{01-15}-*.png`
- **IMPORTANT**: Press Enter after typing in search fields - forms do not auto-submit
- Select an available time slot for appointment creation
- Capture browser console messages after test completes
- Close all browser tabs when done

## Appointment Data

```
Patient: FAKE-Jacky, FAKE-Jones (ID: 1)
Provider: openodoc
Reason: UI Test Appointment
Notes: Created by UI Test 3
```

## Post-Test Verification

After completing all 15 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 15 screenshots captured
3. **Verify Cleanup**: Test appointment should be deleted
4. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-3/reports/test-3-results.md` with:
   - Pass/fail for each step
   - Console warnings (compare against expected)
   - Screenshot verification
   - Overall verdict
5. **Update Overview**: Add results to `ui-test-runs/$TIMESTAMP/overview.md`

**Expected Warnings** (documented in `test-3-EXECUTION.md`):
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings
- Any NEW warnings should be flagged as potential regressions

## Success Criteria

Test passes when:
- All 15 steps complete
- All 15 screenshots captured
- Appointment created successfully
- Status change persists
- All schedule views work (day/month)
- Test appointment deleted at end
- Only expected console warnings present

See full criteria in `docs/ui-tests/test-3/test-3-README.md`

## Next Steps

After test completes:
1. Review test report in `ui-test-runs/$TIMESTAMP/test-3/reports/`
2. Compare screenshots against gold standards in `docs/ui-tests/test-3/screenshots/`
3. Document any regressions or unexpected changes

**Note**: DO NOT update gold standards as part of this test - that's a separate task requiring approval.
