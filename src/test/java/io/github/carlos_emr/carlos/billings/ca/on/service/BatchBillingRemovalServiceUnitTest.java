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

import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the typed-exception contract on
 * {@link BatchBillingRemovalService#removeAll}.
 *
 * <p>Pre-fix the loop ran inline in {@code BatchBill2Action} and called
 * {@code .get(0)} unconditionally — an empty lookup NPE'd. Post-fix the
 * service throws {@link BatchBillingRemovalService.RemovalRowMissingException}
 * carrying the offending {@link BatchBillingRemovalService.Row}, which the
 * caller surfaces as a 404. The exception type also lets Spring's
 * {@code @Transactional} roll back any prior removes in the batch
 * (the AOP boundary itself is not exercisable on a plain {@code new …()}
 * instance — that is covered by the action-level integration test).</p>
 *
 * @since 2026-04-30
 */
@DisplayName("BatchBillingRemovalService loop semantics")
@Tag("unit")
@Tag("billing")
class BatchBillingRemovalServiceUnitTest {

    private BatchBillingDAO dao;
    private BatchBillingRemovalService service;

    @BeforeEach
    void setUp() {
        dao = mock(BatchBillingDAO.class);
        service = new BatchBillingRemovalService(dao);
    }

    private static BatchBilling row(int id) {
        BatchBilling bb = new BatchBilling();
        bb.setId(id);
        return bb;
    }

    @Test
    void shouldRemoveEachMatchedRow() {
        when(dao.find(1, "A007")).thenReturn(List.of(row(11)));
        when(dao.find(2, "A008")).thenReturn(List.of(row(22)));

        service.removeAll(List.of(
                new BatchBillingRemovalService.Row(1, "A007"),
                new BatchBillingRemovalService.Row(2, "A008")));

        verify(dao).remove(eq(11));
        verify(dao).remove(eq(22));
        verify(dao, times(2)).remove(any(Integer.class));
    }

    @Test
    void shouldThrowTyped_whenLookupReturnsEmpty() {
        when(dao.find(1, "A007")).thenReturn(List.of(row(11)));
        when(dao.find(2, "MISSING")).thenReturn(List.of());

        BatchBillingRemovalService.Row missing =
                new BatchBillingRemovalService.Row(2, "MISSING");

        assertThatThrownBy(() -> service.removeAll(List.of(
                new BatchBillingRemovalService.Row(1, "A007"),
                missing)))
                .isInstanceOf(BatchBillingRemovalService.RemovalRowMissingException.class)
                .hasMessageContaining("demographicNo=2")
                .hasMessageContaining("serviceCode=MISSING");

        // First row was removed before the throw — the @Transactional
        // boundary (not exercisable here) is what actually rolls it back.
        verify(dao).remove(eq(11));
    }

    @Test
    void shouldThrowTyped_whenLookupReturnsNull() {
        when(dao.find(7, "X")).thenReturn(null);

        assertThatThrownBy(() -> service.removeAll(List.of(
                new BatchBillingRemovalService.Row(7, "X"))))
                .isInstanceOf(BatchBillingRemovalService.RemovalRowMissingException.class);

        verify(dao, never()).remove(any(Integer.class));
    }

    @Test
    void shouldExposeRowOnException_forCallerLogging() {
        when(dao.find(99, "BAD")).thenReturn(List.of());

        BatchBillingRemovalService.Row badRow =
                new BatchBillingRemovalService.Row(99, "BAD");

        try {
            service.removeAll(List.of(badRow));
            assertThat(false).as("expected RemovalRowMissingException").isTrue();
        } catch (BatchBillingRemovalService.RemovalRowMissingException ex) {
            assertThat(ex.row()).isEqualTo(badRow);
            assertThat(ex.row().demographicNo()).isEqualTo(99);
            assertThat(ex.row().serviceCode()).isEqualTo("BAD");
        }
    }

    @Test
    void shouldHandleEmptyInput_asNoOp() {
        service.removeAll(List.of());
        verify(dao, never()).find(any(Integer.class), any(String.class));
        verify(dao, never()).remove(any(Integer.class));
    }
}
