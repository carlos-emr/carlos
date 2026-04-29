/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BillingONNewReportViewModel")
@Tag("unit")
@Tag("billing")
class BillingONNewReportViewModelUnitTest {

    @Test
    void shouldTreatNullBuilderListsAsEmptyImmutableLists() {
        BillingONNewReportViewModel model = BillingONNewReportViewModel.builder()
                .columnHeaders(null)
                .rows(null)
                .totalRow(null)
                .providerOptions(null)
                .siteOptions(null)
                .build();

        assertThat(model.getColumnHeaders()).isEmpty();
        assertThat(model.getRows()).isEmpty();
        assertThat(model.getTotalRow()).isEmpty();
        assertThat(model.getProviderOptions()).isEmpty();
        assertThat(model.getSiteOptions()).isEmpty();
        assertThatThrownBy(() -> model.getColumnHeaders().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldTreatNullSiteProvidersAsEmptyImmutableList() {
        BillingONNewReportViewModel.SiteOption site =
                new BillingONNewReportViewModel.SiteOption("site", "red", null);

        assertThat(site.providers()).isEmpty();
        assertThatThrownBy(() -> site.providers().add(
                new BillingONNewReportViewModel.SiteProviderEntry("1", "Dr A")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
