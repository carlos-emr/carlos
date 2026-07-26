# Handoff: eForm browser-render surface does not reproduce the interactive viewer's page chrome

**Status:** RESOLVED — see §11. Kept for the corpus analysis and the reasoning trail.
**Branch:** `claude/pr-3164-eform-review-bakfzo` (PR #3164)
**Failing check:** `npm run test:eform-rtl-attachment-behavior-playwright` — now passing
**Merge base for "pre-existing vs introduced" calls:** `2f5e0037b40eeecdc86bfe1df8dd7771106258f7`

---

## 1. Summary

The new browser-based PDF renderer (`EFormBrowserPdfService`, added by this PR) renders a saved eForm by
navigating a headless Chromium to `/<context>/EFormViewForPdfGenerationServlet`, which serves **only the
stored form HTML** assembled by `EFormRenderPdfHtmlComposer`.

The interactive viewer (`/WEB-INF/jsp/eform/efmshowform_data.jsp`) serves the *same* stored HTML but wraps
it in page chrome: jQuery, jQuery UI, Bootstrap, the runtime-compat shim, the floating toolbar, CSS, and
hidden inputs. **The render surface reproduces none of that.**

Consequence: corpus forms that depend on host-page-injected libraries fail during render. The specific
observed failure is an `alert()` from `printControl.js`, which aborts the whole render via Selenium's
`UnhandledAlertException`, and the clinician gets a dead-end
`"This eForm (and attachments, if applicable) could not be downloaded."`

**This is not a regression introduced by this PR.** The "host page injects jQuery" contract predates it
(commit `dabb2482df1`, 2026-03-24, PR #716 — confirmed an ancestor of the merge base). What this PR adds
is a **second host page** that never implemented that contract.

---

## 2. Reproduction

Prereqs: devcontainer with MariaDB running locally (`/etc/init.d/mariadb start`), app deployed via
`make install`, Chromium at the path in `/root/carlos.properties`
(`eform_pdf_browser_chromium_path=/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome`).

```bash
npm run test:eform-rtl-attachment-behavior-playwright
```

Fails at `scripts/eform-rtl-attachment-behavior-playwright-checks.js:125`
(`viewPage.waitForEvent('download', { timeout: 90000 })` after clicking `#remoteDownloadButton`).

Server-side evidence (`/usr/local/tomcat/logs/catalina.out`):

```
ERROR util.EFormBrowserPdfService (EFormBrowserPdfService.java:774) - Browser eForm renderer failed:
  fdid=143 baseUrl=http://127.0.0.1:8080/carlos
  type=org.openqa.selenium.UnhandledAlertException
  error=unexpected alert open: {Alert text : The printControl library requires jQuery. Please ensure that it is loaded first}
WARN gate.ViewSignatureControl2Action (ViewSignatureControl2Action.java:53) - Denied signatureControl: no session
ERROR actions.AddEForm2Action (AddEForm2Action.java:520) - This eForm (and attachments, if applicable) could not be downloaded.
```

---

## 3. Root cause chain

1. `src/main/webapp/library/eforms/printControl.js:5-7`:
   ```js
   if (typeof jQuery == "undefined") {
       alert("The printControl library requires jQuery. Please ensure that it is loaded first");
   }
   ```
2. The stored HTML of the affected forms contains **no jQuery `<script>` tag**. Form `fid=1`
   ("Rich Text Letter") instead carries the literal marker:
   ```html
   <!-- jQuery 3.7.1 injected by host page -->
   ```
3. `src/main/webapp/WEB-INF/jsp/eform/efmshowform_data.jsp:124-131` is that host page. Note the file's own
   comment — **"Ordering is very important. For Javascript: First is last."** — i.e. `addHeadJavascript`
   prepends, so the *last* call ends up first in the document:
   ```java
   eForm.addHeadJavascript(ctx + "/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js");
   eForm.addHeadJavascript(ctx + "/js/jquery.are-you-sure.js");
   eForm.addHeadJavascript(ctx + "/library/jquery/jquery-ui-1.14.2.min.js");
   eForm.addHeadJavascript(ctx + "/library/jquery/jquery-3.7.1.min.js");   // ends up FIRST
   eForm.addCSS(ctx + "/library/bootstrap/5.3.8/css/bootstrap.min.css", "all");
   eForm.addHeadJavascript(ctx + "/eform/eform-runtime-compat.js");
   eForm.addCSS(ctx + "/css/oscar_alert.css", "all");
   eForm.addBodyJavascript(ctx + "/js/oscar-alert.js");
   eForm.addCSS(ctx + "/library/jquery/jquery-ui-1.14.2.min.css", "all");
   eForm.addBodyJavascript(ctx + "/eform/eformFloatingToolbar/eform_floating_toolbar.js");
   eForm.addFontLibrary(ctx + "/share/javascript/eforms/dejavufonts/ttf/DejaVuSans.ttf");
   eForm.addHiddenInputElement("context", ctx);
   eForm.addHiddenInputElement("demographicNo", eForm.getDemographicNo());
   eForm.addHiddenInputElement("fdid", fdid);
   ```
4. `EFormRenderPdfHtmlComposer.buildPdfHtml(...)`
   (`src/main/java/io/github/carlos_emr/carlos/eform/util/EFormRenderPdfHtmlComposer.java:133+`) makes **no
   equivalent calls**. It injects letter/signature content, rewrites image paths, splices the render token,
   and returns.
5. Chromium raises the alert; Selenium throws `UnhandledAlertException`; the render aborts.

**Ordering matters.** `printControl.js` runs its guard at parse time, so jQuery must be in `<head>` *before*
the form's own control-script tags. The form's tags are in the stored body HTML, so a naive append will not
work — the injection must land in `<head>` (which is what `addHeadJavascript` → `addHeadElement` does).

---

## 4. Corpus impact (dev database, `oscar` schema, 55 forms)

| Metric | Count |
|---|---|
| eForms total | 55 |
| Reference a control script (`printControl` / `faxControl` / `imageControl` / `APCache`) | 23 |
| Reference `printControl.js` **and have no real jQuery `<script src>` tag** | **21** |
| Carry a real jQuery `<script src=...jquery....js>` tag | 2 |
| Carry the `<!-- ... injected by host page -->` marker | 1 |
| Reference `signatureControl` | 3 |
| Reference `stamps.js` | 1 |

Reproduce with:
```sql
SELECT COUNT(*) FROM eform
 WHERE form_html LIKE '%eforms/printControl.js%'
   AND form_html NOT REGEXP 'src="[^"]*jquery[^"]*\\.js';
```

So **~21 of 55 forms (38%)** in this corpus are exposed. A production corpus is likely worse, since these
control scripts are what `eformGenerator.jsp` emits for generated forms.

---

## 5. The decision to make

Injecting jQuery into the render surface means scripts that **currently fail fast now execute** during
rendering. That is the blast radius, and it is a clinical-content decision, not a mechanical one:

- **Upside:** the render surface finally matches what the clinician sees in the viewer, which is the whole
  point of a WYSIWYG print path. Forms whose content is populated by `APCache.js` / `imageControl.js` /
  form-local jQuery code would render *correctly* instead of silently under-populated.
- **Risk:** those scripts have never run headlessly. They may open dialogs (`alert`/`confirm` → another
  `UnhandledAlertException`), fire XHRs (the renderer's dead proxy blocks off-origin; same-origin ones need
  auth the sessionless browser lacks), mutate the DOM after the geometry pass, or depend on the toolbar /
  hidden inputs that the render surface also lacks.

### Candidate approaches

**A. Mirror the viewer's chrome in the composer (recommended starting point).**
Add the same `addHeadJavascript`/`addCSS` calls to `EFormRenderPdfHtmlComposer`, ideally factored into one
shared helper so the viewer and the renderer cannot drift again. Consider trimming interactive-only pieces
(floating toolbar, are-you-sure) that have no meaning in a print snapshot.
*Pro:* fidelity, single source of truth. *Con:* largest behavioural surface.

**B. Inject jQuery only.**
Minimal change that clears the observed guard.
*Pro:* small, targeted. *Con:* leaves the renderer subtly different from the viewer; the next
host-page-dependent library repeats this bug. Also still lets the control scripts execute.

**C. Strip interactive-only control scripts from the render HTML (jsoup).**
Remove `printControl` / `faxControl` / `imageControl` / `signatureControl` `<script>` tags at compose time —
the renderer already derives signature geometry itself by parsing `signatureControl.initialize({...})`
(`EFormRenderPdfHtmlComposer.java:260`) and serves signature images through
`EFormSignatureViewForPdfGenerationServlet`, so these libraries are arguably editor-only.
*Pro:* smallest runtime surface; deterministic renders; also resolves §6 and §7 below.
*Con:* must confirm none of them contribute *content* (`APCache.js` in particular — verify what "AP" caches
and whether it populates fields).

**Suggested empirical step before choosing:** apply C to the four interactive controls and A/B for jQuery,
then diff rendered PDFs across a representative sample of the 23 control-script forms against the current
output. That distinguishes "script was decorative" from "script populated clinical content".

Whatever is chosen, add a regression test pinning the composed `<head>` contents so the viewer/renderer
contract is enforced in CI rather than by convention.

---

## 6. Also unresolved: `signatureControl.jsp` is unreachable from the renderer

- Route: `src/main/webapp/WEB-INF/classes/struts-integration.xml:165-172` — canonical
  `library/eforms/signatureControl` plus a compatibility alias `library/eforms/signatureControl.jsp` for
  corpus forms that embed the literal `.jsp` URL. Both resolve to
  `/WEB-INF/jsp/library/eforms/signatureControl.jsp`.
- Action: `io.github.carlos_emr.carlos.library.eforms.gate.ViewSignatureControl2Action:52-60` requires a
  live session **and** `_con` write.
- The renderer is sessionless (its only credential is the render token), so it logs
  `Denied signatureControl: no session` and throws `SecurityException`.

The gate is behaving correctly — do **not** weaken it. Options: strip the tag at compose time (approach C),
or teach that action to accept a live render grant the way the image/signature servlets already do. 3 forms
in this corpus reference it.

---

## 7. Also unresolved: `stamps.js` is referenced-but-never-deployed

- `src/main/webapp/WEB-INF/jsp/eform/eformGenerator.jsp:1197` injects
  `<script src="<ctx>/eform/displayImage?imagefile=stamps.js"></script>` into **every generated eForm**.
- `src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java:82-85` documents that `stamps.js`
  is **intentionally never auto-deployed** ("clinic-specific doctor signature image mappings that
  administrators create themselves through the eForm admin UI").
- Result on a fresh install: the tag 404s. `Script` is a content-critical resource type, so it counts toward
  `failedCriticalSubresources` → the completeness gate blocks the PDF.

Note the deployer's stated rationale ("prevents overwriting clinic-customized versions") is already
satisfied by the `targetFile.exists()` guard in `deployAssetFromPath`/`deployGeneratedAsset`, so shipping a
default empty `stamps.js` would *not* clobber a clinic's file. That is one option; not emitting the tag when
no stamps exist is another; approach C is a third.

> ⚠️ **Cleanup required:** I created a diagnostic stub at
> `/var/lib/OscarDocument/oscar/eform/images/stamps.js` (127 bytes) to isolate this from the CSS failure.
> **Delete it** before drawing conclusions about fresh-install behaviour — with it present, the `stamps.js`
> 404 does not reproduce.

---

## 8. Already fixed on this branch (context — do not redo)

Three changes are in the working tree, all test-covered (263 unit tests green; `eform-render`,
`eform-saved-render`, `rtl-attachment-routes`, `rtl-attachment-types` Playwright checks all pass):

1. **`EForm.java`** — `rewriteViewerRelativeAssetReferences` / `anchorViewerRelativePath`: generic jsoup
   re-anchoring of `../` asset references to the context path. The render page sits one path segment below
   the origin (`/<ctx>/EFormViewForPdfGenerationServlet`) while the viewer sits two
   (`/<ctx>/eform/efmshowform_data`), so clinic-authored `../css/x.css` was resolving to the origin ROOT and
   404ing. Render-path only — stored HTML is never rewritten in the DB. Tests:
   `EFormViewerRelativeAssetUnitTest`.
2. **`EFormBrowserPdfService.java`** — new `PRESENTATION_RESOURCE_TYPES` (`Stylesheet`, `Font`) routed to
   the advisory bucket instead of `failedCriticalSubresources`. A 404'd icon font was withholding a complete
   clinical letter. Content-bearing types still block. Test:
   `shouldTreatPresentationResources_asAdvisoryFailures`.
3. **`LoginFilter.java`** — `LOOPBACK_ONLY_RENDERER_ASSET_URLS` (`/library/eforms/`, `/webfonts/`) exempt
   from the login redirect **only for loopback callers**, deliberately reading raw `getRemoteAddr()` and
   never `X-Forwarded-For`. Tests: `LoopbackOnlyRendererAssets` (5 cases).

Measured effect: `failedContentResources` 2 → 0. Change #3 is what *revealed* the jQuery bug — before it,
`printControl.js` was 302'd to login and never executed.

---

## 9. Verification commands

```bash
# Unit
mvn -o test -Dtest='EFormViewerRelativeAssetUnitTest,EFormBrowserPdfServiceUnitTest,\
EFormRenderPdfHtmlComposerUnitTest,EFormSetContextPathUnitTest,LoginFilterUnitTest'

# End-to-end (needs Tomcat + local MariaDB)
make install
npm run test:eform-render-playwright                      # must stay PASS
npm run test:eform-saved-render-playwright                # must stay PASS
npm run test:eform-rtl-attachment-behavior-playwright     # currently FAIL — the target

# Gate telemetry
grep -E "detected missing content|tolerated non-fatal|blocked incomplete|renderer failed" \
  /usr/local/tomcat/logs/catalina.out | tail
```

---

## 10. Environment caveat

The dev container is currently running **without an init process**, so orphaned Chromium helpers are not
reaped — 138 zombies observed after a handful of renders. `.devcontainer/docker-compose.yml` already sets
`init: true` (tini); the container simply needs recreating rather than restarting. This does not affect the
bugs above but will eventually exhaust the pids cgroup during long render-testing sessions.

---

## 11. Resolution

The decision in §5 went to **approach C** (strip the interactive controls), not A or B, and the
reason is the opposite of what §5 anticipated. Injecting jQuery was tried first: it cleared the
`printControl.js` alert, but then `editControl2.js` actually booted on the render surface and issued
two same-origin XHRs — the letter-template list and the attachment-sidebar poll. The renderer's
`connect-src` admits only the APCache endpoint, so both were refused, counted as failed *content*
resources, and the completeness gate blocked the entire PDF. The render got further and still
produced nothing.

`EFormRenderPdfHtmlComposer.removeInteractiveEditorContent` now strips `editControl2.js`, the
print/fax/image controls, the signature-capture control, the floating toolbar, and the
`insertEditControl()` bootstrap. `APCache.js` is kept — §5 flagged the open question of whether it
contributes content, and it does, which is why the capability-scoped APCache endpoint exists.

Stripping the editor introduced one new failure that had to be handled: `Start()` (the body
`onload`) and `cache` are both defined *inside* `editControl2.js`, so their removal produced two
ReferenceErrors — two severe console errors, gate blocks again. `installStrippedEditorShim` rebuilds
`cache` from APCache's `createCache` when present and no-ops only the editor sinks.

Also resolved by the same change:

- **§6 `signatureControl.jsp` unreachable** — the tag is stripped at compose time, so the gated
  action is never reached from the renderer. The gate was not weakened.
- **§7 `stamps.js` referenced-but-never-deployed** — `removeAbsentOptionalStamps` drops the tag when
  the file is genuinely absent. The `/var/lib/OscarDocument/oscar/eform/images/stamps.js` diagnostic
  stub mentioned in §7 was NOT recreated; fresh-install behaviour is what was verified.

Two defects found by manual visual inspection of the rendered PDFs, which no gate could see because
both loaded cleanly and failed only in paint order or encoding:

- **Background images vanished.** `PREPARE_PRINT_JS` set `html { background: white }`; the root
  background propagates to the page canvas, painted beneath the `z-index:-1` layer that eForms use
  for scanned form backgrounds. Removed.
- **Letters printed their own markup as text.** `saveRTL()` stores the letter entity-encoded and the
  composer spliced it raw. `decodeStoredLetter` reverses it in `editControl2.js`'s exact order.

And one that was never about the renderer at all: reopening a saved letter showed an empty editor,
because `seteditControlContents` was called before `designMode` was enabled, and because DOMPurify
was never loaded on the eForm host pages so the sanitize gate failed closed to `textContent`. Both
fixed; the toolbar's save-and-download was otherwise persisting an empty letter over a real one.

§10's environment caveat is addressed separately by `init: true` in the devcontainer compose file.
