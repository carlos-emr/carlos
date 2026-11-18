/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** Repeats the legacy NULL regressions on the shipped physical MariaDB Flyway columns. */
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
class CaseManagementNoteLegacyFlagsMariaDbIntegrationTest extends CaseManagementNoteLegacyFlagsIntegrationTest {
    @Test
    void shouldUseTheNullablePublishedColumnTypes_onThePhysicalSchema() {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=:tableName
                  AND COLUMN_NAME IN (:archived, :position) ORDER BY COLUMN_NAME
                """).setParameter("tableName", "casemgmt_note")
                .setParameter("archived", "archived").setParameter("position", "position").getResultList();
        assertThat(rows).extracting(row -> Arrays.asList((Object[]) row)).containsExactly(
                List.of("archived", "tinyint", "YES"), List.of("position", "int", "YES"));
    }
}
