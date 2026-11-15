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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The report viewer route refuses a report filed to a patient the provider is locked out of
 * before the viewer page, which reads the report itself, is reached.
 */
@Tag("unit")
@Tag("hrm")
@Tag("security")
@DisplayName("HRMDisplayReport2Action patient access")
class HRMDisplayReport2ActionUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 4242;

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final HRMDocumentToDemographicDao links = mock(HRMDocumentToDemographicDao.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(HRMDocumentToDemographicDao.class, links);
        registerMock(HRMDocumentToProviderDao.class, mock(HRMDocumentToProviderDao.class));
        request = new MockHttpServletRequest("GET", "/hospitalReportManager/Display");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(user);
        when(security.hasPrivilege(user, "_hrm", "r", null)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private void fileReportTo(int reportId, Integer patient) {
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setHrmDocumentId(reportId);
        link.setDemographicNo(patient);
        when(links.findByHrmDocumentId(reportId)).thenReturn(List.of(link));
    }

    private void permitPatient() {
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", "r", Integer.toString(PATIENT))).thenReturn(true);
        when(security.hasPrivilege(user, "_hrm", "r", Integer.toString(PATIENT))).thenReturn(true);
    }

    @Test
    @DisplayName("should show a report filed to a patient the provider may read")
    void shouldForwardToViewer_whenPatientIsReadable() {
        request.setParameter("id", "7");
        fileReportTo(7, PATIENT);
        permitPatient();

        assertThat(new HRMDisplayReport2Action().execute()).isEqualTo("display");
    }

    @Test
    @DisplayName("should refuse a report filed to a patient the provider is locked out of")
    void shouldRefuse_whenPatientIsLocked() {
        request.setParameter("id", "7");
        fileReportTo(7, PATIENT);
        permitPatient();
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> new HRMDisplayReport2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required patient read access");
        assertThat(request.getAttribute("criteria")).isNull();
    }

    @Test
    @DisplayName("should decide for the number the viewer page will read, however it is written")
    void shouldRefuse_whenLockedReportIdIsWrittenWithSign() {
        request.setParameter("id", "+7");
        fileReportTo(7, PATIENT);

        assertThatThrownBy(() -> new HRMDisplayReport2Action().execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should show a report filed to no patient on the module right alone")
    void shouldForwardToViewer_whenReportIsFiledToNoPatient() {
        request.setParameter("id", "7");
        when(links.findByHrmDocumentId(7)).thenReturn(List.of());

        assertThat(new HRMDisplayReport2Action().execute()).isEqualTo("display");
        verify(security, never()).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt());
    }

    @Test
    @DisplayName("should keep refusing a caller without the module right before any report is looked up")
    void shouldThrowSecurityException_whenModuleRightIsMissing() {
        when(security.hasPrivilege(user, "_hrm", "r", null)).thenReturn(false);
        request.setParameter("id", "7");

        assertThatThrownBy(() -> new HRMDisplayReport2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_hrm)");
        verify(links, never()).findByHrmDocumentId(anyInt());
    }
}
