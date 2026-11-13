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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the email footer migration (issue #3981) to a minimal H2 copy of the tables it changes. The
 * file is found by its description, not its version, because the version is assigned at merge.
 */
@Tag("unit")
@Tag("fast")
@Tag("update")
class EmailFooterSchemaMigrationUnitTest {

    private static final Path COMMON_MIGRATIONS = Path.of("database", "mysql", "migration", "common");

    @Test
    @DisplayName("should add the footer column, widen property.value, add the logo table and the property index, once")
    void shouldApplyFooterSchema_whenMigrationAppliedTwice() throws Exception {
        List<String> statements = statements(footerMigration());
        assertThat(statements).hasSize(4);

        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:email_footer_migration;MODE=MySQL;NON_KEYWORDS=VALUE");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE emailLog (id INTEGER PRIMARY KEY, body BLOB, status VARCHAR(20))");
            // property as the baseline creates it.
            statement.execute("CREATE TABLE property (id INTEGER PRIMARY KEY, name VARCHAR(255), "
                    + "value VARCHAR(2000), provider_no VARCHAR(6))");
            statement.execute("INSERT INTO emailLog (id, body, status) VALUES (1, NULL, 'SUCCESS')");
            statement.execute("INSERT INTO property (id, name, value) VALUES (1, 'email_footer', 'Kept as it was')");

            for (int run = 0; run < 2; run++) {
                // IF NOT EXISTS and MODIFY: a second run is a no-op rather than a failure.
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }

            assertThat(columns(statement, "EMAILLOG")).containsExactly("id", "body", "footer", "status");
            try (var rows = statement.executeQuery("SELECT footer FROM emailLog WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBytes(1)).isNull();
            }

            // A formatted footer can be longer than the old 2,000-character column.
            String longFooter = "<b>Dr. Test</b><br>".repeat(400);
            statement.execute("INSERT INTO property (id, name, value) VALUES (2, 'email_footer', '" + longFooter + "')");
            try (var rows = statement.executeQuery("SELECT id, value FROM property ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(2)).isEqualTo("Kept as it was");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(2)).hasSize(longFooter.length()).isEqualTo(longFooter);
            }

            assertThat(columns(statement, "EMAILFOOTERLOGO")).containsExactly("id", "contenttype", "imagedata",
                    "width", "height", "sha256", "uploadedby", "uploadedat", "removedat", "removedby");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM emailFooterLogo")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }

            // The lookup index on (name, provider_no), added once however often the file runs.
            try (var rows = statement.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                    + "WHERE UPPER(INDEX_NAME) = 'IDX_PROPERTY_NAME_PROVIDER' ORDER BY ORDINAL_POSITION")) {
                List<String> indexed = new ArrayList<>();
                while (rows.next()) {
                    indexed.add(rows.getString(1).toLowerCase(Locale.ROOT));
                }
                assertThat(indexed).containsExactly("name", "provider_no");
            }
        }
    }

    @Test
    @DisplayName("should no longer add the unused emailConfig.defaultFooter column")
    void shouldNotAddDefaultFooter_forEmailConfig() throws Exception {
        assertThat(statements(footerMigration())).noneMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("emailconfig"));
    }

    private static List<String> columns(Statement statement, String table) throws SQLException {
        try (var rows = statement.executeQuery(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = '" + table + "' "
                        + "ORDER BY ORDINAL_POSITION")) {
            List<String> columns = new ArrayList<>();
            while (rows.next()) {
                columns.add(rows.getString(1).toLowerCase(Locale.ROOT));
            }
            return columns;
        }
    }

    private static String footerMigration() throws IOException {
        try (Stream<Path> files = Files.list(COMMON_MIGRATIONS)) {
            List<Path> matches = files
                    .filter(file -> file.getFileName().toString().matches("V1\\.0\\.\\d+__add_email_log_footer\\.sql"))
                    .toList();
            assertThat(matches).hasSize(1);
            return Files.readString(matches.get(0), StandardCharsets.UTF_8);
        }
    }

    private static List<String> statements(String migration) {
        String withoutComments = migration.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(sql -> !sql.isEmpty())
                .toList();
    }
}
