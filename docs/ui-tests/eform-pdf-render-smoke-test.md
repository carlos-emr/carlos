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

7. Confirm the database has the Rich Text Letter attachment-route migration applied:

   ```bash
   mysql -h db -u root -p"$MYSQL_ROOT_PASSWORD" oscar \
     < database/mysql/updates/update-2026-06-29-rtl-attachment-route-fix.sql
   ```

   Without it the stored RTL template still opens `../eform/attachEform.jsp`, which 404s, and all
   three RTL attachment checks fail on an error-page assertion that looks like a routing regression.

8. No action needed for `editControl2.js`. It is a *managed* asset: `EFormAssetDeployer` compares the
   deployed copy against the shipped bytes on every startup and replaces it when they differ, so a
   change to `src/main/webapp/WEB-INF/eform-assets/editControl2.js` reaches the running install on
   the next restart. (Clinic-customizable assets — `blank.rtl`, `editor_help.html`, the lab
   decision-support stubs — are still deployed once and then left alone.)

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

### 5. Extended eForm Contract Checks

Run the remaining eForm-adjacent browser contracts before release:

```bash
npm run test:eform-test-pattern-playwright
npm run test:eform-rtl-attachment-routes-playwright
npm run test:eform-rtl-attachment-behavior-playwright
npm run test:eform-rtl-attachment-types-playwright
npm run test:consultation-signature-playwright
npm run test:consultation-signature-submit-playwright
```

These pin test-pattern rendering, Rich Text Letter attachment routes/data families, and both
consultation signature display and submit behavior. Do not add a new missing-resource exception to
any recorder without clinician/developer approval; `stamps.js`, legacy `signature.js`
compatibility, and genuinely optional font/style failures are the only pre-approved categories.

## Manual Smoke Pass

Perform this after the 5 scripted checks pass. Use a fresh browser session for the renderer
observation so an existing CARLOS login cannot mask a session-scoping defect.

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

### 4. Renderer isolation and passive saved-view profile

1. Generate a PDF from a saved eForm that uses a background image, local CSS/JS, a stored
   signature, and an APCache-populated field when those fixtures are available.
2. In the server-side Chromium network observation, confirm the initial renderer document is the
   only URL containing `renderToken`.
3. Confirm subsequent renderer requests use only GET/HEAD and remain on the exact loopback origin.
4. Confirm no `JSESSIONID`, CARLOS login cookie, provider identity, demographic identity, or CSRF
   token is present in the renderer browser.
5. Confirm the renderer cookie is `CARLOS_EFORM_RENDER`, host-only, HttpOnly, SameSite=Strict,
   application-path scoped, and Secure when the target is HTTPS.
6. Confirm the renderer document has `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, and
   CSP containing `form-action 'none'`.
7. Confirm saved values, Letter content, background assets, and stored signature geometry are
   visible in the PDF. Confirm editor/toolbar/signature-capture controls are absent.
8. Attempt a form submit, popup, and navigation from a renderer-specific test fixture. Confirm no
   write request or popup succeeds and the render either completes with a contained-interaction
   report or fails safely.

Pass criteria:
- the browser has only the renderer capability, never a clinician session
- every resource is an exact granted read and the bootstrap token does not propagate
- security containment does not remove saved clinical content
- any newly missing clinic resource is recorded and approved before an exception is added

### 5. Open the produced PDF and look at it

The render gates cannot see everything. Two defects shipped past a completely clean gate because
the failure was in *paint order and encoding*, not in resource loading: a background image that
returned HTTP 200 but was covered by the page canvas, and a letter that was spliced in as escaped
text so the PDF printed `<h3>` instead of a heading. `%PDF-` plus a byte count proves neither.

For at least one form with a background image and one Rich Text Letter, open the PDF and confirm:

1. The background image is visible, correctly placed and scaled. Test a form whose background uses
   `position:absolute; z-index:-1` — that is the idiom the canvas used to cover.
2. Letter content is formatted — headings, paragraphs, lists — with no literal `<p>` / `&lt;` in the
   page.
3. No editor chrome: no toolbar, no template/font selectors, no Submit/Print/Reset buttons, no
   signature-capture pad. A printed eForm is a passive snapshot.
4. Anything marked `.DoNotPrint` is absent.
5. Text is selectable, not a raster image.

If a rasterizer is needed, PDFBox is already on the classpath and `PDFTextStripper` plus
`PDFRenderer` will both extract the text layer and produce a PNG to eyeball.

### 6. Saved-letter round trip

1. Type a formatted letter into a Rich Text Letter, save, then reopen the saved `fdid`.
2. Confirm the editor shows the letter **formatted** — not escaped markup, not blank.
3. Save again, then confirm the stored value has not gained another layer of entity encoding.

This path silently lost content twice: once because the editor wrote contents before enabling
`designMode` (a no-op), and once because DOMPurify was not loaded on the host page, so the editor's
sanitize gate failed closed to `textContent`. Both look identical to the clinician — an empty or
escaped editor — and both cause the *next* save to overwrite the stored letter.

## Failure Capture

If any step fails, capture all of the following before retrying:

- the exact command or manual step that failed
- screenshot path from `/tmp/eform-smoke` or your chosen output directory
- browser console errors
- relevant failing request URL and HTTP status
- whether the failure was in admin UI, render pipeline, saved render, consultation reuse, preview generation, or `saveAsEdoc`

## Final Acceptance

Treat the branch as smoke-tested only if all of the following are true:

- all 5 scripted checks pass
- the manual preview-on-save pass works
- the manual reopen-saved-form pass works
- the `saveAsEdoc` success path works
- the renderer isolation/passive-profile pass works
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
