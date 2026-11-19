/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.forms.FormsDao;
import io.github.carlos_emr.carlos.prevention.reports.PreventionReportUtil;
import io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Form;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * An "as of" demographic query asks who was rostered to the selected provider on a past date. Both
 * builders filtered SQL candidates on the patient's <em>current</em> {@code d.provider_no} before
 * the per-row history check ran, so a patient rostered to Dr A on that date who has since moved to
 * Dr B never reached the check and was dropped from Dr A's report (#5877). The candidate filter
 * must also admit patients whose archived periods name the provider, and the history check alone
 * decides who stays.
 */
@Tag("unit")
@Tag("report")
@DisplayName("Demographic report queries as of a roster date")
class RptDemographicQueryAsOfRosterUnitTest extends CarlosUnitTestBase {

    private static final String DR_A = "101";
    private static final String AS_OF = "2025-06-30";
    private static final String CURRENT_ONLY = " d.provider_no = :provider0 )";
    private static final String ARCHIVE_ADMISSION =
            "d.demographic_no in (select da.demographic_no from demographicArchive da where da.provider_no = :provider0)";

    private FormsDao formsDao;
    private MockedStatic<PreventionReportUtil> history;

    @BeforeEach
    void setUp() {
        formsDao = mock(FormsDao.class);
        registerMock(FormsDao.class, formsDao);
        history = mockStatic(PreventionReportUtil.class);
    }

    @AfterEach
    void closeHistory() {
        if (history != null) {
            history.close();
        }
    }

    /** Each builder's three-argument {@code buildQuery}, so both get the same contract. */
    static Stream<Arguments> builders() {
        BiFunction<RptDemographicReport2Form, String, List<?>> first =
                (frm, asOf) -> new RptDemographicQueryBuilder().buildQuery(mock(LoggedInInfo.class), frm, asOf);
        BiFunction<RptDemographicReport2Form, String, List<?>> second =
                (frm, asOf) -> new RptDemographicQuery2Builder().buildQuery(mock(LoggedInInfo.class), frm, asOf);
        return Stream.of(Arguments.of("RptDemographicQueryBuilder", first),
                Arguments.of("RptDemographicQuery2Builder", second));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builders")
    @DisplayName("should keep the current-provider filter alone when no as-of date is asked for")
    void shouldFilterOnCurrentProviderOnly_whenNoAsOfDate(
            String name, BiFunction<RptDemographicReport2Form, String, List<?>> build) {
        when(formsDao.runParameterizedNativeQuery(anyString(), anyMap())).thenReturn(new ArrayList<>());

        build.apply(form(), null);

        String sql = capturedSql();
        assertThat(sql).contains(CURRENT_ONLY).doesNotContain("demographicArchive");
        history.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builders")
    @DisplayName("should admit patients an archived period assigns to the provider when asked as of a date")
    void shouldAdmitArchivedProviderPatients_whenAsOfDateIsSet(
            String name, BiFunction<RptDemographicReport2Form, String, List<?>> build) {
        when(formsDao.runParameterizedNativeQuery(anyString(), anyMap())).thenReturn(new ArrayList<>());

        build.apply(form(), AS_OF);

        String sql = capturedSql();
        assertThat(sql).contains("( d.provider_no = :provider0 or " + ARCHIVE_ADMISSION + " )");
        assertThat(capturedParams()).containsEntry("provider0", DR_A);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builders")
    @DisplayName("should keep exactly the patients the roster history places with the provider on that date")
    void shouldKeepOnlyHistoricallyRosteredPatients_whenAsOfDateIsSet(
            String name, BiFunction<RptDemographicReport2Form, String, List<?>> build) {
        // 11: Dr A's on the as-of date, now with Dr B (admitted by the archive predicate).
        // 12: Dr A's now, but with someone else on the as-of date.
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {"11"});
        rows.add(new Object[] {"12"});
        when(formsDao.runParameterizedNativeQuery(anyString(), anyMap())).thenReturn(rows);
        history.when(() -> PreventionReportUtil.wasRosteredToThisProvider(
                        any(), eq(11), any(Date.class), eq(DR_A)))
                .thenReturn(true);
        history.when(() -> PreventionReportUtil.wasRosteredToThisProvider(
                        any(), eq(12), any(Date.class), eq(DR_A)))
                .thenReturn(false);

        List<?> result = build.apply(form(), AS_OF);

        assertThat(result).hasSize(1);
        assertThat(new ArrayList<Object>((List<?>) result.get(0))).containsExactly("11");
    }

    private RptDemographicReport2Form form() {
        RptDemographicReport2Form frm = new RptDemographicReport2Form();
        frm.setSelect(new String[] {"demographic_no"});
        frm.setProviderNo(new String[] {DR_A});
        return frm;
    }

    private String capturedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(formsDao).runParameterizedNativeQuery(sql.capture(), anyMap());
        return sql.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedParams() {
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(formsDao).runParameterizedNativeQuery(anyString(), params.capture());
        return params.getValue();
    }
}
