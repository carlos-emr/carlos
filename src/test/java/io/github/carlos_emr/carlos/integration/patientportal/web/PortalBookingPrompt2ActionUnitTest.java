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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalAccountDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingPromptDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingPromptRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Access, account eligibility, withdrawal ownership, and confirmed versus unknown mutation audits. */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingPrompt2ActionUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalStaffContextResolver resolver = mock(PortalStaffContextResolver.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext("999998", "Synthetic Provider",
            Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_MANAGE,
                    PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE));
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<LogAction> audit;

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        request.setParameter("method", "create");
        request.setParameter("demographicNo", "123");
        request.setParameter("operationId", "operation-1");
        request.setParameter("urgency", "soon");
        request.setParameter("appointmentType", "follow_up");
        audit = mockStatic(LogAction.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(mock(LoggedInInfo.class));
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        when(resolver.resolveForPatient(any(), any(), eq(123))).thenReturn(staff);
        when(portal.findAccount(eq(123), same(staff))).thenReturn(account("active"));
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
        audit.close();
    }

    private void execute() throws Exception {
        new PortalBookingPrompt2Action(security, portal, resolver).execute();
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    private PatientPortalAccountDto account(String status) {
        return new PatientPortalAccountDto(3, "clinic-a", 123, status, false, false, null, null);
    }

    private PatientPortalBookingPromptDto prompt(int patient, String state) {
        Instant timestamp = Instant.parse("2026-10-01T12:00:00Z");
        return new PatientPortalBookingPromptDto(7, patient, "soon", "follow_up", null, state,
                "Synthetic Provider", timestamp, timestamp.plusSeconds(86400), null, null, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "DELETE", "post"})
    void shouldRejectUnsafeMethod_beforeAccessOrRemoteCalls(String method) throws Exception {
        request.setMethod(method);
        execute();
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(security, resolver);
        verify(portal, never()).findAccount(anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_portal.booking_prompt", "_portal.account"})
    void shouldRejectPatientSpecificDenial_beforeSending(String object) throws Exception {
        when(security.hasPrivilege(any(), eq(object), anyString(), eq("123"))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(resolver);
        verify(portal, never()).findAccount(anyInt(), any());
        verify(portal, never()).createBookingPrompt(anyInt(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "withdraw"})
    void shouldRefuseChange_whenBookingReadIsAllowedButWriteIsDenied(String method) throws Exception {
        request.setParameter("method", method);
        request.setParameter("promptId", "7");
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT),
                eq(SecurityInfoManager.WRITE), eq("123"))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(resolver, portal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "delete", "CREATE", "approve"})
    void shouldRejectUnsupportedMethod_beforeSending(String method) throws Exception {
        request.setParameter("method", method);
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("unsupported booking prompt action");
        verifyNoInteractions(resolver, portal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad/id", "an-operation-id-that-is-longer-than-sixty-four-characters-0000000"})
    void shouldRejectInvalidOperationId_beforeSending(String operationId) throws Exception {
        request.setParameter("operationId", operationId);
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(resolver, portal);
    }

    @Test
    void shouldRejectMissingOperationId_beforeSending() throws Exception {
        request.removeParameter("operationId");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(resolver, portal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-7", "seven", "99999999999999999999"})
    void shouldRejectUnselectedPrompt_beforeSending(String promptId) throws Exception {
        request.setParameter("method", "withdraw");
        request.setParameter("promptId", promptId);
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("a booking prompt must be selected");
        verifyNoInteractions(resolver, portal);
    }

    @Test
    void shouldRejectRestrictedPatient_beforeSending() throws Exception {
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(resolver);
    }

    @Test
    void shouldRejectInvalidVocabulary_beforeAccountLookup() throws Exception {
        request.setParameter("urgency", "invented");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verify(portal, never()).findAccount(anyInt(), any());
    }

    @Test
    void shouldRefuseInactiveAccount_withoutCreatingPrompt() throws Exception {
        when(portal.findAccount(eq(123), same(staff))).thenReturn(account("disabled"));
        execute();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("active portal account");
        verify(portal, never()).createBookingPrompt(anyInt(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void shouldAuditConfirmedIdentity_whenCreateOrRetrySucceeds(String createdText) throws Exception {
        boolean created = Boolean.parseBoolean(createdText);
        var expected = new PatientPortalBookingPromptRequest("operation-1", "soon", "follow_up", null);
        request.setParameter("suggestedBy", "untrusted browser provider");
        when(portal.createBookingPrompt(eq(123), eq(expected), same(staff)))
                .thenReturn(new PatientPortalBookingPromptDto.Creation(prompt(123, "sent"), created));
        execute();
        assertThat(response.getStatus()).isEqualTo(created ? 201 : 200);
        assertThat(response.getContentAsString()).contains("\"created\":" + created)
                .doesNotContain("untrusted browser provider");
        verify(resolver).resolveForPatient(any(), eq(Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                PortalStaffContextResolver.OBJECT_ACCOUNT)), eq(123));
        audit.verify(() -> LogAction.addLog(any(LoggedInInfo.class),
                eq(created ? "PortalBookingPrompt2Action.create" : "PortalBookingPrompt2Action.create.confirmed"),
                eq("PatientPortal"), eq("7"), eq("123"), eq(created ? "" : "retry")));
    }

    @Test
    void shouldAuditUnknownOutcome_whenMutationResponseIsLost() throws Exception {
        when(portal.createBookingPrompt(anyInt(), any(), any())).thenThrow(
                PatientPortalException.ofTransportFailure("/internal/carlos/patients/{id}/booking-prompts", null));
        execute();
        assertThat(response.getStatus()).isEqualTo(504);
        audit.verify(() -> LogAction.addLog(any(LoggedInInfo.class), eq("PortalBookingPrompt2Action.create.unconfirmed"),
                eq("PatientPortal"), eq("0"), eq("123"), eq("outcome=unconfirmed")));
    }

    @Test
    void shouldNotAuditMutation_whenAccountPrerequisiteFails() throws Exception {
        when(portal.findAccount(anyInt(), any())).thenThrow(
                PatientPortalException.ofTransportFailure("/internal/carlos/patients/{id}/portal-account", null));
        execute();
        assertThat(response.getStatus()).isEqualTo(504);
        audit.verifyNoInteractions();
    }

    @Test
    void shouldListWithReadPermission_whenWriteIsDenied() throws Exception {
        request.setParameter("method", "list");
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT),
                eq(SecurityInfoManager.WRITE), eq("123"))).thenReturn(false);
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of(prompt(123, "read")));
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        var prompts = new ObjectMapper().readTree(response.getContentAsString()).get("prompts");
        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0).get("id").asLong()).isEqualTo(7);
        assertThat(prompts.get(0).get("state").asText()).isEqualTo("read");
        verify(portal, never()).findAccount(anyInt(), any());
        verify(resolver).resolveForPatient(any(), eq(Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT)), eq(123));
    }

    @Test
    void shouldRefuseList_whenBookingReadIsDenied() throws Exception {
        request.setParameter("method", "list");
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT),
                eq(SecurityInfoManager.READ), eq("123"))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(resolver, portal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "disabled"})
    void shouldExposeEligibilityAndReadStatus_whenPanelIsRequested(String accountStatus) throws Exception {
        request.setParameter("method", "panel");
        when(portal.findAccount(eq(123), same(staff))).thenReturn(account(accountStatus));
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of(prompt(123, "read")));
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"accountActive\":" + "active".equals(accountStatus),
                "\"mayCreate\":true", "\"mayWithdraw\":true", "\"state\":\"read\"");
        verify(portal, never()).createBookingPrompt(anyInt(), any(), any());
        audit.verifyNoInteractions();
    }

    @Test
    void shouldReportInactiveAccount_whenPortalConfirmsAccountAbsent() throws Exception {
        request.setParameter("method", "panel");
        when(portal.findAccount(anyInt(), any())).thenThrow(PatientPortalException.ofStatus(
                404, "/internal/carlos/patients/{id}/portal-account", PatientPortalException.ACCOUNT_NOT_FOUND_DETAIL));
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of());
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"accountActive\":false");
        audit.verifyNoInteractions();
    }

    @Test
    void shouldRejectPanelRead_whenPatientBookingReadIsDenied() throws Exception {
        request.setParameter("method", "panel");
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT),
                eq(SecurityInfoManager.READ), eq("123"))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(resolver);
        verify(portal, never()).findAccount(anyInt(), any());
    }

    @Test
    void shouldOmitAccountLookup_whenPanelHasOnlyBookingRead() throws Exception {
        request.setParameter("method", "panel");
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_ACCOUNT),
                eq(SecurityInfoManager.READ), eq("123"))).thenReturn(false);
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT),
                eq(SecurityInfoManager.WRITE), eq("123"))).thenReturn(false);
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of());
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"mayCreate\":false", "\"mayWithdraw\":false")
                .doesNotContain("accountActive");
        verify(portal, never()).findAccount(anyInt(), any());
        verify(resolver).resolveForPatient(any(), eq(Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT)), eq(123));
    }

    @Test
    void shouldFailClosed_whenPanelAccountLookupIsUnavailable() throws Exception {
        request.setParameter("method", "panel");
        when(portal.findAccount(anyInt(), any())).thenThrow(
                PatientPortalException.ofTransportFailure("/internal/carlos/patients/{id}/portal-account", null));
        execute();
        assertThat(response.getStatus()).isEqualTo(504);
        verify(portal, never()).listBookingPrompts(anyInt(), any());
        audit.verifyNoInteractions();
    }

    @Test
    void shouldRefuseForeignPrompt_beforeWithdrawal() throws Exception {
        request.setParameter("method", "withdraw");
        request.setParameter("promptId", "7");
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of(prompt(456, "sent")));
        execute();
        assertThat(response.getStatus()).isEqualTo(404);
        verify(portal, never()).withdrawBookingPrompt(anyInt(), anyLong(), any());
    }

    @Test
    void shouldRefuseUnlistedPrompt_beforeWithdrawal() throws Exception {
        request.setParameter("method", "withdraw");
        request.setParameter("promptId", "7");
        Instant timestamp = Instant.parse("2026-10-01T12:00:00Z");
        var otherPrompt = new PatientPortalBookingPromptDto(8, 123, "soon", "follow_up", null, "sent",
                "Synthetic Provider", timestamp, timestamp.plusSeconds(86400), null, null, null, null);
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of(otherPrompt));
        execute();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("booking_prompt_not_verified");
        verify(portal, never()).withdrawBookingPrompt(anyInt(), anyLong(), any());
        audit.verifyNoInteractions();
    }

    @Test
    void shouldAuditUnknownOutcome_whenWithdrawalResponseIsLost() throws Exception {
        request.setParameter("method", "withdraw");
        request.setParameter("promptId", "7");
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of(prompt(123, "sent")));
        when(portal.withdrawBookingPrompt(eq(123), eq(7L), same(staff))).thenThrow(
                PatientPortalException.ofTransportFailure("/internal/carlos/booking-prompts/{id}/withdraw", null));
        execute();
        assertThat(response.getStatus()).isEqualTo(504);
        audit.verify(() -> LogAction.addLog(any(LoggedInInfo.class), eq("PortalBookingPrompt2Action.withdraw.unconfirmed"),
                eq("PatientPortal"), eq("0"), eq("123"), eq("outcome=unconfirmed")));
    }

    @Test
    void shouldWithdrawVerifiedPrompt_withPatientScopedIdentity() throws Exception {
        request.setParameter("method", "withdraw");
        request.setParameter("promptId", "7");
        when(portal.listBookingPrompts(eq(123), same(staff))).thenReturn(List.of(prompt(123, "sent")));
        when(portal.withdrawBookingPrompt(eq(123), eq(7L), same(staff))).thenReturn(prompt(123, "withdrawn"));
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(resolver).resolveForPatient(any(), eq(Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT)), eq(123));
        audit.verify(() -> LogAction.addLog(any(LoggedInInfo.class), eq("PortalBookingPrompt2Action.withdraw"),
                eq("PatientPortal"), eq("7"), eq("123"), eq("")));
    }
}
