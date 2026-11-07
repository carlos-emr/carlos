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
import java.lang.reflect.RecordComponent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

    /** Publishes figures computed at {@code computedAt} as the fresh cached report. */
    @SuppressWarnings("unchecked")
    private static void seedCache(Date computedAt) throws Exception {
        Class<?> figures = Class.forName(PopulationReport2Action.class.getName() + "$Figures");
        Class<?>[] components = Arrays.stream(figures.getRecordComponents())
                .map(RecordComponent::getType).toArray(Class<?>[]::new);
        var constructor = figures.getDeclaredConstructor(components);
        constructor.setAccessible(true);
        Object seeded = constructor.newInstance(computedAt, mock(ShelterPopulation.class), mock(ShelterUsage.class),
                mock(Mortalities.class), Map.of(), Map.of(), Map.of(), Map.of(), System.currentTimeMillis());
        Field cached = PopulationReport2Action.class.getDeclaredField("CACHED_FIGURES");
        cached.setAccessible(true);
        ((AtomicReference<Object>) cached.get(null)).set(seeded);
    }

    @Test
    void shouldPublishTheClinicDateAndTime_whenTheUtcDateHasAlreadyChanged() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            // 23:30 in Toronto on 28 February is already 1 March in UTC.
            Date computedAt = Date.from(Instant.parse("2026-03-01T04:30:00Z"));
            seedCache(computedAt);

            PopulationReport2Action action = newAction();
            assertThat(action.report()).isEqualTo("report");

            // The page has always shown dd/MM/yyyy and the short Canadian time; these call-local
            // legacy formatters are the reference for both.
            assertThat(request.getAttribute("date")).isEqualTo("28/02/2026")
                    .isEqualTo(new SimpleDateFormat("dd/MM/yyyy").format(computedAt));
            assertThat(request.getAttribute("time"))
                    .isEqualTo(DateFormat.getTimeInstance(DateFormat.SHORT, Locale.CANADA).format(computedAt));
            verify(manager, never()).getShelterPopulation();
        } finally {
            TimeZone.setDefault(original);
        }
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
    void shouldComputeOnlyOnce_whenConcurrentRequestsMissTheCache() throws Exception {
        var firstQueryStarted = new CountDownLatch(1);
        var allowFirstQuery = new CountDownLatch(1);
        var secondRequestStarted = new CountDownLatch(1);
        var duplicateCompute = new CountDownLatch(1);
        var firstQuery = new AtomicBoolean(true);
        ShelterPopulation population = mock(ShelterPopulation.class);
        when(manager.getShelterPopulation()).thenAnswer(call -> {
            if (firstQuery.getAndSet(false)) {
                firstQueryStarted.countDown();
                assertThat(allowFirstQuery.await(5, TimeUnit.SECONDS)).isTrue();
            } else {
                duplicateCompute.countDown();
            }
            return population;
        });
        PopulationReport2Action first = newAction();
        PopulationReport2Action second = newAction();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var firstResult = workers.submit(first::report);
            assertThat(firstQueryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var secondResult = workers.submit(() -> {
                secondRequestStarted.countDown();
                return second.report();
            });
            assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
            // Hold the first computation open: the second request must wait for its complete
            // snapshot rather than starting the same database work or receiving partial data.
            assertThat(duplicateCompute.await(200, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(secondResult.isDone()).isFalse();
            allowFirstQuery.countDown();
            assertThat(firstResult.get(5, TimeUnit.SECONDS)).isEqualTo("report");
            assertThat(secondResult.get(5, TimeUnit.SECONDS)).isEqualTo("report");
            verify(manager, times(1)).getShelterPopulation();
            verify(manager, times(1)).getCategoryCodeDescriptions();
        } finally {
            allowFirstQuery.countDown();
            workers.shutdownNow();
        }
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
