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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentStaging;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action}: redirect safety, and sending only the attachments the
 * window's own compose staged for the email's patient (#4425).
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action")
class EmailSend2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private EmailManager emailManager;
    private EformDataManager eformDataManager;
    private AttachmentOwnershipService ownership;
    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void setUp() {
        emailManager = mock(EmailManager.class);
        eformDataManager = mock(EformDataManager.class);
        ownership = mock(AttachmentOwnershipService.class);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(EmailManager.class, emailManager);
        registerMock(EformDataManager.class, eformDataManager);
        registerMock(AttachmentOwnershipService.class, ownership);
        EmailLog sent = new EmailLog();
        sent.setStatus(EmailStatus.SUCCESS);
        when(emailManager.sendEmail(any(), any())).thenReturn(sent);
        when(ownership.allBelongToDemographic(anyMap(), any())).thenReturn(true);
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
    @DisplayName("should encode fdid when cancel redirects to eForm")
    void shouldEncodeFdid_whenCancelRedirectsToEForm() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "123&parentAjaxId=evil#fragment%25 +/");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        String result = action.cancel();

        assertThat(result).isEqualTo("EFORM");
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/eform/efmshowform_data?fdid="
                        + "123%26parentAjaxId%3Devil%23fragment%2525%20%2B%2F&parentAjaxId=eforms");
    }

    private static EmailAttachment attachment(DocumentType type, int id) {
        return new EmailAttachment("FAKE-" + type + "-" + id + ".pdf", "/tmp/FAKE-" + id + ".pdf", type, id, 10L);
    }

    /** The send the compose page posts, from the window holding {@code key}, for {@code patient}. */
    private EmailSend2Action sendFrom(String key, String patient) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/emailSendAction");
        request.setSession(session);
        LoggedInInfo.setLoggedInInfoIntoSession(session, new LoggedInInfo());
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "20001");
        request.setParameter("deleteEFormAfterEmail", "true");
        request.setParameter("receiverEmailAddress", "fake-" + patient + "@example.com");
        if (patient != null) {
            request.setParameter("demographicId", patient);
        }
        if (key != null) {
            request.setParameter(EmailAttachmentStaging.KEY_PARAMETER, key);
        }
        EmailSend2Action action = new EmailSend2Action(ownership);
        action.request = request;
        action.response = new MockHttpServletResponse();
        return action;
    }

    private EmailData sentEmail() {
        ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
        verify(emailManager).sendEmail(any(), captor.capture());
        return captor.getValue();
    }

    private void assertRefused(EmailSend2Action action, String result) {
        assertThat(result).isEqualTo("success");
        assertThat(action.request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat(action.request.getAttribute("emailErrorMessage")).isEqualTo(EmailSend2Action.ATTACHMENTS_REFUSED_MESSAGE);
        assertThat(action.request.getAttribute("isEmailSuccessful")).isNull();
        verify(emailManager, never()).sendEmail(any(), any());
        verifyNoInteractions(eformDataManager);
    }

    @Test
    @DisplayName("should send X's own eForm from X's window after Y's window composed")
    void shouldSendOwnWindowsAttachments_whenAnotherPatientsWindowComposedLater() {
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of(attachment(DocumentType.EFORM, 501))).key();
        EmailAttachmentStaging.stage(session, 10002, List.of(attachment(DocumentType.EFORM, 502)));

        String result = sendFrom(windowX, "10001").sendEFormEmail();

        assertThat(result).isEqualTo("success");
        EmailData sent = sentEmail();
        assertThat(sent.getDemographicNo()).isEqualTo(10001);
        assertThat(sent.getAttachments()).extracting(EmailAttachment::getDocumentId).containsExactly(501);
        verify(ownership).allBelongToDemographic(Map.of(DocumentType.EFORM, List.of(501)), 10001);
        verify(eformDataManager).removeEFormData(any(), eq("20001"));
    }

    @Test
    @DisplayName("should refuse a send without a key, with an unknown key, or with a key already used")
    void shouldRefuseSend_whenKeyMissingUnknownOrReused() {
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of(attachment(DocumentType.EFORM, 501))).key();
        assertThat(EmailAttachmentStaging.take(session, windowX)).isNotNull();

        for (String key : new String[]{null, "not-a-key", "AAAAAAAAAAAAAAAAAAAAAA", windowX}) {
            EmailSend2Action action = sendFrom(key, "10001");
            assertRefused(action, action.sendEFormEmail());
        }
    }

    @Test
    @DisplayName("should refuse, and use up, a window's attachments posted for another patient")
    void shouldRefuseSend_whenDemographicIsNotThePreparedPatient() {
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of(attachment(DocumentType.EFORM, 501))).key();

        for (String patient : new String[]{"10002", null, "", "FAKE"}) {
            String key = patient == null ? windowX
                    : EmailAttachmentStaging.stage(session, 10001, List.of(attachment(DocumentType.EFORM, 501))).key();
            EmailSend2Action action = sendFrom(key, patient);
            assertRefused(action, action.sendDirectEmail());
            assertThat(EmailAttachmentStaging.take(session, key)).as("a refused entry cannot be retried").isNull();
        }
        verifyNoInteractions(ownership);
    }

    @Test
    @DisplayName("should refuse a send whose attachment record does not belong to the email's patient")
    void shouldRefuseSend_whenAttachmentBelongsToAnotherPatient() {
        when(ownership.allBelongToDemographic(anyMap(), eq(10001))).thenReturn(false);
        String windowX = EmailAttachmentStaging.stage(session, 10001,
                List.of(attachment(DocumentType.EFORM, 501), attachment(DocumentType.DOC, 601))).key();

        EmailSend2Action action = sendFrom(windowX, "10001");

        assertRefused(action, action.sendEFormEmail());
    }

    @Test
    @DisplayName("should verify eForms, documents, labs and HRM reports by type, leaving forms rendered for the patient")
    void shouldVerifyEveryOwnedType_exceptPatientRenderedForms() {
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of(
                attachment(DocumentType.EFORM, 501), attachment(DocumentType.DOC, 601), attachment(DocumentType.DOC, 602),
                attachment(DocumentType.LAB, 701), attachment(DocumentType.HRM, 801), attachment(DocumentType.FORM, 901))).key();

        sendFrom(windowX, "10001").sendDirectEmail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<DocumentType, Collection<Integer>>> checked = ArgumentCaptor.forClass(Map.class);
        verify(ownership).allBelongToDemographic(checked.capture(), eq(10001));
        assertThat(checked.getValue()).containsOnlyKeys(DocumentType.EFORM, DocumentType.DOC, DocumentType.LAB, DocumentType.HRM);
        assertThat(checked.getValue().get(DocumentType.DOC)).containsExactly(601, 602);
        assertThat(sentEmail().getAttachments()).hasSize(6);
    }

    @Test
    @DisplayName("should refuse a staged attachment that has no document type")
    void shouldRefuseSend_whenAttachmentHasNoType() {
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of(attachment(null, 501))).key();

        EmailSend2Action action = sendFrom(windowX, "10001");

        assertRefused(action, action.sendEFormEmail());
    }

    @Test
    @DisplayName("should send an email without attachments when its window staged none")
    void shouldSendWithoutAttachments_whenWindowStagedNone() {
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of()).key();

        sendFrom(windowX, "10001").sendDirectEmail();

        assertThat(sentEmail().getAttachments()).isEmpty();
    }

    @Test
    @DisplayName("should discard only the cancelling window's attachments")
    void shouldDiscardOwnAttachments_whenCancelled() {
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of(attachment(DocumentType.EFORM, 501))).key();
        String windowY = EmailAttachmentStaging.stage(session, 10002, List.of(attachment(DocumentType.EFORM, 502))).key();

        sendFrom(windowX, "10001").cancel();

        assertThat(EmailAttachmentStaging.take(session, windowX)).isNull();
        assertThat(EmailAttachmentStaging.take(session, windowY).demographicNo()).isEqualTo(10002);
        verify(emailManager, never()).sendEmail(any(), any());
    }
}
