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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.integration.patientportal.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Verifies patient authorization, invitation ownership and the unavailable issuance boundary. */
@Tag("unit")
@Tag("patient-portal")
class PortalInvite2ActionUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalStaffContextResolver resolver = mock(PortalStaffContextResolver.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext(
            "999998", "Synthetic Provider", Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach void setUp() {
        request.setMethod("POST");
        request.setParameter("method", "revoke");
        request.setParameter("demographicNo", "123");
        request.setParameter("inviteId", "7");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request))
                .thenReturn(mock(LoggedInInfo.class));
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        when(resolver.resolveForPatient(any(), any(), eq(123))).thenReturn(staff);
    }

    @AfterEach void tearDown() { login.close(); servlet.close(); }

    private void execute() throws Exception {
        assertThat(new PortalInvite2Action(security, portal, resolver).execute())
                .isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(response.getContentType()).startsWith("application/json");
    }

    private PatientPortalInviteDto invite(long id, int patient, String status) {
        return new PatientPortalInviteDto(id, "clinic", patient, status, "999998",
                "Synthetic Provider", 1, null, "Synthetic Provider", null, null, null);
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "HEAD", "post", "DELETE"})
    void rejectsMethodsBeforeAnyLookup(String method) throws Exception {
        request.setMethod(method);
        execute();
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(security, resolver, portal);
    }

    @ParameterizedTest @ValueSource(strings = {"create", "resend"})
    void issuanceIsUnavailableEvenWithReplacementConfirmed(String method) throws Exception {
        request.setParameter("method", method);
        request.setParameter("confirmReplace", "true");
        execute();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("portal_invitation_unavailable");
        verifyNoInteractions(portal, resolver);
    }

    @ParameterizedTest @ValueSource(strings = {"_demographic", "_portal.invite"})
    void rejectsScopedDenialsEvenWithGlobalPrivilege(String object) throws Exception {
        when(security.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(security.hasPrivilege(any(), eq(object), anyString(), eq("123"))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(portal, resolver);
    }

    @Test void rejectsRestrictedPatientRecord() throws Exception {
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(portal, resolver);
    }

    @Test void rejectsMissingPatient() throws Exception {
        request.removeParameter("demographicNo");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(portal, resolver);
    }

    @Test void rejectsForeignInvitationBeforeMutation() throws Exception {
        when(portal.listInvites(eq(123), same(staff)))
                .thenReturn(List.of(invite(7, 456, "pending")));
        execute();
        assertThat(response.getStatus()).isEqualTo(404);
        verify(portal, never()).revokeInvite(anyLong(), any());
    }

    @Test void revokesVerifiedInvitationWithScopedStaffIdentity() throws Exception {
        when(portal.listInvites(eq(123), same(staff)))
                .thenReturn(List.of(invite(7, 123, "pending")));
        when(portal.revokeInvite(eq(7L), same(staff))).thenReturn(invite(7, 123, "revoked"));
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("revoked");
        verify(resolver).resolveForPatient(any(), eq(Set.of(PortalStaffContextResolver.OBJECT_INVITE)), eq(123));
        verify(portal).revokeInvite(eq(7L), same(staff));
    }

    @Test void revokesVerifiedInvitationBeyondIntegerIdRange() throws Exception {
        long id = Integer.MAX_VALUE + 1L;
        request.setParameter("inviteId", String.valueOf(id));
        when(portal.listInvites(eq(123), same(staff)))
                .thenReturn(List.of(invite(id, 123, "pending")));
        when(portal.revokeInvite(eq(id), same(staff))).thenReturn(invite(id, 123, "revoked"));
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(portal).revokeInvite(eq(id), same(staff));
    }

    @Test void lookupFailureCannotAuthorizeMutation() throws Exception {
        when(portal.listInvites(eq(123), same(staff)))
                .thenThrow(PatientPortalException.ofTransportFailure("/internal/carlos/patients/{id}/invites", null));
        execute();
        assertThat(response.getStatus()).isEqualTo(504);
        verify(portal, never()).revokeInvite(anyLong(), any());
    }

    @Test void unexpectedReturnedIdentityIsNotReportedAsSuccess() throws Exception {
        when(portal.listInvites(eq(123), same(staff)))
                .thenReturn(List.of(invite(7, 123, "pending")));
        when(portal.revokeInvite(eq(7L), same(staff))).thenReturn(invite(8, 123, "revoked"));
        execute();
        assertThat(response.getStatus()).isEqualTo(502);
    }
    @Test void failsClosedWhenFullListingDoesNotContainSelectedInvitation() throws Exception {
        when(portal.listInvites(eq(123), same(staff)))
                .thenReturn(Collections.nCopies(100, invite(1, 123, "revoked")));
        execute();
        assertThat(response.getStatus()).isEqualTo(404);
        verify(portal, times(1)).listInvites(eq(123), same(staff));
        verify(portal, never()).revokeInvite(anyLong(), any());
    }
}
