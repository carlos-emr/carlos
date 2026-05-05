/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewInsideLabUpload2Action}, the GET-only view gate
 * for the HL7 lab upload page (admin &gt; Labs &gt; HL7 Lab upload). Verifies
 * privilege enforcement and HTTP method gating mirroring the pattern used by
 * other lab/share gate actions.
 *
 * @since 2026-05-03
 */
@DisplayName("ViewInsideLabUpload2Action Unit Tests")
@Tag("unit")
@Tag("gate")
@Tag("lab")
class ViewInsideLabUpload2ActionTest extends CarlosUnitTestBase {

    private static final String VIEW_ROUTE = "lab/CA/ALL/ViewInsideLabUpload";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager mockSecurityInfoManager;
    private LoggedInInfo mockLoggedInInfo;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ViewInsideLabUpload2Action action;

    @BeforeEach
    void setUp() {
        mockSecurityInfoManager = mock(SecurityInfoManager.class);
        mockLoggedInInfo = mock(LoggedInInfo.class);
        mockRequest = new MockHttpServletRequest("GET", "/" + VIEW_ROUTE);
        mockResponse = new MockHttpServletResponse();
        stubServletActionContext();
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        stubLoggedInInfo(mockLoggedInInfo);
        stubLabWritePrivilege(false);
        action = new ViewInsideLabUpload2Action(mockSecurityInfoManager);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldReturnSuccess_whenSafeMethodRequest(String method) throws Exception {
        stubLabWritePrivilege(true);
        mockRequest.setMethod(method);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldThrow_whenSessionMissing() {
        stubLoggedInInfo(null);

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining(VIEW_ROUTE);
    }

    @Test
    void shouldThrow_whenPrivilegeMissing() {
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining(VIEW_ROUTE);
    }

    @Test
    void shouldSend405_onPost() throws Exception {
        stubLabWritePrivilege(true);
        mockRequest.setMethod("POST");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD");
    }

    private void stubServletActionContext() {
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
    }

    private void stubLoggedInInfo(LoggedInInfo loggedInInfo) {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    private void stubLabWritePrivilege(boolean allowed) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull()))
                .thenReturn(allowed);
    }
}
