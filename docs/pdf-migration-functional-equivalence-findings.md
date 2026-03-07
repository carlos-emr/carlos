# PDF Migration Functional Equivalence Findings

**PR**: #536 — PDF dependency consolidation (iText 5 AGPL → OpenPDF 3.0.2 LGPL + Flying Saucer 10.0.7)
**Branch**: `claude/pdf-dependency-consolidation-xaJN3`
**Date**: 2026-03-06

---

## Summary Table

| # | Code Path | Risk | Status | Notes |
|---|-----------|------|--------|-------|
| H1 | Doc2PDF — Messenger letter generation | HIGH | **Different** | Engine change: XMLWorkerHelper → Flying Saucer. Layout/CSS differences possible. SSRF guard added. Several behavioral improvements. |
| H2 | ConvertToEdoc — HRM document conversion | HIGH | **Equivalent + Improved** | Already used Flying Saucer on develop as fallback. Only change: `new ITextRenderer()` → `LocalOnlyUserAgent.createRestrictedRenderer()` (adds SSRF protection). |
| H3 | ImagePDFCreator — TIFF→PDF for consultations | HIGH | **Different** | Codec change: iText TiffImage → ImageIO+TwelveMonkeys. Functional but with behavioral differences (see details). |
| H4 | LocalOnlyUserAgent — SSRF prevention | HIGH | **Improved (New)** | New security layer. No develop equivalent. Blocks http/https/ftp, allows file:/data:/relative. |
| M1 | PdfWriterFactory — centralized writer creation | MEDIUM | **Equivalent** | Develop iText overload had identical stamper chain. PR consolidates to single OpenPDF overload. |
| M2 | FooterSupport / PageNumberStamper / PromoTextStamper | MEDIUM | **Equivalent** | Same rendering logic, same Y-offsets (30/20/10). Import swap only + made abstract. |
| M3 | FontSettings — immutable font config | MEDIUM | **Improved** | Made truly immutable (final fields, validation). Same values. Removed no-arg constructor. |
| M4 | ReplacedElementFactoryImpl — CSS background-image | MEDIUM | **Equivalent** | Import swap (`com.lowagie.*` → `org.openpdf.*`). Same image loading logic. |
| L1 | EFormPDFServlet / FrmPDFServlet | LOW | **Equivalent** | Pure import swap `com.itextpdf.*` → `org.openpdf.*`. Same API signatures. |
| L2 | IncomingDocUtil — page rotation/extraction | LOW | **Equivalent** | Pure import swap. Same PdfReader/PdfStamper API. |
| L3 | ConcatPDF — PDF concatenation | LOW | **Equivalent** | Already used PDFBox on develop. No PDF library change. Minor logging improvements only. |
| L4 | ConsultationPDFCreator | LOW | **Equivalent** | Import swap. Keeps its own EndPage inner class. No PdfWriterFactory. |
| L5 | OscarChartPrinter | LOW | **Equivalent** | Import swap. Keeps its own EndPage inner class. No PdfWriterFactory. |
| L6 | PdfRecordPrinter | LOW | **Equivalent** | Import swap. Already used PdfWriterFactory on develop (iText overload). Now uses OpenPDF overload with identical stamper chain. |

---

## Detailed Findings

### H1: Doc2PDF — Messenger Letter Generation (Different)

**Entry point**: `ManageDocument2Action.parseJSP2PDF()` → `Doc2PDF.parseJSP2PDF()`

**Develop pipeline**:
```
HTML string → Document(PageSize.A4, 36, 36, 36, 36)
            → PdfWriter.getInstance(document, baos)
            → XMLWorkerHelper.getInstance().parseXHtml(writer, document, inputStream)
            → document.close()
```

**PR pipeline**:
```
HTML string → Jsoup.parse() → configureJsoupForXhtml()
            → strip <script>, add alt="" to <img>, add type="text" to <input>
            → LocalOnlyUserAgent.createRestrictedRenderer()
            → SharedContext: print=true, interactive=false, smoothingThreshold=0
            → renderer.setDocumentFromString(html, baseUrl)
            → renderer.layout() → renderer.createPDF(os, true)
```

**Behavioral differences**:

1. **Page size**: Develop explicitly set `PageSize.A4` with 36pt margins. PR uses Flying Saucer's default page size (also A4 per CSS `@page` defaults), but margins are controlled by CSS rather than hard-coded. If the Messenger templates don't specify CSS page margins, the output margins will differ.
   - **Classification**: **Different** — needs manual testing with Messenger templates

2. **CSS support**: XMLWorkerHelper supports a limited CSS subset (basic properties only, no CSS3). Flying Saucer supports significantly more CSS 2.1 (including `@page`, `page-break-*`, floats, positioning). Templates that relied on XMLWorkerHelper's quirks may render differently.
   - **Classification**: **Different** — may be better or worse depending on template CSS

3. **HTML preprocessing**: PR adds three new Jsoup cleanups before rendering:
   - `doc.select("script").remove()` — removes `<script>` tags
   - `doc.select("img:not([alt])").attr("alt", "")` — adds missing alt attributes
   - `doc.select("input:not([type])").attr("type", "text")` — adds missing type attributes
   - **Classification**: **Improved** — prevents Flying Saucer rendering errors

4. **Resource resolution (SSRF)**: Develop used `AddAbsoluteTag()` to rewrite `src` attributes to absolute `http://` URLs pointing to the local server. PR strips leading slashes from `src` and resolves relative to a `file://` base URL. Resources load from disk instead of HTTP.
   - **Classification**: **Improved** — eliminates HTTP round-trips and SSRF vectors

5. **`AddAbsoluteTag()` behavior change**: On develop, this method constructed `http://hostname:port/context/` URLs and prepended them to `src` attributes. On PR, it **only strips leading slashes** — the request/URI parameters are unused. This means images that were previously loaded via HTTP now resolve as local files via the `file://` base URL.
   - **Classification**: **Different** — images that aren't on the local filesystem may break. However, Messenger letter JSP templates are server-generated, so their images should be in the webapp directory.

6. **Error handling**: Develop silently logged and continued on error. PR adds `sendErrorIfPossible(response)` to return HTTP 500 on failure, and adds null-check on `GetInputFromURI` return.
   - **Classification**: **Improved** — callers now get proper error responses

7. **Connection leak fix**: `GetInputFromURI()` now wraps the input stream with a `FilterInputStream` that calls `conn.disconnect()` on close, and falls back to disconnect on error.
   - **Classification**: **Improved** — fixes connection pool leak

8. **Removed methods**: `topdf()` and `HTMLDOC()` (external htmldoc command) were removed. No callers exist in the codebase.
   - **Classification**: **Equivalent** — dead code removal

9. **`GetPDFBin()` and `PrintPDFFromHTMLString()` signature change**: Now take an additional `String baseUrl` parameter. All callers updated to pass `getFileBaseUrl(request)`.
   - **Classification**: **Equivalent** — internal API change, all callers updated

**Recommendation**: Manual testing required with actual Messenger letter templates. The most likely visual difference is page margins and CSS interpretation. Font rendering should be similar (both use the system's Helvetica equivalent).

---

### H2: ConvertToEdoc — HRM Document Conversion (Equivalent + Improved)

**Entry point**: `ManageDocument2Action.convertToEdoc()` → `ConvertToEdoc.renderPDF()`

**Key finding**: ConvertToEdoc already used Flying Saucer as a fallback renderer on develop. The primary conversion path uses `InternalEDocConverter` (wkhtmltopdf). The PR change is minimal:

**Develop `fallbackRender()`** (line 340):
```java
ITextRenderer renderer = new ITextRenderer();
SharedContext sharedContext = renderer.getSharedContext();
sharedContext.setPrint(true);
sharedContext.setInteractive(false);
sharedContext.setReplacedElementFactory(new ReplacedElementFactoryImpl());
sharedContext.getTextRenderer().setSmoothingThreshold(0);
renderer.setDocumentFromString(doc.outerHtml(), null);
renderer.layout();
renderer.createPDF(os, true);
```

**PR `fallbackRender()`** (line 396):
```java
ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer();
SharedContext sharedContext = renderer.getSharedContext();
sharedContext.setPrint(true);
sharedContext.setInteractive(false);
sharedContext.setReplacedElementFactory(new ReplacedElementFactoryImpl());
sharedContext.getTextRenderer().setSmoothingThreshold(0);
renderer.setDocumentFromString(doc.outerHtml(), null);
renderer.layout();
renderer.createPDF(os, true);
```

**Single difference**: `new ITextRenderer()` → `LocalOnlyUserAgent.createRestrictedRenderer()`

This adds SSRF protection without changing any rendering behavior. The same CSS handling, same image factory, same document preparation (`prepareDocumentForFlyingSaucer()`), same output settings.

The `com.lowagie.text.DocumentException` catch on develop was needed because Flying Saucer's OpenPDF backend threw `com.lowagie.text.DocumentException` (the old OpenPDF package). On the PR branch, OpenPDF 3.0 throws `org.openpdf.text.DocumentException` which Flying Saucer 10.0.7 handles internally, so the manual catch is no longer needed.

Also: the `DocumentException` import changed from `com.itextpdf.text.DocumentException` to `org.openpdf.text.DocumentException` at the top of the file.

**Classification**: **Equivalent** rendering + **Improved** security (SSRF protection on the fallback path)

---

### H3: ImagePDFCreator — TIFF→PDF for Consultation Attachments (Different)

**Entry point**: `EctConsultationFormRequestAction` → `ImagePDFCreator.printPdf()`

**Develop pipeline (TIFF)**:
```java
Image image = Image.getInstance(imagePath);
int type = image.getOriginalType();
if (type == Image.ORIGINAL_TIFF) {
    RandomAccessFileOrArray ra = new RandomAccessFileOrArray(imagePath);
    int comps = TiffImage.getNumberOfPages(ra);
    for (int c = 0; c < comps; ++c) {
        Image img = TiffImage.getTiffImage(ra, c + 1);  // 1-indexed
        // ... scale, position, add to PDF
    }
    ra.close();
}
```

**PR pipeline (TIFF)**:
```java
String lowerPath = imageFile.getName().toLowerCase(Locale.ROOT);
boolean isTiff = lowerPath.endsWith(".tif") || lowerPath.endsWith(".tiff");
if (isTiff) {
    try (ImageInputStream iis = ImageIO.createImageInputStream(imageFile)) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        ImageReader reader = readers.next();
        reader.setInput(iis);
        int comps = reader.getNumImages(true);
        for (int c = 0; c < comps; c++) {  // 0-indexed
            BufferedImage bufferedImage = reader.read(c);
            Image img = Image.getInstance(bufferedImage, null);
            // ... scale, position, add to PDF
        }
    }
}
```

**Behavioral differences**:

1. **TIFF detection method**: Develop detected TIFF via `Image.getInstance()` → `image.getOriginalType() == Image.ORIGINAL_TIFF`. PR detects by file extension (`.tif`/`.tiff`, case-insensitive). Both approaches are standard. The extension check is slightly less robust for misnamed files, but TIFF files in medical contexts always use standard extensions.
   - **Classification**: **Equivalent** — standard medical TIFF files always use `.tif`/`.tiff`

2. **Page indexing**: Develop used 1-indexed (`TiffImage.getTiffImage(ra, c + 1)`). PR uses 0-indexed (`reader.read(c)`). Both correctly iterate all pages.
   - **Classification**: **Equivalent**

3. **TIFF compression support**: iText's `TiffImage` had built-in decoders for CCITT Group 3/4, LZW, PackBits, JPEG-in-TIFF. TwelveMonkeys 3.13.0 also supports all these plus Deflate, ZLib, and Old-style JPEG. Equal or better.
   - **Classification**: **Equivalent to Improved**

4. **Image quality / DPI**: `TiffImage.getTiffImage()` preserved the original TIFF resolution metadata and passed it directly to the PDF image object. `Image.getInstance(BufferedImage, null)` converts through Java's `BufferedImage` which defaults to 72 DPI unless the TIFF reader preserves DPI in the image metadata. This is the **most significant potential difference** — scanned medical documents are typically 200-600 DPI, and if the DPI metadata is lost during `BufferedImage` conversion, images could render at incorrect sizes in the PDF.
   - **Classification**: **Different** — needs testing with real medical TIFF files. The `scaleToFit(500, 700)` call may mask this issue for oversized images, but images smaller than 500x700pt at 72 DPI may appear larger than they should.

5. **Adjust-size mode removed**: Develop had an `adjustSize` boolean (always `false`) that would set page size to match the image and use absolute position (0,0). PR always uses the scale-to-fit path. Since `adjustSize` was never set to `true`, this is a dead-code removal.
   - **Classification**: **Equivalent** — dead code path

6. **Non-TIFF images**: Develop used `Image.getInstance(imagePath)` from iText. PR uses `Image.getInstance(imageFile.getAbsolutePath())` from OpenPDF. Same API, same behavior.
   - **Classification**: **Equivalent**

7. **Path validation added**: PR adds `PathValidationUtils.validateExistingPath()` to prevent path traversal attacks. Develop had no path validation.
   - **Classification**: **Improved** — security hardening

8. **Resource cleanup**: PR uses try-with-resources for `ImageInputStream` and explicit `reader.dispose()` in a finally block. Develop relied on manual `ra.close()` with no error handling.
   - **Classification**: **Improved**

9. **Error handling**: PR adds validation (null imagePath, missing DOCUMENT_DIR, no ImageReader found, zero pages). Develop would throw NPE or opaque iText exceptions.
   - **Classification**: **Improved**

10. **Creator tag**: Changed from `"OSCAR"` to `"CARLOS EMR"`.
    - **Classification**: **Improved** — project branding

11. **Missing null check on TIFF page read**: Develop had `if (img != null)` guard around each TIFF page (line 92). PR does not check if `reader.read(c)` returns null — if a TIFF page is corrupted/unreadable, this will throw NPE at `Image.getInstance(bufferedImage, null)`.
    - **Classification**: **Regression (minor)** — should add null check for defensive programming

**Recommendation**:
- Add null check: `if (bufferedImage == null) { logger.warn("Skipping unreadable TIFF page {}", c + 1); continue; }`
- **Test with real CCITT Group 4 TIFF files** (typical medical faxes/scans) to verify:
  - Image dimensions render correctly in the PDF
  - Multi-page TIFFs produce the correct number of pages
  - DPI is preserved (compare physical size of rendered images)

---

### H4: LocalOnlyUserAgent — SSRF Prevention (New, Improved)

**No develop equivalent** — entirely new class.

**What it does**:
- Extends Flying Saucer's `ITextUserAgent`
- `resolveAndOpenStream()`: Blocks `http:`, `https:`, `ftp:`, `jar:`, `//` (protocol-relative). Allows `data:`, `file:`, relative paths.
- `openStream()`: Enforces path containment for `file:` URIs — only allows webapp root, `java.io.tmpdir`, and Catalina work directory.
- `setBaseURL()`: Rejects non-`file:` base URLs to prevent relative URI resolution against HTTP origins.
- `createRestrictedRenderer()`: Factory method that wires up the restricted agent with correct DPI settings matching the default `ITextRenderer` constructor.

**Security analysis**:
- Properly handles edge cases: null URIs, single-letter drive letters on Windows, protocol-relative URLs
- Uses canonical path comparison (resolves symlinks) for path containment
- Log sanitization prevents log forging via control characters in URIs
- Fail-closed: returns null (blocked) on any parse error

**Classification**: **Improved** — closes SSRF attack vector in HTML→PDF rendering

---

### M1: PdfWriterFactory (Equivalent)

**Develop** had three overloads:
1. `newInstance(com.lowagie.text.Document, OutputStream, FontSettings)` — deprecated, **commented-out stamper code**
2. `newInstance(com.itextpdf.text.Document, OutputStream, FontSettings)` — active, chains stampers:
   - `PromoTextStamper(confidentiality, 30)` with `pts.setFontSize(settings.getFontSize())`
   - `PromoTextStamper(promoText + date, 20)` with `pts.setFontSize(settings.getFontSize())`
   - `PageNumberStamper(10)` with `pns.setFontSize(settings.getFontSize())`
3. `setFont(com.lowagie.text.pdf.PdfContentByte, FontSettings)` — deprecated utility

**PR** has one overload:
1. `newInstance(org.openpdf.text.Document, OutputStream, FontSettings)` — chains stampers via explicit `PdfPageEventForwarder`:
   - `PromoTextStamper(confidentiality, 30)` with `pts.applyFont(settings)`
   - `PromoTextStamper(promoText + date, 20)` with `pts.applyFont(settings)`
   - `PageNumberStamper(10)` with `pns.applyFont(settings)`

**Differences**:
- Same Y-offsets: 30, 20, 10 ✓
- Same stamper order: confidentiality, promo+date, page numbers ✓
- Font propagation: Develop used `setFontSize()` only. PR uses `applyFont()` which sets font name, encoding, embedding, AND size. This is actually **more correct** — develop was only propagating font size, not the full font configuration.
- PR uses explicit `PdfPageEventForwarder` instead of relying on `setPageEvent()` auto-chaining. OpenPDF 3.x auto-chains, so both approaches work.
- PR adds null-checks (`requireNonNull`) and throws `DocumentException` directly instead of returning null.
- Removed deprecated `com.lowagie.*` and `com.itextpdf.*` overloads.

**Classification**: **Equivalent** (same output) + minor **Improvement** (full font propagation via `applyFont()`)

---

### M2: FooterSupport / PageNumberStamper / PromoTextStamper (Equivalent)

**Changes**:
- Import swap: `com.itextpdf.text.*` → `org.openpdf.text.*`
- `FooterSupport` made `abstract` (was concrete) — appropriate since it has no standalone use
- `FooterSupport.setFont()` exception handling: `catch (Exception e)` → `catch (DocumentException | IOException e)` — more precise
- `FooterSupport.applyFont(FontSettings)` method added — convenience method calling `setFont()` + `setFontSize()`
- `FooterSupport.setFontSize()` adds validation: `fontSize <= 0` throws `IllegalArgumentException`
- `FooterSupport.setFont(BaseFont)` adds null check: `Objects.requireNonNull`
- `PromoTextStamper.text` made `final` — immutability improvement
- `PromoTextStamper` constructor adds `Objects.requireNonNull(promoText)` validation
- `PageNumberStamper.total` changed from `protected` to `private` — better encapsulation
- `PageNumberStamper.setTotal()` removed (unused)

**Rendering logic**: Identical in all three classes. Same `PdfContentByte` operations, same `ALIGN_CENTER`, same positioning math, same template mechanism for "Page X of Y".

**Classification**: **Equivalent** — same visual output, improved code quality

---

### M3: FontSettings (Improved)

**Develop**: Mutable class with no-arg constructor. Static constants initialized via static block modifying fields after construction. `createFont()` method included.

**PR**: Immutable class (all fields `final`, no setters, no no-arg constructor). Proper constructor validation (`requireNonNull`, positive fontSize). Added `equals()`/`hashCode()`. Removed `createFont()` method (font creation done by `FooterSupport.applyFont()`).

**Classification**: **Improved** — thread-safe, validated, proper value-type semantics. Same font values (HELVETICA_6PT, 10PT, 12PT all identical).

---

### M4: ReplacedElementFactoryImpl (Equivalent)

**Changes**: Import swap only (`com.lowagie.text.*` → `org.openpdf.text.*`). Flying Saucer 10.0.7 works with OpenPDF 3.x's `org.openpdf.text.Image` class. The `imageForPDF()` method, image scaling logic, and `TextFormField` handling are all unchanged.

Log level changed from `debug` to `warn` for image loading failures — more appropriate for production monitoring.

**Classification**: **Equivalent**

---

### L1–L6: Low-Risk 1:1 API Swaps (All Equivalent)

| File | Change | Verified |
|------|--------|----------|
| `EFormPDFServlet.java` | `com.itextpdf.text.*` → `org.openpdf.text.*` + `java.awt.Color` (OpenPDF uses `java.awt.Color` not `BaseColor`) | ✓ Equivalent |
| `FrmPDFServlet.java` | Same import swap pattern | ✓ Equivalent |
| `IncomingDocUtil.java` | Same import swap for PdfReader/PdfStamper/PdfCopy | ✓ Equivalent |
| `ConcatPDF.java` | Already used PDFBox — no PDF library change. Minor logging improvements. | ✓ Equivalent |
| `ConsultationPDFCreator.java` | Import swap. Keeps own EndPage. No PdfWriterFactory. | ✓ Equivalent |
| `PdfRecordPrinter.java` | Import swap. Already used `PdfWriterFactory.newInstance()` on develop (iText overload) → now uses OpenPDF overload. Identical stamper chain. | ✓ Equivalent |

**Note on `java.awt.Color`**: OpenPDF 3.0 removed `BaseColor` (which was iText's wrapper for `java.awt.Color`). The migration replaces `BaseColor` with `java.awt.Color` directly. This is a documented OpenPDF 3.0 migration step and is fully compatible.

---

## Items Requiring Manual Testing

1. **Doc2PDF Messenger letters** (H1): Test with actual Messenger letter templates to verify:
   - Page layout and margins match expectations
   - Images resolve correctly via `file://` base URL
   - CSS-heavy templates render without layout regressions

2. **ImagePDFCreator TIFF handling** (H3): Test with:
   - Single-page CCITT Group 4 TIFF (typical medical fax)
   - Multi-page TIFF (3+ pages)
   - High-DPI scan (300/600 DPI) — verify rendered size matches expected physical dimensions
   - Various compression formats if available (LZW, PackBits)

3. **PdfRecordPrinter footer rendering** (M1/M2): Print a multi-page patient record and verify:
   - Confidentiality statement appears at correct position
   - Promo text + date appears at correct position
   - "Page X of Y" is correct and properly positioned

---

## Security Improvements Summary

| Improvement | Files | Description |
|-------------|-------|-------------|
| SSRF prevention | `LocalOnlyUserAgent` (new) | Blocks http/https/ftp fetches during PDF rendering |
| Path traversal prevention | `ImagePDFCreator` | `PathValidationUtils.validateExistingPath()` for image paths |
| Connection leak fix | `Doc2PDF.GetInputFromURI()` | `FilterInputStream` wraps stream to auto-disconnect |
| Local file disclosure prevention | `LocalOnlyUserAgent.openStream()` | Path containment for `file:` URIs |
| Base URL injection prevention | `LocalOnlyUserAgent.setBaseURL()` | Rejects non-`file:` base URLs |
| Input validation | `FontSettings`, `FooterSupport` | Null checks and range validation |
| Log forging prevention | `LocalOnlyUserAgent.sanitizeForLog()` | Strips control characters from URIs before logging |

---

## Conclusion

The migration is well-structured with clear separation of concerns. The vast majority of changes (L1-L6, M1-M4) are **functionally equivalent** — same API with different package names. The two high-risk engine changes (H1, H3) have been handled thoughtfully but need manual verification with real medical documents due to the different rendering engines involved. The security improvements (H4, path validation, connection leak fix) are significant and justified additions.
