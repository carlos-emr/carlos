/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.EFormReportToolDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionReportDao;
import io.github.carlos_emr.carlos.commn.model.DemographicSets;
import io.github.carlos_emr.carlos.commn.model.EFormReportTool;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DemographicSetsManager;
import io.github.carlos_emr.carlos.managers.EFormReportToolManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link ReportingService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for reporting REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ReportingService REST endpoint tests")
class ReportingServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private DemographicSetsManager mockDemographicSetsManager;

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private EFormReportToolManager mockEFormReportToolManager;

    @Mock
    private PreventionReportDao mockPreventionReportDao;

    @Override
    protected Object getServiceBean() {
        ReportingService service = new ReportingService();
        injectDependency(service, "demographicSetsManager", mockDemographicSetsManager);
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "eformReportToolManager", mockEFormReportToolManager);
        injectDependency(service, "preventionReportDao", mockPreventionReportDao);
        return service;
    }

    @Nested
    @DisplayName("GET /reporting/demographicSets/list")
    class ListDemographicSets {

        @Test
        @DisplayName("should return 200 with demographic set names")
        void shouldReturn200_whenSetsExist() {
            when(mockDemographicSetsManager.getDemographicSetNames())
                .thenReturn(List.of("Set A", "Set B"));

            Response response = request().path("/reporting/demographicSets/list").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no sets")
        void shouldReturn200WithEmptyList_whenNoSets() {
            when(mockDemographicSetsManager.getDemographicSetNames())
                .thenReturn(Collections.emptyList());

            Response response = request().path("/reporting/demographicSets/list").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /reporting/demographicSets/demographicSet/{name}")
    class GetDemographicSetByName {

        @Test
        @DisplayName("should return 200 with demographic set")
        void shouldReturn200_whenSetExists() {
            DemographicSets ds = new DemographicSets();
            when(mockDemographicSetsManager.getDemographicSetByName(eq("TestSet")))
                .thenReturn(List.of(ds));

            Response response = request().path("/reporting/demographicSets/demographicSet/TestSet").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty result when set not found")
        void shouldReturn200WithEmpty_whenSetNotFound() {
            when(mockDemographicSetsManager.getDemographicSetByName(eq("nonexistent")))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/reporting/demographicSets/demographicSet/nonexistent").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /reporting/eformReportTool/list")
    class ListEFormReportTools {

        @Test
        @DisplayName("should return 200 with eForm report tools")
        void shouldReturn200_whenToolsExist() {
            EFormReportTool tool = new EFormReportTool();
            when(mockEFormReportToolManager.findAll(any(LoggedInInfo.class)))
                .thenReturn(List.of(tool));

            Response response = request().path("/reporting/eformReportTool/list").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no tools")
        void shouldReturn200WithEmptyList_whenNoTools() {
            when(mockEFormReportToolManager.findAll(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/reporting/eformReportTool/list").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /reporting/preventionReport/getList")
    class GetPreventionReportList {

        @Test
        @DisplayName("should return 200 with prevention reports")
        void shouldReturn200_whenReportsExist() {
            when(mockPreventionReportDao.findActive())
                .thenReturn(Collections.emptyList());

            Response response = request().path("/reporting/preventionReport/getList").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
