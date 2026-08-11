/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

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

import io.github.carlos_emr.carlos.decision.AntenatalRiskConfigService;
import io.github.carlos_emr.carlos.decision.AntenatalRiskConfigService.InvalidConfigurationException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@DisplayName("Save antenatal risk configuration action")
@Tag("unit")
@Tag("decision")
class SaveAntenatalRiskConfig2ActionTest {

    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loggedInContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private AntenatalRiskConfigService configService;
    private LoggedInInfo loggedInInfo;
    private SaveAntenatalRiskConfig2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("checklist", "<riskFactors/>");
        securityInfoManager = mock(SecurityInfoManager.class);
        configService = mock(AntenatalRiskConfigService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInContext = mockStatic(LoggedInInfo.class);
        loggedInContext.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", "w", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        action = new SaveAntenatalRiskConfig2Action(securityInfoManager, configService);
    }

    @AfterEach
    void tearDown() {
        loggedInContext.close();
        servletContext.close();
    }

    @Test
    @DisplayName("should reject a caller without clinical form write privilege")
    void shouldReject_missingFormPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", "w", null)).thenReturn(false);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_form w");
        verify(configService, never()).save(any());
    }

    @Test
    @DisplayName("should reject a form editor without configuration administration privilege")
    void shouldReject_missingAdminPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "w", null)).thenReturn(false);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.misc w");
        verify(configService, never()).save(any());
    }

    @Test
    @DisplayName("should allow miscellaneous administrators to save clinical configuration")
    void shouldAllow_miscAdminPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "w", null)).thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(configService).save("<riskFactors/>");
        assertThat(request.getSession().getAttribute(SaveAntenatalRiskConfig2Action.SAVED_FLASH_ATTRIBUTE))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("should reject non-POST requests without invoking persistence")
    void shouldReject_nonPostRequest() throws Exception {
        request.setMethod("GET");

        assertThat(action.execute()).isEqualTo("methodNotAllowed");
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(configService, never()).save(any());
    }

    @Test
    @DisplayName("should redisplay submitted XML after a validation failure")
    void shouldRedisplay_validationFailure() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidConfigurationException("invalid structure"))
                .when(configService).save("<riskFactors/>");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("riskEditorError")).isEqualTo("invalid structure");
        assertThat(request.getAttribute("riskEditorChecklist")).isEqualTo("<riskFactors/>");
    }

    @Test
    @DisplayName("should report a generic failure and preserve submitted XML after an I/O error")
    void shouldRedisplay_writeFailure() throws Exception {
        org.mockito.Mockito.doThrow(new IOException("filesystem details"))
                .when(configService).save("<riskFactors/>");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("riskEditorError").toString())
                .doesNotContain("filesystem details")
                .contains("existing configuration was not changed");
        assertThat(request.getAttribute("riskEditorChecklist")).isEqualTo("<riskFactors/>");
    }
}
