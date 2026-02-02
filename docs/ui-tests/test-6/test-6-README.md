# Test 6: Encounter & E-Chart

## Overview

Test 6 validates the clinical documentation workflow in OpenO EMR, including the E-Chart interface, encounter creation, vital signs entry, diagnosis coding, and clinical note documentation.

**Test Type**: Comprehensive End-to-End Test
**Duration**: ~20-25 minutes
**Steps**: 22
**Screenshots**: 22

## What This Test Validates

1. **E-Chart Access** - Navigate to E-Chart from patient search
2. **E-Chart Overview** - View patient clinical summary
3. **Encounter Creation** - Create new clinical encounter
4. **Vital Signs** - Add measurements (BP, HR, Temp)
5. **Diagnosis/Issues** - Add ICD diagnosis code
6. **Clinical Notes** - Add assessment and plan text
7. **Panel Navigation** - View allergies, meds, labs, prevention panels
8. **Tickler from Encounter** - Create tickler within encounter
9. **Encounter Save** - Save complete encounter
10. **Print/View** - Print encounter note preview
11. **Encounter History** - View past encounters

## Prerequisites

### Application Requirements
- OpenO EMR running at http://localhost:8080/oscar
- Database accessible and writable
- ICD diagnosis codes loaded
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

## Test Workflow (22 Steps)

### Phase 1: Authentication & E-Chart Access (Steps 1-4)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login form displays |
| 2 | Login with credentials | Provider dashboard loads |
| 3 | Search for test patient | Patient found |
| 4 | Click E-Chart link | E-Chart opens |

### Phase 2: E-Chart Overview (Steps 5-6)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 5 | View E-Chart overview | Summary page displays |
| 6 | Review patient info header | Demographics visible |

### Phase 3: Create Encounter (Steps 7-9)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 7 | Click "+" to create encounter | Encounter editor opens |
| 8 | View encounter editor | Note sections visible |
| 9 | Select encounter type | Type selected |

### Phase 4: Vital Signs (Steps 10-11)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 10 | Add vital signs (BP, HR, Temp) | Measurements form opens |
| 11 | Save measurements | Vitals recorded |

### Phase 5: Diagnosis & Issues (Steps 12-13)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 12 | Add diagnosis (ICD code) | Diagnosis search opens |
| 13 | Link issue to encounter | Diagnosis added |

### Phase 6: Clinical Notes (Steps 14-15)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 14 | Add assessment text | Assessment recorded |
| 15 | Add plan text | Plan recorded |

### Phase 7: Panel Navigation (Steps 16-19)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 16 | View allergies panel | Allergies list shown |
| 17 | View medications panel | Medications list shown |
| 18 | View lab results panel | Lab results shown |
| 19 | View prevention panel | Prevention records shown |

### Phase 8: Finalize Encounter (Steps 20-22)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 20 | Save encounter | Encounter saved |
| 21 | Print encounter note | Print preview opens |
| 22 | View encounter history | History list shows |

## Test Data Values

### Vital Signs (Added During Test)
```
Blood Pressure: 120/80 mmHg
Heart Rate: 72 bpm
Temperature: 37.0 C
```

### Diagnosis (Added During Test)
```
ICD Code: J06.9 (or similar)
Description: Acute upper respiratory infection
```

### Clinical Notes
```
Assessment: Patient presents with symptoms of upper respiratory infection.
Plan: Rest, fluids, symptomatic treatment. Follow up in 1 week if not improved.
```

## Screenshot Reference

| Screenshot | Phase | Description |
|------------|-------|-------------|
| test-6-01-login-page.png | Auth | Login form |
| test-6-02-provider-dashboard.png | Auth | Dashboard |
| test-6-03-patient-search.png | Access | Search results |
| test-6-04-echart-link.png | Access | E-Chart link clicked |
| test-6-05-echart-overview.png | Overview | E-Chart main view |
| test-6-06-patient-header.png | Overview | Patient info header |
| test-6-07-new-encounter.png | Create | New encounter button |
| test-6-08-encounter-editor.png | Create | Editor opened |
| test-6-09-encounter-type.png | Create | Type selected |
| test-6-10-vitals-form.png | Vitals | Measurements form |
| test-6-11-vitals-saved.png | Vitals | Measurements recorded |
| test-6-12-diagnosis-search.png | Diagnosis | ICD search |
| test-6-13-diagnosis-added.png | Diagnosis | Issue linked |
| test-6-14-assessment-text.png | Notes | Assessment entered |
| test-6-15-plan-text.png | Notes | Plan entered |
| test-6-16-allergies-panel.png | Panels | Allergies view |
| test-6-17-medications-panel.png | Panels | Medications view |
| test-6-18-labs-panel.png | Panels | Lab results view |
| test-6-19-prevention-panel.png | Panels | Prevention view |
| test-6-20-encounter-saved.png | Finalize | Save confirmation |
| test-6-21-print-preview.png | Finalize | Print preview |
| test-6-22-encounter-history.png | Finalize | History list |

## Expected Warnings (Non-Blocking)

### 404 Errors
- `/oscar/js/dateFormatUtils.js` - Various pages (expected)
- `/oscar/js/custom/default/master.js` - Various pages (expected)

### JavaScript Warnings
- `Unexpected token '%'` - JSP template parsing (expected)

## Success Criteria

### Test Passes When:
- [ ] All 22 steps complete without blocking errors
- [ ] All 22 screenshots captured
- [ ] E-Chart opens successfully
- [ ] Encounter created and saved
- [ ] Vital signs recorded
- [ ] Diagnosis added
- [ ] Clinical notes saved
- [ ] All panels accessible

### Test Fails When:
- Login fails
- E-Chart doesn't load
- Encounter creation fails
- Measurements won't save
- Diagnosis search fails
- New 404 errors appear

## Troubleshooting

### E-Chart Won't Load
1. Verify patient exists
2. Check JavaScript console
3. Try different patient
4. Clear browser cache

### Encounter Won't Save
1. Check required fields
2. Verify encounter type selected
3. Check for validation errors
4. Check JavaScript console

### Vital Signs Form Missing
1. Look for measurements link/button
2. Check E-Chart left panel
3. May be in different location per config

## Related Documentation

- [Test 1 - Smoke Test](../test-1/test-1-README.md)
- [Test 4 - Prescriptions](../test-4/test-4-README.md)
- [UI Test Process](../UI-TEST-PROCESS.md)
- [Execution Guide](./test-6-EXECUTION.md)
