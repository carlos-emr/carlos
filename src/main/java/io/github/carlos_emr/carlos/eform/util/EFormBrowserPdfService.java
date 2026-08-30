/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.AddHasCdp;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.http.ClientConfig;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Browser-backed eForm PDF renderer driven entirely from the JVM.
 *
 * <p>Selenium launches a pinned headless Chromium (no Node.js runtime anywhere), navigates over
 * loopback to {@link EFormBrowserRenderPageServlet} using a render-scoped token from
 * {@link EFormRenderTokenService}, and prints the stabilized page to a native, text-layer PDF via
 * CDP {@code Page.printToPDF} (sizing each {@code @page} to the authored page geometry) for fax and
 * eDoc workflows.</p>
 *
 * <p>Security invariants (change together or not at all):</p>
 * <ul>
 *   <li>Browser egress is physically contained: a dead proxy plus a loopback bypass list block
 *       every non-loopback HTTP request at the network layer, so nothing off-origin is ever fetched.
 *       Observing such a (already-blocked) request is <em>advisory</em> by default and fails the
 *       render only under the strict network gate ({@code eform_pdf_browser_strict_network_gate});
 *       the always-hard egress gate is a live WebSocket/WebTransport channel. {@code acceptInsecureCerts}
 *       is safe only because of this containment — it can never be leveraged against an external host.</li>
 *   <li>The browser holds no CARLOS {@code HttpSession}, {@code JSESSIONID}, CSRF token or user
 *       identity. Its ONLY credential is the host-only, HttpOnly, SameSite=Strict,
 *       2-minute {@code CARLOS_EFORM_RENDER} capability cookie, exchanged on the first loopback
 *       navigation from a bootstrap token minted after the caller's {@code _eform} privilege check
 *       and bound to one fdid/provider.</li>
 *   <li>A fresh browser is launched per render so no state can bleed between users' renders.</li>
 * </ul>
 */
@Service
public class EFormBrowserPdfService {

    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** URI scheme constant reused by the scheme gates and default-port logic (SonarCloud S1192). */
    private static final String SCHEME_HTTPS = "https";

    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(90);
    // Package-private so the unit test can pin the "client read timeout stays above the in-band
    // timeouts" invariant; production code treats them as private.
    static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(30);
    static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Client-side HTTP read timeout for every WebDriver/CDP command sent to chromedriver
     * (print-to-PDF, script execution, quit). Aligned to the render budget — and deliberately 3x
     * the 30s in-band {@link #PAGE_LOAD_TIMEOUT}/{@link #SCRIPT_TIMEOUT} so legitimate slow pages
     * always hit the in-band timeout first — this replaces Selenium's ~180s default, which let a
     * wedged Chromium hold one of the {@link #MAX_CONCURRENT_RENDERS} global render slots for
     * ~6 minutes (blocked command + blocked quit) and starve all rendering.
     */
    static final Duration WEBDRIVER_COMMAND_READ_TIMEOUT = RENDER_TIMEOUT;
    static final Duration WEBDRIVER_CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Dedicated fast bound on browser-session creation (the "new session" command that launches
     * Chromium). A doomed launch — a missing/incompatible Chromium or chromedriver, or (when the OS
     * sandbox is opted in) a sandbox that cannot start — otherwise blocks on chromedriver's internal
     * browser-start timeout (~60s), and the fax-with-attachments flow renders serially, so each doomed
     * render stacks that wait into a multi-minute "faxing page never loads" for the user. A
     * real Chromium launch completes in a few seconds; this budget fails a doomed one fast and frees
     * the render slot. Kept independent of {@link #WEBDRIVER_COMMAND_READ_TIMEOUT} because a legitimate
     * render command (navigation up to {@link #PAGE_LOAD_TIMEOUT}) needs the longer per-command read.
     */
    /**
     * Deadline for the teardown backstop. Short on purpose: it runs when something is already
     * wedged, and it must never become a second place a render can hang.
     */
    /** Cause chains are third-party here and may be cyclic; walking one must always terminate. */
    private static final int MAX_CAUSE_DEPTH = 32;
    /**
     * V8 old-space cap (MB) for each renderer process. An eForm is a single small document; a page
     * that genuinely needs more heap than this is runaway form script, and capping it turns a
     * box-wide memory squeeze into that one render failing (surfaced through the normal
     * fail-closed render error, which is retryable).
     */
    static final int RENDERER_V8_HEAP_MB = 256;
    /**
     * Cap on renderer processes per browser. All render content is same-origin loopback (off-origin
     * is dead-proxied), so Chromium's default one-renderer-per-site-instance fan-out cannot pay for
     * itself here; four covers the page plus embedded same-origin iframes with room to spare.
     */
    static final int RENDERER_PROCESS_LIMIT = 4;
    /** Cap on per-error console descriptions shown for informed override; the count is unbounded. */
    private static final int MAX_CONSOLE_DETAILS = 10;
    // describeConsoleError parses Chrome's structural entry header only:
    //   "<source> <line>:<col> [Uncaught ]<Type>: <body>"
    // Both patterns are anchored to the start of the URL-redacted entry (source already replaced with
    // [redacted-url]/[redacted-path]) so the free-text <body> — which a form controls and may carry
    // PHI — can never contribute the surfaced type or location. Compiled once, not per call.
    private static final java.util.regex.Pattern CONSOLE_HEADER_PATTERN = java.util.regex.Pattern.compile(
            "^\\s*(?:\\[redacted-(?:url|path)\\]\\s+)?"          // optional redacted source token
            + "(?:(\\d{1,7}):(\\d{1,7})\\s+)?"                   // optional Chrome line:col (groups 1,2)
            + "(?:Uncaught\\s+)?"                                // optional Chrome "Uncaught" prefix
            + "([A-Z][A-Za-z0-9_]{0,40}(?:Error|Exception))\\b"); // group 3: the error type
    private static final java.util.regex.Pattern CONSOLE_LEADING_LOCATION_PATTERN =
            java.util.regex.Pattern.compile(
            "^\\s*(?:\\[redacted-(?:url|path)\\]\\s+)?(\\d{1,7}):(\\d{1,7})\\b");
    static final Duration BACKSTOP_TIMEOUT = Duration.ofSeconds(5);
    /**
     * How long the late-session reaper waits for an abandoned session-create to finish. Longer than
     * the start budget it follows: the point is to learn the session id that budget gave up on.
     */
    static final Duration LATE_SESSION_REAP_TIMEOUT = Duration.ofSeconds(90);
    static final Duration DRIVER_START_TIMEOUT = Duration.ofSeconds(30);

    /** Bounded well below Tomcat's worker pool so renders can never saturate request threads. */
    private static final int MAX_CONCURRENT_RENDERS = 2;
    private static final Duration RENDER_SLOT_WAIT = Duration.ofSeconds(30);
    private static final Semaphore RENDER_SLOTS = new Semaphore(MAX_CONCURRENT_RENDERS, true);

    /**
     * Filename prefix of the renderer's output PDF. The {@link RenderedEformPdf} guard keys on it
     * (plus a {@code .pdf} suffix) so the AutoCloseable can only ever delete this renderer's own
     * output, and the stale-artifact sweep keys on it too. The native print path creates one PDF per
     * render; the sweep also recognises same-prefixed legacy raster-capture directories
     * (see {@link #sweepStaleRendererRoots}).
     */
    static final String RENDER_ARTIFACT_PREFIX = "eform-browser-render-";

    private static final String BASE_URL_PROPERTY = "eform_pdf_browser_base_url";
    private static final String CHROME_PATH_PROPERTY = "eform_pdf_browser_chromium_path";
    /**
     * URL of an ALREADY-RUNNING chromedriver on loopback. The application connects to it; it never
     * spawns one. That is the whole point: a chromedriver the JVM forks inherits this service's
     * cgroup and systemd confinement, and Chromium's sandbox — which is built on user namespaces —
     * cannot initialise under {@code RestrictNamespaces=yes}. Running the browser under its own unit
     * is what lets it be sandboxed without loosening the EMR's own hardening.
     */
    private static final String SERVICE_URL_PROPERTY = "eform_pdf_browser_service_url";
    private static final String DEFAULT_SERVICE_URL = "http://127.0.0.1:9515";
    private static final String CATALINA_BASE_PROPERTY = "catalina.base";
    /**
     * Enables hard failure for contained off-origin HTTP attempts, non-content resource failures,
     * and severe page-script console errors. Failed resources that can affect clinical content
     * always enter the completeness report and require an exact clinician approval.
     */
    private static final String STRICT_NETWORK_GATE_PROPERTY = "eform_pdf_browser_strict_network_gate";
    private static final String ENV_REQUIRE_SANDBOX = "EFORM_RENDER_SANDBOX";
    // Log the configured unsandboxed mode once per JVM to avoid a WARN on every render.
    private static final java.util.concurrent.atomic.AtomicBoolean UNSANDBOXED_NOTICE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Dead proxy plus the exact-origin bypass built by {@link #proxyBypassListFor}: only the
     * application's own render origin escapes the dead proxy, so a request to any other host —
     * or any other loopback host/port — is blocked before it is ever sent. This holds ONLY
     * because the bypass list carries {@code <-loopback>}: Chromium's implicit rules otherwise
     * exempt all loopback traffic from proxying entirely. Together with the performance-log
     * gate, egress attempts are stopped at two independent layers: pre-send (proxy) and
     * post-hoc (event replay).
     */
    static final String DEAD_PROXY = "http://127.0.0.1:1";

    private static final int VIEWPORT_WIDTH = 1800;
    private static final int VIEWPORT_HEIGHT = 3200;

    // DOM-controlled page-geometry caps (fail-closed): a clinic-authored form cannot drive an
    // unbounded number of CSS @page sizes, or a pathological single-page dimension, into Chromium's
    // native print pipeline. Generous vs. any real multi-page eForm. (Native Page.printToPDF returns
    // a compressed PDF straight from Chromium, so unlike the former raster path there is no decoded
    // per-page image retained on the JVM heap — these caps bound the injected @page CSS, not memory.)
    private static final int MAX_PAGE_COUNT = 200;
    // Hard ceiling on a single rendered PDF's decoded byte size. Bounds the heap a pathological or
    // very large render (many high-res image pages) can allocate; ~150MB is generous for any real
    // clinical eForm packet while preventing a runaway render from exhausting the Tomcat JVM.
    private static final long MAX_PDF_BYTES = 150L * 1024L * 1024L;
    private static final double MAX_PAGE_DIMENSION = 20_000;

    // ---------------------------------------------------------------------------------------------
    // Browser-side JS. Each script owns one print guarantee: STABILIZE_ASYNC_JS waits until fonts,
    // images, and DOM mutations have settled, PREPARE_PRINT_JS injects the baseline print stylesheet
    // (zero page margin, exact color/background reproduction, non-print chrome hidden),
    // COMPUTE_PAGE_GEOMETRY_JS measures each authored page div's content box (and isolates non-page
    // flow content and detects a broken signature image) so the JVM can size the CSS @page boxes, and
    // INJECT_PAGE_SIZE_CSS_JS applies those computed @page sizes. Chromium's native Page.printToPDF
    // then emits a real text-layer PDF (the former raster path screenshotted each region and glued
    // the PNGs with PDFBox, which produced an image-only, unsearchable document).
    // ---------------------------------------------------------------------------------------------

    /**
     * Installed before any stored form code. Legacy modal APIs cannot block WebDriver, create a
     * popup, or disclose their argument text to logs; only a bounded count enters completeness.
     */
    static final String INSTALL_INTERACTION_CONTAINMENT_JS =
            "(() => {\n"
            + "  let count = 0;\n"
            + "  Object.defineProperty(window, '__carlosRendererInteractionCount', {\n"
            + "    configurable: false, get: () => count\n"
            + "  });\n"
            + "  window.alert = () => { count += 1; };\n"
            + "  window.confirm = () => { count += 1; return false; };\n"
            + "  window.prompt = () => { count += 1; return null; };\n"
            + "  window.open = () => { count += 1; return null; };\n"
            + "})();";

    /**
     * Installed before any stored form code, alongside {@link #INSTALL_INTERACTION_CONTAINMENT_JS}.
     * Wraps {@code fetch}/{@code XMLHttpRequest.send} at the earliest possible point in the page
     * lifecycle — before the render page's own bootstrap script or a stored eForm's onload work can
     * fire a request — publishing a live pending-request count on {@code window}. Installing this
     * wrap later, inside {@link #STABILIZE_ASYNC_JS} itself as before, left a window between
     * navigation and that script's own execution during which an already-in-flight request was
     * invisible to the quiet-window guarantee: the page could be captured while a request supplying
     * clinical content was still outstanding. Because this script runs once per navigation (like
     * {@link #INSTALL_INTERACTION_CONTAINMENT_JS}), it observes every request the page issues, not
     * just ones started after the settle script begins.
     */
    static final String INSTALL_NETWORK_ACTIVITY_TRACKING_JS = """
            (() => {
              let pending = 0;
              Object.defineProperty(window, '__carlosRendererPendingNetworkRequests', {
                configurable: false, get: () => pending
              });
              const nativeFetch = typeof window.fetch === 'function' ? window.fetch : null;
              const nativeXhrSend = typeof XMLHttpRequest === 'function' ? XMLHttpRequest.prototype.send : null;
              function started() { pending += 1; }
              function finished() { pending = Math.max(0, pending - 1); }
              if (nativeFetch) {
                window.fetch = function() {
                  started();
                  let result;
                  try { result = nativeFetch.apply(this, arguments); }
                  catch (error) { finished(); throw error; }
                  return Promise.resolve(result).then(
                    (value) => { finished(); return value; },
                    (error) => { finished(); throw error; });
                };
              }
              if (nativeXhrSend) {
                XMLHttpRequest.prototype.send = function() {
                  started();
                  this.addEventListener('loadend', finished, { once: true });
                  try { return nativeXhrSend.apply(this, arguments); }
                  catch (error) { finished(); throw error; }
                };
              }
            })();""";

    /**
     * Async settle: fonts ready, pending images resolved, network activity and DOM mutations quiet together, two animation frames.
     *
     * <p>The DOM-quiescence wait is load-bearing for script-built forms. The Rich Text Letter (and
     * other editor-driven corpus forms) construct their visible content asynchronously after
     * {@code onload} — fonts/images alone can settle while the editor is still assembling the letter
     * body, and a capture in that window prints the half-built editor chrome instead of the document
     * (nondeterministically, since it is a race the renderer sometimes lost and sometimes won). The
     * observer waits for a quiet window with no DOM mutations anywhere in the document (subtree-wide,
     * including attribute and text changes), bounded by a hard cap so a form with a perpetual
     * animation/timer cannot stall the render: after the cap the page is captured as-is, and the
     * render gates still apply.</p>
     */
    static final String STABILIZE_ASYNC_JS =
            "var callback = arguments[arguments.length - 1];\n"
            + "(async () => {\n"
            + "  if (document.fonts && document.fonts.ready instanceof Promise) {\n"
            + "    await document.fonts.ready;\n"
            + "  }\n"
            + "  const pendingImages = Array.from(document.images).filter((image) => !image.complete);\n"
            + "  await Promise.all(pendingImages.map((image) => new Promise((resolve) => {\n"
            + "    image.addEventListener('load', resolve, { once: true });\n"
            + "    image.addEventListener('error', resolve, { once: true });\n"
            + "  })));\n"
            // Let the form's own deferred work finish before the quiet-window race below starts.
            // Stored eForms schedule string timers a second or more out (measured across the shared
            // corpus: 49 of 50 use a delay >= 1000ms) while the quiet window is 500ms, so the capture
            // routinely beat them. A timer that populates a field then left that field BLANK in the
            // delivered PDF with every gate satisfied, and one that ran only on slower renders made
            // the same saved form pass or fail run to run. Waiting makes the output deterministic and
            // costs nothing on the forms that schedule no string timers.
            + "  const timerCompat = window.__carlosEformTimerCompat;\n"
            + "  let timersDrained = true;\n"
            + "  if (timerCompat && typeof timerCompat.whenIdle === 'function') {\n"
            + "    timersDrained = await timerCompat.whenIdle(4000);\n"
            + "  }\n"
            // whenIdle's answer was previously discarded. It resolves false when the 4s cap expired
            // with timers still pending — the one signal that says the page was captured before its
            // own deferred work ran — and nothing consumed it, so that render reported complete.
            //
            // Then re-await fonts and images: the waits above ran BEFORE the timers did, and the
            // quiet window below observes DOM mutations, not resource completion. A timer that sets
            // img.src restarts the quiet window and the page is captured with the image still in
            // flight. This pass is bounded by the same script timeout as everything else here, and
            // only awaits resources that already exist — it cannot wait on a request never made.
            + "  if (document.fonts && document.fonts.ready instanceof Promise) {\n"
            + "    await document.fonts.ready;\n"
            + "  }\n"
            + "  const timerImages = Array.from(document.images).filter((image) => !image.complete);\n"
            + "  await Promise.all(timerImages.map((image) => new Promise((resolve) => {\n"
            + "    image.addEventListener('load', resolve, { once: true });\n"
            + "    image.addEventListener('error', resolve, { once: true });\n"
            + "  })));\n"
            + "  const capped = await new Promise((resolve) => {\n"
            + "    const quietWindowMillis = 500;\n"
            + "    const maxWaitMillis = 5000;\n"
            + "    let quietTimer = null;\n"
            + "    let done = false;\n"
            + "    let resourceObserver = null;\n"
            // The counter itself lives on window, published once per navigation by
            // INSTALL_NETWORK_ACTIVITY_TRACKING_JS (see the CDP Page.addScriptToEvaluateOnNewDocument
            // call in renderWithSlot) — before this script, or any stored form code, ever runs. Poll
            // it here rather than re-wrapping fetch/XHR locally, so a request that started before
            // this settle script began executing is already reflected on the very first check below.
            + "    function pendingNetworkRequestCount() {\n"
            + "      return window.__carlosRendererPendingNetworkRequests || 0;\n"
            + "    }\n"
            + "    let lastPendingNetworkRequests = pendingNetworkRequestCount();\n"
            + "    const observer = new MutationObserver(() => {\n"
            + "      if (done) { return; }\n"
            + "      resetQuietWindow();\n"
            + "    });\n"
            + "    function resetQuietWindow() {\n"
            + "      clearTimeout(quietTimer);\n"
            + "      if (pendingNetworkRequestCount() > 0) { return; }\n"
            + "      quietTimer = setTimeout(() => finish(false), quietWindowMillis);\n"
            + "    }\n"
            + "    const networkPollInterval = setInterval(() => {\n"
            + "      if (done) { return; }\n"
            + "      const pendingNow = pendingNetworkRequestCount();\n"
            + "      if (pendingNow !== lastPendingNetworkRequests) {\n"
            + "        lastPendingNetworkRequests = pendingNow;\n"
            + "        resetQuietWindow();\n"
            + "      }\n"
            + "    }, 100);\n"
            // finish(true) means the hard cap fired before a quiet window was ever observed — the page
            // kept mutating (a broken/animating editor). Resolve with that flag so the JVM can WARN
            // that the capture is as-is rather than logging it as an ordinary quiet settle.
            + "    function finish(wasCapped) {\n"
            + "      if (done) { return; }\n"
            + "      done = true;\n"
            + "      observer.disconnect();\n"
            + "      if (resourceObserver) { resourceObserver.disconnect(); }\n"
            + "      clearInterval(networkPollInterval);\n"
            + "      resolve(!!wasCapped);\n"
            + "    }\n"
            + "    observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true, characterData: true });\n"
            + "    try {\n"
            + "      if (typeof PerformanceObserver === 'function') {\n"
            + "        resourceObserver = new PerformanceObserver(() => resetQuietWindow());\n"
            + "        resourceObserver.observe({ type: 'resource', buffered: true });\n"
            + "      }\n"
            + "    } catch (ignored) { }\n"
            + "    resetQuietWindow();\n"
            + "    setTimeout(() => finish(true), maxWaitMillis);\n"
            + "  });\n"
            + "  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));\n"
            + "  return capped ? 'CAPPED' : (timersDrained ? null : 'TIMERS_PENDING');\n"
            + "})().then((outcome) => callback(outcome)).catch((error) => callback(String(error)));";

    /**
     * Baseline print stylesheet injected before printing. This is a <em>safety net</em>: a
     * well-authored eForm already carries its own {@code @media print} rules (the corpus fixtures
     * toggle {@code .DoNotPrint}/{@code .PrintOnly} themselves), so this only guarantees zero page
     * margin, exact color/background reproduction (the page-background <img> elements are content
     * and print regardless; {@code print-color-adjust} matters only for CSS {@code background-*}
     * decorations), and that the non-print viewer chrome is hidden for forms that lack their own
     * print CSS. It deliberately does NOT set {@code width: max-content} or {@code overflow: visible}
     * (those were raster screenshot hacks); native print lays the form out at its natural width.
     *
     * <p>It also deliberately does NOT paint a background colour onto {@code <html>}. Observed
     * behaviour: with {@code html.style.background = 'white'} set here, a form whose scanned
     * background is an {@code <img>} at {@code position:absolute; z-index:-1} — the standard eForm
     * idiom — printed with a blank background; removing that one statement restored it, with the
     * rendered page otherwise byte-identical. The exact Chromium paint-order reason has NOT been
     * established, so treat this as an empirical rule rather than a derived one: never declare a
     * background on {@code <html>} here. It matters because the image still loads with HTTP 200, so
     * no render gate can detect the loss. Chromium already prints white paper.</p>
     */
    static final String PREPARE_PRINT_JS =
            "const existingCleanupStyle = document.getElementById('eform-browser-pdf-render-cleanup');\n"
            + "if (!existingCleanupStyle) {\n"
            + "  const cleanupStyle = document.createElement('style');\n"
            + "  cleanupStyle.id = 'eform-browser-pdf-render-cleanup';\n"
            + "  cleanupStyle.textContent = `\n"
            + "    @page {\n"
            + "      margin: 0;\n"
            + "    }\n"
            + "    * {\n"
            + "      -webkit-print-color-adjust: exact !important;\n"
            + "      print-color-adjust: exact !important;\n"
            + "    }\n"
            + "    .DoNotPrint,\n"
            + "    #BottomButtons,\n"
            + "    #BaseSelect,\n"
            + "    #SupplementalInfo,\n"
            // CARLOS's own on-screen warning banners are viewer chrome, not clinical content. The
            // timer-compat shim inserts a fixed-position notice at the top of the body; on the render
            // surface that was being PRINTED INTO the PDF, and on at least one corpus form it covered
            // the document's title. It became visible once timer failures were reclassified as
            // advisory, since those forms now produce a PDF instead of being refused. The condition
            // still reaches the reader through the render report and the download banner.
            + "    #carlos-eform-timer-compat-error,\n"
            + "    #carlos-render-advisory,\n"
            // Same reasoning for the APCache lookup-failure notice: it tells a clinician filling the
            // form that a field could not be populated. On the render surface the equivalent
            // condition is a 422 that the completeness gate reports, so the banner is redundant here
            // and must never be baked into a delivered document.
            + "    #carlos-apcache-lookup-failure,\n"
            + "    #labDetail {\n"
            + "      display: none !important;\n"
            + "      visibility: hidden !important;\n"
            + "    }\n"
            + "    .carlos-render-nonpage {\n"
            + "      display: none !important;\n"
            + "    }\n"
            + "    textarea {\n"
            + "      resize: none !important;\n"
            + "    }\n"
            + "  `;\n"
            + "  document.head.appendChild(cleanupStyle);\n"
            + "}\n"
            + "const body = document.body;\n"
            + "if (body) {\n"
            + "  body.style.margin = '0';\n"
            + "  body.style.padding = '0';\n"
            + "}\n"
            + "const html = document.documentElement;\n"
            + "if (html) {\n"
            + "  html.style.margin = '0';\n"
            + "  html.style.padding = '0';\n"
            + "}";

    /**
     * Page-geometry measurement and page-content isolation. For each authored {@code pageN} div this
     * returns the LARGER of the div's own border box and its visible-descendant union, per dimension,
     * tagged with the div id — the printed page must hold both the div's full flow extent (or the div
     * spills a mostly-blank page after it) and any content overflowing the div (or that content is
     * clipped). The union alone under-measures a div taller than its contents; the border box alone
     * under-measures a degenerate div (e.g. a placeholder background) its content overflows.
     *
     * <p>When page divs exist, this also marks every <em>in-flow</em> {@code body} child that neither
     * is nor contains a page div with the {@code carlos-render-nonpage} class (hidden by the baseline
     * print stylesheet): corpus forms carry interstitial/trailing flow content that the legacy region
     * capture never photographed but that native print would paginate into stray extra pages — and
     * interstitial in-flow content structurally CANNOT stay in flow, because it shifts every
     * subsequent authored page off its page boundary (the checkbox-misalignment bug). Invisible
     * layout junk (spacer divs, empty paragraphs) is excluded silently; SUBSTANTIVE content (real
     * text or visual elements, e.g. a license notice) is counted and measured so the render logs an
     * operator WARN that authored content was excluded from the printed PDF (the on-screen eForm
     * still shows it). Absolutely/fixed-positioned siblings are left visible — they are out of flow
     * (cost no pagination space), and some corpus forms overlay inputs onto pages from outside the
     * page divs.</p>
     *
     * <p>Also reports {@code signatureBroken}: whether the composer-spliced signature image
     * ({@code #signatureDisplay img}) is present but failed to load. This runs for every form,
     * independent of the page-div marking, so a signed free-flow letter is covered too.</p>
     *
     * <p>Returns {@code {pages: [{id,width,height}, ...], excludedCount, excludedHeight,
     * signatureBroken}}; {@code pages} is empty for a free-flow form (e.g. the Rich Text Letter)
     * that authored no {@code pageN} divs (no page-div marking happens in that case — the whole
     * document prints).</p>
     */
    static final String COMPUTE_PAGE_GEOMETRY_JS =
            "const rectOf = (el) => {\n"
            + "  const r = el.getBoundingClientRect();\n"
            + "  return {\n"
            + "    left: r.left + window.scrollX,\n"
            + "    top: r.top + window.scrollY,\n"
            + "    right: r.right + window.scrollX,\n"
            + "    bottom: r.bottom + window.scrollY,\n"
            + "    width: r.width,\n"
            + "    height: r.height,\n"
            + "  };\n"
            + "};\n"
            + "const isVisible = (el) => {\n"
            + "  const style = window.getComputedStyle(el);\n"
            + "  return style.display !== 'none' && style.visibility !== 'hidden' && style.position !== 'fixed';\n"
            + "};\n"
            + "const contentBox = (pageNode) => {\n"
            + "  let left = Number.POSITIVE_INFINITY;\n"
            + "  let top = Number.POSITIVE_INFINITY;\n"
            + "  let right = 0;\n"
            + "  let bottom = 0;\n"
            + "  for (const el of pageNode.querySelectorAll('*')) {\n"
            + "    if (!isVisible(el)) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    const rect = rectOf(el);\n"
            + "    if (rect.width <= 0 || rect.height <= 0) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    left = Math.min(left, rect.left);\n"
            + "    top = Math.min(top, rect.top);\n"
            + "    right = Math.max(right, rect.right);\n"
            + "    bottom = Math.max(bottom, rect.bottom);\n"
            + "  }\n"
            + "  if (!Number.isFinite(left) || right <= left || bottom <= top) {\n"
            + "    return { right: 0, bottom: 0, has: false };\n"
            + "  }\n"
            // Return the ABSOLUTE right/bottom edges (not the width/height span). The caller sizes each
            // page from the page-div origin (own.left/own.top), so it must know how far the content
            // reaches from that origin — using right-left (the span) discarded the content's offset and
            // sized the @page too narrow, cropping offset content when a full-width background 404'd.
            + "  return { right: right, bottom: bottom, has: true };\n"
            + "};\n"
            + "const pageNodes = Array.from(document.body ? document.body.querySelectorAll('*') : [])\n"
            + "  .filter((el) => /^page\\d+$/i.test(el.id));\n"
            + "let excludedCount = 0;\n"
            + "let excludedHeight = 0;\n"
            + "let decorativeExcludedCount = 0;\n"
            // Structural descriptors of the elements the two buckets counted, so a withheld render
            // can be diagnosed. STRUCTURE ONLY -- tag, id, class, height and the LENGTH of the text.
            // Never the text itself: an off-page block is exactly where clinical prose ends up, and
            // this string reaches the application log. The length is what distinguishes a spacer
            // from a paragraph, which is all the diagnosis needs.
            + "const carlosDescribe = (el, rect) => {\n"
            + "  const cls = (typeof el.className === 'string' && el.className) ? '.' + el.className.trim().split(/\\s+/).join('.') : '';\n"
            + "  const id = el.id ? '#' + el.id : '';\n"
            + "  return el.tagName + id + cls + ' h=' + Math.round(rect.height) + 'px'\n"
            + "    + ' chars=' + (el.textContent || '').trim().length;\n"
            + "};\n"
            // Bounded: a pathological form must not turn one render into thousands of log lines.
            + "const CARLOS_MAX_DESCRIBED = 20;\n"
            + "const excludedDetails = [];\n"
            + "const decorativeDetails = [];\n"
            + "const carlosPageOrder = (a, b) => a.compareDocumentPosition(b);\n"
            + "const carlosOffPageDecoration = (el) => {\n"
            + "  const firstPage = pageNodes[0];\n"
            + "  const lastPage = pageNodes[pageNodes.length - 1];\n"
            + "  const beforeFirst = (carlosPageOrder(el, firstPage) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;\n"
            + "  const afterLast = (carlosPageOrder(el, lastPage) & Node.DOCUMENT_POSITION_PRECEDING) !== 0;\n"
            + "  if (!beforeFirst && !afterLast) { return false; }\n"
            // Position CANNOT establish that content is non-clinical. This predicate used to treat an
            // off-page element as decoration whenever it merely LACKED a control and a media element,
            // with no length or content test of any kind, so a plain <div> of clinical prose authored
            // before page1 or after the last page div was reclassified as decoration, hidden by the
            // classList.add below like any other off-page node, and disclosed only through the
            // ADVISORY decorativeExcludedElements component -- which withholdsDocument never acts on.
            // Print, fax and archive therefore shipped a PDF with that clinical text silently missing.
            // Decoration is now OPT-IN: an off-page element qualifies only when the form marks it as
            // such, and everything else stays in the BLOCKING bucket where a clinician approves before
            // it ships. Authors mark genuine badges, mastheads and boilerplate disclaimers with either
            // spelling of the marker; an unmarked off-page block is treated as clinical content.
            + "  const carlosDecorationMarker = '.carlos-print-decoration, [data-carlos-print-decoration]';\n"
            + "  if (!el.matches(carlosDecorationMarker)) { return false; }\n"
            // Defence in depth below the marker: an explicitly marked container must still not carry a
            // control or a media element out of the document. A marker is an author assertion about
            // boilerplate, not a licence to drop a field or a signature.
            + "  const carlosControls = 'input, textarea, select, button, [contenteditable]';\n"
            // Self AND descendants, exactly like the media check below: a BARE off-page control
            // (a top-level textarea holding clinical default text) must stay in the BLOCKING
            // bucket, and querySelector alone only sees descendants.
            + "  if (el.matches(carlosControls) || el.querySelector(carlosControls)) { return false; }\n"
            // Off-page imagery is far more likely a signature preview or a rendered clinical
            // figure than a badge logo, and unlike short text it cannot be judged from a count
            // alone -- media keeps the element in the BLOCKING excludedContentElements bucket.
            + "  const carlosMedia = 'img, canvas, svg, video, iframe, object, embed';\n"
            + "  if (el.matches(carlosMedia) || el.querySelector(carlosMedia)) { return false; }\n"
            + "  return true;\n"
            + "};\n"
            // DESCEND, don't skip, through elements that merely CONTAIN a page div. Scanning only
            // document.body.children made this whole pass dead on almost the entire real corpus:
            // eformGenerator.jsp wraps the form body in <form id="FormName"> (emitted at its line
            // 1287, closed at 1675) and emits every <div id="pageN"> inside it, so body.children is
            // just [<form>], that one child contains every page node, and the loop `continue`d on
            // its only iteration. Measured against the 227 stored forms in this instance: 223 use
            // page divs and 220 of those nest them inside a <form> — so excludedCount was
            // structurally pinned at 0 for 98.7% of them, and excludedContentElements (a BLOCKING
            // component) could never fire. Interstitial content between </div> and the next
            // <div id="pageN"> then stays in flow and takes its own printed page, shifting every
            // later authored page off its boundary — the checkbox-misalignment bug this pass exists
            // to catch.
            + "const scanForExcluded = (container) => {\n"
            + "  for (const child of Array.from(container.children)) {\n"
            + "    if (child.tagName === 'SCRIPT' || child.tagName === 'STYLE') {\n"
            + "      continue;\n"
            + "    }\n"
            + "    if (pageNodes.some((pageNode) => child === pageNode)) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    if (pageNodes.some((pageNode) => child.contains(pageNode))) {\n"
            + "      scanForExcluded(child);\n"
            + "      continue;\n"
            + "    }\n"
            + "    const position = window.getComputedStyle(child).position;\n"
            + "    if (position !== 'absolute' && position !== 'fixed') {\n"
            // Measure and classify BEFORE hiding. The baseline print stylesheet sets
            // .carlos-render-nonpage { display:none }, so adding the class first collapses the
            // element to a 0x0 box and makes isVisible() false — every element would then test
            // non-substantive and excludedCount could never rise above 0, silently defeating the
            // "authored content excluded from the printed PDF" WARN. Substantive = visible, taller
            // than a spacer, carrying real text or a visual element. Invisible layout junk
            // (whitespace divs, empty paragraphs, <br> runs) is excluded silently; substantive
            // authored content is counted, THEN the element is hidden.
            + "      const rect = child.getBoundingClientRect();\n"
            + "      const substantive = isVisible(child) && rect.height > 4 && (\n"
            + "        (child.textContent || '').trim().length > 0\n"
            + "        || child.querySelector('img, canvas, svg, video, input, textarea, select') !== null);\n"
            + "      if (substantive && carlosOffPageDecoration(child)) {\n"
            + "        decorativeExcludedCount += 1;\n"
            + "        if (decorativeDetails.length < CARLOS_MAX_DESCRIBED) {\n"
            + "          decorativeDetails.push(carlosDescribe(child, rect));\n"
            + "        }\n"
            + "      } else if (substantive) {\n"
            + "        excludedCount += 1;\n"
            + "        excludedHeight += rect.height;\n"
            + "        if (excludedDetails.length < CARLOS_MAX_DESCRIBED) {\n"
            + "          excludedDetails.push(carlosDescribe(child, rect));\n"
            + "        }\n"
            + "      }\n"
            + "      child.classList.add('carlos-render-nonpage');\n"
            + "    }\n"
            + "  }\n"
            + "};\n"
            + "if (pageNodes.length > 0 && document.body) {\n"
            + "  scanForExcluded(document.body);\n"
            + "}\n"
            // Signed-form safety: the composer splices a stored signature as
            // <div id="signatureDisplay"><img src=...>. After the async image settle, a present but
            // failed-to-load signature reads complete===true with naturalWidth===0. Surface it so the
            // JVM can require informed approval instead of silently producing a
            // clinician-signed PDF with no signature. Runs for every form (a free-flow letter can be
            // signed too), independent of the page-div marking above.
            + "let signatureBroken = false;\n"
            + "const signatureImage = document.querySelector('#signatureDisplay img');\n"
            + "if (signatureImage && signatureImage.complete && signatureImage.naturalWidth === 0) {\n"
            + "  signatureBroken = true;\n"
            + "}\n"
            // Composer marker: a non-blank stored signature whose placeholder was altered/removed, so
            // no signature <img> exists at all (the naturalWidth check above cannot see that case).
            + "if (document.querySelector('#carlos-signature-unrendered')) {\n"
            + "  signatureBroken = true;\n"
            + "}\n"
            // A shim object can be published (status is assigned to window before the handlers are
            // wired) without installation completing, so an absent object, a half-installed one, and
            // an explicit failure must all count as a compatibility failure. Checking only `failed`
            // would read a half-installed shim as healthy.
            + "const timerCompat = window.__carlosEformTimerCompat;\n"
            + "const timerCompatShimMissing = !timerCompat || timerCompat.installed !== true;\n"
            + "const timerCompatibilityFailure = timerCompatShimMissing"
            + " || timerCompat.failed === true;\n"
            // Deployer marker: a lab decision-support script is missing and a stub was published under
            // its real filename, so the request returned 200 and the network scan saw nothing wrong.
            // Without this the requisition renders "complete" with unpopulated fields and no tickler.
            + "const labDecisionSupportStubbed = "
            + "!!document.querySelector('#carlos-lab-ds-stubbed');\n"
            // Composer marker: the form expects a provider signature stamp that is not on file. Kept
            // separate from signatureBroken, which means a SIGNED document lost its signature.
            + "const providerStampMissing = "
            + "!!document.querySelector('#carlos-provider-stamp-missing');\n"
            + "return {\n"
            + "  pages: pageNodes.map((pageNode) => {\n"
            + "    const own = rectOf(pageNode);\n"
            + "    const box = contentBox(pageNode);\n"
            + "    return {\n"
            // Width hugs the CONTENT extent FROM THE PAGE-DIV ORIGIN (box.right - own.left), not the
            // content span: a plain block page div stretches to the full viewport, so printing its own
            // width would emit a giant blank right margin, but sizing to the span (right-left) cropped
            // content inset from the div's left edge (e.g. a field at left:700 when the full-width
            // background 404'd). Fall back to own.width when there is no measurable content. Height
            // takes the LARGER of the div's flow extent and its content extent from the div top, so
            // vertical under-measurement can never spill blank pages or clip fields.
            + "      id: pageNode.id,\n"
            + "      width: (box.has && (box.right - own.left) > 0) ? (box.right - own.left) : own.width,\n"
            + "      height: Math.max(own.height, box.has ? (box.bottom - own.top) : 0),\n"
            + "    };\n"
            + "  }),\n"
            + "  excludedCount: excludedCount,\n"
            + "  excludedHeight: excludedHeight,\n"
            + "  decorativeExcludedCount: decorativeExcludedCount,\n"
            + "  excludedDetails: excludedDetails,\n"
            + "  decorativeDetails: decorativeDetails,\n"
            + "  signatureBroken: signatureBroken,\n"
            + "  timerCompatibilityFailure: timerCompatibilityFailure,\n"
            + "  timerCompatShimMissing: timerCompatShimMissing,\n"
            + "  labDecisionSupportStubbed: labDecisionSupportStubbed,\n"
            + "  providerStampMissing: providerStampMissing,\n"
            + "};";

    /**
     * Applies the JVM-computed {@code @page} sizing CSS (argument 0) into a dedicated style element,
     * so Chromium's {@code Page.printToPDF} with {@code preferCSSPageSize:true} sizes each printed
     * page to its authored page div. Kept as its own element (never merged into the baseline cleanup
     * style) so the sizing is idempotent and the two concerns stay independently pinned by tests.
     */
    static final String INJECT_PAGE_SIZE_CSS_JS =
            "const css = arguments[0];\n"
            + "let style = document.getElementById('eform-browser-pdf-page-size');\n"
            + "if (!style) {\n"
            + "  style = document.createElement('style');\n"
            + "  style.id = 'eform-browser-pdf-page-size';\n"
            + "  document.head.appendChild(style);\n"
            + "}\n"
            + "style.textContent = css;";

    /**
     * A rendered eForm PDF whose on-disk lifetime the holder owns: {@link #close()} deletes the
     * file (quietly; the 24h stale-output sweep remains the backstop for holders that die before
     * closing). Hold it in try-with-resources when the bytes are consumed in-request; call
     * {@link #path()} and skip close only when ownership genuinely transfers onward (e.g. the fax
     * flow promoting the file into the document store).
     *
     * <p>Guard scope: the compact constructor enforces the renderer output <em>name</em> (the
     * delete-safety invariant for {@link #close()}); content validity is enforced at the render
     * success gate ({@code hasPdfMagicBytes}), and containment under the managed temp root is
     * structural — every production path is created via {@code createSecureTempFile} under
     * {@code resolveRendererTempRoot()}.</p>
     */
    public record RenderedEformPdf(Path path, EFormRenderCompletenessReport completeness,
            List<String> severeConsoleDetails)
            implements AutoCloseable {
        /**
         * Rejects any path that is not this renderer's own output. {@link #close()} deletes the
         * wrapped file, so constraining the wrapper to a non-null {@code eform-browser-render-*.pdf}
         * filename makes "close() deletes only renderer output" self-enforcing — a stray
         * {@code new RenderedEformPdf(Path.of("/etc/passwd"))} can never turn this AutoCloseable into
         * an arbitrary-file delete.
         *
         * <p>A null {@code completeness} normalizes to {@link EFormRenderCompletenessReport#complete()}
         * so callers reading it never have to null-check; a delivered PDF with no report recorded is
         * by definition one that raised nothing.</p>
         *
         * @throws NullPointerException if {@code path} is null
         * @throws IllegalArgumentException if the filename is not a renderer output name
         */
        public RenderedEformPdf {
            completeness = completeness == null ? EFormRenderCompletenessReport.complete() : completeness;
            // PHI-safe per-error descriptions for the informed-override screen; display only,
            // never part of the completeness report or the approval digest.
            severeConsoleDetails = severeConsoleDetails == null ? List.of() : List.copyOf(severeConsoleDetails);
            Objects.requireNonNull(path, "rendered eForm PDF path must not be null");
            Path fileNamePath = path.getFileName();
            String fileName = fileNamePath == null ? "" : fileNamePath.toString();
            if (!fileName.startsWith(RENDER_ARTIFACT_PREFIX) || !fileName.endsWith(".pdf")) {
                // The filename is a managed temp name, not PHI; naming it aids diagnosis of a
                // mis-wired caller.
                throw new IllegalArgumentException(
                        "RenderedEformPdf must wrap renderer output (" + RENDER_ARTIFACT_PREFIX
                        + "*.pdf); refusing: " + fileName);
            }
        }

        /**
         * Wraps renderer output whose completeness was not recorded, reporting it as complete.
         *
         * <p>Retained for callers that only need the file. A render that reached the point of
         * producing a PDF already passed the gate, so "no report" and "nothing to report" are the
         * same statement here.</p>
         */
        public RenderedEformPdf(Path path) {
            this(path, EFormRenderCompletenessReport.complete(), List.of());
        }

        /** Convenience for callers that produced no console-error detail. */
        public RenderedEformPdf(Path path, EFormRenderCompletenessReport completeness) {
            this(path, completeness, List.of());
        }

        @Override
        public void close() {
            deleteQuietly(path);
        }
    }

    /**
     * Renders a saved eForm by loading the token-authorized local servlet route in headless
     * Chromium and printing the stabilized page to a native, text-layer PDF via CDP
     * {@code Page.printToPDF} (with each {@code @page} sized to the authored page geometry).
     *
     * <p>The public service boundary loads the saved record and enforces demographic-scoped
     * {@code _eform} READ access before minting any renderer capability.
     *
     * @param loggedInInfo authenticated caller used for patient-scoped authorization and provider
     *        binding
     * @param fdid saved eForm data identifier
     * @return handle to a readable temporary PDF; the holder owns cleanup via
     *         {@link RenderedEformPdf#close()}
     * @throws PDFGenerationException when no render slot is available, the browser cannot start,
     *         the page fails its gates (bad status, blocked egress, console errors), the render
     *         times out, or no readable PDF is produced
     */
    public RenderedEformPdf renderSavedEformPdf(
            LoggedInInfo loggedInInfo, int fdid) throws PDFGenerationException {
        return renderSavedEformPdf(loggedInInfo, fdid, null);
    }

    /**
     * Renders with an optional server-issued approval for exact visual-resource, excluded-content,
     * signature, and timer-compatibility omissions. Unapproved omissions raise
     * {@link EformContentUnavailableException}; main-document, network-evidence, and live-channel
     * failures are never overridable.
     *
     * @param loggedInInfo authenticated caller used for patient-scoped authorization and provider
     *        binding
     * @param fdid saved eForm data identifier
     * @param approval exact, short-lived approval capability, or {@code null}
     * @return handle to a readable temporary PDF
     * @throws EformContentUnavailableException when the render has unapproved omissions
     * @throws PDFGenerationException for every other render failure
     */
    public RenderedEformPdf renderSavedEformPdf(
            LoggedInInfo loggedInInfo, int fdid, EFormRenderApproval approval) throws PDFGenerationException {
        EFormData eFormData = SpringUtils.getBean(EFormDataDao.class).find(fdid);
        if (eFormData == null) {
            throw new PDFGenerationException(
                    "EForm PDF generation failed because the eForm was not found.");
        }
        String demographicId = eFormData.getDemographicId() == null
                ? null : String.valueOf(eFormData.getDemographicId());
        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        if (loggedInInfo == null
                || !securityInfoManager.hasPrivilege(
                        loggedInInfo, "_eform", SecurityInfoManager.READ, demographicId)) {
            throw new SecurityException("missing required sec object (_eform)");
        }
        String providerId = loggedInInfo.getLoggedInProviderNo();
        // Decided here because this is the only point on the render path that knows WHO is asking:
        // the render browser carries no identity, so any data-access decision must be made by the
        // initiator and travel with the render. Measurement history reached through an eForm is still
        // measurement data — the route the renderer's adapter replaces enforces _measurement.
        boolean measurementsPermitted = securityInfoManager.hasPrivilege(
                loggedInInfo, "_measurement", SecurityInfoManager.READ, demographicId);
        return renderSavedEformPdfAuthorized(fdid, providerId, approval, measurementsPermitted);
    }

    /**
     * Test seam below the public authorization boundary. Production callers must use the
     * LoggedInInfo-bearing API above.
     */
    RenderedEformPdf renderSavedEformPdfAuthorized(
            int fdid, String providerId, EFormRenderApproval approval) throws PDFGenerationException {
        // Withholds measurement history: a caller that has not stated the privilege does not get it.
        return renderSavedEformPdfAuthorized(fdid, providerId, approval, false);
    }

    RenderedEformPdf renderSavedEformPdfAuthorized(
            int fdid, String providerId, EFormRenderApproval approval, boolean measurementsPermitted)
            throws PDFGenerationException {
        // Resolve the managed temp root and sweep stale renderer artifacts BEFORE competing for a
        // render slot. The sweep is best-effort housekeeping (a filesystem walk of the shared root);
        // running it while holding one of the scarce MAX_CONCURRENT_RENDERS slots would charge its
        // latency to every render and needlessly narrow throughput under load. A resolve failure here
        // is a real config error and must surface before a slot is taken.
        Path tempRoot = resolveRendererTempRoot();
        sweepStaleRendererRoots(tempRoot);

        SlotAcquisition acquisition = acquireRenderSlot(RENDER_SLOTS, RENDER_SLOT_WAIT);
        if (acquisition == SlotAcquisition.TIMED_OUT) {
            // Load-shed: all render slots were busy for the full wait. Log so a maintainer can see the
            // renderer is saturated (fdid only — no PHI, no render URL/token).
            logger.warn("Browser eForm renderer at capacity ({} concurrent slots); rejecting render for fdid={}",
                    MAX_CONCURRENT_RENDERS, fdid);
            // Retryable: the renderer itself is healthy, every slot is just momentarily busy. Marked
            // structurally (not just in the message text) so callers can tell this apart from a
            // durable failure without matching on wording — see PDFGenerationException.isRetryable().
            throw new PDFGenerationException("Browser rendering is at capacity; please retry shortly.", true);
        }
        if (acquisition == SlotAcquisition.INTERRUPTED) {
            // Distinct from capacity: the waiting thread was interrupted (JVM/app shutdown), so no
            // slot was ever taken (nothing to release) and the render never started. Still marked
            // retryable structurally: on a live JVM a retry can still succeed, even though the
            // message itself omits retry advice since a shutdown-triggered interrupt would make it
            // misleading.
            logger.warn("Browser eForm render aborted: waiting thread interrupted (shutdown?): fdid={}", fdid);
            throw new PDFGenerationException("Browser rendering was aborted before it started.", true);
        }
        try {
            return renderWithSlot(fdid, providerId, tempRoot, approval, measurementsPermitted);
        } finally {
            RENDER_SLOTS.release();
        }
    }

    private RenderedEformPdf renderWithSlot(
            int fdid, String providerId, Path tempRoot, EFormRenderApproval approval,
            boolean measurementsPermitted)
            throws PDFGenerationException {
        HttpServletRequest currentRequest = currentRequestOrNull();
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");
        // Declared before the try (validated to non-null inside it) so the catch-block diagnostics can
        // reference it AND so an invalid base-URL configuration is reported as a checked
        // PDFGenerationException rather than letting an IllegalArgumentException escape this method's
        // throws contract — see the narrow try/catch immediately below.
        String baseUrl = null;
        // Scoped to ONLY this validation call, deliberately OUTSIDE the main try/finally below:
        // an IllegalArgumentException thrown later in the render (e.g.
        // Base64.getDecoder().decode(...) on a corrupt Page.printToPDF payload inside
        // printToPdf) must never be misattributed to base-URL configuration. Keeping this
        // catch lexically scoped to only the validateRendererBaseUrl(...) call means any other
        // IllegalArgumentException raised further down falls through to the main try's
        // catch (RuntimeException e) below instead, which reports the honest generic
        // "Browser rendering failed..." diagnosis rather than a false configuration diagnosis.
        try {
            baseUrl = validateRendererBaseUrl(resolveBaseUrl(projectHome, currentRequest));
        } catch (IllegalArgumentException e) {
            String reason = RenderLogRedaction.redactUrls(String.valueOf(e.getMessage()));
            logger.error("Browser eForm renderer rejected its base-URL configuration: {}", reason);
            throw new PDFGenerationException("Browser renderer base URL configuration is invalid: " + reason);
        }
        Path outputPdfPath = null;
        ChromiumDriver driver = null;
        // Held for the finally: teardown needs the session id and endpoint captured at
        // creation, not just the driver handle.
        RendererBrowser browser = null;
        boolean success = false;
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + RENDER_TIMEOUT.toNanos();

        // The grant is render-scoped: the lease is opened as the first resource of this block so its
        // close() invalidates the token at end of render — success, failure, or a throw before the
        // browser ever redeems it — instead of letting it linger in the bounded token cache for its
        // full TTL. try-with-resources guarantees close() runs before the catch/finally below.
        try (var renderLease = EFormRenderTokenService.getInstance().lease(fdid, providerId)) {
            // Record the initiator's measurement decision on the grant, before the browser is pointed
            // at the page: the composer reads it there, and a grant that never received it embeds
            // nothing.
            if (measurementsPermitted) {
                EFormRenderTokenService.getInstance().authorizeMeasurementHistory(renderLease.token());
            }
            String appPath = validateRendererAppPath(buildAppPath(fdid, renderLease.token()));
            logger.info("Browser eForm renderer starting: fdid={} baseUrl={}", fdid, baseUrl);

            String allowedOrigin = originOf(baseUrl);
            if (allowedOrigin == null) {
                throw new PDFGenerationException("Browser renderer configuration is invalid for the resolved local eForm URL.");
            }
            outputPdfPath = createSecureTempFile(tempRoot, RENDER_ARTIFACT_PREFIX, ".pdf");

            boolean unsandboxed = !sandboxEnabled();
            if (unsandboxed && UNSANDBOXED_NOTICE_LOGGED.compareAndSet(false, true)) {
                logger.warn("Browser eForm renderer running WITHOUT Chromium's OS-level sandbox (default; "
                        + "set EFORM_RENDER_SANDBOX=true on a non-root deployment with unprivileged user "
                        + "namespaces to enable it). OS-level containment is delegated to the container boundary.");
            }
            browser = createDriver(buildChromeOptions(resolveChromiumPath(), unsandboxed, allowedOrigin));
            driver = browser.driver();
            long driverStartedNanos = System.nanoTime();
            logger.debug("Browser eForm renderer driver started for fdid={} (OS sandbox {})",
                    fdid, unsandboxed ? "disabled" : "enabled");
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT).scriptTimeout(SCRIPT_TIMEOUT);
            // Emulate PRINT media (not screen): the page then settles and is measured in the exact
            // layout Page.printToPDF will emit, and each form's own {@code @media print} rules (e.g.
            // the corpus fixture's PrintOnly/DoNotPrint toggles) take effect for the captured PDF.
            driver.executeCdpCommand("Emulation.setEmulatedMedia", Map.of("media", "print"));
            driver.executeCdpCommand(
                    "Page.addScriptToEvaluateOnNewDocument",
                    Map.of("source", INSTALL_INTERACTION_CONTAINMENT_JS));
            driver.executeCdpCommand(
                    "Page.addScriptToEvaluateOnNewDocument",
                    Map.of("source", INSTALL_NETWORK_ACTIVITY_TRACKING_JS));

            List<LogEntry> performanceEntries = new ArrayList<>();
            // Navigate the sessionless render browser to the loopback render page. Do NOT log the full
            // URL: it carries the fdid and the render token; log the origin only.
            logger.debug("Browser eForm renderer navigating to render page: fdid={} origin={}", fdid, allowedOrigin);
            driver.get(baseUrl + appPath);
            long navigationFinishedNanos = System.nanoTime();
            // Drain immediately after navigation so the main-document response is captured into our
            // non-evicting list before any later request flood can push it out of Selenium's
            // bounded internal buffer, then latch its status as a fallback for the final gate.
            drainPerformanceLog(driver, performanceEntries);
            Integer latchedMainStatus = scanNetworkEvents(
                    performanceEntries.stream().map(LogEntry::getMessage).toList(), allowedOrigin).mainDocumentStatus();
            boolean stabilizationCapped = settle(driver, deadlineNanos, fdid);
            long stabilizationFinishedNanos = System.nanoTime();
            if (!isExpectedRendererUrl(driver.getCurrentUrl(), baseUrl + appPath)) {
                throw new PDFGenerationException(
                        "Browser rendering navigated away from the authorized eForm page.");
            }

            JavascriptExecutor js = driver;
            js.executeScript(PREPARE_PRINT_JS);
            // Measure each authored page div's content box, then size the CSS @page boxes to match so
            // native print reproduces the legacy per-page geometry. An empty list is valid: a free-flow
            // form (the Rich Text Letter) authored no pageN divs, so we inject no @page size and let the
            // form's own @page rules or Chromium's default paper drive natural pagination.
            PageGeometry geometry = readPageGeometry(js.executeScript(COMPUTE_PAGE_GEOMETRY_JS));
            // Emitted HERE, immediately after the scan and before any branch below can throw.
            // The withheld case is the one that most needs this, and it exits through
            // EformContentUnavailableException a few lines down — logging it later would make the
            // identity unavailable in exactly the situation it exists for.
            //
            // The counts reach the operator through the completeness report, which is counts and
            // booleans by construction: enough to withhold a clinical document, not enough to fix
            // one. Nobody can act on "1 element" without knowing which. This is the only place the
            // identity exists at all — the scan runs inside the render browser, against a URL the
            // front door cannot reach.
            //
            // Structure only: tag, id, class, height, and the character COUNT of the text. Never
            // the text: an off-page block is exactly where clinical prose ends up, and this line
            // goes to the application log.
            if (!geometry.excludedDetails().isEmpty()) {
                logger.debug("Browser eForm renderer excluded element(s): fdid={} elements={}",
                        fdid, geometry.excludedDetails());
            }
            if (!geometry.decorativeDetails().isEmpty()) {
                logger.debug("Browser eForm renderer decoration element(s): fdid={} elements={}",
                        fdid, geometry.decorativeDetails());
            }
            if (geometry.decorativeExcludedCount() > 0) {
                // An off-page element the FORM EXPLICITLY MARKED as decoration (a license or
                // attribution badge, a masthead, a boilerplate disclaimer) was treated as
                // non-clinical: it does NOT withhold the document, but
                // it is never silent either — the count enters the completeness report as the
                // ADVISORY decorativeExcludedElements component below, so every delivery surface
                // discloses that something was removed. A count only (no content/PHI).
                logger.info("Browser eForm renderer excluded {} off-page element(s) as non-clinical "
                        + "decoration (advisory, disclosed): fdid={}", geometry.decorativeExcludedCount(), fdid);
            }
            int containedInteractions = readContainedInteractionCount(
                    js.executeScript("return window.__carlosRendererInteractionCount || 0;"));
            drainPerformanceLog(driver, performanceEntries);
            List<String> severeConsoleDetails = new ArrayList<>();
            EFormRenderCompletenessReport completeness = enforceRenderGates(
                    driver, performanceEntries, latchedMainStatus, baseUrl, fdid, severeConsoleDetails)
                    .merge(new EFormRenderCompletenessReport(
                            0,
                            geometry.excludedCount(),
                            0,
                            containedInteractions,
                            geometry.decorativeExcludedCount(),
                            geometry.signatureBroken(),
                            geometry.timerCompatibilityFailure(),
                            stabilizationCapped,
                            geometry.labDecisionSupportStubbed(),
                            geometry.providerStampMissing()));
            if (withholdsDocument(completeness, approval, fdid, providerId)) {
                // Name the components, not just the totals: a bare "blocking=2" cannot be acted on,
                // and three of the components have no log line of their own to infer from.
                logger.warn("Browser eForm renderer blocked incomplete output: fdid={} issues={} blocking={} [{}]",
                        fdid, completeness.issueCount(), completeness.blockingIssueCount(),
                        completeness.describe(true));
                throw new EformContentUnavailableException(
                        "The eForm could not be fully rendered. Review the reported omissions before proceeding.",
                        fdid, completeness, List.copyOf(severeConsoleDetails));
            }
            if (completeness.hasBlockingOmissions()) {
                logger.warn("Browser eForm renderer proceeding with approved incomplete output: fdid={} issues={}",
                        fdid, completeness.issueCount());
            } else if (!completeness.isComplete()) {
                // Advisory-only: the document is delivered, so this WARN is the audit record on the
                // fax and direct-download paths, which stream bytes and cannot carry a notice. The
                // preview path additionally surfaces it to the clinician (see lastCompletenessReport).
                logger.warn("Browser eForm renderer produced advisory-only issues: fdid={} advisories={} [{}]",
                        fdid, completeness.advisoryIssueCount(), completeness.describe(false));
            }
            List<PageSize> pageSizes = geometry.pages();
            if (!pageSizes.isEmpty()) {
                js.executeScript(INJECT_PAGE_SIZE_CSS_JS, buildPageSizeCss(pageSizes));
            }
            if (geometry.excludedCount() > 0) {
                // Authored in-flow content outside the pageN divs was excluded from the printed PDF
                // (legacy region-capture parity — interstitial flow content would shift every later
                // authored page off its boundary). Surface it: the form author intended that content,
                // and an operator/form designer must be able to see WHY it is absent from the PDF.
                // Counts and extent only — never element text, which can carry eForm content.
                logger.warn("Browser eForm renderer excluded {} substantive in-flow element(s) (~{}px tall) "
                        + "outside the authored page divs from the printed PDF (legacy region-capture parity; "
                        + "the on-screen eForm still shows them): fdid={}",
                        geometry.excludedCount(), Math.round(geometry.excludedHeight()), fdid);
            }
            logger.debug("Browser eForm renderer measured {} authored page size(s): fdid={}", pageSizes.size(), fdid);
            long gatesFinishedNanos = System.nanoTime();

            printToPdf(driver, outputPdfPath, deadlineNanos);
            long printedNanos = System.nanoTime();

            // Capture the size once, before declaring success: a second Files.size inside the
            // success log could race an external sweep and turn a completed render into a
            // misreported failure with the finished PDF orphaned.
            long outputPdfBytes = Files.isReadable(outputPdfPath) ? Files.size(outputPdfPath) : 0;
            // Magic-byte check per the direct-response guidance: the fax/eDoc pipeline must never
            // receive a nonempty-but-not-PDF output (a crashed print, a stray file).
            if (outputPdfBytes == 0 || !hasPdfMagicBytes(outputPdfPath)) {
                throw new PDFGenerationException("Browser rendering completed without producing a readable eForm PDF.");
            }
            success = true;
            // Success record: fdid, page count, output size and elapsed time give operators an
            // end-to-end render trace. No PHI, no render URL/token — origin/counts/bytes only.
            logger.info("Browser eForm renderer completed: fdid={} pages={} bytes={} elapsedMs={} "
                            + "driverStartupMs={} navigationMs={} stabilizationMs={} gatesMs={} printMs={}",
                    fdid, pageSizes.size(), outputPdfBytes,
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    (driverStartedNanos - startNanos) / 1_000_000L,
                    (navigationFinishedNanos - driverStartedNanos) / 1_000_000L,
                    (stabilizationFinishedNanos - navigationFinishedNanos) / 1_000_000L,
                    (gatesFinishedNanos - stabilizationFinishedNanos) / 1_000_000L,
                    (printedNanos - gatesFinishedNanos) / 1_000_000L);
            // Carry the report out with the file rather than in a field: renders run concurrently
            // under the slot semaphore, so any per-service mutable state would cross-talk.
            return new RenderedEformPdf(outputPdfPath, completeness, List.copyOf(severeConsoleDetails));
        } catch (EformContentUnavailableException e) {
            // Re-throw incomplete renders without relabeling them as renderer-integrity failures;
            // the caller displays the sanitized issue report and requires exact approval.
            throw e;
        } catch (PDFGenerationException e) {
            logger.error("Browser eForm renderer failed: fdid={} baseUrl={} reason={}", fdid, baseUrl, RenderLogRedaction.redactUrls(e.getMessage()));
            throw e;
        } catch (IOException e) {
            // Redact: an IOException from temp-file/capture handling can carry a path; keep the type and
            // a redacted message rather than the raw throwable, consistent with the RuntimeException path.
            logger.error("Browser eForm renderer I/O failure: fdid={} baseUrl={} type={} error={}",
                    fdid, baseUrl, e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            throw new PDFGenerationException("Unable to prepare files for the browser PDF renderer.", e);
        } catch (RuntimeException e) {
            // Deliberately no catch (IllegalArgumentException e) here: that would re-widen this
            // handler back over the whole render body and risk mislabeling an unrelated IAE (e.g.
            // Base64.getDecoder().decode(...) on a corrupt Page.printToPDF payload inside
            // printToPdf) as a base-URL configuration failure. The only IAE this method
            // converts to a configuration diagnosis is the one from validateRendererBaseUrl(...)
            // above, in its own narrowly-scoped try/catch before this try block even starts. Any
            // other IllegalArgumentException (a RuntimeException subtype) is caught here and gets
            // the honest generic diagnosis below.
            //
            // WebDriver exception messages can embed the loopback render URL (which carries the fdid
            // and render token). Deliberately do NOT chain the raw exception as the cause: a
            // downstream handler that logs the throwable (FaxDocumentManagerImpl) would otherwise
            // re-emit the unredacted URL, defeating the renderer's PHI-safe logging. Instead we log,
            // here and only here, a fully-redacted picture: the type, the URL/path-redacted message,
            // a type+frame-only stack summary for message-less exceptions, and the redacted caused-by
            // chain so a wrapped Selenium
            // root cause (WebDriverException around a TimeoutException/ConnectException) is not lost.
            logger.error("Browser eForm renderer failed: fdid={} baseUrl={} type={} error={} at={} causedBy={}",
                    fdid, baseUrl, e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())),
                    RenderLogRedaction.stackSummary(e), RenderLogRedaction.causeChain(e));
            throw new PDFGenerationException("Browser rendering failed while generating the eForm PDF.");
        } finally {
            // The render grant was already invalidated by the RenderLease's close() (the lease is the
            // first resource of the try above, so it closes before this finally runs).
            // Belt-and-braces after quit: if the quit command timed out against a wedged Chromium,
            // a targeted force-delete of THIS session is what tears the browser down before the
            // render slot is released. Killing the chromedriver process is no longer available --
            // this JVM does not own it -- so the session id captured at creation is the handle.
            teardownQuietly(browser);
            if (!success) {
                deleteQuietly(outputPdfPath);
            }
        }
    }

    /**
     * Real readiness probe for the advisory startup check: launches the pinned headless Chromium
     * exactly as a render would (same binary/sandbox/option resolution), navigates to {@code about:blank},
     * then tears the browser and its chromedriver back down. A real launch is the only honest
     * readiness signal — a mere binary-exists check cannot detect a sandbox that refuses to start
     * or a chromedriver/browser version mismatch, both of which surface only once a session is
     * actually created.
     *
     * <p>{@code about:blank} loads no CARLOS page and carries no fdid, render token, or PHI, so the
     * probe exercises the browser launch path without touching patient data.</p>
     *
     * @throws PDFGenerationException if the renderer cannot launch or fails the navigation probe;
     *         the message carries operator remediation guidance and is redacted (PHI-safe)
     */
    public void verifyRendererReady() throws PDFGenerationException {
        RendererBrowser browser = createDriver(
                buildChromeOptions(resolveChromiumPath(), !sandboxEnabled(), "http://127.0.0.1"));
        ChromiumDriver driver = browser.driver();
        try {
            driver.get("about:blank");
        } catch (RuntimeException e) {
            // A WebDriver failure message can embed a local filesystem path; keep the type plus a
            // redacted message and deliberately do NOT chain the raw throwable (a downstream logger
            // would otherwise re-emit the unredacted text), consistent with the render path's
            // PHI-safe logging.
            throw new PDFGenerationException(
                    "The eForm browser renderer started but failed a basic navigation readiness probe: "
                    + e.getClass().getName() + " " + RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        } finally {
            // Mirrors renderWithSlot: quit, and escalate to a targeted force-delete of this
            // session if quit() could not end it.
            teardownQuietly(browser);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Browser lifecycle and options
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds the pinned launch configuration. The dead-proxy egress lockdown and
     * {@code acceptInsecureCerts} form a paired invariant: insecure certs are acceptable only for
     * loopback rendering, which the proxy configuration guarantees is the only reachable network.
     */
    static ChromeOptions buildChromeOptions(String chromiumBinary, boolean unsandboxed, String allowedOrigin) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-dev-shm-usage",
                "--window-size=" + VIEWPORT_WIDTH + "," + VIEWPORT_HEIGHT,
                "--force-device-scale-factor=1",
                "--hide-scrollbars",
                "--proxy-server=" + DEAD_PROXY,
                "--proxy-bypass-list=" + proxyBypassListFor(allowedOrigin),
                // DevTools over a pipe instead of an ephemeral localhost TCP port, so no other
                // local process can attach to the render browser's control channel.
                "--remote-debugging-pipe",
                // Turn off the FileSystem API. Local file access is otherwise blocked by
                // Chromium's default cross-scheme policy plus the strict scheme gate in
                // isDisallowedRendererRequestUrl. INVARIANT: never add --allow-file-access-from-files
                // or --disable-web-security here — either would let a malicious eForm read local
                // files into the captured PDF.
                "--disable-file-system",
                // Close the WebRTC egress hole: RTCPeerConnection ICE/STUN/TURN is UDP and would
                // bypass the HTTP dead proxy entirely (and emits none of the CDP network events the
                // gate inspects). The load-bearing control is forcing all WebRTC UDP through the
                // (dead) proxy so non-proxied ICE/STUN/TURN cannot leave the host; Chromium has no
                // single "WebRtc" feature flag (an unknown --disable-features name is silently
                // ignored), so we do NOT rely on one.
                "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
                "--disable-background-networking",
                "--disable-extensions",
                "--no-first-run",
                "--no-default-browser-check",
                // Memory governors. This surface renders ONE small same-origin document per session,
                // so Chromium's desktop-scale defaults (unbounded V8 heaps, one renderer per site
                // instance, a GPU process) are pure overhead — and on small deployments the burst of
                // an ungoverned browser tree is what pushes the whole box into memory pressure.
                // Deliberately NOT --single-process/--no-zygote (would break the sandbox) and NOT
                // disabling site isolation (a security posture change): these only cap size/fan-out.
                "--js-flags=--max-old-space-size=" + RENDERER_V8_HEAP_MB,
                "--renderer-process-limit=" + RENDERER_PROCESS_LIMIT,
                // Headless print-to-PDF rasters through Skia in software; the GPU process buys
                // nothing here and costs a process plus its mappings.
                "--disable-gpu");
        if (unsandboxed) {
            // Default posture: OS-level containment is delegated to the container boundary. The operator
            // can restore Chromium's OS sandbox with EFORM_RENDER_SANDBOX=true — see sandboxEnabled().
            options.addArguments("--no-sandbox");
        }
        options.setAcceptInsecureCerts(true);
        if (chromiumBinary != null && !chromiumBinary.isBlank()) {
            options.setBinary(chromiumBinary.trim());
        }
        LoggingPreferences loggingPreferences = new LoggingPreferences();
        loggingPreferences.enable(LogType.PERFORMANCE, Level.ALL);
        loggingPreferences.enable(LogType.BROWSER, Level.SEVERE);
        options.setCapability("goog:loggingPrefs", loggingPreferences);
        return options;
    }

    /**
     * Builds the proxy bypass for the validated loopback origin: exactly the render origin's
     * {@code host:port}, plus the {@code <-loopback>} sentinel that disables Chromium's
     * <em>implicit</em> bypass rules. Without the sentinel Chromium exempts every loopback host
     * and port from the proxy regardless of this list. The sentinel ensures only the exact render
     * origin escapes the dead proxy and blocks requests to other loopback hosts or ports before
     * dispatch.
     * INVARIANT: never remove {@code <-loopback>} as a "simplification" — it is what makes the
     * dead proxy apply to loopback at all.
     */
    // IMPROPER_UNICODE: equalsIgnoreCase here classifies the literal URI scheme ("https") to pick a
    // default port; a case-insensitive protocol-name compare, not an authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal URI scheme to choose a default port; not a security or authorization decision")
    static String proxyBypassListFor(String allowedOrigin) {
        URI uri = URI.create(allowedOrigin);
        int port = uri.getPort();
        if (port == -1) {
            port = SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        // URI.getHost() keeps IPv6 brackets ("[::1]"), so the entry is emitted as-is.
        // ORDER IS LOAD-BEARING (verified empirically against Chromium 148): the sentinel must
        // precede the explicit entry — "entry;<-loopback>" subtracts the earlier explicit
        // loopback entry too and dead-proxies the render origin itself, failing every render.
        return "<-loopback>;" + uri.getHost() + ":" + port;
    }

    /**
     * Whether the operator has opted into Chromium's OS-level sandbox with
     * {@code EFORM_RENDER_SANDBOX=true}.
     *
     * <p>Default is <strong>unsandboxed</strong> ({@code --no-sandbox}) so the renderer starts out of
     * the box on the common deployment shape (Tomcat as root / a container without unprivileged user
     * namespaces), where Chromium's sandbox cannot initialize and a sandboxed launch would otherwise
     * fail every render. In that default posture OS-level containment is delegated to the container
     * boundary; all the <em>other</em> renderer controls (loopback-only egress via the dead proxy,
     * {@code --disable-file-system}, WebRTC UDP lockdown, DevTools-over-pipe, and the sessionless
     * render token) remain active regardless.</p>
     *
     * <p>Hardened deployments that run the renderer as a non-root user with unprivileged user
     * namespaces enabled should set {@code EFORM_RENDER_SANDBOX=true} to keep the OS sandbox; when
     * set, a sandboxed launch that cannot start fails closed (see {@link #createDriver}) rather than
     * silently degrading to {@code --no-sandbox}.</p>
     */
    static boolean sandboxEnabled() {
        return "true".equals(System.getenv(ENV_REQUIRE_SANDBOX));
    }

    /**
     * A started render-browser session plus everything teardown needs.
     *
     * <p>INVARIANT (this replaces the old "service is non-null on both paths"): {@code serviceUri}
     * and {@code sessionId} are both non-null and are captured HERE, at creation.
     * <ul>
     *   <li>{@code serviceUri} is the exact endpoint this session was created against. The teardown
     *       backstop MUST use this value rather than re-reading the property: a property edited
     *       mid-render would otherwise send the force-delete to a different chromedriver and leave a
     *       browser holding a rendered PHI page alive.</li>
     *   <li>{@code sessionId} is captured here because {@code RemoteWebDriver.quit()} nulls its own
     *       session id in a {@code finally} EVEN WHEN THE QUIT FAILS. By the time
     *       {@link #quitQuietly} reports failure, {@code driver.getSessionId()} is already null and
     *       the backstop would have nothing to address.</li>
     * </ul>
     */
    record RendererBrowser(ChromiumDriver driver, URI serviceUri, SessionId sessionId) {
        RendererBrowser {
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(serviceUri, "serviceUri captured at session creation");
            Objects.requireNonNull(sessionId, "sessionId captured at session creation");
        }
    }

    /**
     * A ChromiumDriver bound to an already-running chromedriver instead of one this JVM spawned.
     *
     * <p>Why this exists rather than {@code new RemoteWebDriver(...)}: {@code executeCdpCommand} is
     * declared on {@code HasCdp}, which {@code RemoteWebDriver} does not implement. The obvious
     * workarounds do not work either — {@code new Augmenter().augment(driver)} yields a proxy that
     * implements the interface but never registers the CDP command on the executor (Augmenter reads
     * {@code AugmenterProvider}, never {@code AdditionalHttpCommands}, and {@code HttpCommandExecutor}
     * performs no service lookup), so the first CDP call throws {@code UnsupportedCommandException};
     * and {@code getExecuteMethod()} is {@code protected}, reachable only from a subclass.
     */
    static final class RemoteRenderBrowser extends ChromiumDriver {
        RemoteRenderBrowser(URL chromedriverUrl, ChromeOptions options, ClientConfig clientConfig) {
            super(new HttpCommandExecutor(new AddHasCdp().getAdditionalCommands(), chromedriverUrl, clientConfig),
                  options, ChromeOptions.CAPABILITY, clientConfig);
            // LOAD-BEARING, and silent if omitted. ChromiumDriver.executeCdpCommand returns
            // Map.of() -- NOT an error -- when `cdp` is null (verified in bytecode:
            // getfield cdp; ifnonnull; invokestatic Map.of; areturn). Without this assignment every
            // Page.printToPDF hands back an empty map and printToPdf() reports "returned an empty
            // PDF" for EVERY render, forever, while pointing at the wrong cause.
            this.cdp = new AddHasCdp().getImplementation(getCapabilities(), getExecuteMethod());
        }
    }

    /**
     * Per-command HTTP client configuration for the chromedriver connection. Bounds every blocking
     * WebDriver/CDP call at {@link #WEBDRIVER_COMMAND_READ_TIMEOUT} instead of Selenium's ~180s
     * default so a wedged Chromium cannot hold a render slot for minutes past the render budget.
     */
    static ClientConfig rendererClientConfig() {
        return ClientConfig.defaultConfig()
                .readTimeout(WEBDRIVER_COMMAND_READ_TIMEOUT)
                .connectionTimeout(WEBDRIVER_CONNECTION_TIMEOUT);
    }

    private RendererBrowser createDriver(ChromeOptions options) throws PDFGenerationException {
        // Validate the configured service URL FIRST and in its own narrow catch, so a bad
        // eform_pdf_browser_service_url surfaces as a config-specific error naming the property
        // instead of being swallowed by the broad catch below and misreported as a Chromium sandbox
        // failure that sends operators to change user namespaces.
        URI serviceUri;
        try {
            serviceUri = validateBrowserServiceUrl(resolveBrowserServiceUrl());
        } catch (IllegalArgumentException e) {
            // Name the PROPERTY, never the value: the URL may carry the chromedriver --url-base
            // capability token, and this message reaches clinician-visible surfaces.
            throw new PDFGenerationException(
                    "The configured " + SERVICE_URL_PROPERTY + " is not a usable loopback chromedriver "
                    + "URL: " + RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        }
        // Pre-validate the configured Chromium binary path (if any) with the same up-front,
        // config-specific treatment. Note this check is now ADVISORY: chromedriver resolves the
        // binary on the browser host, so its own error is authoritative. It still earns its place
        // because both run on the same host and a typo is worth catching here.
        String chromiumPath = resolveChromiumPath();
        if (chromiumPath != null) {
            try {
                PathValidationUtils.validateConfiguredFile(chromiumPath, CHROME_PATH_PROPERTY);
            } catch (RuntimeException e) {
                throw new PDFGenerationException(
                        "The configured " + CHROME_PATH_PROPERTY + " does not point to a usable Chromium "
                        + "binary. Fix the property, or unset it to let chromedriver resolve the browser.", e);
            }
        }
        try {
            return startSessionWithinBudget(serviceUri, options);
        } catch (RuntimeException e) {
            // causeChain is REQUIRED here, not decoration: for a connect failure the informative part
            // ("Connection refused") is always a nested cause, so type+message alone would log a
            // useless top-level WebDriverException. Still never chained into the thrown exception --
            // a downstream handler that logs the chain would re-emit an embedded URL unredacted.
            logger.error("eForm render browser session failure detail: type={} error={} at={} causedBy={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())),
                    RenderLogRedaction.stackSummary(e), RenderLogRedaction.causeChain(e));
            if (isServiceUnreachable(e)) {
                logger.error("The eForm render browser service is not reachable. Start it "
                        + "(systemctl status carlos-emr-chromedriver) or correct {} in carlos.properties.",
                        SERVICE_URL_PROPERTY);
                throw browserServiceUnavailable();
            }
            throw chromiumStartupFailure(!sandboxEnabled());
        }
    }

    /**
     * True when {@code t}'s cause chain shows the chromedriver endpoint was never reached, as opposed
     * to reached-but-unable-to-start-a-browser. Classified by TYPE only, never by message: WebDriver
     * exception messages are assembled from {@code getAdditionalInformation()} and are not a stable
     * discriminator (the Selenium smoke test carries the same hard-won warning).
     */
    static boolean isServiceUnreachable(Throwable t) {
        // Bounded rather than guarded on self-reference: `c != c.getCause()` only catches a
        // throwable that causes ITSELF, and a two-element cycle (a causes b, b causes a) would spin
        // forever. Java forbids direct self-causation but not a cycle, and this runs on a failure
        // path where the chain is third-party.
        int depth = 0;
        for (Throwable c = t; c != null && depth < MAX_CAUSE_DEPTH; c = c.getCause(), depth++) {
            if (c instanceof java.net.ConnectException
                    || c instanceof java.net.http.HttpConnectTimeoutException
                    || c instanceof org.openqa.selenium.remote.http.ConnectionFailedException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Operator-facing failure for an unreachable render browser service. Deliberately terse and
     * URL-free: this message reaches clinician-visible surfaces, and the configured URL may carry the
     * chromedriver --url-base capability token. The remediation goes in the log line, which names the
     * property key and the unit -- never the value. Never carries a cause, matching
     * {@link #chromiumStartupFailure}.
     */
    static PDFGenerationException browserServiceUnavailable() {
        return new PDFGenerationException("The eForm render browser service is unavailable.");
    }

    /**
     * Creates the render session on a bounded background thread so a doomed launch fails within
     * {@link #DRIVER_START_TIMEOUT} instead of blocking on chromedriver's internal ~60s browser-start
     * timeout.
     *
     * <p>The old implementation killed the chromedriver PROCESS on timeout, which unblocked the
     * doomed constructor and guaranteed no browser leaked. This JVM no longer owns that process, so
     * that guarantee is gone and is replaced by a late-quit hook: if the cancelled session arrives
     * anyway, it is quit immediately. The residual window is acceptable only because navigation has
     * not happened yet -- a timed-out session is an {@code about:blank} browser holding no PHI.
     */
    private RendererBrowser startSessionWithinBudget(URI serviceUri, ChromeOptions options) {
        // shutdown(), NOT shutdownNow(). shutdownNow() interrupts the in-flight task, and an
        // interrupted session-create abandons the POST /session read — so if chromedriver already
        // made the session, its id never reaches this JVM and there is nothing left to delete.
        // shutdown() does not interrupt, does not block, and the daemon worker still terminates.
        ExecutorService starter = Executors.newSingleThreadExecutor(runnable -> { // NOSONAR java:S2095 - shutdown() in finally; close() would block the watchdog
            Thread thread = new Thread(runnable, "eform-render-driver-start");
            thread.setDaemon(true);
            return thread;
        });
        // The session is published from INSIDE the callable rather than read off the Future,
        // because a Future the caller gave up on cannot hand it back — see reapLateSession.
        AtomicReference<RendererBrowser> created = new AtomicReference<>();
        AtomicBoolean abandoned = new AtomicBoolean();
        try {
            Future<RendererBrowser> pending = starter.submit(() -> {
                ChromiumDriver driver = new RemoteRenderBrowser(
                        serviceUri.toURL(), options, rendererClientConfig().baseUri(serviceUri));
                RendererBrowser browser;
                try {
                    browser = new RendererBrowser(driver, serviceUri, driver.getSessionId());
                } catch (RuntimeException e) {
                    // The session exists but we cannot describe it (e.g. a null session id). Quit it
                    // here: nothing downstream will ever see a handle to it.
                    quitQuietly(driver);
                    throw e;
                }
                created.set(browser);
                if (abandoned.get()) {
                    // The caller timed out between the session being created and it being
                    // published. Reap it on this thread; nobody else holds it.
                    RendererBrowser mine = created.getAndSet(null);
                    if (mine != null) {
                        logger.warn("eForm render browser session arrived after its start budget "
                                + "expired; quitting it so it cannot leak a browser process");
                        teardownQuietly(mine);
                    }
                    throw new IllegalStateException("render session abandoned after its start budget");
                }
                return browser;
            });
            try {
                return pending.get(DRIVER_START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                reapLateSession(pending, created, abandoned);
                // KNOWN OVERSHOOT: the caller's finally releases this render's slot now, while the
                // reaper may keep the late session's browser alive for up to LATE_SESSION_REAP_TIMEOUT.
                // Under repeated start-timeouts (a memory-starved host — the same condition that causes
                // them) live browser TREES can therefore briefly exceed MAX_CONCURRENT_RENDERS. Total
                // browser-tree MEMORY stays bounded regardless: the carlos-emr-chromedriver unit's
                // MemoryHigh/MemoryMax cgroup ceiling covers every tree the driver spawned, so the
                // overshoot cannot compound the pressure that caused it. Holding the slot until the
                // reaper resolves would close the gap but moves slot ownership across threads —
                // deliberately not done without test scaffolding for that handoff.
                throw new IllegalStateException(
                        "Chromium session creation exceeded the " + DRIVER_START_TIMEOUT.toSeconds()
                        + "s startup budget", timeout);
            } catch (ExecutionException failure) {
                // The constructor threw. If it threw AFTER chromedriver created the session (e.g.
                // the HTTP-client wiring in the ChromiumDriver constructor tail failed), that
                // session's browser is unaddressable from here — the constructor never returned,
                // so no session id exists in this JVM. Like the start-timeout case it is an
                // about:blank browser holding no PHI. NOTHING in-process ever reaps it: the only
                // sweep is for temp FILES, and chromedriver has no session timeout. What bounds
                // it is the driver service's lifecycle — on the .deb, PartOf= ties it to every
                // carlos-emr restart and the unit's MemoryMax caps the trees' total memory; on
                // other deployments the operator restart named in the WARN is the reclaim. The
                // leak must be VISIBLE, not silent, because a fault in the constructor tail
                // repeats on every render and each attempt can strand one browser tree.
                logger.warn("Chromium session constructor failed; if chromedriver had already "
                        + "created the session, that browser is unaddressable from this JVM and "
                        + "persists until the driver service restarts — repeated failures here "
                        + "strand one browser per attempt: restart the chromedriver service "
                        + "(about:blank only — no document was loaded).");
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Chromium session creation failed", cause);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                reapLateSession(pending, created, abandoned);
                throw new IllegalStateException("Interrupted while starting the Chromium renderer", interrupted);
            }
        } finally {
            starter.shutdown();
        }
    }

    /**
     * Quits a session that arrives after its start budget expired.
     *
     * <p>Deliberately does NOT call {@code Future.cancel(true)}. A cancelled {@code FutureTask}
     * discards its result and every {@code get()} throws {@link CancellationException} immediately,
     * so a reaper written around {@code pending.get()} after a cancel can never see the session and
     * never tears anything down — it just logs nothing while the browser stays alive. Cancelling
     * also interrupts the in-flight {@code POST /session}, which can lose the session id before this
     * JVM ever learns it, leaving nothing to address. So: let the exchange finish, bounded, and take
     * the handle the callable published.
     *
     * <p>The residual hole is honest and documented: if the interrupt or a connection failure means
     * the id never arrives, no targeted teardown is possible and
     * {@code systemctl restart carlos-emr-chromedriver} is the backstop. A timed-out session has not
     * navigated yet, so it is an {@code about:blank} browser holding no clinical data.
     */
    private static void reapLateSession(Future<RendererBrowser> pending,
            AtomicReference<RendererBrowser> created, AtomicBoolean abandoned) {
        abandoned.set(true);
        Thread reaper = new Thread(() -> {
            try {
                pending.get(LATE_SESSION_REAP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException | CancellationException ignored) {
                // Fall through: the callable may still have published a session before failing.
            }
            // getAndSet: whoever takes it non-null owns the teardown, so the callable's own
            // self-reap and this thread can never both quit the same session.
            RendererBrowser late = created.getAndSet(null);
            if (late != null) {
                logger.warn("eForm render browser session arrived after its start budget expired; "
                        + "quitting it so it cannot leak a browser process");
                teardownQuietly(late);
            }
        }, "eform-render-late-session-reaper");
        reaper.setDaemon(true);
        reaper.start();
    }

    /**
     * Builds the operator-facing Chromium launch-failure exception. Never carries a cause: the
     * raw WebDriver throwable can embed local filesystem paths in its message, and a downstream
     * handler that logs this exception's chain would re-emit them unredacted — the redacted
     * "Chromium startup failure detail" log line at the catch site is the diagnostic record.
     */
    static PDFGenerationException chromiumStartupFailure(boolean unsandboxed) {
        if (!unsandboxed) {
            // The operator opted into Chromium's OS sandbox (EFORM_RENDER_SANDBOX=true) but it could not
            // start; fail closed rather than silently degrading to --no-sandbox. The message admits the
            // non-sandbox causes too (bad/missing browser or driver) so a misconfigured install is not
            // misread as purely a namespace problem.
            return new PDFGenerationException(
                    "Unable to start the sandboxed headless Chromium renderer for eForms. "
                    + "Common causes: missing or incompatible Chromium/chromedriver, or a kernel "
                    + "without unprivileged user namespaces. If the browser installation is correct, "
                    + "enable unprivileged user namespaces and run as a non-root user, or unset "
                    + "EFORM_RENDER_SANDBOX to run with --no-sandbox where the container provides isolation.");
        }
        return new PDFGenerationException("Unable to start the headless Chromium renderer for eForms.");
    }

    /**
     * Ends the session, returning whether {@code quit()} actually succeeded so the caller can escalate
     * to {@link #forceDeleteSessionQuietly}.
     */
    private static boolean quitQuietly(ChromiumDriver driver) {
        if (driver == null) {
            return true;
        }
        try {
            driver.quit();
            return true;
        } catch (RuntimeException e) {
            // Never pass the raw WebDriver throwable: its message/stack can embed the loopback render
            // URL (fdid + render token). Log the type and a redacted message only.
            logger.debug("Unable to quit browser eForm renderer driver cleanly: type={} error={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            return false;
        }
    }

    /**
     * Full teardown for a render session: quit, and escalate to a targeted force-delete if that fails.
     *
     * <p>This is the direct replacement for the old {@code stopServiceQuietly} backstop. That method
     * killed the chromedriver process, which is how a wedged Chromium was guaranteed to be torn down
     * before the render slot was released. This JVM no longer owns that process; what it can still do
     * is address the one session by id.
     */
    // Package-private for EFormBrowserRemoteDriverFakeChromedriverUnitTest, which pins the
    // quit-failure -> targeted-DELETE escalation against a fake chromedriver.
    static void teardownQuietly(RendererBrowser browser) {
        if (browser == null) {
            return;
        }
        if (!quitQuietly(browser.driver())) {
            forceDeleteSessionQuietly(browser.serviceUri(), browser.sessionId());
        }
    }

    /**
     * Second, independent teardown attempt for the exact session {@code quit()} failed to end.
     *
     * <p>Deliberately uses a fresh {@link java.net.http.HttpClient} with a short deadline rather than
     * Selenium: the wedged driver's client may be blocked on the 90s command read timeout, and this
     * backstop must not inherit that. chromedriver kills the session's browser process tree on DELETE
     * even when the browser is unresponsive to WebDriver commands.
     *
     * <p>WARN, not DEBUG, for the same reason the old backstop was: a leaked browser holding a
     * rendered PHI page in memory must be visible at default log levels. The session id is logged (it
     * is not PHI, and it is what an operator needs against the chromedriver journal); the URI is
     * redacted because it may carry the --url-base capability token.
     */
    private static void forceDeleteSessionQuietly(URI serviceUri, SessionId sessionId) {
        if (serviceUri == null || sessionId == null) {
            return;
        }
        try (java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(BACKSTOP_TIMEOUT)
                .build()) {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(serviceUri + "/session/" + sessionId))
                    .timeout(BACKSTOP_TIMEOUT)
                    .DELETE()
                    .build();
            java.net.http.HttpResponse<Void> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            // send() does not throw on 4xx/5xx. Without this check a wedged chromedriver returning
            // 500 — or a 404 from something that is not chromedriver at all — would be reported as
            // "force-deleted it", and an operator would close the ticket while the browser holding
            // the rendered page is still running. That is precisely what this WARN exists to catch.
            if (response.statusCode() / 100 == 2) {
                logger.warn("Browser eForm renderer session {} did not quit cleanly; force-deleted it "
                        + "against the render browser service", sessionId);
            } else {
                logger.warn("Force-delete of browser eForm renderer session {} was refused (HTTP {}). "
                        + "A browser holding a rendered page may still be running; "
                        + "systemctl restart carlos-emr-chromedriver clears it.",
                        sessionId, response.statusCode());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while force-deleting browser eForm renderer session {}", sessionId);
        } catch (RuntimeException | java.io.IOException e) {
            logger.warn("Unable to force-delete browser eForm renderer session {}: type={} error={}. "
                    + "A browser holding a rendered page may still be running; "
                    + "systemctl restart carlos-emr-chromedriver clears it.",
                    sessionId, e.getClass().getName(),
                    RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stabilization and capture
    // ---------------------------------------------------------------------------------------------

     /**
     * Waits for both the page DOM and its resource activity to become quiet, then reports whether it
     * reached a quiet window.
     *
     * @return {@code true} when the stabilization cap expired with the DOM still changing, meaning the
     *         page was captured mid-assembly. The caller MUST fold this into
     *         {@link EFormRenderCompletenessReport}: a WARN alone is invisible to the gate, and the
     *         forms this cap exists for (editor-driven letters that build their body after onload) are
     *         exactly the ones that print half-assembled.
     * @throws PDFGenerationException if the page reported a stabilization error
     */
    boolean settle(ChromiumDriver driver, long deadlineNanos, int fdid) throws PDFGenerationException {
        // Deferred one-shot timers are tracked by eform-runtime-compat.js and awaited by
        // STABILIZE_ASYNC_JS below.  Do not impose a blind grace sleep here: static forms
        // can proceed after the same measured quiet-window check as dynamic forms.
        checkDeadline(deadlineNanos);
        Object settleResult = driver.executeAsyncScript(STABILIZE_ASYNC_JS);
        boolean capped = false;
        if (settleResult != null) {
            if ("TIMERS_PENDING".equals(settleResult)) {
                // The form's own deferred work had not finished when the wait expired, so whatever
                // those timers populate is missing from the capture. Reported through the same
                // component as the DOM cap below: the clinical condition is identical — the document
                // was captured before it finished building — and so is the clinician's decision. The
                // log distinguishes them so an operator can tell which wait ran out.
                capped = true;
                logger.warn("eForm page still had pending legacy timers at the stabilization cap; "
                        + "capturing as-is: fdid={}", fdid);
            } else if ("CAPPED".equals(settleResult)) {
                // The DOM never reached a quiet window before the 5s cap — a form with a perpetual
                // timer/animation, or a broken editor that keeps mutating. The render proceeds with the
                // captured-as-is page, but the clinician must be told: the captured document may be
                // missing content that was still being written. Fixed string + fdid only — no page content.
                capped = true;
                logger.warn("eForm page DOM never quiesced within the stabilization cap; capturing as-is: fdid={}", fdid);
            } else {
                // A non-null, non-CAPPED result is a page-controlled error string: a hostile eForm can
                // throw an Error whose message carries arbitrary text — potentially PHI read from the
                // form's own rendered fields — and redactUrls() only strips URLs/paths, not free text.
                // Never propagate or log its content; use a fixed message and record only that a
                // stabilization error occurred.
                logger.warn("eForm page stabilization reported an error (content suppressed as potential PHI)");
                throw new PDFGenerationException("Browser rendering failed while stabilizing the eForm page.");
            }
        }
        checkDeadline(deadlineNanos);
        return capped;
    }

    /**
     * Emits the fully-settled render surface as a native, text-layer PDF via Chromium's
     * {@code Page.printToPDF} and writes it to {@code outputPdfPath}. All margins are zeroed and
     * {@code preferCSSPageSize} is set so the injected {@code @page} sizes (see
     * {@link #buildPageSizeCss}) — or the form's own {@code @page} rules — drive geometry, and
     * {@code scale} is pinned to 1 so Chromium never rescales the form to fit a default paper.
     * {@code printBackground} is on so the page-background {@code <img>} content and any CSS
     * backgrounds print. {@code ReturnAsBase64} hands the PDF back inline in the command result (the
     * same transport as the former screenshot path), so there is no CDP stream to read back.
     */
    private void printToPdf(ChromiumDriver driver, Path outputPdfPath, long deadlineNanos)
            throws IOException, PDFGenerationException {
        checkDeadline(deadlineNanos);
        Map<String, Object> result = driver.executeCdpCommand("Page.printToPDF", Map.of(
                "preferCSSPageSize", Boolean.TRUE,
                "printBackground", Boolean.TRUE,
                "scale", 1.0d,
                "marginTop", 0.0d,
                "marginBottom", 0.0d,
                "marginLeft", 0.0d,
                "marginRight", 0.0d,
                "transferMode", "ReturnAsBase64"));
        Object data = result.get("data");
        if (!(data instanceof String encoded) || encoded.isEmpty()) {
            throw new PDFGenerationException("Browser rendering returned an empty PDF for the eForm.");
        }
        // Bound the output size BEFORE decoding: ReturnAsBase64 already materialized the full string on
        // the heap, but a hard cap here prevents the second (decoded byte[]) allocation and an
        // over-cap file, so a pathological/huge render (many high-res image pages, a runaway form) can
        // spike Tomcat heap by at most ~1 PDF worth, not several. base64 is ~4/3 the byte size.
        if ((long) encoded.length() > (MAX_PDF_BYTES / 3L) * 4L) {
            throw new PDFGenerationException("Browser rendering produced a PDF larger than the allowed maximum.");
        }
        Files.write(outputPdfPath, Base64.getDecoder().decode(encoded));
        logger.debug("Browser eForm renderer printed the eForm to a native PDF");
    }

    /**
     * Validates the complete result of {@link #COMPUTE_PAGE_GEOMETRY_JS}. Every omission field is
     * required because it contributes to the exact user-approval report.
     */
    static PageGeometry readPageGeometry(Object rawGeometry) throws PDFGenerationException {
        if (!(rawGeometry instanceof Map<?, ?> rawMap)) {
            throw new PDFGenerationException("Browser rendering returned an unexpected page-geometry result.");
        }
        List<PageSize> pages = readPageSizes(rawMap.get("pages"));
        double rawCount = requiredNonNegativeNumber(rawMap, "excludedCount");
        if (Math.floor(rawCount) < rawCount || rawCount > Integer.MAX_VALUE) {
            throw new PDFGenerationException(
                    "Browser rendering returned an invalid excluded-content count.");
        }
        int excludedCount = (int) rawCount;
        double excludedHeight = requiredNonNegativeNumber(rawMap, "excludedHeight");
        // Off-page elements the detector classified as non-clinical decoration (a license
        // or attribution badge, a branding masthead). Reported for transparency, NEVER
        // folded into excludedContentElements, so they do not withhold a patient document.
        double rawDecorative = requiredNonNegativeNumber(rawMap, "decorativeExcludedCount");
        if (Math.floor(rawDecorative) < rawDecorative || rawDecorative > Integer.MAX_VALUE) {
            throw new PDFGenerationException(
                    "Browser rendering returned an invalid decorative-content count.");
        }
        int decorativeExcludedCount = (int) rawDecorative;
        boolean signatureBroken = requiredBoolean(rawMap, "signatureBroken");
        boolean timerCompatibilityFailure = requiredBoolean(
                rawMap, "timerCompatibilityFailure");
        if (timerCompatibilityFailure) {
            // The two sub-causes need very different responses — a shim that never installed is a
            // CARLOS-side load/ordering problem, while a shim reporting failure means the form's own
            // legacy string timer really was blocked — and the single merged boolean cannot tell an
            // operator which happened. Booleans only; nothing here can carry form content.
            logger.warn("Browser eForm renderer timer compatibility failed: shimMissing={}",
                    rawMap.get("timerCompatShimMissing"));
        }
        boolean labDecisionSupportStubbed = requiredBoolean(
                rawMap, "labDecisionSupportStubbed");
        boolean providerStampMissing = requiredBoolean(rawMap, "providerStampMissing");
        return new PageGeometry(pages, excludedCount, excludedHeight, decorativeExcludedCount,
                describedElements(rawMap, "excludedDetails"),
                describedElements(rawMap, "decorativeDetails"),
                signatureBroken,
                timerCompatibilityFailure, labDecisionSupportStubbed, providerStampMissing);
    }

    /**
     * Reads one of the scan's structural-descriptor lists.
     *
     * <p>Diagnostic only, and deliberately lenient: a missing or malformed list must never fail a
     * render that otherwise succeeded, because these strings exist to explain a withheld document,
     * not to gate one. Anything unexpected degrades to an empty list.</p>
     *
     * <p>The scan emits structure only — tag, id, class, pixel height and the character COUNT of the
     * element's text. It never emits the text, because an off-page block is precisely where clinical
     * prose ends up and these strings are written to the application log. Each entry is length-capped
     * here as a second bound, so a form with pathological class attributes cannot produce an
     * unbounded log line even though the browser side already caps the number of entries.</p>
     */
    private static List<String> describedElements(Map<?, ?> rawMap, String key) {
        if (!(rawMap.get(key) instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                // The id and class come from author-controlled markup and an HTML attribute may
                // hold a literal newline, so an element could otherwise inject its own line into
                // the log. LogSafe escapes the control characters; the length cap still applies.
                .map(LogSafe::sanitize)
                .map(entry -> entry.length() > 200 ? entry.substring(0, 200) + "…" : entry)
                .limit(20)
                .toList();
    }

    private static double requiredNonNegativeNumber(Map<?, ?> rawMap, String key)
            throws PDFGenerationException {
        if (!(rawMap.get(key) instanceof Number number)) {
            throw new PDFGenerationException(
                    "Browser rendering returned a missing or non-numeric " + key + ".");
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0) {
            throw new PDFGenerationException(
                    "Browser rendering returned an invalid " + key + ".");
        }
        return value;
    }

    private static boolean requiredBoolean(Map<?, ?> rawMap, String key)
            throws PDFGenerationException {
        if (!(rawMap.get(key) instanceof Boolean value)) {
            throw new PDFGenerationException(
                    "Browser rendering returned a missing or non-boolean " + key + ".");
        }
        return value;
    }

    /**
     * Validates the {@code pages} list (extracted by {@link #readPageGeometry} from the
     * {@link #COMPUTE_PAGE_GEOMETRY_JS} result) into bounded {@link PageSize} values. The geometry
     * comes from the (clinic-authored) eForm DOM, so it is
     * fail-closed: too many pages, a non-finite dimension, or a dimension past
     * {@link #MAX_PAGE_DIMENSION} aborts the render rather than feeding a pathological {@code @page}
     * size into Chromium. A zero/negative measured box is skipped (that page falls back to Chromium's
     * default paper). An empty result is legal — a free-flow form authored no {@code pageN} divs.
     */
    static List<PageSize> readPageSizes(Object rawSizes) throws PDFGenerationException {
        if (!(rawSizes instanceof List<?> rawList)) {
            throw new PDFGenerationException("Browser rendering returned an unexpected page-geometry result.");
        }
        if (rawList.size() > MAX_PAGE_COUNT) {
            throw new PDFGenerationException("Browser rendering produced too many pages to size safely.");
        }
        List<PageSize> sizes = new ArrayList<>();
        for (Object rawSize : rawList) {
            if (!(rawSize instanceof Map<?, ?> rawMap)) {
                throw new PDFGenerationException("Browser rendering returned an unexpected page-geometry entry.");
            }
            String id = String.valueOf(rawMap.get("id"));
            double width = numberValue(rawMap, "width");
            double height = numberValue(rawMap, "height");
            // Fail closed on non-finite geometry (NaN/Infinity): every comparison below is false for
            // NaN, so it would otherwise slip through the bounds checks and reach the injected @page
            // CSS as an invalid size, failing the whole render unpredictably.
            if (!Double.isFinite(width) || !Double.isFinite(height)) {
                throw new PDFGenerationException("Browser rendering returned a non-finite page dimension.");
            }
            if (width <= 0 || height <= 0) {
                continue;
            }
            if (width > MAX_PAGE_DIMENSION || height > MAX_PAGE_DIMENSION) {
                throw new PDFGenerationException("Browser rendering page exceeds the maximum page dimension.");
            }
            sizes.add(new PageSize(id, width, height));
        }
        return sizes;
    }

    private static double numberValue(Map<?, ?> rawMap, String key) throws PDFGenerationException {
        Object value = rawMap.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new PDFGenerationException("Browser rendering returned a non-numeric page dimension.");
    }

    /**
     * Builds the {@code @page} sizing and pagination CSS for {@link #INJECT_PAGE_SIZE_CSS_JS}. When
     * every page shares a size (the common single-scan-geometry form) one anonymous
     * {@code @page { size }} rule covers them all; when sizes differ (e.g. a portrait page followed
     * by a landscape one) a CSS named page is emitted per page div and bound to it by id, so each
     * printed page keeps its authored geometry.
     *
     * <p>Every page div additionally gets an explicit pagination contract, because the legacy corpus
     * authors <em>no</em> page-break CSS at all (the raster path photographed regions and never
     * needed any): its {@code height} is pinned to the printed page height so the div's flow extent
     * can never exceed one page (a fractional-height background otherwise spills a mostly-blank
     * page and shifts every later field off its background — the "extra blank pages between pages,
     * checkboxes misaligned" corpus regression), {@code overflow: hidden} clips content past the
     * page box exactly as the region capture did, {@code margin: 0} removes inter-page gaps, and
     * {@code break-after: page} forces each div onto its own printed page. The LAST div instead gets
     * {@code break-after: auto} so a form whose final div carries an authored inline
     * {@code page-break-after: always} does not emit a trailing blank page ({@code !important} in an
     * author stylesheet outranks a non-important inline declaration, so these rules win over inline
     * authored styles in both directions).</p>
     *
     * <p>Sizes are px (Chromium converts to the PDF's points at 96dpi), matching the legacy raster
     * path's {@code px * 72/96} page boxes. Returns empty CSS for an empty list (never injected).</p>
     */
    static String buildPageSizeCss(List<PageSize> pages) {
        if (pages.isEmpty()) {
            return "";
        }
        long firstWidth = (long) Math.ceil(pages.get(0).width());
        long firstHeight = (long) Math.ceil(pages.get(0).height());
        boolean uniform = pages.stream().allMatch(page ->
                (long) Math.ceil(page.width()) == firstWidth && (long) Math.ceil(page.height()) == firstHeight);
        StringBuilder css = new StringBuilder();
        if (uniform) {
            css.append("@page { size: ").append(cssPx(pages.get(0).width())).append(' ')
                    .append(cssPx(pages.get(0).height())).append("; margin: 0; }\n");
        } else {
            for (int index = 0; index < pages.size(); index++) {
                PageSize page = pages.get(index);
                css.append("@page carlosPage").append(index + 1).append(" { size: ")
                        .append(cssPx(page.width())).append(' ').append(cssPx(page.height()))
                        .append("; margin: 0; }\n");
            }
        }
        for (int index = 0; index < pages.size(); index++) {
            PageSize page = pages.get(index);
            boolean last = index == pages.size() - 1;
            // The id came through the /^page\d+$/i geometry filter, so it is a safe CSS id selector.
            css.append('#').append(page.id()).append(" {");
            if (!uniform) {
                css.append(" page: carlosPage").append(index + 1).append(';');
            }
            css.append(" height: ").append(cssPx(page.height())).append(" !important;")
                    .append(" margin: 0 !important;")
                    .append(" overflow: hidden !important;")
                    .append(" break-inside: avoid !important;")
                    .append(" break-after: ").append(last ? "auto" : "page").append(" !important; }\n");
        }
        return css.toString();
    }

    /** CSS px length rounded up to a whole pixel, so a fractional content box never clips content. */
    private static String cssPx(double value) {
        return ((long) Math.ceil(value)) + "px";
    }

    /** Immutable authored page size in CSS px, tagged with the source {@code pageN} div id. */
    record PageSize(String id, double width, double height) {
        /**
         * Enforces the dimension invariants structurally rather than leaving them to the reader.
         * {@code buildPageSizeCss} applies {@code (long) Math.ceil(...)} to whatever it is handed, so a
         * NaN or negative that slipped past {@code readPageSizes} would be emitted as a nonsense CSS
         * length instead of failing the render.
         */
        PageSize {
            Objects.requireNonNull(id, "page id must not be null");
            if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Page dimensions must be finite and positive");
            }
        }
    }

    /**
     * Authored page sizes and sanitized omission signals used by the completeness report.
     */
    record PageGeometry(List<PageSize> pages, int excludedCount, double excludedHeight,
            int decorativeExcludedCount,
            List<String> excludedDetails, List<String> decorativeDetails,
            boolean signatureBroken, boolean timerCompatibilityFailure,
            boolean labDecisionSupportStubbed, boolean providerStampMissing) {
        PageGeometry {
            // Defensive copy: readPageSizes hands back a mutable ArrayList.
            pages = List.copyOf(Objects.requireNonNull(pages, "pages must not be null"));
            // Diagnostic descriptors are optional: an older or partial scan result simply has none.
            excludedDetails = excludedDetails == null ? List.of() : List.copyOf(excludedDetails);
            decorativeDetails = decorativeDetails == null ? List.of() : List.copyOf(decorativeDetails);
            if (excludedCount < 0 || !Double.isFinite(excludedHeight) || excludedHeight < 0) {
                throw new IllegalArgumentException(
                        "Excluded-content counters must be non-negative and finite");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Render gates (fail-closed): main-document status, loopback-only egress, console errors
    // ---------------------------------------------------------------------------------------------

    // Package-private for the unit test that pins the console-log-unavailable fail-closed branch.
    EFormRenderCompletenessReport enforceRenderGates(ChromiumDriver driver, List<LogEntry> performanceEntries,
            Integer latchedMainStatus, String baseUrl, int fdid,
            List<String> severeConsoleDetailsOut) throws PDFGenerationException {
        NetworkGateScan scan = scanNetworkEvents(
                performanceEntries.stream().map(LogEntry::getMessage).toList(),
                originOf(baseUrl));
        int disallowedRequests = scan.disallowedRequests();
        // Prefer the status latched immediately after navigation — at that point only the top-level
        // document has responded, so it is unambiguously the MAIN document's status. Fall back to the
        // full-scan value only if the latch is missing. This prevents a later same-origin iframe's
        // Document 200 from standing in for a missing/failed main-document response.
        Integer mainDocumentStatus = latchedMainStatus != null ? latchedMainStatus : scan.mainDocumentStatus();

        int severeConsoleEntries = 0;
        // Dedupe on the RAW message BEFORE describeConsoleError: a form erroring in a
        // rAF/interval loop emits thousands of identical entries per render, and describing
        // each one pays URL-redaction regex work on the hot render path while the description
        // dedupe below keeps the list too small for the cap guard to short-circuit. Distinct
        // raws mapping to one description are still collapsed by the contains() check.
        java.util.Set<String> describedRawMessages = new java.util.HashSet<>();
        try {
            for (LogEntry entry : driver.manage().logs().get(LogType.BROWSER)) {
                if (entry.getLevel().intValue() >= Level.SEVERE.intValue()
                        && !isResourceLoadConsoleEntry(entry.getMessage())
                        && !isPolicyContainmentConsoleEntry(entry.getMessage())) {
                    severeConsoleEntries++;
                    if (severeConsoleDetailsOut != null && severeConsoleDetailsOut.size() < MAX_CONSOLE_DETAILS
                            && describedRawMessages.add(entry.getMessage())) {
                        // De-duplicated, matching the fax-packet aggregation: the COUNT
                        // (severeConsoleEntries, unbounded) still reports every occurrence.
                        String consoleErrorDescription = describeConsoleError(entry.getMessage());
                        if (!severeConsoleDetailsOut.contains(consoleErrorDescription)) {
                            severeConsoleDetailsOut.add(consoleErrorDescription);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // Fail closed: we explicitly enable BROWSER logging in goog:loggingPrefs, so retrieval
            // failing is a WebDriver fault, not a capability gap — and a silently-empty console
            // gate was the only defense against faxing a form whose background never painted.
            // Redacted (no raw WebDriver throwable — it can embed the render URL/token).
            logger.error("Browser console log unavailable for eForm render gate: fdid={} type={} error={}",
                    fdid, e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            throw new PDFGenerationException(
                    "Browser rendering could not verify the page's console error state.");
        }

        if (scan.parseFailures() > 0) {
            // Same philosophy as the drain-fault gate in drainPerformanceLog: an unparseable
            // network event is egress evidence the replay could not account for, and silently
            // skipping it would let a broken render pass every gate on truncated evidence.
            // Counts only — never the raw entries, which can carry URLs.
            logger.error("Browser eForm renderer could not parse {} network event(s): fdid={}",
                    scan.parseFailures(), fdid);
            throw new PDFGenerationException("Browser rendering could not verify the page's network activity.");
        }
        if (mainDocumentStatus == null || mainDocumentStatus != 200) {
            logger.error("Browser eForm renderer rejected main document: fdid={} status={}", fdid, mainDocumentStatus);
            throw new PDFGenerationException("Browser rendering did not receive a successful eForm page response. status=" + mainDocumentStatus);
        }
        // Hard fail-closed on live bidirectional channels regardless of the strict-gate switch: a
        // render surface never legitimately opens a WebSocket or WebTransport, and these bypass the
        // dead HTTP proxy, so any such attempt is treated as an egress channel and aborts the render.
        if (scan.liveChannelAttempts() > 0) {
            logger.error("Browser eForm renderer observed a live egress channel: fdid={} liveChannelAttempts={}",
                    fdid, scan.liveChannelAttempts());
            throw new PDFGenerationException(
                    "Browser rendering attempted a live egress channel. liveChannelAttempts=" + scan.liveChannelAttempts());
        }
        if (scan.nonReadRequests() > 0) {
            logger.error("Browser eForm renderer attempted a non-read request: fdid={} count={}",
                    fdid, scan.nonReadRequests());
            throw new PDFGenerationException(
                    "Browser rendering attempted a prohibited write request.");
        }
        // Content-affecting failures enter the completeness report below. Only sanitized counts
        // cross this boundary; request URLs may contain clinical data.
        if (scan.failedCriticalSubresources() > 0) {
            logger.warn("Browser eForm renderer detected missing content: fdid={} failedContentResources={}",
                    fdid, scan.failedCriticalSubresources());
        }
        if (disallowedRequests > 0 || severeConsoleEntries > 0 || scan.failedSubresources() > 0) {
            // Non-content failures and blocked off-origin requests are advisory unless strict mode
            // is enabled. Content/structural failures also enter the completeness report and require
            // exact user approval. Counts only — URLs and console text can carry clinical data.
            if (strictNetworkGateEnabled()) {
                logger.error("Browser eForm renderer surfaced page errors (strict gate): fdid={} disallowedRequests={} severeConsoleEntries={} failedSubresources={}",
                        fdid, disallowedRequests, severeConsoleEntries, scan.failedSubresources());
                throw new PDFGenerationException("Browser rendering surfaced page errors. disallowedRequests="
                        + disallowedRequests + " consoleErrors=" + severeConsoleEntries
                        + " failedSubresources=" + scan.failedSubresources());
            }
            logger.warn("Browser eForm renderer tolerated non-fatal page issues: fdid={} disallowedRequests={} severeConsoleEntries={} failedSubresources={}"
                    + " (off-origin egress is contained by the dead proxy; set {}=true to fail closed instead)",
                    fdid, disallowedRequests, severeConsoleEntries, scan.failedSubresources(), STRICT_NETWORK_GATE_PROPERTY);
        }
        // severeConsoleEntries enters the completeness report rather than living only in the strict
        // gate. Resource-load and CSP entries are already excluded upstream, so what remains is an
        // uncaught page-script exception -- the only observable for a form whose script aborted midway
        // through injecting clinical content (a score, a dose, a stamp, a letter body). Neither the
        // network scan nor the geometry pass can see that: every subresource returned 200 and the page
        // divs still measure. Strict mode is off by default and cannot realistically be enabled on the
        // legacy corpus, so leaving this advisory meant shipping a silently truncated document.
        return new EFormRenderCompletenessReport(
                scan.failedCriticalSubresources(), 0, severeConsoleEntries, 0, false, false, false, false);
    }

    /**
     * Whether the strict fail-closed network gate is enabled. Defaults to {@code false} (advisory);
     * see {@link #STRICT_NETWORK_GATE_PROPERTY}. Never affects the always-on hard gates
     * (WebSocket/WebTransport, main-document status, unparseable network evidence).
     */
    private static boolean strictNetworkGateEnabled() {
        return Boolean.parseBoolean(
                CarlosProperties.getInstance().getProperty(STRICT_NETWORK_GATE_PROPERTY, "false").trim());
    }

    /**
     * True for Chrome console entries reporting a resource load failure ("Failed to load
     * resource: ..."). Resource failures are gated <em>type-aware</em> by the network scan
     * ({@link NetworkGateScan#failedSubresources()} — render-critical types drive an operator WARN
     * (advisory by default; see the strict-gate switch), speculative loads such as favicons
     * deliberately do not), so counting them in the console
     * gate too made every render fail on the origin-root {@code /favicon.ico} 404 that headless
     * Chrome's own speculative fetch produces. The console gate's remaining job is what the
     * network events cannot see: JavaScript errors on the render surface. Only the message
     * pattern is inspected; console text is still never logged.
     */
    static boolean isResourceLoadConsoleEntry(String message) {
        // Substring match on Chrome's emission (phrase + colon). Residual risk, mirroring the CSP
        // matcher below: a form's own console.error that happens to contain the exact phrase is
        // reclassified as a resource-load entry, suppressing only that form's JS-error signal —
        // resource failures stay gated type-aware by the network scan and egress stays gated by
        // the dead proxy plus event replay, so nothing is bypassed.
        return message != null && message.contains("Failed to load resource:");
    }

    /**
     * True for Chrome console entries reporting that the render surface's own Content-Security-
     * Policy blocked something ("... violates the following Content Security Policy directive
     * ..."). A CSP block is the containment WORKING — the offending content was refused, which is
     * fail-safe by construction — and the normal in-app eForm viewer emits the identical notices
     * for the same stored content while displaying the form fine. Failing the render on them
     * would turn the surface's own defense into a denial of service for legacy forms carrying
     * embedded objects. Actual egress attempts remain gated by the dead proxy and the network
     * event replay regardless of what the console says.
     */
    /**
     * A PHI-safe one-line description of a severe console entry, for the informed-override screen.
     *
     * <p>The clinician needs to know WHAT kind of script failure the form hit before approving a
     * possibly-incomplete render, but a raw console message can carry clinical data (a form is free
     * to {@code console.error} anything). So this extracts ONLY structural, developer-authored
     * facts — the error TYPE and its source line:col — and never the message body. The source URL
     * (which carries the fdid and render token) is stripped first as defence in depth.
     */
    static String describeConsoleError(String message) {
        if (message == null || message.isBlank()) {
            return "Script error";
        }
        String redacted = RenderLogRedaction.redactUrls(message);
        // Parse ONLY the structural header Chrome authors at the START of the entry. The message
        // BODY (which a form controls and may contain PHI) sits after the type and is never read:
        // both patterns are anchored to the start, so a NN:NN or a SomethingError token sitting in
        // the body cannot be lifted into the description — a body-only entry degrades to "Script
        // error". Residual (accepted): a form whose console text, right at the header position,
        // literally begins with "<Word>Error:" can surface that made-up type word — a type token
        // only, never body numbers/names.
        java.util.regex.Matcher header = CONSOLE_HEADER_PATTERN.matcher(redacted);
        if (header.find()) {
            String location = header.group(1) != null
                    ? " (line " + header.group(1) + ":" + header.group(2) + ")" : "";
            return header.group(3) + location;
        }
        // No structural type; still surface Chrome's leading source line:col when it is present.
        java.util.regex.Matcher location = CONSOLE_LEADING_LOCATION_PATTERN.matcher(redacted);
        if (location.find()) {
            return "Script error (line " + location.group(1) + ":" + location.group(2) + ")";
        }
        return "Script error";
    }

    static boolean isPolicyContainmentConsoleEntry(String message) {
        // Anchored to Chrome's full violation phrase, not a bare "Content Security Policy"
        // substring: a form's own console.error would need to reproduce the exact enforcement
        // wording to be reclassified, which only suppresses that form's own gate signal and
        // bypasses nothing (egress remains gated by the dead proxy and network-event replay).
        return message != null && message.contains("violates the following Content Security Policy directive");
    }

    /**
     * Outcome of replaying Chrome's network events against the allowed loopback origin.
     * {@code parseFailures} counts entries the replay could not parse — evidence the egress gate
     * could not account for, which {@link #enforceRenderGates} fails closed on.
     * {@code liveChannelAttempts} counts WebSocket/WebTransport creations, which are an always-on
     * hard fail-closed signal (they bypass the dead HTTP proxy).
     * {@code failedCriticalSubresources} counts failed resources that can affect rendered content
     * and enters the user-approval report.
     * {@code disallowedRequests} (off-origin HTTP, already blocked by the dead proxy) and
     * {@code failedSubresources} contains only known non-content failures and is advisory by
     * default; strict mode also rejects it.
     */
    record NetworkGateScan(int disallowedRequests, Integer mainDocumentStatus, int failedSubresources,
            int parseFailures, int liveChannelAttempts, int failedCriticalSubresources,
            int nonReadRequests) {
    }

    /**
     * CDP resource types whose failure visibly breaks the rendered form (a blank background, a
     * missing signature iframe, an unstyled or script-broken page). Speculative loads Chrome makes
     * on its own — favicons and other {@code Other}-typed requests, prefetches, pings — are
     * deliberately excluded so they cannot fail a render whose visible content is intact.
     */
    private static final Set<String> RENDER_CRITICAL_RESOURCE_TYPES =
            Set.of("Document", "Image", "Script", "Stylesheet", "Font", "Media", "XHR", "Fetch");

    /**
     * Render-critical types that carry data rather than a named file.
     *
     * <p>These are excluded from the duplicate-basename downgrade: several of them legitimately
     * share one path and differ only by query string, which {@link #resourceBasename} discards, so
     * the "this filename also loaded successfully" test would silently excuse a real failure.</p>
     */
    private static final Set<String> DATA_RESOURCE_TYPES = Set.of("XHR", "Fetch");

    /**
     * Cap on the requestId→URL map used to classify {@code Network.loadingFailed} events (which
     * carry no URL of their own). Far above any real form's request count; bounds the memory a
     * pathological page can make the scan hold.
     */
    private static final int MAX_TRACKED_REQUEST_URLS = 4096;

    /**
     * Render-critical types whose failure degrades presentation only — an unstyled heading, a glyph
     * that falls back to a system font. These are reported (and rejected under the strict gate) but
     * never block the document on their own: refusing to hand a clinician their letter because an
     * icon-font stylesheet 404'd withholds a complete clinical record over cosmetics.
     *
     * <p>There is no corresponding constant for the blocking types — the classification is by
     * <em>exclusion</em>: a failed render-critical resource blocks unless it is listed here. So the
     * types that can omit clinical content ({@code Image}, {@code Script}, {@code XHR}/{@code Fetch},
     * {@code Media}, {@code Document}) block by virtue of being absent from this set. Adding a type
     * here therefore silently demotes it from blocking to advisory; that is the whole effect of
     * editing this line, and it is the reason to be conservative about it.</p>
     */
    private static final Set<String> PRESENTATION_RESOURCE_TYPES = Set.of("Stylesheet", "Font");

    /**
     * Replays raw CDP performance-log messages: counts egress attempts to any origin other than
     * the allowed loopback origin, records the status of the first main-frame document response,
     * and counts failed render-critical subresources. Later {@code Document} events belong to
     * iframes (e.g. signature blocks) and are checked as subresources.
     *
     * <p>Egress is observed across all CDP channels a page can open, not just HTTP: WebSocket
     * ({@code Network.webSocketCreated}) and WebTransport ({@code Network.webTransportCreated})
     * arrive on their own events rather than {@code requestWillBeSent}. A render surface never
     * legitimately opens either, so <em>any</em> such event fails the render unconditionally — the
     * URL is not origin-checked, because a same-origin {@code wss:}/{@code https:} WebTransport
     * would otherwise pass the origin allowlist and slip a live bidirectional channel past the
     * gate. This keeps the "reject every non-allowlisted egress attempt" invariant whole even
     * though the dead proxy already blocks the external ones.</p>
     *
     * <p>Subresource failures are observed on both CDP legs: an HTTP error page arrives as
     * {@code Network.responseReceived} with status &ge; 400 (a 404'd form background is otherwise
     * visible only as a SEVERE console entry), while connection-level failures arrive as
     * {@code Network.loadingFailed} ({@code canceled=true} events are benign navigation aborts).
     * Both are restricted to {@link #RENDER_CRITICAL_RESOURCE_TYPES} so speculative browser loads do
     * not affect the document. Every failed render-critical type enters the approval report because
     * missing scripts or data fetches can omit clinical content as readily as missing images.</p>
     *
     * <p><strong>Duplicate references.</strong> A failure is only counted as missing content if no
     * other request in the same render loaded the <em>same filename</em> successfully. Much of the
     * shared-eForm corpus deliberately references each asset twice — once bare (so the form opens
     * off a local disk) and once through {@code ${oscar_image_path}} (so it resolves when served) —
     * and the bare reference is expected to 404 over HTTP. Counting that by-design 404 as missing
     * content blocked forms whose assets were demonstrably present and executing. Matching is on the
     * filename alone and requires an observed 2xx, so a genuinely absent file still blocks: nothing
     * else would have loaded it.</p>
     *
     * <p>The classification is therefore deferred to a second pass. CDP events are replayed from a
     * buffered log in arrival order, so the 404 for a filename can be seen before the 200 for it;
     * deciding inline would make the verdict depend on event ordering.</p>
     */
    static NetworkGateScan scanNetworkEvents(List<String> rawEntries, String allowedOrigin) {
        int disallowedRequests = 0;
        Integer mainDocumentStatus = null;
        String mainDocumentUrl = null;
        int failedSubresources = 0;
        int parseFailures = 0;
        int liveChannelAttempts = 0;
        int failedCriticalSubresources = 0;
        int nonReadRequests = 0;
        java.util.Map<String, String> requestUrlsById = new java.util.HashMap<>();
        java.util.Set<String> loadedResourceNames = new java.util.HashSet<>();
        List<String> criticalFailureNames = new ArrayList<>();
        for (String rawEntry : rawEntries) {
            JsonNode message = parsePerformanceMessage(rawEntry);
            if (message == null) {
                // Count, never log the raw entry (it can carry URLs): a skipped entry is network
                // evidence the gate did not see, and the gate fails closed on a nonzero count.
                parseFailures++;
                continue;
            }
            String method = message.path("method").asText("");
            JsonNode params = message.path("params");
            if ("Network.requestWillBeSent".equals(method)) {
                String url = params.path("request").path("url").asText("");
                String requestMethod = params.path("request").path("method").asText("");
                // Network.loadingFailed carries only a requestId, so remember the URL here to
                // classify the failure later. Bounded so a pathological page cannot grow this
                // without limit.
                if (requestUrlsById.size() < MAX_TRACKED_REQUEST_URLS) {
                    requestUrlsById.putIfAbsent(params.path("requestId").asText(""), url);
                }
                if (originOf(url) != null
                        && !requestMethod.isEmpty()
                        && !"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
                    nonReadRequests++;
                }
                if (isDisallowedRendererRequestUrl(url, allowedOrigin)) {
                    disallowedRequests++;
                }
            } else if ("Network.webSocketCreated".equals(method) || "Network.webTransportCreated".equals(method)) {
                // A render surface never opens a WebSocket or WebTransport. Counted separately from
                // off-origin HTTP because this is an unconditional hard fail-closed signal regardless
                // of URL/origin: a same-origin wss:/https: WebTransport would pass
                // isDisallowedRendererRequestUrl (http(s) to the allowed origin) yet is still a live
                // bidirectional egress channel the dead HTTP proxy does not cover. Off-origin HTTP,
                // by contrast, is already blocked by the dead proxy and is only advisory.
                liveChannelAttempts++;
            } else if ("Network.responseReceived".equals(method)) {
                String resourceType = params.path("type").asText("");
                String responseUrl = params.path("response").path("url").asText("");
                int status = params.path("response").path("status").asInt();
                boolean sameOrigin = allowedOrigin != null && allowedOrigin.equals(originOf(responseUrl));
                if (mainDocumentStatus == null && "Document".equals(resourceType) && sameOrigin) {
                    mainDocumentStatus = status;
                    mainDocumentUrl = responseUrl;
                } else if (RENDER_CRITICAL_RESOURCE_TYPES.contains(resourceType)
                        && (status >= 400 || isRedirect(status))) {
                    // Every failed content-bearing resource can omit clinical content. Two advisory
                    // exceptions: an empty-src placeholder resolving back to the main document URL
                    // (not a distinct form resource), and a presentation-only asset, whose loss
                    // changes how the record looks but not what it says.
                    if (responseUrl.equals(mainDocumentUrl)
                            || PRESENTATION_RESOURCE_TYPES.contains(resourceType)) {
                        failedSubresources++;
                    } else if (DATA_RESOURCE_TYPES.contains(resourceType)) {
                        // Never eligible for the duplicate-basename downgrade below. That rule
                        // exists for assets the corpus deliberately references twice under one
                        // filename; data fetches instead share a filename by construction — every
                        // APCache lookup hits the same servlet path and differs only in its query
                        // string, which resourceBasename discards. Downgrading here meant a 422 for
                        // one clinical field was reclassified as a by-design duplicate whenever any
                        // other lookup in the same render returned 200, and the downgraded bucket is
                        // then dropped from the report entirely — the document shipped complete with
                        // that whole batch of fields blank.
                        failedCriticalSubresources++;
                    } else if (criticalFailureNames.size() < MAX_TRACKED_REQUEST_URLS) {
                        criticalFailureNames.add(resourceBasename(responseUrl));
                    } else {
                        // Past the bound, classify immediately as missing content: dropping the
                        // entry would silently undercount failures.
                        failedCriticalSubresources++;
                    }
                } else if (RENDER_CRITICAL_RESOURCE_TYPES.contains(resourceType)
                        && isLoaded(status)
                        && loadedResourceNames.size() < MAX_TRACKED_REQUEST_URLS) {
                    // A 2xx, or a 304 which means the browser already holds the bytes. A redirect is
                    // neither and must never license downgrading a failure for the same filename.
                    String loadedName = resourceBasename(responseUrl);
                    if (loadedName != null) {
                        loadedResourceNames.add(loadedName);
                    }
                }
            } else if ("Network.loadingFailed".equals(method)
                    && RENDER_CRITICAL_RESOURCE_TYPES.contains(params.path("type").asText(""))
                    && !params.path("canceled").asBoolean(false)) {
                // Same content-vs-presentation split as the HTTP-error leg above, plus the
                // containment split below.
                String failedUrl = requestUrlsById.get(params.path("requestId").asText(""));
                if (PRESENTATION_RESOURCE_TYPES.contains(params.path("type").asText(""))
                        || isContainmentBlockedResource(failedUrl, allowedOrigin)) {
                    failedSubresources++;
                } else if (criticalFailureNames.size() < MAX_TRACKED_REQUEST_URLS) {
                    criticalFailureNames.add(resourceBasename(failedUrl));
                } else {
                    failedCriticalSubresources++;
                }
            }
        }
        // Second pass: a failure whose filename also loaded successfully in this render is a
        // by-design duplicate reference, not missing content. An unknown filename (no URL recorded
        // for the requestId) can never be matched, so it stays blocking — fail closed.
        for (String failedName : criticalFailureNames) {
            if (failedName != null && loadedResourceNames.contains(failedName)) {
                failedSubresources++;
            } else {
                failedCriticalSubresources++;
            }
        }
        return new NetworkGateScan(disallowedRequests, mainDocumentStatus, failedSubresources,
                parseFailures, liveChannelAttempts, failedCriticalSubresources, nonReadRequests);
    }

    /**
     * Whether this render must be withheld from the clinician pending their explicit approval.
     *
     * <p>Extracted from {@code renderWithSlot} for one reason: it is the single decision that gives
     * every approval in this feature its meaning, and inline it was untestable. It sits below
     * {@code createDriver}, which connects to a real chromedriver with no injection point, so
     * nothing above it can be reached from a unit test without a browser on the machine — the
     * clause could have been deleted outright and the whole suite would still have passed.</p>
     *
     * <p>A null approval withholds: the caller supplied none, so there is no consent to rely on.
     * A non-null approval only releases the document when {@link EFormRenderApproval#permits} finds
     * this exact provider, an unexpired ticket, and a digest matching this exact issue set — a
     * render that failed differently than the one the clinician read cannot ride the old ticket.</p>
     */
    static boolean withholdsDocument(EFormRenderCompletenessReport completeness,
            EFormRenderApproval approval, int fdid, String providerId) {
        return completeness.hasBlockingOmissions()
                && (approval == null || !approval.permits(fdid, providerId, completeness));
    }

    /**
     * Whether a response redirected the browser away from the asset it asked for.
     *
     * <p>A redirect means the subresource was not delivered. On the render surface that is how a
     * missing or unauthorized asset would present if it ever reached a filter that redirects rather
     * than refuses — the browser holds no session, so it has nothing to follow a login redirect
     * with. Measured across 394 render windows the surface produced only 200/404/403/400 and no
     * redirect at all, so this is hardening rather than a fix for an observed failure; it exists
     * because a status the gate scores as <em>neither</em> loaded nor failed is a silent hole in a
     * completeness guarantee.</p>
     *
     * <p>304 is deliberately excluded — see {@link #isLoaded(int)}.</p>
     */
    static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /**
     * Whether a response means the browser has the asset's bytes.
     *
     * <p>Includes 304 Not Modified. That is the half of this rule that can actually bite: a 304 is a
     * successful conditional load, and lumping it in with redirects would mark every cached asset as
     * missing content and refuse essentially every document. The render browser is launched fresh
     * per render so its cache is empty and 304 does not arise there today, but it is common on the
     * interactive viewer, and any future change that reuses a browser or enables a render-surface
     * cache would otherwise turn this gate into a blanket refusal.</p>
     */
    static boolean isLoaded(int status) {
        return (status >= 200 && status < 300) || status == 304;
    }

    /**
     * The filename a render request ultimately addresses, used only to recognise that two different
     * URLs name the same asset.
     *
     * <p>The eForm image routes carry the real name in the {@code imagefile} query parameter
     * ({@code …/EFormImageViewForPdfGenerationServlet?imagefile=bg.png}), so that value wins when
     * present; otherwise the last path segment is used ({@code /carlos/bg.png} → {@code bg.png}).
     * This is deliberately a name comparison, not a URL comparison: the whole point is to match a
     * bare reference against the {@code ${oscar_image_path}} reference to the same file.</p>
     *
     * @return the filename, or {@code null} when none can be determined (an unusable value must not
     *         match anything, so the caller keeps treating the failure as missing content)
     */
    static String resourceBasename(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String value = url;
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        int queryStart = value.indexOf('?');
        String name = null;
        if (queryStart >= 0) {
            // "&amp;" appears when the URL was read back out of HTML rather than off the wire.
            String query = value.substring(queryStart + 1).replace("&amp;", "&");
            value = value.substring(0, queryStart);
            for (String parameter : query.split("&")) {
                int separator = parameter.indexOf('=');
                if (separator > 0 && "imagefile".equals(parameter.substring(0, separator))) {
                    try {
                        name = URLDecoder.decode(
                                parameter.substring(separator + 1), StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        // A malformed percent sequence cannot identify an asset; fall through to
                        // the path segment rather than matching on a half-decoded value.
                        name = null;
                    }
                    break;
                }
            }
        }
        if (name == null || name.isEmpty()) {
            name = value;
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name.isEmpty() ? null : name;
    }

    // Package-private for the unit test that pins the fail-closed behavior.
    int drainPerformanceLog(ChromiumDriver driver, List<LogEntry> performanceEntries) throws PDFGenerationException {
        try {
            int before = performanceEntries.size();
            for (LogEntry entry : driver.manage().logs().get(LogType.PERFORMANCE)) {
                performanceEntries.add(entry);
            }
            return performanceEntries.size() - before;
        } catch (RuntimeException e) {
            // Fail closed, mirroring the console gate: the performance log is the ONLY detector
            // for failed render-critical subresources (the console gate delegates resource
            // failures here), so a drain fault silently returning 0 would let a broken render
            // pass every gate on truncated evidence.
            // Redacted (no raw WebDriver throwable — it can embed the render URL/token).
            logger.error("Browser performance log unavailable for eForm render: type={} error={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            throw new PDFGenerationException("Browser rendering could not verify the page's network activity.");
        }
    }

    private static JsonNode parsePerformanceMessage(String rawEntry) {
        try {
            return OBJECT_MAPPER.readTree(rawEntry).path("message");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * True when a failed resource is one the renderer itself refused to fetch — an off-origin or
     * non-web URL that the dead proxy and CSP block by design.
     *
     * <p>Such a failure is evidence that containment worked, not that clinical content is missing:
     * the resource could never have loaded on this surface no matter what. Blocking the PDF for it
     * means a decorative third-party asset can withhold an otherwise complete clinical record — the
     * case that surfaced this was a Creative Commons licence badge on a real corpus form.</p>
     *
     * <p>Deliberately narrow: this covers ONLY off-origin resources. A same-origin image that 404s
     * is still a hard content failure, because that one genuinely should have loaded — a missing
     * scanned background is the catastrophic case this gate exists for. The residual risk is a form
     * hosting a load-bearing image off-origin; that now degrades to a WARN rather than a block, and
     * the request is still visible through the {@code disallowedRequests} count it already
     * produces. {@code eform_pdf_browser_strict_network_gate=true} restores fail-closed.</p>
     */
    static boolean isContainmentBlockedResource(String requestUrl, String allowedOrigin) {
        return requestUrl != null && !requestUrl.isEmpty()
                && isDisallowedRendererRequestUrl(requestUrl, allowedOrigin);
    }

    /**
     * Allowlist classifier for the network gate. Permitted requests are exactly the inert
     * pseudo-schemes ({@code data:}/{@code blob:}/{@code about:}) or http(s) to the validated loopback
     * origin. Everything else — {@code file:}, {@code filesystem:}, {@code chrome:},
     * {@code view-source:}, {@code ftp:}, etc. — is classified as a <em>disallowed request</em>.
     *
     * <p>IMPORTANT: a disallowed request is <em>advisory</em> by default. It increments the
     * {@code disallowedRequests} counter that drives an operator WARN, and only hard-fails the render
     * when the strict network gate ({@code eform_pdf_browser_strict_network_gate}) is enabled. So this
     * is a defence-in-depth <em>signal</em> against local file disclosure into the captured PDF — a
     * {@code file://} subresource is already blocked by Chromium's default cross-scheme policy — and
     * a hard CARLOS-side backstop only under the strict gate, not by default.</p>
     */
    // IMPROPER_UNICODE: equalsIgnoreCase compares the literal request scheme against "http"/"https"
    // to fail-close non-web schemes; a case-insensitive protocol-name compare, not identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal request scheme against http/https for the fail-closed scheme gate; not user identity folding")
    static boolean isDisallowedRendererRequestUrl(String requestUrl, String allowedOrigin) {
        if (requestUrl == null || requestUrl.isEmpty()
                || requestUrl.startsWith("data:") || requestUrl.startsWith("blob:") || requestUrl.startsWith("about:")) {
            return false;
        }
        String scheme = requestUrl.indexOf(':') > 0 ? requestUrl.substring(0, requestUrl.indexOf(':')) : "";
        if (!"http".equalsIgnoreCase(scheme) && !SCHEME_HTTPS.equalsIgnoreCase(scheme)) {
            // Any non-web scheme (file:, filesystem:, chrome:, view-source:, ...) is fail-closed:
            // the eForm render surface only ever needs http(s) to the loopback app plus inert
            // data:/blob:/about: resources.
            return true;
        }
        String origin = originOf(requestUrl);
        return origin == null || !origin.equals(allowedOrigin);
    }

    // IMPROPER_UNICODE: toLowerCase(Locale.ROOT) normalizes the URI scheme and host for an
    // origin-equality compare against the pinned loopback base; canonicalizing protocol/host labels,
    // not folding user identity.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Locale.ROOT lower-casing normalizes the URI scheme/host for loopback origin comparison; not user identity folding")
    static String originOf(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            int port = uri.getPort();
            if (port == -1) {
                port = SCHEME_HTTPS.equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean isExpectedRendererUrl(String actualUrl, String expectedUrl) {
        try {
            URI actual = URI.create(actualUrl);
            URI expected = URI.create(expectedUrl);
            return Objects.equals(originOf(actualUrl), originOf(expectedUrl))
                    && Objects.equals(actual.getPath(), expected.getPath())
                    && Objects.equals(actual.getRawQuery(), expected.getRawQuery());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static int readContainedInteractionCount(Object value) throws PDFGenerationException {
        if (!(value instanceof Number number)) {
            throw new PDFGenerationException(
                    "Browser rendering could not verify contained page interactions.");
        }
        long count = number.longValue();
        if (count < 0 || count > Integer.MAX_VALUE) {
            throw new PDFGenerationException(
                    "Browser rendering reported an invalid contained-interaction count.");
        }
        return (int) count;
    }

    private static void checkDeadline(long deadlineNanos) throws PDFGenerationException {
        // Difference comparison is nanoTime wrap-around safe, unlike a direct `>`.
        if (System.nanoTime() - deadlineNanos > 0) {
            throw new PDFGenerationException("Browser rendering timed out while generating the eForm PDF.");
        }
    }

    /**
     * Outcome of competing for one of the bounded render slots: {@code ACQUIRED} within the wait,
     * {@code TIMED_OUT} with every slot busy for the full wait, or {@code INTERRUPTED} when the
     * waiting thread was interrupted (shutdown) before a slot was taken. Distinguishing the last two
     * lets the caller give correct operator guidance — capacity load-shed (retry) versus an aborted
     * render (no retry) — rather than collapsing both into a single boolean {@code false}.
     */
    enum SlotAcquisition { ACQUIRED, TIMED_OUT, INTERRUPTED }

    static SlotAcquisition acquireRenderSlot(Semaphore slots, Duration wait) {
        try {
            return slots.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS)
                    ? SlotAcquisition.ACQUIRED : SlotAcquisition.TIMED_OUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SlotAcquisition.INTERRUPTED;
        }
    }

    private static HttpServletRequest currentRequestOrNull() {
        try {
            return ServletActionContext.getRequest();
        } catch (RuntimeException e) {
            // Renders triggered outside a Struts request thread fall back to configured base URL.
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // URL construction and validation
    // ---------------------------------------------------------------------------------------------

    static String buildAppPath(int fdid, EFormRenderTokenService.RenderToken renderToken) {
        // A null token here would silently build a tokenless renderer URL that the render-page
        // servlet then rejects (403) — a confusing failure far from the real cause. The render URL
        // is only ever built from a freshly issued grant, so require it up front.
        Objects.requireNonNull(renderToken, "render token must be issued before building the renderer URL");
        return "/EFormViewForPdfGenerationServlet?fdid=" + fdid
                + "&browserRender=true"
                + "&" + EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM + "="
                + URLEncoder.encode(renderToken.queryValue(), StandardCharsets.UTF_8);
    }

    static String buildDefaultBaseUrl(String projectHome) {
        if (projectHome == null || projectHome.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        String normalizedProjectHome = projectHome.startsWith("/") ? projectHome.substring(1) : projectHome;
        normalizedProjectHome = normalizedProjectHome.endsWith("/")
                ? normalizedProjectHome.substring(0, normalizedProjectHome.length() - 1)
                : normalizedProjectHome;
        return "http://127.0.0.1:8080/" + normalizedProjectHome;
    }

    static String buildLocalBaseUrl(String scheme, int port, String contextPath) {
        String normalizedScheme = (scheme == null || scheme.isBlank()) ? "http" : scheme.trim();
        String normalizedContextPath = contextPath == null ? "" : contextPath.trim();
        if (!normalizedContextPath.isEmpty() && !normalizedContextPath.startsWith("/")) {
            normalizedContextPath = Path.of("/", normalizedContextPath).toString();
        }
        if (normalizedContextPath.endsWith("/")) {
            normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
        }
        StringBuilder baseUrl = new StringBuilder(normalizedScheme).append("://127.0.0.1");
        if (port > 0 && !isDefaultPort(normalizedScheme, port)) {
            baseUrl.append(":").append(port);
        }
        baseUrl.append(normalizedContextPath);
        return baseUrl.toString();
    }

    // IMPROPER_UNICODE: equalsIgnoreCase checks the literal configured base-URL scheme is http/https;
    // a case-insensitive protocol-name compare, not user identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal configured base-URL scheme against http/https; not user identity folding")
    static String validateRendererBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Renderer base URL must be non-empty");
        }

        URI uri = URI.create(rawBaseUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !SCHEME_HTTPS.equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Renderer base URL must use http or https");
        }
        if (uri.getHost() == null || !isLocalRendererHost(uri.getHost())) {
            throw new IllegalArgumentException("Renderer base URL host must resolve to loopback");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            // Servlet paths are appended verbatim to this base; a query/fragment would swallow
            // them and fail every render far from the misconfiguration. Reject at the source.
            throw new IllegalArgumentException("Renderer base URL must not contain user-info, query, or fragment components");
        }
        return rawBaseUrl.trim().replaceAll("/$", "");
    }

    /**
     * Startup-time format validation of {@code eform_pdf_browser_base_url}. A no-op when the
     * property is unset (request-derived URLs cannot be validated before Tomcat serves). Lets the
     * advisory startup check warn immediately about a malformed configured base URL.
     *
     * @throws PDFGenerationException when the configured value fails
     *         {@link #validateRendererBaseUrl}; the message names the property
     */
    void verifyConfiguredBaseUrl() throws PDFGenerationException {
        String configured = CarlosProperties.getInstance().getProperty(BASE_URL_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        try {
            validateRendererBaseUrl(configured);
        } catch (IllegalArgumentException e) {
            throw new PDFGenerationException("The configured " + BASE_URL_PROPERTY + " is invalid: " + e.getMessage());
        }
    }

    static String validateRendererAppPath(String appPath) {
        if (appPath == null || appPath.isBlank()) {
            throw new IllegalArgumentException("Application path must be non-empty");
        }
        String normalizedPath = appPath.trim();
        if (!normalizedPath.startsWith("/") || normalizedPath.startsWith("//")) {
            throw new IllegalArgumentException("Application path must be root-relative");
        }
        return normalizedPath;
    }

    static boolean isLocalRendererHost(String rawHost) {
        String host = rawHost == null ? "" : rawHost.trim().toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty()) {
            return false;
        }
        return Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(host);
    }

    private String resolveBaseUrl(String projectHome, HttpServletRequest request) {
        String configuredBaseUrl = CarlosProperties.getInstance().getProperty(BASE_URL_PROPERTY);
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.trim().replaceAll("/$", "");
        }
        if (request != null) {
            return buildLocalBaseUrl(deriveLoopbackScheme(request), request.getLocalPort(), request.getContextPath());
        }
        return buildDefaultBaseUrl(projectHome);
    }

    /**
     * Scheme for the renderer's loopback hop. A proxy-rewritten request (RemoteIpValve /
     * X-Forwarded-Proto) reports https while the local connector speaks plaintext; that state is
     * detectable as scheme=https with serverPort != localPort (Tomcat-terminated TLS keeps them
     * equal). Without the downgrade the derived base is https://127.0.0.1:<httpPort>, which fails
     * every render. eform_pdf_browser_base_url overrides this derivation entirely.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of the literal request scheme against https to detect a proxied-TLS loopback hop; not a security or authorization decision on user identity.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal request scheme against https to detect a proxied-TLS loopback hop; not a security or authorization decision on user identity")
    static String deriveLoopbackScheme(HttpServletRequest request) {
        String scheme = request.getScheme();
        if (SCHEME_HTTPS.equalsIgnoreCase(scheme) && request.getServerPort() != request.getLocalPort()) {
            logger.info("Renderer base URL: proxied TLS detected (serverPort={} localPort={}); using http for the loopback hop. Set {} to override.",
                    request.getServerPort(), request.getLocalPort(), BASE_URL_PROPERTY);
            return "http";
        }
        return scheme;
    }

    private String resolveChromiumPath() {
        String configuredChromiumPath = CarlosProperties.getInstance().getProperty(CHROME_PATH_PROPERTY);
        if (configuredChromiumPath != null && !configuredChromiumPath.isBlank()) {
            return configuredChromiumPath.trim();
        }
        return null;
    }

    /**
     * The chromedriver endpoint this JVM connects to. Uses the 2-arg property lookup deliberately:
     * the 1-arg form logs a WARN on every miss, and this property is expected to be absent on
     * deployments that have not installed the render browser package.
     */
    private String resolveBrowserServiceUrl() {
        return CarlosProperties.getInstance()
                .getProperty(SERVICE_URL_PROPERTY, DEFAULT_SERVICE_URL).trim();
    }

    /**
     * Validates the chromedriver endpoint, reusing the same loopback allow-list that gates the render
     * base URL. Returns a {@link URI} rather than a String so no caller can reassemble it by
     * concatenation and drift.
     *
     * <p>The port must be explicit: chromedriver has no default port, and silently falling back to 80
     * would point the renderer at Tomcat or nginx. A path component IS permitted -- that is the
     * chromedriver {@code --url-base} prefix, which this deployment uses as a capability token.
     */
    // IMPROPER_UNICODE: equalsIgnoreCase on the literal "http" is an intended case-insensitive
    // scheme comparison per RFC 3986; no locale-sensitive or trust-path case folding is involved.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "intended case-insensitive URI scheme comparison against a literal")
    static URI validateBrowserServiceUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Render browser service URL must not be blank");
        }
        URI uri = URI.create(rawUrl.trim());
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            // Not a style preference: chromedriver serves plaintext only, so https can never work.
            throw new IllegalArgumentException("Render browser service URL must use http");
        }
        if (uri.getHost() == null || !isLocalRendererHost(uri.getHost())) {
            throw new IllegalArgumentException("Render browser service URL host must resolve to loopback");
        }
        if (uri.getPort() < 1) {
            throw new IllegalArgumentException("Render browser service URL must name an explicit port");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Render browser service URL must not carry user-info, a query or a fragment");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.endsWith("/")) {
            throw new IllegalArgumentException("Render browser service URL must not end in '/'");
        }
        // Linear-time equivalent of the old (/[A-Za-z0-9._~-]+)+ grammar, restructured because
        // SpotBugs' REDOS detector matches the nested-quantifier SHAPE (it flagged the greedy
        // and the possessive spelling alike, though possessive cannot backtrack). One-or-more
        // segments of allowed characters joined by single slashes == every character in class,
        // leading slash, no empty segment, no trailing slash. Each check is a single pass.
        boolean usablePath = path.isEmpty()
                || (path.startsWith("/")
                        && !path.endsWith("/")
                        && !path.contains("//")
                        && path.matches("[/A-Za-z0-9._~-]+"));
        if (!usablePath) {
            throw new IllegalArgumentException("Render browser service URL path is not a usable url-base prefix");
        }
        return uri;
    }

    /**
     * Startup-time format check for {@link #SERVICE_URL_PROPERTY}, the sibling of
     * {@link #verifyConfiguredBaseUrl()}. Kept separate on purpose: the two properties have different
     * operator remediations, and one message covering both is one nobody can act on.
     */
    void verifyConfiguredServiceUrl() throws PDFGenerationException {
        // Migration tripwire: the spawn-a-driver property is retired and silently ignoring it
        // would strand a deployment configured per the old docs — the renderer would quietly try
        // the default service URL instead of the operator's chromedriver, and every render would
        // fail with a message that never mentions the actual misconfiguration. One WARN at
        // startup names the retirement and the replacement.
        String retiredPath = CarlosProperties.getInstance()
                .getProperty("eform_pdf_browser_chromedriver_path", "");
        if (!retiredPath.isBlank()) {
            logger.warn("eform_pdf_browser_chromedriver_path is RETIRED and ignored: CARLOS no "
                    + "longer spawns chromedriver. Run chromedriver as a service and set {} "
                    + "instead (the .deb's carlos-emr-eform-renderer package does both).",
                    SERVICE_URL_PROPERTY);
        }
        try {
            validateBrowserServiceUrl(resolveBrowserServiceUrl());
        } catch (IllegalArgumentException e) {
            throw new PDFGenerationException("The configured " + SERVICE_URL_PROPERTY + " is invalid: "
                    + RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Managed temp locations and PDF assembly
    // ---------------------------------------------------------------------------------------------

    private Path resolveRendererTempRoot() throws PDFGenerationException {
        try {
            return resolveRendererTempRoot(
                    System.getProperty(CATALINA_BASE_PROPERTY),
                    System.getProperty("java.io.tmpdir"));
        } catch (RuntimeException e) {
            throw new PDFGenerationException("Unable to resolve a managed temporary directory for eForm browser PDF generation.", e);
        }
    }

    /**
     * Resolves the managed temp root for renderer artifacts.
     *
     * <p>The rendered PDF is later reused by the fax flow as {@code faxFilePath}, and
     * {@code FaxManagerImpl.validateFilePath}/{@code resolveAndValidateFilePath} only accept files
     * under {@code DOCUMENT_DIR} or a CARLOS application-owned temp subtree
     * ({@link PathValidationUtils#isInApplicationTempDirectory(File)}) — not the entire shared temp
     * root. The roots returned here ({@code <catalina.base>/work/carlos/eform-browser-pdf-temp} and
     * {@code <java.io.tmpdir>/carlos-eform-browser-pdf-temp}) are CARLOS-owned and satisfy that
     * check; do not move renderer output to a non-{@code carlos}-owned temp location (or to
     * {@code BASE_DOCUMENT_DIR}, removed for exactly this reason) that fax path validation rejects.</p>
     */
    static Path resolveRendererTempRoot(String catalinaBase, String javaTmpDir) {
        if (catalinaBase != null && !catalinaBase.isBlank()) {
            File catalinaDir = PathValidationUtils.resolveConfiguredDirectory(catalinaBase.trim(), CATALINA_BASE_PROPERTY);
            return Path.of(catalinaDir.getPath(), "work", "carlos", "eform-browser-pdf-temp");
        }
        File tempDir = PathValidationUtils.validateConfiguredDirectory(javaTmpDir, "java.io.tmpdir");
        return Path.of(tempDir.getPath(), "carlos-eform-browser-pdf-temp");
    }

    /**
     * True when {@code path} starts with the {@code %PDF} magic bytes. Read failures (including a
     * missing file) return false — at the success gate, "cannot prove it is a PDF" and "is not a
     * PDF" both mean the render failed.
     */
    static boolean hasPdfMagicBytes(Path path) {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(header, 0, 4) == 4
                    && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
        } catch (IOException e) {
            // Fail closed, but say why: a permissions/mount fault on the renderer temp root would
            // otherwise be indistinguishable from a garbage capture and send the operator chasing
            // the Chromium pipeline. Redacted message per this file's convention.
            logger.warn("Could not read rendered PDF header at the success gate: type={} error={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            return false;
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: the renderer output PDF is created only under a validated managed temp root, with caller-controlled filenames disallowed.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Renderer temp files are created only beneath resolveRendererTempRoot(), which validates configured roots before creating a managed private temp file.")
    static Path createSecureTempFile(Path tempRoot, String prefix, String suffix) throws IOException {
        Path managedRoot = Files.createDirectories(tempRoot);
        // Reject a pre-seeded symlink at the managed root: a local attacker who wins the race to
        // create the predictable renderer temp root as a symlink could otherwise redirect the
        // rendered PDF outside the CARLOS-owned tree. The file itself is created atomically with a
        // random name and 0600 via createTempFile below.
        if (Files.isSymbolicLink(managedRoot)) {
            throw new IOException("Renderer temp root must be a real directory, not a symbolic link: " + managedRoot);
        }
        FileAttribute<?>[] secureAttributes = securePosixAttributes();
        try {
            return Files.createTempFile(managedRoot, prefix, suffix, secureAttributes);
        } catch (UnsupportedOperationException e) {
            throw new IOException("Renderer temp path requires POSIX filesystem permissions under " + managedRoot, e);
        }
    }

    private static FileAttribute<?>[] securePosixAttributes() {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            return new FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(permissions) };
        } catch (UnsupportedOperationException e) {
            return new FileAttribute<?>[0];
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison only classifies literal protocol names for default-port handling, not any auth decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Case-insensitive comparison here only classifies literal protocol names for port defaults and is not used for authentication or authorization.")
    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || (SCHEME_HTTPS.equalsIgnoreCase(scheme) && port == 443);
    }

    /** @return {@code true} when the path no longer exists after the attempt (deleted or already gone). */
    private static boolean deleteQuietly(Path outputPath) {
        if (outputPath == null) {
            return false;
        }
        try {
            Files.deleteIfExists(outputPath);
        } catch (IOException e) {
            // WARN, not DEBUG: an undeletable rendered PDF is PHI accumulating on disk, and at
            // default log levels an operator must learn the sweep is papering over a real
            // filesystem/permission problem. The path is a managed temp path, not PHI.
            logger.warn("Unable to delete temporary browser-rendered PDF {}", outputPath, e);
        }
        return !Files.exists(outputPath);
    }

    /** @return {@code true} only when every entry was removed and the directory itself no longer exists. */
    private static boolean deleteRecursivelyQuietly(Path directory) {
        if (directory == null) {
            return false;
        }
        // Per-entry failures stay at DEBUG so one undeletable tree cannot spam WARN per file;
        // the aggregate below surfaces the problem once at WARN.
        int failedEntries = 0;
        try (var stream = Files.walk(directory)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    failedEntries++;
                    logger.debug("Unable to delete renderer capture entry {}", entry, e);
                }
            }
        } catch (IOException e) {
            logger.warn("Unable to delete temporary browser-rendered capture directory {}", directory, e);
            return false;
        }
        if (failedEntries > 0) {
            logger.warn("Unable to delete {} entries under renderer capture directory {}; PHI capture images may be accumulating",
                    failedEntries, directory);
            return false;
        }
        return !Files.exists(directory);
    }

    /**
     * Age after which an orphaned same-prefixed renderer <em>directory</em> under the shared managed
     * root is swept. The native print path creates no per-render directory; this setting reclaims
     * detached legacy raster-capture directories after a short safety window.
     */
    private static final Duration STALE_RENDERER_DIR_TTL = Duration.ofHours(1);

    /**
     * Age after which an orphaned renderer output {@code .pdf} is swept. The output is RETURNED to the
     * caller, which owns cleanup, so this window is deliberately far larger than any possible request
     * lifetime (a single render is capped at {@link #RENDER_TIMEOUT} = 90s and callers consume the PDF
     * synchronously within the same request). At 24h the age sweep cannot intersect a still-in-use
     * output — even a long multi-attachment workflow — while still bounding disk use if a
     * caller dies mid-consumption and never cleans up.
     */
    private static final Duration STALE_RENDERER_OUTPUT_TTL = Duration.ofHours(24);

    /**
     * Best-effort sweep of renderer artifacts under the shared managed root: the {@code .pdf} output
     * files this native print path creates when a caller does not consume a returned output, plus any
     * same-prefixed legacy raster <em>capture directory</em>. Directories
     * are reclaimed after {@link #STALE_RENDERER_DIR_TTL} and caller-owned output PDFs only after the
     * much longer {@link #STALE_RENDERER_OUTPUT_TTL}, so an in-flight render — and a returned,
     * not-yet-consumed output — is never touched. Never throws — a sweep failure must not fail the
     * render.
     */
    static void sweepStaleRendererRoots(Path managedRoot) {
        if (managedRoot == null || !Files.isDirectory(managedRoot)) {
            return;
        }
        long now = System.currentTimeMillis();
        long dirCutoffMillis = now - STALE_RENDERER_DIR_TTL.toMillis();
        long outputCutoffMillis = now - STALE_RENDERER_OUTPUT_TTL.toMillis();
        // The render's output .pdf sits directly under the managed root with the same prefix and is
        // RETURNED by renderSavedEformPdf for caller-owned cleanup, so it is age-gated on the long
        // output window that no live request can reach (fresh outputs are never swept, an
        // in-use output cannot be reached, and growth stays bounded). Capture dirs are always caller-detached, so a short
        // window reclaims them. Catch unchecked failures too (e.g. DirectoryIteratorException) so a
        // traversal/permission error can never turn this best-effort cleanup into a render prerequisite.
        int reclaimed = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(managedRoot, RENDER_ARTIFACT_PREFIX + "*")) {
            for (Path entry : entries) {
                boolean isDirectory;
                long modifiedMillis;
                try {
                    modifiedMillis = Files.getLastModifiedTime(entry).toMillis();
                    isDirectory = Files.isDirectory(entry);
                } catch (IOException e) {
                    continue; // can't stat it; leave it for a later sweep
                }
                if (isDirectory) {
                    // Count only a confirmed reclamation: deleteRecursivelyQuietly returns false when
                    // any entry survived, so the metric can't claim cleanup that didn't happen.
                    if (modifiedMillis < dirCutoffMillis && deleteRecursivelyQuietly(entry)) {
                        reclaimed++;
                    }
                } else {
                    Path entryFileName = entry.getFileName();
                    if (entryFileName != null
                        && entryFileName.toString().endsWith(".pdf")
                        && modifiedMillis < outputCutoffMillis
                        && deleteQuietly(entry)) { // orphaned output reclaimed only long past any request lifetime
                        reclaimed++;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // WARN: a sweep that cannot run leaves orphaned PHI captures on disk indefinitely.
            logger.warn("Unable to sweep stale renderer temp roots under {}", managedRoot, e);
        }
        if (reclaimed > 0) {
            logger.debug("Swept {} stale renderer artifact(s) under the managed temp root", reclaimed);
        }
    }

}
