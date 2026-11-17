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
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link RxRequestedPatientAccess#activateAuthorised}: a JSP entry point opens a patient's Rx bean
 * only after the caller is authorised for that patient, so a forward that authorised a different
 * patient (rx/addFavoriteStaticScript) cannot expose or activate the requested one (#3908).
 */
@Tag("unit")
@Tag("prescription")
@Tag("security")
@DisplayName("RxRequestedPatientAccess.activateAuthorised")
class RxRequestedPatientAccessActivateUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 42;
    private static final String PROVIDER_NO = "999998";

    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/rx/ViewStaticScript2");
        request.addParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(true);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
    }

    @Test
    @DisplayName("should open the patient's bean when the caller may read that patient")
    void shouldActivateBean_whenPatientIsAuthorised() {
        RxSessionBean bean = RxRequestedPatientAccess.activateAuthorised(request, DEMOGRAPHIC_NO, PROVIDER_NO, "_rx", "r");

        assertThat(bean).isNotNull();
        assertThat(bean.getDemographicNo()).isEqualTo(DEMOGRAPHIC_NO);
        assertThat(RxSessionBeanResolver.resolve(request)).isSameAs(bean);
    }

    @Test
    @DisplayName("should neither open nor activate the patient when patient-level read is denied")
    void shouldReturnNullAndActivateNothing_whenPatientLevelReadDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO)).thenReturn(false);

        assertThat(RxRequestedPatientAccess.activateAuthorised(request, DEMOGRAPHIC_NO, PROVIDER_NO, "_rx", "r")).isNull();

        assertThat(RxSessionBeanResolver.resolve(request)).isNull();
    }

    @Test
    @DisplayName("should neither open nor activate the patient when record access is denied")
    void shouldReturnNullAndActivateNothing_whenRecordAccessDenied() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(false);

        assertThat(RxRequestedPatientAccess.activateAuthorised(request, DEMOGRAPHIC_NO, PROVIDER_NO, "_rx", "r")).isNull();

        assertThat(RxSessionBeanResolver.resolve(request)).isNull();
    }
}
