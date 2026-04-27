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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link BillingONInvoiceTotalsCalculator}, which now
 * holds only the cross-DAO {@code calculateBalanceOwing} method (the
 * "sum the active items" recompute lives on {@link BillingONCHeader1} as
 * pure entity arithmetic and is covered by {@code BillingONCHeader1UnitTest}).
 *
 * @since 2026-04-27
 */
@DisplayName("BillingONInvoiceTotalsCalculator.calculateBalanceOwing")
@Tag("unit")
@Tag("billing")
class BillingONInvoiceTotalsCalculatorUnitTest {

    @Test
    void shouldReturnNull_whenInvoiceDoesNotResolve() {
        BillingONCHeader1Dao headerDao = Mockito.mock(BillingONCHeader1Dao.class);
        BillingONPaymentDao paymentDao = Mockito.mock(BillingONPaymentDao.class);
        // The DAO has overloaded find(int) and find(Object); production
        // passes Integer (auto-boxed) so the Object overload is selected.
        // Stub via any(Integer.class) which targets that overload.
        when(headerDao.find(any(Integer.class))).thenReturn(null);

        BillingONInvoiceTotalsCalculator calc =
                new BillingONInvoiceTotalsCalculator(headerDao, paymentDao);

        assertThat(calc.calculateBalanceOwing(42)).isNull();
    }

    @Test
    void shouldReturnTotal_whenNoPaymentsExist() {
        BillingONCHeader1Dao headerDao = Mockito.mock(BillingONCHeader1Dao.class);
        BillingONPaymentDao paymentDao = Mockito.mock(BillingONPaymentDao.class);
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setTotal(new BigDecimal("100.00"));
        when(headerDao.find(any(Integer.class))).thenReturn(header);
        when(paymentDao.find3rdPartyPayRecordsByBill(any())).thenReturn(List.of());

        BillingONInvoiceTotalsCalculator calc =
                new BillingONInvoiceTotalsCalculator(headerDao, paymentDao);

        // total - 0 + 0 = total
        assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("100.00");
    }
}
