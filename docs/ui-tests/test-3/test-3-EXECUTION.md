# Test 3 Execution Guide

## Pre-Flight Checklist

Before executing Test 3, verify all prerequisites are met.

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
# Verify test patient exists
mysql -h db -uroot -ppassword oscar -e "
SELECT demographic_no, last_name, first_name, patient_status
FROM demographic
WHERE demographic_no = 1;"
```

Expected output:
```
+----------------+------------+------------+----------------+
| demographic_no | last_name  | first_name | patient_status |
+----------------+------------+------------+----------------+
|              1 | FAKE-Jones | FAKE-Jacky | AC             |
+----------------+------------+------------+----------------+
```

### 4. Create Test Run Directory
```bash
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-3/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-3"
```

---

## Test Execution

### Phase 1: Authentication & Schedule Access

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-3-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-3-02-provider-dashboard.png`
**Expected**: Provider dashboard with schedule visible

#### Step 3: View Day Schedule
**Action**: View the provider schedule (should be visible on dashboard)
**Screenshot**: `test-3-03-schedule-day-view.png`
**Expected**:
- Day view of appointment schedule
- Time slots visible
- Provider name shown
- Current date displayed

---

### Phase 2: Schedule Navigation

#### Step 4: Navigate to Next Week
**Action**: Click "Next Week" or forward navigation arrow
**Screenshot**: `test-3-04-schedule-next-week.png`
**Expected**:
- Calendar advances one week
- Date range updates
- Schedule for next week shown

#### Step 5: Return to Today
**Action**: Click "Today" button or current date link
**Screenshot**: `test-3-05-schedule-today.png`
**Expected**:
- Returns to current day
- Today's date highlighted
- Current appointments shown

---

### Phase 3: Create Appointment

#### Step 6: Open Appointment Form
**Action**: Click on an available time slot (e.g., 10:00 AM)
**Screenshot**: `test-3-06-create-appointment-form.png`
**Expected**:
- Appointment creation form opens
- Time slot pre-filled
- Patient search available

#### Step 7: Select Patient
**Action**:
1. In patient search field, type "FAKE-J"
2. Press Enter to search
3. Select patient FAKE-Jacky FAKE-Jones (ID: 1)

**Screenshot**: `test-3-07-patient-selected.png`
**Expected**:
- Patient name appears in appointment form
- Patient demographic number linked

#### Step 8: Set Appointment Details
**Action**:
1. Select appointment type from dropdown (e.g., "Office Visit")
2. Enter reason: "UI Test Appointment"
3. Leave duration as default (15 min)
4. Add note: "Created by UI Test 3"

**Screenshot**: `test-3-08-appointment-details.png`
**Expected**:
- Appointment type selected
- Reason visible
- Notes field populated

#### Step 9: Save Appointment
**Action**: Click "Save" or "Add Appointment" button
**Screenshot**: `test-3-09-appointment-saved.png`
**Expected**:
- Appointment appears on schedule
- Patient name visible in time slot
- Color coding matches appointment type

---

### Phase 4: Appointment Management

#### Step 10: Open Appointment for Editing
**Action**: Click on the newly created appointment
**Screenshot**: `test-3-10-edit-appointment.png`
**Expected**:
- Appointment edit dialog opens
- Current details displayed
- Status dropdown available

#### Step 11: Change Appointment Status
**Action**:
1. Find status dropdown
2. Change status (e.g., from blank to "Here" or "Confirmed")
3. Save changes

**Screenshot**: `test-3-11-status-changed.png`
**Expected**:
- Status updated
- Visual indicator changes (color or icon)
- Save confirmed

#### Step 12: View Appointment History
**Action**:
1. Click on patient name or "History" link
2. View patient's appointment history

**Screenshot**: `test-3-12-appointment-history.png`
**Expected**:
- List of patient's appointments
- New appointment included
- Dates and types shown

---

### Phase 5: Schedule Views

#### Step 13: Switch to Month View
**Action**: Click "Month" view tab or button
**Screenshot**: `test-3-13-month-view.png`
**Expected**:
- Full month calendar displayed
- Appointment counts per day visible
- Current day highlighted

#### Step 14: Return to Day View
**Action**: Click "Day" view tab or click on today's date
**Screenshot**: `test-3-14-day-view-restored.png`
**Expected**:
- Day view restored
- Time slots visible
- Test appointment still shown

---

### Phase 6: Cleanup

#### Step 15: Delete Test Appointment
**Action**:
1. Click on test appointment
2. Click "Delete" or "Cancel Appointment"
3. Confirm deletion

**Screenshot**: `test-3-15-appointment-deleted.png`
**Expected**:
- Appointment removed from schedule
- Time slot shows as available
- No errors displayed

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-3/screenshots/test-3-*.png | wc -l
# Should show: 15
```

### 2. Database Verification (Optional)
```bash
# Check no orphan test appointments remain
mysql -h db -uroot -ppassword oscar -e "
SELECT appointment_no, appointment_date, reason
FROM appointment
WHERE reason LIKE '%UI Test%'
ORDER BY appointment_date DESC
LIMIT 5;"
```

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
cp ui-test-runs/$TIMESTAMP/test-3/screenshots/test-3-*.png \
   docs/ui-tests/test-3/screenshots/

# Verify
ls -lh docs/ui-tests/test-3/screenshots/test-3-*.png | wc -l
# Should show: 15
```

---

## Decision Tree

### Test PASSES when:
- All 15 steps complete
- All 15 screenshots captured
- Appointment created successfully
- Status change persists
- All views work
- Test appointment deleted

### Test has ACCEPTABLE DIFFERENCES when:
- Dynamic dates vary
- Appointment counts vary
- Minor styling differences

### Test FAILS when:
- Login fails
- Schedule doesn't load
- Appointment creation fails
- Status changes don't save
- Schedule views broken
- New 404 errors appear

### Test has REGRESSIONS when:
- Previously working features fail
- New error messages appear
- UI elements missing

---

## Troubleshooting

### Schedule Page Blank
1. Check if provider has active schedule
2. Verify providerNo in URL matches logged-in provider
3. Check JavaScript console for errors

### Appointment Won't Save
1. Check required fields are filled
2. Verify patient is selected
3. Check for validation error messages

### Patient Search Returns Nothing
1. Confirm Enter key pressed after search term
2. Verify patient exists in database
3. Check search is using correct field (name vs HIN)

### Status Change Doesn't Persist
1. Verify save button clicked
2. Check for JavaScript errors
3. Refresh page and verify

### Month View Won't Load
1. Check for JavaScript errors
2. Verify calendar JavaScript loaded
3. Try different browser/clear cache
