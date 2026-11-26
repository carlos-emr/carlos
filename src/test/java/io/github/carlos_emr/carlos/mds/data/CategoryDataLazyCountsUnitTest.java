/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Counts;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.DateSearchType;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Operation;
import io.github.carlos_emr.carlos.commn.service.InboxDateSearchTypeResolver;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lazy legacy aggregates and the per-population date-mode snapshot of {@link CategoryData}.
 * @since 2026-11-06
 */
@Tag("unit")
class CategoryDataLazyCountsUnitTest extends CarlosUnitTestBase {
    private void registerDateSearchService() {
        registerMock(io.github.carlos_emr.carlos.inboxhub.InboxDateSearchService.class,
                new io.github.carlos_emr.carlos.inboxhub.InboxDateSearchService(
                    mock(io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao.class)));
    }

    private InboxCategoryDao dao;
    private InboxDateSearchTypeResolver dateSearchTypes;
    private CategoryData data;

    @BeforeEach
    void setup() throws SQLException {
        registerDateSearchService();
        dao = mock(InboxCategoryDao.class);
        registerMock(InboxCategoryDao.class, dao);
        dateSearchTypes = mock(InboxDateSearchTypeResolver.class);
        when(dateSearchTypes.resolve()).thenReturn(DateSearchType.SERVICE_OBSERVATION);
        registerMock(InboxDateSearchTypeResolver.class, dateSearchTypes);
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
        verifyNoInteractions(dao, dateSearchTypes);
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
        assertThatThrownBy(data::getTotalDocs).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getTotalLabs).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getUnmatchedLabs).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getUnmatchedDocs).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getUnmatchedHRMCount).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getMatchedHRMCount).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getTotalNumDocs).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getAbnormalCount).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getNormalCount).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getPatients).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getPatientList).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getCategoryHash).isInstanceOf(IllegalStateException.class);

        when(dao.count(any(), eq(Operation.HRM_PATIENT), isNull())).thenReturn(new Counts(4, List.of()));
        data.populateCountsAndPatients();
        assertThat(data.getTotalLabs()).isEqualTo(3);
        assertThat(data.getTotalDocs()).isEqualTo(4);
        assertThat(data.getUnmatchedLabs()).isZero();
        assertThat(data.getUnmatchedDocs()).isZero();
        assertThat(data.getMatchedHRMCount()).isEqualTo(4);
        assertThat(data.getUnmatchedHRMCount()).isZero();
        assertThat(data.getTotalNumDocs()).isEqualTo(7);
        assertThat(data.getAbnormalCount()).isEqualTo(2);
        assertThat(data.getNormalCount()).isEqualTo(1);
        assertThat(data.getPatientList()).isEmpty();
        assertThat(data.getPatients()).isEmpty();
        assertThat(data.getCategoryHash()).isNotNull();
    }

    @Test
    void shouldReadDatePreferenceOnce_forAPopulationAndItsLazyAbnormalCount() throws SQLException {
        // The preference changes after the first read; the whole population, including the lazy
        // abnormal count read after it, must keep the snapshot, or normal = total - abnormal
        // would subtract counts taken by different date columns.
        when(dateSearchTypes.resolve()).thenReturn(DateSearchType.RECEIVED_CREATED, DateSearchType.SERVICE_OBSERVATION);
        var notPatientSearch = new CategoryData("", "", "", false, true, "999998", "N", "all",
                "2026-01-01", "2026-01-31");

        notPatientSearch.populateCountsAndPatients();
        assertThat(notPatientSearch.getNormalCount()).isEqualTo(1);
        assertThat(notPatientSearch.getDateSearchType()).isEqualTo("receivedCreated");

        verify(dateSearchTypes, times(1)).resolve();
        // Six category counts plus the lazy abnormal count, all with the snapshot's mode.
        verify(dao, times(7)).count(argThat(filter -> filter.dateSearchType() == DateSearchType.RECEIVED_CREATED),
                any(), isNull());
        verify(dao, never()).count(argThat(filter -> filter.dateSearchType() != DateSearchType.RECEIVED_CREATED),
                any(), any());
    }

    @Test
    void shouldTakeNewSnapshot_whenRepopulated() throws SQLException {
        when(dateSearchTypes.resolve()).thenReturn(DateSearchType.RECEIVED_CREATED, DateSearchType.SERVICE_OBSERVATION);

        data.populateCountsAndPatients();
        assertThat(data.getDateSearchType()).isEqualTo("receivedCreated");
        data.populateCountsAndPatients();

        assertThat(data.getDateSearchType()).isEqualTo("serviceObservation");
        verify(dateSearchTypes, times(2)).resolve();
        assertThat(data.getAbnormalCount()).isEqualTo(2);
        verify(dao).count(argThat(filter -> filter.dateSearchType() == DateSearchType.SERVICE_OBSERVATION),
                eq(Operation.ABNORMAL), isNull());
    }

    @Test
    void shouldShareOneSnapshot_acrossSingleCategoryCallsOutsideAPopulation() throws SQLException {
        when(dao.count(any(), eq(Operation.LAB_DEMOGRAPHIC), eq("7"))).thenReturn(new Counts(1, List.of()));
        data.getLabCountForUnmatched();
        data.getDocumentCountForUnmatched();
        data.getLabCountForDemographic("7");

        verify(dateSearchTypes, times(1)).resolve();
        verify(dao).count(any(), eq(Operation.LAB_DEMOGRAPHIC), eq("7"));
    }

    @Test
    void shouldCarryRequestValues_intoTheSnapshotFilter() throws SQLException {
        data.populateCountsAndPatients();

        verify(dao).count(eq(new InboxCategoryDao.Filter("Synthetic", "", "", true, true, "999998", "N", "all",
                "2026-01-01", "2026-01-31", DateSearchType.SERVICE_OBSERVATION)), eq(Operation.LAB_PATIENT), isNull());
    }

    @Test
    void shouldRefuseInvalidDate_beforeReadingPreferenceOrCounting() {
        var invalid = new CategoryData("Synthetic", "", "", true, true, "999998", "N", "all",
                "2026-01-01", "2026-02-30");

        assertThatThrownBy(invalid::populateCountsAndPatients)
                .isInstanceOf(java.time.format.DateTimeParseException.class);
        verifyNoInteractions(dao, dateSearchTypes);
    }

    @Test
    void shouldReportPreferenceReadFailure_asCheckedSqlFailureWithoutCounting() {
        when(dateSearchTypes.resolve()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"));

        assertThatThrownBy(data::populateCountsAndPatients)
                .isInstanceOf(SQLException.class)
                .hasCauseInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        verifyNoInteractions(dao);
        assertThatThrownBy(data::getTotalLabs).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(data::getAbnormalCount).isInstanceOf(IllegalStateException.class);
    }
}
