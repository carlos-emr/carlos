# eForm Browser PDF Renderer (Selenium + Headless Chromium)

CARLOS renders saved eForms to PDF with a real browser engine because eForms are
JavaScript-built documents (signature blocks, `editControl*.js` DOM construction, dynamic
layout) that pure-Java HTML renderers cannot reproduce faithfully. The renderer is driven
entirely from the JVM by Selenium — **no Node.js runtime is installed, bundled, or executed
anywhere on the server**.

## Architecture

```text
EformDataManagerImpl.createEformPDF (checks _eform read privilege)
  └─ EFormBrowserPdfService.renderSavedEformPdf(fdid, providerNo)
       ├─ mints a render-scoped render token (EFormRenderTokenService, 2-min TTL)
       ├─ launches headless Chromium via Selenium ChromeDriver (fresh browser per render)
       ├─ navigates over loopback to /EFormViewForPdfGenerationServlet?fdid=…&browserRender=true&renderToken=…
       │    ├─ servlet enforces: loopback remote address + token grant bound to the fdid
       │    └─ rewrites the eForm's ${oscar_image_path}/displayImage asset URLs to
       │         /EFormImageViewForPdfGenerationServlet?renderToken=…&imagefile=…, so the sessionless
       │         browser fetches each background/asset image under the same grant (loopback + token)
       ├─ emulates print media, stabilizes the page (fonts, images, animation frames),
       │    measures each authored page div's content box, and sizes the CSS @page boxes to match
       └─ prints the page to a native, text-layer eform-browser-render-*.pdf via CDP Page.printToPDF
```

The token replaces any session forwarding: the browser never holds a user's session cookie, so
script on a rendered eForm can act as no one. Authorization is anchored at the manager's
`SecurityInfoManager.hasPrivilege(_eform)` check, which is the only place tokens are minted.

### Native print-to-PDF with authored @page sizing

The renderer uses Chromium's native print pipeline (`Page.printToPDF`) so every eForm PDF carries a
real, selectable/searchable **text layer** (accessibility, archive search, smaller files). Native
print paginates by **paper geometry**, and legacy eForms are absolutely-positioned documents built
from `page1..pageN` divs sized in CSS pixels over background images — so left alone, native print
would split or rescale those authored page boundaries to fit a default paper size. The renderer
closes that gap by **measuring each authored page div's content box and injecting a matching CSS
`@page` size** before printing:

- `Emulation.setEmulatedMedia` is set to `print` before the page settles, so the layout that is
  measured and gated is exactly the layout that prints (a form's own `@media print` rules apply).
- `COMPUTE_PAGE_GEOMETRY_JS` measures each `pageN` div: printed page height is the LARGER of the
  div's own flow extent and its visible-descendant union (vertical under-measurement spills blank
  pages or clips fields), while printed page width hugs the content union (region-capture parity —
  a plain block page div stretches to the viewport and would print a giant blank right margin).
- Corpus forms author **no page-break CSS at all** (the legacy region capture never needed it), so
  `buildPageSizeCss` injects an explicit per-div pagination contract: pinned `height`,
  `overflow: hidden` (region-capture clipping parity), `margin: 0`, and `break-after: page` on every
  page div but the last (`auto` on the last, so an authored inline `page-break-after` cannot emit a
  trailing blank page). It emits either one anonymous `@page { size }` (all pages share a size — the
  common single-scan-geometry form) or CSS **named pages** bound to each page div by id (sizes
  differ), and `Page.printToPDF` runs with `preferCSSPageSize:true`, `printBackground:true`,
  `scale:1`, and zero margins so those authored sizes drive the PDF page boxes 1:1.
  `readPageGeometry`/`readPageSizes` validate the measured geometry fail-closed (page-count and
  per-dimension caps, non-finite rejection).
- **In-flow content outside the page divs is excluded from the printed PDF, with an operator WARN
  when it is substantive.** Interstitial in-flow content structurally cannot stay in flow — it
  shifts every subsequent authored page off its page boundary — and the legacy region capture never
  photographed it. Invisible layout junk (spacer divs, empty paragraphs) is excluded silently;
  substantive content (real text or visual elements, e.g. a trailing license notice) triggers a
  WARN with a count and pixel extent (never the content itself) so a form designer can see why it
  is absent from the PDF. The on-screen eForm view still shows it. Absolutely-positioned siblings
  stay visible (out of flow; some corpus forms overlay inputs onto pages from outside the divs).
- A **free-flow form** (the Rich Text Letter) authors no `pageN` divs, so no `@page` size is
  injected, nothing is excluded, and the form's own `@page` rule (or Chromium's default paper)
  drives natural pagination.

This preserves the legacy per-page geometry contract the form corpus depends on **and** yields a
text-layer PDF. (The previous implementation screenshotted each `pageN` region via
`Page.captureScreenshot` and glued the PNGs with PDFBox `LosslessFactory`, which produced image-only
PDFs with no extractable text — and clipped pages whose background image was smaller than their
content.) Equivalence is pinned by the Selenium smoke test
(`EFormBrowserPdfServiceSeleniumSmokeIntegrationTest`): the page-div fixture prints to a 2-page PDF
whose page count matches the authored divs and whose text layer contains the form's text, and the
free-flow fixture prints to a text-layer PDF with no injected `@page` size.

## Deployment requirements

> **Containerized deployments MUST run an init process (zombie reaping).** Each render launches a
> Chromium process tree; on an abnormal teardown (a killed chromedriver, a deploy mid-render, an
> OOM-killed helper) orphaned helper processes reparent to PID 1 and become zombies. A container
> whose PID 1 is not an init (e.g. a bare `tail -f /dev/null` or the JVM itself) never reaps them,
> and each zombie permanently consumes a pids-cgroup slot — a long-lived deployment can exhaust
> `pids.max` and every later browser launch fails with "unable to create native thread". Run the
> container with `docker run --init` (or tini/dumb-init as PID 1, or a systemd-managed service) so
> orphans are reaped.

> **Runbook: provision the browser BEFORE deploying.** Because the startup gate below defaults to
> `required`, deployment ordering matters: install Chromium and a matching chromedriver (and set
> `eform_pdf_browser_chromium_path` / `eform_pdf_browser_chromedriver_path`) **before** the webapp
> deploys, or Tomcat will refuse to deploy CARLOS at all. That refusal is the deliberate
> deployment decision — an EMR whose eForm fax/archive pipeline is known-broken must not start.
> Verify a new host by deploying to a staging slot with `eform_pdf_browser_startup_check=required`
> and confirming `eForm browser renderer startup check passed.` in the log. `warn`/`off` are
> explicit, logged opt-outs for staged rollouts only — never a steady-state configuration.

> **Upgrade notice: Chromium + a matching chromedriver are now required.** The browser renderer is
> the **only** path that produces saved-eForm fax/archive PDFs — there is no legacy fallback — and
> the webapp now **refuses to start** when the renderer cannot launch. `EFormBrowserRendererStartupValidator`
> probes the renderer from `@PostConstruct` by performing a real headless Chromium launch
> (`EFormBrowserPdfService.verifyRendererReady()` navigates to `about:blank`, then tears the
> browser down); in the default mode a failed probe throws `IllegalStateException`, which aborts
> Spring context initialization so Tomcat refuses to deploy the webapp rather than run it with a
> silently broken eForm print/fax/archive workflow. The mode is selected by
> `eform_pdf_browser_startup_check`:
>
> | Value | Behavior |
> |---|---|
> | `required` (default) | Probe on startup; abort context init (`IllegalStateException`) on failure. |
> | `warn` | Probe on startup; log an ERROR and continue — the failure surfaces at first render instead. |
> | `off` | Skip the probe entirely. Integration-test Spring contexts set this so the gate never launches Chromium in the test JVM. |
>
> Before upgrading, install Chromium/Chrome and a matching `chromedriver` (or confirm the host can
> reach Selenium Manager to download one), and configure `eform_pdf_browser_chromium_path` /
> `eform_pdf_browser_chromedriver_path` per the bullets below. The renderer is unsandboxed by default
> (see "Security operations note"), so the readiness probe launches Chromium with `--no-sandbox` and
> does not fail merely because the host lacks user namespaces. If you have opted into the OS sandbox
> with `EFORM_RENDER_SANDBOX=true` and it cannot start, the readiness probe fails the same as a real
> render would — review that setting before assuming the deployment is broken.

- **Chromium** (or Chrome) on the server. Point the renderer at it with
  `eform_pdf_browser_chromium_path`; without the property, Selenium looks for a system Chrome.
- **chromedriver matching the browser's major version.** Recommended for production (and
  required for air-gapped hosts): install it alongside Chromium and pin
  `eform_pdf_browser_chromedriver_path`. Without the property, Selenium Manager downloads a
  matching chromedriver at first use — acceptable for dev/CI, not recommended for clinics.
- **No Node.js.** The renderer runs entirely in the JVM (Selenium driving Chromium); no Node
  runtime or npm modules are required on the host. (The dev/CI Playwright check scripts under
  `scripts/` are separate test tooling, not part of the renderer.)

## Configuration properties (`carlos.properties` / override file)

| Property | Default | Meaning |
|---|---|---|
| `eform_pdf_browser_base_url` | derived | Loopback base URL the renderer navigates to. Derived from the active request (`scheme://127.0.0.1:localPort/context`), downgrading a proxied `https` scheme to `http` when a TLS-terminating reverse proxy is detected (see "Base URL behind a TLS-terminating proxy" below), or from `project_home` when no request is available. Must resolve to a loopback host — anything else is rejected. |
| `eform_pdf_browser_chromium_path` | unset | Absolute path to the Chromium/Chrome binary. |
| `eform_pdf_browser_chromedriver_path` | unset | Absolute path to a pinned chromedriver. Set this in production. |
| `eform_pdf_browser_startup_check` | `required` | Startup readiness gate mode: `required` aborts webapp startup on a failed renderer probe, `warn` logs and defers the failure to first render, `off` skips the probe (test contexts). See the upgrade notice above. |
| `eform_pdf_browser_strict_network_gate` | `false` | When `true`, restores the original fail-closed posture where any observed off-origin HTTP request, failed render-critical subresource, or severe page-script console error aborts the whole render. The default (`false`) treats those three as advisory (logged, render proceeds) so the legacy eForm corpus — which routinely references off-origin assets, 404s optional helper scripts/images, and emits benign JS errors — still produces a PDF of what painted. Physical egress containment is unaffected: the dead proxy still blocks off-origin HTTP, and the WebSocket/WebTransport gate, the same-origin main-document requirement, and the unparseable-network-evidence gate stay hard fail-closed regardless of this switch. |

Environment: the renderer is **unsandboxed by default** so it starts out of the box on the common
deployment shape (Tomcat as root / a container without unprivileged user namespaces, where
Chromium's own sandbox cannot initialize). In that default posture Chromium runs with `--no-sandbox`
and OS-level containment is delegated to the container boundary; every other renderer control
(loopback-only egress, `--disable-file-system`, WebRTC UDP lockdown, DevTools-over-pipe, sessionless
render token) stays active regardless. Set **`EFORM_RENDER_SANDBOX=true`** to opt back into Chromium's
OS sandbox on a hardened deployment (non-root user + unprivileged user namespaces); when enabled, a
sandboxed launch that cannot start **fails closed** rather than silently degrading. See "Security
operations" below.

### Base URL behind a TLS-terminating proxy

When `eform_pdf_browser_base_url` is unset, `EFormBrowserPdfService.resolveBaseUrl` derives the
renderer's loopback base URL from the in-flight request, and `deriveLoopbackScheme` decides which
scheme to use for that loopback hop:

- **Tomcat-terminated TLS** (no proxy, or a proxy that passes TLS straight through) reports the
  same value for `request.getServerPort()` and `request.getLocalPort()`, so the derived scheme
  matches the request's scheme unchanged.
- **A proxy that terminates TLS upstream** and forwards to Tomcat over plaintext HTTP
  (`RemoteIpValve` / `X-Forwarded-Proto`) makes the request report `scheme=https` while the local
  connector is plaintext. `deriveLoopbackScheme` detects this as `scheme=https` with
  `serverPort != localPort` and downgrades the loopback hop to `http` (logged at INFO). Without
  this downgrade the derived base would be `https://127.0.0.1:<httpPort>`, which fails every
  render, because the local HTTP connector never speaks TLS.

`eform_pdf_browser_base_url` overrides this derivation entirely, and **TLS-terminating-proxy
deployments should set it explicitly** —
`eform_pdf_browser_base_url=http://127.0.0.1:<tomcatPort>/<context>` — rather than relying on the
heuristic.

## Security model

- **Loopback-only egress, enforced twice — scoped to the exact render origin.** Chromium
  launches with a dead proxy (`--proxy-server=http://127.0.0.1:1`) whose bypass list is
  `<-loopback>;<host>:<port>` — the exact render origin only. The `<-loopback>` sentinel is
  load-bearing: it disables Chromium's *implicit* loopback proxy exemption, without which every
  loopback host and port would silently skip the proxy regardless of the explicit list. With it,
  the browser physically cannot reach non-loopback hosts, other loopback aliases (e.g.
  `localhost` when the origin is `127.0.0.1`), *or other local services on different ports* —
  such requests are blocked before they are sent. Independently, the renderer replays
  Chrome's network log and **records** any request that targeted an origin other than the configured
  loopback base. By default this is *advisory* — it drives an operator WARN, not a hard failure,
  because the legacy corpus routinely references optional off-origin assets, and the off-origin
  request was physically blocked anyway. Set `eform_pdf_browser_strict_network_gate=true` to make
  such an observation fail the render. (Live WebSocket/WebTransport egress channels always hard-fail,
  regardless of the switch.)
- **No attachable browser control channel.** Chromium runs with `--remote-debugging-pipe`, so
  DevTools is a parent-process pipe rather than a localhost TCP port another local process could
  connect to.
- **No local file access.** A malicious eForm cannot read server files (`file:///etc/passwd`,
  `/var/lib/OscarDocument/...`) into the rendered PDF. Two layers: Chromium's default cross-scheme
  policy blocks `file://` subresources from the http render origin, and the renderer's request
  gate classifies any non-web scheme (`file:`, `filesystem:`, `chrome:`, `view-source:`, …)
  **other than** the non-network pseudo-schemes `data:`/`blob:`/`about:` as a disallowed request
  (matching `isDisallowedRendererRequestUrl`). That classification is advisory by default (WARN) and
  only fails the render under the strict network gate — Chromium's cross-scheme policy is the primary
  block; the CARLOS scheme gate is a hard backstop only when the strict gate is enabled. These are permitted because they cannot reach
  the network — **not** because their content is inert: `data:`/`blob:` can carry executable script
  and `about:blank` can inherit the opener origin, so in-render script is contained by the egress
  lockdown, not by this scheme gate. The launch config must **never** add `--allow-file-access-from-files` or
  `--disable-web-security` (an inline code invariant and a unit test enforce their absence); the
  FileSystem API is additionally turned off with `--disable-file-system`.
- **`acceptInsecureCerts` is paired with the lockdown.** HTTPS connectors present certificates
  for the clinic's hostname, not `127.0.0.1`; the renderer accepts that mismatch so rendering
  works on TLS deployments. This is safe *only because* egress is loopback-locked — do not
  change either setting without the other.
- **Render-scoped render tokens.** 32 random bytes, 2-minute TTL, bound to one fdid. The grant is
  *peeked* (not consumed) on each redemption so one render can authorize the eForm document plus its
  loopback asset-image subresources — the render browser holds no session, so those sessionless
  fetches authorize themselves with the same grant. The renderer invalidates the token when the
  render finishes; the TTL is the backstop. `EFormImageViewForPdfGenerationServlet` accepts a live
  grant as an alternative to a session **only** on the loopback path and only for the exact shared
  eForm template assets referenced by the rendered form — never patient records.
  `EFormSignatureViewForPdfGenerationServlet` (digital signatures — PHI) **requires** a live
  grant on the loopback path. In-render
  script is contained by the egress lockdown, not the grant: a malicious form can read what its own
  render sees but cannot send it anywhere.
- **Fresh browser per render.** No cookies, storage, or cache can bleed between renders or
  users. `driver.quit()` in a `finally` block tears down chromedriver and Chromium, and the
  caller-owned chromedriver service is stopped afterwards as a backstop
  when quit times out.
- **Bounded concurrency.** At most 2 concurrent renders (30s slot wait, then a clean failure)
  so rendering can never saturate Tomcat's request workers. Every WebDriver/CDP HTTP command is
  additionally client-bounded at 90s (vs Selenium's ~180s default), which sharply cuts down —
  but does not eliminate — how long a wedged Chromium can hold one of the 2 render slots; see
  "Known limitations and tracked follow-ups" for the honest worst-case shape.
- **Page gates and informed approval.** The render gates have three outcomes:
  - **Always hard fail-closed:** a `null` or non-200 same-origin main document; a
    WebSocket/WebTransport creation; unparseable network evidence; or an unreadable browser console
    log. These security and renderer-integrity failures cannot be approved or overridden.
  - **Incomplete document — explicit approval required:** any failed resource type that can affect
    clinical content (`Document`, `Image`, `Script`, `Stylesheet`, `Font`, `Media`, `XHR`, or
    `Fetch`), visible elements excluded from print layout, a stored signature that cannot be
    represented, or an unavailable/failed timer compatibility shim. Rendering stops before returning
    a PDF and raises a typed incompleteness report containing PHI-free categories and counts. The web
    layer informs the clinician, then issues a one-time, 2-minute approval capability bound to the
    session, provider, patient, requested form, operation, and exact issue digest. A retry proceeds
    only when that exact capability is consumed; changed or additional issues require a new approval.
  - **Advisory unless strict mode is enabled:** contained off-origin request attempts, severe
    page-script console errors, and known non-content resource failures. Set
    `eform_pdf_browser_strict_network_gate=true` to turn these signals into hard failures.
  Logs and approval reports contain counts only; resource URLs and page content never cross the
  PHI-safe reporting boundary.
- **PHI-safe diagnostics.** Log lines carry fdid (a separate structured field), the loopback base
  URL (host + context path only — no PHI, and the fdid/token live in a separate path value not
  embedded in it), and counters. URLs inside WebDriver error messages are redacted before logging,
  and the raw WebDriver exception is **not** propagated as the failure cause (only a redacted
  message is), so a downstream handler that logs the throwable cannot re-emit an unredacted URL.
  This covers render-path failures **and** browser launch failures alike; the redacted
  `Chromium startup failure detail` log line is the diagnostic record for the latter.

### Security operations note

The containment layers split responsibility: the egress lockdown and fail-closed gates contain
**malicious page content** (JS-level attacks — including WebSocket/WebTransport egress, which the
gate rejects alongside HTTP), while an **OS-level sandbox** contains native browser exploits (an
RCE in Chromium's renderer). Because the renderer processes clinic-authored content, treat the
pinned Chromium and chromedriver like any other security-patched dependency: keep them updated.
The token design means the render browser holds no session cookies or credentials, so a
compromised render exposes only the content of the form being rendered.

**Selenium is not an isolation layer.** It only launches `chromedriver` → `chrome`; the
chroot / namespace / seccomp confinement is Chromium's *own* sandbox (or the container). By default
this renderer runs **unsandboxed** (`--no-sandbox`) so it starts on the common deployment shape where
Chromium's own sandbox cannot initialize (Tomcat as root, or a container without unprivileged user
namespaces); OS-level containment is then delegated to the container boundary. Operators who can run
the renderer confined should choose one of the two paths below — ideally both:

1. **Chromium's own sandbox (preferred; opt-in via `EFORM_RENDER_SANDBOX=true`).** Chromium confines
   the renderer with a Layer-1 namespace sandbox plus a Layer-2 seccomp-bpf syscall filter. In the
   legacy **setuid** sandbox (the `chrome-sandbox` helper) Layer 1 `chroot()`s the renderer into an
   empty directory inside new PID + network namespaces. The modern **unprivileged user-namespaces**
   variant achieves equivalent confinement using user + PID + network namespaces without any setuid
   helper, and needs **no root** — only a kernel with **unprivileged user namespaces enabled** (the
   exact knob is distro-specific: on Debian/Ubuntu `sysctl kernel.unprivileged_userns_clone=1`;
   mainline/other distros gate it differently, e.g. `user.max_user_namespaces`, and many enable it by
   default) — with the renderer running as a **non-root** user (Chromium refuses its sandbox as root).
   Set `EFORM_RENDER_SANDBOX=true` to enable it; if the sandbox then cannot start, the render **fails
   closed** with a message telling the operator to enable user namespaces / run as non-root, or to
   unset the variable and fall back to path 2.
2. **Make the container the boundary (the default).** With `EFORM_RENDER_SANDBOX` unset the renderer
   launches with `--no-sandbox`. This is acceptable **only if the container itself is the isolation
   boundary**: dedicated non-root UID, `cap-drop=ALL` (no `CAP_SYS_ADMIN`),
   `--security-opt no-new-privileges`, read-only root filesystem, `tmpfs` for `/dev/shm` and the
   renderer temp root mounted `noexec,nosuid,nodev`, a seccomp profile (Docker default or a
   Chrome-tuned one), and PID / CPU / memory limits. (Landlock filesystem confinement is **not**
   configured by CARLOS or applied automatically by a newer kernel — it would only apply if Chromium
   engages it itself or you add an explicit Landlock policy, so do not assume this path gains
   filesystem confinement for free.) The renderer logs a one-time `WARN` per JVM while running
   unsandboxed, so the posture is visible in ops logs without flooding them.

Because unsandboxed is the default, deploying the renderer where it can be reached by clinic-authored
eForms **without** a real container boundary leaves the browser without OS-level containment — enable
`EFORM_RENDER_SANDBOX=true` (with user namespaces + non-root) or ensure the container hardening above.

(The dev/CI Playwright check scripts under `scripts/` are separate test tooling; they run as root
in CI and use their own `EFORM_RENDER_ENABLE_CHROMIUM_SANDBOX` opt-in — not this production knob.)

## Known limitations and tracked follow-ups

These are inherited from the original browser-render feature (PR #3164) and are intentionally
**not** changed here, because a code change would be either behavior-breaking for rendering or an
operational configuration matter:

- **Output is a native text-layer PDF, sized by measured page geometry.** Pages carry selectable/
  searchable text and are smaller than the equivalent image-only PDF. The page boxes come from the
  authored page divs' measured content boxes (see "Native print-to-PDF with authored @page sizing"
  above), not the physical paper — a form whose `@media print` rules or absolute layout differ
  sharply from its on-screen layout will print per those print rules. Because there is no runtime
  fallback to the old raster path, a **staging soak against a real form corpus** is recommended
  before a production cutover so any form with surprising print-media behavior is caught early.
- **eForm HTML rewrites run on display and save, not only on render.** `EForm.setContextPath()` /
  `getFormHtml()` normalize asset URLs on the ordinary display and save paths as well as the render
  path, so saving can persist transformed HTML and perturb the `sameform` de-duplication. The render
  path depends on these rewrites; narrowing them to render-only risks breaking rendering and belongs
  in an upstream change with full display/save regression coverage. Tracked as a follow-up.
- **Fax preview of page-image eForms needs `_edoc` read.** `CoverPage.jsp` builds the inline
  preview page images via `createCacheVersion2`, which requires `_edoc` read. Fax users without
  `_edoc` still get a working **Open PDF** link (soft degradation) — this is an operator
  role-configuration note, not a defect.

Two further limitations are operational realities of the later hardening work (proxy-aware
base-URL derivation, the hard startup gate, and per-command WebDriver timeouts), not carryovers
from PR #3164:

- **A rolling upgrade can strand an already-open fax preview.**
  `FaxManagerImpl.resolveAndValidateFilePath` accepts a preview's temp file path only when
  `PathValidationUtils.isInApplicationTempDirectory` recognizes its root/first-segment
  combination; anything else falls through to the permanent-document-store containment check and
  is rejected. If a release changes which temp-root segment names are recognized under which root,
  a preview minted by the pre-upgrade JVM and sent after the app restarts onto the new release
  fails this check — the file can still exist on disk, but the running release no longer
  recognizes its containing root as one it owns. The failure surfaces as the generic per-job
  status `File missing on local storage or invalid file path.`, with no indication that a restart
  is the cause. There is no cross-restart carry-forward for an open preview: the user re-opens the
  preview (same fdid), which mints a fresh temp path validated by the running release, and
  resends.
- **The 90-second render timeout is a cooperative budget, not a hard preemptive cutoff.**
  `RENDER_TIMEOUT` is only checked between browser commands (`checkDeadline`, called before
  `settle()` and before the `Page.printToPDF` call) — a command already dispatched is never cancelled
  mid-flight. So a genuinely wedged Chromium (one that stops answering the WebDriver protocol
  entirely, rather than erroring cleanly) can consume close to the full 90-second budget as
  legitimate elapsed time, then hang the one command already in flight when the deadline is
  crossed for another full `WEBDRIVER_COMMAND_READ_TIMEOUT` (90s), and `driver.quit()` in the
  render's `finally` block is itself one more WebDriver command bound by that same 90-second client
  read timeout. Worst case that is roughly three 90-second spans — **up to ~4.5 minutes** — before
  `stopServiceQuietly`'s process-level kill actually frees the render
  slot. `MAX_CONCURRENT_RENDERS=2` bounds the blast radius to at most 2 stuck slots at a time, not
  the whole renderer, but a wedged browser is not guaranteed to fail within the nominal 90-second
  budget.

## Output contract

The rendered PDF is written beneath the managed temp root
(`$CATALINA_BASE/work/carlos/eform-browser-pdf-temp`, or a namespaced `java.io.tmpdir`
fallback), which fax path validation (`FaxManagerImpl`) already whitelists. Pages are native
Chromium print output; the injected `@page` sizes are expressed in CSS px, which Chromium converts
to PDF points at 96 px → 72 pt. Callers own cleanup of the returned file.

## Application-temp purge job

`ApplicationTempPurgeJob` (`io.github.carlos_emr.carlos.managers`) is the backstop for whatever
cleanup misses its own crash/cancellation path — a Spring-managed daemon `Timer` sweep, modeled on
`FaxSchedulerJob`, that removes orphaned PHI-bearing temp artifacts left behind by generation/
preview flows. Each cycle sweeps two locations:

- **Application temp root** (`<java.io.tmpdir>/carlos-temp`, see
  `PathValidationUtils.APPLICATION_TEMP_ROOT_NAME`) — every direct child (file or directory,
  regardless of name) older than the max age is removed. This root is exclusively CARLOS-owned
  (`NioFileManagerImpl.saveTempFile`'s `tempPDF*` subdirectories and `createTempFile`'s
  `tempDirectory*` subdirectories, the latter used by `ImportDemographicDataAction42Action` to
  stage multi-patient demographic import files), so there is no name-prefix gate — any old-enough
  direct child is a purgeable orphan.
- **Document preview cache** (`document_cache`, see
  `NioFileManagerImpl.resolveDocumentCacheDirectory()`) — stale `*.png` files older than the max
  age are removed. This is the backstop for the flush-vs-writer race: a cancelled preview whose
  render lands after a successful `removeCacheVersions` flush can leave one PHI-bearing page PNG
  behind.

Properties (`carlos.properties` / override file):

| Property | Default | Meaning |
|---|---|---|
| `carlos_temp_purge_interval_ms` | `3600000` (one hour) | Sweep interval. A configured value of `0` disables the sweep entirely (no timer is started). Negative or unparsable values fall back to the default. |
| `carlos_temp_purge_max_age_hours` | `24` | Age threshold: entries last modified before `now - max_age_hours` are removed. Non-positive or unparsable values fall back to the default — unlike the interval property, there is no "disable" value here. |

The first sweep runs 3 seconds after Spring finishes wiring the bean (matching `FaxSchedulerJob`'s
pattern of not sweeping synchronously from `@PostConstruct`), then on the configured interval.
Symlinked children are never followed or deleted — they are skipped and logged at WARN regardless
of age, since a symlink under an application-owned temp root is itself suspicious. Every deletion
target is re-validated with `PathValidationUtils.validateExistingPath` immediately before removal,
closing the check-then-use gap between listing a directory and deleting an entry. Because this
class is component-scanned into the production Spring context, `initialize()` must never throw:
property-parsing failures fall back to defaults (logged at WARN), a missing temp root or cache
directory is treated as "nothing to sweep yet," and `runCycle()` catches every `Throwable`
category so one bad cycle (a transient I/O failure, a JVM error) never cancels the timer for
subsequent cycles.

## Verification

- Unit tests: `mvn test -Dtest=EFormBrowserPdfServiceUnitTest,EFormRenderTokenServiceUnitTest,EFormBrowserRenderPageServletUnitTest,EformViewForPdfGenerationServletUnitTest` (the last is the legacy session-gate servlet, kept alongside the browser render servlet)
- End-to-end smoke (skips cleanly without a browser):
  `mvn test -Dtest=EFormBrowserPdfServiceSeleniumSmokeIntegrationTest` — serves
  `scripts/fixtures/eform/test-pattern.html` over loopback and drives the native print path,
  asserting the PDF page count matches the authored `pageN` divs and that a real text layer is
  present (the raster path this replaced produced zero extractable text); a companion case prints a
  free-flow (no-`pageN`-div) letter and asserts a text-layer `%PDF` with no injected `@page` size.
