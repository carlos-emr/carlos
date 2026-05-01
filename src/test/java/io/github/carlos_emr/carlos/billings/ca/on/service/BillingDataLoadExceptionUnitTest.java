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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the structured-payload contract on {@link BillingDataLoadException}.
 * The operator-facing JSP renders the {@code phase} discriminator and the
 * {@code context} map as a structured banner; without these tests, a future
 * refactor that reverts to message-only could silently break the JSP layout.
 *
 * @since 2026-05-01
 */
@DisplayName("BillingDataLoadException structured payload")
@Tag("unit")
@Tag("billing")
class BillingDataLoadExceptionUnitTest {

    @Test
    void shouldDefaultToDaoQueryPhase_whenLegacyConstructorUsed() {
        BillingDataLoadException ex = new BillingDataLoadException("loader failed");
        assertThat(ex.phase()).isEqualTo(BillingDataLoadException.Phase.DAO_QUERY);
        assertThat(ex.context()).isEmpty();
    }

    @Test
    void shouldDefaultToDaoQueryPhase_whenLegacyConstructorWithCauseUsed() {
        Throwable cause = new IllegalStateException("boom");
        BillingDataLoadException ex = new BillingDataLoadException("loader failed", cause);
        assertThat(ex.phase()).isEqualTo(BillingDataLoadException.Phase.DAO_QUERY);
        assertThat(ex.context()).isEmpty();
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void shouldCarryStructuredPhaseAndContext() {
        BillingDataLoadException ex = new BillingDataLoadException(
                "missing batch_header",
                BillingDataLoadException.Phase.BATCH_HEADER_LOOKUP,
                Map.of("bid", "12345"));

        assertThat(ex.phase()).isEqualTo(BillingDataLoadException.Phase.BATCH_HEADER_LOOKUP);
        assertThat(ex.context()).containsEntry("bid", "12345");
    }

    @Test
    void shouldReturnUnmodifiableContext() {
        BillingDataLoadException ex = new BillingDataLoadException(
                "loader failed",
                BillingDataLoadException.Phase.DAO_QUERY,
                Map.of("k", "v"));

        assertThatThrownBy(() -> ex.context().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldDefaultToDaoQueryPhase_whenPhaseIsNull() {
        BillingDataLoadException ex = new BillingDataLoadException(
                "loader failed", null, null, Map.of());
        assertThat(ex.phase()).isEqualTo(BillingDataLoadException.Phase.DAO_QUERY);
    }

    @Test
    void shouldDefaultToEmptyContext_whenContextIsNull() {
        BillingDataLoadException ex = new BillingDataLoadException(
                "loader failed", null,
                BillingDataLoadException.Phase.DATE_PARSE, null);
        assertThat(ex.context()).isEmpty();
    }

    @Test
    void shouldPreserveInsertionOrder_inContext() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("first", "1");
        input.put("second", "2");
        input.put("third", "3");

        BillingDataLoadException ex = new BillingDataLoadException(
                "ordered", BillingDataLoadException.Phase.CLAIM_EXTRACT, input);

        assertThat(ex.context().keySet()).containsExactly("first", "second", "third");
    }
}
