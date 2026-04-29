/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCalendarPopupViewModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BillingCalendarPopupViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingCalendarPopupViewModelAssemblerUnitTest {

    @Test
    void shouldPreserveLegacyZeroDate_whenYearAndMonthAreMissing() {
        BillingCalendarPopupViewModel model =
                new BillingCalendarPopupViewModelAssembler().assemble(null, null, null, "admission");

        assertThat(model.getYear()).isEqualTo(2);
        assertThat(model.getMonth()).isEqualTo(12);
        assertThat(model.getType()).isEqualTo("admission");
    }

    @Test
    void shouldRejectMalformedYear() {
        assertThatThrownBy(() -> new BillingCalendarPopupViewModelAssembler()
                .assemble("bad-year", "4", "0", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("year");
    }

    @Test
    void shouldRejectMalformedDelta() {
        assertThatThrownBy(() -> new BillingCalendarPopupViewModelAssembler()
                .assemble("2026", "4", "next", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delta");
    }
}
