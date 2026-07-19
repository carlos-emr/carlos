# Test 2 Fixes Applied

## Issues Discovered and Resolved (2026-01-19)

### 1. "Create Demographic" Link Discovery
**Problem**: The "Create New" or "Create Demographic" link was not visible on the initial search page.

**Root Cause**: The link only appears AFTER performing a search and displaying results.

**Solution (APPLIED)**:
1. From the search page, type any search term (e.g., "TEST") and press Enter
2. Wait for search results to load
3. The "Create Demographic" link appears at the bottom of the results
4. Click the link to open the add patient form

**Status**: RESOLVED - Updated in test2.md and test-2-EXECUTION.md

### 2. HIN Validation Strict
**Problem**: The Health Insurance Number (HIN) field has strict validation that rejects invalid formats, causing JavaScript alerts.

**Solution (APPLIED)**: Leave the HIN field EMPTY when creating test patients. Empty HIN is acceptable.

**Status**: RESOLVED - Updated in test2.md and test-2-EXECUTION.md

### 3. Browser Operation Timeout/Abort
**Problem**: Form submissions (especially "Add Record") caused AbortError timeouts.

**Solution (APPLIED)**: Add explicit waits after form submissions:
- After "Add Record": `browser_wait_for` with `time: 5`
- After "Update Record": `browser_wait_for` with `time: 3`
- After search: `browser_wait_for` with `time: 2`

**Status**: RESOLVED - Documented in test2.md Timeout Handling section

### 4. Console Message Logging
**Problem**: Console errors and warnings were not being systematically captured during test execution.

**Solution (APPLIED)**: Added console logging checkpoints:
- After login
- After patient creation
- After each edit save
- After search operations
- End of test (before closing browser)

**Status**: RESOLVED - Added "Console Message Logging (CRITICAL)" section to test2.md

## URL Patterns for Test 2

| Action | URL Pattern |
|--------|-------------|
| Search Page | `/oscar/demographic/search.jsp` |
| Search Results | `/oscar/demographic/demographiccontrol.jsp?...` |
| Add Demographic (via link) | `/oscar/demographic/demographicaddarecordhtm.jsp?search_mode=...` |
| Edit Demographic | `/oscar/demographic/demographiccontrol.jsp?demographic_no=X&displaymode=edit` |
| View Demographic | `/oscar/demographic/demographiccontrol.jsp?demographic_no=X&displaymode=edit` |

## Expected Console Warnings (Non-blocking)

These warnings are expected and should NOT cause test failure:
- `Unexpected token '%'` - JSP template parsing
- 404 for `dateFormatUtils.js` - Expected missing resource
- 404 for `master.js` - Expected missing resource
- `TypeError: Cannot read properties of undefined (reading 'username')` - Login page

## Anomalies to Flag as Regressions

Flag any of these as potential issues:
- NEW 404 errors not in expected list above
- JavaScript errors that prevent functionality
- Form submission failures
- Missing UI elements that were previously visible
- Database write failures

## Test Flow Summary (Corrected)

1. Login → wait for "Schedule"
2. Click Search menu → switch to new tab
3. Type search term → press Enter → wait for results
4. Click "Create Demographic" link at bottom of results
5. Fill form fields (leave HIN empty)
6. Click "Add Record" → **wait 5 seconds** → log console
7. Take screenshot of result
8. Verify in database if needed
9. Continue with search/edit/filter tests
10. Cleanup: mark patient Inactive
11. Log final console messages
12. Close browser

## Version History

| Date | Changes |
|------|---------|
| 2026-01-19 | Initial issues documented |
| 2026-01-19 | All fixes applied to test2.md and test-2-EXECUTION.md |
