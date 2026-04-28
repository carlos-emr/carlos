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
package io.github.carlos_emr.carlos.billings.ca.on.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BillingValidationException}.
 *
 * <p>The exception is the contract between billing services that reject
 * invalid input and the Struts {@code <global-exception-mappings>} that
 * routes the rejection to {@code billingValidationError.jsp}. These tests
 * lock down:</p>
 *
 * <ul>
 *   <li>It IS a {@link RuntimeException} (so Spring's default rollback
 *       behaviour engages on @Transactional).</li>
 *   <li>Both constructors preserve message + cause for log diagnostics.</li>
 *   <li>It is NOT an {@link IllegalArgumentException} — Struts routes
 *       the two differently.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
@DisplayName("BillingValidationException")
@Tag("unit")
@Tag("billing")
@Tag("validator")
class BillingValidationExceptionUnitTest {

    @Test
    @DisplayName("is a RuntimeException — engages Spring's default rollback")
    void shouldBeRuntimeException() {
        BillingValidationException ex = new BillingValidationException("anything");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("is NOT an IllegalArgumentException — Struts routes them differently")
    void shouldNotBeIllegalArgumentException() {
        BillingValidationException ex = new BillingValidationException("anything");
        assertThat(ex).isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("(message) constructor preserves the message")
    void shouldPreserveMessage_singleArgConstructor() {
        BillingValidationException ex = new BillingValidationException("invalid demographic_no");
        assertThat(ex.getMessage()).isEqualTo("invalid demographic_no");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("(message, cause) constructor preserves both")
    void shouldPreserveMessageAndCause_twoArgConstructor() {
        NumberFormatException root = new NumberFormatException("not a number");
        BillingValidationException ex = new BillingValidationException(
                "rejected: demographic_no", root);
        assertThat(ex.getMessage()).isEqualTo("rejected: demographic_no");
        assertThat(ex.getCause()).isSameAs(root);
    }

    @Test
    @DisplayName("is throwable across method boundaries (smoke)")
    void shouldBeThrowable() {
        org.junit.jupiter.api.Assertions.assertThrows(
                BillingValidationException.class,
                () -> { throw new BillingValidationException("simulate"); });
    }
}
