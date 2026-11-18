/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Preserve the physical driver's AP column lookup, including qualified duplicate labels. */
@Tag("integration")
@Tag("dao")
@Tag("eform")
class EFormApColumnLookupIntegrationTest extends CarlosTestBase {
    @Autowired private EFormValueDao values;

    private final ParameterizedSql query = new ParameterizedSql(
            "SELECT d.last_name, p.last_name, d.last_name AS display_name"
                    + " FROM (SELECT 'FAKE first' AS last_name UNION ALL SELECT 'FAKE last' AS last_name) d"
                    + " CROSS JOIN (SELECT 'FAKE provider' AS last_name) p ORDER BY d.last_name", List.of());

    @Test
    void shouldResolveQualifiedAndAliasedColumns_whenPatientAndProviderLabelsOverlap() {
        var names = List.of("d.last_name", "p.last_name", "DISPLAY_NAME", "last_name");
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(EFormValueDao.class)).thenReturn(values);
            assertThat(EFormUtil.getValuesOrNull(names, query))
                    .containsExactly("FAKE last", "FAKE provider", "FAKE last", "FAKE last");
            var json = EFormUtil.getJsonValuesOrNull(names, query);
            assertThat(json).hasSize(2);
            assertThat(json.get(0).get("d.last_name").asText()).isEqualTo("FAKE first");
            assertThat(json.get(1).get("d.last_name").asText()).isEqualTo("FAKE last");
            assertThat(json.get(0).get("p.last_name").asText()).isEqualTo("FAKE provider");
            assertThat(json.get(1).get("DISPLAY_NAME").asText()).isEqualTo("FAKE last");
            assertThat(json.get(0).get("last_name").asText()).isEqualTo("FAKE first");
        }
    }

    @Test
    void shouldRetainStrictFailureAndLegacyPlaceholder_whenRequestedColumnIsMissing() {
        var names = List.of("d.last_name", "missing");
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(EFormValueDao.class)).thenReturn(values);
            assertThat(EFormUtil.getValuesOrNull(names, query)).isNull();
            assertThat(EFormUtil.getJsonValuesOrNull(names, query)).isNull();
            assertThat(EFormUtil.getValues(names, query)).containsExactly("FAKE last", "<(missing)NotFound>");
            var json = EFormUtil.getJsonValues(names, query);
            assertThat(json).hasSize(2);
            assertThat(json.get(0).get("d.last_name").asText()).isEqualTo("FAKE first");
            assertThat(json.get(1).get("missing").asText()).isEqualTo("<(missing)NotFound>");
        }
    }
}
