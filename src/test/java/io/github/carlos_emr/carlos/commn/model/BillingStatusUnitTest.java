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
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one definition of "this claim can no longer be unbilled" that the
 * Billing History page (to hide the Unbill link) and the unbill actions (to
 * refuse the post) must share.
 */
@DisplayName("BillingStatus")
@Tag("unit")
@Tag("billing")
class BillingStatusUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {BillingStatus.BILLED, BillingStatus.SETTLED})
    void shouldBeSubmittedOrSettled_forBilledAndSettledCodes(String status) {
        assertThat(BillingStatus.isSubmittedOrSettled(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            BillingStatus.OPEN, BillingStatus.DELETED, BillingStatus.PATIENT_BILLED,
            BillingStatus.NOT_BILLED, BillingStatus.INDEPENDENT, BillingStatus.WCB,
            BillingStatus.ACKNOWLEDGED})
    void shouldNotBeSubmittedOrSettled_forEveryOtherKnownCode(String status) {
        assertThat(BillingStatus.isSubmittedOrSettled(status)).isFalse();
    }

    @Test
    void shouldNotBeSubmittedOrSettled_forNullAndBlank() {
        assertThat(BillingStatus.isSubmittedOrSettled(null)).isFalse();
        assertThat(BillingStatus.isSubmittedOrSettled("")).isFalse();
        assertThat(BillingStatus.isSubmittedOrSettled(" ")).isFalse();
    }
}
