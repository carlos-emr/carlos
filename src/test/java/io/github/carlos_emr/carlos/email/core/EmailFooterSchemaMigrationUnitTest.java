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
 * Applies the email footer migration (issue #3981) to a minimal H2 copy of the two tables. The
 * file is found by its description, not its version, because the version is assigned at merge.
 */
@Tag("unit")
@Tag("fast")
@Tag("update")
class EmailFooterSchemaMigrationUnitTest {

    private static final Path COMMON_MIGRATIONS = Path.of("database", "mysql", "migration", "common");

    @Test
    @DisplayName("should add the footer columns once and leave existing rows without a footer")
    void shouldAddFooterColumns_whenMigrationAppliedTwice() throws Exception {
        List<String> statements = statements(footerMigration());
        assertThat(statements).hasSize(2);

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:email_footer_migration;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE emailLog (id INTEGER PRIMARY KEY, body BLOB, status VARCHAR(20))");
            statement.execute("CREATE TABLE emailConfig (id INTEGER PRIMARY KEY, configDetails TEXT)");
            statement.execute("INSERT INTO emailLog (id, body, status) VALUES (1, NULL, 'SUCCESS')");
            statement.execute("INSERT INTO emailConfig (id, configDetails) VALUES (1, NULL)");

            for (int run = 0; run < 2; run++) {
                // IF NOT EXISTS: a second run is a no-op rather than a duplicate-column failure.
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }

            try (var rows = statement.executeQuery(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = 'EMAILLOG' "
                            + "ORDER BY ORDINAL_POSITION")) {
                List<String> columns = new ArrayList<>();
                while (rows.next()) {
                    columns.add(rows.getString(1).toLowerCase(Locale.ROOT));
                }
                assertThat(columns).containsExactly("id", "body", "footer", "status");
            }
            try (var rows = statement.executeQuery(
                    "SELECT l.footer, c.defaultFooter FROM emailLog l, emailConfig c WHERE l.id = 1 AND c.id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBytes(1)).isNull();
                assertThat(rows.getString(2)).isNull();
            }
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
