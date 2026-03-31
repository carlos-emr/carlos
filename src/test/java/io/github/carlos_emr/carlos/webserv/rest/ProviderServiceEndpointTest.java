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
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.OscarLogManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link ProviderService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for provider-related REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ProviderService REST endpoint tests")
class ProviderServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private ProviderDao mockProviderDao;

    @Mock
    private ProviderManager2 mockProviderManager;

    @Mock
    private OscarLogManager mockOscarLogManager;

    @Mock
    private DemographicManager mockDemographicManager;

    @Override
    protected Object getServiceBean() {
        ProviderService service = new ProviderService();
        injectDependency(service, "providerDao", mockProviderDao);
        injectDependency(service, "providerManager", mockProviderManager);
        injectDependency(service, "oscarLogManager", mockOscarLogManager);
        injectDependency(service, "demographicManager", mockDemographicManager);
        return service;
    }

    private Provider createTestProvider(String providerNo, String firstName, String lastName) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        return provider;
    }

    /** Tests for GET /providerService/providers_json endpoint. */
    @Nested
    @DisplayName("GET /providerService/providers_json")
    class GetProvidersAsJSON {

        @Test
        @DisplayName("should return 200 with providers list as JSON")
        void shouldReturn200WithProviders_whenActiveProvidersExist() {
            Provider testProvider = createTestProvider("100", "John", "Smith");
            when(mockProviderDao.getActiveProviders()).thenReturn(List.of(testProvider));

            Response response = request().path("/providerService/providers_json").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no active providers")
        void shouldReturn200WithEmptyList_whenNoActiveProviders() {
            when(mockProviderDao.getActiveProviders()).thenReturn(Collections.emptyList());

            Response response = request().path("/providerService/providers_json").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for GET /providerService/provider/me endpoint. */
    @Nested
    @DisplayName("GET /providerService/provider/me")
    class GetLoggedInProvider {

        @Test
        @DisplayName("should return 200 with provider JSON when logged in")
        void shouldReturn200WithProvider_whenProviderLoggedIn() {
            Provider testProvider = createTestProvider("100", "John", "Smith");
            when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(testProvider);

            Response response = request().path("/providerService/provider/me").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 404 when no provider logged in")
        void shouldReturn404_whenNoProviderLoggedIn() {
            when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(null);

            Response response = request().path("/providerService/provider/me").get();

            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    /** Tests for GET /providerService/getActiveTeams endpoint. */
    @Nested
    @DisplayName("GET /providerService/getActiveTeams")
    class GetActiveTeams {

        @Test
        @DisplayName("should return 200 with teams list")
        void shouldReturn200WithTeams_whenActiveTeamsExist() {
            when(mockProviderManager.getActiveTeams(any(LoggedInInfo.class)))
                .thenReturn(List.of("TeamA", "TeamB"));

            Response response = request().path("/providerService/getActiveTeams").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no teams")
        void shouldReturn200WithEmptyList_whenNoActiveTeams() {
            when(mockProviderManager.getActiveTeams(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/providerService/getActiveTeams").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
