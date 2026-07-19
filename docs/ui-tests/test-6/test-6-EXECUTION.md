# Test 6 Execution Guide

## Pre-Flight Checklist

Before executing Test 6, verify all prerequisites are met.

### 1. Application Status
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/oscar/index.jsp
# Should return: 200
```

### 2. Database Connectivity
```bash
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"
```

### 3. Test Patient Exists
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT demographic_no, last_name, first_name
FROM demographic WHERE demographic_no = 1;"
```

### 4. Create Test Run Directory
```bash
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-6/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-6"
```

---

## ⚠️ Critical: E-Chart Navigation

### E-Chart Entry Point (IMPORTANT)

**DO NOT** navigate directly to `CaseManagementEntry.do` - this causes 500 errors.

**MUST USE** `IncomingEncounter.do` as the entry point:
```
/oscar/oscarEncounter/IncomingEncounter.do?providerNo={providerNo}&appointmentNo=&demographicNo={demographicNo}&curProviderNo=&reason=Tel-Progress+Note&encType=&curDate={YYYY-M-DD}&appointmentDate=&startTime=&status=
```

**For Playwright automation:**
1. Extract the E-Chart URL from the "E" link's onclick handler using `browser_evaluate`
2. Navigate directly to the URL in the same tab using `browser_navigate`
3. Avoid `browser_tabs select` on E-Chart pages (causes timeouts due to heavy AJAX)

### Known Limitations

- **Measurements**: May only have "Test" group configured (no vital signs groups)
- **Issue Search**: Searches existing patient issues, NOT ICD codes
- **Hover Menus**: Use `click({ force: true })` via `browser_run_code` for panel "+" buttons

---

## Test Execution

### Phase 1: Authentication & E-Chart Access

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-6-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-6-02-provider-dashboard.png`
**Expected**: Provider dashboard with navigation menu

#### Step 3: Search for Test Patient
**Action**:
1. Click "Search" in navigation
2. Type "FAKE-J" in search field
3. Press Enter

**Screenshot**: `test-6-03-patient-search.png`
**Expected**: Search results showing FAKE-Jones patient

#### Step 4: Click E-Chart Link
**Action**: Click "E" or "E-Chart" link for patient
**Screenshot**: `test-6-04-echart-link.png`
**Expected**: E-Chart loading or transition

---

### Phase 2: E-Chart Overview

#### Step 5: View E-Chart Overview
**Action**: Wait for E-Chart to fully load
**Screenshot**: `test-6-05-echart-overview.png`
**Expected**: E-Chart main view with:
- Left panel with module links
- Center encounter area
- Patient context header

#### Step 6: Review Patient Header
**Action**: View patient information header
**Screenshot**: `test-6-06-patient-header.png`
**Expected**: Patient demographics visible (name, DOB, age, sex)

---

### Phase 3: Create Encounter

#### Step 7: Click New Encounter
**Action**: Click "+" or "New Encounter" button
**Screenshot**: `test-6-07-new-encounter.png`
**Expected**: New encounter creation initiated

#### Step 8: View Encounter Editor
**Action**: Wait for encounter editor to open
**Screenshot**: `test-6-08-encounter-editor.png`
**Expected**: Encounter note editor with sections:
- Subjective/HPI
- Objective/Exam
- Assessment
- Plan

#### Step 9: Select Encounter Type
**Action**: Select encounter type from dropdown (e.g., "Office Visit")
**Screenshot**: `test-6-09-encounter-type.png`
**Expected**: Encounter type selected and displayed

---

### Phase 4: Vital Signs

#### Step 10: Open Vital Signs Form
**Action**:
1. Find measurements/vitals link in E-Chart
2. Click to open vital signs entry form
3. Enter values:
   - Blood Pressure: `120/80`
   - Heart Rate: `72`
   - Temperature: `37.0`

**Screenshot**: `test-6-10-vitals-form.png`
**Expected**: Vital signs form with values entered

#### Step 11: Save Measurements
**Action**: Click Save or submit vital signs
**Screenshot**: `test-6-11-vitals-saved.png`
**Expected**: Measurements saved and displayed in encounter

---

### Phase 5: Diagnosis & Issues

#### Step 12: Search for Diagnosis
**Action**:
1. Find diagnosis/issue search
2. Search for "upper respiratory" or ICD code "J06"
3. View search results

**Screenshot**: `test-6-12-diagnosis-search.png`
**Expected**: ICD diagnosis search results displayed

#### Step 13: Add Diagnosis to Encounter
**Action**: Select diagnosis and link to encounter
**Screenshot**: `test-6-13-diagnosis-added.png`
**Expected**: Diagnosis appears in encounter problem list

---

### Phase 6: Clinical Notes

#### Step 14: Add Assessment Text
**Action**: In Assessment section, type:
"Patient presents with symptoms of upper respiratory infection."

**Screenshot**: `test-6-14-assessment-text.png`
**Expected**: Assessment text visible in editor

#### Step 15: Add Plan Text
**Action**: In Plan section, type:
"Rest, fluids, symptomatic treatment. Follow up in 1 week if not improved."

**Screenshot**: `test-6-15-plan-text.png`
**Expected**: Plan text visible in editor

---

### Phase 7: Panel Navigation

#### Step 16: View Allergies Panel
**Action**: Click allergies link in E-Chart left panel
**Screenshot**: `test-6-16-allergies-panel.png`
**Expected**: Allergies list displayed (may show Penicillin from Test 4)

#### Step 17: View Medications Panel
**Action**: Click medications/Rx link in E-Chart
**Screenshot**: `test-6-17-medications-panel.png`
**Expected**: Current medications list displayed

#### Step 18: View Lab Results Panel
**Action**: Click labs link in E-Chart
**Screenshot**: `test-6-18-labs-panel.png`
**Expected**: Lab results panel (may be empty)

#### Step 19: View Prevention Panel
**Action**: Click prevention/immunizations link in E-Chart
**Screenshot**: `test-6-19-prevention-panel.png`
**Expected**: Prevention records displayed

---

### Phase 8: Finalize Encounter

#### Step 20: Save Encounter
**Action**: Click "Save" or "Sign & Save" button
**Screenshot**: `test-6-20-encounter-saved.png`
**Expected**: Encounter saved confirmation

#### Step 21: Print Encounter Note
**Action**: Click "Print" or "Print Preview"
**Screenshot**: `test-6-21-print-preview.png`
**Expected**: Print preview showing formatted encounter note

#### Step 22: View Encounter History
**Action**: Navigate to encounter history list
**Screenshot**: `test-6-22-encounter-history.png`
**Expected**: List of encounters including new one

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-6/screenshots/test-6-*.png | wc -l
# Should show: 22
```

### 2. Database Verification
```bash
# Verify encounter was created
mysql -h db -uroot -ppassword oscar -e "
SELECT id, observation_date, provider_no
FROM casemgmt_note
WHERE demographic_no = 1
ORDER BY id DESC LIMIT 1;"

# Verify measurements were recorded
mysql -h db -uroot -ppassword oscar -e "
SELECT id, type, dataField, dateObserved
FROM measurements
WHERE demographicNo = 1
ORDER BY id DESC LIMIT 5;"
```

### 3. Console Warnings Check
Expected 404 errors (non-blocking):
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

---

## Gold Standard Promotion

After a successful test run:

```bash
cp ui-test-runs/$TIMESTAMP/test-6/screenshots/test-6-*.png \
   docs/ui-tests/test-6/screenshots/

ls -lh docs/ui-tests/test-6/screenshots/test-6-*.png | wc -l
# Should show: 22
```

---

## Decision Tree

### Test PASSES when:
- All 22 steps complete
- All 22 screenshots captured
- E-Chart opens successfully
- Encounter created and saved
- Vital signs recorded
- Diagnosis added
- Notes saved
- All panels accessible

### Test FAILS when:
- Login fails
- E-Chart won't load
- Encounter creation fails
- Measurements won't save
- New errors appear

---

## Troubleshooting

### E-Chart Blank or Slow
1. Wait for full page load
2. Check JavaScript console
3. Try refreshing
4. May need to clear session

### Encounter Editor Not Opening
1. Check if popup blocked
2. Look for new tab/window
3. Verify encounter button clicked
4. Check JavaScript errors

### Measurements Not Saving
1. Verify values entered correctly
2. Check for validation errors
3. Ensure save button clicked
4. Check database write permissions

### Panels Empty
1. This may be expected for test patient
2. Verify panel is loading
3. Check JavaScript console
