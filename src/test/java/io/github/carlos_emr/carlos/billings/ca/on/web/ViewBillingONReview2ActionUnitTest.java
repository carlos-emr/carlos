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

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONReviewViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingONReviewViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONReviewDxPersister;
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
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

    @Mock
    private BillingONReviewViewModelAssembler mockAssembler;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private static final BillingONReviewViewModel STUB_MODEL =
            BillingONReviewViewModel.builder().dxCode("401").build();

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockAssembler.assemble(any(), any())).thenReturn(STUB_MODEL);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private ViewBillingONReview2Action newAction() {
        return new ViewBillingONReview2Action(mockSecurityInfoManager, mockDxPersister, mockAssembler);
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedPostRequest() throws Exception {
        ViewBillingONReview2Action action = newAction();
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getReviewModel()).isSameAs(STUB_MODEL);
    }

    @Test
    void shouldRejectGet_with405() throws Exception {
        mockRequest.setMethod("GET");

        ViewBillingONReview2Action action = newAction();
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
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

        ViewBillingONReview2Action action = newAction();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("session");
    }

    @Test
    void shouldThrowSecurityException_whenLacksBillingWrite() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        ViewBillingONReview2Action action = newAction();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldExposeModel_asRequestAttribute() throws Exception {
        ViewBillingONReview2Action action = newAction();
        action.execute();
        assertThat(mockRequest.getAttribute("reviewModel")).isSameAs(STUB_MODEL);
    }

    /**
     * Regression armor: the persister runs BEFORE the assembler so any audit
     * failure (non-numeric demoNo, DAO outage) propagates through the action's
     * standard error handling and the operator never sees a successful review
     * page with the dx silently dropped. Uses {@code InOrder} so an accidental
     * swap of the two lines in {@code execute()} fails this test loudly.
     */
    @Test
    void shouldRunPersister_beforeAssembler() throws Exception {
        ViewBillingONReview2Action action = newAction();
        action.execute();

        InOrder ordering = inOrder(mockDxPersister, mockAssembler);
        ordering.verify(mockDxPersister).persistIfRequested(any(), any());
        ordering.verify(mockAssembler).assemble(any(), any());
    }
}
