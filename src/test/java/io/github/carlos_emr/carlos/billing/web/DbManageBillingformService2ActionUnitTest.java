/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billing.web;

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

@DisplayName("DbManageBillingformService2Action transactional replacement")
@Tag("unit")
@Tag("billing")
class DbManageBillingformService2ActionUnitTest extends CarlosUnitTestBase {

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
        request.setMethod("POST");
        request.setContextPath("/carlos");
        request.setParameter("typeid", "42");
        request.setParameter("type", "Family Medicine");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should reject invalid order before transactional replacement")
    void shouldRejectInvalidOrder_beforeTransactionalReplacement() throws Exception {
        request.setParameter("group1", "Common Codes");
        request.setParameter("group1_service0", "A007A");
        request.setParameter("group1_service0_order", "1");
        request.setParameter("group1_service1", "K005A");
        request.setParameter("group1_service1_order", "bad");

        String result = new DbManageBillingformService2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verify(billingFormConfigurationService, never()).replaceServiceCodes(any(), any());
    }

    @Test
    @DisplayName("should delegate complete replacement to transactional service")
    void shouldDelegateCompleteReplacement_toTransactionalService() throws Exception {
        request.setParameter("group1", "Common Codes");
        request.setParameter("group1_service0", "A007A");
        request.setParameter("group1_service0_order", "1");
        request.setParameter("group1_service1", "K005A");
        request.setParameter("group1_service1_order", "2");

        String result = new DbManageBillingformService2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/billing/CA/ON/ManageBillingform");
        verify(billingFormConfigurationService).replaceServiceCodes(eq("42"), any(List.class));
    }
}
