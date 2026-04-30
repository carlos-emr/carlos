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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the per-method invariants on {@link BillingONItem} —
 * status whitelist (parallel to {@link BillingONCHeader1}'s whitelist),
 * the {@code markDeleted} mutator, and the {@code isActive}/{@code isDeleted}
 * pure-state queries.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingONItem (invariants)")
@Tag("unit")
@Tag("billing")
class BillingONItemUnitTest {

    @Nested
    @DisplayName("setStatus whitelist")
    class SetStatusWhitelist {

        @Test
        void shouldAcceptNull() {
            BillingONItem item = new BillingONItem();
            item.setStatus(null);
            assertThat(item.getStatus()).isNull();
        }

        @Test
        void shouldThrowIllegalArgumentException_whenSetStatusUnknownValue() {
            BillingONItem item = new BillingONItem();
            // "Z" is not in KNOWN_STATUSES = {O,S,D,B,P,N,I,W,A}; whitelist rejection
            // catches drift at write-time.
            assertThatThrownBy(() -> item.setStatus("Z"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("known set");
        }

        @Test
        void shouldAcceptAllNineKnownStatusConstants() {
            BillingONItem item = new BillingONItem();
            item.setStatus(BillingONItem.OPEN);
            item.setStatus(BillingONItem.SETTLED);
            item.setStatus(BillingONItem.DELETED);
            item.setStatus(BillingONItem.BILLED);
            item.setStatus(BillingONItem.PATIENT_BILLED);
            item.setStatus(BillingONItem.NOT_BILLED);
            item.setStatus(BillingONItem.INDEPENDENT);
            item.setStatus(BillingONItem.WCB);
            item.setStatus(BillingONItem.ACKNOWLEDGED);
        }
    }

    @Nested
    @DisplayName("isActive / isDeleted / markDeleted")
    class StateQueries {

        @Test
        void shouldReportActive_whenStatusIsOpen() {
            BillingONItem item = new BillingONItem();
            item.setStatus(BillingONItem.OPEN);
            assertThat(item.isActive()).isTrue();
            assertThat(item.isDeleted()).isFalse();
        }

        @Test
        void shouldReportActive_whenStatusIsNull() {
            BillingONItem item = new BillingONItem();
            // Null status defaults to active (legacy contract).
            assertThat(item.isActive()).isTrue();
            assertThat(item.isDeleted()).isFalse();
        }

        @Test
        void shouldReportDeleted_whenStatusIsD() {
            BillingONItem item = new BillingONItem();
            item.setStatus(BillingONItem.DELETED);
            assertThat(item.isDeleted()).isTrue();
            assertThat(item.isActive()).isFalse();
        }

        @Test
        void shouldFlipStatusToDeleted_onMarkDeleted() {
            BillingONItem item = new BillingONItem();
            item.setStatus(BillingONItem.OPEN);
            item.markDeleted();
            assertThat(item.getStatus()).isEqualTo(BillingONItem.DELETED);
            assertThat(item.isDeleted()).isTrue();
            assertThat(item.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("setFee BigDecimal-parseability")
    class SetFeeInvariant {

        @Test
        void shouldAcceptNull() {
            BillingONItem item = new BillingONItem();
            item.setFee(null);
            assertThat(item.getFee()).isNull();
        }

        @Test
        void shouldAcceptParseableBigDecimalString() {
            BillingONItem item = new BillingONItem();
            item.setFee("123.45");
            assertThat(item.getFee()).isEqualTo("123.45");
        }

        @Test
        void shouldAcceptZeroAndNegative() {
            BillingONItem item = new BillingONItem();
            item.setFee("0.00");
            assertThat(item.getFee()).isEqualTo("0.00");
            item.setFee("-12.34");
            assertThat(item.getFee()).isEqualTo("-12.34");
        }

        @Test
        void shouldRejectAtWriteTime_whenFeeCannotBeParsed() {
            BillingONItem item = new BillingONItem();
            // Catches a future caller's typo at the boundary instead of letting
            // it propagate as a silent Optional.empty() out of
            // BillingONCHeader1.recomputeTotalFromItems().
            assertThatThrownBy(() -> item.setFee("not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BigDecimal");
        }

        @Test
        void shouldRejectAtWriteTime_whenFeeIsEmptyString() {
            BillingONItem item = new BillingONItem();
            // Empty string is not a valid BigDecimal — caller must pass null
            // for "no fee" rather than rely on empty-string lenience.
            assertThatThrownBy(() -> item.setFee(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldAcceptDefunctSentinel_persistedByCorrectionServiceForTerminatedCodes() {
            // BillingCorrectionService writes this sentinel when an item references
            // a service code whose termination date precedes the service date.
            // Downstream code detects it via DEFUNCT_FEE.equals(item.getFee())
            // and skips numeric operations. The setter MUST allow the sentinel
            // through unchanged or correcting a bill containing a terminated
            // code throws IllegalArgumentException and aborts the entire save.
            BillingONItem item = new BillingONItem();
            item.setFee(BillingONItem.DEFUNCT_FEE);
            assertThat(item.getFee()).isEqualTo(BillingONItem.DEFUNCT_FEE);
        }

        @Test
        void shouldNotMutateField_whenRejectingInvalidValue() {
            BillingONItem item = new BillingONItem();
            item.setFee("100.00");
            try {
                item.setFee("garbage");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
            // The previous valid value must remain — partial-write semantics
            // would corrupt the row otherwise.
            assertThat(item.getFee()).isEqualTo("100.00");
        }
    }
}
