# PDF and report font policy

Tracking: [#5046](https://github.com/carlos-emr/carlos/issues/5046) (application-wide audit,
which absorbed [#4907](https://github.com/carlos-emr/carlos/issues/4907)). The Rourke 2020
renderer's stricter contract is described in [rourke2020-pdf-fonts.md](rourke2020-pdf-fonts.md).

CARLOS prints clinical and billing documents through three engines: JasperReports templates
(labels, billing reports, day sheets, year-end statements, Rourke and BCAR forms, patient
letters), handwritten OpenPDF code (consultations, prescriptions, labs, HRM, chart and
encounter prints, fax cover pages, form and eForm overlays) and Flying Saucer for HTML without
scripts of its own (a form saved as a document or faxed, an HTML HRM report, an encrypted
email, a Messenger attachment; see [html-pdf-rendering.md](html-pdf-rendering.md)). The first
two silently dropped text: the PDF was valid and printed, but a patient name, a date or a total
was missing. This page states the rules that stop that, the mechanisms behind them, and how they
are tested.

## Rules

1. **Never let a host font decide what prints.** Every text element resolves to a face that
   ships inside the WAR, and that face is embedded in the PDF as `Identity-H`.
2. **Jasper:** a template either names a bundled family (`DejaVu Sans`, `DejaVu Serif`,
   `DejaVu Sans Mono`) or names nothing and gets the default from
   `src/main/resources/jasperreports.properties` (`DejaVu Sans`). Java's logical names
   (`SansSerif`, `Serif`, `Monospaced`, `Dialog`, `DialogInput`) are mapped to bundled faces for
   Jasper by `src/main/resources/fonts/carlos-jasper-fonts.xml`, so site-customised label
   templates are covered too. A name nobody provides fails the fill
   (`net.sf.jasperreports.awt.ignore.missing.font=false`); it is never substituted.
3. **Jasper boxes:** a fixed-height text box must fit at least one line of its bundled face.
   Otherwise Jasper prints nothing, without an error. Let the box stretch
   (`textAdjust="StretchHeight"`) or make it tall enough.
4. **OpenPDF:** create fonts with `PdfFonts.createFont`/`getFont` (standard Latin names map to
   embedded DejaVu, Courier to Liberation Mono). Put any text that comes from the record
   (names, addresses, notes, results, drug lines, configured clinic text) into the PDF with
   `PdfFonts.phrase`, `PdfFonts.paragraph`, `PdfFonts.addText` or `PdfFonts.showTextAligned`.
   These draw characters DejaVu lacks (Chinese, Japanese, Korean, many emoji) in the bundled
   WenQuanYi Micro Hei and Symbola faces, and mark anything no face can draw with U+FFFD.
   Do not use `new Phrase(text, font)`, `Phrase.add(String)`, font-less `new Chunk(text)`,
   `PdfPTable.addCell(String)` or `PdfContentByte.showText*` for such text, and never OpenPDF's
   default font (`new Font()`, `new Paragraph(text)`): it is non-embedded Helvetica/Cp1252.
5. **HTML to PDF:** non-scripted HTML goes through `HtmlPdfRenderService`; never build an
   `ITextRenderer` of your own. Every CSS font family resolves to the bundled DejaVu Sans (and
   its bold), whatever the document names, so a host font or an author's `@font-face` cannot
   decide what prints. A character DejaVu lacks is wrapped in a span that names WenQuanYi
   Micro Hei or Symbola (`FallbackFontRuns`), and one that no bundled face can draw prints as a
   visible U+FFFD, never as nothing.

## Mechanisms

Reproduced on `release/2026.08` with Java 25 and JasperReports 7.0.8; every fixture synthetic.

**Host-dependent line height (Jasper).** A text element with no font used Jasper's default, the
Java logical `SansSerif`. The JDK resolves that through fontconfig to whatever face the host
prefers and Jasper measures text with that face. When its line is taller than the box, Jasper
fits zero lines (`textHeight=0`, cut index 0) and exports nothing for the field. 12pt text in
the year-end statement's 16pt and 17pt boxes:

| JDK SansSerif face | 12pt line | 16pt box | 17pt box |
| --- | ---: | --- | --- |
| Inter (the investigation host's default) | 14.52 | printed | printed |
| WenQuanYi Zen Hei | 16.20 | **empty** | printed |
| Loma (Thai) | 19.07 | **empty** | **empty** |
| Liberation Sans with a 1.65 em line (matrix "tall" face) | 19.80 | **empty** | **empty** |
| bundled DejaVu Sans (the policy) | 13.97 | printed | printed |

Under Loma the unchanged `PatientEndYearStatementIntegrationTest` fails 3 of 4 tests, exactly
as reported in #5046. `fc-match` does not show this: it reported Inter while the JDK's choice
depended on the configuration it cached (below).

**Cp1252 export (Jasper).** The same default exported as non-embedded Helvetica with Cp1252
encoding, so on every host `FAKE-Nguyễn` printed as `FAKE-Nguyn`; Greek, Cyrillic and Polish
letters went the same way.

**Single-face OpenPDF text.** DejaVu has no CJK characters. Text built in one face loses them:
`FAKE-陈小明 さくら 김민준 Nguyen` printed as `FAKE-   Nguyen`. `PdfFonts.addText` already
existed and was used by preventions and the consultation "labs not included" page, but no
other printer called it. `BaseFont.getWidthPoint` also measures missing characters as zero
wide, so the form "fit" mode did not shrink a CJK name.

**The JDK font cache.** The JDK stores its fontconfig answer under
`${user.home}/.java/fonts` and reuses it while the host's fontconfig cache directories are
unchanged, even when `FONTCONFIG_FILE` points elsewhere. A test JVM therefore keeps the first
host's fonts. Surefire's `@{argLine}` also ignores `-DargLine` from the command line, so the
earlier Rourke matrix could not pass a separate `user.home` to its forks: its "no OS DejaVu"
scenario most likely still measured with the cached host mapping. The pom now has a
`test.jvmArgs` property that reaches the forks, and the matrix gives every scenario its own
`user.home` and checks what the JDK actually sees.

**HTML engine (Flying Saucer).** Before the module existed, `Doc2PDF` and the `ConvertToEdoc`
fallback used Flying Saucer's defaults, so the base-14 fonts and whatever the JVM found on the host
decided the text, and wkhtmltopdf (then the primary engine) took its fonts from the host's fontconfig:
production had `fonts-dejavu-core` only, so CJK printed as boxes. `BundledFontResolver` replaces
the whole family list of every element with one registered family, so Flying Saucer's base-14 fonts
are never chosen and a name it does not know is not looked up on the host; bold selects DejaVu Sans
Bold. No oblique face is registered: italic text uses the regular or bold face, which Flying Saucer
slants synthetically at the same widths (the DejaVu Sans Oblique files differ in width for 120 of
2536 characters and would move line breaks). `FallbackFontRuns` wraps each run of characters that
DejaVu Sans lacks in `<span style="font-family:'CARLOS CJK'">` or `'CARLOS Symbol'`
(`BundledFontSet.canDraw` asks OpenPDF whether the face has the character), and replaces what no
face draws with U+FFFD, which DejaVu Sans draws, so the gap shows. DejaVu Sans covers accented
Latin, Greek, Cyrillic, Hebrew, Arabic presentation forms, maths, arrows and the form marks
(☐ ☒ ◉ ○ •); WenQuanYi covers Han, kana, Hangul and CJK punctuation; Symbola most other symbols.
Scripts none of them shape (Devanagari, Gurmukhi, Tamil, Thai) print as U+FFFD, as wkhtmltopdf's
boxes did. The font files are extracted once into
`<java.io.tmpdir>/carlos-pdf-fonts-<hash>` (owner-only, verified before every render) because
Flying Saucer registers a face from a file path. OpenPDF caches the parsed fonts by path for the
life of the JVM, so a test must not extract a directory of its own (`TestFonts.sharedDirectory()`).

## Tests

| Test | What it pins |
| --- | --- |
| `JasperTemplateFontPolicyUnitTest` | Compiles all 51 shipped templates through the default context: every text element resolves to an embedded bundled face, and every fixed-height box fits one line of it |
| `JasperReportTemplatesIntegrationTest` | Every packaged template compiles, now including the `.xml` ones |
| `JasperBundledFontRenderingUnitTest` | Unnamed and logical-font text in 16/17pt boxes fills and exports intact; an unknown face fails |
| `ClinicalFormJasperRenderingUnitTest` | Rourke 2017 (four pages) and BCAR 2020 print synthetic identifiers in their boxes, embedded |
| `PatientEndYearStatementIntegrationTest`, `Rourke2020PdfReportIntegrationTest` | Existing end-to-end report checks |
| `*Unicode*Test`, `LabPDFCreatorObrCommentUnitTest`, `FrmCustomedPDFServletUnitTest#shouldPrintCjkRecordText_inEveryRxSection` | Each OpenPDF printer below prints synthetic CJK and accented Latin record text with only embedded fonts |
| `PdfFontsUnitTest` | The fallback and the `phrase`/`paragraph`/`showTextAligned` helpers |
| `BundledFontResolverUnitTest`, `BundledFontSetUnitTest`, `BundledFontFileServiceUnitTest` | Every family resolves to a bundled face, bold selects the bold face, the files are verified and restored, a foreign-owned directory is not used |
| `FallbackFontRunsUnitTest` | CJK and symbol runs get the right face and are read back from a real render; a character no face draws becomes U+FFFD; the same input gives the same spans |
| `HtmlPdfFixturesUnitTest#shouldPrintScripts_ofManyLanguages` | Accents, symbols, Chinese, Japanese, Korean, Hebrew, Arabic and Vietnamese print and only the four bundled fonts are embedded; Hindi, Punjabi, Tamil and Thai print as U+FFFD |

Removing the Jasper policy files makes the policy, rendering and clinical-form tests fail on an
ordinary host (mangled non-Cp1252 text) and under a tall host face (fields at zero height).
Each OpenPDF test fails against the class as it was before.

### Fresh-process font matrix

```sh
bash scripts/check-report-fonts.sh        # extra arguments go to Maven, e.g. -o
```

`scripts/check-rourke-fonts.sh`, which CI runs, now calls it. Each scenario runs the report
tests (`REPORT_FONT_TESTS`; they include `HtmlPdfFixturesUnitTest`, `HtmlPdfFormsUnitTest` and
`PrintLayoutServiceUnitTest`, so the HTML engine is checked under the same host fonts) in a new JVM
with its own `user.home`:

| Scenario | Host fonts | Check before running |
| --- | --- | --- |
| `host` | the machine's own | none |
| `liberation` | Liberation Sans only, no DejaVu | neither fontconfig nor the JDK sees DejaVu |
| `tall` | Liberation Sans with a 1.65 em line (`scripts/fonts/TallLineFont.java`) | the JDK's 12pt SansSerif line is over 17pt |

each with a writable and an unwritable (`/proc`) fontconfig cache. `scripts/fonts/JavaFontProbe.java`
records the JDK's own view beside `fc-match`. Logs and PDFs go to
`target/rourke-font-matrix/<scenario>/`, which CI uploads.

## Audit

Every output path that produces a PDF or Jasper document, and its disposition. "Fixed" items
are covered by the tests above.

### Jasper templates

| Output | Disposition |
| --- | --- |
| All 51 shipped templates (labels, referral label, BC billing PDF/CSV reports and remittance, ON year-end statement, lab and billing day sheets, billing invoice, Dx research export, appointment receipt, Rourke 2017/2020, BCAR 2020) | **Fixed:** bundled embedded default and logical-name mapping; policy tests |
| ON year-end statement | **Fixed:** template pins DejaVu (port of `main` e2dc6d0d49, byte-identical to #5067) |
| BC write-off and account-receivable PDFs | **Fixed:** six labels/fields and the insurer header were fixed 10pt boxes holding 10pt text, which no common face fits; they now stretch like their neighbours |
| BC write-off and payment summary CSVs | **Fixed:** 8pt bundled default style, as #5067 does for the payment CSVs |
| Lab and billing day sheets (`printLabDaySheetAction`), sample `label.xml` / `label-appt.xml` | **Fixed:** they kept JasperReports 6 header syntax and did not load at all; converted. The day-sheet route has no UI link |
| `csv_rep_payref.xml`, `msppremsums23.jrxml` | **Removed:** unreferenced and outside the report allowlist |
| Site templates in `user.home` (`label.xml`, `Addresslabel.xml`, `pdfLabelMRP`) and uploaded patient-letter templates | **Covered** by the default font and logical-name mapping; a name nobody provides now fails |
| CJK characters in Jasper output | **Open:** DejaVu has no CJK and Jasper cannot load the bundled WenQuanYi `.ttc` as a font-extension face. Rourke 2020 refuses such characters; the other renderers still drop them, as they did before. Needs a TTF fallback face (Jasper font set) or a shared refusal |
| `OscarDocumentCreator` error handling | **Open:** it logs and swallows `JRException`, so a failed fill can return a partial or empty document (BC reports, day sheets, Dx research export) |

### OpenPDF and PDFBox printers

| Output | Disposition |
| --- | --- |
| Consultation request (`ConsultationPDFCreator`) | **Fixed** |
| Chart print (`OscarChartPrinter`, `OscarChartPrint`) | **Fixed**, plus an unbalanced `endText()` that stopped the chart after its first CPP section. The route has no UI link |
| Prescriptions, print and fax (`FrmCustomedPDFServlet`) | **Fixed** |
| Lab print, attachments, previews, faxes (`LabPDFCreator`) | **Fixed**, plus the TRUENORTH OBR-comment `ClassCastException`. The RTF export path names fonts rather than embedding them and is unchanged |
| HRM print (`HRMPDFCreator`) | **Fixed** |
| Fax cover page (`PdfCoverPageCreator`) | **Fixed** |
| eChart Print (`CaseManagementPrintPdf`) and its measurement extension | **Fixed** |
| TIFF attachment page titles (`ImagePDFCreator`) | **Fixed** (were non-embedded Helvetica) |
| Form overlays (`FrmPDFServlet`) and eForm overlays (`EFormPDFServlet`) | **Fixed**, including fit sizing |
| Footer promo text and wrapped confidentiality footer | **Fixed** |
| Preventions (`PreventionPrintPdf`), labs-not-included page | Already used `addText` |
| `PdfRecordPrinter` OpenPDF sections | **Not reachable:** only its Jasper invoice method has a caller |
| Annotated documents (`AnnotatedDocumentComposer`), envelopes (`GenerateEnvelopes2Action`), REST Rx print (`RxWebService`) | **Refuse, not silent:** they reject text their single face cannot draw. Printing CJK there needs per-run font switching |
| BPMH and Form14 clipping | In #5067 |
| Rourke 2020 field layout (growth values over guidance text, notes over section headers, dates across their guides) | **Fixed:** boxes re-measured from the background scans; a full-capacity pixel test pins it ([rourke2020-pdf-fonts.md](rourke2020-pdf-fonts.md#field-layout)) |
| Browser-rendered eForm PDFs | Rendered by the eForm browser renderer, not covered here ([eform-browser-pdf-renderer.md](eform-browser-pdf-renderer.md)) |
