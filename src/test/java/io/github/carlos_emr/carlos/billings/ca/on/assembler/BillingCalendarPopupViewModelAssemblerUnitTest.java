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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCalendarPopupViewModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit coverage for {@code BillingCalendarPopupViewModelAssembler} and its popup field contract. */
@DisplayName("BillingCalendarPopupViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingCalendarPopupViewModelAssemblerUnitTest {

    @Test
    void shouldFallBackToToday_whenYearAndMonthAreMissing() {
        // Legacy behaviour rendered year=2/month=12 (year-1-BC December) for
        // the (null, null, null) input — never what the popup wanted. The
        // assembler now defaults to "today" so the calendar opens on the
        // current month when the request omits year/month.
        java.util.Calendar today = java.util.Calendar.getInstance();
        BillingCalendarPopupViewModel model =
                new BillingCalendarPopupViewModelAssembler().assemble(null, null, null, "admission");

        assertThat(model.getYear()).isEqualTo(today.get(java.util.Calendar.YEAR));
        assertThat(model.getMonth()).isEqualTo(today.get(java.util.Calendar.MONTH) + 1);
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
