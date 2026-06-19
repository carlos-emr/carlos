/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("MsgDisplayDemographicMessages2Action")
class MsgDisplayDemographicMessages2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should sanitize provider value when creating session bean debug log")
    void shouldSanitizeProviderValue_whenCreatingSessionBeanDebugLog() throws Exception {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demographic/messages");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("user", "999998\r\nforged-provider");
        request.addParameter("userName", "Unit Test");
        request.addParameter("demographic_no", "123");
        when(securityInfoManager.hasPrivilege(any(), eq("_msg"), eq("r"), isNull())).thenReturn(true);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class);
             LogCapture capture = LogCapture.forLogger(MsgDisplayDemographicMessages2Action.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            MsgDisplayDemographicMessages2Action action = new MsgDisplayDemographicMessages2Action();

            assertThat(action.execute()).isEqualTo("demoMsg");

            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("Created new MsgSessionBean"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("999998\\r\\nforged-provider");
        }
    }

    @Test
    @DisplayName("should omit demographic number when rejecting non numeric value")
    void shouldOmitDemographicNumber_whenRejectingNonNumericValue() throws Exception {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demographic/messages");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("user", "999998");
        request.addParameter("userName", "Unit Test");
        request.addParameter("demographic_no", "123\r\nforged-demo");
        when(securityInfoManager.hasPrivilege(any(), eq("_msg"), eq("r"), isNull())).thenReturn(true);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class);
             LogCapture capture = LogCapture.forLogger(MsgDisplayDemographicMessages2Action.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            MsgDisplayDemographicMessages2Action action = new MsgDisplayDemographicMessages2Action();

            assertThat(action.execute()).isEqualTo("error");

            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("Invalid non-numeric demographic_no"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).doesNotContain("123\r\nforged-demo", "123\\r\\nforged-demo", "forged-demo");
        }
    }
}
