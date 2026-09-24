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

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthException;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthValidator;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action} redirect safety and over-length field rejection.
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

    @BeforeEach
    void setUp() {
        emailManager = mock(EmailManager.class);
        eformDataManager = mock(EformDataManager.class);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(EmailManager.class, emailManager);
        registerMock(EformDataManager.class, eformDataManager);
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

    private EmailSend2Action overLengthAction(MockHttpServletRequest request) {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        List<EmailFieldLengthValidator.Violation> violations = EmailFieldLengthValidator.validate(overLengthSubject());
        when(emailManager.sendEmail(any(), any())).thenThrow(new EmailFieldLengthException(violations));
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = new MockHttpServletResponse();
        return action;
    }

    private static EmailData overLengthSubject() {
        EmailData data = new EmailData();
        data.setSubject("s".repeat(EmailFieldLengthValidator.MAX_SUBJECT_CHARS + 1));
        return data;
    }

    @Test
    @DisplayName("should show length errors and keep the eForm when a field is too long")
    void shouldRejectWithoutDeletingEForm_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "42");
        request.setParameter("deleteEFormAfterEmail", "true");
        request.setParameter("openEFormAfterEmail", "true");
        EmailSend2Action action = overLengthAction(request);

        String result = action.sendEFormEmail();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(false);
        assertThat(request.getAttribute("fdid")).isEqualTo("42");
        assertThat(request.getAttribute("isOpenEForm")).isEqualTo("true");
        assertThat((List<?>) request.getAttribute("emailLengthViolations"))
                .extracting("messageKey")
                .containsExactly("email.compose.msg.subjectTooLong");
        verifyNoInteractions(eformDataManager);
    }

    @Test
    @DisplayName("should show length errors for a direct email when a field is too long")
    void shouldRejectDirectEmail_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "DIRECT");
        EmailSend2Action action = overLengthAction(request);

        String result = action.sendDirectEmail();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(false);
        assertThat(request.getAttribute("emailLengthViolations")).isNotNull();
        assertThat(request.getAttribute("emailLog")).isNull();
    }
}
