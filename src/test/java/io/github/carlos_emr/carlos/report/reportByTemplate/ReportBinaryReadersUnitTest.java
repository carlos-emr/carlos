/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Binary BLOB compatibility through both public bounded report consumers.
 * @since 2026-11-16
 */
@Tag("unit")
@Tag("report")
@Isolated("Temporarily overrides the configured report character budget")
class ReportBinaryReadersUnitTest {
    private enum Kind { CONFIGURED, TEMPLATE }

    static Stream<Arguments> binaryKinds() {
        return Arrays.stream(Kind.values()).flatMap(kind -> IntStream.of(
                Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB)
                .mapToObj(type -> Arguments.of(kind, type)));
    }

    @ParameterizedTest
    @MethodSource("binaryKinds")
    void shouldStreamBinaryUtf8Text_withoutUsingTheRefusedCharacterCodec(Kind kind, int type) throws Exception {
        String expected = "FAKE report \u03a9 caf\u00e9";
        ResultSet rows = rows(type);
        var stream = new TrackedBytes(expected.getBytes(StandardCharsets.UTF_8));
        when(rows.getBinaryStream(1)).thenReturn(stream);
        when(rows.getCharacterStream(1)).thenThrow(new SQLDataException("Data type BLOB cannot be decoded as Reader"));
        assertThat(read(kind, rows)).isEqualTo(expected);
        assertThat(stream.closed).isTrue();
        verify(rows, never()).getString(1);
        verify(rows, never()).getCharacterStream(1);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void shouldKeepSqlNullSemantics_forBinaryCells(Kind kind) throws Exception {
        ResultSet rows = rows(Types.BLOB);
        assertThat(read(kind, rows)).isEqualTo(kind == Kind.CONFIGURED ? null : "");
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void shouldRetainCharacterStreams_forTextColumns(Kind kind) throws Exception {
        ResultSet rows = rows(Types.VARCHAR);
        when(rows.getCharacterStream(1)).thenReturn(new StringReader("FAKE text \u03a9"));
        assertThat(read(kind, rows)).isEqualTo("FAKE text \u03a9");
        verify(rows, never()).getBinaryStream(1);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void shouldRetainBoundedScalarReads_forNumericColumns(Kind kind) throws Exception {
        ResultSet rows = rows(Types.INTEGER);
        when(rows.getString(1)).thenReturn("42");
        assertThat(read(kind, rows)).isEqualTo("42");
        verify(rows, never()).getBinaryStream(1);
        verify(rows, never()).getCharacterStream(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {8, 9})
    void shouldCountDecodedCharacters_andRetainQualifiedColumnLookup(int characters) throws Exception {
        var properties = CarlosProperties.getInstance();
        String setting = ConfiguredReportQuery.Limit.OUTPUT.setting();
        // The public budget counts the five-character column name plus decoded cell characters.
        Object previous = properties.setProperty(setting, "13");
        try {
            ResultSet rows = rows(Types.BLOB);
            String text = "\u03a9".repeat(characters);
            var stream = new TrackedBytes(text.getBytes(StandardCharsets.UTF_8));
            when(rows.getBinaryStream(1)).thenReturn(stream);
            if (characters == 8) {
                assertThat(read(Kind.CONFIGURED, rows, "report.value")).isEqualTo(text);
            } else {
                assertThatThrownBy(() -> read(Kind.CONFIGURED, rows, "report.value"))
                        .isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class,
                                failure -> assertThat(failure.limit()).isEqualTo(ConfiguredReportQuery.Limit.OUTPUT));
            }
            assertThat(stream.closed).isTrue();
        } finally {
            if (previous == null) properties.remove(setting);
            else properties.put(setting, previous);
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void shouldRetainScalarCellRefusals_afterAddingBinaryDecoding(Kind kind) throws Exception {
        ResultSet rows = rows(Types.INTEGER);
        when(rows.getString(1)).thenReturn("1".repeat(2 * ConfiguredReportQuery.MAX_OUTPUT_CHARACTERS));
        assertThatThrownBy(() -> read(kind, rows)).satisfies(failure ->
                assertThat(failure.getClass().getSimpleName()).isEqualTo("LimitExceededException"));
        verify(rows, never()).getBinaryStream(1);
        verify(rows, never()).getCharacterStream(1);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void shouldCloseAndStopTheBinaryStream_beforeMaterializingAnOversizedCell(Kind kind) throws Exception {
        ResultSet rows = rows(Types.BLOB);
        var stream = new GeneratedBytes(2 * ConfiguredReportQuery.MAX_OUTPUT_CHARACTERS);
        when(rows.getBinaryStream(1)).thenReturn(stream);
        when(rows.getCharacterStream(1)).thenThrow(new SQLDataException("Data type BLOB cannot be decoded as Reader"));
        assertThatThrownBy(() -> read(kind, rows)).satisfies(failure -> {
            assertThat(failure.getClass().getSimpleName()).isEqualTo("LimitExceededException");
            if (kind == Kind.CONFIGURED) {
                assertThat(failure).isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class,
                        limit -> assertThat(limit.limit()).isEqualTo(ConfiguredReportQuery.Limit.OUTPUT));
            } else {
                assertThat(failure).isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class,
                        limit -> assertThat(limit.limit()).isEqualTo(ReportResultBudget.Limit.CELL));
            }
        });
        assertThat(stream.bytesRead).isLessThan(stream.length);
        assertThat(stream.closed).isTrue();
        verify(rows, never()).getString(1);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void shouldCloseTheBinaryStream_whenReadingFails(Kind kind) throws Exception {
        ResultSet rows = rows(Types.BLOB);
        GeneratedBytes stream = new GeneratedBytes(1) {
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("synthetic stream failure");
            }
        };
        when(rows.getBinaryStream(1)).thenReturn(stream);
        when(rows.getCharacterStream(1)).thenThrow(new SQLDataException("Data type BLOB cannot be decoded as Reader"));
        if (kind == Kind.CONFIGURED) {
            assertThatThrownBy(() -> read(kind, rows)).isInstanceOf(SQLException.class)
                    .hasMessage("Could not read report value").hasRootCauseMessage("synthetic stream failure");
        } else {
            assertThatThrownBy(() -> read(kind, rows)).isInstanceOf(IOException.class).hasMessage("synthetic stream failure");
        }
        assertThat(stream.closed).isTrue();
    }

    private static ResultSet rows(int type) throws SQLException {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(metadata.getTableName(1)).thenReturn("");
        when(metadata.getColumnType(1)).thenReturn(type);
        when(rows.next()).thenReturn(true, false);
        return rows;
    }

    private static String read(Kind kind, ResultSet rows) throws Exception {
        return read(kind, rows, "value");
    }

    private static String read(Kind kind, ResultSet rows, String column) throws Exception {
        if (kind == Kind.CONFIGURED) {
            try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
                jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), anyInt(), anyLong(), any(Object[].class)))
                        .thenReturn(rows);
                return ConfiguredReportQuery.read("select value from report").rows().getFirst().value(column);
            }
        }
        var budget = new ReportResultBudget();
        int remaining = budget.remainingRows();
        var result = budget.render(rows, null);
        assertThat(budget.remainingRows()).isEqualTo(remaining - 1);
        try (var csv = CSVParser.parse(result.csv(), CSVFormat.DEFAULT)) {
            return csv.getRecords().get(1).get(0);
        }
    }

    private static final class TrackedBytes extends ByteArrayInputStream {
        private boolean closed;
        private TrackedBytes(byte[] value) { super(value); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    private static class GeneratedBytes extends InputStream {
        private final int length;
        private int bytesRead;
        private boolean closed;
        private GeneratedBytes(int length) { this.length = length; }
        @Override public int read() { return bytesRead == length ? -1 : increment(); }
        private int increment() { bytesRead++; return 'x'; }
        @Override public int read(byte[] buffer, int offset, int count) throws IOException {
            int available = Math.min(count, length - bytesRead);
            if (available == 0) return -1;
            Arrays.fill(buffer, offset, offset + available, (byte) 'x');
            bytesRead += available;
            return available;
        }
        @Override public void close() { closed = true; }
    }
}
