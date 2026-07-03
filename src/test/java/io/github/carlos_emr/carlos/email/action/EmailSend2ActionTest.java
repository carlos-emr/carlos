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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action}: redirect safety and the HTTP-method
 * rejection contract for send dispatches (issue #3111). Registered in
 * {@code MutatorActionGetRejectionContractTest#CONDITIONAL_MUTATORS} — the
 * {@code cancel} dispatch is legitimate GET navigation, so this focused test
 * pins the mutation-intent paths (no {@code method} param, or
 * {@code method=sendDirectEmail}) rejecting GET/HEAD before any side effect.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action")
class EmailSend2ActionTest extends CarlosUnitTestBase {

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

    @Test
    @DisplayName("should encode fdid when cancel redirects to eForm")
    void shouldEncodeFdid_whenCancelRedirectsToEForm() {
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "123&parentAjaxId=evil#fragment%25 +/");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        String result = newAction().cancel();

        assertThat(result).isEqualTo("EFORM");
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/eform/efmshowform_data?fdid="
                        + "123%26parentAjaxId%3Devil%23fragment%2525%20%2B%2F&parentAjaxId=eforms");
    }

    /**
     * The GET/HEAD rejection contract for send dispatches: a crafted GET URL must
     * not send patient email, persist an {@code EmailLog}, or delete eForm data.
     */
    @Nested
    @DisplayName("HTTP-method rejection for send dispatches")
    class SendDispatchMethodRejection {

        @Test
        @DisplayName("should send 405 without side effects when GET has no method parameter")
        void shouldSend405WithoutSideEffects_whenGetHasNoMethodParameter() {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            request.setParameter("deleteEFormAfterEmail", "true");
            request.setParameter("fdid", "42");

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send 405 without side effects when GET requests sendDirectEmail")
        void shouldSend405WithoutSideEffects_whenGetRequestsSendDirectEmail() {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            request.setParameter("method", "sendDirectEmail");

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send 405 without side effects when HEAD carries send intent")
        void shouldSend405WithoutSideEffects_whenHeadCarriesSendIntent() {
            grantEmailWritePrivilege();
            request.setMethod("HEAD");

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should allow cancel navigation when GET method is cancel")
        void shouldAllowCancelNavigation_whenGetMethodIsCancel() {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            request.setParameter("method", "cancel");
            request.setParameter("transactionType", "EFORM");
            request.setParameter("fdid", "42");

            String result = newAction().execute();

            assertThat(result).isEqualTo("EFORM");
            assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send eForm email when POST has no method parameter")
        void shouldSendEFormEmail_whenPostHasNoMethodParameter() {
            grantEmailWritePrivilege();
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
    }
}
