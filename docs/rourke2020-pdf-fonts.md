# Rourke 2020 PDF font contract

Tracking: [original investigation #4903](https://github.com/carlos-emr/carlos/issues/4903),
[implementation #4906](https://github.com/carlos-emr/carlos/issues/4906),
[application-wide audit #4907](https://github.com/carlos-emr/carlos/issues/4907).

## Failure mechanism

The control was `release/2026.08` commit
`b9900257beb529ae7de74f3d706bda9a96f6e86b`, Java 25.0.3 and JasperReports 7.0.8.
The existing Rourke suite ran five tests, with one failure: the four-page PDF
retained its scanned backgrounds while the tested patient name and visit date
were absent. All fixtures were synthetic.

The main templates inherited the logical font `SansSerif`. The continuation
template explicitly requested DejaVu Sans. A diagnostic enabled
`net.sf.jasperreports.print.keep.full.text` and examined the filled `JRPrintText`:

| Same synthetic name / font size 16 | Field height | Printable text height | Truncation | Export |
| --- | ---: | ---: | --- | --- |
| Host logical default | 22 | 0 | Cut index 0 | Name absent |
| Host logical default, taller diagnostic field | 200 | 26.400055 | None | Name present |
| Explicit bundled DejaVu Sans | 22 | 18.625 | None | Name present; DejaVu embedded |

The logical font's measured line height was 26.400053. Enlarging only the
diagnostic field allowed its text to print. The immediate cause is fill/layout
clipping under incompatible ambient font metrics, before PDF export. The fixed
22-pixel name/date boxes fit the chosen DejaVu metrics, so their geometry is
unchanged by the fix.

Fontconfig reported `OpenAISans-Regular.woff2`; the actual Java logical composite
started with Noto Sans Egyptian Hieroglyphs and used other fallback faces. This
does not establish a general WOFF2 defect. The native font-substitution mismatch
is a separate investigation detail; `fc-match` alone does not identify Java's
actual font or prove correct Jasper layout.

Both the default-font failure and explicit-DejaVu success reproduced with a
writable cache and without the original cache warning. Cache permissions alone
do not explain this reproduction.

## Rendering policy

Every main page and the notes continuation explicitly select **DejaVu Sans**.
The existing version-locked `jasperreports-fonts` dependency supplies normal,
bold, italic and bold-italic TTF faces, the `Identity-H` PDF encoding and embedding
configuration. Fill and export share a report-specific Jasper context. No OS
DejaVu installation or process-wide default-font override is required.

The renderer checks that the extension, classpath faces, Unicode encoding and
embedding configuration are available. The original library and font notices
remain in that unmodified JAR at `META-INF/LICENSE` and
`net/sf/jasperreports/fonts/dejavu/LICENSE`. No new font binary or dependency is
introduced.

Before exporting, the renderer checks populated print elements for unsupported
characters and non-whitespace text cut by Jasper. Keeping full text exposes the
cut index instead of discarding the evidence. Jasper's stretching continuation
fields still split long notes across pages normally. Identifiers and other
fixed fields that cannot fit produce an error instead of a partial value.

DejaVu does not cover every script or symbol. Unsupported characters produce a
clear failure; this change does not promise universal Unicode coverage. A wider
coverage policy would require explicit fonts, packaging and layout validation.

Export goes to a request-local buffer. Font/layout/glyph validation and exporter
failures therefore send no PDF bytes. The existing print action resets its
response and returns a fixed, actionable plain-text error body. This avoids the
generic error JSP, which hides servlet error messages in production. Error messages include no
patient values. A transport failure after the completed PDF starts being copied
to the client remains an ordinary interrupted download.

Only compiled templates, font resources and static backgrounds are cached.
Parameters, filled reports and output buffers remain request-local.

## Regression evidence

The final focused renderer/action/record run passed **25 tests**. The renderer suite
then passed **10 tests in each of four separate Maven/JVM runs**:

| OS font selection | Writable cache | Unwritable cache |
| --- | --- | --- |
| Host defaults (OpenAI Sans reported by fontconfig) | 10 passed | 10 passed |
| Liberation-only font directory; no OS DejaVu visible | 10 passed | 10 passed |

The matrix is reproducible with:

```sh
bash scripts/check-rourke-fonts.sh
```

That script now runs the application-wide report font matrix
(`scripts/check-report-fonts.sh`, see [pdf-font-policy.md](pdf-font-policy.md)), which adds a
tall-line host face and the other report renderers. It also gives every scenario its own
`user.home`: the JDK caches its fontconfig answer there, and the runs above did not isolate it,
so the Liberation-only rows most likely still measured with the cached host mapping. Rourke 2020
itself is unaffected, because it pins the bundled DejaVu extension.

Additional Maven options can be passed as arguments. The script requires
fontconfig and Liberation fonts, already present in the development image. Each
case starts a fresh JVM, because AWT and Jasper cache font resolution. The
unwritable case uses a nonexistent path under `/proc` so it remains unwritable
when CI runs as root.

Tests verify exact identifiers, dates, measurements, accented names and clinical
symbols in the actual field regions on **each** main page. Rendered pixels must
also differ from the corresponding blank/background field. PDF fonts must be
embedded DejaVu with Unicode mappings. Existing tests cover blank dates, notes,
long continuation text, page selection and two distinct synthetic patients.
Negative tests remove the extension in a child JVM even with missing-font
warnings disabled, supply unsupported characters and overlong text, and simulate
an exporter that writes partial bytes before failing. The print-action test
checks that the user receives an error rather than a PDF response.

Synthetic PDFs, previews and logs are saved before assertions under
`target/rourke-pdf/` or `target/rourke-font-matrix/`. CI uploads those directories
even after a failure. The Rourke JavaScript regression and scoped BDD naming
check also passed.

## Field layout

Issue #5046 found two growth values printed over the form's own guidance: the 12-month head
circumference over "(avg 47 cm)" and the 24-month one over "if prior abN". Checking every
field filled to capacity against the blank form found the same problem across all four
pages, so the boxes were re-measured from the background scans (108 dpi, 1.5 pixels per
template point):

| Fields | Before | Now |
| --- | --- | --- |
| Growth values, pages 1–3 | Touched the descenders of "Length" and "Weight"; two page 1 values ran over "(avg 35 cm)" or out of their cell into the next section | On their own line under the label, centred in the space left in the cell |
| Head circumference at 1 week, 12 and 24 months; weight at 2 weeks | Over the second-line guidance | Beside it, in the same cell |
| Visit dates, pages 1–3 | Crossed by the printed `___/___/20__` slashes | Above the guide, as page 4 already printed them |
| Parent/caregiver concerns, problems and plans | Full notes ran over the next section header (page 4's last two problem lines were unreadable) | Fitted to the blank space between the headers |
| Name; birth head circumference and discharge weight | Long values ran into "Birth Day", "cm" and "g" | Ends before the printed text |
| Birth remarks and family history | The two boxes overlapped | One column each |
| Signatures, Nipissing items | Ran past the end of their line or onto the next heading | Fitted to their line |

The birth date still prints across its d/m/yy guide: there is no blank space for it in the
header.

Where a box had to get smaller (page 4 concerns and problems, page 2 problems, narrowed growth
boxes, names, birth remarks), it uses `textAdjust="ScaleFont"` so an entry that used to fit
still prints, smaller, instead of overprinting the form. Jasper would shrink such text without
limit, so the renderer refuses text below 8 points (the size of the form's own footnotes) with
the same "does not fit" error as any other overflow. Every main-page text field also has
`blankWhenNull`, so a record with no value prints an empty box rather than `null` (a record
without a gestational age did).

`Rourke2020PdfReportIntegrationTest#shouldKeepFullFields_clearOfPrintedForm` pins this. It
fills every field to capacity (five-character growth values, full dates, full lines of text),
renders each page and the blank form at 108 dpi, and requires that each box covers only paper
and the thin ruled lines it is written on, that every printed pixel stays inside its own box,
two pixels clear of print outside it, and that no two boxes overlap. Against the previous
templates it reports more than 150 problems, including both reported collisions.

## Deployment and release disposition

The WAR build passed with dependency-lock verification, compilation, Checkstyle
and Javadoc. In a clean Ubuntu 26.04 container (image digest
`sha256:c098726dd9b9733f3e885f8de0ef375206a311b0a317eadc40d347b1115fb944`),
Java 25.0.3 rendered a five-page synthetic fixture from the extracted WAR's
`WEB-INF/classes` and `WEB-INF/lib` classpath. Identifiers, dates, measurements,
clinical symbols and normal/bold embedded DejaVu were checked on all applicable
pages. The same probe passed with a Liberation-only OS font configuration and
an unwritable cache.

A development Debian package was built on that Ubuntu container using the
repository's `debian/rules`, the prebuilt WAR and the existing changelog version
`2026.09.0~snapshot26`. `SKIP_DRUGREF=1` and `SKIP_EFORM_RENDERER=1` omitted unrelated
payloads; this is a development verification package, not a release artifact.
The five-page probe passed from the package's extracted
`usr/share/carlos-emr/webapp/carlos` classpath under both the normal and
no-OS-DejaVu/unwritable-cache configurations.

These checks exercise the actual WAR and packaged rendering resources. They do
not install/configure the complete services, start Tomcat/MariaDB, or exercise
the authenticated browser flow on an installed system.
Full installed-service acceptance and the alpha20 release decision remain tracked
in #4903; this application fix does not publish or promote a release.

The four Rourke 2017 templates had the same unnamed-default-font pattern. The
application-wide policy and audit in [pdf-font-policy.md](pdf-font-policy.md) (#5046, which
absorbed #4907) now cover them and every other shipped template.

Land this supported-release correctness fix on `release/2026.08`, preserving its
snapshot version and SCM metadata, then forward-merge through the normal release
process. Alpha20 promotion should wait for the required release/deployment gates.
