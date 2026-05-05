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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ViewInsideLabUpload2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull()))
                .thenReturn(false);
        action = new ViewInsideLabUpload2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    void shouldReturnSuccess_whenGetRequest() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull()))
                .thenReturn(true);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldReturnSuccess_whenHeadRequest() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("HEAD");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldThrow_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("lab/CA/ALL/ViewInsideLabUpload");
    }

    @Test
    void shouldThrow_whenPrivilegeMissing() {
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("lab/CA/ALL/ViewInsideLabUpload");
    }

    @Test
    void shouldSend405_onPost() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("POST");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD");
    }
}
