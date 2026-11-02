/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo;

/**
 * Dx exports are data tables: every field is explicitly a string, never a formula or number.
 *
 * @since 2026-11-02
 */
public final class DxResearchExcel {
    private DxResearchExcel() {
    }

    /**
     * Writes one frozen header and one row per patient, retaining only 100 worksheet rows in memory.
     * Null field values become empty strings; identifiers and formula-like text stay string cells.
     *
     * @param output OutputStream receiving the XLSX workbook; remains owned by the caller
     * @param patients List of DxRegistedPTInfo records in the required export order
     * @throws IOException if workbook output fails or the worksheet row limit is exceeded
     */
    public static void write(OutputStream output, List<DxRegistedPTInfo> patients) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            var sheet = workbook.createSheet("Dx Research");
            String[] headers = {"First Name", "Last Name", "Sex", "DOB", "Phone", "HIN", "Code System",
                    "Code", "Start Date", "Update Date", "Status"};
            var header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
                sheet.setColumnWidth(c, 22 * 256);
            }
            sheet.createFreezePane(0, 1);
            int index = 1;
            for (DxRegistedPTInfo p : patients) {
                if (index >= 1_048_576) {
                    throw new IOException("Export exceeds worksheet row limit");
                }
                String[] values = {p.getStrFirstName(), p.getStrLastName(), p.getStrSex(), p.getStrDOB(),
                        p.getStrPhone(), p.getStrHIN(), p.getStrCodeSys(), p.getStrCode(), p.getStrStartDate(),
                        p.getStrUpdateDate(), p.getStrStatus()};
                var row = sheet.createRow(index++);
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c).setCellValue(values[c] == null ? "" : values[c]);
                }
            }
            workbook.write(output);
        }
    }
}
