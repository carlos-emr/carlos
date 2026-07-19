# Test 4 Execution Guide

## Pre-Flight Checklist

Before executing Test 4, verify all prerequisites are met.

### 1. Application Status
```bash
# Verify application is running
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/oscar/index.jsp
# Should return: 200
```

### 2. Database Connectivity
```bash
# Verify database connection
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"
```

### 3. Test Patient Exists
```bash
# Verify test patient exists
mysql -h db -uroot -ppassword oscar -e "
SELECT demographic_no, last_name, first_name
FROM demographic WHERE demographic_no = 1;"
```

### 4. Drug Database Available
```bash
# Verify drug database has entries
mysql -h db -uroot -ppassword oscar -e "
SELECT COUNT(*) as drug_count FROM drugs LIMIT 1;"
```

### 5. Create Test Run Directory
```bash
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-4/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-4"
```

---

## Test Execution

### Phase 1: Authentication & Patient Access

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-4-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-4-02-provider-dashboard.png`
**Expected**: Provider dashboard with navigation menu

#### Step 3: Search for Test Patient
**Action**:
1. Click "Search" in navigation
2. Type "FAKE-J" in search field
3. Press Enter

**Screenshot**: `test-4-03-patient-search.png`
**Expected**: Search results showing FAKE-Jones patient

#### Step 4: Open Patient Chart
**Action**: Click on patient to open chart/demographic view
**Screenshot**: `test-4-04-patient-chart.png`
**Expected**: Patient chart or demographic view with action links

---

### Phase 2: Prescription Module Access

#### Step 5: Open Rx Module
**Action**: Click "Rx" or "Prescriptions" link in patient chart
**Screenshot**: `test-4-05-rx-module.png`
**Expected**: Prescription module opens with:
- Patient info header
- Current medications section
- Allergies section
- Action buttons

#### Step 6: View Current Medications
**Action**: View the medications list (may be empty for test patient)
**Screenshot**: `test-4-06-current-meds.png`
**Expected**: Medications list displayed (may show "No medications" if empty)

---

### Phase 3: Allergy Management

#### Step 7: Add Allergy
**Action**:
1. Find "Add Allergy" or "+" button in allergies section
2. Click to open allergy form
3. Fill allergy details:
   - Allergen: `Penicillin`
   - Type: `Drug` (if dropdown available)
   - Reaction: `Hives`
   - Severity: `Moderate` (if dropdown available)

**Screenshot**: `test-4-07-add-allergy.png`
**Expected**: Allergy form with fields filled

#### Step 8: Save Allergy
**Action**: Click "Save" or "Add" button
**Screenshot**: `test-4-08-allergy-saved.png`
**Expected**:
- Allergy appears in allergies list
- May show confirmation message
- Penicillin visible in allergies panel

---

### Phase 4: Create Prescription

#### Step 9: Open New Prescription Form
**Action**: Click "New Prescription" or "Add Rx" button
**Screenshot**: `test-4-09-new-rx-form.png`
**Expected**: Empty prescription form with:
- Drug search field
- Dosage fields
- Quantity/refills fields

#### Step 10: Search Drug Database
**Action**:
1. In drug search field, type "Acetaminophen"
2. Press Enter or click Search

**Screenshot**: `test-4-10-drug-search.png`
**Expected**: Search results showing Acetaminophen options:
- Multiple strengths/forms available
- 500mg tablet should be listed

#### Step 11: Select Drug and Dosage
**Action**:
1. Select "Acetaminophen 500mg Tablet" from results
2. Enter dosage instructions: "Take 1 tablet by mouth every 6 hours as needed for pain"

**Screenshot**: `test-4-11-drug-selected.png`
**Expected**:
- Drug name populated
- Dosage field has instructions

#### Step 12: Set Quantity and Refills
**Action**:
1. Set Quantity: `30`
2. Set Refills: `0`
3. Duration: `10 days` (if field available)

**Screenshot**: `test-4-12-quantity-set.png`
**Expected**: Quantity and refills fields populated

#### Step 13: Check for Interactions
**Action**:
1. If interaction check button exists, click it
2. Or observe automatic interaction check results

**Screenshot**: `test-4-13-interaction-check.png`
**Expected**:
- Interaction check runs (may show no interactions)
- May show warning if Penicillin allergy affects this drug (unlikely)

#### Step 14: Save Prescription
**Action**: Click "Save" or "Add to Rx" button
**Screenshot**: `test-4-14-rx-saved.png`
**Expected**:
- Prescription saved confirmation
- Appears in medications list
- May return to Rx module main view

---

### Phase 5: Prescription Management

#### Step 15: View Prescription History
**Action**: Navigate to prescription history or view saved Rx list
**Screenshot**: `test-4-15-rx-history.png`
**Expected**:
- List of prescriptions (including new one)
- Date and drug info visible
- Acetaminophen entry shown

#### Step 16: View Prescription Details
**Action**: Click on the Acetaminophen prescription to view details
**Screenshot**: `test-4-16-rx-details.png`
**Expected**:
- Full prescription details displayed
- Dosage instructions visible
- Quantity and refills shown

#### Step 17: Re-Prescribe (if available)
**Action**:
1. Click "Re-prescribe" or "Refill" button
2. Confirm re-prescription (don't actually save to avoid duplicates)

**Screenshot**: `test-4-17-re-prescribe.png`
**Expected**: Re-prescription form pre-filled with previous values

#### Step 18: Preview Print
**Action**: Click "Print" or "Print Preview" button
**Screenshot**: `test-4-18-print-preview.png`
**Expected**:
- Print preview dialog or new window
- Formatted prescription visible
- Patient and provider info shown

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-4/screenshots/test-4-*.png | wc -l
# Should show: 18
```

### 2. Database Verification
```bash
# Verify allergy was added
mysql -h db -uroot -ppassword oscar -e "
SELECT id, description, reaction
FROM allergies
WHERE demographic_no = 1
ORDER BY id DESC LIMIT 1;"

# Verify prescription was created
mysql -h db -uroot -ppassword oscar -e "
SELECT drug_id, BN, special
FROM drugs
WHERE demographic_no = 1
ORDER BY drug_id DESC LIMIT 1;"
```

### 3. Console Warnings Check
Expected 404 errors (non-blocking):
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

---

## Gold Standard Promotion

After a successful test run:

```bash
# Copy to gold standard location
cp ui-test-runs/$TIMESTAMP/test-4/screenshots/test-4-*.png \
   docs/ui-tests/test-4/screenshots/

# Verify count
ls -lh docs/ui-tests/test-4/screenshots/test-4-*.png | wc -l
# Should show: 18
```

---

## Decision Tree

### Test PASSES when:
- All 18 steps complete
- All 18 screenshots captured
- Allergy added successfully
- Prescription created successfully
- Drug search works
- Print preview displays

### Test FAILS when:
- Login fails
- Rx module doesn't load
- Drug search empty
- Prescription won't save
- Allergies not working
- New errors appear

---

## Troubleshooting

### Drug Search Returns Empty
1. Verify drug database populated
2. Check search term spelling
3. Try generic drug names
4. Check JavaScript console

### Prescription Save Fails
1. Verify all required fields filled
2. Check for validation messages
3. Ensure drug is selected (not just typed)
4. Check provider Rx privileges

### Allergy Section Missing
1. Check if patient has allergies panel visible
2. Refresh the Rx module page
3. Try accessing via E-Chart allergies
