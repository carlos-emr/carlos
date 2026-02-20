# JasperReports migration plan for Jakarta transition

## Recommendation: target version

For this codebase, the **correct and recommended target is JasperReports 7.x (latest 7.x patch at migration time)** rather than jumping directly to an unknown 8.x line.

Why this is the safer recommendation for CARLOS:

1. The project currently pins JasperReports **6.21.5** and already has hand-managed transitive overrides (Jackson exclusions/overrides), so a major bump should be staged carefully. (`pom.xml`)
2. The code still contains **legacy exporter API usage** (`JRExporterParameter`), **runtime JRXML compilation**, and **older DTD-based templates**. These are migration-sensitive patterns better handled in a single major-version step before any second major jump.
3. A Jakarta migration already increases risk in servlet/JSP and framework layers; keeping JasperReports on the next major line (7.x) is the lowest-risk modernization path.

> Practical policy: migrate to the newest 7.x first, run full report regression, then evaluate 8.x separately.

---

## Current JasperReports footprint (what must be refactored)

### Dependency and compatibility anchor

- `pom.xml`
  - Current dependency: `net.sf.jasperreports:jasperreports:6.21.5`.
  - Explicit exclusions for Jackson and old bouncycastle/commons-beanutils indicate dependency conflict handling that must be revisited after upgrade.

### Core Java report engine wrappers

- `src/main/java/io/github/carlos_emr/OscarDocumentCreator.java`
  - Central abstraction used by many actions (`fillDocumentStream`).
  - Compiles JRXML at runtime from `InputStream` and exports to PDF/CSV/XLSX.
- `src/main/java/io/github/carlos_emr/carlos/commn/service/PdfRecordPrinter.java`
  - Uses Jasper directly and still relies on legacy exporter parameter API.

### Direct Jasper usage in form/report actions

- `src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2017Record.java`
- `src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2020Record.java`
- `src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR20202Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/GeneratePatientLetters2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/ManagePatientLetters2Action.java`

### Places setting Jasper compile classpath system property

These should be standardized/removed during refactor because they are globally mutable and brittle in Jakarta containers:

- `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/CreateBillingReport2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/commn/web/PrintReferralLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintClientLabLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintDemoAddressLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintDemoChartLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintDemoLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/GeneratePatientLetters2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/ManagePatientLetters2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/PrintAppointmentReceipt2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/printLabDaySheet2Action.java`

### JRXML/XML templates that need validation and likely conversion

Legacy DTD/iReport templates (highest risk):

- `src/main/resources/oscar/oscarReport/pageUtil/labDaySheet.xml`
- `src/main/resources/oscar/oscarReport/pageUtil/billDaySheet.xml`
- `src/main/resources/oscar/oscarBilling/ca/bc/reports/csv_rep_payref.xml`
- `src/main/resources/oscar/oscarBilling/ca/on/reports/end_year_statement_subreport.jrxml`
- `src/main/resources/oscar/oscarDemographic/Addresslabel.xml`
- `src/main/resources/oscar/oscarDemographic/ClientLabLabel.xml`
- `src/main/resources/oscar/oscarDemographic/AppointmentReceipt.xml`
- `src/main/resources/org/oscarehr/common/web/DxResearchReport.jrxml`
- `src/main/resources/org/oscarehr/common/web/reflabel.xml`
- `src/main/resources/label.xml`
- `src/main/resources/label-appt.xml`
- `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/reports/end_year_statement_subreport.jrxml` (duplicate copy under `src/main/java` to resolve)

Modern Jaspersoft templates (still require compile/fill/export validation on 7.x):

- `src/main/resources/oscar/form/rourke2017/*.jrxml`
- `src/main/resources/oscar/form/rourke2020/*.jrxml`
- `src/main/resources/oscar/form/bcar2020/*.jrxml`
- `src/main/resources/oscar/oscarBilling/ca/bc/reports/*.jrxml`
- `src/main/resources/oscar/oscarBilling/ca/on/reports/end_year_statement_report.jrxml`
- `src/main/resources/org/oscarehr/common/web/BillingInvoiceTemplate.jrxml`

---

## Detailed refactoring plan (file-by-file)

## Phase 0 - Baseline and safety net

1. Add migration tracking document (this file) and establish report regression matrix (PDF/CSV/XLSX + key templates).
2. Capture current outputs from representative flows before any code change:
   - demographic labels,
   - referral labels,
   - billing reports (BC and ON),
   - Rourke forms,
   - BCAR form,
   - patient letters,
   - invoice PDF via `PdfRecordPrinter`.

## Phase 1 - Build/dependency refactor

### `pom.xml`

Refactor tasks:

1. Bump `net.sf.jasperreports:jasperreports` from `6.21.5` to latest `7.x` patch.
2. Re-evaluate explicit exclusions/overrides:
   - Jackson exclusions currently required by 6.x transitive graph may change.
   - Keep explicit secure versions where still needed; remove obsolete overrides after `mvn dependency:tree` verification.
3. Confirm compatibility of `itextpdf` with Jasper 7 exporter stack used here.
4. Produce updated dependency lock evidence (if your dependency lock workflow is active in CI).

## Phase 2 - API modernization in Java code

### `src/main/java/io/github/carlos_emr/carlos/commn/service/PdfRecordPrinter.java`

Required refactoring:

1. Replace deprecated legacy exporter API:
   - remove `JRExporter` + `JRExporterParameter` usage.
   - standardize to typed exporter API used elsewhere:
     - `JRPdfExporter exporter = new JRPdfExporter();`
     - `exporter.setExporterInput(new SimpleExporterInput(jasperPrint));`
     - `exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(os));`
2. Keep `JasperFillManager.fillReport(...)` path but add explicit failure handling if template/params are missing under new engine behavior.
3. Consider introducing a shared helper (in `OscarDocumentCreator`) to avoid duplicated exporter wiring logic.

### `src/main/java/io/github/carlos_emr/OscarDocumentCreator.java`

Required refactoring:

1. Keep this class as the single integration seam for Jasper 7.
2. Strengthen typing and null-safety:
   - replace raw `HashMap` parameters with `Map<String, Object>` where feasible.
   - enforce non-null `JasperReport` before fill/export.
3. Add explicit exception propagation strategy:
   - currently errors are logged and swallowed; for migration validation, optionally bubble up a checked/unchecked wrapper in selected flows.
4. Add hooks for compile caching (optional but strongly recommended):
   - cache compiled reports by template checksum/path to reduce repeated runtime compilation.
5. Verify/adjust PDF JavaScript property behavior with Jasper 7:
   - `net.sf.jasperreports.export.pdf.javascript` support should be tested in affected label flows.

### `src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2017Record.java`
### `src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2020Record.java`
### `src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR20202Action.java`

Required refactoring:

1. Stop compiling from `cl.getResource(...).toURI().getPath()`; this is fragile in packed WAR/JAR and different classloader contexts.
2. Compile from `InputStream` (resource stream) via centralized helper in `OscarDocumentCreator` or a new report utility.
3. Keep multi-page `JRPdfExporter` assembly but standardize exporter setup through shared utility method.

### `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/ManagePatientLetters2Action.java`

Required refactoring:

1. Keep pre-compile validation behavior but align exceptions/logging with Jasper 7 parser outcomes.
2. If templates are persisted in DB as byte arrays, ensure encoding assumptions are explicit (UTF-8).

### `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/GeneratePatientLetters2Action.java`

Required refactoring:

1. Validate subreport handling and `JasperExportManager.exportReportToPdfFile(...)` behavior with Jasper 7.
2. Confirm all report parameters are provided after any JRXML migration (schema changes can make missing parameters fail earlier).

## Phase 3 - Remove/contain global compile-classpath hacks

Target files:

- `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/CreateBillingReport2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/commn/web/PrintReferralLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintClientLabLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintDemoAddressLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintDemoChartLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/demographic/PrintDemoLabel2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/GeneratePatientLetters2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/ManagePatientLetters2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/PrintAppointmentReceipt2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/printLabDaySheet2Action.java`

Required refactoring:

1. Replace per-action `System.setProperty("jasper.reports.compile.class.path", ...)` calls with one controlled initialization point (startup config) or remove entirely if Jasper 7 compile path resolution works via classloader.
2. If still required for specific runtime compilers, isolate in a dedicated service and guard with idempotence + diagnostics.
3. Remove repetitive boilerplate from each action once centralized.

## Phase 4 - JRXML/XML template migration workstream

### High priority: legacy DTD templates

Files:

- `src/main/resources/oscar/oscarReport/pageUtil/labDaySheet.xml`
- `src/main/resources/oscar/oscarReport/pageUtil/billDaySheet.xml`
- `src/main/resources/oscar/oscarBilling/ca/bc/reports/csv_rep_payref.xml`
- `src/main/resources/oscar/oscarBilling/ca/on/reports/end_year_statement_subreport.jrxml`
- `src/main/resources/oscar/oscarDemographic/Addresslabel.xml`
- `src/main/resources/oscar/oscarDemographic/ClientLabLabel.xml`
- `src/main/resources/oscar/oscarDemographic/AppointmentReceipt.xml`
- `src/main/resources/org/oscarehr/common/web/DxResearchReport.jrxml`
- `src/main/resources/org/oscarehr/common/web/reflabel.xml`
- `src/main/resources/label.xml`
- `src/main/resources/label-appt.xml`

Required refactoring:

1. Convert DTD-based syntax to schema-based JRXML where needed.
2. Open/save in Jaspersoft Studio compatible with Jasper 7 to auto-upgrade element metadata.
3. Re-validate fonts, measurements, and image expressions (pixel/unit differences may appear).
4. Re-test SQL/query fields and parameter class names.

### Medium priority: modern JRXML verification

Files:

- `src/main/resources/oscar/form/rourke2017/*.jrxml`
- `src/main/resources/oscar/form/rourke2020/*.jrxml`
- `src/main/resources/oscar/form/bcar2020/*.jrxml`
- `src/main/resources/oscar/oscarBilling/ca/bc/reports/*.jrxml`
- `src/main/resources/oscar/oscarBilling/ca/on/reports/end_year_statement_report.jrxml`
- `src/main/resources/org/oscarehr/common/web/BillingInvoiceTemplate.jrxml`

Required refactoring:

1. Recompile each template under Jasper 7 and fix any parser or expression breakages.
2. Validate subreport references and resource paths.
3. Standardize import statements only where needed (remove wildcard imports if causing parser/compiler ambiguity).

### Cleanup task: duplicate template source

- `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/reports/end_year_statement_subreport.jrxml`

Required refactoring:

1. Decide canonical location (should be under `src/main/resources`).
2. Remove duplicate to avoid divergence and classpath ambiguity.

## Phase 5 - Functional validation and regression tests

Minimum report test matrix by flow:

1. **Labels**: demo label, address label, referral label, appointment receipt, client lab label.
2. **Billing**:
   - BC report variants (`pdf_rep_*`, `csv_rep_*`, premium summaries).
   - ON year-end statement (report + subreport).
3. **Clinical forms**: Rourke 2017, Rourke 2020, BCAR2020 multi-page exports.
4. **Letters**: manage/compile + generate/export paths.
5. **Invoice**: `BillingInvoiceTemplate.jrxml` path in `PdfRecordPrinter`.

Validation criteria:

- Report compiles successfully.
- No missing parameter/field exceptions.
- Output rendering parity acceptable (layout, fonts, pagination).
- Export formats remain correct (PDF opens, CSV delimiter correctness, XLSX sheet structure).

## Phase 6 - Post-migration hardening

1. Add targeted tests around `OscarDocumentCreator` (unit/integration style) for PDF/CSV/XLSX.
2. Add smoke tests for representative templates (compile + fill with empty/mock data).
3. Document operational runbook for adding/changing JRXML after Jasper 7 migration.

---

## Execution order (recommended)

1. `pom.xml` bump + dependency resolution.
2. `PdfRecordPrinter` exporter API modernization.
3. `OscarDocumentCreator` hardening and resource-loading standardization.
4. Form classes (`FrmRourke2017Record`, `FrmRourke2020Record`, `FrmBCAR20202Action`) resource compile-path refactor.
5. Remove/centralize `jasper.reports.compile.class.path` mutation in all actions.
6. Migrate high-risk legacy DTD templates.
7. Recompile/verify modern templates.
8. Full report regression matrix and sign-off.

---

## Expected effort and risk hotspots

Highest-risk items:

1. DTD-era templates and iReport-generated XML.
2. Runtime compilation path assumptions (`toURI().getPath()` and global compile classpath property).
3. Legacy exporter API usage in `PdfRecordPrinter`.
4. Multi-format export compatibility (CSV/XLSX/PDF behavior differences).

Estimated implementation profile:

- **Refactor + dependency update**: medium.
- **Template migration/validation**: high (most time-consuming).
- **Regression testing**: high but predictable with a fixed matrix.
