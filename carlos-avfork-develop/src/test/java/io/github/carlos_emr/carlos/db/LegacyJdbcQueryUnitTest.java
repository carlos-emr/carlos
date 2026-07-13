/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Regression tests for the legacy JDBC transition boundary.
 *
 * @since 2026-05-07
 */
@Tag("unit")
@Tag("database")
@DisplayName("LegacyJdbcQuery transition boundary")
class LegacyJdbcQueryUnitTest extends CarlosUnitTestBase {

    @AfterEach
    void releaseLegacyJdbcResources() {
        LegacyJdbcQuery.releaseThreadResources();
    }

    @Test
    @DisplayName("shouldAllowSelectOnlyQueries_forAdminReportBoundary")
    void shouldAllowSelectOnlyQueries_forAdminReportBoundary() {
        assertThatCode(() -> validateSafeSelectQuery("select demographic_no from demographic"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("shouldCreateTrustedSql_forValidatedSelectOnlyQueries")
    void shouldCreateTrustedSql_forValidatedSelectOnlyQueries() {
        assertThatCode(() -> LegacyJdbcQuery.trustedSelectSql("select demographic_no from demographic"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("shouldRejectUnsafeQueries_forTrustedSql")
    void shouldRejectUnsafeQueries_forTrustedSql() {
        assertThatThrownBy(() -> LegacyJdbcQuery.trustedSelectSql("select * from demographic; drop table provider"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("shouldAllowUnion_forTrustedReportSql")
    void shouldAllowUnion_forTrustedReportSql() {
        assertThatCode(() -> LegacyJdbcQuery.trustedReportSelectSql(
                "select demographic_no from demographic union select demographic_no from provider"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("shouldRejectStatementSeparator_forTrustedReportSql")
    void shouldRejectStatementSeparator_forTrustedReportSql() {
        assertThatThrownBy(() -> LegacyJdbcQuery.trustedReportSelectSql(
                "select demographic_no from demographic; select provider_no from provider"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("should reject unsafe parameterized report SQL before JDBC")
    void shouldRejectUnsafeParameterizedReportSql_beforeJdbcBoundary() {
        ParameterizedSql query = new ParameterizedSql(
                "select demographic_no from demographic; drop table provider",
                List.of());

        assertThatThrownBy(() -> LegacyJdbcQuery.getPreparedResultSet(query))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Unsafe SQL detected");
    }

    @Test
    @DisplayName("should allow trailing semicolon and semicolon literals for report SQL")
    void shouldAllowTrailingSemicolonAndSemicolonLiterals_forTrustedReportSql() {
        assertThatCode(() -> LegacyJdbcQuery.trustedReportSelectSql(
                "select group_concat(name separator ';') from provider;"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject SQL comments outside quoted literals")
    void shouldRejectSqlCommentsOutsideQuotedLiterals_forTrustedReportSql() {
        assertThatCode(() -> LegacyJdbcQuery.trustedReportSelectSql(
                "select '-- not a comment' as value from demographic"))
                .doesNotThrowAnyException();
        assertThatCode(() -> LegacyJdbcQuery.trustedReportSelectSql(
                "select '#' as value from demographic"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> LegacyJdbcQuery.trustedReportSelectSql(
                "select demographic_no from demographic -- comment"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> LegacyJdbcQuery.trustedReportSelectSql(
                "select demographic_no from demographic # comment"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("shouldRejectUnsafeQueries_forAdminReportBoundary")
    void shouldRejectUnsafeQueries_forAdminReportBoundary() {
        assertThatThrownBy(() -> validateSafeSelectQuery("select * from demographic UnIoN select * from provider"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNION");
    }

    @Test
    @DisplayName("shouldRejectBlockedPatterns_forAdminReportBoundary")
    void shouldRejectBlockedPatterns_forAdminReportBoundary() {
        List<String> unsafeSql = List.of(
                "select * from demographic; select * from provider",
                "select * from demographic -- comment",
                "select * from demographic /* comment */",
                "selective * from demographic",
                "select * from demographic where last_name = 'x' or '1'='1'",
                "select * from demographic where last_name = \"x\" or \"1\"=\"1\"",
                "select * from demographic where 1 = 1 or 1=1",
                "select * from demographic insert into provider values (1)",
                "select * from demographic update provider set last_name = 'x'",
                "select * from demographic delete from provider",
                "select * from demographic drop table provider",
                "select * from demographic alter table provider",
                "select * from demographic create table x",
                "select * from demographic truncate table provider",
                "select * from demographic grant all on provider to user",
                "select * from demographic revoke all on provider from user",
                "select * from demographic exec sp_test",
                "select * from demographic execute sp_test",
                "select * from demographic call sp_test",
                "select * from demographic merge into provider",
                "select * from demographic commit",
                "select * from demographic rollback",
                "select * from demographic into outfile '/tmp/out'",
                "select load_file('/tmp/secret')",
                "select * from demographic load data infile '/tmp/in'");

        for (String sql : unsafeSql) {
            assertThatThrownBy(() -> validateSafeSelectQuery(sql))
                    .as(sql)
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("shouldRejectDirectSqlExecutionBoundary")
    void shouldReject_directSqlExecutionBoundary() {
        assertThatThrownBy(() -> LegacyJdbcQuery.queryResults("select demographic_no from demographic"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Direct SQL execution is disabled");
    }

    @Test
    @DisplayName("shouldAvoidDeprecatedHandlers_inProductionCallers")
    void shouldAvoidDeprecatedHandlers_inProductionCallers() throws Exception {
        List<Path> offenders = new ArrayList<>();
        for (Path sourceRoot : List.of(Path.of("src", "main", "java"), Path.of("src", "main", "webapp"))) {
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                offenders.addAll(files
                    .filter(this::isProductionSourceFile)
                    .filter(path -> !path.endsWith(Path.of("db", "DBHandler.java")))
                    .filter(path -> !path.endsWith(Path.of("db", "DBPreparedHandler.java")))
                    .filter(path -> !path.endsWith(Path.of("db", "DBPreparedHandlerParam.java")))
                    .filter(this::usesDeprecatedDatabaseBoundary)
                    .toList());
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("shouldAvoidRawEFormUtilSqlOverloads_inProductionCallers")
    void shouldAvoidRawEFormUtilSqlOverloads_inProductionCallers() throws Exception {
        List<Path> offenders = new ArrayList<>();
        for (Path sourceRoot : List.of(Path.of("src", "main", "java"), Path.of("src", "main", "webapp"))) {
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                offenders.addAll(files
                    .filter(this::isProductionSourceFile)
                    .filter(path -> !path.endsWith(Path.of("eform", "EFormUtil.java")))
                    .filter(this::usesRawEFormUtilSqlOverload)
                    .toList());
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("shouldDisallowRawAdminSqlBoundary_inProductionCallers")
    void shouldDisallowRawAdminSqlBoundary_inProductionCallers() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("db", "LegacyJdbcQuery.java")))
                    .filter(path -> !path.endsWith(Path.of("db", "DBPreparedHandler.java")))
                    .filter(this::usesRawAdminSqlBoundary)
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    @Test
    @DisplayName("should ignore unregister when no thread resources are tracked")
    void shouldIgnoreUnregister_whenNoThreadResourcesAreTracked() {
        LegacyJdbcQuery.releaseThreadResources();
        AtomicBoolean closed = new AtomicBoolean(false);
        AutoCloseable resource = () -> closed.set(true);

        assertThatCode(() -> LegacyJdbcQuery.unregisterThreadResource(resource)).doesNotThrowAnyException();
        assertThatCode(LegacyJdbcQuery::releaseThreadResources).doesNotThrowAnyException();
        assertThat(closed).isFalse();
    }

    @Test
    @DisplayName("should release acquired connection when getConnection registration fails")
    void shouldReleaseAcquiredConnection_whenGetConnectionRegistrationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<Connection> connections = mockConnections(51);
        when(dataSource.getConnection()).thenReturn(connections.get(0),
                connections.subList(1, connections.size()).toArray(new Connection[0]));
        registerMock(DataSource.class, dataSource);

        for (int i = 0; i < 50; i++) {
            assertThatCode(LegacyJdbcQuery::getConnection).doesNotThrowAnyException();
        }

        Connection overflowConnection = connections.get(50);
        assertThatThrownBy(LegacyJdbcQuery::getConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resource limit exceeded");
        verify(overflowConnection).close();
    }

    @Test
    @DisplayName("should release acquired connection when procExecute registration fails")
    void shouldReleaseAcquiredConnection_whenProcExecuteRegistrationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<Connection> connections = mockConnections(51);
        when(dataSource.getConnection()).thenReturn(connections.get(0),
                connections.subList(1, connections.size()).toArray(new Connection[0]));
        registerMock(DataSource.class, dataSource);

        for (int i = 0; i < 50; i++) {
            assertThatCode(LegacyJdbcQuery::getConnection).doesNotThrowAnyException();
        }

        Connection overflowConnection = connections.get(50);
        assertThatThrownBy(() -> LegacyJdbcQuery.procExecute("test_proc", new String[] {"value"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resource limit exceeded");
        verify(overflowConnection).close();
        verify(overflowConnection, never()).prepareCall(anyString());
    }

    @Test
    @DisplayName("should close CAISI result set and statement together")
    void shouldClose_caisiResultSetAndStatementTogether() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet releasingResultSet = StatementClosingResultSet.wrap(rs, ps);

        try (LegacyJdbcQuery.CaisiResult ignored = new LegacyJdbcQuery.CaisiResult(releasingResultSet)) {
            // Resource ownership belongs to the holder.
        }

        InOrder order = inOrder(rs, ps);
        order.verify(rs).close();
        order.verify(ps).close();
    }

    @Test
    @DisplayName("should not expose CAISI statement")
    void shouldNotExpose_caisiStatement() {
        assertThat(LegacyJdbcQuery.CaisiResult.class.getMethods())
                .noneMatch(method -> "statement".equals(method.getName()));
    }

    private List<Connection> mockConnections(int count) {
        List<Connection> connections = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            connections.add(mock(Connection.class));
        }
        return connections;
    }

    private boolean usesDeprecatedDatabaseBoundary(Path path) {
        try {
            String content = Files.readString(path);
            // Raw text is intentional: production comments, strings, and JavaDoc that
            // mention removed APIs should be updated to LegacyJdbcQuery terminology.
            return content.contains("DBHandler.GetPreSQL")
                    || content.contains("new DBPreparedHandler(")
                    || content.contains("DbConnectionFilter.getThreadLocalDbConnection()");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private boolean isProductionSourceFile(Path path) {
        String fileName = path.toString();
        return fileName.endsWith(".java") || fileName.endsWith(".jsp") || fileName.endsWith(".jspf");
    }

    private boolean usesRawEFormUtilSqlOverload(Path path) {
        try {
            String content = Files.readString(path);
            int start = 0;
            while ((start = findNextEFormUtilValueCall(content, start)) >= 0) {
                int openParen = content.indexOf('(', start);
                int closeParen = findClosingParen(content, openParen);
                if (closeParen < 0) {
                    return true;
                }
                List<String> arguments = topLevelArguments(content, openParen + 1, closeParen);
                if (arguments.size() == 2 && !isParameterizedSqlArgument(content, start, arguments.get(1))) {
                    return true;
                }
                start = closeParen + 1;
            }
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private int findNextEFormUtilValueCall(String content, int start) {
        ParseState state = ParseState.CODE;
        for (int i = start; i < content.length(); i++) {
            if (state == ParseState.CODE
                    && (content.startsWith("EFormUtil.getValues(", i)
                            || content.startsWith("EFormUtil.getJsonValues(", i))) {
                return i;
            }
            state = nextState(state, content, i, content.length());
        }
        return -1;
    }

    private boolean isParameterizedSqlArgument(String content, int callStart, String argument) {
        String trimmed = argument.trim();
        if (trimmed.startsWith("new ParameterizedSql(")
                || trimmed.startsWith("DatabaseAP.parameterizeSql(")
                || trimmed.startsWith("parameterizeAllFields(")
                || trimmed.contains(".parameterizeAllFields(")) {
            return true;
        }
        if (!trimmed.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return false;
        }
        String precedingContent = content.substring(0, callStart);
        String identifier = Pattern.quote(trimmed);
        return Pattern.compile("\\bParameterizedSql\\s+" + identifier + "\\b")
                .matcher(precedingContent)
                .find();
    }

    private boolean usesRawAdminSqlBoundary(Path path) {
        try {
            String content = Files.readString(path);
            int start = 0;
            while ((start = findNextRawAdminSqlBoundaryCall(content, start)) >= 0) {
                int openParen = content.indexOf('(', start);
                int closeParen = findClosingParen(content, openParen);
                if (closeParen < 0 || countTopLevelArguments(content, openParen + 1, closeParen) == 1) {
                    return true;
                }
                start = closeParen + 1;
            }
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private int findNextRawAdminSqlBoundaryCall(String content, int start) {
        ParseState state = ParseState.CODE;
        for (int i = start; i < content.length(); i++) {
            if (state == ParseState.CODE && content.startsWith("LegacyJdbcQuery.queryResults(", i)) {
                return i;
            }
            state = nextState(state, content, i, content.length());
        }
        return -1;
    }

    private int findClosingParen(String content, int openParen) {
        int depth = 0;
        ParseState state = ParseState.CODE;
        for (int i = openParen; i < content.length(); i++) {
            char current = content.charAt(i);
            state = nextState(state, content, i, content.length());
            if (state != ParseState.CODE) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int countTopLevelArguments(String content, int start, int end) {
        int depth = 0;
        int count = content.substring(start, end).trim().isEmpty() ? 0 : 1;
        ParseState state = ParseState.CODE;
        for (int i = start; i < end; i++) {
            char current = content.charAt(i);
            state = nextState(state, content, i, end);
            if (state != ParseState.CODE) {
                continue;
            }
            if (current == '(' || current == '[' || current == '{') {
                depth++;
            } else if (current == ')' || current == ']' || current == '}') {
                depth--;
            } else if (current == ',' && depth == 0) {
                count++;
            }
        }
        return count;
    }

    private List<String> topLevelArguments(String content, int start, int end) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int argumentStart = start;
        ParseState state = ParseState.CODE;
        for (int i = start; i < end; i++) {
            char current = content.charAt(i);
            state = nextState(state, content, i, end);
            if (state != ParseState.CODE) {
                continue;
            }
            if (current == '(' || current == '[' || current == '{') {
                depth++;
            } else if (current == ')' || current == ']' || current == '}') {
                depth--;
            } else if (current == ',' && depth == 0) {
                arguments.add(content.substring(argumentStart, i).trim());
                argumentStart = i + 1;
            }
        }

        String lastArgument = content.substring(argumentStart, end).trim();
        if (!lastArgument.isEmpty()) {
            arguments.add(lastArgument);
        }
        return arguments;
    }

    private ParseState nextState(ParseState state, String content, int index, int end) {
        char current = content.charAt(index);
        char next = index + 1 < end ? content.charAt(index + 1) : '\0';
        if (state == ParseState.LINE_COMMENT) {
            return current == '\n' ? ParseState.CODE : state;
        }
        if (state == ParseState.BLOCK_COMMENT) {
            return current == '*' && next == '/' ? ParseState.CODE : state;
        }
        if (state == ParseState.STRING) {
            return current == '"' && !isEscaped(content, index) ? ParseState.CODE : state;
        }
        if (state == ParseState.CHARACTER) {
            return current == '\'' && !isEscaped(content, index) ? ParseState.CODE : state;
        }
        if (state == ParseState.TEXT_BLOCK_OPENING_1) {
            return ParseState.TEXT_BLOCK_OPENING_2;
        }
        if (state == ParseState.TEXT_BLOCK_OPENING_2) {
            return ParseState.TEXT_BLOCK;
        }
        if (state == ParseState.TEXT_BLOCK_CLOSING_1) {
            return ParseState.TEXT_BLOCK_CLOSING_2;
        }
        if (state == ParseState.TEXT_BLOCK_CLOSING_2) {
            return ParseState.CODE;
        }
        if (state == ParseState.TEXT_BLOCK) {
            return isTextBlockDelimiter(content, index, end) ? ParseState.TEXT_BLOCK_CLOSING_1 : state;
        }
        if (current == '/' && next == '/') {
            return ParseState.LINE_COMMENT;
        }
        if (current == '/' && next == '*') {
            return ParseState.BLOCK_COMMENT;
        }
        if (isTextBlockDelimiter(content, index, end)) {
            return ParseState.TEXT_BLOCK_OPENING_1;
        }
        if (current == '"') {
            return ParseState.STRING;
        }
        if (current == '\'') {
            return ParseState.CHARACTER;
        }
        return ParseState.CODE;
    }

    private boolean isEscaped(String content, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0 && content.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private boolean isTextBlockDelimiter(String content, int index, int end) {
        return index + 2 < end
                && content.charAt(index) == '"'
                && content.charAt(index + 1) == '"'
                && content.charAt(index + 2) == '"'
                && !isEscaped(content, index);
    }

    private enum ParseState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK,
        TEXT_BLOCK_OPENING_1,
        TEXT_BLOCK_OPENING_2,
        TEXT_BLOCK_CLOSING_1,
        TEXT_BLOCK_CLOSING_2
    }

    private void validateSafeSelectQuery(String sql) throws Exception {
        LegacyJdbcQuery.validateSafeSelectQuery(sql);
    }
}
