/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitCommand;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionSubmissionService;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@code _billing/w} privilege + POST-only on the submit boundary,
 * and verifies that a {@link BillingValidationException} from the service
 * is converted to {@code ERROR} (not propagated) so the JSP can render the
 * error banner.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingCorrectionSubmit2Action")
@Tag("unit")
@Tag("billing")
class BillingCorrectionSubmit2ActionUnitTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingCorrectionSubmissionService mockSubmissionService;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");
        // itemCount=0 → empty items list, avoids the parser branch.
        mockRequest.setParameter("itemCount", "0");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldDelegateAndReturnSuccess_whenPrivilegeGrantedAndPost() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);

        BillingCorrectionSubmit2Action action = new BillingCorrectionSubmit2Action(
                mockSecurityInfoManager, mockSubmissionService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockSubmissionService).submit(eq(mockLoggedInInfo), any(BillingCorrectionSubmitCommand.class));
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        BillingCorrectionSubmit2Action action = new BillingCorrectionSubmit2Action(
                mockSecurityInfoManager, mockSubmissionService);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
        verify(mockSubmissionService, never()).submit(any(LoggedInInfo.class), any(BillingCorrectionSubmitCommand.class));
    }

    @Test
    void shouldReturnError_whenServiceThrowsValidationException() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        doThrow(new BillingValidationException("bad"))
                .when(mockSubmissionService).submit(any(LoggedInInfo.class), any(BillingCorrectionSubmitCommand.class));

        BillingCorrectionSubmit2Action action = new BillingCorrectionSubmit2Action(
                mockSecurityInfoManager, mockSubmissionService);

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(mockRequest.getAttribute("correctionError")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldReturn405_whenNotPost() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("GET");

        BillingCorrectionSubmit2Action action = new BillingCorrectionSubmit2Action(
                mockSecurityInfoManager, mockSubmissionService);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(mockSubmissionService, never()).submit(any(LoggedInInfo.class), any(BillingCorrectionSubmitCommand.class));
    }
}
