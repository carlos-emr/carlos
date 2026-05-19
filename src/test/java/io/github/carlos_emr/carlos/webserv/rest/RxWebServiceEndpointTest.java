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
import static org.mockito.ArgumentMatchers.anyInt;
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

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.FavoriteConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.PrescriptionConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;

/**
 * HTTP-level endpoint tests for {@link RxWebService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for the prescription REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("RxWebService REST endpoint tests")
class RxWebServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private RxManager mockRxManager;

    @Mock
    private DrugConverter mockDrugConverter;

    @Mock
    private PrescriptionConverter mockPrescriptionConverter;

    @Mock
    private FavoriteConverter mockFavoriteConverter;

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private PrescriptionManager mockPrescriptionManager;

    @Override
    protected Object getServiceBean() {
        RxWebService service = new RxWebService();
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "rxManager", mockRxManager);
        injectDependency(service, "drugConverter", mockDrugConverter);
        injectDependency(service, "prescriptionConverter", mockPrescriptionConverter);
        injectDependency(service, "favoriteConverter", mockFavoriteConverter);
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "prescriptionManager", mockPrescriptionManager);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), anyInt()))
            .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), anyInt()))
            .thenReturn(true);
        Provider testProvider = new Provider();
        testProvider.setProviderNo("999998");
        when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(testProvider);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    @Nested
    @DisplayName("GET /rx/drugs/all/{demographicNo}")
    class GetAllDrugs {

        @Test
        @DisplayName("should return 200 with drug list")
        void shouldReturn200_whenDrugsExist() throws Exception {
            Drug drug = new Drug();
            drug.setId(1);
            drug.setBrandName("Aspirin");
            when(mockRxManager.getDrugs(any(LoggedInInfo.class), eq(1), eq(RxManager.ALL)))
                .thenReturn(List.of(drug));

            DrugTo1 drugTo = new DrugTo1();
            drugTo.setDrugId(1);
            drugTo.setBrandName("Aspirin");
            when(mockDrugConverter.getAllAsTransferObjects(any(LoggedInInfo.class), any()))
                .thenReturn(List.of(drugTo));

            Response response = request().path("/rx/drugs/all/1").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no drugs")
        void shouldReturn200WithEmptyList_whenNoDrugs() throws Exception {
            when(mockRxManager.getDrugs(any(LoggedInInfo.class), eq(2), eq(RxManager.ALL)))
                .thenReturn(Collections.emptyList());
            when(mockDrugConverter.getAllAsTransferObjects(any(LoggedInInfo.class), any()))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/rx/drugs/all/2").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /rx/drugs/current/{demographicNo}")
    class GetCurrentDrugs {

        @Test
        @DisplayName("should return 200 with current drugs")
        void shouldReturn200_whenCurrentDrugsExist() throws Exception {
            Drug drug = new Drug();
            drug.setId(1);
            when(mockRxManager.getDrugs(any(LoggedInInfo.class), eq(1), eq(RxManager.CURRENT)))
                .thenReturn(List.of(drug));
            when(mockDrugConverter.getAllAsTransferObjects(any(LoggedInInfo.class), any()))
                .thenReturn(List.of(new DrugTo1()));

            Response response = request().path("/rx/drugs/current/1").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /rx/drugs/archived/{demographicNo}")
    class GetArchivedDrugs {

        @Test
        @DisplayName("should return 200 with archived drugs")
        void shouldReturn200_whenArchivedDrugsExist() throws Exception {
            Drug drug = new Drug();
            drug.setId(1);
            drug.setArchived(true);
            when(mockRxManager.getDrugs(any(LoggedInInfo.class), eq(1), eq(RxManager.ARCHIVED)))
                .thenReturn(List.of(drug));
            when(mockDrugConverter.getAllAsTransferObjects(any(LoggedInInfo.class), any()))
                .thenReturn(List.of(new DrugTo1()));

            Response response = request().path("/rx/drugs/archived/1").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /rx/history")
    class GetDrugHistory {

        @Test
        @DisplayName("should return 200 with drug history")
        void shouldReturn200_whenHistoryExists() {
            Drug drug = new Drug();
            drug.setId(1);
            when(mockRxManager.getHistory(eq(1), any(LoggedInInfo.class), eq(100)))
                .thenReturn(List.of(drug));
            when(mockDrugConverter.getAllAsTransferObjects(any(LoggedInInfo.class), any()))
                .thenReturn(List.of(new DrugTo1()));

            Response response = request().path("/rx/history")
                .query("drugId", 1)
                .query("demographicNo", 100)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty history when no records")
        void shouldReturn200WithEmpty_whenNoHistory() {
            when(mockRxManager.getHistory(eq(999), any(LoggedInInfo.class), eq(100)))
                .thenReturn(Collections.emptyList());
            when(mockDrugConverter.getAllAsTransferObjects(any(LoggedInInfo.class), any()))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/rx/history")
                .query("drugId", 999)
                .query("demographicNo", 100)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /rx/rxStatus")
    class GetRxStatus {

        @Test
        @DisplayName("should return 200 with status values")
        void shouldReturn200_withStatusValues() {
            Response response = request().path("/rx/rxStatus").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
