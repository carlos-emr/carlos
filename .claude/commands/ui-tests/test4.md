---
description: Run UI Test 4 (prescriptions - allergies, drug search, create Rx, history)
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

# Run UI Test 4: Prescriptions (Rx)

Automated execution of Test 4 (prescription management workflow) using Playwright MCP.

**Reference Documentation**:
- **Execution Guide**: `docs/ui-tests/test-4/test-4-EXECUTION.md`
- **Test Overview**: `docs/ui-tests/test-4/test-4-README.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`

## Test Overview

Test 4 validates the prescription management workflow:
- Access Rx module from patient chart
- Add patient allergy (Penicillin)
- Search drug database
- Create new prescription (Acetaminophen)
- Check for drug interactions
- View prescription history
- Re-prescribe existing medication
- Print preview

**Duration**: ~15-20 minutes
**Steps**: 18
**Screenshots**: 18

## Pre-Flight Checks

Before starting, verify application and database are ready:

1. **Application Check**: Run `curl -sI http://localhost:8080/oscar/index.jsp | head -1`
   - Expected: `HTTP/1.1 200`

2. **Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no FROM demographic WHERE demographic_no = 1;"`
   - Expected: Patient ID 1 exists

3. **Drug Database Check**: Run `mysql -h db -uroot -ppassword oscar -e "SELECT COUNT(*) FROM drugs;"`
   - Expected: Returns count (database has drug data)

**If checks fail**: Run `server start` to start Tomcat, or check `server log` for errors.

## Test Execution Instructions

Follow the 18-step workflow defined in `docs/ui-tests/test-4/test-4-EXECUTION.md`:

### Phase 1: Authentication & Patient Access (Steps 1-4)
1. **Login Page** - Navigate to http://localhost:8080/oscar, screenshot
2. **Provider Dashboard** - Login (openodoc/openo2025/2025), screenshot
3. **Patient Search** - Search "FAKE-J", screenshot results
4. **Patient Chart** - Open patient chart, screenshot

### Phase 2: Prescription Module Access (Steps 5-6)
5. **Rx Module** - Click "Rx" link to open prescription module, screenshot
6. **Current Meds** - View current medications list, screenshot

### Phase 3: Allergy Management (Steps 7-8)
7. **Add Allergy** - Add Penicillin allergy with reaction "Hives", screenshot
8. **Allergy Saved** - Confirm allergy appears in list, screenshot

### Phase 4: Create Prescription (Steps 9-14)
9. **New Rx Form** - Click "New Prescription", screenshot empty form
10. **Drug Search** - Search "Acetaminophen", screenshot results
11. **Drug Selected** - Select Acetaminophen 500mg, add dosage instructions, screenshot
12. **Quantity Set** - Set quantity=30, refills=0, screenshot
13. **Interaction Check** - Run/view interaction check, screenshot
14. **Rx Saved** - Save prescription, screenshot confirmation

### Phase 5: Prescription Management (Steps 15-18)
15. **Rx History** - View prescription history list, screenshot
16. **Rx Details** - Click to view prescription details, screenshot
17. **Re-Prescribe** - Click re-prescribe (view form, don't save duplicate), screenshot
18. **Print Preview** - Open print preview, screenshot

## Key Requirements

- Create unique timestamp: `TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`
- Save screenshots to: `ui-test-runs/$TIMESTAMP/test-4/screenshots/test-4-{01-18}-*.png`
- **IMPORTANT**: Press Enter after typing in search fields
- Capture browser console messages after test completes
- Close all browser tabs when done

## Critical Execution Notes

### Allergy Dialog Handling (Step 7-8)
When clicking "Add Allergy" to save an allergy, a **JavaScript confirmation dialog** will appear.
- Use `browser_handle_dialog` with `accept: true` to confirm the dialog
- The dialog blocks the page until handled - snapshot will fail if dialog is pending

### Drug Search Autocomplete (Step 10-11)
The drug search uses a **YUI AutoComplete widget** that requires specific interaction:
1. **Type slowly** using `browser_type` with `slowly: true` parameter
2. The autocomplete requires **minimum 3 characters** to trigger suggestions
3. **Wait for dropdown** to appear with drug suggestions
4. **Click on the drug item** from the dropdown list to select it
5. Do NOT click the "Search" button - use the autocomplete dropdown instead

**Example interaction:**
```
1. browser_type(ref=searchField, text="asa", slowly=true)
2. browser_snapshot() - verify dropdown appears with drug options
3. browser_click(ref=drugOption) - click on desired drug from dropdown
```

**Recommended search terms:** "asa" (for aspirin), "acet" (for acetaminophen), "ibup" (for ibuprofen)

## Test Data

### Allergy Data
```
Allergen: Penicillin
Type: Drug
Reaction: Hives
Severity: Moderate
```

### Prescription Data
```
Drug: Acetaminophen 500mg Tablet
Dosage: Take 1 tablet by mouth every 6 hours as needed for pain
Quantity: 30
Refills: 0
```

## Post-Test Verification

After completing all 18 steps:

1. **Copy Screenshots**: Playwright MCP saves to `.playwright-mcp/` - copy to test run directory
2. **Verify Count**: Confirm exactly 18 screenshots captured
3. **Database Verification**:
   ```bash
   mysql -h db -uroot -ppassword oscar -e "
   SELECT description FROM allergies WHERE demographic_no = 1 ORDER BY id DESC LIMIT 1;"
   ```
   Expected: Shows Penicillin allergy

4. **Generate Report**: Create `ui-test-runs/$TIMESTAMP/test-4/reports/test-4-results.md`
5. **Update Overview**: Add results to `ui-test-runs/$TIMESTAMP/overview.md`

**Expected Warnings**:
- 404: dateFormatUtils.js, master.js (non-blocking)
- JavaScript: "Unexpected token '%'" warnings
- Any NEW warnings should be flagged as potential regressions

## Success Criteria

Test passes when:
- All 18 steps complete
- All 18 screenshots captured
- Allergy added successfully
- Prescription created successfully
- Drug search returns results
- Interaction check runs
- Print preview displays
- Only expected console warnings present

See full criteria in `docs/ui-tests/test-4/test-4-README.md`

## Next Steps

After test completes:
1. Review test report in `ui-test-runs/$TIMESTAMP/test-4/reports/`
2. Compare screenshots against gold standards in `docs/ui-tests/test-4/screenshots/`
3. Document any regressions or unexpected changes

**Note**: DO NOT update gold standards as part of this test - that's a separate task requiring approval.
