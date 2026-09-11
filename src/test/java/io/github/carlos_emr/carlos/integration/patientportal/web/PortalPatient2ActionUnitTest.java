/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

@Tag("unit")
@Tag("patient-portal")
@DisplayName("Portal patient view gate")
class PortalPatient2ActionUnitTest {

    private static final int DEMOGRAPHIC_NO = 123;

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<ServletActionContext> servletActionContextStatic;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/demographic/portalPatient");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        response = new MockHttpServletResponse();

        servletActionContextStatic = mockStatic(ServletActionContext.class);
        servletActionContextStatic.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextStatic.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic
                .when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoStatic.close();
        servletActionContextStatic.close();
    }

    @Test
    @DisplayName("should reject POST before consulting access controls")
    void shouldRejectPost_beforeAuthorization() {
        request.setMethod("POST");

        String result = new PortalPatient2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("GET");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should reject a missing patient before consulting access controls")
    void shouldRejectRequest_withoutPatientScope() {
        request.removeParameter("demographicNo");

        String result = new PortalPatient2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should require both demographic and portal account read access")
    void shouldRejectRequest_withoutPortalReadAccess() {
        allowDemographicAccess();
        when(securityInfoManager.hasPrivilege(
                        loggedInInfo,
                        PortalStaffContextResolver.OBJECT_ACCOUNT,
                        SecurityInfoManager.READ,
                        String.valueOf(DEMOGRAPHIC_NO)))
                .thenReturn(false);

        assertThatThrownBy(() -> new PortalPatient2Action(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_portal.account)");
    }

    @Test
    @DisplayName("should expose only patient scope and capability flags to the view")
    void shouldPrepareView_withAuthorizedCapabilities() {
        allowDemographicAccess();
        when(securityInfoManager.hasPrivilege(
                        loggedInInfo,
                        PortalStaffContextResolver.OBJECT_ACCOUNT,
                        SecurityInfoManager.READ,
                        String.valueOf(DEMOGRAPHIC_NO)))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(
                        loggedInInfo,
                        PortalStaffContextResolver.OBJECT_ACCOUNT,
                        SecurityInfoManager.WRITE,
                        String.valueOf(DEMOGRAPHIC_NO)))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(
                        loggedInInfo,
                        PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK,
                        SecurityInfoManager.WRITE,
                        String.valueOf(DEMOGRAPHIC_NO)))
                .thenReturn(false);

        String result = new PortalPatient2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("demographicNo")).isEqualTo("123");
        assertThat(request.getAttribute("canManagePortalAccount")).isEqualTo(true);
        assertThat(request.getAttribute("canUnlockPortalAccount")).isEqualTo(false);
    }

    @Test
    @DisplayName("should keep browser mutations behind CSRF and text-only rendering")
    void shouldProtectBrowserMutations_withCsrfAndSafeRendering() throws IOException {
        String jsp = Files.readString(
                Path.of("src/main/webapp/WEB-INF/jsp/demographic/portalPatient.jsp"),
                StandardCharsets.UTF_8);
        String script = Files.readString(
                Path.of("src/main/webapp/share/javascript/patientPortalAccount.js"),
                StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("/WEB-INF/jspf/csrf-token.jspf")
                .contains("patientPortalAccount.js")
                .contains("requestScope.demographicNo")
                .contains("forHtmlContent(requestScope.demographicNo)")
                .contains("portal-account-disabled-reason")
                .contains("for=\"portal-account-disable-reason\"")
                .contains("maxlength=\"64\"");
        assertThat(script)
                .contains("CSRF-TOKEN")
                .contains("X-Requested-With")
                .contains("textContent")
                .contains("hideAccountControls()")
                .contains("await loadAccount(payload.note")
                .contains("refresh.addEventListener(\"click\", function ()")
                .contains("disabledReasonValue.textContent")
                .contains("disableReason.value = \"\"")
                .contains("CARLOS patient \" + demographicNo")
                .contains("/demographic/portalAccount")
                .doesNotContain("innerHTML")
                .doesNotContain("outerHTML");
    }

    @Test
    @DisplayName("should expose the protected direct route without adding navigation")
    void shouldKeepViewUnreachable_fromNormalNavigation() throws IOException {
        String routes = Files.readString(
                Path.of("src/main/webapp/WEB-INF/classes/struts-demographic.xml"),
                StandardCharsets.UTF_8);
        String demographic = Files.readString(
                Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit.jsp"),
                StandardCharsets.UTF_8);

        assertThat(routes)
                .contains("demographic/portalPatient")
                .contains("PortalPatient2Action")
                .contains("/WEB-INF/jsp/demographic/portalPatient.jsp");
        assertThat(demographic).doesNotContain("/demographic/portalPatient");
    }

    private void allowDemographicAccess() {
        when(securityInfoManager.hasPrivilege(
                        loggedInInfo,
                        "_demographic",
                        SecurityInfoManager.READ,
                        String.valueOf(DEMOGRAPHIC_NO)))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO))
                .thenReturn(true);
    }
}
