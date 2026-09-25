/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxPreviewSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link ViewPreview22Action}: the read-only print preview gate, bound to the explicitly requested saved prescription.
 */
@Tag("unit")
@Tag("prescription")
@Tag("security")
@DisplayName("ViewPreview22Action patient authorisation")
class ViewPreview22ActionUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 42;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/rx/ViewPreview2");
        request.addParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        response = new MockHttpServletResponse();
        RxSessionBeanResolver.activate(request, DEMOGRAPHIC_NO, "doctor");
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should forward to the page when the caller is authorised for the named patient")
    void shouldForward_whenNamedPatientIsAuthorised() throws Exception {
        assertThat(new ViewPreview22Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should refuse the page without global _rx read")
    void shouldThrow_whenGlobalPrivilegeIsDenied() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"), eq("r"), isNull())).thenReturn(false);

        ViewPreview22Action action = new ViewPreview22Action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }

    @Test
    @DisplayName("should refuse a patient the caller lacks _rx r for, despite the global privilege")
    void shouldThrow_whenPatientLevelPrivilegeIsDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO)).thenReturn(false);

        ViewPreview22Action action = new ViewPreview22Action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }

    @Test
    @DisplayName("should refuse a patient whose chart the caller may not open")
    void shouldThrow_whenPatientRecordAccessIsDenied() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(false);

        ViewPreview22Action action = new ViewPreview22Action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }

    @Test
    void pinsTheExplicitSavedScriptDespiteDifferentSessionStaging() throws Exception {
        request.addParameter("scriptId", "7");
        RxPreviewSnapshot expected = new RxPreviewSnapshot(new RxSessionBean(), "7", "notes");
        try (var snapshots = mockStatic(RxPreviewSnapshot.class)) {
            snapshots.when(() -> RxPreviewSnapshot.load(DEMOGRAPHIC_NO, "7")).thenReturn(expected);
            assertThat(new ViewPreview22Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute(RxPreviewSnapshot.REQUEST_ATTRIBUTE)).isSameAs(expected);
        }
    }

    @Test
    void refusesMissingOrForeignScriptWithoutFallingBackToSession() throws Exception {
        request.addParameter("scriptId", "7");
        try (var snapshots = mockStatic(RxPreviewSnapshot.class)) {
            assertThat(new ViewPreview22Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(request.getAttribute(RxPreviewSnapshot.REQUEST_ATTRIBUTE)).isNull();
        }
    }

    @Test
    void refusesMalformedScriptWithoutFallingBackToSession() throws Exception {
        request.addParameter("scriptId", "bad");
        try (var snapshots = mockStatic(RxPreviewSnapshot.class)) {
            snapshots.when(() -> RxPreviewSnapshot.load(DEMOGRAPHIC_NO, "bad"))
                    .thenThrow(new IllegalArgumentException());
            assertThat(new ViewPreview22Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
        }
    }
}
