# Test 9 Execution Guide

## Pre-Flight Checklist

Before executing Test 9, verify all prerequisites are met.

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

### 4. Prevention Types Available
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT COUNT(*) as prevention_types FROM preventionsExt LIMIT 1;"
```

### 5. Create Test Run Directory
```bash
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-9/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-9"
```

---

## Test Execution

### Phase 1: Authentication & Prevention Access

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-9-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-9-02-provider-dashboard.png`
**Expected**: Provider dashboard with navigation menu

#### Step 3: Search for Test Patient
**Action**:
1. Click "Search" in navigation
2. Type "FAKE-J" in search field
3. Press Enter

**Screenshot**: `test-9-03-patient-search.png`
**Expected**: Search results showing FAKE-Jones patient

#### Step 4: Open Patient Chart
**Action**: Click on patient to open chart/E-Chart
**Screenshot**: `test-9-04-patient-chart.png`
**Expected**: Patient chart or E-Chart opens

---

### Phase 2: Prevention Records

#### Step 5: Click Preventions Link
**Action**: Find and click "Preventions" or "Immunizations" link
- May be in E-Chart left panel
- May be in patient demographic actions

**Screenshot**: `test-9-05-preventions-link.png`
**Expected**: Prevention module opening/loading

#### Step 6: View Prevention Record
**Action**: View patient's prevention/immunization record
**Screenshot**: `test-9-06-prevention-record.png`
**Expected**: Prevention record with:
- Immunization history (may be empty)
- Recommended preventions
- Add immunization option

---

### Phase 3: Add Immunization

#### Step 7: Open Add Immunization Form
**Action**: Click "Add" or "+" to add new immunization
**Screenshot**: `test-9-07-add-immunization.png`
**Expected**: Immunization entry form with:
- Vaccine type selection
- Date fields
- Administration details

#### Step 8: Select Vaccine Type
**Action**: Select vaccine from dropdown (e.g., "Influenza" or first available)
**Screenshot**: `test-9-08-vaccine-type.png`
**Expected**: Vaccine type selected

#### Step 9: Set Date Administered
**Action**: Set administration date to today
**Screenshot**: `test-9-09-date-set.png`
**Expected**: Date populated with today's date

#### Step 10: Save Immunization
**Action**: Click "Save" or "Add" button
**Screenshot**: `test-9-10-immunization-saved.png`
**Expected**: Immunization saved confirmation

---

### Phase 4: History & Reports

#### Step 11: View Immunization History
**Action**: Navigate to or view immunization history
**Screenshot**: `test-9-11-immunization-history.png`
**Expected**: History list showing new immunization

#### Step 12: Print Prevention Report
**Action**: Click "Print" or "Report" button
**Screenshot**: `test-9-12-prevention-report.png`
**Expected**: Prevention report print preview

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-9/screenshots/test-9-*.png | wc -l
# Should show: 12
```

### 2. Database Verification
```bash
# Verify immunization was added
mysql -h db -uroot -ppassword oscar -e "
SELECT id, demographic_no, prevention_type, prevention_date
FROM preventions
WHERE demographic_no = 1
ORDER BY id DESC LIMIT 1;"
```

### 3. Console Warnings Check
Expected 404 errors (non-blocking):
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

---

## Gold Standard Promotion

After a successful test run:

```bash
cp ui-test-runs/$TIMESTAMP/test-9/screenshots/test-9-*.png \
   docs/ui-tests/test-9/screenshots/

ls -lh docs/ui-tests/test-9/screenshots/test-9-*.png | wc -l
# Should show: 12
```

---

## Decision Tree

### Test PASSES when:
- All 12 steps complete
- All 12 screenshots captured
- Prevention module accessible
- Immunization added
- History displays
- Report preview works

### Test FAILS when:
- Login fails
- Prevention module not found
- Immunization won't save
- New errors appear

---

## Troubleshooting

### Prevention Link Not Found
1. Check E-Chart left panel
2. Look for "Immunizations" label
3. Check patient demographic page

### Vaccine Types Empty
1. Verify preventionsExt table has data
2. Check prevention configuration
3. Try selecting different category

### Immunization Save Fails
1. Check all required fields
2. Verify date format
3. Check JavaScript console
4. Verify provider has privileges
