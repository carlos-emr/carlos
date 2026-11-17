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
package io.github.carlos_emr.carlos.report.reportByTemplate;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class DepressionContinuityReporterUnitTest extends CarlosUnitTestBase {
    private BillingONCHeader1Dao billingDao;
    private OscarAppointmentDao appointmentDao;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        billingDao = mock(BillingONCHeader1Dao.class);
        appointmentDao = mock(OscarAppointmentDao.class);
        registerMock(BillingONCHeader1Dao.class, billingDao);
        registerMock(OscarAppointmentDao.class, appointmentDao);
        registerMock(ReportTemplatesDao.class, mock(ReportTemplatesDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        request = new MockHttpServletRequest();
        request.setParameter("templateId", "1");
        request.setParameter("diag_date_from", "2026-01-01");
        request.setParameter("diag_date_to", "2026-01-31");
        request.setParameter("visit_date_from", "2026-03-01");
        request.setParameter("visit_date_to", "2026-03-31");
        request.setParameter("dxCodes:list", "311");
    }

    private Object[] diagnosis(int id) {
        var patient = new Demographic();
        patient.setDemographicNo(id);
        var item = new BillingONItem();
        item.setDx("311");
        item.setServiceDate(Date.valueOf("2026-01-03"));
        return new Object[] {patient, new BillingONCHeader1(), item};
    }

    private Object[] appointment(int id, Object date) {
        return new Object[] {date, "Seen Doctor", "Family Doctor", "A007A", "Medication", "Prescriber", id, null, null};
    }

    static Stream<Object> dates() {
        return Stream.of(LocalDate.of(2026, java.time.Month.MARCH, 4), Date.valueOf("2026-03-04"));
    }

    @ParameterizedTest
    @MethodSource("dates")
    void shouldRenderDiagnosisAndVisit_whenNativeOrJdbcDateReturned(Object date) {
        when(billingDao.findDemographicsAndBillingsByDxAndServiceDates(anyList(), any(), any()))
                .thenReturn(Collections.singletonList(diagnosis(42)));
        when(appointmentDao.findAppointmentsByDemographicIds(anySet(), any(), any()))
                .thenReturn(Collections.singletonList(appointment(42, date)));
        assertThat(new DepressionContinuityReporter().generateReport(request)).isTrue();
        assertThat((String) request.getAttribute("resultsethtml")).contains("2026-01-03", "2026-03-04", "A007A", "Medication");
        assertThat((String) request.getAttribute("csv")).contains("42,2026-01-03,311", "42,, ,2026-03-04");
        var table = org.jsoup.Jsoup.parse((String) request.getAttribute("resultsethtml"));
        assertThat(table.select("table.reportTable > thead > tr > th")).hasSize(9);
        assertThat(table.select("table.reportTable > tbody > tr")).hasSize(2)
                .allSatisfy(row -> assertThat(row.children()).hasSize(9));
        verify(appointmentDao).findAppointmentsByDemographicIds(Set.of("42"), Date.valueOf("2026-03-01"), Date.valueOf("2026-03-31"));
    }

    @Test
    void shouldKeepEveryPatientAndUseTheirIdInCsv_whenSomeHaveMultipleVisitsAndOthersHaveNone() {
        when(billingDao.findDemographicsAndBillingsByDxAndServiceDates(anyList(), any(), any()))
                .thenReturn(List.of(diagnosis(42), diagnosis(43), diagnosis(44)));
        when(appointmentDao.findAppointmentsByDemographicIds(anySet(), any(), any()))
                .thenReturn(List.of(appointment(42, Date.valueOf("2026-03-04")), appointment(42, Date.valueOf("2026-03-05")),
                        appointment(43, Date.valueOf("2026-03-06"))));
        assertThat(new DepressionContinuityReporter().generateReport(request)).isTrue();
        var csv = (String) request.getAttribute("csv");
        assertThat(csv).containsOnlyOnce("42,2026-01-03,311").containsOnlyOnce("43,2026-01-03,311")
                .containsOnlyOnce("44,2026-01-03,311").contains("42,, ,2026-03-04", "42,, ,2026-03-05", "43,, ,2026-03-06")
                .doesNotContain("44,, ,2026-03");
    }

    @Test
    void shouldRenderEmptyTableWithHeaders_whenNoDiagnosesMatch() {
        assertThat(new DepressionContinuityReporter().generateReport(request)).isTrue();
        var table = org.jsoup.Jsoup.parse((String) request.getAttribute("resultsethtml"));
        assertThat(table.select("table.reportTable > thead > tr > th")).hasSize(9);
        assertThat(table.select("table.reportTable > tbody > tr")).isEmpty();
        verifyNoInteractions(appointmentDao);
    }

    static Stream<Arguments> medicationNames() {
        return Stream.of(Arguments.of("Brand", "Generic", "Custom", "Brand"),
                Arguments.of(null, "Generic", "Custom", "Generic"),
                Arguments.of("null", "NULL", "Custom", "Custom"),
                Arguments.of("NULL", null, null, ""),
                Arguments.of("", "Generic", "Custom", ""),
                Arguments.of("null", "null", "null", "null"));
    }

    @ParameterizedTest
    @MethodSource("medicationNames")
    void shouldKeepDrugNameFallbacks_whenStoredNamesContainLegacyNullMarkers(String brand, String generic, String custom, String expected) {
        when(billingDao.findDemographicsAndBillingsByDxAndServiceDates(anyList(), any(), any()))
                .thenReturn(Collections.singletonList(diagnosis(42)));
        Object[] visit = appointment(42, Date.valueOf("2026-03-04"));
        visit[4] = brand;
        visit[7] = generic;
        visit[8] = custom;
        when(appointmentDao.findAppointmentsByDemographicIds(anySet(), any(), any()))
                .thenReturn(Collections.singletonList(visit));
        assertThat(new DepressionContinuityReporter().generateReport(request)).isTrue();
        assertThat((String) request.getAttribute("csv")).contains(",A007A," + expected + ",Prescriber\n");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {4, 7, 8})
    void shouldRenderStoredMedicationAndProviderNamesAsText_whenNamesContainMarkup(int nameColumn) {
        when(billingDao.findDemographicsAndBillingsByDxAndServiceDates(anyList(), any(), any()))
                .thenReturn(Collections.singletonList(diagnosis(42)));
        String name = "<img src=x onerror=alert(1)> & medication";
        String provider = "<b>Doctor & colleague</b>";
        Object[] visit = appointment(42, Date.valueOf("2026-03-04"));
        visit[4] = null;
        visit[nameColumn] = name;
        visit[1] = provider;
        visit[2] = provider;
        visit[5] = provider;
        when(appointmentDao.findAppointmentsByDemographicIds(anySet(), any(), any()))
                .thenReturn(Collections.singletonList(visit));

        assertThat(new DepressionContinuityReporter().generateReport(request)).isTrue();
        var table = org.jsoup.Jsoup.parse((String) request.getAttribute("resultsethtml"));
        var cells = table.select("tbody tr").last().children();
        assertThat(cells.get(7).text()).isEqualTo(name);
        assertThat(cells.get(4).text()).isEqualTo(provider);
        assertThat(cells.get(5).text()).isEqualTo(provider);
        assertThat(cells.get(8).text()).isEqualTo(provider);
        assertThat(table.select("img, b, script")).isEmpty();
        assertThat((String) request.getAttribute("csv")).contains(name, provider);
    }

    @Test
    void shouldReportFailure_whenDatabaseReadFails() {
        when(billingDao.findDemographicsAndBillingsByDxAndServiceDates(anyList(), any(), any()))
                .thenThrow(new IllegalStateException("unavailable"));
        assertThat(new DepressionContinuityReporter().generateReport(request)).isFalse();
        assertThat((String) request.getAttribute("errormsg")).isNotBlank();
        assertThat(request.getAttribute("resultsethtml")).isNull();
    }
}
