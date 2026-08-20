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

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalAccountAcknowledgementDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalAccountDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The account mutations and the panel read.
 *
 * <p>The behaviours worth protecting here are the ones that would mislead staff rather than fail
 * loudly: unlock gating on its own narrower object, the forced-reset note travelling with the
 * unlock response, and the panel distinguishing "no portal account" from "could not read".
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("Portal account and panel actions")
class PortalAccountAndPanelActionUnitTest {

    private static final int DEMOGRAPHIC_NO = 123;

    private SecurityInfoManager securityInfoManager;
    private PatientPortalService patientPortalService;
    private PortalStaffContextResolver staffContextResolver;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<ServletActionContext> servletActionContextMock;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        patientPortalService = mock(PatientPortalService.class);
        staffContextResolver = mock(PortalStaffContextResolver.class);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic
                .when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(true);
        when(staffContextResolver.resolve(any(), any()))
                .thenReturn(
                        new PatientPortalStaffContext(
                                "999998",
                                "Dr Example",
                                Set.of(
                                        PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE,
                                        PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK,
                                        PatientPortalStaffContext.PERMISSION_INVITE_MANAGE)));
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

    private PortalAccount2Action accountAction() {
        return new PortalAccount2Action(
                securityInfoManager, patientPortalService, staffContextResolver);
    }

    private PortalPanel2Action panelAction() {
        return new PortalPanel2Action(
                securityInfoManager, patientPortalService, staffContextResolver);
    }

    private PatientPortalAccountAcknowledgementDto acknowledgement() {
        return new PatientPortalAccountAcknowledgementDto(5L, "active", true, null);
    }

    @Nested
    @DisplayName("account mutations")
    class AccountMutations {

        @Test
        @DisplayName("should reject GET before touching the portal")
        void shouldRejectGet_beforeAnySideEffect() throws Exception {
            request.setMethod("GET");
            request.setParameter("method", "unlock");

            accountAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getContentAsString()).contains("method_not_allowed");
            verifyNoInteractions(patientPortalService);
        }

        /**
         * Unlock forces a password reset on the patient, so it is gated on its own narrower object.
         * If it gated on _portal.account instead, anyone able to view an account could force a
         * reset, and splitting the objects would mean nothing.
         */
        @Test
        @DisplayName("should gate unlock on the narrower unlock object")
        void shouldRequireUnlockObject_ratherThanAccountManagement() throws Exception {
            when(securityInfoManager.hasPrivilege(
                            any(),
                            eq(PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK),
                            anyString(),
                            isNull()))
                    .thenReturn(false);
            request.setParameter("method", "unlock");

            assertThatThrownBy(() -> accountAction().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_portal.account.unlock)");
            verifyNoInteractions(patientPortalService);
        }

        @Test
        @DisplayName("should tell staff the patient must still reset their password")
        void shouldReturnForcedResetNote_whenAccountIsUnlocked() throws Exception {
            request.setParameter("method", "unlock");
            when(patientPortalService.unlockAccount(eq(DEMOGRAPHIC_NO), any()))
                    .thenReturn(acknowledgement());

            accountAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString())
                    .contains("forcePasswordReset\":true")
                    .contains("password reset");
        }

        @Test
        @DisplayName("should require a reason before disabling an account")
        void shouldRefuseDisable_whenNoReasonIsGiven() throws Exception {
            request.setParameter("method", "access");
            request.setParameter("enabled", "false");

            accountAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verify(patientPortalService, never())
                    .setAccountAccess(anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                            anyString(), any());
        }

        @Test
        @DisplayName("should disable with the supplied reason")
        void shouldDisableAccount_whenReasonIsGiven() throws Exception {
            request.setParameter("method", "access");
            request.setParameter("enabled", "false");
            request.setParameter("reason", "left_practice");
            when(patientPortalService.setAccountAccess(
                            eq(DEMOGRAPHIC_NO), eq(false), eq("left_practice"), any()))
                    .thenReturn(acknowledgement());

            accountAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verify(patientPortalService)
                    .setAccountAccess(eq(DEMOGRAPHIC_NO), eq(false), eq("left_practice"), any());
        }

        /**
         * Found by running against a live portal. A patient who never activated is the most
         * ordinary case there is, and the generic 404 copy sent staff to check the portal
         * connection over it.
         */
        @Test
        @DisplayName("should read a 404 as no portal account, not as a broken connection")
        void shouldReportNoPortalAccount_whenUnlockingAPatientWhoNeverActivated() throws Exception {
            request.setParameter("method", "unlock");
            when(patientPortalService.unlockAccount(eq(DEMOGRAPHIC_NO), any()))
                    .thenThrow(PatientPortalException.ofStatus(404, "/x", null));

            accountAction().execute();

            assertThat(response.getContentAsString())
                    .contains("does not have a patient portal account")
                    .doesNotContain("connection needs checking");
        }

        @Test
        @DisplayName("should re-enable without demanding a reason")
        void shouldEnableAccount_withoutARequiredReason() throws Exception {
            request.setParameter("method", "access");
            request.setParameter("enabled", "true");
            when(patientPortalService.setAccountAccess(
                            eq(DEMOGRAPHIC_NO), eq(true), anyString(), any()))
                    .thenReturn(acknowledgement());

            accountAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }

    @Nested
    @DisplayName("panel read")
    class PanelRead {

        private PatientPortalInviteDto invite() {
            return new PatientPortalInviteDto(
                    7L, "maplecreek", DEMOGRAPHIC_NO, "pending", "999998", "Dr Example", 1,
                    Instant.now(), "Dr Example", Instant.now(), null, null);
        }

        private PatientPortalAccountDto account() {
            return new PatientPortalAccountDto(
                    5L, "maplecreek", DEMOGRAPHIC_NO, "active", false, false, null, null);
        }

        @Test
        @DisplayName("should permit GET, being read-only")
        void shouldAllowGet_forAReadOnlyPanel() throws Exception {
            request.setMethod("GET");
            when(patientPortalService.listInvites(anyInt(), anyInt(), any()))
                    .thenReturn(List.of(invite()));
            when(patientPortalService.findAccount(anyInt(), any())).thenReturn(account());

            panelAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("invites").contains("account");
        }

        @Test
        @DisplayName("should refuse a method that is neither GET nor POST")
        void shouldRejectDelete_asUnsupported() throws Exception {
            request.setMethod("DELETE");

            panelAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getContentAsString()).contains("method_not_allowed");
        }

        /**
         * A section the caller may not read is absent, not empty. An empty invite list means "this
         * patient has no invitations", and showing that to someone who merely lacks the privilege
         * would be a falsehood the panel would render faithfully.
         */
        @Test
        @DisplayName("should omit a section the provider may not read, rather than showing it empty")
        void shouldOmitInvites_whenProviderLacksTheInviteObject() throws Exception {
            request.setMethod("GET");
            when(securityInfoManager.hasPrivilege(
                            any(), eq(PortalStaffContextResolver.OBJECT_INVITE), anyString(),
                            isNull()))
                    .thenReturn(false);
            when(patientPortalService.findAccount(anyInt(), any())).thenReturn(account());

            panelAction().execute();

            assertThat(response.getContentAsString()).doesNotContain("invites");
            assertThat(response.getContentAsString()).contains("account");
            verify(patientPortalService, never()).listInvites(anyInt(), anyInt(), any());
        }

        @Test
        @DisplayName("should refuse a provider holding neither portal object")
        void shouldThrow_whenProviderMayReadNeitherSection() {
            request.setMethod("GET");
            when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> panelAction().execute())
                    .isInstanceOf(SecurityException.class);
        }

        /**
         * Most patients have never activated, so a 404 on the account lookup is the routine case
         * and must not read as an error. The distinction matters in the other direction too:
         * reporting an outage as "no account" would invite staff to issue an invitation the
         * patient does not need.
         */
        @Test
        @DisplayName("should report an absent account as a state, not an error")
        void shouldReportNoPortalAccount_whenTheLookupIsNotFound() throws Exception {
            request.setMethod("GET");
            when(patientPortalService.listInvites(anyInt(), anyInt(), any())).thenReturn(List.of());
            when(patientPortalService.findAccount(anyInt(), any()))
                    .thenThrow(PatientPortalException.ofStatus(404, "/x", null));

            panelAction().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString())
                    .contains("no_portal_account")
                    .doesNotContain("accountError");
        }

        @Test
        @DisplayName("should report a portal outage as unavailable, not as an absent account")
        void shouldReportUnavailable_whenTheAccountLookupFailsForAnotherReason() throws Exception {
            request.setMethod("GET");
            when(patientPortalService.listInvites(anyInt(), anyInt(), any())).thenReturn(List.of());
            when(patientPortalService.findAccount(anyInt(), any()))
                    .thenThrow(PatientPortalException.ofTransportFailure("/x", null));

            panelAction().execute();

            assertThat(response.getContentAsString())
                    .contains("accountError")
                    .doesNotContain("no_portal_account");
        }

        @Test
        @DisplayName("should still return invitations when the account lookup fails")
        void shouldKeepInvites_whenOnlyTheAccountSectionFails() throws Exception {
            request.setMethod("GET");
            when(patientPortalService.listInvites(anyInt(), anyInt(), any()))
                    .thenReturn(List.of(invite()));
            when(patientPortalService.findAccount(anyInt(), any()))
                    .thenThrow(PatientPortalException.ofTransportFailure("/x", null));

            panelAction().execute();

            assertThat(response.getContentAsString())
                    .contains("inviteId")
                    .contains("accountError");
        }
    }
}
