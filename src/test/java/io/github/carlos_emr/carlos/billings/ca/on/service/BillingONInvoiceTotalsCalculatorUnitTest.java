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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link BillingONInvoiceTotalsCalculator}. The class
 * holds the two pure-calculation methods that used to live on the deleted
 * {@code BillingONService} catch-all.
 *
 * @since 2026-04-27
 */
@DisplayName("BillingONInvoiceTotalsCalculator")
@Tag("unit")
@Tag("billing")
class BillingONInvoiceTotalsCalculatorUnitTest {

    @Nested
    @DisplayName("calculateBalanceOwing")
    class CalculateBalanceOwing {

        @Test
        void shouldReturnNull_whenInvoiceDoesNotResolve() {
            BillingONCHeader1Dao headerDao = Mockito.mock(BillingONCHeader1Dao.class);
            BillingONPaymentDao paymentDao = Mockito.mock(BillingONPaymentDao.class);
            // The DAO has overloaded find(int) and find(Object); production
            // passes Integer so the Object overload is selected. Stub via the
            // typed matcher rather than a literal int.
            // The DAO has overloaded find(int) and find(Object); production
            // passes Integer so the Object overload is selected. Stub via
            // any(Integer.class) which targets that overload.
            when(headerDao.find(org.mockito.ArgumentMatchers.any(Integer.class))).thenReturn(null);

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
            when(headerDao.find(org.mockito.ArgumentMatchers.any(Integer.class))).thenReturn(header);
            when(paymentDao.find3rdPartyPayRecordsByBill(any())).thenReturn(List.of());

            BillingONInvoiceTotalsCalculator calc =
                    new BillingONInvoiceTotalsCalculator(headerDao, paymentDao);

            // total - 0 + 0 = total
            assertThat(calc.calculateBalanceOwing(1)).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("recomputeTotal")
    class RecomputeTotal {

        @Test
        void shouldReturnEmpty_forNullHeader() {
            BillingONInvoiceTotalsCalculator calc = newCalculator();
            assertThat(calc.recomputeTotal(null)).isEmpty();
        }

        @Test
        void shouldReturnZero_whenItemsCollectionIsEmpty() {
            BillingONCHeader1 header = new BillingONCHeader1();
            // Default-constructed entity has an empty (non-null) items list.
            BillingONInvoiceTotalsCalculator calc = newCalculator();
            Optional<BigDecimal> result = calc.recomputeTotal(header);
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("0");
        }

        @Test
        void shouldReturnZero_whenNoActiveItems() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(new ArrayList<>(List.of(deletedItem("10.00"))));

            BillingONInvoiceTotalsCalculator calc = newCalculator();
            Optional<BigDecimal> result = calc.recomputeTotal(header);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("0");
        }

        @Test
        void shouldSumActiveItemFees() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(new ArrayList<>(List.of(
                    activeItem("10.00"),
                    activeItem("25.50"),
                    deletedItem("999.99")))); // skipped

            BillingONInvoiceTotalsCalculator calc = newCalculator();
            Optional<BigDecimal> result = calc.recomputeTotal(header);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("35.50");
        }

        @Test
        void shouldReturnEmpty_whenAFeeCannotBeParsed() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(new ArrayList<>(List.of(
                    activeItem("10.00"),
                    activeItem("not-a-number"))));

            BillingONInvoiceTotalsCalculator calc = newCalculator();
            assertThat(calc.recomputeTotal(header)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_whenFeeIsNull() {
            BillingONCHeader1 header = new BillingONCHeader1();
            BillingONItem item = new BillingONItem();
            item.setStatus("O");
            item.setFee(null);
            header.setBillingItems(new ArrayList<>(List.of(item)));

            BillingONInvoiceTotalsCalculator calc = newCalculator();
            // BigDecimal(null) throws NumberFormatException -> Optional.empty()
            assertThat(calc.recomputeTotal(header)).isEmpty();
        }

        private BillingONInvoiceTotalsCalculator newCalculator() {
            return new BillingONInvoiceTotalsCalculator(
                    Mockito.mock(BillingONCHeader1Dao.class),
                    Mockito.mock(BillingONPaymentDao.class));
        }

        private BillingONItem activeItem(String fee) {
            BillingONItem item = new BillingONItem();
            item.setStatus("O");
            item.setFee(fee);
            return item;
        }

        private BillingONItem deletedItem(String fee) {
            BillingONItem item = new BillingONItem();
            item.setStatus(BillingONItem.DELETED);
            item.setFee(fee);
            return item;
        }
    }
}
