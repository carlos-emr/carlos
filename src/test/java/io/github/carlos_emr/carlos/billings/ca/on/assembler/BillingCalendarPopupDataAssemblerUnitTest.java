/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCalendarPopupViewModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BillingCalendarPopupDataAssembler")
@Tag("unit")
@Tag("billing")
class BillingCalendarPopupDataAssemblerUnitTest {

    @Test
    void shouldPreserveLegacyZeroDate_whenYearAndMonthAreMissing() {
        BillingCalendarPopupViewModel model =
                new BillingCalendarPopupDataAssembler().assemble(null, null, null, "admission");

        assertThat(model.getYear()).isEqualTo(2);
        assertThat(model.getMonth()).isEqualTo(12);
        assertThat(model.getType()).isEqualTo("admission");
    }

    @Test
    void shouldRejectMalformedYear() {
        assertThatThrownBy(() -> new BillingCalendarPopupDataAssembler()
                .assemble("bad-year", "4", "0", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("year");
    }

    @Test
    void shouldRejectMalformedDelta() {
        assertThatThrownBy(() -> new BillingCalendarPopupDataAssembler()
                .assemble("2026", "4", "next", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delta");
    }
}
