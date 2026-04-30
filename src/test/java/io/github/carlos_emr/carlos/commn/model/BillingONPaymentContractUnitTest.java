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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the {@link BillingONPayment#equals(Object)} / {@link BillingONPayment#hashCode()}
 * contract and the null-safe Comparator.
 *
 * <p>Pre-fix: no equals/hashCode override (inherited identity from AbstractModel),
 * AND the BILLING_ON_PAYMENT_COMPARATOR called {@code compareTo(getPaymentDate())}
 * without a null guard — NPE on any payment with null paymentDate (a real
 * pre-persist state).
 *
 * @since 2026-04-30
 */
@DisplayName("BillingONPayment contract")
@Tag("unit")
@Tag("billing")
class BillingONPaymentContractUnitTest {

    private static BillingONPayment makePayment(Integer id, Date paymentDate) {
        BillingONPayment p = new BillingONPayment();
        if (id != null) {
            try {
                Field idField = BillingONPayment.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(p, id);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("id field structure changed?", e);
            }
        }
        p.setPaymentDate(paymentDate);
        return p;
    }

    @Test
    void shouldHaveEqualHashCodes_whenIdsMatch() {
        BillingONPayment a = makePayment(7, new Date());
        BillingONPayment b = makePayment(7, new Date());

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotEqual_whenIdsDiffer() {
        BillingONPayment a = makePayment(5, new Date());
        BillingONPayment b = makePayment(7, new Date());

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldNotEqualAnotherTransient_whenBothIdsAreNull() {
        // Two unpersisted payments are NOT equal — there's no natural key on
        // BillingONPayment, so identity is the only safe semantic.
        BillingONPayment a = makePayment(null, new Date());
        BillingONPayment b = makePayment(null, new Date());

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldEqualSelf_whenTransient() {
        BillingONPayment p = makePayment(null, new Date());
        assertThat(p).isEqualTo(p);
    }

    @Test
    void shouldNotEqual_whenPersistedComparedWithTransient() {
        BillingONPayment persisted = makePayment(5, new Date());
        BillingONPayment transientP = makePayment(null, new Date());

        assertThat(persisted).isNotEqualTo(transientP);
        assertThat(transientP).isNotEqualTo(persisted);
    }

    @Test
    void shouldNotThrow_whenSortingWithNullPaymentDate() {
        // The whole point of the comparator fix — sorting a list with a
        // pre-persist payment that has no date must not NPE.
        BillingONPayment dated1 = makePayment(1, new Date(100_000));
        BillingONPayment dated2 = makePayment(2, new Date(200_000));
        BillingONPayment nullDate = makePayment(3, null);

        List<BillingONPayment> list = Stream.of(dated2, nullDate, dated1)
                .sorted(BillingONPayment.BILLING_ON_PAYMENT_COMPARATOR)
                .toList();

        // null sorts last; the dated entries sort ascending
        assertThat(list.get(0)).isSameAs(dated1);
        assertThat(list.get(1)).isSameAs(dated2);
        assertThat(list.get(2)).isSameAs(nullDate);
    }

    @Test
    void shouldNotThrow_whenAllPaymentDatesAreNull() {
        BillingONPayment a = makePayment(1, null);
        BillingONPayment b = makePayment(2, null);

        assertThatCode(() ->
                Stream.of(a, b).sorted(BillingONPayment.BILLING_ON_PAYMENT_COMPARATOR).toList())
                .doesNotThrowAnyException();
    }
}
