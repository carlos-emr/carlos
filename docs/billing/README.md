# Ontario MOH OHIP billing — manual + supplementary references

This directory holds the upstream contract documents that the Ontario billing
module under `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/**`
implements against. The implementation side is documented in
[`../billing-ontario-module.md`](../billing-ontario-module.md).

## Files in this directory

| File | What it is |
|------|------------|
| [`moh-tech-spec-health-care-systems-manual-en-2023-06-12.pdf`](moh-tech-spec-health-care-systems-manual-en-2023-06-12.pdf) | **Authoritative source.** Ontario MOH "Interface to Health Care Systems Technical Specifications", v6.2, April 1 2023. Defines the OHIP claim-file format, MCEDT envelope, Remittance Advice (RA) record layout, error-code workflow, and Health Card magnetic-stripe spec. |
| [`moh-tech-spec-health-care-systems-manual.md`](moh-tech-spec-health-care-systems-manual.md) | Markdown rendering of the PDF above for grep/diff use during development. **Not authoritative** — if it disagrees with the PDF, the PDF wins. |
| [`external/`](external/) | Snapshots of supplementary PDFs and HTML pages the manual hyperlinks to (specialty/province/diagnostic/RA/error code tables, MCEDT/EBS/HCV companion specs, MOH portal pages). |

## Authoritative-PDF disclaimer

The Markdown rendering is a working copy for developer convenience —
`grep`-able, diff-able, and visible in PRs. It was produced by [`marker-pdf`
1.10.2](https://github.com/VikParuchuri/marker) followed by manual cleanup,
and like any auto-conversion of a 171-page table-heavy document it can have
silent errors. **For any field-level decision in production code (offset,
length, format, allowed values), open the PDF and confirm.** The Markdown is
for orientation and search; the PDF is the truth.

## Table of contents (mirrors the source PDF)

Page numbers refer to the original PDF. The Markdown has matching
`<!-- page NNN -->` comments at every page boundary.

### Chapter 1 — Introduction (PDF p.5)
- 1.1 Introduction (p.6)
- 1.2 Contact Number and Email (p.6)

### Chapter 2 — General Information (PDF p.7)
- 2.1 Processing Schedules (p.8)
- 2.2 Medical Claims Electronic Data Transfer (MCEDT) (p.8)

### Chapter 3 — Claims Submission (PDF p.9)
- 3.1 Claims Submission References (p.10)
- 3.2 Other Technical Specifications (p.10)

### Chapter 4 — Electronic Input Specifications (PDF p.11)
- 4.1 Media Types (p.12)
- 4.2 File Naming Convention (p.12)
- 4.3 Claim Submission (p.13)
- 4.4 Format Summary (p.13)
- 4.5 Batch File Submission Sample (p.14)
- 4.6 Summary of Data Requirements (p.15)
- **4.7 Electronic Input (EI) Record Layout (p.17)** — Batch Header,
  Claim Header-1, Claim Header-2, Item, Batch Trailer record formats.
  Drives `OhipClaimFileService` (fixed-width claim-file generation) and the
  `BillingBatchHeaderDto` / claim DTOs under `billings/ca/on/dto/`.
- 4.8 Specialty Codes (p.37) — refers to [`external/specialty_codes.md`](external/specialty_codes.md)
- 4.9 Services Requiring Diagnostic Codes (p.37) — refers to [`external/diagnostic_codes.md`](external/diagnostic_codes.md)
- 4.10 Fee Schedule Code Relationships (p.39)
- 4.11 Fee Schedule Code Suffix B/C Exceptions (p.65)
- 4.12 Service Codes Requiring Specialized Submissions (p.67)
- 4.13 Service Location Indicator Codes (p.68)
- 4.14 MOD 10 Check Digit (p.78)
- 4.15 Province Code and Numbering (p.79) — refers to [`external/province_and_territory_codes.md`](external/province_and_territory_codes.md)
- 4.16 Valid Payment Program/Payee Combinations (p.79)
- 4.17 Workplace Safety and Insurance Board (WSIB) (p.80)

### Chapter 5 — Electronic Output Specifications for Reports (PDF p.81)
- 5.1 Claims Batch Edit Reports (p.82)
- 5.2 Remittance Advice (RA) (p.82)
- 5.3 Remittance Advice Data Sequences (p.83)
- 5.4 File Naming Convention — Remittance Advice (p.85)
- 5.5 Format Summary (p.86)
- **5.6 Remittance Advice (RA) Record Layout (p.89)** — File Header,
  Address, Claim Header, Claim Item, Balance Forward, Accounting
  Transaction, Message Facility records (Record Types 1-8). Drives
  `BillingONRemittanceAdviceService`.
- 5.7 Accounting Transactions for Record Type 7 (p.105)
- 5.8 Remittance Advice Explanatory Codes (p.106) — full table at
  [`external/remittance_advice_explanatory_codes.md`](external/remittance_advice_explanatory_codes.md) (224 codes)
- 5.9 Generic Governance Report (p.106)

### Chapter 6 — Rejection Conditions (PDF p.113)
- 6.1 Correction of Errors (p.114)
- 6.2 Rejection Categories (p.114)
- 6.3 Error Report Explanatory Message Codes (p.137) — full table at
  [`external/error_report_explanatory_codes.md`](external/error_report_explanatory_codes.md) (31 codes)
- 6.4 Error Report Rejection Conditions — Error Codes (p.137) — full table at
  [`external/error_report_rejection_conditions.md`](external/error_report_rejection_conditions.md) (188 codes across 6 sections)

### Chapter 7 — Health Card Magnetic Stripe Specifications (PDF p.138)
- 7.1 Health Card Types (p.139)
- 7.2 Magnetic Stripe Specifications for Photo Health Card (p.141)

### Chapter 8 — Information Management System (IMS) Connect (PDF p.145)
- 8.1 Information Management System Connect (p.146)
  - 8.1.1 TCP/IP Data Specifications for use with IMS Connect (p.149)
  - 8.1.2 TCP/IP Socket Troubleshooting (p.151)
  - 8.1.3 IMS Connect Information (p.152)
- 8.2 GO Net TCP/IP Data Specifications for use with IMS Listener (p.160)

### Chapter 9 — Glossary (PDF p.166)

## External / supplementary resources (`external/`)

The manual hyperlinks out to several documents that it does not include
inline. Snapshots of all of them were captured at conversion time
(2026-04-29) and committed under `external/`. **MOH itself was returning
HTTP 502 with an expired TLS certificate at fetch time**, so the snapshots
were taken via the Internet Archive Wayback Machine. Re-fetches must
continue to use Wayback (`https://web.archive.org/web/<timestamp>id_/<url>`)
or wait until MOH brings their site back up.

### Critical OHIP code tables (referenced from §4.x and §5.8 / §6.3 / §6.4)

| File | Codes / rows | Used by |
|------|--------------|---------|
| [`external/specialty_codes.md`](external/specialty_codes.md) | 65 specialty codes (4 GFM tables, by physician/dental/practitioner/other) | EI Claim Header-1 §4.7 "Specialty" field; §4.8 |
| [`external/diagnostic_codes.md`](external/diagnostic_codes.md) | 749 diagnostic codes across 21 GFM tables (3- and 4-digit) | EI Item Record §4.7 "Diagnostic Code" field; §4.9 |
| [`external/province_and_territory_codes.md`](external/province_and_territory_codes.md) | All Canadian province / territory codes (verbatim text — 3-column layout doesn't extract cleanly; refer to PDF for the canonical table) | EI Claim Header-2 §4.7 "Province Code" field; §4.15 |
| [`external/remittance_advice_explanatory_codes.md`](external/remittance_advice_explanatory_codes.md) | 224 RA explanatory codes (single GFM table) | RA Claim Item Record §5.6 "Explanatory Code" field; §5.8 |
| [`external/error_report_explanatory_codes.md`](external/error_report_explanatory_codes.md) | 31 error report explanatory codes | §6.3 |
| [`external/error_report_rejection_conditions.md`](external/error_report_rejection_conditions.md) | 188 error rejection codes (6 GFM tables: General, Health Number VHA-VH9, IHF EF1-EF9, RMB R01-R09, Telemedicine ET/TM, WSIB VW1) | §6.4 |

### Companion technical specifications (referenced from §3.2)

| File | Subject |
|------|---------|
| [`external/techspec_mcedt_ebs.md`](external/techspec_mcedt_ebs.md) | MCEDT SOAP service over EBS (claim submission, file upload/download, batch lifecycle, March 2013 v3.0) |
| [`external/techspec_ebs.md`](external/techspec_ebs.md) | Generic Electronic Business Services security spec (WS-Security envelope, MSA/IDP cert, signing, audit) |
| [`external/techspec_hcv_ebs.md`](external/techspec_hcv_ebs.md) | Health Card Validation real-time service over EBS |
| [`external/techspec_outside_use_en.md`](external/techspec_outside_use_en.md) | Outside Use Report spec (cross-provider utilization disclosure) |
| [`external/ohipvalid_manual_mn.md`](external/ohipvalid_manual_mn.md) | Health Card Validation reference manual (operational + business rules) |

### MOH portal pages

| File | Subject |
|------|---------|
| [`external/mcedt_landing.md`](external/mcedt_landing.md) | MCEDT landing page (registration, web vs. web-service interface, 2024/25 cut-off dates) |
| [`external/master_number_system.md`](external/master_number_system.md) | Master Numbering System landing — points to the 2022 MNB spreadsheet (4-digit health-facility / program identifiers used in CIHI abstracts) |
| [`external/ohip_landing.md`](external/ohip_landing.md) | "Resources for Physicians" hub (billing briefs, registration, SOB, EBS, claim submission) |
| [`external/schedule_of_benefits_physician_services.md`](external/schedule_of_benefits_physician_services.md) | 2016 Schedule of Benefits landing page (legacy `/english/...` URL; main deliverable is the master SOB PDF link) |
| [`external/faq_tech_specs.md`](external/faq_tech_specs.md) | ~85-question FAQ covering MCEDT/HCV web-service security models, PKI/ARM certs, audit trails, conformance testing, file storage |

### Note on extraction fidelity

The 11 supplementary PDFs were converted to Markdown via `pypdfium2` text
extraction (lighter than `marker-pdf` and runnable without GPU). Quality
varies:
- 5 of 6 OHIP code tables came out as clean GFM tables (`specialty_codes`,
  `diagnostic_codes`, `remittance_advice_explanatory_codes`,
  `error_report_explanatory_codes`, `error_report_rejection_conditions`).
- `province_and_territory_codes` has a multi-column "Province / Code /
  Format / Prior format" layout that pypdfium2 cannot reliably reconstruct —
  the Markdown is a verbatim text block; refer to the PDF for the table.
- The 5 companion tech specs are prose-heavy and rendered as preformatted
  text per page; for cleaner GFM re-extraction, re-run `marker_single` on
  the original PDFs.

## How this directory was produced

1. `marker_single` (marker-pdf 1.10.2, CPU mode, ~60 min wall time)
   converted the source PDF with `--paginate_output --disable_image_extraction`.
2. Mechanical post-processing stripped the running PDF page-header
   ("Interface to Health Care Systems Technical Specifications") and
   page-footer ("Page N of 171"), and split into per-page Markdown.
3. A manual cleanup pass:
   - rebuilt the auto-mangled Table of Contents on PDF p.3 (chapters 1-4),
   - inserted missing record-type subheadings on pp.20 (Claim Header-1) and
     p.27 (Claim Header-1 + Claim Header-2 continuations),
   - normalized heading levels in the §5.6 RA record-layout section
     (mixed H2/H4 → consistent H3) and on p.31 (Item Record H4 → H3),
   - fixed 110 OCR confusables (Greek Α/Cyrillic А/Cyrillic Х silently
     substituted for Latin A/X in Format-column cells across the EI/RA
     record-layout tables).
4. Three parallel agents pulled the 16 external resources via Wayback
   Machine and converted them to Markdown alongside the original PDFs.

## Cross-references back to the codebase

- Module overview & layer policy: [`../billing-ontario-module.md`](../billing-ontario-module.md)
- OHIP fixed-width claim-file generation: `OhipClaimFileService`,
  `OhipClaimExtractService`, `OhipReportGenerationService` (all under
  `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/`)
- Claim record DTOs (Batch Header, Claim Header, Item, Trailer):
  `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/dto/Billing*Dto.java`
- Disk creation & batch assembly: `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/BillingDiskCreationService.java`
- Remittance Advice import & reconciliation: `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/BillingONRemittanceAdviceService.java`
- Correction workflow: `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/BillingCorrectionRecordService.java`
- Bill review loader: `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/BillingReviewLoader.java`
- Struts gates (`*2Action`): `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/web/`
