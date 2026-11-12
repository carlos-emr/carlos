/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("report")
class SpreadsheetExportUnitTest {
    @Test
    void shouldRoundTripTypedCells_withUnicodeWhitespaceAndFormulaLookingText() throws Exception {
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
    void shouldRoundTripUnpairedSurrogates_withoutCorruptingWorksheetXml() throws Exception {
        Object[][] values = {{"before\uD800after", "before\uDC00after", "\uD800\uD800", "\uDC00\uD800", "😀\uD800😀", "_xD800_"}};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SpreadsheetExport.write(output, values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var row = workbook.getSheet("OSCAR_Report").getRow(0);
            for (int column = 0; column < values[0].length; column++) {
                assertThat(row.getCell(column).getStringCellValue()).isEqualTo(values[0][column]);
            }
        }
    }

    @Test
    void shouldPreserveOverlappingExcelEscapeTokens_whenWritingLiteralText() throws Exception {
        Object[][] values = {{"_x005F_x0041_", "_x0041_x0042_", "_x000D__x005F_",
                "_x005F_x005F_x005F_", "before_x0041_x0042_after"}};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SpreadsheetExport.write(output, values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var row = workbook.getSheet("OSCAR_Report").getRow(0);
            for (int column = 0; column < values[0].length; column++) {
                assertThat(row.getCell(column).getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(column).getStringCellValue()).isEqualTo(values[0][column]);
            }
        }
    }

    @Test
    void shouldPreserveLiteralEscapePrefixes_whenTheNextCharacterIsEscaped() throws Exception {
        // Each prefix lacks its own closing underscore; the _xHHHH_ written for the following
        // CR, C0 control or lone surrogate would otherwise supply it and decode the literal.
        Object[][] values = {{"ref_x1234\r\nnext", "_x0041\u0001", "_x00e9\uD800", "_x0041\uFFFE",
                "_x0041", "a_x12345_", "_x_x0041\r"}};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SpreadsheetExport.write(output, values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var row = workbook.getSheet("OSCAR_Report").getRow(0);
            for (int column = 0; column < values[0].length; column++) {
                assertThat(row.getCell(column).getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(column).getStringCellValue()).isEqualTo(values[0][column]);
            }
        }
    }

    @Test
    void shouldFlushOnceAndKeepTheStreamOpen_whenTheWorkbookIsComplete() throws Exception {
        RecordingOutputStream output = new RecordingOutputStream(Long.MAX_VALUE);
        SpreadsheetExport.writeTable(output, "Report", new String[] {"Name"}, List.<Object[]>of(new Object[] {"x"}));
        assertThat(output.flushes).isEqualTo(1);
        assertThat(output.sizeAtFirstFlush).isEqualTo(output.size());
        assertThat(output.closes).isZero();
        assertThat(endOfCentralDirectory(output.toByteArray())).isTrue();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheet("Report").getRow(1).getCell(0).getStringCellValue()).isEqualTo("x");
        }
    }

    @Test
    void shouldAbandonTheArchiveWithoutFlushing_whenALazilyValidatedRowIsInvalid() {
        // Enough valid rows to have streamed worksheet bytes before the invalid row is reached.
        List<Object[]> rows = new ArrayList<>(Collections.nCopies(2_000, new Object[] {"synthetic", "row"}));
        rows.add(new Object[] {"synthetic", 42});
        RecordingOutputStream output = new RecordingOutputStream(Long.MAX_VALUE);
        assertThatThrownBy(() -> SpreadsheetExport.writeTable(output, "Report", new String[] {"A", "B"}, rows))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spreadsheet cells must be strings or finite doubles");
        assertThat(output.size()).isPositive();
        assertThat(output.flushes).isZero();
        assertThat(output.closes).isZero();
        assertThat(endOfCentralDirectory(output.toByteArray())).isFalse();
    }

    @Test
    void shouldRethrowWithoutFlushingOrFinishing_whenTheTargetFailsMidWrite() {
        // Distinct rows so the deflated worksheet passes the failure point, not just its first parts.
        Object[][] rows = new Object[5_000][];
        for (int i = 0; i < rows.length; i++) rows[i] = new Object[] {"synthetic row " + i, i + 0.25};
        RecordingOutputStream output = new RecordingOutputStream(2_048);
        assertThatThrownBy(() -> SpreadsheetExport.write(output, rows))
                .isSameAs(output.failure);
        assertThat(output.flushes).isZero();
        assertThat(output.closes).isZero();
        assertThat(output.writesAfterFailure).isZero();
        assertThat(endOfCentralDirectory(output.toByteArray())).isFalse();
    }

    @Test
    void shouldRejectUnsupportedCellTypes_beforeWritingTheResponse() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThatThrownBy(() -> SpreadsheetExport.write(output, new Object[][]{{42}}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(output.size()).isZero();
    }

    /**
     * True when the bytes end with a ZIP end-of-central-directory record (the writer adds no
     * archive comment, so it is the last 22 bytes): a recipient could then open a truncated sheet.
     */
    private static boolean endOfCentralDirectory(byte[] bytes) {
        int at = bytes.length - 22;
        return at >= 0 && bytes[at] == 'P' && bytes[at + 1] == 'K' && bytes[at + 2] == 5 && bytes[at + 3] == 6;
    }

    /** Records flush/close calls, as a servlet response would commit on flush, and can fail mid-write. */
    private static final class RecordingOutputStream extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final long failAfter;
        private final IOException failure = new IOException("synthetic write failure");
        private int flushes;
        private int closes;
        private int sizeAtFirstFlush = -1;
        private int writesAfterFailure;
        private boolean failed;

        RecordingOutputStream(long failAfter) {
            this.failAfter = failAfter;
        }

        @Override
        public void write(int value) throws IOException {
            check(1);
            bytes.write(value);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            check(length);
            bytes.write(data, offset, length);
        }

        private void check(int length) throws IOException {
            if (failed) {
                writesAfterFailure++;
            } else if (bytes.size() + (long) length > failAfter) {
                failed = true;
                throw failure;
            }
        }

        @Override
        public void flush() {
            if (flushes++ == 0) sizeAtFirstFlush = bytes.size();
        }

        @Override
        public void close() {
            closes++;
        }

        int size() {
            return bytes.size();
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }
}
