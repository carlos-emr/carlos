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

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
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

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSessionKeys;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action}: redirect safety, explicit dispatch
 * allowlisting, and the POST-only contract (issue #3111). The action is registered in
 * {@code MutatorActionGetRejectionContractUnitTest#unconditionalMutators()}.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action")
class EmailSend2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    private SecurityInfoManager securityInfoManager;
    private EmailManager emailManager;
    private EformDataManager eformDataManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        emailManager = mock(EmailManager.class);
        eformDataManager = mock(EformDataManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(EformDataManager.class, eformDataManager);
        // EmailSend2Action reads request/response from ServletActionContext in field initializers
        // (evaluated at construction), so mock the static to keep `new EmailSend2Action()` from
        // NPEing before each test assigns action.request/response explicitly.
        servletActionContextMock = mockStatic(ServletActionContext.class);

        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    private EmailSend2Action newAction() {
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;
        return action;
    }

    private void grantEmailWritePrivilege() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_email"), eq("w"), nullable(String.class)))
            .thenReturn(true);
    }

    private void prepareValidUnencryptedMessage() {
        request.setParameter("message", "Appointment reminder");
        request.setParameter("isEmailEncrypted", "false");
    }

    @Test
    @DisplayName("should reject GET cancel without consuming session attachments")
    void shouldRejectGetCancel_withoutConsumingSessionAttachments() {
        grantEmailWritePrivilege();
        request.setMethod("GET");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "DIRECT");
        List<?> attachments = List.of("sentinel");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should clear session attachments when cancel arrives via POST")
    void shouldClearSessionAttachments_whenCancelArrivesViaPost() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "DIRECT");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, List.of());

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST)).isNull();
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should reject POST cancel when transaction type is missing")
    void shouldRejectPostCancel_whenTransactionTypeIsMissing() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        List<?> attachments = List.of("sentinel");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should reject POST cancel when transaction type is misspelled")
    void shouldRejectPostCancel_whenTransactionTypeIsMisspelled() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "EFROM");
        List<?> attachments = List.of("sentinel");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should encode fdid when cancel redirects to eForm")
    void shouldEncodeFdid_whenCancelRedirectsToEForm() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "123&parentAjaxId=evil#fragment%25 +/");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/eform/efmshowform_data?fdid="
                        + "123%26parentAjaxId%3Devil%23fragment%2525%20%2B%2F&parentAjaxId=eforms");
        verifyNoInteractions(emailManager, eformDataManager);
    }

    /**
     * The action-level method and dispatch contract: non-POST requests and unknown
     * dispatch values must not send patient email, persist an {@code EmailLog},
     * delete eForm data, or consume session-scoped attachments.
     */
    @Nested
    @DisplayName("HTTP method and dispatch rejection")
    class RequestRejection {

        @Test
        @DisplayName("should send 405 without side effects when GET has no method parameter")
        void shouldSend405WithoutSideEffects_whenGetHasNoMethodParameter() {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            request.setParameter("deleteEFormAfterEmail", "true");
            request.setParameter("fdid", "42");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send 405 without side effects when GET requests sendDirectEmail")
        void shouldSend405WithoutSideEffects_whenGetRequestsSendDirectEmail() {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            request.setParameter("method", "sendDirectEmail");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send 405 without side effects when HEAD carries send intent")
        void shouldSend405WithoutSideEffects_whenHeadCarriesSendIntent() {
            grantEmailWritePrivilege();
            request.setMethod("HEAD");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should still block mutation when 405 sendError hits a committed response")
        void shouldStillBlockMutation_whenSendErrorHitsCommittedResponse() throws Exception {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            // Per the Servlet spec, sendError throws IllegalStateException once the
            // response is committed (e.g. client abort); the rejection must still hold.
            HttpServletResponse committedResponse = mock(HttpServletResponse.class);
            doThrow(new IllegalStateException("Response already committed"))
                .when(committedResponse).sendError(anyInt(), anyString());
            EmailSend2Action action = newAction();
            action.response = committedResponse;

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should reject misspelled dispatch without consuming session attachments")
        void shouldRejectMisspelledDispatch_withoutConsumingSessionAttachments() {
            grantEmailWritePrivilege();
            request.setMethod("POST");
            request.setParameter("method", "cacnel");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send eForm email when POST has no method parameter")
        void shouldSendEFormEmail_whenPostHasNoMethodParameter() {
            grantEmailWritePrivilege();
            prepareValidUnencryptedMessage();
            request.setMethod("POST");
            EmailLog emailLog = new EmailLog();
            emailLog.setStatus(EmailStatus.SUCCESS);
            when(emailManager.sendEmail(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(emailLog);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(emailManager).sendEmail(any(LoggedInInfo.class), any(EmailData.class));
            assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        }

        @Test
        @DisplayName("should send eForm email when POST has method sendEFormEmail")
        void shouldSendEFormEmail_whenPostHasSendEFormEmailMethod() {
            grantEmailWritePrivilege();
            prepareValidUnencryptedMessage();
            request.setMethod("POST");
            request.setParameter("method", "sendEFormEmail");
            EmailLog emailLog = new EmailLog();
            emailLog.setStatus(EmailStatus.SUCCESS);
            when(emailManager.sendEmail(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(emailLog);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(emailManager).sendEmail(any(LoggedInInfo.class), any(EmailData.class));
            assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        }

        @Test
        @DisplayName("should send direct email when POST has method sendDirectEmail")
        void shouldSendDirectEmail_whenPostHasSendDirectEmailMethod() {
            grantEmailWritePrivilege();
            prepareValidUnencryptedMessage();
            request.setMethod("POST");
            request.setParameter("method", "sendDirectEmail");
            EmailLog emailLog = new EmailLog();
            emailLog.setStatus(EmailStatus.SUCCESS);
            when(emailManager.sendEmail(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(emailLog);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(emailManager).sendEmail(any(LoggedInInfo.class), any(EmailData.class));
            assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        }
    }
}
