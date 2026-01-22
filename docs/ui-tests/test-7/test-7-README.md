# Test 7: Billing (Ontario MOH)

## Overview

Test 7 validates the Ontario MOH billing workflow in OpenO EMR, including creating billing entries, selecting service codes, adding diagnostic codes, and viewing billing history.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~12-15 minutes
**Steps**: 15
**Screenshots**: 15
**Province**: Ontario (ON) specific

## What This Test Validates

1. **Billing Module Access** - Navigate to patient billing
2. **Billing Entry Creation** - Create new MOH billing entry
3. **Service Codes** - Select OHIP service codes
4. **Diagnostic Codes** - Add ICD diagnostic codes
5. **Service Date** - Set billing service date
6. **Billing Submission** - Submit billing entry
7. **Billing History** - View patient billing history
8. **Billing Reports** - Generate billing reports
9. **Third Party Billing** - View 3rd party options

## Prerequisites

### Application Requirements
- OpenO EMR running at http://localhost:8080/oscar
- Ontario (ON) billing configuration enabled
- OHIP service codes loaded
- Database accessible and writable
- Playwright MCP configured (headless Chromium)

### Test Data Requirements

#### Provider Credentials
| Field | Value |
|-------|-------|
| Username | openodoc |
| Password | openo2025 |
| PIN | 2025 |
| Provider No | 999998 |

#### Existing Test Patients (Must Exist)
| ID | Name | HIN | Status |
|----|------|-----|--------|
| 1 | FAKE-Jacky, FAKE-Jones | 9876543225 | Active |

## Test Workflow (15 Steps)

### Phase 1: Authentication & Billing Access (Steps 1-4)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | Search for test patient | Patient found |
| 4 | Open patient billing | Billing module opens |

### Phase 2: Create Billing Entry (Steps 5-9)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 5 | Click "Create Billing" | Billing form opens |
| 6 | Select service code | OHIP code selected |
| 7 | Add diagnostic code | ICD code added |
| 8 | Set service date | Date set to today |
| 9 | Submit billing | Billing submitted |

### Phase 3: Billing Management (Steps 10-13)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 10 | View billing confirmation | Confirmation displays |
| 11 | View billing history | History list shows |
| 12 | View billing details | Entry details shown |
| 13 | Generate billing report | Report preview |

### Phase 4: Additional Options (Steps 14-15)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 14 | View 3rd party billing | Third party options shown |
| 15 | Print billing statement | Statement preview |

## Test Data Values

### Billing Entry (Created During Test)
```
Service Code: A003 (or similar OHIP code)
Diagnostic Code: J06.9 (Upper respiratory infection)
Service Date: Today's date
Provider: openodoc
Location: Clinic (default)
```

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-7-01-login-page.png | Auth | Login form |
| test-7-02-provider-dashboard.png | Auth | Dashboard |
| test-7-03-patient-search.png | Access | Search results |
| test-7-04-billing-module.png | Access | Billing module opened |
| test-7-05-create-billing.png | Create | Empty billing form |
| test-7-06-service-code.png | Create | Service code selected |
| test-7-07-diagnostic-code.png | Create | ICD code added |
| test-7-08-service-date.png | Create | Date set |
| test-7-09-billing-submitted.png | Create | Submission complete |
| test-7-10-billing-confirmation.png | Manage | Confirmation |
| test-7-11-billing-history.png | Manage | History list |
| test-7-12-billing-details.png | Manage | Entry details |
| test-7-13-billing-report.png | Manage | Report preview |
| test-7-14-third-party.png | Options | 3rd party options |
| test-7-15-billing-statement.png | Options | Statement preview |

## Expected Warnings (Non-Blocking)

### 404 Errors
- `/oscar/js/dateFormatUtils.js` - Various pages (expected)
- `/oscar/js/custom/default/master.js` - Various pages (expected)

### JavaScript Warnings
- `Unexpected token '%'` - JSP template parsing (expected)

## Success Criteria

### Test Passes When:
- [ ] All 15 steps complete without blocking errors
- [ ] All 15 screenshots captured
- [ ] Billing entry created successfully
- [ ] Service code selected
- [ ] Diagnostic code added
- [ ] Billing history accessible

### Test Fails When:
- Login fails
- Billing module doesn't load
- Service codes missing
- Billing submission fails
- New 404 errors appear

## Troubleshooting

### Billing Module Not Available
1. Verify Ontario billing is configured
2. Check provider has billing privileges
3. Verify OHIP service codes loaded

### Service Codes Empty
1. Check service code database
2. Verify Ontario billing configuration
3. Try manual code entry

### Billing Won't Submit
1. Check required fields
2. Verify patient HIN valid
3. Check service date is valid
4. Verify provider billing number

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [Test 6 - Encounters](../test-6/test-6-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-7-EXECUTION.md)
