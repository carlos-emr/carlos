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
 * and on {@code BillingOnInvoiceTotalsService}; they belong on the entity
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

        @Test
        void shouldAccept_unusuallyLargeValues() {
            // Pin DB column-precision contract — billing.total is DECIMAL(12,4)
            // in the schema. setTotal must not reject high-precision or
            // wide-magnitude values within that contract.
            BillingONCHeader1 header = new BillingONCHeader1();
            BigDecimal large = new BigDecimal("99999999.9999");
            header.setTotal(large);
            assertThat(header.getTotal()).isEqualByComparingTo(large);
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

        @Test
        void shouldThrowIllegalArgumentException_whenSetStatusUnknownValue() {
            BillingONCHeader1 header = new BillingONCHeader1();
            // "Z" is not in KNOWN_STATUSES = {O,S,D,B,P,N,I,W,A}; whitelist rejection
            // catches drift at write-time so a future contributor's typo stops here
            // rather than spreading through the system.
            assertThatThrownBy(() -> header.setStatus("Z"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("known set");
        }

        @Test
        void shouldAcceptAllNineKnownStatusConstants() {
            BillingONCHeader1 header = new BillingONCHeader1();
            // Each constant is whitelisted; setStatus must accept all without throwing.
            header.setStatus(BillingONCHeader1.OPEN);
            header.setStatus(BillingONCHeader1.SETTLED);
            header.setStatus(BillingONCHeader1.DELETED);
            header.setStatus(BillingONCHeader1.BILLED);
            header.setStatus(BillingONCHeader1.PATIENT_BILLED);
            header.setStatus(BillingONCHeader1.NOT_BILLED);
            header.setStatus(BillingONCHeader1.INDEPENDENT);
            header.setStatus(BillingONCHeader1.WCB);
            header.setStatus(BillingONCHeader1.ACKNOWLEDGED);
        }
    }

    @Nested
    @DisplayName("billingItems collection contract")
    class BillingItemsCollection {

        @Test
        void shouldReturnUnmodifiableView_whenGetBillingItems() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.addBillingItem(item("10.00"));
            List<BillingONItem> view = header.getBillingItems();

            // Mutating the returned view must throw — production callers must
            // use addBillingItem/removeBillingItem to mutate the collection.
            assertThatThrownBy(() -> view.add(item("20.00")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldAppendToLiveCollection_whenAddBillingItem() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.addBillingItem(item("10.00"));
            header.addBillingItem(item("20.00"));

            assertThat(header.getBillingItems()).hasSize(2);
            assertThat(header.getBillingItems())
                    .extracting(BillingONItem::getFee)
                    .containsExactly("10.00", "20.00");
        }

        @Test
        void shouldIgnoreNull_whenAddBillingItem() {
            BillingONCHeader1 header = new BillingONCHeader1();
            header.addBillingItem(null);
            assertThat(header.getBillingItems()).isEmpty();
        }

        @Test
        void shouldRemoveFromLiveCollection_whenRemoveBillingItem() {
            BillingONCHeader1 header = new BillingONCHeader1();
            BillingONItem first = item("10.00");
            BillingONItem second = item("20.00");
            header.addBillingItem(first);
            header.addBillingItem(second);

            boolean removed = header.removeBillingItem(first);

            assertThat(removed).isTrue();
            assertThat(header.getBillingItems()).hasSize(1);
            assertThat(header.getBillingItems()).contains(second);
        }

        @Test
        void shouldReturnFalse_whenRemoveBillingItemNotInCollection() {
            BillingONCHeader1 header = new BillingONCHeader1();
            assertThat(header.removeBillingItem(item("10.00"))).isFalse();
        }

        private BillingONItem item(String fee) {
            BillingONItem i = new BillingONItem();
            i.setStatus(BillingONItem.OPEN);
            i.setFee(fee);
            return i;
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
        void shouldRejectAtWriteTime_whenFeeCannotBeParsed() {
            // Phase: BillingONItem.setFee now validates at write-time, so
            // an unparseable fee never reaches recomputeTotalFromItems().
            // Pin the rejection here so a future regression that loosens
            // setFee surfaces in this entity's own test suite.
            BillingONItem item = new BillingONItem();
            item.setStatus(BillingONItem.OPEN);
            assertThatThrownBy(() -> item.setFee("not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a valid BigDecimal");
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
            // setBillingItems(null) is a documented test-setup affordance on
            // a transient header; the C16 guard only rejects calls when an id
            // has been assigned.

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

    @Nested
    @DisplayName("setBillingItems managed/transient guard (C16)")
    class SetBillingItemsGuard {

        @Test
        void shouldThrowIllegalStateException_whenCalledOnManagedHeader() throws Exception {
            // Simulate a managed entity by setting `id` reflectively (no public
            // setter exists). Hibernate would normally assign this on persist;
            // the C16 guard fires for any caller that touches setBillingItems
            // after an id is in scope, so dirty-tracking can't silently break.
            BillingONCHeader1 header = new BillingONCHeader1();
            java.lang.reflect.Field idField = BillingONCHeader1.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(header, 42);

            assertThatThrownBy(() -> header.setBillingItems(new java.util.ArrayList<>()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("managed-or-detached");
        }

        @Test
        void shouldAcceptCall_whenHeaderIsTransient() {
            // Transient entity (id == null) is the documented use case.
            BillingONCHeader1 header = new BillingONCHeader1();
            header.setBillingItems(new java.util.ArrayList<>());
            // Must not throw — guard is gated on id != null.
        }
    }

    @Nested
    @DisplayName("getBillingItems never returns null")
    class GetBillingItemsNullFloor {

        @Test
        void shouldReturnEmptyList_whenBackingCollectionIsNull() throws Exception {
            BillingONCHeader1 header = new BillingONCHeader1();
            // Bypass the public setter (which the C16 guard would still allow
            // here for a transient header) by clearing the field directly,
            // mirroring a corner case where Hibernate field-access populates
            // a null collection.
            java.lang.reflect.Field f = BillingONCHeader1.class.getDeclaredField("billingItems");
            f.setAccessible(true);
            f.set(header, null);

            assertThat(header.getBillingItems()).isEmpty();
        }
    }
}
