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
/oscar/encounter/IncomingEncounter.do?providerNo={providerNo}&appointmentNo=&demographicNo={demographicNo}&curProviderNo=&reason=Tel-Progress+Note&encType=&curDate={YYYY-M-DD}&appointmentDate=&startTime=&status=
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
- Username: `carlosdoc`
- Password: `carlos2026`
- PIN: `2026`

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

### Phase 9: Autosave Draft Survival Regression (issue #1873)

**Purpose**: End-to-end regression guard for PR #1887. Ensures that exiting an encounter
without saving does **not** delete the autosaved draft, so reopening the encounter
restores the unsaved text instead of losing it.

**Pre-requisite**: a clean `casemgmt_tmpsave` state for the test patient+provider. Run:
```bash
mysql -h db -uroot -ppassword oscar -e "
DELETE FROM casemgmt_tmpsave
WHERE demographic_no = '1'
  AND provider_no = (SELECT provider_no FROM provider WHERE user_name = 'carlosdoc' LIMIT 1);"
```

#### Step 23: Create fresh encounter and type probe text
**Action**:
1. From the E-Chart for patient `demographicNo=1`, open a new encounter using the
   `IncomingEncounter.do` pattern (see "E-Chart Navigation" section above).
2. Generate a unique probe token: `PROBE="AUTOSAVE-PROBE-$TIMESTAMP"`.
3. Type the probe token into the encounter note textarea (element id `caseNote`).
4. Wait **≥ 7 seconds** so the 5-second autosave timer fires and `autoSave()` POSTs
   to `/oscar/CaseManagementEntry?method=autosave`. The "draft saved" timestamp
   should appear next to the textarea (`#autosaveTime`).

**Screenshot**: `test-6-23-probe-typed-autosaved.png`
**Expected**: Note contains probe token; `#autosaveTime` shows recent draft-saved
timestamp.

**Optional DB assertion** (skip if `mysql` unavailable in runner):
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT note FROM casemgmt_tmpsave
WHERE demographic_no='1'
ORDER BY update_date DESC LIMIT 1;" | grep -q "$PROBE" \
  && echo "DRAFT PERSISTED" || echo "FAIL: probe not in casemgmt_tmpsave"
```

#### Step 24: Exit without save
**Action**:
1. Register a dialog handler to accept the `closeWithoutSaveMsg` confirm:
   `browser_handle_dialog(accept=true)`.
2. Click the Exit control (the element that invokes `closeEnc(e)` — usually the
   encounter window close button or "Exit" link).
3. After the dialog is accepted, navigate back to the patient search / dashboard
   (because `window.close()` has no effect when the tab was not script-opened;
   the test drives the navigation explicitly).

**Screenshot**: `test-6-24-exit-without-save.png`
**Expected**: Tab returned to dashboard/patient search; no network POST with
`method=cancel` was issued (verify via `browser_network_requests` — assert the
request list contains no entry whose URL includes `CaseManagementEntry` AND whose
body contains `method=cancel`).

**DB assertion** (required for this step — the behavioral contract of #1873):
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT note FROM casemgmt_tmpsave
WHERE demographic_no='1'
ORDER BY update_date DESC LIMIT 1;" | grep -q "$PROBE" \
  && echo "PASS: draft survives Exit" \
  || { echo "FAIL: #1873 regression — draft was deleted on Exit"; exit 1; }
```

#### Step 25: Reopen encounter and confirm draft is restored
**Action**:
1. Reopen a new encounter for the same patient (same `IncomingEncounter.do` flow).
2. The server-side flow detects the tmpsave row and prompts with
   `unsavedNoteMsg` — register another dialog handler to accept it.
3. After the restore submits, read the note textarea's value via
   `browser_evaluate` on `document.getElementById('caseNote').value`.

**Screenshot**: `test-6-25-draft-restored.png`
**Expected**: The textarea contains the `$PROBE` token that was typed in step 23.

#### Step 26: Negative control — explicit Cancel still clears the draft
**Purpose**: Prove the explicit Cancel button (not the Exit / `closeEnc` path) still
invokes `method=cancel` → `deleteTmpSave`. This guards against over-correcting and
breaking intentional cancel behavior.

**Action**:
1. In the restored encounter, click the explicit **Cancel** button (the one defined
   at `src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp:462`, which
   sets `frm.method.value='cancel'` and submits the form).
2. Accept any resulting dialog.

**Screenshot**: `test-6-26-cancel-button-clears.png`
**Expected**: DB assertion passes:
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT COUNT(*) AS remaining_drafts
FROM casemgmt_tmpsave
WHERE demographic_no='1' AND note LIKE '%$PROBE%';"
# remaining_drafts should be 0
```

If `remaining_drafts > 0`, the explicit Cancel path is broken — an over-correction
from #1873 / PR #1887 has leaked into the wrong handler.

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-6/screenshots/test-6-*.png | wc -l
# Should show: 26
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

### 2b. Autosave Draft Survival Check (Phase 9)
```bash
# After Phase 9 Step 24, before Step 26, this must hold:
mysql -h db -uroot -ppassword oscar -e "
SELECT COUNT(*) AS draft_rows FROM casemgmt_tmpsave
WHERE demographic_no='1' AND note LIKE '%AUTOSAVE-PROBE-%';"
# draft_rows must be >= 1 between steps 24 and 26.

# After Phase 9 Step 26, this must hold:
# draft_rows for matching $PROBE must be 0 (explicit cancel cleaned up).
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
