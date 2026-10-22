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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The classification rows of {@code db/reporting/carlos_report_grants.sql}, the list that decides
 * what the Query by Example reporting account may read (issue #3372). Every table of the
 * clinical schema is a row, {@code reportable} or {@code restricted}; a reportable row can name
 * columns that stay hidden. MariaDB grants the account exactly what the rows allow, and
 * {@link ReportingConnectionService} refuses an account whose grants reach past them, so the
 * same file drives both the grants and the check of the grants.
 *
 * <p>The file's header documents the one-row-per-line format read here, and the classification
 * tests keep every row in it. A row this reader cannot parse is an error, never skipped: a
 * reader that skipped rows could take a restricted table for an unclassified one.</p>
 *
 * @since 2026-10-22
 */
final class ReportingGrantList {
    /** Where the grant list sits on the application classpath (WEB-INF/classes in the WAR). */
    static final String RESOURCE = "/db/reporting/carlos_report_grants.sql";

    private static final String HEADER =
            "INSERT INTO carlos_report_tables (table_name, access, excluded_columns, reason) VALUES";
    private static final Pattern ROW = Pattern.compile(
            "^\\('(?<table>[A-Za-z0-9_]+)', '(?<access>reportable|restricted)', "
                    + "(?:NULL|'(?<columns>[A-Za-z0-9_]+(?:,[A-Za-z0-9_]+)*+)'), "
                    + "(?:NULL|'(?<reason>[^']+)')\\)(?<end>[,;])$");

    // Two tables whose names differ only in case. Under lower_case_table_names they would be one
    // table, so neither row can say which of them a lower-cased grant names.
    private static final Entry AMBIGUOUS = new Entry("", "restricted", Set.of(), "ambiguous");

    // The shipped list never changes while the application runs; parse it once. Two threads
    // racing here both parse the same file, which is harmless.
    private static volatile ReportingGrantList classpathList;

    /** One classification row. */
    record Entry(String table, String access, Set<String> excludedColumns, String reason) {
        boolean reportable() {
            return "reportable".equals(access);
        }

        /**
         * Whether the row hides {@code column}. MariaDB column names are case-insensitive, so a
         * grant can spell a hidden column in any case.
         */
        @SuppressFBWarnings(value = "IMPROPER_UNICODE",
                justification = "MariaDB compares column names case-insensitively; the list holds ASCII identifiers")
        boolean hides(String column) {
            // FindSecBugs IMPROPER_UNICODE: MariaDB column names compare case-insensitively; the list is ASCII.
            for (String excluded : excludedColumns) {
                if (excluded.equalsIgnoreCase(column)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Map<String, Entry> entries;
    private final Map<String, Entry> byFoldedName;

    private ReportingGrantList(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(entries);
        Map<String, Entry> folded = new HashMap<>();
        for (Entry entry : entries.values()) {
            folded.merge(fold(entry.table()), entry, (first, second) -> AMBIGUOUS);
        }
        this.byFoldedName = Collections.unmodifiableMap(folded);
    }

    /**
     * The grant list the application ships, read from the classpath once.
     *
     * @return the shipped classification
     * @throws IOException if the list is not on the classpath or cannot be read
     * @throws IllegalArgumentException if the list is not in the documented format
     */
    static ReportingGrantList fromClasspath() throws IOException {
        ReportingGrantList list = classpathList;
        if (list == null) {
            try (InputStream in = ReportingGrantList.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new FileNotFoundException(RESOURCE + " is not on the classpath");
                }
                list = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList(), RESOURCE);
            }
            classpathList = list;
        }
        return list;
    }

    /**
     * Reads a grant list file.
     *
     * @param file the file to read
     * @return its classification
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file is not in the documented format
     */
    static ReportingGrantList read(Path file) throws IOException {
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8), file.toString());
    }

    /**
     * Parses the classification rows: the lines after the {@code INSERT INTO carlos_report_tables}
     * header, up to and including the row that ends with {@code ;}.
     *
     * @param lines the file's lines
     * @param source where the lines came from, for error messages
     * @return the classification
     * @throws IllegalArgumentException if the header is missing, a line in the row block is not a
     *         well-formed row, a table is listed twice, or the block never ends
     */
    static ReportingGrantList parse(List<String> lines, String source) {
        int start = lines.indexOf(HEADER);
        if (start < 0) {
            throw new IllegalArgumentException(source + " must keep its '" + HEADER + "' line");
        }
        Map<String, Entry> rows = new LinkedHashMap<>();
        for (int i = start + 1; i < lines.size(); i++) {
            Matcher row = ROW.matcher(lines.get(i));
            if (!row.matches()) {
                throw new IllegalArgumentException(source + " line " + (i + 1) + " is not a classification row");
            }
            String columns = row.group("columns");
            Entry entry = new Entry(row.group("table"), row.group("access"),
                    columns == null ? Set.of()
                            : Collections.unmodifiableSet(new LinkedHashSet<>(List.of(columns.split(",")))),
                    row.group("reason"));
            if (rows.put(entry.table(), entry) != null) {
                throw new IllegalArgumentException(source + " lists " + entry.table() + " twice");
            }
            if (";".equals(row.group("end"))) {
                return new ReportingGrantList(rows);
            }
        }
        throw new IllegalArgumentException(source + ": the classification rows never end with ';'");
    }

    /**
     * Every row, in file order.
     *
     * @return table name (exact spelling) to its row
     */
    Map<String, Entry> entries() {
        return entries;
    }

    /**
     * The row for a table name as MariaDB prints it in a grant.
     *
     * @param table the table name
     * @param ignoreCase {@code true} when the server folds table names
     *        ({@code lower_case_table_names} other than 0), so a grant names the table in lower case
     * @return the row, or empty when no row names the table (or, ignoring case, two rows do)
     */
    Optional<Entry> find(String table, boolean ignoreCase) {
        Entry entry = ignoreCase ? byFoldedName.get(fold(table)) : entries.get(table);
        return entry == null || entry == AMBIGUOUS ? Optional.empty() : Optional.of(entry);
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Folds ASCII table names the way MariaDB lower_case_table_names does")
    private static String fold(String table) {
        // FindSecBugs IMPROPER_UNICODE: table names are ASCII; this mirrors lower_case_table_names.
        return table.toLowerCase(Locale.ROOT);
    }
}
