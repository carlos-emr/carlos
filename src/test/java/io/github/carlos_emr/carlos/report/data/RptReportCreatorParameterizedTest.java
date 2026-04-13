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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RptReportCreator#getParameterizedWhereClause(String, Vector)}.
 *
 * <p>Validates that SQL template placeholders {@code ${...}} are replaced with
 * {@code ?} bind markers and that the corresponding parameter values are
 * collected correctly for JDBC PreparedStatement usage.</p>
 *
 * @since 2026-04-13
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("RptReportCreator.getParameterizedWhereClause")
class RptReportCreatorParameterizedTest {

    // ── Quoted (string) context ──────────────────────────────────────────

    @Nested
    @DisplayName("Quoted context (string values)")
    class QuotedContext {

        @Test
        @DisplayName("should replace quoted placeholder with ? and collect value")
        void shouldReplacePlaceholder_whenQuotedContext() {
            Vector vec = new Vector();
            vec.add("Smith");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.last_name like '${startDate1}'", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.last_name like ?");
            assertThat(pc.getParams()).containsExactly("Smith");
        }

        @Test
        @DisplayName("should handle multiple quoted placeholders in one clause")
        void shouldHandleMultiplePlaceholders_whenQuotedContext() {
            Vector vec = new Vector();
            vec.add("2024-01-01");
            vec.add("2024-12-31");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.date_of_birth >= '${startDate1}' and demographic.date_of_birth <= '${endDate1}'",
                            vec);

            assertThat(pc.getClause())
                    .isEqualTo("demographic.date_of_birth >= ? and demographic.date_of_birth <= ?");
            assertThat(pc.getParams()).containsExactly("2024-01-01", "2024-12-31");
        }

        @Test
        @DisplayName("should pass raw value without SQL escaping (JDBC driver handles it)")
        void shouldNotEscapeValue_whenQuotedContext() {
            Vector vec = new Vector();
            vec.add("O'Brien");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.last_name = '${startDate1}'", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.last_name = ?");
            // Value should NOT be escaped — PreparedStatement handles quoting
            assertThat(pc.getParams()).containsExactly("O'Brien");
        }

        @Test
        @DisplayName("should use empty string when value is null")
        void shouldUseEmptyString_whenValueIsNull() {
            Vector vec = new Vector();
            vec.add(null);

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.last_name = '${var}'", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.last_name = ?");
            assertThat(pc.getParams()).containsExactly("");
        }
    }

    // ── Unquoted (numeric) context ───────────────────────────────────────

    @Nested
    @DisplayName("Unquoted context (numeric values)")
    class UnquotedContext {

        @Test
        @DisplayName("should replace unquoted placeholder with ? and collect numeric value")
        void shouldReplacePlaceholder_whenNumericContext() {
            Vector vec = new Vector();
            vec.add("42");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.demographic_no = ${var}", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.demographic_no = ?");
            assertThat(pc.getParams()).containsExactly("42");
        }

        @Test
        @DisplayName("should reject non-numeric value and bind empty string")
        void shouldRejectNonNumericValue_whenUnquotedContext() {
            Vector vec = new Vector();
            vec.add("malicious; DROP TABLE");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.demographic_no = ${var}", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.demographic_no = ?");
            assertThat(pc.getParams()).containsExactly("");
        }

        @Test
        @DisplayName("should accept negative numeric value")
        void shouldAcceptNegativeNumeric_whenUnquotedContext() {
            Vector vec = new Vector();
            vec.add("-5");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.demographic_no > ${var}", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.demographic_no > ?");
            assertThat(pc.getParams()).containsExactly("-5");
        }

        @Test
        @DisplayName("should accept decimal numeric value")
        void shouldAcceptDecimalNumeric_whenUnquotedContext() {
            Vector vec = new Vector();
            vec.add("3.14");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "some_table.weight > ${var}", vec);

            assertThat(pc.getClause()).isEqualTo("some_table.weight > ?");
            assertThat(pc.getParams()).containsExactly("3.14");
        }
    }

    // ── No placeholders ──────────────────────────────────────────────────

    @Nested
    @DisplayName("No placeholders")
    class NoPlaceholders {

        @Test
        @DisplayName("should return clause unchanged with empty params when no placeholders")
        void shouldReturnUnchanged_whenNoPlaceholders() {
            Vector vec = new Vector();

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.status = 'AC'", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.status = 'AC'");
            assertThat(pc.getParams()).isEmpty();
        }
    }

    // ── Mixed context ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed contexts")
    class MixedContext {

        @Test
        @DisplayName("should handle mix of quoted and unquoted placeholders")
        void shouldHandleMixed_whenQuotedAndUnquoted() {
            Vector vec = new Vector();
            vec.add("Smith");
            vec.add("42");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.last_name = '${name}' and demographic.demographic_no = ${id}",
                            vec);

            assertThat(pc.getClause())
                    .isEqualTo("demographic.last_name = ? and demographic.demographic_no = ?");
            assertThat(pc.getParams()).containsExactly("Smith", "42");
        }
    }

    // ── Edge cases ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should use empty string when vec has fewer values than placeholders")
        void shouldUseEmptyString_whenVecTooSmall() {
            Vector vec = new Vector();
            // No values provided, but template has a placeholder

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.last_name = '${name}'", vec);

            assertThat(pc.getClause()).isEqualTo("demographic.last_name = ?");
            assertThat(pc.getParams()).containsExactly("");
        }

        @Test
        @DisplayName("should produce same clause structure as getWhereValueClause for table references")
        void shouldPreserveTableReferences_forFilterRouting() {
            Vector vec = new Vector();
            vec.add("2024-01-01");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographicExt.value = '${var}'", vec);

            // Filter routing in RptDownloadCSVServlet checks for table name prefixes
            assertThat(pc.getClause()).contains("demographicExt.");
            assertThat(pc.getParams()).hasSize(1);
        }

        @Test
        @DisplayName("should return immutable params list")
        void shouldReturnImmutableParams() {
            Vector vec = new Vector();
            vec.add("test");

            RptReportCreator.ParameterizedClause pc =
                    RptReportCreator.getParameterizedWhereClause(
                            "demographic.last_name = '${var}'", vec);

            List<Object> params = pc.getParams();
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> params.add("should fail"));
        }
    }

    // ── Backward compatibility with getWhereValueClause ──────────────────

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("should produce same table references as legacy method for filter routing")
        void shouldPreserveSameTableReferences_asLegacyMethod() {
            Vector vec = new Vector();
            vec.add("2024-01-01");
            vec.add("2024-12-31");

            String template = "demographic.date_of_birth >= '${startDate1}' and demographic.date_of_birth <= '${endDate1}'";

            String legacyResult = RptReportCreator.getWhereValueClause(template, vec);
            RptReportCreator.ParameterizedClause pc = RptReportCreator.getParameterizedWhereClause(template, vec);

            // Both should contain the same table references (used for routing in servlet)
            assertThat(legacyResult.contains("demographic.")).isTrue();
            assertThat(pc.getClause().contains("demographic.")).isTrue();

            // Parameterized version should have ? instead of inlined values
            assertThat(pc.getClause()).contains("?");
            assertThat(pc.getClause()).doesNotContain("2024-01-01");
            assertThat(pc.getClause()).doesNotContain("2024-12-31");
        }
    }
}
