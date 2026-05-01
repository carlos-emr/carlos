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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.ManageBillingLocationViewModel;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the {@code _admin.billing/w} privilege gate, the
 * Delete-on-GET → 405 contract, and verifies that the location DAO is
 * not called when the gate denies.
 *
 * @since 2026-04-29
 */
@DisplayName("ManageBillingLocation2Action")
@Tag("unit")
@Tag("billing")
class ManageBillingLocation2ActionUnitTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private ClinicLocationDao mockClinicLocationDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockClinicLocationDao.findByClinicNo(1)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldRenderForm_whenPrivilegeGrantedAndNoSubmit() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);

        ManageBillingLocation2Action action = new ManageBillingLocation2Action(
                mockSecurityInfoManager, mockClinicLocationDao);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        // isInstanceOf catches a future refactor that stashes the wrong-shape
        // viewmodel (e.g., the BillType model instead of Location); a bare
        // isNotNull would let that regression slip through.
        assertThat(mockRequest.getAttribute("manageLocationModel"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.viewmodel
                        .ManageBillingLocationViewModel.class);
        verify(mockClinicLocationDao, never()).removeByClinicLocationNo(any());
    }

    @Test
    void shouldExposeClinicLocationRowsInsteadOfJpaEntities() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        ClinicLocation location = new ClinicLocation();
        location.setClinicLocationNo("42");
        location.setClinicLocationName("Downtown");
        when(mockClinicLocationDao.findByClinicNo(1)).thenReturn(List.of(location));

        ManageBillingLocation2Action action = new ManageBillingLocation2Action(
                mockSecurityInfoManager, mockClinicLocationDao);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        ManageBillingLocationViewModel model =
                (ManageBillingLocationViewModel) mockRequest.getAttribute("manageLocationModel");
        assertThat(model.getLocations()).hasSize(1);
        assertThat(model.getLocations().get(0))
                .isInstanceOf(ManageBillingLocationViewModel.ClinicLocationRow.class)
                .isNotInstanceOf(ClinicLocation.class);
        assertThat(model.getLocations().get(0).getClinicLocationNo()).isEqualTo("42");
        assertThat(model.getLocations().get(0).getClinicLocationName()).isEqualTo("Downtown");
    }

    @Test
    void shouldDeleteLocation_whenPostWithDeleteSubmit() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("submit", "Delete");
        mockRequest.setParameter("location_no", "42");

        ManageBillingLocation2Action action = new ManageBillingLocation2Action(
                mockSecurityInfoManager, mockClinicLocationDao);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockClinicLocationDao).removeByClinicLocationNo("42");
    }

    @Test
    void shouldReturn405_whenGetWithDeleteSubmit() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("submit", "Delete");
        mockRequest.setParameter("location_no", "42");

        ManageBillingLocation2Action action = new ManageBillingLocation2Action(
                mockSecurityInfoManager, mockClinicLocationDao);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(mockClinicLocationDao, never()).removeByClinicLocationNo(any());
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("submit", "Delete");
        mockRequest.setParameter("location_no", "42");

        ManageBillingLocation2Action action = new ManageBillingLocation2Action(
                mockSecurityInfoManager, mockClinicLocationDao);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
        verify(mockClinicLocationDao, never()).removeByClinicLocationNo(any());
    }
}
