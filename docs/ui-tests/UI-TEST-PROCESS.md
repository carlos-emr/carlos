# OpenO EMR UI Test Process

**Version**: 1.0
**Last Updated**: 2026-01-16

---

## Overview

This document defines the standard process for executing UI tests in OpenO EMR using Playwright MCP. UI tests validate critical user workflows to ensure core functionality works after deployments or code changes.

### Test Framework Scope

This framework supports multiple types of browser-based UI tests:

- **Smoke Tests**: Fast validation of critical paths - typically 2-5 minutes per test
  - Example: Test 1 (login + patient access)
- **Comprehensive Tests**: Thorough end-to-end workflows - typically 10-30 minutes per test
  - Example: Complete encounter workflow from search to billing
- **Regression Tests**: Visual comparison against gold standard screenshots
- **Integration Tests**: Multi-module user journeys

All tests use the same infrastructure (Playwright MCP, screenshot capture, gold standards) regardless of test type.

---

## Directory Structure

### Gold Standard Screenshots
```
docs/ui-tests/
├── test-1/
│   └── screenshots/
│       ├── test-1-01-login-page.png          (Gold standard for Test 1, Step 1)
│       ├── test-1-02-provider-dashboard.png  (Gold standard for Test 1, Step 2)
│       ├── test-1-03-patient-search.png
│       ├── test-1-04-search-results-patient1.png
│       ├── test-1-05-demographic-patient1.png
│       ├── test-1-06-search-results-patient2.png
│       └── test-1-07-demographic-patient2.png
├── test-2/
│   └── screenshots/
│       └── test-2-*.png                      (Future test gold standards)
└── ...
```

**Purpose**: These are the **reference screenshots** that represent correct, expected behavior. All new test runs compare against these.

### Ephemeral Test Output (Timestamped Runs)
```
ui-test-runs/
├── 20260116-113900-123/           (Test run on 2026-01-16 at 11:39:00.123)
│   ├── test-1/                    (Test 1 results from this run)
│   │   ├── screenshots/           (Screenshots from Test 1)
│   │   ├── reports/              (Test 1 execution reports)
│   │   └── comparison/           (Test 1 visual diff results)
│   ├── test-2/                    (Test 2 results, if run in same session)
│   │   └── ...
│   └── overview.md                (Optional summary of all tests in this run)
├── 20260117-091500-456/           (Another test run)
│   ├── test-1/
│   │   └── ...
│   └── overview.md
└── ...
```

**Purpose**: Temporary directory for test execution artifacts. Each test run creates a **unique timestamped directory at the top level** with test subdirectories underneath. This groups all tests from a single execution together, prevents overwriting, and enables easy comparison across test runs. **Not committed to Git** (in `.gitignore`).

---

## Standard UI Test Procedure

This procedure applies to all UI tests (smoke tests, comprehensive tests, regression tests).

### Phase 1: Pre-Test Setup

1. **Verify Application Running**:
   ```bash
   server start
   server log  # Confirm no errors
   ```

2. **Confirm Database State**:
   ```bash
   mysql -h db -uroot -ppassword oscar -e \
     "SELECT user_name, pin FROM security WHERE user_name='openodoc';"
   ```

3. **Create Unique Test Run Directory**:
   ```bash
   # Set timestamp for this test session (includes milliseconds for uniqueness)
   TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
   echo "Test run: $TIMESTAMP"

   # Create directory for Test 1
   mkdir -p ui-test-runs/$TIMESTAMP/test-1/{screenshots,reports,comparison}

   # If running multiple tests in same session, create directories for each:
   # mkdir -p ui-test-runs/$TIMESTAMP/test-2/{screenshots,reports,comparison}
   # mkdir -p ui-test-runs/$TIMESTAMP/test-3/{screenshots,reports,comparison}
   ```

4. **Document Test Environment**:
   - Record date/time (captured in $TIMESTAMP)
   - Note any recent code changes
   - Identify branch/commit being tested

### Phase 2: Test Execution

Execute the UI test following the test-specific procedure (see test-specific sections below).

**Key Requirements**:
- Take screenshots at each major step
- Save screenshots to `ui-test-runs/$TIMESTAMP/test-N/screenshots/`
- Use consistent naming: `test-N-{step}-{description}.png`
- Capture network requests for 404 detection
- Record console errors/warnings
- Document results in `ui-test-runs/$TIMESTAMP/test-N/reports/`

### Phase 3: Screenshot Comparison

After test execution, compare new screenshots against gold standards:

1. **Visual Inspection**:
   ```bash
   # View screenshots from latest test run
   cd ui-test-runs/$TIMESTAMP/test-1/screenshots
   ls -lh

   # Compare with gold standard
   cd /workspace/docs/ui-tests/test-1/screenshots
   ls -lh
   ```

2. **Check for Differences**:
   - **Layout Changes**: Elements moved, missing, or resized
   - **Content Changes**: Different text, data, or images
   - **Styling Changes**: Colors, fonts, spacing modified
   - **Functional Changes**: Missing buttons, broken forms

3. **Acceptable vs. Unacceptable Differences**:

   **✅ Acceptable (Update Gold Standard)**:
   - Intentional UI improvements
   - New features added
   - Date/time stamps (e.g., "2026-01-16" vs "2026-01-17")
   - Dynamic content (e.g., appointment counts, message badges)

   **❌ Unacceptable (Regression)**:
   - Missing elements (broken layout)
   - Error messages appearing
   - Data not loading
   - Functionality broken

4. **Document Comparison Results**:
   Create comparison report in `ui-test-runs/$TIMESTAMP/test-N/reports/comparison-test-N-$TIMESTAMP.md`

### Phase 4: Results Documentation

1. **Record Test Results**:
   - Create test results file: `ui-test-runs/$TIMESTAMP/test-N/reports/test-N-results-$TIMESTAMP.md`
   - Include pass/fail status for each step
   - Document any 404 errors with exact URLs
   - List JavaScript console warnings with file:line references
   - Note differences from gold standard screenshots
   - **Do NOT update gold standards** - that's a separate task (see below)

2. **Create Optional Overview** (if multiple tests run):
   ```bash
   # Summary of all tests in this run
   cat > ui-test-runs/$TIMESTAMP/overview.md <<EOF
   # Test Run Overview - $TIMESTAMP

   ## Tests Executed
   - Test 1: ✅ PASS / ❌ FAIL
   - Test 2: ✅ PASS / ❌ FAIL

   ## Summary
   [Overall findings from this test run]
   EOF
   ```

3. **View Test Runs**:
   ```bash
   # Test runs are automatically preserved in timestamped directories
   # Each run at: ui-test-runs/YYYYMMDD-HHMMSS-mmm/

   # View all test runs (sorted by date)
   ls -lht ui-test-runs/

   # View specific test run
   ls -lh ui-test-runs/20260116-113900-123/

   # Find specific test results
   find ui-test-runs -name "test-1-results-*.md" -type f
   ```

**Note**: Test run cleanup is manual and user-decided. Do not delete test runs automatically.

---

## Updating Gold Standards (Separate Task)

**CRITICAL**: Updating gold standards is a **separate, explicit action** that should **NEVER** be part of the normal testing process.

### When to Update

Update gold standards **only when**:
- Intentional UI improvements have been implemented and approved
- Test data has been deliberately changed
- New features have been added to the interface
- Changes have been reviewed and approved by the team
- **NEVER** update gold standards just because a test failed

### How to Update (After Testing is Complete)

```bash
# IMPORTANT: This is a SEPARATE task from testing
# Do NOT do this as part of the normal test run

# For Test 1 (adjust for other tests):

# 1. Identify the test run you want to promote
TIMESTAMP=20260116-113900-123  # Replace with actual timestamp (includes milliseconds)

# 2. Backup old gold standards
cd /workspace/docs/ui-tests/test-1/screenshots
mkdir -p archive/$(date +%Y%m%d)
cp test-1-*.png archive/$(date +%Y%m%d)/

# 3. Copy new screenshots to gold standard location
cp /workspace/ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-*.png \
   /workspace/docs/ui-tests/test-1/screenshots/

# 4. Verify file sizes reasonable
ls -lh test-1-*.png

# 5. Commit with detailed explanation
git add docs/ui-tests/test-1/screenshots/test-1-*.png
git commit -m "Update Test 1 gold standard screenshots

Reason: [UI improvement / test data update / new feature]
Test Run: $TIMESTAMP
Changes:
- test-1-01: [describe change]
- test-1-02: [describe change]
- ...

Verified: All steps pass, functionality intact
Approved by: [team member]
"
```

---

## Test 1: Smoke Test (Login and Patient Demographics)

### Test ID
`test-1`

### Purpose
Validate the most common provider workflow: logging in as a healthcare provider and accessing patient demographic records through the search interface.

### Gold Standard Location
`docs/ui-tests/test-1/screenshots/test-1-*.png` (7 screenshots)

### Prerequisites

- **Application**: Running on http://localhost:8080/oscar
- **Database**: Contains test data (openodoc user, patient IDs 1 and 182)
- **Playwright MCP**: Configured with headless Chromium

### Test Steps

#### Step 1: Login Page Display

**Action**:
```
Navigate to: http://localhost:8080/oscar/index.jsp
```

**Expected Behavior**:
- Login form rendered with username, password, PIN fields
- Roboto fonts displaying correctly
- No 404 errors

**Screenshot**: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-01-login-page.png`

**Gold Standard**: `docs/ui-tests/test-1/screenshots/test-1-01-login-page.png`

**Comparison Checklist**:
- [ ] Login form layout identical
- [ ] All input fields visible
- [ ] Fonts rendering correctly
- [ ] Logo and branding present

---

#### Step 2: Provider Dashboard

**Action**:
```
Fill username: openodoc
Fill password: openo2025
Fill PIN: 2025
Press Enter
```

**Expected Behavior**:
- Redirect to `/provider/providercontrol.jsp`
- Dashboard loads with navigation menu
- Appointment schedule visible
- User name displayed: "doctor openodoc"
- **NO 404 errors** (verified)

**Screenshot**: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-02-provider-dashboard.png`

**Gold Standard**: `docs/ui-tests/test-1/screenshots/test-1-02-provider-dashboard.png`

**Comparison Checklist**:
- [ ] Navigation menu complete (13 items)
- [ ] Appointment schedule displayed
- [ ] Provider name shown correctly
- [ ] Date matches current date (acceptable difference)

---

#### Step 3: Patient Search Page

**Action**:
```
Click "Search" in navigation menu
```

**Expected Behavior**:
- Search page opens in new tab/window
- Search form with dropdown (Name, Phone, DOB, etc.)
- Active/Inactive/All filter buttons visible
- **Known 404**: `/oscar/js/dateFormatUtils.js` (non-blocking)

**Screenshot**: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-03-patient-search.png`

**Gold Standard**: `docs/ui-tests/test-1/screenshots/test-1-03-patient-search.png`

**Comparison Checklist**:
- [ ] Search dropdown with all options
- [ ] Filter buttons present
- [ ] Clean layout, no errors visible
- [ ] Search box functional

---

#### Step 4: Search Results (First Patient - "FAKE-G")

**Action**:
```
Type search term: FAKE-G
Press Enter
```

**Expected Behavior**:
- Results table displays 10 matching patients
- Table shows: Demographic No., Name, Sex, DOB, Doctor, Status, Phone
- Results include patient ID 182 (FAKE-Gaylord, FAKE-Branda)
- Sortable columns
- **Known JavaScript warning**: `Unexpected token '%'` (cosmetic)

**Screenshot**: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-04-search-results-patient1.png`

**Gold Standard**: `docs/ui-tests/test-1/screenshots/test-1-04-search-results-patient1.png`

**Comparison Checklist**:
- [ ] Search results table rendered
- [ ] 10 patients displayed
- [ ] Patient ID 182 visible
- [ ] All table columns present
- [ ] No layout breakage

---

#### Step 5: First Patient Demographic Record

**Action**:
```
Click on patient ID 182 (FAKE-Gaylord, FAKE-Branda)
```

**Expected Behavior**:
- Demographic page loads showing complete patient record
- All sections visible: Demographic, Contact Info, Health Insurance, Clinic Status
- Left navigation menu with patient functions
- Action buttons at bottom
- **Known 404**: `/oscar/js/custom/default/master.js` (non-blocking)

**Screenshot**: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-05-demographic-patient1.png`

**Gold Standard**: `docs/ui-tests/test-1/screenshots/test-1-05-demographic-patient1.png`

**Comparison Checklist**:
- [ ] Patient name: FAKE-Gaylord, FAKE-Branda
- [ ] Age: 16 years (may change over time - acceptable)
- [ ] DOB: 2009-03-05
- [ ] All demographic sections complete
- [ ] Left navigation menu intact
- [ ] Action buttons visible

---

#### Step 6: Search Results (Second Patient - "FAKE-J")

**Action**:
```
In search box on demographic page, type: FAKE-J
Press Enter
```

**Expected Behavior**:
- New search executes successfully
- Results table shows 10 patients matching "FAKE-J"
- Results include patient ID 1 (FAKE-Jacky, FAKE-Jones)

**Screenshot**: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-06-search-results-patient2.png`

**Gold Standard**: `docs/ui-tests/test-1/screenshots/test-1-06-search-results-patient2.png`

**Comparison Checklist**:
- [ ] Search executed from demographic page
- [ ] Results table rendered
- [ ] Patient ID 1 visible
- [ ] Search term shown: "FAKE-J"

---

#### Step 7: Second Patient Demographic Record

**Action**:
```
Click on patient ID 1 (FAKE-Jacky, FAKE-Jones)
```

**Expected Behavior**:
- Demographic page loads for patient ID 1
- Complete patient record displayed
- All sections: Demographic, Contact, Insurance, Team
- HEART team member shown
- Referral doctor information visible

**Screenshot**: `ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-07-demographic-patient2.png`

**Gold Standard**: `docs/ui-tests/test-1/screenshots/test-1-07-demographic-patient2.png`

**Comparison Checklist**:
- [ ] Patient name: FAKE-Jacky, FAKE-Jones
- [ ] Title: MR
- [ ] Age: 40 years (may change - acceptable)
- [ ] DOB: 1985-06-15
- [ ] HEART team: provider, one
- [ ] All sections complete

---

### Post-Test Validation

After completing all 7 steps:

1. **Verify All Screenshots Captured**:
   ```bash
   ls -1 ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-*.png | wc -l
   # Should output: 7
   ```

2. **Check for Expected Warnings**:
   - ✅ 404: `/oscar/js/dateFormatUtils.js` (search page)
   - ✅ 404: `/oscar/js/custom/default/master.js` (demographic pages)
   - ✅ JavaScript: `Unexpected token '%'` (multiple pages)
   - ✅ TypeError: `Cannot read properties of undefined (reading 'username')` (login page)

3. **Compare Against Gold Standards**:
   ```bash
   # Visual comparison for Test 1
   for file in test-1-*.png; do
     echo "Comparing $file"
     # Manual visual inspection or use image comparison tool
   done
   ```

4. **Document Results**:
   - Update `test-1/test-1-results.md` with findings
   - Note any differences from gold standard
   - Record new 404 errors or warnings
   - Update test metrics

---

## Known Issues (Test 1)

### Non-Blocking Issues

These are **expected** and should be present in every Test 1 run:

1. **404 Errors**:
   - `/oscar/js/dateFormatUtils.js` - Patient search page
   - `/oscar/js/custom/default/master.js` - Demographic pages

2. **JavaScript Warnings**:
   - `Unexpected token '%'` - JSP template variable issues
   - `TypeError: Cannot read properties of undefined (reading 'username')` - Login page focus

3. **Dynamic Content**:
   - Dates change daily (appointment schedule, "today" label)
   - Patient ages increment over time
   - Message counts may vary

### Blocking Issues

If any of these occur, **test fails**:

- Login fails (authentication broken)
- Dashboard doesn't load (navigation broken)
- Search returns no results (database issue)
- Patient records don't display (data access broken)
- Layout completely broken (CSS/rendering issue)
- New 404 errors on pages that previously had none

---

## Future Enhancements

### Phase 2: Visual Regression Testing

Implement automated screenshot comparison:

1. **Image Diff Tools**:
   - Use ImageMagick `compare` command
   - Generate diff images showing pixel differences
   - Calculate similarity percentage

2. **Automated Comparison**:
   ```bash
   compare -metric RMSE \
     docs/ui-tests/test-1/screenshots/test-1-01-login-page.png \
     ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-01-login-page.png \
     ui-test-runs/$TIMESTAMP/test-1/comparison/test-1-01-diff.png
   ```

3. **Threshold-Based Pass/Fail**:
   - Define acceptable difference threshold (e.g., 5%)
   - Auto-pass if within threshold
   - Auto-fail if exceeds threshold
   - Flag for manual review if borderline

### Phase 3: CI/CD Integration

1. **GitHub Actions Workflow**:
   - Run UI tests on every PR
   - Compare screenshots automatically
   - Post comparison results as PR comment
   - Block merge if regressions detected

2. **Automated Reporting**:
   - Generate HTML comparison reports
   - Email results to team
   - Dashboard showing test history

### Phase 4: Additional UI Tests

- **Test 2**: Appointment booking workflow
- **Test 3**: Prescription entry workflow
- **Test 4**: Clinical notes (E-Chart) workflow
- **Test 5**: Lab results viewing workflow

---

## Troubleshooting

### Screenshots Don't Match

**Problem**: New screenshots look different from gold standards

**Diagnosis**:
1. Check if differences are date-related (acceptable)
2. Verify test data unchanged
3. Look for layout breakage
4. Check for new errors in console

**Resolution**:
- If acceptable: Update gold standards
- If regression: Fix the bug, rerun test
- If unclear: Ask team for review

### Test Fails to Complete

**Problem**: Test stops partway through

**Diagnosis**:
1. Check application logs: `server log`
2. Verify database connectivity
3. Confirm test credentials valid
4. Review browser console for errors

**Resolution**:
- Fix application/database issues
- Reset test environment
- Rerun from beginning

### Can't Login

**Problem**: Authentication fails

**Solution**:
```bash
# Reset openodoc password
mysql -h db -uroot -ppassword oscar -e \
  "UPDATE security SET
   password='{bcrypt}\$2b\$12\$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96',
   forcePasswordReset=0
   WHERE user_name='openodoc';"
```

---

## Checklist: Before Running UI Tests

- [ ] Application running (`server start`)
- [ ] Database accessible (`mysql -h db -uroot -ppassword oscar`)
- [ ] Test user exists and password correct
- [ ] TIMESTAMP variable set (`TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)`)
- [ ] Test output directory created (`ui-test-runs/$TIMESTAMP/test-N/`)
- [ ] Playwright MCP available and configured
- [ ] Git working directory clean (no uncommitted changes)
- [ ] Know what code changes to test (branch, commit, PR)

---

## Checklist: After Running UI Tests

- [ ] All screenshots captured (verify count matches expected)
- [ ] Screenshots compared against gold standards
- [ ] Differences documented (acceptable vs. regression)
- [ ] Test results file created in `ui-test-runs/$TIMESTAMP/test-N/reports/`
- [ ] Known warnings/404s verified (expected vs. new)
- [ ] Test run preserved in timestamped directory (automatic)
- [ ] Team notified of results (pass/fail, any issues)
- [ ] **Do NOT update gold standards** - that's a separate task requiring approval
- [ ] **Do NOT delete old test runs** - user will decide manually when needed

---

*OpenO EMR UI Test Process v1.0*
*Last Updated: 2026-01-16*
