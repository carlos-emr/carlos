/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that non-POST HTTP methods cannot reach either email mutation route.
 *
 * @since 2026-08-24
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email send HTTP method guard")
class EmailSend2ActionRequestMethodUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest(name = "{0} is rejected")
    @ValueSource(strings = {"GET", "HEAD", "PUT", "PATCH", "DELETE"})
    @DisplayName("should reject non-POST methods before sending email")
    void shouldRejectNonPostMethod_beforeSendingEmail(String httpMethod) {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EmailManager emailManager = mock(EmailManager.class);
        EformDataManager eformDataManager = mock(EformDataManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(EformDataManager.class, eformDataManager);
        when(securityInfoManager.hasPrivilege(any(), eq("_email"), eq("w"), isNull(String.class)))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/email/send");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailSend2Action action = new EmailSend2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
        }

        verify(securityInfoManager).hasPrivilege(any(), eq("_email"), eq("w"), isNull(String.class));
        verifyNoInteractions(emailManager, eformDataManager);
    }
}
