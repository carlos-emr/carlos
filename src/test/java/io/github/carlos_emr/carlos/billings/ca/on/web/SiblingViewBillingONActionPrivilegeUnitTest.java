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
package io.github.carlos_emr.carlos.billings.ca.on.web;

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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Privilege-parity tests for the five sibling Ontario billing view actions
 * whose access privilege flipped from {@code _team_billing_only} to
 * {@code _billing} (and which gained a {@code loggedInInfo == null} guard)
 * during this PR. Without these tests, a regression flipping the privilege
 * string back — or removing the null guard — would not fail any existing
 * test.
 *
 * <p>Parameterised across the five actions; each test asserts:
 * <ul>
 *   <li>null session → {@code SecurityException("missing session")}</li>
 *   <li>missing {@code _billing r} → {@code SecurityException}</li>
 *   <li>authorised → {@code SUCCESS}</li>
 * </ul>
 *
 * @since 2026-04-25
 */
@DisplayName("Sibling ON billing view-action privilege parity")
@Tag("unit")
@Tag("billing")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // Allows non-static @MethodSource that closes over @Mock fields
class SiblingViewBillingONActionPrivilegeUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewPrep.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewPrep.class));
        registerMock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService.class));
        registerMock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONRemittanceAdviceService.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONRemittanceAdviceService.class));
        registerMock(io.github.carlos_emr.carlos.billings.ca.on.service.OhipClaimFileService.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.OhipClaimFileService.class));
        registerMock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.ReportProviderDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.ReportProviderDao.class));
        registerMock(io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.SiteDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.SiteDao.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.ProviderDataDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.ProviderDataDao.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    Stream<org.junit.jupiter.params.provider.Arguments> actions() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "ViewBillingONMRI2Action",
                        (Callable<String>) () -> new ViewBillingONMRI2Action(mockSecurityInfoManager,
                                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONMRIDataAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "ViewBillingONNewReport2Action",
                        (Callable<String>) () -> new ViewBillingONNewReport2Action(mockSecurityInfoManager,
                                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONNewReportDataAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "ViewBillingON3rdPayments2Action",
                        (Callable<String>) () -> new ViewBillingON3rdPayments2Action(mockSecurityInfoManager).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "ViewBillingOHIPsimulation2Action",
                        (Callable<String>) () -> new ViewBillingOHIPsimulation2Action(mockSecurityInfoManager,
                                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOHIPSimulationDataAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "ViewOnGenRA2Action",
                        (Callable<String>) () -> new ViewOnGenRA2Action(mockSecurityInfoManager,
                                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.OnGenRADataAssembler.class)).execute()));
    }

    @ParameterizedTest(name = "{0} — sessionless throws SecurityException")
    @MethodSource("actions")
    void shouldThrowSecurityException_whenSessionMissing(String name, Callable<String> exec) {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(exec::call)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");
    }

    @ParameterizedTest(name = "{0} — lacking _billing r throws SecurityException")
    @MethodSource("actions")
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege(String name, Callable<String> exec) {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(exec::call)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @ParameterizedTest(name = "{0} — authorised returns SUCCESS")
    @MethodSource("actions")
    void shouldReturnSuccess_whenAuthorisedWithBillingReadPrivilege(String name, Callable<String> exec) throws Exception {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);

        assertThat(exec.call()).isEqualTo(ActionSupport.SUCCESS);
    }
}
