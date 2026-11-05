/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.test.base;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class IsolatedJpaTestDatabaseUnitTest {
    @Test
    void shouldRefuseAConfiguredServer_withoutNativeOptIn() {
        assertThatThrownBy(() -> new IsolatedJpaTestDatabase("guard", Map.of(
                "CARLOS_TEST_MARIADB_URL", "jdbc:mysql://127.0.0.1/")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseFlywaySchema_withoutNativeOptIn() {
        assertThatThrownBy(() -> new IsolatedJpaTestDatabase("guard", Map.of(
                "CARLOS_TEST_MARIADB_SCHEMA", "flyway")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("native-test opt-in");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:mysql://127.0.0.1/clinical", "jdbc:mysql://127.0.0.1/?database=clinical",
            "jdbc:mysql://127.0.0.1/;database=clinical", "jdbc:h2:file:clinical", "jdbc:mysql://user:synthetic@127.0.0.1/"})
    void shouldRefuseDatabaseSelection_beforeAnyNativeConnection(String server) {
        assertThatThrownBy(() -> new IsolatedJpaTestDatabase("guard", Map.of(
                "CARLOS_TEST_MARIADB_DAO", "true", "CARLOS_TEST_MARIADB_URL", server)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseAnOversizedIdentifier_beforeAnyNativeConnection() {
        assertThatThrownBy(() -> new IsolatedJpaTestDatabase("x".repeat(20), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepIndependentSchemas_forConcurrentTestOwners() throws Exception {
        try (var first = new IsolatedJpaTestDatabase("guard", Map.of());
             var second = new IsolatedJpaTestDatabase("guard", Map.of());
             var a = first.dataSource().getConnection(); var b = second.dataSource().getConnection()) {
            try (var statement = a.createStatement()) { statement.execute("CREATE TABLE owned_marker(id INT)"); }
            assertThat(a.getMetaData().getURL()).isNotEqualTo(b.getMetaData().getURL());
            try (var tables = b.getMetaData().getTables(null, null, "OWNED_MARKER", null)) {
                assertThat(tables.next()).isFalse();
            }
        }
    }
}
