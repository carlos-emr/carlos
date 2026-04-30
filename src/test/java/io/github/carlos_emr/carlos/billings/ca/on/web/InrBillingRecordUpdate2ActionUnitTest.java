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

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionContext;
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

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InrBillingRecordUpdate2Action}. Pins
 * {@code _admin.billing/w} privilege gate, POST-only contract, and key
 * input-validation paths.
 *
 * @since 2026-04-29
 */
@DisplayName("InrBillingRecordUpdate2Action")
@Tag("unit")
@Tag("billing")
class InrBillingRecordUpdate2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingInrDao mockBillingInrDao;
    @Mock private BillingServiceDao mockBillingServiceDao;
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
        registerMock(BillingInrDao.class, mockBillingInrDao);
        registerMock(BillingServiceDao.class, mockBillingServiceDao);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        ActionContext.of().bind();
    }

    @AfterEach
    void tearDown() throws Exception {
        ActionContext.clear();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("billinginr_no", "1");

        InrBillingRecordUpdate2Action action = new InrBillingRecordUpdate2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }

    @Test
    void shouldNotMutate_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("billinginr_no", "1");

        InrBillingRecordUpdate2Action action = new InrBillingRecordUpdate2Action();

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verify(mockBillingInrDao, never()).find(anyInt());
        verify(mockBillingInrDao, never()).merge(any(BillingInr.class));
    }

    @Test
    void shouldReturnError_whenBillingInrNoMissing() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);

        InrBillingRecordUpdate2Action action = new InrBillingRecordUpdate2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        verify(mockBillingInrDao, never()).merge(any(BillingInr.class));
    }

    @Test
    void shouldReturn405_whenNotPost() throws Exception {
        mockRequest.setMethod("GET");

        InrBillingRecordUpdate2Action action = new InrBillingRecordUpdate2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        verify(mockBillingInrDao, never()).merge(any(BillingInr.class));
    }

    @Test
    void shouldNotMergeButRenderError_whenServiceCodeUnknown() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("billinginr_no", "1");
        mockRequest.setParameter("service_code", "ZZZZZ");
        mockRequest.setParameter("diag_code", "123");
        mockRequest.setParameter("inraction", "update");
        when(mockBillingServiceDao.findGst(anyString(), any())).thenReturn(Collections.emptyList());

        InrBillingRecordUpdate2Action action = new InrBillingRecordUpdate2Action();

        // Action returns SUCCESS even on validation failures (the JSP renders
        // the errorCode banner) but the merge MUST NOT have been called.
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockBillingInrDao, never()).merge(any(BillingInr.class));
        assertThat((String) mockRequest.getAttribute("errorCode"))
                .contains("Service code not found");
    }
}
