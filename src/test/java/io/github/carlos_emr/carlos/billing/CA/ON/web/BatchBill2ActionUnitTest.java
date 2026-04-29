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
package io.github.carlos_emr.carlos.billing.CA.ON.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONHeaderCreationService;
import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the batch-billing mutation paths.
 *
 * @since 2026-04-27
 */
@DisplayName("BatchBill2Action")
@Tag("unit")
@Tag("billing")
class BatchBill2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private BillingONHeaderCreationService headerCreationService;

    @Mock
    private BatchBillingDAO batchBillingDAO;

    @Mock
    private io.github.carlos_emr.carlos.billing.CA.ON.web.BatchBillingViewModelAssembler batchBillingAssembler;

    @Mock
    private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("user", "999998");
        request.setParameter("BillDate", "2026-04-27");
        request.setParameter("clinic_view", "clinic-a");
        request.setParameter("providers", "999998");
        request.setParameter("service_code", "A007A");

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(BillingONHeaderCreationService.class, headerCreationService);
        registerMock(BatchBillingDAO.class, batchBillingDAO);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(headerCreationService.createBill(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("12.34");

        BatchBilling row = new BatchBilling();
        row.setId(77);
        when(batchBillingDAO.find(42, "A007A")).thenReturn(List.of(row));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldRejectDoBatchBill_whenRequestIsNotPost() throws Exception {
        request.setMethod("GET");
        request.setParameter("method", "doBatchBill");
        request.setParameter("bill", "A007A;250;42;999998");

        String result = new BatchBill2Action(headerCreationService, securityInfoManager, batchBillingAssembler).execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(headerCreationService, never())
                .createBill(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldCreateBillUsingFourFieldCheckboxSchema_whenBatchSubmitted() {
        request.setMethod("POST");
        request.setParameter("bill", "A007A;250;42;999998");

        String result = new BatchBill2Action(headerCreationService, securityInfoManager, batchBillingAssembler).doBatchBill();

        assertThat(result).isNull();
        verify(headerCreationService)
                .createBill(eq("999998"), eq(42), eq("A007A"), eq("250"), eq("clinic-a"), any(), eq("999998"));
        verify(batchBillingDAO).find(42, "A007A");
    }

    @Test
    void shouldRejectLegacyThreeFieldCheckboxSchema_beforeCreatingAnyBill() {
        request.setMethod("POST");
        request.setParameter("bill", "A007A;42;999998");

        String result = new BatchBill2Action(headerCreationService, securityInfoManager, batchBillingAssembler).doBatchBill();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        verify(headerCreationService, never())
                .createBill(any(), any(), any(), any(), any(), any(), any());
    }
}
