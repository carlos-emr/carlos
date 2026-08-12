/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.decision.AntenatalRiskConfigService;
import io.github.carlos_emr.carlos.decision.AntenatalRiskConfigService.InvalidConfigurationException;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@DisplayName("Save antenatal risk configuration action")
@Tag("unit")
@Tag("decision")
class SaveAntenatalRiskConfig2ActionUnitTest {

    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loggedInContext;
    private MockedStatic<LogAction> auditLog;
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
        // The audit entry writes through a DAO; stub it out so these stay unit tests.
        auditLog = mockStatic(LogAction.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(loggedInInfo.getIp()).thenReturn("10.0.0.7");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", "w", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        action = new SaveAntenatalRiskConfig2Action(securityInfoManager, configService);
    }

    @AfterEach
    void tearDown() {
        auditLog.close();
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

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("should reject non-POST requests without invoking persistence")
    void shouldReject_nonPostRequest(String method) throws Exception {
        request.setMethod(method);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager);
        verify(configService, never()).save(any());
    }

    @Test
    @DisplayName("should audit the configuration replacement only after a successful store")
    void shouldAudit_onSuccessfulSave() throws Exception {
        action.execute();

        auditLog.verify(() -> LogAction.addLogSynchronous(
                eq("999998"),
                eq(LogConst.UPDATE),
                eq(LogConst.CON_ANTENATAL_RISK_CONFIG),
                isNull(),
                eq("10.0.0.7")));
    }

    @Test
    @DisplayName("should not audit when the store fails")
    void shouldNotAudit_whenStoreFails() throws Exception {
        doThrow(new IOException("disk full"))
                .when(configService).save("<riskFactors/>");

        action.execute();

        auditLog.verifyNoInteractions();
    }

    @Test
    @DisplayName("should redisplay submitted XML after a validation failure")
    void shouldRedisplay_validationFailure() throws Exception {
        doThrow(new InvalidConfigurationException("invalid structure"))
                .when(configService).save("<riskFactors/>");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("riskEditorError")).isEqualTo("invalid structure");
        assertThat(request.getAttribute("riskEditorChecklist")).isEqualTo("<riskFactors/>");
    }

    @Test
    @DisplayName("should report a generic failure and preserve submitted XML after an I/O error")
    void shouldRedisplay_writeFailure() throws Exception {
        doThrow(new IOException("filesystem details"))
                .when(configService).save("<riskFactors/>");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("riskEditorError").toString())
                .doesNotContain("filesystem details")
                .contains("existing configuration was not changed");
        assertThat(request.getAttribute("riskEditorChecklist")).isEqualTo("<riskFactors/>");
    }
}
