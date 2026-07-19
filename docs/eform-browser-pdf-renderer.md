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

Environment: the renderer is **sandboxed by default** (see "Security operations" below).
`EFORM_RENDER_ALLOW_UNSANDBOXED=true` is the explicit opt-out that launches Chromium with
`--no-sandbox`, for deployments where the container is the isolation boundary.

## Security model

- **Loopback-only egress, enforced twice — scoped to the application's own port.** Chromium
  launches with a dead proxy (`--proxy-server=http://127.0.0.1:1`) whose bypass list is exactly
  the app's own loopback `host:port` (`127.0.0.1:<port>;localhost:<port>;[::1]:<port>`), so the
  browser physically cannot reach non-loopback hosts *or other local services on different
  ports* — such requests are blocked before they are sent. Independently, the renderer replays
  Chrome's network log and **fails the render** if any request targeted an origin other than the
  configured loopback base — a form whose content tries (or needs) to fetch elsewhere is never
  silently faxed.
- **No attachable browser control channel.** Chromium runs with `--remote-debugging-pipe`, so
  DevTools is a parent-process pipe rather than a localhost TCP port another local process could
  connect to.
- **No local file access.** A malicious eForm cannot read server files (`file:///etc/passwd`,
  `/var/lib/OscarDocument/...`) into the rendered PDF. Two layers: Chromium's default cross-scheme
  policy blocks `file://` subresources from the http render origin, and the renderer's request
  gate fails the render on *any* non-web scheme (`file:`, `filesystem:`, `chrome:`,
  `view-source:`, …). The launch config must **never** add `--allow-file-access-from-files` or
  `--disable-web-security` (an inline code invariant and a unit test enforce their absence); the
  FileSystem API is additionally turned off with `--disable-file-system`.
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

### Security operations note

The containment layers split responsibility: the egress lockdown and fail-closed gates contain
**malicious page content** (JS-level attacks — including WebSocket/WebTransport egress, which the
gate rejects alongside HTTP), while an **OS-level sandbox** contains native browser exploits (an
RCE in Chromium's renderer). Because the renderer processes clinic-authored content, treat the
pinned Chromium and chromedriver like any other security-patched dependency: keep them updated.
The token design means the render browser holds no session cookies or credentials, so a
compromised render exposes only the content of the form being rendered.

**Selenium is not an isolation layer.** It only launches `chromedriver` → `chrome`; the
chroot / namespace / seccomp confinement is Chromium's *own* sandbox (or the container). This
renderer is **secure by default and fails closed**: it launches Chromium sandboxed, and if a
sandboxed launch can't start it **fails the render with actionable guidance** rather than silently
dropping to `--no-sandbox`. Run the browser confined via one of the two paths below — ideally both:

1. **Chromium's own sandbox (default, preferred).** Chromium's Linux sandbox chroots the renderer
   into an empty dir inside new PID + network namespaces (Layer 1) and applies a seccomp-bpf
   syscall filter (Layer 2). The modern **unprivileged user-namespaces** variant needs **no
   root** — only kernel ≥3.10 with user namespaces enabled
   (`sysctl kernel.unprivileged_userns_clone=1`), and the renderer must run as a **non-root**
   user (Chromium refuses its sandbox as root). This is the default; no configuration is needed.
   If the sandbox cannot start, the render fails with a message telling the operator to enable
   user namespaces / run as non-root, or to consciously choose path 2.
2. **Make the container the boundary (explicit opt-out).** Where user namespaces are unavailable,
   set `EFORM_RENDER_ALLOW_UNSANDBOXED=true` to launch with `--no-sandbox`. This is acceptable
   **only if the container itself is the isolation boundary**: dedicated non-root UID,
   `cap-drop=ALL` (no `CAP_SYS_ADMIN`), `--security-opt no-new-privileges`, read-only root
   filesystem, `tmpfs` for `/dev/shm` and the renderer temp root mounted `noexec,nosuid,nodev`, a
   seccomp profile (Docker default or a Chrome-tuned one), and PID / CPU / memory limits. On newer
   kernels, Landlock adds filesystem confinement. Every render logs a `WARN` while this opt-out is
   active, so an unsandboxed browser is never silent in ops logs.

There is **deliberately no automatic fallback** from path 1 to path 2 — a silent drop to
`--no-sandbox` would reinstate the insecure default. `EFORM_RENDER_ALLOW_UNSANDBOXED` with **no**
real container boundary is a testing-only configuration and must not be used where the renderer can
be reached by clinic-authored eForms.

(The dev/CI Playwright check scripts under `scripts/` are separate test tooling; they run as root
in CI and use their own `EFORM_RENDER_ENABLE_CHROMIUM_SANDBOX` opt-in — not this production knob.)

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
