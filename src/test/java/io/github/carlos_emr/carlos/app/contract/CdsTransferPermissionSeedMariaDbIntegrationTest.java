/*
 * Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.app.contract;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** The real forward migrations and privilege evaluator on an exclusively owned native database. */
@Tag("integration")
@Tag("security")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsTransferPermissionSeedMariaDbIntegrationTest {
    private Connection connection;

    @BeforeEach
    void openOwnedNativeDatabase() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!"true".equals(properties.getProperty("ownedDisposableDatabase"))
                || !properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")) {
            throw new IllegalArgumentException(
                    "An exclusively owned disposable cds_export_test database is required");
        }
        connection =
                DriverManager.getConnection(
                        properties.getProperty("jdbcUrl"),
                        properties.getProperty("jdbcUser"),
                        properties.getProperty("jdbcPassword"));
        try (var query = connection.createStatement();
                var rows =
                        query.executeQuery(
                                "SELECT ENGINE FROM information_schema.TABLES WHERE"
                                        + " TABLE_SCHEMA=DATABASE() AND"
                                        + " TABLE_NAME='secObjPrivilege'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("InnoDB");
        }
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollbackEverySecurityFixture() throws Exception {
        if (connection != null) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void shouldAuthorizeBothIndependentPermissions_fromActualFreshMigrationRows() throws Exception {
        try (var query = connection.createStatement();
                var rows = query.executeQuery("SELECT COUNT(*) FROM secObjPrivilege")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1))
                    .as("all fresh Ontario permission rows, including conditional reference seeds")
                    .isEqualTo(517);
        }
        for (String object : List.of("_cds_import", "_cds_export")) {
            var privileges = new Properties();
            try (var query =
                    connection.prepareStatement(
                            "SELECT roleUserGroup,privilege FROM secObjPrivilege WHERE"
                                    + " objectName=?")) {
                query.setString(1, object);
                try (var rows = query.executeQuery()) {
                    while (rows.next())
                        privileges.setProperty(rows.getString(1), rows.getString(2));
                }
            }
            assertThat(privileges).containsOnlyKeys("admin");
            assertThat(
                            OscarRoleObjectPrivilege.checkPrivilege(
                                    "admin", privileges, List.of("admin"), "w"))
                    .as("fresh native permission %s must satisfy the real write gate", object)
                    .isTrue();
            assertThat(
                            OscarRoleObjectPrivilege.checkPrivilege(
                                    "doctor", privileges, List.of("admin"), "w"))
                    .isFalse();
        }
    }

    @Test
    void shouldPreserveExplicitTransferGrantsAndDenials_withoutChangingTheirMetadata()
            throws Exception {
        for (String object : List.of("_cds_import", "_cds_export")) {
            for (String privilege : List.of("x", "w", "r", "o", "|o|", "|x|", "|w|")) {
                try (var update =
                        connection.prepareStatement(
                                "UPDATE secObjPrivilege SET"
                                        + " privilege=?,priority=17,provider_no='989873' WHERE"
                                        + " roleUserGroup='admin' AND objectName=?")) {
                    update.setString(1, privilege);
                    update.setString(2, object);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
                replaySeed(object);
                try (var query =
                        connection.prepareStatement(
                                "SELECT privilege,priority,provider_no FROM secObjPrivilege "
                                        + "WHERE roleUserGroup='admin' AND objectName=?")) {
                    query.setString(1, object);
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo(privilege);
                        assertThat(rows.getInt(2)).isEqualTo(17);
                        assertThat(rows.getString(3)).isEqualTo("989873");
                        assertThat(rows.next()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void shouldLeaveARestrictedAdministrator_withoutNewTransferGrants() throws Exception {
        for (String object : List.of("_cds_import", "_cds_export")) {
            deleteGrant(object);
            for (String privilege : List.of("o", "r", "|o|", "|r|")) {
                setAdminPrivilege(privilege);
                replaySeed(object);
                assertThat(grantCount(object)).isZero();
            }
        }
    }

    @Test
    void shouldSeedOnlyAnUnrestrictedAdministrator_andRemainIdempotent() throws Exception {
        for (String object : List.of("_cds_import", "_cds_export")) {
            for (String privilege : List.of("x", "w", "|x|", "|w|")) {
                deleteGrant(object);
                setAdminPrivilege(privilege);
                replaySeed(object);
                replaySeed(object);
                assertThat(grantCount(object)).isEqualTo(1);
                try (var query =
                        connection.prepareStatement(
                                "SELECT roleUserGroup,privilege,priority,provider_no "
                                        + "FROM secObjPrivilege WHERE objectName=?")) {
                    query.setString(1, object);
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("admin");
                        assertThat(rows.getString(2)).isEqualTo("|x|");
                        assertThat(rows.getInt(3)).isZero();
                        assertThat(rows.getString(4)).isNull();
                        assertThat(rows.next()).isFalse();
                    }
                }
            }
        }
    }

    private void deleteGrant(String object) throws Exception {
        try (var statement =
                connection.prepareStatement(
                        "DELETE FROM secObjPrivilege "
                                + "WHERE roleUserGroup='admin' AND objectName=?")) {
            statement.setString(1, object);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void setAdminPrivilege(String privilege) throws Exception {
        try (var statement =
                connection.prepareStatement(
                        "UPDATE secObjPrivilege SET privilege=?,provider_no='989873' "
                                + "WHERE roleUserGroup='admin' AND objectName='_admin'")) {
            statement.setString(1, privilege);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private int grantCount(String object) throws Exception {
        try (var query =
                connection.prepareStatement(
                        "SELECT COUNT(*) FROM secObjPrivilege WHERE objectName=?")) {
            query.setString(1, object);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    private void replaySeed(String object) throws Exception {
        String migration =
                object.equals("_cds_import")
                        ? "V1.0.59.41__seed_admin_cds_import_permission.sql"
                        : "V1.0.59.39__cds_export_ledger.sql";
        try (var input = getClass().getResourceAsStream("/db/migration/common/" + migration)) {
            assertThat(input).as("the actual packaged forward migration").isNotNull();
            String sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int start = sql.lastIndexOf("INSERT INTO secObjPrivilege");
            assertThat(start)
                    .as("the actual security seed; DDL must not commit this rollback fixture")
                    .isNotNegative();
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(sql.substring(start));
            }
        }
    }
}
