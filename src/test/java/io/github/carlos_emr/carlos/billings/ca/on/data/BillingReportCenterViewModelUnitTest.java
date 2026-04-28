/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BillingReportCenterViewModel")
@Tag("unit")
@Tag("billing")
class BillingReportCenterViewModelUnitTest {

    @Test
    void shouldDefensivelyCopyProviderRows() {
        List<BillingReportCenterViewModel.ProviderRow> rows = new ArrayList<>();
        rows.add(new BillingReportCenterViewModel.ProviderRow("ohip", "First", "Last"));

        BillingReportCenterViewModel model = BillingReportCenterViewModel.builder()
                .providerRows(rows)
                .build();

        rows.clear();

        assertThat(model.getProviderRows()).hasSize(1);
        assertThatThrownBy(() -> model.getProviderRows()
                .add(new BillingReportCenterViewModel.ProviderRow("x", "y", "z")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
