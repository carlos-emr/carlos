---
description: Run UI Test 5 (ticklers & messaging - create, status, inbox)
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

# Run UI Test 5: Ticklers & Messaging

Automated execution of Test 5 (tickler and messaging workflows) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-5/test-5-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-5/test-5-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 5 validates tickler (task reminder) and messaging workflows:
- Access tickler dashboard
- Create tickler linked to patient
- Assign to provider and set date
- Update tickler status
- Access messaging module
- View inbox

**Duration**: ~8-10 minutes
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

Follow the 12-step workflow defined in `docs/ui-tests/test-5/test-5-EXECUTION.md`:

### Phase 1: Authentication & Tickler Access (Steps 1-3)
1. **Login Page** - Navigate to http://localhost:8080/oscar, screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), screenshot
3. **Tickler Dashboard** - Click Tickler menu, screenshot

### Phase 2: Create Tickler (Steps 4-7)
4. **New Tickler Form** - Click Create New, screenshot empty form
5. **Patient Linked** - Search "FAKE-J" and link patient, screenshot
6. **Tickler Details** - Set message, date (+7 days), assign to openodoc, screenshot
7. **Tickler Saved** - Save tickler, screenshot confirmation

### Phase 3: Tickler Management (Steps 8-9)
8. **Tickler List** - View tickler in list, screenshot
9. **Status Updated** - Change status to Completed/Closed, screenshot

### Phase 4: Messaging (Steps 10-12)
10. **Messaging Module** - Click Msg menu, screenshot
11. **Compose Message** - View compose form, screenshot
12. **Inbox** - View inbox, screenshot

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-5/screenshots/test-5-{01-12}-*.png`
- **IMPORTANT**: Press Enter after typing in search fields
- Capture browser console messages after test completes
- Close all browser tabs when done

## Critical Execution Notes

### Patient Linking Autocomplete (Step 5)
When linking a patient to a tickler, the patient search uses an **autocomplete widget**:
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

### Provider Assignment (Step 6)
Provider selection may use a **dropdown** or **autocomplete**:
- If standard dropdown, use `browser_select_option` with the provider name
- If autocomplete, follow the same pattern as patient selection above

### JavaScript Dialogs
Tickler actions may trigger **confirmation dialogs**:
- Use `browser_handle_dialog` with `accept: true` to confirm
- Common triggers: deleting ticklers, changing status

### Recommended Waits
- After saving tickler: `browser_wait_for` with `time: 2` seconds
- After status change: `browser_wait_for` with `time: 1` second

## Test Data

### Tickler Data
```
Message: UI Test 5 - Follow up call needed
Service Date: (today + 7 days)
Assigned To: openodoc
Priority: Normal
Patient: FAKE-Jacky FAKE-Jones (ID: 1)
```

## Post-Test Verification

After completing all 12 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 12 screenshots captured
3. **Database Verification**:
   ```bash
   mysql -h db -uroot -ppassword oscar -e "
   SELECT tickler_no, message, status FROM tickler
   WHERE message LIKE '%UI Test 5%' ORDER BY tickler_no DESC LIMIT 1;"
   ```

4. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-5/reports/test-5-results.md`
5. **Update Overview**: Add results to `ui-test-runs/$TIMESTAMP/overview.md`

**Expected Warnings**:
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings

## Success Criteria

Test passes when:
- All 12 steps complete
- All 12 screenshots captured
- Tickler created and linked to patient
- Status update works
- Messaging module accessible
- Only expected console warnings present

See full criteria in `docs/ui-tests/test-5/test-5-README.md`
