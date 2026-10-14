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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regression coverage for Ministry invoice settlement through Billing Correction. */
@Tag("unit")
@Tag("billing")
class BillingCorrectionSettlementUnitTest extends CarlosUnitTestBase {
    private final BillingONCHeader1Dao headers = mock(BillingONCHeader1Dao.class);
    private final BillingONPaymentDao payments = mock(BillingONPaymentDao.class);
    private final BillingONExtDao extensions = mock(BillingONExtDao.class);
    private final BillingONRepoDao audit = mock(BillingONRepoDao.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final LoggedInInfo operator = mock(LoggedInInfo.class);
    private final BillingCorrectionService service = new BillingCorrectionService(payments, headers, extensions,
            mock(BillingPaymentTypeDao.class), audit, providers, mock(BillingServiceDao.class));
    private BillingONCHeader1 bill;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        bill = new BillingONCHeader1();
        org.springframework.test.util.ReflectionTestUtils.setField(bill, "id", 42);
        bill.setStatus("O");
        bill.setProviderNo("999998");
        bill.setDemographicNo(100);
        bill.setTotal(new BigDecimal("50.00"));
        bill.setPaid(BigDecimal.ZERO);
        bill.setBillingDate(new Date());
        bill.setAdmissionDate(null);
        when(headers.findForUpdate(42)).thenReturn(bill);
        when(headers.findWithItems(42)).thenReturn(bill);
        when(operator.getLoggedInProviderNo()).thenReturn("999998");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setOhipNo("123456");
        provider.setRmaNo("0001");
        when(providers.getProvider("999998")).thenReturn(provider);
        request = new MockHttpServletRequest();
        request.setPreferredLocales(List.of(Locale.CANADA));
        request.setParameter("xml_billing_no", "42");
        request.setParameter("status", "S");
        request.setParameter("oldStatus", "O");
        request.setParameter("payProgram", "HCP");
        request.setParameter("xml_appointment_date", "2026-03-04");
        request.setParameter("xml_vdate", "");
        request.setParameter("rdohip", "");
        request.setParameter("visittype", "00");
        request.setParameter("clinic_ref_code", "0001");
        request.setParameter("provider_no", "999998");
        request.setParameter("comment", "");
        request.setParameter("site", "");
        request.setParameter("hc_type", "ON");
        request.setParameter("xml_slicode", "P1");
        request.setParameter("submit", "Save");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HCP", "RMB", "WCB"})
    void shouldRecordOutstandingAmount_whenMinistryInvoiceIsSettled(String program) {
        bill.setPayProgram(program);
        request.setParameter("payProgram", program);
        bill.setPaid(new BigDecimal("12.50"));
        Date before = new Date();

        assertThat(service.updateInvoice(operator, request)).isEqualTo("submitClose");

        ArgumentCaptor<BillingONPayment> saved = ArgumentCaptor.forClass(BillingONPayment.class);
        verify(payments).persist(saved.capture());
        assertThat(saved.getValue().getBillingNo()).isEqualTo(42);
        assertThat(saved.getValue().getBillingONCheader1()).isSameAs(bill);
        assertThat(saved.getValue().getTotal_payment()).isEqualByComparingTo("37.50");
        assertThat(saved.getValue().getCreator()).isEqualTo("999998");
        assertThat(saved.getValue().getPaymentDate()).isAfterOrEqualTo(before).isBeforeOrEqualTo(new Date());
        assertThat(bill.getPaid()).isEqualByComparingTo("50.00");
        assertThat(bill.getStatus()).isEqualTo("S");
        verify(headers).findForUpdate(42);
    }

    @Test
    void shouldNotDuplicatePayment_whenSettledInvoiceIsSavedAgain() {
        service.updateInvoice(operator, request);
        service.updateInvoice(operator, request);
        verify(payments, times(1)).persist(any(BillingONPayment.class));
        assertThat(bill.getPaid()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldNotAddPayment_whenBalanceIsAlreadyPaid() {
        bill.setPaid(new BigDecimal("50.00"));
        service.updateInvoice(operator, request);
        verify(payments, never()).persist(any(BillingONPayment.class));
        assertThat(bill.getPaid()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldNotAddPayment_whenStatusIsNotSettled() {
        request.setParameter("status", "B");
        service.updateInvoice(operator, request);
        verifyNoInteractions(payments);
        assertThat(bill.getPaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldRejectSettlement_whenPaidAmountExceedsTotal() {
        bill.setPaid(new BigDecimal("51.00"));
        assertThatThrownBy(() -> service.updateInvoice(operator, request))
                .isInstanceOf(BillingValidationException.class);
        verify(payments, never()).persist(any(BillingONPayment.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"total", "paid"})
    void shouldRejectSettlement_whenBalanceIsMissing(String field) {
        if (field.equals("total")) bill.setTotal(null);
        else bill.setPaid(null);
        assertThatThrownBy(() -> service.updateInvoice(operator, request))
                .isInstanceOf(BillingValidationException.class).hasMessageContaining("missing invoice balance");
        verify(payments, never()).persist(any(BillingONPayment.class));
    }

    @Test
    void shouldPropagateFailure_whenPaymentCannotBePersisted() {
        doThrow(new IllegalStateException("synthetic database failure"))
                .when(payments).persist(any(BillingONPayment.class));
        assertThatThrownBy(() -> service.updateInvoice(operator, request))
                .isInstanceOf(IllegalStateException.class).hasMessage("synthetic database failure");
    }

    @Test
    void shouldKeepThirdPartySettlement_whenInvoiceIsPatientBilled() {
        bill.setPayProgram("PAT");
        request.setParameter("payProgram", "PAT");
        request.setParameter("oldStatus", "thirdParty");
        when(payments.find3rdPartyPayRecordsByBill(bill)).thenReturn(List.of());
        service.updateInvoice(operator, request);
        verify(payments).createPayment(eq(bill), any(), eq("P"), eq(new BigDecimal("50.00")), eq(""), eq("999998"));
        verify(payments, never()).persist(any(BillingONPayment.class));
    }
}
