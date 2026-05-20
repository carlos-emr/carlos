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

import java.util.LinkedHashMap;
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
