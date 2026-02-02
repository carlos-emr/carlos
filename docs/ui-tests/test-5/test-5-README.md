# Test 5: Ticklers & Messaging

## Overview

Test 5 validates the tickler (task reminder) and messaging workflows in OpenO EMR, including creating ticklers, assigning to providers, managing tickler status, and viewing the inbox.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~8-10 minutes
**Steps**: 12
**Screenshots**: 12

## What This Test Validates

1. **Tickler Dashboard** - Access and view tickler list
2. **Tickler Creation** - Create new tickler with patient link
3. **Provider Assignment** - Assign tickler to provider
4. **Date Setting** - Set tickler service date
5. **Status Management** - Update tickler status
6. **Message Composition** - Send message via messaging system
7. **Inbox Access** - View provider inbox

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

## Test Workflow (12 Steps)

### Phase 1: Authentication & Tickler Access (Steps 1-3)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | Click Tickler menu | Tickler dashboard opens |

### Phase 2: Create Tickler (Steps 4-7)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 4 | Click "Create New Tickler" | Tickler form opens |
| 5 | Link to patient (FAKE-Jones) | Patient linked |
| 6 | Set service date and assign provider | Date and provider set |
| 7 | Save tickler | Tickler created |

### Phase 3: Tickler Management (Steps 8-9)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 8 | View tickler in list | Tickler visible |
| 9 | Edit tickler status | Status updated |

### Phase 4: Messaging (Steps 10-12)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 10 | Open messaging/Msg menu | Message module opens |
| 11 | Compose message | Message form visible |
| 12 | View inbox | Inbox displays |

## Test Data Values

### Tickler (Created During Test)
```
Message: UI Test 5 - Follow up call needed
Service Date: (today + 7 days)
Assigned To: openodoc
Priority: Normal
Patient: FAKE-Jacky FAKE-Jones (ID: 1)
```

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-5-01-login-page.png | Auth | Login form |
| test-5-02-provider-dashboard.png | Auth | Dashboard |
| test-5-03-tickler-dashboard.png | Tickler | Tickler list |
| test-5-04-new-tickler-form.png | Create | Empty form |
| test-5-05-patient-linked.png | Create | Patient selected |
| test-5-06-tickler-details.png | Create | Date/provider set |
| test-5-07-tickler-saved.png | Create | Tickler created |
| test-5-08-tickler-list.png | Manage | List with new tickler |
| test-5-09-status-updated.png | Manage | Status changed |
| test-5-10-messaging-module.png | Message | Message module |
| test-5-11-compose-message.png | Message | Compose form |
| test-5-12-inbox.png | Message | Inbox view |

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
- [ ] Tickler created successfully
- [ ] Tickler linked to patient
- [ ] Status update works
- [ ] Messaging module accessible

### Test Fails When:
- Login fails
- Tickler module doesn't load
- Tickler creation fails
- Patient linking fails
- New 404 errors appear

## Troubleshooting

### Tickler Dashboard Empty
1. Verify ticklers exist in database
2. Check date filter settings
3. Verify provider assignment

### Tickler Won't Save
1. Check required fields filled
2. Verify service date is valid
3. Check JavaScript console for errors

### Patient Search Fails
1. Confirm patient exists
2. Verify search term correct
3. Press Enter to submit search

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [Test 4 - Prescriptions](../test-4/test-4-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-5-EXECUTION.md)
