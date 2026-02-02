# Test 2: Comprehensive Demographic Module Test

## Overview

Test 2 is a **comprehensive end-to-end UI test** for the Demographic module in OpenO EMR. Unlike Test 1 (smoke test with 7 steps), Test 2 thoroughly validates all major demographic workflows including patient creation, data modification, search functionality, and filters.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~25-30 minutes
**Steps**: 30
**Screenshots**: 30

## What This Test Validates

1. **Authentication** - Login workflow with PIN
2. **Patient Creation** - Create a new patient with all required fields
3. **Patient Search** - Multiple search methods (name, HIN)
4. **Search Filters** - Active/Inactive/All patient status filters
5. **Patient Profile View** - Complete demographic record display
6. **Patient Edit** - Edit and save multiple fields (phone, email, address, city)
7. **Data Persistence** - Verify all changes save correctly
8. **Healthcare Team** - View MRP and team members
9. **Clinic Status** - View patient and roster status
10. **Cleanup** - Mark test patient as Inactive

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
| ID | Name | HIN | Status |
|----|------|-----|--------|
| 1 | FAKE-Jacky, FAKE-Jones | 9876543225 | Active |
| 182 | FAKE-Gaylord, FAKE-Branda | 2088617755 | Active |

### Pre-Test Cleanup (For Re-Runs)

Before running the test, check if a previous test patient exists:

```sql
SELECT demographic_no, last_name, first_name, patient_status
FROM demographic
WHERE last_name = 'TEST-UITEST2';
```

If a record exists, delete it:
```sql
DELETE FROM demographic WHERE last_name = 'TEST-UITEST2';
```

## Test Workflow (30 Steps)

### Phase 1: Authentication & Navigation (Steps 1-3)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | Click "Search" menu | Patient search page opens |

### Phase 2: Create New Patient (Steps 4-8)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 4 | Search any term, click "Create Demographic" | Add demographic form opens |
| 5 | Fill name, DOB, sex | Required fields populated |
| 6 | Fill phone, email (leave HIN empty) | Contact fields populated |
| 7 | Set Doctor (MRP) | Provider field populated |
| 8 | Click "Add Record", **wait 5 sec**, log console | Success message, patient created |

> **IMPORTANT**: The "Create Demographic" link only appears after performing a search.
> Search for any term (e.g., "TEST"), then click the link at the bottom of results.

### Phase 3: Verify New Patient (Steps 9-12)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 9 | Search for "TEST-UITEST2" | New patient appears in results |
| 10 | Click on new patient | Full profile displays |
| 11 | Verify demographics section | Name, DOB, Sex correct |
| 12 | Verify contact section | Phone, Email correct |

### Phase 4: Edit Multiple Fields (Steps 13-18)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 13 | Click "Edit Demographic" | Edit form opens |
| 14 | Change phone to 416-555-9999 | Phone updated |
| 15 | Change email to updated@openoemr.test | Email updated |
| 16 | Change address to 456 Updated Avenue | Address updated |
| 17 | Change city to Ottawa | City updated |
| 18 | Click "Update Record" | Changes saved |

### Phase 5: Verify Edits Persisted (Steps 19-21)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 19 | Verify phone | Shows 416-555-9999 |
| 20 | Verify email | Shows updated@openoemr.test |
| 21 | Verify address | Shows 456 Updated Avenue, Ottawa |

### Phase 6: Search Methods & Filters (Steps 22-26)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 22 | Search by HIN 9876543225 | Finds patient ID 1 |
| 23 | Click patient ID 1 | FAKE-Jones profile displays |
| 24 | Click "Inactive" filter | Shows inactive patients only |
| 25 | Click "All" filter | Shows all patients |
| 26 | Click "Active" filter | Returns to active patients |

### Phase 7: Healthcare Team & Status (Steps 27-28)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 27 | View Healthcare Team section | MRP visible |
| 28 | View Clinic Status section | Patient/Roster status visible |

### Phase 8: Cleanup (Steps 29-30)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 29 | Edit TEST-UITEST2, set status to IN | Status changed |
| 30 | Save and verify | Patient marked Inactive |

## Test Data Values

### New Patient (Created During Test)
```
Last Name: TEST-UITEST2
First Name: Patient
DOB: 1990-01-01
Sex: M (Male)
HIN: (leave empty - strict validation)
Phone: 416-555-0199
Email: test.uitest2@example.com
Doctor (MRP): openodoc, doctor
```

> **NOTE**: HIN is intentionally left empty because the field has strict validation
> that rejects invalid formats. This is acceptable for test purposes.

### Edit Values (Applied During Test)
```
Phone: 416-555-9999
Email: updated@openoemr.test
Address: 456 Updated Avenue
City: Ottawa
```

### Cleanup Values
```
Patient Status: IN (Inactive)
```

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-2-01-login-page.png | Auth | Login form |
| test-2-02-provider-dashboard.png | Auth | Dashboard |
| test-2-03-search-page.png | Nav | Search page |
| test-2-04-add-patient-form.png | Create | Empty add form |
| test-2-05-add-patient-filled.png | Create | Name/DOB filled |
| test-2-06-add-patient-address.png | Create | Address filled |
| test-2-07-add-patient-contact.png | Create | Contact filled |
| test-2-08-add-patient-success.png | Create | Success message |
| test-2-09-search-new-patient.png | Verify | Search results |
| test-2-10-new-patient-profile.png | Verify | Profile view |
| test-2-11-demographics-section.png | Verify | Demographics |
| test-2-12-contact-section.png | Verify | Contact info |
| test-2-13-edit-form-open.png | Edit | Edit form |
| test-2-14-edit-phone.png | Edit | Phone changed |
| test-2-15-edit-email.png | Edit | Email changed |
| test-2-16-edit-address.png | Edit | Address changed |
| test-2-17-edit-city.png | Edit | City changed |
| test-2-18-update-saved.png | Edit | Update saved |
| test-2-19-verify-phone.png | Verify | Phone confirmed |
| test-2-20-verify-email.png | Verify | Email confirmed |
| test-2-21-verify-address.png | Verify | Address confirmed |
| test-2-22-search-by-hin.png | Search | HIN results |
| test-2-23-patient1-view.png | Search | Patient 1 |
| test-2-24-filter-inactive.png | Filter | Inactive filter |
| test-2-25-filter-all.png | Filter | All filter |
| test-2-26-filter-active.png | Filter | Active filter |
| test-2-27-healthcare-team.png | Team | Healthcare team |
| test-2-28-clinic-status.png | Status | Clinic status |
| test-2-29-mark-inactive.png | Cleanup | Status changed |
| test-2-30-final-verification.png | Final | Inactive verified |

## Expected Warnings (Non-Blocking)

### 404 Errors
- `/oscar/js/dateFormatUtils.js` - Search page (expected)
- `/oscar/js/custom/default/master.js` - Demographic pages (expected)

### JavaScript Warnings
- `Unexpected token '%'` - JSP template parsing (expected)
- `TypeError: Cannot read properties of undefined` - Login page (expected)

## Success Criteria

### Test Passes When:
- [ ] All 30 steps complete without blocking errors
- [ ] All 30 screenshots captured
- [ ] New patient created successfully
- [ ] All edits save and persist correctly
- [ ] All search methods work
- [ ] All filters work correctly
- [ ] Test patient marked Inactive at end

### Test Fails When:
- Login fails
- Patient creation fails
- Edits don't save
- Search returns unexpected results
- New 404 errors appear
- Layout significantly broken

## Troubleshooting

### Patient Creation Fails
1. Check if TEST-UITEST2 already exists (see Pre-Test Cleanup)
2. Verify database write permissions
3. Check form validation messages

### Edits Don't Save
1. Check for JavaScript errors in console
2. Verify form submission completes
3. Check database after save

### Search Returns No Results
1. Verify test patients exist in database
2. Check search mode (Name vs HIN dropdown)
3. Ensure Enter key pressed after search term

## Data Safety

### Test Patient Identification
- All test patients use TEST-UITEST2 prefix
- Final state is Inactive (won't appear in normal searches)

### Post-Test Verification
```sql
SELECT demographic_no, last_name, patient_status
FROM demographic
WHERE last_name = 'TEST-UITEST2';
-- Should show patient_status = 'IN'
```

### Manual Cleanup (If Needed)
```sql
DELETE FROM demographic WHERE last_name = 'TEST-UITEST2';
```

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-2-EXECUTION.md)
