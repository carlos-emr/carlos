/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the front-desk email default (follow-up to #3981, maintainer decision of 8 Oct 2026) to a
 * minimal H2 copy of secObjPrivilege. The file is found by its description, not its version,
 * because the version is assigned at merge.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
class EmailFrontDeskDefaultMigrationUnitTest {

    private static final Path COMMON_MIGRATIONS = Path.of("database", "mysql", "migration", "common");

    @Test
    @DisplayName("should give front-desk roles email once, and leave a role the clinic already set alone")
    void shouldGrantFrontDeskEmail_onlyWhereRoleHasNoEmailLine() throws Exception {
        String migration = migration();

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:email_front_desk_default;MODE=MySQL");
             var statement = connection.createStatement()) {
            // secObjPrivilege as the baseline creates it.
            statement.execute("CREATE TABLE secObjPrivilege (roleUserGroup VARCHAR(30) NOT NULL DEFAULT '', "
                    + "objectName VARCHAR(100) NOT NULL DEFAULT '', privilege VARCHAR(100) NOT NULL DEFAULT '|0|', "
                    + "priority INT DEFAULT 0, provider_no VARCHAR(6) DEFAULT NULL, PRIMARY KEY (roleUserGroup, objectName))");
            statement.execute("INSERT INTO secObjPrivilege VALUES ('doctor', '_email', 'x', 0, '999998')");
            // A clinic that switched email off for secretaries keeps that.
            statement.execute("INSERT INTO secObjPrivilege VALUES ('secretary', '_email', 'o', 0, '999998')");

            for (int run = 0; run < 2; run++) {
                statement.execute(migration);
            }

            Map<String, String> email = new LinkedHashMap<>();
            try (var rows = statement.executeQuery(
                    "SELECT roleUserGroup, privilege FROM secObjPrivilege WHERE objectName = '_email' ORDER BY roleUserGroup")) {
                while (rows.next()) {
                    email.put(rows.getString(1), rows.getString(2));
                }
            }
            assertThat(email).containsExactly(
                    Map.entry("Medical Secretary", "x"),
                    Map.entry("doctor", "x"),
                    Map.entry("receptionist", "x"),
                    Map.entry("secretary", "o"));
        }
    }

    @Test
    @DisplayName("should grant only email, and only to the three front-desk roles")
    void shouldTouchOnlyEmail_forFrontDeskRoles() throws Exception {
        String sql = migration();

        assertThat(sql).contains("INSERT IGNORE INTO secObjPrivilege");
        assertThat(sql.lines().filter(line -> line.strip().startsWith("('")).toList())
                .containsExactly(
                        "    ('receptionist',      '_email', 'x', 0, '999998'),",
                        "    ('Medical Secretary', '_email', 'x', 0, '999998'),",
                        "    ('secretary',         '_email', 'x', 0, '999998');");
    }

    private static String migration() throws IOException {
        try (Stream<Path> files = Files.list(COMMON_MIGRATIONS)) {
            List<Path> matches = files
                    .filter(file -> file.getFileName().toString().matches("V1\\.0\\.\\d+__email_front_desk_default\\.sql"))
                    .toList();
            assertThat(matches).hasSize(1);
            return Files.readString(matches.get(0), StandardCharsets.UTF_8);
        }
    }
}
