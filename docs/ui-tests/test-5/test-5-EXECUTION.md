# Test 5 Execution Guide

## Pre-Flight Checklist

Before executing Test 5, verify all prerequisites are met.

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
mkdir -p ui-test-runs/$TIMESTAMP/test-5/{screenshots,reports}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-5"
```

---

## Test Execution

### Phase 1: Authentication & Tickler Access

#### Step 1: Navigate to Login Page
**Action**: Navigate to http://localhost:8080/oscar
**Screenshot**: `test-5-01-login-page.png`
**Expected**: Login form with username, password, and PIN fields

#### Step 2: Login
**Action**: Fill and submit login form
- Username: `openodoc`
- Password: `openo2025`
- PIN: `2025`

**Screenshot**: `test-5-02-provider-dashboard.png`
**Expected**: Provider dashboard with navigation menu

#### Step 3: Open Tickler Dashboard
**Action**: Click "Tickler" in navigation menu
**Screenshot**: `test-5-03-tickler-dashboard.png`
**Expected**: Tickler dashboard with:
- List of existing ticklers (may be empty)
- Filter options (date range, status)
- "Create New" or "Add" button

---

### Phase 2: Create Tickler

#### Step 4: Open New Tickler Form
**Action**: Click "Create New Tickler" or "Add" button
**Screenshot**: `test-5-04-new-tickler-form.png`
**Expected**: Empty tickler form with:
- Message/description field
- Service date field
- Provider assignment dropdown
- Patient search/link option
- Priority selection

#### Step 5: Link to Patient
**Action**:
1. Find patient search field or "Link Patient" button
2. Search for "FAKE-J"
3. Select FAKE-Jacky FAKE-Jones

**Screenshot**: `test-5-05-patient-linked.png`
**Expected**: Patient name displayed and linked to tickler

#### Step 6: Set Tickler Details
**Action**:
1. Enter message: "UI Test 5 - Follow up call needed"
2. Set service date to 7 days from today
3. Assign to provider: openodoc
4. Set priority: Normal

**Screenshot**: `test-5-06-tickler-details.png`
**Expected**: All fields populated with test values

#### Step 7: Save Tickler
**Action**: Click "Save" or "Create" button
**Screenshot**: `test-5-07-tickler-saved.png`
**Expected**:
- Tickler saved confirmation
- Returns to tickler list or detail view
- New tickler visible

---

### Phase 3: Tickler Management

#### Step 8: View Tickler in List
**Action**: Navigate to tickler list if not already there
**Screenshot**: `test-5-08-tickler-list.png`
**Expected**:
- New tickler visible in list
- Shows message, date, patient
- Status shows as "Active" or "Open"

#### Step 9: Update Tickler Status
**Action**:
1. Click on tickler to open or use status dropdown
2. Change status to "Completed" or "Closed"
3. Save if required

**Screenshot**: `test-5-09-status-updated.png`
**Expected**: Status updated and visual indicator changes

---

### Phase 4: Messaging

#### Step 10: Open Messaging Module
**Action**: Click "Msg" or "Messages" in navigation menu
**Screenshot**: `test-5-10-messaging-module.png`
**Expected**: Message module opens with inbox or compose view

#### Step 11: Compose Message
**Action**:
1. Click "Compose" or "New Message"
2. View compose form (don't need to send)

**Screenshot**: `test-5-11-compose-message.png`
**Expected**: Message compose form with:
- To field (provider selection)
- Subject field
- Message body

#### Step 12: View Inbox
**Action**: Click "Inbox" or navigate to received messages
**Screenshot**: `test-5-12-inbox.png`
**Expected**: Inbox view showing messages (may be empty)

---

## Post-Test Verification

### 1. Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-5/screenshots/test-5-*.png | wc -l
# Should show: 12
```

### 2. Database Verification
```bash
# Verify tickler was created
mysql -h db -uroot -ppassword oscar -e "
SELECT tickler_no, message, demographic_no, status
FROM tickler
WHERE message LIKE '%UI Test 5%'
ORDER BY tickler_no DESC LIMIT 1;"
```

### 3. Console Warnings Check
Expected 404 errors (non-blocking):
- `/oscar/js/dateFormatUtils.js`
- `/oscar/js/custom/default/master.js`

---

## Gold Standard Promotion

After a successful test run:

```bash
cp ui-test-runs/$TIMESTAMP/test-5/screenshots/test-5-*.png \
   docs/ui-tests/test-5/screenshots/

ls -lh docs/ui-tests/test-5/screenshots/test-5-*.png | wc -l
# Should show: 12
```

---

## Decision Tree

### Test PASSES when:
- All 12 steps complete
- All 12 screenshots captured
- Tickler created successfully
- Patient linked correctly
- Status update works
- Messaging module accessible

### Test FAILS when:
- Login fails
- Tickler module doesn't load
- Tickler creation fails
- Patient linking fails
- New errors appear

---

## Troubleshooting

### Tickler Won't Save
1. Verify message field not empty
2. Check service date is valid
3. Verify provider is selected
4. Check JavaScript console

### Patient Search Empty
1. Confirm patient exists in database
2. Verify search term correct
3. Press Enter after typing

### Status Won't Update
1. Check tickler is editable
2. Verify status dropdown works
3. Click save if required
