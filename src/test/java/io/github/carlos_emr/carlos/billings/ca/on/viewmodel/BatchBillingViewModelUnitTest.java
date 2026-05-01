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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BatchBillingViewModel")
@Tag("unit")
@Tag("billing")
class BatchBillingViewModelUnitTest {

    @Test
    void shouldDefensivelyCopyCallerOwnedLists() {
        List<BatchBillingViewModel.ProviderOption> providers = new ArrayList<>();
        providers.add(new BatchBillingViewModel.ProviderOption("1", "A", "B"));
        List<String> serviceCodes = new ArrayList<>(List.of("A001A"));
        List<BatchBillingViewModel.ClinicOption> clinics = new ArrayList<>();
        clinics.add(new BatchBillingViewModel.ClinicOption("c", "Clinic"));
        List<BatchBillingViewModel.Row> rows = new ArrayList<>();
        rows.add(new BatchBillingViewModel.Row("v", "demo", "provider", "A001A", "10", "401", "2026-04-28"));

        BatchBillingViewModel model = BatchBillingViewModel.builder()
                .providers(providers)
                .serviceCodes(serviceCodes)
                .clinicLocations(clinics)
                .rows(rows)
                .build();

        providers.clear();
        serviceCodes.clear();
        clinics.clear();
        rows.clear();

        assertThat(model.getProviders()).hasSize(1);
        assertThat(model.getServiceCodes()).containsExactly("A001A");
        assertThat(model.getClinicLocations()).hasSize(1);
        assertThat(model.getRows()).hasSize(1);
        assertThatThrownBy(() -> model.getServiceCodes().add("B001A"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
