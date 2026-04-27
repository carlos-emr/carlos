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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONReviewViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONReviewDataAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONReviewDxPersister;

/**
 * Unit tests for {@link ViewBillingONReview2Action}.
 *
 * @since 2026-04-24
 */
@DisplayName("ViewBillingONReview2Action")
@Tag("unit")
@Tag("billing")
class ViewBillingONReview2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingONReviewDxPersister mockDxPersister;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        // Persister is fetched via SpringUtils.getBean in the action's no-arg
        // ctor; register the same instance the test holds so ordering can be
        // verified against the same mock the action received.
        registerMock(BillingONReviewDxPersister.class, mockDxPersister);
        registerMock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONServiceCodeService.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONServiceCodeService.class));
        registerMock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.SiteDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.SiteDao.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private static final BillingONReviewViewModel STUB_MODEL =
            BillingONReviewViewModel.builder().dxCode("401").build();

    @Test
    void shouldReturnSuccess_whenAuthorizedPostRequest() throws Exception {
        try (MockedConstruction<BillingONReviewDxPersister> persisterIgnored = mockConstruction(BillingONReviewDxPersister.class);
             MockedConstruction<BillingONReviewDataAssembler> ignored = mockConstruction(
                BillingONReviewDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            ViewBillingONReview2Action action = new ViewBillingONReview2Action(mockSecurityInfoManager, mockDxPersister, new BillingONReviewDataAssembler());
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(action.getReviewModel()).isSameAs(STUB_MODEL);
        }
    }

    @Test
    void shouldRejectGet_with405() throws Exception {
        mockRequest.setMethod("GET");

        try (MockedConstruction<BillingONReviewDxPersister> persisterIgnored = mockConstruction(BillingONReviewDxPersister.class);
             MockedConstruction<BillingONReviewDataAssembler> ignored =
                mockConstruction(BillingONReviewDataAssembler.class)) {
            ViewBillingONReview2Action action = new ViewBillingONReview2Action(mockSecurityInfoManager, mockDxPersister, new BillingONReviewDataAssembler());
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        }
    }

    /**
     * Reject sessionless requests up front rather than letting the call
     * to {@code SecurityInfoManager.hasPrivilege(null, ...)} dereference
     * null inside the manager (which emits an internal ERROR log,
     * polluting the privilege-denial signal). Regression armor for the
     * null-loggedInInfo guard in the action.
     */
    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        try (MockedConstruction<BillingONReviewDxPersister> persisterIgnored = mockConstruction(BillingONReviewDxPersister.class);
             MockedConstruction<BillingONReviewDataAssembler> ignored =
                mockConstruction(BillingONReviewDataAssembler.class)) {
            ViewBillingONReview2Action action = new ViewBillingONReview2Action(mockSecurityInfoManager, mockDxPersister, new BillingONReviewDataAssembler());
            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("session");
        }
    }

    @Test
    void shouldThrowSecurityException_whenLacksBillingWrite() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        try (MockedConstruction<BillingONReviewDxPersister> persisterIgnored = mockConstruction(BillingONReviewDxPersister.class);
             MockedConstruction<BillingONReviewDataAssembler> ignored =
                mockConstruction(BillingONReviewDataAssembler.class)) {
            ViewBillingONReview2Action action = new ViewBillingONReview2Action(mockSecurityInfoManager, mockDxPersister, new BillingONReviewDataAssembler());
            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_billing");
        }
    }

    @Test
    void shouldExposeModel_asRequestAttribute() throws Exception {
        try (MockedConstruction<BillingONReviewDxPersister> persisterIgnored = mockConstruction(BillingONReviewDxPersister.class);
             MockedConstruction<BillingONReviewDataAssembler> ignored = mockConstruction(
                BillingONReviewDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            ViewBillingONReview2Action action = new ViewBillingONReview2Action(mockSecurityInfoManager, mockDxPersister, new BillingONReviewDataAssembler());
            action.execute();
            assertThat(mockRequest.getAttribute("reviewModel")).isSameAs(STUB_MODEL);
        }
    }

    /**
     * Regression armor: the persister runs BEFORE the assembler so any audit
     * failure (non-numeric demoNo, DAO outage) propagates through the action's
     * standard error handling and the operator never sees a successful review
     * page with the dx silently dropped. Uses {@code InOrder} so an accidental
     * swap of the two lines in {@code execute()} fails this test loudly
     * rather than passing on construction-count alone.
     *
     * <p>The persister comes from the registered mock (the action's no-arg
     * ctor calls {@code SpringUtils.getBean(BillingONReviewDxPersister.class)},
     * which the {@link CarlosUnitTestBase} static intercept resolves to
     * {@link #mockDxPersister}). The assembler is intercepted via
     * {@link MockedConstruction} because the action's no-arg ctor instantiates
     * it directly via {@code new BillingONReviewDataAssembler()}.</p>
     */
    @Test
    void shouldRunPersister_beforeAssembler() throws Exception {
        try (MockedConstruction<BillingONReviewDataAssembler> assemblerMock = mockConstruction(
                BillingONReviewDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            ViewBillingONReview2Action action = new ViewBillingONReview2Action(mockSecurityInfoManager, mockDxPersister, new BillingONReviewDataAssembler());
            action.execute();

            assertThat(assemblerMock.constructed()).hasSize(1);

            BillingONReviewDataAssembler assembler = assemblerMock.constructed().get(0);
            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mockDxPersister, assembler);
            inOrder.verify(mockDxPersister).persistIfRequested(any(), any());
            inOrder.verify(assembler).assemble(any(), any());
        }
    }
}
