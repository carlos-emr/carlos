/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prevention.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("Ontario prevention report action")
class PreventionReport2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/prevention/PreventionReport");
        MockHttpServletResponse response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null))
                .thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void closeServletActionContextMock() {
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should render the report form when opened without selections")
    void shouldRenderForm_whenOpenedWithoutSelections() {
        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should render the report form when placeholder selections are submitted")
    void shouldRenderForm_whenPlaceholderSelectionsSubmitted() {
        request.addParameter("patientSet", "-1");
        request.addParameter("prevention", "-1");

        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should render the report form when the saved-query id is malformed")
    void shouldRenderForm_whenPatientSetIdMalformed() {
        request.addParameter("patientSet", "not-a-query-id");
        request.addParameter("prevention", "Flu");

        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should render the report form when the report type is unknown")
    void shouldRenderForm_whenReportTypeUnknown() {
        request.addParameter("patientSet", "1");
        request.addParameter("prevention", "not-a-report");

        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should preserve the report privilege requirement")
    void shouldRejectRequest_whenReportPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null))
                .thenReturn(false);

        PreventionReport2Action action = new PreventionReport2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_report");
    }
}
