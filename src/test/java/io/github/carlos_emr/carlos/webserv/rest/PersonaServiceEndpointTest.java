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

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.AppManager;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.PreferenceManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.PersonaRightsResponse;

/**
 * HTTP-level endpoint tests for {@link PersonaService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for persona/user-context REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("PersonaService REST endpoint tests")
class PersonaServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private ProgramManager2 mockProgramManager2;

    @Mock
    private MessagingManager mockMessagingManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private ConsultationManager mockConsultationManager;

    @Mock
    private PreferenceManager mockPreferenceManager;

    @Mock
    private AppManager mockAppManager;

    @Mock
    private DashboardManager mockDashboardManager;

    @Override
    protected Object getServiceBean() {
        PersonaService service = new PersonaService();
        injectDependency(service, "programManager2", mockProgramManager2);
        injectDependency(service, "messagingManager", mockMessagingManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "consultationManager", mockConsultationManager);
        injectDependency(service, "preferenceManager", mockPreferenceManager);
        injectDependency(service, "appManager", mockAppManager);
        injectDependency(service, "dashboardManager", mockDashboardManager);
        return service;
    }

    /** Tests for GET /persona/rights endpoint. */
    @Nested
    @DisplayName("GET /persona/rights")
    class GetMyRights {

        @Test
        @DisplayName("should return 200 with rights response")
        void shouldReturn200WithRights_whenCalled() {
            when(mockSecurityInfoManager.getRoles(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());
            when(mockSecurityInfoManager.getSecurityObjects(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/persona/rights").get();

            assertThat(response.getStatus()).isEqualTo(200);
            PersonaRightsResponse body = response.readEntity(PersonaRightsResponse.class);
            assertThat(body).isNotNull();
            assertThat(body.getRoles()).isEmpty();
            assertThat(body.getPrivileges()).isEmpty();
        }
    }

    /** Tests for GET /persona/hasRight endpoint. */
    @Nested
    @DisplayName("GET /persona/hasRight")
    class HasRight {

        @Test
        @DisplayName("should return success response when privilege is granted")
        void shouldReturnSuccess_whenPrivilegeGranted() {
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("r"), eq("123")))
                .thenReturn(true);

            Response response = request().path("/persona/hasRight")
                .query("objectName", "_demographic")
                .query("privilege", "r")
                .query("demographicNo", "123")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return error response when privilege is denied")
        void shouldReturnError_whenPrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("123")))
                .thenReturn(false);

            Response response = request().path("/persona/hasRight")
                .query("objectName", "_demographic")
                .query("privilege", "w")
                .query("demographicNo", "123")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for GET /persona/navbar endpoint. */
    @Nested
    @DisplayName("GET /persona/navbar")
    class GetMyNavbar {

        @Test
        @DisplayName("should return 200 with navbar response when provider has programs")
        void shouldReturn200WithNavbar_whenProviderHasPrograms() {
            Provider testProvider = new Provider();
            testProvider.setProviderNo("100");
            testProvider.setPractitionerNo("P100");
            when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(testProvider);

            when(mockProgramManager2.getProgramDomain(any(LoggedInInfo.class), eq("100")))
                .thenReturn(Collections.emptyList());
            when(mockProgramManager2.getCurrentProgramInDomain(any(LoggedInInfo.class), eq("100")))
                .thenReturn(null);
            when(mockMessagingManager.getMyInboxMessageCount(any(LoggedInInfo.class), eq("100"), eq(false)))
                .thenReturn(0);
            when(mockMessagingManager.getMyInboxMessageCount(any(LoggedInInfo.class), eq("100"), eq(true)))
                .thenReturn(0);
            when(mockConsultationManager.isConsultResponseEnabled()).thenReturn(false);
            when(mockConsultationManager.isConsultRequestEnabled()).thenReturn(false);
            when(mockDashboardManager.getDashboards(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/persona/navbar").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
