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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/** Unit coverage for the DB-only disk-finalization transaction boundary. */
@DisplayName("BillingOnDiskTransactionService")
@Tag("unit")
@Tag("billing")
class BillingOnDiskTransactionServiceUnitTest {

    private final BillingOnDiskTransactionService service = new BillingOnDiskTransactionService();

    @Test
    void shouldFinalizeSingleWriterInOrder_whenDiskIsGenerated() {
        OhipClaimFileService writer = mock(OhipClaimFileService.class);

        service.finalizeGeneratedDisk(writer, 42);

        var ordered = inOrder(writer);
        ordered.verify(writer).finalizeGeneratedDisk();
        ordered.verify(writer).updateDisknameSum(42);
    }

    @Test
    void shouldFinalizeAllWritersInOrder_whenGroupDiskIsGenerated() {
        OhipClaimFileService first = mock(OhipClaimFileService.class);
        OhipClaimFileService second = mock(OhipClaimFileService.class);

        service.finalizeGeneratedDisks(List.of(first, second), 43);

        var firstOrder = inOrder(first);
        firstOrder.verify(first).finalizeGeneratedDisk();
        firstOrder.verify(first).updateDisknameSum(43);
        var secondOrder = inOrder(second);
        secondOrder.verify(second).finalizeGeneratedDisk();
        secondOrder.verify(second).updateDisknameSum(43);
    }

    @Test
    void shouldExposeTransactionalBoundary_onFinalizeMethods() throws Exception {
        assertThat(BillingOnDiskTransactionService.class
                .getMethod("finalizeGeneratedDisk", OhipClaimFileService.class, int.class)
                .getAnnotation(Transactional.class))
                .isNotNull();
        assertThat(BillingOnDiskTransactionService.class
                .getMethod("finalizeGeneratedDisks", List.class, int.class)
                .getAnnotation(Transactional.class))
                .isNotNull();
    }
}
