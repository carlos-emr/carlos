/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("report")
class SpreadsheetExportUnitTest {
    @Test
    void shouldRoundTripTypedCellsUnicodeWhitespaceAndFormulaLookingText() throws Exception {
        Object[][] values = {{"Élodie 李 😀 & <report>", "00123", "1234567890123456789", "=1+2", "  spaced  ", "_x0041_", "a\u0001b\rc\r\nd", 23.5}};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SpreadsheetExport.write(output, values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var row = workbook.getSheet("OSCAR_Report").getRow(0);
            for (int column = 0; column < 7; column++) {
                assertThat(row.getCell(column).getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(column).getStringCellValue()).isEqualTo(values[0][column]);
            }
            assertThat(row.getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(7).getNumericCellValue()).isEqualTo(23.5);
        }
    }

    @Test
    void shouldRejectUnsupportedCellTypes_beforeWritingTheResponse() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SpreadsheetExport.write(output, new Object[][]{{42}}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(output.size()).isZero();
    }
}
