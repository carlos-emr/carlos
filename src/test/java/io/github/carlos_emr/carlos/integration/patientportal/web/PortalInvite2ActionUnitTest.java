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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalIssuedInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteIdentityValidator;
import io.github.carlos_emr.carlos.integration.patientportal.PortalSecret;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The staff entry point for portal invitations.
 *
 * <p>Two behaviours here are worth more than the rest: the action must refuse {@code GET} before any
 * side effect, and it must refuse to replace a pending invitation without explicit confirmation. The
 * second exists because the portal's create endpoint silently revokes the previous invite, so two
 * clicks strand an email that was already sent and nothing anywhere reports it.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalInvite2Action")
class PortalInvite2ActionUnitTest {

    private static final int DEMOGRAPHIC_NO = 123;

    private SecurityInfoManager securityInfoManager;
    private DemographicManager demographicManager;
    private PatientPortalService patientPortalService;
    private PortalStaffContextResolver staffContextResolver;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private PortalInvite2Action action;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        demographicManager = mock(DemographicManager.class);
        patientPortalService = mock(PatientPortalService.class);
        staffContextResolver = mock(PortalStaffContextResolver.class);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        // Matches the established pattern for 2Action tests in this codebase: ServletActionContext
        // is mocked rather than bound, because binding a real ActionContext pulls in the whole
        // Struts dispatcher for no benefit here.
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoStatic = Mockito.mockStatic(LoggedInInfo.class);
        loggedInInfoStatic
                .when(
                        () ->
                                LoggedInInfo.getLoggedInInfoFromSession(
                                        any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(true);
        when(staffContextResolver.resolve(any()))
                .thenReturn(
                        new PatientPortalStaffContext(
                                "999998",
                                "Dr Example",
                                Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE)));

        action =
                new PortalInvite2Action(
                        securityInfoManager,
                        demographicManager,
                        patientPortalService,
                        staffContextResolver,
                        new PortalInviteIdentityValidator());
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoStatic != null) {
            loggedInInfoStatic.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    private Demographic completeDemographic() {
        Demographic demographic = mock(Demographic.class);
        when(demographic.getEmail()).thenReturn("patient@example.com");
        when(demographic.getYearOfBirth()).thenReturn("1980");
        when(demographic.getMonthOfBirth()).thenReturn("01");
        when(demographic.getDateOfBirth()).thenReturn("15");
        when(demographic.getHin()).thenReturn("1234567890");
        return demographic;
    }

    private PatientPortalInviteDto invite(String status) {
        return new PatientPortalInviteDto(
                7L, "maplecreek", DEMOGRAPHIC_NO, status, "999998", "Dr Example", 1,
                Instant.now(), "Dr Example", Instant.now(), null, null);
    }

    private PatientPortalIssuedInviteDto issued() {
        return new PatientPortalIssuedInviteDto(invite("pending"), PortalSecret.of("token-value"));
    }

    private void createRequest() {
        request.setParameter("method", "create");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        // Built before the stubbing call: creating a mock inside thenReturn() leaves Mockito
        // mid-stub on the outer mock and fails with UnfinishedStubbingException.
        Demographic demographic = completeDemographic();
        when(demographicManager.getDemographic(any(), eq(Integer.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(demographic);
    }

    @Nested
    @DisplayName("method and privilege gates")
    class Gates {

        @Test
        @DisplayName("should reject GET before touching the portal")
        void shouldRejectGet_beforeAnySideEffect() throws Exception {
            request.setMethod("GET");
            request.setParameter("method", "create");
            request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));

            action.execute();

            assertThat(response.getStatus())
                    .isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(patientPortalService);
            verifyNoInteractions(demographicManager);
        }

        @Test
        @DisplayName("should reject HEAD before touching the portal")
        void shouldRejectHead_beforeAnySideEffect() throws Exception {
            request.setMethod("HEAD");
            request.setParameter("method", "revoke");
            request.setParameter("inviteId", "7");

            action.execute();

            assertThat(response.getStatus())
                    .isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(patientPortalService);
        }

        @Test
        @DisplayName("should refuse a provider without the invite privilege")
        void shouldThrowSecurityException_whenPrivilegeIsAbsent() {
            when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                    .thenReturn(false);
            createRequest();

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_portal.invite)");
            verifyNoInteractions(patientPortalService);
        }
    }

    @Nested
    @DisplayName("replace guard")
    class ReplaceGuard {

        /**
         * The hazard this action exists to contain. A second create returns 201 and silently
         * revokes the pending invite, so the email already sent to the patient stops working.
         */
        @Test
        @DisplayName("should refuse to replace a pending invite without confirmation")
        void shouldRefuseCreate_whenAPendingInviteWouldBeSilentlyRevoked() throws Exception {
            createRequest();
            when(patientPortalService.listInvites(eq(DEMOGRAPHIC_NO), anyInt(), any()))
                    .thenReturn(List.of(invite("pending")));

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            assertThat(response.getContentAsString()).contains("confirm_replace");
            verify(patientPortalService, never())
                    .createInvite(anyInt(), anyString(), any(), anyString(), any());
        }

        @Test
        @DisplayName("should create once the replacement is confirmed")
        void shouldCreate_whenReplacementIsConfirmed() throws Exception {
            createRequest();
            request.setParameter("confirmReplace", "true");
            when(patientPortalService.createInvite(
                            eq(DEMOGRAPHIC_NO), anyString(), any(), anyString(), any()))
                    .thenReturn(issued());

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verify(patientPortalService)
                    .createInvite(eq(DEMOGRAPHIC_NO), anyString(), any(), anyString(), any());
        }

        @Test
        @DisplayName("should create without confirmation when nothing would be revoked")
        void shouldCreate_whenNoPendingInviteExists() throws Exception {
            createRequest();
            when(patientPortalService.listInvites(eq(DEMOGRAPHIC_NO), anyInt(), any()))
                    .thenReturn(List.of(invite("revoked")));
            when(patientPortalService.createInvite(
                            eq(DEMOGRAPHIC_NO), anyString(), any(), anyString(), any()))
                    .thenReturn(issued());

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }

        /**
         * A portal hiccup must not become the reason a working invitation is silently revoked, so
         * an unreadable invite list is treated as "something may be pending".
         */
        @Test
        @DisplayName("should fail closed when the existing invites cannot be read")
        void shouldRequireConfirmation_whenTheInviteListCannotBeRead() throws Exception {
            createRequest();
            when(patientPortalService.listInvites(eq(DEMOGRAPHIC_NO), anyInt(), any()))
                    .thenThrow(
                            io.github.carlos_emr.carlos.integration.patientportal
                                    .PatientPortalException.ofTransportFailure("/x", null));

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            verify(patientPortalService, never())
                    .createInvite(anyInt(), anyString(), any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("record completeness")
    class RecordCompleteness {

        @Test
        @DisplayName("should name the missing fields rather than mint an unactivatable invite")
        void shouldRefuseCreate_whenTheRecordCannotProveIdentity() throws Exception {
            request.setParameter("method", "create");
            request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
            Demographic incomplete = mock(Demographic.class);
            when(incomplete.getEmail()).thenReturn("patient@example.com");
            when(incomplete.getHin()).thenReturn("  ");
            when(demographicManager.getDemographic(any(), eq(Integer.valueOf(DEMOGRAPHIC_NO))))
                    .thenReturn(incomplete);

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            assertThat(response.getContentAsString())
                    .contains("health card number")
                    .contains("date of birth");
            verify(patientPortalService, never())
                    .createInvite(anyInt(), anyString(), any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("responses")
    class Responses {

        /**
         * The activation token is a credential and delivery is CARLOS's own workflow. Returning it
         * to the browser would put it in the page, the browser cache, and any proxy log in between.
         */
        @Test
        @DisplayName("should never return the activation token to the browser")
        void shouldOmitActivationToken_fromTheResponse() throws Exception {
            createRequest();
            request.setParameter("confirmReplace", "true");
            when(patientPortalService.createInvite(
                            eq(DEMOGRAPHIC_NO), anyString(), any(), anyString(), any()))
                    .thenReturn(issued());

            action.execute();

            assertThat(response.getContentAsString()).doesNotContain("token-value");
        }

        @Test
        @DisplayName("should return NONE so Struts does not append a JSP to the JSON")
        void shouldReturnNone_afterWritingTheResponseBody() throws Exception {
            createRequest();
            request.setParameter("confirmReplace", "true");
            when(patientPortalService.createInvite(
                            eq(DEMOGRAPHIC_NO), anyString(), any(), anyString(), any()))
                    .thenReturn(issued());

            assertThat(action.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        }

        @Test
        @DisplayName("should reject an unknown method rather than guessing")
        void shouldReturnBadRequest_whenMethodIsUnknown() throws Exception {
            request.setParameter("method", "destroy");

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(patientPortalService);
        }

        @Test
        @DisplayName("should revoke an invitation by id")
        void shouldRevokeInvite_whenIdIsSupplied() throws Exception {
            request.setParameter("method", "revoke");
            request.setParameter("inviteId", "7");
            when(patientPortalService.revokeInvite(eq(7L), any())).thenReturn(invite("revoked"));

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("revoked");
        }
    }
}
