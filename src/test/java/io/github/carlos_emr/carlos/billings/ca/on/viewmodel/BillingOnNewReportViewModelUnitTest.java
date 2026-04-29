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

@DisplayName("BillingOnNewReportViewModel")
@Tag("unit")
@Tag("billing")
class BillingOnNewReportViewModelUnitTest {

    @Test
    void shouldTreatNullBuilderListsAsEmptyImmutableLists() {
        BillingOnNewReportViewModel model = BillingOnNewReportViewModel.builder()
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
        BillingOnNewReportViewModel.SiteOption site =
                new BillingOnNewReportViewModel.SiteOption("site", "red", null);

        assertThat(site.providers()).isEmpty();
        assertThatThrownBy(() -> site.providers().add(
                new BillingOnNewReportViewModel.SiteProviderEntry("1", "Dr A")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
