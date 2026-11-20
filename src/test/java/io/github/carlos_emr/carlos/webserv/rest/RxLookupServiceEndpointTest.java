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
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.managers.DrugLookUp;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;

/**
 * CXF local-transport endpoint tests for {@link RxLookupService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("RxLookupService REST endpoint tests")
class RxLookupServiceEndpointTest extends CarlosRestTestBase {
    @org.junit.jupiter.api.BeforeEach
    void authorizeFixture() {
        org.mockito.Mockito.when(mockSecurityInfoManager.hasPrivilege(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull())).thenReturn(true);
    }

    @Test
    void shouldRejectAccessBeforeLookup_whenPrivilegeIsDenied() {
        org.mockito.Mockito.when(mockSecurityInfoManager.hasPrivilege(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull())).thenReturn(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> request().path("/rxlookup/search").get())
                .hasRootCauseInstanceOf(SecurityException.class);
        org.mockito.Mockito.verify(mockSecurityInfoManager).hasPrivilege(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull());
        org.mockito.Mockito.verifyNoInteractions(mockDrugLookUpManager);
    }


    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private DrugLookUp mockDrugLookUpManager;

    @Override
    protected Object getServiceBean() {
        RxLookupService service = new RxLookupService();
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "drugLookUpManager", mockDrugLookUpManager);
        return service;
    }

    @Nested
    @DisplayName("GET /rxlookup/search")
    class Search {

        @Test
        @DisplayName("should return 200 with drug search results")
        void shouldReturn200_whenDrugsFound() {
            DrugSearchTo1 drug = new DrugSearchTo1();
            drug.setName("Aspirin");
            when(mockDrugLookUpManager.search("aspirin")).thenReturn(List.of(drug));

            Response response = request().path("/rxlookup/search")
                .query("string", "aspirin")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/success").booleanValue()).isTrue();
            assertThat(wireJson.at("/drugs")).hasSize(1);
            assertThat(wireJson.at("/drugs/0/name").textValue()).isEqualTo("Aspirin");
        }

        @Test
        @DisplayName("should return 200 with failure when no drugs found")
        void shouldReturn200WithFailure_whenNoDrugsFound() {
            when(mockDrugLookUpManager.search("nonexistent")).thenReturn(null);

            Response response = request().path("/rxlookup/search")
                .query("string", "nonexistent")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/success").booleanValue()).isFalse();
            assertThat(wireJson.at("/message").textValue()).isEqualTo("Failed to find drugs that match: nonexistent");
        }
    }

    @Nested
    @DisplayName("GET /rxlookup/details")
    class Details {

        @Test
        @DisplayName("should return 200 with drug details")
        void shouldReturn200_whenDrugFound() throws Exception {
            DrugSearchTo1 drug = new DrugSearchTo1();
            drug.setName("Aspirin");
            when(mockDrugLookUpManager.details("12345")).thenReturn(drug);

            Response response = request().path("/rxlookup/details")
                .query("id", "12345")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/success").booleanValue()).isTrue();
            assertThat(wireJson.at("/drugs")).hasSize(1);
            assertThat(wireJson.at("/drugs/0/name").textValue()).isEqualTo("Aspirin");
        }

        @Test
        @DisplayName("should return 200 with failure when drug not found")
        void shouldReturn200WithFailure_whenDrugNotFound() throws Exception {
            when(mockDrugLookUpManager.details("99999")).thenReturn(null);

            Response response = request().path("/rxlookup/details")
                .query("id", "99999")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/success").booleanValue()).isFalse();
            assertThat(wireJson.at("/message").textValue()).isEqualTo("Failed to find drugs that have an id: 99999");
        }
    }
}
