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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingErrorReportDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BillingOnErrorReportService}: a stored rejected-claim fee is read in
 * the form the row carries, so OSCAR 19 imported rows (six-digit implied cents)
 * and CARLOS rows (dollars) render as the same money.
 */
@DisplayName("BillingOnErrorReportService")
@Tag("unit")
@Tag("billing")
class BillingOnErrorReportServiceUnitTest {

    @ParameterizedTest
    @CsvSource({
            "003370, 33.70",
            "000000, 0.00",
            "' 1234 ', 12.34",
            "33.70, 33.70",
            "1234.50, 1234.50",
    })
    void shouldReadStoredFeeInEitherForm_whenLoadingErrorReportRows(String stored, String expected) {
        BillingErrorReportDto dto = new BillingErrorReportDto();

        BillingOnErrorReportService.applyStoredFee(dto, stored);

        assertThat(dto.getFee()).isEqualTo(expected);
    }

    @Test
    void shouldLeaveFeeUnset_whenStoredValueIsBlank() {
        BillingErrorReportDto dto = new BillingErrorReportDto();

        BillingOnErrorReportService.applyStoredFee(dto, "  ");

        assertThat(dto.getFeeMoney()).isNull();
    }
}
