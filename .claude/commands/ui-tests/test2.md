---
description: Run UI Test 2 (comprehensive demographics - create, edit, search, filters)
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

# Run UI Test 2: Comprehensive Demographics

Automated execution of Test 2 (comprehensive demographic module testing) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-2/test-2-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-2/test-2-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 2 is a **comprehensive end-to-end test** that validates:
- Patient creation workflow
- Patient search (by name and HIN)
- Patient editing and data persistence
- Search filters (Active/Inactive/All)
- Healthcare team and clinic status views
- Test cleanup (marking patient inactive)

**Duration**: ~25-30 minutes
**Steps**: 30
**Screenshots**: 30

## Pre-Flight Checks

Before starting, verify application and database are ready:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200`

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT user_name FROM security WHERE user_name='openodoc' LIMIT 1;"`
   - Expected: Shows `openodoc` user exists

3. **Pre-Test Cleanup** (REQUIRED for re-runs):
   ```bash
   mysql -h db -uroot -ppassword oscar -e "DELETE FROM demographic WHERE last_name = 'TEST-UITEST2';"
   ```

**If checks fail**: Run `server start` to start Tomcat, or check `server log` for errors.

## Console Message Logging (CRITICAL)

**You MUST log console messages at these checkpoints:**

| Checkpoint | When to Log |
|------------|-------------|
| After login | Before taking dashboard screenshot |
| After patient creation | After "Add Record" completes |
| After each edit save | After "Update Record" completes |
| After search operations | After search results display |
| End of test | Before closing browser |

**How to log**: Use `browser_console_messages` tool and record:
- Error messages (critical - may indicate failures)
- Warning messages (note but don't fail test)
- 404 resource errors (document which resources)

**Include in final report**: All console messages with timestamps.

## Test Execution Instructions

Follow the 30-step workflow defined in `docs/ui-tests/test-2/test-2-EXECUTION.md`:

### Phase 1: Authentication & Navigation (Steps 1-3)
1. **Login Page** - Navigate to http://localhost:8080/oscar and screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), log console messages, screenshot
3. **Search Page** - Click Search menu, switch to new tab, screenshot

### Phase 2: Create New Patient (Steps 4-8)
4. **Access Add Patient Form** - From search page:
   - Type any search term (e.g., "TEST") and press Enter
   - Wait for results to load
   - Click "Create Demographic" link at bottom of results
   - Screenshot the empty add patient form
5. **Fill Name/DOB** - Fill: Last=TEST-UITEST2, First=Patient, DOB Year=1990, Month=01, Day=01, Sex=Male, screenshot
6. **Fill Contact** - Fill: Phone=416-555-0199, Email=test.uitest2@example.com
   - **IMPORTANT**: Leave HIN field EMPTY (strict validation rejects invalid formats)
   - Screenshot
7. **Set Provider** - Set Doctor (MRP) to "openodoc, doctor", screenshot
8. **Save Patient** - Click "Add Record" button, **wait 5 seconds** using `browser_wait_for`, log console messages, screenshot success

### Phase 3: Verify New Patient (Steps 9-12)
9. **Search New Patient** - Navigate to search, search "TEST-UITEST2", screenshot results
10. **View Profile** - Click patient to open profile, screenshot
11. **Verify Demographics** - Screenshot demographics section
12. **Verify Contact** - Screenshot contact section

### Phase 4: Edit Multiple Fields (Steps 13-18)
13. **Open Edit Form** - Click "Edit" link (in header area), screenshot
14. **Change Phone** - Update to 416-555-9999, screenshot
15. **Change Email** - Update to updated@openoemr.test, screenshot
16. **Change Address** - Update to 456 Updated Avenue, screenshot
17. **Change City** - Update to Ottawa, screenshot
18. **Save Changes** - Click "Update Record", **wait 3 seconds**, log console messages, screenshot

### Phase 5: Verify Edits Persisted (Steps 19-21)
19. **Verify Phone** - Confirm 416-555-9999, screenshot
20. **Verify Email** - Confirm updated@openoemr.test, screenshot
21. **Verify Address** - Confirm 456 Updated Avenue, Ottawa, screenshot

### Phase 6: Search Methods & Filters (Steps 22-26)
22. **Search by HIN** - Change dropdown to "Health Ins. #", search 9876543225 (this is FAKE-Jones's HIN, NOT the test patient - test patient has no HIN), screenshot results
23. **View Patient 1** - Click patient 1, screenshot FAKE-Jones profile
24. **Inactive Filter** - Return to search, click Inactive filter, screenshot
25. **All Filter** - Click All filter, screenshot
26. **Active Filter** - Click Active filter, screenshot

### Phase 7: Healthcare Team & Status (Steps 27-28)
27. **Healthcare Team** - Search for patient 182, view healthcare team section, screenshot
28. **Clinic Status** - View clinic status section, screenshot

### Phase 8: Cleanup (Steps 29-30)
29. **Mark Inactive** - Search TEST-UITEST2, open profile, click Edit, change Patient Status to "IN - Inactive", screenshot
30. **Final Verification** - Click "Update Record", **wait 3 seconds**, log console messages, screenshot showing Inactive status

## Timeout Handling (CRITICAL)

**Always use `browser_wait_for` after these actions to prevent timeouts:**

| Action | Wait Method | Duration |
|--------|-------------|----------|
| After login | `browser_wait_for` with `text: "Schedule"` | auto |
| After clicking "Add Record" | `browser_wait_for` with `time: 5` | 5 seconds |
| After clicking "Update Record" | `browser_wait_for` with `time: 3` | 3 seconds |
| After search submit | `browser_wait_for` with `time: 2` | 2 seconds |
| After filter button click | `browser_wait_for` with `time: 2` | 2 seconds |

**Example usage:**
```
1. Click "Add Record" button
2. Use browser_wait_for tool with parameter: {"time": 5}
3. Use browser_console_messages to log any errors
4. Take screenshot
```

## Error Recovery

**If form submission times out (AbortError):**
1. Take screenshot to capture current state
2. Check browser_console_messages for errors
3. Verify in database if record was created despite timeout:
   ```bash
   mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no, last_name FROM demographic WHERE last_name='TEST-UITEST2' ORDER BY demographic_no DESC LIMIT 1;"
   ```
4. If record exists, continue to next step
5. If record doesn't exist, refresh page and retry

**If page doesn't load:**
1. Check with `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
2. If not 200, run `server restart`
3. Wait for server to be ready, then retry

**If HIN validation fails:**
1. Dismiss any alert dialog using `browser_handle_dialog` with `accept: true`
2. Clear the HIN field completely
3. Leave HIN empty and retry save

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-2/screenshots/test-2-{01-30}-*.png`
- **IMPORTANT**: Press Enter after typing search terms - forms do not auto-submit
- **IMPORTANT**: Always use `browser_wait_for` after form submissions (see Timeout Handling above)
- **IMPORTANT**: Log console messages at each checkpoint (see Console Message Logging above)
- **IMPORTANT**: Leave HIN field empty when creating patient
- Close all browser tabs when done

## Post-Test Verification

After completing all 30 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory:
   ```bash
   cp /workspace/.playwright-mcp/test-2-*.png ui-test-runs/$TIMESTAMP/test-2/screenshots/
   ```

2. **Verify Count**: Confirm exactly 30 screenshots captured

3. **Database Verification**:
   ```bash
   mysql -h db -uroot -ppassword oscar -e "
   SELECT demographic_no, last_name, patient_status, phone, email, address, city
   FROM demographic WHERE last_name = 'TEST-UITEST2';"
   ```
   Expected: status=IN, phone=416-555-9999, email=updated@openoemr.test, address=456 Updated Avenue, city=Ottawa

4. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-2/reports/test-2-results.md` with:
   - Pass/fail for each step
   - **ALL console messages logged during test** (with checkpoint timestamps)
   - Screenshot verification
   - Database verification results
   - Anomalies/issues discovered
   - Overall verdict

5. **Generate Overview**: Update `ui-test-runs/$TIMESTAMP/overview.md` with test results

**Expected Console Messages** (non-blocking):
- `Unexpected token '%'` - JSP template parsing (expected)
- 404: `dateFormatUtils.js`, `master.js` (expected, non-blocking)
- `TypeError: Cannot read properties of undefined (reading 'username')` - Login page (expected)

**Anomalies to Flag**:
- Any NEW 404 errors not in expected list
- JavaScript errors that cause functionality to fail
- Form submission errors
- Missing UI elements

## Success Criteria

Test passes when:
- All 30 steps complete
- All 30 screenshots captured
- New patient created successfully (TEST-UITEST2)
- All edits save and persist correctly
- All search methods work (name, HIN)
- All filters work (Active, Inactive, All)
- Test patient marked Inactive at end
- Only expected console warnings present
- All console messages documented in report

See full criteria in `docs/ui-tests/test-2/test-2-README.md`

## Next Steps

After test completes:
1. Review test report in `ui-test-runs/$TIMESTAMP/test-2/reports/`
2. Compare screenshots against gold standards in `docs/ui-tests/test-2/screenshots/`
3. Document any regressions or unexpected changes
4. **Provide summary of anomalies** in addition to the full report

**Note**: DO NOT update gold standards as part of this test - that's a separate task requiring approval (see `UI-TEST-PROCESS.md`).
