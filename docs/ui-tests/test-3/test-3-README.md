# Test 3: Appointments & Scheduling

## Overview

Test 3 validates the appointment scheduling workflow in OpenO EMR, including viewing schedules, creating appointments, modifying appointments, and navigating schedule views.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~10-15 minutes
**Steps**: 15
**Screenshots**: 15

## What This Test Validates

1. **Schedule View** - Provider appointment schedule display
2. **Schedule Navigation** - Day/week/month view switching
3. **Appointment Creation** - Create new appointment with patient selection
4. **Appointment Types** - Selecting appointment types and reasons
5. **Appointment Editing** - Modifying appointment status
6. **Appointment History** - Viewing patient's appointment history
7. **Schedule Templates** - Applying schedule templates (if available)
8. **Appointment Cancellation** - Cancel/delete appointment workflow

## Prerequisites

### Application Requirements
- OpenO EMR running at http://localhost:8080/oscar
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
| ID | Name | Status |
|----|------|--------|
| 1 | FAKE-Jacky, FAKE-Jones | Active |
| 182 | FAKE-Gaylord, FAKE-Branda | Active |

## Test Workflow (15 Steps)

### Phase 1: Authentication & Schedule Access (Steps 1-3)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | View provider schedule (day view) | Appointment schedule visible |

### Phase 2: Schedule Navigation (Steps 4-5)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 4 | Navigate to next week | Calendar advances one week |
| 5 | Return to current day | Shows today's schedule |

### Phase 3: Create Appointment (Steps 6-9)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 6 | Click time slot to create appointment | Appointment form opens |
| 7 | Search and select patient (FAKE-Jones) | Patient linked to appointment |
| 8 | Set appointment type and reason | Type and reason selected |
| 9 | Save appointment | Appointment appears on schedule |

### Phase 4: Appointment Management (Steps 10-12)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 10 | Click appointment to edit | Edit dialog opens |
| 11 | Change appointment status | Status updated |
| 12 | View patient's appointment history | History displays |

### Phase 5: Schedule Views (Steps 13-14)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 13 | Switch to month view | Month calendar displayed |
| 14 | Return to day view | Day view restored |

### Phase 6: Cleanup (Step 15)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 15 | Cancel/delete test appointment | Appointment removed |

## Test Data Values

### Appointment Details (Created During Test)
```
Patient: FAKE-Jacky, FAKE-Jones (ID: 1)
Provider: openodoc
Type: (select first available)
Reason: UI Test Appointment
Duration: 15 minutes (default)
Notes: Created by UI Test 3
```

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-3-01-login-page.png | Auth | Login form |
| test-3-02-provider-dashboard.png | Auth | Dashboard |
| test-3-03-schedule-day-view.png | Schedule | Day view |
| test-3-04-schedule-next-week.png | Nav | Next week view |
| test-3-05-schedule-today.png | Nav | Return to today |
| test-3-06-create-appointment-form.png | Create | Empty form |
| test-3-07-patient-selected.png | Create | Patient linked |
| test-3-08-appointment-details.png | Create | Type/reason set |
| test-3-09-appointment-saved.png | Create | On schedule |
| test-3-10-edit-appointment.png | Manage | Edit dialog |
| test-3-11-status-changed.png | Manage | Status updated |
| test-3-12-appointment-history.png | Manage | History view |
| test-3-13-month-view.png | Views | Month calendar |
| test-3-14-day-view-restored.png | Views | Day view |
| test-3-15-appointment-deleted.png | Cleanup | Appointment removed |

## Expected Warnings (Non-Blocking)

### 404 Errors
- `/oscar/js/dateFormatUtils.js` - Schedule pages (expected)
- `/oscar/js/custom/default/master.js` - Various pages (expected)

### JavaScript Warnings
- `Unexpected token '%'` - JSP template parsing (expected)

## Success Criteria

### Test Passes When:
- [ ] All 15 steps complete without blocking errors
- [ ] All 15 screenshots captured
- [ ] Appointment created successfully
- [ ] Appointment status changed successfully
- [ ] All schedule views work
- [ ] Test appointment deleted at end

### Test Fails When:
- Login fails
- Schedule doesn't load
- Appointment creation fails
- Status changes don't save
- Schedule views don't switch
- New 404 errors appear

## Troubleshooting

### Schedule Doesn't Load
1. Verify provider has schedule template assigned
2. Check if provider_no matches logged-in user
3. Verify date range is valid

### Appointment Creation Fails
1. Check patient exists in database
2. Verify appointment type codes exist
3. Check for JavaScript errors in console

### Schedule Views Don't Change
1. Check for JavaScript errors
2. Verify calendar JavaScript loaded
3. Try refreshing page

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [Test 2 - Demographics](../test-2/test-2-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-3-EXECUTION.md)
