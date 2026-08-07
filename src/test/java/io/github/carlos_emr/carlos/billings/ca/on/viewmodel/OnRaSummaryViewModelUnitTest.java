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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for RA summary money normalization. */
@DisplayName("OnRaSummaryViewModel")
@Tag("unit")
@Tag("billing")
class OnRaSummaryViewModelUnitTest {

    @Test
    void shouldNormalizeTotals_toScaleTwo() {
        OnRaSummaryViewModel model = OnRaSummaryViewModel.builder()
                .localTotal(new BigDecimal("1"))
                .payTotal(new BigDecimal("2.345"))
                .otherTotal(null)
                .obTotal(new BigDecimal("4.5"))
                .coTotal(new BigDecimal("6.789"))
                .build();

        assertThat(model.getLocalTotal()).isEqualByComparingTo("1.00");
        assertThat(model.getPayTotal()).isEqualByComparingTo("2.35");
        assertThat(model.getOtherTotal()).isEqualByComparingTo("0.00");
        assertThat(model.getObTotal()).isEqualByComparingTo("4.50");
        assertThat(model.getCoTotal()).isEqualByComparingTo("6.79");
    }
}
