/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prevention;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Isolated("PreventionData retains static DAO references")
class PreventionPrecisionDisplayUnitTest extends CarlosUnitTestBase {
    private final PreventionDao preventions = mock(PreventionDao.class);
    private final PartialDateDao dates = mock(PartialDateDao.class);
    private Object originalPreventions;
    private Object originalDates;

    @BeforeEach
    void setUp() {
        registerMock(PreventionDao.class, preventions);
        registerMock(PreventionExtDao.class, mock(PreventionExtDao.class));
        registerMock(PartialDateDao.class, dates);
        originalPreventions = ReflectionTestUtils.getField(PreventionData.class, "preventionDao");
        originalDates = ReflectionTestUtils.getField(PreventionData.class, "partialDateDao");
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", preventions);
        ReflectionTestUtils.setField(PreventionData.class, "partialDateDao", dates);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", originalPreventions);
        ReflectionTestUtils.setField(PreventionData.class, "partialDateDao", originalDates);
    }

    @Test
    void shouldRetainListAndPrintRecords_whenTimingIsUncertain() {
        when(preventions.findByTypeAndDemoNo("Td", 7)).thenReturn(List.of(
                record(41, "2019-01-01"), record(42, null), record(43, "2019-01-01")));
        when(dates.getFormat(4, 41, 7)).thenReturn("YYYY");
        var rows = PreventionData.listForCheckedPatient("Td", 7, Date.valueOf("2000-01-01"));
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("prevention_date", "2019")
                .containsEntry("prevention_date_no_time", "2019")
                .containsEntry("prevention_date_asDate", null).containsEntry("age", "Unknown");
        assertThat(((PreventionDateDTO) rows.get(0).get("prevention_date_info")).getIntervalEnd())
                .isEqualTo("2019-12-31");
        assertThat(rows.get(1)).containsEntry("prevention_date_no_time", "Unknown")
                .containsEntry("prevention_date_asDate", null).containsEntry("age", "Unknown");
        assertThat(rows.get(2)).containsEntry("prevention_date_no_time", "2019-01-01")
                .containsEntry("prevention_date_asDate", Date.valueOf("2019-01-01"));
        assertThat(rows.get(2).get("age")).isNotEqualTo("Unknown");
    }

    @Test
    void shouldRefuseAutofillBeforeExecutingSql_whenPatientTimingIsUncertain() {
        when(preventions.hasUncertainPreventionDates(7)).thenReturn(true);
        var query = new io.github.carlos_emr.carlos.report.data.ParameterizedSql(
                "select max(prevention_date) as last_date from preventions where demographic_no=?", List.of(7));
        try (var jdbc = mockStatic(io.github.carlos_emr.carlos.db.LegacyJdbcQuery.class)) {
            assertThatThrownBy(() -> io.github.carlos_emr.carlos.eform.EFormUtil
                    .getValuesOrNull(List.of("last_date"), query, 7))
                    .isInstanceOf(io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException.class);
            jdbc.verifyNoInteractions();
        }
    }

    @Test
    void shouldRefuseClinicalNumerator_whenUncertainHistoryWouldOtherwiseEvaluateFalse() {
        when(preventions.hasUncertainPreventionDates(7)).thenReturn(true);
        var numerator = new io.github.carlos_emr.carlos.report.ClinicalReports.SQLNumerator();
        numerator.setSQL("select * from preventions where demographic_no='${demographic_no}' and prevention_date > '2020-01-01'");
        assertThatThrownBy(() -> numerator.evaluate(null, "7"))
                .isInstanceOf(io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException.class);
    }

    private static io.github.carlos_emr.carlos.commn.model.Prevention record(int id, String date) {
        var record = new io.github.carlos_emr.carlos.commn.model.Prevention();
        ReflectionTestUtils.setField(record, "id", id);
        record.setDemographicId(7);
        record.setPreventionType("Td");
        record.setPreventionDate(date == null ? null : Date.valueOf(date));
        return record;
    }
}
