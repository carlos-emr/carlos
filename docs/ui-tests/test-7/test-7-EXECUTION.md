# Test 7 Execution Guide

## Pre-Flight Checklist

Before executing Test 7, verify all prerequisites are met.

### 1. Application Status
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/oscar/index.jsp
# Should return: 200
```

### 2. Database Connectivity
```bash
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"
```

### 3. Test Patient Exists with HIN
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT demographic_no, last_name, first_name, hin
FROM demographic WHERE demographic_no = 1;"
```

### 4. Ontario Billing Codes Available
```bash
mysql -h db -uroot -ppassword oscar -e "
SELECT COUNT(*) as code_count FROM billingservice WHERE region = 'ON' LIMIT 1;"
```

### 5. Create Test Run Directory
```bash
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-7/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-7"
```

---

## Test Execution

### Phase 1: Authentication & Billing Access

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-7-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-7-02-provider-dashboard.png`
**Expected**: Provider dashboard with navigation menu

#### Step 3: Search for Test Patient
**Action**:
1. Click "Search" in navigation
2. Type "FAKE-J" in search field
3. Press Enter

**Screenshot**: `test-7-03-patient-search.png`
**Expected**: Search results showing FAKE-Jones patient

#### Step 4: Open Billing Module
**Action**: Click "Billing" link for patient or navigate to billing from patient record
**Screenshot**: `test-7-04-billing-module.png`
**Expected**: Billing module opens with:
- Patient info header
- Billing options/actions
- May show existing billing history

---

### Phase 2: Create Billing Entry

#### Step 5: Click Create Billing
**Action**: Click "Create Billing" or "New Billing" button
**Screenshot**: `test-7-05-create-billing.png`
**Expected**: Empty billing form with:
- Service code field
- Diagnostic code field
- Date field
- Provider field

#### Step 6: Select Service Code
**Action**:
1. Find service code field or search
2. Search for "A003" or similar consultation code
3. Select the service code

**Screenshot**: `test-7-06-service-code.png`
**Expected**: Service code selected and fee displayed

#### Step 7: Add Diagnostic Code
**Action**:
1. Find diagnostic code field
2. Search for "J06" or "upper respiratory"
3. Select ICD code J06.9

**Screenshot**: `test-7-07-diagnostic-code.png`
**Expected**: Diagnostic code added to billing entry

#### Step 8: Set Service Date
**Action**: Set service date to today (or confirm default)
**Screenshot**: `test-7-08-service-date.png`
**Expected**: Service date set to current date

#### Step 9: Submit Billing
**Action**: Click "Submit" or "Save" button
**Screenshot**: `test-7-09-billing-submitted.png`
**Expected**: Billing submitted successfully

---

### Phase 3: Billing Management

#### Step 10: View Billing Confirmation
**Action**: View confirmation message/page
**Screenshot**: `test-7-10-billing-confirmation.png`
**Expected**: Confirmation of billing entry with:
- Billing number
- Service details
- Amount

#### Step 11: View Billing History
**Action**: Navigate to billing history for patient
**Screenshot**: `test-7-11-billing-history.png`
**Expected**: List of billing entries including new entry

#### Step 12: View Billing Details
**Action**: Click on billing entry to view details
**Screenshot**: `test-7-12-billing-details.png`
**Expected**: Full billing entry details displayed

#### Step 13: Generate Billing Report
**Action**: Click "Report" or navigate to billing reports
**Screenshot**: `test-7-13-billing-report.png`
**Expected**: Billing report preview or options

---

### Phase 4: Additional Options

#### Step 14: View Third Party Billing
**Action**: Navigate to 3rd party billing options (if available)
**Screenshot**: `test-7-14-third-party.png`
**Expected**: Third party billing options displayed

#### Step 15: Print Billing Statement
**Action**: Click "Print" or "Statement" button
**Screenshot**: `test-7-15-billing-statement.png`
**Expected**: Billing statement print preview

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-7/screenshots/test-7-*.png | wc -l
# Should show: 15
```

### 2. Database Verification
```bash
# Verify billing entry was created
mysql -h db -uroot -ppassword oscar -e "
SELECT billing_no, demographic_no, billing_date, total
FROM billing
WHERE demographic_no = 1
ORDER BY billing_no DESC LIMIT 1;"
```

### 3. Console Warnings Check
Expected 404 errors (non-blocking):
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

---

## Gold Standard Promotion

After a successful test run:

```bash
cp ui-test-runs/$TIMESTAMP/test-7/screenshots/test-7-*.png \
   docs/ui-tests/test-7/screenshots/

ls -lh docs/ui-tests/test-7/screenshots/test-7-*.png | wc -l
# Should show: 15
```

---

## Decision Tree

### Test PASSES when:
- All 15 steps complete
- All 15 screenshots captured
- Billing entry created
- Service code selected
- Diagnostic code added
- History viewable

### Test FAILS when:
- Login fails
- Billing module missing
- Service codes empty
- Submission fails
- New errors appear

---

## Troubleshooting

### Billing Module Not Found
1. Check URL path for billing
2. Verify billing configured for Ontario
3. Check provider privileges

### Service Codes Missing
1. Verify billingservice table has ON codes
2. Check billing configuration
3. Try different code format

### Billing Submission Error
1. Check all required fields
2. Verify patient HIN valid
3. Check date format
4. Verify provider billing number
