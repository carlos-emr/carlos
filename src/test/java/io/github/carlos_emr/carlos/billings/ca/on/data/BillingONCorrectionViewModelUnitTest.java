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
    void shouldDefaultAllFieldsWhenBuiltEmpty() {
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
    void shouldRoundTripAllFields() {
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
    void shouldReturnImmutableProviderAccessList() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .providerAccessList(Set.of("999998"))
                .build();

        assertThatThrownBy(() -> m.getProviderAccessList().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnImmutableMgrSites() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .mgrSites(List.of("Site A"))
                .build();

        assertThatThrownBy(() -> m.getMgrSites().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
