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
package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Logger;

/** MariaDB transport with the result conversion contract of existing CARLOS configurations. */
// JDBC requires legacy temporal types and Calendar overloads. GregorianCalendar's cutover and
// year validation also preserve Connector/J behavior that ISO java.time chronology would change.
public final class MariaDbCompatibilityDriver implements Driver {
    private static final String FALSE_OPTION = "false";
    private static final String TRUE_OPTION = "true";
    private static final String GET_OBJECT = "getObject";
    private static final String GET_BIG_DECIMAL = "getBigDecimal";
    private static final String UNWRAP = "unwrap";
    private static final String INVALID_CONVERSION_STATE = "S1009";
    private static final String INVALID_NUMERIC_STATE = "22018";
    private final Driver delegate = new org.mariadb.jdbc.Driver();

    /** Opens MariaDB transport and retains the installed Connector/J conversion policy for every derived JDBC object. */
    @Override public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        var settings = MariaDbJdbcCompatibility.settings(url, info == null ? new Properties() : info);
        Properties properties = settings.properties();
        properties.putIfAbsent("connectTimeout", "0");
        Connection connection = delegate.connect(settings.url(), properties);
        if (connection == null) throw new SQLException("Database driver rejected configured URL", "08001");
        var nativeConnection = connection.unwrap(org.mariadb.jdbc.Connection.class);
        var policy = new ConversionPolicy(properties.getProperty("zeroDateTimeBehavior"),
                Boolean.parseBoolean(properties.getProperty("useOldAliasMetadataBehavior", FALSE_OPTION)),
                Boolean.parseBoolean(properties.getProperty("treatMysqlDatetimeAsTimestamp", FALSE_OPTION)),
                Boolean.parseBoolean(properties.getProperty("emptyStringsConvertToZero", TRUE_OPTION)),
                Boolean.parseBoolean(properties.getProperty("jdbcCompliantTruncation", TRUE_OPTION)),
                TimeZone.getDefault(), nativeConnection.getContext().getConnectionTimeZone(),
                Boolean.parseBoolean(properties.getProperty("preserveInstants", TRUE_OPTION)));
        return (Connection) wrap(connection, Connection.class, null, null, policy);
    }

    /** Accepts both existing MySQL URLs and MariaDB URLs handled by the configuration adapter. */
    @Override public boolean acceptsURL(String url) { return MariaDbJdbcCompatibility.isMariaDbUrl(url); }
    /** Reports the effective MariaDB properties, including the legacy default connection timeout. */
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        if (url == null) return delegate.getPropertyInfo(null, info);
        var settings = MariaDbJdbcCompatibility.settings(url, info == null ? new Properties() : info);
        settings.properties().putIfAbsent("connectTimeout", "0");
        return delegate.getPropertyInfo(settings.url(), settings.properties());
    }
    /** {@inheritDoc} */
    @Override public int getMajorVersion() { return delegate.getMajorVersion(); }
    /** {@inheritDoc} */
    @Override public int getMinorVersion() { return delegate.getMinorVersion(); }
    /** {@inheritDoc} */
    @Override public boolean jdbcCompliant() { return delegate.jdbcCompliant(); }
    /** {@inheritDoc} */
    @Override public Logger getParentLogger() { return Logger.getLogger("org.mariadb.jdbc"); }

    /** Immutable conversion settings captured when the physical connection is created. */
    private record ConversionPolicy(String zeroDates, boolean aliases, boolean datetimeAsTimestamp,
                                    boolean emptyStringsConvertToZero, boolean jdbcCompliantTruncation,
                                    TimeZone defaultZone, TimeZone connectionZone, boolean preserveInstants) { }

    /** Wraps one JDBC object while retaining its owning compatibility connection and statement. */
    private static Object wrap(Object target, Class<?> type, Connection connection, Statement statement,
                               ConversionPolicy policy) {
        return Proxy.newProxyInstance(MariaDbCompatibilityDriver.class.getClassLoader(), new Class<?>[]{type},
                new JdbcHandler(target, connection, statement, policy));
    }

    private static final class JdbcHandler implements InvocationHandler {
        private final Object target;
        private final Connection connection;
        private final Statement statement;
        private final ConversionPolicy policy;
        private Boolean convertedWasNull;

        /** Records the native object and its wrapped owners without allocating database resources. */
        private JdbcHandler(Object target, Connection connection, Statement statement, ConversionPolicy policy) {
            this.target = target;
            this.connection = connection;
            this.statement = statement;
            this.policy = policy;
        }

        /** Adapts result conversions and object ownership, delegating all remaining JDBC operations. */
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            String name = method.getName();
            Object special = objectOperation(proxy, name, args);
            if (special != NO_CONVERSION) return special;
            if (target instanceof Statement configured && name.equals("setFetchSize")
                    && Integer.valueOf(Integer.MIN_VALUE).equals(args[0])) {
                configured.setFetchSize(1); // MariaDB uses positive sizes for Connector/J's streaming sentinel.
                return null;
            }
            if (target instanceof ResultSet rows) {
                Object adapted = resultOperation(method, args, rows);
                if (adapted != NO_CONVERSION) return adapted;
            }
            if (target instanceof ResultSetMetaData metadata) {
                Object adapted = metadataOperation(metadata, name, args);
                if (adapted != NO_CONVERSION) return adapted;
            }
            Object result = invokeNative(method, args);
            // Native unwrap must satisfy the requested concrete type, not produce another proxy.
            return name.equals(UNWRAP) ? result : wrapResult(result, proxy);
        }

        /** Preserves proxy identity, JDBC wrapper queries, and the owning compatibility objects. */
        private Object objectOperation(Object proxy, String name, Object[] args) {
            if (name.equals("equals")) return proxy == args[0];
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("toString")) return "MariaDB JDBC " + target.getClass().getSimpleName();
            if ((name.equals(UNWRAP) || name.equals("isWrapperFor")) && ((Class<?>) args[0]).isInstance(proxy)) {
                if (name.equals(UNWRAP)) return proxy;
                return Boolean.TRUE;
            }
            if (name.equals("getConnection") && connection != null) return connection;
            if (name.equals("getStatement") && statement != null) return statement;
            return NO_CONVERSION;
        }

        /** Applies ResultSet contracts before delegating row navigation and unsupported conversions. */
        private Object resultOperation(Method method, Object[] args, ResultSet rows) throws SQLException {
            String name = method.getName();
            if (name.equals("wasNull") && convertedWasNull != null) return convertedWasNull;
            if (name.equals(GET_OBJECT) && args.length == 2) {
                if (method.getParameterTypes()[1] == Map.class) {
                    // Connector/J ignores SQL structured-type maps for scalar columns.
                    convertedWasNull = null;
                    Object converted = columnValue(rows, GET_OBJECT, new Object[]{args[0]});
                    return converted == NO_CONVERSION ? rows.getObject(columnIndex(rows, args)) : converted;
                }
                if (args[1] == null || args[1] == Object.class) {
                    throw new SQLException("Unsupported result conversion type", INVALID_CONVERSION_STATE);
                }
            }
            if (isColumnGetter(name, args)) {
                convertedWasNull = null;
                return columnValue(rows, name, args);
            }
            if (java.util.Set.of("next", "previous", "absolute", "relative").contains(name)) convertedWasNull = null;
            return NO_CONVERSION;
        }

        /** Identifies indexed or named column reads without treating metadata access as a value read. */
        private static boolean isColumnGetter(String name, Object[] args) {
            return name.startsWith("get") && args != null && args.length > 0
                    && (args[0] instanceof String || args[0] instanceof Integer);
        }

        /** Selects the narrow numeric, lexical, or temporal adaptation needed by a column getter. */
        private Object columnValue(ResultSet rows, String name, Object[] args) throws SQLException {
            Object bool = booleanValue(rows, name, args);
            if (bool != NO_CONVERSION) return bool;
            Object numeric = numericTextValue(rows, name, args);
            if (numeric != NO_CONVERSION) return numeric;
            Object legacyNumber = legacyNumberValue(rows, name, args);
            if (legacyNumber != NO_CONVERSION) return legacyNumber;
            Object text = textValue(rows, name, args);
            if (text != NO_CONVERSION) return text;
            if (java.util.Set.of("getDate", "getTimestamp", "getTime", GET_OBJECT).contains(name)) {
                return temporalValue(rows, name, args);
            }
            return NO_CONVERSION;
        }

        /**
         * Keeps textual temporal reads lexical rather than applying the native driver's timezone shift, and
         * renders floating-point and BIT values as Connector/J did rather than as the server's text.
         */
        private Object textValue(ResultSet rows, String name, Object[] args) throws SQLException {
            boolean stream = name.equals("getCharacterStream") || name.equals("getNCharacterStream");
            boolean typedString = name.equals(GET_OBJECT) && args.length == 2 && args[1] == String.class;
            if (!name.equals("getString") && !name.equals("getNString") && !stream && !typedString) return NO_CONVERSION;
            int column = columnIndex(rows, args);
            ResultSetMetaData metadata = rows.getMetaData();
            int sqlType = metadata.getColumnType(column);
            String text;
            if (isTemporalColumn(sqlType)) {
                text = temporalText(rows, column, metadata);
            } else if (!stream && (FLOATING_TYPES.contains(sqlType) || sqlType == Types.BIT)) {
                // Both drivers refuse character streams over numeric columns; only plain text reads change.
                text = legacyNumberText(rows, column, sqlType);
            } else {
                return NO_CONVERSION;
            }
            convertedWasNull = text == null;
            return stream && text != null ? new java.io.StringReader(text) : text;
        }

        /**
         * Connector/J printed a FLOAT, REAL or DOUBLE through its decoded double ("100.0", "1.0E7") and a BIT field
         * through its signed long ("1", "5"), where MariaDB returns the server's text ("100", "b'101'"). MariaDB's
         * own double and long decoders already match Connector/J's in both protocols (#5968).
         */
        private static String legacyNumberText(ResultSet rows, int column, int sqlType) throws SQLException {
            String text = sqlType == Types.BIT ? String.valueOf(rows.getLong(column)) : String.valueOf(rows.getDouble(column));
            return rows.wasNull() ? null : text;
        }

        /**
         * Retains the prior driver's non-text reads of the same columns (#5968): a floating column's BigDecimal
         * carries the decoded double's scale ("100.0", not "100"); BIT(1) reads as a Boolean object; and BIT
         * values convert to double, float and BigDecimal as unsigned numbers instead of being refused. The
         * scaled, deprecated getBigDecimal overload is left to MariaDB.
         */
        private Object legacyNumberValue(ResultSet rows, String method, Object[] args) throws SQLException {
            Class<?> requested = legacyNumberTarget(method, args);
            if (requested == null) return NO_CONVERSION;
            int column = columnIndex(rows, args);
            ResultSetMetaData metadata = rows.getMetaData();
            int sqlType = metadata.getColumnType(column);
            if (FLOATING_TYPES.contains(sqlType)) {
                return requested == java.math.BigDecimal.class ? floatingDecimal(rows, column) : NO_CONVERSION;
            }
            return sqlType == Types.BIT ? bitValue(rows, column, metadata.getPrecision(column), requested) : NO_CONVERSION;
        }

        /** Names the type a getter asks for when {@link #legacyNumberValue} may answer it, or null. */
        private static Class<?> legacyNumberTarget(String method, Object[] args) {
            return switch (method) {
                case GET_BIG_DECIMAL -> args.length == 1 ? java.math.BigDecimal.class : null;
                case "getDouble" -> double.class;
                case "getFloat" -> float.class;
                case GET_OBJECT -> objectTarget(args);
                default -> null;
            };
        }

        /** Returns Object for getObject(column) and the requested class for getObject(column, type). */
        private static Class<?> objectTarget(Object[] args) {
            if (args.length == 1) return Object.class;
            return args[1] instanceof Class<?> type ? type : null;
        }

        /** Reads a floating column's BigDecimal with the scale of its decoded double. */
        private java.math.BigDecimal floatingDecimal(ResultSet rows, int column) throws SQLException {
            double value = rows.getDouble(column);
            convertedWasNull = rows.wasNull();
            return convertedWasNull ? null : java.math.BigDecimal.valueOf(value);
        }

        /** Converts a BIT column (or TINYINT(1), which is reported as BIT) as Connector/J did. */
        private Object bitValue(ResultSet rows, int column, int precision, Class<?> requested) throws SQLException {
            boolean flag = requested == Object.class && precision == 1;
            boolean real = requested == double.class || requested == Double.class;
            boolean single = requested == float.class || requested == Float.class;
            boolean decimal = requested == java.math.BigDecimal.class;
            if (!flag && !real && !single && !decimal) return NO_CONVERSION;
            long value = rows.getLong(column);
            convertedWasNull = rows.wasNull();
            if (convertedWasNull) return nullValue(requested);
            // TINYINT(1) can hold negatives: Connector/J treats only positive values and -1 as true.
            if (flag) return value > 0 || value == -1;
            // Connector/J read BIT fields as unsigned for these conversions, though signed for text and getLong.
            // TINYINT(1) is also reported as BIT with precision 1 and keeps its sign; BIT(1) is only ever 0 or 1.
            java.math.BigDecimal number = precision > 1
                    ? new java.math.BigDecimal(Long.toUnsignedString(value)) : java.math.BigDecimal.valueOf(value);
            if (decimal) return number;
            return real ? (Object) number.doubleValue() : (Object) number.floatValue();
        }

        /** A primitive getter cannot return null; JDBC reports SQL NULL as zero plus wasNull(). */
        private static Object nullValue(Class<?> requested) {
            if (requested == double.class) return 0.0d;
            return requested == float.class ? (Object) 0.0f : null;
        }

        /** Returns the metadata aliases and Java class names exposed by the previous driver. */
        private Object metadataOperation(ResultSetMetaData metadata, String name, Object[] args) throws SQLException {
            if (policy.aliases() && name.equals("getColumnName")) return metadata.getColumnLabel((Integer) args[0]);
            if (policy.aliases() && name.equals("getTableName")) return tableAlias(metadata, (Integer) args[0]);
            if (!policy.datetimeAsTimestamp() && name.equals("getColumnClassName")
                    && MariaDbJdbcCompatibility.asciiEquals("DATETIME", metadata.getColumnTypeName((Integer) args[0]))) {
                return LocalDateTime.class.getName();
            }
            if (name.equals("getColumnClassName") && metadata.getColumnType((Integer) args[0]) == Types.BIT) {
                // Connector/J named Boolean for every BIT width, although only BIT(1) values are Boolean objects.
                return Boolean.class.getName();
            }
            return NO_CONVERSION;
        }

        /** Invokes JDBC directly and preserves its original checked or runtime failure. */
        private Object invokeNative(Method method, Object[] args) throws SQLException {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof SQLException sql) throw sql;
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new SQLException("JDBC method raised an unexpected checked exception", cause);
            } catch (IllegalAccessException inaccessible) {
                throw new SQLException("JDBC method could not be accessed", inaccessible);
            }
        }

        /** Carries the current compatibility connection and statement into derived JDBC objects. */
        private Object wrapResult(Object result, Object proxy) {
            Connection owner = target instanceof Connection ? (Connection) proxy : connection;
            Statement source = target instanceof Statement ? (Statement) proxy : statement;
            if (result instanceof CallableStatement) return wrap(result, CallableStatement.class, owner, null, policy);
            if (result instanceof PreparedStatement) return wrap(result, PreparedStatement.class, owner, null, policy);
            if (result instanceof Statement) return wrap(result, Statement.class, owner, null, policy);
            if (result instanceof ResultSet) return wrap(result, ResultSet.class, owner, source, policy);
            if (result instanceof ResultSetMetaData) return wrap(result, ResultSetMetaData.class, owner, source, policy);
            if (result instanceof DatabaseMetaData) return wrap(result, DatabaseMetaData.class, owner, null, policy);
            return result;
        }

        private static final Object NO_CONVERSION = new Object();
        private static final java.util.Set<Integer> TEXTUAL_TYPES = java.util.Set.of(Types.CHAR, Types.VARCHAR,
                Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB,
                Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB);
        private static final java.util.Set<Integer> BINARY_TYPES = java.util.Set.of(Types.BINARY, Types.VARBINARY,
                Types.LONGVARBINARY, Types.BLOB);
        private static final java.util.Set<Integer> FLOATING_TYPES = java.util.Set.of(Types.REAL, Types.FLOAT, Types.DOUBLE);
        private static final java.util.Set<Integer> NUMERIC_TYPES = java.util.Set.of(Types.TINYINT, Types.SMALLINT,
                Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL,
                Types.BOOLEAN, Types.BIT);
        private static final java.util.Set<Class<?>> TIME_TYPES = java.util.Set.of(java.sql.Time.class, LocalTime.class, OffsetTime.class);
        private static final java.util.Set<Class<?>> TEMPORAL_TYPES = java.util.Set.of(java.sql.Time.class, LocalTime.class,
                OffsetTime.class, java.sql.Date.class, java.sql.Timestamp.class, java.util.Date.class, Calendar.class,
                LocalDate.class, LocalDateTime.class, OffsetDateTime.class, ZonedDateTime.class);
        private static final java.time.format.DateTimeFormatter SQL_TIMESTAMP =
                java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

        /** Preserves Connector/J boolean tokens and its positive-or-negative-one numeric truth rule. */
        private Object booleanValue(ResultSet rows, String method, Object[] args) throws SQLException {
            boolean primitiveGetter = method.equals("getBoolean");
            boolean typedGetter = method.equals(GET_OBJECT) && args.length == 2
                    && (args[1] == Boolean.class || args[1] == boolean.class);
            if (!primitiveGetter && !typedGetter) return NO_CONVERSION;
            int column = columnIndex(rows, args);
            int sqlType = rows.getMetaData().getColumnType(column);
            Object result;
            if (TEXTUAL_TYPES.contains(sqlType)) {
                result = textBoolean(rows, column, sqlType);
            } else if (NUMERIC_TYPES.contains(sqlType)) {
                result = numericBoolean(rows, column, sqlType);
            } else {
                return NO_CONVERSION;
            }
            convertedWasNull = result == null;
            return primitiveGetter ? Boolean.TRUE.equals(result) : result;
        }

        /** Reads a nullable textual boolean without losing the underlying SQL NULL state. */
        private Object textBoolean(ResultSet rows, int column, int sqlType) throws SQLException {
            byte[] bytes = rows.getBytes(column);
            return bytes == null ? null : textBoolean(numericText(rows, column, sqlType, bytes));
        }

        /** Retains fractional truth values and the legacy special meaning of negative one. */
        private static Object numericBoolean(ResultSet rows, int column, int sqlType) throws SQLException {
            java.math.BigDecimal value = sqlType == Types.BIT
                    ? java.math.BigDecimal.valueOf(rows.getLong(column)) : rows.getBigDecimal(column);
            if (rows.wasNull()) return null;
            return value.signum() > 0 || value.compareTo(java.math.BigDecimal.ONE.negate()) == 0;
        }

        /** Parses only the textual truth values accepted by the prior driver, rejecting unrecognized values. */
        private boolean textBoolean(String text) throws SQLException {
            if (text.isEmpty() && policy.emptyStringsConvertToZero()) return false;
            Object named = namedBoolean(text);
            if (named != NO_CONVERSION) return (Boolean) named;
            try {
                if (text.contains("e") || text.contains("E") || text.matches("-?\\d*\\.\\d*")) {
                    double value = Double.parseDouble(text);
                    return value > 0 || value == -1.0d;
                }
                if (text.matches("-?\\d+")) {
                    if (text.startsWith("-")) return Long.parseLong(text) == -1;
                    return new java.math.BigInteger(text).signum() > 0;
                }
            } catch (NumberFormatException invalid) {
                throw withFailureLocation(new java.sql.SQLDataException(
                        "SQL string cannot be converted to a boolean", INVALID_NUMERIC_STATE), invalid);
            }
            throw new java.sql.SQLDataException("SQL string cannot be converted to a boolean", INVALID_NUMERIC_STATE);
        }

        /** Retains Connector/J's case-insensitive truth tokens, including the Unicode long-s equivalence. */
        private static Object namedBoolean(String text) {
            String folded = text.replace('\u017f', 's');
            for (String token : new String[]{"y", "yes", "t", TRUE_OPTION}) {
                if (MariaDbJdbcCompatibility.asciiEquals(folded, token)) return true;
            }
            for (String token : new String[]{"n", "no", "f", FALSE_OPTION}) {
                if (MariaDbJdbcCompatibility.asciiEquals(folded, token)) return false;
            }
            return NO_CONVERSION;
        }

        /** Decodes textual and binary values using the character encoding previously used for numeric conversions. */
        private static String numericText(ResultSet rows, int column, int sqlType, byte[] bytes) throws SQLException {
            return BINARY_TYPES.contains(sqlType)
                    ? new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1) : rows.getString(column);
        }

        /** Preserves empty-string numeric coercion and the old driver's single-byte textual conversion. */
        private Object numericTextValue(ResultSet rows, String method, Object[] args) throws SQLException {
            Class<?> requested = numericTarget(method, args);
            Object zero = numericZero(requested);
            if (zero == NO_CONVERSION) return NO_CONVERSION;
            int column = columnIndex(rows, args);
            int sqlType = rows.getMetaData().getColumnType(column);
            if (!TEXTUAL_TYPES.contains(sqlType)) return NO_CONVERSION;
            byte[] bytes = rows.getBytes(column);
            if (bytes == null) return NO_CONVERSION;
            boolean byteValue = requested == Byte.class || requested == byte.class;
            if (bytes.length > 0) {
                return byteValue ? textByte(rows, column, sqlType, bytes) : NO_CONVERSION;
            }
            if (!policy.emptyStringsConvertToZero()) {
                String state = byteValue ? "22003" : INVALID_NUMERIC_STATE;
                throw new java.sql.SQLDataException("Empty SQL string cannot be converted to a numeric value", state);
            }
            convertedWasNull = false;
            if (method.equals(GET_BIG_DECIMAL) && args.length == 2) {
                return java.math.BigDecimal.ZERO.setScale((Integer) args[1]);
            }
            return zero;
        }

        /** Identifies the requested numeric type without interpreting the column contents. */
        private static Class<?> numericTarget(String method, Object[] args) {
            return switch (method) {
                case "getBoolean" -> Boolean.class;
                case "getByte" -> Byte.class;
                case "getShort" -> Short.class;
                case "getInt" -> Integer.class;
                case "getLong" -> Long.class;
                case "getFloat" -> Float.class;
                case "getDouble" -> Double.class;
                case GET_BIG_DECIMAL -> java.math.BigDecimal.class;
                case GET_OBJECT -> args.length == 2 && args[1] instanceof Class<?> type ? type : null;
                default -> null;
            };
        }

        /** Supplies typed zero values only for conversions supported by the legacy empty-string policy. */
        private static Object numericZero(Class<?> requested) {
            if (requested == null) return NO_CONVERSION;
            return switch (requested.getName()) {
                case "boolean", "java.lang.Boolean" -> false;
                case "byte", "java.lang.Byte" -> (byte) 0;
                case "short", "java.lang.Short" -> (short) 0;
                case "int", "java.lang.Integer" -> 0;
                case "long", "java.lang.Long" -> 0L;
                case "float", "java.lang.Float" -> 0.0f;
                case "double", "java.lang.Double" -> 0.0d;
                case "java.math.BigDecimal" -> java.math.BigDecimal.ZERO;
                default -> NO_CONVERSION;
            };
        }

        /** Uses the prior driver's decoded first byte and honors its optional truncation check. */
        private Byte textByte(ResultSet rows, int column, int sqlType, byte[] bytes) throws SQLException {
            // Connector/J decodes binary fields as Latin-1 and then uses the JVM charset for Byte reads.
            String text = numericText(rows, column, sqlType, bytes);
            byte[] encoded = text.getBytes(java.nio.charset.Charset.defaultCharset());
            if (policy.jdbcCompliantTruncation() && encoded.length != 1) {
                throw new java.sql.SQLDataException("SQL string cannot be represented as a single byte", "22003");
            }
            convertedWasNull = false;
            return encoded[0];
        }

        /**
         * MariaDB exposes column aliases publicly but not table aliases. Read only
         * its pinned metadata array so buffered and streaming result sets preserve
         * the same alias policy; refuse an incompatible future driver layout.
         */
        private static String tableAlias(ResultSetMetaData metadata, int column) throws SQLException {
            metadata.getColumnLabel(column); // Preserve the driver's JDBC index validation.
            try {
                var nativeMetadata = metadata.unwrap(org.mariadb.jdbc.client.result.ResultSetMetaData.class);
                var fields = org.mariadb.jdbc.client.result.ResultSetMetaData.class.getDeclaredField("fieldPackets");
                if (!fields.trySetAccessible()) throw new SQLException("Database table alias metadata is unavailable", INVALID_CONVERSION_STATE);
                var columns = (org.mariadb.jdbc.client.ColumnDecoder[]) fields.get(nativeMetadata);
                return columns[column - 1].getTableAlias();
            } catch (ReflectiveOperationException | SecurityException | ClassCastException unsupported) {
                throw withFailureLocation(new SQLException(
                        "Database table alias metadata is unavailable", INVALID_CONVERSION_STATE), unsupported);
            }
        }

        /** Keeps temporal policies and rejects invalid fields before the native driver can silently coerce them. */
        private Object temporalValue(ResultSet rows, String method, Object[] args) throws SQLException {
            int column = columnIndex(rows, args);
            ResultSetMetaData metadata = rows.getMetaData();
            int sqlType = metadata.getColumnType(column);
            if (!isTemporalColumn(sqlType)) return NO_CONVERSION;
            Class<?> requested = temporalTarget(method, args, metadata, column);
            if (!TEMPORAL_TYPES.contains(requested)) return NO_CONVERSION;
            String value = temporalText(rows, column, metadata);
            boolean zero = isZeroTemporal(value);
            if (value == null || zero && policy.zeroDates().equals("CONVERT_TO_NULL")) {
                convertedWasNull = true;
                return null;
            }
            convertedWasNull = false;
            boolean timeOnly = TIME_TYPES.contains(requested);
            if (zero && policy.zeroDates().equals("EXCEPTION") && !timeOnly) throw invalidDate();
            try {
                return timeOnly ? timeValue(value, sqlType, zero, requested, args)
                        : dateValue(value, sqlType, zero, requested, args);
            } catch (java.time.DateTimeException | IllegalArgumentException invalid) {
                throw withFailureLocation(invalidDate(), invalid); // Clinical values never enter the failure message.
            }
        }

        /** Recognizes a complete zero date and optional zero time using a bounded linear scan. */
        private static boolean isZeroTemporal(String value) {
            if (value == null || !value.startsWith("0000-00-00")) return false;
            if (value.length() == 10) return true;
            if (value.length() < 19 || (value.charAt(10) != ' ' && value.charAt(10) != 'T')
                    || !value.startsWith("00:00:00", 11)) return false;
            if (value.length() == 19) return true;
            if (value.charAt(19) != '.' || value.length() == 20) return false;
            for (int index = 20; index < value.length(); index++) {
                if (value.charAt(index) != '0') return false;
            }
            return true;
        }

        /** Chooses the requested JDBC type, including Connector/J's ordinary DATETIME object policy. */
        private Class<?> temporalTarget(String method, Object[] args, ResultSetMetaData metadata, int column) throws SQLException {
            return switch (method) {
                case "getDate" -> java.sql.Date.class;
                case "getTimestamp" -> java.sql.Timestamp.class;
                case "getTime" -> java.sql.Time.class;
                default -> args.length == 2 ? (Class<?>) args[1] : ordinaryTemporalTarget(metadata, column);
            };
        }

        /** Uses LocalDateTime for DATETIME unless the operator requested the legacy Timestamp override. */
        private Class<?> ordinaryTemporalTarget(ResultSetMetaData metadata, int column) throws SQLException {
            if (metadata.getColumnType(column) == Types.DATE) return java.sql.Date.class;
            return !policy.datetimeAsTimestamp() && MariaDbJdbcCompatibility.asciiEquals("DATETIME", metadata.getColumnTypeName(column))
                    ? LocalDateTime.class : java.sql.Timestamp.class;
        }

        /** Discards date fields for time-only reads and preserves the historical standard-offset rule. */
        private Object timeValue(String value, int sqlType, boolean zero, Class<?> requested, Object[] args) {
            LocalTime time = sqlType == Types.DATE || zero ? LocalTime.MIDNIGHT : LocalTime.parse(value.substring(11));
            if (requested == LocalTime.class) return time;
            // Connector/J deliberately uses the standard JVM offset even for summer DATETIME values.
            if (requested == OffsetTime.class) return time.atOffset(java.time.ZoneOffset.ofTotalSeconds(policy.defaultZone().getRawOffset() / 1000));
            Calendar calendar = calendar(args, policy.defaultZone());
            calendar.set(1970, Calendar.JANUARY, 1, time.getHour(), time.getMinute(), time.getSecond());
            calendar.set(Calendar.MILLISECOND, time.getNano() / 1_000_000);
            return new java.sql.Time(calendar.getTimeInMillis());
        }

        /** Converts validated wall-clock fields to the requested local, offset, or calendar-based date type. */
        private Object dateValue(String value, int sqlType, boolean zero, Class<?> requested, Object[] args) {
            LocalDateTime dateTime = temporalFields(value, sqlType, zero);
            if (requested == LocalDate.class) return dateTime.toLocalDate();
            if (requested == LocalDateTime.class) return dateTime;
            TimeZone zone = sqlType == Types.DATE || requested == java.sql.Date.class || !policy.preserveInstants()
                    ? policy.defaultZone() : policy.connectionZone();
            if (requested == OffsetDateTime.class) return dateTime.atZone(zone.toZoneId()).toOffsetDateTime();
            if (requested == ZonedDateTime.class) return dateTime.atZone(zone.toZoneId());
            return calendarValue(dateTime, requested, args, zone);
        }

        /** Parses valid server fields or supplies the explicit year-one zero-date rounding value. */
        private static LocalDateTime temporalFields(String value, int sqlType, boolean zero) {
            if (zero) return LocalDate.of(1, Month.JANUARY, 1).atStartOfDay();
            return sqlType == Types.DATE ? LocalDate.parse(value).atStartOfDay() : LocalDateTime.parse(value.replace(' ', 'T'));
        }

        /** Matches non-lenient java.sql/util conversions, which reject year zero unlike java.time. */
        private static Object calendarValue(LocalDateTime dateTime, Class<?> requested, Object[] args, TimeZone zone) {
            Calendar calendar = calendar(args, zone);
            LocalDateTime selected = requested == java.sql.Date.class ? dateTime.toLocalDate().atStartOfDay() : dateTime;
            calendar.set(selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth(),
                    selected.getHour(), selected.getMinute(), selected.getSecond());
            calendar.set(Calendar.MILLISECOND, selected.getNano() / 1_000_000);
            long millis = calendar.getTimeInMillis();
            if (requested == java.sql.Date.class) return new java.sql.Date(millis);
            if (requested == Calendar.class) return calendar;
            if (requested == java.util.Date.class) return new java.util.Date(millis);
            java.sql.Timestamp timestamp = new java.sql.Timestamp(millis);
            timestamp.setNanos(selected.getNano());
            return timestamp;
        }

        /** Resolves the JDBC index while retaining native validation for named columns. */
        private static int columnIndex(ResultSet rows, Object[] args) throws SQLException {
            return args[0] instanceof Integer index ? index : rows.findColumn((String) args[0]);
        }

        /** Limits custom date parsing to actual DATE, DATETIME, and TIMESTAMP metadata. */
        private static boolean isTemporalColumn(int sqlType) {
            return sqlType == Types.DATE || sqlType == Types.TIMESTAMP;
        }

        /** MariaDB's String codec shifts timestamps; JDBC text must retain the server's wall-clock fields. */
        private static String temporalText(ResultSet rows, int column, ResultSetMetaData metadata) throws SQLException {
            if (metadata.getColumnType(column) == Types.DATE) {
                return dateText(rows, column);
            }
            try {
                LocalDateTime value = rows.getObject(column, LocalDateTime.class);
                if (value != null) return timestampText(value, metadata.getScale(column));
            } catch (java.sql.SQLDataException invalidDate) {
                // The LocalDateTime codec rejects partial-zero fields. Reading their
                // literal bytes lets the caller apply the legacy fail-closed policy;
                // connection/query failures must still propagate.
                if (!"22007".equals(invalidDate.getSQLState())) throw invalidDate;
            }
            String text = rows.getString(column);
            return text == null ? null : text.replaceFirst("\\.0+$", "");
        }

        /** Reads lexical DATE fields, recovering only the pinned driver's partial-zero binary conversion failure. */
        private static String dateText(ResultSet rows, int column) throws SQLException {
            try {
                return rows.getString(column);
            } catch (java.sql.SQLDataException invalidDate) {
                if (!"22007".equals(invalidDate.getSQLState())) throw invalidDate;
                return binaryDateText(rows);
            }
        }

        /** Formats server wall-clock fields at their declared fractional precision. */
        private static String timestampText(LocalDateTime value, int declaredScale) {
            String text = SQL_TIMESTAMP.format(value);
            int scale = Math.clamp(declaredScale, 0, 9);
            if (value.getNano() != 0 && scale > 0) {
                text += "." + String.format(Locale.ROOT, "%09d", value.getNano()).substring(0, scale);
            }
            return text;
        }

        /**
         * The pinned driver rejects a partial-zero binary DATE even for getString.
         * After that exact decoder failure its four date bytes have been consumed;
         * read them without modifying the row or relying on exception message text.
         */
        private static String binaryDateText(ResultSet rows) throws SQLException {
            try {
                var nativeRows = rows.unwrap(org.mariadb.jdbc.client.result.Result.class);
                Class<?> resultType = org.mariadb.jdbc.client.result.Result.class;
                var decoderField = resultType.getDeclaredField("rowDecoder");
                var lengthField = resultType.getDeclaredField("fieldLength");
                var bufferField = resultType.getDeclaredField("rowBuf");
                if (!decoderField.trySetAccessible() || !lengthField.trySetAccessible() || !bufferField.trySetAccessible()) {
                    throw invalidDate();
                }
                if (!(decoderField.get(nativeRows) instanceof org.mariadb.jdbc.client.result.rowdecoder.BinaryRowDecoder)
                        || ((org.mariadb.jdbc.client.util.MutableInt) lengthField.get(nativeRows)).get() != 4) {
                    throw invalidDate();
                }
                var buffer = (org.mariadb.jdbc.client.ReadableByteBuf) bufferField.get(nativeRows);
                int end = buffer.pos();
                byte[] bytes = buffer.buf();
                if (end < 4 || end > bytes.length) throw invalidDate();
                int year = (bytes[end - 4] & 0xff) | (bytes[end - 3] & 0xff) << 8;
                int month = bytes[end - 2] & 0xff;
                int day = bytes[end - 1] & 0xff;
                if (year > 9999 || month > 12 || day > 31) throw invalidDate();
                return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
            } catch (ReflectiveOperationException | SecurityException | ClassCastException incompatibleDriver) {
                throw withFailureLocation(invalidDate(), incompatibleDriver);
            }
        }

        /** Retain useful failure locations while excluding messages and causes that can contain clinical values. */
        private static <T extends SQLException> T withFailureLocation(T sanitized, Exception failure) {
            sanitized.setStackTrace(failure.getStackTrace());
            return sanitized;
        }

        /** Creates a private strict calendar, preserving an explicitly supplied getter calendar's timezone. */
        private static Calendar calendar(Object[] args, TimeZone zone) {
            Calendar calendar = args.length == 2 && args[1] instanceof Calendar supplied
                    ? (Calendar) supplied.clone() : Calendar.getInstance(zone, Locale.US);
            calendar.clear();
            calendar.setLenient(false);
            return calendar;
        }

        /** Reports an unrepresentable date without including clinical values in the error. */
        private static SQLException invalidDate() {
            return new SQLException("Date cannot be represented by the requested JDBC type", INVALID_CONVERSION_STATE);
        }
    }
}
