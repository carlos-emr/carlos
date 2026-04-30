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
package io.github.carlos_emr.carlos.commn.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pure-domain methods on {@link BillingONCHeader1}. These
 * methods previously lived on the deleted {@code BillingONService} catch-all
 * and on {@code BillingOnInvoiceTotalsCalculator}; they belong on the entity
 * because they reason about its own state with no DAO calls.
 *
 * @since 2026-04-27
 */
@DisplayName("BillingONCHeader1 (domain methods)")
@Tag("unit")
@Tag("billing")
class BillingONCHeader1UnitTest {

    @Nested
    @DisplayName("setTotal")
    class SetTotal {

        @Test
        void shouldAcceptZero() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(BigDecimal.ZERO);
            assertThat(header.getTotal()).isEqualByComparingTo("0");
        }

        @Test
        void shouldAcceptPositiveValues() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(new BigDecimal("125.50"));
            assertThat(header.getTotal()).isEqualByComparingTo("125.50");
        }

        @Test
        void shouldAcceptNull() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(null);
            assertThat(header.getTotal()).isNull();
        }

        @Test
        void shouldRejectNegativeValues() {
            BillingONCHeader1 header = new BillingONCHeader1();

            assertThatThrownBy(() -> header.setTotal(new BigDecimal("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("BillingONCHeader1 total cannot be negative");
        }
    }

    @Nested
    @DisplayName("isOhipBill")
    class IsOhipBill {

        @Test
        void shouldReturnTrue_forHcpPayProgram() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setPayProgram("HCP");
            assertThat(header.isOhipBill()).isTrue();
        }

        @Test
        void shouldReturnFalse_forRmbReciprocalBilling() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setPayProgram("RMB");
            assertThat(header.isOhipBill()).isFalse();
        }

        @Test
        void shouldReturnFalse_forNullPayProgram() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setPayProgram(null);
            assertThat(header.isOhipBill()).isFalse();
        }
    }

    @Nested
    @DisplayName("status checks (isSettled/isActive/isDeleted/markSettled)")
    class StatusChecks {

        @Test
        void shouldReportSettled_whenStatusIsS() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setStatus(BillingONCHeader1.SETTLED);
            assertThat(header.isSettled()).isTrue();
            assertThat(header.isActive()).isTrue();
            assertThat(header.isDeleted()).isFalse();
        }

        @Test
        void shouldReportDeleted_whenStatusIsD() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setStatus(BillingONCHeader1.DELETED);
            assertThat(header.isDeleted()).isTrue();
            assertThat(header.isActive()).isFalse();
            assertThat(header.isSettled()).isFalse();
        }

        @Test
        void shouldTreatOpenStatusAsActive_butNotSettled() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setStatus("O");
            assertThat(header.isActive()).isTrue();
            assertThat(header.isSettled()).isFalse();
            assertThat(header.isDeleted()).isFalse();
        }

        @Test
        void shouldTreatNullStatusAsActive_butNotSettled() {
            BillingONCHeader1 header = new BillingONCHeader1();
            // status defaults to null
            assertThat(header.isActive()).isTrue();
            assertThat(header.isSettled()).isFalse();
            assertThat(header.isDeleted()).isFalse();
        }

        @Test
        void shouldFlipStatusToSettled_onMarkSettled() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setStatus("O");
            header.markSettled();
            assertThat(header.getStatus()).isEqualTo(BillingONCHeader1.SETTLED);
            assertThat(header.isSettled()).isTrue();
        }
    }

    @Nested
    @DisplayName("isPaidInFull")
    class IsPaidInFull {

        @Test
        void shouldReturnTrue_whenPaidEqualsTotal() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(new BigDecimal("50.00"));
            assertThat(header.isPaidInFull(new BigDecimal("50.00"))).isTrue();
        }

        @Test
        void shouldReturnTrue_whenPaidExceedsTotal() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(new BigDecimal("50.00"));
            assertThat(header.isPaidInFull(new BigDecimal("75.00"))).isTrue();
        }

        @Test
        void shouldReturnFalse_whenPaidLessThanTotal() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(new BigDecimal("50.00"));
            assertThat(header.isPaidInFull(new BigDecimal("49.99"))).isFalse();
        }

        @Test
        void shouldReturnFalse_whenTotalIsNull() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(null);
            assertThat(header.isPaidInFull(new BigDecimal("10.00"))).isFalse();
        }

        @Test
        void shouldReturnFalse_whenPaidIsNull() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setTotal(new BigDecimal("50.00"));
            assertThat(header.isPaidInFull(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("recomputeTotalFromItems")
    class RecomputeTotalFromItems {

        @Test
        void shouldReturnZero_whenItemsCollectionIsEmpty() {
            BillingONCHeader1 header = new BillingONCHeader1();
            // Default-constructed entity has an empty (non-null) items list.
            Optional<BigDecimal> result = header.recomputeTotalFromItems();
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("0");
        }

        @Test
        void shouldReturnZero_whenAllItemsAreDeleted() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(new ArrayList<>(List.of(deletedItem("10.00"))));

            Optional<BigDecimal> result = header.recomputeTotalFromItems();

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("0");
        }

        @Test
        void shouldSumActiveItemFees_andSkipDeletedOnes() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(new ArrayList<>(List.of(
                    activeItem("10.00"),
                    activeItem("25.50"),
                    deletedItem("999.99")))); // skipped

            Optional<BigDecimal> result = header.recomputeTotalFromItems();

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("35.50");
        }

        @Test
        void shouldReturnEmpty_whenAFeeCannotBeParsed() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(new ArrayList<>(List.of(
                    activeItem("10.00"),
                    activeItem("not-a-number"))));

            assertThat(header.recomputeTotalFromItems()).isEmpty();
        }

        @Test
        void shouldReturnEmpty_whenFeeIsNull() {
            BillingONCHeader1 header = new BillingONCHeader1();
            BillingONItem item = new BillingONItem();
            item.setStatus("O");
            item.setFee(null);
            header.setBillingItems(new ArrayList<>(List.of(item)));

            assertThat(header.recomputeTotalFromItems()).isEmpty();
        }

        @Test
        void shouldReturnZero_whenItemsCollectionIsNull() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(null);

            Optional<BigDecimal> result = header.recomputeTotalFromItems();

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("0");
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
