# eForm Browser PDF Renderer (Selenium + Headless Chromium)

CARLOS renders saved eForms to PDF with a real browser engine because eForms are
JavaScript-built documents (signature blocks, `editControl*.js` DOM construction, dynamic
layout) that pure-Java HTML renderers cannot reproduce faithfully. The renderer is driven
entirely from the JVM by Selenium — **no Node.js runtime is installed, bundled, or executed
anywhere on the server**.

## Architecture

```
EformDataManagerImpl.createEformPDF (checks _eform read privilege)
  └─ EFormBrowserPdfRenderer.renderSavedEformPdf(fdid, providerNo)
       ├─ mints a single-use render token (EFormRenderTokenService, 2-min TTL, consume-once)
       ├─ launches headless Chromium via Selenium ChromeDriver (fresh browser per render)
       ├─ navigates over loopback to /EFormViewForPdfGenerationServlet?fdid=…&browserRender=true&renderToken=…
       │    └─ servlet enforces: loopback remote address + token redemption bound to the fdid
       ├─ stabilizes the page (fonts, images, animation frames), computes capture regions,
       │    takes clipped CDP screenshots (page-NNN.png)
       └─ assembles the captures into eform-browser-render-*.pdf with PDFBox
```

The token replaces any session forwarding: the browser never holds a user's session cookie, so
script on a rendered eForm can act as no one. Authorization is anchored at the manager's
`SecurityInfoManager.hasPrivilege(_eform)` check, which is the only place tokens are minted.

## Deployment requirements

- **Chromium** (or Chrome) on the server. Point the renderer at it with
  `eform_pdf_browser_chromium_path`; without the property, Selenium looks for a system Chrome.
- **chromedriver matching the browser's major version.** Recommended for production (and
  required for air-gapped hosts): install it alongside Chromium and pin
  `eform_pdf_browser_chromedriver_path`. Without the property, Selenium Manager downloads a
  matching chromedriver at first use — acceptable for dev/CI, not recommended for clinics.
- **No Node.js.** The previous Playwright-based renderer required Node + npm modules on the
  host; that requirement is gone.

## Configuration properties (`carlos.properties` / override file)

| Property | Default | Meaning |
|---|---|---|
| `eform_pdf_browser_base_url` | derived | Loopback base URL the renderer navigates to. Derived from the active request (`scheme://127.0.0.1:localPort/context`) or `project_home` when unset. Must resolve to a loopback host — anything else is rejected. |
| `eform_pdf_browser_chromium_path` | unset | Absolute path to the Chromium/Chrome binary. |
| `eform_pdf_browser_chromedriver_path` | unset | Absolute path to a pinned chromedriver. Set this in production. |

Environment: `EFORM_RENDER_ENABLE_CHROMIUM_SANDBOX=true` keeps Chromium's sandbox on (requires
kernel user-namespace support); otherwise the renderer launches with `--no-sandbox`, which is
typical for containerized Tomcat.

## Security model

- **Loopback-only egress, enforced twice.** Chromium launches with a dead proxy
  (`--proxy-server=http://127.0.0.1:1`) and a loopback bypass list, so the browser physically
  cannot reach non-loopback hosts. Independently, the renderer replays Chrome's network log and
  **fails the render** if any request targeted an origin other than the configured loopback base
  — a form whose content tries (or needs) to fetch elsewhere is never silently faxed.
- **`acceptInsecureCerts` is paired with the lockdown.** HTTPS connectors present certificates
  for the clinic's hostname, not `127.0.0.1`; the renderer accepts that mismatch so rendering
  works on TLS deployments. This is safe *only because* egress is loopback-locked — do not
  change either setting without the other.
- **Single-use render tokens.** 32 random bytes, 2-minute TTL, atomically consumed on first
  redemption, bound to one fdid. Unredeemed tokens are invalidated when a render finishes.
- **Fresh browser per render.** No cookies, storage, or cache can bleed between renders or
  users. `driver.quit()` in a `finally` block tears down chromedriver and Chromium.
- **Bounded concurrency.** At most 2 concurrent renders (30s slot wait, then a clean failure)
  so rendering can never saturate Tomcat's request workers.
- **Fail-closed page gates.** The main document must return HTTP 200 and produce zero severe
  console entries; otherwise the render fails with counts (never page content) in the log.
- **PHI-safe diagnostics.** Log lines carry fdid, sanitized origin, and counters. URLs inside
  WebDriver error messages are redacted before logging.

## Output contract

The rendered PDF is written beneath the managed temp root
(`$CATALINA_BASE/work/carlos/eform-browser-pdf-temp`, or a namespaced `java.io.tmpdir`
fallback), which fax path validation (`FaxManagerImpl`) already whitelists. Pages are lossless
raster captures at 96 CSS px → 72 pt scale; callers own cleanup of the returned file.

## Verification

- Unit tests: `mvn test -Dtest=EFormBrowserPdfRendererUnitTest,EFormRenderTokenServiceUnitTest,EFormViewForPdfGenerationServletUnitTest`
- End-to-end smoke (skips cleanly without a browser):
  `mvn test -Dtest=EFormBrowserPdfRendererSeleniumSmokeTest` — serves
  `scripts/fixtures/eform/test-pattern.html` over loopback and asserts real regions, captures,
  and a valid `%PDF` output.
