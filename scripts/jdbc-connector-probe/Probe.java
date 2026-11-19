/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;

/**
 * Differential JDBC probe: runs the same reads/writes/statements through one driver and prints
 * "section \t key \t outcome" lines. Run once per driver and diff the outputs.
 *
 * args: kind(cj|adapter|adapter-native|raw) protocol(text|binary) fetch(buffered|stream) outFile [sections,comma]
 */
public class Probe {
    static PrintWriter out;
    static String kind;
    static boolean binary;
    static boolean stream;
    static final String HOST = System.getProperty("probe.host", "127.0.0.1:33306");
    static final String DB = "carlos";
    static final String PACKAGED = "zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true&jdbcCompliantTruncation=false";

    public static void main(String[] a) throws Exception {
        kind = a[0];
        binary = a[1].equals("binary");
        stream = a[2].equals("stream");
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[3]), StandardCharsets.UTF_8), true);
        Set<String> sections = a.length > 4 ? new HashSet<>(Arrays.asList(a[4].split(","))) : null;
        Map<String, Section> all = new LinkedHashMap<>();
        all.put("SESSION", Probe::session);
        all.put("DBMETA", Probe::dbmeta);
        all.put("RSMETA", Probe::rsmeta);
        all.put("VALUES", Probe::values);
        all.put("EXPR", Probe::expr);
        all.put("LABELS", Probe::labels);
        all.put("WRITE", Probe::write);
        all.put("STMT", Probe::stmt);
        all.put("TIMEOUT", Probe::timeout);
        all.put("EXC", Probe::exc);
        all.put("PARSE", Probe::parse);
        all.put("DMLQ", Probe::dmlq);
        all.put("READONLY", Probe::readonly);
        all.put("FORMS", Probe::forms);
        for (var e : all.entrySet()) {
            if (sections != null && !sections.contains(e.getKey())) continue;
            try (Connection c = open("")) {
                e.getValue().run(c);
            } catch (Throwable t) {
                emit(e.getKey(), "SECTION-FAILED", ex(t) + " " + t.getMessage());
                t.printStackTrace();
            }
        }
        out.close();
    }

    interface Section { void run(Connection c) throws Exception; }
    interface Call { Object get() throws Exception; }

    static void emit(String section, String key, String value) {
        out.println(section + "\t" + key + "\t" + String.valueOf(value).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r"));
    }

    static Connection open(String extra) throws Exception {
        return open(extra, "probe", "probe");
    }

    static Connection open(String extra, String user, String password) throws Exception {
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", password);
        String zone = TimeZone.getDefault().getID();
        String opts = PACKAGED + "&useInformationSchema=false" + (binary ? "&useServerPrepStmts=true" : "") + System.getProperty("probe.extra", "") + extra;
        switch (kind) {
            case "cj": {
                Driver d = (Driver) Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
                return d.connect("jdbc:mysql://" + HOST + "/" + DB + "?" + opts + "&serverTimezone=" + zone, p);
            }
            case "adapter":
            case "adapter-native":
            case "raw": {
                String scheme = kind.equals("adapter-native") ? "jdbc:mariadb://" : "jdbc:mysql://";
                String url = scheme + HOST + "/" + DB + "?" + opts + "&connectionTimeZone=" + java.net.URLEncoder.encode(zone, StandardCharsets.UTF_8);
                Class<?> compat = Class.forName("io.github.carlos_emr.carlos.utility.MariaDbJdbcCompatibility");
                String normalized = (String) compat.getMethod("normalizeUrl", String.class).invoke(null, url);
                if (kind.equals("raw")) {
                    Object settings = compat.getMethod("settings", String.class, Properties.class).invoke(null, normalized, p);
                    String rawUrl = (String) settings.getClass().getMethod("url").invoke(settings);
                    Properties rawProps = (Properties) settings.getClass().getMethod("properties").invoke(settings);
                    rawProps.putIfAbsent("connectTimeout", "0");
                    Driver d = (Driver) Class.forName("org.mariadb.jdbc.Driver").getDeclaredConstructor().newInstance();
                    return d.connect(rawUrl, rawProps);
                }
                Driver d = (Driver) Class.forName("io.github.carlos_emr.carlos.utility.MariaDbCompatibilityDriver").getDeclaredConstructor().newInstance();
                return d.connect(normalized, p);
            }
            default:
                throw new IllegalArgumentException(kind);
        }
    }

    // ---------- rendering ----------
    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, 48); i++) s.append(String.format("%02X", b[i]));
        if (b.length > 48) s.append("..(").append(b.length).append(")");
        return s.toString();
    }

    static String clip(String s) { return s.length() > 80 ? s.substring(0, 80) + "..(" + s.length() + ")" : s; }

    static String render(Object v) throws Exception {
        if (v == null) return "null";
        if (v instanceof byte[] b) return "byte[]:" + hex(b);
        if (v instanceof Timestamp t) return "Timestamp:" + t + "@" + t.getTime();
        if (v instanceof java.sql.Date d) return "sql.Date:" + d + "@" + d.getTime();
        if (v instanceof Time t) return "Time:" + t + "@" + t.getTime();
        if (v instanceof Date d) return "util.Date:@" + d.getTime();
        if (v instanceof Reader r) { StringBuilder s = new StringBuilder(); int ch; while ((ch = r.read()) >= 0) s.append((char) ch); return "Reader:" + clip(s.toString()); }
        if (v instanceof InputStream s) return "InputStream:" + hex(s.readAllBytes());
        if (v instanceof Clob c) return "Clob:" + clip(c.getSubString(1, (int) c.length()));
        if (v instanceof Blob b) return "Blob:" + hex(b.getBytes(1, (int) b.length()));
        if (v instanceof String s) return "String:" + clip(s);
        return v.getClass().getName() + ":" + v;
    }

    static String ex(Throwable t) {
        if (t instanceof java.lang.reflect.InvocationTargetException i && i.getCause() != null) t = i.getCause();
        Class<?> c = t.getClass();
        while (c != null && !c.getName().startsWith("java.")) c = c.getSuperclass();
        String std = c == null ? t.getClass().getSimpleName() : c.getSimpleName();
        if (t instanceof SQLException s) return "EX:" + std + ":" + s.getSQLState() + ":" + s.getErrorCode();
        return "EX:" + std;
    }

    static String msg(Throwable t) {
        String m = String.valueOf(t.getMessage());
        return m.replaceAll("\\(conn=\\d+\\) ", "").replaceAll("\\s+", " ");
    }

    static String attempt(Call c) {
        try {
            return render(c.get());
        } catch (Throwable t) {
            return ex(t);
        }
    }

    static String attemptMsg(Call c) {
        try {
            return render(c.get());
        } catch (Throwable t) {
            return ex(t) + " | " + clip(msg(t));
        }
    }

    static Statement statement(Connection c) throws SQLException {
        Statement s = c.createStatement();
        if (stream) s.setFetchSize(Integer.MIN_VALUE);
        return s;
    }

    /** Text protocol uses a plain Statement; binary uses a (server) prepared statement. */
    static ResultSet query(Connection c, String sql) throws SQLException {
        if (binary) {
            PreparedStatement ps = c.prepareStatement(sql);
            if (stream) ps.setFetchSize(Integer.MIN_VALUE);
            return ps.executeQuery();
        }
        return statement(c).executeQuery(sql);
    }

    // ---------- sections ----------
    static void session(Connection c) throws Exception {
        String[] vars = {"@@session.sql_mode", "@@session.time_zone", "@@system_time_zone", "@@character_set_client", "@@character_set_connection",
                "@@character_set_results", "@@collation_connection", "@@autocommit", "@@tx_isolation", "@@net_write_timeout", "@@net_read_timeout",
                "@@wait_timeout", "@@interactive_timeout", "@@sql_select_limit", "@@max_statement_time", "@@session.sql_auto_is_null",
                "@@session.group_concat_max_len", "@@session.innodb_lock_wait_timeout", "@@session.tx_read_only", "@@session.sql_safe_updates",
                "@@session.max_allowed_packet", "@@session.net_buffer_length", "@@session.session_track_schema", "@@session.sql_big_selects",
                "DATABASE()", "CURRENT_USER()", "NOW() = UTC_TIMESTAMP()", "TIMEDIFF(NOW(), UTC_TIMESTAMP())"};
        for (String v : vars) {
            emit("SESSION", v, attempt(() -> { try (ResultSet r = c.createStatement().executeQuery("SELECT " + v)) { r.next(); return r.getString(1); } }));
        }
        emit("SESSION", "conn.getCatalog", attempt(c::getCatalog));
        emit("SESSION", "conn.getSchema", attempt(c::getSchema));
        emit("SESSION", "conn.getTransactionIsolation", attempt(c::getTransactionIsolation));
        emit("SESSION", "conn.isReadOnly", attempt(c::isReadOnly));
        emit("SESSION", "conn.getAutoCommit", attempt(c::getAutoCommit));
        emit("SESSION", "conn.getHoldability", attempt(c::getHoldability));
        emit("SESSION", "conn.getNetworkTimeout", attempt(c::getNetworkTimeout));
        emit("SESSION", "conn.isValid(2)", attempt(() -> c.isValid(2)));
        emit("SESSION", "conn.getClientInfo", attempt(() -> String.valueOf(c.getClientInfo())));
        emit("SESSION", "conn.nativeSQL({fn NOW()})", attempt(() -> c.nativeSQL("SELECT {fn NOW()}")));
        emit("SESSION", "setReadOnly(true) then @@tx_read_only", attempt(() -> { c.setReadOnly(true); try (ResultSet r = c.createStatement().executeQuery("SELECT @@session.tx_read_only")) { r.next(); return r.getString(1); } finally { c.setReadOnly(false); } }));
        emit("SESSION", "setCatalog(mysql) then DATABASE()", attempt(() -> { c.setCatalog("mysql"); try (ResultSet r = c.createStatement().executeQuery("SELECT DATABASE()")) { r.next(); return r.getString(1) + "/" + c.getCatalog(); } finally { c.setCatalog(DB); } }));
        emit("SESSION", "setSchema(mysql) then DATABASE()", attempt(() -> { c.setSchema("mysql"); try (ResultSet r = c.createStatement().executeQuery("SELECT DATABASE()")) { r.next(); return r.getString(1) + "/" + c.getSchema(); } finally { c.setCatalog(DB); } }));
        // process list view of this connection (connection attributes / host form)
        emit("SESSION", "SELECT USER()", attempt(() -> { try (ResultSet r = c.createStatement().executeQuery("SELECT SUBSTRING_INDEX(USER(),'@',1)")) { r.next(); return r.getString(1); } }));
    }

    static void dbmeta(Connection c) throws Exception {
        DatabaseMetaData m = c.getMetaData();
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("getDatabaseProductName", m::getDatabaseProductName);
        calls.put("getDatabaseProductVersion", m::getDatabaseProductVersion);
        calls.put("getDatabaseMajorVersion", m::getDatabaseMajorVersion);
        calls.put("getDatabaseMinorVersion", m::getDatabaseMinorVersion);
        calls.put("getDriverName", m::getDriverName);
        calls.put("getDriverVersion", m::getDriverVersion);
        calls.put("getJDBCMajorVersion", m::getJDBCMajorVersion);
        calls.put("getURL(no-query)", () -> { String u = m.getURL(); return u == null ? null : u.replaceAll("\\?.*", "?..."); });
        calls.put("getUserName", m::getUserName);
        calls.put("getIdentifierQuoteString", m::getIdentifierQuoteString);
        calls.put("getCatalogTerm", m::getCatalogTerm);
        calls.put("getSchemaTerm", m::getSchemaTerm);
        calls.put("getCatalogSeparator", m::getCatalogSeparator);
        calls.put("getSearchStringEscape", m::getSearchStringEscape);
        calls.put("getExtraNameCharacters", m::getExtraNameCharacters);
        calls.put("storesLowerCaseIdentifiers", m::storesLowerCaseIdentifiers);
        calls.put("storesMixedCaseIdentifiers", m::storesMixedCaseIdentifiers);
        calls.put("supportsMixedCaseIdentifiers", m::supportsMixedCaseIdentifiers);
        calls.put("supportsBatchUpdates", m::supportsBatchUpdates);
        calls.put("supportsGetGeneratedKeys", m::supportsGetGeneratedKeys);
        calls.put("supportsSavepoints", m::supportsSavepoints);
        calls.put("supportsStoredProcedures", m::supportsStoredProcedures);
        calls.put("supportsMultipleResultSets", m::supportsMultipleResultSets);
        calls.put("supportsMultipleOpenResults", m::supportsMultipleOpenResults);
        calls.put("supportsCatalogsInTableDefinitions", m::supportsCatalogsInTableDefinitions);
        calls.put("supportsSchemasInTableDefinitions", m::supportsSchemasInTableDefinitions);
        calls.put("supportsSelectForUpdate", m::supportsSelectForUpdate);
        calls.put("supportsTransactionIsolationLevel(RR)", () -> m.supportsTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ));
        calls.put("supportsResultSetType(SCROLL_SENSITIVE)", () -> m.supportsResultSetType(ResultSet.TYPE_SCROLL_SENSITIVE));
        calls.put("supportsResultSetConcurrency(FWD,UPDATABLE)", () -> m.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
        calls.put("getDefaultTransactionIsolation", m::getDefaultTransactionIsolation);
        calls.put("getMaxColumnNameLength", m::getMaxColumnNameLength);
        calls.put("getSQLStateType", m::getSQLStateType);
        calls.put("nullsAreSortedLow", m::nullsAreSortedLow);
        calls.put("getResultSetHoldability", m::getResultSetHoldability);
        calls.put("locatorsUpdateCopy", m::locatorsUpdateCopy);
        calls.put("getMaxStatementLength", m::getMaxStatementLength);
        calls.put("getConnection==conn", () -> m.getConnection() == c);
        for (var e : calls.entrySet()) emit("DBMETA", e.getKey(), attempt(e.getValue()));

        // catalog/schema argument semantics for table lookups
        String[][] tableArgs = {{null, null}, {DB, null}, {"", null}, {null, DB}, {DB, DB}, {null, ""}, {"mysql", null}, {"nosuchdb", null}};
        for (String[] t : tableArgs) {
            emit("DBMETA", "getTables(" + t[0] + "," + t[1] + ",zz_probe)", attempt(() -> rows(m.getTables(t[0], t[1], "zz_probe", null), "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE")));
            emit("DBMETA", "getTables(" + t[0] + "," + t[1] + ",user)", attempt(() -> rows(m.getTables(t[0], t[1], "user", null), "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE")));
        }
        emit("DBMETA", "getTables(null,null,ZZ_PROBE)", attempt(() -> rows(m.getTables(null, null, "ZZ_PROBE", null), "TABLE_NAME")));
        emit("DBMETA", "getTables(null,null,zz\\_pro%,[TABLE])", attempt(() -> rows(m.getTables(null, null, "zz\\_pro%", new String[]{"TABLE"}), "TABLE_NAME", "TABLE_TYPE")));
        emit("DBMETA", "getTables columns", attempt(() -> labels(m.getTables(null, null, "zz_probe", null))));
        emit("DBMETA", "getColumns columns", attempt(() -> labels(m.getColumns(null, null, "zz_probe", "%"))));
        try (ResultSet r = m.getColumns(null, null, "zz_probe", "%")) {
            while (r.next()) {
                emit("DBMETA", "getColumns:" + r.getString("COLUMN_NAME"), "DATA_TYPE=" + r.getInt("DATA_TYPE") + " TYPE_NAME=" + r.getString("TYPE_NAME")
                        + " COLUMN_SIZE=" + r.getString("COLUMN_SIZE") + " DECIMAL_DIGITS=" + r.getString("DECIMAL_DIGITS") + " NULLABLE=" + r.getInt("NULLABLE")
                        + " IS_NULLABLE=" + r.getString("IS_NULLABLE") + " IS_AUTOINCREMENT=" + r.getString("IS_AUTOINCREMENT") + " COLUMN_DEF=" + r.getString("COLUMN_DEF")
                        + " CHAR_OCTET_LENGTH=" + r.getString("CHAR_OCTET_LENGTH") + " TABLE_CAT=" + r.getString("TABLE_CAT") + " TABLE_SCHEM=" + r.getString("TABLE_SCHEM"));
            }
        } catch (Throwable t) { emit("DBMETA", "getColumns", ex(t)); }
        emit("DBMETA", "getPrimaryKeys(null,null,zz_probe)", attempt(() -> rows(m.getPrimaryKeys(null, null, "zz_probe"), "TABLE_CAT", "TABLE_SCHEM", "COLUMN_NAME", "KEY_SEQ", "PK_NAME")));
        emit("DBMETA", "getIndexInfo(zz_write)", attempt(() -> rows(m.getIndexInfo(null, null, "zz_write", false, false), "TABLE_CAT", "INDEX_NAME", "NON_UNIQUE", "COLUMN_NAME", "TYPE")));
        emit("DBMETA", "getImportedKeys(zz_child)", attempt(() -> rows(m.getImportedKeys(null, null, "zz_child"), "PKTABLE_CAT", "PKTABLE_NAME", "PKCOLUMN_NAME", "FKCOLUMN_NAME", "FK_NAME", "UPDATE_RULE", "DELETE_RULE")));
        emit("DBMETA", "getCatalogs", attempt(() -> rows(m.getCatalogs(), "TABLE_CAT")));
        emit("DBMETA", "getSchemas", attempt(() -> rows(m.getSchemas(), "TABLE_SCHEM")));
        emit("DBMETA", "getProcedures(zz_proc)", attempt(() -> rows(m.getProcedures(null, null, "zz_proc"), "PROCEDURE_CAT", "PROCEDURE_NAME", "PROCEDURE_TYPE")));
        emit("DBMETA", "getTableTypes", attempt(() -> rows(m.getTableTypes(), "TABLE_TYPE")));
    }

    static String labels(ResultSet r) throws SQLException {
        try (r) {
            ResultSetMetaData md = r.getMetaData();
            StringBuilder s = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) s.append(md.getColumnLabel(i)).append(',');
            return s.toString();
        }
    }

    static String rows(ResultSet r, String... cols) throws SQLException {
        try (r) {
            List<String> lines = new ArrayList<>();
            while (r.next() && lines.size() < 30) {
                StringBuilder s = new StringBuilder();
                for (String col : cols) s.append(col).append('=').append(r.getString(col)).append(' ');
                lines.add(s.toString().trim());
            }
            Collections.sort(lines);
            return lines.size() + " rows: " + lines;
        }
    }

    static void metaLine(String section, String key, ResultSetMetaData md, int i) {
        StringBuilder s = new StringBuilder();
        Map<String, Call> f = new LinkedHashMap<>();
        f.put("type", () -> md.getColumnType(i));
        f.put("typeName", () -> md.getColumnTypeName(i));
        f.put("class", () -> md.getColumnClassName(i));
        f.put("precision", () -> md.getPrecision(i));
        f.put("scale", () -> md.getScale(i));
        f.put("displaySize", () -> md.getColumnDisplaySize(i));
        f.put("signed", () -> md.isSigned(i));
        f.put("nullable", () -> md.isNullable(i));
        f.put("autoInc", () -> md.isAutoIncrement(i));
        f.put("caseSens", () -> md.isCaseSensitive(i));
        f.put("searchable", () -> md.isSearchable(i));
        f.put("currency", () -> md.isCurrency(i));
        f.put("readOnly", () -> md.isReadOnly(i));
        f.put("writable", () -> md.isWritable(i));
        f.put("label", () -> md.getColumnLabel(i));
        f.put("name", () -> md.getColumnName(i));
        f.put("table", () -> md.getTableName(i));
        f.put("catalog", () -> md.getCatalogName(i));
        f.put("schema", () -> md.getSchemaName(i));
        for (var e : f.entrySet()) {
            String v;
            try { v = String.valueOf(e.getValue().get()); } catch (Throwable t) { v = ex(t); }
            emit(section, key + "#" + e.getKey(), v);
        }
    }

    /** Result metadata for every column of every real CARLOS table. */
    static void rsmeta(Connection c) throws Exception {
        List<String> tables = new ArrayList<>();
        try (ResultSet r = c.createStatement().executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='" + DB + "' AND table_type='BASE TABLE' ORDER BY table_name")) {
            while (r.next()) tables.add(r.getString(1));
        }
        Map<String, String> columnTypes = new HashMap<>();
        try (ResultSet r = c.createStatement().executeQuery("SELECT table_name, column_name, column_type FROM information_schema.columns WHERE table_schema='" + DB + "'")) {
            while (r.next()) columnTypes.put(r.getString(1) + "." + r.getString(2), r.getString(3));
        }
        for (String t : tables) {
            try (ResultSet r = query(c, "SELECT * FROM `" + t + "` LIMIT 0")) {
                ResultSetMetaData md = r.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String col = md.getColumnLabel(i);
                    metaLine("RSMETA", t + "." + col + "[" + columnTypes.get(t + "." + col) + "]", md, i);
                }
            } catch (Throwable e) {
                emit("RSMETA", t, ex(e));
            }
        }
    }

    static Map<String, java.util.function.BiFunction<ResultSet, Integer, Call>> getters() {
        Map<String, java.util.function.BiFunction<ResultSet, Integer, Call>> g = new LinkedHashMap<>();
        g.put("getString", (r, i) -> () -> r.getString(i));
        g.put("getObject", (r, i) -> () -> r.getObject(i));
        g.put("getBoolean", (r, i) -> () -> r.getBoolean(i));
        g.put("getByte", (r, i) -> () -> r.getByte(i));
        g.put("getShort", (r, i) -> () -> r.getShort(i));
        g.put("getInt", (r, i) -> () -> r.getInt(i));
        g.put("getLong", (r, i) -> () -> r.getLong(i));
        g.put("getFloat", (r, i) -> () -> r.getFloat(i));
        g.put("getDouble", (r, i) -> () -> r.getDouble(i));
        g.put("getBigDecimal", (r, i) -> () -> r.getBigDecimal(i));
        g.put("getBytes", (r, i) -> () -> r.getBytes(i));
        g.put("getDate", (r, i) -> () -> r.getDate(i));
        g.put("getTime", (r, i) -> () -> r.getTime(i));
        g.put("getTimestamp", (r, i) -> () -> r.getTimestamp(i));
        g.put("getCharacterStream", (r, i) -> () -> r.getCharacterStream(i));
        g.put("getBinaryStream", (r, i) -> () -> r.getBinaryStream(i));
        g.put("getAsciiStream", (r, i) -> () -> r.getAsciiStream(i));
        g.put("getClob", (r, i) -> () -> r.getClob(i));
        g.put("getBlob", (r, i) -> () -> r.getBlob(i));
        g.put("getNString", (r, i) -> () -> r.getNString(i));
        Class<?>[] typed = {String.class, Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class, BigDecimal.class, BigInteger.class,
                Boolean.class, byte[].class, java.sql.Date.class, Time.class, Timestamp.class, Date.class, LocalDate.class, LocalDateTime.class,
                LocalTime.class, OffsetDateTime.class, ZonedDateTime.class, Instant.class, Character.class};
        for (Class<?> t : typed) g.put("getObject(" + t.getSimpleName() + ")", (r, i) -> () -> r.getObject(i, t));
        return g;
    }

    static void readAll(String section, Connection c, String sql, boolean meta) throws Exception {
        var getters = getters();
        try (ResultSet r = query(c, sql)) {
            ResultSetMetaData md = r.getMetaData();
            int n = md.getColumnCount();
            if (meta) for (int i = 2; i <= n; i++) metaLine(section + "-META", md.getColumnLabel(i), md, i);
            while (r.next()) {
                String id = r.getString(1);
                for (int i = 2; i <= n; i++) {
                    String raw;
                    final int col = i;
                    for (var g : getters.entrySet()) {
                        String v = attempt(g.getValue().apply(r, col));
                        String wasNull;
                        try { wasNull = r.wasNull() ? " wasNull" : ""; } catch (Throwable t) { wasNull = " wasNull=" + ex(t); }
                        emit(section, md.getColumnLabel(i) + "@" + id + "#" + g.getKey(), v + wasNull);
                    }
                }
            }
        }
    }

    static void values(Connection c) throws Exception {
        readAll("VALUES", c, "SELECT * FROM zz_probe ORDER BY id", true);
    }

    static void expr(Connection c) throws Exception {
        String[] exprs = {
                "COUNT(*)", "SUM(c_int)", "SUM(c_tinyint1)", "AVG(c_int)", "MAX(c_tinyint1)", "MIN(c_tinyint1)", "MAX(c_date)", "MIN(c_datetime)", "MAX(c_smallint)",
                "SUM(c_double)", "SUM(c_dec102)", "AVG(c_dec102)", "COUNT(DISTINCT c_varchar)", "GROUP_CONCAT(c_char1)", "MAX(c_bit1)", "SUM(c_bit1)", "MAX(c_time)",
                "MAX(c_float)", "SUM(c_float)", "BIT_OR(c_int)", "STD(c_int)", "MAX(c_varchar)", "MAX(c_text)", "MAX(c_blob)"};
        StringBuilder agg = new StringBuilder("SELECT 'agg'");
        for (int i = 0; i < exprs.length; i++) agg.append(", ").append(exprs[i]).append(" AS `").append(exprs[i]).append('`');
        agg.append(" FROM zz_probe WHERE id IN (1,4,7)");
        readAll("EXPR", c, agg.toString(), true);
        String[] scalar = {
                "c_int = 1", "c_varchar IS NULL", "c_int > 0 AND c_int < 9", "NOT c_tinyint1", "EXISTS(SELECT 1)", "TRUE", "FALSE", "1", "1.5", "1e0", "-0.0e0", "NULL", "'literal'",
                "b'1'", "0x41", "_binary 'x'", "IF(c_tinyint1=1,'Y','N')", "IF(c_int=5,1,0)", "IFNULL(c_tinyint1,0)", "COALESCE(c_date,'x')", "COALESCE(c_int,0)",
                "CASE WHEN c_int>0 THEN 1 ELSE 0 END", "CASE WHEN c_int>0 THEN 'a' ELSE 'b' END", "c_int/2", "c_int DIV 2", "c_int*1.5", "c_int+c_double", "c_int+0", "c_tinyint1+0",
                "c_tinyint1*1", "-c_tinyint1", "c_double+0", "c_float+0", "c_dec102*2", "c_varchar+0", "CONCAT(c_varchar,'x')", "CONCAT(c_int,'')", "CONCAT(c_date,'')",
                "CONCAT(c_double,'')", "CONCAT(c_blob,'x')", "CAST(c_int AS CHAR)", "CAST(c_varchar AS UNSIGNED)", "CAST(c_varchar AS SIGNED)", "CAST(c_varchar AS DECIMAL(10,2))",
                "CAST('2020-01-01' AS DATE)", "CAST(c_datetime AS DATE)", "CAST(c_date AS DATETIME)", "CAST(c_datetime AS TIME)", "CAST(c_int AS DOUBLE)", "CAST(c_blob AS CHAR)",
                "CONVERT(c_blob USING utf8mb4)", "CAST(c_varchar AS BINARY)", "HEX(c_blob)", "LENGTH(c_text)", "CHAR_LENGTH(c_text)", "ROUND(c_double,2)", "ROUND(c_double)",
                "TRUNCATE(c_double,1)", "FORMAT(c_double,2)", "FLOOR(c_double)", "CEIL(c_dec102)", "ABS(c_int)", "LPAD(c_int,5,'0')", "NOW()", "CURDATE()", "CURTIME()",
                "CURRENT_TIMESTAMP(6)", "UTC_TIMESTAMP()", "SYSDATE()", "DATE(c_datetime)", "TIME(c_datetime)", "YEAR(c_date)", "MONTH(c_date)", "DAYNAME(c_date)",
                "DATE_FORMAT(c_datetime,'%Y-%m-%d')", "DATE_FORMAT(c_date,'%Y%m%d')", "STR_TO_DATE('2020-01-15','%Y-%m-%d')", "STR_TO_DATE('2020-01-15 10:11:12','%Y-%m-%d %H:%i:%s')",
                "UNIX_TIMESTAMP(c_datetime)", "FROM_UNIXTIME(1579083072)", "DATEDIFF(c_date,'2020-01-01')", "TIMESTAMPDIFF(YEAR,c_date,'2026-01-01')", "TO_DAYS(c_date)",
                "DATE_ADD(c_date, INTERVAL 1 DAY)", "DATE_ADD(c_date, INTERVAL 1 HOUR)", "DATE_ADD(c_datetime, INTERVAL 1 DAY)", "DATE_ADD('2020-01-15', INTERVAL 1 DAY)",
                "c_date + INTERVAL 1 MONTH", "ADDTIME(c_time,'01:00:00')", "TIMEDIFF(c_datetime,'2020-01-01 00:00:00')", "SEC_TO_TIME(3600)", "TIME_TO_SEC(c_time)", "TIMESTAMP(c_date)",
                "LAST_DAY(c_date)", "MAKEDATE(2020,15)", "c_date + 0", "c_datetime + 0", "c_time + 0", "LAST_INSERT_ID()", "@nosuchvar", "(SELECT 1)", "(SELECT c_tinyint1 FROM zz_probe WHERE id=1)",
                "(SELECT MAX(id) FROM zz_probe)", "UUID() IS NOT NULL", "MD5('a')", "SHA2('a',256)", "UNHEX('41')", "LEFT(c_text,3)", "SUBSTRING(c_blob,1,3)", "UPPER(c_varchar)",
                "TRIM(c_char10)", "REPLACE(c_text,'a','b')", "c_varchar COLLATE utf8mb4_bin", "c_enum+0", "CONCAT(c_enum)", "c_set+0", "BIN(c_bit8)", "c_bit8+0", "c_bit1+0",
                "c_bit1 = b'1'", "CAST(c_bit1 AS UNSIGNED)", "JSON_EXTRACT(c_json,'$.a')", "JSON_OBJECT('a',1)", "INET_ATON('1.2.3.4')", "RAND() < 2", "PI()", "POW(2,10)", "10 % 3",
                "'12' + 1", "'a' = 'A'", "c_year + 0", "CONCAT(c_year,'')", "LEAST(c_int,1)", "GREATEST(c_date,'2000-01-01')", "NULLIF(c_int,5)", "ISNULL(c_int)", "INTERVAL(1,2,3)",
                "c_int IN (1,5)", "c_varchar LIKE 'h%'", "c_varchar REGEXP '^h'", "STRCMP('a','b')", "SIGN(c_int)", "c_intu + 0", "c_bigintu + 0", "CONVERT(c_int, CHAR)",
                "DATE_SUB(NOW(), INTERVAL 1 DAY) > '2000-01-01'", "CAST(NULL AS DATE)", "CAST(c_double AS CHAR)", "CAST(c_float AS CHAR)", "CAST(c_double AS DECIMAL(20,5))"};
        StringBuilder sql = new StringBuilder("SELECT id");
        for (int i = 0; i < scalar.length; i++) sql.append(", ").append(scalar[i]).append(" AS `").append(scalar[i].replace("`", "")).append('`');
        sql.append(" FROM zz_probe WHERE id IN (1,2,3) ORDER BY id");
        readAll("EXPR", c, sql.toString(), true);
    }

    static void labels(Connection c) throws Exception {
        String sql = "SELECT p.id, p.c_varchar AS vc, p.c_int, q.c_int, p.c_date AS `When Created`, p.c_text txt, COUNT(*) AS cnt, p.c_char1 AS `C_CHAR1`, q.id AS qid "
                + "FROM zz_probe p JOIN zz_probe q ON q.id = p.id WHERE p.id = 1 GROUP BY p.id";
        try (ResultSet r = query(c, sql)) {
            ResultSetMetaData md = r.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) metaLine("LABELS-META", "col" + i, md, i);
            r.next();
            String[] names = {"id", "ID", "p.id", "q.id", "zz_probe.id", "vc", "VC", "c_varchar", "p.c_varchar", "p.vc", "zz_probe.c_varchar", "zz_probe.vc", "c_int",
                    "p.c_int", "q.c_int", "When Created", "when created", "c_date", "txt", "c_text", "cnt", "COUNT(*)", "c_char1", "C_CHAR1", "qid", "nosuch", "", " id", "`id`"};
            for (String n : names) {
                emit("LABELS", "findColumn(" + n + ")", attempt(() -> r.findColumn(n)));
                emit("LABELS", "getString(" + n + ")", attempt(() -> r.getString(n)));
            }
        }
        // generic scroll / position behaviour on a default (forward-only, read-only) result
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id, c_varchar FROM zz_probe ORDER BY id")) {
            emit("LABELS", "default rs.getType", attempt(r::getType));
            emit("LABELS", "default rs.getConcurrency", attempt(r::getConcurrency));
            emit("LABELS", "default rs.getFetchSize", attempt(r::getFetchSize));
            emit("LABELS", "getString before next()", attempt(() -> r.getString(1)));
            emit("LABELS", "isBeforeFirst", attempt(r::isBeforeFirst));
            emit("LABELS", "last()", attempt(r::last));
            emit("LABELS", "getRow after last()", attempt(r::getRow));
            emit("LABELS", "isLast", attempt(r::isLast));
            emit("LABELS", "beforeFirst()", attempt(() -> { r.beforeFirst(); return "ok"; }));
            emit("LABELS", "next after beforeFirst", attempt(r::next));
            emit("LABELS", "absolute(3)", attempt(() -> r.absolute(3)));
            emit("LABELS", "getRow after absolute", attempt(r::getRow));
            emit("LABELS", "previous()", attempt(r::previous));
            emit("LABELS", "first()", attempt(r::first));
            emit("LABELS", "relative(2)", attempt(() -> r.relative(2)));
            emit("LABELS", "afterLast()", attempt(() -> { r.afterLast(); return "ok"; }));
            emit("LABELS", "getString after end", attempt(() -> r.getString(1)));
            emit("LABELS", "isAfterLast", attempt(r::isAfterLast));
            emit("LABELS", "getStatement==s", attempt(() -> r.getStatement() == s));
        }
        try (Statement s = c.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE); ResultSet r = s.executeQuery("SELECT id, v, s FROM zz_lock ORDER BY id")) {
            emit("LABELS", "updatable rs.getConcurrency", attempt(r::getConcurrency));
            emit("LABELS", "updatable rs.getType", attempt(r::getType));
            emit("LABELS", "updatable updateRow", attempt(() -> { r.next(); r.updateInt("v", 0); r.updateRow(); return "ok"; }));
            emit("LABELS", "updatable insertRow+deleteRow", attempt(() -> { r.moveToInsertRow(); r.updateInt(1, 99); r.updateInt(2, 1); r.updateString(3, "z"); r.insertRow(); r.moveToCurrentRow(); r.last(); int id = r.getInt(1); r.deleteRow(); return id; }));
        }
        c.createStatement().executeUpdate("DELETE FROM zz_lock WHERE id = 99");
        // getters on closed result / closed statement
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT 1");
        r.next();
        r.close();
        emit("LABELS", "getString on closed rs", attempt(() -> r.getString(1)));
        emit("LABELS", "rs.isClosed", attempt(r::isClosed));
        s.close();
        emit("LABELS", "executeQuery on closed stmt", attempt(() -> s.executeQuery("SELECT 1")));
        emit("LABELS", "second executeQuery closes first rs", attempt(() -> { Statement t = c.createStatement(); ResultSet a = t.executeQuery("SELECT 1"); ResultSet b = t.executeQuery("SELECT 2"); return a.isClosed(); }));
        emit("LABELS", "rs usable after stmt.close", attempt(() -> { Statement t = c.createStatement(); ResultSet a = t.executeQuery("SELECT 1"); t.close(); return a.isClosed(); }));
        emit("LABELS", "executeQuery(UPDATE)", attemptMsg(() -> c.createStatement().executeQuery("UPDATE zz_lock SET v = v WHERE id = 1")));
        emit("LABELS", "executeUpdate(SELECT)", attemptMsg(() -> c.createStatement().executeUpdate("SELECT 1")));
        emit("LABELS", "execute(SELECT) returns", attempt(() -> c.createStatement().execute("SELECT 1")));
        emit("LABELS", "prepared executeQuery(INSERT..)", attemptMsg(() -> c.prepareStatement("DELETE FROM zz_lock WHERE id = 12345").executeQuery()));
        emit("LABELS", "prepared executeUpdate(SELECT)", attemptMsg(() -> c.prepareStatement("SELECT 1").executeUpdate()));
        emit("LABELS", "getUpdateCount after SELECT execute", attempt(() -> { Statement t = c.createStatement(); t.execute("SELECT 1"); return t.getUpdateCount(); }));
        emit("LABELS", "getResultSet after UPDATE execute", attempt(() -> { Statement t = c.createStatement(); t.execute("UPDATE zz_lock SET v = v WHERE id = 1"); return t.getResultSet(); }));
        emit("LABELS", "getMoreResults after single", attempt(() -> { Statement t = c.createStatement(); t.execute("SELECT 1"); return t.getMoreResults() + "/" + t.getUpdateCount(); }));
        emit("LABELS", "CALL via Statement.execute", attempt(() -> { Statement t = c.createStatement(); boolean b = t.execute("CALL zz_proc()"); return b + "/" + t.getMoreResults() + "/" + t.getUpdateCount(); }));
        emit("LABELS", "CALL via prepareCall", attempt(() -> { CallableStatement t = c.prepareCall("{call zz_proc()}"); boolean b = t.execute(); ResultSet x = t.getResultSet(); x.next(); return b + "/" + x.getInt(1); }));
        emit("LABELS", "multi-statement", attemptMsg(() -> c.createStatement().execute("SELECT 1; SELECT 2")));
    }

    // ---------- write side ----------
    static void write(Connection c) throws Exception {
        c.createStatement().executeUpdate("DELETE FROM zz_write");
        ZoneId tor = ZoneId.of("America/Toronto");
        long epoch = ZonedDateTime.of(2020, 7, 15, 10, 11, 12, 345_000_000, tor).toInstant().toEpochMilli(); // summer
        long epochW = ZonedDateTime.of(2020, 1, 15, 23, 30, 0, 0, tor).toInstant().toEpochMilli(); // winter, late evening
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Calendar van = Calendar.getInstance(TimeZone.getTimeZone("America/Vancouver"));
        Map<String, Object> objects = new LinkedHashMap<>();
        objects.put("util.Date", new Date(epoch));
        objects.put("sql.Date", new java.sql.Date(epochW));
        objects.put("sql.Time", new Time(epochW));
        objects.put("sql.Timestamp", new Timestamp(epoch));
        objects.put("Calendar", GregorianCalendar.from(ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch), tor)));
        objects.put("LocalDate", LocalDate.of(2020, 1, 15));
        objects.put("LocalDateTime", LocalDateTime.of(2020, 1, 15, 23, 30, 0, 123_000_000));
        objects.put("LocalTime", LocalTime.of(23, 30, 5));
        objects.put("Instant", Instant.ofEpochMilli(epoch));
        objects.put("OffsetDateTime", OffsetDateTime.of(2020, 1, 15, 23, 30, 0, 0, ZoneOffset.ofHours(2)));
        objects.put("ZonedDateTime", ZonedDateTime.of(2020, 1, 15, 23, 30, 0, 0, ZoneId.of("America/Vancouver")));
        objects.put("OffsetTime", OffsetTime.of(23, 30, 0, 0, ZoneOffset.ofHours(2)));
        objects.put("Boolean.TRUE", Boolean.TRUE);
        objects.put("Boolean.FALSE", Boolean.FALSE);
        objects.put("Character", 'Y');
        objects.put("Integer", 42);
        objects.put("Long", 42L);
        objects.put("Short", (short) 42);
        objects.put("Byte", (byte) 42);
        objects.put("BigInteger", new BigInteger("12345678901234567890"));
        objects.put("BigDecimal", new BigDecimal("12.50"));
        objects.put("Double", 12.5d);
        objects.put("Double.int", 100d);
        objects.put("Float", 1.1f);
        objects.put("Double.NaN", Double.NaN);
        objects.put("byte[]", "bytes".getBytes(StandardCharsets.UTF_8));
        objects.put("String", "str");
        objects.put("String.empty", "");
        objects.put("String.date", "2020-01-15");
        objects.put("String.datetime", "2020-01-15 10:11:12");
        objects.put("String.num", "12");
        objects.put("String.bad", "abc");
        objects.put("UUID", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        objects.put("StringBuilder", new StringBuilder("sb"));
        objects.put("char[]", new char[]{'a', 'b'});
        objects.put("int[]", new int[]{1});
        objects.put("Enum", java.util.concurrent.TimeUnit.DAYS);
        objects.put("Year", Year.of(2020));
        objects.put("Duration", Duration.ofHours(1));
        objects.put("StringReader", null); // placeholder; handled separately
        String[] targets = {"w_varchar", "w_char1", "w_int", "w_tinyint1", "w_bit1", "w_date", "w_datetime", "w_timestamp", "w_time", "w_double", "w_dec", "w_text", "w_blob"};
        int seq = 0;
        for (String target : targets) {
            for (var o : objects.entrySet()) {
                if (o.getValue() == null) continue;
                String tag = "setObject:" + o.getKey() + "->" + target;
                final int id = ++seq;
                emit("WRITE", tag, writeRead(c, target, id, tag, ps -> ps.setObject(1, o.getValue())));
            }
            // explicit target SQL types
            int[] types = {Types.VARCHAR, Types.TIMESTAMP, Types.DATE, Types.TIME, Types.INTEGER, Types.BOOLEAN, Types.BIT, Types.DECIMAL, Types.DOUBLE, Types.CHAR, Types.LONGVARCHAR, Types.OTHER};
            Object[] typedValues = {new Date(epoch), new Timestamp(epoch), "2020-01-15", "12", 42, Boolean.TRUE, LocalDateTime.of(2020, 1, 15, 23, 30), 12.5d};
            String[] typedNames = {"util.Date", "sql.Timestamp", "String.date", "String.num", "Integer", "Boolean", "LocalDateTime", "Double"};
            for (int t : types) for (int i = 0; i < typedValues.length; i++) {
                final Object v = typedValues[i];
                String tag = "setObject(" + typedNames[i] + "," + JDBCType.valueOf(t).getName() + ")->" + target;
                final int id = ++seq;
                emit("WRITE", tag, writeRead(c, target, id, tag, ps -> ps.setObject(1, v, t)));
            }
            Map<String, PsSetter> setters = new LinkedHashMap<>();
            setters.put("setNull(VARCHAR)", ps -> ps.setNull(1, Types.VARCHAR));
            setters.put("setNull(NULL)", ps -> ps.setNull(1, Types.NULL));
            setters.put("setNull(OTHER)", ps -> ps.setNull(1, Types.OTHER));
            setters.put("setObject(null)", ps -> ps.setObject(1, null));
            setters.put("setString(null)", ps -> ps.setString(1, null));
            setters.put("setString('')", ps -> ps.setString(1, ""));
            setters.put("setString('12')", ps -> ps.setString(1, "12"));
            setters.put("setString('12abc')", ps -> ps.setString(1, "12abc"));
            setters.put("setString('abc')", ps -> ps.setString(1, "abc"));
            setters.put("setString('1')", ps -> ps.setString(1, "1"));
            setters.put("setString('true')", ps -> ps.setString(1, "true"));
            setters.put("setString('Y')", ps -> ps.setString(1, "Y"));
            setters.put("setString('2020-01-15')", ps -> ps.setString(1, "2020-01-15"));
            setters.put("setString('2020-01-15 10:11:12')", ps -> ps.setString(1, "2020-01-15 10:11:12"));
            setters.put("setString('0000-00-00')", ps -> ps.setString(1, "0000-00-00"));
            setters.put("setString('10:11:12')", ps -> ps.setString(1, "10:11:12"));
            setters.put("setString(quote)", ps -> ps.setString(1, "it's \\ \"q\" \u0000 \u00e9 \ud83d\ude00 % _ \n ?"));
            setters.put("setNString('n')", ps -> ps.setNString(1, "n"));
            setters.put("setBoolean(true)", ps -> ps.setBoolean(1, true));
            setters.put("setBoolean(false)", ps -> ps.setBoolean(1, false));
            setters.put("setInt(7)", ps -> ps.setInt(1, 7));
            setters.put("setInt(0)", ps -> ps.setInt(1, 0));
            setters.put("setLong(7)", ps -> ps.setLong(1, 7L));
            setters.put("setShort(7)", ps -> ps.setShort(1, (short) 7));
            setters.put("setByte(7)", ps -> ps.setByte(1, (byte) 7));
            setters.put("setDouble(12.5)", ps -> ps.setDouble(1, 12.5));
            setters.put("setDouble(100)", ps -> ps.setDouble(1, 100));
            setters.put("setDouble(1e10)", ps -> ps.setDouble(1, 1e10));
            setters.put("setDouble(0.1+0.2)", ps -> ps.setDouble(1, 0.1 + 0.2));
            setters.put("setFloat(1.1)", ps -> ps.setFloat(1, 1.1f));
            setters.put("setBigDecimal(12.50)", ps -> ps.setBigDecimal(1, new BigDecimal("12.50")));
            setters.put("setBigDecimal(1E+3)", ps -> ps.setBigDecimal(1, new BigDecimal("1E+3")));
            setters.put("setBytes", ps -> ps.setBytes(1, "bytes".getBytes(StandardCharsets.UTF_8)));
            setters.put("setDate", ps -> ps.setDate(1, new java.sql.Date(epochW)));
            setters.put("setDate(utcCal)", ps -> ps.setDate(1, new java.sql.Date(epochW), utc));
            setters.put("setDate(vanCal)", ps -> ps.setDate(1, new java.sql.Date(epochW), van));
            setters.put("setTime", ps -> ps.setTime(1, new Time(epochW)));
            setters.put("setTime(utcCal)", ps -> ps.setTime(1, new Time(epochW), utc));
            setters.put("setTimestamp", ps -> ps.setTimestamp(1, new Timestamp(epoch)));
            setters.put("setTimestamp(winter)", ps -> ps.setTimestamp(1, new Timestamp(epochW)));
            setters.put("setTimestamp(utcCal)", ps -> ps.setTimestamp(1, new Timestamp(epoch), utc));
            setters.put("setTimestamp(vanCal)", ps -> ps.setTimestamp(1, new Timestamp(epoch), van));
            setters.put("setTimestamp(nanos)", ps -> { Timestamp t = new Timestamp(epoch); t.setNanos(999_999_999); ps.setTimestamp(1, t); });
            setters.put("setCharacterStream", ps -> ps.setCharacterStream(1, new StringReader("reader")));
            setters.put("setCharacterStream(len)", ps -> ps.setCharacterStream(1, new StringReader("reader"), 3));
            setters.put("setBinaryStream", ps -> ps.setBinaryStream(1, new ByteArrayInputStream("stream".getBytes(StandardCharsets.UTF_8))));
            setters.put("setAsciiStream", ps -> ps.setAsciiStream(1, new ByteArrayInputStream("ascii".getBytes(StandardCharsets.UTF_8))));
            setters.put("setClob(reader)", ps -> ps.setClob(1, new StringReader("clob")));
            setters.put("setBlob(stream)", ps -> ps.setBlob(1, new ByteArrayInputStream("blob".getBytes(StandardCharsets.UTF_8))));
            for (var e : setters.entrySet()) {
                String tag = e.getKey() + "->" + target;
                final int id = ++seq;
                emit("WRITE", tag, writeRead(c, target, id, tag, e.getValue()));
            }
        }
        // comparisons through parameters (WHERE col = ?) with mismatched Java types
        Map<String, PsSetter> where = new LinkedHashMap<>();
        where.put("c_tinyint1=setBoolean(true)", ps -> ps.setBoolean(1, true));
        where.put("c_tinyint1=setString('1')", ps -> ps.setString(1, "1"));
        where.put("c_tinyint1=setString('true')", ps -> ps.setString(1, "true"));
        where.put("c_char1=setBoolean(true)", ps -> ps.setBoolean(1, true));
        where.put("c_char1=setInt(1)", ps -> ps.setInt(1, 1));
        where.put("c_varchar=setInt(12)", ps -> ps.setInt(1, 12));
        where.put("c_int=setString('5')", ps -> ps.setString(1, "5"));
        where.put("c_int=setString('5abc')", ps -> ps.setString(1, "5abc"));
        where.put("c_date=setString('2020-01-15')", ps -> ps.setString(1, "2020-01-15"));
        where.put("c_date=setDate", ps -> ps.setDate(1, java.sql.Date.valueOf("2020-01-15")));
        where.put("c_date=setTimestamp", ps -> ps.setTimestamp(1, Timestamp.valueOf("2020-01-15 00:00:00")));
        where.put("c_date=setObject(util.Date)", ps -> ps.setObject(1, new Date(java.sql.Date.valueOf("2020-01-15").getTime())));
        where.put("c_date=setObject(LocalDate)", ps -> ps.setObject(1, LocalDate.of(2020, 1, 15)));
        where.put("c_datetime=setTimestamp", ps -> ps.setTimestamp(1, Timestamp.valueOf("2020-01-15 10:11:12")));
        where.put("c_datetime=setObject(LocalDateTime)", ps -> ps.setObject(1, LocalDateTime.of(2020, 1, 15, 10, 11, 12)));
        where.put("c_datetime=setString", ps -> ps.setString(1, "2020-01-15 10:11:12"));
        where.put("c_timestamp=setTimestamp", ps -> ps.setTimestamp(1, Timestamp.valueOf("2020-01-15 10:11:12")));
        where.put("c_time=setTime", ps -> ps.setTime(1, Time.valueOf("10:11:12")));
        where.put("c_time=setString", ps -> ps.setString(1, "10:11:12"));
        where.put("c_bit1=setBoolean(true)", ps -> ps.setBoolean(1, true));
        where.put("c_bit1=setInt(1)", ps -> ps.setInt(1, 1));
        where.put("c_bit1=setString('1')", ps -> ps.setString(1, "1"));
        where.put("c_double=setString('1.1')", ps -> ps.setString(1, "1.1"));
        where.put("c_double=setDouble(1.1)", ps -> ps.setDouble(1, 1.1));
        where.put("c_float=setFloat(1.1)", ps -> ps.setFloat(1, 1.1f));
        where.put("c_float=setDouble(1.1)", ps -> ps.setDouble(1, 1.1));
        where.put("c_blob=setString('42')", ps -> ps.setString(1, "42"));
        where.put("c_blob=setBytes('42')", ps -> ps.setBytes(1, "42".getBytes()));
        where.put("c_varchar=setBytes('12')", ps -> ps.setBytes(1, "12".getBytes()));
        for (var e : where.entrySet()) {
            String col = e.getKey().substring(0, e.getKey().indexOf('='));
            emit("WRITE", "WHERE " + e.getKey(), attempt(() -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT GROUP_CONCAT(id ORDER BY id) FROM zz_probe WHERE " + col + " = ?")) {
                    e.getValue().set(ps);
                    try (ResultSet r = ps.executeQuery()) { r.next(); return r.getString(1); }
                }
            }));
        }
        emit("WRITE", "LIMIT setInt", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM (SELECT id FROM zz_probe LIMIT ?) x")) { ps.setInt(1, 3); ResultSet r = ps.executeQuery(); r.next(); return r.getInt(1); } }));
        emit("WRITE", "LIMIT setString", attemptMsg(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM (SELECT id FROM zz_probe LIMIT ?) x")) { ps.setString(1, "3"); ResultSet r = ps.executeQuery(); r.next(); return r.getInt(1); } }));
        emit("WRITE", "IN (?) setString('1,2')", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM zz_probe WHERE id IN (?)")) { ps.setString(1, "1,2"); ResultSet r = ps.executeQuery(); r.next(); return r.getInt(1); } }));
        emit("WRITE", "missing parameter", attemptMsg(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT ?, ?")) { ps.setInt(1, 1); ResultSet r = ps.executeQuery(); r.next(); return r.getString(2); } }));
        emit("WRITE", "extra parameter index", attemptMsg(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT ?")) { ps.setInt(1, 1); ps.setInt(2, 1); return "ok"; } }));
        emit("WRITE", "param reuse after clearParameters", attemptMsg(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT ?")) { ps.setInt(1, 1); ps.executeQuery(); ps.clearParameters(); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1); } }));
        emit("WRITE", "param persists across executes", attemptMsg(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT ?")) { ps.setInt(1, 5); ps.executeQuery(); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1); } }));
        emit("WRITE", "ps.getMetaData before execute", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT id, c_varchar FROM zz_probe WHERE id = ?")) { ResultSetMetaData md = ps.getMetaData(); return md == null ? null : md.getColumnCount(); } }));
        emit("WRITE", "ps.getParameterMetaData count", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT id FROM zz_probe WHERE id = ? AND c_varchar = ?")) { return ps.getParameterMetaData().getParameterCount(); } }));
        emit("WRITE", "ps.toString contains sql", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT id FROM zz_probe WHERE id = ?")) { ps.setInt(1, 5); return ps.toString().contains("zz_probe"); } }));
    }

    interface PsSetter { void set(PreparedStatement ps) throws Exception; }

    static String writeRead(Connection c, String target, int id, String tag, PsSetter setter) {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO zz_write (tag, " + target + ") VALUES ('" + id + "', ?)")) {
            setter.set(ps);
            ps.executeUpdate();
            String warn = "";
            SQLWarning w = ps.getWarnings();
            if (w != null) warn = " WARN:" + w.getErrorCode();
        } catch (Throwable t) {
            return ex(t);
        }
        try (ResultSet r = c.createStatement().executeQuery("SELECT IF(" + target + " IS NULL, 'NULL', CONCAT('', "
                + (target.equals("w_blob") ? "HEX(w_blob)" : target.equals("w_bit1") ? "w_bit1+0" : target) + ")) FROM zz_write WHERE tag = '" + id + "'")) {
            return r.next() ? "stored:" + clip(r.getString(1)) : "NOROW";
        } catch (Throwable t) {
            return "READBACK-" + ex(t);
        }
    }

    // ---------- statement behaviours ----------
    static void stmt(Connection c) throws Exception {
        Statement s = c.createStatement();
        s.executeUpdate("UPDATE zz_lock SET v = 0");
        emit("STMT", "update count unchanged rows", attempt(() -> s.executeUpdate("UPDATE zz_lock SET v = 0")));
        emit("STMT", "update count changed rows", attempt(() -> s.executeUpdate("UPDATE zz_lock SET v = 1")));
        emit("STMT", "update count no match", attempt(() -> s.executeUpdate("UPDATE zz_lock SET v = 1 WHERE id = 999")));
        emit("STMT", "insert on duplicate key update count", attempt(() -> s.executeUpdate("INSERT INTO zz_lock (id, v) VALUES (1, 5) ON DUPLICATE KEY UPDATE v = 5")));
        emit("STMT", "insert on duplicate key no-op count", attempt(() -> s.executeUpdate("INSERT INTO zz_lock (id, v) VALUES (1, 5) ON DUPLICATE KEY UPDATE v = 5")));
        emit("STMT", "replace count", attempt(() -> s.executeUpdate("REPLACE INTO zz_lock (id, v, s) VALUES (1, 0, 'a')")));
        emit("STMT", "delete count", attempt(() -> s.executeUpdate("DELETE FROM zz_lock WHERE id = 999")));
        emit("STMT", "ddl count", attempt(() -> s.executeUpdate("CREATE TEMPORARY TABLE zz_tmp (a int)")));
        emit("STMT", "executeLargeUpdate", attempt(() -> s.executeLargeUpdate("UPDATE zz_lock SET v = 0")));
        s.executeUpdate("DELETE FROM zz_write");
        emit("STMT", "generated keys single", attempt(() -> { s.executeUpdate("INSERT INTO zz_write (tag) VALUES ('g1')", Statement.RETURN_GENERATED_KEYS); return keys(s.getGeneratedKeys()); }));
        emit("STMT", "generated keys multi-row", attempt(() -> { s.executeUpdate("INSERT INTO zz_write (tag) VALUES ('g2'),('g3'),('g4')", Statement.RETURN_GENERATED_KEYS); return keys(s.getGeneratedKeys()); }));
        emit("STMT", "generated keys without flag", attempt(() -> { s.executeUpdate("INSERT INTO zz_write (tag) VALUES ('g5')"); return keys(s.getGeneratedKeys()); }));
        emit("STMT", "generated keys after UPDATE", attempt(() -> { s.executeUpdate("UPDATE zz_write SET w_int = 1 WHERE tag = 'g1'", Statement.RETURN_GENERATED_KEYS); return keys(s.getGeneratedKeys()); }));
        emit("STMT", "generated keys explicit id", attempt(() -> { s.executeUpdate("INSERT INTO zz_write (id, tag) VALUES (100000, 'g6')", Statement.RETURN_GENERATED_KEYS); return keys(s.getGeneratedKeys()); }));
        emit("STMT", "generated keys by column names", attempt(() -> { PreparedStatement ps = c.prepareStatement("INSERT INTO zz_write (tag) VALUES ('g7')", new String[]{"id"}); ps.executeUpdate(); return keys(ps.getGeneratedKeys()); }));
        emit("STMT", "generated keys prepared", attempt(() -> { PreparedStatement ps = c.prepareStatement("INSERT INTO zz_write (tag) VALUES (?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1, "g8"); ps.executeUpdate(); return keys(ps.getGeneratedKeys()); }));
        emit("STMT", "generated keys ON DUP update", attempt(() -> { s.executeUpdate("INSERT INTO zz_write (tag, w_int) VALUES ('g8', 1) ON DUPLICATE KEY UPDATE w_int = 2", Statement.RETURN_GENERATED_KEYS); return keys(s.getGeneratedKeys()); }));
        emit("STMT", "LAST_INSERT_ID() after insert", attempt(() -> { s.executeUpdate("INSERT INTO zz_write (tag) VALUES ('g9')"); ResultSet r = s.executeQuery("SELECT LAST_INSERT_ID() > 0"); r.next(); return r.getString(1); }));
        emit("STMT", "batch Statement", attempt(() -> { Statement b = c.createStatement(); b.addBatch("INSERT INTO zz_write (tag) VALUES ('b1')"); b.addBatch("UPDATE zz_lock SET v = v"); b.addBatch("UPDATE zz_lock SET v = 7 WHERE id = 999"); return Arrays.toString(b.executeBatch()); }));
        emit("STMT", "batch prepared insert", attempt(() -> { PreparedStatement ps = c.prepareStatement("INSERT INTO zz_write (tag) VALUES (?)", Statement.RETURN_GENERATED_KEYS); for (String t : new String[]{"b2", "b3", "b4"}) { ps.setString(1, t); ps.addBatch(); } String counts = Arrays.toString(ps.executeBatch()); return counts + " keys=" + keys(ps.getGeneratedKeys()); }));
        emit("STMT", "batch prepared update", attempt(() -> { PreparedStatement ps = c.prepareStatement("UPDATE zz_lock SET v = ? WHERE id = ?"); for (int i : new int[]{1, 2, 999}) { ps.setInt(1, 3); ps.setInt(2, i); ps.addBatch(); } return Arrays.toString(ps.executeBatch()); }));
        emit("STMT", "batch with duplicate failure", attemptMsg(() -> { PreparedStatement ps = c.prepareStatement("INSERT INTO zz_write (tag) VALUES (?)"); for (String t : new String[]{"d1", "b2", "d2"}) { ps.setString(1, t); ps.addBatch(); } try { return Arrays.toString(ps.executeBatch()); } catch (BatchUpdateException e) { return "BatchUpdateException counts=" + Arrays.toString(e.getUpdateCounts()) + " state=" + e.getSQLState() + " code=" + e.getErrorCode(); } }));
        emit("STMT", "rows after failed batch", attempt(() -> { ResultSet r = s.executeQuery("SELECT GROUP_CONCAT(tag ORDER BY tag) FROM zz_write WHERE tag IN ('d1','d2')"); r.next(); return r.getString(1); }));
        emit("STMT", "empty batch", attempt(() -> Arrays.toString(c.createStatement().executeBatch())));
        // maxRows
        String[] maxRowSql = {"SELECT id FROM zz_probe", "SELECT id FROM zz_probe LIMIT 10", "SELECT id FROM zz_probe LIMIT 2", "SELECT id FROM zz_probe UNION ALL SELECT id FROM zz_probe",
                "(SELECT id FROM zz_probe) UNION ALL (SELECT id FROM zz_probe)", "SELECT COUNT(*) FROM (SELECT id FROM zz_probe) x", "SHOW TABLES LIKE 'zz%'", "CALL zz_proc()",
                "SELECT (SELECT COUNT(*) FROM (SELECT id FROM zz_probe) y) FROM zz_probe", "WITH w AS (SELECT id FROM zz_probe) SELECT * FROM w", "/* c */ SELECT id FROM zz_probe",
                "select id from zz_probe where id in (select id from zz_probe)", "SELECT id FROM zz_probe ORDER BY id DESC", "SELECT SQL_CALC_FOUND_ROWS id FROM zz_probe"};
        for (String sql : maxRowSql) {
            emit("STMT", "maxRows(5) " + sql, attemptMsg(() -> { Statement t = c.createStatement(); t.setMaxRows(5); try (ResultSet r = t.executeQuery(sql)) { int n = 0; String first = null; while (r.next()) { if (n == 0) first = r.getString(1); n++; } return n + " first=" + first; } }));
        }
        emit("STMT", "maxRows(5) prepared", attemptMsg(() -> { PreparedStatement t = c.prepareStatement("SELECT id FROM zz_probe WHERE id > ?"); t.setInt(1, 0); t.setMaxRows(5); try (ResultSet r = t.executeQuery()) { int n = 0; while (r.next()) n++; return n; } }));
        emit("STMT", "maxRows(5) then subquery count", attempt(() -> { Statement t = c.createStatement(); t.setMaxRows(5); try (ResultSet r = t.executeQuery("SELECT COUNT(*) FROM zz_probe WHERE id IN (SELECT id FROM zz_probe)")) { r.next(); return r.getInt(1); } }));
        emit("STMT", "maxRows(5) INSERT..SELECT", attempt(() -> { Statement t = c.createStatement(); t.setMaxRows(5); t.executeUpdate("CREATE TEMPORARY TABLE zz_tmp2 (a int)"); return t.executeUpdate("INSERT INTO zz_tmp2 SELECT id FROM zz_probe"); }));
        emit("STMT", "maxRows(5) sticky on next statement", attempt(() -> { Statement t = c.createStatement(); t.setMaxRows(5); t.executeQuery("SELECT id FROM zz_probe").close(); try (ResultSet r = c.createStatement().executeQuery("SELECT id FROM zz_probe")) { int n = 0; while (r.next()) n++; return n; } }));
        emit("STMT", "setLargeMaxRows", attempt(() -> { Statement t = c.createStatement(); t.setLargeMaxRows(5); return t.getMaxRows(); }));
        // fetch size semantics
        emit("STMT", "fetchSize MIN then second query same conn", attemptMsg(() -> { try (Connection c3 = open("")) { Statement t = c3.createStatement(); t.setFetchSize(Integer.MIN_VALUE); ResultSet r = t.executeQuery("SELECT id FROM zz_probe ORDER BY id"); r.next(); ResultSet q = c3.createStatement().executeQuery("SELECT 42"); q.next(); int x = q.getInt(1); int n = 1; while (r.next()) n++; return x + "/" + n; } }));
        emit("STMT", "fetchSize 10 then second query same conn", attemptMsg(() -> { try (Connection c3 = open("")) { Statement t = c3.createStatement(); t.setFetchSize(10); ResultSet r = t.executeQuery("SELECT id FROM zz_probe ORDER BY id"); r.next(); ResultSet q = c3.createStatement().executeQuery("SELECT 42"); q.next(); int x = q.getInt(1); int n = 1; while (r.next()) n++; return x + "/" + n; } }));
        emit("STMT", "fetchSize MIN getFetchSize", attempt(() -> { Statement t = c.createStatement(); t.setFetchSize(Integer.MIN_VALUE); int f = t.getFetchSize(); t.close(); return f; }));
        emit("STMT", "fetchSize -1", attemptMsg(() -> { Statement t = c.createStatement(); t.setFetchSize(-1); return t.getFetchSize(); }));
        emit("STMT", "fetchSize MIN rs.getType/last()", attemptMsg(() -> { try (Connection c3 = open("")) { Statement t = c3.createStatement(); t.setFetchSize(Integer.MIN_VALUE); ResultSet r = t.executeQuery("SELECT id FROM zz_probe ORDER BY id"); int ty = r.getType(); String l; try { l = String.valueOf(r.last()); } catch (SQLException e) { l = ex(e); } r.close(); return ty + "/" + l; } }));
        emit("STMT", "fetchSize MIN net_write_timeout during stream", attemptMsg(() -> { try (Connection c2 = open("")) { Statement t = c2.createStatement(); t.setFetchSize(Integer.MIN_VALUE); ResultSet r = t.executeQuery("SELECT id, @@session.net_write_timeout FROM zz_probe ORDER BY id"); r.next(); String v = r.getString(2); while (r.next()) { } return v; } }));
        emit("STMT", "fetchSize MIN commit while streaming", attemptMsg(() -> { try (Connection c2 = open("")) { c2.setAutoCommit(false); Statement t = c2.createStatement(); t.setFetchSize(Integer.MIN_VALUE); ResultSet r = t.executeQuery("SELECT id FROM zz_probe ORDER BY id"); r.next(); c2.commit(); int n = 1; while (r.next()) n++; return n; } }));
        emit("STMT", "ps fetchSize MIN + setMaxRows", attemptMsg(() -> { try (Connection c3 = open("")) { PreparedStatement t = c3.prepareStatement("SELECT id FROM zz_probe ORDER BY id"); t.setFetchSize(Integer.MIN_VALUE); t.setMaxRows(3); ResultSet r = t.executeQuery(); int n = 0; while (r.next()) n++; return n; } }));
        // warnings
        emit("STMT", "warning on truncation (non-strict)", attemptMsg(() -> { Statement t = c.createStatement(); t.executeUpdate("INSERT INTO zz_lock (id, v, s) VALUES (50, 1, 'toolongvalue')"); SQLWarning w = t.getWarnings(); String r = w == null ? "no-warning" : w.getClass().getSimpleName() + ":" + w.getSQLState() + ":" + w.getErrorCode(); t.executeUpdate("DELETE FROM zz_lock WHERE id = 50"); return r; }));
        emit("STMT", "conn.getWarnings after truncation", attemptMsg(() -> { Statement t = c.createStatement(); t.executeUpdate("INSERT INTO zz_lock (id, v, s) VALUES (51, 1, 'toolongvalue')"); SQLWarning w = c.getWarnings(); t.executeUpdate("DELETE FROM zz_lock WHERE id = 51"); return w == null ? "no-warning" : w.getSQLState() + ":" + w.getErrorCode(); }));
        emit("STMT", "strict truncation exception", attemptMsg(() -> { try (Connection c2 = open("")) { Statement t = c2.createStatement(); t.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'"); return t.executeUpdate("INSERT INTO zz_lock (id, v, s) VALUES (52, 1, 'toolongvalue')"); } }));
        emit("STMT", "strict bad int exception", attemptMsg(() -> { try (Connection c2 = open("")) { Statement t = c2.createStatement(); t.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'"); return t.executeUpdate("INSERT INTO zz_lock (id, v) VALUES (53, 'abc')"); } }));
        emit("STMT", "non-strict bad date stored", attemptMsg(() -> { Statement t = c.createStatement(); t.executeUpdate("INSERT INTO zz_write (tag, w_date) VALUES ('baddate', '2020-13-45')"); ResultSet r = t.executeQuery("SELECT CONCAT('', w_date) FROM zz_write WHERE tag = 'baddate'"); r.next(); return r.getString(1); }));
        // closing semantics / autocommit
        emit("STMT", "setAutoCommit(false) then @@autocommit", attempt(() -> { try (Connection c2 = open("")) { c2.setAutoCommit(false); ResultSet r = c2.createStatement().executeQuery("SELECT @@autocommit"); r.next(); return r.getString(1); } }));
        emit("STMT", "close with open tx rolls back", attempt(() -> { try (Connection c2 = open("")) { c2.setAutoCommit(false); c2.createStatement().executeUpdate("UPDATE zz_lock SET v = 77 WHERE id = 3"); } ResultSet r = s.executeQuery("SELECT v FROM zz_lock WHERE id = 3"); r.next(); return r.getInt(1); }));
        emit("STMT", "commit in autocommit mode", attemptMsg(() -> { c.commit(); return "ok"; }));
        emit("STMT", "rollback in autocommit mode", attemptMsg(() -> { c.rollback(); return "ok"; }));
        emit("STMT", "isolation set/get", attempt(() -> { try (Connection c2 = open("")) { c2.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED); ResultSet r = c2.createStatement().executeQuery("SELECT @@tx_isolation"); r.next(); return r.getString(1) + "/" + c2.getTransactionIsolation(); } }));
        emit("STMT", "savepoint", attempt(() -> { try (Connection c2 = open("")) { c2.setAutoCommit(false); Savepoint sp = c2.setSavepoint("sp1"); c2.createStatement().executeUpdate("UPDATE zz_lock SET v = 88 WHERE id = 3"); c2.rollback(sp); c2.commit(); ResultSet r = c2.createStatement().executeQuery("SELECT v FROM zz_lock WHERE id = 3"); r.next(); return r.getInt(1); } }));
        emit("STMT", "isValid after server kill", attemptMsg(() -> { try (Connection c2 = open("")) { ResultSet r = c2.createStatement().executeQuery("SELECT CONNECTION_ID()"); r.next(); long id = r.getLong(1); s.execute("KILL " + id); Thread.sleep(200); boolean valid = c2.isValid(2); String after; try { c2.createStatement().executeQuery("SELECT 1"); after = "query-ok"; } catch (SQLException e) { after = ex(e); } return valid + "/" + after + "/closed=" + c2.isClosed(); } }));
        emit("STMT", "abort()", attemptMsg(() -> { Connection c2 = open(""); c2.abort(Runnable::run); return c2.isClosed(); }));
        emit("STMT", "setNetworkTimeout", attemptMsg(() -> { try (Connection c2 = open("")) { c2.setNetworkTimeout(Runnable::run, 1000); int t = c2.getNetworkTimeout(); try { c2.createStatement().executeQuery("SELECT SLEEP(3)"); return t + "/no-timeout"; } catch (SQLException e) { return t + "/" + ex(e) + "/closed=" + c2.isClosed(); } } }));
    }

    static String keys(ResultSet r) throws SQLException {
        try (r) {
            ResultSetMetaData md = r.getMetaData();
            StringBuilder s = new StringBuilder("label=" + md.getColumnLabel(1) + " name=" + md.getColumnName(1) + " type=" + md.getColumnType(1) + " class=" + md.getColumnClassName(1) + " n=");
            int n = 0;
            long first = -1;
            String cls = null;
            while (r.next()) { if (n == 0) { first = r.getLong(1); cls = r.getObject(1) == null ? null : r.getObject(1).getClass().getSimpleName(); } n++; }
            return s.append(n).append(" consecutive-first=").append(first > 0).append(" objClass=").append(cls).toString();
        }
    }

    // ---------- query timeout / cancel across statement kinds ----------
    static void timeout(Connection c) throws Exception {
        String[] kinds = {
                "SELECT 1", "select 1", " SELECT 1", "/* hint */ SELECT 1", "-- c\nSELECT 1", "(SELECT 1)", "(SELECT 1) UNION (SELECT 2)", "WITH w AS (SELECT 1 a) SELECT * FROM w",
                "SELECT 1 FROM DUAL WHERE 1 = 1 FOR UPDATE", "SELECT id FROM zz_lock WHERE id = 1 FOR UPDATE", "SELECT id FROM zz_lock WHERE id = 1 LOCK IN SHARE MODE",
                "UPDATE zz_lock SET v = v WHERE id = 1", "INSERT INTO zz_lock (id, v) VALUES (60, 1)", "DELETE FROM zz_lock WHERE id = 60", "REPLACE INTO zz_lock (id, v, s) VALUES (2, 0, 'b')",
                "INSERT INTO zz_lock (id, v) VALUES (2, 0) ON DUPLICATE KEY UPDATE v = 0", "INSERT INTO zz_lock (id, v) SELECT 61, 1 FROM DUAL", "DELETE FROM zz_lock WHERE id = 61",
                "SHOW TABLES LIKE 'zz%'", "SHOW GRANTS", "SHOW GRANTS FOR CURRENT_USER()", "SHOW VARIABLES LIKE 'sql_mode'", "SHOW CREATE TABLE zz_lock", "SHOW COLUMNS FROM zz_lock",
                "SHOW INDEX FROM zz_lock", "SHOW PROCESSLIST", "SHOW WARNINGS", "DESCRIBE zz_lock", "EXPLAIN SELECT 1", "ANALYZE TABLE zz_lock", "CHECK TABLE zz_lock",
                "SET @zz = 1", "SET SESSION group_concat_max_len = 2048", "SET NAMES utf8mb4", "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED", "SET autocommit = 1",
                "SET STATEMENT max_statement_time=5 FOR SELECT 1", "USE carlos", "CALL zz_proc()", "DO 1", "SELECT GET_LOCK('zz_probe_lock', 1)", "SELECT RELEASE_LOCK('zz_probe_lock')",
                "LOCK TABLES zz_lock WRITE", "UNLOCK TABLES", "BEGIN", "COMMIT", "START TRANSACTION", "ROLLBACK", "START TRANSACTION READ ONLY", "COMMIT ",
                "SAVEPOINT zzsp", "CREATE TEMPORARY TABLE zz_tt (a int)", "DROP TEMPORARY TABLE zz_tt", "CREATE TABLE zz_ddl (a int)", "ALTER TABLE zz_ddl ADD COLUMN b int",
                "CREATE INDEX zz_ddl_i ON zz_ddl (a)", "TRUNCATE TABLE zz_ddl", "DROP TABLE zz_ddl", "PREPARE zzp FROM 'SELECT 1'", "EXECUTE zzp", "DEALLOCATE PREPARE zzp",
                "FLUSH STATUS", "KILL QUERY 999999999", "SELECT 1 INTO @zzv", "SELECT SQL_NO_CACHE 1", "HANDLER zz_lock OPEN", "HANDLER zz_lock CLOSE", "XA RECOVER",
                "SELECT /*+ MAX_EXECUTION_TIME(1000) */ 1", "CREATE OR REPLACE VIEW zz_view AS SELECT 1 AS a", "DROP VIEW zz_view", "GRANT USAGE ON *.* TO 'probe'@'localhost'",
                "OPTIMIZE TABLE zz_lock", "SELECT SLEEP(0)", "SELECT BENCHMARK(1, 1)", "VALUES (1)", "TABLE zz_lock", "SELECT 1; "};
        for (String sql : kinds) {
            emit("TIMEOUT", "Statement qt=30: " + sql.replace("\n", "\\n"), attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setQueryTimeout(30); boolean rs = t.execute(sql); return rs ? "resultset" : "count=" + t.getUpdateCount(); } }));
        }
        try { c.createStatement().execute("UNLOCK TABLES"); c.createStatement().execute("ROLLBACK"); c.createStatement().execute("SET autocommit=1"); } catch (SQLException ignored) { }
        String[] prepared = {"SELECT ?", "UPDATE zz_lock SET v = v WHERE id = ?", "SHOW TABLES LIKE ?", "SELECT GET_LOCK(?, 1)", "CALL zz_proc()", "SET @zz = ?", "INSERT INTO zz_lock (id, v) VALUES (?, 1) ON DUPLICATE KEY UPDATE v = v"};
        for (String sql : prepared) {
            emit("TIMEOUT", "Prepared qt=30: " + sql, attemptMsg(() -> { try (PreparedStatement t = c.prepareStatement(sql)) { if (sql.contains("?")) t.setObject(1, sql.contains("LIKE") ? "zz%" : sql.contains("GET_LOCK") ? "zz_p" : (Object) 1); t.setQueryTimeout(30); boolean rs = t.execute(); return rs ? "resultset" : "count=" + t.getUpdateCount(); } }));
        }
        emit("TIMEOUT", "qt=30 + maxRows=5 SHOW TABLES", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setQueryTimeout(30); t.setMaxRows(5); return t.execute("SHOW TABLES"); } }));
        emit("TIMEOUT", "maxRows=5 only: SHOW GRANTS", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setMaxRows(5); return t.execute("SHOW GRANTS"); } }));
        emit("TIMEOUT", "maxRows=5 only: CALL", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setMaxRows(5); return t.execute("CALL zz_proc()"); } }));
        emit("TIMEOUT", "maxRows=5 only: SET @zz=1", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setMaxRows(5); return t.execute("SET @zz = 1"); } }));
        emit("TIMEOUT", "maxRows=5 only: BEGIN", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setMaxRows(5); boolean b = t.execute("BEGIN"); t.execute("ROLLBACK"); return b; } }));
        emit("TIMEOUT", "maxRows=5 only: UPDATE", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setMaxRows(5); return t.executeUpdate("UPDATE zz_lock SET v = v"); } }));
        emit("TIMEOUT", "maxRows=5 only: LOCK TABLES", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setMaxRows(5); boolean b = t.execute("LOCK TABLES zz_lock READ"); t.execute("UNLOCK TABLES"); return b; } }));
        // actual expiry behaviour
        emit("TIMEOUT", "SELECT SLEEP(3) qt=1", timed(() -> { try (Statement t = c.createStatement()) { t.setQueryTimeout(1); ResultSet r = t.executeQuery("SELECT SLEEP(3)"); r.next(); return "returned " + r.getString(1); } }));
        emit("TIMEOUT", "conn usable after timeout", attemptMsg(() -> { ResultSet r = c.createStatement().executeQuery("SELECT 7"); r.next(); return r.getInt(1) + " closed=" + c.isClosed(); }));
        emit("TIMEOUT", "heavy SELECT qt=1", timed(() -> { try (Statement t = c.createStatement()) { t.setQueryTimeout(1); ResultSet r = t.executeQuery("SELECT COUNT(*) FROM zz_probe a, zz_probe b, zz_probe c2, zz_probe d, zz_probe e, zz_probe f WHERE SHA2(CONCAT(a.id,b.id,c2.id,d.id,e.id,f.id),256) = 'x'"); r.next(); return "returned " + r.getString(1); } }));
        emit("TIMEOUT", "UPDATE SLEEP qt=1", timed(() -> { try (Statement t = c.createStatement()) { t.setQueryTimeout(1); return "count=" + t.executeUpdate("UPDATE zz_lock SET v = v + SLEEP(2) WHERE id = 1"); } }));
        emit("TIMEOUT", "CALL sleeping proc qt=1", timed(() -> { c.createStatement().execute("DROP PROCEDURE IF EXISTS zz_slow"); c.createStatement().execute("CREATE PROCEDURE zz_slow() BEGIN DO SLEEP(3); SELECT 1; END"); try (Statement t = c.createStatement()) { t.setQueryTimeout(1); t.execute("CALL zz_slow()"); return "completed"; } }));
        emit("TIMEOUT", "lock wait blocked UPDATE qt=1", timed(() -> { try (Connection holder = open("")) { holder.setAutoCommit(false); holder.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 2"); try (Statement t = c.createStatement()) { t.setQueryTimeout(1); return "count=" + t.executeUpdate("UPDATE zz_lock SET v = v WHERE id = 2"); } finally { holder.rollback(); } } }));
        emit("TIMEOUT", "GET_LOCK wait qt=1", timed(() -> { try (Connection holder = open("")) { holder.createStatement().executeQuery("SELECT GET_LOCK('zz_wait', 0)").close(); try (Statement t = c.createStatement()) { t.setQueryTimeout(1); ResultSet r = t.executeQuery("SELECT GET_LOCK('zz_wait', 5)"); r.next(); return "returned " + r.getString(1); } } }));
        emit("TIMEOUT", "prepared SLEEP qt=1", timed(() -> { try (PreparedStatement t = c.prepareStatement("SELECT SLEEP(?)")) { t.setInt(1, 3); t.setQueryTimeout(1); ResultSet r = t.executeQuery(); r.next(); return "returned " + r.getString(1); } }));
        emit("TIMEOUT", "streaming SLEEP qt=1", timed(() -> { try (Statement t = c.createStatement()) { t.setFetchSize(Integer.MIN_VALUE); t.setQueryTimeout(1); ResultSet r = t.executeQuery("SELECT SLEEP(3)"); r.next(); return "returned " + r.getString(1); } }));
        emit("TIMEOUT", "slow fetch after fast execute qt=1", timed(() -> { try (Statement t = c.createStatement()) { t.setFetchSize(Integer.MIN_VALUE); t.setQueryTimeout(1); ResultSet r = t.executeQuery("SELECT id, SLEEP(0.2) FROM zz_probe ORDER BY id"); int n = 0; while (r.next()) n++; return "rows=" + n; } }));
        emit("TIMEOUT", "cancel() from other thread", timed(() -> { try (Statement t = c.createStatement()) { ScheduledExecutorService x = Executors.newSingleThreadScheduledExecutor(); x.schedule(() -> { try { t.cancel(); } catch (SQLException e) { } }, 500, TimeUnit.MILLISECONDS); try { ResultSet r = t.executeQuery("SELECT SLEEP(3)"); r.next(); return "returned " + r.getString(1); } finally { x.shutdownNow(); } } }));
        emit("TIMEOUT", "conn usable after cancel", attemptMsg(() -> { ResultSet r = c.createStatement().executeQuery("SELECT 8"); r.next(); return r.getInt(1) + " closed=" + c.isClosed(); }));
        emit("TIMEOUT", "cancel() idle statement", attemptMsg(() -> { try (Statement t = c.createStatement()) { t.cancel(); ResultSet r = t.executeQuery("SELECT 9"); r.next(); return r.getInt(1); } }));
        emit("TIMEOUT", "session max_statement_time after qt stmt", attempt(() -> { try (Statement t = c.createStatement()) { t.setQueryTimeout(30); t.executeQuery("SELECT 1").close(); } ResultSet r = c.createStatement().executeQuery("SELECT @@max_statement_time, @@sql_select_limit"); r.next(); return r.getString(1) + "/" + r.getString(2); }));
        emit("TIMEOUT", "getQueryTimeout default", attempt(() -> c.createStatement().getQueryTimeout()));
        emit("TIMEOUT", "setQueryTimeout(-1)", attemptMsg(() -> { Statement t = c.createStatement(); t.setQueryTimeout(-1); return "ok"; }));
    }

    static String timed(Call call) {
        long t0 = System.nanoTime();
        String r = attemptMsg(call);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return r + " | elapsed~" + (ms < 700 ? "<0.7s" : ms < 1700 ? "~1s" : ms < 2700 ? "~2s" : ms < 3700 ? "~3s" : ">3.7s");
    }

    // ---------- exception taxonomy ----------
    static void exc(Connection c) throws Exception {
        Statement s = c.createStatement();
        Map<String, Call> cases = new LinkedHashMap<>();
        cases.put("duplicate key", () -> s.executeUpdate("INSERT INTO zz_lock (id, v) VALUES (1, 1)"));
        cases.put("fk violation insert", () -> s.executeUpdate("INSERT INTO zz_child (id, pid) VALUES (1, 12345)"));
        cases.put("not null violation", () -> { try (Connection c2 = open("")) { c2.createStatement().execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'"); return c2.createStatement().executeUpdate("INSERT INTO zz_lock (id, v) VALUES (70, NULL)"); } });
        cases.put("syntax error", () -> s.executeQuery("SELEC 1"));
        cases.put("unknown table", () -> s.executeQuery("SELECT * FROM zz_nosuch"));
        cases.put("unknown column", () -> s.executeQuery("SELECT nosuch FROM zz_lock"));
        cases.put("unknown database", () -> s.executeQuery("SELECT * FROM nosuchdb.t"));
        cases.put("division by zero strict", () -> { try (Connection c2 = open("")) { c2.createStatement().execute("SET SESSION sql_mode='STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO'"); return c2.createStatement().executeUpdate("INSERT INTO zz_lock (id, v) VALUES (71, 1/0)"); } });
        cases.put("data too long strict", () -> { try (Connection c2 = open("")) { c2.createStatement().execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'"); return c2.createStatement().executeUpdate("INSERT INTO zz_lock (id, v, s) VALUES (72, 1, 'toolongvalue')"); } });
        cases.put("out of range strict", () -> { try (Connection c2 = open("")) { c2.createStatement().execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'"); return c2.createStatement().executeUpdate("INSERT INTO zz_lock (id, v) VALUES (73, 99999999999)"); } });
        cases.put("incorrect date strict", () -> { try (Connection c2 = open("")) { c2.createStatement().execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'"); return c2.createStatement().executeUpdate("INSERT INTO zz_write (tag, w_date) VALUES ('x73', 'notadate')"); } });
        cases.put("access denied table", () -> { try (Connection c2 = open("", "probe_ro", "probe")) { return c2.createStatement().executeUpdate("UPDATE zz_lock SET v = v"); } });
        cases.put("access denied login", () -> open("", "probe", "wrong"));
        cases.put("unknown user login", () -> open("", "nosuchuser", "x"));
        cases.put("lock wait timeout", () -> { try (Connection holder = open(""); Connection waiter = open("")) { holder.setAutoCommit(false); holder.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 2"); waiter.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 1"); try { return waiter.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 2"); } finally { holder.rollback(); } } });
        cases.put("select for update nowait", () -> { try (Connection holder = open(""); Connection waiter = open("")) { holder.setAutoCommit(false); holder.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 2"); try { return waiter.createStatement().executeQuery("SELECT * FROM zz_lock WHERE id = 2 FOR UPDATE NOWAIT"); } finally { holder.rollback(); } } });
        cases.put("deadlock", Probe::deadlock);
        cases.put("killed connection mid-query", () -> { try (Connection victim = open("")) { ResultSet r = victim.createStatement().executeQuery("SELECT CONNECTION_ID()"); r.next(); long id = r.getLong(1); ScheduledExecutorService x = Executors.newSingleThreadScheduledExecutor(); x.schedule(() -> { try { s.execute("KILL " + id); } catch (SQLException e) { } }, 500, TimeUnit.MILLISECONDS); try { return victim.createStatement().executeQuery("SELECT SLEEP(3)"); } finally { x.shutdownNow(); } } });
        cases.put("killed query mid-query", () -> { try (Connection victim = open("")) { ResultSet r = victim.createStatement().executeQuery("SELECT CONNECTION_ID()"); r.next(); long id = r.getLong(1); ScheduledExecutorService x = Executors.newSingleThreadScheduledExecutor(); x.schedule(() -> { try { s.execute("KILL QUERY " + id); } catch (SQLException e) { } }, 500, TimeUnit.MILLISECONDS); try { ResultSet q = victim.createStatement().executeQuery("SELECT SLEEP(3)"); q.next(); return "returned " + q.getString(1); } finally { x.shutdownNow(); } } });
        cases.put("use after close", () -> { Connection c2 = open(""); c2.close(); return c2.createStatement(); });
        cases.put("connect refused", () -> { Properties p = new Properties(); p.setProperty("user", "probe"); p.setProperty("password", "probe"); Driver d = (Driver) Class.forName(kind.equals("cj") ? "com.mysql.cj.jdbc.Driver" : "io.github.carlos_emr.carlos.utility.MariaDbCompatibilityDriver").getDeclaredConstructor().newInstance(); return d.connect("jdbc:mysql://127.0.0.1:1/carlos?connectTimeout=2000", p); });
        cases.put("read-only tx write", () -> { try (Connection c2 = open("")) { c2.setReadOnly(true); c2.setAutoCommit(false); return c2.createStatement().executeUpdate("UPDATE zz_lock SET v = v"); } });
        cases.put("packet too large + conn usable after", () -> { try (Connection big = open("")) { String first; try { PreparedStatement ps = big.prepareStatement("SELECT LENGTH(?)"); ps.setBytes(1, new byte[70 * 1024 * 1024]); ResultSet r = ps.executeQuery(); r.next(); first = "ok:" + r.getLong(1); } catch (SQLException e) { first = ex(e) + " | " + clip(msg(e)); } String after; try { ResultSet r = big.createStatement().executeQuery("SELECT 5"); r.next(); after = "usable"; } catch (SQLException e) { after = ex(e); } return first + " || after: " + after + " closed=" + big.isClosed(); } });
        cases.put("blob insert over max_allowed_packet", () -> { try (Connection big = open("")) { String first; try { PreparedStatement ps = big.prepareStatement("INSERT INTO zz_write (tag, w_blob) VALUES ('big', ?)"); ps.setBinaryStream(1, new ByteArrayInputStream(new byte[70 * 1024 * 1024])); first = "count=" + ps.executeUpdate(); } catch (SQLException e) { first = ex(e) + " | " + clip(msg(e)); } String after; try { ResultSet r = big.createStatement().executeQuery("SELECT 5"); r.next(); after = "usable"; } catch (SQLException e) { after = ex(e); } return first + " || after: " + after; } });
        cases.put("getInt bad text", () -> { ResultSet r = s.executeQuery("SELECT 'abc'"); r.next(); return r.getInt(1); });
        cases.put("getInt overflow", () -> { ResultSet r = s.executeQuery("SELECT 99999999999"); r.next(); return r.getInt(1); });
        cases.put("getByte overflow", () -> { ResultSet r = s.executeQuery("SELECT 300"); r.next(); return r.getByte(1); });
        cases.put("getShort overflow", () -> { ResultSet r = s.executeQuery("SELECT 70000"); r.next(); return r.getShort(1); });
        cases.put("getLong overflow", () -> { ResultSet r = s.executeQuery("SELECT 18446744073709551615"); r.next(); return r.getLong(1); });
        cases.put("getInt from 12.7", () -> { ResultSet r = s.executeQuery("SELECT 12.7"); r.next(); return r.getInt(1); });
        cases.put("getInt from 12.7e0", () -> { ResultSet r = s.executeQuery("SELECT 12.7e0"); r.next(); return r.getInt(1); });
        cases.put("getInt from '12.7'", () -> { ResultSet r = s.executeQuery("SELECT '12.7'"); r.next(); return r.getInt(1); });
        cases.put("getLong from -0.5", () -> { ResultSet r = s.executeQuery("SELECT -0.5"); r.next(); return r.getLong(1); });
        cases.put("column index 0", () -> { ResultSet r = s.executeQuery("SELECT 1"); r.next(); return r.getString(0); });
        cases.put("column index too big", () -> { ResultSet r = s.executeQuery("SELECT 1"); r.next(); return r.getString(2); });
        cases.put("unknown label", () -> { ResultSet r = s.executeQuery("SELECT 1 AS a"); r.next(); return r.getString("b"); });
        for (var e : cases.entrySet()) {
            String v;
            try {
                v = render(e.getValue().get());
            } catch (Throwable t) {
                String chain = "";
                Throwable cause = t.getCause();
                if (cause != null && cause != t) chain = " cause=" + ex(cause);
                v = ex(t) + chain + " | " + clip(msg(t));
            }
            emit("EXC", e.getKey(), v);
        }
    }

    static Object deadlock() throws Exception {
        try (Connection a = open(""); Connection b = open("")) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            a.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 1");
            b.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 3");
            ExecutorService x = Executors.newSingleThreadExecutor();
            Future<Object> fa = x.submit(() -> { try { return a.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 3"); } catch (Throwable t) { return t; } });
            Thread.sleep(400);
            Object rb;
            try { rb = b.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 1"); } catch (Throwable t) { rb = t; }
            b.rollback();
            Object ra = fa.get(10, TimeUnit.SECONDS);
            a.rollback();
            x.shutdownNow();
            Throwable loser = rb instanceof Throwable t ? t : ra instanceof Throwable t ? t : null;
            if (loser == null) return "no deadlock";
            if (loser instanceof Exception e) throw e;
            throw new RuntimeException(loser);
        }
    }


    // ---------- write statements sent through query APIs ----------
    static String lockState(Connection c) throws SQLException {
        try (ResultSet r = c.createStatement().executeQuery("SELECT CONCAT(COUNT(*), ':', COALESCE(SUM(v), 0), ':', (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('zz_dmlq_created', 'zz_dmlq_victim'))) FROM zz_lock")) {
            r.next();
            return r.getString(1);
        }
    }

    static void dmlq(Connection c) throws Exception {
        Statement admin = c.createStatement();
        String[] sqls = {
                "INSERT INTO zz_lock (id, v) VALUES (901, 1)", "UPDATE zz_lock SET v = v + 1 WHERE id = 1", "DELETE FROM zz_lock WHERE id = 3",
                "REPLACE INTO zz_lock (id, v, s) VALUES (902, 1, 'r')", "CREATE TABLE zz_dmlq_created (a int)", "DROP TABLE zz_dmlq_victim", "TRUNCATE TABLE zz_dmlq_victim",
                "ALTER TABLE zz_dmlq_victim ADD COLUMN b int", "RENAME TABLE zz_dmlq_victim TO zz_dmlq_created", "  insert into zz_lock (id, v) values (903, 1)",
                "/* x */ INSERT INTO zz_lock (id, v) VALUES (904, 1)", "-- c\nUPDATE zz_lock SET v = v + 1 WHERE id = 1", "(SELECT 1) UNION (SELECT 2)",
                "INSERT INTO zz_lock (id, v) SELECT 905, 1 FROM DUAL", "WITH w AS (SELECT 1 a) SELECT a FROM w", "SET @zzq = 1", "CALL zz_proc()", "DO SLEEP(0)",
                "GRANT USAGE ON *.* TO 'probe'@'localhost'", "LOAD DATA LOCAL INFILE '/etc/hostname' INTO TABLE zz_lock", "HANDLER zz_lock OPEN", "SELECT 1 INTO @zzq2",
                "SELECT 1 INTO OUTFILE '/tmp/zz_probe_outfile_should_not_exist'", "INSERT INTO zz_lock (id, v) VALUES (906, 1) RETURNING id", "DELETE FROM zz_lock WHERE id = 906 RETURNING id",
                "EXPLAIN DELETE FROM zz_lock", "ANALYZE TABLE zz_lock", "LOCK TABLES zz_lock WRITE", "UNLOCK TABLES", "FLUSH TABLES", "SHOW TABLES LIKE 'zz_lock'", "USE mysql", "USE carlos",
                "BEGIN", "COMMIT", "KILL QUERY 999999999", "SET PASSWORD = PASSWORD('probe')", "PREPARE zzs FROM 'DELETE FROM zz_lock WHERE id = 2'", "EXECUTE zzs"};
        String[] apis = {"Statement.executeQuery", "PreparedStatement.executeQuery", "Statement.execute", "PreparedStatement.execute"};
        for (String api : apis) {
            for (String sql : sqls) {
                // reset fixture state
                try {
                    admin.execute("UNLOCK TABLES");
                    admin.executeUpdate("DELETE FROM zz_lock");
                    admin.executeUpdate("INSERT INTO zz_lock VALUES (1,0,'a'),(2,0,'b'),(3,0,'c')");
                    admin.executeUpdate("DROP TABLE IF EXISTS zz_dmlq_created");
                    admin.executeUpdate("DROP TABLE IF EXISTS zz_dmlq_victim");
                    admin.executeUpdate("CREATE TABLE zz_dmlq_victim (a int)");
                    admin.executeUpdate("INSERT INTO zz_dmlq_victim VALUES (1)");
                    try { admin.execute("DEALLOCATE PREPARE zzs"); } catch (SQLException ignored) { }
                } catch (SQLException e) { emit("DMLQ", "RESET-FAILED " + sql, ex(e)); }
                String before = lockState(c);
                String outcome;
                try (Connection c2 = open("")) {
                    try {
                        switch (api) {
                            case "Statement.executeQuery" -> { try (Statement t = c2.createStatement(); ResultSet r = t.executeQuery(sql)) { outcome = "returned-resultset"; } }
                            case "PreparedStatement.executeQuery" -> { try (PreparedStatement t = c2.prepareStatement(sql); ResultSet r = t.executeQuery()) { outcome = "returned-resultset"; } }
                            case "Statement.execute" -> { try (Statement t = c2.createStatement()) { outcome = "execute=" + t.execute(sql); } }
                            default -> { try (PreparedStatement t = c2.prepareStatement(sql)) { outcome = "execute=" + t.execute(); } }
                        }
                    } catch (Throwable t) {
                        outcome = ex(t);
                    }
                    try { c2.createStatement().execute("UNLOCK TABLES"); } catch (SQLException ignored) { }
                }
                String victim;
                try (ResultSet r = admin.executeQuery("SELECT COUNT(*) FROM zz_dmlq_victim")) { r.next(); victim = r.getString(1); } catch (SQLException e) { victim = "gone"; }
                String after = lockState(c);
                boolean changed = !before.equals(after) || !"1".equals(victim);
                emit("DMLQ", api + ": " + sql.replace("\n", "\\n"), outcome + (changed ? " | DATABASE-CHANGED" : " | unchanged"));
            }
        }
        admin.execute("UNLOCK TABLES");
        admin.executeUpdate("DELETE FROM zz_lock");
        admin.executeUpdate("INSERT INTO zz_lock VALUES (1,0,'a'),(2,0,'b'),(3,0,'c')");
        admin.executeUpdate("DROP TABLE IF EXISTS zz_dmlq_created");
        admin.executeUpdate("DROP TABLE IF EXISTS zz_dmlq_victim");
    }

    // ---------- read-only connection enforcement ----------
    static void readonly(Connection c) throws Exception {
        Statement admin = c.createStatement();
        admin.executeUpdate("DELETE FROM zz_lock");
        admin.executeUpdate("INSERT INTO zz_lock VALUES (1,0,'a'),(2,0,'b'),(3,0,'c')");
        String[][] cases = {
                {"autocommit executeUpdate", "auto", "executeUpdate"}, {"autocommit executeQuery(UPDATE)", "auto", "executeQuery"}, {"autocommit execute(UPDATE)", "auto", "execute"},
                {"tx executeUpdate", "tx", "executeUpdate"}, {"tx executeQuery(UPDATE)", "tx", "executeQuery"}, {"tx execute(UPDATE)", "tx", "execute"},
                {"readOnly set inside open tx, executeUpdate", "late", "executeUpdate"}, {"readOnly set inside open tx, executeQuery(UPDATE)", "late", "executeQuery"},
                {"prepared tx executeUpdate", "tx", "ps.executeUpdate"}, {"prepared tx executeQuery(UPDATE)", "tx", "ps.executeQuery"}, {"tx batch", "tx", "batch"},
                {"tx SELECT FOR UPDATE", "tx", "forupdate"}, {"tx CREATE TEMPORARY TABLE", "tx", "temp"}, {"tx DDL", "tx", "ddl"}};
        for (String[] k : cases) {
            admin.executeUpdate("UPDATE zz_lock SET v = 0");
            String outcome;
            try (Connection c2 = open("")) {
                try {
                    if (k[1].equals("late")) { c2.setAutoCommit(false); c2.createStatement().executeQuery("SELECT * FROM zz_lock").close(); c2.setReadOnly(true); }
                    else { c2.setReadOnly(true); if (k[1].equals("tx")) c2.setAutoCommit(false); }
                    String sql = "UPDATE zz_lock SET v = 9 WHERE id = 1";
                    switch (k[2]) {
                        case "executeUpdate" -> outcome = "count=" + c2.createStatement().executeUpdate(sql);
                        case "executeQuery" -> { c2.createStatement().executeQuery(sql).close(); outcome = "returned-resultset"; }
                        case "execute" -> outcome = "execute=" + c2.createStatement().execute(sql);
                        case "ps.executeUpdate" -> outcome = "count=" + c2.prepareStatement(sql).executeUpdate();
                        case "ps.executeQuery" -> { c2.prepareStatement(sql).executeQuery().close(); outcome = "returned-resultset"; }
                        case "batch" -> { Statement b = c2.createStatement(); b.addBatch(sql); outcome = "batch=" + Arrays.toString(b.executeBatch()); }
                        case "forupdate" -> { c2.createStatement().executeQuery("SELECT * FROM zz_lock WHERE id = 1 FOR UPDATE").close(); outcome = "ok"; }
                        case "temp" -> { c2.createStatement().execute("CREATE TEMPORARY TABLE zz_ro_tmp (a int)"); c2.createStatement().executeUpdate("INSERT INTO zz_ro_tmp VALUES (1)"); outcome = "ok"; }
                        default -> { c2.createStatement().execute("CREATE TABLE zz_ro_ddl (a int)"); outcome = "ok"; }
                    }
                    if (!c2.getAutoCommit()) c2.commit();
                } catch (Throwable t) {
                    outcome = ex(t) + " | " + clip(msg(t));
                    try { if (!c2.getAutoCommit()) c2.rollback(); } catch (SQLException ignored) { }
                }
            }
            String v;
            try (ResultSet r = admin.executeQuery("SELECT v FROM zz_lock WHERE id = 1")) { r.next(); v = r.getString(1); }
            try { admin.executeUpdate("DROP TABLE IF EXISTS zz_ro_ddl"); } catch (SQLException ignored) { }
            emit("READONLY", k[0], outcome + " | row " + ("9".equals(v) ? "WRITTEN" : "unchanged"));
        }
        emit("READONLY", "isReadOnly after setReadOnly(true)", attempt(() -> { try (Connection c2 = open("")) { c2.setReadOnly(true); return c2.isReadOnly(); } }));
        emit("READONLY", "server tx_read_only after setReadOnly(true)", attempt(() -> { try (Connection c2 = open("")) { c2.setReadOnly(true); ResultSet r = c2.createStatement().executeQuery("SELECT @@session.tx_read_only"); r.next(); return r.getString(1); } }));
        emit("READONLY", "server tx_read_only after true then false", attempt(() -> { try (Connection c2 = open("")) { c2.setReadOnly(true); c2.setReadOnly(false); ResultSet r = c2.createStatement().executeQuery("SELECT @@session.tx_read_only"); r.next(); return r.getString(1); } }));
        emit("READONLY", "setReadOnly(true) inside tx", attemptMsg(() -> { try (Connection c2 = open("")) { c2.setAutoCommit(false); c2.createStatement().executeUpdate("UPDATE zz_lock SET v = v WHERE id = 2"); c2.setReadOnly(true); c2.rollback(); return "ok"; } }));
        admin.executeUpdate("UPDATE zz_lock SET v = 0");
    }


    // ---------- legacy form save/load path (FrmRecordHelp semantics over updatable result sets) ----------
    static boolean isBooleanColumnType(String n) {
        return n != null && (n.regionMatches(true, 0, "TINYINT", 0, 7) || "bit".equalsIgnoreCase(n));
    }

    static java.util.Date parseDate(String v, String fmt) {
        if (v == null || v.isEmpty()) return null;
        try { java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(fmt); return f.parse(v); } catch (java.text.ParseException e) { return null; }
    }

    static final long FIXED_NOW = ZonedDateTime.of(2020, 7, 15, 10, 11, 12, 0, ZoneId.of("America/Toronto")).toInstant().toEpochMilli();

    /** Mirrors FrmRecordHelp.updateResultSet. */
    static void updateResultSet(Properties props, ResultSet rs, boolean insert) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String name = md.getColumnName(i);
            if (name.equalsIgnoreCase("ID")) { if (insert) rs.updateInt(name, 0); continue; }
            String value = props.getProperty(name, null);
            String type = md.getColumnTypeName(i);
            if (isBooleanColumnType(type)) {
                rs.updateInt(name, value != null && (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("checked='checked'")) ? 1 : 0);
                continue;
            }
            if (type.equalsIgnoreCase("date")) {
                java.util.Date d = name.equalsIgnoreCase("formEdited") ? new java.util.Date(FIXED_NOW)
                        : (value == null || value.indexOf('/') != -1) ? parseDate(value, "yyyy/MM/dd") : parseDate(value, "yyyy-MM-dd");
                if (d == null) rs.updateNull(name); else rs.updateDate(name, new java.sql.Date(d.getTime()));
                continue;
            }
            if (type.equalsIgnoreCase("timestamp")) {
                java.util.Date d = name.equalsIgnoreCase("formEdited") ? new java.util.Date(FIXED_NOW) : parseDate(value, "yyyyMMddHHmmss");
                if (d == null) rs.updateNull(name); else rs.updateTimestamp(name, new Timestamp(d.getTime()));
                continue;
            }
            if (value == null) rs.updateNull(name); else rs.updateString(name, value);
        }
    }

    /** Mirrors FrmRecordHelp.getFormRecord. */
    static Properties getFormRecord(Connection c, String sql, Object... params) throws SQLException {
        Properties props = new Properties();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData md = rs.getMetaData();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String name = md.getColumnName(i);
                        String value;
                        String type = md.getColumnTypeName(i);
                        try {
                            if (isBooleanColumnType(type)) value = rs.getInt(i) == 1 ? "checked='checked'" : "";
                            else if (type.equalsIgnoreCase("date")) { java.sql.Date d = rs.getDate(i); value = d == null ? null : new java.text.SimpleDateFormat("yyyy/MM/dd").format(d); }
                            else if (type.equalsIgnoreCase("timestamp")) { Timestamp t = rs.getTimestamp(i); value = t == null ? null : new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(t); }
                            else { String v = rs.getString(i); value = v == null ? "" : v.trim(); }
                        } catch (SQLException e) { value = ex(e); }
                        if (value != null) props.setProperty(name, value);
                    }
                }
            }
        }
        return props;
    }

    static Properties formProps(Connection c, String table, int variant) throws SQLException {
        Properties props = new Properties();
        try (ResultSet r = c.createStatement().executeQuery("SELECT column_name, data_type, column_type, is_nullable, IFNULL(character_maximum_length, 0), ordinal_position FROM information_schema.columns WHERE table_schema = '" + DB + "' AND table_name = '" + table + "' ORDER BY ordinal_position")) {
            while (r.next()) {
                String name = r.getString(1), dataType = r.getString(2), columnType = r.getString(3);
                boolean notNull = "NO".equals(r.getString(4));
                long max = r.getLong(5);
                int i = r.getInt(6) + variant;
                String v;
                switch (dataType) {
                    case "tinyint", "bit" -> v = columnType.startsWith("tinyint(1)") || dataType.equals("bit") ? (i % 2 == 0 ? "on" : null) : (i % 3 == 0 ? "7" : i % 3 == 1 ? "" : null);
                    case "date" -> v = i % 3 == 0 ? "2020/01/15" : i % 3 == 1 ? "2020-01-16" : null;
                    case "timestamp" -> v = i % 2 == 0 ? "20200115101112" : null;
                    case "datetime" -> v = i % 3 == 0 ? "2020-01-15 10:11:12" : i % 3 == 1 ? "" : null;
                    case "time" -> v = i % 3 == 0 ? "10:11:12" : i % 3 == 1 ? "" : null;
                    case "int", "smallint", "mediumint", "bigint" -> v = i % 3 == 0 ? "7" : i % 3 == 1 ? "" : null;
                    case "double", "float", "decimal" -> v = i % 3 == 0 ? "1.50" : i % 3 == 1 ? "" : null;
                    case "blob", "mediumblob", "longblob", "tinyblob" -> v = i % 2 == 0 ? "blob text" : null;
                    default -> { String base = i % 5 == 4 ? "téxt ✓ '\"" + i : "v" + i; v = i % 4 == 3 ? null : (max > 0 && base.length() > max ? base.substring(0, (int) max) : base); }
                }
                if (v == null && notNull) v = switch (dataType) { case "date" -> "2020/01/15"; case "timestamp" -> "20200115101112"; case "datetime" -> "2020-01-15 10:11:12"; case "time" -> "10:11:12"; case "int", "smallint", "mediumint", "bigint", "double", "float", "decimal" -> "7"; default -> max == 1 ? "x" : "nn"; };
                if (name.equalsIgnoreCase("demographic_no")) v = "987654";
                if (v != null) props.setProperty(name, v);
            }
        }
        return props;
    }

    static String rawRow(Connection c, String table, long id) throws SQLException {
        List<String> cols = new ArrayList<>();
        try (ResultSet r = c.createStatement().executeQuery("SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = '" + DB + "' AND table_name = '" + table + "' ORDER BY ordinal_position")) {
            while (r.next()) {
                String n = r.getString(1), t = r.getString(2);
                if (n.equalsIgnoreCase("ID")) continue;
                cols.add(t.endsWith("blob") ? "HEX(`" + n + "`)" : t.equals("bit") ? "CAST(`" + n + "` + 0 AS CHAR)" : "CAST(`" + n + "` AS CHAR)");
            }
        }
        StringBuilder sb = new StringBuilder();
        try (ResultSet r = c.createStatement().executeQuery("SELECT CONCAT_WS('|', " + String.join(", ", cols.stream().map(x -> "IFNULL(" + x + ", '<N>')").toList()) + ") FROM `" + table + "` WHERE ID = " + id)) {
            if (!r.next()) return "NO-ROW";
            String v = r.getString(1);
            try { return v.length() + ":" + hex(java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))).substring(0, 16) + ":" + clip(v); } catch (java.security.NoSuchAlgorithmException e) { return v; }
        }
    }

    static void forms(Connection c) throws Exception {
        List<String> tables = new ArrayList<>();
        try (ResultSet r = c.createStatement().executeQuery("SELECT table_name FROM information_schema.columns WHERE table_schema = '" + DB + "' AND column_name = 'ID' AND extra LIKE '%auto_increment%' AND table_name LIKE 'form%' ORDER BY table_name")) {
            while (r.next()) tables.add(r.getString(1));
        }
        emit("FORMS", "tables", String.valueOf(tables.size()));
        for (String t : tables) {
            try { c.createStatement().executeUpdate("DELETE FROM `" + t + "` WHERE demographic_no = 987654"); } catch (SQLException e) { emit("FORMS", t + " cleanup", ex(e)); }
            Properties props = formProps(c, t, 0);
            long id = -1;
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM `" + t + "` WHERE demographic_no = ? AND ID = 0", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                ps.setObject(1, 987654);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.moveToInsertRow();
                    updateResultSet(props, rs, true);
                    rs.insertRow();
                }
                try (ResultSet k = c.createStatement().executeQuery("SELECT LAST_INSERT_ID()")) { k.next(); id = k.getInt(1); }
                emit("FORMS", t + " save", "ok generated=" + (id > 0));
            } catch (Throwable e) {
                emit("FORMS", t + " save", ex(e) + " | " + clip(msg(e)));
                continue;
            }
            final long saved = id;
            emit("FORMS", t + " stored-after-insert", attemptMsg(() -> rawRow(c, t, saved)));
            try {
                Properties loaded = getFormRecord(c, "SELECT * FROM `" + t + "` WHERE demographic_no = ? AND ID = ?", 987654, saved);
                loaded.remove("ID");
                TreeMap<String, String> sorted = new TreeMap<>();
                for (String n : loaded.stringPropertyNames()) sorted.put(n, loaded.getProperty(n));
                String joined = sorted.toString();
                emit("FORMS", t + " loaded", joined.length() + ":" + hex(java.security.MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8))).substring(0, 16));
                for (var e : sorted.entrySet()) emit("FORMS-FIELD", t + "." + e.getKey(), e.getValue());
            } catch (Throwable e) {
                emit("FORMS", t + " loaded", ex(e) + " | " + clip(msg(e)));
            }
            Properties props2 = formProps(c, t, 1);
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM `" + t + "` WHERE demographic_no = ? AND ID = ?", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                ps.setObject(1, 987654);
                ps.setObject(2, saved);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("row not visible to updatable result");
                    updateResultSet(props2, rs, false);
                    rs.updateRow();
                }
                emit("FORMS", t + " update", "ok");
            } catch (Throwable e) {
                emit("FORMS", t + " update", ex(e) + " | " + clip(msg(e)));
            }
            emit("FORMS", t + " stored-after-update", attemptMsg(() -> rawRow(c, t, saved)));
            try { c.createStatement().executeUpdate("DELETE FROM `" + t + "` WHERE demographic_no = 987654"); } catch (SQLException ignored) { }
        }
    }

    // ---------- client-side SQL parsing ----------
    static void parse(Connection c) throws Exception {
        String[] sqls = {
                "SELECT 'what?', ?", "SELECT \"a?\", ?", "SELECT `id`, ? FROM zz_lock WHERE id = 1", "SELECT /* ? */ ?", "SELECT ? -- trailing ?\n", "SELECT ? # trailing ?\n",
                "SELECT 'it\\'s ?', ?", "SELECT 'a''?', ?", "SELECT '\\\\', ?", "SELECT ?, 'x' AS `q?`", "SELECT ?\n,\t1", "SELECT 1 WHERE 1 = ? AND 'a' LIKE 'a?' ESCAPE '|'",
                "SELECT {fn CONCAT('a', ?)}", "SELECT {d '2020-01-15'}, ?", "SELECT {ts '2020-01-15 10:11:12'}, ?", "SELECT {t '10:11:12'}, ?", "SELECT {fn NOW()} IS NOT NULL, ?",
                "SELECT {fn LENGTH('abc')}, ?", "SELECT {fn UCASE('a')}, ?", "SELECT {fn IFNULL(NULL, 1)}, ?", "SELECT {fn CURDATE()} IS NOT NULL, ?", "SELECT '{d ''x''}', ?",
                "SELECT id, ? FROM zz_lock {limit 1}", "SELECT 1 FROM zz_lock a {oj LEFT OUTER JOIN zz_lock b ON a.id = b.id} WHERE a.id = ?", "SELECT ? FROM DUAL;", "SELECT ? ; ",
                "SELECT ?::text", "SELECT :named, ?", "SELECT @v := ?", "SELECT ? ?", "SELECT '?'", "SELECT CONVERT(?, CHAR)", "SELECT ? + ?"};
        for (String sql : sqls) {
            emit("PARSE", "prepared " + sql.replace("\n", "\\n").replace("\t", "\\t"), attemptMsg(() -> {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    int n;
                    try { n = ps.getParameterMetaData().getParameterCount(); } catch (SQLException e) { n = -1; }
                    for (int i = 1; i <= Math.max(n, 1) && i < 5; i++) { try { ps.setString(i, "p" + i); } catch (SQLException e) { break; } }
                    try (ResultSet r = ps.executeQuery()) { r.next(); StringBuilder b = new StringBuilder("params=" + n + " ->"); for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) b.append(' ').append(r.getString(i)); return b.toString(); }
                }
            }));
        }
        String[] plain = {"SELECT {fn CONCAT('a','b')}", "SELECT {d '2020-01-15'}", "SELECT {ts '2020-01-15 10:11:12'}", "SELECT '{d ''2020-01-15''}'", "SELECT {fn NOW()} IS NOT NULL",
                "SELECT id FROM zz_lock {limit 1}", "{call zz_proc()}", "SELECT {fn UCASE('a')}", "SELECT {fn CONVERT('1', SQL_INTEGER)}", "SELECT {fn TIMESTAMPDIFF(SQL_TSI_DAY, {d '2020-01-01'}, {d '2020-01-15'})}",
                "SELECT '{'", "SELECT '{fn x}'", "SELECT 1 /* {fn x} */"};
        for (String sql : plain) {
            emit("PARSE", "statement " + sql, attemptMsg(() -> { try (Statement t = c.createStatement(); ResultSet r = t.executeQuery(sql)) { r.next(); return r.getString(1); } }));
            emit("PARSE", "statement(escape off) " + sql, attemptMsg(() -> { try (Statement t = c.createStatement()) { t.setEscapeProcessing(false); try (ResultSet r = t.executeQuery(sql)) { r.next(); return r.getString(1); } } }));
        }
        emit("PARSE", "string with NUL/backslash roundtrip", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT HEX(?)")) { ps.setString(1, "a\u0000b\\c'd\"e\u001af\n"); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1); } }));
        emit("PARSE", "NO_BACKSLASH_ESCAPES roundtrip", attempt(() -> { try (Connection c2 = open("")) { c2.createStatement().execute("SET SESSION sql_mode='NO_BACKSLASH_ESCAPES'"); try (PreparedStatement ps = c2.prepareStatement("SELECT HEX(?)")) { ps.setString(1, "a\\'b"); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1); } } }));
        emit("PARSE", "emoji roundtrip", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT HEX(?), CHAR_LENGTH(?)")) { ps.setString(1, "\ud83d\ude00"); ps.setString(2, "\ud83d\ude00"); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1) + "/" + r.getString(2); } }));
        emit("PARSE", "bytes param in text context", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT HEX(?), ? = 'ab'")) { ps.setBytes(1, new byte[]{(byte) 0xFF, 0x27, 0x5C, 0x00}); ps.setBytes(2, "ab".getBytes()); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1) + "/" + r.getString(2); } }));
        emit("PARSE", "collation of string param", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT COLLATION(?), CHARSET(?), COERCIBILITY(?)")) { ps.setString(1, "a"); ps.setString(2, "a"); ps.setString(3, "a"); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1) + "/" + r.getString(2) + "/" + r.getString(3); } }));
        emit("PARSE", "collation of literal", attempt(() -> { ResultSet r = c.createStatement().executeQuery("SELECT COLLATION('a'), CHARSET('a'), COLLATION(CONCAT('a', 1)), COLLATION(NOW()), COLLATION(USER())"); r.next(); return r.getString(1) + "/" + r.getString(2) + "/" + r.getString(3) + "/" + r.getString(4) + "/" + r.getString(5); }));
        emit("PARSE", "type of numeric param", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT ? + 0, ? = '5', CONCAT(?, '')")) { ps.setInt(1, 5); ps.setInt(2, 5); ps.setDouble(3, 100d); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1) + "/" + r.getString(2) + "/" + r.getString(3); } }));
        emit("PARSE", "rendering of date params", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT CONCAT(?, ''), CONCAT(?, ''), CONCAT(?, ''), CONCAT(?, ''), CONCAT(?, '')")) { ps.setDate(1, java.sql.Date.valueOf("2020-01-15")); ps.setTimestamp(2, Timestamp.valueOf("2020-01-15 10:11:12.5")); ps.setTime(3, Time.valueOf("10:11:12")); ps.setTimestamp(4, Timestamp.valueOf("2020-01-15 10:11:12")); ps.setBoolean(5, true); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1) + " | " + r.getString(2) + " | " + r.getString(3) + " | " + r.getString(4) + " | " + r.getString(5); } }));
        emit("PARSE", "rendering of float/double/decimal params", attempt(() -> { try (PreparedStatement ps = c.prepareStatement("SELECT CONCAT(?, ''), CONCAT(?, ''), CONCAT(?, ''), CONCAT(?, ''), CONCAT(?, '')")) { ps.setDouble(1, 1e10); ps.setDouble(2, 0.1); ps.setFloat(3, 1.1f); ps.setBigDecimal(4, new BigDecimal("1E+3")); ps.setDouble(5, 1e-7); ResultSet r = ps.executeQuery(); r.next(); return r.getString(1) + " | " + r.getString(2) + " | " + r.getString(3) + " | " + r.getString(4) + " | " + r.getString(5); } }));
    }
}
