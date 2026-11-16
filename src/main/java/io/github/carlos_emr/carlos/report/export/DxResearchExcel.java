/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo;
import io.github.carlos_emr.carlos.report.SpreadsheetExport;

/**
 * Dx exports are data tables: every field is explicitly a string, never a formula or number.
 *
 * @since 2026-11-02
 */
public final class DxResearchExcel {
    private DxResearchExcel() {
    }

    /**
     * Writes one frozen header and one row per patient, constructing one worksheet row at a time.
     * Null field values become empty strings; identifiers and formula-like text stay string cells.
     *
     * @param output OutputStream receiving the XLSX workbook; remains owned by the caller
     * @param patients List of DxRegistedPTInfo records in the required export order
     * @throws IOException if workbook output fails or the worksheet row limit is exceeded
     */
    public static void write(OutputStream output, List<DxRegistedPTInfo> patients) throws IOException {
        if (patients.size() >= 1_048_576) throw new IOException("Export exceeds worksheet row limit");
        String[] headers = {"First Name", "Last Name", "Sex", "DOB", "Phone", "HIN", "Code System",
                "Code", "Start Date", "Update Date", "Status"};
        Iterable<Object[]> rows = () -> patients.stream().map(p -> new Object[] {
                        p.getStrFirstName(), p.getStrLastName(), p.getStrSex(), p.getStrDOB(),
                        p.getStrPhone(), p.getStrHIN(), p.getStrCodeSys(), p.getStrCode(), p.getStrStartDate(),
                        p.getStrUpdateDate(), p.getStrStatus()}).iterator();
        SpreadsheetExport.writeTable(output, "Dx Research", headers, rows);
    }
}
