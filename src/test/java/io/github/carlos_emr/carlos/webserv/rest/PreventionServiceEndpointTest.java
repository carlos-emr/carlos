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

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.PreventionResponse;

/**
 * HTTP-level endpoint tests for {@link PreventionService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("PreventionService REST endpoint tests")
class PreventionServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private PreventionManager mockPreventionManager;

    @Override
    protected Object getServiceBean() {
        PreventionService service = new PreventionService();
        injectDependency(service, "preventionManager", mockPreventionManager);
        return service;
    }

    @Nested
    @DisplayName("GET /preventions/active")
    class GetActivePreventions {

        @Test
        @DisplayName("should return 200 with preventions")
        void shouldReturn200_whenPreventionsExist() {
            Prevention prevention = new Prevention();
            when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(123)))
                .thenReturn(List.of(prevention));

            Response response = request().path("/preventions/active")
                .query("demographicNo", 123)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no preventions")
        void shouldReturn200WithEmptyList_whenNoPreventions() {
            when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(456)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/preventions/active")
                .query("demographicNo", 456)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            PreventionResponse body = response.readEntity(PreventionResponse.class);
            assertThat(body.getPreventions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /preventions/immunizations/{demographicNo}")
    class GetImmunizations {

        @Test
        @DisplayName("should return 200 with immunizations")
        void shouldReturn200_whenImmunizationsExist() {
            Prevention immunization = new Prevention();
            when(mockPreventionManager.getImmunizationsByDemographic(any(LoggedInInfo.class), eq(789)))
                .thenReturn(List.of(immunization));

            Response response = request().path("/preventions/immunizations/789").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no immunizations")
        void shouldReturn200WithEmptyList_whenNoImmunizations() {
            when(mockPreventionManager.getImmunizationsByDemographic(any(LoggedInInfo.class), eq(100)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/preventions/immunizations/100").get();

            assertThat(response.getStatus()).isEqualTo(200);
            PreventionResponse body = response.readEntity(PreventionResponse.class);
            assertThat(body.getPreventions()).isEmpty();
        }
    }
}
