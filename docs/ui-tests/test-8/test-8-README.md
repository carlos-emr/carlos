# Test 8: Lab Results

## Overview

Test 8 validates the lab results viewing workflow in OpenO EMR, including accessing lab inbox, viewing lab result details, reviewing patient context, and managing lab result status.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~10-12 minutes
**Steps**: 12
**Screenshots**: 12

## What This Test Validates

1. **Lab Inbox Access** - Navigate to lab results inbox
2. **Lab Results List** - View pending/new lab results
3. **Lab Result Detail** - View individual lab result
4. **Patient Context** - View patient information from lab
5. **Lab Forwarding** - Forward lab result to provider
6. **Lab History** - View patient's lab history
7. **Lab Review** - Mark lab as reviewed
8. **Print Preview** - Print lab result

## Prerequisites

### Application Requirements
- OpenO EMR running at http://localhost:8080/oscar
- Database accessible
- Lab results in database (if available)
- Playwright MCP configured (headless Chromium)

### Test Data Requirements

#### Provider Credentials
| Field | Value |
|-------|-------|
| Username | openodoc |
| Password | openo2025 |
| PIN | 2025 |
| Provider No | 999998 |

#### Test Patients
| ID | Name | Status |
|----|------|--------|
| 1 | FAKE-Jacky, FAKE-Jones | Active |

**Note**: Lab results may be empty for test patients. Test focuses on navigation and module functionality.

## Test Workflow (12 Steps)

### Phase 1: Authentication & Inbox Access (Steps 1-4)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | Click Inbox menu | Inbox opens |
| 4 | View lab results section | Lab inbox visible |

### Phase 2: Lab Result Viewing (Steps 5-7)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 5 | Open a lab result (if available) | Lab detail view |
| 6 | View patient context | Patient info shown |
| 7 | View lab values | Lab data displayed |

### Phase 3: Lab Management (Steps 8-10)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 8 | Forward lab result | Forward dialog |
| 9 | View lab history | History list |
| 10 | Print lab result | Print preview |

### Phase 4: Lab Status (Steps 11-12)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 11 | Mark lab reviewed | Status updated |
| 12 | Verify status change | Review confirmed |

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-8-01-login-page.png | Auth | Login form |
| test-8-02-provider-dashboard.png | Auth | Dashboard |
| test-8-03-inbox-menu.png | Access | Inbox clicked |
| test-8-04-lab-inbox.png | Access | Lab results section |
| test-8-05-lab-detail.png | View | Lab result detail |
| test-8-06-patient-context.png | View | Patient info |
| test-8-07-lab-values.png | View | Lab values |
| test-8-08-forward-lab.png | Manage | Forward dialog |
| test-8-09-lab-history.png | Manage | History list |
| test-8-10-print-preview.png | Manage | Print preview |
| test-8-11-mark-reviewed.png | Status | Review action |
| test-8-12-status-confirmed.png | Status | Review confirmed |

## Expected Warnings (Non-Blocking)

### 404 Errors
- `/oscar/js/dateFormatUtils.js` - Various pages (expected)
- `/oscar/js/custom/default/master.js` - Various pages (expected)

### JavaScript Warnings
- `Unexpected token '%'` - JSP template parsing (expected)

## Success Criteria

### Test Passes When:
- [ ] All 12 steps complete without blocking errors
- [ ] All 12 screenshots captured
- [ ] Lab inbox accessible
- [ ] Lab module navigation works
- [ ] Print preview available

### Test Fails When:
- Login fails
- Inbox doesn't load
- Lab module not accessible
- New 404 errors appear

## Troubleshooting

### Lab Inbox Empty
1. This may be expected for test data
2. Verify navigation still works
3. Check if lab data exists in database

### Lab Detail Won't Open
1. Verify lab result exists
2. Check JavaScript console
3. Try different lab result

### Mark Reviewed Not Working
1. Verify lab is unreviewed
2. Check user permissions
3. Check JavaScript console

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [Test 6 - Encounters](../test-6/test-6-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-8-EXECUTION.md)
