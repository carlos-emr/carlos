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

import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreatePaymentType2Action}. Verifies the JSON
 * contract ({@code ret: 0} on success, {@code ret: 1} on duplicate /
 * empty input) and the privilege gate.
 *
 * @since 2026-04-27
 */
@DisplayName("CreatePaymentType2Action")
@Tag("unit")
@Tag("billing")
class CreatePaymentType2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private BillingPaymentTypeDao mockDao;

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

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldPersistAndReturnRet0_onUniqueName() throws Exception {
        mockRequest.setParameter("paymentType", "CRYPTO");
        when(mockDao.getPaymentTypeByName("CRYPTO")).thenReturn(null);

        CreatePaymentType2Action action = new CreatePaymentType2Action(mockSecurityInfoManager, mockDao);
        assertThat(action.execute()).isNull(); // null = bypass result rendering, JSON body already on wire

        verify(mockDao, times(1)).persist(any(BillingPaymentType.class));
        assertThat(mockResponse.getContentAsString()).contains("\"ret\":\"0\"");
    }

    @Test
    void shouldRefuseDuplicate_andReturnRet1() throws Exception {
        mockRequest.setParameter("paymentType", "CASH");
        BillingPaymentType existing = new BillingPaymentType();
        existing.setPaymentType("CASH");
        when(mockDao.getPaymentTypeByName("CASH")).thenReturn(existing);

        CreatePaymentType2Action action = new CreatePaymentType2Action(mockSecurityInfoManager, mockDao);
        action.execute();

        verify(mockDao, never()).persist(any(BillingPaymentType.class));
        assertThat(mockResponse.getContentAsString()).contains("\"ret\":\"1\"");
        assertThat(mockResponse.getContentAsString()).contains("already exists");
    }

    @Test
    void shouldNoOp_whenPaymentTypeParamMissing() throws Exception {
        // No paymentType param.
        CreatePaymentType2Action action = new CreatePaymentType2Action(mockSecurityInfoManager, mockDao);
        action.execute();

        verify(mockDao, never()).persist(any(BillingPaymentType.class));
        // No JSON body written.
        assertThat(mockResponse.getContentAsString()).isEmpty();
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        CreatePaymentType2Action action = new CreatePaymentType2Action(mockSecurityInfoManager, mockDao);
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }
}
