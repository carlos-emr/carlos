/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BillingCalendarPopupViewModel")
@Tag("unit")
@Tag("billing")
class BillingCalendarPopupViewModelUnitTest {

    @Test
    void shouldTreatNullWeekCellsAsEmptyImmutableList() {
        BillingCalendarPopupViewModel.WeekRow row =
                new BillingCalendarPopupViewModel.WeekRow(null);

        assertThat(row.cells()).isEmpty();
        assertThatThrownBy(() -> row.cells().add(new BillingCalendarPopupViewModel.DayCell(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
