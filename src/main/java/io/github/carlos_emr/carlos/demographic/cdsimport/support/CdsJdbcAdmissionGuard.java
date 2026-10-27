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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportUnitService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionState.Lease;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Physical JDBC execution barrier shared by JDBC, ORM and background jobs using the application pool.
 * Shared admission locks live in the same transaction as clinical SQL until its actual commit.
 * Autocommit work is wrapped in a statement transaction; query leases last until results close.
 * Vendor unwrap, callable/DDL and implicit-commit SQL are refused while this barrier is installed.
 */
public final class CdsJdbcAdmissionGuard implements InvocationHandler {
    private static final Set<String> AUTH_READ = Set.of("provider", "security", "securityarchive", "secuserrole",
            "secrole", "secobjectname", "secobjprivilege", "secprivilege", "securitytoken", "facility",
            "program", "program_provider", "provider_default_program", "role", "provider_facility", "providersite", "site", "property",
            "mygroup", "clinic", "resourcestorage", "providerpreference", "providerpreferenceappointmentscreenform",
            "providerpreferenceappointmentscreeneform", "providerpreferenceappointmentscreenquicklink");
    private static final Set<String> AUTH_WRITE = Set.of("security", "securityarchive", "securitytoken", "log");
    private static final Set<String> SCHEMA_METADATA = Set.of("tables", "columns", "statistics", "schemata",
            "key_column_usage", "referential_constraints", "table_constraints", "engines", "collations",
            "character_sets", "collation_character_set_applicability", "system_variables", "session_variables");
    private static final Set<String> LEDGER = Set.of("cds_import_run", "cds_import_patient", "cds_import_unit",
            "cds_import_lineage", "cds_import_checkpoint", "cds_import_field", "cds_import_event",
            "cds_import_diagnostic", "cds_import_admission", "cds_import_dependency", "cds_import_file",
            "cds_import_file_reference", "cds_import_upload", "cds_import_review", "cds_import_preparation", "cds_import_identity");
    private final Connection raw;
    private final CdsJdbcAdmissionState state;
    private final BooleanSupplier importUnit;
    private Connection proxy;
    private boolean gateHeld;
    private boolean readLeaseOnly;
    private boolean implicit;
    private String originalSqlMode;
    private boolean sqlModeChanged;
    private int openResults;
    private final Map<ResultSet, Object> resultProxies = new IdentityHashMap<>();

    private CdsJdbcAdmissionGuard(Connection raw, CdsJdbcAdmissionState state, BooleanSupplier importUnit) {
        this.raw = raw; this.state = state; this.importUnit = importUnit;
    }

    public static Connection wrap(Connection raw, CdsJdbcAdmissionState state) {
        return wrap(raw, state, CdsImportUnitService::isActiveForMaintenance);
    }

    /** Only new CDS metadata mutations are scoped when clinical maintenance admission is off. */
    public static Connection wrapMetadataOnly(Connection raw) {
        return wrap(raw, null, CdsImportUnitService::isActiveForMaintenance);
    }

    /** Explicit capability supplier is used by real-database race tests, never by request binding. */
    public static Connection wrap(Connection raw, CdsJdbcAdmissionState state, BooleanSupplier importUnit) {
        CdsJdbcAdmissionGuard guard = new CdsJdbcAdmissionGuard(raw, state, importUnit);
        guard.proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, guard);
        return guard.proxy;
    }

    @Override public Object invoke(Object target, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("unwrap")) {
            Class<?> requested=(Class<?>) args[0];
            return state==null && !requested.isInstance(proxy) ? raw.unwrap(requested) : safeUnwrap(proxy, requested);
        }
        if (name.equals("isWrapperFor")) return ((Class<?>) args[0]).isInstance(proxy) || state==null && raw.isWrapperFor((Class<?>) args[0]);
        if (name.equals("getAutoCommit") && implicit) return true;
        if (name.equals("setSavepoint") && implicit) throw refused("Savepoints require an explicit caller transaction");
        if (name.equals("setAutoCommit") && Boolean.FALSE.equals(args[0]) && implicit) {
            if (openResults != 0) throw refused("Close autocommit query results before beginning a transaction");
            finishImplicit();
        }
        if (name.equals("commit") || name.equals("rollback") || name.equals("setAutoCommit") || name.equals("close") || name.equals("abort")) {
            boolean fullBoundary=name.equals("commit") || name.equals("rollback") && (args==null || args.length==0)
                    || name.equals("setAutoCommit") && Boolean.TRUE.equals(args[0]);
            boolean closing=name.equals("close") || name.equals("abort");
            try {
                if (name.equals("close")) {
                    if (implicit || originalSqlMode!=null) raw.rollback();
                    restoreImportSession(false); // Before DBCP can return this physical connection.
                }
                Object result = call(raw, method, args);
                if (fullBoundary) restoreImportSession(name.equals("commit") || name.equals("setAutoCommit"));
                if (name.equals("commit") || name.equals("rollback") || closing
                        || name.equals("setAutoCommit") && Boolean.TRUE.equals(args[0])) {
                    boolean restore = implicit && !closing && !name.equals("setAutoCommit");
                    reset();
                    if (name.equals("abort")) { originalSqlMode=null; sqlModeChanged=false; }
                    if (restore) raw.setAutoCommit(true);
                }
                return result;
            } catch (Throwable failure) {
                if (fullBoundary || closing) {
                    try { restoreImportSession(fullBoundary && !name.equals("rollback")); }
                    catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                }
                if (closing) reset();
                throw failure;
            }
        }
        if (name.equals("prepareCall") && state!=null) throw refused("Callable SQL requires an explicitly reviewed maintenance adapter");
        if (name.equals("prepareStatement") && args!=null && args.length>0 && args[0] instanceof String sql) {
            try {
                SqlShape prepared=shape(sql);
                boolean nativeUnit=importUnit.getAsBoolean();
                if (nativeUnit || prepared.importMutation()) ensureStrictSession(nativeUnit);
                return statement((Statement) call(raw, method, args), sql);
            } catch (Throwable failure) { abortImplicit(failure); throw failure; }
        }
        Object result = call(raw, method, args);
        if (result instanceof Statement statement) {
            String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
            return statement(statement, sql);
        }
        if (result instanceof DatabaseMetaData metadata) {
            return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                    (ignored, operation, values) -> operation.getName().equals("getConnection") ? proxy
                            : operation.getName().equals("unwrap") ? guardedUnwrap(ignored, metadata, (Class<?>) values[0])
                            : operation.getName().equals("isWrapperFor") ? ((Class<?>) values[0]).isInstance(ignored) || state==null && metadata.isWrapperFor((Class<?>) values[0])
                            : metadataResult(metadata, operation, values));
        }
        return result;
    }

    private Object statement(Statement rawStatement, String preparedSql) {
        Class<?> type = rawStatement instanceof CallableStatement ? CallableStatement.class
                : rawStatement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        List<String> batch = new ArrayList<>();
        Object[] wrapper = new Object[1];
        wrapper[0] = Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{type}, (ignored, operation, values) -> {
            String name = operation.getName();
            if (name.equals("getConnection")) return proxy;
            if (name.equals("unwrap")) {
                Class<?> requested=(Class<?>) values[0];
                return state==null && !requested.isInstance(wrapper[0]) ? rawStatement.unwrap(requested) : safeUnwrap(wrapper[0], requested);
            }
            if (name.equals("isWrapperFor")) return ((Class<?>) values[0]).isInstance(wrapper[0]) || state==null && rawStatement.isWrapperFor((Class<?>) values[0]);
            if (name.equals("addBatch") && preparedSql == null) batch.add((String) values[0]);
            if (name.equals("clearBatch")) batch.clear();
            if (name.startsWith("execute")) {
                String sql = preparedSql != null ? preparedSql
                        : values != null && values.length > 0 && values[0] instanceof String text ? text : null;
                SqlShape shape = sql == null ? new SqlShape(false, false, false) : shape(sql);
                boolean clinical = shape.clinical();
                boolean readOnly = shape.readOnly();
                boolean importMutation = shape.importMutation();
                if (name.equals("executeBatch") || name.equals("executeLargeBatch")) {
                    for (String member : batch) {
                        SqlShape item=shape(member); clinical |= item.clinical(); importMutation |= item.importMutation();
                    }
                    readOnly = false;
                }
                try {
                    boolean nativeUnit=importUnit.getAsBoolean();
                    if (nativeUnit || importMutation) ensureStrictSession(nativeUnit);
                    if (clinical && state!=null) admit(readOnly);
                    Object result = call(rawStatement, operation, values);
                    if (result instanceof ResultSet rows) return resultSet(rows, wrapper[0]);
                    if (result instanceof Boolean hasResult && hasResult) {
                        resultSet(rawStatement.getResultSet(), wrapper[0]);
                    } else finishImplicit();
                    return result;
                } catch (Throwable failure) {
                    abortImplicit(failure);
                    throw failure;
                } finally { if (name.equals("executeBatch") || name.equals("executeLargeBatch")) batch.clear(); }
            }
            Object result = call(rawStatement, operation, values);
            if (result instanceof ResultSet rows) return resultSet(rows, wrapper[0]);
            if (name.equals("close")) {
                // Driver closes its results, but does not call our result wrappers.
                List<ResultSet> closed = new ArrayList<>();
                for (ResultSet rows : resultProxies.keySet()) if (rows.isClosed()) closed.add(rows);
                for (ResultSet rows : closed) releaseResult(rows);
                finishImplicit();
            }
            return result;
        });
        return wrapper[0];
    }

    private Object resultSet(ResultSet rawRows, Object statement) throws SQLException {
        if (rawRows == null) return null;
        Object existing = resultProxies.get(rawRows);
        if (existing != null) return existing;
        boolean counted = implicit;
        if (counted) openResults++;
        Object wrapper = Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (ignored, operation, values) -> {
                    String name = operation.getName();
                    if (name.equals("getStatement")) return statement;
                    if (name.equals("unwrap")) return guardedUnwrap(ignored, rawRows, (Class<?>) values[0]);
                    if (name.equals("isWrapperFor")) return ((Class<?>) values[0]).isInstance(ignored) || state==null && rawRows.isWrapperFor((Class<?>) values[0]);
                    try {
                        Object result = call(rawRows, operation, values);
                        if (counted && (name.equals("close") || name.equals("next") && Boolean.FALSE.equals(result))) {
                            releaseResult(rawRows); finishImplicit();
                        }
                        return result;
                    } catch (Throwable failure) {
                        if (implicit) {
                            try { raw.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                            try { restoreImportSession(false); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                            reset();
                            try { raw.setAutoCommit(true); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                        }
                        throw failure;
                    }
                });
        resultProxies.put(rawRows, wrapper);
        return wrapper;
    }

    private void releaseResult(ResultSet rows) {
        if (resultProxies.remove(rows) != null && implicit && openResults > 0) openResults--;
    }

    private void admit(boolean readOnly) throws SQLException {
        if (gateHeld) {
            if (readLeaseOnly) {
                if (importUnit.getAsBoolean()) readLeaseOnly = false;
                else if (!readOnly || !state.hasReadScope(state.owner())) {
                    throw refused("A maintenance read capability cannot write or escape its bounded scope");
                }
            }
            return;
        }
        boolean activeImport = importUnit.getAsBoolean();
        Lease local = state.owner();
        boolean ownerRead = readOnly && state.hasReadScope(local);
        // Refuse new ordinary work during the drain instead of waiting behind the exclusive waiter.
        try (PreparedStatement check = raw.prepareStatement("SELECT IS_USED_LOCK(?)")) {
            check.setQueryTimeout(3); check.setString(1, state.lockName());
            try (ResultSet rows = check.executeQuery()) {
                rows.next(); long owner = rows.getLong(1); boolean free = rows.wasNull();
                if (!free && (!(activeImport || ownerRead) || local == null || local.connectionId() != owner)) {
                    throw refused("CDS maintenance is entering or active; clinical work requires retry after reopening");
                }
            }
        }
        boolean auto = raw.getAutoCommit();
        if (auto) { raw.setAutoCommit(false); implicit = true; }
        try (PreparedStatement gate = raw.prepareStatement("""
                SELECT maintenance_active, maintenance_owner_token, maintenance_generation, maintenance_connection_id,
                    IS_USED_LOCK(?), EXISTS (SELECT 1 FROM cds_import_patient WHERE held=TRUE)
                FROM cds_import_admission WHERE id=? LOCK IN SHARE MODE
                """)) {
            gate.setQueryTimeout(3); gate.setString(1, state.lockName()); gate.setString(2, CdsJdbcAdmissionState.ROW);
            try (ResultSet rows = gate.executeQuery()) {
                if (!rows.next()) throw refused("CDS maintenance admission row is missing");
                boolean held = rows.getBoolean(1) || rows.getBoolean(6);
                if (activeImport || ownerRead) {
                    if (!rows.getBoolean(1) || local == null || !local.token().equals(rows.getString(2))
                            || local.generation() != rows.getLong(3) || local.connectionId() != rows.getLong(4)
                            || local.connectionId() != rows.getLong(5) || rows.wasNull()) {
                        throw refused("CDS maintenance owner is absent or fenced");
                    }
                } else if (held) throw refused("CDS maintenance or an incomplete chart hold blocks clinical access");
                gateHeld = true;
                readLeaseOnly = ownerRead && !activeImport;
            }
        } catch (SQLException failure) {
            if (implicit) {
                try { raw.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                reset();
                try { raw.setAutoCommit(true); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    /** Import-only session scope survives removal of the thread marker until physical completion. */
    private void ensureStrictSession(boolean nativeUnit) throws SQLException {
        if (nativeUnit) {
            if (state==null || !state.hasVerifiedCatalogue(state.owner())) {
                throw refused("CDS native target catalogue must be verified under the current maintenance owner before unit writes");
            }
            admit(false); // Native scope still requires current physical ownership and admission.
        } else if (raw.getAutoCommit()) {
            raw.setAutoCommit(false); implicit=true; // Metadata-only autocommit mutation owns its statement transaction.
        }
        if (originalSqlMode!=null) return;
        // SET NAMES can spoof server variables while a latin1 JDBC encoder still replaces Unicode
        // with '?'. Verify the actual shipped driver's parameter encoder as well as the session.
        if (!raw.isWrapperFor(com.mysql.cj.jdbc.JdbcConnection.class)) {
            throw refused("CDS import requires the verified UTF-8 JDBC encoder and utf8mb4 session charsets");
        }
        String encoder=raw.unwrap(com.mysql.cj.jdbc.JdbcConnection.class).getPropertySet()
                .getStringProperty(com.mysql.cj.conf.PropertyKey.characterEncoding).getValue();
        try {
            if (encoder==null || !java.nio.charset.Charset.forName(encoder).equals(java.nio.charset.StandardCharsets.UTF_8)) {
                throw refused("CDS import requires the verified UTF-8 JDBC encoder and utf8mb4 session charsets");
            }
        } catch (IllegalArgumentException unsupported) {
            throw refused("CDS import JDBC parameter encoder cannot be verified");
        }
        try (Statement query=raw.createStatement(); ResultSet rows=query.executeQuery(
                "SELECT @@SESSION.sql_mode,@@SESSION.character_set_client,@@SESSION.character_set_connection,@@SESSION.character_set_results")) {
            if (!rows.next() || !"utf8mb4".equalsIgnoreCase(rows.getString(2))
                    || !"utf8mb4".equalsIgnoreCase(rows.getString(3))
                    || rows.getString(4)!=null && !"utf8mb4".equalsIgnoreCase(rows.getString(4))) {
                throw refused("CDS import requires utf8mb4 session charsets before storing import values");
            }
            originalSqlMode=rows.getString(1);
            if (originalSqlMode==null) throw refused("CDS import SQL mode is unavailable");
        }
        boolean strict=java.util.Arrays.stream(originalSqlMode.split(","))
                .anyMatch(mode-> mode.trim().equalsIgnoreCase("STRICT_ALL_TABLES"));
        if (!strict) {
            sqlModeChanged=true; // A lost SET acknowledgement must also restore or discard the connection.
            setSqlMode(originalSqlMode.isBlank() ? "STRICT_ALL_TABLES" : originalSqlMode+",STRICT_ALL_TABLES");
        }
    }

    private void abortImplicit(Throwable failure) {
        if (!implicit) return;
        try { raw.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
        try { restoreImportSession(false); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
        reset();
        try { raw.setAutoCommit(true); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
    }

    private void setSqlMode(String value) throws SQLException {
        try (PreparedStatement statement=raw.prepareStatement("SET SESSION sql_mode=?")) {
            statement.setQueryTimeout(3); statement.setString(1, value); statement.executeUpdate();
        }
    }

    private void restoreImportSession(boolean commitMayHaveCompleted) throws SQLException {
        if (originalSqlMode==null) return;
        String previous=originalSqlMode; boolean changed=sqlModeChanged;
        originalSqlMode=null; sqlModeChanged=false;
        if (!changed) return;
        try { setSqlMode(previous); }
        catch (SQLException failure) {
            // A connection with uncertain session state must never be reused by an ordinary writer.
            try { raw.abort(Runnable::run); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw new SQLException(commitMayHaveCompleted
                    ? "Import session restoration failed; commit acknowledgement requires reconciliation"
                    : "Import session restoration failed; physical connection discarded",
                    commitMayHaveCompleted ? "08007" : "08006", failure);
        }
    }

    private void finishImplicit() throws SQLException {
        if (implicit && openResults == 0) {
            try { raw.commit(); restoreImportSession(true); }
            catch (SQLException failure) {
                try { raw.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                try { restoreImportSession(false); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                try { raw.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                reset(); throw failure;
            }
            reset(); raw.setAutoCommit(true);
        }
    }
    private void reset() { gateHeld = false; readLeaseOnly = false; implicit = false; openResults = 0; resultProxies.clear(); }

    /** Exact administrative metadata names also form part of the native target startup catalogue. */
    public static Set<String> administrativeTables() { return LEDGER; }

    /** Conservative table classification; administrative SQL cannot smuggle a clinical-table join. */
    static boolean clinical(String sql) throws SQLException {
        return classify(sql).clinical();
    }
    private record SqlShape(boolean clinical, boolean readOnly, boolean importMutation) { }
    private SqlShape shape(String sql) throws SQLException {
        // A pre-maintenance metadata guard must not reinterpret or restrict ordinary legacy SQL.
        if (state==null && !sql.toLowerCase(Locale.ROOT).contains("cds_import_")) return new SqlShape(false, false, false);
        return classify(sql);
    }
    private static boolean importTable(net.sf.jsqlparser.schema.Table table) {
        if (table==null) return false;
        String name=table.getName().replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
        return name.startsWith("cds_import_");
    }
    private static SqlShape classify(String sql) throws SQLException {
        String normalized = stripTerminalSemicolons(sql);
        // Hibernate's MariaDB PESSIMISTIC_READ spelling is not supported by JSQLParser.
        // Normalize only the terminal lock clause for classification; execute the original
        // SQL unchanged, including its real shared row lock and transaction lifetime.
        String parserSql = normalizeSharedLock(normalized);
        // parse() accepts a leading statement and can ignore a trailing COMMIT. Count the
        // complete stream before even administrative/metadata exemptions are considered.
        final net.sf.jsqlparser.statement.Statement parsed;
        try {
            var statements=CCJSqlParserUtil.parseStatements(parserSql).getStatements();
            if (statements.size()!=1) throw refused("Only one SQL statement may use a CDS admission lease");
            parsed=statements.getFirst();
        } catch (JSQLParserException | RuntimeException unsupported) {
            throw refused("SQL cannot be admitted safely by the CDS maintenance guard");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.matches("SELECT\\s+(?:1|VERSION\\(\\)|DATABASE\\(\\)|CURRENT_USER\\(\\)|CONNECTION_ID\\(\\)|@@[A-Z0-9_.,@ ]+)")) return new SqlShape(false, true, false);
        if (upper.matches("(?:DESCRIBE|DESC)\\s+.*")) return new SqlShape(false, true, false);
        if (upper.startsWith("SHOW ")) {
            boolean metadata=upper.matches("SHOW\\s+(?:(?:GLOBAL|SESSION|FULL)\\s+)?(?:TABLES|COLUMNS|FIELDS|INDEX|INDEXES|KEYS|VARIABLES|STATUS|ENGINES|COLLATION|CHARACTER SET|CREATE TABLE)\\b.*");
            return new SqlShape(!metadata, true, false); // PROCESSLIST/binlog output can contain patient SQL/data.
        }
        if (upper.matches("SET\\s+(?:SESSION\\s+)?TRANSACTION\\s+ISOLATION\\s+LEVEL\\s+(?:READ COMMITTED|READ UNCOMMITTED|REPEATABLE READ|SERIALIZABLE)")) return new SqlShape(false, false, false);
        try {
            boolean read = parsed instanceof Select;
            if (!read && !(parsed instanceof Insert) && !(parsed instanceof Update) && !(parsed instanceof Delete)) {
                throw refused("Implicit-commit, routine or unsupported SQL requires a reviewed maintenance adapter");
            }
            boolean importMutation=parsed instanceof Insert insert && importTable(insert.getTable())
                    || parsed instanceof Update update && importTable(update.getTable())
                    || parsed instanceof Delete delete && importTable(delete.getTable());
            class Functions extends TablesNamesFinder<Void> {
                private boolean unsupported;
                @Override public <S> Void visit(PlainSelect select, S context) {
                    if (select.getIntoTempTable() != null || select.getIntoTables() != null && !select.getIntoTables().isEmpty()) {
                        unsupported = true;
                    }
                    return super.visit(select, context);
                }
                @Override public <S> Void visit(Function function, S context) {
                    String name = function.getName().toLowerCase(Locale.ROOT);
                    if (function.getMultipartName() == null || function.getMultipartName().size() != 1
                            || !(io.github.carlos_emr.carlos.report.data.QueryByExampleSqlValidator.isKnownReadOnlyBuiltin(name)
                                || Set.of("last_insert_id", "uuid", "uuid_short", "connection_id", "database", "version",
                                    "current_user", "get_lock", "release_lock", "is_used_lock", "is_free_lock").contains(name))) {
                        unsupported = true;
                    }
                    return super.visit(function, context);
                }
            }
            Functions inspector = new Functions();
            Set<String> tables = inspector.getTables(parsed);
            if (inspector.unsupported) throw refused("Stored or unsupported SQL functions require a reviewed maintenance adapter");
            if (tables.isEmpty()) return new SqlShape(true, read, importMutation);
            for (String table : tables) {
                String full = table.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
                if (read && full.startsWith("information_schema.")
                        && SCHEMA_METADATA.contains(full.substring("information_schema.".length()))) continue;
                String name = full.substring(full.lastIndexOf('.') + 1);
                if (LEDGER.contains(name)) continue;
                if (read && (AUTH_READ.contains(name) || name.equals("flyway_schema_history"))) continue;
                if (!read && AUTH_WRITE.contains(name)) continue;
                return new SqlShape(true, read, importMutation);
            }
            return new SqlShape(false, read, importMutation);
        } catch (RuntimeException unsupported) {
            throw refused("SQL cannot be admitted safely by the CDS maintenance guard");
        }
    }

    /** Preserve strip/terminal-semicolon semantics without retrying a regex at every semicolon. */
    static String stripTerminalSemicolons(String sql) {
        String stripped = sql.strip();
        int end = stripped.length();
        while (end > 0 && stripped.charAt(end - 1) == ';') end--;
        return end == stripped.length() ? stripped : stripped.substring(0, end);
    }

    /** Only the fixed terminal MariaDB clause is rewritten, in one backwards scan. */
    static String normalizeSharedLock(String sql) {
        int end = sql.length();
        while (end > 0 && sqlWhitespace(sql.charAt(end - 1))) end--;
        for (String word : new String[] {"MODE", "SHARE", "IN", "LOCK"}) {
            int start = end - word.length();
            if (start < 0) return sql;
            for (int i = 0; i < word.length(); i++) {
                char actual = sql.charAt(start + i);
                if (actual >= 'a' && actual <= 'z') actual -= 'a' - 'A';
                if (actual != word.charAt(i)) return sql;
            }
            end = start;
            while (end > 0 && sqlWhitespace(sql.charAt(end - 1))) end--;
            if (end == start) return sql; // Each word, including LOCK, needs preceding whitespace.
        }
        return sql.substring(0, end) + " FOR SHARE";
    }

    private static boolean sqlWhitespace(char value) {
        // Java Pattern's default \\s is ASCII; Unicode folding would broaden accepted syntax.
        return value == ' ' || value == '\t' || value == '\n' || value == '\u000b'
                || value == '\f' || value == '\r';
    }

    private Object guardedUnwrap(Object guarded, java.sql.Wrapper delegate, Class<?> type) throws SQLException {
        return state==null && !type.isInstance(guarded) ? delegate.unwrap(type) : safeUnwrap(guarded, type);
    }
    private static Object safeUnwrap(Object proxy, Class<?> type) throws SQLException {
        if (type.isInstance(proxy)) return proxy;
        throw refused("Raw JDBC unwrap is unavailable while the CDS maintenance guard is enabled");
    }
    private Object metadataResult(DatabaseMetaData metadata, Method method, Object[] args) throws Throwable {
        Object result = call(metadata, method, args);
        return result instanceof ResultSet rows ? resultSet(rows, null) : result;
    }
    private static SQLException refused(String message) { return new SQLException(message, "45000"); }
    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
