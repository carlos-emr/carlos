---
description: Run UI Test 8 (lab results - inbox, detail view, history, review status)
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

# Run UI Test 8: Lab Results

Automated execution of Test 8 (lab results viewing workflow) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-8/test-8-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-8/test-8-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 8 validates lab results viewing workflow:
- Access lab inbox from provider dashboard
- View lab results list
- Open lab result detail
- View patient context from lab
- Forward lab to provider
- View patient lab history
- Print lab result
- Mark lab as reviewed

**Duration**: ~10-12 minutes
**Steps**: 12
**Screenshots**: 12

**Note**: Lab inbox may be empty in test database. Test focuses on navigation and module accessibility.

## Pre-Flight Checks

Before starting, verify application is ready:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200`

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT 1;"`
   - Expected: Returns 1

**If checks fail**: Run `server start` to start Tomcat, or check `server log` for errors.

## Test Execution Instructions

Follow the 12-step workflow defined in `docs/ui-tests/test-8/test-8-EXECUTION.md`:

### Phase 1: Authentication & Inbox Access (Steps 1-4)
1. **Login Page** - Navigate to http://localhost:8080/oscar, screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), screenshot
3. **Inbox Menu** - Click Inbox in navigation, screenshot
4. **Lab Inbox** - View lab results section, screenshot

### Phase 2: Lab Result Viewing (Steps 5-7)
5. **Lab Detail** - Open lab result (or screenshot empty state), screenshot
6. **Patient Context** - View patient info from lab, screenshot
7. **Lab Values** - View test values and results, screenshot

### Phase 3: Lab Management (Steps 8-10)
8. **Forward Lab** - Click forward button, screenshot dialog
9. **Lab History** - View patient's lab history, screenshot
10. **Print Preview** - Print lab result, screenshot

### Phase 4: Lab Status (Steps 11-12)
11. **Mark Reviewed** - Mark lab as reviewed, screenshot
12. **Status Confirmed** - Verify review status, screenshot

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-8/screenshots/test-8-{01-12}-*.png`
- **Empty inbox is acceptable** - focus on navigation functionality
- Capture browser console messages after test completes
- Close all browser tabs when done

## Critical Execution Notes

### Lab Module Window Handling
Lab result links may open in a **new browser window or tab**. Use `browser_tabs` to:
1. List open tabs after clicking lab-related links
2. Select the appropriate tab for continued interaction
3. Close popup windows after completing actions

### Forward Lab Provider Selection (Step 8)
When forwarding a lab result, provider selection may use an **autocomplete widget**:
- If autocomplete appears, type slowly with `browser_type` using `slowly: true`
- Wait for dropdown suggestions, then click on the provider
- If standard dropdown, use `browser_select_option`

**Example interaction for autocomplete:**
```
1. browser_type(ref=providerField, text="openodoc", slowly=true)
2. browser_snapshot() - verify dropdown appears
3. browser_click(ref=providerOption) - click on provider name
```

### JavaScript Dialogs
Lab actions may trigger **confirmation dialogs**:
- Use `browser_handle_dialog` with `accept: true` to confirm
- Common triggers: marking as reviewed, forwarding labs

### Recommended Waits
- After clicking lab result: `browser_wait_for` with `time: 2` seconds
- After marking reviewed: `browser_wait_for` with `time: 1` second
- After print preview: `browser_wait_for` with `time: 2` seconds

## Handling Empty Lab Inbox

If lab results don't exist in test database:
- Still capture all screenshots showing empty states
- Focus on verifying navigation and module access works
- Document in report that test data was not available
- Test passes if navigation works even with empty data

## Post-Test Verification

After completing all 12 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 12 screenshots captured
3. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-8/reports/test-8-results.md`
4. **Update Overview**: Add results to `ui-test-runs/$TIMESTAMP/overview.md`

**Expected Warnings**:
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings

## Success Criteria

Test passes when:
- All 12 steps complete
- All 12 screenshots captured
- Lab inbox accessible
- Navigation works correctly
- Print preview available (if lab exists)
- Only expected console warnings present

See full criteria in `docs/ui-tests/test-8/test-8-README.md`
