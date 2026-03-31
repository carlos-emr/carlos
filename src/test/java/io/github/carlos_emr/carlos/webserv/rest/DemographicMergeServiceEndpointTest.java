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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link DemographicMergeService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("DemographicMergeService REST endpoint tests")
class DemographicMergeServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private DemographicManager mockDemographicManager;

    @Override
    protected Object getServiceBean() {
        DemographicMergeService service = new DemographicMergeService();
        injectDependency(service, "demographicManager", mockDemographicManager);
        return service;
    }

    @Nested
    @DisplayName("GET /demographics/merge/{parentId}")
    class GetMergedDemographics {

        @Test
        @DisplayName("should return 200 with merged demographics")
        void shouldReturn200_whenMergedDemographicsExist() {
            DemographicMerged merged = new DemographicMerged();
            when(mockDemographicManager.getMergedDemographics(any(LoggedInInfo.class), eq(100)))
                .thenReturn(List.of(merged));

            Response response = request().path("/demographics/merge/100").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no merged demographics")
        void shouldReturn200WithEmptyList_whenNoMergedDemographics() {
            when(mockDemographicManager.getMergedDemographics(any(LoggedInInfo.class), eq(200)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/demographics/merge/200").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("PUT /demographics/merge/")
    class MergeDemographic {

        @Test
        @DisplayName("should return 204 when merge succeeds")
        void shouldReturn204_whenMergeSucceeds() {
            Response response = request().path("/demographics/merge/")
                .query("parentId", 100)
                .query("childId", 200)
                .put(null);

            assertThat(response.getStatus()).isIn(200, 204);
            verify(mockDemographicManager).mergeDemographics(any(LoggedInInfo.class), eq(100), any());
        }
    }

    @Nested
    @DisplayName("DELETE /demographics/merge/")
    class UnmergeDemographic {

        @Test
        @DisplayName("should return 204 when unmerge succeeds")
        void shouldReturn204_whenUnmergeSucceeds() {
            Response response = request().path("/demographics/merge/")
                .query("parentId", 100)
                .query("childsId", 200)
                .delete();

            assertThat(response.getStatus()).isIn(200, 204);
            verify(mockDemographicManager).unmergeDemographics(any(LoggedInInfo.class), eq(100), any());
        }
    }
}
