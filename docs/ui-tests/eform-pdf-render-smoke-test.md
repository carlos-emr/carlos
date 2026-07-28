# eForm PDF Render Smoke Test

This runbook smoke-tests the eForm PDF render fidelity work covered by PR `#3017`.

It uses the existing repo Playwright scripts plus a short manual pass for the exact
preview and `saveAsEdoc` flows touched by the branch.

## Scope

This smoke test is meant to answer one question:

Can a real eForm still render, save, reopen, preview as PDF, and participate in the
consultation and `saveAsEdoc` flows without losing backgrounds, field data, or
user-facing error handling?

## Prerequisites

1. Start the CARLOS app locally.
   Recommended:
   ```bash
   make install
   server start
   ```

2. Confirm the app is reachable.
   Default target used by the scripts:
   - `http://127.0.0.1:8080/carlos`

3. Confirm test credentials work.
   Default script credentials:
   - `TEST_USER=carlosdoc`
   - `TEST_PASSWORD=carlos2026`
   - `TEST_PIN=2026`

4. Confirm a valid test demographic exists.
   Default demographic used by the scripts:
   - `1`

5. Set screenshot output directories.
   Recommended:
   ```bash
   export EFORM_SCREENSHOT_DIR=/tmp/eform-smoke
   export SAVED_RENDER_SCREENSHOT_DIR=/tmp/eform-smoke
   export EFORM_CONSULT_SCREENSHOT_DIR=/tmp/eform-smoke
   ```

6. Set `CHROME_PATH` only if Playwright cannot find Chromium automatically.

## Scripted Smoke Pass

Run these in order.

### 1. eForm Admin UI Regression

Command:

```bash
npm run test:eform-admin-playwright
```

Expected result:
- `Create eForm` dropdown opens correctly
- admin nav still uses the expected Bootstrap dropdown behavior
- editor redirect regression does not reappear
- no unexpected browser console errors

### 2. App-Backed Render Pipeline Check

Command:

```bash
npm run test:eform-render-playwright
```

Expected result:
- temporary eForm fixture imports successfully
- malformed HTML comments do not break the render path
- background image resolves through `displayImage`
- `/previewDocs?method=renderEFormPDF` returns a real PDF
- no unexpected severe console or network failures

### 3. Saved eForm Reopen Check

Command:

```bash
npm run test:eform-saved-render-playwright
```

Expected result:
- a real saved `fdid` is created
- reopened saved form still shows persisted field data
- reopened saved form still shows the background image
- no unexpected severe console or network failures

### 4. Consultation Reuse Check

Command:

```bash
npm run test:eform-consultation-acceptance
```

Expected result:
- a saved eForm instance is reused by the consultation workflow
- consultation preview requests succeed
- saved-form identity stays stable through the consultation path
- no unexpected severe console or network failures

## Manual Smoke Pass

Perform this after the 4 scripted checks pass.

### 1. Preview-on-Save

1. Open an eForm with a visible background image and at least one editable field.
2. Fill one or more fields.
3. Save the eForm normally.
4. Confirm the close-with-preview flow appears.
5. Confirm the PDF preview is rendered and not replaced by an error page.
6. Confirm the preview filename is sensible.

Pass criteria:
- save completes successfully
- preview content renders
- no raw exception text is shown to the user

### 2. Reopen Saved eForm

1. Reopen the just-saved form from the patient eForm list or equivalent saved-form path.
2. Confirm the saved field values are still present.
3. Confirm the background image is still visible.
4. Trigger PDF preview or download again if available from that surface.

Pass criteria:
- reopened form visually matches the saved form
- saved values are intact
- background image still resolves

### 3. `saveAsEdoc`

1. Open an eForm that can be saved to documents.
2. Trigger `saveAsEdoc`.
3. Confirm the success case creates the document entry.
4. If the environment safely supports a failure simulation, confirm the error shown to the user is generic and not an internal exception message.

Pass criteria:
- success path stores the document
- failure path, if exercised, shows only user-safe messaging

## Failure Capture

If any step fails, capture all of the following before retrying:

- the exact command or manual step that failed
- screenshot path from `/tmp/eform-smoke` or your chosen output directory
- browser console errors
- relevant failing request URL and HTTP status
- whether the failure was in admin UI, render pipeline, saved render, consultation reuse, preview generation, or `saveAsEdoc`

## Final Acceptance

Treat the branch as smoke-tested only if all of the following are true:

- all 4 scripted checks pass
- the manual preview-on-save pass works
- the manual reopen-saved-form pass works
- the `saveAsEdoc` success path works
- no unexpected console errors or 4xx/5xx responses appear during the pass

## Optional Local RTL Regression Checks

These are local developer regression tools only. They are intentionally not part
of CI or any required repo check.

### 1. RTL Attachment Route Check

Command:

```bash
npm run test:eform-rtl-attachment-routes-playwright
```

Expected result:
- the Rich Text Letter attach popup opens through `/eform/attachEform`
- the attachment sidebar refresh hits `/eform/displayAttachedFiles`
- neither flow falls back to a legacy `.jsp` endpoint

### 2. RTL Attachment Behavior Check

Command:

```bash
npm run test:eform-rtl-attachment-behavior-playwright
```

Expected result:
- a saved Rich Text Letter instance gets a real `fdid`
- the attach popup request uses that saved `fdid`
- submitting the popup with no selected attachments does not crash

### 3. RTL Attachment Type Coverage Check

Command:

```bash
npm run test:eform-rtl-attachment-types-playwright
```

Expected result:
- the RTL attachment surface exposes documents, labs, HRM, eForms, and encounter forms
- missing attachment families are reported as an explicit regression
