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
import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.report.ClinicalReports.SQLDenominator;
import io.github.carlos_emr.carlos.report.ClinicalReports.SQLNumerator;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportManager;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportObjectGeneric;
import io.github.carlos_emr.carlos.report.reportByTemplate.SQLReporter;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Legacy SQL reads of native partial dates use the same storage anchors as the merge base. */
@Tag("integration")
@Tag("prevention")
@Isolated("PreventionData retains a static DAO reference")
class PreventionLegacySqlIntegrationTest extends CarlosTestBase {
    private static final int PATIENT = 974231;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("preventionDao") private PreventionDao preventions;
    @Autowired private PartialDateDao dates;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    private Object originalPreventions;

    @BeforeEach
    void populateNativeYearOnlyHistory() {
        var record = new Prevention();
        record.setDemographicId(PATIENT);
        record.setPreventionType("Flu");
        record.setPreventionDate(Date.valueOf("2019-01-01"));
        record.setCreatorProviderNo("999998");
        preventions.persist(record);
        dates.persist(new PartialDate(4, record.getId(), 7, "YYYY"));
        entityManager.flush();
        originalPreventions = ReflectionTestUtils.getField(PreventionData.class, "preventionDao");
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", preventions);
    }

    @AfterEach
    void restoreStaticDao() {
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", originalPreventions);
    }

    @Test
    void shouldReturnAutofillValues_whenNativeHistoryHasYearOnlyMetadata() {
        var query = new ParameterizedSql(
                "select prevention_date as date_value from preventions where demographic_no=?", List.of(PATIENT));
        assertThat(EFormUtil.getValuesOrNull(List.of("date_value"), query, PATIENT)).singleElement()
                .asString().startsWith("2019-01-01");
    }

    @Test
    void shouldEvaluateClinicalNumeratorAndDenominator_withNativeYearOnlyHistory() {
        var numerator = new SQLNumerator();
        numerator.setSQL("select id from preventions where demographic_no=${demographic_no} and prevention_date >= '2018-01-01'");
        assertThat(numerator.evaluate(null, Integer.toString(PATIENT))).isTrue();
        var denominator = new SQLDenominator();
        denominator.setSQL("select demographic_no from preventions where demographic_no=974231");
        assertThat(denominator.getDenominatorList()).containsExactly(Integer.toString(PATIENT));
    }

    @Test
    void shouldRenderReportByTemplate_withNativeYearOnlyHistory() {
        var templates = mock(ReportTemplatesDao.class);
        var active = new ReportTemplates();
        active.setActive(1);
        when(templates.find(1)).thenReturn(active);
        var request = mock(HttpServletRequest.class);
        when(request.getParameter("templateId")).thenReturn("1");
        Map<String, String[]> parameters = Map.of();
        when(request.getParameterMap()).thenReturn(parameters);
        var report = mock(ReportObjectGeneric.class);
        when(report.buildParameterizedSql(parameters)).thenReturn(new ParameterizedSql(
                "select prevention_date as date_value from preventions where demographic_no=?", List.of(PATIENT)));
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS);
                var managers = mockConstruction(ReportManager.class, (manager, context) ->
                        when(manager.getReportTemplateNoParam("1")).thenReturn(report))) {
            spring.when(() -> SpringUtils.getBean(ReportTemplatesDao.class)).thenReturn(templates);
            assertThat(new SQLReporter().generateReport(request)).isTrue();
            verify(request).setAttribute(eq("csv"), contains("2019-01-01"));
        }
    }
}
