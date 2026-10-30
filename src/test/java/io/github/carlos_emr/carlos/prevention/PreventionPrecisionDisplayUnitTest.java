/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
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
    void shouldKeepNativeAnchorAndClinicalCalculations_forYearOnlyHistory() {
        var nativeRecord = record(41, "2019-01-01");
        nativeRecord.getPreventionExts().clear();
        when(preventions.findByTypeAndDemoNo("Td", 7)).thenReturn(List.of(nativeRecord));
        when(dates.getFormat(4, 41, 7)).thenReturn("YYYY");
        var rows = PreventionData.listForCheckedPatient("Td", 7, Date.valueOf("2000-01-01"));
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("prevention_date", "2019")
                    .containsEntry("prevention_date_asDate", Date.valueOf("2019-01-01"));
            assertThat(row.get("age")).isNotEqualTo("Unknown");
        });
        assertThatCode(() -> PreventionData.requireExactDateMaps(rows)).doesNotThrowAnyException();
    }

    @Test
    void shouldExecuteInteractiveAutofill_withoutAWholeDatabaseDateGate() throws Exception {
        var query = new io.github.carlos_emr.carlos.report.data.ParameterizedSql(
                "select prevention_date as last_date from preventions", List.of());
        var rows = mock(java.sql.ResultSet.class);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("last_date")).thenReturn("2019-01-01");
        try (var jdbc = mockStatic(io.github.carlos_emr.carlos.db.LegacyJdbcQuery.class)) {
            jdbc.when(() -> io.github.carlos_emr.carlos.db.LegacyJdbcQuery.getPreparedResultSet(query)).thenReturn(rows);
            assertThat(io.github.carlos_emr.carlos.eform.EFormUtil.getValues(List.of("last_date"), query))
                    .containsExactly("2019-01-01");
            verifyNoInteractions(preventions);
        }
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
        var provenance = new io.github.carlos_emr.carlos.commn.model.PreventionExt();
        provenance.setKeyval("cdsDatePrecision");
        record.addPreventionExt(provenance);
        return record;
    }
}
