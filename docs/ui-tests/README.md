# OpenO EMR UI Test Suite

Comprehensive UI testing for OpenO EMR using Playwright MCP (Model Context Protocol) server for browser automation.

## 📚 Quick Start

- **New to UI testing?** Start with [UI-TEST-PROCESS.md](UI-TEST-PROCESS.md) - Complete testing procedures
- **Running Test 1?** See [test-1/test-1-EXECUTION.md](test-1/test-1-EXECUTION.md) - Step-by-step execution guide
- **Test results?** Check [test-1/test-1-results.md](test-1/test-1-results.md) - Latest test results with screenshots
- **Implementation details?** Read [SUMMARY.md](SUMMARY.md) - Technical implementation and troubleshooting

## Overview

This UI test suite validates critical user workflows in OpenO EMR to ensure core functionality works as expected after deployments or significant code changes.

### Test Types Supported

This framework supports multiple types of browser-based UI tests:

- **Smoke Tests**: Fast validation of critical paths (e.g., Test 1 - login and patient demographics)
- **Comprehensive Tests**: Thorough end-to-end testing of complete workflows
- **Regression Tests**: Visual comparison against gold standard screenshots
- **Integration Tests**: Multi-step user journeys across different modules

**Test 1** is classified as a smoke test because it validates core authentication and patient access in under 3 minutes.

### Directory Structure

Each test has its own directory containing gold standards and documentation:

**Gold Standard Test Assets** (committed to Git):
```
docs/ui-tests/
├── test-1/                           # Test 1 - Smoke Test
│   ├── screenshots/                  # Gold standard reference images
│   │   └── test-1-*.png             # 7 reference screenshots
│   ├── test-1-README.md             # Test overview and description
│   ├── test-1-EXECUTION.md          # Step-by-step execution guide
│   └── test-1-results.md            # Latest validated results
├── test-2/                           # Future test
└── ...
```

**Test Run Output** (not committed, in `.gitignore`):
```
ui-test-runs/
├── 20260116-113900-123/              # Test run timestamp (includes milliseconds)
│   ├── test-1/                       # Test 1 execution outputs
│   │   ├── screenshots/              # Screenshots from this run
│   │   │   └── test-1-*.png
│   │   ├── reports/                  # Test execution reports
│   │   │   └── test-1-results-*.md
│   │   └── comparison/               # Visual diff results
│   ├── test-2/                       # Test 2 (if run in same session)
│   │   └── ...
│   └── overview.md                   # Optional summary of all tests
├── 20260117-091500-456/              # Another test run
│   └── ...
└── README.md                         # Documentation
```

## Test Coverage

### Complete Test Matrix

| Test | Name | Steps | Screenshots | Duration | Status |
|------|------|-------|-------------|----------|--------|
| 1 | Smoke Test (Login, Search, Demographics) | 7 | 7 | ~3 min | ✅ Implemented |
| 2 | Comprehensive Demographics | 30 | 30 | ~25 min | ✅ Implemented |
| 3 | Appointments & Scheduling | 15 | 15 | ~12 min | ✅ Implemented |
| 4 | Prescriptions (Rx) | 18 | 18 | ~15 min | ✅ Implemented |
| 5 | Ticklers & Messaging | 12 | 12 | ~8 min | ✅ Implemented |
| 6 | Encounter & E-Chart | 22 | 22 | ~20 min | ✅ Implemented |
| 7 | Billing (Ontario MOH) | 15 | 15 | ~12 min | ✅ Implemented |
| 8 | Lab Results | 12 | 12 | ~10 min | ✅ Implemented |
| 9 | Prevention & Immunization | 12 | 12 | ~10 min | ✅ Implemented |

**Totals**: 143 steps, 143 screenshots, ~115 minutes for full suite

### Slash Commands

Run tests using Claude Code slash commands:

| Command | Test | Description |
|---------|------|-------------|
| `/test1` | Smoke Test | Login, search, patient demographics |
| `/test2` | Demographics | Create, edit, search, filters |
| `/test3` | Appointments | Schedule, create, edit, views |
| `/test4` | Prescriptions | Allergies, drugs, Rx creation |
| `/test5` | Ticklers | Create, status, messaging |
| `/test6` | Encounters | E-Chart, vitals, notes, panels |
| `/test7` | Billing | Ontario MOH billing workflow |
| `/test8` | Lab Results | Inbox, detail, history |
| `/test9` | Prevention | Immunizations, reports |
| `/test-fullsuite` | All Tests | Run all 9 tests sequentially |

### Test Details

#### Test 1 - Smoke Test (Login and Patient Demographics)
**Type**: Smoke Test | **Steps**: 7 | **Duration**: ~3 minutes
**Coverage**: Authentication, Dashboard, Patient Search, Demographic Records
**Documentation**: [test-1/](test-1/)

#### Test 2 - Comprehensive Demographics
**Type**: Comprehensive Test | **Steps**: 30 | **Duration**: ~25 minutes
**Coverage**: Patient creation, editing, search by name/HIN, filters, healthcare team
**Documentation**: [test-2/](test-2/)

#### Test 3 - Appointments & Scheduling
**Type**: Comprehensive Test | **Steps**: 15 | **Duration**: ~12 minutes
**Coverage**: Schedule views, appointment creation, editing, status, history
**Documentation**: [test-3/](test-3/)

#### Test 4 - Prescriptions (Rx)
**Type**: Comprehensive Test | **Steps**: 18 | **Duration**: ~15 minutes
**Coverage**: Allergy management, drug search, prescription creation, interaction check
**Documentation**: [test-4/](test-4/)

#### Test 5 - Ticklers & Messaging
**Type**: Comprehensive Test | **Steps**: 12 | **Duration**: ~8 minutes
**Coverage**: Tickler creation, status management, messaging module
**Documentation**: [test-5/](test-5/)

#### Test 6 - Encounter & E-Chart
**Type**: Comprehensive Test | **Steps**: 22 | **Duration**: ~20 minutes
**Coverage**: E-Chart, encounter creation, vitals, diagnosis, clinical notes, panels
**Documentation**: [test-6/](test-6/)

#### Test 7 - Billing (Ontario MOH)
**Type**: Comprehensive Test | **Steps**: 15 | **Duration**: ~12 minutes
**Coverage**: Service codes, diagnostic codes, billing submission, history
**Documentation**: [test-7/](test-7/)

#### Test 8 - Lab Results
**Type**: Comprehensive Test | **Steps**: 12 | **Duration**: ~10 minutes
**Coverage**: Lab inbox, result detail, forwarding, history, review status
**Documentation**: [test-8/](test-8/)

#### Test 9 - Prevention & Immunization
**Type**: Comprehensive Test | **Steps**: 12 | **Duration**: ~10 minutes
**Coverage**: Prevention records, immunization entry, history, reports
**Documentation**: [test-9/](test-9/)

### Additional Documentation

- **[COMPREHENSIVE-TEST-GUIDE.md](COMPREHENSIVE-TEST-GUIDE.md)** - Master guide for all tests
- **[UI-TEST-PROCESS.md](UI-TEST-PROCESS.md)** - Testing procedures and gold standards

## Running Smoke Tests

### Prerequisites

1. **Application Running**:
   ```bash
   make install
   server start
   ```

2. **Database Seeded**:
   - Development database must contain test data
   - Test user `openodoc` with password `openo2025` and PIN `2025`
   - Test patients with "FAKE-" prefix

3. **Playwright MCP Available**:
   - Playwright MCP server must be configured
   - Browser automation tools accessible

### Manual Testing with Playwright MCP

The UI tests are currently run using the Playwright MCP server through Claude Code. To run manually:

1. **Start the application**:
   ```bash
   server start
   server log  # Verify no errors
   ```

2. **Open Claude Code with Playwright MCP enabled**

3. **Run UI test commands** (example):
   ```
   Navigate to http://localhost:8080/oscar/index.jsp
   Fill username: openodoc
   Fill password: openo2025
   Fill PIN: 2025
   Press Enter
   Verify dashboard loaded
   ```

4. **Review results**:
   ```bash
   cat docs/ui-tests/smoke-test-results.md
   ```

### Automated Testing (Future)

The Maven dependency for Playwright has been added to `pom.xml`:

```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.40.0</version>
    <scope>test</scope>
</dependency>
```

**Planned**: JUnit 5 test classes in `src/test-modern/java/ca/openosp/openo/ui/smoke/`

## Test Credentials

### Provider Account
- **Username**: `openodoc`
- **Password**: `openo2025`
- **PIN**: `2025`
- **Provider No**: `999998`
- **Name**: doctor openodoc

### Test Patients

| ID | Name | Status | HIN | DOB |
|---|---|---|---|---|
| 1 | FAKE-Jacky FAKE-Jones | AC | 9876543225 | 1985-06-15 |
| 2 | FAKE-JOHN FAKE-SMITH | AC | - | - |
| 3 | FAKE-GEORGE FAKE-FOREMAN | AC | 1111111165 | 1985-04-05 |

**Note**: All test patients have "FAKE-" prefix for easy identification

## Test Results

### Latest Results

See [smoke-test-results.md](./smoke-test-results.md) for detailed test results from the most recent run.

**Last Run**: 2026-01-16
**Status**: ✅ 4/5 tests passing
**Known Issues**: 1 (Patient search returns no results)

### Known Issues

#### Issue #1: Patient Search Returns Empty Results ⚠️

**Severity**: Medium
**Affected**: `/demographic/search.jsp` search functionality
**Workaround**: Direct navigation to demographic records using `demographic_no` parameter

**Details**:
- Search form loads and submits correctly
- Database contains patient data
- Direct access to patient records works
- Search query returns 0 results (may be query/filtering issue)

**Investigation Needed**:
- Review `demographiccontrol.jsp` search query logic
- Check JavaScript table population
- Verify security filtering
- Test with different search criteria

## Test Environment

### Development Environment
- **Application Server**: Apache Tomcat 9.0.97
- **Java Version**: 21
- **Database**: MariaDB (Docker container)
- **Application URL**: http://localhost:8080/oscar
- **Framework**: Struts 2.x + Spring 5.3.39

### Database Connection
- **Host**: db (Docker service name)
- **Database**: oscar
- **Username**: root
- **Password**: password
- **Port**: 3306

## Test Data Management

### Resetting Test Data

```bash
# Connect to database
mysql -h db -uroot -ppassword oscar

# Reset specific test user password
UPDATE security
SET password='{bcrypt}$2b$12$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96',
    forcePasswordReset=0
WHERE user_name='openodoc';

# Exit
exit
```

### Adding Test Data

Test data is defined in:
```
.devcontainer/db/scripts/development.sql
```

To reload:
```bash
# Stop application
server stop

# Reload database
mysql -h db -uroot -ppassword oscar < .devcontainer/db/scripts/development.sql

# Start application
server start
```

## Critical Workflows Tested

### 1. Authentication Flow
- ✅ Login page loads
- ✅ Valid credentials accepted
- ✅ Session established
- ✅ Redirect to provider dashboard
- ✅ User info displayed in header

### 2. Provider Dashboard
- ✅ Navigation menu rendered
- ✅ Appointment schedule displayed
- ✅ All menu items accessible
- ✅ Provider selector working

### 3. Patient Search
- ✅ Search page loads
- ✅ Search form rendered
- ⚠️ Search results not displaying (known issue)
- ✅ Direct patient access works

### 4. Demographic Record
- ✅ Patient details displayed
- ✅ Contact information visible
- ✅ Health insurance info shown
- ✅ Action buttons functional
- ✅ Navigation links working

## URL Patterns

| Function | URL | Status |
|---|---|---|
| Login | `/index.jsp` | ✅ |
| Dashboard | `/provider/providercontrol.jsp` | ✅ |
| Patient Search | `/demographic/search.jsp` | ✅ |
| Search Results | `/demographic/demographiccontrol.jsp?search_mode=search_name&keyword=...` | ⚠️ |
| Patient Record | `/demographic/demographiccontrol.jsp?demographic_no=3&displaymode=edit` | ✅ |
| Appointments | `/appointment/appointmentcontrol.jsp` | Not tested |
| E-Chart | `/oscarEncounter/Index.jsp` | Not tested |
| Prescriptions | `/oscarRx/choosePatient.do` | Not tested |

## Troubleshooting

### Application Won't Start

```bash
# Check Tomcat logs
server log

# Check if port is in use
netstat -tulpn | grep 8080

# Restart container
server restart
```

### Database Connection Issues

```bash
# Verify database container is running
docker ps | grep mariadb

# Test connection
mysql -h db -uroot -ppassword -e "SHOW DATABASES;"
```

### Login Fails

```bash
# Check if user exists
mysql -h db -uroot -ppassword oscar -e \
  "SELECT user_name, pin, forcePasswordReset FROM security WHERE user_name='openodoc';"

# Reset password
mysql -h db -uroot -ppassword oscar -e \
  "UPDATE security SET password='{bcrypt}\$2b\$12\$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96', forcePasswordReset=0 WHERE user_name='openodoc';"
```

### JavaScript Errors in Console

Some non-critical JavaScript errors are expected in development:
- Template variable rendering: `Unexpected token '%'`
- Function undefined errors: Usually cosmetic
- 404 for resources: Check if resource file exists

**Action**: Only investigate if functionality is blocked

## Future Enhancements

### Phase 2: Expanded Coverage
- Encounter window testing
- Prescription workflow
- Lab result upload/display
- Appointment booking
- Billing workflows
- Document management
- E-form creation

### Phase 3: Automation
- JUnit 5 test classes
- Automated screenshot capture
- Visual regression testing
- CI/CD integration
- GitHub Actions workflows
- Performance metrics

### Phase 4: Advanced Testing
- Cross-browser testing (Chrome, Firefox, Safari)
- Mobile viewport testing
- Accessibility testing (WCAG compliance)
- Load testing
- API endpoint UI tests
- Security scanning

## Contributing

When adding new UI tests:

1. **Update Test Coverage** in this README
2. **Document Test Steps** with clear descriptions
3. **Capture Screenshots** for visual validation
4. **Record Issues** with severity and workarounds
5. **Update Results** in smoke-test-results.md

## Test Execution Checklist

- [ ] Application is running (`server start`)
- [ ] Database is seeded with test data
- [ ] Test credentials are valid
- [ ] Playwright MCP is available
- [ ] Previous test results archived
- [ ] Issues from last run reviewed

## Support

For issues with UI tests:
1. Check [smoke-test-results.md](./smoke-test-results.md) for known issues
2. Review application logs: `server log`
3. Check database connectivity
4. Verify test data exists
5. Report new issues with:
   - Steps to reproduce
   - Expected vs actual results
   - Console errors
   - Screenshots if applicable

---

## Quick Reference

### Start Testing
```bash
# 1. Start app
make install && server start

# 2. Verify app is ready
curl http://localhost:8080/oscar/index.jsp

# 3. Check test user
mysql -h db -uroot -ppassword oscar -e \
  "SELECT user_name, pin FROM security WHERE user_name='openodoc';"

# 4. Run UI tests (via Playwright MCP)
```

### After Testing
```bash
# Review results
cat docs/ui-tests/smoke-test-results.md

# Check for new issues
grep "⚠️\|❌" docs/ui-tests/smoke-test-results.md
```

---

*Generated for OpenO EMR UI Test Suite*
*Last Updated: 2026-01-16*
