# Design: Consolidate HTML-to-PDF on Flying Saucer and Remove wkhtmltopdf

**Date:** 2026-10-29 (revised the same day after a verification review)
**Status:** Design approved in conversation; written spec awaiting review
**Target:** `release/2026.08` (currently `2026.08.0-beta3-SNAPSHOT`), forward-merged to `develop`

Every behavioural claim in this spec was checked against the code on `release/2026.08` and,
where it concerns rendering, by rendering test documents with the real engines:
wkhtmltopdf 0.12.6.1 from the vendored `ultrabuk-htmltopdf-java` jar with CARLOS's exact
settings, Flying Saucer 10.0.6 and 10.7.0 through CARLOS's own `LocalOnlyUserAgent` and
`ReplacedElementFactoryImpl`, and PDFBox 3.0.8 for merging. "Measured" means a value read
from those outputs.

## Problem

CARLOS EMR turns HTML into PDF with three engines:

| Engine | Used for |
|---|---|
| Headless Chromium (Selenium) | Saved eForms (`EFormBrowserPdfService`) |
| wkhtmltopdf (`ultrabuk-htmltopdf-java`, native `libwkhtmltox`) | First choice in `ConvertToEdoc`: encounter forms (print, fax, email, consultation and eForm attachments), HTML HRM reports, encrypted-email message PDFs |
| Flying Saucer 10.0.6 | `ConvertToEdoc` fallback when wkhtmltopdf throws; `Doc2PDF` (Messenger "attach as PDF", which also renders encounter forms through `FormTransportContainer`) |

Chromium stays for scripted and complex pages. The other two have these problems.

### wkhtmltopdf is abandoned and weakly guarded

- The upstream project was archived in January 2023; its Qt WebKit engine is unmaintained;
  it has a published SSRF vulnerability (CVE-2022-35583).
- `InternalEDocConverter` runs it with `load.blockLocalFileAccess=false` and no
  transport-level URL guard. CARLOS relies only on DOM clean-up before rendering.
- It ships as a 33 MB vendored jar with a native library, needs `libxrender1` on the host
  and pulls in JNA.
- CARLOS's own dependency audits (`docs/dependency-audit-2026-03-16.md`, `-03-17.md`) mark
  it "abandoned, replace".
- Its margin settings in `InternalEDocConverter` (`T`, `L`, `R`) are not valid libwkhtmltox
  object settings and are silently ignored: renders with and without them are
  pixel-identical. Real output is US Letter with wkhtmltopdf's default 10 mm margins on all
  sides (measured).

### Flying Saucer, as CARLOS configures it today, cannot replace it

Each item below was observed by rendering. Items 1–4 also affect today's `Doc2PDF` output
and today's `ConvertToEdoc` fallback.

1. **Default stylesheet blocked.** `LocalOnlyUserAgent` blocks every `jar:` URL, including
   Flying Saucer's own default stylesheet
   (`jar:file:…/flying-saucer-core-*.jar!/resources/css/XhtmlNamespaceHandler.css`). Every
   Flying Saucer PDF prints the page `<title>` and raw CSS text at the top, loses block and
   table layout and ignores forced page breaks.
2. **Image sizes ignored.** `ReplacedElementFactoryImpl` calls `fsImage.scale(width, height)`
   and discards the returned image.
3. **Fonts break the PDF font policy (#5046).** For no family, generic families and unknown
   names, Flying Saucer uses non-embedded base-14 Times, Helvetica and Courier. It has no
   per-glyph fallback: Chinese, Japanese and Korean text and symbols such as ✓ are silently
   dropped. Registering bundled fonts under the generic names does not override this.
4. **Form controls lose or leak data.** `ReplacedElementFactoryImpl` turns every `<input>`,
   whatever its type, into a live AcroForm text field. Flying Saucer's default factory
   (used by `Doc2PDF`) handles only `input`, `img`, `svg` and `bookmark`. Compared with
   wkhtmltopdf, which prints ☒/☐, ◉/○, masked passwords and only the selected option:

   | Control | `ConvertToEdoc` fallback today | `Doc2PDF` today |
   |---|---|---|
   | checkbox | blank (checked state lost) | ZapfDingbats mark (non-embedded font) |
   | radio | prints its `value` ("y"/"n") | mark, but group `/V` is `Off` |
   | hidden | **value prints** | not printed |
   | password | **value prints in clear** | **value prints in clear** |
   | select | every option run together | every option run together |
   | textarea | line breaks collapsed | line breaks collapsed |
   | text value with CJK | CJK dropped | CJK dropped |
   | button/submit | truncated text field | truncated text field |

   The real Annual form (`formannual.jsp` → `formannualfemale.jsp` / `formannualmale.jsp`)
   has 55–64 checkboxes, 33–36 text inputs, 7 textareas, 8 hidden inputs (`demographic_no`,
   `ID`, `provider_no`, `provNo`, `formCreated`, `form_class`, `form_link`, `submit`) and
   8–9 buttons. Every checkbox answer would vanish and patient-linked identifiers would
   print on faxed and emailed PDFs.

   The fields are also live AcroForm widgets: only `DocumentAttachmentManagerImpl.concatPDF`
   flattens them. The consultation print packet, save-as-document, email attachments,
   single form faxes and `HRMPDFCreator` ship them unflattened, and a PDFBox merge without
   flattening draws no checkbox or radio marks (measured).
5. **Geometry differs from wkhtmltopdf** (measured):

   | Property | wkhtmltopdf (CARLOS settings) | Flying Saucer default |
   |---|---|---|
   | Scale | 0.8 zoom: 1 CSS px = 0.60 pt, 1 pt = 0.80 pt | 1 px = 0.75 pt, 1 pt = 1 pt (25 % larger) |
   | Content wider than the page | shrunk uniformly to fit (1000 px → 0.555 pt/px, 1400 px → 0.396 pt/px) | clipped at the right margin (beyond ~720 px) |
   | Page size | always US Letter; author `@page` ignored | author `@page` obeyed; otherwise A4 or Letter from the JVM locale (Letter only for US, CA, MX; `LANG=C.UTF-8` gives A4) |
   | Margins | 10 mm all sides | 0.5 in, or author `@page` |
   | Minimum font size | 10 CSS px | none |
   | Media | `print` | `print` (same) |

   The Annual form's `form/annualStyle.css` has 800–955 px containers, so Flying Saucer
   would clip its right side. A legacy-eForm-like page took 1 page in wkhtmltopdf and 2 in
   Flying Saucer; an HRM-like table 2 versus 3.
6. **Stylesheets blocked on Debian installs.** `ConvertToEdoc.fallbackRender` passes a null
   base URL. Flying Saucer then uses the JVM working directory as the base, and
   `LocalOnlyUserAgent` only allows the base directory, `java.io.tmpdir` and
   `catalina.base/work`. `translateResourcePaths` rewrites `<link href>`, CSS `url()`,
   `background=` and the `pdfMediaStylesheet` link to absolute paths under the webapp root
   or `EFORM_IMAGES_DIR`. On Debian the working directory is `/var/lib/carlos-emr/catalina`
   (`debian/assets/carlos-emr-tomcat`), the webapp is `/usr/share/carlos-emr/webapp/carlos`
   and eForm images are under `/var/lib/carlos-emr/CarlosDocument`, so every one of those
   resources is blocked with only a WARN. The devcontainer and Podman keep the webapp under
   the working directory and hide this.
7. **`data:` and SVG images dropped in `ConvertToEdoc`.** `ReplacedElementFactoryImpl` always
   opens `new File(src)`, so `data:` URIs (kept by `translatePaths`) fail with "No such
   file" and print nothing.

### Failure paths that crash instead of failing clearly

With no second engine, these fire more often:

- `HRMPDFCreator.printPdf` (HTML branch, line ~182) calls `Files.readAllBytes(null)` when
  `ConvertToEdoc` returns null. The `NullPointerException` escapes `HRMUtil.renderHRM`,
  which catches only `IOException`, into fax, consultation and attachment flows.
- `EmailManager.createMessageAttachment` (line ~516) calls `toString()` on a null path.
- `ConvertToEdoc.execute` calls `tidyDocument` outside its `try` (blank HTML throws
  `IllegalArgumentException`) and catches only `DocumentException` and `IOException`.

### Unused code and dependencies

- `openpdf-html` (and, through it, `neko-htmlunit`) is on the classpath; nothing imports it.
- `EformDataManager.saveEformDataAsEDoc`, `FormsManager.saveFormDataAsEDoc`,
  `ConvertToEdoc.from(EFormData)`, `ConvertToEdoc.from(FormTransportContainer)` and
  `ConvertToEdoc.saveAsTempPDF(String, String)` have no production callers.
  `saveFormDataAsEDoc` also dereferences a null `EDoc` on failure.
- `ConvertToEdoc.DEFAULT_WKHTMLTOPDF_COMMAND` / `DEFAULT_WKHTMLTOPDF_ARGS` are unused.

## Decisions

Agreed in conversation on 2026-10-29:

| Question | Decision |
|---|---|
| Engine for non-scripted HTML-to-PDF | Flying Saucer only; Chromium stays for saved eForms |
| Rollout | Hard cut: wkhtmltopdf removed in the same release, no configuration switch |
| When Flying Saucer cannot render | Fail clearly with an error naming the document; no fallback to Chromium |
| Flying Saucer version | Upgrade 10.0.6 → 10.7.0 as the first step |
| Structure | One shared Flying Saucer pipeline; `ConvertToEdoc` and `Doc2PDF` become thin callers; their callers keep their contracts |
| Fonts | Hard-coded bundled fonts, same mapping as JasperReports, plus a fallback-font step |
| Output geometry | Emulate wkhtmltopdf: Letter, 10 mm margins, 0.8 zoom, shrink-to-fit, 10 px minimum font |
| Form controls | Printed as static text and marks; never as PDF form fields |
| Release | Land on `release/2026.08`; maintainers forward-merge to `develop` |

Flying Saucer 10.7.0 was checked before choosing it:

- LGPL 2.1 or later; licence files byte-identical to 10.0.6.
- Binary-compatible with the vendored OpenPDF core 3.0.3: all 204 OpenPDF members it
  references exist in 3.0.3 (inheritance followed). CARLOS's Flying Saucer classes compile
  against it unchanged.
- Same dependencies as 10.0.6 (`flying-saucer-core`, `openpdf`, `batik-codec`; Batik's
  bridge classes, needed for inline SVG, already come in through JasperReports).
- SSRF probe (9 vectors) and XXE probe (external entity, parameter entity, external DTD):
  zero requests on 10.7.0, as on 10.0.6.
- `ITextRenderer.setScaleToFit(boolean)` exists (used below).

## Goals

- Every non-scripted HTML-to-PDF render goes through one Flying Saucer pipeline.
- Output matches what wkhtmltopdf produces today: same paper, margins, scale, shrink-to-fit
  and page count, verified on the comparison fixtures.
- Output is deterministic: the same HTML gives the same fonts and layout on every server,
  whatever its locale, working directory or installed fonts.
- Every font in the output is bundled in the WAR and embedded; no text is silently dropped.
- Form answers print; hidden values, passwords and buttons never do; no PDF form fields.
- The SSRF guard covers every path and never depends on the working directory.
- Every failure reaches the caller as a clear error or `null` result per the caller's
  existing contract; none escapes as an unhandled `NullPointerException`.
- The WAR, the Debian package and the build no longer carry wkhtmltopdf, its native
  library, `libxrender1`, JNA, `openpdf-html` or `neko-htmlunit`.

## Non-Goals

- Changing the Chromium eForm renderer (`EFormBrowserPdfService`).
- Changing PDFs drawn directly with OpenPDF (cover pages, consultations, labs, HRM text and
  image reports) or JasperReports templates.
- Moving callers onto a new API, or deleting `ConvertToEdoc`/`Doc2PDF`.
- Removing `ConvertToEdoc`'s class-level `synchronized` methods or its static `realPath`.
- Upgrading the vendored OpenPDF core (stays 3.0.3).
- Changing the OSCAR 19 import map: carlos-ctl's own test suite, which CI runs against this
  tree (`debian-python-tests.yml`, `CARLOS_SRC`), asserts `WKHTMLTOPDF_COMMAND` is
  `deploy-owned` (`carlos-ctl/tests/test_props.py:210-211`), and neither disposition carries
  the value over anyway.
- Devcontainer system packages (`.devcontainer/development/Dockerfile:79`: `libxrender1`,
  `libxi6`, `xfonts-75dpi`, `xfonts-base`); see "Follow-ups".

## Design

### Components

All new and changed classes are in `io.github.carlos_emr.carlos.documentManager`, next to
the existing Flying Saucer classes, unless noted. Names follow
`docs/architecture/layer-names.md`: static utilities use a domain noun; the font class
makes one decision through a priority chain, so it is a `*Resolver`. The table shows the
end state after PR 2; "Delivery" shows the split.

| Class | Status | Responsibility |
|---|---|---|
| `HtmlPdf` | New, public, static utility | The only entry point: `render(String html, RenderContext context)` returns PDF bytes or throws `PDFGenerationException` |
| `HtmlPdf.RenderContext` | New record | `baseDirectory` (`Path`, required, absolute), `allowedDirectories` (`List<Path>`, required, non-empty, no filesystem root), `imageSource` (`ImageSource`) |
| `HtmlPdf.ImageSource` | New enum | `GUARDED_USER_AGENT` (all images load through `LocalOnlyUserAgent`) or `VALIDATED_LOCAL_FILES` (absolute file paths already validated by `ConvertToEdoc` load directly; everything else through `LocalOnlyUserAgent`) |
| `XhtmlCleanup` | New, package-private, static utility | Jsoup clean-up and the injected default stylesheet |
| `PrintFormControls` | New, package-private, static utility | Replaces form controls with static print markup |
| `FallbackFontRuns` | New, package-private, static utility | Wraps characters the primary fonts cannot draw in a span using a bundled fallback font |
| `BundledFontResolver` | New, package-private | Subclass of Flying Saucer's `ITextFontResolver`; maps every requested family to a bundled face and enforces the minimum font size |
| `BundledFontFiles` | New, package-private, static utility | Copies the bundled font files to a private, verified directory and returns their paths |
| `PageGeometry` | New, package-private, static utility | The two-pass layout and the 0.8 zoom / shrink-to-fit page import |
| `LocalOnlyUserAgent` | Changed | Explicit allowed directories; allows Flying Saucer's own default stylesheet by exact URL; PHI-safe logging |
| `ReplacedElementFactoryImpl` | Changed | Extends Flying Saucer's `ITextReplacedElementFactory`; loads only validated absolute file images itself, delegates everything else; keeps the scaled image |
| `ConvertToEdoc` | Changed | `renderPDF` calls `HtmlPdf`; explicit base and allowed directories; broader error handling; dead code removed |
| `Doc2PDF` | Changed | `renderWithFlyingSaucer` calls `HtmlPdf` |
| `HRMPDFCreator` (`hospitalReportManager`) | Changed | HTML branch throws `PDFGenerationException` instead of dereferencing `null` |
| `HRMUtil`, `PrintHRMReport2Action` (`hospitalReportManager`) | Changed | Handle the new checked exception |
| `EmailManager` (`managers`) | Changed | Throws `EmailSendingException` when the message PDF cannot be built |
| `InternalEDocConverter`, `EDocConverterInterface` | Deleted | wkhtmltopdf wrapper |
| Dead entry points listed under "Unused code" | Deleted | No production callers |

`HtmlPdf` and all helpers hold no mutable state (apart from the verified font directory,
which is set once) and are safe to call concurrently.

### Render pipeline

`HtmlPdf.render(html, context)`:

1. **Validate the context.** `baseDirectory` must be an absolute existing directory and one
   of `allowedDirectories`; no allowed directory may be a filesystem root. Otherwise throw
   `PDFGenerationException`.
2. **Clean** (`XhtmlCleanup`), on a Jsoup DOM:
   - remove `<script>` elements and all comment nodes (comments never render; an HTML
     comment containing `--` is not valid XML);
   - run `PrintFormControls` (next section);
   - add `alt=""` to `<img>` without one;
   - ensure a `<head>` exists and insert, as its **first** child,
     `<style id="carlos-pdf-defaults">` containing the form-control styles and the `@page`
     rule for the current pass (see "Output geometry"). Being first, author rules of equal
     specificity (for example a class that sets an input's width or font) still win; the
     `@page` rule wins anyway because its declarations are `!important`;
   - serialise with XML syntax, XHTML escaping, UTF-8, pretty-printing off.

   This merges the two current routines (`Doc2PDF` removed comments; `ConvertToEdoc`
   rewrote them) and replaces `ConvertToEdoc.prepareDocumentForFlyingSaucer`. A
   `<!DOCTYPE html>` added by `ConvertToEdoc.parseDocument` is kept, so standards mode is
   used, as with wkhtmltopdf.
3. **Fallback fonts** (`FallbackFontRuns`), on the same DOM before serialising (see "Fonts").
4. **Lay out and render** (`PageGeometry`, see "Output geometry"). Each pass builds a
   renderer with `LocalOnlyUserAgent.createRestrictedRenderer(context)` and a new
   `BundledFontResolver`; print mode on, interactive off, smoothing threshold 0. For
   `VALIDATED_LOCAL_FILES` it installs `ReplacedElementFactoryImpl`, otherwise Flying
   Saucer's `ITextReplacedElementFactory`. It calls
   `setDocumentFromString(xhtml, baseDirectory.toUri().toString())`, `layout()` and
   `createPDF(buffer, true)`.
5. **Place on Letter** (`PageGeometry`): flatten any AcroForm fields, import each page onto
   a 612 × 792 pt page scaled by the zoom, and copy the source PDF's `/Title`.
6. **Check.** The bytes must start with `%PDF-`; PDFBox must load them with at least one
   page; every page must be 612 × 792 pt; the document must have no `/AcroForm`. Only then
   are the bytes returned.

Any `Exception` or `LinkageError` (for example `NoClassDefFoundError`) is caught and
rethrown as `PDFGenerationException("HTML could not be converted to PDF", cause)`. No other
`Error` is caught. No partial bytes are ever returned.

### Form controls (`PrintFormControls`)

Applied to every render, both image sources. Each replacement copies the original
element's `id`, `class`, `style`, `title`, `dir` and `lang`, adds
`class="carlos-pdf-control carlos-pdf-<kind>"` and `data-input-type="<type>"`, and keeps
document order. Text in replacements is ordinary text, so it gets the fallback-font step.

| Original | Replacement |
|---|---|
| `<input>` of type `text`, `email`, `tel`, `number`, `date`, `time`, `datetime-local`, `month`, `week`, `url`, `search`, `color`, an unknown type, or no type | `<span>` containing the `value` attribute text |
| `<input type="checkbox">` | `<span>` containing ☒ (U+2612) if `checked`, else ☐ (U+2610) |
| `<input type="radio">` | `<span>` containing ◉ (U+25C9) if `checked`, else ○ (U+25CB) |
| `<input type="password">` | `<span>` containing eight • (U+2022) if the value is non-empty, else empty; the length is not revealed |
| `<input>` of type `hidden`, `submit`, `button`, `reset`, `image`, `file` | removed |
| `<button>` | removed |
| `<select>` | `<span>` containing the text of the selected `<option>`; for `multiple`, the selected options joined with ", "; if none is selected, the first option (single) or nothing (multiple) |
| `<textarea>` | `<div>` containing the text, newlines preserved |

Styles in `carlos-pdf-defaults` (first in `<head>`, so author CSS by `id` or `class` still
applies and wins at equal specificity; author element-type selectors such as
`input[type=text]` no longer match — `form/annualStyle.css` has none):

```css
.carlos-pdf-control { display: inline-block; vertical-align: baseline;
  font-family: sans-serif; font-size: 10pt; }
.carlos-pdf-text, .carlos-pdf-password, .carlos-pdf-select {
  border: 1px solid #000; padding: 1px 2px; min-height: 1.2em;
  white-space: pre-wrap; overflow-wrap: anywhere; }
.carlos-pdf-textarea { display: inline-block; border: 1px solid #000; padding: 1px 2px;
  white-space: pre-wrap; overflow-wrap: anywhere; }
```

Per element, `PrintFormControls` also sets `min-width` from the `size` attribute (default
20) as `size × 0.55em` for text-like spans, and `min-width: cols × 0.55em` and
`min-height: rows × 1.2em` for textareas (defaults `cols=20`, `rows=2`), unless the element
has an inline `width`/`height`. Replacements grow to fit their content; values are never
clipped. This differs from wkhtmltopdf, which clipped long values at the field width, and
is intentional: a clipped value is hidden clinical data.

Deliberate differences from wkhtmltopdf, to be listed in the PR: buttons and image inputs
no longer print; long values wrap instead of being clipped; passwords print as eight
bullets.

### Output geometry (`PageGeometry`)

Constants: Letter `612 × 792 pt`; margin `M = 28.3465 pt` (10 mm); default zoom `z = 0.8`.

1. **Pass 1.** Inject
   `@page { size: 765pt 990pt !important; margin: 35.4331pt !important }` (Letter and
   10 mm divided by 0.8). Render with `ITextRenderer.setScaleToFit(true)`, so content wider
   than the page widens the page instead of being clipped. Read the first page's width `W`.
2. **If `W ≤ 765.5 pt`**, use pass 1 with `z = 0.8`.
3. **Otherwise**, compute `C = W − 2M/0.8`, `P = C / (1 − 2M/612)`, `z = 612 / P`. Pass 2:
   inject `@page { size: P pt (792/z) pt !important; margin: (M/z) pt !important }` and
   render without scale-to-fit. Use pass 2 with that `z`.
4. **Place.** Flatten AcroForm fields with OpenPDF `PdfStamper.setFormFlattening(true)`
   (defensive: `PrintFormControls` leaves none). Create a 612 × 792 pt document with no
   margins; for each page, `getImportedPage` and `addTemplate(page, z, 0, 0, z, 0,
   792 − h × z)` where `h` is the source page height. Copy `/Title`.

`!important` makes the injected `@page` win over any author `@page`, so paper and margins
never depend on the document, the JVM locale or `LANG`. Several form JSPs declare `@page`
(for example `formrourke2017p1.jsp`, `formannualmaleV2.jsp`); wkhtmltopdf ignored those
too.

**Minimum font size.** `BundledFontResolver` raises `FontSpecification.size()` (in dots) to
at least `10 × dotsPerPixel` (`10 × 20 = 200`), so text below 10 CSS px prints as 10 px
before zoom: 6 pt on paper, as with wkhtmltopdf.

**Media.** Flying Saucer applies `@media print` and not `@media screen`, as wkhtmltopdf
does with `printMediaType=true`. Unchanged; covered by a test.

**What import drops.** Imported pages keep text (extractable), vector graphics, images and
embedded fonts. They drop link annotations, bookmarks and tagged structure. wkhtmltopdf
kept links; no CARLOS consumer of these PDFs (fax, print, email attachment, document
store) uses them.

Measured with a prototype of exactly these rules: page size 612 × 792 pt and content edges
within 1 pt of wkhtmltopdf for 400, 850, 1000 and 1400 px wide pages; identical page counts
on all five comparison fixtures; 8 px text printed at 6 pt as with wkhtmltopdf.

### Fonts

**Fixed mapping** (`BundledFontResolver`), identical to JasperReports'
(`src/main/resources/fonts/carlos-jasper-fonts.xml`). Families are registered under exact
names (Flying Saucer matches family names case-sensitively):

| Registered family | Files (all `Identity-H`, embedded) |
|---|---|
| `CARLOS Sans` | `net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf`, `-Bold`, `-Oblique`, `-BoldOblique` |
| `CARLOS Serif` | `net/sf/jasperreports/fonts/dejavu/DejaVuSerif.ttf`, `-Bold`, `-Italic`, `-BoldItalic` |
| `CARLOS Mono` | `fonts/liberation/LiberationMono-Regular.ttf`, `-Bold`, `-Italic`, `-BoldItalic` |
| `CARLOS CJK` | `fonts/wqy/wqy-microhei.ttc,0` |
| `CARLOS Symbol` | `fonts/symbola/Symbola_hint.ttf` |

`resolveFont(SharedContext, FontSpecification)` takes the **first** family in the CSS list,
lower-cased with quotes and whitespace trimmed, and replaces the whole list with one
registered family:

| First family | Uses |
|---|---|
| exactly `carlos cjk` / `carlos symbol` (set only by `FallbackFontRuns`) | `CARLOS CJK` / `CARLOS Symbol` |
| `monospace`, or contains `courier`, `mono`, `consolas` or `lucida console` | `CARLOS Mono` |
| `serif`; or contains `times`, `georgia`, `garamond`, `cambria`, `palatino` or `book antiqua`; or ends with ` serif` and does not contain `sans` (for example `DejaVu Serif`, `Liberation Serif`) | `CARLOS Serif` |
| anything else (`sans-serif`, Arial, Helvetica, Verdana, Calibri, unknown names) | `CARLOS Sans` |

Flying Saucer's initial `font-family` is `serif`, as in browsers, so text with no font set
uses DejaVu Serif. Its default stylesheet gives `pre`, `tt`, `code`, `kbd`, `samp`
`monospace` and form controls `sans-serif`. On Debian, where the only host font package is
`fonts-dejavu-core`, wkhtmltopdf already used DejaVu Serif and Sans, so text faces do not
change on production servers; monospace changes from DejaVu Sans Mono to Liberation Mono,
as in JasperReports. Bold, italic and bold-italic select the matching registered faces
(verified); `CARLOS CJK` and `CARLOS Symbol` have one face each.

**Fallback-font step** (`FallbackFontRuns`), mirroring `PdfFonts`' rules for OpenPDF text.
For each text node outside `<style>` and `<title>`, each code point that is not whitespace
and not a format or variation-selector character, and that is not drawable by **all**
three primary regular faces (DejaVu Sans, DejaVu Serif, Liberation Mono), is assigned the
first face that can draw it: DejaVu Sans, then WenQuanYi Micro Hei, then Symbola. Maximal
runs with the same face are wrapped in `<span style="font-family:'CARLOS Sans'">` (or
`'carlos cjk'`, `'carlos symbol'`). A code point no face can draw is replaced with U+FFFD,
which DejaVu Sans draws, so the gap is visible; a DEBUG log records only the count. The
same input always produces the same spans.

Coverage, checked with OpenPDF `BaseFont.charExists`:

| Face | Accented Latin, Greek, Cyrillic, math, units, arrows, punctuation | ✓ ☐ ☒ ◉ ○ • | Arabic | CJK, kana, Hangul |
|---|---|---|---|---|
| DejaVu Sans | yes | yes | yes | no |
| DejaVu Serif | yes | no | no | no |
| Liberation Mono | yes | no | no | no |
| WenQuanYi Micro Hei | – | – | – | yes (Han, kana, Hangul, Bopomofo, CJK punctuation, fullwidth) |

So ✓, Arabic and similar characters in serif or monospace text are drawn in DejaVu Sans,
and CJK in WenQuanYi.

**Font files on disk** (`BundledFontFiles`). Flying Saucer's `addFont` reads a filesystem
path (its `BaseFont` overload re-reads the path to extract names), and DejaVu ships inside
the `jasperreports-fonts` jar (compile scope, already used by `PdfFonts`). On first use:

- create a directory with `Files.createTempDirectory(tmpdir, "carlos-pdf-fonts-")`, owner
  read/write/execute only where POSIX permissions are supported. It is under
  `java.io.tmpdir` but **not** under `<tmpdir>/carlos-temp`, which
  `ApplicationTempPurgeJob` deletes after 24 h. On Debian `java.io.tmpdir` is
  `/var/lib/carlos-emr/catalina/temp` (in `ReadWritePaths`, not aged by tmpfiles); on
  Podman it is the `/tomcat-temp` volume.
- copy the 14 files from the classpath and record each file's size and SHA-256.

Before each render, check that every file still exists with its recorded size; if not,
re-extract into a new directory. Each file's SHA-256 is verified when it is copied. A
missing classpath resource fails the render with `PDFGenerationException`; nothing is
substituted. Measured cost: first render about 1.2 s while fonts load, later renders about
130 ms per pass.

### Resource loading and security (`LocalOnlyUserAgent`)

- **Explicit allowed directories.** `createRestrictedRenderer(RenderContext)` passes the
  context's `allowedDirectories`. The implicit roots are removed: the base URL's directory,
  the JVM working directory, and the static `java.io.tmpdir` and `catalina.base/work`
  roots. Callers pass exactly what they need (below). A `file:` URI is allowed only if its
  canonical path is inside one of them.
- **Default stylesheet.** Exactly one extra URI is allowed: Flying Saucer's default
  stylesheet, computed once as Flying Saucer computes it
  (`Configuration.valueFor("xr.css.user-agent-default-css") + "XhtmlNamespaceHandler.css"`
  resolved with `Class.getResource` on `XhtmlCssOnlyNamespaceHandler`), matched by exact
  string equality in both `resolveAndOpenStream` and `openStream`. `setBaseURL` ignores that
  URI silently instead of logging a WARN. Every other `jar:` URI (including other entries in
  the same jar and `jar:http:`), `http:`, `https:`, `ftp:`, protocol-relative and
  out-of-directory `file:` request stays blocked.
- **`data:` URIs** stay allowed (inline images).
- **SVG.** An SVG reference to an outside resource makes Batik throw (Flying Saucer gives it
  no document URL), so the render fails with `PDFGenerationException` and nothing is
  fetched (tested with a local HTTP server).
- **XXE.** CARLOS's path parses through Flying Saucer's `XMLResource` SAX path and entity
  resolver; zero requests on 10.0.6 and 10.7.0. 10.7.0's `DocumentBuilderFactory`
  hardening covers paths CARLOS does not reach.
- **PHI-safe logging.** Blocked requests are logged with the scheme and, for network
  schemes, the host only. Blocked `file:` requests log no path (file names can carry patient
  identifiers). No message includes HTML content. Flying Saucer 10.7.0's `Html5Support`
  warnings name only tags and CSS properties.

**Callers' contexts:**

| Caller | `baseDirectory` | `allowedDirectories` | `imageSource` |
|---|---|---|---|
| `ConvertToEdoc` | webapp root: `realPath` if set, else `ServletContext.getRealPath("/")` from `ContextLoader.getCurrentWebApplicationContext()` (CARLOS's `OscarSpringContextLoader` extends `ContextLoaderListener`) | webapp root; `CarlosProperties.getEformImageDirectory()` if configured; `java.io.tmpdir`; `catalina.base/work` if set | `VALIDATED_LOCAL_FILES` |
| `Doc2PDF` | webapp root from `getFileBaseUrl(request)` (today's base) | webapp root; `java.io.tmpdir`; `catalina.base/work` if set | `GUARDED_USER_AGENT` |

If the webapp root cannot be resolved, the render fails (`ConvertToEdoc` returns `null`;
`Doc2PDF` sends its existing error response).

### `ReplacedElementFactoryImpl`

Extends Flying Saucer's `ITextReplacedElementFactory`. For `<img>` whose `src` is an
absolute filesystem path (as produced by `ConvertToEdoc.translatePaths`), it loads the file
as today (path containment is enforced by `translatePaths`/`validateResourcePaths`), keeps
the image returned by `FSImage.scale(width, height)`, and returns an `ITextImageElement`.
Every other element and every other `src` (`data:`, relative, `.svg`) goes to `super`, so
it loads through `LocalOnlyUserAgent`. The `input` branch is removed (no inputs remain).

### Errors

| Caller | After |
|---|---|
| `HtmlPdf.render` | Throws `PDFGenerationException` for any failure (see "Render pipeline"); never returns partial bytes |
| `ConvertToEdoc.execute` | `tidyDocument`, rendering and saving all inside one `try`; catches `PDFGenerationException`, `IOException` and `RuntimeException`; logs a fixed message, the `LogSafe`-sanitised file name and the cause class; returns `null` (contract unchanged) |
| `FormsManagerImpl.renderForm`, `FaxDocumentManagerImpl.getFormFaxDocument` | Unchanged: already turn `null` into `PDFGenerationException` naming the form |
| `HRMPDFCreator.printPdf` | Declares `throws PDFGenerationException`; the HTML branch throws it when `ConvertToEdoc` returns `null` |
| `HRMUtil.renderHRM` | Rethrows as `PDFGenerationException` naming the HRM report (it already declares the type) |
| `EctConsultationFormRequestPrintAction22Action` | Unchanged: its `execute` already catches `PDFGenerationException` (line ~343) |
| `PrintHRMReport2Action` | Adds `PDFGenerationException` to its existing error handling (same error response as its `IOException` path) |
| `EmailManager.createMessageAttachment` | Declares and throws `EmailSendingException("Encrypted message could not be converted to PDF")` when the path is `null`; `encryptEmail` already declares it |
| `Doc2PDF.GetPDFBin`, `PrintPDFFromHTMLString` | Unchanged: they catch `Exception` and return `null` / send their error response |
| `MsgAttachPDF2Action` | Unchanged: already handles a `null` PDF |

## Removals and Packaging

- **`pom.xml`:**
  - Flying Saucer `10.0.6` → `10.7.0` (keep the `com.github.librepdf:openpdf` exclusion).
  - Remove `com.github.openosp:ultrabuk-htmltopdf-java`.
  - Remove `com.github.librepdf.openpdf:openpdf-html` (also drops `neko-htmlunit`).
  - Remove the `jna.version` property and the `net.java.dev.jna:jna` dependency-management
    pin (only ultrabuk used JNA; nothing imports `com.sun.jna`).
  - Update the repositories comment that lists ultrabuk and openpdf-html as vendored.
- **`local_repo/`:** delete `com/github/openosp/ultrabuk-htmltopdf-java` (33 MB) and
  `com/github/librepdf/openpdf/openpdf-html`.
- **`dependencies-lock.json`:** regenerate with the project's lock plugin (`make lock`,
  `se.vandmo:dependency-lock-maven-plugin`), never by hand. The forward merge to `develop`
  regenerates it there too.
- **Java:** delete `InternalEDocConverter`, `EDocConverterInterface`,
  `ConvertToEdoc.fallbackRender`, `ConvertToEdoc.prepareDocumentForFlyingSaucer`,
  `ConvertToEdoc.normalizeHtmlCommentsForXml` (if no other caller remains),
  `DEFAULT_WKHTMLTOPDF_COMMAND`, `DEFAULT_WKHTMLTOPDF_ARGS`, the wkhtmltopdf comments
  ("htmltopdf tools require String input"), `ConvertToEdoc.from(EFormData)`,
  `ConvertToEdoc.from(FormTransportContainer)`, `ConvertToEdoc.saveAsTempPDF(String,
  String)`, `EformDataManager(Impl).saveEformDataAsEDoc`,
  `FormsManager(Impl).saveFormDataAsEDoc` and its test case
  (`FormsManagerUnitTest` around line 318).
- **`debian/control`:** remove `libxrender1` and its comment. Its comment names wkhtmltopdf
  as the only user; Chromium's dependency list does not include it (and `libcairo2` pulls it
  in where needed); no `debian/tests` file mentions it. Existing installs keep it as an
  auto-removable package.
- **Settings:** delete the orphaned wkhtmltopdf comment block in
  `src/main/resources/carlos.properties`, and `WKHTMLTOPDF_COMMAND` / `WKHTMLTOPDF_ARGS`
  from `.devcontainer/development/config/shared/volumes/carlos.properties`.
- **CI:**
  - `.github/workflows/dependency-submission.yml`: drop ultrabuk from the failure-recovery
    comment and the "formerly-JitPack" comment; remove `~/.m2/repository/com/github/openosp`
    from the cache eviction; keep `com/github/librepdf` (OpenPDF core stays vendored).
  - `.github/actions/dev-container/action.yml`: remove the `com/github/openosp` eviction.
  - The repository's `.claude/settings.json` denies Claude writes under `.github/`. If the
    rule blocks the edit, the exact patch goes to the maintainer instead of working around
    it.
- **Docs:**
  - `docs/pdf-font-policy.md`: add the HTML engine as a third engine (fixed mapping,
    fallback-font step, tests).
  - `CLAUDE.md`, "PDF and Report Fonts": one line pointing HTML-to-PDF at `HtmlPdf`.
  - `docs/pdf-dependency-consolidation-plan.md`: mark ultrabuk and openpdf-html removed.
  - `utils/readme.txt`: remove the wkhtmltopdf section.

## Testing

Conventions: JUnit 5 under `src/test/java/io/github/carlos_emr/carlos/documentManager/`
(or the changed class's package), `should<Action>_<context>()`. Tests that need neither
Spring nor a database are tagged `unit` and named `*UnitTest`, as
`JasperBundledFontRenderingUnitTest` is. New Java files carry the CARLOS copyright header
and JavaDoc with `@since 2026-10-29` and no `@author`. Fixtures contain FAKE- names only.

| Test | Asserts |
|---|---|
| `LocalOnlyUserAgentUnitTest` (extended) | Default stylesheet URL opens and `setBaseURL` with it logs nothing; another entry in the same jar, `jar:http:`, `http:`, protocol-relative and out-of-directory `file:` return `null`; a root directory is rejected as an allowed directory; blocked `file:` logs contain no path; nothing depends on the working directory |
| `ReplacedElementFactoryImplUnitTest` | Absolute-path `<img>` with width and height gets the requested size; `data:` and `.svg` sources are delegated and render |
| `XhtmlCleanupUnitTest` | Scripts and comments removed (including a comment with `--`); `alt` added; `carlos-pdf-defaults` is the first child of `<head>`; an author class rule setting a control's width beats the defaults; output parses with the JDK XML parser |
| `PrintFormControlsUnitTest` | Every row of the form-control table, including copied `id`/`class`/`style`, `size`/`cols`/`rows` sizing, multi-select, no selection, password not revealing length |
| `FallbackFontRunsUnitTest` | Only characters missing from a primary face are wrapped; face order DejaVu Sans → WenQuanYi → Symbola; U+FFFD for undrawable; `<style>`/`<title>` untouched; output identical across runs |
| `BundledFontResolverUnitTest` | Each mapping row; exact registered names; bold/italic faces; minimum size 200 dots |
| `BundledFontFilesUnitTest` | Directory outside `carlos-temp`, owner-only permissions where supported, SHA-256 verified; a deleted or truncated file is re-extracted; a missing classpath font fails clearly |
| `PageGeometryUnitTest` | 400, 850, 1000, 1400 px pages: 612 × 792 pt, content left/top edges at 28 ± 1 pt, nothing clipped; author A4-landscape `@page` ignored; same result with `-Duser.country=GB` and with no country; 8 px text prints at 6 pt; `@media print` applies and `@media screen` does not; `/Title` copied |
| `HtmlPdfRenderUnitTest` | For each fixture: valid PDF; PDFBox text contains the expected strings (accents, ≥, ✓, Chinese); every font embedded and from the bundled set (no base-14); no `<title>` or CSS text; no `/AcroForm`; expected page count |
| `HtmlPdfFormsUnitTest` | Forms fixture: every value present including CJK; ☒/☐ and ◉/○ correct; hidden values and the Annual hidden field names never appear; only the selected option prints; textarea keeps two lines; a date value is complete; text identical after `ConcatPDF` alone and after `ConcatPDF` + flatten |
| `HtmlPdfSsrfUnitTest` | Hostile HTML (`<link>`, `@import`, `url()` background, `@font-face`, `<img>` over `http:`, `jar:http:`, protocol-relative, `file:///etc/passwd`, SVG `xlink:href`) makes no request to a local `ServerSocket`; the outside-SVG case fails with `PDFGenerationException` |
| `ConvertToEdocUnitTest` (updated) | A linked stylesheet, a CSS `url()` background and `pdfMediaStylesheet` under the webapp root load when the working directory is outside it; blank HTML and render failures return `null` and log no HTML |
| `HRMPDFCreator`/`HRMUtil` tests | A `null` conversion surfaces as `PDFGenerationException` naming the report, never a `NullPointerException` |
| `EmailManager` test | A `null` message PDF surfaces as `EmailSendingException` |
| Existing `ConvertToEdocIntegrationTest`, `Doc2PDFIntegrationTest` | Updated for removed methods |
| Existing `EformDataManagerImplCreatePdfUnitTest`, `FormsManagerImplUnitTest`, `FaxDocumentManagerImplUnitTest`, `MsgAttachPDF2ActionUnitTest` | Run unchanged |

**Fixtures** (`src/test/resources/htmlpdf/`):

- Annual form pages, female and male, captured from the dev server with checked boxes,
  textarea text and the hidden fields present.
- A forms page with every control type (including CJK in a value).
- The demo HRM report and three synthetic HTML HRM reports: table-heavy, inline styles,
  with images (file, `data:` and remote).
- An encrypted-email message body.
- A Messenger letter.
- Wide pages (400, 850, 1000, 1400 px), an author `@page` page, small text, print/screen
  media rules.
- Edge-case markup: empty elements, self-closing source tags, misnested tags, entities.

All fixtures include `<!DOCTYPE html>` (as `ConvertToEdoc` adds one), so baselines are
standards-mode.

**Host-font independence:** add `HtmlPdfRenderUnitTest`, `HtmlPdfFormsUnitTest` and
`PageGeometryUnitTest` to the default list in `scripts/check-report-fonts.sh` (it runs
Surefire with `-Dtest`).

**One-off comparison (PR 1):** a script renders every fixture with wkhtmltopdf (vendored
jar, CARLOS's settings, `LANG=C.UTF-8` or `-Djna.encoding=UTF-8`, as production sets
`LANG=C.UTF-8` in `debian/carlos-emr.carlos-emr.service`) and with `HtmlPdf`, and attaches
side-by-side PNGs to PR 1. Acceptance per fixture: same page count, 612 × 792 pt pages,
content edges at 28 ± 1 pt, every value and checked box visible, no hidden value visible.
Differences are listed in the PR. The script is not kept in CI (wkhtmltopdf is removed in
PR 2).

**Manual checks on Tomcat:** in the devcontainer **and** with the webapp outside the
working directory (as on Debian):

- print an Annual form (female and male);
- preview and send a consultation fax with an Annual form and an HTML HRM report attached;
- fax an Annual form on its own;
- send an encrypted email;
- attach a Messenger PDF of a form;
- save a form as a document.

## Delivery

Two PRs, both from and to `release/2026.08`, both merged before 2026.08 ships:

1. **New engine.** Flying Saucer 10.7.0; `HtmlPdf` and its helpers; the
   `LocalOnlyUserAgent`, `ReplacedElementFactoryImpl` and error-path fixes; `Doc2PDF` and
   the `ConvertToEdoc` fallback use `HtmlPdf` (wkhtmltopdf still first, so today's fallback
   output also becomes correct). Tests, fixtures, the font-check script entry and the
   comparison images.
2. **Switch and removal.** `ConvertToEdoc` uses `HtmlPdf` only; deletions and every item in
   "Removals and Packaging".

Rules from `docs/release-process.md` and `CLAUDE.md`:

- Do not change the `-SNAPSHOT` version or the SCM tag.
- After each PR merges, a maintainer forward-merges `release/2026.08` into `develop`.
- During beta the release branch takes high-priority correctness and security fixes. Each
  PR body states the case: abandoned wkhtmltopdf with a published SSRF vulnerability and
  local file access on; today's Flying Saucer fallback printing CSS text, losing checkbox
  answers and printing hidden identifiers; the font policy gap. The 2026.08 release owner
  decides whether to accept it.
- Commits are not signed off by Claude; a maintainer confirms DCO on each PR with the
  repository's manual phrase.
- Each PR body includes the "Generated with Claude Code" line and references this spec.

## Risks

| Risk | Mitigation |
|---|---|
| A form or HRM report lays out differently from wkhtmltopdf despite the emulation | Side-by-side comparison with acceptance criteria in PR 1; small CSS fixes to form print markup are in scope; other differences are listed for the release owner |
| Real hospital HRM HTML is more varied than the fixtures (CSS beyond Flying Saucer's CSS 2.1, malformed markup) | Failures surface as an error naming the report, not a garbled PDF; new variants become fixtures |
| Remote images in HRM HTML no longer print | Intended: wkhtmltopdf fetched them, which is the SSRF the change removes; listed in the PR |
| Author CSS that targets `input`/`select`/`textarea` element types no longer matches the replacements | Replacements keep `id`, `class` and `style`; `annualStyle.css` has no such selectors; the comparison fixtures cover the Annual form |
| Two passes plus page import make each render slower | Measured ~130 ms per pass after warm-up; `ConvertToEdoc` is already serialised; fax batches are checked in the manual tests |
| Default-stylesheet fix changes today's `Doc2PDF` (Messenger) output | It restores the intended layout; covered by the render tests and the manual Messenger check |
| Font files deleted or altered in the temp directory | Private owner-only directory outside `carlos-temp`; size check before each render, re-extraction; SHA-256 verified on copy |
| Webapp root unavailable when `ConvertToEdoc` runs outside a request (HRM, email) | Resolved from the Spring web application context; if unavailable the render fails clearly |
| CI workflow edit blocked by the `.github/` write rule | Exact patch handed to the maintainer |

## Follow-ups (not in these PRs)

- `.devcontainer/development/Dockerfile:79` installs `libxrender1`, `libxi6`, `xfonts-75dpi`
  and `xfonts-base` for wkhtmltopdf. Remove them after a devcontainer Playwright run
  confirms Chromium does not need them.
- `carlos-podman`'s `templates/carlos.properties.j2` still sets `WKHTMLTOPDF_*` (harmless,
  unused); remove it in that repository.
- The OSCAR 19 import map keeps `WKHTMLTOPDF_*` as `deploy-owned` until carlos-ctl's test
  changes in its own repository.
