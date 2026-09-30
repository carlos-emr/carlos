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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * The migration that turns the SMS consent type on with its approved wording (#3848). It runs the real
 * migration file on H2 against the rows a clinic could have, and checks that its guard names exactly the
 * draft the seeding migration wrote: a guard that differed by one character would silently change nothing.
 */
@DisplayName("SMS consent activation migration")
@Tag("unit")
class SmsConsentActivationMigrationUnitTest {
    private static final Path COMMON_MIGRATIONS = Path.of("database", "mysql", "migration", "common");
    private static final String TYPE = "sms_communication_consent";
    /** The seeding migration's description: the third value of its consentType insert. */
    private static final Pattern SEEDED_DESCRIPTION = Pattern.compile(
            "SELECT\\s+'sms_communication_consent'\\s*,\\s*'[^']*'\\s*,\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern APPROVED_DESCRIPTION = Pattern.compile(
            "SET\\s+description\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUARD_DESCRIPTION = Pattern.compile(
            "AND\\s+description\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);
    private static final String CLINIC_WORDING = "This patient agreed to appointment texts. Wording set by the clinic.";

    @Test
    @DisplayName("guards on exactly the draft the seeding migration wrote, and replaces it with shorter-than-column wording")
    void shouldGuardOnSeededDraft_andFitTheColumn() throws IOException {
        String seeded = only(SEEDED_DESCRIPTION, sql(migration("add_sms_consent")));
        String activation = sql(migration("activate_sms_consent"));

        assertThat(only(GUARD_DESCRIPTION, activation)).isEqualTo(seeded);
        String approved = only(APPROVED_DESCRIPTION, activation);
        assertThat(approved).isNotEqualTo(seeded).hasSizeLessThanOrEqualTo(500);
        // Consent is recorded for the patient, not for one number, until #2674, so the wording must not claim one.
        assertThat(approved.toLowerCase()).doesNotContain("number", "mobile");
    }

    @Test
    @DisplayName("runs after the migration that seeds the consent type")
    void shouldRunAfterTheSeedingMigration() throws IOException {
        assertThat(version(migration("activate_sms_consent"))).isGreaterThan(version(migration("add_sms_consent")));
    }

    @Test
    @DisplayName("turns the untouched draft on with the approved wording, and a second run changes nothing")
    void shouldActivateWithApprovedWording_whenDraftIsUntouched() throws Exception {
        try (Connection connection = database("sms_consent_fresh"); Statement statement = connection.createStatement()) {
            insert(statement, TYPE, seededDraft(), 0);
            insert(statement, "electronic_communication_consent", seededDraft(), 0);

            applyActivation(connection);
            applyActivation(connection);

            assertThat(row(statement, TYPE)).containsExactly(approvedWording(), "1");
            // Another consent type with the same text is not touched.
            assertThat(row(statement, "electronic_communication_consent")).containsExactly(seededDraft(), "0");
        }
    }

    @Test
    @DisplayName("replaces the draft wording when a clinic had already switched the type on by hand")
    void shouldReplaceDraft_whenClinicAlreadyActivatedIt() throws Exception {
        try (Connection connection = database("sms_consent_on"); Statement statement = connection.createStatement()) {
            insert(statement, TYPE, seededDraft(), 1);

            applyActivation(connection);

            assertThat(row(statement, TYPE)).containsExactly(approvedWording(), "1");
        }
    }

    @Test
    @DisplayName("leaves a clinic's own wording and its own active setting alone")
    void shouldLeaveClinicWordingAlone() throws Exception {
        try (Connection connection = database("sms_consent_own"); Statement statement = connection.createStatement()) {
            insert(statement, TYPE, CLINIC_WORDING, 0);

            applyActivation(connection);

            assertThat(row(statement, TYPE)).containsExactly(CLINIC_WORDING, "0");
        }
    }

    @Test
    @DisplayName("does nothing where the SMS consent type does not exist")
    void shouldDoNothing_whenTypeIsMissing() throws Exception {
        try (Connection connection = database("sms_consent_none"); Statement statement = connection.createStatement()) {
            applyActivation(connection);

            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM consentType")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    private static Connection database(String name) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=MySQL");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE consentType (
                      id INT NOT NULL AUTO_INCREMENT,
                      type VARCHAR(50) DEFAULT NULL,
                      name VARCHAR(50) DEFAULT NULL,
                      description VARCHAR(500) DEFAULT NULL,
                      active TINYINT DEFAULT NULL,
                      providerNo VARCHAR(11) DEFAULT NULL,
                      remoteEnabled TINYINT DEFAULT NULL,
                      PRIMARY KEY (id)
                    )""");
        }
        return connection;
    }

    private static void insert(Statement statement, String type, String description, int active) throws SQLException {
        statement.execute("INSERT INTO consentType (type, name, description, active) VALUES ('" + type
                + "', 'SMS Text Message Consent', '" + description + "', " + active + ")");
    }

    private static List<String> row(Statement statement, String type) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT description, active FROM consentType WHERE type = '" + type + "'")) {
            assertThat(rows.next()).as("a %s row", type).isTrue();
            return List.of(rows.getString(1), String.valueOf(rows.getInt(2)));
        }
    }

    private static void applyActivation(Connection connection) throws IOException, SQLException {
        RunScript.execute(connection, new StringReader(
                Files.readString(migration("activate_sms_consent"), StandardCharsets.UTF_8)));
    }

    private static String seededDraft() throws IOException {
        return only(SEEDED_DESCRIPTION, sql(migration("add_sms_consent")));
    }

    private static String approvedWording() throws IOException {
        return only(APPROVED_DESCRIPTION, sql(migration("activate_sms_consent")));
    }

    /** The file with line comments stripped, for the patterns only; never executed. */
    private static String sql(Path migration) throws IOException {
        return Files.readString(migration, StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
    }

    private static String only(Pattern pattern, String sql) {
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find()).as("pattern %s", pattern).isTrue();
        String value = matcher.group(1);
        assertThat(matcher.find()).as("only one match of %s", pattern).isFalse();
        return value;
    }

    /** The one common migration named {@code V1.0.<n>__<name>.sql}; the number is not pinned, since it is set at merge. */
    private static Path migration(String name) throws IOException {
        try (Stream<Path> files = Files.list(COMMON_MIGRATIONS)) {
            List<Path> candidates = files
                    .filter(p -> p.getFileName().toString().matches("V1\\.0\\.\\d+__" + name + "\\.sql"))
                    .toList();
            assertThat(candidates).as("exactly one %s migration", name).hasSize(1);
            return candidates.get(0);
        }
    }

    private static int version(Path migration) {
        Matcher matcher = Pattern.compile("V1\\.0\\.(\\d+)__").matcher(migration.getFileName().toString());
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }
}
