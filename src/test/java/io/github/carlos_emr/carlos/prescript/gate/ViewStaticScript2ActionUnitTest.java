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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Patient-scoped authorisation for the static-script gate: the page renders, and offers to
 * re-prescribe, the saved drugs of the patient named in the URL, so global {@code _rx} read is not
 * enough on its own.
 *
 * @since 2026-09-24
 */
@DisplayName("ViewStaticScript2Action patient authorisation")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class ViewStaticScript2ActionUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 42;

    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/rx/ViewStaticScript2");
        request.addParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.addParameter("regionalIdentifier", "02242903");
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should forward when the caller may read the named patient's Rx")
    void shouldForward_whenNamedPatientIsAuthorised() throws Exception {
        assertThat(new ViewStaticScript2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should refuse a patient whose Rx the caller may not read, despite global _rx read")
    void shouldThrow_whenPatientLevelRxReadIsDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO)).thenReturn(false);

        assertThatThrownBy(() -> new ViewStaticScript2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }

    @Test
    @DisplayName("should refuse a patient whose chart the caller may not open")
    void shouldThrow_whenPatientRecordAccessIsDenied() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(false);

        assertThatThrownBy(() -> new ViewStaticScript2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }

    @Test
    @DisplayName("should refuse before any patient check when global _rx read is missing")
    void shouldThrow_whenGlobalRxReadIsDenied() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"), eq("r"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> new ViewStaticScript2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    @DisplayName("should leave a request that names no patient to the page, which refuses it")
    void shouldSkipPatientCheck_whenNoPatientNamed() throws Exception {
        request.removeParameter("demographicNo");

        assertThat(new ViewStaticScript2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }
}
