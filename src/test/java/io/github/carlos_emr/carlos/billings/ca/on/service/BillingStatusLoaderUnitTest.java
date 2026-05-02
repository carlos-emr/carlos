/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Ontario billing status loader")
@Tag("unit")
@Tag("billing")
class BillingStatusLoaderUnitTest {

    private final BillingOnClaimLoader claimLoader = mock(BillingOnClaimLoader.class);
    private final BillingStatusLoader loader = new BillingStatusLoader(claimLoader);

    @Test
    void shouldNormalizeAnySentinelsToNull_forBasicStatusSearch() {
        when(claimLoader.getBill((String[]) null, null, null, null, null, null, null, null, null))
                .thenReturn(List.of());

        loader.getBills(new String[0], "%", "all", "", "", "", "0000", "", "");

        verify(claimLoader).getBill(
                org.mockito.ArgumentMatchers.<String[]>isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void shouldNormalizeAnySentinelsToNull_forSortedStatusSearch() {
        List<String> mergedCodes = List.of();
        when(claimLoader.mergeServiceCodes(null, null)).thenReturn(mergedCodes);
        when(claimLoader.getBillWithSorting(null, null, null, null, null, null, mergedCodes,
                null, null, null, "billing_date", "desc", null, null, null))
                .thenReturn(List.of());

        loader.getBillsWithSorting(new String[0], "%", "all", "", "", "",
                "%", "1", "1", "---", "0000", "billing_date", "desc", "", "", "");

        verify(claimLoader).mergeServiceCodes(isNull(), isNull());
        verify(claimLoader).getBillWithSorting(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(mergedCodes),
                isNull(),
                isNull(),
                isNull(),
                eq("billing_date"),
                eq("desc"),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void shouldUppercaseConcreteServiceCodeBeforeMerging() {
        List<String> mergedCodes = List.of("A001A");
        when(claimLoader.mergeServiceCodes("A001A", "FORM")).thenReturn(mergedCodes);
        when(claimLoader.getBillWithSorting(null, "O", "999998", "2026-05-01", "2026-05-02",
                "123", mergedCodes, "250", "00", "1234", null, null, null, null, "42"))
                .thenReturn(List.of(new BillingClaimHeaderDto().withId("42")));

        List<BillingClaimHeaderDto> bills = loader.getBillsWithSorting(null, "O", "999998",
                "2026-05-01", "2026-05-02", "123", "a001a", "250", "00", "FORM",
                "1234", null, null, null, null, "42");

        assertThat(bills).extracting(BillingClaimHeaderDto::id).containsExactly("42");
        verify(claimLoader).mergeServiceCodes("A001A", "FORM");
    }
}
