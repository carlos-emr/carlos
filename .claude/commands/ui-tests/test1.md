---
description: Run UI Test 1 (smoke test - login, search, patient records)
allowed-tools:
  - mcp__playwright__browser_navigate
  - mcp__playwright__browser_snapshot
  - mcp__playwright__browser_take_screenshot
  - mcp__playwright__browser_click
  - mcp__playwright__browser_type
  - mcp__playwright__browser_fill_form
  - mcp__playwright__browser_tabs
  - mcp__playwright__browser_close
  - mcp__playwright__browser_handle_dialog
  - mcp__playwright__browser_console_messages
  - mcp__playwright__browser_network_requests
  - mcp__playwright__browser_press_key
  - mcp__playwright__browser_select_option
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
  - Bash(TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N))
  - Write(path:ui-test-runs/**)
model: claude-sonnet-4-5-20250929
---

# Run UI Test 1: Smoke Test

Automated execution of Test 1 (login + patient demographics) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-1/test-1-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-1/test-1-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Pre-Flight Checks

Before starting, verify application and database are ready by running these checks:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200` (if you get connection refused or 000, the app server isn't running)

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT user_name FROM security WHERE user_name='openodoc' LIMIT 1;"`
   - Expected: Shows `openodoc` user exists

**If checks fail**: Run `server start` to start Tomcat, or check `server log` for errors.

## Test Execution Instructions

Follow the 7-step workflow defined in `docs/ui-tests/test-1/test-1-EXECUTION.md`:

1. **Login Page** - Navigate and screenshot login form
2. **Provider Dashboard** - Login (openodoc/openo2025/2025) and screenshot
3. **Patient Search** - Click Search menu and screenshot
4. **Search "FAKE-G"** - Type "FAKE-G" and **press Enter to submit**, screenshot results (verify patient 182 visible)
5. **Patient #182** - Click patient 182 and screenshot demographic record
6. **Search "FAKE-J"** - Type "FAKE-J" and **press Enter to submit**, screenshot results (verify patient 1 visible)
7. **Patient #1** - Click patient 1 and screenshot demographic record

**Key Requirements**:
- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-{01-07}-*.png`
- Capture browser console messages after test completes
- Close all browser tabs when done

## Post-Test Verification

After completing all 7 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 7 screenshots captured
3. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-1/reports/test-1-results.md` with:
   - Pass/fail for each step
   - Console warnings (compare against expected - see docs)
   - Screenshot verification
   - Overall verdict
4. **Generate Overview**: Create `ui-test-runs/$TIMESTAMP/overview.md` summarizing test run

**Expected Warnings** (documented in `test-1-EXECUTION.md`):
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings
- Any NEW warnings should be flagged as potential regressions

## Success Criteria

✅ **Test passes when**:
- All 7 steps complete
- All 7 screenshots captured
- Only expected console warnings present
- No blocking errors

See full criteria in `docs/ui-tests/test-1/test-1-README.md`

## Next Steps

After test completes:
1. Review test report in `ui-test-runs/$TIMESTAMP/test-1/reports/`
2. Compare screenshots against gold standards in `docs/ui-tests/test-1/screenshots/`
3. Document any regressions or unexpected changes

**Note**: DO NOT update gold standards as part of this test - that's a separate task requiring approval (see `UI-TEST-PROCESS.md`).
