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

# Jasper export baseline for #5438

Captured before the replacement, from release/2026.08 at 112feaecb7b, using JasperReports 7 and synthetic map rows matching each template's fields. All nine exports are present. CSV bytes are the JRCsvExporter output; XLSX bytes are the JRXlsxExporter output, with extracted cell values alongside it for review. The remittance summary uses an H2 JDBC fixture with S21, S22, S23, adjustment-code and S25 rows, including the three shared subreports. The manifest records the response names/types and byte conventions. Run timestamps are incidental capture-time values.

Synthetic inputs use IDs 1, dates 20261001, name Synthetic Élodie, amount/balance -1234.50, HIN/phone 0012345678, reason Synthetic reason, aging bucket 4. Summary parameters contain cash -1234.50. Teleplan values are 1 (one cent), date 20261001 and Synthetic Clinic/adjustment/broadcast labels. No clinic data is used.

The current release already fixes PayRefSummary's CAD locale, the summary filename and the missing orphan subreport. These fixtures reflect that release, rather than the older develop revision audited in the issue.

`Capture.java.txt` contains the standalone capture program. Copy it to a scratch `Baseline.java`, put the baseline revision's compiled application classes, runtime Jasper dependencies and H2 on its classpath, and invoke it with the baseline `src/main/resources` directory and an output directory. Its XLSX exporter uses the same configuration as the pre-change `OscarDocumentCreator` (including type detection). `DxResearchReport.cells.json` lists the resulting sheet values.
