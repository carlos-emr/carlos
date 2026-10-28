/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@DisplayName("DatabaseAP SQL parameterization")
class DatabaseAPUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldKeepInsertedDataLiteral_inTheCompatibilityReplacer() {
        for (String value : List.of("${value}", "${other}", "${", "$1\\tail", "</textarea><script>alert(1)</script>")) {
            assertThat(DatabaseAP.parserReplace("value", value, "${value}|${value}"))
                    .isEqualTo(value + "|" + value);
        }
        assertThat(DatabaseAP.parserReplace("value", null, "a${value}b${unknown}")).isEqualTo("ab${unknown}");
    }

    @Test
    void shouldRenderOriginalDuplicateAndCrossAliasMarkers_withoutParsingInsertedValues() {
        assertThat(DatabaseAP.renderOutput("${first}|${second}|${first}",
                Map.of("first", "${second}", "second", "${first}")))
                .isEqualTo("${second}|${first}|${second}");
        assertThat(DatabaseAP.renderOutput("${first}${first}",
                List.of("first", "first"), List.of("${first}", "later value")))
                .isEqualTo("${first}${first}");
        assertThat(DatabaseAP.renderOutput("a${first}b${first}",
                List.of("first", "first"), Arrays.asList(null, "later value"))).isEqualTo("ab");
    }

    @Test
    void shouldPreserveUnknownTokensAndMalformedData_andKeepNullAndEmptyValuesEmpty() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("empty", ""); values.put("nil", null); values.put("value", "$1\\tail${unfinished");
        assertThat(DatabaseAP.renderOutput("${empty}${nil}${value}|${unknown}", values))
                .isEqualTo("$1\\tail${unfinished|${unknown}");
        assertThat(DatabaseAP.renderOutput("unchanged ${unfinished", values)).isEqualTo("unchanged ${unfinished");
        assertThat(DatabaseAP.renderOutput("constant", List.of(), List.of())).isEqualTo("constant");
        assertThatThrownBy(() -> DatabaseAP.renderOutput("${value}", List.of("value"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should parameterize quoted and unquoted placeholders")
    void shouldParameterize_quotedAndUnquotedPlaceholders() {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("provider", "prov'1");
        replacements.put("demographic", "123");

        ParameterizedSql sql = DatabaseAP.parameterizeSql(
                "select * from provider where provider_no='${provider}' and demographic_no=${demographic}",
                replacements);

        assertThat(sql.getSql())
                .isEqualTo("select * from provider where provider_no=? and demographic_no=?");
        assertThat(sql.getParams()).containsExactly("prov'1", "123");
    }

    @Test
    @DisplayName("should parameterize wildcard placeholders inside quoted literals")
    void shouldParameterize_wildcardPlaceholdersInsideQuotedLiterals() {
        ParameterizedSql sql = DatabaseAP.parameterizeSql(
                "select * from demographic where last_name like '%${name}%'",
                Map.of("name", "Smith"));

        assertThat(sql.getSql()).isEqualTo("select * from demographic where last_name like ?");
        assertThat(sql.getParams()).containsExactly("%Smith%");
    }

    @Test
    @DisplayName("should parameterize multiple placeholders inside one quoted literal")
    void shouldParameterize_multiplePlaceholdersInsideOneQuotedLiteral() {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("code", "A'1");
        replacements.put("suffix", "B2");

        ParameterizedSql sql = DatabaseAP.parameterizeSql(
                "select * from demographic where chart_no like '%${code}-${suffix}%'",
                replacements);

        assertThat(sql.getSql()).isEqualTo("select * from demographic where chart_no like ?");
        assertThat(sql.getParams()).containsExactly("%A'1-B2%");
    }

    @Test
    @DisplayName("should preserve escaped quote before placeholder inside quoted literal")
    void shouldPreserve_escapedQuoteBeforePlaceholderInsideQuotedLiteral() {
        ParameterizedSql sql = DatabaseAP.parameterizeSql(
                "select * from demographic where note = 'it''s ${value}'",
                Map.of("value", "ok"));

        assertThat(sql.getSql()).isEqualTo("select * from demographic where note = ?");
        assertThat(sql.getParams()).containsExactly("it's ok");
    }

    @Test
    @DisplayName("should preserve legacy null replacement inside quoted literals")
    void shouldPreserve_legacyNullReplacementInsideQuotedLiterals() {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("name", null);

        ParameterizedSql sql = DatabaseAP.parameterizeSql(
                "select * from demographic where last_name like '%${name}%'",
                replacements);

        assertThat(sql.getSql()).isEqualTo("select * from demographic where last_name like ?");
        assertThat(sql.getParams()).containsExactly("%%");
    }

    @Test
    @DisplayName("should preserve legacy output placeholder cleanup")
    void shouldPreserve_legacyOutputPlaceholderCleanup() {
        ParameterizedSql sql = DatabaseAP.parameterizeSql(
                "select ${unknown_column} as value",
                Map.of());

        assertThat(sql.getSql()).isEqualTo("select \"unknown_column\" as value");
        assertThat(sql.getParams()).isEmpty();
    }
}
