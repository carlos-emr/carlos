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

/** Unit coverage for {@code BillingClaimItemDto} money normalization. */
@DisplayName("Ontario claim item DTO")
@Tag("unit")
@Tag("billing")
class BillingClaimItemDtoUnitTest {

    @Test
    void shouldNormalizeNonBlankMoneyFieldsToScaleTwo() {
        BillingClaimItemDto dto = new BillingClaimItemDto()
                .withFee(" 1 ")
                .withPaid("2.5")
                .withRefund("0")
                .withCredit("3.456")
                .withDiscount("4.454");

        assertThat(dto.fee()).isEqualTo("1.00");
        assertThat(dto.paid()).isEqualTo("2.50");
        assertThat(dto.refund()).isEqualTo("0.00");
        assertThat(dto.credit()).isEqualTo("3.46");
        assertThat(dto.discount()).isEqualTo("4.45");
    }
}
