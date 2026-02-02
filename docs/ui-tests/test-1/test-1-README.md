# Test 1 - Smoke Test (Login and Patient Demographics)

## Overview

Test 1 is a foundational smoke test that validates the most common workflow in OpenO EMR: logging in as a healthcare provider and accessing patient demographic records through the search interface.

**Test Duration**: ~2 minutes
**Test Type**: Manual (via Playwright MCP)
**Status**: ✅ Passing (100%)

---

## What This Test Validates

### Core Workflows
1. **Authentication** - Provider login with username, password, and PIN
2. **Dashboard Access** - Provider appointment schedule and navigation menu
3. **Patient Search** - Finding patients by partial name search
4. **Demographic Records** - Viewing complete patient information

### Key Features Tested
- Session management and authentication
- Navigation menu functionality
- Search query processing
- Patient data display and formatting
- Health insurance information
- Clinic status tracking
- Contact information management

---

## Test Steps

### 1. Login (Step 1)
- Navigate to http://localhost:8080/oscar/index.jsp
- Enter credentials (openodoc / openo2025 / 2025)
- Submit login form
- **Expected**: Redirect to provider dashboard

### 2. Dashboard Verification (Step 2)
- Verify page title: "Openodoc, D-Appointment Access"
- Check navigation menu displays all items
- Confirm provider name shown: "doctor openodoc"
- Verify appointment schedule visible
- **Expected**: Full dashboard with all navigation options

### 3. Navigate to Search (Step 3)
- Click "Search" in navigation menu
- **Expected**: Patient search page loads in new tab/window

### 4. Search for First Patient (Steps 4-5)
- Enter search term: `FAKE-G`
- **IMPORTANT**: Press Enter to submit the search (the form does not auto-submit)
- **Expected**: Table of results showing patients with "FAKE-G" in their name
- Click on patient ID 182 (FAKE-Gaylord, FAKE-Branda)
- **Expected**: Complete demographic record with all patient information

### 5. Search for Second Patient (Steps 6-7)
- Use search box on demographic page
- Enter search term: `FAKE-J`
- **IMPORTANT**: Press Enter to submit the search (the form does not auto-submit)
- **Expected**: Table of results showing patients with "FAKE-J" in their name
- Click on patient ID 1 (FAKE-Jacky, FAKE-Jones)
- **Expected**: Complete demographic record with all patient information

---

## Prerequisites

### Application Running
```bash
# Start OpenO EMR
server start

# Verify application is running
server log
```

### Database Contains Test Data
- Provider: openodoc (password: openo2025, PIN: 2025)
- Patient ID 182: FAKE-Gaylord, FAKE-Branda
- Patient ID 1: FAKE-Jacky, FAKE-Jones
- Additional test patients with "FAKE-" prefix (IDs 1-49)

### Playwright MCP Available
- Configured in `.mcp.json` with headless Chromium
- Environment variable: `PW_TEST_SCREENSHOT_NO_FONTS_READY=1`

---

## Running the Test

### Manual Execution (Current Method)

1. **Start Application**:
   ```bash
   server start
   server log  # Verify no errors
   ```

2. **Open Claude Code** with Playwright MCP enabled

3. **Follow Test Steps** (see section above)

4. **Capture Screenshots** at each step:
   - test-1-01-login-page.png
   - test-1-02-provider-dashboard.png
   - test-1-03-patient-search.png
   - test-1-04-search-results-patient1.png
   - test-1-05-demographic-patient1.png
   - test-1-06-search-results-patient2.png
   - test-1-07-demographic-patient2.png

5. **Review Results**:
   ```bash
   cat docs/ui-tests/test-1/test-1-results.md
   ```

### Future: Automated Execution

Once JUnit 5 test classes are created:

```bash
# Run Test 1 only
mvn test -Dtest=QuickTest1SmokeTest

# Run all quick tests
mvn test -Dgroups="quick-test"
```

---

## Expected Results

### All Steps Pass ✅
- Login successful
- Dashboard loads with all navigation
- Search finds patients by partial name
- Both demographic records display completely
- No functional errors or exceptions

### Known Warnings ⚠️
- **404 errors**: Missing static resources (CSS/JS/images) on multiple pages
- **JavaScript warnings**: Template variable parsing issues
- **Impact**: None - core functionality unaffected

---

## Test Data Details

### Provider Credentials
```
Username: openodoc
Password: openo2025
PIN: 2025
Provider No: 999998
Name: doctor openodoc
```

### Test Patients

**Patient 1** (ID: 182):
- Name: FAKE-Gaylord, FAKE-Branda Kristal
- Sex: Female
- DOB: 2009-03-05 (16 years old)
- HIN: 2088617755 GC (ON)
- Status: AC (Active), Rostered
- MRP: openodoc, doctor

**Patient 2** (ID: 1):
- Name: FAKE-Jacky, FAKE-Jones (MR)
- Sex: Male
- DOB: 1985-06-15 (40 years old)
- HIN: 9876543225 AB (ON)
- Status: AC (Active)
- MRP: openodoc, doctor

---

## Screenshot Reference

| Screenshot | Description | Key Elements |
|------------|-------------|--------------|
| test-1-01 | Login page | Username, password, PIN fields |
| test-1-02 | Provider dashboard | Navigation menu, appointment schedule |
| test-1-03 | Patient search | Empty search form, filter buttons |
| test-1-04 | Search results (FAKE-G) | Table with 10 matching patients |
| test-1-05 | Demographic #182 | Complete patient record for FAKE-Branda |
| test-1-06 | Search results (FAKE-J) | Table with 10 matching patients |
| test-1-07 | Demographic #1 | Complete patient record for FAKE-Jones |

All screenshots saved to: `docs/ui-tests/test-1/screenshots/`

---

## Troubleshooting

### Login Fails
```bash
# Check user exists and reset password if needed
mysql -h db -uroot -ppassword oscar -e \
  "SELECT user_name, pin, forcePasswordReset FROM security WHERE user_name='openodoc';"

# Reset password
mysql -h db -uroot -ppassword oscar -e \
  "UPDATE security SET
   password='{bcrypt}\$2b\$12\$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96',
   forcePasswordReset=0
   WHERE user_name='openodoc';"
```

### Search Returns No Results
- **Expected Behavior**: Search requires entering search terms (partial or full name)
- **Does Not**: Display all patients by default
- **Solution**: Enter at least the first few characters of patient name
- **Common Issue**: Remember to **press Enter** after typing the search term - the form does not auto-submit

### Patient Records Missing
```bash
# Verify test data exists
mysql -h db -uroot -ppassword oscar -e \
  "SELECT demographic_no, first_name, last_name, patient_status
   FROM demographic
   WHERE demographic_no IN (1, 182);"
```

### Application Won't Start
```bash
# Check Tomcat status
server log

# Restart if needed
server restart

# Verify port 8080 available
netstat -tulpn | grep 8080
```

---

## Test Metrics

- **Total Steps**: 7
- **Pass Rate**: 100% (7/7)
- **Screenshots**: 7
- **Patients Tested**: 2
- **Pages Tested**: 4 unique URLs
- **Warnings**: 2 types (404s, JS template issues)
- **Blockers**: 0

---

## Next Steps

### Phase 2: Expand Test Coverage
- Add appointment booking workflow
- Include prescription entry
- Test clinical note creation
- Validate lab results display

### Phase 3: Automation
- Create JUnit 5 test class: `QuickTest1SmokeTest.java`
- Implement LoginHelper for authentication
- Add screenshot capture on failure
- Integrate with CI/CD pipeline

### Phase 4: Monitoring
- Add performance metrics (page load times)
- Track warning/error trends over time
- Alert on test failures
- Generate HTML test reports

---

## Related Documentation

- [Full Test Results](test-1-results.md) - Detailed findings and screenshots
- [Smoke Test Suite README](../README.md) - Complete testing guide
- [Implementation Summary](../SUMMARY.md) - Technical implementation details
- [Modern Test Framework](../../test/modern-test-framework-complete.md) - JUnit 5 patterns

---

*Test 1 README*
*Last Updated: 2026-01-16*
