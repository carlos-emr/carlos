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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.action;

import java.util.Collections;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action} redirect safety.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action")
class EmailSendOutcomeUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private SecurityInfoManager securityInfoManager;
    private EmailManager emailManager;
    private EformDataManager eformDataManager;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        emailManager = mock(EmailManager.class);
        eformDataManager = mock(EformDataManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(EformDataManager.class, eformDataManager);
        registerMock(io.github.carlos_emr.carlos.managers.EmailComposeManager.class,
                mock(io.github.carlos_emr.carlos.managers.EmailComposeManager.class));
        // EmailSend2Action reads request/response from ServletActionContext in field initializers
        // (evaluated at construction), so mock the static to keep `new EmailSend2Action()` from
        // NPEing before each test assigns action.request/response explicitly.
        servletActionContextMock = mockStatic(ServletActionContext.class);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should report transport acceptance even when persisted status is resolved")
    void shouldReportTransportAcceptance_whenPersistedStatusWasConcurrentlyResolved() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/emailSendAction");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("receiverEmailAddress", "patient@example.invalid");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("message", "Body");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "doNotAddAsNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        request.getSession().setAttribute("emailAttachmentList", Collections.emptyList());

        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(new Provider("999998"));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        EmailLog resolvedLog = new EmailLog();
        resolvedLog.setStatus(EmailStatus.RESOLVED);
        resolvedLog.setToEmail(new String[] {"patient@example.invalid"});

        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_email"), eq("w"), nullable(String.class)))
                .thenReturn(true);
        when(emailManager.sendEmailWithResult(eq(loggedInInfo), any(EmailData.class)))
                .thenReturn(EmailSendResult.accepted(resolvedLog, false));

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = new MockHttpServletResponse();

        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailStatusRecorded")).isEqualTo(false);
        assertThat(request.getAttribute("emailLog")).isSameAs(resolvedLog);
    }
    @Test
    @DisplayName("should render unconfirmed delivery without presenting it as a definite failure")
    void shouldReportUnconfirmedDelivery_withoutInvitingImmediateRetry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/emailSendAction");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("receiverEmailAddress", "patient@example.invalid");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("message", "Body");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "doNotAddAsNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        request.getSession().setAttribute("emailAttachmentList", Collections.emptyList());

        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(new Provider("999998"));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        EmailLog resolvedLog = new EmailLog();
        resolvedLog.setStatus(EmailStatus.PENDING);
        resolvedLog.setToEmail(new String[] {"patient@example.invalid"});

        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_email"), eq("w"), nullable(String.class)))
                .thenReturn(true);
        when(emailManager.sendEmailWithResult(eq(loggedInInfo), any(EmailData.class)))
                .thenReturn(EmailSendResult.unconfirmed(resolvedLog));

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = new MockHttpServletResponse();

        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(false);
        assertThat(request.getAttribute("isEmailDeliveryUnconfirmed")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailStatusRecorded")).isEqualTo(false);
        assertThat(request.getAttribute("emailLog")).isSameAs(resolvedLog);
    }
    @Test
    @DisplayName("should preserve acceptance and warn when eForm cleanup fails")
    void shouldWarnWithoutRetrying_whenEFormCleanupFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/emailSendAction");
        request.setParameter("method", "sendEFormEmail");
        request.setParameter("receiverEmailAddress", "patient@example.invalid");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("message", "Body");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "doNotAddAsNote");
        request.setParameter("transactionType", "EFORM");
        request.setParameter("deleteEFormAfterEmail", "true");
        request.setParameter("fdid", "42");
        request.setParameter("demographicId", "123");
        request.getSession().setAttribute("emailAttachmentList", Collections.emptyList());

        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(new Provider("999998"));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        EmailLog resolvedLog = new EmailLog();
        resolvedLog.setStatus(EmailStatus.SUCCESS);
        resolvedLog.setToEmail(new String[] {"patient@example.invalid"});

        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_email"), eq("w"), nullable(String.class)))
                .thenReturn(true);
        when(emailManager.sendEmailWithResult(eq(loggedInInfo), any(EmailData.class)))
                .thenReturn(EmailSendResult.accepted(resolvedLog, true));

        doThrow(new IllegalStateException("cleanup unavailable"))
                .when(eformDataManager).removeEFormData(loggedInInfo, "42");
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = new MockHttpServletResponse();

        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailStatusRecorded")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailFollowUpRequired")).isEqualTo(true);
        assertThat(request.getAttribute("emailLog")).isSameAs(resolvedLog);
    }
}
