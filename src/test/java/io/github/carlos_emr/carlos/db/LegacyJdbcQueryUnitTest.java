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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the legacy JDBC transition boundary.
 *
 * @since 2026-05-07
 */
@Tag("unit")
@Tag("database")
@DisplayName("LegacyJdbcQuery transition boundary")
class LegacyJdbcQueryUnitTest {

    @Test
    @DisplayName("shouldAllowSelectOnlyQueries_forAdminReportBoundary")
    void shouldAllowSelectOnlyQueries_forAdminReportBoundary() {
        assertThatCode(() -> validateSafeSelectQuery("select demographic_no from demographic"))
                .doesNotThrowAnyException();
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
    @DisplayName("shouldAvoidDeprecatedHandlers_inProductionCallers")
    void shouldAvoidDeprecatedHandlers_inProductionCallers() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("db", "DBHandler.java")))
                    .filter(path -> !path.endsWith(Path.of("db", "DBPreparedHandler.java")))
                    .filter(path -> !path.endsWith(Path.of("db", "DBPreparedHandlerParam.java")))
                    .filter(this::usesDeprecatedDatabaseBoundary)
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    @Test
    @DisplayName("shouldLimitRawAdminSqlBoundary_toReportByExample")
    void shouldLimitRawAdminSqlBoundary_toReportByExample() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("db", "LegacyJdbcQuery.java")))
                    .filter(path -> !path.endsWith(Path.of("db", "DBPreparedHandler.java")))
                    .filter(path -> !path.endsWith(Path.of("report", "data", "RptByExampleData.java")))
                    .filter(this::usesRawAdminSqlBoundary)
                    .toList();

            assertThat(offenders).isEmpty();
        }
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
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
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
            char next = i + 1 < end ? content.charAt(i + 1) : '\0';
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
