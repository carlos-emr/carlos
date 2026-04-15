/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.administration.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ViewAdministrationIndex2Action Unit Tests")
@Tag("unit")
@Tag("gate")
class ViewAdministrationIndex2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ViewAdministrationIndex2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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
        // Default: no privileges granted. Tests opt into specific ones.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), eq("r"), isNull()))
                .thenReturn(false);
        action = new ViewAdministrationIndex2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should return SUCCESS when _admin r is held")
    void shouldReturnSuccess_whenAdminGranted() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should return SUCCESS when only a sub-privilege like _admin.billing is held")
    void shouldReturnSuccess_whenSubPrivilegeGranted() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("r"), isNull()))
                .thenReturn(true);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should throw when caller holds no admin privileges at all")
    void shouldThrow_whenNoAdminPrivs() {
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    @Test
    void shouldThrow_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldSend405_onPost() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("POST");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
