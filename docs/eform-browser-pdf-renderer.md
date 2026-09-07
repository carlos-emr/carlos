# eForm Browser PDF Renderer (Selenium + Headless Chromium)

CARLOS renders saved eForms to PDF with a real browser engine because eForms are CSS- and
JavaScript-dependent documents — absolutely-positioned fields over scanned background images,
clinic-authored layout scripts, APCache-populated content, `@media print` rules — that pure-Java HTML
renderers cannot reproduce faithfully. The renderer is driven entirely from the JVM by Selenium —
**no Node.js runtime is installed, bundled, or executed anywhere on the server**.

The render surface is a *passive* snapshot: the WYSIWYG editor and the other interactive control
libraries are stripped before the page is printed (see "The render surface is passive"). A browser is
needed for faithful layout, not to run the editor.

## Architecture

```text
EformDataManagerImpl.createEformPDF
  └─ EFormBrowserPdfService.renderSavedEformPdf(loggedInInfo, fdid)
       ├─ loads the saved eForm and checks _eform READ for its demographic
       ├─ mints a render-scoped render token (EFormRenderTokenService, 2-min TTL)
       ├─ launches headless Chromium via Selenium ChromeDriver (fresh browser per render)
       ├─ navigates over loopback to /EFormViewForPdfGenerationServlet?fdid=…&browserRender=true&renderToken=…
       │    ├─ servlet exchanges the one-time bootstrap token for a host-only,
       │    │    HttpOnly, SameSite=Strict CARLOS_EFORM_RENDER cookie
       │    ├─ composes the passive render surface (EFormRenderPdfHtmlComposer):
       │    │    Letter replacement → decode + harden → signature splice →
       │    │    strip editor/controls + inject dependency profile and shim →
       │    │    legacy image-path rewrites → drop absent optional assets
       │    └─ grants only exact referenced static/image/signature/APCache resources
       ├─ emulates print media, stabilizes the page (fonts, images, animation frames),
       │    measures each authored page div's content box, and sizes the CSS @page boxes to match
       └─ prints the page to a native, text-layer eform-browser-render-*.pdf via CDP Page.printToPDF
```

The renderer capability replaces session forwarding: Chromium never receives `JSESSIONID`, the
CARLOS session, or a CSRF token. The public service entry point loads the saved record and performs
patient-scoped `_eform` READ authorization before minting the bootstrap capability. The bootstrap
token appears only on the initial loopback navigation; subresources use the short-lived renderer
cookie and do not receive it in their URLs.

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

> **Memory footprint is deliberately bounded.** The launch options cap each renderer's V8 heap
> (`--js-flags=--max-old-space-size=256`) and the renderer-process fan-out
> (`--renderer-process-limit=4`, all render content is same-origin loopback), and drop the GPU
> process (`--disable-gpu`; headless print rasters in software). On the `.deb`, the
> `carlos-emr-chromedriver` unit additionally carries a cgroup ceiling for the whole browser tree
> (`MemoryHigh=1G`, `MemoryMax=1536M`): under pressure the kernel throttles and, at the limit,
> OOM-kills **inside the unit** — a runaway form's render fails (retryably, via the normal
> fail-closed render error, with `Restart=always` recycling the driver) instead of the browser
> squeezing the EMR beside it. Size hosts for steady state: up to `MAX_CONCURRENT_RENDERS` (2)
> concurrent Chromium instances of roughly 150–300 MB each, plus chromedriver (~20 MB), on top of
> the Tomcat JVM. Deployments that raise concurrency must raise the unit ceiling to match.

> **Containerized deployments MUST run an init process (zombie reaping).** Each render launches a
> Chromium process tree; on an abnormal teardown (a killed chromedriver, a deploy mid-render, an
> OOM-killed helper) orphaned helper processes reparent to PID 1 and become zombies. A container
> whose PID 1 is not an init (e.g. a bare `tail -f /dev/null` or the JVM itself) never reaps them,
> and each zombie permanently consumes a pids-cgroup slot — a long-lived deployment can exhaust
> `pids.max` and every later browser launch fails with "unable to create native thread". Run the
> container with `docker run --init` (or tini/dumb-init as PID 1, or a systemd-managed service) so
> orphans are reaped.

> **Runbook: provision the browser before using eForm PDF workflows.** Run a chromedriver as its
> own service and point `eform_pdf_browser_service_url` at it (the `.deb` does both via
> `carlos-emr-eform-renderer`); set `eform_pdf_browser_chromium_path` to the browser binary the
> driver should launch. CARLOS never spawns a chromedriver itself. It probes the renderer at
> startup and logs a warning if it is unavailable, but continues deploying so other application
> workflows remain available. Confirm `eForm browser renderer startup check passed.` in the log
> before relying on eForm print/fax/archive.

> **Upgrade notice: Chromium + a matching chromedriver are now required.** The browser renderer is
> the **only** path that produces saved-eForm fax/archive PDFs — there is no legacy fallback.
> `EFormBrowserRendererStartupValidator` probes the renderer from `@PostConstruct` by performing a
> real headless Chromium launch (`EFormBrowserPdfService.verifyRendererReady()` navigates to
> `about:blank`, then tears the browser down). A failed probe logs a warning and allows application
> startup to continue; eForm print/fax/archive remains unavailable until the renderer is fixed. The
> probe is controlled by
> `eform_pdf_browser_startup_check`:
>
> | Value | Behavior |
> |---|---|
> | `warn` (default) | Probe on startup; log a WARN and continue when the renderer is unavailable. |
> | `required` | Legacy compatibility value; behaves like `warn` and no longer aborts startup. |
> | `off` | Skip the probe entirely. Integration-test Spring contexts set this so the gate never launches Chromium in the test JVM. |
>
> Before upgrading, install Chromium/Chrome and a matching `chromedriver` running as a service,
> and configure `eform_pdf_browser_service_url` / `eform_pdf_browser_chromium_path` per the
> bullets below — `eform_pdf_browser_chromedriver_path` is retired and ignored (a startup WARN
> names it if still set). The renderer is unsandboxed by default
> (see "Security operations note"), so the readiness probe launches Chromium with `--no-sandbox` and
> does not fail merely because the host lacks user namespaces. If you have opted into the OS sandbox
> with `EFORM_RENDER_SANDBOX=true` and it cannot start, the readiness probe fails the same as a real
> render would — review that setting before assuming the deployment is broken.

- **Chromium** (or Chrome) on the server. Point the renderer at it with
  `eform_pdf_browser_chromium_path`; without the property, Selenium looks for a system Chrome.
- **A RUNNING chromedriver, matching the browser's major version.** CARLOS connects to it over
  loopback (`eform_pdf_browser_service_url`); it does not launch one, and there is no
  Selenium Manager fallback to download a driver at first use. On the .deb this is the
  `carlos-emr-chromedriver` service, which the `carlos-emr-eform-renderer` package installs
  and starts. Elsewhere, run one yourself before the webapp deploys.

  **Why it is a separate process and not a child of the JVM.** Chromium sandboxes its
  renderers with an unprivileged user namespace. A chromedriver the application spawns
  inherits the application service's cgroup and confinement, and `carlos-emr.service` denies
  namespace creation because it runs a PHI-handling Tomcat. Measured on a clean 26.04 VM:
  sandboxed Chromium started directly as an unprivileged user works in about a second;
  spawned by the JVM under that unit it fails every boot on the 30s startup budget. Moving
  the browser to its own unit is what lets it be sandboxed without loosening the EMR's own
  hardening.
- **No Node.js.** The renderer runs entirely in the JVM (Selenium driving Chromium); no Node
  runtime or npm modules are required on the host. (The dev/CI Playwright check scripts under
  `scripts/` are separate test tooling, not part of the renderer.)
- **Managed eForm assets update themselves.** `EFormAssetDeployer` keeps `editControl2.js`, the
  bundled JS libraries and `BNK.png` at the shipped version, replacing the deployed copy on startup
  whenever its bytes differ. No manual delete-and-restart step is required. Clinic-customizable
  assets (`blank.rtl`, `editor_help.html`, the lab decision-support stubs) are still deployed once
  and then never touched.
- **Apply the RTL attachment-route migration.**
  `database/mysql/updates/update-2026-06-29-rtl-attachment-route-fix.sql` rewires the stored Rich
  Text Letter template off the dead `../eform/attachEform.jsp` path. Without it the attach popup
  404s, which reads like a routing regression rather than a missing migration.

## Configuration properties (`carlos.properties` / override file)

| Property | Default | Meaning |
|---|---|---|
| `eform_pdf_browser_base_url` | derived | Loopback base URL the renderer navigates to. Derived from the active request (`scheme://127.0.0.1:localPort/context`), downgrading a proxied `https` scheme to `http` when a TLS-terminating reverse proxy is detected (see "Base URL behind a TLS-terminating proxy" below), or from `project_home` when no request is available. Must resolve to a loopback host — anything else is rejected. |
| `eform_pdf_browser_chromium_path` | unset | Absolute path to the Chromium/Chrome binary. |
| `eform_pdf_browser_service_url` | `http://127.0.0.1:9515` | URL of an **already-running** chromedriver. CARLOS connects to it and never launches one. Must be `http` (chromedriver serves plaintext only) with an explicit port on a loopback host — anything else is rejected at startup. A path component is permitted and is the chromedriver `--url-base` prefix, which the .deb uses as a capability token. Unreachable fails the render closed; there is no fallback to launching a browser. |
| ~~`eform_pdf_browser_chromedriver_path`~~ | — | **Removed.** CARLOS no longer spawns chromedriver, so a path to one has nothing to launch. See `eform_pdf_browser_service_url`. |
| `eform_pdf_browser_startup_check` | `warn` | Startup readiness mode: `warn` probes and logs without aborting startup, legacy `required` behaves the same way, and `off` skips the probe (test contexts). See the upgrade notice above. |
| `eform_pdf_browser_strict_network_gate` | `false` | When `true`, restores the original fail-closed posture where any observed off-origin HTTP request, failed render-critical subresource, or severe page-script console error aborts the whole render. The default (`false`) treats those three as advisory (logged, render proceeds) so the legacy eForm corpus — which routinely references off-origin assets, 404s optional helper scripts/images, and emits benign JS errors — still produces a PDF of what painted. Physical egress containment is unaffected: the dead proxy still blocks off-origin HTTP, and the WebSocket/WebTransport gate, the same-origin main-document requirement, and the unparseable-network-evidence gate stay hard fail-closed regardless of this switch. |
| `eform_pdf_browser_saved_view_profile_enabled` | `true` | Enables the saved-view dependency profile and renderer-only APCache bridge. Set `false` only as a temporary compatibility rollback while investigating a clinic form; capability/session isolation and all browser containment remain enabled. |

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
  `/var/lib/CarlosDocument/...`) into the rendered PDF. Two layers: Chromium's default cross-scheme
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
- **Render-scoped capability session.** A 32-byte bootstrap token and independent 32-byte cookie
  handle have a 2-minute TTL and are bound to one fdid/provider. The initial loopback document
  exchanges the token for `CARLOS_EFORM_RENDER`; token replay without the already-bound cookie is
  rejected. The cookie is host-only, HttpOnly, SameSite=Strict, scoped to the application path, and
  Secure on HTTPS. It is not a CARLOS `HttpSession` and carries no user identity or CSRF authority.
  The render lease invalidates both handles on every completion path; TTL is only the backstop.
- **Exact resource grants.** Before serving the composed document, the renderer records exact local
  script, stylesheet, font, image, signature, and literal APCache references. LoginFilter permits
  only loopback GET/HEAD requests for those exact passive static paths. Dedicated image/signature
  servlets and the read-only APCache bridge independently require the live cookie and their exact
  asset/id/key grant. APCache derives patient/provider identity from the saved fdid, rejects
  appointment-dependent APs when no appointment context exists, and never accepts browser-supplied
  patient/provider identities.
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
  - **Always hard fail-closed:** a `null` or non-200 same-origin main document; navigation away from
    the exact authorized renderer URL; any observed non-GET/HEAD request; a
    WebSocket/WebTransport creation; unparseable network evidence; or an unreadable browser console
    log. These security and renderer-integrity failures cannot be approved or overridden. CSP also
    sets `form-action 'none'`, and pre-navigation shims contain alert/confirm/prompt/window.open.
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

### The render browser runs out of process, as its own account

CARLOS connects to a chromedriver it does not own. Two consequences worth stating plainly.

**A new local attack surface.** chromedriver listens on a loopback TCP port with **no
authentication**, and a WebDriver new-session request can name the browser binary and its arguments.
An exposed chromedriver is therefore an arbitrary-code-execution service running as whatever account
started it. Three mitigations, in the order they actually matter:

1. **A dedicated, group-less account.** On the .deb the browser runs as `carlos-render`, which is in
   no group — deliberately not `carlos`. Reaching the port yields a browser owning nothing: no
   patient documents, no database credentials, no read on `carlos.properties`. Running it as the
   application account would instead hand over the uid that owns the document store. This is only
   possible because the rendered PDF returns inline over CDP, so the two sides share no filesystem.
2. **`--url-base` as a speed bump — and not more than that.** A random path prefix generated at
   install. Be clear about its limits: systemd expands it into the process's `argv`, and
   `/proc/<pid>/cmdline` is world-readable, so **any local uid can read the token**. It raises the
   bar against blind scanning; it does not bound the port. Anyone reasoning about this design should
   treat control 1 as the boundary and this as hygiene. The service does refuse to start without one,
   because an empty `--url-base` silently moves every endpoint to the bare root.

   If a site needs the port genuinely closed to other local accounts, the available control is a
   firewall owner-match (e.g. an nftables rule on `lo` dport matching `meta skuid`), not anything
   chromedriver offers. AppArmor cannot express it on this kernel (`af_unix` only, no `af_inet`).
3. **chromedriver's loopback-only default, left alone.** `--allowed-ips` and `--allowed-origins` are
   deliberately NOT passed: the defaults are already correct, and passing either with an empty value
   historically means "allow everything".

AppArmor cannot help here. On this kernel it mediates only `af_unix`, not `af_inet`, so there is no
rule expressing "deny this port to that user" — only "deny all inet sockets", which is useless for a
web server. The account split is the control.

**Teardown changed.** The application can no longer kill the browser process, because it does not own
it. A wedged session is ended by `quit()`, escalating to a targeted `DELETE` of that exact session id
over a fresh short-deadline connection; the id is captured at session creation because
`RemoteWebDriver.quit()` clears its own even when the quit fails. The backstop of last resort is now
`systemctl stop carlos-emr-chromedriver`, which tears down the driver and every browser it launched.

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

## The render surface is passive

The composed render document is a snapshot of saved clinical content, never a working copy of the
editor. The interactive viewer and the render surface serve the *same* stored HTML, but they are two
different host pages with two different contracts, and the render surface implements only the
passive half.

### What the composer removes, and why removal is mandatory

`removeInteractiveEditorContent` strips, matching on both the script URL and the `imagefile=` asset
name (clinic forms reference these either way):

| Removed | Reason |
|---|---|
| `editControl2.js` / `editControl.js` | The WYSIWYG editor. Boots a contenteditable iframe. |
| `insertEditControl()` call | The editor bootstrap, neutralized inside its inline block so surrounding clinic logic survives. |
| `printControl.js`, `faxControl.js` | Print/fax dialogs — no meaning in a PDF. |
| `imageControl.js` | Editor image insertion UI. |
| `signatureControl` (any URL form) | Signature *capture*. Stored signatures are spliced server-side. |
| `eform_floating_toolbar` | Save/Print/Download/Fax/Attach toolbar. |
| `#edit-controllers`, `.edit-controllers` | The toolbar mount point the form itself renders. |

`APCache.js` is deliberately **kept** — it populates clinical field content, which is why the
capability-scoped APCache endpoint exists at all.

This is a correctness requirement, not tidiness. The render surface's `connect-src` admits only the
APCache endpoint, so any other XHR the editor issues — the letter-template list
(`/eform/efmformrtl_templates`), the attachment sidebar poll (`/eform/displayAttachedFiles`) — is
refused, counts as a failed *content* resource, and the completeness gate then blocks the entire
PDF. Before the strip, a clinician could be denied their letter because a toolbar dropdown could not
populate.

### The shim for globals that lived inside the editor

Removing the editor is not free: `Start()` (the Rich Text Letter's body `onload`) and `cache` are
both **defined inside `editControl2.js`**, and clinic forms call `cache.addMapping({...})` inline at
parse time. Stripping the editor without replacing them produced two `ReferenceError`s per render →
two severe console errors → gate blocks. `installStrippedEditorShim` therefore injects a guarded
shim **immediately after the `APCache.js` script tag** (so `createCache` exists, and so the shim runs
before any inline form script):

- `cache` is rebuilt **for real** from APCache's `createCache` when APCache is present, so forms that
  populate fields through it still populate them. Only when APCache is absent does it fall back to an
  inert stub.
- Editor sinks that wrote into the now-absent contenteditable iframe — `doHtml`, `printKey`,
  `editControlContents`, `seteditControlContents`, `parseTemplate`, `updateAttached`,
  `fetchAttached`, `saveRTL`, `maximize`, `viewsource`, `usecss`, `collapseFooter`,
  `consultantSearch`, `getMeasures`, `checkKeyResponse`, `Start`, `insertEditControl` — become
  no-ops.
- Every assignment is `w.x = w.x || …` guarded, so a form shipping its own implementation keeps it.

A separate inline stub neutralizes `signatureControl.initialize` the same way, and the
`${oscar_signature_code}` marker is blanked rather than passed to `EForm.setSignatureCode()` (which
would mint preview/write state on a read-only path).

### Optional assets that are referenced but not deployed

`eformGenerator.jsp` injects a `stamps.js` tag into **every generated eForm**, while
`EFormAssetDeployer` intentionally never deploys `stamps.js` (it holds clinic-specific signature-image
mappings administrators create themselves). On a fresh install that tag 404s, and `Script` is a
content-critical resource type — so the completeness gate blocked the PDF over an asset that is
absent by design. `removeAbsentOptionalStamps` drops the tag only when the file is genuinely absent
from the eForm image directory; if the directory cannot be read, the tag is left in place so the
browser gate still reports the real failure rather than silently hiding it.

### Stored letters are decoded before they are spliced

`saveRTL()` entity-encodes the letter (`&`, `"`, `<`, `>`, `'`) so it survives inside a textarea
value. `decodeStoredLetter` reverses that in the same order `editControl2.js` uses — `&amp;` LAST,
so text a clinician actually typed as `&lt;` stays text. Diverging from that order makes the PDF and
the on-screen editor disagree about the same stored letter; change both together or neither. Without
the decode the PDF prints the clinician's own markup as visible text (`<h3>Consultation Letter</h3>`
instead of a heading) — a clean render, gate-green, and clinically useless.

Decoding turns previously-inert markup into live markup on a surface that permits
`'unsafe-inline'`, so `hardenLetterHtml` strips event-handler attributes and `javascript:` URLs
(including whitespace/NUL-obfuscated forms). Script *elements* are kept on purpose: the stored
signature's geometry is read out of the letter's own `signatureControl.initialize({...})` call, and
clinic letters carry image-path fixups inline. A full allow-list sanitizer removes both and silently
costs the clinician a signature — that was tried and reverted. Execution is contained rather than
forbidden: no session or cookie in the render browser, egress blocked at the network layer,
`connect-src` limited to the APCache endpoint, and any non-GET request fails the render gate.

### Ordering inside the composer is load-bearing

`buildPdfHtml` runs in a fixed order and each step depends on the previous one:

1. `applyLetterHtml` — whole-document replacement, so every later substitution must follow it.
2. `applySignatureHtml` — reads geometry from the letter's own `signatureControl.initialize(...)`.
3. `applyRendererViewProfile` — strips the editor, injects dependencies + the shim + hidden inputs.
4. Legacy image-path rewrites (`.do` spelling first, then `${oscar_image_path}`, then
   `/eform/displayImage`) — these must run after letter/signature injection so they also cover the
   freshly injected markup.
5. `removeAbsentOptionalStamps`, then grant population (`authorizeAssets`, `authorizeApKeys`).

### Corpus exposure (why this mattered)

Measured against a 55-form development corpus:

| Metric | Count |
|---|---|
| eForms total | 55 |
| Reference a control script (`printControl` / `faxControl` / `imageControl` / `APCache`) | 23 |
| Reference `printControl.js` **and carry no real jQuery `<script src>`** | **21** |
| Reference `signatureControl` | 3 |
| Reference `stamps.js` | 1 |

```sql
SELECT COUNT(*) FROM eform
 WHERE form_html LIKE '%eforms/printControl.js%'
   AND form_html NOT REGEXP 'src="[^"]*jquery[^"]*\\.js';
```

Roughly 38% of that corpus depended on host-page-injected libraries the render surface never
provided; a production corpus is likely worse, since these control scripts are what
`eformGenerator.jsp` emits for generated forms. An earlier attempt to fix this by mirroring the
viewer's chrome (injecting jQuery) made renders get *further* and still produce nothing — the editor
then booted and its XHRs hit `connect-src`. Stripping the editor is the fix; the saved-view
dependency profile (jQuery, jQuery UI, Bootstrap) remains for clinic form code that expects it.

## What the render gates cannot see

The completeness gate reasons about **resource loading**. Two defects reached rendered PDFs with a
completely clean gate, because both loaded successfully and failed afterwards — in paint order and in
encoding:

- **Background images lost when a background was declared on `<html>`.** `PREPARE_PRINT_JS` used to
  set `html { background: white }`. With it, a form whose scanned background is an `<img>` at
  `position:absolute; z-index:-1` — the standard eForm idiom — printed with a blank background;
  removing that one statement restored it, with the page otherwise byte-identical. The precise
  Chromium paint-order reason has **not** been established, so this is recorded as an empirical
  rule: never declare a background on `<html>` in the print preparation script. Chromium already
  prints white paper. What makes it dangerous is that the image still returns HTTP 200, so no gate
  can see the loss. `shouldNotPaintRootBackground_whenPreparingPrint` is a string tripwire only —
  the real guard is opening the PDF.
- **Letters printed as escaped markup** (see the decode section above).

Neither is detectable from network evidence, console errors, or `%PDF-` plus a byte count. The
smoke-test runbook therefore requires opening the produced PDF and looking at it — see
`docs/ui-tests/eform-pdf-render-smoke-test.md`, "Open the produced PDF and look at it".

### Off-page content is blocking unless the form marks it as decoration

Content authored **outside** every `<div id="pageN">` — before the first page div or after the last —
cannot be printed by the authored-page geometry, so the geometry pass hides it
(`.carlos-render-nonpage`) and records it. How it is recorded decides whether a clinician ever finds
out:

- **Blocking** (`excludedContentElements`): counted, measured, and fed to `withholdsDocument`, so the
  document is withheld pending informed approval.
- **Advisory** (`decorativeExcludedElements`): disclosed in the completeness report and logged at
  INFO, but it never withholds anything.

Classification is **opt-in**. An off-page element is treated as decoration only when it carries an
explicit marker:

```html
<div class="carlos-print-decoration">College of ... — license #12345</div>
<div data-carlos-print-decoration>Printed from CARLOS EMR</div>
```

Anything unmarked stays blocking, including plain text. This is deliberate: position in the document
cannot establish that content is non-clinical. An earlier heuristic inferred decoration from the mere
absence of a control or media element, with no length or content test, so a `<div>` of clinical prose
placed outside the page divs was silently dropped from every printed, faxed and archived PDF with
only an advisory note. If a form legitimately carries an off-page badge, masthead or boilerplate
disclaimer, mark it; do not rely on its position.

A marker is an assertion about boilerplate, not a licence to drop a field. A marked element that
still contains a control (`input, textarea, select, button, [contenteditable]`) or a media element
(`img, canvas, svg, video, iframe, object, embed`) stays blocking regardless.

## Saved-letter round trip (interactive viewer)

This is viewer-side, not renderer-side, but it determines what the renderer is given and it silently
destroyed content twice:

- `editControl2.js` called `seteditControlContents(...)` **before** enabling `designMode`. That
  helper only writes into the iframe when its document is already in `designMode`, so the write was a
  no-op and reopening a saved letter showed an empty editor. `designMode` is now set first, and the
  fallback branch refuses to assign `.value` to an iframe (it logs instead of discarding content).
- `DOMPurify` ships in the webapp (`/library/dompurify/purify.min.js`) but was never loaded on the
  eForm host pages, so the editor's `sanitizeHtml` gate returned `null` and fell back to
  `textContent`. The letter then displayed as escaped text, and the next save stored it
  **double-encoded**. Both `efmshowform_data.jsp` and `efmformadd_data.jsp` now load it.

Either failure looks the same to the clinician — an empty or escaped editor — and in both cases the
toolbar's save-and-download persisted that empty editor over the stored letter. Regression coverage
is the round-trip step in the smoke-test runbook plus
`npm run test:eform-rtl-attachment-behavior-playwright`.

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
- **A clinic cannot customize a managed eForm asset.** `MANAGED_ASSETS` (the editor engine, the JS
  libraries, `BNK.png`) are replaced on startup whenever they differ from the shipped bytes, so a
  local edit is reverted at the next restart. That is the intended contract — these are application
  code — but it means a clinic with a genuine need to patch the editor has no supported path. If one
  ever appears, the fix is provenance tracking (record the digest CARLOS deployed; replace only when
  the on-disk copy still matches it), not a return to skip-if-exists, which silently pinned every
  install to whatever version it first received.
- **`stamps.js` is still referenced by every generated eForm.** `removeAbsentOptionalStamps` hides
  the consequence on the render path only; the interactive viewer still requests it and still logs a
  404 in the browser console on a fresh install. Shipping a default empty `stamps.js` would not
  clobber clinic files (the deployer's exists-guard already protects them), and not emitting the tag
  when no stamps exist is the other option; neither is done here.
- **Viewer-relative URLs built inside JavaScript strings are not re-anchored.**
  `EForm.rewriteViewerRelativeAssetReferences` re-anchors `../` references in element attributes, but
  a URL assembled in a script literal (`var url = "../eform/displayAttachedFiles"`) resolves against
  the render page's shallower path and misses the context. Stripping the editor makes this moot for
  the known cases; it would resurface for any clinic script that fetches by relative path.

Two further limitations are operational realities of the later hardening work (proxy-aware
base-URL derivation, the advisory startup probe, and per-command WebDriver timeouts), not
carryovers from PR #3164:

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

## Reading the logs

The renderer spans two services, so a render failure can leave nothing at all in the application
log. Check both, in this order:

```
# 1. Did the application manage to drive the browser?
sudo carlos-ctl logs | grep -i renderer

# 2. What did the browser itself say? Separate unit, separate journal.
sudo systemctl status carlos-emr-chromedriver
sudo journalctl -u carlos-emr-chromedriver -n 50
```

At startup the application probes the browser exactly once and reports the outcome. That report is
visible at default verbosity **only because `log4j2.xml` gives this package its own INFO level** —
the root logger defaults to ERROR (`LOG_VERBOSITY`), which previously hid a passing probe entirely
and hid the summary line of a failing one. If you are reading logs from a build that predates that,
raise `LOG_VERBOSITY` to `info` before concluding the probe did not run.

The line to look for is:

```
eForm browser renderer startup check passed.
```

Anything else is a real finding. The two worth recognising:

| What you see | What it means |
|---|---|
| `Chromium session creation exceeded the 30s startup budget` | The application reached chromedriver but could not get a usable session. Usually the browser cannot start — check its own journal, not this one. |
| `The eForm render browser service is unavailable.` | Nothing was listening. `systemctl status carlos-emr-chromedriver`, and check `eform_pdf_browser_service_url`. |
| `eForm browser renderer startup check is OFF` | The probe is disabled (`eform_pdf_browser_startup_check=off`). Expected in test contexts; on a deployment it means failures will surface at first print instead. |

Two things the messages deliberately will **not** tell you, so do not go looking for them there. The
service URL never appears in an operator- or clinician-facing message, because it carries the
`--url-base` capability token — the log line names the *property* instead. And the underlying
WebDriver exception is never chained into the thrown error, because a downstream handler that logged
the chain would re-emit whatever the message embedded; the redacted `causedBy=` detail line at the
failure site is the diagnostic record.

Nothing here is PHI-safe by accident: raising a log level for troubleshooting is fine, but put it
back, because DEBUG on this application can put request parameters into the log.

### The configured confidentiality statement

`PrivacyStatementAppendingFilter` appends the configured
`confidentiality_statement.*` to every printable page. The renderer measures
under print-media emulation, where that paragraph is `display:block`, so it is a
substantive off-page element and — once `bb5320b3` made decoration opt-in — it
withheld **every** eForm download on any install with a statement configured.
It now carries `carlos-print-decoration`, the gate's own opt-in for platform
boilerplate.

Two consequences worth knowing before reading a report:

- On those installs `Off-page decoration removed: 1` is the **normal** state,
  and the render is logged as not-strictly-complete on every request. That is
  expected noise, not a fault.
- The marker classifies the paragraph, not its contents. If a clinic's
  configured statement contains an `<img>` — a letterhead or logo — the
  decoration predicate rejects it and the withhold returns. A statement that
  must carry an image needs the image marked as decoration too, or the
  statement reduced to text.

### Diagnosing a withheld render ("Some eForm content could not be loaded")

When the completeness gate withholds a PDF, the operator-facing page reports **counts**:

```
Failed content resources: 0
Excluded visible elements: 1
Off-page decoration removed: 0
```

`EFormRenderCompletenessReport` is counts and booleans by construction, so that is all it can say.
It is enough to withhold a document and not enough to fix one — nobody can act on "1 element"
without knowing which. The identity is available in one place only, because the scan runs inside the
render browser against a URL the front door cannot reach (`wasForwarded()` rejects any request
carrying `X-Forwarded-*`, and nginx sets all of them).

Raise the root level to DEBUG for one render:

```
# The shipped env file has no LOG_VERBOSITY line, so sed alone matches nothing and
# is a silent no-op -- add the line if absent, otherwise rewrite it in place.
grep -q '^LOG_VERBOSITY=' /etc/carlos-emr/carlos-emr.env \
  && sudo sed -i 's/^LOG_VERBOSITY=.*/LOG_VERBOSITY=debug/' /etc/carlos-emr/carlos-emr.env \
  || echo 'LOG_VERBOSITY=debug' | sudo tee -a /etc/carlos-emr/carlos-emr.env
sudo carlos-ctl restart
# reproduce the download, then:
sudo carlos-ctl logs | grep 'renderer excluded element'
```

```
Browser eForm renderer excluded element(s): fdid=5 elements=[DIV#footer.legal h=42px chars=137]
```

Each entry is `TAG#id.class h=<height>px chars=<n>`. That is deliberately **structure only** — the
character *count* of the element's text, never the text, because an off-page block is exactly where
clinical prose ends up and this line goes to the application log. The count is what separates a
spacer from a paragraph, which is all the diagnosis needs. `renderer decoration element(s)` is the
same for the advisory bucket, so an author can confirm the right things carry the decoration marker.

**Put `LOG_VERBOSITY` back afterwards.** DEBUG on this application can put request parameters into
the log.

## Verification

Three layers, in increasing cost. **All three are required** — the first two are structurally blind
to the defect class described in "What the render gates cannot see".

### 1. Unit and integration tests

```bash
mvn test -Dtest='EForm*UnitTest,EFormViewerRelativeAssetUnitTest,LoginFilterUnitTest,\
EformDataManagerImplCreatePdfUnitTest,EFormJspMigrationRegressionTest,HttpMethodGuardFilter*Test'
```

Covers the composer (letter decode/harden, editor strip, shim ordering, image-path rewrites, APCache
key extraction), the render servlet and token service, the capability cookie and static-grant
authorization, the image/signature/APCache servlets, `LoginFilter` least-privilege, and the
mutator/GET contract.

End-to-end smoke, which skips cleanly on a host without a browser:

```bash
mvn test -Dtest=EFormBrowserPdfServiceSeleniumSmokeIntegrationTest
```

Serves `scripts/fixtures/eform/test-pattern.html` over loopback and drives the native print path,
asserting the PDF page count matches the authored `pageN` divs and that a real text layer is present
(the raster path this replaced produced zero extractable text); a companion case prints a free-flow
(no-`pageN`-div) letter and asserts a text-layer `%PDF` with no injected `@page` size.

### 2. Browser checks against a running app

Requires Tomcat plus the dev database, and the prerequisites in the smoke-test runbook (RTL
attachment-route migration applied; deployed `editControl2.js` current):

```bash
npm run test:eform-admin-playwright
npm run test:eform-render-playwright
npm run test:eform-saved-render-playwright
npm run test:eform-test-pattern-playwright
npm run test:eform-rtl-attachment-routes-playwright
npm run test:eform-rtl-attachment-types-playwright
npm run test:eform-rtl-attachment-behavior-playwright
npm run test:eform-rtl-attachment-pdf-playwright
```

`eform-rtl-attachment-behavior` is the highest-value one: it exercises a saved Rich Text Letter
through attachment selection and a merged eForm+attachment PDF download, which is the flow that the
editor-on-the-render-surface bug blocked entirely. `eform-rtl-attachment-pdf` extends it per
attachment family: a document, a lab result, an HRM report, another eForm, and an encounter form
are each attached to their own letter and must show on the saved letter and add pages to the PDF
from both download paths (toolbar Download and the form's `print=true` PDF button). It needs the
demo document files and the HRM report fixture described in the smoke-test runbook.

### 3. Look at the PDF

Mandatory before release, because layers 1 and 2 both pass while a background image is missing or a
letter prints as raw markup. Render at least one background-image form and one Rich Text Letter, then
confirm the background is present and correctly placed, letter content is formatted, no editor chrome
appears, `.DoNotPrint` content is absent, and the text layer is selectable. PDFBox is already on the
classpath — `PDFTextStripper` extracts the text layer and `PDFRenderer` rasterizes a page for visual
inspection. Full procedure: `docs/ui-tests/eform-pdf-render-smoke-test.md`.
