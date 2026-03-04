# PDF Dependency Consolidation Plan

> **Status**: Draft — Pending team review
> **Target**: Consolidate from 5+ PDF libraries → 2 (OpenPDF + PDFBox)
> **Risk Level**: High (healthcare forms, lab reports, prescriptions — visual fidelity is critical)
> **Estimated Scope**: ~45 Java files, 4 phases, each independently shippable

## Executive Summary

CARLOS EMR currently uses **5+ overlapping PDF libraries** for PDF creation, form filling, HTML-to-PDF conversion, and document manipulation. This plan consolidates to **two** libraries:

1. **OpenPDF 2.0.x** (LGPL, `com.github.librepdf:openpdf`) — PDF creation, form filling, stamping, embedded JS
2. **Apache PDFBox 2.0.35** (Apache 2.0) — PDF merging, splitting, encryption, rendering

### Why Consolidate?

- **License risk**: iText 5.x is AGPL-licensed (copyleft for server-side use). OpenPDF is LGPL (safe for CARLOS's GPL codebase)
- **Maintenance**: iText 5.x is EOL; OpenPDF is actively maintained
- **Dependency sprawl**: Fewer libraries = smaller attack surface, simpler upgrades
- **API compatibility**: OpenPDF forked from iText 2.1.7 and maintains a nearly identical API under `com.lowagie.*` packages — Flying Saucer already depends on it transitively

---

## Current State Inventory

### Dependencies in pom.xml

| Library | GroupId:ArtifactId | Version | License | Usage | Action |
|---------|-------------------|---------|---------|-------|--------|
| iText 5 | `com.itextpdf:itextpdf` | 5.5.13.5 | AGPL | PDF creation (45 files) | **REMOVE** |
| iText XMLWorker | `com.itextpdf.tool:xmlworker` | 5.5.13.5 | AGPL | HTML→PDF in Doc2PDF | **REMOVE** |
| PDFBox | `org.apache.pdfbox:pdfbox` | 2.0.35 | Apache 2.0 | Merge, split, encrypt (11 files) | **KEEP** |
| Flying Saucer | `org.xhtmlrenderer:flying-saucer-pdf` | 9.13.3 | LGPL | XHTML→PDF (4 files) | **KEEP** |
| OpenRTF | `com.github.librepdf:openrtf` | 2.0.0 | LGPL | RTF export (1 file) | **KEEP** |
| ultrabuk-htmltopdf | `com.github.openosp:ultrabuk-htmltopdf-java` | 1.0.11 | — | wkhtmltopdf wrapper (1 file) | **KEEP (defer)** |
| OpenPDF | (transitive via Flying Saucer) | ~2.0.x | LGPL | — | **ADD explicit** |

### Files by Library Usage

**45 files import `com.itextpdf.*`** — the bulk of the migration work:

#### Group A: PDF Creation from Scratch (21 files)
These files create PDFs programmatically using `Document`, `PdfWriter`, `PdfPTable`, `Paragraph`, `Font`, etc.

| File | Feature | Risk |
|------|---------|------|
| `prevention/pageUtil/PreventionPrintPdf.java` | Immunization records | Medium |
| `prevention/pageUtil/HeaderPageEvent.java` | Page headers for prevention PDFs | Low |
| `prevention/pageUtil/PreventionPrint2Action.java` | Prevention print controller | Low |
| `lab/ca/all/pageUtil/LabPDFCreator.java` | Lab result PDFs (1500+ lines) | **High** |
| `fax/util/PdfCoverPageCreator.java` | Fax cover pages | Medium |
| `encounter/.../ConsultationPDFCreator.java` | Consultation request PDFs | Medium |
| `encounter/.../EctConsultationFormRequestPrintPdf.java` | Consultation print PDFs | Medium |
| `encounter/.../ImagePDFCreator.java` | Image-based consultation PDFs | Medium |
| `report/pageUtil/GenerateEnvelopes2Action.java` | Mailing envelopes | Low |
| `report/pageUtil/GeneratePatientSpreadSheetList2Action.java` | Spreadsheet exports | Low |
| `hospitalReportManager/HRMPDFCreator.java` | Hospital report PDFs | Medium |
| `jobs/OscarOnCallClinic.java` | On-call clinic PDF schedules | Low |
| `casemgmt/service/CaseManagementPrintPdf.java` | Clinical note PDFs | **High** |
| `casemgmt/service/PageNumberStamper.java` | Page number events | Low |
| `casemgmt/service/PromoTextStamper.java` | Promo text footer events | Low |
| `casemgmt/service/FooterSupport.java` | Footer rendering | Low |
| `casemgmt/service/MeasurementPrint.java` | Measurement data PDFs | Medium |
| `casemgmt/print/OscarChartPrinter.java` | E-chart printing | Medium |
| `casemgmt/service/CaseManagementPrint.java` | Case management interface | Low |
| `casemgmt/util/ExtPrint.java` | Extended print utility | Low |
| `casemgmt/web/EChartPrint2Action.java` | E-chart print controller | Low |

#### Group B: PDF Form Filling / AcroForms (4 files)
These files open existing PDF templates and fill in AcroForm fields.

| File | Feature | Risk |
|------|---------|------|
| `form/pdfservlet/FrmPDFServlet.java` | Medical form PDF generation (Rourke, BCAR, ONAR, etc.) | **High** |
| `form/pdfservlet/FrmCustomedPDFServlet.java` | Custom form PDF generation | **High** |
| `form/pharmaForms/formBPMH/pdf/PDFController.java` | BPMH medication form (AcroForm + embedded JS) | **High** |
| `lab/ca/all/upload/handlers/PDFHandler.java` | Lab PDF form reading | Medium |

#### Group C: PDF Reading / Metadata / Merging (8 files)
These use `PdfReader` and `PdfStamper` for reading, stamping, or merging.

| File | Feature | Risk |
|------|---------|------|
| `encounter/.../ConsultationAttachDocs2Action.java` | Consultation document attachment | Low |
| `encounter/.../EctConsultationFormRequest2Action.java` | Consultation form controller | Low |
| `encounter/.../EctConsultationFormFax2Action.java` | Fax consultation form | Low |
| `encounter/.../EctConsultationFormRequestPrintAction22Action.java` | Print consultation controller | Low |
| `fax/core/FaxImporter.java` | Fax import PDF reading | Medium |
| `managers/ConsultationManager.java` | Consultation interface | Low |
| `managers/ConsultationManagerImpl.java` | Consultation merge logic | Medium |
| `managers/DocumentManagerImpl.java` | Document management | Medium |
| `managers/LabManagerImpl.java` | Lab PDF page counting | Low |
| `documentManager/IncomingDocUtil.java` | Incoming document processing | Medium |
| `documentManager/actions/ManageDocument2Action.java` | Document page insertion | Medium |
| `documentManager/actions/AddEditDocument2Action.java` | Document page counting | Low |

#### Group D: Factories & Utilities (4 files)
Cross-cutting utilities used by Groups A-C.

| File | Feature | Risk |
|------|---------|------|
| `commn/printing/PdfWriterFactory.java` | PdfWriter factory (has both iText and lowagie overloads) | Medium |
| `commn/printing/FontSettings.java` | Font configuration | Low |
| `commn/service/PdfRecordPrinter.java` | Generic record printer (16 iText imports) | **High** |
| `utility/ClinicLogoUtility.java` | Clinic logo for PDF headers | Medium |

#### Group E: HTML-to-PDF (Deferred)
| File | Feature | Action |
|------|---------|--------|
| `util/Doc2PDF.java` | @Deprecated HTML→PDF via XMLWorker | **DEFER** (separate PR) |
| `documentManager/ConvertToEdoc.java` | HTML→PDF via ultrabuk+Flying Saucer fallback | **KEEP as-is** |

#### Group F: Flying Saucer (already on OpenPDF)
| File | Feature | Action |
|------|---------|--------|
| `documentManager/ReplacedElementFactoryImpl.java` | Flying Saucer image handling (com.lowagie) | **KEEP** |

### PDFBox Files (11 files — no changes needed)

These files use `org.apache.pdfbox` and remain unchanged:

- `util/ConcatPDF.java` — PDF merging
- `documentManager/actions/SplitDocument2Action.java` — PDF splitting
- `documentManager/actions/ManageDocument2Action.java` — Document management
- `documentManager/IncomingDocUtil.java` — Page counting via PDFBox
- `documentManager/EDocUtil.java` — Edoc utilities
- `documentManager/DocumentAttachmentManagerImpl.java` — Attachment handling
- `utility/PDFEncryptionUtil.java` — AES-256 encryption
- `managers/NioFileManagerImpl.java` — PDF rendering for thumbnails
- `managers/DocumentManagerImpl.java` — Document management
- `webserv/rest/RxWebService.java` — Prescription PDF export
- `lab/ca/all/upload/handlers/FHIRCommunicationRequestHandler.java` — FHIR handler

---

## JavaScript in PDFs — No Browser JS Needed

Three files embed **Acrobat JavaScript** (PDF-internal JS for auto-printing). This is NOT browser-side JavaScript. These are executed by the PDF viewer (Adobe Acrobat, etc.) when the PDF opens:

1. **`PDFController.java`** — `addJavaScript("this.print({bUI: true, bSilent: true})")` for BPMH auto-print
2. **`GenerateEnvelopes2Action.java`** — `PdfAction.javaScript()` for envelope auto-print
3. **`PrinterList2Action.java`** — `PdfAction.javaScript()` for silent printing

OpenPDF supports `PdfAction.javaScript()` and `PdfWriter.addJavaScript()` identically to iText. No changes needed beyond import renaming.

**Answer to your question**: No, we do NOT need browser-side JavaScript execution for any PDF creation. All PDF generation is server-side Java. The embedded JS is purely for PDF viewer auto-print triggers.

---

## API Migration: iText 5 → OpenPDF 2.0.x

### Package Mapping (Mechanical — import replacement)

| iText 5 (`com.itextpdf.*`) | OpenPDF 2.0.x (`com.lowagie.*`) |
|----|---|
| `com.itextpdf.text.Document` | `com.lowagie.text.Document` |
| `com.itextpdf.text.pdf.PdfWriter` | `com.lowagie.text.pdf.PdfWriter` |
| `com.itextpdf.text.pdf.PdfReader` | `com.lowagie.text.pdf.PdfReader` |
| `com.itextpdf.text.pdf.PdfStamper` | `com.lowagie.text.pdf.PdfStamper` |
| `com.itextpdf.text.pdf.PdfPTable` | `com.lowagie.text.pdf.PdfPTable` |
| `com.itextpdf.text.pdf.PdfPCell` | `com.lowagie.text.pdf.PdfPCell` |
| `com.itextpdf.text.pdf.PdfAction` | `com.lowagie.text.pdf.PdfAction` |
| `com.itextpdf.text.pdf.AcroFields` | `com.lowagie.text.pdf.AcroFields` |
| `com.itextpdf.text.pdf.PdfContentByte` | `com.lowagie.text.pdf.PdfContentByte` |
| `com.itextpdf.text.pdf.BaseFont` | `com.lowagie.text.pdf.BaseFont` |
| `com.itextpdf.text.pdf.ColumnText` | `com.lowagie.text.pdf.ColumnText` |
| `com.itextpdf.text.pdf.PdfPageEventHelper` | `com.lowagie.text.pdf.PdfPageEventHelper` |
| `com.itextpdf.text.Paragraph` | `com.lowagie.text.Paragraph` |
| `com.itextpdf.text.Phrase` | `com.lowagie.text.Phrase` |
| `com.itextpdf.text.Chunk` | `com.lowagie.text.Chunk` |
| `com.itextpdf.text.Font` | `com.lowagie.text.Font` |
| `com.itextpdf.text.Image` | `com.lowagie.text.Image` |
| `com.itextpdf.text.Element` | `com.lowagie.text.Element` |
| `com.itextpdf.text.Rectangle` | `com.lowagie.text.Rectangle` |
| `com.itextpdf.text.PageSize` | `com.lowagie.text.PageSize` |
| `com.itextpdf.text.BaseColor` | `java.awt.Color` ⚠️ |
| `com.itextpdf.text.DocumentException` | `com.lowagie.text.DocumentException` |
| `com.itextpdf.text.FontFactory` | `com.lowagie.text.FontFactory` |
| `com.itextpdf.text.html.simpleparser.HTMLWorker` | `com.lowagie.text.html.simpleparser.HTMLWorker` ⚠️ (needs `openpdf-html` artifact) |

### Known API Differences Requiring Code Changes

#### 1. `BaseColor` → `java.awt.Color` ⚠️ CRITICAL
iText 5 introduced `com.itextpdf.text.BaseColor` to wrap `java.awt.Color`. OpenPDF uses `java.awt.Color` directly.

```java
// iText 5
import com.itextpdf.text.BaseColor;
new Font(bf, 10, Font.NORMAL, BaseColor.RED);
cell.setBackgroundColor(new BaseColor(230, 230, 230));
cell.setBorderColor(BaseColor.BLACK);

// OpenPDF
import java.awt.Color;
new Font(bf, 10, Font.NORMAL, Color.RED);
cell.setBackgroundColor(new Color(230, 230, 230));
cell.setBorderColor(Color.BLACK);
```

**Files affected**: ~15 files use `BaseColor`. This is the most impactful API change.

#### 2. `PdfWriter.getInstance()` Return Type
Both iText 5 and OpenPDF have this method with identical signatures. No code change needed.

#### 3. `PdfStamper` Constructor
Both have `new PdfStamper(reader, os)`. No code change needed.

#### 4. `XMLWorkerHelper` — Does NOT exist in OpenPDF ⚠️
Only used in `Doc2PDF.java` (deferred). No issue for this plan.

#### 5. `HTMLWorker.parseToList()` — Available in OpenPDF ⚠️
Used in `LabPDFCreator.java` for TrueNorth lab format. Available in OpenPDF's `openpdf-html` (previously `pdf-html`) submodule. Must add `com.github.librepdf:openpdf-html` dependency.

#### 6. Font Constants
Both libraries use `Font.BOLD`, `Font.NORMAL`, `Font.ITALIC`, `Font.BOLDITALIC`. Identical.

#### 7. `PdfCopy` and `PdfSmartCopy`
Both exist in OpenPDF. Used for document concatenation in consultation attachment code.

#### 8. Image Handling
`Image.getInstance(byte[])` and `Image.getInstance(URL)` exist in both. No code change.

---

## Phased Migration Plan

### Phase 0: Preparation (Low Risk)
**Goal**: Add explicit OpenPDF dependency, verify no conflicts, create branch

**pom.xml changes:**
```xml
<!-- ADD: Explicit OpenPDF dependency (currently transitive via Flying Saucer) -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
</dependency>

<!-- ADD: OpenPDF HTML module (for HTMLWorker in LabPDFCreator) -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf-html</artifactId>
    <version>2.0.3</version>
</dependency>
```

**Verification**: Build compiles, no classpath conflicts between iText 5 and OpenPDF (different packages, `com.itextpdf.*` vs `com.lowagie.*`).

**Files changed**: 1 (pom.xml)

---

### Phase 1: Utilities & Factories (Low Risk, 6 files)
**Goal**: Migrate cross-cutting utilities that other files depend on

**Files:**
1. `commn/printing/PdfWriterFactory.java` — Remove deprecated `com.lowagie` overloads (redundant after migration), migrate iText overloads to OpenPDF
2. `commn/printing/FontSettings.java` — Update font import if needed
3. `casemgmt/service/PageNumberStamper.java` — `PdfPageEventHelper` import change + `BaseColor` → `Color`
4. `casemgmt/service/PromoTextStamper.java` — Same pattern as PageNumberStamper
5. `casemgmt/service/FooterSupport.java` — Same pattern
6. `utility/ClinicLogoUtility.java` — `Image` import change

**Import pattern** (mechanical for most):
```java
// BEFORE
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.BaseColor;

// AFTER
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
```

**Testing**: Build compiles. Run `make install`. Manual spot-check: Print a case management note (uses PageNumberStamper + PromoTextStamper) and verify page numbers and footer text render correctly.

---

### Phase 2: Low-Risk PDF Creators (Medium Risk, 14 files)
**Goal**: Migrate files that create standalone PDFs (not form-filling)

**Batch 2a — Simple PDF creators (7 files):**
1. `report/pageUtil/GenerateEnvelopes2Action.java` — Envelope PDFs + embedded JS
2. `report/pageUtil/GeneratePatientSpreadSheetList2Action.java` — Spreadsheet export
3. `jobs/OscarOnCallClinic.java` — On-call schedule PDFs
4. `printer/PrinterList2Action.java` — Printer list PDFs + embedded JS
5. `fax/util/PdfCoverPageCreator.java` — Fax cover pages
6. `hospitalReportManager/HRMPDFCreator.java` — Hospital reports
7. `prevention/pageUtil/PreventionPrintPdf.java` + `PreventionPrint2Action.java` + `HeaderPageEvent.java` — Immunization records

**Batch 2b — Consultation PDFs (5 files):**
1. `encounter/.../ConsultationPDFCreator.java`
2. `encounter/.../EctConsultationFormRequestPrintPdf.java`
3. `encounter/.../ImagePDFCreator.java`
4. `encounter/.../ConsultationAttachDocs2Action.java`
5. `encounter/.../EctConsultationFormRequest2Action.java` + `EctConsultationFormFax2Action.java` + `EctConsultationFormRequestPrintAction22Action.java`

**Key change pattern**:
```java
// BEFORE
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
BaseColor.LIGHT_GRAY

// AFTER
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
Color.LIGHT_GRAY  // or new Color(192, 192, 192) for exact match
```

**Testing**: Build compiles. For each batch:
- **2a**: Generate an envelope PDF, fax cover page, prevention record, HRM report — compare visual output with pre-migration baseline
- **2b**: Generate a consultation request PDF with attachments — verify all pages present, images embedded correctly

---

### Phase 3: High-Risk PDF Creators (High Risk, 12 files)
**Goal**: Migrate the most complex PDF generation code

**Batch 3a — Case Management Printing (5 files):**
1. `casemgmt/service/CaseManagementPrintPdf.java` — Core clinical note PDF (complex layout)
2. `casemgmt/service/MeasurementPrint.java` — Measurements in clinical notes
3. `casemgmt/print/OscarChartPrinter.java` — E-chart printer
4. `casemgmt/service/CaseManagementPrint.java` — Interface/base class
5. `casemgmt/util/ExtPrint.java` — Extended print support
6. `casemgmt/web/EChartPrint2Action.java` — Controller

**Batch 3b — Lab & Generic Record Printing (3 files):**
1. `lab/ca/all/pageUtil/LabPDFCreator.java` — **1500+ line lab PDF creator** (highest complexity)
   - Uses both `com.itextpdf` AND `com.lowagie` (for RTF export)
   - Uses `HTMLWorker.parseToList()` (needs `openpdf-html` module)
   - After migration, ALL imports will be `com.lowagie.*` (unified)
2. `commn/service/PdfRecordPrinter.java` — Generic record printer (16 iText imports)
3. `lab/ca/all/upload/handlers/PDFHandler.java` — Lab PDF reading

**Batch 3c — Medical Form Servlets (3 files):**
1. `form/pdfservlet/FrmPDFServlet.java` — **Core medical form PDF** (Rourke, BCAR, ONAR, etc.)
   - Fills AcroForm fields in PDF templates
   - Must verify ALL 150+ template PDFs still fill correctly
2. `form/pdfservlet/FrmCustomedPDFServlet.java` — Custom form variant
3. `form/pharmaForms/formBPMH/pdf/PDFController.java` — BPMH form
   - AcroForm filling + embedded JavaScript for auto-print
   - `addJavaScript()` must work identically in OpenPDF

**Testing (CRITICAL for this phase)**:
- **3a**: Print a full e-chart with clinical notes, measurements, and multiple pages. Compare line-by-line with pre-migration output.
- **3b**: Generate lab result PDFs for different lab formats (standard, TrueNorth). Verify RTF export still works. Test PdfRecordPrinter with various record types.
- **3c**: **MUST test every form template**:
  - Rourke growth charts (2006, 2009, 2017, 2020) — boys + girls
  - BCAR 2020 pages 1-5
  - ONAR pages
  - Lab requisition forms (2007, 2010)
  - Mental health forms
  - BPMH form (verify auto-print JS triggers in PDF viewer)
  - Newborn records
  - All other templates in `src/main/resources/oscar/form/prop/`

---

### Phase 4: Cleanup & Dependency Removal (Low Risk)
**Goal**: Remove iText 5 dependency, clean up remaining references

**Remaining files:**
1. `managers/ConsultationManager.java` — Interface (import only)
2. `managers/ConsultationManagerImpl.java` — Implementation
3. `managers/DocumentManagerImpl.java` — Document management
4. `managers/LabManagerImpl.java` — Lab management
5. `fax/core/FaxImporter.java` — Fax import
6. `documentManager/IncomingDocUtil.java` — Incoming docs
7. `documentManager/actions/ManageDocument2Action.java` — Document management
8. `documentManager/actions/AddEditDocument2Action.java` — Document editing
9. `documentManager/ConvertToEdoc.java` — Update catch clause (already uses com.lowagie fallback)
10. `eform/util/EFormPDFServlet.java` — E-form PDF
11. `lab/ca/all/upload/handlers/FHIRCommunicationRequestHandler.java` — FHIR handler

**Doc2PDF handling** (deferred per user decision):
- `util/Doc2PDF.java` — Keep but update `com.itextpdf` imports to `com.lowagie`
- The `XMLWorkerHelper` usage will be replaced with OpenPDF's `HTMLWorker` (basic but sufficient for this deprecated class)
- This is a minimal-change approach; full Doc2PDF removal is a separate PR

**PdfWriterFactory cleanup:**
- Remove the `@Deprecated` com.lowagie overloads (now redundant since everything is com.lowagie)
- Keep only the active overloads (which are now on com.lowagie too)

**pom.xml changes:**
```xml
<!-- REMOVE -->
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itextpdf</artifactId>
    <version>5.5.13.5</version>
</dependency>
<dependency>
    <groupId>com.itextpdf.tool</groupId>
    <artifactId>xmlworker</artifactId>
    <version>5.5.13.5</version>
</dependency>
```

**Verification**: Full build with tests (`make install --run-tests`). Verify no remaining `com.itextpdf` imports exist. Run comprehensive PDF output tests.

---

## Testing Strategy

### Pre-Migration Baseline
Before starting, generate reference PDFs for visual comparison:
1. Immunization/prevention record PDF
2. Lab result PDF (standard + TrueNorth format)
3. Consultation request PDF with attachments
4. E-chart with clinical notes (multi-page)
5. Fax cover page
6. Rourke 2020 growth chart (filled form)
7. BCAR 2020 page 1 (filled form)
8. BPMH form (verify auto-print triggers)
9. Patient envelope
10. Lab requisition form

### Per-Phase Testing
Each phase must:
1. **Build**: `make install` succeeds with no new compiler errors
2. **Visual**: Compare generated PDFs against baseline (spot-check focus areas)
3. **Functional**: AcroForm fields fill correctly, page numbers render, logos appear, fonts correct
4. **JS**: PDF auto-print JS triggers in Adobe Acrobat/Foxit (Phase 3c)

### Post-Migration Regression
Full regression across all PDF features using the CARLOS devcontainer:
- Create prescriptions and print
- Generate lab result PDFs
- Print prevention records
- Fill and print medical forms (Rourke, BCAR, ONAR)
- Generate consultation requests
- Print e-charts
- Generate envelopes and patient letters
- Send faxes with cover pages
- Upload and split/merge documents

---

## Risk Mitigation

### BaseColor → Color (Highest Risk)
`com.itextpdf.text.BaseColor` wraps `java.awt.Color` with additional constructors. Key differences:
- `BaseColor.RED` → `Color.RED` (identical constant names)
- `BaseColor.LIGHT_GRAY` → `Color.LIGHT_GRAY` (identical)
- `new BaseColor(r, g, b)` → `new Color(r, g, b)` (identical constructor)
- `new BaseColor(r, g, b, a)` → `new Color(r, g, b, a)` (identical constructor)

**Mitigation**: Find-and-replace is safe. The API surface is identical for all usage patterns in CARLOS.

### AcroForm Field Names
PDF template AcroForm fields are identified by string names. OpenPDF's `AcroFields` API is identical to iText's. Field names are embedded in the template PDFs themselves and don't change.

**Mitigation**: Template PDFs are unchanged — they're binary files not affected by this migration. Only the Java code that reads/writes fields changes imports.

### Font Handling
Both libraries use `BaseFont.createFont()` with identical signatures. DejaVu fonts (used throughout) are loaded by path.

**Mitigation**: Font loading code only needs import changes, not API changes.

### RTF Export (LabPDFCreator)
Already uses `com.lowagie.text.rtf.RtfWriter2` from OpenRTF. After migration, LabPDFCreator will consistently use `com.lowagie.*` for both PDF and RTF — actually cleaner than the current mixed state.

---

## Out of Scope (Future Work)

1. **Doc2PDF full removal** — Migrate messenger PDF actions to ConvertToEdoc, then delete Doc2PDF (separate PR)
2. **ultrabuk-htmltopdf-java removal** — Replace wkhtmltopdf wrapper with Flying Saucer as primary HTML→PDF, remove JitPack repo dependency (separate PR)
3. **PDFBox 2.x → 3.x upgrade** — PDFBox 3.0 is available with significant API changes (separate initiative)
4. **Flying Saucer 9.x → 10.x upgrade** — v10 requires Java 21 (we have it) and uses `org.openpdf` packages. This would be a follow-up after the OpenPDF 2.x migration stabilizes. Alternatively, go directly to OpenPDF 3.x + Flying Saucer 10.x in a future phase.
5. **OpenPDF 2.x → 3.x upgrade** — When ready to adopt `org.openpdf.*` packages (replacing `com.lowagie.*`), coordinate with Flying Saucer 10.x upgrade

---

## Summary

| Phase | Files | Risk | Key Change |
|-------|-------|------|------------|
| 0: Preparation | 1 | Low | Add OpenPDF explicit dependency |
| 1: Utilities | 6 | Low | Migrate factories, stampers, font utils |
| 2: Simple PDF Creators | ~14 | Medium | Migrate envelope, fax, prevention, consultation PDFs |
| 3: Complex PDF Creators | ~12 | **High** | Migrate lab PDFs, clinical notes, medical form servlets |
| 4: Cleanup | ~11 | Low | Migrate remaining files, remove iText 5 from pom.xml |

**Total**: ~44 files modified, 2 dependencies removed, 2 dependencies added
**Net result**: 5 PDF libraries → 2 (OpenPDF + PDFBox)
