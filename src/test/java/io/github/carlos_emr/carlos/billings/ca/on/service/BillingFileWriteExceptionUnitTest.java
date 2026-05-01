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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of {@link BillingFileWriteException} — the class
 * introduced by the swallow-and-persist fix wave to surface OHIP claim-file
 * write failures to the operator instead of letting them silently log into
 * the void.
 *
 * <p>The class is short, but it has six throw sites in
 * {@code OhipClaimFileService} / {@code OhipClaimExtractService} and is wired
 * to the {@code billingFileWriteError} struts result via
 * {@code <global-exception-mappings>} in {@code struts-billing.xml}. A
 * regression that demoted the throw to a swallow, or changed the parent class
 * away from {@link RuntimeException} (breaking the in-flight transaction
 * roll-back), would otherwise pass CI silently.</p>
 */
@DisplayName("BillingFileWriteException")
@Tag("unit")
@Tag("billing")
class BillingFileWriteExceptionUnitTest {

    @Test
    void shouldExtendRuntimeException_soInFlightTransactionsRollBack() {
        // Spring's default rollback behavior is keyed off RuntimeException.
        // If someone changes the parent to Exception, a checked-throw site
        // would still compile, but the transaction would commit before the
        // exception propagates — defeating the entire point of this class.
        assertThat(RuntimeException.class)
                .isAssignableFrom(BillingFileWriteException.class);
    }

    @Test
    void shouldPreserveMessage() {
        BillingFileWriteException ex = new BillingFileWriteException(
                "Failed to write OHIP claim file: claim123.txt", null);
        assertThat(ex.getMessage())
                .isEqualTo("Failed to write OHIP claim file: claim123.txt");
    }

    @Test
    void shouldPreserveCause() {
        IOException cause = new IOException("Disk full");
        BillingFileWriteException ex = new BillingFileWriteException(
                "Failed to write OHIP claim file: claim123.txt", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void shouldAllowNullCause() {
        // Some throw sites (e.g. createBillingFileStr) wrap a generic Exception
        // and pass it as cause; others (validation pre-checks) may throw with
        // null cause. Both call shapes must work.
        BillingFileWriteException ex = new BillingFileWriteException(
                "OHIP claim extraction failed", null);
        assertThat(ex.getMessage()).isEqualTo("OHIP claim extraction failed");
        assertThat(ex.getCause()).isNull();
    }
}
