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
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the patient-level {@code _con} read privilege checks added to
 * {@link ConsultationWebService}'s response, attachment and eReferral-attachment endpoints
 * so a caller cannot read another patient's consultation data by supplying an arbitrary
 * {@code demographicNo} (or pairing a foreign {@code responseId} with an authorized
 * {@code demographicNo}).
 *
 * @since 2026-06-24
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultationWebService privilege unit tests")
@Tag("unit")
@Tag("fast")
class ConsultationWebServicePrivilegeUnitTest extends CarlosUnitTestBase {

    @Mock
    private ConsultationManager consultationManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private ConsultationWebService service;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new ConsultationWebService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };

        injectDependency(service, "consultationManager", consultationManager);
        injectDependency(service, "securityInfoManager", securityInfoManager);
    }

    @Test
    @DisplayName("should deny getResponse for new response when caller lacks consultation read privilege")
    void shouldDenyGetResponse_whenCallerLacksReadPrivilegeForNewResponse() {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), eq(99))).thenReturn(false);

        // responseId <= 0 routes to the "new response" branch, which authorizes the supplied demographic.
        assertThatThrownBy(() -> service.getResponse(0, 99))
                .isInstanceOf(AccessDeniedException.class);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_con"), eq("r"), eq(99));
    }

    @Test
    @DisplayName("should deny getResponse using the response's own demographic when caller lacks privilege")
    void shouldDenyGetResponse_usingResponseDemographicWhenCallerLacksPrivilege() {
        ConsultationResponse stored = new ConsultationResponse();
        stored.setDemographicNo(50);
        when(consultationManager.getResponse(any(), eq(123))).thenReturn(stored);
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), eq(50))).thenReturn(false);

        // A foreign responseId (belongs to demographic 50) paired with an otherwise-authorized
        // demographicNo (7) must still be denied because authorization uses the response's demographic.
        assertThatThrownBy(() -> service.getResponse(123, 7))
                .isInstanceOf(AccessDeniedException.class);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_con"), eq("r"), eq(50));
    }

    @Test
    @DisplayName("should deny getResponseAttachments when caller lacks consultation read privilege")
    void shouldDenyGetResponseAttachments_whenCallerLacksReadPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), eq(99))).thenReturn(false);

        assertThatThrownBy(() -> service.getResponseAttachments(5, 99, true))
                .isInstanceOf(AccessDeniedException.class);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_con"), eq("r"), eq(99));
    }

    @Test
    @DisplayName("should deny getEReferAttachments when caller lacks consultation read privilege")
    void shouldDenyGetEReferAttachments_whenCallerLacksReadPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), eq(99))).thenReturn(false);
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = org.mockito.Mockito.mock(HttpServletResponse.class);

        assertThatThrownBy(() -> service.getEReferAttachments(99, request, httpResponse))
                .isInstanceOf(AccessDeniedException.class);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_con"), eq("r"), eq(99));
        verify(consultationManager, never()).getEReferAttachments(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should return ok for getEReferAttachments when caller has consultation read privilege")
    void shouldReturnOkGetEReferAttachments_whenCallerHasReadPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), eq(7))).thenReturn(true);
        when(consultationManager.getEReferAttachments(any(), any(), any(), eq(7)))
                .thenReturn(Collections.emptyList());
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = org.mockito.Mockito.mock(HttpServletResponse.class);

        Response response = service.getEReferAttachments(7, request, httpResponse);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_con"), eq("r"), eq(7));
        verify(consultationManager).getEReferAttachments(any(), any(), any(), eq(7));
    }

    @Test
    @DisplayName("should return not found when responseId has no stored consultation response")
    void shouldReturnNotFound_whenResponseIdHasNoStoredResponse() {
        when(consultationManager.getResponse(any(), eq(123))).thenReturn(null);

        assertThatThrownBy(() -> service.getResponse(123, 7))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.NOT_FOUND.getStatusCode()));

        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("should return bad request when demographicNo is missing for a new response")
    void shouldReturnBadRequest_whenDemographicNoMissingForNewResponse() {
        assertThatThrownBy(() -> service.getResponse(0, null))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode()));

        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("should return bad request when demographicNo is missing for response attachments")
    void shouldReturnBadRequest_whenDemographicNoMissingForResponseAttachments() {
        assertThatThrownBy(() -> service.getResponseAttachments(5, null, true))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode()));

        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("should return bad request when demographicNo is missing for eReferral attachments")
    void shouldReturnBadRequest_whenDemographicNoMissingForEReferAttachments() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = org.mockito.Mockito.mock(HttpServletResponse.class);

        assertThatThrownBy(() -> service.getEReferAttachments(null, request, httpResponse))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode()));

        verify(consultationManager, never()).getEReferAttachments(any(), any(), any(), any());
    }
}
