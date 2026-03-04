# Plan: Migrate from Ultrabuk/wkhtmltopdf to Playwright Java (Headless Chromium)

## Executive Summary

Replace the deprecated `ultrabuk-htmltopdf-java` (JNA wrapper around wkhtmltopdf) with **Playwright Java** (`com.microsoft.playwright:playwright`) for HTML-to-PDF conversion. Playwright uses headless Chromium's native print engine, providing full modern HTML5/CSS3/JavaScript support, active maintenance by Microsoft, and a pure-Java API with no JNI/JNA native library management.

## Current State Analysis

### What We Have Today

The codebase has **three layers** of PDF generation:

| Layer | Library | Purpose | Files |
|-------|---------|---------|-------|
| **HTML→PDF (Primary)** | `ultrabuk-htmltopdf-java:1.0.11` via `io.woo.htmltopdf` | Converts HTML to PDF using embedded wkhtmltopdf native library (JNA) | `InternalEDocConverter.java` |
| **HTML→PDF (Fallback)** | `flying-saucer-pdf:9.13.3` + OpenPDF/lowagie | Strict XHTML→PDF when primary fails (no JS, no modern CSS) | `ConvertToEdoc.fallbackRender()`, `ReplacedElementFactoryImpl.java` |
| **Programmatic PDF** | `itextpdf:5.5.13.5` + `xmlworker` + PDFBox | Direct PDF construction (tables, fonts, forms, labs, fax covers, etc.) | ~47 files |
| **Legacy (Deprecated)** | `Doc2PDF.java` using iText XMLWorker | Old HTML→PDF via iText; marked `@Deprecated` | `Doc2PDF.java` + 3 Messenger actions |

### The Ultrabuk Problem

The `ultrabuk-htmltopdf-java` library is a fork of `io.woo/htmltopdf`, which is a JNA wrapper around **wkhtmltopdf** — a tool that is now **end-of-life and abandoned**:

- **wkhtmltopdf is archived/deprecated** — no security patches, no updates
- Uses an **ancient Qt WebKit** rendering engine (circa 2012 era)
- **No modern CSS support**: No flexbox, no grid, no CSS columns
- **No modern JavaScript**: No ES6+, limited DOM APIs
- **JavaScript is currently disabled** (`web.enableJavascript=false`) in `InternalEDocConverter.java`
- **Single-threaded**: wkhtmltopdf uses Qt internally and cannot do concurrent conversions per process
- **Native library dependency**: Bundles `libwkhtmltox.so` which must match the OS — fragile across environments
- **Security risk**: Unmaintained native binary with known vulnerabilities processing untrusted HTML

### What Calls the Ultrabuk Path

The entry point is `ConvertToEdoc.renderPDF()` → `InternalEDocConverter.convert()`:

```
ConvertToEdoc.renderPDF()
  ├── PRIMARY: InternalEDocConverter (ultrabuk/wkhtmltopdf)
  └── FALLBACK: Flying Saucer (ITextRenderer)
```

**Callers of ConvertToEdoc:**
- `EformDataManagerImpl` — eForm to eDoc conversion
- `FormsManagerImpl` — Clinical form to eDoc conversion
- `FaxDocumentManagerImpl` — Fax document preparation
- `EmailManager` — Email body to PDF attachment
- `HRMPDFCreator` — Hospital Report Manager
- `EForm.java` / `EFormBase.java` — eForm data model convenience methods

**Separate legacy path (not via ConvertToEdoc):**
- `Doc2PDF.java` (deprecated) — used by `MsgDoc2PDF2Action`, `MsgAttachPDF2Action`, `MsgViewPDF2Action`
- Uses iText `XMLWorkerHelper` directly (poor HTML support)

**JSP client-side caller:**
- `efmpatientformlistsingle.jsp` — POST to `/html2pdf` endpoint (servlet mapping unclear, may be dead code)

### What Is NOT Affected

The following use iText/PDFBox for **programmatic PDF construction** (not HTML→PDF conversion) and are **out of scope** for this migration:

- `PdfCoverPageCreator` — Fax cover pages (iText direct API)
- `LabPDFCreator` — Lab result PDFs (iText + JasperReports)
- `PdfRecordPrinter` — Patient record printing (iText + JasperReports)
- `ConsultationPDFCreator` — Consultation request PDFs (iText)
- `EFormPDFServlet` — Template overlay PDFs (iText PdfReader)
- `ConcatPDF` — PDF merging (PDFBox)
- `PDFEncryptionUtil` — PDF encryption (PDFBox)
- All `FrmPDFServlet`, `FrmCustomedPDFServlet` — Form PDF servlets (iText)
- `CaseManagementPrintPdf`, `PreventionPrintPdf`, etc. — Clinical printing (iText)

---

## Why Playwright Java (Recommended)

### Comparison of Options

| Feature | wkhtmltopdf (current) | OpenHTMLtoPDF | Flying Saucer (current fallback) | Playwright Java |
|---------|----------------------|---------------|--------------------------------|-----------------|
| **Status** | Abandoned/EOL | Community fork, active | Maintained but limited | Active (Microsoft) |
| **Rendering engine** | Ancient Qt WebKit | Custom Java engine | Custom Java engine | **Chromium (latest)** |
| **HTML5 support** | Partial | Partial | Minimal | **Full** |
| **CSS3 (flex, grid)** | No | No | No | **Full** |
| **JavaScript execution** | Old engine | None | None | **Full V8 engine** |
| **Modern CSS print** | Partial | Partial | Partial | **Full @media print, @page** |
| **Pure Java** | No (native .so) | Yes | Yes | Java API, bundles Chromium |
| **Concurrent conversions** | No (single-threaded) | Yes | Yes | **Yes (multiple browser contexts)** |
| **Security** | Risk (unmaintained) | Good | Good | **Actively patched Chromium** |
| **License** | LGPL | LGPL | LGPL | **Apache 2.0** |
| **Maven artifact** | JitPack only | Maven Central | Maven Central | **Maven Central** |
| **eForm rendering fidelity** | Good (for old CSS) | Poor (no JS, limited CSS) | Poor (strict XHTML only) | **Excellent** |

### Why NOT the Other Options

- **OpenHTMLtoPDF**: Pure Java but **no JavaScript, no flexbox, no grid**. The user specifically needs JS support for future eForm rendering. Would be a lateral move from Flying Saucer with marginal improvement.
- **Flying Saucer** (already the fallback): Requires strict XHTML. No JS. Limited CSS. Already fails on many eForms, which is why wkhtmltopdf was made primary.
- **Gotenberg**: Docker-based API service — adds infrastructure complexity, network dependency, and a separate container to manage. Overkill for embedded EMR use.
- **iText pdfHTML**: Commercial license (AGPL) — incompatible with GPL2 project. Expensive per-deployment licensing.

### Why Playwright IS the Right Choice

1. **JavaScript support** — eForms can contain JavaScript for calculations, dynamic content, conditional rendering. Playwright executes all JS before capturing the PDF.
2. **Full modern CSS** — flexbox, grid, `@media print`, `@page` rules, web fonts — all work natively.
3. **Java API** — `com.microsoft.playwright:playwright` on Maven Central. Pure Java API. Chromium is auto-downloaded on first use (or can be bundled in the Docker image).
4. **Headless by default** — designed for server-side use with no GUI needed.
5. **Actively maintained** — Microsoft-backed, monthly releases, security patches.
6. **Already in the project** — CARLOS already uses Playwright MCP tools for UI testing; the infrastructure familiarity exists.
7. **Concurrent** — multiple browser contexts can render PDFs simultaneously (unlike wkhtmltopdf's single-thread limitation).
8. **Secure** — Chromium's sandbox isolates rendering. No native `.so` management. Regular security updates.

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Chromium binary size (~300MB) | Already in devcontainer Docker image; bundle at build time |
| Startup latency (browser launch) | Use a **shared browser instance** (singleton) with per-conversion browser contexts |
| Resource usage (memory) | Browser contexts are lightweight; pool and reuse; set memory limits |
| Rendering differences from wkhtmltopdf | Test with existing eForms; CSS `@media print` rules will actually work better |
| Playwright version upgrades changing PDF output | Pin Playwright + Chromium version; test with reference PDFs |

---

## Migration Plan

### Phase 1: Add Playwright Dependency and Create New Converter

**Files to create:**
- `src/main/java/io/github/carlos_emr/carlos/documentManager/PlaywrightPdfConverter.java`

**Files to modify:**
- `pom.xml` — Add Playwright dependency, remove ultrabuk dependency

**Implementation:**

```java
public class PlaywrightPdfConverter implements EDocConverterInterface {

    private static final Logger logger = MiscUtils.getLogger();

    // Singleton browser instance — launched once, reused across conversions
    private static volatile Playwright playwright;
    private static volatile Browser browser;
    private static final Object lock = new Object();

    /**
     * Converts HTML to PDF using headless Chromium via Playwright.
     * Uses Chromium's native print-to-PDF engine, supporting full HTML5,
     * CSS3 (including @media print, flexbox, grid), and JavaScript execution.
     */
    @Override
    public void convert(String html, OutputStream os) throws Exception {
        Browser browser = getBrowser();
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent(html, new Page.SetContentOptions()
                .setWaitUntil(WaitUntilState.NETWORKIDLE));

            byte[] pdfBytes = page.pdf(new Page.PdfOptions()
                .setFormat("Letter")
                .setMargin(new Margin()
                    .setTop("10mm")
                    .setLeft("8mm")
                    .setRight("8mm"))
                .setPrintBackground(true));

            os.write(pdfBytes);
        }
    }

    private Browser getBrowser() {
        if (browser == null) {
            synchronized (lock) {
                if (browser == null) {
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of(
                                "--no-sandbox",
                                "--disable-gpu",
                                "--disable-dev-shm-usage")));
                }
            }
        }
        return browser;
    }

    /** Shutdown hook — call on application shutdown */
    public static void shutdown() {
        synchronized (lock) {
            if (browser != null) { browser.close(); browser = null; }
            if (playwright != null) { playwright.close(); playwright = null; }
        }
    }
}
```

### Phase 2: Wire Into ConvertToEdoc

**File to modify:** `ConvertToEdoc.java`

Change `renderPDF()` to use the new converter as primary:

```java
private static void renderPDF(final String document, ByteArrayOutputStream os)
        throws DocumentException, IOException {
    // NEW: Playwright (headless Chromium) — full HTML5/CSS3/JS support
    EDocConverterInterface converter = new PlaywrightPdfConverter();

    try {
        converter.convert(document, os);
    } catch (Exception e) {
        logger.warn("Playwright PDF conversion failed, attempting Flying Saucer fallback: "
            + e.getMessage());
        try {
            os.reset();
            fallbackRender(document, os);
        } catch (Exception fallbackError) {
            // ... existing error handling ...
        }
    }
}
```

The existing fallback to Flying Saucer is preserved as a safety net.

### Phase 3: Update JavaScript Handling

Currently, `InternalEDocConverter` has `web.enableJavascript=false`. The `tidyDocument()` method in `ConvertToEdoc` also strips `<script>` tags when preparing for Flying Saucer fallback.

For the Playwright path, we need a **separate document preparation** that preserves JavaScript:

**Approach:**
- The `tidyDocument()` method already handles resource path translation and CSS injection
- For Playwright, we skip script removal since Chromium can safely execute JS in a sandboxed context
- The Flying Saucer fallback path already has its own `prepareDocumentForFlyingSaucer()` which strips scripts

**Security consideration:** Playwright's Chromium runs with `--no-sandbox` in the container but processes HTML in an isolated browser context. External network access can be blocked via `context.route()` to prevent data exfiltration from eForms containing malicious JS.

### Phase 4: Handle Resource Path Resolution

The existing `ConvertToEdoc.tidyDocument()` translates relative paths to absolute filesystem paths. Chromium needs file:// URIs or base64-encoded inline resources:

**Options (pick one during implementation):**
1. **file:// URIs** — Convert absolute paths to `file:///path/to/resource` format (simplest)
2. **Base64 inline** — Inline images as `data:image/png;base64,...` (more portable, no file access needed)
3. **Local HTTP server** — Serve resources via a temporary local server (most compatible but complex)

**Recommended:** Option 1 (file:// URIs) with Option 2 as enhancement for images. The existing path translation already produces absolute filesystem paths — prepending `file://` is trivial.

### Phase 5: Migrate Doc2PDF Callers

The deprecated `Doc2PDF.java` is used by three Messenger actions:
- `MsgDoc2PDF2Action.java`
- `MsgAttachPDF2Action.java`
- `MsgViewPDF2Action.java`

These should be migrated to use `ConvertToEdoc` (which will now use Playwright), then `Doc2PDF.java` can be deleted entirely.

### Phase 6: Remove Ultrabuk Dependency

**Files to modify:**
- `pom.xml` — Remove `com.github.openosp:ultrabuk-htmltopdf-java:1.0.11`
- `dependencies-lock.json` — Regenerate
- `dependencies-lock-modern.json` — Regenerate

**Files to delete:**
- `InternalEDocConverter.java` — No longer needed (replaced by `PlaywrightPdfConverter`)
- `ExternalEDocConverter.java` — No longer needed (wkhtmltopdf CLI fallback)
- `Doc2PDF.java` — Deprecated, callers migrated in Phase 5

**Configuration to clean up:**
- `carlos.properties` — Remove `WKHTMLTOPDF_COMMAND` and `WKHTMLTOPDF_ARGS` properties
- `.devcontainer` config — Remove wkhtmltopdf installation if present

### Phase 7: Docker/DevContainer Updates

Ensure Chromium is available in the devcontainer:

**Option A:** Let Playwright auto-download on first use (adds ~300MB on first run)
**Option B (Recommended):** Pre-install in Dockerfile:
```dockerfile
# Install Playwright browsers at build time
RUN mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI \
    -D exec.args="install chromium --with-deps"
```

### Phase 8: Add Shutdown Hook

Register browser cleanup on application shutdown:

```java
// In Spring application context or ServletContextListener
@PreDestroy
public void cleanup() {
    PlaywrightPdfConverter.shutdown();
}
```

### Phase 9: Testing

1. **Unit test** `PlaywrightPdfConverter` with simple HTML, CSS, and JavaScript
2. **Integration test** with real eForm HTML from the database
3. **Visual regression** — Generate PDFs from a sample set of eForms with both old and new converters; compare
4. **Concurrent conversion test** — Verify multiple simultaneous PDF generations work
5. **Memory/resource test** — Verify browser contexts are properly closed and don't leak

---

## Dependency Changes Summary

### Add:
```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.50.0</version> <!-- Pin to specific version -->
</dependency>
```

### Remove:
```xml
<dependency>
    <groupId>com.github.openosp</groupId>
    <artifactId>ultrabuk-htmltopdf-java</artifactId>
    <version>1.0.11</version>
</dependency>
```

### Keep (unchanged):
- `org.xhtmlrenderer:flying-saucer-pdf:9.13.3` — Remains as fallback
- `com.itextpdf:itextpdf:5.5.13.5` — Used by 47+ files for programmatic PDF (out of scope)
- `org.apache.pdfbox:pdfbox:2.0.35` — PDF manipulation (out of scope)

---

## Migration Order and Effort Estimate

| Phase | Description | Risk | Scope |
|-------|-------------|------|-------|
| 1 | Create `PlaywrightPdfConverter` | Low | 1 new file |
| 2 | Wire into `ConvertToEdoc.renderPDF()` | Low | 1 line change |
| 3 | JS-aware document preparation | Medium | Modify `tidyDocument()` flow |
| 4 | Resource path resolution (file:// URIs) | Medium | Modify path translation |
| 5 | Migrate `Doc2PDF` callers | Low | 3 action files |
| 6 | Remove ultrabuk + dead code | Low | pom.xml + delete 3 files |
| 7 | Docker/devcontainer updates | Low | Dockerfile changes |
| 8 | Shutdown hook | Low | 1 Spring config change |
| 9 | Testing | Medium | New test files |

**Total scope:** ~5-8 files modified, ~3 new files, ~3 files deleted.

The `EDocConverterInterface` abstraction already in place makes this a clean swap.

---

## Decision Points for Discussion

1. **JavaScript execution policy**: Should we enable JS execution for all eForms, or have a per-eForm opt-in flag? (Recommendation: enable by default — it's the whole point of the migration, and Chromium sandboxing makes it safe)

2. **Network isolation**: Should we block all network requests from the Chromium context? (Recommendation: yes — use `context.route("**", route -> route.abort())` for external URLs, allow only `file://` and `data:` URIs)

3. **Timeout policy**: How long to wait for JS execution before generating PDF? (Recommendation: 10 second timeout with `WaitUntilState.NETWORKIDLE`)

4. **Flying Saucer retention**: Keep Flying Saucer as fallback indefinitely, or remove it after validation? (Recommendation: keep for 1 release cycle, then remove)

5. **Doc2PDF removal**: Delete immediately or deprecate-then-remove? (Recommendation: delete — it's already `@Deprecated` and the migration gives us a clean replacement)
