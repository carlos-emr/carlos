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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxManagePharmacy2Action}.
 *
 * @since 2026-05-03
 */
@DisplayName("RxManagePharmacy2Action Unit Tests")
@Tag("unit")
@Tag("rx")
class RxManagePharmacy2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private PharmacyInfoDao mockPharmacyInfoDao;

    @Mock
    private DemographicPharmacyDao mockDemographicPharmacyDao;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private RxManagePharmacy2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(PharmacyInfoDao.class, mockPharmacyInfoDao);
        registerMock(DemographicPharmacyDao.class, mockDemographicPharmacyDao);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        action = new RxManagePharmacy2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should return success when pharmacyAction is missing")
    void shouldReturnSuccess_whenPharmacyActionIsMissing() throws Exception {
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(mockPharmacyInfoDao, mockDemographicPharmacyDao);
    }

    @Test
    @DisplayName("should throw security exception when pharmacyAction is missing and write privilege denied")
    void shouldThrowSecurityException_whenPharmacyActionIsMissingAndWritePrivilegeDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_rx");
    }

    @Test
    @DisplayName("should throw security exception when pharmacyAction is missing and only read privilege granted")
    void shouldThrowSecurityException_whenPharmacyActionIsMissingAndOnlyReadPrivilegeGranted() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_rx");
    }
}
