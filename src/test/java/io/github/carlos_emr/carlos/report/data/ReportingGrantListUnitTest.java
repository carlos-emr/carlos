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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReportingGrantList}, the reader both the grant audit and the
 * classification tests use for {@code db/reporting/carlos_report_grants.sql} (issue #3372).
 */
@Tag("unit")
@Tag("report")
@Tag("security")
@DisplayName("Query by Example reporting grant list")
class ReportingGrantListUnitTest {
    private static final String HEADER =
            "INSERT INTO carlos_report_tables (table_name, access, excluded_columns, reason) VALUES";

    private static ReportingGrantList parse(String... rows) {
        List<String> lines = new ArrayList<>(List.of("-- header comment", HEADER));
        lines.addAll(List.of(rows));
        lines.add("SELECT 1;");
        return ReportingGrantList.parse(lines, "test list");
    }

    @Test
    @DisplayName("reads the rows up to the one that ends with a semicolon")
    void shouldReadRows_untilClosingSemicolon() {
        ReportingGrantList list = parse(
                "('demographic', 'reportable', NULL, NULL),",
                "('casemgmt_note', 'reportable', 'password,lock_key', 'Note-lock credentials'),",
                "('security', 'restricted', NULL, 'Password hashes');");

        assertThat(list.entries()).containsOnlyKeys("demographic", "casemgmt_note", "security");
        assertThat(list.entries().get("casemgmt_note").excludedColumns()).containsExactly("password", "lock_key");
        assertThat(list.entries().get("security").reportable()).isFalse();
        assertThat(list.entries().get("demographic").reportable()).isTrue();
    }

    @Test
    @DisplayName("matches hidden columns in any case, as MariaDB does")
    void shouldMatchHiddenColumn_inAnyCase() {
        ReportingGrantList.Entry note = parse(
                "('casemgmt_note', 'reportable', 'password', 'Note-lock password');").entries().get("casemgmt_note");

        assertThat(note.hides("PASSWORD")).isTrue();
        assertThat(note.hides("password")).isTrue();
        assertThat(note.hides("note")).isFalse();
    }

    @Test
    @DisplayName("finds tables by exact name unless the server folds table names")
    void shouldFindTable_byExactNameUnlessFolded() {
        ReportingGrantList list = parse("('DigitalSignature', 'reportable', 'signatureImage', 'Signatures');");

        assertThat(list.find("DigitalSignature", false)).isPresent();
        assertThat(list.find("digitalsignature", false)).isEmpty();
        assertThat(list.find("digitalsignature", true)).isPresent();
        assertThat(list.find("other", true)).isEmpty();
    }

    @Test
    @DisplayName("finds neither of two rows whose names differ only in case when names are folded")
    void shouldFindNeither_whenFoldedNamesCollide() {
        ReportingGrantList list = parse(
                "('Report', 'reportable', NULL, NULL),",
                "('report', 'restricted', NULL, 'A different table on a case-sensitive server');");

        assertThat(list.find("report", true)).isEmpty();
        assertThat(list.find("Report", false)).hasValueSatisfying(row -> assertThat(row.reportable()).isTrue());
    }

    @Test
    @DisplayName("rejects a malformed row instead of skipping it")
    void shouldReject_whenRowIsMalformed() {
        assertThatThrownBy(() -> parse("('security', 'restricted', NULL, 'Password hashes')  ;"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 3");
        assertThatThrownBy(() -> parse("('security', 'hidden', NULL, NULL);"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a hidden-column list with an empty name")
    void shouldReject_whenHiddenColumnListHasEmptyName() {
        for (String columns : List.of(",password", "password,", "password,,lock_key")) {
            assertThatThrownBy(() -> parse("('casemgmt_note', 'reportable', '" + columns + "', 'Note-lock');"))
                    .as(columns)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("rejects a table listed twice")
    void shouldReject_whenTableIsListedTwice() {
        assertThatThrownBy(() -> parse("('security', 'restricted', NULL, 'x'),", "('security', 'reportable', NULL, NULL);"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security twice");
    }

    @Test
    @DisplayName("rejects a list whose rows never end or whose header is missing")
    void shouldReject_whenRowsNeverEndOrHeaderIsMissing() {
        assertThatThrownBy(() -> ReportingGrantList.parse(List.of(HEADER, "('a', 'reportable', NULL, NULL),"), "t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never end");
        assertThatThrownBy(() -> ReportingGrantList.parse(List.of("('a', 'reportable', NULL, NULL);"), "t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must keep");
    }

    @Test
    @DisplayName("loads the shipped list from the classpath")
    void shouldLoadShippedList_fromClasspath() throws IOException {
        ReportingGrantList shipped = ReportingGrantList.fromClasspath();

        assertThat(shipped.find("security", false)).hasValueSatisfying(row -> assertThat(row.reportable()).isFalse());
        assertThat(shipped.find("casemgmt_note", false))
                .hasValueSatisfying(row -> assertThat(row.hides("password")).isTrue());
        assertThat(ReportingGrantList.fromClasspath()).isSameAs(shipped);
    }
}
