---
description: Run complete UI Test Suite (all 9 tests sequentially)
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

# Run Full UI Test Suite

Execute all 9 UI tests sequentially for comprehensive OpenO EMR validation.

**Reference Documentation**:
- **Comprehensive Guide**: `docs/ui-tests/COMPREHENSIVE-TEST-GUIDE.md`
- **Process Guide**: `docs/ui-tests/UI-TEST-PROCESS.md`
- **Test Directory**: `docs/ui-tests/`

## Test Suite Overview

| Test | Name | Steps | Est. Duration |
|------|------|-------|---------------|
| 1 | Smoke Test (Login, Search, Demographics) | 7 | ~3 min |
| 2 | Comprehensive Demographics | 30 | ~25 min |
| 3 | Appointments & Scheduling | 15 | ~12 min |
| 4 | Prescriptions (Rx) | 18 | ~15 min |
| 5 | Ticklers & Messaging | 12 | ~8 min |
| 6 | Encounter & E-Chart | 22 | ~20 min |
| 7 | Billing (Ontario MOH) | 15 | ~12 min |
| 8 | Lab Results | 12 | ~10 min |
| 9 | Prevention & Immunization | 12 | ~10 min |

**Total**: ~143 steps, ~115 minutes

## Pre-Flight Checks (Run Before Starting)

Execute these checks to ensure the environment is ready:

```bash
# 1. Application running
curl -sI http://localhost:8080/oscar/index.jsp | head -1
# Expected: HTTP/1.1 200

# 2. Database connection
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"
# Expected: Returns 1

# 3. Test user exists
mysql -h db -uroot -ppassword oscar -e "SELECT user_name FROM security WHERE user_name='openodoc';"
# Expected: openodoc

# 4. Test patients exist
mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no, last_name FROM demographic WHERE demographic_no IN (1, 182);"
# Expected: 2 rows
```

**If checks fail**: Run `server start` and wait for application to be ready.

## Test Execution Strategy

### Sequential Execution Order

Execute tests in this specific order (dependencies considered):

1. **Test 1** - Smoke test validates basic functionality
2. **Test 2** - Demographics creates test patient used by later tests
3. **Test 3** - Appointments (uses patient from Test 2 or existing patients)
4. **Test 4** - Prescriptions (adds allergy, creates Rx)
5. **Test 5** - Ticklers (creates tickler linked to patient)
6. **Test 6** - Encounters (creates clinical documentation)
7. **Test 7** - Billing (creates billing entry)
8. **Test 8** - Lab Results (may have empty data)
9. **Test 9** - Prevention (adds immunization)

### Session Management

- Create ONE test session directory for all tests
- Each test gets its own subdirectory
- Browser should be closed and reopened between tests to ensure clean state

## Test Session Setup

```bash
# Create unique session directory
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
SESSION_DIR="ui-test-runs/$TIMESTAMP"

# Create directories for all tests
mkdir -p $SESSION_DIR/test-{1,2,3,4,5,6,7,8,9}/{screenshots,reports}
mkdir -p $SESSION_DIR/summary

echo "Full suite session: $SESSION_DIR"
```

## Individual Test Execution

For each test, refer to its execution guide:

### Test 1: Smoke Test
- **Guide**: `docs/ui-tests/test-1/test-1-EXECUTION.md`
- **Screenshots**: 7
- **Key Actions**: Login, dashboard, search, view 2 patient records

### Test 2: Demographics
- **Guide**: `docs/ui-tests/test-2/test-2-EXECUTION.md`
- **Screenshots**: 30
- **Key Actions**: Create patient, edit, search by HIN, filters, cleanup
- **Pre-cleanup**: Delete any existing TEST-UITEST2 patient

### Test 3: Appointments
- **Guide**: `docs/ui-tests/test-3/test-3-EXECUTION.md`
- **Screenshots**: 15
- **Key Actions**: View schedule, create appointment, edit status, views, delete

### Test 4: Prescriptions
- **Guide**: `docs/ui-tests/test-4/test-4-EXECUTION.md`
- **Screenshots**: 18
- **Key Actions**: Rx module, add allergy, search drugs, create Rx, history

### Test 5: Ticklers
- **Guide**: `docs/ui-tests/test-5/test-5-EXECUTION.md`
- **Screenshots**: 12
- **Key Actions**: Tickler dashboard, create tickler, status update, messaging

### Test 6: Encounters
- **Guide**: `docs/ui-tests/test-6/test-6-EXECUTION.md`
- **Screenshots**: 22
- **Key Actions**: E-Chart, create encounter, vitals, diagnosis, notes, panels

### Test 7: Billing
- **Guide**: `docs/ui-tests/test-7/test-7-EXECUTION.md`
- **Screenshots**: 15
- **Key Actions**: Billing module, create entry, service codes, history, report

### Test 8: Lab Results
- **Guide**: `docs/ui-tests/test-8/test-8-EXECUTION.md`
- **Screenshots**: 12
- **Key Actions**: Inbox, lab detail, forward, history, review status

### Test 9: Prevention
- **Guide**: `docs/ui-tests/test-9/test-9-EXECUTION.md`
- **Screenshots**: 12
- **Key Actions**: Prevention module, add immunization, history, report

## Post-Suite Verification

After completing all 9 tests:

### 1. Screenshot Count Verification
```bash
for i in 1 2 3 4 5 6 7 8 9; do
  count=$(ls -1 $SESSION_DIR/test-$i/screenshots/test-$i-*.png 2>/dev/null | wc -l)
  echo "Test $i: $count screenshots"
done
```

Expected counts:
- Test 1: 7
- Test 2: 30
- Test 3: 15
- Test 4: 18
- Test 5: 12
- Test 6: 22
- Test 7: 15
- Test 8: 12
- Test 9: 12
- **Total**: 143

### 2. Generate Suite Summary
Create `$SESSION_DIR/summary/full-suite-results.md`:

```markdown
# Full UI Test Suite Results - [DATE]

## Environment
- Commit: [git commit hash]
- Branch: [branch name]
- Session: $TIMESTAMP

## Test Results Summary

| Test | Name | Steps | Screenshots | Status |
|------|------|-------|-------------|--------|
| 1 | Smoke Test | 7 | X/7 | PASS/FAIL |
| 2 | Demographics | 30 | X/30 | PASS/FAIL |
| 3 | Appointments | 15 | X/15 | PASS/FAIL |
| 4 | Prescriptions | 18 | X/18 | PASS/FAIL |
| 5 | Ticklers | 12 | X/12 | PASS/FAIL |
| 6 | Encounters | 22 | X/22 | PASS/FAIL |
| 7 | Billing | 15 | X/15 | PASS/FAIL |
| 8 | Lab Results | 12 | X/12 | PASS/FAIL |
| 9 | Prevention | 12 | X/12 | PASS/FAIL |

## Overall Status: PASS/FAIL

## Issues Found
- [List any blocking issues]
- [List any regressions]

## Expected Warnings (Non-blocking)
- 404: dateFormatUtils.js, master.js
- JS: "Unexpected token '%'"

## Notes
- [Any observations or anomalies]
```

## Success Criteria

### Suite PASSES when:
- All 9 tests complete
- All 143 screenshots captured
- No blocking errors encountered
- Only expected warnings present

### Suite has PARTIAL PASS when:
- 7+ tests pass completely
- Critical tests (1, 2, 6) pass
- Non-blocking issues in remaining tests

### Suite FAILS when:
- Test 1 (Smoke) fails
- Login doesn't work
- More than 2 tests have blocking errors
- New critical errors appear

## Recovery Steps

If a test fails mid-suite:

1. **Document the failure** - Screenshot and console output
2. **Try to continue** - Skip to next test if possible
3. **Re-run failed test** - After suite completes, re-run individual test
4. **Compare against gold standards** - Check for regressions

## Important Notes

- **DO NOT rush** - Each test should complete properly before starting next
- **Close browser between tests** - Ensures clean state
- **Check console warnings** - Look for NEW errors only
- **Database changes persist** - Tests 2, 4, 5, 6, 7, 9 create data
- **Test 2 cleanup** - Marks TEST-UITEST2 as Inactive, delete if re-running
