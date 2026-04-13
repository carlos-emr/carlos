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

import java.util.Vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RptReportCreator#getWhereValueClauseParameterized(String, Vector)}.
 * Covers quote-context detection, placeholder rewriting, numeric-allowlist enforcement
 * for unquoted contexts (legacy parity), and edge cases around missing parameter values.
 *
 * @since 2026-04-13
 */
@Tag("unit")
@Tag("report")
class RptReportCreatorParameterizedTest {

    @Test
    @DisplayName("should emit single ? placeholder and bind value for quoted string context")
    void shouldBindValue_forQuotedStringContext() {
        Vector<String> vec = new Vector<>();
        vec.add("Smith");

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.last_name='${lastName}'", vec);

        assertThat(result.getSql()).isEqualTo("demographic.last_name=?");
        assertThat(result.getParams()).containsExactly("Smith");
    }

    @Test
    @DisplayName("should bind numeric value for unquoted context")
    void shouldBindNumericValue_forUnquotedContext() {
        Vector<String> vec = new Vector<>();
        vec.add("42");

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.age>${age}", vec);

        assertThat(result.getSql()).isEqualTo("demographic.age>?");
        assertThat(result.getParams()).containsExactly("42");
    }

    @Test
    @DisplayName("should bind negative and decimal numeric values for unquoted context")
    void shouldAcceptNegativeAndDecimal_forUnquotedContext() {
        Vector<String> vec = new Vector<>();
        vec.add("-3.14");

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "t.score=${s}", vec);

        assertThat(result.getSql()).isEqualTo("t.score=?");
        assertThat(result.getParams()).containsExactly("-3.14");
    }

    @Test
    @DisplayName("should bind NULL when non-numeric value supplied in unquoted context (legacy allowlist)")
    void shouldBindNull_whenNonNumericSuppliedInUnquotedContext() {
        Vector<String> vec = new Vector<>();
        vec.add("1 OR 1=1");

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.age>${age}", vec);

        assertThat(result.getSql()).isEqualTo("demographic.age>?");
        assertThat(result.getParams()).hasSize(1);
        assertThat(result.getParams().get(0)).isNull();
    }

    @Test
    @DisplayName("should bind NULL when unquoted parameter value is missing from vector")
    void shouldBindNull_whenUnquotedParamMissingFromVector() {
        Vector<String> vec = new Vector<>();

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.age>${age}", vec);

        assertThat(result.getSql()).isEqualTo("demographic.age>?");
        assertThat(result.getParams()).hasSize(1);
        assertThat(result.getParams().get(0)).isNull();
    }

    @Test
    @DisplayName("should bind empty string when quoted parameter value is missing from vector")
    void shouldBindEmptyString_whenQuotedParamMissingFromVector() {
        Vector<String> vec = new Vector<>();

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.last_name='${lastName}'", vec);

        assertThat(result.getSql()).isEqualTo("demographic.last_name=?");
        assertThat(result.getParams()).containsExactly("");
    }

    @Test
    @DisplayName("should bind multiple placeholders in declaration order")
    void shouldBindMultiplePlaceholders_inOrder() {
        Vector<String> vec = new Vector<>();
        vec.add("Smith");
        vec.add("2026-01-01");
        vec.add("100");

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.last_name='${lastName}' and demographic.dob>='${dob}' and demographic.age<${age}",
                vec);

        assertThat(result.getSql()).isEqualTo(
                "demographic.last_name=? and demographic.dob>=? and demographic.age<?");
        assertThat(result.getParams()).containsExactly("Smith", "2026-01-01", "100");
    }

    @Test
    @DisplayName("should not attempt injection via quoted value — single quotes stay in bind param, not SQL")
    void shouldNotInjectViaQuotedValue() {
        Vector<String> vec = new Vector<>();
        vec.add("'; DROP TABLE demographic;--");

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.last_name='${lastName}'", vec);

        // Single ? placeholder, value preserved verbatim as bind param (PreparedStatement handles escaping)
        assertThat(result.getSql()).isEqualTo("demographic.last_name=?");
        assertThat(result.getParams()).containsExactly("'; DROP TABLE demographic;--");
    }

    @Test
    @DisplayName("should return template unchanged when no ${var} placeholders present")
    void shouldReturnUnchanged_whenNoPlaceholders() {
        Vector<String> vec = new Vector<>();

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.status='AC'", vec);

        assertThat(result.getSql()).isEqualTo("demographic.status='AC'");
        assertThat(result.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should stop processing on malformed placeholder without closing brace")
    void shouldStop_onMalformedPlaceholder() {
        Vector<String> vec = new Vector<>();
        vec.add("Smith");

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.last_name='${lastName", vec);

        // Malformed — no "}" found; loop breaks without mutation
        assertThat(result.getSql()).isEqualTo("demographic.last_name='${lastName");
        assertThat(result.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should strip surrounding single quotes only for quoted context, not unquoted")
    void shouldHandleMixedQuotedAndUnquoted() {
        Vector<String> vec = new Vector<>();
        vec.add("Smith");  // quoted
        vec.add("42");     // unquoted

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.last_name='${lastName}' and demographic.age>${age}", vec);

        assertThat(result.getSql()).isEqualTo(
                "demographic.last_name=? and demographic.age>?");
        assertThat(result.getParams()).containsExactly("Smith", "42");
    }

    @Test
    @DisplayName("should treat null parameter in quoted context as empty string")
    void shouldBindEmptyString_forNullValueInQuotedContext() {
        Vector<Object> vec = new Vector<>();
        vec.add(null);

        ParameterizedSql result = RptReportCreator.getWhereValueClauseParameterized(
                "demographic.last_name='${lastName}'", vec);

        assertThat(result.getSql()).isEqualTo("demographic.last_name=?");
        assertThat(result.getParams()).containsExactly("");
    }
}
