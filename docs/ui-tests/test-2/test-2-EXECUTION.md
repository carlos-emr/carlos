# Test 2 Execution Guide

## Pre-Flight Checklist

Before executing Test 2, verify all prerequisites are met.

### 1. Application Status
```bash
# Verify application is running
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/oscar/index.jsp
# Should return: 200

# Check server status
server status
```

### 2. Database Connectivity
```bash
# Verify database connection
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"
```

### 3. Test Data Exists
```bash
# Verify test patients exist
mysql -h db -uroot -ppassword oscar -e "
SELECT demographic_no, last_name, first_name, patient_status
FROM demographic
WHERE demographic_no IN (1, 182);"
```

Expected output:
```
+----------------+--------------+-------------+----------------+
| demographic_no | last_name    | first_name  | patient_status |
+----------------+--------------+-------------+----------------+
|              1 | FAKE-Jones   | FAKE-Jacky  | AC             |
|            182 | FAKE-Gaylord | FAKE-Branda | AC             |
+----------------+--------------+-------------+----------------+
```

### 4. Pre-Test Cleanup (REQUIRED for Re-Runs)
```bash
# Check for existing test patient
mysql -h db -uroot -ppassword oscar -e "
SELECT demographic_no, last_name, first_name
FROM demographic
WHERE last_name = 'TEST-UITEST2';"

# If exists, delete it
mysql -h db -uroot -ppassword oscar -e "
DELETE FROM demographic WHERE last_name = 'TEST-UITEST2';"
```

### 5. Create Test Run Directory
```bash
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-2/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-2"
```

---

## Test Execution

### Phase 1: Authentication & Navigation

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-2-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-2-02-provider-dashboard.png`
**Expected**: Provider dashboard with navigation menu (Schedule, Search, etc.)

#### Step 3: Navigate to Search
**Action**: Click "Search" in navigation menu
**Screenshot**: `test-2-03-search-page.png`
**Expected**: Patient search page with:
- Search dropdown (Name, HIN, DOB, etc.)
- Search input field
- Filter buttons (Active, Inactive, All)
- Results table (may be empty)

---

### Phase 2: Create New Patient

#### Step 4: Open Add Patient Form
**Action**: Access the add patient form via search results:
1. In the search box, type any search term (e.g., "TEST")
2. Press Enter to execute search
3. Wait for search results to load
4. Scroll to bottom of results and click "Create Demographic" link

> **NOTE**: The "Create Demographic" link only appears AFTER performing a search.
> It is located at the bottom of the search results page.

**Screenshot**: `test-2-04-add-patient-form.png`
**Expected**: Empty demographic add form with sections for:
- Name fields
- Date of birth
- Address
- Contact information
- Health Insurance fields

#### Step 5: Fill Required Fields
**Action**: Fill the following fields:
- Last Name: `TEST-UITEST2`
- First Name: `Patient`
- Year of Birth: `1990`
- Month of Birth: `01`
- Date of Birth: `01`
- Sex: `Male` (select from dropdown)

**Screenshot**: `test-2-05-add-patient-filled.png`
**Expected**: Name and DOB fields populated

#### Step 6: Fill Contact Fields
**Action**: Fill the following fields:
- Phone (Home): `416-555-0199`
- Email: `test.uitest2@example.com`

> **IMPORTANT**: Leave the Health Ins. # (HIN) field EMPTY.
> The HIN field has strict validation that rejects invalid formats.
> Leaving it empty is acceptable and avoids validation errors.

**Screenshot**: `test-2-06-add-patient-contact.png`
**Expected**: Contact fields populated, HIN field empty

#### Step 7: Set Provider
**Action**: Select the following:
- Doctor (MRP): `openodoc, doctor` (select from dropdown)

**Screenshot**: `test-2-07-add-patient-provider.png`
**Expected**: Provider field populated

#### Step 8: Save New Patient
**Action**:
1. Click "Add Record" button at bottom of form
2. **WAIT 5 SECONDS** using `browser_wait_for` with `{"time": 5}` to allow form to process
3. **LOG CONSOLE MESSAGES** using `browser_console_messages` - record any errors or warnings

> **IMPORTANT**: Form submissions can be slow. Always wait after clicking submit buttons
> to prevent AbortError timeouts.

**Screenshot**: `test-2-08-add-patient-success.png`
**Expected**: Success message or patient record displayed
- May show the new patient's record
- Note the new demographic_no if displayed

**Console Logging**: Record all messages at this checkpoint for the test report.

**Database Verification** (if timeout occurs):
```bash
mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no, last_name FROM demographic WHERE last_name='TEST-UITEST2' ORDER BY demographic_no DESC LIMIT 1;"
```
If record exists, the save was successful despite any browser timeout.

---

### Phase 3: Verify New Patient

#### Step 9: Search for New Patient
**Action**:
1. Navigate to Search page
2. Ensure "Name" is selected in dropdown
3. Type `TEST-UITEST2` in search field
4. Press Enter

**Screenshot**: `test-2-09-search-new-patient.png`
**Expected**: Search results showing new patient
- Name: TEST-UITEST2, AutomatedTest
- Status: AC (Active)

#### Step 10: View Patient Profile
**Action**: Click on the new patient row to open profile
**Screenshot**: `test-2-10-new-patient-profile.png`
**Expected**: Full demographic record display

#### Step 11: Verify Demographics Section
**Action**: Scroll to demographics section
**Screenshot**: `test-2-11-demographics-section.png`
**Expected**:
- Name: TEST-UITEST2, AutomatedTest
- DOB: 2000-01-15
- Sex: Male
- Age: (calculated from DOB)

#### Step 12: Verify Contact Section
**Action**: Scroll to contact information section
**Screenshot**: `test-2-12-contact-section.png`
**Expected**:
- Phone: 416-555-0001
- Email: test2@openoemr.test
- Address: 123 Test Street, Toronto, ON

---

### Phase 4: Edit Multiple Fields

#### Step 13: Open Edit Form
**Action**: Click "Edit Demographic" button
**Screenshot**: `test-2-13-edit-form-open.png`
**Expected**: Edit form opens with all current values

#### Step 14: Change Phone
**Action**:
1. Find Phone (Home) field
2. Clear current value
3. Enter: `416-555-9999`

**Screenshot**: `test-2-14-edit-phone.png`
**Expected**: Phone field shows new value

#### Step 15: Change Email
**Action**:
1. Find Email field
2. Clear current value
3. Enter: `updated@openoemr.test`

**Screenshot**: `test-2-15-edit-email.png`
**Expected**: Email field shows new value

#### Step 16: Change Address
**Action**:
1. Find Address field
2. Clear current value
3. Enter: `456 Updated Avenue`

**Screenshot**: `test-2-16-edit-address.png`
**Expected**: Address field shows new value

#### Step 17: Change City
**Action**:
1. Find City field
2. Clear current value
3. Enter: `Ottawa`

**Screenshot**: `test-2-17-edit-city.png`
**Expected**: City field shows new value

#### Step 18: Save Changes
**Action**: Click "Update Record" button
**Screenshot**: `test-2-18-update-saved.png`
**Expected**:
- Form submits successfully
- Returns to demographic view
- May show success message

---

### Phase 5: Verify Edits Persisted

#### Step 19: Verify Phone Update
**Action**: View contact section for phone
**Screenshot**: `test-2-19-verify-phone.png`
**Expected**: Phone shows `416-555-9999`

#### Step 20: Verify Email Update
**Action**: View contact section for email
**Screenshot**: `test-2-20-verify-email.png`
**Expected**: Email shows `updated@openoemr.test`

#### Step 21: Verify Address Update
**Action**: View address section
**Screenshot**: `test-2-21-verify-address.png`
**Expected**: Address shows `456 Updated Avenue, Ottawa`

---

### Phase 6: Search Methods & Filters

#### Step 22: Search by HIN
**Action**:
1. Navigate to Search page
2. Change dropdown to "HIN"
3. Enter: `9876543225`
4. Press Enter

**Screenshot**: `test-2-22-search-by-hin.png`
**Expected**: Results show patient ID 1 (FAKE-Jones)

#### Step 23: View Patient 1
**Action**: Click on patient ID 1
**Screenshot**: `test-2-23-patient1-view.png`
**Expected**: Demographic for FAKE-Jacky, FAKE-Jones displays

#### Step 24: Test Inactive Filter
**Action**:
1. Return to Search page
2. Click "Inactive" filter button

**Screenshot**: `test-2-24-filter-inactive.png`
**Expected**: Filter button highlighted, shows only inactive patients

#### Step 25: Test All Filter
**Action**: Click "All" filter button
**Screenshot**: `test-2-25-filter-all.png`
**Expected**: Filter button highlighted, shows all patients

#### Step 26: Test Active Filter
**Action**: Click "Active" filter button
**Screenshot**: `test-2-26-filter-active.png`
**Expected**: Filter button highlighted, returns to active patients only

---

### Phase 7: Healthcare Team & Status

#### Step 27: View Healthcare Team
**Action**:
1. Search for patient 182 (FAKE-Gaylord)
2. Open demographic record
3. Locate Healthcare Team section

**Screenshot**: `test-2-27-healthcare-team.png`
**Expected**: MRP (Most Responsible Provider) visible

#### Step 28: View Clinic Status
**Action**: Locate Clinic Status section
**Screenshot**: `test-2-28-clinic-status.png`
**Expected**:
- Patient Status: AC (Active)
- Roster Status: visible

---

### Phase 8: Cleanup

#### Step 29: Mark Test Patient Inactive
**Action**:
1. Search for `TEST-UITEST2`
2. Click to open profile
3. Click "Edit Demographic"
4. Find Patient Status dropdown
5. Change to `IN` (Inactive)

**Screenshot**: `test-2-29-mark-inactive.png`
**Expected**: Status dropdown shows IN

#### Step 30: Save and Verify Cleanup
**Action**:
1. Click "Update Record"
2. Verify patient now shows as Inactive

**Screenshot**: `test-2-30-final-verification.png`
**Expected**: Patient status displays as Inactive

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-2/screenshots/test-2-*.png | wc -l
# Should show: 30
```

### 2. Database Verification
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT demographic_no, last_name, first_name, patient_status, phone, email, address, city
FROM demographic
WHERE last_name = 'TEST-UITEST2';"
```

Expected:
- patient_status = 'IN' (Inactive)
- phone = '416-555-9999'
- email = 'updated@openoemr.test'
- address = '456 Updated Avenue'
- city = 'Ottawa'

### 3. Console Warnings Check
Expected 404 errors (non-blocking):
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

Expected JavaScript warnings (non-blocking):
- `Unexpected token '%'`

---

## Gold Standard Promotion

After a successful test run, promote screenshots to gold standards:

```bash
# Copy to gold standard location
cp ui-test-runs/$TIMESTAMP/test-2/screenshots/test-2-*.png \
   docs/ui-tests/test-2/screenshots/

# Verify
ls -lh docs/ui-tests/test-2/screenshots/test-2-*.png | wc -l
# Should show: 30
```

---

## Decision Tree

### Test PASSES when:
- All 30 steps complete
- All 30 screenshots captured
- Patient creation successful
- All edits persist correctly
- All search methods work
- Test patient marked Inactive

### Test has ACCEPTABLE DIFFERENCES when:
- Dynamic dates vary (dashboard date)
- Patient ages vary (calculated)
- Minor styling differences

### Test FAILS when:
- Login fails
- Patient creation fails
- Edits don't save
- Search returns unexpected results
- New 404 errors appear
- Layout broken

### Test has REGRESSIONS when:
- Previously working features fail
- New error messages appear
- UI elements missing

---

## Troubleshooting

### Patient Creation Fails
1. Check for existing TEST-UITEST2 (duplicate name)
2. Verify all required fields filled
3. Check JavaScript console for errors

### Edits Don't Persist
1. Verify Update Record button clicked
2. Check for form validation errors
3. Verify database write permissions

### Search Returns No Results
1. Confirm Enter key pressed
2. Verify search mode (Name vs HIN)
3. Check Active/Inactive filter state

### Filter Buttons Don't Work
1. Check for JavaScript errors
2. Try refreshing page
3. Verify filter state in URL parameters
