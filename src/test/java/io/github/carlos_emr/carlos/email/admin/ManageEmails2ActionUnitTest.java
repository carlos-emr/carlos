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
package io.github.carlos_emr.carlos.email.admin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentStaging;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Manage Emails dispatches status updates and stages patient-bound resend attachments. */
@Tag("unit")
@Tag("security")
@Tag("email")
@DisplayName("ManageEmails2Action")
class ManageEmails2ActionUnitTest extends CarlosUnitTestBase {

    private EmailComposeManager emailComposeManager;
    private EmailManager emailManager;
    private SecurityInfoManager securityInfoManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private MockedStatic<ServletActionContext> servletActionContext;
    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void setUp() {
        emailComposeManager = mock(EmailComposeManager.class);
        emailManager = mock(EmailManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(FormsManager.class, mock(FormsManager.class));
        registerMock(PdfPreviewCapabilityService.class, mock(PdfPreviewCapabilityService.class));
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "Yes"});
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("FAKE Patient");
        servletActionContext = mockStatic(ServletActionContext.class);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    private static EmailLog loggedEmail(int demographicNo, int fdid) {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(demographicNo);
        EmailLog emailLog = new EmailLog();
        emailLog.setDemographic(demographic);
        emailLog.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        emailLog.setBody("FAKE body");
        emailLog.setEncryptedMessage("");
        emailLog.setEmailAttachments(new ArrayList<>(List.of(
                new EmailAttachment(emailLog, "FAKE-eform.pdf", "/tmp/old.pdf", DocumentType.EFORM, fdid))));
        return emailLog;
    }

    private MockHttpServletRequest resend(int logId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/ManageEmails");
        request.setSession(session);
        LoggedInInfo.setLoggedInInfoIntoSession(session, new LoggedInInfo());
        request.setParameter("method", "resendEmail");
        request.setParameter("logId", String.valueOf(logId));
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        String result = new ManageEmails2Action().execute();
        assertThat(result).isEqualTo("compose");
        return request;
    }

    @Test
    @DisplayName("should dispatch the Resolve POST to the status update instead of rendering the manager")
    void shouldResolveEmail_whenDispatchedFromExecute() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        EmailLog resolved = new EmailLog();
        resolved.setStatus(EmailLog.EmailStatus.RESOLVED);
        when(emailManager.updateEmailStatus(loggedInInfo, 7003, EmailLog.EmailStatus.RESOLVED, null))
                .thenReturn(resolved);
        MockHttpServletResponse response = resolve("7003", loggedInInfo);

        verify(emailManager).updateEmailStatus(loggedInInfo, 7003, EmailLog.EmailStatus.RESOLVED, null);
        verifyNoInteractions(emailComposeManager);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should return a JSON not-found error for a missing email log")
    void shouldRejectResolve_whenLogDoesNotExist() throws Exception {
        MockHttpServletResponse response = resolve("7004", new LoggedInInfo());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("Email log not found");
        verifyNoInteractions(emailComposeManager);
    }

    @Test
    @DisplayName("should report a conflict when the email cannot transition to resolved")
    void shouldRejectResolve_whenEmailIsNotFailed() throws Exception {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        EmailLog sent = new EmailLog();
        sent.setStatus(EmailLog.EmailStatus.SUCCESS);
        when(emailManager.updateEmailStatus(loggedInInfo, 7003, EmailLog.EmailStatus.RESOLVED, null)).thenReturn(sent);

        MockHttpServletResponse response = resolve("7003", loggedInInfo);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("Only failed emails can be resolved");
    }

    @Test
    @DisplayName("should check write privilege in the action before dispatching any update")
    void shouldDenyResolve_whenActionWritePrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> resolve("7003", new LoggedInInfo()))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_email)");
        verifyNoInteractions(emailManager);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "7003 OR 1=1", "2147483648"})
    @DisplayName("should reject invalid Resolve IDs without updating an email")
    void shouldRejectResolve_whenLogIdIsInvalid(String logId) throws Exception {
        MockHttpServletResponse response = resolve(logId, new LoggedInInfo());

        verifyNoInteractions(emailManager);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("Invalid email log id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("should refuse Resolve outside a CSRF-protected POST")
    void shouldRejectResolve_whenMethodIsNotPost(String method) {
        MockHttpServletResponse response = resolve(method, "7003", new LoggedInInfo());

        verifyNoInteractions(emailManager);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should propagate the manager's privilege refusal instead of reporting success")
    void shouldRejectResolve_whenManagerDeniesWritePrivilege() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        doThrow(new SecurityException("missing required sec object (_email)"))
                .when(emailManager).updateEmailStatus(loggedInInfo, 7003, EmailLog.EmailStatus.RESOLVED, null);

        assertThatThrownBy(() -> resolve("7003", loggedInInfo)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(emailComposeManager);
    }

    private MockHttpServletResponse resolve(String logId, LoggedInInfo loggedInInfo) {
        return resolve("POST", logId, loggedInInfo);
    }

    private MockHttpServletResponse resolve(String method, String logId, LoggedInInfo loggedInInfo) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/admin/ManageEmails");
        request.setSession(session);
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        request.setParameter("method", "setResolved");
        if (logId != null) {
            request.setParameter("logId", logId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        assertThat(new ManageEmails2Action().execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        return response;
    }

    @Test
    @DisplayName("should stage the resend's refreshed attachments under its own key, bound to the logged patient")
    void shouldStageRefreshedAttachments_boundToLoggedPatient() throws Exception {
        when(emailComposeManager.prepareEmailForResend(any(), eq(7001))).thenReturn(loggedEmail(10001, 501));
        when(documentAttachmentManager.renderDocument(any(), eq(DocumentType.EFORM), eq(501)))
                .thenReturn(Path.of("/tmp/FAKE-refreshed.pdf"));
        session.setAttribute("emailAttachmentList", List.of());

        MockHttpServletRequest request = resend(7001);

        String key = (String) request.getAttribute(EmailAttachmentStaging.KEY_ATTRIBUTE);
        assertThat(EmailAttachmentStaging.isKey(key)).isTrue();
        assertThat(session.getAttribute("emailAttachmentList")).as("no session-wide list").isNull();
        EmailAttachmentStaging.Prepared prepared = EmailAttachmentStaging.take(session, key);
        assertThat(prepared.demographicNo()).isEqualTo(10001);
        assertThat(prepared.attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getDocumentId()).isEqualTo(501);
            assertThat(attachment.getFilePath()).isEqualTo("/tmp/FAKE-refreshed.pdf");
            assertThat(attachment.getEmailLog()).as("detached from the persistent log").isNull();
        });
    }

    @Test
    @DisplayName("should stage nothing when the resend's attachments cannot be refreshed")
    void shouldStageNothing_whenRefreshFails() throws Exception {
        when(emailComposeManager.prepareEmailForResend(any(), eq(7002))).thenReturn(loggedEmail(10001, 502));
        when(documentAttachmentManager.renderDocument(any(), eq(DocumentType.EFORM), eq(502)))
                .thenThrow(new PDFGenerationException("FAKE render failure"));

        MockHttpServletRequest request = resend(7002);

        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat(request.getAttribute(EmailAttachmentStaging.KEY_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute("emailAttachmentList")).isNull();
        assertThat(Collections.list(session.getAttributeNames()))
                .noneMatch(name -> name.startsWith(EmailAttachmentStaging.class.getName()));
    }
}
