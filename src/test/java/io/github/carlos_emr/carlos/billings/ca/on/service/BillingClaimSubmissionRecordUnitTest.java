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

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the {@link BillingClaimSubmissionService.BillingClaimSubmission}
 * record's compact-constructor invariants:
 * <ul>
 *   <li>non-null header (NPE on null)</li>
 *   <li>null items → empty immutable list</li>
 *   <li>caller-supplied items list is defensively copied (mutation
 *       post-construction does not affect the record)</li>
 *   <li>{@code toLegacyArrayList} / {@code fromLegacy} round-trips the
 *       record: the resulting submission carries the same header
 *       reference and an items list with the same contents (in the same
 *       order)</li>
 * </ul>
 */
@DisplayName("BillingClaimSubmission record")
@Tag("unit")
@Tag("billing")
class BillingClaimSubmissionRecordUnitTest {

    @Test
    void shouldThrowNullPointerException_whenHeaderIsNull() {
        assertThatThrownBy(() -> new BillingClaimSubmissionService.BillingClaimSubmission(
                null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("header");
    }

    @Test
    void shouldReturnEmptyImmutableList_whenItemsConstructorArgIsNull() {
        BillingClaimHeaderDto h = new BillingClaimHeaderDto();
        BillingClaimSubmissionService.BillingClaimSubmission s =
                new BillingClaimSubmissionService.BillingClaimSubmission(h, null);
        assertThat(s.items()).isEmpty();
        assertThatThrownBy(() -> s.items().add(new BillingClaimItemDto()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnImmutableCopyOfItems_whenCallerMutatesOriginalListAfterConstruction() {
        BillingClaimHeaderDto h = new BillingClaimHeaderDto();
        List<BillingClaimItemDto> mutable = new ArrayList<>();
        mutable.add(new BillingClaimItemDto());
        BillingClaimSubmissionService.BillingClaimSubmission s =
                new BillingClaimSubmissionService.BillingClaimSubmission(h, mutable);

        // Mutate the original list AFTER construction.
        mutable.add(new BillingClaimItemDto());
        mutable.add(new BillingClaimItemDto());

        // Record's items are unaffected — it took a defensive copy.
        assertThat(s.items()).hasSize(1);
        // And they're an immutable view.
        assertThatThrownBy(() -> s.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRoundTripThroughLegacyArrayList_andPreserveHeaderAndItemRefs() {
        BillingClaimHeaderDto h = new BillingClaimHeaderDto();
        BillingClaimItemDto i1 = new BillingClaimItemDto();
        BillingClaimSubmissionService.BillingClaimSubmission s1 =
                new BillingClaimSubmissionService.BillingClaimSubmission(h, List.of(i1));

        @SuppressWarnings("rawtypes")
        ArrayList legacy = s1.toLegacyArrayList();
        BillingClaimSubmissionService.BillingClaimSubmission s2 =
                BillingClaimSubmissionService.BillingClaimSubmission.fromLegacy(legacy);

        assertThat(s2.header()).isSameAs(h);
        assertThat(s2.items()).hasSize(1);
        assertThat(s2.items().get(0)).isSameAs(i1);
    }
}
