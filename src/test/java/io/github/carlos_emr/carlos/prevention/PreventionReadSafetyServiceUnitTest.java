/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prevention;

import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PreventionReadSafetyServiceUnitTest {
    @Test
    void shouldRefuseBeforeDateAggregation_whenPopulationHasUncertainTiming() {
        PreventionDao dao = mock(PreventionDao.class);
        when(dao.hasUncertainPreventionDates(null)).thenReturn(true);
        var safety = new PreventionReadSafetyService(dao);
        assertThatThrownBy(() -> safety.requireSafeSql(
                "select max(p.prevention_date) from preventions p where p.prevention_date > '2019-05-01'", null))
                .isInstanceOf(PreventionDatePrecisionException.class);
    }

    @Test
    void shouldKeepPatientScope_whenSqlReadsPreventionHistory() {
        PreventionDao dao = mock(PreventionDao.class);
        var safety = new PreventionReadSafetyService(dao);
        assertThatCode(() -> safety.requireSafeSql("select p.* from preventions p where p.demographic_no = ?", 7))
                .doesNotThrowAnyException();
        verify(dao).hasUncertainPreventionDates(7);
        verify(dao, never()).hasUncertainPreventionDates(null);
    }

    @Test
    void shouldRecognizeNestedAndQualifiedReferences_withQuotedTableNames() {
        assertThat(PreventionReadSafetyService.readsPreventions(
                "select * from demographic d where exists (select 1 from `clinic`.`preventions` p where p.demographic_no=d.demographic_no)"))
                .isTrue();
        assertThat(PreventionReadSafetyService.readsPreventions("select 'preventions' from demographic")).isFalse();
    }

    @Test
    void shouldRefuseUnsupportedDialect_whenPreventionReferenceCannotBeReviewed() {
        assertThatThrownBy(() -> PreventionReadSafetyService.readsPreventions("select !!! from preventions"))
                .isInstanceOf(PreventionDatePrecisionException.class);
    }

    @Test
    void shouldLeaveUnrelatedQueriesUnchanged_whenTheyDoNotReadPreventions() {
        PreventionDao dao = mock(PreventionDao.class);
        new PreventionReadSafetyService(dao).requireSafeSql("select * from allergies", null);
        verifyNoInteractions(dao);
    }
}
