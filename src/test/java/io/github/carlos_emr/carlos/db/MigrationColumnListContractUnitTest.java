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
            "\\b(?:INSERT(?:\\s+IGNORE)?|REPLACE)\\s+INTO\\s+"
                    // the target may be schema-qualified, with or without
                    // backticks on either part: `carlos`.`property`,
                    // carlos.property, `property`, property. import-o19
                    // itself writes schema-qualified SQL, so that is the
                    // style a migration author is most likely to copy, and
                    // an unqualified-only pattern would not see it.
                    + "(?:`?\\w+`?\\s*\\.\\s*)?`?\\w+`?\\s+VALUES\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Comment forms stripped before matching, and the whitespace run collapsed when an offender is
     * reported. Compiled once: {@link #withoutComments} runs per migration file and the reporting
     * one runs per match, so leaving them as {@code String.replaceAll} recompiled the pattern on
     * every iteration.
     */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern DASH_COMMENT = Pattern.compile("(?m)--[^\\n]*");
    private static final Pattern HASH_COMMENT = Pattern.compile("(?m)#[^\\n]*");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

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
        String out = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        out = DASH_COMMENT.matcher(out).replaceAll(" ");
        // MySQL also treats `#` as a line comment; leaving it in
        // only ever produces a false offender, but a false
        // offender that fails the build is still a bug report
        // somebody has to chase.
        //
        // Quote-aware, unlike the two above: a `#` INSIDE a string
        // literal is data, and blanking from there to end-of-line would
        // swallow a positional INSERT later on the same line — the check
        // would then pass by not looking. `--` and `/*` cannot do this
        // here because the migrations that carry them never place a
        // statement after one on the same line, but `#` appears inside
        // quoted values.
        return stripHashComments(out);
    }

    /**
     * {@code out} with {@code #} line comments removed, ignoring a
     * {@code #} inside a single- or double-quoted SQL string.
     */
    private static String stripHashComments(String out) {
        StringBuilder sb = new StringBuilder(out.length());
        char quote = 0;
        boolean inComment = false;
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (inComment) {
                if (c == '\n') {
                    inComment = false;
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
                continue;
            }
            if (quote != 0) {
                // a doubled quote ('' or "") is an escaped quote, not the end
                if (c == '\\' && i + 1 < out.length()) {
                    sb.append(c).append(out.charAt(++i));
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
                sb.append(c);
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                sb.append(c);
                continue;
            }
            if (c == '#') {
                inComment = true;
                sb.append(' ');
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
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
                offenders.add(name + ": "
                        + WHITESPACE_RUN.matcher(m.group()).replaceAll(" "));
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
    @DisplayName("A # inside a string does not hide a later positional INSERT")
    void shouldStillSeeThePositionalInsert_whenAHashSitsInsideAString()
            throws Exception {
        // blanking from the `#` to end-of-line would swallow the INSERT
        // that follows it, and the contract would pass by not looking
        String sql = "INSERT INTO a (x) VALUES ('#'); INSERT INTO b VALUES (1);";
        java.lang.reflect.Method m = MigrationColumnListContractUnitTest.class
                .getDeclaredMethod("withoutComments", String.class);
        m.setAccessible(true);
        String stripped = (String) m.invoke(null, sql);
        assertThat(POSITIONAL_INSERT.matcher(stripped).find())
                .describedAs("the positional INSERT after a quoted '#' "
                        + "was hidden by comment stripping: " + stripped)
                .isTrue();
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
                "INSERT INTO property\n  VALUES ('a')",
                // schema-qualified, the style import-o19 itself writes:
                // an unqualified-only pattern read straight past these
                "INSERT INTO `carlos`.`property` VALUES ('a')",
                "INSERT INTO carlos.property VALUES ('a')",
                "REPLACE INTO `carlos`.security VALUES (1)")) {
            assertThat(POSITIONAL_INSERT.matcher(positional).find())
                    .describedAs(positional).isTrue();
        }
        for (String named : List.of(
                "INSERT INTO property (name, value) VALUES ('a', 'b')",
                "INSERT IGNORE INTO secRole (role_name) VALUES ('x')",
                "INSERT INTO property SET name = 'a'",
                "INSERT INTO property (name) SELECT name FROM other",
                "INSERT INTO `carlos`.`property` (name) VALUES ('a')")) {
            assertThat(POSITIONAL_INSERT.matcher(named).find())
                    .describedAs(named).isFalse();
        }
    }
}
