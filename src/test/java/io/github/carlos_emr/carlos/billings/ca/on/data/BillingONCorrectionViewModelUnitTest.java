/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BillingONCorrectionViewModel")
@Tag("unit")
@Tag("billing")
class BillingONCorrectionViewModelUnitTest {

    @Test
    void shouldDefaultAllFields_whenBuiltEmpty() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder().build();

        assertThat(m.getUserProviderNo()).isEmpty();
        assertThat(m.getUserFirstName()).isEmpty();
        assertThat(m.getUserLastName()).isEmpty();
        assertThat(m.isSiteAccessPrivacy()).isFalse();
        assertThat(m.isTeamAccessPrivacy()).isFalse();
        assertThat(m.isTeamBillingOnly()).isFalse();
        assertThat(m.isMultisites()).isFalse();
        assertThat(m.getProviderAccessList()).isEmpty();
        assertThat(m.getMgrSites()).isEmpty();
    }

    @Test
    void shouldRoundTripAllFields_whenBuilderSetsThem() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .userProviderNo("999998")
                .userFirstName("doctor")
                .userLastName("carlosdoc")
                .siteAccessPrivacy(true)
                .teamAccessPrivacy(true)
                .teamBillingOnly(true)
                .multisites(true)
                .providerAccessList(Set.of("999998", "111111"))
                .mgrSites(List.of("Site A", "Site B"))
                .build();

        assertThat(m.getUserProviderNo()).isEqualTo("999998");
        assertThat(m.getUserFirstName()).isEqualTo("doctor");
        assertThat(m.getUserLastName()).isEqualTo("carlosdoc");
        assertThat(m.isSiteAccessPrivacy()).isTrue();
        assertThat(m.isTeamAccessPrivacy()).isTrue();
        assertThat(m.isTeamBillingOnly()).isTrue();
        assertThat(m.isMultisites()).isTrue();
        assertThat(m.getProviderAccessList()).containsExactlyInAnyOrder("999998", "111111");
        assertThat(m.getMgrSites()).containsExactly("Site A", "Site B");
    }

    @Test
    void shouldReturnImmutableProviderAccessList_whenAccessed() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .providerAccessList(Set.of("999998"))
                .build();

        assertThatThrownBy(() -> m.getProviderAccessList().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnImmutableMgrSites_whenAccessed() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .mgrSites(List.of("Site A"))
                .build();

        assertThatThrownBy(() -> m.getMgrSites().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Regression armor for the d2db61d4 Group D bridge-migration bug where
     * `xml_payProgram` was bound to `correctionModel.billDate` (copy/paste
     * error). The model fields are independent — verify they round-trip
     * separately so a future renaming or accidental field merge breaks loud.
     */
    @Test
    void shouldKeepPayProgram_independentFromBillDate() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .payProgram("HCP")
                .billDate("2026-04-25")
                .build();

        assertThat(m.getPayProgram()).isEqualTo("HCP");
        assertThat(m.getBillDate()).isEqualTo("2026-04-25");
        assertThat(m.getPayProgram()).isNotEqualTo(m.getBillDate());
    }

    /**
     * Regression armor for the d2db61d4 clinic-dropdown bug where every
     * `<option>` rendered the same `correctionModel.billLocationNo` instead
     * of the per-iteration loop variable. The model field is a single
     * canonical String value, distinct from the `bCh1.getFaciltyNum()` row
     * data — verify the field exists and round-trips.
     */
    @Test
    void shouldRoundTripBillLocationNo_asASingleCanonicalValue() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .billLocationNo("1985")
                .build();

        assertThat(m.getBillLocationNo()).isEqualTo("1985");
    }

    /**
     * The bill-record fields (billLocationNo, payProgram, billDate, billStatus,
     * billProvider, billTotal, hcType, hcSex) are populated by
     * BillingCorrection2Action#loadBillRecord from a BillingONCHeader1 row.
     * They MUST be independent — collapsing any pair would re-introduce the
     * d2db61d4 bridge-migration class of bugs.
     */
    @Test
    void shouldKeepBillRecordFields_independentOfEachOther() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .billLocationNo("1985")
                .billDate("2026-04-25")
                .billStatus("O")
                .billProvider("999998")
                .billTotal("123.45")
                .payProgram("HCP")
                .hcType("ON")
                .hcSex("F")
                .build();

        assertThat(m.getBillLocationNo()).isEqualTo("1985");
        assertThat(m.getBillDate()).isEqualTo("2026-04-25");
        assertThat(m.getBillStatus()).isEqualTo("O");
        assertThat(m.getBillProvider()).isEqualTo("999998");
        assertThat(m.getBillTotal()).isEqualTo("123.45");
        assertThat(m.getPayProgram()).isEqualTo("HCP");
        assertThat(m.getHcType()).isEqualTo("ON");
        assertThat(m.getHcSex()).isEqualTo("F");
    }
}
