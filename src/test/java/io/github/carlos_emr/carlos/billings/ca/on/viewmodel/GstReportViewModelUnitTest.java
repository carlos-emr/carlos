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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for GST report money normalization. */
@DisplayName("GstReportViewModel")
@Tag("unit")
@Tag("billing")
class GstReportViewModelUnitTest {

    @Test
    void shouldNormalizeMoneyTotalsAndRows_toScaleTwo() {
        GstReportViewModel model = new GstReportViewModel(
                "2026-05-02", "", "", "",
                List.of(),
                List.of(new GstReportViewModel.Row(
                        "2026-04-01", "100", "Patient",
                        new BigDecimal("1"), new BigDecimal("2.345"), null)),
                new BigDecimal("3"), new BigDecimal("4.5"), null);

        assertThat(model.gstTotal()).isEqualByComparingTo("3.00");
        assertThat(model.earnedTotal()).isEqualByComparingTo("4.50");
        assertThat(model.billedTotal()).isEqualByComparingTo("0.00");
        assertThat(model.rows().get(0).gstBilled()).isEqualByComparingTo("1.00");
        assertThat(model.rows().get(0).earned()).isEqualByComparingTo("2.35");
        assertThat(model.rows().get(0).billed()).isEqualByComparingTo("0.00");
    }
}
