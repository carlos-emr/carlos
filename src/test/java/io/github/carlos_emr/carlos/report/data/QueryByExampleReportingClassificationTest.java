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
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Keeps the Query by Example reporting-account grant list ({@code carlos_report_grants.sql})
 * complete and consistent with the schema and the application validator (issue #3372).
 *
 * <p>The database grants the reporting account only the tables that file lists as reportable,
 * so a table a new migration creates is unreadable to Query by Example until someone classifies
 * it. This test makes that decision explicit: the build fails until every table a Flyway
 * migration creates is listed as {@code reportable} or {@code restricted}. It also fails when the
 * restricted tables and {@link QueryByExampleSqlValidator#SECURITY_SENSITIVE_TABLES} disagree,
 * when a hidden column does not exist, and when a column whose name looks like a credential
 * would be granted.</p>
 */
@Tag("unit")
@Tag("report")
@Tag("security")
@DisplayName("Query by Example reporting-account table classification")
class QueryByExampleReportingClassificationTest {
    private static final Path GRANTS = ReportingGrantList.FILE;
    private static final Path MIGRATIONS = Path.of("database/mysql/migration");
    private static final List<String> MIGRATION_LOCATIONS = List.of("common", "on", "bc");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(?<table>[A-Za-z0-9_]+)`?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "\\bALTER\\s+TABLE\\s+`?(?<table>[A-Za-z0-9_]+)`?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDED_COLUMN = Pattern.compile(
            "\\b(?:ADD(?:\\s+COLUMN)?|CHANGE(?:\\s+COLUMN)?\\s+(?:IF\\s+EXISTS\\s+)?`?[A-Za-z0-9_]+`?|RENAME\\s+COLUMN\\s+"
                    + "`?[A-Za-z0-9_]+`?\\s+TO)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(?<column>[A-Za-z0-9_]+)`?",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> NOT_COLUMNS = Set.of("index", "key", "unique", "primary", "constraint",
            "foreign", "fulltext", "spatial", "check", "partition", "period", "system");
    /** Name parts that mark a credential, token, key or live session identifier. */
    private static final Set<String> CREDENTIAL_PARTS = Set.of("password", "passwd", "pwd", "secret", "token",
            "nonce", "pin", "mfa", "credential", "credentials", "sessionid", "session", "apikey", "privkey");
    private static final List<List<String>> CREDENTIAL_PHRASES = List.of(
            List.of("private", "key"), List.of("priv", "key"), List.of("api", "key"), List.of("signature", "image"));

    private static Map<String, ReportingGrantList.Entry> classified;
    private static Map<String, Set<String>> migrationColumns;

    @BeforeAll
    static void readGrantListAndMigrations() throws IOException {
        classified = ReportingGrantList.read();
        migrationColumns = readMigrationTables();
    }

    @Test
    @DisplayName("classifies every table that a Flyway migration creates")
    void shouldClassifyEveryMigrationTable_forReportingGrants() {
        Set<String> unclassified = new TreeSet<>(migrationColumns.keySet());
        unclassified.removeAll(classified.keySet());

        assertThat(unclassified)
                .as("Tables created by a migration must be listed in %s as 'reportable' or 'restricted' "
                        + "(exact spelling). Until then Query by Example cannot read them.", GRANTS)
                .isEmpty();
    }

    @Test
    @DisplayName("lists only tables that a Flyway migration creates")
    void shouldListOnlyExistingTables_forReportingGrants() {
        Set<String> unknown = new TreeSet<>(classified.keySet());
        unknown.removeAll(migrationColumns.keySet());

        assertThat(unknown)
                .as("Every row in %s must name a table a migration creates, spelled exactly as the schema does", GRANTS)
                .isEmpty();
    }

    @Test
    @DisplayName("restricts exactly the tables the application validator refuses")
    void shouldRestrictExactlyValidatorTables_forDefenseInDepth() {
        Set<String> restricted = classified.values().stream()
                .filter(entry -> !entry.reportable())
                .map(entry -> entry.table().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(restricted)
                .as("The restricted rows in %s and QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES must agree", GRANTS)
                .containsExactlyInAnyOrderElementsOf(QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES);
    }

    @Test
    @DisplayName("names each table once and explains every restriction")
    void shouldExplainEveryRestriction_whenTableIsNotFullyReportable() {
        Set<String> folded = new LinkedHashSet<>();
        for (ReportingGrantList.Entry entry : classified.values()) {
            assertThat(folded.add(entry.table().toLowerCase(Locale.ROOT)))
                    .as("%s is listed more than once (names differing only by case count as one)", entry.table())
                    .isTrue();
            if (entry.reportable() && entry.excludedColumns().isEmpty()) {
                assertThat(entry.reason()).as("plain reportable row %s carries no reason", entry.table()).isNull();
            } else {
                assertThat(entry.reason()).as("row %s must say why it is restricted", entry.table()).isNotBlank();
            }
            if (!entry.reportable()) {
                assertThat(entry.excludedColumns()).as("restricted row %s hides no columns", entry.table()).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("hides only columns that exist")
    void shouldHideExistingColumns_whenTableExcludesColumns() {
        for (ReportingGrantList.Entry entry : classified.values()) {
            Set<String> columns = migrationColumns.getOrDefault(entry.table(), Set.of()).stream()
                    .map(column -> column.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            for (String excluded : entry.excludedColumns()) {
                assertThat(columns)
                        .as("%s hides %s, which no migration creates: a typo would grant the real column",
                                entry.table(), excluded)
                        .contains(excluded.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    @DisplayName("grants no column whose name looks like a credential, token or session identifier")
    void shouldNotGrantCredentialColumns_whenTableIsReportable() {
        List<String> exposed = new ArrayList<>();
        migrationColumns.forEach((table, columns) -> {
            ReportingGrantList.Entry entry = classified.get(table);
            if (entry == null || !entry.reportable()) {
                return;
            }
            Set<String> hidden = entry.excludedColumns().stream()
                    .map(column -> column.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            for (String column : columns) {
                if (looksLikeCredential(column) && !hidden.contains(column.toLowerCase(Locale.ROOT))) {
                    exposed.add(table + "." + column);
                }
            }
        });

        assertThat(exposed)
                .as("These reportable columns look like credentials. Restrict the table or add the column to "
                        + "its excluded columns in %s", GRANTS)
                .isEmpty();
    }

    @Test
    @DisplayName("recognizes credential-like column names by their parts, not by substring")
    void shouldMatchCredentialParts_forCamelAndSnakeCaseNames() {
        assertThat(looksLikeCredential("mfaSecret")).isTrue();
        assertThat(looksLikeCredential("url_name_password")).isTrue();
        assertThat(looksLikeCredential("session_id")).isTrue();
        assertThat(looksLikeCredential("privateKey")).isTrue();
        assertThat(looksLikeCredential("signatureImage")).isTrue();
        assertThat(looksLikeCredential("Obsessions")).isFalse();
        assertThat(looksLikeCredential("pinkEye")).isFalse();
        assertThat(looksLikeCredential("signature")).isFalse();
        assertThat(looksLikeCredential("reportHash")).isFalse();
    }

    private static boolean looksLikeCredential(String column) {
        List<String> parts = Arrays.stream(column.split("_|(?<=[a-z0-9])(?=[A-Z])"))
                .filter(part -> !part.isEmpty())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .toList();
        if (parts.stream().anyMatch(CREDENTIAL_PARTS::contains)) {
            return true;
        }
        for (List<String> phrase : CREDENTIAL_PHRASES) {
            for (int i = 0; i + phrase.size() <= parts.size(); i++) {
                if (parts.subList(i, i + phrase.size()).equals(phrase)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Table name to the columns its CREATE TABLE and later ALTER TABLE statements give it. */
    private static Map<String, Set<String>> readMigrationTables() throws IOException {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        for (String location : MIGRATION_LOCATIONS) {
            try (Stream<Path> files = Files.list(MIGRATIONS.resolve(location))) {
                for (Path file : files.filter(path -> path.getFileName().toString().matches("V[0-9.]+__.*\\.sql"))
                        .sorted().toList()) {
                    readMigration(withoutComments(Files.readString(file, StandardCharsets.UTF_8)), tables);
                }
            }
        }
        assertThat(tables).as("no CREATE TABLE found under %s", MIGRATIONS).hasSizeGreaterThan(300);
        return tables;
    }

    private static void readMigration(String sql, Map<String, Set<String>> tables) {
        Matcher create = CREATE_TABLE.matcher(sql);
        while (create.find()) {
            Set<String> columns = tables.computeIfAbsent(create.group("table"), table -> new LinkedHashSet<>());
            int open = sql.indexOf('(', create.end());
            int nextStatement = sql.indexOf(';', create.end());
            if (open < 0 || (nextStatement >= 0 && nextStatement < open)) {
                continue;
            }
            for (String definition : topLevelDefinitions(sql, open)) {
                Matcher name = Pattern.compile("^`?([A-Za-z0-9_]+)`?\\s").matcher(definition.strip() + " ");
                if (name.find() && !NOT_COLUMNS.contains(name.group(1).toLowerCase(Locale.ROOT))) {
                    columns.add(name.group(1));
                }
            }
        }
        Matcher alter = ALTER_TABLE.matcher(sql);
        while (alter.find()) {
            int end = statementEnd(sql, alter.end());
            Set<String> columns = tables.computeIfAbsent(alter.group("table"), table -> new LinkedHashSet<>());
            Matcher added = ADDED_COLUMN.matcher(sql.substring(alter.end(), end));
            while (added.find()) {
                if (!NOT_COLUMNS.contains(added.group("column").toLowerCase(Locale.ROOT))) {
                    columns.add(added.group("column"));
                }
            }
        }
        // ALTER TABLE on a table no migration creates (none today) is not a table of the schema.
        tables.values().removeIf(Set::isEmpty);
    }

    /**
     * The SQL with {@code --}, {@code #} and {@code /* *}{@code /} comments blanked out (quoted text
     * is kept), so a comma or a word in a comment is never read as a column. MySQL executable
     * comments ({@code /*!...*}{@code /}) are kept: they hold real statements.
     */
    private static String withoutComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        char quote = 0;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && quote != '`' && next != 0) {
                    out.append(next);
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
                i++;
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                out.append(c);
                i++;
            } else if ((c == '-' && next == '-' && (i + 2 >= sql.length() || Character.isWhitespace(sql.charAt(i + 2))))
                    || c == '#') {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*' && (i + 2 >= sql.length() || sql.charAt(i + 2) != '!')) {
                int close = sql.indexOf("*/", i + 2);
                i = close < 0 ? sql.length() : close + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** The comma-separated definitions inside the parentheses that open at {@code open}. */
    private static List<String> topLevelDefinitions(String sql, int open) {
        List<String> definitions = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        StringBuilder current = new StringBuilder();
        for (int i = open; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
            } else if (c == '(') {
                if (depth++ > 0) {
                    current.append(c);
                }
            } else if (c == ')') {
                if (--depth == 0) {
                    definitions.add(current.toString());
                    return definitions;
                }
                current.append(c);
            } else if (c == ',' && depth == 1) {
                definitions.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        return definitions;
    }

    /** End of the statement (or of the string literal holding it, for prepared DDL). */
    private static int statementEnd(String sql, int from) {
        int semicolon = sql.indexOf(';', from);
        int quote = sql.indexOf('\'', from);
        int end = sql.length();
        if (semicolon >= 0) {
            end = semicolon;
        }
        if (quote >= 0 && quote < end) {
            end = quote;
        }
        return end;
    }
}
