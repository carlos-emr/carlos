# HTML-to-PDF on Flying Saucer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every non-scripted HTML-to-PDF render in CARLOS through one Flying Saucer 10.7.0 pipeline that matches production wkhtmltopdf output, degrades visibly instead of failing, never exhausts the heap, and then remove wkhtmltopdf.

**Architecture:** A new `documentManager.htmlpdf` module: a public `HtmlPdfRenderService` entry point with typed request/result records, five package-private Spring beans (composer, image service, layout service, validator, font files), static markup rules in `support`, and Flying Saucer/OpenPDF extensions in `engine`. `ConvertToEdoc` and `Doc2PDF` stay as thin legacy facades with unchanged contracts. Two PRs: PR 1 adds the engine (wkhtmltopdf still first in `ConvertToEdoc`); PR 2 switches over and deletes wkhtmltopdf.

**Tech Stack:** Java 25, Spring 7.0.6, Struts 7.1.1, Flying Saucer 10.7.0 (`org.xhtmlrenderer`), OpenPDF 3.0.3 (`org.openpdf.*`, vendored), PDFBox 3.0.8, jsoup 1.23.2, ICU4J 78.1, TwelveMonkeys ImageIO 3.15.3, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-10-29-flying-saucer-html-pdf-consolidation-design.md` (revision 7). Read it alongside this plan; this plan cites its sections by title ("spec §Images") instead of repeating them.

## Global Constraints

- Target branch `release/2026.08`; do not change the `-SNAPSHOT` version or the SCM tag. PR 1 is opened from `claude/flying-saucer-html-pdf-consolidation` (already contains `origin/release/2026.08`, the spec and this plan). PR 2 is a new branch `claude/flying-saucer-html-pdf-removal` cut from `origin/release/2026.08` after PR 1 merges.
- Versions: Flying Saucer `10.7.0`; OpenPDF core stays `3.0.3`; PDFBox `3.0.8`; ICU4J `78.1` (declare explicitly); TwelveMonkeys `${twelvemonkeys.version}` = `3.15.3` (`imageio-tiff` only: `imageio-webp` was added in Task 1 and removed again in Task 3 fix round 2, ruling R21).
- New module root: `src/main/java/io/github/carlos_emr/carlos/documentManager/htmlpdf/`. Tests mirror it under `src/test/java/...`. Fixtures: `src/test/resources/htmlpdf/`.
- Every new Java file: the CARLOS header from `docs/copyright-header-carlos.md`, class JavaDoc with `@since 2026-10-29`, no `@author`. Every new package gets a `package-info.java` stating its boundary and allowed dependencies (spec §Package dependencies).
- Logging: `private static final Logger logger = MiscUtils.getLogger();`. Never log a path, file name, URL, HTML or exception message from the engine; log exception **class names** only. Use `LogSafe.sanitize` for the one file name `ConvertToEdoc` already logs.
- Path containment checks use `PathValidationUtils.validateExistingPath(File, File)` (throws `SecurityException`), looping over the allowed directories.
- Case-insensitive comparisons use `toLowerCase(Locale.ROOT)`; each such site carries `@SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "...")` plus an adjacent `//` comment with the same reason (CLAUDE.md, static analysis).
- Tests: JUnit 5, AssertJ, names `should<Action>_<context>()` with exactly one underscore; module tests tagged `@Tag("unit") @Tag("htmlpdf")` and named `*UnitTest`; the one exception is `HtmlPdfMemoryUnitTest`, tagged only `@Tag("slow") @Tag("htmlpdf")` (so it runs with `-Dgroups=htmlpdf` or `-Dgroups=slow`, not in `make install --run-unit-tests`; the pom has no `excludedGroups`); log assertions only through `io.github.carlos_emr.carlos.test.logging.LogCapture` (`LogCapture.forLogger(Class)` / `forLogger(String)`, `messages()`, `events()`, try-with-resources). Fixtures use FAKE- names only.
- Constants, not settings: no new `carlos.properties` keys. Limits are `static final` fields in the class named in "Shared interfaces", each with a one-line comment giving the measured reason from the spec.
- Run a single test class with `mvn -q test -Dtest=<ClassName>`; the module suite with `mvn -q test -Dgroups=htmlpdf`. If Maven Central answers HTTP 429 from the sandbox, add a mirror in `~/.m2/settings.xml` for `central` pointing at `https://maven-central.storage-download.googleapis.com/maven2` (verified reachable).
- Commits: Conventional Commits; end every message with the two lines below; never add `Signed-off-by` (a maintainer confirms DCO).
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MKkHNF5XmtMPsyT3pjmP2P
  ```
- `.claude/settings.json` denies Claude writes under `.github/`. If a write there is denied, put the exact patch in the PR description for the maintainer; do not work around the rule.

## Review Focus

Inputs the spec implies but its test list does not exercise, most likely to bite first. Each has a test in the owning task.

1. **XML-invalid control characters** (U+0000–U+0008, U+000B, U+000C, U+000E–U+001F, U+FFFE, U+FFFF, unpaired surrogates) pasted into form values or hospital text. Expected: the document prints normally with those characters dropped, not in safe or text-only mode. → Task 6 (`XhtmlCleanup`) and Task 15 (service).
2. **Phone photos with an EXIF orientation** other than 1. Expected: printed upright, as a browser shows them. A JPEG with orientation ≠ 1 is planned as **converted** (decoded, rotated, re-encoded as JPEG 0.9), never embedded as is. → Task 3 (`ImageDecoding`) and Tasks 13–14.
3. **`<img src>` with a query string or fragment** (`logo.png?v=2`, `chart.svg#a`). Expected: resolves to the file; the query and fragment are ignored for `file:` sources. → Task 13.
4. **Large `data:` images pushing the HTML past the text-only size limit.** Expected: normal mode, because `data:` payloads do not count (spec §Time and memory bounds). → Task 15.
5. **Legacy upper- or mixed-case control markup** (`TYPE="CHECKBOX"`, `type="Radio"`, `checked="checked"`, `SELECTED`). Expected: identical output to lowercase markup. → Task 7.

## Refinements to the spec (decided here, consistent with its intent)

- **Webapp root lookup:** `ConvertToEdoc` gets the root from the `ServletContext` bean that `ServletContextConfig` already exposes (`applicationContext.xml` line 41): `SpringUtils.getBean(ServletContext.class).getRealPath("/")`. The spec's caller table was updated to match.
- **Shared image helpers:** both `LocalOnlyUserAgent` and `PrintImageService` need header reading, the 250-megapixel limit and subsampled streaming reads, so these live once in `engine/ImageDecoding` (static) with an `engine/ImageHeader` record.
- **Dead fallback helpers go in PR 1:** once the `ConvertToEdoc` fallback calls the service (Task 19), `prepareDocumentForFlyingSaucer` and `normalizeHtmlCommentsForXml` have no callers and are deleted then; their `--` comment test moves to `XhtmlCleanupUnitTest`. PR 2 stays "switch and deletions only".
- **HRM fixture:** the demo HRM report (`.devcontainer/db/db_data/hrm/demo-hrm-diagnostic-imaging.xml`) is a `.txt` report and never reaches the HTML engine, so the HTML HRM fixtures are synthetic; one wraps the demo report's text in typical hospital HTML.
- **Integration test context:** `src/test/resources/test-context-full.xml` scans only `@Repository`; Task 19 adds `<context:component-scan base-package="io.github.carlos_emr.carlos.documentManager.htmlpdf"/>` so `Doc2PDFIntegrationTest` finds the service.
- **HRM temp file:** `HRMPDFCreator.printPdf` deletes the temporary PDF after copying it (today it is left for the 24-hour purge).
- **Signatures:** the spec's outline shows `finish(document, mode, degradations)` and `prepare(dom, request, mode, imageMemory, degradations)`; this plan's `finish` also takes the `BundledFontSet` (the fallback-font step needs it) and `prepare` takes the base and allowed directories instead of the request (the service substitutes its private base directory). The spec's `HtmlPdfRenderServiceUnitTest` is split into `HtmlPdfRenderServiceUnitTest` (ladder, logging, limits; Task 15) and `HtmlPdfFixturesUnitTest` (fixture assertions; Task 17); the latter is what `check-report-fonts.sh` lists.
- **Log levels on the boundary type:** `HtmlPdfRenderDegradation.logLevel()` returns `org.apache.logging.log4j.Level`, so the `dto` package depends on Log4j (already a CARLOS-wide dependency). Accepted: the alternative, a module severity enum mapped again in the service, adds a type for no caller.
- **WebP is not decoded (R21):** the Task 3 review found that the only pure-Java WebP decoder cannot be bounded (spin on corrupt lossless streams, full-size rasters whatever the subsampling, container-versus-frame size mismatch, ICC-profile allocation in the header read). `imageio-webp` is removed; `ImageDecoding` allow-lists vetted formats (png, jpeg, gif, bmp, wbmp, tiff); a WebP, like a HEIC, shows the box. Wherever this plan says WebP is recognised, converted or fixture-tested, read it as "shows the box".
- **SVG parsing is a new XML entry point:** the spec's "zero requests" XXE measurement covered Flying Saucer's own parser only, so the SVG cleaner uses a hardened `DocumentBuilderFactory` (Task 14) and the SSRF test gains external-entity vectors (Tasks 14, 17).

## Shared interfaces

All later tasks use exactly these names. Package prefix `io.github.carlos_emr.carlos.documentManager.htmlpdf` is written `htmlpdf`.

```java
// htmlpdf.dto — public
public enum HtmlPdfRenderSource { FORM, HRM, EMAIL, MESSENGER }

public enum HtmlPdfRenderDegradation {
    IMAGE_DOWNSCALED(Level.INFO), IMAGE_CONVERTED(Level.INFO), SVG_REFERENCES_REMOVED(Level.INFO),
    MARGINS_REDUCED(Level.INFO), RTL_BEST_EFFORT(Level.INFO),
    IMAGE_UNAVAILABLE(Level.WARN), IMAGES_OMITTED_FOR_MEMORY(Level.WARN),
    FALLBACK_FONT_UNAVAILABLE(Level.WARN), WEBAPP_ROOT_UNAVAILABLE(Level.WARN),
    SAFE_MODE(Level.ERROR), TEXT_ONLY(Level.ERROR);
    public Level logLevel();                       // org.apache.logging.log4j.Level
}

public record HtmlPdfRenderRequest(String html, Path baseDirectory /* nullable */,
        List<Path> allowedDirectories, HtmlPdfRenderSource source) { /* validated; PHI-safe toString */ }

public record HtmlPdfRenderResult(byte[] pdf, Set<HtmlPdfRenderDegradation> degradations) {
    public int size();
    public void writeTo(OutputStream out) throws IOException;
}

// htmlpdf.engine — public, not Spring beans
public record ImageHeader(String formatName, int width, int height) {
    public long pixels();  public boolean isJpeg();
}
public final class ImageDecoding {                 // static only
    public static final long MAX_SOURCE_PIXELS = 250_000_000L;
    public static Optional<ImageHeader> readHeader(Path file);
    public static Optional<ImageHeader> readHeader(byte[] data);
    public static int subsamplingToFit(ImageHeader header, long maxPixels);   // >= 1
    public static BufferedImage readSubsampled(Path file, int subsampling) throws IOException;
    public static BufferedImage readSubsampled(byte[] data, int subsampling) throws IOException;
    public static int exifOrientation(Path file);  // 1..8, 1 when absent or not JPEG
    public static int exifOrientation(byte[] data);
    public static BufferedImage applyOrientation(BufferedImage image, int orientation);
}
public final class LocalOnlyUserAgent extends ITextUserAgent {
    public LocalOnlyUserAgent(ITextOutputDevice outputDevice, int dotsPerPixel, List<Path> allowedDirectories);
    public static ITextRenderer createRestrictedRenderer(List<Path> allowedDirectories, FontResolver fontResolver);
    @Override public ImageResource getImageResource(String uri);
}
public final class BundledFontSet {                // immutable, shared across renders
    public enum Face { SANS_REGULAR, SANS_BOLD, CJK, SYMBOL }
    // Loads a BaseFont per path (IDENTITY_H, embedded) for coverage; CJK and SYMBOL may be null or fail to load → unavailable;
    // a SANS face that is null or fails to load throws PDFGenerationException.
    public static BundledFontSet load(Path sansRegular, Path sansBold, Path cjk, Path symbol) throws PDFGenerationException;
    public static final String FAMILY_SANS = "CARLOS Sans", FAMILY_CJK = "CARLOS CJK", FAMILY_SYMBOL = "CARLOS Symbol";
    public boolean isAvailable(Face face);
    public boolean canDraw(Face face, int codePoint);
    public String fontPath(Face face);             // filesystem path; ",0" suffix for the TTC
}
public final class BundledFontResolver extends ITextFontResolver {
    public static final float MIN_FONT_SIZE_DOTS = 200f;
    public BundledFontResolver(BundledFontSet fonts);
}

// htmlpdf.support — public static utilities
XhtmlCleanup:      removeScriptsAndComments, removeInvisibleCharacters, rewriteFixedPositioning,
                   mapOrderedListTypes, clampRowspans, markPaginatedTables, addMissingAltText  (each: static void x(Document))
                   static String serialize(Document)
PrintFormControls: static void replaceControls(Document)
RtlTextShaping:    static void shape(Document, Set<HtmlPdfRenderDegradation> degradations)
FallbackFontRuns:  static void apply(Document, BundledFontSet, Set<HtmlPdfRenderDegradation> degradations)
PrintStylesheet:   static final String PASS_1_PAGE_RULE; static String css(); static String pageRule(double widthPt, double heightPt, double marginPt)
PrintMessages:     static String imageUnavailable(); static Optional<String> noteFor(HtmlPdfRenderDegradation)

// htmlpdf.service — HtmlPdfRenderService public; everything else package-private
public class HtmlPdfRenderService {
    public HtmlPdfRenderResult render(HtmlPdfRenderRequest request) throws PDFGenerationException;
}
enum PrintMode { NORMAL, SAFE, TEXT_ONLY }
class PrintDocumentComposer {
    Document prepare(String html, PrintMode mode, Set<HtmlPdfRenderDegradation> degradations);           // steps a–i
    String finish(Document document, PrintMode mode, BundledFontSet fonts, Set<HtmlPdfRenderDegradation> degradations); // k–m
}
class PrintImageService {
    PreparedPrintImages prepare(Document document, Path baseDirectory, List<Path> allowedDirectories,
            PrintMode mode, long imageMemoryBytes, Set<HtmlPdfRenderDegradation> degradations) throws IOException; // step j
    ImagePlan plan(Document document, Path baseDirectory, List<Path> allowedDirectories, long imageMemoryBytes);
    PreparedPrintImages apply(Document document, ImagePlan plan, Set<HtmlPdfRenderDegradation> degradations) throws IOException;
}
record ImagePlan(List<PlannedImage> images, long totalCharge) {}
record PlannedImage(Element element, ImageOutcome outcome, Path file /* nullable */, byte[] data /* nullable */,
        ImageHeader header /* nullable */, int subsampling, int orientation, long charge, String reason) {}
enum ImageOutcome { AS_IS, CONVERT, CLEAN_SVG, BOX }
final class PreparedPrintImages implements AutoCloseable { Path directory() /* nullable */; static PreparedPrintImages none(); void close(); }
class PrintLayoutService {
    @Autowired PrintLayoutService();                                                   // LocalOnlyUserAgent::createRestrictedRenderer
    PrintLayoutService(BiFunction<List<Path>, FontResolver, ITextRenderer> rendererFactory); // tests
    byte[] render(String xhtml, Path baseDirectory, List<Path> allowedDirectories, BundledFontSet fonts,
            Set<HtmlPdfRenderDegradation> degradations);   // throws HtmlPdfRenderException
}
final class PageZoomCalculator { static float zoomFor(float pass1WidthPt); static PageGeometry pass2Geometry(float pass1WidthPt); }
record PageGeometry(float widthPt, float heightPt, float marginPt, float zoom) {}
class RenderedPdfValidator { Result validate(byte[] pdf); record Result(List<String> messages, boolean valid) {} }
class BundledFontFileService {
    @Autowired BundledFontFileService();                           // java.io.tmpdir, this class's ClassLoader
    BundledFontFileService(Path tempRoot, ClassLoader resources);  // tests
    BundledFontSet fontSet() throws PDFGenerationException;
}
final class HtmlPdfRenderException extends RuntimeException {
    enum Phase { PREPARE, IMAGES, LAYOUT_PASS_1, LAYOUT_PASS_2, PLACE, VALIDATE }
    HtmlPdfRenderException(PrintMode mode, Phase phase, Throwable original);  // message: mode, phase, original class; copies stack trace; no cause chained
    Phase phase();
}
```

Limits and where they live: `HtmlPdfRenderService` — `RENDER_SLOTS = 2`, `SLOT_WAIT = Duration.ofSeconds(10)`, image memory per slot `Runtime.maxMemory() / 4 / RENDER_SLOTS`, `TEXT_ONLY_HTML_CHARS` (provisional `8_000_000`, set from measurements in Task 21). `PrintImageService` — `MAX_DECODED_PIXELS = 25_000_000`, `AS_IS_MAX_FILE_BYTES = 32L << 20`, `BYTES_PER_DECODED_PIXEL = 16`, `JPEG_CHARGE_FACTOR = 4`, `JPEG_QUALITY = 0.9f`. `LocalOnlyUserAgent` — `MAX_FILE_BYTES = 128L << 20`, `MAX_BACKGROUND_PIXELS = 4_000_000`. `RtlTextShaping` — `LONG_RUN_CHARS = 40`. `PageZoomCalculator` — `LETTER_WIDTH_PT = 612`, `LETTER_HEIGHT_PT = 792`, `MARGIN_PT = 28.3465f`, `DEFAULT_ZOOM = 0.8f`, `PASS_1_MAX_WIDTH_PT = 765.5f`, `LOW_ZOOM_WARNING = 0.4f`.

## File map

| File | Responsibility | Task |
|---|---|---|
| `pom.xml`, `dependencies-lock.json` | FS 10.7.0, ICU4J, imageio-webp (PR 1); removals (PR 2) | 1, 25 |
| `htmlpdf/package-info.java`, `htmlpdf/dto/*` | Module boundary; request, result, source, degradations | 2 |
| `htmlpdf/engine/ImageHeader.java`, `ImageDecoding.java` | Header reads, subsampled streaming reads, EXIF orientation | 3 |
| `htmlpdf/engine/LocalOnlyUserAgent.java` (moved) | Resource guard, default stylesheet, image caps | 4 |
| `htmlpdf/engine/BundledFontSet.java`, `BundledFontResolver.java`, `htmlpdf/service/BundledFontFileService.java` | Bundled fonts | 5 |
| `htmlpdf/support/*.java` | Markup rules, stylesheet, printed text | 6–10 |
| `oscarResources_{en,fr,es,pl,pt_BR}.properties` | `htmlpdf.*` keys | 10 |
| `htmlpdf/service/PrintDocumentComposer.java`, `PrintMode.java` | Steps a–i, k–m | 11 |
| `htmlpdf/service/PageZoomCalculator.java`, `PageGeometry.java`, `PrintLayoutService.java`, `RenderedPdfValidator.java` | Layout, Letter placement, output checks | 12 |
| `htmlpdf/service/PrintImageService.java`, `ImagePlan.java`, `PlannedImage.java`, `ImageOutcome.java`, `PreparedPrintImages.java` | Step j | 13–14 |
| `htmlpdf/service/HtmlPdfRenderService.java`, `HtmlPdfRenderException.java` | Entry point, slots, ladder, logging | 15 |
| `src/main/resources/log4j2.xml`, architecture/wiring tests | Guardrails | 16 |
| `src/test/resources/htmlpdf/**`, end-to-end tests, `scripts/check-report-fonts.sh` | Fixtures and cross-cutting tests | 17 |
| `documentManager/ConvertToEdoc.java` | Facade: path fixes, request, error handling | 18, 19, 24 |
| `util/Doc2PDF.java` | Facade (Messenger) | 19 |
| `hospitalReportManager/HRMPDFCreator.java`, `HRMUtil.java`, `PrintHRMReport2Action.java`, `managers/EmailManager.java` | Error paths | 20 |
| `scripts/htmlpdf/compare-with-wkhtmltopdf.sh`, test-scope `HtmlPdfComparisonMain.java` | One-off comparison (PR 1 only) | 21, 25 |
| `docs/html-pdf-rendering.md`, `docs/pdf-font-policy.md`, `CLAUDE.md` | Documentation | 22 |
| Deletions listed in spec §Removals and Packaging | PR 2 | 24–26 |

---

# Phase 1 — PR 1: New engine

### Task 1: Dependencies

**Files:**
- Modify: `pom.xml:1486` (`flying-saucer-pdf` version), after `pom.xml:857` (`imageio-tiff`), after `pom.xml:1147` (`jsoup`)
- Modify: `dependencies-lock.json` (regenerated, never hand-edited)
- Create: `src/test/java/io/github/carlos_emr/carlos/documentManager/htmlpdf/HtmlPdfDependenciesUnitTest.java`

**Interfaces:**
- Produces: Flying Saucer 10.7.0 API (`ITextRenderer.setScaleToFit(boolean)`, the 5-argument `ITextRenderer` constructor), `com.ibm.icu.text.Bidi`/`ArabicShaping`, an `ImageIO` WebP reader.

- [ ] **Step 1: Write the failing test**

```java
@Tag("unit") @Tag("htmlpdf")
class HtmlPdfDependenciesUnitTest {
    @Test void shouldFindWebpReader_onClasspath() {
        assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
    }
    @Test void shouldExposeScaleToFit_inFlyingSaucer() throws Exception {
        assertThat(ITextRenderer.class.getMethod("setScaleToFit", boolean.class)).isNotNull();
        assertThat(ITextRenderer.class.getConstructor(float.class, int.class, ITextOutputDevice.class,
                ITextUserAgent.class, FontResolver.class)).isNotNull();
    }
    @Test void shouldReorderHebrew_withIcu4j() {
        Bidi bidi = new Bidi();
        bidi.setPara("אב", Bidi.RTL, null);
        assertThat(bidi.writeReordered(Bidi.DO_MIRRORING)).isEqualTo("בא");
    }
}
```

- [ ] **Step 2: Run it; expect failure** — `mvn -q test -Dtest=HtmlPdfDependenciesUnitTest` → FAIL (no WebP reader; `setScaleToFit` missing; ICU may compile transitively).

- [ ] **Step 3: Edit `pom.xml`**
  - `org.xhtmlrenderer:flying-saucer-pdf` `10.0.6` → `10.7.0`; keep the `com.github.librepdf:openpdf` exclusion (1488–1491).
  - After `imageio-tiff` add `com.twelvemonkeys.imageio:imageio-webp` at `${twelvemonkeys.version}` with a comment: read-only WebP decoding for HTML-to-PDF images; BSD 3-clause.
  - After `jsoup` add `com.ibm.icu:icu4j` `78.1` with a comment: bidi reordering and Arabic shaping for HTML-to-PDF; was transitive at the same locked version.

- [ ] **Step 4: Regenerate the lock** — `mvn se.vandmo:dependency-lock-maven-plugin:lock` (what `make lock` runs). Check `git diff dependencies-lock.json`: `flying-saucer-core`/`-pdf` at 10.7.0, `imageio-webp` 3.15.3 added, `icu4j` still 78.1. List every other changed entry in the commit message (FS 10.7.0 may move Batik).

- [ ] **Step 5: Run tests** — `mvn -q test -Dtest='HtmlPdfDependenciesUnitTest,LocalOnlyUserAgentUnitTest,ConvertToEdocUnitTest'` → PASS (existing classes compile unchanged against 10.7.0, spec §Decisions).

- [ ] **Step 6: Commit** — `build(deps): upgrade Flying Saucer to 10.7.0, add ICU4J and WebP reader`.

### Task 2: Boundary types (`dto`)

**Files:**
- Create: `htmlpdf/package-info.java`, `htmlpdf/dto/package-info.java`, `htmlpdf/dto/HtmlPdfRenderSource.java`, `HtmlPdfRenderDegradation.java`, `HtmlPdfRenderRequest.java`, `HtmlPdfRenderResult.java`
- Test: `htmlpdf/dto/HtmlPdfRenderRequestUnitTest.java`, `HtmlPdfRenderResultUnitTest.java`, `HtmlPdfRenderDegradationUnitTest.java`

**Interfaces:**
- Produces: the four `dto` types exactly as in "Shared interfaces".

- [ ] **Step 1: Write the failing tests**

```java
// HtmlPdfRenderRequestUnitTest
@Test void shouldRejectNullHtml_forConstructor()              { assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderRequest(null, null, List.of(tmp), FORM)); }
@Test void shouldRejectEmptyAllowedDirectories_forConstructor() { assertThatIllegalArgumentException().isThrownBy(() -> new HtmlPdfRenderRequest("x", null, List.of(), FORM)); }
@Test void shouldRejectRelativeOrRootDirectory_forConstructor() // Path.of("rel"), Path.of("/"), tmp.resolve("a/../b") (unnormalised) each → IAE
@Test void shouldRejectBaseOutsideAllowed_forConstructor()      // base = other temp dir → IAE
@Test void shouldAcceptNullBase_forMissingWebappRoot()          // baseDirectory() is null, no exception
@Test void shouldCopyAllowedDirectories_forDefensiveCopy()      // mutate source ArrayList after construction; allowedDirectories() unchanged and unmodifiable
@Test void shouldOmitHtmlAndPaths_fromToString()                // html "FAKE-Patient <b>x</b>", allowed [tmp], base tmp.resolve("FAKE-dir") (created) → toString contains "source=FORM", "htmlLength=", not "FAKE-"
// HtmlPdfRenderResultUnitTest
@Test void shouldCopyBytes_onConstructionAndAccess()            // mutate input array and returned array; pdf() unchanged
@Test void shouldCompareContents_forEquality()                  // two results with equal bytes and sets are equal, same hashCode
@Test void shouldPrintSizeAndNamesOnly_fromToString()           // contains "size=3" and "IMAGE_CONVERTED", not "[B@"
@Test void shouldExposeUnmodifiableDegradations_forResult()
@Test void shouldWriteWithoutCopy_toOutputStream()
// HtmlPdfRenderDegradationUnitTest
@Test void shouldMapLevels_perSpecObservabilityTable()          // INFO: 5 constants, WARN: 4, ERROR: SAFE_MODE, TEXT_ONLY (spec §Observability)
```

- [ ] **Step 2: Run; expect compilation failure** — `mvn -q test -Dtest='HtmlPdfRender*UnitTest'`.

- [ ] **Step 3: Implement** the four types. `HtmlPdfRenderRequest` validates in its canonical constructor (`Objects.requireNonNull` for `html`, `source`, `allowedDirectories`; `IllegalArgumentException` for empty list, non-absolute, `!p.equals(p.normalize())`, `p.getParent() == null`, or a non-null base not under any allowed directory by `Path.startsWith`), stores `List.copyOf`, overrides `toString()` as `HtmlPdfRenderRequest[source=FORM, htmlLength=123, allowedDirectories=3]`. `HtmlPdfRenderResult` stores `pdf.clone()` and `Collections.unmodifiableSet(EnumSet.noneOf(...) + addAll)`, overrides `pdf()`, `equals`, `hashCode`, `toString`. Write both `package-info.java` files: the module one states the layering, PHI and dependency rules (spec §Architecture rules and §Package dependencies); the `dto` one says it depends on nothing else in the module.

- [ ] **Step 4: Run; expect PASS** — same command.

- [ ] **Step 5: Commit** — `feat(htmlpdf): add render request, result and degradation types`.

### Task 3: Image decoding helpers (`engine`)

**Files:**
- Create: `htmlpdf/engine/package-info.java`, `htmlpdf/engine/ImageHeader.java`, `htmlpdf/engine/ImageDecoding.java`
- Create fixtures (generated once with PIL and committed; no test generates them at run time, because `imageio-webp` is read-only and CI has no PIL): `src/test/resources/htmlpdf/images/exif-orientation-6.jpg` (8 × 4 px: `python3 -c "from PIL import Image; im=Image.new('RGB',(8,4),'red'); ex=im.getexif(); ex[0x0112]=6; im.save('src/test/resources/htmlpdf/images/exif-orientation-6.jpg', exif=ex)"`), `images/opaque-4x4.webp` and `images/alpha-4x4.webp` (`Image.new('RGB',(4,4),'red').save(..., 'WEBP')` and `Image.new('RGBA',(4,4),(255,0,0,128)).save(..., 'WEBP', lossless=True)`)
- Test: `htmlpdf/engine/ImageDecodingUnitTest.java`; test helper `htmlpdf/engine/PngHeaders.java` (test scope, `public final class` so the `service` test package can use it: `public static byte[] declaring(int width, int height)` writes the PNG signature, a valid IHDR with CRC and IEND, no IDAT; reused by Tasks 4 and 13)

**Interfaces:**
- Produces: `ImageHeader`, `ImageDecoding` as in "Shared interfaces".

- [ ] **Step 1: Write the failing tests**

```java
@Test void shouldReadHeaderWithoutDecoding_forHugeDeclaredPng() {
    byte[] png = PngHeaders.declaring(20_000, 20_000);   // test helper: signature + IHDR + IEND, no IDAT
    assertThat(ImageDecoding.readHeader(png)).hasValueSatisfying(h -> assertThat(h.pixels()).isEqualTo(400_000_000L));
}
@Test void shouldReturnEmpty_forUnknownOrCorruptHeader()        // "HEIC"-like ftyp bytes and random bytes → Optional.empty()
@Test void shouldRecogniseWebp_forHeader()                       // fixture images/opaque-4x4.webp; formatName "webp"
@Test void shouldPickSmallestSubsampling_toFitLimit()            // 6600×10200, 25_000_000 → 2; 4000×3000, 25_000_000 → 1; 20000×20000, 4_000_000 → 10
@Test void shouldReadSubsampledFromStream_forLargePng() throws IOException // generated 4000×3000 PNG, subsampling 2 → 2000×1500
@Test void shouldReadExifOrientation_forRotatedJpeg()            // fixture → 6; a JPEG without EXIF → 1; a PNG → 1
@Test void shouldRotateQuarterTurn_forOrientation6()             // 8×4 image → 4×8
```

- [ ] **Step 2: Run; expect failure** — `mvn -q test -Dtest=ImageDecodingUnitTest`.

- [ ] **Step 3: Implement** — headers via `ImageIO.createImageInputStream` + `ImageIO.getImageReaders(stream)` + `reader.getWidth(0)/getHeight(0)` (never `read`); `readSubsampled` via `ImageReadParam.setSourceSubsampling(s, s, 0, 0)` on a file-backed `ImageInputStream` (never `Files.readAllBytes`); EXIF Orientation (tag `0x0112`) from the JPEG APP1 segment using TwelveMonkeys `imageio-metadata` (already a dependency); `applyOrientation` with an `AffineTransform` for all 8 values. `MAX_SOURCE_PIXELS` carries the comment "about 15 800 × 15 800; sharp refuses above 268 MP, Debian ImageMagick caps area at 256 MP (spec §Images)". `package-info`: engine types are Flying Saucer/OpenPDF extensions and image helpers, created per render, not Spring beans, depending on nothing else in the module.

- [ ] **Step 4: Run; expect PASS**.

- [ ] **Step 5: Commit** — `feat(htmlpdf): add image header, subsampling and EXIF helpers`.

### Task 4: Move and harden `LocalOnlyUserAgent`

**Files:**
- Move (git mv): `src/main/java/io/github/carlos_emr/carlos/documentManager/LocalOnlyUserAgent.java` → `htmlpdf/engine/LocalOnlyUserAgent.java`; `src/test/java/.../documentManager/LocalOnlyUserAgentUnitTest.java` → `src/test/java/.../documentManager/htmlpdf/engine/LocalOnlyUserAgentUnitTest.java`
- Modify: `documentManager/ConvertToEdoc.java` (add import; JavaDoc `{@link}` at 421), `util/Doc2PDF.java:65` (import)

**Interfaces:**
- Consumes: `ImageDecoding`, `ImageHeader` (Task 3).
- Produces: `LocalOnlyUserAgent(ITextOutputDevice, int, List<Path>)`, `createRestrictedRenderer(List<Path>, FontResolver)`. The old `createRestrictedRenderer()` stays, annotated `@Deprecated(forRemoval = true)` with its current implicit roots (base URL directory, `java.io.tmpdir`, `catalina.base/work`), until Task 19 deletes it.

- [ ] **Step 1: Move the files** — `git mv` both; fix `package` lines and imports; build compiles; `mvn -q test -Dtest=LocalOnlyUserAgentUnitTest` → PASS (behaviour unchanged). Commit `refactor(htmlpdf): move LocalOnlyUserAgent into htmlpdf.engine` so the rename keeps history.

- [ ] **Step 2: Write the failing tests** (in the moved test; rewrite `createAgent()` at line ~79 to pass `List.of(allowedRoot)`; the three renderer tests at ~472–494 call `createRestrictedRenderer(List.of(tmp), new ITextFontResolver())`)

```java
@Nested class DefaultStylesheetTests {
    @Test void shouldOpenFlyingSaucerDefaultStylesheet_byExactUri()       // DEFAULT_STYLESHEET_URI stream non-null in resolveAndOpenStream and openStream
    @Test void shouldNotWarn_whenBaseUrlIsDefaultStylesheet()             // LogCapture.forLogger(LocalOnlyUserAgent.class).messages() empty
    @Test void shouldBlockOtherEntries_inFlyingSaucerJar()                // same jar, other entry → null
    @Test void shouldBlockJarHttp_forStylesheet()
}
@Nested class ExplicitRootsTests {
    @Test void shouldAllowFile_underAnAllowedDirectory()
    @Test void shouldBlockFile_inWorkingDirectoryNotAllowed()             // file under Path.of("").toAbsolutePath() but outside allowed list → null
    @Test void shouldLogNoPath_whenFileBlocked()                          // messages contain "file" scheme only, never the FAKE- path
}
@Nested class ImageResourceTests {
    @Test void shouldLoadDataUriImage_asIs()
    @Test void shouldLoadLargeJpegBackground_asIs()                       // 4000×3000 JPEG → ImageResource with an image
    @Test void shouldDownscaleLargePngBackground_toFourMegapixels()       // 3000×2000 PNG → image width*height <= 4_000_000 (in FS dots scaled back)
    @Test void shouldReturnEmpty_forCorruptImage()
    @Test void shouldReturnEmpty_forFileOver128Megabytes()                // sparse file via RandomAccessFile.setLength
    @Test void shouldReturnEmpty_forHeaderOver250Megapixels()             // PngHeaders.declaring(20_000, 20_000); never decoded
}
```

- [ ] **Step 3: Run; expect failures**.

- [ ] **Step 4: Implement** per spec §Resource loading and security and §Images "During layout": allowed directories come only from the constructor (plus the default stylesheet URI); `DEFAULT_STYLESHEET_URI` computed once from `Configuration.valueFor("xr.css.user-agent-default-css") + "XhtmlNamespaceHandler.css"` resolved with `XhtmlCssOnlyNamespaceHandler.class.getResource(...)`, matched by `equals` in `resolveAndOpenStream`, `openStream` and silently in `setBaseURL`; containment via `PathValidationUtils.validateExistingPath`; `getImageResource` rules (data:/file:, 128 MB, header first, JPEG as is, other backgrounds over `MAX_BACKGROUND_PIXELS` subsampled, over `MAX_SOURCE_PIXELS` or any exception → `new ImageResource(uri, null)`, else `super.getImageResource(uri)`). Keep the existing `@SuppressFBWarnings IMPROPER_UNICODE` site and comment.

- [ ] **Step 5: Run; expect PASS** — `mvn -q test -Dtest='LocalOnlyUserAgentUnitTest,Doc2PDF*,ConvertToEdocUnitTest'`.

- [ ] **Step 6: Commit** — `feat(htmlpdf): explicit allowed directories, default stylesheet and image caps in LocalOnlyUserAgent`.

### Task 5: Bundled fonts

**Files:**
- Create: `htmlpdf/engine/BundledFontSet.java`, `htmlpdf/engine/BundledFontResolver.java`, `htmlpdf/service/package-info.java`, `htmlpdf/service/BundledFontFileService.java`
- Test: `htmlpdf/engine/BundledFontResolverUnitTest.java`, `htmlpdf/engine/BundledFontSetUnitTest.java`, `htmlpdf/service/BundledFontFileServiceUnitTest.java`

**Interfaces:**
- Consumes: classpath resources `net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf`, `DejaVuSans-Bold.ttf` (from `jasperreports-fonts`), `fonts/wqy/wqy-microhei.ttc`, `fonts/symbola/Symbola_hint.ttf` (same set as `commn/printing/PdfFonts.java:29-31`).
- Produces: `BundledFontSet`, `BundledFontResolver`, `BundledFontFileService.fontSet()` (package-private `@Service`; `@Autowired BundledFontFileService()` uses `java.io.tmpdir` and its own class loader; package-private `BundledFontFileService(Path tempRoot, ClassLoader resources)` for tests).

- [ ] **Step 1: Write the failing tests** (spec §Testing rows `BundledFontResolverUnitTest` and `BundledFontFileServiceUnitTest`, plus coverage)

```java
// BundledFontFileServiceUnitTest (@TempDir root)
@Test void shouldNameDirectory_fromBundleHash()            // dir name matches "carlos-pdf-fonts-[0-9a-f]{12}", same across two services, directly under root (never under root/carlos-temp)
@Test void shouldTouchNoFile_whenConstructed()             // root empty after new BundledFontFileService(root, getClass().getClassLoader())
@Test void shouldRestoreFile_deletedAfterFirstUse()        // fontSet(); delete DejaVuSans.ttf; fontSet() → file back, new set instance
@Test void shouldRewriteFile_whenSameSizeContentAltered()  // flip one byte; restart service; fontSet() → SHA-256 matches classpath
@Test void shouldUsePrivateDirectory_whenDirectoryIsSymlink() // pre-create symlink with the hashed name → set paths not under it; WARN via LogCapture
@Test void shouldCreateOwnerOnlyDirectory_wherePosixSupported()
@Test void shouldExtractOnce_underConcurrentFirstUse()     // 8 threads; one directory, files intact
@Test void shouldFailClearly_whenDejaVuMissing()           // new BundledFontFileService(root, new URLClassLoader(new URL[0], null)) → fontSet() throws PDFGenerationException
// BundledFontResolverUnitTest: render tiny XHTML with ITextRenderer + resolver, inspect PDFBox fonts
@Test void shouldUseCarlosSans_forEveryFamily()            // serif, monospace, "Times New Roman", unknown, none → BaseFont name contains "DejaVuSans" and embedded
@Test void shouldUseBoldFace_forBoldText()
@Test void shouldSlantRegularFace_forItalicText()          // same font as regular; glyph widths equal
@Test void shouldRaiseTinyText_toMinimumSize()             // 8px → 10px (7.5 pt without zoom)
@Test void shouldUseFallbackFamilies_whenRequested()       // font-family:'CARLOS CJK' → WenQuanYi; 'CARLOS Symbol' → Symbola
// BundledFontSetUnitTest (fonts extracted to @TempDir from the classpath)
@Test void shouldReportCoverage_forBundledFaces()          // canDraw(SANS_REGULAR,'é') true; ('中') false; (CJK,'中') true; (SYMBOL, 0x1D11E) true
@Test void shouldMarkFallbackUnavailable_whenPathNull()    // load(sans, sansBold, null, symbol): isAvailable(CJK) false
@Test void shouldThrow_whenSansFaceMissing()               // load(missingPath, sansBold, null, null) → PDFGenerationException
```

- [ ] **Step 2: Run; expect failure**.

- [ ] **Step 3: Implement** per spec §Fonts. Directory: `<tmpdir>/carlos-pdf-fonts-<first 12 hex of SHA-256 over the four files' bytes in the order above>`; owner and symlink checks with `Files.isSymbolicLink` and `Files.getOwner(dir, NOFOLLOW_LINKS)`; atomic writes (`Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`); `ReentrantLock` around extraction; `volatile BundledFontSet`; per-call existence and size check, re-verify on mismatch. `BundledFontResolver` registers each face with `addFont(path, family, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, null)` (TTC path with `,0`), never registers obliques, and overrides `resolveFont(SharedContext, FontSpecification)` mapping the first family (lower-cased with `Locale.ROOT`, quotes and whitespace stripped) to `CARLOS CJK`/`CARLOS Symbol`/`CARLOS Sans` and raising size to `MIN_FONT_SIZE_DOTS`. The `service/package-info.java` states: only `HtmlPdfRenderService` is public; beans are stateless singletons with constructor injection; no servlet or Struts types.

- [ ] **Step 4: Run; expect PASS** — `mvn -q test -Dtest='Bundled*UnitTest'`.

- [ ] **Step 5: Commit** — `feat(htmlpdf): bundled font set, resolver and verified font files`.

### Task 6: `XhtmlCleanup` (steps a, b, d–g, i, m)

**Files:**
- Create: `htmlpdf/support/package-info.java`, `htmlpdf/support/XhtmlCleanup.java`
- Test: `htmlpdf/support/XhtmlCleanupUnitTest.java`

**Interfaces:**
- Produces: the `XhtmlCleanup` methods in "Shared interfaces"; `serialize` returns XML-syntax, XHTML-escaped, UTF-8, non-pretty output keeping `<!DOCTYPE html>`.

- [ ] **Step 1: Write the failing tests** — the `XhtmlCleanupUnitTest` row of spec §Testing, one test per rule, including:

```java
@Test void shouldRemoveCommentWithDoubleHyphen_forXmlValidity()   // moved case: "<!-- bad -- comment-->" → no comment node; serialize() parses with DocumentBuilderFactory
@Test void shouldClampRowspan_toRowGroupEnd()                     // td rowspan=4 with 3 rows left in tbody → 3; rowspan=0 → rows left
@Test void shouldMarkOnlyTheadTablesWithoutRowspans_forPagination()
@Test void shouldDropXmlInvalidCharacters_fromTextAndValues()     // Review Focus 1: "FAKE\u0001-Name\u000B" and value="a￾b" and a lone "\uD800" → removed; serialize() parses with the JDK XML parser
```

- [ ] **Step 2: Run; expect failure**.

- [ ] **Step 3: Implement** the rules exactly as spec §Render pipeline steps a, b, d, e, f, g, i, m. `removeInvisibleCharacters` covers U+00AD, the bidi controls listed in step b, and the XML-invalid characters of Review Focus 1, in text nodes and form-control `value` attributes. Support `package-info`: static, dependency-free domain-noun utilities; may use `dto` and `engine` value types; never `service`.

- [ ] **Step 4: Run; expect PASS**.

- [ ] **Step 5: Commit** — `feat(htmlpdf): XHTML cleanup rules for printing`.

### Task 7: `PrintFormControls` (step c)

**Files:**
- Create: `htmlpdf/support/PrintFormControls.java`
- Test: `htmlpdf/support/PrintFormControlsUnitTest.java`

- [ ] **Step 1: Write the failing tests** — one per row of spec §Form controls plus the sizing, attribute-copy and boolean-attribute rules; and Review Focus 5:

```java
@ParameterizedTest @ValueSource(strings = {"CHECKBOX", "Checkbox", "checkbox"})
void shouldPrintCheckedMark_forAnyTypeCase(String type) {
    Document d = Jsoup.parse("<input TYPE=\"" + type + "\" CHECKED=\"checked\">");
    PrintFormControls.replaceControls(d);
    assertThat(d.select("span.carlos-pdf-checkbox").text()).isEqualTo("☒");
}
@Test void shouldNotRevealLength_forPasswordValue()        // value "FAKE-secret-1234" → exactly 8 "•"
@Test void shouldPrintCheckedMark_whenCheckedIsFalseString() // checked="false" → ☒
@Test void shouldRemoveHiddenAndButtons_fromOutput()       // hidden value "FAKE-12345" and <button> absent from d.text() and d.html()
@Test void shouldJoinSelectedOptions_forMultiSelect()      // inside optgroup; ", " separator
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** per spec §Form controls (attribute copies `id class style title dir lang`, classes `carlos-pdf-control carlos-pdf-<kind>`, `data-input-type`, `min-width`/`min-height` from `size`/`cols`/`rows` unless inline width/height). Type comparison lower-cases with `Locale.ROOT` (annotated per Global Constraints).
- [ ] **Step 4: Run; expect PASS**.
- [ ] **Step 5: Commit** — `feat(htmlpdf): print form controls as static text and marks`.

### Task 8: `RtlTextShaping` (step h)

**Files:**
- Create: `htmlpdf/support/RtlTextShaping.java`
- Test: `htmlpdf/support/RtlTextShapingUnitTest.java`

- [ ] **Step 1: Write the failing tests** — spec §Testing row `RtlTextShapingUnitTest`, with exact expectations:

```java
@Test void shouldReorderHebrew_toVisualOrder()          // "<p>שלום</p>" → text "םולש"
@Test void shouldJoinArabic_toPresentationForms()       // "<p>سلام</p>" → every char in U+FE70–U+FEFF and reversed order
@Test void shouldMirrorBrackets_inRtlParagraph()        // "<p dir=\"rtl\">(א)</p>" → "(א)" mirrored per Bidi.DO_MIRRORING
@Test void shouldAlignRight_forRtlBlockWithoutTextAlign()
@Test void shouldAddBestEffortNote_forRunOverFortyChars() // 41-letter Hebrew run → degradations contains RTL_BEST_EFFORT; 40 → not
@Test void shouldKeepLatinAndDigitOrder_inMixedText()   // "שלום 123 FAKE" keeps "123" and "FAKE" readable left-to-right
@Test void shouldLeaveStyleAndTitle_untouched()
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** per spec §Right-to-left text: ICU4J `ArabicShaping(LETTERS_SHAPE | TEXT_DIRECTION_LOGICAL)` (`shape` throws the checked `ArabicShapingException`; catch it and keep that node's original text) then `Bidi` with the paragraph level from the nearest `dir` or `Bidi.LEVEL_DEFAULT_LTR`, `writeReordered(Bidi.DO_MIRRORING)`, per text node; `text-align: right` added to the nearest block ancestor with `dir="rtl"` and no `text-align`.
- [ ] **Step 4: Run; expect PASS**.
- [ ] **Step 5: Commit** — `feat(htmlpdf): best-effort right-to-left shaping and reordering`.

### Task 9: `FallbackFontRuns` (step l)

**Files:**
- Create: `htmlpdf/support/FallbackFontRuns.java`
- Test: `htmlpdf/support/FallbackFontRunsUnitTest.java`

**Interfaces:**
- Consumes: `BundledFontSet` (Task 5).

- [ ] **Step 1: Write the failing tests** — spec §Testing row `FallbackFontRunsUnitTest`, e.g.:

```java
@Test void shouldWrapCjkRun_inCjkSpan()        // "FAKE 中文 x" → one span style="font-family:'CARLOS CJK'; font-style:normal" containing "中文"
@Test void shouldUseSymbola_forMusicalSymbol() // "𝄞" → 'CARLOS Symbol'
@Test void shouldReplaceUndrawable_withReplacementCharacter() // U+0A15 (Gurmukhi) → "�", DEBUG logs count only
@Test void shouldReportMissingFallback_whenCjkUnavailable()   // BundledFontSet.load(sans, sansBold, null, symbol) → CJK text becomes "�" and FALLBACK_FONT_UNAVAILABLE
@Test void shouldProduceIdenticalSpans_acrossRuns()
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** per spec §Fonts "Fallback-font step" (skip whitespace, `Character.getType == FORMAT`, variation selectors; "drawable" means both `SANS_REGULAR` and `SANS_BOLD` can draw it; maximal same-face runs).
- [ ] **Step 4: Run; expect PASS**.
- [ ] **Step 5: Commit** — `feat(htmlpdf): fallback-font runs for characters DejaVu Sans lacks`.

### Task 10: `PrintStylesheet`, `PrintMessages` and message keys

**Files:**
- Create: `htmlpdf/support/PrintStylesheet.java`, `htmlpdf/support/PrintMessages.java`
- Modify: `src/main/resources/oscarResources_en.properties`, `_fr`, `_es`, `_pl`, `_pt_BR` (append a commented `htmlpdf` block)
- Test: `htmlpdf/support/PrintStylesheetUnitTest.java`, `htmlpdf/support/PrintMessagesUnitTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test void shouldContainSpecRules_inDefaults()            // css() contains PASS_1_PAGE_RULE and each selector of spec §Injected stylesheet
@Test void shouldFormatPageRule_withRootLocale()          // Locale.setDefault(Locale.FRENCH); pageRule(918.5, 1188.75, 33.6) uses '.' decimals; restore locale
@Test void shouldReadEnglishNotes_regardlessOfDefaultLocale() // Locale.setDefault(FRENCH); noteFor(SAFE_MODE) equals the spec's English sentence
@Test void shouldHaveNoNote_forContentPreservingDegradations() // noteFor(IMAGE_CONVERTED) empty
@Test void shouldDefineKeys_inEveryLocaleBundle()         // load each of the five files as Properties (UTF-8); all five keys present and non-blank
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement.** `PrintStylesheet.css()` is the exact CSS of spec §Injected stylesheet; `PASS_1_PAGE_RULE = "@page { size: 765pt 990pt !important; margin: 35.4331pt !important }"`; `pageRule` formats with `Locale.ROOT`. `PrintMessages` loads `ResourceBundle.getBundle("oscarResources", Locale.ENGLISH, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES))` once. Keys and values:

  | Key | en | fr | es | pl | pt_BR |
  |---|---|---|---|---|---|
  | `htmlpdf.note.safeMode` | spec §Resilience wording | Ce document a été imprimé avec une mise en forme simplifiée, car sa mise en page n'a pas pu être rendue. | Este documento se imprimió con un formato simplificado porque no se pudo representar su diseño. | Ten dokument wydrukowano w uproszczonym formacie, ponieważ nie udało się odwzorować jego układu. | Este documento foi impresso com formatação simplificada porque não foi possível renderizar o layout. |
  | `htmlpdf.note.textOnly` | spec wording | Ce document a été imprimé en texte brut, car sa mise en page n'a pas pu être rendue. | Este documento se imprimió como texto sin formato porque no se pudo representar su diseño. | Ten dokument wydrukowano jako zwykły tekst, ponieważ nie udało się odwzorować jego układu. | Este documento foi impresso como texto simples porque não foi possível renderizar o layout. |
  | `htmlpdf.note.imagesOmittedForMemory` | spec wording | Les images ont été omises parce que le serveur était occupé. Imprimez de nouveau pour les inclure. | Se omitieron las imágenes porque el servidor estaba ocupado. Vuelva a imprimir para incluirlas. | Pominięto obrazy, ponieważ serwer był zajęty. Wydrukuj ponownie, aby je uwzględnić. | As imagens foram omitidas porque o servidor estava ocupado. Imprima novamente para incluí-las. |
  | `htmlpdf.note.rtlBestEffort` | spec wording | Le texte de droite à gauche a été converti pour l'impression. Vérifiez l'ordre des lignes des longs passages de droite à gauche par rapport à l'original. | El texto de derecha a izquierda se convirtió para la impresión. Compare el orden de las líneas de los pasajes largos de derecha a izquierda con el original. | Tekst pisany od prawej do lewej został przekształcony do druku. Porównaj kolejność wierszy dłuższych fragmentów pisanych od prawej do lewej z oryginałem. | O texto da direita para a esquerda foi convertido para impressão. Confira a ordem das linhas dos trechos mais longos da direita para a esquerda com o original. |
  | `htmlpdf.imageUnavailable` | [image unavailable] | [image non disponible] | [imagen no disponible] | [obraz niedostępny] | [imagem indisponível] |

- [ ] **Step 4: Run; expect PASS** — plus `./scripts/check-i18n-properties.sh` reports no new missing keys.
- [ ] **Step 5: Commit** — `feat(htmlpdf): print stylesheet and translated document notes`.

### Task 11: `PrintDocumentComposer`

**Files:**
- Create: `htmlpdf/service/PrintMode.java`, `htmlpdf/service/PrintDocumentComposer.java`
- Test: `htmlpdf/service/PrintDocumentComposerUnitTest.java`

**Interfaces:**
- Consumes: Tasks 6–10.
- Produces: `prepare` and `finish` as in "Shared interfaces"; a package-private `@Service` with a no-argument constructor.

- [ ] **Step 1: Write the failing tests** — spec §Testing row `PrintDocumentComposerUnitTest`, including:

```java
@Test void shouldPlaceDefaultsFirst_inHead()                 // first child of <head> is <style id="carlos-pdf-defaults">
@Test void shouldPrependNote_forSafeMode()                   // degradations {SAFE_MODE} → first child of <body> has class carlos-pdf-note with the en sentence
@Test void shouldDropAuthorStylesAndBoxSvg_inSafeMode()      // <style>, <link rel=stylesheet>, style= removed; inline <svg> → span.carlos-pdf-image-unavailable
@Test void shouldBuildReadingOrderText_inTextOnlyMode()      // table row "<td>FAKE-A</td><td>FAKE-B</td>" → "FAKE-A | FAKE-B"; form-control text kept; no <img>
@Test void shouldApplyStepsInSpecOrder_forPrepare()          // a soft hyphen inside an RTL text node is removed before shaping (b before h)
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** — `prepare` parses with Jsoup, runs a, b, c, d, e, f, g, h, i (SAFE first strips author styles and boxes inline `<svg>`; TEXT_ONLY returns a new document built from the cleaned DOM); `finish` runs k (stylesheet first in `<head>`, note from `PrintMessages.noteFor` for each note-bearing degradation in the set), l, m.
- [ ] **Step 4: Run; expect PASS**.
- [ ] **Step 5: Commit** — `feat(htmlpdf): print document composer with safe and text-only modes`.

### Task 12: Layout, Letter placement and output checks

**Files:**
- Create: `htmlpdf/service/PageZoomCalculator.java`, `PageGeometry.java`, `PrintLayoutService.java` (`@Service`), `RenderedPdfValidator.java` (`@Component`), `HtmlPdfRenderException.java`
- Test: `PageZoomCalculatorUnitTest.java`, `PrintLayoutServiceUnitTest.java`, `RenderedPdfValidatorUnitTest.java`

**Interfaces:**
- Consumes: `LocalOnlyUserAgent.createRestrictedRenderer`, `BundledFontResolver`, `PrintStylesheet.PASS_1_PAGE_RULE`/`pageRule`.
- Produces: `PrintLayoutService.render(...)` returning Letter-placed PDF bytes or throwing `HtmlPdfRenderException` with phase `LAYOUT_PASS_1`, `LAYOUT_PASS_2` or `PLACE`; `RenderedPdfValidator.validate`; `HtmlPdfRenderException` as in "Shared interfaces".

- [ ] **Step 1: Write the failing tests** — spec §Testing rows `PageZoomCalculatorUnitTest`, `PrintLayoutServiceUnitTest`, `RenderedPdfValidatorUnitTest`. Key assertions:

```java
@Test void shouldKeepDefaultZoom_forNarrowPages()          // zoomFor(765.5f) == 0.8f
@Test void shouldComputeZoom_forWidePassOneWidths()        // pass-1 widths in pt: 850 px fits (content area 694.13 pt = 925 px) → zoomFor(765f) == 0.8f; 1000 px → W = 820.87f → pass2Geometry zoom 0.7404 ± 0.0005; 1400 px → W = 1120.87f → 0.5289 ± 0.0005; in each case widthPt * zoom == 612 ± 0.01 and marginPt * zoom == 28.3465 ± 0.01
@Test void shouldPlaceOnLetter_withTenMillimetreMargins()  // 400/850/1000/1400 px fixtures → every page 612×792; leftmost glyph x and top glyph y = 28 ± 1 pt (PDFTextStripper positions)
@Test void shouldUsePassOne_whenPassTwoFails()             // rendererFactory returns a real renderer first, then one whose layout() throws → output valid, MARGINS_REDUCED added
@Test void shouldWarnWithZoomOnly_whenZoomBelowPointFour() // LogCapture.forLogger(PrintLayoutService.class) (the service logs; the calculator is static and silent): message contains "0.3" and no path
@Test void shouldRenderIdentically_underUkAndCountrylessLocales()
@Test void shouldFlattenAcroForm_beforePlacement()
@Test void shouldRejectNonLetterPage_inValidator()         // and not-a-PDF, zero pages, AcroForm present, valid PDF → valid
@Test void shouldCopyStackWithoutMessage_inRenderException() // new HtmlPdfRenderException(NORMAL, LAYOUT_PASS_1, new IllegalStateException("FAKE-Patient")) → message lacks "FAKE-", getCause() null, stack trace equals original's
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** per spec §Output geometry and §Render pipeline "Lay out"/"Place on Letter"/"Check": pass 1 with `setScaleToFit(true)`, `W` = widest page; pass 2 replaces `PASS_1_PAGE_RULE` in the XHTML with `pageRule(P, 792/z, M/z)`; placement with OpenPDF `PdfReader` → `PdfStamper.setFormFlattening(true)` → new 612×792 document, `getImportedPage`, `addTemplate(page, z, 0, 0, z, 0, 792 − h·z)`, `Document.addTitle` from the source `/Title`. Renderer settings: print on, interactive off, smoothing 0, Flying Saucer's `ITextReplacedElementFactory`, base URI from `baseDirectory.toUri()`. The test constructor's `rendererFactory` lets tests inject a renderer that fails on the second pass.
- [ ] **Step 4: Run; expect PASS**.
- [ ] **Step 5: Commit** — `feat(htmlpdf): wkhtmltopdf-equivalent layout on US Letter`.

### Task 13: `PrintImageService` — planning

**Files:**
- Create: `htmlpdf/service/PrintImageService.java` (planning half), `ImagePlan.java`, `PlannedImage.java`, `ImageOutcome.java`
- Test: `htmlpdf/service/PrintImageServicePlanUnitTest.java`

**Interfaces:**
- Consumes: `ImageDecoding`, `ImageHeader`.
- Produces: `ImagePlan plan(Document, Path baseDirectory, List<Path> allowedDirectories, long imageMemoryBytes)`; writes no file.

- [ ] **Step 1: Write the failing tests** — spec §Testing row `PrintImageServiceUnitTest (plan)` (every listed case) plus Review Focus 2 and 3:

```java
@Test void shouldConvertRotatedJpeg_insteadOfEmbedding()   // exif-orientation-6.jpg → outcome CONVERT, orientation 6
@Test void shouldResolveFile_ignoringQueryAndFragment()    // src="photo.jpg?v=2" and "photo.jpg#x" → AS_IS with the file
@Test void shouldChargeJpegFourTimesFileSize_forAsIs()     // charge == 4 * Files.size
@Test void shouldPlanLargestImagesSmaller_whenOverShare()  // three 4000×3000 PNGs, imageMemoryBytes = 256 MB → total <= 256 MB, largest reduced first
@Test void shouldBoxEveryRasterImage_withoutSlot()         // imageMemoryBytes == 0 → all BOX with reason "no render slot"
@Test void shouldGiveSamePlan_forSameDocumentAndShare()
@Test void shouldBoxHeaderOverLimit_withoutDecoding()      // PngHeaders.declaring(20_000, 20_000) file → BOX
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** spec §Images rules 1–5: resolution with `baseDirectory.toUri().resolve(new URI(src))` (strip query and fragment for `file:`), containment via `PathValidationUtils`, PDF sniff, SVG detection (outcome `CLEAN_SVG`), headers, plans and charges with the "Shared interfaces" limits; per-image cap `min(MAX_DECODED_PIXELS, imageMemoryBytes / BYTES_PER_DECODED_PIXEL)`; repeated sources charged once; orientation ≠ 1 forces `CONVERT`.
- [ ] **Step 4: Run; expect PASS**.
- [ ] **Step 5: Commit** — `feat(htmlpdf): deterministic image planning against the render's memory share`.

### Task 14: `PrintImageService` — applying the plan

**Files:**
- Modify: `htmlpdf/service/PrintImageService.java` (add `apply`, `prepare`); Create: `PreparedPrintImages.java`
- Test: `htmlpdf/service/PrintImageServiceApplyUnitTest.java`

**Interfaces:**
- Produces: `prepare(...)` (plan then apply; `TEXT_ONLY` returns `PreparedPrintImages.none()`); `PreparedPrintImages` deletes its directory on `close()`.

- [ ] **Step 1: Write the failing tests** — spec §Testing row `PrintImageServiceUnitTest (apply)` plus:

```java
@Test void shouldRotateAndReencode_forExifOrientation()     // output JPEG 4×8; <img> has width/height of the upright original
@Test void shouldKeepJpegBytes_forAsIsJpeg()                // file referenced by the rewritten src has identical bytes
@Test void shouldDeleteImageDirectory_whenLaterStepThrows() // try-with-resources then throw; directory gone
@Test void shouldStripSvgImportsAndStylesheetInstructions_fromCleanedSvg()  // "@import 'x.css'", "@import url(y)", "<?xml-stylesheet href='z'?>" all absent from the written SVG
@Test void shouldNotResolveEntities_whenCleaningSvg()        // SVG with an external DTD and an external general entity pointing at a local ServerSocket URL and at file:///etc/passwd → boxed or cleaned, zero connections, no "root:" in any output
@Test void shouldLogReasonWithoutPath_forBoxedImage()
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** spec §Images rules 3, 6, 7 and the box: OpenPDF `Image.getInstance(bytes)` check for `AS_IS` (failure → convert); conversions via `ImageDecoding.readSubsampled` + `applyOrientation`, JPEG 0.9 for a JPEG source, else PNG (normalise a CMYK or other non-RGB decode to RGB before writing: ImageIO has no PNG writer for CMYK rasters, found in Task 3); SVG cleaning with a namespace-aware DOM from a hardened `DocumentBuilderFactory` (`FEATURE_SECURE_PROCESSING` on; `http://xml.org/sax/features/external-general-entities`, `external-parameter-entities` and `http://apache.org/xml/features/nonvalidating/load-external-dtd` off; `XMLConstants.ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_SCHEMA` set to `""`; `setXIncludeAware(false)`, `setExpandEntityReferences(false)`); remove non-fragment/non-`data:` references, `url()` values, `@import` rules, processing instructions; an SVG that cannot be parsed under these settings is unusable (the box); rewrite `src` to `Path.toUri()`; set `width`/`height` to the original CSS px when downscaled and absent; boxes with `PrintMessages.imageUnavailable()`, sized from attributes or inline style; degradations `IMAGE_DOWNSCALED`, `IMAGE_CONVERTED`, `SVG_REFERENCES_REMOVED`, `IMAGE_UNAVAILABLE`, and `IMAGES_OMITTED_FOR_MEMORY` when boxes come from a missing slot.
- [ ] **Step 4: Run; expect PASS** — `mvn -q test -Dtest='PrintImageService*UnitTest'`.
- [ ] **Step 5: Commit** — `feat(htmlpdf): prepare images (convert, downscale, clean SVG, box)`.

### Task 15: `HtmlPdfRenderService`

**Files:**
- Create: `htmlpdf/service/HtmlPdfRenderService.java`
- Test: `htmlpdf/service/HtmlPdfRenderServiceUnitTest.java` (ladder, logging, limits), `htmlpdf/service/HtmlPdfRenderServiceSlotUnitTest.java`

**Interfaces:**
- Consumes: every service bean above.
- Produces: `public HtmlPdfRenderResult render(HtmlPdfRenderRequest) throws PDFGenerationException`. Constructors: `@Autowired public HtmlPdfRenderService(PrintDocumentComposer, PrintImageService, PrintLayoutService, RenderedPdfValidator, BundledFontFileService)`; package-private one adding `Duration slotWait, long slotImageMemoryBytes, int textOnlyHtmlChars`. Package-private accessor `long slotImageMemoryBytes()` for the slot test.

- [ ] **Step 1: Write the failing tests** — spec §Testing rows `HtmlPdfRenderServiceSlotUnitTest` and the ladder/logging half of `HtmlPdfRenderServiceUnitTest`, plus Review Focus 1 and 4:

```java
@Test void shouldUseSafeMode_whenNormalLayoutFails()        // Mockito spy on PrintLayoutService throws HtmlPdfRenderException for the first call → result has SAFE_MODE; PDF text starts with the safe-mode note
@Test void shouldUseTextOnly_whenSafeModeFails()
@Test void shouldThrowPdfGenerationException_whenAllModesFail() // cause is HtmlPdfRenderException; no cause message contains "FAKE-"
@Test void shouldRetryAndLogError_forLinkageError()        // NoClassDefFoundError from layout in NORMAL → SAFE_MODE result; LogCapture has an ERROR naming NoClassDefFoundError
@Test void shouldReportMissingWebappRoot_withNullBase()    // WEBAPP_ROOT_UNAVAILABLE, valid PDF
@Test void shouldLogOneLine_atMostSeriousLevel()           // degradations {IMAGE_CONVERTED, IMAGE_UNAVAILABLE} → exactly one "htmlpdf-render" event at WARN with source, mode, duration, names; no path, no "FAKE-"
@Test void shouldLogAtDebug_whenNoDegradation()
@Test void shouldGoStraightToTextOnly_whenHtmlOverLimit()   // textOnlyHtmlChars = 1000; 2000-char HTML → TEXT_ONLY, layout spy never called in NORMAL
@Test void shouldIgnoreDataUriPayloads_forSizeLimit()      // Review Focus 4: 1000-char limit; small HTML plus 5000-char data: URI → NORMAL
@Test void shouldPrintNormally_withXmlInvalidCharacters()  // Review Focus 1: "FAKE\u0001 value" → no SAFE_MODE/TEXT_ONLY
@Test void shouldFailAtOnce_forWhitespaceOnlyHtml()
@Test void shouldFailAtOnce_whenDejaVuCannotLoad()         // BundledFontFileService mock throwing PDFGenerationException → render throws it; layout spy never called
@Test void shouldPrintBlankPage_forMarkupWithoutContent()
// HtmlPdfRenderServiceSlotUnitTest (slotWait = 200 ms)
@Test void shouldSplitQuarterOfHeap_betweenTwoSlots()       // production constructor: slot share == Runtime.maxMemory() / 4 / 2
@Test void shouldBoxImagesWithNote_whenNoSlotFree()         // hold both slots from other threads; third render with an image → IMAGES_OMITTED_FOR_MEMORY
@Test void shouldRenderNormally_whenNoSlotAndNoImages()
@Test void shouldReturnSlot_whenAttemptThrows()
@Test void shouldRestoreInterruptFlag_whenWaitInterrupted()
@Test void shouldServeEarlierWaiterFirst_forFairness()
// Slot tests coordinate threads with CountDownLatch/CyclicBarrier, never Thread.sleep.
```

- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** spec §Render pipeline (steps 1–3), §Time and memory bounds, §Resilience and §Observability: fair `Semaphore(RENDER_SLOTS, true)`, `tryAcquire(SLOT_WAIT)`, release in `finally`; the private empty base directory when `baseDirectory == null` (deleted in `finally`); attempts in `NORMAL` → `SAFE` → `TEXT_ONLY`, each re-parsing the HTML with a fresh `EnumSet` seeded with render-level degradations; catch `Exception | LinkageError` per attempt into `HtmlPdfRenderException`; validator failure fails the attempt; size limit counts `html.length()` minus the lengths of `data:` URI payloads (regex on `data:[^"')\s]+`); one log line `htmlpdf-render source=… mode=… durationMs=… degradations=[…]` at the most serious `logLevel()` (DEBUG when none).
- [ ] **Step 4: Run; expect PASS** — `mvn -q test -Dtest='HtmlPdfRenderService*UnitTest'`.
- [ ] **Step 5: Commit** — `feat(htmlpdf): render service with bounded slots and degradation ladder`.

### Task 16: Guardrails

**Files:**
- Modify: `src/main/resources/log4j2.xml` (inside `<Loggers>`, before `<Root>` at line 102, with a comment like the others at 70–75)
- Test: `htmlpdf/HtmlPdfArchitectureUnitTest.java`, `htmlpdf/HtmlPdfSpringWiringUnitTest.java`, `htmlpdf/HtmlPdfLibraryLoggingUnitTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// HtmlPdfArchitectureUnitTest — model on billings/ca/on/assembler/OntarioAssemblerArchitectureUnitTest (Files.walk + readAllLines)
@Test void shouldKeepDtoAndEngineIndependent_ofModule()     // no import of htmlpdf.service/support/engine from dto; none of service/support from engine
@Test void shouldKeepSupportFree_ofService()
@Test void shouldImportNoServletStrutsDaoOrFacade_inModule() // on `import` lines only: jakarta.servlet, org.apache.struts2, .dao., Manager;, ConvertToEdoc, Doc2PDF
@Test void shouldUseNoWildcardImports_inModule()
@Test void shouldExposeOnlyRenderService_publiclyInService() // only HtmlPdfRenderService.java declares "public class"
// HtmlPdfSpringWiringUnitTest
@Test void shouldWireRenderService_fromModuleScanAlone() {
    try (var ctx = new AnnotationConfigApplicationContext("io.github.carlos_emr.carlos.documentManager.htmlpdf")) {
        HtmlPdfRenderService service = ctx.getBean(HtmlPdfRenderService.class);
        assertThat(service.render(simpleRequest()).pdf()).startsWith("%PDF-".getBytes(US_ASCII));
    }
}
// HtmlPdfLibraryLoggingUnitTest
@Test void shouldLogNoDocumentText_whenGlyphMissing()       // capture java.util.logging "org.xhtmlrenderer" with a Handler AND LogCapture.forLogger("org.xhtmlrenderer"); render "FAKE-Patient "; no record contains "FAKE-"
@Test void shouldLeaveLibraryLoggingSwitchesOff_forRenders()           // System.getProperty("xr.renderer.replace-missing-characters") and ("xr.util-logging.loggingEnabled") are null
@Test void shouldPinLibraryLoggerAtWarn_inLog4jConfig()     // parse src/main/resources/log4j2.xml with the JDK XML parser (tests load src/test/resources/log4j2.xml instead): a <Logger name="org.xhtmlrenderer" level="warn"/> exists
```

- [ ] **Step 2: Run; expect failure** (log4j logger absent).
- [ ] **Step 3: Add** `<Logger name="org.xhtmlrenderer" level="warn"/>` with a comment: Flying Saucer can log document text at INFO/DEBUG; keep it at WARN whatever `LOG_VERBOSITY` is (spec §Resource loading and security).
- [ ] **Step 4: Run; expect PASS** — `mvn -q test -Dtest='HtmlPdfArchitectureUnitTest,HtmlPdfSpringWiringUnitTest,HtmlPdfLibraryLoggingUnitTest'`.
- [ ] **Step 5: Commit** — `test(htmlpdf): architecture, wiring and library-logging guardrails`.

### Task 17: Fixtures and end-to-end tests

**Files:**
- Create fixtures under `src/test/resources/htmlpdf/` per spec §Testing "Fixtures": `annual/female.html`, `annual/male.html` (+ copies of `src/main/webapp/form/annualStyle.css` and the `print.css` they link, with `href`s made relative), `forms/all-controls.html`, `hrm/demo-text-as-html.html`, `hrm/table-heavy.html`, `hrm/inline-styles.html`, `hrm/images.html` (+ small images; large ones generated at run time), `email/body.html`, `messenger/letter.html`, `geometry/*.html`, `scripts/*.html`, `edge/*.html`.
- Create: `scripts/htmlpdf/capture-annual-fixtures.js` (Playwright, devcontainer only)
- Test: `htmlpdf/HtmlPdfFixturesUnitTest.java` (the fixture half of spec row `HtmlPdfRenderServiceUnitTest`), `HtmlPdfFormsUnitTest.java`, `HtmlPdfSsrfUnitTest.java`, `HtmlPdfConcurrencyUnitTest.java`, `HtmlPdfMemoryUnitTest.java` (`@Tag("slow") @Tag("htmlpdf")`, not `unit`) with its test-scope `main` class `HtmlPdfMemoryProbe.java`
- Modify: `scripts/check-report-fonts.sh:35` (append `HtmlPdfFixturesUnitTest,HtmlPdfFormsUnitTest,PrintLayoutServiceUnitTest`)

- [ ] **Step 1: Capture the Annual fixtures.** In the devcontainer: `scripts/htmlpdf/capture-annual-fixtures.js` logs in as `carlosdoc`, opens a FAKE- demo patient's eChart, opens **Forms → Annual** (female and male), ticks at least ten boxes, types two-line textareas, saves, reopens the saved form (so `checked` and values are server-rendered attributes), and writes `page.content()`. Rewrite stylesheet `href`s to the relative copies. Confirm no non-FAKE name appears (`grep -i` against the demo patient list).
- [ ] **Step 2: Write the remaining fixtures** by hand as listed (FAKE- names only; the HRM image fixture includes file, `data:`, remote, repeated `src`, `.bmp`, WBMP, corrupt header, corrupt body, the committed `opaque-4x4.webp` from Task 3 (not decoded, shows the box), HEIC bytes, a PDF, SVG with outside references).
- [ ] **Step 3: Write the failing tests** — spec §Testing rows `HtmlPdfRenderServiceUnitTest` (fixture assertions), `HtmlPdfFormsUnitTest`, `HtmlPdfSsrfUnitTest`, `HtmlPdfConcurrencyUnitTest`, `HtmlPdfMemoryUnitTest`. Required assertions include:

```java
@ParameterizedTest @ValueSource(strings = {"annual/female.html","annual/male.html","forms/all-controls.html",
        "hrm/demo-text-as-html.html","email/body.html","messenger/letter.html"})
void shouldRenderRealDocument_withoutWarnOrErrorDegradation(String fixture) {
    HtmlPdfRenderResult r = service.render(requestFor(fixture));
    assertThat(r.degradations()).allMatch(d -> d.logLevel() == Level.INFO);
}
@Test void shouldNeverPrintHiddenFieldNames_onAnnualForm() // "demographic_no", "provider_no", "formCreated", "form_class", "form_link" absent from PDFBox text
@Test void shouldMakeNoRequest_forHostileHtmlInEveryMode() // every vector in the spec's HtmlPdfSsrfUnitTest row, plus an SVG file and an inline <svg> with an external DTD and external entities; local ServerSocket accept count 0; /etc/passwd content absent; forced SAFE and TEXT_ONLY via spies
```
  `HtmlPdfMemoryUnitTest` starts a child JVM: `new ProcessBuilder(javaBin, "-Xmx256m", "-XX:+ExitOnOutOfMemoryError", "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), HtmlPdfMemoryProbe.class.getName(), tempDir)`, where the probe (test scope) generates the scaled images, runs the scenarios on 4 threads and exits 0 only if every document is produced; the test asserts exit code 0 within 5 minutes.
- [ ] **Step 4: Run; expect failures, fix only inside the module** (a fixture that degrades means a module bug or a rule gap; change the module, never the expectation, and record any genuine Flying Saucer limit in the PR).
- [ ] **Step 5: Run; expect PASS** — `mvn -q test -Dgroups=htmlpdf` and `REPORT_FONT_TESTS=HtmlPdfFixturesUnitTest,HtmlPdfFormsUnitTest,PrintLayoutServiceUnitTest bash scripts/check-report-fonts.sh`.
- [ ] **Step 6: Commit** — `test(htmlpdf): document fixtures and end-to-end, SSRF, concurrency and memory tests`.

### Task 18: `ConvertToEdoc` path and image fixes

**Files:**
- Modify: `documentManager/ConvertToEdoc.java` — `translateResourcePaths` (617–638), `translateLinkPaths`/`translateJavascriptPaths`/`translateImagePaths` (644–664), `translatePaths` (900–916), `validateResourcePaths` (1007–1020), `appendRewrittenCssUrl` (714), `setParameterInjectedCss` (600–610)
- Test: `src/test/java/.../documentManager/ConvertToEdocUnitTest.java`

**Interfaces:**
- Produces: translated paths emitted as `Path.toUri().toString()`; unusable `<img>` keep their element with `data-carlos-image-unavailable` and no `src`.

- [ ] **Step 1: Write the failing tests** (CarlosUnitTestBase as today; root set through `tidyDocument(html, root)`)

```java
@Test void shouldTranslateEveryImage_whenSrcRepeats()        // three <img src="stamp.png"> → all three src start with "file:/" 
@Test void shouldEmitEncodedFileUris_forTranslatedPaths()     // root dir "FAKE dir é" → src contains "%20" and "%C3%A9"
@Test void shouldMarkUnresolvedImage_insteadOfRemoving()      // <img src="missing.png"> and <img src="scan.bmp"> remain with data-carlos-image-unavailable and no src
@Test void shouldMarkRemoteImage_insteadOfRemoving()          // http: src
@Test void shouldKeepRemovingUnresolvedLinksAndScripts_whenTidying()       // unchanged behaviour for <link>/<script>
```
  Update `shouldStripUnresolvedAbsoluteFilePaths_whenTidyingDocument` (145) and `shouldRemoveUnresolvedTraversalShapedResourceElements_whenParsingDocument` (184): the `<img>` now remains as an unavailable marker; assert no `src` and no path text.
- [ ] **Step 2: Run; expect failure** — `mvn -q test -Dtest=ConvertToEdocUnitTest`.
- [ ] **Step 3: Implement** — replace both `HashMap<List<String>, Element>` maps with `LinkedHashMap<Element, List<String>>` and adjust the loops; for `img` elements, where today `element.remove()` runs (905, 911, 1017), call `element.removeAttr("src").attr("data-carlos-image-unavailable", "")`; emit `Path.of(resolved).toUri()` in `src`/`href`/CSS `url()`/the stylesheet link (CSS still through `SafeEncode.forCssString`).
- [ ] **Step 4: Run; expect PASS**.
- [ ] **Step 5: Commit** — `fix(documents): keep every image visible and use encoded file URIs in ConvertToEdoc`.

### Task 19: Facades render through the service

**Files:**
- Modify: `documentManager/ConvertToEdoc.java` — `execute` (289–314), `renderPDF` (392–412), `fallbackRender` (432–447) → `renderWithService`, every `execute` caller (`from(EFormData)` 127, `from(FormTransportContainer)` 199, `saveAsTempPDF` 237/251/262/276), class JavaDoc 90–92 and 416–431; delete `prepareDocumentForFlyingSaucer` (452) and `normalizeHtmlCommentsForXml` (477)
- Modify: `util/Doc2PDF.java` — `renderWithFlyingSaucer` (137–172), `GetPDFBin` (545), `PrintPDFFromHTMLString` (627), JavaDoc 126 and 647
- Modify: `htmlpdf/engine/LocalOnlyUserAgent.java` — delete the deprecated `createRestrictedRenderer()` and its implicit roots
- Delete: `documentManager/ReplacedElementFactoryImpl.java`
- Modify: `src/test/resources/test-context-full.xml` (after the `@Repository` scan at 544–546: `<context:component-scan base-package="io.github.carlos_emr.carlos.documentManager.htmlpdf"/>`)
- Test: `ConvertToEdocUnitTest.java` (remove the `--` test at 93, already moved in Task 6), new `ConvertToEdocRenderUnitTest.java`, new `ConvertToEdocRenderIntegrationTest.java` (`@Tag("integration")`), `Doc2PDFIntegrationTest.java`

**Interfaces:**
- Consumes: `HtmlPdfRenderService` via `SpringUtils.getBean(HtmlPdfRenderService.class)` at call time.
- Produces: `private static Path execute(String html, String filename, HtmlPdfRenderSource source)` (`saveAsTempPDF(String, String)`, which has no production caller and is deleted in PR 2, passes `FORM`); package-private `static HtmlPdfRenderRequest buildRenderRequest(String tidiedHtml, HtmlPdfRenderSource source)`; package-private `static Path webappRoot()` (realPath, else `SpringUtils.getBean(ServletContext.class).getRealPath("/")`, else `null`; sets `realPath` when it was null).

- [ ] **Step 1: Write the failing tests**

```java
// ConvertToEdocRenderUnitTest extends CarlosUnitTestBase; registerMock(HtmlPdfRenderService.class, service); registerMock(NioFileManager.class, nio)
@Test void shouldBuildRequest_withWebappRootAndAllowedDirectories() // base = root; allowed = [root, eForm image dir, java.io.tmpdir, catalina.base/work if set]
@Test void shouldPassNullBase_whenNoWebappRoot()                    // realPath null, ServletContext bean returns null → baseDirectory null
@Test void shouldUseSameRoot_forTranslationAndRendering()           // realPath null; ServletContext root R → realPath becomes R and request base is R
@Test void shouldLabelSources_perEntryPoint()                       // FTC → FORM, EmailData → EMAIL, EFormData → HRM
@Test void shouldReturnNullAndLogNoHtml_whenRenderFails()           // service throws PDFGenerationException; LogCapture has fixed text + sanitized file name + "PDFGenerationException"; no "FAKE-"
@Test void shouldReturnNull_forBlankHtml()                          // "   " → null, no exception escapes (tidyDocument now inside the try)
@Test void shouldReturnNull_whenTempSaveFails()                     // NioFileManager throws IOException → null, no retry (verify called once)
@Test void shouldUseServiceAsFallback_whenWkhtmltopdfFails()        // mockConstruction(InternalEDocConverter) throws → service.render called once with source FORM
// ConvertToEdocRenderIntegrationTest (extends CarlosTestBase, real HtmlPdfRenderService from test-context-full.xml, mockConstruction(InternalEDocConverter) throwing)
@Test void shouldLoadWebappResources_whenWorkingDirectoryIsElsewhere() // webapp root in a @TempDir with a linked stylesheet (a distinctive font size), a CSS url() background image and a pdfMediaStylesheet hidden input; user.dir is never under the root → PDF text present at that font size (PDFBox TextPosition), no IMAGE_UNAVAILABLE
// Doc2PDFIntegrationTest: existing tests keep passing through the real service; add
@Test void shouldReturnNull_whenRenderFails()                       // whitespace-only docText → parseString2Bin returns null
```
- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** spec §Errors rows for `ConvertToEdoc.execute`, `Doc2PDF`, and §Callers' requests: `execute` puts `tidyDocument`, rendering and saving in one `try`, catches `PDFGenerationException | DocumentException | IOException | RuntimeException`, logs `"HTML could not be converted to PDF for {}: {}"` with `LogSafe.sanitize(filename)` and `e.getClass().getSimpleName()`; `renderPDF` keeps wkhtmltopdf first and replaces line 399's message log with the exception class; `renderWithService` writes `result.writeTo(os)`; `Doc2PDF.renderWithFlyingSaucer(String html, OutputStream os, String baseUrl) throws PDFGenerationException, IOException` builds a `MESSENGER` request whose base is the path after the `file://` prefix of `baseUrl` (or `null`) and allowed directories are that root, `java.io.tmpdir` and `catalina.base/work` if set. Delete the listed dead code and `ReplacedElementFactoryImpl`.
- [ ] **Step 4: Run; expect PASS** — `mvn -q test -Dtest='ConvertToEdoc*,Doc2PDFIntegrationTest,LocalOnlyUserAgentUnitTest,MsgAttachPDF2ActionUnitTest,FormsManagerImplUnitTest,FaxDocumentManagerImplUnitTest,EformDataManagerImplCreatePdfUnitTest'`.
- [ ] **Step 5: Commit** — `feat(documents): render ConvertToEdoc fallback and Doc2PDF through HtmlPdfRenderService`.

### Task 20: Caller error paths (HRM, email)

**Files:**
- Modify: `hospitalReportManager/HRMPDFCreator.java` — `printPdf()` (128; HTML branch 178–183; JavaDoc 122–127 left for PR 2)
- Modify: `hospitalReportManager/HRMUtil.java` — `renderHRM` (361–375)
- Modify: `hospitalReportManager/PrintHRMReport2Action.java` — catch at 133
- Modify: `managers/EmailManager.java` — `createMessageAttachment` (510–519)
- Test: `HRMPdfUnicodeUnitTest.java`, `PrintHRMReport2ActionUnitTest.java` (compile only), new `HRMPDFCreatorHtmlFailureUnitTest.java`, new `HRMUtilRenderFailureUnitTest.java`, new `EmailManagerEncryptionUnitTest.java`

**Interfaces:**
- Produces: `public void printPdf() throws PDFGenerationException`; `private EmailAttachment createMessageAttachment(EmailData) throws EmailSendingException`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void shouldThrowPdfGenerationException_whenHtmlConversionReturnsNull() // mockStatic(ConvertToEdoc).saveAsTempPDF(any(EFormData)) → null; HTML HRM → PDFGenerationException, no NPE
@Test void shouldDeleteTempPdf_afterCopyingHtmlReport()
@Test void shouldNameReport_whenRenderHrmFails()                              // mockConstruction(HRMPDFCreator) printPdf throws → renderHRM throws PDFGenerationException with the existing "HRM [...]" message shape
@Test void shouldSendExistingError_whenPrintPdfThrowsPdfGenerationException()   // PrintHRMReport2Action → sendError(500, "Unable to generate HRM PDF"), returns NONE
@Test void shouldFailEmail_whenMessagePdfIsNull()                              // EmailManager.sendEmail with encrypted body, saveAsTempPDF(EmailData) → null → status FAILED, no NPE
```
- [ ] **Step 2: Run; expect failure**.
- [ ] **Step 3: Implement** spec §Errors rows: `printPdf` throws `new PDFGenerationException("HTML HRM report could not be converted to PDF")` on `null` and deletes the temp file in `finally`; `renderHRM` adds `catch (PDFGenerationException e)` rethrowing with the same message shape as its `IOException` catch (371); `PrintHRMReport2Action` catches `IOException | PDFGenerationException | RuntimeException`; `createMessageAttachment` throws `new EmailSendingException("Encrypted message could not be converted to PDF")` on `null`. `EctConsultationFormRequestPrintAction22Action` needs no change (its `catch (PDFGenerationException)` at 343 already covers line 298).
- [ ] **Step 4: Run; expect PASS** — `mvn -q test -Dtest='HRM*UnitTest,PrintHRMReport2ActionUnitTest,EmailManager*UnitTest,EctConsultationFormRequestPrintAction22Action*'`.
- [ ] **Step 5: Commit** — `fix(hrm,email): surface failed HTML-to-PDF conversions instead of NullPointerException`.

### Task 21: Comparison with wkhtmltopdf and the performance budget

**Files:**
- Create: `src/test/java/io/github/carlos_emr/carlos/documentManager/htmlpdf/tools/HtmlPdfComparisonMain.java` (test scope, not a test), `scripts/htmlpdf/compare-with-wkhtmltopdf.sh`
- Modify: `HtmlPdfRenderService.java` (`TEXT_ONLY_HTML_CHARS` final value and its comment)

- [ ] **Step 1: Write the tool.** For each fixture: run `ConvertToEdoc.tidyDocument` (fixture root as `realPath`), render with `InternalEDocConverter` (process env `LANG=C.UTF-8`, `FONTCONFIG_FILE` pointing at a generated fonts.conf listing only `/usr/share/fonts/truetype/dejavu`) and with `HtmlPdfRenderService`; write both PDFs, 100-dpi PNGs (PDFBox `PDFRenderer`) side by side, and a CSV row: pages, page sizes, left/top content edges, wkhtmltopdf ms, Flying Saucer warm ms (median of 5). The script builds the test classpath with `mvn -q dependency:build-classpath -Dmdep.outputFile=target/htmlpdf-cp.txt` and writes to `target/htmlpdf-compare/`.
- [ ] **Step 2: Run it** — `bash scripts/htmlpdf/compare-with-wkhtmltopdf.sh`. Check every fixture against spec §Testing "One-off comparison" acceptance. Expected: all pass; any difference listed for the PR body.
- [ ] **Step 3: Measure the size limit** — render generated tables of growing size (500 to 20 000 rows) in `NORMAL` mode on the devcontainer and record layout time; set `TEXT_ONLY_HTML_CHARS` to the HTML length at which a normal-mode layout takes 30 s (rounded down to a million), with a comment giving the measurement and machine. Confirm warm p95 < 2 s on the real-document fixtures and the worst case (10 s wait + three attempts) < 60 s.
- [ ] **Step 4: Run the module suite** — `mvn -q test -Dgroups=htmlpdf` → PASS.
- [ ] **Step 5: Commit** — `test(htmlpdf): wkhtmltopdf comparison tool and measured text-only limit`. Attach the PNGs and CSV to PR 1, not to the repository.

### Task 22: Documentation

**Files:**
- Create: `docs/html-pdf-rendering.md`
- Modify: `docs/pdf-font-policy.md` (sections `## Rules` line 14, `## Mechanisms` 38, `## Tests` 79), `CLAUDE.md` (`## PDF and Report Fonts`, lines 98–109)

- [ ] **Step 1: Write `docs/html-pdf-rendering.md`** in the style of `docs/billing-ontario-module.md`: purpose, package map and dependency rules, layering rules, the pipeline (steps a–m), time and memory bounds, the resilience ladder, degradations with their log levels and what an operator does for each, printed text and its keys, how to add a markup rule (support utility + composer step + test), the facades and their contracts, the known limitations (spec §Known limitations), the rollback note from spec §Delivery.
- [ ] **Step 2: Update `docs/pdf-font-policy.md`** — the HTML engine as a third engine: every family → DejaVu Sans, fallback-font spans, U+FFFD, the tests (`BundledFontResolverUnitTest`, `FallbackFontRunsUnitTest`, the `check-report-fonts.sh` entries).
- [ ] **Step 3: Add one line to `CLAUDE.md` "PDF and Report Fonts"**: non-scripted HTML-to-PDF goes through `HtmlPdfRenderService` (never a new `ITextRenderer`); see `docs/html-pdf-rendering.md`.
- [ ] **Step 4: Commit** — `docs(htmlpdf): module guide, font policy and CLAUDE.md pointer`.

### Task 23: PR 1 verification and pull request

- [ ] **Step 1: Full checks** — `mvn -q test -Dgroups=unit` (PASS), `mvn -q test -Dgroups=htmlpdf` (PASS), `bash scripts/check-report-fonts.sh` (PASS), `./scripts/check-i18n-properties.sh` (no new missing keys), `bash scripts/lint/check-encoder-null-safety.sh` (PASS), `mvn -q -DskipTests package` (WAR builds).
- [ ] **Step 2: Manual checks on Tomcat** — every item in spec §Testing "Manual checks", in the devcontainer and with the webapp outside the working directory. Record results in the PR body.
- [ ] **Step 3: Self-review the diff** against spec §Goals and Global Constraints; grep the diff for `getMessage()` in new log calls, `printStackTrace`, `System.out`, `FAKE` outside fixtures, and absolute developer paths.
- [ ] **Step 4: Push and open PR 1** to `release/2026.08` — title `feat(documents): Flying Saucer HTML-to-PDF engine (1/2)`. Body follows `.github/PULL_REQUEST_TEMPLATE.md`, states the release-branch case from spec §Delivery, links the spec and this plan, lists deliberate differences (spec §Deliberate differences), attaches the comparison PNGs/CSV and the measured text-only limit, and ends with the "Generated with Claude Code" line.

---

# Phase 2 — PR 2: Switch and removal

Start after PR 1 merges: `git fetch origin release/2026.08 && git checkout -b claude/flying-saucer-html-pdf-removal origin/release/2026.08`.

### Task 24: `ConvertToEdoc` uses only the service; delete wkhtmltopdf Java and dead entry points

**Files:**
- Modify: `documentManager/ConvertToEdoc.java` — `renderPDF` calls `renderWithService` only; delete `from(EFormData)` (127), `from(FormTransportContainer)` (199) and its JavaDoc example, `saveAsTempPDF(String, String)` (251), `DEFAULT_WKHTMLTOPDF_COMMAND`/`ARGS` (113–114), the wkhtmltopdf comments and JavaDoc (83–84, 383); class JavaDoc describes the single engine
- Delete: `documentManager/InternalEDocConverter.java`, `documentManager/EDocConverterInterface.java`
- Modify: `managers/EformDataManager.java:57` + `EformDataManagerImpl.java:147-171` (delete `saveEformDataAsEDoc`), `managers/FormsManager.java:74` + `FormsManagerImpl.java:215-235` (delete `saveFormDataAsEDoc`; 238–241 is the next method's JavaDoc, keep it)
- Modify: `hospitalReportManager/HRMPDFCreator.java:122-127` (JavaDoc: one engine)
- Test: delete `FormsManagerUnitTest` `@Nested SaveFormDataAsEDocSecurity` (~308–323); rewrite `ConvertToEdocIntegrationTest` (the `MockedStatic`/`MockedConstruction` block at 86–104 → `saveAsTempPDF(EFormData)` through the real service, no `MockedConstruction`; keep the `from(eForm, pdfPath)` case at 105–107)

- [ ] **Step 1: Update the tests first** (integration test asserts the PDF text through PDFBox and `moduleId`/page count as today) and run them → compile failures expected where methods are deleted only after Step 2.
- [ ] **Step 2: Delete and simplify** as listed; `grep -rn "InternalEDocConverter\|EDocConverterInterface\|saveEformDataAsEDoc\|saveFormDataAsEDoc\|wkhtmltopdf" src/main src/test` → no matches except historical docs.
- [ ] **Step 3: Run** — `mvn -q test -Dtest='ConvertToEdoc*,FormsManager*UnitTest,EformDataManager*UnitTest,FaxDocumentManagerImplUnitTest,HRM*UnitTest'` → PASS.
- [ ] **Step 4: Commit** — `refactor(documents): remove wkhtmltopdf conversion and unused EDoc entry points`.

### Task 25: Remove packaging, settings and the comparison tool

**Files:**
- Modify: `pom.xml` — delete `ultrabuk-htmltopdf-java` (915–919), `openpdf-html` and its comment (905–913), `jna.version` (187–188) and the dependencyManagement pin (332–337); update the vendored-artifacts comment (212–224) and the lock-plugin comment (2013–2016)
- Delete: `local_repo/com/github/openosp/ultrabuk-htmltopdf-java/`, `local_repo/com/github/librepdf/openpdf/openpdf-html/`
- Modify: `dependencies-lock.json` (regenerate)
- Modify: `debian/control` (delete 75–81: comment and `libxrender1`)
- Modify: `src/main/resources/carlos.properties` (delete 753–757), `.devcontainer/development/config/shared/volumes/carlos.properties` (delete 878–884)
- Modify: `utils/readme.txt` (delete 4–10), `docs/pdf-dependency-consolidation-plan.md` (rows at 35, 37; lines 116, 123, 142–144, 546: mark ultrabuk and openpdf-html removed, give `LocalOnlyUserAgent`'s package)
- Delete: `scripts/htmlpdf/compare-with-wkhtmltopdf.sh`, `src/test/java/.../htmlpdf/tools/HtmlPdfComparisonMain.java`

- [ ] **Step 1: Edit `pom.xml` and delete the vendored directories.**
- [ ] **Step 2: Regenerate the lock** — `mvn se.vandmo:dependency-lock-maven-plugin:lock`; `git diff dependencies-lock.json` shows only `ultrabuk-htmltopdf-java`, `openpdf-html`, `neko-htmlunit` (and its own transitive entries) and `jna` removed. `grep -rn "com.sun.jna" src` → none.
- [ ] **Step 3: Edit packaging, settings and docs** as listed.
- [ ] **Step 4: Verify** — `mvn -q -DskipTests package` (PASS), `mvn -q test -Dgroups=unit` (PASS), `python3 -m unittest discover -s debian/assets/tests` (PASS, as `.github/workflows/debian-python-tests.yml` runs it), `ls target/*/WEB-INF/lib | grep -E "ultrabuk|jna|openpdf-html|neko"` → nothing.
- [ ] **Step 5: Commit** — `build: remove wkhtmltopdf, JNA, openpdf-html and libxrender1`.

### Task 26: CI workflow references

**Files:**
- Modify: `.github/workflows/dependency-submission.yml` (24; 97–101 comment; 102 `rm -rf` keeps only `~/.m2/repository/com/github/librepdf`), `.github/actions/dev-container/action.yml` (142 comment; 148–149 keep only `.m2-cache/repository/com/github/librepdf`)

- [ ] **Step 1: Make the edits.** If `.claude/settings.json` denies the write, produce the unified diff with `git diff --no-index` against copies in the scratchpad and paste it into the PR 2 body under "Maintainer patch (Claude may not write under .github/)".
- [ ] **Step 2: Commit** (if written) — `ci: stop evicting the removed ultrabuk artifacts`.

### Task 27: PR 2 verification and pull request

- [ ] **Step 1: Full checks** — as Task 23 Step 1, plus `git grep -n -i "wkhtml\|ultrabuk\|libxrender" -- ':!docs/superpowers' ':!docs/pdf-migration-functional-equivalence-findings.md' ':!docs/static'` → only the follow-up notes the spec keeps (devcontainer Dockerfile, OSCAR 19 manifest).
- [ ] **Step 2: Manual checks** — repeat spec §Testing "Manual checks" on the PR 2 WAR; on a Debian test install, `apt-get install` the built `carlos-emr` package and confirm `libxrender1` is no longer pulled in and a form fax renders.
- [ ] **Step 3: Push and open PR 2** to `release/2026.08` — title `feat(documents): switch to Flying Saucer and remove wkhtmltopdf (2/2)`; body per the template, states that PR 2 contains only the switch and deletions and can be reverted as a unit (spec §Delivery "Rollback"), includes any maintainer patch from Task 26, links the spec and plan, ends with the "Generated with Claude Code" line.
