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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
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
class EmailSend2ActionTest extends CarlosUnitTestBase {

    private static final String ENCRYPTED_BODY_NOTICE_KEY = "email.compose.msg.encryptedBodyNotice";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private EmailManager emailManager;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        emailManager = mock(EmailManager.class);
        registerMock(EmailManager.class, emailManager);
        registerMock(EformDataManager.class, mock(EformDataManager.class));
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
        EmailSend2Action action = spy(new EmailSend2Action());
        // cancel() builds EmailData via prepareEmailFields, which resolves the encrypted-body notice
        // (encryption now fails closed by default). getText() has no live Struts container here.
        doReturn("SECURE_NOTICE").when(action).getText(eq(ENCRYPTED_BODY_NOTICE_KEY));
        action.request = request;
        action.response = response;

        String result = action.cancel();

        assertThat(result).isEqualTo("EFORM");
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/eform/efmshowform_data?fdid="
                        + "123%26parentAjaxId%3Devil%23fragment%2525%20%2B%2F&parentAjaxId=eforms");
    }

    @Test
    @DisplayName("should route the single message into the encrypted PDF when encryption is on")
    void shouldRouteMessageToEncryptedPdf_whenEncryptionOn() {
        EmailData sent = captureSentEmail("Confidential lab result for the patient.", "true");

        // The clinical content goes into the encrypted-PDF channel; the visible cleartext body
        // is only the fixed, PHI-free notice.
        assertThat(sent.getEncryptedMessage()).isEqualTo("Confidential lab result for the patient.");
        assertThat(sent.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(sent.getIsEncrypted()).isTrue();
    }

    @Test
    @DisplayName("should route the single message into the cleartext body when encryption is off")
    void shouldRouteMessageToCleartextBody_whenEncryptionOff() {
        EmailData sent = captureSentEmail("A non-clinical reminder.", "false");

        // The message is sent as the cleartext body; the encrypted-PDF channel stays empty so the
        // client can never populate both at once.
        assertThat(sent.getBody()).isEqualTo("A non-clinical reminder.");
        assertThat(sent.getEncryptedMessage()).isEmpty();
        assertThat(sent.getIsEncrypted()).isFalse();
    }

    @Test
    @DisplayName("should default the body to empty when the message param is missing")
    void shouldDefaultBodyToEmpty_whenMessageParamMissing() {
        // A direct POST that omits the message param must not push null into the body/PDF channels.
        EmailData sent = captureSentEmail(null, "false");

        assertThat(sent.getBody()).isEmpty();
        assertThat(sent.getEncryptedMessage()).isEmpty();
    }

    @Test
    @DisplayName("should encrypt by default when the encryption flag is missing")
    void shouldEncryptByDefault_whenEncryptionFlagMissing() {
        // Fail closed: a direct/malformed POST that omits the isEmailEncrypted toggle must route the
        // message into the encrypted-PDF channel, never the cleartext body.
        EmailData sent = captureSentEmail("Confidential note.", null);

        assertThat(sent.getEncryptedMessage()).isEqualTo("Confidential note.");
        assertThat(sent.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(sent.getIsEncrypted()).isTrue();
    }

    /**
     * Drives sendDirectEmail() with the given single "message" field and encryption flag, and
     * returns the EmailData the action handed to EmailManager so routing can be asserted. A null
     * {@code message} or {@code isEmailEncrypted} omits that parameter entirely, mirroring a direct
     * POST that leaves it out.
     */
    private EmailData captureSentEmail(String message, String isEmailEncrypted) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (message != null) {
            request.setParameter("message", message);
        }
        if (isEmailEncrypted != null) {
            request.setParameter("isEmailEncrypted", isEmailEncrypted);
        }
        request.setParameter("senderConfigId", "1");
        request.setParameter("demographicId", "42");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.SUCCESS);
        ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
        when(emailManager.sendEmail(any(LoggedInInfo.class), captor.capture())).thenReturn(emailLog);

        EmailSend2Action action = spy(new EmailSend2Action());
        // getText() has no live Struts container in a unit test, so stub the notice lookup.
        doReturn("SECURE_NOTICE").when(action).getText(eq(ENCRYPTED_BODY_NOTICE_KEY));
        action.request = request;
        action.response = new MockHttpServletResponse();

        action.sendDirectEmail();

        return captor.getValue();
    }
}
