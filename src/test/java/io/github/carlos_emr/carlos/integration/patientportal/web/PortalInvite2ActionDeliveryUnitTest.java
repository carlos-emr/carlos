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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Channel;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.Decision;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.InviteRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The invite, resend and recover routes: parameters in, workflow outcomes and refusals out as JSON. */
@Tag("unit")
@Tag("patient-portal")
class PortalInvite2ActionDeliveryUnitTest {

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalStaffContextResolver resolver = mock(PortalStaffContextResolver.class);
    private final PortalInviteDeliveryService invites = mock(PortalInviteDeliveryService.class);
    private final DemographicManager demographics = mock(DemographicManager.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo session = mock(LoggedInInfo.class);
    private final Demographic patient = new Demographic();
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext(
            "999998", "Synthetic Provider", Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        request.setParameter("demographicNo", "123");
        patient.setDemographicNo(123);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.hasPrivilege(any(), eq("_email"), anyString(), isNull())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        when(resolver.resolveForPatient(any(), any(), eq(123))).thenReturn(staff);
        when(demographics.getDemographic(session, 123)).thenReturn(patient);
    }

    @AfterEach
    void tearDown() {
        if (login != null) {
            login.close();
        }
        if (servlet != null) {
            servlet.close();
        }
    }

    @Test
    @DisplayName("should invite by email and answer with the recorded delivery")
    void shouldInvite_andAnswerWithTheDelivery() throws Exception {
        request.setParameter("method", "create");
        PatientPortalInviteDelivery sent = delivery(State.SENT);
        when(invites.invite(same(session), same(patient), same(staff), any())).thenReturn(sent);
        when(invites.isRecoverable(sent)).thenReturn(false);

        execute();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = payload();
        assertThat(body.get("ok").booleanValue()).isTrue();
        assertThat(body.get("delivery").get("state").asText()).isEqualTo("sent");
        assertThat(body.get("delivery").get("finished").booleanValue()).isTrue();
        assertThat(body.get("delivery").get("decisions").size()).isZero();
        ArgumentCaptor<InviteRequest> invite = ArgumentCaptor.forClass(InviteRequest.class);
        verify(invites).invite(same(session), same(patient), same(staff), invite.capture());
        assertThat(invite.getValue()).isEqualTo(new InviteRequest(Channel.EMAIL, false, false, null, false));
    }

    @Test
    @DisplayName("should pass replacement and a documented consent override through")
    void shouldPassConfirmations_toTheWorkflow() throws Exception {
        request.setParameter("method", "create");
        request.setParameter("confirmReplace", "true");
        request.setParameter("consentOverride", "true");
        request.setParameter("consentOverrideReason", "Verbal consent at the front desk");
        when(invites.invite(any(), any(), any(), any())).thenReturn(delivery(State.SENT));

        execute();

        ArgumentCaptor<InviteRequest> invite = ArgumentCaptor.forClass(InviteRequest.class);
        verify(invites).invite(any(), any(), any(), invite.capture());
        assertThat(invite.getValue()).isEqualTo(
                new InviteRequest(Channel.EMAIL, true, true, "Verbal consent at the front desk", false));
    }

    @Test
    @DisplayName("should pass staff's confirmation to withdraw a stuck attempt through")
    void shouldPassWithdrawStale_toTheWorkflow() throws Exception {
        request.setParameter("method", "create");
        request.setParameter("withdrawStale", "true");
        when(invites.invite(any(), any(), any(), any())).thenReturn(delivery(State.SENT));

        execute();

        ArgumentCaptor<InviteRequest> invite = ArgumentCaptor.forClass(InviteRequest.class);
        verify(invites).invite(any(), any(), any(), invite.capture());
        assertThat(invite.getValue().withdrawStale()).isTrue();
    }

    @Test
    @DisplayName("should answer a stuck earlier attempt with a conflict the page can confirm")
    void shouldAnswerStaleAttempt_withConflict() throws Exception {
        request.setParameter("method", "create");
        when(invites.invite(any(), any(), any(), any())).thenThrow(refusal("STALE_ATTEMPT_EXISTS"));

        execute();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(payload().get("reason").asText()).isEqualTo("stale_attempt_exists");
    }

    @Test
    @DisplayName("should resend the named invitation")
    void shouldResend_theNamedInvitation() throws Exception {
        request.setParameter("method", "resend");
        request.setParameter("inviteId", "41");
        when(invites.resend(same(session), same(patient), eq(41L), same(staff), any()))
                .thenReturn(delivery(State.SENT));

        execute();

        assertThat(response.getStatus()).isEqualTo(200);
        verify(invites).resend(same(session), same(patient), eq(41L), same(staff), any());
    }

    @Test
    @DisplayName("should refuse a resend without an invitation")
    void shouldRefuseResend_withoutAnInvitation() throws Exception {
        request.setParameter("method", "resend");

        execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(invites);
    }

    @Test
    @DisplayName("should apply a staff decision to the named delivery")
    void shouldRecover_withTheNamedDecision() throws Exception {
        request.setParameter("method", "recover");
        request.setParameter("deliveryId", "9");
        request.setParameter("decision", "confirmNotSent");
        when(invites.recover(same(session), same(patient), eq(9L), eq(Decision.CONFIRM_NOT_SENT), same(staff)))
                .thenReturn(delivery(State.REVOKED));

        execute();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(payload().get("delivery").get("state").asText()).isEqualTo("revoked");
    }

    @Test
    @DisplayName("should refuse an unknown decision")
    void shouldRefuseRecovery_withAnUnknownDecision() throws Exception {
        request.setParameter("method", "recover");
        request.setParameter("deliveryId", "9");
        request.setParameter("decision", "delete");

        execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(invites);
    }

    @Test
    @DisplayName("should require email write to resolve a delivery, which closes an outbox row")
    void shouldRefuseRecovery_withoutEmailWrite() throws Exception {
        when(security.hasPrivilege(any(), eq("_email"), anyString(), isNull())).thenReturn(false);
        request.setParameter("method", "recover");
        request.setParameter("deliveryId", "9");
        request.setParameter("decision", "abandon");

        execute();

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(invites);
    }

    @Test
    @DisplayName("should hand a text message request to the workflow, which refuses it")
    void shouldPassTheSmsChannel_toTheWorkflow() throws Exception {
        request.setParameter("method", "create");
        request.setParameter("channel", "sms");
        when(invites.invite(any(), any(), any(), any())).thenThrow(refusal("CHANNEL_UNAVAILABLE"));

        execute();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(payload().get("reason").asText()).isEqualTo("channel_unavailable");
    }

    @Test
    @DisplayName("should refuse an unknown channel before the workflow runs")
    void shouldRefuse_anUnknownChannel() throws Exception {
        request.setParameter("method", "create");
        request.setParameter("channel", "fax");

        execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(invites);
    }

    @Test
    @DisplayName("should answer a workflow refusal with its reason code and fixed message")
    void shouldAnswerRefusals_withReasonCodes() throws Exception {
        request.setParameter("method", "create");
        when(invites.invite(any(), any(), any(), any())).thenThrow(refusal("PENDING_INVITE_EXISTS"));

        execute();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(payload().get("reason").asText()).isEqualTo("pending_invite_exists");
        assertThat(payload().get("message").asText()).contains("pending invitation");
    }

    @Test
    @DisplayName("should map a portal failure the same way every portal action does")
    void shouldMapPortalFailures_likeOtherPortalActions() throws Exception {
        request.setParameter("method", "create");
        when(invites.invite(any(), any(), any(), any()))
                .thenThrow(PatientPortalException.ofStatus(409, "/x", "portal account already exists"));

        execute();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(payload().get("message").asText()).isEqualTo("portal account already exists");
    }

    @Test
    @DisplayName("should offer the recovery decisions once the delivery is recoverable")
    void shouldListDecisions_whenRecoverable() throws Exception {
        request.setParameter("method", "create");
        PatientPortalInviteDelivery uncertain = delivery(State.SEND_UNCERTAIN);
        when(invites.invite(any(), any(), any(), any())).thenReturn(uncertain);
        when(invites.isRecoverable(uncertain)).thenReturn(true);

        execute();

        List<String> decisions = new ArrayList<>();
        payload().get("delivery").get("decisions").forEach(node -> decisions.add(node.asText()));
        assertThat(decisions).containsExactly("confirmSent", "confirmNotSent");
    }

    private void execute() throws Exception {
        assertThat(new PortalInvite2Action(security, portal, resolver, invites, demographics).execute())
                .isEqualTo(org.apache.struts2.ActionSupport.NONE);
    }

    private JsonNode payload() throws IOException {
        return new ObjectMapper().readTree(response.getContentAsString());
    }

    private static PatientPortalInviteDelivery delivery(State state) {
        PatientPortalInviteDelivery row = new PatientPortalInviteDelivery(
                "inv-1", 123, "clinic", "https://portal-api.clinic.example", Channel.EMAIL, null, "999998");
        row.setState(state);
        row.setPortalInviteId(41L);
        return row;
    }

    private static RuntimeException refusal(String reason) {
        return new PortalInviteException(PortalInviteException.Reason.valueOf(reason));
    }
}
