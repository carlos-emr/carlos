# HTML-to-PDF rendering (`documentManager/htmlpdf`)

How CARLOS turns an HTML document that has no scripts of its own into a PDF: the saved-as-document
copy of a form, a form sent by fax, an HTML report from the Hospital Report Manager (HRM), the PDF
of an encrypted email, a Messenger attachment. One module does it with one engine. Read this before
you change how any of those documents print, and before you add a second `ITextRenderer` anywhere.

The decision record, with every measurement behind it, is
[the design spec](superpowers/specs/2026-10-29-flying-saucer-html-pdf-consolidation-design.md).
This page is the guide for people who maintain the code. Fonts are covered by
[pdf-font-policy.md](pdf-font-policy.md).

## 1 — At a glance

| | |
|---|---|
| Entry point | `HtmlPdfRenderService.render(HtmlPdfRenderRequest)` returns an `HtmlPdfRenderResult` (the PDF bytes and the degradations) or throws `PDFGenerationException` |
| Engine | Flying Saucer 10.7.0 (`org.xhtmlrenderer`) writing through OpenPDF 3.0.3; PDFBox 3.0.8 reads the output back for checks; jsoup 1.23.2 parses and cleans the HTML; ICU4J reorders right-to-left text |
| Output | US Letter, 10 mm margins, 0.8 zoom (wide content is shrunk to fit, without limit), DejaVu Sans for every font family, WenQuanYi Micro Hei and Symbola for the characters DejaVu lacks |
| Callers | `ConvertToEdoc` (forms, HRM, email) and `Doc2PDF` (Messenger). New code injects `HtmlPdfRenderService` |
| Not used for | Saved eForms that run scripts: `EFormBrowserPdfService` (headless Chromium) prints those. JasperReports and the hand-written OpenPDF printers are separate (see the font policy) |
| Never | Fetches anything over the network, reads a file outside the directories the caller allows, prints a form control's hidden value or a password, or logs a path, a URL or document text |
| Failure | Is the last resort. A layout that fails is retried in safe mode (the author's styles removed) and then as plain text, with a note printed on the page; only a document that cannot be printed at all throws |

The log line to look for is `htmlpdf-render` (section 7).

## 2 — Package map and dependency rules

```
io.github.carlos_emr.carlos.documentManager.htmlpdf
├── dto/       HtmlPdfRenderRequest, HtmlPdfRenderResult, HtmlPdfRenderSource, HtmlPdfRenderDegradation
├── service/   Spring beans. Only HtmlPdfRenderService is public
│              (PrintDocumentComposer, PrintImageService, PrintLayoutService, RenderedPdfValidator,
│               BundledFontFileService; PageZoomCalculator, PageGeometry, PrintMode, HtmlPdfRenderException,
│               PreparedPrintImages, ImagePlan, PlannedImage, ImageOutcome)
├── support/   Static markup rules and printed text (domain nouns, no suffix, private constructor)
│              XhtmlCleanup, EngineExtensions, PrintFormControls, RtlTextShaping, FallbackFontRuns,
│              SystemColors, LegacyBodyMargins, SafeModeMarkup, HiddenContent, PlainTextDocument,
│              ImageBox, SvgReferences, SvgViewBox, PrintStylesheet, PrintMessages;
│              package-private: InlineStyle, HtmlAttributeNumbers
└── engine/    Flying Saucer and OpenPDF extensions, one set per render
               LocalOnlyUserAgent, BoundedOutputDevice, AllowedDirectories, DataUris, ImageDecoding,
               ImageHeader, BundledFontResolver, BundledFontSet
```

```
callers ──► service ──► support ──► dto, engine
              │  └────► engine
              └───────► dto
```

`dto` and `engine` depend on nothing else in the module; `support` uses only `dto` and `engine`
value types; only `service` sees everything. Nothing in the module imports `jakarta.servlet`, Struts,
a DAO, a manager or the legacy facades. `HtmlPdfArchitectureUnitTest` reads the source files and
fails the build when a rule is broken; it also rejects wildcard imports (they would hide a violation),
allows `ImageIO` decoding only in `ImageDecoding` (every decoder is vetted there) and requires a
`package-info.java` in every package.

## 3 — Architecture

The module is a **view renderer**. The controllers (Struts `*2Action`s and the managers behind them)
are unchanged; the model is a typed request and result; the renderer never sees an HTTP request.

```
Controllers: FormsManagerImpl, FaxDocumentManagerImpl, HRMUtil, EmailManager, MsgAttachPDF2Action ...
   │  legacy contracts: a Path or null, sendError
Legacy facades (thin, static): ConvertToEdoc, Doc2PDF
   │  HtmlPdfRenderRequest ──► HtmlPdfRenderResult
htmlpdf service tier (Spring beans)
   HtmlPdfRenderService  render slots, attempts, the ladder
   PrintDocumentComposer print-ready XHTML (pure DOM rules)
   PrintImageService     plans and prepares every image
   PrintLayoutService    layout passes and placement on Letter
   BundledFontFileService, RenderedPdfValidator
Engine extensions (per render): LocalOnlyUserAgent, BoundedOutputDevice, BundledFontResolver, BundledFontSet
```

Rules, as in [billing-ontario-module.md](billing-ontario-module.md) §3 and
[architecture/layer-names.md](architecture/layer-names.md):

- **Few stateless singletons.** Fields are `private final`, set by the constructor, which does no I/O
  (fonts are extracted on first use). The only JVM-wide state is the render slots (a fair
  `Semaphore`) and the verified font set. A per-render object (the jsoup DOM, the set of
  degradations, the renderer, the user agent, the temporary directories) lives on the calling
  thread for one attempt and is never kept in a field.
- **One public entry point.** Outside the module, use `HtmlPdfRenderService` and the `dto` types.
  Component scanning instantiates the package-private beans; `support` and `engine` types are public
  only because the service package needs them.
- **Constructor injection.** The production constructor carries `@Autowired`; a package-private
  constructor lets a test set a limit (the slot wait, the temporary root, the text-only size). The
  static facades look the service up with `SpringUtils.getBean(HtmlPdfRenderService.class)` when they
  need it, never in a static field.
- **No HTTP below the facades.** No status codes, no servlet types. A facade or controller decides the
  response.
- **Degradations are data.** Each attempt creates one `EnumSet<HtmlPdfRenderDegradation>`, passes it to
  the steps that can degrade, and the result carries it. No nulls, sentinels or exceptions for control
  flow.
- **Log or throw, never both.** The service logs each render's outcome and each failed attempt; it
  does not log the exception it throws. Callers log what they catch.
- **Constants, not settings.** Every limit is a named `static final` in the class that owns it, with
  its measured reason in a comment. There are no new `carlos.properties` keys (the image memory
  already follows the heap size).
- **No `@Transactional`.** The module does no database work.

## 4 — The pipeline

`HtmlPdfRenderService.render(request)`:

1. **Validate.** White-space-only HTML throws `PDFGenerationException`; markup with no visible content
   prints one blank page. An unloadable DejaVu Sans throws at once (every mode needs it). A `null`
   base directory (no webapp root) is replaced by a private empty directory and reported as
   `WEBAPP_ROOT_UNAVAILABLE`.
2. **Take a render slot** (section 5): wait at most 10 s.
3. **Attempt, on the ladder** `NORMAL`, then `SAFE`, then `TEXT_ONLY` (section 6), each from the
   request's HTML with a fresh set of degradations:
   - `PrintDocumentComposer.prepare`: the document steps a to i, n and o below;
   - `PrintImageService`: step j, the only step with side effects (temporary files in a per-attempt
     directory that is deleted whatever happens);
   - `PrintDocumentComposer.finish`: steps k to m;
   - `PrintLayoutService`: layout pass 1 (a 765 x 990 pt page, which the 0.8 zoom turns into Letter
     with 10 mm margins), pass 2 on a page sized to the content when it is wider, placement of each
     page on Letter;
   - `RenderedPdfValidator`: the output checks (the bytes start with `%PDF-`, PDFBox loads them and
     finds a page, every page is 612 x 792 pt, there is no AcroForm). An invalid PDF fails the attempt.

The document steps, in this order:

| Step | Rule | Where |
|---|---|---|
| a | Remove `<script>` and every comment (an HTML comment may contain `--`, which XML does not allow), then Flying Saucer's own `-fs-` CSS properties from `style` attributes and `<style>` text (`-fs-table-cell-rowspan` bypasses the span clamp of step f) | `XhtmlCleanup`, `EngineExtensions` |
| b | Remove soft hyphens and invisible bidi marks from text and `value` attributes | `XhtmlCleanup` |
| c | Form controls become static text and marks (section 9) | `PrintFormControls` |
| d | An inline `position: fixed` becomes `absolute` (Flying Saucer repeats fixed boxes on every page) | `XhtmlCleanup` |
| e | `<ol type>` becomes `list-style-type` | `XhtmlCleanup` |
| f | Clamp `rowspan` to the rows left in the row group, and `colspan` to 1000 | `XhtmlCleanup` |
| g | Mark tables with a `<thead>` and no row spans for repeated headers | `XhtmlCleanup` |
| h | Right-to-left text is shaped and reordered, line by line | `RtlTextShaping` |
| i | `alt=""` on an `<img>` without one | `XhtmlCleanup` |
| n | Mark the block around every word of 40 characters or more, so it wraps instead of shrinking the page | `XhtmlCleanup` |
| o | Replace the CSS2 system colours (`windowtext`) in colour properties | `SystemColors` |
| j | Images (section 6, "Images") | `PrintImageService` |
| k | Insert the `carlos-pdf-defaults` style sheet first in `<head>`, ending with the rule for the body's `leftmargin`/`topmargin` attributes, and the document note first in `<body>` | `PrintStylesheet`, `LegacyBodyMargins` |
| l | Wrap the characters DejaVu lacks in spans that name WenQuanYi or Symbola | `FallbackFontRuns` |
| m | Serialise as XHTML (valid XML, UTF-8) | `XhtmlCleanup` |

Every mode first removes markup-hidden elements (`hidden`) and their descendants, before replacing
form controls. Both simplified modes also remove elements with an inline `display: none`,
`visibility: hidden` or `collapse`; `HiddenContent`), because they drop the styles that would hide them
or keep every word. Safe mode then removes the author's `<style>`, stylesheet `<link>` and `style`
attributes and turns inline `<svg>` into a box (`SafeModeMarkup`); text-only mode builds a new document
from the text in reading order, table rows as `a | b | c` lines (`PlainTextDocument`). What a style
sheet class hides (`form/print.css` hides `.hidePrint` and others for print) cannot be told without
evaluating the sheets and is printed on these rungs.

**The order matters, and each dependency has a test:** control characters go before the form controls
copy a value and before right-to-left text is reordered; form controls are replaced before
reordering, so a Hebrew value is reordered like any text; spans are clamped before tables are marked;
fallback fonts come last, because their spans would split the runs that shaping reorders.

**Geometry.** The output of wkhtmltopdf, the engine this module replaced, is the reference: US Letter,
10 mm margins on all sides, 1 CSS px = 0.6 pt, `line-height: normal` about 1.164 for DejaVu Sans, a
repeated `<thead>`, no top margin at the start of a page, content wider than 925 px shrunk uniformly to
fit. `PrintStylesheet` and `PageZoomCalculator` reproduce it; the author's `@page` is ignored
(`!important` on ours).

## 5 — Time and memory bounds

Flying Saucer's layout cannot be interrupted, so the module bounds what it starts instead of timing
it out.

| Bound | Value | Why |
|---|---|---|
| Render slots | 2, fair | `ConvertToEdoc` is serialised, so one form render and one Messenger render can overlap |
| Wait for a slot | 10 s, once per render | The only wait in the module; a fax with several attachments queued behind `ConvertToEdoc`'s lock stays far inside the proxy's 300 s read timeout |
| Image memory | a quarter of the heap, shared equally by the slots (256 MB per slot at the 2 GB default) | How a document's images are reduced depends only on the document and the heap size, never on load, so output is deterministic |
| Decoded image | 25 megapixels; a header declaring more than 250 is a box; a file used as is up to 32 MB; a file read by the user agent up to 128 MB | Measured; see `ImageDecoding`, `PrintImageService`, `LocalOnlyUserAgent` |
| HTML size | 1,000,000 characters, not counting `data:` payloads, then straight to `TEXT_ONLY` | Measured when the module was added (below) |
| SVG bitmap | One per SVG, at most `clamp(maxMemory / 256, 1, 8)` million pixels (1.05 million at a 256 MB heap, 8 million from 2 GB), kept in proportion and stretched to the box | Flying Saucer rasterises an SVG with a gradient, pattern, filter, mask or translucency at its box in device dots (20 to a CSS pixel): 400 times the pixels of the CSS box, again at every draw. A 500 x 500 px chart is 400 MB |
| Picture inside an SVG | A base64 PNG, JPEG or GIF whose header declares at most 4 million pixels | Batik decodes it in full; a 100 KB PNG can declare 900 million pixels |
| Spans | `rowspan`/`colspan` clamped; `-fs-table-cell-*` properties removed; form control sizes capped (200 columns, 100 rows) | A span of 99,999,999,999 exhausts a 512 MB heap |

**Measured text-only limit** (Flying Saucer 10.7.0, JDK 25, 4 cores of a 2.1 GHz Xeon, 2 GB heap, a
four-column table of 77 characters a row): a normal-mode layout takes 0.6 s at 153,000 characters
(2000 rows, the reference fixture), 7.9 s at 1.0 million, 15.6 s at 1.55 million, 26.9 s at 2.0
million, 31.7 s at 2.17 million and 66 s at 3.1 million. Text-only mode takes 0.8 s at 1.0 million.
The limit is where the worst case of the ladder (10 s wait + normal + safe + text-only) stays under
60 s: 27 s at 1 million, 66 s at 2 million. To repeat the measurement, build `HtmlPdfRenderService` with
the constructor that takes the limit (raise it above the size under test) and time `render` on a
generated table of four-column rows. The tool that did this, `HtmlPdfComparisonMain size-limit`, went
with wkhtmltopdf; `git log --diff-filter=A -- '*HtmlPdfComparisonMain.java'` finds the commit that
added it.

The time is **quadratic in the number of rows** (Flying Saucer walks the siblings of a block while it
collapses margins; found by profiling), so characters are only a proxy: a document of very short
rows reaches the same time with fewer characters. A tag or row count would bound it better; it is a
follow-up.

## 6 — The resilience ladder

Each row is tried in order; an error reaches the caller only from the last column.

| Failure point | Mitigations, in order | Last resort |
|---|---|---|
| A JPEG photo | None: embedded as it is, never decoded (a JPEG with an EXIF orientation is decoded and turned upright, by the Orientation of its first EXIF block alone; a thumbnail's own is ignored) | |
| Another image over the per-image cap, or over 32 MB | Read as a stream, subsampled to fit (`IMAGE_DOWNSCALED`), printed size kept | The box |
| An image format OpenPDF cannot draw but a vetted reader can (BMP, WBMP, TIFF) | Converted to PNG or JPEG (`IMAGE_CONVERTED`) | The box |
| A valid header with corrupt data | Decoded by the tolerant reader and re-encoded (`IMAGE_CONVERTED`) | The box |
| An SVG with outside references | The referencing elements, attributes, `url()` values and `@import` rules are removed (`SVG_REFERENCES_REMOVED`) | The box (unparseable SVG) |
| An SVG Batik cannot draw (a `use` of an element that does not exist) | Left out of the page (`IMAGE_UNAVAILABLE`); the rest of the page prints | |
| A missing, remote, unreadable or unknown image (HEIC), a header over 250 megapixels, a format outside the vetted list (WebP) | Nothing: a remote source is never fetched | The box (`IMAGE_UNAVAILABLE`) |
| The document's images exceed the slot's memory share | The largest are reduced further, keeping printed sizes (`IMAGE_DOWNSCALED`) | |
| No slot within 10 s | | Every raster image is a box, with a note (`IMAGES_OMITTED_FOR_MEMORY`); a document without images renders normally |
| Layout pass 2 fails | Pass 1 placed at `612 / W`: smaller margins (`MARGINS_REDUCED`) | Next row |
| Layout fails, or placing, or the output check | Safe mode, with the note (`SAFE_MODE`) | Next row |
| Safe mode fails, or the HTML is over the size limit | Text-only mode, with the note (`TEXT_ONLY`) | `PDFGenerationException("HTML could not be converted to PDF", cause)` |
| No webapp root | A private empty base directory; webapp styles and images show as boxes (`WEBAPP_ROOT_UNAVAILABLE`) | |
| A fallback font cannot load | The characters print as U+FFFD (`FALLBACK_FONT_UNAVAILABLE`) | |
| The font directory is foreign-owned or a symlink | A fresh private directory for this JVM | |
| DejaVu Sans cannot load (a build defect) | | `PDFGenerationException`, at once |
| `ConvertToEdoc` cannot save the temporary file | (a full disk does not heal on retry) | `null`, as before |

A `StackOverflowError` inside an attempt is an attempt failure (a deeply nested document unwinds to
the attempt boundary). Every other `Error`, including `OutOfMemoryError`, propagates after the slot,
the image directory and the private base directory are released (the packaged server runs with
`-XX:+ExitOnOutOfMemoryError`). An interrupted wait for a slot restores the interrupt flag and throws
a retryable `PDFGenerationException`: with the flag set every NIO read fails and the page would print
without its images.

### Images

`PrintImageService` plans every image first, against the slot's memory share, then applies the plan:

- **Plan** (nothing is written to disk; decoded `data:` payloads retained during planning share a
  total byte budget equal to the slot's image-memory share, with duplicate URIs decoded once;
  an over-budget payload becomes an unavailable-image box before decoding):
  resolve and probe each `<img>` and inline `<svg>`; charge 16 bytes
  per decoded pixel (and four times the file size for a JPEG that must be decoded; a progressive one
  costs four bytes per source pixel more, because the JDK keeps its coefficients outside the heap);
  fit the total to the share by lowering the highest common charge ("water-filling"), so the largest
  images are reduced first in whole subsampling steps and small ones keep their size.
- **Apply:** decode, convert and write into the attempt's image directory, and rewrite or box each
  element. Apply never trusts the plan's view of the world: a file that vanished, grew or cannot be
  read becomes a box, not a failed attempt.
- **WebP and HEIC are not decoded** and print as the box (JPEG copies print). WebP decoding was removed
  after three separate bounding holes in the third-party decoder; `ImageDecoding` has an allow-list of
  vetted formats (PNG, JPEG, GIF, BMP, WBMP, TIFF) **and of vetted readers** (the JDK's, and the
  TwelveMonkeys TIFF reader), so a decoder that appears on the class path later never reads untrusted
  bytes unnoticed. The JAI ImageIO jar on the class path registers its own BMP, WBMP and TIFF readers,
  ahead of the JDK's for BMP and TIFF, under names that pass a list of formats; a provider that is not
  vetted is not even asked whether it can decode an input.
- **SVG** is cleaned (outside references removed) and then drawn by Flying Saucer and Batik, not
  decoded by the plan. The cleaning is a second layer: Flying Saucer gives Batik no document URL, so Batik
  itself refuses everything an SVG names but a `data:` URI. What the cleaner adds is that the rest of the
  drawing prints. Stylesheet SVGs pass through the same cleaner in the user agent before Batik reads
  them, including the nested raster-image limits. SVG streams over 16 MB or without a configured
  sanitizer are refused. Failed background-image loads report `IMAGE_UNAVAILABLE`.
  Four things were learned the hard way (section 14): an SVG that needs a bitmap is
  bounded by `BoundedOutputDevice`; its size is scaled to device dots by `LocalOnlyUserAgent` (a
  viewBox-only inline `<svg>` gets `width` and `height` from `SvgViewBox`); right-to-left text inside
  it is left in logical order for Batik; and one that cannot be drawn is left out instead of failing
  the document.

## 7 — Degradations and what an operator does

`HtmlPdfRenderService` writes one line per render, with the fixed token `htmlpdf-render`, the source
(`FORM`, `HRM`, `EMAIL`, `MESSENGER`), the mode that succeeded, the duration and the degradation names
(never a path or content), at the level of the most serious degradation:

```
htmlpdf-render source=FORM mode=SAFE durationMs=238 degradations=[SAFE_MODE]
```

| Level | Degradations | What it means | What to do |
|---|---|---|---|
| DEBUG | none | Normal | Nothing |
| INFO | `IMAGE_DOWNSCALED`, `IMAGE_CONVERTED`, `SVG_REFERENCES_REMOVED`, `MARGINS_REDUCED`, `RTL_BEST_EFFORT` | Content kept | Nothing |
| WARN | `IMAGE_UNAVAILABLE` | An image shows the box: missing, remote, corrupt or of an unsupported format; or an SVG that Batik could not draw was left out of the page (a log line names the exception class) | Check the form's image path, or the format (a HEIC or WebP photo), or the SVG (a `use` of an id that does not exist) |
| WARN | `IMAGES_OMITTED_FOR_MEMORY` | Two renders held both slots for more than 10 s | Look for a very large or runaway document; raise the heap |
| WARN | `FALLBACK_FONT_UNAVAILABLE` | A bundled font failed to load | Reinstall the package; check the font directory's owner |
| WARN | `WEBAPP_ROOT_UNAVAILABLE` | The render ran outside a request and the webapp root could not be found | Styles and images from the webapp are missing from that document; report it with the `source` |
| ERROR | `SAFE_MODE`, `TEXT_ONLY` | A document could not be laid out as authored | A template or engine defect: reproduce it with FAKE data and open an issue; **never attach a patient's PDF** |

A failed attempt logs WARN with the mode, the phase and the exception class; a `LinkageError` logs
ERROR (a packaging defect: a library version mismatch). The root logger defaults to WARN, so INFO lines
appear only when raised (`debug-on` in the devcontainer). There are no metrics: grep the token.

**Flying Saucer's own logging stays at WARN.** The library's CSS parser traces every declaration of a
document's CSS with its values at TRACE, and its font resolver logs families, so at TRACE a patient's
text could reach the log through a `url(...)` or a font name. `log4j2.xml` pins the `org.xhtmlrenderer`
logger at WARN with a comment; **an override configuration must carry the same logger**. Its SVG
logger, `org.xhtmlrenderer.pdf.SvgImage`, is held at ERROR: the WARN it would write quotes the reference
Batik could not follow, which can be a whole `data:` URI (a picture in a record). The pipeline logs the
class of that exception itself. `HtmlPdfLibraryLoggingUnitTest` checks the settings the application
actually reads.

## 8 — Printed text and its keys

The document notes and the box text come from `oscarResources`, read with `Locale.ENGLISH`, so the
output never depends on the server's default locale (`PrintMessages`):

| Key | Printed when | English text |
|---|---|---|
| `htmlpdf.note.safeMode` | `SAFE_MODE` | This document was printed with simplified formatting because its layout could not be rendered. |
| `htmlpdf.note.textOnly` | `TEXT_ONLY` | This document was printed as plain text because its layout could not be rendered. |
| `htmlpdf.note.imagesOmittedForMemory` | `IMAGES_OMITTED_FOR_MEMORY` | Images were omitted because the server was busy. Print again to include them. |
| `htmlpdf.note.rtlBestEffort` | `RTL_BEST_EFFORT` | Right-to-left text was converted for printing. Check the line order of longer right-to-left passages against the original. |
| `htmlpdf.imageUnavailable` | An image prints as a box | [image unavailable] |

The keys are in all five `oscarResources_*.properties` files (`bash scripts/check-i18n-properties.sh`
checks parity); the French, Spanish, Polish and Brazilian Portuguese values carry a
`# TODO: review the translations` marker. Printing in the reader's language needs a locale on
`HtmlPdfRenderRequest`; it is a follow-up.

## 9 — Security and PHI rules

- **No network, ever.** `LocalOnlyUserAgent` refuses `http:`, `https:`, `ftp:` and protocol-relative
  URLs at the transport layer; `data:` URIs and local files only. A remote `<img>` is a box.
- **Allowed directories.** A request lists the directories a document may read from (the webapp root,
  the eForm image directory, `java.io.tmpdir`, Catalina's work directory for `ConvertToEdoc`);
  `AllowedDirectories` resolves symbolic links before it compares, and every path must be absolute
  and normalised (`HtmlPdfRenderRequest` enforces it). The user agent also bounds the size of a file
  it reads and the pixels it decodes.
- **XML.** Standalone SVG is parsed with external entities, external DTDs and XInclude disabled; its
  parser features, access properties and entity resolver overlap on purpose, and removing all of them
  is caught by the XXE test. The service's own XHTML loader is strict and namespace-aware.
- **Form controls print what a person typed, never what the page hid.** Hidden inputs, submit and
  image buttons, `<button>` and file inputs are removed; a password prints eight bullets whatever its
  length; a `<select>` prints the chosen option's text, a multi-select the chosen options joined by
  commas; `<progress>` and `<meter>` print as bars, a grey track filled to the value (a progress is
  10em wide and blue, a meter 5em and green, amber or red by its `low`, `high` and `optimum`, as in
  Chromium; a progress with no value is the track alone; a bar has no text, so the plain-text last
  resort leaves it out as it leaves out an image); a `<textarea>` keeps its line breaks.
  Replacements grow to fit, so a value is never clipped.
- **Logs carry class names, counts and mode names.** No path, URL, file name, HTML or exception
  message from the engine is logged. `HtmlPdfRenderRequest.toString()` prints the source, the HTML's
  length and the number of allowed directories, and `HtmlPdfRenderResult.toString()` the size and the
  degradation names, so logging either is safe. Exception messages name the failed check only.
- **Fixtures use FAKE- names only.** No patient data in tests, issues or pull requests.

## 10 — The facades and their contracts

| Facade | Builds | Contract |
|---|---|---|
| `ConvertToEdoc.saveAsTempPDF(FormTransportContainer)` | `FORM` | A `Path` to the PDF in the temporary directory, or `null` (it logs why) |
| `ConvertToEdoc.saveAsTempPDF(EFormData)` | `HRM` | The same; its only production caller is `HRMPDFCreator` |
| `ConvertToEdoc.saveAsTempPDF(EmailData)` | `EMAIL` | The same |
| `ConvertToEdoc.from(EFormData, Path)` | none | An `EDoc` that wraps a PDF rendered elsewhere (the headless-browser eForm path); no HTML is rendered; throws `PDFGenerationException` if the PDF is unreadable |
| `Doc2PDF.parseString2PDF`, `parseString2Bin` and the string entry points | `MESSENGER` | Their existing failure paths; null or blank text is a failure, not a blank page |

`ConvertToEdoc` does the legacy translation before it asks the service to render: it settles one
webapp root, rewrites each element's resource paths (every occurrence of a repeated `src` is
translated and validated) into encoded `file:` URIs, and gives an `<img>` that is remote, outside the
root, missing or of an unlisted type a `data-carlos-image-unavailable` marker so it prints as the box.
The `FileType` list holds the picture types the service draws (`jpeg`, `jpg`, `png`, `gif`, `bmp`,
`wbmp`, `tif`, `tiff`, `svg`) and the `css`, `js` and `pdf` a document may link; it is only the front
door, as the service judges a file by its content, not its extension (a `.png` that is a PDF is still a
box). It then builds one request: the webapp root as the base directory; the root, the eForm image
directory, the temporary directory and Catalina's work directory as the allowed ones.

`ConvertToEdoc` has one engine: the service. It renders the document once; a document the service
cannot print at all is a failure of the facade (a logged `null`), not a reason to try another engine.

What callers do with a failure:

- `HRMPDFCreator.printPdf` declares `PDFGenerationException` and throws it, with a fixed message, when
  an HTML report cannot be converted; it deletes the converted temporary PDF once copied.
  `HRMUtil.renderHRM` names the report in the exception; `PrintHRMReport2Action` answers with its
  existing 500, "Unable to generate HRM PDF"; the consultation print action already catches it.
- `EmailManager.createMessageAttachment` throws `EmailSendingException("Encrypted message could not
  be converted to PDF")`; `sendEmail` records the email as failed with that fixed message and sends
  nothing.
- The form, fax and Messenger callers keep their existing `null` and `sendError` handling.

## 11 — Adding a markup rule

1. **Write the rule in `support`** as a static utility named for what it knows about (a domain noun:
   `SystemColors`, not `ColourHelper`), with a private constructor and only static methods. It changes
   the jsoup `Document` it is given and nothing else (or returns text for the style sheet), keeps no
   mutable static state, reads no file and clock, behaves the same under any locale, and logs no
   content.
2. **Call it from `PrintDocumentComposer`** in `prepare` (a document rule) or `finish` (the style
   sheet, the note, fonts, serialisation), where its order is right, and say in the class Javadoc why
   it sits there. A rule that must also run in text-only mode goes after `PlainTextDocument.from`.
3. **Test it three ways:** a unit test of the rule (the cases, and what must be left alone); a
   composer test of the order (a test for each dependency between steps); and, if it changes what a
   real form looks like, an assertion in `HtmlPdfFixturesUnitTest` on a fixture, read back with
   `TestPdfs`. Then break the rule on purpose (a mutation) and check a test fails.
4. **Look at the page.** Two real defects (a stylesheet that squeezed table columns to one letter, and
   a keyword Flying Saucer silently dropped) passed unit tests and showed only in a rendered image.
   Render the fixture and open the PNG: `TestPdfs.image(pdf, page)`.
5. **Update** `support/package-info.java`, the pipeline table here and the spec.

## 12 — Testing

Run the module's tests with the `htmlpdf` tag (`mvn -q test -Dgroups=htmlpdf`) or by package
(`mvn -q test -Dtest='io.github.carlos_emr.carlos.documentManager.htmlpdf.**'`). Highlights:

| Test | What it pins |
|---|---|
| `HtmlPdfArchitectureUnitTest` | The dependency graph, forbidden imports, no wildcard imports, one public service class, a `package-info` everywhere, `ImageIO` decoding only in `ImageDecoding`, the layer-name policy |
| `HtmlPdfSpringWiringUnitTest` | The module's package scan alone wires the six beans, all singletons, and renders |
| `HtmlPdfFixturesUnitTest`, `HtmlPdfFormsUnitTest` | The Annual forms, an every-control page, HRM reports (demo, table-heavy, Word-style, images), email, Messenger, 14 geometry pages, scripts and edge markup, through the production beans. The central assertion: **a real document renders with no WARN- or ERROR-level degradation** |
| `HtmlPdfSsrfUnitTest` | No network request and no read outside the allowed directories, whichever way the document asks, in every mode |
| `HtmlPdfImageOrientationUnitTest` | A picture of four colours stored with each of the eight EXIF orientations prints as a viewer shows it; a 12-megapixel phone photo, stored sideways, prints upright in portrait proportions; the images grid's scene has the sun at the top right |
| `HtmlPdfConcurrencyUnitTest`, `HtmlPdfMemoryUnitTest` | Concurrent renders give identical bytes; the image memory share holds under a 256 MB heap in a child JVM |
| `HtmlPdfLibraryLoggingUnitTest` | The library's logger stays at WARN, its SVG logger at ERROR, and a document's values never reach either |
| `HtmlPdfSvgSizeUnitTest`, `HtmlPdfSvgTextUnitTest`, `BoundedOutputDeviceUnitTest` | An SVG prints at the size a browser gives it however it is sized; Hebrew in an inline SVG keeps its order; huge gradient SVGs, tiled backgrounds and SVGs that cannot be drawn render within bounds. `HtmlPdfMemoryUnitTest` renders them in a 256 MB child JVM |
| `*UnitTest` per class | Each rule and bean, with mutation checks |

Helpers: `HtmlPdfFixtures` (documents, the web root they read, the images report), `HtmlPdfTestServices`
(a real service for tests outside the package), `TestPdfs` (read geometry, text, fonts and images back out
of a PDF), `TestFonts` (**use `sharedDirectory()`/`sharedFonts()`**: OpenPDF caches a parsed copy of
each font file by path for the life of the JVM, about 25 MB for the four bundled fonts, so a font
directory per test exhausts a test JVM's heap), `TestImages`.

Fixtures are in `src/test/resources/htmlpdf` (its README says how each was made). The two Annual form
pages were derived from the JSPs by `scripts/htmlpdf/derive-annual-fixtures.py` because they were made
where no server runs; **`scripts/htmlpdf/capture-annual-fixtures.js` captures them from a running
devcontainer or a packaged install instead (run once, in an Ubuntu 26.04 container)**. Fonts under other hosts: `bash scripts/check-report-fonts.sh`.
Text read back from a PDF: PDFBox returns right-to-left text in logical order and splits a line at
every cell, so assert on phrases and glyph positions (`TestPdfs.glyphs`), not on whole lines.

## 13 — Known limitations

- **WebP and HEIC images** are not decoded and print as the box.
- **`ConvertToEdoc` image types:** a picture named by path passes by its extension (`jpeg`, `jpg`, `png`,
  `gif`, `bmp`, `wbmp`, `tif`, `tiff`, `svg`, in any case); any other extension (`.webp`, `.heic`,
  `.jfif`) shows the box. The same formats inside a `data:` URI are drawn.
- **Indic and Thai scripts** (Devanagari, Gurmukhi, Tamil, Thai) have no bundled glyphs or shaping and
  print as U+FFFD (wkhtmltopdf printed boxes). Some Persian and Urdu letter forms are missing from
  DejaVu Sans. Copying or searching shaped Arabic out of the PDF yields presentation forms in visual
  order; the printed page is right.
- **Long right-to-left passages** that wrap print their lines in reverse order (the note says so).
- **Progress and meter bars** fill from the left even in right-to-left text, where a browser fills from the right;
  the filled share is right.
- **The label of a very small placeholder box** (an image under about 45 pixels wide) is clipped even at the smallest
  size (4 pt); a wider box wraps it between its words and sets it smaller to fit. The box still marks where the
  image was.
- **Messenger holds the chart PDFs of a message in a `MEDIUMBLOB`.** `messagetbl.pdfattachment` is 16,777,215 bytes
  (`MessageTbl.PDF_ATTACHMENT_MAX_BYTES`) since migration `V1.0.59.8`; it was a 65,535-byte `BLOB`, which held one
  chart PDF (with its fonts embedded a PDF is about 40 KB, 55 KB as base64) and cut a second off in the middle. The
  application still refuses what does not fit (a 413 when attaching, the compose page again when sending) instead of
  letting MariaDB cut the text off, judging it as it is stored (`MsgMessageData.pdfAttachmentFits`: the send doubles
  each backslash of a title). The migration copies `messagetbl` once, so it takes as long as the table is large.
- **Stylesheet-defined `position: fixed`** still repeats on every page; an inline one is rewritten.
- **A table with both `<thead>` and row spans** prints its header once.
- **Top margins at the start of a page** are dropped only for the body and its direct first child (as
  WebKit dropped any first block's): a heading nested in a wrapper (`<form><h1>`), a paragraph after a
  `position: fixed` element and an empty block keep theirs, a few points at most.
- **Layout time is quadratic in the number of rows**, and the size limit is in characters (section 5).
- **CSS background images** that fail to load are omitted and report `IMAGE_UNAVAILABLE`; they cannot
  show the box. An SVG named by a stylesheet is sanitized before loading, using the same reference
  and nested-image rules as other SVGs.
- **Safe and text-only mode print what a style sheet class hid** (section 4); in normal mode Flying Saucer
  applies the print rules. Elements marked `hidden`, their descendants, and `type="hidden"` inputs
  never print in any mode. The existing exception for a hidden document root (`html` or `body`)
  remains, so pages hidden until a script runs can still print.
- **A non-vector SVG is rasterised once for each `SvgImage` Flying Saucer makes**, so fifty `<img>` of the
  same gradient icon are fifty small bitmaps (about 40 ms each); one SVG tiled, or drawn on several pages,
  is one bitmap.
- **Decoded `data:` payloads are bounded together** by the slot's image-memory share during planning
  (and individually by 128 MB). The original HTML string remains in memory.
- **HRM files in Windows-1252** decode to U+FFFD, as they did with wkhtmltopdf; `HRMPDFCreator` decodes UTF-8.
- **Form controls are replaced by their text:** author CSS that targets `input`/`select`/`textarea`
  element types no longer sizes them.

## 14 — Notes on the libraries (facts learned the hard way)

- Flying Saucer 10.7.0's XML loader is **strict and namespace-aware**: an undeclared prefix
  (`<o:p>` from Word), `--` in a comment or a control character in a style sheet throws. `serialize`
  folds undeclared prefixes into names and strips XML-invalid characters.
- It **drops a whole declaration that uses a CSS2 system colour** (`border: solid windowtext 1.0pt`
  is what Word writes for every table border), hence `SystemColors`.
- It supports `word-wrap`, not `overflow-wrap`, and `word-wrap: break-word` on the whole body makes
  every table cell's minimum width one character (the Annual form's label columns printed as vertical
  strips), hence step n and the `carlos-pdf-wrap` class.
- It does not read `<body leftmargin>`, `topmargin` and the like, which WebKit did; `LegacyBodyMargins`
  maps them (230 of CARLOS's JSPs carry them).
- It paints an element's own text before its children's, so read a PDF with
  `PDFTextStripper.setSortByPosition(true)` to get page order.
- Flying Saucer 10.7.0 is built against OpenPDF 3.0.5 and CARLOS runs the vendored 3.0.3: every
  member it references resolves (266 of 266 when checked), and a mismatch would surface as a
  `LinkageError`, which the service logs at ERROR. **Re-check when you bump either.**
- jsoup 1.23.2 caps the nesting depth of the tree it builds at 513.
- DejaVu Sans draws U+1F600 and U+2603; Symbola draws a few Han characters; of 426 format and
  variation-selector code points only 42 are drawable by DejaVu, so the rest are left in the text and
  OpenPDF draws nothing for them. A line of only U+1D11E (Symbola gives it zero advance) is not
  drawn at all.
- `ImageIO.scanForPlugins()` is not called anywhere; the TIFF reader is found by the same service
  provider mechanism as before. Check it under Tomcat's web application class loader (a manual check).
  `ImageDecoding` asks only the JDK's providers (the `java.desktop` module) and the TwelveMonkeys TIFF
  provider; which of the two answers for TIFF follows ImageIO's registry order.
- **Flying Saucer takes an SVG's size in device dots** and a raster image's in CSS pixels (it scales those
  by the dots per pixel in `ITextUserAgent`, and not SVGs), so an SVG whose page gives no size would print
  at a twentieth of its width. `SvgImage.getImage()` also runs Batik's `PNGTranscoder` at the box size in
  dots *every time it is called*, with nothing cached, for an SVG with a gradient, pattern, filter, mask or
  translucency (`SvgImage.isDrawableAsVector` decides; a vector SVG is drawn into a template added to the
  page only when complete). A `BridgeException` (a `use` of an element that does not exist) is not caught
  by the library and ends the render.
- **Batik lays out SVG `<text>` with its own bidirectional algorithm** and Arabic shaping; text the
  pipeline has already put in visual order is reordered a second time and prints back to front.
- **`word-wrap` is inherited** in Flying Saucer 10.7.0: a table inside a block marked `carlos-pdf-wrap`
  needs `word-wrap: normal` (the stylesheet has it), or its words break mid-word.

## 15 — Rollback

If printed, faxed or emailed documents regress after release, revert the change that made the service
`ConvertToEdoc`'s only engine (`feat(documents): switch to Flying Saucer and remove wkhtmltopdf (2/2)`):
it restores wkhtmltopdf as the primary engine with this module as its fallback, and brings back the
vendored jar, the JNA pin, `libxrender1`, the settings and the comparison tool (regenerate
`dependencies-lock.json` with `make lock`). Triage reports as GitHub issues with a FAKE-data
reproduction (the form, values with FAKE- names, and the `htmlpdf-render` log line), never a patient's
PDF.

## 16 — Cross-references

- [pdf-font-policy.md](pdf-font-policy.md): the font rules for every PDF engine, including this one.
- [billing-ontario-module.md](billing-ontario-module.md) §3 and [architecture/layer-names.md](architecture/layer-names.md):
  the layering and naming conventions this module follows.
- [superpowers/specs/2026-10-29-flying-saucer-html-pdf-consolidation-design.md](superpowers/specs/2026-10-29-flying-saucer-html-pdf-consolidation-design.md):
  the decisions, the measurements and the full resilience table.
