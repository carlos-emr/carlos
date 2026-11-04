/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.handler;

import java.io.StringReader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ExportQueryHandlerUnitTest {
    @Test
    void shouldRoundTripFields_whenValuesContainCsvSyntaxOrNull() throws Exception {
        String csv = ExportQueryHandler.writeLine(new Object[]{"Zoë", "Ro\"b", "a,b", "a\r\nb", null, 42});
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)) {
            var records = parser.getRecords();
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().toList()).containsExactly("Zoë", "Ro\"b", "a,b", "a\r\nb", "", "42");
        }
        assertThat(csv).endsWith("\n");
    }
    @Test
    void shouldWriteEmptyRecord_whenNoColumnsExist() {
        assertThat(ExportQueryHandler.writeLine(new Object[0])).isEqualTo("\n");
    }
    @Test
    void shouldNeutralizeFormulas_whenExportingUntrustedCellText() throws Exception {
        Object[] values = {"=1+1", "+cmd", "-cmd", "@SUM(A1)", "  =1+1", "\tname", "\rname", "\nname", -12.5};
        String csv = ExportQueryHandler.writeLine(values);
        assertThat(csv).startsWith("\"\t=1+1\"");
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)) {
            var row = parser.getRecords().getFirst();
            for (int i = 0; i < values.length - 1; i++) assertThat(row.get(i)).isEqualTo("\t" + values[i]);
            assertThat(row.get(values.length - 1)).isEqualTo("-12.5");
        }
    }

}
