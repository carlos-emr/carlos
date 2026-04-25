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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link BillingValidationException} throw paths in
 * {@link BillingCorrection2Action#updateInvoice()} and
 * {@link BillingCorrection2Action#add3rdPartyPayment()}.
 *
 * <p>Regression armor for the audit-trail integrity guarantees: the action
 * MUST throw on validated-input failures rather than returning a silent
 * "closeReload" / "failure" result that masks write rejection as
 * page-reload-as-if-success. A future refactor that re-introduces silent
 * returns must fail these tests loudly.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("BillingCorrection2Action validation throws")
@Tag("unit")
@Tag("billing")
class BillingCorrection2ActionValidationThrowsUnitTest extends CarlosUnitTestBase {

    private BillingONCHeader1Dao bCh1Dao;
    private BillingONPaymentDao bPaymentDao;
    private BillingPaymentTypeDao billingPaymentTypeDao;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        servletActionContextMock = Mockito.mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        bCh1Dao = Mockito.mock(BillingONCHeader1Dao.class);
        bPaymentDao = Mockito.mock(BillingONPaymentDao.class);
        billingPaymentTypeDao = Mockito.mock(BillingPaymentTypeDao.class);

        registerMock(SecurityInfoManager.class, Mockito.mock(SecurityInfoManager.class));
        registerMock(BillingONCHeader1Dao.class, bCh1Dao);
        registerMock(BillingONPaymentDao.class, bPaymentDao);
        registerMock(BillingONExtDao.class, Mockito.mock(BillingONExtDao.class));
        registerMock(BillingPaymentTypeDao.class, billingPaymentTypeDao);
        registerMock(BillingServiceDao.class, Mockito.mock(BillingServiceDao.class));
        registerMock(ProviderDao.class, Mockito.mock(ProviderDao.class));
        registerMock(ProviderSiteDao.class, Mockito.mock(ProviderSiteDao.class));
        registerMock(SiteDao.class, Mockito.mock(SiteDao.class));
    }

    @AfterEach
    void tearDownServletMock() {
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void updateInvoice_shouldThrowBillingValidationException_whenBillingNoIsNonNumeric() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        // Non-empty, non-null but unparseable: indicates tampering / browser
        // auto-fill regression — throw rather than silent "closeReload".
        mockRequest.setParameter("xml_billing_no", "not-a-number");

        assertThatThrownBy(action::updateInvoice)
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("invalid bill identifier");

        // Audit-trail integrity: the bill record was never fetched.
        verify(bCh1Dao, never()).find(Mockito.anyInt());
    }

    @Test
    void updateInvoice_shouldThrowBillingValidationException_whenBillNotFound() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("xml_billing_no", "999999");
        when(bCh1Dao.find(Integer.valueOf(999999))).thenReturn(null);

        assertThatThrownBy(action::updateInvoice)
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("not found");
    }

    /**
     * The action funnels every non-add3rdPartyPayment request through
     * updateInvoice(), including the GET-load path that opens the correction
     * page (which posts no xml_billing_no). That path returns the dedicated
     * "loadOnly" result, distinct from the post-save "closeReload" path so
     * the result vocabulary stays semantically honest. Both map to the same
     * JSP, so the user-facing render is identical.
     */
    @Test
    void updateInvoice_shouldReturnLoadOnly_whenXmlBillingNoIsAbsent() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        // No xml_billing_no parameter at all (the GET-load case).

        String result = action.updateInvoice();

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("loadOnly");
        verify(bCh1Dao, never()).find(Mockito.anyInt());
    }

    @Test
    void updateInvoice_shouldReturnLoadOnly_whenXmlBillingNoIsEmpty() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("xml_billing_no", "");

        String result = action.updateInvoice();

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("loadOnly");
        verify(bCh1Dao, never()).find(Mockito.anyInt());
    }

    @Test
    void add3rdPartyPayment_shouldThrowBillingValidationException_whenBillingNoIsNonNumeric() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("billing_no", "not-numeric");
        LoggedInInfo info = Mockito.mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<LoggedInInfo> liiMock = Mockito.mockStatic(LoggedInInfo.class)) {
            liiMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                    Mockito.any(HttpServletRequest.class))).thenReturn(info);

            assertThatThrownBy(action::add3rdPartyPayment)
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("invalid billing_no");

            verify(bPaymentDao, never()).createPayment(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    @Test
    void add3rdPartyPayment_shouldThrowBillingValidationException_whenBillNotFound() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("billing_no", "12345");
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(null);
        LoggedInInfo info = Mockito.mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<LoggedInInfo> liiMock = Mockito.mockStatic(LoggedInInfo.class)) {
            liiMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                    Mockito.any(HttpServletRequest.class))).thenReturn(info);

            assertThatThrownBy(action::add3rdPartyPayment)
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("bill not found");
        }
    }

    @Test
    void add3rdPartyPayment_shouldThrowBillingValidationException_whenAmtPaidIsNonNumeric() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("billing_no", "12345");
        mockRequest.setParameter("amtPaid", "not-a-number");
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);
        LoggedInInfo info = Mockito.mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<LoggedInInfo> liiMock = Mockito.mockStatic(LoggedInInfo.class)) {
            liiMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                    Mockito.any(HttpServletRequest.class))).thenReturn(info);

            assertThatThrownBy(action::add3rdPartyPayment)
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("not a valid number");

            verify(bPaymentDao, never()).createPayment(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    @Test
    void add3rdPartyPayment_shouldThrowBillingValidationException_whenAmtPaidIsMissing() {
        // Missing 'amtPaid' would previously hit `new BigDecimal(null)` →
        // NullPointerException → uncaught 500 (the catch only covered
        // NumberFormatException). Verify the explicit null/empty pre-check
        // routes the failure to the validation page instead.
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("billing_no", "12345");
        // intentionally do not set 'amtPaid'
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);
        LoggedInInfo info = Mockito.mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<LoggedInInfo> liiMock = Mockito.mockStatic(LoggedInInfo.class)) {
            liiMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                    Mockito.any(HttpServletRequest.class))).thenReturn(info);

            assertThatThrownBy(action::add3rdPartyPayment)
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("amount is missing");

            verify(bPaymentDao, never()).createPayment(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    @Test
    void add3rdPartyPayment_shouldThrowBillingValidationException_whenPayMethodNotConfigured() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("billing_no", "12345");
        mockRequest.setParameter("amtPaid", "100.00");
        mockRequest.setParameter("payMethod", "UNKNOWN");
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);
        when(billingPaymentTypeDao.find("UNKNOWN")).thenReturn(null);
        LoggedInInfo info = Mockito.mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<LoggedInInfo> liiMock = Mockito.mockStatic(LoggedInInfo.class)) {
            liiMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                    Mockito.any(HttpServletRequest.class))).thenReturn(info);

            assertThatThrownBy(action::add3rdPartyPayment)
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("not configured");

            verify(bPaymentDao, never()).createPayment(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    @Test
    void add3rdPartyPayment_shouldThrowBillingValidationException_whenPayTypeIsNotPorR() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        mockRequest.setParameter("billing_no", "12345");
        mockRequest.setParameter("amtPaid", "100.00");
        mockRequest.setParameter("payMethod", "CASH");
        mockRequest.setParameter("payType", "X");
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);
        when(billingPaymentTypeDao.find("CASH")).thenReturn(
                Mockito.mock(io.github.carlos_emr.carlos.commn.model.BillingPaymentType.class));
        LoggedInInfo info = Mockito.mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<LoggedInInfo> liiMock = Mockito.mockStatic(LoggedInInfo.class)) {
            liiMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                    Mockito.any(HttpServletRequest.class))).thenReturn(info);

            assertThatThrownBy(action::add3rdPartyPayment)
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("must be P (payment) or R (refund)");

            verify(bPaymentDao, never()).createPayment(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }
}
