/**
 * Copyright (c) 2026 CARLOS Contributors.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.github.carlos_emr.carlos.utility;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Pins the Connector/J text and conversions that {@link MariaDbCompatibilityDriver} restores for floating-point
 * and BIT columns (#5968) without a database server.
 *
 * <p>The MariaDB driver is replaced by a scripted result set that answers as MariaDB Connector/J 3.5.11 does:
 * the server's text for {@code getString} and the decoded double or signed long for the numeric getters. The
 * expected values are the ones MySQL Connector/J 9.6.0 returned for the same rows. {@code
 * JdbcConnectorMariaDbIntegrationTest} repeats these reads against a physical MariaDB server.</p>
 *
 * @since 2026-11-10
 */
@Tag("unit")
@DisplayName("Connector/J floating-point and BIT values over MariaDB (#5968)")
class MariaDbCompatibilityDriverNumberUnitTest {
    private static final int DOUBLE = 1;
    private static final int FLOAT = 2;
    private static final int FLAG = 3;
    private static final int WIDE_BIT = 4;
    private static final int TINY_FLAG = 5;
    private static final int TEXT = 6;
    private static final int NULL_BIT = 7;
    private static final int NULL_DOUBLE = 8;

    @Test
    void shouldPrintFloatingColumnsThroughTheirDouble_whenReadAsText() throws Exception {
        withRows(rows -> {
            assertThat(rows.getString(DOUBLE)).isEqualTo("100.0");
            assertThat(rows.getString("d")).isEqualTo("100.0");
            assertThat(rows.getNString(DOUBLE)).isEqualTo("100.0");
            assertThat(rows.getObject(DOUBLE, String.class)).isEqualTo("100.0");
            assertThat(rows.getString(FLOAT)).isEqualTo("1.0E7");
            assertThat(rows.wasNull()).isFalse();
        });
    }

    @Test
    void shouldKeepTheDoubleScale_whenAFloatingColumnIsReadAsBigDecimal() throws Exception {
        withRows(rows -> {
            assertThat(rows.getBigDecimal(DOUBLE)).isEqualTo(new BigDecimal("100.0"));
            assertThat(rows.getObject(FLOAT, BigDecimal.class)).isEqualTo(new BigDecimal("1.0E+7"));
            // Primitive floating reads already match Connector/J and reach MariaDB unchanged.
            assertThat(rows.getDouble(DOUBLE)).isEqualTo(100.0d);
        });
    }

    @Test
    void shouldPrintBitColumnsThroughTheirSignedLong_whenReadAsText() throws Exception {
        withRows(rows -> {
            assertThat(rows.getString(FLAG)).isEqualTo("1");
            assertThat(rows.getString(WIDE_BIT)).isEqualTo("-9223372036854775808");
            assertThat(rows.getString(TINY_FLAG)).isEqualTo("-1");
        });
    }

    @Test
    void shouldReadSingleBitColumnsAsBoolean_whenReadAsObject() throws Exception {
        withRows(rows -> {
            assertThat(rows.getObject(FLAG)).isEqualTo(Boolean.TRUE);
            assertThat(rows.getObject(TINY_FLAG)).isEqualTo(Boolean.TRUE);
            assertThat(rows.getObject(WIDE_BIT)).isEqualTo(new byte[]{(byte) 0x80, 0, 0, 0, 0, 0, 0, 0});
        });
    }

    @ParameterizedTest
    @CsvSource(value = {"-128,false", "-2,false", "-1,true", "0,false", "1,true", "127,true", "NULL,NULL"},
            nullValues = "NULL")
    @DisplayName("should use Connector/J's numeric truth rule for every TINYINT(1) object read")
    void shouldPreserveNumericTruth_whenTinyIntOneIsReadAsObject(Long value, Boolean expected) throws Exception {
        withRows(value, rows -> {
            assertThat(rows.getObject(TINY_FLAG)).isEqualTo(expected);
            assertThat(rows.wasNull()).isEqualTo(value == null);
            assertThat(rows.getObject("t")).isEqualTo(expected);
            assertThat(rows.getObject(TINY_FLAG, Map.of())).isEqualTo(expected);
            assertThat(rows.getObject(TINY_FLAG, Boolean.class)).isEqualTo(expected);
            assertThat(rows.getBoolean(TINY_FLAG)).isEqualTo(Boolean.TRUE.equals(expected));
            assertThat(rows.wasNull()).isEqualTo(value == null);
            assertThat(rows.getString(TINY_FLAG)).isEqualTo(value == null ? null : value.toString());
        });
    }

    @Test
    void shouldConvertWideBitColumnsAsUnsigned_whenReadAsNumbers() throws Exception {
        withRows(rows -> {
            assertThat(rows.getDouble(WIDE_BIT)).isEqualTo(9.223372036854776E18d);
            assertThat(rows.getFloat(WIDE_BIT)).isEqualTo(9.223372E18f);
            assertThat(rows.getBigDecimal(WIDE_BIT)).isEqualTo(new BigDecimal("9223372036854775808"));
            assertThat(rows.getObject(WIDE_BIT, Double.class)).isEqualTo(9.223372036854776E18d);
            assertThat(rows.getObject(WIDE_BIT, Float.class)).isEqualTo(9.223372E18f);
            assertThat(rows.getLong(WIDE_BIT)).isEqualTo(Long.MIN_VALUE);
        });
    }

    @Test
    void shouldKeepTheSign_whenATinyIntOneColumnReportedAsBitIsReadAsANumber() throws Exception {
        withRows(rows -> {
            assertThat(rows.getDouble(TINY_FLAG)).isEqualTo(-1.0d);
            assertThat(rows.getBigDecimal(TINY_FLAG)).isEqualTo(BigDecimal.valueOf(-1));
        });
    }

    @Test
    void shouldReportSqlNull_whenANullFloatingOrBitColumnIsConverted() throws Exception {
        withRows(rows -> {
            assertThat(rows.getString(NULL_BIT)).isNull();
            assertThat(rows.wasNull()).isTrue();
            assertThat(rows.getDouble(NULL_BIT)).isZero();
            assertThat(rows.wasNull()).isTrue();
            assertThat(rows.getFloat(NULL_BIT)).isZero();
            assertThat(rows.wasNull()).isTrue();
            assertThat(rows.getObject(NULL_BIT, Double.class)).isNull();
            assertThat(rows.getBigDecimal(NULL_BIT)).isNull();
            assertThat(rows.getString(NULL_DOUBLE)).isNull();
            assertThat(rows.wasNull()).isTrue();
            assertThat(rows.getBigDecimal(NULL_DOUBLE)).isNull();
            assertThat(rows.wasNull()).isTrue();
        });
    }

    @Test
    void shouldLeaveOtherColumnsAndReadsToMariaDb_forTextAndStreams() throws Exception {
        withRows(rows -> {
            assertThat(rows.getString(TEXT)).isEqualTo("server text");
            assertThat(rows.getBigDecimal(DOUBLE, 2)).isEqualTo(new BigDecimal("100.00"));
            assertThatThrownBy(() -> rows.getCharacterStream(DOUBLE)).isInstanceOf(SQLException.class);
        });
    }

    @Test
    void shouldNameBooleanForEveryBitWidth_whenDescribingColumns() throws Exception {
        withRows(rows -> {
            ResultSetMetaData metadata = rows.getMetaData();
            assertThat(metadata.getColumnClassName(FLAG)).isEqualTo(Boolean.class.getName());
            assertThat(metadata.getColumnClassName(WIDE_BIT)).isEqualTo(Boolean.class.getName());
            assertThat(metadata.getColumnClassName(DOUBLE)).isEqualTo(Double.class.getName());
        });
    }

    /** Opens the compatibility driver over a scripted MariaDB connection and hands its wrapped result set over. */
    private static void withRows(RowsCheck check) throws Exception {
        withRows(-1L, check);
    }

    private static void withRows(Long tinyFlag, RowsCheck check) throws Exception {
        ResultSet nativeRows = scriptedRows(tinyFlag);
        org.mariadb.jdbc.Statement nativeStatement = mock(org.mariadb.jdbc.Statement.class);
        when(nativeStatement.executeQuery(anyString())).thenReturn(nativeRows);
        org.mariadb.jdbc.Connection physical = mock(org.mariadb.jdbc.Connection.class, Answers.RETURNS_DEEP_STUBS);
        when(physical.createStatement()).thenReturn(nativeStatement);
        when(physical.getContext().getConnectionTimeZone()).thenReturn(TimeZone.getTimeZone("UTC"));
        when(physical.unwrap(org.mariadb.jdbc.Connection.class)).thenReturn(physical);
        try (MockedConstruction<org.mariadb.jdbc.Driver> drivers = mockConstruction(org.mariadb.jdbc.Driver.class,
                (driver, context) -> when(driver.connect(anyString(), any(Properties.class))).thenReturn(physical))) {
            Connection connection = new MariaDbCompatibilityDriver().connect("jdbc:mysql://127.0.0.1:3306/probe", new Properties());
            assertThat(drivers.constructed()).isNotEmpty();
            try (connection; Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT *")) {
                check.accept(rows);
            }
        }
    }

    /** A result set that answers like MariaDB Connector/J 3.5.11 for one row of the probe columns. */
    private static ResultSet scriptedRows(Long tinyFlag) throws SQLException {
        Map<Integer, Object> values = new HashMap<>();
        values.put(DOUBLE, 100.0d);
        values.put(FLOAT, 1.0E7d);
        values.put(FLAG, 1L);
        values.put(WIDE_BIT, Long.MIN_VALUE);
        values.put(TINY_FLAG, tinyFlag);
        values.put(TEXT, "server text");
        values.put(NULL_BIT, null);
        values.put(NULL_DOUBLE, null);
        Map<Integer, String> serverText = Map.of(DOUBLE, "100", FLOAT, "10000000", FLAG, "b'1'",
                WIDE_BIT, "b'1000000000000000000000000000000000000000000000000000000000000000'", TINY_FLAG, String.valueOf(tinyFlag),
                TEXT, "server text");
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        int[] types = {0, Types.DOUBLE, Types.REAL, Types.BIT, Types.BIT, Types.BIT, Types.VARCHAR, Types.BIT, Types.DOUBLE};
        int[] precisions = {0, 22, 12, 1, 64, 1, 255, 8, 22};
        String[] typeNames = {"", "DOUBLE", "FLOAT", "BIT", "BIT", "TINYINT", "VARCHAR", "BIT", "DOUBLE"};
        for (int column = 1; column < types.length; column++) {
            when(metadata.getColumnType(column)).thenReturn(types[column]);
            when(metadata.getPrecision(column)).thenReturn(precisions[column]);
            when(metadata.getColumnTypeName(column)).thenReturn(typeNames[column]);
            when(metadata.getColumnClassName(column)).thenReturn(types[column] == Types.BIT ? byte[].class.getName()
                    : types[column] == Types.VARCHAR ? String.class.getName() : Double.class.getName());
        }
        boolean[] lastNull = {false};
        return mock(ResultSet.class, invocation -> {
            String name = invocation.getMethod().getName();
            Object[] args = invocation.getArguments();
            switch (name) {
                case "getMetaData": return metadata;
                case "wasNull": return lastNull[0];
                case "findColumn": return switch ((String) args[0]) {
                    case "d" -> DOUBLE;
                    case "t" -> TINY_FLAG;
                    default -> 0;
                };
                case "close": return null;
                default: break;
            }
            if (args.length == 0 || !(args[0] instanceof Integer column)) return null;
            Object value = values.get(column);
            lastNull[0] = value == null;
            return switch (name) {
                case "getDouble" -> value == null ? 0.0d : ((Number) value).doubleValue();
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                case "getString", "getNString" -> value == null ? null : serverText.get(column);
                case "getObject" -> value instanceof Long bits && precisions[column] > 1
                        ? java.nio.ByteBuffer.allocate(8).putLong(bits).array() : value;
                case "getBigDecimal" -> value == null ? null
                        : new BigDecimal(serverText.get(column)).setScale((Integer) args[1]);
                case "getCharacterStream" -> throw new SQLException("Data type DOUBLE cannot be decoded as Reader");
                default -> null;
            };
        });
    }

    @FunctionalInterface
    private interface RowsCheck {
        void accept(ResultSet rows) throws Exception;
    }
}
