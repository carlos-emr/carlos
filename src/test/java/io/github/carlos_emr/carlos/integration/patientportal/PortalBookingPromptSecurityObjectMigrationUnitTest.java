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
package io.github.carlos_emr.carlos.integration.patientportal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the default grants seeded for the booking prompt security object.
 * <p>
 * The seed is what every clinic gets on upgrade, so another role or a different privilege is a
 * deliberate privilege change. The file is executed verbatim against H2 in MySQL mode, because the H2
 * test schema is built from the entity mappings and never runs the Flyway files. Its version number is
 * set when it merges, so only the name after the version is matched.
 *
 * @since 2026-10-08
 */
@DisplayName("Portal booking prompt security object migration")
@Tag("unit")
@Tag("security")
class PortalBookingPromptSecurityObjectMigrationUnitTest {
    private static final Path COMMON_MIGRATIONS = Path.of("database", "mysql", "migration", "common");
    private static final String DEFAULT_GRANTS_FOR = "Nurse Manager,RN,RPN,admin,doctor,locum,nurse,psychiatrist,receptionist";
    private static final String GRANT_ROWS_QUERY = """
            SELECT CONCAT(roleUserGroup, ' ', objectName, ' ', privilege) FROM secObjPrivilege
             ORDER BY roleUserGroup""";

    @Test
    @DisplayName("applied twice to a fresh schema, seeds the object the action checks and the nine default grants")
    void shouldSeedObjectAndDefaultGrants_whenAppliedTwiceToFreshSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:portal_booking_sec_fresh;MODE=MySQL");
             Statement statement = connection.createStatement()) {
            createSecurityTables(statement);

            applyMigration(connection);
            applyMigration(connection);

            assertThat(rows(statement, "SELECT objectName FROM secObjectName"))
                    .containsExactly(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT);
            List<String> expected = new ArrayList<>();
            for (String role : DEFAULT_GRANTS_FOR.split(",")) {
                expected.add(role + " " + PortalStaffContextResolver.OBJECT_BOOKING_PROMPT + " x");
            }
            assertThat(rows(statement, GRANT_ROWS_QUERY)).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    @DisplayName("leaves a clinic's own grant and description alone")
    void shouldPreserveClinicRows_whenClinicAlreadyDecided() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:portal_booking_sec_existing;MODE=MySQL");
             Statement statement = connection.createStatement()) {
            createSecurityTables(statement);
            statement.execute("INSERT INTO secObjectName VALUES ('_portal.booking_prompt', 'clinic wording', 0)");
            statement.execute("INSERT INTO secObjPrivilege VALUES ('receptionist', '_portal.booking_prompt', 'r', 0, '999998')");

            applyMigration(connection);

            assertThat(rows(statement, "SELECT description FROM secObjectName")).containsExactly("clinic wording");
            assertThat(rows(statement, GRANT_ROWS_QUERY))
                    .contains("receptionist _portal.booking_prompt r")
                    .doesNotContain("receptionist _portal.booking_prompt x")
                    .hasSize(9);
        }
    }

    private static void createSecurityTables(Statement statement) throws SQLException {
        // Column shapes from V1__baseline_schema.sql; the composite key is what lets INSERT IGNORE keep
        // a clinic's existing row.
        statement.execute("""
                CREATE TABLE secObjectName (
                    objectName VARCHAR(100) NOT NULL DEFAULT '',
                    description VARCHAR(60),
                    orgapplicable TINYINT DEFAULT 0,
                    PRIMARY KEY (objectName))""");
        statement.execute("""
                CREATE TABLE secObjPrivilege (
                    roleUserGroup VARCHAR(30) NOT NULL DEFAULT '',
                    objectName VARCHAR(100) NOT NULL DEFAULT '',
                    privilege VARCHAR(100) NOT NULL DEFAULT '|0|',
                    priority INT DEFAULT 0,
                    provider_no VARCHAR(6),
                    PRIMARY KEY (roleUserGroup, objectName))""");
    }

    private static List<String> rows(Statement statement, String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (var results = statement.executeQuery(query)) {
            while (results.next()) {
                values.add(results.getString(1));
            }
        }
        return values;
    }

    /** Runs the file exactly as shipped, comments and all, through H2's own script parser. */
    private static void applyMigration(Connection connection) throws IOException, SQLException {
        try (var reader = Files.newBufferedReader(migrationPath(), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }

    private static Path migrationPath() throws IOException {
        try (Stream<Path> files = Files.list(COMMON_MIGRATIONS)) {
            List<Path> candidates = files
                    .filter(p -> p.getFileName().toString()
                            .matches("V1\\.0\\.\\d+__portal_booking_prompt_security_object\\.sql"))
                    .toList();
            assertThat(candidates).as("exactly one booking prompt security object migration").hasSize(1);
            return candidates.get(0);
        }
    }
}
