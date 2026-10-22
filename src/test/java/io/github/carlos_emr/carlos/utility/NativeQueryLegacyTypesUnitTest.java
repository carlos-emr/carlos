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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.PMmodule.dao.WaitlistDaoImpl;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriaBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriasBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.VacancyDisplayBO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.PrivateBillTransactionsDAO;
import io.github.carlos_emr.carlos.commn.dao.EFormReportToolDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EFormReportTool;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests real DAO row mapping with Hibernate 7 and legacy JDBC scalar types. */
@Tag("unit")
class NativeQueryLegacyTypesUnitTest extends CarlosUnitTestBase {
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, java.time.Month.MARCH, 4, 12, 34, 56);

    private record VacancyCase(String name, Function<WaitlistDaoImpl, List<VacancyDisplayBO>> query,
            Function<Object, Object[]> row) { }

    static Stream<Arguments> vacancies() {
        var cases = List.of(
                new VacancyCase("program", dao -> dao.listDisplayVacanciesForWaitListProgram(42),
                        date -> new Object[] {7, "Template", date}),
                new VacancyCase("all programs", WaitlistDaoImpl::listDisplayVacanciesForAllWaitListPrograms,
                        date -> new Object[] {7, "Template", date, "Program", "Vacancy", 42}),
                new VacancyCase("agency", dao -> dao.getDisplayVacanciesForAgencyProgram(42),
                        date -> new Object[] {7, "Template", date, "Program", "Vacancy", 42}),
                new VacancyCase("single", dao -> List.of(dao.getDisplayVacancy(7)),
                        date -> new Object[] {"Vacancy", "Template", date, "Program", "Vacancy"}),
                new VacancyCase("counts", WaitlistDaoImpl::listNoOfVacanciesForWaitListProgram,
                        date -> new Object[] {42, 1L, "Vacancy", date, 7}),
                new VacancyCase("list", WaitlistDaoImpl::listVacanciesForWaitListProgram,
                        date -> new Object[] {42, "Vacancy", date, 7, "Template"}));
        return cases.stream().flatMap(scenario -> Stream.of(CREATED, Timestamp.valueOf(CREATED))
                .map(date -> Arguments.of(scenario.name(), scenario, date)));
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("vacancies")
    void shouldPreserveVacancyCreationTime_whenNativeOrJdbcDateReturned(String name, VacancyCase scenario, Object date) {
        var dao = new WaitlistDaoImpl();
        var query = stubRows(dao, scenario.row().apply(date));
        if ("single".equals(name)) {
            when(query.getResultList()).thenReturn(Collections.singletonList(scenario.row().apply(date)),
                    Collections.singletonList(new Object[] {0L, 0L, 0L}));
        }
        var rows = scenario.query().apply(dao);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCreated()).isEqualTo(Timestamp.valueOf(CREATED));
    }

    static Stream<Object> formDates() {
        return Stream.of(CREATED.toLocalDate(), java.sql.Date.valueOf(CREATED.toLocalDate()));
    }

    @ParameterizedTest
    @MethodSource("formDates")
    void shouldPreserveMatchingFormDate_whenNativeOrJdbcDateReturned(Object date) {
        var dao = new WaitlistDaoImpl();
        stubRows(dao, new Object[] {17, 42, date});
        var criteria = new CriteriasBO();
        criteria.crits = new CriteriaBO[0];
        var forms = dao.searchForMatchingEforms(criteria);
        assertThat(forms).hasSize(1);
        var form = forms.iterator().next();
        assertThat(form.getId()).isEqualTo(17);
        assertThat(form.getDemographicId()).isEqualTo(42);
        assertThat(form.getFormDate()).isEqualTo(java.sql.Date.valueOf(CREATED.toLocalDate()));
    }

    static Stream<Object> creationDates() {
        return Stream.of(CREATED, Timestamp.valueOf(CREATED));
    }

    @ParameterizedTest
    @MethodSource("creationDates")
    void shouldPreservePrivatePaymentDate_whenNativeOrJdbcDateReturned(Object date) {
        var dao = new PrivateBillTransactionsDAO();
        stubRows(dao, new Object[] {7, 42, 12.50d, date, 1, "Cash"});
        var payments = dao.getPrivateBillTransactions("42");
        assertThat(payments).hasSize(1);
        assertThat(payments.getFirst().getCreation_date()).isEqualTo(Timestamp.valueOf(CREATED));
        assertThat(payments.getFirst().getAmount_received()).isEqualTo(12.50d);
    }

    static Stream<Number> counts() {
        return Stream.of(3L, 3, BigInteger.valueOf(3), new BigDecimal("3"));
    }

    @ParameterizedTest
    @MethodSource("counts")
    void shouldReadRecordCount_whenDriverReturnsAnyNumericCountType(Number count) {
        var dao = new EFormReportToolDaoImpl();
        var manager = mock(EntityManager.class);
        var query = mock(Query.class);
        ReflectionTestUtils.setField(dao, "entityManager", manager);
        when(manager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(count));
        var tool = new EFormReportTool();
        tool.setTableName("ERT_owned_native_test");
        assertThat(dao.getNumRecords(tool)).isEqualTo(3);
    }

    private static Query stubRows(Object dao, Object[] row) {
        var manager = mock(EntityManager.class);
        var query = mock(Query.class);
        ReflectionTestUtils.setField(dao, "entityManager", manager);
        when(manager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        return query;
    }
}
