/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@Tag("security")
@DisplayName("MsgDisplayDemographicMessages2Action")
class MsgDisplayDemographicMessages2ActionUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectUnlinkBeforeLoadingOrChangingMessages_whenRequestIsNotPost(String method) throws Exception {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        MessageTblDao messages = createAndRegisterMock(MessageTblDao.class);
        MsgDemoMapDao mappings = createAndRegisterMock(MsgDemoMapDao.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(login, "_msg", "r", null)).thenReturn(true);
        when(security.hasPrivilege(login, "_msg", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_demographic", "r", "101")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/messenger/DisplayDemographicMessages");
        request.setParameter("demographic_no", "101");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo("999998");
        request.getSession().setAttribute("msgSessionBean", bean);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            MsgDisplayDemographicMessages2Action action = new MsgDisplayDemographicMessages2Action();
            action.setMessageNo(new String[]{"100"});

            assertThat(action.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(messages, mappings);
        }
    }

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
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        when(securityInfoManager.hasPrivilege(login, "_msg", "r", "123")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(login, "_demographic", "r", "123")).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(login, 123)).thenReturn(true);
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
