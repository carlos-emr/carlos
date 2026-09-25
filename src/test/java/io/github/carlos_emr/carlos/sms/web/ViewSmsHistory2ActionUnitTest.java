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
package io.github.carlos_emr.carlos.sms.web;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.assembler.SmsHistoryViewModelAssembler;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.service.SmsMessageBodyReadService;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsHistoryViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
class ViewSmsHistory2ActionUnitTest {
    private static final int DEMOGRAPHIC_NO = 123;

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final SmsHistoryViewModelAssembler assembler = mock(SmsHistoryViewModelAssembler.class);
    private final SmsTransactionDao smsTransactionDao = mock(SmsTransactionDao.class);
    private final SmsMessageBodyReadService bodyReadService = mock(SmsMessageBodyReadService.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setParameter("demographic_no", String.valueOf(DEMOGRAPHIC_NO));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    @Test
    @DisplayName("history lists the patient's messages when the user may read SMS and the chart")
    void shouldListHistory_whenUserHasSmsAndDemographicRead() throws Exception {
        request.setMethod("GET");
        request.setParameter("page", "2");
        allowPatientAccess();
        SmsHistoryViewModel model = emptyModel();
        when(assembler.assemble(loggedInInfo, DEMOGRAPHIC_NO, 2)).thenReturn(model);

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("smsHistory")).isSameAs(model);
    }

    @Test
    @DisplayName("history is denied without _sms read for the patient")
    void shouldDenyHistory_withoutSmsRead() {
        request.setMethod("GET");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_sms", "r", DEMOGRAPHIC_NO)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", DEMOGRAPHIC_NO)).thenReturn(true);

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_sms)");
        verifyNoInteractions(assembler);
    }

    @Test
    @DisplayName("history is denied without _demographic read for the patient")
    void shouldDenyHistory_withoutDemographicRead() {
        request.setMethod("GET");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_sms", "r", DEMOGRAPHIC_NO)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", DEMOGRAPHIC_NO)).thenReturn(false);

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
        verifyNoInteractions(assembler);
    }

    @Test
    @DisplayName("history rejects a non-numeric demographic number before any privilege or data lookup")
    void shouldRejectHistory_whenDemographicNoIsNotNumeric() throws Exception {
        request.setMethod("GET");
        request.setParameter("demographic_no", "12 OR 1=1");

        String result = action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(securityInfoManager, assembler);
    }

    @Test
    @DisplayName("history is denied without a logged-in session")
    void shouldDenyHistory_withoutSession() {
        request.setMethod("GET");
        request.getSession().invalidate();

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required session");
        verifyNoInteractions(securityInfoManager, assembler);
    }

    @Test
    @DisplayName("showMessage rejects GET with 405 before reading or auditing anything")
    void shouldRejectShowMessage_whenGet() throws Exception {
        request.setMethod("GET");
        showMessageRequest("11", "CARE_REVIEW");

        String result = action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, smsTransactionDao, bodyReadService);
    }

    @Test
    @DisplayName("showMessage reads the body through the audited service with the chosen reason")
    void shouldReadBody_throughAuditedService() throws Exception {
        request.setMethod("POST");
        showMessageRequest("11", "DELIVERY_REVIEW");
        allowPatientAccess();
        SmsTransaction transaction = outboundFor(DEMOGRAPHIC_NO);
        when(smsTransactionDao.find(11L)).thenReturn(transaction);
        when(bodyReadService.readFullMessageBody(transaction, loggedInInfo, "DELIVERY_REVIEW"))
                .thenReturn(Optional.of("Appointment reminder"));

        String result = action().execute();

        assertThat(result).isEqualTo("message");
        assertThat(request.getAttribute("smsMessageBody")).isEqualTo("Appointment reminder");
        verify(bodyReadService).readFullMessageBody(transaction, loggedInInfo, "DELIVERY_REVIEW");
    }

    @Test
    @DisplayName("showMessage refuses a message that belongs to another patient, without reading it")
    void shouldRefuseBody_whenMessageBelongsToAnotherPatient() throws Exception {
        request.setMethod("POST");
        showMessageRequest("11", "CARE_REVIEW");
        allowPatientAccess();
        when(smsTransactionDao.find(11L)).thenReturn(outboundFor(999));

        String result = action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(bodyReadService);
    }

    @Test
    @DisplayName("showMessage refuses a reason that is not on the allowed list")
    void shouldRefuseBody_whenReasonIsNotAllowed() throws Exception {
        request.setMethod("POST");
        showMessageRequest("11", "CURIOSITY");
        allowPatientAccess();

        String result = action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(smsTransactionDao, bodyReadService);
    }

    @Test
    @DisplayName("showMessage does not read or audit when no text is stored for the message")
    void shouldNotReadBody_whenNoTextIsStored() throws Exception {
        request.setMethod("POST");
        showMessageRequest("11", "CARE_REVIEW");
        allowPatientAccess();
        SmsTransaction blocked = outboundFor(DEMOGRAPHIC_NO);
        blocked.markConsentBlocked(SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", "No SMS consent is recorded for this patient."));
        when(smsTransactionDao.find(11L)).thenReturn(blocked);

        String result = action().execute();

        assertThat(result).isEqualTo("message");
        assertThat(request.getAttribute("smsMessageNotStored")).isEqualTo(Boolean.TRUE);
        verifyNoInteractions(bodyReadService);
    }

    @Test
    @DisplayName("showMessage shows a denial, not the text, when the user lacks _msgSMS")
    void shouldShowDenial_whenBodyReadIsDenied() throws Exception {
        request.setMethod("POST");
        showMessageRequest("11", "CARE_REVIEW");
        allowPatientAccess();
        SmsTransaction transaction = outboundFor(DEMOGRAPHIC_NO);
        when(smsTransactionDao.find(11L)).thenReturn(transaction);
        when(bodyReadService.readFullMessageBody(transaction, loggedInInfo, "CARE_REVIEW"))
                .thenThrow(new AccessDeniedException("_msgSMS", "r", DEMOGRAPHIC_NO));

        String result = action().execute();

        assertThat(result).isEqualTo("message");
        // Rendered with 200: CARLOS's ResponseSanitizationFilter blanks a JSP body rendered under a 4xx status.
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute("smsMessageDenied")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("smsMessageBody")).isNull();
    }

    @Test
    @DisplayName("showMessage is denied without _sms read for the patient, before loading the message")
    void shouldDenyShowMessage_withoutSmsRead() {
        request.setMethod("POST");
        showMessageRequest("11", "CARE_REVIEW");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(smsTransactionDao, bodyReadService);
        verify(bodyReadService, never()).readFullMessageBody(any(), any(), any());
    }

    private ViewSmsHistory2Action action() {
        return new ViewSmsHistory2Action(securityInfoManager, assembler, smsTransactionDao, bodyReadService);
    }

    private void allowPatientAccess() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_sms", "r", DEMOGRAPHIC_NO)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", DEMOGRAPHIC_NO)).thenReturn(true);
    }

    private void showMessageRequest(String smsTransactionId, String reason) {
        request.setParameter("method", "showMessage");
        request.setParameter("smsTransactionId", smsTransactionId);
        request.setParameter("reason", reason);
    }

    private static SmsTransaction outboundFor(int demographicNo) {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(demographicNo, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        ReflectionTestUtils.setField(transaction, "id", 11L);
        return transaction;
    }

    private static SmsHistoryViewModel emptyModel() {
        return new SmsHistoryViewModel("123", "SMSTEST, CONSENT", List.of(), 1, 1, 0L, false, false, false);
    }
}
