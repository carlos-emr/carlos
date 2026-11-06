<!--
Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

This software is published under the GPL GNU General Public License.
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

CARLOS EMR Project
https://github.com/carlos-emr/carlos
-->

# BC billing and Dx Research spreadsheet exports

Issue #5438 moves eight BC CSV exports to Apache Commons CSV and Dx Research XLSX to a direct table writer. The #5472 dependency cleanup supplies the shared JDK OOXML implementation; POI remains a test-only workbook reader. PDF generation and the three shared remittance subreports remain on Jasper. The pre-change files are in `src/test/resources/export-baseline`, committed separately before the replacement.

All CSV files retain `<REP_TYPE>.csv`, UTF-8 without BOM, LF records and the `application/csv` content type (now with an explicit UTF-8 charset). All fields are quoted. They have one header, no run timestamp, report/page titles, blank layout columns, repeated page headings or page-number footers. Each source record occupies one CSV record; embedded newlines remain quoted cell content. The shared `SpreadsheetCsv` policy prefixes formula-like text with a tab, including leading tab/CR/LF and disguised formula markers; plain negative numbers stay numeric. This guard tab is intentional cell content.

Money is plain decimal CAD with two places and no currency symbol or thousands separators. Dates use `dd-MMM-yy` with Canadian English month names. Missing or legacy `00000000` report dates are empty. Malformed nonempty accounting dates and amounts retain their source text (with the same spreadsheet formula guard), so legacy rows do not abort the download. Out-of-range amounts retain their original notation. An invoice whose billed amount is unavailable or cannot be represented as a finite balance has an empty calculated Balance; A/R retains that row rather than treating its unknown balance as settled. Malformed S00 payment amounts also leave the calculated balance empty and retain the claim, without assuming a zero payment. Payment/refund summaries count missing or malformed amounts and individual adjustment components as zero, while retaining valid payments, refunds and adjustment components (including adjustments on zero payments). In payment detail, an unparseable adjustment total shows the seven components separated by ` | `; fractional-cent payment input is labeled `Unparsed Teleplan amount` to avoid presenting it as decimal dollars. Patient DOB, HIN and phone strings are preserved as supplied. Host locale does not select any output format.

Intentional changes by export, relative to the baseline:

| Export | Columns and changes |
| --- | --- |
| Account receivable | Insurer, Patient, ServDate, Invoice#, 91 Days+, 61 - 90 Days, 31 - 60 Days, 0 - 30 Days, Status, Unknown age. Keeps the legacy oldest-first aging order and populated amount bucket; day 91 belongs to 91 Days+. Future service dates belong to 0 - 30 Days. Issue #5662 appends Unknown age after the original nine columns: a missing, blank, legacy 00000000 or malformed service date puts the amount there and leaves the four known-age buckets empty. Valid dates leave Unknown age empty. Each claim retains its amount in exactly one bucket. |
| Invoice | Account, Insurer, ServDate, Inv#-Line#, Patient, Qty, Item, Amt, Balance, Status, Adjustment Code. Keeps the old order; separates the adjustment code formerly appended to Amt. |
| Write-off | ID#, Type, Insurer, Account, Patient, Amount, ServDate, Item, DX. Type is no longer truncated to Typ. |
| Rejection | Patient, Invoice #, ServDate, RejDate, ID#, Qty, Item, Ins, DX, Ref, Balance, DOB, Sex, Phone 1, Phone 2, Start, End, ToDate, MVA, Family Doctor, Location, Explanation. Flattens the four-line Jasper record and retains its secondary fields as named columns. Removes labels embedded in data cells and the presentation TOTAL row. DOB stays the original text. |
| Payments and refunds | Payment Date, Type, Insurer, Account, Patient, Amount, Explanations, Sequence, ServDate, Provider, Adjustment Amount, Adjustment Description. Separates the formerly combined adjustment amount/description. |
| Payments and refunds summary | Payment Method, Payments (CAD), Refunds (CAD). Eight payment categories and a Total record replace the side-by-side page layout. Payments retains the historical net semantics (includes refunds and electronic adjustments); Refunds is the negative-payment subset. BigDecimal accumulation replaces floating-point summation. |
| MSP remittance billed | Account, Type, ServDate, Payment Date, Data Centre, Patient, Paid (CAD), Explanation, Sequence, Item. Keeps the legacy order and first explanation code. The bound payee filter is now an exact match; SQL wildcard characters no longer expand a requested payee. |
| MSP remittance summary | One 22-column header and Record Type = Remittance, Practitioner, Adjustment or Message. The remaining columns are Payment Date, Payee No, Payee Name, Billed (CAD), Paid (CAD), Cheque (CAD), Practitioner No, Practitioner Name, Adjustment Code, Adjustment Description, Calculation Method, Regular Percent, One-time Percent, Gross (CAD), Regular (CAD), One-time (CAD), Balance Forward (CAD), Adjustment Made (CAD), Adjustment Outstanding (CAD), Line Code, Message. Keeps raw percentages, decodes space-padded implied-cents Teleplan amounts, includes S23 rows even when a practitioner record is missing, and retains broadcast line/practitioner identifiers. Removes Jasper's repeated per-practitioner adjustment totals (consumers can aggregate the data). All four queries bind the remittance ID; deleted remittances and practitioner records remain excluded. |
| Dx Research XLSX | First Name, Last Name, Sex, DOB, Phone, HIN, Code System, Code, Start Date, Update Date, Status. Keeps the visual column order, filename `dxResearchReport.xlsx`, and XLSX content type. Removes title/provider/status labels, timestamps, layout cells and paging. Uses one frozen header, 22-character columns and one patient record per row; all cells are strings, including formula-like text and leading-zero identifiers. The JDK writer constructs one worksheet row at a time without POI or workbook temporary files; an oversized worksheet fails before download. |

Files are completed in private temporary files before download headers or response streams are accessed. Generation/query failures return HTTP 500 with no attachment or partial file; missing/invalid download parameters return HTTP 400. Billing and report privilege checks remain in the action entry points. Export failure logs contain fixed messages and exception class names, never patient rows or exception messages. The CSV accounting reader uses strict failure propagation; PDF callers retain their existing behavior.
