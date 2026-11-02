# JasperReports and OpenPDF 3

CARLOS uses JasperReports 7.1.0 with `jasperreports-openpdf` and stock Maven
Central `com.github.librepdf:openpdf:3.0.5`. Application PDF code, Flying
Saucer 10.7.0 and OpenRTF 3.0.0 share that same OpenPDF jar. The legacy
`jasperreports-pdf` exporter, OpenPDF 1.3.43 and the vendored OpenPDF 3.0.3
coordinate are removed. Maven Enforcer prevents their reintroduction.

## Stock OpenPDF and tagging

The new exporter declares Jaspersoft's patched `3.0.5.jaspersoft.1` build.
CARLOS excludes it and selects the stock Central release explicitly. The
patch serves PDF/UA link-annotation structure tagging; CARLOS does not
enable PDF tagging or PDF/UA. Stock OpenPDF covers the application's
existing untagged report exports without adding another repository.
Reassess that choice before enabling tagged PDF or PDF/UA exports.

## Brotli and PDF compatibility

Keep OpenPDF's upstream `brotli4j` dependency, including its Linux amd64
native jar. OpenPDF's `PdfReader` uses it to decode incoming PDF streams
with `/BrotliDecode`. Removing it would leave that supported reader path
without its implementation. The native library loads on first Brotli use.
CARLOS does not enable Brotli output: OpenPDF defaults to Flate compression.

JasperReports-generated files now use PDF 2.0, matching files CARLOS
already generated directly with OpenPDF 3 and Flying Saucer. This changes
the PDF header for labels, invoices and statements; it does not change
their printed content or make PDF/UA claims.

## Other consumers

Displaytag 3.8.0 contains optional export implementations that reference
`com.lowagie`, but CARLOS does not select them. Displaytag's bundled defaults
disable PDF export (`export.pdf=false`) and name `FopExportView`; no CARLOS
override enables its legacy OpenPDF export views. Its OpenPDF 2.4.0
dependency is optional and does not bring another OpenPDF jar into the WAR.
Do not enable those legacy views with the unified OpenPDF 3 classpath.

## Regression evidence

[Issue #5471](https://github.com/carlos-emr/carlos/issues/5471) records the
installed ON and BC before/after comparisons: 69 PDF pairs with matching
page counts and fonts, no material visual changes, and four matching Excel
cell-value comparisons. The reported text changes were dates and clock-based
synthetic markers. BC CSV row-order differences involved tied database sort
keys and unchanged rows.

The repository also checks the export contracts directly:

- `DemographicLabelPdfUnitTest` reads the label's auto-print JavaScript
  back from the generated PDF.
- `JasperExporterCompatibilityUnitTest` checks `OscarDocumentCreator`
  auto-print JavaScript, Unicode PDF text, embedded DejaVu fonts and Excel
  cell values with the application's POI version.
- `AppointmentReceiptTemplateUnitTest` exports the actual receipt template
  with its `force.linebreak.policy` property, checking wrapped Unicode names,
  all booking details, embedded fonts and every glyph's page bounds in
  English and French.
- `JasperTemplateFontPolicyUnitTest` and `scripts/check-report-fonts.sh`
  verify the bundled font policy across host fonts and cache permissions.

Only synthetic fixtures are used for these checks. Installation and browser
verification for the proposed change are recorded in the PR.
