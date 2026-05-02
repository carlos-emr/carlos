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

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingPaymentDeletionService} — the service that
 * wraps the four-write payment-deletion sequence under
 * {@code @Transactional}.
 *
 * <p>The @Transactional rollback semantics themselves are Spring-AOP-driven
 * and can't be exercised by a plain unit test (no proxy on `new …()`). What
 * we DO verify here:
 * <ul>
 *   <li>happy path: all 4 writes happen with the right arguments</li>
 *   <li>not-found path: typed exception, no writes attempted</li>
 *   <li>format contract: ext-key values are plain decimal, not locale currency</li>
 *   <li>arg-propagation: a DAO that throws mid-sequence does NOT swallow
 *       the exception (Spring will see it and roll back)</li>
 * </ul>
 *
 * @since 2026-04-30
 */
@DisplayName("BillingPaymentDeletionService")
@Tag("unit")
@Tag("billing")
class BillingPaymentDeletionServiceUnitTest {

    private BillingONPaymentDao paymentDao;
    private BillingONCHeader1Dao headerDao;
    private BillingONExtDao extDao;
    private BillingPaymentDeletionService service;

    @BeforeEach
    void setUp() {
        paymentDao = mock(BillingONPaymentDao.class);
        headerDao = mock(BillingONCHeader1Dao.class);
        extDao = mock(BillingONExtDao.class);
        service = new BillingPaymentDeletionService(paymentDao, headerDao, extDao);
    }

    private BillingONPayment paymentWithHeader(int billingNo, int demoNo) {
        BillingONPayment p = new BillingONPayment();
        BillingONCHeader1 ch1 = new BillingONCHeader1();
        // BillingONCHeader1 has no public setId; use reflection to mimic
        // a persisted header with a generated PK.
        try {
            java.lang.reflect.Field f = BillingONCHeader1.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(ch1, billingNo);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("BillingONCHeader1.id field structure changed?", e);
        }
        ch1.setDemographicNo(demoNo);
        p.setBillingOnCheader1(ch1);
        return p;
    }

    @Test
    void shouldRebalanceHeaderAndUpdateExtKeys_onHappyPath() {
        BillingONPayment payment = paymentWithHeader(42, 7);
        when(paymentDao.find(101)).thenReturn(payment);
        when(paymentDao.getPaymentsSumByBillingNo(42)).thenReturn(new BigDecimal("80.00"));
        when(paymentDao.getPaymentsRefundByBillingNo(42)).thenReturn(new BigDecimal("10.00"));

        service.deletePayment(101);

        // 1. payment removed
        verify(paymentDao).remove(101);
        // 2. header rebalanced — paid (80) - refund.negate (-10) = 90
        BillingONCHeader1 ch1 = payment.getBillingONCheader1();
        assertThat(ch1.getPaid()).isEqualByComparingTo("90.00");
        verify(headerDao).merge(ch1);
        // 3. ext payment-key written as plain decimal "80.00" — NOT "$80.00" or "€80.00"
        verify(extDao).setExtItem(eq(42), eq(7),
                eq(BillingONExtDao.KEY_PAYMENT), eq("80.00"),
                any(Date.class), eq('1'));
        // 4. ext refund-key written negated
        verify(extDao).setExtItem(eq(42), eq(7),
                eq(BillingONExtDao.KEY_REFUND), eq("-10.00"),
                any(Date.class), eq('1'));
    }

    @Test
    void shouldThrowPaymentNotFoundException_andNotWriteWhenPaymentMissing() {
        when(paymentDao.find(99)).thenReturn(null);

        assertThatThrownBy(() -> service.deletePayment(99))
                .isInstanceOf(BillingPaymentDeletionService.PaymentNotFoundException.class)
                .hasMessageContaining("99");

        verify(paymentDao, never()).remove(anyInt());
        verify(headerDao, never()).merge(any());
        verify(extDao, never()).setExtItem(anyInt(), anyInt(), any(), any(), any(), anyChar());
    }

    @Test
    void shouldPropagateException_whenHeaderMergeFails() {
        // A mid-sequence failure must propagate so the surrounding @Transactional
        // can roll back. Pre-fix the action's plain catch(Exception) absorbed
        // this; the service itself doesn't catch.
        BillingONPayment payment = paymentWithHeader(42, 7);
        when(paymentDao.find(101)).thenReturn(payment);
        when(paymentDao.getPaymentsSumByBillingNo(42)).thenReturn(new BigDecimal("80.00"));
        when(paymentDao.getPaymentsRefundByBillingNo(42)).thenReturn(new BigDecimal("0.00"));
        doThrow(new RuntimeException("simulated DAO failure")).when(headerDao).merge(any());

        assertThatThrownBy(() -> service.deletePayment(101))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated DAO failure");

        // Payment remove DID happen (the rollback is Spring's job, not ours).
        verify(paymentDao, times(1)).remove(101);
        // Ext writes did NOT happen — exception aborted the sequence.
        verify(extDao, never()).setExtItem(anyInt(), anyInt(), any(), any(), any(), anyChar());
    }

    @Test
    void shouldFormatLargeAmount_asPlainDecimalWithoutGroupingSeparators() {
        // Pre-fix used NumberFormat.getCurrencyInstance() which adds locale-
        // dependent grouping separators ("1,234.56" or "1.234,56"). The fix
        // uses BigDecimal.toPlainString() which is guaranteed locale-neutral.
        BillingONPayment payment = paymentWithHeader(42, 7);
        when(paymentDao.find(1)).thenReturn(payment);
        when(paymentDao.getPaymentsSumByBillingNo(42)).thenReturn(new BigDecimal("12345.67"));
        when(paymentDao.getPaymentsRefundByBillingNo(42)).thenReturn(BigDecimal.ZERO);

        service.deletePayment(1);

        verify(extDao).setExtItem(eq(42), eq(7),
                eq(BillingONExtDao.KEY_PAYMENT), eq("12345.67"),
                any(Date.class), eq('1'));
    }

    @Test
    void shouldHandleZeroPaidAndZeroRefund_byWritingZeroFormatted() {
        BillingONPayment payment = paymentWithHeader(42, 7);
        when(paymentDao.find(1)).thenReturn(payment);
        when(paymentDao.getPaymentsSumByBillingNo(42)).thenReturn(BigDecimal.ZERO);
        when(paymentDao.getPaymentsRefundByBillingNo(42)).thenReturn(BigDecimal.ZERO);

        service.deletePayment(1);

        verify(extDao).setExtItem(eq(42), eq(7),
                eq(BillingONExtDao.KEY_PAYMENT), eq("0.00"),
                any(Date.class), eq('1'));
        verify(extDao).setExtItem(eq(42), eq(7),
                eq(BillingONExtDao.KEY_REFUND), eq("0.00"),
                any(Date.class), eq('1'));
    }
}
