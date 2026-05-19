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

@Tag("unit")
@DisplayName("DatabaseAP SQL parameterization")
class DatabaseAPUnitTest {

    @Test
    @DisplayName("should parameterize quoted and unquoted placeholders")
    void shouldParameterizeQuotedAndUnquotedPlaceholders() {
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
    @DisplayName("should preserve legacy output placeholder cleanup")
    void shouldPreserveLegacyOutputPlaceholderCleanup() {
        ParameterizedSql sql = DatabaseAP.parameterizeSql(
                "select ${unknown_column} as value",
                Map.of());

        assertThat(sql.getSql()).isEqualTo("select \"unknown_column\" as value");
        assertThat(sql.getParams()).isEmpty();
    }
}
