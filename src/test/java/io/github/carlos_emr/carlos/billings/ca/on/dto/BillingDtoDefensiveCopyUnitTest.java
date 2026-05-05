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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit coverage for defensive-copy and referential DTO invariants. */
@DisplayName("Ontario billing DTO defensive copies")
@Tag("unit")
@Tag("billing")
class BillingDtoDefensiveCopyUnitTest {

    @Test
    void shouldDefensivelyCopyDiskFilenameRows_whenListIsSetOrRead() {
        BillingDiskNameDto dto = new BillingDiskNameDto();
        List<DiskFilenameRow> rows = new ArrayList<>();
        rows.add(new DiskFilenameRow("1", "a.txt", "123", "999998", "1", "O", "10.00"));

        dto.setFilenames(rows);
        rows.add(new DiskFilenameRow("2", "b.txt", "456", "999999", "2", "O", "20.00"));

        assertThat(dto.getFilenames()).hasSize(1);
        assertThatThrownBy(() -> dto.getFilenames().add(
                new DiskFilenameRow("3", "c.txt", "", "", "", "", "")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldDefensivelyCopyFluBillingDate_whenConstructedOrRead() {
        Date billingDate = new Date(1_000L);

        BillFluRow row = new BillFluRow("<specialty/>", 1, "10.00", "O", billingDate, "Doe, Jane");
        billingDate.setTime(2_000L);
        Date readDate = row.billingDate();
        readDate.setTime(3_000L);

        assertThat(row.billingDate()).isEqualTo(new Date(1_000L));
    }

    @Test
    void shouldDefensivelyCopyObBillingDate_whenConstructedOrRead() {
        Date billingDate = new Date(1_000L);

        BillObRow row = new BillObRow(1, "10.00", "O", billingDate, "Doe, Jane");
        billingDate.setTime(2_000L);
        Date readDate = row.billingDate();
        readDate.setTime(3_000L);

        assertThat(row.billingDate()).isEqualTo(new Date(1_000L));
    }

    @Test
    void shouldRejectSpecialistClaim_whenItemReferencesAnotherHeader() {
        BillingClaimHeaderDto header = new BillingClaimHeaderDto().withId("100");
        BillingClaimItemDto item = new BillingClaimItemDto().withClaimHeaderId("200");

        assertThatThrownBy(() -> new BillingSpecialistClaim(header, List.of(item)))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("item claimHeaderId [200] does not match header id [100]");
    }
}
