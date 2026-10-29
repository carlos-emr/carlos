# Design: Consolidate HTML-to-PDF on Flying Saucer and Remove wkhtmltopdf

**Date:** 2026-10-29 (fourth revision, after a full verification pass and a final review cycle)
**Status:** Design approved in conversation; written spec awaiting review
**Target:** `release/2026.08` (currently `2026.08.0-beta3-SNAPSHOT`), forward-merged to `develop`

## How this spec was verified

Every behavioural claim was checked against the code on `release/2026.08` and, where it
concerns rendering, by rendering documents with the real engines:

- **wkhtmltopdf 0.12.6.1** from the vendored `ultrabuk-htmltopdf-java` jar, with
  `InternalEDocConverter`'s exact settings, `LANG=C.UTF-8` (as the Debian service sets it),
  a fontconfig limited to `fonts-dejavu-core` (the only font package `carlos-emr` depends
  on) and, as input, the output of `ConvertToEdoc.tidyDocument`. This is the "production
  baseline".
- **Flying Saucer 10.0.6 and 10.7.0** through CARLOS's own `LocalOnlyUserAgent` and
  `ReplacedElementFactoryImpl`, and through an end-to-end prototype of this design.
- **PDFBox 3.0.8** for merging and flattening, as CARLOS uses it.

Three rounds were run: a verification pass (claims audit, prototype of the second revision
against 15 fixtures, about 80 adversarial documents) and a final review cycle (document
review, prototype of the third revision against the full test list). Defects found are fixed
here. Items marked *(tested)* were re-run with the fix; the pixel budget under "Images" is
the one rule that is designed but not yet load-tested, and PR 1 must load-test it.

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
  transport-level URL guard. `ConvertToEdoc`'s DOM clean-up removes remote `<img>`/`<link>`
  and blanks remote CSS `url()`, but `<iframe>`, `<object>`, `<embed>`, `srcset` and
  string-form `@import "…"` reach wkhtmltopdf unchanged.
- It ships as a 33 MB vendored jar with a native library, needs `libxrender1` on the host
  and pulls in JNA. CARLOS's dependency audits (`docs/dependency-audit-2026-03-16.md`,
  `-03-17.md`) mark it "abandoned, replace".
- Its margin settings in `InternalEDocConverter` (`T`, `L`, `R`) are not valid libwkhtmltox
  object settings and are silently ignored (renders with and without them are
  pixel-identical). Real output is US Letter with 10 mm margins on all sides (measured).

### Flying Saucer, as CARLOS configures it today, cannot replace it

Items 1–4 also affect today's `Doc2PDF` output and today's `ConvertToEdoc` fallback.

1. **Default stylesheet blocked.** `LocalOnlyUserAgent` blocks every `jar:` URL, including
   Flying Saucer's own default stylesheet
   (`jar:file:…/flying-saucer-core-*.jar!/resources/css/XhtmlNamespaceHandler.css`). Every
   Flying Saucer PDF prints the page `<title>` and raw CSS text at the top, loses block and
   table layout and ignores forced page breaks.
2. **Images mis-sized or unreadable.** `ReplacedElementFactoryImpl` calls
   `fsImage.scale(width, height)` and discards the result, and it skips Flying Saucer's
   output-resolution scaling, so an `<img>` without `width`/`height` prints about 20 times
   too small (2400 dpi instead of 120 dpi, measured). Pages that draw state with unsized
   images (for example `formannualfemaleprint.jsp`'s `checkmark.gif`/`notChecked.gif`)
   would lose those marks. It also opens any absolute path with `new File(src)` (it read
   `/etc/passwd` in the prototype and failed only because the file is not an image), and it
   cannot load `data:` URIs.
3. **Fonts break the PDF font policy (#5046).** For no family, generic families and unknown
   names, Flying Saucer uses non-embedded base-14 Times, Helvetica and Courier. It has no
   per-glyph fallback: Chinese, Japanese and Korean text and symbols such as ✓ are silently
   dropped. Registering bundled fonts under the generic names does not override this.
4. **Form controls lose or leak data.** `ReplacedElementFactoryImpl` turns every `<input>`
   into a live AcroForm text field; Flying Saucer's default factory (used by `Doc2PDF`)
   handles only `input`, `img`, `svg` and `bookmark`. Compared with wkhtmltopdf, which prints
   ☒/☐, ◉/○, masked passwords and only the selected option:

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
   print on faxed and emailed PDFs. The fields are also live widgets: only
   `DocumentAttachmentManagerImpl.concatPDF` flattens them; the consultation print packet,
   save-as-document, email attachments, single form faxes and `HRMPDFCreator` ship them
   unflattened, and a PDFBox merge without flattening draws no checkbox or radio marks
   (measured).
5. **Geometry differs from wkhtmltopdf** (measured):

   | Property | wkhtmltopdf (CARLOS settings) | Flying Saucer default |
   |---|---|---|
   | Scale | 0.8 zoom: 1 CSS px = 0.60 pt, 1 pt = 0.80 pt | 1 px = 0.75 pt, 1 pt = 1 pt (25 % larger) |
   | Content wider than the page | shrunk uniformly to fit (1000 px → 0.555 pt/px, 1400 px → 0.396 pt/px; about 2× at most, then clipped) | clipped at the right margin (beyond ~720 px) |
   | Page size | always US Letter; author `@page` ignored | author `@page` obeyed; otherwise A4 or Letter from the JVM locale (Letter only for US, CA, MX; `LANG=C.UTF-8` gives A4) |
   | Margins | 10 mm all sides | 0.5 in, or author `@page` |
   | Minimum font size | 10 CSS px | none |
   | `line-height: normal` with DejaVu Sans | about 1.164 | about 24 % taller |
   | `thead` | repeated on every page | printed once |
   | `rowspan` past the end of its row group | clamped (HTML table model) | not clamped: following content is drawn over the cell |
   | Media | `print` | `print` (same) |

   The Annual form's `form/annualStyle.css` has 800–955 px containers, so Flying Saucer would
   clip its right side, and `formannualfemale.jsp` has a `rowspan="4"` over three rows.
6. **Stylesheets blocked on Debian installs.** `ConvertToEdoc.fallbackRender` passes a null
   base URL; Flying Saucer then uses the JVM working directory as the base, and
   `LocalOnlyUserAgent` only allows the base directory, `java.io.tmpdir` and
   `catalina.base/work`. `translateResourcePaths` rewrites `<link href>`, CSS `url()`,
   `background=` and the `pdfMediaStylesheet` link to absolute paths under the webapp root
   or the eForm image directory. On Debian the working directory is
   `/var/lib/carlos-emr/catalina` (`debian/assets/bin/carlos-emr-tomcat`:
   `cd "${CATALINA_BASE}"`, line ~200), the webapp is `/usr/share/carlos-emr/webapp/carlos`
   and eForm images are under `/var/lib/carlos-emr/CarlosDocument`, so all of those
   resources are blocked with only a WARN. The devcontainer and Podman keep the webapp under
   the working directory and hide this.
7. **No right-to-left support.** Flying Saucer 10.7.0 has no bidi reordering or Arabic
   shaping: Hebrew prints with its letters reversed, Arabic and Urdu as unjoined letters in
   reversed order, and `dir="rtl"` alignment is ignored. wkhtmltopdf prints them correctly.

### Failures that crash or are hidden today

- `HRMPDFCreator.printPdf` (HTML branch, line ~182) calls `Files.readAllBytes(null)` when
  `ConvertToEdoc` returns null. The `NullPointerException` escapes `HRMUtil.renderHRM`, which
  catches only `IOException`, into fax, consultation and attachment flows.
- `EmailManager.createMessageAttachment` (line ~516) calls `toString()` on a null path.
- `ConvertToEdoc.execute` calls `tidyDocument` outside its `try` (blank HTML throws
  `IllegalArgumentException`) and catches only `DocumentException` and `IOException`.
- `ConvertToEdoc` silently **removes** `<img>` elements in two places:
  `validateResourcePaths` (line ~1017) removes any whose path fails `validateLink`, which
  only accepts the extensions in `FileType` (`pdf, css, jpeg, png, gif, js, jpg`, line 103),
  so `.bmp`, `.tif`, `.svg` and `.webp` images disappear; `translatePaths` (lines ~905/911)
  removes any whose source is remote, `file:` or unresolvable.
- `translateResourcePaths` (line ~617) and `validateResourcePaths` collect their work in a
  `HashMap<List<String>, Element>` keyed by the list of candidate paths. When the same
  `src` appears more than once, equal keys overwrite each other and only the last `<img>`
  is translated and validated; the earlier ones keep their raw `src` and print nothing.
- An image of 10 000 × 10 000 pixels (a 316 KB PNG) exhausts a 1 GB heap while Flying Saucer
  decodes it, and two concurrent renders of one 25-megapixel RGBA PNG exhaust 512 MB.
  Production runs with `-XX:+ExitOnOutOfMemoryError` and heap dumps
  (`carlos-emr-tomcat`, lines 147–151), so such images would restart CARLOS and write a heap
  dump containing patient data. wkhtmltopdf decoded images in native memory.

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
| When Flying Saucer cannot render a document | Fail clearly with an error naming the document; no fallback to Chromium |
| Flying Saucer version | Upgrade 10.0.6 → 10.7.0 as the first step |
| Structure | One shared Flying Saucer pipeline; `ConvertToEdoc` and `Doc2PDF` become thin callers; their callers keep their contracts |
| Fonts | Every family prints in bundled DejaVu Sans, as production wkhtmltopdf does; bundled fallbacks only for characters DejaVu Sans lacks |
| Output geometry | Emulate wkhtmltopdf: Letter, 10 mm margins, 0.8 zoom, shrink-to-fit, 10 px minimum font, WebKit line height, repeated table headers, HTML row-span clamping |
| Shrink-to-fit limit | None (nothing is cut off); WARN when the zoom falls below 0.4 |
| Form controls | Printed as static text and marks; never as PDF form fields |
| Bad images | That image is replaced by a visible "[image unavailable]" box; the rest of the document prints; WARN without the path |
| Right-to-left text | Documents containing right-to-left letters fail clearly |
| Release | Land on `release/2026.08`; maintainers forward-merge to `develop` |

Flying Saucer 10.7.0 was checked before choosing it:

- LGPL 2.1 or later; licence files byte-identical to 10.0.6.
- Binary-compatible with the vendored OpenPDF core 3.0.3: all 204 OpenPDF members it
  references exist in 3.0.3 (inheritance followed). CARLOS's Flying Saucer classes compile
  against it unchanged.
- Same dependencies as 10.0.6 (`flying-saucer-core`, `openpdf`, `batik-codec`; Batik's
  bridge classes, needed for inline SVG, already come in through JasperReports).
- SSRF probe (11 vectors) and XXE probe (external entity, parameter entity, external DTD):
  zero requests. Author `@font-face` (local or remote) and `<base href>` (`http:` or
  `file:`) cause no request or file read; wkhtmltopdf fetched both.
- APIs used below exist and are overridable where needed: `ITextRenderer.setScaleToFit`,
  `ITextRenderer(float, int, ITextOutputDevice, ITextUserAgent, FontResolver)`,
  `ITextFontResolver.resolveFont(SharedContext, FontSpecification)` and
  `addFont(String, String, String, boolean, String)`, the `FontSpecification` record,
  `ITextUserAgent.getImageResource(String)` (public, non-final), `Configuration.valueFor`,
  `XhtmlCssOnlyNamespaceHandler.getDefaultStylesheet`.

## Goals

- Every non-scripted HTML-to-PDF render goes through one Flying Saucer pipeline.
- For `ConvertToEdoc` (forms, HRM, email, fax): output matches what production wkhtmltopdf
  produces today, within the acceptance criteria under "Testing".
- Output is deterministic: the same HTML gives the same fonts and layout on every server,
  whatever its locale, working directory or installed fonts.
- Every font in the output is bundled in the WAR and embedded; no text is silently dropped
  or garbled (unsupported characters print as a visible U+FFFD; right-to-left documents
  fail clearly).
- Form answers print; hidden values, passwords and buttons never do; no PDF form fields.
- Image problems are visible, never silent, and never exhaust the heap.
- The SSRF guard covers every path and never depends on the working directory.
- Every failure reaches the caller as a clear error or `null` per the caller's existing
  contract; none escapes as an unhandled `NullPointerException`.
- The WAR, the Debian package and the build no longer carry wkhtmltopdf, its native
  library, `libxrender1`, JNA, `openpdf-html` or `neko-htmlunit`.

## Non-Goals

- Changing the Chromium eForm renderer (`EFormBrowserPdfService`).
- Changing PDFs drawn directly with OpenPDF (cover pages, consultations, labs, HRM text and
  image reports) or JasperReports templates.
- Moving callers onto a new API, or deleting `ConvertToEdoc`/`Doc2PDF`.
- Removing `ConvertToEdoc`'s class-level `synchronized` methods or its static `realPath`.
- Upgrading the vendored OpenPDF core (stays 3.0.3).
- Widening `ConvertToEdoc`'s `FileType` extension list.
- Right-to-left layout (see "Known limitations").
- Changing the OSCAR 19 import map: carlos-ctl's own test suite, which CI runs against this
  tree (`debian-python-tests.yml`, `CARLOS_SRC`), asserts `WKHTMLTOPDF_COMMAND` is
  `deploy-owned` (`carlos-ctl/tests/test_props.py:210-211`), and neither disposition carries
  the value over anyway.
- Devcontainer system packages (`.devcontainer/development/Dockerfile:79`); see
  "Follow-ups".

## Design

### Components

All new and changed classes are in `io.github.carlos_emr.carlos.documentManager` unless
noted. Names follow `docs/architecture/layer-names.md`: static utilities use a domain noun;
the font class makes one decision through a priority chain, so it is a `*Resolver`. The
table shows the end state after PR 2; "Delivery" shows the split.

| Class | Status | Responsibility |
|---|---|---|
| `HtmlPdf` | New, public, static utility | The only entry point: `render(String html, RenderContext context)` returns PDF bytes or throws `PDFGenerationException` |
| `HtmlPdf.RenderContext` | New record | `baseDirectory` (`Path`, absolute, required) and `allowedDirectories` (`List<Path>`, non-empty, no filesystem root, must contain `baseDirectory`) |
| `XhtmlCleanup` | New, package-private, static utility | All DOM preparation (steps 2a–2m) and serialisation |
| `PrintFormControls` | New, package-private, static utility | Replaces form controls with static print markup |
| `PrintImages` | New, package-private, static utility | Checks every `<img>` and inline `<svg>` before layout, holds the image pixel budget, and replaces unusable images with the "[image unavailable]" box |
| `FallbackFontRuns` | New, package-private, static utility | Wraps characters DejaVu Sans cannot draw in a span using a bundled fallback font |
| `BundledFontResolver` | New, package-private | Subclass of Flying Saucer's `ITextFontResolver`; every family resolves to a bundled face; enforces the minimum font size |
| `BundledFontFiles` | New, package-private, static utility | Extracts and verifies the bundled font files in one private directory |
| `PageGeometry` | New, package-private, static utility | Two-pass layout, zoom/shrink-to-fit page import onto Letter |
| `LocalOnlyUserAgent` | Changed | Explicit allowed directories; allows Flying Saucer's own default stylesheet by exact URL; image caps and failure handling; PHI-safe logging |
| `ReplacedElementFactoryImpl` | **Deleted** | Replaced by Flying Saucer's own factory with all images loading through `LocalOnlyUserAgent` |
| `ConvertToEdoc` | Changed | `renderPDF` calls `HtmlPdf`; per-element path translation; encoded `file:` URIs; marks unusable images instead of removing them; one webapp-root lookup; broader error handling; dead code removed |
| `Doc2PDF` (`util`) | Changed | `renderWithFlyingSaucer` calls `HtmlPdf` |
| `HRMPDFCreator`, `HRMUtil`, `PrintHRMReport2Action` (`hospitalReportManager`) | Changed | A failed HTML conversion surfaces as `PDFGenerationException` |
| `EmailManager` (`managers`) | Changed | Throws `EmailSendingException` when the message PDF cannot be built |
| `InternalEDocConverter`, `EDocConverterInterface` | Deleted | wkhtmltopdf wrapper |
| Dead entry points listed under "Unused code" | Deleted | No production callers |

`HtmlPdf` and its helpers hold no mutable state apart from the verified font directory and
the JVM-wide image pixel budget, and are safe to call concurrently (24 mixed renders on
8 threads gave identical bytes and no errors, *tested*).

### Render pipeline

`HtmlPdf.render(html, context)`:

1. **Validate input.** `html` must not be null or whitespace-only, and the context rules
   above must hold; otherwise throw `PDFGenerationException`. Markup with no visible content
   prints one blank page.
2. **Prepare the DOM** (`XhtmlCleanup`), parsing with Jsoup, in this order:
   - a. remove `<script>` elements and all comment nodes (comments never render; an HTML
     comment containing `--` is not valid XML);
   - b. from text nodes and from the `value` attributes of form controls, remove soft
     hyphens (U+00AD, which Flying Saucer prints as visible hyphens) and invisible bidi
     formatting characters (U+200E, U+200F, U+061C, U+202A–U+202E, U+2066–U+2069);
   - c. `PrintFormControls` (see "Form controls");
   - d. `PrintImages` (see "Images"); this acquires the document's pixel-budget permits,
     which are released in a `finally` after step 5;
   - e. rewrite an inline `style` that sets `position: fixed` to `position: absolute`
     (Flying Saucer repeats fixed elements on every page, overprinting body text);
   - f. on `<ol type="…">` without an inline `list-style-type`, set `list-style-type`
     (`a` → `lower-alpha`, `A` → `upper-alpha`, `i` → `lower-roman`, `I` → `upper-roman`,
     `1` → `decimal`);
   - g. **clamp `rowspan`:** for each `td`/`th` whose `rowspan` is 0 or larger than the
     number of rows from its row to the end of its row group (`thead`, `tbody`, `tfoot`, or
     the table), set it to that number, as the HTML table model does (*tested* on
     `formannualfemale.jsp`: without it, the following table is drawn over the cell);
   - h. add class `carlos-pdf-paginate` to each `<table>` that has a direct `<thead>` child
     and no cell of its own with a `rowspan` other than 1 (Flying Saucer's pagination
     breaks row-span heights, *tested*);
   - i. **right-to-left check:** if any text node outside `<style>` and `<title>` contains a
     code point whose `Character.getDirectionality` is `RIGHT_TO_LEFT` or
     `RIGHT_TO_LEFT_ARABIC`, throw
     `PDFGenerationException("HTML could not be converted to PDF: right-to-left text is not supported")`;
   - j. add `alt=""` to `<img>` without one;
   - k. ensure a `<head>` exists and insert `<style id="carlos-pdf-defaults">` (see
     "Injected stylesheet") as its **first** child, so author rules of equal specificity
     still win, while its `@page` rule wins anyway because it is `!important` (*tested*:
     with the defaults first, an author A4-landscape `@page` lost and an author class font
     size won);
   - l. `FallbackFontRuns` (see "Fonts");
   - m. serialise with XML syntax, XHTML escaping, UTF-8 and pretty-printing off. A
     `<!DOCTYPE html>` added by `ConvertToEdoc.parseDocument` is kept, so standards mode is
     used, as with wkhtmltopdf.

   This replaces `ConvertToEdoc.prepareDocumentForFlyingSaucer` and the Jsoup steps in
   `Doc2PDF.renderWithFlyingSaucer`.
3. **Lay out and render** (`PageGeometry`, see "Output geometry"). Each pass builds its
   renderer with
   `LocalOnlyUserAgent.createRestrictedRenderer(context, new BundledFontResolver(...))`,
   which uses `ITextRenderer(float, int, ITextOutputDevice, ITextUserAgent, FontResolver)`
   with CARLOS's existing 26.666666 dots per point and 20 dots per pixel. Print mode on,
   interactive off, smoothing threshold 0, Flying Saucer's own
   `ITextReplacedElementFactory`, `setDocumentFromString(xhtml,
   baseDirectory.toUri().toString())`, `layout()`, `createPDF(buffer, true)`.
4. **Place on Letter** (`PageGeometry`): flatten any AcroForm fields, import each page onto
   a 612 × 792 pt page scaled by the zoom, and copy the source PDF's `/Title`.
5. **Check.** The bytes must start with `%PDF-`; PDFBox must load them with at least one
   page; every page must be 612 × 792 pt; there must be no `/AcroForm`. Only then are the
   bytes returned.

Any `Exception` or `LinkageError` (for example `NoClassDefFoundError`) is caught and
rethrown as `PDFGenerationException("HTML could not be converted to PDF", cause)`. Other
`Error`s are not caught. No partial bytes are ever returned.

### Form controls (`PrintFormControls`)

Each replacement copies the original element's `id`, `class`, `style`, `title`, `dir` and
`lang`, adds `class="carlos-pdf-control carlos-pdf-<kind>"` and
`data-input-type="<type>"`, and keeps document order. Text in replacements is ordinary
text, so the fallback-font step and the right-to-left check apply to it.

| Original | Replacement |
|---|---|
| `<input>` of type `text`, `email`, `tel`, `number`, `date`, `time`, `datetime-local`, `month`, `week`, `url`, `search`, `color`, an unknown type, or no type | `<span>` (kind `text`) containing the `value` attribute with CR and LF removed (as browsers do for these types) |
| `<input type="checkbox">` | `<span>` (kind `checkbox`) containing ☒ (U+2612) if the `checked` attribute is present, else ☐ (U+2610) |
| `<input type="radio">` | `<span>` (kind `radio`) containing ◉ (U+25C9) if `checked` is present, else ○ (U+25CB) |
| `<input type="password">` | `<span>` (kind `password`) containing eight • (U+2022) if the value is non-empty, else empty; the length is not revealed |
| `<input>` of type `hidden`, `submit`, `button`, `reset`, `image`, `file` | removed |
| `<button>` | removed |
| `<select>` | `<span>` (kind `select`) with the text of the `<option>` that has `selected`; for `multiple`, all selected options joined with ", "; if none is selected, the first option (single) or nothing (multiple); options inside `<optgroup>` count |
| `<textarea>` | `<div>` (kind `textarea`) containing the text, newlines preserved |
| `<progress>` | `<span>` (kind `text`) containing `value/max` (`max` defaults to 1); nothing when `value` is absent (indeterminate) |
| `<meter>` | `<span>` (kind `text`) containing `value` |

`checked` and `selected` follow HTML boolean-attribute rules: present means on, whatever
the value (so `checked="false"` is checked, as in browsers and wkhtmltopdf).

Per element, `PrintFormControls` sets `min-width: size × 0.55em` for text-like spans
(default `size` 20) and `min-width: cols × 0.55em; min-height: rows × 1.2em` for textareas
(defaults 20 and 2), unless the element has an inline `width`/`height`. Replacements grow to
fit their content; values are never clipped.

### Images (`PrintImages`, `LocalOnlyUserAgent`, `ConvertToEdoc`)

**Before layout** (`PrintImages`), each `<img>` is checked in document order:

1. **Resolve the source as the renderer will.** A `data:` URI is decoded. Otherwise the
   source must be a valid URI reference: `baseDirectory.toUri().resolve(new URI(src))`
   (a raw space or other invalid character makes it unusable). The result must be a
   `file:` URI naming an existing regular file inside an allowed directory. Remote, empty
   and `data-carlos-image-unavailable` sources are unusable.
2. **Size and type.** Unusable if the file or decoded `data:` payload exceeds 32 MB, or if
   it is a PDF (starts with `%PDF-`; Flying Saucer 10.7.0 would otherwise render it as an
   image page).
3. **SVG.** If the content is SVG, it is unusable if any `href`, `xlink:href` or CSS
   `url()` in it points anywhere other than a fragment (`#…`) or a `data:` URI (Batik would
   otherwise fail the whole render); otherwise it is usable and skips rules 4–6.
4. **Header.** Read only the header with `ImageIO` (no decoding). Unusable if no `ImageIO`
   reader recognises it (for example WebP) or the header cannot be read, or if
   width × height exceeds the **per-image cap**: the smaller of 25 000 000 pixels and the
   budget capacity below.
5. **Pixel budget** *(designed, to be load-tested in PR 1)*. One JVM-wide semaphore holds
   `Runtime.getRuntime().maxMemory() / 64` pixel permits (about a quarter of the heap at 16
   bytes per pixel, the worst case measured: a 2 GB heap gives about 33.5 million pixels).
   Walking the usable raster images in document order (each distinct file or `data:`
   payload once), the
   document keeps images while their running total fits the capacity; later images become
   the box (reason `over render pixel budget`). The render then acquires the total from the
   semaphore, waiting at most 60 s, and fails with `PDFGenerationException` if it cannot.
   The permits are released in a `finally` after the PDF is built. (One 25-megapixel RGBA
   PNG needs 256–384 MB of heap; two concurrent renders of one exhausted 512 MB, *tested*.)
6. **Trial decode.** Decode with OpenPDF `org.openpdf.text.Image.getInstance(bytes)`, the
   call Flying Saucer uses, after the permits are held. A failure makes it unusable (reason
   `undecodable`). This catches a valid header with corrupt data and formats `ImageIO` reads
   but OpenPDF cannot (for example WBMP or PNM from `jai-imageio-core`). Passing rules 1–6
   guarantees Flying Saucer can draw the image.
7. **Rewrite.** A usable `<img>` gets its `src` set to the checked file's `Path.toUri()`
   (encoded, so paths with spaces or accents load, *tested*); `data:` sources are kept.

An unusable `<img>` is replaced by
`<span class="carlos-pdf-image-unavailable">[image unavailable]</span>`, sized from the
image's `width`/`height` attributes or inline style when present, and a WARN is logged with
the reason only (no path, no URL). Inline `<svg>` elements get rule 3 and the same
replacement.

**During layout** (`LocalOnlyUserAgent.getImageResource`, defence in depth and for CSS
background images, which cannot show a box): the override handles `data:` and `file:`
sources, applies the 32 MB limit and reads the header before any decoding; a CSS background
over 4 000 000 pixels, or any exception while loading, returns an empty image resource (so
Flying Saucer draws nothing for it), with a WARN without the path. Otherwise it delegates to
`super.getImageResource(uri)`, keeping Flying Saucer's resolution scaling. A single bad
image therefore never fails the render, and never decodes past the caps or the budget.

**`ConvertToEdoc`'s part:**

- `translateResourcePaths`, `translatePaths` and `validateResourcePaths` collect their work
  per element (`LinkedHashMap<Element, List<String>>`) instead of keyed by the candidate
  path list, so every occurrence of a repeated image is translated and validated (today
  only the last is).
- Translated paths are emitted as encoded `file:` URIs (`Path.toUri()`), in `src`, `href`,
  CSS `url()` and the `pdfMediaStylesheet` link.
- Where `validateResourcePaths` (line ~1017) or `translatePaths` (lines ~905/911) today
  removes an `<img>` (remote, `file:`, unresolvable, or an extension outside `FileType`), it
  instead removes the `src` and sets `data-carlos-image-unavailable`, so `PrintImages` shows
  the box. No URL reaches the renderer. `<link>` and `<script>` handling is unchanged, and
  remote CSS `url()` stays blanked.
- When the static `realPath` is null, `ConvertToEdoc` sets it from the same webapp-root
  lookup used for the render context (below), so path translation and rendering use one
  root.

Measured through `Doc2PDF`'s path: CMYK and progressive JPEG, 16-bit and alpha PNG, GIF
(first frame), BMP and TIFF load; WebP and corrupt, zero-byte and missing files become the
box. In `ConvertToEdoc`, only `jpeg`, `jpg`, `png` and `gif` files pass `FileType`; other
image files show the box (today they silently disappear).

### Output geometry (`PageGeometry`)

Constants: Letter `612 × 792 pt`; margin `M = 28.3465 pt` (10 mm); default zoom `0.8`.

1. **Pass 1.** The defaults contain
   `@page { size: 765pt 990pt !important; margin: 35.4331pt !important }` (Letter and 10 mm
   divided by 0.8). Render with `setScaleToFit(true)`, so content wider than the page widens
   the page instead of being clipped. `W` is the largest page width in the output
   (scale-to-fit widens every page to the same width, *tested*).
2. **If `W ≤ 765.5 pt`**, use pass 1 with `z = 0.8`.
3. **Otherwise** compute `C = W − 2M/0.8`, `P = C / (1 − 2M/612)`, `z = 612 / P`. Pass 2
   replaces the `@page` rule with
   `@page { size: P pt (792/z) pt !important; margin: (M/z) pt !important }` and renders
   without scale-to-fit. Use pass 2 with that `z`. If `z < 0.4`, log a WARN with `z` only.
4. **Place.** Flatten AcroForm fields with OpenPDF `PdfStamper.setFormFlattening(true)`
   (defensive: `PrintFormControls` leaves none). Create a 612 × 792 pt document with no
   margins; for each page, `getImportedPage` and
   `addTemplate(page, z, 0, 0, z, 0, 792 − h × z)` with `h` the source page height. Copy
   `/Title` with `Document.addTitle`.

**Minimum font size.** `BundledFontResolver` raises `FontSpecification.size()` (in dots) to
at least `10 × 20 = 200` (10 CSS px), so 8 px text prints at 6 pt, as with wkhtmltopdf
(*tested*).

**Media.** Flying Saucer applies `@media print` and not `@media screen`, as wkhtmltopdf does
with `printMediaType=true` (*tested*).

**What page import drops:** link annotations, bookmarks and tagged structure. Text stays
extractable; vector graphics, images and embedded fonts are kept. wkhtmltopdf kept links;
no consumer of these PDFs (fax, print, email attachment, document store) uses them.

Measured with these rules: 612 × 792 pt pages; content edges at 28 pt (wkhtmltopdf 29);
the Annual form at zoom 0.776, 1400 px at 0.529; production-baseline page counts on every
fixture of up to 3 pages; a 2000-row table at 41–42 pages against 43.

### Injected stylesheet (`carlos-pdf-defaults`)

```css
@page { size: 765pt 990pt !important; margin: 35.4331pt !important }  /* pass 1; see geometry */
html { line-height: 1.164 }                     /* WebKit "normal" for DejaVu Sans */
html, body { margin-top: 0 !important }          /* WebKit drops the top margin at the page start */
body > *:first-child { margin-top: 0 !important }
body { word-wrap: break-word }                  /* long URLs/values wrap instead of forcing shrink */
table.carlos-pdf-paginate { -fs-table-paginate: paginate }  /* repeat thead (step 2h) */
.carlos-pdf-control { display: inline-block; vertical-align: baseline; font-size: 10pt;
  text-align: left; text-indent: 0; text-transform: none; letter-spacing: normal;
  word-spacing: normal }
.carlos-pdf-text, .carlos-pdf-password, .carlos-pdf-select { border: 1px solid #000;
  padding: 1px 2px; min-height: 1.2em; white-space: pre-wrap; word-wrap: break-word }
.carlos-pdf-textarea { border: 1px solid #000; padding: 1px 2px; white-space: pre-wrap;
  word-wrap: break-word }
.carlos-pdf-checkbox, .carlos-pdf-radio { font-size: 13px }
.carlos-pdf-image-unavailable { display: inline-block; border: 1px dashed #000;
  padding: 2px; font-size: 8pt; text-align: left; white-space: nowrap; overflow: hidden }
```

Flying Saucer 10.7.0 ignores `overflow-wrap`; it supports `word-wrap: break-word`. Without
it, one 600-character URL shrank a whole document to zoom 0.145 (*tested*; with it, 0.8).
A box narrower than its text keeps its size and shows "[image un…" rather than breaking
mid-word (*tested*).

### Fonts

**Mapping** (`BundledFontResolver`). With production fonts (`fonts-dejavu-core` only),
wkhtmltopdf prints every generic and unknown family (`serif`, `Times New Roman`,
`monospace` including `<pre>`, `Courier New`, `Arial`, `Verdana`, unstyled text) in DejaVu
Sans (measured). For parity, every family resolves to one registered family:

| Registered family | Files (all `Identity-H`, embedded) | Used for |
|---|---|---|
| `CARLOS Sans` | `net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf`, `DejaVuSans-Bold.ttf` | Every requested family |
| `CARLOS CJK` | `fonts/wqy/wqy-microhei.ttc,0` | Only fallback spans |
| `CARLOS Symbol` | `fonts/symbola/Symbola_hint.ttf` | Only fallback spans |

`resolveFont(SharedContext, FontSpecification)` takes the first family in the CSS list,
lower-cases it and strips quotes and whitespace. `carlos cjk` and `carlos symbol` (set only
by `FallbackFontRuns`) resolve to those families; everything else resolves to
`CARLOS Sans`. The whole list is replaced by that one family, so Flying Saucer's base-14
fonts are never chosen. Families are registered under the exact names above (Flying Saucer
matches registered names case-sensitively, *tested*). Bold selects DejaVu Sans Bold
(*tested*).

No oblique faces are registered, so italic and oblique text use the regular or bold face,
which Flying Saucer slants synthetically with the same character widths (*tested*). This
matches production, where `fonts-dejavu-core` has no oblique face and Debian's default
`90-synthetic.conf` slants it the same way (*tested* side by side). The bundled DejaVu Sans
Oblique faces are deliberately not used: their widths differ from the regular face for 120
of 2536 characters (for example « » æ œ ľ), which would move line breaks (measured).

This differs from JasperReports' logical-font mapping (`carlos-jasper-fonts.xml`, serif →
DejaVu Serif, monospace → Liberation Mono), because parity with today's HTML output is the
goal here.

**Fallback-font step** (`FallbackFontRuns`), mirroring `PdfFonts`' rules for OpenPDF text.
For each text node outside `<style>` and `<title>`, each code point that is not whitespace
and not a format or variation-selector character, and that is not drawable by **both**
registered DejaVu Sans faces (regular and bold), is assigned the first face that can draw
it: WenQuanYi Micro Hei, then Symbola. Maximal runs with the same face are wrapped in
`<span style="font-family:'CARLOS CJK'; font-style:normal">` (or `'CARLOS Symbol'`);
fallback faces have no italic, so the span forces upright. A code point neither can draw is
replaced with U+FFFD, which DejaVu Sans draws, so the gap is visible; a DEBUG log records
only the count. The same input always produces the same spans (*tested*).

Coverage (OpenPDF `BaseFont.charExists`): DejaVu Sans (regular and bold) covers accented
Latin, Greek, Cyrillic, math, units, arrows, punctuation, ☐ ☒ ◉ ○ • ✓ and U+FFFD.
WenQuanYi Micro Hei covers Han, kana, Hangul, Bopomofo, CJK punctuation and fullwidth
forms. Symbola covers most remaining symbols (for example 𝄞).

**Font files** (`BundledFontFiles`). Flying Saucer's `addFont` reads a filesystem path (its
`BaseFont` overload re-reads the path to extract names), and DejaVu ships inside the
`jasperreports-fonts` jar (compile scope, already used by `PdfFonts`).

- The directory is `<java.io.tmpdir>/carlos-pdf-fonts-<first 12 hex digits of the SHA-256
  over all four font files' bytes>`, so one directory is reused across restarts (a unique
  directory per start leaked 14 MB each time, *tested*). It is not under
  `<tmpdir>/carlos-temp`, which `ApplicationTempPurgeJob` deletes after
  `carlos_temp_purge_max_age_hours` (default 24 h).
- If it does not exist, it is created owner-only (where POSIX permissions are supported).
  If it exists but is a symlink or not owned by the process user, this JVM uses a fresh
  `Files.createTempDirectory(tmpdir, "carlos-pdf-fonts-")` (owner-only) instead and logs a
  WARN, so another local user cannot block PDF generation.
- On first use, every file's SHA-256 is verified against the classpath bytes; a missing or
  different file is written to a temporary name and moved atomically into place.
  Extraction runs under one JVM-wide lock.
- Before every render, each file's existence and size are checked; on a mismatch the
  verification and rewrite run again under the lock (a file deleted after first use is
  restored by the next render, *tested*).
- Directories of other bundle versions are not deleted (another JVM sharing the temp
  directory may be using one); there is one per font-bundle version.
- Owner-only permissions are the tamper control. A missing classpath font fails the render;
  nothing is substituted.

On Debian `java.io.tmpdir` is `/var/lib/carlos-emr/catalina/temp` (`CATALINA_TMPDIR`,
`carlos-emr-tomcat` line 178; in `ReadWritePaths`; not aged by tmpfiles). In the Podman
example it is `/carlos-tmp` (`-Djava.io.tmpdir=/carlos-tmp`).

Measured cost: first render about 0.95 s including extraction; warm renders 81–300 ms (an
email about 118 ms; the Annual form with two passes 81–115 ms).

### Resource loading and security (`LocalOnlyUserAgent`)

- **Explicit allowed directories.**
  `createRestrictedRenderer(RenderContext, FontResolver)` passes the context's
  `allowedDirectories`. The implicit roots are removed: the base URL's directory, the JVM
  working directory, and the static `java.io.tmpdir` and `catalina.base/work` roots. A
  `file:` URI is allowed only if its canonical path is inside one of them.
- **Default stylesheet.** Exactly one extra URI is allowed: Flying Saucer's default
  stylesheet, computed once as Flying Saucer computes it
  (`Configuration.valueFor("xr.css.user-agent-default-css") + "XhtmlNamespaceHandler.css"`,
  resolved with `Class.getResource` on `XhtmlCssOnlyNamespaceHandler`), matched by exact
  string equality in both `resolveAndOpenStream` and `openStream`. `setBaseURL` ignores that
  URI silently instead of logging a WARN. Every other `jar:` URI (including other entries in
  the same jar and `jar:http:`), `http:`, `https:`, `ftp:`, protocol-relative and
  out-of-directory `file:` request stays blocked.
- **`data:` URIs** stay allowed for images (`<img>` and CSS backgrounds, decoded in
  `getImageResource`). Flying Saucer opens no other `data:` resource; a `data:` stylesheet
  is ignored, as today.
- **Images:** see "Images" (caps, budget, failures become the box or an empty resource).
- **XXE.** CARLOS's path parses through Flying Saucer's `XMLResource` SAX path and entity
  resolver; zero requests on 10.0.6 and 10.7.0.
- **PHI-safe logging.** Blocked requests log the scheme and, for network schemes, the host
  only. Blocked `file:` requests and image failures log no path. No message includes HTML
  content. Flying Saucer 10.7.0's `Html5Support` warnings name only tags and CSS
  properties.

**Callers' contexts:**

| Caller | `baseDirectory` | `allowedDirectories` |
|---|---|---|
| `ConvertToEdoc` | webapp root: `realPath` if set, else `ServletContext.getRealPath("/")` from `ContextLoader.getCurrentWebApplicationContext()` (`OscarSpringContextLoader` extends `ContextLoaderListener`) | webapp root; `CarlosProperties.getEformImageDirectory()` (`EFORM_IMAGES_DIR`, else derived from `BASE_DOCUMENT_DIR`), omitted if it cannot be resolved; `java.io.tmpdir`; `catalina.base/work` if set |
| `Doc2PDF` | webapp root from `request.getServletContext().getRealPath("/")` (today's base) | webapp root; `java.io.tmpdir`; `catalina.base/work` if set |

If the webapp root cannot be resolved, the render fails (`ConvertToEdoc` returns `null`;
`Doc2PDF` follows its existing failure path).

### Errors

| Caller | After |
|---|---|
| `HtmlPdf.render` | Throws `PDFGenerationException` for any failure (see "Render pipeline"), including blank input, right-to-left text and a pixel-budget timeout; never returns partial bytes |
| `ConvertToEdoc.execute` | `tidyDocument`, rendering and saving all inside one `try`; catches `PDFGenerationException`, `IOException` and `RuntimeException`; logs a fixed message, the `LogSafe`-sanitised file name and the cause class (and, for right-to-left text, that reason); returns `null` (contract unchanged) |
| `FormsManagerImpl.renderForm`, `FaxDocumentManagerImpl.getFormFaxDocument` | Unchanged: they already turn `null` into `PDFGenerationException` naming the form |
| `HRMPDFCreator.printPdf` | Declares `throws PDFGenerationException`; the HTML branch throws it when `ConvertToEdoc` returns `null` (the existing `IOException`/`DocumentException` handling for text reports is unchanged) |
| `HRMUtil.renderHRM` | Rethrows as `PDFGenerationException` naming the HRM report (it already declares the type) |
| `EctConsultationFormRequestPrintAction22Action` | Unchanged: the `printPdf` call (line ~298) is inside the `try` whose `catch (PDFGenerationException)` is at line ~343 |
| `PrintHRMReport2Action` | Adds `PDFGenerationException` to its existing `IOException \| RuntimeException` handling (same error response) |
| `EmailManager.createMessageAttachment` | Declares and throws `EmailSendingException("Encrypted message could not be converted to PDF")` when the path is `null`; `encryptEmail` already declares it |
| `Doc2PDF.GetPDFBin` / `parseString2Bin` | Unchanged: return `null`; Messenger records the item as failed (`MsgAttachPDF2Action`, line ~284) |
| `Doc2PDF.PrintPDFFromHTMLString` | Unchanged: sends its existing error response |

Callers that name the document (forms, fax, HRM) give the user the reason for any failure,
including right-to-left text.

## Removals and Packaging

- **`pom.xml`:**
  - Flying Saucer `10.0.6` → `10.7.0` (keep the `com.github.librepdf:openpdf` exclusion).
  - Remove `com.github.openosp:ultrabuk-htmltopdf-java`.
  - Remove `com.github.librepdf.openpdf:openpdf-html` (also drops `neko-htmlunit`).
  - Remove the `jna.version` property and the `net.java.dev.jna:jna` dependency-management
    pin (only ultrabuk used JNA; nothing imports `com.sun.jna`; no packaging test pins it).
  - Update the repositories comment that lists ultrabuk and openpdf-html as vendored.
- **`local_repo/`:** delete `com/github/openosp/ultrabuk-htmltopdf-java` (33 MB) and
  `com/github/librepdf/openpdf/openpdf-html`.
- **`dependencies-lock.json`:** regenerate with the project's lock plugin (`make lock`,
  `se.vandmo:dependency-lock-maven-plugin`), never by hand. The forward merge to `develop`
  regenerates it there too.
- **Java:**
  - delete `InternalEDocConverter`, `EDocConverterInterface`, `ReplacedElementFactoryImpl`,
    and the `@see`/`{@link}` references to `ReplacedElementFactoryImpl` in `ConvertToEdoc`
    (lines ~92 and ~419);
  - in `ConvertToEdoc` delete `fallbackRender`, `prepareDocumentForFlyingSaucer`,
    `normalizeHtmlCommentsForXml` (its only caller is `prepareDocumentForFlyingSaucer`),
    `DEFAULT_WKHTMLTOPDF_COMMAND`, `DEFAULT_WKHTMLTOPDF_ARGS`, the wkhtmltopdf comments
    ("htmltopdf tools require String input"), `from(EFormData)`,
    `from(FormTransportContainer)` (and the class JavaDoc example that shows it) and
    `saveAsTempPDF(String, String)`;
  - delete `EformDataManager(Impl).saveEformDataAsEDoc` and
    `FormsManager(Impl).saveFormDataAsEDoc`.
- **`debian/control`:** remove `libxrender1` (line 81) and its comment. The comment names
  wkhtmltopdf as its only user; Chromium's dependency list does not include it (`libcairo2`,
  line 132, pulls it in where needed); no `debian/tests` file mentions it. Existing installs
  keep it as an auto-removable package.
- **Settings:** delete the orphaned wkhtmltopdf comment block in
  `src/main/resources/carlos.properties`, and `WKHTMLTOPDF_COMMAND` / `WKHTMLTOPDF_ARGS` in
  `.devcontainer/development/config/shared/volumes/carlos.properties`.
- **CI:**
  - `.github/workflows/dependency-submission.yml`: drop ultrabuk from the failure-recovery
    comment and the "formerly-JitPack" comment; remove `~/.m2/repository/com/github/openosp`
    from the cache eviction; keep `com/github/librepdf` (OpenPDF core stays vendored).
  - `.github/actions/dev-container/action.yml` (line ~148): remove the
    `com/github/openosp` eviction.
  - The repository's `.claude/settings.json` denies Claude writes under `.github/`. If the
    rule blocks the edit, the exact patch goes to the maintainer instead of working around
    it.
- **Docs and JavaDoc:**
  - `docs/pdf-font-policy.md`: add the HTML engine as a third engine (every family →
    DejaVu Sans, fallback-font step, tests).
  - `CLAUDE.md`, "PDF and Report Fonts": one line pointing HTML-to-PDF at `HtmlPdf`.
  - `docs/pdf-dependency-consolidation-plan.md`: mark ultrabuk and openpdf-html removed.
  - `utils/readme.txt`: remove the wkhtmltopdf section.
  - `ConvertToEdoc` class JavaDoc (line ~84, "Fallback conversion via Flying Saucer") and
    `HRMPDFCreator` JavaDoc (line ~126): describe the single engine.
  - Historical records (`docs/pdf-migration-functional-equivalence-findings.md`) stay as
    they are.

## Testing

Conventions: JUnit 5, `should<Action>_<context>()`. Tests that need neither Spring nor a
database are tagged `unit` and named `*UnitTest` (as `JasperBundledFontRenderingUnitTest`).
New Java files carry the CARLOS copyright header and JavaDoc with `@since 2026-10-29` and no
`@author`. Fixtures contain FAKE- names only. Tests that exercise `ConvertToEdoc` set the
webapp root through the existing `tidyDocument(String, String)` / `getDocument(String,
String)` (which set `realPath`) or a package-private setter, because no web application
context exists in tests.

| Test | Asserts |
|---|---|
| `LocalOnlyUserAgentUnitTest` (extended; its three `createRestrictedRenderer()` calls at lines ~472–494 move to the new signature) | Default stylesheet URL opens and `setBaseURL` with it logs nothing; another entry in the same jar, `jar:http:`, `http:`, protocol-relative and out-of-directory `file:` return `null`; a root directory is rejected; blocked `file:` logs contain no path; behaviour does not depend on the working directory; `getImageResource` returns an empty resource for an over-cap background, a corrupt image and an over-32 MB file without decoding past the cap, and loads a `data:` image |
| `XhtmlCleanupUnitTest` | Steps 2a–2m: scripts and comments removed (including the `--` comment case moved from `ConvertToEdocUnitTest:94`); soft hyphens and bidi controls removed; inline `position: fixed` rewritten; `<ol type>` mapped; `rowspan` clamped to its row group; only `thead` tables without row spans get `carlos-pdf-paginate`; `alt` added; `carlos-pdf-defaults` first in `<head>` and an author class rule beats it; output parses with the JDK XML parser |
| `PrintFormControlsUnitTest` | Every row of the form-control table, including copied attributes, sizing, CR/LF removal, multi-select, `optgroup`, no selection, `checked="false"`, password not revealing length, `progress` with and without `value`/`max`, `meter` |
| `PrintImagesUnitTest` | Usable: file inside an allowed directory, `data:` PNG, local SVG with only fragment references, a path with spaces and accents (rewritten to an encoded URI). Unusable → box: remote, outside allowed directories, missing, zero-byte, over 32 MB, a PDF, corrupt header, valid header with corrupt data, WebP, WBMP, 10 000 × 10 000 PNG (no decoding), a raw space in a relative `src`, SVG with an outside reference, inline `<svg>` with an outside reference, `data-carlos-image-unavailable`; images past the pixel budget; box sized from `width`/`height`; WARN without path |
| `PrintImagesMemoryUnitTest` | Six near-cap images in one document, and four such documents rendered concurrently at `-Xmx1g`: no `OutOfMemoryError`; a render that cannot acquire the budget within the timeout fails with `PDFGenerationException` (run in its own JVM) |
| `FallbackFontRunsUnitTest` | Only characters missing from DejaVu Sans regular or bold are wrapped (for example CJK and 𝄞); WenQuanYi → Symbola → U+FFFD order; spans set `font-style: normal`; `<style>`/`<title>` untouched; identical output across runs |
| `BundledFontResolverUnitTest` | Every family (generic, named, unknown, none) → `CARLOS Sans`; `carlos cjk`/`carlos symbol` → their families; bold → DejaVu Sans Bold; italic → the regular face, slanted, with regular widths; minimum size 200 dots |
| `BundledFontFilesUnitTest` | Directory name from the bundle hash; outside `carlos-temp`; owner-only where supported; reused across calls; a deleted, truncated or same-size altered file is rewritten; a file deleted after first use is restored by the next render; a symlinked or foreign-owned directory leads to a fresh private directory and a WARN; concurrent first use extracts once; a missing classpath font fails clearly |
| `PageGeometryUnitTest` | 400, 850, 1000, 1400 px pages: 612 × 792 pt, content left/top at 28 ± 1 pt, nothing clipped; a narrow first page with a wide later page still shrinks; author A4-landscape `@page` ignored; identical output with `Locale.setDefault(Locale.UK)` and with a country-less locale (restored afterwards); 8 px text prints at 6 pt; `@media print` applies, `@media screen` does not; `thead` repeats on every page; a 600-character URL keeps zoom 0.8; zoom below 0.4 logs a WARN; `/Title` copied |
| `HtmlPdfRenderUnitTest` | For each fixture: valid PDF; PDFBox text contains the expected strings (accents, ≥, ✓, Chinese); every font embedded and one of the four bundled faces; no `<title>` or CSS text; no `/AcroForm`; expected page count; a `rowspan=4` cell over three rows and a row span inside a `thead` table do not overlap the following content; null and whitespace-only HTML fail with `PDFGenerationException`, while markup with no visible content gives one blank page; a `baseDirectory` outside `allowedDirectories` fails; a `LinkageError` from the renderer becomes `PDFGenerationException`; Hebrew, Arabic and Urdu text fail with the right-to-left message, and right-to-left text only inside `<title>` does not; Hindi, Punjabi, Tamil and Thai print as U+FFFD |
| `HtmlPdfFormsUnitTest` | Forms fixture: every value present including CJK; ☒/☐ and ◉/○ correct; hidden values and the Annual hidden field names never appear; only the selected option prints; textarea keeps two lines; a date value complete; values left-aligned inside right-aligned cells; text identical after `ConcatPDF` alone and after `ConcatPDF` + flatten |
| `HtmlPdfSsrfUnitTest` | Hostile HTML (`<link>`, `@import` both forms, `url()` background, `@font-face`, `<base href>`, `<img>` over `http:`, `jar:http:`, protocol-relative, `file:///etc/passwd`, `srcset`, `<iframe>`, `<object>`, `<embed>`, SVG `xlink:href`) makes no request to a local `ServerSocket` and reads no file outside the allowed directories |
| `HtmlPdfConcurrencyUnitTest` | 24 mixed renders on 8 threads give identical output and no errors |
| `ConvertToEdocUnitTest` (updated) | A linked stylesheet, a CSS `url()` background and `pdfMediaStylesheet` under the webapp root load when the working directory is outside it; translated paths are encoded `file:` URIs; three `<img>` with the same `src` all translate; an unresolvable `<img>` and a `.bmp` `<img>` become the box instead of disappearing; with `realPath` unset, translation and rendering use the same webapp root; blank HTML and render failures return `null` and log no HTML |
| `ConvertToEdocIntegrationTest` (rewritten) | Its `MockedConstruction<InternalEDocConverter>` and `ConvertToEdoc.from(eform)` cases (lines ~87–105) move to `saveAsTempPDF(EFormData)`; the `from(eForm, pdfPath)` case stays |
| `Doc2PDFIntegrationTest` (updated) | Uses `HtmlPdf` through `Doc2PDF`; failure returns `null` |
| `FormsManagerUnitTest` | The `@Nested` class `SaveFormDataAsEDocSecurity` (lines ~305–323) is deleted with the method |
| `HRMPdfUnicodeUnitTest`, `PrintHRMReport2ActionUnitTest` (updated) | Compile against `printPdf() throws PDFGenerationException`; a thrown `PDFGenerationException` gives `PrintHRMReport2Action`'s existing error response |
| `HRMPDFCreator`/`HRMUtil` tests | A `null` conversion surfaces as `PDFGenerationException` naming the report, never a `NullPointerException` |
| `EmailManager` test | A `null` message PDF surfaces as `EmailSendingException` |
| Existing `EformDataManagerImplCreatePdfUnitTest`, `FormsManagerImplUnitTest`, `FaxDocumentManagerImplUnitTest`, `MsgAttachPDF2ActionUnitTest` | Run unchanged |

**Fixtures** (`src/test/resources/htmlpdf/`), all with `<!DOCTYPE html>`:

- Annual form pages, female and male, built from the real JSPs with checked boxes, textarea
  text, the hidden fields, the row spans and the real `annualStyle.css`/`print.css`.
- A forms page with every control type (CJK in a value, `optgroup`, `progress`, `meter`).
- The demo HRM report and three synthetic HTML HRM reports: table-heavy (including a
  2000-row table), inline styles, and images (file, `data:`, remote, repeated `src`, `.bmp`,
  corrupt header, corrupt body, WebP, PDF, oversized).
- An encrypted-email message body; a Messenger letter.
- Wide pages (400, 850, 1000, 1400 px), a narrow-then-wide page, an author `@page` page,
  small text, print/screen media rules, a long URL, `thead` across pages, `position: fixed`,
  soft hyphens, `<ol type="a">`, an over-long `rowspan`.
- Scripts: CJK, ✓, 𝄞, Hebrew, Arabic, Urdu, Hindi, Punjabi, Tamil, Thai, Vietnamese with
  combining marks.
- Edge markup: empty elements, self-closing source tags, misnested tags, entities,
  fragment-only and text-only input.

**Host-font independence:** add `HtmlPdfRenderUnitTest`, `HtmlPdfFormsUnitTest` and
`PageGeometryUnitTest` to the default list in `scripts/check-report-fonts.sh` (it runs
Surefire with `-Dtest` in fresh JVMs).

**One-off comparison (PR 1):** a script renders every fixture with wkhtmltopdf (vendored
jar; `InternalEDocConverter`'s settings; `LANG=C.UTF-8` or `-Djna.encoding=UTF-8`;
`FONTCONFIG_FILE` limited to `fonts-dejavu-core`; input = the output of
`ConvertToEdoc.tidyDocument`) and with `HtmlPdf`, and attaches side-by-side PNGs to PR 1.
Acceptance per fixture:

- page size 612 × 792 pt;
- page count equal for documents of up to 3 pages; for longer ones at most wkhtmltopdf's
  count and at least 90 % of it (Flying Saucer packs long tables slightly tighter: a
  2000-row table gives 41–42 pages against 43);
- content left and top edges at 28 ± 1 pt for edge-to-edge fixtures, otherwise within 4 pt
  of wkhtmltopdf;
- every value and checked box visible, no hidden value visible, every image present or
  shown as the box;
- no text or box drawn over another (checked on the side-by-side PNGs).

Differences are listed in the PR. The script is not kept in CI (wkhtmltopdf is removed in
PR 2).

**Manual checks on Tomcat**, in the devcontainer **and** with the webapp outside the working
directory (as on Debian):

- print an Annual form (female and male);
- preview and send a consultation fax with an Annual form and an HTML HRM report attached;
- fax an Annual form on its own;
- send an encrypted email;
- attach a Messenger PDF of a form;
- save a form as a document;
- trigger an HRM render from a non-request thread (for example the fax scheduler) to check
  `ContextLoader.getCurrentWebApplicationContext()` resolves there.

## Deliberate differences from today's output

List these in PR 1:

- **`ConvertToEdoc`:**
  - buttons and image inputs no longer print (the Annual form's top bar keeps its grey
    background but loses its five buttons);
  - long values wrap instead of being clipped; long unbroken words and URLs in body text
    wrap;
  - passwords print as eight bullets; multi-selects print the chosen options as text;
    textareas lose their scrollbars; `<progress>`/`<meter>` print as text instead of bars;
  - checkbox and radio marks are DejaVu glyphs (about 7.8 pt) rather than drawn 10 pt
    boxes;
  - author CSS that targets `input`/`select`/`textarea` element types (for example
    `input[type=text] { width: 220px }`) no longer sizes the replacements
    (`annualStyle.css` has no such rules);
  - table column widths differ slightly (Flying Saucer gives right-hand columns more
    width, so the Annual form's header fields spread across the page);
  - author `margin-top` on `html`, `body` and the body's first child is ignored at the page
    start;
  - images that today silently disappear (repeated `src`, removed sources) now print, or
    show the "[image unavailable]" box; unusable images (including an `<img>` pointing to a
    PDF) show the box;
  - very wide content shrinks without limit instead of being clipped;
  - a table with both `<thead>` and row spans prints its header once instead of on every
    page;
  - documents with right-to-left text fail instead of printing.
- **`Doc2PDF` (Messenger):** output adopts the same geometry and rules (Letter, 10 mm,
  0.8 zoom, author `@page` ignored, form controls, fonts, images). Today it uses Flying
  Saucer's defaults, honours author `@page`, takes its paper size from the JVM locale and,
  because of the blocked default stylesheet, prints the title and CSS text.

## Known limitations

- **Right-to-left text** (Hebrew, Arabic, Urdu, Persian, Syriac and others) fails the
  render with a clear error, so such forms, HRM reports and emails cannot be produced as
  PDF on this path until right-to-left support is added (see "Follow-ups").
- **Scripts without bundled glyphs or shaping** (Devanagari, Gurmukhi, Tamil, Thai and
  others) print each character as a visible U+FFFD. Production wkhtmltopdf with only
  `fonts-dejavu-core` prints boxes, so both lose this text visibly. Punjabi is common in
  British Columbia.
- **`ConvertToEdoc` image types:** only `jpeg`, `jpg`, `png` and `gif` pass its `FileType`
  list; other image files show the box (today they disappear).
- **HRM files in Windows-1252** decode to U+FFFD in both engines (`HRMPDFCreator` decodes
  as UTF-8); pre-existing.
- **CSS background images** that fail to load are omitted with a WARN; they cannot show the
  box.
- **Stylesheet-defined `position: fixed`** (as opposed to inline) still repeats on every
  page; inline is rewritten.
- **Tables with both `<thead>` and row spans** print their header once.

## Delivery

Two PRs, both from and to `release/2026.08`, both merged before 2026.08 ships:

1. **New engine.** Flying Saucer 10.7.0; `HtmlPdf` and its helpers; the
   `LocalOnlyUserAgent`, `ConvertToEdoc` path/image and error-path fixes; deletion of
   `ReplacedElementFactoryImpl`; `Doc2PDF` and the `ConvertToEdoc` fallback use `HtmlPdf`
   (wkhtmltopdf still first, so today's fallback output also becomes correct). Tests
   (including the pixel-budget load test), fixtures, the font-check script entry and the
   comparison images.
2. **Switch and removal.** `ConvertToEdoc` uses `HtmlPdf` only; deletions and every item in
   "Removals and Packaging".

Rules from `docs/release-process.md` and `CLAUDE.md`:

- Do not change the `-SNAPSHOT` version or the SCM tag.
- After each PR merges, a maintainer forward-merges `release/2026.08` into `develop`.
- During beta the release branch takes high-priority correctness and security fixes
  (`release-process.md`, lines ~38 and ~113). Each PR body states the case: abandoned
  wkhtmltopdf with a published SSRF vulnerability and local file access on; today's Flying
  Saucer fallback printing CSS text, losing checkbox answers and printing hidden
  identifiers; images silently dropped today; the font policy gap; the image
  heap-exhaustion risk. The 2026.08 release owner decides whether to accept it.
- Commits are not signed off by Claude; a maintainer confirms DCO on each PR with the
  repository's manual phrase.
- Each PR body includes the "Generated with Claude Code" line and references this spec.

## Risks

| Risk | Mitigation |
|---|---|
| A form or HRM report lays out differently from wkhtmltopdf despite the emulation | Side-by-side comparison with acceptance criteria (including an overlap check) in PR 1; small CSS fixes to form print markup are in scope; other differences are listed for the release owner |
| Real hospital HRM HTML is more varied than the fixtures (CSS beyond Flying Saucer's CSS 2.1, malformed markup) | Failures surface as an error naming the report; unusable images show the box; new variants become fixtures |
| The pixel budget is too small (images boxed needlessly) or too large (heap still exhausted) | Load test in PR 1 at `-Xmx1g` and at the production default; the budget is derived from the heap size, not hard-coded |
| Clinics with right-to-left patient names or reports hit the new failure | The error names the document; listed as a known limitation and a follow-up |
| Two passes plus page import make each render slower | Measured 81–300 ms warm; `ConvertToEdoc` is already serialised; fax batches are checked in the manual tests |
| Default-stylesheet fix and geometry change today's `Doc2PDF` (Messenger) output | Listed as a deliberate difference; covered by tests and the manual Messenger check |
| Font directory tampering, deletion or squatting in a shared temp directory | Owner-only directory with ownership and symlink checks and a private fallback directory; verified on first use, existence and size checked before every render, rewritten on mismatch |
| Webapp root unavailable when `ConvertToEdoc` runs outside a request (HRM, email) | Resolved from the Spring web application context; checked from a scheduler thread in the manual tests; if unavailable the render fails clearly |
| CI workflow edit blocked by the `.github/` write rule | Exact patch handed to the maintainer |

## Follow-ups (not in these PRs)

- Right-to-left support: bidi reordering with `java.text.Bidi` and Arabic shaping with ICU4J
  (already a dependency) before layout. A prototype made Hebrew read correctly; wrapped
  multi-line paragraphs still need line-order handling.
- `ConvertToEdoc.getRealPath` matches a resource by file name anywhere under the webapp
  (`Files.walk`), so it can pick a same-named file from another directory; pre-existing.
- `.devcontainer/development/Dockerfile:79` installs `libxrender1`, `libxi6`,
  `xfonts-75dpi` and `xfonts-base` for wkhtmltopdf. Remove them after a devcontainer
  Playwright run confirms Chromium does not need them.
- `carlos-podman`'s `templates/carlos.properties.j2` still sets `WKHTMLTOPDF_*` (harmless,
  unused); remove it in that repository.
- The OSCAR 19 import map keeps `WKHTMLTOPDF_*` as `deploy-owned` until carlos-ctl's test
  changes in its own repository.
- HRM charset detection for non-UTF-8 reports.
