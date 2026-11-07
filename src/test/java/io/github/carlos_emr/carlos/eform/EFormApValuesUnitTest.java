/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform;

import java.util.List;
import java.util.LinkedHashMap;
import io.github.carlos_emr.carlos.commn.dao.ClinicalDataRow;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class EFormApValuesUnitTest extends CarlosUnitTestBase {
    private final EFormValueDao dao = mock(EFormValueDao.class);
    private final ParameterizedSql query = new ParameterizedSql("SELECT notes FROM eform_values WHERE demographic_no=?", List.of(101));

    @BeforeEach
    void configureDao() { registerMock(EFormValueDao.class, dao); }

    private ClinicalDataRow row(String text) {
        var columns = new LinkedHashMap<String, String>();
        columns.put("notes", text);
        columns.put("nullable", null);
        return new ClinicalDataRow(columns);
    }

    @Test
    void shouldUseLastScalarRowAndEveryJsonRow_whenQueryReturnsMultipleRows() {
        when(dao.findDatabaseApValues(eq(query), anyList())).thenReturn(List.of(row("first"), row("  last  ")));
        assertThat(EFormUtil.getValuesOrNull(List.of("NOTES", "nullable"), query)).containsExactly("last", "");
        var json = EFormUtil.getJsonValuesOrNull(List.of("notes"), query);
        assertThat(json).hasSize(2);
        assertThat(json.get(0).get("notes").asText()).isEqualTo("first");
        assertThat(json.get(1).get("notes").asText()).isEqualTo("last");
    }

    @Test
    void shouldDistinguishEmptyFromFailure_whenStrictClinicalRenderingNeedsCompleteValues() {
        when(dao.findDatabaseApValues(eq(query), anyList())).thenReturn(List.of());
        assertThat(EFormUtil.getValuesOrNull(List.of("notes"), query)).isEmpty();
        assertThat(EFormUtil.getJsonValuesOrNull(List.of("notes"), query)).isEmpty();
        when(dao.findDatabaseApValues(eq(query), anyList())).thenThrow(new jakarta.persistence.PersistenceException("synthetic failure"));
        assertThat(EFormUtil.getValuesOrNull(List.of("notes"), query)).isNull();
        assertThat(EFormUtil.getJsonValuesOrNull(List.of("notes"), query)).isNull();
        assertThat(EFormUtil.getValues(List.of("notes"), query)).isEmpty();
        assertThat(EFormUtil.getJsonValues(List.of("notes"), query)).isEmpty();
    }

    @Test
    void shouldKeepLegacyPlaceholderAndStrictFailure_whenRequestedColumnIsMissing() {
        when(dao.findDatabaseApValues(eq(query), anyList())).thenReturn(List.of(row("present")));
        assertThat(EFormUtil.getValues(List.of("missing"), query)).containsExactly("<(missing)NotFound>");
        assertThat(EFormUtil.getValuesOrNull(List.of("missing"), query)).isNull();
        assertThat(EFormUtil.getJsonValuesOrNull(List.of("missing"), query)).isNull();
        assertThat(EFormUtil.getJsonValues(List.of("missing"), query).get(0).get("missing").asText()).isEqualTo("<(missing)NotFound>");
    }
}
