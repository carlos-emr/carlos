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

## BC install (second pass)

This pass uses the same method on a fresh BC install (`CARLOS_PROVINCE=bc`). The checks were `billing-bc-account-reports`, `billing-bc-account-report-types`, `billing-bc-account-report-csv`, `form-bc-antenatal-record`, `form-bc-newborn`, `form-catalog-smoke-bc-inr-print`, `export-content-dx-registry-xlsx` and `form-rourke2017`. A direct `submit=printAllJasperReport` request printed the saved Rourke 2017 form. All 8 checks passed in both runs. Images are in `bc/`.

| # | PDF (check -- route) | engine | pages before/after | fonts identical | text lines changed | largest per-page pixel change |
|---|---|---|---|---|---|---|
| 1 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--01` | JasperReports | 1/1 | yes | 2 | 0.00% |
| 2 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--02` | JasperReports | 1/1 | yes | 2 | 0.01% |
| 3 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--03` | JasperReports | 1/1 | yes | 2 | 0.01% |
| 4 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--04` | JasperReports | 1/1 | yes | 2 | 0.01% |
| 5 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--05` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 6 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--06` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 7 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--07` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 8 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--08` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 9 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--09` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 10 | `billing-bc-account-report-types--billing_CA_BC_createBillingReportAction--10` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 11 | `billing-bc-account-reports--billing_CA_BC_createBillingReportAction--01` | JasperReports | 1/1 | yes | 2 | 0.00% |
| 12 | `billing-bc-account-reports--billing_CA_BC_createBillingReportAction--02` | JasperReports | 1/1 | yes | 2 | 0.00% |
| 13 | `billing-bc-account-reports--billing_CA_BC_createBillingReportAction--03` | JasperReports | 1/1 | yes | 0 | 0.00% |
| 14 | `form-bc-antenatal-record--form_BCAR2020--01` | JasperReports | 2/2 | yes | 0 | 0.00% |
| 15 | `form-bc-newborn--form_createpdf--01` | OpenPDF | 1/1 | yes | 0 | 0.00% |
| 16 | `rourke2017-print--form_formname--01` | JasperReports | 4/4 | yes | 0 | 0.00% |

### CSV and Excel exports (JasperReports CSV and `jasperreports-excel-poi` exporters)

| export | type | result |
|---|---|---|
| `billing-bc-account-report-csv--billing_CA_BC_createBillingReportAction--01.csv` | CSV | 6/6 lines; 2 changed (e.g. '-dc,MSP,20-Jan-95,9-9,"FAKE-PW16dbb991de7f1836,Workflow(3010)",1,00100,¤23.00 ,¤') |
| `billing-bc-account-report-csv--billing_CA_BC_createBillingReportAction--02.csv` | CSV | 4/4 lines; 2 changed (e.g. '-MSP,"FAKE-PW16dbb991de7f1836,Workflow(3010)",20-Jan-95,9-9,¤23.00,,,,REJ') |
| `billing-bc-account-report-csv--billing_CA_BC_createBillingReportAction--03.csv` | CSV | 17/17 lines; 2 changed (e.g. '-01-Nov-26 at 12:28 AM,,,,Date Range:1995-01-20 to 1995-01-20,,,,,,Account:ALL,,') |
| `billing-bc-account-reports--billing_CA_BC_createBillingReportAction--04.csv` | CSV | 10/10 lines; 0 changed |
| `export-content-dx-registry-xlsx--report_DxresearchReport--01.xlsx` | XLSX | 224 cells; 0 cell values differ; zip parts differing: docProps/app.xml, xl/worksheets/sheet1.xml |
| `export-content-dx-registry-xlsx--report_DxresearchReport--02.xlsx` | XLSX | 112 cells; 0 cell values differ; zip parts differing: docProps/app.xml, xl/worksheets/sheet1.xml |
| `export-content-dx-registry-xlsx--report_DxresearchReport--03.xlsx` | XLSX | 112 cells; 0 cell values differ; zip parts differing: docProps/app.xml, xl/worksheets/sheet1.xml |
| `export-content-dx-registry-xlsx--report_DxresearchReport--04.xlsx` | XLSX | 112 cells; 0 cell values differ; zip parts differing: docProps/app.xml, xl/worksheets/sheet1.xml |

- **Excel (Dx Research report):** every cell value is identical. The worksheet XML changed only in number formatting: row heights are written `ht="24.0"` instead of `ht="24"`. `docProps/app.xml` now names JasperReports 7.1.0.
- **CSV:** the same rows appear in a different order among rows that tie on every column of the report's `ORDER BY` (`MSPReconcile`). The before run used the freshly installed tables, and the after run used tables reloaded from the snapshot, so MariaDB's order for tied rows can differ. This is most likely not a JasperReports change.
