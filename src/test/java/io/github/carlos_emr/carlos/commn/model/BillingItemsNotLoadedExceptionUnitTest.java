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
    void shouldRejectNullHeaderId_forInvalidInput() {
        assertThatThrownBy(() -> new BillingItemsNotLoadedException("LAZY proxy", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive header id");
    }

    @Test
    void shouldRejectZeroHeaderId_forInvalidInput() {
        assertThatThrownBy(() -> new BillingItemsNotLoadedException("LAZY proxy", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive header id");
    }

    @Test
    void shouldRejectNegativeHeaderId_forInvalidInput() {
        assertThatThrownBy(() -> new BillingItemsNotLoadedException("LAZY proxy", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive header id");
    }
}
