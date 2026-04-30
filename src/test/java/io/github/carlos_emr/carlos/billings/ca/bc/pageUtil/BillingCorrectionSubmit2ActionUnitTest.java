/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@code _billing/w} privilege + the early-return contract when session
 * beans are missing on the BC correction submit boundary. The action
 * persists RecycleBin / Billing / BillingDetail rows on the happy path; the
 * invariant we care about here is that none of those DAOs are called when
 * the gate denies or the session is incomplete.
 *
 * @since 2026-04-30
 */
@DisplayName("BillingCorrectionSubmit2Action (BC)")
@Tag("unit")
@Tag("billing")
class BillingCorrectionSubmit2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingDao mockBillingDao;
    @Mock private BillingDetailDao mockBillingDetailDao;
    @Mock private RecycleBinDao mockRecycleBinDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingDao.class, mockBillingDao);
        registerMock(BillingDetailDao.class, mockBillingDetailDao);
        registerMock(RecycleBinDao.class, mockRecycleBinDao);

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
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        BillingCorrectionSubmit2Action action = new BillingCorrectionSubmit2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        // Privilege deny must short-circuit before any DAO write.
        verify(mockRecycleBinDao, never()).persist(any());
        verify(mockBillingDao, never()).merge(any());
        verify(mockBillingDetailDao, never()).persist(any());
        verify(mockBillingDetailDao, never()).merge(any());
    }

    @Test
    void shouldReturnError_whenSessionBeansMissing() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        // No billingDataBean or billing in the session — the early-return guard
        // fires and the action returns ERROR without persisting anything.

        BillingCorrectionSubmit2Action action = new BillingCorrectionSubmit2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(mockRequest.getAttribute("correctionError")).isEqualTo(Boolean.TRUE);
        verify(mockRecycleBinDao, never()).persist(any());
        verify(mockBillingDao, never()).merge(any());
        verify(mockBillingDetailDao, never()).persist(any());
    }
}
