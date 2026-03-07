/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 test to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Integration tests for {@link ExcludeDemographicHandler}.
 *
 * <p>Tests the exclude/un-exclude demographic functionality for dashboard
 * indicator templates, including list-based and JSON-based exclusion APIs.
 *
 * <p>Migrated from legacy JUnit 4 ExcludeDemographicHandlerTest.
 *
 * @since 2026-03-07
 */
@Tag("integration")
@Tag("dashboard")
@DisplayName("ExcludeDemographicHandler integration tests")
class ExcludeDemographicHandlerIntegrationTest extends CarlosTestBase {

    private static DemographicDao demographicDao;
    private static ExcludeDemographicHandler excludeDemographicHandler;
    private static final String PROVIDER_NO = "100";
    private static List<Integer> demoNos = new ArrayList<>();

    @BeforeAll
    static void setUpBeforeAll() throws Exception {
        demographicDao = SpringUtils.getBean(DemographicDao.class);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER_NO);

        for (int i = 0; i < 10; i++) {
            Demographic demographic = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(demographic);
            demographic.setDemographicNo(null);
            demographic.setProvider(provider);
            demographicDao.save(demographic);
            demoNos.add(demographic.getDemographicNo());
        }

        loggedInInfo.setLoggedInProvider(provider);
        excludeDemographicHandler = new ExcludeDemographicHandler();
        excludeDemographicHandler.setLoggedinInfo(loggedInInfo);
    }

    @Test
    @DisplayName("should return empty list when no demographics are excluded")
    void shouldReturnEmptyList_whenNoDemographicsAreExcluded() {
        String indicatorName = "indicatorName_getDemoIds";
        assertThat(excludeDemographicHandler.getDemoIds(indicatorName)).isEmpty();
    }

    @Test
    @DisplayName("should return empty list of demographic extensions when none excluded")
    void shouldReturnEmptyDemoExts_whenNoneExcluded() {
        String indicatorName = "indicatorName_getDemoExts";
        assertThat(excludeDemographicHandler.getDemoExts(indicatorName)).isEmpty();
    }

    @Test
    @DisplayName("should exclude single demographic and return it in excluded list")
    void shouldExcludeSingleDemographic_andReturnInExcludedList() {
        String indicatorName = "myIndicatorName_setDemoId";
        assertThat(demoNos).hasSize(10);

        excludeDemographicHandler.excludeDemoId(demoNos.get(5), indicatorName);
        assertThat(excludeDemographicHandler.getDemoIds(indicatorName)).hasSize(1);
        assertThat(excludeDemographicHandler.getDemoIds(indicatorName).get(0))
                .isEqualTo(demoNos.get(5));

        // Clean up for other tests due to uk_demo_ext constraint
        excludeDemographicHandler.unExcludeDemoIds(demoNos, indicatorName);
    }

    @Test
    @DisplayName("should exclude list of demographics and return all in excluded list")
    void shouldExcludeDemographicList_andReturnAllInExcludedList() {
        String indicatorName = "myIndicatorName_setDemoIDList";
        excludeDemographicHandler.excludeDemoIds(demoNos, indicatorName);

        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        assertThat(demoIds).hasSize(demoNos.size());
        for (Integer el : demoNos) {
            assertThat(demoIds).contains(el);
        }

        // Clean up for other tests due to uk_demo_ext constraint
        excludeDemographicHandler.unExcludeDemoIds(demoNos, indicatorName);
    }

    @Test
    @DisplayName("should un-exclude demographics and return empty list")
    void shouldUnExcludeDemographics_andReturnEmptyList() {
        String indicatorName = "myIndicatorName_unsetDemoIDList";
        excludeDemographicHandler.excludeDemoIds(demoNos, indicatorName);

        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        assertThat(demoIds).hasSize(demoNos.size());

        excludeDemographicHandler.unExcludeDemoIds(demoNos, indicatorName);
        assertThat(excludeDemographicHandler.getDemoIds(indicatorName)).isEmpty();
    }

    @Test
    @DisplayName("should exclude demographics from JSON string and return all in excluded list")
    void shouldExcludeDemographics_fromJsonString() {
        String indicatorName = "myIndicatorName_setDemoIdJson";
        String jsonStr = getJsonDemoNoStr(demoNos);

        excludeDemographicHandler.excludeDemoIds(jsonStr, indicatorName);
        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        for (int demoNo : demoNos) {
            assertThat(demoIds).contains(demoNo);
        }

        // Clean up for other tests due to uk_demo_ext constraint
        excludeDemographicHandler.unExcludeDemoIds(jsonStr, indicatorName);
    }

    @Test
    @DisplayName("should un-exclude demographics from JSON string and return empty list")
    void shouldUnExcludeDemographics_fromJsonString() {
        String indicatorName = "myIndicatorName_unsetDemoIdJson";
        String jsonStr = getJsonDemoNoStr(demoNos);

        excludeDemographicHandler.excludeDemoIds(jsonStr, indicatorName);
        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        for (int demoNo : demoNos) {
            assertThat(demoIds).contains(demoNo);
        }

        excludeDemographicHandler.unExcludeDemoIds(jsonStr, indicatorName);
        assertThat(excludeDemographicHandler.getDemoIds(indicatorName)).isEmpty();
    }

    /**
     * Builds a comma-separated string of demographic numbers for JSON-based exclusion.
     *
     * @param demoNums List of Integer demographic numbers
     * @return String comma-separated demographic numbers
     */
    private static String getJsonDemoNoStr(List<Integer> demoNums) {
        StringBuilder builder = new StringBuilder();
        for (Integer i : demoNums) {
            builder.append(i).append(",");
        }
        builder.deleteCharAt(builder.length() - 1); // remove trailing comma
        return builder.toString();
    }
}
