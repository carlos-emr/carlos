# Test 4: Prescriptions (Rx)

## Overview

Test 4 validates the prescription management workflow in OpenO EMR, including drug search, allergy management, prescription creation, interaction checking, and prescription history.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~15-20 minutes
**Steps**: 18
**Screenshots**: 18

## What This Test Validates

1. **Rx Module Access** - Navigate to prescription module for patient
2. **Allergy Management** - Add and view patient allergies
3. **Drug Search** - Search drug database by name
4. **Prescription Creation** - Create new prescription with dosage
5. **Interaction Checking** - Verify drug interaction warnings
6. **Prescription Save** - Save and verify prescription
7. **Prescription History** - View prescription history
8. **Re-Prescribe** - Re-prescribe existing medication
9. **Print Preview** - Preview prescription for printing

## Prerequisites

### Application Requirements
- OpenO EMR running at http://localhost:8080/oscar
- Database accessible and writable
- Drug database populated (DrugRef data)
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

## Test Workflow (18 Steps)

### Phase 1: Authentication & Patient Access (Steps 1-4)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | Search for test patient | Patient found |
| 4 | Open patient chart | E-chart or patient view opens |

### Phase 2: Prescription Module Access (Steps 5-6)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 5 | Click Prescriptions/Rx link | Rx module opens |
| 6 | View current medications | Medication list displays |

### Phase 3: Allergy Management (Steps 7-8)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 7 | Add allergy (Penicillin) | Allergy form opens |
| 8 | Save allergy | Allergy added to record |

### Phase 4: Create Prescription (Steps 9-14)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 9 | Click "New Prescription" | Rx entry form opens |
| 10 | Search drug database (Acetaminophen) | Drug results displayed |
| 11 | Select drug and dosage | Drug added to prescription |
| 12 | Set quantity and refills | Quantity/refills set |
| 13 | Check for interactions | Interaction check runs |
| 14 | Save prescription | Prescription saved |

### Phase 5: Prescription Management (Steps 15-18)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 15 | View prescription history | History list shows |
| 16 | Select existing prescription | Prescription details shown |
| 17 | Re-prescribe medication | Re-prescription created |
| 18 | Preview print | Print preview displays |

## Test Data Values

### Allergy (Added During Test)
```
Allergen: Penicillin
Type: Drug
Reaction: Hives
Severity: Moderate
```

### Prescription (Created During Test)
```
Drug: Acetaminophen 500mg Tablet
Dosage: Take 1 tablet by mouth every 6 hours as needed for pain
Quantity: 30
Refills: 0
Duration: 10 days
```

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-4-01-login-page.png | Auth | Login form |
| test-4-02-provider-dashboard.png | Auth | Dashboard |
| test-4-03-patient-search.png | Access | Search results |
| test-4-04-patient-chart.png | Access | Patient chart |
| test-4-05-rx-module.png | Rx | Prescription module |
| test-4-06-current-meds.png | Rx | Current medications |
| test-4-07-add-allergy.png | Allergy | Allergy form |
| test-4-08-allergy-saved.png | Allergy | Allergy confirmed |
| test-4-09-new-rx-form.png | Create | Empty Rx form |
| test-4-10-drug-search.png | Create | Drug search results |
| test-4-11-drug-selected.png | Create | Drug added |
| test-4-12-quantity-set.png | Create | Quantity/refills |
| test-4-13-interaction-check.png | Create | Interaction check |
| test-4-14-rx-saved.png | Create | Prescription saved |
| test-4-15-rx-history.png | Manage | History list |
| test-4-16-rx-details.png | Manage | Rx details |
| test-4-17-re-prescribe.png | Manage | Re-prescription |
| test-4-18-print-preview.png | Manage | Print preview |

## Expected Warnings (Non-Blocking)

### 404 Errors
- `/oscar/js/dateFormatUtils.js` - Various pages (expected)
- `/oscar/js/custom/default/master.js` - Various pages (expected)

### JavaScript Warnings
- `Unexpected token '%'` - JSP template parsing (expected)

## Success Criteria

### Test Passes When:
- [ ] All 18 steps complete without blocking errors
- [ ] All 18 screenshots captured
- [ ] Allergy added successfully
- [ ] Prescription created successfully
- [ ] Drug search returns results
- [ ] Interaction check runs
- [ ] Print preview displays

### Test Fails When:
- Login fails
- Rx module doesn't load
- Drug search returns nothing
- Prescription creation fails
- Allergy not saved
- New 404 errors appear

## Troubleshooting

### Drug Search Returns Nothing
1. Verify drug database is populated
2. Check for JavaScript errors
3. Try different search terms
4. Verify DrugRef integration active

### Prescription Won't Save
1. Check required fields filled
2. Verify patient linked
3. Check for validation errors
4. Verify provider has Rx privileges

### Allergy Not Showing
1. Confirm save was successful
2. Refresh patient chart
3. Check allergies panel visibility

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [Test 2 - Demographics](../test-2/test-2-README.md)
- [Test 3 - Appointments](../test-3/test-3-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-4-EXECUTION.md)
