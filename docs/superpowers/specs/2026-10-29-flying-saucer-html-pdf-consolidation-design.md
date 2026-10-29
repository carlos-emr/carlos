# Design: Consolidate HTML-to-PDF on Flying Saucer and Remove wkhtmltopdf

**Date:** 2026-10-29 (seventh revision: approved design, plus the "failure is the last resort"
principle, CARLOS layering conventions, a Spring/MVC class-structure review, measured image
limits and a final fit-for-purpose review)
**Status:** Approved in conversation on 2026-10-29, with the changes in this revision. The
project owner chose to keep it on `release/2026.08` despite the release-fit concern (see
"Delivery")
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
- **Final review**: an independent proportionality review of this spec against
  `docs/release-process.md`, `docs/architecture/layer-names.md` and the packaging, and a
  sourced survey of current HTML-to-PDF engines and image limits (see "Decisions"). It led
  to fewer beans, a bounded 10 s wait instead of a two-minute permit ladder, per-severity
  logging, a rollback plan and the release-fit question (decided: it stays on
  `release/2026.08`).
- **Large images**: the smallest heap that renders 12- and 48-megapixel JPEGs and 8.4-,
  25- and 67-megapixel PNGs, streamed subsampled decoding of a 95 MB PNG, and TwelveMonkeys
  3.15.3 WebP decoding (see "Images").

Three rounds were run: a verification pass (claims audit, prototype against 15 fixtures,
about 80 adversarial documents) and a final review cycle (document review, prototype of the
full test list). Items marked *(tested)* were run with the rule in place. Two parts of this
revision are designed but not yet run, and PR 1 must test them: the retry and degradation
ladder under "Resilience" (the render slots and image memory, and the safe-mode and text-only retries)
and the right-to-left shaping under "Right-to-left text" (a Hebrew prototype read
correctly; Arabic shaping with ICU4J is untested). The class structure under
"Architecture" was reviewed against `docs/architecture/layer-names.md`, the Ontario billing
module and standard Spring practice; the prototype used a flatter structure, so the
structure itself is first exercised in PR 1.

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
| Failure handling | **Failure is the last resort.** Every failure point first retries or degrades visibly (see "Resilience"); an error naming the document is returned only when nothing printable can be produced. No fallback to Chromium |
| Flying Saucer version | Upgrade 10.0.6 → 10.7.0 as the first step |
| Structure | One shared Flying Saucer pipeline, built to CARLOS's layering conventions (`docs/architecture/layer-names.md`, `docs/billing-ontario-module.md`); `ConvertToEdoc` and `Doc2PDF` become thin legacy facades; their callers keep their contracts |
| Fonts | Every family prints in bundled DejaVu Sans, as production wkhtmltopdf does; bundled fallbacks only for characters DejaVu Sans lacks |
| Output geometry | Emulate wkhtmltopdf: Letter, 10 mm margins, 0.8 zoom, shrink-to-fit, 10 px minimum font, WebKit line height, repeated table headers, HTML row-span clamping |
| Shrink-to-fit limit | None (nothing is cut off); WARN when the zoom falls below 0.4 |
| Form controls | Printed as static text and marks; never as PDF form fields |
| Images | JPEG photos embedded unchanged at any resolution; other images reduced only above 25 megapixels or the render's share of memory (16.7 megapixels with the default 2 GB heap; beyond what paper shows); formats OpenPDF cannot draw converted; a visible "[image unavailable]" box only for what cannot be read; never exhaust the heap |
| Right-to-left text | Best effort for all of it: shaped and reordered before layout, never failing; documents with long right-to-left passages get a visible note |
| Release | Land on `release/2026.08`; maintainers forward-merge to `develop` |

Alternatives considered in the final review (sources: project repositories and Maven
Central, October 2026):

- **openhtmltopdf** (community fork, `io.github.openhtmltopdf`, LGPL, PDFBox 3): very
  active (1.1.105 on 2026-10-29, released once per pull request), with per-character font
  fallback, right-to-left line breaking in the right order and PDF/UA. It would not remove
  the Indic gap, the image limits, the shrink-to-fit emulation (it has no zoom), the
  font-family mapping or the form-control replacement (it writes fillable fields); its
  network and file access is open by default, as Flying Saucer's is; and its frequent
  patch releases change layout, so an exact pin and visual tests would be needed. Not a
  clear win; a prototype is listed under "Follow-ups" in case right-to-left quality
  becomes important.
- **Chromium for everything** (already deployed for eForms): shapes every script, but
  needs a browser process per render, depends on system fonts, and was ruled out for
  documents that need no scripting.
- **openpdf-html**: no release since OpenPDF 3.0.5 (May 2026), HTML parsed by NekoHTML.
- **WeasyPrint, Apache FOP, iText pdfHTML**: Python, XSL-FO input, or AGPL/commercial with
  closed-source Indic shaping.

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
  (unsupported characters print as a visible U+FFFD).
- Form answers print; hidden values, passwords and buttons never do; no PDF form fields.
- A document is produced whenever anything printable can be produced; every degradation
  is visible in the PDF or reported to the caller, never silent; nothing exhausts the heap.
- The SSRF guard covers every path and never depends on the working directory.
- Every remaining failure reaches the caller as a clear error or `null` per the caller's
  existing contract; none escapes as an unhandled `NullPointerException`.
- The WAR, the Debian package and the build no longer carry wkhtmltopdf, its native
  library, `libxrender1`, JNA, `openpdf-html` or `neko-htmlunit`.
- New code follows CARLOS's layering and naming conventions and standard Spring practice
  (stateless constructor-injected beans, immutable boundary records, owned resources, an
  acyclic package graph enforced by a test).

## Non-Goals

- Changing the Chromium eForm renderer (`EFormBrowserPdfService`).
- Changing PDFs drawn directly with OpenPDF (cover pages, consultations, labs, HRM text and
  image reports) or JasperReports templates.
- Moving callers onto a new API, or deleting `ConvertToEdoc`/`Doc2PDF`.
- Removing `ConvertToEdoc`'s class-level `synchronized` methods or its static `realPath`.
- Upgrading the vendored OpenPDF core (stays 3.0.3; Flying Saucer 10.7.0 is built against
  3.0.5, and every OpenPDF member it uses exists in 3.0.3, see "Decisions").
- Tagged PDF, PDF/UA or PDF/A output. Flying Saucer 10.5+ can tag, but placing pages on
  Letter discards the structure, and wkhtmltopdf's output had none either.
- Widening `ConvertToEdoc`'s `FileType` extension list.
- Full right-to-left layout (line breaking in right-to-left order; see "Follow-ups").
- Changing the OSCAR 19 import map: carlos-ctl's own test suite, which CI runs against this
  tree (`debian-python-tests.yml`, `CARLOS_SRC`), asserts `WKHTMLTOPDF_COMMAND` is
  `deploy-owned` (`carlos-ctl/tests/test_props.py:210-211`), and neither disposition carries
  the value over anyway.
- Devcontainer system packages (`.devcontainer/development/Dockerfile:79`); see
  "Follow-ups".

## Architecture

### Where the pieces sit

In MVC terms the controllers are unchanged (Struts `*2Action`s and the managers they
call), the model is the typed request and result, and this module is a view renderer: like
a JSP view, it turns a model into output and never sees the HTTP request. It is shaped like
Ontario billing's service tier (`docs/billing-ontario-module.md`, §3):

```
┌───────────────────────────────────────────────────────────────────────────┐
│ Controllers (unchanged): Struts *2Actions and managers                    │
│   FormsManagerImpl, FaxDocumentManagerImpl, HRMUtil, EmailManager,        │
│   MsgAttachPDF2Action, consultation print actions …                       │
│   Decide HTTP responses and user messages.                                │
└───────────────────────────────┬───────────────────────────────────────────┘
                                │ legacy contracts (Path or null, sendError)
┌───────────────────────────────▼───────────────────────────────────────────┐
│ Legacy facades (thin): ConvertToEdoc, Doc2PDF                             │
│   Translate paths, build an HtmlPdfRenderRequest, map the result to the   │
│   legacy contract.                                                        │
└───────────────────────────────┬───────────────────────────────────────────┘
                                │ HtmlPdfRenderRequest → HtmlPdfRenderResult
┌───────────────────────────────▼───────────────────────────────────────────┐
│ htmlpdf service tier (new Spring beans, constructor injection)            │
│   HtmlPdfRenderService  — the only entry point; render slots, attempts,   │
│                           the ladder                                      │
│   PrintDocumentComposer — print-ready XHTML (pure DOM rules)              │
│   PrintImageService     — plans and prepares every image                  │
│   PrintLayoutService    — layout passes and placement on Letter           │
│   BundledFontFileService, RenderedPdfValidator                            │
│ No servlet API, no HTTP codes, no PHI in messages or logs.                │
└───────────────────────────────┬───────────────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────────────┐
│ Engine extensions (per render, not Spring beans): LocalOnlyUserAgent,     │
│ BundledFontResolver, BundledFontSet                                       │
│ Flying Saucer 10.7.0 + OpenPDF 3.0.3 + PDFBox 3.0.8                       │
└───────────────────────────────────────────────────────────────────────────┘
```

Rules, following `docs/billing-ontario-module.md` §3.2, §9.2 and §14 and standard Spring
practice:

- **Few, cohesive, stateless singletons.** Six beans, each owning one cohesive job; a
  class exists because it has a separate responsibility, not to satisfy a suffix. Every
  bean is a singleton whose fields are `private final` and set by its constructor. The only
  JVM-wide state is the render slots (a `Semaphore` in `HtmlPdfRenderService`) and the
  verified font set (`BundledFontFileService`), both thread-safe. Per-render objects (the
  Jsoup DOM, the degradation set, the renderer, `LocalOnlyUserAgent`,
  `BundledFontResolver`, temporary directories) stay on the calling thread for one
  attempt and are never stored in a field.
- **Constructor injection, no I/O in constructors.** Constructors only assign fields, so a
  temporary-directory or font problem can never stop the Spring context from starting;
  fonts are extracted on first render. Where a test must set a limit (slot wait,
  temporary root), the class also has a package-private test constructor, and its
  production constructor carries `@Autowired` (the documented way to tell Spring which of
  two constructors to use). No field-initialised `SpringUtils.getBean(...)`, no `@Lazy`,
  no circular dependencies.
- **Legacy facades.** `ConvertToEdoc` and `Doc2PDF` are static, so they obtain the service
  at call time with `SpringUtils.getBean(HtmlPdfRenderService.class)`, the pattern
  `ConvertToEdoc` already uses for `NioFileManager` (line ~302: "resolve against the
  current context; do not retain a bean across context reloads"). New callers inject
  `HtmlPdfRenderService`.
- **Concrete classes, no interface/`Impl` pairs.** Each bean has one implementation,
  Mockito mocks concrete classes, and Ontario billing's services are concrete. The legacy
  `*Manager`/`*ManagerImpl` pairing is not used for new code.
- **One public entry point.** Outside the module, code uses only `HtmlPdfRenderService`
  and the `dto` types. The other service-package classes are package-private (component
  scanning instantiates package-private classes). `support` and `engine` types are public
  only because the service package uses them; their `package-info.java` marks them
  module-internal.
- **No HTTP semantics below the facades:** no `jakarta.servlet` or Struts types and no
  status codes in the module. Facades and controllers decide responses, as Ontario
  billing's assemblers leave HTTP codes to actions.
- **Degradations are data.** Each attempt creates one `EnumSet<HtmlPdfRenderDegradation>`
  and passes it to the steps that can degrade (a collecting parameter confined to the
  thread); the result carries the final set (Ontario billing's `partial` flag pattern,
  §9.2.1). Never nulls, sentinel values or exceptions for control flow.
- **Bounded concurrency and waits (bulkhead).** At most two renders hold a render slot at
  once (`ConvertToEdoc` is already serialised, so this is one form render plus one
  Messenger render). A render waits at most 10 s for a slot; this is the module's only
  wait. Each slot carries a fixed share of image memory, so how a document's images are
  reduced depends only on the document and the heap size, never on load. This is the
  usual pattern for server-side document rendering (Gotenberg and Browserless default to
  a fixed number of concurrent conversions and a 30 s request timeout).
- **Resources are owned by a scope.** The render slot and the per-render directories are
  released with `try`/`finally` or `try`-with-resources by `HtmlPdfRenderService` around
  the render and each attempt, so a slot is always returned and temporary files always
  deleted, whichever step fails.
- **Exceptions:**
  - the checked `PDFGenerationException` (existing, `utility`) is the boundary type for an
    expected failure (nothing printable could be produced); every caller already handles
    it;
  - an invalid request is a programming error: the request record's constructor throws
    `NullPointerException` or `IllegalArgumentException`, which the facades' existing
    broad catches handle;
  - `HtmlPdfRenderException` (package-private, unchecked, with a `Phase`) is internal and
    never leaves the service;
  - log or throw, never both: the service logs each render's outcome and each failed
    attempt (PHI-safe), and does not log the exception it throws; callers log what they
    catch.
- **Logging** uses `MiscUtils.getLogger()` in a `private static final Logger`, as elsewhere.
  The module never logs a path, file name, URL or document content. Levels follow what an
  operator must act on (see "Observability").
- **Constants, not settings.** The limits (two render slots; a 10 s slot wait; image
  memory of a quarter of the heap shared between the slots; charges of 16 bytes per
  decoded pixel and four times a JPEG's file size; 25 megapixels per decoded image;
  250 megapixels per source header; 32 MB per file used as is; 128 MB per file in the user
  agent; 4 megapixels per decoded CSS background; the HTML size above which a render goes
  straight to text-only mode; a 40-character right-to-left threshold; a zoom warning below
  0.4) are named `static final` constants in the class that owns them, each commented with
  its measured reason. No new `carlos.properties` keys: the image memory already follows
  the heap size, and no operator need for the others has been shown.
- **Printed text** (the document notes and "[image unavailable]") comes from
  `oscarResources`, as in the other PDF creators (`ConsultationPDFCreator`,
  `CaseManagementPrintPdf`); see "Printed text".
- **No `@Transactional`:** the module does no database work.
- Components are discovered by the existing broad
  `<context:component-scan base-package="io.github.carlos_emr.carlos">`. The bean names
  (`htmlPdfRenderService`, `printDocumentComposer`, `printImageService`,
  `printLayoutService`, `bundledFontFileService`, `renderedPdfValidator`) clash with no
  existing bean or class name (checked).

### Package dependencies

```
callers (ConvertToEdoc, Doc2PDF, future services)
   │   HtmlPdfRenderService and dto only
   ▼
service ──► support ──► dto, engine
   │  └───► engine
   └──────► dto
```

`dto` and `engine` depend on nothing else in the module; `support` uses only `dto` and
`engine` value types; only `service` sees everything. Nothing in the module imports
`jakarta.servlet`, Struts, a DAO, a manager or the legacy facades.
`HtmlPdfArchitectureUnitTest` enforces these rules (reading the source files, as
`OntarioAssemblerArchitectureUnitTest` does; wildcard imports are rejected so the check
cannot be bypassed). Moving `LocalOnlyUserAgent` into `engine` (with `git mv`, so its
history follows) is what keeps the graph acyclic: left in `documentManager`, the service
would use it while `documentManager.ConvertToEdoc` uses the service. After this change
nothing else uses it, and it imports only Flying Saucer, the JDK and `MiscUtils`.

### Package map

```
src/main/java/io/github/carlos_emr/carlos/documentManager/htmlpdf/
├── package-info.java                  module boundary, dependency rules, PHI rules
├── dto/
│   ├── package-info.java
│   ├── HtmlPdfRenderRequest.java      record: html, baseDirectory (nullable),
│   │                                  allowedDirectories, source
│   ├── HtmlPdfRenderSource.java       enum: FORM, HRM, EMAIL, MESSENGER (for logs)
│   ├── HtmlPdfRenderResult.java       record: PDF bytes + degradations
│   └── HtmlPdfRenderDegradation.java  enum (see "Resilience")
├── service/
│   ├── package-info.java
│   ├── HtmlPdfRenderService.java      public @Service: entry point, slots, attempts, ladder
│   ├── PrintDocumentComposer.java     @Service: pure DOM rules (steps a–i, k–m)
│   ├── PrintImageService.java         @Service: plans and prepares images (step j)
│   ├── PreparedPrintImages.java       AutoCloseable: one attempt's image directory
│   ├── PrintLayoutService.java        @Service: layout passes, placement on Letter
│   ├── RenderedPdfValidator.java      @Component: output checks → Result(messages, valid)
│   ├── BundledFontFileService.java    @Service: font extraction and verification
│   ├── PageZoomCalculator.java        static: zoom and pass-2 page size
│   ├── PrintMode.java                 enum: NORMAL, SAFE, TEXT_ONLY
│   └── HtmlPdfRenderException.java    unchecked, internal, Phase, PHI-safe message
├── support/                           static, dependency-free (domain nouns)
│   ├── package-info.java
│   ├── XhtmlCleanup.java              steps a, b, d–g, i, m
│   ├── PrintFormControls.java         step c
│   ├── RtlTextShaping.java            step h
│   ├── PrintStylesheet.java           the injected stylesheet (step k)
│   ├── FallbackFontRuns.java          step l
│   └── PrintMessages.java             printed notes and box text from oscarResources
└── engine/                            Flying Saucer and OpenPDF extensions
    ├── package-info.java
    ├── LocalOnlyUserAgent.java        moved from documentManager, changed (per render)
    ├── BundledFontResolver.java       ITextFontResolver subclass (per render)
    └── BundledFontSet.java            verified font files and glyph coverage (immutable)
```

Names follow `docs/architecture/layer-names.md`:

- Beans carry a role suffix where one fits (`*Composer`, `*Validator`) or the `*Service`
  fallback. `PrintImageService` plans and prepares the images in one class, because the
  plan and its application share all their state; a whole-document plan with file I/O is
  not the policy's `*Resolver` ("picks one value"). `PrintLayoutService` runs the layout
  passes and places the pages on Letter: placement is a few lines that only consume the
  layout's output.
- `RenderedPdfValidator` is a `@Component`, as the policy and `BillingOnReviewValidator`
  have it, kept package-private in `service` because nothing else uses it.
  `PageZoomCalculator` is a `final` class with static methods and a private constructor,
  like `BillingOnHistoryBalanceCalculator`.
- The boundary records are named `*Request`/`*Result` in `dto`, like Ontario billing's
  `FeeScheduleImportRequest`/`FeeScheduleImportResult`: the policy reserves `*Command` for
  write and validation inputs and `*Dto` for persistence and query shapes, and a render
  request is neither.
- Static utilities are domain nouns; engine types use Flying Saucer's vocabulary
  (`*UserAgent`, `*FontResolver`).
- No `*Manager`, `*Helper`, `*Utils` or compound suffixes.

**Typed boundary objects:**

- `HtmlPdfRenderRequest(String html, Path baseDirectory, List<Path> allowedDirectories,
  HtmlPdfRenderSource source)`, validated in its canonical constructor:
  - `html` and `source` not null;
  - `allowedDirectories` non-empty, each absolute, normalised and not a filesystem root;
    stored with `List.copyOf`;
  - `baseDirectory` either `null` (no webapp root: the service substitutes a private empty
    directory and reports `WEBAPP_ROOT_UNAVAILABLE`) or absolute and inside an allowed
    directory;
  - `toString()` prints only the source, the HTML length and the number of allowed
    directories, so logging a request can never write PHI or paths (a record's default
    `toString` would print the HTML).
- `HtmlPdfRenderSource`: which kind of document is being rendered (`FORM` from
  `ConvertToEdoc`'s form paths, `HRM`, `EMAIL`, `MESSENGER` from `Doc2PDF`); it carries no
  identifier and exists so an operator can tell which workflow degrades.
- `HtmlPdfRenderResult(byte[] pdf, Set<HtmlPdfRenderDegradation> degradations)`:
  - the constructor copies the bytes and stores an unmodifiable copy of the set;
  - `pdf()` returns a copy; `size()` and `writeTo(OutputStream)` avoid copying;
  - `equals`/`hashCode` compare the bytes' contents and `toString()` prints the size and
    the degradation names only (a record's defaults would compare array identity and
    print an array reference).
- `HtmlPdfRenderDegradation`: one constant per reportable step down (see "Resilience"),
  each with its log level (see "Observability").
- `HtmlPdfRenderException`: like Ontario billing's `BillingDataLoadException`, it carries a
  `Phase` (`PREPARE`, `IMAGES`, `LAYOUT_PASS_1`, `LAYOUT_PASS_2`, `PLACE`, `VALIDATE`) and
  a PHI-safe message (mode, phase, original exception class; never the original message);
  unlike it, it is package-private and is thrown only inside the service, which turns an
  exhausted ladder into `PDFGenerationException` with the last one as its cause.

**Documentation** follows `docs/billing-ontario-module.md` §9.2.2 and CLAUDE.md: each
package has a `package-info.java` stating its boundary and allowed dependencies; public
entry points have contract-level JavaDoc (inputs, degradations, failure semantics); inline
comments explain the why (wkhtmltopdf parity numbers, security and PHI constraints, the
ladder order, each limit's measured reason); no mechanical JavaDoc on trivial accessors;
CARLOS copyright header on new files; `@since 2026-10-29`; no `@author`.

### Components

| Class | Kind | Responsibility |
|---|---|---|
| `HtmlPdfRenderService` | public `@Service` | `HtmlPdfRenderResult render(HtmlPdfRenderRequest) throws PDFGenerationException`. Takes a render slot (bounded wait), runs attempts in `NORMAL`, `SAFE` and `TEXT_ONLY` mode (the ladder), owns each attempt's resources, and logs the outcome (see "Observability") |
| `PrintDocumentComposer` | `@Service` (`*Composer`) | `prepare(html, mode, degradations)` → Jsoup `Document` (steps a–i) and `finish(document, mode, degradations)` → XHTML (k–m); builds the text-only document in `TEXT_ONLY` mode. Pure: no file or network access |
| `PrintImageService` | `@Service` | Step j: resolves and probes every `<img>` and inline `<svg>`, plans each one's outcome against the slot's image memory (use, convert, downscale, clean SVG, box), then decodes, converts and writes into the attempt's image directory and rewrites or boxes each element; returns `PreparedPrintImages` |
| `PrintLayoutService` | `@Service` | Layout pass 1 and, when needed, pass 2; falls back to pass 1 at `612 / W` (`MARGINS_REDUCED`); flattens and places each page on Letter with the zoom; copies `/Title` |
| `RenderedPdfValidator` | `@Component` (`*Validator`) | Pure checks on the output → `Result(List<String> messages, boolean valid)`; messages name the failed check only |
| `BundledFontFileService` | `@Service` | Extracts, verifies and re-verifies the bundled font files; returns the shared `BundledFontSet` |
| `PageZoomCalculator` | static (`*Calculator`) | Zoom and pass-2 page size from the pass-1 width |
| `XhtmlCleanup`, `PrintFormControls`, `RtlTextShaping`, `PrintStylesheet`, `FallbackFontRuns`, `PrintMessages` | static utilities | The dependency-free markup rules and printed text |
| `LocalOnlyUserAgent` | engine extension (per render), moved to `engine` and changed | `createRestrictedRenderer(List<Path> allowedDirectories, FontResolver)`; explicit allowed directories; default stylesheet by exact URL; image caps; PHI-safe logging |
| `BundledFontResolver` | engine extension (per render) | Every family → a bundled face; minimum font size |
| `BundledFontSet` | engine value (immutable, shared) | The verified font paths, which fallback faces loaded, and `canDraw(face, codePoint)` through OpenPDF `BaseFont.charExists` (font tables are only read after loading, so one instance is shared by all renders) |
| `ReplacedElementFactoryImpl` | **deleted** | Replaced by Flying Saucer's own factory with all images loading through `LocalOnlyUserAgent` |
| `ConvertToEdoc` | legacy facade, changed | Per-element path translation; encoded `file:` URIs; marks unusable images; one webapp-root lookup; builds the request (`FORM` for `FormTransportContainer`, `EMAIL` for `EmailData`, `HRM` for `EFormData`, whose only production caller is `HRMPDFCreator` once the dead eForm entry points are deleted); maps the result to its `Path`-or-`null` contract |
| `Doc2PDF` (`util`) | legacy facade, changed | Builds the request (`MESSENGER`) from the webapp root; keeps its existing failure paths |
| `HRMPDFCreator`, `HRMUtil`, `PrintHRMReport2Action` | changed | A failed HTML conversion surfaces as `PDFGenerationException` |
| `EmailManager` | changed | Throws `EmailSendingException` when the message PDF cannot be built |
| `InternalEDocConverter`, `EDocConverterInterface` | deleted | wkhtmltopdf wrapper |
| Dead entry points listed under "Unused code" | deleted | No production callers |

All beans are safe to call concurrently (24 mixed renders on 8 threads gave identical
bytes, *tested* on the prototype; `HtmlPdfConcurrencyUnitTest` repeats it on the real
structure).

## Design

### Render pipeline

`HtmlPdfRenderService.render(request)`:

1. **Validate input.** Whitespace-only `html` throws `PDFGenerationException` (there is
   nothing to print). Markup with no visible content prints one blank page. Get the
   `BundledFontSet` (an unloadable DejaVu Sans throws `PDFGenerationException` at once:
   every mode needs it, so retrying is pointless). If `baseDirectory` is `null`, create a
   private empty base directory for this render (deleted when the render ends) and record
   `WEBAPP_ROOT_UNAVAILABLE`.
2. **Take a render slot** (see "Time and memory bounds"): wait at most 10 s. Without a
   slot, the render continues with every raster image planned as the box
   (`IMAGES_OMITTED_FOR_MEMORY`, only if the document has images). The slot is released in
   a `finally` when the render ends.
3. **Run attempts** in `NORMAL`, then `SAFE`, then `TEXT_ONLY` mode until one succeeds
   (see "Resilience"); HTML over the size limit starts at `TEXT_ONLY`. Each attempt starts
   again from the request's HTML string with a fresh degradation set, so nothing from a
   failed attempt leaks into the next; only the render-level degradations (the base
   directory, a missing slot) carry forward. One attempt, in outline:

   ```java
   Document dom = printDocumentComposer.prepare(html, mode, degradations);      // a–i
   try (PreparedPrintImages images =
           printImageService.prepare(dom, request, mode, imageMemory, degradations)) { // j
       String xhtml = printDocumentComposer.finish(dom, mode, degradations);     // k–m
       byte[] pdf = printLayoutService.render(
               xhtml, baseDirectory, allowedDirectoriesWith(images), fontSet,
               degradations);                                                    // lay out, place
       RenderedPdfValidator.Result check = renderedPdfValidator.validate(pdf);    // check
       ...
   } // the image directory is deleted here, whatever happened
   ```

   The document steps, in this order (Jsoup parsing):
   - a. remove `<script>` elements and all comment nodes (comments never render; an HTML
     comment containing `--` is not valid XML);
   - b. from text nodes and from the `value` attributes of form controls, remove soft
     hyphens (U+00AD, which Flying Saucer prints as visible hyphens) and invisible bidi
     formatting characters (U+200E, U+200F, U+061C, U+202A–U+202E, U+2066–U+2069);
   - c. `PrintFormControls` (see "Form controls");
   - d. rewrite an inline `style` that sets `position: fixed` to `position: absolute`
     (Flying Saucer repeats fixed elements on every page, overprinting body text);
   - e. on `<ol type="…">` without an inline `list-style-type`, set `list-style-type`
     (`a` → `lower-alpha`, `A` → `upper-alpha`, `i` → `lower-roman`, `I` → `upper-roman`,
     `1` → `decimal`);
   - f. **clamp `rowspan`:** for each `td`/`th` whose `rowspan` is 0 or larger than the
     number of rows from its row to the end of its row group (`thead`, `tbody`, `tfoot`, or
     the table), set it to that number, as the HTML table model does (*tested* on
     `formannualfemale.jsp`: without it, the following table is drawn over the cell);
   - g. add class `carlos-pdf-paginate` to each `<table>` that has a direct `<thead>` child
     and no cell of its own with a `rowspan` other than 1 (Flying Saucer's pagination
     breaks row-span heights, *tested*);
   - h. `RtlTextShaping` (see "Right-to-left text");
   - i. add `alt=""` to `<img>` without one;
   - j. images: `PrintImageService` (see "Images"), the only step with side effects before
     layout (temporary files). In `TEXT_ONLY` mode there are no images and `prepare`
     returns an empty holder with no directory;
   - k. ensure a `<head>` exists and insert `<style id="carlos-pdf-defaults">`
     (`PrintStylesheet`, see "Injected stylesheet") as its **first** child, so author rules
     of equal specificity still win, while its `@page` rule wins anyway because it is
     `!important` (*tested*: with the defaults first, an author A4-landscape `@page` lost
     and an author class font size won); prepend the document note, if any (see
     "Resilience"), as the first child of `<body>`;
   - l. `FallbackFontRuns` (see "Fonts"), after the note and the boxes so their text is
     covered too;
   - m. serialise with XML syntax, XHTML escaping, UTF-8 and pretty-printing off. A
     `<!DOCTYPE html>` added by `ConvertToEdoc.parseDocument` is kept, so standards mode is
     used, as with wkhtmltopdf.

   Steps a–i and k–m are pure DOM rules (`PrintDocumentComposer` and the `support`
   utilities). Images run after the other element rules, which do not depend on them, so
   that every side effect sits in one step whose resources one scope owns. This replaces
   `ConvertToEdoc.prepareDocumentForFlyingSaucer` and the Jsoup steps in
   `Doc2PDF.renderWithFlyingSaucer`.

   Then, in the same attempt:
   - **Lay out** (`PrintLayoutService`, see "Output geometry"). Each pass builds its
     renderer with `LocalOnlyUserAgent.createRestrictedRenderer(allowedDirectories,
     new BundledFontResolver(fontSet))`, where `allowedDirectories` is the request's list
     plus the attempt's image directory and, when the request had no base directory, the
     private base directory. It uses
     `ITextRenderer(float, int, ITextOutputDevice, ITextUserAgent, FontResolver)` with
     CARLOS's existing 26.666666 dots per point and 20 dots per pixel. Print mode on,
     interactive off, smoothing threshold 0, Flying Saucer's own
     `ITextReplacedElementFactory`, `setDocumentFromString(xhtml, the base directory's
     URI)`, `layout()`, `createPDF(buffer, true)`.
   - **Place on Letter** (`PrintLayoutService`): flatten any AcroForm fields, import each
     page onto a 612 × 792 pt page scaled by the zoom, copy `/Title`.
   - **Check** (`RenderedPdfValidator`): starts with `%PDF-`; PDFBox loads it with at least
     one page; every page 612 × 792 pt; no `/AcroForm`. An invalid result fails the
     attempt.

Any `Exception` or `LinkageError` (for example `NoClassDefFoundError`) inside an attempt is
caught, logged (see "Observability") with the mode, phase and exception class only, and
handled by the ladder; a `LinkageError` is still retried, because safe mode can avoid the
failing path (for example Batik, for inline SVG). Exception messages from the engine can
quote document text, so the original exception is never chained or logged: the attempt's
`HtmlPdfRenderException` records the mode, phase and original exception class and copies
the original stack trace (useful line numbers, no message). The last one becomes the cause
of the final `PDFGenerationException`, so callers that log the whole exception (for
example `Doc2PDF`'s `logger.error(..., e)`) log no PHI. Other `Error`s (including
`OutOfMemoryError`) are not caught. No partial bytes are ever returned.

### Time and memory bounds

Flying Saucer's layout cannot be interrupted, so the module bounds what it starts rather
than timing it out:

- **Render slots.** A fair `Semaphore` of two slots in `HtmlPdfRenderService`; a render
  waits at most 10 s for one (`ConvertToEdoc` is already serialised, so the second slot
  serves Messenger renders). This is the only wait in the module, so a fax with several
  attachments queued behind `ConvertToEdoc`'s lock stays far inside nginx's 300 s
  `proxy_read_timeout` (`debian/assets/nginx/proxy-params.conf`).
- **Image memory per slot.** A quarter of the heap is set aside for images and shared
  equally by the two slots: 256 MB per slot with the Debian default heap of 2 GB
  (`CARLOS_JAVA_XMX`). Each document's images are planned to fit its slot's share (see
  "Images"), so the reductions depend only on the document and the heap size, never on
  load, and output stays deterministic.
- **Document size.** HTML larger than a limit (measured in PR 1 and set so that the
  slowest normal-mode layout of a reference server stays under 30 s; the 2000-row table
  fixture is the reference) goes straight to `TEXT_ONLY`. `data:` URI payloads do not
  count towards it.
- **Performance budget**, checked in PR 1 against the wkhtmltopdf baseline: warm renders
  of the real form fixtures under 2 s at the 95th percentile; the worst case, with a 10 s
  slot wait and all three attempts, under 60 s.

### Resilience: failure is the last resort

Each row is tried in order; an error reaches the caller only from the last column, and
only where it says so. Every step down is recorded as a `HtmlPdfRenderDegradation` on the
result and logged once per render by `HtmlPdfRenderService` (see "Observability").
Facades do not log them again.

| Failure point | Mitigations, in order | Last resort |
|---|---|---|
| Large JPEG photo (up to 32 MB, any resolution) | None needed: embedded as is, at full resolution (it is never decoded) | — |
| Other image over the per-image cap (25 megapixels, or the slot's share: 16.7 megapixels with a 2 GB heap), or any image file over 32 MB | Read as a stream with `ImageIO` (never loaded whole), subsampled with `ImageReadParam.setSourceSubsampling` to fit the cap when larger; keep the printed size (`IMAGE_DOWNSCALED` when reduced, otherwise `IMAGE_CONVERTED`) | The box |
| Image format OpenPDF cannot draw (for example WebP, WBMP, PNM) but `ImageIO` can read | Convert with `ImageIO` (subsampled if needed) to JPEG or PNG (`IMAGE_CONVERTED`) | The box |
| Valid header, corrupt data | Decode with `ImageIO` (tolerant reader) and re-encode as PNG (`IMAGE_CONVERTED`) | The box |
| SVG with outside references | Remove the referencing elements, attributes, `url()` values, `@import` rules and processing instructions; render the rest (`SVG_REFERENCES_REMOVED`) | The box (unparseable SVG) |
| Image missing, remote, unreadable or of an unknown format (for example HEIC) | — (remote sources are never fetched) | The box (`IMAGE_UNAVAILABLE`) |
| Image header declaring over 250 megapixels (malformed or a decompression bomb) | — (never decoded) | The box (`IMAGE_UNAVAILABLE`) |
| Document's images exceed the slot's image memory | Downscale the largest decoded or converted images further, and convert as-is JPEGs that still do not fit, keeping printed sizes, until the total fits (`IMAGE_DOWNSCALED`) | — |
| No render slot free within 10 s | — | Render with every raster image as the box and a document note (`IMAGES_OMITTED_FOR_MEMORY`); a document without images renders normally |
| Layout pass 2 fails | Use pass 1 (complete content on a widened page) placed at `612 / W`: margins come out smaller than 10 mm (`MARGINS_REDUCED`) | Next row |
| Layout fails (both passes), placing or the output check fails | Retry in **safe mode**: author `<style>`, `<link rel="stylesheet">` and `style` attributes removed, inline `<svg>` as the box, with the document note (`SAFE_MODE`) | Next row |
| Safe mode fails, or the HTML is over the size limit | **Text-only mode**: the document's text in reading order (block elements as paragraphs, table rows as lines with cells separated by " \| ", form-control replacements kept, no images), with the document note (`TEXT_ONLY`) | `PDFGenerationException("HTML could not be converted to PDF", cause)` |
| Webapp root cannot be resolved | The facade passes `baseDirectory = null`; the service renders against a private, empty base directory with the remaining allowed directories; webapp stylesheets and images are unavailable and show as the box (`WEBAPP_ROOT_UNAVAILABLE`) | — |
| A fallback font (WenQuanYi or Symbola) cannot be loaded | Characters that needed it print as U+FFFD (`FALLBACK_FONT_UNAVAILABLE`) | — |
| The font directory is foreign-owned or a symlink | Use a fresh private directory for this JVM (`BundledFontFileService`) | — |
| DejaVu Sans cannot be loaded from the classpath (a build defect) | — | `PDFGenerationException`, at once |
| Right-to-left text | Shape and reorder (see "Right-to-left text"); a long passage adds the document note (`RTL_BEST_EFFORT`) | — |
| `ConvertToEdoc` cannot save the temporary file | — (a full disk or a permission error does not heal on retry) | `null` (existing contract) |
| Whitespace-only input | — | `PDFGenerationException` (nothing to print) |

**The document note** is one bordered box (`carlos-pdf-note`, DejaVu Sans 8 pt) at the top
of the first page listing, in fixed wording, each applicable reason:

- `SAFE_MODE`: "This document was printed with simplified formatting because its layout
  could not be rendered."
- `TEXT_ONLY`: "This document was printed as plain text because its layout could not be
  rendered."
- `IMAGES_OMITTED_FOR_MEMORY`: "Images were omitted because the server was busy. Print
  again to include them."
- `RTL_BEST_EFFORT`: "Right-to-left text was converted for printing. Check the line order
  of longer right-to-left passages against the original."

Other degradations are visible where they happen (the box) or do not change content
(downscaled or converted images, smaller margins) and are only reported.

**Observability.** `HtmlPdfRenderService` writes one line per render with the fixed token
`htmlpdf-render`, the source, the mode that succeeded, the duration and the degradation
names (no path, no content), at the level of its most serious degradation:

| Level | Degradations | Meaning for an operator |
|---|---|---|
| DEBUG | none | Normal |
| INFO | `IMAGE_DOWNSCALED`, `IMAGE_CONVERTED`, `SVG_REFERENCES_REMOVED`, `MARGINS_REDUCED`, `RTL_BEST_EFFORT` | Content kept; nothing to do |
| WARN | `IMAGE_UNAVAILABLE`, `IMAGES_OMITTED_FOR_MEMORY`, `FALLBACK_FONT_UNAVAILABLE`, `WEBAPP_ROOT_UNAVAILABLE` | Content visibly missing; check the source or the server's load |
| ERROR | `SAFE_MODE`, `TEXT_ONLY` | A template or engine defect to fix |

Each failed attempt logs WARN with the mode, phase and exception class; a `LinkageError`
logs ERROR (a packaging defect). The root logger defaults to WARN (`LOG_VERBOSITY`), so
routine INFO lines appear only when an operator raises it. There are no metrics: CARLOS has
no metrics registry (the pom imports the Micrometer BOM only for Spring Security), and the
log token is greppable.

**Printed text** (`PrintMessages`). The four note sentences and the box text
"[image unavailable]" are `oscarResources` entries (`htmlpdf.note.safeMode`,
`htmlpdf.note.textOnly`, `htmlpdf.note.imagesOmittedForMemory`,
`htmlpdf.note.rtlBestEffort`, `htmlpdf.imageUnavailable`), read with `Locale.ENGLISH` and
`ResourceBundle.Control.getNoFallbackControl(FORMAT_PROPERTIES)`, so the output never
depends on the server's default locale. The keys are added to all five
`oscarResources_*.properties` files, as the i18n workflow's "New Key Parity" check
requires, with French, Spanish, Polish and Brazilian Portuguese values for review in PR 1.
Printing in the reader's language needs callers to pass a locale (see "Follow-ups").

### Form controls (`PrintFormControls`)

Each replacement copies the original element's `id`, `class`, `style`, `title`, `dir` and
`lang`, adds `class="carlos-pdf-control carlos-pdf-<kind>"` and
`data-input-type="<type>"`, and keeps document order. Text in replacements is ordinary
text, so right-to-left shaping and the fallback-font step apply to it.

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

### Images (`PrintImageService`, `LocalOnlyUserAgent`)

**What large images cost** (measured with Flying Saucer 10.7.0, one image per page, FAKE
content):

| Image | File | Smallest heap that rendered it |
|---|---|---|
| 12-megapixel JPEG photo | 1 MB | 64 MB (peak 30 MB) |
| 48-megapixel JPEG photo | 7 MB | 64 MB (peak 39 MB) |
| 8.4-megapixel PNG (a 300-dpi Letter scan) | 11 MB | 128 MB |
| 25-megapixel RGBA PNG | 30 MB | 512 MB |
| 67-megapixel PNG (a 600-dpi tabloid scan) | 95 MB | 1 GB |

Flying Saucer embeds a JPEG without decoding it, so a JPEG costs about its file size
whatever its resolution; every other format is decoded, at 14–15 bytes per pixel. Reading
with `ImageIO` from a file stream with source subsampling holds only the result: the 95 MB,
67-megapixel PNG was reduced to 3300 × 5100 in a 96 MB heap in 3.5 s, and a 48-megapixel
WebP to 4000 × 3000 in a 96 MB heap (*tested*).

So the rules let through everything that is cheap, at full resolution, and reduce only
what would otherwise exhaust memory, never below what paper shows: 25 megapixels on a full
Letter page is about 520 dpi and 16.7 megapixels about 420 dpi, above fax (200 dpi) and
office printers (300–600 dpi). For comparison, common tools refuse or cap images at
similar sizes: sharp refuses above 268 megapixels, Debian's ImageMagick policy caps the
area at 256 megapixels, Pillow refuses above 179 megapixels, Thumbor at 75 megapixels.

**Before layout** (step j), `PrintImageService` first plans every `<img>` in document
order, reading only (rules 1–5), then carries the plan out (the SVG file in rule 3,
rules 6 and 7):

1. **Resolve the source as the renderer will.** A `data:` URI is decoded. Otherwise the
   source must be a valid URI reference: `baseDirectory.toUri().resolve(new URI(src))`,
   with the render's base directory (a raw space or other invalid character makes it
   unusable). The result must be a `file:` URI naming an existing regular file inside an
   allowed directory. Remote, empty and `data-carlos-image-unavailable` sources are
   unusable; remote sources are never fetched.
2. **Type.** Unusable if it is a PDF (starts with `%PDF-`; Flying Saucer 10.7.0 would
   otherwise render it as an image page). There is no file-size limit: a large file is
   read as a stream (rule 6), never loaded whole.
3. **SVG.** If the content is SVG: elements, attributes and CSS `url()` values that point
   anywhere other than a fragment (`#…`) or a `data:` URI are removed, as are `@import`
   rules (string or `url()` form) and processing instructions such as
   `<?xml-stylesheet?>` (Batik, which Flying Saucer 10.7 uses to draw SVG, honours both,
   and its default resource security allows same-host and `file:` loads), and the cleaned SVG
   is written to the attempt's image directory and used (`SVG_REFERENCES_REMOVED`).
   An SVG that cannot be parsed is unusable. Inline `<svg>` elements get the same cleaning
   in place. SVG skips rules 4–6.
4. **Header.** Read only the header with `ImageIO`, without decoding. TwelveMonkeys
   `imageio-webp` is added (see "Removals and Packaging") so WebP is recognised. Unusable
   if no reader recognises it (for example HEIC), if the header cannot be read, or if it
   declares more than 250 megapixels (about 15 800 × 15 800, nearly four times a 600-dpi
   tabloid scan): such a header is malformed or a decompression bomb, and decoding it would
   occupy a server thread for minutes.
5. **Plan.** Each image gets one of three plans, with a memory charge:
   - **as is, JPEG:** a JPEG of at most 32 MB that OpenPDF can embed, at any resolution;
     charged four times its file size (the bytes are held by the renderer, the layout
     PDF, the page import and the final PDF);
   - **as is, decoded:** another format OpenPDF can embed (PNG, GIF, BMP, TIFF) of at most
     32 MB and at most the per-image cap; charged 16 bytes per pixel;
   - **converted:** everything else `ImageIO` can read (a larger file, a decoded format over
     the cap, a format OpenPDF cannot embed such as WebP or WBMP), planned at the largest
     subsampling that fits the cap, or at full size when already under it; charged
     16 bytes per output pixel.

   The memory available is the render slot's share (see "Time and memory bounds"):
   256 MB with a 2 GB heap. The per-image cap is the smaller of 25 megapixels and what that
   share holds at 16 bytes per pixel (16.7 megapixels with a 2 GB heap). If the document's
   total charge (each distinct file or `data:` payload once) exceeds the share, the largest
   decoded or converted images are planned smaller, and as-is JPEGs that still do not fit
   are converted, until it fits. Without a render slot, every raster image is planned as
   the box. The plan depends only on the document and the heap size.
6. **Decode check and conversion.** An as-is image is checked once
   with OpenPDF `org.openpdf.text.Image.getInstance(bytes)`, the call Flying Saucer uses
   (for a JPEG this reads only its header); on failure it is converted instead. A
   converted image is read by an `ImageIO` reader from a file `ImageInputStream` (or the
   decoded `data:` bytes) at the planned subsampling and written into the attempt's image
   directory: as JPEG at quality 0.9 when the source is a JPEG or WebP without
   transparency (so the result is again cheap to embed), otherwise as PNG (lossless, for
   scans and text) (`IMAGE_DOWNSCALED` when reduced, `IMAGE_CONVERTED` otherwise). If
   `ImageIO` cannot decode it, it is unusable.
7. **Rewrite.** A usable `<img>` gets its `src` set to the checked or converted file's
   `Path.toUri()` (encoded, so paths with spaces or accents load, *tested*); `data:` sources
   are kept. If the image was downscaled and the element has no `width`/`height`, they are
   set to the original pixel size in CSS px, so the printed size does not change.

An unusable `<img>` is replaced by
`<span class="carlos-pdf-image-unavailable">[image unavailable]</span>` (text from
`PrintMessages`), sized from the image's `width`/`height` attributes or inline style when
present (`IMAGE_UNAVAILABLE`), and a WARN is logged with the reason only (no path, no URL).

The attempt's image directory is a private temporary directory owned by
`PreparedPrintImages` and deleted when it closes.

**During layout** (`LocalOnlyUserAgent.getImageResource`, defence in depth and for CSS
background images, which are not in the plan because they can come from stylesheets and
cannot show a box): the override handles `data:` and `file:` sources, refuses files over
128 MB (above the largest converted output, a 25-megapixel PNG of noise at under 100 MB) and
reads the header before any decoding. A JPEG background loads as is; another background
over 4 megapixels is decoded with subsampling to fit 4 megapixels (at most 64 MB each)
instead of being dropped; a header over 250 megapixels, or any exception while loading,
returns an empty image resource (Flying Saucer draws nothing for it), with a WARN without
the path. Otherwise it delegates to `super.getImageResource(uri)`, keeping Flying Saucer's
resolution scaling. No form JSP in the repository uses a CSS background image (checked),
so this matters mainly for HRM and email HTML.

**`ConvertToEdoc`'s part:**

- `translateResourcePaths`, `translatePaths` and `validateResourcePaths` collect their work
  per element (`LinkedHashMap<Element, List<String>>`) instead of keyed by the candidate
  path list, so every occurrence of a repeated image is translated and validated (today
  only the last is).
- Translated paths are emitted as encoded `file:` URIs (`Path.toUri()`), in `src`, `href`,
  CSS `url()` and the `pdfMediaStylesheet` link.
- Where `validateResourcePaths` (line ~1017) or `translatePaths` (lines ~905/911) today
  removes an `<img>` (remote, `file:`, unresolvable, or an extension outside `FileType`), it
  instead removes the `src` and sets `data-carlos-image-unavailable`, so the box is shown.
  No URL reaches the renderer. `<link>` and `<script>` handling is unchanged, and remote CSS
  `url()` stays blanked.
- When the static `realPath` is null, `ConvertToEdoc` sets it from the same webapp-root
  lookup used for the request (below), so path translation and rendering use one root.

Measured through `Doc2PDF`'s path: CMYK and progressive JPEG, 16-bit and alpha PNG, GIF
(first frame), BMP and TIFF load; corrupt and zero-byte files show the box. WebP converts
with the TwelveMonkeys reader (*tested* standalone; through the pipeline in PR 1); HEIC has
no Java reader and shows the box. In `ConvertToEdoc`, only `jpeg`, `jpg`, `png` and `gif`
files pass `FileType`; other image files, WebP included, show the box (today they silently
disappear); WebP still prints there when embedded as a `data:` URI, and always through
`Doc2PDF`.

### Right-to-left text (`RtlTextShaping`)

Best effort for all right-to-left text, never failing. For each text node outside
`<style>` and `<title>` that contains a code point whose `Character.getDirectionality` is
`RIGHT_TO_LEFT` or `RIGHT_TO_LEFT_ARABIC`:

1. Arabic-script letters are shaped to their joined presentation forms with ICU4J
   `com.ibm.icu.text.ArabicShaping` (`LETTERS_SHAPE`, logical order). DejaVu Sans regular
   and bold both contain the Arabic presentation forms and Hebrew.
2. The text is reordered into visual order with ICU4J `com.ibm.icu.text.Bidi`
   (`writeReordered` with `DO_MIRRORING`, so brackets mirror), with the paragraph direction
   taken from the nearest `dir` attribute, or from the first strong character when there is
   none.
3. On the nearest block ancestor with `dir="rtl"` and no explicit `text-align`,
   `text-align: right` is set.
4. If any right-to-left run is longer than 40 characters (long enough to wrap), the
   document note `RTL_BEST_EFFORT` is added: Flying Saucer breaks the reordered text into
   lines from the left, so a passage that wraps prints its lines in reverse order.

Reordering works within one text node; a passage split across inline elements is reordered
piece by piece. Short right-to-left text (names, field values, short notes) prints
correctly; a Hebrew prototype read correctly (*tested*); Arabic shaping is to be tested in
PR 1. This step runs before the fallback-font step, because fallback spans would split
runs.

`com.ibm.icu:icu4j` is already on the classpath at 78.1 through HAPI FHIR
(`hapi-fhir-structures-dstu3` → `org.hl7.fhir.utilities`); because CARLOS code now imports
it, it is declared explicitly in `pom.xml` at the locked version.

### Output geometry (`PrintLayoutService`, `PageZoomCalculator`)

Constants: Letter `612 × 792 pt`; margin `M = 28.3465 pt` (10 mm); default zoom `0.8`.

1. **Pass 1.** The defaults contain
   `@page { size: 765pt 990pt !important; margin: 35.4331pt !important }` (Letter and 10 mm
   divided by 0.8). Render with `setScaleToFit(true)`, so content wider than the page widens
   the page instead of being clipped. `W` is the largest page width in the output
   (scale-to-fit widens every page to the same width, *tested*).
2. **If `W ≤ 765.5 pt`**, use pass 1 with `z = 0.8`.
3. **Otherwise** `PageZoomCalculator` computes `C = W − 2M/0.8`, `P = C / (1 − 2M/612)`,
   `z = 612 / P`. Pass 2 replaces the `@page` rule with
   `@page { size: P pt (792/z) pt !important; margin: (M/z) pt !important }` and renders
   without scale-to-fit. Use pass 2 with that `z`. If `z < 0.4`, log a WARN with `z` only.
4. **Place** (`PrintLayoutService`). Flatten AcroForm fields with OpenPDF
   `PdfStamper.setFormFlattening(true)` (defensive: `PrintFormControls` leaves none).
   Create a 612 × 792 pt document with no margins; for each page, `getImportedPage` and
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

### Injected stylesheet (`PrintStylesheet`)

```css
@page { size: 765pt 990pt !important; margin: 35.4331pt !important }  /* pass 1; see geometry */
html { line-height: 1.164 }                     /* WebKit "normal" for DejaVu Sans */
html, body { margin-top: 0 !important }          /* WebKit drops the top margin at the page start */
body > *:first-child { margin-top: 0 !important }
body { word-wrap: break-word }                  /* long URLs/values wrap instead of forcing shrink */
table.carlos-pdf-paginate { -fs-table-paginate: paginate }  /* repeat thead (step g) */
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
.carlos-pdf-note { border: 1px solid #000; padding: 4px; margin: 0 0 6px 0; font-size: 8pt }
```

Flying Saucer 10.7.0 ignores `overflow-wrap`; it supports `word-wrap: break-word`. Without
it, one 600-character URL shrank a whole document to zoom 0.145 (*tested*; with it, 0.8).
A box narrower than its text keeps its size and shows "[image un…" rather than breaking
mid-word (*tested*).

### Fonts (`BundledFontResolver`, `FallbackFontRuns`, `BundledFontFileService`)

**Mapping.** With production fonts (`fonts-dejavu-core` only), wkhtmltopdf prints every
generic and unknown family (`serif`, `Times New Roman`, `monospace` including `<pre>`,
`Courier New`, `Arial`, `Verdana`, unstyled text) in DejaVu Sans (measured). For parity,
every family resolves to one registered family:

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
it (`BundledFontSet.canDraw`): WenQuanYi Micro Hei, then Symbola. Maximal runs with the same face are wrapped in
`<span style="font-family:'CARLOS CJK'; font-style:normal">` (or `'CARLOS Symbol'`);
fallback faces have no italic, so the span forces upright. A code point neither can draw is
replaced with U+FFFD, which DejaVu Sans draws, so the gap is visible; a DEBUG log records
only the count. The same input always produces the same spans (*tested*).

Coverage (OpenPDF `BaseFont.charExists`): DejaVu Sans (regular and bold) covers accented
Latin, Greek, Cyrillic, Hebrew, Arabic presentation forms, math, units, arrows,
punctuation, ☐ ☒ ◉ ○ • ✓ and U+FFFD. WenQuanYi Micro Hei covers Han, kana, Hangul,
Bopomofo, CJK punctuation and fullwidth forms. Symbola covers most remaining symbols (for
example 𝄞).

**Font files** (`BundledFontFileService`). Flying Saucer's `addFont` reads a filesystem
path (its `BaseFont` overload re-reads the path to extract names), and DejaVu ships inside
the `jasperreports-fonts` jar (compile scope, already used by `PdfFonts`).

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
- Owner-only permissions are the tamper control.
- The verified state is published as an immutable `BundledFontSet` (paths, which fallback
  faces loaded, the loaded `BaseFont`s for coverage) in a `volatile` field; a
  re-verification builds a new set and replaces it, so a render always sees one complete,
  consistent set. Each render creates its own `BundledFontResolver` from the set, because
  a Flying Saucer font resolver belongs to one renderer.

On Debian `java.io.tmpdir` is `/var/lib/carlos-emr/catalina/temp` (`CATALINA_TMPDIR`,
`carlos-emr-tomcat` line 178; in `ReadWritePaths`; not aged by tmpfiles). In the Podman
example it is `/carlos-tmp` (`-Djava.io.tmpdir=/carlos-tmp`).

Measured cost: first render about 0.95 s including extraction; warm renders 81–300 ms (an
email about 118 ms; the Annual form with two passes 81–115 ms).

### Resource loading and security (`LocalOnlyUserAgent`)

`LocalOnlyUserAgent` moves to `htmlpdf/engine` (see "Package dependencies") and takes plain
types, so the engine package does not depend on the request record.

- **Explicit allowed directories.**
  `createRestrictedRenderer(List<Path> allowedDirectories, FontResolver)` receives the
  request's `allowedDirectories` plus the attempt's image directory and, when the request
  had no base directory, the private base directory. The implicit roots are
  removed: the base URL's directory, the JVM working directory, and the static
  `java.io.tmpdir` and `catalina.base/work` roots. A `file:` URI is allowed only if its
  canonical path is inside one of them.
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
- **XXE.** CARLOS's path parses through Flying Saucer's `XMLResource` SAX path and entity
  resolver; zero requests on 10.0.6 and 10.7.0.
- **PHI-safe logging.** Blocked requests log the scheme and, for network schemes, the host
  only. Blocked `file:` requests, image outcomes and degradations log no path and no
  content. Flying Saucer 10.7.0's `Html5Support` warnings name only tags and CSS
  properties.
- **Flying Saucer's own logging.** Flying Saucer can log whole text strings: with
  `xr.renderer.replace-missing-characters` switched on, `ITextOutputDevice` logs each line
  that has a character missing from its font, at INFO (read in the 10.7.0 bytecode). It
  has two logging paths: the legacy `XRLog`, which writes through `java.util.logging` and
  is off unless `xr.util-logging.loggingEnabled` is set, and SLF4J in newer classes, which
  CARLOS routes to Log4j 2 (`log4j-slf4j2-impl`). The module never sets either property
  (`FallbackFontRuns` makes every character drawable or U+FFFD first), and `log4j2.xml`
  gains `<Logger name="org.xhtmlrenderer" level="warn"/>` so that raising CARLOS's
  verbosity (for example the devcontainer's `debug-on`) never turns on the library's INFO
  and DEBUG output.

**Callers' requests:**

| Caller | `baseDirectory` | `allowedDirectories` |
|---|---|---|
| `ConvertToEdoc` | webapp root: `realPath` if set, else `getRealPath("/")` of the `ServletContext` bean that `ServletContextConfig` already exposes (`applicationContext.xml` line 41), obtained with `SpringUtils.getBean(ServletContext.class)`, which works on any thread; if neither resolves, `null` (the service substitutes a private empty directory: `WEBAPP_ROOT_UNAVAILABLE`) | webapp root if resolved; `CarlosProperties.getEformImageDirectory()` (`EFORM_IMAGES_DIR`, else derived from `BASE_DOCUMENT_DIR`), omitted if it cannot be resolved; `java.io.tmpdir`; `catalina.base/work` if set |
| `Doc2PDF` | webapp root from `request.getServletContext().getRealPath("/")` (today's base); if that is null, `null` (as above) | webapp root if resolved; `java.io.tmpdir`; `catalina.base/work` if set |

### Errors

| Caller | After |
|---|---|
| `HtmlPdfRenderService.render` | Returns `HtmlPdfRenderResult` with its degradations whenever any attempt in the ladder succeeds. Throws `PDFGenerationException` only for whitespace-only input, an unloadable DejaVu Sans, or an exhausted ladder; never returns partial bytes. An invalid request cannot reach it: the record's constructor rejects it (`NullPointerException`/`IllegalArgumentException`, a programming error) |
| `ConvertToEdoc.execute` | `tidyDocument`, rendering and saving inside one `try`; builds the request inside it too; catches `PDFGenerationException`, `IOException` and `RuntimeException`; returns `null` only on final failure (contract unchanged), logging a fixed message, the `LogSafe`-sanitised file name and the cause class |
| `FormsManagerImpl.renderForm`, `FaxDocumentManagerImpl.getFormFaxDocument` | Unchanged: they already turn `null` into `PDFGenerationException` naming the form |
| `HRMPDFCreator.printPdf` | Declares `throws PDFGenerationException`; the HTML branch throws it when `ConvertToEdoc` returns `null` (the existing `IOException`/`DocumentException` handling for text reports is unchanged) |
| `HRMUtil.renderHRM` | Rethrows as `PDFGenerationException` naming the HRM report (it already declares the type) |
| `EctConsultationFormRequestPrintAction22Action` | Unchanged: the `printPdf` call (line ~298) is inside the `try` whose `catch (PDFGenerationException)` is at line ~343 |
| `PrintHRMReport2Action` | Adds `PDFGenerationException` to its existing `IOException \| RuntimeException` handling (same error response) |
| `EmailManager.createMessageAttachment` | Declares and throws `EmailSendingException("Encrypted message could not be converted to PDF")` when the path is `null`; `encryptEmail` already declares it |
| `Doc2PDF.GetPDFBin` / `parseString2Bin` | Unchanged: their existing `catch (Exception)` (lines ~550, ~265) also covers request construction; return `null`; Messenger records the item as failed (`MsgAttachPDF2Action`, line ~284) |
| `Doc2PDF.PrintPDFFromHTMLString` | Unchanged: sends its existing error response |

Callers that name the document (forms, fax, HRM) give the user the reason for any final
failure.

## Removals and Packaging

- **`pom.xml`:**
  - Flying Saucer `10.0.6` → `10.7.0` (keep the `com.github.librepdf:openpdf` exclusion).
  - Declare `com.ibm.icu:icu4j` explicitly at the locked version 78.1 (CARLOS code now
    imports it; it was only transitive through HAPI FHIR).
  - Add `com.twelvemonkeys.imageio:imageio-webp` at `${twelvemonkeys.version}` (3.15.3, the
    release of the existing `imageio-tiff`; BSD 3-clause, pure Java, read-only). Its
    dependencies (`imageio-core`, `imageio-metadata`, `common-*`) are already locked at
    3.15.3, so it adds one 80 KB jar. It registers with `ImageIO` the same way as the TIFF
    reader that `ImagePDFCreator` already relies on.
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
- **Messages** (added in PR 1): the five `htmlpdf.*` keys (see "Printed text") in all five
  `oscarResources_*.properties` files (UTF-8, as `scripts/check-i18n-properties.sh`
  checks).
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
  - New `docs/html-pdf-rendering.md`: the module guide (package map, layering, pipeline,
    resilience ladder, degradations, how to add a rule), in the style of
    `docs/billing-ontario-module.md`; linked from `CLAUDE.md`.
  - `CLAUDE.md`, "PDF and Report Fonts": one line pointing HTML-to-PDF at
    `HtmlPdfRenderService` and the new guide.
  - `docs/pdf-dependency-consolidation-plan.md`: mark ultrabuk and openpdf-html removed and
    give `LocalOnlyUserAgent`'s new package.
  - `utils/readme.txt`: remove the wkhtmltopdf section.
  - `ConvertToEdoc` class JavaDoc (line ~84, "Fallback conversion via Flying Saucer") and
    `HRMPDFCreator` JavaDoc (line ~126): describe the single engine.
  - `src/main/resources/log4j2.xml`: the `org.xhtmlrenderer` logger at WARN (see
    "Resource loading and security").
  - Historical records (`docs/pdf-migration-functional-equivalence-findings.md`) stay as
    they are.

## Testing

Conventions (CLAUDE.md and `docs/billing-ontario-module.md` §10–11): JUnit 5;
`should<Action>_<context>()`; services are plain constructor-injected classes, so unit
tests (in the same packages, so package-private types and test constructors are reachable)
build them with real or mocked collaborators and no Spring context; tests that need
neither Spring nor a database are tagged `unit` and `htmlpdf` and named `*UnitTest` (as
`JasperBundledFontRenderingUnitTest`); facade tests use `CarlosUnitTestBase` with
`SpringUtils.getBean(HtmlPdfRenderService.class)` registered. Fixtures contain FAKE- names
only. Tests that exercise `ConvertToEdoc` set the webapp root through the existing
`tidyDocument(String, String)` / `getDocument(String, String)` (which set `realPath`) or a
package-private setter, because no web application context exists in tests.

| Test | Asserts |
|---|---|
| `HtmlPdfRenderRequestUnitTest`, `HtmlPdfRenderResultUnitTest` | Canonical-constructor validation (null HTML, empty, relative, unnormalised or root directories, base outside allowed; a `null` base accepted); defensive copies; unmodifiable degradations; `toString()` contains no HTML and no path; results with equal bytes are equal |
| `HtmlPdfArchitectureUnitTest` | Reads the module's source files and checks the import rules under "Package dependencies" (no `jakarta.servlet`, Struts, DAO, manager or facade imports; `dto` and `engine` independent; `support` free of `service`; no wildcard imports), and that only `HtmlPdfRenderService` is public in `service` |
| `HtmlPdfSpringWiringUnitTest` | An `AnnotationConfigApplicationContext` scanning only `documentManager.htmlpdf` starts and supplies `HtmlPdfRenderService` (the module needs no outside beans, constructor selection works, no cycles) and renders one fixture |
| `LocalOnlyUserAgentUnitTest` (moved to `engine` with the class and extended; its three `createRestrictedRenderer()` calls at lines ~472–494 move to the new signature) | Default stylesheet URL opens and `setBaseURL` with it logs nothing; another entry in the same jar, `jar:http:`, `http:`, protocol-relative and out-of-directory `file:` return `null`; behaviour does not depend on the working directory; blocked `file:` logs contain no path; `getImageResource` loads a `data:` image and a large JPEG background as is, downscales an over-4-megapixel PNG background to fit, and returns an empty resource for a corrupt image, an over-128 MB file and an over-250-megapixel header without decoding it |
| `XhtmlCleanupUnitTest` | Steps a, b, d–g, i and m: scripts and comments removed (including the `--` comment case moved from `ConvertToEdocUnitTest:94`); soft hyphens and bidi controls removed; inline `position: fixed` rewritten; `<ol type>` mapped; `rowspan` clamped to its row group; only `thead` tables without row spans get `carlos-pdf-paginate`; `alt` added; output parses with the JDK XML parser |
| `PrintDocumentComposerUnitTest` | Step order a–i and k–m with no collaborators (pure); `carlos-pdf-defaults` first in `<head>` and an author class rule beats it; the document note is the first child of `<body>`; note and box text identical under `Locale.setDefault(Locale.FRENCH)`; `SAFE` mode drops author styles and boxes inline SVG; `TEXT_ONLY` mode keeps reading order, table cells joined with " \| " and form-control text |
| `PrintFormControlsUnitTest` | Every row of the form-control table, including copied attributes, sizing, CR/LF removal, multi-select, `optgroup`, no selection, `checked="false"`, password not revealing length, `progress` with and without `value`/`max`, `meter` |
| `PrintImageServiceUnitTest` (plan) | The planned outcome and charge, with no file written: as is (file inside an allowed directory, `data:` PNG, a path with spaces and accents, a 48-megapixel JPEG charged four times its file size); convert (WBMP, WebP, an over-32 MB file); downscale (an over-25-megapixel PNG; a document over its slot's share plans its largest images smaller and converts a JPEG that does not fit; without a slot every raster image is planned as the box; the same document and heap always give the same plan); clean (SVG with outside references); box (remote, outside allowed directories, missing, zero-byte, a PDF, HEIC, a crafted PNG header declaring 20 000 × 20 000 that is never decoded, a raw space in a relative `src`, unparseable SVG, `data-carlos-image-unavailable`); repeated sources counted once |
| `PrintImageServiceUnitTest` (apply) | Applying the plan: encoded-URI rewrite; a JPEG embedded unchanged (same bytes); WBMP and a valid header with corrupt data that `ImageIO` can still read are converted; WebP without transparency becomes JPEG, with transparency PNG; an over-cap PNG is read as a stream with subsampling (no full decode) and its printed size kept; SVG and inline `<svg>` cleaned with their other content kept; undecodable data boxed; box sized from `width`/`height`; WARN without path; the image directory is deleted when a later step throws; SVG `@import` rules and `<?xml-stylesheet?>` are removed |
| `HtmlPdfRenderServiceSlotUnitTest` | Slot share a quarter of `maxMemory()` split between two slots; a third concurrent render waits, and after the (injected, short) wait renders with boxes and the note, or normally if it has no images; a slot is returned when an attempt throws; an interrupted wait restores the interrupt flag and does not wait again; fairness: an earlier waiter is served first |
| `HtmlPdfMemoryUnitTest`, tagged `slow` (a child JVM started by the test with `ProcessBuilder`, the test classpath, `-Xmx256m` and `-XX:+ExitOnOutOfMemoryError`, so the Surefire configuration is unchanged and a failure ends the child, not the build) | With images scaled to the small heap: six near-cap PNGs in one document and four such documents concurrently; a document with a large JPEG and a large streamed PNG scan: no `OutOfMemoryError`; every document produced (the JPEG at full resolution; PNGs downscaled, or boxes with the note when no slot is free) |
| `RtlTextShapingUnitTest` | Hebrew reordered to visual order; Arabic joined to presentation forms and reordered; brackets mirrored; paragraph direction from `dir` or the first strong character; `text-align: right` on `dir="rtl"` blocks; a run over 40 characters adds `RTL_BEST_EFFORT`; mixed left-to-right digits and Latin keep their order |
| `FallbackFontRunsUnitTest` | Only characters missing from DejaVu Sans regular or bold are wrapped (for example CJK and 𝄞); WenQuanYi → Symbola → U+FFFD order; spans set `font-style: normal`; `<style>`/`<title>` untouched; identical output across runs; a missing fallback font gives U+FFFD and `FALLBACK_FONT_UNAVAILABLE` |
| `BundledFontResolverUnitTest` | Every family (generic, named, unknown, none) → `CARLOS Sans`; `carlos cjk`/`carlos symbol` → their families; bold → DejaVu Sans Bold; italic → the regular face, slanted, with regular widths; minimum size 200 dots |
| `BundledFontFileServiceUnitTest` | Directory name from the bundle hash; outside `carlos-temp`; owner-only where supported; reused across calls; a deleted, truncated or same-size altered file is rewritten; a file deleted after first use is restored by the next render; a symlinked or foreign-owned directory leads to a fresh private directory and a WARN; concurrent first use extracts once; a missing DejaVu face fails clearly; constructing the service touches no file; a re-verification publishes a new `BundledFontSet` |
| `PageZoomCalculatorUnitTest` | `W ≤ 765.5` gives 0.8; the pass-2 formula for 850, 1000, 1400 px; printed width `P × z = 612` |
| `PrintLayoutServiceUnitTest` | 400, 850, 1000, 1400 px pages: 612 × 792 pt, content left/top at 28 ± 1 pt, nothing clipped; a narrow first page with a wide later page still shrinks; author A4-landscape `@page` ignored; identical output with `Locale.setDefault(Locale.UK)` and with a country-less locale (restored afterwards); 8 px text prints at 6 pt; `@media print` applies, `@media screen` does not; `thead` repeats on every page; a 600-character URL keeps zoom 0.8; zoom below 0.4 logs a WARN; `/Title` copied; flattening removes any AcroForm |
| `RenderedPdfValidatorUnitTest` | Typed result for: not a PDF, zero pages, a non-Letter page, an AcroForm present, a valid PDF |
| `HtmlPdfRenderServiceUnitTest` | For each fixture: valid PDF; PDFBox text contains the expected strings (accents, ≥, ✓, Chinese, Hebrew); every font embedded and one of the four bundled faces; no `<title>` or CSS text; no `/AcroForm`; expected page count and degradations; a `rowspan=4` cell over three rows and a row span inside a `thead` table do not overlap the following content; whitespace-only input fails with `PDFGenerationException`, markup with no visible content gives one blank page; Hindi, Punjabi, Tamil and Thai print as U+FFFD. Ladder: a forced pass-2 failure gives `MARGINS_REDUCED` with complete text; a forced layout failure gives `SAFE_MODE` with the note; a forced safe-mode failure gives `TEXT_ONLY` with the note; a forced text-only failure gives `PDFGenerationException`; a `LinkageError` from the renderer is retried and logged at ERROR; the final exception's cause chain contains no engine message (a forced failure whose message holds a FAKE- name does not leak it); a `null` base directory gives `WEBAPP_ROOT_UNAVAILABLE` and still produces the document; one `htmlpdf-render` line per render at the level of its most serious degradation, with source, mode, duration and names only; a character no bundled font has logs no document text on either of Flying Saucer's logging paths, and neither `xr.renderer.replace-missing-characters` nor `xr.util-logging.loggingEnabled` is set; HTML over the size limit goes straight to `TEXT_ONLY`. **every real document fixture (Annual female and male, the forms page, the demo HRM report, the email body, the Messenger letter) renders with no WARN- or ERROR-level degradation**, so a slide into safe mode or a lost image fails CI. Failures are forced by injecting failing collaborators (Mockito spies) through the constructor |
| `HtmlPdfFormsUnitTest` | Forms fixture: every value present including CJK and Hebrew; ☒/☐ and ◉/○ correct; hidden values and the Annual hidden field names never appear; only the selected option prints; textarea keeps two lines; a date value complete; values left-aligned inside right-aligned cells; text identical after `ConcatPDF` alone and after `ConcatPDF` + flatten |
| `HtmlPdfSsrfUnitTest` | Hostile HTML (`<link>`, `@import` both forms, `url()` background, `@font-face`, `<base href>`, `<img>` over `http:`, `jar:http:`, protocol-relative, `file:///etc/passwd`, `srcset`, `<iframe>`, `<object>`, `<embed>`, SVG `xlink:href`) makes no request to a local `ServerSocket` and reads no file outside the allowed directories, in every ladder mode |
| `HtmlPdfConcurrencyUnitTest` | 24 mixed renders on 8 threads give identical output and no errors |
| `ConvertToEdocUnitTest` (updated) | A linked stylesheet, a CSS `url()` background and `pdfMediaStylesheet` under the webapp root load when the working directory is outside it; translated paths are encoded `file:` URIs; three `<img>` with the same `src` all translate; an unresolvable `<img>` and a `.bmp` `<img>` become the box instead of disappearing; with `realPath` unset, translation and rendering use the same webapp root; an unresolvable root passes a `null` base directory; a failed temporary-file save returns `null`; blank HTML and final failures return `null` and log no HTML |
| `ConvertToEdocIntegrationTest` (rewritten) | Its `MockedConstruction<InternalEDocConverter>` and `ConvertToEdoc.from(eform)` cases (lines ~87–105) move to `saveAsTempPDF(EFormData)`; the `from(eForm, pdfPath)` case stays |
| `Doc2PDFIntegrationTest` (updated) | Uses `HtmlPdfRenderService` through `Doc2PDF`; final failure returns `null` |
| `FormsManagerUnitTest` | The `@Nested` class `SaveFormDataAsEDocSecurity` (lines ~305–323) is deleted with the method |
| `HRMPdfUnicodeUnitTest`, `PrintHRMReport2ActionUnitTest` (updated) | Compile against `printPdf() throws PDFGenerationException`; a thrown `PDFGenerationException` gives `PrintHRMReport2Action`'s existing error response |
| `HRMPDFCreator`/`HRMUtil` tests | A `null` conversion surfaces as `PDFGenerationException` naming the report, never a `NullPointerException` |
| `EmailManager` test | A `null` message PDF surfaces as `EmailSendingException` |
| Existing `EformDataManagerImplCreatePdfUnitTest`, `FormsManagerImplUnitTest`, `FaxDocumentManagerImplUnitTest`, `MsgAttachPDF2ActionUnitTest` | Run unchanged |

**Fixtures** (`src/test/resources/htmlpdf/`), all with `<!DOCTYPE html>`:

- Annual form pages, female and male, built from the real JSPs with checked boxes, textarea
  text, the hidden fields, the row spans and the real `annualStyle.css`/`print.css`.
- A forms page with every control type (CJK and Hebrew in values, `optgroup`, `progress`,
  `meter`).
- The demo HRM report and three synthetic HTML HRM reports: table-heavy (including a
  2000-row table), inline styles, and images (file, `data:`, remote, repeated `src`, `.bmp`,
  WBMP, corrupt header, corrupt body, WebP with and without transparency, HEIC, PDF,
  oversized, SVG with outside references). The large images (48-megapixel JPEG,
  67-megapixel PNG, a header declaring 20 000 × 20 000) are generated by the tests at run
  time into a temporary directory, not committed.
- An encrypted-email message body; a Messenger letter.
- Wide pages (400, 850, 1000, 1400 px), a narrow-then-wide page, an author `@page` page,
  small text, print/screen media rules, a long URL, `thead` across pages, `position: fixed`,
  soft hyphens, `<ol type="a">`, an over-long `rowspan`.
- Scripts: CJK, ✓, 𝄞, Hebrew (short and long), Arabic, Urdu, Hindi, Punjabi, Tamil, Thai,
  Vietnamese with combining marks.
- Edge markup: empty elements, self-closing source tags, misnested tags, entities,
  fragment-only and text-only input; a document crafted to fail layout (for the ladder).

**Host-font independence:** add `HtmlPdfRenderServiceUnitTest`, `HtmlPdfFormsUnitTest` and
`PrintLayoutServiceUnitTest` to the default list in `scripts/check-report-fonts.sh` (it runs
Surefire with `-Dtest` in fresh JVMs).

**One-off comparison (PR 1):** a script renders every fixture with wkhtmltopdf (vendored
jar; `InternalEDocConverter`'s settings; `LANG=C.UTF-8` or `-Djna.encoding=UTF-8`;
`FONTCONFIG_FILE` limited to `fonts-dejavu-core`; input = the output of
`ConvertToEdoc.tidyDocument`) and with `HtmlPdfRenderService`, and attaches side-by-side
PNGs to PR 1. Acceptance per fixture:

- page size 612 × 792 pt;
- page count equal for documents of up to 3 pages; for longer ones at most wkhtmltopdf's
  count and at least 90 % of it (Flying Saucer packs long tables slightly tighter: a
  2000-row table gives 41–42 pages against 43);
- content left and top edges at 28 ± 1 pt for edge-to-edge fixtures, otherwise within 4 pt
  of wkhtmltopdf;
- every value and checked box visible, no hidden value visible, every image present or
  shown as the box;
- no text or box drawn over another (checked on the side-by-side PNGs);
- render time against wkhtmltopdf's, within the performance budget under "Time and memory
  bounds"; the same measurements set the text-only size limit, which the PR records.

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
  the `ServletContext` bean lookup resolves the webapp root there.

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
  - images that today silently disappear (repeated `src`, removed sources, `.bmp`/`.tif`)
    now print, or show the "[image unavailable]" box; non-JPEG images over 25 megapixels
    print at reduced resolution (still above print resolution) with the same printed
    size; JPEG photos print at full resolution; WebP prints where it reaches the renderer;
    SVGs with outside references print without
    those references;
  - very wide content shrinks without limit instead of being clipped;
  - a table with both `<thead>` and row spans prints its header once instead of on every
    page;
  - right-to-left text prints shaped and reordered; long passages carry the note and may
    show their lines in reverse order;
  - when a document's layout cannot be rendered, it prints with simplified formatting or
    as plain text with a note, instead of failing.
- **`Doc2PDF` (Messenger):** output adopts the same geometry and rules (Letter, 10 mm,
  0.8 zoom, author `@page` ignored, form controls, fonts, images, ladder). Today it uses
  Flying Saucer's defaults, honours author `@page`, takes its paper size from the JVM locale
  and, because of the blocked default stylesheet, prints the title and CSS text.

## Known limitations

- **Copying or searching shaped Arabic** in the PDF yields presentation forms in visual
  order, not the original text (Unicode advises against those forms in stored text); the
  printed page is correct. Some Persian and Urdu letter forms are missing from DejaVu Sans
  and print as U+FFFD.
- **Long right-to-left passages** that wrap print their lines in reverse order (marked by
  the note); passages split across inline elements are reordered piece by piece.
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
- **HEIC images** (the iPhone camera format) have no Java reader and show the box; JPEG
  copies print. Images whose header declares over 250 megapixels show the box.
- **Stylesheet-defined `position: fixed`** (as opposed to inline) still repeats on every
  page; inline is rewritten.
- **Tables with both `<thead>` and row spans** print their header once.

## Delivery

Two PRs, both from and to `release/2026.08`, both merged before 2026.08 ships:

1. **New engine.** Flying Saucer 10.7.0; `icu4j` declared; `imageio-webp` added; the
   `org.xhtmlrenderer` logger pinned at WARN; the `htmlpdf` package with its
   `package-info` files and the `htmlpdf.*` message keys; `LocalOnlyUserAgent` moved into
   it (`git mv`, with its test) and changed; the `ConvertToEdoc` path/image and error-path
   fixes; deletion of `ReplacedElementFactoryImpl`; `Doc2PDF` and the `ConvertToEdoc` fallback use
   `HtmlPdfRenderService` (wkhtmltopdf still first, so today's fallback output also becomes
   correct). Tests (including the memory test, the ladder tests and Arabic shaping),
   fixtures, the font-check script entry, `docs/html-pdf-rendering.md` and the comparison
   images.
2. **Switch and removal.** `ConvertToEdoc` uses `HtmlPdfRenderService` only; deletions and
   every remaining item in "Removals and Packaging" (the Flying Saucer upgrade, the
   `icu4j` and `imageio-webp` additions, the logger and the message keys are already in
   PR 1). Nothing else goes into PR 2, so it can be reverted as a unit.

**Rollback.** If printed, faxed or emailed documents regress after release, reverting PR 2
restores wkhtmltopdf as the primary engine, with PR 1's renderer as its fallback. The
revert brings back everything PR 2 removed (the vendored jar in `local_repo/`, the JNA pin,
`libxrender1` in `debian/control`, the settings); the patch release then regenerates
`dependencies-lock.json` with `make lock`. Reports are triaged as GitHub issues with a
FAKE-data reproduction (the form, values entered with FAKE- names, and the
`htmlpdf-render` log line), never a patient's PDF.

Rules from `docs/release-process.md` and `CLAUDE.md`:

- Do not change the `-SNAPSHOT` version or the SCM tag.
- After each PR merges, a maintainer forward-merges `release/2026.08` into `develop`.
- **Release fit (decided: stays on `release/2026.08`).** "Normal features and fixes
  target `develop`. A high-priority bug or security fix for a supported release starts
  from and targets its `release/YYYY.MM` branch" (`release-process.md`, lines 38–39; the fix cycle, lines 113–114, covers
  "a high-priority correctness or security fix found in an alpha, beta…"). Since beta1 the
  branch has taken only fixes and dependency patches. This change is a new module, two new
  dependencies, an engine upgrade and a new layout for every printed, faxed and emailed
  form, without alpha exposure: by that policy it is a feature. The alternative is a small
  hardening PR on `release/2026.08` (strip the wkhtmltopdf SSRF vectors from the input:
  `iframe`, `object`, `embed`, `frame`, `srcset`, string-form `@import`; fix the
  null-pointer crashes and the silent image loss; fix the Flying Saucer fallback's data
  leaks: the blocked default stylesheet, printed hidden values, passwords and buttons; stop
  logging exception messages) and this design on `develop` for the next train. On
  2026-10-29 the project owner chose to keep this design on `release/2026.08`. Each PR
  body therefore states the case (abandoned wkhtmltopdf with a published SSRF vulnerability and local file
  access on; today's fallback printing CSS text, losing checkbox answers and printing
  hidden identifiers; images silently dropped; the font policy gap; heap exhaustion) and
  the release owner accepts it.
- Commits are not signed off by Claude; a maintainer confirms DCO on each PR with the
  repository's manual phrase.
- Each PR body includes the "Generated with Claude Code" line and references this spec.

## Risks

| Risk | Mitigation |
|---|---|
| A form or HRM report lays out differently from wkhtmltopdf despite the emulation | Side-by-side comparison with acceptance criteria (including an overlap check) in PR 1; small CSS fixes to form print markup are in scope; other differences are listed for the release owner |
| Real hospital HRM HTML is more varied than the fixtures (CSS beyond Flying Saucer's CSS 2.1, malformed markup) | The ladder prints it in safe or text-only mode with a note instead of failing; unusable images show the box; new variants become fixtures |
| The ladder hides a systematic problem (many documents in safe mode) | Every degradation is logged by name at WARN; the PR adds a note to the module guide on reading them |
| The image memory share is too small (images downscaled needlessly) or too large (heap still exhausted) | Charges come from measurements by format (JPEGs are not decoded); load test in PR 1 at a small heap and at the 2 GB production default; the share follows the heap size, not hard-coded |
| JPEG photos embedded unchanged make large PDFs (a 48-megapixel photo adds its 7 MB) | Photos are rare in these documents (forms, HRM reports, email bodies); converted images are written at their reduced size; downsampling oversized JPEGs to printed resolution is a follow-up if fax or email size limits are hit |
| Right-to-left passages print with reversed line order | Visible note on such documents; short text (the common case) is correct; full support is a follow-up |
| Regressions in printed forms reported after release, with no switch back | See "Rollback" under "Delivery" |
| Waiting for a render slot happens inside `ConvertToEdoc`'s class-level lock, delaying other `ConvertToEdoc` renders | The wait is at most 10 s, once per render, and only when two other renders hold the slots; removing the class lock is a non-goal |
| Two passes plus page import (and occasional retries) make some renders slower | Measured 81–300 ms warm for one attempt; retries happen only after a failure; `ConvertToEdoc` is already serialised; fax batches are checked in the manual tests |
| Default-stylesheet fix and geometry change today's `Doc2PDF` (Messenger) output | Listed as a deliberate difference; covered by tests and the manual Messenger check |
| Font directory tampering, deletion or squatting in a shared temp directory | Owner-only directory with ownership and symlink checks and a private fallback directory; verified on first use, existence and size checked before every render, rewritten on mismatch |
| Webapp root unavailable when `ConvertToEdoc` runs outside a request (HRM, email) | Resolved from the Spring web application context; checked from a scheduler thread in the manual tests; otherwise the document still renders without webapp resources |
| CI workflow edit blocked by the `.github/` write rule | Exact patch handed to the maintainer |

## Follow-ups (not in these PRs)

- Full right-to-left layout: line breaking in right-to-left order (for example laying out
  right-to-left paragraphs with Java2D `LineBreakMeasurer` inside a Flying Saucer replaced
  element), so long passages wrap correctly.
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
- Indic and Thai text: neither Java engine shapes these scripts. A route that sends
  documents containing them to the existing Chromium renderer (scripts off, network
  blocked), with a Noto font package for those scripts added to the Debian dependencies,
  would print Punjabi correctly instead of U+FFFD.
- A prototype of openhtmltopdf (see "Decisions") if right-to-left layout quality becomes
  important.
- Print the document notes in the reader's language: callers pass a locale on
  `HtmlPdfRenderRequest` and `PrintMessages` uses it (the translated keys already exist).
