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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Ownership checks for {@link EctDeleteData2Action}.
 *
 * <p>The endpoint is handed a measurement id and nothing else, so the chart-wide
 * {@code _measurement d} privilege it checks first says only that the caller may
 * delete measurements <em>somewhere</em>. The deleted row names its own patient,
 * and this is the check that asks whether the caller may delete in <em>that</em>
 * chart — without it, changing the posted id reaches any patient's measurement.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class EctDeleteData2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private EctDeleteData2Action action;

    @BeforeEach
    void setUp() {
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(new MockHttpServletRequest());
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        registerMock(SecurityInfoManager.class, securityInfoManager);
        action = new EctDeleteData2Action();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should allow deletion when both patient-scoped checks pass")
    void shouldAllowDelete_whenPatientScopedPrivilegeHeld() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), eq("111")))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt()))
                .thenReturn(true);

        assertThat(action.isAllowedToDelete(mock(LoggedInInfo.class), 111)).isTrue();
    }

    @Test
    @DisplayName("should refuse deletion when the measurement belongs to a locked chart")
    void shouldRefuseDelete_whenPatientRecordNotAccessible() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), eq("111")))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt()))
                .thenReturn(false);

        assertThat(action.isAllowedToDelete(mock(LoggedInInfo.class), 111)).isFalse();
    }

    @Test
    @DisplayName("should refuse deletion when delete is denied for that patient")
    void shouldRefuseDelete_whenPatientScopedPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), eq("111")))
                .thenReturn(false);

        assertThat(action.isAllowedToDelete(mock(LoggedInInfo.class), 111)).isFalse();
    }

    @Test
    @DisplayName("should refuse deletion when the row carries no demographic")
    void shouldRefuseDelete_whenDemographicNull() {
        assertThat(action.isAllowedToDelete(mock(LoggedInInfo.class), null)).isFalse();
    }
}
