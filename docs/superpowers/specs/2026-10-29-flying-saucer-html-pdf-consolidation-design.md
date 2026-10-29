# Design: Consolidate HTML-to-PDF on Flying Saucer and Remove wkhtmltopdf

**Date:** 2026-10-29
**Status:** Design approved in conversation; written spec awaiting review
**Target:** `release/2026.08` (currently `2026.08.0-beta3-SNAPSHOT`), forward-merged to `develop`

## Problem

CARLOS EMR turns HTML into PDF with three engines:

| Engine | Used for |
|---|---|
| Headless Chromium (Selenium) | Saved eForms (`EFormBrowserPdfService`) |
| wkhtmltopdf (`ultrabuk-htmltopdf-java`, native `libwkhtmltox`) | First choice in `ConvertToEdoc`: encounter forms (print, fax, email, attachments, save as document), HTML HRM reports, encrypted-email message PDFs |
| Flying Saucer 10.0.6 | `ConvertToEdoc` fallback when wkhtmltopdf throws; `Doc2PDF` (Messenger PDFs) |

Chromium is kept for scripted and complex pages. The other two have problems:

1. **wkhtmltopdf is abandoned and weakly guarded.** The upstream project was archived in
   January 2023, its Qt WebKit engine is unmaintained, and it has a published SSRF
   vulnerability (CVE-2022-35583). `InternalEDocConverter` runs it with
   `load.blockLocalFileAccess=false` and no transport-level URL guard; CARLOS relies only on
   DOM clean-up before rendering. It ships as a 33 MB vendored jar with a native library,
   needs `libxrender1` on the host and pulls in JNA. CARLOS's own dependency audits
   (`docs/dependency-audit-2026-03-16.md`, `-03-17.md`) mark it "abandoned, replace".
2. **Flying Saucer output is broken today.** Verified by rendering the same documents
   through CARLOS's own `LocalOnlyUserAgent` and `ReplacedElementFactoryImpl`:
   - `LocalOnlyUserAgent` blocks every `jar:` URL, including Flying Saucer's own default
     stylesheet (`jar:file:…/flying-saucer-core-*.jar!/resources/css/XhtmlNamespaceHandler.css`).
     Every Flying Saucer PDF therefore prints the page `<title>` and raw CSS text at the top,
     loses block and table layout, and ignores forced page breaks.
   - `ReplacedElementFactoryImpl` calls `fsImage.scale(width, height)` and discards the
     returned image, so `<img>` width and height are ignored.
3. **Flying Saucer fonts break the PDF font policy (#5046).** For no family, generic
   families (`serif`, `sans-serif`, `monospace`) and unknown names, Flying Saucer uses the
   non-embedded base-14 Times, Helvetica and Courier. It does not fall back to another font
   for missing glyphs: Chinese, Japanese and Korean text, and symbols such as ✓, are
   silently dropped. Registering bundled fonts under the generic names does not override
   this. `docs/pdf-font-policy.md` covers JasperReports and handwritten OpenPDF but not the
   HTML engines.
4. **Unused dependency.** `openpdf-html` (and, through it, `neko-htmlunit`) is on the
   classpath; nothing imports it.

## Decisions

Agreed in conversation on 2026-10-29:

| Question | Decision |
|---|---|
| Engine for non-scripted HTML-to-PDF | Flying Saucer only; Chromium stays for saved eForms |
| Rollout | Hard cut: wkhtmltopdf removed in the same release, no configuration switch |
| Behaviour when Flying Saucer cannot render | Fail clearly with an error naming the document; no fallback to Chromium |
| Flying Saucer version | Upgrade 10.0.6 → 10.7.0 as the first step |
| Structure | One shared Flying Saucer setup; `ConvertToEdoc` and `Doc2PDF` become thin callers; their callers do not change |
| Fonts | Hard-coded mapping, identical to JasperReports' mapping, plus one CJK step |
| Release | Land on `release/2026.08`; maintainers forward-merge to `develop` |

Flying Saucer 10.7.0 was checked before choosing it:

- Licence is LGPL 2.1 or later, byte-identical licence files to 10.0.6.
- Binary-compatible with the vendored OpenPDF core 3.0.3: all 204 OpenPDF members it
  references exist in 3.0.3 (inheritance followed). CARLOS's Flying Saucer classes compile
  against it unchanged.
- Same dependencies as 10.0.6 (`flying-saucer-core`, `openpdf`, `batik-codec`).
- Fixes since 10.0.6 include table header repetition across page splits, table-cell
  width fixes, rowspan splits, line-fitting fixes and XXE hardening of
  `DocumentBuilderFactory` use (10.4.0).

## Goals

- Every non-scripted HTML-to-PDF render in CARLOS goes through one Flying Saucer pipeline.
- Output is deterministic: the same HTML gives the same fonts and layout on every server.
- Every font in the output is bundled in the WAR and embedded; no text is silently dropped.
- The SSRF guard covers every HTML-to-PDF path; wkhtmltopdf's unguarded path is gone.
- The WAR, the Debian package and the build no longer carry wkhtmltopdf, its native
  library, `libxrender1`, JNA, `openpdf-html` or `neko-htmlunit`.

## Non-Goals

- Changing the Chromium eForm renderer (`EFormBrowserPdfService`).
- Changing PDFs drawn directly with OpenPDF (cover pages, consultations, labs, HRM text and
  image reports) or JasperReports templates.
- Moving `ConvertToEdoc`'s callers onto a new API or deleting `ConvertToEdoc`/`Doc2PDF`.
- Removing `ConvertToEdoc`'s class-level `synchronized` methods. They protect the static
  `realPath` field; changing that is separate work.
- Upgrading the vendored OpenPDF core (stays 3.0.3).

## Design

### Components

All new and changed classes are in `io.github.carlos_emr.carlos.documentManager`, next to
the existing Flying Saucer classes. Names follow `docs/architecture/layer-names.md`:
static utilities use a domain noun; the font class makes one decision through a priority
chain, so it is a `*Resolver`. The table shows the end state after PR 2; "Delivery" shows
how the work splits across the two PRs.

| Class | Status | Responsibility |
|---|---|---|
| `HtmlPdf` | New, public, static utility | The only entry point: `render(String html, String baseUrl, ImageSource images)` returns PDF bytes or throws `PDFGenerationException` |
| `HtmlPdf.ImageSource` | New enum | `GUARDED_USER_AGENT` (images load through `LocalOnlyUserAgent`) or `VALIDATED_LOCAL_FILES` (images load through `ReplacedElementFactoryImpl`, for `ConvertToEdoc`, whose paths are already validated) |
| `XhtmlCleanup` | New, package-private, static utility | Jsoup clean-up shared by both callers |
| `CjkFontRuns` | New, package-private, static utility | Wraps runs of CJK and Korean characters in a span that uses the bundled CJK font |
| `BundledFontResolver` | New, package-private | Subclass of Flying Saucer's `ITextFontResolver`; maps every requested family to a bundled face |
| `BundledFontFiles` | New, package-private, static utility | Copies the bundled font files to a private directory once per JVM and returns their paths |
| `LocalOnlyUserAgent` | Changed | Also allows Flying Saucer's own default stylesheet, matched by its exact URL |
| `ReplacedElementFactoryImpl` | Changed | Keeps the scaled image returned by `FSImage.scale` |
| `ConvertToEdoc` | Changed | `renderPDF` calls `HtmlPdf.render(document, null, VALIDATED_LOCAL_FILES)`; `fallbackRender` and `prepareDocumentForFlyingSaucer` are removed (clean-up moves to `XhtmlCleanup`) |
| `Doc2PDF` | Changed | `renderWithFlyingSaucer` calls `HtmlPdf.render(html, baseUrl, GUARDED_USER_AGENT)` |
| `InternalEDocConverter`, `EDocConverterInterface` | Deleted | wkhtmltopdf wrapper |
| `EformDataManager.saveEformDataAsEDoc` (+ impl) | Deleted | Has no callers; it was the last wkhtmltopdf path for eForms |

### Render pipeline

`HtmlPdf.render` runs these steps in order:

1. **Clean** (`XhtmlCleanup`). Parse with Jsoup; remove `<script>` elements and all comment
   nodes (comments never render, and an HTML comment containing `--` is not valid XML);
   add `alt=""` to `<img>` without one and `type="text"` to `<input>` without one; serialise
   with XML syntax, XHTML escaping, UTF-8 and pretty-printing off. This merges the two
   current routines (`Doc2PDF.renderWithFlyingSaucer` removed comments;
   `ConvertToEdoc.prepareDocumentForFlyingSaucer` rewrote them).
2. **CJK step** (`CjkFontRuns`). On the Jsoup DOM, before serialising: split each text node
   into runs and wrap each maximal run of CJK characters in
   `<span style="font-family:'CARLOS CJK'">`. A code point is CJK when its
   `Character.UnicodeScript` is `HAN`, `HIRAGANA`, `KATAKANA`, `HANGUL` or `BOPOMOFO`, or its
   `Character.UnicodeBlock` is `CJK_SYMBOLS_AND_PUNCTUATION` or
   `HALFWIDTH_AND_FULLWIDTH_FORMS`. Text inside `<style>`, `<title>`, `<textarea>` and
   attribute values is left alone. Same input always produces the same spans.
3. **Render.** Build the renderer with `LocalOnlyUserAgent.createRestrictedRenderer`, using
   a new `BundledFontResolver` for this render. Set print mode on, interactive off,
   smoothing threshold 0. For `VALIDATED_LOCAL_FILES`, install `ReplacedElementFactoryImpl`.
   Call `setDocumentFromString(xhtml, baseUrl)`, `layout()`, `createPDF(buffer, true)`.
4. **Check.** The buffer must start with `%PDF-` and PDFBox must load it with at least one
   page. Only then are the bytes returned.

`HtmlPdf` holds no mutable state and is safe to call concurrently.

### Fixed font mapping

The mapping is the one JasperReports already uses (`src/main/resources/fonts/carlos-jasper-fonts.xml`),
so CARLOS has one font rule across engines.

| The first family named in the CSS `font-family` list | Face used |
|---|---|
| `CARLOS CJK` (set only by the CJK step) | WenQuanYi Micro Hei (`fonts/wqy/wqy-microhei.ttc`, index 0) |
| `monospace`, or a name containing `courier`, `mono`, `consolas` or `lucida console` | Liberation Mono (`fonts/liberation/LiberationMono-*.ttf`) |
| `serif`, or a name containing `times`, `georgia`, `garamond`, `cambria`, `palatino` or `book antiqua` | DejaVu Serif (`net/sf/jasperreports/fonts/dejavu/DejaVuSerif*.ttf`) |
| Anything else: `sans-serif`, Arial, Helvetica, Verdana, Calibri, unknown names, or no family | DejaVu Sans (`net/sf/jasperreports/fonts/dejavu/DejaVuSans*.ttf`) |

- Matching is case-insensitive, after stripping quotes and whitespace.
- Bold, italic and bold-italic use the matching bundled faces (DejaVu `-Bold`, `-Oblique`
  or `-Italic`, `-BoldOblique` or `-BoldItalic`; Liberation `-Bold`, `-Italic`,
  `-BoldItalic`). WenQuanYi has one face.
- All faces are registered with `Identity-H` encoding and embedded.
- `BundledFontResolver` overrides `resolveFont(SharedContext, FontSpecification)`: it
  replaces the specification's family list with the single mapped bundled family and
  delegates to `super`. Flying Saucer's base-14 fonts are therefore never chosen.

Coverage, checked with OpenPDF's `BaseFont.charExists`:

| Face | Accented Latin, Greek, Cyrillic, math, units, arrows, punctuation | Checkmarks ✓ ☐ | Arabic | CJK, Hangul |
|---|---|---|---|---|
| DejaVu Sans | yes | yes | yes | no |
| DejaVu Serif | yes | no | no | no |
| Liberation Mono | yes | no | no | no |
| WenQuanYi Micro Hei | n/a | n/a | n/a | yes (Han, kana, Hangul, Bopomofo, CJK punctuation, fullwidth) |

**Font files on disk.** Flying Saucer's `addFont` needs a filesystem path: the overload
that takes a `BaseFont` reads the path again to extract font names. DejaVu ships inside the
`jasperreports-fonts` jar. `BundledFontFiles` copies the 13 font files from the classpath
into one private directory under `java.io.tmpdir` on first use, checks each target with
`PathValidationUtils`, and reuses that directory for the life of the JVM. Missing font
resources fail the render with `PDFGenerationException`; they are never substituted.
OpenPDF caches parsed fonts, so repeated renders do not re-parse the files.

### Security

- **SSRF guard.** `LocalOnlyUserAgent` gains one exception: a URI equal to Flying Saucer's
  default stylesheet URL is allowed. That URL is computed once, the same way Flying Saucer
  computes it (`xr.css.user-agent-default-css` + `XhtmlNamespaceHandler.css`, resolved with
  `Class.getResource`). The match is exact string equality, in both `resolveAndOpenStream`
  and `openStream`. Every other `jar:` URL (including other entries in the same jar and
  `jar:http:`), `http:`, `https:`, `ftp:`, protocol-relative and out-of-folder `file:`
  request stays blocked.
- **SVG.** Flying Saucer 10.7.0 renders inline `<svg>` through Batik. A reference from an
  SVG to an outside resource makes Batik throw (Flying Saucer gives it no document URL),
  so the render fails and nothing is fetched. This was tested with a local HTTP server:
  zero requests. The general SSRF probe (9 vectors: `<link>`, `@import`, `url()`
  background, `@font-face`, `<img>` over `http:`, protocol-relative, `jar:http:`,
  `file:///etc/passwd`) also made zero requests on 10.7.0.
- **XXE.** CARLOS's path parses through Flying Saucer's `XMLResource` SAX path with its
  entity resolver; the probe with external entities and an external DTD made zero
  requests on both 10.0.6 and 10.7.0. 10.7.0 also hardens its `DocumentBuilderFactory`
  uses, which CARLOS does not reach.
- **Logging.** No message or log line includes HTML content, patient data or full file
  paths; identifiers go through `LogSafe`.

### Errors

| Caller | Today | After |
|---|---|---|
| `HtmlPdf.render` | n/a | Throws `PDFGenerationException("HTML could not be converted to PDF", cause)` for any failure; never returns partial bytes |
| `ConvertToEdoc.saveAsTempPDF` / `from(...)` | wkhtmltopdf, then Flying Saucer; returns `null` if both fail | Flying Saucer only; still returns `null` on failure and logs a fixed message plus the cause class |
| `FormsManagerImpl`, `FaxDocumentManagerImpl` | Turn `null` into `PDFGenerationException` naming the form | Unchanged |
| `HRMPDFCreator`, `EmailManager` | Use the returned path | Unchanged |
| `Doc2PDF` | `sendError(500, "PDF generation failed")` | Unchanged |

## Removals and Packaging

- **`pom.xml`:** Flying Saucer `10.0.6` → `10.7.0` (keep the `com.github.librepdf:openpdf`
  exclusion). Remove `com.github.openosp:ultrabuk-htmltopdf-java`, the
  `com.github.librepdf.openpdf:openpdf-html` dependency, the `jna.version` property and the
  `net.java.dev.jna:jna` dependency-management pin (only ultrabuk used it, per
  `mvn dependency:tree`). Update the repositories comment that lists ultrabuk and
  openpdf-html as vendored.
- **`local_repo/`:** delete `com/github/openosp/ultrabuk-htmltopdf-java` (33 MB) and
  `com/github/librepdf/openpdf/openpdf-html`.
- **`dependencies-lock.json`:** regenerate with `make lock`.
- **`debian/control`:** remove `libxrender1` and its comment (the comment states wkhtmltopdf
  is its only user).
- **Settings:** delete the orphaned wkhtmltopdf comment block in
  `src/main/resources/carlos.properties`; delete `WKHTMLTOPDF_COMMAND` and `WKHTMLTOPDF_ARGS`
  from `.devcontainer/development/config/shared/volumes/carlos.properties`.
- **OSCAR 19 import map:** in `scripts/migration/o19/overrides_props.py`, change
  `WKHTMLTOPDF_COMMAND` and `WKHTMLTOPDF_ARGS` from `deploy-owned` to `dropped-flag`;
  regenerate `debian/assets/o19-manifest/*.json` with
  `scripts/migration/o19/generate_manifests.py`; run `scripts/migration/o19/tests/`. This is
  manifest data, not a new `carlos-ctl` verb or flag, so the `carlos-ctl` `Depends` floor
  does not change.
- **CI:** in `.github/workflows/dependency-submission.yml`, drop ultrabuk from the
  failure-recovery comment and the "formerly-JitPack" comment, and remove
  `~/.m2/repository/com/github/openosp` from the cache eviction (keep
  `com/github/librepdf`, because OpenPDF core stays vendored). In
  `.github/actions/dev-container/action.yml`, remove the matching `openosp` eviction line.
  The repository's `.claude/settings.json` denies Claude writes under `.github/`; if the
  rule blocks the edit, the exact patch goes to the maintainer instead of working around it.
- **Docs:**
  - `docs/pdf-font-policy.md`: add the HTML engine (fixed mapping and CJK step) as a third
    engine.
  - `CLAUDE.md`, "PDF and Report Fonts": one line pointing HTML-to-PDF at `HtmlPdf` and
    its fixed fonts.
  - `docs/pdf-dependency-consolidation-plan.md`: mark ultrabuk as removed and openpdf-html
    as removed.
  - `utils/readme.txt`: remove the wkhtmltopdf section.

## Testing

All tests are JUnit 5 under `src/test/java/io/github/carlos_emr/carlos/documentManager/`,
named `should<Action>_<context>()`, tagged `unit` or `integration`. New Java files carry
the CARLOS copyright header and JavaDoc with `@since 2026-10-29` and no `@author`.

| Test | Asserts |
|---|---|
| `LocalOnlyUserAgentUnitTest` (extended) | The default stylesheet URL opens; another entry in the same jar, `jar:http:`, `http:`, protocol-relative and out-of-folder `file:` URIs return `null` |
| `ReplacedElementFactoryImplUnitTest` (new) | An `<img>` with width and height produces an element of the requested size |
| `XhtmlCleanupUnitTest` | Scripts and comments removed (including a comment containing `--`); `alt` and `type` added; output parses with the JDK XML parser |
| `CjkFontRunsUnitTest` | Only CJK and Hangul runs are wrapped; mixed text splits correctly; `<style>`, `<title>`, `<textarea>` untouched; output is identical across runs |
| `BundledFontResolverUnitTest` | Each family category maps to its face; unknown and missing families map to DejaVu Sans; bold and italic select the right faces |
| `HtmlPdfRenderIntegrationTest` | For each fixture: valid PDF; PDFBox-extracted text contains the expected strings, including accents, ≥, ✓ and Chinese; every font is embedded and from the bundled set (no base-14); the `<title>` and CSS text do not appear; expected page counts |
| `HtmlPdfSsrfIntegrationTest` | Hostile HTML (`<link>`, `@import`, `url()` backgrounds, `@font-face`, `<img>` with `http:`, `jar:http:`, `file:///etc/passwd`, protocol-relative, SVG `xlink:href`) produces no request on a local `ServerSocket`, and the outside-SVG case fails with `PDFGenerationException` |
| Existing `ConvertToEdocUnitTest`, `ConvertToEdocIntegrationTest`, `Doc2PDFIntegrationTest` | Updated: they reference `fallbackRender`, `prepareDocumentForFlyingSaucer` or `renderWithFlyingSaucer` |
| Existing `EformDataManagerImplCreatePdfUnitTest`, `FormsManagerImplUnitTest`, `FaxDocumentManagerImplUnitTest`, `MsgAttachPDF2ActionUnitTest` | Run unchanged; they exercise the callers whose contracts do not change |

**Fixtures** (`src/test/resources/htmlpdf/`, FAKE- names only, no real patient data):

- An Annual form page captured from the dev server.
- The demo HRM report, plus three synthetic HTML HRM reports: table-heavy, inline styles,
  and with images.
- An encrypted-email message body.
- A Messenger letter.
- Edge-case markup: empty elements, self-closing source tags, misnested tags, entities.

**Host-font independence:** add `HtmlPdfRenderIntegrationTest` to the default test list in
`scripts/check-report-fonts.sh`.

**One-off comparison (PR 1 only):** a script renders each fixture with wkhtmltopdf and with
`HtmlPdf`, and writes side-by-side PNGs that are attached to PR 1 for review. It is not
kept in CI, because wkhtmltopdf is removed in PR 2.

**Manual checks on Tomcat (dev container):**

- Print an Annual form.
- Preview a consultation fax with an Annual form and an HTML HRM report attached.
- Send an encrypted email.
- Attach a Messenger PDF.

## Delivery

Two PRs, both from and to `release/2026.08`, both merged before 2026.08 ships:

1. **New engine.** Flying Saucer 10.7.0; `HtmlPdf`, `XhtmlCleanup`, `CjkFontRuns`,
   `BundledFontResolver`, `BundledFontFiles`; both bug fixes; `Doc2PDF` and the
   `ConvertToEdoc` fallback use `HtmlPdf` (wkhtmltopdf still first). Tests, fixtures, the
   font-check script entry and the comparison images.
2. **Switch and removal.** `ConvertToEdoc` uses `HtmlPdf` only; deletions and every item in
   "Removals and Packaging".

Rules from `docs/release-process.md` and `CLAUDE.md`:

- Do not change the `-SNAPSHOT` version or the SCM tag.
- After each PR merges, a maintainer forward-merges `release/2026.08` into `develop`.
- During beta the release branch takes high-priority correctness and security fixes. Each
  PR body states the case: the abandoned wkhtmltopdf with a published SSRF vulnerability
  and local file access on, the garbled Flying Saucer output, and the font policy gap. The
  2026.08 release owner decides whether to accept it.
- Each PR body includes the "Generated with Claude Code" line and references this spec.

## Risks

| Risk | Mitigation |
|---|---|
| Annual form or HRM layout differs from wkhtmltopdf (Flying Saucer supports CSS 2.1 and needs well-formed markup) | Side-by-side comparison in PR 1; small CSS fixes to the Annual form's print markup are in scope; anything else is listed in the PR for the release owner |
| Real hospital HRM HTML is more varied than the fixtures | Failures surface as a clear error naming the document, not a garbled PDF; new variants become fixtures |
| Default-stylesheet fix changes every existing Flying Saucer PDF (Messenger) | The change restores the intended layout; covered by the render tests and the manual Messenger check |
| Font extraction fails on a read-only or full temp directory | Render fails with `PDFGenerationException`; the integration test covers a missing resource |
| CI workflow edit blocked by the `.github/` write rule | Exact patch handed to the maintainer |
