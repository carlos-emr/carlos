/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Counts;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Operation;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CategoryDataLazyCountsUnitTest extends CarlosUnitTestBase {
    @org.junit.jupiter.api.BeforeEach
    void registerDateSearchService() {
        registerMock(io.github.carlos_emr.carlos.inboxhub.InboxDateSearchService.class,
                new io.github.carlos_emr.carlos.inboxhub.InboxDateSearchService(
                    org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao.class)));
    }

    private InboxCategoryDao dao;
    private CategoryData data;

    @BeforeEach
    void setup() throws SQLException {
        registerDateSearchService();
        dao = mock(InboxCategoryDao.class);
        registerMock(InboxCategoryDao.class, dao);
        when(dao.count(any(), any(), isNull())).thenAnswer(call ->
                new Counts(call.getArgument(1) == Operation.LAB_PATIENT ? 3 : 0, List.of()));
        when(dao.count(any(), eq(Operation.ABNORMAL), isNull())).thenReturn(new Counts(2, List.of()));
        data = new CategoryData("Synthetic", "", "", true, true, "999998", "N", "all",
                "2026-01-01", "2026-01-31");
    }

    @Test
    void shouldAvoidAggregateScan_whenPopulatingOrdinaryInbox() throws SQLException {
        assertThat(data.getAbnormalCount()).isZero();
        assertThat(data.getNormalCount()).isZero();
        verifyNoInteractions(dao);
        data.populateCountsAndPatients();
        assertThat(data.getTotalLabs()).isEqualTo(3);
        verify(dao, never()).count(any(), eq(Operation.ABNORMAL), any());
    }

    @Test
    void shouldShareOneScopedQuery_whenReadingBothLegacyGetters() throws SQLException {
        data.populateCountsAndPatients();
        assertThat(data.getNormalCount()).isEqualTo(1);
        assertThat(data.getAbnormalCount()).isEqualTo(2);
        assertThat(data.getNormalCount()).isEqualTo(1);
        verify(dao).count(argThat(filter -> filter.providerNo().equals("999998")
                && filter.startDate().equals("2026-01-01") && filter.endDate().equals("2026-01-31")
                && filter.patientSearch()), eq(Operation.ABNORMAL), isNull());
    }

    @Test
    void shouldRefreshLazyCount_withoutAccumulatingTotalsWhenRepopulated() throws SQLException {
        data.populateCountsAndPatients();
        assertThat(data.getAbnormalCount()).isEqualTo(2);
        when(dao.count(any(), eq(Operation.ABNORMAL), isNull())).thenReturn(new Counts(1, List.of()));
        data.populateCountsAndPatients();
        assertThat(data.getTotalLabs()).isEqualTo(3);
        assertThat(data.getAbnormalCount()).isEqualTo(1);
        assertThat(data.getNormalCount()).isEqualTo(2);
        verify(dao, times(2)).count(any(), eq(Operation.ABNORMAL), isNull());
    }

    @Test
    void shouldExposeFailure_andRetryRatherThanCachingZero() throws SQLException {
        data.populateCountsAndPatients();
        when(dao.count(any(), eq(Operation.ABNORMAL), isNull()))
                .thenThrow(new SQLException("synthetic count failure")).thenReturn(new Counts(2, List.of()));
        assertThatThrownBy(data::getAbnormalCount).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(SQLException.class);
        assertThat(data.getNormalCount()).isEqualTo(1);
    }
    @Test
    void shouldRefuseEveryPublishedTotal_whenRepopulationFails() throws SQLException {
        data.populateCountsAndPatients();
        when(dao.count(any(), eq(Operation.HRM_PATIENT), isNull())).thenThrow(new SQLException("synthetic failure"));
        assertThatThrownBy(data::populateCountsAndPatients).isInstanceOf(SQLException.class);
        assertThatThrownBy(data::getTotalLabs).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getPatients).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getCategoryHash).isInstanceOf(IllegalStateException.class);
    }
}
