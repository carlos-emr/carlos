/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for {@code BillingClaimHeaderDto} compatibility accessors and normalization helpers. */
@DisplayName("Ontario claim header DTO")
@Tag("unit")
@Tag("billing")
class BillingClaimHeaderDtoUnitTest {

    @Test
    void shouldCoalesceNullCashAndDebit_totalsToScaleTwoZero() {
        BillingClaimHeaderDto dto = new BillingClaimHeaderDto()
                .withCashTotal(null)
                .withDebitTotal(null);

        assertThat(dto.cashTotal()).isEqualByComparingTo("0.00");
        assertThat(dto.debitTotal()).isEqualByComparingTo("0.00");
        assertThat(dto.cashTotal().scale()).isEqualTo(2);
        assertThat(dto.debitTotal().scale()).isEqualTo(2);
    }

    @Test
    void shouldNormalizeNonBlankStringMoneyFields_toScaleTwo() {
        BillingClaimHeaderDto dto = new BillingClaimHeaderDto()
                .withTotal(" 12.3 ")
                .withPaid("4.456");

        assertThat(dto.total()).isEqualTo("12.30");
        assertThat(dto.paid()).isEqualTo("4.46");
    }
}
