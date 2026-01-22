---
description: Run UI Test 7 (Ontario MOH billing - service codes, diagnostics, history)
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

# Run UI Test 7: Billing (Ontario MOH)

Automated execution of Test 7 (Ontario MOH billing workflow) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-7/test-7-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-7/test-7-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 7 validates Ontario MOH billing workflows:
- Access billing module from patient
- Create new billing entry
- Select OHIP service codes
- Add ICD diagnostic codes
- Set service date and submit
- View billing history
- Generate billing report
- View 3rd party options

**Duration**: ~12-15 minutes
**Steps**: 15
**Screenshots**: 15
**Province**: Ontario (ON)

## Pre-Flight Checks

Before starting, verify application and database are ready:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200`

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT hin FROM demographic WHERE demographic_no = 1;"`
   - Expected: Shows patient HIN

3. **Billing Codes Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT COUNT(*) FROM billingservice WHERE region = 'ON';"`
   - Expected: Returns count > 0

**If checks fail**: Run `server start` to start Tomcat, or check billing configuration.

## Test Execution Instructions

Follow the 15-step workflow defined in `docs/ui-tests/test-7/test-7-EXECUTION.md`:

### Phase 1: Authentication & Billing Access (Steps 1-4)
1. **Login Page** - Navigate to http://localhost:8080/oscar, screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), screenshot
3. **Patient Search** - Search "FAKE-J", screenshot results
4. **Billing Module** - Click Billing link, screenshot

### Phase 2: Create Billing Entry (Steps 5-9)
5. **Create Billing** - Click Create Billing, screenshot empty form
6. **Service Code** - Select OHIP code (A003 or similar), screenshot
7. **Diagnostic Code** - Add ICD J06.9, screenshot
8. **Service Date** - Set to today, screenshot
9. **Billing Submitted** - Submit billing, screenshot

### Phase 3: Billing Management (Steps 10-13)
10. **Billing Confirmation** - View confirmation, screenshot
11. **Billing History** - View patient billing history, screenshot
12. **Billing Details** - Click to view entry details, screenshot
13. **Billing Report** - Generate report preview, screenshot

### Phase 4: Additional Options (Steps 14-15)
14. **Third Party** - View 3rd party billing options, screenshot
15. **Billing Statement** - Print statement preview, screenshot

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-7/screenshots/test-7-{01-15}-*.png`
- **IMPORTANT**: Ontario billing requires valid HIN on patient
- Capture browser console messages after test completes
- Close all browser tabs when done

## Critical Execution Notes

### Service Code Selection (Step 6)
OHIP service codes may use an **autocomplete widget** or dropdown:
1. **Type slowly** using `browser_type` with `slowly: true` parameter
2. Wait for **dropdown suggestions** to appear with matching service codes
3. **Click on the service code** from the dropdown to select it
4. If using a standard dropdown, use `browser_select_option` instead

**Example interaction:**
```
1. browser_type(ref=serviceCodeField, text="A003", slowly=true)
2. browser_snapshot() - verify dropdown appears with service codes
3. browser_click(ref=serviceCodeOption) - click on "A003 - Consultation"
```

**Common OHIP codes:** "A003" (consultation), "A004" (general assessment), "K130" (annual checkup)

### Diagnostic Code Autocomplete (Step 7)
The ICD diagnostic code search uses an **autocomplete widget**:
1. **Type slowly** using `browser_type` with `slowly: true` parameter
2. Wait for **dropdown suggestions** to appear with matching ICD codes
3. **Click on the diagnostic code** from the dropdown to select it

**Example interaction:**
```
1. browser_type(ref=diagnosticField, text="J06", slowly=true)
2. browser_snapshot() - verify dropdown appears with ICD codes
3. browser_click(ref=icdOption) - click on "J06.9 - Upper respiratory infection"
```

**Recommended ICD codes:** "J06" (respiratory), "I10" (hypertension), "E11" (diabetes)

### JavaScript Dialogs
Billing actions may trigger **confirmation dialogs**:
- Use `browser_handle_dialog` with `accept: true` to confirm
- Common triggers: submitting billing, deleting entries

## Test Data

### Billing Entry
```
Service Code: A003 (Consultation)
Diagnostic Code: J06.9 (Upper respiratory infection)
Service Date: Today
Provider: openodoc
```

## Post-Test Verification

After completing all 15 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 15 screenshots captured
3. **Database Verification**:
   ```bash
   mysql -h db -uroot -ppassword oscar -e "
   SELECT billing_no, billing_date, total FROM billing
   WHERE demographic_no = 1 ORDER BY billing_no DESC LIMIT 1;"
   ```

4. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-7/reports/test-7-results.md`
5. **Update Overview**: Add results to `ui-test-runs/$TIMESTAMP/overview.md`

**Expected Warnings**:
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings

## Success Criteria

Test passes when:
- All 15 steps complete
- All 15 screenshots captured
- Billing entry created
- Service code selected
- Diagnostic code added
- Billing history viewable
- Only expected console warnings present

See full criteria in `docs/ui-tests/test-7/test-7-README.md`
