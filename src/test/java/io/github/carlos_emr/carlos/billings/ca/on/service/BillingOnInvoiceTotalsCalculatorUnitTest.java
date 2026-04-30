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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link BillingOnInvoiceTotalsCalculator}, which now
 * holds only the cross-DAO {@code calculateBalanceOwing} method (the
 * "sum the active items" recompute lives on {@link BillingONCHeader1} as
 * pure entity arithmetic and is covered by {@code BillingONCHeader1UnitTest}).
 *
 * @since 2026-04-27
 */
@DisplayName("BillingOnInvoiceTotalsCalculator.calculateBalanceOwing")
@Tag("unit")
@Tag("billing")
class BillingOnInvoiceTotalsCalculatorUnitTest {

    private BillingONCHeader1Dao headerDao;
    private BillingONPaymentDao paymentDao;
    private BillingOnInvoiceTotalsCalculator calc;

    @BeforeEach
    void setUp() {
        headerDao = Mockito.mock(BillingONCHeader1Dao.class);
        paymentDao = Mockito.mock(BillingONPaymentDao.class);
        calc = new BillingOnInvoiceTotalsCalculator(headerDao, paymentDao);
    }

    private BillingONPayment payment(String paid, String refund) {
        BillingONPayment p = new BillingONPayment();
        p.setTotal_payment(new BigDecimal(paid));
        p.setTotal_refund(new BigDecimal(refund));
        return p;
    }

    private void stubInvoice(BigDecimal total, BillingONPayment... payments) {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setTotal(total);
        when(headerDao.find(any(Integer.class))).thenReturn(header);
        when(paymentDao.find3rdPartyPayRecordsByBill(any())).thenReturn(List.of(payments));
    }

    @Test
    void shouldReturnNull_whenInvoiceDoesNotResolve() {
        // The DAO has overloaded find(int) and find(Object); production
        // passes Integer (auto-boxed) so the Object overload is selected.
        when(headerDao.find(any(Integer.class))).thenReturn(null);

        assertThat(calc.calculateBalanceOwing(42)).isNull();
    }

    @Test
    void shouldReturnTotal_whenNoPaymentsExist() {
        stubInvoice(new BigDecimal("100.00"));

        // total - 0 + 0 = total
        assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldReturnZero_whenPaidExactlyMatchesTotal() {
        stubInvoice(new BigDecimal("100.00"),
                payment("100.00", "0.00"));

        assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldReturnNegative_whenPaidExceedsTotal() {
        // Operator overpaid (e.g. recorded twice). The calculator surfaces
        // the negative balance rather than clamping — caller decides.
        stubInvoice(new BigDecimal("100.00"),
                payment("150.00", "0.00"));

        assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("-50.00");
    }

    @Test
    void shouldAddRefundsBackToBalance_whenRefundIssued() {
        // total=100, paid=100, refund=30 → balance = 100 - 100 + 30 = 30 owing again.
        stubInvoice(new BigDecimal("100.00"),
                payment("100.00", "30.00"));

        assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldSumAcrossMultiplePayments() {
        // total=200, three partial payments + one refund.
        stubInvoice(new BigDecimal("200.00"),
                payment("50.00", "0.00"),
                payment("75.00", "0.00"),
                payment("60.00", "10.00"));

        // 200 - (50 + 75 + 60) + (0 + 0 + 10) = 200 - 185 + 10 = 25
        assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldPreserveScale_acrossHighPrecisionInputs() {
        // The DB column is 4-decimal; calculator must not round mid-flight
        // (caller chooses display rounding).
        stubInvoice(new BigDecimal("100.0001"),
                payment("33.3333", "0.0000"),
                payment("33.3333", "0.0000"),
                payment("33.3333", "0.0000"));

        // 100.0001 - 99.9999 + 0 = 0.0002
        assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("0.0002");
    }

    @Test
    void shouldThrowNullPointer_whenHeaderTotalIsNull() {
        // Pins the gap noted in the silent-failure review: a header with
        // null `total` (corrupt RA-import row) NPEs on subtract. The fix
        // belongs upstream; entity setters validate explicit writes, but
        // null can still slip through field access.
        // This test documents the current behavior so a future fix has a
        // failing-spec to flip.
        BillingONCHeader1 header = new BillingONCHeader1();
        // header.total intentionally left null
        when(headerDao.find(any(Integer.class))).thenReturn(header);
        when(paymentDao.find3rdPartyPayRecordsByBill(any())).thenReturn(List.of());

        assertThatThrownBy(() -> calc.calculateBalanceOwing(1))
                .isInstanceOf(NullPointerException.class);
    }
}
