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
}
