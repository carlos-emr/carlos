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
package io.github.carlos_emr.carlos.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forward migrations must name the columns they insert into.
 *
 * <p>A column-less {@code INSERT INTO t VALUES (...)} binds by position, so it breaks the moment
 * the table gains a column — with {@code ER_WRONG_VALUE_COUNT_ON_ROW}, which aborts the migration
 * and leaves a failed row in {@code flyway_schema_history}. Flyway's {@code validate} then fails,
 * and with {@code carlos.flyway.onBoot=validate} the application refuses to start until an
 * operator runs {@code flyway repair}.</p>
 *
 * <p>That is a live hazard rather than a stylistic one, because CARLOS tables now DO gain columns
 * outside the migration set: {@code carlos-ctl import-o19} preserves every OSCAR 19 column CARLOS
 * has no home for on the live table as {@code import_archived_&lt;column&gt;}. Thirteen tables the
 * genesis files seed positionally are in the manifest's curated set alone
 * ({@code security}, {@code property}, {@code secRole}, {@code Facility},
 * {@code ProviderPreference}, the {@code lst_*} lookups …), and a clinic's own fork can widen any
 * copied table. A data migration written after an import that inserts positionally into one of
 * them fails on exactly the hosts that hold patient data.</p>
 *
 * <p>The genesis and restore files below are grandfathered: every deployment applies them before
 * any import can run, so their positional inserts can never meet a widened table. Nothing new may
 * join that list — name the columns instead, which is what makes a migration survive a schema that
 * grows underneath it.</p>
 */
@DisplayName("Flyway migration column-list contract")
@Tag("unit")
@Tag("regression")
class MigrationColumnListContractUnitTest {

    private static final Path MIGRATION_ROOT = Path.of("database", "mysql", "migration");

    /**
     * INSERT/REPLACE whose table name is followed straight by VALUES — no column list. Both bind
     * by position and both fail the same way when the table widens.
     */
    private static final Pattern POSITIONAL_INSERT = Pattern.compile(
            "\\b(?:INSERT(?:\\s+IGNORE)?|REPLACE)\\s+INTO\\s+`?\\w+`?\\s+VALUES\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Files that already carry positional inserts, kept as a closed list rather than a rule.
     *
     * <p>These are the genesis (V1.0.2 province data) and the legacy-restore files. Flyway applies
     * them at deploy time, long before {@code import-o19} can widen anything, so their positional
     * form is safe where a new migration's would not be.</p>
     */
    private static final Set<String> GRANDFATHERED = Set.of(
            "V1.0.2__on_data.sql",
            "V1.0.2__bc_data.sql",
            "V1.0.5__restore_live_legacy_common_tables.sql");

    private static List<Path> migrations() throws IOException {
        try (Stream<Path> files = Files.walk(MIGRATION_ROOT)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * SQL with {@code --} line comments and {@code /* *}{@code /} blocks removed, so a
     * commented-out example never reads as a live statement.
     */
    private static String withoutComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--[^\\n]*", " ");
    }

    @Test
    @DisplayName("No new migration inserts by position")
    void shouldNameItsColumns_inEveryMigrationOutsideTheGenesis() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path migration : migrations()) {
            String name = migration.getFileName().toString();
            if (GRANDFATHERED.contains(name)) {
                continue;
            }
            Matcher m = POSITIONAL_INSERT.matcher(
                    withoutComments(Files.readString(migration, StandardCharsets.UTF_8)));
            while (m.find()) {
                offenders.add(name + ": " + m.group().replaceAll("\\s+", " "));
            }
        }

        assertThat(offenders)
                .describedAs("a column-less INSERT binds by position and breaks when the table "
                        + "gains a column — which CARLOS tables now do outside the migration set, "
                        + "because import-o19 preserves unmapped OSCAR 19 columns as "
                        + "import_archived_<column> on the live table. Name the columns.")
                .isEmpty();
    }

    @Test
    @DisplayName("The grandfathered list names files that exist and still need it")
    void shouldHoldNoStaleEntries_inTheGrandfatheredList() throws IOException {
        List<Path> migrations = migrations();
        for (String name : GRANDFATHERED) {
            Path file = migrations.stream()
                    .filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
            assertThat(file).describedAs("grandfathered migration %s no longer exists", name)
                    .isNotNull();
            String sql = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
            assertThat(POSITIONAL_INSERT.matcher(sql).find())
                    .describedAs("%s no longer inserts by position — drop it from the "
                            + "grandfathered list rather than leaving a hole open", name)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("The detector matches positional inserts and only those")
    void shouldMatchPositionalFormsOnly_forTheDetectorItself() {
        for (String positional : List.of(
                "INSERT INTO property VALUES ('a', 'b')",
                "insert into `security` values (1)",
                "INSERT IGNORE INTO secRole VALUES (1, 'x')",
                "REPLACE INTO lst_gender VALUES ('M')",
                "INSERT INTO property\n  VALUES ('a')")) {
            assertThat(POSITIONAL_INSERT.matcher(positional).find())
                    .describedAs(positional).isTrue();
        }
        for (String named : List.of(
                "INSERT INTO property (name, value) VALUES ('a', 'b')",
                "INSERT IGNORE INTO secRole (role_name) VALUES ('x')",
                "INSERT INTO property SET name = 'a'",
                "INSERT INTO property (name) SELECT name FROM other")) {
            assertThat(POSITIONAL_INSERT.matcher(named).find())
                    .describedAs(named).isFalse();
        }
    }
}
