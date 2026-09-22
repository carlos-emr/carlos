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
package io.github.carlos_emr.carlos.sms;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the default grants seeded for the SMS security objects.
 * <p>
 * The seed is what every clinic gets on upgrade, so widening it (another role, or a grant on
 * {@code _admin.sms} beyond admin) is a privilege change that must be deliberate. The grants and their
 * guards are pinned by reading the file, and their effect is checked by executing the file verbatim
 * against H2 in MySQL mode, because the H2 test schema is built from the entity mappings and never runs
 * the Flyway files.
 *
 * @since 2026-09-22
 */
@DisplayName("SMS security objects migration")
@Tag("unit")
@Tag("security")
class SmsSecurityObjectsMigrationUnitTest {
    private static final Path COMMON_MIGRATIONS = Path.of("database", "mysql", "migration", "common");
    private static final Pattern OBJECT_ROW = Pattern.compile(
            "SELECT\\s+'(_[A-Za-z.]+)'\\s*,\\s*'([^']*)'\\s*,\\s*0\\s+FROM\\s+DUAL", Pattern.CASE_INSENSITIVE);
    /** A grant statement together with the role and object its guard keys on. */
    private static final Pattern GUARDED_GRANT = Pattern.compile(
            "SELECT\\s+'([a-z_]+)'\\s*,\\s*'(_[A-Za-z.]+)'\\s*,\\s*'[rwudx]'[^;]*?"
                    + "WHERE\\s+NOT\\s+EXISTS\\s*\\(\\s*SELECT\\s+1\\s+FROM\\s+`?secObjPrivilege`?\\s+"
                    + "WHERE\\s+`?roleUserGroup`?\\s*=\\s*'([a-z_]+)'\\s+"
                    + "AND\\s+`?objectName`?\\s*=\\s*'(_[A-Za-z.]+)'\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GRANT_ROW = Pattern.compile(
            "SELECT\\s+'([a-z_]+)'\\s*,\\s*'(_[A-Za-z.]+)'\\s*,\\s*'([rwudx])'\\s*,\\s*0\\s*,\\s*'999998'\\s+FROM\\s+DUAL",
            Pattern.CASE_INSENSITIVE);

    private static final String GRANT_ROWS_QUERY = """
            SELECT CONCAT(roleUserGroup, ' ', objectName, ' ', privilege) FROM secObjPrivilege
             ORDER BY objectName, roleUserGroup""";
    private static final String OBJECT_NAMES_QUERY = "SELECT objectName FROM secObjectName ORDER BY objectName";
    private static final String DESCRIPTIONS_QUERY = "SELECT description FROM secObjectName ORDER BY objectName";

    @Test
    @DisplayName("seeds exactly the _sms and _admin.sms object names")
    void shouldSeedSmsObjectNames_forAdminUiListing() throws IOException {
        String sql = migrationSql();

        assertThat(matches(OBJECT_ROW, sql, 1)).containsExactlyInAnyOrder("_sms", "_admin.sms");
        assertThat(matches(OBJECT_ROW, sql, 2)).containsExactlyInAnyOrder(
                "Send patient text messages and view SMS history", "Configure and manage SMS");
    }

    @Test
    @DisplayName("grants admin and doctor all rights on _sms, admin alone on _admin.sms, and nothing else")
    void shouldGrantDefaults_toAdminAndDoctorOnly() throws IOException {
        String sql = migrationSql();

        assertThat(grants(sql)).containsExactlyInAnyOrder(
                "admin _sms x",
                "doctor _sms x",
                "admin _admin.sms x");
    }

    @Test
    @DisplayName("every grant is guarded on its own role and object, so a re-run or an existing clinic grant is left alone")
    void shouldGuardEveryInsert_onItsOwnPrimaryKey() throws IOException {
        String sql = migrationSql();

        long inserts = Pattern.compile("INSERT\\s+INTO", Pattern.CASE_INSENSITIVE).matcher(sql).results().count();
        assertThat(inserts).isEqualTo(5);
        assertThat(sql).doesNotContainIgnoringCase("INSERT IGNORE");
        Matcher grant = GUARDED_GRANT.matcher(sql);
        int guardedGrants = 0;
        while (grant.find()) {
            guardedGrants++;
            assertThat(grant.group(3)).as("guard role for %s %s", grant.group(1), grant.group(2)).isEqualTo(grant.group(1));
            assertThat(grant.group(4)).as("guard object for %s %s", grant.group(1), grant.group(2)).isEqualTo(grant.group(2));
        }
        assertThat(guardedGrants).isEqualTo(3);
    }

    @Test
    @DisplayName("applied twice to a fresh schema, leaves exactly the default rows")
    void shouldSeedDefaults_whenAppliedTwiceToFreshSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:sms_sec_fresh;MODE=MySQL");
             Statement statement = connection.createStatement()) {
            createSecurityTables(statement);

            applyMigration(connection);
            applyMigration(connection);

            assertThat(rows(statement, OBJECT_NAMES_QUERY))
                    .containsExactly("_admin.sms", "_sms");
            assertThat(rows(statement, GRANT_ROWS_QUERY))
                    .containsExactly("admin _admin.sms x", "admin _sms x", "doctor _sms x");
        }
    }

    @Test
    @DisplayName("leaves a clinic's narrower or no-rights grant alone")
    void shouldPreserveExistingGrants_whenClinicAlreadyDecided() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:sms_sec_existing;MODE=MySQL");
             Statement statement = connection.createStatement()) {
            createSecurityTables(statement);
            // The clinic already gave doctors history only, and gave admin an 'o' (no-rights) row on _sms.
            statement.execute("INSERT INTO secObjPrivilege VALUES ('doctor', '_sms', 'r', 0, '999998')");
            statement.execute("INSERT INTO secObjPrivilege VALUES ('admin', '_sms', 'o', 0, '999998')");

            applyMigration(connection);

            assertThat(rows(statement, GRANT_ROWS_QUERY))
                    .containsExactly("admin _admin.sms x", "admin _sms o", "doctor _sms r");
        }
    }

    @Test
    @DisplayName("still seeds the grants when an adopted database already lists the object names")
    void shouldSeedGrants_whenObjectNamesAlreadyExistWithoutGrants() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:sms_sec_names;MODE=MySQL");
             Statement statement = connection.createStatement()) {
            createSecurityTables(statement);
            statement.execute("INSERT INTO secObjectName VALUES ('_sms', 'clinic wording', 0)");
            statement.execute("INSERT INTO secObjectName VALUES ('_admin.sms', 'clinic wording', 0)");

            applyMigration(connection);

            assertThat(rows(statement, DESCRIPTIONS_QUERY))
                    .containsExactly("clinic wording", "clinic wording");
            assertThat(rows(statement, GRANT_ROWS_QUERY))
                    .containsExactly("admin _admin.sms x", "admin _sms x", "doctor _sms x");
        }
    }

    private static void createSecurityTables(Statement statement) throws SQLException {
        // Column shapes from V1__baseline_schema.sql; the composite key is what makes "one grant per
        // role and object" a hard rule rather than a convention.
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

    /**
     * Runs the file exactly as shipped, comments and all, through H2's own script parser. H2 is more
     * permissive than MariaDB (it accepts {@code --} with no following space, which MariaDB rejects), so
     * this proves the statements run, not that MariaDB will accept every comment style; CI's
     * flyway-baseline job covers that.
     */
    private static void applyMigration(Connection connection) throws IOException, SQLException {
        try (var reader = Files.newBufferedReader(migrationPath(), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }

    /** The file with line comments stripped, for the pinning regexes only; never executed. */
    private static String migrationSql() throws IOException {
        return Files.readString(migrationPath(), StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
    }

    private static Path migrationPath() throws IOException {
        try (Stream<Path> files = Files.list(COMMON_MIGRATIONS)) {
            List<Path> candidates = files
                    .filter(p -> p.getFileName().toString().matches("V1\\.0\\.\\d+__add_sms_security_objects\\.sql"))
                    .toList();
            assertThat(candidates).as("exactly one SMS security objects migration").hasSize(1);
            // A renumber must be loud: the number was chosen to sit above every open claim at the time.
            assertThat(candidates.get(0).getFileName().toString()).startsWith("V1.0.31__");
            return candidates.get(0);
        }
    }

    private static List<String> grants(String sql) {
        Matcher m = GRANT_ROW.matcher(sql);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1) + " " + m.group(2) + " " + m.group(3));
        }
        return found;
    }

    private static List<String> matches(Pattern pattern, String sql, int group) {
        Matcher m = pattern.matcher(sql);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(group));
        }
        return found;
    }
}
