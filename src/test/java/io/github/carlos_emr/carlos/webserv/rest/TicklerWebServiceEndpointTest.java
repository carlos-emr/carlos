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

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.TicklerResponse;

/**
 * HTTP-level endpoint tests for {@link TicklerWebService} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-RS pipeline: path routing,
 * JSON serialization via Jackson, query parameter binding, and HTTP
 * status codes. Dependencies are mocked — no database required.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("TicklerWebService REST endpoint tests")
class TicklerWebServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private TicklerManager mockTicklerManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private ProgramManager2 mockProgramManager;

    @Override
    protected Object getServiceBean() {
        TicklerWebService service = new TicklerWebService();
        injectDependency(service, "ticklerManager", mockTicklerManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "programManager", mockProgramManager);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any()))
            .thenReturn(true);
        registerMock(UserPropertyDAO.class, org.mockito.Mockito.mock(UserPropertyDAO.class));
    }

    private Tickler createTestTickler(int id, String message) {
        Tickler tickler = new Tickler();
        tickler.setId(id);
        tickler.setMessage(message);
        return tickler;
    }

    /** Tests for POST /tickler/search endpoint. */
    @Nested
    @DisplayName("POST /tickler/search")
    class SearchTicklers {

        @Test
        @DisplayName("should return 200 with ticklers matching search criteria")
        void shouldReturn200WithTicklers_whenSearchReturnsResults() {
            when(mockTicklerManager.getTicklers(any(LoggedInInfo.class), any(CustomFilter.class), eq(0), eq(10)))
                .thenReturn(Collections.emptyList());

            String searchJson = "{\"status\":\"A\"}";

            Response response = request().path("/tickler/search")
                .query("startIndex", 0)
                .query("limit", 10)
                .post(Entity.json(searchJson));

            assertThat(response.getStatus()).isEqualTo(200);
            TicklerResponse body = response.readEntity(TicklerResponse.class);
            assertThat(body.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should return 200 with empty list when no ticklers match")
        void shouldReturn200WithEmptyList_whenNoTicklersMatch() {
            when(mockTicklerManager.getTicklers(any(LoggedInInfo.class), any(CustomFilter.class), eq(0), eq(10)))
                .thenReturn(Collections.emptyList());

            String searchJson = "{\"status\":\"C\"}";

            Response response = request().path("/tickler/search")
                .query("startIndex", 0)
                .query("limit", 10)
                .post(Entity.json(searchJson));

            assertThat(response.getStatus()).isEqualTo(200);
            TicklerResponse body = response.readEntity(TicklerResponse.class);
            assertThat(body.getContent()).isEmpty();
        }
    }

    /** Tests for GET /tickler/mine endpoint. */
    @Nested
    @DisplayName("GET /tickler/mine")
    class GetMyTicklers {

        @Test
        @DisplayName("should return 200 with current provider ticklers")
        void shouldReturn200WithMyTicklers_whenTicklersExist() {
            when(mockTicklerManager.getTicklers(any(LoggedInInfo.class), any(CustomFilter.class), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/tickler/mine")
                .query("limit", 20)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            TicklerResponse body = response.readEntity(TicklerResponse.class);
            assertThat(body.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should return 200 with empty list when provider has no ticklers")
        void shouldReturn200WithEmptyList_whenNoTicklersForProvider() {
            when(mockTicklerManager.getTicklers(any(LoggedInInfo.class), any(CustomFilter.class), eq(0), eq(10)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/tickler/mine")
                .query("limit", 10)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            TicklerResponse body = response.readEntity(TicklerResponse.class);
            assertThat(body.getContent()).isEmpty();
        }
    }

    /** Tests for GET /tickler/{demographicNo}/count/overdue endpoint. */
    @Nested
    @DisplayName("GET /tickler/{demographicNo}/count/overdue")
    class GetTicklerOverdueCount {

        @Test
        @DisplayName("should return 200 with overdue count")
        void shouldReturn200WithCount_whenOverdueTicklersExist() {
            when(mockTicklerManager.getActiveTicklerByDemoCount(any(LoggedInInfo.class), eq(123)))
                .thenReturn(5);

            Response response = request().path("/tickler/123/count/overdue").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for GET /tickler/textSuggestions endpoint. */
    @Nested
    @DisplayName("GET /tickler/textSuggestions")
    class GetTextSuggestions {

        @Test
        @DisplayName("should return 200 with text suggestions")
        void shouldReturn200WithSuggestions_whenSuggestionsExist() {
            TicklerTextSuggest suggestion = new TicklerTextSuggest();
            suggestion.setId(1);
            suggestion.setSuggestedText("Follow up in 2 weeks");

            when(mockTicklerManager.getActiveTextSuggestions(any(LoggedInInfo.class)))
                .thenReturn(List.of(suggestion));

            Response response = request().path("/tickler/textSuggestions").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
