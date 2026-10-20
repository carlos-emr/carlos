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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.model.Mortalities;
import io.github.carlos_emr.carlos.commn.model.ReportStatistic;
import io.github.carlos_emr.carlos.commn.model.ShelterPopulation;
import io.github.carlos_emr.carlos.commn.model.ShelterUsage;
import io.github.carlos_emr.carlos.commn.service.PopulationReportManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Population Report's one-hour cache is stamped only after every figure has been computed:
 * a failed compute must not serve an hour of partial figures (issue #4855).
 */
@Tag("unit")
@Tag("report")
@DisplayName("Population Report cache")
class PopulationReport2ActionUnitTest extends CarlosUnitTestBase {

    private PopulationReportManager manager;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        resetCache();
        createAndRegisterMock(SecurityInfoManager.class);
        manager = createAndRegisterMock(PopulationReportManager.class);
        ShelterPopulation population = mock(ShelterPopulation.class);
        ShelterUsage usage = mock(ShelterUsage.class);
        Mortalities deaths = mock(Mortalities.class);
        when(manager.getShelterPopulation()).thenReturn(population);
        when(manager.getShelterUsage()).thenReturn(usage);
        when(manager.getMortalities()).thenReturn(deaths);
        when(manager.getMajorMedicalConditions()).thenReturn(Map.of("Diabetes", new ReportStatistic(1, 2)));
        when(manager.getMajorMentalIllnesses()).thenReturn(Map.of());
        when(manager.getSeriousMedicalConditions()).thenReturn(Map.of());
        when(manager.getCategoryCodeDescriptions()).thenReturn(Map.of());
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() throws Exception {
        servletActionContext.close();
        resetCache();
    }

    private PopulationReport2Action newAction() {
        request = new MockHttpServletRequest("GET", "/PopulationReport");
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        return new PopulationReport2Action();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ReportStatistic> publishedConditions() {
        return (Map<String, ReportStatistic>) request.getAttribute("majorMedicalConditions");
    }

    /** The report caches its figures in a static reference; each test starts with none. */
    private static void resetCache() throws Exception {
        Field cached = PopulationReport2Action.class.getDeclaredField("CACHED_FIGURES");
        cached.setAccessible(true);
        ((AtomicReference<?>) cached.get(null)).set(null);
    }

    @Test
    void shouldComputeAgain_whenThePreviousComputeFailed() {
        when(manager.getMajorMedicalConditions())
                .thenThrow(new ArithmeticException("The denominator must not be zero"))
                .thenReturn(Map.of("Diabetes", new ReportStatistic(1, 2)));

        PopulationReport2Action failing = newAction();
        assertThatThrownBy(failing::report).isInstanceOf(ArithmeticException.class);
        String result = newAction().report();

        assertThat(result).isEqualTo("report");
        verify(manager, times(2)).getShelterPopulation();
        assertThat(publishedConditions()).containsOnlyKeys("Diabetes");
    }

    @Test
    void shouldServeTheCachedFigures_whenTheComputeSucceeded() {
        newAction().report();
        newAction().report();

        verify(manager, times(1)).getShelterPopulation();
        verify(manager, times(1)).getMajorMedicalConditions();
        assertThat(publishedConditions()).containsOnlyKeys("Diabetes");
        assertThat(request.getAttribute("date")).isNotNull();
    }

    @Test
    void shouldPublishNoFigures_whenTheFirstComputeFails() {
        when(manager.getMajorMentalIllnesses()).thenThrow(new IllegalStateException("database unavailable"));

        PopulationReport2Action first = newAction();
        assertThatThrownBy(first::report).isInstanceOf(IllegalStateException.class);

        // The conditions computed before the failure are not left behind for the next request.
        PopulationReport2Action second = newAction();
        assertThatThrownBy(second::report).isInstanceOf(IllegalStateException.class);
        verify(manager, times(2)).getMajorMedicalConditions();
    }
}
