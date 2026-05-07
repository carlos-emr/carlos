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

    private boolean usesDeprecatedDatabaseBoundary(Path path) {
        try {
            String content = Files.readString(path);
            // Raw text is intentional: production comments, strings, and JavaDoc that
            // mention removed APIs should be updated to LegacyJdbcQuery terminology.
            return content.contains("DBHandler.GetPreSQL")
                    || content.contains("new DBPreparedHandler")
                    || content.contains("DbConnectionFilter.getThreadLocalDbConnection()");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private void validateSafeSelectQuery(String sql) throws Exception {
        LegacyJdbcQuery.validateSafeSelectQuery(sql);
    }
}
