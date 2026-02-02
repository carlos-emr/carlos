# Test 8 Execution Guide

## Pre-Flight Checklist

Before executing Test 8, verify all prerequisites are met.

### 1. Application Status
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/oscar/index.jsp
# Should return: 200
```

### 2. Database Connectivity
```bash
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"
```

### 3. Check for Lab Data (Optional)
```bash
# Check if any lab results exist
mysql -h db -uroot -ppassword oscar -e "
SELECT COUNT(*) as lab_count FROM hl7TextMessage LIMIT 1;"
```

### 4. Create Test Run Directory
```bash
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-8/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-8"
```

---

## Test Execution

### Phase 1: Authentication & Inbox Access

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-8-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-8-02-provider-dashboard.png`
**Expected**: Provider dashboard with navigation menu

#### Step 3: Click Inbox Menu
**Action**: Click "Inbox" in navigation menu
**Screenshot**: `test-8-03-inbox-menu.png`
**Expected**: Inbox page loading or transition

#### Step 4: View Lab Results Section
**Action**: Navigate to lab results within inbox (may be tab or section)
**Screenshot**: `test-8-04-lab-inbox.png`
**Expected**: Lab inbox/results section with:
- List of lab results (may be empty)
- Filter options
- Status indicators

---

### Phase 2: Lab Result Viewing

#### Step 5: Open Lab Result Detail
**Action**: Click on a lab result to open details (if available)
- If no lab results exist, screenshot the empty state
**Screenshot**: `test-8-05-lab-detail.png`
**Expected**: Lab result detail view OR empty state message

#### Step 6: View Patient Context
**Action**: View patient information associated with lab
**Screenshot**: `test-8-06-patient-context.png`
**Expected**: Patient demographics linked to lab result

#### Step 7: View Lab Values
**Action**: View actual lab test values and results
**Screenshot**: `test-8-07-lab-values.png`
**Expected**: Lab values displayed with:
- Test names
- Results
- Reference ranges (if available)

---

### Phase 3: Lab Management

#### Step 8: Forward Lab Result
**Action**: Click "Forward" or share button
**Screenshot**: `test-8-08-forward-lab.png`
**Expected**: Forward dialog with provider selection

#### Step 9: View Lab History
**Action**: Navigate to patient's lab history
**Screenshot**: `test-8-09-lab-history.png`
**Expected**: List of historical lab results for patient

#### Step 10: Print Lab Result
**Action**: Click "Print" button
**Screenshot**: `test-8-10-print-preview.png`
**Expected**: Print preview of lab result

---

### Phase 4: Lab Status

#### Step 11: Mark Lab as Reviewed
**Action**: Click "Mark Reviewed" or similar acknowledgment button
**Screenshot**: `test-8-11-mark-reviewed.png`
**Expected**: Review action initiated

#### Step 12: Verify Status Change
**Action**: Confirm lab shows as reviewed
**Screenshot**: `test-8-12-status-confirmed.png`
**Expected**: Lab status indicates reviewed

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-8/screenshots/test-8-*.png | wc -l
# Should show: 12
```

### 2. Console Warnings Check
Expected 404 errors (non-blocking):
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

---

## Handling Empty Lab Inbox

If no lab results exist in the test database:

1. **Still capture all screenshots** showing empty states
2. **Focus on navigation** - verify all links and tabs work
3. **Document in report** that test data was not available
4. **Test passes** if navigation works even with empty data

---

## Gold Standard Promotion

After a successful test run:

```bash
cp ui-test-runs/$TIMESTAMP/test-8/screenshots/test-8-*.png \
   docs/ui-tests/test-8/screenshots/

ls -lh docs/ui-tests/test-8/screenshots/test-8-*.png | wc -l
# Should show: 12
```

---

## Decision Tree

### Test PASSES when:
- All 12 steps complete
- All 12 screenshots captured
- Lab inbox accessible
- Navigation works
- Print preview available

### Test has ACCEPTABLE differences when:
- Lab inbox is empty (expected for test data)
- Lab forward shows no recipients

### Test FAILS when:
- Login fails
- Inbox won't load
- Lab module broken
- New errors appear

---

## Troubleshooting

### Inbox Not Loading
1. Check JavaScript console
2. Verify user has inbox permissions
3. Try refreshing page

### Lab Module Not Found
1. Check navigation menu
2. May be under different label
3. Check URL patterns

### No Lab Results
1. This is expected for test database
2. Test navigation functionality instead
3. Document empty state in report
