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
        setValidCorrectionSubmitParameters();

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
        setValidCorrectionSubmitParameters();
        doThrow(new BillingValidationException("billing_date is missing or not yyyy-MM-dd"))
                .when(mockSubmissionService).submit(any(LoggedInInfo.class), any(BillingCorrectionSubmitCommand.class));

        BillingCorrectionSubmit2Action action = new BillingCorrectionSubmit2Action(
                mockSecurityInfoManager, mockSubmissionService);

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(mockRequest.getAttribute("correctionError")).isEqualTo(Boolean.TRUE);
        // The specific message must round-trip to the request so the JSP
        // can render the cause inline instead of a generic banner.
        assertThat(mockRequest.getAttribute("correctionErrorMessage"))
                .isEqualTo("billing_date is missing or not yyyy-MM-dd");
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

    private void setValidCorrectionSubmitParameters() {
        mockRequest.setParameter("billingNo", "42");
        mockRequest.setParameter("content", "<rd>Ref Doctor</rd>");
        mockRequest.setParameter("total", "3000");
        mockRequest.setParameter("hin", "1234567890");
        mockRequest.setParameter("dob", "1980-01-01");
        mockRequest.setParameter("visitType", "00");
        mockRequest.setParameter("visitDate", "2026-04-28");
        mockRequest.setParameter("status", "O");
        mockRequest.setParameter("clinicRefCode", "0000");
        mockRequest.setParameter("providerNo", "999998");
        mockRequest.setParameter("billingDate", "2026-04-28");
        mockRequest.setParameter("itemCount", "1");
        mockRequest.setParameter("serviceCode_0", "A001A");
        mockRequest.setParameter("description_0", "Minor assessment");
        mockRequest.setParameter("serviceValue_0", "2000");
        mockRequest.setParameter("diagCode_0", "250");
        mockRequest.setParameter("quantity_0", "2");
    }
}
