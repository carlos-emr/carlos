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
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for billing-history balance DAO coordination. */
@DisplayName("BillingOnHistoryBalanceService")
@Tag("unit")
@Tag("billing")
class BillingOnHistoryBalanceServiceUnitTest {

    private final BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
    private final BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
    private final BillingOnHistoryBalanceService service =
            new BillingOnHistoryBalanceService(paymentDao, headerDao);

    @Test
    void shouldAggregateThirdPartyPayments_whenHeaderHasTotal() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setTotal(new BigDecimal("200.00"));
        when(headerDao.find(42)).thenReturn(header);
        when(paymentDao.find3rdPartyPaymentsByBillingNo(42)).thenReturn(List.of(
                payment("50.00", "10.00", "5.00"),
                payment("25.00", null, "2.50")));

        BillingOnHistoryBalanceCalculator.Result result = service.calculate("42");

        assertThat(result.partial()).isFalse();
        assertThat(result.balance()).isEqualByComparingTo("122.50");
        assertThat(result.balance().scale()).isEqualTo(2);
    }

    @Test
    void shouldReturnZero_whenHeaderTotalIsNull() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setTotal(null);
        when(headerDao.find(43)).thenReturn(header);

        BillingOnHistoryBalanceCalculator.Result result = service.calculate("43");

        assertThat(result).isEqualTo(BillingOnHistoryBalanceCalculator.Result.ZERO);
        assertThat(result.balance()).isEqualTo(new BigDecimal("0.00"));
        verify(paymentDao, never()).find3rdPartyPaymentsByBillingNo(43);
    }

    @Test
    void shouldReturnZero_whenHeaderIsMissing() {
        when(headerDao.find(44)).thenReturn(null);

        BillingOnHistoryBalanceCalculator.Result result = service.calculate("44");

        assertThat(result).isEqualTo(BillingOnHistoryBalanceCalculator.Result.ZERO);
        assertThat(result.balance()).isEqualTo(new BigDecimal("0.00"));
        verify(paymentDao, never()).find3rdPartyPaymentsByBillingNo(44);
    }

    @Test
    void shouldReturnTotal_whenHeaderHasTotalAndPaymentsAreEmpty() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setTotal(new BigDecimal("123.40"));
        when(headerDao.find(45)).thenReturn(header);
        when(paymentDao.find3rdPartyPaymentsByBillingNo(45)).thenReturn(List.of());

        BillingOnHistoryBalanceCalculator.Result result = service.calculate("45");

        assertThat(result.partial()).isFalse();
        assertThat(result.balance()).isEqualTo(new BigDecimal("123.40"));
    }

    @Test
    void shouldReturnPartialZero_whenBillingIdIsNotNumeric() {
        BillingOnHistoryBalanceCalculator.Result result = service.calculate("not-a-number");

        assertThat(result.balance()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.partial()).isTrue();
        verify(headerDao, never()).find(org.mockito.ArgumentMatchers.any());
    }

    private static BillingONPayment payment(String paid, String discount, String credit) {
        BillingONPayment payment = new BillingONPayment();
        payment.setTotal_payment(paid == null ? null : new BigDecimal(paid));
        payment.setTotal_discount(discount == null ? null : new BigDecimal(discount));
        payment.setTotal_credit(credit == null ? null : new BigDecimal(credit));
        return payment;
    }
}
