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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the {@link BillingONItem#equals(Object)} / {@link BillingONItem#hashCode()}
 * contract.
 *
 * <p>Pre-fix: {@code equals()} fell back to {@code (ch1Id, serviceCode)} when
 * either id was null, but {@code hashCode()} returned only {@code id.hashCode()}
 * (or 0 for transient). A transient item and a persisted-with-same-natural-key
 * item were {@code .equals()} but hashed to different buckets — illegal pair.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingONItem equals/hashCode contract")
@Tag("unit")
@Tag("billing")
class BillingONItemEqualsContractUnitTest {

    private static BillingONItem makeItem(Integer id, Integer ch1Id, String serviceCode) {
        BillingONItem item = new BillingONItem();
        if (id != null) {
            // The entity has no public setId — Hibernate sets it on persist via
            // the @GeneratedValue path. Use reflection to simulate persisted state.
            try {
                Field idField = BillingONItem.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(item, id);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to set id via reflection — entity field structure changed?", e);
            }
        }
        item.setCh1Id(ch1Id);
        item.setServiceCode(serviceCode);
        return item;
    }

    @Test
    void shouldHaveEqualHashCodes_whenEqualsByNaturalKeyAndOneIdNull() {
        BillingONItem persisted = makeItem(5, 100, "A001");
        BillingONItem transientItem = makeItem(null, 100, "A001");

        assertThat(persisted)
                .as("persisted vs transient with same (ch1Id, serviceCode) — equals branch 2")
                .isEqualTo(transientItem);

        assertThat(persisted.hashCode())
                .as("equals/hashCode contract: equal objects must share a hash code")
                .isEqualTo(transientItem.hashCode());
    }

    @Test
    void shouldHaveEqualHashCodes_whenEqualsByNaturalKeyAndBothIdsNull() {
        BillingONItem a = makeItem(null, 100, "A001");
        BillingONItem b = makeItem(null, 100, "A001");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldHaveEqualHashCodes_whenEqualsByIdAndBothIdsNonNull() {
        // Branch 1: both ids present → equality is id-based.
        BillingONItem a = makeItem(7, 100, "A001");
        BillingONItem b = makeItem(7, 100, "A001");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotEqual_whenIdsDiffer() {
        BillingONItem a = makeItem(5, 100, "A001");
        BillingONItem b = makeItem(7, 100, "A001");

        assertThat(a).isNotEqualTo(b);
        // collision allowed but not required
    }

    @Test
    void shouldNotEqual_whenServiceCodeDiffers() {
        BillingONItem a = makeItem(null, 100, "A001");
        BillingONItem b = makeItem(null, 100, "A002");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldHandleNullCh1IdAndNullServiceCodeWithoutThrowing() {
        // Defensive: hashCode must not NPE on bare instances.
        BillingONItem bare = new BillingONItem();
        // id is null, ch1Id is null, serviceCode is null
        assertThat(bare.hashCode()).isZero();
    }

    @Test
    void shouldNotThrowNpe_whenEqualsCompareWithNullCh1Id() {
        // Pre-fix: equals fallback path used `ch1Id.equals(...)` directly,
        // NPEing whenever one transient had a null ch1Id (a real pre-persist
        // state, e.g. setCh1Id(parent.getId()) where parent isn't saved yet).
        BillingONItem nullCh1 = makeItem(null, null, "A001");
        BillingONItem withCh1 = makeItem(null, 100, "A001");

        assertThatCode(() -> nullCh1.equals(withCh1)).doesNotThrowAnyException();
        assertThat(nullCh1.equals(withCh1)).isFalse();
    }

    @Test
    void shouldNotThrowNpe_whenEqualsCompareWithNullServiceCode() {
        BillingONItem nullSc = makeItem(null, 100, null);
        BillingONItem withSc = makeItem(null, 100, "A001");

        assertThatCode(() -> nullSc.equals(withSc)).doesNotThrowAnyException();
        assertThat(nullSc.equals(withSc)).isFalse();
    }

    @Test
    void shouldEqual_whenBothTransientWithSameNaturalKey() {
        // Two transient items with the same (ch1Id, serviceCode) ARE equal.
        BillingONItem a = makeItem(null, 100, "A001");
        BillingONItem b = makeItem(null, 100, "A001");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldEqual_whenAllNaturalKeyFieldsAreNull() {
        BillingONItem a = makeItem(null, null, null);
        BillingONItem b = makeItem(null, null, null);
        assertThat(a).isEqualTo(b);
    }
}
