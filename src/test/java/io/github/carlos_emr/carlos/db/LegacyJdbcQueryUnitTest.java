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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
            // Raw text is intentional here: comments and string literals that mention the
            // removed production APIs should be cleaned up rather than allowlisted.
            return content.contains("DBHandler.GetPreSQL")
                    || content.contains("new DBPreparedHandler")
                    || content.contains("DbConnectionFilter.getThreadLocalDbConnection()");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private void validateSafeSelectQuery(String sql) throws Exception {
        Method method = LegacyJdbcQuery.class.getDeclaredMethod("validateSafeSelectQuery", String.class);
        method.setAccessible(true);
        try {
            method.invoke(null, sql);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
