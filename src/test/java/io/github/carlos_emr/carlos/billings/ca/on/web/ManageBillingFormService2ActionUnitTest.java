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

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFormConfigurationService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the round-7 S1 contract on
 * {@link ManageBillingFormService2Action}: a fat-fingered service-order
 * field must abort the entire save with an action error, not silently
 * persist {@code serviceOrder=0} across every code in the affected group.
 * Without this, the form's "Save" silently corrupts ordering with no
 * operator-visible signal.
 */
@DisplayName("ManageBillingFormService2Action orderParseFailures guard")
@Tag("unit")
@Tag("billing")
class ManageBillingFormService2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private SecurityInfoManager securityInfoManager;
    private BillingFormConfigurationService billingFormConfigurationService;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        billingFormConfigurationService = mock(BillingFormConfigurationService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(BillingFormConfigurationService.class, billingFormConfigurationService);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("typeid", "42");
        request.setParameter("type", "Family Medicine");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        // ActionContext binding lets ActionSupport.addActionError /
        // getText work in this unit test — without it the round-7
        // orderParseFailures path NPEs at the addActionError call.
        org.apache.struts2.ActionContext.of()
                .withServletRequest(request)
                .withServletResponse(response)
                .bind();

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldReturnError_andAbortReplaceServiceCodes_whenAnyOrderParamIsNonNumeric() throws Exception {
        // Two service codes in group1: one with a clean order, one with
        // a non-numeric (fat-fingered comma) order. Pre-fix the bad row
        // silently defaulted to 0 and persisted; post-fix the entire
        // save must abort BEFORE replaceServiceCodes is invoked.
        request.setParameter("group1", "Common Codes");
        request.setParameter("group1_service0", "A007A");
        request.setParameter("group1_service0_order", "1");
        request.setParameter("group1_service1", "K005A");
        request.setParameter("group1_service1_order", "1,2");  // non-numeric

        ManageBillingFormService2Action action = org.mockito.Mockito.spy(new ManageBillingFormService2Action());
        org.mockito.Mockito.doReturn("Order parse failed: group1_service1=1,2")
                .when(action).getText(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(String[].class));
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(action.getActionErrors())
                .as("operator must see a banner naming the offending field")
                .isNotEmpty();
        verify(billingFormConfigurationService, never())
                .replaceServiceCodes(any(), any());
    }

    @Test
    void shouldRedirectToManageBillingform_whenAllOrdersParseCleanly() throws Exception {
        // Happy path: every order parses; replaceServiceCodes is invoked
        // and the response redirects.
        request.setParameter("group1", "Common Codes");
        request.setParameter("group1_service0", "A007A");
        request.setParameter("group1_service0_order", "1");
        request.setParameter("group1_service1", "K005A");
        request.setParameter("group1_service1_order", "2");

        ManageBillingFormService2Action action = new ManageBillingFormService2Action();
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(action.getActionErrors()).isEmpty();
        verify(billingFormConfigurationService).replaceServiceCodes(eq("42"), any(List.class));
        assertThat(response.getRedirectedUrl()).contains("ManageBillingform");
    }

    @Test
    void shouldNotPersistRowsBuiltBeforeFailure_whenLastOrderParamIsBad() throws Exception {
        // Even when the bad row is the LAST one in the group, the action
        // must not have called replaceServiceCodes with a partial list —
        // collecting failures and aborting before the call is what makes
        // the save atomic from the operator's perspective.
        request.setParameter("group1", "Common Codes");
        request.setParameter("group1_service0", "A007A");
        request.setParameter("group1_service0_order", "1");
        request.setParameter("group1_service1", "K005A");
        request.setParameter("group1_service1_order", "abc");

        ManageBillingFormService2Action action = org.mockito.Mockito.spy(new ManageBillingFormService2Action());
        org.mockito.Mockito.doReturn("Order parse failed")
                .when(action).getText(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(String[].class));
        action.execute();

        verify(billingFormConfigurationService, never())
                .replaceServiceCodes(any(), any());
    }

    /** Mark unused — tests assert behavior, not used directly. */
    @SuppressWarnings("unused")
    private CtlBillingService unused;
}
