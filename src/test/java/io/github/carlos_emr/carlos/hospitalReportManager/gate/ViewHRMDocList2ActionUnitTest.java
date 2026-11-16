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
package io.github.carlos_emr.carlos.hospitalReportManager.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The chart's HRM Documents popup lists one patient's reports. Its gate refuses a patient the
 * provider is locked out of before the page reads anything.
 */
@Tag("unit")
@Tag("hrm")
@Tag("security")
@DisplayName("ViewHRMDocList2Action patient access")
class ViewHRMDocList2ActionUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 4242;

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        request = new MockHttpServletRequest("GET", "/hospitalReportManager/ViewDocList");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(user);
        when(security.hasPrivilege(user, "_hrm", "r", null)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private void permitPatient() {
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", "r", Integer.toString(PATIENT))).thenReturn(true);
        when(security.hasPrivilege(user, "_hrm", "r", Integer.toString(PATIENT))).thenReturn(true);
    }

    @Test
    @DisplayName("should list the reports of a patient the provider may read")
    void shouldForwardToList_whenPatientIsReadable() throws Exception {
        request.setParameter("demographic_no", Integer.toString(PATIENT));
        permitPatient();

        assertThat(new ViewHRMDocList2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should refuse the list of a patient the provider is locked out of")
    void shouldRefuse_whenPatientIsLocked() {
        request.setParameter("demographic_no", Integer.toString(PATIENT));
        permitPatient();
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> new ViewHRMDocList2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required patient read access");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_hrm"})
    @DisplayName("should refuse the list when a right is withheld for that patient")
    void shouldRefuse_whenPatientScopedRightIsMissing(String object) {
        request.setParameter("demographic_no", Integer.toString(PATIENT));
        permitPatient();
        when(security.hasPrivilege(user, object, "r", Integer.toString(PATIENT))).thenReturn(false);

        assertThatThrownBy(() -> new ViewHRMDocList2Action().execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should leave a request that names no patient to the page, as before")
    void shouldForwardToList_whenNoPatientIsNamed() throws Exception {
        assertThat(new ViewHRMDocList2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        request.setParameter("demographic_no", "0");
        assertThat(new ViewHRMDocList2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(security, never()).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt());
    }

    @Test
    @DisplayName("should keep refusing a caller without the module right")
    void shouldThrowSecurityException_whenModuleRightIsMissing() {
        when(security.hasPrivilege(user, "_hrm", "r", null)).thenReturn(false);
        request.setParameter("demographic_no", Integer.toString(PATIENT));
        permitPatient();

        assertThatThrownBy(() -> new ViewHRMDocList2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_hrm r)");
    }
}
