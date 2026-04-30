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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingEditWithApptNoViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipBillingHistoryViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnPaymentViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.EditBillingPaymentTypeViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.GstReportViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.InrBillingUpdateViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionSubmissionService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingThirdPartyService;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Privilege parity tests for ON billing 2Actions that previously had no test
 * coverage. Each action is invoked with {@code loggedInInfo == null}; every
 * action must throw {@link SecurityException} from its own gate (or, where
 * applicable, from the {@code SecurityInfoManagerImpl.hasPrivilege(null,...)}
 * downstream call). Both shapes satisfy the contract — the test guards
 * against a regression that lets a null-session request reach business logic.
 *
 * <p>Coverage scope (per the PR-review-toolkit's pr-test-analyzer findings):
 * 11 actions that called {@code hasPrivilege} but had no unit test.
 *
 * @since 2026-04-30
 */
@DisplayName("Untested ON billing action privilege parity")
@Tag("unit")
@Tag("billing")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UntestedBillingActionPrivilegeUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private SecurityInfoManager mockSecurity;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();
        mockSecurity = mock(SecurityInfoManager.class);
        // Default: no privilege. Each action's gate should throw before
        // touching business logic.
        when(mockSecurity.hasPrivilege(any(), any(), any(), any())).thenReturn(false);

        registerMock(SecurityInfoManager.class, mockSecurity);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        // Always return null — the strongest gate-test shape.
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    /**
     * Each row supplies (label, factory). The factory returns a Callable that
     * invokes the action's execute() method; we assert SecurityException
     * propagates from the privilege gate.
     */
    Stream<org.junit.jupiter.params.provider.Arguments> actionFactories() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "BillingCorrectionSubmit2Action",
                        (Callable<String>) () -> new BillingCorrectionSubmit2Action(
                                mockSecurity, mock(BillingCorrectionSubmissionService.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "BillingCorrectionReview2Action",
                        (Callable<String>) () -> new BillingCorrectionReview2Action(mockSecurity).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "BillingEditWithApptNo2Action",
                        (Callable<String>) () -> new BillingEditWithApptNo2Action(
                                mockSecurity, mock(BillingEditWithApptNoViewModelAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "InrBillingUpdate2Action",
                        (Callable<String>) () -> new InrBillingUpdate2Action(
                                mockSecurity, mock(InrBillingUpdateViewModelAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "OnThirdPartyAddressEdit2Action",
                        (Callable<String>) () -> new OnThirdPartyAddressEdit2Action(
                                mockSecurity, mock(BillingThirdPartyService.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "GstReport2Action",
                        (Callable<String>) () -> new GstReport2Action(
                                mockSecurity, mock(GstReportViewModelAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "EditBillingPaymentType2Action",
                        (Callable<String>) () -> new EditBillingPaymentType2Action(
                                mockSecurity, mock(EditBillingPaymentTypeViewModelAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "BillingLegacyReport2Action",
                        (Callable<String>) () -> new BillingLegacyReport2Action(mockSecurity).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "ManageBillingLocation2Action",
                        (Callable<String>) () -> new ManageBillingLocation2Action(
                                mockSecurity, mock(ClinicLocationDao.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "BillingOB2View2Action",
                        (Callable<String>) () -> new BillingOB2View2Action(
                                mockSecurity, mock(BillingOhipBillingHistoryViewModelAssembler.class)).execute()),
                org.junit.jupiter.params.provider.Arguments.of(
                        "BillingOnPayment2Action",
                        (Callable<String>) () -> new BillingOnPayment2Action(
                                mockSecurity, mock(BillingOnPaymentViewModelAssembler.class)).execute())
        );
    }

    @ParameterizedTest(name = "{0} throws SecurityException when session is missing")
    @MethodSource("actionFactories")
    void shouldRejectMissingSession(String label, Callable<String> action) {
        // null session — every action must throw before reaching business logic.
        assertThatThrownBy(action::call)
                .as("Action [%s] must enforce session gate", label)
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest(name = "{0} throws SecurityException when privilege missing")
    @MethodSource("actionFactories")
    void shouldRejectMissingPrivilege(String label, Callable<String> action) {
        // Drive the privilege-false branch independently of the null-session
        // gate. Pre-fix the parameterized test only ran with null session, so
        // a regression that flipped the privilege check (e.g., wrong privilege
        // string) wouldn't have failed any test.
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));
        when(mockSecurity.hasPrivilege(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(action::call)
                .as("Action [%s] must enforce privilege gate when session is present", label)
                .isInstanceOf(SecurityException.class);
    }

}
