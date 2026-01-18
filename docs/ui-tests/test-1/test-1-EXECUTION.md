# Test 1: Execution Guide

**Quick Reference for executing Test 1 with screenshot comparison**

---

## Pre-Flight Checklist

```bash
# 1. Start application
server start && server log

# 2. Verify database
mysql -h db -uroot -ppassword oscar -e \
  "SELECT user_name, pin FROM security WHERE user_name='openodoc';"

# 3. Create unique test run directory
TIMESTAMP=$(date +%Y%m%d-%H%M%S-%3N)
mkdir -p ui-test-runs/$TIMESTAMP/test-1/{screenshots,reports,comparison}
echo "Test run directory: ui-test-runs/$TIMESTAMP/test-1/"

# 4. Note git state
git status  # Should be clean for baseline testing
git log -1 --oneline  # Note current commit
```

---

## Test Execution

### Step 1: Login Page
```
Navigate: http://localhost:8080/oscar/index.jsp
Screenshot: ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-01-login-page.png
Verify: Form visible, fonts correct, no 404s
```

### Step 2: Dashboard
```
Action: Login (openodoc / openo2025 / 2025)
Screenshot: ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-02-provider-dashboard.png
Verify: Navigation menu (13 items), schedule visible, NO 404s
```

### Step 3: Search Page
```
Action: Click "Search" menu item
Screenshot: ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-03-patient-search.png
Verify: Search form, filters, 404: dateFormatUtils.js (expected)
```

### Step 4: Search "FAKE-G"
```
Action: Type "FAKE-G" then PRESS ENTER to submit (form does not auto-submit)
Screenshot: ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-04-search-results-patient1.png
Verify: 10 patients, patient ID 182 visible
```

### Step 5: Patient #182
```
Action: Click patient ID 182
Screenshot: ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-05-demographic-patient1.png
Verify: FAKE-Gaylord FAKE-Branda, all sections, 404: master.js (expected)
```

### Step 6: Search "FAKE-J"
```
Action: Type "FAKE-J" in search box then PRESS ENTER to submit (form does not auto-submit)
Screenshot: ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-06-search-results-patient2.png
Verify: 10 patients, patient ID 1 visible
```

### Step 7: Patient #1
```
Action: Click patient ID 1
Screenshot: ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-07-demographic-patient2.png
Verify: FAKE-Jacky FAKE-Jones, HEART team, all sections
```

---

## Post-Test Verification

### 1. Check Screenshot Count
```bash
ls -1 ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-*.png | wc -l
# Expected: 7
```

### 2. Compare Against Gold Standards
```bash
cd ui-test-runs/$TIMESTAMP/test-1/screenshots

# Quick visual check
ls -lh test-1-*.png

# Compare each with gold standard
for f in test-1-*.png; do
  echo "=== $f ==="
  echo "New: $(stat -f%z $f 2>/dev/null || stat -c%s $f) bytes"
  echo "Gold: $(stat -f%z /workspace/docs/ui-tests/test-1/screenshots/$f 2>/dev/null || \
               stat -c%s /workspace/docs/ui-tests/test-1/screenshots/$f) bytes"
  echo ""
done
```

### 3. Verify Expected Warnings

**Expected 404s**:
- `/oscar/js/dateFormatUtils.js` (Step 3)
- `/oscar/js/custom/default/master.js` (Steps 5, 7)

**Expected JavaScript Warnings**:
- `Unexpected token '%'` (Steps 1, 4, 6)
- `TypeError: Cannot read properties of undefined (reading 'username')` (Step 1)

**If you see NEW 404s or warnings → INVESTIGATE**

### 4. Document Results

Create test report at `ui-test-runs/$TIMESTAMP/test-1/reports/test-1-results-$TIMESTAMP.md`:

```markdown
# Test 1 Results - [DATE]

## Environment
- Commit: [git commit hash]
- Branch: [branch name]
- Time: [execution time]

## Results
- Step 1: ✅ / ❌
- Step 2: ✅ / ❌
- Step 3: ✅ / ❌
- Step 4: ✅ / ❌
- Step 5: ✅ / ❌
- Step 6: ✅ / ❌
- Step 7: ✅ / ❌

## Screenshot Comparison
- test-1-01: ✅ Matches / ⚠️ Acceptable difference / ❌ Regression
- test-1-02: ...
- [Continue for all 7]

## New Issues
- [List any NEW 404s]
- [List any NEW JavaScript errors]
- [List any layout breakage]

## Differences from Gold Standard
- [Describe any visual differences]
- [Note if acceptable or regression]

## Conclusion
✅ PASS / ❌ FAIL
[Summary of findings]
```

---

## Decision Tree: What to Do Next

### All Screenshots Match Gold Standard
✅ **Test Passes** - Document as passing, commit results

### Screenshots Different (Date/Dynamic Content)
⚠️ **Acceptable** - Document differences, optionally update gold standard

### Screenshots Different (Layout/Functionality)
❌ **Regression** - Bug found, do not update gold standard
1. Document the regression
2. Create bug report with screenshots
3. Fix the issue
4. Rerun test to verify fix

### New 404 Errors Found
❌ **Regression** - Missing resources
1. Document the missing files
2. Check if files were deleted in recent commits
3. Restore files or fix references
4. Rerun test

### New JavaScript Errors Found
⚠️ **Investigate** - May or may not be blocking
1. Document error with file:line
2. Test if functionality still works
3. If blocking → Fix and rerun
4. If non-blocking → Document as known issue

---

## Updating Gold Standards

**IMPORTANT**: Updating gold standards is a **separate, explicit action** that should NEVER be part of the normal testing process.

### When to Update (Separate Task, Not During Testing)

Update gold standards **only when**:
- Intentional UI improvements have been implemented
- Test data has been deliberately changed
- New features have been added to the interface
- Changes have been reviewed and approved by the team

### How to Update (After Testing is Complete)

```bash
# IMPORTANT: This is a SEPARATE task from testing
# Do NOT do this as part of the normal test run

# 1. Backup old gold standards
cd /workspace/docs/ui-tests/test-1/screenshots
mkdir -p archive/$(date +%Y%m%d)
cp test-1-*.png archive/$(date +%Y%m%d)/

# 2. Copy new screenshots to gold standard location
# Use the specific test run you want to promote
TIMESTAMP=20260116-113900-123  # Replace with actual timestamp (includes milliseconds)
cp /workspace/ui-test-runs/$TIMESTAMP/test-1/screenshots/test-1-*.png \
   /workspace/docs/ui-tests/test-1/screenshots/

# 3. Verify file sizes reasonable
ls -lh test-1-*.png

# 4. Commit with detailed explanation
git add docs/ui-tests/test-1/screenshots/test-1-*.png
git commit -m "Update Test 1 gold standard screenshots

Reason: [UI improvement / test data update / date change]
Test Run: $TIMESTAMP
Changes:
- test-1-01: [describe change]
- test-1-02: [describe change]
- ...

Verified: All 7 steps pass, functionality intact
Approved by: [team member]
"
```

---

## Quick Troubleshooting

### Login Fails
```bash
# Reset password
mysql -h db -uroot -ppassword oscar -e \
  "UPDATE security SET
   password='{bcrypt}\$2b\$12\$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96',
   forcePasswordReset=0
   WHERE user_name='openodoc';"
```

### Search Returns No Results
```bash
# Verify patient data
mysql -h db -uroot -ppassword oscar -e \
  "SELECT demographic_no, last_name, first_name, patient_status
   FROM demographic
   WHERE demographic_no IN (1, 182);"
```

### Screenshots Not Saving
```bash
# Check directory exists and is writable
ls -ld ui-test-runs/$TIMESTAMP/test-1/screenshots
# Should show: drwxr-xr-x ... ui-test-runs/$TIMESTAMP/test-1/screenshots

# Recreate if needed
mkdir -p ui-test-runs/$TIMESTAMP/test-1/screenshots
```

### Application Not Responding
```bash
# Check application status
server log

# Restart if needed
server restart

# Wait for startup
sleep 10

# Verify ready
curl -I http://localhost:8080/oscar/index.jsp
```

---

## Time Estimate

- **Setup**: 2 minutes
- **Execution**: 2 minutes
- **Verification**: 3 minutes
- **Documentation**: 3 minutes
- **Total**: ~10 minutes per test run

---

## Success Criteria

✅ **Test Passes When**:
- All 7 steps complete without errors
- Screenshots match gold standards (or have acceptable differences)
- Only expected 404s and warnings present
- All functionality works correctly

❌ **Test Fails When**:
- Login fails
- Navigation broken
- Search doesn't work
- Patient records don't display
- New 404 errors or JavaScript errors appear
- Layout significantly broken

---

*Test 1 Execution Guide v1.0*
*For detailed process, see: UI-TEST-PROCESS.md*
