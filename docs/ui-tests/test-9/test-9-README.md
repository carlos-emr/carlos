# Test 9: Prevention & Immunization

## Overview

Test 9 validates the preventive care and immunization tracking workflow in OpenO EMR, including viewing prevention records, adding immunizations, and printing prevention reports.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~10-12 minutes
**Steps**: 12
**Screenshots**: 12

## What This Test Validates

1. **Prevention Module Access** - Navigate to prevention records
2. **Prevention Record View** - View patient's prevention history
3. **Immunization Entry** - Add new immunization record
4. **Vaccine Selection** - Select vaccine type
5. **Date Entry** - Set administration date
6. **Save Record** - Save immunization
7. **Immunization History** - View immunization history
8. **Prevention Report** - Print prevention report

## Prerequisites

### Application Requirements
- OpenO EMR running at http://localhost:8080/oscar
- Database accessible and writable
- Prevention/immunization module configured
- Playwright MCP configured (headless Chromium)

### Test Data Requirements

#### Provider Credentials
| Field | Value |
|-------|-------|
| Username | openodoc |
| Password | openo2025 |
| PIN | 2025 |
| Provider No | 999998 |

#### Existing Test Patients
| ID | Name | Status |
|----|------|--------|
| 1 | FAKE-Jacky, FAKE-Jones | Active |

## Test Workflow (12 Steps)

### Phase 1: Authentication & Prevention Access (Steps 1-4)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | Search for test patient | Patient found |
| 4 | Open patient chart | E-Chart/demographic opens |

### Phase 2: Prevention Records (Steps 5-6)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 5 | Click Preventions link | Prevention module opens |
| 6 | View prevention record | Prevention list displays |

### Phase 3: Add Immunization (Steps 7-10)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 7 | Add new immunization | Immunization form opens |
| 8 | Select vaccine type | Vaccine selected |
| 9 | Set date administered | Date set |
| 10 | Save immunization | Record saved |

### Phase 4: History & Reports (Steps 11-12)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 11 | View immunization history | History displays |
| 12 | Print prevention report | Report preview |

## Test Data Values

### Immunization (Added During Test)
```
Vaccine Type: Influenza (or similar available vaccine)
Date Administered: Today's date
Route: IM (intramuscular)
Site: Left arm
Lot Number: TEST-LOT-001
Provider: openodoc
```

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-9-01-login-page.png | Auth | Login form |
| test-9-02-provider-dashboard.png | Auth | Dashboard |
| test-9-03-patient-search.png | Access | Search results |
| test-9-04-patient-chart.png | Access | Patient chart |
| test-9-05-preventions-link.png | Prevention | Prevention clicked |
| test-9-06-prevention-record.png | Prevention | Prevention list |
| test-9-07-add-immunization.png | Immunize | Immunization form |
| test-9-08-vaccine-type.png | Immunize | Vaccine selected |
| test-9-09-date-set.png | Immunize | Date entered |
| test-9-10-immunization-saved.png | Immunize | Save confirmed |
| test-9-11-immunization-history.png | History | History view |
| test-9-12-prevention-report.png | Report | Report preview |

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
- [ ] Prevention module accessible
- [ ] Immunization added successfully
- [ ] History displays correctly
- [ ] Report preview available

### Test Fails When:
- Login fails
- Prevention module not found
- Immunization creation fails
- New 404 errors appear

## Troubleshooting

### Prevention Module Not Found
1. Check patient chart for Prevention link
2. May be in E-Chart left panel
3. Look for Immunizations link

### Vaccine Types Empty
1. Verify prevention types configured
2. Check database for vaccine options
3. Try searching for common vaccines

### Immunization Won't Save
1. Check required fields
2. Verify date format correct
3. Check JavaScript console

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [Test 6 - Encounters](../test-6/test-6-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-9-EXECUTION.md)
