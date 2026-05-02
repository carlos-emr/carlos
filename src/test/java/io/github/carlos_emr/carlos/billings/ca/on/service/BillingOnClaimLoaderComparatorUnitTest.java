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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sentinel-sort behaviour of {@link BillingOnClaimLoader#SERVICE_DATE_COMPARATOR}
 * and {@link BillingOnClaimLoader#DEMOGRAPHIC_NO_COMPARATOR}.
 *
 * <p>Earlier versions returned {@code 0} for unparseable values which broke
 * TimSort's transitivity contract — a single corrupt row would crash
 * {@code Collections.sort} with "Comparison method violates its general
 * contract!". The current implementation pushes parse failures to a sentinel
 * (epoch / {@code Integer.MIN_VALUE}) so corrupt rows sort consistently
 * without breaking the sort.</p>
 *
 * @since 2026-04-30
 */
@DisplayName("BillingOnClaimLoader comparators")
@Tag("unit")
@Tag("billing")
class BillingOnClaimLoaderComparatorUnitTest {

    private static BillingClaimHeaderDto withBillingDate(String d) {
        BillingClaimHeaderDto dto = new BillingClaimHeaderDto();
        dto = dto.withBillingDate(d);
        return dto;
    }

    private static BillingClaimHeaderDto withDemoNo(String n) {
        BillingClaimHeaderDto dto = new BillingClaimHeaderDto();
        dto = dto.withDemographicNo(n);
        return dto;
    }

    @Test
    void shouldSortValidDatesAscending_andNotThrow_whenListContainsCorruptRow() {
        // The load-bearing contract: a single corrupt row must not crash
        // Collections.sort. This whole test is the regression — a return-0
        // implementation throws IllegalArgumentException here.
        List<BillingClaimHeaderDto> list = new ArrayList<>(List.of(
                withBillingDate("2026-04-01"),
                withBillingDate("garbage"),
                withBillingDate("2026-01-15"),
                withBillingDate("2026-03-20")));

        Collections.sort(list, BillingOnClaimLoader.SERVICE_DATE_COMPARATOR);

        // Garbage row must sort to position 0 (epoch sentinel < any 2026 date).
        assertThat(list.get(0).billingDate()).isEqualTo("garbage");
        // Remaining three are sorted ascending.
        assertThat(list.get(1).billingDate()).isEqualTo("2026-01-15");
        assertThat(list.get(2).billingDate()).isEqualTo("2026-03-20");
        assertThat(list.get(3).billingDate()).isEqualTo("2026-04-01");
    }

    @Test
    void shouldSortValidDemoNosAscending_andNotThrow_whenListContainsCorruptRow() {
        List<BillingClaimHeaderDto> list = new ArrayList<>(List.of(
                withDemoNo("100"),
                withDemoNo("not-a-number"),
                withDemoNo("50"),
                withDemoNo("75")));

        Collections.sort(list, BillingOnClaimLoader.DEMOGRAPHIC_NO_COMPARATOR);

        // Corrupt row sorts to position 0 (Integer.MIN_VALUE).
        assertThat(list.get(0).demographicNo()).isEqualTo("not-a-number");
        assertThat(list.get(1).demographicNo()).isEqualTo("50");
        assertThat(list.get(2).demographicNo()).isEqualTo("75");
        assertThat(list.get(3).demographicNo()).isEqualTo("100");
    }

    @Test
    void shouldHandleNullBillingDate_withoutThrow() {
        BillingClaimHeaderDto a = withBillingDate(null);
        BillingClaimHeaderDto b = withBillingDate("2026-01-01");

        // No throw; null sorts to epoch sentinel which is before 2026-01-01.
        assertThat(BillingOnClaimLoader.SERVICE_DATE_COMPARATOR.compare(a, b))
                .isLessThan(0);
    }

    @Test
    void shouldHandleNullDemographicNo_withoutThrow() {
        BillingClaimHeaderDto a = withDemoNo(null);
        BillingClaimHeaderDto b = withDemoNo("42");

        assertThat(BillingOnClaimLoader.DEMOGRAPHIC_NO_COMPARATOR.compare(a, b))
                .isLessThan(0);
    }
}
