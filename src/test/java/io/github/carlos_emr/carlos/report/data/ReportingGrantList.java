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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-side reader for the classification rows of {@code db/reporting/carlos_report_grants.sql},
 * the list that decides which tables the Query by Example reporting account is granted
 * (issue #3372). The file's header documents the one-row-per-line format this reads.
 */
final class ReportingGrantList {
    /** The grant list, relative to the project directory Maven runs tests from. */
    static final Path FILE = Path.of("src/main/resources/db/reporting/carlos_report_grants.sql");

    private static final String HEADER =
            "INSERT INTO carlos_report_tables (table_name, access, excluded_columns, reason) VALUES";
    private static final Pattern ROW = Pattern.compile(
            "^\\('(?<table>[A-Za-z0-9_]+)', '(?<access>reportable|restricted)', "
                    + "(?:NULL|'(?<columns>[A-Za-z0-9_]+(?:,[A-Za-z0-9_]+)*)'), "
                    + "(?:NULL|'(?<reason>[^']+)')\\)(?<end>[,;])$");

    /** One classification row. */
    record Entry(String table, String access, Set<String> excludedColumns, String reason) {
        boolean reportable() {
            return "reportable".equals(access);
        }
    }

    private ReportingGrantList() {
    }

    /**
     * Reads every classification row, in file order.
     *
     * @return table name (exact spelling) to its row
     * @throws IOException if the file cannot be read
     * @throws AssertionError if a line in the row block is not a well-formed row, a table is listed
     *         twice, or the block never ends
     */
    static Map<String, Entry> read() throws IOException {
        List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        int start = lines.indexOf(HEADER);
        if (start < 0) {
            throw new AssertionError(FILE + " must keep its '" + HEADER + "' line");
        }
        Map<String, Entry> rows = new LinkedHashMap<>();
        for (int i = start + 1; i < lines.size(); i++) {
            Matcher row = ROW.matcher(lines.get(i));
            if (!row.matches()) {
                throw new AssertionError(FILE + " line " + (i + 1) + " is not a classification row: " + lines.get(i));
            }
            String columns = row.group("columns");
            Entry entry = new Entry(row.group("table"), row.group("access"),
                    columns == null ? Set.of()
                            : Collections.unmodifiableSet(new LinkedHashSet<>(List.of(columns.split(",")))),
                    row.group("reason"));
            if (rows.put(entry.table(), entry) != null) {
                throw new AssertionError(FILE + " lists " + entry.table() + " twice");
            }
            if (";".equals(row.group("end"))) {
                return rows;
            }
        }
        throw new AssertionError(FILE + ": the classification rows never end with ';'");
    }
}
