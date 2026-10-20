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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Manage Emails resend stages its attachments under its own window's patient-bound key (#4425). */
@Tag("unit")
@Tag("security")
@Tag("email")
@DisplayName("ManageEmails2Action resend")
class ManageEmails2ActionUnitTest extends CarlosUnitTestBase {

    private EmailComposeManager emailComposeManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private MockedStatic<ServletActionContext> servletActionContext;
    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void setUp() {
        emailComposeManager = mock(EmailComposeManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EmailManager.class, mock(EmailManager.class));
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
