# PDF output comparison for #5471

Images in this branch support https://github.com/carlos-emr/carlos/issues/5471. This branch holds images only and is not meant to be merged.

- **Before:** the released `2026.08.0-beta5` packages (`carlos-emr`, `carlos-emr-drugref`, `carlos-ctl` 1.3.0) on Ubuntu 26.04, installed by `scripts/deb-docker-validation.sh` (ON, demo data). This is JasperReports 7.0.8 + OpenPDF 1.3.43 for Jasper, and the vendored OpenPDF 3.0.3 for CARLOS code and Flying Saucer.
- **After:** the same install, with only `WEB-INF/lib` changed to JasperReports 7.1.0 (`jasperreports-openpdf` exporter) + stock Maven Central OpenPDF 3.0.5. The database and documents were restored from the same snapshot before the run.
- **PDFs:** captured from the existing Playwright checks, run with the same names, in the same order, against both setups.

All synthetic demo/fixture data; no real patient information.

Every text difference is a date, or a test marker built from the clock: the after run started just past midnight UTC, and fixtures compute dates from "today". Fonts and page counts are identical in all 53 pairs.

| # | PDF (check -- route) | engine | pages before/after | fonts identical | text lines changed | largest per-page pixel change |
|---|---|---|---|---|---|---|
| 1 | `billing-on-invoice-third-party--BillingInvoiceListPrint--01` | JasperReports | 1/1 | yes | 6 | 0.02% |
| 2 | `billing-on-invoice-third-party--BillingInvoicePrint--01` | JasperReports | 1/1 | yes | 6 | 0.02% |
| 3 | `billing-on-year-end-statement--billing_CA_ON_endYearStatement_pdf--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 4 | `billing-on-year-end-statement--billing_CA_ON_endYearStatement_pdf--02` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 5 | `billing-on-year-end-statement--billing_CA_ON_endYearStatement_pdf--03` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 6 | `consultation-pdf-cjk--blob--01` | OpenPDF | 1/1 | yes | 2 | 0.01% |
| 7 | `consultation-pdf-cjk--encounter_oscarConsultationRequest_printPdf2--01` | OpenPDF | 1/1 | yes | 2 | 0.01% |
| 8 | `demographic-label-content--demographic_printClientLabLabelAction--01` | JasperReports | 1/1 | yes | 2 | 0.25% |
| 9 | `demographic-label-content--demographic_printDemoAddressLabelAction--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 10 | `demographic-label-content--demographic_printDemoChartLabelAction--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 11 | `demographic-label-content--demographic_printDemoLabelAction--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 12 | `demographic-label-content--report_GenerateEnvelopes--01` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 13 | `demographic-labels--demographic_printClientLabLabelAction--01` | JasperReports | 1/1 | yes | 2 | 0.25% |
| 14 | `demographic-labels--demographic_printDemoAddressLabelAction--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 15 | `demographic-labels--demographic_printDemoChartLabelAction--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 16 | `demographic-labels--demographic_printDemoLabelAction--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 17 | `demographic-labels--report_GenerateEnvelopes--01` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 18 | `echart-print--CaseManagementEntry--01` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 19 | `echart-print--CaseManagementEntry--02` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 20 | `echart-print--CaseManagementEntry--03` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 21 | `echart-print--CaseManagementEntry--04` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 22 | `echart-print--CaseManagementEntry--05` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 23 | `echart-print--CaseManagementEntry--06` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 24 | `echart-print--CaseManagementEntry--07` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 25 | `echart-print--CaseManagementEntry--08` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 26 | `echart-print--CaseManagementEntry--09` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 27 | `echart-print--CaseManagementEntry--10` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 28 | `echart-print--CaseManagementEntry--11` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 29 | `echart-print--CaseManagementEntry--12` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 30 | `echart-print--CaseManagementEntry--13` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 31 | `echart-print--CaseManagementEntry--14` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 32 | `echart-print--CaseManagementEntry--15` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 33 | `echart-print--CaseManagementEntry--16` | OpenPDF | 4/4 | yes | 0 | 0.00% |
| 34 | `echart-print--CaseManagementEntry--17` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 35 | `form-print-pdf--form_formname--01` | OpenPDF | 1/1 | yes | 2 | 0.01% |
| 36 | `form-rourke2020-growth--any--01` | JasperReports | 5/5 | yes | 18 | 0.01% |
| 37 | `form-rourke2020-growth--any--02` | JasperReports | 1/1 | yes | 10 | 0.01% |
| 38 | `form-rourke2020-growth--any--03` | JasperReports | 2/2 | yes | 12 | 0.01% |
| 39 | `form-rourke2020-growth--form_formname--01` | JasperReports | 2/2 | yes | 12 | 0.01% |
| 40 | `form-rourke2020-growth--form_formname--02` | JasperReports | 1/1 | yes | 10 | 0.01% |
| 41 | `lab-pdf-footer--lab_CA_ALL_PrintPDF--01` | OpenPDF | 11/11 | yes | 0 | 0.00% |
| 42 | `messenger-pdf-attachments-server-render--any--01` | Flying Saucer | 1/1 | yes | 0 | 0.00% |
| 43 | `messenger-pdf-attachments-server-render--any--02` | Flying Saucer | 1/1 | yes | 0 | 0.00% |
| 44 | `messenger-pdf-attachments-server-render--messenger_Doc2PDF--01` | Flying Saucer | 1/1 | yes | 0 | 0.00% |
| 45 | `patient-letter-actions--any--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 46 | `patient-letters-envelopes--report_GenerateEnvelopes--01` | OpenPDF | 1/1 | yes | 2 | 0.39% |
| 47 | `patient-letters-envelopes--report_GenerateEnvelopes--02` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 48 | `patient-letters-envelopes--report_GenerateLetters--01` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 49 | `prevention-print--prevention_printPrevention--01` | OpenPDF | 2/2 | yes | 0 | 0.00% |
| 50 | `prevention-print--prevention_printPrevention--02` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 51 | `prevention-print--prevention_printPrevention--03` | OpenPDF | 2/2 | yes | 0 | 0.00% |
| 52 | `prevention-print--prevention_printPrevention--04` | OpenPDF | 2/2 | yes | 0 | 0.00% |
| 53 | `referral-labels--printReferralLabelAction--01` | JasperReports | 2/2 | yes | 0 | 0.00% |
