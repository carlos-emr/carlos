/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the constructor-time guard on {@link BillingItemsNotLoadedException}.
 * The recovery contract relies on {@link BillingItemsNotLoadedException#headerId()}
 * being a real PK; the ctor must reject null and non-positive values so a
 * future caller's mistake doesn't propagate as
 * {@code findWithItems(0)} silently returning empty.
 *
 * @since 2026-04-30
 */
@DisplayName("BillingItemsNotLoadedException ctor guard")
@Tag("unit")
@Tag("billing")
class BillingItemsNotLoadedExceptionUnitTest {

    @Test
    void shouldStoreHeaderId_whenPositive() {
        BillingItemsNotLoadedException ex =
                new BillingItemsNotLoadedException("LAZY proxy", 42);
        assertThat(ex.headerId()).isEqualTo(42);
    }

    @Test
    void shouldRejectNullHeaderId() {
        assertThatThrownBy(() -> new BillingItemsNotLoadedException("LAZY proxy", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive header id");
    }

    @Test
    void shouldRejectZeroHeaderId() {
        assertThatThrownBy(() -> new BillingItemsNotLoadedException("LAZY proxy", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive header id");
    }

    @Test
    void shouldRejectNegativeHeaderId() {
        assertThatThrownBy(() -> new BillingItemsNotLoadedException("LAZY proxy", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive header id");
    }
}
