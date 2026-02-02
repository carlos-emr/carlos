# Comprehensive UI Test Guide for OpenO EMR

This guide provides a complete overview of the UI test suite, covering all 9 tests and their interdependencies.

## Test Suite Summary

| Test | Name | Steps | Screenshots | Priority | Duration |
|------|------|-------|-------------|----------|----------|
| 1 | Smoke Test | 7 | 7 | Critical | ~3 min |
| 2 | Comprehensive Demographics | 30 | 30 | High | ~25 min |
| 3 | Appointments & Scheduling | 15 | 15 | High | ~12 min |
| 4 | Prescriptions (Rx) | 18 | 18 | High | ~15 min |
| 5 | Ticklers & Messaging | 12 | 12 | Medium | ~8 min |
| 6 | Encounter & E-Chart | 22 | 22 | High | ~20 min |
| 7 | Billing (Ontario MOH) | 15 | 15 | Medium | ~12 min |
| 8 | Lab Results | 12 | 12 | Medium | ~10 min |
| 9 | Prevention & Immunization | 12 | 12 | Medium | ~10 min |

**Totals**: 143 steps, 143 screenshots, ~115 minutes

---

## Quick Reference: Slash Commands

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

---

## Test Dependencies

```
Test 1 (Smoke)
    └── Validates basic login and navigation

Test 2 (Demographics)
    └── Creates TEST-UITEST2 patient (used by other tests optionally)
    └── Tests patient creation and editing

Test 3 (Appointments)
    └── Uses existing test patient (FAKE-Jones)
    └── Creates and deletes test appointment

Test 4 (Prescriptions)
    └── Depends on: Test patient exists
    └── Creates: Allergy (Penicillin), Prescription (Acetaminophen)

Test 5 (Ticklers)
    └── Depends on: Test patient exists
    └── Creates: Tickler linked to patient

Test 6 (Encounters)
    └── Depends on: Test patient exists
    └── Creates: Encounter with vitals, diagnosis, notes

Test 7 (Billing)
    └── Depends on: Ontario billing configured, patient with HIN
    └── Creates: Billing entry

Test 8 (Lab Results)
    └── Depends on: Lab inbox accessible
    └── May have empty data in test environment

Test 9 (Prevention)
    └── Depends on: Prevention module configured
    └── Creates: Immunization record
```

---

## Test Data Requirements

### Provider Credentials

All tests use the same provider credentials:

| Field | Value |
|-------|-------|
| Username | `openodoc` |
| Password | `openo2025` |
| PIN | `2025` |
| Provider No | `999998` |
| Name | doctor openodoc |

### Required Test Patients

| ID | Name | HIN | Required By |
|----|------|-----|-------------|
| 1 | FAKE-Jacky, FAKE-Jones | 9876543225 | Tests 1, 3-9 |
| 182 | FAKE-Gaylord, FAKE-Branda | 2088617755 | Tests 1, 2 |

### Test-Created Data

| Test | Data Created | Cleanup |
|------|--------------|---------|
| Test 2 | Patient: TEST-UITEST2 | Marked Inactive |
| Test 3 | Appointment | Deleted |
| Test 4 | Allergy, Prescription | Persists |
| Test 5 | Tickler | Persists |
| Test 6 | Encounter | Persists |
| Test 7 | Billing Entry | Persists |
| Test 9 | Immunization | Persists |

---

## Pre-Test Checklist

Before running any test:

### 1. Application Status
```bash
curl -sI http://localhost:8080/oscar/index.jsp | head -1
# Expected: HTTP/1.1 200
```

### 2. Database Connection
```bash
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"
# Expected: Returns 1
```

### 3. Test User Exists
```bash
mysql -h db -uroot -ppassword oscar -e \
  "SELECT user_name, pin FROM security WHERE user_name='openodoc';"
# Expected: openodoc | 2025
```

### 4. Test Patients Exist
```bash
mysql -h db -uroot -ppassword oscar -e \
  "SELECT demographic_no, last_name, first_name FROM demographic WHERE demographic_no IN (1, 182);"
# Expected: 2 rows
```

### 5. Reset Test Password (if needed)
```bash
mysql -h db -uroot -ppassword oscar -e \
  "UPDATE security SET password='{bcrypt}\$2b\$12\$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96', forcePasswordReset=0 WHERE user_name='openodoc';"
```

---

## Recommended Execution Order

For first-time execution or full regression testing:

1. **Test 1** - Validates basic functionality works
2. **Test 2** - Tests demographics (creates cleanup patient)
3. **Test 3** - Tests appointments
4. **Test 4** - Tests prescriptions (adds allergy and Rx)
5. **Test 6** - Tests encounters (core clinical workflow)
6. **Test 5** - Tests ticklers and messaging
7. **Test 7** - Tests billing (Ontario-specific)
8. **Test 8** - Tests lab results
9. **Test 9** - Tests prevention/immunizations

---

## Expected Warnings

These warnings are expected and do not indicate failures:

### 404 Errors (Non-blocking)
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

### JavaScript Warnings
- `Unexpected token '%'` - JSP template parsing
- `TypeError: Cannot read properties of undefined (reading 'username')` - Login page

### When to Investigate

**Investigate if you see:**
- NEW 404 errors for previously working resources
- JavaScript errors that break functionality
- Form submission failures
- Missing UI elements

---

## Gold Standard Management

### Directory Structure

```
docs/ui-tests/
├── test-1/
│   ├── screenshots/          # Gold standard images
│   │   └── test-1-*.png
│   ├── test-1-README.md
│   └── test-1-EXECUTION.md
├── test-2/
│   └── ...
├── ...
└── test-9/
    └── ...
```

### Test Run Output

```
ui-test-runs/
└── YYYYMMDD-HHMMSS-mmm/      # Session timestamp
    ├── test-1/
    │   ├── screenshots/      # This run's screenshots
    │   ├── reports/          # Test results
    │   └── comparison/       # Visual diffs (future)
    ├── test-2/
    │   └── ...
    └── summary/
        └── full-suite-results.md
```

### Updating Gold Standards

**IMPORTANT**: Gold standards should only be updated when:
- Intentional UI changes have been made
- Test data has been deliberately changed
- Changes have been reviewed and approved

**Never update gold standards** as part of normal test execution.

---

## Troubleshooting

### Login Fails

```bash
# Reset password
mysql -h db -uroot -ppassword oscar -e \
  "UPDATE security SET password='{bcrypt}\$2b\$12\$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96', forcePasswordReset=0 WHERE user_name='openodoc';"
```

### Application Not Responding

```bash
# Restart application
server restart
server log  # Watch for startup completion
```

### Search Returns No Results

- **Remember**: Press Enter after typing search terms
- Forms do not auto-submit
- Check patient exists in database

### Test Data Missing

```bash
# Verify patient exists
mysql -h db -uroot -ppassword oscar -e \
  "SELECT * FROM demographic WHERE demographic_no = 1;"
```

### Browser Issues

- Close browser between tests for clean state
- Check Playwright MCP is configured
- Verify `.mcp.json` exists

---

## Module-Specific Notes

### Ontario Billing (Test 7)

- Requires Ontario billing configuration
- Patient must have valid HIN
- Service codes must be loaded in database

### Lab Results (Test 8)

- Lab inbox may be empty in test environment
- Test focuses on navigation, not data
- Pass criteria includes empty state handling

### E-Chart/Encounters (Test 6)

- E-Chart may open in new window/tab
- Vital signs form location varies by configuration
- Diagnosis search uses ICD codes

---

## Version History

| Date | Changes |
|------|---------|
| 2026-01-19 | Initial comprehensive test suite (9 tests) |

---

## Related Documentation

- [UI Test Process](UI-TEST-PROCESS.md) - Standard testing procedures
- [Test 1 README](test-1/test-1-README.md) - Smoke test details
- [Commands README](/.claude/commands/ui-tests/README.md) - Slash command reference
